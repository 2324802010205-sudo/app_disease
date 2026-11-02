package com.example.project_final.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.os.CancellationSignal;

import java.util.ArrayList;
import java.util.List;

/**
 * Lấy tọa độ hiện tại, ưu tiên nhanh nhất: có vị trí đã biết (dù cũ) thì trả ngay lập tức, sau đó nếu nó đã cũ thì hỏi song song
 * GPS và mạng (bên nào trả lời trước thắng) và trả thêm vị trí mới khi nó lệch đáng kể. Vì vậy
 * {@link Callback#onLocation} có thể được gọi tối đa hai lần. Quyền vị trí do màn hình gọi tự xin.
 */
public final class DeviceLocation {
    public static final String[] PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    /** Vị trí đã biết trong khoảng này được coi là đủ mới cho thời tiết/khu vực. */
    private static final long FRESH_MS = 15 * 60 * 1000L;
    /** Quá thời gian này mà chưa có vị trí mới thì dùng vị trí cũ nhất có được. */
    private static final long FALLBACK_MS = 3000L;
    /** Vị trí mới lệch dưới ngưỡng này so với vị trí đã báo thì bỏ qua (không gọi lại API thời tiết). */
    private static final float MOVED_M = 1000f;

    public interface Callback {
        void onLocation(double lat, double lng);

        /** Thiếu quyền, tắt dịch vụ vị trí hoặc không xác định được. */
        void onUnavailable();
    }

    private DeviceLocation() {}

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Trả về tín hiệu huỷ để màn hình huỷ khi bị đóng; null nếu đã có kết quả ngay hoặc không thể bắt đầu (callback đã được gọi). */
    public static CancellationSignal current(Context c, Callback callback) {
        Callback cb = new Callback() {
            private Location delivered;

            @Override
            public void onLocation(double lat, double lng) {
                if (delivered != null) {
                    float[] d = new float[1];
                    Location.distanceBetween(delivered.getLatitude(), delivered.getLongitude(), lat, lng, d);
                    if (d[0] < MOVED_M) return;   // vị trí mới gần như trùng vị trí đã báo
                }
                Location l = new Location("");
                l.setLatitude(lat);
                l.setLongitude(lng);
                delivered = l;
                callback.onLocation(lat, lng);
            }

            @Override
            public void onUnavailable() {
                if (delivered == null) callback.onUnavailable();   // đã có vị trí tạm thì không báo lỗi
            }
        };
        LocationManager manager = (LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || !hasPermission(c)) {
            cb.onUnavailable();
            return null;
        }
        Location known = lastKnown(manager);
        if (known != null) {
            cb.onLocation(known.getLatitude(), known.getLongitude());   // hiện ngay, không chờ GPS
            if (System.currentTimeMillis() - known.getTime() <= FRESH_MS) return null;
        }

        boolean precise = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        List<String> providers = new ArrayList<>();
        try {
            if (precise && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) providers.add(LocationManager.GPS_PROVIDER);
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
        } catch (SecurityException | IllegalArgumentException e) {
            providers.clear();
        }
        if (providers.isEmpty()) {
            deliver(known, cb);
            return null;
        }

        CancellationSignal signal = new CancellationSignal();
        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] finished = {false};
        int[] pending = {providers.size()};
        List<CancellationSignal> requests = new ArrayList<>();

        Runnable fallback = () -> {
            if (finished[0]) return;
            Location any = lastKnown(manager);
            if (any != null) {
                finished[0] = true;
                cancelAll(requests);
                deliver(any, cb);
            }
        };
        handler.postDelayed(fallback, FALLBACK_MS);
        signal.setOnCancelListener(() -> {
            finished[0] = true;
            handler.removeCallbacks(fallback);
            cancelAll(requests);
        });

        for (String provider : providers) {
            CancellationSignal request = new CancellationSignal();
            requests.add(request);
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, request, ContextCompat.getMainExecutor(c), location -> {
                    if (finished[0]) return;
                    pending[0]--;
                    if (location == null && pending[0] > 0) return;   // còn nhà cung cấp khác đang chạy
                    finished[0] = true;
                    handler.removeCallbacks(fallback);
                    cancelAll(requests);
                    deliver(location != null ? location : lastKnown(manager), cb);
                });
            } catch (SecurityException | IllegalArgumentException e) {
                pending[0]--;
                if (pending[0] == 0 && !finished[0]) {
                    finished[0] = true;
                    handler.removeCallbacks(fallback);
                    deliver(lastKnown(manager), cb);
                    return null;
                }
            }
        }
        return signal;
    }

    private static void deliver(Location location, Callback cb) {
        if (location == null) cb.onUnavailable();
        else cb.onLocation(location.getLatitude(), location.getLongitude());
    }

    private static void cancelAll(List<CancellationSignal> requests) {
        for (CancellationSignal r : requests) r.cancel();
    }

    /** Vị trí mới nhất trong các nhà cung cấp (kể cả thụ động), null nếu chưa có hoặc không có quyền. */
    private static Location lastKnown(LocationManager manager) {
        Location best = null;
        try {
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER}) {
                Location l = manager.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // không có quyền hoặc nhà cung cấp không tồn tại
        }
        return best;
    }
}

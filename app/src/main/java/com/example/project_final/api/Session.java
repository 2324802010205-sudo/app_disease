package com.example.project_final.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.project_final.model.AuthResponse;
import com.example.project_final.model.User;
import com.example.project_final.push.PushRegistration;
import com.example.project_final.util.Appearance;
import com.google.gson.Gson;

import java.util.concurrent.CopyOnWriteArrayList;

/** Phiên đăng nhập: token và hồ sơ người dùng hiện tại, lưu trong SharedPreferences riêng của app. */
public final class Session {
    public interface ExpiredListener {
        void onSessionExpired();
    }

    private static final String PREFS = "riceguard_session";
    private static final String K_ID = "idToken", K_REFRESH = "refreshToken", K_USER = "user", K_GUEST = "guest", K_GUEST_PROVINCE = "guestProvince";
    private static final String K_WEATHER_OWNER = "weatherOwner", K_WEATHER_LABEL = "weatherLabel";
    private static final String K_WEATHER_LAT = "weatherLat", K_WEATHER_LNG = "weatherLng";

    public static final class WeatherLocation {
        public final String label;
        public final Double lat, lng;

        public WeatherLocation(String label, Double lat, Double lng) {
            this.label = label;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static SharedPreferences prefs;
    private static Context appContext;
    private static final Gson GSON = new Gson();
    private static final CopyOnWriteArrayList<ExpiredListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static User cachedUser;

    private Session() {}

    public static void init(Context app) {
        appContext = app.getApplicationContext();
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized boolean isLoggedIn() {
        return refreshToken() != null;
    }

    /** Khách: dùng app không đăng nhập, chỉ xem. Có tài khoản đăng nhập thì không còn là khách. */
    public static synchronized boolean isGuest() {
        return !isLoggedIn() && prefs.getBoolean(K_GUEST, false);
    }

    public static synchronized void enterGuest() {
        prefs.edit().putBoolean(K_GUEST, true).apply();
    }

    /** Tỉnh khách chọn để xem thời tiết. */
    public static synchronized String guestProvince() {
        return prefs.getString(K_GUEST_PROVINCE, null);
    }

    public static synchronized void setGuestProvince(String province) {
        prefs.edit().putString(K_GUEST_PROVINCE, province).apply();
    }

    /** Tỉnh dùng cho thời tiết: tỉnh trong hồ sơ nếu đã đăng nhập, ngược lại tỉnh khách đã chọn. */
    public static synchronized String province() {
        User u = user();
        if (isLoggedIn()) return u == null || u.province == null || u.province.isEmpty() ? null : u.province;
        return guestProvince();
    }

    /** A weather choice belongs to the current account (or guest), independently of profile province. */
    public static synchronized WeatherLocation weatherLocation() {
        String owner = weatherOwner();
        if (owner.equals(prefs.getString(K_WEATHER_OWNER, null))) {
            String label = prefs.getString(K_WEATHER_LABEL, null);
            if (label != null) {
                Double lat = prefs.contains(K_WEATHER_LAT) ? Double.longBitsToDouble(prefs.getLong(K_WEATHER_LAT, 0)) : null;
                Double lng = prefs.contains(K_WEATHER_LNG) ? Double.longBitsToDouble(prefs.getLong(K_WEATHER_LNG, 0)) : null;
                return new WeatherLocation(label, lat, lng);
            }
        }
        String province = province();
        return province == null || province.isEmpty() ? null : new WeatherLocation(province, null, null);
    }

    public static synchronized void setWeatherLocation(String label, Double lat, Double lng) {
        SharedPreferences.Editor edit = prefs.edit().putString(K_WEATHER_OWNER, weatherOwner()).putString(K_WEATHER_LABEL, label);
        if (lat == null || lng == null) edit.remove(K_WEATHER_LAT).remove(K_WEATHER_LNG);
        else edit.putLong(K_WEATHER_LAT, Double.doubleToRawLongBits(lat)).putLong(K_WEATHER_LNG, Double.doubleToRawLongBits(lng));
        edit.apply();
    }

    private static String weatherOwner() {
        User u = user();
        return isLoggedIn() && u != null && u.id != null ? u.id : "guest";
    }

    public static synchronized String idToken() {
        return prefs.getString(K_ID, null);
    }

    public static synchronized String refreshToken() {
        return prefs.getString(K_REFRESH, null);
    }

    public static synchronized void save(AuthResponse r) {
        SharedPreferences.Editor e = prefs.edit().putString(K_ID, r.idToken).remove(K_GUEST);
        if (r.refreshToken != null) e.putString(K_REFRESH, r.refreshToken);
        if (r.user != null) {
            cachedUser = r.user;
            e.putString(K_USER, GSON.toJson(r.user));
        }
        e.apply();
        if (r.user != null) syncAppearance(r.user);
        if (prefs.getString(K_REFRESH, null) != null) PushRegistration.sync(appContext);
    }

    public static synchronized User user() {
        if (cachedUser == null) {
            String json = prefs.getString(K_USER, null);
            if (json != null) cachedUser = GSON.fromJson(json, User.class);
        }
        return cachedUser;
    }

    public static synchronized void setUser(User u) {
        cachedUser = u;
        prefs.edit().putString(K_USER, GSON.toJson(u)).apply();
        syncAppearance(u);
    }

    private static void syncAppearance(User user) {
        if (user == null || user.settings == null) return;
        if (user.settings.theme != null && !user.settings.theme.equals(Appearance.theme(appContext)))
            Appearance.applyTheme(appContext, user.settings.theme);
        if (user.settings.language != null && !user.settings.language.equals(Appearance.language(appContext)))
            Appearance.applyLanguage(appContext, user.settings.language);
    }

    public static synchronized void clear() {
        cachedUser = null;
        prefs.edit().clear().apply();
    }

    /** Gọi khi refresh token hết hạn: xóa phiên rồi báo cho màn hình đang mở quay về đăng nhập. */
    static void expire() {
        clear();
        new Handler(Looper.getMainLooper()).post(() -> {
            for (ExpiredListener l : LISTENERS) l.onSessionExpired();
        });
    }

    public static void addExpiredListener(ExpiredListener l) {
        LISTENERS.addIfAbsent(l);
    }

    public static void removeExpiredListener(ExpiredListener l) {
        LISTENERS.remove(l);
    }
}

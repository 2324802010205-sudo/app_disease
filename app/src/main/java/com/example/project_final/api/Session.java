package com.example.project_final.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.project_final.model.AuthResponse;
import com.example.project_final.model.User;
import com.google.gson.Gson;

import java.util.concurrent.CopyOnWriteArrayList;

/** Phiên đăng nhập: token và hồ sơ người dùng hiện tại, lưu trong SharedPreferences riêng của app. */
public final class Session {
    public interface ExpiredListener {
        void onSessionExpired();
    }

    private static final String PREFS = "riceguard_session";
    private static final String K_ID = "idToken", K_REFRESH = "refreshToken", K_USER = "user", K_GUEST = "guest", K_GUEST_PROVINCE = "guestProvince";

    private static SharedPreferences prefs;
    private static final Gson GSON = new Gson();
    private static final CopyOnWriteArrayList<ExpiredListener> LISTENERS = new CopyOnWriteArrayList<>();
    private static User cachedUser;

    private Session() {}

    public static void init(Context app) {
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

package com.example.project_final;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;

public class SplashActivity extends BaseActivity {
    private static final long MIN_SHOW_MS = 1200;
    private static final String PREFS = "riceguard_app";
    private static final String K_ONBOARDED = "onboarded";

    private boolean timeUp;
    private Runnable afterTimer;

    @Override
    protected int layout() {
        return R.layout.activity_splash;
    }

    @Override
    protected boolean padTopInset() {
        return false;
    }

    @Override
    protected boolean requiresLogin() {
        return false;
    }

    static boolean onboarded(Context c) {
        return c.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(K_ONBOARDED, false);
    }

    static void markOnboarded(Context c) {
        c.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(K_ONBOARDED, true).apply();
    }

    /**
     * Android 17 chặn kết nối tới địa chỉ mạng nội bộ (10.0.2.2, IP LAN) nếu chưa cấp quyền ACCESS_LOCAL_NETWORK.
     * Chỉ bản debug khai báo quyền này (backend chạy trên máy tính); bản phát hành dùng HTTPS công khai nên không cần.
     */
    private void ensureLocalNetworkAccess(Runnable next) {
        final String permission = "android.permission.ACCESS_LOCAL_NETWORK";
        boolean needed = BuildConfig.DEBUG && android.os.Build.VERSION.SDK_INT >= 37
                && checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED;
        if (!needed) {
            next.run();
            return;
        }
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> next.run())
                .launch(permission);
    }

    @Override
    protected void onReady() {
        ensureLocalNetworkAccess(this::start);
    }

    private void start() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            timeUp = true;
            if (afterTimer != null) afterTimer.run();
        }, MIN_SHOW_MS);

        if (Session.isGuest()) {
            next(HomeActivity.class);
            return;
        }
        if (!Session.isLoggedIn()) {
            next(onboarded(this) ? LoginActivity.class : OnboardingActivity.class);
            return;
        }
        // Có phiên cũ: xác nhận còn hợp lệ và làm mới hồ sơ. Mất mạng thì vẫn vào được bằng hồ sơ đã lưu.
        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
                next(HomeActivity.class);
            }

            @Override
            public void onError(ApiError e) {
                if (e.isUnauthenticated()) next(LoginActivity.class);
                else if (e.isNetwork() && Session.user() != null) next(HomeActivity.class);
                else {
                    toast(e.message);
                    next(LoginActivity.class);
                }
            }
        });
    }

    /** Chuyển màn hình sau khi hết thời gian hiển thị tối thiểu. */
    private void next(Class<?> target) {
        Runnable go = () -> {
            Intent it = new Intent(this, target);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (target == HomeActivity.class && getIntent().hasExtra("notificationId")) {
                startActivities(new Intent[]{it, new Intent(this, NotificationsActivity.class)});
            } else {
                startActivity(it);
            }
            finish();
        };
        if (timeUp) go.run();
        else afterTimer = go;
    }
}

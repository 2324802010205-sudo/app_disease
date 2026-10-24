package com.example.project_final;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiClient;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.RiceApi;
import com.example.project_final.api.Session;
import com.example.project_final.view.NavBar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;

/**
 * Nền chung của mọi màn hình. Mỗi màn hình có layout với view gốc id "root".
 * Cung cấp: xử lý tai thỏ/thanh hệ thống, gọi API gắn theo vòng đời (tự hủy khi đóng màn hình),
 * thông báo lỗi chuẩn, lớp chờ khi gửi dữ liệu, thanh điều hướng nổi và đưa về đăng nhập khi phiên hết hạn.
 */
public abstract class BaseActivity extends AppCompatActivity implements Session.ExpiredListener {
    /** Cỡ chữ hệ thống tối đa được tôn trọng. Lớn hơn mức này bố cục dễ vỡ, nên chặn ở 1,3 lần. */
    private static final float MAX_FONT_SCALE = 1.3f;

    private final List<Call<?>> pending = new ArrayList<>();
    private View busyOverlay;
    private int busyCount;
    private NavBar navBar;

    @LayoutRes
    protected abstract int layout();

    /** Called once the layout is inflated. */
    protected abstract void onReady();

    /** Screens with a full-bleed photo at the top draw under the status bar. */
    protected boolean padTopInset() {
        return true;
    }

    /** Màn hình đăng nhập/đăng ký trả false; các màn hình khác tự quay về đăng nhập nếu chưa có phiên. */
    protected boolean requiresLogin() {
        return true;
    }

    /** Màn hình chỉ xem (thư viện bệnh, thời tiết, bản đồ nguy cơ, cộng đồng...) cho phép khách vào. */
    protected boolean allowsGuest() {
        return false;
    }

    /** Đang dùng app ở chế độ khách (chưa đăng nhập, chỉ xem). */
    protected boolean isGuest() {
        return Session.isGuest();
    }

    /**
     * Cổng chặn cho thao tác cần tài khoản (quét, bình luận, thích, lưu...). Trả true nếu được phép tiếp tục.
     * Khách thì hiện hộp thoại mời đăng nhập hoặc đăng ký và trả false. action nối vào câu "Bạn cần đăng nhập ... để <action>".
     */
    public boolean requireAccount(String action) {
        if (!Session.isGuest()) return true;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Cần đăng nhập")
                .setMessage("Bạn đang xem ở chế độ khách. Hãy đăng nhập hoặc đăng ký để " + action + ".")
                .setNegativeButton("Để sau", null)
                .setNeutralButton("Đăng ký", (d, w) -> leaveGuest(true))
                .setPositiveButton("Đăng nhập", (d, w) -> leaveGuest(false))
                .show();
        return false;
    }

    private void leaveGuest(boolean register) {
        Session.clear();
        Intent it = new Intent(this, LoginActivity.class).putExtra(LoginActivity.EXTRA_OPEN_REGISTER, register);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(it);
        finish();
    }

    @Override
    protected void attachBaseContext(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        if (c.fontScale > MAX_FONT_SCALE) {
            c.fontScale = MAX_FONT_SCALE;
            base = base.createConfigurationContext(c);
        }
        super.attachBaseContext(base);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (requiresLogin() && !Session.isLoggedIn() && !(Session.isGuest() && allowsGuest())) {
            toLogin();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(layout());
        View root = findViewById(R.id.root);
        if (root instanceof ViewGroup) ((ViewGroup) root).setClipToPadding(false);
        final boolean padTop = padTopInset();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, padTop ? bars.top : 0, bars.right, bars.bottom);
            if (padTop) liftDecorations(v, -bars.top);
            return insets;
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> Ui.scaleIcons(root));
        View back = findViewById(R.id.btn_back);
        if (back != null) back.setOnClickListener(v -> finish());
        onReady();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Session.addExpiredListener(this);
    }

    @Override
    protected void onStop() {
        Session.removeExpiredListener(this);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (navBar != null) refreshNav();
    }

    @Override
    protected void onDestroy() {
        for (Call<?> c : pending) c.cancel();
        pending.clear();
        super.onDestroy();
    }

    @Override
    public void onSessionExpired() {
        toast("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        toLogin();
    }

    protected void toLogin() {
        Intent it = new Intent(this, LoginActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(it);
        finish();
    }

    // ---------------------------------------------------------------- API

    protected RiceApi api() {
        return ApiClient.api();
    }

    /** Gọi API; kết quả chỉ được chuyển cho màn hình khi nó còn tồn tại, và yêu cầu tự hủy khi màn hình đóng. */
    protected <T> void call(Call<T> call, ApiCallback<T> callback) {
        pending.add(call);
        call.enqueue(new ApiCallback<T>() {
            @Override
            public void onSuccess(T body) {
                pending.remove(call);
                if (!isFinishing() && !isDestroyed()) callback.onSuccess(body);
            }

            @Override
            public void onError(ApiError error) {
                pending.remove(call);
                if (!isFinishing() && !isDestroyed()) callback.onError(error);
            }
        });
    }

    /** Lấy danh mục lựa chọn (giống lúa, giai đoạn, tỉnh...) rồi gọi ok; lỗi thì báo toast. */
    protected void withMeta(java.util.function.Consumer<com.example.project_final.model.MetaOptions> ok) {
        com.example.project_final.util.Meta.load(new com.example.project_final.util.Meta.Listener() {
            @Override
            public void onLoaded(com.example.project_final.model.MetaOptions options) {
                if (!isFinishing() && !isDestroyed()) ok.accept(options);
            }

            @Override
            public void onError(ApiError error) {
                if (!isFinishing() && !isDestroyed()) showError(error);
            }
        });
    }

    /** Tải nhiều ảnh lên máy chủ; callback chỉ chạy khi màn hình còn tồn tại. */
    protected void uploadImages(java.util.List<android.net.Uri> uris, String kind, com.example.project_final.util.ImageUploader.Callback cb) {
        if (uris.isEmpty()) {
            cb.onDone(new ArrayList<>());
            return;
        }
        com.example.project_final.util.ImageUploader.upload(this, uris, kind, new com.example.project_final.util.ImageUploader.Callback() {
            @Override
            public void onDone(java.util.List<com.example.project_final.model.ImageRef> refs) {
                if (!isFinishing() && !isDestroyed()) cb.onDone(refs);
            }

            @Override
            public void onError(String message) {
                if (!isFinishing() && !isDestroyed()) cb.onError(message);
            }
        });
    }

    /** Hiển thị lỗi từ API dưới dạng toast (thông điệp tiếng Việt do backend/lớp mạng cung cấp). */
    protected void showError(ApiError e) {
        toast(e.message);
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /** Lớp chờ chặn thao tác khi đang gửi dữ liệu. Gọi cặp busy(true) / busy(false). */
    protected void busy(boolean on) {
        busyCount = Math.max(0, busyCount + (on ? 1 : -1));
        if (busyCount > 0 && busyOverlay == null) {
            FrameLayout scrim = new FrameLayout(this);
            scrim.setBackgroundResource(R.color.scrim);
            scrim.setClickable(true);
            scrim.setFocusable(true);
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.white)));
            scrim.addView(pb, new FrameLayout.LayoutParams((int) Ui.dp(this, 48), (int) Ui.dp(this, 48), Gravity.CENTER));
            ((ViewGroup) findViewById(android.R.id.content)).addView(scrim,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            busyOverlay = scrim;
        } else if (busyCount == 0 && busyOverlay != null) {
            ((ViewGroup) busyOverlay.getParent()).removeView(busyOverlay);
            busyOverlay = null;
        }
    }

    // ---------------------------------------------------------------- layout helpers

    /** Decorative corner images (tag "deco") bleed under the status bar. */
    private void liftDecorations(View view, float dy) {
        if ("deco".equals(view.getTag())) view.setTranslationY(dy);
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) liftDecorations(g.getChildAt(i), dy);
        }
    }

    protected void go(@IdRes int viewId, Class<?> target) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> startActivity(new Intent(this, target)));
    }

    public static final String EXTRA_ID = "id";

    /** Mở màn hình chi tiết kèm id của đối tượng. */
    protected void open(Class<?> target, String id) {
        startActivity(new Intent(this, target).putExtra(EXTRA_ID, id));
    }

    protected void go(View v, Class<?> target) {
        v.setOnClickListener(x -> startActivity(new Intent(this, target)));
    }

    protected void done(@IdRes int viewId) {
        View v = findViewById(viewId);
        if (v != null) v.setOnClickListener(x -> finish());
    }

    protected void setText(@IdRes int id, CharSequence s) {
        ((TextView) findViewById(id)).setText(s);
    }

    // ---------------------------------------------------------------- thanh điều hướng

    /** Gắn thanh điều hướng nổi (view id bottom_nav) và đánh dấu mục đang mở, xem NavBar.HOME... */
    protected void setupBottomNav(int selected) {
        navBar = findViewById(R.id.bottom_nav);
        if (navBar == null) return;
        navBar.select(selected);
        // Màn hình chính không có nút quay lại
        View back = findViewById(R.id.btn_back);
        if (back != null) back.setVisibility(View.GONE);
    }

    private void refreshNav() {
        navBar.bindAvatar();
        if (Session.isGuest()) {
            navBar.setBadge(0);
            return;
        }
        call(api().unreadCount(), new ApiCallback<java.util.Map<String, Long>>() {
            @Override
            public void onSuccess(java.util.Map<String, Long> body) {
                Long n = body == null ? null : body.get("unread");
                navBar.setBadge(n == null ? 0 : n);
            }

            @Override
            public void onError(ApiError error) {
                // huy hiệu chỉ là thông tin phụ, lỗi thì bỏ qua
            }
        });
    }
}

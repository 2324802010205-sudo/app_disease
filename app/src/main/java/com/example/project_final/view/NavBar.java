package com.example.project_final.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.project_final.FieldsActivity;
import com.example.project_final.HomeActivity;
import com.example.project_final.LibraryActivity;
import com.example.project_final.NotificationsActivity;
import com.example.project_final.ProfileActivity;
import com.example.project_final.R;
import com.example.project_final.ScanActivity;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;
import com.example.project_final.util.Img;

/** Thanh điều hướng nổi dạng viên thuốc của các màn hình chính. */
public class NavBar extends FrameLayout {
    public static final int HOME = 0, FIELDS = 1, SCAN = 2, LIBRARY = 3, NOTIFICATIONS = 4, PROFILE = 5;

    private static final int[] ITEMS = {R.id.nav_home, R.id.nav_fields, R.id.nav_scan, R.id.nav_library, R.id.nav_notifications, R.id.nav_profile};
    private static final int[] ICONS = {R.id.nav_home_icon, R.id.nav_fields_icon, R.id.nav_scan_icon, R.id.nav_library_icon, R.id.nav_notifications_icon, 0};
    private static final int[] BLOBS = {R.id.nav_home_blob, R.id.nav_fields_blob, R.id.nav_scan_blob, R.id.nav_library_blob, R.id.nav_notifications_blob, R.id.nav_profile_blob};
    private static final Class<?>[] TARGETS = {HomeActivity.class, FieldsActivity.class, ScanActivity.class, LibraryActivity.class, NotificationsActivity.class, ProfileActivity.class};

    private int selected = -1;

    public NavBar(Context c) {
        this(c, null);
    }

    public NavBar(Context c, AttributeSet a) {
        super(c, a);
        LayoutInflater.from(c).inflate(R.layout.view_nav_bar, this, true);
        setClipChildren(false);
        setClipToPadding(false);
        for (int i = 0; i < ITEMS.length; i++) {
            final int index = i;
            findViewById(ITEMS[i]).setOnClickListener(v -> open(index));
        }
        bindAvatar();
    }

    /** Đánh dấu mục đang mở. */
    public void select(int index) {
        selected = index;
        int on = getContext().getColor(R.color.nav_icon_on);
        int off = getContext().getColor(R.color.nav_icon);
        for (int i = 0; i < ITEMS.length; i++) {
            boolean sel = i == index;
            findViewById(BLOBS[i]).setVisibility(sel ? VISIBLE : INVISIBLE);
            if (ICONS[i] != 0) ((ImageView) findViewById(ICONS[i])).setColorFilter(sel ? on : off);
            findViewById(ITEMS[i]).setSelected(sel);
        }
        findViewById(R.id.nav_profile_ring).setVisibility(index == PROFILE ? VISIBLE : GONE);
        ((ImageView) findViewById(R.id.nav_home_icon)).setImageResource(index == HOME ? R.drawable.ic_home : R.drawable.ic_home_outline);
        ((ImageView) findViewById(R.id.nav_notifications_icon)).setImageResource(index == NOTIFICATIONS ? R.drawable.ic_notifications : R.drawable.ic_notifications_outline);
    }

    /** Số thông báo chưa đọc; 0 thì ẩn huy hiệu. */
    public void setBadge(long count) {
        TextView badge = findViewById(R.id.nav_badge);
        if (count <= 0) {
            badge.setVisibility(GONE);
            findViewById(R.id.nav_notifications).setContentDescription("Thông báo");
        } else {
            badge.setText(count > 9 ? "9+" : String.valueOf(count));
            badge.setVisibility(VISIBLE);
            findViewById(R.id.nav_notifications).setContentDescription("Thông báo, " + count + " chưa đọc");
        }
    }

    /** Nạp lại ảnh đại diện từ hồ sơ đang đăng nhập. */
    public void bindAvatar() {
        User u = Session.user();
        Img.load((ImageView) findViewById(R.id.nav_profile_icon), u == null ? null : u.avatarUrl, R.drawable.ic_avatar_default);
    }

    private static final String[] GUEST_ACTIONS = {null, "quản lý ruộng lúa", "quét và phân tích bệnh lúa", null, "xem thông báo", "xem hồ sơ cá nhân"};

    private void open(int index) {
        if (index == selected) return;
        Context ctx = getContext();
        // Khách chỉ xem: các mục cần tài khoản hiện lời mời đăng nhập/đăng ký.
        if (GUEST_ACTIONS[index] != null && ctx instanceof com.example.project_final.BaseActivity
                && !((com.example.project_final.BaseActivity) ctx).requireAccount(GUEST_ACTIONS[index])) return;
        Intent it = new Intent(ctx, TARGETS[index]);
        if (index == SCAN) {
            ctx.startActivity(it);
            return;
        }
        // Các tab chính đứng cạnh nhau: về Trang chủ thì dọn ngăn xếp, sang tab khác thì thay tab hiện tại.
        it.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | (index == HOME ? Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP : 0));
        ctx.startActivity(it);
        if (ctx instanceof Activity && index != HOME && !(ctx instanceof HomeActivity)) ((Activity) ctx).finish();
        if (ctx instanceof Activity) ((Activity) ctx).overridePendingTransition(0, 0);
    }
}

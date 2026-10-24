package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;
import com.example.project_final.view.NavBar;

public class ProfileActivity extends BaseActivity {
    @Override
    protected int layout() {
        return R.layout.activity_profile;
    }

    @Override
    protected void onReady() {
        setupBottomNav(NavBar.PROFILE);
        go(R.id.stat_scans, HistoryActivity.class);
        go(R.id.stat_fields, FieldsActivity.class);
        go(R.id.stat_alerts, NotificationsActivity.class);

        String[] labels = {"Chỉnh sửa hồ sơ", "Thống kê cá nhân", "Lịch sử quét", "Bệnh đã lưu", "Ruộng lúa của tôi", "Cộng đồng nông dân", "Bài viết của tôi", "Cài đặt", "Hướng dẫn sử dụng", "Quyền riêng tư"};
        int[] icons = {R.drawable.ic_person_outline, R.drawable.ic_bar_chart, R.drawable.ic_history, R.drawable.ic_bookmark, R.drawable.ic_sprout,
                R.drawable.ic_group, R.drawable.ic_description, R.drawable.ic_settings, R.drawable.ic_menu_book, R.drawable.ic_shield_check};
        Class<?>[] targets = {EditProfileActivity.class, StatsActivity.class, HistoryActivity.class, SavedActivity.class, FieldsActivity.class,
                CommunityActivity.class, MyPostsActivity.class, SettingsActivity.class, null, null};
        String[] infoTitle = {null, null, null, null, null, null, null, null, "Hướng dẫn sử dụng", "Quyền riêng tư"};
        String[] infoText = {null, null, null, null, null, null, null, null,
                "1. Mở mục Quét AI và chụp ảnh một lá lúa có dấu hiệu bất thường, đủ sáng và rõ nét.\n2. Chọn ruộng tương ứng để theo dõi theo từng ruộng.\n3. Xem kết quả, triệu chứng và khuyến nghị; gửi phản hồi nếu kết quả chưa đúng.\n4. Ghi nhật ký canh tác và theo dõi bản đồ nguy cơ của khu vực.",
                "Ảnh và dữ liệu của bạn chỉ được dùng để chẩn đoán bệnh và cải thiện dịch vụ. Dữ liệu cá nhân được lưu riêng theo tài khoản; bản đồ nguy cơ chỉ dùng số liệu tổng hợp ẩn danh theo khu vực. Bạn có thể xóa tài khoản và toàn bộ dữ liệu bất cứ lúc nào trong mục Cài đặt."};
        LinearLayout menu = findViewById(R.id.list_menu);
        Ui.fill(menu, R.layout.item_menu_row, labels.length, (item, i) -> {
            Ui.text(item, R.id.label, labels[i]);
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(icons[i]);
            if (i == 0) item.findViewById(R.id.divider).setVisibility(View.GONE);
            item.findViewById(R.id.row).setOnClickListener(v -> {
                if (targets[i] != null) startActivity(new Intent(this, targets[i]));
                else new AlertDialog.Builder(this).setTitle(infoTitle[i]).setMessage(infoText[i]).setPositiveButton("Đã hiểu", null).show();
            });
        });

        findViewById(R.id.btn_logout).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Đăng xuất?")
                .setMessage("Bạn sẽ cần đăng nhập lại để tiếp tục sử dụng.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (d, w) -> logout())
                .show());
        bind(Session.user());
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
                bind(u);
            }

            @Override
            public void onError(ApiError e) {
                // vẫn hiển thị hồ sơ đã lưu trên máy
            }
        });
    }

    private void bind(User u) {
        if (u == null) return;
        setText(R.id.profile_name, u.fullName);
        setText(R.id.profile_role, u.verifiedExpert ? "Chuyên gia" : Look.role(u.role));
        setText(R.id.profile_email, u.email);
        String place = (u.district == null || u.district.isEmpty() ? "" : u.district + ", ") + (u.province == null ? "" : u.province);
        setText(R.id.profile_place, place.isEmpty() ? "Chưa chọn khu vực" : place);
        Img.avatar(findViewById(R.id.avatar), u.avatarUrl);
        User.Stats s = u.stats;
        setText(R.id.stat_scans_n, s == null ? "-" : String.valueOf(s.scans));
        setText(R.id.stat_fields_n, s == null ? "-" : String.valueOf(s.fields));
        setText(R.id.stat_alerts_n, s == null ? "-" : String.valueOf(s.unreadAlerts));
    }

    /** Thu hồi phiên trên máy chủ (best-effort) rồi xóa phiên cục bộ. */
    private void logout() {
        busy(true);
        call(api().logout(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                finishLogout();
            }

            @Override
            public void onError(ApiError e) {
                finishLogout();
            }
        });
    }

    private void finishLogout() {
        busy(false);
        Session.clear();
        toLogin();
    }
}

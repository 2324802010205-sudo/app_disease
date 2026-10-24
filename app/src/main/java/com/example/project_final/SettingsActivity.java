package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.User;
import com.example.project_final.util.Forms;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends BaseActivity {
    private MaterialSwitch[] switches = new MaterialSwitch[3];
    private boolean binding;

    @Override
    protected int layout() {
        return R.layout.activity_settings;
    }

    @Override
    protected void onReady() {
        String[] title = {"Cảnh báo bệnh", "Nhắc lịch kiểm tra", "Cập nhật cộng đồng"};
        String[] desc = {"Nhận thông báo khi phát hiện dấu hiệu sâu bệnh hoặc thời tiết bất thường", "Nhận nhắc nhở định kỳ kiểm tra đồng ruộng", "Nhận thông báo về bài viết và hoạt động cộng đồng"};
        int[] icon = {R.drawable.ic_eco, R.drawable.ic_calendar, R.drawable.ic_group};
        Ui.fill(findViewById(R.id.list_switches), R.layout.item_switch_row, 3, (item, i) -> {
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(icon[i]);
            Ui.text(item, R.id.title, title[i]);
            Ui.text(item, R.id.desc, desc[i]);
            MaterialSwitch sw = item.findViewById(R.id.switcher);
            switches[i] = sw;
            sw.setOnCheckedChangeListener((b, checked) -> {
                if (!binding) saveSwitches();
            });
        });
        bindSettings(Session.user());

        String[] appLabels = {"Phiên bản", "Giới thiệu ứng dụng", "Trợ giúp & câu hỏi thường gặp", "Quyền riêng tư"};
        int[] appIcons = {R.drawable.ic_info, R.drawable.ic_description, R.drawable.ic_help, R.drawable.ic_shield_check};
        String[] infoText = {null,
                "RiceGuard AI giúp nông dân phát hiện sớm bệnh trên lá lúa bằng ảnh chụp, đưa ra khuyến nghị, theo dõi ruộng và chia sẻ kinh nghiệm với cộng đồng. Kết quả chỉ mang tính tham khảo; hãy đọc kỹ nhãn thuốc và hỏi cán bộ bảo vệ thực vật trước khi xử lý.",
                "• Ảnh bị báo “cần chụp lại”: chụp cận một lá, đủ sáng, giữ chắc tay.\n• Không có kết quả: kiểm tra kết nối mạng rồi bấm thử lại ở màn kết quả.\n• Kết quả chưa đúng: dùng nút Gửi phản hồi để giúp cải thiện độ chính xác.",
                "Ảnh và dữ liệu của bạn chỉ dùng để chẩn đoán và cải thiện dịch vụ, lưu riêng theo tài khoản. Bạn có thể xóa tài khoản cùng toàn bộ dữ liệu ngay trong mục này."};
        Ui.fill(findViewById(R.id.list_app), R.layout.item_menu_row, 4, (item, i) -> {
            Ui.text(item, R.id.label, appLabels[i]);
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(appIcons[i]);
            if (i == 0) {
                item.findViewById(R.id.chevron).setVisibility(View.GONE);
                TextView value = item.findViewById(R.id.value);
                value.setVisibility(View.VISIBLE);
                value.setText(BuildConfig.VERSION_NAME);
            } else {
                item.findViewById(R.id.row).setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setTitle(appLabels[i]).setMessage(infoText[i]).setPositiveButton("Đã hiểu", null).show());
            }
        });

        findViewById(R.id.btn_password).setOnClickListener(v -> changePassword());
        findViewById(R.id.btn_reset).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Xóa tài khoản?")
                .setMessage("Toàn bộ ruộng, lịch sử quét, nhật ký và ảnh của bạn sẽ bị xóa vĩnh viễn. Không thể hoàn tác.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa vĩnh viễn", (d, w) -> deleteAccount())
                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
                bindSettings(u);
            }

            @Override
            public void onError(ApiError e) {
                // dùng cài đặt đã lưu trên máy
            }
        });
    }

    private void bindSettings(User u) {
        if (u == null) return;
        User.Settings s = u.settingsOrDefault();
        binding = true;
        switches[0].setChecked(s.notifyAlert);
        switches[1].setChecked(s.notifyReminder);
        switches[2].setChecked(s.notifyCommunity);
        binding = false;
    }

    private void saveSwitches() {
        Requests.Settings body = new Requests.Settings();
        body.notifyAlert = switches[0].isChecked();
        body.notifyReminder = switches[1].isChecked();
        body.notifyCommunity = switches[2].isChecked();
        call(api().updateSettings(body), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                bindSettings(Session.user());
            }
        });
    }

    private void changePassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText current = passwordField("Mật khẩu hiện tại");
        EditText next = passwordField("Mật khẩu mới (ít nhất 8 ký tự)");
        box.addView(current);
        box.addView(next);
        new AlertDialog.Builder(this)
                .setTitle("Đổi mật khẩu")
                .setView(box)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đổi mật khẩu", (d, w) -> {
                    String cur = current.getText().toString();
                    String nw = next.getText().toString();
                    if (cur.isEmpty() || nw.length() < 8) {
                        toast("Nhập mật khẩu hiện tại và mật khẩu mới từ 8 ký tự");
                        return;
                    }
                    busy(true);
                    call(api().changePassword(new Requests.ChangePassword(cur, nw)), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast("Đã đổi mật khẩu. Vui lòng đăng nhập lại");
                            Session.clear();
                            toLogin();
                        }

                        @Override
                        public void onError(ApiError e) {
                            busy(false);
                            showError(e);
                        }
                    });
                })
                .show();
    }

    private EditText passwordField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void deleteAccount() {
        busy(true);
        call(api().deleteAccount(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                busy(false);
                Session.clear();
                toast("Đã xóa tài khoản");
                toLogin();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }
}

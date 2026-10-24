package com.example.project_final;

import android.net.Uri;
import android.widget.EditText;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.User;
import com.example.project_final.util.Forms;
import com.example.project_final.util.ImagePicker;
import com.example.project_final.util.Img;
import com.example.project_final.util.Uploads;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;

public class EditProfileActivity extends BaseActivity {
    private String province;
    private String role = "USER";
    private Uri newAvatar;
    private User current;
    private ImagePicker picker;
    private EditText name, phone, district, bio;

    @Override
    protected int layout() {
        return R.layout.activity_edit_profile;
    }

    @Override
    protected void onReady() {
        name = findViewById(R.id.input_name);
        phone = findViewById(R.id.input_phone);
        district = findViewById(R.id.input_district);
        bio = findViewById(R.id.input_bio);

        picker = new ImagePicker(this, 1, uris -> {
            newAvatar = uris.get(0);
            ((android.widget.ImageView) findViewById(R.id.avatar_img)).setImageURI(newAvatar);
        });
        findViewById(R.id.btn_avatar).setOnClickListener(v -> picker.show());
        findViewById(R.id.pick_province).setOnClickListener(v -> withMeta(o -> Ui.chooser(this, "Chọn tỉnh/thành", o.provinces, (i, label) -> {
            province = label;
            setText(R.id.value_province, label);
        })));
        findViewById(R.id.pick_role).setOnClickListener(v -> withMeta(this::chooseRole));
        done(R.id.btn_cancel);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                current = u;
                prefill(u);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void prefill(User u) {
        name.setText(u.fullName);
        phone.setText(u.phone);
        district.setText(u.district);
        bio.setText(u.bio);
        setText(R.id.email_text, u.email);
        province = u.province;
        setText(R.id.value_province, province == null || province.isEmpty() ? "Chọn tỉnh/thành" : province);
        role = u.role == null ? "USER" : u.role;
        setText(R.id.value_role, com.example.project_final.util.Look.role(role));
        Img.avatar(findViewById(R.id.avatar_img), u.avatarUrl);
    }

    private void chooseRole(MetaOptions o) {
        List<String> labels = new ArrayList<>();
        // Vai trò "Chuyên gia" chỉ do quản trị xác minh, nhưng người dùng vẫn có thể tự khai báo nghề nghiệp.
        for (Option r : o.roles) labels.add(r.label);
        Ui.chooser(this, "Chọn vai trò", labels, (i, label) -> {
            role = o.roles.get(i).code;
            ((TextView) findViewById(R.id.value_role)).setText(label);
        });
    }

    private void save() {
        if (!Forms.require(name, "Họ và tên không được để trống")) return;
        Requests.Profile body = new Requests.Profile();
        body.fullName = Forms.text(name);
        body.phone = Forms.text(phone);
        body.province = province == null ? "" : province;
        body.district = Forms.text(district);
        body.role = role;
        body.bio = Forms.text(bio);
        busy(true);
        call(api().updateProfile(body), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                Session.setUser(u);
                if (newAvatar == null) {
                    busy(false);
                    toast("Đã lưu thay đổi");
                    finish();
                } else {
                    uploadAvatar();
                }
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("fullName", name, "phone", phone, "bio", bio))) showError(e);
            }
        });
    }

    private void uploadAvatar() {
        new Thread(() -> {
            MultipartBody.Part part;
            try {
                part = Uploads.imagePart(this, newAvatar, "file");
            } catch (IOException e) {
                runOnUiThread(() -> {
                    busy(false);
                    toast("Đã lưu hồ sơ nhưng không đọc được ảnh đại diện");
                    finish();
                });
                return;
            }
            runOnUiThread(() -> call(api().uploadAvatar(part), new ApiCallback<User>() {
                @Override
                public void onSuccess(User u) {
                    busy(false);
                    Session.setUser(u);
                    toast("Đã lưu thay đổi");
                    finish();
                }

                @Override
                public void onError(ApiError e) {
                    busy(false);
                    toast("Đã lưu hồ sơ nhưng chưa tải được ảnh đại diện: " + e.message);
                    finish();
                }
            }));
        }).start();
    }
}

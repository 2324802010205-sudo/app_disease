package com.example.project_final;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.RiceField;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Forms;
import com.example.project_final.util.ImagePicker;
import com.example.project_final.util.Uploads;

import java.io.IOException;
import java.util.Calendar;
import java.util.Map;

import okhttp3.MultipartBody;

/** Thêm ruộng mới, hoặc sửa ruộng khi được mở kèm EXTRA_ID. */
public class AddFieldActivity extends BaseActivity {
    private String editId;
    private String variety, stage, plantingDate, province, district;
    private Uri photo;
    private EditText name, area, note;
    private TextView ddVariety, ddStage, ddDate;
    private ImagePicker picker;

    @Override
    protected int layout() {
        return R.layout.activity_add_field;
    }

    @Override
    protected void onReady() {
        editId = getIntent().getStringExtra(EXTRA_ID);
        setText(R.id.screen_title, editId == null ? "Thêm ruộng mới" : "Chỉnh sửa ruộng");
        name = findViewById(R.id.input_name);
        area = findViewById(R.id.input_area);
        note = findViewById(R.id.input_note);
        ddVariety = findViewById(R.id.dd_variety);
        ddStage = findViewById(R.id.dd_stage);
        ddDate = findViewById(R.id.dd_date);

        withMeta(this::bindOptions);

        ddDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (p, y, m, d) -> setDate(Fmt.toIso(y, m, d)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        findViewById(R.id.btn_location).setOnClickListener(v -> chooseLocation());

        picker = new ImagePicker(this, 1, uris -> {
            photo = uris.get(0);
            setText(R.id.photo_title, "Đã chọn 1 ảnh ruộng");
            setText(R.id.photo_desc, "Chạm để đổi ảnh khác");
        });
        findViewById(R.id.btn_photo).setOnClickListener(v -> picker.show());

        TextView counter = findViewById(R.id.counter);
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { counter.setText(s.length() + "/200"); }
        });

        done(R.id.btn_cancel);
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        if (editId != null) {
            setText(R.id.screen_title, "Chỉnh sửa ruộng");
            call(api().field(editId), new ApiCallback<RiceField.Detail>() {
                @Override
                public void onSuccess(RiceField.Detail d) {
                    prefill(d.field);
                }

                @Override
                public void onError(ApiError e) {
                    showError(e);
                    finish();
                }
            });
        }
    }

    private void bindOptions(MetaOptions o) {
        Ui.picker(ddVariety, "Chọn giống lúa", o.varieties, (i, label) -> variety = label);
        Ui.picker(ddStage, "Chọn giai đoạn", o.stages, (i, label) -> stage = label);
    }

    private void setDate(String iso) {
        plantingDate = iso;
        ddDate.setText(Fmt.isoDate(iso));
        ddDate.setTextColor(getColor(R.color.ink_deep));
    }

    private void prefill(RiceField f) {
        name.setText(f.name);
        if (f.areaHa > 0) area.setText(Fmt.areaNumber(f.areaHa).replace(',', '.'));
        note.setText(f.note);
        if (f.variety != null && !f.variety.isEmpty()) {
            variety = f.variety;
            ddVariety.setText(f.variety);
            ddVariety.setTextColor(getColor(R.color.ink_deep));
        }
        if (f.stage != null && !f.stage.isEmpty()) {
            stage = f.stage;
            ddStage.setText(f.stage);
            ddStage.setTextColor(getColor(R.color.ink_deep));
        }
        if (f.plantingDate != null && !f.plantingDate.isEmpty()) setDate(f.plantingDate);
        province = f.province;
        district = f.district;
        showLocation();
    }

    /** Hai bước: chọn tỉnh/thành, rồi nhập huyện/xã (không bắt buộc). */
    private void chooseLocation() {
        withMeta(o -> Ui.chooser(this, "Chọn tỉnh/thành", o.provinces, (i, label) -> {
            province = label;
            EditText input = new EditText(this);
            input.setHint("Huyện / xã (không bắt buộc)");
            input.setText(district);
            int pad = (int) Ui.dp(this, 20);
            android.widget.FrameLayout box = new android.widget.FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(input);
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setView(box)
                    .setPositiveButton("Xong", (d, w) -> {
                        district = Forms.orNull(input);
                        showLocation();
                    })
                    .setNegativeButton("Bỏ qua", (d, w) -> showLocation())
                    .show();
        }));
    }

    private void showLocation() {
        if (province == null || province.isEmpty()) return;
        setText(R.id.location_title, "Đã chọn vị trí");
        setText(R.id.location_desc, (district == null || district.isEmpty() ? "" : district + ", ") + province);
        setText(R.id.location_action, "Đổi");
    }

    private void save() {
        if (!Forms.require(name, "Nhập tên ruộng để lưu")) return;
        Requests.FieldBody b = new Requests.FieldBody();
        b.name = Forms.text(name);
        String areaText = Forms.text(area).replace(',', '.');
        if (!areaText.isEmpty()) {
            try {
                b.areaHa = Double.parseDouble(areaText);
            } catch (NumberFormatException e) {
                area.setError("Diện tích không hợp lệ");
                area.requestFocus();
                return;
            }
        }
        b.variety = variety;
        b.stage = stage;
        b.plantingDate = plantingDate;
        b.province = province;
        b.district = district;
        b.note = Forms.orNull(note);

        busy(true);
        retrofit2.Call<RiceField> request = editId == null ? api().createField(b) : api().updateField(editId, b);
        call(request, new ApiCallback<RiceField>() {
            @Override
            public void onSuccess(RiceField f) {
                if (photo == null) {
                    busy(false);
                    toast("Đã lưu " + f.name);
                    finish();
                } else {
                    uploadPhoto(f);
                }
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("name", name, "areaHa", area, "note", note))) showError(e);
            }
        });
    }

    private void uploadPhoto(RiceField f) {
        new Thread(() -> {
            MultipartBody.Part part;
            try {
                part = Uploads.imagePart(this, photo, "file");
            } catch (IOException e) {
                runOnUiThread(() -> {
                    busy(false);
                    toast("Đã lưu ruộng nhưng không đọc được ảnh: " + e.getMessage());
                    finish();
                });
                return;
            }
            runOnUiThread(() -> call(api().uploadFieldPhoto(f.id, part), new ApiCallback<RiceField>() {
                @Override
                public void onSuccess(RiceField body) {
                    busy(false);
                    toast("Đã lưu " + f.name);
                    finish();
                }

                @Override
                public void onError(ApiError e) {
                    busy(false);
                    toast("Đã lưu ruộng nhưng chưa tải được ảnh: " + e.message);
                    finish();
                }
            }));
        }).start();
    }
}

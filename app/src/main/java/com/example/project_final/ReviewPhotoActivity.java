package com.example.project_final;

import android.content.Intent;
import android.widget.EditText;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.RiceField;
import com.example.project_final.util.Forms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Xem lại ảnh vừa chụp/chọn, chọn ruộng và ghi chú rồi bắt đầu phân tích. */
public class ReviewPhotoActivity extends BaseActivity {
    public static final String EXTRA_PATH = "photoPath";

    private String path;
    private String fieldId;
    private final List<RiceField> fields = new ArrayList<>();

    @Override
    protected int layout() {
        return R.layout.activity_review;
    }

    @Override
    protected void onReady() {
        path = getIntent().getStringExtra(EXTRA_PATH);
        fieldId = getIntent().getStringExtra(ScanActivity.EXTRA_FIELD_ID);
        if (path == null || !new File(path).exists()) {
            toast("Không tìm thấy ảnh, vui lòng chọn lại");
            finish();
            return;
        }
        com.bumptech.glide.Glide.with(this).load(new File(path)).into((android.widget.ImageView) findViewById(R.id.photo));

        findViewById(R.id.btn_zoom).setOnClickListener(v ->
                startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_PATH, path)));
        done(R.id.btn_retake);
        done(R.id.btn_replace);
        findViewById(R.id.btn_choose_field).setOnClickListener(v -> chooseField());
        findViewById(R.id.btn_analyze).setOnClickListener(v -> analyze());
        showField();
        loadFields();
    }

    private void loadFields() {
        call(api().fields("updated"), new ApiCallback<List<RiceField>>() {
            @Override
            public void onSuccess(List<RiceField> list) {
                fields.clear();
                fields.addAll(list);
                showField();
            }

            @Override
            public void onError(ApiError e) {
                // vẫn có thể phân tích mà không gắn ruộng
            }
        });
    }

    private void showField() {
        String name = "Không gắn ruộng";
        for (RiceField f : fields) if (f.id.equals(fieldId)) name = f.name;
        setText(R.id.field_name, name);
    }

    private void chooseField() {
        if (fields.isEmpty()) {
            toast("Bạn chưa có ruộng nào. Thêm ruộng trong mục Ruộng lúa");
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("Không gắn ruộng");
        for (RiceField f : fields) labels.add(f.name);
        Ui.chooser(this, "Chọn ruộng", labels, (i, label) -> {
            fieldId = i == 0 ? null : fields.get(i - 1).id;
            showField();
        });
    }

    private void analyze() {
        String note = Forms.text((EditText) findViewById(R.id.input_note));
        Intent it = new Intent(this, AnalyzingActivity.class)
                .putExtra(EXTRA_PATH, path)
                .putExtra(ScanActivity.EXTRA_FIELD_ID, fieldId)
                .putExtra(AnalyzingActivity.EXTRA_NOTE, note);
        startActivity(it);
        finish();
    }
}

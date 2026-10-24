package com.example.project_final;

import android.content.Intent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/** Chi tiết một lượt quét trong lịch sử: kết quả, thông tin liên quan, ghi chú, so sánh, xóa. */
public class ResultDetailActivity extends BaseActivity {
    private String id;
    private Scan scan;

    @Override
    protected int layout() {
        return R.layout.activity_result_detail;
    }

    @Override
    protected void onReady() {
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            if (scan != null) startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_URL, scan.displayImage()));
        });
        go(R.id.btn_library, LibraryActivity.class);
        findViewById(R.id.btn_rescan).setOnClickListener(v -> startActivity(new Intent(this, ScanActivity.class)
                .putExtra(ScanActivity.EXTRA_FIELD_ID, scan == null ? null : scan.fieldId)));
        findViewById(R.id.btn_compare).setOnClickListener(v -> open(CompareActivity.class, id));
        findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        load();
    }

    private void load() {
        call(api().scan(id), new ApiCallback<Scan>() {
            @Override
            public void onSuccess(Scan s) {
                bind(s);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void bind(Scan s) {
        scan = s;
        Img.load(findViewById(R.id.hero), s.displayImage());
        setText(R.id.disease_name, HomeActivity.scanTitle(s));
        setText(R.id.disease_desc, s.isDiagnosed() && s.summary != null ? s.summary : s.needsRetake() && s.retakeAdvice != null ? s.retakeAdvice : "");
        setText(R.id.confidence_text, s.isDiagnosed() ? s.confidencePercent() + "%" : "--");
        Look.scanPill(findViewById(R.id.pill_risk), s);
        setText(R.id.scan_time, Fmt.dateTime(s.createdAt));
        setText(R.id.field_label, s.fieldName == null ? "Không gắn ruộng" : s.fieldName);
        findViewById(R.id.row_field).setOnClickListener(s.fieldId == null ? null : v -> open(FieldDetailActivity.class, s.fieldId));

        LinearLayout summary = findViewById(R.id.list_summary);
        summary.removeAllViews();
        List<String> lines = new ArrayList<>(s.symptoms == null ? List.of() : s.symptoms);
        if (lines.isEmpty()) Ui.emptyNote(summary, "Chưa có tóm tắt cho lượt quét này", null);
        else Ui.fill(summary, R.layout.item_check_line, lines.size(), (item, i) -> ((TextView) item).setText(lines.get(i)));

        bindRelated(s);
    }

    private void bindRelated(Scan s) {
        LinearLayout box = findViewById(R.id.list_related);
        box.removeAllViews();
        boolean hasNote = s.note != null && !s.note.isEmpty();
        String weather = s.weather == null ? "—" : Math.round(s.weather.tempC) + "°C";
        String weatherNote = s.weather == null ? "Chưa có dữ liệu" : s.weather.condition + ", độ ẩm " + s.weather.humidity + "%";
        String[] label = {"Thời tiết lúc quét", "Giai đoạn lúa", "Ghi chú"};
        String[] value = {weather, s.stage == null || s.stage.isEmpty() ? "—" : s.stage, hasNote ? s.note : "Không có ghi chú"};
        String[] note = {weatherNote, "", hasNote ? "Sửa ghi chú" : "Thêm ghi chú"};
        int[] icon = {R.drawable.ic_w_rain, R.drawable.ic_sprout, R.drawable.ic_description};
        Ui.fill(box, R.layout.item_related, 3, (item, i) -> {
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(i == 0 && s.weather != null ? Look.weatherIconFromLabel(s.weather.condition) : icon[i]);
            if (i == 1) img.setColorFilter(getColor(R.color.primary));
            if (i == 2) img.setColorFilter(getColor(R.color.ink));
            Ui.text(item, R.id.label, label[i]);
            Ui.text(item, R.id.value, value[i]);
            TextView n = item.findViewById(R.id.note);
            n.setText(note[i]);
            if (i == 2) {
                n.setTextColor(getColor(R.color.primary));
                n.setTypeface(null, android.graphics.Typeface.BOLD);
                item.setOnClickListener(v -> editNote());
            }
        });
    }

    private void editNote() {
        EditText input = new EditText(this);
        input.setText(scan.note);
        input.setHint("Ghi chú về lần quét này");
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        int pad = (int) Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Ghi chú")
                .setView(box)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", (d, w) -> {
                    busy(true);
                    call(api().updateScanNote(id, new Requests.Note(input.getText().toString().trim())), new ApiCallback<Scan>() {
                        @Override
                        public void onSuccess(Scan s) {
                            busy(false);
                            bind(s);
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

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bản ghi?")
                .setMessage("Kết quả quét này và ảnh đi kèm sẽ bị xóa khỏi lịch sử.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    busy(true);
                    call(api().deleteScan(id), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast("Đã xóa bản ghi");
                            finish();
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
}

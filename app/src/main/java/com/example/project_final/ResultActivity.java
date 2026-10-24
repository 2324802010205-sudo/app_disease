package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;
import com.google.gson.Gson;

import java.util.List;

/** Kết quả ngay sau khi quét. Nhận EXTRA_SCAN_JSON (từ màn phân tích) hoặc EXTRA_ID (mở lại). */
public class ResultActivity extends BaseActivity {
    public static final String EXTRA_SCAN_JSON = "scanJson";

    private Scan scan;

    @Override
    protected int layout() {
        return R.layout.activity_result;
    }

    @Override
    protected void onReady() {
        findViewById(R.id.btn_save).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            if (scan != null) startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_URL, scan.displayImage()));
        });

        String json = getIntent().getStringExtra(EXTRA_SCAN_JSON);
        if (json != null) {
            bind(new Gson().fromJson(json, Scan.class));
            return;
        }
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
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
        Img.load(findViewById(R.id.thumb), s.displayImage());
        setText(R.id.disease_name, HomeActivity.scanTitle(s));
        Look.scanPill(findViewById(R.id.pill_risk), s);
        setText(R.id.disease_desc, s.summary == null ? "" : s.summary);
        int pct = s.confidencePercent();
        ((ProgressBar) findViewById(R.id.confidence_ring)).setProgress(pct);
        setText(R.id.confidence_text, s.isDiagnosed() ? pct + "%" : "--");
        setText(R.id.scan_time, Fmt.dateTime(s.createdAt));
        setText(R.id.scan_stage, empty(s.stage));
        setText(R.id.scan_variety, empty(s.variety));
        setText(R.id.field_label, s.fieldName == null ? "Không gắn ruộng" : s.fieldName);

        bindSymptoms(s.symptoms);
        bindAdvice(s);
        bindState(s);

        View rowField = findViewById(R.id.row_field);
        if (s.fieldId != null) rowField.setOnClickListener(v -> open(FieldDetailActivity.class, s.fieldId));
        else rowField.setOnClickListener(null);

        findViewById(R.id.btn_feedback).setOnClickListener(v -> open(FeedbackActivity.class, s.id));
        TextView related = findViewById(R.id.btn_related);
        boolean disease = s.isDiagnosed() && s.diseaseCode != null && !"healthy".equals(s.diseaseCode);
        related.setVisibility(disease ? View.VISIBLE : View.GONE);
        related.setOnClickListener(v -> open(DiseaseDetailActivity.class, s.diseaseCode));
    }

    private void bindSymptoms(List<String> symptoms) {
        LinearLayout box = findViewById(R.id.list_symptoms);
        box.removeAllViews();
        if (symptoms == null || symptoms.isEmpty()) {
            Ui.emptyNote(box, "Chưa có triệu chứng được ghi nhận", null);
            return;
        }
        Ui.fill(box, R.layout.item_symptom, symptoms.size(), (item, i) -> {
            Ui.text(item, R.id.num, String.valueOf(i + 1));
            Ui.text(item, R.id.text, symptoms.get(i));
        });
    }

    private void bindAdvice(Scan s) {
        LinearLayout box = findViewById(R.id.list_advice);
        box.removeAllViews();
        if (s.recommendations == null || s.recommendations.isEmpty()) return;
        Ui.fill(box, R.layout.item_advice, s.recommendations.size(), (item, i) -> {
            Scan.Recommendation r = s.recommendations.get(i);
            boolean water = "WATER".equals(r.icon);
            item.findViewById(R.id.icon_bg).setBackgroundResource(water ? R.drawable.bg_icon_blue : R.drawable.bg_icon_tint);
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(Look.recommendationIcon(r.icon));
            img.setColorFilter(getColor(water ? R.color.blue : R.color.primary));
            Ui.text(item, R.id.title, r.title);
            Ui.text(item, R.id.desc, r.description);
        });
    }

    /** Ảnh cần chụp lại hoặc AI chưa trả lời: giải thích và cho thử lại. */
    private void bindState(Scan s) {
        View box = findViewById(R.id.state_box);
        TextView retry = findViewById(R.id.btn_retry);
        if (s.isDiagnosed()) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        if (s.needsRetake()) {
            setText(R.id.state_title, "Cần chụp lại ảnh");
            setText(R.id.state_text, s.retakeAdvice == null ? "Ảnh chưa đủ rõ để kết luận." : s.retakeAdvice);
            retry.setText("Chụp lại");
            retry.setOnClickListener(v -> {
                startActivity(new Intent(this, ScanActivity.class).putExtra(ScanActivity.EXTRA_FIELD_ID, s.fieldId));
                finish();
            });
        } else {
            setText(R.id.state_title, "Đang chờ phân tích");
            setText(R.id.state_text, s.summary == null ? "Ảnh đã được lưu. Kết quả sẽ có khi dịch vụ AI sẵn sàng." : s.summary);
            retry.setText("Thử phân tích lại");
            retry.setOnClickListener(v -> rediagnose(s.id));
        }
    }

    private void rediagnose(String id) {
        busy(true);
        call(api().diagnose(id), new ApiCallback<Scan>() {
            @Override
            public void onSuccess(Scan s) {
                busy(false);
                bind(s);
                if (s.isPending()) toast("AI vẫn chưa sẵn sàng, vui lòng thử lại sau");
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }

    private static String empty(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}

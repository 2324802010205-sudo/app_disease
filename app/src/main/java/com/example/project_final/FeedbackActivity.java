package com.example.project_final;

import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.Disease;
import com.example.project_final.model.ImageRef;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Forms;
import com.example.project_final.util.Img;
import com.example.project_final.util.ImagePicker;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/** Gửi phản hồi về một kết quả AI để cải thiện mô hình. */
public class FeedbackActivity extends BaseActivity {
    private static final int MAX_ATTACHMENTS = 5;

    private final List<View> reasonViews = new ArrayList<>();
    private final List<Option> reasons = new ArrayList<>();
    private final List<Disease> diseases = new ArrayList<>();
    private final List<Uri> attachments = new ArrayList<>();
    private String scanId;
    private int selected;
    private String suggestedCode;
    private ImagePicker picker;

    @Override
    protected int layout() {
        return R.layout.activity_feedback;
    }

    @Override
    protected void onReady() {
        scanId = getIntent().getStringExtra(EXTRA_ID);
        if (scanId == null) {
            finish();
            return;
        }
        call(api().scan(scanId), new ApiCallback<Scan>() {
            @Override
            public void onSuccess(Scan s) {
                Img.load(findViewById(R.id.scan_thumb), s.displayImage());
                setText(R.id.scan_title, HomeActivity.scanTitle(s));
                Look.scanPill(findViewById(R.id.scan_pill), s);
                setText(R.id.scan_time, "Phân tích lúc " + Fmt.dateTime(s.createdAt));
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });

        withMeta(this::bindReasons);
        loadDiseases();

        EditText desc = findViewById(R.id.input_desc);
        TextView counter = findViewById(R.id.counter);
        desc.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { counter.setText(s.length() + "/500"); }
        });

        picker = new ImagePicker(this, MAX_ATTACHMENTS, uris -> {
            for (Uri u : uris) if (attachments.size() < MAX_ATTACHMENTS) attachments.add(u);
            setText(R.id.attach_title, attachments.size() + " ảnh đã chọn");
            setText(R.id.attach_desc, "Chạm để thêm hoặc chọn lại (tối đa " + MAX_ATTACHMENTS + " ảnh).");
        });
        findViewById(R.id.btn_attach).setOnClickListener(v -> {
            if (attachments.size() >= MAX_ATTACHMENTS) attachments.clear();
            picker.show();
        });
        done(R.id.btn_cancel);
        findViewById(R.id.btn_send).setOnClickListener(v -> send());
    }

    private void loadDiseases() {
        call(api().diseases(null, null, null, null), new ApiCallback<List<Disease>>() {
            @Override
            public void onSuccess(List<Disease> list) {
                diseases.addAll(list);
                List<String> names = new ArrayList<>();
                for (Disease d : list) names.add(d.name);
                Ui.picker(findViewById(R.id.dd_disease), "Chọn bệnh phù hợp", names, (i, label) -> suggestedCode = diseases.get(i).id);
            }

            @Override
            public void onError(ApiError e) {
                // bỏ qua: đề xuất bệnh là tùy chọn
            }
        });
    }

    private void bindReasons(MetaOptions o) {
        reasons.clear();
        reasons.addAll(o.feedbackReasons);
        LinearLayout grid = findViewById(R.id.grid_reasons);
        grid.removeAllViews();
        reasonViews.clear();
        LinearLayout row = null;
        for (int i = 0; i < reasons.size(); i++) {
            if (i % 2 == 0) {
                row = (LinearLayout) getLayoutInflater().inflate(R.layout.item_reason_row, grid, false);
                grid.addView(row);
            }
            View item = getLayoutInflater().inflate(R.layout.item_reason, row, false);
            Ui.text(item, R.id.label, reasons.get(i).label);
            final int idx = i;
            item.setOnClickListener(v -> select(idx));
            reasonViews.add(item);
            row.addView(item);
        }
        select(0);
    }

    private static int reasonIcon(String code) {
        if ("UNCLEAR_IMAGE".equals(code)) return R.drawable.ic_photo_outline;
        if ("MISSING_INFO".equals(code)) return R.drawable.ic_description;
        if ("NEED_ADVICE".equals(code)) return R.drawable.ic_help;
        return R.drawable.ic_check;
    }

    /** Chỉ chọn một lý do: thẻ đang chọn có viền xanh và dấu tích. */
    private void select(int index) {
        selected = index;
        for (int i = 0; i < reasonViews.size(); i++) {
            View item = reasonViews.get(i);
            boolean sel = i == index;
            item.setBackgroundResource(sel ? R.drawable.bg_option_selected : R.drawable.bg_option);
            FrameLayout bg = item.findViewById(R.id.icon_bg);
            ImageView icon = item.findViewById(R.id.icon);
            bg.setBackgroundResource(sel ? R.drawable.bg_circle_primary : R.drawable.bg_circle_tint);
            icon.setImageResource(sel ? R.drawable.ic_check : reasonIcon(reasons.get(i).code));
            icon.setColorFilter(getColor(sel ? R.color.white : R.color.ink_deep));
        }
    }

    private void send() {
        if (reasons.isEmpty()) {
            toast("Chưa tải được danh sách lý do, vui lòng thử lại");
            return;
        }
        busy(true);
        uploadImages(attachments, "feedback", new com.example.project_final.util.ImageUploader.Callback() {
            @Override
            public void onDone(List<ImageRef> refs) {
                submit(refs);
            }

            @Override
            public void onError(String message) {
                busy(false);
                toast(message);
            }
        });
    }

    private void submit(List<ImageRef> uploaded) {
        Requests.Feedback body = new Requests.Feedback();
        body.reason = reasons.get(selected).code;
        body.suggestedDiseaseCode = suggestedCode;
        body.description = Forms.orNull((EditText) findViewById(R.id.input_desc));
        body.attachments = uploaded.isEmpty() ? null : uploaded;
        call(api().feedback(scanId, body), new ApiCallback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> r) {
                busy(false);
                toast("Đã gửi phản hồi. Cảm ơn bạn!");
                finish();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }
}

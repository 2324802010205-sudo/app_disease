package com.example.project_final;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Page;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.List;

/** So sánh hai lần quét (mặc định: lần đang xem với lần liền trước cùng ruộng). */
public class CompareActivity extends BaseActivity {
    private final List<Scan> candidates = new ArrayList<>();
    private String firstId;
    private String secondId;

    @Override
    protected int layout() {
        return R.layout.activity_compare;
    }

    @Override
    protected void onReady() {
        firstId = getIntent().getStringExtra(EXTRA_ID);
        if (firstId == null) {
            finish();
            return;
        }
        findViewById(R.id.pick_1).setOnClickListener(v -> choose(true));
        findViewById(R.id.pick_2).setOnClickListener(v -> choose(false));
        findViewById(R.id.btn_other).setOnClickListener(v -> choose(false));

        call(api().scan(firstId), new ApiCallback<Scan>() {
            @Override
            public void onSuccess(Scan s) {
                loadCandidates(s);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    /** Lấy các lượt quét cùng ruộng (hoặc gần nhất nếu không gắn ruộng) để chọn đối chiếu. */
    private void loadCandidates(Scan first) {
        call(api().scans("all", null, first.fieldId, null, 50), new ApiCallback<Page<Scan>>() {
            @Override
            public void onSuccess(Page<Scan> page) {
                candidates.clear();
                for (Scan s : page.items) if (s.isDiagnosed()) candidates.add(s);
                if (secondId == null) {
                    // mặc định: lần quét ngay trước lần đang xem; không có thì lần ngay sau
                    Scan pick = null;
                    for (Scan s : candidates) if (!s.id.equals(firstId) && s.createdAt < first.createdAt) { pick = s; break; }
                    if (pick == null) for (Scan s : candidates) if (!s.id.equals(firstId)) pick = s;
                    if (pick != null) secondId = pick.id;
                }
                if (secondId == null) {
                    toast("Cần ít nhất hai lần quét đã có kết quả để so sánh");
                    finish();
                    return;
                }
                if (first.fieldId != null) bindField(first.fieldId);
                compare();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void bindField(String fieldId) {
        call(api().field(fieldId), new ApiCallback<RiceField.Detail>() {
            @Override
            public void onSuccess(RiceField.Detail d) {
                Img.load(findViewById(R.id.field_thumb), d.field.photoUrl, R.drawable.ph_field);
                setText(R.id.field_name, d.field.name);
                setText(R.id.field_place, FieldsActivity.place(d.field));
                findViewById(R.id.row_field).setOnClickListener(v -> open(FieldDetailActivity.class, fieldId));
            }

            @Override
            public void onError(ApiError e) {
                // thông tin ruộng chỉ là phần đầu trang
            }
        });
    }

    private void choose(boolean first) {
        if (candidates.isEmpty()) return;
        List<String> labels = new ArrayList<>();
        for (Scan s : candidates) labels.add(Fmt.dateTime(s.createdAt) + " – " + HomeActivity.scanTitle(s));
        Ui.chooser(this, first ? "Chọn lần quét thứ nhất" : "Chọn lần quét thứ hai", labels, (i, label) -> {
            String id = candidates.get(i).id;
            if (first) firstId = id;
            else secondId = id;
            if (firstId.equals(secondId)) {
                toast("Hãy chọn hai lần quét khác nhau");
                return;
            }
            compare();
        });
    }

    private void compare() {
        busy(true);
        call(api().compare(firstId, secondId), new ApiCallback<Scan.Comparison>() {
            @Override
            public void onSuccess(Scan.Comparison c) {
                busy(false);
                bind(c);
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }

    private void bind(Scan.Comparison c) {
        // backend luôn trả lần cũ hơn là first
        firstId = c.first.id;
        secondId = c.second.id;
        setText(R.id.date_1, Fmt.dateLong(c.first.createdAt));
        setText(R.id.date_2, Fmt.dateLong(c.second.createdAt));

        LinearLayout cards = findViewById(R.id.cards);
        cards.removeAllViews();
        Scan[] pair = {c.first, c.second};
        Ui.fill(cards, R.layout.item_compare_card, 2, (item, i) -> {
            Scan s = pair[i];
            Ui.text(item, R.id.title, i == 0 ? "Lần 1" : "Lần 2");
            Ui.text(item, R.id.date, Fmt.dateTime(s.createdAt));
            Ui.text(item, R.id.disease, HomeActivity.scanTitle(s));
            Ui.text(item, R.id.confidence, "Độ tin cậy " + s.confidencePercent() + "%");
            Img.load(item.findViewById(R.id.img), s.displayImage(), R.drawable.bg_image_placeholder);
            Ui.text(item, R.id.row_stage, "Giai đoạn      " + dash(s.stage));
            Ui.text(item, R.id.row_variety, "Giống lúa      " + dash(s.variety));
            Ui.text(item, R.id.row_place, "Vị trí quét      " + dash(s.fieldName));
            if (i == 1) ((LinearLayout.LayoutParams) item.getLayoutParams()).setMarginStart((int) Ui.dp(this, 8));
        });

        String[] label = {"Kết quả", "Ngày quét", "Mức độ", "Ghi chú\ntheo dõi"};
        String[] left = {
                HomeActivity.scanTitle(c.first) + " (" + c.first.confidencePercent() + "%)",
                Fmt.dateTime(c.first.createdAt),
                severity(c.first.severity),
                c.first.note == null || c.first.note.isEmpty() ? "—" : c.first.note};
        String[] right = {
                HomeActivity.scanTitle(c.second) + " (" + c.second.confidencePercent() + "%)",
                Fmt.dateTime(c.second.createdAt),
                severity(c.second.severity),
                c.second.note == null || c.second.note.isEmpty() ? "—" : c.second.note};
        int[] icon = {R.drawable.ic_eco, R.drawable.ic_calendar, R.drawable.ic_warning, R.drawable.ic_description};
        LinearLayout rows = findViewById(R.id.list_compare);
        rows.removeAllViews();
        Ui.fill(rows, R.layout.item_compare_row, 4, (item, i) -> {
            Ui.text(item, R.id.left, left[i]);
            Ui.text(item, R.id.label, label[i]);
            Ui.text(item, R.id.right, right[i]);
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(icon[i]);
        });

        setText(R.id.compare_note, trendText(c));
    }

    private static String trendText(Scan.Comparison c) {
        String gap = c.daysBetween <= 0 ? "trong cùng ngày" : "cách nhau " + c.daysBetween + " ngày";
        switch (c.trend == null ? "" : c.trend) {
            case "IMPROVING":
                return "Hai lần quét " + gap + ". Mức độ bệnh đang giảm so với lần trước, hãy tiếp tục chăm sóc và theo dõi.";
            case "WORSENING":
                return "Hai lần quét " + gap + ". Bệnh có dấu hiệu nặng hơn, nên xử lý sớm theo khuyến nghị và hỏi cán bộ bảo vệ thực vật.";
            case "STABLE":
                return "Hai lần quét " + gap + ". Mức độ bệnh chưa thay đổi, hãy tiếp tục theo dõi định kỳ.";
            default:
                return "Hai lần quét " + gap + " cho kết quả khác nhau. Hãy chụp thêm ảnh rõ nét để theo dõi diễn biến.";
        }
    }

    private static String severity(String s) {
        if (s == null) return "—";
        switch (s) {
            case "HIGH":
                return "Nặng";
            case "MEDIUM":
                return "Vừa";
            case "LOW":
                return "Nhẹ";
            default:
                return "Không có";
        }
    }

    private static String dash(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}

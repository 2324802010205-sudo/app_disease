package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Session;
import com.example.project_final.model.Disease;
import com.example.project_final.model.Risk;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/**
 * Bản đồ nguy cơ theo khu vực: số ca phát hiện bệnh (ẩn danh) từ các lượt quét của cộng đồng trong N ngày gần nhất.
 */
public class RiskMapActivity extends BaseActivity {
    private static final String[] RANGE_LABELS = {"7 ngày qua", "14 ngày qua", "30 ngày qua"};
    private static final int[] RANGE_DAYS = {7, 14, 30};
    private static final String[] LEVELS = {"LOW", "MEDIUM", "HIGH", "VERY_HIGH"};

    private final List<Disease> diseases = new ArrayList<>();
    private String province;
    private String disease;
    private int range;
    private boolean byRisk = true;

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_risk_map;
    }

    @Override
    protected void onReady() {
        province = Session.user() == null || Session.user().province == null || Session.user().province.isEmpty() ? null : Session.user().province;
        setText(R.id.value_area, province == null ? "Tất cả tỉnh" : province);
        setText(R.id.value_disease, "Tất cả bệnh");
        setText(R.id.value_time, RANGE_LABELS[range]);

        findViewById(R.id.pick_disease).setOnClickListener(v -> chooseDisease());
        findViewById(R.id.pick_time).setOnClickListener(v -> Ui.chooser(this, "Khoảng thời gian", java.util.Arrays.asList(RANGE_LABELS), (i, label) -> {
            range = i;
            setText(R.id.value_time, label);
            load();
        }));
        findViewById(R.id.pick_area).setOnClickListener(v -> withMeta(o -> {
            List<String> names = new ArrayList<>();
            names.add("Tất cả tỉnh");
            names.addAll(o.provinces);
            Ui.chooser(this, "Chọn tỉnh/thành", names, (i, label) -> {
                province = i == 0 ? null : label;
                setText(R.id.value_area, label);
                load();
            });
        }));
        TextView sort = findViewById(R.id.dd_sort);
        Ui.dropdown(sort, new String[]{"Sắp xếp: Mức nguy cơ", "Sắp xếp: Số lượt ghi nhận"}, i -> {
            byRisk = i == 0;
            load();
        });
        load();
    }

    private void chooseDisease() {
        if (diseases.isEmpty()) {
            call(api().diseases(null, null, null, null), new ApiCallback<List<Disease>>() {
                @Override
                public void onSuccess(List<Disease> list) {
                    diseases.addAll(list);
                    chooseDisease();
                }

                @Override
                public void onError(ApiError e) {
                    showError(e);
                }
            });
            return;
        }
        List<String> names = new ArrayList<>();
        names.add("Tất cả bệnh");
        for (Disease d : diseases) names.add(d.name);
        Ui.chooser(this, "Chọn bệnh", names, (i, label) -> {
            disease = i == 0 ? null : diseases.get(i - 1).id;
            setText(R.id.value_disease, label);
            load();
        });
    }

    private void load() {
        call(api().riskAreas(province, disease, RANGE_DAYS[range], byRisk ? "risk" : "count"), new ApiCallback<List<Risk.Area>>() {
            @Override
            public void onSuccess(List<Risk.Area> list) {
                render(list);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void render(List<Risk.Area> areas) {
        bindLevels(areas);
        LinearLayout list = findViewById(R.id.list_areas);
        list.removeAllViews();
        if (areas.isEmpty()) {
            Ui.emptyNote(list, "Chưa ghi nhận ca bệnh nào trong khoảng thời gian này.\nĐây là tin tốt, hoặc chưa có người dùng quét tại khu vực này.", null);
            return;
        }
        for (Risk.Area a : areas) {
            View item = getLayoutInflater().inflate(R.layout.item_area, list, false);
            ImageView img = item.findViewById(R.id.img);
            img.setImageResource(R.drawable.ic_location_on);
            img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int pad = (int) Ui.dp(this, 14);
            img.setPadding(pad, pad, pad, pad);
            img.setBackgroundResource(Look.areaPillBg(a.level));
            img.setColorFilter(getColor(Look.areaTextColor(a.level)));
            Ui.text(item, R.id.name, a.district + ", " + a.province);
            Ui.text(item, R.id.count, a.cases + " lượt ghi nhận");
            Ui.text(item, R.id.updated, a.lastAt == null ? "Chưa có cập nhật" : "Cập nhật: " + Fmt.dateTime(a.lastAt));
            TextView risk = item.findViewById(R.id.risk);
            Ui.pill(risk, Look.areaLabel(a.level), "LOW".equals(a.level) ? R.drawable.ic_check_circle : R.drawable.ic_warning,
                    Look.areaTextColor(a.level), Look.areaPillBg(a.level));
            View.OnClickListener open = v -> startActivity(new Intent(this, AreaInfoActivity.class)
                    .putExtra(AreaInfoActivity.EXTRA_PROVINCE, a.province)
                    .putExtra(AreaInfoActivity.EXTRA_DISTRICT, a.district)
                    .putExtra(AreaInfoActivity.EXTRA_DAYS, RANGE_DAYS[range]));
            item.findViewById(R.id.btn_info).setOnClickListener(open);
            item.setOnClickListener(open);
            list.addView(item);
        }
    }

    /** Dải tóm tắt: số khu vực theo từng mức nguy cơ. */
    private void bindLevels(List<Risk.Area> areas) {
        LinearLayout box = findViewById(R.id.list_levels);
        box.removeAllViews();
        for (int i = 0; i < LEVELS.length; i++) {
            int count = 0;
            for (Risk.Area a : areas) if (LEVELS[i].equals(a.level)) count++;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(android.view.Gravity.CENTER);
            tile.setBackgroundResource(Look.areaPillBg(LEVELS[i]));
            int p = (int) Ui.dp(this, 10);
            tile.setPadding(p / 2, p, p / 2, p);
            TextView n = new TextView(this);
            n.setText(String.valueOf(count));
            n.setTextColor(getColor(Look.areaTextColor(LEVELS[i])));
            n.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_24));
            n.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView l = new TextView(this);
            l.setText(Look.areaLabel(LEVELS[i]));
            l.setTextColor(getColor(Look.areaTextColor(LEVELS[i])));
            l.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_13));
            l.setGravity(android.view.Gravity.CENTER);
            tile.addView(n);
            tile.addView(l);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.setMarginStart((int) Ui.dp(this, 6));
            box.addView(tile, lp);
        }
    }

}

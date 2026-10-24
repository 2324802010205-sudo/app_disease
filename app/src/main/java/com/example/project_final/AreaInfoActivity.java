package com.example.project_final;

import android.widget.ImageView;
import android.widget.LinearLayout;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Risk;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/** Thông tin chi tiết một khu vực (huyện) trên bản đồ nguy cơ. */
public class AreaInfoActivity extends BaseActivity {
    public static final String EXTRA_PROVINCE = "province";
    public static final String EXTRA_DISTRICT = "district";
    public static final String EXTRA_DAYS = "days";

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_area_info;
    }

    @Override
    protected void onReady() {
        String province = getIntent().getStringExtra(EXTRA_PROVINCE);
        String district = getIntent().getStringExtra(EXTRA_DISTRICT);
        int days = getIntent().getIntExtra(EXTRA_DAYS, 7);
        if (province == null || district == null) {
            finish();
            return;
        }
        findViewById(R.id.btn_scan).setOnClickListener(v -> {
            if (requireAccount("quét và phân tích bệnh lúa")) startActivity(new android.content.Intent(this, ScanActivity.class));
        });
        go(R.id.btn_library, LibraryActivity.class);
        setText(R.id.area_name, district);
        setText(R.id.area_place, district + ", " + province);

        call(api().riskInfo(province, district, days), new ApiCallback<Risk.AreaInfo>() {
            @Override
            public void onSuccess(Risk.AreaInfo info) {
                bind(info);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void bind(Risk.AreaInfo a) {
        android.widget.TextView pill = findViewById(R.id.area_pill);
        com.example.project_final.Ui.pill(pill, "Nguy cơ " + Look.areaLabel(a.level).toLowerCase(),
                "LOW".equals(a.level) ? R.drawable.ic_check_circle : R.drawable.ic_warning, Look.areaTextColor(a.level), Look.areaPillBg(a.level));
        setText(R.id.area_intro, a.cases == 0
                ? "Chưa ghi nhận ca bệnh nào tại " + a.district + " trong " + days(a) + " ngày gần đây."
                : "Có " + a.cases + " lượt ghi nhận bệnh tại " + a.district + ", " + a.province + " trong " + days(a) + " ngày gần đây.");

        StringBuilder names = new StringBuilder();
        for (Risk.DiseaseCount d : a.diseases) {
            if (names.length() > 0) names.append(", ");
            names.append(d.name);
        }
        String[] mLabel = {"Kỳ dữ liệu", "Số lượt ghi nhận", "Các bệnh liên quan", "Cập nhật gần nhất"};
        String[] mValue = {Fmt.isoDate(a.fromDate) + " – " + Fmt.isoDate(a.toDate), a.cases + " lượt",
                names.length() == 0 ? "Chưa có" : names.toString(), a.lastAt == null ? "Chưa có" : Fmt.dateTime(a.lastAt)};
        int[] mIcon = {R.drawable.ic_calendar, R.drawable.ic_description, R.drawable.ic_bug, R.drawable.ic_schedule};
        LinearLayout meta = findViewById(R.id.list_meta);
        meta.removeAllViews();
        Ui.fill(meta, R.layout.item_meta_row, 4, (item, i) -> {
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(mIcon[i]);
            Ui.text(item, R.id.label, mLabel[i]);
            Ui.text(item, R.id.value, mValue[i]);
        });

        List<String[]> rows = new ArrayList<>();
        List<Integer> icons = new ArrayList<>(), bgs = new ArrayList<>(), tints = new ArrayList<>();
        rows.add(new String[]{"Mức độ nguy cơ", Look.areaLabel(a.level) + ". " + (a.advice == null ? "" : a.advice)});
        icons.add(R.drawable.ic_warning); bgs.add(R.drawable.bg_circle_orange); tints.add(R.color.orange);
        if (!a.diseases.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Risk.DiseaseCount d : a.diseases) sb.append(sb.length() > 0 ? "; " : "").append(d.name).append(" (").append(d.cases).append(" ca)");
            rows.add(new String[]{"Bệnh hại chính", sb.toString()});
            icons.add(R.drawable.ic_eco); bgs.add(R.drawable.bg_circle_tint); tints.add(R.color.primary);
        }
        if (a.dominantStage != null && !a.dominantStage.isEmpty()) {
            rows.add(new String[]{"Giai đoạn lúa", a.dominantStage + " (giai đoạn xuất hiện nhiều nhất)"});
            icons.add(R.drawable.ic_sprout); bgs.add(R.drawable.bg_circle_tint); tints.add(R.color.primary);
        }
        if (a.weather != null) {
            rows.add(new String[]{"Điều kiện thời tiết", a.weather.condition.label + ", độ ẩm " + a.weather.humidity + "%, nhiệt độ "
                    + Math.round(a.weather.tempC) + "°C, khả năng mưa " + a.weather.rainProbability + "%."});
            icons.add(Look.weatherIcon(a.weather.condition.icon)); bgs.add(R.drawable.bg_circle_blue); tints.add(0);
        }
        LinearLayout box = findViewById(R.id.list_overview);
        box.removeAllViews();
        Ui.fill(box, R.layout.item_overview_row, rows.size(), (item, i) -> {
            item.findViewById(R.id.icon_bg).setBackgroundResource(bgs.get(i));
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(icons.get(i));
            if (tints.get(i) != 0) img.setColorFilter(getColor(tints.get(i)));
            Ui.text(item, R.id.title, rows.get(i)[0]);
            Ui.text(item, R.id.text, rows.get(i)[1]);
        });
    }

    private static long days(Risk.AreaInfo a) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            long diff = f.parse(a.toDate).getTime() - f.parse(a.fromDate).getTime();
            return diff / 86_400_000L + 1;
        } catch (java.text.ParseException | NullPointerException e) {
            return 7;
        }
    }
}

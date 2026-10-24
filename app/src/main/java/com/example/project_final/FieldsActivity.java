package com.example.project_final;

import android.content.Intent;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.RiceField;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;
import com.example.project_final.view.NavBar;

import java.util.List;

public class FieldsActivity extends BaseActivity {
    private static final String[] SORT_LABELS = {"Mới cập nhật", "Tên ruộng", "Diện tích"};
    private static final String[] SORT_KEYS = {"updated", "name", "area"};
    private int sort;

    @Override
    protected int layout() {
        return R.layout.activity_fields;
    }

    @Override
    protected void onReady() {
        setupBottomNav(NavBar.FIELDS);
        go(R.id.btn_add, AddFieldActivity.class);
        TextView sortView = findViewById(R.id.btn_sort);
        sortView.setText(SORT_LABELS[sort]);
        Ui.dropdown(sortView, SORT_LABELS, i -> {
            sort = i;
            load();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        call(api().fields(SORT_KEYS[sort]), new ApiCallback<List<RiceField>>() {
            @Override
            public void onSuccess(List<RiceField> list) {
                bind(list);
            }

            @Override
            public void onError(ApiError e) {
                LinearLayout box = findViewById(R.id.list_fields);
                box.removeAllViews();
                Ui.emptyNote(box, e.message + "\nChạm để thử lại", FieldsActivity.this::load);
            }
        });
    }

    private void bind(List<RiceField> list) {
        double area = 0;
        for (RiceField f : list) area += f.areaHa;
        setText(R.id.total_fields_n, String.valueOf(list.size()));
        setText(R.id.total_area_n, Fmt.areaNumber(area));
        setText(R.id.list_title, "Danh sách ruộng (" + list.size() + ")");

        LinearLayout box = findViewById(R.id.list_fields);
        box.removeAllViews();
        if (list.isEmpty()) {
            Ui.emptyNote(box, "Bạn chưa có ruộng nào.\nChạm “Thêm ruộng” để bắt đầu theo dõi.", null);
            return;
        }
        Ui.fill(box, R.layout.item_field, list.size(), (item, i) -> {
            RiceField f = list.get(i);
            Img.load(item.findViewById(R.id.img), f.photoUrl, R.drawable.ph_field);
            Ui.text(item, R.id.name, f.name);
            Ui.text(item, R.id.place, place(f));
            Ui.text(item, R.id.area, Fmt.area(f.areaHa));
            Ui.text(item, R.id.variety, f.variety == null || f.variety.isEmpty() ? "—" : f.variety);
            Ui.text(item, R.id.stage, f.stage == null || f.stage.isEmpty() ? "—" : f.stage);
            TextView next = item.findViewById(R.id.next_check);
            next.setText(Fmt.nextCheck(f.nextCheckDate, f.daysUntilCheck));
            next.setTextColor(getColor(f.needsAttention() ? R.color.orange : R.color.ink));
            Look.fieldPill(item.findViewById(R.id.status), f);
            item.setOnClickListener(v -> startActivity(new Intent(this, FieldDetailActivity.class).putExtra(EXTRA_ID, f.id)));
        });
    }

    static String place(RiceField f) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{f.plot, f.district, f.province}) {
            if (part == null || part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(part);
        }
        return sb.length() == 0 ? "Chưa có địa chỉ" : sb.toString();
    }
}

package com.example.project_final;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Page;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/** Lịch sử quét: lọc theo thời gian, mức rủi ro, ruộng; tìm theo tên bệnh/ruộng; tải thêm theo trang. */
public class HistoryActivity extends BaseActivity {
    public static final String EXTRA_FIELD_ID = "fieldId";

    private static final String[] FILTERS = {"Tất cả", "7 ngày", "Nguy cơ cao", "Bình thường", "Tất cả ruộng"};
    private static final int PAGE = 20;

    private final List<Scan> items = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<RiceField> fields = new ArrayList<>();
    private int filter;
    private String fieldId;
    private String nextCursor;
    private String query = "";
    private boolean loading;

    @Override
    protected int layout() {
        return R.layout.activity_history;
    }

    @Override
    protected void onReady() {
        fieldId = getIntent().getStringExtra(EXTRA_FIELD_ID);
        LinearLayout chips = findViewById(R.id.chips);
        Ui.fill(chips, R.layout.item_chip, FILTERS.length, (item, i) -> {
            TextView tv = (TextView) item;
            tv.setText(FILTERS[i]);
            if (i == FILTERS.length - 1) tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_more, 0);
            tv.setOnClickListener(v -> onChip(i));
            chipViews.add(tv);
        });
        restyleChips();

        EditText search = findViewById(R.id.input_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase();
                render();
            }
        });
        findViewById(R.id.btn_more).setOnClickListener(v -> fetch(false));
        fetch(true);
    }

    private void onChip(int i) {
        if (i == FILTERS.length - 1) {
            chooseField();
            return;
        }
        filter = i;
        restyleChips();
        fetch(true);
    }

    private void chooseField() {
        if (fields.isEmpty()) {
            call(api().fields("name"), new ApiCallback<List<RiceField>>() {
                @Override
                public void onSuccess(List<RiceField> list) {
                    fields.addAll(list);
                    if (list.isEmpty()) toast("Bạn chưa có ruộng nào");
                    else chooseField();
                }

                @Override
                public void onError(ApiError e) {
                    showError(e);
                }
            });
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("Tất cả ruộng");
        for (RiceField f : fields) labels.add(f.name);
        Ui.chooser(this, "Lọc theo ruộng", labels, (i, label) -> {
            fieldId = i == 0 ? null : fields.get(i - 1).id;
            chipViews.get(FILTERS.length - 1).setText(label);
            restyleChips();
            fetch(true);
        });
    }

    private void restyleChips() {
        for (int k = 0; k < chipViews.size(); k++) {
            boolean selected = k == filter || (k == FILTERS.length - 1 && fieldId != null);
            TextView tv = chipViews.get(k);
            tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
            tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(selected ? R.color.white : R.color.ink)));
        }
    }

    /** Tải trang đầu (reset=true) hoặc trang kế tiếp. */
    private void fetch(boolean reset) {
        if (loading) return;
        loading = true;
        if (reset) {
            items.clear();
            nextCursor = null;
        }
        String range = filter == 1 ? "7d" : "all";
        String risk = filter == 2 ? "HIGH" : filter == 3 ? "NORMAL" : null;
        call(api().scans(range, risk, fieldId, nextCursor, PAGE), new ApiCallback<Page<Scan>>() {
            @Override
            public void onSuccess(Page<Scan> page) {
                loading = false;
                items.addAll(page.items);
                nextCursor = page.hasMore() ? page.nextCursor : null;
                render();
            }

            @Override
            public void onError(ApiError e) {
                loading = false;
                showError(e);
                render();
            }
        });
    }

    private boolean matches(Scan s) {
        if (query.isEmpty()) return true;
        return HomeActivity.scanTitle(s).toLowerCase().contains(query)
                || (s.fieldName != null && s.fieldName.toLowerCase().contains(query));
    }

    private void render() {
        LinearLayout list = findViewById(R.id.list_history);
        list.removeAllViews();
        findViewById(R.id.btn_more).setVisibility(nextCursor == null ? View.GONE : View.VISIBLE);
        String group = null;
        int groupCount = 0;
        View header = null;
        int shown = 0;
        for (Scan s : items) {
            if (!matches(s)) continue;
            shown++;
            String day = dayLabel(s.createdAt);
            if (!day.equals(group)) {
                if (header != null) Ui.text(header, R.id.count, groupCount + " kết quả");
                group = day;
                groupCount = 0;
                header = getLayoutInflater().inflate(R.layout.item_group_header, list, false);
                Ui.text(header, R.id.title, day);
                list.addView(header);
            }
            groupCount++;
            View item = getLayoutInflater().inflate(R.layout.item_history, list, false);
            Img.load(item.findViewById(R.id.img), s.displayImage(), R.drawable.bg_image_placeholder);
            Ui.text(item, R.id.title, HomeActivity.scanTitle(s));
            Ui.text(item, R.id.field, s.fieldName == null ? "Không gắn ruộng" : s.fieldName);
            Ui.text(item, R.id.time, Fmt.dateTime(s.createdAt));
            Look.scanPill(item.findViewById(R.id.status), s);
            item.setOnClickListener(v -> open(ResultDetailActivity.class, s.id));
            list.addView(item);
        }
        if (header != null) Ui.text(header, R.id.count, groupCount + " kết quả");
        if (shown == 0 && !loading) {
            Ui.emptyNote(list, items.isEmpty() ? "Chưa có lần quét nào phù hợp.\nHãy chụp ảnh lá lúa để bắt đầu." : "Không có kết quả khớp từ khóa.", null);
        }
    }

    private static String dayLabel(long millis) {
        java.util.Calendar a = java.util.Calendar.getInstance();
        java.util.Calendar b = java.util.Calendar.getInstance();
        b.setTimeInMillis(millis);
        if (a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)) return "Hôm nay";
        a.add(java.util.Calendar.DAY_OF_YEAR, -1);
        if (a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)) return "Hôm qua";
        return Fmt.dateLong(millis);
    }
}

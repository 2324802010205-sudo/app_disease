package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.DiaryEntry;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Page;
import com.example.project_final.model.RiceField;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/** Nhật ký canh tác theo ruộng và khoảng thời gian. */
public class DiaryActivity extends BaseActivity {
    public static final String EXTRA_FIELD_ID = "fieldId";

    private static final String[] RANGE_LABELS = {"7 ngày", "30 ngày", "90 ngày"};
    private static final int[] RANGE_DAYS = {7, 30, 90};

    private final List<DiaryEntry> entries = new ArrayList<>();
    private final List<RiceField> fields = new ArrayList<>();
    private String fieldId;
    private int range = 1;
    private String nextCursor;
    private boolean loading;
    private MetaOptions meta;

    @Override
    protected int layout() {
        return R.layout.activity_diary;
    }

    @Override
    protected void onReady() {
        fieldId = getIntent().getStringExtra(EXTRA_FIELD_ID);
        setText(R.id.n_reminders_sub, "Sắp tới");
        findViewById(R.id.btn_add).setOnClickListener(v ->
                startActivity(new Intent(this, AddDiaryActivity.class).putExtra(AddDiaryActivity.EXTRA_FIELD_ID, fieldId)));
        findViewById(R.id.row_field).setOnClickListener(v -> chooseField());
        TextView rangeView = findViewById(R.id.dd_range);
        rangeView.setText(RANGE_LABELS[range]);
        Ui.dropdown(rangeView, RANGE_LABELS, i -> {
            range = i;
            fetch(true);
        });
        findViewById(R.id.btn_more).setOnClickListener(v -> fetch(false));
        withMeta(m -> {
            meta = m;
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().fields("name"), new ApiCallback<List<RiceField>>() {
            @Override
            public void onSuccess(List<RiceField> list) {
                fields.clear();
                fields.addAll(list);
                bindField();
                bindReminders();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
        fetch(true);
    }

    private RiceField selected() {
        for (RiceField f : fields) if (f.id.equals(fieldId)) return f;
        return null;
    }

    private void bindField() {
        RiceField f = selected();
        if (f == null) {
            Img.load(findViewById(R.id.field_thumb), null, R.drawable.bg_image_placeholder);
            setText(R.id.field_name, "Tất cả ruộng");
            setText(R.id.field_meta, fields.size() + " ruộng");
        } else {
            Img.load(findViewById(R.id.field_thumb), f.photoUrl, R.drawable.ph_field);
            setText(R.id.field_name, f.name);
            setText(R.id.field_meta, Fmt.area(f.areaHa) + (f.variety == null || f.variety.isEmpty() ? "" : "  |  " + f.variety));
        }
    }

    private void bindReminders() {
        int upcoming = 0;
        for (RiceField f : fields) {
            if (fieldId != null && !f.id.equals(fieldId)) continue;
            if (f.daysUntilCheck != null && f.daysUntilCheck >= 0 && f.daysUntilCheck <= 14) upcoming++;
        }
        setText(R.id.n_reminders, String.valueOf(upcoming));
    }

    private void chooseField() {
        if (fields.isEmpty()) {
            toast("Bạn chưa có ruộng nào");
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("Tất cả ruộng");
        for (RiceField f : fields) labels.add(f.name);
        Ui.chooser(this, "Chọn ruộng", labels, (i, label) -> {
            fieldId = i == 0 ? null : fields.get(i - 1).id;
            bindField();
            bindReminders();
            fetch(true);
        });
    }

    private void fetch(boolean reset) {
        if (loading) return;
        loading = true;
        if (reset) {
            entries.clear();
            nextCursor = null;
        }
        call(api().diary(fieldId, RANGE_DAYS[range], nextCursor, 30), new ApiCallback<Page<DiaryEntry>>() {
            @Override
            public void onSuccess(Page<DiaryEntry> page) {
                loading = false;
                entries.addAll(page.items);
                nextCursor = page.hasMore() ? page.nextCursor : null;
                render();
            }

            @Override
            public void onError(ApiError e) {
                loading = false;
                showError(e);
            }
        });
    }

    private void render() {
        int photos = 0;
        for (DiaryEntry e : entries) photos += e.images == null ? 0 : e.images.size();
        setText(R.id.n_entries, entries.size() + (nextCursor != null ? "+" : ""));
        setText(R.id.n_entries_sub, "Trong " + RANGE_DAYS[range] + " ngày qua");
        setText(R.id.n_photos, String.valueOf(photos));
        findViewById(R.id.btn_more).setVisibility(nextCursor == null ? View.GONE : View.VISIBLE);

        LinearLayout list = findViewById(R.id.list_timeline);
        list.removeAllViews();
        if (entries.isEmpty() && !loading) {
            Ui.emptyNote(list, "Chưa có nhật ký trong khoảng thời gian này.\nChạm “Thêm nhật ký” để ghi lại hoạt động chăm sóc.", null);
            return;
        }
        String group = null;
        for (DiaryEntry e : entries) {
            if (!e.date.equals(group)) {
                group = e.date;
                int count = 0;
                for (DiaryEntry o : entries) if (o.date.equals(e.date)) count++;
                View header = getLayoutInflater().inflate(R.layout.item_diary_date, list, false);
                Ui.text(header, R.id.date, longDate(e.date));
                Ui.text(header, R.id.count, count + " hoạt động");
                list.addView(header);
            }
            View item = getLayoutInflater().inflate(R.layout.item_diary_entry, list, false);
            item.findViewById(R.id.icon_bg).setBackgroundResource(Look.diaryBg(e.type));
            ImageView icon = item.findViewById(R.id.icon);
            icon.setImageResource(Look.diaryIcon(e.type));
            icon.setColorFilter(getColor(Look.diaryTint(e.type)));
            Ui.text(item, R.id.title, e.title);
            Ui.text(item, R.id.time, e.fieldName == null ? Fmt.isoDate(e.date) : e.fieldName + "  |  " + Fmt.isoDate(e.date));
            Ui.text(item, R.id.desc, e.content == null ? "" : e.content);
            ImageView img = item.findViewById(R.id.img);
            if (e.images != null && !e.images.isEmpty()) {
                img.setVisibility(View.VISIBLE);
                Img.load(img, e.images.get(0).url);
            } else {
                img.setVisibility(View.GONE);
            }
            TextView cat = item.findViewById(R.id.category);
            String label = meta == null ? e.type : MetaOptions.labelOf(meta.diaryTypes, e.type);
            if ("SCAN".equals(e.type)) label = "Kết quả quét";
            Ui.pill(cat, label, Look.diaryIcon(e.type), Look.diaryTint(e.type), R.drawable.bg_chip_soft);
            item.findViewById(R.id.card).setOnClickListener(v -> open(AddDiaryActivity.class, e.id));
            list.addView(item);
        }
    }

    private static String longDate(String iso) {
        if (iso == null || iso.length() < 10) return "";
        int d = Integer.parseInt(iso.substring(8, 10)), m = Integer.parseInt(iso.substring(5, 7));
        return d + " tháng " + m + ", " + iso.substring(0, 4);
    }
}

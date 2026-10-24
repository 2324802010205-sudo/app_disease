package com.example.project_final;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.DiaryEntry;
import com.example.project_final.model.RiceField;
import com.example.project_final.model.Scan;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.Calendar;

public class FieldDetailActivity extends BaseActivity {
    private String id;
    private RiceField field;

    @Override
    protected int layout() {
        return R.layout.activity_field_detail;
    }

    @Override
    protected void onReady() {
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        findViewById(R.id.act_edit).setOnClickListener(v -> open(AddFieldActivity.class, id));
        findViewById(R.id.act_scan).setOnClickListener(v ->
                startActivity(new Intent(this, ScanActivity.class).putExtra(ScanActivity.EXTRA_FIELD_ID, id)));
        findViewById(R.id.link_scans).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class).putExtra(HistoryActivity.EXTRA_FIELD_ID, id)));
        findViewById(R.id.link_diary).setOnClickListener(v ->
                startActivity(new Intent(this, DiaryActivity.class).putExtra(DiaryActivity.EXTRA_FIELD_ID, id)));
        findViewById(R.id.act_recheck).setOnClickListener(v -> pickRecheckDate());
        findViewById(R.id.banner_recheck).setOnClickListener(v -> pickRecheckDate());
        findViewById(R.id.act_delete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            if (field != null && field.photoUrl != null) startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_URL, field.photoUrl));
            else toast("Ruộng này chưa có ảnh");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().field(id), new ApiCallback<RiceField.Detail>() {
            @Override
            public void onSuccess(RiceField.Detail d) {
                bind(d);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void bind(RiceField.Detail d) {
        RiceField f = d.field;
        field = f;
        Img.load(findViewById(R.id.field_photo), f.photoUrl, R.drawable.ph_field);
        setText(R.id.field_name, f.name);
        setText(R.id.field_place, FieldsActivity.place(f));

        String[] labels = {"Diện tích", "Giống lúa", "Giai đoạn", "Tình trạng chung"};
        int[] icons = {R.drawable.ic_crop_free, R.drawable.ic_eco, R.drawable.ic_sprout, R.drawable.ic_shield_check};
        LinearLayout info = findViewById(R.id.list_info);
        info.removeAllViews();
        Ui.fill(info, R.layout.item_info_row, labels.length, (item, i) -> {
            ((ImageView) item.findViewById(R.id.icon)).setImageResource(icons[i]);
            Ui.text(item, R.id.label, labels[i]);
            TextView v = item.findViewById(R.id.value);
            switch (i) {
                case 0:
                    v.setText(Fmt.area(f.areaHa));
                    break;
                case 1:
                    v.setText(f.variety == null || f.variety.isEmpty() ? "—" : f.variety);
                    break;
                case 2:
                    v.setText(f.stage == null || f.stage.isEmpty() ? "—" : f.stage);
                    break;
                default:
                    Look.fieldPill(v, f);
            }
        });

        bindScans(d);
        bindRecheck(f);
        bindDiary(d);
    }

    private void bindScans(RiceField.Detail d) {
        LinearLayout box = findViewById(R.id.list_scans);
        box.removeAllViews();
        if (d.recentScans.isEmpty()) {
            Ui.emptyNote(box, "Chưa có lần quét nào cho ruộng này", null);
            return;
        }
        Ui.fill(box, R.layout.item_home_scan, d.recentScans.size(), (item, i) -> {
            Scan s = d.recentScans.get(i);
            Img.load(item.findViewById(R.id.img), s.displayImage(), R.drawable.bg_image_placeholder);
            Ui.text(item, R.id.title, HomeActivity.scanTitle(s));
            Ui.text(item, R.id.meta, Fmt.dateTime(s.createdAt));
            Look.scanPill(item.findViewById(R.id.status), s);
            item.setOnClickListener(v -> open(ResultDetailActivity.class, s.id));
        });
    }

    private void bindRecheck(RiceField f) {
        boolean set = f.nextCheckDate != null && !f.nextCheckDate.isEmpty();
        setText(R.id.recheck_date, set ? Fmt.isoDate(f.nextCheckDate) : "Chưa đặt");
        String left = "";
        if (set && f.daysUntilCheck != null) {
            left = f.daysUntilCheck < 0 ? "(quá hạn " + (-f.daysUntilCheck) + " ngày)"
                    : f.daysUntilCheck == 0 ? "(hôm nay)" : "(còn " + f.daysUntilCheck + " ngày)";
        }
        setText(R.id.recheck_left, left);
        setText(R.id.recheck_state, set ? "Đã đặt" : "Đặt lịch");
    }

    private void bindDiary(RiceField.Detail d) {
        LinearLayout box = findViewById(R.id.list_diary);
        box.removeAllViews();
        if (d.recentDiaries.isEmpty()) {
            Ui.emptyNote(box, "Chưa có nhật ký nào. Chạm để thêm", () ->
                    startActivity(new Intent(this, AddDiaryActivity.class).putExtra(AddDiaryActivity.EXTRA_FIELD_ID, id)));
            return;
        }
        Ui.fill(box, R.layout.item_diary_row, d.recentDiaries.size(), (item, i) -> {
            DiaryEntry e = d.recentDiaries.get(i);
            item.findViewById(R.id.icon_bg).setBackgroundResource(Look.diaryBg(e.type));
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(Look.diaryIcon(e.type));
            img.setColorFilter(getColor(Look.diaryTint(e.type)));
            Ui.text(item, R.id.title, e.title);
            Ui.text(item, R.id.time, Fmt.isoDate(e.date));
            Ui.text(item, R.id.desc, e.content == null ? "" : e.content);
            item.setOnClickListener(v -> open(AddDiaryActivity.class, e.id));
        });
    }

    private void pickRecheckDate() {
        if (field == null) return;
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (p, y, m, day) -> {
            Requests.FieldBody body = Requests.FieldBody.from(field);
            body.nextCheckDate = Fmt.toIso(y, m, day);
            busy(true);
            call(api().updateField(id, body), new ApiCallback<RiceField>() {
                @Override
                public void onSuccess(RiceField f) {
                    busy(false);
                    toast("Đã đặt lịch kiểm tra " + Fmt.isoDate(f.nextCheckDate));
                    field = f;
                    bindRecheck(f);
                }

                @Override
                public void onError(ApiError e) {
                    busy(false);
                    showError(e);
                }
            });
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa ruộng?")
                .setMessage("Ruộng và ảnh của ruộng sẽ bị xóa. Lịch sử quét vẫn được giữ lại.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    busy(true);
                    call(api().deleteField(id), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast("Đã xóa ruộng");
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

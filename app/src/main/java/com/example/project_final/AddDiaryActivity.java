package com.example.project_final;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.DiaryEntry;
import com.example.project_final.model.ImageRef;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.RiceField;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Forms;
import com.example.project_final.util.ImageUploader;
import com.example.project_final.util.ImagePicker;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/** Thêm nhật ký (EXTRA_FIELD_ID tùy chọn) hoặc sửa/xóa nhật ký có sẵn (EXTRA_ID). */
public class AddDiaryActivity extends BaseActivity {
    public static final String EXTRA_FIELD_ID = "fieldId";
    private static final int MAX_IMAGES = 5;

    private final List<View> weatherViews = new ArrayList<>();
    private final List<RiceField> fields = new ArrayList<>();
    private final List<ImageRef> keptImages = new ArrayList<>();
    private final List<Uri> newImages = new ArrayList<>();
    private MetaOptions meta;
    private String editId;
    private String fieldId;
    private String type = "CARE";
    private String weather = "SUNNY";
    private String date = Fmt.todayIso();
    private ImagePicker picker;
    private EditText title, notes, material, amount;

    @Override
    protected int layout() {
        return R.layout.activity_add_diary;
    }

    @Override
    protected void onReady() {
        editId = getIntent().getStringExtra(EXTRA_ID);
        fieldId = getIntent().getStringExtra(EXTRA_FIELD_ID);
        setText(R.id.screen_title, editId == null ? "Thêm nhật ký" : "Sửa nhật ký");
        title = findViewById(R.id.input_title);
        notes = findViewById(R.id.input_notes);
        material = findViewById(R.id.input_material);
        amount = findViewById(R.id.input_amount);

        TextView dateView = findViewById(R.id.dd_date);
        dateView.setText(Fmt.isoDate(date));
        dateView.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (p, y, m, d) -> {
                date = Fmt.toIso(y, m, d);
                dateView.setText(Fmt.isoDate(date));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        TextView counter = findViewById(R.id.counter);
        notes.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { counter.setText(s.length() + "/500"); }
        });

        picker = new ImagePicker(this, MAX_IMAGES, uris -> {
            for (Uri u : uris) if (keptImages.size() + newImages.size() < MAX_IMAGES) newImages.add(u);
            renderAttached();
        });
        findViewById(R.id.btn_attach).setOnClickListener(v -> addImage());
        renderAttached();

        findViewById(R.id.row_field).setOnClickListener(v -> chooseField());
        withMeta(m -> {
            meta = m;
            bindOptions();
            if (editId != null) loadEntry();
        });
        loadFields();

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        TextView cancel = findViewById(R.id.btn_cancel);
        if (editId != null) {
            setText(R.id.screen_title, "Sửa nhật ký");
            cancel.setText("Xóa nhật ký");
            cancel.setOnClickListener(v -> confirmDelete());
        } else {
            cancel.setOnClickListener(v -> finish());
        }
    }

    private void bindOptions() {
        TextView typeView = findViewById(R.id.dd_type);
        List<String> labels = new ArrayList<>();
        for (Option o : meta.diaryTypes) labels.add(o.label);
        typeView.setText(MetaOptions.labelOf(meta.diaryTypes, type));
        typeView.setOnClickListener(v -> Ui.chooser(this, "Loại hoạt động", labels, (i, label) -> {
            type = meta.diaryTypes.get(i).code;
            typeView.setText(label);
        }));

        LinearLayout box = findViewById(R.id.list_weather);
        box.removeAllViews();
        weatherViews.clear();
        Ui.fill(box, R.layout.item_weather_option, meta.weather.size(), (item, i) -> {
            Option o = meta.weather.get(i);
            Ui.text(item, R.id.label, o.label);
            ImageView icon = item.findViewById(R.id.icon);
            icon.setImageResource(weatherIcon(o.code));
            if ("OTHER".equals(o.code)) icon.setColorFilter(getColor(R.color.muted));
            item.setOnClickListener(v -> {
                weather = o.code;
                selectWeather();
            });
            weatherViews.add(item);
        });
        selectWeather();
    }

    private static int weatherIcon(String code) {
        switch (code) {
            case "SUNNY":
                return R.drawable.ic_w_sun;
            case "CLOUDY":
                return R.drawable.ic_w_cloud;
            case "LIGHT_RAIN":
            case "HEAVY_RAIN":
                return R.drawable.ic_w_rain;
            default:
                return R.drawable.ic_air;
        }
    }

    private void selectWeather() {
        for (int i = 0; i < weatherViews.size(); i++) {
            boolean on = meta.weather.get(i).code.equals(weather);
            weatherViews.get(i).setBackgroundResource(on ? R.drawable.bg_option_selected : R.drawable.bg_option);
        }
    }

    private void loadFields() {
        call(api().fields("name"), new ApiCallback<List<RiceField>>() {
            @Override
            public void onSuccess(List<RiceField> list) {
                fields.clear();
                fields.addAll(list);
                if (fieldId == null && editId == null && fields.size() == 1) fieldId = fields.get(0).id;
                bindField();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void bindField() {
        RiceField f = null;
        for (RiceField x : fields) if (x.id.equals(fieldId)) f = x;
        if (f == null) {
            Img.load(findViewById(R.id.field_thumb), null, R.drawable.bg_image_placeholder);
            setText(R.id.field_name, "Chưa chọn ruộng");
            setText(R.id.field_place, "Chạm để chọn ruộng cho nhật ký");
        } else {
            Img.load(findViewById(R.id.field_thumb), f.photoUrl, R.drawable.ph_field);
            setText(R.id.field_name, f.name);
            setText(R.id.field_place, FieldsActivity.place(f));
        }
    }

    private void chooseField() {
        if (fields.isEmpty()) {
            toast("Bạn chưa có ruộng nào. Hãy thêm ruộng trước");
            return;
        }
        List<String> labels = new ArrayList<>();
        labels.add("Không gắn ruộng");
        for (RiceField f : fields) labels.add(f.name);
        Ui.chooser(this, "Chọn ruộng", labels, (i, label) -> {
            fieldId = i == 0 ? null : fields.get(i - 1).id;
            bindField();
        });
    }

    private void loadEntry() {
        call(api().diaryEntry(editId), new ApiCallback<DiaryEntry>() {
            @Override
            public void onSuccess(DiaryEntry e) {
                fieldId = e.fieldId;
                type = e.type == null ? "CARE" : e.type;
                weather = e.weather == null ? "SUNNY" : e.weather;
                date = e.date;
                title.setText(e.title);
                notes.setText(e.content);
                ((TextView) findViewById(R.id.dd_date)).setText(Fmt.isoDate(date));
                ((TextView) findViewById(R.id.dd_type)).setText(MetaOptions.labelOf(meta.diaryTypes, type));
                keptImages.clear();
                if (e.images != null) keptImages.addAll(e.images);
                renderAttached();
                bindField();
                selectWeather();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void addImage() {
        if (keptImages.size() + newImages.size() >= MAX_IMAGES) {
            toast("Tối đa " + MAX_IMAGES + " ảnh cho mỗi nhật ký");
            return;
        }
        picker.show();
    }

    private void renderAttached() {
        LinearLayout list = findViewById(R.id.list_attached);
        list.removeAllViews();
        for (ImageRef r : new ArrayList<>(keptImages)) {
            View item = getLayoutInflater().inflate(R.layout.item_attached, list, false);
            Img.load(item.findViewById(R.id.img), r.url);
            item.findViewById(R.id.remove).setOnClickListener(v -> {
                keptImages.remove(r);
                renderAttached();
            });
            list.addView(item);
        }
        for (Uri u : new ArrayList<>(newImages)) {
            View item = getLayoutInflater().inflate(R.layout.item_attached, list, false);
            ((ImageView) item.findViewById(R.id.img)).setImageURI(u);
            item.findViewById(R.id.remove).setOnClickListener(v -> {
                newImages.remove(u);
                renderAttached();
            });
            list.addView(item);
        }
        if (keptImages.size() + newImages.size() < MAX_IMAGES) {
            ImageView add = new ImageView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) Ui.dp(this, 56), 1);
            add.setLayoutParams(lp);
            add.setBackgroundResource(R.drawable.bg_dashed_box);
            add.setImageResource(R.drawable.ic_add);
            add.setColorFilter(getColor(R.color.ink_deep));
            add.setContentDescription("Thêm ảnh");
            int pad = (int) Ui.dp(this, 14);
            add.setPadding(pad, pad, pad, pad);
            add.setOnClickListener(v -> addImage());
            list.addView(add);
        }
    }

    private void save() {
        if (!Forms.require(title, "Nhập tiêu đề nhật ký")) return;
        busy(true);
        uploadImages(newImages, "diary", new ImageUploader.Callback() {
            @Override
            public void onDone(List<ImageRef> refs) {
                List<ImageRef> all = new ArrayList<>(keptImages);
                all.addAll(refs);
                submit(all);
            }

            @Override
            public void onError(String message) {
                busy(false);
                toast(message);
            }
        });
    }

    private void submit(List<ImageRef> images) {
        Requests.DiaryBody b = new Requests.DiaryBody();
        b.fieldId = fieldId;
        b.title = Forms.text(title);
        b.type = type;
        b.date = date;
        b.weather = weather;
        b.images = images;
        StringBuilder content = new StringBuilder(Forms.text(notes));
        String mat = Forms.text(material), amt = Forms.text(amount);
        if (!mat.isEmpty()) content.append(content.length() > 0 ? "\n" : "").append("Vật tư: ").append(mat);
        if (!amt.isEmpty()) content.append(content.length() > 0 ? "\n" : "").append("Số lượng/chi phí: ").append(amt);
        b.content = content.length() == 0 ? null : content.toString();

        retrofit2.Call<DiaryEntry> request = editId == null ? api().createDiary(b) : api().updateDiary(editId, b);
        call(request, new ApiCallback<DiaryEntry>() {
            @Override
            public void onSuccess(DiaryEntry e) {
                busy(false);
                toast("Đã lưu nhật ký");
                finish();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("title", title, "content", notes))) showError(e);
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa nhật ký?")
                .setMessage("Nhật ký này và ảnh đính kèm sẽ bị xóa.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    busy(true);
                    call(api().deleteDiary(editId), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast("Đã xóa nhật ký");
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

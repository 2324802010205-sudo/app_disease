package com.example.project_final;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Disease;
import com.example.project_final.util.DiseaseArt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

public class DiseaseDetailActivity extends BaseActivity {
    private String id;
    private Disease disease;

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_disease_detail;
    }

    @Override
    protected void onReady() {
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        findViewById(R.id.btn_zoom).setOnClickListener(v -> {
            if (disease != null && disease.imageUrl != null) startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_URL, disease.imageUrl));
            else toast("Bệnh này chưa có ảnh để phóng to");
        });
        findViewById(R.id.btn_scan).setOnClickListener(v -> {
            if (requireAccount("quét và phân tích bệnh lúa")) startActivity(new Intent(this, ScanActivity.class));
        });
        findViewById(R.id.btn_bookmark_top).setOnClickListener(v -> toggle());
        findViewById(R.id.btn_save).setOnClickListener(v -> toggle());

        call(api().disease(id), new ApiCallback<Disease>() {
            @Override
            public void onSuccess(Disease d) {
                disease = d;
                bind(d);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void bind(Disease d) {
        Img.load(findViewById(R.id.hero), d.imageUrl, DiseaseArt.hero(d.id));
        setText(R.id.disease_name, d.name);
        TextView cause = findViewById(R.id.disease_cause);
        cause.setText("Bệnh do " + Look.cause(d.cause).toLowerCase());
        setText(R.id.disease_desc, d.description);

        StringBuilder overview = new StringBuilder();
        if (d.scientificName != null && !d.scientificName.isEmpty()) overview.append("Tác nhân: ").append(d.scientificName).append(". ");
        overview.append("Vị trí gây hại: ").append(Look.part(d.part).toLowerCase()).append('.');
        if (d.common) overview.append(" Đây là bệnh thường gặp trên đồng ruộng.");
        setText(R.id.disease_overview, overview);

        bullets(R.id.list_signs, d.symptoms);
        bullets(R.id.list_conditions, d.conditions);
        List<String> care = new ArrayList<>(d.prevention);
        care.addAll(d.treatment);
        bullets(R.id.list_prevention, care);
        refreshSaved();
    }

    private void bullets(int containerId, List<String> lines) {
        LinearLayout box = findViewById(containerId);
        box.removeAllViews();
        Ui.fill(box, R.layout.item_bullet, lines.size(), (item, i) -> ((TextView) item).setText(lines.get(i)));
    }

    private void toggle() {
        if (disease == null || !requireAccount("lưu bệnh vào danh sách của bạn")) return;
        boolean target = !disease.saved;
        busy(true);
        call(target ? api().saveDisease(id) : api().unsaveDisease(id), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                busy(false);
                disease.saved = target;
                refreshSaved();
                com.google.android.material.snackbar.Snackbar bar = com.google.android.material.snackbar.Snackbar.make(
                        findViewById(R.id.root), target ? "Đã lưu bệnh" : "Đã bỏ lưu bệnh", com.google.android.material.snackbar.Snackbar.LENGTH_LONG);
                if (target) bar.setAction("Xem bệnh đã lưu", v -> startActivity(new Intent(DiseaseDetailActivity.this, SavedActivity.class)));
                bar.show();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }

    private void refreshSaved() {
        boolean saved = disease != null && disease.saved;
        ((ImageView) findViewById(R.id.icon_bookmark_top)).setImageResource(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        TextView btn = findViewById(R.id.btn_save);
        btn.setText(saved ? "Đã lưu" : "Lưu");
        btn.setCompoundDrawablesRelativeWithIntrinsicBounds(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border, 0, 0, 0);
    }
}

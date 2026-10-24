package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Disease;
import com.example.project_final.util.DiseaseArt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;

import java.util.ArrayList;
import java.util.List;

/** Bệnh đã lưu của người dùng. */
public class SavedActivity extends BaseActivity {
    private final List<Disease> saved = new ArrayList<>();
    private String query = "";
    private String cause; // FUNGUS | BACTERIA | VIRUS | null

    @Override
    protected int layout() {
        return R.layout.activity_saved;
    }

    @Override
    protected void onReady() {
        ((android.widget.EditText) findViewById(R.id.input_search)).addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim().toLowerCase();
                render();
            }
        });
        findViewById(R.id.btn_filter).setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(this, v);
            menu.getMenu().add("Tất cả");
            menu.getMenu().add("Bệnh do nấm");
            menu.getMenu().add("Bệnh do vi khuẩn");
            menu.getMenu().add("Bệnh do virus");
            menu.setOnMenuItemClickListener(item -> {
                String t = item.getTitle().toString();
                cause = t.equals("Bệnh do nấm") ? "FUNGUS" : t.equals("Bệnh do vi khuẩn") ? "BACTERIA" : t.equals("Bệnh do virus") ? "VIRUS" : null;
                render();
                return true;
            });
            menu.show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().savedDiseases(), new ApiCallback<List<Disease>>() {
            @Override
            public void onSuccess(List<Disease> list) {
                saved.clear();
                saved.addAll(list);
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void render() {
        LinearLayout list = findViewById(R.id.list_saved);
        list.removeAllViews();
        int shown = 0;
        for (Disease d : new ArrayList<>(saved)) {
            if (cause != null && !cause.equals(d.cause)) continue;
            if (!query.isEmpty() && !d.name.toLowerCase().contains(query) && !Look.cause(d.cause).toLowerCase().contains(query)) continue;
            shown++;
            View item = getLayoutInflater().inflate(R.layout.item_saved, list, false);
            Img.load(item.findViewById(R.id.img), d.imageUrl, DiseaseArt.of(d.id));
            Ui.text(item, R.id.name, d.name);
            Ui.text(item, R.id.desc, d.description);
            TextView c = item.findViewById(R.id.cause);
            String label = "Bệnh do " + Look.cause(d.cause).toLowerCase();
            if ("FUNGUS".equals(d.cause)) Ui.pill(c, label, R.drawable.ic_eco, R.color.red, R.drawable.bg_pill_red);
            else if ("BACTERIA".equals(d.cause)) Ui.pill(c, label, R.drawable.ic_bug, R.color.green_ok, R.drawable.bg_pill_green);
            else Ui.pill(c, label, R.drawable.ic_sun, R.color.blue, R.drawable.bg_pill_blue);
            item.findViewById(R.id.bookmark).setOnClickListener(v -> unsave(d));
            item.findViewById(R.id.more).setOnClickListener(v -> {
                PopupMenu menu = new PopupMenu(this, v);
                menu.getMenu().add("Xem chi tiết");
                menu.getMenu().add("Bỏ lưu");
                menu.setOnMenuItemClickListener(m -> {
                    if (m.getTitle().equals("Bỏ lưu")) unsave(d);
                    else open(DiseaseDetailActivity.class, d.id);
                    return true;
                });
                menu.show();
            });
            item.setOnClickListener(v -> open(DiseaseDetailActivity.class, d.id));
            list.addView(item);
        }
        if (shown == 0) {
            Ui.emptyNote(list, saved.isEmpty() ? "Bạn chưa lưu bệnh nào.\nMở Thư viện bệnh và chạm biểu tượng đánh dấu để lưu." : "Không có bệnh khớp bộ lọc.", null);
        }
        ((TextView) findViewById(R.id.subtitle)).setText(saved.size() + " bệnh đã lưu");
    }

    private void unsave(Disease d) {
        call(api().unsaveDisease(d.id), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                saved.remove(d);
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }
}

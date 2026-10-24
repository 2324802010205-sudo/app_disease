package com.example.project_final;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Disease;
import com.example.project_final.util.DiseaseArt;
import com.example.project_final.util.Img;
import com.example.project_final.util.Look;
import com.example.project_final.view.NavBar;

import java.util.ArrayList;
import java.util.List;

/** Thư viện bệnh lúa: lọc theo bộ phận/thường gặp và tìm kiếm ngay trên máy chủ. */
public class LibraryActivity extends BaseActivity {
    private static final String[] FILTERS = {"Tất cả", "Lá", "Thân", "Hạt", "Thường gặp"};
    private static final int[] FILTER_ICONS = {R.drawable.ic_grid, R.drawable.ic_eco, R.drawable.ic_sprout, R.drawable.ic_eco, R.drawable.ic_star};
    private static final String[] PARTS = {null, "LEAF", "STEM", "GRAIN", null};

    private final List<TextView> chipViews = new ArrayList<>();
    private int filter;
    private String query = "";
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_library;
    }

    @Override
    protected void onReady() {
        setupBottomNav(NavBar.LIBRARY);
        LinearLayout chips = findViewById(R.id.chips);
        Ui.fill(chips, R.layout.item_chip, FILTERS.length, (item, i) -> {
            TextView tv = (TextView) item;
            tv.setText(FILTERS[i]);
            tv.setCompoundDrawablePadding((int) Ui.dp(this, 6));
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(FILTER_ICONS[i], 0, 0, 0);
            tv.setOnClickListener(v -> {
                filter = i;
                restyle();
                load();
            });
            chipViews.add(tv);
        });
        restyle();

        ((EditText) findViewById(R.id.input_search)).addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                // chờ người dùng gõ xong một nhịp rồi mới gọi máy chủ
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = LibraryActivity.this::load;
                handler.postDelayed(pendingSearch, 350);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void restyle() {
        for (int k = 0; k < chipViews.size(); k++) {
            boolean selected = k == filter;
            TextView tv = chipViews.get(k);
            int color = getColor(selected ? R.color.white : R.color.ink);
            tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            tv.setTextColor(color);
            tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    private void load() {
        Boolean common = filter == 4 ? Boolean.TRUE : null;
        call(api().diseases(PARTS[filter], null, common, query.isEmpty() ? null : query), new ApiCallback<List<Disease>>() {
            @Override
            public void onSuccess(List<Disease> list) {
                render(list);
            }

            @Override
            public void onError(ApiError e) {
                LinearLayout box = findViewById(R.id.list_diseases);
                box.removeAllViews();
                Ui.emptyNote(box, e.message + "\nChạm để thử lại", LibraryActivity.this::load);
            }
        });
    }

    private void render(List<Disease> list) {
        LinearLayout box = findViewById(R.id.list_diseases);
        box.removeAllViews();
        if (list.isEmpty()) {
            Ui.emptyNote(box, "Không tìm thấy bệnh phù hợp", null);
            return;
        }
        for (Disease d : list) {
            View item = getLayoutInflater().inflate(R.layout.item_disease, box, false);
            Img.load(item.findViewById(R.id.img), d.imageUrl, DiseaseArt.of(d.id));
            Ui.text(item, R.id.name, d.name);
            Ui.text(item, R.id.desc, d.description);
            item.findViewById(R.id.common).setVisibility(d.common ? View.VISIBLE : View.GONE);
            TextView cat = item.findViewById(R.id.category);
            if ("STEM".equals(d.part)) Ui.pill(cat, "Bệnh trên thân", R.drawable.ic_sprout, R.color.amber, R.drawable.bg_pill_amber);
            else if ("GRAIN".equals(d.part)) Ui.pill(cat, "Bệnh trên hạt", R.drawable.ic_eco, R.color.orange, R.drawable.bg_pill_orange);
            else Ui.pill(cat, "Bệnh trên lá", R.drawable.ic_eco, R.color.primary, R.drawable.bg_chip_soft);
            bindBookmark(item.findViewById(R.id.bookmark), d);
            item.setOnClickListener(v -> startActivity(new Intent(this, DiseaseDetailActivity.class).putExtra(EXTRA_ID, d.id)));
            box.addView(item);
        }
    }

    private void bindBookmark(ImageView bm, Disease d) {
        bm.setImageResource(d.saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        bm.setOnClickListener(v -> {
            if (!requireAccount("lưu bệnh vào danh sách của bạn")) return;
            boolean target = !d.saved;
            bm.setImageResource(target ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            d.saved = target;
            call(target ? api().saveDisease(d.id) : api().unsaveDisease(d.id), new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void body) {
                    toast(target ? "Đã lưu " + d.name : "Đã bỏ lưu " + d.name);
                }

                @Override
                public void onError(ApiError e) {
                    d.saved = !target;
                    bm.setImageResource(d.saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
                    showError(e);
                }
            });
        });
    }
}

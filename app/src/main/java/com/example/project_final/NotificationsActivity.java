package com.example.project_final;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Notification;
import com.example.project_final.model.Page;
import com.example.project_final.util.Fmt;
import com.example.project_final.view.NavBar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Trung tâm thông báo: lọc theo loại, đánh dấu đã đọc, xóa, mở nội dung liên quan. */
public class NotificationsActivity extends BaseActivity {
    private static final String[] FILTERS = {"Tất cả", "Chưa đọc", "Cảnh báo", "Nhắc lịch", "Cộng đồng", "Mẹo"};
    private static final String[] KEYS = {"all", "unread", "alert", "reminder", "community", "tip"};
    private static final int[] FILTER_ICONS = {R.drawable.ic_grid, R.drawable.ic_dot, R.drawable.ic_warning, R.drawable.ic_calendar,
            R.drawable.ic_group, R.drawable.ic_lightbulb};

    private final List<Notification> items = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private int filter;
    private String nextCursor;
    private long unread;
    private boolean loading;

    @Override
    protected int layout() {
        return R.layout.activity_notifications;
    }

    @Override
    protected void onReady() {
        setupBottomNav(NavBar.NOTIFICATIONS);
        LinearLayout chips = findViewById(R.id.chips);
        Ui.fill(chips, R.layout.item_chip, FILTERS.length, (item, i) -> {
            TextView tv = (TextView) item;
            tv.setText(FILTERS[i]);
            tv.setCompoundDrawablePadding((int) Ui.dp(this, 6));
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(FILTER_ICONS[i], 0, 0, 0);
            tv.setOnClickListener(v -> {
                filter = i;
                restyle();
                fetch(true);
            });
            chipViews.add(tv);
        });
        restyle();
        findViewById(R.id.btn_read_all).setOnClickListener(v -> readAll());
        findViewById(R.id.btn_more).setOnClickListener(v -> fetch(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetch(true);
    }

    private void restyle() {
        for (int k = 0; k < chipViews.size(); k++) {
            boolean selected = k == filter;
            TextView tv = chipViews.get(k);
            int color = getColor(selected ? R.color.white : R.color.ink);
            tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            tv.setTextColor(color);
            tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(k == 1 && !selected ? getColor(R.color.red) : color));
        }
    }

    private void fetch(boolean reset) {
        if (loading) return;
        loading = true;
        if (reset) {
            items.clear();
            nextCursor = null;
        }
        call(api().notifications(KEYS[filter], nextCursor, 20), new ApiCallback<Page<Notification>>() {
            @Override
            public void onSuccess(Page<Notification> page) {
                loading = false;
                items.addAll(page.items);
                nextCursor = page.hasMore() ? page.nextCursor : null;
                unread = page.unread;
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

    private void render() {
        TextView banner = findViewById(R.id.banner_title);
        banner.setText(unread == 0 ? "Bạn đã đọc tất cả thông báo" : "Bạn có " + unread + " thông báo chưa đọc");
        findViewById(R.id.btn_read_all).setVisibility(unread == 0 ? View.INVISIBLE : View.VISIBLE);
        findViewById(R.id.btn_more).setVisibility(nextCursor == null ? View.GONE : View.VISIBLE);

        LinearLayout list = findViewById(R.id.list_notifications);
        list.removeAllViews();
        if (items.isEmpty() && !loading) {
            Ui.emptyNote(list, "Chưa có thông báo nào.", null);
            return;
        }
        String group = null;
        for (Notification n : new ArrayList<>(items)) {
            String day = dayLabel(n.createdAt);
            if (!day.equals(group)) {
                group = day;
                TextView h = new TextView(this);
                h.setText(day);
                h.setTextColor(getColor(R.color.ink_deep));
                h.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_18));
                h.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.topMargin = (int) Ui.dp(this, 12);
                lp.bottomMargin = (int) Ui.dp(this, 8);
                list.addView(h, lp);
            }
            View row = getLayoutInflater().inflate(R.layout.item_notification, list, false);
            Ui.text(row, R.id.title, n.title);
            Ui.text(row, R.id.text, n.body);
            Ui.text(row, R.id.time, Fmt.relative(n.createdAt));
            row.findViewById(R.id.dot).setVisibility(n.read ? View.GONE : View.VISIBLE);
            row.findViewById(R.id.unread_bar).setVisibility(n.read ? View.INVISIBLE : View.VISIBLE);
            bindIcon(row, n.kind);
            row.findViewById(R.id.btn_delete).setOnClickListener(v -> delete(n));
            row.setOnClickListener(v -> openItem(n));
            list.addView(row);
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

    private void openItem(Notification n) {
        if (!n.read) {
            n.read = true;
            unread = Math.max(0, unread - 1);
            call(api().markRead(n.id), new ApiCallback<Void>() {
                @Override public void onSuccess(Void body) {}
                @Override public void onError(ApiError e) {}
            });
            render();
        }
        String ref = n.refType == null ? "" : n.refType;
        switch (ref) {
            case "scan":
                open(ResultDetailActivity.class, n.refId);
                break;
            case "field":
                open(FieldDetailActivity.class, n.refId);
                break;
            case "post":
                open(PostDetailActivity.class, n.refId);
                break;
            case "disease":
                open(DiseaseDetailActivity.class, n.refId);
                break;
            case "weather":
                startActivity(new android.content.Intent(this, WeatherActivity.class));
                break;
            default:
                break;
        }
    }

    private void delete(Notification n) {
        call(api().deleteNotification(n.id), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                items.remove(n);
                if (!n.read) unread = Math.max(0, unread - 1);
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void readAll() {
        call(api().markAllRead(), new ApiCallback<Map<String, Integer>>() {
            @Override
            public void onSuccess(Map<String, Integer> body) {
                for (Notification n : items) n.read = true;
                unread = 0;
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void bindIcon(View row, String kind) {
        ImageView icon = row.findViewById(R.id.icon);
        View bg = row.findViewById(R.id.icon_bg);
        row.findViewById(R.id.thumb).setVisibility(View.GONE);
        icon.setVisibility(View.VISIBLE);
        String k = kind == null ? "" : kind;
        switch (k) {
            case "ALERT":
                bg.setBackgroundResource(R.drawable.bg_icon_red);
                icon.setImageResource(R.drawable.ic_warning);
                icon.setColorFilter(getColor(R.color.red));
                break;
            case "REMINDER":
                bg.setBackgroundResource(R.drawable.bg_icon_tint);
                icon.setImageResource(R.drawable.ic_calendar);
                icon.setColorFilter(getColor(R.color.primary));
                break;
            case "COMMUNITY":
                bg.setBackgroundResource(R.drawable.bg_icon_tint);
                icon.setImageResource(R.drawable.ic_group);
                icon.setColorFilter(getColor(R.color.primary));
                break;
            case "TIP":
                bg.setBackgroundResource(R.drawable.bg_icon_amber);
                icon.setImageResource(R.drawable.ic_menu_book);
                icon.setColorFilter(getColor(R.color.amber));
                break;
            case "WEATHER":
                bg.setBackgroundResource(R.drawable.bg_icon_blue);
                icon.setImageResource(R.drawable.ic_cloud);
                icon.setColorFilter(getColor(R.color.blue));
                break;
            default:
                bg.setBackgroundResource(R.drawable.bg_icon_blue);
                icon.setImageResource(R.drawable.ic_campaign);
                icon.setColorFilter(getColor(R.color.blue));
                break;
        }
    }
}

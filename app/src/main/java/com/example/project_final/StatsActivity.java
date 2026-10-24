package com.example.project_final;

import android.content.Intent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Page;
import com.example.project_final.model.Post;
import com.example.project_final.model.Stats;
import com.example.project_final.model.User;
import com.example.project_final.util.Fmt;

import java.util.List;
import java.util.Locale;

/** Thống kê cá nhân 7 ngày / 30 ngày. */
public class StatsActivity extends BaseActivity {
    private boolean week = true;

    @Override
    protected int layout() {
        return R.layout.activity_stats;
    }

    @Override
    protected void onReady() {
        go(R.id.link_history, HistoryActivity.class);
        findViewById(R.id.tab_7).setOnClickListener(v -> select(true));
        findViewById(R.id.tab_30).setOnClickListener(v -> select(false));
        select(true);
        loadTotals();
    }

    /** Số ruộng và số bài viết không phụ thuộc khoảng thời gian. */
    private void loadTotals() {
        call(api().me(), new ApiCallback<User>() {
            @Override
            public void onSuccess(User u) {
                if (u.stats != null) setText(R.id.stat_fields, String.valueOf(u.stats.fields));
            }

            @Override
            public void onError(ApiError e) {
                setText(R.id.stat_fields, "-");
            }
        });
        call(api().myPosts(null, 30), new ApiCallback<Page<Post>>() {
            @Override
            public void onSuccess(Page<Post> p) {
                setText(R.id.stat_posts, p.items.size() + (p.hasMore() ? "+" : ""));
            }

            @Override
            public void onError(ApiError e) {
                setText(R.id.stat_posts, "-");
            }
        });
    }

    private void select(boolean isWeek) {
        week = isWeek;
        TextView t7 = findViewById(R.id.tab_7);
        TextView t30 = findViewById(R.id.tab_30);
        t7.setBackgroundResource(week ? R.drawable.bg_tab_selected : 0);
        t7.setTextColor(getColor(week ? R.color.white : R.color.ink));
        t7.setTypeface(null, week ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        t30.setBackgroundResource(week ? 0 : R.drawable.bg_tab_selected);
        t30.setTextColor(getColor(week ? R.color.ink : R.color.white));
        t30.setTypeface(null, week ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        int days = week ? 7 : 30;
        setText(R.id.chart_subtitle, "Số lần quét cây lúa bằng RiceGuard AI trong " + days + " ngày gần nhất");
        setText(R.id.dist_subtitle, "Tổng quan kết quả từ các lượt quét trong " + days + " ngày qua");

        call(api().stats(week ? "week" : "month"), new ApiCallback<Stats>() {
            @Override
            public void onSuccess(Stats s) {
                bind(s);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void bind(Stats s) {
        setText(R.id.stat_scans, String.valueOf(s.scans));
        setText(R.id.stat_alerts, String.valueOf(s.alerts));
        bindChart(s.daily);
        bindDistribution(s);
        bindActivities(s.recentActivities);
    }

    private void bindChart(List<Stats.Day> daily) {
        // 7 ngày: mỗi cột một ngày. 30 ngày: gộp mỗi 5 ngày thành một cột để dễ đọc.
        int group = week ? 1 : 5;
        int n = (daily.size() + group - 1) / group;
        int[] data = new int[n];
        String[] labels = new String[n];
        int peak = 0;
        for (int i = 0; i < n; i++) {
            long sum = 0;
            for (int k = i * group; k < Math.min(daily.size(), (i + 1) * group); k++) sum += daily.get(k).count;
            data[i] = (int) sum;
            peak = Math.max(peak, data[i]);
            Stats.Day first = daily.get(i * group);
            labels[i] = week ? Fmt.weekday(first.date, false) : Fmt.isoDate(first.date).substring(0, 5);
        }
        int max = Math.max(4, peak % 2 == 0 ? peak : peak + 1);
        ((BarChartView) findViewById(R.id.chart)).setData(data, labels, max);
    }

    private void bindDistribution(Stats s) {
        String[] labels = {"Bình thường", "Có nguy cơ", "Cần theo dõi"};
        String[] keys = {"NORMAL", "HIGH", "WATCH"};
        int[] icon = {R.drawable.ic_eco, R.drawable.ic_warning, R.drawable.ic_error};
        int[] bg = {R.drawable.bg_card_tint, R.drawable.bg_card_red, R.drawable.bg_card_warm};
        int[] tint = {R.color.green_ok, R.color.red, R.color.orange};
        LinearLayout dist = findViewById(R.id.list_dist);
        dist.removeAllViews();
        Ui.fill(dist, R.layout.item_dist, 3, (item, i) -> {
            Stats.Bucket b = null;
            for (Stats.Bucket x : s.distribution) if (keys[i].equals(x.riskLevel)) b = x;
            item.setBackgroundResource(bg[i]);
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(icon[i]);
            img.setColorFilter(getColor(tint[i]));
            TextView l = item.findViewById(R.id.label);
            l.setText(labels[i]);
            l.setTextColor(getColor(tint[i]));
            Ui.text(item, R.id.count, String.valueOf(b == null ? 0 : b.count));
            Ui.text(item, R.id.percent, String.format(new Locale("vi", "VN"), "%.1f%%", b == null ? 0.0 : b.percent));
        });
    }

    private void bindActivities(List<Stats.Activity> list) {
        LinearLayout box = findViewById(R.id.list_activity);
        box.removeAllViews();
        if (list.isEmpty()) {
            Ui.emptyNote(box, "Chưa có hoạt động nào", null);
            return;
        }
        Ui.fill(box, R.layout.item_activity, list.size(), (item, i) -> {
            Stats.Activity a = list.get(i);
            boolean scan = "SCAN".equals(a.type), post = "POST".equals(a.type);
            item.findViewById(R.id.icon_bg).setBackgroundResource(post ? R.drawable.bg_circle_blue : R.drawable.bg_circle_tint);
            ImageView img = item.findViewById(R.id.icon);
            img.setImageResource(scan ? R.drawable.ic_crop_free : post ? R.drawable.ic_group : R.drawable.ic_description);
            img.setColorFilter(getColor(post ? R.color.blue : R.color.primary));
            Ui.text(item, R.id.title, a.title);
            Ui.text(item, R.id.desc, a.description == null ? "" : a.description);
            Ui.text(item, R.id.time, Fmt.dateTime(a.at));
            item.setOnClickListener(v -> {
                if (scan) open(ResultDetailActivity.class, a.refId);
                else if (post) open(PostDetailActivity.class, a.refId);
                else open(AddDiaryActivity.class, a.refId);
            });
        });
    }
}

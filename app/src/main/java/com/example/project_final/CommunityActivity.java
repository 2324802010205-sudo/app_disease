package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.model.ImageRef;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.Page;
import com.example.project_final.model.Post;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.List;

/** Bảng tin cộng đồng nông dân: lọc theo chủ đề, tìm kiếm, thích, bình luận, chia sẻ, báo cáo. */
public class CommunityActivity extends BaseActivity {
    private final List<Post> posts = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private MetaOptions meta;
    private int topic;       // 0 = tất cả, i = meta.topics[i-1]
    private String query = "";
    private String nextCursor;
    private boolean loading;
    private Runnable pendingSearch;

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_community;
    }

    @Override
    protected void onReady() {
        findViewById(R.id.btn_my_posts).setOnClickListener(v -> {
            if (requireAccount("xem bài viết của bạn")) startActivity(new Intent(this, MyPostsActivity.class));
        });
        findViewById(R.id.btn_write).setOnClickListener(v -> {
            if (requireAccount("viết bài chia sẻ với cộng đồng")) startActivity(new Intent(this, CreatePostActivity.class));
        });
        findViewById(R.id.btn_more).setOnClickListener(v -> fetch(false));
        ((android.widget.EditText) findViewById(R.id.input_search)).addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString().trim();
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = () -> fetch(true);
                handler.postDelayed(pendingSearch, 400);
            }
        });
        withMeta(m -> {
            meta = m;
            buildChips();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetch(true);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static int topicIcon(String code) {
        if (code == null) return R.drawable.ic_grid;
        switch (code) {
            case "PEST":
                return R.drawable.ic_eco;
            case "EXPERIENCE":
                return R.drawable.ic_menu_book;
            case "VARIETY":
                return R.drawable.ic_sprout;
            case "WEATHER":
                return R.drawable.ic_cloud;
            default:
                return R.drawable.ic_water_drop;
        }
    }

    private void buildChips() {
        LinearLayout chips = findViewById(R.id.chips);
        chips.removeAllViews();
        chipViews.clear();
        int n = meta.topics.size() + 1;
        Ui.fill(chips, R.layout.item_chip, n, (item, i) -> {
            TextView tv = (TextView) item;
            tv.setText(i == 0 ? "Tất cả" : meta.topics.get(i - 1).label);
            tv.setCompoundDrawablePadding((int) Ui.dp(this, 6));
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(topicIcon(i == 0 ? null : meta.topics.get(i - 1).code), 0, 0, 0);
            tv.setOnClickListener(v -> {
                topic = i;
                restyle();
                fetch(true);
            });
            chipViews.add(tv);
        });
        restyle();
    }

    private void restyle() {
        for (int k = 0; k < chipViews.size(); k++) {
            boolean selected = k == topic;
            TextView tv = chipViews.get(k);
            int color = getColor(selected ? R.color.white : R.color.ink);
            tv.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            tv.setTextColor(color);
            tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
        }
    }

    /** Màu và biểu tượng nhãn chủ đề dùng chung cho bảng tin, chi tiết và bài của tôi. */
    static void topicPill(TextView tv, String code) {
        if (code == null) code = "";
        switch (code) {
            case "PEST":
                Ui.pill(tv, "Sâu bệnh", R.drawable.ic_eco, R.color.green_ok, R.drawable.bg_pill_green);
                break;
            case "EXPERIENCE":
                Ui.pill(tv, "Kinh nghiệm", R.drawable.ic_menu_book, R.color.amber, R.drawable.bg_pill_amber);
                break;
            case "VARIETY":
                Ui.pill(tv, "Giống lúa", R.drawable.ic_sprout, R.color.green_ok, R.drawable.bg_pill_green);
                break;
            case "WATER":
                Ui.pill(tv, "Quản lý nước", R.drawable.ic_water_drop, R.color.blue, R.drawable.bg_pill_blue);
                break;
            default:
                Ui.pill(tv, "Thời tiết", R.drawable.ic_cloud, R.color.blue, R.drawable.bg_pill_blue);
        }
    }

    private void fetch(boolean reset) {
        if (loading && !reset) return;
        loading = true;
        if (reset) {
            posts.clear();
            nextCursor = null;
        }
        String code = topic == 0 || meta == null ? null : meta.topics.get(topic - 1).code;
        call(api().posts(code, query.isEmpty() ? null : query, nextCursor, 10), new ApiCallback<Page<Post>>() {
            @Override
            public void onSuccess(Page<Post> page) {
                loading = false;
                posts.addAll(page.items);
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

    private void render() {
        findViewById(R.id.btn_more).setVisibility(nextCursor == null ? View.GONE : View.VISIBLE);
        LinearLayout list = findViewById(R.id.list_posts);
        list.removeAllViews();
        if (posts.isEmpty() && !loading) {
            Ui.emptyNote(list, query.isEmpty() ? "Chưa có bài viết nào trong chủ đề này.\nHãy là người đầu tiên chia sẻ!" : "Không tìm thấy bài viết phù hợp.", null);
            return;
        }
        for (Post p : posts) {
            View item = getLayoutInflater().inflate(R.layout.item_post, list, false);
            Img.avatar(item.findViewById(R.id.avatar), p.author == null ? null : p.author.avatarUrl);
            Ui.text(item, R.id.author, p.author == null ? "Người dùng" : p.author.name);
            Ui.text(item, R.id.meta, Fmt.relative(p.createdAt) + (p.province == null || p.province.isEmpty() ? "" : " · " + p.province));
            Ui.text(item, R.id.title, p.title);
            Ui.text(item, R.id.excerpt, p.content);
            topicPill(item.findViewById(R.id.topic), p.topic);
            bindPhotos(item.findViewById(R.id.photos), p.images);
            bindLike(item.findViewById(R.id.like), p);
            TextView comment = item.findViewById(R.id.comment);
            comment.setText(String.valueOf(p.commentCount));
            comment.setOnClickListener(v -> open(PostDetailActivity.class, p.id));
            item.findViewById(R.id.share).setOnClickListener(v -> share(p));
            item.findViewById(R.id.report).setOnClickListener(v -> report(p));
            item.setOnClickListener(v -> open(PostDetailActivity.class, p.id));
            list.addView(item);
        }
    }

    private void bindPhotos(LinearLayout box, List<ImageRef> images) {
        box.removeAllViews();
        if (images == null || images.isEmpty()) {
            box.setVisibility(View.GONE);
            return;
        }
        box.setVisibility(View.VISIBLE);
        int shown = Math.min(3, images.size());
        for (int i = 0; i < shown; i++) {
            ImageView iv = (ImageView) getLayoutInflater().inflate(R.layout.item_post_photo, box, false);
            Img.load(iv, images.get(i).url);
            box.addView(iv);
        }
    }

    private void bindLike(TextView like, Post p) {
        like.setText(String.valueOf(p.likeCount));
        like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(p.liked ? R.color.primary : R.color.ink_deep)));
        like.setOnClickListener(v -> {
            if (!requireAccount("thích bài viết")) return;
            boolean target = !p.liked;
            p.liked = target;
            p.likeCount += target ? 1 : -1;
            bindLike(like, p);
            call(target ? api().likePost(p.id) : api().unlikePost(p.id), new ApiCallback<Post.LikeResult>() {
                @Override
                public void onSuccess(Post.LikeResult r) {
                    p.liked = r.liked;
                    p.likeCount = r.likeCount;
                    bindLike(like, p);
                }

                @Override
                public void onError(ApiError e) {
                    p.liked = !target;
                    p.likeCount += target ? -1 : 1;
                    bindLike(like, p);
                    showError(e);
                }
            });
        });
    }

    private void share(Post p) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, p.title)
                .putExtra(Intent.EXTRA_TEXT, p.title + (p.shareUrl == null ? "" : "\n" + p.shareUrl));
        startActivity(Intent.createChooser(send, "Chia sẻ bài viết"));
    }

    private void report(Post p) {
        if (meta == null || !requireAccount("báo cáo bài viết")) return;
        List<String> labels = new ArrayList<>();
        for (Option o : meta.reportReasons) labels.add(o.label);
        new AlertDialog.Builder(this)
                .setTitle("Báo cáo bài viết")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    call(api().reportPost(p.id, new Requests.Report(meta.reportReasons.get(which).code, null)), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            toast("Đã gửi báo cáo. Cảm ơn bạn!");
                        }

                        @Override
                        public void onError(ApiError e) {
                            showError(e);
                        }
                    });
                })
                .show();
    }
}

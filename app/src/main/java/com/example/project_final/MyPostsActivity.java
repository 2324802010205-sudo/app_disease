package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.model.Page;
import com.example.project_final.model.Post;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.List;

/** Bài viết của tôi: đã đăng và bản nháp. */
public class MyPostsActivity extends BaseActivity {
    private final List<Post> published = new ArrayList<>();
    private final List<Post.Draft> drafts = new ArrayList<>();
    private boolean showDrafts;

    @Override
    protected int layout() {
        return R.layout.activity_my_posts;
    }

    @Override
    protected void onReady() {
        go(R.id.btn_new, CreatePostActivity.class);
        findViewById(R.id.tab_published).setOnClickListener(v -> switchTab(false));
        findViewById(R.id.tab_drafts).setOnClickListener(v -> switchTab(true));
        switchTab(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        call(api().myPosts(null, 30), new ApiCallback<Page<Post>>() {
            @Override
            public void onSuccess(Page<Post> page) {
                published.clear();
                published.addAll(page.items);
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
        call(api().drafts(null, 30), new ApiCallback<Page<Post.Draft>>() {
            @Override
            public void onSuccess(Page<Post.Draft> page) {
                drafts.clear();
                drafts.addAll(page.items);
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void switchTab(boolean showDraftTab) {
        showDrafts = showDraftTab;
        style(findViewById(R.id.tab_published), !showDraftTab);
        style(findViewById(R.id.tab_drafts), showDraftTab);
        render();
    }

    private void style(TextView tv, boolean selected) {
        tv.setBackgroundResource(selected ? R.drawable.bg_tab_selected : R.drawable.bg_chip);
        tv.setTextColor(getColor(selected ? R.color.white : R.color.ink));
        tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tv.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(selected ? R.color.white : R.color.ink)));
    }

    private void render() {
        ((TextView) findViewById(R.id.tab_published)).setText("Đã đăng (" + published.size() + ")");
        ((TextView) findViewById(R.id.tab_drafts)).setText("Bản nháp (" + drafts.size() + ")");
        LinearLayout list = findViewById(R.id.list_posts);
        list.removeAllViews();
        int count = showDrafts ? drafts.size() : published.size();
        if (count == 0) {
            Ui.emptyNote(list, showDrafts ? "Chưa có bản nháp nào." : "Bạn chưa đăng bài viết nào.\nChạm “Tạo bài mới” để chia sẻ với cộng đồng.", null);
            return;
        }
        for (int i = 0; i < count; i++) {
            View item = getLayoutInflater().inflate(R.layout.item_my_post, list, false);
            if (showDrafts) bindDraft(item, drafts.get(i));
            else bindPost(item, published.get(i));
            list.addView(item);
        }
    }

    private void bindPost(View item, Post p) {
        Img.load(item.findViewById(R.id.img), p.images == null || p.images.isEmpty() ? null : p.images.get(0).url, R.drawable.bg_image_placeholder);
        Ui.text(item, R.id.title, p.title);
        Ui.text(item, R.id.date, Fmt.dateLong(p.createdAt));
        Ui.text(item, R.id.likes, String.valueOf(p.likeCount));
        Ui.text(item, R.id.comments, String.valueOf(p.commentCount));
        CommunityActivity.topicPill(item.findViewById(R.id.topic), p.topic);
        item.findViewById(R.id.btn_edit).setOnClickListener(v -> open(CreatePostActivity.class, p.id));
        item.findViewById(R.id.btn_delete).setOnClickListener(v -> confirm("Xóa bài viết?", "\"" + p.title + "\" sẽ bị xóa khỏi cộng đồng.", () ->
                call(api().deletePost(p.id), afterDelete(() -> published.remove(p)))));
        item.setOnClickListener(v -> open(PostDetailActivity.class, p.id));
    }

    private void bindDraft(View item, Post.Draft d) {
        Img.load(item.findViewById(R.id.img), d.images == null || d.images.isEmpty() ? null : d.images.get(0).url, R.drawable.bg_image_placeholder);
        Ui.text(item, R.id.title, d.title == null || d.title.isEmpty() ? "(Chưa có tiêu đề)" : d.title);
        Ui.text(item, R.id.date, "Bản nháp · " + Fmt.relative(d.updatedAt));
        item.findViewById(R.id.likes).setVisibility(View.GONE);
        item.findViewById(R.id.comments).setVisibility(View.GONE);
        CommunityActivity.topicPill(item.findViewById(R.id.topic), d.topic);
        item.findViewById(R.id.btn_edit).setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class).putExtra(CreatePostActivity.EXTRA_DRAFT_ID, d.id)));
        item.findViewById(R.id.btn_delete).setOnClickListener(v -> confirm("Xóa bản nháp?", "Bản nháp này sẽ bị xóa.", () ->
                call(api().deleteDraft(d.id), afterDelete(() -> drafts.remove(d)))));
        item.setOnClickListener(v ->
                startActivity(new Intent(this, CreatePostActivity.class).putExtra(CreatePostActivity.EXTRA_DRAFT_ID, d.id)));
    }

    private ApiCallback<Void> afterDelete(Runnable remove) {
        return new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                remove.run();
                render();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        };
    }

    private void confirm(String title, String message, Runnable yes) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> yes.run())
                .show();
    }
}

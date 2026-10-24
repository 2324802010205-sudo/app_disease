package com.example.project_final;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.Comment;
import com.example.project_final.model.ImageRef;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.Post;
import com.example.project_final.util.Fmt;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.List;

/** Chi tiết bài viết cộng đồng cùng bình luận và trả lời. */
public class PostDetailActivity extends BaseActivity {
    private String id;
    private Post post;
    private MetaOptions meta;
    private String sort = "newest";
    private Comment replyTo;
    private EditText input;

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_post_detail;
    }

    @Override
    protected void onReady() {
        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        input = findViewById(R.id.input_comment);
        Img.avatar(findViewById(R.id.my_avatar), Session.user() == null ? null : Session.user().avatarUrl);
        if (isGuest()) {
            // Khách chỉ xem: ô bình luận không nhập được, chạm vào sẽ mời đăng nhập/đăng ký.
            input.setHint("Đăng nhập để bình luận");
            input.setFocusable(false);
            input.setOnClickListener(v -> requireAccount("bình luận"));
        }
        withMeta(m -> meta = m);

        findViewById(R.id.like).setOnClickListener(v -> toggleLike());
        findViewById(R.id.save).setOnClickListener(v -> toggleSave());
        findViewById(R.id.share).setOnClickListener(v -> share());
        findViewById(R.id.post_menu).setOnClickListener(v -> postMenu(v));
        findViewById(R.id.btn_send).setOnClickListener(v -> sendComment());
        findViewById(R.id.reply_hint).setOnClickListener(v -> setReplyTarget(null));

        TextView sortView = findViewById(R.id.dd_sort);
        Ui.dropdown(sortView, new String[]{"Mới nhất", "Hữu ích nhất"}, i -> {
            sort = i == 0 ? "newest" : "helpful";
            loadComments();
        });
        loadPost();
        loadComments();
    }

    // ---- bài viết

    private void loadPost() {
        call(api().post(id), new ApiCallback<Post>() {
            @Override
            public void onSuccess(Post p) {
                post = p;
                bindPost();
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
                finish();
            }
        });
    }

    private void bindPost() {
        Img.avatar(findViewById(R.id.post_avatar), post.author == null ? null : post.author.avatarUrl);
        setText(R.id.post_author, post.author == null ? "Người dùng" : post.author.name);
        setText(R.id.post_meta, Fmt.relative(post.createdAt) + (post.province == null || post.province.isEmpty() ? "" : " · " + post.province));
        CommunityActivity.topicPill(findViewById(R.id.post_topic), post.topic);
        setText(R.id.post_title, post.title);
        setText(R.id.post_body, post.content);
        setText(R.id.comment_count, String.valueOf(post.commentCount));
        setText(R.id.comments_title, "Bình luận (" + post.commentCount + ")");
        bindLike();
        bindSave();

        LinearLayout photos = findViewById(R.id.post_photos);
        photos.removeAllViews();
        if (post.images != null) {
            for (ImageRef r : post.images) {
                com.google.android.material.imageview.ShapeableImageView iv = new com.google.android.material.imageview.ShapeableImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setShapeAppearanceModel(iv.getShapeAppearanceModel().toBuilder().setAllCornerSizes(Ui.dp(this, 16)).build());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, (int) Ui.dp(this, 210));
                lp.topMargin = (int) Ui.dp(this, 12);
                photos.addView(iv, lp);
                Img.load(iv, r.url);
                iv.setOnClickListener(v -> startActivity(new Intent(this, PhotoViewActivity.class).putExtra(PhotoViewActivity.EXTRA_URL, r.url)));
            }
        }
    }

    private void bindLike() {
        TextView like = findViewById(R.id.like);
        like.setText(String.valueOf(post.likeCount));
        like.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(post.liked ? R.color.primary : R.color.ink_deep)));
    }

    private void bindSave() {
        TextView save = findViewById(R.id.save);
        save.setCompoundDrawablesRelativeWithIntrinsicBounds(post.saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border, 0, 0, 0);
        save.setText(post.saved ? "Đã lưu" : "Lưu");
    }

    private void toggleLike() {
        if (post == null || !requireAccount("thích bài viết")) return;
        boolean target = !post.liked;
        post.liked = target;
        post.likeCount += target ? 1 : -1;
        bindLike();
        call(target ? api().likePost(id) : api().unlikePost(id), new ApiCallback<Post.LikeResult>() {
            @Override
            public void onSuccess(Post.LikeResult r) {
                post.liked = r.liked;
                post.likeCount = r.likeCount;
                bindLike();
            }

            @Override
            public void onError(ApiError e) {
                post.liked = !target;
                post.likeCount += target ? -1 : 1;
                bindLike();
                showError(e);
            }
        });
    }

    private void toggleSave() {
        if (post == null || !requireAccount("lưu bài viết")) return;
        boolean target = !post.saved;
        call(target ? api().savePost(id) : api().unsavePost(id), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void body) {
                post.saved = target;
                bindSave();
                toast(target ? "Đã lưu bài viết" : "Đã bỏ lưu bài viết");
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void share() {
        if (post == null) return;
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, post.title)
                .putExtra(Intent.EXTRA_TEXT, post.title + (post.shareUrl == null ? "" : "\n" + post.shareUrl));
        startActivity(Intent.createChooser(send, "Chia sẻ bài viết"));
    }

    private void postMenu(View anchor) {
        if (post == null || !requireAccount("quản lý hoặc báo cáo bài viết")) return;
        PopupMenu menu = new PopupMenu(this, anchor);
        if (post.mine) {
            menu.getMenu().add("Sửa bài viết");
            menu.getMenu().add("Xóa bài viết");
        } else {
            menu.getMenu().add("Báo cáo bài viết");
        }
        menu.setOnMenuItemClickListener(item -> {
            String t = item.getTitle().toString();
            if (t.startsWith("Sửa")) startActivity(new Intent(this, CreatePostActivity.class).putExtra(EXTRA_ID, id));
            else if (t.startsWith("Xóa")) confirmDeletePost();
            else report(null);
            return true;
        });
        menu.show();
    }

    private void confirmDeletePost() {
        new AlertDialog.Builder(this)
                .setTitle("Xóa bài viết?")
                .setMessage("Bài viết và toàn bộ bình luận sẽ bị xóa khỏi cộng đồng.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa", (d, w) -> {
                    busy(true);
                    call(api().deletePost(id), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void body) {
                            busy(false);
                            toast("Đã xóa bài viết");
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

    /** Báo cáo bài viết (comment == null) hoặc một bình luận. */
    private void report(Comment comment) {
        if (meta == null) return;
        List<String> labels = new ArrayList<>();
        for (Option o : meta.reportReasons) labels.add(o.label);
        new AlertDialog.Builder(this)
                .setTitle(comment == null ? "Báo cáo bài viết" : "Báo cáo bình luận")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    Requests.Report body = new Requests.Report(meta.reportReasons.get(which).code, null);
                    call(comment == null ? api().reportPost(id, body) : api().reportComment(id, comment.id, body), new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void r) {
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

    // ---- bình luận

    private void loadComments() {
        call(api().comments(id, sort), new ApiCallback<List<Comment>>() {
            @Override
            public void onSuccess(List<Comment> list) {
                renderComments(list);
            }

            @Override
            public void onError(ApiError e) {
                showError(e);
            }
        });
    }

    private void renderComments(List<Comment> list) {
        LinearLayout box = findViewById(R.id.list_comments);
        box.removeAllViews();
        if (list.isEmpty()) {
            Ui.emptyNote(box, "Chưa có bình luận. Hãy là người đầu tiên!", null);
            return;
        }
        for (int index = 0; index < list.size(); index++) {
            Comment c = list.get(index);
            View item = getLayoutInflater().inflate(R.layout.item_comment, box, false);
            if (index > 0) item.findViewById(R.id.divider).setVisibility(View.VISIBLE);
            Img.avatar(item.findViewById(R.id.avatar), c.author == null ? null : c.author.avatarUrl);
            Ui.text(item, R.id.author, c.author == null ? "Người dùng" : c.author.name);
            bindBadge(item.findViewById(R.id.badge), c.badge);
            Ui.text(item, R.id.time, Fmt.relative(c.createdAt));
            Ui.text(item, R.id.text, c.text);
            bindCommentLike(item.findViewById(R.id.likes), c);
            item.findViewById(R.id.reply).setOnClickListener(v -> setReplyTarget(c));
            item.findViewById(R.id.btn_menu).setOnClickListener(v -> commentMenu(v, c));

            LinearLayout replies = item.findViewById(R.id.replies);
            for (Comment r : c.replies) {
                View rv = getLayoutInflater().inflate(R.layout.item_reply, replies, false);
                Img.avatar(rv.findViewById(R.id.avatar), r.author == null ? null : r.author.avatarUrl);
                Ui.text(rv, R.id.author, r.author == null ? "Người dùng" : r.author.name);
                bindBadge(rv.findViewById(R.id.badge), r.badge);
                Ui.text(rv, R.id.time, Fmt.relative(r.createdAt));
                Ui.text(rv, R.id.text, r.text);
                rv.setOnLongClickListener(v -> {
                    commentMenu(v, r);
                    return true;
                });
                replies.addView(rv);
            }
            box.addView(item);
        }
    }

    private void bindBadge(TextView badge, String kind) {
        if ("EXPERT".equals(kind)) {
            badge.setVisibility(View.VISIBLE);
            Ui.pill(badge, "Chuyên gia", 0, R.color.green_ok, R.drawable.bg_pill_green);
        } else if ("AUTHOR".equals(kind)) {
            badge.setVisibility(View.VISIBLE);
            Ui.pill(badge, "Tác giả", 0, R.color.blue, R.drawable.bg_pill_blue);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    private void bindCommentLike(TextView likes, Comment c) {
        likes.setText(String.valueOf(c.likeCount));
        likes.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(c.liked ? R.color.primary : R.color.ink)));
        likes.setOnClickListener(v -> {
            if (!requireAccount("thích bình luận")) return;
            boolean target = !c.liked;
            c.liked = target;
            c.likeCount += target ? 1 : -1;
            bindCommentLike(likes, c);
            call(target ? api().likeComment(id, c.id) : api().unlikeComment(id, c.id), new ApiCallback<Void>() {
                @Override public void onSuccess(Void body) {}

                @Override
                public void onError(ApiError e) {
                    c.liked = !target;
                    c.likeCount += target ? -1 : 1;
                    bindCommentLike(likes, c);
                    showError(e);
                }
            });
        });
    }

    private void commentMenu(View anchor, Comment c) {
        PopupMenu menu = new PopupMenu(this, anchor);
        if (c.mine) menu.getMenu().add("Xóa bình luận");
        else menu.getMenu().add("Báo cáo bình luận");
        menu.setOnMenuItemClickListener(item -> {
            if (c.mine) {
                call(api().deleteComment(id, c.id), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void body) {
                        toast("Đã xóa bình luận");
                        loadComments();
                        loadPost();
                    }

                    @Override
                    public void onError(ApiError e) {
                        showError(e);
                    }
                });
            } else {
                report(c);
            }
            return true;
        });
        menu.show();
    }

    private void setReplyTarget(Comment c) {
        if (c != null && !requireAccount("trả lời bình luận")) return;
        replyTo = c;
        TextView hint = findViewById(R.id.reply_hint);
        if (c == null) {
            hint.setVisibility(View.GONE);
            input.setHint("Viết bình luận...");
        } else {
            hint.setVisibility(View.VISIBLE);
            hint.setText("Đang trả lời " + (c.author == null ? "bình luận" : c.author.name));
            input.setHint("Viết câu trả lời...");
            input.requestFocus();
        }
    }

    private void sendComment() {
        if (!requireAccount("bình luận")) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        Requests.CommentBody body = new Requests.CommentBody();
        body.text = text;
        body.parentId = replyTo == null ? null : replyTo.id;
        busy(true);
        call(api().addComment(id, body), new ApiCallback<Comment>() {
            @Override
            public void onSuccess(Comment c) {
                busy(false);
                input.setText("");
                setReplyTarget(null);
                loadComments();
                loadPost();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                showError(e);
            }
        });
    }
}

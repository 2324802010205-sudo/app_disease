package com.example.project_final;

import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.project_final.api.ApiCallback;
import com.example.project_final.api.ApiError;
import com.example.project_final.api.Requests;
import com.example.project_final.api.Session;
import com.example.project_final.model.ImageRef;
import com.example.project_final.model.MetaOptions;
import com.example.project_final.model.Option;
import com.example.project_final.model.Post;
import com.example.project_final.util.Forms;
import com.example.project_final.util.ImageUploader;
import com.example.project_final.util.ImagePicker;
import com.example.project_final.util.Img;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Viết bài mới. Mở kèm EXTRA_ID để sửa bài đã đăng, hoặc EXTRA_DRAFT_ID để tiếp tục bản nháp.
 */
public class CreatePostActivity extends BaseActivity {
    public static final String EXTRA_DRAFT_ID = "draftId";
    private static final int MAX_IMAGES = 5;

    private final List<ImageRef> keptImages = new ArrayList<>();
    private final List<Uri> newImages = new ArrayList<>();
    private MetaOptions meta;
    private String postId;
    private String draftId;
    private String topic;
    private ImagePicker picker;
    private EditText title, content;

    @Override
    protected int layout() {
        return R.layout.activity_create_post;
    }

    @Override
    protected void onReady() {
        postId = getIntent().getStringExtra(EXTRA_ID);
        draftId = getIntent().getStringExtra(EXTRA_DRAFT_ID);
        setText(R.id.screen_title, postId == null ? "Tạo bài viết" : "Sửa bài viết");
        title = findViewById(R.id.input_title);
        content = findViewById(R.id.input_content);
        TextView previewTitle = findViewById(R.id.preview_title);
        TextView previewContent = findViewById(R.id.preview_content);
        setText(R.id.preview_author, Session.user() == null ? "Bạn" : Session.user().fullName);

        title.addTextChangedListener(new Watcher(s -> {
            ((TextView) findViewById(R.id.counter_title)).setText(s.length() + "/100");
            previewTitle.setText(s.length() == 0 ? "Tiêu đề bài viết của bạn sẽ hiển thị ở đây" : s);
        }));
        content.addTextChangedListener(new Watcher(s -> {
            ((TextView) findViewById(R.id.counter_content)).setText(s.length() + "/2000");
            previewContent.setText(s.length() == 0 ? "Nội dung bài viết của bạn sẽ hiển thị ở đây..." : s);
        }));

        picker = new ImagePicker(this, MAX_IMAGES, uris -> {
            for (Uri u : uris) if (keptImages.size() + newImages.size() < MAX_IMAGES) newImages.add(u);
            renderPhotos();
        });
        renderPhotos();

        withMeta(m -> {
            meta = m;
            TextView dd = findViewById(R.id.dd_topic);
            List<String> labels = new ArrayList<>();
            for (Option o : m.topics) labels.add(o.label);
            Ui.picker(dd, "Chọn chủ đề", labels, (i, label) -> topic = m.topics.get(i).code);
            if (topic != null) {
                dd.setText(MetaOptions.labelOf(m.topics, topic));
                dd.setTextColor(getColor(R.color.ink_deep));
            }
        });

        findViewById(R.id.btn_preview).setOnClickListener(v -> findViewById(R.id.preview_title).requestFocus());
        findViewById(R.id.btn_draft).setOnClickListener(v -> save(false));
        findViewById(R.id.btn_publish).setOnClickListener(v -> save(true));

        if (postId != null) {
            setText(R.id.screen_title, "Sửa bài viết");
            findViewById(R.id.btn_draft).setVisibility(View.GONE);
            call(api().post(postId), new ApiCallback<Post>() {
                @Override
                public void onSuccess(Post p) {
                    fill(p.title, p.content, p.topic, p.images);
                }

                @Override
                public void onError(ApiError e) {
                    showError(e);
                    finish();
                }
            });
        } else if (draftId != null) {
            call(api().drafts(null, 30), new ApiCallback<com.example.project_final.model.Page<Post.Draft>>() {
                @Override
                public void onSuccess(com.example.project_final.model.Page<Post.Draft> page) {
                    for (Post.Draft d : page.items) if (d.id.equals(draftId)) fill(d.title, d.content, d.topic, d.images);
                }

                @Override
                public void onError(ApiError e) {
                    showError(e);
                }
            });
        }
    }

    private void fill(String t, String c, String tp, List<ImageRef> images) {
        title.setText(t);
        content.setText(c);
        topic = tp;
        TextView dd = findViewById(R.id.dd_topic);
        if (meta != null && tp != null) {
            dd.setText(MetaOptions.labelOf(meta.topics, tp));
            dd.setTextColor(getColor(R.color.ink_deep));
        }
        keptImages.clear();
        if (images != null) keptImages.addAll(images);
        renderPhotos();
    }

    private void renderPhotos() {
        LinearLayout slots = findViewById(R.id.list_photos);
        slots.removeAllViews();
        int total = keptImages.size() + newImages.size();
        setText(R.id.counter_photos, total + "/" + MAX_IMAGES);
        Ui.fill(slots, R.layout.item_photo_slot, MAX_IMAGES, (item, i) -> {
            FrameLayout slot = (FrameLayout) item;
            ((LinearLayout.LayoutParams) item.getLayoutParams()).setMarginEnd(i == MAX_IMAGES - 1 ? 0 : (int) Ui.dp(this, 6));
            if (i < keptImages.size() + newImages.size()) {
                slot.removeAllViews();
                slot.setBackgroundResource(R.drawable.bg_card);
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                slot.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                if (i < keptImages.size()) Img.load(iv, keptImages.get(i).url);
                else iv.setImageURI(newImages.get(i - keptImages.size()));
                slot.setOnClickListener(v -> removePhoto(i));
                slot.setContentDescription("Ảnh " + (i + 1) + ", chạm để xóa");
            } else if (i == total) {
                slot.setBackgroundResource(R.drawable.bg_dashed_box);
                ((ImageView) slot.findViewById(R.id.icon)).setImageResource(R.drawable.ic_add_a_photo);
                ((ImageView) slot.findViewById(R.id.icon)).setColorFilter(getColor(R.color.primary));
                slot.setContentDescription("Thêm ảnh");
                slot.setOnClickListener(v -> picker.show());
            }
        });
    }

    private void removePhoto(int index) {
        if (index < keptImages.size()) keptImages.remove(index);
        else newImages.remove(index - keptImages.size());
        renderPhotos();
    }

    /** publish=true: đăng bài; false: lưu bản nháp. */
    private void save(boolean publish) {
        if (publish) {
            if (!Forms.require(title, "Nhập tiêu đề bài viết")) return;
            if (!Forms.require(content, "Nhập nội dung bài viết")) return;
            if (topic == null) {
                toast("Hãy chọn chủ đề cho bài viết");
                return;
            }
        }
        busy(true);
        uploadImages(newImages, "post", new ImageUploader.Callback() {
            @Override
            public void onDone(List<ImageRef> refs) {
                List<ImageRef> all = new ArrayList<>(keptImages);
                all.addAll(refs);
                send(publish, all);
            }

            @Override
            public void onError(String message) {
                busy(false);
                toast(message);
            }
        });
    }

    private void send(boolean publish, List<ImageRef> images) {
        Requests.PostBody b = new Requests.PostBody();
        b.topic = topic;
        b.title = Forms.text(title);
        b.content = Forms.text(content);
        b.images = images;

        if (!publish) {
            retrofit2.Call<Post.Draft> request = draftId == null ? api().createDraft(b) : api().updateDraft(draftId, b);
            call(request, new ApiCallback<Post.Draft>() {
                @Override
                public void onSuccess(Post.Draft d) {
                    busy(false);
                    toast("Đã lưu bản nháp");
                    finish();
                }

                @Override
                public void onError(ApiError e) {
                    busy(false);
                    showError(e);
                }
            });
            return;
        }
        if (postId != null) {
            call(api().updatePost(postId, b), publishCallback());
        } else if (draftId != null) {
            // cập nhật nội dung nháp rồi đăng chính bản nháp đó
            call(api().updateDraft(draftId, b), new ApiCallback<Post.Draft>() {
                @Override
                public void onSuccess(Post.Draft d) {
                    call(api().publishDraft(draftId), publishCallback());
                }

                @Override
                public void onError(ApiError e) {
                    busy(false);
                    showError(e);
                }
            });
        } else {
            call(api().createPost(b), publishCallback());
        }
    }

    private ApiCallback<Post> publishCallback() {
        return new ApiCallback<Post>() {
            @Override
            public void onSuccess(Post p) {
                busy(false);
                toast(postId != null ? "Đã cập nhật bài viết" : "Đã đăng bài");
                finish();
            }

            @Override
            public void onError(ApiError e) {
                busy(false);
                if (!Forms.showFieldErrors(e, Map.of("title", title, "content", content))) showError(e);
            }
        };
    }

    private static final class Watcher implements TextWatcher {
        interface OnChange {
            void run(CharSequence s);
        }

        private final OnChange onChange;

        Watcher(OnChange onChange) {
            this.onChange = onChange;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) { onChange.run(s); }
    }
}

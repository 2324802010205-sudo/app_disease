package com.example.project_final;

import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.project_final.view.ZoomImageView;

import java.io.File;

/** Xem ảnh toàn màn hình, chụm để phóng to. Nhận EXTRA_URL (địa chỉ http) hoặc EXTRA_PATH (tệp cục bộ). */
public class PhotoViewActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_PATH = "path";

    @Override
    protected boolean allowsGuest() {
        return true;
    }

    @Override
    protected int layout() {
        return R.layout.activity_photo_view;
    }

    @Override
    protected boolean padTopInset() {
        return false;
    }

    @Override
    protected void onReady() {
        String url = getIntent().getStringExtra(EXTRA_URL);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        Object source = path != null ? new File(path) : url;
        if (source == null) {
            finish();
            return;
        }
        View loading = findViewById(R.id.loading);
        ZoomImageView zoom = findViewById(R.id.zoom);
        Glide.with(this).load(source).listener(new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                loading.setVisibility(View.GONE);
                toast("Không tải được ảnh");
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                loading.setVisibility(View.GONE);
                return false;
            }
        }).into(zoom);
    }
}

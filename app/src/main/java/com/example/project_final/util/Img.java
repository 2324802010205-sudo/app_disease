package com.example.project_final.util;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.project_final.R;

/** Nạp ảnh từ URL (Cloudinary hoặc backend) bằng Glide, có ảnh thay thế khi chưa có hoặc lỗi. */
public final class Img {
    private Img() {}

    public static void load(ImageView view, String url, @DrawableRes int fallback) {
        if (url == null || url.isEmpty()) {
            Glide.with(view).clear(view);
            view.setImageResource(fallback);
            return;
        }
        Glide.with(view).load(url)
                .placeholder(R.drawable.bg_image_placeholder)
                .error(fallback)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(view);
    }

    public static void load(ImageView view, String url) {
        load(view, url, R.drawable.bg_image_placeholder);
    }

    public static void avatar(ImageView view, String url) {
        load(view, url, R.drawable.ic_avatar_default);
    }
}

package com.example.project_final.util;

import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.project_final.R;
import com.example.project_final.model.PublicUser;

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

    /** Local portraits for sample community members; uploaded profile photos take priority. */
    public static void communityAvatar(ImageView view, PublicUser author) {
        int portrait = R.drawable.ic_avatar_default;
        if (author != null && author.id != null) {
            switch (author.id) {
                case "demo-hai": portrait = R.drawable.avatar_photo_hai; break;
                case "demo-lan": portrait = R.drawable.avatar_photo_lan; break;
                case "demo-tam": portrait = R.drawable.avatar_photo_tam; break;
                case "demo-nga": portrait = R.drawable.avatar_photo_nga; break;
                case "demo-expert": portrait = R.drawable.avatar_photo_khoa; break;
            }
        }
        view.setContentDescription(author == null || author.name == null ? "Ảnh đại diện" : "Ảnh đại diện " + author.name);
        Object source = author == null || author.avatarUrl == null || author.avatarUrl.trim().isEmpty()
                ? portrait : author.avatarUrl;
        Glide.with(view).load(source).circleCrop().override(192, 192).disallowHardwareConfig()
                .placeholder(R.drawable.ic_avatar_default).error(portrait).into(view);
    }
}

package com.example.project_final.util;

import androidx.annotation.DrawableRes;

import com.example.project_final.R;

/** Ảnh minh họa đi kèm app cho các bệnh trong danh mục, dùng khi bệnh chưa có ảnh trên Cloudinary (imageUrl rỗng). */
public final class DiseaseArt {
    private DiseaseArt() {}

    @DrawableRes
    public static int of(String diseaseId) {
        if (diseaseId == null) return R.drawable.bg_image_placeholder;
        switch (diseaseId) {
            case "dao-on-la":
                return R.drawable.img_lib_1;
            case "bac-la":
                return R.drawable.img_lib_2;
            case "dom-nau":
                return R.drawable.img_lib_3;
            case "kho-van":
                return R.drawable.img_lib_4;
            case "ri-sat":
                return R.drawable.img_saved_5;
            case "lun-xoan-la":
                return R.drawable.img_saved_6;
            default:
                return R.drawable.bg_image_placeholder;
        }
    }

    @DrawableRes
    public static int hero(String diseaseId) {
        return "dao-on-la".equals(diseaseId) ? R.drawable.img_dis_hero : of(diseaseId);
    }
}

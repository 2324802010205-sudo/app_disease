package com.riceguard.api.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slug {
    private Slug() {}

    /** Bỏ dấu tiếng Việt, hạ chữ thường: "Thái Bình" -> "thai-binh". */
    public static String of(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim().replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return n;
    }

    /** Dạng so khớp tìm kiếm: bỏ dấu, hạ chữ thường, giữ khoảng trắng. */
    public static String fold(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.replace('đ', 'd').replace('Đ', 'D'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }
}

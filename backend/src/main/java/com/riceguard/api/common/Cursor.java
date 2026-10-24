package com.riceguard.api.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Con trỏ phân trang = (createdAt, id) mã hóa base64url. */
public record Cursor(long createdAt, String id) {

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((createdAt + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String s = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int i = s.indexOf(':');
            return new Cursor(Long.parseLong(s.substring(0, i)), s.substring(i + 1));
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Con trỏ phân trang không hợp lệ");
        }
    }

    public static int limit(Integer requested, int def, int max) {
        if (requested == null) return def;
        return Math.max(1, Math.min(requested, max));
    }
}

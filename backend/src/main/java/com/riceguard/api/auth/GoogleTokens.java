package com.riceguard.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Đọc email từ Google ID token KHÔNG kiểm tra chữ ký. Chỉ dùng để quyết định nhánh xử lý (đã có tài khoản chưa);
 * việc xác thực thật do Firebase thực hiện khi đổi token ({@link IdentityToolkitClient#signInWithGoogle}).
 * Hỗ trợ cả JWT (header.payload.signature) và JSON thô mà Firebase Auth Emulator chấp nhận.
 */
final class GoogleTokens {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoogleTokens() {}

    static String emailOf(String idToken) {
        if (idToken == null || idToken.isBlank()) return null;
        try {
            String json = idToken.trim();
            if (!json.startsWith("{")) {
                String[] parts = json.split("\\.");
                if (parts.length < 2) return null;
                json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            }
            JsonNode email = MAPPER.readTree(json).path("email");
            return email.isTextual() && !email.asText().isBlank() ? email.asText().trim().toLowerCase(Locale.ROOT) : null;
        } catch (Exception e) {
            return null;
        }
    }
}

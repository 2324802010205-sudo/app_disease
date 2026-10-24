package com.riceguard.api.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoogleTokensTest {
    private static String jwt(String payload) {
        Base64.Encoder e = Base64.getUrlEncoder().withoutPadding();
        return e.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
                + e.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + ".sig";
    }

    @Test
    void readsEmailFromJwtPayload() {
        assertEquals("lan@gmail.com", GoogleTokens.emailOf(jwt("{\"sub\":\"1\",\"email\":\"Lan@Gmail.com\"}")));
    }

    @Test
    void readsEmailFromEmulatorJson() {
        assertEquals("a@b.vn", GoogleTokens.emailOf("{\"sub\":\"1\",\"email\":\"a@b.vn\",\"email_verified\":true}"));
    }

    @Test
    void returnsNullForGarbage() {
        assertNull(GoogleTokens.emailOf(null));
        assertNull(GoogleTokens.emailOf(""));
        assertNull(GoogleTokens.emailOf("not-a-token"));
        assertNull(GoogleTokens.emailOf(jwt("{\"sub\":\"1\"}")));
    }
}

package com.example.project_final.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class ApiErrorTest {
    @Test
    public void parsesBackendErrorBody() {
        String json = "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Email không hợp lệ\",\"status\":400,\"fields\":{\"email\":\"Email không hợp lệ\"}}";
        ApiError e = ApiError.from(Response.error(400, ResponseBody.create(json, MediaType.parse("application/json"))));
        assertEquals("VALIDATION_FAILED", e.code);
        assertEquals("Email không hợp lệ", e.message);
        assertEquals("Email không hợp lệ", e.fields.get("email"));
        assertEquals(400, e.status);
        assertFalse(e.isNetwork());
    }

    @Test
    public void keepsFriendlyMessageWhenBodyIsNotJson() {
        ApiError e = ApiError.from(Response.error(502, ResponseBody.create("<html>Bad gateway</html>", MediaType.parse("text/html"))));
        assertEquals("Máy chủ đang gặp sự cố, vui lòng thử lại sau", e.message);
    }

    @Test
    public void marksUnauthenticated() {
        ApiError e = ApiError.from(Response.error(401, ResponseBody.create("{}", MediaType.parse("application/json"))));
        assertTrue(e.isUnauthenticated());
    }

    @Test
    public void networkFailuresAreFlagged() {
        assertTrue(ApiError.from(new UnknownHostException("x")).isNetwork());
        assertTrue(ApiError.from(new IOException("x")).isNetwork());
        ApiError timeout = ApiError.from(new SocketTimeoutException("x"));
        assertEquals(ApiError.TIMEOUT, timeout.code);
        assertTrue(timeout.isNetwork());
    }
}

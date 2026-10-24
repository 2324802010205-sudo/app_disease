package com.example.project_final.api;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

import okhttp3.ResponseBody;

/** Lỗi đã chuẩn hóa từ backend ({code, message, status, fields}) hoặc từ tầng mạng. Message luôn là tiếng Việt, hiển thị thẳng cho người dùng. */
public class ApiError {
    public static final String NETWORK = "NETWORK";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String UNKNOWN = "UNKNOWN";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String GOOGLE_NOT_REGISTERED = "GOOGLE_NOT_REGISTERED";

    public String code = UNKNOWN;
    public String message = "Có lỗi xảy ra, vui lòng thử lại";
    public int status;
    public Map<String, String> fields;
    /** Mã yêu cầu gửi kèm header X-Request-Id, dùng để tra log phía backend khi gỡ lỗi. */
    public transient String requestId;

    public static ApiError from(retrofit2.Response<?> response) {
        ApiError e = new ApiError();
        e.status = response.code();
        e.requestId = response.raw().request().header(ApiClient.HEADER_REQUEST_ID);
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                ApiError parsed = new Gson().fromJson(body.string(), ApiError.class);
                if (parsed != null) {
                    if (parsed.code != null) e.code = parsed.code;
                    if (parsed.message != null) e.message = parsed.message;
                    e.fields = parsed.fields;
                }
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // thân phản hồi không phải JSON lỗi của backend (ví dụ lỗi từ proxy): giữ thông điệp mặc định
        }
        if (e.status >= 500 && UNKNOWN.equals(e.code)) e.message = "Máy chủ đang gặp sự cố, vui lòng thử lại sau";
        return e;
    }

    public static ApiError from(Throwable t) {
        ApiError e = new ApiError();
        if (t instanceof SocketTimeoutException) {
            e.code = TIMEOUT;
            e.message = "Máy chủ phản hồi quá lâu, vui lòng thử lại";
        } else if (t instanceof UnknownHostException || t instanceof ConnectException || t instanceof IOException) {
            e.code = NETWORK;
            e.message = "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại";
        } else {
            e.message = "Có lỗi xảy ra, vui lòng thử lại";
        }
        return e;
    }

    public boolean isNetwork() {
        return NETWORK.equals(code) || TIMEOUT.equals(code);
    }

    public boolean isUnauthenticated() {
        return status == 401;
    }

    @Override
    public String toString() {
        return code + " (" + status + "): " + message + (requestId == null ? "" : " [req " + requestId + "]");
    }
}

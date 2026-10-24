package com.riceguard.api.common;

import java.util.Map;

public record ErrorResponse(String code, String message, int status, String path, long timestamp, Map<String, String> fields) {
    public static ErrorResponse of(String code, String message, int status, String path) {
        return new ErrorResponse(code, message, status, path, System.currentTimeMillis(), null);
    }
}

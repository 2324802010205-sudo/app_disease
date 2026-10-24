package com.riceguard.api.security;

/** Người dùng đã xác thực, lấy từ Firebase ID token. */
public record AuthUser(String uid, String email, String name, boolean admin) {}

package com.example.project_final.model;

public class AuthResponse {
    public String idToken;
    public String refreshToken;
    public long expiresIn;
    public User user; // không có ở phản hồi /auth/refresh
}

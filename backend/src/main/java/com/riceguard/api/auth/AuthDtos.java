package com.riceguard.api.auth;

import com.riceguard.api.user.UserDtos.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Họ và tên không được để trống") @Size(max = 80, message = "Họ và tên tối đa 80 ký tự") String fullName,
            @NotBlank(message = "Email không được để trống") @Email(message = "Email không hợp lệ") String email,
            @NotBlank(message = "Mật khẩu không được để trống") @Size(min = 8, max = 72, message = "Mật khẩu cần từ 8 đến 72 ký tự") String password,
            @Pattern(regexp = "^$|^[0-9+ ()-]{8,20}$", message = "Số điện thoại không hợp lệ") String phone,
            @Size(max = 60) String province,
            @Pattern(regexp = "^(USER|FARMER|EXPERT)?$", message = "Vai trò không hợp lệ") String role) {}

    public record LoginRequest(
            @NotBlank(message = "Email không được để trống") @Email(message = "Email không hợp lệ") String email,
            @NotBlank(message = "Mật khẩu không được để trống") String password) {}

    /** idToken: Google ID token lấy từ Google Sign-In trên Android. mode: LOGIN (chỉ cho tài khoản đã có) | REGISTER (tạo nếu chưa có). */
    public record GoogleRequest(
            @NotBlank(message = "Thiếu mã xác thực Google") String idToken,
            @Pattern(regexp = "^(LOGIN|REGISTER)$", message = "Chế độ không hợp lệ") String mode) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record ForgotPasswordRequest(@NotBlank(message = "Email không được để trống") @Email(message = "Email không hợp lệ") String email) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Nhập mật khẩu hiện tại") String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = "Mật khẩu mới cần từ 8 đến 72 ký tự") String newPassword) {}

    public record AuthResponse(String idToken, String refreshToken, long expiresIn, UserDto user) {}

    public record TokenResponse(String idToken, String refreshToken, long expiresIn) {}
}

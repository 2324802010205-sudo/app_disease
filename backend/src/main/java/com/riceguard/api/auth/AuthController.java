package com.riceguard.api.auth;

import com.riceguard.api.auth.AuthDtos.*;
import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Xác thực", description = "Đăng ký, đăng nhập bằng email/mật khẩu qua Firebase Authentication")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) {
        return service.register(r);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) {
        return service.login(r);
    }

    /**
     * Đăng nhập / đăng ký bằng Google. mode=LOGIN mà tài khoản chưa có thì trả 404 GOOGLE_NOT_REGISTERED
     * để app chuyển sang màn đăng ký; mode=REGISTER tạo tài khoản (hoặc đăng nhập nếu đã có).
     */
    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleRequest r) {
        return service.google(r.idToken(), "REGISTER".equals(r.mode()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return service.refresh(r.refreshToken());
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgot(@Valid @RequestBody ForgotPasswordRequest r) {
        service.forgotPassword(r.email());
        return Map.of("message", "Nếu email tồn tại, chúng tôi đã gửi hướng dẫn đặt lại mật khẩu");
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ChangePasswordRequest r) {
        service.changePassword(u, r);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthUser u) {
        service.logoutAll(u);
    }
}

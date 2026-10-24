package com.riceguard.api.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.riceguard.api.auth.AuthDtos.*;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserProfile;
import com.riceguard.api.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final FirebaseAuth auth;
    private final IdentityToolkitClient identity;
    private final UserService users;
    private final Set<String> adminEmails;

    public AuthService(FirebaseAuth auth, IdentityToolkitClient identity, UserService users, AppProperties props) {
        this.auth = auth;
        this.identity = identity;
        this.users = users;
        this.adminEmails = Arrays.stream(props.security().adminEmails().split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        UserRecord created;
        try {
            created = auth.createUser(new UserRecord.CreateRequest()
                    .setEmail(email).setPassword(r.password()).setDisplayName(r.fullName().trim()));
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) throw IdentityToolkitClient.mapCode("EMAIL_EXISTS");
            log.error("Tạo tài khoản Firebase thất bại", e);
            throw ApiException.badRequest("Không tạo được tài khoản: " + e.getMessage());
        }
        try {
            users.create(created.getUid(), email, r.fullName().trim(), blank(r.phone()), blank(r.province()), blank(r.role()));
            grantAdminIfListed(created.getUid(), email);
        } catch (RuntimeException e) {
            // Không để lại tài khoản Auth "mồ côi" nếu tạo hồ sơ thất bại.
            try { auth.deleteUser(created.getUid()); } catch (FirebaseAuthException ignored) { /* best-effort */ }
            throw e;
        }
        return login(new LoginRequest(email, r.password()));
    }

    public AuthResponse login(LoginRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        IdentityToolkitClient.SignIn s = identity.signInWithPassword(email, r.password());
        UserProfile p = users.getOrCreate(new AuthUser(s.uid(), email, null, false));
        boolean admin = isAdmin(s.uid());
        return new AuthResponse(s.idToken(), s.refreshToken(), s.expiresIn(), UserService.toDto(p, admin, users.stats(s.uid())));
    }

    /**
     * Đăng nhập/đăng ký bằng Google. Tài khoản chưa có mà createIfMissing=false (đang ở màn đăng nhập)
     * thì trả GOOGLE_NOT_REGISTERED để app chuyển sang màn đăng ký.
     */
    public AuthResponse google(String googleIdToken, boolean createIfMissing) {
        String tokenEmail = GoogleTokens.emailOf(googleIdToken);
        if (tokenEmail == null) throw ApiException.badRequest("Mã xác thực Google không hợp lệ");
        if (!createIfMissing && !existsByEmail(tokenEmail)) throw notRegistered();

        IdentityToolkitClient.IdpSignIn r = identity.signInWithGoogle(googleIdToken);
        String uid = r.signIn().uid();
        if (r.newUser() && !createIfMissing) {
            // Email đọc từ token không khớp token thật: Firebase vừa tạo tài khoản ngoài ý muốn, hoàn tác.
            try { auth.deleteUser(uid); } catch (FirebaseAuthException e) { log.warn("Không hoàn tác được tài khoản Google mới {}", uid, e); }
            throw notRegistered();
        }
        String email = r.email() != null ? r.email().toLowerCase(Locale.ROOT) : tokenEmail;
        boolean hadProfile = users.exists(uid);
        UserProfile p = users.getOrCreate(new AuthUser(uid, email, r.displayName(), false));
        if (!hadProfile) {
            grantAdminIfListed(uid, email);
            if (r.photoUrl() != null && !r.photoUrl().isBlank()) p = users.setExternalAvatar(uid, r.photoUrl());
        }
        IdentityToolkitClient.SignIn s = r.signIn();
        return new AuthResponse(s.idToken(), s.refreshToken(), s.expiresIn(), UserService.toDto(p, isAdmin(uid), users.stats(uid)));
    }

    private boolean existsByEmail(String email) {
        try {
            auth.getUserByEmail(email);
            return true;
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) return false;
            log.error("Không kiểm tra được tài khoản theo email", e);
            throw ApiException.unavailable("Dịch vụ đăng nhập tạm thời không khả dụng, vui lòng thử lại");
        }
    }

    private static ApiException notRegistered() {
        return new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "GOOGLE_NOT_REGISTERED",
                "Tài khoản Google này chưa đăng ký. Hãy tạo tài khoản để tiếp tục");
    }

    public TokenResponse refresh(String refreshToken) {
        IdentityToolkitClient.SignIn s = identity.refresh(refreshToken);
        return new TokenResponse(s.idToken(), s.refreshToken(), s.expiresIn());
    }

    /** Luôn trả thành công để không lộ email nào đã đăng ký. */
    public void forgotPassword(String email) {
        try {
            identity.sendPasswordReset(email.trim().toLowerCase(Locale.ROOT));
        } catch (ApiException e) {
            if (e.status().is5xxServerError()) throw e;
            log.debug("Bỏ qua lỗi gửi email đặt lại mật khẩu: {}", e.getMessage());
        }
    }

    public void changePassword(AuthUser u, ChangePasswordRequest r) {
        String email = u.email();
        if (email == null) throw ApiException.badRequest("Tài khoản này không dùng mật khẩu");
        identity.signInWithPassword(email, r.currentPassword());
        try {
            auth.updateUser(new UserRecord.UpdateRequest(u.uid()).setPassword(r.newPassword()));
            auth.revokeRefreshTokens(u.uid());
        } catch (FirebaseAuthException e) {
            throw ApiException.badRequest("Không đổi được mật khẩu: " + e.getMessage());
        }
    }

    /** Đăng xuất mọi thiết bị: thu hồi refresh token. */
    public void logoutAll(AuthUser u) {
        try {
            auth.revokeRefreshTokens(u.uid());
        } catch (FirebaseAuthException e) {
            throw ApiException.unavailable("Không đăng xuất được, vui lòng thử lại");
        }
    }

    private boolean isAdmin(String uid) {
        try {
            return Boolean.TRUE.equals(auth.getUser(uid).getCustomClaims().get("admin"));
        } catch (FirebaseAuthException e) {
            return false;
        }
    }

    private void grantAdminIfListed(String uid, String email) {
        if (!adminEmails.contains(email)) return;
        try {
            auth.setCustomUserClaims(uid, Map.of("admin", true));
        } catch (FirebaseAuthException e) {
            log.warn("Không gán quyền admin cho {}", email, e);
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

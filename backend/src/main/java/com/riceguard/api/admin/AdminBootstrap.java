package com.riceguard.api.admin;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.UserRecord;
import com.riceguard.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Gán quyền admin (custom claim) cho các email trong ADMIN_EMAILS nếu tài khoản đã tồn tại. */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final FirebaseAuth auth;
    private final AppProperties props;

    public AdminBootstrap(FirebaseAuth auth, AppProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        provisionBootstrapAdmin();
        for (String email : props.security().adminEmails().split(",")) {
            String e = email.trim();
            if (e.isEmpty()) continue;
            try {
                UserRecord u = auth.getUserByEmail(e);
                if (Boolean.TRUE.equals(u.getCustomClaims().get("admin"))) continue;
                Map<String, Object> claims = new HashMap<>(u.getCustomClaims());
                claims.put("admin", true);
                auth.setCustomUserClaims(u.getUid(), claims);
                log.info("Đã gán quyền admin cho {}", e);
            } catch (FirebaseAuthException ex) {
                log.info("Chưa gán admin cho {} (tài khoản chưa tồn tại hoặc lỗi: {})", e, ex.getMessage());
            }
        }
    }

    private void provisionBootstrapAdmin() {
        String email = props.security().adminBootstrapEmail().trim().toLowerCase(java.util.Locale.ROOT);
        String password = props.security().adminBootstrapPassword();
        if (email.isEmpty() && password.isEmpty()) return;
        if (email.isEmpty() || password.length() < 12 || !email.contains("@")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL và ADMIN_BOOTSTRAP_PASSWORD (ít nhất 12 ký tự) phải hợp lệ");
        }
        try {
            UserRecord user;
            try {
                user = auth.getUserByEmail(email);
            } catch (FirebaseAuthException ex) {
                if (ex.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) throw ex;
                user = auth.createUser(new UserRecord.CreateRequest()
                        .setEmail(email).setPassword(password).setDisplayName("RiceGuard Admin"));
                log.info("Đã tạo tài khoản admin dashboard cho {}", email);
            }
            if (!Boolean.TRUE.equals(user.getCustomClaims().get("admin"))) {
                Map<String, Object> claims = new HashMap<>(user.getCustomClaims());
                claims.put("admin", true);
                auth.setCustomUserClaims(user.getUid(), claims);
                log.info("Đã gán quyền admin cho {}", email);
            }
        } catch (FirebaseAuthException ex) {
            throw new IllegalStateException("Không thể tạo hoặc cấp quyền admin dashboard cho " + email, ex);
        }
    }
}

package com.riceguard.api.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Xác thực Bearer token do Firebase Authentication cấp. Token sai/hết hạn -> bỏ qua, entry point trả 401. */
public class FirebaseAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthFilter.class);
    public static final String ERROR_ATTR = "riceguard.auth.error";

    private final FirebaseAuth auth;
    private final boolean checkRevoked;

    public FirebaseAuthFilter(FirebaseAuth auth, boolean checkRevoked) {
        this.auth = auth;
        this.checkRevoked = checkRevoked;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            try {
                FirebaseToken decoded = auth.verifyIdToken(token, checkRevoked);
                boolean admin = Boolean.TRUE.equals(decoded.getClaims().get("admin"));
                AuthUser user = new AuthUser(decoded.getUid(), decoded.getEmail(), decoded.getName(), admin);
                List<SimpleGrantedAuthority> roles = new ArrayList<>();
                roles.add(new SimpleGrantedAuthority("ROLE_USER"));
                if (admin) roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, roles));
                request.setAttribute(com.riceguard.api.common.RequestLogFilter.UID_ATTR, user.uid());
            } catch (FirebaseAuthException | IllegalArgumentException e) {
                log.debug("Token không hợp lệ: {}", e.getMessage());
                request.setAttribute(ERROR_ATTR, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ, vui lòng đăng nhập lại");
            }
        }
        chain.doFilter(request, response);
    }
}

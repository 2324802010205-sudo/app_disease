package com.riceguard.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.riceguard.api.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Giới hạn tần suất theo cửa sổ 1 phút trong bộ nhớ: theo IP cho /auth, theo người dùng cho phần còn lại.
 * Chạy nhiều instance thì mỗi instance đếm riêng; muốn chặt hơn hãy đặt thêm rate limit ở API gateway.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).maximumSize(200_000).build();
    private final ObjectMapper mapper;
    private final int perMinute;
    private final int authPerMinute;

    public RateLimitFilter(ObjectMapper mapper, int perMinute, int authPerMinute) {
        this.mapper = mapper;
        this.perMinute = perMinute;
        this.authPerMinute = authPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !p.startsWith("/api/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean authPath = request.getRequestURI().startsWith("/api/v1/auth/");
        String key;
        int limit;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (!authPath && a != null && a.getPrincipal() instanceof AuthUser u) {
            key = "u:" + u.uid();
            limit = perMinute;
        } else {
            key = (authPath ? "a:" : "i:") + request.getRemoteAddr();
            limit = authPath ? authPerMinute : perMinute;
        }
        int count = counters.get(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), ErrorResponse.of("RATE_LIMITED",
                    "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút", 429, request.getRequestURI()));
            return;
        }
        chain.doFilter(request, response);
    }
}

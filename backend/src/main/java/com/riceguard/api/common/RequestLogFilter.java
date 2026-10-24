package com.riceguard.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gắn mã yêu cầu (X-Request-Id) vào mọi dòng log và phản hồi, rồi ghi một dòng tổng kết cho mỗi yêu cầu API:
 * {@code GET /api/v1/fields 200 35ms uid=abc req=3f2a9c1d}.
 * App Android gửi sẵn X-Request-Id nên có thể lần theo một thao tác từ logcat (tag RiceApi) sang log máy chủ.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLogFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    /** Thuộc tính request do FirebaseAuthFilter gán khi xác thực được người dùng. */
    public static final String UID_ATTR = "riceguard.uid";

    private static final Logger log = LoggerFactory.getLogger("riceguard.request");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !p.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE_ID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", id);
        response.setHeader(HEADER, id);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            Object uid = request.getAttribute(UID_ATTR);
            String line = request.getMethod() + " " + request.getRequestURI() + " " + status + " " + ms + "ms uid=" + (uid == null ? "-" : uid);
            if (status >= 500) log.error(line);
            else if (status >= 400) log.warn(line);
            else log.info(line);
            MDC.remove("requestId");
        }
    }
}

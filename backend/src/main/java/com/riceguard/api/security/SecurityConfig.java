package com.riceguard.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.riceguard.api.common.ErrorResponse;
import com.riceguard.api.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, FirebaseAuth firebaseAuth, AppProperties props, ObjectMapper mapper) throws Exception {
        FirebaseAuthFilter authFilter = new FirebaseAuthFilter(firebaseAuth, props.firebase().checkRevoked());
        RateLimitFilter rateLimit = new RateLimitFilter(mapper, props.security().rateLimitPerMinute(), props.security().authRateLimitPerMinute());

        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/refresh", "/api/v1/auth/forgot-password", "/api/v1/auth/google").permitAll()
                        // Khách (chưa đăng nhập) được xem: thư viện bệnh, thời tiết, bản đồ nguy cơ, bảng tin cộng đồng.
                        .requestMatchers(HttpMethod.GET, "/api/v1/diseases/**", "/api/v1/weather/**", "/api/v1/risk/**",
                                "/api/v1/meta/**", "/files/**", "/api/v1/posts", "/api/v1/posts/*",
                                "/api/v1/posts/*/comments").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> write(mapper, req, res, 401, "UNAUTHENTICATED", message(req)))
                        .accessDeniedHandler((req, res, ex) -> write(mapper, req, res, 403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này")))
                .addFilterBefore(authFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(rateLimit, FirebaseAuthFilter.class);
        return http.build();
    }

    private static String message(HttpServletRequest req) {
        Object attr = req.getAttribute(FirebaseAuthFilter.ERROR_ATTR);
        return attr != null ? attr.toString() : "Vui lòng đăng nhập để tiếp tục";
    }

    private static void write(ObjectMapper mapper, HttpServletRequest req, HttpServletResponse res, int status, String code, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ErrorResponse.of(code, msg, status, req.getRequestURI()));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = Arrays.stream(props.security().corsAllowedOrigins().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        c.setAllowedOriginPatterns(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
}

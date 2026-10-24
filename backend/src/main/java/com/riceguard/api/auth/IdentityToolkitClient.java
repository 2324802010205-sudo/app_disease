package com.riceguard.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Gọi REST API của Firebase Authentication (Identity Toolkit) để đăng nhập bằng mật khẩu,
 * làm mới token và gửi email đặt lại mật khẩu. Cần FIREBASE_WEB_API_KEY.
 */
@Component
public class IdentityToolkitClient {
    private static final Logger log = LoggerFactory.getLogger(IdentityToolkitClient.class);

    public record SignIn(String uid, String idToken, String refreshToken, long expiresIn) {}

    private final RestClient http;
    private final ObjectMapper mapper;
    private final AppProperties.Firebase cfg;

    public IdentityToolkitClient(AppProperties props, ObjectMapper mapper) {
        this.cfg = props.firebase();
        this.mapper = mapper;
        this.http = RestClient.builder().build();
    }

    public SignIn signInWithPassword(String email, String password) {
        JsonNode r = post(cfg.identityToolkitUrl() + "/accounts:signInWithPassword?key=" + key(),
                Map.of("email", email, "password", password, "returnSecureToken", true));
        return new SignIn(r.path("localId").asText(), r.path("idToken").asText(), r.path("refreshToken").asText(),
                r.path("expiresIn").asLong(3600));
    }

    /** Kết quả đăng nhập bằng nhà cung cấp ngoài (Google); newUser = Firebase vừa tạo tài khoản mới. */
    public record IdpSignIn(SignIn signIn, boolean newUser, String email, String displayName, String photoUrl) {}

    /** Đổi Google ID token lấy phiên Firebase. Firebase tự kiểm tra chữ ký token với Google. */
    public IdpSignIn signInWithGoogle(String googleIdToken) {
        JsonNode r = post(cfg.identityToolkitUrl() + "/accounts:signInWithIdp?key=" + key(),
                Map.of("postBody", "id_token=" + java.net.URLEncoder.encode(googleIdToken, java.nio.charset.StandardCharsets.UTF_8) + "&providerId=google.com",
                        "requestUri", "http://localhost", "returnIdpCredential", true, "returnSecureToken", true));
        if (r.path("needConfirmation").asBoolean(false)) {
            throw new ApiException(HttpStatus.CONFLICT, "NEEDS_PASSWORD_LOGIN", "Email này đã đăng ký bằng mật khẩu. Hãy đăng nhập bằng email và mật khẩu");
        }
        SignIn s = new SignIn(r.path("localId").asText(), r.path("idToken").asText(), r.path("refreshToken").asText(), r.path("expiresIn").asLong(3600));
        return new IdpSignIn(s, r.path("isNewUser").asBoolean(false), r.path("email").asText(null),
                r.path("displayName").asText(null), r.path("photoUrl").asText(null));
    }

    public SignIn refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        try {
            JsonNode r = http.post().uri(cfg.secureTokenUrl() + "/token?key=" + key())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
            return new SignIn(r.path("user_id").asText(), r.path("id_token").asText(), r.path("refresh_token").asText(),
                    r.path("expires_in").asLong(3600));
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    public void sendPasswordReset(String email) {
        post(cfg.identityToolkitUrl() + "/accounts:sendOobCode?key=" + key(), Map.of("requestType", "PASSWORD_RESET", "email", email));
    }

    private JsonNode post(String url, Object body) {
        try {
            return http.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private String key() {
        if (cfg.webApiKey() == null || cfg.webApiKey().isBlank()) {
            throw ApiException.unavailable("Máy chủ chưa cấu hình FIREBASE_WEB_API_KEY");
        }
        return cfg.webApiKey();
    }

    private ApiException unavailable(RestClientException e) {
        log.error("Không gọi được Firebase Authentication", e);
        return ApiException.unavailable("Dịch vụ đăng nhập tạm thời không khả dụng, vui lòng thử lại");
    }

    /** Chuyển mã lỗi của Firebase thành thông điệp tiếng Việt. */
    ApiException map(RestClientResponseException e) {
        String code = "UNKNOWN";
        try {
            code = mapper.readTree(e.getResponseBodyAsString()).path("error").path("message").asText("UNKNOWN");
        } catch (Exception ignored) {
            // thân phản hồi không phải JSON
        }
        return mapCode(code);
    }

    static ApiException mapCode(String raw) {
        String code = raw.contains(" ") ? raw.substring(0, raw.indexOf(' ')) : raw;
        return switch (code) {
            case "EMAIL_EXISTS" -> new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email này đã được đăng ký");
            case "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND" ->
                    new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
            case "USER_DISABLED" -> new ApiException(HttpStatus.FORBIDDEN, "USER_DISABLED", "Tài khoản đã bị khóa");
            case "TOO_MANY_ATTEMPTS_TRY_LATER" -> ApiException.tooMany("Đăng nhập sai quá nhiều lần, vui lòng thử lại sau");
            case "WEAK_PASSWORD" -> ApiException.badRequest("Mật khẩu quá yếu");
            case "INVALID_REFRESH_TOKEN", "TOKEN_EXPIRED", "USER_NOT_FOUND", "INVALID_GRANT_TYPE" ->
                    ApiException.unauthorized("Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
            case "INVALID_EMAIL" -> ApiException.badRequest("Email không hợp lệ");
            default -> ApiException.badRequest("Không thực hiện được yêu cầu (" + code + ")");
        };
    }
}

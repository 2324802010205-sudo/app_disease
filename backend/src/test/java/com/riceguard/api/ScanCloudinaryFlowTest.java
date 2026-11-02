package com.riceguard.api;

import com.cloudinary.Cloudinary;
import com.riceguard.api.diagnosis.DiagnosisEngine;
import com.riceguard.api.diagnosis.DiagnosisException;
import com.riceguard.api.diagnosis.DiagnosisRequest;
import com.riceguard.api.diagnosis.DiagnosisResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quét ảnh qua HTTP thật rồi đọc lại Cloudinary thật: ảnh phải nằm đúng thư mục theo dự đoán, kể cả không xác định.
 * Cần FIRESTORE_EMULATOR_HOST và CLOUDINARY_URL. Ảnh nằm dưới riceguard/test và được xóa khi kết thúc.
 */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_URL", matches = "cloudinary://.+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScanCloudinaryFlowTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    private static volatile boolean aiDown;

    @TestConfiguration
    static class FakeAi {
        @Bean @Primary
        DiagnosisEngine fake() {
            return new DiagnosisEngine() {
                public String name() { return "fake"; }
                public DiagnosisResult diagnose(DiagnosisRequest r) {
                    if (aiDown) throw new DiagnosisException("AI tắt");
                    return switch (String.valueOf(r.stage())) {
                        case "khoe" -> new DiagnosisResult(true, "healthy", 0.9, "NONE", List.of(), "", List.of(), "");
                        case "mo" -> new DiagnosisResult(false, "unknown", 0, "NONE", List.of(), "", List.of(), "Chụp lại");
                        default -> new DiagnosisResult(true, "dom-nau-hep", 0.8, "LOW", List.of(), "", List.of(), "");
                    };
                }
            };
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String auth = System.getenv().getOrDefault("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099");
        r.add("riceguard.firebase.emulator", () -> "true");
        r.add("riceguard.firebase.project-id", () -> "demo-riceguard");
        r.add("riceguard.firebase.web-api-key", () -> "fake");
        r.add("riceguard.firebase.identity-toolkit-url", () -> "http://" + auth + "/identitytoolkit.googleapis.com/v1");
        r.add("riceguard.firebase.secure-token-url", () -> "http://" + auth + "/securetoken.googleapis.com/v1");
        r.add("riceguard.media.provider", () -> "cloudinary");
        r.add("riceguard.media.cloudinary-url", () -> System.getenv("CLOUDINARY_URL"));
        r.add("riceguard.media.folder-prefix", () -> "riceguard/test");
        r.add("riceguard.reminders.enabled", () -> "false");
    }

    @Autowired TestRestTemplate http;

    private Map<String, Object> json(HttpMethod m, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.setBearerAuth(token);
        if (body != null) h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, m, new HttpEntity<>(body, h), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    private Map<String, Object> scan(String token, String stage) {
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("image", new ByteArrayResource(PNG) { @Override public String getFilename() { return "leaf.png"; } });
        form.add("stage", stage);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/scans", HttpMethod.POST, new HttpEntity<>(form, h), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();
    }

    @SuppressWarnings("rawtypes")
    private static String folderOf(Map<String, Object> scan) throws Exception {
        Map info = new Cloudinary(System.getenv("CLOUDINARY_URL")).api().resource((String) scan.get("imagePublicId"), Map.of());
        return (String) info.get("asset_folder");
    }

    @Test
    void everyScanLandsInTheFolderOfItsPrediction() throws Exception {
        String email = "cloud" + System.nanoTime() + "@test.vn";
        String token = (String) json(HttpMethod.POST, "/api/v1/auth/register", null, Map.of("fullName", "Thu Kho", "email", email, "password", "matkhau123")).get("idToken");
        var disease = scan(token, "benh");
        var healthy = scan(token, "khoe");
        var unknown = scan(token, "mo");
        aiDown = true;
        var pending = scan(token, "cho");
        try {
            assertEquals("riceguard/test/dataset/dom-nau-hep", folderOf(disease));
            assertEquals("riceguard/test/dataset/la-khoe", folderOf(healthy));
            assertEquals("riceguard/test/dataset/khong-xac-dinh", folderOf(unknown));
            assertEquals("riceguard/test/dataset/cho-phan-tich", folderOf(pending));

            aiDown = false;
            var again = json(HttpMethod.POST, "/api/v1/scans/" + pending.get("id") + "/diagnose", token, null);
            assertEquals("DIAGNOSED", again.get("status"));
            assertEquals("riceguard/test/dataset/dom-nau-hep", folderOf(again));
            assertEquals(HttpStatus.OK, http.getForEntity(java.net.URI.create((String) again.get("imageUrl")), byte[].class).getStatusCode());
        } finally {
            for (var s : List.of(disease, healthy, unknown, pending)) json(HttpMethod.DELETE, "/api/v1/scans/" + s.get("id"), token, null);
        }
    }
}

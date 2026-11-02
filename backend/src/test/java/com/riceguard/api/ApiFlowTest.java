package com.riceguard.api;

import com.riceguard.api.diagnosis.DiagnosisEngine;
import com.riceguard.api.diagnosis.DiagnosisRequest;
import com.riceguard.api.diagnosis.DiagnosisResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm thử luồng chính qua HTTP thật với Firestore + Firebase Auth emulator.
 * Chỉ chạy khi có FIRESTORE_EMULATOR_HOST, ví dụ:
 *   firebase emulators:exec --only auth,firestore --project demo-riceguard "./gradlew test"
 * Cloudinary được thay bằng kho ảnh cục bộ; AI được thay bằng engine giả.
 */
@EnabledIfEnvironmentVariable(named = "FIRESTORE_EMULATOR_HOST", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiFlowTest {

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        DiagnosisEngine fakeEngine() {
            return new DiagnosisEngine() {
                public String name() { return "fake"; }
                public DiagnosisResult diagnose(DiagnosisRequest r) {
                    if ("Ảnh mờ".equals(r.stage())) return new DiagnosisResult(true, "bac-la", 0.3, "MEDIUM", List.of("Vết cháy mép lá"), "Nghi bạc lá", List.of(), "");
                    if ("Gieo mạ".equals(r.stage())) return new DiagnosisResult(true, "healthy", 0.95, "NONE", List.of(), "Cây khỏe", List.of(), "");
                    return new DiagnosisResult(true, "dao-on-la", 0.87, "MEDIUM", List.of("Vết hình thoi"), "Đạo ôn lá",
                            List.of(new DiagnosisResult.Recommendation("Phun thuốc", "Tricyclazole", "SHIELD")), "");
                }
            };
        }
    }

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0};

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        String auth = System.getenv().getOrDefault("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:9099");
        r.add("riceguard.firebase.emulator", () -> "true");
        r.add("riceguard.firebase.project-id", () -> "demo-riceguard");
        r.add("riceguard.firebase.web-api-key", () -> "fake");
        r.add("riceguard.firebase.identity-toolkit-url", () -> "http://" + auth + "/identitytoolkit.googleapis.com/v1");
        r.add("riceguard.firebase.secure-token-url", () -> "http://" + auth + "/securetoken.googleapis.com/v1");
        r.add("riceguard.media.provider", () -> "local");
        r.add("riceguard.media.local-dir", () -> tmp.toString());
        r.add("riceguard.reminders.enabled", () -> "false");
    }

    private static Path tmp;

    @BeforeAll
    static void createTmp() throws Exception {
        tmp = Files.createTempDirectory("riceguard-test");
    }

    @Autowired TestRestTemplate http;
    @Autowired com.google.firebase.auth.FirebaseAuth firebaseAuth;

    private static final String SUFFIX = Long.toString(System.nanoTime());
    /** Mỗi lần chạy dùng một huyện riêng để không lẫn với dữ liệu emulator của lần chạy trước. */
    private static final String DISTRICT = "Huyện Thử " + SUFFIX;
    private static String tokenA, tokenB, fieldId, scanId, postId;

    private HttpHeaders auth(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<Map<String, Object>> call(HttpMethod m, String path, String token, Object body) {
        HttpHeaders h = token == null ? new HttpHeaders() : auth(token);
        if (body != null) h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, m, new HttpEntity<>(body, h), new ParameterizedTypeReference<>() {});
    }

    private ResponseEntity<List<Map<String, Object>>> callList(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(token)), new ParameterizedTypeReference<>() {});
    }

    private static List<Map<String, Object>> ours(List<Map<String, Object>> areas) {
        return areas.stream().filter(a -> DISTRICT.equals(a.get("district"))).toList();
    }

    private ResponseEntity<Map<String, Object>> scan(String token, Map<String, String> fields) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("image", new ByteArrayResource(JPEG) {
            @Override public String getFilename() { return "leaf.jpg"; }
        });
        fields.forEach(form::add);
        HttpHeaders h = auth(token);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange("/api/v1/scans", HttpMethod.POST, new HttpEntity<>(form, h), new ParameterizedTypeReference<>() {});
    }

    private String register(String name) {
        var res = call(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("fullName", name, "email", name.toLowerCase().replace(' ', '.') + SUFFIX + "@test.vn", "password", "matkhau123"));
        assertEquals(HttpStatus.CREATED, res.getStatusCode(), String.valueOf(res.getBody()));
        return (String) res.getBody().get("idToken");
    }

    @Test @Order(1)
    void registerLoginAndProfile() {
        tokenA = register("Nong Dan A");
        tokenB = register("Nong Dan B");
        var me = call(HttpMethod.GET, "/api/v1/users/me", tokenA, null);
        assertEquals(HttpStatus.OK, me.getStatusCode());
        assertEquals("Nong Dan A", me.getBody().get("fullName"));
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/v1/users/me", null, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.GET, "/api/v1/users/me", "garbage", null).getStatusCode());
    }

    @Test @Order(2)
    void duplicateEmailAndWrongPasswordAreRejected() {
        String email = "nong.dan.a" + SUFFIX + "@test.vn";
        assertEquals(HttpStatus.CONFLICT, call(HttpMethod.POST, "/api/v1/auth/register", null,
                Map.of("fullName", "X", "email", email, "password", "matkhau123")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", email, "password", "sai-mat-khau")).getStatusCode());
        assertEquals(HttpStatus.OK, call(HttpMethod.POST, "/api/v1/auth/login", null,
                Map.of("email", email, "password", "matkhau123")).getStatusCode());
    }

    @Test @Order(3)
    void validationErrorsListTheFields() {
        var res = call(HttpMethod.PUT, "/api/v1/users/me", tokenA, Map.of("fullName", ""));
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("VALIDATION_FAILED", res.getBody().get("code"));
    }

    @Test @Order(4)
    void scanDiagnosesAndUpdatesFieldNotificationsAndRiskMap() {
        var f = call(HttpMethod.POST, "/api/v1/fields", tokenA,
                Map.of("name", "Ruộng Bờ Đông", "province", "Thái Bình", "district", DISTRICT, "areaHa", 0.8, "stage", "Làm đòng"));
        assertEquals(HttpStatus.CREATED, f.getStatusCode(), String.valueOf(f.getBody()));
        fieldId = (String) f.getBody().get("id");

        var s = scan(tokenA, Map.of("fieldId", fieldId));
        assertEquals(HttpStatus.CREATED, s.getStatusCode(), String.valueOf(s.getBody()));
        assertEquals("DIAGNOSED", s.getBody().get("status"));
        assertEquals("HIGH", s.getBody().get("riskLevel"));
        assertEquals("dao-on-la", s.getBody().get("diseaseCode"));
        scanId = (String) s.getBody().get("id");

        var detail = call(HttpMethod.GET, "/api/v1/fields/" + fieldId, tokenA, null).getBody();
        @SuppressWarnings("unchecked") var field = (Map<String, Object>) detail.get("field");
        assertEquals("ALERT", field.get("status"));
        assertEquals(5, field.get("daysUntilCheck"));
        assertEquals(1, ((List<?>) detail.get("recentScans")).size());
        assertEquals(1, ((List<?>) detail.get("recentDiaries")).size());

        var notifs = call(HttpMethod.GET, "/api/v1/notifications?filter=alert", tokenA, null).getBody();
        assertEquals(1, ((List<?>) notifs.get("items")).size());

        var areas = ours(callList("/api/v1/risk/areas?days=7&province=Thái Bình", tokenA).getBody());
        assertEquals(1, areas.size());
        assertEquals(1, ((Number) areas.get(0).get("cases")).intValue());
    }

    @Test @Order(5)
    void healthyScanAndPagination() {
        var s = scan(tokenA, Map.of("stage", "Gieo mạ"));
        assertEquals("NORMAL", s.getBody().get("riskLevel"));
        var page1 = call(HttpMethod.GET, "/api/v1/scans?limit=1", tokenA, null).getBody();
        assertEquals(1, ((List<?>) page1.get("items")).size());
        String cursor = (String) page1.get("nextCursor");
        assertNotNull(cursor);
        var page2 = call(HttpMethod.GET, "/api/v1/scans?limit=1&cursor=" + cursor, tokenA, null).getBody();
        assertEquals(1, ((List<?>) page2.get("items")).size());
        assertEquals(null, page2.get("nextCursor"));
        var high = call(HttpMethod.GET, "/api/v1/scans?risk=HIGH", tokenA, null).getBody();
        assertEquals(1, ((List<?>) high.get("items")).size());
    }

    @Test @Order(6)
    void usersCannotSeeEachOthersData() {
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/api/v1/scans/" + scanId, tokenB, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/api/v1/fields/" + fieldId, tokenB, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.DELETE, "/api/v1/scans/" + scanId, tokenB, null).getStatusCode());
        // ảnh của người khác không được gắn vào nhật ký của mình
        var res = call(HttpMethod.POST, "/api/v1/diary", tokenB, Map.of("title", "x", "images", List.of(
                Map.of("url", "http://localhost/files/riceguard/dev/users/someone-else/diary/x", "publicId", "riceguard/dev/users/someone-else/diary/x"))));
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test @Order(7)
    void communityPostLikeCommentFlow() {
        var p = call(HttpMethod.POST, "/api/v1/posts", tokenA, Map.of("topic", "PEST", "title", "Phòng đạo ôn", "content", "Chia sẻ kinh nghiệm"));
        assertEquals(HttpStatus.CREATED, p.getStatusCode(), String.valueOf(p.getBody()));
        postId = (String) p.getBody().get("id");

        assertEquals(1, ((Number) call(HttpMethod.PUT, "/api/v1/posts/" + postId + "/like", tokenB, null).getBody().get("likeCount")).intValue());
        assertEquals(1, ((Number) call(HttpMethod.PUT, "/api/v1/posts/" + postId + "/like", tokenB, null).getBody().get("likeCount")).intValue(), "like lần hai phải idempotent");
        assertEquals(0, ((Number) call(HttpMethod.DELETE, "/api/v1/posts/" + postId + "/like", tokenB, null).getBody().get("likeCount")).intValue());

        var c = call(HttpMethod.POST, "/api/v1/posts/" + postId + "/comments", tokenB, Map.of("text", "Cảm ơn bạn"));
        assertEquals(HttpStatus.CREATED, c.getStatusCode());
        String commentId = (String) c.getBody().get("id");
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/v1/posts/" + postId + "/comments", tokenA,
                Map.of("text", "Không có chi", "parentId", commentId)).getStatusCode());

        var comments = callList("/api/v1/posts/" + postId + "/comments", tokenA).getBody();
        assertEquals(1, comments.size());
        assertEquals(1, ((List<?>) comments.get(0).get("replies")).size());
        assertEquals(2, ((Number) call(HttpMethod.GET, "/api/v1/posts/" + postId, tokenA, null).getBody().get("commentCount")).intValue());

        var notifs = call(HttpMethod.GET, "/api/v1/notifications?filter=community", tokenA, null).getBody();
        assertFalse(((List<?>) notifs.get("items")).isEmpty(), "tác giả phải nhận thông báo bình luận");

        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.DELETE, "/api/v1/posts/" + postId, tokenB, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.PUT, "/api/v1/posts/khong-ton-tai/like", tokenB, null).getStatusCode());
        assertTrue(call(HttpMethod.GET, "/api/v1/posts?topic=PEST", tokenA, null).getStatusCode().is2xxSuccessful());
    }

    @Test @Order(8)
    void deletingScanRemovesItFromAreaStats() {
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/api/v1/scans/" + scanId, tokenA, null).getStatusCode());
        assertTrue(ours(callList("/api/v1/risk/areas?days=7&province=Thái Bình", tokenA).getBody()).isEmpty());
    }

    @Test @Order(9)
    void adminEndpointsAreForbiddenForNormalUsers() {
        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, "/api/v1/admin/reports", tokenA, null).getStatusCode());
    }

    @Test @Order(10)
    void publicEndpointsNeedNoLogin() {
        assertEquals(HttpStatus.OK, http.getForEntity("/api/v1/diseases", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/api/v1/meta/options", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/actuator/health", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, http.getForEntity("/api/v1/fields", String.class).getStatusCode());
    }

    @Test @Order(11)
    void guestCanReadCommunityAndRiskButNotWrite() {
        assertEquals(HttpStatus.OK, http.getForEntity("/api/v1/posts", String.class).getStatusCode());
        assertEquals(HttpStatus.OK, http.getForEntity("/api/v1/risk/areas?days=7", String.class).getStatusCode());
        var post = call(HttpMethod.POST, "/api/v1/posts", tokenA,
                Map.of("topic", "PEST", "title", "Bài cho khách xem", "content", "Nội dung công khai"));
        String id = (String) post.getBody().get("id");
        var view = call(HttpMethod.GET, "/api/v1/posts/" + id, null, null);
        assertEquals(HttpStatus.OK, view.getStatusCode());
        assertEquals(false, view.getBody().get("liked"));
        assertEquals(false, view.getBody().get("mine"));
        assertEquals(HttpStatus.OK, http.getForEntity("/api/v1/posts/" + id + "/comments", String.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/v1/posts/" + id + "/comments", null, Map.of("text", "x")).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.PUT, "/api/v1/posts/" + id + "/like", null, null).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, call(HttpMethod.POST, "/api/v1/posts", null,
                Map.of("topic", "PEST", "title", "x", "content", "y")).getStatusCode());
    }

    @Test @Order(12)
    void googleLoginRequiresRegistrationFirst() {
        String email = "google.user" + SUFFIX + "@gmail.com";
        String idToken = "{\"sub\":\"g-" + SUFFIX + "\",\"email\":\"" + email + "\",\"email_verified\":true,\"name\":\"Nguyen Van Google\"}";

        var login = call(HttpMethod.POST, "/api/v1/auth/google", null, Map.of("idToken", idToken, "mode", "LOGIN"));
        assertEquals(HttpStatus.NOT_FOUND, login.getStatusCode());
        assertEquals("GOOGLE_NOT_REGISTERED", login.getBody().get("code"));

        var reg = call(HttpMethod.POST, "/api/v1/auth/google", null, Map.of("idToken", idToken, "mode", "REGISTER"));
        assertEquals(HttpStatus.OK, reg.getStatusCode());
        assertNotNull(reg.getBody().get("idToken"));
        @SuppressWarnings("unchecked") Map<String, Object> user = (Map<String, Object>) reg.getBody().get("user");
        assertEquals(email, user.get("email"));

        var again = call(HttpMethod.POST, "/api/v1/auth/google", null, Map.of("idToken", idToken, "mode", "LOGIN"));
        assertEquals(HttpStatus.OK, again.getStatusCode());
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/v1/users/me", (String) again.getBody().get("idToken"), null).getStatusCode());

        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.POST, "/api/v1/auth/google", null, Map.of("idToken", "rac", "mode", "LOGIN")).getStatusCode());
    }

    private long folderTotal(Map<String, Object> summary, String label) {
        @SuppressWarnings("unchecked") var folders = (List<Map<String, Object>>) summary.get("folders");
        return folders.stream().filter(f -> label.equals(f.get("label"))).mapToLong(f -> ((Number) f.get("total")).longValue()).sum();
    }

    @Test @Order(13)
    void uploadedImagesAreFiledByAiLabelAndCanBeReviewed() throws Exception {
        String email = "kho.anh" + SUFFIX + "@test.vn";
        var reg = call(HttpMethod.POST, "/api/v1/auth/register", null, Map.of("fullName", "Quan Tri Kho", "email", email, "password", "matkhau123"));
        @SuppressWarnings("unchecked") String uid = (String) ((Map<String, Object>) reg.getBody().get("user")).get("id");
        firebaseAuth.setCustomUserClaims(uid, Map.of("admin", true));
        String admin = (String) call(HttpMethod.POST, "/api/v1/auth/login", null, Map.of("email", email, "password", "matkhau123")).getBody().get("idToken");

        assertEquals(HttpStatus.FORBIDDEN, call(HttpMethod.GET, "/api/v1/admin/dataset/summary", tokenA, null).getStatusCode());
        var before = call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody();

        // Ảnh tải lên vào đúng thư mục AI dự đoán, kể cả ảnh không xác định.
        String blast = (String) scan(tokenA, Map.of("province", "An Giang", "district", DISTRICT)).getBody().get("id");
        String healthy = (String) scan(tokenA, Map.of("stage", "Gieo mạ")).getBody().get("id");
        var blurry = scan(tokenA, Map.of("stage", "Ảnh mờ")).getBody();
        String unknown = (String) blurry.get("id");
        assertEquals("NEEDS_RETAKE", blurry.get("status"));
        assertTrue(((List<?>) blurry.get("symptoms")).isEmpty(), "ảnh chưa kết luận không được hiện triệu chứng của bệnh nghi ngờ");

        var after = call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody();
        assertEquals(((Number) before.get("total")).longValue() + 3, ((Number) after.get("total")).longValue());
        assertEquals(folderTotal(before, "dao-on-la") + 1, folderTotal(after, "dao-on-la"));
        assertEquals(folderTotal(before, "healthy") + 1, folderTotal(after, "healthy"));
        assertEquals(folderTotal(before, "unknown") + 1, folderTotal(after, "unknown"));

        var image = call(HttpMethod.GET, "/api/v1/admin/dataset/" + blast, admin, null).getBody();
        assertEquals("dao-on-la", image.get("label"));
        assertEquals("PENDING", image.get("reviewStatus"));
        assertTrue(((String) image.get("imagePublicId")).contains("/dataset/dao-on-la/"), String.valueOf(image.get("imagePublicId")));
        assertTrue(((String) call(HttpMethod.GET, "/api/v1/admin/dataset/" + unknown, admin, null).getBody().get("imagePublicId")).contains("/dataset/khong-xac-dinh/"));
        // Ảnh của người dùng vẫn mở được bằng URL mới sau khi chuyển thư mục.
        assertEquals(image.get("imageUrl"), call(HttpMethod.GET, "/api/v1/scans/" + blast, tokenA, null).getBody().get("imageUrl"));

        @SuppressWarnings("unchecked") var inFolder = (List<Map<String, Object>>) call(HttpMethod.GET, "/api/v1/admin/dataset?label=healthy&limit=60", admin, null).getBody().get("items");
        assertTrue(inFolder.stream().anyMatch(i -> healthy.equals(i.get("id"))));

        // Người dùng báo sai: ảnh được đánh dấu để ưu tiên xem.
        assertEquals(HttpStatus.CREATED, call(HttpMethod.POST, "/api/v1/scans/" + blast + "/feedback", tokenA,
                Map.of("reason", "WRONG_RESULT", "suggestedDiseaseCode", "dom-nau")).getStatusCode());
        image = call(HttpMethod.GET, "/api/v1/admin/dataset/" + blast, admin, null).getBody();
        assertEquals(true, image.get("reportedWrong"));
        assertEquals("dom-nau", image.get("suggestedLabel"));

        // Duyệt: AI đúng, sửa nhãn, duyệt lại; bộ đếm phải khớp.
        long confirmed = ((Number) after.get("confirmed")).longValue(), corrected = ((Number) after.get("corrected")).longValue();
        assertEquals("CONFIRMED", call(HttpMethod.PUT, "/api/v1/admin/dataset/" + healthy + "/review", admin, Map.of("action", "CONFIRM")).getBody().get("reviewStatus"));
        assertEquals(HttpStatus.BAD_REQUEST, call(HttpMethod.PUT, "/api/v1/admin/dataset/" + blast + "/review", admin, Map.of("action", "CORRECT", "trueLabel", "khong-co")).getStatusCode());
        var fixed = call(HttpMethod.PUT, "/api/v1/admin/dataset/" + blast + "/review", admin, Map.of("action", "CORRECT", "trueLabel", "dom-nau", "note", "đốm tròn")).getBody();
        assertEquals("CORRECTED", fixed.get("reviewStatus"));
        assertEquals("dom-nau", fixed.get("trueLabel"));
        var reviewed = call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody();
        assertEquals(confirmed + 1, ((Number) reviewed.get("confirmed")).longValue());
        assertEquals(corrected + 1, ((Number) reviewed.get("corrected")).longValue());
        assertTrue(((List<?>) reviewed.get("confusion")).stream().anyMatch(c -> "dao-on-la".equals(((Map<?, ?>) c).get("ai")) && "dom-nau".equals(((Map<?, ?>) c).get("actual"))));

        call(HttpMethod.PUT, "/api/v1/admin/dataset/" + healthy + "/review", admin, Map.of("action", "RESET"));
        assertEquals(confirmed, ((Number) call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody().get("confirmed")).longValue());

        // Sửa nhãn nhiều lần chỉ được đếm một lần.
        call(HttpMethod.PUT, "/api/v1/admin/dataset/" + blast + "/review", admin, Map.of("action", "CORRECT", "trueLabel", "dom-nau"));
        assertEquals(corrected + 1, ((Number) call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody().get("corrected")).longValue());

        // Chỉ ảnh đã duyệt mới lọc ra được để huấn luyện lại.
        @SuppressWarnings("unchecked") var usable = (List<Map<String, Object>>) call(HttpMethod.GET, "/api/v1/admin/dataset?review=CORRECTED&limit=60", admin, null).getBody().get("items");
        assertTrue(usable.stream().anyMatch(i -> blast.equals(i.get("id"))));

        // Chẩn đoán lại ảnh không xác định giữ nguyên thư mục khi AI vẫn không nhận ra.
        assertEquals("unknown", call(HttpMethod.POST, "/api/v1/admin/dataset/" + unknown + "/rediagnose", admin, null).getBody().get("label"));

        // Người dùng xóa lượt quét: ảnh rời kho, bộ đếm trừ theo.
        assertEquals(HttpStatus.NO_CONTENT, call(HttpMethod.DELETE, "/api/v1/scans/" + blast, tokenA, null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, call(HttpMethod.GET, "/api/v1/admin/dataset/" + blast, admin, null).getStatusCode());
        var gone = call(HttpMethod.GET, "/api/v1/admin/dataset/summary", admin, null).getBody();
        assertEquals(((Number) after.get("total")).longValue() - 1, ((Number) gone.get("total")).longValue());
        assertEquals(corrected, ((Number) gone.get("corrected")).longValue());

        assertEquals(0, ((Number) call(HttpMethod.POST, "/api/v1/admin/dataset/backfill", admin, null).getBody().get("count")).intValue());

        // Các bảng quản trị hiện tên thay vì UID và có số liệu cho biểu đồ.
        @SuppressWarnings("unchecked") var posts = (List<Map<String, Object>>) call(HttpMethod.GET, "/api/v1/admin/posts?limit=5", admin, null).getBody().get("items");
        assertFalse(posts.isEmpty());
        assertNotNull(posts.get(0).get("title"), "bài viết phải được trải phẳng trong JSON");
        assertNotNull(posts.get(0).get("authorName"));
        assertNotNull(posts.get(0).get("pendingReports"));
        assertNotNull(call(HttpMethod.GET, "/api/v1/admin/posts/" + posts.get(0).get("id"), admin, null).getBody().get("authorName"));
        var stats = call(HttpMethod.GET, "/api/v1/admin/stats", admin, null).getBody();
        @SuppressWarnings("unchecked") var postStats = (Map<String, Object>) stats.get("posts");
        @SuppressWarnings("unchecked") var reportStats = (Map<String, Object>) stats.get("reports");
        @SuppressWarnings("unchecked") var feedbackStats = (Map<String, Object>) stats.get("feedbacks");
        assertNotNull(postStats.get("reported"));
        assertNotNull(reportStats.get("byReason"));
        assertNotNull(((Map<?, ?>) feedbackStats.get("byReason")).get("WRONG_RESULT"));
        assertEquals(HttpStatus.OK, call(HttpMethod.GET, "/api/v1/admin/reports?status=ALL", admin, null).getStatusCode());
        var info = call(HttpMethod.GET, "/api/v1/risk/areas/info?province=Thái Bình&district=" + DISTRICT + "&days=7", tokenA, null).getBody();
        assertEquals(7, ((List<?>) info.get("daily")).size());
    }
}

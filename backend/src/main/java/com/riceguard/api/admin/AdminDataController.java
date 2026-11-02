package com.riceguard.api.admin;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.riceguard.api.common.*;
import com.riceguard.api.community.Post;
import com.riceguard.api.community.CommunityService;
import com.riceguard.api.community.CommunityDtos.CommentView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserDtos.PublicUser;
import com.riceguard.api.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.riceguard.api.scan.Scan;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminDataController {
    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final CommunityService community;
    private final UserService users;

    public AdminDataController(Fs fs, Refs refs, Paging paging, Times times, CommunityService community, UserService users) {
        this.fs = fs; this.refs = refs; this.paging = paging; this.times = times; this.community = community; this.users = users;
    }

    public record AdminPost(String id, String authorId, String province, String topic, String title,
                            String content, List<com.riceguard.api.media.ImageRef> images,
                            long likeCount, long commentCount, long createdAt, long updatedAt, boolean hidden) {}

    /** Bài viết kèm tên tác giả và số báo cáo chưa xử lý, để bảng quản trị không phải hiện UID. */
    public record AdminPostView(@JsonUnwrapped AdminPost post, String authorName, long pendingReports) {}

    private List<AdminPostView> views(List<AdminPost> posts) {
        Map<String, PublicUser> names = users.lookup(posts.stream().map(AdminPost::authorId).collect(Collectors.toSet()));
        Map<String, Long> pending = fs.query(refs.reports(), AdminController.AdminReport.class).stream().filter(r -> "NEW".equals(r.status()))
                .collect(Collectors.groupingBy(AdminController.AdminReport::postId, Collectors.counting()));
        return posts.stream().map(p -> new AdminPostView(p, names.containsKey(p.authorId()) ? names.get(p.authorId()).name() : null,
                pending.getOrDefault(p.id(), 0L))).toList();
    }

    @GetMapping("/posts")
    public PageResult<AdminPostView> posts(@RequestParam(defaultValue = "ALL") String status,
                                       @RequestParam(defaultValue = "") String topic,
                                       @RequestParam(defaultValue = "") String authorId,
                                       @RequestParam(required = false) Boolean reported,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        Set<String> reportedPosts = reported == null ? Set.of() : fs.query(refs.reports(), AdminController.AdminReport.class)
                .stream().map(AdminController.AdminReport::postId).collect(Collectors.toSet());
        PageResult<AdminPost> page = paging.page(refs.posts(), cursor, Cursor.limit(limit, 20, 50), AdminPost.class, p ->
                (status.equals("ALL") || (status.equals("HIDDEN") == p.hidden()))
                && (topic.isBlank() || topic.equals(p.topic()))
                && (authorId.isBlank() || authorId.equals(p.authorId()))
                && (reported == null || reported == reportedPosts.contains(p.id()))
                && (needle.isBlank() || (p.title() + " " + p.content() + " " + p.authorId()).toLowerCase(Locale.ROOT).contains(needle)));
        return PageResult.of(views(page.items()), page.nextCursor());
    }

    @GetMapping("/posts/{id}")
    public AdminPostView post(@PathVariable String id) {
        return views(List.of(fs.require(refs.posts().document(id), AdminPost.class, "Không tìm thấy bài viết"))).get(0);
    }

    @GetMapping("/posts/{id}/comments")
    public List<CommentView> comments(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        return community.adminComments(admin.uid(), id);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<AdminController.AdminReport> reports = fs.query(refs.reports(), AdminController.AdminReport.class);
        List<AdminController.AdminFeedback> feedbacks = fs.query(refs.feedbacks(), AdminController.AdminFeedback.class);
        List<AdminPost> posts = fs.query(refs.posts(), AdminPost.class);
        long today = times.startOfDay(times.today());
        long yesterday = times.startOfDay(times.today().minusDays(1));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", Map.of("total", reports.size(), "pending", reports.stream().filter(r -> "NEW".equals(r.status())).count(),
                "resolvedToday", reports.stream().filter(r -> !"NEW".equals(r.status()) && r.resolvedAt() != null && r.resolvedAt() >= today).count(),
                "today", reports.stream().filter(r -> r.createdAt() >= today).count(),
                "yesterday", reports.stream().filter(r -> r.createdAt() >= yesterday && r.createdAt() < today).count(),
                "byReason", reports.stream().collect(Collectors.groupingBy(AdminController.AdminReport::reason, Collectors.counting()))));
        result.put("feedbacks", Map.of("total", feedbacks.size(), "pending", feedbacks.stream().filter(f -> "NEW".equals(f.status())).count(),
                "reviewing", feedbacks.stream().filter(f -> "REVIEWING".equals(f.status())).count(),
                "resolved", feedbacks.stream().filter(f -> "RESOLVED".equals(f.status())).count(),
                "today", feedbacks.stream().filter(f -> f.createdAt() >= today).count(),
                "byReason", feedbacks.stream().collect(Collectors.groupingBy(AdminController.AdminFeedback::reason, Collectors.counting()))));
        result.put("posts", Map.of("total", posts.size(), "visible", posts.stream().filter(p -> !p.hidden()).count(),
                "hidden", posts.stream().filter(AdminPost::hidden).count(),
                "today", posts.stream().filter(p -> p.createdAt() >= today).count(),
                "reported", reports.stream().filter(r -> "NEW".equals(r.status())).map(AdminController.AdminReport::postId).distinct().count()));
        return result;
    }

    /** Lượt quét là "ca bệnh" khi đã chẩn đoán ra một bệnh cụ thể: không tính lá khỏe, chưa xác định hay đang chờ. */
    static boolean isCase(Scan s) {
        String code = s.diseaseCode();
        return "DIAGNOSED".equals(s.status()) && code != null && !code.isBlank()
                && !"healthy".equals(code) && !"unknown".equals(code);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> stats = stats();
        List<Scan> scans = fs.query(fs.db().collectionGroup("scans"), Scan.class);
        long today = times.startOfDay(times.today());
        Map<String, Long> mix = scans.stream().filter(AdminDataController::isCase)
                .collect(Collectors.groupingBy(s -> s.diseaseName() == null || s.diseaseName().isBlank() ? s.diseaseCode() : s.diseaseName(), Collectors.counting()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            var date = times.today().minusDays(offset);
            long start = times.startOfDay(date), end = times.startOfDay(date.plusDays(1));
            trend.add(Map.of("day", date.toString(), "scans", scans.stream().filter(s -> s.createdAt() >= start && s.createdAt() < end).count(),
                    "cases", scans.stream().filter(s -> s.createdAt() >= start && s.createdAt() < end && isCase(s)).count()));
        }
        Map<String, Object> users = Map.of("total", fs.count(refs.users()),
                "newToday", fs.count(refs.users().whereGreaterThanOrEqualTo("createdAt", today)));
        return Map.of("scans", scans.size(), "todayCases", scans.stream().filter(s -> s.createdAt() >= today && isCase(s)).count(),
                "users", users, "reports", stats.get("reports"), "feedbacks", stats.get("feedbacks"), "posts", stats.get("posts"),
                "trend", trend, "diseaseMix", mix.entrySet().stream().map(e -> Map.of("name", e.getKey(), "value", e.getValue())).toList());
    }
}

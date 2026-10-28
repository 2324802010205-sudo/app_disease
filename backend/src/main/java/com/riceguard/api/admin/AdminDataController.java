package com.riceguard.api.admin;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.riceguard.api.common.*;
import com.riceguard.api.community.Post;
import com.riceguard.api.community.CommunityService;
import com.riceguard.api.community.CommunityDtos.CommentView;
import com.riceguard.api.security.AuthUser;
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

    public AdminDataController(Fs fs, Refs refs, Paging paging, Times times, CommunityService community) {
        this.fs = fs; this.refs = refs; this.paging = paging; this.times = times; this.community = community;
    }

    public record AdminPost(String id, String authorId, String province, String topic, String title,
                            String content, List<com.riceguard.api.media.ImageRef> images,
                            long likeCount, long commentCount, long createdAt, long updatedAt, boolean hidden) {}

    @GetMapping("/posts")
    public PageResult<AdminPost> posts(@RequestParam(defaultValue = "ALL") String status,
                                       @RequestParam(defaultValue = "") String topic,
                                       @RequestParam(defaultValue = "") String authorId,
                                       @RequestParam(required = false) Boolean reported,
                                       @RequestParam(defaultValue = "") String q,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(required = false) Integer limit) {
        String needle = q.toLowerCase(Locale.ROOT).trim();
        Set<String> reportedPosts = reported == null ? Set.of() : fs.query(refs.reports(), AdminController.AdminReport.class)
                .stream().map(AdminController.AdminReport::postId).collect(Collectors.toSet());
        return paging.page(refs.posts(), cursor, Cursor.limit(limit, 20, 50), AdminPost.class, p ->
                (status.equals("ALL") || (status.equals("HIDDEN") == p.hidden()))
                && (topic.isBlank() || topic.equals(p.topic()))
                && (authorId.isBlank() || authorId.equals(p.authorId()))
                && (reported == null || reported == reportedPosts.contains(p.id()))
                && (needle.isBlank() || (p.title() + " " + p.content() + " " + p.authorId()).toLowerCase(Locale.ROOT).contains(needle)));
    }

    @GetMapping("/posts/{id}")
    public AdminPost post(@PathVariable String id) {
        return fs.require(refs.posts().document(id), AdminPost.class, "Không tìm thấy bài viết");
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
                "yesterday", reports.stream().filter(r -> r.createdAt() >= yesterday && r.createdAt() < today).count()));
        result.put("feedbacks", Map.of("total", feedbacks.size(), "pending", feedbacks.stream().filter(f -> "NEW".equals(f.status())).count(),
                "reviewing", feedbacks.stream().filter(f -> "REVIEWING".equals(f.status())).count(),
                "resolved", feedbacks.stream().filter(f -> "RESOLVED".equals(f.status())).count()));
        result.put("posts", Map.of("total", posts.size(), "visible", posts.stream().filter(p -> !p.hidden()).count(),
                "hidden", posts.stream().filter(AdminPost::hidden).count(),
                "today", posts.stream().filter(p -> p.createdAt() >= today).count()));
        return result;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> stats = stats();
        List<Scan> scans = fs.query(fs.db().collectionGroup("scans"), Scan.class);
        long today = times.startOfDay(times.today());
        Map<String, Long> mix = scans.stream().filter(s -> s.diseaseCode() != null && !s.diseaseCode().isBlank())
                .collect(Collectors.groupingBy(Scan::diseaseCode, Collectors.counting()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            var date = times.today().minusDays(offset);
            long start = times.startOfDay(date), end = times.startOfDay(date.plusDays(1));
            trend.add(Map.of("day", date.toString(), "scans", scans.stream().filter(s -> s.createdAt() >= start && s.createdAt() < end).count(),
                    "cases", scans.stream().filter(s -> s.createdAt() >= start && s.createdAt() < end && s.diseaseCode() != null && !s.diseaseCode().isBlank()).count()));
        }
        return Map.of("scans", scans.size(), "todayCases", scans.stream().filter(s -> s.createdAt() >= today && s.diseaseCode() != null && !s.diseaseCode().isBlank()).count(),
                "reports", stats.get("reports"), "feedbacks", stats.get("feedbacks"), "posts", stats.get("posts"),
                "trend", trend, "diseaseMix", mix.entrySet().stream().map(e -> Map.of("name", e.getKey(), "value", e.getValue())).toList());
    }
}

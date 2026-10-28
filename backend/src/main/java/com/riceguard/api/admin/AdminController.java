package com.riceguard.api.admin;

import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.community.CommunityService;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;

/** Chỉ tài khoản có custom claim admin=true. */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Quản trị")
public class AdminController {
    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final CommunityService community;
    private final UserService users;
    private final DiseaseService diseases;
    private final MediaService media;
    private final ReminderService reminders;
    private final AdminOperationsController operations;
    private final AdminAuditService audit;

    public AdminController(Fs fs, Refs refs, Paging paging, Times times, CommunityService community, UserService users,
                           DiseaseService diseases, MediaService media, ReminderService reminders,
                           AdminOperationsController operations, AdminAuditService audit) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.community = community;
        this.users = users;
        this.diseases = diseases;
        this.media = media;
        this.reminders = reminders;
        this.operations = operations;
        this.audit = audit;
    }

    public record AdminReport(String id, String type, String postId, String targetId, String reporterId, String reason,
                              String detail, String status, long createdAt, Long resolvedAt) {}

    public record AdminFeedback(String id, String userId, String scanId, String scanDiseaseCode, Double scanConfidence,
                                String engine, String imageUrl, String reason, String suggestedDiseaseCode,
                                String description, List<ImageRef> attachments, String status, String resolution,
                                long createdAt, Long resolvedAt) {}

    @GetMapping("/reports")
    public PageResult<AdminReport> reports(@RequestParam(defaultValue = "NEW") String status,
                                           @RequestParam(defaultValue = "") String type,
                                           @RequestParam(defaultValue = "") String reason,
                                           @RequestParam(defaultValue = "") String priority,
                                           @RequestParam(defaultValue = "") String q,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        String needle = q.toLowerCase(java.util.Locale.ROOT).trim();
        return paging.page(refs.reports(), cursor, Cursor.limit(limit, 20, 50), AdminReport.class,
                r -> ("ALL".equals(status) || status.equals(r.status()))
                        && (type.isBlank() || type.equals(r.type()))
                        && (reason.isBlank() || reason.equals(r.reason()))
                        && (priority.isBlank() || priority.equals("MISLEADING".equals(r.reason()) ? "HIGH" : "SPAM".equals(r.reason()) ? "MEDIUM" : "LOW"))
                        && (needle.isBlank() || (r.id() + " " + r.postId() + " " + r.targetId() + " " + r.reporterId() + " " + r.detail()).toLowerCase(java.util.Locale.ROOT).contains(needle)));
    }

    /** status: RESOLVED | DISMISSED. */
    @PutMapping("/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@AuthenticationPrincipal AuthUser admin, @PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!"RESOLVED".equals(status) && !"DISMISSED".equals(status)) throw ApiException.badRequest("status phải là RESOLVED hoặc DISMISSED");
        fs.require(refs.reports().document(id), AdminReport.class, "Không tìm thấy báo cáo");
        Fs.await(refs.reports().document(id).update("status", status, "resolvedAt", times.now()));
        audit.record(admin.uid(), "REPORT_" + status, "REPORT", id, "");
    }

    @PostMapping("/reports/{id}/remove-content")
    public Map<String, Object> removeReportedContent(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        AdminReport report = fs.require(refs.reports().document(id), AdminReport.class, "Không tìm thấy báo cáo");
        boolean existed;
        if ("POST".equals(report.type())) {
            existed = fs.exists(refs.posts().document(report.postId()));
            if (existed) community.delete(admin, report.postId());
        } else if ("COMMENT".equals(report.type())) {
            existed = fs.exists(refs.comments(report.postId()).document(report.targetId()));
            if (existed) community.deleteComment(admin, report.postId(), report.targetId());
        } else throw ApiException.badRequest("Loại nội dung không hợp lệ");
        int resolved = 0;
        for (AdminReport related : fs.query(refs.reports(), AdminReport.class)) {
            boolean same = "POST".equals(report.type())
                    ? report.postId().equals(related.postId())
                    : report.postId().equals(related.postId()) && report.targetId().equals(related.targetId());
            if (same && "NEW".equals(related.status())) {
                Fs.await(refs.reports().document(related.id()).update("status", "RESOLVED", "resolvedAt", times.now()));
                resolved++;
            }
        }
        audit.record(admin.uid(), "REPORT_REMOVE_CONTENT", report.type(), report.targetId(),
                "reportsResolved=" + resolved + ";contentExisted=" + existed);
        return Map.of("contentExisted", existed, "reportsResolved", resolved);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        community.delete(admin, id);
        audit.record(admin.uid(), "POST_DELETE", "POST", id, "");
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal AuthUser admin, @PathVariable String postId,
                              @PathVariable String commentId) {
        community.deleteComment(admin, postId, commentId);
        audit.record(admin.uid(), "COMMENT_DELETE", "COMMENT", commentId, "postId=" + postId);
    }

    @PutMapping("/users/{uid}/verified-expert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifiedExpert(@AuthenticationPrincipal AuthUser admin, @PathVariable String uid, @RequestBody Map<String, Boolean> body) {
        if (!body.containsKey("verified")) throw ApiException.badRequest("Thiếu trạng thái verified");
        boolean verified = Boolean.TRUE.equals(body.get("verified"));
        users.setVerifiedExpert(uid, verified, admin.uid());
        audit.record(admin.uid(), verified ? "EXPERT_VERIFY" : "EXPERT_UNVERIFY", "USER", uid, "");
    }

    @PostMapping(value = "/diseases/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> diseaseImage(@AuthenticationPrincipal AuthUser admin, @PathVariable String id,
                                            @RequestParam("file") MultipartFile file) {
        var old = diseases.require(id);
        ImageRef ref = media.upload(file, admin.uid(), "disease");
        diseases.setImage(id, ref.url(), ref.publicId());
        media.deleteByPublicId(old.imagePublicId());
        audit.record(admin.uid(), "DISEASE_IMAGE", "DISEASE", id, "");
        return Map.of("imageUrl", ref.url());
    }

    @GetMapping("/feedbacks")
    public PageResult<AdminFeedback> feedbacks(@RequestParam(defaultValue = "ALL") String status,
                                                @RequestParam(defaultValue = "") String reason,
                                                @RequestParam(defaultValue = "") String suggestedDiseaseCode,
                                                @RequestParam(required = false) Long from,
                                                @RequestParam(required = false) Long to,
                                                @RequestParam(defaultValue = "") String q,
                                                @RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        String needle = q.toLowerCase(java.util.Locale.ROOT).trim();
        if (from != null && to != null && from > to) throw ApiException.badRequest("Khoảng thời gian không hợp lệ");
        return paging.page(refs.feedbacks(), cursor, Cursor.limit(limit, 20, 50), AdminFeedback.class,
                f -> ("ALL".equals(status) || status.equals(f.status()))
                        && (reason.isBlank() || reason.equals(f.reason()))
                        && (suggestedDiseaseCode.isBlank() || suggestedDiseaseCode.equals(f.suggestedDiseaseCode()))
                        && (from == null || f.createdAt() >= from)
                        && (to == null || f.createdAt() <= to)
                        && (needle.isBlank() || (f.id() + " " + f.userId() + " " + f.scanDiseaseCode() + " " + f.suggestedDiseaseCode() + " " + f.description()).toLowerCase(java.util.Locale.ROOT).contains(needle)));
    }

    @PutMapping("/feedbacks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateFeedback(@AuthenticationPrincipal AuthUser admin, @PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || !List.of("NEW", "REVIEWING", "RESOLVED").contains(status)) throw ApiException.badRequest("Trạng thái phản hồi không hợp lệ");
        String resolution = body.getOrDefault("resolution", "").trim();
        if (resolution.length() > 2000) throw ApiException.badRequest("Kết luận quá dài");
        fs.require(refs.feedbacks().document(id), AdminFeedback.class, "Không tìm thấy phản hồi");
        Fs.await(refs.feedbacks().document(id).update("status", status, "resolution", resolution,
                "resolvedAt", "RESOLVED".equals(status) ? times.now() : null));
        audit.record(admin.uid(), "FEEDBACK_" + status, "FEEDBACK", id, "");
    }

    @PutMapping("/posts/{id}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void postVisibility(@AuthenticationPrincipal AuthUser admin, @PathVariable String id, @RequestBody Map<String, Boolean> body) {
        if (!body.containsKey("hidden")) throw ApiException.badRequest("Thiếu trạng thái hidden");
        fs.require(refs.posts().document(id), com.riceguard.api.community.Post.class, "Không tìm thấy bài viết");
        Fs.await(refs.posts().document(id).update("hidden", body.get("hidden"), "updatedAt", times.now()));
        audit.record(admin.uid(), Boolean.TRUE.equals(body.get("hidden")) ? "POST_HIDE" : "POST_SHOW", "POST", id, "");
    }

    /** Chạy tay job nhắc lịch (kiểm thử). */
    @PostMapping("/jobs/reminders")
    public Map<String, Integer> runReminders(@AuthenticationPrincipal AuthUser admin, @RequestParam(defaultValue = "false") boolean test) {
        int sent = reminders.runFor(times.today(), false, test ? 10 : Integer.MAX_VALUE);
        operations.log(test ? "REMINDERS_TEST" : "REMINDERS", sent);
        audit.record(admin.uid(), test ? "REMINDERS_TEST" : "REMINDERS_RUN", "JOB", "reminders", "sent=" + sent);
        return Map.of("sent", sent);
    }
}

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

    public AdminController(Fs fs, Refs refs, Paging paging, Times times, CommunityService community, UserService users,
                           DiseaseService diseases, MediaService media, ReminderService reminders) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.community = community;
        this.users = users;
        this.diseases = diseases;
        this.media = media;
        this.reminders = reminders;
    }

    public record AdminReport(String id, String type, String postId, String targetId, String reporterId, String reason,
                              String detail, String status, long createdAt) {}

    public record AdminFeedback(String id, String userId, String scanId, String scanDiseaseCode, Double scanConfidence,
                                String engine, String imageUrl, String reason, String suggestedDiseaseCode,
                                String description, String status, long createdAt) {}

    @GetMapping("/reports")
    public PageResult<AdminReport> reports(@RequestParam(defaultValue = "NEW") String status,
                                           @RequestParam(required = false) String cursor,
                                           @RequestParam(required = false) Integer limit) {
        return paging.page(refs.reports(), cursor, Cursor.limit(limit, 20, 50), AdminReport.class,
                r -> "ALL".equals(status) || status.equals(r.status()));
    }

    /** status: RESOLVED | DISMISSED. */
    @PutMapping("/reports/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (!"RESOLVED".equals(status) && !"DISMISSED".equals(status)) throw ApiException.badRequest("status phải là RESOLVED hoặc DISMISSED");
        fs.require(refs.reports().document(id), AdminReport.class, "Không tìm thấy báo cáo");
        Fs.await(refs.reports().document(id).update("status", status, "resolvedAt", times.now()));
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        community.delete(admin, id);
    }

    @PutMapping("/users/{uid}/verified-expert")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifiedExpert(@PathVariable String uid, @RequestBody Map<String, Boolean> body) {
        users.setVerifiedExpert(uid, Boolean.TRUE.equals(body.get("verified")));
    }

    @PostMapping(value = "/diseases/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> diseaseImage(@AuthenticationPrincipal AuthUser admin, @PathVariable String id,
                                            @RequestParam("file") MultipartFile file) {
        var old = diseases.require(id);
        ImageRef ref = media.upload(file, admin.uid(), "disease");
        diseases.setImage(id, ref.url(), ref.publicId());
        media.deleteByPublicId(old.imagePublicId());
        return Map.of("imageUrl", ref.url());
    }

    @GetMapping("/feedbacks")
    public PageResult<AdminFeedback> feedbacks(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
        return paging.page(refs.feedbacks(), cursor, Cursor.limit(limit, 20, 50), AdminFeedback.class);
    }

    /** Chạy tay job nhắc lịch (kiểm thử). */
    @PostMapping("/jobs/reminders")
    public Map<String, Integer> runReminders() {
        return Map.of("sent", reminders.runFor(times.today(), false));
    }
}

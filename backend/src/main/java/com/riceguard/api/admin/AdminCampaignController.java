package com.riceguard.api.admin;

import com.google.cloud.firestore.DocumentSnapshot;
import com.riceguard.api.common.*;
import com.riceguard.api.security.AuthUser;
import com.riceguard.api.user.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
public class AdminCampaignController {
    public record Draft(@NotBlank @Size(max = 120) String title,
                        @NotBlank @Size(max = 2000) String body,
                        @NotBlank String audience,
                        String province,
                        List<String> userIds) {}
    public record Campaign(String id, String title, String body, String audience, String province,
                           List<String> userIds, String status, String createdBy, long createdAt,
                           Long startedAt, Long sentAt, int recipientCount, int delivered, int failed) {}
    public record Recipient(String id, String name, String province) {}
    public record Preview(int count, List<Recipient> recipients) {}

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final AdminCampaignDispatcher dispatcher;
    private final AdminAuditService audit;

    public AdminCampaignController(Fs fs, Refs refs, Paging paging, Times times,
                                   AdminCampaignDispatcher dispatcher, AdminAuditService audit) {
        this.fs = fs; this.refs = refs; this.paging = paging; this.times = times;
        this.dispatcher = dispatcher; this.audit = audit;
    }

    @PostMapping("/preview")
    public Preview preview(@Valid @RequestBody Draft draft) {
        validate(draft);
        List<Recipient> recipients = recipients(draft);
        return new Preview(recipients.size(), recipients.stream().limit(50).toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Campaign create(@AuthenticationPrincipal AuthUser admin, @Valid @RequestBody Draft draft) {
        validate(draft);
        var ref = fs.db().collection("adminCampaigns").document();
        Campaign campaign = new Campaign(ref.getId(), draft.title().trim(), draft.body().trim(), draft.audience(),
                draft.province(), draft.userIds() == null ? List.of() : List.copyOf(draft.userIds()),
                "DRAFT", admin.uid(), times.now(), null, null, 0, 0, 0);
        fs.set(ref, campaign);
        audit.record(admin.uid(), "CAMPAIGN_CREATE", "CAMPAIGN", campaign.id(), "");
        return campaign;
    }

    @GetMapping
    public PageResult<Campaign> list(@RequestParam(required = false) String cursor,
                                     @RequestParam(required = false) Integer limit) {
        return paging.page(fs.db().collection("adminCampaigns"), cursor, Cursor.limit(limit, 20, 50), Campaign.class);
    }

    @GetMapping("/{id}")
    public Campaign detail(@PathVariable String id) {
        return fs.require(fs.db().collection("adminCampaigns").document(id), Campaign.class, "Không tìm thấy chiến dịch");
    }

    @PostMapping("/{id}/send")
    public Campaign send(@AuthenticationPrincipal AuthUser admin, @PathVariable String id) {
        var ref = fs.db().collection("adminCampaigns").document(id);
        Campaign campaign = detail(id);
        if (!retryable(campaign)) throw ApiException.conflict("Chiến dịch đã được gửi hoặc đang xử lý");
        Draft draft = new Draft(campaign.title(), campaign.body(), campaign.audience(), campaign.province(), campaign.userIds());
        List<Recipient> recipients = recipients(draft);
        if (recipients.isEmpty()) throw ApiException.badRequest("Không có người nhận phù hợp");
        var auditRef = fs.db().collection("adminAudit").document();
        Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot current = tx.get(ref).get();
            if (!current.exists() || !retryable(fs.read(current, Campaign.class)))
                throw ApiException.conflict("Chiến dịch đã được gửi hoặc đang xử lý");
            tx.update(ref, "status", "SENDING", "startedAt", times.now(), "recipientCount", recipients.size());
            tx.set(auditRef, Map.of("actorId", admin.uid(), "action", "CAMPAIGN_START",
                    "targetType", "CAMPAIGN", "targetId", id, "detail", "recipients=" + recipients.size(),
                    "createdAt", times.now()));
            return null;
        }));
        dispatcher.dispatch(campaign, recipients, admin.uid());
        return detail(id);
    }

    private boolean retryable(Campaign campaign) {
        return "DRAFT".equals(campaign.status()) || "PARTIAL".equals(campaign.status())
                || ("SENDING".equals(campaign.status()) && campaign.startedAt() != null
                    && campaign.startedAt() < times.now() - 15 * 60_000L);
    }

    private static void validate(Draft draft) {
        if (draft.audience() == null || !List.of("ALL", "PROVINCE", "SELECTED").contains(draft.audience()))
            throw ApiException.badRequest("Nhóm người nhận không hợp lệ");
        if ("PROVINCE".equals(draft.audience()) && (draft.province() == null || draft.province().isBlank()))
            throw ApiException.badRequest("Chọn tỉnh/thành phố");
        if ("SELECTED".equals(draft.audience()) && (draft.userIds() == null || draft.userIds().isEmpty()))
            throw ApiException.badRequest("Chọn ít nhất một người nhận");
        if (draft.userIds() != null && draft.userIds().size() > 10000)
            throw ApiException.badRequest("Danh sách người nhận quá lớn");
    }

    private List<Recipient> recipients(Draft draft) {
        Set<String> selected = draft.userIds() == null ? Set.of() : new HashSet<>(draft.userIds());
        List<Recipient> result = new ArrayList<>();
        for (UserProfile user : fs.query(refs.users(), UserProfile.class)) {
            if (user.locked()) continue;
            if ("PROVINCE".equals(draft.audience()) && !draft.province().equalsIgnoreCase(user.province())) continue;
            if ("SELECTED".equals(draft.audience()) && !selected.contains(user.id())) continue;
            result.add(new Recipient(user.id(), user.fullName(), user.province()));
        }
        return result;
    }
}

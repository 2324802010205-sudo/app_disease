package com.riceguard.api.admin;

import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Times;
import com.riceguard.api.notification.NotificationService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdminCampaignDispatcher {
    private final Fs fs;
    private final NotificationService notifications;
    private final Times times;

    public AdminCampaignDispatcher(Fs fs, NotificationService notifications, Times times) {
        this.fs = fs; this.notifications = notifications; this.times = times;
    }

    @Async
    public void dispatch(AdminCampaignController.Campaign campaign,
                         List<AdminCampaignController.Recipient> recipients, String actorId) {
        var ref = fs.db().collection("adminCampaigns").document(campaign.id());
        int delivered = 0, failed = 0;
        for (AdminCampaignController.Recipient recipient : recipients) {
            try {
                if (notifications.createCampaign(recipient.id(), campaign.id(), campaign.title(), campaign.body())) delivered++;
                else failed++;
            } catch (RuntimeException error) { failed++; }
        }
        String status = failed == 0 ? "COMPLETED" : "PARTIAL";
        try {
            var batch = fs.db().batch();
            long now = times.now();
            batch.update(ref, "status", status, "sentAt", now, "delivered", delivered, "failed", failed);
            batch.set(fs.db().collection("adminAudit").document(), Map.of("actorId", actorId,
                    "action", "CAMPAIGN_SEND", "targetType", "CAMPAIGN", "targetId", campaign.id(),
                    "detail", "delivered=" + delivered + ";failed=" + failed, "createdAt", now));
            Fs.await(batch.commit());
        } catch (RuntimeException error) {
            // The campaign remains SENDING and can be retried after the lease expires.
        }
    }
}

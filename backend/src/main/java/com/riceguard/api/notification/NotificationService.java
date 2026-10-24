package com.riceguard.api.notification;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.WriteBatch;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.user.UserProfile;
import com.riceguard.api.user.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final UserService users;
    private final PushService push;

    public NotificationService(Fs fs, Refs refs, Paging paging, Times times, UserService users, PushService push) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.users = users;
        this.push = push;
    }

    /** Tạo thông báo (nếu người dùng chưa tắt loại này) và đẩy FCM không đồng bộ. */
    public Notification create(String uid, String kind, String title, String body, String refType, String refId) {
        UserProfile p = fs.get(refs.user(uid), UserProfile.class);
        if (p == null || !enabled(p.settingsOrDefault(), kind)) return null;
        DocumentReference ref = refs.notifications(uid).document();
        Notification n = new Notification(ref.getId(), kind, title, body, refType, refId, false, times.now());
        fs.set(ref, n);
        push.send(p.fcmTokens(), uid, n);
        return n;
    }

    static boolean enabled(UserProfile.Settings s, String kind) {
        return switch (kind) {
            case "ALERT", "WEATHER" -> s.notifyAlert();
            case "REMINDER" -> s.notifyReminder();
            case "COMMUNITY" -> s.notifyCommunity();
            default -> true;
        };
    }

    /** filter: all | unread | alert | reminder | community | tip */
    public PageResult<Notification> list(String uid, String filter, String cursor, int limit) {
        String f = filter == null ? "all" : filter.toLowerCase();
        return paging.page(refs.notifications(uid), cursor, limit, Notification.class, n -> switch (f) {
            case "unread" -> !n.read();
            case "alert" -> n.kind().equals("ALERT") || n.kind().equals("WEATHER");
            case "reminder" -> n.kind().equals("REMINDER");
            case "community" -> n.kind().equals("COMMUNITY");
            case "tip" -> n.kind().equals("TIP") || n.kind().equals("UPDATE");
            default -> true;
        });
    }

    public long unreadCount(String uid) {
        return fs.count(refs.notifications(uid).whereEqualTo("read", false));
    }

    public void markRead(String uid, String id) {
        DocumentReference ref = refs.notifications(uid).document(id);
        fs.require(ref, Notification.class, "Không tìm thấy thông báo");
        Fs.await(ref.update("read", true));
    }

    public int markAllRead(String uid) {
        var docs = Fs.await(refs.notifications(uid).whereEqualTo("read", false).limit(500).get()).getDocuments();
        if (docs.isEmpty()) return 0;
        WriteBatch batch = fs.db().batch();
        docs.forEach(d -> batch.update(d.getReference(), "read", true));
        Fs.await(batch.commit());
        return docs.size();
    }

    public void delete(String uid, String id) {
        Fs.await(refs.notifications(uid).document(id).delete());
    }

    public Map<String, Long> counts(String uid) {
        return Map.of("unread", unreadCount(uid));
    }
}

package com.riceguard.api.notification;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.riceguard.api.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Gửi thông báo đẩy FCM ở luồng nền, tự dọn token thiết bị đã hết hạn. */
@Component
public class PushService {
    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final FirebaseMessaging fcm;
    private final UserService users;

    public PushService(FirebaseMessaging fcm, UserService users) {
        this.fcm = fcm;
        this.users = users;
    }

    @Async
    public void send(List<String> tokens, String uid, Notification n) {
        if (tokens == null || tokens.isEmpty()) return;
        try {
            List<Message> messages = new ArrayList<>();
            for (String t : tokens) {
                Message.Builder b = Message.builder().setToken(t)
                        .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(n.title()).setBody(n.body()).build())
                        .putData("notificationId", n.id()).putData("kind", n.kind());
                if (n.refType() != null) b.putData("refType", n.refType());
                if (n.refId() != null) b.putData("refId", n.refId());
                if ("ALERT".equals(n.kind())) b.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
                messages.add(b.build());
            }
            BatchResponse res = fcm.sendEach(messages);
            List<SendResponse> rs = res.getResponses();
            for (int i = 0; i < rs.size(); i++) {
                FirebaseMessagingException ex = rs.get(i).getException();
                if (ex != null && (ex.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                        || ex.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                    users.removeDevice(uid, tokens.get(i));
                }
            }
        } catch (Exception e) {
            log.warn("Đẩy thông báo FCM thất bại cho {}: {}", uid, e.getMessage());
        }
    }
}

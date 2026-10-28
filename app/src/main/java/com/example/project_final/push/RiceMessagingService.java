package com.example.project_final.push;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.project_final.NotificationsActivity;
import com.example.project_final.R;
import com.example.project_final.api.Session;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** FCM handles background notification payloads; foreground messages are shown here. */
public class RiceMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL = "riceguard_updates";

    @Override public void onNewToken(String token) {
        PushRegistration.register(this, token);
    }

    @Override public void onMessageReceived(RemoteMessage message) {
        if (!Session.isLoggedIn()) return;
        String title = message.getNotification() == null ? message.getData().get("title") : message.getNotification().getTitle();
        String body = message.getNotification() == null ? message.getData().get("body") : message.getNotification().getBody();
        if (title == null && body == null) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    getString(R.string.push_channel_name), NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent intent = new Intent(this, NotificationsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        String id = message.getData().get("notificationId");
        manager.notify(id == null ? (int) System.currentTimeMillis() : id.hashCode(), notification.build());
    }
}

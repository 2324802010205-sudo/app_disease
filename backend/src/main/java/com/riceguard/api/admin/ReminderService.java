package com.riceguard.api.admin;

import com.google.cloud.firestore.DocumentSnapshot;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.riceguard.api.config.AppProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/** Mỗi sáng gửi nhắc lịch kiểm tra cho những ruộng có nextCheckDate = hôm nay. */
@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final NotificationService notifications;
    private final AppProperties props;

    public ReminderService(Fs fs, Refs refs, Times times, NotificationService notifications, AppProperties props) {
        this.props = props;
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${riceguard.reminders.cron}", zone = "${riceguard.time-zone}")
    public void daily() {
        if (!props.reminders().enabled()) return;
        try {
            int sent = runFor(times.today(), true);
            log.info("Nhắc lịch kiểm tra ruộng: đã gửi {} thông báo", sent);
        } catch (RuntimeException e) {
            log.error("Job nhắc lịch thất bại", e);
        }
    }

    /** Trả về số thông báo đã gửi; với useLock=true, nếu instance khác đã chạy job ngày này thì trả 0. */
    public int runFor(LocalDate date, boolean useLock) {
        if (useLock && !acquire("reminders_" + date)) return 0;
        int sent = 0;
        for (DocumentSnapshot d : Fs.await(fs.db().collectionGroup("fields").whereEqualTo("nextCheckDate", date.toString()).get()).getDocuments()) {
            String uid = d.getReference().getParent().getParent().getId();
            String name = d.getString("name");
            notifications.create(uid, "REMINDER", "Nhắc lịch kiểm tra ruộng",
                    "Đã đến lịch kiểm tra " + (name == null ? "ruộng" : name) + ". Hãy kiểm tra tình trạng lúa và cập nhật lên ứng dụng.",
                    "field", d.getId());
            sent++;
        }
        return sent;
    }

    /** Khóa phân tán đơn giản: chỉ một instance tạo được document khóa của ngày. */
    private boolean acquire(String key) {
        try {
            Fs.await(refs.locks().document(key).create(Map.of("at", times.now())));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}

package com.riceguard.api.scan;

import com.google.cloud.firestore.DocumentReference;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.diagnosis.DiagnosisException;
import com.riceguard.api.diagnosis.DiagnosisResult;
import com.riceguard.api.diagnosis.DiagnosisService;
import com.riceguard.api.diary.DiaryService;
import com.riceguard.api.field.Field;
import com.riceguard.api.field.FieldService;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.notification.NotificationService;
import com.riceguard.api.risk.AreaStatService;
import com.riceguard.api.weather.Provinces;
import com.riceguard.api.weather.WeatherDto;
import com.riceguard.api.weather.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class ScanService {
    private static final Logger log = LoggerFactory.getLogger(ScanService.class);

    public record ScanInput(MultipartFile image, String fieldId, String stage, String variety, String note,
                            String province, String district, Double lat, Double lng) {}

    public record Comparison(Scan first, Scan second, long daysBetween, boolean sameDisease, double confidenceDelta, String trend) {}

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final MediaService media;
    private final FieldService fields;
    private final DiagnosisService diagnosis;
    private final WeatherService weather;
    private final AreaStatService areaStats;
    private final DiaryService diary;
    private final NotificationService notifications;

    public ScanService(Fs fs, Refs refs, Paging paging, Times times, MediaService media, FieldService fields,
                       DiagnosisService diagnosis, WeatherService weather, AreaStatService areaStats,
                       DiaryService diary, NotificationService notifications) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.media = media;
        this.fields = fields;
        this.diagnosis = diagnosis;
        this.weather = weather;
        this.areaStats = areaStats;
        this.diary = diary;
        this.notifications = notifications;
    }

    public Scan create(String uid, ScanInput in) {
        byte[] bytes = media.readValidated(in.image());
        Field field = null;
        if (in.fieldId() != null && !in.fieldId().isBlank()) field = fields.get(uid, in.fieldId());

        String province = firstNonBlank(field == null ? null : field.province(), in.province());
        String district = firstNonBlank(field == null ? null : field.district(), in.district());
        Double lat = field != null && field.lat() != null ? field.lat() : in.lat();
        Double lng = field != null && field.lng() != null ? field.lng() : in.lng();
        if (lat == null || lng == null) {
            var p = Provinces.find(province);
            if (p.isPresent()) { lat = p.get().lat(); lng = p.get().lng(); }
        }
        String stage = firstNonBlank(in.stage(), field == null ? null : field.stage());
        String variety = firstNonBlank(in.variety(), field == null ? null : field.variety());

        ImageRef image = media.uploadBytes(bytes, uid, "scan");
        WeatherDto.Snapshot snap = weather.snapshot(lat, lng);

        long now = times.now();
        DocumentReference ref = refs.scans(uid).document();
        Scan scan = base(ref.getId(), field, province, district, image, stage, variety, blank(in.note()), now, snap);
        scan = diagnoseInto(scan);
        fs.set(ref, scan);
        afterSave(uid, field, scan);
        return scan;
    }

    /** Chẩn đoán lại một lượt quét đang chờ hoặc cần chụp lại (ví dụ khi dịch vụ AI vừa khôi phục). */
    public Scan rediagnose(String uid, String id) {
        Scan old = get(uid, id);
        if ("DIAGNOSED".equals(old.status())) return old;
        Scan updated = diagnoseInto(old);
        fs.set(refs.scans(uid).document(id), updated);
        Field field = fields.find(uid, old.fieldId());
        afterSave(uid, field, updated);
        return updated;
    }

    private Scan base(String id, Field field, String province, String district, ImageRef image, String stage, String variety,
                      String note, long now, WeatherDto.Snapshot snap) {
        return new Scan(id, field == null ? null : field.id(), field == null ? null : field.name(), province, district,
                image.url(), image.publicId(), media.variant(image.url(), 480), "PENDING", "UNKNOWN", null, null, 0, null,
                List.of(), null, List.of(), null, snap, stage, variety, note, null, false, now);
    }

    private Scan diagnoseInto(Scan s) {
        if (!diagnosis.available()) {
            return with(s, "PENDING", "UNKNOWN", null, null, 0, null, List.of(), "Chưa có dịch vụ chẩn đoán AI, kết quả sẽ có sau.", List.of(), null, diagnosis.engineName());
        }
        try {
            String weatherText = s.weather() == null ? null
                    : s.weather().condition() + ", " + Math.round(s.weather().tempC()) + "°C, độ ẩm " + s.weather().humidity() + "%";
            DiagnosisResult r = diagnosis.run(media.variant(s.imageUrl(), 1568), s.stage(), s.variety(), weatherText);
            DiagnosisService.Outcome o = diagnosis.evaluate(r);
            double conf = Math.max(0, Math.min(1, r.confidence()));
            if ("NEEDS_RETAKE".equals(o.status())) {
                String advice = r.retakeAdvice() != null && !r.retakeAdvice().isBlank() ? r.retakeAdvice()
                        : "Ảnh chưa đủ rõ để kết luận. Hãy chụp lại cận lá bệnh, đủ sáng, không rung tay.";
                return with(s, o.status(), o.riskLevel(), null, null, conf, null, list(r.symptoms()), r.summary(), List.of(), advice, diagnosis.engineName());
            }
            return with(s, o.status(), o.riskLevel(), o.diseaseCode(), o.diseaseName(), conf, o.severity(), list(r.symptoms()),
                    r.summary(), r.recommendations() == null ? List.of() : r.recommendations(), null, diagnosis.engineName());
        } catch (DiagnosisException e) {
            log.warn("Chẩn đoán thất bại: {}", e.getMessage());
            return with(s, "PENDING", "UNKNOWN", null, null, 0, null, List.of(), "Chưa chẩn đoán được, vui lòng thử lại sau ít phút.", List.of(), null, diagnosis.engineName());
        }
    }

    private static Scan with(Scan s, String status, String risk, String code, String name, double conf, String severity,
                             List<String> symptoms, String summary, List<DiagnosisResult.Recommendation> recos, String retake, String engine) {
        return new Scan(s.id(), s.fieldId(), s.fieldName(), s.province(), s.district(), s.imageUrl(), s.imagePublicId(), s.thumbUrl(),
                status, risk, code, name, conf, severity, symptoms, summary, recos, retake, s.weather(), s.stage(), s.variety(),
                s.note(), engine, s.countedInArea(), s.createdAt());
    }

    /** Việc phụ sau khi lưu: cập nhật ruộng, bộ đếm khu vực, nhật ký, thông báo. Lỗi ở đây không làm mất lượt quét. */
    private void afterSave(String uid, Field field, Scan scan) {
        if (!"DIAGNOSED".equals(scan.status())) return;
        try {
            boolean diseased = scan.diseaseCode() != null && !"healthy".equals(scan.diseaseCode());
            if (diseased && !scan.countedInArea()) {
                areaStats.record(scan.province(), scan.district(), scan.diseaseCode(), scan.stage(), scan.createdAt(), 1);
                Fs.await(refs.scans(uid).document(scan.id()).update("countedInArea", true));
            }
            if (field != null) {
                int recheck = switch (scan.riskLevel()) { case "HIGH" -> 5; case "WATCH" -> 7; default -> 14; };
                fields.recordScan(uid, field.id(), scan.id(), scan.riskLevel(), scan.createdAt(), times.today().plusDays(recheck));
                String title = diseased ? "Phát hiện " + scan.diseaseName() : "Lúa khỏe mạnh";
                diary.createFromScan(uid, field, scan.id(), title, scan.summary(), new ImageRef(scan.thumbUrl(), null));
            }
            if (!"NORMAL".equals(scan.riskLevel())) {
                String where = scan.fieldName() == null ? "" : " tại " + scan.fieldName();
                notifications.create(uid, "ALERT", "Cảnh báo: " + scan.diseaseName(),
                        "Phát hiện dấu hiệu " + scan.diseaseName().toLowerCase() + where + ". Nên kiểm tra lại sau 5–7 ngày.", "scan", scan.id());
            }
        } catch (RuntimeException e) {
            log.error("Xử lý sau khi quét thất bại (scan={}): {}", scan.id(), e.getMessage(), e);
        }
    }

    public Scan get(String uid, String id) {
        return fs.require(refs.scans(uid).document(id), Scan.class, "Không tìm thấy lượt quét");
    }

    /** range: 7d | 30d | all; risk: NORMAL | WATCH | HIGH; fieldId tùy chọn. */
    public PageResult<Scan> list(String uid, String range, String risk, String fieldId, String cursor, int limit) {
        long since = switch (range == null ? "all" : range) {
            case "7d" -> times.sinceDays(7);
            case "30d" -> times.sinceDays(30);
            default -> 0L;
        };
        return paging.page(refs.scans(uid).whereGreaterThanOrEqualTo("createdAt", since), cursor, limit, Scan.class,
                s -> (risk == null || risk.isBlank() || risk.equalsIgnoreCase(s.riskLevel()))
                        && (fieldId == null || fieldId.isBlank() || fieldId.equals(s.fieldId())));
    }

    public List<Scan> recentForField(String uid, String fieldId, int limit) {
        return paging.page(refs.scans(uid), null, limit, Scan.class, s -> fieldId.equals(s.fieldId())).items();
    }

    public Scan updateNote(String uid, String id, String note) {
        get(uid, id);
        Fs.await(refs.scans(uid).document(id).update("note", blank(note)));
        return get(uid, id);
    }

    public void delete(String uid, String id) {
        Scan s = get(uid, id);
        Fs.await(refs.scans(uid).document(id).delete());
        media.deleteByPublicId(s.imagePublicId());
        diary.deleteByScan(uid, id);
        if (s.countedInArea() && s.diseaseCode() != null) {
            areaStats.record(s.province(), s.district(), s.diseaseCode(), s.stage(), s.createdAt(), -1);
        }
    }

    public Comparison compare(String uid, String firstId, String secondId) {
        Scan a = get(uid, firstId);
        Scan b = get(uid, secondId);
        if (a.createdAt() > b.createdAt()) { Scan t = a; a = b; b = t; }
        boolean same = a.diseaseCode() != null && a.diseaseCode().equals(b.diseaseCode());
        double delta = b.confidence() - a.confidence();
        String trend;
        if (!same) trend = "CHANGED";
        else if (rank(b.severity()) < rank(a.severity())) trend = "IMPROVING";
        else if (rank(b.severity()) > rank(a.severity())) trend = "WORSENING";
        else trend = "STABLE";
        long days = java.time.temporal.ChronoUnit.DAYS.between(times.dateOf(a.createdAt()), times.dateOf(b.createdAt()));
        return new Comparison(a, b, days, same, Math.round(delta * 100.0) / 100.0, trend);
    }

    private static int rank(String severity) {
        return switch (severity == null ? "" : severity) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    public Map<String, Object> feedbackContext(String uid, String id) {
        Scan s = get(uid, id);
        return Map.of("scanId", s.id(), "diseaseCode", String.valueOf(s.diseaseCode()), "engine", String.valueOf(s.engine()));
    }

    private static <T> List<T> list(List<T> l) {
        return l == null ? List.of() : l;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}

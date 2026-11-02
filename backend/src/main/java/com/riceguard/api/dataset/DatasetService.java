package com.riceguard.api.dataset;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.SetOptions;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Cursor;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.disease.Disease;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.media.MediaService;
import com.riceguard.api.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Kho ảnh quét theo nhãn AI: ghi khi người dùng tải ảnh, quản trị viên duyệt để cải tiến mô hình. */
@Service
public class DatasetService {
    private static final Logger log = LoggerFactory.getLogger(DatasetService.class);

    public static final String PENDING = "pending", UNKNOWN = "unknown", HEALTHY = "healthy";

    public record Folder(String label, String name, long total, long confirmed, long corrected, long rejected, Double accuracy) {}

    public record Confusion(String ai, String aiName, String actual, String actualName, long count) {}

    public record Summary(long total, long reviewed, long confirmed, long corrected, long rejected, Double accuracy,
                          List<Folder> folders, List<Confusion> confusion) {}

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final MediaService media;
    private final DiseaseService diseases;

    public DatasetService(Fs fs, Refs refs, Paging paging, Times times, MediaService media, DiseaseService diseases) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.media = media;
        this.diseases = diseases;
    }

    /** Thư mục theo nhãn AI: chưa chẩn đoán = pending, không xác định/chụp lại = unknown. */
    public static String labelOf(Scan s) {
        if ("PENDING".equals(s.status())) return PENDING;
        return "DIAGNOSED".equals(s.status()) && s.diseaseCode() != null ? s.diseaseCode() : UNKNOWN;
    }

    /** Ghi hoặc cập nhật ảnh sau mỗi lần chẩn đoán. Đổi nhãn (chẩn đoán lại) thì bỏ kết quả duyệt cũ. */
    public void record(String uid, Scan s) {
        String label = labelOf(s);
        DocumentReference ref = refs.datasetImages().document(s.id());
        Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot old = tx.get(ref).get();
            DatasetImage prev = old.exists() ? fs.read(old, DatasetImage.class) : null;
            boolean same = prev != null && label.equals(prev.label());
            DatasetCounters c = new DatasetCounters();
            if (prev == null) c.image(label, 1);
            else if (!same) {
                c.image(prev.label(), -1);
                c.image(label, 1);
                c.review(prev.label(), prev.reviewStatus(), prev.trueLabel(), -1);
            }
            DatasetImage next = new DatasetImage(s.id(), uid, label, s.diseaseName(), s.status(), s.confidence(), s.severity(), s.engine(),
                    s.imageUrl(), s.imagePublicId(), s.thumbUrl(), s.province(), s.district(), s.stage(),
                    same ? prev.reviewStatus() : "PENDING", same ? prev.trueLabel() : null, same ? prev.reviewNote() : null,
                    same ? prev.reviewedBy() : null, same ? prev.reviewedAt() : null,
                    prev != null && prev.reportedWrong(), prev == null ? null : prev.suggestedLabel(), s.createdAt());
            tx.set(ref, fs.toMap(next));
            if (!c.raw().isEmpty()) tx.set(stats(), c.toIncrements(), SetOptions.merge());
            return null;
        }));
    }

    /** Người dùng báo kết quả sai: đánh dấu để quản trị viên ưu tiên xem. */
    public void markReported(String scanId, String suggestedLabel) {
        DocumentReference ref = refs.datasetImages().document(scanId);
        if (!fs.exists(ref)) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportedWrong", true);
        if (suggestedLabel != null && !suggestedLabel.isBlank()) m.put("suggestedLabel", suggestedLabel);
        Fs.await(ref.update(m));
    }

    /** Lượt quét bị xóa (hoặc xóa tài khoản): gỡ khỏi kho và trừ bộ đếm. */
    public void remove(String scanId) {
        DocumentReference ref = refs.datasetImages().document(scanId);
        Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot old = tx.get(ref).get();
            if (!old.exists()) return null;
            DatasetImage prev = fs.read(old, DatasetImage.class);
            DatasetCounters c = new DatasetCounters();
            c.image(prev.label(), -1);
            c.review(prev.label(), prev.reviewStatus(), prev.trueLabel(), -1);
            tx.delete(ref);
            tx.set(stats(), c.toIncrements(), SetOptions.merge());
            return null;
        }));
    }

    public DatasetImage require(String id) {
        return fs.require(refs.datasetImages().document(id), DatasetImage.class, "Không tìm thấy ảnh");
    }

    /** review: PENDING | CONFIRMED | CORRECTED | REJECTED | ALL. conf: LOW (<60%) | MID (60-85%) | HIGH (>=85%). */
    public PageResult<DatasetImage> list(String label, String review, Boolean reported, String conf, String q, String cursor, Integer limit) {
        String needle = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        Query base = refs.datasetImages();
        return paging.page(base, cursor, Cursor.limit(limit, 24, 60), DatasetImage.class, d ->
                (label == null || label.isBlank() || label.equals(d.label()))
                        && (review == null || review.isBlank() || "ALL".equals(review) || review.equals(d.reviewStatus()))
                        && (reported == null || reported == d.reportedWrong())
                        && confidenceMatches(conf, d.confidence())
                        && (needle.isBlank() || (d.id() + " " + d.userId() + " " + d.province() + " " + d.district() + " " + d.diseaseName())
                        .toLowerCase(Locale.ROOT).contains(needle)));
    }

    static boolean confidenceMatches(String conf, double c) {
        return switch (conf == null ? "" : conf) {
            case "LOW" -> c < 0.6;
            case "MID" -> c >= 0.6 && c < 0.85;
            case "HIGH" -> c >= 0.85;
            default -> true;
        };
    }

    /** action: CONFIRM (AI đúng) | CORRECT (AI sai, kèm trueLabel) | REJECT (ảnh không dùng được) | RESET (duyệt lại). */
    public DatasetImage review(String adminUid, String id, String action, String trueLabel, String note) {
        String status = switch (action == null ? "" : action) {
            case "CONFIRM" -> "CONFIRMED";
            case "CORRECT" -> "CORRECTED";
            case "REJECT" -> "REJECTED";
            case "RESET" -> "PENDING";
            default -> throw ApiException.badRequest("Thao tác duyệt không hợp lệ");
        };
        if ("CORRECTED".equals(status) && !knownLabel(trueLabel)) throw ApiException.badRequest("Chọn nhãn đúng trong danh sách bệnh");
        String clean = note == null || note.isBlank() ? null : note.trim();
        if (clean != null && clean.length() > 500) throw ApiException.badRequest("Ghi chú tối đa 500 ký tự");
        DocumentReference ref = refs.datasetImages().document(id);
        long now = times.now();
        DatasetImage updated = Fs.await(fs.db().runTransaction(tx -> {
            DocumentSnapshot old = tx.get(ref).get();
            if (!old.exists()) throw ApiException.notFound("Không tìm thấy ảnh");
            DatasetImage p = fs.read(old, DatasetImage.class);
            String truth = switch (status) {
                case "CONFIRMED" -> p.label();
                case "CORRECTED" -> trueLabel;
                default -> null;
            };
            DatasetCounters c = new DatasetCounters();
            c.review(p.label(), p.reviewStatus(), p.trueLabel(), -1);
            c.review(p.label(), status, truth, 1);
            DatasetImage n = new DatasetImage(p.id(), p.userId(), p.label(), p.diseaseName(), p.status(), p.confidence(), p.severity(), p.engine(),
                    p.imageUrl(), p.imagePublicId(), p.thumbUrl(), p.province(), p.district(), p.stage(), status, truth, clean,
                    "PENDING".equals(status) ? null : adminUid, "PENDING".equals(status) ? null : now,
                    p.reportedWrong(), p.suggestedLabel(), p.createdAt());
            tx.set(ref, fs.toMap(n));
            if (!c.raw().isEmpty()) tx.set(stats(), c.toIncrements(), SetOptions.merge());
            return n;
        }));
        refile(updated);
        return updated;
    }

    /** Đổi thư mục hiển thị trên kho ảnh theo kết quả duyệt. URL giữ nguyên nên ảnh trong app người dùng không bị hỏng. */
    private void refile(DatasetImage d) {
        try {
            Map<String, String> context = new LinkedHashMap<>();
            context.put("ai_label", d.label());
            context.put("review", d.reviewStatus());
            if (d.trueLabel() != null) context.put("true_label", d.trueLabel());
            media.refile(d.imagePublicId(), "PENDING".equals(d.reviewStatus()) ? media.datasetFolder(d.label())
                    : media.verifiedFolder("REJECTED".equals(d.reviewStatus()) ? "rejected" : d.trueLabel()), context);
        } catch (RuntimeException e) {
            log.warn("Không đổi được thư mục ảnh {}: {}", d.id(), e.getMessage());
        }
    }

    private boolean knownLabel(String label) {
        if (label == null || label.isBlank()) return false;
        return HEALTHY.equals(label) || UNKNOWN.equals(label) || diseases.find(label) != null;
    }

    /** Đưa các lượt quét cũ (trước khi có kho ảnh) vào kho. Trả về số ảnh mới thêm. */
    public int backfill() {
        int added = 0;
        for (DocumentSnapshot d : Fs.await(fs.db().collectionGroup("scans").get()).getDocuments()) {
            if (fs.exists(refs.datasetImages().document(d.getId()))) continue;
            Scan scan = fs.read(d, Scan.class);
            record(d.getReference().getParent().getParent().getId(), scan);
            added++;
            try {
                // Ảnh cũ giữ nguyên URL, chỉ đổi thư mục hiển thị để kho ảnh khớp với trang quản trị.
                media.refile(scan.imagePublicId(), media.datasetFolder(labelOf(scan)), Map.of("scan_id", scan.id(), "ai_label", labelOf(scan)));
            } catch (RuntimeException e) {
                log.warn("Không đổi được thư mục ảnh cũ {}: {}", scan.id(), e.getMessage());
            }
        }
        return added;
    }

    @SuppressWarnings("unchecked")
    public Summary summary() {
        DocumentSnapshot s = Fs.await(stats().get());
        Map<String, Object> data = s.exists() && s.getData() != null ? s.getData() : Map.of();
        return build(data, labels());
    }

    /** Mọi thư mục luôn hiện, kể cả thư mục chưa có ảnh: các bệnh best.pt nhận diện được, lá khỏe, không xác định, chờ phân tích. */
    private Map<String, String> labels() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Disease d : diseases.all()) if (d.aiDetectable()) out.put(d.id(), d.name());
        out.put(HEALTHY, "Lá khỏe");
        out.put(UNKNOWN, "Không xác định");
        out.put(PENDING, "Chờ phân tích");
        return out;
    }

    @SuppressWarnings("unchecked")
    static Summary build(Map<String, Object> data, Map<String, String> names) {
        Map<String, Object> byLabel = (Map<String, Object>) data.getOrDefault("byLabel", Map.of());
        Map<String, Object> reviewed = (Map<String, Object>) data.getOrDefault("reviewed", Map.of());
        Map<String, Object> confusion = (Map<String, Object>) data.getOrDefault("confusion", Map.of());
        Map<String, String> all = new LinkedHashMap<>(names);
        byLabel.keySet().forEach(k -> all.putIfAbsent(k, k));   // nhãn cũ đã bị gỡ khỏi thư viện vẫn phải hiện
        List<Folder> folders = new ArrayList<>();
        long confirmed = 0, corrected = 0, rejected = 0;
        for (var e : all.entrySet()) {
            Map<String, Object> r = (Map<String, Object>) reviewed.getOrDefault(e.getKey(), Map.of());
            long ok = num(r.get("confirmed")), bad = num(r.get("corrected")), no = num(r.get("rejected"));
            confirmed += ok;
            corrected += bad;
            rejected += no;
            folders.add(new Folder(e.getKey(), e.getValue(), num(byLabel.get(e.getKey())), ok, bad, no, ratio(ok, bad)));
        }
        List<Confusion> pairs = new ArrayList<>();
        for (var ai : confusion.entrySet()) {
            for (var actual : ((Map<String, Object>) ai.getValue()).entrySet()) {
                long n = num(actual.getValue());
                if (n > 0) pairs.add(new Confusion(ai.getKey(), all.getOrDefault(ai.getKey(), ai.getKey()), actual.getKey(),
                        all.getOrDefault(actual.getKey(), actual.getKey()), n));
            }
        }
        pairs.sort((a, b) -> Long.compare(b.count(), a.count()));
        return new Summary(num(data.get("total")), confirmed + corrected + rejected, confirmed, corrected, rejected, ratio(confirmed, corrected), folders, pairs);
    }

    private static Double ratio(long ok, long bad) {
        return ok + bad == 0 ? null : (double) ok / (ok + bad);
    }

    private static long num(Object o) {
        return o instanceof Number n ? Math.max(0, n.longValue()) : 0;
    }

    private DocumentReference stats() {
        return refs.meta().document("dataset");
    }
}

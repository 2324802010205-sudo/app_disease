package com.riceguard.api.field;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Query;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.field.FieldDtos.FieldDto;
import com.riceguard.api.field.FieldDtos.FieldRequest;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FieldService {
    static final int MAX_FIELDS = 100;

    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final MediaService media;

    public FieldService(Fs fs, Refs refs, Times times, MediaService media) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.media = media;
    }

    public Field create(String uid, FieldRequest r) {
        if (fs.count(refs.fields(uid)) >= MAX_FIELDS) throw ApiException.badRequest("Bạn đã đạt tối đa " + MAX_FIELDS + " ruộng");
        long now = times.now();
        DocumentReference ref = refs.fields(uid).document();
        Field f = new Field(ref.getId(), r.name().trim(), blank(r.plot()), blank(r.province()), blank(r.district()), blank(r.address()),
                r.lat(), r.lng(), r.areaHa() == null ? 0 : r.areaHa(), blank(r.variety()), blank(r.stage()),
                r.plantingDate() == null ? null : r.plantingDate().toString(), blank(r.note()), null, null,
                r.nextCheckDate() == null ? null : r.nextCheckDate().toString(), null, null, null, now, now);
        fs.set(ref, f);
        return f;
    }

    public Field get(String uid, String id) {
        return fs.require(refs.fields(uid).document(id), Field.class, "Không tìm thấy ruộng");
    }

    public Field find(String uid, String id) {
        if (id == null || id.isBlank()) return null;
        return fs.get(refs.fields(uid).document(id), Field.class);
    }

    public List<FieldDto> list(String uid, String sort) {
        List<Field> all = fs.query(refs.fields(uid).limit(MAX_FIELDS), Field.class);
        Comparator<Field> cmp = switch (sort == null ? "updated" : sort) {
            case "name" -> Comparator.comparing(Field::name, String.CASE_INSENSITIVE_ORDER);
            case "area" -> Comparator.comparingDouble(Field::areaHa).reversed();
            default -> Comparator.comparingLong(Field::updatedAt).reversed();
        };
        return all.stream().sorted(cmp).map(this::toDto).toList();
    }

    public Field update(String uid, String id, FieldRequest r) {
        Field old = get(uid, id);
        Map<String, Object> m = new HashMap<>();
        m.put("name", r.name().trim());
        m.put("plot", blank(r.plot()));
        m.put("province", blank(r.province()));
        m.put("district", blank(r.district()));
        m.put("address", blank(r.address()));
        m.put("lat", r.lat());
        m.put("lng", r.lng());
        m.put("areaHa", r.areaHa() == null ? old.areaHa() : r.areaHa());
        m.put("variety", blank(r.variety()));
        m.put("stage", blank(r.stage()));
        m.put("plantingDate", r.plantingDate() == null ? null : r.plantingDate().toString());
        m.put("note", blank(r.note()));
        m.put("nextCheckDate", r.nextCheckDate() == null ? null : r.nextCheckDate().toString());
        m.put("updatedAt", times.now());
        Fs.await(refs.fields(uid).document(id).update(m));
        return get(uid, id);
    }

    public Field setPhoto(String uid, String id, ImageRef image) {
        Field old = get(uid, id);
        Fs.await(refs.fields(uid).document(id).update("photoUrl", image.url(), "photoPublicId", image.publicId(), "updatedAt", times.now()));
        media.deleteByPublicId(old.photoPublicId());
        return get(uid, id);
    }

    public void delete(String uid, String id) {
        Field f = get(uid, id);
        Fs.await(refs.fields(uid).document(id).delete());
        media.deleteByPublicId(f.photoPublicId());
    }

    /** Cập nhật ruộng sau mỗi lần quét: lần quét gần nhất, mức rủi ro, ngày kiểm tra tiếp theo. */
    public void recordScan(String uid, String fieldId, String scanId, String risk, long at, LocalDate nextCheck) {
        Map<String, Object> m = new HashMap<>();
        m.put("lastScanId", scanId);
        m.put("lastScanAt", at);
        m.put("lastRisk", risk);
        m.put("nextCheckDate", nextCheck.toString());
        m.put("updatedAt", at);
        Fs.await(refs.fields(uid).document(fieldId).update(m));
    }

    public FieldDto toDto(Field f) {
        String status = f.lastRisk() == null ? "UNKNOWN" : switch (f.lastRisk()) {
            case "HIGH" -> "ALERT";
            case "WATCH" -> "WATCH";
            default -> "HEALTHY";
        };
        Integer days = null;
        if (f.nextCheckDate() != null) {
            days = (int) ChronoUnit.DAYS.between(times.today(), LocalDate.parse(f.nextCheckDate()));
        }
        return new FieldDto(f, status, days);
    }

    public Query query(String uid) {
        return refs.fields(uid);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

package com.riceguard.api.diary;

import com.google.cloud.firestore.DocumentReference;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.PageResult;
import com.riceguard.api.common.Paging;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.field.Field;
import com.riceguard.api.field.FieldService;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DiaryService {
    public static final Set<String> TYPES = Set.of("CARE", "FERTILIZE", "SPRAY", "WATER", "WEED", "HARVEST", "SCAN");

    public record DiaryRequest(
            String fieldId,
            @NotBlank(message = "Nhập tiêu đề nhật ký") @Size(max = 100, message = "Tiêu đề tối đa 100 ký tự") String title,
            @Pattern(regexp = "^(CARE|FERTILIZE|SPRAY|WATER|WEED|HARVEST)$", message = "Loại hoạt động không hợp lệ") String type,
            @Size(max = 500, message = "Nội dung tối đa 500 ký tự") String content,
            LocalDate date,
            @Pattern(regexp = "^(SUNNY|CLOUDY|LIGHT_RAIN|HEAVY_RAIN|OTHER)?$", message = "Thời tiết không hợp lệ") String weather,
            @Size(max = 5, message = "Tối đa 5 ảnh") List<ImageRef> images) {}

    private final Fs fs;
    private final Refs refs;
    private final Paging paging;
    private final Times times;
    private final FieldService fields;
    private final MediaService media;

    public DiaryService(Fs fs, Refs refs, Paging paging, Times times, FieldService fields, MediaService media) {
        this.fs = fs;
        this.refs = refs;
        this.paging = paging;
        this.times = times;
        this.fields = fields;
        this.media = media;
    }

    public DiaryEntry create(String uid, DiaryRequest r) {
        Field f = fields.find(uid, r.fieldId());
        if (r.fieldId() != null && !r.fieldId().isBlank() && f == null) throw ApiException.notFound("Không tìm thấy ruộng");
        media.assertOwned(uid, r.images());
        long now = times.now();
        DocumentReference ref = refs.diaries(uid).document();
        DiaryEntry e = new DiaryEntry(ref.getId(), f == null ? null : f.id(), f == null ? null : f.name(),
                r.type() == null ? "CARE" : r.type(), r.title().trim(), blank(r.content()),
                (r.date() == null ? times.today() : r.date()).toString(), blank(r.weather()),
                r.images() == null ? List.of() : r.images(), null, now, now);
        fs.set(ref, e);
        return e;
    }

    /** Nhật ký tự sinh khi quét bệnh. */
    public DiaryEntry createFromScan(String uid, Field f, String scanId, String title, String content, ImageRef image) {
        long now = times.now();
        DocumentReference ref = refs.diaries(uid).document();
        DiaryEntry e = new DiaryEntry(ref.getId(), f.id(), f.name(), "SCAN", title, content, times.today().toString(), null,
                image == null ? List.of() : List.of(new ImageRef(image.url(), null)), scanId, now, now);
        fs.set(ref, e);
        return e;
    }

    public DiaryEntry get(String uid, String id) {
        return fs.require(refs.diaries(uid).document(id), DiaryEntry.class, "Không tìm thấy nhật ký");
    }

    /** days: 7 | 30 | 90 (null = tất cả). */
    public PageResult<DiaryEntry> list(String uid, String fieldId, Integer days, String cursor, int limit) {
        String sinceDate = days == null ? "" : times.today().minusDays(days - 1L).toString();
        return paging.page(refs.diaries(uid), cursor, limit, DiaryEntry.class,
                e -> (fieldId == null || fieldId.isBlank() || fieldId.equals(e.fieldId()))
                        && (sinceDate.isEmpty() || e.date().compareTo(sinceDate) >= 0));
    }

    public DiaryEntry update(String uid, String id, DiaryRequest r) {
        DiaryEntry old = get(uid, id);
        if ("SCAN".equals(old.type())) throw ApiException.badRequest("Không sửa được nhật ký tự tạo từ lần quét");
        Field f = fields.find(uid, r.fieldId());
        media.assertOwned(uid, r.images());
        Map<String, Object> m = new HashMap<>();
        m.put("fieldId", f == null ? null : f.id());
        m.put("fieldName", f == null ? null : f.name());
        m.put("type", r.type() == null ? old.type() : r.type());
        m.put("title", r.title().trim());
        m.put("content", blank(r.content()));
        m.put("date", r.date() == null ? old.date() : r.date().toString());
        m.put("weather", blank(r.weather()));
        List<ImageRef> images = r.images() == null ? List.of() : r.images();
        m.put("images", images.stream().map(fs::toMap).toList());
        m.put("updatedAt", times.now());
        Fs.await(refs.diaries(uid).document(id).update(m));
        // Xóa ảnh bị gỡ khỏi nhật ký
        if (old.images() != null) {
            Set<String> keep = new java.util.HashSet<>();
            images.forEach(i -> keep.add(i.publicId()));
            old.images().stream().filter(i -> i.publicId() != null && !keep.contains(i.publicId())).forEach(media::delete);
        }
        return get(uid, id);
    }

    public void delete(String uid, String id) {
        DiaryEntry e = get(uid, id);
        Fs.await(refs.diaries(uid).document(id).delete());
        media.deleteAll(e.images());
    }

    public void deleteByScan(String uid, String scanId) {
        var docs = Fs.await(refs.diaries(uid).whereEqualTo("scanId", scanId).get()).getDocuments();
        docs.forEach(d -> Fs.await(d.getReference().delete()));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

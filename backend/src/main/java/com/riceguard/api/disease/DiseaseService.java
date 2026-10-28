package com.riceguard.api.disease;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Slug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Danh mục bệnh lúa: nạp từ seed/diseases.json vào Firestore khi khởi động (chỉ ghi khi version mới hơn). */
@Service
public class DiseaseService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DiseaseService.class);

    private final Fs fs;
    private final Refs refs;
    private final ObjectMapper mapper;

    public DiseaseService(Fs fs, Refs refs, ObjectMapper mapper) {
        this.fs = fs;
        this.refs = refs;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seed();
    }

    @CacheEvict(value = "diseases", allEntries = true)
    public void seed() throws IOException {
        List<Map<String, Object>> seed;
        try (InputStream in = new ClassPathResource("seed/diseases.json").getInputStream()) {
            seed = mapper.readValue(in, new TypeReference<>() {});
        }
        Map<String, Long> existing = new HashMap<>();
        Set<String> edited = new java.util.HashSet<>();
        for (DocumentSnapshot d : Fs.await(refs.diseases().get()).getDocuments()) {
            Long v = d.getLong("version");
            existing.put(d.getId(), v == null ? 0L : v);
            if (Boolean.TRUE.equals(d.getBoolean("adminEdited"))) edited.add(d.getId());
        }
        WriteBatch batch = fs.db().batch();
        int n = 0;
        for (Map<String, Object> d : seed) {
            String id = (String) d.get("id");
            long version = ((Number) d.get("version")).longValue();
            if (edited.contains(id) || existing.getOrDefault(id, -1L) >= version) continue;
            Map<String, Object> data = new HashMap<>(d);
            data.remove("id");
            data.values().removeIf(java.util.Objects::isNull);
            // merge để không ghi đè ảnh minh họa do quản trị viên tải lên
            batch.set(refs.diseases().document(id), data, SetOptions.merge());
            n++;
        }
        if (n > 0) Fs.await(batch.commit());
        log.info("Danh mục bệnh: đã nạp/cập nhật {} bệnh", n);
    }

    @Cacheable("diseases")
    public List<Disease> all() {
        // Bệnh thường gặp lên đầu, rồi theo tên (so sánh theo bảng chữ cái tiếng Việt).
        java.text.Collator vi = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("vi"));
        return fs.query(refs.diseases(), Disease.class).stream()
                .sorted(Comparator.<Disease, Boolean>comparing(Disease::common).reversed().thenComparing(Disease::name, vi))
                .toList();
    }

    public List<Disease> visible() {
        return all().stream().filter(d -> !d.hidden()).toList();
    }

    public List<Disease> search(String part, String cause, Boolean common, String q) {
        String needle = Slug.fold(q);
        return all().stream()
                .filter(d -> !d.hidden())
                .filter(d -> part == null || part.isBlank() || d.part().equalsIgnoreCase(part))
                .filter(d -> cause == null || cause.isBlank() || d.cause().equalsIgnoreCase(cause))
                .filter(d -> common == null || d.common() == common)
                .filter(d -> needle.isBlank() || Slug.fold(d.name() + " " + d.description()).contains(needle))
                .toList();
    }

    public Disease require(String id) {
        return all().stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bệnh này"));
    }

    public Disease requireVisible(String id) {
        Disease disease = require(id);
        if (disease.hidden()) throw ApiException.notFound("Không tìm thấy bệnh này");
        return disease;
    }

    public Disease find(String id) {
        return all().stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
    }

    public List<Disease> related(Disease d, int limit) {
        return all().stream().filter(o -> !o.hidden() && !o.id().equals(d.id()) && (o.part().equals(d.part()) || o.cause().equals(d.cause())))
                .limit(limit).toList();
    }

    @CacheEvict(value = "diseases", allEntries = true)
    public void setImage(String id, String url, String publicId) {
        require(id);
        Fs.await(refs.diseases().document(id).update("imageUrl", url, "imagePublicId", publicId));
    }

    // ---- Bệnh đã lưu ----

    public Set<String> savedIds(String uid) {
        return Fs.await(refs.savedDiseases(uid).get()).getDocuments().stream()
                .map(DocumentSnapshot::getId).collect(java.util.stream.Collectors.toSet());
    }

    public void save(String uid, String id, long now) {
        requireVisible(id);
        Fs.await(refs.savedDiseases(uid).document(id).set(Map.of("savedAt", now)));
    }

    public void unsave(String uid, String id) {
        Fs.await(refs.savedDiseases(uid).document(id).delete());
    }

    public List<Disease> saved(String uid) {
        Set<String> ids = savedIds(uid);
        return all().stream().filter(d -> !d.hidden() && ids.contains(d.id())).toList();
    }
}

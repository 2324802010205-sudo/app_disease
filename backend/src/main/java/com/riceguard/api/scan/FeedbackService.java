package com.riceguard.api.scan;

import com.google.cloud.firestore.DocumentReference;
import com.riceguard.api.common.Fs;
import com.riceguard.api.common.Refs;
import com.riceguard.api.common.Times;
import com.riceguard.api.disease.DiseaseService;
import com.riceguard.api.media.ImageRef;
import com.riceguard.api.media.MediaService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {
    public record FeedbackRequest(
            @NotBlank @Pattern(regexp = "^(WRONG_RESULT|UNCLEAR_IMAGE|MISSING_INFO|NEED_ADVICE)$", message = "Lý do phản hồi không hợp lệ") String reason,
            @Size(max = 60) String suggestedDiseaseCode,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description,
            @Size(max = 5, message = "Tối đa 5 ảnh") List<ImageRef> attachments) {}

    private final Fs fs;
    private final Refs refs;
    private final Times times;
    private final MediaService media;
    private final DiseaseService diseases;

    public FeedbackService(Fs fs, Refs refs, Times times, MediaService media, DiseaseService diseases) {
        this.fs = fs;
        this.refs = refs;
        this.times = times;
        this.media = media;
        this.diseases = diseases;
    }

    public String submit(String uid, Scan scan, FeedbackRequest r) {
        media.assertOwned(uid, r.attachments());
        if (r.suggestedDiseaseCode() != null && !r.suggestedDiseaseCode().isBlank()) diseases.require(r.suggestedDiseaseCode());
        DocumentReference ref = refs.feedbacks().document();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("scanId", scan.id());
        data.put("scanDiseaseCode", scan.diseaseCode());
        data.put("scanConfidence", scan.confidence());
        data.put("engine", scan.engine());
        data.put("imageUrl", scan.imageUrl());
        data.put("reason", r.reason());
        data.put("suggestedDiseaseCode", r.suggestedDiseaseCode());
        data.put("description", r.description());
        data.put("attachments", r.attachments() == null ? List.of() : r.attachments().stream().map(fs::toMap).toList());
        data.put("status", "NEW");
        data.put("createdAt", times.now());
        data.values().removeIf(java.util.Objects::isNull);
        Fs.await(ref.set(data));
        return ref.getId();
    }
}

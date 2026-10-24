package com.riceguard.api.diagnosis;

import com.riceguard.api.config.AppProperties;
import com.riceguard.api.disease.Disease;
import com.riceguard.api.disease.DiseaseService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiagnosisService {
    /** status: DIAGNOSED | NEEDS_RETAKE. riskLevel: NORMAL | WATCH | HIGH | UNKNOWN. */
    public record Outcome(String status, String riskLevel, String diseaseCode, String diseaseName, String severity, boolean diseased) {}

    private final DiagnosisEngine engine;
    private final DiseaseService diseases;
    private final AppProperties props;

    public DiagnosisService(DiagnosisEngine engine, DiseaseService diseases, AppProperties props) {
        this.engine = engine;
        this.diseases = diseases;
        this.props = props;
    }

    public boolean available() {
        return engine.available();
    }

    public String engineName() {
        return engine.name();
    }

    public DiagnosisResult run(String imageUrl, String stage, String variety, String weatherSummary) {
        return engine.diagnose(new DiagnosisRequest(imageUrl, stage, variety, weatherSummary, diseases.all()));
    }

    /** Quy đổi kết quả thô của AI thành trạng thái và mức rủi ro lưu trong lượt quét. */
    public Outcome evaluate(DiagnosisResult r) {
        return evaluate(r, props.diagnosis().minConfidence(), diseases.all());
    }

    static Outcome evaluate(DiagnosisResult r, double minConfidence, List<Disease> catalog) {
        String code = r.diseaseCode() == null ? "unknown" : r.diseaseCode().trim();
        double confidence = Math.max(0, Math.min(1, r.confidence()));
        if (!r.riceLeaf() || confidence < minConfidence) {
            return new Outcome("NEEDS_RETAKE", "UNKNOWN", "unknown", "Chưa xác định", "NONE", false);
        }
        if ("healthy".equalsIgnoreCase(code)) {
            return new Outcome("DIAGNOSED", "NORMAL", "healthy", "Bình thường", "NONE", false);
        }
        Disease d = catalog.stream().filter(x -> x.id().equals(code)).findFirst().orElse(null);
        if (d == null) {
            return new Outcome("NEEDS_RETAKE", "UNKNOWN", "unknown", "Chưa xác định", "NONE", false);
        }
        String severity = r.severity() == null ? "LOW" : r.severity().toUpperCase();
        String risk = switch (severity) {
            case "HIGH", "MEDIUM" -> "HIGH";
            default -> "WATCH";
        };
        return new Outcome("DIAGNOSED", risk, d.id(), d.name(), "NONE".equals(severity) ? "LOW" : severity, true);
    }
}

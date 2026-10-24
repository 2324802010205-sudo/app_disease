package com.riceguard.api.diagnosis;

import java.util.List;

/**
 * Kết quả thô của engine AI.
 * diseaseCode: mã trong danh mục bệnh, hoặc "healthy" (lúa khỏe), hoặc "unknown" (không xác định).
 * severity: NONE | LOW | MEDIUM | HIGH.
 */
public record DiagnosisResult(
        boolean riceLeaf,
        String diseaseCode,
        double confidence,
        String severity,
        List<String> symptoms,
        String summary,
        List<Recommendation> recommendations,
        String retakeAdvice) {

    /** icon: SHIELD | LEAF | WATER | BUG | CALENDAR (gợi ý icon cho app). */
    public record Recommendation(String title, String description, String icon) {}
}

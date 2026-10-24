package com.riceguard.api.diagnosis;

import com.riceguard.api.disease.Disease;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisEvaluateTest {
    private static final List<Disease> CATALOG = List.of(
            new Disease("dao-on-la", 1, "Đạo ôn lá", null, "mô tả", "LEAF", "FUNGUS", true, List.of(), List.of(), List.of(), List.of(), null, null));

    private static DiagnosisResult result(boolean rice, String code, double conf, String severity) {
        return new DiagnosisResult(rice, code, conf, severity, List.of(), "tóm tắt", List.of(), "");
    }

    @Test
    void diseaseWithMediumSeverityIsHighRisk() {
        var o = DiagnosisService.evaluate(result(true, "dao-on-la", 0.87, "MEDIUM"), 0.5, CATALOG);
        assertEquals("DIAGNOSED", o.status());
        assertEquals("HIGH", o.riskLevel());
        assertEquals("Đạo ôn lá", o.diseaseName());
        assertTrue(o.diseased());
    }

    @Test
    void lowSeverityDiseaseNeedsWatching() {
        var o = DiagnosisService.evaluate(result(true, "dao-on-la", 0.9, "LOW"), 0.5, CATALOG);
        assertEquals("WATCH", o.riskLevel());
    }

    @Test
    void severityNoneOnDiseaseIsStillWatchAndPromotedToLow() {
        var o = DiagnosisService.evaluate(result(true, "dao-on-la", 0.9, "NONE"), 0.5, CATALOG);
        assertEquals("WATCH", o.riskLevel());
        assertEquals("LOW", o.severity());
    }

    @Test
    void healthyIsNormalAndNotCountedAsDiseased() {
        var o = DiagnosisService.evaluate(result(true, "healthy", 0.95, "NONE"), 0.5, CATALOG);
        assertEquals("NORMAL", o.riskLevel());
        assertFalse(o.diseased());
    }

    @Test
    void lowConfidenceAsksForRetake() {
        var o = DiagnosisService.evaluate(result(true, "dao-on-la", 0.3, "HIGH"), 0.5, CATALOG);
        assertEquals("NEEDS_RETAKE", o.status());
        assertEquals("UNKNOWN", o.riskLevel());
    }

    @Test
    void nonRiceImageAsksForRetake() {
        assertEquals("NEEDS_RETAKE", DiagnosisService.evaluate(result(false, "healthy", 0.99, "NONE"), 0.5, CATALOG).status());
    }

    @Test
    void codeOutsideCatalogIsNotTrusted() {
        var o = DiagnosisService.evaluate(result(true, "benh-la", 0.99, "HIGH"), 0.5, CATALOG);
        assertEquals("NEEDS_RETAKE", o.status());
    }

    @Test
    void outOfRangeConfidenceIsClamped() {
        assertEquals("NORMAL", DiagnosisService.evaluate(result(true, "healthy", 7.0, "NONE"), 0.5, CATALOG).riskLevel());
        assertEquals("NEEDS_RETAKE", DiagnosisService.evaluate(result(true, "healthy", -1.0, "NONE"), 0.5, CATALOG).status());
    }
}

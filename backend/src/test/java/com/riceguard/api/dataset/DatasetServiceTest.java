package com.riceguard.api.dataset;

import com.riceguard.api.scan.Scan;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatasetServiceTest {
    private static Scan scan(String status, String code) {
        return new Scan("s1", null, null, null, null, "u", "p", "t", status, "UNKNOWN", code, null, 0.9, null, List.of(), null, List.of(), null,
                null, null, null, null, "http", false, 1L);
    }

    @Test
    void labelFollowsDiagnosisResult() {
        assertEquals("dom-nau", DatasetService.labelOf(scan("DIAGNOSED", "dom-nau")));
        assertEquals("healthy", DatasetService.labelOf(scan("DIAGNOSED", "healthy")));
        assertEquals("unknown", DatasetService.labelOf(scan("NEEDS_RETAKE", "unknown")));
        assertEquals("pending", DatasetService.labelOf(scan("PENDING", null)));
    }

    @Test
    void countersMergeOppositeChanges() {
        DatasetCounters c = new DatasetCounters();
        c.image("dom-nau", 1);
        c.image("dom-nau", -1);
        assertFalse(c.toIncrements().containsKey("total"));   // cộng trừ triệt tiêu thì không ghi
        c.image("bac-la", 1);
        assertEquals(1, ((Map<?, ?>) c.toIncrements().get("byLabel")).size());
    }

    @Test
    void pendingReviewIsNotCounted() {
        DatasetCounters c = new DatasetCounters();
        c.review("dom-nau", "PENDING", null, 1);
        assertTrue(c.raw().isEmpty());
    }

    @Test
    void summaryComputesAccuracyAndConfusion() {
        Map<String, Object> data = Map.of("total", 10L,
                "byLabel", Map.of("dom-nau", 6L, "bac-la", 4L),
                "reviewed", Map.of("dom-nau", Map.of("confirmed", 3L, "corrected", 1L), "bac-la", Map.of("rejected", 1L)),
                "confusion", Map.of("dom-nau", Map.of("dom-nau-hep", 1L)));
        Map<String, String> names = new LinkedHashMap<>();
        names.put("dom-nau", "Đốm nâu");
        names.put("bac-la", "Bạc lá");
        names.put("dom-nau-hep", "Đốm nâu hẹp");
        DatasetService.Summary s = DatasetService.build(data, names);
        assertEquals(10, s.total());
        assertEquals(5, s.reviewed());
        assertEquals(0.75, s.accuracy());           // ảnh loại không tính vào độ chính xác
        assertEquals(3, s.folders().size());
        assertEquals(0, s.folders().get(2).total());     // thư mục chưa có ảnh vẫn hiện
        assertEquals("Đốm nâu hẹp", s.confusion().get(0).actualName());
    }

    @Test
    void emptySummaryHasNoAccuracy() {
        assertNull(DatasetService.build(Map.of(), Map.of("a", "A")).accuracy());
    }

    @Test
    void confidenceBands() {
        assertTrue(DatasetService.confidenceMatches("LOW", 0.59));
        assertTrue(DatasetService.confidenceMatches("MID", 0.6));
        assertTrue(DatasetService.confidenceMatches("HIGH", 0.85));
        assertFalse(DatasetService.confidenceMatches("HIGH", 0.84));
        assertTrue(DatasetService.confidenceMatches("", 0.1));
    }
}

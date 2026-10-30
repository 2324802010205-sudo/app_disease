package com.riceguard.api.diagnosis;

import com.riceguard.api.disease.Disease;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HttpDiagnosisEnrichTest {
    private static final Disease BLAST = new Disease("dao-on-la", 1, "Đạo ôn lá", null, "mô tả", "LEAF", "FUNGUS", true,
            List.of("Vết hình thoi"), List.of(), List.of("Dùng giống kháng", "Bón cân đối", "Vệ sinh ruộng"),
            List.of("Phun Tricyclazole"), null, null, false, false);

    private static DiagnosisResult raw(String code, List<String> symptoms, List<DiagnosisResult.Recommendation> recos) {
        return new DiagnosisResult(true, code, 0.8, "MEDIUM", symptoms, "tóm tắt", recos, null);
    }

    @Test
    void fillsSymptomsAndRecommendationsFromCatalog() {
        var out = HttpDiagnosisEngine.enrich(raw("dao-on-la", List.of(), List.of()), List.of(BLAST));
        assertEquals(List.of("Vết hình thoi"), out.symptoms());
        assertEquals(3, out.recommendations().size());   // 1 xử lý + tối đa 2 phòng ngừa
        assertEquals("LEAF", out.recommendations().get(0).icon());
        assertEquals("SHIELD", out.recommendations().get(1).icon());
        assertEquals("tóm tắt", out.summary());
    }

    @Test
    void keepsWhatTheAiServiceAlreadySent() {
        var recos = List.of(new DiagnosisResult.Recommendation("a", "b", "BUG"));
        var in = raw("dao-on-la", List.of("của AI"), recos);
        assertSame(in, HttpDiagnosisEngine.enrich(in, List.of(BLAST)));
    }

    @Test
    void leavesHealthyAndUnknownCodesAlone() {
        var healthy = raw("healthy", List.of(), List.of());
        var unknown = raw("unknown", List.of(), List.of());
        assertSame(healthy, HttpDiagnosisEngine.enrich(healthy, List.of(BLAST)));
        assertSame(unknown, HttpDiagnosisEngine.enrich(unknown, List.of(BLAST)));
    }
}

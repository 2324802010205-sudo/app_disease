package com.riceguard.api.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riceguard.api.diagnosis.YoloPostprocess.Detection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Bản Java của model/tests/test_postprocess.py: hai bên phải cho cùng kết quả. */
class YoloPostprocessTest {
    private static final Path MODEL_DIR = Path.of("../model");
    private static final Map<String, String> CLASS_MAP = OnnxDiagnosisEngine.loadClassMap(new ObjectMapper(), MODEL_DIR.resolve("class_map.json"));

    private static Detection d(String name, double conf, double area) {
        return new Detection(name, conf, area);
    }

    private static DiagnosisResult diagnose(Detection... dets) {
        return YoloPostprocess.toDiagnosis(List.of(dets), CLASS_MAP);
    }

    @Test
    void noDetectionAsksForRetake() {
        DiagnosisResult r = diagnose();
        assertThat(r.riceLeaf()).isFalse();
        assertThat(r.retakeAdvice()).isNotBlank();
    }

    @Test
    void healthyOnly() {
        DiagnosisResult r = diagnose(d("healthy", 0.9, 0.6));
        assertThat(r.riceLeaf()).isTrue();
        assertThat(r.diseaseCode()).isEqualTo("healthy");
        assertThat(r.severity()).isEqualTo("NONE");
    }

    @Test
    void diseaseWinsOverHealthy() {
        DiagnosisResult r = diagnose(d("healthy", 0.9, 0.6), d("Rice Blast", 0.4, 0.02));
        assertThat(r.diseaseCode()).isEqualTo("dao-on-la");
        assertThat(r.confidence()).isEqualTo(0.4);
    }

    @ParameterizedTest
    @CsvSource({"Brown spot,Rice Brownspot", "Leaf blast,Rice Blast", "Rice Bacterial Blight,bacterial leaf blight"})
    void duplicateClassesShareACode(String a, String b) {
        assertThat(diagnose(d(a, 0.8, 0.01)).diseaseCode()).isEqualTo(diagnose(d(b, 0.7, 0.01)).diseaseCode());
    }

    @Test
    void coverageSumsSameDiseaseAndSetsSeverity() {
        DiagnosisResult r = diagnose(d("Brown spot", 0.8, 0.12), d("Rice Brownspot", 0.6, 0.10));
        assertThat(r.diseaseCode()).isEqualTo("dom-nau");
        assertThat(r.severity()).isEqualTo("HIGH");
    }

    @Test
    void coverageCappedAtOne() {
        assertThat(diagnose(d("Brown spot", 0.8, 0.9), d("Brown spot", 0.7, 0.9)).severity()).isEqualTo("HIGH");
    }

    @Test
    void formerlyUnmappedClassesNowHaveLibraryEntries() {
        assertThat(diagnose(d("Leaf Scald", 0.9, 0.1)).diseaseCode()).isEqualTo("chay-la");
        assertThat(diagnose(d("Leaf smut", 0.9, 0.1)).diseaseCode()).isEqualTo("than-la");
        assertThat(diagnose(d("Narrow brown spot", 0.9, 0.1)).diseaseCode()).isEqualTo("dom-nau-hep");
    }

    @Test
    void classWithoutLibraryEntryIsUnknownWithRetakeAdvice() {
        Map<String, String> map = new HashMap<>();
        map.put("Leaf smut", null);
        DiagnosisResult r = YoloPostprocess.toDiagnosis(List.of(d("Leaf smut", 0.9, 0.1)), map);
        assertThat(r.riceLeaf()).isTrue();
        assertThat(r.diseaseCode()).isEqualTo("unknown");
        assertThat(r.retakeAdvice()).isNotBlank();
    }

    @Test
    void classMissingFromMapIsUnknown() {
        assertThat(diagnose(d("Totally new class", 0.9, 0.1)).diseaseCode()).isEqualTo("unknown");
    }

    @ParameterizedTest
    @CsvSource({"0,LOW", "0.049,LOW", "0.05,MEDIUM", "0.199,MEDIUM", "0.2,HIGH"})
    void severityThresholds(double coverage, String expected) {
        assertThat(YoloPostprocess.severityOf(coverage)).isEqualTo(expected);
    }

    @Test
    void everyModelClassIsMapped() {
        assertThat(CLASS_MAP).hasSize(11);
    }

    @Test
    void everyMappedCodeExistsInDiseaseLibrary() throws Exception {
        var seed = new ObjectMapper().readTree(Files.readAllBytes(Path.of("src/main/resources/seed/diseases.json")));
        Set<String> known = new java.util.HashSet<>(Set.of("healthy"));
        seed.forEach(n -> known.add(n.get("id").asText()));
        Set<String> mapped = CLASS_MAP.values().stream().filter(c -> c != null && !c.isEmpty()).collect(Collectors.toSet());
        assertThat(known).containsAll(mapped);
    }

    @Test
    void mergeAveragesEachCodeOverRunsAndCountsMissesAsZero() {
        var runs = List.of(List.of(d("Leaf Scald", 0.8, 0.1), d("Rice Bacterial Blight", 0.3, 0.2)),
                List.of(d("Rice Bacterial Blight", 0.5, 0.2)));
        Map<String, Detection> merged = YoloPostprocess.mergeRuns(runs, CLASS_MAP).stream()
                .collect(Collectors.toMap(x -> CLASS_MAP.get(x.name()), x -> x));
        assertThat(merged.get("chay-la").confidence()).isCloseTo(0.4, within(1e-9));
        assertThat(merged.get("bac-la").confidence()).isCloseTo(0.4, within(1e-9));
        assertThat(merged.get("bac-la").area()).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void mergeJoinsDuplicateClassesByCodeAndKeepsBestName() {
        var merged = YoloPostprocess.mergeRuns(List.of(List.of(d("Rice Blast", 0.3, 0.05), d("Leaf blast", 0.5, 0.05))), CLASS_MAP);
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).name()).isEqualTo("Leaf blast");
        assertThat(merged.get(0).confidence()).isEqualTo(0.5);
        assertThat(merged.get(0).area()).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void mergeWithNoRunsIsEmpty() {
        assertThat(YoloPostprocess.mergeRuns(List.of(), CLASS_MAP)).isEmpty();
    }

    @Test
    void mergedResultIsSteadierThanASingleRun() {
        var merged = YoloPostprocess.mergeRuns(List.of(List.of(d("Leaf Scald", 0.54, 0.2)), List.of(d("Leaf Scald", 0.48, 0.2))), CLASS_MAP);
        assertThat(YoloPostprocess.toDiagnosis(merged, CLASS_MAP).diseaseCode()).isEqualTo("chay-la");
    }

    @Test
    void lowConfidenceResultCarriesACaution() {
        assertThat(diagnose(d("Rice Blast", 0.4, 0.02)).summary()).contains("đối chiếu");
        assertThat(diagnose(d("Rice Blast", 0.8, 0.02)).summary()).doesNotContain("đối chiếu");
    }
}

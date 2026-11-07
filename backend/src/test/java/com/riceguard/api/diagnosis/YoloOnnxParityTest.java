package com.riceguard.api.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riceguard.api.diagnosis.YoloPostprocess.Detection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đối chiếu bản Java (ONNX Runtime) với dịch vụ Python (ultralytics) trên các ảnh mẫu ở model/test-images.
 * Kết quả chuẩn nằm ở src/test/resources/yolo-parity.json (sinh bằng model/service chạy trên chính các ảnh đó).
 * Java dùng cùng letterbox/TTA/NMS với ultralytics nên lệch chỉ còn do khác biệt làm tròn khi thu phóng ảnh.
 * Bỏ qua nếu chưa xuất model/weights/best.onnx (chạy model/export-onnx.sh).
 */
class YoloOnnxParityTest {
    private static final Path ONNX = Path.of("../model/weights/best.onnx");
    private static final Path IMAGES = Path.of("../model/test-images");

    @Test
    void javaMatchesPythonOnSampleImages() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ONNX), "chưa có best.onnx");
        ObjectMapper json = new ObjectMapper();
        JsonNode expected = json.readTree(getClass().getResourceAsStream("/yolo-parity.json"));
        Map<String, String> classMap = OnnxDiagnosisEngine.loadClassMap(json, Path.of("../model/class_map.json"));
        int[] sizes = new int[expected.get("imgsz").size()];
        for (int i = 0; i < sizes.length; i++) sizes[i] = expected.get("imgsz").get(i).asInt();
        boolean augment = expected.get("augment").asBoolean();
        float runConf = (float) expected.get("runConf").asDouble();
        double conf = expected.get("conf").asDouble();

        int images = 0, sameCode = 0;
        double worstConf = 0, sumConf = 0;
        int confCount = 0;
        List<String> report = new ArrayList<>();
        try (YoloOnnxDetector detector = new YoloOnnxDetector(ONNX)) {
            Iterator<Map.Entry<String, JsonNode>> it = expected.get("images").fields();
            while (it.hasNext()) {
                var e = it.next();
                var img = ImageIO.read(IMAGES.resolve(e.getKey()).toFile());
                List<List<Detection>> runs = new ArrayList<>();
                for (int s : sizes) runs.add(detector.detect(img, s, augment, runConf));
                List<Detection> merged = YoloPostprocess.mergeRuns(runs, classMap).stream().filter(d -> d.confidence() >= conf).toList();
                DiagnosisResult got = YoloPostprocess.toDiagnosis(merged, classMap);

                JsonNode want = e.getValue().get("diagnosis");
                images++;
                boolean same = got.diseaseCode().equals(want.get("diseaseCode").asText());
                if (same) sameCode++;
                double dc = Math.abs(got.confidence() - want.get("confidence").asDouble());
                worstConf = Math.max(worstConf, dc);
                sumConf += dc;
                confCount++;
                report.add(String.format("%-44s py=%-10s %.4f  java=%-10s %.4f  Δ=%.4f runs(py/java)=%s/%s", e.getKey(),
                        want.get("diseaseCode").asText(), want.get("confidence").asDouble(), got.diseaseCode(), got.confidence(), dc,
                        e.getValue().get("runs").get(0).size() + "+" + e.getValue().get("runs").get(1).size(),
                        runs.get(0).size() + "+" + runs.get(1).size()));
            }
        }
        report.forEach(System.out::println);
        System.out.printf("PARITY images=%d sameCode=%d meanΔconf=%.4f worstΔconf=%.4f%n", images, sameCode, sumConf / confCount, worstConf);
        assertThat(images).isEqualTo(28);
        assertThat(sameCode).as("số ảnh Java và Python cùng kết luận bệnh").isEqualTo(images);
        assertThat(sumConf / confCount).as("sai lệch trung bình độ tin cậy").isLessThan(0.01);
        assertThat(worstConf).as("sai lệch lớn nhất độ tin cậy").isLessThan(0.03);
    }
}

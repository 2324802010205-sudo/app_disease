package com.riceguard.api.diagnosis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chuyển phát hiện của YOLO thành {@link DiagnosisResult}. Bản Java của {@code model/service/postprocess.py};
 * hai bên phải giữ cùng quy tắc (test ở YoloPostprocessTest và model/tests/test_postprocess.py).
 */
final class YoloPostprocess {
    static final String HEALTHY = "healthy";
    static final String UNKNOWN = "unknown";

    /** Tỉ lệ diện tích ảnh bị các vùng bệnh che phủ -> mức độ nặng (heuristic, xem model/README.md). */
    static final double SEVERITY_MEDIUM_FROM = 0.05;
    static final double SEVERITY_HIGH_FROM = 0.20;

    /** Dưới mức này kết quả vẫn được báo nhưng kèm lời nhắc đối chiếu thêm. */
    static final double CONFIDENT_FROM = 0.6;

    private YoloPostprocess() {}

    /**
     * name: tên lớp trong best.pt; confidence: 0..1; area: diện tích hộp / diện tích ảnh, 0..1.
     */
    record Detection(String name, double confidence, double area) {}

    private static String codeOf(Map<String, String> classMap, String name) {
        String code = classMap.get(name);
        return code == null || code.isEmpty() ? UNKNOWN : code;
    }

    /**
     * Gộp phát hiện của nhiều lần suy luận (kích thước ảnh khác nhau, có TTA). Mỗi mã bệnh có độ tin cậy = trung bình
     * các lần chạy của (độ tin cậy cao nhất trong lần đó), lần không thấy tính 0; diện tích = trung bình tổng diện tích
     * các hộp cùng mã. Một lần chạy riêng lẻ rất dao động nên gộp lại làm kết quả ổn định hơn.
     */
    static List<Detection> mergeRuns(List<List<Detection>> runs, Map<String, String> classMap) {
        if (runs.isEmpty()) return List.of();
        int n = runs.size();
        Map<String, Double> conf = new LinkedHashMap<>();
        Map<String, Double> area = new LinkedHashMap<>();
        Map<String, double[]> bestScore = new LinkedHashMap<>();   // mã -> {conf cao nhất}
        Map<String, String> bestName = new LinkedHashMap<>();      // mã -> tên lớp tương ứng
        for (List<Detection> dets : runs) {
            Map<String, Double> best = new LinkedHashMap<>();
            Map<String, Double> cover = new LinkedHashMap<>();
            for (Detection d : dets) {
                String code = codeOf(classMap, d.name());
                if (d.confidence() > best.getOrDefault(code, 0.0)) best.put(code, d.confidence());
                cover.merge(code, d.area(), Double::sum);
                double[] top = bestScore.get(code);
                if (top == null ? d.confidence() > 0.0 : d.confidence() > top[0]) {
                    bestScore.put(code, new double[]{d.confidence()});
                    bestName.put(code, d.name());
                }
            }
            for (Map.Entry<String, Double> e : best.entrySet()) {
                conf.merge(e.getKey(), e.getValue() / n, Double::sum);
                area.merge(e.getKey(), Math.min(1.0, cover.get(e.getKey())) / n, Double::sum);
            }
        }
        List<Detection> out = new ArrayList<>();
        for (String code : conf.keySet()) out.add(new Detection(bestName.get(code), conf.get(code), area.get(code)));
        return out;
    }

    static String severityOf(double coverage) {
        if (coverage >= SEVERITY_HIGH_FROM) return "HIGH";
        if (coverage >= SEVERITY_MEDIUM_FROM) return "MEDIUM";
        return "LOW";
    }

    private static DiagnosisResult result(boolean riceLeaf, String code, double confidence, String severity,
                                          String summary, String retake) {
        double c = Math.max(0.0, Math.min(1.0, confidence));
        // symptoms / recommendations để trống: backend điền từ thư viện bệnh (HttpDiagnosisEngine.enrich)
        return new DiagnosisResult(riceLeaf, code, Math.round(c * 10000.0) / 10000.0, severity, List.of(), summary,
                List.of(), retake);
    }

    /**
     * Quy tắc:
     * - Không có phát hiện nào: riceLeaf=false (backend yêu cầu chụp lại).
     * - Có vùng bệnh: chọn bệnh có độ tin cậy cao nhất; lá khỏe chỉ thắng khi không có vùng bệnh nào
     *   (ưu tiên báo bệnh khi nghi ngờ, vì bỏ sót bệnh thiệt hại hơn báo nhầm).
     * - Lớp bệnh chưa ánh xạ: diseaseCode="unknown", backend xử lý như chưa xác định.
     */
    static DiagnosisResult toDiagnosis(List<Detection> detections, Map<String, String> classMap) {
        if (detections.isEmpty()) {
            return result(false, UNKNOWN, 0.0, "NONE", "AI chưa nhận ra dấu hiệu bệnh nào đủ rõ trong ảnh này.",
                    "Hãy chụp cận vùng lá có vết bệnh, đủ sáng, vết bệnh nằm giữa khung hình và không rung tay; có thể chụp thêm một vài góc.");
        }
        List<Detection> diseased = detections.stream().filter(d -> !HEALTHY.equals(codeOf(classMap, d.name()))).toList();
        if (diseased.isEmpty()) {
            Detection best = maxByConfidence(detections);
            return result(true, HEALTHY, best.confidence(), "NONE", "Lá lúa trông khỏe mạnh, chưa thấy dấu hiệu bệnh.", null);
        }
        Detection top = maxByConfidence(diseased);
        String code = codeOf(classMap, top.name());
        double coverage = Math.min(1.0, diseased.stream().filter(d -> codeOf(classMap, d.name()).equals(code))
                .mapToDouble(Detection::area).sum());
        if (UNKNOWN.equals(code)) {
            return result(true, UNKNOWN, top.confidence(), "NONE",
                    "Mô hình phát hiện dấu hiệu \"" + top.name() + "\" nhưng thư viện bệnh chưa có bệnh này.",
                    "Hãy nhờ cán bộ bảo vệ thực vật xác nhận, hoặc chụp lại cận lá bệnh để thử lại.");
        }
        // Math.rint làm tròn về số chẵn như round() của Python
        String summary = "Phát hiện vùng nghi bệnh, che khoảng " + (long) Math.rint(coverage * 100) + "% khung ảnh.";
        if (top.confidence() < CONFIDENT_FROM) {
            summary += " Độ tin cậy chưa cao, nên đối chiếu với thư viện bệnh hoặc hỏi cán bộ bảo vệ thực vật.";
        }
        return result(true, code, top.confidence(), severityOf(coverage), summary, null);
    }

    /** Phần tử đầu tiên có độ tin cậy cao nhất (giống max() của Python khi bằng nhau). */
    private static Detection maxByConfidence(List<Detection> list) {
        Detection best = list.get(0);
        for (Detection d : list) if (d.confidence() > best.confidence()) best = d;
        return best;
    }
}

package com.riceguard.api.diagnosis;

import ai.onnxruntime.OrtException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riceguard.api.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Chạy mô hình best.pt ngay trong backend (ONNX Runtime), thay cho dịch vụ Python ở {@link HttpDiagnosisEngine}.
 * Bật bằng DIAGNOSIS_PROVIDER=onnx; trọng số xuất sang ONNX bằng model/export-onnx.sh.
 * Mỗi ảnh chạy ở nhiều kích thước (mặc định 640 và 800, có TTA) rồi gộp, đúng như dịch vụ Python.
 */
@Component
@ConditionalOnProperty(name = "riceguard.diagnosis.provider", havingValue = "onnx")
public class OnnxDiagnosisEngine implements DiagnosisEngine {
    private static final Logger log = LoggerFactory.getLogger(OnnxDiagnosisEngine.class);
    private static final int MAX_IMAGE_BYTES = 12 * 1024 * 1024;

    private final Path modelPath;
    private final Map<String, String> classMap;
    private final int[] imgsz;
    private final boolean augment;
    private final float runConf;
    private final double conf;
    private final Duration timeout;
    private final HttpClient http;
    // ONNX Runtime an toàn khi gọi song song nhưng mỗi lần chạy ngốn nhiều CPU và RAM; xếp hàng như dịch vụ Python
    private final ReentrantLock lock = new ReentrantLock(true);
    private volatile YoloOnnxDetector detector;

    public OnnxDiagnosisEngine(AppProperties props,
                               ObjectMapper json,
                               @Value("${riceguard.diagnosis.onnx.model:../model/weights/best.onnx}") String model,
                               @Value("${riceguard.diagnosis.onnx.class-map:../model/class_map.json}") String classMapFile,
                               @Value("${riceguard.diagnosis.onnx.imgsz:640,800}") String imgsz,
                               @Value("${riceguard.diagnosis.onnx.augment:true}") boolean augment,
                               @Value("${riceguard.diagnosis.onnx.run-conf:0.05}") float runConf,
                               @Value("${riceguard.diagnosis.onnx.conf:0.15}") double conf) {
        this.modelPath = Path.of(model);
        this.classMap = loadClassMap(json, Path.of(classMapFile));
        this.imgsz = parseSizes(imgsz);
        this.augment = augment;
        this.runConf = runConf;
        this.conf = conf;
        this.timeout = Duration.ofSeconds(Math.max(5, props.diagnosis().timeoutSeconds()));
        // Chặn chuyển hướng: URL ảnh đến từ kho ảnh của chính hệ thống, không có lý do đi qua nơi khác
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        if (!Files.isRegularFile(modelPath)) {
            log.warn("Chưa có {} - chạy model/export-onnx.sh để xuất từ best.pt", modelPath.toAbsolutePath());
        }
    }

    @Override
    public String name() {
        return "onnx";
    }

    @Override
    public boolean available() {
        return Files.isRegularFile(modelPath);
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisRequest r) throws DiagnosisException {
        BufferedImage image = fetchImage(r.imageUrl());
        List<YoloPostprocess.Detection> detections;
        try {
            detections = detect(image);
        } catch (OrtException | RuntimeException e) {
            throw new DiagnosisException("Mô hình ONNX lỗi: " + e.getMessage(), e);
        }
        return HttpDiagnosisEngine.enrich(YoloPostprocess.toDiagnosis(detections, classMap), r.catalog());
    }

    /** Chạy qua mọi kích thước rồi gộp, giữ phát hiện đủ ngưỡng sau khi gộp. */
    List<YoloPostprocess.Detection> detect(BufferedImage image) throws OrtException {
        lock.lock();
        try {
            YoloOnnxDetector d = detector();
            List<List<YoloPostprocess.Detection>> runs = new ArrayList<>();
            for (int size : imgsz) runs.add(d.detect(image, size, augment, runConf));
            return YoloPostprocess.mergeRuns(runs, classMap).stream().filter(x -> x.confidence() >= conf).toList();
        } finally {
            lock.unlock();
        }
    }

    private YoloOnnxDetector detector() throws OrtException {
        YoloOnnxDetector d = detector;
        if (d == null) {
            if (!available()) throw new DiagnosisException("Không thấy file mô hình ONNX: " + modelPath.toAbsolutePath());
            d = new YoloOnnxDetector(modelPath);
            log.info("Đã nạp {}, lớp: {}, imgsz: {}, augment: {}", modelPath, d.names(), imgsz, augment);
            detector = d;
        }
        return d;
    }

    @PreDestroy
    void close() throws OrtException {
        if (detector != null) detector.close();
    }

    // ------------------------------------------------------------------ nạp ảnh / cấu hình

    private BufferedImage fetchImage(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new DiagnosisException("imageUrl không hợp lệ");
        }
        if (uri.getHost() == null || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
            throw new DiagnosisException("imageUrl phải là http(s)");
        }
        try {
            HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = res.body()) {
                if (res.statusCode() != 200) throw new DiagnosisException("Tải ảnh thất bại (HTTP " + res.statusCode() + ")");
                byte[] data = in.readNBytes(MAX_IMAGE_BYTES + 1);
                if (data.length > MAX_IMAGE_BYTES) throw new DiagnosisException("Ảnh quá lớn");
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
                if (img == null) throw new DiagnosisException("Không đọc được ảnh");
                return img;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiagnosisException("Tải ảnh bị gián đoạn", e);
        } catch (IOException e) {
            throw new DiagnosisException("Không tải được ảnh: " + e.getMessage(), e);
        }
    }

    static Map<String, String> loadClassMap(ObjectMapper json, Path file) {
        try {
            JsonNode root = json.readTree(Files.readAllBytes(file));
            Map<String, String> map = new HashMap<>();
            root.fields().forEachRemaining(e -> {
                if (!e.getKey().startsWith("_")) map.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText());
            });
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được class_map: " + file.toAbsolutePath(), e);
        }
    }

    static int[] parseSizes(String list) {
        int[] sizes = java.util.Arrays.stream(list.replace(" ", "").split(",")).filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
        if (sizes.length == 0) throw new IllegalStateException("DIAGNOSIS_ONNX_IMGSZ rỗng");
        return sizes;
    }
}

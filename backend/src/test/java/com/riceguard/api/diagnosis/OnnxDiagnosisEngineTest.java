package com.riceguard.api.diagnosis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.disease.Disease;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Chạy trọn engine ONNX: tải ảnh qua HTTP, suy luận, quy đổi và bổ sung triệu chứng từ thư viện bệnh. */
class OnnxDiagnosisEngineTest {
    private static final Path ONNX = Path.of("../model/weights/best.onnx");
    private static final Path IMAGE = Path.of("../model/test-images/bac-la/mendeley-bac-la-01.jpg");

    private static OnnxDiagnosisEngine engine(String model) {
        AppProperties props = mock(AppProperties.class);
        when(props.diagnosis()).thenReturn(new AppProperties.Diagnosis("onnx", 0.35, 30, "", "", ""));
        return new OnnxDiagnosisEngine(props, new ObjectMapper(), model, "../model/class_map.json", "640,800", true, 0.05f, 0.15);
    }

    private static Disease bacLa() {
        return new Disease("bac-la", 1, "Bạc lá", null, "mô tả", "LEAF", "BACTERIA", true, List.of("Vết úa dọc mép lá"), List.of(),
                List.of("Dùng giống kháng"), List.of("Ngưng bón đạm"), null, null, false, false);
    }

    @Test
    void diagnosesAnImageServedOverHttp() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ONNX), "chưa có best.onnx");
        byte[] jpg = Files.readAllBytes(IMAGE);
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/leaf.jpg", ex -> {
            ex.sendResponseHeaders(200, jpg.length);
            ex.getResponseBody().write(jpg);
            ex.close();
        });
        server.start();
        try {
            OnnxDiagnosisEngine e = engine(ONNX.toString());
            assertThat(e.available()).isTrue();
            DiagnosisResult r = e.diagnose(new DiagnosisRequest("http://127.0.0.1:" + server.getAddress().getPort() + "/leaf.jpg",
                    null, null, null, List.of(bacLa())));
            assertThat(r.riceLeaf()).isTrue();
            assertThat(r.diseaseCode()).isEqualTo("bac-la");
            assertThat(r.confidence()).isBetween(0.8, 0.95);
            assertThat(r.severity()).isIn("MEDIUM", "HIGH");
            assertThat(r.symptoms()).containsExactly("Vết úa dọc mép lá");   // lấy từ thư viện bệnh, không phải từ mô hình
            e.close();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsUnavailableWhenModelFileIsMissing() {
        OnnxDiagnosisEngine e = engine("does-not-exist.onnx");
        assertThat(e.available()).isFalse();
        assertThatThrownBy(() -> e.detect(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)))
                .isInstanceOf(DiagnosisException.class).hasMessageContaining("does-not-exist.onnx");
    }

    @Test
    void rejectsNonHttpImageUrls() {
        OnnxDiagnosisEngine e = engine("does-not-exist.onnx");
        assertThatThrownBy(() -> e.diagnose(new DiagnosisRequest("file:///etc/passwd", null, null, null, List.of())))
                .isInstanceOf(DiagnosisException.class);
    }

    private ApplicationContextRunner context(String provider) {
        AppProperties props = mock(AppProperties.class);
        when(props.diagnosis()).thenReturn(new AppProperties.Diagnosis(provider, 0.35, 30, "", "", ""));
        return new ApplicationContextRunner()
                .withBean(AppProperties.class, () -> props)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(OnnxDiagnosisEngine.class)
                .withPropertyValues("riceguard.diagnosis.provider=" + provider, "riceguard.diagnosis.onnx.imgsz=640");
    }

    @Test
    void springUsesTheOnnxEngineOnlyWhenProviderIsOnnx() {
        context("onnx").run(ctx -> {
            assertThat(ctx).hasSingleBean(DiagnosisEngine.class);
            assertThat(ctx.getBean(DiagnosisEngine.class).name()).isEqualTo("onnx");
        });
        context("http").run(ctx -> assertThat(ctx).doesNotHaveBean(OnnxDiagnosisEngine.class));
        context("none").run(ctx -> assertThat(ctx).doesNotHaveBean(OnnxDiagnosisEngine.class));
    }
}

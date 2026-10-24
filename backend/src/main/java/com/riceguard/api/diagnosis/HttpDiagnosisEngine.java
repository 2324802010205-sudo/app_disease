package com.riceguard.api.diagnosis;

import com.riceguard.api.config.AppProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Gọi mô hình AI riêng của nhóm (ví dụ FastAPI + TensorFlow/PyTorch).
 * POST {http-url} với JSON {imageUrl, stage, variety, weather} và trả JSON đúng dạng {@link DiagnosisResult}.
 */
@Component
@ConditionalOnProperty(name = "riceguard.diagnosis.provider", havingValue = "http")
public class HttpDiagnosisEngine implements DiagnosisEngine {
    private final RestClient http;
    private final AppProperties.Diagnosis cfg;

    public HttpDiagnosisEngine(AppProperties props) {
        this.cfg = props.diagnosis();
        if (cfg.httpUrl() == null || cfg.httpUrl().isBlank()) throw new IllegalStateException("Thiếu DIAGNOSIS_HTTP_URL");
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
        f.setReadTimeout(Duration.ofSeconds(cfg.timeoutSeconds()));
        // Bộ đệm để gửi Content-Length thay vì chunked: tương thích với mọi máy chủ/proxy.
        this.http = RestClient.builder().requestFactory(new org.springframework.http.client.BufferingClientHttpRequestFactory(f)).build();
    }

    @Override
    public String name() {
        return "http";
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisRequest r) {
        try {
            RestClient.RequestBodySpec spec = http.post().uri(cfg.httpUrl()).contentType(MediaType.APPLICATION_JSON);
            if (cfg.httpToken() != null && !cfg.httpToken().isBlank()) spec = spec.header("Authorization", "Bearer " + cfg.httpToken());
            DiagnosisResult res = spec.body(Map.of("imageUrl", r.imageUrl(), "stage", String.valueOf(r.stage()),
                    "variety", String.valueOf(r.variety()), "weather", String.valueOf(r.weatherSummary()))).retrieve().body(DiagnosisResult.class);
            if (res == null) throw new DiagnosisException("Dịch vụ AI trả về rỗng");
            return res;
        } catch (RestClientException e) {
            throw new DiagnosisException("Không gọi được dịch vụ AI: " + e.getMessage(), e);
        }
    }
}

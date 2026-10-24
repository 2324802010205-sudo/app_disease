package com.riceguard.api.diagnosis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dùng khi chưa cấu hình AI: lượt quét vẫn được lưu (trạng thái PENDING) để chẩn đoán sau. */
@Component
@ConditionalOnProperty(name = "riceguard.diagnosis.provider", havingValue = "none", matchIfMissing = true)
public class NoopDiagnosisEngine implements DiagnosisEngine {
    @Override
    public String name() {
        return "none";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public DiagnosisResult diagnose(DiagnosisRequest request) {
        throw new DiagnosisException("Chưa cấu hình dịch vụ chẩn đoán AI");
    }
}

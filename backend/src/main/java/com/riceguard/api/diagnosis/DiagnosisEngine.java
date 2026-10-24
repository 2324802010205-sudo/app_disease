package com.riceguard.api.diagnosis;

public interface DiagnosisEngine {
    /** Tên engine, lưu cùng kết quả quét để truy vết. */
    String name();

    /** Có thể gọi được không (đã cấu hình). */
    default boolean available() {
        return true;
    }

    DiagnosisResult diagnose(DiagnosisRequest request) throws DiagnosisException;
}

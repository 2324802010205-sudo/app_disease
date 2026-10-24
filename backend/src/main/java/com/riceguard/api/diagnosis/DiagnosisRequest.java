package com.riceguard.api.diagnosis;

import com.riceguard.api.disease.Disease;

import java.util.List;

/** imageUrl: URL ảnh đã thu nhỏ trên kho ảnh, engine tự tải về hoặc để dịch vụ AI tải. */
public record DiagnosisRequest(String imageUrl, String stage, String variety, String weatherSummary, List<Disease> catalog) {}

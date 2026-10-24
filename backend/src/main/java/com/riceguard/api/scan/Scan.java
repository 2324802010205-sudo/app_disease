package com.riceguard.api.scan;

import com.riceguard.api.diagnosis.DiagnosisResult;
import com.riceguard.api.weather.WeatherDto;

import java.util.List;

public record Scan(
        String id,
        String fieldId,
        String fieldName,
        String province,
        String district,
        String imageUrl,
        String imagePublicId,
        String thumbUrl,
        String status,          // DIAGNOSED | NEEDS_RETAKE | PENDING
        String riskLevel,       // NORMAL | WATCH | HIGH | UNKNOWN
        String diseaseCode,
        String diseaseName,
        double confidence,
        String severity,
        List<String> symptoms,
        String summary,
        List<DiagnosisResult.Recommendation> recommendations,
        String retakeAdvice,
        WeatherDto.Snapshot weather,
        String stage,
        String variety,
        String note,
        String engine,
        boolean countedInArea,
        long createdAt) {}

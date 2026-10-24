package com.riceguard.api.field;

public record Field(
        String id,
        String name,
        String plot,
        String province,
        String district,
        String address,
        Double lat,
        Double lng,
        double areaHa,
        String variety,
        String stage,
        String plantingDate,
        String note,
        String photoUrl,
        String photoPublicId,
        String nextCheckDate,
        String lastScanId,
        Long lastScanAt,
        String lastRisk,
        long createdAt,
        long updatedAt) {}

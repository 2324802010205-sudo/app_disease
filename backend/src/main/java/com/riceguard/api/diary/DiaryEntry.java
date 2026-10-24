package com.riceguard.api.diary;

import com.riceguard.api.media.ImageRef;

import java.util.List;

public record DiaryEntry(
        String id,
        String fieldId,
        String fieldName,
        String type,        // CARE | FERTILIZE | SPRAY | WATER | WEED | HARVEST | SCAN
        String title,
        String content,
        String date,        // yyyy-MM-dd
        String weather,     // SUNNY | CLOUDY | LIGHT_RAIN | HEAVY_RAIN | OTHER
        List<ImageRef> images,
        String scanId,
        long createdAt,
        long updatedAt) {}

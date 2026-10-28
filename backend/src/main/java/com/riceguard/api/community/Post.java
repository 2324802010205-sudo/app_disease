package com.riceguard.api.community;

import com.riceguard.api.media.ImageRef;

import java.util.List;

public record Post(
        String id,
        String authorId,
        String province,
        String topic,   // PEST | EXPERIENCE | VARIETY | WEATHER | WATER
        String title,
        String content,
        List<ImageRef> images,
        long likeCount,
        long commentCount,
        long createdAt,
        long updatedAt,
        boolean hidden) {}

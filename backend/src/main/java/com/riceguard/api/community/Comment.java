package com.riceguard.api.community;

import java.util.List;

public record Comment(
        String id,
        String postId,
        String parentId,
        String authorId,
        String text,
        long likeCount,
        List<String> likedBy,
        long createdAt) {}

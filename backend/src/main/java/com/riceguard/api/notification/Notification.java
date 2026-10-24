package com.riceguard.api.notification;

public record Notification(
        String id,
        String kind,       // ALERT | REMINDER | COMMUNITY | TIP | WEATHER | UPDATE
        String title,
        String body,
        String refType,    // scan | field | post | disease | weather
        String refId,
        boolean read,
        long createdAt) {}

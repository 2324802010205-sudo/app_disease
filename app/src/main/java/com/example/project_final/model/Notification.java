package com.example.project_final.model;

/** kind: ALERT | REMINDER | COMMUNITY | TIP | WEATHER | UPDATE. refType: scan | field | post | disease | weather. */
public class Notification {
    public String id;
    public String kind;
    public String title;
    public String body;
    public String refType;
    public String refId;
    public boolean read;
    public long createdAt;
}

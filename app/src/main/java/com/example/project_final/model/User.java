package com.example.project_final.model;

public class User {
    public String id;
    public String email;
    public String fullName;
    public String phone;
    public String province;
    public String district;
    public String role;
    public String bio;
    public String avatarUrl;
    public boolean verifiedExpert;
    public boolean admin;
    public Settings settings;
    public Stats stats;
    public long createdAt;

    public static class Settings {
        public String theme = "SYSTEM";
        public String language = "vi";
        public boolean notifyAlert = true;
        public boolean notifyReminder = true;
        public boolean notifyCommunity = true;
    }

    public static class Stats {
        public long scans;
        public long fields;
        public long unreadAlerts;
    }

    public Settings settingsOrDefault() {
        return settings == null ? new Settings() : settings;
    }

    /** Tên cuối dùng khi chào hỏi: "Nguyễn Văn Vinh" -> "Vinh". */
    public String firstName() {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        String[] parts = fullName.trim().split("\\s+");
        return parts[parts.length - 1];
    }
}

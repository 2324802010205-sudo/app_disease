package com.riceguard.api.user;

import java.util.List;

public record UserProfile(
        String id,
        String email,
        String fullName,
        String phone,
        String province,
        String district,
        String role,
        String bio,
        String avatarUrl,
        String avatarPublicId,
        boolean verifiedExpert,
        Settings settings,
        List<String> fcmTokens,
        long createdAt,
        long updatedAt,
        boolean locked,
        String verifiedBy,
        Long verifiedAt,
        String lockedBy,
        Long lockedAt) {

    public record Settings(String theme, String language, boolean notifyAlert, boolean notifyReminder, boolean notifyCommunity) {
        public static Settings defaults() {
            return new Settings("SYSTEM", "vi", true, true, true);
        }
    }

    public Settings settingsOrDefault() {
        return settings == null ? Settings.defaults() : settings;
    }
}

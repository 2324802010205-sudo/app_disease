package com.riceguard.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "riceguard")
public record AppProperties(
        String env,
        String publicBaseUrl,
        String timeZone,
        Firebase firebase,
        Security security,
        Media media,
        Diagnosis diagnosis,
        Weather weather,
        Risk risk,
        Reminders reminders) {

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    public record Firebase(String projectId, String credentials, String credentialsFile, String webApiKey,
                           String identityToolkitUrl, String secureTokenUrl, boolean checkRevoked,
                           boolean emulator) {}

    public record Security(String adminEmails, String corsAllowedOrigins, int rateLimitPerMinute,
                           int authRateLimitPerMinute) {}

    public record Media(String provider, String cloudinaryUrl, String folderPrefix, long maxBytes, String localDir) {}

    public record Diagnosis(String provider, double minConfidence, int timeoutSeconds, String claudeModel,
                            String httpUrl, String httpToken) {}

    public record Weather(String baseUrl, int cacheMinutes) {}

    public record Risk(int mediumFrom, int highFrom, int veryHighFrom) {}

    public record Reminders(boolean enabled, String cron) {}
}

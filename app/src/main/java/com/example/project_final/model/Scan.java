package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Một lượt quét. status: DIAGNOSED | NEEDS_RETAKE | PENDING. riskLevel: NORMAL | WATCH | HIGH | UNKNOWN. */
public class Scan {
    public String id;
    public String fieldId;
    public String fieldName;
    public String province;
    public String district;
    public String imageUrl;
    public String thumbUrl;
    public String status;
    public String riskLevel;
    public String diseaseCode;
    public String diseaseName;
    public double confidence;
    public String severity;
    public List<String> symptoms = new ArrayList<>();
    public String summary;
    public List<Recommendation> recommendations = new ArrayList<>();
    public String retakeAdvice;
    public WeatherSnapshot weather;
    public String stage;
    public String variety;
    public String note;
    public String engine;
    public long createdAt;

    public static class Recommendation {
        public String title;
        public String description;
        public String icon; // SHIELD | LEAF | WATER | BUG | CALENDAR
    }

    public static class WeatherSnapshot {
        public double tempC;
        public int humidity;
        public String condition;
        public int rainProbability;
    }

    public static class Comparison {
        public Scan first;
        public Scan second;
        public long daysBetween;
        public boolean sameDisease;
        public double confidenceDelta;
        public String trend;
    }

    public boolean isDiagnosed() {
        return "DIAGNOSED".equals(status);
    }

    public boolean needsRetake() {
        return "NEEDS_RETAKE".equals(status);
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isHealthy() {
        return isDiagnosed() && "NORMAL".equals(riskLevel);
    }

    public String displayImage() {
        return imageUrl != null ? imageUrl : thumbUrl;
    }

    public int confidencePercent() {
        return (int) Math.round(confidence * 100);
    }
}

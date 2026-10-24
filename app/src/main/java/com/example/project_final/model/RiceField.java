package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Ruộng lúa. status: HEALTHY | WATCH | ALERT | UNKNOWN (suy ra từ lần quét gần nhất). */
public class RiceField {
    public String id;
    public String name;
    public String plot;
    public String province;
    public String district;
    public String address;
    public Double lat;
    public Double lng;
    public double areaHa;
    public String variety;
    public String stage;
    public String plantingDate;
    public String note;
    public String photoUrl;
    public String nextCheckDate;
    public String lastScanId;
    public Long lastScanAt;
    public String lastRisk;
    public long createdAt;
    public long updatedAt;
    public String status;
    public Integer daysUntilCheck;

    public static class Detail {
        public RiceField field;
        public List<Scan> recentScans = new ArrayList<>();
        public List<DiaryEntry> recentDiaries = new ArrayList<>();
    }

    public boolean needsAttention() {
        return "WATCH".equals(status) || "ALERT".equals(status);
    }
}

package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

public class Stats {
    public String range;
    public long scans;
    public long alerts;
    public List<Bucket> distribution = new ArrayList<>();
    public List<Day> daily = new ArrayList<>();
    public List<TopDisease> topDiseases = new ArrayList<>();
    public List<Activity> recentActivities = new ArrayList<>();

    public static class Bucket {
        public String riskLevel;
        public long count;
        public double percent;
    }

    public static class Day {
        public String date;
        public long count;
    }

    public static class TopDisease {
        public String code;
        public String name;
        public long count;
    }

    /** type: SCAN | DIARY | POST. */
    public static class Activity {
        public String type;
        public String title;
        public String description;
        public long at;
        public String refId;
    }
}

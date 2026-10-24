package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Mức nguy cơ theo khu vực. level: LOW | MEDIUM | HIGH | VERY_HIGH. */
public class Risk {
    public static class DiseaseCount {
        public String code;
        public String name;
        public long cases;
    }

    public static class Area {
        public String province;
        public String district;
        public long cases;
        public String level;
        public Long lastAt;
        public List<DiseaseCount> diseases = new ArrayList<>();
        public String dominantStage;
    }

    public static class AreaInfo {
        public String province;
        public String district;
        public String fromDate;
        public String toDate;
        public long cases;
        public String level;
        public Long lastAt;
        public List<DiseaseCount> diseases = new ArrayList<>();
        public String dominantStage;
        public String advice;
        public Weather.Current weather;
    }
}

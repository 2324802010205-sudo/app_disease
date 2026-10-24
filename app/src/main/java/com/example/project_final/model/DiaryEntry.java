package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

public class DiaryEntry {
    public String id;
    public String fieldId;
    public String fieldName;
    public String type;     // CARE | FERTILIZE | SPRAY | WATER | WEED | HARVEST | SCAN
    public String title;
    public String content;
    public String date;     // yyyy-MM-dd
    public String weather;  // SUNNY | CLOUDY | LIGHT_RAIN | HEAVY_RAIN | OTHER
    public List<ImageRef> images = new ArrayList<>();
    public String scanId;
    public long createdAt;
    public long updatedAt;
}

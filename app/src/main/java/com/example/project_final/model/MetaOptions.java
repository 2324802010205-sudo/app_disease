package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Danh mục lựa chọn cho các ô dropdown, lấy từ GET /meta/options. */
public class MetaOptions {
    public List<String> varieties = new ArrayList<>();
    public List<String> stages = new ArrayList<>();
    public List<String> provinces = new ArrayList<>();
    public List<Option> diaryTypes = new ArrayList<>();
    public List<Option> weather = new ArrayList<>();
    public List<Option> topics = new ArrayList<>();
    public List<Option> risk = new ArrayList<>();
    public List<Option> areaRisk = new ArrayList<>();
    public List<Option> feedbackReasons = new ArrayList<>();
    public List<Option> reportReasons = new ArrayList<>();
    public List<Option> roles = new ArrayList<>();

    public static String labelOf(List<Option> list, String code) {
        if (list != null) for (Option o : list) if (o.code.equals(code)) return o.label;
        return code == null ? "" : code;
    }
}

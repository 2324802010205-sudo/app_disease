package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

/** Bệnh trong thư viện. part: LEAF | STEM | GRAIN. cause: FUNGUS | BACTERIA | VIRUS. */
public class Disease {
    public String id;
    public String name;
    public String scientificName;
    public String description;
    public String part;
    public String cause;
    public boolean common;
    public List<String> symptoms = new ArrayList<>();
    public List<String> conditions = new ArrayList<>();
    public List<String> prevention = new ArrayList<>();
    public List<String> treatment = new ArrayList<>();
    public String imageUrl;
    public boolean saved;                 // có ở /diseases và /diseases/{id}
    public List<Disease> related = new ArrayList<>(); // chỉ có ở /diseases/{id}
}

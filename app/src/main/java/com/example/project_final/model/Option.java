package com.example.project_final.model;

public class Option {
    public String code;
    public String label;

    public Option() {}

    public Option(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}

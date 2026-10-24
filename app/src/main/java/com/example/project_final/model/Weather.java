package com.example.project_final.model;

import java.util.ArrayList;
import java.util.List;

public class Weather {
    public String location;
    public double lat;
    public double lng;
    public long updatedAt;
    public Current current;
    public List<Hour> hourly = new ArrayList<>();
    public List<Day> daily = new ArrayList<>();
    public List<String> advice = new ArrayList<>();

    /** icon: SUN | NIGHT | PARTLY | CLOUD | RAIN ... theo Open-Meteo, xem WeatherIcons. */
    public static class Condition {
        public int code;
        public String label;
        public String icon;
    }

    public static class Current {
        public double tempC;
        public double feelsLikeC;
        public int humidity;
        public Condition condition;
        public double windKmh;
        public String windDirection;
        public double uvIndex;
        public String uvLabel;
        public double visibilityKm;
        public String visibilityLabel;
        public int rainProbability;
    }

    public static class Hour {
        public String time;
        public double tempC;
        public int rainProbability;
        public Condition condition;
    }

    public static class Day {
        public String date;
        public double tempMinC;
        public double tempMaxC;
        public int rainProbability;
        public Condition condition;
    }
}

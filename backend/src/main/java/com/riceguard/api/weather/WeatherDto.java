package com.riceguard.api.weather;

import java.util.List;

public record WeatherDto(
        String location,
        double lat,
        double lng,
        long updatedAt,
        Current current,
        List<Hour> hourly,
        List<Day> daily,
        List<String> advice) {

    public record Condition(int code, String label, String icon) {}

    public record Current(double tempC, double feelsLikeC, int humidity, Condition condition, double windKmh,
                          String windDirection, double uvIndex, String uvLabel, double visibilityKm,
                          String visibilityLabel, int rainProbability) {}

    public record Hour(String time, double tempC, int rainProbability, Condition condition) {}

    public record Day(String date, double tempMinC, double tempMaxC, int rainProbability, Condition condition) {}

    /** Ảnh chụp ngắn gọn đính kèm kết quả quét. */
    public record Snapshot(double tempC, int humidity, String condition, int rainProbability) {}
}

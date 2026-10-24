package com.riceguard.api.weather;

import com.riceguard.api.common.Slug;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Tọa độ tâm các tỉnh trồng lúa chính, dùng để lấy thời tiết khi ruộng chưa có tọa độ. */
public final class Provinces {
    public record Point(double lat, double lng) {}

    private static final Map<String, Point> BY_NAME = new LinkedHashMap<>();

    static {
        put("Hà Nội", 21.03, 105.85);
        put("Bắc Giang", 21.27, 106.19);
        put("Vĩnh Phúc", 21.31, 105.60);
        put("Phú Thọ", 21.32, 105.40);
        put("Hải Dương", 20.94, 106.33);
        put("Hưng Yên", 20.65, 106.05);
        put("Thái Bình", 20.45, 106.34);
        put("Nam Định", 20.42, 106.17);
        put("Hà Nam", 20.54, 105.92);
        put("Ninh Bình", 20.25, 105.97);
        put("Thanh Hóa", 19.81, 105.78);
        put("Nghệ An", 18.68, 105.68);
        put("Đắk Lắk", 12.67, 108.04);
        put("TP. Hồ Chí Minh", 10.78, 106.70);
        put("Bình Dương", 11.00, 106.65);
        put("Đồng Nai", 10.95, 106.82);
        put("Long An", 10.54, 106.41);
        put("Tiền Giang", 10.36, 106.36);
        put("Bến Tre", 10.24, 106.38);
        put("Vĩnh Long", 10.25, 105.97);
        put("Trà Vinh", 9.93, 106.34);
        put("Đồng Tháp", 10.46, 105.63);
        put("An Giang", 10.52, 105.13);
        put("Cần Thơ", 10.03, 105.78);
        put("Hậu Giang", 9.78, 105.47);
        put("Sóc Trăng", 9.60, 105.97);
        put("Kiên Giang", 10.01, 105.08);
        put("Bạc Liêu", 9.29, 105.72);
        put("Cà Mau", 9.18, 105.15);
    }

    private static void put(String name, double lat, double lng) {
        BY_NAME.put(name, new Point(lat, lng));
    }

    private Provinces() {}

    public static java.util.Set<String> names() {
        return BY_NAME.keySet();
    }

    public static Optional<Point> find(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String key = Slug.of(name);
        for (Map.Entry<String, Point> e : BY_NAME.entrySet()) {
            if (Slug.of(e.getKey()).equals(key)) return Optional.of(e.getValue());
        }
        return Optional.empty();
    }
}

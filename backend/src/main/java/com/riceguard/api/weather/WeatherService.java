package com.riceguard.api.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.riceguard.api.common.ApiException;
import com.riceguard.api.common.Times;
import com.riceguard.api.config.AppProperties;
import com.riceguard.api.weather.WeatherDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Thời tiết từ Open-Meteo (miễn phí, không cần khóa), có cache theo vị trí. */
@Service
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final RestClient http;
    private final AppProperties props;
    private final Times times;
    private final Cache<String, WeatherDto> cache;

    public WeatherService(AppProperties props, Times times) {
        this.props = props;
        this.times = times;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
        this.cache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(props.weather().cacheMinutes())).maximumSize(2000).build();
    }

    public WeatherDto forecast(double lat, double lng, String label) {
        String key = String.format(Locale.ROOT, "%.2f,%.2f", lat, lng);
        WeatherDto hit = cache.getIfPresent(key);
        if (hit != null) return withLabel(hit, label);
        WeatherDto fresh = fetch(lat, lng, label);
        cache.put(key, fresh);
        return fresh;
    }

    /** Thời tiết ngắn gọn cho kết quả quét; lỗi mạng thì bỏ qua (null), không làm hỏng việc quét. */
    public Snapshot snapshot(Double lat, Double lng) {
        if (lat == null || lng == null) return null;
        try {
            Current c = forecast(lat, lng, null).current();
            return new Snapshot(c.tempC(), c.humidity(), c.condition().label(), c.rainProbability());
        } catch (RuntimeException e) {
            log.debug("Bỏ qua thời tiết: {}", e.getMessage());
            return null;
        }
    }

    private WeatherDto fetch(double lat, double lng, String label) {
        String url = props.weather().baseUrl() + "/forecast?latitude=" + lat + "&longitude=" + lng
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,is_day"
                + "&hourly=temperature_2m,precipitation_probability,weather_code,uv_index,visibility"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                + "&timezone=" + times.zone().getId().replace("/", "%2F") + "&forecast_days=7&wind_speed_unit=kmh";
        JsonNode r;
        try {
            r = http.get().uri(java.net.URI.create(url)).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Open-Meteo lỗi: {}", e.getMessage());
            throw ApiException.unavailable("Không lấy được dữ liệu thời tiết, vui lòng thử lại sau");
        }
        return parse(r, lat, lng, label, times.now());
    }

    static WeatherDto parse(JsonNode r, double lat, double lng, String label, long now) {
        JsonNode cur = r.path("current");
        JsonNode h = r.path("hourly");
        JsonNode d = r.path("daily");
        boolean night = cur.path("is_day").asInt(1) == 0;

        // Vị trí giờ hiện tại trong mảng hourly (so khớp theo chuỗi thời gian "yyyy-MM-dd'T'HH:00").
        String nowHour = cur.path("time").asText("").length() >= 13 ? cur.path("time").asText().substring(0, 13) : "";
        int idx = 0;
        JsonNode times = h.path("time");
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i).asText().startsWith(nowHour)) { idx = i; break; }
        }
        int rainNow = intAt(h.path("precipitation_probability"), idx);
        double uv = h.path("uv_index").path(idx).asDouble(0);
        double visKm = h.path("visibility").path(idx).asDouble(10000) / 1000.0;

        Current current = new Current(round1(cur.path("temperature_2m").asDouble()), round1(cur.path("apparent_temperature").asDouble()),
                cur.path("relative_humidity_2m").asInt(), condition(cur.path("weather_code").asInt(), night),
                round1(cur.path("wind_speed_10m").asDouble()), windDirection(cur.path("wind_direction_10m").asDouble()),
                round1(uv), uvLabel(uv), round1(visKm), visibilityLabel(visKm), rainNow);

        List<Hour> hourly = new ArrayList<>();
        for (int k = 1; k <= 6; k++) {
            int i = idx + k * 3;
            if (i >= times.size()) break;
            LocalDateTime t = LocalDateTime.parse(times.get(i).asText());
            boolean isNight = t.getHour() >= 18 || t.getHour() < 5;
            hourly.add(new Hour(String.format("%02d:00", t.getHour()), round1(h.path("temperature_2m").path(i).asDouble()),
                    intAt(h.path("precipitation_probability"), i), condition(h.path("weather_code").path(i).asInt(), isNight)));
        }

        List<Day> daily = new ArrayList<>();
        JsonNode dt = d.path("time");
        for (int i = 0; i < dt.size(); i++) {
            daily.add(new Day(dt.get(i).asText(), round1(d.path("temperature_2m_min").path(i).asDouble()),
                    round1(d.path("temperature_2m_max").path(i).asDouble()), intAt(d.path("precipitation_probability_max"), i),
                    condition(d.path("weather_code").path(i).asInt(), false)));
        }

        int maxRain24 = 0;
        for (int i = idx; i < Math.min(idx + 24, times.size()); i++) maxRain24 = Math.max(maxRain24, intAt(h.path("precipitation_probability"), i));
        return new WeatherDto(label, lat, lng, now, current, hourly, daily, advice(current, maxRain24));
    }

    private static WeatherDto withLabel(WeatherDto w, String label) {
        return new WeatherDto(label != null ? label : w.location(), w.lat(), w.lng(), w.updatedAt(), w.current(), w.hourly(), w.daily(), w.advice());
    }

    static List<String> advice(Current c, int maxRain24h) {
        List<String> out = new ArrayList<>();
        if (maxRain24h >= 70) out.add("Khả năng mưa cao trong 24 giờ tới: kiểm tra hệ thống thoát nước và hoãn phun thuốc.");
        if (c.humidity() >= 85 && c.tempC() >= 22 && c.tempC() <= 30) out.add("Độ ẩm cao, thời tiết thuận lợi cho nấm đạo ôn: tăng cường kiểm tra ruộng.");
        if (c.tempC() >= 35) out.add("Nắng nóng: giữ mực nước ruộng 3–5 cm, tránh phun thuốc vào buổi trưa.");
        if (c.windKmh() >= 30) out.add("Gió mạnh: không phun thuốc, chú ý lúa đổ ngã.");
        if (out.isEmpty()) out.add("Thời tiết thuận lợi cho công việc đồng áng.");
        return out;
    }

    static Condition condition(int code, boolean night) {
        return switch (code) {
            case 0 -> night ? new Condition(code, "Trời quang", "NIGHT") : new Condition(code, "Trời nắng", "SUN");
            case 1, 2 -> new Condition(code, "Ít mây", night ? "NIGHT" : "PARTLY");
            case 3 -> new Condition(code, "Nhiều mây", "CLOUD");
            case 45, 48 -> new Condition(code, "Sương mù", "CLOUD");
            case 51, 53, 55, 56, 57 -> new Condition(code, "Mưa phùn", "RAIN");
            case 61, 66 -> new Condition(code, "Mưa nhẹ", "RAIN");
            case 63, 67 -> new Condition(code, "Mưa vừa", "RAIN");
            case 65 -> new Condition(code, "Mưa to", "RAIN");
            case 80 -> new Condition(code, "Mưa rào nhẹ", "RAIN");
            case 81 -> new Condition(code, "Mưa rào", "RAIN");
            case 82 -> new Condition(code, "Mưa rào lớn", "RAIN");
            case 95, 96, 99 -> new Condition(code, "Dông", "STORM");
            default -> new Condition(code, "Nhiều mây", "CLOUD");
        };
    }

    static String windDirection(double deg) {
        String[] names = {"Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc"};
        return "Hướng " + names[(int) Math.round(((deg % 360) + 360) % 360 / 45.0) % 8];
    }

    static String uvLabel(double uv) {
        if (uv < 3) return "Thấp";
        if (uv < 6) return "Trung bình";
        if (uv < 8) return "Cao";
        if (uv < 11) return "Rất cao";
        return "Cực cao";
    }

    static String visibilityLabel(double km) {
        if (km >= 10) return "Tốt";
        if (km >= 4) return "Trung bình";
        return "Kém";
    }

    private static int intAt(JsonNode arr, int i) {
        return arr.path(i).isMissingNode() || arr.path(i).isNull() ? 0 : arr.path(i).asInt();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

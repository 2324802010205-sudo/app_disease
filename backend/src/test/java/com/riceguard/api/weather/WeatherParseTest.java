package com.riceguard.api.weather;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherParseTest {
    private static final ObjectMapper M = new ObjectMapper();

    /** Dựng phản hồi Open-Meteo rút gọn: 48 giờ từ 2026-10-23T00:00, hiện tại 10:00. */
    private static JsonNode sample(int humidity, double temp, int rainPercent, int code) throws Exception {
        List<String> time = new ArrayList<>();
        List<Object> temps = new ArrayList<>(), rain = new ArrayList<>(), codes = new ArrayList<>(), uv = new ArrayList<>(), vis = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            time.add(String.format("2026-10-%02dT%02d:00", 23 + i / 24, i % 24));
            temps.add(temp); rain.add(rainPercent); codes.add(code); uv.add(6.2); vis.add(12000);
        }
        var root = M.createObjectNode();
        var cur = root.putObject("current");
        cur.put("time", "2026-10-23T10:00").put("temperature_2m", temp).put("apparent_temperature", temp + 3)
                .put("relative_humidity_2m", humidity).put("weather_code", code).put("wind_speed_10m", 12.4)
                .put("wind_direction_10m", 135).put("is_day", 1);
        var h = root.putObject("hourly");
        h.set("time", M.valueToTree(time)); h.set("temperature_2m", M.valueToTree(temps));
        h.set("precipitation_probability", M.valueToTree(rain)); h.set("weather_code", M.valueToTree(codes));
        h.set("uv_index", M.valueToTree(uv)); h.set("visibility", M.valueToTree(vis));
        var d = root.putObject("daily");
        d.set("time", M.valueToTree(List.of("2026-10-23", "2026-10-24")));
        d.set("weather_code", M.valueToTree(List.of(code, code)));
        d.set("temperature_2m_max", M.valueToTree(List.of(33.0, 32.0)));
        d.set("temperature_2m_min", M.valueToTree(List.of(25.0, 24.0)));
        d.set("precipitation_probability_max", M.valueToTree(List.of(rainPercent, rainPercent)));
        return root;
    }

    @Test
    void parsesCurrentHourlyAndDaily() throws Exception {
        WeatherDto w = WeatherService.parse(sample(78, 29.0, 30, 3), 20.45, 106.34, "Thái Bình", 1L);
        assertEquals("Thái Bình", w.location());
        assertEquals(29.0, w.current().tempC());
        assertEquals(78, w.current().humidity());
        assertEquals("Nhiều mây", w.current().condition().label());
        assertEquals("Hướng Đông Nam", w.current().windDirection());
        assertEquals("Cao", w.current().uvLabel());          // UV 6.2
        assertEquals("Tốt", w.current().visibilityLabel());  // 12 km
        assertEquals(6, w.hourly().size());
        assertEquals("13:00", w.hourly().get(0).time());
        assertEquals("04:00", w.hourly().get(5).time());
        assertEquals(2, w.daily().size());
    }

    @Test
    void humidHotDayWarnsAboutBlast() throws Exception {
        WeatherDto w = WeatherService.parse(sample(90, 27.0, 20, 3), 0, 0, null, 1L);
        assertTrue(w.advice().stream().anyMatch(a -> a.contains("đạo ôn")), w.advice().toString());
    }

    @Test
    void heavyRainWarnsAboutDrainage() throws Exception {
        WeatherDto w = WeatherService.parse(sample(70, 27.0, 85, 65), 0, 0, null, 1L);
        assertTrue(w.advice().stream().anyMatch(a -> a.contains("thoát nước")), w.advice().toString());
        assertEquals("Mưa to", w.current().condition().label());
    }

    @Test
    void calmDayGivesDefaultAdvice() throws Exception {
        WeatherDto w = WeatherService.parse(sample(60, 28.0, 10, 0), 0, 0, null, 1L);
        assertEquals(List.of("Thời tiết thuận lợi cho công việc đồng áng."), w.advice());
    }

    @Test
    void windDirectionWrapsAround() {
        assertEquals("Hướng Bắc", WeatherService.windDirection(359));
        assertEquals("Hướng Bắc", WeatherService.windDirection(0));
        assertEquals("Hướng Tây Bắc", WeatherService.windDirection(315));
    }

    @Test
    void provincesLookupIgnoresDiacritics() {
        assertTrue(Provinces.find("thai binh").isPresent());
        assertTrue(Provinces.find("Đồng Tháp").isPresent());
        assertTrue(Provinces.find("Atlantis").isEmpty());
        assertTrue(Provinces.find(null).isEmpty());
    }
}

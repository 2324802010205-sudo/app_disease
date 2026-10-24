package com.riceguard.api.weather;

import com.riceguard.api.common.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "Thời tiết", description = "Dự báo từ Open-Meteo, công khai")
public class WeatherController {
    private final WeatherService service;

    public WeatherController(WeatherService service) {
        this.service = service;
    }

    /** Truyền province (tên tỉnh) hoặc cặp lat & lng. */
    @GetMapping
    public WeatherDto weather(@RequestParam(required = false) String province,
                              @RequestParam(required = false) Double lat,
                              @RequestParam(required = false) Double lng) {
        if (lat != null && lng != null) {
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) throw ApiException.badRequest("Tọa độ không hợp lệ");
            return service.forecast(lat, lng, province);
        }
        Provinces.Point p = Provinces.find(province)
                .orElseThrow(() -> ApiException.badRequest("Chọn tỉnh hợp lệ hoặc truyền lat và lng"));
        return service.forecast(p.lat(), p.lng(), province);
    }

    @GetMapping("/provinces")
    public Set<String> provinces() {
        return Provinces.names();
    }
}

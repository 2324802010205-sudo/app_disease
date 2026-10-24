package com.riceguard.api.meta;

import com.riceguard.api.weather.Provinces;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Danh sách lựa chọn dùng cho các ô dropdown của app, để thêm/bớt mà không cần phát hành lại app. */
@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Danh mục lựa chọn", description = "Công khai")
public class MetaController {
    public record Option(String code, String label) {}

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.ofEntries(
                Map.entry("varieties", List.of("Bắc Thơm 7", "TBR225", "OM5451", "ST25", "Đài Thơm 8", "OM18", "IR50404", "Khang Dân")),
                Map.entry("stages", List.of("Gieo mạ", "Đẻ nhánh", "Làm đòng", "Trổ bông", "Chín sữa", "Chín")),
                Map.entry("diaryTypes", List.of(
                        new Option("CARE", "Chăm sóc"), new Option("FERTILIZE", "Bón phân"), new Option("SPRAY", "Phun thuốc"),
                        new Option("WATER", "Tưới nước"), new Option("WEED", "Làm cỏ"), new Option("HARVEST", "Thu hoạch"))),
                Map.entry("weather", List.of(
                        new Option("SUNNY", "Nắng"), new Option("CLOUDY", "Nhiều mây"), new Option("LIGHT_RAIN", "Mưa nhẹ"),
                        new Option("HEAVY_RAIN", "Mưa to"), new Option("OTHER", "Khác"))),
                Map.entry("topics", List.of(
                        new Option("PEST", "Sâu bệnh"), new Option("EXPERIENCE", "Kinh nghiệm"), new Option("VARIETY", "Giống lúa"),
                        new Option("WEATHER", "Thời tiết"), new Option("WATER", "Quản lý nước"))),
                Map.entry("risk", List.of(
                        new Option("NORMAL", "Bình thường"), new Option("WATCH", "Cần theo dõi"), new Option("HIGH", "Có nguy cơ"))),
                Map.entry("areaRisk", List.of(
                        new Option("LOW", "Thấp"), new Option("MEDIUM", "Trung bình"), new Option("HIGH", "Cao"), new Option("VERY_HIGH", "Rất cao"))),
                Map.entry("feedbackReasons", List.of(
                        new Option("WRONG_RESULT", "Kết quả chưa đúng"), new Option("UNCLEAR_IMAGE", "Ảnh chưa rõ"),
                        new Option("MISSING_INFO", "Thiếu thông tin"), new Option("NEED_ADVICE", "Cần tư vấn thêm"))),
                Map.entry("reportReasons", List.of(
                        new Option("SPAM", "Spam"), new Option("MISLEADING", "Thông tin sai lệch"),
                        new Option("OFFENSIVE", "Nội dung xúc phạm"), new Option("OTHER", "Khác"))),
                Map.entry("roles", List.of(new Option("USER", "Người dùng"), new Option("FARMER", "Nông dân"), new Option("EXPERT", "Chuyên gia"))),
                Map.entry("provinces", Set.copyOf(Provinces.names()).stream().sorted().toList()));
    }
}

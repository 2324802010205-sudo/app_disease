package com.riceguard.api.risk;

import com.riceguard.api.common.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/risk")
@Tag(name = "Bản đồ nguy cơ", description = "Mức nguy cơ theo khu vực, tính từ các lượt quét ẩn danh của cộng đồng")
public class RiskController {
    private final RiskService service;

    public RiskController(RiskService service) {
        this.service = service;
    }

    /** days: 7 | 14 | 30. sort: risk | count. */
    @GetMapping("/areas")
    public List<RiskService.AreaRisk> areas(@RequestParam(required = false) String province,
                                            @RequestParam(required = false) String disease,
                                            @RequestParam(defaultValue = "7") int days,
                                            @RequestParam(defaultValue = "risk") String sort) {
        return service.areas(province, disease, clamp(days), sort);
    }

    @GetMapping("/areas/info")
    public RiskService.AreaInfo info(@RequestParam String province, @RequestParam String district,
                                     @RequestParam(defaultValue = "7") int days) {
        return service.info(province, district, clamp(days));
    }

    private static int clamp(int days) {
        if (days < 1 || days > 90) throw ApiException.badRequest("Số ngày phải từ 1 đến 90");
        return days;
    }
}

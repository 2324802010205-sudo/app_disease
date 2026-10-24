package com.riceguard.api.stats;

import com.riceguard.api.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Thống kê cá nhân")
public class StatsController {
    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    /** range: week | month. */
    @GetMapping
    public StatsService.Stats stats(@AuthenticationPrincipal AuthUser u, @RequestParam(defaultValue = "week") String range) {
        return service.stats(u.uid(), range);
    }
}

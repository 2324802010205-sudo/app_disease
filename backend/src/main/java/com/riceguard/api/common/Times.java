package com.riceguard.api.common;

import com.riceguard.api.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Giờ hệ thống theo múi giờ Việt Nam; dễ thay Clock khi test. */
@Component
public class Times {
    private final Clock clock;
    private final ZoneId zone;

    public Times(AppProperties props) {
        this.zone = props.zone();
        this.clock = Clock.system(zone);
    }

    public long now() { return clock.millis(); }
    public ZoneId zone() { return zone; }
    public LocalDate today() { return LocalDate.now(clock); }
    public LocalDate dateOf(long epochMillis) { return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(); }
    public long startOfDay(LocalDate d) { return d.atStartOfDay(zone).toInstant().toEpochMilli(); }

    /** Mốc đầu ngày cách đây (days - 1) ngày, để "7 ngày" gồm cả hôm nay. */
    public long sinceDays(int days) { return startOfDay(today().minusDays(Math.max(0, days - 1L))); }
}

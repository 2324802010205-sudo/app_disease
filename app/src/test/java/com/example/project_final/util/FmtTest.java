package com.example.project_final.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FmtTest {
    @Test
    public void areaUsesVietnameseDecimalComma() {
        assertEquals("0,8 ha", Fmt.area(0.8));
        assertEquals("2 ha", Fmt.area(2.0));
        assertEquals("1,25 ha", Fmt.area(1.25));
        assertEquals("2,8", Fmt.areaNumber(2.8));
    }

    @Test
    public void isoDateConvertsToDayMonthYear() {
        assertEquals("26/04/2025", Fmt.isoDate("2025-04-26"));
        assertEquals("", Fmt.isoDate(null));
        assertEquals("", Fmt.isoDate("2025"));
    }

    @Test
    public void toIsoPadsMonthAndDay() {
        assertEquals("2026-01-05", Fmt.toIso(2026, 0, 5));
    }

    @Test
    public void nextCheckDescribesDistance() {
        assertEquals("Chưa đặt lịch", Fmt.nextCheck(null, null));
        assertEquals("26/04/2025 (2 ngày nữa)", Fmt.nextCheck("2025-04-26", 2));
        assertEquals("26/04/2025 (hôm nay)", Fmt.nextCheck("2025-04-26", 0));
        assertEquals("26/04/2025 (quá hạn 3 ngày)", Fmt.nextCheck("2025-04-26", -3));
    }

    @Test
    public void relativeTimeBuckets() {
        long now = System.currentTimeMillis();
        assertEquals("Vừa xong", Fmt.relative(now - 10_000));
        assertEquals("5 phút trước", Fmt.relative(now - 5 * 60_000));
        assertEquals("2 giờ trước", Fmt.relative(now - 2 * 3_600_000L));
        assertEquals("Hôm qua", Fmt.relative(now - 30 * 3_600_000L));
        assertEquals("3 ngày trước", Fmt.relative(now - 3 * 86_400_000L));
    }

    @Test
    public void percentRoundsFraction() {
        assertEquals("87%", Fmt.percent(0.87));
    }
}

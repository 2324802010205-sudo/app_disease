package com.riceguard.api.risk;

import com.riceguard.api.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskLevelsTest {
    private static final AppProperties.Risk T = new AppProperties.Risk(6, 12, 20);

    @Test
    void thresholdsMatchAppSample() {
        assertEquals("VERY_HIGH", RiskLevels.level(24, T));
        assertEquals("HIGH", RiskLevels.level(18, T));
        assertEquals("MEDIUM", RiskLevels.level(11, T));
        assertEquals("MEDIUM", RiskLevels.level(8, T));
        assertEquals("LOW", RiskLevels.level(3, T));
        assertEquals("LOW", RiskLevels.level(0, T));
    }

    @Test
    void boundariesAreInclusive() {
        assertEquals("MEDIUM", RiskLevels.level(6, T));
        assertEquals("HIGH", RiskLevels.level(12, T));
        assertEquals("VERY_HIGH", RiskLevels.level(20, T));
    }

    @Test
    void rankOrdersLevels() {
        assertTrue(RiskLevels.rank("VERY_HIGH") > RiskLevels.rank("HIGH"));
        assertTrue(RiskLevels.rank("HIGH") > RiskLevels.rank("MEDIUM"));
        assertTrue(RiskLevels.rank("MEDIUM") > RiskLevels.rank("LOW"));
    }
}

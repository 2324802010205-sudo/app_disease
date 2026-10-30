package com.riceguard.api.admin;

import com.riceguard.api.scan.Scan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminOverviewCountingTest {
    private static Scan scan(String status, String code) {
        return new Scan("s", null, null, null, null, null, null, null, status, "UNKNOWN", code, null, 0, null,
                List.of(), null, List.of(), null, null, null, null, null, null, false, 0);
    }

    @Test
    void diagnosedDiseaseIsACase() {
        assertTrue(AdminDataController.isCase(scan("DIAGNOSED", "dao-on-la")));
    }

    @Test
    void healthyUnknownPendingAndBlankAreNotCases() {
        assertFalse(AdminDataController.isCase(scan("DIAGNOSED", "healthy")));
        assertFalse(AdminDataController.isCase(scan("DIAGNOSED", "unknown")));
        assertFalse(AdminDataController.isCase(scan("PENDING", "dao-on-la")));
        assertFalse(AdminDataController.isCase(scan("DIAGNOSED", " ")));
        assertFalse(AdminDataController.isCase(scan("DIAGNOSED", null)));
    }
}

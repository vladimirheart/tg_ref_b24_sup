package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionReadinessServiceTest {

    private final ProductionReadinessService service = new ProductionReadinessService(
        null, null, null, null, null, null, null, null,
        "postgresql",
        "docs/runbooks/postgresql-production-contour.md"
    );

    @Test
    void overallStatusRequiresAllCanonicalComponentsHealthy() {
        assertEquals("ready", service.overallStatus(true, List.of(
            component("healthy"),
            component("healthy")
        )));
        assertEquals("degraded", service.overallStatus(true, List.of(
            component("healthy"),
            component("degraded")
        )));
        assertEquals("degraded", service.overallStatus(true, List.of(
            component("unavailable")
        )));
    }

    @Test
    void nonCanonicalModeIsReportedAsCompatibilityWhenNoProbeIsDegraded() {
        assertEquals("compatibility", service.overallStatus(false, List.of(
            component("compatibility"),
            component("healthy")
        )));
    }

    private Map<String, Object> component(String status) {
        return Map.of("status", status);
    }
}

package com.example.panel.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.service.ProductionReadinessService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class ProductionReadinessHealthIndicatorTest {

    @Test
    void reportsUpForReadySnapshot() {
        ProductionReadinessService service = mock(ProductionReadinessService.class);
        when(service.buildSnapshot()).thenReturn(Map.of(
            "overall", "ready",
            "datasource_mode", "postgresql",
            "transport_mode", "rabbitmq",
            "runbook", "docs/runbooks/production-launch-checklist.md",
            "components", List.of()
        ));

        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(service);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownForDegradedSnapshot() {
        ProductionReadinessService service = mock(ProductionReadinessService.class);
        when(service.buildSnapshot()).thenReturn(Map.of(
            "overall", "degraded",
            "datasource_mode", "postgresql",
            "transport_mode", "rabbitmq",
            "runbook", "docs/runbooks/production-launch-checklist.md",
            "components", List.of()
        ));

        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(service);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsUnknownForCompatibilitySnapshot() {
        ProductionReadinessService service = mock(ProductionReadinessService.class);
        when(service.buildSnapshot()).thenReturn(Map.of(
            "overall", "compatibility",
            "datasource_mode", "sqlite",
            "transport_mode", "jdbc",
            "runbook", "docs/runbooks/production-launch-checklist.md",
            "components", List.of()
        ));

        ProductionReadinessHealthIndicator indicator = new ProductionReadinessHealthIndicator(service);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }
}

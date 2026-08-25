package com.example.panel.observability;

import com.example.panel.service.ProductionReadinessService;
import java.util.Map;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("iguanaProduction")
public class ProductionReadinessHealthIndicator implements HealthIndicator {

    private final ProductionReadinessService productionReadinessService;

    public ProductionReadinessHealthIndicator(ProductionReadinessService productionReadinessService) {
        this.productionReadinessService = productionReadinessService;
    }

    @Override
    public Health health() {
        Map<String, Object> snapshot = productionReadinessService.buildSnapshot();
        String overall = String.valueOf(snapshot.getOrDefault("overall", "unknown"));
        Health.Builder builder = switch (overall) {
            case "ready", "healthy" -> Health.up();
            case "compatibility" -> Health.status(Status.UNKNOWN);
            default -> Health.down();
        };

        return builder
            .withDetail("overall", overall)
            .withDetail("datasource_mode", snapshot.get("datasource_mode"))
            .withDetail("transport_mode", snapshot.get("transport_mode"))
            .withDetail("runbook", snapshot.get("runbook"))
            .build();
    }
}

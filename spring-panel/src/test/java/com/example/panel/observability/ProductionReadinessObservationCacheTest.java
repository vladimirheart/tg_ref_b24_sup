package com.example.panel.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.panel.service.ProductionReadinessService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionReadinessObservationCacheTest {

    @Test
    void applySnapshotPopulatesReadinessAndQueueMetrics() {
        ProductionReadinessObservationCache cache = new ProductionReadinessObservationCache(mock(ProductionReadinessService.class));

        cache.apply(Map.of(
            "overall", "ready",
            "components", List.of(
                Map.of(
                    "key", "postgresql",
                    "status", "healthy",
                    "details", Map.of()
                ),
                Map.of(
                    "key", "rabbitmq",
                    "status", "degraded",
                    "details", Map.of(
                        "inbound_messages", 5L,
                        "ticket_created_messages", 2L,
                        "inbound_dlq_messages", 1L,
                        "ticket_created_dlq_messages", 0L
                    )
                ),
                Map.of(
                    "key", "incident_delivery",
                    "status", "healthy",
                    "details", Map.of(
                        "failed_current", 0L,
                        "queued_current", 3L,
                        "processing_current", 1L,
                        "stale_processing", 0L,
                        "delivered_24h", 20L,
                        "failed_24h", 2L
                    )
                )
            )
        ));

        assertThat(cache.overallReadyValue()).isEqualTo(1);
        assertThat(cache.componentReadyValue("postgresql")).isEqualTo(1);
        assertThat(cache.componentReadyValue("rabbitmq")).isEqualTo(0);
        assertThat(cache.queueMetricValue("inbound_messages")).isEqualTo(5L);
        assertThat(cache.queueMetricValue("inbound_dlq_messages")).isEqualTo(1L);
        assertThat(cache.incidentDeliveryMetricValue("queued_current")).isEqualTo(3L);
        assertThat(cache.incidentDeliveryMetricValue("failed_24h")).isEqualTo(2L);
    }
}

package com.example.panel.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProductionReadinessMeterBinder implements MeterBinder {

    private final ProductionReadinessObservationCache cache;

    public ProductionReadinessMeterBinder(ProductionReadinessObservationCache cache) {
        this.cache = cache;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("iguana.production.readiness", cache, ProductionReadinessObservationCache::overallReadyValue)
            .description("1 when Iguana production readiness snapshot is ready, otherwise 0.")
            .register(registry);

        Gauge.builder("iguana.production.readiness.refresh_success", cache, ProductionReadinessObservationCache::lastRefreshSucceededValue)
            .description("1 when the latest production readiness metrics refresh succeeded, otherwise 0.")
            .register(registry);

        for (String component : List.of("postgresql", "redis", "rabbitmq", "object_storage", "incident_delivery")) {
            Gauge.builder("iguana.production.component.ready", cache, observationCache -> observationCache.componentReadyValue(component))
                .tag("component", component)
                .description("1 when the latest production readiness snapshot marks the component healthy.")
                .register(registry);
        }

        for (String metric : List.of("inbound_messages", "ticket_created_messages", "inbound_dlq_messages", "ticket_created_dlq_messages")) {
            Gauge.builder("iguana.transport.queue.messages", cache, observationCache -> observationCache.queueMetricValue(metric))
                .tag("queue_metric", metric)
                .description("Latest RabbitMQ queue counters from Iguana production readiness snapshot.")
                .register(registry);
        }

        for (String metric : List.of("failed_current", "queued_current", "processing_current", "stale_processing", "delivered_24h", "failed_24h")) {
            Gauge.builder("iguana.incident.delivery.outbox", cache, observationCache -> observationCache.incidentDeliveryMetricValue(metric))
                .tag("metric", metric)
                .description("Latest incident delivery outbox counters from Iguana production readiness snapshot.")
                .register(registry);
        }
    }
}

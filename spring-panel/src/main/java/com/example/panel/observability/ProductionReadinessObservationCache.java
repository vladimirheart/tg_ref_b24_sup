package com.example.panel.observability;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.service.ProductionReadinessService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RuntimeWorkload(
    id = "production-readiness-observation-cache",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
@Component
public class ProductionReadinessObservationCache {

    private static final List<String> COMPONENT_KEYS = List.of(
        "postgresql",
        "redis",
        "rabbitmq",
        "object_storage",
        "incident_delivery"
    );

    private static final List<String> QUEUE_METRIC_KEYS = List.of(
        "inbound_messages",
        "ticket_created_messages",
        "inbound_dlq_messages",
        "ticket_created_dlq_messages"
    );

    private static final List<String> INCIDENT_DELIVERY_KEYS = List.of(
        "failed_current",
        "queued_current",
        "processing_current",
        "stale_processing",
        "delivered_24h",
        "failed_24h"
    );

    private final ProductionReadinessService productionReadinessService;
    private final ConcurrentMap<String, AtomicInteger> componentStatuses = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> queueMetrics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> incidentDeliveryMetrics = new ConcurrentHashMap<>();
    private final AtomicInteger overallReady = new AtomicInteger(0);
    private final AtomicInteger lastRefreshSucceeded = new AtomicInteger(0);
    private volatile String overallStatus = "unknown";
    private volatile String lastRefreshError = "";

    public ProductionReadinessObservationCache(ProductionReadinessService productionReadinessService) {
        this.productionReadinessService = productionReadinessService;
        COMPONENT_KEYS.forEach(key -> componentStatuses.put(key, new AtomicInteger(0)));
        QUEUE_METRIC_KEYS.forEach(key -> queueMetrics.put(key, new AtomicLong(0L)));
        INCIDENT_DELIVERY_KEYS.forEach(key -> incidentDeliveryMetrics.put(key, new AtomicLong(0L)));
    }

    @PostConstruct
    public void initialize() {
        refresh();
    }

    @Scheduled(
        fixedDelayString = "${panel.observability.production-readiness.refresh-interval-ms:30000}",
        initialDelayString = "${panel.observability.production-readiness.initial-delay-ms:5000}"
    )
    public void refresh() {
        try {
            Map<String, Object> snapshot = productionReadinessService.buildSnapshot();
            apply(snapshot);
            lastRefreshSucceeded.set(1);
            lastRefreshError = "";
        } catch (Exception ex) {
            lastRefreshSucceeded.set(0);
            lastRefreshError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
    }

    public int overallReadyValue() {
        return overallReady.get();
    }

    public int componentReadyValue(String key) {
        AtomicInteger value = componentStatuses.get(key);
        return value == null ? 0 : value.get();
    }

    public long queueMetricValue(String key) {
        AtomicLong value = queueMetrics.get(key);
        return value == null ? 0L : value.get();
    }

    public long incidentDeliveryMetricValue(String key) {
        AtomicLong value = incidentDeliveryMetrics.get(key);
        return value == null ? 0L : value.get();
    }

    public int lastRefreshSucceededValue() {
        return lastRefreshSucceeded.get();
    }

    public String overallStatus() {
        return overallStatus;
    }

    public String lastRefreshError() {
        return lastRefreshError;
    }

    @SuppressWarnings("unchecked")
    void apply(Map<String, Object> snapshot) {
        overallStatus = String.valueOf(snapshot.getOrDefault("overall", "unknown"));
        overallReady.set(isReadyStatus(overallStatus) ? 1 : 0);

        COMPONENT_KEYS.forEach(key -> componentStatuses.get(key).set(0));
        QUEUE_METRIC_KEYS.forEach(key -> queueMetrics.get(key).set(0L));
        INCIDENT_DELIVERY_KEYS.forEach(key -> incidentDeliveryMetrics.get(key).set(0L));

        Object componentsValue = snapshot.get("components");
        if (!(componentsValue instanceof List<?> components)) {
            return;
        }

        for (Object componentValue : components) {
            if (!(componentValue instanceof Map<?, ?> rawComponent)) {
                continue;
            }
            String key = stringValue(rawComponent.get("key"));
            if (key == null) {
                continue;
            }
            AtomicInteger status = componentStatuses.get(key);
            if (status != null) {
                status.set(isHealthyStatus(stringValue(rawComponent.get("status"))) ? 1 : 0);
            }
            Object detailsValue = rawComponent.get("details");
            if (detailsValue instanceof Map<?, ?> rawDetails) {
                applyComponentDetails(key, (Map<Object, Object>) rawDetails);
            }
        }
    }

    private void applyComponentDetails(String key, Map<Object, Object> rawDetails) {
        if ("rabbitmq".equals(key)) {
            setLongMetric(queueMetrics, "inbound_messages", rawDetails.get("inbound_messages"));
            setLongMetric(queueMetrics, "ticket_created_messages", rawDetails.get("ticket_created_messages"));
            setLongMetric(queueMetrics, "inbound_dlq_messages", rawDetails.get("inbound_dlq_messages"));
            setLongMetric(queueMetrics, "ticket_created_dlq_messages", rawDetails.get("ticket_created_dlq_messages"));
        }
        if ("incident_delivery".equals(key)) {
            setLongMetric(incidentDeliveryMetrics, "failed_current", rawDetails.get("failed_current"));
            setLongMetric(incidentDeliveryMetrics, "queued_current", rawDetails.get("queued_current"));
            setLongMetric(incidentDeliveryMetrics, "processing_current", rawDetails.get("processing_current"));
            setLongMetric(incidentDeliveryMetrics, "stale_processing", rawDetails.get("stale_processing"));
            setLongMetric(incidentDeliveryMetrics, "delivered_24h", rawDetails.get("delivered_24h"));
            setLongMetric(incidentDeliveryMetrics, "failed_24h", rawDetails.get("failed_24h"));
        }
    }

    private void setLongMetric(ConcurrentMap<String, AtomicLong> target, String key, Object value) {
        AtomicLong gauge = target.get(key);
        if (gauge != null) {
            gauge.set(longValue(value));
        }
    }

    private boolean isReadyStatus(String status) {
        return "ready".equalsIgnoreCase(status) || "healthy".equalsIgnoreCase(status);
    }

    private boolean isHealthyStatus(String status) {
        return "healthy".equalsIgnoreCase(status) || "ready".equalsIgnoreCase(status);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

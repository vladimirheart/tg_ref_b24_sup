package com.example.panel.service;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.ObjectStorageProperties;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductionReadinessService {

    private static final String STATUS_HEALTHY = "healthy";
    private static final String STATUS_DEGRADED = "degraded";
    private static final String STATUS_COMPATIBILITY = "compatibility";
    private static final String STATUS_UNAVAILABLE = "unavailable";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final RuntimeCoordinationProperties runtimeCoordinationProperties;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final PanelIntegrationTransportMode integrationTransportMode;
    private final IntegrationRabbitProperties rabbitProperties;
    private final RabbitAdmin rabbitAdmin;
    private final AttachmentObjectStorageService objectStorageService;
    private final ObjectStorageProperties objectStorageProperties;
    private final String datasourceMode;
    private final String runbookPath;

    public ProductionReadinessService(
        JdbcTemplate jdbcTemplate,
        DataSource dataSource,
        RuntimeCoordinationProperties runtimeCoordinationProperties,
        RuntimeCoordinationService runtimeCoordinationService,
        PanelIntegrationTransportMode integrationTransportMode,
        IntegrationRabbitProperties rabbitProperties,
        RabbitAdmin rabbitAdmin,
        AttachmentObjectStorageService objectStorageService,
        ObjectStorageProperties objectStorageProperties,
        @Value("${app.datasource.mode:postgresql}") String datasourceMode,
        @Value("${panel.production-readiness.runbook:docs/runbooks/postgresql-production-contour.md}") String runbookPath
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.runtimeCoordinationProperties = runtimeCoordinationProperties;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.integrationTransportMode = integrationTransportMode;
        this.rabbitProperties = rabbitProperties;
        this.rabbitAdmin = rabbitAdmin;
        this.objectStorageService = objectStorageService;
        this.objectStorageProperties = objectStorageProperties;
        this.datasourceMode = normalizeLower(datasourceMode, "postgresql");
        this.runbookPath = StringUtils.hasText(runbookPath)
            ? runbookPath.trim()
            : "docs/runbooks/postgresql-production-contour.md";
    }

    public Map<String, Object> buildSnapshot() {
        boolean postgresqlMode = "postgresql".equals(datasourceMode);
        boolean fullProductionContour = isFullProductionContour(postgresqlMode);
        List<Map<String, Object>> components = new ArrayList<>();
        components.add(databaseProbe(postgresqlMode));
        components.add(redisProbe(postgresqlMode));
        components.add(rabbitProbe(postgresqlMode));
        components.add(objectStorageProbe(postgresqlMode));
        components.add(incidentDeliveryProbe(postgresqlMode));

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("overall", overallStatus(fullProductionContour, components));
        payload.put("contour", fullProductionContour ? "production" : STATUS_COMPATIBILITY);
        payload.put("generated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        payload.put("datasource_mode", datasourceMode);
        payload.put("transport_mode", integrationTransportMode != null ? integrationTransportMode.mode() : "unknown");
        payload.put("runbook", runbookPath);
        payload.put("components", components);
        return payload;
    }

    String overallStatus(boolean fullProductionContour, List<Map<String, Object>> components) {
        for (Map<String, Object> component : components == null ? List.<Map<String, Object>>of() : components) {
            String status = String.valueOf(component.getOrDefault("status", STATUS_UNAVAILABLE));
            if (STATUS_DEGRADED.equals(status) || STATUS_UNAVAILABLE.equals(status)) {
                return STATUS_DEGRADED;
            }
        }
        return fullProductionContour ? "ready" : STATUS_COMPATIBILITY;
    }

    private Map<String, Object> databaseProbe(boolean canonical) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("mode", datasourceMode);
        try (Connection connection = dataSource.getConnection()) {
            Integer ping = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            DatabaseMetaData metadata = connection.getMetaData();
            String product = metadata == null ? null : normalize(metadata.getDatabaseProductName());
            details.put("product", product == null ? "unknown" : product);
            if (ping == null || ping != 1) {
                return component("postgresql", "PostgreSQL", STATUS_UNAVAILABLE, canonical,
                    "Database probe returned an unexpected result.", details);
            }
            boolean postgresProduct = product != null
                && product.toLowerCase(Locale.ROOT).contains("postgresql");
            if (canonical && !postgresProduct) {
                return component("postgresql", "PostgreSQL", STATUS_DEGRADED, true,
                    "Canonical mode is postgresql, but active JDBC product is not PostgreSQL.", details);
            }
            return component("postgresql", "PostgreSQL",
                canonical ? STATUS_HEALTHY : STATUS_COMPATIBILITY,
                canonical,
                canonical ? "Canonical database answers SELECT 1." : "Compatibility datasource is reachable.",
                details);
        } catch (Exception ex) {
            return component("postgresql", "PostgreSQL", STATUS_UNAVAILABLE, canonical,
                "Database probe failed: " + errorText(ex), details);
        }
    }

    private Map<String, Object> redisProbe(boolean canonical) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        String mode = runtimeCoordinationProperties == null
            ? "unknown"
            : normalizeLower(runtimeCoordinationProperties.getMode(), "unknown");
        boolean requiredForPostgresql = runtimeCoordinationProperties == null
            || runtimeCoordinationProperties.isRequiredForPostgresql();
        details.put("mode", mode);
        details.put("required_for_postgresql", requiredForPostgresql);
        if (!canonical) {
            return component("redis", "Redis coordination", STATUS_COMPATIBILITY, false,
                "Redis is not a required gate in compatibility datasource mode.", details);
        }
        if (!requiredForPostgresql) {
            return component("redis", "Redis coordination", STATUS_COMPATIBILITY, false,
                "Local PostgreSQL bootstrap allows direct coordination because Redis is not required for this contour.", details);
        }
        try {
            runtimeCoordinationService.verifyAvailable();
            return component("redis", "Redis coordination", STATUS_HEALTHY, true,
                "Shared coordination backend responds to readiness ping.", details);
        } catch (Exception ex) {
            return component("redis", "Redis coordination", STATUS_UNAVAILABLE, true,
                "Redis readiness probe failed: " + errorText(ex), details);
        }
    }

    private Map<String, Object> rabbitProbe(boolean canonical) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        String transportMode = integrationTransportMode == null ? "unknown" : integrationTransportMode.mode();
        details.put("transport_mode", transportMode);
        if (!canonical) {
            return component("rabbitmq", "RabbitMQ transport", STATUS_COMPATIBILITY, false,
                "RabbitMQ is not a required gate in compatibility datasource mode.", details);
        }
        if (integrationTransportMode == null || !integrationTransportMode.isRabbitMqMode()) {
            return component("rabbitmq", "RabbitMQ transport", STATUS_DEGRADED, true,
                "Production contour expects app.integration.transport.mode=rabbitmq.", details);
        }
        String inboundQueue = rabbitProperties == null ? null : normalize(rabbitProperties.getInboundQueue());
        String ticketQueue = rabbitProperties == null ? null : normalize(rabbitProperties.getTicketCreatedQueue());
        String inboundDlq = rabbitProperties == null ? null : normalize(rabbitProperties.getInboundDlq());
        String ticketDlq = rabbitProperties == null ? null : normalize(rabbitProperties.getTicketCreatedDlq());
        details.put("inbound_queue", inboundQueue == null ? "not configured" : inboundQueue);
        details.put("ticket_created_queue", ticketQueue == null ? "not configured" : ticketQueue);
        details.put("inbound_dlq", inboundDlq == null ? "not configured" : inboundDlq);
        details.put("ticket_created_dlq", ticketDlq == null ? "not configured" : ticketDlq);
        if (inboundQueue == null || ticketQueue == null) {
            return component("rabbitmq", "RabbitMQ transport", STATUS_DEGRADED, true,
                "Required RabbitMQ queue names are not configured.", details);
        }
        try {
            var inboundInfo = rabbitAdmin.getQueueInfo(inboundQueue);
            var ticketInfo = rabbitAdmin.getQueueInfo(ticketQueue);
            if (inboundInfo == null || ticketInfo == null) {
                return component("rabbitmq", "RabbitMQ transport", STATUS_UNAVAILABLE, true,
                    "Required RabbitMQ queues are not declared or cannot be inspected.", details);
            }
            details.put("inbound_messages", inboundInfo.getMessageCount());
            details.put("ticket_created_messages", ticketInfo.getMessageCount());
            long dlqMessages = 0L;
            if (inboundDlq != null) {
                var info = rabbitAdmin.getQueueInfo(inboundDlq);
                if (info != null) {
                    details.put("inbound_dlq_messages", info.getMessageCount());
                    dlqMessages += info.getMessageCount();
                }
            }
            if (ticketDlq != null) {
                var info = rabbitAdmin.getQueueInfo(ticketDlq);
                if (info != null) {
                    details.put("ticket_created_dlq_messages", info.getMessageCount());
                    dlqMessages += info.getMessageCount();
                }
            }
            return component("rabbitmq", "RabbitMQ transport",
                dlqMessages > 0L ? STATUS_DEGRADED : STATUS_HEALTHY,
                true,
                dlqMessages > 0L
                    ? "RabbitMQ is reachable, but dead-letter queues contain messages that require review."
                    : "Required queues are declared and RabbitMQ is reachable.",
                details);
        } catch (Exception ex) {
            return component("rabbitmq", "RabbitMQ transport", STATUS_UNAVAILABLE, true,
                "RabbitMQ probe failed: " + errorText(ex), details);
        }
    }

    private Map<String, Object> objectStorageProbe(boolean canonical) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        String provider = objectStorageService == null ? "unknown" : objectStorageService.providerLabel();
        boolean requiredForPostgresql = objectStorageProperties == null
            || objectStorageProperties.isRequiredForPostgresql();
        details.put("provider", provider);
        details.put("required_for_postgresql", requiredForPostgresql);
        if (!canonical) {
            return component("object_storage", "MinIO / S3", STATUS_COMPATIBILITY, false,
                "Object storage is not a required gate in compatibility datasource mode.", details);
        }
        if (!requiredForPostgresql) {
            return component("object_storage", "MinIO / S3", STATUS_COMPATIBILITY, false,
                "Local PostgreSQL bootstrap allows filesystem-backed attachments because S3 is not required for this contour.", details);
        }
        try {
            objectStorageService.verifyAvailable();
            return component("object_storage", "MinIO / S3", STATUS_HEALTHY, true,
                "Configured S3-compatible bucket is reachable.", details);
        } catch (Exception ex) {
            return component("object_storage", "MinIO / S3", STATUS_UNAVAILABLE, true,
                "Object storage probe failed: " + errorText(ex), details);
        }
    }

    private Map<String, Object> incidentDeliveryProbe(boolean canonical) {
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Timestamp staleBefore = Timestamp.from(now.minusMinutes(5).toInstant());
        Timestamp since = Timestamp.from(now.minusHours(24).toInstant());
        try {
            long failed = count("SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'failed'");
            long queued = count("SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'queued'");
            long processing = count("SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'processing'");
            long stale = count(
                "SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'processing' AND processing_started_at IS NOT NULL AND processing_started_at < ?",
                staleBefore
            );
            long delivered24h = count(
                "SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'delivered' AND created_at >= ?",
                since
            );
            long failed24h = count(
                "SELECT COUNT(*) FROM incident_route_delivery_outbox WHERE status = 'failed' AND created_at >= ?",
                since
            );
            details.put("failed_current", failed);
            details.put("queued_current", queued);
            details.put("processing_current", processing);
            details.put("stale_processing", stale);
            details.put("delivered_24h", delivered24h);
            details.put("failed_24h", failed24h);
            long terminal24h = delivered24h + failed24h;
            details.put("terminal_success_rate_24h",
                terminal24h == 0L ? null : Math.round((delivered24h * 1000.0d / terminal24h)) / 10.0d);
            boolean degraded = failed > 0L || stale > 0L;
            String status = degraded
                ? STATUS_DEGRADED
                : canonical ? STATUS_HEALTHY : STATUS_COMPATIBILITY;
            return component("incident_delivery", "Incident alert delivery", status, canonical,
                degraded
                    ? "Durable incident delivery has unresolved failures or stale processing."
                    : "Durable incident delivery has no unresolved failures or stale processing.",
                details);
        } catch (Exception ex) {
            return component("incident_delivery", "Incident alert delivery", STATUS_UNAVAILABLE, canonical,
                "Incident delivery probe failed: " + errorText(ex), details);
        }
    }

    private long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : Math.max(0L, value);
    }

    private Map<String, Object> component(String key,
                                          String label,
                                          String status,
                                          boolean required,
                                          String summary,
                                          Map<String, Object> details) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("label", label);
        payload.put("status", status);
        payload.put("required", required);
        payload.put("summary", summary);
        payload.put("details", details == null ? Map.of() : new LinkedHashMap<>(details));
        return payload;
    }

    private String errorText(Exception ex) {
        if (ex == null) {
            return "unknown error";
        }
        String message = normalize(ex.getMessage());
        String value = message == null ? ex.getClass().getSimpleName() : message;
        return value.length() <= 400 ? value : value.substring(0, 400) + "...";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeLower(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized.toLowerCase(Locale.ROOT);
    }

    private boolean isFullProductionContour(boolean postgresqlMode) {
        if (!postgresqlMode) {
            return false;
        }
        boolean coordinationRequired = runtimeCoordinationProperties == null
            || runtimeCoordinationProperties.isRequiredForPostgresql();
        boolean objectStorageRequired = objectStorageProperties == null
            || objectStorageProperties.isRequiredForPostgresql();
        boolean rabbitMqTransport = integrationTransportMode != null && integrationTransportMode.isRabbitMqMode();
        return coordinationRequired && objectStorageRequired && rabbitMqTransport;
    }
}

package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class IncidentNotificationRouteHealthService {

    private static final int WINDOW_HOURS = 24;
    private static final int FAILURE_LIMIT = 15;

    private final JdbcTemplate jdbcTemplate;

    public IncidentNotificationRouteHealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RouteHealthSnapshot buildSnapshot() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Timestamp since = Timestamp.from(now.minusHours(WINDOW_HOURS).toInstant());

        List<RouteStatusCount> recentCounts = jdbcTemplate.query(
            """
            SELECT route_type,
                   status,
                   COUNT(*) AS total
              FROM incident_route_delivery_outbox
             WHERE created_at >= ?
             GROUP BY route_type, status
            """,
            (rs, rowNum) -> new RouteStatusCount(
                rs.getString("route_type"),
                rs.getString("status"),
                rs.getLong("total")
            ),
            since
        );

        List<StatusCount> backlogCounts = jdbcTemplate.query(
            """
            SELECT status,
                   COUNT(*) AS total
              FROM incident_route_delivery_outbox
             WHERE status IN ('queued', 'processing', 'failed')
             GROUP BY status
            """,
            (rs, rowNum) -> new StatusCount(
                rs.getString("status"),
                rs.getLong("total")
            )
        );

        OffsetDateTime lastDeliveredAt = jdbcTemplate.query(
            "SELECT MAX(delivered_at) FROM incident_route_delivery_outbox WHERE status = 'delivered'",
            rs -> rs.next() ? readOffsetDateTime(rs.getObject(1)) : null
        );
        OffsetDateTime lastFailedAt = jdbcTemplate.query(
            "SELECT MAX(updated_at) FROM incident_route_delivery_outbox WHERE status = 'failed'",
            rs -> rs.next() ? readOffsetDateTime(rs.getObject(1)) : null
        );

        List<FailedDeliveryEvent> recentFailures = jdbcTemplate.query(
            """
            SELECT event_id,
                   incident_id,
                   route_id,
                   event_type,
                   route_type,
                   route_target,
                   attempt_count,
                   last_error,
                   created_at,
                   updated_at
              FROM incident_route_delivery_outbox
             WHERE status = 'failed'
             ORDER BY updated_at DESC, created_at DESC, event_id DESC
             LIMIT ?
            """,
            (rs, rowNum) -> new FailedDeliveryEvent(
                rs.getString("event_id"),
                rs.getLong("incident_id"),
                rs.getLong("route_id"),
                rs.getString("event_type"),
                rs.getString("route_type"),
                rs.getString("route_target"),
                rs.getInt("attempt_count"),
                rs.getString("last_error"),
                readOffsetDateTime(rs.getObject("created_at")),
                readOffsetDateTime(rs.getObject("updated_at")),
                classifyErrorKind(rs.getString("last_error")),
                classifyFailureKind(rs.getString("last_error"))
            ),
            FAILURE_LIMIT
        );

        long delivered24h = 0L;
        long failed24h = 0L;
        long pending24h = 0L;
        Map<String, MutableBreakdown> breakdown = new LinkedHashMap<>();
        for (RouteStatusCount count : recentCounts) {
            MutableBreakdown bucket = breakdown.computeIfAbsent(normalizeRouteType(count.routeType()), ignored -> new MutableBreakdown());
            bucket.add(count.status(), count.total());
            String normalizedStatus = normalizeStatus(count.status());
            if ("delivered".equals(normalizedStatus)) {
                delivered24h += count.total();
            } else if ("failed".equals(normalizedStatus)) {
                failed24h += count.total();
            } else if ("queued".equals(normalizedStatus) || "processing".equals(normalizedStatus)) {
                pending24h += count.total();
            }
        }

        long queuedBacklog = 0L;
        long processingBacklog = 0L;
        long failedBacklog = 0L;
        for (StatusCount count : backlogCounts) {
            String normalizedStatus = normalizeStatus(count.status());
            if ("queued".equals(normalizedStatus)) {
                queuedBacklog += count.total();
            } else if ("processing".equals(normalizedStatus)) {
                processingBacklog += count.total();
            } else if ("failed".equals(normalizedStatus)) {
                failedBacklog += count.total();
            }
        }

        long transientFailures = recentFailures.stream().filter(item -> "transient".equals(item.failureKind())).count();
        long permanentFailures = recentFailures.stream().filter(item -> "permanent".equals(item.failureKind())).count();

        List<RouteTypeBreakdown> routeTypes = new ArrayList<>();
        for (Map.Entry<String, MutableBreakdown> entry : breakdown.entrySet()) {
            MutableBreakdown bucket = entry.getValue();
            routeTypes.add(new RouteTypeBreakdown(
                entry.getKey(),
                bucket.delivered,
                bucket.failed,
                bucket.pending(),
                successRate(bucket.delivered, bucket.failed)
            ));
        }

        String overallStatus = resolveOverallStatus(delivered24h, failed24h, queuedBacklog, processingBacklog, failedBacklog);

        return new RouteHealthSnapshot(
            now,
            WINDOW_HOURS,
            FAILURE_LIMIT,
            overallStatus,
            delivered24h,
            failed24h,
            pending24h,
            queuedBacklog,
            processingBacklog,
            failedBacklog,
            transientFailures,
            permanentFailures,
            successRate(delivered24h, failed24h),
            lastDeliveredAt,
            lastFailedAt,
            routeTypes,
            recentFailures
        );
    }

    private String resolveOverallStatus(long delivered24h,
                                        long failed24h,
                                        long queuedBacklog,
                                        long processingBacklog,
                                        long failedBacklog) {
        if (failedBacklog > 0L) {
            return "critical";
        }
        if (queuedBacklog > 0L || processingBacklog > 0L || failed24h > 0L) {
            return "warning";
        }
        if (delivered24h > 0L) {
            return "ok";
        }
        return "idle";
    }

    private Double successRate(long delivered,
                               long failed) {
        long terminal = delivered + failed;
        if (terminal <= 0L) {
            return null;
        }
        double value = delivered * 100.0d / terminal;
        return Math.round(value * 10.0d) / 10.0d;
    }

    private static OffsetDateTime readOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(value.toString());
    }

    private String normalizeRouteType(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all_operators", "operator", "operators" -> "all_operators";
            default -> normalized;
        };
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String classifyErrorKind(String error) {
        String normalized = normalizeText(error);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.contains("responded with status=")) {
            return "webhook_http";
        }
        if (lowered.contains("invalid incident webhook url")) {
            return "invalid_target";
        }
        if (lowered.contains("no active recipients") || lowered.contains("users target is empty")) {
            return "empty_recipients";
        }
        if (lowered.contains("unsupported incident route type")) {
            return "unsupported_route";
        }
        if (lowered.contains("timeout")) {
            return "timeout";
        }
        if (lowered.contains("i/o")) {
            return "io_error";
        }
        return "delivery_error";
    }

    private String classifyFailureKind(String error) {
        String normalized = normalizeText(error);
        if (normalized == null) {
            return "unknown";
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.contains("timeout")
            || lowered.contains("interrupted")
            || lowered.contains("i/o")
            || lowered.contains("503")
            || lowered.contains("502")
            || lowered.contains("504")
            || lowered.contains("429")) {
            return "transient";
        }
        if (lowered.contains("invalid")
            || lowered.contains("unsupported")
            || lowered.contains("empty")
            || lowered.contains("400")
            || lowered.contains("401")
            || lowered.contains("403")
            || lowered.contains("404")) {
            return "permanent";
        }
        return "unknown";
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static final class MutableBreakdown {
        private long delivered;
        private long failed;
        private long queued;
        private long processing;

        private void add(String status,
                         long count) {
            String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "delivered" -> delivered += count;
                case "failed" -> failed += count;
                case "queued" -> queued += count;
                case "processing" -> processing += count;
                default -> {
                }
            }
        }

        private long pending() {
            return queued + processing;
        }
    }

    private record RouteStatusCount(String routeType, String status, long total) {
    }

    private record StatusCount(String status, long total) {
    }

    public record RouteHealthSnapshot(OffsetDateTime generatedAt,
                                      int windowHours,
                                      int failureLimit,
                                      String overallStatus,
                                      long delivered24h,
                                      long failed24h,
                                      long pending24h,
                                      long queuedBacklog,
                                      long processingBacklog,
                                      long failedBacklog,
                                      long transientFailures,
                                      long permanentFailures,
                                      Double successRate24h,
                                      OffsetDateTime lastDeliveredAt,
                                      OffsetDateTime lastFailedAt,
                                      List<RouteTypeBreakdown> routeTypes,
                                      List<FailedDeliveryEvent> recentFailures) {
    }

    public record RouteTypeBreakdown(String routeType,
                                     long delivered24h,
                                     long failed24h,
                                     long pending24h,
                                     Double successRate24h) {
    }

    public record FailedDeliveryEvent(String eventId,
                                      long incidentId,
                                      long routeId,
                                      String eventType,
                                      String routeType,
                                      String routeTarget,
                                      int attemptCount,
                                      String lastError,
                                      OffsetDateTime createdAt,
                                      OffsetDateTime updatedAt,
                                      String errorKind,
                                      String failureKind) {
    }
}

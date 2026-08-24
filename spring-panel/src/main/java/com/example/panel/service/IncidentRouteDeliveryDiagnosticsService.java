package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentRouteDeliveryDiagnosticsService {

    private static final int WINDOW_HOURS = 24;
    private static final int HISTORY_LIMIT = 80;

    private final JdbcTemplate jdbcTemplate;
    private final IncidentRepository incidentRepository;
    private final IncidentRouteRepository incidentRouteRepository;

    public IncidentRouteDeliveryDiagnosticsService(JdbcTemplate jdbcTemplate,
                                                   IncidentRepository incidentRepository,
                                                   IncidentRouteRepository incidentRouteRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.incidentRepository = incidentRepository;
        this.incidentRouteRepository = incidentRouteRepository;
    }

    public Map<String, Object> buildHealth(Long incidentId) {
        if (incidentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите incident id.");
        }
        Incident incident = incidentRepository.findById(incidentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident не найден."));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Timestamp since = Timestamp.from(now.minusHours(WINDOW_HOURS).toInstant());

        List<RouteStatusCount> counts = jdbcTemplate.query("""
                SELECT route_id,
                       status,
                       COUNT(*) AS total
                  FROM incident_route_delivery_outbox
                 WHERE incident_id = ?
                   AND created_at >= ?
                 GROUP BY route_id, status
                """,
            (rs, rowNum) -> new RouteStatusCount(
                rs.getLong("route_id"),
                rs.getString("status"),
                rs.getLong("total")
            ),
            incidentId,
            since
        );

        List<DeliveryHistoryRow> history = jdbcTemplate.query("""
                SELECT event_id,
                       route_id,
                       event_type,
                       route_type,
                       route_target,
                       status,
                       attempt_count,
                       last_error,
                       available_at,
                       processing_started_at,
                       delivered_at,
                       created_at,
                       updated_at,
                       requested_by
                  FROM incident_route_delivery_outbox
                 WHERE incident_id = ?
                 ORDER BY created_at DESC, event_id DESC
                 LIMIT ?
                """,
            this::mapHistoryRow,
            incidentId,
            HISTORY_LIMIT
        );

        List<IncidentRoute> routes = incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
        return assembleHealth(incident, routes, counts, history, now);
    }

    Map<String, Object> assembleHealth(Incident incident,
                                       List<IncidentRoute> routes,
                                       List<RouteStatusCount> counts,
                                       List<DeliveryHistoryRow> history,
                                       OffsetDateTime generatedAt) {
        List<IncidentRoute> safeRoutes = routes == null ? List.of() : routes;
        List<RouteStatusCount> safeCounts = counts == null ? List.of() : counts;
        List<DeliveryHistoryRow> safeHistory = history == null ? List.of() : history;

        LinkedHashMap<Long, MutableStats> statsByRoute = new LinkedHashMap<>();
        for (IncidentRoute route : safeRoutes) {
            if (route != null && route.getId() != null) {
                statsByRoute.put(route.getId(), new MutableStats());
            }
        }

        MutableStats totalStats = new MutableStats();
        for (RouteStatusCount count : safeCounts) {
            if (count == null || count.routeId() <= 0L || count.total() <= 0L) {
                continue;
            }
            MutableStats routeStats = statsByRoute.computeIfAbsent(count.routeId(), ignored -> new MutableStats());
            routeStats.add(count.status(), count.total());
            totalStats.add(count.status(), count.total());
        }

        List<Map<String, Object>> routePayloads = new ArrayList<>();
        boolean hasCurrentFailedRoute = false;
        boolean hasCurrentPendingRoute = false;
        for (IncidentRoute route : safeRoutes) {
            if (route == null || route.getId() == null) {
                continue;
            }
            MutableStats routeStats = statsByRoute.getOrDefault(route.getId(), new MutableStats());
            String currentStatus = normalize(route.getRouteStatus());
            hasCurrentFailedRoute = hasCurrentFailedRoute || "failed".equalsIgnoreCase(currentStatus);
            hasCurrentPendingRoute = hasCurrentPendingRoute
                || "queued".equalsIgnoreCase(currentStatus)
                || "processing".equalsIgnoreCase(currentStatus);

            LinkedHashMap<String, Object> routePayload = new LinkedHashMap<>();
            routePayload.put("route_id", route.getId());
            routePayload.put("route_type", normalize(route.getRouteType()));
            routePayload.put("route_target", normalize(route.getRouteTarget()));
            routePayload.put("route_status", currentStatus);
            routePayload.put("note", normalize(route.getNote()));
            routePayload.put("events_24h_count", routeStats.total());
            routePayload.put("delivered_24h_count", routeStats.delivered);
            routePayload.put("failed_24h_count", routeStats.failed);
            routePayload.put("pending_24h_count", routeStats.pending());
            routePayload.put("success_rate_24h", successRate(routeStats.delivered, routeStats.failed));
            routePayload.put("health_status", routeHealth(currentStatus, routeStats));
            routePayloads.add(routePayload);
        }

        List<Map<String, Object>> historyPayload = new ArrayList<>();
        for (DeliveryHistoryRow row : safeHistory) {
            if (row == null) {
                continue;
            }
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("event_id", normalize(row.eventId()));
            item.put("route_id", row.routeId());
            item.put("event_type", normalize(row.eventType()));
            item.put("route_type", normalize(row.routeType()));
            item.put("route_target", normalize(row.routeTarget()));
            item.put("status", normalize(row.status()));
            item.put("attempt_count", row.attemptCount());
            item.put("last_error", normalize(row.lastError()));
            item.put("error_kind", classifyError(row.lastError()));
            item.put("available_at", normalize(row.availableAt()));
            item.put("processing_started_at", normalize(row.processingStartedAt()));
            item.put("delivered_at", normalize(row.deliveredAt()));
            item.put("created_at", normalize(row.createdAt()));
            item.put("updated_at", normalize(row.updatedAt()));
            item.put("requested_by", normalize(row.requestedBy()));
            historyPayload.add(item);
        }

        boolean hasFailed = totalStats.failed > 0L || hasCurrentFailedRoute;
        boolean hasPending = totalStats.pending() > 0L || hasCurrentPendingRoute;
        String overallStatus = hasFailed
            ? "degraded"
            : hasPending
                ? "pending"
                : totalStats.terminal() > 0L
                    ? "healthy"
                    : "idle";

        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("route_count", safeRoutes.size());
        summary.put("events_24h_count", totalStats.total());
        summary.put("delivered_24h_count", totalStats.delivered);
        summary.put("failed_24h_count", totalStats.failed);
        summary.put("pending_24h_count", totalStats.pending());
        summary.put("success_rate_24h", successRate(totalStats.delivered, totalStats.failed));
        summary.put("overall_status", overallStatus);

        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("incident_id", incident == null ? null : incident.getId());
        payload.put("incident_key", incident == null ? null : normalize(incident.getIncidentKey()));
        payload.put("window_hours", WINDOW_HOURS);
        payload.put("history_limit", HISTORY_LIMIT);
        payload.put("generated_at", (generatedAt == null ? OffsetDateTime.now(ZoneOffset.UTC) : generatedAt).toString());
        payload.put("summary", summary);
        payload.put("routes", routePayloads);
        payload.put("history", historyPayload);
        return payload;
    }

    private DeliveryHistoryRow mapHistoryRow(ResultSet rs,
                                             int rowNum) throws SQLException {
        return new DeliveryHistoryRow(
            rs.getString("event_id"),
            rs.getLong("route_id"),
            rs.getString("event_type"),
            rs.getString("route_type"),
            rs.getString("route_target"),
            rs.getString("status"),
            rs.getInt("attempt_count"),
            rs.getString("last_error"),
            rs.getString("available_at"),
            rs.getString("processing_started_at"),
            rs.getString("delivered_at"),
            rs.getString("created_at"),
            rs.getString("updated_at"),
            rs.getString("requested_by")
        );
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

    private String routeHealth(String currentStatus,
                               MutableStats stats) {
        if ("failed".equalsIgnoreCase(currentStatus)) {
            return "failed";
        }
        if ("queued".equalsIgnoreCase(currentStatus) || "processing".equalsIgnoreCase(currentStatus)) {
            return "pending";
        }
        if (stats.failed > 0L) {
            return "degraded";
        }
        if (stats.terminal() > 0L || "delivered".equalsIgnoreCase(currentStatus)) {
            return "healthy";
        }
        return "idle";
    }

    String classifyError(String error) {
        String normalized = normalize(error);
        if (normalized == null) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        if (lowered.contains("webhook responded with status=")) {
            return "webhook_http";
        }
        if (lowered.contains("i/o failure while delivering incident webhook")) {
            return "webhook_io";
        }
        if (lowered.contains("invalid incident webhook url")) {
            return "webhook_invalid_url";
        }
        if (lowered.contains("has no active recipients") || lowered.contains("users target is empty")) {
            return "empty_recipients";
        }
        if (lowered.contains("unsupported incident route type")) {
            return "unsupported_route";
        }
        return "delivery_error";
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    record RouteStatusCount(long routeId, String status, long total) {
    }

    record DeliveryHistoryRow(String eventId,
                              long routeId,
                              String eventType,
                              String routeType,
                              String routeTarget,
                              String status,
                              int attemptCount,
                              String lastError,
                              String availableAt,
                              String processingStartedAt,
                              String deliveredAt,
                              String createdAt,
                              String updatedAt,
                              String requestedBy) {
    }

    private static final class MutableStats {
        private long delivered;
        private long failed;
        private long queued;
        private long processing;
        private long other;

        private void add(String status,
                         long count) {
            String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
            switch (normalized) {
                case "delivered" -> delivered += count;
                case "failed" -> failed += count;
                case "queued" -> queued += count;
                case "processing" -> processing += count;
                default -> other += count;
            }
        }

        private long pending() {
            return queued + processing;
        }

        private long terminal() {
            return delivered + failed;
        }

        private long total() {
            return delivered + failed + queued + processing + other;
        }
    }
}
package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRouteRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@RuntimeWorkload(
    id = "incident-route-delivery-outbox-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Service
public class IncidentRouteDeliveryOutboxService {

    private static final Logger log = LoggerFactory.getLogger(IncidentRouteDeliveryOutboxService.class);
    private static final Duration DISPATCH_LEASE_TTL = Duration.ofSeconds(45);
    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final IncidentRouteRepository incidentRouteRepository;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final IncidentRouteDeliveryService incidentRouteDeliveryService;

    public IncidentRouteDeliveryOutboxService(JdbcTemplate jdbcTemplate,
                                              ObjectMapper objectMapper,
                                              IncidentRouteRepository incidentRouteRepository,
                                              RuntimeCoordinationService runtimeCoordinationService,
                                              IncidentRouteDeliveryService incidentRouteDeliveryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.incidentRouteRepository = incidentRouteRepository;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.incidentRouteDeliveryService = incidentRouteDeliveryService;
    }

    public int enqueueIncidentRoutes(Incident incident,
                                     String eventType,
                                     String eventText,
                                     Object payload,
                                     String actor) {
        if (incident == null || incident.getId() == null) {
            return 0;
        }
        List<IncidentRoute> routes = incidentRouteRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incident.getId());
        int queued = 0;
        for (IncidentRoute route : routes) {
            if (route == null || route.getId() == null) {
                continue;
            }
            enqueueRoute(incident, route, eventType, eventText, payload, actor);
            queued++;
        }
        return queued;
    }

    public int enqueueRouteReplay(Incident incident,
                                  Long routeId,
                                  String actor) {
        if (incident == null || incident.getId() == null || routeId == null) {
            return 0;
        }
        IncidentRoute route = incidentRouteRepository.findById(routeId)
            .filter(item -> item.getIncident() != null && Objects.equals(item.getIncident().getId(), incident.getId()))
            .orElse(null);
        if (route == null) {
            return 0;
        }
        enqueueRoute(incident, route, "manual_route_redelivery", "Manual route redelivery requested", Map.of(
            "incident_id", incident.getId(),
            "route_id", routeId
        ), actor);
        dispatchBatch();
        return 1;
    }

    public int enqueueFailedRouteReplays(Incident incident,
                                         int limit,
                                         String actor) {
        if (incident == null || incident.getId() == null) {
            return 0;
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Timestamp staleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(STALE_PROCESSING_TIMEOUT));
        List<Long> routeIds = jdbcTemplate.query("""
                SELECT route_id
                  FROM incident_route_delivery_outbox
                 WHERE incident_id = ?
                   AND (
                        status = 'failed'
                        OR (status = 'processing'
                            AND processing_started_at IS NOT NULL
                            AND processing_started_at < ?)
                   )
                 ORDER BY updated_at ASC, event_id ASC
                """,
            (rs, rowNum) -> rs.getLong("route_id"),
            incident.getId(),
            staleThreshold
        );
        List<Long> uniqueRouteIds = new ArrayList<>();
        for (Long routeId : routeIds) {
            if (routeId == null || uniqueRouteIds.contains(routeId)) {
                continue;
            }
            uniqueRouteIds.add(routeId);
            if (uniqueRouteIds.size() >= safeLimit) {
                break;
            }
        }
        int queued = 0;
        for (Long routeId : uniqueRouteIds) {
            queued += enqueueRouteReplay(incident, routeId, actor);
        }
        return queued;
    }

    @Scheduled(fixedDelayString = "${panel.incidents.route-delivery.dispatch-interval-ms:3000}")
    public void dispatchScheduled() {
        runtimeCoordinationService.runWithLease(
            "incident-route-delivery-dispatch",
            DISPATCH_LEASE_TTL,
            this::dispatchBatch
        );
    }

    void dispatchBatch() {
        recoverStaleProcessing();
        List<String> eventIds = jdbcTemplate.query("""
                SELECT event_id
                  FROM incident_route_delivery_outbox
                 WHERE status IN ('queued', 'failed')
                   AND (available_at IS NULL OR available_at <= CURRENT_TIMESTAMP)
                 ORDER BY created_at ASC, event_id ASC
                 LIMIT ?
                """,
            (rs, rowNum) -> rs.getString("event_id"),
            BATCH_SIZE
        );
        for (String eventId : eventIds) {
            if (!claim(eventId)) {
                continue;
            }
            DeliveryEntry entry = load(eventId);
            if (entry == null) {
                continue;
            }
            try {
                incidentRouteDeliveryService.deliver(
                    entry.routeType(),
                    entry.routeTarget(),
                    entry.messageText(),
                    entry.incidentUrl(),
                    parsePayload(entry.payloadJson())
                );
                markDelivered(entry);
            } catch (Exception ex) {
                markFailed(entry, ex);
            }
        }
    }

    public Map<Long, Map<String, Object>> loadLatestRouteDeliverySnapshots(Long incidentId) {
        if (incidentId == null) {
            return Map.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.query("""
                SELECT route_id,
                       event_id,
                       status,
                       attempt_count,
                       last_error,
                       updated_at,
                       delivered_at
                  FROM incident_route_delivery_outbox
                 WHERE incident_id = ?
                 ORDER BY created_at DESC, event_id DESC
                """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("route_id", rs.getLong("route_id"));
                row.put("event_id", rs.getString("event_id"));
                row.put("status", rs.getString("status"));
                row.put("attempt_count", rs.getInt("attempt_count"));
                row.put("last_error", normalize(rs.getString("last_error")));
                row.put("updated_at", normalize(rs.getString("updated_at")));
                row.put("delivered_at", normalize(rs.getString("delivered_at")));
                return row;
            },
            incidentId
        );
        LinkedHashMap<Long, Map<String, Object>> snapshots = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long routeId = row.get("route_id") instanceof Number number ? number.longValue() : null;
            if (routeId == null || snapshots.containsKey(routeId)) {
                continue;
            }
            snapshots.put(routeId, row);
        }
        return snapshots;
    }

    private void enqueueRoute(Incident incident,
                              IncidentRoute route,
                              String eventType,
                              String eventText,
                              Object payload,
                              String actor) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String eventId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO incident_route_delivery_outbox (
                    event_id,
                    incident_id,
                    route_id,
                    event_type,
                    route_type,
                    route_target,
                    message_text,
                    incident_url,
                    payload_json,
                    requested_by,
                    status,
                    attempt_count,
                    available_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            eventId,
            incident.getId(),
            route.getId(),
            normalize(eventType),
            normalize(route.getRouteType()),
            route.getRouteTarget().trim(),
            buildMessageText(incident, eventText),
            buildIncidentUrl(incident),
            toJson(buildPayload(incident, route, eventType, eventText, payload, actor)),
            normalize(actor),
            "queued",
            0,
            timestamp(now),
            timestamp(now),
            timestamp(now)
        );
        route.setRouteStatus("queued");
        route.setUpdatedAt(now);
        incidentRouteRepository.save(route);
    }

    private Map<String, Object> buildPayload(Incident incident,
                                             IncidentRoute route,
                                             String eventType,
                                             String eventText,
                                             Object payload,
                                             String actor) {
        LinkedHashMap<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("incident_id", incident.getId());
        envelope.put("incident_key", incident.getIncidentKey());
        envelope.put("title", incident.getTitle());
        envelope.put("summary", normalize(incident.getSummary()));
        envelope.put("description", normalize(incident.getDescription()));
        envelope.put("status", normalize(incident.getStatus()));
        envelope.put("severity", normalize(incident.getSeverity()));
        envelope.put("source", normalize(incident.getSource()));
        envelope.put("owner", normalize(incident.getOwner()));
        envelope.put("signal_type", normalize(incident.getSignalType()));
        envelope.put("signal_key", normalize(incident.getSignalKey()));
        envelope.put("event_type", normalize(eventType));
        envelope.put("event_text", normalize(eventText));
        envelope.put("route_id", route.getId());
        envelope.put("route_type", normalize(route.getRouteType()));
        envelope.put("route_target", normalize(route.getRouteTarget()));
        envelope.put("route_note", normalize(route.getNote()));
        envelope.put("incident_url", buildIncidentUrl(incident));
        envelope.put("requested_by", normalize(actor));
        envelope.put("payload", payload);
        return envelope;
    }

    private String buildMessageText(Incident incident,
                                    String eventText) {
        String incidentKey = normalize(incident.getIncidentKey());
        String title = normalize(incident.getTitle());
        String summary = normalize(eventText);
        if (StringUtils.hasText(summary)) {
            return incidentKey + " | " + title + " | " + summary;
        }
        return incidentKey + " | " + title;
    }

    private String buildIncidentUrl(Incident incident) {
        String incidentKey = normalize(incident.getIncidentKey());
        if (!StringUtils.hasText(incidentKey)) {
            return "/dialogs";
        }
        return "/dialogs?incidentId=" + incidentKey;
    }

    private void recoverStaleProcessing() {
        Timestamp staleThreshold = timestamp(OffsetDateTime.now(ZoneOffset.UTC).minus(STALE_PROCESSING_TIMEOUT));
        jdbcTemplate.update("""
                UPDATE incident_route_delivery_outbox
                   SET status = 'queued',
                       processing_started_at = NULL,
                       available_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE status = 'processing'
                   AND processing_started_at IS NOT NULL
                   AND processing_started_at < ?
                """,
            staleThreshold
        );
    }

    private boolean claim(String eventId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return jdbcTemplate.update("""
                UPDATE incident_route_delivery_outbox
                   SET status = 'processing',
                       attempt_count = attempt_count + 1,
                       processing_started_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                   AND status IN ('queued', 'failed')
                   AND (available_at IS NULL OR available_at <= ?)
                """,
            timestamp(now),
            timestamp(now),
            eventId,
            timestamp(now)
        ) > 0;
    }

    private DeliveryEntry load(String eventId) {
        List<DeliveryEntry> rows = jdbcTemplate.query("""
                SELECT event_id,
                       incident_id,
                       route_id,
                       event_type,
                       route_type,
                       route_target,
                       message_text,
                       incident_url,
                       payload_json,
                       attempt_count
                  FROM incident_route_delivery_outbox
                 WHERE event_id = ?
                """,
            this::mapEntry,
            eventId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private DeliveryEntry mapEntry(ResultSet rs,
                                   int rowNum) throws SQLException {
        return new DeliveryEntry(
            rs.getString("event_id"),
            rs.getLong("incident_id"),
            rs.getLong("route_id"),
            rs.getString("event_type"),
            rs.getString("route_type"),
            rs.getString("route_target"),
            rs.getString("message_text"),
            rs.getString("incident_url"),
            rs.getString("payload_json"),
            rs.getInt("attempt_count")
        );
    }

    private void markDelivered(DeliveryEntry entry) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE incident_route_delivery_outbox
                   SET status = 'delivered',
                       last_error = NULL,
                       processing_started_at = NULL,
                       delivered_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            timestamp(now),
            timestamp(now),
            entry.eventId()
        );
        incidentRouteRepository.findById(entry.routeId()).ifPresent(route -> {
            route.setRouteStatus("delivered");
            route.setUpdatedAt(now);
            incidentRouteRepository.save(route);
        });
    }

    private void markFailed(DeliveryEntry entry,
                            Exception exception) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime retryAt = now.plusSeconds(Math.min(300L, Math.max(10L, entry.attemptCount() * 10L)));
        jdbcTemplate.update("""
                UPDATE incident_route_delivery_outbox
                   SET status = 'failed',
                       last_error = ?,
                       processing_started_at = NULL,
                       available_at = ?,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            truncateError(exception),
            timestamp(retryAt),
            timestamp(now),
            entry.eventId()
        );
        incidentRouteRepository.findById(entry.routeId()).ifPresent(route -> {
            route.setRouteStatus("failed");
            route.setUpdatedAt(now);
            incidentRouteRepository.save(route);
        });
        log.warn("Failed to deliver incident route event {} for incident {} route {}: {}",
            entry.eventId(), entry.incidentId(), entry.routeId(), exception.getMessage());
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize incident route payload.", ex);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize incident route payload.", ex);
        }
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }

    private record DeliveryEntry(String eventId,
                                 long incidentId,
                                 long routeId,
                                 String eventType,
                                 String routeType,
                                 String routeTarget,
                                 String messageText,
                                 String incidentUrl,
                                 String payloadJson,
                                 int attemptCount) {
    }
}

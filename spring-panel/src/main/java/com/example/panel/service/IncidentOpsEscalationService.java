package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentEvent;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentEventRepository;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@RuntimeWorkload(
    id = "incident-ops-escalation-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Service
public class IncidentOpsEscalationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentOpsEscalationService.class);
    private static final Collection<String> TERMINAL_STATUSES = Set.of("resolved", "closed");
    private static final String ACTOR = "system";
    private static final Set<String> POLICIES = Set.of("critical", "aged", "route_delivery_failed");
    private static final int DEFAULT_MUTE_MINUTES = 60;
    private static final int MIN_MUTE_MINUTES = 5;
    private static final int MAX_MUTE_MINUTES = 1440;

    private final IncidentRepository incidentRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final ObjectMapper objectMapper;
    private final IncidentService incidentService;
    private final RuntimeCoordinationService runtimeCoordinationService;
    private final boolean enabled;
    private final Duration agingThreshold;
    private final Duration criticalCooldown;
    private final Duration agingCooldown;
    private final Duration failedRouteCooldown;

    public IncidentOpsEscalationService(
        IncidentRepository incidentRepository,
        IncidentRouteRepository incidentRouteRepository,
        IncidentEventRepository incidentEventRepository,
        ObjectMapper objectMapper,
        IncidentService incidentService,
        RuntimeCoordinationService runtimeCoordinationService,
        @Value("${panel.incidents.escalation.enabled:true}") boolean enabled,
        @Value("${panel.incidents.escalation.aging-threshold-minutes:60}") long agingThresholdMinutes,
        @Value("${panel.incidents.escalation.critical-cooldown-minutes:30}") long criticalCooldownMinutes,
        @Value("${panel.incidents.escalation.aging-cooldown-minutes:120}") long agingCooldownMinutes,
        @Value("${panel.incidents.escalation.failed-route-cooldown-minutes:30}") long failedRouteCooldownMinutes
    ) {
        this.incidentRepository = incidentRepository;
        this.incidentRouteRepository = incidentRouteRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.objectMapper = objectMapper;
        this.incidentService = incidentService;
        this.runtimeCoordinationService = runtimeCoordinationService;
        this.enabled = enabled;
        this.agingThreshold = durationMinutes(agingThresholdMinutes, 60L);
        this.criticalCooldown = durationMinutes(criticalCooldownMinutes, 30L);
        this.agingCooldown = durationMinutes(agingCooldownMinutes, 120L);
        this.failedRouteCooldown = durationMinutes(failedRouteCooldownMinutes, 30L);
    }

    @Scheduled(fixedDelayString = "${panel.incidents.escalation.interval-ms:300000}")
    public void evaluateScheduled() {
        if (!enabled) {
            return;
        }
        runtimeCoordinationService.runWithLease(
            "incident-ops-escalation",
            Duration.ofMinutes(4),
            this::evaluateOnce
        );
    }

    void evaluateOnce() {
        evaluateOnce(OffsetDateTime.now(ZoneOffset.UTC));
    }

    void evaluateOnce(OffsetDateTime now) {
        OffsetDateTime safeNow = now == null ? OffsetDateTime.now(ZoneOffset.UTC) : now;
        Set<Long> criticalIncidentIds = new LinkedHashSet<>();

        List<Incident> criticalIncidents = incidentRepository
            .findTop100BySeverityAndStatusNotInOrderByCreatedAtAscIdAsc("critical", TERMINAL_STATUSES);
        for (Incident incident : criticalIncidents) {
            if (!isActive(incident)) {
                continue;
            }
            criticalIncidentIds.add(incident.getId());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("severity", incident.getSeverity());
            details.put("status", incident.getStatus());
            details.put("age_minutes", ageMinutes(incident, safeNow));
            tryEscalate(
                incident,
                "critical",
                "Критический incident требует внимания.",
                details,
                criticalCooldown
            );
        }

        OffsetDateTime agingBefore = safeNow.minus(agingThreshold);
        List<Incident> agedIncidents = incidentRepository
            .findTop100ByCreatedAtBeforeAndStatusNotInOrderByCreatedAtAscIdAsc(agingBefore, TERMINAL_STATUSES);
        for (Incident incident : agedIncidents) {
            if (!isActive(incident) || criticalIncidentIds.contains(incident.getId())) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("severity", incident.getSeverity());
            details.put("status", incident.getStatus());
            details.put("age_minutes", ageMinutes(incident, safeNow));
            details.put("threshold_minutes", agingThreshold.toMinutes());
            tryEscalate(
                incident,
                "aged",
                "Incident остаётся активным дольше порога " + agingThreshold.toMinutes() + " мин.",
                details,
                agingCooldown
            );
        }

        Map<Long, FailedRouteState> failedRoutesByIncident = new LinkedHashMap<>();
        for (IncidentRoute route : incidentRouteRepository.findTop200ByRouteStatusOrderByUpdatedAtAscIdAsc("failed")) {
            Incident incident = route == null ? null : route.getIncident();
            if (!isActive(incident)) {
                continue;
            }
            failedRoutesByIncident.computeIfAbsent(
                incident.getId(),
                ignored -> new FailedRouteState(incident)
            ).addRoute(route);
        }
        for (FailedRouteState failedState : failedRoutesByIncident.values()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("failed_route_count", failedState.routeIds.size());
            details.put("failed_route_ids", List.copyOf(failedState.routeIds));
            details.put("status", failedState.incident.getStatus());
            details.put("severity", failedState.incident.getSeverity());
            tryEscalate(
                failedState.incident,
                "route_delivery_failed",
                "Есть ошибки доставки incident alert по " + failedState.routeIds.size() + " маршрут(ам).",
                details,
                failedRouteCooldown
            );
        }
    }

    public Map<String, Object> controlState(Long incidentId) {
        Incident incident = requireIncidentForControl(incidentId);
        Map<String, OffsetDateTime> latestEscalations = loadLatestEscalations(incidentId);

        List<Map<String, Object>> policies = List.of(
            policyState(
                incidentId,
                "critical",
                "Критический",
                criticalCooldown,
                null,
                latestEscalations.get("critical")
            ),
            policyState(
                incidentId,
                "aged",
                "Дольше порога",
                agingCooldown,
                agingThreshold,
                latestEscalations.get("aged")
            ),
            policyState(
                incidentId,
                "route_delivery_failed",
                "Ошибка доставки",
                failedRouteCooldown,
                null,
                latestEscalations.get("route_delivery_failed")
            )
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("enabled", enabled);
        payload.put("incident_id", incidentId);
        payload.put("incident_active", isActive(incident));
        payload.put("incident_status", incident.getStatus());
        payload.put("incident_severity", incident.getSeverity());
        payload.put("policies", policies);
        return payload;
    }

    public Map<String, Object> mutePolicy(Long incidentId,
                                          String rawPolicy,
                                          Object rawMinutes,
                                          String actor) {
        Incident incident = requireIncidentForControl(incidentId);
        if (!isActive(incident)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя приглушить эскалацию завершённого incident.");
        }
        String policy = normalizePolicy(rawPolicy);
        long minutes = normalizeMuteMinutes(rawMinutes);
        runtimeCoordinationService.refreshCooldown(muteKey(policy, incidentId), Duration.ofMinutes(minutes));
        incidentService.recordEscalationControlEvent(
            incidentId,
            "escalation_muted",
            "Эскалация " + policy + " приглушена на " + minutes + " мин.",
            Map.of("policy", policy, "mute_minutes", minutes),
            actor
        );
        return controlState(incidentId);
    }

    public Map<String, Object> unmutePolicy(Long incidentId,
                                            String rawPolicy,
                                            String actor) {
        requireIncidentForControl(incidentId);
        String policy = normalizePolicy(rawPolicy);
        String key = muteKey(policy, incidentId);
        long remainingBeforeClear = runtimeCoordinationService.cooldownRemainingSeconds(key);
        runtimeCoordinationService.clearCooldown(key);
        if (remainingBeforeClear > 0L) {
            incidentService.recordEscalationControlEvent(
                incidentId,
                "escalation_unmuted",
                "Mute эскалации " + policy + " снят оператором.",
                Map.of("policy", policy, "remaining_seconds_before_clear", remainingBeforeClear),
                actor
            );
        }
        return controlState(incidentId);
    }

    private Map<String, Object> policyState(Long incidentId,
                                            String policy,
                                            String label,
                                            Duration cooldown,
                                            Duration threshold,
                                            OffsetDateTime lastEscalatedAt) {
        long muteRemainingSeconds = runtimeCoordinationService.cooldownRemainingSeconds(muteKey(policy, incidentId));
        long cooldownRemainingSeconds = runtimeCoordinationService.cooldownRemainingSeconds(cooldownKey(policy, incidentId));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policy", policy);
        payload.put("label", label);
        payload.put("cooldown_minutes", cooldown.toMinutes());
        payload.put("cooldown_remaining_seconds", cooldownRemainingSeconds);
        payload.put("muted", muteRemainingSeconds > 0L);
        payload.put("mute_remaining_seconds", muteRemainingSeconds);
        payload.put("last_escalated_at", lastEscalatedAt == null ? null : lastEscalatedAt.toString());
        if (threshold != null) {
            payload.put("threshold_minutes", threshold.toMinutes());
        }
        return payload;
    }

    private Map<String, OffsetDateTime> loadLatestEscalations(Long incidentId) {
        Map<String, OffsetDateTime> latest = new LinkedHashMap<>();
        List<IncidentEvent> events = incidentEventRepository.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
        for (int index = events.size() - 1; index >= 0 && latest.size() < POLICIES.size(); index--) {
            IncidentEvent event = events.get(index);
            String policy = extractEscalationPolicy(event);
            if (policy == null || latest.containsKey(policy)) {
                continue;
            }
            latest.put(policy, event.getCreatedAt());
        }
        return latest;
    }

    private String extractEscalationPolicy(IncidentEvent event) {
        if (event == null || !"escalation".equalsIgnoreCase(String.valueOf(event.getEventType()))) {
            return null;
        }
        String payloadJson = event.getPayloadJson();
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            String rawPolicy = payload.path("policy").asText("");
            String normalized = rawPolicy.trim().toLowerCase(Locale.ROOT);
            return POLICIES.contains(normalized) ? normalized : null;
        } catch (Exception ex) {
            log.debug("Unable to read escalation policy from incident event {}: {}", event.getId(), ex.getMessage());
            return null;
        }
    }

    private Incident requireIncidentForControl(Long incidentId) {
        if (incidentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите incident id.");
        }
        return incidentRepository.findById(incidentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident не найден"));
    }

    private String normalizePolicy(String rawPolicy) {
        String normalized = rawPolicy == null ? "" : rawPolicy.trim().toLowerCase(Locale.ROOT);
        if (!POLICIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неподдерживаемая escalation policy: " + normalized);
        }
        return normalized;
    }

    private long normalizeMuteMinutes(Object rawMinutes) {
        if (rawMinutes == null) {
            return DEFAULT_MUTE_MINUTES;
        }
        long parsed;
        try {
            parsed = rawMinutes instanceof Number number
                ? number.longValue()
                : Long.parseLong(String.valueOf(rawMinutes).trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mute duration должна быть числом минут.");
        }
        return Math.max(MIN_MUTE_MINUTES, Math.min(MAX_MUTE_MINUTES, parsed));
    }

    private String muteKey(String policy, Long incidentId) {
        return "incident-ops-escalation-mute:" + policy + ":" + incidentId;
    }

    private String cooldownKey(String policy, Long incidentId) {
        return "incident-ops-escalation:" + policy + ":" + incidentId;
    }
    private void tryEscalate(Incident incident,
                             String policy,
                             String message,
                             Map<String, Object> details,
                             Duration cooldown) {
        if (!isActive(incident)) {
            return;
        }
        if (runtimeCoordinationService.isCooldownActive(muteKey(policy, incident.getId()))) {
            return;
        }
        String cooldownKey = cooldownKey(policy, incident.getId());
        if (!runtimeCoordinationService.tryAcquireCooldown(cooldownKey, cooldown)) {
            return;
        }
        try {
            incidentService.escalateIncident(
                incident.getId(),
                policy,
                message,
                details,
                ACTOR
            );
        } catch (RuntimeException ex) {
            log.warn(
                "Unable to escalate incident {} with policy {}: {}",
                incident.getId(),
                policy,
                ex.getMessage()
            );
        }
    }

    private boolean isActive(Incident incident) {
        return incident != null
            && incident.getId() != null
            && !TERMINAL_STATUSES.contains(String.valueOf(incident.getStatus()));
    }

    private long ageMinutes(Incident incident, OffsetDateTime now) {
        if (incident == null || incident.getCreatedAt() == null || now == null || incident.getCreatedAt().isAfter(now)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(incident.getCreatedAt(), now).toMinutes());
    }

    private static Duration durationMinutes(long value, long fallback) {
        long safeValue = value > 0L ? value : fallback;
        return Duration.ofMinutes(safeValue);
    }

    private static final class FailedRouteState {
        private final Incident incident;
        private final LinkedHashSet<Long> routeIds = new LinkedHashSet<>();

        private FailedRouteState(Incident incident) {
            this.incident = incident;
        }

        private void addRoute(IncidentRoute route) {
            if (route != null && route.getId() != null) {
                routeIds.add(route.getId());
            }
        }
    }
}

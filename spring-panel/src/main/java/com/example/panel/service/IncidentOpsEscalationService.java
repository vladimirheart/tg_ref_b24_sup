package com.example.panel.service;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class IncidentOpsEscalationService {

    private static final Logger log = LoggerFactory.getLogger(IncidentOpsEscalationService.class);
    private static final Collection<String> TERMINAL_STATUSES = Set.of("resolved", "closed");
    private static final String ACTOR = "system";

    private final IncidentRepository incidentRepository;
    private final IncidentRouteRepository incidentRouteRepository;
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

    private void tryEscalate(Incident incident,
                             String policy,
                             String message,
                             Map<String, Object> details,
                             Duration cooldown) {
        if (!isActive(incident)) {
            return;
        }
        String cooldownKey = "incident-ops-escalation:" + policy + ":" + incident.getId();
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

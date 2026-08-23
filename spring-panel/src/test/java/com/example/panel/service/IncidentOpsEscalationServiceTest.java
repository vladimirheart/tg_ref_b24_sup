package com.example.panel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import com.example.panel.repository.IncidentRepository;
import com.example.panel.repository.IncidentRouteRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentOpsEscalationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentRouteRepository incidentRouteRepository;
    @Mock
    private IncidentService incidentService;
    @Mock
    private RuntimeCoordinationService runtimeCoordinationService;

    private IncidentOpsEscalationService service;

    @BeforeEach
    void setUp() {
        service = new IncidentOpsEscalationService(
            incidentRepository,
            incidentRouteRepository,
            incidentService,
            runtimeCoordinationService,
            true,
            60L,
            30L,
            120L,
            30L
        );
    }

    @Test
    void evaluateOnceEscalatesCriticalAgedAndFailedRoutePolicies() {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 23, 21, 0, 0, 0, ZoneOffset.UTC);
        Incident critical = incident(1L, "critical", "open", now.minusMinutes(20));
        Incident aged = incident(2L, "high", "investigating", now.minusMinutes(180));
        Incident routeFailed = incident(3L, "medium", "acknowledged", now.minusMinutes(15));
        IncidentRoute failedRouteA = route(31L, routeFailed);
        IncidentRoute failedRouteB = route(32L, routeFailed);

        when(incidentRepository.findTop100BySeverityAndStatusNotInOrderByCreatedAtAscIdAsc(eq("critical"), any()))
            .thenReturn(List.of(critical));
        when(incidentRepository.findTop100ByCreatedAtBeforeAndStatusNotInOrderByCreatedAtAscIdAsc(any(OffsetDateTime.class), any()))
            .thenReturn(List.of(aged));
        when(incidentRouteRepository.findTop200ByRouteStatusOrderByUpdatedAtAscIdAsc("failed"))
            .thenReturn(List.of(failedRouteA, failedRouteB));
        when(runtimeCoordinationService.tryAcquireCooldown(any(), any(Duration.class))).thenReturn(true);
        when(incidentService.escalateIncident(any(), any(), any(), anyMap(), eq("system"))).thenReturn(true);

        service.evaluateOnce(now);

        verify(incidentService).escalateIncident(eq(1L), eq("critical"), any(), anyMap(), eq("system"));
        verify(incidentService).escalateIncident(eq(2L), eq("aged"), any(), anyMap(), eq("system"));
        verify(incidentService).escalateIncident(eq(3L), eq("route_delivery_failed"), any(), anyMap(), eq("system"));
    }

    @Test
    void criticalIncidentIsNotEscalatedAgainAsAgedAndCooldownSuppressesDelivery() {
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 23, 21, 0, 0, 0, ZoneOffset.UTC);
        Incident criticalAndAged = incident(7L, "critical", "open", now.minusMinutes(240));

        when(incidentRepository.findTop100BySeverityAndStatusNotInOrderByCreatedAtAscIdAsc(eq("critical"), any()))
            .thenReturn(List.of(criticalAndAged));
        when(incidentRepository.findTop100ByCreatedAtBeforeAndStatusNotInOrderByCreatedAtAscIdAsc(any(OffsetDateTime.class), any()))
            .thenReturn(List.of(criticalAndAged));
        when(incidentRouteRepository.findTop200ByRouteStatusOrderByUpdatedAtAscIdAsc("failed"))
            .thenReturn(List.of());
        when(runtimeCoordinationService.tryAcquireCooldown(
            eq("incident-ops-escalation:critical:7"),
            eq(Duration.ofMinutes(30))
        )).thenReturn(false);

        service.evaluateOnce(now);

        verify(incidentService, never()).escalateIncident(any(), any(), any(), anyMap(), any());
        verify(runtimeCoordinationService, never()).tryAcquireCooldown(
            eq("incident-ops-escalation:aged:7"),
            any(Duration.class)
        );
    }

    private Incident incident(Long id,
                              String severity,
                              String status,
                              OffsetDateTime createdAt) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setIncidentKey("INC-" + id);
        incident.setTitle("Incident " + id);
        incident.setSeverity(severity);
        incident.setStatus(status);
        incident.setCreatedAt(createdAt);
        return incident;
    }

    private IncidentRoute route(Long id, Incident incident) {
        IncidentRoute route = new IncidentRoute();
        route.setId(id);
        route.setIncident(incident);
        route.setRouteStatus("failed");
        return route;
    }
}

package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.AlertmanagerIngestionProperties;
import com.example.panel.model.observability.AlertmanagerWebhookPayload;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertmanagerIngestionServiceTest {

    private IncidentService incidentService;
    private RuntimeCoordinationService coordinationService;
    private AlertmanagerIngestionProperties properties;
    private AlertmanagerIngestionService service;

    @BeforeEach
    void setUp() {
        incidentService = mock(IncidentService.class);
        coordinationService = mock(RuntimeCoordinationService.class);
        properties = new AlertmanagerIngestionProperties();
        properties.setLeaseTtl(Duration.ofSeconds(30));

        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(anyString(), any(Duration.class), any(Runnable.class));

        service = new AlertmanagerIngestionService(
            properties,
            incidentService,
            coordinationService,
            new SimpleMeterRegistry()
        );
    }

    @Test
    void firingHighAlertCreatesSignalIncidentWithApprovedRoute() {
        when(incidentService.listIncidentSummariesForSignal("alertmanager", "fp-1"))
            .thenReturn(List.of());

        Map<String, Object> result = service.ingest(payload("firing", "high", "fp-1"));

        assertThat(result)
            .containsEntry("processed", 1)
            .containsEntry("firing", 1)
            .containsEntry("deduplicated", 0);

        verify(incidentService).openOrRefreshSignalIncident(
            eq("alertmanager"),
            eq("fp-1"),
            anyString(),
            anyString(),
            anyString(),
            eq("high"),
            eq("alertmanager"),
            any(),
            eq("alertmanager"),
            argThat(routes ->
                routes != null
                    && routes.size() == 1
                    && "all_operators".equals(routes.get(0).get("route_type"))
                    && "all_operators".equals(routes.get(0).get("route_target"))
            )
        );
    }

    @Test
    void repeatedFiringAlertIsDeduplicatedByActiveSignalIncident() {
        when(incidentService.listIncidentSummariesForSignal("alertmanager", "fp-2"))
            .thenReturn(List.of(Map.of("status", "investigating")));

        Map<String, Object> result = service.ingest(payload("firing", "critical", "fp-2"));

        assertThat(result)
            .containsEntry("processed", 1)
            .containsEntry("deduplicated", 1);
        verify(incidentService, never()).openOrRefreshSignalIncident(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), any(), anyString(), any()
        );
    }

    @Test
    void resolvedAlertResolvesActiveSignalIncidentExactlyOnce() {
        when(incidentService.listIncidentSummariesForSignal("alertmanager", "fp-3"))
            .thenReturn(List.of(Map.of("status", "open")));

        Map<String, Object> result = service.ingest(payload("resolved", "high", "fp-3"));

        assertThat(result)
            .containsEntry("processed", 1)
            .containsEntry("resolved", 1);
        verify(incidentService).resolveSignalIncident(
            eq("alertmanager"),
            eq("fp-3"),
            anyString(),
            any(),
            eq("alertmanager")
        );
    }

    @Test
    void mediumAlertIsIgnoredDefensively() {
        Map<String, Object> result = service.ingest(payload("firing", "medium", "fp-4"));

        assertThat(result)
            .containsEntry("processed", 0)
            .containsEntry("ignored", 1);
        verify(incidentService, never()).listIncidentSummariesForSignal(anyString(), anyString());
    }

    private AlertmanagerWebhookPayload payload(String status, String severity, String fingerprint) {
        return new AlertmanagerWebhookPayload(
            "4",
            "{}:{alertname=\"IguanaSmoke\"}",
            0,
            status,
            "iguana-internal",
            Map.of("alertname", "IguanaSmoke", "severity", severity),
            Map.of(),
            Map.of("alertname", "IguanaSmoke", "severity", severity, "service", "test"),
            Map.of("summary", "Synthetic Alertmanager test"),
            "http://alertmanager:9093",
            "test",
            List.of(new AlertmanagerWebhookPayload.Alert(
                status,
                Map.of(
                    "alertname", "IguanaSmoke",
                    "severity", severity,
                    "service", "test"
                ),
                Map.of(
                    "summary", "Synthetic Alertmanager test",
                    "description", "Synthetic description"
                ),
                "2026-08-27T00:00:00Z",
                "resolved".equals(status) ? "2026-08-27T00:05:00Z" : "0001-01-01T00:00:00Z",
                "http://prometheus:9090/graph",
                fingerprint
            ))
        );
    }
}

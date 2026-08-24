package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.panel.entity.Incident;
import com.example.panel.entity.IncidentRoute;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentRouteDeliveryDiagnosticsServiceTest {

    private final IncidentRouteDeliveryDiagnosticsService service =
        new IncidentRouteDeliveryDiagnosticsService(null, null, null);

    @Test
    void assembleHealthAggregatesRoutesAndClassifiesWebhookFailure() {
        Incident incident = new Incident();
        incident.setId(42L);
        incident.setIncidentKey("INC-42");

        IncidentRoute webhook = route(1L, "webhook", "https://hooks.example.test/incident", "failed");
        IncidentRoute user = route(2L, "user", "operator@example.test", "delivered");

        List<IncidentRouteDeliveryDiagnosticsService.RouteStatusCount> counts = List.of(
            new IncidentRouteDeliveryDiagnosticsService.RouteStatusCount(1L, "delivered", 3L),
            new IncidentRouteDeliveryDiagnosticsService.RouteStatusCount(1L, "failed", 2L),
            new IncidentRouteDeliveryDiagnosticsService.RouteStatusCount(2L, "delivered", 5L),
            new IncidentRouteDeliveryDiagnosticsService.RouteStatusCount(2L, "queued", 1L)
        );

        List<IncidentRouteDeliveryDiagnosticsService.DeliveryHistoryRow> history = List.of(
            new IncidentRouteDeliveryDiagnosticsService.DeliveryHistoryRow(
                "evt-failed",
                1L,
                "incident_escalation",
                "webhook",
                "https://hooks.example.test/incident",
                "failed",
                3,
                "Incident webhook responded with status=503 body=temporarily unavailable",
                "2026-08-24 00:40:00",
                null,
                null,
                "2026-08-24 00:30:00",
                "2026-08-24 00:35:00",
                "system"
            )
        );

        Map<String, Object> result = service.assembleHealth(
            incident,
            List.of(webhook, user),
            counts,
            history,
            OffsetDateTime.of(2026, 8, 24, 0, 35, 0, 0, ZoneOffset.UTC)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        assertEquals(11L, summary.get("events_24h_count"));
        assertEquals(8L, summary.get("delivered_24h_count"));
        assertEquals(2L, summary.get("failed_24h_count"));
        assertEquals(1L, summary.get("pending_24h_count"));
        assertEquals(80.0d, (Double) summary.get("success_rate_24h"), 0.001d);
        assertEquals("degraded", summary.get("overall_status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> routes = (List<Map<String, Object>>) result.get("routes");
        Map<String, Object> webhookRoute = routes.stream()
            .filter(item -> Long.valueOf(1L).equals(item.get("route_id")))
            .findFirst()
            .orElseThrow();
        assertEquals(60.0d, (Double) webhookRoute.get("success_rate_24h"), 0.001d);
        assertEquals("failed", webhookRoute.get("health_status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> historyPayload = (List<Map<String, Object>>) result.get("history");
        assertEquals("webhook_http", historyPayload.get(0).get("error_kind"));
    }

    private IncidentRoute route(Long id,
                                String type,
                                String target,
                                String status) {
        IncidentRoute route = new IncidentRoute();
        route.setId(id);
        route.setRouteType(type);
        route.setRouteTarget(target);
        route.setRouteStatus(status);
        return route;
    }
}
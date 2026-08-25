package com.example.panel.controller;

import com.example.panel.entity.SmtpNotificationMonitor;
import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.IncidentNotificationRouteHealthService;
import com.example.panel.service.SmtpNotificationMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/smtp-notifications")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class SmtpNotificationMonitoringApiController {

    private final SmtpNotificationMonitoringService monitoringService;
    private final IncidentNotificationRouteHealthService routeHealthService;

    public SmtpNotificationMonitoringApiController(SmtpNotificationMonitoringService monitoringService,
                                                   IncidentNotificationRouteHealthService routeHealthService) {
        this.monitoringService = monitoringService;
        this.routeHealthService = routeHealthService;
    }

    @GetMapping("/monitors")
    public Map<String, Object> listMonitors() {
        List<SmtpNotificationMonitor> monitors = monitoringService.findAll();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("items", monitors.stream().map(this::toDto).toList());
        payload.put("availability_overview", toAvailabilityOverview(monitoringService.buildAvailabilityOverview(monitors)));
        return payload;
    }

    @PostMapping("/monitors")
    public ResponseEntity<Map<String, Object>> createMonitor(@RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, null, null, null);
            SmtpNotificationMonitor item = monitoringService.createMonitor(toDraft(source));
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PatchMapping("/monitors/{monitorId}")
    public ResponseEntity<Map<String, Object>> updateMonitor(@PathVariable long monitorId,
                                                             @RequestBody(required = false) MonitorPayload payload) {
        try {
            MonitorPayload source = payload != null ? payload : new MonitorPayload(null, null, null, null, null, null);
            SmtpNotificationMonitor item = monitoringService.updateMonitor(monitorId, toDraft(source));
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @DeleteMapping("/monitors/{monitorId}")
    public ResponseEntity<Map<String, Object>> deleteMonitor(@PathVariable long monitorId) {
        try {
            monitoringService.deleteMonitor(monitorId);
            return ResponseEntity.ok(successOnly());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        SmtpNotificationMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
        Map<String, Object> payload = successOnly();
        payload.put("summary", Map.of(
            "total", summary.total(),
            "checked", summary.checked()
        ));
        return payload;
    }

    @PostMapping("/monitors/{monitorId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshMonitor(@PathVariable long monitorId) {
        try {
            SmtpNotificationMonitor item = monitoringService.refreshById(monitorId);
            return ResponseEntity.ok(successItem(item));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/monitors/{monitorId}/history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long monitorId) {
        try {
            List<Map<String, Object>> timeline = monitoringService.loadHistory(monitorId, 20).stream()
                .map(this::toHistoryDto)
                .toList();
            Map<String, Object> payload = successOnly();
            payload.put("items", timeline);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/route-health")
    public Map<String, Object> loadRouteHealth() {
        IncidentNotificationRouteHealthService.RouteHealthSnapshot snapshot = routeHealthService.buildSnapshot();
        Map<String, Object> payload = successOnly();
        Map<String, Object> snapshotPayload = new LinkedHashMap<>();
        snapshotPayload.put("generated_at", snapshot.generatedAt());
        snapshotPayload.put("window_hours", snapshot.windowHours());
        snapshotPayload.put("failure_limit", snapshot.failureLimit());
        snapshotPayload.put("overall_status", snapshot.overallStatus());
        snapshotPayload.put("delivered_24h", snapshot.delivered24h());
        snapshotPayload.put("failed_24h", snapshot.failed24h());
        snapshotPayload.put("pending_24h", snapshot.pending24h());
        snapshotPayload.put("queued_backlog", snapshot.queuedBacklog());
        snapshotPayload.put("processing_backlog", snapshot.processingBacklog());
        snapshotPayload.put("failed_backlog", snapshot.failedBacklog());
        snapshotPayload.put("transient_failures", snapshot.transientFailures());
        snapshotPayload.put("permanent_failures", snapshot.permanentFailures());
        snapshotPayload.put("success_rate_24h", snapshot.successRate24h());
        snapshotPayload.put("last_delivered_at", snapshot.lastDeliveredAt());
        snapshotPayload.put("last_failed_at", snapshot.lastFailedAt());
        payload.put("snapshot", snapshotPayload);
        payload.put("route_types", snapshot.routeTypes().stream().map(item -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("route_type", item.routeType());
            dto.put("delivered_24h", item.delivered24h());
            dto.put("failed_24h", item.failed24h());
            dto.put("pending_24h", item.pending24h());
            dto.put("success_rate_24h", item.successRate24h());
            return dto;
        }).toList());
        payload.put("recent_failures", snapshot.recentFailures().stream().map(item -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("event_id", item.eventId());
            dto.put("incident_id", item.incidentId());
            dto.put("route_id", item.routeId());
            dto.put("event_type", item.eventType());
            dto.put("route_type", item.routeType());
            dto.put("route_target", item.routeTarget());
            dto.put("attempt_count", item.attemptCount());
            dto.put("last_error", item.lastError());
            dto.put("created_at", item.createdAt());
            dto.put("updated_at", item.updatedAt());
            dto.put("error_kind", item.errorKind());
            dto.put("failure_kind", item.failureKind());
            return dto;
        }).toList());
        return payload;
    }

    private SmtpNotificationMonitoringService.MonitorDraft toDraft(MonitorPayload payload) {
        return new SmtpNotificationMonitoringService.MonitorDraft(
            payload.monitorName(),
            payload.relayHost(),
            payload.relayPort(),
            payload.protocolMode(),
            payload.connectTimeoutMs(),
            payload.enabled()
        );
    }

    private Map<String, Object> successOnly() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    private Map<String, Object> successItem(SmtpNotificationMonitor item) {
        Map<String, Object> payload = successOnly();
        payload.put("item", toDto(item));
        return payload;
    }

    private Map<String, Object> errorResponse(String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", error);
        return payload;
    }

    private Map<String, Object> toDto(SmtpNotificationMonitor item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("monitor_name", item.getMonitorName());
        dto.put("relay_host", item.getRelayHost());
        dto.put("relay_port", item.getRelayPort());
        dto.put("protocol_mode", item.getProtocolMode());
        dto.put("connect_timeout_ms", item.getConnectTimeoutMs());
        dto.put("enabled", item.getEnabled());
        dto.put("last_status", item.getLastStatus());
        dto.put("status_level", monitoringService.resolveSeverity(item));
        dto.put("availability", monitoringService.resolveAvailability(item));
        dto.put("last_summary", item.getLastSummary());
        dto.put("last_error_message", item.getLastErrorMessage());
        dto.put("last_banner", item.getLastBanner());
        dto.put("last_tls_protocol", item.getLastTlsProtocol());
        dto.put("last_tls_cipher_suite", item.getLastTlsCipherSuite());
        dto.put("last_connected_at", item.getLastConnectedAt());
        dto.put("last_checked_at", item.getLastCheckedAt());
        dto.put("created_at", item.getCreatedAt());
        dto.put("updated_at", item.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> toHistoryDto(MonitoringCheckHistoryRepository.HistoryEntry entry) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entry.id());
        dto.put("check_kind", entry.checkKind());
        dto.put("status", entry.status());
        dto.put("summary", entry.summary());
        dto.put("details_excerpt", entry.detailsExcerpt());
        dto.put("duration_ms", entry.durationMs());
        dto.put("created_at", entry.createdAt());
        return dto;
    }

    private Map<String, Object> toAvailabilityOverview(SmtpNotificationMonitoringService.AvailabilityOverview overview) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", overview.total());
        payload.put("up", overview.up());
        payload.put("down", overview.down());
        payload.put("unknown", overview.unknown());
        payload.put("disabled", overview.disabled());
        payload.put("availability_percent", overview.availabilityPercent());
        return payload;
    }

    private record MonitorPayload(String monitorName,
                                  String relayHost,
                                  Integer relayPort,
                                  String protocolMode,
                                  Integer connectTimeoutMs,
                                  Boolean enabled) {
    }
}

package com.example.panel.controller;

import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.ProviderHealthMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/provider-health")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class ProviderHealthMonitoringApiController {

    private final ProviderHealthMonitoringService monitoringService;

    public ProviderHealthMonitoringApiController(ProviderHealthMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping("/channels")
    public Map<String, Object> listChannels() {
        ProviderHealthMonitoringService.OverviewSnapshot snapshot = monitoringService.buildOverview();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("availability_overview", toAvailabilityOverview(snapshot.availabilityOverview()));
        payload.put("items", snapshot.items().stream().map(this::toDto).toList());
        return payload;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        ProviderHealthMonitoringService.RefreshSummary summary = monitoringService.refreshAll();
        Map<String, Object> payload = successOnly();
        payload.put("summary", Map.of(
            "total", summary.total(),
            "checked", summary.checked()
        ));
        return payload;
    }

    @PostMapping("/channels/{channelId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshChannel(@PathVariable long channelId) {
        try {
            ProviderHealthMonitoringService.ProviderChannelHealth item = monitoringService.refreshById(channelId);
            Map<String, Object> payload = successOnly();
            payload.put("item", toDto(item));
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    @GetMapping("/channels/{channelId}/history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long channelId) {
        try {
            List<Map<String, Object>> items = monitoringService.loadHistory(channelId, 20).stream()
                .map(this::toHistoryDto)
                .toList();
            Map<String, Object> payload = successOnly();
            payload.put("items", items);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    private Map<String, Object> toDto(ProviderHealthMonitoringService.ProviderChannelHealth item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("channel_id", item.channelId());
        dto.put("channel_name", item.channelName());
        dto.put("platform", item.platform());
        dto.put("active", item.active());
        dto.put("overall_status", item.overallStatus());
        dto.put("availability", item.availability());
        dto.put("runtime_status", item.runtimeStatus());
        dto.put("runtime_message", item.runtimeMessage());
        dto.put("runtime_started_at", item.runtimeStartedAt());
        dto.put("provider_status", item.providerStatus());
        dto.put("provider_message", item.providerMessage());
        dto.put("provider_identity", item.providerIdentity());
        dto.put("provider_http_status", item.providerHttpStatus());
        dto.put("provider_duration_ms", item.providerDurationMs());
        dto.put("ingress_status", item.ingressStatus());
        dto.put("last_inbound_at", item.lastInboundAt());
        dto.put("inbound_24h", item.inbound24h());
        dto.put("outbound_status", item.outboundStatus());
        dto.put("last_outbound_at", item.lastOutboundAt());
        dto.put("outbound_24h", item.outbound24h());
        dto.put("summary", item.summary());
        dto.put("checked_at", item.checkedAt());
        dto.put("check_duration_ms", item.checkDurationMs());
        return dto;
    }

    private Map<String, Object> toHistoryDto(MonitoringCheckHistoryRepository.HistoryEntry entry) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", entry.id());
        dto.put("check_kind", entry.checkKind());
        dto.put("status", entry.status());
        dto.put("summary", entry.summary());
        dto.put("details_excerpt", entry.detailsExcerpt());
        dto.put("http_status", entry.httpStatus());
        dto.put("duration_ms", entry.durationMs());
        dto.put("created_at", entry.createdAt());
        return dto;
    }

    private Map<String, Object> toAvailabilityOverview(ProviderHealthMonitoringService.AvailabilityOverview overview) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total", overview.total());
        dto.put("active", overview.active());
        dto.put("ok", overview.ok());
        dto.put("warning", overview.warning());
        dto.put("critical", overview.critical());
        dto.put("disabled", overview.disabled());
        dto.put("idle", overview.idle());
        dto.put("availability_percent", overview.availabilityPercent());
        return dto;
    }

    private Map<String, Object> successOnly() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    private Map<String, Object> errorResponse(String error) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", false);
        payload.put("error", error);
        return payload;
    }
}

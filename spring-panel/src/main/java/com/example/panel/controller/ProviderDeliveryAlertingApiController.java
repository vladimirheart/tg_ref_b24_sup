package com.example.panel.controller;

import com.example.panel.repository.MonitoringCheckHistoryRepository;
import com.example.panel.service.ProviderDeliveryAlertingService;
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
@RequestMapping("/api/monitoring/provider-delivery")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class ProviderDeliveryAlertingApiController {

    private final ProviderDeliveryAlertingService alertingService;

    public ProviderDeliveryAlertingApiController(ProviderDeliveryAlertingService alertingService) {
        this.alertingService = alertingService;
    }

    @GetMapping("/alerts")
    public Map<String, Object> loadAlerts() {
        ProviderDeliveryAlertingService.OverviewSnapshot snapshot = alertingService.buildOverview();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverviewDto(snapshot.overview()));
        payload.put("items", snapshot.items().stream().map(this::toChannelDto).toList());
        return payload;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refreshAll() {
        ProviderDeliveryAlertingService.RefreshSummary summary = alertingService.refreshAll();
        Map<String, Object> payload = successOnly();
        payload.put("summary", Map.of(
            "checked", summary.checked(),
            "actionable", summary.actionable()
        ));
        return payload;
    }

    @GetMapping("/channels/{channelId}/alert-history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long channelId) {
        try {
            List<Map<String, Object>> items = alertingService.loadHistory(channelId, 20).stream()
                .map(this::toHistoryDto)
                .toList();
            Map<String, Object> payload = successOnly();
            payload.put("items", items);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    private Map<String, Object> toOverviewDto(ProviderDeliveryAlertingService.Overview overview) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total_channels", overview.totalChannels());
        dto.put("active_channels", overview.activeChannels());
        dto.put("actionable_channels", overview.actionableChannels());
        dto.put("warning_channels", overview.warningChannels());
        dto.put("critical_channels", overview.criticalChannels());
        dto.put("idle_channels", overview.idleChannels());
        dto.put("disabled_channels", overview.disabledChannels());
        dto.put("failure_pressure_channels", overview.failurePressureChannels());
        dto.put("rate_limit_pressure_channels", overview.rateLimitPressureChannels());
        dto.put("active_incidents", overview.activeIncidents());
        return dto;
    }

    private Map<String, Object> toChannelDto(ProviderDeliveryAlertingService.ChannelAlertSnapshot item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("channel_id", item.channelId());
        dto.put("channel_name", item.channelName());
        dto.put("platform", item.platform());
        dto.put("active", item.active());
        dto.put("alert_status", item.alertStatus());
        dto.put("summary", item.summary());
        dto.put("last_attempt_at", item.lastAttemptAt());
        dto.put("last_success_at", item.lastSuccessAt());
        dto.put("last_failure_at", item.lastFailureAt());
        dto.put("failure_signal", toSignalDto(item.failureSignal()));
        dto.put("rate_limit_signal", toSignalDto(item.rateLimitSignal()));
        dto.put("related_incidents", item.relatedIncidents());
        return dto;
    }

    private Map<String, Object> toSignalDto(ProviderDeliveryAlertingService.BurnRateSignal signal) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("signal_key", signal.signalKey());
        dto.put("label", signal.label());
        dto.put("status", signal.status());
        dto.put("summary", signal.summary());
        dto.put("short_window_attempts", signal.shortWindowAttempts());
        dto.put("short_window_errors", signal.shortWindowErrors());
        dto.put("short_window_error_rate", signal.shortWindowErrorRate());
        dto.put("short_window_burn_rate", signal.shortWindowBurnRate());
        dto.put("long_window_attempts", signal.longWindowAttempts());
        dto.put("long_window_errors", signal.longWindowErrors());
        dto.put("long_window_error_rate", signal.longWindowErrorRate());
        dto.put("long_window_burn_rate", signal.longWindowBurnRate());
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

package com.example.panel.controller;

import com.example.panel.entity.ProviderDeliveryLedgerEntry;
import com.example.panel.service.ProviderDeliveryLedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring/provider-delivery")
@PreAuthorize("hasAuthority('PAGE_ANALYTICS')")
public class ProviderDeliveryLedgerApiController {

    private final ProviderDeliveryLedgerService ledgerService;

    public ProviderDeliveryLedgerApiController(ProviderDeliveryLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/channels")
    public Map<String, Object> listChannels() {
        ProviderDeliveryLedgerService.OverviewSnapshot snapshot = ledgerService.buildOverview();
        Map<String, Object> payload = successOnly();
        payload.put("generated_at", snapshot.generatedAt());
        payload.put("overview", toOverviewDto(snapshot.overview()));
        payload.put("items", snapshot.items().stream().map(this::toChannelDto).toList());
        payload.put("recent_attempts", snapshot.recentAttempts().stream().map(this::toEntryDto).toList());
        return payload;
    }

    @GetMapping("/channels/{channelId}/history")
    public ResponseEntity<Map<String, Object>> loadHistory(@PathVariable long channelId) {
        try {
            List<Map<String, Object>> items = ledgerService.loadHistory(channelId, 50).stream()
                .map(this::toEntryDto)
                .toList();
            Map<String, Object> payload = successOnly();
            payload.put("items", items);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorResponse(ex.getMessage()));
        }
    }

    private Map<String, Object> toOverviewDto(ProviderDeliveryLedgerService.DeliveryOverview overview) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("total_channels", overview.totalChannels());
        dto.put("active_channels", overview.activeChannels());
        dto.put("ok_channels", overview.okChannels());
        dto.put("warning_channels", overview.warningChannels());
        dto.put("critical_channels", overview.criticalChannels());
        dto.put("idle_channels", overview.idleChannels());
        dto.put("disabled_channels", overview.disabledChannels());
        dto.put("attempts_24h", overview.attempts24h());
        dto.put("success_24h", overview.success24h());
        dto.put("failure_24h", overview.failure24h());
        dto.put("rate_limited_24h", overview.rateLimited24h());
        dto.put("terminal_failures_24h", overview.terminalFailures24h());
        dto.put("transient_failures_24h", overview.transientFailures24h());
        return dto;
    }

    private Map<String, Object> toChannelDto(ProviderDeliveryLedgerService.ChannelDeliverySummary item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("channel_id", item.channelId());
        dto.put("channel_name", item.channelName());
        dto.put("platform", item.platform());
        dto.put("active", item.active());
        dto.put("status", item.status());
        dto.put("total_24h", item.total24h());
        dto.put("success_24h", item.success24h());
        dto.put("failure_24h", item.failure24h());
        dto.put("rate_limited_24h", item.rateLimited24h());
        dto.put("terminal_failures_24h", item.terminalFailures24h());
        dto.put("transient_failures_24h", item.transientFailures24h());
        dto.put("last_attempt_at", item.lastAttemptAt());
        dto.put("last_success_at", item.lastSuccessAt());
        dto.put("last_failure_at", item.lastFailureAt());
        dto.put("last_classification", item.lastClassification());
        dto.put("last_severity_level", item.lastSeverityLevel());
        dto.put("last_provider_message", item.lastProviderMessage());
        dto.put("last_http_status", item.lastHttpStatus());
        dto.put("summary", item.summary());
        return dto;
    }

    private Map<String, Object> toEntryDto(ProviderDeliveryLedgerEntry item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", item.getId());
        dto.put("channel_id", item.getChannelId());
        dto.put("ticket_id", item.getTicketId());
        dto.put("platform", item.getPlatform());
        dto.put("provider", item.getProvider());
        dto.put("user_id", item.getUserId());
        dto.put("sender_kind", item.getSenderKind());
        dto.put("message_kind", item.getMessageKind());
        dto.put("delivery_status", item.getDeliveryStatus());
        dto.put("classification", item.getClassification());
        dto.put("severity_level", item.getSeverityLevel());
        dto.put("retry_state", item.getRetryState());
        dto.put("http_status", item.getHttpStatus());
        dto.put("provider_error_code", item.getProviderErrorCode());
        dto.put("provider_message", item.getProviderMessage());
        dto.put("response_excerpt", item.getResponseExcerpt());
        dto.put("provider_message_id", item.getProviderMessageId());
        dto.put("reply_to_message_id", item.getReplyToMessageId());
        dto.put("duration_ms", item.getDurationMs());
        dto.put("attempted_at", item.getAttemptedAt());
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

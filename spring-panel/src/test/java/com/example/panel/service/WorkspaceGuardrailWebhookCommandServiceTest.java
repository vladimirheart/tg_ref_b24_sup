package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceGuardrailWebhookCommandServiceTest {

    @Test
    void resolvesWebhookCommandForAttentionGuardrails() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogWorkspaceTelemetrySummaryService summaryService = mock(DialogWorkspaceTelemetrySummaryService.class);
        WorkspaceGuardrailWebhookCommandService service =
                new WorkspaceGuardrailWebhookCommandService(sharedConfigService, summaryService, new ObjectMapper());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_guardrail_webhook_enabled", true,
                        "workspace_guardrail_webhook_url", "https://guardrails.example/hooks",
                        "workspace_guardrail_webhook_min_alerts", 1
                )));
        when(summaryService.loadSummary(7, null)).thenReturn(Map.of(
                "guardrails", Map.of(
                        "status", "attention",
                        "alerts", List.of(Map.of("key", "render_error", "severity", "high"))
                )));

        WorkspaceGuardrailWebhookCommandService.WorkspaceGuardrailWebhookCommand command =
                service.resolveCommand(Instant.parse("2026-04-30T12:00:00Z"), null, "");

        assertThat(command).isNotNull();
        assertThat(command.webhookUrl()).isEqualTo("https://guardrails.example/hooks");
        assertThat(command.payload()).containsEntry("alerts_total", 1);
        assertThat(command.fingerprint()).isNotBlank();
    }

    @Test
    void suppressesWebhookCommandDuringCooldownOrDuplicateFingerprint() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogWorkspaceTelemetrySummaryService summaryService = mock(DialogWorkspaceTelemetrySummaryService.class);
        WorkspaceGuardrailWebhookCommandService service =
                new WorkspaceGuardrailWebhookCommandService(sharedConfigService, summaryService, new ObjectMapper());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "workspace_guardrail_webhook_enabled", true,
                        "workspace_guardrail_webhook_url", "https://guardrails.example/hooks",
                        "workspace_guardrail_webhook_cooldown_minutes", 60
                )));
        when(summaryService.loadSummary(7, null)).thenReturn(Map.of(
                "guardrails", Map.of(
                        "status", "attention",
                        "alerts", List.of(Map.of("key", "render_error"))
                )));

        WorkspaceGuardrailWebhookCommandService.WorkspaceGuardrailWebhookCommand first =
                service.resolveCommand(Instant.parse("2026-04-30T12:00:00Z"), null, "");
        WorkspaceGuardrailWebhookCommandService.WorkspaceGuardrailWebhookCommand cooldown =
                service.resolveCommand(Instant.parse("2026-04-30T12:30:00Z"), Instant.parse("2026-04-30T12:00:00Z"), "");
        WorkspaceGuardrailWebhookCommandService.WorkspaceGuardrailWebhookCommand duplicate =
                service.resolveCommand(Instant.parse("2026-04-30T14:00:00Z"), Instant.parse("2026-04-30T12:00:00Z"), first.fingerprint());

        assertThat(first).isNotNull();
        assertThat(cooldown).isNull();
        assertThat(duplicate).isNull();
    }
}

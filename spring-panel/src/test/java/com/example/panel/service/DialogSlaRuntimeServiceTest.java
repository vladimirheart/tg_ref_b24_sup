package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogSlaRuntimeServiceTest {

    private final DialogSlaRuntimeService service = new DialogSlaRuntimeService();

    @Test
    void resolveSlaStateAndMinutesLeftRespectWarningAndBreachWindows() {
        String createdAt = "2026-04-21T09:00:00Z";
        long atRiskNow = service.parseTimestampToMillis("2026-04-21T09:50:00Z");
        long breachedNow = service.parseTimestampToMillis("2026-04-21T10:05:00Z");

        assertThat(service.resolveSlaState(createdAt, 60, 15, "open", atRiskNow)).isEqualTo("at_risk");
        assertThat(service.resolveSlaMinutesLeft(createdAt, 60, "open", atRiskNow)).isEqualTo(10L);
        assertThat(service.resolveSlaState(createdAt, 60, 15, "open", breachedNow)).isEqualTo("breached");
        assertThat(service.resolveSlaMinutesLeft(createdAt, 60, "open", breachedNow)).isEqualTo(-5L);
    }

    @Test
    void normalizeLifecycleAndDeadlineSupportClosedAndLegacyTimestamps() {
        assertThat(service.normalizeSlaLifecycleState("auto_closed")).isEqualTo("closed");
        assertThat(service.computeDeadlineAt("2026-04-21 09:00:00", 30)).isEqualTo("2026-04-21T09:30:00Z");
        assertThat(service.resolveSlaMinutesLeft("2026-04-21T09:00:00Z", 30, "closed", 0)).isNull();
    }

    @Test
    void resolveDialogConfigValuesFallsBackForInvalidPayloads() {
        Map<String, Object> settings = Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", "90",
                        "sla_critical_escalation_enabled", "no"
                )
        );

        assertThat(service.resolveDialogConfigMinutes(settings, "sla_target_minutes", 10)).isEqualTo(90);
        assertThat(service.resolveDialogConfigMinutes(settings, "missing_key", 10)).isEqualTo(10);
        assertThat(service.resolveDialogConfigBoolean(settings, "sla_critical_escalation_enabled", true)).isEqualTo(false);
        assertThat(service.resolveDialogConfigBoolean(Map.of(), "sla_critical_escalation_enabled", true)).isEqualTo(true);
    }
}

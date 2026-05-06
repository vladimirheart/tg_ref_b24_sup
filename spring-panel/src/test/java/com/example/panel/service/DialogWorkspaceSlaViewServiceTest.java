package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceSlaViewServiceTest {

    private final DialogSlaRuntimeService dialogSlaRuntimeService = mock(DialogSlaRuntimeService.class);
    private final SlaEscalationWebhookNotifier notifier = mock(SlaEscalationWebhookNotifier.class);
    private final DialogWorkspaceSlaViewService service = new DialogWorkspaceSlaViewService(dialogSlaRuntimeService, notifier);

    @Test
    void buildResolvesSlaWindowAndPolicyPayload() {
        DialogListItem summary = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", "2026-05-01T10:00:00Z", "open", null, null, "alice", null, null, null, "user",
                "2026-05-01T10:00:00Z", 0, null, null
        );
        when(dialogSlaRuntimeService.resolveDialogConfigMinutes(Map.of("dialog_config", Map.of()), "sla_target_minutes", 1440)).thenReturn(1440);
        when(dialogSlaRuntimeService.resolveDialogConfigMinutes(Map.of("dialog_config", Map.of()), "sla_warning_minutes", 240)).thenReturn(120);
        when(dialogSlaRuntimeService.resolveDialogConfigMinutes(Map.of("dialog_config", Map.of()), "sla_critical_minutes", 30)).thenReturn(15);
        when(dialogSlaRuntimeService.resolveSlaState(summary.createdAt(), 1440, 120, summary.statusKey())).thenReturn("warning");
        when(dialogSlaRuntimeService.resolveSlaMinutesLeft(eq(summary.createdAt()), eq(1440), eq(summary.statusKey()), anyLong())).thenReturn(10L);
        when(dialogSlaRuntimeService.computeDeadlineAt(summary.createdAt(), 1440)).thenReturn("2026-05-02T10:00:00Z");
        when(notifier.buildRoutingPolicySnapshot(summary, Map.of("dialog_config", Map.of()))).thenReturn(Map.of("action", "assist"));

        DialogWorkspaceSlaViewService.SlaView view = service.build(summary, Map.of("dialog_config", Map.of()));

        assertThat(view.targetMinutes()).isEqualTo(1440);
        assertThat(view.warningMinutes()).isEqualTo(120);
        assertThat(view.criticalMinutes()).isEqualTo(15);
        assertThat(view.state()).isEqualTo("warning");
        assertThat(view.minutesLeft()).isEqualTo(10L);
        assertThat(view.policy()).containsEntry("action", "assist");
    }
}

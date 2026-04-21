package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogListReadServiceTest {

    private final DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final DialogSlaRuntimeService dialogSlaRuntimeService = new DialogSlaRuntimeService();
    private final DialogListReadService service =
            new DialogListReadService(dialogLookupReadService, sharedConfigService, dialogSlaRuntimeService);

    @Test
    void loadListPayloadBuildsSlaOrchestrationWithCriticalUnassignedTicket() {
        when(dialogLookupReadService.loadSummary()).thenReturn(mock(DialogSummary.class));
        when(dialogLookupReadService.loadDialogs("operator")).thenReturn(List.of(
                sampleDialog("T-100", null, "2026-04-21T00:00:00Z", "open"),
                sampleDialog("T-200", "operator", "2026-04-21T09:50:00Z", "open")
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "sla_target_minutes", 180,
                        "sla_warning_minutes", 60,
                        "sla_critical_minutes", 30,
                        "sla_critical_escalation_enabled", true
                )
        ));

        Map<String, Object> payload = service.loadListPayload("operator");

        assertThat(payload.get("success")).isEqualTo(true);
        Map<?, ?> orchestration = (Map<?, ?>) payload.get("sla_orchestration");
        Map<?, ?> tickets = (Map<?, ?>) orchestration.get("tickets");
        Map<?, ?> criticalTicket = (Map<?, ?>) tickets.get("T-100");
        Map<?, ?> freshTicket = (Map<?, ?>) tickets.get("T-200");

        assertThat(orchestration.get("enabled")).isEqualTo(true);
        assertThat(orchestration.get("critical_minutes")).isEqualTo(30);
        assertThat(criticalTicket.get("is_critical")).isEqualTo(true);
        assertThat(criticalTicket.get("escalation_required")).isEqualTo(true);
        assertThat(criticalTicket.get("escalation_reason")).isEqualTo("critical_sla_unassigned");
        assertThat(freshTicket.get("auto_pin")).isEqualTo(false);
    }

    private DialogListItem sampleDialog(String ticketId, String responsible, String createdAt, String statusKey) {
        return new DialogListItem(
                ticketId,
                700L,
                1001L,
                "client_username",
                "Клиент",
                "sales",
                44L,
                "Telegram",
                "Moscow",
                "Moscow",
                "message preview",
                createdAt,
                statusKey,
                false,
                null,
                responsible,
                "21.04.2026",
                "10:00:00",
                "vip",
                "client",
                "2026-04-21T10:00:00Z",
                1,
                5,
                "billing"
        );
    }
}

package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogMyDialogs;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.model.dialog.DialogListPage;
import com.example.panel.model.dialog.DialogListQuery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class DialogListReadServiceTest {

    private final DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final DialogSlaRuntimeService dialogSlaRuntimeService = new DialogSlaRuntimeService();
    private final DialogListReadService service =
            new DialogListReadService(dialogLookupReadService, sharedConfigService, dialogSlaRuntimeService);

    @Test
    void pageSwitchDoesNotRebuildOverviewAndNeverLoadsEntireList() {
        var query = new DialogListQuery(4, 20, "клиент", "", "active", null, "default", List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(dialogLookupReadService.loadDialogsPage(eq("operator"), eq(query), any()))
                .thenReturn(new DialogListPage(List.of(), 0, 1, 20));
        var payload = service.loadListPayload("operator", query, false);
        assertThat(payload).doesNotContainKeys("summary", "my_dialogs");
        verify(dialogLookupReadService, never()).loadDialogs(any());
        verify(dialogLookupReadService, never()).loadSummary();
        verify(dialogLookupReadService, never()).loadMyDialogsPreview(any(), any(), any());
    }

    @Test
    void loadListPayloadBuildsSlaOrchestrationWithCriticalUnassignedTicket() {
        when(dialogLookupReadService.loadSummary()).thenReturn(new DialogSummary(2, 0, 2, List.of()));
        List<DialogListItem> dialogs = List.of(
                sampleDialog("T-100", null, "2026-04-21T00:00:00Z", "open"),
                sampleDialog("T-200", "operator", Instant.now().minus(10, ChronoUnit.MINUTES).toString(), "open")
        );
        when(dialogLookupReadService.loadDialogsPage(eq("operator"), any(), any()))
                .thenReturn(new DialogListPage(dialogs, 2000, 1, 20));
        when(dialogLookupReadService.loadMyDialogsPreview(eq("operator"), any(), any()))
                .thenReturn(new DialogLookupReadService.MyDialogsPreview(
                        new DialogMyDialogs(List.of(dialogs.get(0)), List.of(), List.of(dialogs.get(1))),
                        Map.of("new", 1200L, "unanswered", 0L, "in_work", 800L)));
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
        assertThat(((Map<?, ?>) payload.get("pagination")).get("total_items")).isEqualTo(2000L);
        verify(dialogLookupReadService, never()).loadDialogs(any());
        Map<?, ?> orchestration = (Map<?, ?>) payload.get("sla_orchestration");
        Map<?, ?> tickets = (Map<?, ?>) orchestration.get("tickets");
        Map<?, ?> criticalTicket = (Map<?, ?>) tickets.get("T-100");
        Map<?, ?> freshTicket = (Map<?, ?>) tickets.get("T-200");
        Map<?, ?> myDialogs = (Map<?, ?>) payload.get("my_dialogs");

        assertThat(orchestration.get("enabled")).isEqualTo(true);
        assertThat(orchestration.get("critical_minutes")).isEqualTo(30);
        assertThat(criticalTicket.get("is_critical")).isEqualTo(true);
        assertThat(criticalTicket.get("escalation_required")).isEqualTo(true);
        assertThat(criticalTicket.get("escalation_reason")).isEqualTo("critical_sla_unassigned");
        assertThat(freshTicket.get("auto_pin")).isEqualTo(false);
        assertThat((List<?>) myDialogs.get("new")).hasSize(1);
        assertThat((List<?>) myDialogs.get("unanswered")).isEmpty();
        assertThat((List<?>) myDialogs.get("in_work")).hasSize(1);
    }

    @Test
    void loadListPayloadPreservesMyDialogsBucketsForAutoProcessingAndOwnerHandoffSemantics() {
        when(dialogLookupReadService.loadSummary()).thenReturn(new DialogSummary(2, 0, 2, List.of()));
        List<DialogListItem> dialogs = List.of(
                sampleDialog("T-300", "new_owner", Instant.now().minus(5, ChronoUnit.MINUTES).toString(), "open"),
                autoProcessingDialog("T-301", "new_owner", Instant.now().minus(3, ChronoUnit.MINUTES).toString())
        );
        when(dialogLookupReadService.loadDialogsPage(eq("new_owner"), any(), any()))
                .thenReturn(new DialogListPage(dialogs, 2, 1, 20));
        when(dialogLookupReadService.loadMyDialogsPreview(eq("new_owner"), any(), any()))
                .thenReturn(new DialogLookupReadService.MyDialogsPreview(
                        new DialogMyDialogs(List.of(), List.of(dialogs.get(0)), List.of(dialogs.get(1))),
                        Map.of("new", 0L, "unanswered", 1L, "in_work", 1L)));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());

        Map<String, Object> payload = service.loadListPayload("new_owner");

        Map<?, ?> myDialogs = (Map<?, ?>) payload.get("my_dialogs");

        assertThat(payload.get("success")).isEqualTo(true);
        assertThat((List<?>) payload.get("dialogs")).hasSize(2);
        assertThat((List<?>) myDialogs.get("new")).isEmpty();
        assertThat((List<?>) myDialogs.get("unanswered")).hasSize(1);
        assertThat((List<?>) myDialogs.get("in_work")).hasSize(1);
        assertThat(((DialogListItem) ((List<?>) myDialogs.get("unanswered")).get(0)).ticketId()).isEqualTo("T-300");
        assertThat(((DialogListItem) ((List<?>) myDialogs.get("in_work")).get(0)).ticketId()).isEqualTo("T-301");
    }

    private DialogListItem sampleDialog(String ticketId, String responsible, String createdAt, String statusKey) {
        return dialogItem(ticketId, responsible, createdAt, statusKey, false, "client", 1);
    }

    private DialogListItem autoProcessingDialog(String ticketId, String responsible, String createdAt) {
        return dialogItem(ticketId, responsible, createdAt, "open", true, "support", 0);
    }

    private DialogListItem dialogItem(String ticketId,
                                      String responsible,
                                      String createdAt,
                                      String status,
                                      boolean aiProcessing,
                                      String lastSender,
                                      int unreadCount) {
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
                status,
                aiProcessing,
                null,
                responsible,
                "21.04.2026",
                "10:00:00",
                "vip",
                lastSender,
                "2026-04-21T10:00:00Z",
                unreadCount,
                5,
                "billing"
        );
    }
}

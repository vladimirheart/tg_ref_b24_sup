package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceNavigationServiceTest {

    private final DialogService dialogService = mock(DialogService.class);
    private final DialogWorkspaceNavigationService service = new DialogWorkspaceNavigationService(dialogService);

    @Test
    void buildNavigationMetaIncludesPreviousAndNextQueueContext() {
        when(dialogService.loadDialogs("operator")).thenReturn(List.of(
                sampleDialog("T-100", "Клиент 1", "2026-04-20T10:00:00Z"),
                sampleDialog("T-200", "Клиент 2", "2026-04-20T10:05:00Z"),
                sampleDialog("T-300", "Клиент 3", "2026-04-20T10:10:00Z")
        ));

        Map<String, Object> payload = service.buildNavigationMeta(Map.of(), "operator", "T-200");

        assertThat(payload.get("enabled")).isEqualTo(true);
        assertThat(payload.get("found_in_queue")).isEqualTo(true);
        assertThat(payload.get("position")).isEqualTo(2);
        assertThat(payload.get("total")).isEqualTo(3);
        assertThat(payload.get("has_previous")).isEqualTo(true);
        assertThat(payload.get("has_next")).isEqualTo(true);
        assertThat(((Map<?, ?>) payload.get("previous")).get("ticket_id")).isEqualTo("T-100");
        assertThat(((Map<?, ?>) payload.get("next")).get("ticket_id")).isEqualTo("T-300");
    }

    @Test
    void buildNavigationMetaRespectsDisabledInlineNavigationSetting() {
        when(dialogService.loadDialogs("operator")).thenReturn(List.of(sampleDialog("T-200", "Клиент 2", "2026-04-20T10:05:00Z")));

        Map<String, Object> payload = service.buildNavigationMeta(
                Map.of("dialog_config", Map.of("workspace_inline_navigation", false)),
                "operator",
                "T-200"
        );

        assertThat(payload.get("enabled")).isEqualTo(false);
        assertThat(payload.get("summary")).isEqualTo("Inline navigation отключена текущей настройкой rollout.");
    }

    private DialogListItem sampleDialog(String ticketId, String clientName, String lastMessageAt) {
        return new DialogListItem(
                ticketId,
                401L,
                1001L,
                "client_username",
                clientName,
                "sales",
                44L,
                "Telegram",
                "Moscow",
                "Moscow",
                "message preview",
                lastMessageAt,
                "open",
                false,
                null,
                "operator",
                "20.04.2026",
                "10:00:00",
                "vip",
                "client",
                "2026-04-20T10:01:00Z",
                1,
                5,
                "billing"
        );
    }
}

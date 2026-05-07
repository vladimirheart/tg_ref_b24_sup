package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceClientCardServiceTest {

    private final DialogWorkspaceClientProfileService clientProfileService = mock(DialogWorkspaceClientProfileService.class);
    private final DialogWorkspaceClientPayloadService clientPayloadService = mock(DialogWorkspaceClientPayloadService.class);
    private final DialogWorkspaceClientCardService service =
            new DialogWorkspaceClientCardService(clientProfileService, clientPayloadService);

    @Test
    void buildCreatesBaseWorkspaceClientCard() {
        DialogListItem summary = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", "2026-05-01T10:00:00Z", "open", null, null, "alice", null, null, null, "user",
                "2026-05-01T10:00:00Z", 2, null, null
        );
        Map<String, Object> settings = Map.of();
        Map<String, Object> profile = Map.of("country", "RU");

        when(clientProfileService.buildClientSegments(summary, profile, settings)).thenReturn(List.of("vip"));
        when(clientPayloadService.resolveExternalProfileLinks(settings, summary, "T-1", profile)).thenReturn(Map.of("crm", Map.of("url", "https://crm")));
        when(clientPayloadService.resolveClientAttributeLabels(settings)).thenReturn(Map.of("country", "Country"));
        when(clientPayloadService.resolveClientAttributeOrder(settings)).thenReturn(List.of("country"));

        DialogWorkspaceClientCardService.WorkspaceClientCard card = service.build(settings, summary, "T-1", profile);

        assertThat(card.workspaceClient()).containsEntry("name", "Client");
        assertThat(card.workspaceClient()).containsEntry("country", "RU");
        assertThat(card.workspaceClient()).containsEntry("segments", List.of("vip"));
        assertThat(card.externalLinks()).containsKey("crm");
        assertThat(card.attributeLabels()).containsEntry("country", "Country");
    }
}

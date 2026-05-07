package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogWorkspaceProfileEnrichmentServiceTest {

    private final DialogWorkspaceExternalProfileService externalProfileService = mock(DialogWorkspaceExternalProfileService.class);
    private final DialogWorkspaceClientPayloadService clientPayloadService = mock(DialogWorkspaceClientPayloadService.class);
    private final DialogWorkspaceProfileEnrichmentService service =
            new DialogWorkspaceProfileEnrichmentService(externalProfileService, clientPayloadService);

    @Test
    void resolveMergesExternalEnrichmentAndAppliesHiddenAttributes() {
        DialogListItem summary = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", "2026-05-01T10:00:00Z", "open", null, null, "alice", null, null, null, "user",
                "2026-05-01T10:00:00Z", 2, null, null
        );
        Map<String, Object> settings = Map.of("dialog_config", Map.of("workspace_client_external_profile_url", "https://ext/{ticket_id}"));
        Map<String, Object> profile = Map.of("country", "RU");

        when(clientPayloadService.buildExternalLinkPlaceholders(eq(summary), eq("T-1"), eq(profile)))
                .thenReturn(Map.of("ticket_id", "T-1"));
        when(externalProfileService.resolveProfile(any(), eq("https://ext/T-1")))
                .thenReturn(Map.of("crm_city", "Moscow"));
        when(clientPayloadService.resolveHiddenClientAttributes(settings)).thenReturn(Set.of("crm_city"));
        when(clientPayloadService.filterProfileEnrichment(any(), eq(Set.of("crm_city"))))
                .thenReturn(Map.of("country", "RU"));

        DialogWorkspaceProfileEnrichmentService.ProfileEnrichmentBundle bundle =
                service.resolve(settings, summary, "T-1", profile);

        assertThat(bundle.effectiveProfileEnrichment()).containsEntry("crm_city", "Moscow");
        assertThat(bundle.filteredProfileEnrichment()).containsEntry("country", "RU");
    }
}

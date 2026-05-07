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

class DialogWorkspaceContextGraphServiceTest {

    private final DialogWorkspaceContextSourceService contextSourceService = mock(DialogWorkspaceContextSourceService.class);
    private final DialogWorkspaceContextBlockService contextBlockService = mock(DialogWorkspaceContextBlockService.class);
    private final DialogWorkspaceContextContractService contextContractService = mock(DialogWorkspaceContextContractService.class);
    private final DialogClientContextReadService clientContextReadService = mock(DialogClientContextReadService.class);
    private final DialogWorkspaceClientProfileService clientProfileService = mock(DialogWorkspaceClientProfileService.class);
    private final DialogWorkspaceRequestContractService requestContractService = new DialogWorkspaceRequestContractService();

    private final DialogWorkspaceContextGraphService service =
            new DialogWorkspaceContextGraphService(
                    contextSourceService,
                    contextBlockService,
                    contextContractService,
                    clientContextReadService,
                    clientProfileService,
                    requestContractService
            );

    @Test
    void buildAddsProfileMatchHealthAndContextLayers() {
        DialogListItem summary = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", "2026-05-01T10:00:00Z", "open", null, null, "alice", null, null, null, "user",
                "2026-05-01T10:00:00Z", 2, null, null
        );
        Map<String, Object> workspaceClient = new java.util.LinkedHashMap<>(Map.of("business", "HQ", "location", "Moscow", "country", "RU"));
        Map<String, String> attributeLabels = Map.of("country", "Country");
        Map<String, Object> effectiveProfile = Map.of("crm_city", "Moscow");
        Map<String, Object> filteredProfile = Map.of("country", "RU");
        Map<String, Object> externalLinks = Map.of("crm", Map.of("url", "https://crm"));

        when(clientContextReadService.loadDialogProfileMatchCandidates(any(), eq(5))).thenReturn(Map.of("matches", List.of("x")));
        when(clientProfileService.buildProfileHealth(eq(Map.of()), any(), eq(Map.of("country", "Country")))).thenReturn(Map.of("ready", true));
        when(contextSourceService.buildContextSources(eq(Map.of()), any(), eq(filteredProfile), eq(effectiveProfile), eq(externalLinks))).thenReturn(List.of(Map.of("key", "crm")));
        when(contextSourceService.buildContextAttributePolicies(any(), any(), any())).thenReturn(List.of(Map.of("key", "country")));
        when(contextBlockService.buildContextBlocks(eq(Map.of()), any(), any(), any(), any(), eq(null), eq(externalLinks))).thenReturn(List.of(Map.of("key", "profile")));
        when(contextBlockService.buildBlocksHealth(any())).thenReturn(Map.of("ready", true));
        when(contextContractService.buildContextContract(eq(Map.of()), eq(summary), any(), any(), any())).thenReturn(Map.of("enabled", true));

        DialogWorkspaceContextGraphService.ContextGraphBundle bundle = service.build(
                Map.of(),
                summary,
                workspaceClient,
                attributeLabels,
                effectiveProfile,
                filteredProfile,
                externalLinks,
                List.of(Map.of("ticket_id", "T-0")),
                List.of(Map.of("event", "created"))
        );

        assertThat(bundle.workspaceClient()).containsEntry("profile_match_candidates", Map.of("matches", List.of("x")));
        assertThat(bundle.workspaceClient()).containsEntry("context_contract", Map.of("enabled", true));
        assertThat(bundle.contextSources()).hasSize(1);
        assertThat(bundle.contextBlocksHealth()).containsEntry("ready", true);
    }
}

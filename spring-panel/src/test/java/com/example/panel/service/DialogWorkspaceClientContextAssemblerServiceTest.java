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

class DialogWorkspaceClientContextAssemblerServiceTest {

    private final DialogWorkspaceExternalProfileService externalProfileService = mock(DialogWorkspaceExternalProfileService.class);
    private final DialogWorkspaceClientProfileService clientProfileService = mock(DialogWorkspaceClientProfileService.class);
    private final DialogWorkspaceContextBlockService contextBlockService = mock(DialogWorkspaceContextBlockService.class);
    private final DialogWorkspaceClientPayloadService clientPayloadService = mock(DialogWorkspaceClientPayloadService.class);
    private final DialogWorkspaceContextSourceService contextSourceService = mock(DialogWorkspaceContextSourceService.class);
    private final DialogWorkspaceContextContractService contextContractService = mock(DialogWorkspaceContextContractService.class);
    private final DialogClientContextReadService clientContextReadService = mock(DialogClientContextReadService.class);
    private final DialogWorkspaceRequestContractService requestContractService = new DialogWorkspaceRequestContractService();

    private final DialogWorkspaceClientContextAssemblerService service =
            new DialogWorkspaceClientContextAssemblerService(
                    externalProfileService,
                    clientProfileService,
                    contextBlockService,
                    clientPayloadService,
                    contextSourceService,
                    contextContractService,
                    clientContextReadService,
                    requestContractService
            );

    @Test
    void assembleBuildsWorkspaceClientAndDerivedContext() {
        DialogListItem summary = new DialogListItem(
                "T-1", 100L, 1L, "client", "Client", "biz", 10L, "Telegram", "Moscow", "HQ",
                "Issue", "2026-05-01T10:00:00Z", "open", null, null, "alice", null, null, null, "user",
                "2026-05-01T10:00:00Z", 2, null, null
        );
        Map<String, Object> settings = Map.of("dialog_config", Map.of("workspace_client_external_profile_url", "https://ext/{ticket_id}"));
        Map<String, Object> profile = Map.of("crm_city", "Moscow", "country", "RU");
        when(clientPayloadService.buildExternalLinkPlaceholders(eq(summary), eq("T-1"), eq(profile)))
                .thenReturn(Map.of("ticket_id", "T-1"));
        when(externalProfileService.resolveProfile(any(), eq("https://ext/T-1")))
                .thenReturn(Map.of("external_loyalty_tier", "gold"));
        when(clientPayloadService.resolveHiddenClientAttributes(settings)).thenReturn(java.util.Set.of());
        when(clientPayloadService.filterProfileEnrichment(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(clientProfileService.buildClientSegments(eq(summary), any(), eq(settings))).thenReturn(List.of("vip"));
        when(clientPayloadService.resolveExternalProfileLinks(eq(settings), eq(summary), eq("T-1"), any())).thenReturn(Map.of("crm", Map.of("url", "https://crm")));
        when(clientPayloadService.resolveClientAttributeLabels(settings)).thenReturn(Map.of("country", "Country"));
        when(clientPayloadService.resolveClientAttributeOrder(settings)).thenReturn(List.of("country"));
        when(clientContextReadService.loadDialogProfileMatchCandidates(any(), eq(5))).thenReturn(Map.of("matches", List.of("x")));
        when(clientProfileService.buildProfileHealth(eq(settings), any(), eq(Map.of("country", "Country")))).thenReturn(Map.of("ready", true));
        when(contextSourceService.buildContextSources(eq(settings), any(), any(), any(), any())).thenReturn(List.of(Map.of("key", "crm")));
        when(contextSourceService.buildContextAttributePolicies(any(), any(), any())).thenReturn(List.of(Map.of("key", "country")));
        when(contextBlockService.buildContextBlocks(eq(settings), any(), any(), any(), any(), eq(null), any())).thenReturn(List.of(Map.of("key", "profile")));
        when(contextBlockService.buildBlocksHealth(any())).thenReturn(Map.of("ready", true));
        when(contextContractService.buildContextContract(eq(settings), eq(summary), any(), any(), any())).thenReturn(Map.of("enabled", true));

        DialogWorkspaceClientContextAssemblerService.WorkspaceClientContextBundle bundle = service.assemble(
                settings,
                summary,
                "T-1",
                List.of(Map.of("ticket_id", "T-0")),
                List.of(Map.of("event", "created")),
                profile
        );

        assertThat(bundle.workspaceClient()).containsEntry("name", "Client");
        assertThat(bundle.workspaceClient()).containsEntry("profile_match_candidates", Map.of("matches", List.of("x")));
        assertThat(bundle.workspaceClient()).containsEntry("context_contract", Map.of("enabled", true));
        assertThat(bundle.contextSources()).hasSize(1);
        assertThat(bundle.contextBlocksHealth()).containsEntry("ready", true);
    }
}

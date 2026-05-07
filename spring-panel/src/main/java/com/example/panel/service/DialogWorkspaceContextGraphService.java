package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceContextGraphService {

    private final DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService;
    private final DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService;
    private final DialogWorkspaceContextContractService dialogWorkspaceContextContractService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;
    private final DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService;

    public DialogWorkspaceContextGraphService(DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService,
                                              DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService,
                                              DialogWorkspaceContextContractService dialogWorkspaceContextContractService,
                                              DialogClientContextReadService dialogClientContextReadService,
                                              DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService,
                                              DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService) {
        this.dialogWorkspaceContextSourceService = dialogWorkspaceContextSourceService;
        this.dialogWorkspaceContextBlockService = dialogWorkspaceContextBlockService;
        this.dialogWorkspaceContextContractService = dialogWorkspaceContextContractService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
        this.dialogWorkspaceRequestContractService = dialogWorkspaceRequestContractService;
    }

    public ContextGraphBundle build(Map<String, Object> settings,
                                    DialogListItem summary,
                                    Map<String, Object> workspaceClient,
                                    Map<String, String> attributeLabels,
                                    Map<String, Object> effectiveProfileEnrichment,
                                    Map<String, Object> filteredProfileEnrichment,
                                    Map<String, Object> externalLinks,
                                    List<Map<String, Object>> clientHistory,
                                    List<Map<String, Object>> relatedEvents) {
        Map<String, String> profileMatchIncomingValues = new LinkedHashMap<>();
        dialogWorkspaceRequestContractService.putProfileMatchField(profileMatchIncomingValues, "business", summary.businessLabel());
        dialogWorkspaceRequestContractService.putProfileMatchField(profileMatchIncomingValues, "location", summary.location());
        dialogWorkspaceRequestContractService.putProfileMatchField(profileMatchIncomingValues, "city", workspaceClient.get("city"));
        dialogWorkspaceRequestContractService.putProfileMatchField(profileMatchIncomingValues, "country", workspaceClient.get("country"));
        Map<String, Object> profileMatchCandidates = dialogClientContextReadService.loadDialogProfileMatchCandidates(profileMatchIncomingValues, 5);
        if (!profileMatchCandidates.isEmpty()) {
            workspaceClient.put("profile_match_candidates", profileMatchCandidates);
        }

        Map<String, Object> profileHealth = dialogWorkspaceClientProfileService.buildProfileHealth(settings, workspaceClient, attributeLabels);
        if (!profileHealth.isEmpty()) {
            workspaceClient.put("profile_health", profileHealth);
        }

        List<Map<String, Object>> contextSources = dialogWorkspaceContextSourceService.buildContextSources(
                settings,
                workspaceClient,
                filteredProfileEnrichment,
                effectiveProfileEnrichment,
                externalLinks
        );
        if (!contextSources.isEmpty()) {
            workspaceClient.put("context_sources", contextSources);
        }

        List<Map<String, Object>> attributePolicies = dialogWorkspaceContextSourceService.buildContextAttributePolicies(
                workspaceClient,
                profileHealth,
                contextSources
        );
        if (!attributePolicies.isEmpty()) {
            workspaceClient.put("attribute_policies", attributePolicies);
        }

        List<Map<String, Object>> contextBlocks = dialogWorkspaceContextBlockService.buildContextBlocks(
                settings,
                profileHealth,
                contextSources,
                clientHistory,
                relatedEvents,
                null,
                externalLinks
        );
        Map<String, Object> contextBlocksHealth = dialogWorkspaceContextBlockService.buildBlocksHealth(contextBlocks);
        Map<String, Object> contextContract = dialogWorkspaceContextContractService.buildContextContract(
                settings,
                summary,
                workspaceClient,
                contextSources,
                contextBlocks
        );
        if (!contextContract.isEmpty()) {
            workspaceClient.put("context_contract", contextContract);
        }

        return new ContextGraphBundle(
                workspaceClient,
                profileMatchCandidates,
                profileHealth,
                contextSources,
                attributePolicies,
                contextBlocks,
                contextBlocksHealth,
                contextContract
        );
    }

    public record ContextGraphBundle(Map<String, Object> workspaceClient,
                                     Map<String, Object> profileMatchCandidates,
                                     Map<String, Object> profileHealth,
                                     List<Map<String, Object>> contextSources,
                                     List<Map<String, Object>> attributePolicies,
                                     List<Map<String, Object>> contextBlocks,
                                     Map<String, Object> contextBlocksHealth,
                                     Map<String, Object> contextContract) {
    }
}

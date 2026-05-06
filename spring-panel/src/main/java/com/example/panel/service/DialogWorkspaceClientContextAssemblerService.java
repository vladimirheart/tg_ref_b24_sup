package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DialogWorkspaceClientContextAssemblerService {

    private final DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService;
    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;
    private final DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService;
    private final DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService;
    private final DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService;
    private final DialogWorkspaceContextContractService dialogWorkspaceContextContractService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService;

    public DialogWorkspaceClientContextAssemblerService(DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService,
                                                        DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService,
                                                        DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService,
                                                        DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService,
                                                        DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService,
                                                        DialogWorkspaceContextContractService dialogWorkspaceContextContractService,
                                                        DialogClientContextReadService dialogClientContextReadService,
                                                        DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService) {
        this.dialogWorkspaceExternalProfileService = dialogWorkspaceExternalProfileService;
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
        this.dialogWorkspaceContextBlockService = dialogWorkspaceContextBlockService;
        this.dialogWorkspaceClientPayloadService = dialogWorkspaceClientPayloadService;
        this.dialogWorkspaceContextSourceService = dialogWorkspaceContextSourceService;
        this.dialogWorkspaceContextContractService = dialogWorkspaceContextContractService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogWorkspaceRequestContractService = dialogWorkspaceRequestContractService;
    }

    public WorkspaceClientContextBundle assemble(Map<String, Object> settings,
                                                 DialogListItem summary,
                                                 String ticketId,
                                                 List<Map<String, Object>> clientHistory,
                                                 List<Map<String, Object>> relatedEvents,
                                                 Map<String, Object> profileEnrichment) {
        Map<String, Object> effectiveProfileEnrichment = mergeExternalProfileEnrichment(settings, summary, ticketId, profileEnrichment);
        Set<String> hiddenProfileAttributes = dialogWorkspaceClientPayloadService.resolveHiddenClientAttributes(settings);
        Map<String, Object> filteredProfileEnrichment = dialogWorkspaceClientPayloadService.filterProfileEnrichment(
                effectiveProfileEnrichment,
                hiddenProfileAttributes
        );

        Map<String, Object> workspaceClient = new LinkedHashMap<>();
        workspaceClient.put("id", summary.userId());
        workspaceClient.put("name", summary.displayClientName());
        workspaceClient.put("language", "ru");
        workspaceClient.put("username", summary.username());
        workspaceClient.put("status", summary.clientStatusLabel());
        workspaceClient.put("channel", summary.channelLabel());
        workspaceClient.put("business", summary.businessLabel());
        workspaceClient.put("location", summary.location());
        workspaceClient.put("responsible", summary.responsible());
        workspaceClient.put("unread_count", summary.unreadCount());
        workspaceClient.put("rating", summary.rating());
        workspaceClient.put("last_message_at", summary.lastMessageTimestamp());
        workspaceClient.put("segments", dialogWorkspaceClientProfileService.buildClientSegments(summary, filteredProfileEnrichment, settings));

        Map<String, Object> externalLinks = dialogWorkspaceClientPayloadService.resolveExternalProfileLinks(
                settings, summary, ticketId, filteredProfileEnrichment
        );
        if (!externalLinks.isEmpty()) {
            workspaceClient.put("external_links", externalLinks);
        }

        Map<String, String> attributeLabels = dialogWorkspaceClientPayloadService.resolveClientAttributeLabels(settings);
        List<String> attributeOrder = dialogWorkspaceClientPayloadService.resolveClientAttributeOrder(settings);
        if (!attributeLabels.isEmpty()) {
            workspaceClient.put("attribute_labels", attributeLabels);
        }
        if (!attributeOrder.isEmpty()) {
            workspaceClient.put("attribute_order", attributeOrder);
        }
        if (!filteredProfileEnrichment.isEmpty()) {
            workspaceClient.putAll(filteredProfileEnrichment);
        }

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

        return new WorkspaceClientContextBundle(
                workspaceClient,
                clientHistory,
                profileMatchCandidates,
                relatedEvents,
                profileHealth,
                contextSources,
                attributePolicies,
                contextBlocks,
                contextBlocksHealth,
                contextContract
        );
    }

    private Map<String, Object> mergeExternalProfileEnrichment(Map<String, Object> settings,
                                                               DialogListItem summary,
                                                               String ticketId,
                                                               Map<String, Object> profileEnrichment) {
        Object rawDialogConfig = settings != null ? settings.get("dialog_config") : null;
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig) || summary == null) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        String externalUrlTemplate = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_url")));
        if (!StringUtils.hasText(externalUrlTemplate)) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, String> placeholders = dialogWorkspaceClientPayloadService.buildExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        String resolvedUrl = externalUrlTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolvedUrl = trimToNull(resolvedUrl);
        if (!StringUtils.hasText(resolvedUrl)) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, Object> externalProfileEnrichment = dialogWorkspaceExternalProfileService.resolveProfile(dialogConfig, resolvedUrl);
        if (externalProfileEnrichment.isEmpty()) {
            return profileEnrichment == null ? Map.of() : profileEnrichment;
        }
        Map<String, Object> mergedEnrichment = new LinkedHashMap<>();
        if (profileEnrichment != null) {
            mergedEnrichment.putAll(profileEnrichment);
        }
        externalProfileEnrichment.forEach(mergedEnrichment::putIfAbsent);
        return mergedEnrichment;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record WorkspaceClientContextBundle(Map<String, Object> workspaceClient,
                                               List<Map<String, Object>> clientHistory,
                                               Map<String, Object> profileMatchCandidates,
                                               List<Map<String, Object>> relatedEvents,
                                               Map<String, Object> profileHealth,
                                               List<Map<String, Object>> contextSources,
                                               List<Map<String, Object>> attributePolicies,
                                               List<Map<String, Object>> contextBlocks,
                                               Map<String, Object> contextBlocksHealth,
                                               Map<String, Object> contextContract) {
    }
}

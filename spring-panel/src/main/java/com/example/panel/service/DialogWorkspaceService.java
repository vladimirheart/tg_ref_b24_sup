package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DialogWorkspaceService {

    private final DialogDetailsReadService dialogDetailsReadService;
    private final SharedConfigService sharedConfigService;
    private final DialogAuthorizationService dialogAuthorizationService;
    private final SlaEscalationWebhookNotifier slaEscalationWebhookNotifier;
    private final DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService;
    private final DialogWorkspaceParityService dialogWorkspaceParityService;
    private final DialogWorkspaceNavigationService dialogWorkspaceNavigationService;
    private final DialogWorkspaceRolloutService dialogWorkspaceRolloutService;
    private final DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService;
    private final DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService;
    private final DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService;
    private final DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService;
    private final DialogWorkspaceContextContractService dialogWorkspaceContextContractService;
    private final DialogSlaRuntimeService dialogSlaRuntimeService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogConversationReadService dialogConversationReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService;
    private final DialogWorkspacePayloadAssemblerService dialogWorkspacePayloadAssemblerService;
    private static final int DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
    private static final int DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
    public DialogWorkspaceService(DialogDetailsReadService dialogDetailsReadService,
                                  SharedConfigService sharedConfigService,
                                  DialogAuthorizationService dialogAuthorizationService,
                                  SlaEscalationWebhookNotifier slaEscalationWebhookNotifier,
                                  DialogWorkspaceExternalProfileService dialogWorkspaceExternalProfileService,
                                  DialogWorkspaceParityService dialogWorkspaceParityService,
                                  DialogWorkspaceNavigationService dialogWorkspaceNavigationService,
                                  DialogWorkspaceRolloutService dialogWorkspaceRolloutService,
                                  DialogWorkspaceClientProfileService dialogWorkspaceClientProfileService,
                                  DialogWorkspaceContextBlockService dialogWorkspaceContextBlockService,
                                  DialogWorkspaceClientPayloadService dialogWorkspaceClientPayloadService,
                                  DialogWorkspaceContextSourceService dialogWorkspaceContextSourceService,
                                  DialogWorkspaceContextContractService dialogWorkspaceContextContractService,
                                  DialogSlaRuntimeService dialogSlaRuntimeService,
                                  DialogClientContextReadService dialogClientContextReadService,
                                  DialogConversationReadService dialogConversationReadService,
                                  DialogResponsibilityService dialogResponsibilityService,
                                  DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService,
                                  DialogWorkspacePayloadAssemblerService dialogWorkspacePayloadAssemblerService) {
        this.dialogDetailsReadService = dialogDetailsReadService;
        this.sharedConfigService = sharedConfigService;
        this.dialogAuthorizationService = dialogAuthorizationService;
        this.slaEscalationWebhookNotifier = slaEscalationWebhookNotifier;
        this.dialogWorkspaceExternalProfileService = dialogWorkspaceExternalProfileService;
        this.dialogWorkspaceParityService = dialogWorkspaceParityService;
        this.dialogWorkspaceNavigationService = dialogWorkspaceNavigationService;
        this.dialogWorkspaceRolloutService = dialogWorkspaceRolloutService;
        this.dialogWorkspaceClientProfileService = dialogWorkspaceClientProfileService;
        this.dialogWorkspaceContextBlockService = dialogWorkspaceContextBlockService;
        this.dialogWorkspaceClientPayloadService = dialogWorkspaceClientPayloadService;
        this.dialogWorkspaceContextSourceService = dialogWorkspaceContextSourceService;
        this.dialogWorkspaceContextContractService = dialogWorkspaceContextContractService;
        this.dialogSlaRuntimeService = dialogSlaRuntimeService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogWorkspaceRequestContractService = dialogWorkspaceRequestContractService;
        this.dialogWorkspacePayloadAssemblerService = dialogWorkspacePayloadAssemblerService;
    }

    private Map<String, Object> resolveWorkspaceExternalProfileEnrichment(Map<String, Object> settings,
                                                                           DialogListItem summary,
                                                                           String ticketId,
                                                                           Map<String, Object> profileEnrichment) {
        Object rawDialogConfig = settings != null ? settings.get("dialog_config") : null;
        if (!(rawDialogConfig instanceof Map<?, ?> dialogConfig) || summary == null) {
            return Map.of();
        }
        String externalUrlTemplate = trimToNull(String.valueOf(dialogConfig.get("workspace_client_external_profile_url")));
        if (!StringUtils.hasText(externalUrlTemplate)) {
            return Map.of();
        }
        Map<String, String> placeholders = dialogWorkspaceClientPayloadService.buildExternalLinkPlaceholders(summary, ticketId, profileEnrichment);
        String resolvedUrl = externalUrlTemplate;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            resolvedUrl = resolvedUrl.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        resolvedUrl = trimToNull(resolvedUrl);
        if (!StringUtils.hasText(resolvedUrl)) {
            return Map.of();
        }
        return dialogWorkspaceExternalProfileService.resolveProfile(dialogConfig, resolvedUrl);
    }

    private String humanizeMacroVariableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "Переменная";
        }
        return Arrays.stream(key.trim().toLowerCase().split("_"))
                .filter(StringUtils::hasText)
                .map(token -> Character.toUpperCase(token.charAt(0)) + token.substring(1))
                .collect(Collectors.joining(" "));
    }

    public ResponseEntity<?> workspace(String ticketId,
                                       Long channelId,
                                       String include,
                                       Integer limit,
                                       String cursor,
                                       Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
        Optional<DialogDetails> details = dialogDetailsReadService.loadDialogDetails(ticketId, channelId, operator);
        if (details.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }

        DialogDetails dialogDetails = details.get();
        DialogListItem summary = dialogDetails.summary();
        Set<String> includeSections = dialogWorkspaceRequestContractService.resolveWorkspaceInclude(include);
        int resolvedLimit = dialogWorkspaceRequestContractService.resolveWorkspaceLimit(limit);
        int resolvedCursor = dialogWorkspaceRequestContractService.resolveWorkspaceCursor(cursor);
        List<ChatMessageDto> history = dialogConversationReadService.loadHistory(ticketId, channelId);

        int safeCursor = Math.min(Math.max(resolvedCursor, 0), history.size());
        int endExclusive = Math.min(safeCursor + resolvedLimit, history.size());
        List<ChatMessageDto> pagedHistory = history.subList(safeCursor, endExclusive);
        boolean hasMore = endExclusive < history.size();
        Integer nextCursor = hasMore ? endExclusive : null;

        int slaTargetMinutes = resolveDialogConfigMinutes("sla_target_minutes", DEFAULT_SLA_TARGET_MINUTES);
        int slaWarningMinutes = Math.min(
                resolveDialogConfigMinutes("sla_warning_minutes", DEFAULT_SLA_WARNING_MINUTES),
                slaTargetMinutes
        );
        int slaCriticalMinutes = Math.min(resolveDialogConfigMinutes("sla_critical_minutes", 30), slaTargetMinutes);
        String slaState = dialogSlaRuntimeService.resolveSlaState(summary.createdAt(), slaTargetMinutes, slaWarningMinutes, summary.statusKey());
        Long slaMinutesLeft = dialogSlaRuntimeService.resolveSlaMinutesLeft(summary.createdAt(), slaTargetMinutes, summary.statusKey(), System.currentTimeMillis());
        Map<String, Object> settings = sharedConfigService.loadSettings();
        int workspaceHistoryLimit = dialogWorkspaceRequestContractService.resolveDialogConfigRangeMinutes(settings, "workspace_context_history_limit", 5, 1, 20);
        int workspaceRelatedEventsLimit = dialogWorkspaceRequestContractService.resolveDialogConfigRangeMinutes(settings, "workspace_context_related_events_limit", 5, 1, 20);
        List<Map<String, Object>> clientHistory = dialogClientContextReadService.loadClientDialogHistory(summary.userId(), ticketId, workspaceHistoryLimit);
        List<Map<String, Object>> relatedEvents = dialogClientContextReadService.loadRelatedEvents(ticketId, workspaceRelatedEventsLimit);
        Map<String, Object> profileEnrichment = dialogClientContextReadService.loadClientProfileEnrichment(summary.userId());
        Map<String, Object> externalProfileEnrichment = resolveWorkspaceExternalProfileEnrichment(settings, summary, ticketId, profileEnrichment);
        if (!externalProfileEnrichment.isEmpty()) {
            Map<String, Object> mergedEnrichment = new LinkedHashMap<>(profileEnrichment);
            externalProfileEnrichment.forEach(mergedEnrichment::putIfAbsent);
            profileEnrichment = mergedEnrichment;
        }
        Set<String> hiddenProfileAttributes = dialogWorkspaceClientPayloadService.resolveHiddenClientAttributes(settings);
        Map<String, Object> filteredProfileEnrichment = dialogWorkspaceClientPayloadService.filterProfileEnrichment(profileEnrichment, hiddenProfileAttributes);
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
        Map<String, Object> externalLinks = dialogWorkspaceClientPayloadService.resolveExternalProfileLinks(settings, summary, ticketId, filteredProfileEnrichment);
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
                externalProfileEnrichment,
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
                slaState,
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

        Map<String, Object> workspaceRollout = dialogWorkspaceRolloutService.resolveRolloutMeta(settings);
        Map<String, Object> workspaceNavigation = dialogWorkspaceNavigationService.buildNavigationMeta(settings, operator, ticketId);
        Map<String, Object> workspaceSlaPolicyRaw = slaEscalationWebhookNotifier.buildRoutingPolicySnapshot(summary, settings);
        Map<String, Object> workspaceSlaPolicy = workspaceSlaPolicyRaw != null ? workspaceSlaPolicyRaw : Map.of();
        Map<String, Object> workspacePermissions = includeSections.contains("permissions")
                ? dialogAuthorizationService.resolveWorkspacePermissions(authentication)
                : Map.of(
                "can_reply", false,
                "can_assign", false,
                "can_close", false,
                "can_snooze", false,
                "can_bulk", false,
                "unavailable", true
        );
        Map<String, Object> workspaceComposer = dialogWorkspaceParityService.buildComposerMeta(summary, history, workspacePermissions);
        Map<String, Object> workspaceParity = dialogWorkspaceParityService.buildParityMeta(
                includeSections,
                workspaceClient,
                clientHistory,
                relatedEvents,
                profileHealth,
                contextBlocksHealth,
                workspacePermissions,
                workspaceComposer,
                slaState,
                summary,
                workspaceRollout
        );

        Map<String, Object> payload = dialogWorkspacePayloadAssemblerService.buildWorkspacePayload(
                includeSections,
                resolvedLimit,
                safeCursor,
                pagedHistory,
                nextCursor,
                hasMore,
                workspaceClient,
                clientHistory,
                profileMatchCandidates,
                relatedEvents,
                profileHealth,
                contextSources,
                attributePolicies,
                contextBlocks,
                contextBlocksHealth,
                contextContract,
                workspacePermissions,
                workspaceComposer,
                slaTargetMinutes,
                slaWarningMinutes,
                slaCriticalMinutes,
                dialogSlaRuntimeService.computeDeadlineAt(summary.createdAt(), slaTargetMinutes),
                slaState,
                slaMinutesLeft,
                workspaceSlaPolicy,
                workspaceRollout,
                workspaceNavigation,
                workspaceParity
        );
        payload.put("conversation", summary);
        return ResponseEntity.ok(payload);
    }

    private int resolveDialogConfigMinutes(String key, int fallbackValue) {
        return dialogSlaRuntimeService.resolveDialogConfigMinutes(sharedConfigService.loadSettings(), key, fallbackValue);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}

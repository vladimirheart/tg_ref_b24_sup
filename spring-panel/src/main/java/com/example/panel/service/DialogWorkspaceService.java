package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DialogWorkspaceService {

    private final DialogDetailsReadService dialogDetailsReadService;
    private final SharedConfigService sharedConfigService;
    private final DialogAuthorizationService dialogAuthorizationService;
    private final DialogWorkspaceParityService dialogWorkspaceParityService;
    private final DialogWorkspaceNavigationService dialogWorkspaceNavigationService;
    private final DialogWorkspaceRolloutService dialogWorkspaceRolloutService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogConversationReadService dialogConversationReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService;
    private final DialogWorkspacePayloadAssemblerService dialogWorkspacePayloadAssemblerService;
    private final DialogWorkspaceHistorySliceService dialogWorkspaceHistorySliceService;
    private final DialogWorkspaceClientContextAssemblerService dialogWorkspaceClientContextAssemblerService;
    private final DialogWorkspaceSlaViewService dialogWorkspaceSlaViewService;

    public DialogWorkspaceService(DialogDetailsReadService dialogDetailsReadService,
                                  SharedConfigService sharedConfigService,
                                  DialogAuthorizationService dialogAuthorizationService,
                                  DialogWorkspaceParityService dialogWorkspaceParityService,
                                  DialogWorkspaceNavigationService dialogWorkspaceNavigationService,
                                  DialogWorkspaceRolloutService dialogWorkspaceRolloutService,
                                  DialogClientContextReadService dialogClientContextReadService,
                                  DialogConversationReadService dialogConversationReadService,
                                  DialogResponsibilityService dialogResponsibilityService,
                                  DialogWorkspaceRequestContractService dialogWorkspaceRequestContractService,
                                  DialogWorkspacePayloadAssemblerService dialogWorkspacePayloadAssemblerService,
                                  DialogWorkspaceHistorySliceService dialogWorkspaceHistorySliceService,
                                  DialogWorkspaceClientContextAssemblerService dialogWorkspaceClientContextAssemblerService,
                                  DialogWorkspaceSlaViewService dialogWorkspaceSlaViewService) {
        this.dialogDetailsReadService = dialogDetailsReadService;
        this.sharedConfigService = sharedConfigService;
        this.dialogAuthorizationService = dialogAuthorizationService;
        this.dialogWorkspaceParityService = dialogWorkspaceParityService;
        this.dialogWorkspaceNavigationService = dialogWorkspaceNavigationService;
        this.dialogWorkspaceRolloutService = dialogWorkspaceRolloutService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogWorkspaceRequestContractService = dialogWorkspaceRequestContractService;
        this.dialogWorkspacePayloadAssemblerService = dialogWorkspacePayloadAssemblerService;
        this.dialogWorkspaceHistorySliceService = dialogWorkspaceHistorySliceService;
        this.dialogWorkspaceClientContextAssemblerService = dialogWorkspaceClientContextAssemblerService;
        this.dialogWorkspaceSlaViewService = dialogWorkspaceSlaViewService;
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
        DialogWorkspaceHistorySliceService.HistorySlice historySlice =
                dialogWorkspaceHistorySliceService.slice(history, resolvedCursor, resolvedLimit);

        Map<String, Object> settings = sharedConfigService.loadSettings();
        DialogWorkspaceSlaViewService.SlaView slaView = dialogWorkspaceSlaViewService.build(summary, settings);
        int workspaceHistoryLimit = dialogWorkspaceRequestContractService.resolveDialogConfigRangeMinutes(settings, "workspace_context_history_limit", 5, 1, 20);
        int workspaceRelatedEventsLimit = dialogWorkspaceRequestContractService.resolveDialogConfigRangeMinutes(settings, "workspace_context_related_events_limit", 5, 1, 20);
        List<Map<String, Object>> clientHistory = dialogClientContextReadService.loadClientDialogHistory(summary.userId(), ticketId, workspaceHistoryLimit);
        List<Map<String, Object>> relatedEvents = dialogClientContextReadService.loadRelatedEvents(ticketId, workspaceRelatedEventsLimit);
        Map<String, Object> profileEnrichment = dialogClientContextReadService.loadClientProfileEnrichment(summary.userId());
        DialogWorkspaceClientContextAssemblerService.WorkspaceClientContextBundle clientContextBundle =
                dialogWorkspaceClientContextAssemblerService.assemble(
                        settings,
                        summary,
                        ticketId,
                        clientHistory,
                        relatedEvents,
                        profileEnrichment
                );

        Map<String, Object> workspaceRollout = dialogWorkspaceRolloutService.resolveRolloutMeta(settings);
        Map<String, Object> workspaceNavigation = dialogWorkspaceNavigationService.buildNavigationMeta(settings, operator, ticketId);
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
                clientContextBundle.workspaceClient(),
                clientHistory,
                relatedEvents,
                clientContextBundle.profileHealth(),
                clientContextBundle.contextBlocksHealth(),
                workspacePermissions,
                workspaceComposer,
                slaView.state(),
                summary,
                workspaceRollout
        );

        Map<String, Object> payload = dialogWorkspacePayloadAssemblerService.buildWorkspacePayload(
                includeSections,
                resolvedLimit,
                historySlice.safeCursor(),
                historySlice.pagedHistory(),
                historySlice.nextCursor(),
                historySlice.hasMore(),
                clientContextBundle.workspaceClient(),
                clientContextBundle.clientHistory(),
                clientContextBundle.profileMatchCandidates(),
                clientContextBundle.relatedEvents(),
                clientContextBundle.profileHealth(),
                clientContextBundle.contextSources(),
                clientContextBundle.attributePolicies(),
                clientContextBundle.contextBlocks(),
                clientContextBundle.contextBlocksHealth(),
                clientContextBundle.contextContract(),
                workspacePermissions,
                workspaceComposer,
                slaView.targetMinutes(),
                slaView.warningMinutes(),
                slaView.criticalMinutes(),
                slaView.deadlineAt(),
                slaView.state(),
                slaView.minutesLeft(),
                slaView.policy(),
                workspaceRollout,
                workspaceNavigation,
                workspaceParity
        );
        payload.put("conversation", summary);
        return ResponseEntity.ok(payload);
    }
}

package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.model.dialog.DialogSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DialogService {

    private final DialogWorkspaceTelemetrySummaryAssemblerService dialogWorkspaceTelemetrySummaryAssemblerService;
    private final DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogConversationReadService dialogConversationReadService;
    private final DialogDetailsReadService dialogDetailsReadService;
    private final DialogAuditService dialogAuditService;
    private final DialogTicketLifecycleService dialogTicketLifecycleService;

    public DialogService(DialogWorkspaceTelemetrySummaryAssemblerService dialogWorkspaceTelemetrySummaryAssemblerService,
                         DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService,
                         DialogLookupReadService dialogLookupReadService,
                         DialogResponsibilityService dialogResponsibilityService,
                         DialogClientContextReadService dialogClientContextReadService,
                         DialogConversationReadService dialogConversationReadService,
                         DialogDetailsReadService dialogDetailsReadService,
                         DialogAuditService dialogAuditService,
                         DialogTicketLifecycleService dialogTicketLifecycleService) {
        this.dialogWorkspaceTelemetrySummaryAssemblerService = dialogWorkspaceTelemetrySummaryAssemblerService;
        this.dialogMacroGovernanceAuditService = dialogMacroGovernanceAuditService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.dialogDetailsReadService = dialogDetailsReadService;
        this.dialogAuditService = dialogAuditService;
        this.dialogTicketLifecycleService = dialogTicketLifecycleService;
    }

    public DialogSummary loadSummary() {
        return dialogLookupReadService.loadSummary();
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        return dialogLookupReadService.loadDialogs(currentOperator);
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        return dialogLookupReadService.findDialog(ticketId, operator);
    }

    public void assignResponsibleIfMissing(String ticketId, String username) {
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, username);
    }

    public void markDialogAsRead(String ticketId, String operator) {
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, newResponsible, assignedBy);
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        return dialogConversationReadService.loadHistory(ticketId, channelId);
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId, String operator) {
        return dialogDetailsReadService.loadDialogDetails(ticketId, channelId, operator);
    }

    public Optional<DialogPreviousHistoryPage> loadPreviousDialogHistory(String ticketId, int offset) {
        return dialogConversationReadService.loadPreviousDialogHistory(ticketId, offset);
    }

    public List<Map<String, Object>> loadClientDialogHistory(Long userId, String currentTicketId, int limit) {
        return dialogClientContextReadService.loadClientDialogHistory(userId, currentTicketId, limit);
    }

    public Map<String, Object> loadClientProfileEnrichment(Long userId) {
        return dialogClientContextReadService.loadClientProfileEnrichment(userId);
    }

    public Map<String, Object> loadDialogProfileMatchCandidates(Map<String, String> incomingValues, int perFieldLimit) {
        return dialogClientContextReadService.loadDialogProfileMatchCandidates(incomingValues, perFieldLimit);
    }

    public List<Map<String, Object>> loadRelatedEvents(String ticketId, int limit) {
        return dialogClientContextReadService.loadRelatedEvents(ticketId, limit);
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        dialogAuditService.logDialogActionAudit(ticketId, actor, action, result, detail);
    }

    public void logWorkspaceTelemetry(String actor,
                                      String eventType,
                                      String eventGroup,
                                      String ticketId,
                                      String reason,
                                      String errorCode,
                                      String contractVersion,
                                      Long durationMs,
                                      String experimentName,
                                      String experimentCohort,
                                      String operatorSegment,
                                      List<String> primaryKpis,
                                      List<String> secondaryKpis,
                                      String templateId,
                                      String templateName) {
        dialogAuditService.logWorkspaceTelemetry(
                actor,
                eventType,
                eventGroup,
                ticketId,
                reason,
                errorCode,
                contractVersion,
                durationMs,
                experimentName,
                experimentCohort,
                operatorSegment,
                primaryKpis,
                secondaryKpis,
                templateId,
                templateName);
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days, String experimentName) {
        return dialogWorkspaceTelemetrySummaryAssemblerService.loadSummary(days, experimentName);
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days,
                                                             String experimentName,
                                                             Instant fromUtc,
                                                             Instant toUtc) {
        return dialogWorkspaceTelemetrySummaryAssemblerService.loadSummary(days, experimentName, fromUtc, toUtc);
    }

    public Map<String, Object> buildMacroGovernanceAudit(Map<String, Object> settings) {
        return dialogMacroGovernanceAuditService.buildAudit(settings);
    }

    public List<String> loadTicketCategories(String ticketId) {
        return dialogConversationReadService.loadTicketCategories(ticketId);
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        dialogTicketLifecycleService.setTicketCategories(ticketId, categories);
    }

    public DialogResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        return dialogTicketLifecycleService.resolveTicket(ticketId, operator, categories);
    }

    public DialogResolveResult reopenTicket(String ticketId, String operator) {
        return dialogTicketLifecycleService.reopenTicket(ticketId, operator);
    }
}




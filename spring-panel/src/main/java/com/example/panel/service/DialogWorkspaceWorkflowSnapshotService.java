package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DialogWorkspaceWorkflowSnapshotService {

    private final DialogParticipantService dialogParticipantService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogTriagePreferenceService dialogTriagePreferenceService;

    public DialogWorkspaceWorkflowSnapshotService(DialogParticipantService dialogParticipantService,
                                                  DialogResponsibilityService dialogResponsibilityService,
                                                  DialogTriagePreferenceService dialogTriagePreferenceService) {
        this.dialogParticipantService = dialogParticipantService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogTriagePreferenceService = dialogTriagePreferenceService;
    }

    public Map<String, Object> buildWorkflowSnapshot(String ticketId,
                                                     String operator,
                                                     DialogListItem summary,
                                                     Map<String, Object> workspacePermissions) {
        List<DialogParticipantDto> participants = dialogParticipantService.loadParticipants(ticketId);
        String responsibleUsername = resolveResponsibleUsername(ticketId, summary);
        Optional<DialogOperatorOption> responsibleProfile = StringUtils.hasText(responsibleUsername)
                ? dialogParticipantService.findOperator(responsibleUsername)
                : Optional.empty();
        boolean canAssign = workspacePermissions != null && Boolean.TRUE.equals(workspacePermissions.get("can_assign"));

        List<DialogOperatorOption> assignableOperators = canAssign
                ? dialogParticipantService.loadAssignableOperators()
                : List.of();
        Set<String> participantUsernames = participants.stream()
                .map(DialogParticipantDto::username)
                .map(this::normalizeIdentity)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String normalizedResponsible = normalizeIdentity(responsibleUsername);
        List<DialogOperatorOption> reassignCandidates = assignableOperators.stream()
                .filter(option -> !sameIdentity(option.username(), normalizedResponsible))
                .toList();
        List<DialogOperatorOption> participantCandidates = assignableOperators.stream()
                .filter(option -> !sameIdentity(option.username(), normalizedResponsible))
                .filter(option -> !participantUsernames.contains(normalizeIdentity(option.username())))
                .toList();

        Map<String, Object> responsible = new LinkedHashMap<>();
        responsible.put("assigned", StringUtils.hasText(responsibleUsername));
        responsible.put("username", responsibleUsername);
        responsible.put("display_name", firstNonBlank(
                summary != null ? summary.responsibleDisplayName() : null,
                responsibleProfile.map(DialogOperatorOption::displayLabel).orElse(null),
                summary != null ? summary.responsible() : null,
                responsibleUsername
        ));
        responsible.put("avatar_url", firstNonBlank(
                summary != null ? summary.responsibleAvatarUrl() : null,
                responsibleProfile.map(DialogOperatorOption::avatarUrl).orElse(null)
        ));
        responsible.put("department", responsibleProfile.map(DialogOperatorOption::department).orElse(null));
        responsible.put("role", responsibleProfile.map(DialogOperatorOption::role).orElse(null));

        Map<String, Object> collaboration = new LinkedHashMap<>();
        collaboration.put("assigned", StringUtils.hasText(responsibleUsername));
        collaboration.put("participant_count", participants.size());
        collaboration.put("can_reassign", canAssign && !isClosedDialog(summary) && !reassignCandidates.isEmpty());
        collaboration.put("can_manage_participants", canAssign && !isClosedDialog(summary));
        collaboration.put("reassign_candidate_count", reassignCandidates.size());
        collaboration.put("participant_candidate_count", participantCandidates.size());

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("responsible", responsible);
        workflow.put("participants", participants);
        workflow.put("reassign_candidates", reassignCandidates);
        workflow.put("participant_candidates", participantCandidates);
        workflow.put("triage_preferences", dialogTriagePreferenceService.loadForOperator(operator));
        workflow.put("collaboration", collaboration);
        workflow.put("actions", buildActionAvailability(
                operator,
                responsibleUsername,
                summary,
                participants,
                reassignCandidates,
                participantCandidates,
                workspacePermissions
        ));
        return workflow;
    }

    private Map<String, Object> buildActionAvailability(String operator,
                                                        String responsibleUsername,
                                                        DialogListItem summary,
                                                        List<DialogParticipantDto> participants,
                                                        List<DialogOperatorOption> reassignCandidates,
                                                        List<DialogOperatorOption> participantCandidates,
                                                        Map<String, Object> workspacePermissions) {
        boolean closed = isClosedDialog(summary);
        boolean canReply = workspacePermissions != null && Boolean.TRUE.equals(workspacePermissions.get("can_reply"));
        boolean canAssign = workspacePermissions != null && Boolean.TRUE.equals(workspacePermissions.get("can_assign"));
        boolean canClose = workspacePermissions != null && Boolean.TRUE.equals(workspacePermissions.get("can_close"));
        boolean assignedToOperator = sameIdentity(responsibleUsername, operator);
        boolean hasParticipants = participants != null && !participants.isEmpty();
        boolean hasReassignCandidates = reassignCandidates != null && !reassignCandidates.isEmpty();
        boolean hasParticipantCandidates = participantCandidates != null && !participantCandidates.isEmpty();

        Map<String, Object> actions = new LinkedHashMap<>();
        actions.put("reply", actionAvailability(canReply, canReply ? null : "permission_denied"));
        actions.put("reply_media", actionAvailability(canReply, canReply ? null : "permission_denied"));
        actions.put("take", actionAvailability(
                canAssign && !assignedToOperator,
                !canAssign ? "permission_denied" : (assignedToOperator ? "already_assigned_to_operator" : null)
        ));
        actions.put("resolve", actionAvailability(
                canClose && !closed,
                !canClose ? "permission_denied" : (closed ? "already_closed" : null)
        ));
        actions.put("reopen", actionAvailability(
                canClose && closed,
                !canClose ? "permission_denied" : (!closed ? "not_closed" : null)
        ));
        actions.put("reassign", actionAvailability(
                canAssign && !closed && hasReassignCandidates,
                !canAssign ? "permission_denied"
                        : (closed ? "closed_dialog"
                        : (!hasReassignCandidates ? "no_reassign_candidates" : null))
        ));
        actions.put("participants_add", actionAvailability(
                canAssign && !closed && hasParticipantCandidates,
                !canAssign ? "permission_denied"
                        : (closed ? "closed_dialog"
                        : (!hasParticipantCandidates ? "no_participant_candidates" : null))
        ));
        actions.put("participants_remove", actionAvailability(
                canAssign && hasParticipants,
                !canAssign ? "permission_denied" : (!hasParticipants ? "no_participants" : null)
        ));
        return actions;
    }

    private Map<String, Object> actionAvailability(boolean enabled, String disabledReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("enabled", enabled);
        payload.put("disabled_reason", enabled ? null : disabledReason);
        return payload;
    }

    private String resolveResponsibleUsername(String ticketId, DialogListItem summary) {
        String fromSummary = firstNonBlank(summary != null ? summary.rawResponsible() : null);
        if (StringUtils.hasText(fromSummary)) {
            return fromSummary;
        }
        return firstNonBlank(dialogResponsibilityService.loadResponsible(ticketId));
    }

    private boolean sameIdentity(String left, String right) {
        String normalizedLeft = normalizeIdentity(left);
        String normalizedRight = normalizeIdentity(right);
        return StringUtils.hasText(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private boolean isClosedDialog(DialogListItem summary) {
        if (summary == null) {
            return false;
        }
        String statusKey = firstNonBlank(summary.statusKey());
        return "closed".equalsIgnoreCase(statusKey) || "auto_closed".equalsIgnoreCase(statusKey);
    }

    private String normalizeIdentity(String value) {
        String normalized = firstNonBlank(value);
        return normalized != null ? normalized.toLowerCase(Locale.ROOT) : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}

package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogWorkspaceWorkflowSnapshotServiceTest {

    @Test
    void buildWorkflowSnapshotProjectsResponsibleParticipantsCandidatesAndTriagePreferences() {
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogTriagePreferenceService dialogTriagePreferenceService = mock(DialogTriagePreferenceService.class);
        DialogWorkspaceWorkflowSnapshotService service = new DialogWorkspaceWorkflowSnapshotService(
                dialogParticipantService,
                dialogResponsibilityService,
                dialogTriagePreferenceService
        );

        when(dialogParticipantService.loadParticipants("T-WF-1")).thenReturn(List.of(
                new DialogParticipantDto("watcher_peer", "Watcher Peer", "/avatars/peer.png", "Ops", "Support", "2026-05-26T09:00:00Z", "watcher_owner")
        ));
        when(dialogParticipantService.findOperator("watcher_owner")).thenReturn(Optional.of(
                new DialogOperatorOption("watcher_owner", "Watcher Owner", "/avatars/owner.png", "Ops", "Lead")
        ));
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("watcher_owner", "Watcher Owner", "/avatars/owner.png", "Ops", "Lead"),
                new DialogOperatorOption("watcher_peer", "Watcher Peer", "/avatars/peer.png", "Ops", "Support"),
                new DialogOperatorOption("watcher_new", "Watcher New", "/avatars/new.png", "Backoffice", "Support")
        ));
        when(dialogTriagePreferenceService.loadForOperator("watcher_owner")).thenReturn(Map.of(
                "view", "sla_critical",
                "sort_mode", "default"
        ));

        Map<String, Object> snapshot = service.buildWorkflowSnapshot(
                "T-WF-1",
                "watcher_owner",
                dialogWithResponsible("T-WF-1", "watcher_owner", null, null),
                Map.of("can_assign", true)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> responsible = (Map<String, Object>) snapshot.get("responsible");
        @SuppressWarnings("unchecked")
        Map<String, Object> collaboration = (Map<String, Object>) snapshot.get("collaboration");
        @SuppressWarnings("unchecked")
        List<DialogParticipantDto> participants = (List<DialogParticipantDto>) snapshot.get("participants");
        @SuppressWarnings("unchecked")
        List<DialogOperatorOption> reassignCandidates = (List<DialogOperatorOption>) snapshot.get("reassign_candidates");
        @SuppressWarnings("unchecked")
        List<DialogOperatorOption> participantCandidates = (List<DialogOperatorOption>) snapshot.get("participant_candidates");
        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) snapshot.get("actions");
        @SuppressWarnings("unchecked")
        Map<String, Object> reassignAction = (Map<String, Object>) actions.get("reassign");
        @SuppressWarnings("unchecked")
        Map<String, Object> participantAddAction = (Map<String, Object>) actions.get("participants_add");
        @SuppressWarnings("unchecked")
        Map<String, Object> takeAction = (Map<String, Object>) actions.get("take");
        @SuppressWarnings("unchecked")
        Map<String, Object> categoriesAction = (Map<String, Object>) actions.get("categories");
        @SuppressWarnings("unchecked")
        Map<String, Object> spamAction = (Map<String, Object>) actions.get("spam");

        assertThat(responsible).containsEntry("assigned", true);
        assertThat(responsible).containsEntry("username", "watcher_owner");
        assertThat(responsible).containsEntry("display_name", "Watcher Owner");
        assertThat(responsible).containsEntry("avatar_url", "/avatars/owner.png");
        assertThat(participants).hasSize(1);
        assertThat(reassignCandidates).extracting(DialogOperatorOption::username).containsExactly("watcher_peer", "watcher_new");
        assertThat(participantCandidates).extracting(DialogOperatorOption::username).containsExactly("watcher_new");
        assertThat(snapshot).containsEntry("triage_preferences", Map.of("view", "sla_critical", "sort_mode", "default"));
        assertThat(collaboration).containsEntry("participant_count", 1);
        assertThat(collaboration).containsEntry("reassign_candidate_count", 2);
        assertThat(collaboration).containsEntry("participant_candidate_count", 1);
        assertThat(collaboration).containsEntry("can_reassign", true);
        assertThat(collaboration).containsEntry("can_manage_participants", true);
        assertThat(reassignAction).containsEntry("enabled", true);
        assertThat(participantAddAction).containsEntry("enabled", true);
        assertThat(takeAction).containsEntry("enabled", false);
        assertThat(takeAction).containsEntry("disabled_reason", "already_assigned_to_operator");
        assertThat(categoriesAction).containsEntry("enabled", false);
        assertThat(categoriesAction).containsEntry("disabled_reason", "permission_denied");
        assertThat(spamAction).containsEntry("enabled", false);
        assertThat(spamAction).containsEntry("disabled_reason", "permission_denied");
    }

    @Test
    void buildWorkflowSnapshotFallsBackToStoredResponsibleAndSkipsCandidatesWithoutAssignPermission() {
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogTriagePreferenceService dialogTriagePreferenceService = mock(DialogTriagePreferenceService.class);
        DialogWorkspaceWorkflowSnapshotService service = new DialogWorkspaceWorkflowSnapshotService(
                dialogParticipantService,
                dialogResponsibilityService,
                dialogTriagePreferenceService
        );

        when(dialogResponsibilityService.loadResponsible("T-WF-2")).thenReturn("watcher_owner");
        when(dialogParticipantService.loadParticipants("T-WF-2")).thenReturn(List.of());
        when(dialogParticipantService.findOperator("watcher_owner")).thenReturn(Optional.of(
                new DialogOperatorOption("watcher_owner", "Watcher Owner", "/avatars/owner.png", "Ops", "Lead")
        ));
        when(dialogTriagePreferenceService.loadForOperator("viewer_only")).thenReturn(Map.of());

        Map<String, Object> snapshot = service.buildWorkflowSnapshot(
                "T-WF-2",
                "viewer_only",
                dialogWithResponsible("T-WF-2", null, null, null),
                Map.of("can_assign", false)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> responsible = (Map<String, Object>) snapshot.get("responsible");
        @SuppressWarnings("unchecked")
        Map<String, Object> collaboration = (Map<String, Object>) snapshot.get("collaboration");
        @SuppressWarnings("unchecked")
        List<DialogOperatorOption> reassignCandidates = (List<DialogOperatorOption>) snapshot.get("reassign_candidates");
        @SuppressWarnings("unchecked")
        List<DialogOperatorOption> participantCandidates = (List<DialogOperatorOption>) snapshot.get("participant_candidates");
        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) snapshot.get("actions");
        @SuppressWarnings("unchecked")
        Map<String, Object> reassignAction = (Map<String, Object>) actions.get("reassign");
        @SuppressWarnings("unchecked")
        Map<String, Object> participantAddAction = (Map<String, Object>) actions.get("participants_add");
        @SuppressWarnings("unchecked")
        Map<String, Object> categoriesAction = (Map<String, Object>) actions.get("categories");
        @SuppressWarnings("unchecked")
        Map<String, Object> spamAction = (Map<String, Object>) actions.get("spam");

        assertThat(responsible).containsEntry("username", "watcher_owner");
        assertThat(responsible).containsEntry("display_name", "Watcher Owner");
        assertThat(reassignCandidates).isEmpty();
        assertThat(participantCandidates).isEmpty();
        assertThat(collaboration).containsEntry("can_reassign", false);
        assertThat(collaboration).containsEntry("can_manage_participants", false);
        assertThat(reassignAction).containsEntry("enabled", false);
        assertThat(reassignAction).containsEntry("disabled_reason", "permission_denied");
        assertThat(participantAddAction).containsEntry("enabled", false);
        assertThat(participantAddAction).containsEntry("disabled_reason", "permission_denied");
        assertThat(categoriesAction).containsEntry("enabled", false);
        assertThat(categoriesAction).containsEntry("disabled_reason", "permission_denied");
        assertThat(spamAction).containsEntry("enabled", false);
        assertThat(spamAction).containsEntry("disabled_reason", "permission_denied");
        verify(dialogParticipantService, never()).loadAssignableOperators();
    }

    @Test
    void buildWorkflowSnapshotProjectsClosedDialogActionGuards() {
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogTriagePreferenceService dialogTriagePreferenceService = mock(DialogTriagePreferenceService.class);
        DialogWorkspaceWorkflowSnapshotService service = new DialogWorkspaceWorkflowSnapshotService(
                dialogParticipantService,
                dialogResponsibilityService,
                dialogTriagePreferenceService
        );

        when(dialogParticipantService.loadParticipants("T-WF-3")).thenReturn(List.of(
                new DialogParticipantDto("watcher_peer", "Watcher Peer", "/avatars/peer.png", "Ops", "Support", "2026-05-26T09:00:00Z", "watcher_owner")
        ));
        when(dialogParticipantService.findOperator("watcher_owner")).thenReturn(Optional.of(
                new DialogOperatorOption("watcher_owner", "Watcher Owner", "/avatars/owner.png", "Ops", "Lead")
        ));
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("watcher_new", "Watcher New", "/avatars/new.png", "Ops", "Support")
        ));
        when(dialogTriagePreferenceService.loadForOperator("watcher_owner")).thenReturn(Map.of());

        Map<String, Object> snapshot = service.buildWorkflowSnapshot(
                "T-WF-3",
                "watcher_owner",
                closedDialog("T-WF-3", "watcher_owner"),
                Map.of("can_assign", true, "can_close", true, "can_reply", true)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) snapshot.get("actions");

        assertThat(((Map<?, ?>) actions.get("resolve")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) actions.get("resolve")).get("disabled_reason")).isEqualTo("already_closed");
        assertThat(((Map<?, ?>) actions.get("reopen")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("categories")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("spam")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("reassign")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) actions.get("reassign")).get("disabled_reason")).isEqualTo("closed_dialog");
        assertThat(((Map<?, ?>) actions.get("participants_add")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) actions.get("participants_add")).get("disabled_reason")).isEqualTo("closed_dialog");
        assertThat(((Map<?, ?>) actions.get("participants_remove")).get("enabled")).isEqualTo(true);
    }

    @Test
    void buildWorkflowSnapshotProjectsNoParticipantGuardAfterParticipantRemoval() {
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogTriagePreferenceService dialogTriagePreferenceService = mock(DialogTriagePreferenceService.class);
        DialogWorkspaceWorkflowSnapshotService service = new DialogWorkspaceWorkflowSnapshotService(
                dialogParticipantService,
                dialogResponsibilityService,
                dialogTriagePreferenceService
        );

        when(dialogParticipantService.loadParticipants("T-WF-4")).thenReturn(List.of());
        when(dialogParticipantService.findOperator("watcher_new")).thenReturn(Optional.of(
                new DialogOperatorOption("watcher_new", "Watcher New", "/avatars/new.png", "Ops", "Lead")
        ));
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("watcher_owner", "Watcher Owner", "/avatars/owner.png", "Ops", "Lead"),
                new DialogOperatorOption("watcher_peer", "Watcher Peer", "/avatars/peer.png", "Ops", "Support")
        ));
        when(dialogTriagePreferenceService.loadForOperator("watcher_new")).thenReturn(Map.of());

        Map<String, Object> snapshot = service.buildWorkflowSnapshot(
                "T-WF-4",
                "watcher_new",
                dialogWithResponsible("T-WF-4", "watcher_new", "Watcher New", "/avatars/new.png"),
                Map.of("can_assign", true, "can_close", true, "can_reply", true)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> actions = (Map<String, Object>) snapshot.get("actions");
        @SuppressWarnings("unchecked")
        Map<String, Object> collaboration = (Map<String, Object>) snapshot.get("collaboration");

        assertThat(((Map<?, ?>) actions.get("resolve")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("reopen")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) actions.get("reopen")).get("disabled_reason")).isEqualTo("not_closed");
        assertThat(((Map<?, ?>) actions.get("categories")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("spam")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("reassign")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("participants_add")).get("enabled")).isEqualTo(true);
        assertThat(((Map<?, ?>) actions.get("participants_remove")).get("enabled")).isEqualTo(false);
        assertThat(((Map<?, ?>) actions.get("participants_remove")).get("disabled_reason")).isEqualTo("no_participants");
        assertThat(collaboration).containsEntry("participant_count", 0);
        assertThat(collaboration).containsEntry("can_reassign", true);
        assertThat(collaboration).containsEntry("can_manage_participants", true);
    }

    private DialogListItem dialogWithResponsible(String ticketId,
                                                 String responsible,
                                                 String responsibleDisplayName,
                                                 String responsibleAvatarUrl) {
        return new DialogListItem(
                ticketId,
                5001L,
                9001L,
                "client_username",
                "Клиент",
                "retail",
                44L,
                "Telegram",
                "Москва",
                "Офис",
                "problem",
                "2026-05-26T09:00:00Z",
                "open",
                false,
                null,
                null,
                responsible,
                "26.05.2026",
                "09:00:00",
                "vip",
                "client",
                "2026-05-26T09:01:00Z",
                1,
                5,
                "billing",
                responsibleDisplayName,
                responsibleAvatarUrl
        );
    }

    private DialogListItem closedDialog(String ticketId, String responsible) {
        return new DialogListItem(
                ticketId,
                5002L,
                9002L,
                "client_username",
                "Клиент",
                "retail",
                44L,
                "Telegram",
                "Москва",
                "Офис",
                "problem",
                "2026-05-26T09:00:00Z",
                "resolved",
                false,
                "operator",
                "2026-05-26T10:00:00Z",
                responsible,
                "26.05.2026",
                "09:00:00",
                "vip",
                "operator",
                "2026-05-26T10:00:00Z",
                0,
                5,
                "billing",
                "Watcher Owner",
                "/avatars/owner.png"
        );
    }
}

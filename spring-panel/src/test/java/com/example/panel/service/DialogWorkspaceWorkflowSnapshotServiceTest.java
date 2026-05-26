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

        assertThat(responsible).containsEntry("username", "watcher_owner");
        assertThat(responsible).containsEntry("display_name", "Watcher Owner");
        assertThat(reassignCandidates).isEmpty();
        assertThat(participantCandidates).isEmpty();
        assertThat(collaboration).containsEntry("can_reassign", false);
        assertThat(collaboration).containsEntry("can_manage_participants", false);
        verify(dialogParticipantService, never()).loadAssignableOperators();
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
}

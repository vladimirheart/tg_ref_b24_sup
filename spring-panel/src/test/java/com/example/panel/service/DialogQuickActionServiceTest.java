package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogQuickActionServiceTest {

    @Test
    void sendReplyRegistersLearningAndParticipantNotificationOnSuccess() {
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogNotificationService dialogNotificationService = mock(DialogNotificationService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                dialogLookupReadService,
                dialogResponsibilityService,
                mock(DialogParticipantService.class),
                dialogReplyService,
                dialogNotificationService,
                dialogAiAssistantService,
                notificationService,
                attachmentService
        );

        when(dialogReplyService.sendReply("T-701", "Принято в работу", 77L, "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-05-21T12:00:00Z", 501L, "operator"));
        when(notificationService.buildDialogUrl("T-701")).thenReturn("/dialogs/T-701");

        DialogReplyService.DialogReplyResult result = service.sendReply("T-701", "Принято в работу", 77L, "operator");

        assertThat(result.success()).isTrue();
        verify(dialogAiAssistantService).clearProcessing("T-701", "operator_reply", null);
        verify(dialogAiAssistantService).registerOperatorReply("T-701", "Принято в работу", "operator");
        verify(notificationService).notifyDialogParticipants(
                "T-701",
                "Новое сообщение в обращении T-701",
                "/dialogs/T-701",
                "operator"
        );
    }

    @Test
    void sendReplyDoesNotNotifyParticipantsWhenReplyFails() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogReplyService.sendReply("T-702", "Не ушло", null, "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.error("transport_error"));

        DialogReplyService.DialogReplyResult result = service.sendReply("T-702", "Не ушло", null, "operator");

        assertThat(result.success()).isFalse();
        verify(dialogAiAssistantService).clearProcessing("T-702", "operator_reply", null);
        verify(dialogAiAssistantService, never()).registerOperatorReply("T-702", "Не ушло", "operator");
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolveTicketNotifiesResolvedLifecycleWhenUpdated() {
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        DialogNotificationService dialogNotificationService = mock(DialogNotificationService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                dialogNotificationService,
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogTicketLifecycleService.resolveTicket("T-703", "operator", List.of("billing")))
                .thenReturn(new DialogResolveResult(true, true, null));
        when(notificationService.buildDialogUrl("T-703")).thenReturn("/dialogs/T-703");

        DialogResolveResult result = service.resolveTicket("T-703", "operator", List.of("billing"));

        assertThat(result.updated()).isTrue();
        verify(dialogAiAssistantService).clearProcessing("T-703", "resolved", null);
        verify(dialogNotificationService).notifyResolved("T-703");
        verify(notificationService).notifyDialogParticipants(
                "T-703",
                "Обращение T-703 закрыто",
                "/dialogs/T-703",
                "operator"
        );
    }

    @Test
    void reopenTicketNotifiesReopenedLifecycleWhenUpdated() {
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        DialogNotificationService dialogNotificationService = mock(DialogNotificationService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                dialogNotificationService,
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogTicketLifecycleService.reopenTicket("T-704", "operator"))
                .thenReturn(new DialogResolveResult(true, true, null));
        when(notificationService.buildDialogUrl("T-704")).thenReturn("/dialogs/T-704");

        DialogResolveResult result = service.reopenTicket("T-704", "operator");

        assertThat(result.updated()).isTrue();
        verify(dialogAiAssistantService).clearProcessing("T-704", "reopened", null);
        verify(dialogNotificationService).notifyReopened("T-704");
        verify(notificationService).notifyDialogParticipants(
                "T-704",
                "Обращение T-704 снова открыто",
                "/dialogs/T-704",
                "operator"
        );
    }

    @Test
    void takeTicketAssignsResponsibleAndNotifiesParticipants() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                dialogResponsibilityService,
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-705", "operator"))
                .thenReturn(Optional.of(dialog("T-705", null)))
                .thenReturn(Optional.of(dialog("T-705", "lead_operator")));
        when(notificationService.buildDialogUrl("T-705")).thenReturn("/dialogs/T-705");

        Optional<String> responsible = service.takeTicket("T-705", "operator");

        assertThat(responsible).contains("lead_operator");
        verify(dialogResponsibilityService).assignResponsibleIfMissingOrRedirected("T-705", "operator", "operator");
        verify(dialogAiAssistantService).clearProcessing("T-705", "operator_take", null);
        verify(notificationService).notifyDialogParticipants(
                "T-705",
                "Обращение T-705 взято в работу оператором operator",
                "/dialogs/T-705",
                "operator"
        );
    }

    @Test
    void takeTicketReturnsEmptyWhenDialogMissing() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                dialogResponsibilityService,
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-706", "operator")).thenReturn(Optional.empty());

        Optional<String> responsible = service.takeTicket("T-706", "operator");

        assertThat(responsible).isEmpty();
        verify(dialogResponsibilityService, never()).assignResponsibleIfMissingOrRedirected("T-706", "operator", "operator");
        verify(dialogAiAssistantService, never()).clearProcessing("T-706", "operator_take", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendMediaReplyReturnsAttachmentPayloadAndNotifiesParticipantsOnSuccess() throws Exception {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                attachmentService
        );

        MockMultipartFile file = new MockMultipartFile("file", "screen.png", "image/png", "hello".getBytes());
        AttachmentService.AttachmentUploadMetadata metadata = new AttachmentService.AttachmentUploadMetadata(
                "screen.png",
                "stored-screen.png",
                "image/png",
                5L,
                java.time.OffsetDateTime.parse("2026-05-21T12:10:00Z")
        );

        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(Authentication.class), eq("T-707"), eq(file)))
                .thenReturn(metadata);
        when(dialogReplyService.sendMediaReply("T-707", file, "caption", "operator", "stored-screen.png", "screen.png"))
                .thenReturn(new DialogReplyService.DialogMediaReplyResult(
                        true,
                        null,
                        "2026-05-21T12:11:00Z",
                        9001L,
                        "stored-screen.png",
                        "image",
                        "caption",
                        "operator"
                ));
        when(notificationService.buildDialogUrl("T-707")).thenReturn("/dialogs/T-707");

        Map<String, Object> response = service.sendMediaReply(
                "T-707",
                file,
                "caption",
                "operator",
                mock(Authentication.class)
        );

        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("timestamp", "2026-05-21T12:11:00Z")
                .containsEntry("telegramMessageId", 9001L)
                .containsEntry("responsible", "operator")
                .containsEntry("attachment", "/api/attachments/tickets/T-707/stored-screen.png")
                .containsEntry("messageType", "image")
                .containsEntry("message", "caption");
        verify(dialogAiAssistantService).clearProcessing("T-707", "operator_reply_media", null);
        verify(dialogAiAssistantService).registerOperatorReply("T-707", "caption", "operator");
        verify(notificationService).notifyDialogParticipants(
                "T-707",
                "Новое медиа-сообщение в обращении T-707",
                "/dialogs/T-707",
                "operator"
        );
    }

    @Test
    void sendMediaReplyReturnsErrorWithoutNotificationWhenReplyFails() throws Exception {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);
        AttachmentService attachmentService = mock(AttachmentService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                attachmentService
        );

        MockMultipartFile file = new MockMultipartFile("file", "screen.png", "image/png", "hello".getBytes());
        AttachmentService.AttachmentUploadMetadata metadata = new AttachmentService.AttachmentUploadMetadata(
                "screen.png",
                "stored-screen.png",
                "image/png",
                5L,
                java.time.OffsetDateTime.parse("2026-05-21T12:10:00Z")
        );

        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(Authentication.class), eq("T-708"), eq(file)))
                .thenReturn(metadata);
        when(dialogReplyService.sendMediaReply("T-708", file, "caption", "operator", "stored-screen.png", "screen.png"))
                .thenReturn(new DialogReplyService.DialogMediaReplyResult(
                        false,
                        "transport_error",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        Map<String, Object> response = service.sendMediaReply(
                "T-708",
                file,
                "caption",
                "operator",
                mock(Authentication.class)
        );

        assertThat(response)
                .containsEntry("success", false)
                .containsEntry("error", "transport_error");
        verify(dialogAiAssistantService).clearProcessing("T-708", "operator_reply_media", null);
        verify(dialogAiAssistantService, never()).registerOperatorReply("T-708", "caption", "operator");
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCategoriesNotifiesParticipantsThroughDialogRoute() {
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(notificationService.buildDialogUrl("T-709")).thenReturn("/dialogs/T-709");

        service.updateCategories("T-709", "operator", List.of("billing", "vip"));

        verify(dialogTicketLifecycleService).setTicketCategories("T-709", List.of("billing", "vip"));
        verify(notificationService).notifyDialogParticipants(
                "T-709",
                "В обращении T-709 обновлены категории",
                "/dialogs/T-709",
                "operator"
        );
    }

    @Test
    void addParticipantAddsOperatorAndNotifiesParticipants() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                dialogResponsibilityService,
                dialogParticipantService,
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        DialogOperatorOption targetOperator = new DialogOperatorOption("alice", "Alice Doe", null, "support", "operator");
        List<DialogParticipantDto> participants = List.of(participant("alice", "Alice Doe"));

        when(dialogLookupReadService.findDialog("T-710", "operator")).thenReturn(Optional.of(dialog("T-710", "lead_operator")));
        when(dialogParticipantService.findOperator("alice")).thenReturn(Optional.of(targetOperator));
        when(dialogResponsibilityService.loadResponsible("T-710")).thenReturn("lead_operator");
        when(dialogParticipantService.addParticipant("T-710", "alice", "operator")).thenReturn(true);
        when(dialogParticipantService.loadParticipants("T-710")).thenReturn(participants);
        when(notificationService.buildDialogUrl("T-710")).thenReturn("/dialogs/T-710");

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.addParticipant("T-710", "alice", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(notificationService).notifyDialogParticipants(
                "T-710",
                "К обращению T-710 подключен оператор Alice Doe",
                "/dialogs/T-710",
                "operator"
        );
    }

    @Test
    void removeParticipantRemovesOperatorAndNotifiesParticipants() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                dialogParticipantService,
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        List<DialogParticipantDto> participants = List.of(participant("lead_operator", "Lead Operator"));

        when(dialogLookupReadService.findDialog("T-711", "operator")).thenReturn(Optional.of(dialog("T-711", "lead_operator")));
        when(dialogParticipantService.findOperator("alice")).thenReturn(Optional.of(new DialogOperatorOption("alice", "Alice Doe", null, "support", "operator")));
        when(dialogParticipantService.removeParticipant("T-711", "alice")).thenReturn(true);
        when(dialogParticipantService.loadParticipants("T-711")).thenReturn(participants);
        when(notificationService.buildDialogUrl("T-711")).thenReturn("/dialogs/T-711");

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.removeParticipant("T-711", "alice", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(notificationService).notifyDialogParticipants(
                "T-711",
                "Из обращения T-711 исключен оператор Alice Doe",
                "/dialogs/T-711",
                "operator"
        );
    }

    @Test
    void reassignTicketTransfersOwnershipAndNotifiesParticipants() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogResponsibilityService dialogResponsibilityService = mock(DialogResponsibilityService.class);
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                dialogResponsibilityService,
                dialogParticipantService,
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        DialogOperatorOption targetOperator = new DialogOperatorOption("alice", "Alice Doe", "/avatars/alice.png", "support", "operator");
        List<DialogParticipantDto> participants = List.of(participant("alice", "Alice Doe"));

        when(dialogLookupReadService.findDialog("T-712", "operator")).thenReturn(Optional.of(dialog("T-712", "lead_operator")));
        when(dialogParticipantService.findOperator("alice")).thenReturn(Optional.of(targetOperator));
        when(dialogResponsibilityService.loadResponsible("T-712")).thenReturn("lead_operator");
        when(dialogParticipantService.loadParticipants("T-712")).thenReturn(participants);
        when(notificationService.buildDialogUrl("T-712")).thenReturn("/dialogs/T-712");

        DialogQuickActionService.DialogReassignResult result =
                service.reassignTicket("T-712", "alice", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.responsible()).isEqualTo("alice");
        assertThat(result.responsibleDisplayName()).isEqualTo("Alice Doe");
        assertThat(result.responsibleAvatarUrl()).isEqualTo("/avatars/alice.png");
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(dialogResponsibilityService).assignResponsibleIfMissingOrRedirected("T-712", "alice", "operator");
        verify(dialogParticipantService).removeParticipant("T-712", "alice");
        verify(dialogAiAssistantService).clearProcessing("T-712", "operator_reassign", null);
        verify(notificationService).notifyDialogParticipants(
                "T-712",
                "Обращение T-712 передано оператору Alice Doe",
                "/dialogs/T-712",
                "operator"
        );
    }

    private DialogListItem dialog(String ticketId, String responsible) {
        return dialog(ticketId, responsible, "open");
    }

    private DialogListItem dialog(String ticketId, String responsible, String statusKey) {
        return new DialogListItem(
                ticketId,
                1L,
                10L,
                "client",
                "Client",
                "Support",
                7L,
                "Telegram",
                "Moscow",
                "HQ",
                "Need help",
                "2026-05-21T12:00:00Z",
                statusKey,
                false,
                null,
                null,
                responsible,
                null,
                null,
                null,
                "client",
                "2026-05-21T12:00:00Z",
                0,
                null,
                null,
                null,
                null
        );
    }

    private DialogParticipantDto participant(String username, String displayName) {
        return new DialogParticipantDto(
                username,
                displayName,
                null,
                "support",
                "operator",
                "2026-05-21T12:00:00Z",
                "operator"
        );
    }

}

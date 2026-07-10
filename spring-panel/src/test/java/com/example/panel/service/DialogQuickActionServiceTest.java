package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.example.panel.model.dialog.DialogParticipantDto;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void editReplyNotifiesParticipantsWhenUpdateSucceeds() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogReplyService.editOperatorMessage( "T-702A", 7001L, "Уточненный ответ", "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-05-21T12:05:00Z", 7001L, "operator"));
        when(notificationService.buildDialogUrl("T-702A")).thenReturn("/dialogs/T-702A");

        DialogReplyService.DialogReplyResult result =
                service.editReply("T-702A", 7001L, "Уточненный ответ", "operator");

        assertThat(result.success()).isTrue();
        verify(notificationService).notifyDialogParticipants(
                "T-702A",
                "Сообщение в обращении T-702A было отредактировано",
                "/dialogs/T-702A",
                "operator"
        );
    }

    @Test
    void editReplyDoesNotNotifyParticipantsWhenUpdateFails() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogReplyService.editOperatorMessage("T-702B", 7002L, "Не обновилось", "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.error("transport_error"));

        DialogReplyService.DialogReplyResult result =
                service.editReply("T-702B", 7002L, "Не обновилось", "operator");

        assertThat(result.success()).isFalse();
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteReplyNotifiesParticipantsWhenDeleteSucceeds() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogReplyService.deleteOperatorMessage("T-702C", 7003L, "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.success("2026-05-21T12:06:00Z", 7003L, "operator"));
        when(notificationService.buildDialogUrl("T-702C")).thenReturn("/dialogs/T-702C");

        DialogReplyService.DialogReplyResult result =
                service.deleteReply("T-702C", 7003L, "operator");

        assertThat(result.success()).isTrue();
        verify(notificationService).notifyDialogParticipants(
                "T-702C",
                "Сообщение в обращении T-702C было удалено",
                "/dialogs/T-702C",
                "operator"
        );
    }

    @Test
    void deleteReplyDoesNotNotifyParticipantsWhenDeleteFails() {
        DialogReplyService dialogReplyService = mock(DialogReplyService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class),
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                dialogReplyService,
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogReplyService.deleteOperatorMessage("T-702D", 7004L, "operator"))
                .thenReturn(DialogReplyService.DialogReplyResult.error("transport_error"));

        DialogReplyService.DialogReplyResult result =
                service.deleteReply("T-702D", 7004L, "operator");

        assertThat(result.success()).isFalse();
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

        DialogQuickActionService.DialogTakeResult result = service.takeTicket("T-705", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(result.responsible()).isEqualTo("lead_operator");
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
    void takeTicketReturnsUnchangedWhenOperatorAlreadyResponsible() {
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

        when(dialogLookupReadService.findDialog("T-705OWN", "operator"))
                .thenReturn(Optional.of(dialog("T-705OWN", "Operator Display")));
        when(dialogResponsibilityService.loadResponsible("T-705OWN")).thenReturn("operator");

        DialogQuickActionService.DialogTakeResult result = service.takeTicket("T-705OWN", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.responsible()).isEqualTo("Operator Display");
        assertThat(result.error()).isNull();
        verify(dialogResponsibilityService, never()).assignResponsibleIfMissingOrRedirected("T-705OWN", "operator", "operator");
        verify(dialogAiAssistantService, never()).clearProcessing("T-705OWN", "operator_take", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void takeTicketReturnsErrorWhenDialogClosed() {
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

        when(dialogLookupReadService.findDialog("T-705C", "operator"))
                .thenReturn(Optional.of(dialog("T-705C", "Closed Owner", "closed")));

        DialogQuickActionService.DialogTakeResult result = service.takeTicket("T-705C", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.responsible()).isEqualTo("Closed Owner");
        assertThat(result.error()).isEqualTo("Взять в работу можно только открытый диалог");
        verify(dialogResponsibilityService, never()).assignResponsibleIfMissingOrRedirected("T-705C", "operator", "operator");
        verify(dialogAiAssistantService, never()).clearProcessing("T-705C", "operator_take", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void takeTicketReturnsNotFoundWhenDialogMissing() {
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

        DialogQuickActionService.DialogTakeResult result = service.takeTicket("T-706", "operator");

        assertThat(result.exists()).isFalse();
        assertThat(result.changed()).isFalse();
        assertThat(result.responsible()).isNull();
        assertThat(result.error()).isNull();
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

        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(Authentication.class), eq("T-707"), any(MultipartFile.class)))
                .thenReturn(metadata);
        when(dialogReplyService.sendMediaReply(eq("T-707"), any(MultipartFile.class), eq("caption"), eq(null), eq("operator"), eq("stored-screen.png"), eq("screen.png")))
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
                null,
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

        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(Authentication.class), eq("T-708"), any(MultipartFile.class)))
                .thenReturn(metadata);
        when(dialogReplyService.sendMediaReply(eq("T-708"), any(MultipartFile.class), eq("caption"), eq(null), eq("operator"), eq("stored-screen.png"), eq("screen.png")))
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
                null,
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
    void sendMediaReplyCachesMultipartFileBeforeStorageAndTransport() throws Exception {
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

        MultipartFile file = new OneShotMultipartFile("file", "proof.png", "image/png", "hello".getBytes());
        AttachmentService.AttachmentUploadMetadata metadata = new AttachmentService.AttachmentUploadMetadata(
                "proof.png",
                "stored-proof.png",
                "image/png",
                5L,
                java.time.OffsetDateTime.parse("2026-05-21T12:10:00Z")
        );

        when(attachmentService.storeTicketAttachment(org.mockito.ArgumentMatchers.any(Authentication.class), eq("T-709"), any(MultipartFile.class)))
                .thenReturn(metadata);
        when(dialogReplyService.sendMediaReply(eq("T-709"), any(MultipartFile.class), eq("caption"), eq(null), eq("operator"), eq("stored-proof.png"), eq("proof.png")))
                .thenReturn(new DialogReplyService.DialogMediaReplyResult(
                        true,
                        null,
                        "2026-05-21T12:11:00Z",
                        9002L,
                        "stored-proof.png",
                        "image",
                        "caption",
                        "operator"
                ));
        when(notificationService.buildDialogUrl("T-709")).thenReturn("/dialogs/T-709");

        Map<String, Object> response = service.sendMediaReply(
                "T-709",
                file,
                "caption",
                null,
                "operator",
                mock(Authentication.class)
        );

        assertThat(response)
                .containsEntry("success", true)
                .containsEntry("attachment", "/api/attachments/tickets/T-709/stored-proof.png");
    }

    private static final class OneShotMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;
        private boolean streamOpened;

        private OneShotMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            if (streamOpened) {
                throw new IOException("stream already consumed");
            }
            streamOpened = true;
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }

    @Test
    void updateCategoriesNotifiesParticipantsThroughDialogRoute() {
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(notificationService.buildDialogUrl("T-709")).thenReturn("/dialogs/T-709");
        when(dialogLookupReadService.findDialog("T-709", "operator")).thenReturn(Optional.of(dialog("T-709", "operator")));

        DialogQuickActionService.DialogCategoryUpdateResult result =
                service.updateCategories("T-709", "operator", List.of("billing", "vip", "billing", "  vip  "));

        assertThat(result.exists()).isTrue();
        assertThat(result.categories()).containsExactly("billing", "vip");
        verify(dialogTicketLifecycleService).setTicketCategories("T-709", List.of("billing", "vip"));
        verify(notificationService).notifyDialogParticipants(
                "T-709",
                "В обращении T-709 обновлены категории",
                "/dialogs/T-709",
                "operator"
        );
    }

    @Test
    void updateCategoriesReturnsNotFoundWhenDialogMissing() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-709X", "operator")).thenReturn(Optional.empty());

        DialogQuickActionService.DialogCategoryUpdateResult result =
                service.updateCategories("T-709X", "operator", List.of("billing"));

        assertThat(result.exists()).isFalse();
        assertThat(result.categories()).isEmpty();
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markClientAsSpamBlocksClientAndAddsSpamCategory() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ClientBlacklistService clientBlacklistService = mock(ClientBlacklistService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class),
                clientBlacklistService
        );

        when(dialogLookupReadService.findDialog("T-709S", "operator"))
                .thenReturn(Optional.of(new DialogListItem(
                        "T-709S",
                        1L,
                        77L,
                        "client",
                        "Client",
                        "Support",
                        7L,
                        "Telegram",
                        "Moscow",
                        "HQ",
                        "Spam",
                        "2026-05-21T12:00:00Z",
                        "pending",
                        false,
                        null,
                        null,
                        "operator",
                        null,
                        null,
                        null,
                        "client",
                        "2026-05-21T12:00:00Z",
                        0,
                        null,
                        "billing, vip",
                        null,
                        null
                )));
        when(clientBlacklistService.blockClient("77", "Спам", "operator", false))
                .thenReturn(new ClientBlacklistService.BlacklistMutationResult(true, "ok", null));
        when(notificationService.buildDialogUrl("T-709S")).thenReturn("/dialogs/T-709S");

        DialogQuickActionService.DialogSpamResult result = service.markClientAsSpam("T-709S", "operator", "Спам");

        assertThat(result.exists()).isTrue();
        assertThat(result.updated()).isTrue();
        assertThat(result.error()).isNull();
        assertThat(result.userId()).isEqualTo("77");
        assertThat(result.categories()).containsExactly("billing", "vip", "Спам");
        verify(dialogTicketLifecycleService).setTicketCategories("T-709S", List.of("billing", "vip", "Спам"));
        verify(clientBlacklistService).blockClient("77", "Спам", "operator", false);
        verify(notificationService).notifyDialogParticipants(
                "T-709S",
                "Обращение T-709S помечено как спам",
                "/dialogs/T-709S",
                "operator"
        );
    }

    @Test
    void markClientAsSpamDoesNotNotifyParticipantsWhenBlacklistFails() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogTicketLifecycleService dialogTicketLifecycleService = mock(DialogTicketLifecycleService.class);
        NotificationService notificationService = mock(NotificationService.class);
        ClientBlacklistService clientBlacklistService = mock(ClientBlacklistService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                dialogTicketLifecycleService,
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                notificationService,
                mock(AttachmentService.class),
                clientBlacklistService
        );

        when(dialogLookupReadService.findDialog("T-709SF", "operator"))
                .thenReturn(Optional.of(new DialogListItem(
                        "T-709SF",
                        1L,
                        77L,
                        "client",
                        "Client",
                        "Support",
                        7L,
                        "Telegram",
                        "Moscow",
                        "HQ",
                        "Spam",
                        "2026-05-21T12:00:00Z",
                        "pending",
                        false,
                        null,
                        null,
                        "operator",
                        null,
                        null,
                        null,
                        "client",
                        "2026-05-21T12:00:00Z",
                        0,
                        null,
                        "billing, vip",
                        null,
                        null
                )));
        when(clientBlacklistService.blockClient("77", "Спам", "operator", false))
                .thenReturn(new ClientBlacklistService.BlacklistMutationResult(false, null, "blacklist_failed"));

        DialogQuickActionService.DialogSpamResult result = service.markClientAsSpam("T-709SF", "operator", "Спам");

        assertThat(result.exists()).isTrue();
        assertThat(result.updated()).isFalse();
        assertThat(result.error()).isEqualTo("blacklist_failed");
        verify(dialogTicketLifecycleService, never()).setTicketCategories("T-709SF", List.of("billing", "vip", "Спам"));
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void snoozeTicketReturnsExistsWhenDialogPresent() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                mock(NotificationService.class),
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-709SN", "operator")).thenReturn(Optional.of(dialog("T-709SN", "operator")));

        DialogQuickActionService.DialogSnoozeResult result = service.snoozeTicket("T-709SN", "operator", 15);

        assertThat(result.exists()).isTrue();
        assertThat(result.minutes()).isEqualTo(15);
        assertThat(result.error()).isNull();
    }

    @Test
    void snoozeTicketReturnsErrorWhenDialogClosed() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                mock(NotificationService.class),
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-709SNC", "operator"))
                .thenReturn(Optional.of(dialog("T-709SNC", "operator", "closed")));

        DialogQuickActionService.DialogSnoozeResult result = service.snoozeTicket("T-709SNC", "operator", 15);

        assertThat(result.exists()).isTrue();
        assertThat(result.minutes()).isEqualTo(15);
        assertThat(result.error()).isEqualTo("Отложить можно только открытый диалог");
    }

    @Test
    void snoozeTicketReturnsNotFoundWhenDialogMissing() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                mock(DialogParticipantService.class),
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                mock(DialogAiAssistantService.class),
                mock(NotificationService.class),
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-709SNF", "operator")).thenReturn(Optional.empty());

        DialogQuickActionService.DialogSnoozeResult result = service.snoozeTicket("T-709SNF", "operator", 15);

        assertThat(result.exists()).isFalse();
        assertThat(result.minutes()).isEqualTo(15);
        assertThat(result.error()).isNull();
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
    void addParticipantReturnsErrorWhenTargetAlreadyResponsible() {
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

        when(dialogLookupReadService.findDialog("T-710R", "operator")).thenReturn(Optional.of(dialog("T-710R", "lead_operator")));
        when(dialogParticipantService.findOperator("lead_operator")).thenReturn(Optional.of(new DialogOperatorOption("lead_operator", "Lead Operator", null, "support", "operator")));
        when(dialogResponsibilityService.loadResponsible("T-710R")).thenReturn("lead_operator");
        when(dialogParticipantService.loadParticipants("T-710R")).thenReturn(List.of());

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.addParticipant("T-710R", "lead_operator", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.error()).isEqualTo("Этот пользователь уже назначен ответственным за диалог");
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addParticipantReturnsErrorWhenDialogIsClosed() {
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

        when(dialogLookupReadService.findDialog("T-710C", "operator")).thenReturn(Optional.of(dialog("T-710C", "lead_operator", "closed")));
        when(dialogParticipantService.loadParticipants("T-710C")).thenReturn(List.of(participant("lead_operator", "Lead Operator")));

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.addParticipant("T-710C", "alice", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.error()).isEqualTo("К закрытому диалогу нельзя добавлять новых участников");
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void addParticipantReturnsErrorWhenTargetOperatorMissing() {
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

        when(dialogLookupReadService.findDialog("T-710U", "operator")).thenReturn(Optional.of(dialog("T-710U", "lead_operator")));
        when(dialogParticipantService.findOperator("ghost_operator")).thenReturn(Optional.empty());
        when(dialogParticipantService.loadParticipants("T-710U")).thenReturn(participants);

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.addParticipant("T-710U", "ghost_operator", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.error()).isEqualTo("Пользователь панели не найден");
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(dialogParticipantService, never()).addParticipant("T-710U", "ghost_operator", "operator");
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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
    void removeParticipantReturnsUnchangedWhenParticipantMissing() {
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

        when(dialogLookupReadService.findDialog("T-711M", "operator")).thenReturn(Optional.of(dialog("T-711M", "lead_operator")));
        when(dialogParticipantService.findOperator("ghost")).thenReturn(Optional.empty());
        when(dialogParticipantService.removeParticipant("T-711M", "ghost")).thenReturn(false);
        when(dialogParticipantService.loadParticipants("T-711M")).thenReturn(participants);

        DialogQuickActionService.DialogParticipantMutationResult result =
                service.removeParticipant("T-711M", "ghost", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.error()).isNull();
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

    @Test
    void reassignTicketReturnsErrorWhenTargetAlreadyResponsible() {
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

        List<DialogParticipantDto> participants = List.of(participant("lead_operator", "Lead Operator"));

        when(dialogLookupReadService.findDialog("T-712S", "operator")).thenReturn(Optional.of(dialog("T-712S", "lead_operator")));
        when(dialogParticipantService.findOperator("lead_operator")).thenReturn(Optional.of(new DialogOperatorOption("lead_operator", "Lead Operator", "/avatars/lead.png", "support", "operator")));
        when(dialogResponsibilityService.loadResponsible("T-712S")).thenReturn("lead_operator");
        when(dialogParticipantService.loadParticipants("T-712S")).thenReturn(participants);

        DialogQuickActionService.DialogReassignResult result =
                service.reassignTicket("T-712S", "lead_operator", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isEqualTo("Диалог уже назначен на этого пользователя");
        assertThat(result.responsible()).isEqualTo("lead_operator");
        verify(dialogAiAssistantService, never()).clearProcessing("T-712S", "operator_reassign", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reassignTicketReturnsErrorWhenDialogIsClosed() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                dialogParticipantService,
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        when(dialogLookupReadService.findDialog("T-712C", "operator")).thenReturn(Optional.of(dialog("T-712C", "lead_operator", "closed")));
        when(dialogParticipantService.loadParticipants("T-712C")).thenReturn(List.of(participant("lead_operator", "Lead Operator")));

        DialogQuickActionService.DialogReassignResult result =
                service.reassignTicket("T-712C", "alice", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isEqualTo("Переадресовать можно только открытый диалог");
        verify(dialogAiAssistantService, never()).clearProcessing("T-712C", "operator_reassign", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reassignTicketReturnsErrorWhenTargetOperatorMissing() {
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        DialogParticipantService dialogParticipantService = mock(DialogParticipantService.class);
        DialogAiAssistantService dialogAiAssistantService = mock(DialogAiAssistantService.class);
        NotificationService notificationService = mock(NotificationService.class);

        DialogQuickActionService service = new DialogQuickActionService(
                mock(DialogTicketLifecycleService.class),
                dialogLookupReadService,
                mock(DialogResponsibilityService.class),
                dialogParticipantService,
                mock(DialogReplyService.class),
                mock(DialogNotificationService.class),
                dialogAiAssistantService,
                notificationService,
                mock(AttachmentService.class)
        );

        List<DialogParticipantDto> participants = List.of(participant("lead_operator", "Lead Operator"));

        when(dialogLookupReadService.findDialog("T-712U", "operator")).thenReturn(Optional.of(dialog("T-712U", "lead_operator")));
        when(dialogParticipantService.findOperator("ghost_operator")).thenReturn(Optional.empty());
        when(dialogParticipantService.loadParticipants("T-712U")).thenReturn(participants);

        DialogQuickActionService.DialogReassignResult result =
                service.reassignTicket("T-712U", "ghost_operator", "operator");

        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isEqualTo("Пользователь панели не найден");
        assertThat(result.participants()).containsExactlyElementsOf(participants);
        verify(dialogAiAssistantService, never()).clearProcessing("T-712U", "operator_reassign", null);
        verify(notificationService, never()).notifyDialogParticipants(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
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

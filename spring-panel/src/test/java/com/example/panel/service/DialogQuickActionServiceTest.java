package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.storage.AttachmentService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    private DialogListItem dialog(String ticketId, String responsible) {
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
                "open",
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

}

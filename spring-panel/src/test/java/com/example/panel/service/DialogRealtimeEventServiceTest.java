package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DialogRealtimeEventServiceTest {

    @Test
    void initialClientMessageSkipsSecondBellButKeepsAiProcessing() {
        NotificationService notificationService = mock(NotificationService.class);
        DialogAiAssistantService aiService = mock(DialogAiAssistantService.class);
        AlertQueueService alertQueueService = mock(AlertQueueService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        Channel channel = new Channel();
        channel.setId(7L);
        when(channelRepository.findById(7L)).thenReturn(java.util.Optional.of(channel));

        DialogRealtimeEventService service = service(
                notificationService,
                aiService,
                alertQueueService,
                channelRepository,
                mock(DialogNotificationService.class)
        );

        service.handleIncomingClientMessage("T-100", 7L, "Первое сообщение", "text", null, false);

        verify(alertQueueService, never()).notifyIncomingClientMessage(channel, "T-100", "Первое сообщение");
        verify(notificationService, never()).notifyDialogUsers(anyString(), org.mockito.ArgumentMatchers.anySet(), anyString(), anyString());
        verify(aiService).processIncomingClientMessage("T-100", "Первое сообщение", "text", null);
    }

    @Test
    void autoCloseUsesOneCanonicalNumberedMessageForBellAndSupportChat() {
        NotificationService notificationService = mock(NotificationService.class);
        DialogAiAssistantService aiService = mock(DialogAiAssistantService.class);
        AlertQueueService alertQueueService = mock(AlertQueueService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        DialogNotificationService dialogNotificationService = mock(DialogNotificationService.class);
        Channel channel = new Channel();
        channel.setId(8L);
        when(channelRepository.findById(8L)).thenReturn(java.util.Optional.of(channel));
        when(notificationService.formatAutoCloseText("T-200"))
                .thenReturn("Обращение №20260928-8 автоматически закрыто из-за отсутствия активности.");
        when(notificationService.findDialogRecipients("T-200")).thenReturn(Set.of("owner"));
        when(notificationService.buildDialogUrl("T-200")).thenReturn("/dialogs/T-200");

        DialogRealtimeEventService service = service(
                notificationService,
                aiService,
                alertQueueService,
                channelRepository,
                dialogNotificationService
        );

        service.handleTicketAutoClosed("T-200", 8L, "legacy text with uuid T-200");

        verify(notificationService).notifyDialogUsers(
                "T-200",
                Set.of("owner"),
                "Обращение №20260928-8 автоматически закрыто из-за отсутствия активности.",
                "/dialogs/T-200"
        );
        verify(dialogNotificationService).notifySupportChat(
                channel,
                "Обращение №20260928-8 автоматически закрыто из-за отсутствия активности."
        );
    }

    @Test
    void incomingMessageSkipsAmbiguousLegacyTicketIdBeforeNotificationsAndAi() {
        NotificationService notificationService = mock(NotificationService.class);
        DialogAiAssistantService aiService = mock(DialogAiAssistantService.class);
        AlertQueueService alertQueueService = mock(AlertQueueService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        UiEventStreamService uiEventStreamService = mock(UiEventStreamService.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        DialogRealtimeEventService service = new DialogRealtimeEventService(
                notificationService,
                aiService,
                alertQueueService,
                mock(ChannelAssignmentRoutingService.class),
                channelRepository,
                mock(DialogResponsibilityService.class),
                mock(DialogNotificationService.class),
                uiEventStreamService,
                legacyTicketIdJdbcGuard
        );

        service.handleIncomingClientMessage("T-DUPLICATE", 7L, "message", "text", null);

        verify(legacyTicketIdJdbcGuard).hasUniqueNonGeneralTicket("T-DUPLICATE");
        verifyNoInteractions(notificationService, aiService, alertQueueService, channelRepository, uiEventStreamService);
    }

    @Test
    void clientEditEventPreservesExactIdAtGuardAndDispatchBoundaries() {
        UiEventStreamService stream = mock(UiEventStreamService.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueNonGeneralTicket(" T-EXACT ")).thenReturn(true);
        DialogRealtimeEventService service = new DialogRealtimeEventService(
            mock(NotificationService.class), mock(DialogAiAssistantService.class), mock(AlertQueueService.class),
            mock(ChannelAssignmentRoutingService.class), mock(ChannelRepository.class),
            mock(DialogResponsibilityService.class), mock(DialogNotificationService.class), stream, guard);

        service.handleClientMessageEdited(" T-EXACT ", 9L);

        verify(guard).hasUniqueNonGeneralTicket(" T-EXACT ");
        verify(stream).publishDialogsChanged("client_message_edited", " T-EXACT ");
        verify(stream).publishDialogHistoryChanged(" T-EXACT ", 9L, "client_message_edited");
    }

    @Test
    void unresolvedPaddedIdCannotDispatchEventForPlainNeighbour() {
        UiEventStreamService stream = mock(UiEventStreamService.class);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueNonGeneralTicket("T-PLAIN")).thenReturn(true);
        DialogRealtimeEventService service = new DialogRealtimeEventService(
            mock(NotificationService.class), mock(DialogAiAssistantService.class), mock(AlertQueueService.class),
            mock(ChannelAssignmentRoutingService.class), mock(ChannelRepository.class),
            mock(DialogResponsibilityService.class), mock(DialogNotificationService.class), stream, guard);

        service.handleClientMessageEdited(" T-PLAIN ", 9L);

        verify(guard).hasUniqueNonGeneralTicket(" T-PLAIN ");
        verifyNoInteractions(stream);
    }

    private DialogRealtimeEventService service(NotificationService notificationService,
                                               DialogAiAssistantService aiService,
                                               AlertQueueService alertQueueService,
                                               ChannelRepository channelRepository,
                                               DialogNotificationService dialogNotificationService) {
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(anyString())).thenReturn(true);
        return new DialogRealtimeEventService(
                notificationService,
                aiService,
                alertQueueService,
                mock(ChannelAssignmentRoutingService.class),
                channelRepository,
                mock(DialogResponsibilityService.class),
                dialogNotificationService,
                mock(UiEventStreamService.class),
                legacyTicketIdJdbcGuard
        );
    }
}

package com.example.panel.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        verify(notificationService, never()).notifyAllOperators(anyString(), anyString(), isNull());
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

        verify(notificationService).notifyUsers(
                Set.of("owner"),
                "Обращение №20260928-8 автоматически закрыто из-за отсутствия активности.",
                "/dialogs/T-200"
        );
        verify(dialogNotificationService).notifySupportChat(
                channel,
                "Обращение №20260928-8 автоматически закрыто из-за отсутствия активности."
        );
    }

    private DialogRealtimeEventService service(NotificationService notificationService,
                                               DialogAiAssistantService aiService,
                                               AlertQueueService alertQueueService,
                                               ChannelRepository channelRepository,
                                               DialogNotificationService dialogNotificationService) {
        return new DialogRealtimeEventService(
                notificationService,
                aiService,
                alertQueueService,
                mock(ChannelAssignmentRoutingService.class),
                channelRepository,
                mock(DialogResponsibilityService.class),
                dialogNotificationService,
                mock(UiEventStreamService.class)
        );
    }
}

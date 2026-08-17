package com.example.supportbot.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.repository.ChannelRepository;
import com.example.supportbot.repository.NotificationRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import com.example.supportbot.settings.BotSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EngagementTasksTest {

    @Test
    void dispatchPendingFeedbackRequestsSkipsBotSideExecutionInRabbitMode() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        EngagementTasks tasks = new EngagementTasks(
            pendingRepository,
            mock(NotificationRepository.class),
            mock(ChannelRepository.class),
            mock(BotSettingsService.class),
            mock(MessagingService.class),
            mock(TicketService.class),
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq"))
        );

        tasks.dispatchPendingFeedbackRequests();

        verify(pendingRepository, never()).findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any());
    }
}

package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Feedback;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.repository.FeedbackRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

class FeedbackServiceTest {

    @Test
    void storeFeedbackShouldPersistRatingEvenWhenPendingRequestExpirationFails() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        UiEventOutboxService outboxService = mock(UiEventOutboxService.class);
        FeedbackService service = new FeedbackService(
            pendingRepository,
            feedbackRepository,
            outboxService,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")),
            mock(PanelTicketReadClient.class),
            mock(PanelTicketWriteClient.class)
        );

        Channel channel = new Channel();
        channel.setId(3L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(77L);
        request.setUserId(1001L);
        request.setTicketId("ticket-77");
        request.setChannel(channel);

        when(feedbackRepository.findFirstByTicketIdOrderByTimestampDesc("ticket-77")).thenReturn(Optional.empty());
        doThrow(new RuntimeException("database is locked"))
                .when(pendingRepository)
                .save(eq(request));

        service.storeFeedback(request, 5);

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        Feedback saved = captor.getValue();
        assertThat(saved.getTicketId()).isEqualTo("ticket-77");
        assertThat(saved.getUserId()).isEqualTo(1001L);
        assertThat(saved.getChannel()).isSameAs(channel);
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getTimestamp()).isNotNull();
        verify(outboxService).publishFeedbackCreated("ticket-77", channel, 5);
    }

    @Test
    void findActiveRequestShouldIgnorePendingRequestWhenFeedbackAlreadyStored() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        UiEventOutboxService outboxService = mock(UiEventOutboxService.class);
        FeedbackService service = new FeedbackService(
            pendingRepository,
            feedbackRepository,
            outboxService,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")),
            mock(PanelTicketReadClient.class),
            mock(PanelTicketWriteClient.class)
        );

        Channel channel = new Channel();
        channel.setId(12L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setUserId(42L);
        request.setTicketId("ticket-42");
        request.setChannel(channel);

        when(pendingRepository.findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), eq(12L), any()))
                .thenReturn(Optional.of(request));
        when(feedbackRepository.existsByTicketId("ticket-42")).thenReturn(true);
        when(pendingRepository.findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), any()))
                .thenReturn(Optional.of(request));

        Optional<PendingFeedbackRequest> result = service.findActiveRequest(42L, channel);

        assertThat(result).isEmpty();
        verify(pendingRepository).findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), eq(12L), any());
        verify(pendingRepository).findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), any());
        verify(outboxService, never()).publishFeedbackCreated(any(), any(), any());
    }

    @Test
    void findActiveRequestUsesPanelReadClientInRabbitMode() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        UiEventOutboxService outboxService = mock(UiEventOutboxService.class);
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketReadClient.isEnabled()).thenReturn(true);

        Channel channel = new Channel();
        channel.setId(99L);
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(100L);
        when(panelTicketReadClient.findActiveFeedbackRequest(55L, 99L)).thenReturn(Optional.of(request));

        FeedbackService service = new FeedbackService(
            pendingRepository,
            feedbackRepository,
            outboxService,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelTicketReadClient,
            panelTicketWriteClient
        );

        Optional<PendingFeedbackRequest> result = service.findActiveRequest(55L, channel);

        assertThat(result).contains(request);
        verify(panelTicketReadClient).findActiveFeedbackRequest(55L, 99L);
        verify(pendingRepository, never()).findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void storeFeedbackUsesPanelWriteClientInRabbitMode() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        UiEventOutboxService outboxService = mock(UiEventOutboxService.class);
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketWriteClient.isEnabled()).thenReturn(true);
        when(panelTicketWriteClient.storeFeedback(101L, 4)).thenReturn(true);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(101L);

        FeedbackService service = new FeedbackService(
            pendingRepository,
            feedbackRepository,
            outboxService,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelTicketReadClient,
            panelTicketWriteClient
        );

        service.storeFeedback(request, 4);

        verify(panelTicketWriteClient).storeFeedback(101L, 4);
        verify(feedbackRepository, never()).save(any());
        verify(outboxService, never()).publishFeedbackCreated(any(), any(), any());
    }
}

package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.service.integration.OutboundFeedbackPromptPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FeedbackPromptDispatchSchedulerServiceTest {

    @Test
    void dispatchPendingFeedbackRequestsPublishesEventAndMarksRequestSent() {
        PendingFeedbackRequestRepository repository = mock(PendingFeedbackRequestRepository.class);
        BotRuntimeTicketReadService ticketReadService = mock(BotRuntimeTicketReadService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        OutboundFeedbackPromptPublisher publisher = mock(OutboundFeedbackPromptPublisher.class);

        Channel channel = new Channel();
        channel.setId(12L);
        channel.setPlatform("telegram");
        channel.setRatingTemplateId("custom");

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(901L);
        request.setUserId(77L);
        request.setTicketId("T-901");
        request.setChannel(channel);
        request.setCreatedAt(OffsetDateTime.now().minusMinutes(5));
        request.setExpiresAt(OffsetDateTime.now().plusHours(2));

        when(repository.findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of(request));
        when(ticketReadService.resolveRequestNumber("T-901")).thenReturn(Optional.of(
            new BotRuntimeTicketReadService.RequestNumberLookup("T-901", "20260817-007")
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "bot_settings", Map.of(
                "rating_templates", List.of(Map.of(
                    "id", "custom",
                    "scale_size", 4,
                    "prompt_text", "Оцените заявку {ticket_id} по шкале 1-{scale}"
                )),
                "active_rating_template_id", "custom"
            )
        ));

        FeedbackPromptDispatchSchedulerService service = new FeedbackPromptDispatchSchedulerService(
            repository,
            ticketReadService,
            new PanelBotSettingsService(sharedConfigService, new BotSettingsPayloadNormalizer()),
            publisher,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq"))
        );

        service.dispatchPendingFeedbackRequests();

        verify(publisher).publish(901L, channel, 77L, "T-901", "Оцените заявку 20260817-007 по шкале 1-4");
        verify(repository).save(request);
        assertThat(request.getSentAt()).isNotNull();
    }

    @Test
    void dispatchPendingFeedbackRequestsSkipsInJdbcMode() {
        PendingFeedbackRequestRepository repository = mock(PendingFeedbackRequestRepository.class);
        FeedbackPromptDispatchSchedulerService service = new FeedbackPromptDispatchSchedulerService(
            repository,
            mock(BotRuntimeTicketReadService.class),
            new PanelBotSettingsService(mock(SharedConfigService.class), new BotSettingsPayloadNormalizer()),
            mock(OutboundFeedbackPromptPublisher.class),
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc"))
        );

        service.dispatchPendingFeedbackRequests();

        verify(repository, never()).findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any());
    }
}

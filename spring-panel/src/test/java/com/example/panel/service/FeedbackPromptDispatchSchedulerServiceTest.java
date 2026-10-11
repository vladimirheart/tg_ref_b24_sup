package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.service.integration.OutboundFeedbackPromptPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class FeedbackPromptDispatchSchedulerServiceTest {

    @Test
    void dispatchPendingFeedbackRequestsPublishesEventAndMarksRequestSent() {
        PendingFeedbackRequestRepository repository = mock(PendingFeedbackRequestRepository.class);
        BotRuntimeTicketReadService ticketReadService = mock(BotRuntimeTicketReadService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        OutboundFeedbackPromptPublisher publisher = mock(OutboundFeedbackPromptPublisher.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);

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
        when(ticketRepository.findUniqueByLegacyTicketId("T-901")).thenReturn(Optional.of(ticket(77L, "T-901", channel)));

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
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            passthroughCoordinationService(),
            ticketRepository
        );

        service.dispatchPendingFeedbackRequestsInternal();

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
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")),
            passthroughCoordinationService(),
            mock(TicketRepository.class)
        );

        service.dispatchPendingFeedbackRequestsInternal();

        verify(repository, never()).findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-user", "wrong-channel"})
    void skipsUnresolvedOrMismatchedTicketWithoutPromptOrSentMarker(String scenario) {
        PendingFeedbackRequestRepository repository = mock(PendingFeedbackRequestRepository.class);
        BotRuntimeTicketReadService ticketReadService = mock(BotRuntimeTicketReadService.class);
        PanelBotSettingsService settingsService = mock(PanelBotSettingsService.class);
        OutboundFeedbackPromptPublisher publisher = mock(OutboundFeedbackPromptPublisher.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Channel channel = new Channel();
        channel.setId(12L);
        Channel otherChannel = new Channel();
        otherChannel.setId(13L);
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(901L);
        request.setUserId(77L);
        request.setTicketId("T-901");
        request.setChannel(channel);
        when(repository.findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of(request));
        Optional<Ticket> resolvedTicket = switch (scenario) {
            case "missing", "duplicate" -> Optional.empty();
            case "wrong-user" -> Optional.of(ticket(78L, "T-901", channel));
            case "wrong-channel" -> Optional.of(ticket(77L, "T-901", otherChannel));
            default -> throw new IllegalArgumentException(scenario);
        };
        when(ticketRepository.findUniqueByLegacyTicketId("T-901")).thenReturn(resolvedTicket);
        FeedbackPromptDispatchSchedulerService service = new FeedbackPromptDispatchSchedulerService(
            repository, ticketReadService, settingsService, publisher,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            passthroughCoordinationService(), ticketRepository
        );

        service.dispatchPendingFeedbackRequestsInternal();

        assertThat(request.getSentAt()).isNull();
        verify(repository, never()).save(any());
        verifyNoInteractions(ticketReadService, settingsService, publisher);
    }

    @Test
    void preservesExactLegacyTicketIdForCanonicalLookup() {
        PendingFeedbackRequestRepository repository = mock(PendingFeedbackRequestRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Channel channel = new Channel();
        channel.setId(12L);
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setUserId(77L);
        request.setTicketId(" T-901 ");
        request.setChannel(channel);
        when(repository.findTop50BySentAtIsNullAndExpiresAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of(request));
        FeedbackPromptDispatchSchedulerService service = new FeedbackPromptDispatchSchedulerService(
            repository, mock(BotRuntimeTicketReadService.class), mock(PanelBotSettingsService.class),
            mock(OutboundFeedbackPromptPublisher.class),
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            passthroughCoordinationService(), ticketRepository
        );

        service.dispatchPendingFeedbackRequestsInternal();

        verify(ticketRepository).findUniqueByLegacyTicketId(" T-901 ");
        verify(ticketRepository, never()).findUniqueByLegacyTicketId("T-901");
        assertThat(request.getSentAt()).isNull();
    }

    private Ticket ticket(long userId, String ticketId, Channel channel) {
        Ticket ticket = new Ticket();
        TicketId id = new TicketId();
        id.setUserId(userId);
        id.setTicketId(ticketId);
        ticket.setId(id);
        ticket.setChannel(channel);
        return ticket;
    }

    private RuntimeCoordinationService passthroughCoordinationService() {
        RuntimeCoordinationService runtimeCoordinationService = mock(RuntimeCoordinationService.class);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(runtimeCoordinationService).runWithLease(anyString(), any(), org.mockito.ArgumentMatchers.any(Runnable.class));
        return runtimeCoordinationService;
    }
}

package com.example.supportbot.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.repository.ChatHistoryRepository;
import com.example.supportbot.repository.FeedbackRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import com.example.supportbot.repository.TicketActiveRepository;
import com.example.supportbot.repository.TicketMessageRepository;
import com.example.supportbot.repository.TicketRepository;
import com.example.supportbot.repository.TicketSpanRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TicketServiceInboundTransportTest {

    @Test
    void recordActiveClientMessagePublishesToRabbitWithoutDirectBusinessWrites() {
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        InboundClientMessagePublisher publisher = mock(InboundClientMessagePublisher.class);

        TicketService service = createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );
        Channel channel = new Channel();
        channel.setId(42L);
        channel.setPlatform("telegram");

        service.recordActiveClientMessage(new ActiveInboundClientMessageCommand(
            101L,
            "client-101",
            "client_user",
            "Client User",
            channel,
            "T-101",
            "Привет",
            "text",
            null,
            null,
            5001L,
            null,
            null,
            OffsetDateTime.parse("2026-08-14T08:00:00Z")
        ));

        verify(publisher).publish(any(ActiveInboundClientMessageCommand.class));
        verify(chatHistoryService, never()).storeUserMessage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(messageRepository, never()).findByTicketId(any());
        verify(ticketActiveRepository, never()).save(any(TicketActive.class));
    }

    @Test
    void recordActiveClientMessageKeepsJdbcWritePathWhenRabbitTransportDisabled() {
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        InboundClientMessagePublisher publisher = mock(InboundClientMessagePublisher.class);
        when(messageRepository.findByTicketId("T-202")).thenReturn(Optional.empty());
        when(ticketActiveRepository.findById("T-202")).thenReturn(Optional.empty());

        TicketService service = createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")
        );
        Channel channel = new Channel();
        channel.setId(55L);
        channel.setPlatform("vk");

        service.recordActiveClientMessage(new ActiveInboundClientMessageCommand(
            202L,
            "vk-202",
            "vk_user",
            "VK Client",
            channel,
            "T-202",
            "Ответ клиента",
            "text",
            null,
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2026-08-14T09:30:00Z")
        ));

        verify(publisher, never()).publish(any(ActiveInboundClientMessageCommand.class));
        verify(chatHistoryService).storeUserMessage(
            eq(202L),
            eq(null),
            eq("Ответ клиента"),
            eq(channel),
            eq("T-202"),
            eq("text"),
            eq(null),
            eq(null),
            eq(null),
            eq(null)
        );
        verify(ticketActiveRepository).save(any(TicketActive.class));
    }

    private TicketService createService(TicketMessageRepository messageRepository,
                                        TicketActiveRepository ticketActiveRepository,
                                        ChatHistoryService chatHistoryService,
                                        InboundClientMessagePublisher publisher,
                                        MockEnvironment environment) {
        return new TicketService(
            mock(TicketRepository.class),
            messageRepository,
            mock(PendingFeedbackRequestRepository.class),
            mock(TicketSpanRepository.class),
            ticketActiveRepository,
            mock(ChatHistoryRepository.class),
            chatHistoryService,
            mock(FeedbackRepository.class),
            mock(AutoCloseFollowUpTaskService.class),
            mock(UiEventOutboxService.class),
            mock(TicketAttributeService.class),
            publisher,
            new BotIntegrationTransportMode(environment)
        );
    }
}

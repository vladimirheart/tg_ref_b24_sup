package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
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
        ConversationTicketCreatedPublisher ticketCreatedPublisher = mock(ConversationTicketCreatedPublisher.class);
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);

        TicketService service = createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            ticketCreatedPublisher,
            panelTicketReadClient,
            panelTicketWriteClient,
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
        ConversationTicketCreatedPublisher ticketCreatedPublisher = mock(ConversationTicketCreatedPublisher.class);
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(messageRepository.findByTicketId("T-202")).thenReturn(Optional.empty());
        when(ticketActiveRepository.findById("T-202")).thenReturn(Optional.empty());

        TicketService service = createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            ticketCreatedPublisher,
            panelTicketReadClient,
            panelTicketWriteClient,
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

    @Test
    void createConversationTicketPublishesBackendOwnedCreationEventInRabbitMode() {
        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        InboundClientMessagePublisher publisher = mock(InboundClientMessagePublisher.class);
        ConversationTicketCreatedPublisher ticketCreatedPublisher = mock(ConversationTicketCreatedPublisher.class);
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);

        TicketService service = createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            ticketCreatedPublisher,
            panelTicketReadClient,
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );
        Channel channel = new Channel();
        channel.setId(61L);
        channel.setPlatform("telegram");

        TicketService.TicketCreationResult created = service.createConversationTicket(
            new ConversationTicketCreationCommand(
                501L,
                "tg-501",
                "tg_user",
                "Telegram User",
                java.util.Map.of("problem", "Пропал интернет"),
                java.util.List.of(),
                java.util.List.of(new ConversationHistoryEntry(501L, "Пропал интернет", "text", null, null, "9001",
                    OffsetDateTime.parse("2026-08-14T11:00:00Z"))),
                channel,
                OffsetDateTime.parse("2026-08-14T11:00:00Z")
            )
        );

        verify(ticketCreatedPublisher).publish(any(), eq(created.ticketId()), eq(""), eq(""), eq(""), eq(""), eq("Пропал интернет"));
        verify(messageRepository, never()).save(any());
        verify(ticketActiveRepository, never()).save(any());
        verify(chatHistoryService, never()).storeEntry(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void findActiveTicketForUserUsesPanelReadClientInRabbitMode() {
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        TicketActive active = new TicketActive();
        active.setTicketId("T-777");
        active.setUser("tg-777");
        active.setLastSeen(OffsetDateTime.parse("2026-08-16T10:00:00Z"));
        when(panelTicketReadClient.isEnabled()).thenReturn(true);
        when(panelTicketReadClient.findActiveTicket(777L, "tg-777", 17L)).thenReturn(Optional.of(active));

        TicketService service = createService(
            mock(TicketMessageRepository.class),
            ticketActiveRepository,
            mock(ChatHistoryService.class),
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            panelTicketReadClient,
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );

        Optional<TicketActive> resolved = service.findActiveTicketForUser(777L, "tg-777", 17L);

        verify(panelTicketReadClient).findActiveTicket(777L, "tg-777", 17L);
        verify(ticketActiveRepository, never()).findByUserInOrderByLastSeenDescAndChannelId(any(), any());
        assertThat(resolved).isPresent();
        assertThat(resolved.orElseThrow().getTicketId()).isEqualTo("T-777");
    }

    @Test
    void findRecentTicketsUsesPanelReadClientInRabbitMode() {
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketReadClient.isEnabled()).thenReturn(true);
        when(panelTicketReadClient.findRecentTickets(501L, 5)).thenReturn(java.util.List.of(
            new TicketService.TicketSummary(
                "T-501",
                "20260816-001",
                "Internet down",
                "Retail",
                "store",
                "Moscow",
                "Tverskaya",
                5,
                OffsetDateTime.parse("2026-08-16T11:00:00Z")
            )
        ));

        TicketMessageRepository messageRepository = mock(TicketMessageRepository.class);
        TicketService service = createService(
            messageRepository,
            mock(TicketActiveRepository.class),
            mock(ChatHistoryService.class),
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            panelTicketReadClient,
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );

        java.util.List<TicketService.TicketSummary> recent = service.findRecentTicketsForUser(501L, 5);

        verify(panelTicketReadClient).findRecentTickets(501L, 5);
        verify(messageRepository, never()).findTop10ByUserIdOrderByCreatedAtDesc(any());
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).ticketId()).isEqualTo("T-501");
    }

    @Test
    void reopenTicketUsesPanelWriteClientInRabbitMode() {
        PanelTicketReadClient panelTicketReadClient = mock(PanelTicketReadClient.class);
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketWriteClient.isEnabled()).thenReturn(true);
        when(panelTicketWriteClient.reopenTicket("T-901")).thenReturn(true);

        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketService service = createService(
            mock(TicketMessageRepository.class),
            mock(TicketActiveRepository.class),
            mock(ChatHistoryService.class),
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            panelTicketReadClient,
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq"),
            ticketRepository
        );

        boolean reopened = service.reopenTicket("T-901");

        assertThat(reopened).isTrue();
        verify(panelTicketWriteClient).reopenTicket("T-901");
        verify(ticketRepository, never()).findByIdTicketId(any());
    }

    @Test
    void registerAndClearActivityUsePanelWriteClientInRabbitMode() {
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketWriteClient.isEnabled()).thenReturn(true);

        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        TicketService service = createService(
            mock(TicketMessageRepository.class),
            ticketActiveRepository,
            mock(ChatHistoryService.class),
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            mock(PanelTicketReadClient.class),
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );

        service.registerActivity("T-777", "operator");
        service.clearTicketActivity("T-777");

        verify(panelTicketWriteClient).registerActivity("T-777", "operator");
        verify(panelTicketWriteClient).clearActivity("T-777");
        verify(ticketActiveRepository, never()).save(any(TicketActive.class));
        verify(ticketActiveRepository, never()).findById(any());
    }

    @Test
    void recordOperatorRelayUsesPanelWriteClientInRabbitMode() {
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketWriteClient.isEnabled()).thenReturn(true);

        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        TicketService service = createService(
            mock(TicketMessageRepository.class),
            mock(TicketActiveRepository.class),
            chatHistoryService,
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            mock(PanelTicketReadClient.class),
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );

        Channel channel = new Channel();
        channel.setId(11L);
        service.recordOperatorRelay(1001L, "T-1001", "Operator reply", channel, 7001L, 6001L, "operator");

        verify(panelTicketWriteClient).recordOperatorRelay("T-1001", "Operator reply", 7001L, 6001L, "operator");
        verify(chatHistoryService, never()).storeOperatorMessage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordOperatorRelayKeepsJdbcWritePathWhenRabbitTransportDisabled() {
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        when(ticketActiveRepository.findById("T-303")).thenReturn(Optional.empty());

        TicketService service = createService(
            mock(TicketMessageRepository.class),
            ticketActiveRepository,
            chatHistoryService,
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            mock(PanelTicketReadClient.class),
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")
        );

        Channel channel = new Channel();
        channel.setId(22L);
        service.recordOperatorRelay(303L, "T-303", "Reply from operator", channel, 8001L, 7999L, "operator");

        verify(chatHistoryService).storeOperatorMessage(303L, "T-303", "Reply from operator", channel, 8001L, 7999L);
        verify(ticketActiveRepository).save(any(TicketActive.class));
        verify(panelTicketWriteClient, never()).recordOperatorRelay(any(), any(), any(), any(), any());
    }

    @Test
    void markClientMessageEditedUsesPanelWriteClientInRabbitMode() {
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        when(panelTicketWriteClient.isEnabled()).thenReturn(true);
        when(panelTicketWriteClient.markClientMessageEdited(44L, 9001L, "Edited by client")).thenReturn(true);

        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        TicketService service = createService(
            mock(TicketMessageRepository.class),
            mock(TicketActiveRepository.class),
            chatHistoryService,
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            mock(PanelTicketReadClient.class),
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")
        );

        boolean updated = service.markClientMessageEdited(44L, 9001L, "Edited by client");

        assertThat(updated).isTrue();
        verify(panelTicketWriteClient).markClientMessageEdited(44L, 9001L, "Edited by client");
        verify(chatHistoryService, never()).markClientMessageEdited(any(), any(), any());
    }

    @Test
    void markClientMessageEditedKeepsJdbcWritePathWhenRabbitTransportDisabled() {
        PanelTicketWriteClient panelTicketWriteClient = mock(PanelTicketWriteClient.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        when(chatHistoryService.markClientMessageEdited(45L, 9002L, "Edited fallback")).thenReturn(true);

        TicketService service = createService(
            mock(TicketMessageRepository.class),
            mock(TicketActiveRepository.class),
            chatHistoryService,
            mock(InboundClientMessagePublisher.class),
            mock(ConversationTicketCreatedPublisher.class),
            mock(PanelTicketReadClient.class),
            panelTicketWriteClient,
            new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")
        );

        boolean updated = service.markClientMessageEdited(45L, 9002L, "Edited fallback");

        assertThat(updated).isTrue();
        verify(chatHistoryService).markClientMessageEdited(45L, 9002L, "Edited fallback");
        verify(panelTicketWriteClient, never()).markClientMessageEdited(any(), any(), any());
    }

    private TicketService createService(TicketMessageRepository messageRepository,
                                        TicketActiveRepository ticketActiveRepository,
                                        ChatHistoryService chatHistoryService,
                                        InboundClientMessagePublisher publisher,
                                        ConversationTicketCreatedPublisher ticketCreatedPublisher,
                                        PanelTicketReadClient panelTicketReadClient,
                                        PanelTicketWriteClient panelTicketWriteClient,
                                        MockEnvironment environment) {
        return createService(
            messageRepository,
            ticketActiveRepository,
            chatHistoryService,
            publisher,
            ticketCreatedPublisher,
            panelTicketReadClient,
            panelTicketWriteClient,
            environment,
            mock(TicketRepository.class)
        );
    }

    private TicketService createService(TicketMessageRepository messageRepository,
                                        TicketActiveRepository ticketActiveRepository,
                                        ChatHistoryService chatHistoryService,
                                        InboundClientMessagePublisher publisher,
                                        ConversationTicketCreatedPublisher ticketCreatedPublisher,
                                        PanelTicketReadClient panelTicketReadClient,
                                        PanelTicketWriteClient panelTicketWriteClient,
                                        MockEnvironment environment,
                                        TicketRepository ticketRepository) {
        return new TicketService(
            ticketRepository,
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
            new BotIntegrationTransportMode(environment),
            ticketCreatedPublisher,
            panelTicketReadClient,
            panelTicketWriteClient
        );
    }
}

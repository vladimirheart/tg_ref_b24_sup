package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.entity.TicketId;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InboundClientMessageIngestionServiceTest {

    @Test
    void ingestStoresInboundMessageInsidePanelBusinessTables() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        ChatHistoryRepository chatHistoryRepository = mock(ChatHistoryRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        ChatAttachmentMetadataService attachmentMetadataService = mock(ChatAttachmentMetadataService.class);

        InboundClientMessageIngestionService service = new InboundClientMessageIngestionService(
            inboxService,
            channelRepository,
            ticketRepository,
            chatHistoryRepository,
            messageRepository,
            ticketActiveRepository,
            attachmentMetadataService
        );

        InboundClientMessageEvent event = new InboundClientMessageEvent(
            "evt-1",
            "client_message.active_ticket",
            "telegram",
            17L,
            "T-17",
            7001L,
            "tg-7001",
            "tg_user",
            "Telegram User",
            "Новый ответ",
            "photo",
            "attachments/inbound/photo.jpg",
            "photo.jpg",
            "1234",
            "1200",
            "forwarded-user",
            OffsetDateTime.parse("2026-08-14T10:15:00Z")
        );
        Channel channel = new Channel();
        channel.setId(17L);
        channel.setPlatform("telegram");
        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setUserId(7001L);
        ticketId.setTicketId("T-17");
        ticket.setId(ticketId);
        ticket.setChannel(channel);
        Message rootMessage = new Message();
        rootMessage.setTicketId("T-17");
        rootMessage.setUsername("old_user");
        rootMessage.setClientName("Old Name");
        ChatHistory persisted = new ChatHistory();
        persisted.setId(501L);

        when(inboxService.beginProcessing(
            eq("evt-1"),
            eq("client_message.active_ticket"),
            eq("telegram"),
            eq(17L),
            eq("T-17"),
            eq("integration.inbound.telegram"),
            eq(event),
            eq(OffsetDateTime.parse("2026-08-14T10:15:00Z"))
        )).thenReturn(true);
        when(channelRepository.findById(17L)).thenReturn(Optional.of(channel));
        when(ticketRepository.findByIdTicketId("T-17")).thenReturn(Optional.of(ticket));
        when(messageRepository.findFirstByTicketId("T-17")).thenReturn(Optional.of(rootMessage));
        when(ticketActiveRepository.findById("T-17")).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any(ChatHistory.class))).thenReturn(persisted);

        service.ingest(event, "integration.inbound.telegram");

        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(historyCaptor.capture());
        ChatHistory saved = historyCaptor.getValue();
        assertThat(saved.getTicketId()).isEqualTo("T-17");
        assertThat(saved.getSender()).isEqualTo("client");
        assertThat(saved.getTgMessageId()).isEqualTo(1234L);
        assertThat(saved.getReplyToTgId()).isEqualTo(1200L);
        assertThat(saved.getForwardedFrom()).isEqualTo("forwarded-user");
        assertThat(saved.getFileName()).isEqualTo("photo.jpg");

        verify(attachmentMetadataService).upsertForChatHistory(
            eq(501L),
            eq("T-17"),
            eq(17L),
            eq("attachments/inbound/photo.jpg"),
            eq("photo.jpg"),
            eq(null),
            eq(null),
            eq("photo")
        );
        verify(messageRepository).save(rootMessage);
        assertThat(rootMessage.getUsername()).isEqualTo("tg_user");
        assertThat(rootMessage.getClientName()).isEqualTo("Telegram User");

        ArgumentCaptor<TicketActive> activeCaptor = ArgumentCaptor.forClass(TicketActive.class);
        verify(ticketActiveRepository).save(activeCaptor.capture());
        assertThat(activeCaptor.getValue().getTicketId()).isEqualTo("T-17");
        assertThat(activeCaptor.getValue().getUserIdentity()).isEqualTo("tg-7001");
        verify(inboxService).markProcessed("evt-1");
    }

    @Test
    void ingestSkipsDuplicateInboxEvent() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        InboundClientMessageIngestionService service = new InboundClientMessageIngestionService(
            inboxService,
            mock(ChannelRepository.class),
            mock(TicketRepository.class),
            mock(ChatHistoryRepository.class),
            mock(MessageRepository.class),
            mock(TicketActiveRepository.class),
            mock(ChatAttachmentMetadataService.class)
        );
        InboundClientMessageEvent event = new InboundClientMessageEvent(
            "evt-dup",
            "client_message.active_ticket",
            "telegram",
            1L,
            "T-dup",
            1L,
            "dup",
            "dup",
            null,
            "text",
            "text",
            null,
            null,
            null,
            null,
            null,
            OffsetDateTime.parse("2026-08-14T10:16:00Z")
        );
        when(inboxService.beginProcessing(
            eq("evt-dup"),
            eq("client_message.active_ticket"),
            eq("telegram"),
            eq(1L),
            eq("T-dup"),
            eq("integration.inbound.telegram"),
            eq(event),
            eq(OffsetDateTime.parse("2026-08-14T10:16:00Z"))
        )).thenReturn(false);

        service.ingest(event, "integration.inbound.telegram");

        verify(inboxService, never()).markProcessed(any());
        verify(inboxService, never()).markFailed(any(), any());
    }
}

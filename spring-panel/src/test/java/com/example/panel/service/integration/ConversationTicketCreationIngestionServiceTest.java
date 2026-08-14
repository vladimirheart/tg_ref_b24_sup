package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
import com.example.panel.entity.TicketSpan;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.TicketSpanRepository;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ConversationTicketCreationIngestionServiceTest {

    @Test
    void ingestCreatesBackendOwnedTicketStateInsidePanel() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketSpanRepository ticketSpanRepository = mock(TicketSpanRepository.class);
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        ChatHistoryRepository chatHistoryRepository = mock(ChatHistoryRepository.class);
        ChatAttachmentMetadataService attachmentMetadataService = mock(ChatAttachmentMetadataService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ConversationTicketCreationIngestionService service = new ConversationTicketCreationIngestionService(
            inboxService,
            channelRepository,
            messageRepository,
            ticketRepository,
            ticketSpanRepository,
            ticketActiveRepository,
            chatHistoryRepository,
            attachmentMetadataService,
            jdbcTemplate
        );

        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-14T11:00:00Z");
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
            "evt-ticket-1",
            "ticket.created.initial_contact",
            "telegram",
            25L,
            "T-RABBIT-1",
            901L,
            "tg-901",
            "tg_user",
            "Telegram User",
            "Retail",
            "store",
            "Moscow",
            "Tverskaya",
            "Internet down",
            occurredAt,
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload(
                "problem",
                "problem",
                "Problem",
                "text",
                null,
                null,
                "Internet down",
                true
            )),
            List.of(new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(
                901L,
                "Internet down",
                "photo",
                "attachments/inbound/photo.jpg",
                "photo.jpg",
                "9001",
                occurredAt
            ))
        );

        Channel channel = new Channel();
        channel.setId(25L);
        channel.setPlatform("telegram");

        when(inboxService.beginProcessing(
            eq("evt-ticket-1"),
            eq("ticket.created.initial_contact"),
            eq("telegram"),
            eq(25L),
            eq("T-RABBIT-1"),
            eq("integration.ticket.telegram"),
            eq(event),
            eq(occurredAt)
        )).thenReturn(true);
        when(channelRepository.findById(25L)).thenReturn(Optional.of(channel));
        when(ticketRepository.findByIdTicketId("T-RABBIT-1")).thenReturn(Optional.empty());
        when(chatHistoryRepository.save(any(ChatHistory.class))).thenAnswer(invocation -> {
            ChatHistory history = invocation.getArgument(0);
            history.setId(801L);
            return history;
        });

        service.ingest(event, "integration.ticket.telegram");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(messageCaptor.capture());
        Message message = messageCaptor.getValue();
        assertThat(message.getUserId()).isEqualTo(901L);
        assertThat(message.getBusiness()).isEqualTo("Retail");
        assertThat(message.getLocationType()).isEqualTo("store");
        assertThat(message.getCity()).isEqualTo("Moscow");
        assertThat(message.getLocationName()).isEqualTo("Tverskaya");
        assertThat(message.getProblem()).isEqualTo("Internet down");
        assertThat(message.getTicketId()).isEqualTo("T-RABBIT-1");
        assertThat(message.getUsername()).isEqualTo("tg_user");
        assertThat(message.getClientName()).isEqualTo("Telegram User");
        assertThat(message.getChannel()).isSameAs(channel);

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        Ticket ticket = ticketCaptor.getValue();
        assertThat(ticket.getTicketId()).isEqualTo("T-RABBIT-1");
        assertThat(ticket.getUserId()).isEqualTo(901L);
        assertThat(ticket.getStatus()).isEqualTo("open");
        assertThat(ticket.getChannel()).isSameAs(channel);
        assertThat(ticket.getGroupMessageId()).isEqualTo(message.getId());

        ArgumentCaptor<TicketSpan> spanCaptor = ArgumentCaptor.forClass(TicketSpan.class);
        verify(ticketSpanRepository).save(spanCaptor.capture());
        assertThat(spanCaptor.getValue().getTicketId()).isEqualTo("T-RABBIT-1");
        assertThat(spanCaptor.getValue().getSpanNo()).isEqualTo(1);
        assertThat(spanCaptor.getValue().getStartedAt()).isEqualTo(occurredAt);

        ArgumentCaptor<TicketActive> activeCaptor = ArgumentCaptor.forClass(TicketActive.class);
        verify(ticketActiveRepository).save(activeCaptor.capture());
        assertThat(activeCaptor.getValue().getTicketId()).isEqualTo("T-RABBIT-1");
        assertThat(activeCaptor.getValue().getUserIdentity()).isEqualTo("tg-901");
        assertThat(activeCaptor.getValue().getLastSeen()).isEqualTo(occurredAt);

        ArgumentCaptor<ChatHistory> historyCaptor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(chatHistoryRepository).save(historyCaptor.capture());
        ChatHistory history = historyCaptor.getValue();
        assertThat(history.getTicketId()).isEqualTo("T-RABBIT-1");
        assertThat(history.getSender()).isEqualTo("client");
        assertThat(history.getMessage()).isEqualTo("Internet down");
        assertThat(history.getMessageType()).isEqualTo("photo");
        assertThat(history.getAttachment()).isEqualTo("attachments/inbound/photo.jpg");
        assertThat(history.getFileName()).isEqualTo("photo.jpg");
        assertThat(history.getTgMessageId()).isEqualTo(9001L);
        assertThat(history.getChannel()).isSameAs(channel);

        verify(jdbcTemplate).update("DELETE FROM ticket_attributes WHERE ticket_id = ?", "T-RABBIT-1");
        verify(jdbcTemplate).batchUpdate(any(String.class), anyList());
        verify(attachmentMetadataService).upsertForChatHistory(
            eq(801L),
            eq("T-RABBIT-1"),
            eq(25L),
            eq("attachments/inbound/photo.jpg"),
            eq("photo.jpg"),
            eq(null),
            eq(null),
            eq("photo")
        );
        verify(inboxService).markProcessed("evt-ticket-1");
    }

    @Test
    void ingestSkipsDuplicateInboxEvent() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ConversationTicketCreationIngestionService service = new ConversationTicketCreationIngestionService(
            inboxService,
            mock(ChannelRepository.class),
            mock(MessageRepository.class),
            mock(TicketRepository.class),
            mock(TicketSpanRepository.class),
            mock(TicketActiveRepository.class),
            mock(ChatHistoryRepository.class),
            mock(ChatAttachmentMetadataService.class),
            mock(JdbcTemplate.class)
        );
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
            "evt-ticket-dup",
            "ticket.created.initial_contact",
            "telegram",
            2L,
            "T-RABBIT-DUP",
            2L,
            "tg-2",
            "tg_dup",
            null,
            "",
            "",
            "",
            "",
            "Need help",
            OffsetDateTime.parse("2026-08-14T11:05:00Z"),
            List.of(),
            List.of()
        );

        when(inboxService.beginProcessing(
            eq("evt-ticket-dup"),
            eq("ticket.created.initial_contact"),
            eq("telegram"),
            eq(2L),
            eq("T-RABBIT-DUP"),
            eq("integration.ticket.telegram"),
            eq(event),
            eq(OffsetDateTime.parse("2026-08-14T11:05:00Z"))
        )).thenReturn(false);

        service.ingest(event, "integration.ticket.telegram");

        verify(inboxService, never()).markProcessed(any());
        verify(inboxService, never()).markFailed(any(), any());
    }
}

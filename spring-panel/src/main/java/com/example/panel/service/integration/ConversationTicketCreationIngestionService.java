package com.example.panel.service.integration;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.TicketSpan;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.TicketSpanRepository;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationTicketCreationIngestionService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IntegrationInboundEventInboxService inboxService;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final TicketRepository ticketRepository;
    private final TicketSpanRepository ticketSpanRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;
    private final JdbcTemplate jdbcTemplate;

    public ConversationTicketCreationIngestionService(IntegrationInboundEventInboxService inboxService,
                                                      ChannelRepository channelRepository,
                                                      MessageRepository messageRepository,
                                                      TicketRepository ticketRepository,
                                                      TicketSpanRepository ticketSpanRepository,
                                                      TicketActiveRepository ticketActiveRepository,
                                                      ChatHistoryRepository chatHistoryRepository,
                                                      ChatAttachmentMetadataService chatAttachmentMetadataService,
                                                      JdbcTemplate jdbcTemplate) {
        this.inboxService = inboxService;
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.ticketSpanRepository = ticketSpanRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void ingest(ConversationTicketCreatedEvent event, String routingKey) {
        if (event == null || !StringUtils.hasText(event.eventId())) {
            return;
        }
        if (!inboxService.beginProcessing(
            event.eventId(),
            event.eventKind(),
            event.platform(),
            event.channelId(),
            event.ticketId(),
            routingKey,
            event,
            event.occurredAt()
        )) {
            return;
        }
        try {
            processEvent(event);
            inboxService.markProcessed(event.eventId());
        } catch (Exception ex) {
            inboxService.markFailed(event.eventId(), ex);
            throw ex;
        }
    }

    private void processEvent(ConversationTicketCreatedEvent event) {
        Channel channel = channelRepository.findById(event.channelId())
            .orElseThrow(() -> new IllegalStateException("Ticket creation channel not found: " + event.channelId()));
        if (ticketRepository.findByIdTicketId(event.ticketId()).isPresent()) {
            return;
        }

        OffsetDateTime occurredAt = event.occurredAt() != null ? event.occurredAt() : OffsetDateTime.now();
        long groupMessageId = nextGroupMessageId();

        Message message = buildRootMessage(event, channel, occurredAt, groupMessageId);
        messageRepository.save(message);

        Ticket ticket = buildTicket(event, channel, groupMessageId, occurredAt);
        ticketRepository.save(ticket);

        TicketSpan span = new TicketSpan();
        span.setTicketId(event.ticketId());
        span.setSpanNo(1);
        span.setStartedAt(occurredAt);
        ticketSpanRepository.save(span);

        TicketActive active = new TicketActive();
        active.setTicketId(event.ticketId());
        active.setUserIdentity(resolveUserIdentity(event));
        active.setLastSeen(occurredAt);
        ticketActiveRepository.save(active);

        replaceTicketAttributes(event.ticketId(), event.attributes(), occurredAt);
        storeConversationHistory(event, channel, occurredAt);
    }

    private Message buildRootMessage(ConversationTicketCreatedEvent event,
                                     Channel channel,
                                     OffsetDateTime occurredAt,
                                     long groupMessageId) {
        Message message = new Message();
        message.setId(groupMessageId);
        message.setUserId(event.userId());
        message.setBusiness(event.business());
        message.setLocationType(event.locationType());
        message.setCity(event.city());
        message.setLocationName(event.locationName());
        message.setProblem(event.problem());
        message.setCreatedAt(occurredAt);
        message.setCreatedDate(LocalDate.from(occurredAt));
        message.setCreatedTime(TIME_FORMATTER.format(occurredAt));
        message.setUsername(trimToNull(event.username()));
        message.setClientName(trimToNull(event.clientName()));
        message.setTicketId(event.ticketId());
        message.setChannel(channel);
        return message;
    }

    private Ticket buildTicket(ConversationTicketCreatedEvent event,
                               Channel channel,
                               long groupMessageId,
                               OffsetDateTime occurredAt) {
        Ticket ticket = new Ticket();
        TicketId id = new TicketId();
        id.setUserId(event.userId());
        id.setTicketId(event.ticketId());
        ticket.setId(id);
        ticket.setGroupMessageId(groupMessageId);
        ticket.setStatus("open");
        ticket.setChannel(channel);
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticket.setLastReopenAt(occurredAt);
        return ticket;
    }

    private void replaceTicketAttributes(String ticketId,
                                         List<ConversationTicketCreatedEvent.TicketAttributePayload> attributes,
                                         OffsetDateTime occurredAt) {
        jdbcTemplate.update("DELETE FROM ticket_attributes WHERE ticket_id = ?", ticketId);
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        List<Object[]> batch = new ArrayList<>();
        for (ConversationTicketCreatedEvent.TicketAttributePayload attribute : attributes) {
            if (attribute == null || !StringUtils.hasText(attribute.questionId())) {
                continue;
            }
            String attributeKey = StringUtils.hasText(attribute.attributeKey())
                ? attribute.attributeKey().trim()
                : attribute.questionId().trim();
            if (!StringUtils.hasText(attribute.valueId())
                && !StringUtils.hasText(attribute.valueLabel())
                && !StringUtils.hasText(attribute.valueText())) {
                continue;
            }
            batch.add(new Object[] {
                ticketId,
                attribute.questionId().trim(),
                attributeKey,
                trimToNull(attribute.questionText()),
                StringUtils.hasText(attribute.inputType()) ? attribute.inputType().trim() : "custom",
                trimToNull(attribute.valueId()),
                trimToNull(attribute.valueLabel()),
                trimToNull(attribute.valueText()),
                attribute.includeInDashboard(),
                occurredAt.toString(),
                occurredAt.toString()
            });
        }
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO ticket_attributes (
                    ticket_id,
                    question_id,
                    attribute_key,
                    question_text,
                    input_type,
                    value_id,
                    value_label,
                    value_text,
                    include_in_dashboard,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch);
    }

    private void storeConversationHistory(ConversationTicketCreatedEvent event,
                                          Channel channel,
                                          OffsetDateTime occurredAt) {
        List<ConversationTicketCreatedEvent.ConversationHistoryEntryPayload> historyEntries = event.historyEntries();
        if (historyEntries == null || historyEntries.isEmpty()) {
            return;
        }
        for (int index = 0; index < historyEntries.size(); index++) {
            ConversationTicketCreatedEvent.ConversationHistoryEntryPayload entry = historyEntries.get(index);
            if (entry == null) {
                continue;
            }
            OffsetDateTime entryTime = entry.occurredAt() != null ? entry.occurredAt() : occurredAt.plusNanos(index + 1L);
            ChatHistory history = new ChatHistory();
            history.setUserId(entry.userId() != null ? entry.userId() : event.userId());
            history.setSender("client");
            history.setMessage(entry.text());
            history.setTimestamp(entryTime);
            history.setTicketId(event.ticketId());
            history.setMessageType(entry.messageType());
            history.setAttachment(entry.attachmentPath());
            history.setFileName(entry.attachmentName());
            history.setChannel(channel);
            history.setTgMessageId(parseLong(entry.providerMessageId()));
            ChatHistory saved = chatHistoryRepository.save(history);
            chatAttachmentMetadataService.upsertForChatHistory(
                saved.getId(),
                event.ticketId(),
                channel.getId(),
                entry.attachmentPath(),
                entry.attachmentName(),
                null,
                null,
                entry.messageType()
            );
        }
    }

    private String resolveUserIdentity(ConversationTicketCreatedEvent event) {
        if (StringUtils.hasText(event.userIdentity())) {
            return event.userIdentity().trim();
        }
        if (StringUtils.hasText(event.username())) {
            return event.username().trim();
        }
        return event.userId() != null ? event.userId().toString() : "unknown";
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private long nextGroupMessageId() {
        long candidate = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        if (candidate == 0L) {
            return System.currentTimeMillis();
        }
        return candidate;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

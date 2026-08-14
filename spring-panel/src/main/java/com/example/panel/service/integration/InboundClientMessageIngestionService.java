package com.example.panel.service.integration;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InboundClientMessageIngestionService {

    private final IntegrationInboundEventInboxService inboxService;
    private final ChannelRepository channelRepository;
    private final TicketRepository ticketRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;

    public InboundClientMessageIngestionService(IntegrationInboundEventInboxService inboxService,
                                                ChannelRepository channelRepository,
                                                TicketRepository ticketRepository,
                                                ChatHistoryRepository chatHistoryRepository,
                                                MessageRepository messageRepository,
                                                TicketActiveRepository ticketActiveRepository,
                                                ChatAttachmentMetadataService chatAttachmentMetadataService) {
        this.inboxService = inboxService;
        this.channelRepository = channelRepository;
        this.ticketRepository = ticketRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.messageRepository = messageRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
    }

    @Transactional
    public void ingest(InboundClientMessageEvent event, String routingKey) {
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

    private void processEvent(InboundClientMessageEvent event) {
        Channel channel = channelRepository.findById(event.channelId())
            .orElseThrow(() -> new IllegalStateException("Inbound event channel not found: " + event.channelId()));
        Ticket ticket = ticketRepository.findByIdTicketId(event.ticketId())
            .orElseThrow(() -> new IllegalStateException("Inbound event ticket not found: " + event.ticketId()));
        if (ticket.getChannel() != null && ticket.getChannel().getId() != null
            && !ticket.getChannel().getId().equals(channel.getId())) {
            throw new IllegalStateException(
                "Inbound event channel " + event.channelId() + " does not match ticket channel for " + event.ticketId()
            );
        }

        OffsetDateTime occurredAt = event.occurredAt() != null ? event.occurredAt() : OffsetDateTime.now();
        ChatHistory history = new ChatHistory();
        history.setUserId(event.userId());
        history.setSender("client");
        history.setMessage(event.text());
        history.setTimestamp(occurredAt);
        history.setTicketId(event.ticketId());
        history.setMessageType(event.messageType());
        history.setAttachment(event.attachmentPath());
        history.setChannel(channel);
        history.setTgMessageId(parseLong(event.providerMessageId()));
        history.setReplyToTgId(parseLong(event.replyToProviderMessageId()));
        history.setForwardedFrom(event.forwardedFrom());
        history.setFileName(event.attachmentName());
        ChatHistory saved = chatHistoryRepository.save(history);

        chatAttachmentMetadataService.upsertForChatHistory(
            saved.getId(),
            event.ticketId(),
            channel.getId(),
            event.attachmentPath(),
            event.attachmentName(),
            null,
            null,
            event.messageType()
        );

        messageRepository.findFirstByTicketId(event.ticketId()).ifPresent(message -> syncClientProfile(message, event, occurredAt));
        upsertTicketActivity(event, occurredAt);
    }

    private void syncClientProfile(Message message, InboundClientMessageEvent event, OffsetDateTime occurredAt) {
        boolean changed = false;
        if (StringUtils.hasText(event.username()) && !event.username().equals(message.getUsername())) {
            message.setUsername(event.username().trim());
            changed = true;
        }
        if (StringUtils.hasText(event.clientName()) && !event.clientName().equals(message.getClientName())) {
            message.setClientName(event.clientName().trim());
            changed = true;
        }
        if (changed) {
            message.setUpdatedAt(occurredAt);
            message.setUpdatedBy("rabbitmq_inbound_bridge");
            messageRepository.save(message);
        }
    }

    private void upsertTicketActivity(InboundClientMessageEvent event, OffsetDateTime occurredAt) {
        TicketActive active = ticketActiveRepository.findById(event.ticketId()).orElseGet(() -> {
            TicketActive created = new TicketActive();
            created.setTicketId(event.ticketId());
            return created;
        });
        active.setLastSeen(occurredAt);
        if (StringUtils.hasText(event.userIdentity())) {
            active.setUserIdentity(event.userIdentity().trim());
        }
        ticketActiveRepository.save(active);
    }

    private Long parseLong(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

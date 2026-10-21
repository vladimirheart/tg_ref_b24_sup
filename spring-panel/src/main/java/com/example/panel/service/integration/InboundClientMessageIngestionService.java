package com.example.panel.service.integration;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InboundClientMessageIngestionService {

    private final IntegrationInboundEventInboxService inboxService;
    private final LegacyTicketIdConcurrencyGuard legacyTicketIdConcurrencyGuard;
    private final ChannelRepository channelRepository;
    private final TicketRepository ticketRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;
    private SelectedTicketFollowupMediaService selectedMedia;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelectedMedia(SelectedTicketFollowupMediaService selectedMedia) { this.selectedMedia=selectedMedia; }

    public InboundClientMessageIngestionService(IntegrationInboundEventInboxService inboxService,
                                                LegacyTicketIdConcurrencyGuard legacyTicketIdConcurrencyGuard,
                                                ChannelRepository channelRepository,
                                                TicketRepository ticketRepository,
                                                ChatHistoryRepository chatHistoryRepository,
                                                MessageRepository messageRepository,
                                                TicketActiveRepository ticketActiveRepository,
                                                ChatAttachmentMetadataService chatAttachmentMetadataService) {
        this.inboxService = inboxService;
        this.legacyTicketIdConcurrencyGuard = legacyTicketIdConcurrencyGuard;
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
            if(InboundClientMessageEvent.MEDIA_BATCH_KIND.equals(event.eventKind())) inboxService.markProcessedWithWrites(event.eventId());
            else inboxService.markProcessed(event.eventId());
        } catch (Exception ex) {
            inboxService.markFailed(event.eventId(), ex);
            throw ex;
        }
    }

    private void processEvent(InboundClientMessageEvent event) {
        Channel channel = channelRepository.findById(event.channelId())
            .orElseThrow(() -> new IllegalStateException("Inbound event channel not found: " + event.channelId()));
        legacyTicketIdConcurrencyGuard.acquire(event.ticketId());
        TicketId canonicalTicketId = new TicketId();
        canonicalTicketId.setUserId(event.userId());
        canonicalTicketId.setTicketId(event.ticketId());
        Ticket ticket = ticketRepository.findById(canonicalTicketId)
            .orElseThrow(() -> new IllegalStateException("Inbound event ticket not found: " + event.ticketId()));
        if (ticket.getChannel() != null && ticket.getChannel().getId() != null
            && !ticket.getChannel().getId().equals(channel.getId())) {
            throw new IllegalStateException(
                "Inbound event channel " + event.channelId() + " does not match ticket channel for " + event.ticketId()
            );
        }

        OffsetDateTime occurredAt = event.occurredAt() != null ? event.occurredAt() : OffsetDateTime.now();
        if(InboundClientMessageEvent.MEDIA_BATCH_KIND.equals(event.eventKind())) {
            if(selectedMedia==null) throw new IllegalStateException("Selected follow-up media verifier is unavailable");
            var proof=selectedMedia.verifyVkBatch(event);
            for(var item:event.mediaBatch()) {
                var entry=new InboundClientMessageEvent(event.eventId(),event.eventKind(),event.platform(),event.channelId(),event.ticketId(),
                    event.userId(),event.userIdentity(),event.username(),event.clientName(),event.text(),item.messageType(),item.attachmentPath(),
                    item.attachmentName(),event.providerMessageId(),event.replyToProviderMessageId(),event.forwardedFrom(),event.occurredAt());
                storeHistory(entry,channel,ticket,occurredAt,proof.get(item.attachmentPath()));
            }
            messageRepository.findFirstByTicketId(event.ticketId()).ifPresent(message -> syncClientProfile(message,event,occurredAt));
            upsertTicketActivity(event,occurredAt);
            return;
        }
        if(event.mediaBatch()!=null) throw new IllegalStateException("A batch requires its versioned event kind");
        boolean selectedRoot=ticket.getQuestionnaireCatalogSha256()!=null;
        boolean media=event.attachmentPath()!=null || !"text".equals(event.messageType());
        SelectedQuestionnaireMediaValidator.Verified verified=null;
        if((selectedRoot && media && ("telegram".equals(channel.getPlatform()) || "vk".equals(channel.getPlatform())))
                || com.example.panel.storage.QuestionnaireMediaStorageProof.isScoped(event.attachmentPath())) {
            if(selectedMedia==null) throw new IllegalStateException("Selected follow-up media verifier is unavailable");
            verified=selectedMedia.verify(event);
        }
        storeHistory(event,channel,ticket,occurredAt,verified);
        messageRepository.findFirstByTicketId(event.ticketId()).ifPresent(message -> syncClientProfile(message, event, occurredAt));
        upsertTicketActivity(event, occurredAt);
    }

    private void storeHistory(InboundClientMessageEvent event,Channel channel,Ticket ticket,OffsetDateTime occurredAt,
            SelectedQuestionnaireMediaValidator.Verified verified) {
        ChatHistory history = new ChatHistory();
        history.setUserId(event.userId());
        history.setSender("client");
        history.setMessage(event.text());
        history.setTimestamp(occurredAt);
        history.setTicketId(event.ticketId());
        history.setTicketOwnerUserId(ticket.getId().getUserId());
        history.setMessageType(event.messageType());
        history.setAttachment(event.attachmentPath());
        history.setChannel(channel);
        history.setTgMessageId(parseLong(event.providerMessageId()));
        history.setReplyToTgId(parseLong(event.replyToProviderMessageId()));
        history.setForwardedFrom(event.forwardedFrom());
        history.setFileName(event.attachmentName());
        ChatHistory saved = chatHistoryRepository.save(history);

        if(verified!=null) chatAttachmentMetadataService.upsertForChatHistory(saved.getId(),event.ticketId(),channel.getId(),
            event.attachmentPath(),event.attachmentName(),null,verified.size(),event.messageType(),verified.sha256());
        else chatAttachmentMetadataService.upsertForChatHistory(
            saved.getId(),
            event.ticketId(),
            channel.getId(),
            event.attachmentPath(),
            event.attachmentName(),
            null,
            null,
            event.messageType()
        );

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

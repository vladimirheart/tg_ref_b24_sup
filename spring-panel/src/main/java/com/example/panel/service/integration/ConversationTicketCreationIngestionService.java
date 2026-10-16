package com.example.panel.service.integration;

import com.example.panel.config.MultibusinessFeatureFlags;
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
import com.example.panel.service.GeneralChannelQuestionTemplateService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ConversationTicketCreationIngestionService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final IntegrationInboundEventInboxService inboxService;
    private final LegacyTicketIdConcurrencyGuard legacyTicketIdConcurrencyGuard;
    private final ChannelRepository channelRepository;
    private final MessageRepository messageRepository;
    private final TicketRepository ticketRepository;
    private final TicketSpanRepository ticketSpanRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;
    private final JdbcTemplate jdbcTemplate;
    private final MultibusinessFeatureFlags multibusinessFeatureFlags;
    private final BusinessTicketWriteScopeResolver businessTicketWriteScopeResolver;
    private final GeneralChannelQuestionTemplateService generalChannelQuestionTemplateService;

    public ConversationTicketCreationIngestionService(IntegrationInboundEventInboxService inboxService,
                                                      LegacyTicketIdConcurrencyGuard legacyTicketIdConcurrencyGuard,
                                                      ChannelRepository channelRepository,
                                                      MessageRepository messageRepository,
                                                      TicketRepository ticketRepository,
                                                      TicketSpanRepository ticketSpanRepository,
                                                      TicketActiveRepository ticketActiveRepository,
                                                      ChatHistoryRepository chatHistoryRepository,
                                                      ChatAttachmentMetadataService chatAttachmentMetadataService,
                                                      JdbcTemplate jdbcTemplate,
                                                      MultibusinessFeatureFlags multibusinessFeatureFlags,
                                                      BusinessTicketWriteScopeResolver businessTicketWriteScopeResolver,
                                                      GeneralChannelQuestionTemplateService generalChannelQuestionTemplateService) {
        this.inboxService = inboxService;
        this.legacyTicketIdConcurrencyGuard = legacyTicketIdConcurrencyGuard;
        this.channelRepository = channelRepository;
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.ticketSpanRepository = ticketSpanRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
        this.jdbcTemplate = jdbcTemplate;
        this.multibusinessFeatureFlags = multibusinessFeatureFlags;
        this.businessTicketWriteScopeResolver = businessTicketWriteScopeResolver;
        this.generalChannelQuestionTemplateService = generalChannelQuestionTemplateService;
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
        legacyTicketIdConcurrencyGuard.acquire(event.ticketId());
        // Idempotency is scoped to the canonical composite ticket identity.
        TicketId canonicalLocator = new TicketId();
        canonicalLocator.setUserId(event.userId());
        canonicalLocator.setTicketId(event.ticketId());
        boolean strictBusinessWrite = multibusinessFeatureFlags.isTicketWriteRequiresBusiness();
        if (!strictBusinessWrite && "GENERAL".equals(event.scopeKind())) {
            throw new IllegalStateException("General ticket scope requires strict ticket writes");
        }
        if (strictBusinessWrite && !multibusinessFeatureFlags.isFoundationEnabled()) {
            throw new IllegalStateException("Business ticket writes require the multibusiness foundation");
        }
        if (ticketRepository.existsById(canonicalLocator)) {
            if (strictBusinessWrite) {
                // A retry keeps the ticket's frozen owner even if channel bindings changed later.
                Ticket existing = ticketRepository.findById(canonicalLocator)
                    .orElseThrow(() -> new IllegalStateException("Ticket creation replay lost its existing ticket"));
                Long existingChannelId = existing.getChannel() != null ? existing.getChannel().getId() : null;
                boolean generalReplay = isGeneralScope(event);
                boolean businessReplay = (event.scopeKind() == null || "BUSINESS".equals(event.scopeKind()))
                    && event.scopeSelectionSource() == null
                    && event.businessId() != null && event.businessId() > 0;
                if ((!generalReplay && !businessReplay)
                    || existing.isGeneral() != generalReplay
                    || !Objects.equals(existing.getBusinessId(), event.businessId())
                    || (generalReplay && !Objects.equals(existing.getGeneralSelectionSource(),
                        event.scopeSelectionSource()))
                    || !Objects.equals(existingChannelId, event.channelId())) {
                    throw new IllegalStateException("Ticket creation replay business or channel mismatch");
                }
            }
            return;
        }
        // Legacy child records still use a bare ticket_id; a cross-user collision
        // must fail closed instead of silently dropping an unrelated ticket.
        if (ticketRepository.existsByIdTicketId(event.ticketId())) {
            throw new IllegalStateException("Legacy ticket ID collision across users");
        }

        Long businessId = null;
        boolean general = false;
        if (strictBusinessWrite) {
            if ("GENERAL".equals(event.scopeKind())) {
                if (!isGeneralScope(event) || !Boolean.TRUE.equals(channel.getActive())) {
                    throw new IllegalStateException("General ticket requires verified scope and active channel");
                }
                if ("GENERAL_CHANNEL".equals(event.scopeSelectionSource())
                        && (!multibusinessFeatureFlags.isGeneralChannelResolution()
                            || generalChannelQuestionTemplateService.resolve(event.channelId()).isEmpty())) {
                    throw new IllegalStateException("General channel questionnaire is not verified");
                }
                general = true;
            } else {
                if (event.scopeKind() != null && !"BUSINESS".equals(event.scopeKind())) {
                    throw new IllegalStateException("Unsupported ticket scope kind");
                }
                if (event.scopeSelectionSource() != null) {
                    throw new IllegalStateException("Unexpected business scope selection source");
                }
                businessId = businessTicketWriteScopeResolver.requireSingleBusinessBinding(
                    event.channelId(), event.businessId());
            }
        }

        OffsetDateTime occurredAt = event.occurredAt() != null ? event.occurredAt() : OffsetDateTime.now();
        long groupMessageId = nextGroupMessageId();

        Message message = buildRootMessage(event, channel, occurredAt, groupMessageId, general);
        messageRepository.save(message);

        Ticket ticket = buildTicket(event, channel, groupMessageId, occurredAt, businessId, general);
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
                                     long groupMessageId,
                                     boolean general) {
        Message message = new Message();
        message.setId(groupMessageId);
        message.setUserId(event.userId());
        message.setBusiness(general ? null : event.business());
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
                               OffsetDateTime occurredAt,
                               Long businessId,
                               boolean general) {
        Ticket ticket = new Ticket();
        TicketId id = new TicketId();
        id.setUserId(event.userId());
        id.setTicketId(event.ticketId());
        ticket.setId(id);
        ticket.setGroupMessageId(groupMessageId);
        ticket.setStatus("open");
        ticket.setChannel(channel);
        ticket.setBusinessId(businessId);
        ticket.setGeneral(general);
        ticket.setGeneralSelectionSource(general ? event.scopeSelectionSource() : null);
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticket.setLastReopenAt(occurredAt);
        return ticket;
    }

    private boolean isGeneralScope(ConversationTicketCreatedEvent event) {
        return "GENERAL".equals(event.scopeKind())
            && event.businessId() == null
            && ("USER_SELECTION".equals(event.scopeSelectionSource())
                || "GENERAL_CHANNEL".equals(event.scopeSelectionSource()));
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
            history.setTicketOwnerUserId(event.userId());
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

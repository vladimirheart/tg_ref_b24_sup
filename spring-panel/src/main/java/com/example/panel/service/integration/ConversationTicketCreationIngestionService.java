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
import com.example.panel.service.BusinessQuestionTemplateRegistryService;
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
    private final BusinessQuestionTemplateRegistryService businessTemplates;
    private final com.example.panel.service.SharedGeneralQuestionnaireService sharedQuestionnaires;

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
                                                      GeneralChannelQuestionTemplateService generalChannelQuestionTemplateService,
                                                      BusinessQuestionTemplateRegistryService businessTemplates,
                                                      com.example.panel.service.SharedGeneralQuestionnaireService sharedQuestionnaires) {
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
        this.businessTemplates = businessTemplates;
        this.sharedQuestionnaires = sharedQuestionnaires;
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
        boolean generalChannel = isGeneralScope(event)
            && "GENERAL_CHANNEL".equals(event.scopeSelectionSource());
        boolean selectedBusiness = "BUSINESS".equals(event.scopeKind())
            && "USER_SELECTION".equals(event.scopeSelectionSource());
        boolean sharedChannel = businessTicketWriteScopeResolver.requiresExplicitSelection(event.channelId());
        boolean selectedGeneral = isGeneralScope(event) && "USER_SELECTION".equals(event.scopeSelectionSource())
            && (event.catalogSha256() != null || sharedChannel);
        if (selectedBusiness || selectedGeneral) {
            requireSelectedQuestionnaireShape(event);
            if (!strictBusinessWrite || !multibusinessFeatureFlags.isSharedChannelResolution()) {
                throw new IllegalStateException("Business questionnaire selection requires strict shared-channel writes");
            }
        }
        if (selectedGeneral && !multibusinessFeatureFlags.isGeneralChannelResolution())
            throw new IllegalStateException("Shared GENERAL selection requires GENERAL resolution");
        if (!selectedBusiness && !selectedGeneral && event.catalogSha256() != null) {
            throw new IllegalStateException("Unexpected questionnaire catalog fingerprint");
        }
        if (!generalChannel && !selectedBusiness && !selectedGeneral
                && (event.questionTemplateId() != null || event.templateSha256() != null)) {
            throw new IllegalStateException("Questionnaire provenance requires a general channel scope");
        }
        if (ticketRepository.existsById(canonicalLocator)) {
            Ticket existing = ticketRepository.findById(canonicalLocator)
                .orElseThrow(() -> new IllegalStateException("Ticket creation replay lost its existing ticket"));
            if (strictBusinessWrite || existing.getBusinessSelectionSource() != null
                    || existing.getQuestionnaireCatalogSha256() != null) {
                // A retry keeps the frozen owner/version, including after feature flags change.
                Long existingChannelId = existing.getChannel() != null ? existing.getChannel().getId() : null;
                boolean generalReplay = isGeneralScope(event);
                boolean businessReplay = (event.scopeKind() == null || "BUSINESS".equals(event.scopeKind()))
                    && (event.scopeSelectionSource() == null || selectedBusiness)
                    && event.businessId() != null && event.businessId() > 0;
                if ((!generalReplay && !businessReplay)
                    || existing.isGeneral() != generalReplay
                    || !Objects.equals(existing.getBusinessId(), event.businessId())
                    || !Objects.equals(existing.getBusinessSelectionSource(),
                        selectedBusiness ? event.scopeSelectionSource() : null)
                    || (generalReplay && !Objects.equals(existing.getGeneralSelectionSource(),
                        event.scopeSelectionSource()))
                    || !Objects.equals(existingChannelId, event.channelId())) {
                    throw new IllegalStateException("Ticket creation replay business or channel mismatch");
                }
                if (!Objects.equals(existing.getQuestionTemplateId(), event.questionTemplateId())
                    || !Objects.equals(existing.getQuestionTemplateSha256(), event.templateSha256())
                    || !Objects.equals(existing.getQuestionnaireCatalogSha256(), event.catalogSha256())) {
                    throw new IllegalStateException("Ticket creation replay questionnaire mismatch");
                }
            }
            return;
        }
        // Legacy child records still use a bare ticket_id; a cross-user collision
        // must fail closed instead of silently dropping an unrelated ticket.
        if (ticketRepository.existsByIdTicketId(event.ticketId())) {
            throw new IllegalStateException("Legacy ticket ID collision across users");
        }

        if (!selectedBusiness && !selectedGeneral && sharedChannel) {
            throw new IllegalStateException("Shared channel requires an explicit verified business questionnaire");
        }

        if (generalChannelQuestionTemplateService.findBinding(event.channelId())
                .filter(GeneralChannelQuestionTemplateService.Binding::active).isPresent()
                && (!strictBusinessWrite || !"GENERAL".equals(event.scopeKind())
                    || !"GENERAL_CHANNEL".equals(event.scopeSelectionSource()))) {
            throw new IllegalStateException("General-only channel requires a verified general questionnaire scope");
        }

        Long businessId = null;
        boolean general = false;
        if (strictBusinessWrite) {
            if ("GENERAL".equals(event.scopeKind())) {
                if (!isGeneralScope(event) || !Boolean.TRUE.equals(channel.getActive())) {
                    throw new IllegalStateException("General ticket requires verified scope and active channel");
                }
                if (generalChannel) {
                    requireMatchingGeneralQuestionnaire(event);
                } else if (selectedGeneral) {
                    requireSharedSelection(event);
                }
                general = true;
            } else {
                if (event.scopeKind() != null && !"BUSINESS".equals(event.scopeKind())) {
                    throw new IllegalStateException("Unsupported ticket scope kind");
                }
                if (selectedBusiness) {
                    if (event.platform() == null || !List.of("telegram", "vk", "max").contains(event.platform())
                            || !Objects.equals(channel.getPlatform(), event.platform())) {
                        throw new IllegalStateException("Business questionnaire channel platform mismatch");
                    }
                    // V1 stays available without a shared GENERAL reservation; a V2 hash commits the full mixed catalog.
                    boolean generalReserved = sharedQuestionnaires.findBinding(event.channelId())
                        .filter(com.example.panel.service.SharedGeneralQuestionnaireService.Binding::active).isPresent();
                    var legacy = generalReserved ? java.util.Optional.<BusinessQuestionTemplateRegistryService.SelectedRuntime>empty()
                        : businessTemplates.select(event.channelId(), event.businessId(), event.catalogSha256());
                    if (legacy.isPresent()) {
                        if (!Objects.equals(legacy.get().questionTemplateId(), event.questionTemplateId())
                                || !Objects.equals(legacy.get().templateSha256(), event.templateSha256()))
                            throw new IllegalStateException("Business questionnaire version mismatch; restart questionnaire");
                    } else requireSharedSelection(event);
                    businessId = event.businessId();
                } else if (event.scopeSelectionSource() != null) {
                    throw new IllegalStateException("Unexpected business scope selection source");
                } else {
                    businessId = businessTicketWriteScopeResolver.requireSingleBusinessBinding(
                        event.channelId(), event.businessId());
                }
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
        ticket.setQuestionTemplateId(event.questionTemplateId());
        ticket.setQuestionTemplateSha256(event.templateSha256());
        ticket.setBusinessSelectionSource("BUSINESS".equals(event.scopeKind())
            && "USER_SELECTION".equals(event.scopeSelectionSource()) ? "USER_SELECTION" : null);
        ticket.setQuestionnaireCatalogSha256(event.catalogSha256());
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticket.setLastReopenAt(occurredAt);
        return ticket;
    }

    private void requireSelectedQuestionnaireShape(ConversationTicketCreatedEvent event) {
        if (("BUSINESS".equals(event.scopeKind()) && (event.businessId() == null || event.businessId() <= 0))
                || ("GENERAL".equals(event.scopeKind()) && event.businessId() != null)
                || event.questionTemplateId() == null || event.questionTemplateId().isBlank()
                || !event.questionTemplateId().equals(event.questionTemplateId().strip())
                || event.templateSha256() == null || !event.templateSha256().matches("[a-f0-9]{64}")
                || event.catalogSha256() == null || !event.catalogSha256().matches("[a-f0-9]{64}")) {
            throw new IllegalStateException("Incomplete selected business questionnaire");
        }
    }

    private void requireSharedSelection(ConversationTicketCreatedEvent event) {
        if (event.platform() == null || !List.of("telegram", "vk", "max").contains(event.platform())
                || !Objects.equals(channelRepository.findById(event.channelId()).orElseThrow().getPlatform(), event.platform()))
            throw new IllegalStateException("Shared questionnaire channel platform mismatch");
        var selected = sharedQuestionnaires.select(event.channelId(), event.scopeKind(), event.businessId(), event.catalogSha256())
            .or(()->sharedQuestionnaires.selectWithResources(event.channelId(),event.scopeKind(),event.businessId(),event.catalogSha256()))
            .or(()->sharedQuestionnaires.selectExpanded(event.channelId(),event.scopeKind(),event.businessId(),event.catalogSha256()))
            .orElseThrow(() -> new IllegalStateException("Shared questionnaire selection is stale; restart questionnaire"));
        if (!Objects.equals(selected.questionTemplateId(), event.questionTemplateId())
                || !Objects.equals(selected.templateSha256(), event.templateSha256()))
            throw new IllegalStateException("Shared questionnaire version mismatch; restart questionnaire");
        if(selected.version()>=3) QuestionnaireResourceAnswerValidator.requireValid(selected,event);
    }

    private void requireMatchingGeneralQuestionnaire(ConversationTicketCreatedEvent event) {
        if (!multibusinessFeatureFlags.isGeneralChannelResolution()) {
            throw new IllegalStateException("General channel questionnaire is not verified");
        }
        GeneralChannelQuestionTemplateService.RuntimeConfig runtime = generalChannelQuestionTemplateService
            .resolve(event.channelId())
            .orElseThrow(() -> new IllegalStateException("General channel questionnaire is not verified"));
        if (!Objects.equals(runtime.questionTemplateId(), event.questionTemplateId())
            || !Objects.equals(runtime.templateSha256(), event.templateSha256())) {
            throw new IllegalStateException("General channel questionnaire version mismatch; restart questionnaire");
        }
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

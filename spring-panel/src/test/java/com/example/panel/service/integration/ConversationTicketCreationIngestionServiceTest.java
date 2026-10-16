package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.TicketActive;
import com.example.panel.entity.TicketSpan;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.TicketSpanRepository;
import com.example.panel.service.ChatAttachmentMetadataService;
import com.example.panel.service.GeneralChannelQuestionTemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
        LegacyTicketIdConcurrencyGuard concurrencyGuard = mock(LegacyTicketIdConcurrencyGuard.class);
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
            concurrencyGuard,
            channelRepository,
            messageRepository,
            ticketRepository,
            ticketSpanRepository,
            ticketActiveRepository,
            chatHistoryRepository,
            attachmentMetadataService,
            jdbcTemplate,
            disabledFlags(),
            mock(BusinessTicketWriteScopeResolver.class),
            mock(GeneralChannelQuestionTemplateService.class)
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
            )),
            51L
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
        when(ticketRepository.existsByIdTicketId("T-RABBIT-1")).thenReturn(false);
        when(chatHistoryRepository.save(any(ChatHistory.class))).thenAnswer(invocation -> {
            ChatHistory history = invocation.getArgument(0);
            history.setId(801L);
            return history;
        });

        service.ingest(event, "integration.ticket.telegram");

        TicketId expectedLocator = new TicketId();
        expectedLocator.setUserId(901L);
        expectedLocator.setTicketId("T-RABBIT-1");
        verify(concurrencyGuard).acquire("T-RABBIT-1");
        verify(ticketRepository).existsById(expectedLocator);
        verify(ticketRepository).existsByIdTicketId("T-RABBIT-1");

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
        assertThat(ticket.getBusinessId()).isNull();

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
        assertThat(history.getTicketOwnerUserId()).isEqualTo(901L);
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
    void ingestMarksExistingCompositeTicketAsIdempotentWithoutWritingRoot() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        ConversationTicketCreationIngestionService service = new ConversationTicketCreationIngestionService(
            inboxService, mock(LegacyTicketIdConcurrencyGuard.class), channelRepository, messageRepository, ticketRepository,
            mock(TicketSpanRepository.class), mock(TicketActiveRepository.class),
            mock(ChatHistoryRepository.class), mock(ChatAttachmentMetadataService.class),
            mock(JdbcTemplate.class), disabledFlags(), mock(BusinessTicketWriteScopeResolver.class),
            mock(GeneralChannelQuestionTemplateService.class)
        );
        ConversationTicketCreatedEvent event = minimalEvent("evt-same-owner", 901L, "T-RABBIT-SAME");
        Channel channel = new Channel();
        channel.setId(25L);
        when(inboxService.beginProcessing(
            eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt())
        )).thenReturn(true);
        when(channelRepository.findById(25L)).thenReturn(Optional.of(channel));
        TicketId canonicalLocator = new TicketId();
        canonicalLocator.setUserId(901L);
        canonicalLocator.setTicketId("T-RABBIT-SAME");
        when(ticketRepository.existsById(canonicalLocator)).thenReturn(true);

        service.ingest(event, "integration.ticket.telegram");

        verify(ticketRepository).existsById(canonicalLocator);
        verify(ticketRepository, never()).existsByIdTicketId(any());
        verify(messageRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
        verify(inboxService).markProcessed("evt-same-owner");
    }

    @Test
    void ingestRejectsCrossUserLegacyTicketIdCollisionBeforeWritingRoot() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        ConversationTicketCreationIngestionService service = new ConversationTicketCreationIngestionService(
            inboxService, mock(LegacyTicketIdConcurrencyGuard.class), channelRepository, messageRepository, ticketRepository,
            mock(TicketSpanRepository.class), mock(TicketActiveRepository.class),
            mock(ChatHistoryRepository.class), mock(ChatAttachmentMetadataService.class),
            mock(JdbcTemplate.class), disabledFlags(), mock(BusinessTicketWriteScopeResolver.class),
            mock(GeneralChannelQuestionTemplateService.class)
        );
        ConversationTicketCreatedEvent event = minimalEvent("evt-other-owner", 902L, "T-RABBIT-COLLISION");
        Channel channel = new Channel();
        channel.setId(25L);
        when(inboxService.beginProcessing(
            eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt())
        )).thenReturn(true);
        when(channelRepository.findById(25L)).thenReturn(Optional.of(channel));
        when(ticketRepository.existsByIdTicketId("T-RABBIT-COLLISION")).thenReturn(true);

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Legacy ticket ID collision");

        TicketId canonicalLocator = new TicketId();
        canonicalLocator.setUserId(902L);
        canonicalLocator.setTicketId("T-RABBIT-COLLISION");
        verify(ticketRepository).existsById(canonicalLocator);
        verify(ticketRepository).existsByIdTicketId("T-RABBIT-COLLISION");
        verify(messageRepository, never()).save(any());
        verify(ticketRepository, never()).save(any());
        verify(inboxService).markFailed(eq("evt-other-owner"), any(Exception.class));
        verify(inboxService, never()).markProcessed(any());
    }

    @Test
    void ingestSkipsDuplicateInboxEvent() {
        IntegrationInboundEventInboxService inboxService = mock(IntegrationInboundEventInboxService.class);
        ConversationTicketCreationIngestionService service = new ConversationTicketCreationIngestionService(
            inboxService,
            mock(LegacyTicketIdConcurrencyGuard.class),
            mock(ChannelRepository.class),
            mock(MessageRepository.class),
            mock(TicketRepository.class),
            mock(TicketSpanRepository.class),
            mock(TicketActiveRepository.class),
            mock(ChatHistoryRepository.class),
            mock(ChatAttachmentMetadataService.class),
            mock(JdbcTemplate.class),
            disabledFlags(),
            mock(BusinessTicketWriteScopeResolver.class),
            mock(GeneralChannelQuestionTemplateService.class)
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

    @Test
    void strictWritePersistsVerifiedExplicitOwner() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        BusinessTicketWriteScopeResolver resolver = mock(BusinessTicketWriteScopeResolver.class);
        MultibusinessFeatureFlags flags = strictFlags();
        ConversationTicketCreatedEvent event = eventWithBusinessId(51L);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets, flags, resolver);
        Channel channel = new Channel();
        channel.setId(25L);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(resolver.requireSingleBusinessBinding(25L, 51L)).thenReturn(51L);

        service.ingest(event, "integration.ticket.telegram");

        verify(resolver).requireSingleBusinessBinding(25L, 51L);
        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getBusinessId()).isEqualTo(51L);
        verify(messages).save(any(Message.class));
        verify(inbox).markProcessed(event.eventId());
    }

    @Test
    void strictWriteRejectsMissingOwnerBeforeRootWrite() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        BusinessTicketWriteScopeResolver resolver = mock(BusinessTicketWriteScopeResolver.class);
        ConversationTicketCreatedEvent event = minimalEvent("evt-no-business", 901L, "T-NO-BUSINESS");
        ConversationTicketCreationIngestionService service = service(
            inbox, channels, messages, tickets, strictFlags(), resolver);
        Channel channel = new Channel();
        channel.setId(25L);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(resolver.requireSingleBusinessBinding(25L, null))
            .thenThrow(new IllegalStateException("Ticket creation requires an explicit business ID and channel"));

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("explicit business ID");
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
        verify(inbox).markFailed(eq(event.eventId()), any(Exception.class));
    }

    @Test
    void strictWritePersistsExplicitGeneralScopeWithoutBusiness() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        BusinessTicketWriteScopeResolver resolver = mock(BusinessTicketWriteScopeResolver.class);
        ConversationTicketCreatedEvent event = generalEvent("USER_SELECTION", null);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            strictFlags(), resolver);
        Channel channel = new Channel();
        channel.setId(25L);
        channel.setActive(true);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));

        service.ingest(event, "integration.ticket.telegram");

        ArgumentCaptor<Ticket> captured = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(captured.capture());
        assertThat(captured.getValue().getBusinessId()).isNull();
        assertThat(captured.getValue().isGeneral()).isTrue();
        assertThat(captured.getValue().getGeneralSelectionSource()).isEqualTo("USER_SELECTION");
        ArgumentCaptor<Message> root = ArgumentCaptor.forClass(Message.class);
        verify(messages).save(root.capture());
        assertThat(root.getValue().getBusiness()).isNull();
        verify(resolver, never()).requireSingleBusinessBinding(any(), any());
        verify(inbox).markProcessed(event.eventId());
    }

    @Test
    void strictWriteRejectsGeneralWithoutExplicitSelectionOrWithBusinessId() {
        for (ConversationTicketCreatedEvent event : List.of(
            generalEvent(null, null), generalEvent("USER_SELECTION", 51L))) {
            IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
            ChannelRepository channels = mock(ChannelRepository.class);
            MessageRepository messages = mock(MessageRepository.class);
            TicketRepository tickets = mock(TicketRepository.class);
            ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
                strictFlags(), mock(BusinessTicketWriteScopeResolver.class));
            Channel channel = new Channel();
            channel.setId(25L);
            channel.setActive(true);
            when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
                eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
                eq(event), eq(event.occurredAt()))).thenReturn(true);
            when(channels.findById(25L)).thenReturn(Optional.of(channel));

            assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("verified scope");
            verify(messages, never()).save(any());
            verify(tickets, never()).save(any());
        }
    }

    @Test
    void strictWriteAcceptsConfiguredGeneralOnlyChannel() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        GeneralChannelQuestionTemplateService generalTemplates = mock(GeneralChannelQuestionTemplateService.class);
        MultibusinessFeatureFlags flags = strictFlags();
        flags.setGeneralChannelResolution(true);
        ConversationTicketCreatedEvent event = generalEvent("GENERAL_CHANNEL", null);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            flags, mock(BusinessTicketWriteScopeResolver.class), generalTemplates);
        Channel channel = new Channel();
        channel.setId(25L);
        channel.setActive(true);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(generalTemplates.resolve(25L)).thenReturn(Optional.of(
            new GeneralChannelQuestionTemplateService.RuntimeConfig(25L, "GENERAL", "GENERAL_CHANNEL",
                "q-general", "a".repeat(64), java.util.Map.of(), java.util.Map.of(), java.util.Map.of())));

        service.ingest(event, "integration.ticket.telegram");

        ArgumentCaptor<Ticket> captured = ArgumentCaptor.forClass(Ticket.class);
        verify(tickets).save(captured.capture());
        assertThat(captured.getValue().isGeneral()).isTrue();
        assertThat(captured.getValue().getGeneralSelectionSource()).isEqualTo("GENERAL_CHANNEL");
        verify(generalTemplates).resolve(25L);
        verify(inbox).markProcessed(event.eventId());
    }

    @Test
    void strictWriteRejectsGeneralChannelWithoutFlagOrVerifiedBinding() {
        for (boolean enabled : new boolean[] {false, true}) {
            IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
            ChannelRepository channels = mock(ChannelRepository.class);
            MessageRepository messages = mock(MessageRepository.class);
            TicketRepository tickets = mock(TicketRepository.class);
            GeneralChannelQuestionTemplateService generalTemplates = mock(GeneralChannelQuestionTemplateService.class);
            MultibusinessFeatureFlags flags = strictFlags();
            flags.setGeneralChannelResolution(enabled);
            ConversationTicketCreatedEvent event = generalEvent("GENERAL_CHANNEL", null);
            ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
                flags, mock(BusinessTicketWriteScopeResolver.class), generalTemplates);
            Channel channel = new Channel();
            channel.setId(25L);
            channel.setActive(true);
            when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
                eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
                eq(event), eq(event.occurredAt()))).thenReturn(true);
            when(channels.findById(25L)).thenReturn(Optional.of(channel));

            assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not verified");
            verify(messages, never()).save(any());
            verify(tickets, never()).save(any());
        }
    }

    @Test
    void activeGeneralBindingRejectsStaleLegacyEventEvenBeforeStrictWriteRollout() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        GeneralChannelQuestionTemplateService generalTemplates = mock(GeneralChannelQuestionTemplateService.class);
        ConversationTicketCreatedEvent event = minimalEvent("evt-stale-legacy", 901L, "T-STALE-LEGACY");
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            disabledFlags(), mock(BusinessTicketWriteScopeResolver.class), generalTemplates);
        Channel channel = new Channel();
        channel.setId(25L);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(generalTemplates.findBinding(25L)).thenReturn(Optional.of(
            new GeneralChannelQuestionTemplateService.Binding(25L, "q-general", "a".repeat(64), true)));

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("General-only channel");
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
    }

    @Test
    void strictReplayRejectsGeneralEventAgainstLegacyUnresolvedTicket() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        ConversationTicketCreatedEvent event = generalEvent("USER_SELECTION", null);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            strictFlags(), mock(BusinessTicketWriteScopeResolver.class));
        Channel channel = new Channel();
        channel.setId(25L);
        Ticket existing = new Ticket();
        existing.setChannel(channel);
        TicketId locator = new TicketId();
        locator.setUserId(event.userId());
        locator.setTicketId(event.ticketId());
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(tickets.existsById(locator)).thenReturn(true);
        when(tickets.findById(locator)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("replay business or channel mismatch");
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
    }

    @Test
    void strictWriteCannotRunWithoutFoundation() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        BusinessTicketWriteScopeResolver resolver = mock(BusinessTicketWriteScopeResolver.class);
        MultibusinessFeatureFlags flags = disabledFlags();
        flags.setTicketWriteRequiresBusiness(true);
        ConversationTicketCreatedEvent event = eventWithBusinessId(51L);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets, flags, resolver);
        Channel channel = new Channel();
        channel.setId(25L);
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multibusiness foundation");
        verify(resolver, never()).requireSingleBusinessBinding(any(), any());
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
    }

    @Test
    void strictReplayAcceptsFrozenOwnerWithoutRecheckingCurrentChannelBinding() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        BusinessTicketWriteScopeResolver resolver = mock(BusinessTicketWriteScopeResolver.class);
        ConversationTicketCreatedEvent event = eventWithBusinessId(51L);
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            strictFlags(), resolver);
        Channel channel = new Channel();
        channel.setId(25L);
        Ticket existing = new Ticket();
        existing.setBusinessId(51L);
        existing.setChannel(channel);
        TicketId locator = new TicketId();
        locator.setUserId(event.userId());
        locator.setTicketId(event.ticketId());
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(25L)).thenReturn(Optional.of(channel));
        when(tickets.existsById(locator)).thenReturn(true);
        when(tickets.findById(locator)).thenReturn(Optional.of(existing));

        service.ingest(event, "integration.ticket.telegram");

        verify(resolver, never()).requireSingleBusinessBinding(any(), any());
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
        verify(inbox).markProcessed(event.eventId());
    }

    @Test
    void strictReplayRejectsDifferentBusinessOrMissingOwner() {
        for (Long replayBusinessId : new Long[] {52L, null}) {
            IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
            ChannelRepository channels = mock(ChannelRepository.class);
            MessageRepository messages = mock(MessageRepository.class);
            TicketRepository tickets = mock(TicketRepository.class);
            ConversationTicketCreatedEvent event = eventWithBusinessId(replayBusinessId);
            ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
                strictFlags(), mock(BusinessTicketWriteScopeResolver.class));
            Channel channel = new Channel();
            channel.setId(25L);
            Ticket existing = new Ticket();
            existing.setBusinessId(51L);
            existing.setChannel(channel);
            TicketId locator = new TicketId();
            locator.setUserId(event.userId());
            locator.setTicketId(event.ticketId());
            when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
                eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
                eq(event), eq(event.occurredAt()))).thenReturn(true);
            when(channels.findById(25L)).thenReturn(Optional.of(channel));
            when(tickets.existsById(locator)).thenReturn(true);
            when(tickets.findById(locator)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("replay business or channel mismatch");
            verify(messages, never()).save(any());
            verify(tickets, never()).save(any());
            verify(inbox).markFailed(eq(event.eventId()), any(Exception.class));
            verify(inbox, never()).markProcessed(any());
        }
    }

    @Test
    void strictReplayRejectsDifferentChannel() {
        IntegrationInboundEventInboxService inbox = mock(IntegrationInboundEventInboxService.class);
        ChannelRepository channels = mock(ChannelRepository.class);
        MessageRepository messages = mock(MessageRepository.class);
        TicketRepository tickets = mock(TicketRepository.class);
        ConversationTicketCreatedEvent original = eventWithBusinessId(51L);
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(original.eventId(),
            original.eventKind(), original.platform(), 26L, original.ticketId(), original.userId(),
            original.userIdentity(), original.username(), original.clientName(), original.business(),
            original.locationType(), original.city(), original.locationName(), original.problem(),
            original.occurredAt(), original.attributes(), original.historyEntries(), original.businessId());
        ConversationTicketCreationIngestionService service = service(inbox, channels, messages, tickets,
            strictFlags(), mock(BusinessTicketWriteScopeResolver.class));
        Channel originalChannel = new Channel();
        originalChannel.setId(25L);
        Channel replayChannel = new Channel();
        replayChannel.setId(26L);
        Ticket existing = new Ticket();
        existing.setBusinessId(51L);
        existing.setChannel(originalChannel);
        TicketId locator = new TicketId();
        locator.setUserId(event.userId());
        locator.setTicketId(event.ticketId());
        when(inbox.beginProcessing(eq(event.eventId()), eq(event.eventKind()), eq(event.platform()),
            eq(event.channelId()), eq(event.ticketId()), eq("integration.ticket.telegram"),
            eq(event), eq(event.occurredAt()))).thenReturn(true);
        when(channels.findById(26L)).thenReturn(Optional.of(replayChannel));
        when(tickets.existsById(locator)).thenReturn(true);
        when(tickets.findById(locator)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.ingest(event, "integration.ticket.telegram"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("replay business or channel mismatch");
        verify(messages, never()).save(any());
        verify(tickets, never()).save(any());
        verify(inbox).markFailed(eq(event.eventId()), any(Exception.class));
    }

    @Test
    void legacyEventJsonWithoutCanonicalOwnerStillDeserializes() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ObjectNode oldPayload = (ObjectNode) mapper.valueToTree(minimalEvent("evt-old", 901L, "T-OLD"));
        oldPayload.remove("businessId");
        oldPayload.remove("scopeKind");
        oldPayload.remove("scopeSelectionSource");

        ConversationTicketCreatedEvent restored = mapper.treeToValue(oldPayload, ConversationTicketCreatedEvent.class);

        assertThat(restored.businessId()).isNull();
        assertThat(restored.scopeKind()).isNull();
        assertThat(restored.ticketId()).isEqualTo("T-OLD");
        assertThat(mapper.treeToValue(mapper.valueToTree(eventWithBusinessId(51L)),
            ConversationTicketCreatedEvent.class).businessId()).isEqualTo(51L);
    }

    private ConversationTicketCreationIngestionService service(IntegrationInboundEventInboxService inbox,
                                                               ChannelRepository channels,
                                                               MessageRepository messages,
                                                               TicketRepository tickets,
                                                               MultibusinessFeatureFlags flags,
                                                               BusinessTicketWriteScopeResolver resolver) {
        return service(inbox, channels, messages, tickets, flags, resolver,
            mock(GeneralChannelQuestionTemplateService.class));
    }

    private ConversationTicketCreationIngestionService service(IntegrationInboundEventInboxService inbox,
                                                               ChannelRepository channels,
                                                               MessageRepository messages,
                                                               TicketRepository tickets,
                                                               MultibusinessFeatureFlags flags,
                                                               BusinessTicketWriteScopeResolver resolver,
                                                               GeneralChannelQuestionTemplateService generalTemplates) {
        return new ConversationTicketCreationIngestionService(inbox, mock(LegacyTicketIdConcurrencyGuard.class),
            channels, messages, tickets, mock(TicketSpanRepository.class), mock(TicketActiveRepository.class),
            mock(ChatHistoryRepository.class), mock(ChatAttachmentMetadataService.class),
            mock(JdbcTemplate.class), flags, resolver, generalTemplates);
    }

    private ConversationTicketCreatedEvent eventWithBusinessId(Long businessId) {
        ConversationTicketCreatedEvent event = minimalEvent("evt-business", 901L, "T-BUSINESS");
        return new ConversationTicketCreatedEvent(event.eventId(), event.eventKind(), event.platform(),
            event.channelId(), event.ticketId(), event.userId(), event.userIdentity(), event.username(),
            event.clientName(), event.business(), event.locationType(), event.city(), event.locationName(),
            event.problem(), event.occurredAt(), event.attributes(), event.historyEntries(), businessId);
    }

    private ConversationTicketCreatedEvent generalEvent(String source, Long businessId) {
        ConversationTicketCreatedEvent event = minimalEvent("evt-general", 901L, "T-GENERAL");
        return new ConversationTicketCreatedEvent(event.eventId(), event.eventKind(), event.platform(),
            event.channelId(), event.ticketId(), event.userId(), event.userIdentity(), event.username(),
            event.clientName(), event.business(), event.locationType(), event.city(), event.locationName(),
            event.problem(), event.occurredAt(), event.attributes(), event.historyEntries(), businessId,
            "GENERAL", source);
    }

    private MultibusinessFeatureFlags strictFlags() {
        MultibusinessFeatureFlags flags = disabledFlags();
        flags.getFoundation().setEnabled(true);
        flags.setTicketWriteRequiresBusiness(true);
        return flags;
    }

    private ConversationTicketCreatedEvent minimalEvent(String eventId, long userId, String ticketId) {
        return new ConversationTicketCreatedEvent(
            eventId, "ticket.created.initial_contact", "telegram", 25L,
            ticketId, userId, "user-identity", "username", null,
            "Retail", "store", "Moscow", "Tverskaya", "Need help",
            OffsetDateTime.parse("2026-08-14T11:00:00Z"), List.of(), List.of()
        );
    }

    private MultibusinessFeatureFlags disabledFlags() {
        return new MultibusinessFeatureFlags();
    }
}

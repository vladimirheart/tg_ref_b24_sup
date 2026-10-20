package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** A general scope is explicit; a missing businessId alone remains unresolved. */
public record ConversationTicketCreationCommand(Long userId,
                                                String userIdentity,
                                                String username,
                                                String clientName,
                                                Map<String, String> answers,
                                                List<TicketService.TicketAttributeInput> attributes,
                                                List<ConversationHistoryEntry> historyEntries,
                                                Channel channel,
                                                OffsetDateTime startedAt,
                                                String providerEventKey,
                                                Long businessId,
                                                String scopeKind,
                                                String scopeSelectionSource,
                                                String questionTemplateId,
                                                String templateSha256,
                                                String catalogSha256) {

    public ConversationTicketCreationCommand(Long userId, String userIdentity, String username,
            String clientName, Map<String, String> answers, List<TicketService.TicketAttributeInput> attributes,
            List<ConversationHistoryEntry> historyEntries, Channel channel, OffsetDateTime startedAt,
            String providerEventKey, Long businessId, String scopeKind, String scopeSelectionSource,
            String questionTemplateId, String templateSha256) {
        this(userId, userIdentity, username, clientName, answers, attributes, historyEntries, channel,
            startedAt, providerEventKey, businessId, scopeKind, scopeSelectionSource, questionTemplateId, templateSha256, null);
    }

    public ConversationTicketCreationCommand(Long userId, String userIdentity, String username,
                                             String clientName, Map<String, String> answers,
                                             List<TicketService.TicketAttributeInput> attributes,
                                             List<ConversationHistoryEntry> historyEntries, Channel channel,
                                             OffsetDateTime startedAt, String providerEventKey, Long businessId,
                                             String scopeKind, String scopeSelectionSource) {
        this(userId, userIdentity, username, clientName, answers, attributes, historyEntries,
            channel, startedAt, providerEventKey, businessId, scopeKind, scopeSelectionSource, null, null);
    }

    public ConversationTicketCreationCommand(Long userId, String userIdentity, String username,
                                             String clientName, Map<String, String> answers,
                                             List<TicketService.TicketAttributeInput> attributes,
                                             List<ConversationHistoryEntry> historyEntries, Channel channel,
                                             OffsetDateTime startedAt, String providerEventKey, Long businessId) {
        this(userId, userIdentity, username, clientName, answers, attributes, historyEntries,
            channel, startedAt, providerEventKey, businessId, null, null);
    }

    public ConversationTicketCreationCommand(Long userId, String userIdentity, String username,
                                             String clientName, Map<String, String> answers,
                                             List<TicketService.TicketAttributeInput> attributes,
                                             List<ConversationHistoryEntry> historyEntries, Channel channel,
                                             OffsetDateTime startedAt, String providerEventKey) {
        this(userId, userIdentity, username, clientName, answers, attributes, historyEntries,
            channel, startedAt, providerEventKey, null, null, null);
    }

    public ConversationTicketCreationCommand(Long userId,
                                             String userIdentity,
                                             String username,
                                             String clientName,
                                             Map<String, String> answers,
                                             List<TicketService.TicketAttributeInput> attributes,
                                             List<ConversationHistoryEntry> historyEntries,
                                             Channel channel,
                                             OffsetDateTime startedAt) {
        this(userId, userIdentity, username, clientName, answers, attributes, historyEntries,
            channel, startedAt, null, null, null, null);
    }
}

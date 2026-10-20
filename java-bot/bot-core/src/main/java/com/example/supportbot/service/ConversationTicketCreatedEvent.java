package com.example.supportbot.service;

import java.time.OffsetDateTime;
import java.util.List;

/** business is legacy display text; GENERAL is distinct from an unresolved owner. */
public record ConversationTicketCreatedEvent(String eventId,
                                             String eventKind,
                                             String platform,
                                             Long channelId,
                                             String ticketId,
                                             Long userId,
                                             String userIdentity,
                                             String username,
                                             String clientName,
                                             String business,
                                             String locationType,
                                             String city,
                                             String locationName,
                                             String problem,
                                             OffsetDateTime occurredAt,
                                             List<TicketAttributePayload> attributes,
                                             List<ConversationHistoryEntry> historyEntries,
                                             Long businessId,
                                             String scopeKind,
                                             String scopeSelectionSource,
                                             String questionTemplateId,
                                             String templateSha256,
                                             String catalogSha256) {

    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
            Long channelId, String ticketId, Long userId, String userIdentity, String username,
            String clientName, String business, String locationType, String city, String locationName,
            String problem, OffsetDateTime occurredAt, List<TicketAttributePayload> attributes,
            List<ConversationHistoryEntry> historyEntries, Long businessId, String scopeKind,
            String scopeSelectionSource, String questionTemplateId, String templateSha256) {
        this(eventId, eventKind, platform, channelId, ticketId, userId, userIdentity, username,
            clientName, business, locationType, city, locationName, problem, occurredAt, attributes,
            historyEntries, businessId, scopeKind, scopeSelectionSource, questionTemplateId, templateSha256, null);
    }

    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
                                          Long channelId, String ticketId, Long userId,
                                          String userIdentity, String username, String clientName,
                                          String business, String locationType, String city,
                                          String locationName, String problem, OffsetDateTime occurredAt,
                                          List<TicketAttributePayload> attributes,
                                          List<ConversationHistoryEntry> historyEntries, Long businessId,
                                          String scopeKind, String scopeSelectionSource) {
        this(eventId, eventKind, platform, channelId, ticketId, userId, userIdentity,
            username, clientName, business, locationType, city, locationName, problem,
            occurredAt, attributes, historyEntries, businessId, scopeKind, scopeSelectionSource, null, null);
    }

    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
                                          Long channelId, String ticketId, Long userId,
                                          String userIdentity, String username, String clientName,
                                          String business, String locationType, String city,
                                          String locationName, String problem, OffsetDateTime occurredAt,
                                          List<TicketAttributePayload> attributes,
                                          List<ConversationHistoryEntry> historyEntries, Long businessId) {
        this(eventId, eventKind, platform, channelId, ticketId, userId, userIdentity,
            username, clientName, business, locationType, city, locationName, problem,
            occurredAt, attributes, historyEntries, businessId, null, null);
    }

    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
                                          Long channelId, String ticketId, Long userId,
                                          String userIdentity, String username, String clientName,
                                          String business, String locationType, String city,
                                          String locationName, String problem, OffsetDateTime occurredAt,
                                          List<TicketAttributePayload> attributes,
                                          List<ConversationHistoryEntry> historyEntries) {
        this(eventId, eventKind, platform, channelId, ticketId, userId, userIdentity,
            username, clientName, business, locationType, city, locationName, problem,
            occurredAt, attributes, historyEntries, null, null, null);
    }

    public record TicketAttributePayload(String questionId,
                                         String attributeKey,
                                         String questionText,
                                         String inputType,
                                         String valueId,
                                         String valueLabel,
                                         String valueText,
                                         boolean includeInDashboard) {
    }
}

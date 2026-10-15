package com.example.supportbot.service;

import java.time.OffsetDateTime;
import java.util.List;

/** business is legacy display text; businessId is the optional canonical owner. */
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
                                             Long businessId) {

    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
                                          Long channelId, String ticketId, Long userId,
                                          String userIdentity, String username, String clientName,
                                          String business, String locationType, String city,
                                          String locationName, String problem, OffsetDateTime occurredAt,
                                          List<TicketAttributePayload> attributes,
                                          List<ConversationHistoryEntry> historyEntries) {
        this(eventId, eventKind, platform, channelId, ticketId, userId, userIdentity,
            username, clientName, business, locationType, city, locationName, problem,
            occurredAt, attributes, historyEntries, null);
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

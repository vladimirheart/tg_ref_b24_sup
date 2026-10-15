package com.example.panel.service.integration;

import java.time.OffsetDateTime;
import java.util.List;

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
                                             List<ConversationHistoryEntryPayload> historyEntries,
                                             Long businessId) {

    /** Existing producers omit the canonical owner until they can supply a verified ID. */
    public ConversationTicketCreatedEvent(String eventId, String eventKind, String platform,
                                          Long channelId, String ticketId, Long userId,
                                          String userIdentity, String username, String clientName,
                                          String business, String locationType, String city,
                                          String locationName, String problem, OffsetDateTime occurredAt,
                                          List<TicketAttributePayload> attributes,
                                          List<ConversationHistoryEntryPayload> historyEntries) {
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

    public record ConversationHistoryEntryPayload(Long userId,
                                                  String text,
                                                  String messageType,
                                                  String attachmentPath,
                                                  String attachmentName,
                                                  String providerMessageId,
                                                  OffsetDateTime occurredAt) {
    }
}

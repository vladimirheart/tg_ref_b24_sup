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
                                             List<ConversationHistoryEntryPayload> historyEntries) {

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

package com.example.supportbot.service;

import java.time.OffsetDateTime;

public record ConversationHistoryEntry(Long userId,
                                       String text,
                                       String messageType,
                                       String attachmentPath,
                                       String attachmentName,
                                       String providerMessageId,
                                       OffsetDateTime occurredAt) {
}

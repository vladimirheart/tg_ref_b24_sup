package com.example.supportbot.service;

import java.time.OffsetDateTime;

public record ConversationHistoryEntry(Long userId,
                                       String text,
                                       String messageType,
                                       String attachmentPath,
                                       String attachmentName,
                                       String providerMessageId,
                                       OffsetDateTime occurredAt,
                                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) MessageSourceLink sourceLink) {
    public ConversationHistoryEntry(Long userId,String text,String messageType,String attachmentPath,String attachmentName,String providerMessageId,OffsetDateTime occurredAt) {
        this(userId,text,messageType,attachmentPath,attachmentName,providerMessageId,occurredAt,null);
    }
}

package com.example.panel.model.dialog;

public record ChatMessageDto(String sender,
                             String message,
                             String originalMessage,
                             String timestamp,
                             String messageType,
                             String attachment,
                             String attachmentName,
                             Long attachmentSize,
                             String attachmentStatus,
                             String attachmentProvider,
                             String attachmentNote,
                             Long telegramMessageId,
                             Long replyToTelegramMessageId,
                             String replyPreview,
                             String editedAt,
                             String deletedAt,
                             String forwardedFrom) {
}

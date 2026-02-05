package com.example.panel.model.dialog;

public record ChatMessageDto(String sender,
                             String message,
                             String originalMessage,
                             String timestamp,
                             String messageType,
                             String attachment,
                             Long telegramMessageId,
                             Long replyToTelegramMessageId,
                             String replyPreview,
                             String editedAt,
                             String deletedAt,
                             String forwardedFrom) {
}

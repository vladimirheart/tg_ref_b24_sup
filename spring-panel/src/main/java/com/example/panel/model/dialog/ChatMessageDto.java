package com.example.panel.model.dialog;

public record ChatMessageDto(String sender,
                             String message,
                             String timestamp,
                             String messageType,
                             String attachment,
                             Long telegramMessageId,
                             Long replyToTelegramMessageId,
                             String replyPreview) {
}
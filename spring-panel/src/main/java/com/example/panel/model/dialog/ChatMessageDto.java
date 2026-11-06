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
                             String forwardedFrom,
                             @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) com.example.panel.service.integration.MessageSourceLink sourceLink) {
    public ChatMessageDto(String sender,String message,String originalMessage,String timestamp,String messageType,String attachment,String attachmentName,Long attachmentSize,
            String attachmentStatus,String attachmentProvider,String attachmentNote,Long telegramMessageId,Long replyToTelegramMessageId,String replyPreview,String editedAt,String deletedAt,String forwardedFrom) {
        this(sender,message,originalMessage,timestamp,messageType,attachment,attachmentName,attachmentSize,attachmentStatus,attachmentProvider,attachmentNote,telegramMessageId,replyToTelegramMessageId,replyPreview,editedAt,deletedAt,forwardedFrom,null);
    }
}

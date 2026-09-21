package com.example.supportbot.telegram;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

final class TelegramDeliveryIdentitySupport {

    private TelegramDeliveryIdentitySupport() {
    }

    static String buildDeliveryKey(Update update) {
        if (update == null) {
            return buildDeliveryKey((Integer) null);
        }
        if (update.getUpdateId() != null) {
            return buildDeliveryKey(update.getUpdateId());
        }
        Message message = update.getMessage() != null
            ? update.getMessage()
            : (update.getEditedMessage() != null ? update.getEditedMessage() : update.getChannelPost());
        String messageKey = buildMessageKey(message);
        if (messageKey != null) {
            return messageKey;
        }
        return "payload:" + UUID.nameUUIDFromBytes(update.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String buildDeliveryKey(Integer updateId) {
        return updateId != null ? "update:" + updateId : "update:missing";
    }

    static String buildMessageKey(Message message) {
        if (message == null) {
            return null;
        }
        return buildMessageKey(message.getChatId(), message.getMessageId());
    }

    static String buildMessageKey(Long chatId, Integer providerMessageId) {
        if (providerMessageId == null) {
            return null;
        }
        return "message:" + String.valueOf(chatId) + ":" + providerMessageId;
    }
}

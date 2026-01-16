package com.example.supportbot.telegram;

import com.example.supportbot.service.OutboundMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TelegramOutboundMessenger implements OutboundMessenger {

    private static final Logger log = LoggerFactory.getLogger(TelegramOutboundMessenger.class);

    private final SupportBot supportBot;

    public TelegramOutboundMessenger(SupportBot supportBot) {
        this.supportBot = supportBot;
    }

    @Override
    public String platform() {
        return "telegram";
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        if (userId == null) {
            return false;
        }
        return supportBot.sendDirectMessage(userId, text);
    }

    @Override
    public boolean sendToSupportChat(String supportChatId, String text) {
        if (supportChatId == null || supportChatId.isBlank()) {
            return false;
        }
        try {
            return supportBot.sendDirectMessage(Long.parseLong(supportChatId), text);
        } catch (NumberFormatException e) {
            log.warn("Support chat id {} is not a valid Telegram chat id", supportChatId);
            return false;
        }
    }
}
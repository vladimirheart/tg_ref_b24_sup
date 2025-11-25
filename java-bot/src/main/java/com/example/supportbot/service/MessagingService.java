package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.telegram.SupportBot;
import com.example.supportbot.vk.VkSupportBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final SupportBot supportBot;
    private final VkSupportBot vkSupportBot;

    public MessagingService(SupportBot supportBot, VkSupportBot vkSupportBot) {
        this.supportBot = supportBot;
        this.vkSupportBot = vkSupportBot;
    }

    public boolean sendToUser(Channel channel, Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return false;
        }
        if (isVk(channel)) {
            if (userId > Integer.MAX_VALUE) {
                log.warn("User id {} is too large for VK peer", userId);
                return false;
            }
            return vkSupportBot.sendDirectMessage(userId.intValue(), text);
        }
        return supportBot.sendDirectMessage(userId, text);
    }

    public boolean sendToSupportChat(Channel channel, String text) {
        if (channel == null || text == null || text.isBlank()) {
            return false;
        }
        String supportChatId = channel.getSupportChatId();
        if (supportChatId == null || supportChatId.isBlank()) {
            return false;
        }
        if (isVk(channel)) {
            try {
                return vkSupportBot.sendDirectMessage(Integer.parseInt(supportChatId), text);
            } catch (NumberFormatException e) {
                log.warn("Support chat id {} is not a valid VK peer id", supportChatId);
                return false;
            }
        }
        try {
            return supportBot.sendDirectMessage(Long.parseLong(supportChatId), text);
        } catch (NumberFormatException e) {
            log.warn("Support chat id {} is not a valid Telegram chat id", supportChatId);
            return false;
        }
    }

    private boolean isVk(Channel channel) {
        return channel != null && "vk".equalsIgnoreCase(channel.getPlatform());
    }
}

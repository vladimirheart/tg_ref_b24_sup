package com.example.supportbot.vk;

import com.example.supportbot.service.OutboundMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VkOutboundMessenger implements OutboundMessenger {

    private static final Logger log = LoggerFactory.getLogger(VkOutboundMessenger.class);

    private final VkSupportBot vkSupportBot;

    public VkOutboundMessenger(VkSupportBot vkSupportBot) {
        this.vkSupportBot = vkSupportBot;
    }

    @Override
    public String platform() {
        return "vk";
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        if (userId == null) {
            return false;
        }
        if (userId > Integer.MAX_VALUE) {
            log.warn("User id {} is too large for VK peer", userId);
            return false;
        }
        return vkSupportBot.sendDirectMessage(userId.intValue(), text);
    }

    @Override
    public boolean sendToSupportChat(String supportChatId, String text) {
        if (supportChatId == null || supportChatId.isBlank()) {
            return false;
        }
        try {
            return vkSupportBot.sendDirectMessage(Integer.parseInt(supportChatId), text);
        } catch (NumberFormatException e) {
            log.warn("Support chat id {} is not a valid VK peer id", supportChatId);
            return false;
        }
    }
}

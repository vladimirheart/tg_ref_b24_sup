package com.example.supportbot.vk;

import com.example.supportbot.service.OutboundMessenger;
import com.example.supportbot.service.OutboundSenderIdentity;
import com.example.supportbot.service.BotRuntimeSenderIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class VkOutboundMessenger implements OutboundMessenger {

    private static final Logger log = LoggerFactory.getLogger(VkOutboundMessenger.class);

    private final VkSupportBot vkSupportBot;
    private final BotRuntimeSenderIdentity runtimeIdentity;

    public VkOutboundMessenger(VkSupportBot vkSupportBot, BotRuntimeSenderIdentity runtimeIdentity) {
        this.vkSupportBot = vkSupportBot;
        this.runtimeIdentity = runtimeIdentity;
    }

    @Override public OutboundSenderIdentity senderIdentity() {
        return vkSupportBot.outboundSenderIdentity(runtimeIdentity);
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

    @Override public com.example.supportbot.service.ProviderSendResult sendToUserWithReceipt(Long userId,String text) {
        if(userId==null || userId<=0 || userId>Integer.MAX_VALUE)return com.example.supportbot.service.ProviderSendResult.unknown();
        return vkSupportBot.sendDirectMessageWithReceipt(userId.intValue(),text);
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

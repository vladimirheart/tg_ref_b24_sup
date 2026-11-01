package com.example.supportbot.max;

import com.example.supportbot.service.OutboundMessenger;
import com.example.supportbot.service.OutboundSenderIdentity;
import com.example.supportbot.service.BotRuntimeSenderIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MaxOutboundMessenger implements OutboundMessenger {

    private static final Logger log = LoggerFactory.getLogger(MaxOutboundMessenger.class);

    private final MaxApiClient maxApiClient;
    private final BotRuntimeSenderIdentity runtimeIdentity;

    public MaxOutboundMessenger(MaxApiClient maxApiClient, BotRuntimeSenderIdentity runtimeIdentity) {
        this.maxApiClient = maxApiClient;
        this.runtimeIdentity = runtimeIdentity;
    }

    @Override public OutboundSenderIdentity senderIdentity() {
        return maxApiClient.outboundSenderIdentity(runtimeIdentity);
    }

    @Override
    public String platform() {
        return "max";
    }

    @Override
    public boolean sendToUser(Long userId, String text) {
        return maxApiClient.sendMessageToUser(userId, text);
    }

    @Override
    public boolean sendToSupportChat(String supportChatId, String text) {
        if (supportChatId == null || supportChatId.isBlank()) {
            return false;
        }
        if (!supportChatId.matches("-?\\d+")) {
            log.warn("MAX support chat id '{}' is not a valid signed integer", supportChatId);
            return false;
        }
        return maxApiClient.sendMessageToChat(supportChatId, text);
    }
}

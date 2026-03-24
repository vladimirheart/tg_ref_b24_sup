package com.example.supportbot.max;

import com.example.supportbot.service.OutboundMessenger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MaxOutboundMessenger implements OutboundMessenger {

    private static final Logger log = LoggerFactory.getLogger(MaxOutboundMessenger.class);

    private final MaxApiClient maxApiClient;

    public MaxOutboundMessenger(MaxApiClient maxApiClient) {
        this.maxApiClient = maxApiClient;
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
        if (!supportChatId.matches("\\d+")) {
            log.warn("MAX support chat id '{}' is not numeric", supportChatId);
            return false;
        }
        return maxApiClient.sendMessageToChat(supportChatId, text);
    }
}

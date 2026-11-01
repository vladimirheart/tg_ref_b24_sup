package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final Map<String, OutboundMessenger> messengers;

    public MessagingService(List<OutboundMessenger> messengers) {
        this.messengers = messengers.stream()
                .collect(Collectors.toMap(
                        messenger -> messenger.platform().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (existing, replacement) -> {
                            throw new IllegalStateException("Multiple outbound messengers configured for the same platform.");
                        }));
    }

    public boolean sendToUser(Channel channel, Long userId, String text) {
        if (userId == null || text == null || text.isBlank()) {
            return false;
        }
        OutboundMessenger messenger = resolveMessenger(channel);
        if (messenger == null) {
            log.warn("No outbound messenger registered for platform {}", platform(channel));
            return false;
        }
        return messenger.sendToUser(userId, text);
    }

    public boolean sendToSupportChat(Channel channel, String text) {
        if (channel == null || text == null || text.isBlank()) {
            return false;
        }
        String supportChatId = channel.getSupportChatId();
        if (supportChatId == null || supportChatId.isBlank()) {
            return false;
        }
        OutboundMessenger messenger = resolveMessenger(channel);
        if (messenger == null) {
            log.warn("No outbound messenger registered for platform {}", platform(channel));
            return false;
        }
        return messenger.sendToSupportChat(supportChatId, text);
    }

    private OutboundMessenger resolveMessenger(Channel channel) {
        if (channel == null || channel.getId() == null || channel.getId() <= 0 || !channel.isActive()) {
            return null;
        }
        String platform = platform(channel);
        if (platform == null || platform.isBlank()) {
            return null;
        }
        // A numeric recipient ID is platform-specific; never reinterpret it via a Telegram fallback.
        return messengers.get(platform.toLowerCase(Locale.ROOT));
    }

    private String platform(Channel channel) {
        return channel != null ? channel.getPlatform() : null;
    }
}

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
    private final PanelOutboundSenderAuthorizationClient senderAuthorization;

    public MessagingService(List<OutboundMessenger> messengers, PanelOutboundSenderAuthorizationClient senderAuthorization) {
        this.senderAuthorization = senderAuthorization;
        this.messengers = messengers.stream()
                .collect(Collectors.toMap(
                        messenger -> messenger.platform().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (existing, replacement) -> {
                            throw new IllegalStateException("Multiple outbound messengers configured for the same platform.");
                        }));
    }

    public boolean sendToUser(Channel channel, Long userId, String text) {
        return sendToUser(channel, userId, text, () -> {});
    }

    /** The hook must finish durably before the provider is called; failures never fall through. */
    public boolean sendToUser(Channel channel, Long userId, String text, Runnable beforeProvider) {
        if (userId == null || text == null || text.isBlank()) {
            return false;
        }
        OutboundMessenger messenger = resolveMessenger(channel);
        if (messenger == null) {
            log.warn("No outbound messenger registered for platform {}", platform(channel));
            return false;
        }
        OutboundSenderIdentity identity = authorizeIdentity(channel, messenger);
        if (identity == null) return false;
        beforeProvider.run();
        // The durable handoff performs I/O too: never send through a sender changed while it commits.
        return identity != null && identity.matches(channel, messenger.platform()) && identity.equals(messenger.senderIdentity())
            && messenger.sendToUser(userId, text);
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
        return authorizeSender(channel, messenger) && java.util.Objects.equals(supportChatId, channel.getSupportChatId())
                && messenger.sendToSupportChat(supportChatId, text);
    }

    private boolean authorizeSender(Channel channel, OutboundMessenger messenger) {
        return authorizeIdentity(channel, messenger) != null;
    }

    private OutboundSenderIdentity authorizeIdentity(Channel channel, OutboundMessenger messenger) {
        OutboundSenderIdentity identity = messenger.senderIdentity();
        if (identity == null || !identity.matches(channel, messenger.platform())) return null;
        if (!senderAuthorization.authorize(identity)) return null;
        // Admission performs I/O: reject a local channel rebind or token rotation while awaiting the decision.
        return identity.matches(channel, messenger.platform()) && identity.equals(messenger.senderIdentity()) ? identity : null;
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

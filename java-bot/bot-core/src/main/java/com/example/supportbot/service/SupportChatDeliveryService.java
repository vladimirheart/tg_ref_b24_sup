package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** Pins a recipient before file I/O and rechecks it at every actual SDK handoff. */
@Service
public class SupportChatDeliveryService {
    private final BotRuntimeSenderIdentity runtimeIdentity;
    private final PanelOutboundSenderAuthorizationClient authorization;

    public SupportChatDeliveryService(BotRuntimeSenderIdentity runtimeIdentity,
                                      PanelOutboundSenderAuthorizationClient authorization) {
        this.runtimeIdentity = runtimeIdentity;
        this.authorization = authorization;
    }

    /** No network or provider I/O. A target never follows a rebind or credential rotation. */
    public Target prepare(Channel channel, String platform, Supplier<String> token) {
        var identity = runtimeIdentity.forToken(platform, token.get());
        if (identity == null || !identity.matches(channel, platform)) return null;
        String recipient = channel.getSupportChatId();
        if (!canonicalRecipient(platform, recipient)) return null;
        return new Target(channel, platform, token, identity, recipient);
    }

    private static boolean canonicalRecipient(String platform, String recipient) {
        if (recipient == null) return false;
        try {
            long id = Long.parseLong(recipient);
            return id != 0 && Long.toString(id).equals(recipient)
                && (!"vk".equals(platform) || id > 0 && id <= Integer.MAX_VALUE);
        } catch (NumberFormatException invalid) { return false; }
    }

    @FunctionalInterface
    public interface ProviderAction { void send(String recipient) throws Exception; }

    public final class Target {
        private final Channel channel;
        private final String platform;
        private final Supplier<String> token;
        private final OutboundSenderIdentity identity;
        private final String recipient;

        private Target(Channel channel, String platform, Supplier<String> token,
                       OutboundSenderIdentity identity, String recipient) {
            this.channel = channel; this.platform = platform; this.token = token;
            this.identity = identity; this.recipient = recipient;
        }

        private boolean unchanged() {
            return identity.matches(channel, platform) && recipient.equals(channel.getSupportChatId())
                && identity.equals(runtimeIdentity.forToken(platform, token.get()));
        }

        /** Uncached, signed backend decisions; unavailable APIs fail before SDK/storage I/O. */
        public boolean authorize() {
            return unchanged() && authorization.authorize(identity) && unchanged()
                && authorization.authorizeSupportChat(identity, recipient) && unchanged();
        }

        public boolean send(ProviderAction provider) throws Exception {
            if (!authorize()) return false;
            provider.send(recipient);
            return true;
        }
    }
}

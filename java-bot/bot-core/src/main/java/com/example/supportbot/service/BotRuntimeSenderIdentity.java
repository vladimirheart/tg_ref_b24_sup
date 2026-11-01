package com.example.supportbot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BotRuntimeSenderIdentity {
    private final long channelId;
    private final String platform;
    public BotRuntimeSenderIdentity(@Value("${app.bot-runtime.channel-id:0}") long channelId,
                                    @Value("${app.bot-runtime.platform:}") String platform) {
        this.channelId = channelId; this.platform = platform;
    }
    public OutboundSenderIdentity forToken(String providerPlatform, String token) {
        if (channelId <= 0 || platform == null || !platform.equals(providerPlatform)
            || !("telegram".equals(platform) || "vk".equals(platform) || "max".equals(platform))
            || token == null || token.isBlank() || "YOUR_TELEGRAM_BOT_TOKEN".equals(token)) return null;
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
            return new OutboundSenderIdentity(channelId, platform, fingerprint);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable.", impossible); }
    }
}

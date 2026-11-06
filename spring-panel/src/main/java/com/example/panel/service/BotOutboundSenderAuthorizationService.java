package com.example.panel.service;

import com.example.panel.repository.ChannelRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class BotOutboundSenderAuthorizationService {
    private final ChannelRepository channels;
    private final SharedConfigService config;
    public BotOutboundSenderAuthorizationService(ChannelRepository channels, SharedConfigService config) {
        this.channels = channels; this.config = config;
    }
    public boolean authorize(SenderIdentity identity) {
        if (identity == null || identity.channelId() == null || identity.channelId() <= 0
            || !("telegram".equals(identity.platform()) || "vk".equals(identity.platform()) || "max".equals(identity.platform()))
            || identity.tokenSha256() == null || !identity.tokenSha256().matches("[0-9a-f]{64}")) return false;
        try {
            var channel = channels.findById(identity.channelId()).orElse(null);
            if (channel == null || !identity.channelId().equals(channel.getId()) || !identity.platform().equalsIgnoreCase(channel.getPlatform())) return false;
            var credential = BotChannelCredentialResolver.resolve(channel, config);
            if (credential == null) return false;
            byte[] expected = MessageDigest.getInstance("SHA-256").digest(credential.token().getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(identity.tokenSha256()));
        } catch (RuntimeException | NoSuchAlgorithmException unavailable) { return false; }
    }
    public record SenderIdentity(Long channelId, String platform, String tokenSha256) {
        @Override public String toString() { return "SenderIdentity[channelId=" + channelId + ", platform=" + platform + ", credential=<redacted>]"; }
    }

    public boolean authorizeSupportChat(SenderIdentity identity, String recipient) {
        if (recipient==null || recipient.isBlank() || !authorize(identity)) return false;
        var channel=channels.findById(identity.channelId()).orElse(null);
        return channel!=null && Boolean.TRUE.equals(channel.getActive()) && identity.platform().equals(channel.getPlatform())
            && recipient.equals(channel.getSupportChatId());
    }
}

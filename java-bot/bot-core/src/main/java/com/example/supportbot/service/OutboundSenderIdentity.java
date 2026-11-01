package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;

/** Contains only the credential fingerprint, never the provider token. */
public record OutboundSenderIdentity(Long channelId, String platform, String tokenSha256) {
    public boolean matches(Channel channel, String providerPlatform) {
        return channel != null && channel.isActive() && channelId != null && channelId > 0
            && channelId.equals(channel.getId()) && platform != null
            && (platform.equals("telegram") || platform.equals("vk") || platform.equals("max"))
            && platform.equalsIgnoreCase(providerPlatform) && platform.equalsIgnoreCase(channel.getPlatform())
            && tokenSha256 != null && tokenSha256.matches("[0-9a-f]{64}");
    }
    @Override public String toString() { return "OutboundSenderIdentity[channelId=" + channelId + ", platform=" + platform + ", credential=<redacted>]"; }
}

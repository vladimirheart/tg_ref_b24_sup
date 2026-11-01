package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Shared launcher/admission contract: an explicit broken binding never selects another token. */
public final class BotChannelCredentialResolver {
    private BotChannelCredentialResolver() { }
    public static BotCredential resolve(Channel channel, SharedConfigService config) {
        if (channel == null || channel.getId() == null || channel.getId() <= 0 || !Boolean.TRUE.equals(channel.getActive())) return null;
        String platform = normalize(channel.getPlatform());
        if (!("telegram".equals(platform) || "vk".equals(platform) || "max".equals(platform))) return null;
        if (channel.getCredentialId() == null) {
            String token = channel.getToken();
            return token == null || token.isBlank() ? null : new BotCredential(null, "db:channels#" + channel.getId(), platform, token, true);
        }
        if (channel.getCredentialId() <= 0) return null;
        List<BotCredential> catalog = config.loadBotCredentials();
        if (catalog == null) return null;
        var matches = catalog.stream().filter(Objects::nonNull).filter(item -> Objects.equals(item.id(), channel.getCredentialId())).toList();
        if (matches.size() != 1) return null;
        BotCredential selected = matches.get(0);
        return Boolean.TRUE.equals(selected.active()) && platform.equals(normalize(selected.platform()))
            && selected.token() != null && !selected.token().isBlank() ? selected : null;
    }
    private static String normalize(String platform) { return platform == null ? "" : platform.toLowerCase(Locale.ROOT); }
}

package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.config.BotProperties;
import org.junit.jupiter.api.Test;

class BotPropertiesTest {

    @Test
    void ingressLeaseIdentityUsesTokenFingerprintInsteadOfGroupChat() {
        BotProperties first = new BotProperties();
        first.setToken("telegram-token");
        first.setChannelId(10L);

        BotProperties second = new BotProperties();
        second.setToken("telegram-token");
        second.setChannelId(20L);

        assertThat(first.ingressLeaseIdentity())
            .isEqualTo(second.ingressLeaseIdentity())
            .startsWith("token-sha256:")
            .doesNotContain("telegram-token");
    }
}

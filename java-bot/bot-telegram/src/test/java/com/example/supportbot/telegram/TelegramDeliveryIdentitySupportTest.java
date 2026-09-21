package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramDeliveryIdentitySupportTest {

    @Test
    void updateIdIsCanonicalDeliveryKey() {
        assertThat(TelegramDeliveryIdentitySupport.buildDeliveryKey(12345))
            .isEqualTo("update:12345");
    }

    @Test
    void messageKeyIncludesChatAndProviderMessageId() {
        assertThat(TelegramDeliveryIdentitySupport.buildMessageKey(777L, 9001))
            .isEqualTo("message:777:9001");
    }

    @Test
    void missingProviderMessageIdDoesNotInventMessageIdentity() {
        assertThat(TelegramDeliveryIdentitySupport.buildMessageKey(777L, null)).isNull();
    }
}

package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupportBotTest {

    @Test
    void buildTelegramBotApiBaseUrlAppendsBotSuffixToCustomRoot() {
        assertThat(SupportBot.buildTelegramBotApiBaseUrl("https://telegram.ftl-dev.ru"))
            .isEqualTo("https://telegram.ftl-dev.ru/bot");
    }

    @Test
    void buildTelegramBotApiBaseUrlKeepsSingleBotSuffix() {
        assertThat(SupportBot.buildTelegramBotApiBaseUrl("https://telegram.ftl-dev.ru/bot/"))
            .isEqualTo("https://telegram.ftl-dev.ru/bot");
    }

    @Test
    void buildTelegramBotApiBaseUrlFallsBackToTelegramDefault() {
        assertThat(SupportBot.buildTelegramBotApiBaseUrl(""))
            .isEqualTo("https://api.telegram.org/bot");
    }
}

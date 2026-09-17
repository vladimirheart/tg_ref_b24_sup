package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramApiEndpointSupportTest {

    @Test
    void botApiBaseUrlAppendsBotSuffixToCustomRoot() {
        assertThat(TelegramApiEndpointSupport.botApiBaseUrl("https://telegram.ftl-dev.ru"))
            .isEqualTo("https://telegram.ftl-dev.ru/bot");
    }

    @Test
    void botApiBaseUrlKeepsSingleBotSuffix() {
        assertThat(TelegramApiEndpointSupport.botApiBaseUrl("https://telegram.ftl-dev.ru/bot/"))
            .isEqualTo("https://telegram.ftl-dev.ru/bot");
    }

    @Test
    void botApiBaseUrlFallsBackToTelegramDefault() {
        assertThat(TelegramApiEndpointSupport.botApiBaseUrl(""))
            .isEqualTo("https://api.telegram.org/bot");
    }

    @Test
    void normalizedRootDropsBotSuffixForLogsAndFileDownloads() {
        assertThat(TelegramApiEndpointSupport.normalizeRootUrl(" https://telegram.ftl-dev.ru/bot/ "))
            .isEqualTo("https://telegram.ftl-dev.ru");
    }
}

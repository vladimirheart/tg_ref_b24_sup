package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.supportbot.entity.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SupportNotificationTextTest {
    @Test void usesCanonicalNumberAndSeparatesSource() {
        Channel channel = new Channel(); channel.setChannelName("Тест\nканала");
        String body = SupportNotificationText.newAppealTitle("20261125-003") + "\nОпишите проблему: читай выше";
        assertThat(SupportNotificationText.fromChannel(channel, "telegram", body))
                .isEqualTo("💬 Диалоги · Telegram\nКанал: Тест канала\n──────────\nНовая заявка №20261125-003\nОпишите проблему: читай выше");
        assertThat(SupportNotificationText.fromChannel(channel, "vk", body)).startsWith("💬 Диалоги · ВКонтакте");
        assertThat(SupportNotificationText.fromChannel(channel, "max", body)).startsWith("💬 Диалоги · MAX");
    }
    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings={" ","bb300bf7-2de6-32b2-beb9-e322d581173d","T-42","20261125-3"})
    void unavailableNumberIsExplicitAndNeverLeaksAnId(String value) {
        assertThat(SupportNotificationText.newAppealTitle(value)).isEqualTo("Новая заявка (номер ожидается)");
    }
}

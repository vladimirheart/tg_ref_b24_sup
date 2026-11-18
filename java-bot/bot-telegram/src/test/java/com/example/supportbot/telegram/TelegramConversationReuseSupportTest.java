package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TelegramConversationReuseSupportTest {

    @Test
    void keepsOnlyReusableRequestValuesAndNeverAddsProblemText() {
        Map<String, String> answers = TelegramConversationReuseSupport.answers(
                "СушиВёсла", "Корпоративная сеть", "Адлер", "Голубые дали");

        assertThat(answers).containsExactly(
                Map.entry("business", "СушиВёсла"),
                Map.entry("location_type", "Корпоративная сеть"),
                Map.entry("city", "Адлер"),
                Map.entry("location_name", "Голубые дали"));
        assertThat(answers).doesNotContainKey("problem");
    }

    @Test
    void ignoresBlankValuesSoTheBotDoesNotOfferAnEmptyReusePrompt() {
        assertThat(TelegramConversationReuseSupport.answers(" ", null, "", "\t")).isEmpty();
    }
}

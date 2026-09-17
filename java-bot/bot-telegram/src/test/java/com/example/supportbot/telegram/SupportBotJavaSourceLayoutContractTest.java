package com.example.supportbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SupportBotJavaSourceLayoutContractTest {

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void choiceInputPolicyUsesDedicatedSupportOwner() throws IOException {
        String bot = read("src/main/java/com/example/supportbot/telegram/SupportBot.java");
        String support = read("src/main/java/com/example/supportbot/telegram/TelegramChoiceInputSupport.java");

        assertThat(bot)
                .contains("TelegramChoiceInputSupport.resolveDirectAnswer(rawAnswer, options)")
                .contains("TelegramChoiceInputSupport.normalizeAlias(resolved)")
                .contains("TelegramChoiceInputSupport.matchOptionByValue(options, canonicalBusiness)")
                .contains("TelegramChoiceInputSupport.mediaGuidance(options)")
                .doesNotContain("static String choiceQuestionMediaGuidance(")
                .doesNotContain("static String normalizeAlias(")
                .doesNotContain("private String matchOptionByValue(");

        assertThat(support)
                .contains("final class TelegramChoiceInputSupport")
                .contains("static String resolveDirectAnswer(String rawAnswer, List<String> options)")
                .contains("static String normalizeAlias(String value)")
                .contains("static String matchOptionByValue(List<String> options, String value)")
                .contains("static String mediaGuidance(List<String> options)")
                .doesNotContain("TelegramLongPollingBot")
                .doesNotContain("BotSettingsService")
                .doesNotContain("ConversationSession")
                .doesNotContain("execute(");
    }
}

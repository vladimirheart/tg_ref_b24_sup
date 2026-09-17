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

    @Test
    void apiEndpointPolicyUsesDedicatedSupportOwner() throws IOException {
        String bot = read("src/main/java/com/example/supportbot/telegram/SupportBot.java");
        String support = read("src/main/java/com/example/supportbot/telegram/TelegramApiEndpointSupport.java");

        assertThat(bot)
                .contains("TelegramApiEndpointSupport.botApiBaseUrl(env(\"TELEGRAM_BOT_API_BASE_URL\"))")
                .contains("TelegramApiEndpointSupport.normalizeRootUrl(env(\"TELEGRAM_BOT_API_BASE_URL\"))")
                .doesNotContain("DEFAULT_TELEGRAM_API_ROOT_URL")
                .doesNotContain("static String buildTelegramBotApiBaseUrl(")
                .doesNotContain("static String normalizeTelegramApiRootUrl(");

        assertThat(support)
                .contains("final class TelegramApiEndpointSupport")
                .contains("static String botApiBaseUrl(String rawRootUrl)")
                .contains("static String normalizeRootUrl(String rawRootUrl)")
                .doesNotContain("System.getenv")
                .doesNotContain("DefaultBotOptions")
                .doesNotContain("HttpURLConnection")
                .doesNotContain("TelegramLongPollingBot")
                .doesNotContain("execute(");
    }

    @Test
    void startupFailurePolicyUsesDedicatedSupportOwner() throws IOException {
        String bot = read("src/main/java/com/example/supportbot/telegram/SupportBot.java");
        String lifecycle = read("src/main/java/com/example/supportbot/telegram/TelegramLongPollingLifecycle.java");
        String support = read("src/main/java/com/example/supportbot/telegram/TelegramStartupFailureSupport.java");

        assertThat(bot)
                .contains("String describeStartupFailure(String fallbackMessage, TelegramApiException exception)")
                .contains("return TelegramStartupFailureSupport.describe(")
                .contains("resolveTelegramApiRootUrlForLogs()")
                .doesNotContain("private Throwable rootCauseOf(")
                .doesNotContain("private boolean isConnectivityFailure(")
                .doesNotContain("private boolean isProxyTunnelFailure(");

        assertThat(lifecycle)
                .contains("supportBot.describeStartupFailure(");

        assertThat(support)
                .contains("final class TelegramStartupFailureSupport")
                .contains("static String describe(String fallbackMessage, Throwable exception, String apiRootUrl)")
                .doesNotContain("System.getenv")
                .doesNotContain("DefaultBotOptions")
                .doesNotContain("TelegramLongPollingBot")
                .doesNotContain("execute(")
                .doesNotContain("LoggerFactory");
    }
}

package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsChannelsRuntimeUiSourceContractTest {

    private static final Path CATALOG = Path.of(
        "src/main/resources/static/js/settings-channels-catalog-runtime.js"
    );
    private static final Path BOT_RUNTIME = Path.of(
        "src/main/resources/static/js/settings-channels-bot-runtime.js"
    );
    private static final Path SHELL = Path.of(
        "src/main/resources/static/js/settings-channels-shell-runtime.js"
    );
    private static final Path WORKSPACE = Path.of(
        "src/main/resources/templates/settings/fragments/channels-workspace.html"
    );

    @Test
    void channelRowsExposeStatusAwareStartAndStopThroughBotRuntime() throws IOException {
        String catalog = read(CATALOG);
        String botRuntime = read(BOT_RUNTIME);
        String shell = read(SHELL);

        assertThat(catalog)
            .contains("data-channel-start=\"${prepared.id}\" disabled")
            .contains("data-channel-stop=\"${prepared.id}\" hidden")
            .contains("typeof options.stopBot === 'function'")
            .contains("options.stopBot(channelId);");
        assertThat(botRuntime)
            .contains("function updateChannelRowBotControls(channelId, code)")
            .contains("startBtn.hidden = running;")
            .contains("stopBtn.hidden = !running;")
            .contains("async function stopBot(channelId)")
            .contains("return success;");
        assertThat(shell)
            .contains("stopBot: (...args) => settingsChannelsBotRuntime.stopBot(...args)");
    }

    @Test
    void platformColumnUsesTwoLineSummaryAndExplicitDisclosure() throws IOException {
        String catalog = read(CATALOG);
        String workspace = read(WORKSPACE);

        assertThat(catalog)
            .contains("data-channel-platform-info=\"${prepared.id}\"")
            .contains("data-channel-platform-details=\"${prepared.id}\"")
            .contains("class=\"small text-muted text-truncate mt-1\"")
            .contains("platformHoverText")
            .contains("details.hidden = expanded;");
        assertThat(workspace)
            .contains("<th style=\"width: 34%\">Название для панели</th>")
            .contains("<th style=\"width: 42%\">Платформа</th>")
            .contains("<th style=\"width: 24%\" class=\"text-center\">Статус</th>");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

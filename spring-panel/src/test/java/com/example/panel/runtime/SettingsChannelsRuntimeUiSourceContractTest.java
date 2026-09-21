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
    private static final Path CHANNELS_SCSS = Path.of(
        "src/main/resources/scss/settings/calm/_channels.scss"
    );

    @Test
    void channelRowsExposeStatusAwareStartAndStopThroughBotRuntime() throws IOException {
        String catalog = read(CATALOG);
        String botRuntime = read(BOT_RUNTIME);
        String shell = read(SHELL);

        assertThat(catalog)
            .contains("data-channel-start=\"${prepared.id}\"")
            .contains("aria-label=\"Запустить\" title=\"Запустить\" disabled")
            .contains("data-channel-stop=\"${prepared.id}\"")
            .contains("aria-label=\"Остановить\" title=\"Остановить\" hidden")
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
    void platformColumnStaysModalSafeAndUsesCompactIconControls() throws IOException {
        String catalog = read(CATALOG);
        String workspace = read(WORKSPACE);
        String channelsScss = read(CHANNELS_SCSS);

        assertThat(catalog)
            .contains("data-channel-platform-info=\"${prepared.id}\"")
            .contains("data-channel-platform-details=\"${prepared.id}\"")
            .contains("channels-platform-template-summary")
            .contains("aria-label=\"Показать подробности\"")
            .contains("bi-play-fill")
            .contains("bi-stop-fill")
            .contains("bi-pencil")
            .contains("aria-label=\"Запустить\"")
            .contains("aria-label=\"Остановить\"")
            .contains("aria-label=\"Редактировать\"")
            .contains("infoBtn.setAttribute('aria-label', disclosureLabel);")
            .contains("details.hidden = expanded;");
        assertThat(workspace)
            .contains("table align-middle mb-0 channels-manage-table")
            .contains("<th style=\"width: 34%\">Название для панели</th>")
            .contains("<th style=\"width: 42%\">Платформа</th>")
            .contains("<th style=\"width: 24%\" class=\"text-center\">Статус</th>");
        assertThat(channelsScss)
            .contains("table-layout: fixed;")
            .contains("max-width: 100%;")
            .contains("min-width: 0;")
            .contains("overflow-wrap: anywhere;")
            .contains("#channelsModal .channels-icon-button")
            .contains("width: 1.9rem;")
            .contains("height: 1.9rem;");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

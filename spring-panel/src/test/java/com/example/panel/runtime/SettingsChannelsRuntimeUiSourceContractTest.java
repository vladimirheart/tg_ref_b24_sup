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
    private static final Path NETWORK_WORKSPACE = Path.of(
        "src/main/resources/templates/settings/fragments/channels-integration-network.html"
    );
    private static final Path CHANNELS_SCSS = Path.of(
        "src/main/resources/scss/settings/calm/_channels.scss"
    );
    private static final Path WORKSPACE_SCSS = Path.of(
        "src/main/resources/scss/settings/_workspace.scss"
    );
    private static final Path TEMPLATES_WORKSPACE = Path.of(
        "src/main/resources/templates/settings/fragments/channels-templates.html"
    );
    private static final Path BOT_SETTINGS = Path.of(
        "src/main/resources/static/js/bot-settings.js"
    );
    private static final Path DIALOG_TEMPLATES = Path.of(
        "src/main/resources/static/js/settings-dialog-templates-runtime.js"
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
            .contains("#channelsModal .channels-icon-button");
    }

    @Test
    void channelManagementTabsAndNestedModalScrollStayCompact() throws IOException {
        String catalog = read(CATALOG);
        String workspace = read(WORKSPACE);
        String networkWorkspace = read(NETWORK_WORKSPACE);
        String channelsScss = read(CHANNELS_SCSS);
        String workspaceScss = read(WORKSPACE_SCSS);

        assertThat(workspace)
            .contains("id=\"channelsManageTabs\"")
            .contains("data-bs-target=\"#channels-manage-bots\"")
            .contains("data-bs-target=\"#channels-manage-profiles\"")
            .contains("data-bs-target=\"#channels-manage-routes\"")
            .contains("data-channels-manage-stat=\"channels-count\"")
            .contains("data-channels-manage-stat=\"active-count\"")
            .contains("channels-manage-info")
            .contains("btn btn-sm btn-primary channels-add-button")
            .doesNotContain("channels-quick-grid");
        assertThat(networkWorkspace)
            .contains("id=\"channels-manage-profiles\"")
            .contains("id=\"channels-manage-routes\"")
            .contains("channels-manage-network-pane")
            .doesNotContain("channelsManageAdvancedAccordion")
            .doesNotContain("channelsProfilesCollapse")
            .doesNotContain("channelsRoutesCollapse");
        assertThat(catalog)
            .contains("const channelStateLabel = prepared.is_active ? 'Канал активен' : 'Канал выключен';")
            .contains("channels-channel-state-icon")
            .contains("title=\"${escapeHtml(channelStateLabel)}\"")
            .contains("channels-runtime-control-row")
            .doesNotContain("const statusBadge = prepared.is_active");
        assertThat(channelsScss)
            .contains("#channelsModal .channels-manage-tabs")
            .contains("#channelsModal .channels-runtime-control-row")
            .contains("#channelsModal .channels-channel-state-icon")
            .contains("width: 1.65rem;")
            .contains("height: 1.65rem;");
        assertThat(workspaceScss)
            .contains(".settings-child-modal.show .modal-dialog-scrollable .modal-body")
            .contains("overscroll-behavior-y: contain;")
            .contains(".modal.show[inert] .modal-dialog-scrollable .modal-body")
            .contains("overflow: hidden !important;")
            .contains("overscroll-behavior: none;");
    }


    @Test
    void templateAndAutomationWorkspacesStayCompactAndUseAccessibleInfoAndIconActions() throws IOException {
        String templatesWorkspace = read(TEMPLATES_WORKSPACE);
        String workspace = read(WORKSPACE);
        String botSettings = read(BOT_SETTINGS);
        String dialogTemplates = read(DIALOG_TEMPLATES);
        String channelsScss = read(CHANNELS_SCSS);

        assertThat(templatesWorkspace)
            .contains("id=\"channelsTemplateTabs\"")
            .contains("data-bs-target=\"#channels-question-templates\"")
            .contains("data-bs-target=\"#channels-rating-templates\"")
            .contains("channels-section-info__toggle")
            .contains("data-bot-question-active-diagnostic")
            .contains("data-bot-rating-active-diagnostic")
            .contains("data-bot-legacy-diagnostic")
            .doesNotContain("alert alert-light border small mb-3\" data-bot-question-active-diagnostic")
            .doesNotContain("alert alert-light border small mb-3\" data-bot-rating-active-diagnostic");
        assertThat(workspace)
            .contains("channels-template-workspace")
            .contains("channels-section-info__toggle")
            .contains("data-auto-close-template-add")
            .contains("data-auto-close-templates")
            .doesNotContain("Активный шаблон будет применяться по умолчанию. Позже вы сможете выбирать подходящий сценарий для каждого бота.");
        assertThat(botSettings)
            .contains("channels-template-card")
            .contains("channels-template-action")
            .contains("data-bot-template-edit")
            .contains("data-bot-template-duplicate")
            .contains("data-bot-template-delete")
            .contains("data-bot-rating-template-edit")
            .contains("data-bot-rating-template-duplicate")
            .contains("data-bot-rating-template-delete")
            .contains("bi-pencil")
            .contains("bi-copy")
            .contains("bi-trash3")
            .doesNotContain("data-bot-template-edit>Редактировать</button>")
            .doesNotContain("data-bot-rating-template-edit>Редактировать</button>");
        assertThat(dialogTemplates)
            .contains("card.matches('[data-auto-close-template]')")
            .contains("channels-auto-close-card")
            .contains("channels-template-action")
            .contains("data-dialog-template-toggle")
            .contains("data-auto-close-template-remove")
            .contains("bi-pencil")
            .contains("bi-trash3");
        assertThat(channelsScss)
            .contains("#channelsModal .channels-section-info__panel")
            .contains("#channelsModal .channels-template-card")
            .contains("#channelsModal .channels-template-action")
            .contains("width: 1.65rem;")
            .contains("height: 1.65rem;");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}

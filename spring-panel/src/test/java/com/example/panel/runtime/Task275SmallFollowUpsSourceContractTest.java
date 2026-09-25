package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task275SmallFollowUpsSourceContractTest {

    @Test
    void acceptanceFixesStayCanonical() throws IOException {
        String sidebarScss = read("src/main/resources/scss/sidebar/_sections.scss");
        String sidebarCss = read("src/main/resources/static/css/sidebar.css");
        String settingsTemplate = read("src/main/resources/templates/settings/fragments/channels-workspace.html");
        String channelEditor = read("src/main/resources/templates/settings/fragments/channel-editor.html");
        String settingsRuntime = read("src/main/resources/static/js/settings-save-runtime.js");
        String editorPersistence = read("src/main/resources/static/js/settings-channel-editor-persistence-runtime.js");
        String editorShell = read("src/main/resources/static/js/settings-channel-editor-shell-runtime.js");
        String scheduler = read("src/main/java/com/example/panel/service/DialogAutoCloseSchedulerService.java");
        String followUp = read("src/main/java/com/example/panel/service/DialogAutoCloseFollowUpTaskService.java");
        String clientProfile = read("src/main/java/com/example/panel/controller/ClientProfileApiController.java");
        String taskDoc = read("../ai-context/tasks/task-details/01-275.md");

        assertThat(sidebarScss)
            .contains("padding: 0 10px 8px")
            .contains("01-275 S4 acceptance: remove the redundant outer account frame")
            .contains("min-height: 40px")
            .contains("width: 32px");
        assertThat(sidebarCss)
            .contains("padding: 0 10px 8px")
            .contains("01-275 S4 acceptance: remove the redundant outer account frame")
            .contains("min-height: 40px")
            .contains("width: 32px");

        assertThat(settingsTemplate).doesNotContain("autoCloseFollowUpProject");
        assertThat(settingsRuntime).doesNotContain("follow_up_project_id");
        assertThat(channelEditor)
            .contains("id=\"channelEditorAutoCloseFollowUpProject\"")
            .contains("autoCloseProjectOptions")
            .contains("Настройка относится только к этому боту");
        assertThat(editorPersistence).contains("auto_close_follow_up_project_id");
        assertThat(editorShell).contains("auto_close_follow_up_project_id");
        assertThat(scheduler).contains("createTaskForAutoClosedDialog(ticket.getTicketId(), ticket.getChannel())");
        assertThat(followUp)
            .contains("channel.getDeliverySettings()")
            .contains("auto_close_follow_up_project_id")
            .contains("resolveConfiguredProjectIds");

        assertThat(clientProfile)
            .contains("case \"max\" -> \"MAX\"")
            .contains("аватар синхронизируется по входящему сообщению MAX");
        assertThat(taskDoc).contains("01-275_S4_ACCEPTANCE_FIXES_R9_2026-09-25");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

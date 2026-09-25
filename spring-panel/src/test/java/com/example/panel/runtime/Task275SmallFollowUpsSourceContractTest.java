package com.example.panel.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Task275SmallFollowUpsSourceContractTest {

    @Test
    void sidebarAndAutoCloseProjectRoutingStayCanonical() throws IOException {
        String sidebar = read("src/main/resources/scss/sidebar/_sections.scss");
        String settingsTemplate = read("src/main/resources/templates/settings/fragments/channels-workspace.html");
        String settingsRuntime = read("src/main/resources/static/js/settings-save-runtime.js");
        String settingsController = read("src/main/java/com/example/panel/settings/SettingsPageController.java");
        String normalizer = read("src/main/java/com/example/panel/service/AutoCloseConfigNormalizer.java");
        String scheduler = read("src/main/java/com/example/panel/service/DialogAutoCloseSchedulerService.java");
        String followUp = read("src/main/java/com/example/panel/service/DialogAutoCloseFollowUpTaskService.java");
        String taskService = read("src/main/java/com/example/panel/service/PanelTaskService.java");
        String taskDoc = read("../ai-context/tasks/task-details/01-275.md");

        assertThat(sidebar)
            .contains("padding: 6px 10px 10px")
            .contains("padding: 6px")
            .contains("padding: 4px 5px");

        assertThat(settingsTemplate)
            .contains("id=\"autoCloseFollowUpProject\"")
            .contains("autoCloseProjectOptions")
            .contains("autoCloseFollowUpProjectId");
        assertThat(settingsRuntime).contains("follow_up_project_id");
        assertThat(settingsController)
            .contains("findAllByArchivedAtIsNullOrderByNameAsc")
            .contains("autoCloseFollowUpProjectId");
        assertThat(normalizer)
            .contains("follow_up_project_id")
            .contains("resolveFollowUpProjectId");
        assertThat(scheduler)
            .contains("createTaskForAutoClosedDialog(ticket.getTicketId(), settings)");
        assertThat(followUp)
            .contains("resolveConfiguredProjectIds")
            .contains("projectRepository.findById")
            .contains("payloadProjectIds");
        assertThat(taskService)
            .contains("payload.projectIds()")
            .contains("recordSaved(")
            .contains("List<Long> projectIds");
        assertThat(taskDoc).contains("01-275_S1_SIDEBAR_AUTOCLOSE_PROJECT_R1_2026-09-25");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

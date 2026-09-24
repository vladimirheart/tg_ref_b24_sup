package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskProjectsWorkspaceUiSourceContractTest {

    @Test
    void tasksWorkspaceUsesProjectScopesNormalizedTagsAndSideSheet() throws IOException {
        String template = read("src/main/resources/templates/tasks/index.html");
        String runtime = read("src/main/resources/static/js/tasks.js");
        String styles = read("src/main/resources/scss/app/_tasks.scss");
        String queryService = read("src/main/java/com/example/panel/service/TaskQueryService.java");
        String taskRepository = read("src/main/java/com/example/panel/repository/TaskRepository.java");

        assertThat(template)
            .contains("data-task-scope=\"all\"")
            .contains("data-task-scope=\"mine\"")
            .contains("id=\"taskProjectScope\"")
            .contains("class=\"modal fade task-side-sheet\"")
            .contains("id=\"taskProjectsSelect\"")
            .contains("name=\"tags\"")
            .contains("id=\"taskEvents\"")
            .doesNotContain("name=\"customer\"")
            .doesNotContain("name=\"access_data\"")
            .doesNotContain("Пока заглушка");

        assertThat(runtime)
            .contains("project_id")
            .contains("mine")
            .contains("project_ids")
            .contains("data.append('tags', tags)")
            .contains("renderProjectChips")
            .contains("renderTagChips")
            .contains("renderEvents");

        assertThat(taskRepository).contains("JpaSpecificationExecutor<Task>");
        assertThat(queryService)
            .contains("TaskProjectMembership")
            .contains("TaskTag")
            .contains("TaskPerson")
            .contains("dto.put(\"projects\"")
            .contains("dto.put(\"tags\"");

        assertThat(styles)
            .contains("task-side-sheet")
            .contains("tasks-project-chip")
            .contains("tasks-tag-chip")
            .contains("tasks-task-sheet-grid");
    }

    @Test
    void projectsCatalogReusesTaskPermissionAndLinksIntoTaskScope() throws IOException {
        String template = read("src/main/resources/templates/projects/index.html");
        String runtime = read("src/main/resources/static/js/projects.js");
        String navbar = read("src/main/resources/templates/fragments/navbar.html");
        String uiConfig = read("src/main/resources/static/js/ui-config.js");

        assertThat(template)
            .contains("data-ui-page=\"projects\"")
            .contains("id=\"createProjectBtn\"")
            .contains("id=\"projectsGrid\"")
            .contains("id=\"projectModal\"");
        assertThat(runtime)
            .contains("/api/projects")
            .contains("/tasks?project=")
            .contains("data-project-edit")
            .contains("method: id ? 'PATCH' : 'POST'")
            .contains("method: 'DELETE'");
        assertThat(navbar)
            .contains("data-page-key=\"projects\"")
            .contains("th:href=\"@{/projects}\"")
            .contains("th:if=\"${canManageTasks}\"");
        assertThat(uiConfig)
            .contains("projects: Object.freeze")
            .contains("path.startsWith('/projects')");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

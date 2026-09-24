package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskProjectFoundationSourceContractTest {

    @Test
    void foundationKeepsLegacyTaskUiCompatibleAndAddsCanonicalPlanningDomain() throws IOException {
        String taskApi = read("src/main/java/com/example/panel/controller/TaskApiController.java");
        String projectApi = read("src/main/java/com/example/panel/controller/ProjectApiController.java");
        String panelTaskService = read("src/main/java/com/example/panel/service/PanelTaskService.java");
        String domainService = read("src/main/java/com/example/panel/service/TaskDomainFoundationService.java");
        String postgresMigration = read("src/main/resources/db/migration/postgresql/V42__task_project_foundation.sql");
        String sqliteMigration = read("src/main/resources/db/migration/sqlite/V53__task_project_foundation.sql");
        String mysqlMigration = read("src/main/resources/db/migration/mysql/V21__task_project_foundation.sql");
        String tasksTemplate = read("src/main/resources/templates/tasks/index.html");

        assertThat(postgresMigration)
            .contains("CREATE TABLE IF NOT EXISTS projects")
            .contains("CREATE TABLE IF NOT EXISTS task_project_memberships")
            .contains("CREATE TABLE IF NOT EXISTS tags")
            .contains("CREATE TABLE IF NOT EXISTS task_tags")
            .contains("CREATE TABLE IF NOT EXISTS task_events")
            .contains("INSERT INTO tags")
            .contains("ON CONFLICT (task_id, tag_id) DO NOTHING");
        assertThat(sqliteMigration)
            .contains("INSERT OR IGNORE INTO tags")
            .contains("task_events");
        assertThat(mysqlMigration)
            .contains("INSERT IGNORE INTO tags")
            .contains("task_project_memberships");

        assertThat(projectApi)
            .contains("@RequestMapping(\"/api/projects\")")
            .contains("@DeleteMapping(\"/{id}\")");
        assertThat(taskApi)
            .contains("@RequestParam(name = \"tags\", required = false) String tags")
            .contains("@RequestParam(name = \"project_ids\", required = false) String projectIds")
            .contains("taskDomainFoundationService.recordSaved")
            .contains("dto.put(\"projects\", taskDomainFoundationService.listProjects(task.getId()))")
            .contains("dto.put(\"tags\", taskDomainFoundationService.listTags(task.getId()))")
            .contains("dto.put(\"events\", taskDomainFoundationService.listEvents(task.getId()))");
        assertThat(panelTaskService).contains("taskDomainFoundationService.recordCreated");
        assertThat(domainService)
            .contains("TASK_CREATED")
            .contains("FIELD_CHANGED")
            .contains("TAG_ADDED")
            .contains("TAG_REMOVED")
            .contains("PROJECT_ADDED")
            .contains("PROJECT_REMOVED")
            .contains("COMMENT_ADDED");

        assertThat(tasksTemplate)
            .contains("name=\"tag\"")
            .doesNotContain("project_ids");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

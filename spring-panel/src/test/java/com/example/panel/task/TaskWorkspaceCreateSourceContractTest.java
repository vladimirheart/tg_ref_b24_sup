package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskWorkspaceCreateSourceContractTest {

    @Test
    void uiCreateUsesNativePostgresTimestampsRequiredFieldsAndNoProjectDefault() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/TaskApiController.java");
        String runtime = read("src/main/resources/static/js/tasks.js");
        String baseline = read("src/main/resources/db/migration/postgresql/V1__baseline_schema.sql");
        String panelTaskService = read("src/main/java/com/example/panel/service/PanelTaskService.java");
        String taskEntity = read("src/main/java/com/example/panel/entity/Task.java");
        String taskComment = read("src/main/java/com/example/panel/entity/TaskComment.java");
        String template = read("src/main/resources/templates/tasks/index.html");

        assertThat(baseline)
            .contains("seq              BIGINT NOT NULL")
            .contains("due_at           TIMESTAMP WITH TIME ZONE")
            .contains("closed_at        TIMESTAMP WITH TIME ZONE")
            .contains("last_activity_at TIMESTAMP WITH TIME ZONE")
            .contains("created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP");

        assertThat(taskEntity)
            .contains("private OffsetDateTime dueAt;")
            .contains("private OffsetDateTime closedAt;")
            .contains("private OffsetDateTime lastActivityAt;")
            .doesNotContain("LenientOffsetDateTimeConverter");
        assertThat(taskComment)
            .contains("private OffsetDateTime createdAt;")
            .doesNotContain("LenientOffsetDateTimeConverter");
        assertThat(panelTaskService)
            .contains("task.setSeq(nextSequenceValue())")
            .contains("private long nextSequenceValue()");

        assertThat(controller)
            .contains("private final TaskSequenceRepository taskSequenceRepository")
            .contains("if (isNew && task.getSeq() == null)")
            .contains("task.setSeq(nextSequenceValue())")
            .contains("private long nextSequenceValue()")
            .contains("taskSequenceRepository.findById(TASK_SEQUENCE_ROW_ID)")
            .contains("validateTaskRequiredFields(title, bodyHtml, assignee)")
            .contains("Название задачи обязательно")
            .contains("Описание задачи обязательно")
            .contains("Ответственный обязателен")
            .doesNotContain("saved.setSeq(saved.getId())");

        int sequenceAssignment = controller.indexOf("task.setSeq(nextSequenceValue())");
        int firstSave = controller.indexOf("Task saved = taskRepository.save(task)");
        assertThat(sequenceAssignment).isGreaterThanOrEqualTo(0);
        assertThat(firstSave).isGreaterThan(sequenceAssignment);

        assertThat(template)
            .contains("name=\"title\" class=\"form-control tasks-title-input\"")
            .contains("aria-required=\"true\"")
            .contains("name=\"assignee\" class=\"form-select\" required")
            .contains("id=\"clearTaskProjectsBtn\"")
            .contains("name=\"due_at\" class=\"form-control\"")
            .doesNotContain("name=\"due_at\" class=\"form-control\" required")
            .doesNotContain("name=\"project_ids_ui\" class=\"form-select mt-1\" required")
            .contains("tasks.js?v=");

        assertThat(runtime)
            .contains("function resetTaskForm()")
            .contains("setSelectedProjects([])")
            .contains("function taskDescriptionText()")
            .contains("Описание задачи обязательно")
            .contains("clearTaskProjectsBtn?.addEventListener('click'")
            .doesNotContain("setSelectedProjects(state.projectId ? [{ id: state.projectId }] : [])");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

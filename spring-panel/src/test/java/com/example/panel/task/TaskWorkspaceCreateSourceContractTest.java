package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskWorkspaceCreateSourceContractTest {

    @Test
    void uiCreateAssignsSequenceBeforeInsertAndStartsWithoutProjectSelection() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/TaskApiController.java");
        String runtime = read("src/main/resources/static/js/tasks.js");
        String baseline = read("src/main/resources/db/migration/postgresql/V1__baseline_schema.sql");
        String panelTaskService = read("src/main/java/com/example/panel/service/PanelTaskService.java");

        assertThat(baseline).contains("seq              BIGINT NOT NULL");
        assertThat(panelTaskService)
            .contains("task.setSeq(nextSequenceValue())")
            .contains("private long nextSequenceValue()");

        assertThat(controller)
            .contains("private final TaskSequenceRepository taskSequenceRepository")
            .contains("if (isNew && task.getSeq() == null)")
            .contains("task.setSeq(nextSequenceValue())")
            .contains("private long nextSequenceValue()")
            .contains("taskSequenceRepository.findById(TASK_SEQUENCE_ROW_ID)")
            .doesNotContain("saved.setSeq(saved.getId())");

        int sequenceAssignment = controller.indexOf("task.setSeq(nextSequenceValue())");
        int firstSave = controller.indexOf("Task saved = taskRepository.save(task)");
        assertThat(sequenceAssignment).isGreaterThanOrEqualTo(0);
        assertThat(firstSave).isGreaterThan(sequenceAssignment);

        assertThat(runtime)
            .contains("function resetTaskForm()")
            .contains("setSelectedProjects([])")
            .doesNotContain("setSelectedProjects(state.projectId ? [{ id: state.projectId }] : [])");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

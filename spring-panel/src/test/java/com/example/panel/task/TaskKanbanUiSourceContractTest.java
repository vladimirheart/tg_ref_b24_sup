package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskKanbanUiSourceContractTest {

    @Test
    void boardViewUsesCanonicalBoardApiAndExistingTaskSheet() throws IOException {
        String template = read("src/main/resources/templates/tasks/index.html");
        String boardRuntime = read("src/main/resources/static/js/tasks-board.js");
        String tasksRuntime = read("src/main/resources/static/js/tasks.js");
        String styles = read("src/main/resources/scss/app/_tasks.scss");

        assertThat(template)
            .contains("data-task-view=\"list\"")
            .contains("data-task-view=\"board\"")
            .contains("id=\"taskBoardView\"")
            .contains("id=\"taskBoardScope\"")
            .contains("id=\"taskKanban\"")
            .contains("tasks-board.js?v=20260925-01-274-kanban-ui-r38")
            .contains("tasks.js?v=20260925-01-274-kanban-ui-r38");

        assertThat(boardRuntime)
            .contains("/api/task-boards/mine/ensure")
            .contains("/api/task-boards/project/")
            .contains("/api/task-boards/${state.board.id}/move")
            .contains("/api/task-boards/${state.board.id}/columns")
            .contains("data-open-task")
            .contains("draggable=\"true\"")
            .contains("target_index")
            .doesNotContain("task.status =")
            .doesNotContain("status: column");

        assertThat(tasksRuntime).contains("BOARD_CARD_MOVED");
        assertThat(styles)
            .contains(".tasks-kanban")
            .contains(".tasks-kanban-column")
            .contains(".tasks-kanban-card")
            .contains(".is-drop-target");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

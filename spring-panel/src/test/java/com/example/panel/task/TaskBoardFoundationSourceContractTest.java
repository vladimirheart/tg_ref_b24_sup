package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskBoardFoundationSourceContractTest {

    @Test
    void phaseCKeepsCanonicalTaskStatusSeparateFromBoardPlacement() throws IOException {
        String postgres = read("src/main/resources/db/migration/postgresql/V43__task_board_foundation.sql");
        String sqlite = read("src/main/resources/db/migration/sqlite/V54__task_board_foundation.sql");
        String mysql = read("src/main/resources/db/migration/mysql/V22__task_board_foundation.sql");
        String service = read("src/main/java/com/example/panel/service/TaskBoardService.java");
        String controller = read("src/main/java/com/example/panel/controller/TaskBoardApiController.java");
        String readiness = read("src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java");
        String memberships = read("src/main/java/com/example/panel/repository/TaskProjectMembershipRepository.java");
        String taskPeople = read("src/main/java/com/example/panel/repository/TaskPersonRepository.java");

        assertThat(postgres)
            .contains("CREATE TABLE IF NOT EXISTS project_boards")
            .contains("CREATE TABLE IF NOT EXISTS board_columns")
            .contains("CREATE TABLE IF NOT EXISTS board_task_placements")
            .contains("CONSTRAINT uq_board_task_placement UNIQUE(board_id, task_id)");
        assertThat(sqlite)
            .contains("project_boards")
            .contains("board_task_placements")
            .contains("UNIQUE(board_id, task_id)");
        assertThat(mysql)
            .contains("project_boards")
            .contains("board_columns")
            .contains("board_task_placements");

        assertThat(controller)
            .contains("@RequestMapping(\"/api/task-boards\")")
            .contains("@PostMapping(\"/project/{projectId}/ensure\")")
            .contains("@PostMapping(\"/mine/ensure\")")
            .contains("@PostMapping(\"/{boardId}/move\")")
            .contains("hasAuthority('PAGE_TASKS')");

        assertThat(service)
            .contains("DEFAULT_COLUMNS")
            .contains("\"PROJECT:\" + project.getId()")
            .contains("\"PERSONAL:\" + owner.toLowerCase")
            .contains("findByProject_IdOrderByTask_IdAsc")
            .contains("findByIdentityIgnoreCaseAndRoleIn")
            .contains("BOARD_CARD_MOVED")
            .contains("defaultColumnForTask")
            .doesNotContain("task.setStatus(");

        assertThat(memberships).contains("findByProject_IdOrderByTask_IdAsc");
        assertThat(taskPeople).contains("findByIdentityIgnoreCaseAndRoleIn");

        assertThat(readiness)
            .contains("FROM project_boards WHERE 1 = 0")
            .contains("FROM board_columns WHERE 1 = 0")
            .contains("FROM board_task_placements WHERE 1 = 0");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

package com.example.panel.controller;

import com.example.panel.service.TaskBoardService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/task-boards")
@PreAuthorize("hasAuthority('PAGE_TASKS')")
public class TaskBoardApiController {

    private final TaskBoardService taskBoardService;

    public TaskBoardApiController(TaskBoardService taskBoardService) {
        this.taskBoardService = taskBoardService;
    }

    @PostMapping("/project/{projectId}/ensure")
    public Map<String, Object> ensureProject(@PathVariable Long projectId,
                                             Authentication authentication) {
        return Map.of(
            "ok", true,
            "board", taskBoardService.ensureProjectBoard(projectId, actor(authentication))
        );
    }

    @PostMapping("/mine/ensure")
    public Map<String, Object> ensureMine(Authentication authentication) {
        return Map.of(
            "ok", true,
            "board", taskBoardService.ensurePersonalBoard(actor(authentication))
        );
    }

    @GetMapping("/{boardId}")
    public Map<String, Object> get(@PathVariable Long boardId,
                                   Authentication authentication) {
        return Map.of(
            "ok", true,
            "board", taskBoardService.getBoard(boardId, actor(authentication))
        );
    }

    @PostMapping("/{boardId}/columns")
    public Map<String, Object> createColumn(@PathVariable Long boardId,
                                            @RequestBody Map<String, Object> payload,
                                            Authentication authentication) {
        return Map.of(
            "ok", true,
            "board", taskBoardService.createColumn(
                boardId,
                text(payload.get("name")),
                integer(payload.get("target_index")),
                actor(authentication)
            )
        );
    }

    @PatchMapping("/{boardId}/columns/{columnId}")
    public Map<String, Object> updateColumn(@PathVariable Long boardId,
                                            @PathVariable Long columnId,
                                            @RequestBody Map<String, Object> payload,
                                            Authentication authentication) {
        String name = payload.containsKey("name") ? text(payload.get("name")) : null;
        Integer targetIndex = payload.containsKey("target_index") ? integer(payload.get("target_index")) : null;
        return Map.of(
            "ok", true,
            "board", taskBoardService.updateColumn(
                boardId,
                columnId,
                name,
                targetIndex,
                actor(authentication)
            )
        );
    }

    @DeleteMapping("/{boardId}/columns/{columnId}")
    public Map<String, Object> deleteColumn(@PathVariable Long boardId,
                                            @PathVariable Long columnId,
                                            Authentication authentication) {
        return Map.of(
            "ok", true,
            "board", taskBoardService.deleteColumn(boardId, columnId, actor(authentication))
        );
    }

    @PostMapping("/{boardId}/move")
    public Map<String, Object> move(@PathVariable Long boardId,
                                    @RequestBody Map<String, Object> payload,
                                    Authentication authentication) {
        Long taskId = requiredLong(payload.get("task_id"), "task_id");
        Long columnId = requiredLong(payload.get("column_id"), "column_id");
        Integer targetIndex = integer(payload.get("target_index"));
        return Map.of(
            "ok", true,
            "board", taskBoardService.moveCard(
                boardId,
                taskId,
                columnId,
                targetIndex,
                actor(authentication)
            )
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }

    private String text(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private Integer integer(Object raw) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected integer value.");
        }
    }

    private Long requiredLong(Object raw, String field) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be numeric.");
        }
    }
}

package com.example.panel.controller;

import com.example.panel.service.TaskAnalyticsService;
import com.example.panel.service.TaskAnalyticsViewService;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-analytics")
@PreAuthorize("hasAuthority('PAGE_TASKS')")
public class TaskAnalyticsApiController {

    private final TaskAnalyticsService taskAnalyticsService;
    private final TaskAnalyticsViewService taskAnalyticsViewService;

    public TaskAnalyticsApiController(TaskAnalyticsService taskAnalyticsService,
                                      TaskAnalyticsViewService taskAnalyticsViewService) {
        this.taskAnalyticsService = taskAnalyticsService;
        this.taskAnalyticsViewService = taskAnalyticsViewService;
    }

    @GetMapping
    public Map<String, Object> summary(@RequestParam(name = "from", required = false) String from,
                                       @RequestParam(name = "to", required = false) String to,
                                       @RequestParam(name = "project_id", required = false) Long projectId,
                                       @RequestParam(name = "tag", required = false) String tag,
                                       @RequestParam(name = "status", required = false) String status,
                                       @RequestParam(name = "assignee", required = false) String assignee,
                                       @RequestParam(name = "source", required = false) String source,
                                       @RequestParam(name = "event_type", required = false) String eventType) {
        return taskAnalyticsService.summary(
            from,
            to,
            projectId,
            tag,
            status,
            assignee,
            source,
            eventType
        );
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(name = "from", required = false) String from,
                                             @RequestParam(name = "to", required = false) String to,
                                             @RequestParam(name = "project_id", required = false) Long projectId,
                                             @RequestParam(name = "tag", required = false) String tag,
                                             @RequestParam(name = "status", required = false) String status,
                                             @RequestParam(name = "assignee", required = false) String assignee,
                                             @RequestParam(name = "source", required = false) String source,
                                             @RequestParam(name = "event_type", required = false) String eventType) {
        String csv = taskAnalyticsService.exportCsv(
            from,
            to,
            projectId,
            tag,
            status,
            assignee,
            source,
            eventType
        );
        String filename = "task-analytics-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/views")
    public Map<String, Object> listViews(Principal principal) {
        return Map.of("items", taskAnalyticsViewService.list(owner(principal)));
    }

    @PostMapping("/views")
    public Map<String, Object> createView(@RequestBody SavedViewRequest request,
                                          Principal principal) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("view", taskAnalyticsViewService.save(
            owner(principal),
            null,
            request != null ? request.name() : null,
            request != null ? request.filters() : null
        ));
        return response;
    }

    @PatchMapping("/views/{id}")
    public Map<String, Object> updateView(@PathVariable Long id,
                                          @RequestBody SavedViewRequest request,
                                          Principal principal) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("view", taskAnalyticsViewService.save(
            owner(principal),
            id,
            request != null ? request.name() : null,
            request != null ? request.filters() : null
        ));
        return response;
    }

    @DeleteMapping("/views/{id}")
    public Map<String, Object> deleteView(@PathVariable Long id,
                                          Principal principal) {
        taskAnalyticsViewService.delete(owner(principal), id);
        return Map.of("ok", true);
    }

    private String owner(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    public record SavedViewRequest(String name, Map<String, Object> filters) {
    }
}

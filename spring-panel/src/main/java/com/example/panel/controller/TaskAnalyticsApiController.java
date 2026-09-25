package com.example.panel.controller;

import com.example.panel.service.TaskAnalyticsService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-analytics")
@PreAuthorize("hasAuthority('PAGE_TASKS')")
public class TaskAnalyticsApiController {

    private final TaskAnalyticsService taskAnalyticsService;

    public TaskAnalyticsApiController(TaskAnalyticsService taskAnalyticsService) {
        this.taskAnalyticsService = taskAnalyticsService;
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
}

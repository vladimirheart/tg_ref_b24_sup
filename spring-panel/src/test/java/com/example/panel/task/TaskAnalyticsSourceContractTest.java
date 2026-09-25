package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskAnalyticsSourceContractTest {

    @Test
    void analyticsUsesCanonicalTasksStructuredEventsAndCoverageAwareTimeInStatus() throws IOException {
        String controller = read("src/main/java/com/example/panel/controller/TaskAnalyticsApiController.java");
        String service = read("src/main/java/com/example/panel/service/TaskAnalyticsService.java");
        String template = read("src/main/resources/templates/tasks/index.html");
        String runtime = read("src/main/resources/static/js/tasks-analytics.js");
        String viewRuntime = read("src/main/resources/static/js/tasks-board.js");
        String styles = read("src/main/resources/scss/app/_tasks.scss");
        String taskDoc = read("../ai-context/tasks/task-details/01-274.md");

        assertThat(controller)
            .contains("@RequestMapping(\"/api/task-analytics\")")
            .contains("@GetMapping")
            .contains("hasAuthority('PAGE_TASKS')")
            .contains("event_type")
            .contains("project_id")
            .contains("/export.csv")
            .contains("/views");

        assertThat(service)
            .contains("FROM tasks t")
            .contains("FROM task_events e")
            .contains("task_project_memberships")
            .contains("task_tags")
            .contains("FIELD_CHANGED")
            .contains("TASK_CREATED")
            .contains("STATUS_DONE = \"Завершена\"")
            .contains("STATUS_IN_PROGRESS = \"В работе\"")
            .contains("metrics.put(\"throughput\"")
            .contains("metrics.put(\"reopened\"")
            .contains("metrics.put(\"avg_lead_hours\"")
            .contains("metrics.put(\"avg_cycle_hours\"")
            .contains("time_in_status_breakdown")
            .contains("time_in_status_eligible_tasks")
            .contains("event-contract v2")
            .contains("exportCsv(")
            .doesNotContain("UPDATE tasks")
            .doesNotContain("DELETE FROM tasks");

        assertThat(template)
            .contains("data-task-view=\"analytics\"")
            .contains("id=\"taskAnalyticsView\"")
            .contains("id=\"taskAnalyticsFilters\"")
            .contains("id=\"taskAnalyticsSavedView\"")
            .contains("id=\"taskAnalyticsExportBtn\"")
            .contains("id=\"taskAnalyticsTimeInStatus\"")
            .contains("tasks-analytics.js?v=20260925-01-274-analytics-r50");

        assertThat(viewRuntime)
            .contains("state.view = ['board', 'analytics'].includes(view) ? view : 'list'")
            .contains("tasks:analytics-activate")
            .contains("requestedView === 'analytics'");

        assertThat(runtime)
            .contains("/api/task-analytics?")
            .contains("/api/task-analytics/views")
            .contains("/api/task-analytics/export.csv")
            .contains("avg_lead_hours")
            .contains("avg_cycle_hours")
            .contains("time_in_status_breakdown")
            .contains("status_breakdown")
            .contains("event_breakdown");

        assertThat(styles)
            .contains(".tasks-analytics-view")
            .contains(".tasks-analytics-saved-views")
            .contains(".tasks-analytics-metric")
            .contains(".tasks-analytics-breakdown-row--hours");

        assertThat(taskDoc)
            .contains("01-274_PHASE_C_KANBAN_ACCEPTED_2026-09-25")
            .contains("01-274_PHASE_D_ANALYTICS_R45_2026-09-25")
            .contains("01-274_PHASE_D_ANALYTICS_R50_2026-09-25");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

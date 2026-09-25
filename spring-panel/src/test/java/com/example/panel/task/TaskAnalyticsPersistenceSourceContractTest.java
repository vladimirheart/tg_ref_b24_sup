package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskAnalyticsPersistenceSourceContractTest {

    @Test
    void savedViewsAreOwnerScopedAndMigrationsFollowEveryDatabaseChain() throws IOException {
        String postgres = read("src/main/resources/db/migration/postgresql/V44__task_analytics_saved_views.sql");
        String sqlite = read("src/main/resources/db/migration/sqlite/V55__task_analytics_saved_views.sql");
        String mysql = read("src/main/resources/db/migration/mysql/V23__task_analytics_saved_views.sql");
        String entity = read("src/main/java/com/example/panel/entity/TaskAnalyticsView.java");
        String repository = read("src/main/java/com/example/panel/repository/TaskAnalyticsViewRepository.java");
        String service = read("src/main/java/com/example/panel/service/TaskAnalyticsViewService.java");
        String readiness = read("src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java");

        assertThat(postgres)
            .contains("CREATE TABLE IF NOT EXISTS task_analytics_views")
            .contains("owner_identity VARCHAR(255) NOT NULL")
            .contains("filters_json TEXT NOT NULL")
            .contains("uq_task_analytics_view_owner_name");
        assertThat(sqlite).contains("CREATE TABLE IF NOT EXISTS task_analytics_views");
        assertThat(mysql).contains("CREATE TABLE IF NOT EXISTS task_analytics_views");

        assertThat(entity)
            .contains("@Table(")
            .contains("name = \"task_analytics_views\"")
            .contains("private String ownerIdentity")
            .contains("private String filtersJson");
        assertThat(repository)
            .contains("findByOwnerIdentityOrderByUpdatedAtDescIdDesc")
            .contains("findByIdAndOwnerIdentity")
            .contains("findByOwnerIdentityAndNameIgnoreCase");
        assertThat(service)
            .contains("ALLOWED_FILTERS")
            .contains("normalizeOwner")
            .contains("findByIdAndOwnerIdentity")
            .contains("findByOwnerIdentityAndNameIgnoreCase");
        assertThat(readiness)
            .contains("SELECT id, owner_identity, name, filters_json, updated_at FROM task_analytics_views WHERE 1 = 0");
    }

    @Test
    void newTaskCreatedEventSeedsStatusTimelineWithoutBackfillingHistory() throws IOException {
        String foundation = read("src/main/java/com/example/panel/service/TaskDomainFoundationService.java");
        String analytics = read("src/main/java/com/example/panel/service/TaskAnalyticsService.java");
        String postgres = read("src/main/resources/db/migration/postgresql/V44__task_analytics_saved_views.sql");

        assertThat(foundation)
            .contains("\"TASK_CREATED\"")
            .contains("\"status\"")
            .contains("{\\\"status_timeline\\\":\\\"v2\\\"}");
        assertThat(analytics)
            .contains("isInitialStatusEvent")
            .contains("hasCompleteStatusTimeline")
            .contains("accumulateTimeInStatus");
        assertThat(postgres)
            .doesNotContain("UPDATE task_events")
            .doesNotContain("INSERT INTO task_events");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

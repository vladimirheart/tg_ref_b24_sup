package com.example.panel.dashboard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ManagerReportPostgresTimestampSourceContractTest {

    private static final Pattern TASK_CREATED_AT_CONVERTER = Pattern.compile(
            "@Convert\\(converter = LenientOffsetDateTimeConverter\\.class\\)\\s+private OffsetDateTime createdAt;"
    );
    private static final Pattern HISTORY_AT_CONVERTER = Pattern.compile(
            "@Convert\\(converter = LenientOffsetDateTimeConverter\\.class\\)\\s+private OffsetDateTime at;"
    );

    @Test
    void olapRangeCountersUseNativePostgresTimestamptzBindings() throws IOException {
        String task = read("src/main/java/com/example/panel/entity/Task.java");
        String history = read("src/main/java/com/example/panel/entity/TaskHistory.java");
        String service = read("src/main/java/com/example/panel/service/ManagerReportService.java");
        String schema = read("src/main/resources/db/migration/postgresql/V1__baseline_schema.sql");

        assertTrue(task.contains("private OffsetDateTime createdAt;"));
        assertTrue(history.contains("private OffsetDateTime at;"));
        assertFalse(TASK_CREATED_AT_CONVERTER.matcher(task).find());
        assertFalse(HISTORY_AT_CONVERTER.matcher(history).find());

        assertTrue(service.contains("taskRepository.countByCreatedAtBetween(from, to)"));
        assertTrue(service.contains("taskHistoryRepository.countByAtBetween(from, to)"));
        assertTrue(schema.contains("created_at       TIMESTAMP WITH TIME ZONE"));
        assertTrue(schema.contains("at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}

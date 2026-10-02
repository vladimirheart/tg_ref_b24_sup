package com.example.panel.task;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskStatusSaveUiSourceContractTest {

    @Test
    void taskSaveUsesCurrentStatusSelectInsteadOfStaleDatasetSnapshot() throws IOException {
        String runtime = Files.readString(Path.of("src/main/resources/static/js/tasks.js"), UTF_8);
        String template = Files.readString(Path.of("src/main/resources/templates/tasks/index.html"), UTF_8);

        assertThat(runtime)
            .contains("const currentStatus = String(taskForm.elements.namedItem('status')?.value || taskForm.dataset.status || '\u041d\u043e\u0432\u0430\u044f').trim() || '\u041d\u043e\u0432\u0430\u044f';")
            .contains("data.append('status', currentStatus);")
            .doesNotContain("data.append('status', String(taskForm.dataset.status || '\u041d\u043e\u0432\u0430\u044f'));");

        assertThat(template).contains("tasks.js?v=20261002-01-278-status-r4");
    }
}

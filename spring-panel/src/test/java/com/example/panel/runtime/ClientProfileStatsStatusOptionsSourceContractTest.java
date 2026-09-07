package com.example.panel.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientProfileStatsStatusOptionsSourceContractTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void profileReusesTotalTimeAndBuildsResilientStatusOptions() throws IOException {
        String template = read("spring-panel/src/main/resources/templates/clients/profile.html");
        String statsModel = read("spring-panel/src/main/java/com/example/panel/model/clients/ClientProfileStats.java");
        String clientsService = read("spring-panel/src/main/java/com/example/panel/service/ClientsService.java");
        String controller = read("spring-panel/src/main/java/com/example/panel/controller/ClientsController.java");
        String scss = read("spring-panel/src/main/resources/scss/app/_unified-ui.scss");
        String taskList = read("ai-context/tasks/task-list.md");

        assertThat(template)
            .contains("Затрачено времени")
            .contains("profile.stats.formattedTime")
            .contains("Статусы не настроены")
            .contains("@{/css/app.css(v='")
            .doesNotContain("@{/css/app.css(v='20260907-2')}");

        assertThat(statsModel)
            .contains("int totalMinutes,")
            .contains("String formattedTime");

        assertThat(clientsService)
            .contains("int totalMinutes = loadTotalMinutes(userId);")
            .contains("formatTimeDuration(totalMinutes)")
            .contains("public List<String> loadKnownClientStatuses()")
            .contains("FROM client_statuses")
            .contains("SELECT DISTINCT client_status")
            .contains("FROM messages");

        assertThat(controller)
            .contains("resolveClientStatusOptions(settings, profile.get())")
            .contains("settings.get(\"client_statuses\")")
            .contains("settings.get(\"client_status_colors\")")
            .contains("clientsService.loadKnownClientStatuses()")
            .contains("profile.clientStatus()");

        assertThat(scss)
            .contains("grid-template-columns: repeat(5, minmax(0, 1fr));");

        assertThat(taskList)
            .contains("🟣 [01-257] Добавить общее время в статистику клиента и восстановить варианты статуса");
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(
            REPO_ROOT.resolve(relativePath),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }
}

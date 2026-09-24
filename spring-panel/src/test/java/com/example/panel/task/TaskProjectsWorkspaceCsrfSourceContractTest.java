package com.example.panel.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskProjectsWorkspaceCsrfSourceContractTest {

    @Test
    void taskAndProjectUnsafeRequestsUseCookieCsrfHeaderContract() throws IOException {
        String security = read("src/main/java/com/example/panel/security/SecurityConfig.java");
        String tasksTemplate = read("src/main/resources/templates/tasks/index.html");
        String projectsTemplate = read("src/main/resources/templates/projects/index.html");
        String tasksRuntime = read("src/main/resources/static/js/tasks.js");
        String projectsRuntime = read("src/main/resources/static/js/projects.js");

        assertThat(security)
            .contains("CookieCsrfTokenRepository.withHttpOnlyFalse()")
            .doesNotContain("csrf.disable");

        assertTemplateExposesCsrf(tasksTemplate);
        assertTemplateExposesCsrf(projectsTemplate);
        assertRuntimeUsesCsrf(tasksRuntime);
        assertRuntimeUsesCsrf(projectsRuntime);
    }

    private void assertTemplateExposesCsrf(String template) {
        assertThat(template)
            .contains("meta name=\"_csrf\"")
            .contains("meta name=\"_csrf_header\"")
            .contains("_csrf.headerName")
            .contains("X-XSRF-TOKEN");
    }

    private void assertRuntimeUsesCsrf(String runtime) {
        assertThat(runtime)
            .contains("const csrfToken = document.querySelector('meta[name=\"_csrf\"]')")
            .contains("const csrfHeader = document.querySelector('meta[name=\"_csrf_header\"]')")
            .contains("headers.set(csrfHeader, csrfToken)")
            .contains("const merged = { credentials: 'same-origin', ...options }")
            .contains("['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)")
            .contains("fetch(url, requestOptions(options))");
    }

    private String read(String relative) throws IOException {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .replace("\r", "\n");
    }
}

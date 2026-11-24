package com.example.panel.task;

import com.example.panel.entity.PanelUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import static org.assertj.core.api.Assertions.assertThat;

class TaskCardRenderTest {
    @Test void renderActualTaskPageWithNamedEmployeesAndAccessControlsInsideScrollableBody() throws Exception {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath() + "/");
        resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet); request.setRequestURI("/tasks");
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, new MockHttpServletResponse());
        var anna = person("anna", "Анна Смирнова");
        var victor = person("victor", "Виктор Крюков");
        var context = new WebContext(exchange, Locale.forLanguageTag("ru"), Map.of(
            "users", List.of(anna, victor), "tasks", List.of(), "canManageTasks", true,
            "sidebarUserDisplayName", "Анна Смирнова", "sidebarUserUsername", "anna",
            "uiPreferencesBootstrap", Map.of(), "uiPreferencesSyncEnabled", false));
        String html = engine.process("tasks/index", context);
        assertThat(html).contains("data-employee-name=\"Анна Смирнова\"", "employee-profiles.js", "data-work-object=\"TASK\"")
            .doesNotContain("th:replace", "th:each");
        Path output = Path.of("target/task-card-rendered/tasks.html"); Files.createDirectories(output.getParent()); Files.writeString(output, html);
    }
    private PanelUser person(String username, String fullName) {
        var person = new PanelUser(); person.setUsername(username); person.setFullName(fullName);
        person.setDepartment("Поддержка"); person.setPhoto("/fixture/avatar.svg"); return person;
    }
}

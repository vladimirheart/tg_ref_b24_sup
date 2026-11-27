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
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectSettingsRenderTest {
    @Test void renderProjectSettingsWithParticipantsInsideScrollableBody() throws Exception {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath() + "/");
        resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var request = new MockHttpServletRequest(servlet); request.setRequestURI("/projects");
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request, new MockHttpServletResponse());
        var employee = new PanelUser(); employee.setUsername("anna"); employee.setFullName("Анна Смирнова");
        var context = new WebContext(exchange, Locale.forLanguageTag("ru"), Map.of(
            "users", List.of(employee), "canManageTasks", true,
            "sidebarUserDisplayName", "Анна Смирнова", "sidebarUserUsername", "anna",
            "uiPreferencesBootstrap", Map.of(), "uiPreferencesSyncEnabled", false,
            "_csrf", new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "project-fixture-csrf")));
        String html = engine.process("projects/index", context);
        var document = org.jsoup.Jsoup.parse(html);
        assertThat(document.select("#projectModal .modal-body #project-members [data-work-object=PROJECT]")).hasSize(1);
        assertThat(document.select("#projectModal [role=tab]")).hasSize(2);
        assertThat(document.select("#projectModal details[open]")).isEmpty();
        assertThat(html).doesNotContain("th:replace", "th:each");
        Path output = Path.of("target/project-settings-qa/projects.html");
        Files.createDirectories(output.getParent()); Files.writeString(output, html);
    }
}

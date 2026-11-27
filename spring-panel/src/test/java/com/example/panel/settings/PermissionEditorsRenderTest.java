package com.example.panel.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionEditorsRenderTest {
    @Test void renderRealUsersWorkspaceForBrowserChecks() throws Exception {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath() + "/");
        resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var context = new Context(Locale.forLanguageTag("ru"));
        context.setVariable("canManagePasswordResetRequests", false);
        context.setVariable("_csrf", new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "permissions-fixture-csrf"));
        String html = engine.process("settings/fragments/users-access", context);
        assertThat(html).contains("data-auth-management", "data-auth-roles-container", "permissions-fixture-csrf");
        assertThat(html).doesNotContain("th:value", "th:if", "data-users-open-password-reset");
        var output = Path.of("target/permission-editors-qa/users-workspace.html");
        Files.createDirectories(output.getParent()); Files.writeString(output, html);
    }
}

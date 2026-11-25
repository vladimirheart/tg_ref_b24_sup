package com.example.panel.ui;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import static org.assertj.core.api.Assertions.assertThat;

class EmployeeIdentityRenderTest {
    private String render(String username) {
        var resolver = new FileTemplateResolver();
        resolver.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath() + "/");
        resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servlet)
            .buildExchange(new MockHttpServletRequest(servlet), new MockHttpServletResponse());
        var variables = new HashMap<String, Object>(); variables.put("username", username);
        return engine.process("fragments/employee-identity", new WebContext(exchange, Locale.forLanguageTag("ru"), variables));
    }

    @Test void actualFragmentEscapesIdentityWithoutTurningItIntoMarkup() {
        String username = "<img src=x onerror=alert(1)> & \"Юзер\"";
        var document = Jsoup.parse(render(username));
        assertThat(document.select("img, script")).isEmpty();
        var link = document.selectFirst("span[data-employee-identity]");
        assertThat(link).isNotNull();
        assertThat(link.attr("data-employee-identity")).isEqualTo(username);
        assertThat(link.text()).isEqualTo(username);
        assertThat(link.hasAttr("href")).isFalse();
        assertThat(link.attr("data-employee-optional")).isEqualTo("true");
    }

    @Test void absentIdentityHasNoInteractiveLink() {
        for (String username : new String[] {null, "", "   "}) {
            var document = Jsoup.parse(render(username));
            assertThat(document.select("a, [data-employee-identity]")).isEmpty();
            assertThat(document.text()).isEqualTo("—");
        }
    }
}

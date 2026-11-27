package com.example.panel.service;

import com.example.panel.model.clients.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import static org.assertj.core.api.Assertions.*;

class ErrorAuditRenderTest {
    @Test void actualProfileTemplatePreservesServerScopeUrlsAndEscapesUntrustedText() throws Exception {
        String exact = " T/100?&%#+ Ю ";
        String businessUrl = "/dialogs?ticketId=" + java.net.URLEncoder.encode(exact, java.nio.charset.StandardCharsets.UTF_8).replace("+","%20") + "&channelId=7";
        var tickets = List.of(ticket(exact,businessUrl),ticket("GENERAL","/general-dialog-work/"+"a".repeat(32)),ticket("AMBIGUOUS",null));
        var profile = new ClientProfile(new ClientProfileHeader(55L,"fixture","Тестовый клиент",7L,"Тестовый канал","telegram","","","",""),
            new ClientProfileStats(3,0,3,0,"0 мин"),tickets,new ClientBlacklistInfo(false,false,"","","",""),
            0.0,List.of(),0,"",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var resolver = new FileTemplateResolver();
        resolver.setPrefix(Path.of("src/main/resources/templates").toAbsolutePath()+"/");
        resolver.setSuffix(".html");resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();var request = new MockHttpServletRequest(servlet);request.setRequestURI("/clients/55");
        var exchange = JakartaServletWebApplication.buildApplication(servlet).buildExchange(request,new MockHttpServletResponse());
        var context = new WebContext(exchange,java.util.Locale.forLanguageTag("ru"));
        context.setVariable("profile",profile);context.setVariable("clientStatuses",List.of());context.setVariable("clientStatusColors",Map.of());
        context.setVariable("canUnblockClient",false);context.setVariable("uiPreferencesBootstrap",Map.of());
        context.setVariable("uiPreferencesSyncEnabled",false);
        context.setVariable("_csrf",new DefaultCsrfToken("X-XSRF-TOKEN","_csrf","fixture-csrf"));
        String html = engine.process("clients/profile",context);
        var document = org.jsoup.Jsoup.parse(html);
        assertThat(document.select("[data-dialog-url]").eachAttr("data-dialog-url")).containsExactly(businessUrl);
        assertThat(document.select("[data-client-ticket-open]").eachAttr("href")).contains(businessUrl,"/general-dialog-work/"+"a".repeat(32));
        assertThat(html).doesNotContain("<script>window.untrusted=true</script>","th:each");
        Path output = Path.of("target/error-audit-qa/client-profile.html");Files.createDirectories(output.getParent());Files.writeString(output,html);
    }
    private ClientProfileTicket ticket(String id,String url) {
        return new ClientProfileTicket(id,id.equals("GENERAL") ? "FIXTURE-2" : id.equals("AMBIGUOUS") ? "FIXTURE-3" : "FIXTURE-1","Бизнес","Город","Кафе","Локация","<script>window.untrusted=true</script>",
            "2026-11-27T10:00:00Z","pending","","","","Тестовый канал",7L,url);
    }
}

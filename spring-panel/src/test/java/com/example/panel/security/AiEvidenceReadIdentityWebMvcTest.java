package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedDialogAiController;
import com.example.panel.service.*;
import com.example.panel.support.AiMemoryIdentityFixture;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import static com.example.panel.service.ScopedAiSourceService.Kind.*;
import static com.example.panel.service.ScopedAiSourceService.Status.*;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/R189 refresh and previous authorized reads precede the handoff candidate SQL. */
@SpringBootTest(classes=AiEvidenceReadIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiEvidenceReadIdentityWebMvcTest {
    private static final String PATH="/business-dialog-ai/"+SCOPE.ticketPublicId(),PASSWORD="fixture evidence read R219";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean ScopedAiHandoffKnowledgeService handoff;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedDialogAiController.class,ScopedDialogAiWorkbenchService.class,ScopedDialogAiPersistenceService.class,ScopedAiSourceService.class,ScopedAiHandoffKnowledgeService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->{AiMemoryIdentityFixture.schema(jdbc,true);jdbc.execute("ALTER TABLE businesses ADD COLUMN display_name TEXT DEFAULT 'Private business'");};}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS'),(7,'PAGE_KNOWLEDGE_BASE')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        var sources=new ScopedAiSourceService(jdbc,flags);assertThat(sources.createKnowledge(11L,auth(7,"caller"),KEY,"PRIVATE Candidate title","refund PRIVATE SOURCE BODY")).isTrue();assertThat(sources.revise(11L,auth(7,"caller"),KNOWLEDGE,KEY,1,"PRIVATE Candidate title","refund PRIVATE SOURCE BODY",APPROVED)).isTrue();assertThat(handoff.record(SCOPE,"refund")).isTrue();
    }
    private MockHttpSession login()throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var signedIn=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();var session=(MockHttpSession)signedIn.getRequest().getSession(false);assertThat(session).isNotNull();return session;
    }
    private void beforeCandidates(Runnable mutation,java.util.concurrent.atomic.AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(handoff);
        var method=Arrays.stream(ScopedAiHandoffKnowledgeService.class.getMethods()).filter(m->m.getName().equals("candidates")).findFirst().orElseThrow();method.invoke(pending,eq(SCOPE),any(method.getParameterTypes()[1]));
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","membership"})
    void lateAccountChangesCannotUseAnotherReadersCandidates(String change)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();beforeCandidates(()->replace(jdbc,change),fired);
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(model().attribute("candidates",org.hamcrest.Matchers.empty())).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE Candidate title"))));
        assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
        // Reads authorized earlier in the response may still supply control/messages; no response-wide lock.
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"})
    void aFreshRealLoginReadsOnlyCandidateSummaries(String name)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login();var before=snapshot(jdbc);
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(model().attribute("candidates",org.hamcrest.Matchers.hasSize(1))).andExpect(content().string(org.hamcrest.Matchers.containsString("PRIVATE Candidate title"))).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE SOURCE BODY"))));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"dialogs","knowledge","anonymous","flag"})
    void bothPageRightsAuthenticationAndFeatureGateStillProtectCandidateReads(String gate)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(gate.equals("dialogs")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='PAGE_DIALOGS'");mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());}
        else if(gate.equals("knowledge")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='PAGE_KNOWLEDGE_BASE'");mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(model().attribute("candidates",org.hamcrest.Matchers.empty())).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE Candidate title"))));}
        else if(gate.equals("anonymous"))mvc.perform(get(PATH)).andExpect(redirectedUrl("http://localhost/login"));
        else {flags.setUiSelector(false);mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());}
        assertThat(mockingDetails(handoff).getInvocations()).noneMatch(i->i.getMethod().getName().equals("candidates"));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
}

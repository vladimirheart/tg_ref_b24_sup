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
import org.springframework.test.web.servlet.MockMvc;
import static com.example.panel.support.AiControlIdentityFixture.SCOPE;
import static com.example.panel.support.AiControlIdentityFixture.PUBLIC;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual login, CSRF and CONFIGURE precheck; actor changes immediately before the MEMORY INSERT. */
@SpringBootTest(classes=AiMemoryActorIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiMemoryActorIdentityWebMvcTest {
    private static final String PATH="/business-dialog-ai/"+PUBLIC,PASSWORD="fixture AI memory R212";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean ScopedDialogAiWorkbenchService workbench;
    private jakarta.servlet.http.Cookie csrfCookie;
    private String csrfValue;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedDialogAiController.class,ScopedDialogAiWorkbenchService.class,ScopedDialogAiPersistenceService.class,ScopedAiSourceService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->AiMemoryIdentityFixture.schema(jdbc,true);}
        @Bean ScopedAiHandoffKnowledgeService knowledge(){return mock(ScopedAiHandoffKnowledgeService.class);}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS'),(7,'PAGE_KNOWLEDGE_BASE')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
    }
    private String token(org.springframework.test.web.servlet.MvcResult page)throws Exception {
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(matcher.find()).isTrue();return matcher.group(1);
    }
    private MockHttpSession login()throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var signedIn=mvc.perform(post("/login").cookie(cookie).param("_csrf",token(page)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        var session=(MockHttpSession)signedIn.getRequest().getSession(false);assertThat(session).isNotNull();
        var fresh=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();csrfCookie=fresh.getResponse().getCookie("XSRF-TOKEN");assertThat(csrfCookie).isNotNull();csrfValue=token(fresh);return session;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command() {
        return post(PATH+"/memory").param("businessId","11").param("key",KEY).param("question","40:"+fingerprint(jdbc,40)).param("answer","41:"+fingerprint(jdbc,41)).param("title","Reviewed title").param("body","safe curated answer").param("reviewed","true");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(MockHttpSession session) {
        return command().session(session).cookie(csrfCookie).param("_csrf",csrfValue);
    }
    private void beforeInsert(Runnable mutation,java.util.concurrent.atomic.AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(workbench);
        var method=Arrays.stream(ScopedDialogAiWorkbenchService.class.getMethods()).filter(m->m.getName().equals("createMemory")).findFirst().orElseThrow();
        method.invoke(pending,eq(SCOPE),any(method.getParameterTypes()[1]),anyString(),anyLong(),anyLong(),anyString(),anyString(),anyString(),anyString(),anyBoolean());
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","membership"})
    void aChangedAuthorAfterConfigurePrecheckCannotCreateAsAnotherAccount(String change)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();beforeInsert(()->replace(jdbc,change),fired);
        mvc.perform(signed(session)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"})
    void realCaseInsensitiveLoginCreatesReviewedDraftWithStoredRawAuthor(String name)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login();mvc.perform(signed(session)).andExpect(redirectedUrl(PATH)).andExpect(header().string("Cache-Control","no-store"));
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,status FROM ai_scoped_sources")).containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("status","DRAFT");
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor FROM ai_scoped_source_events")).containsEntry("actor_user_id",7L).containsEntry("actor",name);assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_scoped_memory_evidence",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER","ADMIN","ALL_ADMIN"})
    void currentConfigurePolicyStillRequiresAdmin(String role)throws Exception {
        if(role.equals("ALL_ADMIN")){jdbc.update("DELETE FROM business_memberships WHERE user_id=7");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','ADMIN')");}else jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=7",role);
        var session=login();var before=snapshot(jdbc);var request=mvc.perform(signed(session));if(role.endsWith("ADMIN"))request.andExpect(redirectedUrl(PATH));else{request.andExpect(status().isForbidden());assertThat(snapshot(jdbc)).isEqualTo(before);}
    }
    @ParameterizedTest @ValueSource(strings={"dialogs","knowledge","csrf","anonymous","flag"})
    void bothPageRightsCsrfAuthenticationAndFeatureGateRemainRequired(String kind)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(kind.equals("dialogs")||kind.equals("knowledge")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority=?",kind.equals("dialogs")?"PAGE_DIALOGS":"PAGE_KNOWLEDGE_BASE");mvc.perform(signed(session)).andExpect(status().isForbidden());}
        else if(kind.equals("csrf"))mvc.perform(command().session(session)).andExpect(redirectedUrl("/error/403"));
        else if(kind.equals("anonymous"))mvc.perform(command().cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(redirectedUrl("http://localhost/login"));
        else {flags.setUiSelector(false);mvc.perform(signed(session)).andExpect(status().isNotFound());}
        assertThat(mockingDetails(workbench).getInvocations()).noneMatch(i->i.getMethod().getName().equals("createMemory"));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"audit","evidence"})
    void failedDependentInsertDoesNotLeavePartialMemory(String table)throws Exception {
        jdbc.execute(table.equals("audit")?"ALTER TABLE ai_scoped_source_events ADD CONSTRAINT deny_create CHECK(action<>'create')":"ALTER TABLE ai_scoped_memory_evidence ADD CONSTRAINT deny_evidence CHECK(source_id<0)");var session=login();var before=snapshot(jdbc);mvc.perform(signed(session)).andExpect(status().isConflict());assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void aRepeatedFormCannotOverwriteTheDraftAuditOrEvidence()throws Exception {
        var session=login();mvc.perform(signed(session)).andExpect(redirectedUrl(PATH));var before=snapshot(jdbc);mvc.perform(signed(session)).andExpect(status().isConflict());assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"owner","grant","edit","deleted"})
    void lateOwnerGrantAndEvidenceChangesRemainDenied(String change)throws Exception {
        var session=login();var fired=new java.util.concurrent.atomic.AtomicBoolean();beforeInsert(()->{
            switch(change){case "owner"->jdbc.update("UPDATE tickets SET business_id=22");case "grant"->jdbc.update("DELETE FROM business_memberships WHERE user_id=7");case "edit"->jdbc.update("UPDATE chat_history SET message='changed' WHERE id=41");default->jdbc.update("UPDATE chat_history SET deleted_at=CURRENT_TIMESTAMP WHERE id=41");}
        },fired);mvc.perform(signed(session)).andExpect(status().isConflict());assertThat(fired).isTrue();for(String table:List.of("ai_scoped_sources","ai_scoped_source_events","ai_scoped_memory_evidence"))assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class)).as(table).isZero();
    }
}

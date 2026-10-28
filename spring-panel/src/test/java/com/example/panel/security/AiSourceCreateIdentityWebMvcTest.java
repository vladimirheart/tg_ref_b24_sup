package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedAiSourceController;
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
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login and CONFIGURE precheck; the author changes immediately before the create call. */
@SpringBootTest(classes=AiSourceCreateIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiSourceCreateIdentityWebMvcTest {
    private static final String PATH="/business-ai-sources",PASSWORD="fixture source create R215";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean ScopedAiSourceService sources;
    private jakarta.servlet.http.Cookie csrfCookie;
    private String csrfValue;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedAiSourceController.class,ScopedAiSourceService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->{AiMemoryIdentityFixture.schema(jdbc,true);jdbc.execute("ALTER TABLE businesses ADD COLUMN display_name TEXT DEFAULT 'Fixture business'");};}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_KNOWLEDGE_BASE')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
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
        return post(PATH+"/create").param("businessId","11").param("key",KEY).param("title","Reviewed title").param("body","safe curated answer");
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(MockHttpSession session) {
        return command().session(session).cookie(csrfCookie).param("_csrf",csrfValue);
    }
    private void beforeInsert(Runnable mutation,java.util.concurrent.atomic.AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(sources);
        var method=Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals("createKnowledge")).findFirst().orElseThrow();
        method.invoke(pending,eq(11L),any(method.getParameterTypes()[1]),anyString(),anyString(),anyString());
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","membership"})
    void aChangedAccountAfterConfigureCannotCreateAsAnotherAuthor(String change)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();beforeInsert(()->replace(jdbc,change),fired);
        mvc.perform(signed(session)).andExpect(status().isConflict()).andExpect(header().string("Cache-Control","no-store"));assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"caller","Caller"})
    void caseInsensitiveLoginCreatesWithTheStoredRawAuthor(String name)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login();mvc.perform(signed(session)).andExpect(status().is3xxRedirection()).andExpect(header().string("Cache-Control","no-store"));
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,status FROM ai_scoped_sources")).containsEntry("updated_by_user_id",7L).containsEntry("updated_by",name).containsEntry("status","DRAFT");
        assertThat(jdbc.queryForMap("SELECT actor_user_id,actor FROM ai_scoped_source_events")).containsEntry("actor_user_id",7L).containsEntry("actor",name);
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","MANAGER","ADMIN","ALL_ADMIN"})
    void configureStillRequiresBusinessAdmin(String role)throws Exception {
        if(role.equals("ALL_ADMIN")){jdbc.update("DELETE FROM business_memberships WHERE user_id=7");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','ADMIN')");}else jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=7",role);
        var session=login();var before=snapshot(jdbc);var request=mvc.perform(signed(session));if(role.endsWith("ADMIN"))request.andExpect(status().is3xxRedirection());else{request.andExpect(status().isForbidden());assertThat(snapshot(jdbc)).isEqualTo(before);}
    }
    @ParameterizedTest @ValueSource(strings={"page","csrf","anonymous","flag"})
    void pageAuthorityCsrfAuthenticationAndFeatureGateRemainRequired(String kind)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(kind.equals("page")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7");mvc.perform(signed(session)).andExpect(status().isForbidden());}
        else if(kind.equals("csrf"))mvc.perform(command().session(session)).andExpect(redirectedUrl("/error/403"));
        else if(kind.equals("anonymous"))mvc.perform(command().cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(redirectedUrl("http://localhost/login"));
        else {flags.setUiSelector(false);mvc.perform(signed(session)).andExpect(status().isNotFound());}
        assertThat(mockingDetails(sources).getInvocations()).noneMatch(i->i.getMethod().getName().equals("createKnowledge"));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void duplicateFormDoesNotOverwriteTheSourceOrAudit()throws Exception {
        var session=login();mvc.perform(signed(session)).andExpect(status().is3xxRedirection());var before=snapshot(jdbc);mvc.perform(signed(session)).andExpect(status().isConflict());assertThat(snapshot(jdbc)).isEqualTo(before);
    }
}

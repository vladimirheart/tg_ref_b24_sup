package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedDialogAiController;
import com.example.panel.service.*;
import com.example.panel.support.AiControlIdentityFixture;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
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
import static com.example.panel.support.AiControlIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login, R189 refresh and control SQL; mutations happen after the controller owner lookup. */
@SpringBootTest(classes=AiControlActorIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiControlActorIdentityWebMvcTest {
    private static final String PATH="/business-dialog-ai/"+PUBLIC,PASSWORD="fixture AI control R208";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @SpyBean ScopedDialogAiWorkbenchService workbench;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedDialogAiController.class,ScopedDialogAiWorkbenchService.class,ScopedDialogAiPersistenceService.class})
    static class Application{
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->AiControlIdentityFixture.schema(jdbc,true);}
        @Bean ScopedAiSourceService sources(){return mock(ScopedAiSourceService.class);}
        @Bean ScopedAiHandoffKnowledgeService knowledge(){return mock(ScopedAiHandoffKnowledgeService.class);}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup(){
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS')");
    }
    private jakarta.servlet.http.Cookie csrfCookie;
    private String csrfValue;
    private MockHttpSession login()throws Exception{
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(matcher.find()).isTrue();
        var response=mvc.perform(post("/login").cookie(cookie).param("_csrf",matcher.group(1)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        var session=(MockHttpSession)response.getRequest().getSession(false);
        var fresh=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();
        csrfCookie=fresh.getResponse().getCookie("XSRF-TOKEN");assertThat(csrfCookie).isNotNull();
        var freshToken=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(fresh.getResponse().getContentAsString());assertThat(freshToken.find()).isTrue();csrfValue=freshToken.group(1);
        return session;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder command(String stamp){return post(PATH+"/control").param("businessId","11").param("expectedUpdatedAt",stamp).param("aiDisabled","true").param("autoReplyBlocked","true").param("reason","pause");}
    static Stream<Arguments> changes(){return Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").flatMap(mode->Stream.of(false,true).map(p->Arguments.of(mode,p)));}
    @ParameterizedTest @MethodSource("changes")
    void aChangedAccountAfterRefreshCannotTakeOverEitherControlBranch(String change,boolean present)throws Exception{
        String stamp=present?existing(jdbc):"";var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call->{Object result=call.callRealMethod();if(fired.compareAndSet(false,true))replace(jdbc,change);return result;}).when(workbench).resolve(PUBLIC,"caller");
        mvc.perform(command(stamp).session(session).cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(status().isConflict());assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void boundOperatorWritesPrivateRedirectRawAuditAndCannotReplayTheForm(boolean present)throws Exception{
        String stamp=present?existing(jdbc):"";var session=login();mvc.perform(command(stamp).session(session).cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(redirectedUrl(PATH)).andExpect(header().string("Cache-Control","no-store"));
        assertThat(jdbc.queryForMap("SELECT updated_by_user_id,updated_by,ai_disabled,auto_reply_blocked FROM ticket_ai_scoped_control")).containsEntry("updated_by_user_id",7L).containsEntry("updated_by","caller").containsEntry("ai_disabled",true).containsEntry("auto_reply_blocked",true);
        var before=snapshot(jdbc);mvc.perform(command(stamp).session(session).cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(status().isConflict());assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"page","csrf","anonymous"})
    void pageCsrfAndAuthenticationRemainRequired(String mode)throws Exception{
        var session=login();var before=snapshot(jdbc);
        if(mode.equals("page")){jdbc.update("DELETE FROM user_authorities");mvc.perform(command("").session(session).cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(status().isForbidden());}
        else if(mode.equals("csrf"))mvc.perform(command("").session(session)).andExpect(redirectedUrl("/error/403"));
        else mvc.perform(command("").cookie(csrfCookie).param("_csrf",csrfValue)).andExpect(redirectedUrl("http://localhost/login"));
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }
}

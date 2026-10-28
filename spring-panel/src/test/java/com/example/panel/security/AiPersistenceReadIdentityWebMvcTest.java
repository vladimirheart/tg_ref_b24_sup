package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedDialogAiController;
import com.example.panel.service.*;
import com.example.panel.support.AiPersistenceReadIdentityFixture;
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
import static com.example.panel.support.AiPersistenceReadIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.empty;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual login and refresh; each mutation happens after owner lookup immediately before its read. */
@SpringBootTest(classes=AiPersistenceReadIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiPersistenceReadIdentityWebMvcTest {
    private static final String PATH="/business-dialog-ai/"+PUBLIC,PASSWORD="fixture AI state R211";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired SharedConfigService shared;
    @SpyBean ScopedDialogAiPersistenceService persistence;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedDialogAiController.class,ScopedDialogAiWorkbenchService.class,ScopedDialogAiPersistenceService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->AiPersistenceReadIdentityFixture.schema(jdbc,true);}
        @Bean ScopedAiSourceService sources(){return mock(ScopedAiSourceService.class);}
        @Bean ScopedAiHandoffKnowledgeService knowledge(){return mock(ScopedAiHandoffKnowledgeService.class);}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        AiPersistenceReadIdentityFixture.seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
    }
    private MockHttpSession login()throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var signedIn=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();var session=(MockHttpSession)signedIn.getRequest().getSession(false);assertThat(session).isNotNull();return session;
    }
    static Stream<Arguments> mutations(){return Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").flatMap(k->Stream.of("findControl","canOperate","findState","findEvents").map(m->Arguments.of(k,m)));}
    @ParameterizedTest @MethodSource("mutations")
    void aReaderChangedAfterOwnerLookupCannotReadOrOperateAsAReplacement(String change,String read)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))replace(jdbc,change);return call.callRealMethod();}).when(persistence);
        var method=Arrays.stream(ScopedDialogAiPersistenceService.class.getMethods()).filter(m->m.getName().equals(read)).findFirst().orElseThrow();
        if(read.equals("findEvents"))method.invoke(pending,eq(SCOPE),any(method.getParameterTypes()[1]),anyInt());else method.invoke(pending,eq(SCOPE),any(method.getParameterTypes()[1]));
        var request=mvc.perform(get(PATH).session(session)).andExpect(header().string("Cache-Control","no-store"));
        if(read.equals("findControl"))request.andExpect(status().isNotFound());
        else {
            request.andExpect(status().isOk());
            if(read.equals("canOperate"))request.andExpect(model().attribute("canOperate",false));
            else if(read.equals("findState"))assertThat(request.andReturn().getModelAndView().getModel().get("state")).isNull();
            else request.andExpect(model().attribute("events",empty()));
        }
        assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","OPERATOR","ALL_VIEWER"})
    void validReadRolesKeepControlStateEventsAndSeparateOperate(String role)throws Exception {
        if(role.equals("ALL_VIEWER")){jdbc.update("DELETE FROM business_memberships WHERE user_id=7");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','VIEWER')");}else jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=7",role);
        var session=login();var rendered=mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(model().attribute("canOperate",role.equals("OPERATOR"))).andReturn();var model=rendered.getModelAndView().getModel();
        assertThat(((ScopedDialogAiPersistenceService.Control)model.get("control")).reason()).isEqualTo(REASON);assertThat(((Map<?,?>)model.get("state")).get("last_action")).isEqualTo(ACTION);
        var events=(List<?>)model.get("events");assertThat(events).hasSize(1);assertThat(((Map<?,?>)events.get(0)).get("decision_reason")).isEqualTo(EVENT);
    }
    @ParameterizedTest @ValueSource(strings={"page","anonymous","flag"})
    void pageAuthenticationAndFeatureGatesRemainRequired(String kind)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(kind.equals("page")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7");mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());}
        else if(kind.equals("flag")){flags.setUiSelector(false);mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());}
        else mvc.perform(get(PATH)).andExpect(redirectedUrl("http://localhost/login"));
        assertThat(mockingDetails(persistence).getInvocations()).noneMatch(i->Set.of("findControl","canOperate","findState","findEvents").contains(i.getMethod().getName()));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void absentControlRetainsDefaultsForTheBoundOperator()throws Exception {
        jdbc.update("DELETE FROM ticket_ai_scoped_control");var session=login();var rendered=mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(model().attribute("canOperate",true)).andReturn();var control=(ScopedDialogAiPersistenceService.Control)rendered.getModelAndView().getModel().get("control");assertThat(control.aiDisabled()).isFalse();assertThat(control.reason()).isNull();assertThat(control.updatedAt()).isNull();
    }
}

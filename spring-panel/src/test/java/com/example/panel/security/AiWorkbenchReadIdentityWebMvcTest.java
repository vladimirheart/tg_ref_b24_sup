package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedDialogAiController;
import com.example.panel.service.*;
import com.example.panel.support.AiWorkbenchReadIdentityFixture;
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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual login and R189 refresh; mutations happen immediately before the selected read SQL. */
@SpringBootTest(classes=AiWorkbenchReadIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiWorkbenchReadIdentityWebMvcTest {
    private static final String PATH="/business-dialog-ai/"+PUBLIC,PASSWORD="fixture AI read R210";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MultibusinessFeatureFlags flags;
    @Autowired SharedConfigService shared;
    @SpyBean ScopedDialogAiWorkbenchService work;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedDialogAiController.class,ScopedDialogAiWorkbenchService.class,ScopedDialogAiPersistenceService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->AiWorkbenchReadIdentityFixture.schema(jdbc,true);}
        @Bean ScopedAiSourceService sources(){return mock(ScopedAiSourceService.class);}
        @Bean ScopedAiHandoffKnowledgeService knowledge(){return mock(ScopedAiHandoffKnowledgeService.class);}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        AiWorkbenchReadIdentityFixture.seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_DIALOGS'),(7,'PAGE_KNOWLEDGE_BASE')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
    }
    private MockHttpSession login()throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var signedIn=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        var session=(MockHttpSession)signedIn.getRequest().getSession(false);assertThat(session).isNotNull();return session;
    }
    static Stream<Arguments> mutations(){return Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").flatMap(k->Stream.of("resolve","messages").map(m->Arguments.of(k,m)));}
    @ParameterizedTest @MethodSource("mutations")
    void changedIdentityAfterRefreshCannotReadByCurrentOrReusedName(String change,String read)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))replace(jdbc,change);return call.callRealMethod();}).when(work);
        var method=Arrays.stream(ScopedDialogAiWorkbenchService.class.getMethods()).filter(m->m.getName().equals(read)).findFirst().orElseThrow();
        method.invoke(pending,read.equals("resolve")?eq(PUBLIC):eq(SCOPE),any(method.getParameterTypes()[1]));
        if(read.equals("resolve"))mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
        else mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(model().attribute("messages",empty())).andExpect(content().string(not(containsString("PRIVATE_R210_ANSWER"))));
        assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"VIEWER","ADMIN","ALL_VIEWER"})
    void validReadRolesKeepCanonicalOwnerAndTextOnlyMessages(String role)throws Exception {
        if(role.equals("ALL_VIEWER")){jdbc.update("DELETE FROM business_memberships WHERE user_id=7");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(7,'ALL_BUSINESSES','VIEWER')");}
        else jdbc.update("UPDATE business_memberships SET role_code=? WHERE user_id=7",role);
        var session=login();var rendered=mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(model().attribute("publicId",PUBLIC)).andExpect(model().attribute("businessId",11L)).andExpect(model().attribute("messages",hasSize(2))).andReturn();
        var messages=(List<?>)rendered.getModelAndView().getModel().get("messages");
        assertThat(messages).extracting(m->((ScopedDialogAiWorkbenchService.Message)m).id()).containsExactly(41L,40L);
        for(Object message:messages)assertThat(((ScopedDialogAiWorkbenchService.Message)message).fingerprint()).matches("[a-f0-9]{64}");
    }
    @ParameterizedTest @ValueSource(strings={"page","anonymous","flag"})
    void pageAuthenticationAndFeatureGatesRemainRequired(String kind)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(kind.equals("page")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='PAGE_DIALOGS'");mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());}
        else if(kind.equals("flag")){flags.setUiSelector(false);mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());}
        else mvc.perform(get(PATH)).andExpect(redirectedUrl("http://localhost/login"));
        assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void absentKnowledgePageRightSkipsMessageRead()throws Exception {
        var session=login();jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority='PAGE_KNOWLEDGE_BASE'");
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk()).andExpect(model().attribute("messages",empty()));
        verify(work,never()).messages(eq(SCOPE),any());
    }
}

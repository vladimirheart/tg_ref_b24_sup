package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.ScopedAiSourceController;
import com.example.panel.service.*;
import com.example.panel.support.AiMemoryIdentityFixture;
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
import static com.example.panel.support.AiControlIdentityFixture.auth;
import static com.example.panel.support.AiControlIdentityFixture.replace;
import static com.example.panel.support.AiMemoryIdentityFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login and R189 refresh. Revocation is injected before one particular source read. */
@SpringBootTest(classes=AiSourceReadIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AiSourceReadIdentityWebMvcTest {
    private static final String PATH="/business-ai-sources",PASSWORD="fixture source read R218";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean ScopedAiSourceService sources;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,ScopedAiSourceController.class,ScopedAiSourceService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){var f=new MultibusinessFeatureFlags();f.getFoundation().setEnabled(true);f.setUiSelector(true);return f;}
        @Bean InitializingBean aiSchema(JdbcTemplate jdbc){return ()->{AiMemoryIdentityFixture.schema(jdbc,true);jdbc.execute("ALTER TABLE businesses ADD COLUMN display_name TEXT DEFAULT 'Private business'");};}
        @Bean NavigationService navigation(){return mock(NavigationService.class);}
    }
    @BeforeEach void setup() {
        seed(jdbc,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");reset(shared);when(shared.loadSettings()).thenReturn(Map.of());jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,'PAGE_KNOWLEDGE_BASE')");flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
        assertThat(new ScopedAiSourceService(jdbc,flags).createKnowledge(11L,auth(7,"caller"),KEY,"PRIVATE Knowledge title","private reviewed answer")).isTrue();
    }
    private MockHttpSession login()throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        var signedIn=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username","CALLER").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();var session=(MockHttpSession)signedIn.getRequest().getSession(false);assertThat(session).isNotNull();return session;
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder page(String kind){
        var page=get(PATH+(kind.equals("new")?"/new":kind.equals("edit")?"/edit":"")).param("businessId","11");if(kind.equals("edit"))page.param("kind","KNOWLEDGE").param("key",KEY);return page;
    }
    private void beforeRead(String name,Runnable mutation,java.util.concurrent.atomic.AtomicBoolean fired)throws Exception {
        var pending=doAnswer(call->{if(fired.compareAndSet(false,true))mutation.run();return call.callRealMethod();}).when(sources);
        var method=Arrays.stream(ScopedAiSourceService.class.getMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();Object[] args=new Object[method.getParameterCount()];for(int i=0;i<args.length;i++){if(method.getParameterTypes()[i]==int.class)args[i]=anyInt();else args[i]=any();}method.invoke(pending,args);
    }
    static Stream<Arguments> late(){return Stream.of("rename-reuse","case-change").flatMap(c->Stream.of("availableBusinesses","canConfigure","catalog","find","history").map(r->Arguments.of(c,r)));}
    @ParameterizedTest @MethodSource("late")
    void eachControllerReadKeepsTheOriginalSessionIdentity(String change,String operation)throws Exception {
        var session=login();var before=snapshot(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();beforeRead(operation,()->replace(jdbc,change),fired);
        String target=operation.equals("canConfigure")?"new":operation.equals("find")||operation.equals("history")?"edit":"catalog";
        var response=mvc.perform(page(target).session(session)).andExpect(header().string("Cache-Control","no-store"));
        switch(operation){case "availableBusinesses","canConfigure"->response.andExpect(status().isForbidden());case "find"->response.andExpect(status().isNotFound());case "catalog"->response.andExpect(status().isOk()).andExpect(model().attribute("sources",org.hamcrest.Matchers.empty()));default->response.andExpect(status().isOk()).andExpect(model().attribute("history",org.hamcrest.Matchers.empty())).andExpect(model().attribute("sourceBody","private reviewed answer"));}
        // A prior authorized find may already have supplied the body; this is not a response-wide lock.
        assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> positives(){return Stream.of("caller","Caller").flatMap(n->Stream.of("catalog","new","edit").map(p->Arguments.of(n,p)));}
    @ParameterizedTest @MethodSource("positives")
    void realLoginReadsUsingTheStoredRawAccount(String name,String target)throws Exception {
        jdbc.update("UPDATE users SET username=? WHERE id=7",name);var session=login();var before=snapshot(jdbc);mvc.perform(page(target).session(session)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> gates(){return Stream.of("anonymous","page","flag").flatMap(g->Stream.of("catalog","new","edit").map(p->Arguments.of(g,p)));}
    @ParameterizedTest @MethodSource("gates")
    void anonymousPageAuthorityAndFeatureGateRemainRequired(String gate,String target)throws Exception {
        var session=login();var before=snapshot(jdbc);
        if(gate.equals("anonymous"))mvc.perform(page(target)).andExpect(redirectedUrl("http://localhost/login"));
        else if(gate.equals("page")){jdbc.update("DELETE FROM user_authorities WHERE user_id=7");mvc.perform(page(target).session(session)).andExpect(status().isForbidden());}
        else{flags.setUiSelector(false);mvc.perform(page(target).session(session)).andExpect(status().isNotFound());}
        assertThat(mockingDetails(sources).getInvocations()).noneMatch(i->List.of("availableBusinesses","canConfigure","catalog","find","history").contains(i.getMethod().getName()));assertThat(snapshot(jdbc)).isEqualTo(before);
    }
}

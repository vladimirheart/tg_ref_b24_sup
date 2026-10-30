package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.UiEventsController;
import com.example.panel.service.*;
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
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static com.example.panel.service.UiEventActorFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/R189 and an already-open servlet SSE response; no live Redis or working datasource. */
@SpringBootTest(classes=UiEventBoundRecipientWebMvcTest.Application.class,properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class UiEventBoundRecipientWebMvcTest {
    static final String PASSWORD="fixture bound SSE",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired UiEventStreamService stream;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,UiEventsController.class,UiEventStreamService.class,UiEventTicketAccessService.class,LegacyTicketIdJdbcGuard.class})
    static class Application {
        @Bean InitializingBean schema(JdbcTemplate jdbc){return ()->UiEventActorFixture.schema(jdbc,true);}
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean UiEventFanoutPublisher publisher(){return mock(UiEventFanoutPublisher.class);}
    }
    @SuppressWarnings("unchecked") void closeStreams(){var entries=(Map<Object,Set<SseEmitter>>)ReflectionTestUtils.getField(stream,"emittersByUser");for(var value:new ArrayList<>(entries.values()))for(var emitter:value)emitter.complete();entries.clear();}
    @BeforeEach void setup(){closeStreams();resetFixture(false);}
    @AfterEach void close(){closeStreams();}
    void resetFixture(boolean all){seed(jdbc,7,"caller",HASH,all);jdbc.execute("TRUNCATE user_authorities,roles,settings_parameters");jdbc.update("INSERT INTO user_authorities VALUES(7,'PAGE_DIALOGS'),(55,'PAGE_DIALOGS')");}
    MockHttpSession login()throws Exception{var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();var result=mvc.perform(post("/login").cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token.group(1)).param("username","caller").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    org.springframework.test.web.servlet.MvcResult open()throws Exception{return mvc.perform(get("/api/events/stream").session(login())).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();}
    String body(org.springframework.test.web.servlet.MvcResult result)throws Exception{return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);}
    @ParameterizedTest @ValueSource(booleans={false,true}) void realLoginReceivesItsBusinessEvent(boolean all)throws Exception{resetFixture(all);var socket=open();stream.publishDialogsChanged("private R233",RAW);assertThat(body(socket)).contains("private R233");}
    static Stream<Arguments> late(){return Stream.of("rename-reuse","delete-reuse","case","blocked","duplicate").flatMap(mode->Stream.of(false,true).map(all->Arguments.of(mode,all)));}
    @ParameterizedTest @MethodSource("late") void openConnectionCannotFollowChangedAccount(String mode,boolean all)throws Exception{resetFixture(all);var socket=open();change(jdbc,mode);stream.publishDialogsChanged("private R233",RAW);assertThat(body(socket)).doesNotContain("private R233");}
    @Test void newLoginAfterReuseDoesNotAuthorizeOldResponse()throws Exception{var old=open();change(jdbc,"rename-reuse");var current=open();stream.publishDialogsChanged("private R233",RAW);assertThat(body(old)).doesNotContain("private R233");assertThat(body(current)).contains("private R233");}
    @Test void anonymousStillRequiresLogin()throws Exception{mvc.perform(get("/api/events/stream")).andExpect(redirectedUrl("http://localhost/login"));}
}

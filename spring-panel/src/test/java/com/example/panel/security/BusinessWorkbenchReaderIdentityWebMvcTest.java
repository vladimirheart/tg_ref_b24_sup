package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessTicketAssignmentController;
import com.example.panel.service.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static com.example.panel.support.BusinessWorkbenchFixture.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login and R189 refresh, then mutation at each of the nine individual SQL read boundaries. */
@SpringBootTest(classes=BusinessWorkbenchReaderIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessWorkbenchReaderIdentityWebMvcTest {
    enum Api { ASSIGNMENT("state"),ASSIGNEES("candidates"),ASSIGNMENT_EVENTS("events"),PARTICIPANT_REVISION("participantRevision"),MEMBERS("members"),MEMBER_CANDIDATES("memberCandidates"),PARTICIPANT_EVENTS("participantEvents"),WORK("work"),WORK_EVENTS("workEvents");
        final String attribute;Api(String attribute){this.attribute=attribute;}}
    private static final String URL="/business-dialog-assignment/"+PUBLIC,PASSWORD="fixture workbench R206";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired SharedConfigService shared;@Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessTicketAssignmentService assignments;@SpyBean BusinessTicketParticipantService participants;@SpyBean BusinessTicketWorkService work;
    @MockBean NavigationService navigation;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessTicketAssignmentController.class,BusinessTicketAssignmentService.class,BusinessTicketParticipantService.class,BusinessTicketWorkService.class,UiEventOutboxAppendService.class,LegacyTicketIdJdbcGuard.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean InitializingBean workbenchSchema(JdbcTemplate jdbc){return ()->schema(jdbc,true);}
    }
    @BeforeEach void fixture(){
        jdbc.execute("TRUNCATE user_authorities,roles,settings_parameters");seed(jdbc," reader ",HASH);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'ROLE_USER'),(2,'PAGE_DIALOGS')");
        when(shared.loadSettings()).thenReturn(Map.of());flags.getFoundation().setEnabled(true);flags.setUiSelector(true);
    }
    private MockHttpSession login()throws Exception{
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(matcher.find()).isTrue();
        return (MockHttpSession)mvc.perform(post("/login").cookie(cookie).param("_csrf",matcher.group(1)).param("username"," READER ").param("password",PASSWORD))
            .andExpect(redirectedUrl("/post-login")).andReturn().getRequest().getSession(false);
    }
    @Test void exactBoundOperatorGetsAllNinePopulatedFieldsAndPrivateSnapshots()throws Exception{
        var before=snapshot(jdbc);var result=mvc.perform(get(URL).session(login())).andExpect(status().isOk()).andExpect(view().name("businesses/dialog-assignment"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff")).andReturn();
        var model=result.getModelAndView().getModel();for(Api api:Api.values()){assertThat(model.get(api.attribute)).as(api.name()).isNotNull();if(model.get(api.attribute) instanceof List<?> values)assertThat(values).isNotEmpty();}
        var state=(BusinessTicketAssignmentService.State)model.get("state");assertThat(state.scope()).isEqualTo(new UiEventTicketScope(OWNER,RAW,PUBLIC,"BUSINESS",11L));assertThat(state.responsible()).isEqualTo(" private assignee ");
        assertThat(result.getResponse().getContentAsString()).contains("private assignee","private member","private work actor");assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> replacements(){return Stream.of(Api.values()).flatMap(api->Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership").map(mode->Arguments.of(api,mode)));}
    @ParameterizedTest @MethodSource("replacements") void laterReadCannotUseAReplacementAfterSuccessfulSessionRefresh(Api api,String mode)throws Exception{
        var session=login();var before=snapshot(jdbc);var fired=new AtomicBoolean();
        Answer<Object> mutate=call->{if(fired.compareAndSet(false,true))replace(jdbc,mode," reader ");return call.callRealMethod();};
        switch(api){
            case ASSIGNMENT->doAnswer(mutate).when(assignments).find(eq(PUBLIC),any());case ASSIGNEES->doAnswer(mutate).when(assignments).candidates(any(),any());case ASSIGNMENT_EVENTS->doAnswer(mutate).when(assignments).recent(any(),any());
            case PARTICIPANT_REVISION->doAnswer(mutate).when(participants).revision(any(),any());case MEMBERS->doAnswer(mutate).when(participants).members(any(),any());case MEMBER_CANDIDATES->doAnswer(mutate).when(participants).candidates(any(),any());case PARTICIPANT_EVENTS->doAnswer(mutate).when(participants).recent(any(),any());
            case WORK->doAnswer(mutate).when(work).find(eq(PUBLIC),any());case WORK_EVENTS->doAnswer(mutate).when(work).recent(any(),any());
        }
        var result=mvc.perform(get(URL).session(session)).andExpect(header().string("Cache-Control","no-store"));
        if(api==Api.ASSIGNMENT)result.andExpect(status().isNotFound());
        else{var model=result.andExpect(status().isOk()).andReturn().getModelAndView().getModel();
            if(api==Api.PARTICIPANT_REVISION||api==Api.WORK)assertThat(model.get(api.attribute)).as(api.name()).isNull();else assertThat((List<?>)model.get(api.attribute)).as(api.name()).isEmpty();
            if(api==Api.WORK)assertThat((List<?>)model.get("workEvents")).isEmpty();
        }
        assertThat(fired).isTrue();assertThat(snapshot(jdbc)).isEqualTo(before);
    }
    @Test void pageRevokeFoundationSelectorAndAnonymousStayProtected()throws Exception{
        var session=login();jdbc.update("DELETE FROM user_authorities WHERE authority='PAGE_DIALOGS'");mvc.perform(get(URL).session(session)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(false);mvc.perform(get(URL).session(session)).andExpect(status().isNotFound());
        flags.getFoundation().setEnabled(true);flags.setUiSelector(false);mvc.perform(get(URL).session(session)).andExpect(status().isNotFound());mvc.perform(get(URL)).andExpect(redirectedUrl("http://localhost/login"));
    }
}

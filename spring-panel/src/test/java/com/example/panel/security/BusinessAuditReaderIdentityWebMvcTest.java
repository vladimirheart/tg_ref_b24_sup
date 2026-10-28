package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessTicketAssignmentReadController;
import com.example.panel.controller.BusinessTicketParticipantReadController;
import com.example.panel.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/refresh and account mutations immediately before each reader's SQL, not before the filter. */
@SpringBootTest(classes=BusinessAuditReaderIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessAuditReaderIdentityWebMvcTest {
    enum Api { ASSIGNMENTS("/assignments/history"),ROSTER("/participants"),PARTICIPANTS("/participants/history");
        final String path;Api(String suffix){path="/api/business-scope/tickets/"+PUBLIC+suffix;}}
    private static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ",PASSWORD="fixture audit R205";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired SharedConfigService shared;@Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessTicketAssignmentReadService assignments;@SpyBean BusinessTicketParticipantReadService participants;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessTicketAssignmentReadController.class,BusinessTicketParticipantReadController.class,BusinessTicketAssignmentReadService.class,BusinessTicketParticipantReadService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean InitializingBean schema(JdbcTemplate jdbc){return ()->{
            jdbc.execute("""
                CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
                CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                    channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
                CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
                """);
            for(String migration:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql","V79__business_ticket_participants.sql"))
                jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
        };}
    }
    @BeforeEach void fixture(){
        jdbc.execute("TRUNCATE users,user_authorities,roles,settings_parameters,businesses,business_memberships,business_access_grants,tickets,ticket_responsibles,business_ticket_assignment_events,scoped_sla_assignment_events,business_ticket_participants,business_ticket_participant_events");
        when(shared.loadSettings()).thenReturn(Map.of());flags.getFoundation().setEnabled(true);
        jdbc.update("INSERT INTO users(id,username,password) VALUES(2,' reader ',?)",HASH);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'ROLE_USER'),(2,'PAGE_DIALOGS')");
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(9007199254740993,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(9007199254740993,?,?,11,1,9223372036854775807,' old target ',2,' old actor ','assign')",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action) VALUES(9007199254740993,?,?,11,1,9223372036854775807,' old member ',2,' old actor ','add')",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor) VALUES(9007199254740993,?,?,11,1,9223372036854775807,' old member ',2,' old actor ')",RAW,PUBLIC);
    }
    private MockHttpSession login()throws Exception{
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(matcher.find()).isTrue();
        return (MockHttpSession)mvc.perform(post("/login").cookie(cookie).param("_csrf",matcher.group(1)).param("username"," READER ").param("password",PASSWORD))
            .andExpect(redirectedUrl("/post-login")).andReturn().getRequest().getSession(false);
    }
    @ParameterizedTest @EnumSource(Api.class) void exactBoundViewerGetsPrivateOwnerAndSnapshot(Api api)throws Exception{
        var result=mvc.perform(get(api.path).session(login())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
            .andExpect(jsonPath("$.owner.ticketId").value(RAW));
        if(api==Api.ROSTER)result.andExpect(jsonPath("$.members[0].id").value(Long.toString(Long.MAX_VALUE))).andExpect(jsonPath("$.members[0].snapshot").value(" old member "));
        else result.andExpect(jsonPath("$.events[0].actor").value(" old actor "));
    }
    static Stream<Arguments> changes(){return Stream.of(Api.values()).flatMap(api->Stream.of("rename-reuse","delete-reuse","case-change","disabled","blocked","membership","business-transfer").map(change->Arguments.of(api,change)));}
    @ParameterizedTest @MethodSource("changes") void sqlCannotRebindReaderAfterSessionRefresh(Api api,String change)throws Exception{
        var session=login();var roster=jdbc.queryForList("SELECT * FROM business_ticket_participants");var events=jdbc.queryForList("SELECT * FROM business_ticket_assignment_events");
        Answer<Object> mutation=call->{switch(change){
            case "rename-reuse","delete-reuse"->{if(change.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=2");else jdbc.update("DELETE FROM users WHERE id=2");jdbc.update("INSERT INTO users(id,username,password) VALUES(4,' reader ',?)",HASH);jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(4,11,'ADMIN')");}
            case "case-change"->jdbc.update("UPDATE users SET username=' READER ' WHERE id=2");case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "membership"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");default->jdbc.update("UPDATE tickets SET business_id=22");}return call.callRealMethod();};
        switch(api){case ASSIGNMENTS->doAnswer(mutation).when(assignments).history(eq(PUBLIC),any(),isNull(),eq(50));case ROSTER->doAnswer(mutation).when(participants).find(eq(PUBLIC),any());case PARTICIPANTS->doAnswer(mutation).when(participants).history(eq(PUBLIC),any(),isNull(),eq(50));}
        mvc.perform(get(api.path).session(session)).andExpect(status().isNotFound());
        switch(api){case ASSIGNMENTS->verify(assignments).history(eq(PUBLIC),any(),isNull(),eq(50));case ROSTER->verify(participants).find(eq(PUBLIC),any());case PARTICIPANTS->verify(participants).history(eq(PUBLIC),any(),isNull(),eq(50));}
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants")).isEqualTo(roster);assertThat(jdbc.queryForList("SELECT * FROM business_ticket_assignment_events")).isEqualTo(events);
    }
    @ParameterizedTest @EnumSource(Api.class) void pageRevokeFoundationAndAnonymousRemainProtected(Api api)throws Exception{
        var session=login();jdbc.update("DELETE FROM user_authorities WHERE authority='PAGE_DIALOGS'");mvc.perform(get(api.path).session(session)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(false);mvc.perform(get(api.path).session(session)).andExpect(status().isNotFound());mvc.perform(get(api.path)).andExpect(redirectedUrl("http://localhost/login"));
    }
}

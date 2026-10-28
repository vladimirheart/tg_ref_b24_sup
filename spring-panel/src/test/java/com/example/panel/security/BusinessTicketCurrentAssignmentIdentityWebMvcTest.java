package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessTicketCurrentAssignmentReadController;
import com.example.panel.service.BusinessTicketCurrentAssignmentReadService;
import com.example.panel.service.SharedConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login, session refresh, PostgreSQL owner/read policy and current-assignment endpoint. */
@SpringBootTest(classes=BusinessTicketCurrentAssignmentIdentityWebMvcTest.Application.class,
        properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessTicketCurrentAssignmentIdentityWebMvcTest {
    private static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ",PASSWORD="fixture R199";
    private static final String PATH="/api/business-scope/tickets/"+PUBLIC+"/assignments/current";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessTicketCurrentAssignmentReadController.class,
            BusinessTicketCurrentAssignmentReadService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean InitializingBean assignmentSchema(JdbcTemplate jdbc){return ()->{
            jdbc.execute("""
                CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
                CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id));
                CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
                """);
            for(String file:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql"))
                jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",file)));
        };}
    }
    @BeforeEach void fixture(){
        jdbc.execute("TRUNCATE users,user_authorities,roles,settings_parameters,businesses,business_memberships,business_access_grants,tickets,ticket_responsibles,business_ticket_assignment_events,scoped_sla_assignment_events");
        when(shared.loadSettings()).thenReturn(Map.of());flags.getFoundation().setEnabled(true);
        jdbc.update("INSERT INTO users(id,username,password) VALUES(2,' viewer ',?),(3,'foreign',?)",HASH,HASH);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'ROLE_USER'),(2,'PAGE_DIALOGS'),(3,'ROLE_ADMIN'),(3,'PAGE_DIALOGS')");
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'VIEWER'),(3,22,'ADMIN')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(9007199254740993,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by,assigned_at) VALUES(?,' target ','private actor',clock_timestamp())",RAW);
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(9007199254740993,?,?,11,1,9223372036854775807,' target ',2,'private actor','assign')",RAW,PUBLIC);
    }
    private MockHttpSession login(String name) throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",matcher.group(1)).param("username",name).param("password",PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    @Test void realViewerSessionReadsLiveProofWithExactOwnerIdsAndNoWrite() throws Exception {
        var before=jdbc.queryForList("SELECT * FROM ticket_responsibles");
        mvc.perform(get(PATH).session(login(" VIEWER "))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.owner.userId").value("9007199254740993"))
                .andExpect(jsonPath("$.owner.ticketId").value(RAW))
                .andExpect(jsonPath("$.revision").value("1")).andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee.id").value(Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.assignee.username").value(" target "));
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_ticket_assignment_events",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","deleted","renamed"})
    void revokedAccountSessionsCannotReadCurrentAssignment(String mode) throws Exception {
        var session=login(" viewer ");
        switch(mode){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");case "deleted"->jdbc.update("DELETE FROM users WHERE id=2");default->jdbc.update("UPDATE users SET username='renamed' WHERE id=2");}
        mvc.perform(get(PATH).session(session)).andExpect(redirectedUrl("http://localhost/login"));
    }
    @Test void businessRevokeDoesNotReuseAnOldAuthenticatedSession() throws Exception {
        var session=login(" viewer ");jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());
    }
    @Test void ownerTransferDoesNotFollowTheOldBusinessOrClientParameters() throws Exception {
        var session=login(" viewer ");jdbc.update("UPDATE tickets SET business_id=22");
        mvc.perform(get(PATH).param("businessId","11").param("readerId","3").session(session)).andExpect(status().isNotFound());
    }
    @Test void globalAdminIsNotBusinessReadButAllBusinessViewerIs() throws Exception {
        var session=login("foreign");
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER')");
        mvc.perform(get(PATH).session(session)).andExpect(status().isOk());
    }
    @Test void pageAuthorityRevokeIsEffectiveBeforeControllerRead() throws Exception {
        var session=login(" viewer ");jdbc.update("DELETE FROM user_authorities WHERE user_id=2 AND authority='PAGE_DIALOGS'");
        mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
    }
    @Test void liveRowWithoutMatchingProvenanceCannotBecomeCurrentByHttp() throws Exception {
        jdbc.update("UPDATE ticket_responsibles SET responsible='private legacy replacement'");
        mvc.perform(get(PATH).session(login(" viewer "))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNVERIFIED")).andExpect(jsonPath("$.assignee").isEmpty());
    }
    @Test void queryFailureIs503RatherThanUnassigned() throws Exception {
        var session=login(" viewer ");
        jdbc.execute("ALTER TABLE ticket_responsibles RENAME COLUMN responsible TO unavailable");
        try {mvc.perform(get(PATH).session(session)).andExpect(status().isServiceUnavailable());}
        finally {jdbc.execute("ALTER TABLE ticket_responsibles RENAME COLUMN unavailable TO responsible");}
    }
    @Test void disabledFoundationHidesTheEndpointAndAnonymousIsNotAdmitted() throws Exception {
        flags.getFoundation().setEnabled(false);
        mvc.perform(get(PATH).session(login(" viewer "))).andExpect(status().isNotFound());
        mvc.perform(get(PATH)).andExpect(redirectedUrl("http://localhost/login"));
    }
}

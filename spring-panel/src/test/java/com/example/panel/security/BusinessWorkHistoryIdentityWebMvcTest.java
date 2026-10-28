package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.controller.BusinessTicketWorkReadController;
import com.example.panel.service.BusinessTicketWorkReadService;
import com.example.panel.service.SharedConfigService;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login/session refresh; account changes injected after the filter, before the read SQL. */
@SpringBootTest(classes=BusinessWorkHistoryIdentityWebMvcTest.Application.class,
        properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class BusinessWorkHistoryIdentityWebMvcTest {
    private static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ",PASSWORD="fixture history R204";
    private static final String PATH="/api/business-scope/tickets/"+PUBLIC+"/work/history";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SharedConfigService shared;
    @Autowired MultibusinessFeatureFlags flags;
    @SpyBean BusinessTicketWorkReadService service;
    @Configuration(proxyBeanMethods=false)
    @Import({PreferenceIdentityWebMvcTest.Application.class,BusinessTicketWorkReadController.class,BusinessTicketWorkReadService.class})
    static class Application {
        @Bean MultibusinessFeatureFlags flags(){return new MultibusinessFeatureFlags();}
        @Bean InitializingBean historySchema(JdbcTemplate jdbc){return ()->{
            jdbc.execute("""
                CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
                CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
                CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                    channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
                """);
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        };}
    }
    @BeforeEach void fixture(){
        jdbc.execute("TRUNCATE users,user_authorities,roles,settings_parameters,businesses,business_memberships,business_access_grants,tickets,business_ticket_work_events");
        when(shared.loadSettings()).thenReturn(Map.of());flags.getFoundation().setEnabled(true);
        jdbc.update("INSERT INTO users(id,username,password) VALUES(2,' reader ',?)",HASH);
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'ROLE_USER'),(2,'PAGE_DIALOGS')");
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(9007199254740993,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,from_status,to_status,previous_categories,categories,actor_user_id,actor) VALUES(9007199254740993,?,?,11,1,'close','open','resolved','[]','[\"Private\"]',9223372036854775807,' old actor ')",RAW,PUBLIC);
    }
    private MockHttpSession login() throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",matcher.group(1)).param("username"," READER ").param("password",PASSWORD))
                .andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    @Test void boundViewerReadsExactOwnerSnapshotsAndPrivateResponse() throws Exception {
        mvc.perform(get(PATH).session(login())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(jsonPath("$.owner.userId").value("9007199254740993")).andExpect(jsonPath("$.owner.ticketId").value(RAW))
            .andExpect(jsonPath("$.events[0].actorId").value(Long.toString(Long.MAX_VALUE)))
            .andExpect(jsonPath("$.events[0].actor").value(" old actor "));
    }
    @ParameterizedTest @ValueSource(strings={"rename-reuse","delete-reuse","case-change","disabled","blocked","membership","business-transfer"})
    void changesAfterSessionRefreshCannotRebindTheReader(String change) throws Exception {
        var session=login();
        var before=jdbc.queryForList("SELECT * FROM business_ticket_work_events");
        doAnswer(call->{
            switch(change){
                case "rename-reuse","delete-reuse" -> {
                    if(change.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=2");else jdbc.update("DELETE FROM users WHERE id=2");
                    jdbc.update("INSERT INTO users(id,username,password) VALUES(4,' reader ',?)",HASH);
                    jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(4,11,'ADMIN')");
                }
                case "case-change" -> jdbc.update("UPDATE users SET username=' READER ' WHERE id=2");
                case "disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
                case "blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
                case "membership" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
                default -> jdbc.update("UPDATE tickets SET business_id=22");
            }
            return call.callRealMethod();
        }).when(service).history(eq(PUBLIC),any(),isNull(),eq(50));
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());
        verify(service).history(eq(PUBLIC),any(),isNull(),eq(50));
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_work_events")).isEqualTo(before);
    }
    @Test void revokedPageAndDisabledFoundationAndAnonymousStayProtected() throws Exception {
        var session=login();jdbc.update("DELETE FROM user_authorities WHERE authority='PAGE_DIALOGS'");
        mvc.perform(get(PATH).session(session)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(2,'PAGE_DIALOGS')");flags.getFoundation().setEnabled(false);
        mvc.perform(get(PATH).session(session)).andExpect(status().isNotFound());
        mvc.perform(get(PATH)).andExpect(redirectedUrl("http://localhost/login"));
    }
}

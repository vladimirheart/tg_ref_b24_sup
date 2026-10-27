package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.config.RestExceptionHandler;
import com.example.panel.controller.LoginRedirectController;
import com.example.panel.controller.WorkforceCheckInController;
import com.example.panel.service.ChannelTransportService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.WorkforceService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/session/CSRF and workforce orchestration; no working database or provider calls. */
@SpringBootTest(classes=WorkforceIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class WorkforceIdentityWebMvcTest {
    private static final String PASSWORD="fixture workforce", HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private final Set<String> sessions=new HashSet<>();
    private final List<Long> insertedUsers=new ArrayList<>();
    private String readRace="", insertRace="";
    private boolean unavailable;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChannelTransportService transport;
    @Autowired WorkforceCheckInController controller;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,MvcConfig.class,RestExceptionHandler.class,
        LoginRedirectController.class,WorkforceCheckInController.class,PermissionService.class,WorkforceService.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc(){var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;}
        @Bean ChannelTransportService transport(){return mock(ChannelTransportService.class);}
    }

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        accounts.clear();sessions.clear();insertedUsers.clear();readRace="";insertRace="";unavailable=false;
        reset(jdbc,transport);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"),anyLong())).thenAnswer(c -> {
            var row=accounts.get(c.<Long>getArgument(1));
            if(row==null)throw new EmptyResultDataAccessException(1);
            return new LinkedHashMap<>(row);
        });
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c -> {
            String sql=c.getArgument(0);
            if(sql.equals("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2"))
                return accounts.values().stream().filter(r -> r.get("username").toString().equalsIgnoreCase(c.getArgument(1)))
                    .limit(2).map(LinkedHashMap::new).toList();
            if(sql.contains("FROM users u")&&sql.contains("workforce_user_settings")) {
                if(unavailable)throw new DataAccessResourceFailureException("fixture workforce unavailable");
                applyRace(readRace);readRace="";
                List<Map<String,Object>> rows=sql.contains("WHERE u.id = ?")
                    ? accounts.values().stream().filter(r -> r.get("id").equals(c.getArgument(1))).toList()
                    : accounts.values().stream().filter(r -> r.get("username").toString().equalsIgnoreCase(c.getArgument(1))).limit(1).toList();
                return rows.stream().map(r -> {var cfg=new LinkedHashMap<>(r);cfg.put("user_id",r.get("id"));
                    cfg.put("account_enabled",r.get("enabled"));cfg.put("account_blocked",r.get("is_blocked"));
                    cfg.put("enabled",r.get("workforce"));cfg.put("time_zone","UTC");
                    cfg.put("notify_on_check_in_override",true);cfg.put("notification_channel_id_override",11L);
                    cfg.put("notification_target_override","support_chat");return cfg;}).toList();
            }
            if(sql.contains("FROM workforce_shift_sessions")&&sessions.contains(c.getArgument(1)))
                return List.of(Map.of("checked_in_at",OffsetDateTime.now(),"check_in_status","unscheduled","notification_status","sent"));
            return List.of();
        });
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenReturn(List.of("PAGE_DIALOGS"));
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Long>>any(),any(Object[].class))).thenAnswer(c -> {
            String sql=c.getArgument(0);
            if(!sql.contains("INSERT INTO workforce_shift_sessions"))return List.of("PAGE_DIALOGS");
            applyRace(insertRace);insertRace="";
            long id=c.getArgument(3);String key=c.getArgument(2);
            if(sql.contains("FROM users u")) {
                Object[] args=c.getArguments();var row=accounts.get(id);
                if(row==null||!row.get("username").equals(args[args.length-1])||!Boolean.TRUE.equals(row.get("enabled"))
                        ||Boolean.TRUE.equals(row.get("is_blocked"))||!Boolean.TRUE.equals(row.get("workforce")))return List.of();
            }
            if(!sessions.add(key))return List.of();
            insertedUsers.add(id);return List.of(101L);
        });
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),anyLong())).thenReturn(true);
        when(transport.resolveConfiguredRecipient(anyLong(),anyString(),nullable(String.class))).thenReturn("-100fixture");
        when(transport.sendConfiguredMessage(anyLong(),anyString(),nullable(String.class),anyString()))
            .thenReturn(new ChannelTransportService.ConfiguredMessageDeliveryResult(true,"-100fixture",null));
    }
    private void account(long id,String username) {
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",username);row.put("password",HASH);
        row.put("enabled",true);row.put("is_blocked",false);row.put("workforce",true);row.put("full_name","Employee "+id);
        accounts.put(id,row);
    }
    private void applyRace(String race) {
        var own=accounts.get(7L);if(own==null)return;
        switch(race){case "rename" -> own.put("username","changed");case "case-rename" -> own.put("username","READER");
            case "disabled" -> own.put("enabled",false);case "blocked" -> own.put("is_blocked",true);
            case "workforce-disabled" -> own.put("workforce",false);case "deleted" -> accounts.remove(7L);default -> {}}
    }
    private MockHttpSession login(String username) throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue();Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username",username).param("password",PASSWORD))
            .andExpect(redirectedUrl("/post-login")).andReturn();
        return (MockHttpSession)result.getRequest().getSession(false);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postWithCsrf(MockHttpSession session) throws Exception {
        var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        return post("/shift-check-in").session(session).cookie(cookie).param("_csrf",token.group(1));
    }
    private void confirm(MockHttpSession session) throws Exception {
        mvc.perform(postWithCsrf(session)).andExpect(redirectedUrl("/post-login"));
    }
    private void noWrites(){assertThat(insertedUsers).isEmpty();verifyNoInteractions(transport);}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void collisionAfterLoginCannotReadOrConfirmTheNeighboursShift(boolean neighbourFirst) throws Exception {
        account(7L,"reader");var session=login("reader");var own=accounts.remove(7L);
        if(!neighbourFirst)accounts.put(7L,own);account(99L,"ReAdEr");if(neighbourFirst)accounts.put(7L,own);
        mvc.perform(get("/shift-check-in").session(session)).andExpect(status().isOk())
            .andExpect(result -> assertThat(((WorkforceService.CheckInContext)result.getModelAndView().getModel().get("checkIn")).userId()).isEqualTo(7L));
        confirm(session);confirm(session);assertThat(insertedUsers).containsExactly(7L);
        verify(transport,times(1)).sendConfiguredMessage(eq(11L),eq("support_chat"),isNull(),contains("Employee 7"));
    }
    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr"," reader ","имя"})
    void exactStoredAccountAndRawLegacyLoginAreRetained(String name) throws Exception {
        account(7L,name);var session=login(name);
        mvc.perform(get("/shift-check-in").session(session)).andExpect(status().isOk())
            .andExpect(result -> assertThat(((WorkforceService.CheckInContext)result.getModelAndView().getModel().get("checkIn")).username()).isEqualTo(name));
        confirm(session);assertThat(insertedUsers).containsExactly(7L);
    }
    @ParameterizedTest @ValueSource(longs={9007199254740993L,Long.MAX_VALUE})
    void largeAccountIdsReachTheInsertWithoutPrecisionLoss(long id) throws Exception {
        account(id,"reader");confirm(login("reader"));assertThat(insertedUsers).containsExactly(id);
    }
    @ParameterizedTest @ValueSource(strings={"rename","case-rename","disabled","blocked","deleted"})
    void aChangedAccountAfterSessionRefreshCannotLoadOrWriteAnotherAccount(String change) throws Exception {
        account(7L,"reader");var session=login("reader");account(99L,"ReAdEr");readRace=change;
        mvc.perform(postWithCsrf(session)).andExpect(status().isForbidden());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"rename","case-rename","disabled","blocked","deleted"})
    void aChangedAccountBeforeInsertCannotCreateAShiftOrSendNotification(String change) throws Exception {
        account(7L,"reader");var session=login("reader");insertRace=change;
        mvc.perform(postWithCsrf(session)).andExpect(status().isForbidden());noWrites();
    }
    @Test void disabledWorkforceBeforeInsertDoesNotCreateAShiftOrNotify() throws Exception {
        account(7L,"reader");var session=login("reader");insertRace="workforce-disabled";confirm(session);noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"rename","disabled","blocked","deleted"})
    void revokedSessionStopsBeforeWorkforceWrites(String change) throws Exception {
        account(7L,"reader");var session=login("reader");applyRace(change);
        mvc.perform(postWithCsrf(session)).andExpect(status().is3xxRedirection());noWrites();
    }
    @ParameterizedTest @ValueSource(strings={"legacy","zero","negative","unknown","blank","absent","anonymous","unauthenticated","disabled-principal"})
    void directControllerCannotResolveAnUnboundActorByName(String mode) {
        account(7L,"reader");
        Object principal=mode.equals("legacy")?User.withUsername("reader").password("fixture").roles("USER").build():
            new PanelUserPrincipal(mode.equals("zero")?0:mode.equals("negative")?-1:mode.equals("unknown")?999:7,
                mode.equals("blank")?" ":"reader","",!mode.equals("disabled-principal"),AuthorityUtils.NO_AUTHORITIES);
        org.springframework.security.core.Authentication actor=switch(mode) {
            case "absent" -> null;
            case "anonymous" -> new org.springframework.security.authentication.AnonymousAuthenticationToken("fixture",principal,AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
            case "unauthenticated" -> UsernamePasswordAuthenticationToken.unauthenticated(principal,null);
            default -> UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
        };
        assertThatThrownBy(() -> controller.checkInPage(actor,new ExtendedModelMap())).isInstanceOf(AccessDeniedException.class);noWrites();
    }
    @Test void workforceReadFailureCannotSilentlyBypassCheckInRouting() throws Exception {
        account(7L,"reader");var session=login("reader");unavailable=true;
        mvc.perform(get("/post-login").session(session)).andExpect(status().isServiceUnavailable());noWrites();
    }
    @Test void workforceRemainsOptInAndNormalPageRoutingIsPreserved() throws Exception {
        account(7L,"reader");accounts.get(7L).put("workforce",false);var session=login("reader");
        mvc.perform(get("/post-login").session(session)).andExpect(redirectedUrl("/"));confirm(session);noWrites();
    }
    @Test void pendingShiftStillPrecedesNormalPageRouting() throws Exception {
        account(7L,"reader");mvc.perform(get("/post-login").session(login("reader"))).andExpect(redirectedUrl("/shift-check-in"));noWrites();
    }
    @Test void csrfAndAnonymousGuardsStopBeforeWorkforceWrites() throws Exception {
        account(7L,"reader");var session=login("reader");
        mvc.perform(post("/shift-check-in").session(session)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post("/shift-check-in").session(session).cookie(new Cookie("XSRF-TOKEN","fixture")).param("_csrf","invalid"))
            .andExpect(redirectedUrl("/error/403"));
        mvc.perform(get("/shift-check-in")).andExpect(status().is3xxRedirection());noWrites();
    }
}


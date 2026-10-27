package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.AuthApiController;
import com.example.panel.controller.ChannelApiController;
import com.example.panel.service.ChannelTransportService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.SerializationUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Login -> mutate mocked DB rows -> same session -> real channel method-security boundary. */
@SpringBootTest(classes=SessionAuthorityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class SessionAuthorityWebMvcTest {
    private static final String USER_SQL="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2";
    private static final String ID_SQL="SELECT * FROM users WHERE id = ?";
    private static final String AUTH_SQL="SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE_SQL="SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String CONTEXT="SPRING_SECURITY_CONTEXT",PASSWORD="fixture password";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> users=new LinkedHashMap<>();
    private final Map<Long,List<String>> rights=new LinkedHashMap<>();
    private final Map<Long,List<Map<String,Object>>> roles=new LinkedHashMap<>();
    private String failing="";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChannelTransportService transport;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,AuthApiController.class,ChannelApiController.class,MvcConfig.class,FullAuthenticationProbe.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc() {
            var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;
        }
        @Bean ChannelTransportService fixtureTransport() { return mock(ChannelTransportService.class); }
    }
    @RestController static class FullAuthenticationProbe {
        @GetMapping("/api/session-test/full-only") @PreAuthorize("isFullyAuthenticated()")
        Map<String,Object> fullOnly() { return Map.of("success",true); }
    }

    @BeforeEach void fixtures() {
        users.clear();rights.clear();roles.clear();failing="";reset(jdbc,transport);
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForList(eq(USER_SQL),anyString())).thenAnswer(call -> users.values().stream()
            .filter(row -> row.get("username").toString().equalsIgnoreCase(call.getArgument(1))).limit(2).toList());
        when(jdbc.queryForMap(eq(ID_SQL),anyLong())).thenAnswer(call -> {
            failIf("user");var row=users.get(call.<Long>getArgument(1));
            if(row==null) throw new EmptyResultDataAccessException(1);return row;
        });
        when(jdbc.query(eq(AUTH_SQL),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong()))
            .thenAnswer(call -> {failIf("direct");return rights.getOrDefault(call.getArgument(2),List.of());});
        when(jdbc.queryForList(eq(ROLE_SQL),anyLong())).thenAnswer(call -> {failIf("role");return roles.getOrDefault(call.getArgument(1),List.of());});
        when(transport.getChannels()).thenReturn(ResponseEntity.ok(Map.of("success",true)));
        when(transport.createChannel(any())).thenReturn(ResponseEntity.ok(Map.of("success",true)));
    }
    private void failIf(String source) { if(failing.equals(source)) throw new DataAccessResourceFailureException("fixture unavailable"); }
    private void account(long id,String username,String... authorities) {
        users.put(id,new LinkedHashMap<>(Map.of("id",id,"username",username,"password",HASH,"enabled",true,"is_blocked",false)));
        rights.put(id,List.of(authorities));
    }
    private void admin() { account(7L," reader ","ROLE_ADMIN","PAGE_CHANNELS"); }
    private void role(String name,String pages) { roles.put(7L,List.of(Map.of("role_name",name,"permissions","{\"pages\":"+pages+"}"))); }
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue();Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        return request.cookie(cookie).param("_csrf",token.group(1));
    }
    private MvcResult login(String username,boolean remember) throws Exception {
        var request=csrf(post("/login")).param("username",username).param("password",PASSWORD);
        if(remember)request.param("remember-me","true");
        return mvc.perform(request).andExpect(redirectedUrl("/post-login")).andExpect(authenticated().withUsername(username)).andReturn();
    }
    private MockHttpSession session(MvcResult result) { return (MockHttpSession)result.getRequest().getSession(false); }
    private Authentication auth(MockHttpSession session) { return ((SecurityContext)session.getAttribute(CONTEXT)).getAuthentication(); }

    @ParameterizedTest @ValueSource(strings={"direct-admin","direct-page","role-admin","role-page"})
    void revokedRoleOrPageBlocksActualChannelWriteBeforeService(String mode) throws Exception {
        admin();if(mode.startsWith("role")){rights.put(7L,List.of());role("admin","[\"channels\"]");}
        var session=session(login(" reader ",false));
        var request=csrf(post("/api/channels")).session(session).contentType("application/json").content("{}");
        switch(mode){
            case "direct-admin" -> rights.put(7L,List.of("PAGE_CHANNELS"));
            case "direct-page" -> rights.put(7L,List.of("ROLE_ADMIN"));
            case "role-admin" -> role("operator","[\"channels\"]");
            default -> role("admin","[]");
        }
        mvc.perform(request).andExpect(status().isForbidden());verifyNoInteractions(transport);
    }

    @ParameterizedTest @ValueSource(strings={"direct","role"})
    void newlyGrantedRightsApplyWithoutAnotherLogin(String mode) throws Exception {
        account(7L," reader ","PAGE_DIALOGS");var session=session(login(" reader ",false));
        if(mode.equals("direct"))rights.put(7L,List.of("ROLE_ADMIN","PAGE_CHANNELS"));
        else{rights.put(7L,List.of());role("admin","[\"channels\"]");}
        mvc.perform(get("/api/channels").session(session)).andExpect(status().isOk());verify(transport).getChannels();
    }

    @ParameterizedTest @ValueSource(strings={"reader"," reader ","\treader\t","\nreader\r","mem\r\nber"})
    void whoamiAndPersistedContextReceiveOnlyCurrentRightsOfTheSameId(String username) throws Exception {
        account(7L,username,"PAGE_DIALOGS");account(99L,"neighbour","ROLE_ADMIN","PAGE_SETTINGS");
        var session=session(login(username,false));rights.put(7L,List.of("PAGE_USERS"));clearInvocations(jdbc);
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(username)).andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_USERS")));
        assertThat(auth(session).getAuthorities()).extracting(a->a.getAuthority()).containsExactly("PAGE_USERS");
        assertThat(auth(session).getCredentials()).isNull();
        verify(jdbc).queryForMap(ID_SQL,7L);verify(jdbc,never()).queryForList(eq(USER_SQL),anyString());
    }

    @ParameterizedTest @ValueSource(strings={"disabled","blocked","deleted","renamed","case-renamed","reused"})
    void invalidAccountRevokesOldSessionAndCookiesInsteadOfBorrowingAnotherAccount(String change) throws Exception {
        admin();MvcResult loggedIn=login(" reader ",true);var session=session(loggedIn);
        switch(change){
            case "disabled" -> users.get(7L).put("enabled",false);
            case "blocked" -> users.get(7L).put("is_blocked",true);
            case "renamed" -> users.get(7L).put("username","new-name");
            case "case-renamed" -> users.get(7L).put("username"," ReAdEr ");
            case "reused" -> {users.remove(7L);account(99L," reader ","ROLE_ADMIN","PAGE_CHANNELS");}
            default -> users.remove(7L);
        }
        clearInvocations(jdbc);
        mvc.perform(get("/api/channels").session(session)).andExpect(status().is3xxRedirection())
            .andExpect(unauthenticated()).andExpect(cookie().maxAge("JSESSIONID",0)).andExpect(cookie().maxAge("remember-me",0));
        assertThat(session.isInvalid()).isTrue();verifyNoInteractions(transport);
        verify(jdbc).queryForMap(ID_SQL,7L);verify(jdbc,never()).queryForList(eq(USER_SQL),anyString());
    }

    @ParameterizedTest @ValueSource(strings={"user","direct","role"})
    void storeFailureBlocksTheRequestWithoutReusingOldRightsAndCanRetry(String source) throws Exception {
        admin();var session=session(login(" reader ",false));failing=source;
        mvc.perform(get("/api/channels").session(session)).andExpect(status().isServiceUnavailable());
        verifyNoInteractions(transport);assertThat(session.isInvalid()).isFalse();
        failing="";mvc.perform(get("/api/channels").session(session)).andExpect(status().isOk());verify(transport).getChannels();
    }

    @Test void refreshingRememberedSessionDoesNotUpgradeItToFullAuthentication() throws Exception {
        admin();var session=session(login(" reader ",false));
        Authentication previous=auth(session);var remembered=new RememberMeAuthenticationToken("iguana-panel-remember-me",previous.getPrincipal(),previous.getAuthorities());
        remembered.setDetails(previous.getDetails());var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(remembered);session.setAttribute(CONTEXT,context);
        rights.put(7L,List.of("PAGE_USERS"));
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_USERS")));
        assertThat(auth(session)).isInstanceOf(RememberMeAuthenticationToken.class);
        mvc.perform(get("/api/session-test/full-only").session(session)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }

    @Test void unboundLegacyPrincipalMustLogInAgainBeforeProtectedActions() throws Exception {
        admin();var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        var legacy=User.withUsername(" reader ").password("fixture").authorities("ROLE_ADMIN","PAGE_CHANNELS").build();
        ((org.springframework.security.core.CredentialsContainer)legacy).eraseCredentials();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(legacy,null,legacy.getAuthorities()));session.setAttribute(CONTEXT,context);
        mvc.perform(get("/api/channels").session(session)).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();verifyNoInteractions(transport);
    }

    @Test void serializedSecurityContextKeepsAccountBindingWithoutKeepingCredentials() throws Exception {
        admin();var before=session(login(" reader ",false));byte[] bytes=SerializationUtils.serialize(before.getAttribute(CONTEXT));
        assertThat(new String(bytes,StandardCharsets.ISO_8859_1)).doesNotContain(HASH);
        var restored=new MockHttpSession();restored.setAttribute(CONTEXT,SerializationUtils.deserialize(bytes));
        users.remove(7L);account(99L," reader ","ROLE_ADMIN","PAGE_CHANNELS");
        mvc.perform(get("/api/channels").session(restored)).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        verifyNoInteractions(transport);
    }

    @ParameterizedTest @ValueSource(strings={"/login","/js/auth-login.js","/error/403","/favicon.ico"})
    void publicRoutesDoNotDependOnSessionRefresh(String path) throws Exception {
        admin();var session=session(login(" reader ",false));failing="user";clearInvocations(jdbc);
        int status=mvc.perform(get(path).session(session)).andReturn().getResponse().getStatus();
        assertThat(status).isBetween(200,399);verifyNoInteractions(jdbc,transport);
    }

    @Test void anonymousRequestsStillUseTheExistingEntryPointWithoutAccountLookup() throws Exception {
        mvc.perform(get("/api/channels")).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        verifyNoInteractions(jdbc,transport);
    }
    @Test void csrfStillRejectsWritesBeforeSessionRefreshOrService() throws Exception {
        admin();var session=session(login(" reader ",false));failing="user";clearInvocations(jdbc);
        mvc.perform(post("/api/channels").session(session).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());verifyNoInteractions(jdbc,transport);
    }
    @Test void eachOpenSessionRefreshesBeforeUsingRevokedRights() throws Exception {
        admin();var first=session(login(" reader ",false));var second=session(login(" reader ",false));rights.put(7L,List.of("PAGE_DIALOGS"));
        mvc.perform(get("/api/channels").session(first)).andExpect(status().isForbidden());
        mvc.perform(get("/api/channels").session(second)).andExpect(status().isForbidden());verifyNoInteractions(transport);
    }
    @Test void unchangedRightsDoNotReplaceTheSessionContextOnEveryRequest() throws Exception {
        admin();var session=session(login(" reader ",false));Object before=session.getAttribute(CONTEXT);
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());assertThat(session.getAttribute(CONTEXT)).isSameAs(before);
    }
    @Test void rememberMeCookieUsesCurrentRightsAndKeepsRememberedAuthentication() throws Exception {
        admin();Cookie cookie=login(" reader ",true).getResponse().getCookie("remember-me");rights.put(7L,List.of("PAGE_USERS"));
        mvc.perform(get("/api/auth/whoami").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value(" reader ")).andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_USERS")));
        mvc.perform(get("/api/session-test/full-only").cookie(cookie)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
    }
}

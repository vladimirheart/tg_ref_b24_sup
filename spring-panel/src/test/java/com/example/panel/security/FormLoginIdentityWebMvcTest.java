package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.AuthApiController;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SecurityConfig/DAO/encoder/session/remember-me pipeline; only the JDBC boundary is mocked. */
@SpringBootTest(classes = FormLoginIdentityWebMvcTest.Application.class,
    properties = {"spring.session.store-type=none", "management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class FormLoginIdentityWebMvcTest {
    private static final String USER_SQL = "SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 1";
    private static final String AUTHORITIES_SQL = "SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE_SQL = "SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String PASSWORD = " fixture secret ";
    private static final String HASH = new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private static final String CONTEXT_KEY = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
    private record Account(Map<String,Object> row, List<String> authorities) {}
    private final Map<String,Account> accounts = new LinkedHashMap<>();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class, SecurityHeadersFilter.class, AuthApiController.class, MvcConfig.class})
    static class Application {
        @Bean(name = "usersJdbcTemplate") JdbcTemplate fixtureJdbc() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
            return jdbc;
        }
    }

    @BeforeEach void fixtures() {
        accounts.clear(); reset(jdbc);
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForMap(eq(USER_SQL), anyString())).thenAnswer(call -> {
            Account account = accounts.get(call.<String>getArgument(1).toLowerCase(Locale.ROOT));
            if (account == null) throw new EmptyResultDataAccessException(1);
            return account.row();
        });
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"), anyLong())).thenAnswer(call ->
            accounts.values().stream().filter(a -> a.row().get("id").equals(call.getArgument(1)))
                .findFirst().orElseThrow(() -> new EmptyResultDataAccessException(1)).row());
        when(jdbc.query(eq(AUTHORITIES_SQL), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), anyLong()))
            .thenAnswer(call -> accounts.values().stream()
                .filter(a -> a.row().get("id").equals(call.getArgument(2))).findFirst().orElseThrow().authorities());
        when(jdbc.queryForList(eq(ROLE_SQL), anyLong())).thenReturn(List.of());
    }

    private void account(String username, long id, boolean enabled, boolean blocked, String... rights) {
        accounts.put(username.toLowerCase(Locale.ROOT), new Account(Map.of("id",id,"username",username,
            "password",HASH,"enabled",enabled,"is_blocked",blocked), List.of(rights)));
    }
    private void pair(String username) {
        if (!username.equals(username.trim())) account(username.trim(), 99L, true, false, "ROLE_ADMIN", "PAGE_SETTINGS");
        account(username, 7L, true, false, "PAGE_DIALOGS");
    }
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        return withCsrf(request,null);
    }
    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, MockHttpSession session) throws Exception {
        var pageRequest=get("/login"); if(session!=null) pageRequest.session(session);
        MvcResult page=mvc.perform(pageRequest).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue(); Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN"); assertThat(cookie).isNotNull();
        return request.cookie(cookie).param("_csrf",token.group(1));
    }
    private MockHttpServletRequestBuilder login(String username) throws Exception {
        return withCsrf(post("/login")).param("username",username).param("password",PASSWORD);
    }
    private MvcResult successful(MockHttpServletRequestBuilder request, String username) throws Exception {
        return mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/post-login"))
            .andExpect(authenticated().withUsername(username)).andReturn();
    }
    private MockHttpSession session(MvcResult result) { return (MockHttpSession) result.getRequest().getSession(false); }
    private void whoami(MockHttpSession session, String username, String... rights) throws Exception {
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.authorities",containsInAnyOrder(rights)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"reader", " reader", "reader ", " reader ", "\treader\t", "\nreader\r", "mem\r\nber",
        "member,one", "member;one", "9007199254740993", "ReAdEr", " user/%?#&+ Ю ", "<img>"})
    void loginAndSessionKeepExactAccountAndOnlyItsAuthorities(String username) throws Exception {
        pair(username);
        MvcResult result = successful(login(username), username);
        whoami(session(result), username, "PAGE_DIALOGS");
        verify(jdbc).queryForMap(USER_SQL, username);
        verify(jdbc,never()).query(eq(AUTHORITIES_SQL), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(99L));
        Authentication auth = ((SecurityContext)session(result).getAttribute(CONTEXT_KEY)).getAuthentication();
        assertThat(auth.getCredentials()).isNull();
        assertThat(auth.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
    }

    @ParameterizedTest @ValueSource(strings = {" reader", "reader ", " reader ", "\treader\t", "\nreader\r"})
    void absentExactAccountCannotLogIntoTrimmedNeighbourWithTheSamePassword(String username) throws Exception {
        account("reader",99L,true,false,"ROLE_ADMIN","PAGE_SETTINGS");
        mvc.perform(login(username)).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        // Existing local/parent AuthenticationManager retry; both lookups must retain the exact key.
        verify(jdbc,times(2)).queryForMap(USER_SQL,username);
        verify(jdbc,never()).queryForMap(USER_SQL,"reader");
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "blocked"})
    void inactiveExactAccountCannotBorrowEnabledNeighbour(String state) throws Exception {
        account("reader",99L,true,false,"ROLE_ADMIN","PAGE_SETTINGS");
        account(" reader ",7L,!state.equals("disabled"),state.equals("blocked"),"PAGE_DIALOGS");
        mvc.perform(login(" reader ")).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        verify(jdbc).queryForMap(USER_SQL," reader ");
        verify(jdbc,never()).queryForMap(USER_SQL,"reader");
    }

    @Test void existingCaseInsensitiveLookupStillReturnsStoredIdentity() throws Exception {
        account("ReAdEr",7L,true,false,"PAGE_DIALOGS");
        whoami(session(successful(login("rEaDeR"),"ReAdEr")),"ReAdEr","PAGE_DIALOGS");
        verify(jdbc).queryForMap(USER_SQL,"rEaDeR");
    }

    @Test void passwordEdgeSpacesRemainSignificant() throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        mvc.perform(withCsrf(post("/login")).param("username","reader").param("password",PASSWORD.trim()))
            .andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
        successful(login("reader"),"reader");
    }

    @ParameterizedTest @ValueSource(strings = {"missing-username","missing-password","empty-username","empty-password","blank-username"})
    void missingOrBlankCredentialsFailThroughTheUsualLoginError(String mode) throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        var request=withCsrf(post("/login"));
        if(!mode.equals("missing-username")) request.param("username",mode.equals("empty-username")?"":mode.equals("blank-username")?" \t ":"reader");
        if(!mode.equals("missing-password")) request.param("password",mode.equals("empty-password")?"":PASSWORD);
        mvc.perform(request).andExpect(redirectedUrl("/login?error")).andExpect(unauthenticated());
    }

    @ParameterizedTest @ValueSource(strings = {"missing","invalid"})
    void csrfIsCheckedBeforeCredentialLookup(String mode) throws Exception {
        var request=post("/login").param("username"," reader ").param("password",PASSWORD);
        if(mode.equals("invalid")) request.cookie(new Cookie("XSRF-TOKEN","fixture-csrf")).param("_csrf","invalid");
        mvc.perform(request).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"))
            .andExpect(unauthenticated());
        verifyNoInteractions(jdbc);
    }

    @Test void rememberMeRestoresExactUsernameAndItsOwnAuthorities() throws Exception {
        pair(" reader ");
        MvcResult loggedIn=successful(login(" reader ").param("remember-me","true")," reader ");
        Cookie cookie=loggedIn.getResponse().getCookie("remember-me"); assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isEqualTo(14*24*60*60);
        clearInvocations(jdbc);
        mvc.perform(get("/api/auth/whoami").cookie(cookie)).andExpect(status().isOk())
            .andExpect(authenticated().withUsername(" reader ")).andExpect(jsonPath("$.username").value(" reader "))
            .andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_DIALOGS")));
        verify(jdbc).queryForMap(USER_SQL," reader "); verify(jdbc,never()).queryForMap(USER_SQL,"reader");
    }

    @Test void rememberMeRechecksDisabledAccountWithoutNeighbourFallback() throws Exception {
        pair(" reader ");
        Cookie cookie=successful(login(" reader ").param("remember-me","true")," reader ").getResponse().getCookie("remember-me");
        account(" reader ",7L,false,false,"PAGE_DIALOGS"); clearInvocations(jdbc);
        mvc.perform(get("/api/auth/whoami").cookie(cookie)).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
        verify(jdbc).queryForMap(USER_SQL," reader "); verify(jdbc,never()).queryForMap(USER_SQL,"reader");
    }

    @Test void tamperedRememberMeCannotAuthenticate() throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        Cookie issued=successful(login("reader").param("remember-me","true"),"reader").getResponse().getCookie("remember-me");
        Cookie invalid=new Cookie("remember-me",issued.getValue().substring(0,issued.getValue().length()-4)+"AAAA");
        mvc.perform(get("/api/auth/whoami").cookie(invalid)).andExpect(status().is3xxRedirection()).andExpect(unauthenticated());
    }

    @Test void sessionFixationProtectionAndExistingSessionAttributesArePreserved() throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        MockHttpSession before=new MockHttpSession(); before.setAttribute("fixture-marker","keep"); String id=before.getId();
        MvcResult result=successful(login("reader").session(before),"reader");
        assertThat(session(result).getId()).isNotEqualTo(id);
        assertThat(session(result).getAttribute("fixture-marker")).isEqualTo("keep");
        whoami(session(result),"reader","PAGE_DIALOGS");
    }

    @Test void logoutInvalidatesSessionAndClearsCookies() throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        MvcResult loggedIn=successful(login("reader").param("remember-me","true"),"reader");
        MockHttpSession session=session(loggedIn);
        mvc.perform(withCsrf(post("/logout"),session).session(session)).andExpect(redirectedUrl("/login?logout"))
            .andExpect(unauthenticated()).andExpect(cookie().maxAge("JSESSIONID",0)).andExpect(cookie().maxAge("remember-me",0));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test void loginPageAndRealCsrfCookieStillSubmitThroughTheSameRoute() throws Exception {
        pair(" reader ");
        MvcResult page=mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"))
            .andExpect(unauthenticated()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue(); Cookie csrfCookie=page.getResponse().getCookie("XSRF-TOKEN"); assertThat(csrfCookie).isNotNull();
        successful(post("/login").cookie(csrfCookie).param("_csrf",token.group(1)).param("username"," reader ").param("password",PASSWORD)," reader ");
    }

    @Test void getLoginWithCredentialsOnlyRendersThePage() throws Exception {
        mvc.perform(get("/login").param("username","reader").param("password",PASSWORD))
            .andExpect(status().isOk()).andExpect(view().name("auth/login")).andExpect(unauthenticated());
        verifyNoInteractions(jdbc);
    }

    @Test void configuredSuccessUrlStillOverridesASavedRequest() throws Exception {
        account("reader",7L,true,false,"PAGE_DIALOGS");
        MvcResult denied=mvc.perform(get("/api/auth/whoami")).andExpect(status().is3xxRedirection()).andExpect(unauthenticated()).andReturn();
        successful(login("reader").session(session(denied)),"reader");
    }
}

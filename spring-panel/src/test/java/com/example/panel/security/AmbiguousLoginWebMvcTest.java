package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.AuthApiController;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Primary login/remember-me must reject multiple case-insensitive matches before reading any grants. */
@SpringBootTest(classes=AmbiguousLoginWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AmbiguousLoginWebMvcTest {
    private static final String OLD_SQL="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 1";
    private static final String SQL="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2";
    private static final String AUTH_SQL="SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE_SQL="SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String PASSWORD="fixture password",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private boolean storeUnavailable;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,AuthApiController.class,MvcConfig.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc() {
            var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;
        }
    }
    @BeforeEach void fixtures() {
        rows.clear();storeUnavailable=false;reset(jdbc);
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForMap(eq(OLD_SQL),anyString())).thenAnswer(call -> matching(call.getArgument(1)).stream().findFirst()
            .orElseThrow(() -> new EmptyResultDataAccessException(1)));
        when(jdbc.queryForList(eq(SQL),anyString())).thenAnswer(call -> matching(call.getArgument(1)).stream().limit(2).toList());
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"),anyLong())).thenAnswer(call -> rows.stream()
            .filter(row -> row.get("id").equals(call.getArgument(1))).findFirst().orElseThrow(() -> new EmptyResultDataAccessException(1)));
        when(jdbc.query(eq(AUTH_SQL),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong()))
            .thenAnswer(call -> call.<Long>getArgument(2)==7L?List.of("PAGE_DIALOGS"):List.of("ROLE_ADMIN","PAGE_SETTINGS"));
        when(jdbc.queryForList(eq(ROLE_SQL),anyLong())).thenReturn(List.of());
    }
    private List<Map<String,Object>> matching(String username) {
        if(storeUnavailable)throw new DataAccessResourceFailureException("fixture unavailable");
        return rows.stream().filter(row -> row.get("username").toString().equalsIgnoreCase(username)).toList();
    }
    private void account(long id,String username) {
        rows.add(Map.of("id",id,"username",username,"password",HASH,"enabled",true,"is_blocked",false));
    }
    private MvcResult login(String username,boolean remember) throws Exception {
        MvcResult page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());
        assertThat(token.find()).isTrue();Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var request=post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username",username).param("password",PASSWORD);
        if(remember)request.param("remember-me","true");return mvc.perform(request).andReturn();
    }
    private void rejected(MvcResult result) throws Exception {
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login?error");unauthenticated().match(result);
        Cookie cookie=result.getResponse().getCookie("remember-me");assertThat(cookie==null||cookie.getMaxAge()==0).isTrue();
    }
    static Stream<Arguments> collisions() {
        return Stream.of("reader","ReAdEr","READER").flatMap(username -> Stream.of(Arguments.of(username,false),Arguments.of(username,true)));
    }

    @ParameterizedTest @MethodSource("collisions")
    void neitherExactCaseNorResultOrderMayChooseOneOfTwoAccounts(String username,boolean reverse) throws Exception {
        if(reverse){account(99L,"ReAdEr");account(7L,"reader");}else{account(7L,"reader");account(99L,"ReAdEr");}
        rejected(login(username,true));
        verify(jdbc,never()).query(eq(AUTH_SQL),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong());
        verify(jdbc,never()).queryForList(eq(ROLE_SQL),anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"same-name","three-matches"})
    void duplicateNamesOrMoreThanTwoMatchesAlsoFail(String mode) throws Exception {
        account(7L,"reader");account(99L,mode.equals("same-name")?"reader":"ReAdEr");
        if(mode.equals("three-matches"))account(100L,"READER");
        rejected(login("reader",true));
        verify(jdbc,never()).query(eq(AUTH_SQL),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong());
    }
    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr","READER","name._-@9","имя"," reader "})
    void uniqueMatchStillAuthenticatesWithItsStoredIdentity(String username) throws Exception {
        account(7L,username);MvcResult result=login(username,false);assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/post-login");
        authenticated().withUsername(username).match(result);
        mvc.perform(get("/api/auth/whoami").session((MockHttpSession)result.getRequest().getSession(false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_DIALOGS")));
    }
    @Test void noMatchingAccountUsesTheSameGenericFailure() throws Exception { rejected(login("absent",true)); }
    @Test void rememberedLoginRejectsACollisionIntroducedAfterCookieWasIssued() throws Exception {
        account(7L,"reader");Cookie cookie=login("reader",true).getResponse().getCookie("remember-me");account(99L,"ReAdEr");clearInvocations(jdbc);
        mvc.perform(get("/api/auth/whoami").cookie(cookie)).andExpect(status().is3xxRedirection()).andExpect(unauthenticated())
            .andExpect(cookie().maxAge("remember-me",0));
        verify(jdbc,never()).query(eq(AUTH_SQL),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong());
    }
    @Test void anAlreadyBoundSessionRetainsOnlyItsOwnNumericAccount() throws Exception {
        account(7L,"reader");var session=(MockHttpSession)login("reader",false).getRequest().getSession(false);account(99L,"ReAdEr");
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("reader")).andExpect(jsonPath("$.authorities",containsInAnyOrder("PAGE_DIALOGS")));
    }
    @Test void storeFailureCannotIssueAValidSessionOrRememberMeCookie() throws Exception {
        account(7L,"reader");storeUnavailable=true;rejected(login("reader",true));
    }
}

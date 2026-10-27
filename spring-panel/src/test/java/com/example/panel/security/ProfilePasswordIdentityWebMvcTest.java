package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.ProfileApiController;
import com.example.panel.service.UiPreferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Stateful JDBC fixture models both baseline and ID/CAS SQL; the security chain and encoder are real. */
@SpringBootTest(classes=ProfilePasswordIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class ProfilePasswordIdentityWebMvcTest {
    private static final String LOGIN="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2";
    private static final String OLD_READ="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 1";
    private static final String ID_READ="SELECT * FROM users WHERE id = ?";
    private static final String AUTH="SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE="SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String CURRENT="fixture current",NEXT=" next fixture password ";
    private final LegacyCompatiblePasswordEncoder encoder=new LegacyCompatiblePasswordEncoder();
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private final ObjectMapper json=new ObjectMapper();
    private boolean hasHash=true;
    private String beforeRead="",beforeWrite="",corruptRow="";
    private Cookie csrfCookie;
    private String csrfToken;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProfileApiController controller;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,ProfileApiController.class,MvcConfig.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc() {
            var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;
        }
        @Bean UiPreferenceService preferences(){return mock(UiPreferenceService.class);}
    }

    @BeforeEach void setup() throws Exception {
        accounts.clear();hasHash=true;beforeRead="";beforeWrite="";corruptRow="";reset(jdbc);
        DataSource ds=mock(DataSource.class);Connection connection=mock(Connection.class);DatabaseMetaData metadata=mock(DatabaseMetaData.class);
        when(jdbc.getDataSource()).thenReturn(ds);when(ds.getConnection()).thenReturn(connection);when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(any(),any(),anyString(),anyString())).thenAnswer(call -> {
            ResultSet result=mock(ResultSet.class);when(result.next()).thenReturn(hasHash);return result;
        });
        when(jdbc.queryForList(eq(LOGIN),anyString())).thenAnswer(call -> matching(call.getArgument(1)).stream().limit(2).map(LinkedHashMap::new).toList());
        when(jdbc.queryForMap(eq(ID_READ),anyLong())).thenAnswer(call -> {
            var row=accounts.get(call.<Long>getArgument(1));if(row==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(row);
        });
        when(jdbc.query(eq(AUTH),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenReturn(List.of("PAGE_DIALOGS"));
        when(jdbc.queryForList(eq(ROLE),anyLong())).thenReturn(List.of());
        when(jdbc.queryForList(eq(OLD_READ),anyString())).thenAnswer(call -> {
            mutate(beforeRead);return snapshot(matching(call.getArgument(1)).stream().findFirst().orElse(null));
        });
        when(jdbc.queryForList(eq(ID_READ),anyLong())).thenAnswer(call -> {
            mutate(beforeRead);return snapshot(accounts.get(call.<Long>getArgument(1)));
        });
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(call -> {
            mutate(beforeWrite);String sql=call.getArgument(0);Object[] args=new Object[call.getArguments().length-1];
            for(int i=0;i<args.length;i++)args[i]=call.getArgument(i+1);
            if(sql.contains("WHERE lower(username) = lower(?)")) {
                var rows=matching(args[args.length-1].toString());for(var row:rows){row.put("password",args[0]);if(sql.contains("password_hash = ?"))row.put("password_hash",args[1]);}return rows.size();
            }
            // Explicit ID and original values, including nulls, must all constrain the single write.
            String expected="UPDATE users SET password = ?"+(hasHash?", password_hash = ?":"")+" WHERE id = ? AND username = ? AND password IS NOT DISTINCT FROM ?"+(hasHash?" AND password_hash IS NOT DISTINCT FROM ?":"")+" AND enabled IS NOT DISTINCT FROM ? AND is_blocked IS NOT DISTINCT FROM ?";
            assertThat(sql).isEqualTo(expected);int offset=hasHash?2:1;assertThat(args).hasSize(hasHash?8:6);
            var row=accounts.get(((Number)args[offset]).longValue());if(row==null)return 0;
            List<String> fields=new ArrayList<>(List.of("username","password"));if(hasHash)fields.add("password_hash");fields.addAll(List.of("enabled","is_blocked"));
            for(int i=0;i<fields.size();i++)if(!Objects.equals(row.get(fields.get(i)),args[offset+1+i]))return 0;
            row.put("password",args[0]);if(hasHash)row.put("password_hash",args[1]);return 1;
        });
    }
    private List<Map<String,Object>> snapshot(Map<String,Object> row) {
        if(row==null)return List.of();var copy=new LinkedHashMap<>(row);
        if(corruptRow.equals("id"))copy.put("id",99L);if(corruptRow.equals("username"))copy.put("username","other");return List.of(copy);
    }
    private List<Map<String,Object>> matching(String name){return accounts.values().stream().filter(row -> row.get("username").toString().equalsIgnoreCase(name)).toList();}
    private void account(long id,String name,String password){
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("password",encoder.encode(password));
        if(hasHash)row.put("password_hash",null);row.put("enabled",true);row.put("is_blocked",false);accounts.put(id,row);
    }
    private void mutate(String action){
        var row=accounts.get(7L);if(row==null)return;
        switch(action){case "delete" -> accounts.remove(7L);case "rename" -> row.put("username","changed");case "case-rename" -> row.put("username","READER");
            case "disable" -> row.put("enabled",false);case "block" -> row.put("is_blocked",true);case "password" -> row.put("password",encoder.encode("other fixture password"));
            case "password_hash" -> row.put("password_hash","changed-fallback-hash");default -> {}}
    }
    private void csrfPage() throws Exception {
        MvcResult page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
        var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        csrfCookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(csrfCookie).isNotNull();csrfToken=token.group(1);
    }
    private MockHttpSession login(String name) throws Exception {
        csrfPage();
        MvcResult result=mvc.perform(post("/login").cookie(csrfCookie).param("_csrf",csrfToken).param("username",name).param("password",CURRENT))
            .andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);
    }
    private Map<String,Object> payload(String current){return Map.of("current_password",current,"new_password",NEXT,"confirm_password",NEXT);}
    private org.springframework.test.web.servlet.ResultActions change(MockHttpSession session,String current) throws Exception {
        return mvc.perform(post("/profile/password").session(session).cookie(csrfCookie).param("_csrf",csrfToken).contentType("application/json").content(json.writeValueAsBytes(payload(current))));
    }
    static Stream<Arguments> unique(){return Stream.of("reader","ReAdEr","name._-@9"," reader ","имя").flatMap(name -> Stream.of(Arguments.of(name,false),Arguments.of(name,true)));}
    @ParameterizedTest @MethodSource("unique")
    void ownPasswordChangesAndRawInputIsPreserved(String name,boolean withHash) throws Exception {
        hasHash=withHash;account(7L,name,CURRENT);change(login(name),CURRENT).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(encoder.matches(NEXT,accounts.get(7L).get("password").toString())).isTrue();
        assertThat(encoder.matches(NEXT.trim(),accounts.get(7L).get("password").toString())).isFalse();
        if(hasHash)assertThat(accounts.get(7L).get("password_hash")).isEqualTo(accounts.get(7L).get("password"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void newCaseCollisionNeverChangesNeighbours(boolean neighbourFirst) throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");var own=accounts.remove(7L);if(!neighbourFirst)accounts.put(7L,own);account(99L,"ReAdEr",CURRENT);if(neighbourFirst)accounts.put(7L,own);
        String neighbour=accounts.get(99L).get("password").toString();change(session,CURRENT).andExpect(status().isOk());
        assertThat(accounts.get(99L).get("password")).isEqualTo(neighbour);assertThat(accounts.get(99L).get("password_hash")).isNull();assertThat(encoder.matches(NEXT,own.get("password").toString())).isTrue();
    }
    @Test void neighboursPasswordCannotAuthorizeOwnPasswordChange() throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");var own=accounts.remove(7L);account(99L,"ReAdEr","neighbour fixture");accounts.put(7L,own);
        var before=new LinkedHashMap<>(own);change(session,"neighbour fixture").andExpect(status().isBadRequest());assertThat(own).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"id","username"})
    void inconsistentProfileReadCannotChooseAnotherIdentity(String field) throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");corruptRow=field;var before=new LinkedHashMap<>(accounts.get(7L));
        change(session,CURRENT).andExpect(status().isConflict());assertThat(accounts.get(7L)).isEqualTo(before);
    }
    @Test void disappearanceAfterSessionRefreshDoesNotReportSuccess() throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");beforeRead="delete";change(session,CURRENT).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"disable","block","rename"})
    void accountChangeBetweenSessionRefreshAndProfileReadIsRejected(String action) throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");beforeRead=action;String original=accounts.get(7L).get("password").toString();
        change(session,CURRENT).andExpect(status().isConflict());assertThat(accounts.get(7L).get("password")).isEqualTo(original);
    }
    @ParameterizedTest @ValueSource(strings={"password","password_hash","rename","case-rename","delete","disable","block"})
    void finalWriteCannotOverwriteAConcurrentAccountChange(String action) throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");beforeWrite=action;
        change(session,CURRENT).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        if(accounts.containsKey(7L))assertThat(encoder.matches(NEXT,accounts.get(7L).get("password").toString())).isFalse();
    }
    @Test void wrongCurrentPasswordStillPreventsWrite() throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");var before=new LinkedHashMap<>(accounts.get(7L));change(session,"wrong fixture").andExpect(status().isBadRequest());assertThat(accounts.get(7L)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"current","new","confirm"})
    void invalidInputsStillCannotWrite(String field) throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");var body=new LinkedHashMap<>(payload(CURRENT));
        if(field.equals("current"))body.put("current_password","");if(field.equals("new"))body.put("new_password","bad\tfixture");if(field.equals("confirm"))body.put("confirm_password","different");
        var before=new LinkedHashMap<>(accounts.get(7L));mvc.perform(post("/profile/password").session(session).cookie(csrfCookie).param("_csrf",csrfToken).contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());assertThat(accounts.get(7L)).isEqualTo(before);
    }
    @Test void csrfStillPrecedesTheWrite() throws Exception {
        account(7L,"reader",CURRENT);var session=login("reader");clearInvocations(jdbc);
        mvc.perform(post("/profile/password").session(session).contentType("application/json").content(json.writeValueAsBytes(payload(CURRENT)))).andExpect(redirectedUrl("/error/403"));verifyNoInteractions(jdbc);
    }
    @Test void anUnboundLegacyPrincipalCannotResolveSelfByName() {
        account(7L,"reader",CURRENT);clearInvocations(jdbc);var actor=UsernamePasswordAuthenticationToken.authenticated("reader",null,List.of());
        assertThat(controller.updatePassword(actor,payload(CURRENT)).getStatusCode().value()).isEqualTo(401);verifyNoInteractions(jdbc);
    }
    @Test void anUnauthenticatedNumericPrincipalCannotChangePassword() {
        account(7L,"reader",CURRENT);clearInvocations(jdbc);var principal=new PanelUserPrincipal(7L,"reader","",true,AuthorityUtils.NO_AUTHORITIES);
        var actor=UsernamePasswordAuthenticationToken.unauthenticated(principal,null);
        assertThat(controller.updatePassword(actor,payload(CURRENT)).getStatusCode().value()).isEqualTo(401);verifyNoInteractions(jdbc);
    }
    @Test void aMissingNumericIdentityCannotFallBackToAnExistingName() {
        account(7L,"reader",CURRENT);var principal=new PanelUserPrincipal(999L,"reader","",true,AuthorityUtils.NO_AUTHORITIES);
        var actor=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());var before=new LinkedHashMap<>(accounts.get(7L));
        assertThat(controller.updatePassword(actor,payload(CURRENT)).getStatusCode().value()).isEqualTo(404);assertThat(accounts.get(7L)).isEqualTo(before);
    }
    @Test void anonymousStillCannotReachPasswordUpdate() throws Exception {
        csrfPage();mvc.perform(post("/profile/password").cookie(csrfCookie).param("_csrf",csrfToken).contentType("application/json").content(json.writeValueAsBytes(payload(CURRENT)))).andExpect(status().is3xxRedirection());verifyNoInteractions(jdbc);
    }
}

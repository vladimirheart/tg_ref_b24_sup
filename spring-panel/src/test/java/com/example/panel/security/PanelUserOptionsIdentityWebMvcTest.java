package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.config.RestExceptionHandler;
import com.example.panel.controller.PanelUserOptionsController;
import com.example.panel.repository.PanelUserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.springframework.context.annotation.*;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security/login/session/controller/repository/RowMapper; isolated JDBC result adapters. */
@SpringBootTest(classes=PanelUserOptionsIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class PanelUserOptionsIdentityWebMvcTest {
    private static final String API="/api/panel-users/options",PASSWORD="fixture employee options",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> rows=new LinkedHashMap<>();
    private final Set<String> privateReads=new LinkedHashSet<>();
    private final List<String> catalogQueries=new ArrayList<>();
    private String authority="PAGE_DIALOGS";
    private boolean unavailable;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,MvcConfig.class,RestExceptionHandler.class,PanelUserOptionsController.class,PanelUserRepository.class})
    static class Application {@Bean(name="usersJdbcTemplate") JdbcTemplate jdbc(){var j=mock(JdbcTemplate.class);when(j.getDataSource()).thenReturn(mock(DataSource.class));return j;}}
    @BeforeEach void setup(){
        rows.clear();privateReads.clear();catalogQueries.clear();authority="PAGE_DIALOGS";unavailable=false;reset(jdbc);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForList(eq("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2"),anyString())).thenAnswer(c->rows.values().stream().filter(r->r.get("username")!=null&&r.get("username").toString().equalsIgnoreCase(c.getArgument(1))).limit(2).<Map<String,Object>>map(LinkedHashMap::new).toList());
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"),anyLong())).thenAnswer(c->{var row=rows.get(c.<Long>getArgument(1));if(row==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(row);});
        when(jdbc.query(eq("SELECT authority FROM user_authorities WHERE user_id = ?"),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenAnswer(c->List.of(authority));
        when(jdbc.queryForList(eq("SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1"),anyLong())).thenReturn(List.of());
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any())).thenAnswer(c->{
            String sql=c.getArgument(0);catalogQueries.add(sql);if(unavailable)throw new DataAccessResourceFailureException("fixture catalog unavailable");
            RowMapper<Object> rowMapper=c.getArgument(1);var output=new ArrayList<Object>();int index=0;
            for(var row:rows.values())output.add(rowMapper.mapRow(result(row),index++));return output;
        });
        account(1,"viewer","zzzz Viewer",true,false);
    }
    private void account(long id,String name,String fullName,Boolean enabled,Boolean blocked){var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("full_name",fullName);row.put("enabled",enabled);row.put("is_blocked",blocked);row.put("password",HASH);row.put("password_hash","private-fixture-hash");row.put("email","private@example.test");row.put("phones","private-phone");row.put("role","private-role");row.put("department","private-department");row.put("photo","private-photo");rows.put(id,row);}
    private void track(String column){if(Set.of("password","password_hash","email","phones","role","role_id","department","photo","birth_date","registration_date").contains(column))privateReads.add(column);}
    private ResultSet result(Map<String,Object> row) throws Exception {
        var rs=mock(ResultSet.class);when(rs.findColumn(anyString())).thenAnswer(c->{String key=c.getArgument(0);if(row.containsKey(key)||key.equals("identity_count"))return 1;throw new SQLException("fixture missing column");});
        when(rs.getString(anyString())).thenAnswer(c->{String key=c.getArgument(0);track(key);Object value=row.get(key);return value==null?null:value.toString();});
        when(rs.getObject(anyString())).thenAnswer(c->{String key=c.getArgument(0);track(key);return row.get(key);});
        when(rs.getBoolean(anyString())).thenAnswer(c->{String key=c.getArgument(0);Object value=row.get(key);return value==null?key.equals("enabled"):Boolean.TRUE.equals(value);});
        when(rs.getLong(anyString())).thenAnswer(c->{String key=c.getArgument(0);if(key.equals("identity_count")){Object name=row.get("username");return rows.values().stream().filter(r->Objects.equals(name,r.get("username"))||name!=null&&r.get("username")!=null&&name.toString().equalsIgnoreCase(r.get("username").toString())).count();}return ((Number)row.get(key)).longValue();});return rs;
    }
    private MockHttpSession login() throws Exception {var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username","viewer").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    private List<Map<String,Object>> options(MockHttpSession session) throws Exception {var response=mvc.perform(get(API).session(session)).andExpect(status().isOk()).andReturn();return mapper.readValue(response.getResponse().getContentAsString(),new TypeReference<List<Map<String,Object>>>(){});}
    private List<Object> values(List<Map<String,Object>> options){return options.stream().map(r->r.get("value")).toList();}

    @ParameterizedTest @ValueSource(strings={"disabled","blocked","both"})
    void inactiveOrBlockedAccountsAreNeverOffered(String state) throws Exception {account(7,"subject","Subject",!state.equals("disabled")&&!state.equals("both"),state.equals("blocked")||state.equals("both"));assertThat(values(options(login()))).containsExactly("viewer");}
    @ParameterizedTest @ValueSource(strings={"enabled-null","blocked-null"})
    void nullableStatusRetainsTheSameLegacyDefaultsAsLogin(String state) throws Exception {account(7,"subject",null,state.equals("enabled-null")?null:true,state.equals("blocked-null")?null:false);assertThat(values(options(login()))).contains("subject");}
    @ParameterizedTest @ValueSource(strings={"active","disabled","blocked"})
    void aCaseFoldCollisionIsRejectedAcrossAllAccounts(String neighbour) throws Exception {account(7,"subject","First",true,false);account(99,"SuBjEcT","Second",!neighbour.equals("disabled"),neighbour.equals("blocked"));assertThat(values(options(login()))).containsExactly("viewer");}
    @Test void rawPaddingDoesNotCollapseDifferentAccountKeys() throws Exception {account(7,"subject","First",true,false);account(99," subject ","Second",true,false);assertThat(values(options(login()))).containsExactly("subject"," subject ","viewer");}
    @ParameterizedTest @ValueSource(strings={"operator","OpErAtOr"," operator ","имя","anonymousUser","ai_agent","9007199254740993"})
    void literalNamesRemainStringOptionValues(String name) throws Exception {account(7,name,null,true,false);var result=options(login());assertThat(result).anySatisfy(option->{assertThat(option.get("value")).isEqualTo(name).isInstanceOf(String.class);assertThat(option.get("label")).isEqualTo(name);});}
    @Test void optionLookupDoesNotReadCredentialsOrPrivateProfileColumns() throws Exception {account(7,"subject","Subject",true,false);var result=options(login());assertThat(privateReads).isEmpty();assertThat(catalogQueries).hasSize(1);assertThat(catalogQueries.get(0).toLowerCase(Locale.ROOT)).doesNotContain("select *");assertThat(result).allSatisfy(option->assertThat(option.keySet()).containsExactlyInAnyOrder("value","label"));}
    @Test void displayLabelsAndCaseInsensitiveOrderRemainUnchanged() throws Exception {account(7,"raw"," Alpha",true,false);account(8,"beta","Beta",true,false);account(9,"fallback",null,true,false);assertThat(options(login()).stream().map(row->row.get("label")).toList()).containsExactly(" Alpha · raw","Beta · beta","fallback","zzzz Viewer · viewer");}
    @ParameterizedTest @ValueSource(strings={"PAGE_DIALOGS","PAGE_TASKS","PAGE_OBJECT_PASSPORTS","PAGE_SETTINGS","PAGE_USERS"})
    void eachExistingPageAuthorityCanReadTheGlobalCatalog(String page) throws Exception {authority=page;assertThat(values(options(login()))).containsExactly("viewer");}
    @ParameterizedTest @ValueSource(strings={"ROLE_ADMIN","ROLE_PORTAL_ADMIN","ALL_BUSINESSES","PAGE_KNOWLEDGE_BASE"})
    void unrelatedOrAdministrativeAuthoritiesDoNotReplaceThePageGate(String page) throws Exception {authority=page;mvc.perform(get(API).session(login())).andExpect(status().isForbidden());assertThat(catalogQueries).isEmpty();}
    @Test void anonymousCannotReadTheCatalog() throws Exception {mvc.perform(get(API)).andExpect(status().is3xxRedirection());assertThat(catalogQueries).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","renamed","deleted"})
    void revokedSessionsCannotReachTheGlobalDirectory(String state) throws Exception {var session=login();switch(state){case "disabled"->rows.get(1L).put("enabled",false);case "blocked"->rows.get(1L).put("is_blocked",true);case "renamed"->rows.get(1L).put("username","changed");default->rows.remove(1L);}mvc.perform(get(API).session(session)).andExpect(status().is3xxRedirection());assertThat(catalogQueries).isEmpty();}
    @Test void aReadFailureCannotFallBackToTheFullUserRepository() throws Exception {var session=login();unavailable=true;mvc.perform(get(API).session(session)).andExpect(status().is5xxServerError()).andExpect(jsonPath("$.success").value(false));assertThat(catalogQueries).hasSize(1);}
    @Test void aRevokedPageRightIsObservedOnTheNextRead() throws Exception {var session=login();authority="ROLE_USER";mvc.perform(get(API).session(session)).andExpect(status().isForbidden());assertThat(catalogQueries).isEmpty();}
}

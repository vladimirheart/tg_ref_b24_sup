package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.AuthApiController;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real security chain and activity bean; the JDBC fixture models old fan-out and the ID-bound writer. */
@SpringBootTest(classes=PortalActivityIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class PortalActivityIdentityWebMvcTest {
    private static final String LOGIN="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2";
    private static final String ID="SELECT * FROM users WHERE id = ?";
    private static final String OLD="UPDATE users SET last_portal_activity_at = ? WHERE lower(username) = lower(?)";
    private static final String UPDATE="UPDATE users SET last_portal_activity_at = ? WHERE id = ? AND username = ?";
    private static final String AUTH="SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE="SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String PASSWORD="fixture current",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private final List<String> attempts=new ArrayList<>();
    private String race="",failure="";
    private boolean sessionStoreFailure;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserLastActivityFilter activity;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,UserLastActivityFilter.class,AuthApiController.class,MvcConfig.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc(){var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;}
    }
    @BeforeEach void setup(){
        accounts.clear();attempts.clear();race="";failure="";sessionStoreFailure=false;reset(jdbc);ReflectionTestUtils.setField(activity,"columnMissing",false);
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForList(eq(LOGIN),anyString())).thenAnswer(c -> accounts.values().stream().filter(r -> r.get("username").toString().equalsIgnoreCase(c.getArgument(1))).limit(2).map(LinkedHashMap::new).toList());
        when(jdbc.queryForMap(eq(ID),anyLong())).thenAnswer(c -> {if(sessionStoreFailure)throw new DataAccessResourceFailureException("fixture session read unavailable");var row=accounts.get(c.<Long>getArgument(1));if(row==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(row);});
        when(jdbc.query(eq(AUTH),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenReturn(List.of("PAGE_DIALOGS"));
        when(jdbc.queryForList(eq(ROLE),anyLong())).thenReturn(List.of());
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(c -> {
            String sql=c.getArgument(0),timestamp=c.getArgument(1);assertThat(sql).isIn(OLD,UPDATE);attempts.add(sql);
            assertThat(OffsetDateTime.parse(timestamp).getOffset()).isEqualTo(ZoneOffset.UTC);
            if(!failure.isEmpty()){String message=failure.equals("column")?"column last_portal_activity_at not found":"fixture transient unavailable";if(failure.equals("transient"))failure="";throw new DataAccessResourceFailureException(message);}
            var own=accounts.get(7L);if(own!=null){switch(race){case "rename" -> own.put("username","changed");case "case-rename" -> own.put("username","READER");case "delete" -> accounts.remove(7L);default -> {}}}
            int count=0;for(var row:accounts.values()){
                boolean match=sql.equals(OLD)?row.get("username").toString().equalsIgnoreCase(c.getArgument(2)):
                    row.get("id").equals(c.getArgument(2))&&row.get("username").equals(c.getArgument(3));
                if(match){row.put("activity",timestamp);count++;}
            }return count;
        });
    }
    @AfterEach void clearContext(){SecurityContextHolder.clearContext();}
    private void account(long id,String name){var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("password",HASH);row.put("enabled",true);row.put("is_blocked",false);row.put("activity","untouched");accounts.put(id,row);}
    private org.springframework.test.web.servlet.MvcResult login(String name,boolean remember) throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var request=post("/login").cookie(cookie).param("_csrf",token.group(1)).param("username",name).param("password",PASSWORD);if(remember)request.param("remember-me","true");
        return mvc.perform(request).andExpect(redirectedUrl("/post-login")).andReturn();
    }
    private MockHttpSession session(String name) throws Exception {return (MockHttpSession)login(name,false).getRequest().getSession(false);}
    private void collision(boolean neighbourFirst){var own=accounts.remove(7L);if(!neighbourFirst)accounts.put(7L,own);account(99L,"ReAdEr");if(neighbourFirst)accounts.put(7L,own);}
    private void ownActivity(long id){assertThat(accounts.get(id).get("activity")).isNotEqualTo("untouched");assertThat(attempts).hasSize(1);}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void aCaseCollisionAfterLoginCannotMarkTheNeighbourOnline(boolean neighbourFirst) throws Exception {
        account(7L,"reader");var session=session("reader");collision(neighbourFirst);mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("reader"));
        ownActivity(7L);assertThat(accounts.get(99L).get("activity")).isEqualTo("untouched");
    }
    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr","name._-@9"," reader ","имя"})
    void uniqueRawAccountIsTouchedExactlyOnce(String name) throws Exception {
        account(7L,name);mvc.perform(get("/api/auth/whoami").session(session(name))).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(name));ownActivity(7L);
    }
    @ParameterizedTest @ValueSource(strings={"rename","case-rename","delete"})
    void renameOrDeletionBetweenRefreshAndTouchCannotFallBackToTheNeighbour(String change) throws Exception {
        account(7L,"reader");var session=session("reader");collision(false);race=change;mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());
        assertThat(accounts.get(99L).get("activity")).isEqualTo("untouched");if(accounts.containsKey(7L))assertThat(accounts.get(7L).get("activity")).isEqualTo("untouched");assertThat(attempts).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","renamed","deleted"})
    void aRevokedSessionCannotWriteActivity(String change) throws Exception {
        account(7L,"reader");var session=session("reader");collision(false);switch(change){case "disabled" -> accounts.get(7L).put("enabled",false);case "blocked" -> accounts.get(7L).put("is_blocked",true);case "renamed" -> accounts.get(7L).put("username","changed");case "deleted" -> accounts.remove(7L);default -> {}}
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().is3xxRedirection());assertThat(attempts).isEmpty();assertThat(accounts.get(99L).get("activity")).isEqualTo("untouched");
    }
    @Test void aTransientActivityFailureDoesNotBlockTheRequestAndCanRetry() throws Exception {
        account(7L,"reader");var session=session("reader");failure="transient";mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());assertThat(accounts.get(7L).get("activity")).isEqualTo("untouched");
        mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());assertThat(accounts.get(7L).get("activity")).isNotEqualTo("untouched");assertThat(attempts).hasSize(2);
    }
    @Test void aMissingColumnRetainsItsOneFailureCache() throws Exception {
        account(7L,"reader");var session=session("reader");failure="column";mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isOk());assertThat(attempts).hasSize(1);
    }
    @Test void aSessionReadFailureStopsBeforeTheActivityWriter() throws Exception {
        account(7L,"reader");var session=session("reader");sessionStoreFailure=true;mvc.perform(get("/api/auth/whoami").session(session)).andExpect(status().isServiceUnavailable());assertThat(attempts).isEmpty();
    }
    @Test void rememberedAuthenticationKeepsItsOwnActivityIdentity() throws Exception {
        account(7L,"ReAdEr");var cookie=login("ReAdEr",true).getResponse().getCookie("remember-me");assertThat(cookie).isNotNull();mvc.perform(get("/api/auth/whoami").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("ReAdEr"));ownActivity(7L);
    }
    @ParameterizedTest @ValueSource(longs={9007199254740993L,Long.MAX_VALUE})
    void largeNumericIdsReachTheWriterWithoutPrecisionLoss(long id) throws Exception {
        account(id,"reader");mvc.perform(get("/api/auth/whoami").session(session("reader"))).andExpect(status().isOk());ownActivity(id);
    }
    @ParameterizedTest @ValueSource(strings={"legacy-string","legacy-user","zero","negative","unknown-id","unauthenticated","blank","absent","anonymous"})
    @SuppressWarnings("unchecked")
    void unboundOrInvalidIdentitiesCannotWriteByAnExistingName(String mode) throws Exception {
        account(7L,"reader");var principal=new PanelUserPrincipal(mode.equals("zero")?0L:mode.equals("negative")?-1L:mode.equals("unknown-id")?999L:7L,mode.equals("blank")?" ":"reader","",true,AuthorityUtils.NO_AUTHORITIES);
        Authentication actor=switch(mode){case "legacy-string" -> UsernamePasswordAuthenticationToken.authenticated("reader",null,List.of());case "legacy-user" -> UsernamePasswordAuthenticationToken.authenticated(User.withUsername("reader").password("fixture").authorities("ROLE_USER").build(),null,List.of());case "unauthenticated" -> UsernamePasswordAuthenticationToken.unauthenticated(principal,null);case "absent" -> null;case "anonymous" -> new AnonymousAuthenticationToken("fixture","anonymous",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));default -> UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());};
        ObjectProvider<JdbcTemplate> provider=mock(ObjectProvider.class);when(provider.getIfAvailable()).thenReturn(jdbc);var isolated=new UserLastActivityFilter(provider);var chained=new java.util.concurrent.atomic.AtomicInteger();SecurityContextHolder.getContext().setAuthentication(actor);
        try{isolated.doFilter(new MockHttpServletRequest(),new MockHttpServletResponse(),(request,response) -> chained.incrementAndGet());}finally{SecurityContextHolder.clearContext();}
        assertThat(chained).hasValue(1);assertThat(accounts.get(7L).get("activity")).isEqualTo("untouched");
    }
}

package com.example.panel.security;

import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.service.GeneralTicketAccessService;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static com.example.panel.support.GeneralTicketAdminTestSupport.*;
import static com.example.panel.support.GeneralEntryIdentityFixture.seed;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real form login/R189/CSRF plus real GENERAL controller and service; existing minimal security application. */
@SpringBootTest(classes=GeneralEntryIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class GeneralTicketAdminAuthorizationWebMvcTest {
    static final String PASSWORD="fixture general entry",HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @SpyBean GeneralTicketAccessService access;
    @SpyBean MultibusinessFeatureFlags flags;
    @BeforeEach void setup(){
        seed(jdbc,7,"caller",HASH);jdbc.execute("TRUNCATE roles,user_authorities,settings_parameters");
        for(String right:List.of("ROLE_ADMIN","PAGE_SETTINGS"))jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(7,?)",right);
        jdbc.update("DELETE FROM general_ticket_read_grants WHERE user_id=7");jdbc.update("DELETE FROM general_ticket_operate_grants WHERE user_id=7");
        flags.getFoundation().setEnabled(true);flags.setGeneralTicketQueue(true);
    }
    void prepare(Operation op){jdbc.update("UPDATE general_ticket_read_grants SET is_active=? WHERE user_id=55",op!=Operation.grantRead);jdbc.update("UPDATE general_ticket_operate_grants SET is_active=? WHERE user_id=55",op==Operation.accounts||op==Operation.revokeRead||op==Operation.revokeOperate);}
    Map<String,List<Map<String,Object>>> snapshot(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("general_ticket_read_grants","general_ticket_operate_grants"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY user_id"));return result;}
    String token(String html){var match=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);assertThat(match.find()).isTrue();return match.group(1);}
    MockHttpSession login()throws Exception{
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();
        var result=mvc.perform(post("/login").cookie(cookie).param("_csrf",token(page.getResponse().getContentAsString())).param("username","caller").param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();
        var session=(MockHttpSession)result.getRequest().getSession(false);assertThat(session).isNotNull();return session;
    }
    MockHttpServletRequestBuilder request(Operation op,MockHttpSession session,boolean csrf)throws Exception{
        String path="/api/general-ticket-admin/access"+(op==Operation.accounts?"":"/55"+(op==Operation.grantOperate||op==Operation.revokeOperate?"/operate":""));
        var request=switch(op){case accounts->get(path);case grantRead,grantOperate->put(path);default->delete(path);};request.session(session);
        if(csrf&&op!=Operation.accounts){var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();request.cookie(page.getResponse().getCookie("XSRF-TOKEN")).param("_csrf",token(page.getResponse().getContentAsString()));}
        return request;
    }
    void mutate(String mode){
        if(mode.equals("rename-reuse")){jdbc.update("UPDATE users SET username='renamed' WHERE id=7");jdbc.update("INSERT INTO users(id,username,password,enabled,is_blocked) VALUES(99,'caller',?,TRUE,FALSE)",HASH);jdbc.update("INSERT INTO user_authorities(user_id,authority) VALUES(99,'ROLE_ADMIN'),(99,'PAGE_SETTINGS')");}
        else jdbc.update("DELETE FROM user_authorities WHERE user_id=7 AND authority=?",mode.equals("role")?"ROLE_ADMIN":"PAGE_SETTINGS");
    }
    @ParameterizedTest @EnumSource(Operation.class) void aRealLoginCanAdministerWithoutAReadOrOperateGrant(Operation op)throws Exception{
        prepare(op);var session=login();assertThat(access.canRead(7L)).isFalse();assertThat(access.canOperate(7L)).isFalse();
        var result=mvc.perform(request(op,session,true)).andExpect(status().is(op==Operation.accounts?200:204));
        if(op==Operation.accounts)result.andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.length()").value(2));
    }
    static Stream<Arguments> late(){return Stream.of("role","page","rename-reuse").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("late") void aLateAuthorizationChangeIsForbiddenWithoutGrantWrites(String mode,Operation op)throws Exception{
        prepare(op);var session=login();var before=snapshot();var fired=new AtomicBoolean();
        beforeCall(access,op,55,()->{assertThat(fired.compareAndSet(false,true)).isTrue();mutate(mode);});
        mvc.perform(request(op,session,true)).andExpect(status().isForbidden());assertThat(fired).isTrue();assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> early(){return Stream.of("role","page").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("early") void r189StillRefreshesRightsBeforeTheController(String mode,Operation op)throws Exception{
        prepare(op);var session=login();mutate(mode);var before=snapshot();mvc.perform(request(op,session,true)).andExpect(status().isForbidden());assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @EnumSource(Operation.class) void featureOffStillReturnsNotFound(Operation op)throws Exception{prepare(op);var session=login();flags.setGeneralTicketQueue(false);var before=snapshot();mvc.perform(request(op,session,true)).andExpect(status().isNotFound());assertThat(snapshot()).isEqualTo(before);}
    @ParameterizedTest @EnumSource(value=Operation.class,names="accounts",mode=EnumSource.Mode.EXCLUDE) void everyMutationStillRequiresCsrf(Operation op)throws Exception{prepare(op);var session=login();var before=snapshot();mvc.perform(request(op,session,false)).andExpect(status().isForbidden());assertThat(snapshot()).isEqualTo(before);}
    static Stream<Arguments> assignedSources(){return roleSources().stream().flatMap(source->Arrays.stream(Operation.values()).map(op->Arguments.of(source,op)));}
    @ParameterizedTest @MethodSource("assignedSources") void actualLoginKeepsDirectMixedAndAssignedRoleRights(String source,Operation op)throws Exception{
        prepare(op);assignRole(jdbc,7,source);var session=login();var before=snapshot();mvc.perform(request(op,session,true)).andExpect(status().is(roleAllows(source)?op==Operation.accounts?200:204:403));if(!roleAllows(source)||op==Operation.accounts)assertThat(snapshot()).isEqualTo(before);
    }
    static Stream<Arguments> assignedLate(){return Stream.of("name","page","pointer").flatMap(source->Arrays.stream(Operation.values()).map(op->Arguments.of(source,op)));}
    @ParameterizedTest @MethodSource("assignedLate") void assignedRoleRevocationAfterRequestEntryIsForbidden(String mutation,Operation op)throws Exception{
        prepare(op);assignRole(jdbc,7,"role");var session=login();var before=snapshot();var fired=new AtomicBoolean();beforeCall(access,op,55,()->{assertThat(fired.compareAndSet(false,true)).isTrue();changeAssignedRole(jdbc,7,mutation);});mvc.perform(request(op,session,true)).andExpect(status().isForbidden());assertThat(fired).isTrue();assertThat(snapshot()).isEqualTo(before);
    }
}

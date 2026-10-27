package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.controller.AuthManagementApiController;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/security/method security and native CSRF; JDBC and file/config adapters are isolated fixtures. */
@SpringBootTest(classes=AuthManagementIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AuthManagementIdentityWebMvcTest {
    private static final String LOGIN="SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2";
    private static final String ID="SELECT * FROM users WHERE id = ?";
    private static final String OLD_ID="SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1";
    private static final String OLD_FIELDS="SELECT r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE lower(u.username) = lower(?) LIMIT 1";
    private static final String FIELDS="SELECT u.*, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ?";
    private static final String AUTH="SELECT authority FROM user_authorities WHERE user_id = ?";
    private static final String ROLE="SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1";
    private static final String PASSWORD="fixture current";
    private static final String HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private final Map<Long,String> fields=new LinkedHashMap<>(),roles=new LinkedHashMap<>();
    private final List<String> writes=new ArrayList<>();
    private List<String> authorities=List.of("PAGE_SETTINGS");
    private String fieldIssue="";
    private Cookie csrfCookie;
    private String csrfToken;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthManagementApiController controller;
    @Autowired PanelUserPhotoService photos;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,AuthManagementApiController.class,PermissionService.class,MvcConfig.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate fixtureJdbc(){var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));return jdbc;}
        @Bean SharedConfigService config(){var shared=mock(SharedConfigService.class);when(shared.loadOrgStructure()).thenReturn(JSON.createObjectNode());return shared;}
        @Bean PanelUserPhotoService photos(){return mock(PanelUserPhotoService.class);}
    }
    @BeforeEach void setup() throws Exception {
        accounts.clear();fields.clear();roles.clear();writes.clear();authorities=List.of("PAGE_SETTINGS");fieldIssue="";reset(jdbc,photos);
        ReflectionTestUtils.setField(controller,"userColumns",Set.of("username","password","password_hash","full_name","email","phones","photo","role_id","is_blocked","enabled"));
        when(jdbc.getDataSource()).thenReturn(mock(DataSource.class));
        when(jdbc.queryForList(eq(LOGIN),anyString())).thenAnswer(c -> matching(c.getArgument(1)).stream().limit(2).map(LinkedHashMap::new).toList());
        when(jdbc.queryForMap(eq(ID),anyLong())).thenAnswer(c -> {var row=accounts.get(c.<Long>getArgument(1));if(row==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(row);});
        when(jdbc.query(eq(AUTH),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenAnswer(c -> authorities);
        when(jdbc.queryForList(eq(ROLE),anyLong())).thenAnswer(c -> List.of(Map.of("role_name",roles.getOrDefault(c.<Long>getArgument(1),"operator"),"permissions",fields.get(c.<Long>getArgument(1)))));
        when(jdbc.queryForList(eq(OLD_ID),anyString())).thenAnswer(c -> matching(c.getArgument(1)).stream().limit(1).map(row -> Map.of("id",row.get("id"))).toList());
        when(jdbc.queryForList(eq(OLD_FIELDS),anyString())).thenAnswer(c -> {fieldMutation();return matching(c.getArgument(1)).stream().limit(1).map(row -> Map.of("permissions",fields.get(row.get("id")))).toList();});
        when(jdbc.queryForList(eq(FIELDS),anyLong())).thenAnswer(c -> {
            fieldMutation();var row=accounts.get(c.<Long>getArgument(1));if(row==null)return List.of();var copy=new LinkedHashMap<>(row);copy.put("permissions",fields.get(row.get("id")));
            if(fieldIssue.equals("wrong-id"))copy.put("id",99L);return fieldIssue.equals("duplicates")?List.of(copy,copy):List.of(copy);
        });
        when(jdbc.queryForList(startsWith("SELECT u.*"))).thenAnswer(c -> accounts.values().stream().map(LinkedHashMap::new).toList());
        when(jdbc.queryForList(startsWith("SELECT id, name, description, permissions FROM roles"))).thenReturn(List.of());
        when(jdbc.queryForList(eq("SELECT username FROM users WHERE id = ?"),anyLong())).thenAnswer(c -> {var row=accounts.get(c.<Long>getArgument(1));return row==null?List.of():List.of(Map.of("username",row.get("username")));});
        when(jdbc.query(eq("SELECT photo FROM users WHERE id = ?"),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenAnswer(c -> List.of(accounts.get(c.<Long>getArgument(2)).get("photo")));
        when(photos.storeUploadedAvatar(any(),any())).thenReturn(new PanelUserPhotoService.StoredAvatar("fixture.png","/fixture/avatar.png"));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(c -> {
            String sql=c.getArgument(0);writes.add(sql);
            if(sql.startsWith("UPDATE users SET ")){
                long id=((Number)c.getArgument(c.getArguments().length-1)).longValue();var row=accounts.get(id);if(row==null)return 0;
                String[] columns=sql.substring("UPDATE users SET ".length(),sql.indexOf(" WHERE id = ?")).split(", ");
                for(int i=0;i<columns.length;i++)row.put(columns[i].replace(" = ?",""),c.getArgument(i+1));
            }
            if(sql.equals("DELETE FROM users WHERE id = ?"))return accounts.remove(c.<Long>getArgument(1))==null?0:1;
            return 1;
        });
    }
    private List<Map<String,Object>> matching(String name){return accounts.values().stream().filter(r -> r.get("username").toString().equalsIgnoreCase(name)).toList();}
    private String permissions(String... edit) throws Exception {return JSON.writeValueAsString(Map.of("pages",List.of(),"fields",Map.of("edit",List.of(edit),"view",List.of())));}
    private void account(long id,String name,String... edit) throws Exception {
        var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("password",HASH);row.put("enabled",true);row.put("is_blocked",false);row.put("role_id",id);row.put("photo","");accounts.put(id,row);fields.put(id,permissions(edit));
    }
    private void collision(String... neighbourPermissions) throws Exception {var own=accounts.remove(7L);account(99L,"ReAdEr",neighbourPermissions);accounts.put(7L,own);}
    private void fieldMutation(){
        if(fieldIssue.equals("failure"))throw new DataAccessResourceFailureException("fixture field store unavailable");
        var own=accounts.get(7L);if(own==null)return;
        switch(fieldIssue){case "missing" -> accounts.remove(7L);case "renamed" -> own.put("username","changed");case "disabled" -> own.put("enabled",false);case "blocked" -> own.put("is_blocked",true);case "malformed" -> fields.put(7L,"{not-json");default -> {}}
    }
    private void csrfPage() throws Exception {
        var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var token=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(token.find()).isTrue();
        csrfCookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(csrfCookie).isNotNull();csrfToken=token.group(1);
    }
    private MockHttpSession login(String name) throws Exception {
        csrfPage();var result=mvc.perform(post("/login").cookie(csrfCookie).param("_csrf",csrfToken).param("username",name).param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);
    }
    private record Action(String key,String method,String path,Map<String,Object> body){}
    static Stream<Action> actions(){return Stream.of(
        new Action("user.create","POST","/api/users",Map.of("username","fresh-actor","password","fixture next")),
        new Action("user.username","PATCH","/api/users/200",Map.of("username","renamed-target")),
        new Action("user.password","PATCH","/api/users/200",Map.of("password","fixture next")),
        new Action("user.role","PATCH","/api/users/200",Map.of("role_id",5)),
        new Action("user.delete","DELETE","/api/users/200",Map.of()),
        new Action("user.block","PATCH","/api/users/200",Map.of("is_blocked",true)),
        new Action("role.create","POST","/api/roles",Map.of("name","fixture-role")),
        new Action("role.name","PATCH","/api/roles/5",Map.of("name","fixture-role")),
        new Action("role.description","PATCH","/api/roles/5",Map.of("description","fixture description")),
        new Action("role.pages","PATCH","/api/roles/5",Map.of("permissions",Map.of("pages",List.of("dialogs")))),
        new Action("role.fields.edit","PATCH","/api/roles/5",Map.of("permissions",Map.of("fields",Map.of("edit",List.of("user.create"))))),
        new Action("role.fields.view","PATCH","/api/roles/5",Map.of("permissions",Map.of("fields",Map.of("view",List.of("user.password"))))),
        new Action("role.delete","DELETE","/api/roles/5",Map.of()));}
    private org.springframework.test.web.servlet.ResultActions action(MockHttpSession session,Action action) throws Exception {
        return send(session,action.method,action.path,action.body);
    }
    private org.springframework.test.web.servlet.ResultActions send(MockHttpSession session,String method,String path,Map<String,Object> body) throws Exception {
        MockHttpServletRequestBuilder request=switch(method){case "POST" -> post(path);case "DELETE" -> delete(path);default -> patch(path);};
        return mvc.perform(request.session(session).cookie(csrfCookie).param("_csrf",csrfToken).contentType("application/json").content(JSON.writeValueAsBytes(body)));
    }
    @ParameterizedTest @MethodSource("actions")
    void neighboursFieldsCannotAuthorizeAnyManagementWrite(Action action) throws Exception {
        account(7L,"reader");account(200L,"target");var session=login("reader");collision("*");
        action(session,action).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));assertThat(writes).isEmpty();verifyNoInteractions(photos);
    }
    @ParameterizedTest @MethodSource("actions")
    void ownFieldsStillAuthorizeAllSupportedManagementWrites(Action action) throws Exception {
        account(7L,"reader",action.key);account(200L,"target");var session=login("reader");collision();
        action(session,action).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));assertThat(writes).hasSize(1);
    }
    @Test void currentUserAndSelfFlagsStayBoundToTheSessionId() throws Exception {
        account(7L,"reader");var session=login("reader");collision();
        mvc.perform(get("/api/auth/state").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.current_user_id").value(7))
            .andExpect(jsonPath("$.users[0].id").value(99)).andExpect(jsonPath("$.users[0].is_self").value(false))
            .andExpect(jsonPath("$.users[1].id").value(7)).andExpect(jsonPath("$.users[1].is_self").value(true));
    }
    @Test void ownProfileStillCanBeEditedWithoutGlobalFieldPermission() throws Exception {
        account(7L,"reader");var session=login("reader");collision();send(session,"PATCH","/api/users/7",Map.of("full_name","Own fixture","phones",List.of())).andExpect(jsonPath("$.success").value(true));assertThat(accounts.get(7L).get("full_name")).isEqualTo("Own fixture");assertThat(accounts.get(99L)).doesNotContainKey("full_name");
    }
    @Test void ownPasswordShortcutDoesNotMoveToANeighbour() throws Exception {
        account(7L,"reader");var session=login("reader");collision();send(session,"PATCH","/api/users/99",Map.of("password","fixture next")).andExpect(jsonPath("$.success").value(false));assertThat(writes).isEmpty();
    }
    @Test void ownPasswordShortcutKeepsItsExistingBehaviour() throws Exception {
        account(7L,"reader");var session=login("reader");collision();send(session,"PATCH","/api/users/7",Map.of("password","fixture next")).andExpect(jsonPath("$.success").value(true));assertThat(accounts.get(99L).get("password")).isEqualTo(HASH);assertThat(new LegacyCompatiblePasswordEncoder().matches("fixture next",accounts.get(7L).get("password").toString())).isTrue();
    }
    @ParameterizedTest @ValueSource(longs={7L,99L})
    void photoOwnershipUsesTheSameNumericSelfBoundary(long id) throws Exception {
        account(7L,"reader");var session=login("reader");collision();var image=new MockMultipartFile("photo","fixture.png","image/png",new byte[]{1,2,3});
        mvc.perform(multipart("/api/users/photo-upload").file(image).param("userId",Long.toString(id)).session(session).cookie(csrfCookie).param("_csrf",csrfToken)).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(id==7));
        if(id==7){verify(photos).storeUploadedAvatar(eq(7L),any());assertThat(accounts.get(7L).get("photo")).isEqualTo("/fixture/avatar.png");assertThat(accounts.get(99L).get("photo")).isEqualTo("");}else{verifyNoInteractions(photos);assertThat(writes).isEmpty();}
    }
    @ParameterizedTest @ValueSource(strings={"block","delete"})
    void administratorCannotBlockOrDeleteItsOwnNumericIdentity(String mode) throws Exception {
        account(7L,"reader");roles.put(7L,"admin");var session=login("reader");collision();
        send(session,mode.equals("delete")?"DELETE":"PATCH","/api/users/7",mode.equals("delete")?Map.of():Map.of("is_blocked",true)).andExpect(jsonPath("$.success").value(false));assertThat(writes).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"block","delete"})
    void administratorStillCanManageAnotherNumericIdentity(String mode) throws Exception {
        account(7L,"reader");roles.put(7L,"admin");var session=login("reader");collision();
        send(session,mode.equals("delete")?"DELETE":"PATCH","/api/users/99",mode.equals("delete")?Map.of():Map.of("is_blocked",true)).andExpect(jsonPath("$.success").value(true));assertThat(writes).hasSize(1);
    }
    @ParameterizedTest @ValueSource(strings={"missing","renamed","disabled","blocked","wrong-id","duplicates","malformed"})
    void roleReadCannotUseMissingChangedOrAmbiguousAccountRows(String issue) throws Exception {
        account(7L,"reader","user.create");var session=login("reader");collision("*");fieldIssue=issue;
        action(session,actions().findFirst().orElseThrow()).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));assertThat(writes).isEmpty();
    }
    @Test void aFieldStoreFailureCannotFallBackToAnotherAccountsRights() throws Exception {
        account(7L,"reader","user.create");var session=login("reader");collision("*");fieldIssue="failure";
        assertThatThrownBy(() -> action(session,actions().findFirst().orElseThrow())).hasRootCauseInstanceOf(DataAccessResourceFailureException.class);assertThat(writes).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr","name._-@9"," reader ","имя"})
    void uniqueRawAccountKeepsItsSelfProfileAccess(String name) throws Exception {
        account(7L,name);send(login(name),"PATCH","/api/users/7",Map.of("full_name","Own fixture")).andExpect(jsonPath("$.success").value(true));assertThat(accounts.get(7L).get("full_name")).isEqualTo("Own fixture");
    }
    @ParameterizedTest @ValueSource(strings={"admin","portal_admin"})
    void existingGlobalRoleBypassesRemainAvailable(String role) throws Exception {
        account(7L,"reader");roles.put(7L,role);var session=login("reader");collision();action(session,actions().findFirst().orElseThrow()).andExpect(jsonPath("$.success").value(true));assertThat(writes).hasSize(1);
    }
    @Test void aRoleOnlyEditCannotDeleteAnUnauthorisedPhoto() throws Exception {
        account(7L,"reader","user.role");account(200L,"target");accounts.get(200L).put("photo","/fixture/original.png");var session=login("reader");collision("user.role");
        send(session,"PATCH","/api/users/200",Map.of("role_id",5,"photo","")).andExpect(jsonPath("$.success").value(true));
        assertThat(accounts.get(200L).get("photo")).isEqualTo("/fixture/original.png");verifyNoInteractions(photos);assertThat(writes).hasSize(1);
    }
    @Test void anAuthorisedSelfPhotoClearStillRemovesItsFiles() throws Exception {
        account(7L,"reader");accounts.get(7L).put("photo","/fixture/original.png");
        send(login("reader"),"PATCH","/api/users/7",Map.of("photo","")).andExpect(jsonPath("$.success").value(true));
        assertThat(accounts.get(7L).get("photo")).isEqualTo("");verify(photos).deleteUserAvatarFiles(7L);assertThat(writes).hasSize(1);
    }
    @Test void pageUsersAloneDoesNotGainSettingsFieldPermissions() throws Exception {
        authorities=List.of("PAGE_USERS");account(7L,"reader","user.create");action(login("reader"),actions().findFirst().orElseThrow()).andExpect(jsonPath("$.success").value(false));assertThat(writes).isEmpty();
    }
    @Test void managementPageGateStillPrecedesTheHandler() throws Exception {
        authorities=List.of("PAGE_DIALOGS");account(7L,"reader","user.create");action(login("reader"),actions().findFirst().orElseThrow()).andExpect(status().isForbidden());assertThat(writes).isEmpty();
    }
    @Test void nativeCsrfIsRequiredBeforeAnyManagementWrite() throws Exception {
        account(7L,"reader","user.create");var session=login("reader");clearInvocations(jdbc);mvc.perform(post("/api/users").session(session).contentType("application/json").content("{}")).andExpect(status().isForbidden());verifyNoInteractions(jdbc);assertThat(writes).isEmpty();
    }
    @Test void anonymousStillCannotReachManagement() throws Exception {
        csrfPage();mvc.perform(post("/api/users").cookie(csrfCookie).param("_csrf",csrfToken).contentType("application/json").content("{}")).andExpect(status().is3xxRedirection());assertThat(writes).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"legacy","unauthenticated","zero","negative","missing"})
    void anUnboundPrincipalCannotResolveSelfByAnExistingName(String mode) throws Exception {
        account(7L,"reader");var principal=new PanelUserPrincipal(mode.equals("zero")?0L:mode.equals("negative")?-1L:mode.equals("missing")?999L:7L,"reader","",true,AuthorityUtils.createAuthorityList("PAGE_SETTINGS"));
        var actor=mode.equals("legacy")?UsernamePasswordAuthenticationToken.authenticated("reader",null,principal.getAuthorities()):mode.equals("unauthenticated")?UsernamePasswordAuthenticationToken.unauthenticated(principal,null):UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        assertThat(AopTestUtils.<AuthManagementApiController>getTargetObject(controller).updateUser(7L,actor,Map.of("full_name","Forbidden fixture")).get("success")).isEqualTo(false);assertThat(writes).isEmpty();
    }
    @Test void noPrincipalHasNoSelfOrFieldAccess() {
        assertThat(AopTestUtils.<AuthManagementApiController>getTargetObject(controller).updateUser(7L,null,Map.of("full_name","Forbidden fixture")).get("success")).isEqualTo(false);assertThat(writes).isEmpty();
    }
}

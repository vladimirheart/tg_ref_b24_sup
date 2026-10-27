package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.config.RestExceptionHandler;
import com.example.panel.controller.*;
import com.example.panel.repository.*;
import com.example.panel.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/security/controllers/credential service; stateful JDBC, transaction and provider adapters. */
@SpringBootTest(classes=AutomationCredentialIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AutomationCredentialIdentityWebMvcTest {
    private static final String TYPE="employee_discount_automation_credentials.account.v2", OLD="employee_discount_automation_credentials.v1";
    private static final String PASSWORD="fixture automation", HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private final List<Map<String,Object>> stored=new ArrayList<>();
    private final List<Object> providerActors=new ArrayList<>();
    private String failure="",race="";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EmployeeDiscountAutomationCredentialService credentials;
    @Autowired Bitrix24RestService bitrix;
    @Autowired IikoDirectoryService iiko;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,MvcConfig.class,RestExceptionHandler.class,
        EmployeeDiscountAutomationController.class,EmployeeDiscountAutomationService.class,
        EmployeeDiscountAutomationCredentialService.class,SettingsParameterService.class,SettingsParametersController.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate jdbc() throws Exception {
            var ds=mock(DataSource.class);var connection=mock(Connection.class);
            when(ds.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
            var jdbc=mock(JdbcTemplate.class);when(jdbc.getDataSource()).thenReturn(ds);return jdbc;
        }
        @Bean SharedConfigService shared(){return mock(SharedConfigService.class);}
        @Bean SettingsCatalogService catalog(){var c=mock(SettingsCatalogService.class);when(c.getParameterTypes()).thenReturn(Map.of(TYPE,"Private",OLD,"Legacy","role","Role"));when(c.getParameterDependencies()).thenReturn(Map.of());return c;}
        @Bean EmployeeDiscountAutomationSettingsService settings(){var s=mock(EmployeeDiscountAutomationSettingsService.class);when(s.load()).thenReturn(new EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings(77L,List.of(),List.of(),"",List.of(),List.of(),true));return s;}
        @Bean Bitrix24RestService bitrix(){return mock(Bitrix24RestService.class);}
        @Bean IikoDirectoryService iiko(){return mock(IikoDirectoryService.class);}
        @Bean AutomationRunRepository runs(){var r=mock(AutomationRunRepository.class);when(r.save(any())).thenAnswer(c -> {com.example.panel.entity.AutomationRun run=c.getArgument(0);run.setId(1L);return run;});return r;}
        @Bean AutomationRunItemRepository items(){return mock(AutomationRunItemRepository.class);}
    }
    @BeforeEach void setup() {
        accounts.clear();stored.clear();providerActors.clear();failure="";race="";
        clearInvocations(jdbc);reset(bitrix,iiko);
        when(jdbc.queryForList(anyString(),any(Object[].class))).thenAnswer(c -> rows(c.getArgument(0),(Object[])c.getRawArguments()[1]));
        when(jdbc.queryForList(anyString())).thenAnswer(c -> publicRows(c.getArgument(0)));
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"),anyLong())).thenAnswer(c -> {var r=accounts.get(c.<Long>getArgument(1));if(r==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(r);});
        when(jdbc.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenReturn(List.of("PAGE_DIALOGS","PAGE_SETTINGS","ROLE_ADMIN"));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(c -> update(c.getArgument(0),(Object[])c.getRawArguments()[1]));
        when(bitrix.loadConnectionStatus(any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return Map.of();});
        when(iiko.loadStatus(any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return Map.of();});
        when(bitrix.listWorkgroups(any(),any(),anyInt())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return List.of();});
        when(bitrix.listTasksForGroup(any(),any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return List.of();});
        when(iiko.loadCategories(any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return List.of();});
        when(iiko.loadOrganizations(any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return List.of();});
        when(iiko.loadWallets(any())).thenAnswer(c -> {providerActors.add(c.getArgument(0));return List.of();});
    }
    private List<Map<String,Object>> rows(String sql,Object[] args) {
        if(sql.contains("lower(username)"))return accounts.values().stream().filter(r -> r.get("username").toString().equalsIgnoreCase(args[0].toString())).limit(2).<Map<String,Object>>map(LinkedHashMap::new).toList();
        if(sql.contains("FROM users")&&sql.contains("WHERE id = ?")) {
            var r=accounts.get(args[0]);if(r==null)return List.of();var snapshot=new LinkedHashMap<>(r);
            if(!race.isEmpty()){switch(race){case "renamed" -> snapshot.put("username","changed");case "blocked" -> snapshot.put("is_blocked",true);case "disabled" -> snapshot.put("enabled",false);case "deleted" -> {return List.of();}default -> {}}}
            return List.of(snapshot);
        }
        if(sql.contains("JOIN roles"))return List.of();
        if(sql.contains("settings_parameters")&&sql.contains("WHERE id = ?"))return stored.stream().filter(r -> r.get("id").equals(args[0])).<Map<String,Object>>map(LinkedHashMap::new).toList();
        if(sql.contains("settings_parameters")) {
            if(failure.equals("read"))throw new DataAccessResourceFailureException("fixture storage unavailable");
            return stored.stream().filter(r -> r.get("param_type").equals(args[0])&&!Boolean.TRUE.equals(r.get("is_deleted")))
                .filter(r -> sql.contains("lower(value)")?r.get("value").toString().equalsIgnoreCase(args[1].toString()):r.get("value").equals(args[1]))
                .limit(sql.contains("LIMIT 1")?1:2).<Map<String,Object>>map(LinkedHashMap::new).toList();
        }
        throw new AssertionError("Unexpected fixture SQL: "+sql);
    }
    private List<Map<String,Object>> publicRows(String sql) {
        return stored.stream().filter(r -> !r.get("param_type").toString().startsWith("ui_preferences."))
            .filter(r -> !sql.contains("employee_discount_automation_credentials.")||!r.get("param_type").toString().startsWith("employee_discount_automation_credentials."))
            .<Map<String,Object>>map(LinkedHashMap::new).toList();
    }
    private int update(String sql,Object[] args) {
        if(failure.equals("write"))throw new DataAccessResourceFailureException("fixture write unavailable");
        if(failure.equals("zero"))return 0;
        if(sql.startsWith("INSERT INTO settings_parameters")){seed(args[0].toString(),args[1].toString(),args[args.length-1].toString());return 1;}
        if(sql.startsWith("UPDATE settings_parameters")&&sql.contains("extra_json = ?")) {
            if(sql.contains("state = ?,"))return 1; // Generic baseline writes are accepted; private API must deny them.
            var row=stored.stream().filter(r -> r.get("id").equals(args[1])).findFirst().orElseThrow();
            if(args.length>2&&(!row.get("param_type").equals(args[2])||!row.get("value").equals(args[3])||Boolean.TRUE.equals(row.get("is_deleted"))||!Objects.equals(row.get("extra_json"),args[4])))return 0;
            row.put("extra_json",args[0]);return 1;
        }
        return 1;
    }
    private void account(long id,String name){var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("password",HASH);row.put("enabled",true);row.put("is_blocked",false);accounts.put(id,row);}
    private long seed(String type,String key,String json){long id=stored.size()+1L;var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("param_type",type);row.put("value",key);row.put("extra_json",json);row.put("is_deleted",false);stored.add(row);return id;}
    private static Authentication actor(long id,String name){var p=new PanelUserPrincipal(id,name,"",true,AuthorityUtils.NO_AUTHORITIES);return UsernamePasswordAuthenticationToken.authenticated(p,null,List.of());}
    private static String json(String portal){return "{\"bitrix24\":{\"portal_url\":\""+portal+"\",\"webhook_url\":\"fixture-private-webhook\"}}";}
    private MockHttpSession login(String name) throws Exception {var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var result=mvc.perform(csrf(post("/login"),page).param("username",name).param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request,MvcResult page) throws Exception {Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(m.find()).isTrue();return request.cookie(cookie).param("_csrf",m.group(1));}
    private MockHttpServletRequestBuilder write(String method,String path,MockHttpSession session,String body) throws Exception {var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();return csrf(request(org.springframework.http.HttpMethod.valueOf(method),path).session(session),page).contentType("application/json").content(body);}
    private Object invoke(String method,Authentication actor,Map<String,Object> payload) throws Throwable {var m=Arrays.stream(credentials.getClass().getMethods()).filter(x -> x.getName().equals(method)).findFirst().orElseThrow();Object key=m.getParameterTypes()[0]==String.class?(actor==null?null:actor.getName()):actor;try{return payload==null?m.invoke(credentials,key):m.invoke(credentials,key,payload);}catch(InvocationTargetException ex){throw ex.getCause();}}
    private void ownedStatus(MockHttpSession session,String portal) throws Exception {mvc.perform(get("/api/ai-ops/employee-discounts/status").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.status.credentials.bitrix24.portal_url").value(portal)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-private-webhook"))));}

    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr"," reader ","имя","anonymousUser"})
    void personalSecretsUseTheNumericAccountKey(String name) throws Exception {account(7,name);seed(TYPE,"7",json("own"));seed(OLD,name,json("legacy"));ownedStatus(login(name),"own");}
    @Test void collisionAfterLoginCannotReadOrOverwriteTheNeighbour() throws Exception {account(7,"reader");var session=login("reader");account(99,"ReAdEr");seed(TYPE,"99",json("neighbour"));seed(TYPE,"7",json("own"));seed(OLD,"ReAdEr",json("legacy"));ownedStatus(session,"own");mvc.perform(write("POST","/api/ai-ops/employee-discounts/credentials",session,"{\"bitrix24\":{\"portal_url\":\"changed\"}}" )).andExpect(status().isOk());assertThat(stored.get(0).get("extra_json")).isEqualTo(json("neighbour"));assertThat(stored.get(2).get("extra_json")).isEqualTo(json("legacy"));assertThat(stored).hasSize(3);assertThat(stored.get(1).get("extra_json").toString()).contains("changed");}
    @ParameterizedTest @ValueSource(longs={9007199254740993L,Long.MAX_VALUE})
    void firstSaveUsesAnExactDecimalLongKey(long id) throws Exception {account(id,"reader");mvc.perform(write("POST","/api/ai-ops/employee-discounts/credentials",login("reader"),"{\"bitrix24\":{\"portal_url\":\"own\"}}" )).andExpect(status().isOk());assertThat(stored).hasSize(1);assertThat(stored.get(0)).containsEntry("param_type",TYPE).containsEntry("value",Long.toString(id));}
    @Test void reusedNameDoesNotInheritDeletedAccountSecrets() throws Exception {account(99,"reader");seed(TYPE,"7",json("former"));seed(OLD,"reader",json("legacy"));ownedStatus(login("reader"),"");}
    @Test void freshLoginAfterRenameRetainsTheAccountSecrets() throws Exception {account(7,"changed");seed(TYPE,"7",json("own"));ownedStatus(login("changed"),"own");}
    @ParameterizedTest @ValueSource(strings={"/status","/bitrix/groups","/preview","/iiko/categories","/iiko/organizations","/iiko/wallets"})
    void everyProviderCallerReceivesTheBoundAuthentication(String path) throws Exception {account(7,"reader");mvc.perform(get("/api/ai-ops/employee-discounts"+path).session(login("reader"))).andExpect(status().isOk());assertThat(providerActors).isNotEmpty().allSatisfy(a -> {assertThat(a).isInstanceOf(Authentication.class);assertThat(((Authentication)a).getPrincipal()).isInstanceOf(PanelUserPrincipal.class);assertThat(((PanelUserPrincipal)((Authentication)a).getPrincipal()).getUserId()).isEqualTo(7);});}
    @Test void dryRunRetainsIdentityThroughDiscovery() throws Exception {account(7,"reader");mvc.perform(write("POST","/api/ai-ops/employee-discounts/runs",login("reader"),"{\"dry_run\":true}" )).andExpect(status().isOk());assertThat(providerActors).singleElement().isInstanceOf(Authentication.class);}
    @ParameterizedTest @ValueSource(strings={"renamed","blocked","disabled","deleted"})
    void accountChangeAfterSessionRefreshStopsBeforeSecretRead(String mode) throws Exception {account(7,"reader");var session=login("reader");seed(TYPE,"7",json("own"));race=mode;mvc.perform(get("/api/ai-ops/employee-discounts/status").session(session)).andExpect(status().isForbidden());assertThat(providerActors).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"duplicate","corrupt","null"})
    void ambiguousOrCorruptOwnedStorageCannotBeOverwritten(String mode) {account(7,"reader");seed(TYPE,"7",mode.equals("corrupt")?"broken":mode.equals("null")?"null":json("own"));if(mode.equals("duplicate"))seed(TYPE,"7",json("second"));assertThatThrownBy(() -> invoke("saveForUser",actor(7,"reader"),Map.of("bitrix24",Map.of("portal_url","changed")))).isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException)ex).getStatusCode().value()).isEqualTo(409));}
    @ParameterizedTest @ValueSource(strings={"legacy","zero","negative","blank","unauthenticated","anonymous","absent"})
    void unboundOrInvalidActorsCannotUseAnExistingName(String mode) {account(7,"reader");Authentication a=switch(mode){case "legacy" -> UsernamePasswordAuthenticationToken.authenticated(User.withUsername("reader").password("fixture").roles("USER").build(),null,List.of());case "zero" -> actor(0,"reader");case "negative" -> actor(-1,"reader");case "blank" -> actor(7," ");case "unauthenticated" -> UsernamePasswordAuthenticationToken.unauthenticated(new PanelUserPrincipal(7,"reader","",true,List.of()),null);case "anonymous" -> new AnonymousAuthenticationToken("fixture","reader",AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));case "absent" -> null;default -> throw new AssertionError();};assertThatThrownBy(() -> invoke("loadForUser",a,null)).isInstanceOf(AccessDeniedException.class);assertThat(stored).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"read","write","zero"})
    void storageFailureDoesNotReportASuccess(String mode) {account(7,"reader");failure=mode;assertThatThrownBy(() -> invoke("saveForUser",actor(7,"reader"),Map.of())).isInstanceOf(ResponseStatusException.class).satisfies(ex -> assertThat(((ResponseStatusException)ex).getStatusCode().value()).isEqualTo(mode.equals("zero")?409:503));assertThat(stored).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"POST","PATCH","DELETE"})
    void genericSettingsCannotMutatePersonalSecretsEvenForAdmin(String method) throws Exception {account(7,"reader");long id=seed(TYPE,"7",json("own"));String path=method.equals("POST")?"/api/settings/parameters":"/api/settings/parameters/"+id;mvc.perform(write(method,path,login("reader"),"{\"param_type\":\""+TYPE+"\",\"value\":\"7\",\"state\":\"changed\"}" )).andExpect(status().isForbidden());assertThat(stored).hasSize(1);assertThat(stored.get(0).get("extra_json")).isEqualTo(json("own"));}
    @Test void genericSettingsListCannotDiscloseOldOrNewSecretPayloads() throws Exception {account(7,"reader");seed(TYPE,"7",json("own"));seed(OLD,"reader",json("legacy"));mvc.perform(get("/api/settings/parameters").session(login("reader"))).andExpect(status().isOk()).andExpect(jsonPath("$.data['"+TYPE+"']").doesNotExist()).andExpect(jsonPath("$.data['"+OLD+"']").doesNotExist()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fixture-private-webhook"))));}
    @Test void missingCsrfCannotSaveSecrets() throws Exception {account(7,"reader");mvc.perform(post("/api/ai-ops/employee-discounts/credentials").session(login("reader")).contentType("application/json").content("{}")).andExpect(status().isForbidden());assertThat(stored).isEmpty();}
    @Test void revokedSessionCannotReachCredentials() throws Exception {account(7,"reader");var session=login("reader");accounts.get(7L).put("enabled",false);mvc.perform(get("/api/ai-ops/employee-discounts/status").session(session)).andExpect(status().is3xxRedirection());assertThat(providerActors).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"bitrix","iiko"})
    void actualProviderAdaptersUseOnlyTheOwnersSecretAndEndpoint(String provider) throws Throwable {
        account(7,"reader");account(99,"ReAdEr");
        var requests=new ArrayList<String>();var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {requests.add(exchange.getRequestURI().toString());String body=exchange.getRequestURI().getPath().endsWith("access_token")?"\"fixture-token\"":"{\"result\":[]}";byte[] bytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);try(var output=exchange.getResponseBody()){output.write(bytes);}});server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();var mapper=new ObjectMapper();
            for(String owner:List.of("own","other")) {
                String url=base+"/"+owner;String data=mapper.writeValueAsString(Map.of("bitrix24",Map.of("portal_url",url,"webhook_url",url+"/"),"active_iiko_profile_url",url,"iiko_profiles",Map.of(url,Map.of("base_url",url,"api_login",owner,"api_secret",owner+"-secret"))));
                seed(owner.equals("own")?TYPE:OLD,owner.equals("own")?"7":"reader",data);
            }
            Object adapter=provider.equals("bitrix")?new Bitrix24RestService(credentials,mapper):new IikoDirectoryService(credentials,mapper);
            String method=provider.equals("bitrix")?"loadConnectionStatus":"loadOrganizations";
            var m=Arrays.stream(adapter.getClass().getMethods()).filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
            Object key=m.getParameterTypes()[0]==String.class?"reader":actor(7,"reader");
            try {m.invoke(adapter,key);}catch(InvocationTargetException ex){throw ex.getCause();}
            assertThat(requests).isNotEmpty().allSatisfy(request -> assertThat(request).startsWith("/own/").doesNotContain("other"));
            if(provider.equals("iiko"))assertThat(requests).anySatisfy(request -> assertThat(request).contains("user_secret=own-secret"));
        } finally {server.stop(0);}
    }
}

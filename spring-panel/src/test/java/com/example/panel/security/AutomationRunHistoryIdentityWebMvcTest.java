package com.example.panel.security;

import com.example.panel.config.MvcConfig;
import com.example.panel.config.RestExceptionHandler;
import com.example.panel.controller.EmployeeDiscountAutomationController;
import com.example.panel.entity.AutomationRun;
import com.example.panel.entity.AutomationRunItem;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.repository.AutomationRunItemRepository;
import com.example.panel.service.*;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real login/session/security/controllers/orchestration; isolated stateful repository/JDBC adapters. */
@SpringBootTest(classes=AutomationRunHistoryIdentityWebMvcTest.Application.class,
    properties={"spring.session.store-type=none","management.endpoint.health.group.readiness.include=ping"})
@AutoConfigureMockMvc
class AutomationRunHistoryIdentityWebMvcTest {
    private static final String API="/api/ai-ops/employee-discounts/runs", KEY=EmployeeDiscountAutomationService.AUTOMATION_KEY;
    private static final String PASSWORD="fixture run history", HASH=new LegacyCompatiblePasswordEncoder().encode(PASSWORD);
    private final Map<Long,Map<String,Object>> accounts=new LinkedHashMap<>();
    private boolean pageRight=true;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired HistoryStore store;
    @Autowired AutomationRunRepository runs;
    @Autowired AutomationRunItemRepository items;
    @Autowired EmployeeDiscountAutomationService service;
    @Autowired EmployeeDiscountAutomationSettingsService settings;
    @Autowired Bitrix24RestService bitrix;
    @Autowired IikoDirectoryService iiko;

    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class,SecurityHeadersFilter.class,MvcConfig.class,RestExceptionHandler.class,
        EmployeeDiscountAutomationController.class,EmployeeDiscountAutomationService.class})
    static class Application {
        @Bean(name="usersJdbcTemplate") JdbcTemplate jdbc(){var j=mock(JdbcTemplate.class);when(j.getDataSource()).thenReturn(mock(DataSource.class));return j;}
        @Bean HistoryStore store(){return new HistoryStore();}
        @Bean AutomationRunRepository runs(HistoryStore s){return mock(AutomationRunRepository.class,s);}
        @Bean AutomationRunItemRepository items(HistoryStore s){return mock(AutomationRunItemRepository.class,s);}
        @Bean EmployeeDiscountAutomationCredentialService credentials(){return mock(EmployeeDiscountAutomationCredentialService.class);}
        @Bean EmployeeDiscountAutomationSettingsService settings(){return mock(EmployeeDiscountAutomationSettingsService.class);}
        @Bean Bitrix24RestService bitrix(){return mock(Bitrix24RestService.class);}
        @Bean IikoDirectoryService iiko(){return mock(IikoDirectoryService.class);}
    }
    static class HistoryStore implements Answer<Object> {
        final Map<Long,AutomationRun> rows=new LinkedHashMap<>();
        final Map<Long,Long> owners=new HashMap<>();
        final List<Saved> saved=new ArrayList<>();
        final List<Long> childQueries=new ArrayList<>();
        final List<Object> queriedOwners=new ArrayList<>();
        boolean unavailable;
        long sequence=1000;
        record Saved(Long owner,String actor,String status) {}
        void reset(){rows.clear();owners.clear();saved.clear();childQueries.clear();queriedOwners.clear();unavailable=false;sequence=1000;}
        public Object answer(InvocationOnMock c) throws Throwable {
            String method=c.getMethod().getName();
            if(method.equals("save")&&c.getArgument(0) instanceof AutomationRun row){if(row.getId()==null)row.setId(sequence++);Long owner=ownerField(row);rows.put(row.getId(),row);owners.put(row.getId(),owner);saved.add(new Saved(owner,row.getActor(),row.getStatus()));return row;}
            if(method.equals("save")&&c.getArgument(0) instanceof AutomationRunItem row){if(row.getId()==null)row.setId(sequence++);return row;}
            if(method.equals("findByRunIdOrderByCreatedAtAsc")){long id=c.getArgument(0);childQueries.add(id);var item=new AutomationRunItem();item.setId(501L);item.setExternalTaskId("task-"+id);item.setTaskTitle("private child "+id);item.setPhone("+79990000000");item.setStatus("success");item.setMessage("private message "+id);item.setChecklistItemId("check-"+id);item.setCreatedAt(OffsetDateTime.parse("2026-10-27T10:00:00Z"));return List.of(item);}
            boolean list=method.startsWith("findTop20ByAutomationKeyAndActor"),detail=method.startsWith("findByIdAndAutomationKeyAndActor");
            if(list||detail){if(unavailable)throw new DataAccessResourceFailureException("fixture history unavailable");String key=c.getArgument(detail?1:0);Object owner=c.getArgument(detail?2:1);queriedOwners.add(owner);boolean numeric=method.contains("ActorUserId");
                var selected=rows.values().stream().filter(row->key.equals(row.getAutomationKey())).filter(row->numeric?Objects.equals(owners.get(row.getId()),owner):row.getActor()!=null&&row.getActor().equalsIgnoreCase(String.valueOf(owner)));
                if(detail){Long id=c.getArgument(0);return selected.filter(row->id.equals(row.getId())).findFirst();}
                Comparator<AutomationRun> order=Comparator.comparing(AutomationRun::getStartedAt,Comparator.reverseOrder());if(numeric)order=order.thenComparing(AutomationRun::getId,Comparator.reverseOrder());return selected.sorted(order).limit(20).toList();
            }
            return RETURNS_DEFAULTS.answer(c);
        }
    }
    private static Long ownerField(AutomationRun run) throws ReflectiveOperationException {try{var field=AutomationRun.class.getDeclaredField("actorUserId");field.setAccessible(true);return (Long)field.get(run);}catch(NoSuchFieldException oldBaseline){return null;}}
    private void seed(long id,Long owner,String actor,String key) throws Exception {var run=new AutomationRun();run.setId(id);run.setActor(actor);run.setAutomationKey(key);run.setMode("dry_run");run.setStatus("success");run.setSummary("fixture");run.setStartedAt(OffsetDateTime.parse("2026-10-27T10:00:00Z").plusSeconds(id));run.setCreatedAt(run.getStartedAt());try{var field=AutomationRun.class.getDeclaredField("actorUserId");field.setAccessible(true);field.set(run,owner);}catch(NoSuchFieldException oldBaseline){}store.rows.put(id,run);store.owners.put(id,owner);}
    @BeforeEach void setup(){
        accounts.clear();pageRight=true;store.reset();clearInvocations(jdbc,runs,items);reset(settings,bitrix,iiko);
        when(jdbc.queryForList(eq("SELECT * FROM users WHERE lower(username) = lower(?) LIMIT 2"),anyString())).thenAnswer(c->accounts.values().stream().filter(r->r.get("username").toString().equalsIgnoreCase(c.getArgument(1))).limit(2).<Map<String,Object>>map(LinkedHashMap::new).toList());
        when(jdbc.queryForMap(eq("SELECT * FROM users WHERE id = ?"),anyLong())).thenAnswer(c->{var row=accounts.get(c.<Long>getArgument(1));if(row==null)throw new EmptyResultDataAccessException(1);return new LinkedHashMap<>(row);});
        when(jdbc.query(eq("SELECT authority FROM user_authorities WHERE user_id = ?"),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),anyLong())).thenAnswer(c->pageRight?List.of("PAGE_DIALOGS"):List.of("ROLE_USER"));
        when(jdbc.queryForList(eq("SELECT r.name AS role_name, r.permissions AS permissions FROM users u LEFT JOIN roles r ON r.id = u.role_id WHERE u.id = ? LIMIT 1"),anyLong())).thenReturn(List.of());
        when(settings.load()).thenReturn(config(77L));
        when(bitrix.listTasksForGroup(any(),any())).thenReturn(List.of(Map.of("id","101","title","Dismiss","description","+79991234567","status","2","closed_date","")));
        when(bitrix.listChecklistItems(any(),any())).thenReturn(List.of(Map.of("id","501","title","remove","is_complete",false)));
        when(iiko.disableCorporateDiscount(any(),any())).thenReturn(new IikoDirectoryService.MutationResult(true,"fixture success"));
    }
    private EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings config(Long group){return new EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings(group,List.of(),List.of("remove"),"([+]7[0-9]{10})",List.of(),List.of(),true);}
    private void account(long id,String name){var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("username",name);row.put("password",HASH);row.put("enabled",true);row.put("is_blocked",false);accounts.put(id,row);}
    private static Authentication actor(long id,String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"",true,AuthorityUtils.NO_AUTHORITIES),null,List.of());}
    private MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request,MvcResult page) throws Exception {Cookie cookie=page.getResponse().getCookie("XSRF-TOKEN");assertThat(cookie).isNotNull();var m=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.getResponse().getContentAsString());assertThat(m.find()).isTrue();return request.cookie(cookie).param("_csrf",m.group(1));}
    private MockHttpSession login(String name) throws Exception {var page=mvc.perform(get("/login")).andExpect(status().isOk()).andReturn();var result=mvc.perform(csrf(post("/login"),page).param("username",name).param("password",PASSWORD)).andExpect(redirectedUrl("/post-login")).andReturn();return (MockHttpSession)result.getRequest().getSession(false);}
    private MockHttpServletRequestBuilder start(MockHttpSession session,String body) throws Exception {var page=mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();return csrf(post(API).session(session),page).contentType("application/json").content(body);}
    private Object invoke(String method,Authentication auth) throws Throwable {var m=Arrays.stream(service.getClass().getMethods()).filter(x->x.getName().equals(method)&&x.getParameterCount()==(method.equals("getRun")?2:1)).findFirst().orElseThrow();Object identity=m.getParameterTypes()[method.equals("getRun")?1:0]==String.class?(auth==null?null:auth.getName()):auth;try{return method.equals("getRun")?m.invoke(service,10L,identity):m.invoke(service,identity);}catch(InvocationTargetException ex){throw ex.getCause();}}

    @Test void aCaseCollisionCannotCombineHistoriesAndLegacyRows() throws Exception {
        account(7,"reader");var session=login("reader");account(99,"ReAdEr");seed(10,7L,"reader",KEY);seed(11,7L,"previous name",KEY);seed(20,99L,"ReAdEr",KEY);seed(30,null,"reader",KEY);seed(40,7L,"reader","another automation");
        mvc.perform(get(API).session(session).param("actor_user_id","99").param("actor","ReAdEr")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].id").value(11)).andExpect(jsonPath("$.items[1].id").value(10)).andExpect(jsonPath("$.items[0].actor").value("previous name")).andExpect(jsonPath("$.items[0].actor_user_id").doesNotExist());assertThat(store.queriedOwners).containsExactly(7L);
    }
    @ParameterizedTest @ValueSource(strings={"foreign","unbound","other-automation","missing"})
    void deniedParentsNeverLoadTheirChildRecords(String mode) throws Exception {account(7,"reader");Long owner=switch(mode){case "foreign"->99L;case "unbound"->null;default->7L;};if(!mode.equals("missing"))seed(10,owner,"reader",mode.equals("other-automation")?"other":KEY);mvc.perform(get(API+"/10").session(login("reader"))).andExpect(status().isNotFound());assertThat(store.childQueries).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"list","details"})
    void freshLoginAfterRenameRetainsOwnHistory(String endpoint) throws Exception {account(7,"changed");seed(10,7L,"reader",KEY);var session=login("changed");if(endpoint.equals("list"))mvc.perform(get(API).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(10));else mvc.perform(get(API+"/10").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.run.actor").value("reader"));}
    @ParameterizedTest @ValueSource(strings={"list","details"})
    void reusedNamesDoNotInheritFormerAccountsHistory(String endpoint) throws Exception {account(99,"reader");seed(10,7L,"reader",KEY);seed(20,null,"reader",KEY);var session=login("reader");if(endpoint.equals("list"))mvc.perform(get(API).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());else mvc.perform(get(API+"/10").session(session)).andExpect(status().isNotFound());assertThat(store.childQueries).isEmpty();}
    @ParameterizedTest @ValueSource(strings={"reader","ReAdEr"," reader ","имя","anonymousUser"})
    void rawDisplayNamesDoNotControlNumericOwnership(String name) throws Exception {account(7,name);seed(10,7L,name,KEY);seed(20,99L,name,KEY);var session=login(name);mvc.perform(get(API).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(10)).andExpect(jsonPath("$.items[0].actor").value(name));mvc.perform(get(API+"/20").session(session)).andExpect(status().isNotFound());}
    @Test void ownedDetailsPreserveTheirChildrenAndResponseShape() throws Exception {account(7,"reader");seed(10,7L,"reader",KEY);mvc.perform(get(API+"/10").session(login("reader"))).andExpect(status().isOk()).andExpect(jsonPath("$.run.id").value(10)).andExpect(jsonPath("$.run.actor").value("reader")).andExpect(jsonPath("$.items[0].task_id").value("task-10")).andExpect(jsonPath("$.items[0].phone").value("+79990000000"));assertThat(store.childQueries).containsExactly(10L);}
    @Test void twentyMostRecentOwnedRunsUseAStableTieBreak() throws Exception {account(7,"reader");for(long id=1;id<=25;id++){seed(id,7L,"reader",KEY);store.rows.get(id).setStartedAt(OffsetDateTime.parse("2026-10-27T10:00:00Z"));}seed(99,99L,"reader",KEY);mvc.perform(get(API).session(login("reader"))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20)).andExpect(jsonPath("$.items[0].id").value(25)).andExpect(jsonPath("$.items[19].id").value(6));}
    @ParameterizedTest @ValueSource(strings={"dry-run","execute","missing-config","discovery-failure","mutation-failure"})
    void everyAuditSaveBindsTheAuthenticatedIdBeforeDiscovery(String mode) throws Exception {
        account(7," reader ");if(mode.equals("missing-config"))when(settings.load()).thenReturn(config(0L));if(mode.equals("discovery-failure"))when(bitrix.listTasksForGroup(any(),any())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,"fixture discovery failed"));if(mode.equals("mutation-failure"))when(iiko.disableCorporateDiscount(any(),any())).thenReturn(new IikoDirectoryService.MutationResult(false,"fixture rejected"));
        String body="{\"dry_run\":"+mode.equals("dry-run")+",\"selected_task_ids\":[\"101\"],\"actor_user_id\":99,\"actor\":\"neighbour\"}";mvc.perform(start(login(" reader "),body)).andExpect(status().isOk());assertThat(store.saved).hasSize(2).allSatisfy(saved->{assertThat(saved.owner()).isEqualTo(7L);assertThat(saved.actor()).isEqualTo(" reader ");});assertThat(store.saved.get(0).status()).isEqualTo("running");assertThat(store.saved.get(1).status()).isEqualTo(mode.equals("dry-run")||mode.equals("execute")?"success":"error");
    }
    @ParameterizedTest @ValueSource(longs={9007199254740993L,Long.MAX_VALUE})
    void ownerIdsRetainLongPrecisionInWritesAndQueries(long id) throws Exception {account(id,"reader");seed(10,id,"reader",KEY);var session=login("reader");mvc.perform(get(API).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(10));assertThat(store.queriedOwners).containsExactly(id);mvc.perform(start(session,"{\"dry_run\":true}")).andExpect(status().isOk());assertThat(store.saved).isNotEmpty().allSatisfy(saved->assertThat(saved.owner()).isEqualTo(id));}
    @ParameterizedTest @ValueSource(strings={"legacy-string","legacy-user","zero","negative","blank","disabled","unauthenticated","anonymous","absent"})
    void unboundOrInvalidServiceCallersCannotUseAStoredName(String mode) {
        Object principal=mode.equals("legacy-string")?"reader":mode.equals("legacy-user")?User.withUsername("reader").password("fixture").roles("USER").build():new PanelUserPrincipal(mode.equals("zero")?0:mode.equals("negative")?-1:7,mode.equals("blank")?" ":"reader","",!mode.equals("disabled"),List.of());
        Authentication auth=mode.equals("absent")?null:mode.equals("anonymous")?new AnonymousAuthenticationToken("fixture",principal,AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")):mode.equals("unauthenticated")?UsernamePasswordAuthenticationToken.unauthenticated(principal,null):UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
        assertThatThrownBy(()->invoke("listRuns",auth)).isInstanceOf(AccessDeniedException.class);assertThatThrownBy(()->invoke("getRun",auth)).isInstanceOf(AccessDeniedException.class);verifyNoInteractions(runs,items);
    }
    @ParameterizedTest @ValueSource(strings={"disabled","blocked","renamed","deleted"})
    void revokedSessionsCannotQueryHistory(String mode) throws Exception {account(7,"reader");var session=login("reader");switch(mode){case "disabled"->accounts.get(7L).put("enabled",false);case "blocked"->accounts.get(7L).put("is_blocked",true);case "renamed"->accounts.get(7L).put("username","changed");default->accounts.remove(7L);}mvc.perform(get(API).session(session)).andExpect(status().is3xxRedirection());verifyNoInteractions(runs,items);}
    @Test void pageRightIsRequiredForListAndDetails() throws Exception {account(7,"reader");pageRight=false;var session=login("reader");mvc.perform(get(API).session(session)).andExpect(status().isForbidden());mvc.perform(get(API+"/10").session(session)).andExpect(status().isForbidden());verifyNoInteractions(runs,items);}
    @Test void missingCsrfCannotCreateAnAuditOrCallProviders() throws Exception {account(7,"reader");mvc.perform(post(API).session(login("reader")).contentType("application/json").content("{\"dry_run\":true}")).andExpect(status().isForbidden());verifyNoInteractions(runs,items,bitrix,iiko);}
    @ParameterizedTest @ValueSource(strings={"","/10"})
    void repositoryFailureCannotFallBackToNameBasedHistory(String suffix) throws Exception {account(7,"reader");seed(10,7L,"reader",KEY);store.unavailable=true;mvc.perform(get(API+suffix).session(login("reader"))).andExpect(status().is5xxServerError()).andExpect(jsonPath("$.success").value(false));assertThat(store.childQueries).isEmpty();}
}

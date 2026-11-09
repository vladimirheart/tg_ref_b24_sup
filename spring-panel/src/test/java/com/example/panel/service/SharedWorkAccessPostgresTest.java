package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static com.example.panel.service.SharedWorkAccessService.*;
import static com.example.panel.support.GeneralTicketAdminTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

class SharedWorkAccessPostgresTest {
    static JdbcTemplate jdbc;
    static final long OWNER=9007199254740993L,READER=55,OTHER=99,ID=9007199254740997L;
    static final String RAW=" Owner/Ю ";
    SharedWorkAccessService service;
    Authentication owner,reader,other;
    DataSourceTransactionManager manager;

    @BeforeAll static void schema() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("shared_work_acl");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE,role_id BIGINT)");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT,permissions TEXT);CREATE TABLE user_authorities(user_id BIGINT,authority TEXT,PRIMARY KEY(user_id,authority))");
        jdbc.execute("CREATE TABLE tasks(id BIGINT PRIMARY KEY,creator TEXT);CREATE TABLE projects(id BIGINT PRIMARY KEY,lead_identity TEXT)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
        jdbc.update("INSERT INTO users(id,username) VALUES(1,'legacy')");
        jdbc.execute("INSERT INTO tasks VALUES(7,'legacy');INSERT INTO projects VALUES(7,'legacy')");
        jdbc.execute(new ClassPathResource("db/migration/postgresql/V88__shared_work_access.sql").getContentAsString(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT shared_owner_user_id FROM tasks WHERE id=7",Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_grants",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_project_members",Integer.class)).isZero();
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE users,roles,user_authorities,tasks,projects,shared_work_grants,shared_task_members,shared_project_members,business_access_grants CASCADE");
        jdbc.update("INSERT INTO users(id,username) VALUES(?,?),(55,'reader'),(99,'other')",OWNER,RAW);
        jdbc.update("INSERT INTO user_authorities VALUES(?,'ROLE_ADMIN'),(?,'PAGE_SETTINGS'),(?,'PAGE_TASKS'),(55,'PAGE_TASKS'),(99,'PAGE_TASKS')",OWNER,OWNER,OWNER);
        jdbc.update("INSERT INTO shared_work_grants(user_id,can_read,can_create_task,can_create_project) VALUES(?,TRUE,TRUE,TRUE),(55,TRUE,FALSE,FALSE)",OWNER);
        manager=new DataSourceTransactionManager(jdbc.getDataSource());
        var proxy=new ProxyFactory(new SharedWorkAccessService(jdbc));proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        service=(SharedWorkAccessService)proxy.getProxy();
        owner=bound(OWNER,RAW);reader=bound(READER,"reader");other=bound(OTHER,"other");
        for(Kind kind:Kind.values()){
            jdbc.update("INSERT INTO "+kind.roots+"(id,shared_owner_user_id) VALUES(?,?),(7,NULL)",ID,OWNER);
            register(kind,ID,owner);
        }
    }
    private void register(Kind kind,long id,Authentication actor) {
        new TransactionTemplate(manager).executeWithoutResult(s->service.registerCreated(kind,id,actor));
    }
    private void denied(Runnable operation){assertThatThrownBy(operation::run).isInstanceOf(ResponseStatusException.class).satisfies(f->assertThat(((ResponseStatusException)f).getStatusCode().value()).isEqualTo(404));}
    static Stream<Arguments> roles(){return Arrays.stream(Kind.values()).flatMap(k->Stream.of("OWNER","EDITOR","VIEWER").flatMap(r->Arrays.stream(Access.values()).map(a->Arguments.of(k,r,a))));}
    @ParameterizedTest @MethodSource("roles") void roleMatrixIsIndependentForEachObject(Kind kind,String role,Access action) {
        Authentication actor=owner;
        if(!role.equals("OWNER")){service.setMember(kind,ID,READER,Role.valueOf(role),owner);actor=reader;}
        Authentication current=actor;
        boolean allowed=role.equals("OWNER")||action==Access.READ||role.equals("EDITOR")&&action==Access.WRITE;
        if(allowed)assertThatCode(()->service.require(kind,ID,action,current)).doesNotThrowAnyException();
        else denied(()->service.require(kind,ID,action,current));
    }
    @ParameterizedTest @EnumSource(Kind.class) void globalReadAndMembershipAreBothRequiredAndDoNotCrossKinds(Kind kind) {
        assertThat(service.visibleIds(kind,other)).isEmpty();
        service.setGrant(owner,OTHER,new Grant(true,true,true));
        assertThat(service.visibleIds(kind,other)).isEmpty();
        service.setMember(kind,ID,OTHER,Role.EDITOR,owner);
        assertThat(service.visibleIds(kind,other)).containsExactly(Long.toString(ID));
        assertThat(service.visibleIds(kind==Kind.TASK?Kind.PROJECT:Kind.TASK,other)).isEmpty();
        service.setGrant(owner,OTHER,new Grant(false,false,false));
        denied(()->service.require(kind,ID,Access.READ,other));assertThat(service.visibleIds(kind,other)).isEmpty();
    }
    @ParameterizedTest @EnumSource(Kind.class) void businessAndPortalAuthorityNeverImplicitlyGrantObjectRead(Kind kind) {
        jdbc.update("INSERT INTO business_access_grants VALUES(99,'ALL_BUSINESSES','ADMIN',TRUE)");
        jdbc.execute("INSERT INTO user_authorities VALUES(99,'PAGE_SETTINGS'),(99,'ROLE_PORTAL_ADMIN')");
        denied(()->service.require(kind,ID,Access.READ,other));
        assertThatThrownBy(()->service.accounts(other)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.visibleIds(kind,other)).isEmpty();
    }
    @ParameterizedTest @EnumSource(Kind.class) void registrationRequiresNumericOwnerAndTheMatchingCreationGrant(Kind kind) {
        denied(()->register(kind,7,owner));
        denied(()->register(kind,ID,reader));
        jdbc.update("DELETE FROM "+kind.members+" WHERE "+kind.key+"=?",ID);
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='ROLE_ADMIN'",OWNER);
        jdbc.update("UPDATE shared_work_grants SET "+kind.create+"=FALSE WHERE user_id=?",OWNER);
        denied(()->register(kind,ID,owner));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+kind.members,Integer.class)).isZero();
    }
    @ParameterizedTest @EnumSource(Kind.class) void registrationRetryAndParticipantRevokeAreIdempotent(Kind kind) {
        register(kind,ID,owner);register(kind,ID,owner);
        assertThat(service.members(kind,ID,owner)).hasSize(1);
        service.setMember(kind,ID,READER,Role.VIEWER,owner);service.setMember(kind,ID,READER,Role.EDITOR,owner);
        assertThat(service.members(kind,ID,owner)).hasSize(2);
        service.require(kind,ID,Access.WRITE,reader);
        service.removeMember(kind,ID,READER,owner);service.removeMember(kind,ID,READER,owner);
        denied(()->service.require(kind,ID,Access.READ,reader));
    }
    @ParameterizedTest @EnumSource(Kind.class) void editorsCannotManageParticipantsAndOwnerCannotBeRemoved(Kind kind) {
        service.setMember(kind,ID,READER,Role.EDITOR,owner);
        denied(()->service.setMember(kind,ID,OTHER,Role.VIEWER,reader));
        denied(()->service.removeMember(kind,ID,OWNER,reader));
        for(Runnable op:List.<Runnable>of(()->service.setMember(kind,ID,OWNER,Role.VIEWER,owner),()->service.removeMember(kind,ID,OWNER,owner))){
            assertThatThrownBy(op::run).isInstanceOf(ResponseStatusException.class).satisfies(e->assertThat(((ResponseStatusException)e).getStatusCode().value()).isEqualTo(409));
        }
        assertThat(service.members(kind,ID,owner)).hasSize(2);
    }
    static Stream<Arguments> invalidActors(){return Arrays.stream(Kind.values()).flatMap(k->Stream.of("disabled","blocked","renamed","case","ambiguous","wrong-id","unbound","trimmed","null","read-revoked","membership-revoked").map(m->Arguments.of(k,m)));}
    @ParameterizedTest @MethodSource("invalidActors") void currentIdentityAndRevocationsAreCheckedAtEveryOperation(Kind kind,String mode) {
        service.setMember(kind,ID,READER,Role.EDITOR,owner);
        Authentication actor=reader;
        switch(mode){
            case "disabled"->jdbc.execute("UPDATE users SET enabled=FALSE WHERE id=55");
            case "blocked"->jdbc.execute("UPDATE users SET is_blocked=TRUE WHERE id=55");
            case "renamed"->jdbc.execute("UPDATE users SET username='renamed' WHERE id=55");
            case "case"->jdbc.execute("UPDATE users SET username='READER' WHERE id=55");
            case "ambiguous"->jdbc.execute("INSERT INTO users(id,username,enabled) VALUES(77,'READER',FALSE)");
            case "wrong-id"->actor=bound(OTHER,"reader");
            case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated("reader",null,List.of());
            case "trimmed"->{jdbc.execute("UPDATE users SET username=' reader ' WHERE id=55");actor=bound(READER,"reader");}
            case "null"->actor=null;
            case "read-revoked"->service.setGrant(owner,READER,new Grant(false,false,false));
            case "membership-revoked"->service.removeMember(kind,ID,READER,owner);
            default->throw new AssertionError(mode);
        }
        Authentication current=actor;
        denied(()->service.require(kind,ID,Access.READ,current));denied(()->service.require(kind,ID,Access.WRITE,current));
    }
    @ParameterizedTest @EnumSource(Kind.class) void rawUnicodeNamesAndBigintIdsRoundtripWithoutAliases(Kind kind) {
        assertThat(service.visibleIds(kind,owner)).containsExactly("7",Long.toString(ID));
        assertThat(service.members(kind,ID,owner)).containsExactly(new Member(Long.toString(OWNER),RAW,"OWNER"));
        jdbc.execute("UPDATE users SET username=' reader ' WHERE id=55");reader=bound(READER," reader ");
        service.setMember(kind,ID,READER,Role.VIEWER,owner);service.require(kind,ID,Access.READ,reader);
        denied(()->service.require(kind,ID,Access.READ,bound(READER,"reader")));
    }
    @ParameterizedTest @EnumSource(Kind.class) void invalidTargetsAndMissingGlobalReadNeverChangeMembership(Kind kind) {
        var before=service.members(kind,ID,owner);
        denied(()->service.setMember(kind,ID,OTHER,Role.VIEWER,owner));
        service.setGrant(owner,OTHER,new Grant(true,false,false));
        jdbc.execute("UPDATE users SET is_blocked=TRUE WHERE id=99");
        denied(()->service.setMember(kind,ID,OTHER,Role.VIEWER,owner));
        assertThat(service.members(kind,ID,owner)).isEqualTo(before);
    }
    static Stream<Arguments> administratorSources(){return roleSources().stream().flatMap(s->Stream.of(false,true).map(write->Arguments.of(s,write)));}
    @ParameterizedTest @MethodSource("administratorSources") void grantAdministrationUsesCurrentDirectOrAssignedSystemRights(String source,boolean write) {
        assignRole(jdbc,OWNER,source);
        Runnable operation=write?()->service.setGrant(owner,OTHER,new Grant(true,false,false)):()->service.accounts(owner);
        if(roleAllows(source)||Set.of("settings-whitespace","malformed","no-settings","non-list").contains(source))assertThatCode(operation::run).doesNotThrowAnyException();
        else {assertThatThrownBy(operation::run).isInstanceOf(AccessDeniedException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_grants WHERE user_id=99",Integer.class)).isZero();}
    }
    @Test void grantHierarchyAndInvalidIdsAreRejectedWithoutMutation() {
        assertThatThrownBy(()->service.setGrant(owner,READER,new Grant(false,true,false))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.setGrant(owner,0,new Grant(true,false,false))).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT can_create_task FROM shared_work_grants WHERE user_id=55",Boolean.class)).isFalse();
        assertThatThrownBy(()->jdbc.execute("INSERT INTO shared_work_grants(user_id,can_read,can_create_task) VALUES(99,FALSE,TRUE)")).isInstanceOf(DataAccessException.class);
    }
    @ParameterizedTest @EnumSource(Kind.class) void databaseConstraintsRejectBadRolesAndSecondOwner(Kind kind) {
        assertThatThrownBy(()->jdbc.update("INSERT INTO "+kind.members+"("+kind.key+",user_id,access_role) VALUES(?,55,'ADMIN')",ID)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO "+kind.members+"("+kind.key+",user_id,access_role) VALUES(?,55,'OWNER')",ID)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO "+kind.members+"("+kind.key+",user_id,access_role) VALUES(?,12345,'VIEWER')",ID)).isInstanceOf(DataAccessException.class);
    }
    static Stream<Arguments> revocations(){return Arrays.stream(Kind.values()).flatMap(k->Stream.of(false,true).map(grant->Arguments.of(k,grant)));}
    @ParameterizedTest @MethodSource("revocations") void concurrentRevokeWaitsForTheOwningTransaction(Kind kind,boolean grant) throws Exception {
        service.setMember(kind,ID,READER,Role.VIEWER,owner);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var reading=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{
                service.require(kind,ID,Access.READ,reader);entered.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release missing");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            }));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var revoke=executor.submit(()->{if(grant)service.setGrant(owner,READER,new Grant(false,false,false));else service.removeMember(kind,ID,READER,owner);});
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE NOT granted)",Boolean.class)))Thread.sleep(20);
            assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE NOT granted)",Boolean.class)).isTrue();
            assertThat(revoke.isDone()).isFalse();release.countDown();reading.get(10,TimeUnit.SECONDS);revoke.get(10,TimeUnit.SECONDS);
            denied(()->service.require(kind,ID,Access.READ,reader));
        }finally{release.countDown();executor.shutdownNow();}
    }
    @ParameterizedTest @EnumSource(Kind.class) void creationUsesTheLockedGrantAfterConcurrentRevocation(Kind kind) throws Exception {
        jdbc.update("INSERT INTO "+kind.roots+"(id,shared_owner_user_id) VALUES(88,?)",OWNER);
        jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='ROLE_ADMIN'",OWNER);
        jdbc.execute("INSERT INTO user_authorities VALUES(99,'ROLE_ADMIN')");
        var updated=new CountDownLatch(1);var release=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var revoking=executor.submit(()->new TransactionTemplate(manager).executeWithoutResult(s->{
                service.setGrant(other,OWNER,new Grant(true,kind!=Kind.TASK,kind!=Kind.PROJECT));updated.countDown();
                try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("release missing");}
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
            }));
            assertThat(updated.await(5,TimeUnit.SECONDS)).isTrue();
            var registering=executor.submit(()->register(kind,88,owner));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE NOT granted)",Boolean.class)))Thread.sleep(20);
            assertThat(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE NOT granted)",Boolean.class)).isTrue();
            release.countDown();revoking.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->registering.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ResponseStatusException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+kind.members+" WHERE "+kind.key+"=88",Integer.class)).isZero();
        }finally{release.countDown();executor.shutdownNow();}
    }
    @Test void ownerRegistrationRequiresAnExistingCreationTransaction() {
        assertThatThrownBy(()->service.registerCreated(Kind.TASK,ID,owner))
            .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }
    @ParameterizedTest @EnumSource(Kind.class) void failedCreationRollsBackOwnerAndMembershipTogether(Kind kind) {
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(s->{
            jdbc.update("INSERT INTO "+kind.roots+"(id,shared_owner_user_id) VALUES(88,?)",OWNER);
            service.registerCreated(kind,88,owner);throw new IllegalStateException("rollback fixture");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+kind.roots+" WHERE id=88",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+kind.members+" WHERE "+kind.key+"=88",Integer.class)).isZero();
    }
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    @org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class WebConfig {
        @org.springframework.context.annotation.Bean JdbcTemplate jdbcTemplate(){return jdbc;}
        @org.springframework.context.annotation.Bean SharedWorkAccessService access(JdbcTemplate template){return new SharedWorkAccessService(template);}
        @org.springframework.context.annotation.Bean com.example.panel.controller.SharedWorkAccessController controller(SharedWorkAccessService access){return new com.example.panel.controller.SharedWorkAccessController(access);}
        @org.springframework.context.annotation.Bean org.springframework.transaction.PlatformTransactionManager transactionManager(JdbcTemplate template){return new DataSourceTransactionManager(template.getDataSource());}
        @org.springframework.context.annotation.Bean org.springframework.security.web.SecurityFilterChain chain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(r->r.anyRequest().authenticated())
                .exceptionHandling(e->e.authenticationEntryPoint((request,response,failure)->response.sendError(401))).build();
        }
    }
    private static class Web implements AutoCloseable {
        final org.springframework.web.context.support.AnnotationConfigWebApplicationContext context=new org.springframework.web.context.support.AnnotationConfigWebApplicationContext();
        final org.springframework.test.web.servlet.MockMvc mvc;
        Web(){
            context.setServletContext(new org.springframework.mock.web.MockServletContext());context.register(WebConfig.class);context.refresh();
            mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        }
        public void close(){context.close();}
    }
    private Authentication page(Authentication authentication) {
        var account=(PanelUserPrincipal)authentication.getPrincipal();
        var rights=new ArrayList<org.springframework.security.core.GrantedAuthority>(authentication.getAuthorities());
        rights.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("PAGE_TASKS"));
        return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(account.getUserId(),account.getUsername(),"unused",true,rights),null,rights);
    }
    @Test void httpAnonymousAndStaleAdministratorClaimsDoNotRevealInventory() throws Exception {
        try(var web=new Web()){
            web.mvc.perform(get("/api/shared-work-admin/access")).andExpect(status().isUnauthorized());
            web.mvc.perform(get("/api/shared-work-admin/access").with(authentication(other))).andExpect(status().isForbidden());
            web.mvc.perform(get("/api/shared-work-admin/access").with(authentication(owner))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$[?(@.userId=='"+OWNER+"')].username").value(RAW));
        }
    }
    @Test void httpGrantMutationsRequireCsrfAndCurrentSystemAdministration() throws Exception {
        try(var web=new Web()){
            String path="/api/shared-work-admin/access/99",body="{\"canRead\":true,\"canCreateTask\":false,\"canCreateProject\":false}";
            web.mvc.perform(put(path).contentType("application/json").content(body).with(authentication(owner))).andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shared_work_grants WHERE user_id=99",Integer.class)).isZero();
            web.mvc.perform(put(path).contentType("application/json").content(body).with(authentication(reader)).with(csrf())).andExpect(status().isForbidden());
            web.mvc.perform(put(path).contentType("application/json").content(body).with(authentication(owner)).with(csrf())).andExpect(status().isNoContent());
            assertThat(service.visibleIds(Kind.TASK,other)).isEmpty();
        }
    }
    @Test void httpMemberDtoPreservesBigintAndRawUnicodeIdentity() throws Exception {
        try(var web=new Web()){
            web.mvc.perform(get("/api/shared-work/TASK/"+ID+"/members").with(authentication(page(owner)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$[0].userId").isString()).andExpect(jsonPath("$[0].userId").value(Long.toString(OWNER)))
                .andExpect(jsonPath("$[0].username").value(RAW)).andExpect(jsonPath("$[0].role").value("OWNER"));
        }
    }
    @Test void httpOwnerChangesRoleAndRevokeTakesEffectOnNextRequest() throws Exception {
        try(var web=new Web()){
            String path="/api/shared-work/PROJECT/"+ID+"/members";
            web.mvc.perform(put(path+"/55").contentType("application/json").content("{\"role\":\"EDITOR\"}")
                .with(authentication(page(owner))).with(csrf())).andExpect(status().isNoContent());
            service.require(Kind.PROJECT,ID,Access.WRITE,reader);
            web.mvc.perform(delete(path+"/55").with(authentication(page(owner))).with(csrf())).andExpect(status().isNoContent());
            web.mvc.perform(get(path).with(authentication(page(reader)))).andExpect(status().isNotFound());
        }
    }
    @Test void httpMembershipCannotPromoteOwnerAndSystemAdminNeedsNoPageAuthority() throws Exception {
        try(var web=new Web()){
            String path="/api/shared-work/TASK/"+ID+"/members";
            web.mvc.perform(get(path).with(authentication(owner))).andExpect(status().isOk());
            web.mvc.perform(put(path+"/55").contentType("application/json").content("{\"role\":\"OWNER\"}")
                .with(authentication(page(owner))).with(csrf())).andExpect(status().isBadRequest());
            assertThat(service.members(Kind.TASK,ID,owner)).hasSize(1);
        }
    }
    @Test void httpInvalidNumericIdentityDoesNotRoundOrCoerce() throws Exception {
        try(var web=new Web()){
            web.mvc.perform(get("/api/shared-work/TASK/9223372036854775808/members").with(authentication(page(owner)))).andExpect(status().isBadRequest());
            web.mvc.perform(get("/api/shared-work/TASK/0/members").with(authentication(page(owner)))).andExpect(status().isBadRequest());
        }
    }
}

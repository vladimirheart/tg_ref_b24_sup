package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import static com.example.panel.support.GeneralTicketAdminTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class GeneralTicketAdminAuthorizationPostgresTest {
    static JdbcTemplate jdbc;
    static final long ID=9007199254740993L,TARGET=55;
    static final String RAW=" Admin/Ю ";
    Authentication actor;
    GeneralTicketAccessService service;
    @BeforeAll static void schema(){
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_admin_authorization");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE,role_id BIGINT)");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT,permissions TEXT)");
        jdbc.execute("CREATE TABLE user_authorities(user_id BIGINT,authority TEXT,PRIMARY KEY(user_id,authority))");
        for(String table:List.of("general_ticket_read_grants","general_ticket_operate_grants"))jdbc.execute("CREATE TABLE "+table+"(user_id BIGINT PRIMARY KEY,is_active BOOLEAN,updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN)");
    }
    @BeforeEach void setup(){
        jdbc.execute("TRUNCATE users,roles,user_authorities,general_ticket_read_grants,general_ticket_operate_grants,business_access_grants");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(?,?,'unused'),(55,'target','unused')",ID,RAW);
        for(String right:List.of("ROLE_ADMIN","PAGE_SETTINGS"))jdbc.update("INSERT INTO user_authorities VALUES(?,?)",ID,right);
        actor=bound(ID,RAW);service=new GeneralTicketAccessService(jdbc);
    }
    void prepare(Operation op){
        jdbc.update("INSERT INTO general_ticket_read_grants(user_id,is_active) VALUES(55,?)",op!=Operation.grantRead);
        jdbc.update("INSERT INTO general_ticket_operate_grants(user_id,is_active) VALUES(55,?)",op==Operation.revokeRead||op==Operation.revokeOperate||op==Operation.accounts);
    }
    Map<String,List<Map<String,Object>>> snapshot(){var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("general_ticket_read_grants","general_ticket_operate_grants"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY user_id"));return result;}
    boolean allowed(GeneralTicketAccessService access,Operation op,Authentication authentication){try{Object result=call(access,op,authentication,TARGET);return result instanceof Boolean value?value:result instanceof List<?> list&&!list.isEmpty();}catch(AccessDeniedException e){return false;}}
    void denied(Operation op){var before=snapshot();assertThat(allowed(service,op,actor)).isFalse();assertThat(snapshot()).isEqualTo(before);}
    void mutate(String mode){switch(mode){
        case "role","page"->jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority=?",ID,mode.equals("role")?"ROLE_ADMIN":"PAGE_SETTINGS");
        case "rename-reuse","delete-reuse"->{if(mode.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=?",ID);else jdbc.update("DELETE FROM users WHERE id=?",ID);jdbc.update("INSERT INTO users(id,username,password) VALUES(99,?,'unused')",RAW);jdbc.update("INSERT INTO user_authorities VALUES(99,'ROLE_ADMIN'),(99,'PAGE_SETTINGS')");}
        case "case"->jdbc.update("UPDATE users SET username=upper(username) WHERE id=?",ID);
        case "wrong-id"->actor=bound(99,RAW);
        case "unbound"->actor=UsernamePasswordAuthenticationToken.authenticated(RAW,null,actor.getAuthorities());
        case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",ID);
        case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",ID);
        case "ambiguous"->jdbc.update("INSERT INTO users(id,username,enabled) VALUES(99,upper(?),FALSE)",RAW);
        case "portal"->{mutate("role");jdbc.update("INSERT INTO user_authorities VALUES(?,'ROLE_PORTAL_ADMIN')",ID);}
        case "business"->{mutate("role");jdbc.update("INSERT INTO business_access_grants VALUES(?,'ALL_BUSINESSES','ADMIN',TRUE)",ID);}
        case "general-only"->{jdbc.update("DELETE FROM user_authorities WHERE user_id=?",ID);jdbc.update("INSERT INTO general_ticket_read_grants(user_id,is_active) VALUES(?,TRUE)",ID);jdbc.update("INSERT INTO general_ticket_operate_grants(user_id,is_active) VALUES(?,TRUE)",ID);}
        default->throw new AssertionError(mode);
    }}
    static Stream<Arguments> invalid(){return Stream.of("role","page","rename-reuse","delete-reuse","case","wrong-id","unbound","disabled","blocked","ambiguous","portal","business","general-only").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("invalid") void onlyTheBoundCurrentSystemAdministratorMayManageAccess(String mode,Operation op){prepare(op);mutate(mode);denied(op);}
    @ParameterizedTest @EnumSource(Operation.class) void anAdministratorNeedsNoGeneralGrant(Operation op){
        prepare(op);assertThat(service.canRead(ID)).isFalse();assertThat(service.canOperate(ID)).isFalse();assertThat(allowed(service,op,actor)).isTrue();
        if(op==Operation.accounts)return;
        boolean read=jdbc.queryForObject("SELECT is_active FROM general_ticket_read_grants WHERE user_id=55",Boolean.class),operate=jdbc.queryForObject("SELECT is_active FROM general_ticket_operate_grants WHERE user_id=55",Boolean.class);
        assertThat(read).isEqualTo(op!=Operation.revokeRead);assertThat(operate).isEqualTo(op==Operation.grantOperate);
    }
    static Stream<Arguments> raw(){return Stream.of(ID,Long.MAX_VALUE).flatMap(id->Stream.of(" Admin ","İ").flatMap(n->Arrays.stream(Operation.values()).map(o->Arguments.of(id,n,o))));}
    @ParameterizedTest @MethodSource("raw") void exactRawNamesAndBigintIdsRemainBound(long id,String name,Operation op){
        jdbc.update("UPDATE users SET id=?,username=? WHERE id=?",id,name,ID);jdbc.update("UPDATE user_authorities SET user_id=? WHERE user_id=?",id,ID);actor=bound(id,name);prepare(op);assertThat(allowed(service,op,actor)).isTrue();
    }
    static Stream<Arguments> boundary(){return Stream.of("role","page","rename-reuse").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("boundary") void authorizationIsPartOfTheOwningStatement(String mode,Operation op){
        prepare(op);var fired=new AtomicBoolean();var original=jdbc.getDataSource();
        var observed=new DelegatingDataSource(original){@Override public Connection getConnection()throws java.sql.SQLException{
            var connection=super.getConnection();return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if((method.getName().equals("prepareStatement")||method.getName().equals("createStatement"))&&fired.compareAndSet(false,true))mutate(mode);
                try{return method.invoke(connection,args);}catch(InvocationTargetException failure){throw failure.getCause();}
            });
        }};
        service=new GeneralTicketAccessService(new JdbcTemplate(observed));denied(op);assertThat(fired).isTrue();
    }
    static Stream<Arguments> waits(){return Stream.of("role","page","rename","assigned-name","assigned-page","assigned-pointer").flatMap(m->Arrays.stream(Operation.values()).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("waits") void aWaitingStatementRechecksCommittedAdministratorRevocation(String mode,Operation op)throws Exception{
        prepare(op);if(mode.startsWith("assigned-"))assignRole(jdbc,ID,"role");var before=snapshot();var pool=Executors.newSingleThreadExecutor();
        try(var blocker=jdbc.getDataSource().getConnection();var worker=jdbc.getDataSource().getConnection()){
            blocker.setAutoCommit(false);
            String sql=switch(mode){case "rename"->"UPDATE users SET username='renamed' WHERE id=?";case "assigned-name"->"UPDATE roles SET name='operator' WHERE id=?";case "assigned-page"->"UPDATE roles SET permissions='{\"pages\":[]}' WHERE id=?";case "assigned-pointer"->"UPDATE users SET role_id=200 WHERE id=?";default->"DELETE FROM user_authorities WHERE user_id=? AND authority=?";};
            try(var statement=blocker.prepareStatement(sql)){statement.setLong(1,mode.equals("assigned-name")||mode.equals("assigned-page")?100:ID);if(mode.equals("role")||mode.equals("page"))statement.setString(2,mode.equals("role")?"ROLE_ADMIN":"PAGE_SETTINGS");statement.executeUpdate();}
            var workerJdbc=new JdbcTemplate(new SingleConnectionDataSource(worker,true));int pid=workerJdbc.queryForObject("SELECT pg_backend_pid()",Integer.class);var access=new GeneralTicketAccessService(workerJdbc);var captured=actor;
            var pending=pool.submit(()->allowed(access,op,captured));boolean waiting=false;long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(!pending.isDone()&&System.nanoTime()<until){waiting=Boolean.TRUE.equals(jdbc.queryForObject("SELECT wait_event_type='Lock' FROM pg_stat_activity WHERE pid=?",Boolean.class,pid));if(waiting)break;Thread.sleep(10);}
            try{assertThat(waiting).as("owning SQL waits for the administrator/authority row lock").isTrue();blocker.commit();assertThat(pending.get(5,TimeUnit.SECONDS)).isFalse();assertThat(snapshot()).isEqualTo(before);}
            finally{blocker.rollback();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
        }finally{pool.shutdownNow();}
    }
    static Stream<Arguments> targetPolicy(){return Stream.of("disabled","blocked","ambiguous","missing-read").flatMap(m->Stream.of(Operation.grantRead,Operation.grantOperate).map(o->Arguments.of(m,o)));}
    @ParameterizedTest @MethodSource("targetPolicy") void recipientReadAndOperatePoliciesStaySeparate(String mode,Operation op){
        prepare(op);switch(mode){case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=55");case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=55");case "ambiguous"->jdbc.update("INSERT INTO users(id,username) VALUES(99,'TARGET')");default->jdbc.update("DELETE FROM general_ticket_read_grants WHERE user_id=55");}
        assertThat(allowed(service,op,actor)).isEqualTo(op==Operation.grantRead);
    }
    @ParameterizedTest @EnumSource(value=Operation.class,names="accounts",mode=EnumSource.Mode.EXCLUDE) void repeatedMutationsRemainIdempotent(Operation op){prepare(op);assertThat(allowed(service,op,actor)).isTrue();var before=jdbc.queryForList("SELECT is_active FROM general_ticket_read_grants UNION ALL SELECT is_active FROM general_ticket_operate_grants");assertThat(allowed(service,op,actor)).isTrue();assertThat(jdbc.queryForList("SELECT is_active FROM general_ticket_read_grants UNION ALL SELECT is_active FROM general_ticket_operate_grants")).isEqualTo(before);}
    @ParameterizedTest @EnumSource(value=Operation.class,names="accounts",mode=EnumSource.Mode.EXCLUDE) void absentTargetsStillReturnFalse(Operation op){prepare(op);jdbc.execute("TRUNCATE general_ticket_read_grants,general_ticket_operate_grants");jdbc.update("DELETE FROM users WHERE id=55");assertThat(allowed(service,op,actor)).isFalse();}
    @ParameterizedTest @EnumSource(value=Operation.class,names="accounts",mode=EnumSource.Mode.EXCLUDE) void invalidTargetIdsRemainRejected(Operation op){prepare(op);var before=snapshot();assertThatThrownBy(()->call(service,op,actor,0)).isInstanceOf(IllegalArgumentException.class);assertThat(snapshot()).isEqualTo(before);}
    @ParameterizedTest @EnumSource(Operation.class) void missingAuthorityStorageNeverPermitsAnOperation(Operation op){prepare(op);var before=snapshot();jdbc.execute("ALTER TABLE user_authorities RENAME TO unavailable_authorities");try{assertThatThrownBy(()->call(service,op,actor,TARGET)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(snapshot()).isEqualTo(before);}finally{jdbc.execute("ALTER TABLE unavailable_authorities RENAME TO user_authorities");}}
    @Test void aSeparateFixtureAdministratorNeverPromotesTheRecipient(){prepare(Operation.accounts);var before=snapshot();var administrator=administrator(jdbc);assertThat(allowed(service,Operation.accounts,administrator)).isTrue();assertThat(service.canRead(9007199254740000L)).isFalse();assertThat(service.canOperate(9007199254740000L)).isFalse();assertThat(snapshot()).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_authorities WHERE user_id=55",Integer.class)).isZero();}
    static Stream<Arguments> assignedSources(){return roleSources().stream().flatMap(source->Arrays.stream(Operation.values()).map(op->Arguments.of(source,op)));}
    @ParameterizedTest @MethodSource("assignedSources") void effectiveDirectAndAssignedRightsKeepTheLoginPolicy(String source,Operation op){prepare(op);assignRole(jdbc,ID,source);var before=snapshot();assertThat(allowed(service,op,actor)).isEqualTo(roleAllows(source));if(!roleAllows(source)||op==Operation.accounts)assertThat(snapshot()).isEqualTo(before);}
    static Stream<Arguments> roleBoundary(){return Stream.of("name","page","pointer").flatMap(source->Arrays.stream(Operation.values()).map(op->Arguments.of(source,op)));}
    @ParameterizedTest @MethodSource("roleBoundary") void roleSnapshotsCannotAuthorizeAfterTheirSourceChanges(String mutation,Operation op){
        prepare(op);assignRole(jdbc,ID,"role");var statements=new java.util.concurrent.atomic.AtomicInteger();var fired=new AtomicBoolean();
        var observed=new DelegatingDataSource(jdbc.getDataSource()){@Override public Connection getConnection()throws java.sql.SQLException{
            var connection=super.getConnection();return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if((method.getName().equals("prepareStatement")||method.getName().equals("createStatement"))&&statements.incrementAndGet()==2){assertThat(fired.compareAndSet(false,true)).isTrue();changeAssignedRole(jdbc,ID,mutation);}
                try{return method.invoke(connection,args);}catch(InvocationTargetException failure){throw failure.getCause();}
            });
        }};
        service=new GeneralTicketAccessService(new JdbcTemplate(observed));denied(op);assertThat(fired).isTrue();
    }
    @ParameterizedTest @EnumSource(Operation.class) void missingRoleStorageNeverPermitsAnOperation(Operation op){prepare(op);var before=snapshot();jdbc.execute("ALTER TABLE roles RENAME TO unavailable_roles");try{assertThatThrownBy(()->call(service,op,actor,TARGET)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(snapshot()).isEqualTo(before);}finally{jdbc.execute("ALTER TABLE unavailable_roles RENAME TO roles");}}
}

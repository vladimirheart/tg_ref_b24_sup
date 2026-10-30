package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.*;

/** Frozen before the product change; reflection allows the old unbound API to demonstrate RED. */
public class BusinessAdministratorAccountPostgresTest {
    public static final List<String> OPERATIONS=List.of("overview","listChannels","previewChannelBinding","saveChannelBinding",
        "deactivateChannelBinding","createBusiness","renameBusiness","setBusinessStatus","grantMembership","revokeMembership",
        "grantAllBusinesses","revokeAllBusinesses","createLocation","setLocationStatus");
    static final long ACTOR=9007199254740993L;
    static final ThreadLocal<org.springframework.jdbc.datasource.SingleConnectionDataSource> CONNECTION=new ThreadLocal<>();
    @org.junit.jupiter.api.AfterEach void close(){var source=CONNECTION.get();if(source!=null)source.destroy();CONNECTION.remove();}
    public static Authentication auth(long id,String name){return UsernamePasswordAuthenticationToken.authenticated(
        new PanelUserPrincipal(id,name,"fixture",true,AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_SETTINGS")),null,
        AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_SETTINGS"));}
    public static void securitySchema(JdbcTemplate jdbc){
        jdbc.execute("CREATE TABLE IF NOT EXISTS roles(id BIGINT PRIMARY KEY,name TEXT,permissions TEXT)");
        jdbc.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE, ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN DEFAULT FALSE, ADD COLUMN IF NOT EXISTS role_id BIGINT");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_authorities(user_id BIGINT,authority TEXT)");
    }
    // Positive ID for legacy fixtures; no existing test account or grant is changed.
    public static Authentication fixtureAdministrator(JdbcTemplate jdbc){securitySchema(jdbc);jdbc.update("INSERT INTO users(id,username) VALUES(9223372036854775806,'fixture-admin') ON CONFLICT(id) DO NOTHING");jdbc.update("INSERT INTO user_authorities SELECT 9223372036854775806,authority FROM (VALUES('ROLE_ADMIN'),('PAGE_SETTINGS')) a(authority) WHERE NOT EXISTS(SELECT 1 FROM user_authorities WHERE user_id=9223372036854775806 AND user_authorities.authority=a.authority)");return auth(9223372036854775806L,"fixture-admin");}
    public static JdbcTemplate fresh()throws Exception{
        var raw=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_admin_account");
        var source=new org.springframework.jdbc.datasource.SingleConnectionDataSource(raw.getDataSource().getConnection(),true);CONNECTION.set(source);var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT UNIQUE NOT NULL)");securitySchema(jdbc);
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY,channel_name TEXT DEFAULT '',platform TEXT DEFAULT 'telegram',is_active BOOLEAN DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,PRIMARY KEY(user_id,ticket_id))");
        for(String sql:Files.readString(Path.of("src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql")).split(";"))if(!sql.isBlank())jdbc.execute(sql);
        jdbc.update("INSERT INTO users(id,username) VALUES(?,'caller'),(55,'target')",ACTOR);
        jdbc.update("INSERT INTO user_authorities VALUES(?,'ROLE_ADMIN'),(?,'PAGE_SETTINGS')",ACTOR,ACTOR);
        jdbc.update("INSERT INTO channels(id) VALUES(10)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,1,'VIEWER')");
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(55,'ALL_BUSINESSES','VIEWER')");
        jdbc.update("INSERT INTO business_locations(id,business_id,location_name,normalized_location_name) OVERRIDING SYSTEM VALUE VALUES(20,1,'Existing','existing')");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id,business_id,resolution_mode) VALUES(10,1,'SINGLE_BUSINESS')");
        return jdbc;
    }
    static Object invoke(BusinessAdministrationService service,String op,Authentication auth)throws Exception{
        Object[] args=switch(op){
            case "overview","listChannels"->new Object[0];case "previewChannelBinding"->new Object[]{10L};
            case "saveChannelBinding"->new Object[]{10L,1L,"SINGLE_BUSINESS"};case "deactivateChannelBinding"->new Object[]{10L,1L};
            case "createBusiness"->new Object[]{"new-business","Created"};case "renameBusiness"->new Object[]{1L,"Renamed"};
            case "setBusinessStatus"->new Object[]{1L,"archived"};case "grantMembership"->new Object[]{1L,55L,"MANAGER"};
            case "revokeMembership"->new Object[]{1L,55L};case "grantAllBusinesses"->new Object[]{55L,"MANAGER"};
            case "revokeAllBusinesses"->new Object[]{55L};case "createLocation"->new Object[]{1L,"New location","store","City"};
            case "setLocationStatus"->new Object[]{1L,20L,"archived"};default->throw new AssertionError(op);};
        var methods=Arrays.stream(BusinessAdministrationService.class.getMethods()).filter(m->m.getName().equals(op)).toList();
        var method=methods.stream().filter(m->m.getParameterCount()==args.length+1).findFirst().orElse(methods.get(0));
        Object[] actual=args;if(method.getParameterCount()>args.length){actual=new Object[args.length+1];actual[0]=auth;System.arraycopy(args,0,actual,1,args.length);}
        try{return method.invoke(service,actual);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception ex)throw ex;throw e;}
    }
    static String state(JdbcTemplate jdbc){return Stream.of("businesses","business_locations","business_memberships","business_access_grants","business_channel_bindings","tickets")
        .map(t->jdbc.queryForObject("SELECT COALESCE(jsonb_agg(row ORDER BY row::text)::text,'[]') FROM (SELECT to_jsonb(t) row FROM "+t+" t) rows",String.class)).collect(Collectors.joining("\n"));}
    static void change(JdbcTemplate jdbc,String mode){switch(mode){
        case "rename-reuse"-> {jdbc.update("UPDATE users SET username='retired' WHERE id=?",ACTOR);jdbc.update("INSERT INTO users(id,username) VALUES(56,'caller')");jdbc.update("INSERT INTO user_authorities VALUES(56,'ROLE_ADMIN'),(56,'PAGE_SETTINGS')");}
        case "delete-reuse"->{jdbc.update("DELETE FROM users WHERE id=?",ACTOR);jdbc.update("INSERT INTO users(id,username) VALUES(56,'caller')");jdbc.update("INSERT INTO user_authorities VALUES(56,'ROLE_ADMIN'),(56,'PAGE_SETTINGS')");}
        case "case"->jdbc.update("UPDATE users SET username='CALLER' WHERE id=?",ACTOR);
        case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?",ACTOR);
        case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=?",ACTOR);
        case "duplicate"->jdbc.update("INSERT INTO users(id,username) VALUES(56,'CALLER')");
        case "admin-revoked"->jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='ROLE_ADMIN'",ACTOR);
        case "page-revoked"->jdbc.update("DELETE FROM user_authorities WHERE user_id=? AND authority='PAGE_SETTINGS'",ACTOR);
        default->throw new AssertionError(mode);}}
    static Stream<Arguments> denied(){return OPERATIONS.stream().flatMap(op->Stream.of("rename-reuse","delete-reuse","case","disabled","blocked","duplicate","admin-revoked","page-revoked","null","unbound","unauthenticated","anonymous").map(mode->Arguments.of(op,mode)));}
    @ParameterizedTest @MethodSource("denied") void deniesEveryOperationWithoutChangingBusinessState(String op,String mode)throws Exception{
        var jdbc=fresh();Authentication account=auth(ACTOR,"caller");
        switch(mode){case "null"->account=null;case "unbound"->account=UsernamePasswordAuthenticationToken.authenticated("caller",null,AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_SETTINGS"));
            case "unauthenticated"->account=UsernamePasswordAuthenticationToken.unauthenticated(account.getPrincipal(),null);
            case "anonymous"->account=new AnonymousAuthenticationToken("fixture",account.getPrincipal(),AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_SETTINGS"));default->change(jdbc,mode);}
        var before=state(jdbc);var frozen=account;assertThatThrownBy(()->invoke(new BusinessAdministrationService(jdbc),op,frozen)).isInstanceOf(AccessDeniedException.class);
        assertThat(state(jdbc)).isEqualTo(before);
    }
    static Stream<Arguments> valid(){return OPERATIONS.stream().flatMap(op->Stream.of("direct","assigned","mixed","nullable-blocked","raw-spaces","unicode","max-id").map(mode->Arguments.of(op,mode)));}
    @ParameterizedTest @MethodSource("valid") void currentAdministratorCanUseEveryOperation(String op,String mode)throws Exception{
        var jdbc=fresh();long id=ACTOR;String name="caller";
        if(mode.equals("assigned")||mode.equals("mixed")){jdbc.update("INSERT INTO roles VALUES(9,' admin ','{\"pages\":[\"settings\"]}')");jdbc.update("UPDATE users SET role_id=9 WHERE id=?",id);jdbc.update("DELETE FROM user_authorities WHERE user_id=?"+(mode.equals("mixed")?" AND authority='PAGE_SETTINGS'":""),id);}
        if(mode.equals("nullable-blocked"))jdbc.update("UPDATE users SET is_blocked=NULL WHERE id=?",id);
        if(mode.equals("raw-spaces")||mode.equals("unicode")){name=mode.equals("raw-spaces")?" caller ":"Имя";jdbc.update("UPDATE users SET username=? WHERE id=?",name,id);}
        if(mode.equals("max-id")){jdbc.update("UPDATE users SET id=? WHERE id=?",Long.MAX_VALUE,id);jdbc.update("UPDATE user_authorities SET user_id=? WHERE user_id=?",Long.MAX_VALUE,id);id=Long.MAX_VALUE;}
        Object result=invoke(new BusinessAdministrationService(jdbc),op,auth(id,name));if(result instanceof Boolean value)assertThat(value).isTrue();else if(result instanceof Optional<?> value)assertThat(value).isPresent();else if(!op.startsWith("revoke"))assertThat(result).isNotNull();
    }
    @Test void publicServiceHasNoUnboundOverloads(){for(var method:BusinessAdministrationService.class.getDeclaredMethods())if(Modifier.isPublic(method.getModifiers())&&OPERATIONS.contains(method.getName()))assertThat(method.getParameterTypes()).startsWith(Authentication.class);}
    static Stream<Arguments> roles(){return com.example.panel.support.GeneralTicketAdminTestSupport.roleSources().stream().flatMap(mode->OPERATIONS.stream().map(op->Arguments.of(op,mode)));}
    @ParameterizedTest @MethodSource("roles") void assignedRoleSemanticsMatchLogin(String op,String mode)throws Exception{
        var jdbc=fresh();com.example.panel.support.GeneralTicketAdminTestSupport.assignRole(jdbc,ACTOR,mode);var before=state(jdbc);
        if(com.example.panel.support.GeneralTicketAdminTestSupport.roleAllows(mode))invoke(new BusinessAdministrationService(jdbc),op,auth(ACTOR,"caller"));
        else {assertThatThrownBy(()->invoke(new BusinessAdministrationService(jdbc),op,auth(ACTOR,"caller"))).isInstanceOf(AccessDeniedException.class);assertThat(state(jdbc)).isEqualTo(before);}
    }
    static Stream<Arguments> boundary(){return OPERATIONS.stream().flatMap(op->Stream.of("rename-reuse","assigned-name","assigned-page","assigned-pointer").map(mode->Arguments.of(op,mode)));}
    @ParameterizedTest @MethodSource("boundary") void roleSnapshotCannotAuthorizeChangedOwningStatement(String op,String mode)throws Exception{
        var jdbc=fresh();if(mode.startsWith("assigned-"))com.example.panel.support.GeneralTicketAdminTestSupport.assignRole(jdbc,ACTOR,"role");var before=state(jdbc);var fired=new java.util.concurrent.atomic.AtomicBoolean();
        var boundary=new JdbcTemplate(jdbc.getDataSource()){
            @Override public <T>T query(org.springframework.jdbc.core.PreparedStatementCreator statement,org.springframework.jdbc.core.ResultSetExtractor<T> mapper){
                if(statement instanceof org.springframework.jdbc.core.SqlProvider sql&&sql.getSql().contains("account_actor AS MATERIALIZED")&&fired.compareAndSet(false,true)){
                    if(mode.equals("rename-reuse"))change(jdbc,mode);else com.example.panel.support.GeneralTicketAdminTestSupport.changeAssignedRole(jdbc,ACTOR,mode.substring("assigned-".length()));
                }return super.query(statement,mapper);
            }};
        assertThatThrownBy(()->invoke(new BusinessAdministrationService(boundary),op,auth(ACTOR,"caller"))).isInstanceOf(AccessDeniedException.class);
        assertThat(fired).isTrue();assertThat(state(jdbc)).isEqualTo(before);
    }
}

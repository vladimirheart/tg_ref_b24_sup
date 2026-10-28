package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.*;

/** Frozen inputs for all three readers; the name-only reflection bridge exists only to demonstrate RED. */
class BusinessAuditReaderIdentityPostgresTest {
    enum Api { ASSIGNMENTS, ROSTER, PARTICIPANTS }
    private static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ";
    private JdbcTemplate jdbc;
    private BusinessTicketAssignmentReadService assignments;
    private BusinessTicketParticipantReadService participants;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("audit_reader_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
            INSERT INTO users(id,username) VALUES(2,'reader'),(3,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'VIEWER'),(3,22,'ADMIN');
            """);
        for(String migration:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql","V79__business_ticket_participants.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(9007199254740993,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(9007199254740993,?,?,11,9223372036854775807,9223372036854775807,' old target ',9223372036854775807,' old actor ','assign')",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action) VALUES(9007199254740993,?,?,11,9223372036854775807,9223372036854775807,' old member ',9223372036854775807,' old actor ','add')",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor) VALUES(9007199254740993,?,?,11,9223372036854775807,9223372036854775807,' old member ',9223372036854775807,' old actor ')",RAW,PUBLIC);
        assignments=new BusinessTicketAssignmentReadService(jdbc);participants=new BusinessTicketParticipantReadService(jdbc);
    }
    private Authentication auth(long id,String name,boolean enabled) {
        var user=new PanelUserPrincipal(id,name,"unused",enabled,AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_DIALOGS"));
        return UsernamePasswordAuthenticationToken.authenticated(user,"unused",user.getAuthorities());
    }
    private Object service(Api api){return api==Api.ASSIGNMENTS?assignments:participants;}
    private String method(Api api){return api==Api.ROSTER?"find":"history";}
    private Class<?>[] types(Api api,Class<?> identity){return api==Api.ROSTER?new Class<?>[]{String.class,identity}:new Class<?>[]{String.class,identity,Long.class,int.class};}
    private Optional<?> read(Api api,Authentication authentication) throws Exception {
        java.lang.reflect.Method method;Object identity=authentication;
        try{method=service(api).getClass().getMethod(method(api),types(api,Authentication.class));}
        catch(NoSuchMethodException baseline){method=service(api).getClass().getMethod(method(api),types(api,String.class));identity=authentication==null?null:authentication.getName();}
        try{return (Optional<?>)method.invoke(service(api),api==Api.ROSTER?new Object[]{PUBLIC,identity}:new Object[]{PUBLIC,identity,null,50});}
        catch(InvocationTargetException ex){if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
    static Stream<Arguments> denied(){return Stream.of(Api.values()).flatMap(api->Stream.of("wrong-id","missing-id","zero-id","negative-id","case","padding","rename-reuse","delete-reuse","case-change","unbound","unauthenticated","anonymous","principal-disabled","db-disabled","db-blocked","ambiguous","membership","archived","foreign","general-grant").map(mode->Arguments.of(api,mode)));}
    @ParameterizedTest @MethodSource("denied")
    void sameStatementRequiresTheBoundAccountAndCurrentBusinessRead(Api api,String mode) throws Exception {
        Authentication principal=auth(2,"reader",true);
        switch(mode){
            case "wrong-id"->principal=auth(3,"reader",true);case "missing-id"->principal=auth(999,"reader",true);
            case "zero-id"->principal=auth(0,"reader",true);case "negative-id"->principal=auth(-1,"reader",true);
            case "case"->principal=auth(2,"READER",true);case "padding"->principal=auth(2," reader ",true);
            case "rename-reuse","delete-reuse"->{if(mode.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=2");else jdbc.update("DELETE FROM users WHERE id=2");jdbc.update("INSERT INTO users(id,username) VALUES(4,'reader')");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(4,11,'ADMIN')");}
            case "case-change"->jdbc.update("UPDATE users SET username='READER' WHERE id=2");
            case "unbound"->principal=UsernamePasswordAuthenticationToken.authenticated("reader","unused",principal.getAuthorities());
            case "unauthenticated"->principal=UsernamePasswordAuthenticationToken.unauthenticated(principal.getPrincipal(),"unused");
            case "anonymous"->principal=new AnonymousAuthenticationToken("key",principal.getPrincipal(),principal.getAuthorities());
            case "principal-disabled"->principal=auth(2,"reader",false);
            case "db-disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");case "db-blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "ambiguous"->jdbc.update("INSERT INTO users(id,username) VALUES(4,'READER')");
            case "membership"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "archived"->jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");case "foreign"->principal=auth(3,"foreign",true);
            case "general-grant"->{jdbc.update("DELETE FROM business_memberships WHERE user_id=2");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'GENERAL_TICKETS','ADMIN')");}
        }
        var before=jdbc.queryForList("SELECT * FROM business_ticket_participants");
        assertThat(read(api,principal)).isEmpty();assertThat(jdbc.queryForList("SELECT * FROM business_ticket_participants")).isEqualTo(before);
    }
    static Stream<Arguments> literalNames(){return Stream.of(Api.values()).flatMap(api->Stream.of("reader"," reader ","ReAdEr","имя","anonymousUser","2","9007199254740993","9223372036854775807").map(name->Arguments.of(api,name)));}
    @ParameterizedTest @MethodSource("literalNames")
    void exactNamesAndBigintAccountsKeepOwnerAndHistoricalSnapshots(Api api,String name) throws Exception {
        long id=name.equals("9007199254740993")?9007199254740993L:name.equals("9223372036854775807")?Long.MAX_VALUE:2;
        jdbc.update("UPDATE users SET id=?,username=? WHERE id=2",id,name);jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",id);
        var node=new ObjectMapper().registerModule(new JavaTimeModule()).valueToTree(read(api,auth(id,name,true)).orElseThrow());
        assertThat(node.at("/owner/userId").asText()).isEqualTo("9007199254740993");assertThat(node.at("/owner/ticketId").asText()).isEqualTo(RAW);
        if(api==Api.ROSTER){assertThat(node.at("/members/0/id").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));assertThat(node.at("/members/0/snapshot").asText()).isEqualTo(" old member ");}
        else{assertThat(node.at("/events/0/revision").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));assertThat(node.at("/events/0/actor").asText()).isEqualTo(" old actor ");}
    }
    @ParameterizedTest @EnumSource(Api.class) void nullPrincipalCannotRead(Api api)throws Exception{assertThat(read(api,null)).isEmpty();}
    @ParameterizedTest @EnumSource(Api.class) void noNameOnlyProductionApi(Api api){assertThatCode(()->service(api).getClass().getMethod(method(api),types(api,Authentication.class))).doesNotThrowAnyException();assertThatThrownBy(()->service(api).getClass().getMethod(method(api),types(api,String.class))).isInstanceOf(NoSuchMethodException.class);}
    @ParameterizedTest @EnumSource(Api.class) void globalRoleIsNotReadButExplicitAllBusinessesViewerIs(Api api)throws Exception{assertThat(read(api,auth(3,"foreign",true))).isEmpty();jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER')");assertThat(read(api,auth(3,"foreign",true))).isPresent();jdbc.update("UPDATE business_access_grants SET is_active=FALSE");assertThat(read(api,auth(3,"foreign",true))).isEmpty();}
}

package com.example.panel.service;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import static org.assertj.core.api.Assertions.*;

/** Frozen RED/GREEN inputs; reflection bridge is test-only, never a product name fallback. */
class BusinessWorkHistoryIdentityPostgresTest {
    private static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ";
    private JdbcTemplate jdbc;
    private BusinessTicketWorkReadService read;
    @BeforeEach void fixture() throws Exception {
        jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("work_history_identity");
        jdbc.execute("""
            CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE);
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',PRIMARY KEY(user_id,ticket_id));
            INSERT INTO users(id,username) VALUES(2,'reader'),(3,'foreign');
            INSERT INTO businesses(id) VALUES(11),(22);
            INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'VIEWER'),(3,22,'ADMIN');
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V81__business_ticket_manual_work.sql")));
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(9007199254740993,?,?,11)",RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,from_status,to_status,previous_categories,categories,actor_user_id,actor) VALUES(9007199254740993,?,?,11,9223372036854775807,'close','open','resolved','[\"Old\"]','[\"Private\"]',9223372036854775807,' old actor ')",RAW,PUBLIC);
        read=new BusinessTicketWorkReadService(jdbc,new ObjectMapper());
    }
    private Authentication auth(long id,String name,boolean enabled) {
        var user=new PanelUserPrincipal(id,name,"unused",enabled,AuthorityUtils.createAuthorityList("ROLE_ADMIN","PAGE_DIALOGS"));
        return UsernamePasswordAuthenticationToken.authenticated(user,"unused",user.getAuthorities());
    }
    @SuppressWarnings("unchecked")
    private Optional<BusinessTicketWorkReadService.History> history(Authentication authentication) throws Exception {
        java.lang.reflect.Method method;
        Object identity=authentication;
        try {method=read.getClass().getMethod("history",String.class,Authentication.class,Long.class,int.class);}
        catch(NoSuchMethodException baseline) {
            method=read.getClass().getMethod("history",String.class,String.class,Long.class,int.class);
            identity=authentication==null?null:authentication.getName();
        }
        try {return (Optional<BusinessTicketWorkReadService.History>)method.invoke(read,PUBLIC,identity,null,50);}
        catch(InvocationTargetException ex) {if(ex.getCause() instanceof Exception cause)throw cause;throw ex;}
    }
    @ParameterizedTest @ValueSource(strings={"wrong-id","missing-id","zero-id","negative-id","case","padding","rename-reuse","delete-reuse","case-change","unbound","unauthenticated","anonymous","principal-disabled","db-disabled","db-blocked","ambiguous","membership","archived","foreign","general-grant"})
    void identityAndCurrentReadPolicyMustHoldInTheSameStatement(String mode) throws Exception {
        Authentication principal=auth(2,"reader",true);
        switch(mode) {
            case "wrong-id" -> principal=auth(3,"reader",true);
            case "missing-id" -> principal=auth(999,"reader",true);
            case "zero-id" -> principal=auth(0,"reader",true);
            case "negative-id" -> principal=auth(-1,"reader",true);
            case "case" -> principal=auth(2,"READER",true);
            case "padding" -> principal=auth(2," reader ",true);
            case "rename-reuse","delete-reuse" -> {
                if(mode.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=2");
                else jdbc.update("DELETE FROM users WHERE id=2");
                jdbc.update("INSERT INTO users(id,username) VALUES(4,'reader')");
                jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(4,11,'ADMIN')");
            }
            case "case-change" -> jdbc.update("UPDATE users SET username='READER' WHERE id=2");
            case "unbound" -> principal=UsernamePasswordAuthenticationToken.authenticated("reader","unused",AuthorityUtils.createAuthorityList("PAGE_DIALOGS"));
            case "unauthenticated" -> principal=UsernamePasswordAuthenticationToken.unauthenticated(principal.getPrincipal(),"unused");
            case "anonymous" -> principal=new AnonymousAuthenticationToken("key",principal.getPrincipal(),principal.getAuthorities());
            case "principal-disabled" -> principal=auth(2,"reader",false);
            case "db-disabled" -> jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "db-blocked" -> jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "ambiguous" -> jdbc.update("INSERT INTO users(id,username) VALUES(4,'READER')");
            case "membership" -> jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            case "archived" -> jdbc.update("UPDATE businesses SET status='archived' WHERE id=11");
            case "foreign" -> principal=auth(3,"foreign",true);
            case "general-grant" -> {jdbc.update("DELETE FROM business_memberships WHERE user_id=2");jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(2,'GENERAL_TICKETS','ADMIN')");}
        }
        var before=jdbc.queryForList("SELECT * FROM business_ticket_work_events");
        assertThat(history(principal)).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM business_ticket_work_events")).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"reader"," reader ","ReAdEr","имя","anonymousUser","2","9007199254740993","9223372036854775807"})
    void literalNamesAndLargeBoundIdsKeepTheirOwnImmutableHistory(String name) throws Exception {
        long id=name.equals("9007199254740993")?9007199254740993L:name.equals("9223372036854775807")?Long.MAX_VALUE:2;
        jdbc.update("UPDATE users SET id=?,username=? WHERE id=2",id,name);
        jdbc.update("UPDATE business_memberships SET user_id=? WHERE user_id=2",id);
        var result=history(auth(id,name,true)).orElseThrow();
        assertThat(result.owner()).isEqualTo(new BusinessTicketWorkReadService.Owner("9007199254740993",RAW,PUBLIC,"11"));
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0).actor()).isEqualTo(" old actor ");
        assertThat(result.events().get(0).actorId()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.latestEventRevision()).isEqualTo(Long.MAX_VALUE);
    }
    @Test void allBusinessesViewerCanReadButGlobalRoleAloneCannot() throws Exception {
        assertThat(history(auth(3,"foreign",true))).isEmpty();
        jdbc.update("INSERT INTO business_access_grants(user_id,grant_scope,role_code) VALUES(3,'ALL_BUSINESSES','VIEWER')");
        assertThat(history(auth(3,"foreign",true))).isPresent();
        jdbc.update("UPDATE business_access_grants SET is_active=FALSE");
        assertThat(history(auth(3,"foreign",true))).isEmpty();
    }
    @Test void productionApiHasNoNameOnlyOverload() {
        assertThatCode(()->read.getClass().getMethod("history",String.class,Authentication.class,Long.class,int.class)).doesNotThrowAnyException();
        assertThatThrownBy(()->read.getClass().getMethod("history",String.class,String.class,Long.class,int.class)).isInstanceOf(NoSuchMethodException.class);
    }
    @Test void nullPrincipalIsDenied() throws Exception {assertThat(history(null)).isEmpty();}
}

package com.example.panel.support;

import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.service.UiEventTicketScope;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/** Dedicated AI-control fixture; no working datasource or provider calls. */
public final class AiControlIdentityFixture {
    private AiControlIdentityFixture(){}
    public static final String PUBLIC="a".repeat(32),RAW=" R208/Ю?%#+ ";
    public static final UiEventTicketScope SCOPE=new UiEventTicketScope(42L,RAW,PUBLIC,"BUSINESS",11L);
    public static void schema(JdbcTemplate jdbc,boolean existingUsers)throws Exception{
        if(!existingUsers)jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        jdbc.execute("""
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id));
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V62__scoped_ai_records.sql")));
    }
    public static void seed(JdbcTemplate jdbc,String name,String password){
        jdbc.execute("TRUNCATE users,businesses,business_memberships,business_access_grants,tickets,ticket_ai_scoped_control,ticket_ai_scoped_state,ticket_ai_scoped_events");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(7,?,?),(99,'foreign','unused')",name,password);
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'OPERATOR'),(99,22,'ADMIN')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",RAW,PUBLIC);
        for(String table:List.of("users","businesses","business_memberships","business_access_grants","tickets","ticket_ai_scoped_control","ticket_ai_scoped_events"))jdbc.execute("ANALYZE "+table);
    }
    public static String existing(JdbcTemplate jdbc){
        jdbc.update("INSERT INTO ticket_ai_scoped_control(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,ai_disabled,auto_reply_blocked,reason,updated_by_user_id,updated_by,updated_at) VALUES(42,?,?,'BUSINESS',11,FALSE,FALSE,'earlier',99,'earlier actor','2000-01-01T00:00:00Z')",RAW,PUBLIC);
        return jdbc.queryForObject("SELECT updated_at FROM ticket_ai_scoped_control",String.class);
    }
    public static Authentication auth(long id,String name){return UsernamePasswordAuthenticationToken.authenticated(new PanelUserPrincipal(id,name,"unused",true,List.of()),null,List.of());}
    public static Map<String,List<Map<String,Object>>> snapshot(JdbcTemplate jdbc){
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();for(String table:List.of("tickets","ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return result;
    }
    public static void replace(JdbcTemplate jdbc,String change){
        if(change.equals("rename-reuse")||change.equals("delete-reuse")){
            if(change.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=7");else jdbc.update("DELETE FROM users WHERE id=7");
            jdbc.update("INSERT INTO users(id,username,password) VALUES(55,'caller','unused')");jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
        }else if(change.equals("case-change"))jdbc.update("UPDATE users SET username='CALLER' WHERE id=7");
        else if(change.equals("disabled"))jdbc.update("UPDATE users SET enabled=FALSE WHERE id=7");
        else if(change.equals("blocked"))jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=7");
        else if(change.equals("membership"))jdbc.update("DELETE FROM business_memberships WHERE user_id=7");
        else throw new IllegalArgumentException(change);
    }
}

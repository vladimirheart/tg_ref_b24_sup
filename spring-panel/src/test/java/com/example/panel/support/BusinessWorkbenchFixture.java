package com.example.panel.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Dedicated PostgreSQL fixture; never connects to the working application's datasource. */
public final class BusinessWorkbenchFixture {
    private BusinessWorkbenchFixture(){}
    public static final String PUBLIC="a".repeat(32),RAW=" raw/Ю?%#+ ";
    public static final long OWNER=9007199254740993L;
    public static final List<String> DATA=List.of("tickets","ticket_responsibles","business_ticket_assignment_events",
        "scoped_sla_assignment_events","business_ticket_participants","business_ticket_participant_events","business_ticket_work_events","ui_event_outbox");
    public static void schema(JdbcTemplate jdbc,boolean existingUsers)throws Exception{
        if(!existingUsers)jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,password TEXT,enabled BOOLEAN DEFAULT TRUE,is_blocked BOOLEAN DEFAULT FALSE)");
        jdbc.execute("""
            CREATE TABLE businesses(id BIGINT PRIMARY KEY,status TEXT DEFAULT 'active');
            CREATE TABLE business_memberships(user_id BIGINT,business_id BIGINT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE business_access_grants(user_id BIGINT,grant_scope TEXT,role_code TEXT,is_active BOOLEAN DEFAULT TRUE);
            CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,ticket_public_id TEXT,business_id BIGINT,is_general BOOLEAN DEFAULT FALSE,
                channel_id BIGINT,status TEXT DEFAULT 'open',resolved_at TIMESTAMPTZ,resolved_by TEXT,closed_count INT DEFAULT 0,
                reopen_count INT DEFAULT 0,last_reopen_at TIMESTAMPTZ,PRIMARY KEY(user_id,ticket_id));
            CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,assigned_by TEXT,assigned_at TIMESTAMPTZ);
            CREATE TABLE ui_event_outbox(id BIGINT PRIMARY KEY,event_type TEXT,ticket_id TEXT,channel_id BIGINT,created_at TIMESTAMPTZ,
                ticket_user_id BIGINT,ticket_public_id TEXT,ticket_scope_kind TEXT,ticket_business_id BIGINT);
            """);
        for(String migration:List.of("V77__scoped_sla_assignment_revision.sql","V78__business_manual_assignment_events.sql","V79__business_ticket_participants.sql","V81__business_ticket_manual_work.sql"))
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql",migration)));
    }
    public static void seed(JdbcTemplate jdbc,String name,String password){
        jdbc.execute("TRUNCATE users,businesses,business_memberships,business_access_grants,"+String.join(",",DATA));
        jdbc.update("INSERT INTO users(id,username,password) VALUES(2,?,?),(3,'foreign','unused'),(6,'target','unused'),(7,'member','unused')",name,password);
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(2,11,'OPERATOR'),(3,22,'ADMIN'),(6,11,'OPERATOR'),(7,11,'VIEWER')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id,channel_id) VALUES(?,?,?,11,9)",OWNER,RAW,PUBLIC);
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible,assigned_by) VALUES(?,' private assignee ',' private assignment actor ')",RAW);
        jdbc.update("INSERT INTO business_ticket_assignment_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,assignee_user_id,assignee,actor_user_id,actor,action) VALUES(?,?,?,11,1,6,' private assignee ',2,' private assignment actor ','assign')",OWNER,RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participant_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor,action) VALUES(?,?,?,11,1,7,' private member ',2,' private participant actor ','add')",OWNER,RAW,PUBLIC);
        jdbc.update("INSERT INTO business_ticket_participants(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,member_user_id,member,actor_user_id,actor) VALUES(?,?,?,11,1,7,' private member ',2,' private participant actor ')",OWNER,RAW,PUBLIC);
        jdbc.update("UPDATE tickets SET business_categories='[\"Own\"]'");
        jdbc.update("INSERT INTO business_ticket_work_events(ticket_user_id,ticket_id,ticket_public_id,business_id,revision,action,from_status,to_status,previous_categories,categories,actor_user_id,actor) VALUES(?,?,?,11,1,'categories','open','open','[]','[\"Own\"]',2,' private work actor ')",OWNER,RAW,PUBLIC);
        // Avoid phantom default cardinalities/JIT costs in tiny newly created test relations.
        for(String table:List.of("users","businesses","business_memberships","business_access_grants","tickets","ticket_responsibles","business_ticket_participants","business_ticket_participant_events","business_ticket_assignment_events","business_ticket_work_events"))jdbc.execute("ANALYZE "+table);
    }
    public static void replace(JdbcTemplate jdbc,String mode,String name){
        switch(mode){
            case "rename-reuse","delete-reuse"->{
                if(mode.equals("rename-reuse"))jdbc.update("UPDATE users SET username='renamed' WHERE id=2");else jdbc.update("DELETE FROM users WHERE id=2");
                jdbc.update("INSERT INTO users(id,username,password) VALUES(55,?,'unused')",name);
                jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(55,11,'ADMIN')");
            }
            case "case-change"->jdbc.update("UPDATE users SET username=upper(username) WHERE id=2");
            case "disabled"->jdbc.update("UPDATE users SET enabled=FALSE WHERE id=2");
            case "blocked"->jdbc.update("UPDATE users SET is_blocked=TRUE WHERE id=2");
            case "membership"->jdbc.update("UPDATE business_memberships SET is_active=FALSE WHERE user_id=2");
            default->throw new IllegalArgumentException(mode);
        }
    }
    public static Map<String,List<Map<String,Object>>> snapshot(JdbcTemplate jdbc){
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:DATA)result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));return result;
    }
}

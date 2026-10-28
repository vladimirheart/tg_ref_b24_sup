package com.example.panel.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Isolated reviewed MEMORY source, audit and evidence fixture; no provider or working datasource. */
public final class AiMemoryIdentityFixture {
    private AiMemoryIdentityFixture(){}
    public static final String KEY="12345678-1234-1234-1234-123456789abc";
    public static void schema(JdbcTemplate jdbc,boolean existingUsers)throws Exception {
        AiControlIdentityFixture.schema(jdbc,existingUsers);
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY,ticket_owner_user_id BIGINT,ticket_id TEXT,sender TEXT,message TEXT,message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ)");
        for(String migration:List.of("V63__business_owned_ai_sources.sql","V64__scoped_ai_handoff_evidence.sql"))jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/"+migration)));
    }
    public static void seed(JdbcTemplate jdbc,String name,String password) {
        jdbc.execute("ALTER TABLE ai_scoped_source_events DROP CONSTRAINT IF EXISTS deny_create; ALTER TABLE ai_scoped_memory_evidence DROP CONSTRAINT IF EXISTS deny_evidence");
        jdbc.execute("TRUNCATE users,businesses,business_memberships,business_access_grants,tickets,chat_history,ticket_ai_scoped_control,ticket_ai_scoped_state,ticket_ai_scoped_events,ai_scoped_sources,ai_scoped_source_events,ai_scoped_memory_evidence,ai_scoped_handoff_evidence");
        jdbc.update("INSERT INTO users(id,username,password) VALUES(7,?,?),(99,'foreign','unused')",name,password);
        jdbc.update("INSERT INTO businesses(id) VALUES(11),(22)");
        jdbc.update("INSERT INTO business_memberships(user_id,business_id,role_code) VALUES(7,11,'ADMIN'),(99,22,'ADMIN')");
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,ticket_public_id,business_id) VALUES(42,?,?,11)",AiControlIdentityFixture.RAW,AiControlIdentityFixture.PUBLIC);
        jdbc.update("INSERT INTO chat_history(id,ticket_owner_user_id,ticket_id,sender,message,message_type) VALUES(40,42,?,'client','private question','text'),(41,42,?,'operator','reviewed answer','operator_message')",AiControlIdentityFixture.RAW,AiControlIdentityFixture.RAW);
        for(String table:List.of("users","businesses","business_memberships","business_access_grants","tickets","chat_history","ai_scoped_sources","ai_scoped_source_events","ai_scoped_memory_evidence"))jdbc.execute("ANALYZE "+table);
    }
    public static String fingerprint(JdbcTemplate jdbc,long id) {
        return jdbc.queryForObject("SELECT encode(sha256(convert_to(message,'UTF8')),'hex') FROM chat_history WHERE id=?",String.class,id);
    }
    public static Map<String,List<Map<String,Object>>> snapshot(JdbcTemplate jdbc) {
        var result=new LinkedHashMap<String,List<Map<String,Object>>>();
        for(String table:List.of("tickets","chat_history","ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events","ai_scoped_sources","ai_scoped_source_events","ai_scoped_memory_evidence","ai_scoped_handoff_evidence"))result.put(table,jdbc.queryForList("SELECT * FROM "+table+" ORDER BY 1,2"));
        return result;
    }
}

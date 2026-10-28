package com.example.panel.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Dedicated history rows on the existing disposable identity fixture. */
public final class AiWorkbenchReadIdentityFixture {
    private AiWorkbenchReadIdentityFixture(){}
    public static void schema(JdbcTemplate jdbc,boolean existingUsers)throws Exception {
        AiControlIdentityFixture.schema(jdbc,existingUsers);
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY,ticket_owner_user_id BIGINT,ticket_id TEXT,sender TEXT,message TEXT,message_type TEXT,attachment TEXT,deleted_at TIMESTAMPTZ)");
    }
    public static void seed(JdbcTemplate jdbc,String name,String password) {
        AiControlIdentityFixture.seed(jdbc,name,password);jdbc.execute("TRUNCATE chat_history");
        String raw=AiControlIdentityFixture.RAW;
        jdbc.update("INSERT INTO chat_history(id,ticket_owner_user_id,ticket_id,sender,message,message_type) VALUES(40,42,?,'client','PRIVATE_R210_QUESTION','text'),(41,42,?,'operator','PRIVATE_R210_ANSWER','operator_message'),(42,42,?,'operator','AI_SECRET','ai_agent'),(43,NULL,?,'client','UNLINKED_SECRET','text'),(44,99,?,'client','FOREIGN_SECRET','text')",raw,raw,raw,raw,raw);
        jdbc.update("INSERT INTO chat_history VALUES(45,42,?,'client','DELETED_SECRET','text',NULL,CURRENT_TIMESTAMP),(46,42,?,'client','ATTACHMENT_SECRET','text','secret/path',NULL),(47,42,?,'client',?,'text',NULL,NULL),(48,42,?,'client','   ','text',NULL,NULL)",raw,raw,raw,"x".repeat(16001),raw);
        jdbc.execute("ANALYZE chat_history");
    }
}

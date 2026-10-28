package com.example.panel.support;

import org.springframework.jdbc.core.JdbcTemplate;
import static com.example.panel.support.AiControlIdentityFixture.*;

/** Canonical private AI rows, independent of the UI writers under test. */
public final class AiPersistenceReadIdentityFixture {
    private AiPersistenceReadIdentityFixture(){}
    public static final String REASON="PRIVATE_R211_REASON",ACTION="PRIVATE_R211_ACTION",EVENT="PRIVATE_R211_EVENT";
    public static void schema(JdbcTemplate jdbc,boolean existingUsers)throws Exception {AiControlIdentityFixture.schema(jdbc,existingUsers);}
    public static void seed(JdbcTemplate jdbc,String name,String password) {
        AiControlIdentityFixture.seed(jdbc,name,password);existing(jdbc);
        jdbc.update("UPDATE ticket_ai_scoped_control SET reason=?,updated_by='PRIVATE_R211_ACTOR'",REASON);
        jdbc.update("INSERT INTO ticket_ai_scoped_state(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,is_processing,mode,last_action) VALUES(42,?,?,'BUSINESS',11,FALSE,'escalate_only',?)",RAW,PUBLIC,ACTION);
        jdbc.update("INSERT INTO ticket_ai_scoped_events(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,event_type,decision_reason) VALUES(42,?,?,'BUSINESS',11,'control_updated',?)",RAW,PUBLIC,EVENT);
        for(String table:java.util.List.of("ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events"))jdbc.execute("ANALYZE "+table);
    }
}

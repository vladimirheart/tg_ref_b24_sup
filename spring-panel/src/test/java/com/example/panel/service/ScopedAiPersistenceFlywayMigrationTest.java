package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ScopedAiPersistenceFlywayMigrationTest {
    @Test
    void upgradePreservesLegacyAiRecordsWithoutClassifyingOrCopyingThemAndProtectsNewSnapshots() {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("scoped_ai_upgrade_v62");
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").target("61").load().migrate();
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,is_general) VALUES(901,' exact ',FALSE)");
        jdbc.update("INSERT INTO ticket_ai_agent_state(ticket_id,last_action) VALUES(' exact ','legacy state')");
        jdbc.update("INSERT INTO ticket_ai_agent_dialog_control(ticket_id,ai_disabled,reason) VALUES(' exact ',1,'legacy control')");
        jdbc.update("INSERT INTO ai_agent_event_log(ticket_id,event_type,detail) VALUES(' exact ','legacy','private old detail')");
        var preserved=new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table:List.of("tickets","ticket_ai_agent_state","ticket_ai_agent_dialog_control","ai_agent_event_log",
                "notifications","ui_event_outbox")) preserved.put(table,jdbc.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(jdbc.getDataSource())
            .locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        preserved.forEach((table,before)->assertThat(jdbc.queryForList("SELECT * FROM "+table)).isEqualTo(before));
        for (String table:List.of("control","state","events")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_"+table,Integer.class)).isZero();
        }
        String owner="ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id";
        String values="901,' exact ','"+"a".repeat(32)+"','BUSINESS',7";
        jdbc.update("INSERT INTO ticket_ai_scoped_control("+owner+",ai_disabled,auto_reply_blocked,updated_by_user_id,updated_by) VALUES("
            +values+",FALSE,FALSE,1,'operator')");
        jdbc.update("INSERT INTO ticket_ai_scoped_state("+owner+",is_processing,mode,last_action) VALUES("
            +values+",FALSE,'escalate_only','operator_required')");
        jdbc.update("INSERT INTO ticket_ai_scoped_events("+owner+",event_type,decision_reason) VALUES("
            +values+",'operator_handoff_requested','scoped_ai_pipeline_pending')");
        for (String table:List.of("control","state","events")) {
            for (String assignment:List.of("ticket_user_id=902","ticket_id='other'","ticket_public_id='"+"b".repeat(32)+"'",
                    "ticket_scope_kind='UNRESOLVED',ticket_business_id=NULL","ticket_business_id=8")) {
                assertThatThrownBy(()->jdbc.update("UPDATE ticket_ai_scoped_"+table+" SET "+assignment))
                    .hasMessageContaining("immutable");
            }
        }
        jdbc.update("UPDATE ticket_ai_scoped_control SET ai_disabled=TRUE,reason='new metadata'");
        jdbc.update("UPDATE ticket_ai_scoped_state SET last_action='new action'");
        assertThatThrownBy(()->jdbc.update("UPDATE ticket_ai_scoped_events SET decision_reason='rewritten'"))
            .hasMessageContaining("immutable");
        for (String invalid:List.of("NULL,'id','"+"c".repeat(32)+"','BUSINESS',7",
                "901,' ','"+"c".repeat(32)+"','BUSINESS',7","901,'id','bad','BUSINESS',7",
                "901,'id','"+"c".repeat(32)+"','GENERAL',NULL","901,'id','"+"c".repeat(32)+"','BUSINESS',NULL",
                "901,'id','"+"c".repeat(32)+"','UNRESOLVED',7","901,'id','"+"c".repeat(32)+"','BUSINESS',0")) {
            assertThatThrownBy(()->jdbc.update("INSERT INTO ticket_ai_scoped_events("+owner+",event_type,decision_reason) VALUES("
                +invalid+",'event','reason')"));
        }
        jdbc.update("DELETE FROM tickets WHERE user_id=901");
        for (String table:List.of("control","state","events")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ticket_ai_scoped_"+table,Integer.class)).isEqualTo(1);
        }
    }
}

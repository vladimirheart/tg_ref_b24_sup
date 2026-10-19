package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ScopedAiSourceFlywayMigrationTest {
    @Test
    void upgradePreservesAllLegacyAndScopedRecordsWithoutCopyingOrInferringSourceOwnership() {
        var jdbc=PostgresqlJdbcTestSupport.freshJdbcTemplate("scoped_ai_sources_v63");
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql")
            .target("62").load().migrate();
        Long business=jdbc.queryForObject("SELECT id FROM businesses WHERE code='sushi'",Long.class);
        String publicId="a".repeat(32);
        jdbc.update("INSERT INTO tickets(user_id,ticket_id,is_general,business_id,ticket_public_id) VALUES(901,' exact ',FALSE,?,?)",business,publicId);
        jdbc.update("INSERT INTO ai_agent_solution_memory(query_key,query_text,solution_text,status,scope_business) VALUES('same-key','legacy question','legacy answer','approved',?)",business.toString());
        jdbc.update("INSERT INTO ai_agent_knowledge_unit(unit_key,title,body_text,status,business) VALUES('same-key','Legacy','global secret','active',?)",business.toString());
        jdbc.update("INSERT INTO ai_agent_memory_link(query_key,knowledge_unit_id) SELECT 'same-key',id FROM ai_agent_knowledge_unit WHERE unit_key='same-key'");
        jdbc.update("INSERT INTO ticket_ai_scoped_state(ticket_user_id,ticket_id,ticket_public_id,ticket_scope_kind,ticket_business_id,is_processing,mode,last_action)"
            +" VALUES(901,' exact ',?,'BUSINESS',?,FALSE,'escalate_only','operator_required')",publicId,business);
        var before=new LinkedHashMap<String,List<Map<String,Object>>>();
        for (String table:List.of("tickets","businesses","ai_agent_solution_memory","ai_agent_knowledge_unit","ai_agent_memory_link",
                "ticket_ai_scoped_control","ticket_ai_scoped_state","ticket_ai_scoped_events")) before.put(table,jdbc.queryForList("SELECT * FROM "+table));
        Flyway.configure().dataSource(jdbc.getDataSource()).locations("filesystem:src/main/resources/db/migration/postgresql").load().migrate();
        before.forEach((table,rows)->assertThat(jdbc.queryForList("SELECT * FROM "+table)).isEqualTo(rows));
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_sources")).isEmpty();
        assertThat(jdbc.queryForList("SELECT * FROM ai_scoped_source_events")).isEmpty();
        String insert="INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by) VALUES(?,'KNOWLEDGE',' exact ','Title','Body','DRAFT',1,1,'admin')";
        jdbc.update(insert,business);
        jdbc.update("INSERT INTO ai_scoped_source_events(source_id,business_id,source_kind,source_key,revision,status,action,actor_user_id,actor)"
            +" SELECT id,business_id,source_kind,source_key,revision,status,'create',1,'admin' FROM ai_scoped_sources");
        for (String assignment:List.of("id=DEFAULT","business_id=business_id+100","source_kind='MEMORY'","source_key='other'",
                "origin_ticket_id='other'","created_at=created_at+INTERVAL '1 second'")) {
            assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_sources SET revision=revision+1,"+assignment))
                .hasMessageContaining("identity is immutable");
        }
        assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_sources SET body='unversioned'"))
            .hasMessageContaining("revision must advance");
        jdbc.update("UPDATE ai_scoped_sources SET body='Reviewed',status='APPROVED',revision=revision+1");
        assertThatThrownBy(()->jdbc.update("UPDATE ai_scoped_source_events SET actor='rewritten'"))
            .hasMessageContaining("immutable");
        assertThatThrownBy(()->jdbc.update(insert,(Object)null));
        assertThatThrownBy(()->jdbc.update(insert,99999999L));
        assertThatThrownBy(()->jdbc.update("INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by)"
            +" VALUES(?,'GLOBAL','global','Title','Body','APPROVED',1,1,'admin')",business));
        String memory="INSERT INTO ai_scoped_sources(business_id,source_kind,source_key,title,body,status,revision,updated_by_user_id,updated_by,"
            +"origin_ticket_user_id,origin_ticket_id,origin_ticket_public_id,origin_ticket_scope_kind,origin_ticket_business_id)"
            +" VALUES(?,'MEMORY','memory','Title','Body','DRAFT',1,1,'admin',901,' exact ',?,'BUSINESS',?)";
        assertThatThrownBy(()->jdbc.update(memory,business,null,business));
        assertThatThrownBy(()->jdbc.update(memory,business,"bad-id",business));
        assertThatThrownBy(()->jdbc.update(memory,business,publicId,business+1));
        jdbc.update(memory,business,publicId,business);
        jdbc.update("DELETE FROM tickets WHERE user_id=901");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_scoped_sources",Integer.class)).isEqualTo(2);
    }
}

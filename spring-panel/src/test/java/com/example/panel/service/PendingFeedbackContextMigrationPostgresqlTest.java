package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.charset.StandardCharsets;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

class PendingFeedbackContextMigrationPostgresqlTest {
    @Test
    void capturesNewProvenanceWithoutReclassifyingHistoricalRequests() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_context");
        String migrations = "filesystem:src/main/resources/db/migration/postgresql";
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations).target("83").load().migrate();
        jdbc.execute(sql("pending-feedback-context-before.sql"));
        Flyway.configure().dataSource(jdbc.getDataSource()).locations(migrations).load().migrate();
        // One connection/batch: temporary assertion table and PL/pgSQL functions share the session.
        jdbc.execute(sql("pending-feedback-context-contract.sql"));
    }

    private String sql(String name) throws Exception {
        return new ClassPathResource("db/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}

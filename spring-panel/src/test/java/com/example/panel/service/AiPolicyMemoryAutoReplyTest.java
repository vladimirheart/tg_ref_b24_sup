package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiPolicyMemoryAutoReplyTest {

    @Test
    void memoryAutoReplyRequiresExplicitDatabaseOptIn() {
        String db = "ai_policy_memory_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        ));
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_solution_memory (
                    query_key VARCHAR(128) PRIMARY KEY,
                    auto_reply_allowed INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.update(
                "INSERT INTO ai_agent_solution_memory(query_key, auto_reply_allowed) VALUES (?, ?), (?, ?)",
                "suggest-only", 0,
                "auto-ok", 1
        );

        AiPolicyService service = new AiPolicyService(jdbcTemplate);

        assertThat(service.isMemoryAutoReplyAllowed("suggest-only")).isFalse();
        assertThat(service.isMemoryAutoReplyAllowed("auto-ok")).isTrue();
        assertThat(service.isMemoryAutoReplyAllowed("missing")).isFalse();
        assertThat(service.isMemoryAutoReplyAllowed(null)).isFalse();
    }
}
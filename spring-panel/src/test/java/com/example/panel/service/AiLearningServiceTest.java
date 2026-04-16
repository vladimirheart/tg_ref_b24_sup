package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiLearningServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AiLearningService service;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:ai_learning_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        AiPolicyService policyService = new AiPolicyService(jdbcTemplate);
        AiIntentService intentService = new AiIntentService(jdbcTemplate, new ObjectMapper());
        service = new AiLearningService(jdbcTemplate, policyService, intentService);
    }

    @Test
    void insertsNewReplyAsDraftPendingReview() {
        AiLearningService.UpsertResult result = service.upsertLearningSolution(
                "T-100",
                "Где мой заказ #A-100 по Блинбери в телеграм?",
                "Проверяем статус заказа и скоро вернемся с ответом.",
                "operator-1",
                0.42d
        );

        assertNotNull(result);
        assertEquals("inserted_draft", result.action());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                """
                SELECT solution_text, pending_solution_text, review_required, status, intent_key, slot_signature,
                       scope_channel, scope_business
                  FROM ai_agent_solution_memory
                 WHERE query_key = ?
                """,
                result.queryKey()
        );

        assertNull(row.get("solution_text"));
        assertEquals("draft", row.get("status"));
        assertEquals(1, ((Number) row.get("review_required")).intValue());
        assertEquals("order_status", row.get("intent_key"));
        assertEquals("telegram", row.get("scope_channel"));
        assertEquals("блинбери", row.get("scope_business"));
        assertNotNull(row.get("slot_signature"));
        assertTrue(String.valueOf(row.get("pending_solution_text")).contains("Проверяем статус"));
    }

    @Test
    void keepsPendingReviewWithoutAutoApproveWhenReplyMatchesPending() {
        String question = "Проблема с оплатой заказа #P-10";
        String key = buildKey(question);
        jdbcTemplate.update(
                """
                INSERT INTO ai_agent_solution_memory(
                    query_key, query_text, solution_text, source, times_used, times_confirmed, times_corrected,
                    review_required, pending_solution_text, last_operator, last_ticket_id, last_client_message,
                    status, trust_level, source_type, safety_level, created_at, updated_at
                ) VALUES (?, ?, ?, 'operator', 0, 0, 0, 1, ?, 'seed', 'T-200', ?, 'draft', 'low', 'operator', 'normal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                key,
                question,
                "Старое решение",
                "Новая формулировка",
                question
        );

        AiLearningService.UpsertResult result = service.upsertLearningSolution(
                "T-200",
                question,
                "Новая формулировка",
                "operator-2",
                0.42d
        );

        assertNotNull(result);
        assertEquals("pending_unchanged", result.action());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT solution_text, pending_solution_text, review_required FROM ai_agent_solution_memory WHERE query_key = ?",
                key
        );
        assertEquals("Старое решение", row.get("solution_text"));
        assertEquals("Новая формулировка", row.get("pending_solution_text"));
        assertEquals(1, ((Number) row.get("review_required")).intValue());
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_solution_memory (
                    query_key VARCHAR(128) PRIMARY KEY,
                    query_text VARCHAR(600),
                    solution_text VARCHAR(2000),
                    source VARCHAR(64),
                    times_used INTEGER DEFAULT 0,
                    times_confirmed INTEGER DEFAULT 0,
                    times_corrected INTEGER DEFAULT 0,
                    review_required INTEGER DEFAULT 0,
                    pending_solution_text VARCHAR(2000),
                    last_operator VARCHAR(120),
                    last_ticket_id VARCHAR(120),
                    last_client_message VARCHAR(600),
                    status VARCHAR(32),
                    trust_level VARCHAR(32),
                    source_type VARCHAR(64),
                    safety_level VARCHAR(32),
                    intent_key VARCHAR(120),
                    slot_signature VARCHAR(300),
                    slots_json VARCHAR(3000),
                    scope_channel VARCHAR(120),
                    scope_business VARCHAR(120),
                    scope_location VARCHAR(120),
                    verified_by VARCHAR(120),
                    last_verified_at TIMESTAMP,
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_solution_memory_history (
                    id IDENTITY PRIMARY KEY,
                    query_key VARCHAR(128),
                    changed_by VARCHAR(120),
                    change_source VARCHAR(64),
                    change_action VARCHAR(64),
                    old_query_text VARCHAR(600),
                    old_solution_text VARCHAR(2000),
                    old_review_required INTEGER,
                    new_query_text VARCHAR(600),
                    new_solution_text VARCHAR(2000),
                    new_review_required INTEGER,
                    note VARCHAR(500),
                    created_at TIMESTAMP
                )
                """);
    }

    private String buildKey(String question) {
        try {
            String normalized = question.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

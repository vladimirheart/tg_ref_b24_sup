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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRetrievalServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AiIntentService intentService;
    private AiRetrievalService retrievalService;
    private AiKnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:ai_retrieval_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        intentService = new AiIntentService(jdbcTemplate, new ObjectMapper());
        retrievalService = new AiRetrievalService(jdbcTemplate, intentService);
        knowledgeService = new AiKnowledgeService(jdbcTemplate);
    }

    @Test
    void prefersExactSlotSignatureForSimilarOrderStatusQueries() {
        seedTicketScope("T-1", 1L, "telegram", "блинбери", "центр");
        String keyA = insertApprovedMemory("Где заказ #A-100", "Заказ A-100 в пути и скоро будет у вас.", 3, "telegram", "блинбери", "центр");
        insertApprovedMemory("Где заказ #B-200", "Заказ B-200 готов к выдаче.", 3, "telegram", "блинбери", "центр");

        AiRetrievalService.RetrievalResult result = retrievalService.retrieve("T-1", "Подскажите, где мой заказ #A-100?", 3);

        assertFalse(result.candidates().isEmpty());
        assertEquals(keyA, result.candidates().get(0).memoryKey());
        assertEquals("order_status", result.candidates().get(0).intentKey());
        assertEquals(intentService.extract("Где заказ #A-100").slotSignature(), result.candidates().get(0).slotSignature());
    }

    @Test
    void marksConflictWhenTopEvidenceDisagrees() {
        seedTicketScope("T-2", 2L, "telegram", "блинбери", "центр");
        insertApprovedMemory("Где заказ #A-100", "Заказ A-100 в пути и скоро будет у вас.", 2, "telegram", "блинбери", "центр");
        insertApprovedMemory("Что с заказом #A-100", "Заказ A-100 отменен, ожидайте возврат средств.", 2, "telegram", "блинбери", "центр");

        AiRetrievalService.RetrievalResult result = retrievalService.retrieve("T-2", "Где мой заказ #A-100?", 3);

        assertTrue(result.consistency().hasConflict());
        assertFalse(result.consistency().autoReplyAllowed());
        assertEquals("evidence_conflict", result.consistency().reason());
    }

    @Test
    void knowledgeUnitLinksProvideSecondConfirmation() {
        seedTicketScope("T-3", 3L, "telegram", "блинбери", "центр");
        String key1 = insertApprovedMemory("Где заказ #A-100", "Заказ A-100 в пути и скоро будет у вас.", 2, "telegram", "блинбери", "центр");
        String key2 = insertApprovedMemory("Подскажите статус заказа #A-100", "Заказ A-100 в пути и скоро будет у вас.", 2, "telegram", "блинбери", "центр");
        knowledgeService.syncFromMemory(key1);
        knowledgeService.syncFromMemory(key2);

        AiRetrievalService.RetrievalResult result = retrievalService.retrieve("T-3", "Где мой заказ #A-100?", 3);

        assertFalse(result.candidates().isEmpty());
        assertTrue(result.consistency().supportCount() >= 2);
        assertTrue(result.consistency().autoReplyAllowed());
        assertFalse(result.consistency().hasConflict());
        assertNotNull(result.candidates().get(0).canonicalKey());
    }

    private void createSchema() {
        jdbcTemplate.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY, platform VARCHAR(32), channel_name VARCHAR(120))");
        jdbcTemplate.execute("CREATE TABLE tickets (user_id BIGINT, ticket_id VARCHAR(120), channel_id BIGINT, PRIMARY KEY (user_id, ticket_id))");
        jdbcTemplate.execute("CREATE TABLE messages (group_msg_id BIGINT PRIMARY KEY, ticket_id VARCHAR(120), business VARCHAR(120), location_name VARCHAR(120), channel_id BIGINT)");
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
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_knowledge_unit (
                    id IDENTITY PRIMARY KEY,
                    unit_key VARCHAR(128) UNIQUE,
                    title VARCHAR(200),
                    body_text VARCHAR(2000),
                    intent_key VARCHAR(120),
                    slot_signature VARCHAR(300),
                    business VARCHAR(120),
                    location VARCHAR(120),
                    channel VARCHAR(120),
                    status VARCHAR(32),
                    source_ref VARCHAR(160),
                    created_at TIMESTAMP,
                    updated_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_memory_link (
                    id IDENTITY PRIMARY KEY,
                    query_key VARCHAR(128),
                    knowledge_unit_id BIGINT,
                    link_type VARCHAR(32),
                    weight DOUBLE,
                    created_at TIMESTAMP
                )
                """);
    }

    private void seedTicketScope(String ticketId, long channelId, String platform, String business, String location) {
        jdbcTemplate.update("INSERT INTO channels(id, platform, channel_name) VALUES (?, ?, ?)", channelId, platform, platform + "-channel");
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id, channel_id) VALUES (1, ?, ?)", ticketId, channelId);
        jdbcTemplate.update("INSERT INTO messages(group_msg_id, ticket_id, business, location_name, channel_id) VALUES (?, ?, ?, ?, ?)", channelId, ticketId, business, location, channelId);
    }

    private String insertApprovedMemory(String queryText,
                                        String solutionText,
                                        int timesConfirmed,
                                        String channel,
                                        String business,
                                        String location) {
        String key = buildKey(queryText);
        AiIntentService.IntentMatch intentMatch = intentService.extract(queryText);
        jdbcTemplate.update(
                """
                INSERT INTO ai_agent_solution_memory(
                    query_key, query_text, solution_text, source, times_used, times_confirmed, times_corrected,
                    review_required, pending_solution_text, last_operator, last_ticket_id, last_client_message,
                    status, trust_level, source_type, safety_level, intent_key, slot_signature, slots_json,
                    scope_channel, scope_business, scope_location, created_at, updated_at
                ) VALUES (?, ?, ?, 'operator', 0, ?, 0, 0, NULL, 'seed', 'seed-ticket', ?, 'approved', 'medium', 'operator', 'normal',
                          ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                key,
                queryText,
                solutionText,
                timesConfirmed,
                queryText,
                intentMatch.intentKey(),
                intentMatch.slotSignature(),
                intentMatch.slotsJson(),
                channel,
                business,
                location
        );
        return key;
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

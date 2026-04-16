package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiOfflineEvaluationServiceTest {

    private AiOfflineEvaluationService service;

    @BeforeEach
    void setUp() throws Exception {
        Path sharedDir = Files.createTempDirectory("ai-offline-eval-settings");
        Files.writeString(sharedDir.resolve("settings.json"), """
                {
                  "dialog_config": {
                    "ai_agent_offline_eval_enabled": true
                  }
                }
                """);
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:ai_offline_eval_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE ai_agent_offline_eval_run (
                    id IDENTITY PRIMARY KEY,
                    dataset_version VARCHAR(120),
                    actor VARCHAR(120),
                    cases_total INTEGER,
                    cases_passed INTEGER,
                    intent_accuracy DOUBLE,
                    policy_accuracy DOUBLE,
                    retrieval_hit_rate DOUBLE,
                    confirmed_reply_rate DOUBLE,
                    details_json CLOB,
                    created_at TIMESTAMP
                )
                """);
        ObjectMapper objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, sharedDir.toString());
        AiIntentService aiIntentService = new AiIntentService(jdbcTemplate, objectMapper);
        AiRetrievalService aiRetrievalService = new AiRetrievalService(jdbcTemplate, aiIntentService);
        service = new AiOfflineEvaluationService(jdbcTemplate, aiIntentService, aiRetrievalService, sharedConfigService, objectMapper);
    }

    @Test
    void datasetOverviewContainsAtLeastThreeHundredCases() {
        Map<String, Object> overview = service.loadDatasetOverview();

        assertTrue(((Number) overview.get("cases_total")).intValue() >= 300);
    }

    @Test
    void manualRunPersistsLatestEvaluationSummary() {
        Map<String, Object> result = service.runEvaluationNow("tester");

        assertTrue(((Boolean) result.get("available")));
        assertTrue(((Number) result.get("dataset_cases")).intValue() >= 300);
    }
}

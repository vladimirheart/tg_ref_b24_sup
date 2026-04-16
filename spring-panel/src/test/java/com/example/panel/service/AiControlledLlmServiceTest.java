package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiControlledLlmServiceTest {

    @Test
    void mockRewriteProducesEffectiveQueryWhenLlmEnabled() throws Exception {
        AiControlledLlmService service = createService("""
                {
                  "dialog_config": {
                    "ai_agent_llm_enabled": true,
                    "ai_agent_llm_provider": "mock",
                    "ai_agent_llm_roles": "rewrite,composer,explainer,parser",
                    "ai_agent_llm_rollout_mode": "selective_auto_reply",
                    "ai_agent_llm_output_guard_enabled": true
                  }
                }
                """);

        AiControlledLlmService.RewriteResult result = service.rewriteQuery(
                "T-100",
                "Где мой заказ #A-100 по Блинбери в telegram?"
        );

        assertTrue(result.usedLlm());
        assertNotNull(result.effectiveQuery());
        assertTrue(result.effectiveQuery().contains("A-100"));
    }

    @Test
    void composerGuardBlocksRefundAutoReply() throws Exception {
        AiControlledLlmService service = createService("""
                {
                  "dialog_config": {
                    "ai_agent_llm_enabled": true,
                    "ai_agent_llm_provider": "mock",
                    "ai_agent_llm_roles": "composer",
                    "ai_agent_llm_rollout_mode": "selective_auto_reply",
                    "ai_agent_llm_output_guard_enabled": true
                  }
                }
                """);

        AiControlledLlmService.TextResult result = service.composeReply(
                "T-200",
                "Хочу возврат по заказу #R-200",
                "Передаем запрос на проверку оператору.",
                null,
                "refund_request",
                true
        );

        assertFalse(result.usedLlm());
        assertNull(result.text());
        assertTrue(String.valueOf(result.reason()).contains("guard"));
    }

    private AiControlledLlmService createService(String settingsJson) throws Exception {
        Path sharedDir = Files.createTempDirectory("ai-llm-settings");
        Files.writeString(sharedDir.resolve("settings.json"), settingsJson);
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:ai_llm_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, sharedDir.toString());
        AiIntentService aiIntentService = new AiIntentService(jdbcTemplate, objectMapper);
        return new AiControlledLlmService(sharedConfigService, aiIntentService, objectMapper);
    }
}

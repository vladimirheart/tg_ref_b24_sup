package com.example.panel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class DialogAiAssistantConfigServiceTest {

    @Test
    void isAgentEnabledTreatsOffAsFalse() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                sharedConfigService,
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("dialog_config", Map.of("ai_agent_enabled", "off"));
        when(sharedConfigService.loadSettings()).thenReturn(settings);

        DialogAiAssistantConfigService service = new DialogAiAssistantConfigService(jdbcTemplate, sharedConfigService, persistenceService);

        assertThat(service.isAgentEnabled()).isFalse();
    }

    @Test
    void resolveAgentModeFallsBackForInvalidValue() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                sharedConfigService,
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("dialog_config", Map.of("ai_agent_mode", "strange"));
        when(sharedConfigService.loadSettings()).thenReturn(settings);

        DialogAiAssistantConfigService service = new DialogAiAssistantConfigService(jdbcTemplate, sharedConfigService, persistenceService);

        assertThat(service.resolveAgentMode()).isEqualTo("auto_reply");
    }

    @Test
    void evaluateAutoReplyGuardReturnsCooldownWindow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        DialogAiAssistantPersistenceService persistenceService = spy(new DialogAiAssistantPersistenceService(
                jdbcTemplate,
                sharedConfigService,
                mock(AiPolicyService.class),
                mock(AiKnowledgeService.class),
                new ObjectMapper()
        ));
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("dialog_config", Map.of(
                "ai_agent_auto_reply_window_minutes", 60,
                "ai_agent_auto_reply_cooldown_seconds", 120,
                "ai_agent_max_auto_replies_per_dialog", 3
        ));
        when(sharedConfigService.loadSettings()).thenReturn(settings);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("T-1"), eq("-60 minutes"))).thenReturn(0);
        when(jdbcTemplate.queryForList(anyString(), eq("T-1"))).thenReturn(List.of(Map.of(
                "last_action", "auto_reply",
                "updated_at", "2026-05-12T10:00:00Z"
        )));
        when(persistenceService.parseInstant("2026-05-12T10:00:00Z")).thenReturn(Instant.now().minusSeconds(30));

        DialogAiAssistantConfigService service = new DialogAiAssistantConfigService(jdbcTemplate, sharedConfigService, persistenceService);

        DialogAiAssistantConfigService.AutoReplyGuard guard = service.evaluateAutoReplyGuard("T-1");

        assertThat(guard.allowed()).isFalse();
        assertThat(guard.reason()).startsWith("cooldown:");
    }
}

package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DialogMacroGovernanceSupportServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogMacroGovernanceSupportService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-macro-governance-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogMacroGovernanceSupportService(jdbcTemplate);
        createSchema();
    }

    @Test
    void loadsMacroTemplateUsageFromTelemetryAudit() {
        Instant now = Instant.now();
        insertAudit("macro_apply", "macro-1", "Welcome", now.minusSeconds(3600).toString(), null);
        insertAudit("macro_apply", "macro-1", "Welcome", now.minusSeconds(1800).toString(), "validation_error");
        insertAudit("macro_preview", "macro-1", "Welcome", now.minusSeconds(900).toString(), null);

        Map<String, Object> usage = service.loadMacroTemplateUsage("macro-1", "Welcome", 30);

        assertThat(usage)
                .containsEntry("usage_count", 2L)
                .containsEntry("preview_count", 1L)
                .containsEntry("error_count", 1L);
        assertThat(String.valueOf(usage.get("last_used_at"))).contains("T");
    }

    @Test
    void resolvesKnownVariablesAliasesAndIssuePayload() {
        Map<String, Object> dialogConfig = Map.of(
                "macro_variable_catalog", List.of(Map.of("key", "ORDER_ID"), Map.of("key", "client_name")),
                "macro_variable_defaults", Map.of("delivery_eta", "soon")
        );

        Set<String> variables = service.resolveKnownMacroVariableKeys(dialogConfig);
        List<String> extractedVariables = service.extractMacroTemplateVariables("{{ order_id }} and {{ delivery_eta }}");
        List<String> aliases = service.resolveMacroTagAliases(List.of("Priority", "VIP"));
        Map<String, Object> issue = service.buildMacroGovernanceIssue(
                "owner_missing",
                "macro-1",
                "Welcome",
                "weird",
                "rollout_blocker",
                "Missing owner",
                "owner=missing");

        assertThat(variables).contains("order_id", "delivery_eta", "client_name");
        assertThat(extractedVariables).containsExactly("order_id", "delivery_eta");
        assertThat(aliases).containsExactly("priority", "vip");
        assertThat(service.resolveMacroUsageTier(1, 1, 5)).isEqualTo("low");
        assertThat(service.resolveMacroTierSlaDays("medium", 7, 30, 90)).isEqualTo(30);
        assertThat(issue)
                .containsEntry("status", "hold")
                .containsEntry("template_id", "macro-1")
                .containsEntry("type", "owner_missing");
    }

    private void insertAudit(String eventType, String templateId, String templateName, String createdAt, String errorCode) {
        jdbcTemplate.update("""
                INSERT INTO workspace_telemetry_audit(event_type, template_id, template_name, created_at, error_code)
                VALUES (?, ?, ?, ?, ?)
                """,
                eventType, templateId, templateName, createdAt, errorCode);
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE workspace_telemetry_audit (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type TEXT,
                    template_id TEXT,
                    template_name TEXT,
                    created_at TEXT,
                    error_code TEXT
                )
                """);
    }
}

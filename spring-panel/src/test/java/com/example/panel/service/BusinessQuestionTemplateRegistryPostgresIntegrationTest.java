package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessQuestionTemplateRegistryPostgresIntegrationTest {

    @TempDir
    Path configDir;

    @Test
    void scopedRuntimeNeverFallsBackToAnotherBusinessOrLegacyTemplate() throws Exception {
        JdbcTemplate jdbc = schema();
        ObjectMapper mapper = new ObjectMapper();
        SharedConfigService config = new SharedConfigService(mapper, configDir.toString());
        config.saveSettings(settings("A", "B"));
        BusinessQuestionTemplateRegistryService registry = new BusinessQuestionTemplateRegistryService(
                jdbc, config, new BotSettingsPayloadNormalizer(), mapper);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO channels(id, is_active) VALUES (10, TRUE)");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
                + "VALUES (10, ?, 'EXPLICIT_SELECTION'), (10, ?, 'EXPLICIT_SELECTION')", sushi, bliny);

        assertThat(registry.availableTemplates()).extracting(
                BusinessQuestionTemplateRegistryService.TemplateSummary::id)
                .containsExactly("q-a", "q-b");
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        assertThatThrownBy(() -> registry.bind(sushi, "q-legacy"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.bind(sushi, "q-a")).isPresent();
        assertThat(registry.bind(bliny, "q-b")).isPresent();
        assertThatThrownBy(() -> registry.bind(bliny, "q-a"))
                .isInstanceOf(DuplicateKeyException.class);
        var a = registry.resolveExplicitChannel(10, sushi).orElseThrow();
        var b = registry.resolveExplicitChannel(10, bliny).orElseThrow();
        assertThat(a.botSettings().get("active_template_id")).isEqualTo("q-a");
        assertThat(b.botSettings().get("active_template_id")).isEqualTo("q-b");
        assertThat(a.botSettings().get("question_templates").toString()).contains("Вопрос A")
                .doesNotContain("Вопрос B", "Legacy");
        assertThat(b.botSettings().get("question_templates").toString()).contains("Вопрос B")
                .doesNotContain("Вопрос A", "Legacy");
        assertThat(a.locationTree()).isEmpty();
        assertThat(a.presetDefinitions()).isEmpty();
        assertThat(registry.resolveExplicitChannel(10, 999)).isEmpty();
        jdbc.update("INSERT INTO general_channel_question_template_bindings "
                + "(channel_id, template_id, template_sha256) VALUES (10, 'q-legacy', ?)",
                "a".repeat(64));
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        jdbc.update("UPDATE general_channel_question_template_bindings SET is_active = FALSE WHERE channel_id = 10");
        assertThat(registry.resolveExplicitChannel(10, sushi)).isPresent();

        config.saveSettings(settings("A", "изменённый B"));
        assertThat(registry.resolveExplicitChannel(10, sushi)).isPresent();
        assertThat(registry.resolveExplicitChannel(10, bliny)).isEmpty();
        assertThat(registry.bind(bliny, "q-b")).isPresent();
        assertThat(registry.resolveExplicitChannel(10, bliny)).isPresent();

        assertThat(registry.deactivate(bliny)).isTrue();
        assertThat(registry.resolveExplicitChannel(10, bliny)).isEmpty();
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        assertThat(registry.bind(bliny, "q-b")).isPresent();
        assertThat(registry.resolveExplicitChannel(10, sushi)).isPresent();
    }

    @Test
    void mixedInactiveArchivedAndPresetBindingsFailClosed() throws Exception {
        JdbcTemplate jdbc = schema();
        ObjectMapper mapper = new ObjectMapper();
        SharedConfigService config = new SharedConfigService(mapper, configDir.toString());
        config.saveSettings(settings("A", "B"));
        BusinessQuestionTemplateRegistryService registry = new BusinessQuestionTemplateRegistryService(
                jdbc, config, new BotSettingsPayloadNormalizer(), mapper);
        long sushi = businessId(jdbc, "sushi");
        long bliny = businessId(jdbc, "bliny");
        jdbc.update("INSERT INTO channels(id, is_active) VALUES (10, TRUE)");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
                + "VALUES (10, ?, 'EXPLICIT_SELECTION'), (10, ?, 'SINGLE_BUSINESS')", sushi, bliny);
        registry.bind(sushi, "q-a");
        registry.bind(bliny, "q-b");
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        jdbc.update("UPDATE business_channel_bindings SET resolution_mode = 'EXPLICIT_SELECTION' "
                + "WHERE channel_id = 10 AND business_id = ?", bliny);
        assertThat(registry.resolveExplicitChannel(10, sushi)).isPresent();
        jdbc.update("UPDATE channels SET question_template_id = 'q-a' WHERE id = 10");
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        jdbc.update("UPDATE channels SET question_template_id = NULL WHERE id = 10");
        jdbc.update("UPDATE businesses SET status = 'archived' WHERE id = ?", bliny);
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        jdbc.update("UPDATE businesses SET status = 'active' WHERE id = ?", bliny);
        jdbc.update("UPDATE channels SET is_active = FALSE WHERE id = 10");
        assertThat(registry.resolveExplicitChannel(10, sushi)).isEmpty();
        assertThat(registry.bind(999, "q-a")).isEmpty();
        assertThatThrownBy(() -> registry.bind(sushi, "missing"))
                .isInstanceOf(IllegalArgumentException.class);

        config.saveSettings(Map.of("bot_settings", Map.of("question_templates", List.of(
                Map.of("id", "q-preset", "question_flow", List.of(
                        Map.of("id", "place", "type", "preset", "text", "Где?")))))));
        assertThat(registry.availableTemplates()).isEmpty();
        assertThatThrownBy(() -> registry.bind(sushi, "q-preset"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Map<String, Object> settings(String textA, String textB) {
        return Map.of("bot_settings", Map.of(
                "active_template_id", "q-legacy",
                "question_templates", List.of(
                        template("q-legacy", "Legacy"),
                        template("q-a", "Вопрос " + textA),
                        template("q-b", "Вопрос " + textB))));
    }

    private Map<String, Object> template(String id, String question) {
        return Map.of("id", id, "name", id, "question_flow", List.of(
                Map.of("id", "problem", "type", "text", "text", question)));
    }

    private JdbcTemplate schema() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("business_template_registry");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY, is_active BOOLEAN NOT NULL DEFAULT TRUE, "
                + "question_template_id TEXT)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, "
                + "PRIMARY KEY (user_id, ticket_id))");
        migrate(jdbc, "V51__multibusiness_foundation.sql");
        migrate(jdbc, "V55__business_question_template_bindings.sql");
        migrate(jdbc, "V57__general_channel_question_template_bindings.sql");
        return jdbc;
    }

    private void migrate(JdbcTemplate jdbc, String name) throws Exception {
        Path file = Path.of("src/main/resources/db/migration/postgresql", name);
        for (String statement : Files.readString(file).replace("\r\n", "\n").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement.trim());
            }
        }
    }

    private long businessId(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("SELECT id FROM businesses WHERE code = ?", Long.class, code);
    }
}

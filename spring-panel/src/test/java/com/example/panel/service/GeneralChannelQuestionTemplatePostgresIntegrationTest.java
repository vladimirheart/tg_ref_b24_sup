package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneralChannelQuestionTemplatePostgresIntegrationTest {

    @TempDir Path configDir;

    @Test
    void explicitlyBoundGeneralChannelPreservesQuestionOrderAndFailsClosedOnDrift() throws Exception {
        JdbcTemplate jdbc = schema();
        SharedConfigService config = config();
        config.saveSettings(settings("Первый", "Второй"));
        GeneralChannelQuestionTemplateService service = service(jdbc, config);
        jdbc.update("INSERT INTO channels(id, is_active, question_template_id) VALUES (10, TRUE, 'q-general')");

        assertThat(service.resolve(10)).isEmpty();
        var binding = service.bind(10, "q-general").orElseThrow();
        assertThat(binding.templateSha256()).hasSize(64);
        var runtime = service.resolve(10).orElseThrow();
        assertThat(runtime.scopeKind()).isEqualTo("GENERAL");
        assertThat(runtime.scopeSelectionSource()).isEqualTo("GENERAL_CHANNEL");
        assertThat(runtime.botSettings().get("active_template_id")).isEqualTo("q-general");
        assertThat(runtime.botSettings().get("question_templates").toString())
                .contains("Первый", "Второй").doesNotContain("Business only");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> onlyTemplate =
                (List<Map<String, Object>>) runtime.botSettings().get("question_templates");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flow = (List<Map<String, Object>>) onlyTemplate.get(0).get("question_flow");
        assertThat(flow).extracting(question -> question.get("id")).containsExactly("q2", "q1");
        assertThat(flow).extracting(question -> question.get("order")).containsExactly(2, 1);
        assertThat(runtime.locationTree()).isEmpty();
        assertThat(runtime.presetDefinitions()).isEmpty();

        config.saveSettings(settings("Первый", "Изменённый"));
        assertThat(service.resolve(10)).isEmpty();
        service.bind(10, "q-general");
        assertThat(service.resolve(10)).isPresent();

        long business = businessId(jdbc, "sushi");
        jdbc.update("INSERT INTO business_channel_bindings(channel_id, business_id, resolution_mode) "
                + "VALUES (10, ?, 'SINGLE_BUSINESS')", business);
        assertThat(service.resolve(10)).isEmpty();
        assertThatThrownBy(() -> service.bind(10, "q-general"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("business bindings");
        jdbc.update("UPDATE business_channel_bindings SET is_active = FALSE WHERE channel_id = 10");
        assertThat(service.resolve(10)).isPresent();

        jdbc.update("UPDATE channels SET question_template_id = 'q-business' WHERE id = 10");
        assertThat(service.resolve(10)).isEmpty();
        assertThatThrownBy(() -> service.bind(10, "q-general"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Select this question template");
        jdbc.update("UPDATE channels SET question_template_id = 'q-general' WHERE id = 10");
        assertThat(service.deactivate(10)).isTrue();
        assertThat(service.resolve(10)).isEmpty();
        assertThat(service.deactivate(10)).isFalse();
    }

    @Test
    void rejectsBusinessTemplatesPresetsAndUnselectedChannels() throws Exception {
        JdbcTemplate jdbc = schema();
        SharedConfigService config = config();
        config.saveSettings(settings("Первый", "Второй"));
        GeneralChannelQuestionTemplateService service = service(jdbc, config);
        jdbc.update("INSERT INTO channels(id, is_active, question_template_id) VALUES (10, TRUE, 'q-general')");
        jdbc.update("INSERT INTO channels(id, is_active, question_template_id) VALUES (11, TRUE, NULL)");
        assertThat(service.bind(99, "q-general")).isEmpty();
        assertThatThrownBy(() -> service.bind(11, "q-general"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.bind(10, "missing"))
                .isInstanceOf(IllegalArgumentException.class);

        long business = businessId(jdbc, "sushi");
        jdbc.update("INSERT INTO business_question_template_bindings "
                + "(business_id, template_id, template_sha256) VALUES (?, 'q-general', ?)",
                business, "a".repeat(64));
        assertThatThrownBy(() -> service.bind(10, "q-general"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("bound to a business");
        jdbc.update("DELETE FROM business_question_template_bindings WHERE business_id = ?", business);

        config.saveSettings(Map.of("bot_settings", Map.of("question_templates", List.of(
                Map.of("id", "q-general", "question_flow", List.of(
                        Map.of("id", "place", "type", "preset", "text", "Где?")))))));
        assertThatThrownBy(() -> service.bind(10, "q-general"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unsuitable");
    }

    private SharedConfigService config() {
        return new SharedConfigService(new ObjectMapper(), configDir.toString());
    }

    private GeneralChannelQuestionTemplateService service(JdbcTemplate jdbc, SharedConfigService config) {
        return new GeneralChannelQuestionTemplateService(jdbc, config,
                new BotSettingsPayloadNormalizer(), new ObjectMapper());
    }

    private Map<String, Object> settings(String first, String second) {
        return Map.of("bot_settings", Map.of(
                "active_template_id", "q-general",
                "question_templates", List.of(
                        Map.of("id", "q-general", "name", "Общий", "question_flow", List.of(
                                Map.of("id", "q2", "order", 2, "type", "custom", "text", second),
                                Map.of("id", "q1", "order", 1, "type", "custom", "text", first))),
                        Map.of("id", "q-business", "name", "Business only", "question_flow", List.of(
                                Map.of("id", "b1", "order", 1, "type", "custom", "text", "Business only"))))));
    }

    private JdbcTemplate schema() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("general_channel_template");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, username TEXT NOT NULL UNIQUE)");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY, is_active BOOLEAN NOT NULL DEFAULT TRUE, "
                + "question_template_id TEXT)");
        jdbc.execute("CREATE TABLE tickets (user_id BIGINT NOT NULL, ticket_id TEXT NOT NULL, "
                + "PRIMARY KEY (user_id, ticket_id))");
        migrate(jdbc, "V51__multibusiness_foundation.sql");
        migrate(jdbc, "V55__business_question_template_bindings.sql");
        migrate(jdbc, "V57__general_channel_question_template_bindings.sql");
        migrate(jdbc, "V74__shared_channel_general_questionnaire.sql");
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

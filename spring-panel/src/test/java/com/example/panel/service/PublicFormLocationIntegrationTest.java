package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicFormLocationIntegrationTest {

    private static Path dbFile;
    private static Path sharedConfigDir;
    private static Path monitoringDbFile;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-location-test", ".db");
        sharedConfigDir = Files.createTempDirectory("panel-location-shared-config");
        monitoringDbFile = Files.createTempFile("panel-location-monitoring", ".db");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
        registry.add("shared-config.dir", () -> sharedConfigDir.toString());
        registry.add("APP_DB_MONITORING", () -> monitoringDbFile.toString());
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SharedConfigService sharedConfigService;

    @Autowired
    private PublicFormService publicFormService;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
        jdbcTemplate.update("DELETE FROM app_settings");
        sharedConfigService.saveSettings(new LinkedHashMap<>());
        sharedConfigService.saveLocations(Map.of(
                "tree", Map.of(),
                "statuses", Map.of()
        ));
    }

    @Test
    void loadConfigEnrichesLocationQuestionsWithCascadingTrees() {
        saveLocationCatalog(Map.of(
                "БлинБери", Map.of(
                        "Корпоративная сеть", Map.of("Москва", List.of("Тверская")),
                        "Партнёры-франчайзи", Map.of("Пенза", List.of("Коллаж ФК"))
                ),
                "СушиВёсла", Map.of(
                        "Партнёры-франчайзи", Map.of("Ростов-на-Дону", List.of("Зорге 33"))
                )
        ));
        insertChannel(101L, "web-location-config", """
                {"schemaVersion":1,"enabled":true,"fields":[
                  {"id":"business","text":"Бизнес","type":"select","required":true},
                  {"id":"location_type","text":"Тип бизнеса","type":"select","required":true},
                  {"id":"city","text":"Город","type":"select","required":true},
                  {"id":"location_name","text":"Локация","type":"select","required":true}
                ]}
                """);

        PublicFormConfig config = publicFormService.loadConfigRaw("web-location-config").orElseThrow();
        Map<String, PublicFormQuestion> questionsById = config.questions().stream()
                .collect(java.util.stream.Collectors.toMap(PublicFormQuestion::id, question -> question));

        assertThat(questionsById.keySet()).contains("business", "location_type", "city", "location_name");
        assertThat(config.questions()).extracting(PublicFormQuestion::type).containsOnly("select");

        @SuppressWarnings("unchecked")
        List<String> businessOptions = (List<String>) questionsById.get("business").metadata().get("options");
        assertThat(businessOptions).containsExactly("БлинБери", "СушиВёсла");

        @SuppressWarnings("unchecked")
        Map<String, Object> cityTree = (Map<String, Object>) questionsById.get("city").metadata().get("tree");
        @SuppressWarnings("unchecked")
        Map<String, Object> blinberiCityTree = (Map<String, Object>) cityTree.get("БлинБери");
        assertThat((List<String>) blinberiCityTree.get("Партнёры-франчайзи")).containsExactly("Пенза");

        @SuppressWarnings("unchecked")
        Map<String, Object> locationTree = (Map<String, Object>) questionsById.get("location_name").metadata().get("tree");
        @SuppressWarnings("unchecked")
        Map<String, Object> blinberiLocationTree = (Map<String, Object>) locationTree.get("БлинБери");
        @SuppressWarnings("unchecked")
        Map<String, Object> franchiseLocations = (Map<String, Object>) blinberiLocationTree.get("Партнёры-франчайзи");
        assertThat((List<String>) franchiseLocations.get("Пенза")).containsExactly("Коллаж ФК");
    }

    @Test
    void createSessionRejectsInvalidLocationCombinationAndAcceptsValidOne() {
        saveLocationCatalog(Map.of(
                "БлинБери", Map.of(
                        "Корпоративная сеть", Map.of("Москва", List.of("Тверская")),
                        "Партнёры-франчайзи", Map.of("Пенза", List.of("Коллаж ФК"))
                )
        ));
        insertChannel(102L, "web-location-submit", """
                {"schemaVersion":1,"enabled":true,"fields":[
                  {"id":"business","text":"Бизнес","type":"select","required":true},
                  {"id":"location_type","text":"Тип бизнеса","type":"select","required":true},
                  {"id":"city","text":"Город","type":"select","required":true},
                  {"id":"location_name","text":"Локация","type":"select","required":true}
                ]}
                """);

        PublicFormSubmission invalid = new PublicFormSubmission(
                "Нужна помощь",
                "Анна",
                "+79991234567",
                "anna",
                null,
                Map.of(
                        "business", "БлинБери",
                        "location_type", "Корпоративная сеть",
                        "city", "Пенза",
                        "location_name", "Коллаж ФК"
                ),
                null
        );

        assertThatThrownBy(() -> publicFormService.createSession("web-location-submit", invalid, "ip-location-invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("недопустимое значение");

        PublicFormSubmission valid = new PublicFormSubmission(
                "Нужна помощь",
                "Анна",
                "+79991234567",
                "anna",
                null,
                Map.of(
                        "business", "БлинБери",
                        "location_type", "Партнёры-франчайзи",
                        "city", "Пенза",
                        "location_name", "Коллаж ФК"
                ),
                null
        );

        PublicFormSessionDto session = publicFormService.createSession("web-location-submit", valid, "ip-location-valid");
        assertThat(session.ticketId()).startsWith("web-");
    }

    private void insertChannel(long id, String publicId, String questionsCfg) {
        jdbcTemplate.update(
                "INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, ?, ?)",
                id,
                publicId,
                "Веб-форма",
                publicId,
                questionsCfg
        );
    }

    private void saveLocationCatalog(Map<String, Object> tree) {
        sharedConfigService.saveLocations(Map.of(
                "tree", tree,
                "statuses", Map.of()
        ));
    }
}

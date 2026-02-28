package com.example.panel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("sqlite")
@TestPropertySource(properties = {
        "spring.flyway.locations=classpath:db/migration/sqlite"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicFormFlowSmokeIntegrationTest {

    private static Path dbFile;

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) throws IOException {
        dbFile = Files.createTempFile("panel-public-form-smoke", ".db");
        registry.add("app.datasource.sqlite.path", () -> dbFile.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM chat_history");
        jdbcTemplate.update("DELETE FROM task_history");
        jdbcTemplate.update("DELETE FROM task_links");
        jdbcTemplate.update("DELETE FROM task_people");
        jdbcTemplate.update("DELETE FROM task_comments");
        jdbcTemplate.update("DELETE FROM tasks");
        jdbcTemplate.update("DELETE FROM task_seq");
        jdbcTemplate.update("DELETE FROM web_form_sessions");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM tickets");
        jdbcTemplate.update("DELETE FROM client_statuses");
        jdbcTemplate.update("DELETE FROM channels");
    }

    @Test
    void publicFormSubmitCreatesDialogVisibleInDialogsApi() throws Exception {
        jdbcTemplate.update("INSERT INTO channels (id, token, channel_name, is_active, created_at, public_id, questions_cfg) VALUES (41, 'web-smoke', 'Веб-форма', 1, CURRENT_TIMESTAMP, 'web-smoke', ?)",
                "{\"schemaVersion\":1,\"enabled\":true,\"captchaEnabled\":false,\"disabledStatus\":410,\"fields\":[{\"id\":\"topic\",\"text\":\"Тема\",\"type\":\"text\",\"required\":true,\"order\":10}]}");

        mockMvc.perform(get("/api/public/forms/web-smoke/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.questions[0].id").value("topic"));

        MvcResult createSessionResult = mockMvc.perform(post("/api/public/forms/web-smoke/sessions")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Нужна помощь по оплате",
                                  "clientName": "Ирина",
                                  "clientContact": "+79990000000",
                                  "username": "irina",
                                  "answers": {
                                    "topic": "Оплата"
                                  },
                                  "requestId": "smoke-req-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.ticketId").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode createSessionPayload = objectMapper.readTree(createSessionResult.getResponse().getContentAsString());
        String ticketId = createSessionPayload.path("ticketId").asText();
        String token = createSessionPayload.path("token").asText();
        assertThat(ticketId).startsWith("web-");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/public/forms/web-smoke/sessions/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session.ticketId").value(ticketId));

        mockMvc.perform(get("/api/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dialogs[0].ticketId").value(ticketId));
    }
}

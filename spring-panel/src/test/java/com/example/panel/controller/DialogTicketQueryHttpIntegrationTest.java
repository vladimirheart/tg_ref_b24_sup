package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.RestExceptionHandler;
import com.example.panel.service.DialogReadService;
import com.example.panel.service.DialogWorkspaceService;
import com.example.panel.service.KnowledgeNoteLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@SpringBootTest(
        classes = DialogTicketQueryHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.group.readiness.include=ping")
class DialogTicketQueryHttpIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @EnableMethodSecurity
    @Import({DialogTicketQueryReadController.class, DialogWorkspaceController.class,
            DialogKnowledgeNotesApiController.class, RestExceptionHandler.class})
    static class TestApplication {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(User.withUsername("operator")
                    .password("{noop}test-password")
                    .authorities("PAGE_DIALOGS", "PAGE_KNOWLEDGE_BASE").build());
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DialogReadService dialogReadService;

    @MockBean
    private DialogWorkspaceService dialogWorkspaceService;

    @MockBean
    private KnowledgeNoteLocationService knowledgeNoteLocationService;

    @Test
    void encodedOpaqueQueryReachesAllThreeServicesThroughEmbeddedTomcat() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        String cursor = "cursor/next &?";
        String encodedId = URLEncoder.encode(ticketId, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedCursor = URLEncoder.encode(cursor, StandardCharsets.UTF_8).replace("+", "%20");
        doReturn(ResponseEntity.ok(Map.of("ticketId", ticketId)))
                .when(dialogWorkspaceService)
                .workspace(eq(ticketId), eq(81L), eq("messages,sla"), eq(25), eq(cursor), any());
        when(knowledgeNoteLocationService.listDialogNotes(ticketId, "operator")).thenReturn(List.of());
        when(dialogReadService.loadParticipants(ticketId)).thenReturn(Map.of("ticketId", ticketId));

        HttpResponse<String> workspace = get("/api/dialog-ticket/workspace?ticketId=" + encodedId
                + "&channelId=81&include=messages%2Csla&limit=25&cursor=" + encodedCursor);
        assertThat(workspace.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(workspace.body()).get("ticketId").asText()).isEqualTo(ticketId);

        HttpResponse<String> notes = get("/api/dialog-ticket/notes?ticketId=" + encodedId);
        assertThat(notes.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(notes.body()).get("success").asBoolean()).isTrue();

        HttpResponse<String> participants = get("/api/dialog-ticket/participants?ticketId=" + encodedId);
        assertThat(participants.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(participants.body()).get("ticketId").asText()).isEqualTo(ticketId);

        verify(dialogWorkspaceService).workspace(eq(ticketId), eq(81L), eq("messages,sla"), eq(25),
                eq(cursor), argThat(auth -> "operator".equals(auth.getName())));
        verify(knowledgeNoteLocationService).listDialogNotes(ticketId, "operator");
        verify(dialogReadService).loadParticipants(ticketId);
    }

    private HttpResponse<String> get(String path) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                "operator:test-password".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Basic " + credentials).timeout(Duration.ofSeconds(10)).GET().build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}

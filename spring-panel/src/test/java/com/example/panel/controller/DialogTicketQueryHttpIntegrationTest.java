package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.RestExceptionHandler;
import com.example.panel.service.DialogAiOpsService;
import com.example.panel.service.DialogAuthorizationService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogReadService;
import com.example.panel.service.DialogWorkspaceService;
import com.example.panel.service.KnowledgeNoteLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        classes = DialogTicketQueryHttpIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.group.readiness.include=ping")
class DialogTicketQueryHttpIntegrationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    @EnableMethodSecurity
    @Import({DialogTicketQueryReadController.class, DialogWorkspaceController.class,
            DialogKnowledgeNotesApiController.class, DialogAiOpsController.class,
            DialogQuickActionsController.class, RestExceptionHandler.class})
    static class TestApplication {
        @Bean
        UserDetailsService testUsers() {
            return new InMemoryUserDetailsManager(User.withUsername("operator")
                    .password("{noop}test-password")
                    .authorities("PAGE_DIALOGS", "PAGE_KNOWLEDGE_BASE").build());
        }

        @Bean
        TestCsrfEndpoint testCsrfEndpoint() {
            return new TestCsrfEndpoint();
        }
    }

    @RestController
    static class TestCsrfEndpoint {
        @GetMapping("/test/csrf")
        public Map<String, String> csrf(CsrfToken token) {
            return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
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

    @MockBean
    private DialogAiOpsService dialogAiOpsService;

    @MockBean
    private DialogAuthorizationService dialogAuthorizationService;

    @MockBean
    private DialogQuickActionService dialogQuickActionService;

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

    @Test
    void aiQueryReadsRoundTripOpaqueIdThroughEmbeddedTomcat() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        String encodedId = URLEncoder.encode(ticketId, StandardCharsets.UTF_8).replace("+", "%20");
        Map<String, Object> payload = Map.of("success", true, "ticket_id", ticketId);
        when(dialogAiOpsService.loadSuggestions(ticketId, 5)).thenReturn(payload);
        when(dialogAiOpsService.loadControlState(ticketId)).thenReturn(payload);
        when(dialogAiOpsService.loadReview(ticketId)).thenReturn(payload);
        when(dialogAiOpsService.loadDecisionTrace(ticketId, 5)).thenReturn(payload);

        for (String endpoint : List.of("ai-suggestions", "ai-control", "ai-review", "ai-decision-trace")) {
            HttpResponse<String> response = get("/api/dialog-ticket/" + endpoint + "?ticketId=" + encodedId + "&limit=5");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(objectMapper.readTree(response.body()).get("ticket_id").asText()).isEqualTo(ticketId);
        }

        verify(dialogAiOpsService).loadSuggestions(ticketId, 5);
        verify(dialogAiOpsService).loadControlState(ticketId);
        verify(dialogAiOpsService).loadReview(ticketId);
        verify(dialogAiOpsService).loadDecisionTrace(ticketId, 5);
        for (String action : List.of("ai_suggestions", "ai_control_state", "ai_review", "ai_decision_trace")) {
            verify(dialogAuthorizationService).requirePermission(any(), eq("can_reply"), eq(action), eq(ticketId));
        }
    }

    @Test
    void aiQueryPostRequiresCsrfAndKeepsExactIdBodyAndOperatorOverHttp() throws Exception {
        String ticketId = " T/100?&%# Ю ";
        String encodedId = URLEncoder.encode(ticketId, StandardCharsets.UTF_8).replace("+", "%20");
        String path = "/api/dialog-ticket/ai-control?ticketId=" + encodedId;
        String body = "{\"ai_disabled\":true,\"autoReplyBlocked\":false,\"reason\":\" reason \"}";
        when(dialogAiOpsService.updateControlState(ticketId, true, false, " reason ", "operator"))
                .thenReturn(Map.of("success", true, "ticket_id", ticketId));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> preLoginTokenResponse = client.send(authenticatedRequest("/test/csrf").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(preLoginTokenResponse.statusCode()).isEqualTo(200);
        var preLoginToken = objectMapper.readTree(preLoginTokenResponse.body());
        HttpResponse<String> login = client.send(authenticatedRequest("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(preLoginToken.get("headerName").asText(), preLoginToken.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString("username=operator&password=test-password")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);

        HttpResponse<String> denied = client.send(authenticatedRequest(path).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);
        org.mockito.Mockito.verifyNoInteractions(dialogAiOpsService, dialogAuthorizationService);

        HttpResponse<String> tokenResponse = client.send(authenticatedRequest("/test/csrf").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        var token = objectMapper.readTree(tokenResponse.body());
        HttpResponse<String> accepted = client.send(authenticatedRequest(path)
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asText(), token.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(accepted.body()).get("ticket_id").asText()).isEqualTo(ticketId);
        verify(dialogAiOpsService).updateControlState(ticketId, true, false, " reason ", "operator");
        verify(dialogAuthorizationService).requirePermission(
                argThat(auth -> "operator".equals(auth.getName())), eq("can_reply"), eq("ai_control_update"), eq(ticketId));
    }

    @Test
    void quickActionPostAndParticipantDeleteKeepOpaqueQueryAndCsrfOverHttp() throws Exception {
        String ticketId = " T/100?&%#+ Ю ";
        String username = " agent/+? Ю ";
        String encodedId = URLEncoder.encode(ticketId, StandardCharsets.UTF_8).replace("+", "%20");
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8).replace("+", "%20");
        String replyPath = "/api/dialog-ticket/reply?ticketId=" + encodedId;
        String removePath = "/api/dialog-ticket/participants?ticketId=" + encodedId + "&username=" + encodedUsername;
        String body = "{\"message\":\" message \",\"replyToTelegramId\":123}";
        when(dialogQuickActionService.sendReply(ticketId, " message ", 123L, "operator"))
                .thenReturn(new DialogReplyService.DialogReplyResult(true, null, "now", 789L, "operator"));
        when(dialogQuickActionService.removeParticipant(ticketId, username, "operator"))
                .thenReturn(new DialogQuickActionService.DialogParticipantMutationResult(true, true, null, List.of()));
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        HttpResponse<String> preLoginResponse = client.send(authenticatedRequest("/test/csrf").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(preLoginResponse.statusCode()).isEqualTo(200);
        var preLoginToken = objectMapper.readTree(preLoginResponse.body());
        HttpResponse<String> login = client.send(authenticatedRequest("/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(preLoginToken.get("headerName").asText(), preLoginToken.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString("username=operator&password=test-password")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(302);

        for (String method : List.of("POST", "DELETE")) {
            HttpResponse<String> denied = client.send(authenticatedRequest("POST".equals(method) ? replyPath : removePath)
                    .header("Content-Type", "application/json")
                    .method(method, "POST".equals(method) ? HttpRequest.BodyPublishers.ofString(body)
                            : HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(denied.statusCode()).isEqualTo(403);
        }
        org.mockito.Mockito.verifyNoInteractions(dialogQuickActionService, dialogAuthorizationService);

        HttpResponse<String> tokenResponse = client.send(authenticatedRequest("/test/csrf").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        var token = objectMapper.readTree(tokenResponse.body());
        HttpResponse<String> reply = client.send(authenticatedRequest(replyPath)
                .header("Content-Type", "application/json")
                .header(token.get("headerName").asText(), token.get("token").asText())
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(reply.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(reply.body()).get("telegramMessageId").asLong()).isEqualTo(789L);

        HttpResponse<String> removed = client.send(authenticatedRequest(removePath)
                .header(token.get("headerName").asText(), token.get("token").asText()).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(removed.body()).get("changed").asBoolean()).isTrue();

        verify(dialogQuickActionService).sendReply(ticketId, " message ", 123L, "operator");
        verify(dialogQuickActionService).removeParticipant(ticketId, username, "operator");
        verify(dialogAuthorizationService).requirePermission(argThat(auth -> "operator".equals(auth.getName())),
                eq("can_reply"), eq("reply"), eq(ticketId));
        verify(dialogAuthorizationService).requirePermission(argThat(auth -> "operator".equals(auth.getName())),
                eq("can_assign"), eq("participants_remove"), eq(ticketId));
        verify(dialogAuthorizationService).logDialogAction("operator", ticketId, "reply", "success", "message_sent");
        verify(dialogAuthorizationService).logDialogAction("operator", ticketId, "participants_remove", "success", "participant_removed");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = authenticatedRequest(path).GET().build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder authenticatedRequest(String path) {
        String credentials = Base64.getEncoder().encodeToString(
                "operator:test-password".getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Basic " + credentials).timeout(Duration.ofSeconds(10));
    }
}

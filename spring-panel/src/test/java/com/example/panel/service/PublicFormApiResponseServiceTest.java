package com.example.panel.service;

import com.example.panel.model.ApiErrorResponse;
import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PublicFormApiResponseServiceTest {

    private final PublicFormApiResponseService service = new PublicFormApiResponseService();

    @Test
    void configPayloadIncludesChannelQuestionsAndRuntimeMetadata() {
        PublicFormConfig config = new PublicFormConfig(
                44L,
                "web-main",
                "Support Web",
                2,
                true,
                true,
                404,
                "Инструкция",
                30,
                List.of(new PublicFormQuestion("topic", "Тема", "select", 1, Map.of("required", true)))
        );

        Map<String, Object> payload = service.configPayload(
                config,
                6000,
                true,
                15,
                "ru",
                Map.of("enabled", false)
        );

        assertThat(payload).containsEntry("success", true);
        assertThat(payload).containsEntry("schemaVersion", 2);
        assertThat(payload).containsEntry("answersTotalMaxLength", 6000);
        assertThat(payload).containsEntry("uiLocale", "ru");
        @SuppressWarnings("unchecked")
        Map<String, Object> channel = (Map<String, Object>) payload.get("channel");
        assertThat(channel).containsEntry("publicId", "web-main");
        assertThat((List<?>) payload.get("questions")).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> firstQuestion = (Map<String, Object>) ((List<?>) payload.get("questions")).get(0);
        assertThat(firstQuestion).containsEntry("required", true);
    }

    @Test
    void sessionPayloadIncludesSessionIdentityAndMappedHistory() {
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-1",
                "web-1",
                44L,
                "web-main",
                "Анна",
                "+79991234567",
                "anna",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );
        ChatMessageDto message = new ChatMessageDto(
                "support",
                "Здравствуйте",
                null,
                "2026-01-01T10:20:00+03:00",
                "text",
                null,
                null,
                null,
                "preview",
                null,
                null,
                null
        );

        Map<String, Object> payload = service.sessionPayload(
                session,
                List.of(message),
                Map.of("enabled", true, "command", "/continue token-1")
        );

        assertThat(payload).containsEntry("success", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> sessionPayload = (Map<String, Object>) payload.get("session");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstMessage = (Map<String, Object>) ((List<?>) payload.get("messages")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> continuation = (Map<String, Object>) payload.get("continuation");
        assertThat(sessionPayload).containsEntry("clientContact", "+79991234567");
        assertThat(firstMessage).containsEntry("replyPreview", "preview");
        assertThat(continuation).containsEntry("command", "/continue token-1");
    }

    @Test
    void errorBuildsStructuredApiErrorWithPathAndTimestamp() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/public/forms/web-main/config");

        ResponseEntity<ApiErrorResponse> response = service.error(
                HttpStatus.NOT_FOUND,
                "Канал не найден",
                "CHANNEL_NOT_FOUND",
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("CHANNEL_NOT_FOUND");
        assertThat(response.getBody().path()).isEqualTo("/api/public/forms/web-main/config");
        assertThat(response.getBody().timestamp()).isNotNull();
    }
}

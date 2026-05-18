package com.example.panel.service;

import com.example.panel.model.ApiErrorResponse;
import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PublicFormApiResponseService {

    public ResponseEntity<ApiErrorResponse> error(HttpStatus status,
                                                  String message,
                                                  String errorCode,
                                                  HttpServletRequest request) {
        String safeMessage = message != null && !message.isBlank() ? message : status.getReasonPhrase();
        ApiErrorResponse body = new ApiErrorResponse(
                false,
                safeMessage,
                errorCode,
                request != null ? request.getRequestURI() : null,
                OffsetDateTime.now()
        );
        return ResponseEntity.status(status).body(body);
    }

    public Map<String, Object> configPayload(PublicFormConfig config,
                                             int answersTotalMaxLength,
                                             boolean sessionPollingEnabled,
                                             int sessionPollingIntervalSeconds,
                                             String uiLocale,
                                             Map<String, Object> continuation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("channel", Map.of(
                "id", config.channelId(),
                "publicId", config.channelPublicId(),
                "name", config.channelName()
        ));
        payload.put("schemaVersion", config.schemaVersion());
        payload.put("captchaEnabled", config.captchaEnabled());
        payload.put("answersTotalMaxLength", answersTotalMaxLength);
        payload.put("sessionPollingEnabled", sessionPollingEnabled);
        payload.put("sessionPollingIntervalSeconds", sessionPollingIntervalSeconds);
        payload.put("uiLocale", uiLocale);
        payload.put("disabledStatus", config.disabledStatus());
        payload.put("successInstruction", config.successInstruction());
        payload.put("responseEtaMinutes", config.responseEtaMinutes());
        payload.put("continuation", continuation);
        payload.put("questions", config.questions().stream().map(this::questionToMap).toList());
        return payload;
    }

    public Map<String, Object> createdSessionPayload(PublicFormSessionDto session,
                                                     Map<String, Object> continuation) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("token", session.token());
        response.put("ticketId", session.ticketId());
        response.put("createdAt", Optional.ofNullable(session.createdAt()).map(OffsetDateTime::toString).orElse(null));
        response.put("channel", Map.of(
                "id", session.channelId(),
                "publicId", session.channelPublicId()
        ));
        response.put("continuation", continuation);
        return response;
    }

    public Map<String, Object> sessionPayload(PublicFormSessionDto session,
                                              List<ChatMessageDto> history,
                                              Map<String, Object> continuation) {
        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> sessionPayload = new LinkedHashMap<>();
        sessionPayload.put("token", session.token());
        sessionPayload.put("ticketId", session.ticketId());
        sessionPayload.put("clientName", session.clientName());
        sessionPayload.put("clientContact", session.clientContact());
        sessionPayload.put("username", session.username());
        sessionPayload.put("createdAt", Optional.ofNullable(session.createdAt()).map(OffsetDateTime::toString).orElse(null));
        response.put("success", true);
        response.put("session", sessionPayload);
        response.put("messages", history.stream().map(this::messageToMap).toList());
        response.put("continuation", continuation);
        return response;
    }

    private Map<String, Object> questionToMap(PublicFormQuestion question) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", question.id());
        map.put("text", question.text());
        map.put("type", question.type());
        map.put("order", question.order());
        map.putAll(question.metadata());
        return map;
    }

    private Map<String, Object> messageToMap(ChatMessageDto message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sender", message.sender());
        map.put("message", message.message());
        map.put("timestamp", message.timestamp());
        map.put("messageType", message.messageType());
        map.put("attachment", message.attachment());
        map.put("replyPreview", message.replyPreview());
        return map;
    }
}

package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.service.DialogService;
import com.example.panel.service.PublicFormService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/forms")
@Validated
public class PublicFormApiController {

    private final PublicFormService publicFormService;
    private final DialogService dialogService;

    public PublicFormApiController(PublicFormService publicFormService, DialogService dialogService) {
        this.publicFormService = publicFormService;
        this.dialogService = dialogService;
    }

    @GetMapping("/{channelId}/config")
    public ResponseEntity<Map<String, Object>> config(@PathVariable String channelId) {
        Optional<PublicFormConfig> config = publicFormService.loadConfig(channelId);
        if (config.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Канал не найден"));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("channel", Map.of(
                "id", config.get().channelId(),
                "publicId", config.get().channelPublicId(),
                "name", config.get().channelName()
        ));
        payload.put("questions", config.get().questions().stream().map(this::questionToMap).toList());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{channelId}/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@PathVariable String channelId,
                                                             @Valid @RequestBody PublicFormRequest request) {
        try {
            PublicFormSessionDto session = publicFormService.createSession(channelId, request.toSubmission());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("token", session.token());
            response.put("ticketId", session.ticketId());
            response.put("createdAt", Optional.ofNullable(session.createdAt()).map(OffsetDateTime::toString).orElse(null));
            response.put("channel", Map.of(
                    "id", session.channelId(),
                    "publicId", session.channelPublicId()
            ));
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/{channelId}/sessions/{token}")
    public ResponseEntity<Map<String, Object>> session(@PathVariable String channelId,
                                                       @PathVariable String token,
                                                       @RequestParam(value = "channel", required = false) Long channelFilter) {
        Optional<PublicFormSessionDto> session = publicFormService.findSession(channelId, token);
        if (session.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден"));
        }
        List<ChatMessageDto> history = dialogService.loadHistory(session.get().ticketId(), channelFilter);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("session", Map.of(
                "token", session.get().token(),
                "ticketId", session.get().ticketId(),
                "clientName", session.get().clientName(),
                "clientContact", session.get().clientContact(),
                "username", session.get().username(),
                "createdAt", Optional.ofNullable(session.get().createdAt()).map(OffsetDateTime::toString).orElse(null)
        ));
        response.put("messages", history.stream().map(this::messageToMap).toList());
        return ResponseEntity.ok(response);
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

    public record PublicFormRequest(@NotBlank String message,
                                    String clientName,
                                    String clientContact,
                                    String username,
                                    Map<String, String> answers) {
        public PublicFormSubmission toSubmission() {
            return new PublicFormSubmission(message, clientName, clientContact, username, answers);
        }
    }
}
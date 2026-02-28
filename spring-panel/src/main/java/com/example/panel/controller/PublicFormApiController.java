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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/forms")
@Validated
public class PublicFormApiController {

    private static final Logger log = LoggerFactory.getLogger(PublicFormApiController.class);

    private final PublicFormService publicFormService;
    private final DialogService dialogService;

    public PublicFormApiController(PublicFormService publicFormService, DialogService dialogService) {
        this.publicFormService = publicFormService;
        this.dialogService = dialogService;
    }

    @GetMapping("/{channelId}/config")
    public ResponseEntity<Map<String, Object>> config(@PathVariable String channelId) {
        Optional<PublicFormConfig> config = publicFormService.loadConfigRaw(channelId);
        if (config.isEmpty()) {
            log.warn("Public form config not found for channel {}", channelId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Канал не найден", "errorCode", "CHANNEL_NOT_FOUND"));
        }
        if (!config.get().enabled()) {
            return ResponseEntity.status(resolveDisabledStatus(config.get()))
                    .body(Map.of("success", false, "error", "Форма канала отключена", "errorCode", "FORM_DISABLED"));
        }
        log.info("Public form config loaded for channel {} (id={}) with {} questions", channelId,
                config.get().channelId(), config.get().questions().size());
        publicFormService.recordConfigView(config.get().channelId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("channel", Map.of(
                "id", config.get().channelId(),
                "publicId", config.get().channelPublicId(),
                "name", config.get().channelName()
        ));
        payload.put("schemaVersion", config.get().schemaVersion());
        payload.put("captchaEnabled", config.get().captchaEnabled());
        payload.put("answersTotalMaxLength", publicFormService.resolveAnswersPayloadMaxLength());
        payload.put("sessionPollingEnabled", publicFormService.isSessionPollingEnabled());
        payload.put("sessionPollingIntervalSeconds", publicFormService.resolveSessionPollingIntervalSeconds());
        payload.put("uiLocale", publicFormService.resolveUiLocale());
        payload.put("disabledStatus", config.get().disabledStatus());
        payload.put("questions", config.get().questions().stream().map(this::questionToMap).toList());
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/{channelId}/sessions")
    public ResponseEntity<Map<String, Object>> createSession(@PathVariable String channelId,
                                                             @Valid @RequestBody PublicFormRequest request,
                                                             HttpServletRequest servletRequest) {
        Optional<PublicFormConfig> config = publicFormService.loadConfigRaw(channelId);
        if (config.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Канал не найден", "errorCode", "CHANNEL_NOT_FOUND"));
        }
        if (!config.get().enabled()) {
            return ResponseEntity.status(resolveDisabledStatus(config.get()))
                    .body(Map.of("success", false, "error", "Форма канала отключена", "errorCode", "FORM_DISABLED"));
        }
        try {
            PublicFormSessionDto session = publicFormService.createSession(channelId, request.toSubmission(), resolveRequesterKey(servletRequest));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("token", session.token());
            response.put("ticketId", session.ticketId());
            response.put("createdAt", Optional.ofNullable(session.createdAt()).map(OffsetDateTime::toString).orElse(null));
            response.put("channel", Map.of(
                    "id", session.channelId(),
                    "publicId", session.channelPublicId()
            ));
            publicFormService.recordSubmitSuccess(session.channelId());
            log.info("Public form session created for channel {} (ticketId={}, token set={})", channelId,
                    session.ticketId(), session.token() != null && !session.token().isBlank());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            publicFormService.recordSubmitError(config.get().channelId(), ex.getMessage());
            log.warn("Failed to create public form session for channel {}: {}", channelId, ex.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", ex.getMessage(), "errorCode", resolveErrorCode(ex.getMessage())));
        } catch (Exception ex) {
            publicFormService.recordSubmitError(config.get().channelId(), "internal_error");
            log.error("Unexpected error during public form submit for channel {}", channelId, ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Внутренняя ошибка сервера", "errorCode", "INTERNAL_ERROR"));
        }
    }

    @GetMapping("/{channelId}/sessions/{token}")
    public ResponseEntity<Map<String, Object>> session(@PathVariable String channelId,
                                                       @PathVariable String token,
                                                       @RequestParam(value = "channel", required = false) Long channelFilter) {
        Optional<Long> resolvedChannelId = publicFormService.resolveChannelId(channelId);
        Optional<PublicFormSessionDto> session = publicFormService.findSession(channelId, token);
        resolvedChannelId.ifPresent(id -> publicFormService.recordSessionLookup(id, session.isPresent()));
        if (session.isEmpty()) {
            log.warn("Public form session not found for channel {}, token {}", channelId, maskToken(token));
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "error", "Диалог не найден", "errorCode", "SESSION_NOT_FOUND"));
        }
        List<ChatMessageDto> history = dialogService.loadHistory(session.get().ticketId(), channelFilter);
        log.info("Public form session {} for channel {} loaded with {} history messages",
                maskToken(token), channelId, history.size());
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



    private HttpStatus resolveDisabledStatus(PublicFormConfig config) {
        HttpStatus status = HttpStatus.resolve(config.disabledStatus());
        return status != null ? status : HttpStatus.NOT_FOUND;
    }

    private String resolveErrorCode(String message) {
        String normalized = Optional.ofNullable(message).orElse("").trim().toLowerCase();
        if (normalized.contains("слишком много запросов") || normalized.contains("rate limit") || normalized.contains("too many requests")) {
            return "RATE_LIMITED";
        }
        if (normalized.contains("captcha")) {
            return "CAPTCHA_FAILED";
        }
        if (normalized.contains("заполните поле") || normalized.contains("подтвердите поле") || normalized.contains("required")) {
            return "VALIDATION_REQUIRED";
        }
        if (normalized.contains("корректный email") || normalized.contains("invalid email")) {
            return "VALIDATION_EMAIL";
        }
        if (normalized.contains("корректный телефон") || normalized.contains("invalid phone")) {
            return "VALIDATION_PHONE";
        }
        if (normalized.contains("превышает лимит") || normalized.contains("слишком длин") || normalized.contains("max")) {
            return "VALIDATION_MAX_LENGTH";
        }
        if (normalized.contains("должно содержать минимум") || normalized.contains("minimum")) {
            return "VALIDATION_MIN_LENGTH";
        }
        if (normalized.contains("idempotency") || normalized.contains("requestid")) {
            return "IDEMPOTENCY_CONFLICT";
        }
        return "VALIDATION_ERROR";
    }

    private String resolveRequesterKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String requesterIp;
        if (forwarded != null && !forwarded.isBlank()) {
            requesterIp = forwarded.split(",")[0].trim();
        } else {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                requesterIp = realIp.trim();
            } else {
                requesterIp = request.getRemoteAddr();
            }
        }
        String fingerprint = request.getHeader("X-Public-Form-Fingerprint");
        return publicFormService.buildRequesterKey(requesterIp, fingerprint);
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "[empty]";
        }
        String normalized = token.trim();
        if (normalized.length() <= 8) {
            return "tok:" + HexFormat.of().formatHex(digestUtf8(normalized), 0, 4);
        }
        return normalized.substring(0, 4) + "…" + normalized.substring(normalized.length() - 4);
    }

    private byte[] digestUtf8(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(Optional.ofNullable(value).orElse("").getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            byte[] fallback = Optional.ofNullable(value).orElse("").getBytes(StandardCharsets.UTF_8);
            byte[] padded = new byte[4];
            System.arraycopy(fallback, 0, padded, 0, Math.min(fallback.length, 4));
            return padded;
        }
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
                                    String captchaToken,
                                    Map<String, String> answers,
                                    String requestId) {
        public PublicFormSubmission toSubmission() {
            return new PublicFormSubmission(message, clientName, clientContact, username, captchaToken, answers, requestId);
        }
    }
}

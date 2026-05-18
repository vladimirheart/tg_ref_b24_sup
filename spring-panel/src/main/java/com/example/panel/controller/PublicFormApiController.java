package com.example.panel.controller;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.service.DialogConversationReadService;
import com.example.panel.service.PublicFormApiResponseService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/forms")
@Validated
public class PublicFormApiController {

    private static final Logger log = LoggerFactory.getLogger(PublicFormApiController.class);

    private final PublicFormService publicFormService;
    private final DialogConversationReadService dialogConversationReadService;
    private final PublicFormApiResponseService publicFormApiResponseService;

    public PublicFormApiController(PublicFormService publicFormService,
                                   DialogConversationReadService dialogConversationReadService,
                                   PublicFormApiResponseService publicFormApiResponseService) {
        this.publicFormService = publicFormService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.publicFormApiResponseService = publicFormApiResponseService;
    }

    @GetMapping("/{channelId}/config")
    public ResponseEntity<?> config(@PathVariable String channelId, HttpServletRequest request) {
        Optional<PublicFormConfig> config = publicFormService.loadConfigRaw(channelId);
        if (config.isEmpty()) {
            log.warn("Public form config not found for channel {}", channelId);
            return publicFormApiResponseService.error(HttpStatus.NOT_FOUND, "Канал не найден", "CHANNEL_NOT_FOUND", request);
        }
        if (!config.get().enabled()) {
            return publicFormApiResponseService.error(
                    resolveDisabledStatus(config.get()),
                    "Форма канала отключена",
                    "FORM_DISABLED",
                    request
            );
        }
        log.info("Public form config loaded for channel {} (id={}) with {} questions", channelId,
                config.get().channelId(), config.get().questions().size());
        publicFormService.recordConfigView(config.get().channelId());
        return ResponseEntity.ok(publicFormApiResponseService.configPayload(
                config.get(),
                publicFormService.resolveAnswersPayloadMaxLength(),
                publicFormService.isSessionPollingEnabled(),
                publicFormService.resolveSessionPollingIntervalSeconds(),
                publicFormService.resolveUiLocale(),
                publicFormService.buildContinuationOptions(channelId, null)
        ));
    }

    @PostMapping("/{channelId}/sessions")
    public ResponseEntity<?> createSession(@PathVariable String channelId,
                                                             @Valid @RequestBody PublicFormRequest requestBody,
                                                             HttpServletRequest servletRequest) {
        Optional<PublicFormConfig> config = publicFormService.loadConfigRaw(channelId);
        if (config.isEmpty()) {
            return publicFormApiResponseService.error(HttpStatus.NOT_FOUND, "Канал не найден", "CHANNEL_NOT_FOUND", servletRequest);
        }
        if (!config.get().enabled()) {
            return publicFormApiResponseService.error(
                    resolveDisabledStatus(config.get()),
                    "Форма канала отключена",
                    "FORM_DISABLED",
                    servletRequest
            );
        }
        try {
            PublicFormSessionDto session = publicFormService.createSession(channelId, requestBody.toSubmission(), resolveRequesterKey(servletRequest));
            publicFormService.recordSubmitSuccess(session.channelId());
            log.info("Public form session created for channel {} (ticketId={}, token set={})", channelId,
                    session.ticketId(), session.token() != null && !session.token().isBlank());
            return ResponseEntity.status(HttpStatus.CREATED).body(publicFormApiResponseService.createdSessionPayload(
                    session,
                    publicFormService.buildContinuationOptions(channelId, session.token())
            ));
        } catch (IllegalArgumentException ex) {
            publicFormService.recordSubmitError(config.get().channelId(), ex.getMessage());
            log.warn("Failed to create public form session for channel {}: {}", channelId, ex.getMessage());
            return publicFormApiResponseService.error(
                    HttpStatus.BAD_REQUEST,
                    ex.getMessage(),
                    resolveErrorCode(ex.getMessage()),
                    servletRequest
            );
        } catch (Exception ex) {
            publicFormService.recordSubmitError(config.get().channelId(), "internal_error");
            log.error("Unexpected error during public form submit for channel {}", channelId, ex);
            return publicFormApiResponseService.error(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Внутренняя ошибка сервера",
                    "INTERNAL_ERROR",
                    servletRequest
            );
        }
    }

    @GetMapping("/{channelId}/sessions/{token}")
    public ResponseEntity<?> session(@PathVariable String channelId,
                                                       @PathVariable String token,
                                                       @RequestParam(value = "channel", required = false) Long channelFilter,
                                                       HttpServletRequest request) {
        Optional<Long> resolvedChannelId = publicFormService.resolveChannelId(channelId);
        Optional<PublicFormSessionDto> session = publicFormService.findSession(channelId, token);
        resolvedChannelId.ifPresent(id -> publicFormService.recordSessionLookup(id, session.isPresent()));
        if (session.isEmpty()) {
            log.warn("Public form session not found for channel {}, token {}", channelId, maskToken(token));
            return publicFormApiResponseService.error(HttpStatus.NOT_FOUND, "Диалог не найден", "SESSION_NOT_FOUND", request);
        }
        List<ChatMessageDto> history = dialogConversationReadService.loadHistory(session.get().ticketId(), channelFilter);
        log.info("Public form session {} for channel {} loaded with {} history messages",
                maskToken(token), channelId, history.size());
        return ResponseEntity.ok(publicFormApiResponseService.sessionPayload(
                session.get(),
                history,
                publicFormService.buildContinuationOptions(channelId, session.get().token())
        ));
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

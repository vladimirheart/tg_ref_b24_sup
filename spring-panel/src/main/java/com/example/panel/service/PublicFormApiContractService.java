package com.example.panel.service;

import com.example.panel.model.publicform.PublicFormConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PublicFormApiContractService {

    public HttpStatus resolveDisabledStatus(PublicFormConfig config) {
        HttpStatus status = HttpStatus.resolve(config.disabledStatus());
        return status != null ? status : HttpStatus.NOT_FOUND;
    }

    public String resolveErrorCode(String message) {
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

    public RequesterContext resolveRequesterContext(HttpServletRequest request) {
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
        return new RequesterContext(requesterIp, fingerprint);
    }

    public String maskToken(String token) {
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

    public record RequesterContext(String requesterIp, String fingerprint) {
    }
}

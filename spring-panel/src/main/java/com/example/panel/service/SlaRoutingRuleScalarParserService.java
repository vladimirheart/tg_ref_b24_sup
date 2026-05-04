package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class SlaRoutingRuleScalarParserService {

    public Boolean parseOptionalBoolean(Object rawValue) {
        if (rawValue instanceof Boolean bool) return bool;
        if (rawValue instanceof Number number) return number.intValue() != 0;
        String raw = trimToNull(String.valueOf(rawValue));
        if (raw == null) return null;
        String normalized = raw.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) return true;
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) return false;
        return null;
    }

    public Integer parseOptionalNonNegativeInt(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof Number number) return number.intValue() < 0 ? null : number.intValue();
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return parsed < 0 ? null : parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public Long parseOptionalLong(Object rawValue) {
        if (rawValue == null) return null;
        if (rawValue instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(rawValue).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public int parsePriority(Object rawValue) {
        if (rawValue == null) return 0;
        if (rawValue instanceof Number number) return Math.max(Math.min(number.intValue(), 100), -100);
        try {
            int parsed = Integer.parseInt(String.valueOf(rawValue).trim());
            return Math.max(Math.min(parsed, 100), -100);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public Instant parseUtcInstant(String rawValue) {
        String normalized = trimToNull(rawValue);
        if (normalized == null) return null;
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(normalized).withOffsetSameInstant(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    public String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) ? null : trimmed;
    }
}

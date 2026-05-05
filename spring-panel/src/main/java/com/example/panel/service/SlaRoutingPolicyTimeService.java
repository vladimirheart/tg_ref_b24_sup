package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Service
public class SlaRoutingPolicyTimeService {

    public Long resolveMinutesLeft(String createdAt, int targetMinutes, long nowMs) {
        Instant created = parseInstant(createdAt);
        if (created == null) return null;
        return Math.floorDiv(created.toEpochMilli() + targetMinutes * 60_000L - nowMs, 60_000L);
    }

    public String normalizeUtcTimestamp(String rawValue) {
        Instant parsed = parseInstant(rawValue);
        return parsed == null ? null : parsed.toString();
    }

    public Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return Instant.parse(rawValue.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(rawValue.trim()).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }
}

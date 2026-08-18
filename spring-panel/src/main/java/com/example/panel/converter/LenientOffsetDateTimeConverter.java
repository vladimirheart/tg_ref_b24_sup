package com.example.panel.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;

@Converter
public class LenientOffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, String> {

    private static final List<DateTimeFormatter> FALLBACK_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    );

    @Override
    public String convertToDatabaseColumn(OffsetDateTime attribute) {
        return attribute != null ? attribute.toString() : null;
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String trimmed = dbData.trim();
        if (trimmed.matches("^\\d+$")) {
            try {
                long epoch = Long.parseLong(trimmed);
                if (trimmed.length() <= 10) {
                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneOffset.UTC);
                }
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        // PostgreSQL's JDBC driver commonly renders TIMESTAMPTZ values as
        // "yyyy-MM-dd HH:mm:ss[.fraction]+HH". ISO_OFFSET_DATE_TIME accepts
        // that representation once the date/time separator is normalized.
        try {
            return OffsetDateTime.parse(trimmed.replace(' ', 'T'));
        } catch (Exception ignored) {
            // Try the legacy/local timestamp formats below.
        }

        for (DateTimeFormatter formatter : FALLBACK_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(trimmed, formatter);
                }
                LocalDateTime localDateTime = LocalDateTime.parse(trimmed, formatter);
                return localDateTime.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
                // try next formatter
            }
        }

        return null;
    }
}

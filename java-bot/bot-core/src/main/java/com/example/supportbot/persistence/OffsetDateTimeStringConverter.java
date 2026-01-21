package com.example.supportbot.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class OffsetDateTimeStringConverter implements AttributeConverter<OffsetDateTime, String> {

    private static final Logger log = LoggerFactory.getLogger(OffsetDateTimeStringConverter.class);
    private static final DateTimeFormatter LEGACY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public String convertToDatabaseColumn(OffsetDateTime attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Override
    public OffsetDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dbData, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // try legacy and instant formats
        }
        try {
            Instant instant = Instant.parse(dbData);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try legacy format
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(dbData, LEGACY_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            log.warn("Failed to parse OffsetDateTime value '{}'", dbData, ex);
            return null;
        }
    }
}

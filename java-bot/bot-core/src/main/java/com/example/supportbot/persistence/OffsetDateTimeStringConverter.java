package com.example.supportbot.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class OffsetDateTimeStringConverter implements AttributeConverter<OffsetDateTime, String> {

    private static final Logger log = LoggerFactory.getLogger(OffsetDateTimeStringConverter.class);
    private static final DateTimeFormatter LEGACY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SPACE_FRACTIONAL_DATE_TIME_FORMATTER =
        new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter DATE_ONLY_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        String trimmed = dbData.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                long epochValue = Long.parseLong(trimmed);
                Instant instant = epochValue > 100000000000L
                    ? Instant.ofEpochMilli(epochValue)
                    : Instant.ofEpochSecond(epochValue);
                return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
            } catch (NumberFormatException ex) {
                log.warn("Failed to parse epoch OffsetDateTime value '{}'", dbData, ex);
            }
        }
        try {
            return OffsetDateTime.parse(trimmed, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // try legacy and instant formats
        }
        try {
            Instant instant = Instant.parse(trimmed);
            return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try legacy format
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try legacy format
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, LEGACY_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try date-time format without milliseconds
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, DATE_TIME_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try date-time format with optional fractional seconds
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(trimmed, SPACE_FRACTIONAL_DATE_TIME_FORMATTER);
            return localDateTime.atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // try date-only format
        }
        try {
            LocalDate localDate = LocalDate.parse(trimmed, DATE_ONLY_FORMATTER);
            return localDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            log.warn("Failed to parse OffsetDateTime value '{}'", dbData, ex);
            return null;
        }
    }
}

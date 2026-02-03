package com.example.supportbot.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(autoApply = true)
public class LocalDateStringConverter implements AttributeConverter<LocalDate, String> {

    private static final Logger log = LoggerFactory.getLogger(LocalDateStringConverter.class);

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
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
                return instant.atOffset(ZoneOffset.UTC).toLocalDate();
            } catch (NumberFormatException ex) {
                log.warn("Failed to parse epoch LocalDate value '{}'", dbData, ex);
            }
        }
        try {
            return LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            log.warn("Failed to parse LocalDate value '{}'", dbData, ex);
            return null;
        }
    }
}

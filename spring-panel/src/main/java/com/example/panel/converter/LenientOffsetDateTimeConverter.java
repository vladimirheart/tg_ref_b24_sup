package com.example.panel.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Converter
public class LenientOffsetDateTimeConverter implements AttributeConverter<OffsetDateTime, String> {

    private static final List<DateTimeFormatter> FALLBACK_FORMATTERS = List.of(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
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

        for (DateTimeFormatter formatter : FALLBACK_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                    return OffsetDateTime.parse(dbData, formatter);
                }
                LocalDateTime localDateTime = LocalDateTime.parse(dbData, formatter);
                return localDateTime.atOffset(ZoneOffset.UTC);
            } catch (Exception ignored) {
                // try next formatter
            }
        }

        return null;
    }
}

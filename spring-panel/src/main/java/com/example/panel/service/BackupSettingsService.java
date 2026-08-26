package com.example.panel.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackupSettingsService {

    private static final String FILE_NAME = "backup.properties";

    private static final String DESTINATION_KEY = "IGUANA_BACKUP_DESTINATION_DIR";
    private static final String EXTERNAL_FAILURE_DOMAIN_KEY = "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN";
    private static final String POSTGRES_RETENTION_KEY = "IGUANA_BACKUP_RETENTION_DAYS";
    private static final String MINIO_RETENTION_KEY = "IGUANA_MINIO_BACKUP_RETENTION_DAYS";
    private static final String ARCHIVE_FORMAT_KEY = "IGUANA_BACKUP_ARCHIVE_FORMAT";
    private static final String MANUAL_MODE_KEY = "IGUANA_BACKUP_MANUAL_MODE";
    private static final String CUSTOM_COMPONENTS_KEY = "IGUANA_BACKUP_CUSTOM_COMPONENTS";
    private static final String RESTORE_COMPONENTS_KEY = "IGUANA_BACKUP_RESTORE_COMPONENTS";
    private static final String CRITICAL_ENABLED_KEY = "IGUANA_BACKUP_CRITICAL_ENABLED";
    private static final String CRITICAL_FREQUENCY_KEY = "IGUANA_BACKUP_CRITICAL_FREQUENCY";
    private static final String CRITICAL_TIME_KEY = "IGUANA_BACKUP_CRITICAL_TIME";
    private static final String CRITICAL_WEEKDAY_KEY = "IGUANA_BACKUP_CRITICAL_WEEKDAY";
    private static final String FULL_ENABLED_KEY = "IGUANA_BACKUP_FULL_ENABLED";
    private static final String FULL_FREQUENCY_KEY = "IGUANA_BACKUP_FULL_FREQUENCY";
    private static final String FULL_TIME_KEY = "IGUANA_BACKUP_FULL_TIME";
    private static final String FULL_WEEKDAY_KEY = "IGUANA_BACKUP_FULL_WEEKDAY";

    private static final int DEFAULT_POSTGRES_RETENTION_DAYS = 30;
    private static final int DEFAULT_MINIO_RETENTION_DAYS = 14;
    private static final int MAX_RETENTION_DAYS = 3650;
    private static final int MAX_DESTINATION_LENGTH = 2048;

    private static final Set<String> ALLOWED_MODES = Set.of("critical", "full", "custom");
    private static final Set<String> ALLOWED_FREQUENCIES = Set.of("daily", "weekly");
    private static final Set<String> ALLOWED_WEEKDAYS =
            Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");
    private static final Set<String> ALLOWED_COMPONENTS = Set.of(
            "postgres",
            "minio",
            "shared-config",
            "templates",
            "static-js",
            "static-css"
    );
    private static final List<String> DEFAULT_CRITICAL_COMPONENTS =
            List.of("postgres", "minio", "shared-config");
    private static final List<String> DEFAULT_RESTORE_COMPONENTS =
            List.of("postgres", "minio", "shared-config");

    private static final List<String> ORDERED_KEYS = List.of(
            DESTINATION_KEY,
            EXTERNAL_FAILURE_DOMAIN_KEY,
            POSTGRES_RETENTION_KEY,
            MINIO_RETENTION_KEY,
            ARCHIVE_FORMAT_KEY,
            MANUAL_MODE_KEY,
            CUSTOM_COMPONENTS_KEY,
            RESTORE_COMPONENTS_KEY,
            CRITICAL_ENABLED_KEY,
            CRITICAL_FREQUENCY_KEY,
            CRITICAL_TIME_KEY,
            CRITICAL_WEEKDAY_KEY,
            FULL_ENABLED_KEY,
            FULL_FREQUENCY_KEY,
            FULL_TIME_KEY,
            FULL_WEEKDAY_KEY
    );

    private final SharedConfigService sharedConfigService;

    public BackupSettingsService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public Map<String, Object> load() {
        Map<String, String> values = readValues();

        String destination = normalizeDestination(values.getOrDefault(DESTINATION_KEY, ""));
        boolean externalFailureDomain = parseBoolean(
                values.get(EXTERNAL_FAILURE_DOMAIN_KEY),
                false
        );
        int postgresRetentionDays = parseInteger(
                values.get(POSTGRES_RETENTION_KEY),
                DEFAULT_POSTGRES_RETENTION_DAYS,
                POSTGRES_RETENTION_KEY
        );
        int minioRetentionDays = parseInteger(
                values.get(MINIO_RETENTION_KEY),
                DEFAULT_MINIO_RETENTION_DAYS,
                MINIO_RETENTION_KEY
        );

        String manualMode = normalizeChoice(
                values.get(MANUAL_MODE_KEY),
                "critical",
                ALLOWED_MODES,
                MANUAL_MODE_KEY
        );
        List<String> customComponents = normalizeComponents(
                values.get(CUSTOM_COMPONENTS_KEY),
                DEFAULT_CRITICAL_COMPONENTS
        );
        List<String> restoreComponents = normalizeComponents(
                values.get(RESTORE_COMPONENTS_KEY),
                DEFAULT_RESTORE_COMPONENTS
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("destination_path", destination);
        result.put("external_failure_domain", externalFailureDomain);
        result.put("postgres_retention_days", postgresRetentionDays);
        result.put("minio_retention_days", minioRetentionDays);
        result.put("archive_format", "tar.gz");
        result.put("manual_mode", manualMode);
        result.put("custom_components", customComponents);
        result.put("restore_components", restoreComponents);
        result.put("critical_enabled", parseBoolean(values.get(CRITICAL_ENABLED_KEY), false));
        result.put("critical_frequency", normalizeFrequency(values.get(CRITICAL_FREQUENCY_KEY), "daily"));
        result.put("critical_time", normalizeTime(values.get(CRITICAL_TIME_KEY), "02:00", CRITICAL_TIME_KEY));
        result.put("critical_weekday", normalizeWeekday(values.get(CRITICAL_WEEKDAY_KEY), "MON"));
        result.put("full_enabled", parseBoolean(values.get(FULL_ENABLED_KEY), false));
        result.put("full_frequency", normalizeFrequency(values.get(FULL_FREQUENCY_KEY), "weekly"));
        result.put("full_time", normalizeTime(values.get(FULL_TIME_KEY), "03:00", FULL_TIME_KEY));
        result.put("full_weekday", normalizeWeekday(values.get(FULL_WEEKDAY_KEY), "SUN"));
        result.put("configured", StringUtils.hasText(destination));
        return result;
    }

    public Map<String, Object> save(Map<String, Object> payload) {
        Map<String, Object> source = payload != null ? payload : Map.of();

        String destination = normalizeDestination(asString(source.get("destination_path")));
        if (!StringUtils.hasText(destination)) {
            throw new IllegalArgumentException("Укажите путь backup-хранилища.");
        }

        boolean externalFailureDomain = parseBoolean(
                source.get("external_failure_domain"),
                false
        );
        int postgresRetentionDays = parseInteger(
                source.get("postgres_retention_days"),
                DEFAULT_POSTGRES_RETENTION_DAYS,
                "postgres_retention_days"
        );
        int minioRetentionDays = parseInteger(
                source.get("minio_retention_days"),
                DEFAULT_MINIO_RETENTION_DAYS,
                "minio_retention_days"
        );

        String manualMode = normalizeChoice(
                source.get("manual_mode"),
                "critical",
                ALLOWED_MODES,
                "manual_mode"
        );
        List<String> customComponents = normalizeComponents(
                source.get("custom_components"),
                DEFAULT_CRITICAL_COMPONENTS
        );
        List<String> restoreComponents = normalizeComponents(
                source.get("restore_components"),
                DEFAULT_RESTORE_COMPONENTS
        );

        boolean criticalEnabled = parseBoolean(source.get("critical_enabled"), false);
        String criticalFrequency = normalizeFrequency(source.get("critical_frequency"), "daily");
        String criticalTime = normalizeTime(source.get("critical_time"), "02:00", "critical_time");
        String criticalWeekday = normalizeWeekday(source.get("critical_weekday"), "MON");

        boolean fullEnabled = parseBoolean(source.get("full_enabled"), false);
        String fullFrequency = normalizeFrequency(source.get("full_frequency"), "weekly");
        String fullTime = normalizeTime(source.get("full_time"), "03:00", "full_time");
        String fullWeekday = normalizeWeekday(source.get("full_weekday"), "SUN");

        Map<String, String> values = new LinkedHashMap<>();
        values.put(DESTINATION_KEY, destination);
        values.put(EXTERNAL_FAILURE_DOMAIN_KEY, Boolean.toString(externalFailureDomain));
        values.put(POSTGRES_RETENTION_KEY, Integer.toString(postgresRetentionDays));
        values.put(MINIO_RETENTION_KEY, Integer.toString(minioRetentionDays));
        values.put(ARCHIVE_FORMAT_KEY, "tar.gz");
        values.put(MANUAL_MODE_KEY, manualMode);
        values.put(CUSTOM_COMPONENTS_KEY, String.join(",", customComponents));
        values.put(RESTORE_COMPONENTS_KEY, String.join(",", restoreComponents));
        values.put(CRITICAL_ENABLED_KEY, Boolean.toString(criticalEnabled));
        values.put(CRITICAL_FREQUENCY_KEY, criticalFrequency);
        values.put(CRITICAL_TIME_KEY, criticalTime);
        values.put(CRITICAL_WEEKDAY_KEY, criticalWeekday);
        values.put(FULL_ENABLED_KEY, Boolean.toString(fullEnabled));
        values.put(FULL_FREQUENCY_KEY, fullFrequency);
        values.put(FULL_TIME_KEY, fullTime);
        values.put(FULL_WEEKDAY_KEY, fullWeekday);

        writeValues(values);
        return load();
    }

    private Map<String, String> readValues() {
        Path file = sharedConfigService.resolvePath(FILE_NAME);
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            return values;
        }

        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator < 1) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                if (!ORDERED_KEYS.contains(key)) {
                    continue;
                }
                values.put(key, trimmed.substring(separator + 1).trim());
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read backup settings from " + file, ex);
        }
    }

    private void writeValues(Map<String, String> values) {
        Path file = sharedConfigService.resolvePath(FILE_NAME);
        Path parent = file.getParent();
        if (parent == null) {
            throw new IllegalStateException("Backup settings path has no parent: " + file);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".backup-settings-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write("# Iguana backup policy (non-sensitive)");
                writer.newLine();
                for (String key : ORDERED_KEYS) {
                    writer.write(key);
                    writer.write('=');
                    writer.write(values.getOrDefault(key, ""));
                    writer.newLine();
                }
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write backup settings to " + file, ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort after a failed publication.
                }
            }
        }
    }

    private String normalizeDestination(String raw) {
        String value = raw != null ? raw.trim() : "";
        if (value.length() > MAX_DESTINATION_LENGTH) {
            throw new IllegalArgumentException("Путь backup-хранилища слишком длинный.");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(
                    "Путь backup-хранилища содержит недопустимые управляющие символы."
            );
        }
        return value;
    }

    private int parseInteger(Object raw, int fallback, String fieldName) {
        if (raw == null || !StringUtils.hasText(raw.toString())) {
            return fallback;
        }
        final int value;
        try {
            value = raw instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " должен быть целым числом.");
        }
        if (value < 1 || value > MAX_RETENTION_DAYS) {
            throw new IllegalArgumentException(
                    fieldName + " должен быть в диапазоне 1.." + MAX_RETENTION_DAYS + " дней."
            );
        }
        return value;
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        String value = raw.toString().trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallback;
        };
    }

    private String normalizeFrequency(Object raw, String fallback) {
        return normalizeChoice(raw, fallback, ALLOWED_FREQUENCIES, "frequency");
    }

    private String normalizeWeekday(Object raw, String fallback) {
        String value = raw == null ? fallback : raw.toString().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_WEEKDAYS.contains(value)) {
            throw new IllegalArgumentException("weekday должен быть одним из MON..SUN.");
        }
        return value;
    }

    private String normalizeTime(Object raw, String fallback, String fieldName) {
        String value = raw == null ? fallback : raw.toString().trim();
        if (!value.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException(fieldName + " должен быть в формате HH:mm.");
        }
        return value;
    }

    private String normalizeChoice(Object raw,
                                   String fallback,
                                   Set<String> allowed,
                                   String fieldName) {
        String value = raw == null ? fallback : raw.toString().trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(fieldName + " содержит неподдерживаемое значение.");
        }
        return value;
    }

    private List<String> normalizeComponents(Object raw, List<String> fallback) {
        List<String> source = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    source.add(item.toString());
                }
            }
        } else if (raw != null && StringUtils.hasText(raw.toString())) {
            for (String item : raw.toString().split(",")) {
                source.add(item);
            }
        } else {
            source.addAll(fallback);
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : source) {
            String value = item != null ? item.trim().toLowerCase(Locale.ROOT) : "";
            if (!StringUtils.hasText(value)) {
                continue;
            }
            if (!ALLOWED_COMPONENTS.contains(value)) {
                throw new IllegalArgumentException(
                        "Неподдерживаемый backup-компонент: " + value
                );
            }
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Выберите хотя бы один backup-компонент.");
        }
        return List.copyOf(normalized);
    }

    private String asString(Object raw) {
        return raw != null ? raw.toString() : "";
    }
}

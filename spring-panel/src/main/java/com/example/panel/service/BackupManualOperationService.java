package com.example.panel.service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackupManualOperationService {

    private static final String REQUEST_FILE = "backup-manual-request.properties";
    private static final String RUNNING_FILE = "backup-manual-request.running";
    private static final String STATUS_FILE = "backup-manual-status.properties";
    private static final String RUNNER_STATUS_FILE = "backup-policy-runner.status";
    private static final Duration RUNNER_ACTIVE_WINDOW = Duration.ofMinutes(3);
    private static final Set<String> ALLOWED_MODES = Set.of("critical", "full", "custom");

    private final SharedConfigService sharedConfigService;
    private final BackupSettingsService backupSettingsService;

    public BackupManualOperationService(SharedConfigService sharedConfigService,
                                        BackupSettingsService backupSettingsService) {
        this.sharedConfigService = sharedConfigService;
        this.backupSettingsService = backupSettingsService;
    }

    public synchronized Map<String, Object> enqueue(Map<String, Object> payload, String requestedBy) {
        Map<String, Object> policy = backupSettingsService.load();
        if (!Boolean.TRUE.equals(policy.get("configured"))) {
            throw new IllegalArgumentException("Сначала сохраните путь backup-хранилища.");
        }

        Map<String, Object> source = payload != null ? payload : Map.of();
        String mode = normalizeMode(source.get("mode"));
        boolean verifyRestore = parseBoolean(source.get("verify_restore"), false);
        boolean allowLocalTest = parseBoolean(source.get("allow_local_test"), false);
        boolean externalFailureDomain = Boolean.TRUE.equals(policy.get("external_failure_domain"));

        if (!externalFailureDomain && !allowLocalTest) {
            throw new IllegalArgumentException(
                    "Путь не подтверждён как внешний failure domain. "
                            + "Для локальной проверки явно включите «Разрешить локальный тестовый запуск (не DR)»."
            );
        }

        Path request = sharedConfigService.resolvePath(REQUEST_FILE);
        Path running = sharedConfigService.resolvePath(RUNNING_FILE);
        if (Files.exists(request) || Files.exists(running)) {
            throw new IllegalStateException(
                    "Другой ручной backup уже находится в очереди или выполняется. Дождитесь завершения."
            );
        }

        String requestId = UUID.randomUUID().toString();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("request_id", requestId);
        values.put("requested_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        values.put("requested_by", sanitizeSimple(requestedBy, "unknown"));
        values.put("mode", mode);
        values.put("verify_restore", Boolean.toString(verifyRestore));
        values.put("allow_local_test", Boolean.toString(allowLocalTest));

        publishRequest(request, values);
        return status();
    }

    public Map<String, Object> status() {
        Map<String, String> request = readValues(REQUEST_FILE);
        Map<String, String> running = readValues(RUNNING_FILE);
        Map<String, String> completed = readValues(STATUS_FILE);
        Map<String, String> heartbeat = readValues(RUNNER_STATUS_FILE);

        String operationStatus = "idle";
        Map<String, String> operation = completed;
        if (!running.isEmpty()) {
            operationStatus = "running";
            operation = running;
            if (sameRequest(running, completed) && StringUtils.hasText(completed.get("status"))) {
                operationStatus = completed.get("status");
            }
        } else if (!request.isEmpty()) {
            operationStatus = "queued";
            operation = request;
        } else if (StringUtils.hasText(completed.get("status"))) {
            operationStatus = completed.get("status");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runner_active", isRunnerActive(heartbeat));
        result.put("runner_last_seen_at", heartbeat.getOrDefault("last_seen_at", ""));
        result.put("runner_platform", heartbeat.getOrDefault("platform", ""));
        result.put("schedule_ready", parseBoolean(heartbeat.get("schedule_ready"), false));
        result.put("operation_status", operationStatus);
        result.put("request_id", operation.getOrDefault("request_id", ""));
        result.put("mode", operation.getOrDefault("mode", ""));
        result.put("verify_restore", parseBoolean(operation.get("verify_restore"), false));
        result.put("allow_local_test", parseBoolean(operation.get("allow_local_test"), false));
        result.put("requested_at", operation.getOrDefault("requested_at", ""));
        result.put("requested_by", operation.getOrDefault("requested_by", ""));
        result.put("started_at", completed.getOrDefault("started_at", ""));
        result.put("finished_at", completed.getOrDefault("finished_at", ""));
        result.put("message", completed.getOrDefault("message", ""));
        return result;
    }

    private boolean sameRequest(Map<String, String> left, Map<String, String> right) {
        String leftId = left.get("request_id");
        String rightId = right.get("request_id");
        return StringUtils.hasText(leftId) && leftId.equals(rightId);
    }

    private boolean isRunnerActive(Map<String, String> heartbeat) {
        String raw = heartbeat.get("last_seen_at");
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        try {
            OffsetDateTime seen = OffsetDateTime.parse(raw);
            Duration age = Duration.between(seen, OffsetDateTime.now(ZoneOffset.UTC));
            return !age.isNegative() && age.compareTo(RUNNER_ACTIVE_WINDOW) <= 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void publishRequest(Path request, Map<String, String> values) {
        Path parent = request.getParent();
        if (parent == null) {
            throw new IllegalStateException("Manual backup request path has no parent: " + request);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".backup-manual-request-", ".tmp");
            writeValues(temporary, values);
            try {
                Files.move(temporary, request, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, request);
            }
        } catch (FileAlreadyExistsException ex) {
            throw new IllegalStateException(
                    "Другой ручной backup уже был поставлен в очередь. Обновите статус операции."
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось создать manual backup request: " + request, ex);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Best effort after failed atomic publication.
                }
            }
        }
    }

    private Map<String, String> readValues(String fileName) {
        Path file = sharedConfigService.resolvePath(fileName);
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
                values.put(
                        trimmed.substring(0, separator).trim(),
                        trimmed.substring(separator + 1).trim()
                );
            }
            return values;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read manual backup state " + file, ex);
        }
    }

    private void writeValues(Path file, Map<String, String> values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# Iguana manual backup request");
            writer.newLine();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey());
                writer.write('=');
                writer.write(sanitizeSimple(entry.getValue(), ""));
                writer.newLine();
            }
        }
    }

    private String normalizeMode(Object raw) {
        String value = raw != null ? raw.toString().trim().toLowerCase() : "critical";
        if (!ALLOWED_MODES.contains(value)) {
            throw new IllegalArgumentException("Режим ручного backup должен быть critical, full или custom.");
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
        return switch (raw.toString().trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> fallback;
        };
    }

    private String sanitizeSimple(String raw, String fallback) {
        String value = StringUtils.hasText(raw) ? raw.trim() : fallback;
        value = value.replace('\r', ' ').replace('\n', ' ').replace('=', '_');
        return value.length() <= 160 ? value : value.substring(0, 160);
    }
}

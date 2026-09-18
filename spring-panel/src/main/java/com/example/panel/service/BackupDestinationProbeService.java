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
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackupDestinationProbeService {

    private static final String REQUEST_FILE = "backup-destination-probe-request.properties";
    private static final String RUNNING_FILE = "backup-destination-probe-request.running";
    private static final String STATUS_FILE = "backup-destination-probe-status.properties";
    private static final String RUNNER_STATUS_FILE = "backup-policy-runner.status";
    private static final Duration RUNNER_ACTIVE_WINDOW = Duration.ofSeconds(20);

    private final SharedConfigService sharedConfigService;
    private final BackupSettingsService backupSettingsService;

    public BackupDestinationProbeService(SharedConfigService sharedConfigService,
                                         BackupSettingsService backupSettingsService) {
        this.sharedConfigService = sharedConfigService;
        this.backupSettingsService = backupSettingsService;
    }

    public synchronized Map<String, Object> enqueue(Map<String, Object> payload, String requestedBy) {
        Map<String, Object> settings = backupSettingsService.load();
        if (!Boolean.TRUE.equals(settings.get("configured"))) {
            throw new IllegalArgumentException("Сначала сохраните backup destination.");
        }

        Path request = sharedConfigService.resolvePath(REQUEST_FILE);
        Path running = sharedConfigService.resolvePath(RUNNING_FILE);
        if (Files.exists(request) || Files.exists(running)) {
            throw new IllegalStateException(
                    "Другой destination probe уже находится в очереди или выполняется. Дождитесь завершения."
            );
        }

        boolean writeTest = parseBoolean(payload != null ? payload.get("write_test") : null, false);
        String requestId = UUID.randomUUID().toString();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("request_id", requestId);
        values.put("requested_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        values.put("requested_by", sanitizeLine(requestedBy, "unknown", 160));
        values.put("write_test", Boolean.toString(writeTest));
        values.put("destination_signature", setting(settings, "destination_signature"));
        values.put("destination_type", setting(settings, "destination_type"));
        values.put("destination_path", setting(settings, "destination_path"));
        values.put("destination_server", setting(settings, "destination_server"));
        values.put("destination_share", setting(settings, "destination_share"));
        values.put("destination_subpath", setting(settings, "destination_subpath"));
        values.put("destination_auth_mode", setting(settings, "destination_auth_mode"));
        values.put("destination_username", setting(settings, "destination_username"));
        values.put("destination_credential_ref", setting(settings, "destination_credential_ref"));

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

        String signature = operation.getOrDefault(
                "destination_signature",
                completed.getOrDefault("destination_signature", "")
        );
        Map<String, Object> settings = backupSettingsService.load();
        boolean destinationVerified = "success".equalsIgnoreCase(operationStatus)
                && StringUtils.hasText(signature)
                && signature.equals(setting(settings, "destination_signature"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runner_active", isRunnerActive(heartbeat));
        result.put("runner_last_seen_at", heartbeat.getOrDefault("last_seen_at", ""));
        result.put("runner_platform", heartbeat.getOrDefault("platform", ""));
        result.put("operation_status", operationStatus);
        result.put("request_id", operation.getOrDefault("request_id", ""));
        result.put("destination_signature", signature);
        result.put("destination_verified", destinationVerified);
        result.put("write_test", parseBoolean(operation.get("write_test"), false));
        result.put("requested_at", operation.getOrDefault("requested_at", ""));
        result.put("requested_by", operation.getOrDefault("requested_by", ""));
        result.put("started_at", completed.getOrDefault("started_at", ""));
        result.put("finished_at", completed.getOrDefault("finished_at", ""));
        result.put("step", completed.getOrDefault("step", ""));
        result.put("completed_steps", completed.getOrDefault("completed_steps", ""));
        result.put("error_code", completed.getOrDefault("error_code", ""));
        result.put("free_bytes", completed.getOrDefault("free_bytes", ""));
        result.put("message", completed.getOrDefault("message", ""));
        return result;
    }

    private String setting(Map<String, Object> settings, String key) {
        Object value = settings != null ? settings.get(key) : null;
        return value != null ? value.toString() : "";
    }

    private boolean sameRequest(Map<String, String> left, Map<String, String> right) {
        String leftId = left.get("request_id");
        String rightId = right.get("request_id");
        return StringUtils.hasText(leftId) && leftId.equals(rightId);
    }

    private boolean isRunnerActive(Map<String, String> heartbeat) {
        if (!"online".equalsIgnoreCase(heartbeat.getOrDefault("status", ""))) {
            return false;
        }
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
            throw new IllegalStateException("Destination probe request path has no parent: " + request);
        }

        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".backup-destination-probe-", ".tmp");
            writeValues(temporary, values);
            try {
                Files.move(temporary, request, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, request);
            }
        } catch (FileAlreadyExistsException ex) {
            throw new IllegalStateException("Другой destination probe уже был поставлен в очередь.");
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось создать destination probe request: " + request, ex);
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
            throw new IllegalStateException("Failed to read destination probe state " + file, ex);
        }
    }

    private void writeValues(Path file, Map<String, String> values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# Iguana backup destination probe request (non-secret)");
            writer.newLine();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                writer.write(entry.getKey());
                writer.write('=');
                writer.write(sanitizeLine(entry.getValue(), "", 2048));
                writer.newLine();
            }
        }
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

    private String sanitizeLine(String raw, String fallback, int maxLength) {
        String value = StringUtils.hasText(raw) ? raw.trim() : fallback;
        value = value.replace('\r', ' ').replace('\n', ' ');
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

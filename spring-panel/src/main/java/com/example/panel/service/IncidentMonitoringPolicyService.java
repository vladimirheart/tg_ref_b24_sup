package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Общая для web/worker политика. Отсутствующий файл сохраняет прежнее поведение. */
@Service
public class IncidentMonitoringPolicyService {
    public static final Map<String, String> DIRECTIONS;
    static {
        Map<String, String> items = new LinkedHashMap<>();
        items.put("provider_health", "Внешние подключения и каналы");
        items.put("public_ingress", "Публичные адреса и доступность панели");
        items.put("smtp", "SMTP и доставка уведомлений");
        items.put("ssl", "SSL-сертификаты");
        items.put("rms", "RMS: лицензии и доступность");
        items.put("iiko", "iiko API");
        items.put("backup_readiness", "Готовность резервных копий");
        items.put("credential_rotation", "Сроки и ротация учётных данных");
        items.put("provider_delivery", "Контроль доставки сообщений");
        items.put("integration_transport", "Транспорт и фоновые задачи — общий выключатель");
        items.put("integration_transport.queue", "Очередь транспорта интеграций");
        items.put("integration_transport.ui_updates", "Обновление открытых страниц панели");
        items.put("integration_transport.operator_notifications", "Обработка новых сообщений для операторов");
        items.put("alertmanager", "Приём инцидентов от Alertmanager");
        DIRECTIONS = java.util.Collections.unmodifiableMap(items);
    }
    private final ObjectMapper mapper;
    private final Path file;
    private final Clock clock;

    @Autowired
    public IncidentMonitoringPolicyService(SharedConfigService config, ObjectMapper mapper) {
        this(config.resolvePath("incident_monitoring.json"), mapper, Clock.systemUTC());
    }

    IncidentMonitoringPolicyService(Path file, ObjectMapper mapper, Clock clock) {
        this.file = file; this.mapper = mapper; this.clock = clock;
    }

    public boolean isSuppressed(String direction) {
        if (direction == null || !DIRECTIONS.containsKey(direction)) return false;
        var saved = read();
        var now = OffsetDateTime.now(clock);
        return suppressed(saved.get(direction), now)
            || (direction.startsWith("integration_transport.") && suppressed(saved.get("integration_transport"), now));
    }

    public static String workerDirection(String workerKey) {
        return switch (workerKey == null ? "" : workerKey) {
            case "ui-event-outbox-watch" -> "integration_transport.ui_updates";
            case "operator-notification-watch.chat-history" -> "integration_transport.operator_notifications";
            default -> null; // Historical/unknown cursors are not active monitored tasks.
        };
    }

    public boolean isSignalSuppressed(String signalType, String signalKey) {
        if (isSuppressed(signalType)) return true;
        if (!"integration_transport".equals(signalType) || signalKey == null) return false;
        if (List.of("panel-rabbitmq-bridge", "panel-transport-sustained-pressure").contains(signalKey))
            return isSuppressed("integration_transport.queue");
        String prefix = "panel-runtime-checkpoints/";
        if ("panel-runtime-checkpoints".equals(signalKey)) return true; // Retired duplicate aggregate.
        String direction = signalKey.startsWith(prefix) ? workerDirection(signalKey.substring(prefix.length())) : null;
        return signalKey.startsWith(prefix) && (direction == null || isSuppressed(direction));
    }

    public Map<String, Object> overview() {
        Map<String, Map<String, Object>> saved = read();
        List<Map<String, Object>> items = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(clock);
        DIRECTIONS.forEach((id, label) -> {
            Map<String, Object> row = new LinkedHashMap<>(saved.getOrDefault(id, Map.of("mode", "enabled")));
            boolean parentSuppressed = id.startsWith("integration_transport.") && suppressed(saved.get("integration_transport"), now);
            boolean ownSuppressed = suppressed(row, now);
            row.put("id", id); row.put("label", label); row.put("suppressed", ownSuppressed);
            row.put("parent_suppressed", parentSuppressed);
            row.put("effective_mode", ownSuppressed ? row.get("mode") : "enabled");
            row.put("description", "alertmanager".equals(id)
                ? "Останавливает приём и повторы инцидентов. Сбор метрик Prometheus настраивается отдельно."
                : id.startsWith("integration_transport.")
                    ? "integration_transport.queue".equals(id)
                        ? "Контроль зависшей обработки и системного накопления очереди. Пауза не останавливает доставку рабочих сообщений."
                        : "Контроль подтверждённого отставания этой задачи. Нет новых данных — нет инцидента. Пауза останавливает проверки и уведомления, но не сам обработчик."
                    : "integration_transport".equals(id)
                        ? "Общий выключатель трёх отдельных проверок ниже. Отдельные ошибки записей и возраст позиции без новых данных не создают инцидент."
                        : "Останавливает проверки направления, новые инциденты и повторные уведомления. История сохраняется.");
            items.add(row);
        });
        return Map.of("items", items, "server_time", now.toString());
    }

    public synchronized Map<String, Object> update(String direction, Map<String, Object> payload, String actor) {
        if (!DIRECTIONS.containsKey(direction)) throw bad("Неизвестное направление мониторинга.");
        String mode = String.valueOf(payload.getOrDefault("mode", ""));
        if (!List.of("enabled", "paused", "disabled").contains(mode)) throw bad("Выберите режим мониторинга.");
        Map<String, Object> row = new LinkedHashMap<>(); row.put("mode", mode);
        OffsetDateTime now = OffsetDateTime.now(clock);
        if ("paused".equals(mode)) {
            OffsetDateTime until;
            try {
                if (payload.get("duration_minutes") instanceof Number duration) {
                    long minutes = duration.longValue();
                    if (minutes < 1 || minutes > 525600 || duration.doubleValue() != minutes || payload.containsKey("until")) throw new IllegalArgumentException();
                    until = now.plusMinutes(minutes);
                } else {
                    until = OffsetDateTime.parse(String.valueOf(payload.get("until")));
                }
            }
            catch (RuntimeException ex) { throw bad("Укажите срок паузы с часовым поясом."); }
            if (!until.isAfter(now)) throw bad("Окончание паузы должно быть в будущем.");
            row.put("until", until.toString());
        }
        row.put("updated_at", now.toString()); row.put("updated_by", actor == null ? "" : actor);
        // Межпроцессный lock + atomic rename: web replicas не теряют изменения других направлений.
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                Map<String, Map<String, Object>> all = new LinkedHashMap<>(read()); all.put(direction, row);
                Path temporary = Files.createTempFile(file.getParent(), "incident-monitoring-", ".tmp");
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), all);
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temporary); }
            }
        } catch (IOException ex) { throw new IllegalStateException("Не удалось сохранить политику мониторинга.", ex); }
        return overview();
    }

    private Map<String, Map<String, Object>> read() {
        if (!Files.exists(file)) return Map.of();
        try {
            Map<String, Map<String, Object>> saved = mapper.readValue(file.toFile(), new TypeReference<>() {});
            if (saved == null) throw new IOException("Empty policy");
            for (var entry : saved.entrySet()) {
                if (!DIRECTIONS.containsKey(entry.getKey()) || entry.getValue() == null) throw new IOException("Invalid direction");
                suppressed(entry.getValue(), OffsetDateTime.now(clock));
            }
            return saved;
        } catch (IOException | RuntimeException ex) {
            // Повреждение файла не должно незаметно включать отключённые проверки.
            throw new IllegalStateException("Не удалось прочитать политику мониторинга; проверки не запускаются.", ex);
        }
    }

    private boolean suppressed(Map<String, Object> row, OffsetDateTime now) {
        if (row == null) return false;
        return switch (String.valueOf(row.get("mode"))) {
            case "enabled" -> false;
            case "disabled" -> true;
            case "paused" -> OffsetDateTime.parse(String.valueOf(row.get("until"))).isAfter(now);
            default -> throw new IllegalArgumentException("Invalid monitoring mode");
        };
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}

package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RmsMonitoringScheduleSettingsService {

    private static final String SETTINGS_KEY = "rms_monitoring_schedule";
    private static final int DEFAULT_LICENSE_INTERVAL_MINUTES = 1440;
    private static final int DEFAULT_NETWORK_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_QUEUE_GAP_SECONDS = 20;

    private final SharedConfigService sharedConfigService;

    public RmsMonitoringScheduleSettingsService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public ScheduleSettings load() {
        Object raw = sharedConfigService.loadSettings().get(SETTINGS_KEY);
        Map<?, ?> values = raw instanceof Map<?, ?> map ? map : Map.of();
        return new ScheduleSettings(
            integer(values.get("license_interval_minutes"), DEFAULT_LICENSE_INTERVAL_MINUTES, 5, 10080, "Интервал лицензий"),
            integer(values.get("network_interval_minutes"), DEFAULT_NETWORK_INTERVAL_MINUTES, 1, 1440, "Интервал доступности"),
            integer(values.get("queue_gap_seconds"), DEFAULT_QUEUE_GAP_SECONDS, 0, 300, "Пауза между запросами")
        );
    }

    public ScheduleSettings save(Map<String, Object> payload) {
        Map<String, Object> source = payload != null ? payload : Map.of();
        ScheduleSettings settings = new ScheduleSettings(
            integer(source.get("license_interval_minutes"), DEFAULT_LICENSE_INTERVAL_MINUTES, 5, 10080, "Интервал лицензий"),
            integer(source.get("network_interval_minutes"), DEFAULT_NETWORK_INTERVAL_MINUTES, 1, 1440, "Интервал доступности"),
            integer(source.get("queue_gap_seconds"), DEFAULT_QUEUE_GAP_SECONDS, 0, 300, "Пауза между запросами")
        );
        Map<String, Object> root = new LinkedHashMap<>(sharedConfigService.loadSettings());
        root.put(SETTINGS_KEY, settings.toMap());
        sharedConfigService.saveSettings(root);
        return settings;
    }

    private int integer(Object raw, int fallback, int min, int max, String label) {
        if (raw == null || raw.toString().isBlank()) return fallback;
        final int value;
        try { value = raw instanceof Number number ? number.intValue() : Integer.parseInt(raw.toString().trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " должен быть целым числом."); }
        if (value < min || value > max) throw new IllegalArgumentException(label + " должен быть в диапазоне " + min + ".." + max + ".");
        return value;
    }

    public record ScheduleSettings(int licenseIntervalMinutes, int networkIntervalMinutes, int queueGapSeconds) {
        public Map<String, Object> toMap() {
            return Map.of("license_interval_minutes", licenseIntervalMinutes, "network_interval_minutes", networkIntervalMinutes, "queue_gap_seconds", queueGapSeconds);
        }
    }
}

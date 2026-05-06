package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LocationsIikoSyncSettingsServiceTest {

    private final LocationsIikoSyncSettingsService service = new LocationsIikoSyncSettingsService();

    @Test
    void loadReturnsDefaultsWhenSettingsMissing() {
        assertThat(service.load(Map.of()).enabled()).isTrue();
        assertThat(service.load(Map.of()).intervalMinutes()).isEqualTo(5);
    }

    @Test
    void applyPayloadNormalizesBounds() {
        Map<String, Object> settings = new java.util.LinkedHashMap<>();

        boolean modified = service.applyPayload(
                Map.of(LocationsIikoSyncSettingsService.SETTINGS_KEY, Map.of("enabled", false, "interval_minutes", 99999)),
                settings
        );

        assertThat(modified).isTrue();
        assertThat(service.load(settings).enabled()).isFalse();
        assertThat(service.load(settings).intervalMinutes()).isEqualTo(1440);
    }
}

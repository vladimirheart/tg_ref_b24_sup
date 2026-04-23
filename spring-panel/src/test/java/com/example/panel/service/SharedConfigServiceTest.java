package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedConfigServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadSettingsReturnsEmptyMapWhenFileMissing() {
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());

        assertTrue(sharedConfigService.loadSettings().isEmpty());
    }

    @Test
    void saveSettingsPersistsReadableJson() throws Exception {
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());

        sharedConfigService.saveSettings(Map.of(
                "theme", "neo",
                "dialog_config", Map.of("sla_target_minutes", 240)
        ));

        assertEquals("neo", sharedConfigService.loadSettings().get("theme"));
        assertTrue(Files.isRegularFile(tempDir.resolve("settings.json")));
    }

    @Test
    void saveLocationsPersistsRoundTripJsonTree() {
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());

        sharedConfigService.saveLocations(Map.of(
                "tree", Map.of(
                        "Retail", Map.of(
                                "shop", Map.of("Moscow", java.util.List.of("Store 1"))
                        )
                ),
                "city_meta", Map.of(
                        "Retail::shop::Moscow", Map.of("country", "Россия", "partner_type", "shop")
                )
        ));

        JsonNode locations = sharedConfigService.loadLocations();

        assertTrue(Files.isRegularFile(tempDir.resolve("locations.json")));
        assertEquals("Store 1", locations.path("tree").path("Retail").path("shop").path("Moscow").get(0).asText());
        assertEquals("Россия", locations.path("city_meta").path("Retail::shop::Moscow").path("country").asText());
    }
}

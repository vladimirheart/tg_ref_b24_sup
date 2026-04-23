package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.panel.model.channel.BotCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Test
    void saveOrgStructurePersistsRoundTripJsonTree() {
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());

        sharedConfigService.saveOrgStructure(Map.of(
            "departments", List.of(
                Map.of(
                    "id", "support",
                    "title", "Support",
                    "teams", List.of(
                        Map.of("id", "l1", "title", "L1")
                    )
                )
            )
        ));

        JsonNode structure = sharedConfigService.loadOrgStructure();

        assertTrue(Files.isRegularFile(tempDir.resolve("org_structure.json")));
        assertEquals("support", structure.path("departments").get(0).path("id").asText());
        assertEquals("L1", structure.path("departments").get(0).path("teams").get(0).path("title").asText());
    }

    @Test
    void saveBotCredentialsPersistsRoundTripCredentialList() {
        SharedConfigService sharedConfigService = new SharedConfigService(objectMapper, tempDir.toString());

        sharedConfigService.saveBotCredentials(List.of(
            new BotCredential(10L, "TG Prod", "telegram", "token-1", true),
            new BotCredential(11L, "VK Backup", "vk", "token-2", false)
        ));

        List<BotCredential> credentials = sharedConfigService.loadBotCredentials();

        assertTrue(Files.isRegularFile(tempDir.resolve("bot_credentials.json")));
        assertEquals(2, credentials.size());
        assertEquals("TG Prod", credentials.get(0).name());
        assertEquals("telegram", credentials.get(0).platform());
        assertTrue(Boolean.TRUE.equals(credentials.get(0).active()));
        assertEquals("VK Backup", credentials.get(1).name());
        assertTrue(Boolean.FALSE.equals(credentials.get(1).active()));
    }
}

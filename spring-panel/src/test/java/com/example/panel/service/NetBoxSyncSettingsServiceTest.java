package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetBoxSyncSettingsServiceTest {

    private final NetBoxSyncSettingsService service = new NetBoxSyncSettingsService();

    @Test
    void loadDropsUiHintFromStoredToken() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("netbox_sync", Map.of(
                "base_url", "https://netbox.example.com",
                "api_token", "Укажите API token NetBox",
                "enabled", true,
                "interval_minutes", 60
        ));

        NetBoxSyncSettingsService.NetBoxSyncSettings loaded = service.load(settings);
        Map<String, Object> clientPayload = service.loadForClient(settings);

        assertEquals("", loaded.apiToken());
        assertFalse(service.hasUsableApiToken("Укажите API token NetBox"));
        assertEquals(Boolean.FALSE, clientPayload.get("api_token_saved"));
    }

    @Test
    void applyPayloadDoesNotPersistUiHintInsteadOfToken() {
        Map<String, Object> settings = new LinkedHashMap<>();
        Map<String, Object> payload = Map.of(
                "netbox_sync", Map.of(
                        "base_url", "https://netbox.example.com/",
                        "api_token", "Укажите API token NetBox",
                        "enabled", false,
                        "interval_minutes", 60
                )
        );

        boolean modified = service.applyPayload(payload, settings);

        assertTrue(modified);
        NetBoxSyncSettingsService.NetBoxSyncSettings loaded = service.load(settings);
        assertEquals("https://netbox.example.com", loaded.baseUrl());
        assertEquals("", loaded.apiToken());
    }

    @Test
    void recognizesTrimmedNetBoxTokenAsUsable() {
        assertTrue(service.hasUsableApiToken("  c8c2bb8a04f8014b483f3a0834fd35cc76cc8f0b  "));
        assertFalse(service.hasUsableApiToken("broken token with spaces"));
    }

    @Test
    void applyPayloadPersistsDistinctSelectedSiteIds() {
        Map<String, Object> settings = new LinkedHashMap<>();
        Map<String, Object> payload = Map.of(
                "netbox_sync", Map.of(
                        "base_url", "https://netbox.example.com",
                        "selected_site_ids", List.of("296", "117", "296", " ")
                )
        );

        boolean modified = service.applyPayload(payload, settings);

        assertTrue(modified);
        NetBoxSyncSettingsService.NetBoxSyncSettings loaded = service.load(settings);
        assertEquals(List.of("296", "117"), loaded.selectedSiteIds());
        assertEquals(List.of("296", "117"), service.loadForClient(settings).get("selected_site_ids"));
    }
}

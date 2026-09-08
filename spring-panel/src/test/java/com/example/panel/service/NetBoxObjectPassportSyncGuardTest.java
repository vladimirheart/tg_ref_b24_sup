package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NetBoxObjectPassportSyncGuardTest {

    @Test
    void everyExistingFieldWinsEvenWhenBlankWhileTrulyMissingFieldsCanBeAddedFromNetBox() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("id", 42L);
        existing.put("netbox_site_id", "160");
        existing.put("business", "Ручной бизнес");
        existing.put("department", "Ручной департамент");
        existing.put("city", "");
        existing.put("schedule", List.of());
        existing.put("equipment", List.of(Map.of("name", "ручная касса")));
        existing.put("photos", List.of(Map.of("source", "manual", "url", "/manual.jpg")));

        Map<String, Object> imported = new LinkedHashMap<>();
        imported.put("netbox_site_id", "160");
        imported.put("business", "NetBox business");
        imported.put("department", "NetBox department");
        imported.put("city", "Смоленск");
        imported.put("schedule", List.of(Map.of("day", "mon", "from", "09:00", "to", "18:00")));
        imported.put("network_provider", "Ростелеком");
        imported.put("equipment", List.of(Map.of("name", "netbox-router")));
        List<Map<String, Object>> mergedPhotos = List.of(
                Map.of("source", "manual", "url", "/manual.jpg"),
                Map.of("source", "netbox", "url", "/netbox.jpg")
        );
        imported.put("photos", mergedPhotos);

        Map<String, Object> result = NetBoxObjectPassportSyncService
                .mergeImportedPassportPreservingExisting(existing, imported);

        assertEquals("Ручной бизнес", result.get("business"));
        assertEquals("Ручной департамент", result.get("department"));
        assertEquals("", result.get("city"));
        assertEquals(List.of(), result.get("schedule"));
        assertEquals("Ростелеком", result.get("network_provider"));
        assertEquals(existing.get("equipment"), result.get("equipment"));
        assertSame(mergedPhotos, result.get("photos"));
        assertFalse(result.containsKey("id"));
    }
    @Test
    void manualSaveTracksChangedFieldsIncludingIntentionalBlank() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("business", "СушиВёсла");
        existing.put("department", "SV-001");
        existing.put("city", "Смоленск");
        existing.put("_manual_overrides", List.of("business"));

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("business", "СушиВёсла");
        incoming.put("department", "SV-001 ручной");
        incoming.put("city", "");

        Map<String, Object> result = ObjectPassportService.markManualOverrides(existing, incoming);

        Object rawOverrides = result.get("_manual_overrides");
        assertEquals(List.of("business", "department", "city"), rawOverrides);
        assertEquals("", result.get("city"));
    }

}

package com.example.panel.passports;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObjectPassportManualOverrideGuardTest {

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

        ObjectPassportManualOverrideModel model = new ObjectPassportManualOverrideModel();
        Map<String, Object> result = model.markManualOverrides(existing, incoming);

        Object rawOverrides = result.get("_manual_overrides");
        assertEquals(List.of("business", "department", "city"), rawOverrides);
        assertEquals("", result.get("city"));
    }

    @Test
    void metadataKeysDoNotBecomeManualOverrides() {
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("business", "СушиВёсла");
        existing.put("department", "SV-001");
        existing.put("_manual_overrides", List.of("business"));

        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("business", "СушиВёсла");
        incoming.put("department", "SV-002");
        incoming.put("_sync_source", "netbox");
        incoming.put("id", 99L);
        incoming.put("is_new", true);

        ObjectPassportManualOverrideModel model = new ObjectPassportManualOverrideModel();
        Map<String, Object> result = model.markManualOverrides(existing, incoming);

        assertEquals(List.of("business", "department"), result.get("_manual_overrides"));
        assertEquals("netbox", result.get("_sync_source"));
        assertEquals(99L, result.get("id"));
        assertEquals(true, result.get("is_new"));
    }

}

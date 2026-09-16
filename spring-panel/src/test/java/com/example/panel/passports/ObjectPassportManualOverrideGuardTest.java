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

        Map<String, Object> result = ObjectPassportService.markManualOverrides(existing, incoming);

        Object rawOverrides = result.get("_manual_overrides");
        assertEquals(List.of("business", "department", "city"), rawOverrides);
        assertEquals("", result.get("city"));
    }

}

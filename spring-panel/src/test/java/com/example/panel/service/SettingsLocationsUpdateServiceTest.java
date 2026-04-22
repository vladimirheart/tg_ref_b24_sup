package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SettingsLocationsUpdateServiceTest {

    @Test
    void applyLocationsUpdatePersistsLocationsAndTriggersParameterSync() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        SettingsLocationsUpdateService service = new SettingsLocationsUpdateService(sharedConfigService, settingsParameterService);

        Map<String, Object> locations = new LinkedHashMap<>(Map.of(
                "tree", Map.of("Business", Map.of("partner", Map.of("Moscow", java.util.List.of("Store 1"))))
        ));

        assertTrue(service.applyLocationsUpdate(Map.of("locations", locations)));
        verify(sharedConfigService).saveLocations(locations);
        verify(settingsParameterService).syncParametersFromLocationsPayload(locations);
    }

    @Test
    void applyLocationsUpdateReturnsFalseWhenPayloadHasNoLocations() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        SettingsParameterService settingsParameterService = mock(SettingsParameterService.class);
        SettingsLocationsUpdateService service = new SettingsLocationsUpdateService(sharedConfigService, settingsParameterService);

        assertFalse(service.applyLocationsUpdate(Map.of("categories", java.util.List.of("A"))));
        verifyNoInteractions(sharedConfigService, settingsParameterService);
    }
}

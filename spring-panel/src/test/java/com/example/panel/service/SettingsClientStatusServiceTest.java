package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsClientStatusServiceTest {

    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final SettingsClientStatusService service = new SettingsClientStatusService(sharedConfigService);

    @Test
    void updateClientStatusesNormalizesStatusesAndPrunesUnknownColors() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>());

        Map<String, Object> response = service.updateClientStatuses(Map.of(
                "client_statuses", List.of(" vip ", "", "new", "vip"),
                "client_status_colors", Map.of("vip", "#fff000", "stale", "#000000")
        ));

        assertThat(response).containsEntry("ok", true);
        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings ->
                List.of("vip", "new").equals(settings.get("client_statuses"))
                        && Map.of("vip", "#fff000").equals(settings.get("client_status_colors"))
        ));
    }

    @Test
    void updateClientStatusesHandlesNullPayloadAsEmpty() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>());

        service.updateClientStatuses(null);

        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings ->
                List.of().equals(settings.get("client_statuses"))
                        && Map.of().equals(settings.get("client_status_colors"))
        ));
    }
}

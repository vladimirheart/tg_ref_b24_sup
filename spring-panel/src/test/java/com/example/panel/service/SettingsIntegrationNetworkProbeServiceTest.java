package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsIntegrationNetworkProbeServiceTest {

    private final IntegrationNetworkService integrationNetworkService = mock(IntegrationNetworkService.class);
    private final SettingsIntegrationNetworkProbeService service =
            new SettingsIntegrationNetworkProbeService(integrationNetworkService);

    @Test
    void probeProfilesSupportsSingleProfileAlias() {
        when(integrationNetworkService.probeProfileRoute(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new IntegrationNetworkService.RouteProbeResult(
                        "proxy", true, "ok", "proxy.internal", 3128, 0L, 0
                ));

        Map<String, Object> response = service.probeProfiles(Map.of(
                "profile", Map.of("id", "corp-proxy", "name", "Corp", "mode", "proxy")
        ));

        assertThat(response).containsEntry("success", true);
        assertThat((List<?>) response.get("items")).hasSize(1);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) response.get("items")).get(0);
        assertThat(item.get("id")).isEqualTo("corp-proxy");
        assertThat(item.get("mode")).isEqualTo("proxy");
        assertThat(item.get("reachable")).isEqualTo(true);
    }

    @Test
    void probeProfilesIgnoresUnsupportedEntriesInProfilesList() {
        when(integrationNetworkService.probeProfileRoute(org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(new IntegrationNetworkService.RouteProbeResult(
                        "direct", false, "offline", "", 0, 123L, 45
                ));

        Map<String, Object> response = service.probeProfiles(Map.of(
                "profiles", List.of(
                        "bad",
                        Map.of("id", "fallback", "name", "Fallback", "mode", "direct")
                )
        ));

        assertThat((List<?>) response.get("items")).hasSize(1);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) response.get("items")).get(0);
        assertThat(item.get("id")).isEqualTo("fallback");
        assertThat(item.get("unavailable_until_millis")).isEqualTo(123L);
        assertThat(item.get("cooldown_seconds")).isEqualTo(45);
    }
}

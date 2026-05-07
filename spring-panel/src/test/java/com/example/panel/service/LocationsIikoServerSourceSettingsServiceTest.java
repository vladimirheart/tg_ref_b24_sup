package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocationsIikoServerSourceSettingsServiceTest {

    private final LocationsIikoServerSourceSettingsService service = new LocationsIikoServerSourceSettingsService();

    @Test
    void applyPayloadPreservesSavedSecretWhenClientSendsBlankPlaceholder() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put(
                LocationsIikoServerSourceSettingsService.SETTINGS_KEY,
                List.of(
                        Map.of(
                                "id", "source-1",
                                "name", "Chain server",
                                "base_url", "https://chain.example/",
                                "api_login", "login-1",
                                "api_secret", "0123456789abcdef0123456789abcdef01234567",
                                "enabled", true
                        )
                )
        );

        boolean modified = service.applyPayload(
                Map.of(
                        LocationsIikoServerSourceSettingsService.SETTINGS_KEY,
                        List.of(
                                Map.of(
                                        "id", "source-1",
                                        "name", "Chain server updated",
                                        "base_url", "https://chain.example/",
                                        "api_login", "login-2",
                                        "api_secret", "",
                                        "enabled", false
                                )
                        )
                ),
                settings
        );

        assertThat(modified).isTrue();
        assertThat(service.loadForRuntime(settings))
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.name()).isEqualTo("Chain server updated");
                    assertThat(source.baseUrl()).isEqualTo("https://chain.example");
                    assertThat(source.apiLogin()).isEqualTo("login-2");
                    assertThat(source.apiSecret()).isEqualTo("0123456789abcdef0123456789abcdef01234567");
                    assertThat(source.enabled()).isFalse();
                });
    }

    @Test
    void loadForClientHidesSecretAndExposesSavedMarker() {
        Map<String, Object> settings = Map.of(
                LocationsIikoServerSourceSettingsService.SETTINGS_KEY,
                List.of(
                        Map.of(
                                "id", "source-1",
                                "name", "Chain server",
                                "base_url", "https://chain.example/",
                                "api_login", "login-1",
                                "api_secret", "0123456789abcdef0123456789abcdef01234567",
                                "enabled", true
                        )
                )
        );

        assertThat(service.loadForClient(settings))
                .singleElement()
                .satisfies(source -> {
                    assertThat(source.get("base_url")).isEqualTo("https://chain.example");
                    assertThat(source.get("api_secret")).isEqualTo("");
                    assertThat(source.get("api_secret_saved")).isEqualTo(true);
                });
    }

    @Test
    void applyPayloadRejectsNonSha1Secret() {
        Map<String, Object> settings = new LinkedHashMap<>();

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> service.applyPayload(
                Map.of(
                        LocationsIikoServerSourceSettingsService.SETTINGS_KEY,
                        List.of(
                                Map.of(
                                        "id", "source-1",
                                        "name", "Chain server",
                                        "base_url", "https://chain.example/",
                                        "api_login", "login-1",
                                        "api_secret", "not-a-sha1",
                                        "enabled", true
                                )
                        )
                ),
                settings
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SHA-1 пароль");
    }
}

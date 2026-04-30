package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsItConnectionCategoryServiceTest {

    private final SharedConfigService sharedConfigService = mock(SharedConfigService.class);
    private final SettingsCatalogService settingsCatalogService = new SettingsCatalogService();
    private final SettingsItConnectionCategoryService service =
            new SettingsItConnectionCategoryService(sharedConfigService, settingsCatalogService);

    @Test
    void createCategoryAddsSlugifiedCustomCategory() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>());

        Map<String, Object> response = service.createCategory(Map.of("label", "Корпоративный VPN"));

        assertThat(response).containsEntry("success", true);
        assertThat((Map<String, Object>) response.get("data")).containsEntry("label", "Корпоративный VPN");
        verify(sharedConfigService).saveSettings(org.mockito.ArgumentMatchers.argThat(settings ->
                settings.containsKey("it_connection_categories")
                        && ((Map<?, ?>) settings.get("it_connection_categories")).size() == 1
        ));
    }

    @Test
    void createCategoryRejectsDuplicateLabelIgnoringCase() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>(Map.of(
                "it_connection_categories", Map.of("vpn", "Корпоративный VPN")
        )));

        Map<String, Object> response = service.createCategory(Map.of("label", " корпоративный vpn "));

        assertThat(response).containsEntry("success", false)
                .containsEntry("error", "Такая категория уже существует");
    }

    @Test
    void createCategoryRejectsDuplicateExplicitKey() {
        when(sharedConfigService.loadSettings()).thenReturn(new LinkedHashMap<>());

        Map<String, Object> response = service.createCategory(Map.of(
                "label", "Новая категория",
                "key", "equipment_type"
        ));

        assertThat(response).containsEntry("success", false)
                .containsEntry("error", "Идентификатор категории уже используется");
    }
}

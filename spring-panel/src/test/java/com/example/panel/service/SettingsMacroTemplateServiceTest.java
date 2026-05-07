package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SettingsMacroTemplateServiceTest {

    @Test
    void normalizeForSettingsUpdatePreservesRequestedDeprecatedAtForNewDeprecatedTemplate() {
        SettingsMacroTemplateService service = new SettingsMacroTemplateService(mock(PermissionService.class));

        SettingsMacroTemplateService.MacroNormalizationResult result = service.normalizeForSettingsUpdate(
                null,
                Map.of(),
                List.of(),
                List.of(Map.of(
                        "id", "macro-deprecated",
                        "name", "Deprecated",
                        "message", "Template",
                        "deprecated", true,
                        "deprecated_at", "2026-01-01T00:00:00Z"
                ))
        );

        assertThat(result.templates()).singleElement().satisfies(template -> {
            assertThat(template).containsEntry("deprecated", true);
            assertThat(template).containsEntry("deprecated_at", "2026-01-01T00:00:00Z");
        });
    }
}

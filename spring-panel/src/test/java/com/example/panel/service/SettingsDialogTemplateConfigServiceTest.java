package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsDialogTemplateConfigServiceTest {

    @Test
    void applySettingsUsesMacroNormalizationResultAndCopiesTemplateGovernanceKeys() {
        SettingsMacroTemplateService macroTemplateService = mock(SettingsMacroTemplateService.class);
        SettingsDialogTemplateConfigService service = new SettingsDialogTemplateConfigService(macroTemplateService);
        Authentication authentication = mock(Authentication.class);

        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        dialogConfig.put("macro_templates", List.of(Map.of("id", "old")));

        List<Map<String, Object>> normalizedTemplates = List.of(Map.of("id", "macro_1", "name", "Welcome"));
        when(macroTemplateService.normalizeForSettingsUpdate(
                eq(authentication),
                eq(dialogConfig),
                eq(dialogConfig.get("macro_templates")),
                any()))
                .thenReturn(new SettingsMacroTemplateService.MacroNormalizationResult(
                        normalizedTemplates,
                        List.of("macro warning")
                ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dialog_category_templates", List.of("cat-a"));
        payload.put("dialog_macro_templates", List.of(Map.of("name", "Welcome", "message", "Hi")));
        payload.put("dialog_macro_governance_require_owner", true);
        payload.put("dialog_macro_variable_catalog_external_url", "https://example.test/catalog");

        List<String> warnings = new ArrayList<>();
        service.applySettings(payload, dialogConfig, authentication, warnings);

        assertEquals(List.of("cat-a"), dialogConfig.get("category_templates"));
        assertEquals(normalizedTemplates, dialogConfig.get("macro_templates"));
        assertEquals(true, dialogConfig.get("macro_governance_require_owner"));
        assertEquals("https://example.test/catalog", dialogConfig.get("macro_variable_catalog_external_url"));
        assertEquals(List.of("macro warning"), warnings);
    }
}

package com.example.panel.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SettingsDialogTemplateConfigServiceTest {

    private final SettingsDialogTemplateConfigService service =
            new SettingsDialogTemplateConfigService(mock(SettingsMacroTemplateService.class));

    @Test
    void savesPriorityMetadataWithoutChangingCategoryNamesOrOtherTemplateFields() {
        Map<String, Object> template = Map.of("id", "cat-1", "name", "Поддержка",
                "categories", List.of("Касса", "Сеть", "Старая категория"),
                "category_priorities", Map.of("Касса", " p1 ", "Сеть", "P5", "Удалённая", "P2"));
        Map<String, Object> config = new LinkedHashMap<>();

        service.applySettings(Map.of("dialog_category_templates", List.of(template)), config, null, new ArrayList<>());

        assertEquals(List.of(Map.of("id", "cat-1", "name", "Поддержка",
                "categories", List.of("Касса", "Сеть", "Старая категория"),
                "category_priorities", Map.of("Касса", "P1", "Сеть", "P5"))), config.get("category_templates"));
        // The request must stay intact, including metadata of a removed category.
        assertEquals(" p1 ", ((Map<?, ?>) template.get("category_priorities")).get("Касса"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"P0", "P6", "urgent"})
    void rejectsInvalidPriorityBeforeUpdatingConfig(String priority) {
        Map<String, Object> config = new LinkedHashMap<>();
        var payload = Map.<String, Object>of("dialog_category_templates", List.of(Map.of(
                "categories", List.of("Касса"), "category_priorities", Map.of("Касса", priority))));
        assertThrows(IllegalArgumentException.class,
                () -> service.applySettings(payload, config, null, new ArrayList<>()));
        assertEquals(Map.of(), config);
    }

    @Test
    void rejectsConflictingPriorityForTheSameCategoryAcrossTemplates() {
        var payload = Map.<String, Object>of("dialog_category_templates", List.of(
                Map.of("categories", List.of("Касса"), "category_priorities", Map.of("Касса", "P1")),
                Map.of("categories", List.of("касса"), "category_priorities", Map.of("касса", "P3"))));
        assertThrows(IllegalArgumentException.class,
                () -> service.applySettings(payload, new LinkedHashMap<>(), null, new ArrayList<>()));
    }

    @Test
    void stillAcceptsLegacyTemplatesWithoutPriorities() {
        var templates = List.of(Map.of("id", "cat-legacy", "categories", List.of("Касса")));
        Map<String, Object> config = new LinkedHashMap<>();
        service.applySettings(Map.of("dialog_category_templates", templates), config, null, new ArrayList<>());
        assertEquals(templates, config.get("category_templates"));
    }

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
        payload.put("dialog_question_templates", List.of(Map.of("id", "legacy-q-1", "questions", List.of("Адрес?"))));
        payload.put("dialog_macro_templates", List.of(Map.of("name", "Welcome", "message", "Hi")));
        payload.put("dialog_macro_governance_require_owner", true);
        payload.put("dialog_macro_variable_catalog_external_url", "https://example.test/catalog");

        List<String> warnings = new ArrayList<>();
        service.applySettings(payload, dialogConfig, authentication, warnings);

        assertEquals(List.of("cat-a"), dialogConfig.get("category_templates"));
        assertEquals(List.of(Map.of("id", "legacy-q-1", "questions", List.of("Адрес?"))), dialogConfig.get("question_templates"));
        assertEquals(normalizedTemplates, dialogConfig.get("macro_templates"));
        assertEquals(true, dialogConfig.get("macro_governance_require_owner"));
        assertEquals("https://example.test/catalog", dialogConfig.get("macro_variable_catalog_external_url"));
        assertEquals(List.of("macro warning"), warnings);
    }
}

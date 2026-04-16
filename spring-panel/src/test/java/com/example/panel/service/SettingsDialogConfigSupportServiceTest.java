package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsDialogConfigSupportServiceTest {

    private final SettingsDialogConfigSupportService supportService = new SettingsDialogConfigSupportService();

    @Test
    void normalizesLegacyTimestampIntoUtc() {
        String normalized = supportService.normalizeUtcTimestampSetting(
                "2026-04-16T09:30:00",
                "reviewed at",
                null
        );

        assertEquals("2026-04-16T09:30Z", normalized);
    }

    @Test
    void rejectsOverlappingMandatoryAndOptionalDatamartFields() {
        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_mandatory_fields", "frt,ttr");
        dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_fields", "ttr,sla_breach");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> supportService.validateExternalKpiDatamartContract(dialogConfig)
        );

        assertEquals(
                "Поля data contract не могут одновременно быть mandatory и optional: ttr.",
                error.getMessage()
        );
    }

    @Test
    void rejectsOptionalCoverageWithoutOptionalFields() {
        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_coverage_required", true);
        dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_fields", "");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> supportService.validateExternalKpiDatamartContract(dialogConfig)
        );

        assertEquals(
                "Для optional coverage gate укажите хотя бы одно optional KPI-поле в data contract.",
                error.getMessage()
        );
    }

    @Test
    void normalizesCommaSeparatedFieldLists() {
        assertEquals(
                List.of("frt", "ttr", "sla_breach"),
                supportService.normalizeDatamartContractFieldList("frt, ttr, frt, sla_breach")
        );
    }
}

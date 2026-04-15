package com.example.panel.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettingsDialogConfigSupportService {

    public String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    public String normalizeUtcTimestampSetting(Object rawValue,
                                               String label,
                                               List<String> updateWarnings) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        OffsetDateTime parsed = parseUtcTimestamp(value);
        if (parsed == null) {
            if (updateWarnings != null && StringUtils.hasText(label)) {
                updateWarnings.add(label + " сохранена как есть: значение не удалось нормализовать в UTC, "
                        + "аналитика пометит timestamp как invalid.");
            }
            return value;
        }
        return parsed.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }

    public boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return false;
    }

    public void validateExternalKpiDatamartContract(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || dialogConfig.isEmpty()) {
            return;
        }
        List<String> mandatoryFields = normalizeDatamartContractFieldList(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_mandatory_fields"));
        List<String> optionalFields = normalizeDatamartContractFieldList(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_fields"));
        boolean optionalCoverageRequired = asBoolean(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_coverage_required"));
        Integer optionalCoverageThreshold = parseInteger(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct"));

        if (optionalCoverageRequired && optionalFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Для optional coverage gate укажите хотя бы одно optional KPI-поле в data contract.");
        }
        if (optionalCoverageThreshold != null
                && (optionalCoverageThreshold < 0 || optionalCoverageThreshold > 100)) {
            throw new IllegalArgumentException(
                    "Порог optional coverage для data contract должен быть в диапазоне 0..100%.");
        }

        Set<String> optionalFieldSet = new LinkedHashSet<>(optionalFields);
        List<String> overlappingFields = mandatoryFields.stream()
                .filter(optionalFieldSet::contains)
                .toList();
        if (!overlappingFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Поля data contract не могут одновременно быть mandatory и optional: "
                            + String.join(", ", overlappingFields) + ".");
        }
    }

    public List<String> normalizeDatamartContractFieldList(Object rawValue) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String field = token != null ? token.trim() : "";
            if (StringUtils.hasText(field)) {
                normalized.add(field);
            }
        }
        return new ArrayList<>(normalized);
    }

    public Integer parseInteger(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback to legacy datetime-local without timezone
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }
}

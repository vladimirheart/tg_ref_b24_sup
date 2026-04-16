package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmployeeDiscountAutomationSettingsService {

    public static final String SETTINGS_KEY = "employee_discount_automation";

    private static final String DEFAULT_PHONE_REGEX =
        "(?iu)тел\\.?\\s*сотрудника\\s*[:\\-]?\\s*([+\\d\\s()\\-]{10,})";

    private final SharedConfigService sharedConfigService;

    public EmployeeDiscountAutomationSettingsService(SharedConfigService sharedConfigService) {
        this.sharedConfigService = sharedConfigService;
    }

    public EmployeeDiscountAutomationSettings load() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object raw = settings.get(SETTINGS_KEY);
        Map<?, ?> node = raw instanceof Map<?, ?> map ? map : Map.of();
        return new EmployeeDiscountAutomationSettings(
            parseLong(node.get("bitrix_group_id")),
            normalizeStringList(node.get("task_title_markers")),
            normalizeStringListWithFallback(node.get("checklist_labels"), List.of(
                "отключение корпоративной скидки",
                "Отключение корпоративных скидок"
            )),
            defaultIfBlank(asText(node.get("phone_regex")), DEFAULT_PHONE_REGEX),
            normalizeStringList(node.get("selected_discount_category_ids")),
            normalizeStringList(node.get("excluded_wallet_ids")),
            parseBoolean(node.get("dry_run_by_default"), true)
        );
    }

    public EmployeeDiscountAutomationSettings save(Map<String, Object> payload) {
        EmployeeDiscountAutomationSettings current = load();
        EmployeeDiscountAutomationSettings updated = new EmployeeDiscountAutomationSettings(
            parseLong(payload.getOrDefault("bitrix_group_id", current.bitrixGroupId())),
            normalizeStringListOrDefault(payload.get("task_title_markers"), current.taskTitleMarkers()),
            normalizeStringListOrDefault(payload.get("checklist_labels"), current.checklistLabels()),
            defaultIfBlank(asText(payload.getOrDefault("phone_regex", current.phoneRegex())), current.phoneRegex()),
            normalizeStringListOrDefault(payload.get("selected_discount_category_ids"), current.selectedDiscountCategoryIds()),
            normalizeStringListOrDefault(payload.get("excluded_wallet_ids"), current.excludedWalletIds()),
            parseBoolean(payload.getOrDefault("dry_run_by_default", current.dryRunByDefault()), current.dryRunByDefault())
        );

        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        settings.put(SETTINGS_KEY, updated.toMap());
        sharedConfigService.saveSettings(settings);
        return updated;
    }

    private List<String> normalizeStringListOrDefault(Object raw, List<String> fallback) {
        if (raw == null) {
            return fallback;
        }
        List<String> normalized = normalizeStringList(raw);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private List<String> normalizeStringListWithFallback(Object raw, List<String> fallback) {
        List<String> normalized = normalizeStringList(raw);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private List<String> normalizeStringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                String value = asText(item);
                if (StringUtils.hasText(value) && !values.contains(value.trim())) {
                    values.add(value.trim());
                }
            }
            return values;
        }
        if (raw instanceof String text) {
            String[] chunks = text.split("[\\r\\n,;]+");
            for (String chunk : chunks) {
                if (StringUtils.hasText(chunk)) {
                    String value = chunk.trim();
                    if (!values.contains(value)) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private Long parseLong(Object raw) {
        String value = asText(raw);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = asText(raw);
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return !"false".equalsIgnoreCase(value.trim()) && !"0".equals(value.trim()) && !"off".equalsIgnoreCase(value.trim());
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String asText(Object raw) {
        return raw != null ? String.valueOf(raw).trim() : "";
    }

    public record EmployeeDiscountAutomationSettings(Long bitrixGroupId,
                                                     List<String> taskTitleMarkers,
                                                     List<String> checklistLabels,
                                                     String phoneRegex,
                                                     List<String> selectedDiscountCategoryIds,
                                                     List<String> excludedWalletIds,
                                                     boolean dryRunByDefault) {

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("bitrix_group_id", bitrixGroupId);
            map.put("task_title_markers", taskTitleMarkers);
            map.put("checklist_labels", checklistLabels);
            map.put("phone_regex", phoneRegex);
            map.put("selected_discount_category_ids", selectedDiscountCategoryIds);
            map.put("excluded_wallet_ids", excludedWalletIds);
            map.put("dry_run_by_default", dryRunByDefault);
            return map;
        }
    }
}

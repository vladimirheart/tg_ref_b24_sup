package com.example.panel.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettingsCatalogService {

    private static final Pattern NON_WORD = Pattern.compile("[^\\w]+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, String> PARAMETER_TYPES;
    private static final Map<String, List<String>> PARAMETER_DEPENDENCIES;
    private static final Map<String, String> DEFAULT_IT_CONNECTION_CATEGORIES;
    private static final Map<String, String> DEFAULT_IT_CONNECTION_CATEGORY_FIELDS;

    static {
        Map<String, String> parameterTypes = new LinkedHashMap<>();
        parameterTypes.put("business", "Бизнес");
        parameterTypes.put("partner_type", "Тип партнёра");
        parameterTypes.put("country", "Страна");
        parameterTypes.put("city", "Город");
        parameterTypes.put("legal_entity", "ЮЛ");
        parameterTypes.put("partner_contact", "Контакты партнёров и КА");
        parameterTypes.put("department", "Департамент");
        parameterTypes.put("network", "Внутренняя сеть");
        parameterTypes.put("it_connection", "Подключения IT-блока");
        parameterTypes.put("remote_access", "Параметры удалённого доступа");
        parameterTypes.put("iiko_server", "Адреса серверов iiko");
        PARAMETER_TYPES = Collections.unmodifiableMap(parameterTypes);

        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        dependencies.put("partner_type", List.of("country"));
        dependencies.put("business", List.of("country", "partner_type"));
        dependencies.put("city", List.of("country", "partner_type", "business"));
        dependencies.put("department", List.of("country", "partner_type", "business", "city"));
        PARAMETER_DEPENDENCIES = Collections.unmodifiableMap(dependencies);

        Map<String, String> itCategories = new LinkedHashMap<>();
        itCategories.put("equipment_type", "Тип оборудования");
        itCategories.put("equipment_vendor", "Производитель оборудования");
        itCategories.put("equipment_model", "Модель оборудования");
        itCategories.put("equipment_status", "Статус оборудования");
        DEFAULT_IT_CONNECTION_CATEGORIES = Collections.unmodifiableMap(itCategories);

        Map<String, String> itCategoryFields = new LinkedHashMap<>();
        itCategoryFields.put("equipment_type", "equipment_type");
        itCategoryFields.put("equipment_vendor", "equipment_vendor");
        itCategoryFields.put("equipment_model", "equipment_model");
        itCategoryFields.put("equipment_status", "equipment_status");
        DEFAULT_IT_CONNECTION_CATEGORY_FIELDS = Collections.unmodifiableMap(itCategoryFields);
    }

    public Map<String, String> getParameterTypes() {
        return PARAMETER_TYPES;
    }

    public Map<String, List<String>> getParameterDependencies() {
        return PARAMETER_DEPENDENCIES;
    }

    public Map<String, String> getDefaultItConnectionCategories() {
        return DEFAULT_IT_CONNECTION_CATEGORIES;
    }

    public Map<String, String> getItConnectionCategoryFields() {
        return DEFAULT_IT_CONNECTION_CATEGORY_FIELDS;
    }

    public Map<String, String> getItConnectionCategories(Map<String, Object> settings) {
        Map<String, String> categories = new LinkedHashMap<>(DEFAULT_IT_CONNECTION_CATEGORIES);
        Object raw = settings != null ? settings.get("it_connection_categories") : null;
        Map<String, String> custom = normalizeItConnectionCategories(raw);
        custom.forEach((key, label) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(label)) {
                categories.put(key, label);
            }
        });
        return categories;
    }

    public Map<String, String> normalizeItConnectionCategories(Object raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String keyValue = String.valueOf(key == null ? "" : key).trim();
                String label = String.valueOf(value == null ? "" : value).trim();
                if (StringUtils.hasText(keyValue) && StringUtils.hasText(label)) {
                    result.put(keyValue, label);
                }
            });
        } else if (raw instanceof List<?> list) {
            Set<String> existing = new LinkedHashSet<>(DEFAULT_IT_CONNECTION_CATEGORIES.keySet());
            for (Object entry : list) {
                String label = "";
                String key = "";
                if (entry instanceof Map<?, ?> entryMap) {
                    Object labelRaw = entryMap.get("label");
                    Object keyRaw = entryMap.get("key");
                    label = labelRaw != null ? labelRaw.toString().trim() : "";
                    key = keyRaw != null ? keyRaw.toString().trim() : "";
                } else if (entry != null) {
                    label = entry.toString().trim();
                }
                if (!StringUtils.hasText(label)) {
                    continue;
                }
                if (!StringUtils.hasText(key)) {
                    key = slugifyItConnectionCategory(label, existing);
                }
                result.put(key, label);
                existing.add(key);
            }
        }
        return result;
    }

    public String slugifyItConnectionCategory(String label, Set<String> existingKeys) {
        String normalized = Normalizer.normalize(label == null ? "" : label, Normalizer.Form.NFKD).trim();
        String base = NON_WORD.matcher(normalized).replaceAll("_").replaceAll("^_+|_+$", "").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(base)) {
            base = "category";
        }
        if (Character.isDigit(base.charAt(0))) {
            base = "cat_" + base;
        }
        String candidate = base;
        int counter = 2;
        Set<String> existing = existingKeys != null ? existingKeys : Set.of();
        while (existing.contains(candidate)) {
            candidate = base + "_" + counter;
            counter += 1;
        }
        return candidate;
    }

    private static final String STATUS_CLOSED = "Закрыт";

    public Map<String, Object> buildLocationPresets(Map<String, Object> locationTree, Map<String, Object> statusMap) {
        Map<String, String> normalizedStatuses = normalizeStatuses(statusMap);
        Set<String> businesses = new LinkedHashSet<>();
        Set<String> locationTypes = new LinkedHashSet<>();
        Set<String> cities = new LinkedHashSet<>();
        Set<String> locationNames = new LinkedHashSet<>();

        if (locationTree != null) {
            for (Map.Entry<String, Object> businessEntry : locationTree.entrySet()) {
                String business = normalizeKey(businessEntry.getKey());
                if (!StringUtils.hasText(business)) {
                    continue;
                }
                if (isClosed(normalizedStatuses, "business", business)) {
                    continue;
                }
                businesses.add(business);
                if (!(businessEntry.getValue() instanceof Map<?, ?> typeMap)) {
                    continue;
                }
                for (Map.Entry<?, ?> typeEntry : typeMap.entrySet()) {
                    String type = normalizeKey(typeEntry.getKey());
                    if (!StringUtils.hasText(type)) {
                        continue;
                    }
                    if (isClosed(normalizedStatuses, "type", business, type)) {
                        continue;
                    }
                    locationTypes.add(type);
                    if (!(typeEntry.getValue() instanceof Map<?, ?> cityMap)) {
                        continue;
                    }
                    for (Map.Entry<?, ?> cityEntry : cityMap.entrySet()) {
                        String city = normalizeKey(cityEntry.getKey());
                        if (!StringUtils.hasText(city)) {
                            continue;
                        }
                        if (isClosed(normalizedStatuses, "city", business, type, city)) {
                            continue;
                        }
                        cities.add(city);
                        Object locations = cityEntry.getValue();
                        if (locations instanceof Iterable<?> iterable) {
                            for (Object location : iterable) {
                                String name = normalizeKey(location);
                                if (!StringUtils.hasText(name)) {
                                    continue;
                                }
                                if (isClosed(normalizedStatuses, "location", business, type, city, name)) {
                                    continue;
                                }
                                if (StringUtils.hasText(name)) {
                                    locationNames.add(name);
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("business", Map.of("label", "Бизнес", "options", toSortedList(businesses)));
        fields.put("location_type", Map.of("label", "Тип бизнеса", "options", toSortedList(locationTypes)));
        fields.put("city", Map.of("label", "Город", "options", toSortedList(cities)));
        fields.put("location_name", Map.of("label", "Локация", "options", toSortedList(locationNames)));

        Map<String, Object> locationsGroup = new LinkedHashMap<>();
        locationsGroup.put("label", "Структура локаций");
        locationsGroup.put("fields", fields);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", locationsGroup);
        return result;
    }

    private Map<String, String> normalizeStatuses(Map<String, Object> statusMap) {
        Map<String, String> result = new LinkedHashMap<>();
        if (statusMap == null) {
            return result;
        }
        statusMap.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            String normalizedKey = key.toString().trim();
            if (!StringUtils.hasText(normalizedKey)) {
                return;
            }
            String normalizedValue = value != null ? value.toString().trim() : "";
            result.put(normalizedKey, normalizedValue);
        });
        return result;
    }

    private boolean isClosed(Map<String, String> statuses, String level, String... parts) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        String key = makeStatusKey(level, parts);
        String status = statuses.getOrDefault(key, "");
        return STATUS_CLOSED.equalsIgnoreCase(status.trim());
    }

    private String makeStatusKey(String level, String... parts) {
        StringBuilder builder = new StringBuilder(level);
        if (parts != null) {
            for (String part : parts) {
                builder.append("::").append(part == null ? "" : part.trim());
            }
        }
        return builder.toString();
    }

    public List<String> collectCities(Map<String, Object> locationTree) {
        Set<String> cities = new LinkedHashSet<>();
        if (locationTree == null) {
            return List.of();
        }
        for (Object typeMapValue : locationTree.values()) {
            if (!(typeMapValue instanceof Map<?, ?> typeMap)) {
                continue;
            }
            for (Object cityMapValue : typeMap.values()) {
                if (!(cityMapValue instanceof Map<?, ?> cityMap)) {
                    continue;
                }
                for (Object cityKey : cityMap.keySet()) {
                    String city = normalizeKey(cityKey);
                    if (StringUtils.hasText(city)) {
                        cities.add(city);
                    }
                }
            }
        }
        return toSortedList(cities);
    }

    private List<String> toSortedList(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    private String normalizeKey(Object value) {
        if (value == null) {
            return "";
        }
        String normalized = value.toString().trim();
        return normalized;
    }
}

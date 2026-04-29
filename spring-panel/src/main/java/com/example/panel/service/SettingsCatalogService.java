package com.example.panel.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettingsCatalogService {

    private static final Pattern NON_WORD = Pattern.compile("[^\\w]+", Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, String> PARAMETER_TYPES;
    private static final Map<String, List<String>> PARAMETER_DEPENDENCIES;
    private static final Map<String, String> DEFAULT_IT_CONNECTION_CATEGORIES;
    private static final Map<String, String> DEFAULT_IT_CONNECTION_CATEGORY_FIELDS;
    private static final String STATUS_CLOSED = "Закрыт";

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

    public Map<String, Object> buildLocationPresets(Map<String, Object> locationTree, Map<String, Object> statusMap) {
        Map<String, String> normalizedStatuses = normalizeStatuses(statusMap);
        Map<String, Object> filteredTree = filterLocationTree(locationTree, normalizedStatuses);

        Set<String> businesses = new LinkedHashSet<>();
        Set<String> locationTypes = new LinkedHashSet<>();
        Set<String> cities = new LinkedHashSet<>();
        Set<String> locationNames = new LinkedHashSet<>();

        Map<String, Set<String>> typesByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> citiesByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> citiesByType = new LinkedHashMap<>();
        Map<List<String>, Set<String>> citiesByPath = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByType = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByCity = new LinkedHashMap<>();
        Map<List<String>, Set<String>> locationsByPath = new LinkedHashMap<>();

        for (Map.Entry<String, Object> businessEntry : filteredTree.entrySet()) {
            String business = normalizeKey(businessEntry.getKey());
            if (!StringUtils.hasText(business)) {
                continue;
            }
            businesses.add(business);
            Map<String, Object> typeMap = toStringObjectMap(businessEntry.getValue());
            for (Map.Entry<String, Object> typeEntry : typeMap.entrySet()) {
                String locationType = normalizeKey(typeEntry.getKey());
                if (!StringUtils.hasText(locationType)) {
                    continue;
                }
                locationTypes.add(locationType);
                typesByBusiness.computeIfAbsent(business, key -> new LinkedHashSet<>()).add(locationType);

                Map<String, Object> cityMap = toStringObjectMap(typeEntry.getValue());
                for (Map.Entry<String, Object> cityEntry : cityMap.entrySet()) {
                    String city = normalizeKey(cityEntry.getKey());
                    if (!StringUtils.hasText(city)) {
                        continue;
                    }
                    cities.add(city);
                    citiesByBusiness.computeIfAbsent(business, key -> new LinkedHashSet<>()).add(city);
                    citiesByType.computeIfAbsent(locationType, key -> new LinkedHashSet<>()).add(city);
                    citiesByPath.computeIfAbsent(List.of(business, locationType), key -> new LinkedHashSet<>()).add(city);

                    for (String locationName : toStringList(cityEntry.getValue())) {
                        if (!StringUtils.hasText(locationName)) {
                            continue;
                        }
                        locationNames.add(locationName);
                        locationsByBusiness.computeIfAbsent(business, key -> new LinkedHashSet<>()).add(locationName);
                        locationsByType.computeIfAbsent(locationType, key -> new LinkedHashSet<>()).add(locationName);
                        locationsByCity.computeIfAbsent(city, key -> new LinkedHashSet<>()).add(locationName);
                        locationsByPath.computeIfAbsent(List.of(business, locationType, city), key -> new LinkedHashSet<>()).add(locationName);
                    }
                }
            }
        }

        Map<String, List<String>> optionMap = new LinkedHashMap<>();
        optionMap.put("business", toSortedList(businesses));
        optionMap.put("location_type", toSortedList(locationTypes));
        optionMap.put("city", toSortedList(cities));
        optionMap.put("location_name", toSortedList(locationNames));

        Map<String, Map<String, Map<String, Set<Object>>>> optionDependencies = new LinkedHashMap<>();
        optionDependencies.put("location_type", new LinkedHashMap<>());
        optionDependencies.put("city", new LinkedHashMap<>());
        optionDependencies.put("location_name", new LinkedHashMap<>());

        for (Map.Entry<String, Set<String>> entry : typesByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String locationType : entry.getValue()) {
                optionDependencies.get("location_type")
                        .computeIfAbsent(locationType, key -> new LinkedHashMap<>())
                        .computeIfAbsent("business", key -> new LinkedHashSet<>())
                        .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : citiesByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, key -> new LinkedHashMap<>())
                        .computeIfAbsent("business", key -> new LinkedHashSet<>())
                        .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : citiesByType.entrySet()) {
            String locationType = entry.getKey();
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, key -> new LinkedHashMap<>())
                        .computeIfAbsent("location_type", key -> new LinkedHashSet<>())
                        .add(locationType);
            }
        }
        for (Map.Entry<List<String>, Set<String>> entry : citiesByPath.entrySet()) {
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, key -> new LinkedHashMap<>())
                        .computeIfAbsent("paths", key -> new LinkedHashSet<>())
                        .add(List.copyOf(entry.getKey()));
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String locationName : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(locationName, key -> new LinkedHashMap<>())
                        .computeIfAbsent("business", key -> new LinkedHashSet<>())
                        .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByType.entrySet()) {
            String locationType = entry.getKey();
            for (String locationName : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(locationName, key -> new LinkedHashMap<>())
                        .computeIfAbsent("location_type", key -> new LinkedHashSet<>())
                        .add(locationType);
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByCity.entrySet()) {
            String city = entry.getKey();
            for (String locationName : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(locationName, key -> new LinkedHashMap<>())
                        .computeIfAbsent("city", key -> new LinkedHashSet<>())
                        .add(city);
            }
        }
        for (Map.Entry<List<String>, Set<String>> entry : locationsByPath.entrySet()) {
            for (String locationName : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(locationName, key -> new LinkedHashMap<>())
                        .computeIfAbsent("paths", key -> new LinkedHashSet<>())
                        .add(List.copyOf(entry.getKey()));
            }
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("business", fieldPayload("Бизнес", optionMap.get("business"), null, null));
        fields.put("location_type", fieldPayload(
                "Тип бизнеса",
                optionMap.get("location_type"),
                finalizeDependencies(optionDependencies.get("location_type")),
                buildLocationTypeTree(typesByBusiness)
        ));
        fields.put("city", fieldPayload(
                "Город",
                optionMap.get("city"),
                finalizeDependencies(optionDependencies.get("city")),
                buildCityTree(citiesByPath)
        ));
        fields.put("location_name", fieldPayload(
                "Локация",
                optionMap.get("location_name"),
                finalizeDependencies(optionDependencies.get("location_name")),
                buildLocationNameTree(locationsByPath)
        ));

        Map<String, Object> locationsGroup = new LinkedHashMap<>();
        locationsGroup.put("label", "Структура локаций");
        locationsGroup.put("fields", fields);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", locationsGroup);
        return result;
    }

    private Map<String, Object> filterLocationTree(Map<String, Object> locationTree, Map<String, String> statuses) {
        LinkedHashMap<String, Object> filteredTree = new LinkedHashMap<>();
        if (locationTree == null) {
            return filteredTree;
        }
        for (Map.Entry<String, Object> businessEntry : locationTree.entrySet()) {
            String business = normalizeKey(businessEntry.getKey());
            if (!StringUtils.hasText(business) || isClosed(statuses, "business", business)) {
                continue;
            }
            Map<String, Object> typeMap = toStringObjectMap(businessEntry.getValue());
            LinkedHashMap<String, Object> filteredTypes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> typeEntry : typeMap.entrySet()) {
                String locationType = normalizeKey(typeEntry.getKey());
                if (!StringUtils.hasText(locationType) || isClosed(statuses, "type", business, locationType)) {
                    continue;
                }
                Map<String, Object> cityMap = toStringObjectMap(typeEntry.getValue());
                LinkedHashMap<String, Object> filteredCities = new LinkedHashMap<>();
                for (Map.Entry<String, Object> cityEntry : cityMap.entrySet()) {
                    String city = normalizeKey(cityEntry.getKey());
                    if (!StringUtils.hasText(city) || isClosed(statuses, "city", business, locationType, city)) {
                        continue;
                    }
                    List<String> filteredLocations = toStringList(cityEntry.getValue()).stream()
                            .filter(StringUtils::hasText)
                            .filter(location -> !isClosed(statuses, "location", business, locationType, city, location))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                    if (!filteredLocations.isEmpty()) {
                        filteredCities.put(city, filteredLocations);
                    }
                }
                if (!filteredCities.isEmpty()) {
                    filteredTypes.put(locationType, filteredCities);
                }
            }
            if (!filteredTypes.isEmpty()) {
                filteredTree.put(business, filteredTypes);
            }
        }
        return filteredTree;
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

    private List<String> toStringList(Object rawValue) {
        if (rawValue instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object value : iterable) {
                String normalized = normalizeKey(value);
                if (StringUtils.hasText(normalized)) {
                    result.add(normalized);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                String normalizedKey = normalizeKey(key);
                if (StringUtils.hasText(normalizedKey)) {
                    result.put(normalizedKey, value);
                }
            });
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> fieldPayload(String label,
                                             List<String> options,
                                             Map<String, Object> optionDependencies,
                                             Object tree) {
        LinkedHashMap<String, Object> field = new LinkedHashMap<>();
        field.put("label", label);
        field.put("options", options == null ? List.of() : options);
        if (optionDependencies != null && !optionDependencies.isEmpty()) {
            field.put("option_dependencies", optionDependencies);
        }
        if (tree instanceof Map<?, ?> map && !map.isEmpty()) {
            field.put("tree", map);
        }
        return field;
    }

    private Map<String, Object> buildLocationTypeTree(Map<String, Set<String>> typesByBusiness) {
        LinkedHashMap<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : typesByBusiness.entrySet()) {
            tree.put(entry.getKey(), toSortedList(entry.getValue()));
        }
        return tree;
    }

    private Map<String, Object> buildCityTree(Map<List<String>, Set<String>> citiesByPath) {
        LinkedHashMap<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Set<String>> entry : citiesByPath.entrySet()) {
            List<String> path = entry.getKey();
            if (path.size() != 2) {
                continue;
            }
            String business = path.get(0);
            String locationType = path.get(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> businessNode = (Map<String, Object>) tree.computeIfAbsent(business, key -> new LinkedHashMap<>());
            businessNode.put(locationType, toSortedList(entry.getValue()));
        }
        return tree;
    }

    private Map<String, Object> buildLocationNameTree(Map<List<String>, Set<String>> locationsByPath) {
        LinkedHashMap<String, Object> tree = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Set<String>> entry : locationsByPath.entrySet()) {
            List<String> path = entry.getKey();
            if (path.size() != 3) {
                continue;
            }
            String business = path.get(0);
            String locationType = path.get(1);
            String city = path.get(2);
            @SuppressWarnings("unchecked")
            Map<String, Object> businessNode = (Map<String, Object>) tree.computeIfAbsent(business, key -> new LinkedHashMap<>());
            @SuppressWarnings("unchecked")
            Map<String, Object> typeNode = (Map<String, Object>) businessNode.computeIfAbsent(locationType, key -> new LinkedHashMap<>());
            typeNode.put(city, toSortedList(entry.getValue()));
        }
        return tree;
    }

    private Map<String, Object> finalizeDependencies(Map<String, Map<String, Set<Object>>> dependencies) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (dependencies == null) {
            return result;
        }
        for (Map.Entry<String, Map<String, Set<Object>>> optionEntry : dependencies.entrySet()) {
            LinkedHashMap<String, Object> optionPayload = new LinkedHashMap<>();
            for (Map.Entry<String, Set<Object>> dependencyEntry : optionEntry.getValue().entrySet()) {
                if ("paths".equals(dependencyEntry.getKey())) {
                    List<Map<String, String>> paths = finalizePathDependencies(dependencyEntry.getValue());
                    if (!paths.isEmpty()) {
                        optionPayload.put("paths", paths);
                    }
                    continue;
                }
                List<String> values = dependencyEntry.getValue().stream()
                        .map(String::valueOf)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                if (!values.isEmpty()) {
                    optionPayload.put(dependencyEntry.getKey(), values);
                }
            }
            if (!optionPayload.isEmpty()) {
                result.put(optionEntry.getKey(), optionPayload);
            }
        }
        return result;
    }

    private List<Map<String, String>> finalizePathDependencies(Set<Object> rawPaths) {
        if (rawPaths == null || rawPaths.isEmpty()) {
            return List.of();
        }
        if (rawPaths.stream().allMatch(item -> item instanceof List<?> list && list.size() == 2)) {
            return rawPaths.stream()
                    .map(item -> (List<?>) item)
                    .map(list -> Map.of(
                            "business", String.valueOf(list.get(0)),
                            "location_type", String.valueOf(list.get(1))))
                    .sorted(Comparator.comparing(map -> map.get("business") + "|" + map.get("location_type")))
                    .collect(Collectors.toList());
        }
        if (rawPaths.stream().allMatch(item -> item instanceof List<?> list && list.size() == 3)) {
            return rawPaths.stream()
                    .map(item -> (List<?>) item)
                    .map(list -> Map.of(
                            "business", String.valueOf(list.get(0)),
                            "location_type", String.valueOf(list.get(1)),
                            "city", String.valueOf(list.get(2))))
                    .sorted(Comparator.comparing(map -> map.get("business") + "|" + map.get("location_type") + "|" + map.get("city")))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private String normalizeKey(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
}

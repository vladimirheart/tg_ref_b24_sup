package com.example.panel.passports.api;

import com.example.panel.passports.ObjectPassportService;
import com.example.panel.entity.ItEquipmentCatalog;
import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SettingsParameterService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ObjectPassportPageController {

    private static final Logger log = LoggerFactory.getLogger(ObjectPassportPageController.class);
    private static final List<String> DEFAULT_OBJECT_PASSPORT_STATUSES = List.of(
            "Новый",
            "Active",
            "Стройка",
            "Закрыт",
            "Заморожен",
            "Форс-мажор",
            "Удалён"
    );

    private final NavigationService navigationService;
    private final ItEquipmentCatalogRepository equipmentRepository;
    private final ObjectPassportService objectPassportService;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final SettingsParameterService settingsParameterService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final ObjectMapper objectMapper;

    public ObjectPassportPageController(NavigationService navigationService,
                                        ItEquipmentCatalogRepository equipmentRepository,
                                        ObjectPassportService objectPassportService,
                                        SharedConfigService sharedConfigService,
                                        SettingsCatalogService settingsCatalogService,
                                        SettingsParameterService settingsParameterService,
                                        IikoDepartmentLocationCatalogService locationCatalogService,
                                        ObjectMapper objectMapper) {
        this.navigationService = navigationService;
        this.equipmentRepository = equipmentRepository;
        this.objectPassportService = objectPassportService;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.settingsParameterService = settingsParameterService;
        this.locationCatalogService = locationCatalogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/object-passports")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passports(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<Map<String, Object>> items = objectPassportService.listPassports();
            model.addAttribute("items", items);
            log.info("Loaded {} object passports for user {}", items.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load object passports page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "passports/list";
    }

    @GetMapping("/object-passports/new")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String newPassport(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        populatePassportEditor(model, true);
        return "passports/new";
    }

    @GetMapping("/object-passports/{id}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportDetails(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        populatePassportEditor(model, false);
        model.addAttribute("passportEditMode", false);
        return "passports/detail";
    }

    @GetMapping("/object-passports/{id}/edit")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportEdit(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        populatePassportEditor(model, false);
        model.addAttribute("passportEditMode", true);
        return "passports/detail";
    }

    @GetMapping("/object-passports/{id}/legacy-edit")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportLegacyEdit(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        populatePassportEditor(model, false);
        return "passports/new";
    }

    private void populatePassportEditor(Model model, boolean isNew) {
        Map<String, String> parameterTypes = settingsCatalogService.getParameterTypes();
        Map<String, List<String>> parameterDependencies = settingsCatalogService.getParameterDependencies();
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> passportLocationsPayload = loadPassportLocationsPayload();
        Map<String, Object> passportLocationTree = normalizeObjectMap(passportLocationsPayload.get("tree"));

        Map<String, List<Map<String, Object>>> parameterValuesPayload =
                buildPassportParameterPayload(parameterTypes.keySet(), settings, passportLocationsPayload);
        Map<String, List<String>> parameterValues = buildPassportParameterValues(parameterValuesPayload);

        List<String> statuses = toStringList(settings.get("object_statuses"));
        if (statuses.isEmpty()) {
            statuses = toStringList(settings.get("client_statuses"));
        }
        if (statuses.isEmpty()) {
            statuses = DEFAULT_OBJECT_PASSPORT_STATUSES;
        } else {
            LinkedHashSet<String> mergedStatuses = new LinkedHashSet<>();
            mergedStatuses.addAll(DEFAULT_OBJECT_PASSPORT_STATUSES);
            mergedStatuses.addAll(statuses);
            statuses = List.copyOf(mergedStatuses);
        }
        List<String> statusesRequiringTask = toStringList(settings.get("statuses_requiring_task"));

        List<Map<String, String>> dayLabels = List.of(
            Map.of("key", "mon", "full", "Понедельник", "short", "Пн"),
            Map.of("key", "tue", "full", "Вторник", "short", "Вт"),
            Map.of("key", "wed", "full", "Среда", "short", "Ср"),
            Map.of("key", "thu", "full", "Четверг", "short", "Чт"),
            Map.of("key", "fri", "full", "Пятница", "short", "Пт"),
            Map.of("key", "sat", "full", "Суббота", "short", "Сб"),
            Map.of("key", "sun", "full", "Воскресенье", "short", "Вс")
        );

        List<ItEquipmentCatalog> equipmentItems = equipmentRepository.findAll();
        List<Map<String, Object>> equipmentCatalog = equipmentItems.stream()
            .map(item -> {
                Map<String, Object> catalogItem = new LinkedHashMap<>();
                catalogItem.put("id", item.getId());
                catalogItem.put("equipment_type", item.getEquipmentType());
                catalogItem.put("equipment_vendor", item.getEquipmentVendor());
                catalogItem.put("equipment_model", item.getEquipmentModel());
                catalogItem.put("serial_number", item.getSerialNumber());
                catalogItem.put("photo_url", item.getPhotoUrl());
                catalogItem.put("accessories", item.getAccessories());
                return catalogItem;
            })
            .toList();

        Map<String, Object> itEquipmentOptions = new java.util.LinkedHashMap<>();
        itEquipmentOptions.put("types", equipmentItems.stream()
            .map(ItEquipmentCatalog::getEquipmentType)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList());
        itEquipmentOptions.put("vendors", equipmentItems.stream()
            .map(ItEquipmentCatalog::getEquipmentVendor)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList());
        itEquipmentOptions.put("models", equipmentItems.stream()
            .map(ItEquipmentCatalog::getEquipmentModel)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList());
        itEquipmentOptions.put("serials", equipmentItems.stream()
            .map(ItEquipmentCatalog::getSerialNumber)
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList());
        itEquipmentOptions.put("statuses", statuses);

        String passportPayloadJson = "{\"is_new\":true}";
        try {
            passportPayloadJson = objectMapper.writeValueAsString(Map.of("is_new", isNew));
        } catch (Exception ex) {
            log.warn("Failed to serialize passport payload: {}", ex.getMessage());
        }

        model.addAttribute("parameterTypes", parameterTypes);
        model.addAttribute("parameterDependencies", parameterDependencies);
        model.addAttribute("parameterValues", parameterValues);
        model.addAttribute("parameterValuesPayload", parameterValuesPayload);
        model.addAttribute("statuses", statuses);
        model.addAttribute("statusesRequiringTask", statusesRequiringTask);
        model.addAttribute("dayLabels", dayLabels);
        model.addAttribute("networkProfiles", toObjectList(settings.get("network_profiles")));
        model.addAttribute("itEquipmentOptions", itEquipmentOptions);
        model.addAttribute("itEquipmentCatalog", equipmentCatalog);
        model.addAttribute("itConnectionOptions", toObjectList(settings.get("it_connection_options")));
        model.addAttribute("iikoServerOptions", toStringList(settings.get("iiko_server_options")));
        model.addAttribute("networkProviderOptions", toStringList(settings.get("network_provider_options")));
        model.addAttribute("networkContractOptions", toStringList(settings.get("network_contract_options")));
        model.addAttribute("networkRestaurantIdOptions", toStringList(settings.get("network_restaurant_id_options")));
        model.addAttribute("networkSupportPhoneOptions", toStringList(settings.get("network_support_phone_options")));
        model.addAttribute("networkSpeedOptions", toStringList(settings.get("network_speed_options")));
        model.addAttribute("networkLegalEntityOptions", toStringList(settings.get("network_legal_entity_options")));
        model.addAttribute("cities", settingsCatalogService.collectCities(passportLocationTree));
        model.addAttribute("passportPayloadJson", passportPayloadJson);
    }

    private Map<String, Object> loadPassportLocationsPayload() {
        JsonNode configuredLocations = sharedConfigService.loadLocations();
        Map<String, Object> configuredPayload = configuredLocations != null && configuredLocations.isObject()
                ? normalizeObjectMap(objectMapper.convertValue(configuredLocations, Map.class))
                : Map.of();
        if (!normalizeObjectMap(configuredPayload.get("tree")).isEmpty()) {
            return configuredPayload;
        }

        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot effectiveCatalog = locationCatalogService.loadCatalog();
        return locationCatalogService.buildEffectiveLocationsPayload(effectiveCatalog);
    }

    private Map<String, List<Map<String, Object>>> buildPassportParameterPayload(Set<String> parameterKeys,
                                                                                 Map<String, Object> settings,
                                                                                 Map<String, Object> effectiveLocationsPayload) {
        Map<String, Object> grouped = settingsParameterService.listParameters(false);
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        parameterKeys.forEach(key -> result.put(key, normalizeParameterItems(grouped.get(key))));
        mergePassportLocationParameters(result, effectiveLocationsPayload);

        List<Map<String, Object>> legalEntityItems =
                result.computeIfAbsent("legal_entity", key -> new java.util.ArrayList<>());
        LinkedHashSet<String> legalEntities = new LinkedHashSet<>();
        legalEntityItems.forEach(item -> legalEntities.add(normalizeText(item.get("value"))));
        toStringList(settings.get("network_legal_entity_options")).forEach(value -> {
            if (legalEntities.add(value)) {
                legalEntityItems.add(parameterOption(value, Map.of(), Map.of("source", "network_legal_entity_options")));
            }
        });
        toObjectList(settings.get("network_profiles")).forEach(raw -> {
            if (!(raw instanceof Map<?, ?> profile)) {
                return;
            }
            String value = normalizeText(profile.get("legal_entity"));
            if (legalEntities.add(value)) {
                legalEntityItems.add(parameterOption(value, Map.of(), Map.of("source", "network_profiles")));
            }
        });
        sanitizePassportParameterPayload(result);
        return result;
    }

    private void mergePassportLocationParameters(Map<String, List<Map<String, Object>>> payload,
                                                 Map<String, Object> effectiveLocationsPayload) {
        Map<String, Object> tree = normalizeObjectMap(effectiveLocationsPayload.get("tree"));
        if (tree.isEmpty()) {
            return;
        }
        Map<String, Map<String, String>> cityMeta = readLocationMetaMap(effectiveLocationsPayload.get("city_meta"));
        Map<String, Map<String, String>> locationMeta = readLocationMetaMap(effectiveLocationsPayload.get("location_meta"));

        List<Map<String, Object>> countryItems = new java.util.ArrayList<>();
        List<Map<String, Object>> partnerTypeItems = new java.util.ArrayList<>();
        List<Map<String, Object>> businessItems = new java.util.ArrayList<>();
        List<Map<String, Object>> cityItems = new java.util.ArrayList<>();
        List<Map<String, Object>> departmentItems = new java.util.ArrayList<>();

        Set<String> countryKeys = new LinkedHashSet<>();
        Set<String> partnerTypeKeys = new LinkedHashSet<>();
        Set<String> businessKeys = new LinkedHashSet<>();
        Set<String> cityKeys = new LinkedHashSet<>();
        Set<String> departmentKeys = new LinkedHashSet<>();

        tree.forEach((businessKey, typesRaw) -> {
            String business = normalizeText(businessKey);
            if (business == null || business.isBlank()) {
                return;
            }
            Map<String, Object> types = normalizeObjectMap(typesRaw);
            types.forEach((typeKey, citiesRaw) -> {
                String fallbackPartnerType = normalizeText(typeKey);
                Map<String, Object> cities = normalizeObjectMap(citiesRaw);
                cities.forEach((cityKey, locationsRaw) -> {
                    String city = normalizeText(cityKey);
                    if (city == null || city.isBlank()) {
                        return;
                    }
                    String cityPath = String.join("::", business, fallbackPartnerType == null ? "" : fallbackPartnerType, city);
                    Map<String, String> cityAttrs = cityMeta.getOrDefault(cityPath, Map.of());
                    String country = normalizeText(cityAttrs.get("country"));
                    String partnerType = normalizeText(cityAttrs.get("partner_type"));
                    if (partnerType == null || partnerType.isBlank()) {
                        partnerType = fallbackPartnerType;
                    }

                    addParameterOption(countryItems, countryKeys, country, Map.of(), "effective_locations");
                    addParameterOption(
                            partnerTypeItems,
                            partnerTypeKeys,
                            partnerType,
                            buildDependencies(Map.of("country", country)),
                            "effective_locations");
                    addParameterOption(
                            businessItems,
                            businessKeys,
                            business,
                            buildDependencies(Map.of(
                                    "country", country,
                                    "partner_type", partnerType)),
                            "effective_locations");
                    addParameterOption(
                            cityItems,
                            cityKeys,
                            city,
                            buildDependencies(Map.of(
                                    "country", country,
                                    "partner_type", partnerType,
                                    "business", business)),
                            "effective_locations");

                    if (!(locationsRaw instanceof List<?> locations)) {
                        return;
                    }
                    for (Object locationRaw : locations) {
                        String department = normalizeText(locationRaw);
                        if (department == null || department.isBlank()) {
                            continue;
                        }
                        String locationPath = String.join(
                                "::",
                                business,
                                fallbackPartnerType == null ? "" : fallbackPartnerType,
                                city,
                                department);
                        Map<String, String> locationAttrs = locationMeta.getOrDefault(locationPath, Map.of());
                        String locationCountry = normalizeText(locationAttrs.get("country"));
                        if (locationCountry == null || locationCountry.isBlank()) {
                            locationCountry = country;
                        }
                        String locationPartnerType = normalizeText(locationAttrs.get("partner_type"));
                        if (locationPartnerType == null || locationPartnerType.isBlank()) {
                            locationPartnerType = partnerType;
                        }

                        addParameterOption(countryItems, countryKeys, locationCountry, Map.of(), "effective_locations");
                        addParameterOption(
                                partnerTypeItems,
                                partnerTypeKeys,
                                locationPartnerType,
                                buildDependencies(Map.of("country", locationCountry)),
                                "effective_locations");
                        addParameterOption(
                                businessItems,
                                businessKeys,
                                business,
                                buildDependencies(Map.of(
                                        "country", locationCountry,
                                        "partner_type", locationPartnerType)),
                                "effective_locations");
                        addParameterOption(
                                departmentItems,
                                departmentKeys,
                                department,
                                buildDependencies(Map.of(
                                        "country", locationCountry,
                                        "partner_type", locationPartnerType,
                                        "business", business,
                                        "city", city)),
                                "effective_locations");
                    }
                });
            });
        });

        overrideParameterItems(payload, "country", countryItems);
        overrideParameterItems(payload, "partner_type", partnerTypeItems);
        overrideParameterItems(payload, "business", businessItems);
        overrideParameterItems(payload, "city", cityItems);
        overrideParameterItems(payload, "department", departmentItems);
    }

    private void overrideParameterItems(Map<String, List<Map<String, Object>>> payload,
                                        String key,
                                        List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        payload.put(key, items);
    }

    private void addParameterOption(List<Map<String, Object>> target,
                                    Set<String> uniquenessGuard,
                                    String value,
                                    Map<String, String> dependencies,
                                    String source) {
        if (value == null || value.isBlank()) {
            return;
        }
        Map<String, String> normalizedDependencies = dependencies != null ? dependencies : Map.of();
        String signature = value + "|" + normalizedDependencies.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        if (!uniquenessGuard.add(signature)) {
            return;
        }
        target.add(parameterOption(
                value,
                normalizedDependencies,
                Map.of("source", source)));
    }

    private Map<String, String> buildDependencies(Map<String, String> values) {
        Map<String, String> dependencies = new LinkedHashMap<>();
        if (values == null) {
            return dependencies;
        }
        values.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            String normalizedValue = normalizeText(value);
            if (normalizedKey != null && !normalizedKey.isBlank()
                    && normalizedValue != null && !normalizedValue.isBlank()) {
                dependencies.put(normalizedKey, normalizedValue);
            }
        });
        return dependencies;
    }

    private Map<String, Map<String, String>> readLocationMetaMap(Object raw) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        map.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            if (normalizedKey == null || normalizedKey.isBlank()) {
                return;
            }
            result.put(normalizedKey, normalizeStringMap(value));
        });
        return result;
    }

    private Map<String, List<String>> buildPassportParameterValues(Map<String, List<Map<String, Object>>> payload) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        payload.forEach((key, items) -> result.put(
                key,
                items.stream()
                        .filter(item -> !asBoolean(item.get("is_deleted")))
                        .map(item -> normalizeText(item.get("value")))
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList()
        ));
        return result;
    }

    private List<Map<String, Object>> normalizeParameterItems(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object itemRaw : list) {
            if (!(itemRaw instanceof Map<?, ?> item)) {
                continue;
            }
            String value = normalizeText(item.get("value"));
            if (value == null || value.isBlank()) {
                continue;
            }
            Map<String, String> dependencies = normalizeStringMap(item.get("dependencies"));
            Map<String, Object> extra = normalizeObjectMap(item.get("extra"));
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("id", item.get("id"));
            normalized.put("value", value);
            normalized.put("state", normalizeText(item.get("state")));
            normalized.put("is_deleted", asBoolean(item.get("is_deleted")));
            normalized.put("dependencies", dependencies);
            normalized.put("extra", extra);
            result.add(normalized);
        }
        return result;
    }

    private Map<String, Object> parameterOption(String value,
                                                Map<String, String> dependencies,
                                                Map<String, Object> extra) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("state", "Активен");
        item.put("is_deleted", false);
        item.put("dependencies", dependencies != null ? dependencies : Map.of());
        item.put("extra", extra != null ? extra : Map.of());
        return item;
    }

    private void sanitizePassportParameterPayload(Map<String, List<Map<String, Object>>> payload) {
        LinkedHashSet<String> businesses = new LinkedHashSet<>();
        payload.getOrDefault("business", List.of()).forEach(item -> {
            String value = normalizeText(item.get("value"));
            if (value != null && !value.isBlank()) {
                businesses.add(value);
            }
        });
        payload.computeIfPresent("city", (key, items) -> items.stream()
                .filter(item -> !looksBrokenCityValue(normalizeText(item.get("value")), businesses))
                .toList());
        payload.computeIfPresent("department", (key, items) -> items.stream()
                .filter(item -> !looksBrokenDepartmentValue(normalizeText(item.get("value"))))
                .toList());
    }

    private boolean looksBrokenCityValue(String value, Set<String> businesses) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!startsWithLetterOrDigit(value)) {
            return true;
        }
        return businesses != null && businesses.contains(value);
    }

    private boolean looksBrokenDepartmentValue(String value) {
        return value == null || value.isBlank() || !startsWithLetterOrDigit(value);
    }

    private Map<String, String> normalizeStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            String normalizedValue = normalizeText(value);
            if (normalizedKey != null && !normalizedKey.isBlank() && normalizedValue != null && !normalizedValue.isBlank()) {
                result.put(normalizedKey, normalizedValue);
            }
        });
        return result;
    }

    private Map<String, Object> normalizeObjectMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            String normalizedKey = normalizeText(key);
            if (normalizedKey != null && !normalizedKey.isBlank()) {
                result.put(normalizedKey, value);
            }
        });
        return result;
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number value) {
            return value.intValue() != 0;
        }
        if (raw instanceof String value) {
            return "true".equalsIgnoreCase(value) || "1".equals(value);
        }
        return false;
    }

    private String normalizeText(Object raw) {
        return raw == null ? null : raw.toString().trim();
    }

    private boolean startsWithLetterOrDigit(String value) {
        return value != null && !value.isBlank() && Character.isLetterOrDigit(value.charAt(0));
    }

    private List<String> toStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                .filter(item -> item != null && !item.toString().isBlank())
                .map(Object::toString)
                .distinct()
                .toList();
        }
        return List.of();
    }

    private List<Object> toObjectList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                .filter(item -> item != null)
                .map(item -> (Object) item)
                .toList();
        }
        return List.of();
    }}

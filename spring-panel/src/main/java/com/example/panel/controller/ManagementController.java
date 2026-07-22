package com.example.panel.controller;

import com.example.panel.entity.AppSetting;
import com.example.panel.entity.Channel;
import com.example.panel.entity.ItEquipmentCatalog;
import com.example.panel.entity.PanelUser;
import com.example.panel.entity.SettingsParameter;
import com.example.panel.entity.Task;
import com.example.panel.repository.AppSettingRepository;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.repository.SettingsParameterRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.service.AutoCloseConfigNormalizer;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.BotSettingsPayloadNormalizer;
import com.example.panel.service.ObjectPassportService;
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

import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Controller
public class ManagementController {

    private static final Logger log = LoggerFactory.getLogger(ManagementController.class);

    private final NavigationService navigationService;
    private final TaskRepository taskRepository;
    private final ChannelRepository channelRepository;
    private final PanelUserRepository panelUserRepository;
    private final AppSettingRepository appSettingRepository;
    private final SettingsParameterRepository settingsParameterRepository;
    private final ItEquipmentCatalogRepository equipmentRepository;
    private final ObjectPassportService objectPassportService;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final SettingsParameterService settingsParameterService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final PermissionService permissionService;
    private final AutoCloseConfigNormalizer autoCloseConfigNormalizer;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;
    private final ObjectMapper objectMapper;

    public ManagementController(NavigationService navigationService,
                                TaskRepository taskRepository,
                                ChannelRepository channelRepository,
                                PanelUserRepository panelUserRepository,
                                AppSettingRepository appSettingRepository,
                                SettingsParameterRepository settingsParameterRepository,
                                ItEquipmentCatalogRepository equipmentRepository,
                                ObjectPassportService objectPassportService,
                                SharedConfigService sharedConfigService,
                                SettingsCatalogService settingsCatalogService,
                                SettingsParameterService settingsParameterService,
                                IikoDepartmentLocationCatalogService locationCatalogService,
                                PermissionService permissionService,
                                AutoCloseConfigNormalizer autoCloseConfigNormalizer,
                                BotSettingsPayloadNormalizer botSettingsPayloadNormalizer,
                                ObjectMapper objectMapper) {
        this.navigationService = navigationService;
        this.taskRepository = taskRepository;
        this.channelRepository = channelRepository;
        this.panelUserRepository = panelUserRepository;
        this.appSettingRepository = appSettingRepository;
        this.settingsParameterRepository = settingsParameterRepository;
        this.equipmentRepository = equipmentRepository;
        this.objectPassportService = objectPassportService;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.settingsParameterService = settingsParameterService;
        this.locationCatalogService = locationCatalogService;
        this.permissionService = permissionService;
        this.autoCloseConfigNormalizer = autoCloseConfigNormalizer;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public String tasks(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<Task> tasks = taskRepository.findTop50ByOrderByCreatedAtDesc();
            model.addAttribute("tasks", tasks);
            model.addAttribute("users", panelUserRepository.findAll());
            log.info("Loaded {} tasks for user {}", tasks.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load tasks page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "tasks/index";
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('PAGE_CHANNELS')")
    public String channels(Authentication authentication, Model model) {
        if (permissionService.hasAuthority(authentication, "PAGE_SETTINGS")) {
            return "redirect:/settings?open=channels";
        }
        navigationService.enrich(model, authentication);
        try {
            List<Channel> channels = channelRepository.findAll();
            model.addAttribute("channels", channels);
            log.info("Loaded {} channels for user {}", channels.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load channels page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "channels/index";
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PAGE_USERS')")
    public String users(Authentication authentication, Model model) {
        if (permissionService.hasAuthority(authentication, "PAGE_SETTINGS")) {
            return "redirect:/settings?open=users";
        }
        navigationService.enrich(model, authentication);
        try {
            List<PanelUser> users = panelUserRepository.findAll();
            model.addAttribute("users", users);
            log.info("Loaded {} panel users for user {}", users.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load users page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "users/index";
    }


    @GetMapping("/users/{username}")
    @PreAuthorize("hasAuthority('PAGE_USERS')")
    public String userCard(@PathVariable String username, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        PanelUser user = panelUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        model.addAttribute("panelUser", user);
        return "users/detail";
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public String settings(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<AppSetting> appSettings = appSettingRepository.findAll();
            List<SettingsParameter> systemParameters = settingsParameterRepository.findAll();
            model.addAttribute("appSettings", appSettings);
            model.addAttribute("systemParameters", systemParameters);
            Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
            settings.put("auto_close_config", autoCloseConfigNormalizer.normalize(settings.get("auto_close_config")));
            settings.put("bot_settings", botSettingsPayloadNormalizer.normalize(
                    settings.get("bot_settings"),
                    settings.get("unblock_request_cooldown_minutes")));
            settings.remove("unblock_request_cooldown_minutes");
            model.addAttribute("settingsPayload", settings);
            model.addAttribute("autoCloseFallbackHours",
                    autoCloseConfigNormalizer.resolveFallbackHours(
                            settings.get("auto_close_config"),
                            settings.get("auto_close_hours")));
            model.addAttribute("dialogLegacyQuestionTemplateAudit",
                    buildDialogLegacyQuestionTemplateAudit(settings));
            IikoDepartmentLocationCatalogService.LocationCatalogSnapshot effectiveCatalog = locationCatalogService.loadCatalog();
            Map<String, Object> effectiveLocationsPayload = locationCatalogService.buildEffectiveLocationsPayload(effectiveCatalog);
            Map<String, Object> effectiveLocationTree = Map.of();
            if (effectiveLocationsPayload.get("tree") instanceof Map<?, ?> tree) {
                effectiveLocationTree = (Map<String, Object>) tree;
            }
            Map<String, Object> effectiveLocationStatuses = Map.of();
            if (effectiveLocationsPayload.get("statuses") instanceof Map<?, ?> statuses) {
                effectiveLocationStatuses = (Map<String, Object>) statuses;
            }
            model.addAttribute("cities", settingsCatalogService.collectCities(effectiveLocationTree));
            model.addAttribute("parameterTypes", settingsCatalogService.getParameterTypes());
            model.addAttribute("parameterDependencies", settingsCatalogService.getParameterDependencies());
            model.addAttribute("itConnectionCategories", settingsCatalogService.getItConnectionCategories(settings));
            model.addAttribute("itConnectionCategoryFields", settingsCatalogService.getItConnectionCategoryFields());
            model.addAttribute("botQuestionPresets",
                settingsCatalogService.buildLocationPresets(
                        effectiveLocationTree,
                        effectiveLocationStatuses));
            model.addAttribute("canPublishDialogMacros",
                canPublishDialogMacros(authentication, settings));
            log.info("Loaded settings for user {}: {} app settings, {} system parameters", authentication.getName(), appSettings.size(), systemParameters.size());
        } catch (Exception ex) {
            log.error("Failed to load settings page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "settings/index";
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
        return "passports/new";
    }

    private void populatePassportEditor(Model model, boolean isNew) {
        Map<String, String> parameterTypes = settingsCatalogService.getParameterTypes();
        Map<String, List<String>> parameterDependencies = settingsCatalogService.getParameterDependencies();
        Map<String, Object> settings = sharedConfigService.loadSettings();
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot effectiveCatalog = locationCatalogService.loadCatalog();
        Map<String, Object> effectiveLocationsPayload = locationCatalogService.buildEffectiveLocationsPayload(effectiveCatalog);
        Map<String, Object> effectiveLocationTree = normalizeObjectMap(effectiveLocationsPayload.get("tree"));

        Map<String, List<Map<String, Object>>> parameterValuesPayload =
                buildPassportParameterPayload(parameterTypes.keySet(), settings, effectiveLocationsPayload);
        Map<String, List<String>> parameterValues = buildPassportParameterValues(parameterValuesPayload);

        List<String> statuses = toStringList(settings.get("object_statuses"));
        if (statuses.isEmpty()) {
            statuses = toStringList(settings.get("client_statuses"));
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
            .map(item -> Map.<String, Object>of(
                "equipment_type", item.getEquipmentType(),
                "equipment_vendor", item.getEquipmentVendor(),
                "equipment_model", item.getEquipmentModel(),
                "serial_number", item.getSerialNumber(),
                "photo_url", item.getPhotoUrl(),
                "accessories", item.getAccessories()
            ))
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
        model.addAttribute("cities", settingsCatalogService.collectCities(effectiveLocationTree));
        model.addAttribute("passportPayloadJson", passportPayloadJson);
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

    private boolean canPublishDialogMacros(Authentication authentication, Map<String, Object> settings) {
        if (!permissionService.hasAuthority(authentication, "DIALOG_MACRO_PUBLISH")) {
            return false;
        }
        Set<String> allowedRoles = resolveMacroPublishAllowedRoles(settings);
        return permissionService.hasAnyRole(authentication, allowedRoles);
    }

    private Set<String> resolveMacroPublishAllowedRoles(Map<String, Object> settings) {
        if (settings == null) {
            return Set.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> dialogConfig)) {
            return Set.of();
        }
        Object allowedRaw = dialogConfig.get("macro_publish_allowed_roles");
        if (!(allowedRaw instanceof List<?> roles)) {
            return Set.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Map<String, Object> buildDialogLegacyQuestionTemplateAudit(Map<String, Object> settings) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("source_path", "dialog_config.question_templates");
        audit.put("classification", "legacy_operator_workspace");
        audit.put("bot_settings_path", "bot_settings.question_templates");
        audit.put("used_by_bot_runtime", false);
        audit.put("workspace_consumers", List.of(
                "settings-dialog-templates-runtime.js",
                "dialogs.js"
        ));
        audit.put("historical_snapshots_are_canonical", false);
        audit.put("historical_snapshot_paths", List.of(
                "temp-recovery/routing-migration-backup-2026-07-08_085737/settings.json"
        ));
        int templateCount = 0;
        if (settings != null) {
            Object dialogConfigRaw = settings.get("dialog_config");
            if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
                Object questionTemplatesRaw = dialogConfig.get("question_templates");
                if (questionTemplatesRaw instanceof List<?> questionTemplates) {
                    templateCount = questionTemplates.size();
                }
            }
        }
        audit.put("template_count", templateCount);
        return audit;
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
    }
}

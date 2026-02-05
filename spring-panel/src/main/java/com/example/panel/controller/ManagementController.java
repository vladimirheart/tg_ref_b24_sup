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
import com.example.panel.service.NavigationService;
import com.example.panel.service.SettingsCatalogService;
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
import java.util.Map;

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
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final ObjectMapper objectMapper;

    public ManagementController(NavigationService navigationService,
                                TaskRepository taskRepository,
                                ChannelRepository channelRepository,
                                PanelUserRepository panelUserRepository,
                                AppSettingRepository appSettingRepository,
                                SettingsParameterRepository settingsParameterRepository,
                                ItEquipmentCatalogRepository equipmentRepository,
                                SharedConfigService sharedConfigService,
                                SettingsCatalogService settingsCatalogService,
                                ObjectMapper objectMapper) {
        this.navigationService = navigationService;
        this.taskRepository = taskRepository;
        this.channelRepository = channelRepository;
        this.panelUserRepository = panelUserRepository;
        this.appSettingRepository = appSettingRepository;
        this.settingsParameterRepository = settingsParameterRepository;
        this.equipmentRepository = equipmentRepository;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
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
            var settings = sharedConfigService.loadSettings();
            JsonNode locationsPayload = sharedConfigService.loadLocations();
            Map<String, Object> locationsMap = locationsPayload != null && locationsPayload.isObject()
                ? objectMapper.convertValue(locationsPayload, Map.class)
                : Map.of();
            Map<String, Object> locationTree = Map.of();
            if (locationsMap.get("tree") instanceof Map<?, ?> tree) {
                locationTree = (Map<String, Object>) tree;
            }
            model.addAttribute("clientStatuses", settings.getOrDefault("client_statuses", List.of()));
            model.addAttribute("clientStatusColors", settings.getOrDefault("client_status_colors", Map.of()));
            model.addAttribute("settingsPayload", settings);
            model.addAttribute("locationsPayload", locationsMap);
            model.addAttribute("cities", settingsCatalogService.collectCities(locationTree));
            model.addAttribute("parameterTypes", settingsCatalogService.getParameterTypes());
            model.addAttribute("parameterDependencies", settingsCatalogService.getParameterDependencies());
            model.addAttribute("itConnectionCategories", settingsCatalogService.getItConnectionCategories(settings));
            model.addAttribute("itConnectionCategoryFields", settingsCatalogService.getItConnectionCategoryFields());
            Map<String, Object> locationStatuses = Map.of();
            if (locationsMap.get("statuses") instanceof Map<?, ?> statuses) {
                locationStatuses = (Map<String, Object>) statuses;
            }
            model.addAttribute("botQuestionPresets",
                settingsCatalogService.buildLocationPresets(locationTree, locationStatuses));
            model.addAttribute("contractUsage", Map.of());
            model.addAttribute("statusUsage", Map.of());
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
            List<ItEquipmentCatalog> items = equipmentRepository.findAll();
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

        Map<String, List<String>> parameterValues = new java.util.LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> parameterValuesPayload = new java.util.LinkedHashMap<>();
        parameterTypes.keySet().forEach(key -> {
            List<SettingsParameter> items = settingsParameterRepository.findByParamType(key);
            List<String> values = items.stream()
                .filter(item -> item.getDeleted() == null || !item.getDeleted())
                .map(SettingsParameter::getValue)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
            parameterValues.put(key, values);
            List<Map<String, Object>> payloadItems = values.stream()
                .map(value -> Map.<String, Object>of("value", value, "is_deleted", false))
                .toList();
            parameterValuesPayload.put(key, payloadItems);
        });

        Map<String, Object> settings = sharedConfigService.loadSettings();
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
        model.addAttribute("cities", toStringList(settings.get("cities")));
        model.addAttribute("passportPayloadJson", passportPayloadJson);
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

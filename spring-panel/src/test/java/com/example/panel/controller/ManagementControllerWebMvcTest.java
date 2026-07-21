package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.panel.repository.AppSettingRepository;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.repository.SettingsParameterRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.entity.PanelUser;
import com.example.panel.service.AutoCloseConfigNormalizer;
import com.example.panel.service.BotSettingsPayloadNormalizer;
import com.example.panel.service.NavigationService;
import com.example.panel.service.ObjectPassportService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.LocationsIikoServerSourceSettingsService;
import com.example.panel.service.LocationsIikoSyncSettingsService;
import com.example.panel.service.PanelUserPhotoService;
import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SettingsParameterService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.UnblockRequestService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ManagementController.class)
@AutoConfigureMockMvc
@Import({NavigationService.class, BotSettingsPayloadNormalizer.class, AutoCloseConfigNormalizer.class})
class ManagementControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskRepository taskRepository;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private PanelUserRepository panelUserRepository;

    @MockBean
    private AppSettingRepository appSettingRepository;

    @MockBean
    private SettingsParameterRepository settingsParameterRepository;

    @MockBean
    private ItEquipmentCatalogRepository equipmentRepository;

    @MockBean
    private ObjectPassportService objectPassportService;

    @MockBean
    private SharedConfigService sharedConfigService;

    @MockBean
    private SettingsCatalogService settingsCatalogService;

    @MockBean
    private SettingsParameterService settingsParameterService;

    @MockBean
    private LocationsIikoServerSourceSettingsService locationsIikoServerSourceSettingsService;

    @MockBean
    private LocationsIikoSyncSettingsService locationsIikoSyncSettingsService;

    @MockBean
    private IikoDepartmentLocationCatalogService locationCatalogService;

    @MockBean
    private PermissionService permissionService;

    @MockBean
    private UnblockRequestService unblockRequestService;

    @MockBean
    private PanelUserPhotoService panelUserPhotoService;

    @Test
    void settingsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        when(appSettingRepository.findAll()).thenReturn(List.of());
        when(settingsParameterRepository.findAll()).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-template-1", "timeout_hours", 1, "description", "legacy")
                        ),
                        "active_template_id", "auto-template-1"
                ),
                "bot_settings", Map.of(
                        "question_templates", List.of(
                                Map.of(
                                        "id", "template-1",
                                        "name", "Новый бот",
                                        "question_flow", List.of(
                                                Map.of("id", "question-1", "type", "custom", "text", "Как вас зовут?")
                                        )
                                )
                        ),
                        "rating_templates", List.of(
                                Map.of(
                                        "id", "rating-template-default",
                                        "name", "Базовый сценарий оценок",
                                        "prompt_text", "Пожалуйста, оцените качество ответа от 1 до 5.",
                                        "scale_size", 5,
                                        "responses", List.of(
                                                Map.of("value", 5, "text", "Красавчик! Спасибо за вашу оценку 5! Нам важно ваше мнение.")
                                        )
                                )
                        ),
                        "active_template_id", "template-1",
                        "active_rating_template_id", "rating-template-default"
                )
        ));
        when(locationsIikoServerSourceSettingsService.loadForClient(Map.of())).thenReturn(List.of());
        when(locationsIikoSyncSettingsService.loadForClient(Map.of())).thenReturn(Map.of("enabled", true, "interval_minutes", 5));
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot liveCatalog =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> effectiveLocationsPayload = Map.of(
                "tree", liveCatalog.tree(),
                "statuses", Map.of(),
                "city_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск", Map.of("country", "Россия", "partner_type", "Корпоративная сеть")),
                "location_meta", Map.of("БлинБери::Корпоративная сеть::Смоленск::Ленина 1", Map.of("country", "Россия", "partner_type", "Корпоративная сеть"))
        );
        when(locationCatalogService.loadCatalog()).thenReturn(liveCatalog);
        when(locationCatalogService.buildEffectiveLocationsPayload(liveCatalog)).thenReturn(effectiveLocationsPayload);
        when(settingsCatalogService.collectCities(liveCatalog.tree())).thenReturn(List.of("Смоленск"));
        when(settingsCatalogService.getParameterTypes()).thenReturn(Map.of());
        when(settingsCatalogService.getParameterDependencies()).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategories(Map.of())).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategoryFields()).thenReturn(Map.of());
        when(settingsCatalogService.buildLocationPresets(liveCatalog.tree(), Map.of())).thenReturn(Map.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);

        mockMvc.perform(get("/settings").with(user("operator").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"settings\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"channels\": {")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"botSettings\":")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"autoCloseConfig\":")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"botSettingsInitial\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"reportingConfigInitial\""))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"locationsInitial\""))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Смоленск")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"question_templates\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rating_templates\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"hours\":1")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"timeout_hours\":1"))))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"template-1\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rating-template-default\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Legacy-аудит bot settings будет показан здесь после загрузки настроек.")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Это legacy-секция для `dialog_config.question_templates`")));
    }

    @Test
    void settingsPageNormalizesLegacyBotSettingsIntoCanonicalBootstrapPayload() throws Exception {
        stubNavigationDefaults();
        when(appSettingRepository.findAll()).thenReturn(List.of());
        when(settingsParameterRepository.findAll()).thenReturn(List.of());
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "bot_settings", Map.of(
                        "question_flow", List.of(
                                Map.of("id", "legacy-question-1", "type", "custom", "text", "Старый вопрос")
                        ),
                        "rating_system", Map.of(
                                "prompt_text", "Оцените старый сценарий",
                                "scale_size", 5,
                                "responses", List.of(
                                        Map.of("value", 5, "text", "Старый ответ 5")
                                )
                        )
                )
        ));
        when(locationsIikoServerSourceSettingsService.loadForClient(Map.of())).thenReturn(List.of());
        when(locationsIikoSyncSettingsService.loadForClient(Map.of())).thenReturn(Map.of("enabled", true, "interval_minutes", 5));
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot liveCatalog =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> effectiveLocationsPayload = Map.of(
                "tree", liveCatalog.tree(),
                "statuses", Map.of(),
                "city_meta", Map.of(),
                "location_meta", Map.of()
        );
        when(locationCatalogService.loadCatalog()).thenReturn(liveCatalog);
        when(locationCatalogService.buildEffectiveLocationsPayload(liveCatalog)).thenReturn(effectiveLocationsPayload);
        when(settingsCatalogService.collectCities(liveCatalog.tree())).thenReturn(List.of("Смоленск"));
        when(settingsCatalogService.getParameterTypes()).thenReturn(Map.of());
        when(settingsCatalogService.getParameterDependencies()).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategories(Map.of("bot_settings", Map.of()))).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategories(any())).thenReturn(Map.of());
        when(settingsCatalogService.getItConnectionCategoryFields()).thenReturn(Map.of());
        when(settingsCatalogService.buildLocationPresets(liveCatalog.tree(), Map.of())).thenReturn(Map.of());
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);

        mockMvc.perform(get("/settings").with(user("operator").authorities(() -> "PAGE_SETTINGS")))
            .andExpect(status().isOk())
            .andExpect(view().name("settings/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"question_templates\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rating_templates\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"template-imported\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"rating-template-imported\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("\"legacy-question-1\"")));
    }

    @Test
    void tasksPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        when(taskRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(panelUserRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/tasks").with(user("operator").authorities(() -> "PAGE_TASKS")))
            .andExpect(status().isOk())
            .andExpect(view().name("tasks/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"tasks\"")));
    }

    @Test
    void channelsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        when(channelRepository.findAll()).thenReturn(List.of());
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(get("/channels").with(user("operator").authorities(() -> "PAGE_CHANNELS")))
            .andExpect(status().isOk())
            .andExpect(view().name("channels/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"channels\"")));
    }

    @Test
    void usersPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        when(panelUserRepository.findAll()).thenReturn(List.of());
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(false);

        mockMvc.perform(get("/users").with(user("operator").authorities(() -> "PAGE_USERS")))
            .andExpect(status().isOk())
            .andExpect(view().name("users/index"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"users\"")));
    }

    @Test
    void channelsPageRedirectsIntoSettingsWhenSettingsPageIsAvailable() throws Exception {
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(true);

        mockMvc.perform(get("/channels").with(user("operator").authorities(() -> "PAGE_CHANNELS")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings?open=channels"));
    }

    @Test
    void usersPageRedirectsIntoSettingsWhenSettingsPageIsAvailable() throws Exception {
        when(permissionService.hasAuthority(any(), eq("PAGE_SETTINGS"))).thenReturn(true);

        mockMvc.perform(get("/users").with(user("operator").authorities(() -> "PAGE_USERS")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/settings?open=users"));
    }

    @Test
    void userDetailPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        PanelUser panelUser = new PanelUser();
        panelUser.setUsername("operator");
        panelUser.setFullName("Оператор");
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.of(panelUser));

        mockMvc.perform(get("/users/operator").with(user("operator").authorities(() -> "PAGE_USERS")))
            .andExpect(status().isOk())
            .andExpect(view().name("users/detail"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"users\"")));
    }

    @Test
    void objectPassportsPageIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        when(objectPassportService.listPassports()).thenReturn(List.of());

        mockMvc.perform(get("/object-passports").with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("passports/list"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"passports\"")));
    }

    @Test
    void newObjectPassportEditorIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        stubPassportEditorDependencies();

        mockMvc.perform(get("/object-passports/new").with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("passports/new"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/common.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("meta name=\"_csrf\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"passports\"")));
    }

    @Test
    void newObjectPassportEditorUsesEffectiveLocationsCatalogForLocationFields() throws Exception {
        stubNavigationDefaults();
        stubPassportEditorDependencies();

        mockMvc.perform(get("/object-passports/new").with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("passports/new"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("БлинБери")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Корпоративная сеть")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Смоленск")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ленина 1")));
    }

    @Test
    void existingObjectPassportEditorIncludesUiHeadBootstrapAndExplicitPagePreset() throws Exception {
        stubNavigationDefaults();
        stubPassportEditorDependencies();

        mockMvc.perform(get("/object-passports/42").with(user("operator").authorities(() -> "PAGE_OBJECT_PASSPORTS")))
            .andExpect(status().isOk())
            .andExpect(view().name("passports/new"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-preferences.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/theme.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/ui-config.js")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ui-page=\"passports\"")));
    }

    private void stubPassportEditorDependencies() {
        Map<String, String> parameterTypes = new LinkedHashMap<>();
        parameterTypes.put("business", "Бизнес");
        parameterTypes.put("partner_type", "Тип партнёра");
        parameterTypes.put("country", "Страна");
        parameterTypes.put("city", "Город");
        parameterTypes.put("department", "Департамент");
        parameterTypes.put("legal_entity", "ЮЛ");
        when(settingsCatalogService.getParameterTypes()).thenReturn(parameterTypes);
        when(settingsCatalogService.getParameterDependencies()).thenReturn(Map.of(
                "partner_type", List.of("country"),
                "business", List.of("country", "partner_type"),
                "city", List.of("country", "partner_type", "business"),
                "department", List.of("country", "partner_type", "business", "city")
        ));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        when(settingsParameterService.listParameters(false)).thenReturn(Map.of());
        when(equipmentRepository.findAll()).thenReturn(List.of());
        IikoDepartmentLocationCatalogService.LocationCatalogSnapshot liveCatalog =
                new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                        Map.of("БлинБери", Map.of("Корпоративная сеть", Map.of("Смоленск", List.of("Ленина 1")))),
                        Map.of(),
                        "iiko_api",
                        false,
                        List.of()
                );
        Map<String, Object> effectiveLocationsPayload = Map.of(
                "tree", liveCatalog.tree(),
                "statuses", Map.of(),
                "city_meta", Map.of(
                        "БлинБери::Корпоративная сеть::Смоленск",
                        Map.of("country", "Россия", "partner_type", "Корпоративная сеть")),
                "location_meta", Map.of(
                        "БлинБери::Корпоративная сеть::Смоленск::Ленина 1",
                        Map.of("country", "Россия", "partner_type", "Корпоративная сеть"))
        );
        when(locationCatalogService.loadCatalog()).thenReturn(liveCatalog);
        when(locationCatalogService.buildEffectiveLocationsPayload(liveCatalog)).thenReturn(effectiveLocationsPayload);
        when(settingsCatalogService.collectCities(liveCatalog.tree())).thenReturn(List.of("Смоленск"));
    }

    private void stubNavigationDefaults() {
        when(permissionService.hasAuthority(any(), any())).thenReturn(false);
        when(panelUserRepository.findByUsernameIgnoreCase("operator")).thenReturn(Optional.empty());
        when(panelUserPhotoService.resolveUrl(any(), any())).thenReturn("/avatar_default.svg");
    }
}

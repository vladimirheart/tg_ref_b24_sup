package com.example.panel.settings;

import com.example.panel.entity.AppSetting;
import com.example.panel.entity.SettingsParameter;
import com.example.panel.repository.AppSettingRepository;
import com.example.panel.repository.ProjectRepository;
import com.example.panel.repository.SettingsParameterRepository;
import com.example.panel.service.AutoCloseConfigNormalizer;
import com.example.panel.service.BotSettingsPayloadNormalizer;
import com.example.panel.service.IikoDepartmentLocationCatalogService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class SettingsPageController {

    private static final Logger log = LoggerFactory.getLogger(SettingsPageController.class);
    private final NavigationService navigationService;
    private final AppSettingRepository appSettingRepository;
    private final ProjectRepository projectRepository;
    private final SettingsParameterRepository settingsParameterRepository;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;
    private final PermissionService permissionService;
    private final AutoCloseConfigNormalizer autoCloseConfigNormalizer;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;

    public SettingsPageController(NavigationService navigationService,
                                  AppSettingRepository appSettingRepository,
                                  ProjectRepository projectRepository,
                                  SettingsParameterRepository settingsParameterRepository,
                                  SharedConfigService sharedConfigService,
                                  SettingsCatalogService settingsCatalogService,
                                  IikoDepartmentLocationCatalogService locationCatalogService,
                                  PermissionService permissionService,
                                  AutoCloseConfigNormalizer autoCloseConfigNormalizer,
                                  BotSettingsPayloadNormalizer botSettingsPayloadNormalizer) {
        this.navigationService = navigationService;
        this.appSettingRepository = appSettingRepository;
        this.projectRepository = projectRepository;
        this.settingsParameterRepository = settingsParameterRepository;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.locationCatalogService = locationCatalogService;
        this.permissionService = permissionService;
        this.autoCloseConfigNormalizer = autoCloseConfigNormalizer;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
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
            Map<String, Object> normalizedAutoCloseConfig = autoCloseConfigNormalizer.normalize(settings.get("auto_close_config"));
            settings.put("auto_close_config", normalizedAutoCloseConfig);
            settings.put("bot_settings", botSettingsPayloadNormalizer.normalize(settings.get("bot_settings")));
            settings.remove("unblock_request_cooldown_minutes");
            model.addAttribute("settingsPayload", settings);
            model.addAttribute("autoCloseFallbackHours",
                    autoCloseConfigNormalizer.resolveFallbackHours(normalizedAutoCloseConfig));
            model.addAttribute("autoCloseFollowUpProjectId",
                    autoCloseConfigNormalizer.resolveFollowUpProjectId(normalizedAutoCloseConfig));
            model.addAttribute("autoCloseProjectOptions",
                    projectRepository.findAllByArchivedAtIsNullOrderByNameAsc());
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
            model.addAttribute("canRunStorageInventory", permissionService.isSuperUser(authentication));
            model.addAttribute("canPublishDialogMacros",
                canPublishDialogMacros(authentication, settings));
            log.info("Loaded settings for user {}: {} app settings, {} system parameters", authentication.getName(), appSettings.size(), systemParameters.size());
        } catch (Exception ex) {
            log.error("Failed to load settings page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "settings/index";
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

}

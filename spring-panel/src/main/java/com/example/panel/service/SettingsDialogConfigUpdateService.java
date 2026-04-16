package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogConfigUpdateService {

    private final SettingsDialogTemplateConfigService settingsDialogTemplateConfigService;
    private final SettingsDialogRuntimeConfigService settingsDialogRuntimeConfigService;
    private final SettingsDialogSlaAiConfigService settingsDialogSlaAiConfigService;
    private final SettingsDialogWorkspaceConfigService settingsDialogWorkspaceConfigService;
    private final SettingsDialogPublicFormConfigService settingsDialogPublicFormConfigService;
    private final SettingsDialogConfigRoutingService settingsDialogConfigRoutingService;
    private final SettingsDialogConfigSupportService settingsDialogConfigSupportService;

    public SettingsDialogConfigUpdateService(SettingsDialogTemplateConfigService settingsDialogTemplateConfigService,
                                             SettingsDialogRuntimeConfigService settingsDialogRuntimeConfigService,
                                             SettingsDialogSlaAiConfigService settingsDialogSlaAiConfigService,
                                             SettingsDialogWorkspaceConfigService settingsDialogWorkspaceConfigService,
                                             SettingsDialogPublicFormConfigService settingsDialogPublicFormConfigService,
                                             SettingsDialogConfigRoutingService settingsDialogConfigRoutingService,
                                             SettingsDialogConfigSupportService settingsDialogConfigSupportService) {
        this.settingsDialogTemplateConfigService = settingsDialogTemplateConfigService;
        this.settingsDialogRuntimeConfigService = settingsDialogRuntimeConfigService;
        this.settingsDialogSlaAiConfigService = settingsDialogSlaAiConfigService;
        this.settingsDialogWorkspaceConfigService = settingsDialogWorkspaceConfigService;
        this.settingsDialogPublicFormConfigService = settingsDialogPublicFormConfigService;
        this.settingsDialogConfigRoutingService = settingsDialogConfigRoutingService;
        this.settingsDialogConfigSupportService = settingsDialogConfigSupportService;
    }

    public boolean applyDialogConfigUpdates(Map<String, Object> payload,
                                            Map<String, Object> settings,
                                            Authentication authentication,
                                            List<String> updateWarnings) {
        if (payload == null || settings == null || !settingsDialogConfigRoutingService.hasDialogConfigUpdates(payload)) {
            return false;
        }

        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        Object existing = settings.get("dialog_config");
        if (existing instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> dialogConfig.put(String.valueOf(key), value));
        }

        settingsDialogTemplateConfigService.applySettings(payload, dialogConfig, authentication, updateWarnings);
        settingsDialogRuntimeConfigService.applySettings(payload, dialogConfig);
        settingsDialogSlaAiConfigService.applySettings(payload, dialogConfig);
        settingsDialogWorkspaceConfigService.applySettings(payload, dialogConfig, updateWarnings);
        settingsDialogPublicFormConfigService.applySettings(payload, dialogConfig);

        settingsDialogConfigSupportService.validateExternalKpiDatamartContract(dialogConfig);
        settings.put("dialog_config", dialogConfig);
        return true;
    }
}

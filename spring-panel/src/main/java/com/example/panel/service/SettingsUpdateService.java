package com.example.panel.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettingsUpdateService {

    private static final Logger log = LoggerFactory.getLogger(SettingsUpdateService.class);

    private final SharedConfigService sharedConfigService;
    private final SettingsDialogConfigUpdateService settingsDialogConfigUpdateService;
    private final SettingsTopLevelUpdateService settingsTopLevelUpdateService;
    private final SettingsLocationsUpdateService settingsLocationsUpdateService;
    private final QuestionTemplateSettingsService questionTemplateSettingsService;

    public SettingsUpdateService(SharedConfigService sharedConfigService,
                                 SettingsDialogConfigUpdateService settingsDialogConfigUpdateService,
                                 SettingsTopLevelUpdateService settingsTopLevelUpdateService,
                                 SettingsLocationsUpdateService settingsLocationsUpdateService,
                                 QuestionTemplateSettingsService questionTemplateSettingsService) {
        this.sharedConfigService = sharedConfigService;
        this.settingsDialogConfigUpdateService = settingsDialogConfigUpdateService;
        this.settingsTopLevelUpdateService = settingsTopLevelUpdateService;
        this.settingsLocationsUpdateService = settingsLocationsUpdateService;
        this.questionTemplateSettingsService = questionTemplateSettingsService;
    }

    public Map<String, Object> updateSettings(Map<String, Object> payload,
                                              Authentication authentication) {
        try {
            Map<String, Object> previous = sharedConfigService.loadSettings();
            Map<String, Object> settings = new LinkedHashMap<>(previous);
            List<String> updateWarnings = new ArrayList<>();

            boolean modified = settingsTopLevelUpdateService.applyTopLevelUpdates(payload, settings);
            modified |= settingsDialogConfigUpdateService.applyDialogConfigUpdates(
                    payload,
                    settings,
                    authentication,
                    updateWarnings
            );

            if (modified) {
                if (payload.containsKey("bot_settings")) {
                    questionTemplateSettingsService.saveSettings(previous, settings);
                } else {
                    sharedConfigService.saveSettings(settings);
                }
            }

            settingsLocationsUpdateService.applyLocationsUpdate(payload);

            if (!updateWarnings.isEmpty()) {
                return Map.of("success", true, "warnings", updateWarnings);
            }
            return Map.of("success", true);
        } catch (Exception ex) {
            log.error("Failed to update settings payload", ex);
            String message = ex.getMessage();
            if (!StringUtils.hasText(message)) {
                message = "Не удалось сохранить настройки. Проверьте журнал приложения.";
            }
            return Map.of("success", false, "error", message);
        }
    }
}

package com.example.panel.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogConfigUpdateService {

    private final SettingsMacroTemplateService settingsMacroTemplateService;
    private final SettingsDialogSlaAiConfigService settingsDialogSlaAiConfigService;
    private final SettingsDialogWorkspaceConfigService settingsDialogWorkspaceConfigService;
    private final SettingsDialogPublicFormConfigService settingsDialogPublicFormConfigService;
    private final SettingsDialogConfigSupportService settingsDialogConfigSupportService;

    public SettingsDialogConfigUpdateService(SettingsMacroTemplateService settingsMacroTemplateService,
                                             SettingsDialogSlaAiConfigService settingsDialogSlaAiConfigService,
                                             SettingsDialogWorkspaceConfigService settingsDialogWorkspaceConfigService,
                                             SettingsDialogPublicFormConfigService settingsDialogPublicFormConfigService,
                                             SettingsDialogConfigSupportService settingsDialogConfigSupportService) {
        this.settingsMacroTemplateService = settingsMacroTemplateService;
        this.settingsDialogSlaAiConfigService = settingsDialogSlaAiConfigService;
        this.settingsDialogWorkspaceConfigService = settingsDialogWorkspaceConfigService;
        this.settingsDialogPublicFormConfigService = settingsDialogPublicFormConfigService;
        this.settingsDialogConfigSupportService = settingsDialogConfigSupportService;
    }

    public boolean applyDialogConfigUpdates(Map<String, Object> payload,
                                            Map<String, Object> settings,
                                            Authentication authentication,
                                            List<String> updateWarnings) {
        if (payload == null || settings == null || !hasDialogConfigUpdates(payload)) {
            return false;
        }

        Map<String, Object> dialogConfig = new LinkedHashMap<>();
        Object existing = settings.get("dialog_config");
        if (existing instanceof Map<?, ?> existingMap) {
            existingMap.forEach((key, value) -> dialogConfig.put(String.valueOf(key), value));
        }

        applyTemplateSettings(payload, dialogConfig, authentication, updateWarnings);
        applyMacroGovernanceSettings(payload, dialogConfig);

        if (payload.containsKey("dialog_time_metrics")) {
            dialogConfig.put("time_metrics", payload.get("dialog_time_metrics"));
        }

        settingsDialogSlaAiConfigService.applySettings(payload, dialogConfig);
        settingsDialogWorkspaceConfigService.applySettings(payload, dialogConfig, updateWarnings);
        settingsDialogPublicFormConfigService.applySettings(payload, dialogConfig);

        settingsDialogConfigSupportService.validateExternalKpiDatamartContract(dialogConfig);
        settings.put("dialog_config", dialogConfig);
        return true;
    }

    private void applyTemplateSettings(Map<String, Object> payload,
                                       Map<String, Object> dialogConfig,
                                       Authentication authentication,
                                       List<String> updateWarnings) {
        if (payload.containsKey("dialog_category_templates")) {
            dialogConfig.put("category_templates", payload.get("dialog_category_templates"));
        }
        if (payload.containsKey("dialog_question_templates")) {
            dialogConfig.put("question_templates", payload.get("dialog_question_templates"));
        }
        if (payload.containsKey("dialog_completion_templates")) {
            dialogConfig.put("completion_templates", payload.get("dialog_completion_templates"));
        }
        if (payload.containsKey("dialog_macro_templates")) {
            SettingsMacroTemplateService.MacroNormalizationResult normalizationResult =
                    settingsMacroTemplateService.normalizeForSettingsUpdate(
                            authentication,
                            dialogConfig,
                            dialogConfig.get("macro_templates"),
                            payload.get("dialog_macro_templates")
                    );
            dialogConfig.put("macro_templates", normalizationResult.templates());
            updateWarnings.addAll(normalizationResult.warnings());
        }
    }

    private void applyMacroGovernanceSettings(Map<String, Object> payload,
                                              Map<String, Object> dialogConfig) {
        putIfPresent(payload, dialogConfig, "dialog_macro_publish_allowed_roles", "macro_publish_allowed_roles");
        putIfPresent(payload, dialogConfig, "dialog_macro_require_independent_review", "macro_require_independent_review");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_require_owner", "macro_governance_require_owner");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_require_namespace", "macro_governance_require_namespace");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_require_review", "macro_governance_require_review");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_review_ttl_hours", "macro_governance_review_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_deprecation_requires_reason", "macro_governance_deprecation_requires_reason");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_unused_days", "macro_governance_unused_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_red_list_enabled", "macro_governance_red_list_enabled");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_red_list_usage_max", "macro_governance_red_list_usage_max");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_owner_action_required", "macro_governance_owner_action_required");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_cleanup_cadence_days", "macro_governance_cleanup_cadence_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_alias_cleanup_required", "macro_governance_alias_cleanup_required");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_variable_cleanup_required", "macro_governance_variable_cleanup_required");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_usage_tier_sla_required", "macro_governance_usage_tier_sla_required");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_usage_tier_low_max", "macro_governance_usage_tier_low_max");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_usage_tier_medium_max", "macro_governance_usage_tier_medium_max");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_cleanup_sla_low_days", "macro_governance_cleanup_sla_low_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_cleanup_sla_medium_days", "macro_governance_cleanup_sla_medium_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_cleanup_sla_high_days", "macro_governance_cleanup_sla_high_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_deprecation_sla_low_days", "macro_governance_deprecation_sla_low_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_deprecation_sla_medium_days", "macro_governance_deprecation_sla_medium_days");
        putIfPresent(payload, dialogConfig, "dialog_macro_governance_deprecation_sla_high_days", "macro_governance_deprecation_sla_high_days");
    }

    private boolean hasDialogConfigUpdates(Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            if (key == null) {
                continue;
            }
            if (key.startsWith("dialog_sla_")
                    || key.startsWith("dialog_ai_agent_")
                    || key.startsWith("dialog_workspace_")
                    || key.startsWith("dialog_public_form_")
                    || key.startsWith("dialog_macro_")
                    || key.startsWith("dialog_cross_product_")) {
                return true;
            }
            if ("dialog_category_templates".equals(key)
                    || "dialog_question_templates".equals(key)
                    || "dialog_completion_templates".equals(key)
                    || "dialog_time_metrics".equals(key)
                    || "dialog_summary_badges".equals(key)
                    || "dialog_default_view".equals(key)
                    || "dialog_quick_snooze_minutes".equals(key)
                    || "dialog_overdue_threshold_hours".equals(key)
                    || "dialog_list_poll_interval_ms".equals(key)
                    || "dialog_history_poll_interval_ms".equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void putIfPresent(Map<String, Object> payload,
                              Map<String, Object> dialogConfig,
                              String payloadKey,
                              String configKey) {
        if (payload.containsKey(payloadKey)) {
            dialogConfig.put(configKey, payload.get(payloadKey));
        }
    }
}

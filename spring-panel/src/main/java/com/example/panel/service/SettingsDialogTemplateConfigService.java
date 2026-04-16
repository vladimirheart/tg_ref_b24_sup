package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogTemplateConfigService {

    private final SettingsMacroTemplateService settingsMacroTemplateService;

    public SettingsDialogTemplateConfigService(SettingsMacroTemplateService settingsMacroTemplateService) {
        this.settingsMacroTemplateService = settingsMacroTemplateService;
    }

    public void applySettings(Map<String, Object> payload,
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
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_defaults", "macro_variable_defaults");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog", "macro_variable_catalog");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_url", "macro_variable_catalog_external_url");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_timeout_ms", "macro_variable_catalog_external_timeout_ms");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_cache_ttl_seconds", "macro_variable_catalog_external_cache_ttl_seconds");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_auth_header", "macro_variable_catalog_external_auth_header");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_auth_token", "macro_variable_catalog_external_auth_token");
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

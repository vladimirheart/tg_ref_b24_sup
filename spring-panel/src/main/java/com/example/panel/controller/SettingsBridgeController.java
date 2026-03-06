package com.example.panel.controller;

import com.example.panel.service.SettingsCatalogService;
import com.example.panel.service.SharedConfigService;
import com.example.panel.service.PermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
public class SettingsBridgeController {

    private static final Logger log = LoggerFactory.getLogger(SettingsBridgeController.class);

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    private record MacroNormalizationResult(List<Map<String, Object>> templates,
                                            List<String> warnings) {}

    public SettingsBridgeController(JdbcTemplate jdbcTemplate,
                                    SharedConfigService sharedConfigService,
                                    SettingsCatalogService settingsCatalogService,
                                    PermissionService permissionService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = {"/settings", "/settings/"}, method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload,
                                              Authentication authentication) {
        try {
            Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
            boolean modified = false;
            List<String> updateWarnings = new ArrayList<>();

            if (payload.containsKey("auto_close_hours")) {
                Object raw = payload.get("auto_close_hours");
                settings.put("auto_close_hours", raw);
                modified = true;
            }

            if (payload.containsKey("auto_close_config")) {
                settings.put("auto_close_config", payload.get("auto_close_config"));
                modified = true;
            }

            if (payload.containsKey("categories")) {
                Object raw = payload.get("categories");
                List<String> categories = new ArrayList<>();
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        String value = item != null ? item.toString().trim() : "";
                        if (!value.isEmpty()) {
                            categories.add(value);
                        }
                    }
                }
                settings.put("categories", categories);
                modified = true;
            }

            if (payload.containsKey("client_statuses")) {
                List<String> statuses = new ArrayList<>();
                Object raw = payload.get("client_statuses");
                if (raw instanceof List<?> list) {
                    for (Object item : list) {
                        String value = item != null ? item.toString().trim() : "";
                        if (!value.isEmpty() && !statuses.contains(value)) {
                            statuses.add(value);
                        }
                    }
                }
                settings.put("client_statuses", statuses);
                modified = true;
            }

            if (payload.containsKey("client_status_colors")) {
                Map<String, String> colors = new LinkedHashMap<>();
                Object raw = payload.get("client_status_colors");
                if (raw instanceof Map<?, ?> map) {
                    map.forEach((key, value) -> {
                        String name = key != null ? key.toString().trim() : "";
                        String color = value != null ? value.toString().trim() : "";
                        if (StringUtils.hasText(name) && StringUtils.hasText(color)) {
                            colors.put(name, color);
                        }
                    });
                }
                settings.put("client_status_colors", colors);
                modified = true;
            }

            if (payload.containsKey("business_cell_styles")) {
                settings.put("business_cell_styles", payload.get("business_cell_styles"));
                modified = true;
            }

            if (payload.containsKey("network_profiles")) {
                settings.put("network_profiles", payload.get("network_profiles"));
                modified = true;
            }

            if (payload.containsKey("bot_settings")) {
                settings.put("bot_settings", payload.get("bot_settings"));
                modified = true;
            }

            if (payload.containsKey("dialog_category_templates")
                || payload.containsKey("dialog_question_templates")
                || payload.containsKey("dialog_completion_templates")
                || payload.containsKey("dialog_macro_templates")
                || payload.containsKey("dialog_time_metrics")
                || payload.containsKey("dialog_summary_badges")
                || payload.containsKey("dialog_sla_target_minutes")
                || payload.containsKey("dialog_sla_warning_minutes")
                || payload.containsKey("dialog_sla_critical_minutes")
                || payload.containsKey("dialog_sla_critical_orchestration_mode")
                || payload.containsKey("dialog_sla_critical_escalation_enabled")
                || payload.containsKey("dialog_sla_critical_auto_assign_enabled")
                || payload.containsKey("dialog_sla_critical_auto_assign_to")
                || payload.containsKey("dialog_sla_critical_auto_assign_max_per_run")
                || payload.containsKey("dialog_sla_critical_auto_assign_actor")
                || payload.containsKey("dialog_sla_critical_auto_assign_rules")
                || payload.containsKey("dialog_sla_critical_auto_assign_max_open_per_operator")
                || payload.containsKey("dialog_sla_critical_auto_assign_require_categories")
                || payload.containsKey("dialog_sla_critical_auto_assign_include_assigned")
                || payload.containsKey("dialog_sla_critical_operator_skills")
                || payload.containsKey("dialog_sla_critical_operator_queues")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_enabled")
                || payload.containsKey("dialog_sla_critical_escalation_include_assigned")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_urls")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_cooldown_minutes")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_timeout_ms")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_retry_attempts")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_retry_backoff_ms")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_event_name")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_severity")
                || payload.containsKey("dialog_sla_critical_escalation_webhook_max_tickets_per_run")
                || payload.containsKey("dialog_sla_window_presets_minutes")
                || payload.containsKey("dialog_sla_window_default_minutes")
                || payload.containsKey("dialog_default_view")
                || payload.containsKey("dialog_quick_snooze_minutes")
                || payload.containsKey("dialog_overdue_threshold_hours")
                || payload.containsKey("dialog_list_poll_interval_ms")
                || payload.containsKey("dialog_history_poll_interval_ms")
                || payload.containsKey("dialog_workspace_v1")
                || payload.containsKey("dialog_workspace_force_workspace")
                || payload.containsKey("dialog_workspace_decommission_legacy_modal")
                || payload.containsKey("dialog_sla_critical_auto_sort")
                || payload.containsKey("dialog_workspace_inline_navigation")
                || payload.containsKey("dialog_workspace_ab_enabled")
                || payload.containsKey("dialog_workspace_ab_rollout_percent")
                || payload.containsKey("dialog_workspace_ab_experiment_name")
                || payload.containsKey("dialog_workspace_ab_operator_segment")
                || payload.containsKey("dialog_workspace_ab_primary_kpis")
                || payload.containsKey("dialog_workspace_ab_secondary_kpis")
                || payload.containsKey("dialog_workspace_ab_operator_overrides")
                || payload.containsKey("dialog_workspace_contract_timeout_ms")
                || payload.containsKey("dialog_workspace_contract_retry_attempts")
                || payload.containsKey("dialog_workspace_contract_include")
                || payload.containsKey("dialog_workspace_messages_page_limit")
                || payload.containsKey("dialog_workspace_disable_legacy_fallback")
                || payload.containsKey("dialog_workspace_failure_streak_threshold")
                || payload.containsKey("dialog_workspace_failure_cooldown_ms")
                || payload.containsKey("dialog_workspace_draft_autosave_delay_ms")
                || payload.containsKey("dialog_workspace_draft_telemetry_interval_ms")
                || payload.containsKey("dialog_workspace_open_slo_ms")
                || payload.containsKey("dialog_workspace_guardrail_render_error_rate")
                || payload.containsKey("dialog_workspace_guardrail_fallback_rate")
                || payload.containsKey("dialog_workspace_guardrail_abandon_rate")
                || payload.containsKey("dialog_workspace_guardrail_slow_open_rate")
                || payload.containsKey("dialog_workspace_dimension_min_events")
                || payload.containsKey("dialog_workspace_cohort_min_events")
                || payload.containsKey("dialog_workspace_rollout_kpi_outcome_min_samples_per_cohort")
                || payload.containsKey("dialog_workspace_rollout_kpi_outcome_frt_max_relative_regression")
                || payload.containsKey("dialog_workspace_rollout_kpi_outcome_ttr_max_relative_regression")
                || payload.containsKey("dialog_workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta")
                || payload.containsKey("dialog_workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier")
                || payload.containsKey("dialog_workspace_rollout_required_outcome_kpis")
                || payload.containsKey("dialog_workspace_rollout_winner_min_open_improvement")
                || payload.containsKey("dialog_macro_variable_defaults")
                || payload.containsKey("dialog_macro_variable_catalog")
                || payload.containsKey("dialog_macro_variable_catalog_external_url")
                || payload.containsKey("dialog_macro_variable_catalog_external_timeout_ms")
                || payload.containsKey("dialog_macro_variable_catalog_external_cache_ttl_seconds")
                || payload.containsKey("dialog_macro_variable_catalog_external_auth_header")
                || payload.containsKey("dialog_macro_variable_catalog_external_auth_token")
                || payload.containsKey("dialog_macro_require_independent_review")
                || payload.containsKey("dialog_workspace_client_crm_profile_url_template")
                || payload.containsKey("dialog_workspace_client_crm_profile_label")
                || payload.containsKey("dialog_workspace_client_contract_profile_url_template")
                || payload.containsKey("dialog_workspace_client_contract_profile_label")
                || payload.containsKey("dialog_workspace_client_external_links")
                || payload.containsKey("dialog_workspace_client_external_profile_url")
                || payload.containsKey("dialog_workspace_client_external_profile_timeout_ms")
                || payload.containsKey("dialog_workspace_client_external_profile_cache_ttl_seconds")
                || payload.containsKey("dialog_workspace_client_external_profile_auth_header")
                || payload.containsKey("dialog_workspace_client_external_profile_auth_token")
                || payload.containsKey("dialog_cross_product_omnichannel_dashboard_url")
                || payload.containsKey("dialog_cross_product_omnichannel_dashboard_label")
                || payload.containsKey("dialog_cross_product_finance_dashboard_url")
                || payload.containsKey("dialog_cross_product_finance_dashboard_label")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_gate_enabled")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_omnichannel_ready")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_finance_ready")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_note")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_owner")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_runbook_url")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_url")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_by")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_review_ttl_hours")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_data_freshness_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_data_updated_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_data_freshness_ttl_hours")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_links_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status_note")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_owner_runbook_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_status")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_note")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_freshness_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_updated_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_ttl_hours")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_blocker_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_status")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_note")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_blocker_url")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_freshness_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_updated_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_ttl_hours")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_timeline_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_target_ready_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours")
                || payload.containsKey("dialog_workspace_client_hidden_attributes")
                || payload.containsKey("dialog_workspace_client_attribute_labels")
                || payload.containsKey("dialog_workspace_client_attribute_order")
                || payload.containsKey("dialog_workspace_client_extra_attributes_max")
                || payload.containsKey("dialog_workspace_client_extra_attributes_collapse_after")
                || payload.containsKey("dialog_workspace_client_extra_attributes_hide_technical")
                || payload.containsKey("dialog_workspace_client_extra_attributes_technical_prefixes")
                || payload.containsKey("dialog_workspace_context_history_limit")
                || payload.containsKey("dialog_workspace_context_related_events_limit")
                || payload.containsKey("dialog_workspace_segment_high_lifetime_volume_min_dialogs")
                || payload.containsKey("dialog_workspace_segment_multi_open_dialogs_min_open")
                || payload.containsKey("dialog_workspace_segment_reactivation_risk_min_dialogs")
                || payload.containsKey("dialog_workspace_segment_reactivation_risk_max_resolved_30d")
                || payload.containsKey("dialog_public_form_message_max_length")
                || payload.containsKey("dialog_public_form_answers_total_max_length")
                || payload.containsKey("dialog_public_form_session_ttl_hours")
                || payload.containsKey("dialog_public_form_idempotency_ttl_seconds")
                || payload.containsKey("dialog_public_form_rate_limit_enabled")
                || payload.containsKey("dialog_public_form_rate_limit_window_seconds")
                || payload.containsKey("dialog_public_form_rate_limit_max_requests")
                || payload.containsKey("dialog_public_form_rate_limit_use_fingerprint")
                || payload.containsKey("dialog_public_form_metrics_enabled")
                || payload.containsKey("dialog_public_form_captcha_shared_secret")
                || payload.containsKey("dialog_public_form_captcha_mode")
                || payload.containsKey("dialog_public_form_turnstile_secret_key")
                || payload.containsKey("dialog_public_form_turnstile_verify_url")
                || payload.containsKey("dialog_public_form_turnstile_timeout_ms")
                || payload.containsKey("dialog_public_form_session_polling_enabled")
                || payload.containsKey("dialog_public_form_session_polling_interval_seconds")
                || payload.containsKey("dialog_public_form_session_token_rotate_on_read")
                || payload.containsKey("dialog_public_form_default_locale")
                || payload.containsKey("dialog_macro_publish_allowed_roles")) {
                Map<String, Object> dialogConfig = new LinkedHashMap<>();
                Object existing = settings.get("dialog_config");
                if (existing instanceof Map<?, ?> existingMap) {
                    existingMap.forEach((key, value) -> dialogConfig.put(String.valueOf(key), value));
                }
                if (payload.containsKey("dialog_category_templates")) {
                    dialogConfig.put("category_templates", payload.get("dialog_category_templates"));
                }
                if (payload.containsKey("dialog_question_templates")) {
                    dialogConfig.put("question_templates", payload.get("dialog_question_templates"));
                }
                if (payload.containsKey("dialog_completion_templates")) {
                    dialogConfig.put("completion_templates", payload.get("dialog_completion_templates"));
                }
                if (payload.containsKey("dialog_macro_publish_allowed_roles")) {
                    dialogConfig.put("macro_publish_allowed_roles", payload.get("dialog_macro_publish_allowed_roles"));
                }
                if (payload.containsKey("dialog_macro_require_independent_review")) {
                    dialogConfig.put("macro_require_independent_review", payload.get("dialog_macro_require_independent_review"));
                }
                if (payload.containsKey("dialog_macro_templates")) {
                    boolean canPublishMacros = canPublishDialogMacros(authentication, dialogConfig);
                    boolean requireIndependentReview = resolveMacroIndependentReviewRequired(dialogConfig);
                    Object existingTemplates = dialogConfig.get("macro_templates");
                    MacroNormalizationResult normalizationResult = normalizeMacroTemplates(
                        existingTemplates,
                        payload.get("dialog_macro_templates"),
                        authentication != null ? authentication.getName() : "system",
                        canPublishMacros,
                        requireIndependentReview
                    );
                    dialogConfig.put("macro_templates", normalizationResult.templates());
                    updateWarnings.addAll(normalizationResult.warnings());
                }
                if (payload.containsKey("dialog_time_metrics")) {
                    dialogConfig.put("time_metrics", payload.get("dialog_time_metrics"));
                }
                if (payload.containsKey("dialog_sla_target_minutes")) {
                    dialogConfig.put("sla_target_minutes", payload.get("dialog_sla_target_minutes"));
                }
                if (payload.containsKey("dialog_sla_warning_minutes")) {
                    dialogConfig.put("sla_warning_minutes", payload.get("dialog_sla_warning_minutes"));
                }
                if (payload.containsKey("dialog_sla_critical_minutes")) {
                    dialogConfig.put("sla_critical_minutes", payload.get("dialog_sla_critical_minutes"));
                }
                if (payload.containsKey("dialog_sla_critical_orchestration_mode")) {
                    dialogConfig.put("sla_critical_orchestration_mode", payload.get("dialog_sla_critical_orchestration_mode"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_enabled")) {
                    dialogConfig.put("sla_critical_escalation_enabled", payload.get("dialog_sla_critical_escalation_enabled"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_enabled")) {
                    dialogConfig.put("sla_critical_auto_assign_enabled", payload.get("dialog_sla_critical_auto_assign_enabled"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_to")) {
                    dialogConfig.put("sla_critical_auto_assign_to", payload.get("dialog_sla_critical_auto_assign_to"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_max_per_run")) {
                    dialogConfig.put("sla_critical_auto_assign_max_per_run", payload.get("dialog_sla_critical_auto_assign_max_per_run"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_actor")) {
                    dialogConfig.put("sla_critical_auto_assign_actor", payload.get("dialog_sla_critical_auto_assign_actor"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_rules")) {
                    dialogConfig.put("sla_critical_auto_assign_rules", payload.get("dialog_sla_critical_auto_assign_rules"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_max_open_per_operator")) {
                    dialogConfig.put("sla_critical_auto_assign_max_open_per_operator", payload.get("dialog_sla_critical_auto_assign_max_open_per_operator"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_require_categories")) {
                    dialogConfig.put("sla_critical_auto_assign_require_categories", payload.get("dialog_sla_critical_auto_assign_require_categories"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_include_assigned")) {
                    dialogConfig.put("sla_critical_auto_assign_include_assigned", payload.get("dialog_sla_critical_auto_assign_include_assigned"));
                }
                if (payload.containsKey("dialog_sla_critical_operator_skills")) {
                    dialogConfig.put("sla_critical_operator_skills", payload.get("dialog_sla_critical_operator_skills"));
                }
                if (payload.containsKey("dialog_sla_critical_operator_queues")) {
                    dialogConfig.put("sla_critical_operator_queues", payload.get("dialog_sla_critical_operator_queues"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_enabled")) {
                    dialogConfig.put("sla_critical_escalation_webhook_enabled", payload.get("dialog_sla_critical_escalation_webhook_enabled"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_include_assigned")) {
                    dialogConfig.put("sla_critical_escalation_include_assigned", payload.get("dialog_sla_critical_escalation_include_assigned"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_urls")) {
                    dialogConfig.put("sla_critical_escalation_webhook_urls", payload.get("dialog_sla_critical_escalation_webhook_urls"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_cooldown_minutes")) {
                    dialogConfig.put("sla_critical_escalation_webhook_cooldown_minutes", payload.get("dialog_sla_critical_escalation_webhook_cooldown_minutes"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_timeout_ms")) {
                    dialogConfig.put("sla_critical_escalation_webhook_timeout_ms", payload.get("dialog_sla_critical_escalation_webhook_timeout_ms"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_retry_attempts")) {
                    dialogConfig.put("sla_critical_escalation_webhook_retry_attempts", payload.get("dialog_sla_critical_escalation_webhook_retry_attempts"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_retry_backoff_ms")) {
                    dialogConfig.put("sla_critical_escalation_webhook_retry_backoff_ms", payload.get("dialog_sla_critical_escalation_webhook_retry_backoff_ms"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_event_name")) {
                    dialogConfig.put("sla_critical_escalation_webhook_event_name", payload.get("dialog_sla_critical_escalation_webhook_event_name"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_severity")) {
                    dialogConfig.put("sla_critical_escalation_webhook_severity", payload.get("dialog_sla_critical_escalation_webhook_severity"));
                }
                if (payload.containsKey("dialog_sla_critical_escalation_webhook_max_tickets_per_run")) {
                    dialogConfig.put("sla_critical_escalation_webhook_max_tickets_per_run", payload.get("dialog_sla_critical_escalation_webhook_max_tickets_per_run"));
                }
                if (payload.containsKey("dialog_sla_window_presets_minutes")) {
                    dialogConfig.put("sla_window_presets_minutes", payload.get("dialog_sla_window_presets_minutes"));
                }
                if (payload.containsKey("dialog_sla_window_default_minutes")) {
                    dialogConfig.put("sla_window_default_minutes", payload.get("dialog_sla_window_default_minutes"));
                }
                if (payload.containsKey("dialog_default_view")) {
                    dialogConfig.put("default_view", payload.get("dialog_default_view"));
                }
                if (payload.containsKey("dialog_quick_snooze_minutes")) {
                    dialogConfig.put("quick_snooze_minutes", payload.get("dialog_quick_snooze_minutes"));
                }
                if (payload.containsKey("dialog_overdue_threshold_hours")) {
                    dialogConfig.put("overdue_threshold_hours", payload.get("dialog_overdue_threshold_hours"));
                }
                if (payload.containsKey("dialog_list_poll_interval_ms")) {
                    dialogConfig.put("list_poll_interval_ms", payload.get("dialog_list_poll_interval_ms"));
                }
                if (payload.containsKey("dialog_history_poll_interval_ms")) {
                    dialogConfig.put("history_poll_interval_ms", payload.get("dialog_history_poll_interval_ms"));
                }
                if (payload.containsKey("dialog_workspace_v1")) {
                    dialogConfig.put("workspace_v1", payload.get("dialog_workspace_v1"));
                }
                if (payload.containsKey("dialog_workspace_force_workspace")) {
                    dialogConfig.put("workspace_force_workspace", payload.get("dialog_workspace_force_workspace"));
                }
                if (payload.containsKey("dialog_workspace_decommission_legacy_modal")) {
                    dialogConfig.put("workspace_decommission_legacy_modal", payload.get("dialog_workspace_decommission_legacy_modal"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_sort")) {
                    dialogConfig.put("sla_critical_auto_sort", payload.get("dialog_sla_critical_auto_sort"));
                }
                if (payload.containsKey("dialog_workspace_inline_navigation")) {
                    dialogConfig.put("workspace_inline_navigation", payload.get("dialog_workspace_inline_navigation"));
                }
                if (payload.containsKey("dialog_workspace_ab_enabled")) {
                    dialogConfig.put("workspace_ab_enabled", payload.get("dialog_workspace_ab_enabled"));
                }
                if (payload.containsKey("dialog_workspace_ab_rollout_percent")) {
                    dialogConfig.put("workspace_ab_rollout_percent", payload.get("dialog_workspace_ab_rollout_percent"));
                }
                if (payload.containsKey("dialog_workspace_ab_experiment_name")) {
                    dialogConfig.put("workspace_ab_experiment_name", payload.get("dialog_workspace_ab_experiment_name"));
                }
                if (payload.containsKey("dialog_workspace_ab_operator_segment")) {
                    dialogConfig.put("workspace_ab_operator_segment", payload.get("dialog_workspace_ab_operator_segment"));
                }
                if (payload.containsKey("dialog_workspace_ab_primary_kpis")) {
                    dialogConfig.put("workspace_ab_primary_kpis", payload.get("dialog_workspace_ab_primary_kpis"));
                }
                if (payload.containsKey("dialog_workspace_ab_secondary_kpis")) {
                    dialogConfig.put("workspace_ab_secondary_kpis", payload.get("dialog_workspace_ab_secondary_kpis"));
                }
                if (payload.containsKey("dialog_workspace_ab_operator_overrides")) {
                    dialogConfig.put("workspace_ab_operator_overrides", payload.get("dialog_workspace_ab_operator_overrides"));
                }
                if (payload.containsKey("dialog_workspace_contract_timeout_ms")) {
                    dialogConfig.put("workspace_contract_timeout_ms", payload.get("dialog_workspace_contract_timeout_ms"));
                }
                if (payload.containsKey("dialog_workspace_contract_retry_attempts")) {
                    dialogConfig.put("workspace_contract_retry_attempts", payload.get("dialog_workspace_contract_retry_attempts"));
                }
                if (payload.containsKey("dialog_workspace_contract_include")) {
                    dialogConfig.put("workspace_contract_include", payload.get("dialog_workspace_contract_include"));
                }
                if (payload.containsKey("dialog_workspace_messages_page_limit")) {
                    dialogConfig.put("workspace_messages_page_limit", payload.get("dialog_workspace_messages_page_limit"));
                }
                if (payload.containsKey("dialog_workspace_disable_legacy_fallback")) {
                    dialogConfig.put("workspace_disable_legacy_fallback", payload.get("dialog_workspace_disable_legacy_fallback"));
                }
                if (payload.containsKey("dialog_workspace_failure_streak_threshold")) {
                    dialogConfig.put("workspace_failure_streak_threshold", payload.get("dialog_workspace_failure_streak_threshold"));
                }
                if (payload.containsKey("dialog_workspace_failure_cooldown_ms")) {
                    dialogConfig.put("workspace_failure_cooldown_ms", payload.get("dialog_workspace_failure_cooldown_ms"));
                }
                if (payload.containsKey("dialog_workspace_draft_autosave_delay_ms")) {
                    dialogConfig.put("workspace_draft_autosave_delay_ms", payload.get("dialog_workspace_draft_autosave_delay_ms"));
                }
                if (payload.containsKey("dialog_workspace_draft_telemetry_interval_ms")) {
                    dialogConfig.put("workspace_draft_telemetry_interval_ms", payload.get("dialog_workspace_draft_telemetry_interval_ms"));
                }
                if (payload.containsKey("dialog_workspace_open_slo_ms")) {
                    dialogConfig.put("workspace_open_slo_ms", payload.get("dialog_workspace_open_slo_ms"));
                }
                if (payload.containsKey("dialog_workspace_guardrail_render_error_rate")) {
                    dialogConfig.put("guardrail_render_error_rate", payload.get("dialog_workspace_guardrail_render_error_rate"));
                }
                if (payload.containsKey("dialog_workspace_guardrail_fallback_rate")) {
                    dialogConfig.put("guardrail_fallback_rate", payload.get("dialog_workspace_guardrail_fallback_rate"));
                }
                if (payload.containsKey("dialog_workspace_guardrail_abandon_rate")) {
                    dialogConfig.put("guardrail_abandon_rate", payload.get("dialog_workspace_guardrail_abandon_rate"));
                }
                if (payload.containsKey("dialog_workspace_guardrail_slow_open_rate")) {
                    dialogConfig.put("guardrail_slow_open_rate", payload.get("dialog_workspace_guardrail_slow_open_rate"));
                }
                if (payload.containsKey("dialog_workspace_dimension_min_events")) {
                    dialogConfig.put("dimension_min_events", payload.get("dialog_workspace_dimension_min_events"));
                }
                if (payload.containsKey("dialog_workspace_cohort_min_events")) {
                    dialogConfig.put("cohort_min_events", payload.get("dialog_workspace_cohort_min_events"));
                }
                if (payload.containsKey("dialog_workspace_rollout_kpi_outcome_min_samples_per_cohort")) {
                    dialogConfig.put("workspace_rollout_kpi_outcome_min_samples_per_cohort",
                            payload.get("dialog_workspace_rollout_kpi_outcome_min_samples_per_cohort"));
                }
                if (payload.containsKey("dialog_workspace_rollout_kpi_outcome_frt_max_relative_regression")) {
                    dialogConfig.put("workspace_rollout_kpi_outcome_frt_max_relative_regression",
                            payload.get("dialog_workspace_rollout_kpi_outcome_frt_max_relative_regression"));
                }
                if (payload.containsKey("dialog_workspace_rollout_kpi_outcome_ttr_max_relative_regression")) {
                    dialogConfig.put("workspace_rollout_kpi_outcome_ttr_max_relative_regression",
                            payload.get("dialog_workspace_rollout_kpi_outcome_ttr_max_relative_regression"));
                }
                if (payload.containsKey("dialog_workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta")) {
                    dialogConfig.put("workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta",
                            payload.get("dialog_workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta"));
                }
                if (payload.containsKey("dialog_workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier")) {
                    dialogConfig.put("workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier",
                            payload.get("dialog_workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier"));
                }
                if (payload.containsKey("dialog_workspace_rollout_required_outcome_kpis")) {
                    dialogConfig.put("workspace_rollout_required_outcome_kpis",
                            payload.get("dialog_workspace_rollout_required_outcome_kpis"));
                }
                if (payload.containsKey("dialog_workspace_rollout_winner_min_open_improvement")) {
                    dialogConfig.put("workspace_rollout_winner_min_open_improvement",
                            payload.get("dialog_workspace_rollout_winner_min_open_improvement"));
                }
                if (payload.containsKey("dialog_macro_variable_defaults")) {
                    dialogConfig.put("macro_variable_defaults", payload.get("dialog_macro_variable_defaults"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog")) {
                    dialogConfig.put("macro_variable_catalog", payload.get("dialog_macro_variable_catalog"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog_external_url")) {
                    dialogConfig.put("macro_variable_catalog_external_url",
                            payload.get("dialog_macro_variable_catalog_external_url"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog_external_timeout_ms")) {
                    dialogConfig.put("macro_variable_catalog_external_timeout_ms",
                            payload.get("dialog_macro_variable_catalog_external_timeout_ms"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog_external_cache_ttl_seconds")) {
                    dialogConfig.put("macro_variable_catalog_external_cache_ttl_seconds",
                            payload.get("dialog_macro_variable_catalog_external_cache_ttl_seconds"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog_external_auth_header")) {
                    dialogConfig.put("macro_variable_catalog_external_auth_header",
                            payload.get("dialog_macro_variable_catalog_external_auth_header"));
                }
                if (payload.containsKey("dialog_macro_variable_catalog_external_auth_token")) {
                    dialogConfig.put("macro_variable_catalog_external_auth_token",
                            payload.get("dialog_macro_variable_catalog_external_auth_token"));
                }
                if (payload.containsKey("dialog_workspace_client_crm_profile_url_template")) {
                    dialogConfig.put("workspace_client_crm_profile_url_template",
                            payload.get("dialog_workspace_client_crm_profile_url_template"));
                }
                if (payload.containsKey("dialog_workspace_client_crm_profile_label")) {
                    dialogConfig.put("workspace_client_crm_profile_label",
                            payload.get("dialog_workspace_client_crm_profile_label"));
                }
                if (payload.containsKey("dialog_workspace_client_contract_profile_url_template")) {
                    dialogConfig.put("workspace_client_contract_profile_url_template",
                            payload.get("dialog_workspace_client_contract_profile_url_template"));
                }
                if (payload.containsKey("dialog_workspace_client_contract_profile_label")) {
                    dialogConfig.put("workspace_client_contract_profile_label",
                            payload.get("dialog_workspace_client_contract_profile_label"));
                }
                if (payload.containsKey("dialog_workspace_client_external_links")) {
                    dialogConfig.put("workspace_client_external_links",
                            payload.get("dialog_workspace_client_external_links"));
                }
                if (payload.containsKey("dialog_workspace_client_external_profile_url")) {
                    dialogConfig.put("workspace_client_external_profile_url",
                            payload.get("dialog_workspace_client_external_profile_url"));
                }
                if (payload.containsKey("dialog_workspace_client_external_profile_timeout_ms")) {
                    dialogConfig.put("workspace_client_external_profile_timeout_ms",
                            payload.get("dialog_workspace_client_external_profile_timeout_ms"));
                }
                if (payload.containsKey("dialog_workspace_client_external_profile_cache_ttl_seconds")) {
                    dialogConfig.put("workspace_client_external_profile_cache_ttl_seconds",
                            payload.get("dialog_workspace_client_external_profile_cache_ttl_seconds"));
                }
                if (payload.containsKey("dialog_workspace_client_external_profile_auth_header")) {
                    dialogConfig.put("workspace_client_external_profile_auth_header",
                            payload.get("dialog_workspace_client_external_profile_auth_header"));
                }
                if (payload.containsKey("dialog_workspace_client_external_profile_auth_token")) {
                    dialogConfig.put("workspace_client_external_profile_auth_token",
                            payload.get("dialog_workspace_client_external_profile_auth_token"));
                }
                if (payload.containsKey("dialog_cross_product_omnichannel_dashboard_url")) {
                    dialogConfig.put("cross_product_omnichannel_dashboard_url",
                            payload.get("dialog_cross_product_omnichannel_dashboard_url"));
                }
                if (payload.containsKey("dialog_cross_product_omnichannel_dashboard_label")) {
                    dialogConfig.put("cross_product_omnichannel_dashboard_label",
                            payload.get("dialog_cross_product_omnichannel_dashboard_label"));
                }
                if (payload.containsKey("dialog_cross_product_finance_dashboard_url")) {
                    dialogConfig.put("cross_product_finance_dashboard_url",
                            payload.get("dialog_cross_product_finance_dashboard_url"));
                }
                if (payload.containsKey("dialog_cross_product_finance_dashboard_label")) {
                    dialogConfig.put("cross_product_finance_dashboard_label",
                            payload.get("dialog_cross_product_finance_dashboard_label"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_gate_enabled")) {
                    dialogConfig.put("workspace_rollout_external_kpi_gate_enabled",
                            payload.get("dialog_workspace_rollout_external_kpi_gate_enabled"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_omnichannel_ready")) {
                    dialogConfig.put("workspace_rollout_external_kpi_omnichannel_ready",
                            payload.get("dialog_workspace_rollout_external_kpi_omnichannel_ready"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_finance_ready")) {
                    dialogConfig.put("workspace_rollout_external_kpi_finance_ready",
                            payload.get("dialog_workspace_rollout_external_kpi_finance_ready"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_note")) {
                    dialogConfig.put("workspace_rollout_external_kpi_note",
                            payload.get("dialog_workspace_rollout_external_kpi_note"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_owner")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_owner",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_owner"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_runbook_url")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_runbook_url",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_runbook_url"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_url")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_url",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_url"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_by")) {
                    dialogConfig.put("workspace_rollout_external_kpi_reviewed_by",
                            payload.get("dialog_workspace_rollout_external_kpi_reviewed_by"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_reviewed_at",
                            payload.get("dialog_workspace_rollout_external_kpi_reviewed_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_review_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_review_ttl_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_review_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_data_freshness_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_data_freshness_required",
                            payload.get("dialog_workspace_rollout_external_kpi_data_freshness_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_data_updated_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_data_updated_at",
                            payload.get("dialog_workspace_rollout_external_kpi_data_updated_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_data_freshness_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_data_freshness_ttl_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_data_freshness_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_links_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_dashboard_links_required",
                            payload.get("dialog_workspace_rollout_external_kpi_dashboard_links_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_dashboard_status_required",
                            payload.get("dialog_workspace_rollout_external_kpi_dashboard_status_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status")) {
                    dialogConfig.put("workspace_rollout_external_kpi_dashboard_status",
                            payload.get("dialog_workspace_rollout_external_kpi_dashboard_status"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_dashboard_status_note")) {
                    dialogConfig.put("workspace_rollout_external_kpi_dashboard_status_note",
                            payload.get("dialog_workspace_rollout_external_kpi_dashboard_status_note"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_owner_runbook_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_owner_runbook_required",
                            payload.get("dialog_workspace_rollout_external_kpi_owner_runbook_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_status")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_status",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_status"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_note")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_note",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_note"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_freshness_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_freshness_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_freshness_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_updated_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_updated_at",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_updated_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_health_ttl_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_blocker_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_blocker_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_blocker_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_status")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_status",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_status"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_note")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_note",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_note"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_blocker_url")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_blocker_url",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_blocker_url"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_freshness_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_freshness_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_freshness_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_updated_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_updated_at",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_updated_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_program_ttl_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_timeline_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_timeline_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_timeline_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_target_ready_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_target_ready_at",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_target_ready_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_timeline_grace_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours"));
                }
                if (payload.containsKey("dialog_workspace_client_hidden_attributes")) {
                    dialogConfig.put("workspace_client_hidden_attributes",
                            payload.get("dialog_workspace_client_hidden_attributes"));
                }
                if (payload.containsKey("dialog_workspace_client_attribute_labels")) {
                    dialogConfig.put("workspace_client_attribute_labels",
                            payload.get("dialog_workspace_client_attribute_labels"));
                }
                if (payload.containsKey("dialog_workspace_client_attribute_order")) {
                    dialogConfig.put("workspace_client_attribute_order",
                            payload.get("dialog_workspace_client_attribute_order"));
                }
                if (payload.containsKey("dialog_workspace_client_extra_attributes_max")) {
                    dialogConfig.put("workspace_client_extra_attributes_max",
                            payload.get("dialog_workspace_client_extra_attributes_max"));
                }
                if (payload.containsKey("dialog_workspace_client_extra_attributes_collapse_after")) {
                    dialogConfig.put("workspace_client_extra_attributes_collapse_after",
                            payload.get("dialog_workspace_client_extra_attributes_collapse_after"));
                }
                if (payload.containsKey("dialog_workspace_client_extra_attributes_hide_technical")) {
                    dialogConfig.put("workspace_client_extra_attributes_hide_technical",
                            payload.get("dialog_workspace_client_extra_attributes_hide_technical"));
                }
                if (payload.containsKey("dialog_workspace_client_extra_attributes_technical_prefixes")) {
                    dialogConfig.put("workspace_client_extra_attributes_technical_prefixes",
                            payload.get("dialog_workspace_client_extra_attributes_technical_prefixes"));
                }
                if (payload.containsKey("dialog_workspace_context_history_limit")) {
                    dialogConfig.put("workspace_context_history_limit",
                            payload.get("dialog_workspace_context_history_limit"));
                }
                if (payload.containsKey("dialog_workspace_context_related_events_limit")) {
                    dialogConfig.put("workspace_context_related_events_limit",
                            payload.get("dialog_workspace_context_related_events_limit"));
                }
                if (payload.containsKey("dialog_workspace_segment_high_lifetime_volume_min_dialogs")) {
                    dialogConfig.put("workspace_segment_high_lifetime_volume_min_dialogs",
                            payload.get("dialog_workspace_segment_high_lifetime_volume_min_dialogs"));
                }
                if (payload.containsKey("dialog_workspace_segment_multi_open_dialogs_min_open")) {
                    dialogConfig.put("workspace_segment_multi_open_dialogs_min_open",
                            payload.get("dialog_workspace_segment_multi_open_dialogs_min_open"));
                }
                if (payload.containsKey("dialog_workspace_segment_reactivation_risk_min_dialogs")) {
                    dialogConfig.put("workspace_segment_reactivation_risk_min_dialogs",
                            payload.get("dialog_workspace_segment_reactivation_risk_min_dialogs"));
                }
                if (payload.containsKey("dialog_workspace_segment_reactivation_risk_max_resolved_30d")) {
                    dialogConfig.put("workspace_segment_reactivation_risk_max_resolved_30d",
                            payload.get("dialog_workspace_segment_reactivation_risk_max_resolved_30d"));
                }
                if (payload.containsKey("dialog_public_form_message_max_length")) {
                    dialogConfig.put("public_form_message_max_length",
                            payload.get("dialog_public_form_message_max_length"));
                }
                if (payload.containsKey("dialog_public_form_answers_total_max_length")) {
                    dialogConfig.put("public_form_answers_total_max_length",
                            payload.get("dialog_public_form_answers_total_max_length"));
                }
                if (payload.containsKey("dialog_public_form_session_ttl_hours")) {
                    dialogConfig.put("public_form_session_ttl_hours",
                            payload.get("dialog_public_form_session_ttl_hours"));
                }
                if (payload.containsKey("dialog_public_form_idempotency_ttl_seconds")) {
                    dialogConfig.put("public_form_idempotency_ttl_seconds",
                            payload.get("dialog_public_form_idempotency_ttl_seconds"));
                }
                if (payload.containsKey("dialog_public_form_rate_limit_enabled")) {
                    dialogConfig.put("public_form_rate_limit_enabled",
                            payload.get("dialog_public_form_rate_limit_enabled"));
                }
                if (payload.containsKey("dialog_public_form_rate_limit_window_seconds")) {
                    dialogConfig.put("public_form_rate_limit_window_seconds",
                            payload.get("dialog_public_form_rate_limit_window_seconds"));
                }
                if (payload.containsKey("dialog_public_form_rate_limit_max_requests")) {
                    dialogConfig.put("public_form_rate_limit_max_requests",
                            payload.get("dialog_public_form_rate_limit_max_requests"));
                }
                if (payload.containsKey("dialog_public_form_rate_limit_use_fingerprint")) {
                    dialogConfig.put("public_form_rate_limit_use_fingerprint",
                            payload.get("dialog_public_form_rate_limit_use_fingerprint"));
                }
                if (payload.containsKey("dialog_public_form_metrics_enabled")) {
                    dialogConfig.put("public_form_metrics_enabled",
                            payload.get("dialog_public_form_metrics_enabled"));
                }
                if (payload.containsKey("dialog_public_form_strip_html_tags")) {
                    dialogConfig.put("public_form_strip_html_tags",
                            payload.get("dialog_public_form_strip_html_tags"));
                }
                if (payload.containsKey("dialog_public_form_captcha_shared_secret")) {
                    dialogConfig.put("public_form_captcha_shared_secret",
                            payload.get("dialog_public_form_captcha_shared_secret"));
                }
                if (payload.containsKey("dialog_public_form_captcha_mode")) {
                    dialogConfig.put("public_form_captcha_mode",
                            payload.get("dialog_public_form_captcha_mode"));
                }
                if (payload.containsKey("dialog_public_form_turnstile_secret_key")) {
                    dialogConfig.put("public_form_turnstile_secret_key",
                            payload.get("dialog_public_form_turnstile_secret_key"));
                }
                if (payload.containsKey("dialog_public_form_turnstile_verify_url")) {
                    dialogConfig.put("public_form_turnstile_verify_url",
                            payload.get("dialog_public_form_turnstile_verify_url"));
                }
                if (payload.containsKey("dialog_public_form_turnstile_timeout_ms")) {
                    dialogConfig.put("public_form_turnstile_timeout_ms",
                            payload.get("dialog_public_form_turnstile_timeout_ms"));
                }
                if (payload.containsKey("dialog_public_form_session_polling_enabled")) {
                    dialogConfig.put("public_form_session_polling_enabled",
                            payload.get("dialog_public_form_session_polling_enabled"));
                }
                if (payload.containsKey("dialog_public_form_session_polling_interval_seconds")) {
                    dialogConfig.put("public_form_session_polling_interval_seconds",
                            payload.get("dialog_public_form_session_polling_interval_seconds"));
                }
                if (payload.containsKey("dialog_public_form_session_token_rotate_on_read")) {
                    dialogConfig.put("public_form_session_token_rotate_on_read",
                            payload.get("dialog_public_form_session_token_rotate_on_read"));
                }
                if (payload.containsKey("dialog_public_form_default_locale")) {
                    dialogConfig.put("public_form_default_locale",
                            payload.get("dialog_public_form_default_locale"));
                }
                if (payload.containsKey("dialog_summary_badges")) {
                    Map<String, Object> summaryBadges = new LinkedHashMap<>();
                    Object existingBadges = dialogConfig.get("summary_badges");
                    if (existingBadges instanceof Map<?, ?> badgesMap) {
                        badgesMap.forEach((key, value) -> summaryBadges.put(String.valueOf(key), value));
                    }
                    Object rawBadges = payload.get("dialog_summary_badges");
                    if (rawBadges instanceof Map<?, ?> incomingMap) {
                        incomingMap.forEach((key, value) -> {
                            if (key != null) {
                                summaryBadges.put(String.valueOf(key), value);
                            }
                        });
                    }
                    dialogConfig.put("summary_badges", summaryBadges);
                }
                settings.put("dialog_config", dialogConfig);
                modified = true;
            }

            if (payload.containsKey("reporting_config")) {
                settings.put("reporting_config", payload.get("reporting_config"));
                modified = true;
            }

            if (payload.containsKey("manager_location_bindings")) {
                settings.put("manager_location_bindings", payload.get("manager_location_bindings"));
                modified = true;
            }

            if (modified) {
                sharedConfigService.saveSettings(settings);
            }

            if (payload.containsKey("locations")) {
                Object locationsPayload = payload.get("locations");
                sharedConfigService.saveLocations(locationsPayload);
                syncParametersFromLocations(locationsPayload);
            }

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

    @GetMapping("/api/settings/parameters")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listParameters() {
        return fetchParametersGrouped(true);
    }

    @PostMapping({"/api/settings/parameters", "/api/settings/parameters/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createParameter(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        String paramType = stringValue(payload.get("param_type"));
        String value = stringValue(payload.get("value"));
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return Map.of("success", false, "error", "Тип и значение параметра обязательны");
        }
        String state = stringValue(payload.get("state"));
        if (!StringUtils.hasText(state)) {
            state = "Активен";
        }
        try {
            validateParameterUniqueness(paramType, value, payload, null);
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
        String extraJson = buildExtraJson(payload, Set.of("param_type", "value", "state"));
        jdbcTemplate.update(
            "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, ?, 0, ?)",
            paramType, value, state, extraJson
        );
        syncLocationsFromParameters();
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @RequestMapping(
        value = {"/api/settings/parameters/{paramId}", "/api/settings/parameters/{paramId}/"},
        method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH}
    )
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateParameter(@PathVariable long paramId,
                                               @RequestBody Map<String, Object> payload) {
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
            "SELECT param_type, value, extra_json FROM settings_parameters WHERE id = ?",
            paramId
        );
        if (existingRows.isEmpty()) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        Map<String, Object> existing = existingRows.get(0);

        String paramType = stringValue(existing.get("param_type"));
        String finalValue = payload.containsKey("value")
            ? stringValue(payload.get("value"))
            : stringValue(existing.get("value"));
        try {
            validateParameterUniqueness(paramType, finalValue, payload, paramId);
        } catch (IllegalArgumentException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }

        StringBuilder updates = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("value")) {
            updates.append("value = ?,");
            params.add(stringValue(payload.get("value")));
        }
        if (payload.containsKey("state")) {
            updates.append("state = ?,");
            params.add(stringValue(payload.get("state")));
        }
        if (payload.containsKey("is_deleted")) {
            updates.append("is_deleted = ?,");
            params.add(Boolean.TRUE.equals(payload.get("is_deleted")) ? 1 : 0);
            if (Boolean.TRUE.equals(payload.get("is_deleted"))) {
                updates.append("deleted_at = datetime('now'),");
            } else {
                updates.append("deleted_at = NULL,");
            }
        }

        String extraJson = mergeExtraJson(existing.get("extra_json"), payload, Set.of("value", "state", "is_deleted"));
        updates.append("extra_json = ?");
        params.add(extraJson);

        if (updates.length() == 0) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }

        params.add(paramId);
        jdbcTemplate.update("UPDATE settings_parameters SET " + updates + " WHERE id = ?", params.toArray());
        syncLocationsFromParameters();
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @DeleteMapping("/api/settings/parameters/{paramId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteParameter(@PathVariable long paramId) {
        int updated = jdbcTemplate.update(
            "UPDATE settings_parameters SET is_deleted = 1, deleted_at = datetime('now') WHERE id = ?",
            paramId
        );
        if (updated == 0) {
            return Map.of("success", false, "error", "Параметр не найден");
        }
        syncLocationsFromParameters();
        Map<String, Object> data = fetchParametersGrouped(true);
        return Map.of("success", true, "data", data);
    }

    @GetMapping("/api/settings/it-equipment")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> listItEquipment() {
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @PostMapping({"/api/settings/it-equipment", "/api/settings/it-equipment/"})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> createItEquipment(HttpServletRequest request) throws IOException {
        Map<String, Object> payload = RequestPayloadUtils.readPayload(request, objectMapper);
        String type = stringValue(payload.get("equipment_type"));
        String vendor = stringValue(payload.get("equipment_vendor"));
        String model = stringValue(payload.get("equipment_model"));
        if (!StringUtils.hasText(type)) {
            return Map.of("success", false, "error", "Поле «Тип оборудования» обязательно");
        }
        if (!StringUtils.hasText(vendor)) {
            return Map.of("success", false, "error", "Поле «Производитель оборудования» обязательно");
        }
        if (!StringUtils.hasText(model)) {
            return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
        }
        String photoUrl = stringValue(payload.getOrDefault("photo_url", payload.get("photo")));
        String serialNumber = stringValue(payload.get("serial_number"));
        String accessories = stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment")));

        jdbcTemplate.update(
            "INSERT INTO it_equipment_catalog(equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, datetime('now'), datetime('now'))",
            type, vendor, model, photoUrl, serialNumber, accessories
        );
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @RequestMapping(value = "/api/settings/it-equipment/{itemId}",
        method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> updateItEquipment(@PathVariable long itemId,
                                                 @RequestBody Map<String, Object> payload) {
        StringBuilder updates = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (payload.containsKey("equipment_type")) {
            String value = stringValue(payload.get("equipment_type"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Тип оборудования» обязательно");
            }
            updates.append("equipment_type = ?,");
            params.add(value);
        }
        if (payload.containsKey("equipment_vendor")) {
            String value = stringValue(payload.get("equipment_vendor"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Производитель оборудования» обязательно");
            }
            updates.append("equipment_vendor = ?,");
            params.add(value);
        }
        if (payload.containsKey("equipment_model")) {
            String value = stringValue(payload.get("equipment_model"));
            if (!StringUtils.hasText(value)) {
                return Map.of("success", false, "error", "Поле «Модель оборудования» обязательно");
            }
            updates.append("equipment_model = ?,");
            params.add(value);
        }
        if (payload.containsKey("photo_url") || payload.containsKey("photo")) {
            updates.append("photo_url = ?,");
            params.add(stringValue(payload.getOrDefault("photo_url", payload.get("photo"))));
        }
        if (payload.containsKey("serial_number")) {
            updates.append("serial_number = ?,");
            params.add(stringValue(payload.get("serial_number")));
        }
        if (payload.containsKey("accessories") || payload.containsKey("additional_equipment")) {
            updates.append("accessories = ?,");
            params.add(stringValue(payload.getOrDefault("accessories", payload.get("additional_equipment"))));
        }

        if (updates.length() == 0) {
            return Map.of("success", false, "error", "Нет данных для обновления");
        }
        updates.append("updated_at = datetime('now')");
        params.add(itemId);
        jdbcTemplate.update("UPDATE it_equipment_catalog SET " + updates + " WHERE id = ?", params.toArray());

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    @DeleteMapping("/api/settings/it-equipment/{itemId}")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public Map<String, Object> deleteItEquipment(@PathVariable long itemId) {
        int removed = jdbcTemplate.update("DELETE FROM it_equipment_catalog WHERE id = ?", itemId);
        if (removed == 0) {
            return Map.of("success", false, "error", "Оборудование не найдено");
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "SELECT id, equipment_type, equipment_vendor, equipment_model, photo_url, serial_number, accessories " +
                "FROM it_equipment_catalog ORDER BY id DESC"
        );
        return Map.of("success", true, "items", items);
    }

    private Map<String, Object> fetchParametersGrouped(boolean includeDeleted) {
        Map<String, Object> grouped = new LinkedHashMap<>();
        Map<String, String> types = settingsCatalogService.getParameterTypes();
        types.keySet().forEach(key -> grouped.put(key, new ArrayList<>()));

        String sql = "SELECT id, param_type, value, state, is_deleted, deleted_at, extra_json " +
            "FROM settings_parameters";
        if (!includeDeleted) {
            sql += " WHERE is_deleted = 0";
        }
        sql += " ORDER BY param_type, value";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        Map<String, List<String>> dependenciesMap = settingsCatalogService.getParameterDependencies();
        for (Map<String, Object> row : rows) {
            String type = stringValue(row.get("param_type"));
            if (!grouped.containsKey(type)) {
                continue;
            }
            Map<String, Object> extra = parseExtraJson(row.get("extra_json"));
            Map<String, String> dependencies = new LinkedHashMap<>();
            List<String> depKeys = dependenciesMap.get(type);
            if (depKeys != null) {
                Object rawDeps = extra.get("dependencies");
                Map<?, ?> depsMap = rawDeps instanceof Map<?, ?> map ? map : Map.of();
                for (String key : depKeys) {
                    Object value = depsMap.containsKey(key) ? depsMap.get(key) : extra.get(key);
                    dependencies.put(key, value != null ? value.toString().trim() : "");
                }
                extra.put("dependencies", dependencies);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", row.get("id"));
            entry.put("value", row.get("value"));
            entry.put("state", row.get("state") != null ? row.get("state") : "Активен");
            entry.put("is_deleted", asBoolean(row.get("is_deleted")));
            entry.put("deleted_at", row.get("deleted_at"));
            entry.put("usage_count", 0);
            entry.put("extra", extra);
            entry.put("dependencies", dependencies);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> list = (List<Map<String, Object>>) grouped.get(type);
            list.add(entry);
        }
        return grouped;
    }

    private Map<String, Object> parseExtraJson(Object raw) {
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(text, Map.class);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String buildExtraJson(Map<String, Object> payload, Set<String> skipKeys) {
        Map<String, Object> extra = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (!skipKeys.contains(key)) {
                extra.put(key, value);
            }
        });
        return writeJson(extra);
    }

    private String mergeExtraJson(Object existingRaw, Map<String, Object> payload, Set<String> skipKeys) {
        Map<String, Object> extra = parseExtraJson(existingRaw);
        payload.forEach((key, value) -> {
            if (!skipKeys.contains(key)) {
                extra.put(key, value);
            }
        });
        return writeJson(extra);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof Number n) {
            return n.intValue() != 0;
        }
        if (raw instanceof String s) {
            return s.equalsIgnoreCase("true") || s.equals("1");
        }
        return false;
    }


    private boolean canPublishDialogMacros(Authentication authentication, Map<String, Object> dialogConfig) {
        if (!permissionService.hasAuthority(authentication, "DIALOG_MACRO_PUBLISH")) {
            return false;
        }
        Set<String> allowedRoles = resolveMacroPublishAllowedRoles(dialogConfig);
        return permissionService.hasAnyRole(authentication, allowedRoles);
    }

    private Set<String> resolveMacroPublishAllowedRoles(Map<String, Object> dialogConfig) {
        if (dialogConfig == null) {
            return Set.of();
        }
        Object raw = dialogConfig.get("macro_publish_allowed_roles");
        if (!(raw instanceof List<?> roles)) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (Object roleRaw : roles) {
            String role = String.valueOf(roleRaw).trim();
            if (!role.isBlank()) {
                normalized.add(role);
            }
        }
        return normalized;
    }
    private MacroNormalizationResult normalizeMacroTemplates(Object existingRaw,
                                                             Object incomingRaw,
                                                             String actor,
                                                             boolean canPublishMacros,
                                                             boolean requireIndependentReview) {
        List<Map<String, Object>> existingTemplates = castTemplateList(existingRaw);
        Map<String, Map<String, Object>> existingById = new LinkedHashMap<>();
        for (Map<String, Object> template : existingTemplates) {
            String id = stringValue(template.get("id"));
            if (StringUtils.hasText(id)) {
                existingById.put(id, template);
            }
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!(incomingRaw instanceof List<?> incomingTemplates)) {
            return new MacroNormalizationResult(normalized, warnings);
        }

        String normalizedActor = StringUtils.hasText(actor) ? actor : "system";
        String now = Instant.now().toString();
        for (Object candidate : incomingTemplates) {
            if (!(candidate instanceof Map<?, ?> sourceMap)) {
                continue;
            }

            String name = stringValue(sourceMap.get("name"));
            String message = stringValue(sourceMap.get("message"));
            if (!StringUtils.hasText(message)) {
                message = stringValue(sourceMap.get("text"));
            }
            if (!StringUtils.hasText(name) || !StringUtils.hasText(message)) {
                continue;
            }

            String id = stringValue(sourceMap.get("id"));
            if (!StringUtils.hasText(id)) {
                id = "macro_" + UUID.randomUUID();
            }
            Map<String, Object> previous = existingById.get(id);

            List<String> tags = normalizeTemplateTags(sourceMap.get("tags"));
            Map<String, Object> workflow = normalizeMacroWorkflow(sourceMap.get("workflow"), sourceMap);
            int version = resolveTemplateVersion(previous);
            boolean changedMeaningfully = templateMeaningfullyChanged(previous, name, message, tags)
                || !Objects.equals(normalizeWorkflowForComparison(previous != null ? previous.get("workflow") : null), workflow);

            boolean previouslyPublished = previous != null && asBoolean(previous.get("published"));
            if (!canPublishMacros && previouslyPublished && changedMeaningfully) {
                warnings.add("Недостаточно прав для изменения опубликованного макроса «"
                    + name
                    + "»: правки сохранены только для пользователей с правом DIALOG_MACRO_PUBLISH.");
                name = stringValue(previous.get("name"));
                message = stringValue(previous.get("message"));
                tags = normalizeTemplateTags(previous.get("tags"));
                workflow = normalizeMacroWorkflow(previous.get("workflow"), previous);
                changedMeaningfully = false;
            }

            if (changedMeaningfully) {
                version += 1;
            }

            String previousUpdatedBy = previous != null ? stringValue(previous.get("updated_by")) : "";
            boolean approvedForPublish = resolveMacroApproval(previous);
            boolean approvalRequested = false;
            if (sourceMap.containsKey("approved_for_publish")) {
                approvalRequested = asBoolean(sourceMap.get("approved_for_publish"));
                approvedForPublish = approvalRequested;
            }
            if (!canPublishMacros) {
                if (sourceMap.containsKey("published") || sourceMap.containsKey("approved_for_publish")) {
                    warnings.add("Недостаточно прав для публикации макросов: изменения статуса публикации для «"
                        + name + "» проигнорированы.");
                }
                approvedForPublish = resolveMacroApproval(previous);
            }
            if (changedMeaningfully) {
                approvedForPublish = false;
            }
            if (requireIndependentReview && changedMeaningfully) {
                if (sourceMap.containsKey("published") || sourceMap.containsKey("approved_for_publish")) {
                    warnings.add("Макрос «" + name + "» требует независимого ревью после изменений: публикация отклонена до подтверждения другим сотрудником.");
                }
                approvedForPublish = false;
            }
            boolean requiresIndependentReview = requireIndependentReview && !changedMeaningfully
                && StringUtils.hasText(previousUpdatedBy)
                && previousUpdatedBy.equalsIgnoreCase(normalizedActor);
            if (approvalRequested && requiresIndependentReview) {
                approvedForPublish = false;
                warnings.add("Макрос «" + name + "» требует независимого ревью: подтверждение тем же автором отклонено.");
                log.info("Dialog macro template '{}' approval requires independent reviewer: actor='{}', previous_updated_by='{}'",
                    id,
                    normalizedActor,
                    previousUpdatedBy);
            }

            boolean published = previous != null
                ? asBoolean(previous.get("published"))
                : true;
            if (sourceMap.containsKey("published")) {
                published = asBoolean(sourceMap.get("published"));
            }
            if (!canPublishMacros) {
                published = previous != null ? asBoolean(previous.get("published")) : false;
            }
            if (!approvedForPublish) {
                published = false;
            }
            boolean wasPublished = previouslyPublished;
            String previousPublishedAt = previous != null ? stringValue(previous.get("published_at")) : "";
            String previousPublishedBy = previous != null ? stringValue(previous.get("published_by")) : "";
            String publishedAt = published
                ? (StringUtils.hasText(previousPublishedAt) ? previousPublishedAt : now)
                : "";
            String publishedBy = published
                ? (StringUtils.hasText(previousPublishedBy) ? previousPublishedBy : normalizedActor)
                : "";
            if (!wasPublished && published) {
                publishedAt = now;
                publishedBy = normalizedActor;
            }

            boolean wasApproved = resolveMacroApproval(previous);
            String previousReviewedAt = previous != null ? stringValue(previous.get("reviewed_at")) : "";
            String previousReviewedBy = previous != null ? stringValue(previous.get("reviewed_by")) : "";
            String reviewedAt = approvedForPublish
                ? (StringUtils.hasText(previousReviewedAt) ? previousReviewedAt : now)
                : "";
            String reviewedBy = approvedForPublish
                ? (StringUtils.hasText(previousReviewedBy) ? previousReviewedBy : normalizedActor)
                : "";
            if (!wasApproved && approvedForPublish) {
                reviewedAt = now;
                reviewedBy = normalizedActor;
            }
            if (changedMeaningfully) {
                reviewedAt = "";
                reviewedBy = "";
            }

            Map<String, Object> normalizedTemplate = new LinkedHashMap<>();
            normalizedTemplate.put("id", id);
            normalizedTemplate.put("name", name);
            normalizedTemplate.put("message", message);
            normalizedTemplate.put("text", message);
            normalizedTemplate.put("tags", tags);
            normalizedTemplate.put("workflow", workflow);
            normalizedTemplate.put("assign_to_me", asBoolean(workflow.get("assign_to_me")));
            Object snoozeMinutes = workflow.get("snooze_minutes");
            normalizedTemplate.put("snooze_minutes", snoozeMinutes instanceof Number n ? n.intValue() : null);
            normalizedTemplate.put("close_ticket", asBoolean(workflow.get("close_ticket")));
            normalizedTemplate.put("published", published);
            normalizedTemplate.put("approved_for_publish", approvedForPublish);
            String reviewState = approvedForPublish
                ? "approved"
                : ((requireIndependentReview && changedMeaningfully) || requiresIndependentReview
                    ? "pending_peer_review"
                    : "pending_review");
            normalizedTemplate.put("review_state", reviewState);
            normalizedTemplate.put("version", Math.max(1, version));
            normalizedTemplate.put("created_at", previous != null
                ? stringValue(previous.get("created_at"))
                : now);
            normalizedTemplate.put("updated_at", now);
            normalizedTemplate.put("updated_by", normalizedActor);
            normalizedTemplate.put("reviewed_at", StringUtils.hasText(reviewedAt) ? reviewedAt : null);
            normalizedTemplate.put("reviewed_by", StringUtils.hasText(reviewedBy) ? reviewedBy : null);
            normalizedTemplate.put("published_at", StringUtils.hasText(publishedAt) ? publishedAt : null);
            normalizedTemplate.put("published_by", StringUtils.hasText(publishedBy) ? publishedBy : null);

            normalized.add(normalizedTemplate);
        }
        log.info("Dialog macro templates normalized: actor='{}', incoming={}, stored={}, can_publish={}",
            normalizedActor,
            incomingTemplates.size(),
            normalized.size(),
            canPublishMacros);
        return new MacroNormalizationResult(normalized, warnings.stream().distinct().toList());
    }

    private boolean resolveMacroApproval(Map<String, Object> template) {
        if (template == null) {
            return false;
        }
        if (template.containsKey("approved_for_publish")) {
            return asBoolean(template.get("approved_for_publish"));
        }
        return asBoolean(template.get("published"));
    }

    private boolean resolveMacroIndependentReviewRequired(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || !dialogConfig.containsKey("macro_require_independent_review")) {
            return true;
        }
        return asBoolean(dialogConfig.get("macro_require_independent_review"));
    }

    private List<Map<String, Object>> castTemplateList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> normalizeTemplateTags(Object rawTags) {
        List<String> tags = new ArrayList<>();
        if (!(rawTags instanceof List<?> list)) {
            return tags;
        }
        for (Object tagRaw : list) {
            String tag = stringValue(tagRaw);
            if (StringUtils.hasText(tag) && !tags.contains(tag)) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private Map<String, Object> normalizeWorkflowForComparison(Object rawWorkflow) {
        return normalizeMacroWorkflow(rawWorkflow, Collections.emptyMap());
    }

    private Map<String, Object> normalizeMacroWorkflow(Object rawWorkflow, Map<?, ?> sourceMap) {
        Map<String, Object> workflow = new LinkedHashMap<>();
        boolean assignToMe = false;
        int snoozeMinutes = 0;
        boolean closeTicket = false;

        if (rawWorkflow instanceof Map<?, ?> workflowMap) {
            assignToMe = asBoolean(workflowMap.get("assign_to_me"));
            snoozeMinutes = normalizeWorkflowSnoozeMinutes(workflowMap.get("snooze_minutes"));
            closeTicket = asBoolean(workflowMap.get("close_ticket"));
        }

        assignToMe = assignToMe || asBoolean(sourceMap.get("assign_to_me"));
        closeTicket = closeTicket || asBoolean(sourceMap.get("close_ticket"));
        int fallbackSnooze = normalizeWorkflowSnoozeMinutes(sourceMap.get("snooze_minutes"));
        if (snoozeMinutes <= 0) {
            snoozeMinutes = fallbackSnooze;
        }

        workflow.put("assign_to_me", assignToMe);
        workflow.put("snooze_minutes", snoozeMinutes > 0 ? snoozeMinutes : null);
        workflow.put("close_ticket", closeTicket);
        return workflow;
    }

    private int normalizeWorkflowSnoozeMinutes(Object rawValue) {
        if (rawValue == null) {
            return 0;
        }
        int minutes;
        if (rawValue instanceof Number number) {
            minutes = number.intValue();
        } else {
            String text = stringValue(rawValue);
            if (!StringUtils.hasText(text)) {
                return 0;
            }
            try {
                minutes = Integer.parseInt(text);
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        return (minutes >= 1 && minutes <= 1440) ? minutes : 0;
    }

    private int resolveTemplateVersion(Map<String, Object> template) {
        if (template == null) {
            return 0;
        }
        Object raw = template.get("version");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(stringValue(raw)));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private boolean templateMeaningfullyChanged(Map<String, Object> previous,
                                                String name,
                                                String message,
                                                List<String> tags) {
        if (previous == null) {
            return true;
        }
        String previousName = stringValue(previous.get("name"));
        String previousMessage = stringValue(previous.get("message"));
        if (!StringUtils.hasText(previousMessage)) {
            previousMessage = stringValue(previous.get("text"));
        }
        List<String> previousTags = normalizeTemplateTags(previous.get("tags"));
        return !previousName.equals(name)
            || !previousMessage.equals(message)
            || !previousTags.equals(tags);
    }

    private void validateParameterUniqueness(String paramType,
                                             String value,
                                             Map<String, Object> payload,
                                             Long excludeId) {
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return;
        }
        List<String> dependencyKeys = settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of());
        Map<String, String> incomingDependencies = extractDependencies(paramType, payload, dependencyKeys);

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
            "SELECT id, extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = 0",
            paramType,
            value
        );
        for (Map<String, Object> candidate : candidates) {
            Long candidateId = candidate.get("id") instanceof Number n ? n.longValue() : null;
            if (excludeId != null && candidateId != null && excludeId.equals(candidateId)) {
                continue;
            }
            Map<String, String> existingDependencies = extractDependencies(
                paramType,
                parseExtraJson(candidate.get("extra_json")),
                dependencyKeys
            );
            if (dependencyKeys.isEmpty() || existingDependencies.equals(incomingDependencies)) {
                throw new IllegalArgumentException("Такая запись уже существует");
            }
        }
    }

    private Map<String, String> extractDependencies(String paramType,
                                                    Map<String, Object> source,
                                                    List<String> dependencyKeys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null || dependencyKeys == null || dependencyKeys.isEmpty()) {
            return result;
        }
        Object rawDependencies = source.get("dependencies");
        Map<?, ?> dependenciesMap = rawDependencies instanceof Map<?, ?> map ? map : Map.of();
        for (String key : dependencyKeys) {
            Object raw = dependenciesMap.containsKey(key) ? dependenciesMap.get(key) : source.get(key);
            result.put(key, stringValue(raw));
        }
        return result;
    }

    private void syncParametersFromLocations(Object locationsPayload) {
        if (!(locationsPayload instanceof Map<?, ?> map)) {
            return;
        }
        Object treeRaw = map.get("tree");
        if (!(treeRaw instanceof Map<?, ?> tree)) {
            return;
        }
        Map<String, Map<String, String>> cityMeta = readMetaMap(map.get("city_meta"));
        Map<String, Map<String, String>> locationMeta = readMetaMap(map.get("location_meta"));

        Set<String> businesses = new LinkedHashSet<>();
        Set<String> partnerTypes = new LinkedHashSet<>();
        Set<String> countries = new LinkedHashSet<>();

        for (Map.Entry<?, ?> businessEntry : tree.entrySet()) {
            String business = stringValue(businessEntry.getKey());
            if (!StringUtils.hasText(business)) {
                continue;
            }
            businesses.add(business);
            if (!(businessEntry.getValue() instanceof Map<?, ?> types)) {
                continue;
            }
            for (Map.Entry<?, ?> typeEntry : types.entrySet()) {
                String type = stringValue(typeEntry.getKey());
                if (StringUtils.hasText(type)) {
                    partnerTypes.add(type);
                }
                if (!(typeEntry.getValue() instanceof Map<?, ?> cities)) {
                    continue;
                }
                for (Map.Entry<?, ?> cityEntry : cities.entrySet()) {
                    String city = stringValue(cityEntry.getKey());
                    if (!StringUtils.hasText(city)) {
                        continue;
                    }
                    String cityPath = String.join("::", business, type, city);
                    Map<String, String> cityAttrs = cityMeta.getOrDefault(cityPath, Map.of());
                    String country = stringValue(cityAttrs.get("country"));
                    String partnerType = stringValue(cityAttrs.get("partner_type"));
                    if (!StringUtils.hasText(partnerType)) {
                        partnerType = type;
                    }
                    if (StringUtils.hasText(partnerType)) {
                        partnerTypes.add(partnerType);
                    }
                    if (StringUtils.hasText(country)) {
                        countries.add(country);
                        upsertParameterIfMissing("city", city, Map.of(
                            "country", country,
                            "partner_type", partnerType,
                            "business", business
                        ));
                    }
                    Object locationsRaw = cityEntry.getValue();
                    if (!(locationsRaw instanceof Iterable<?> locations)) {
                        continue;
                    }
                    for (Object locationRaw : locations) {
                        String location = stringValue(locationRaw);
                        if (!StringUtils.hasText(location)) {
                            continue;
                        }
                        String locationPath = String.join("::", business, type, city, location);
                        Map<String, String> locationAttrs = locationMeta.getOrDefault(locationPath, Map.of());
                        String locationCountry = stringValue(locationAttrs.get("country"));
                        String locationPartnerType = stringValue(locationAttrs.get("partner_type"));
                        if (!StringUtils.hasText(locationCountry)) {
                            locationCountry = country;
                        }
                        if (!StringUtils.hasText(locationPartnerType)) {
                            locationPartnerType = partnerType;
                        }
                        if (StringUtils.hasText(locationCountry)) {
                            countries.add(locationCountry);
                        }
                        if (StringUtils.hasText(locationPartnerType)) {
                            partnerTypes.add(locationPartnerType);
                        }
                        if (StringUtils.hasText(locationCountry) && StringUtils.hasText(locationPartnerType)) {
                            upsertParameterIfMissing("department", location, Map.of(
                                "country", locationCountry,
                                "partner_type", locationPartnerType,
                                "business", business,
                                "city", city
                            ));
                        }
                    }
                }
            }
        }

        businesses.forEach(b -> upsertParameterIfMissing("business", b, Map.of()));
        partnerTypes.forEach(t -> upsertParameterIfMissing("partner_type", t, Map.of("country", "Россия")));
        countries.forEach(c -> upsertParameterIfMissing("country", c, Map.of()));
    }

    private Map<String, Map<String, String>> readMetaMap(Object raw) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = stringValue(entry.getKey());
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> values)) {
                continue;
            }
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("country", stringValue(values.get("country")));
            attrs.put("partner_type", stringValue(values.get("partner_type")));
            result.put(key, attrs);
        }
        return result;
    }

    private void upsertParameterIfMissing(String paramType, String value, Map<String, String> dependencies) {
        if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
            return;
        }
        List<String> dependencyKeys = settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, extra_json FROM settings_parameters WHERE param_type = ? AND value = ? AND is_deleted = 0",
            paramType,
            value
        );
        for (Map<String, Object> row : rows) {
            Map<String, String> existingDeps = extractDependencies(paramType, parseExtraJson(row.get("extra_json")), dependencyKeys);
            Map<String, String> targetDeps = new LinkedHashMap<>();
            for (String key : dependencyKeys) {
                targetDeps.put(key, stringValue(dependencies.get(key)));
            }
            if (dependencyKeys.isEmpty() || existingDeps.equals(targetDeps)) {
                return;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dependencies", dependencies);
        dependencies.forEach(payload::put);
        String extraJson = writeJson(payload);
        jdbcTemplate.update(
            "INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', 0, ?)",
            paramType,
            value,
            extraJson
        );
    }

    private void syncLocationsFromParameters() {
        JsonNode existingLocations = sharedConfigService.loadLocations();
        Map<String, Object> payload = existingLocations != null && existingLocations.isObject()
            ? objectMapper.convertValue(existingLocations, Map.class)
            : new LinkedHashMap<>();

        Map<String, Object> tree = payload.get("tree") instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, Object>) map)
            : new LinkedHashMap<>();
        Map<String, Object> cityMeta = payload.get("city_meta") instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, Object>) map)
            : new LinkedHashMap<>();
        Map<String, Object> locationMeta = payload.get("location_meta") instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, Object>) map)
            : new LinkedHashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT param_type, value, extra_json FROM settings_parameters "
                + "WHERE is_deleted = 0 AND param_type IN ('business', 'city', 'department')"
        );

        for (Map<String, Object> row : rows) {
            String paramType = stringValue(row.get("param_type"));
            String value = stringValue(row.get("value"));
            if (!StringUtils.hasText(paramType) || !StringUtils.hasText(value)) {
                continue;
            }

            Map<String, Object> extra = parseExtraJson(row.get("extra_json"));
            Map<String, String> dependencies = extractDependencies(
                paramType,
                extra,
                settingsCatalogService.getParameterDependencies().getOrDefault(paramType, List.of())
            );

            if ("business".equals(paramType)) {
                tree.computeIfAbsent(value, k -> new LinkedHashMap<>());
                continue;
            }

            String business = stringValue(dependencies.get("business"));
            String partnerType = stringValue(dependencies.get("partner_type"));
            String country = stringValue(dependencies.get("country"));
            if (!StringUtils.hasText(business) || !StringUtils.hasText(partnerType)) {
                continue;
            }

            Map<String, Object> types = tree.get(business) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
            tree.put(business, types);

            Map<String, Object> cities = types.get(partnerType) instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
            types.put(partnerType, cities);

            if ("city".equals(paramType)) {
                if (!cities.containsKey(value) || !(cities.get(value) instanceof List<?>)) {
                    cities.put(value, new ArrayList<>());
                }
                upsertLocationMeta(cityMeta, String.join("::", business, partnerType, value), country, partnerType);
                continue;
            }

            String city = stringValue(dependencies.get("city"));
            if (!StringUtils.hasText(city)) {
                continue;
            }
            List<String> locations = cities.get(city) instanceof List<?> list
                ? new ArrayList<>(list.stream().map(this::stringValue).filter(StringUtils::hasText).toList())
                : new ArrayList<>();
            if (!locations.contains(value)) {
                locations.add(value);
                locations.sort(String::compareToIgnoreCase);
            }
            cities.put(city, locations);
            upsertLocationMeta(cityMeta, String.join("::", business, partnerType, city), country, partnerType);
            upsertLocationMeta(locationMeta, String.join("::", business, partnerType, city, value), country, partnerType);
        }

        payload.put("tree", tree);
        payload.put("city_meta", cityMeta);
        payload.put("location_meta", locationMeta);
        sharedConfigService.saveLocations(payload);
    }

    private void upsertLocationMeta(Map<String, Object> metaMap, String key, String country, String partnerType) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        Map<String, String> value = metaMap.get(key) instanceof Map<?, ?> map
            ? new LinkedHashMap<>((Map<String, String>) map)
            : new LinkedHashMap<>();
        if (StringUtils.hasText(country)) {
            value.put("country", country);
        }
        if (StringUtils.hasText(partnerType)) {
            value.put("partner_type", partnerType);
        }
        metaMap.put(key, value);
    }
}

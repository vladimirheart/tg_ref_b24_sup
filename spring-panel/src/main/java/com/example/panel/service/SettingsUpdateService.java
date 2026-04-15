package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SettingsUpdateService {

    private static final Logger log = LoggerFactory.getLogger(SettingsUpdateService.class);

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final SettingsMacroTemplateService settingsMacroTemplateService;
    private final SettingsTopLevelUpdateService settingsTopLevelUpdateService;
    private final SettingsLocationsUpdateService settingsLocationsUpdateService;
    private final SettingsParameterService settingsParameterService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public SettingsUpdateService(JdbcTemplate jdbcTemplate,
                                 SharedConfigService sharedConfigService,
                                 SettingsCatalogService settingsCatalogService,
                                 SettingsMacroTemplateService settingsMacroTemplateService,
                                 SettingsTopLevelUpdateService settingsTopLevelUpdateService,
                                 SettingsLocationsUpdateService settingsLocationsUpdateService,
                                 SettingsParameterService settingsParameterService,
                                 NotificationService notificationService,
                                 ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.settingsMacroTemplateService = settingsMacroTemplateService;
        this.settingsTopLevelUpdateService = settingsTopLevelUpdateService;
        this.settingsLocationsUpdateService = settingsLocationsUpdateService;
        this.settingsParameterService = settingsParameterService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> updateSettings(Map<String, Object> payload,
                                              Authentication authentication) {
        try {
            Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
            boolean modified = settingsTopLevelUpdateService.applyTopLevelUpdates(payload, settings);
            List<String> updateWarnings = new ArrayList<>();

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
                || payload.containsKey("dialog_ai_agent_enabled")
                || payload.containsKey("dialog_ai_agent_mode")
                || payload.containsKey("dialog_ai_agent_auto_reply_threshold")
                || payload.containsKey("dialog_ai_agent_suggest_threshold")
                || payload.containsKey("dialog_ai_agent_max_auto_replies_per_dialog")
                || payload.containsKey("dialog_sla_critical_escalation_enabled")
                || payload.containsKey("dialog_sla_critical_auto_assign_enabled")
                || payload.containsKey("dialog_sla_critical_auto_assign_to")
                || payload.containsKey("dialog_sla_critical_auto_assign_max_per_run")
                || payload.containsKey("dialog_sla_critical_auto_assign_actor")
                || payload.containsKey("dialog_sla_critical_auto_assign_rules")
                || payload.containsKey("dialog_sla_critical_auto_assign_max_open_per_operator")
                || payload.containsKey("dialog_sla_critical_auto_assign_require_categories")
                || payload.containsKey("dialog_sla_critical_auto_assign_include_assigned")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_require_layers")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_require_owner")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_require_review")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_review_ttl_hours")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct")
                || payload.containsKey("dialog_sla_critical_auto_assign_audit_block_on_conflicts")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_review_required")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_review_path")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_review_ttl_hours")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_decision_required")
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
                || payload.containsKey("dialog_workspace_single_mode")
                || payload.containsKey("dialog_workspace_force_workspace")
                || payload.containsKey("dialog_workspace_decommission_legacy_modal")
                || payload.containsKey("dialog_sla_critical_auto_sort")
                || payload.containsKey("dialog_sla_critical_pin_unassigned_only")
                || payload.containsKey("dialog_sla_critical_view_unassigned_only")
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
                || payload.containsKey("dialog_workspace_rollout_governance_packet_required")
                || payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_required")
                || payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_by")
                || payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_at")
                || payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_ttl_hours")
                || payload.containsKey("dialog_workspace_rollout_governance_review_cadence_days")
                || payload.containsKey("dialog_workspace_rollout_governance_reviewed_by")
                || payload.containsKey("dialog_workspace_rollout_governance_reviewed_at")
                || payload.containsKey("dialog_workspace_rollout_governance_review_decision_required")
                || payload.containsKey("dialog_workspace_rollout_governance_incident_followup_required")
                || payload.containsKey("dialog_workspace_rollout_governance_followup_for_non_go_required")
                || payload.containsKey("dialog_workspace_rollout_governance_review_decision_action")
                || payload.containsKey("dialog_workspace_rollout_governance_review_incident_followup")
                || payload.containsKey("dialog_workspace_rollout_governance_parity_exit_days")
                || payload.containsKey("dialog_workspace_rollout_governance_parity_critical_reasons")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_only_scenarios")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_usage_review_note")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_usage_decision")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_manual_share_max_pct")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_usage_min_workspace_open_events")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required")
                || payload.containsKey("dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n")
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
                || payload.containsKey("dialog_workspace_required_client_attributes")
                || payload.containsKey("dialog_workspace_required_client_attributes_by_segment")
                || payload.containsKey("dialog_workspace_client_context_required_sources")
                || payload.containsKey("dialog_workspace_client_context_source_priority")
                || payload.containsKey("dialog_workspace_client_context_source_stale_after_hours")
                || payload.containsKey("dialog_workspace_client_context_source_labels")
                || payload.containsKey("dialog_workspace_client_context_source_updated_at_attributes")
                || payload.containsKey("dialog_workspace_client_context_source_stale_after_hours_by_source")
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
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours")
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
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_version")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_available_fields")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required")
                || payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct")
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
                || payload.containsKey("dialog_workspace_segment_open_backlog_min_open")
                || payload.containsKey("dialog_workspace_segment_open_backlog_min_share_percent")
                || payload.containsKey("dialog_public_form_message_max_length")
                || payload.containsKey("dialog_public_form_answers_total_max_length")
                || payload.containsKey("dialog_public_form_session_ttl_hours")
                || payload.containsKey("dialog_public_form_idempotency_ttl_seconds")
                || payload.containsKey("dialog_public_form_rate_limit_enabled")
                || payload.containsKey("dialog_public_form_rate_limit_window_seconds")
                || payload.containsKey("dialog_public_form_rate_limit_max_requests")
                || payload.containsKey("dialog_public_form_rate_limit_use_fingerprint")
                || payload.containsKey("dialog_public_form_metrics_enabled")
                || payload.containsKey("dialog_public_form_alerts_enabled")
                || payload.containsKey("dialog_public_form_alert_min_views")
                || payload.containsKey("dialog_public_form_alert_error_rate_threshold")
                || payload.containsKey("dialog_public_form_alert_captcha_failure_rate_threshold")
                || payload.containsKey("dialog_public_form_alert_rate_limit_rejection_rate_threshold")
                || payload.containsKey("dialog_public_form_alert_session_lookup_miss_rate_threshold")
                || payload.containsKey("dialog_public_form_captcha_shared_secret")
                || payload.containsKey("dialog_public_form_captcha_mode")
                || payload.containsKey("dialog_public_form_turnstile_secret_key")
                || payload.containsKey("dialog_public_form_turnstile_verify_url")
                || payload.containsKey("dialog_public_form_turnstile_timeout_ms")
                || payload.containsKey("dialog_public_form_session_polling_enabled")
                || payload.containsKey("dialog_public_form_session_polling_interval_seconds")
                || payload.containsKey("dialog_public_form_session_token_rotate_on_read")
                || payload.containsKey("dialog_public_form_default_locale")
                || payload.containsKey("dialog_macro_publish_allowed_roles")
                || payload.containsKey("dialog_macro_governance_require_owner")
                || payload.containsKey("dialog_macro_governance_require_namespace")
                || payload.containsKey("dialog_macro_governance_require_review")
                || payload.containsKey("dialog_macro_governance_review_ttl_hours")
                || payload.containsKey("dialog_macro_governance_deprecation_requires_reason")
                || payload.containsKey("dialog_macro_governance_unused_days")
                || payload.containsKey("dialog_macro_governance_red_list_enabled")
                || payload.containsKey("dialog_macro_governance_red_list_usage_max")
                || payload.containsKey("dialog_macro_governance_owner_action_required")
                || payload.containsKey("dialog_macro_governance_cleanup_cadence_days")
                || payload.containsKey("dialog_macro_governance_alias_cleanup_required")
                || payload.containsKey("dialog_macro_governance_variable_cleanup_required")
                || payload.containsKey("dialog_macro_governance_usage_tier_sla_required")
                || payload.containsKey("dialog_macro_governance_usage_tier_low_max")
                || payload.containsKey("dialog_macro_governance_usage_tier_medium_max")
                || payload.containsKey("dialog_macro_governance_cleanup_sla_low_days")
                || payload.containsKey("dialog_macro_governance_cleanup_sla_medium_days")
                || payload.containsKey("dialog_macro_governance_cleanup_sla_high_days")
                || payload.containsKey("dialog_macro_governance_deprecation_sla_low_days")
                || payload.containsKey("dialog_macro_governance_deprecation_sla_medium_days")
                || payload.containsKey("dialog_macro_governance_deprecation_sla_high_days")) {
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
                if (payload.containsKey("dialog_macro_governance_require_owner")) {
                    dialogConfig.put("macro_governance_require_owner", payload.get("dialog_macro_governance_require_owner"));
                }
                if (payload.containsKey("dialog_macro_governance_require_namespace")) {
                    dialogConfig.put("macro_governance_require_namespace", payload.get("dialog_macro_governance_require_namespace"));
                }
                if (payload.containsKey("dialog_macro_governance_require_review")) {
                    dialogConfig.put("macro_governance_require_review", payload.get("dialog_macro_governance_require_review"));
                }
                if (payload.containsKey("dialog_macro_governance_review_ttl_hours")) {
                    dialogConfig.put("macro_governance_review_ttl_hours", payload.get("dialog_macro_governance_review_ttl_hours"));
                }
                if (payload.containsKey("dialog_macro_governance_deprecation_requires_reason")) {
                    dialogConfig.put("macro_governance_deprecation_requires_reason", payload.get("dialog_macro_governance_deprecation_requires_reason"));
                }
                if (payload.containsKey("dialog_macro_governance_unused_days")) {
                    dialogConfig.put("macro_governance_unused_days", payload.get("dialog_macro_governance_unused_days"));
                }
                if (payload.containsKey("dialog_macro_governance_red_list_enabled")) {
                    dialogConfig.put("macro_governance_red_list_enabled", payload.get("dialog_macro_governance_red_list_enabled"));
                }
                if (payload.containsKey("dialog_macro_governance_red_list_usage_max")) {
                    dialogConfig.put("macro_governance_red_list_usage_max", payload.get("dialog_macro_governance_red_list_usage_max"));
                }
                if (payload.containsKey("dialog_macro_governance_owner_action_required")) {
                    dialogConfig.put("macro_governance_owner_action_required", payload.get("dialog_macro_governance_owner_action_required"));
                }
                if (payload.containsKey("dialog_macro_governance_cleanup_cadence_days")) {
                    dialogConfig.put("macro_governance_cleanup_cadence_days", payload.get("dialog_macro_governance_cleanup_cadence_days"));
                }
                if (payload.containsKey("dialog_macro_governance_alias_cleanup_required")) {
                    dialogConfig.put("macro_governance_alias_cleanup_required", payload.get("dialog_macro_governance_alias_cleanup_required"));
                }
                if (payload.containsKey("dialog_macro_governance_variable_cleanup_required")) {
                    dialogConfig.put("macro_governance_variable_cleanup_required", payload.get("dialog_macro_governance_variable_cleanup_required"));
                }
                if (payload.containsKey("dialog_macro_governance_usage_tier_sla_required")) {
                    dialogConfig.put("macro_governance_usage_tier_sla_required", payload.get("dialog_macro_governance_usage_tier_sla_required"));
                }
                if (payload.containsKey("dialog_macro_governance_usage_tier_low_max")) {
                    dialogConfig.put("macro_governance_usage_tier_low_max", payload.get("dialog_macro_governance_usage_tier_low_max"));
                }
                if (payload.containsKey("dialog_macro_governance_usage_tier_medium_max")) {
                    dialogConfig.put("macro_governance_usage_tier_medium_max", payload.get("dialog_macro_governance_usage_tier_medium_max"));
                }
                if (payload.containsKey("dialog_macro_governance_cleanup_sla_low_days")) {
                    dialogConfig.put("macro_governance_cleanup_sla_low_days", payload.get("dialog_macro_governance_cleanup_sla_low_days"));
                }
                if (payload.containsKey("dialog_macro_governance_cleanup_sla_medium_days")) {
                    dialogConfig.put("macro_governance_cleanup_sla_medium_days", payload.get("dialog_macro_governance_cleanup_sla_medium_days"));
                }
                if (payload.containsKey("dialog_macro_governance_cleanup_sla_high_days")) {
                    dialogConfig.put("macro_governance_cleanup_sla_high_days", payload.get("dialog_macro_governance_cleanup_sla_high_days"));
                }
                if (payload.containsKey("dialog_macro_governance_deprecation_sla_low_days")) {
                    dialogConfig.put("macro_governance_deprecation_sla_low_days", payload.get("dialog_macro_governance_deprecation_sla_low_days"));
                }
                if (payload.containsKey("dialog_macro_governance_deprecation_sla_medium_days")) {
                    dialogConfig.put("macro_governance_deprecation_sla_medium_days", payload.get("dialog_macro_governance_deprecation_sla_medium_days"));
                }
                if (payload.containsKey("dialog_macro_governance_deprecation_sla_high_days")) {
                    dialogConfig.put("macro_governance_deprecation_sla_high_days", payload.get("dialog_macro_governance_deprecation_sla_high_days"));
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
                if (payload.containsKey("dialog_ai_agent_enabled")) {
                    dialogConfig.put("ai_agent_enabled", payload.get("dialog_ai_agent_enabled"));
                }
                if (payload.containsKey("dialog_ai_agent_mode")) {
                    dialogConfig.put("ai_agent_mode", payload.get("dialog_ai_agent_mode"));
                }
                if (payload.containsKey("dialog_ai_agent_auto_reply_threshold")) {
                    dialogConfig.put("ai_agent_auto_reply_threshold", payload.get("dialog_ai_agent_auto_reply_threshold"));
                }
                if (payload.containsKey("dialog_ai_agent_suggest_threshold")) {
                    dialogConfig.put("ai_agent_suggest_threshold", payload.get("dialog_ai_agent_suggest_threshold"));
                }
                if (payload.containsKey("dialog_ai_agent_max_auto_replies_per_dialog")) {
                    dialogConfig.put("ai_agent_max_auto_replies_per_dialog", payload.get("dialog_ai_agent_max_auto_replies_per_dialog"));
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
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_require_layers")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_require_layers", payload.get("dialog_sla_critical_auto_assign_audit_require_layers"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_require_owner")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_require_owner", payload.get("dialog_sla_critical_auto_assign_audit_require_owner"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_require_review")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_require_review", payload.get("dialog_sla_critical_auto_assign_audit_require_review"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_review_ttl_hours")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_review_ttl_hours", payload.get("dialog_sla_critical_auto_assign_audit_review_ttl_hours"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_broad_rule_coverage_pct", payload.get("dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_audit_block_on_conflicts")) {
                    dialogConfig.put("sla_critical_auto_assign_audit_block_on_conflicts", payload.get("dialog_sla_critical_auto_assign_audit_block_on_conflicts"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_governance_review_required")) {
                    dialogConfig.put("sla_critical_auto_assign_governance_review_required", payload.get("dialog_sla_critical_auto_assign_governance_review_required"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_governance_review_path")) {
                    dialogConfig.put("sla_critical_auto_assign_governance_review_path", payload.get("dialog_sla_critical_auto_assign_governance_review_path"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_governance_review_ttl_hours")) {
                    dialogConfig.put("sla_critical_auto_assign_governance_review_ttl_hours", payload.get("dialog_sla_critical_auto_assign_governance_review_ttl_hours"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required")) {
                    dialogConfig.put("sla_critical_auto_assign_governance_dry_run_ticket_required", payload.get("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required"));
                }
                if (payload.containsKey("dialog_sla_critical_auto_assign_governance_decision_required")) {
                    dialogConfig.put("sla_critical_auto_assign_governance_decision_required", payload.get("dialog_sla_critical_auto_assign_governance_decision_required"));
                }
                dialogConfig.put("sla_critical_auto_assign_governance_policy_changed_at", Instant.now().toString());
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
                if (payload.containsKey("dialog_workspace_single_mode")) {
                    dialogConfig.put("workspace_single_mode", payload.get("dialog_workspace_single_mode"));
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
                if (payload.containsKey("dialog_sla_critical_pin_unassigned_only")) {
                    dialogConfig.put("sla_critical_pin_unassigned_only", payload.get("dialog_sla_critical_pin_unassigned_only"));
                }
                if (payload.containsKey("dialog_sla_critical_view_unassigned_only")) {
                    dialogConfig.put("sla_critical_view_unassigned_only", payload.get("dialog_sla_critical_view_unassigned_only"));
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
                if (payload.containsKey("dialog_workspace_rollout_governance_packet_required")) {
                    dialogConfig.put("workspace_rollout_governance_packet_required",
                            payload.get("dialog_workspace_rollout_governance_packet_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_required")) {
                    dialogConfig.put("workspace_rollout_governance_owner_signoff_required",
                            payload.get("dialog_workspace_rollout_governance_owner_signoff_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_by")) {
                    dialogConfig.put("workspace_rollout_governance_owner_signoff_by",
                            payload.get("dialog_workspace_rollout_governance_owner_signoff_by"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_at")) {
                    dialogConfig.put("workspace_rollout_governance_owner_signoff_at",
                            payload.get("dialog_workspace_rollout_governance_owner_signoff_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_governance_owner_signoff_ttl_hours",
                            payload.get("dialog_workspace_rollout_governance_owner_signoff_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_review_cadence_days")) {
                    dialogConfig.put("workspace_rollout_governance_review_cadence_days",
                            payload.get("dialog_workspace_rollout_governance_review_cadence_days"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_reviewed_by")) {
                    dialogConfig.put("workspace_rollout_governance_reviewed_by",
                            payload.get("dialog_workspace_rollout_governance_reviewed_by"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_reviewed_at")) {
                    dialogConfig.put("workspace_rollout_governance_reviewed_at",
                            payload.get("dialog_workspace_rollout_governance_reviewed_at"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_review_decision_required")) {
                    dialogConfig.put("workspace_rollout_governance_review_decision_required",
                            payload.get("dialog_workspace_rollout_governance_review_decision_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_incident_followup_required")) {
                    dialogConfig.put("workspace_rollout_governance_incident_followup_required",
                            payload.get("dialog_workspace_rollout_governance_incident_followup_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_followup_for_non_go_required")) {
                    dialogConfig.put("workspace_rollout_governance_followup_for_non_go_required",
                            payload.get("dialog_workspace_rollout_governance_followup_for_non_go_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_review_decision_action")) {
                    dialogConfig.put("workspace_rollout_governance_review_decision_action",
                            payload.get("dialog_workspace_rollout_governance_review_decision_action"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_review_incident_followup")) {
                    dialogConfig.put("workspace_rollout_governance_review_incident_followup",
                            payload.get("dialog_workspace_rollout_governance_review_incident_followup"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_parity_exit_days")) {
                    dialogConfig.put("workspace_rollout_governance_parity_exit_days",
                            payload.get("dialog_workspace_rollout_governance_parity_exit_days"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_parity_critical_reasons")) {
                    dialogConfig.put("workspace_rollout_governance_parity_critical_reasons",
                            payload.get("dialog_workspace_rollout_governance_parity_critical_reasons"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_legacy_only_scenarios")) {
                    dialogConfig.put("workspace_rollout_governance_legacy_only_scenarios",
                            payload.get("dialog_workspace_rollout_governance_legacy_only_scenarios"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_legacy_usage_min_workspace_open_events")) {
                    dialogConfig.put("workspace_rollout_governance_legacy_usage_min_workspace_open_events",
                            payload.get("dialog_workspace_rollout_governance_legacy_usage_min_workspace_open_events"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required")) {
                    dialogConfig.put("workspace_rollout_governance_legacy_blocked_reasons_review_required",
                            payload.get("dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n")) {
                    dialogConfig.put("workspace_rollout_governance_legacy_blocked_reasons_top_n",
                            payload.get("dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n"));
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
                if (payload.containsKey("dialog_workspace_required_client_attributes")) {
                    dialogConfig.put("workspace_required_client_attributes",
                            payload.get("dialog_workspace_required_client_attributes"));
                }
                if (payload.containsKey("dialog_workspace_required_client_attributes_by_segment")) {
                    dialogConfig.put("workspace_required_client_attributes_by_segment",
                            payload.get("dialog_workspace_required_client_attributes_by_segment"));
                }
                if (payload.containsKey("dialog_workspace_client_context_required_sources")) {
                    dialogConfig.put("workspace_client_context_required_sources",
                            payload.get("dialog_workspace_client_context_required_sources"));
                }
                if (payload.containsKey("dialog_workspace_client_context_source_priority")) {
                    dialogConfig.put("workspace_client_context_source_priority",
                            payload.get("dialog_workspace_client_context_source_priority"));
                }
                if (payload.containsKey("dialog_workspace_client_context_source_stale_after_hours")) {
                    dialogConfig.put("workspace_client_context_source_stale_after_hours",
                            payload.get("dialog_workspace_client_context_source_stale_after_hours"));
                }
                if (payload.containsKey("dialog_workspace_client_context_source_labels")) {
                    dialogConfig.put("workspace_client_context_source_labels",
                            payload.get("dialog_workspace_client_context_source_labels"));
                }
                if (payload.containsKey("dialog_workspace_client_context_source_updated_at_attributes")) {
                    dialogConfig.put("workspace_client_context_source_updated_at_attributes",
                            payload.get("dialog_workspace_client_context_source_updated_at_attributes"));
                }
                if (payload.containsKey("dialog_workspace_client_context_source_stale_after_hours_by_source")) {
                    dialogConfig.put("workspace_client_context_source_stale_after_hours_by_source",
                            payload.get("dialog_workspace_client_context_source_stale_after_hours_by_source"));
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
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_owner",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at",
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at"),
                                    "Дата обновления dependency-ticket data-mart",
                                    updateWarnings));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_by")) {
                    dialogConfig.put("workspace_rollout_external_kpi_reviewed_by",
                            payload.get("dialog_workspace_rollout_external_kpi_reviewed_by"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_at")) {
                    dialogConfig.put("workspace_rollout_external_kpi_reviewed_at",
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_reviewed_at"),
                                    "Дата review внешних KPI",
                                    updateWarnings));
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
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_data_updated_at"),
                                    "Дата обновления внешнего data-mart",
                                    updateWarnings));
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
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_datamart_health_updated_at"),
                                    "Дата обновления health-сигнала data-mart",
                                    updateWarnings));
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
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_datamart_program_updated_at"),
                                    "Дата обновления программного статуса data-mart",
                                    updateWarnings));
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
                            normalizeUtcTimestampSetting(
                                    payload.get("dialog_workspace_rollout_external_kpi_datamart_target_ready_at"),
                                    "Целевой срок готовности data-mart",
                                    updateWarnings));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_timeline_grace_hours",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_version")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_version",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_version"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_mandatory_fields",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_fields",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_available_fields")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_available_fields",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_available_fields"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_coverage_required",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required"));
                }
                if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct")) {
                    dialogConfig.put("workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct",
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct"));
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
                if (payload.containsKey("dialog_workspace_segment_open_backlog_min_open")) {
                    dialogConfig.put("workspace_segment_open_backlog_min_open",
                            payload.get("dialog_workspace_segment_open_backlog_min_open"));
                }
                if (payload.containsKey("dialog_workspace_segment_open_backlog_min_share_percent")) {
                    dialogConfig.put("workspace_segment_open_backlog_min_share_percent",
                            payload.get("dialog_workspace_segment_open_backlog_min_share_percent"));
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
                if (payload.containsKey("dialog_public_form_alerts_enabled")) {
                    dialogConfig.put("public_form_alerts_enabled",
                            payload.get("dialog_public_form_alerts_enabled"));
                }
                if (payload.containsKey("dialog_public_form_alert_min_views")) {
                    dialogConfig.put("public_form_alert_min_views",
                            payload.get("dialog_public_form_alert_min_views"));
                }
                if (payload.containsKey("dialog_public_form_alert_error_rate_threshold")) {
                    dialogConfig.put("public_form_alert_error_rate_threshold",
                            payload.get("dialog_public_form_alert_error_rate_threshold"));
                }
                if (payload.containsKey("dialog_public_form_alert_captcha_failure_rate_threshold")) {
                    dialogConfig.put("public_form_alert_captcha_failure_rate_threshold",
                            payload.get("dialog_public_form_alert_captcha_failure_rate_threshold"));
                }
                if (payload.containsKey("dialog_public_form_alert_rate_limit_rejection_rate_threshold")) {
                    dialogConfig.put("public_form_alert_rate_limit_rejection_rate_threshold",
                            payload.get("dialog_public_form_alert_rate_limit_rejection_rate_threshold"));
                }
                if (payload.containsKey("dialog_public_form_alert_session_lookup_miss_rate_threshold")) {
                    dialogConfig.put("public_form_alert_session_lookup_miss_rate_threshold",
                            payload.get("dialog_public_form_alert_session_lookup_miss_rate_threshold"));
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
                validateExternalKpiDatamartContract(dialogConfig);
                settings.put("dialog_config", dialogConfig);
                modified = true;
            }

            if (modified) {
                sharedConfigService.saveSettings(settings);
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

    private String stringValue(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private String normalizeUtcTimestampSetting(Object rawValue,
                                               String label,
                                               List<String> updateWarnings) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        OffsetDateTime parsed = parseUtcTimestamp(value);
        if (parsed == null) {
            if (updateWarnings != null && StringUtils.hasText(label)) {
                updateWarnings.add(label + " сохранена как есть: значение не удалось нормализовать в UTC, "
                        + "аналитика пометит timestamp как invalid.");
            }
            return value;
        }
        return parsed.withOffsetSameInstant(ZoneOffset.UTC).toString();
    }

    private OffsetDateTime parseUtcTimestamp(String rawValue) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback to legacy datetime-local without timezone
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
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

    private void validateExternalKpiDatamartContract(Map<String, Object> dialogConfig) {
        if (dialogConfig == null || dialogConfig.isEmpty()) {
            return;
        }
        List<String> mandatoryFields = normalizeDatamartContractFieldList(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_mandatory_fields"));
        List<String> optionalFields = normalizeDatamartContractFieldList(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_fields"));
        boolean optionalCoverageRequired = asBoolean(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_coverage_required"));
        Integer optionalCoverageThreshold = parseInteger(
                dialogConfig.get("workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct"));

        if (optionalCoverageRequired && optionalFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Для optional coverage gate укажите хотя бы одно optional KPI-поле в data contract.");
        }
        if (optionalCoverageThreshold != null
                && (optionalCoverageThreshold < 0 || optionalCoverageThreshold > 100)) {
            throw new IllegalArgumentException(
                    "Порог optional coverage для data contract должен быть в диапазоне 0..100%.");
        }

        Set<String> optionalFieldSet = new LinkedHashSet<>(optionalFields);
        List<String> overlappingFields = mandatoryFields.stream()
                .filter(optionalFieldSet::contains)
                .toList();
        if (!overlappingFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Поля data contract не могут одновременно быть mandatory и optional: "
                            + String.join(", ", overlappingFields) + ".");
        }
    }

    private List<String> normalizeDatamartContractFieldList(Object rawValue) {
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String field = token != null ? token.trim() : "";
            if (StringUtils.hasText(field)) {
                normalized.add(field);
            }
        }
        return new ArrayList<>(normalized);
    }

    private Integer parseInteger(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String value = stringValue(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }


}

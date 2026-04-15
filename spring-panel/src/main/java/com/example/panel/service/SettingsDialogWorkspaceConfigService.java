package com.example.panel.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogWorkspaceConfigService {

    private final SettingsDialogConfigSupportService settingsDialogConfigSupportService;

    public SettingsDialogWorkspaceConfigService(SettingsDialogConfigSupportService settingsDialogConfigSupportService) {
        this.settingsDialogConfigSupportService = settingsDialogConfigSupportService;
    }

    public void applySettings(Map<String, Object> payload,
                              Map<String, Object> dialogConfig,
                              List<String> updateWarnings) {
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
        applyWorkspaceRolloutSettings(payload, dialogConfig, updateWarnings);
        applyWorkspaceMacroAndClientSettings(payload, dialogConfig);
        applyWorkspaceExternalKpiSettings(payload, dialogConfig, updateWarnings);
        applyWorkspaceClientAttributesSettings(payload, dialogConfig);
    }

    private void applyWorkspaceRolloutSettings(Map<String, Object> payload,
                                               Map<String, Object> dialogConfig,
                                               List<String> updateWarnings) {
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_kpi_outcome_min_samples_per_cohort", "workspace_rollout_kpi_outcome_min_samples_per_cohort");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_kpi_outcome_frt_max_relative_regression", "workspace_rollout_kpi_outcome_frt_max_relative_regression");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_kpi_outcome_ttr_max_relative_regression", "workspace_rollout_kpi_outcome_ttr_max_relative_regression");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta", "workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier", "workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_required_outcome_kpis", "workspace_rollout_required_outcome_kpis");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_winner_min_open_improvement", "workspace_rollout_winner_min_open_improvement");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_packet_required", "workspace_rollout_governance_packet_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_owner_signoff_required", "workspace_rollout_governance_owner_signoff_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_owner_signoff_by", "workspace_rollout_governance_owner_signoff_by");
        if (payload.containsKey("dialog_workspace_rollout_governance_owner_signoff_at")) {
            dialogConfig.put("workspace_rollout_governance_owner_signoff_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_governance_owner_signoff_at"),
                            "Owner sign-off timestamp",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_owner_signoff_ttl_hours", "workspace_rollout_governance_owner_signoff_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_review_cadence_days", "workspace_rollout_governance_review_cadence_days");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_reviewed_by", "workspace_rollout_governance_reviewed_by");
        if (payload.containsKey("dialog_workspace_rollout_governance_reviewed_at")) {
            dialogConfig.put("workspace_rollout_governance_reviewed_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_governance_reviewed_at"),
                            "Governance reviewed timestamp",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_review_decision_required", "workspace_rollout_governance_review_decision_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_incident_followup_required", "workspace_rollout_governance_incident_followup_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_followup_for_non_go_required", "workspace_rollout_governance_followup_for_non_go_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_review_decision_action", "workspace_rollout_governance_review_decision_action");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_review_incident_followup", "workspace_rollout_governance_review_incident_followup");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_parity_exit_days", "workspace_rollout_governance_parity_exit_days");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_parity_critical_reasons", "workspace_rollout_governance_parity_critical_reasons");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_only_scenarios", "workspace_rollout_governance_legacy_only_scenarios");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_usage_review_note", "workspace_rollout_governance_legacy_usage_review_note");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_usage_decision", "workspace_rollout_governance_legacy_usage_decision");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_manual_share_max_pct", "workspace_rollout_governance_legacy_manual_share_max_pct");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_usage_min_workspace_open_events", "workspace_rollout_governance_legacy_usage_min_workspace_open_events");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required", "workspace_rollout_governance_legacy_blocked_reasons_review_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n", "workspace_rollout_governance_legacy_blocked_reasons_top_n");
    }

    private void applyWorkspaceMacroAndClientSettings(Map<String, Object> payload,
                                                      Map<String, Object> dialogConfig) {
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_defaults", "macro_variable_defaults");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog", "macro_variable_catalog");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_url", "macro_variable_catalog_external_url");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_timeout_ms", "macro_variable_catalog_external_timeout_ms");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_cache_ttl_seconds", "macro_variable_catalog_external_cache_ttl_seconds");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_auth_header", "macro_variable_catalog_external_auth_header");
        putIfPresent(payload, dialogConfig, "dialog_macro_variable_catalog_external_auth_token", "macro_variable_catalog_external_auth_token");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_crm_profile_url_template", "workspace_client_crm_profile_url_template");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_crm_profile_label", "workspace_client_crm_profile_label");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_contract_profile_url_template", "workspace_client_contract_profile_url_template");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_contract_profile_label", "workspace_client_contract_profile_label");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_links", "workspace_client_external_links");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_profile_url", "workspace_client_external_profile_url");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_profile_timeout_ms", "workspace_client_external_profile_timeout_ms");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_profile_cache_ttl_seconds", "workspace_client_external_profile_cache_ttl_seconds");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_profile_auth_header", "workspace_client_external_profile_auth_header");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_external_profile_auth_token", "workspace_client_external_profile_auth_token");
        putIfPresent(payload, dialogConfig, "dialog_workspace_required_client_attributes", "workspace_required_client_attributes");
        putIfPresent(payload, dialogConfig, "dialog_workspace_required_client_attributes_by_segment", "workspace_required_client_attributes_by_segment");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_required_sources", "workspace_client_context_required_sources");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_source_priority", "workspace_client_context_source_priority");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_source_stale_after_hours", "workspace_client_context_source_stale_after_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_source_labels", "workspace_client_context_source_labels");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_source_updated_at_attributes", "workspace_client_context_source_updated_at_attributes");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_context_source_stale_after_hours_by_source", "workspace_client_context_source_stale_after_hours_by_source");
        putIfPresent(payload, dialogConfig, "dialog_cross_product_omnichannel_dashboard_url", "cross_product_omnichannel_dashboard_url");
        putIfPresent(payload, dialogConfig, "dialog_cross_product_omnichannel_dashboard_label", "cross_product_omnichannel_dashboard_label");
        putIfPresent(payload, dialogConfig, "dialog_cross_product_finance_dashboard_url", "cross_product_finance_dashboard_url");
        putIfPresent(payload, dialogConfig, "dialog_cross_product_finance_dashboard_label", "cross_product_finance_dashboard_label");
    }

    private void applyWorkspaceExternalKpiSettings(Map<String, Object> payload,
                                                   Map<String, Object> dialogConfig,
                                                   List<String> updateWarnings) {
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_gate_enabled", "workspace_rollout_external_kpi_gate_enabled");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_omnichannel_ready", "workspace_rollout_external_kpi_omnichannel_ready");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_finance_ready", "workspace_rollout_external_kpi_finance_ready");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_note", "workspace_rollout_external_kpi_note");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_owner", "workspace_rollout_external_kpi_datamart_owner");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_runbook_url", "workspace_rollout_external_kpi_datamart_runbook_url");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_required", "workspace_rollout_external_kpi_datamart_dependency_ticket_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_url", "workspace_rollout_external_kpi_datamart_dependency_ticket_url");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required", "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner", "workspace_rollout_external_kpi_datamart_dependency_ticket_owner");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required", "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact", "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required", "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required", "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at"),
                            "Дата обновления dependency ticket data-mart",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours", "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_reviewed_by", "workspace_rollout_external_kpi_reviewed_by");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_reviewed_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_reviewed_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_reviewed_at"),
                            "Дата review внешних KPI",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_review_ttl_hours", "workspace_rollout_external_kpi_review_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_data_freshness_required", "workspace_rollout_external_kpi_data_freshness_required");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_data_updated_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_data_updated_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_data_updated_at"),
                            "Дата обновления внешних KPI",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_data_freshness_ttl_hours", "workspace_rollout_external_kpi_data_freshness_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_dashboard_links_required", "workspace_rollout_external_kpi_dashboard_links_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_dashboard_status_required", "workspace_rollout_external_kpi_dashboard_status_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_dashboard_status", "workspace_rollout_external_kpi_dashboard_status");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_dashboard_status_note", "workspace_rollout_external_kpi_dashboard_status_note");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_owner_runbook_required", "workspace_rollout_external_kpi_owner_runbook_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_health_required", "workspace_rollout_external_kpi_datamart_health_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_health_status", "workspace_rollout_external_kpi_datamart_health_status");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_health_note", "workspace_rollout_external_kpi_datamart_health_note");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_health_freshness_required", "workspace_rollout_external_kpi_datamart_health_freshness_required");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_health_updated_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_datamart_health_updated_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_health_updated_at"),
                            "Дата обновления health data-mart",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_health_ttl_hours", "workspace_rollout_external_kpi_datamart_health_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_blocker_required", "workspace_rollout_external_kpi_datamart_program_blocker_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_status", "workspace_rollout_external_kpi_datamart_program_status");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_note", "workspace_rollout_external_kpi_datamart_program_note");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_blocker_url", "workspace_rollout_external_kpi_datamart_program_blocker_url");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_freshness_required", "workspace_rollout_external_kpi_datamart_program_freshness_required");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_program_updated_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_datamart_program_updated_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_program_updated_at"),
                            "Дата обновления программного статуса data-mart",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_program_ttl_hours", "workspace_rollout_external_kpi_datamart_program_ttl_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_timeline_required", "workspace_rollout_external_kpi_datamart_timeline_required");
        if (payload.containsKey("dialog_workspace_rollout_external_kpi_datamart_target_ready_at")) {
            dialogConfig.put("workspace_rollout_external_kpi_datamart_target_ready_at",
                    settingsDialogConfigSupportService.normalizeUtcTimestampSetting(
                            payload.get("dialog_workspace_rollout_external_kpi_datamart_target_ready_at"),
                            "Целевой срок готовности data-mart",
                            updateWarnings));
        }
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours", "workspace_rollout_external_kpi_datamart_timeline_grace_hours");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_required", "workspace_rollout_external_kpi_datamart_contract_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_version", "workspace_rollout_external_kpi_datamart_contract_version");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields", "workspace_rollout_external_kpi_datamart_contract_mandatory_fields");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields", "workspace_rollout_external_kpi_datamart_contract_optional_fields");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_available_fields", "workspace_rollout_external_kpi_datamart_contract_available_fields");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required", "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required");
        putIfPresent(payload, dialogConfig, "dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct", "workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct");
    }

    private void applyWorkspaceClientAttributesSettings(Map<String, Object> payload,
                                                        Map<String, Object> dialogConfig) {
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_hidden_attributes", "workspace_client_hidden_attributes");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_attribute_labels", "workspace_client_attribute_labels");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_attribute_order", "workspace_client_attribute_order");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_extra_attributes_max", "workspace_client_extra_attributes_max");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_extra_attributes_collapse_after", "workspace_client_extra_attributes_collapse_after");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_extra_attributes_hide_technical", "workspace_client_extra_attributes_hide_technical");
        putIfPresent(payload, dialogConfig, "dialog_workspace_client_extra_attributes_technical_prefixes", "workspace_client_extra_attributes_technical_prefixes");
        putIfPresent(payload, dialogConfig, "dialog_workspace_context_history_limit", "workspace_context_history_limit");
        putIfPresent(payload, dialogConfig, "dialog_workspace_context_related_events_limit", "workspace_context_related_events_limit");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_high_lifetime_volume_min_dialogs", "workspace_segment_high_lifetime_volume_min_dialogs");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_multi_open_dialogs_min_open", "workspace_segment_multi_open_dialogs_min_open");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_reactivation_risk_min_dialogs", "workspace_segment_reactivation_risk_min_dialogs");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_reactivation_risk_max_resolved_30d", "workspace_segment_reactivation_risk_max_resolved_30d");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_open_backlog_min_open", "workspace_segment_open_backlog_min_open");
        putIfPresent(payload, dialogConfig, "dialog_workspace_segment_open_backlog_min_share_percent", "workspace_segment_open_backlog_min_share_percent");
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

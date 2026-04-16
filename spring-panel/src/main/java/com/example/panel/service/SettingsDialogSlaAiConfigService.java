package com.example.panel.service;

import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SettingsDialogSlaAiConfigService {

    public void applySettings(Map<String, Object> payload,
                              Map<String, Object> dialogConfig) {
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
        if (payload.containsKey("dialog_ai_agent_llm_enabled")) {
            dialogConfig.put("ai_agent_llm_enabled", payload.get("dialog_ai_agent_llm_enabled"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_provider")) {
            dialogConfig.put("ai_agent_llm_provider", payload.get("dialog_ai_agent_llm_provider"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_endpoint")) {
            dialogConfig.put("ai_agent_llm_endpoint", payload.get("dialog_ai_agent_llm_endpoint"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_model")) {
            dialogConfig.put("ai_agent_llm_model", payload.get("dialog_ai_agent_llm_model"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_timeout_ms")) {
            dialogConfig.put("ai_agent_llm_timeout_ms", payload.get("dialog_ai_agent_llm_timeout_ms"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_roles")) {
            dialogConfig.put("ai_agent_llm_roles", payload.get("dialog_ai_agent_llm_roles"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_rollout_mode")) {
            dialogConfig.put("ai_agent_llm_rollout_mode", payload.get("dialog_ai_agent_llm_rollout_mode"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_rollout_percent")) {
            dialogConfig.put("ai_agent_llm_rollout_percent", payload.get("dialog_ai_agent_llm_rollout_percent"));
        }
        if (payload.containsKey("dialog_ai_agent_llm_output_guard_enabled")) {
            dialogConfig.put("ai_agent_llm_output_guard_enabled", payload.get("dialog_ai_agent_llm_output_guard_enabled"));
        }
        if (payload.containsKey("dialog_ai_agent_offline_eval_enabled")) {
            dialogConfig.put("ai_agent_offline_eval_enabled", payload.get("dialog_ai_agent_offline_eval_enabled"));
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
        if (payload.containsKey("dialog_sla_critical_auto_assign_governance_review_required")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_review_path")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_review_ttl_hours")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_dry_run_ticket_required")
                || payload.containsKey("dialog_sla_critical_auto_assign_governance_decision_required")) {
            dialogConfig.put("sla_critical_auto_assign_governance_policy_changed_at", Instant.now().toString());
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
        if (payload.containsKey("dialog_sla_critical_auto_sort")) {
            dialogConfig.put("sla_critical_auto_sort", payload.get("dialog_sla_critical_auto_sort"));
        }
        if (payload.containsKey("dialog_sla_critical_pin_unassigned_only")) {
            dialogConfig.put("sla_critical_pin_unassigned_only", payload.get("dialog_sla_critical_pin_unassigned_only"));
        }
        if (payload.containsKey("dialog_sla_critical_view_unassigned_only")) {
            dialogConfig.put("sla_critical_view_unassigned_only", payload.get("dialog_sla_critical_view_unassigned_only"));
        }
    }
}

(function () {
  if (window.SettingsPageConfigRuntime) {
    return;
  }

  let lastBuiltConfig = {};

  const DEFAULT_DIALOG_TIME_METRICS = Object.freeze({
    good_limit: 30,
    warning_limit: 60,
    colors: Object.freeze({
      good: '#d1f7d1',
      warning: '#fff4d6',
      danger: '#f8d7da',
    }),
  });

  const DEFAULT_DIALOG_SLA_CONFIG = Object.freeze({
    target_minutes: 24 * 60,
    warning_minutes: 4 * 60,
    critical_minutes: 30,
    orchestration_mode: 'autopilot',
    escalation_enabled: true,
    auto_assign_enabled: true,
    auto_assign_to: '',
    auto_assign_max_per_run: 5,
    auto_assign_actor: 'sla_orchestrator',
    auto_assign_rules: [],
    auto_assign_max_open_per_operator: null,
    auto_assign_operator_queues: {},
    sla_critical_auto_assign_audit_require_layers: false,
    sla_critical_auto_assign_audit_require_owner: false,
    sla_critical_auto_assign_audit_require_review: false,
    sla_critical_auto_assign_audit_review_ttl_hours: 168,
    sla_critical_auto_assign_audit_broad_rule_coverage_pct: 60,
    sla_critical_auto_assign_audit_block_on_conflicts: false,
    sla_critical_auto_assign_governance_review_required: false,
    sla_critical_auto_assign_governance_review_path: 'custom',
    sla_critical_auto_assign_governance_review_ttl_hours: 168,
    sla_critical_auto_assign_governance_dry_run_ticket_required: false,
    sla_critical_auto_assign_governance_decision_required: false,
    auto_assign_require_categories: false,
    sla_critical_auto_assign_include_assigned: true,
    escalation_webhook_enabled: false,
    escalation_webhook_urls: '',
    escalation_webhook_cooldown_minutes: 30,
    escalation_webhook_timeout_ms: 4000,
    escalation_webhook_retry_attempts: 1,
    escalation_webhook_retry_backoff_ms: 250,
    escalation_webhook_event_name: 'sla_critical_escalation_required',
    escalation_webhook_event_severity: 'critical',
    workspace_contract_timeout_ms: 8000,
    workspace_contract_retry_attempts: 1,
    workspace_contract_include: 'messages,context,sla,permissions',
    workspace_messages_page_limit: 50,
    workspace_disable_legacy_fallback: true,
    workspace_failure_streak_threshold: 3,
    workspace_failure_cooldown_ms: 30000,
    workspace_draft_autosave_delay_ms: 900,
    workspace_draft_telemetry_interval_ms: 30000,
    workspace_open_slo_ms: 2000,
    workspace_enabled: true,
    workspace_single_mode: false,
    workspace_force_workspace: true,
    workspace_decommission_legacy_modal: true,
    sla_critical_escalation_include_assigned: true,
    workspace_inline_navigation: true,
    sla_critical_auto_sort: true,
    sla_critical_pin_unassigned_only: false,
    sla_critical_view_unassigned_only: false,
    workspace_ab_enabled: false,
    workspace_ab_rollout_percent: 100,
    workspace_ab_experiment_name: 'workspace_v1_rollout',
    workspace_ab_operator_segment: 'all_operators',
    workspace_ab_primary_kpis: ['frt', 'ttr', 'sla_breach'],
    workspace_ab_secondary_kpis: ['dialogs_per_operator_shift', 'csat', 'ui_error_rate'],
    workspace_ab_operator_overrides: {},
    macro_require_independent_review: true,
    quick_snooze_minutes: 60,
    overdue_threshold_hours: 24,
    ai_agent_enabled: true,
    ai_agent_mode: 'auto_reply',
    ai_agent_auto_reply_threshold: 0.62,
    ai_agent_suggest_threshold: 0.46,
    ai_agent_max_auto_replies_per_dialog: 3,
    list_poll_interval_ms: 8000,
    history_poll_interval_ms: 8000,
    workspace_guardrail_render_error_rate: 0.01,
    workspace_guardrail_fallback_rate: 0.03,
    workspace_guardrail_abandon_rate: 0.10,
    workspace_guardrail_slow_open_rate: 0.05,
    workspace_dimension_min_events: 20,
    workspace_cohort_min_events: 30,
    workspace_rollout_kpi_outcome_min_samples_per_cohort: 5,
    workspace_rollout_kpi_outcome_frt_max_relative_regression: 0.10,
    workspace_rollout_kpi_outcome_ttr_max_relative_regression: 0.10,
    workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta: 0.02,
    workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier: 1.20,
    workspace_rollout_required_outcome_kpis: ['frt', 'ttr', 'sla_breach'],
    workspace_rollout_winner_min_open_improvement: 0.00,
    workspace_rollout_governance_packet_required: false,
    workspace_rollout_governance_owner_signoff_required: false,
    workspace_rollout_governance_owner_signoff_by: '',
    workspace_rollout_governance_owner_signoff_at: '',
    workspace_rollout_governance_owner_signoff_ttl_hours: 168,
    workspace_rollout_governance_review_cadence_days: 0,
    workspace_rollout_governance_reviewed_by: '',
    workspace_rollout_governance_reviewed_at: '',
    workspace_rollout_governance_review_decision_required: false,
    workspace_rollout_governance_incident_followup_required: false,
    workspace_rollout_governance_followup_for_non_go_required: false,
    workspace_rollout_governance_review_decision_action: '',
    workspace_rollout_governance_review_incident_followup: '',
    workspace_rollout_governance_parity_exit_days: 0,
    workspace_rollout_governance_parity_critical_reasons: [],
    workspace_rollout_governance_legacy_only_scenarios: [],
    workspace_rollout_governance_legacy_usage_min_workspace_open_events: '',
    workspace_rollout_governance_legacy_blocked_reasons_review_required: false,
    workspace_rollout_governance_legacy_blocked_reasons_top_n: 3,
    macro_governance_require_owner: false,
    macro_governance_require_namespace: false,
    macro_governance_require_review: false,
    macro_governance_review_ttl_hours: 168,
    macro_governance_deprecation_requires_reason: false,
    macro_governance_unused_days: 30,
    macro_governance_red_list_enabled: false,
    macro_governance_red_list_usage_max: 0,
    macro_governance_owner_action_required: false,
    macro_governance_cleanup_cadence_days: 0,
    macro_governance_alias_cleanup_required: false,
    macro_governance_variable_cleanup_required: false,
    macro_governance_usage_tier_sla_required: false,
    macro_governance_usage_tier_low_max: 0,
    macro_governance_usage_tier_medium_max: 5,
    macro_governance_cleanup_sla_low_days: 7,
    macro_governance_cleanup_sla_medium_days: 30,
    macro_governance_cleanup_sla_high_days: 90,
    macro_governance_deprecation_sla_low_days: 14,
    macro_governance_deprecation_sla_medium_days: 45,
    macro_governance_deprecation_sla_high_days: 120,
    escalation_webhook_max_tickets_per_run: 50,
    workspace_context_history_limit: 5,
    workspace_context_related_events_limit: 5,
    workspace_required_client_attributes: [],
    workspace_required_client_attributes_by_segment: {},
    workspace_client_context_required_sources: [],
    workspace_client_context_source_labels: {},
    workspace_client_context_source_priority: ['local', 'crm', 'contract', 'external'],
    workspace_client_context_source_updated_at_attributes: {},
    workspace_client_context_source_stale_after_hours: 0,
    workspace_client_context_source_stale_after_hours_by_source: {},
    workspace_segment_high_lifetime_volume_min_dialogs: 5,
    workspace_segment_multi_open_dialogs_min_open: 2,
    workspace_segment_reactivation_risk_min_dialogs: 3,
    workspace_segment_reactivation_risk_max_resolved_30d: 0,
    workspace_segment_open_backlog_min_open: 3,
    workspace_segment_open_backlog_min_share_percent: 50,
    workspace_client_extra_attributes_max: 20,
    workspace_client_extra_attributes_collapse_after: 8,
    workspace_client_extra_attributes_hide_technical: true,
    workspace_client_extra_attributes_technical_prefixes: ['_', 'internal_'],
    macro_variable_catalog_external_auth_header: 'Authorization',
    workspace_client_external_profile_timeout_ms: 2500,
    workspace_client_external_profile_cache_ttl_seconds: 120,
    workspace_client_external_profile_auth_header: 'Authorization',
    workspace_client_external_profile_auth_token: '',
    cross_product_omnichannel_dashboard_url: '',
    cross_product_omnichannel_dashboard_label: 'Omni-channel KPI dashboard',
    cross_product_finance_dashboard_url: '',
    cross_product_finance_dashboard_label: 'Финансовый KPI dashboard',
    workspace_rollout_external_kpi_gate_enabled: false,
    workspace_rollout_external_kpi_omnichannel_ready: false,
    workspace_rollout_external_kpi_finance_ready: false,
    workspace_rollout_external_kpi_note: '',
    workspace_rollout_external_kpi_datamart_owner: '',
    workspace_rollout_external_kpi_datamart_runbook_url: '',
    workspace_rollout_external_kpi_datamart_dependency_ticket_required: false,
    workspace_rollout_external_kpi_datamart_dependency_ticket_url: '',
    workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required: false,
    workspace_rollout_external_kpi_datamart_dependency_ticket_owner: '',
    workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required: false,
    workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact: '',
    workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required: false,
    workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required: false,
    workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at: '',
    workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours: 336,
    workspace_rollout_external_kpi_reviewed_by: '',
    workspace_rollout_external_kpi_reviewed_at: '',
    workspace_rollout_external_kpi_review_ttl_hours: 168,
    workspace_rollout_external_kpi_data_freshness_required: false,
    workspace_rollout_external_kpi_data_updated_at: '',
    workspace_rollout_external_kpi_data_freshness_ttl_hours: 48,
    workspace_rollout_external_kpi_dashboard_links_required: false,
    workspace_rollout_external_kpi_dashboard_status_required: false,
    workspace_rollout_external_kpi_dashboard_status: 'unknown',
    workspace_rollout_external_kpi_dashboard_status_note: '',
    workspace_rollout_external_kpi_owner_runbook_required: false,
    workspace_rollout_external_kpi_datamart_health_required: false,
    workspace_rollout_external_kpi_datamart_health_status: 'unknown',
    workspace_rollout_external_kpi_datamart_health_note: '',
    workspace_rollout_external_kpi_datamart_health_freshness_required: false,
    workspace_rollout_external_kpi_datamart_health_updated_at: '',
    workspace_rollout_external_kpi_datamart_health_ttl_hours: 48,
    workspace_rollout_external_kpi_datamart_program_blocker_required: false,
    workspace_rollout_external_kpi_datamart_program_status: 'unknown',
    workspace_rollout_external_kpi_datamart_program_note: '',
    workspace_rollout_external_kpi_datamart_program_blocker_url: '',
    workspace_rollout_external_kpi_datamart_program_freshness_required: false,
    workspace_rollout_external_kpi_datamart_program_updated_at: '',
    workspace_rollout_external_kpi_datamart_program_ttl_hours: 336,
    workspace_rollout_external_kpi_datamart_timeline_required: false,
    workspace_rollout_external_kpi_datamart_target_ready_at: '',
    workspace_rollout_external_kpi_datamart_timeline_grace_hours: 24,
    workspace_rollout_external_kpi_datamart_contract_required: false,
    workspace_rollout_external_kpi_datamart_contract_version: 'v1',
    workspace_rollout_external_kpi_datamart_contract_mandatory_fields: 'frt,ttr,sla_breach,cost_per_contact',
    workspace_rollout_external_kpi_datamart_contract_optional_fields: '',
    workspace_rollout_external_kpi_datamart_contract_available_fields: '',
    workspace_rollout_external_kpi_datamart_contract_optional_coverage_required: false,
    workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct: 80,
  });

  const DEFAULT_SUMMARY_BADGES = Object.freeze({
    default: Object.freeze({
      background: '#f1f3f5',
      text: '#1f2937',
    }),
  });

  const PARAMETER_STATE_TYPES = new Set(['partner_type', 'country', 'legal_entity', 'partner_contact']);
  const PARAMETER_STATES = ['Активен', 'Подписание', 'Заблокирован', 'Black_List', 'Закрыт'];
  const PARAMETER_FILTER_KEYS = Object.freeze({
    business: 'business',
    partner_type: 'partner_type',
    country: 'country',
    legal_entity: 'legal_entity',
    network: 'network',
    it_connection: 'it_connection_type',
    iiko_server: 'it_iiko_server',
  });
  const IT_CONNECTION_USAGE_FILTER_PARAMS = Object.freeze({
    equipment_type: 'it_connection_type',
    equipment_vendor: 'equipment_vendor',
    equipment_model: 'equipment_model',
    equipment_status: 'equipment_status',
  });
  const PARTNER_CONTACT_TYPES = Object.freeze({
    partner: 'Партнёр',
    ka: 'КА',
  });
  const PARTNER_CONTACT_PHONE_TYPES = Object.freeze({
    work: 'Рабочий',
    personal: 'Личный',
    fax: 'Факс',
    support: 'Саппорт',
  });
  const PARTNER_CONTACT_EMAIL_TYPES = Object.freeze({
    work: 'Рабочий',
    personal: 'Личный',
    common: 'Общий',
    support: 'Саппорт',
  });
  const PARTNER_CONTACT_STATE_CLASS_MAP = Object.freeze({
    Активен: 'partner-contact-card__header--state-active',
    Подписание: 'partner-contact-card__header--state-pending',
    Заблокирован: 'partner-contact-card__header--state-blocked',
    Black_List: 'partner-contact-card__header--state-blacklist',
    Закрыт: 'partner-contact-card__header--state-closed',
  });
  const CITY_PARAMETER_TYPE = 'city';
  const CITY_PARAMETER_LABEL = 'Город';

  function normalizeObject(value, fallback = {}) {
    return value && typeof value === 'object' ? value : fallback;
  }

  function normalizeArray(value, fallback = []) {
    return Array.isArray(value) ? value : fallback;
  }

  function normalizeNumber(value, fallback = 0) {
    const normalized = Number(value);
    return Number.isFinite(normalized) ? normalized : fallback;
  }

  function resolveConfigSection(raw, sectionName) {
    if (!raw || typeof raw !== 'object') {
      return {};
    }
    const section = raw[sectionName];
    return section && typeof section === 'object' ? section : {};
  }

  function resolveConfigValue(raw, sectionName, nestedKey, legacyKey) {
    const section = resolveConfigSection(raw, sectionName);
    if (Object.prototype.hasOwnProperty.call(section, nestedKey)) {
      return section[nestedKey];
    }
    return raw ? raw[legacyKey] : undefined;
  }

  function normalizeCityOptions(values) {
    if (!Array.isArray(values)) {
      return [];
    }
    const cleaned = values
      .map((value) => (typeof value === 'string' ? value.trim() : ''))
      .filter((value) => value);
    return Array.from(new Set(cleaned)).sort((left, right) => left.localeCompare(right));
  }

  function build(raw = {}) {
    lastBuiltConfig = {
      dialogConfig: normalizeObject(resolveConfigValue(raw, 'dialog', 'config', 'dialogConfig')),
      defaultDialogSlaConfig: DEFAULT_DIALOG_SLA_CONFIG,
      defaultDialogTimeMetrics: DEFAULT_DIALOG_TIME_METRICS,
      defaultSummaryBadges: DEFAULT_SUMMARY_BADGES,
      canPublishDialogMacros: Boolean(resolveConfigValue(raw, 'dialog', 'canPublishMacros', 'canPublishDialogMacros')),
      autoCloseConfig: normalizeObject(resolveConfigValue(raw, 'dialog', 'autoCloseConfig', 'autoCloseConfig')),
      autoCloseFallbackHours: normalizeNumber(resolveConfigValue(raw, 'dialog', 'autoCloseFallbackHours', 'autoCloseFallbackHours'), 24) || 24,
      fallbackDialogCategories: normalizeArray(resolveConfigValue(raw, 'dialog', 'fallbackCategories', 'fallbackDialogCategories')),
      reportingConfigInitial: normalizeObject(resolveConfigValue(raw, 'admin', 'reportingConfig', 'reportingConfigInitial')),
      managerLocationBindingsInitial: normalizeArray(resolveConfigValue(raw, 'admin', 'managerLocationBindings', 'managerLocationBindingsInitial')),
      botSettingsInitial: normalizeObject(resolveConfigValue(raw, 'channels', 'botSettings', 'botSettingsInitial')),
      botPresetDefinitions: normalizeObject(resolveConfigValue(raw, 'channels', 'botPresetDefinitions', 'botPresetDefinitions')),
      integrationNetworkInitial: normalizeObject(resolveConfigValue(raw, 'channels', 'integrationNetwork', 'integrationNetworkInitial')),
      integrationNetworkProfilesInitial: normalizeArray(resolveConfigValue(raw, 'channels', 'integrationNetworkProfiles', 'integrationNetworkProfilesInitial')),
      parameterTitles: normalizeObject(resolveConfigValue(raw, 'parameters', 'titles', 'parameterTitles')),
      parameterDependencies: normalizeObject(resolveConfigValue(raw, 'parameters', 'dependencies', 'parameterDependencies')),
      parameterStateTypes: PARAMETER_STATE_TYPES,
      parameterStates: PARAMETER_STATES,
      parameterFilterKeys: PARAMETER_FILTER_KEYS,
      itConnectionUsageFilterParams: IT_CONNECTION_USAGE_FILTER_PARAMS,
      partnerContactTypes: PARTNER_CONTACT_TYPES,
      partnerContactPhoneTypes: PARTNER_CONTACT_PHONE_TYPES,
      partnerContactEmailTypes: PARTNER_CONTACT_EMAIL_TYPES,
      partnerContactStateClassMap: PARTNER_CONTACT_STATE_CLASS_MAP,
      cityParameterType: CITY_PARAMETER_TYPE,
      cityParameterLabel: CITY_PARAMETER_LABEL,
      itConnectionFallbackLabels: normalizeObject(resolveConfigValue(raw, 'parameters', 'itConnectionFallbackLabels', 'itConnectionFallbackLabels')),
      itConnectionCategoryFields: normalizeObject(resolveConfigValue(raw, 'parameters', 'itConnectionCategoryFields', 'itConnectionCategoryFields')),
      cityOptions: normalizeCityOptions(resolveConfigValue(raw, 'parameters', 'cityOptions', 'cityOptions')),
      contractUsageData: normalizeObject(resolveConfigValue(raw, 'parameters', 'contractUsageData', 'contractUsageData')),
      networkProfilesInitial: normalizeArray(resolveConfigValue(raw, 'parameters', 'networkProfiles', 'networkProfilesInitial')),
      statusUsage: normalizeObject(resolveConfigValue(raw, 'appearance', 'statusUsage', 'statusUsage')),
      initialClientStatuses: normalizeArray(resolveConfigValue(raw, 'appearance', 'clientStatuses', 'initialClientStatuses')),
      initialClientStatusColors: normalizeObject(resolveConfigValue(raw, 'appearance', 'clientStatusColors', 'initialClientStatusColors')),
      initialBusinessCellStyles: normalizeObject(resolveConfigValue(raw, 'appearance', 'businessCellStyles', 'initialBusinessCellStyles')),
      locationsInitial: normalizeObject(resolveConfigValue(raw, 'locations', 'tree', 'locationsInitial')),
      locationsIikoServerSourcesInitial: normalizeArray(resolveConfigValue(raw, 'locations', 'iikoServerSources', 'locationsIikoServerSourcesInitial')),
      locationsIikoSyncSettingsInitial: normalizeObject(resolveConfigValue(raw, 'locations', 'iikoSyncSettings', 'locationsIikoSyncSettingsInitial')),
    };
    return lastBuiltConfig;
  }

  function getLastConfig() {
    return normalizeObject(lastBuiltConfig);
  }

  window.SettingsPageConfigRuntime = Object.freeze({
    build,
    getLastConfig,
    getBotSettingsInitial() {
      return normalizeObject(lastBuiltConfig.botSettingsInitial);
    },
    getBotPresetDefinitions() {
      return normalizeObject(lastBuiltConfig.botPresetDefinitions);
    },
  });
}());

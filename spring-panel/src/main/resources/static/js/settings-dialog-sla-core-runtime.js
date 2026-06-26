(function () {
  if (window.SettingsDialogSlaCoreRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    let workspaceSingleModeBound = false;
    let autoAssignExampleBound = false;

    function getDialogConfig() {
      const value = typeof options.getDialogConfig === 'function' ? options.getDialogConfig() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getDefaultDialogSlaConfig() {
      const value = typeof options.getDefaultDialogSlaConfig === 'function'
        ? options.getDefaultDialogSlaConfig()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getInputs() {
      return {
        target: document.getElementById('dialogSlaTargetMinutes'),
        warning: document.getElementById('dialogSlaWarningMinutes'),
        critical: document.getElementById('dialogSlaCriticalMinutes'),
        orchestrationMode: document.getElementById('dialogSlaCriticalOrchestrationMode'),
        aiAgentEnabled: document.getElementById('dialogAiAgentEnabled'),
        aiAgentMode: document.getElementById('dialogAiAgentMode'),
        aiAgentAutoReplyThreshold: document.getElementById('dialogAiAgentAutoReplyThreshold'),
        aiAgentSuggestThreshold: document.getElementById('dialogAiAgentSuggestThreshold'),
        aiAgentMaxAutoRepliesPerDialog: document.getElementById('dialogAiAgentMaxAutoRepliesPerDialog'),
        escalationEnabled: document.getElementById('dialogSlaEscalationEnabled'),
        autoAssignEnabled: document.getElementById('dialogSlaAutoAssignEnabled'),
        autoAssignTo: document.getElementById('dialogSlaAutoAssignTo'),
        autoAssignMaxPerRun: document.getElementById('dialogSlaAutoAssignMaxPerRun'),
        autoAssignActor: document.getElementById('dialogSlaAutoAssignActor'),
        autoAssignMaxOpenPerOperator: document.getElementById('dialogSlaAutoAssignMaxOpenPerOperator'),
        autoAssignRequireCategories: document.getElementById('dialogSlaAutoAssignRequireCategories'),
        autoAssignIncludeAssigned: document.getElementById('dialogSlaAutoAssignIncludeAssigned'),
        autoAssignRules: document.getElementById('dialogSlaAutoAssignRules'),
        autoAssignOperatorSkills: document.getElementById('dialogSlaAutoAssignOperatorSkills'),
        autoAssignOperatorQueues: document.getElementById('dialogSlaAutoAssignOperatorQueues'),
        slaPolicyAuditRequireLayers: document.getElementById('dialogSlaPolicyAuditRequireLayers'),
        slaPolicyAuditRequireOwner: document.getElementById('dialogSlaPolicyAuditRequireOwner'),
        slaPolicyAuditRequireReview: document.getElementById('dialogSlaPolicyAuditRequireReview'),
        slaPolicyAuditReviewTtlHours: document.getElementById('dialogSlaPolicyAuditReviewTtlHours'),
        slaPolicyAuditBroadRuleCoveragePct: document.getElementById('dialogSlaPolicyAuditBroadRuleCoveragePct'),
        slaPolicyAuditBlockOnConflicts: document.getElementById('dialogSlaPolicyAuditBlockOnConflicts'),
        slaPolicyGovernanceReviewRequired: document.getElementById('dialogSlaPolicyGovernanceReviewRequired'),
        slaPolicyGovernanceReviewPath: document.getElementById('dialogSlaPolicyGovernanceReviewPath'),
        slaPolicyGovernanceReviewTtlHours: document.getElementById('dialogSlaPolicyGovernanceReviewTtlHours'),
        slaPolicyGovernanceDryRunTicketRequired: document.getElementById('dialogSlaPolicyGovernanceDryRunTicketRequired'),
        slaPolicyGovernanceDecisionRequired: document.getElementById('dialogSlaPolicyGovernanceDecisionRequired'),
        autoAssignInsertExampleBtn: document.getElementById('dialogSlaAutoAssignInsertExampleBtn'),
        escalationWebhookEnabled: document.getElementById('dialogSlaEscalationWebhookEnabled'),
        escalationIncludeAssigned: document.getElementById('dialogSlaEscalationIncludeAssigned'),
        escalationWebhookUrls: document.getElementById('dialogSlaEscalationWebhookUrls'),
        escalationWebhookCooldownMinutes: document.getElementById('dialogSlaEscalationWebhookCooldown'),
        escalationWebhookTimeoutMs: document.getElementById('dialogSlaEscalationWebhookTimeout'),
        escalationWebhookRetryAttempts: document.getElementById('dialogSlaEscalationWebhookRetryAttempts'),
        escalationWebhookRetryBackoffMs: document.getElementById('dialogSlaEscalationWebhookRetryBackoff'),
        escalationWebhookEventName: document.getElementById('dialogSlaEscalationWebhookEventName'),
        escalationWebhookSeverity: document.getElementById('dialogSlaEscalationWebhookSeverity'),
        escalationWebhookMaxTicketsPerRun: document.getElementById('dialogSlaEscalationWebhookMaxTicketsPerRun'),
        slaWindowPresets: document.getElementById('dialogSlaWindowPresets'),
        slaWindowDefault: document.getElementById('dialogSlaWindowDefault'),
        defaultView: document.getElementById('dialogDefaultView'),
        quickSnoozeMinutes: document.getElementById('dialogQuickSnoozeMinutes'),
        overdueThresholdHours: document.getElementById('dialogOverdueThresholdHours'),
        workspaceEnabled: document.getElementById('dialogWorkspaceEnabled'),
        workspaceSingleMode: document.getElementById('dialogWorkspaceSingleMode'),
        slaCriticalAutoSort: document.getElementById('dialogSlaCriticalAutoSort'),
        slaCriticalPinUnassignedOnly: document.getElementById('dialogSlaCriticalPinUnassignedOnly'),
        slaCriticalViewUnassignedOnly: document.getElementById('dialogSlaCriticalViewUnassignedOnly'),
        workspaceForceWorkspace: document.getElementById('dialogWorkspaceForceWorkspace'),
        workspaceDecommissionLegacyModal: document.getElementById('dialogWorkspaceDecommissionLegacyModal'),
        workspaceAbEnabled: document.getElementById('dialogWorkspaceAbEnabled'),
        workspaceInlineNavigation: document.getElementById('dialogWorkspaceInlineNavigation'),
        workspaceContractTimeout: document.getElementById('dialogWorkspaceContractTimeoutMs'),
        workspaceRetryAttempts: document.getElementById('dialogWorkspaceRetryAttempts'),
        workspaceContractInclude: document.getElementById('dialogWorkspaceContractInclude'),
        workspaceMessagesPageLimit: document.getElementById('dialogWorkspaceMessagesPageLimit'),
        workspaceDisableLegacyFallback: document.getElementById('dialogWorkspaceDisableLegacyFallback'),
        workspaceFailureStreakThreshold: document.getElementById('dialogWorkspaceFailureStreakThreshold'),
        workspaceFailureCooldownMs: document.getElementById('dialogWorkspaceFailureCooldownMs'),
        workspaceDraftAutosaveDelay: document.getElementById('dialogWorkspaceDraftAutosaveDelayMs'),
        workspaceDraftTelemetryInterval: document.getElementById('dialogWorkspaceDraftTelemetryIntervalMs'),
        workspaceOpenSloMs: document.getElementById('dialogWorkspaceOpenSloMs'),
        listPollIntervalMs: document.getElementById('dialogListPollIntervalMs'),
        historyPollIntervalMs: document.getElementById('dialogHistoryPollIntervalMs'),
        workspaceAbRolloutPercent: document.getElementById('dialogWorkspaceAbRolloutPercent'),
        workspaceAbExperimentName: document.getElementById('dialogWorkspaceAbExperimentName'),
        workspaceAbOperatorSegment: document.getElementById('dialogWorkspaceAbOperatorSegment'),
        workspaceAbPrimaryKpis: document.getElementById('dialogWorkspaceAbPrimaryKpis'),
        workspaceAbSecondaryKpis: document.getElementById('dialogWorkspaceAbSecondaryKpis'),
        workspaceAbOperatorOverrides: document.getElementById('dialogWorkspaceAbOperatorOverrides'),
        guardrailRenderErrorRate: document.getElementById('dialogWorkspaceGuardrailRenderErrorRate'),
        guardrailFallbackRate: document.getElementById('dialogWorkspaceGuardrailFallbackRate'),
        guardrailAbandonRate: document.getElementById('dialogWorkspaceGuardrailAbandonRate'),
        guardrailSlowOpenRate: document.getElementById('dialogWorkspaceGuardrailSlowOpenRate'),
        dimensionMinEvents: document.getElementById('dialogWorkspaceDimensionMinEvents'),
        cohortMinEvents: document.getElementById('dialogWorkspaceCohortMinEvents'),
        kpiOutcomeMinSamples: document.getElementById('dialogWorkspaceKpiOutcomeMinSamples'),
        kpiOutcomeFrtRegressionPercent: document.getElementById('dialogWorkspaceKpiOutcomeFrtRegressionPercent'),
        kpiOutcomeTtrRegressionPercent: document.getElementById('dialogWorkspaceKpiOutcomeTtrRegressionPercent'),
        kpiOutcomeSlaAbsDeltaPercent: document.getElementById('dialogWorkspaceKpiOutcomeSlaAbsDeltaPercent'),
        kpiOutcomeSlaRelativeMultiplier: document.getElementById('dialogWorkspaceKpiOutcomeSlaRelativeMultiplier'),
        rolloutRequiredOutcomeKpis: document.getElementById('dialogWorkspaceRolloutRequiredOutcomeKpis'),
        winnerMinOpenImprovementPercent: document.getElementById('dialogWorkspaceWinnerMinOpenImprovementPercent'),
        macroVariableDefaults: document.getElementById('dialogMacroVariableDefaults'),
        macroVariableCatalog: document.getElementById('dialogMacroVariableCatalog'),
        macroVariableCatalogExternalUrl: document.getElementById('dialogMacroVariableCatalogExternalUrl'),
        macroVariableCatalogExternalTimeoutMs: document.getElementById('dialogMacroVariableCatalogExternalTimeoutMs'),
        macroVariableCatalogExternalCacheTtlSeconds: document.getElementById('dialogMacroVariableCatalogExternalCacheTtlSeconds'),
        macroVariableCatalogExternalAuthHeader: document.getElementById('dialogMacroVariableCatalogExternalAuthHeader'),
        macroVariableCatalogExternalAuthToken: document.getElementById('dialogMacroVariableCatalogExternalAuthToken'),
        macroPublishAllowedRoles: document.getElementById('dialogMacroPublishAllowedRoles'),
        macroRequireIndependentReview: document.getElementById('dialogMacroRequireIndependentReview'),
        workspaceClientCrmProfileUrlTemplate: document.getElementById('dialogWorkspaceClientCrmProfileUrlTemplate'),
        workspaceClientCrmProfileLabel: document.getElementById('dialogWorkspaceClientCrmProfileLabel'),
        workspaceClientContractProfileUrlTemplate: document.getElementById('dialogWorkspaceClientContractProfileUrlTemplate'),
        workspaceClientContractProfileLabel: document.getElementById('dialogWorkspaceClientContractProfileLabel'),
        workspaceClientExternalLinks: document.getElementById('dialogWorkspaceClientExternalLinks'),
        workspaceClientExternalProfileUrl: document.getElementById('dialogWorkspaceClientExternalProfileUrl'),
        workspaceClientExternalProfileTimeoutMs: document.getElementById('dialogWorkspaceClientExternalProfileTimeoutMs'),
        workspaceClientExternalProfileCacheTtlSeconds: document.getElementById('dialogWorkspaceClientExternalProfileCacheTtlSeconds'),
        workspaceClientExternalProfileAuthHeader: document.getElementById('dialogWorkspaceClientExternalProfileAuthHeader'),
        workspaceClientExternalProfileAuthToken: document.getElementById('dialogWorkspaceClientExternalProfileAuthToken'),
        workspaceClientContextRequiredSources: document.getElementById('dialogWorkspaceClientContextRequiredSources'),
        workspaceClientContextSourcePriority: document.getElementById('dialogWorkspaceClientContextSourcePriority'),
        workspaceClientContextSourceStaleAfterHours: document.getElementById('dialogWorkspaceClientContextSourceStaleAfterHours'),
        workspaceClientContextSourceLabels: document.getElementById('dialogWorkspaceClientContextSourceLabels'),
        workspaceClientContextSourceUpdatedAtAttributes: document.getElementById('dialogWorkspaceClientContextSourceUpdatedAtAttributes'),
        workspaceClientContextSourceStaleAfterHoursBySource: document.getElementById('dialogWorkspaceClientContextSourceStaleAfterHoursBySource'),
        workspaceRequiredClientAttributes: document.getElementById('dialogWorkspaceRequiredClientAttributes'),
        workspaceRequiredClientAttributesBySegment: document.getElementById('dialogWorkspaceRequiredClientAttributesBySegment'),
        macroGovernanceRequireOwner: document.getElementById('dialogMacroGovernanceRequireOwner'),
        macroGovernanceRequireNamespace: document.getElementById('dialogMacroGovernanceRequireNamespace'),
        macroGovernanceRequireReview: document.getElementById('dialogMacroGovernanceRequireReview'),
        macroGovernanceReviewTtlHours: document.getElementById('dialogMacroGovernanceReviewTtlHours'),
        macroGovernanceDeprecationRequiresReason: document.getElementById('dialogMacroGovernanceDeprecationRequiresReason'),
        macroGovernanceUnusedDays: document.getElementById('dialogMacroGovernanceUnusedDays'),
        macroGovernanceRedListEnabled: document.getElementById('dialogMacroGovernanceRedListEnabled'),
        macroGovernanceRedListUsageMax: document.getElementById('dialogMacroGovernanceRedListUsageMax'),
        macroGovernanceOwnerActionRequired: document.getElementById('dialogMacroGovernanceOwnerActionRequired'),
        macroGovernanceCleanupCadenceDays: document.getElementById('dialogMacroGovernanceCleanupCadenceDays'),
        macroGovernanceAliasCleanupRequired: document.getElementById('dialogMacroGovernanceAliasCleanupRequired'),
        macroGovernanceVariableCleanupRequired: document.getElementById('dialogMacroGovernanceVariableCleanupRequired'),
        macroGovernanceUsageTierSlaRequired: document.getElementById('dialogMacroGovernanceUsageTierSlaRequired'),
        macroGovernanceUsageTierLowMax: document.getElementById('dialogMacroGovernanceUsageTierLowMax'),
        macroGovernanceUsageTierMediumMax: document.getElementById('dialogMacroGovernanceUsageTierMediumMax'),
        macroGovernanceCleanupSlaLowDays: document.getElementById('dialogMacroGovernanceCleanupSlaLowDays'),
        macroGovernanceCleanupSlaMediumDays: document.getElementById('dialogMacroGovernanceCleanupSlaMediumDays'),
        macroGovernanceCleanupSlaHighDays: document.getElementById('dialogMacroGovernanceCleanupSlaHighDays'),
        macroGovernanceDeprecationSlaLowDays: document.getElementById('dialogMacroGovernanceDeprecationSlaLowDays'),
        macroGovernanceDeprecationSlaMediumDays: document.getElementById('dialogMacroGovernanceDeprecationSlaMediumDays'),
        macroGovernanceDeprecationSlaHighDays: document.getElementById('dialogMacroGovernanceDeprecationSlaHighDays'),
        workspaceClientHiddenAttributes: document.getElementById('dialogWorkspaceClientHiddenAttributes'),
        workspaceClientAttributeLabels: document.getElementById('dialogWorkspaceClientAttributeLabels'),
        workspaceClientAttributeOrder: document.getElementById('dialogWorkspaceClientAttributeOrder'),
        workspaceClientExtraAttributesMax: document.getElementById('dialogWorkspaceClientExtraAttributesMax'),
        workspaceClientExtraAttributesCollapseAfter: document.getElementById('dialogWorkspaceClientExtraAttributesCollapseAfter'),
        workspaceClientExtraAttributesHideTechnical: document.getElementById('dialogWorkspaceClientExtraAttributesHideTechnical'),
        workspaceClientExtraAttributesTechnicalPrefixes: document.getElementById('dialogWorkspaceClientExtraAttributesTechnicalPrefixes'),
        workspaceContextHistoryLimit: document.getElementById('dialogWorkspaceContextHistoryLimit'),
        workspaceContextRelatedEventsLimit: document.getElementById('dialogWorkspaceContextRelatedEventsLimit'),
        workspaceSegmentHighLifetimeMinDialogs: document.getElementById('dialogWorkspaceSegmentHighLifetimeMinDialogs'),
        workspaceSegmentMultiOpenMinDialogs: document.getElementById('dialogWorkspaceSegmentMultiOpenMinDialogs'),
        workspaceSegmentReactivationMinDialogs: document.getElementById('dialogWorkspaceSegmentReactivationMinDialogs'),
        workspaceSegmentReactivationMaxResolved30d: document.getElementById('dialogWorkspaceSegmentReactivationMaxResolved30d'),
        workspaceSegmentOpenBacklogMinOpen: document.getElementById('dialogWorkspaceSegmentOpenBacklogMinOpen'),
        workspaceSegmentOpenBacklogMinSharePercent: document.getElementById('dialogWorkspaceSegmentOpenBacklogMinSharePercent'),
      };
    }

    function syncWorkspaceSingleModeControls(inputs = getInputs()) {
      const singleMode = inputs.workspaceSingleMode instanceof HTMLInputElement && inputs.workspaceSingleMode.checked;
      if (!(inputs.workspaceEnabled instanceof HTMLInputElement)) {
        return;
      }
      if (singleMode) {
        inputs.workspaceEnabled.checked = true;
      }
      inputs.workspaceEnabled.disabled = singleMode;

      const lockableControls = [
        inputs.workspaceForceWorkspace,
        inputs.workspaceDecommissionLegacyModal,
        inputs.workspaceDisableLegacyFallback,
      ];
      lockableControls.forEach((control) => {
        if (!(control instanceof HTMLInputElement)) {
          return;
        }
        if (singleMode) {
          control.checked = true;
        }
        control.disabled = singleMode;
      });
      if (inputs.workspaceAbEnabled instanceof HTMLInputElement) {
        if (singleMode) {
          inputs.workspaceAbEnabled.checked = false;
        }
        inputs.workspaceAbEnabled.disabled = singleMode;
      }
    }

    function initWorkspaceSingleModeControls() {
      const inputs = getInputs();
      if (!(inputs.workspaceSingleMode instanceof HTMLInputElement)) {
        return;
      }
      if (!workspaceSingleModeBound) {
        inputs.workspaceSingleMode.addEventListener('change', () => syncWorkspaceSingleModeControls(inputs));
        workspaceSingleModeBound = true;
      }
      syncWorkspaceSingleModeControls(inputs);
    }

    function validateSlaAutoAssignRules(rules) {
      const errors = [];
      const normalizedRules = [];
      const allowedPoolStrategies = new Set(['hash_by_ticket', 'round_robin', 'least_loaded']);
      const allowedCategoryModes = new Set(['any', 'all']);
      const allowedSlaStates = new Set(['breached', 'at_risk', 'normal', 'closed']);

      const asArray = (value) => Array.isArray(value)
        ? value.map((item) => String(item || '').trim()).filter(Boolean)
        : [];

      rules.forEach((rule, index) => {
        if (!rule || typeof rule !== 'object' || Array.isArray(rule)) {
          errors.push(`Правило auto-assign #${index + 1}: ожидается JSON-объект.`);
          return;
        }
        const candidate = { ...rule };
        const assignTo = String(candidate.assign_to || '').trim();
        if (assignTo) {
          candidate.assign_to = assignTo;
        }
        const assignToPool = asArray(candidate.assign_to_pool);
        if (!assignTo && assignToPool.length === 0) {
          errors.push(`Правило auto-assign #${index + 1}: укажите assign_to или assign_to_pool.`);
        }
        if (assignTo && assignToPool.length > 0) {
          errors.push(`Правило auto-assign #${index + 1}: используйте только один тип маршрутизации (assign_to или assign_to_pool).`);
        }
        if (assignToPool.length > 0) {
          candidate.assign_to_pool = Array.from(new Set(assignToPool));
        }
        const strategy = String(candidate.assign_to_pool_strategy || '').trim().toLowerCase();
        if (strategy && !allowedPoolStrategies.has(strategy)) {
          errors.push(`Правило auto-assign #${index + 1}: assign_to_pool_strategy должен быть hash_by_ticket|round_robin|least_loaded.`);
        }
        if (strategy) {
          candidate.assign_to_pool_strategy = strategy;
        }

        const categoryMode = String(candidate.match_categories_mode || '').trim().toLowerCase();
        if (categoryMode && !allowedCategoryModes.has(categoryMode)) {
          errors.push(`Правило auto-assign #${index + 1}: match_categories_mode должен быть any|all.`);
        }
        if (categoryMode) {
          candidate.match_categories_mode = categoryMode;
        }

        const parseBoundedInt = (value, min, max) => {
          if (value === null || value === undefined || String(value).trim() === '') {
            return null;
          }
          const parsed = Math.round(Number(value));
          if (!Number.isFinite(parsed) || parsed < min || parsed > max) {
            return null;
          }
          return parsed;
        };

        const ratingMin = parseBoundedInt(candidate.match_rating_min, 0, 100);
        const ratingMax = parseBoundedInt(candidate.match_rating_max, 0, 100);
        if (candidate.match_rating_min !== undefined && ratingMin === null) {
          errors.push(`Правило auto-assign #${index + 1}: match_rating_min должен быть в диапазоне 0..100.`);
        }
        if (candidate.match_rating_max !== undefined && ratingMax === null) {
          errors.push(`Правило auto-assign #${index + 1}: match_rating_max должен быть в диапазоне 0..100.`);
        }
        if (ratingMin !== null && ratingMax !== null && ratingMin > ratingMax) {
          errors.push(`Правило auto-assign #${index + 1}: match_rating_min не может быть больше match_rating_max.`);
        }

        const unreadMin = parseBoundedInt(candidate.match_unread_min, 0, 10000);
        const unreadMax = parseBoundedInt(candidate.match_unread_max, 0, 10000);
        if (candidate.match_unread_min !== undefined && unreadMin === null) {
          errors.push(`Правило auto-assign #${index + 1}: match_unread_min должен быть в диапазоне 0..10000.`);
        }
        if (candidate.match_unread_max !== undefined && unreadMax === null) {
          errors.push(`Правило auto-assign #${index + 1}: match_unread_max должен быть в диапазоне 0..10000.`);
        }
        if (unreadMin !== null && unreadMax !== null && unreadMin > unreadMax) {
          errors.push(`Правило auto-assign #${index + 1}: match_unread_min не может быть больше match_unread_max.`);
        }

        const rawSlaStates = [];
        if (candidate.match_sla_state !== undefined && candidate.match_sla_state !== null) {
          rawSlaStates.push(candidate.match_sla_state);
        }
        if (Array.isArray(candidate.match_sla_states)) {
          rawSlaStates.push(...candidate.match_sla_states);
        }
        const slaStates = asArray(rawSlaStates).map((state) => state.toLowerCase());
        const invalidSlaState = slaStates.find((state) => !allowedSlaStates.has(state));
        if (invalidSlaState) {
          errors.push(`Правило auto-assign #${index + 1}: недопустимый SLA-state «${invalidSlaState}».`);
        }

        normalizedRules.push(candidate);
      });

      return { errors, normalizedRules };
    }

    function bindAutoAssignRulesHelpers() {
      const { autoAssignRules, autoAssignInsertExampleBtn } = getInputs();
      if (!autoAssignRules || !autoAssignInsertExampleBtn) {
        return;
      }
      if (autoAssignExampleBound) {
        return;
      }
      autoAssignInsertExampleBtn.addEventListener('click', () => {
        const example = [
          {
            rule_id: 'vip_escalation',
            name: 'VIP / высокий рейтинг',
            match_client_statuses: ['vip', 'premium'],
            match_rating_min: 80,
            match_sla_states: ['breached', 'at_risk'],
            assign_to_pool: ['lead_support', 'senior_support'],
            assign_to_pool_strategy: 'least_loaded',
            required_assignee_skills: ['vip'],
            required_assignee_queues: ['queue_enterprise'],
            priority: 100,
          },
          {
            rule_id: 'billing_priority',
            match_categories: ['billing'],
            match_categories_mode: 'all',
            match_minutes_left_lte: 20,
            assign_to: 'billing_oncall',
            priority: 80,
          },
        ];
        autoAssignRules.value = JSON.stringify(example, null, 2);
      });
      autoAssignExampleBound = true;
    }

    function initDialogSlaControls() {
      const inputs = getInputs();
      const dialogConfig = getDialogConfig();
      const defaults = getDefaultDialogSlaConfig();

      if (!inputs.target || !inputs.warning || !inputs.critical) {
        return;
      }
      const target = Number.parseInt(dialogConfig.sla_target_minutes, 10);
      const warning = Number.parseInt(dialogConfig.sla_warning_minutes, 10);
      const critical = Number.parseInt(dialogConfig.sla_critical_minutes, 10);

      inputs.target.value = Number.isFinite(target) && target > 0 ? target : defaults.target_minutes;
      inputs.warning.value = Number.isFinite(warning) && warning > 0 ? warning : defaults.warning_minutes;
      inputs.critical.value = Number.isFinite(critical) && critical > 0 ? critical : defaults.critical_minutes;
      if (inputs.orchestrationMode) {
        const mode = String(dialogConfig.sla_critical_orchestration_mode || defaults.orchestration_mode).trim().toLowerCase();
        const allowedModes = new Set(['monitor', 'assist', 'autopilot']);
        inputs.orchestrationMode.value = allowedModes.has(mode) ? mode : defaults.orchestration_mode;
      }
      if (inputs.aiAgentEnabled instanceof HTMLInputElement) {
        inputs.aiAgentEnabled.checked = dialogConfig.ai_agent_enabled !== false;
      }
      if (inputs.aiAgentMode) {
        const mode = String(dialogConfig.ai_agent_mode || defaults.ai_agent_mode).trim().toLowerCase();
        const allowedModes = new Set(['auto_reply', 'assist_only', 'escalate_only']);
        inputs.aiAgentMode.value = allowedModes.has(mode) ? mode : defaults.ai_agent_mode;
      }
      if (inputs.aiAgentAutoReplyThreshold) {
        const value = Number.parseFloat(dialogConfig.ai_agent_auto_reply_threshold);
        inputs.aiAgentAutoReplyThreshold.value = Number.isFinite(value) && value >= 0.2 && value <= 0.95
          ? value.toFixed(2)
          : defaults.ai_agent_auto_reply_threshold.toFixed(2);
      }
      if (inputs.aiAgentSuggestThreshold) {
        const value = Number.parseFloat(dialogConfig.ai_agent_suggest_threshold);
        inputs.aiAgentSuggestThreshold.value = Number.isFinite(value) && value >= 0.1 && value <= 0.95
          ? value.toFixed(2)
          : defaults.ai_agent_suggest_threshold.toFixed(2);
      }
      if (inputs.aiAgentMaxAutoRepliesPerDialog) {
        const value = Number.parseInt(dialogConfig.ai_agent_max_auto_replies_per_dialog, 10);
        inputs.aiAgentMaxAutoRepliesPerDialog.value = Number.isFinite(value) && value >= 1 && value <= 20
          ? value
          : defaults.ai_agent_max_auto_replies_per_dialog;
      }
      if (inputs.escalationEnabled instanceof HTMLInputElement) {
        inputs.escalationEnabled.checked = dialogConfig.sla_critical_escalation_enabled !== false;
      }
      if (inputs.autoAssignEnabled instanceof HTMLInputElement) {
        inputs.autoAssignEnabled.checked = Boolean(dialogConfig.sla_critical_auto_assign_enabled);
      }
      if (inputs.autoAssignTo) {
        inputs.autoAssignTo.value = typeof dialogConfig.sla_critical_auto_assign_to === 'string'
          ? dialogConfig.sla_critical_auto_assign_to
          : defaults.auto_assign_to;
      }
      if (inputs.autoAssignMaxPerRun) {
        const autoAssignMax = Number.parseInt(dialogConfig.sla_critical_auto_assign_max_per_run, 10);
        inputs.autoAssignMaxPerRun.value = Number.isFinite(autoAssignMax) && autoAssignMax >= 1
          ? autoAssignMax
          : defaults.auto_assign_max_per_run;
      }
      if (inputs.autoAssignActor) {
        const actor = String(dialogConfig.sla_critical_auto_assign_actor || '').trim();
        inputs.autoAssignActor.value = actor || defaults.auto_assign_actor;
      }
      if (inputs.autoAssignMaxOpenPerOperator) {
        const maxOpen = Number.parseInt(dialogConfig.sla_critical_auto_assign_max_open_per_operator, 10);
        inputs.autoAssignMaxOpenPerOperator.value = Number.isFinite(maxOpen) && maxOpen >= 1
          ? Math.min(5000, maxOpen)
          : '';
      }
      if (inputs.autoAssignRequireCategories instanceof HTMLInputElement) {
        inputs.autoAssignRequireCategories.checked = Boolean(dialogConfig.sla_critical_auto_assign_require_categories);
      }
      if (inputs.autoAssignIncludeAssigned instanceof HTMLInputElement) {
        inputs.autoAssignIncludeAssigned.checked = Boolean(dialogConfig.sla_critical_auto_assign_include_assigned);
      }
      if (inputs.autoAssignRules) {
        const rawRules = Array.isArray(dialogConfig.sla_critical_auto_assign_rules)
          ? dialogConfig.sla_critical_auto_assign_rules
          : defaults.auto_assign_rules;
        inputs.autoAssignRules.value = JSON.stringify(rawRules, null, 2);
      }
      if (inputs.autoAssignOperatorSkills) {
        const rawOperatorSkills = dialogConfig.sla_critical_operator_skills && typeof dialogConfig.sla_critical_operator_skills === 'object'
          ? dialogConfig.sla_critical_operator_skills
          : {};
        inputs.autoAssignOperatorSkills.value = JSON.stringify(rawOperatorSkills, null, 2);
      }
      if (inputs.autoAssignOperatorQueues) {
        const rawOperatorQueues = dialogConfig.sla_critical_operator_queues && typeof dialogConfig.sla_critical_operator_queues === 'object'
          ? dialogConfig.sla_critical_operator_queues
          : {};
        inputs.autoAssignOperatorQueues.value = JSON.stringify(rawOperatorQueues, null, 2);
      }
      if (inputs.slaPolicyAuditRequireLayers instanceof HTMLInputElement) {
        inputs.slaPolicyAuditRequireLayers.checked = Boolean(dialogConfig.sla_critical_auto_assign_audit_require_layers);
      }
      if (inputs.slaPolicyAuditRequireOwner instanceof HTMLInputElement) {
        inputs.slaPolicyAuditRequireOwner.checked = Boolean(dialogConfig.sla_critical_auto_assign_audit_require_owner);
      }
      if (inputs.slaPolicyAuditRequireReview instanceof HTMLInputElement) {
        inputs.slaPolicyAuditRequireReview.checked = Boolean(dialogConfig.sla_critical_auto_assign_audit_require_review);
      }
      if (inputs.slaPolicyAuditReviewTtlHours) {
        const value = Number.parseInt(dialogConfig.sla_critical_auto_assign_audit_review_ttl_hours, 10);
        inputs.slaPolicyAuditReviewTtlHours.value = Number.isFinite(value) && value >= 1
          ? Math.min(2160, value)
          : defaults.sla_critical_auto_assign_audit_review_ttl_hours;
      }
      if (inputs.slaPolicyAuditBroadRuleCoveragePct) {
        const value = Number.parseInt(dialogConfig.sla_critical_auto_assign_audit_broad_rule_coverage_pct, 10);
        inputs.slaPolicyAuditBroadRuleCoveragePct.value = Number.isFinite(value) && value >= 1
          ? Math.min(100, value)
          : defaults.sla_critical_auto_assign_audit_broad_rule_coverage_pct;
      }
      if (inputs.slaPolicyAuditBlockOnConflicts instanceof HTMLInputElement) {
        inputs.slaPolicyAuditBlockOnConflicts.checked = Boolean(dialogConfig.sla_critical_auto_assign_audit_block_on_conflicts);
      }
      if (inputs.slaPolicyGovernanceReviewRequired instanceof HTMLInputElement) {
        inputs.slaPolicyGovernanceReviewRequired.checked = Boolean(dialogConfig.sla_critical_auto_assign_governance_review_required);
      }
      if (inputs.slaPolicyGovernanceReviewPath) {
        const reviewPath = String(dialogConfig.sla_critical_auto_assign_governance_review_path || '').trim().toLowerCase();
        inputs.slaPolicyGovernanceReviewPath.value = ['custom', 'light', 'standard', 'strict'].includes(reviewPath)
          ? reviewPath
          : defaults.sla_critical_auto_assign_governance_review_path;
      }
      if (inputs.slaPolicyGovernanceReviewTtlHours) {
        const value = Number.parseInt(dialogConfig.sla_critical_auto_assign_governance_review_ttl_hours, 10);
        inputs.slaPolicyGovernanceReviewTtlHours.value = Number.isFinite(value) && value >= 1
          ? Math.min(2160, value)
          : defaults.sla_critical_auto_assign_governance_review_ttl_hours;
      }
      if (inputs.slaPolicyGovernanceDryRunTicketRequired instanceof HTMLInputElement) {
        inputs.slaPolicyGovernanceDryRunTicketRequired.checked = Boolean(dialogConfig.sla_critical_auto_assign_governance_dry_run_ticket_required);
      }
      if (inputs.slaPolicyGovernanceDecisionRequired instanceof HTMLInputElement) {
        inputs.slaPolicyGovernanceDecisionRequired.checked = Boolean(dialogConfig.sla_critical_auto_assign_governance_decision_required);
      }
      if (inputs.escalationWebhookEnabled instanceof HTMLInputElement) {
        inputs.escalationWebhookEnabled.checked = Boolean(dialogConfig.sla_critical_escalation_webhook_enabled);
      }
      if (inputs.escalationIncludeAssigned instanceof HTMLInputElement) {
        inputs.escalationIncludeAssigned.checked = dialogConfig.sla_critical_escalation_include_assigned === true;
      }
      if (inputs.escalationWebhookUrls) {
        const webhookUrls = Array.isArray(dialogConfig.sla_critical_escalation_webhook_urls)
          ? dialogConfig.sla_critical_escalation_webhook_urls
          : [];
        inputs.escalationWebhookUrls.value = webhookUrls.map((value) => String(value || '').trim()).filter(Boolean).join('\n');
      }
      if (inputs.escalationWebhookCooldownMinutes) {
        const cooldown = Number.parseInt(dialogConfig.sla_critical_escalation_webhook_cooldown_minutes, 10);
        inputs.escalationWebhookCooldownMinutes.value = Number.isFinite(cooldown) && cooldown >= 1
          ? cooldown
          : defaults.escalation_webhook_cooldown_minutes;
      }
      if (inputs.escalationWebhookTimeoutMs) {
        const timeoutMs = Number.parseInt(dialogConfig.sla_critical_escalation_webhook_timeout_ms, 10);
        inputs.escalationWebhookTimeoutMs.value = Number.isFinite(timeoutMs) && timeoutMs >= 500
          ? timeoutMs
          : defaults.escalation_webhook_timeout_ms;
      }
      if (inputs.escalationWebhookRetryAttempts) {
        const retryAttempts = Number.parseInt(dialogConfig.sla_critical_escalation_webhook_retry_attempts, 10);
        inputs.escalationWebhookRetryAttempts.value = Number.isFinite(retryAttempts) && retryAttempts >= 1
          ? retryAttempts
          : defaults.escalation_webhook_retry_attempts;
      }
      if (inputs.escalationWebhookRetryBackoffMs) {
        const retryBackoffMs = Number.parseInt(dialogConfig.sla_critical_escalation_webhook_retry_backoff_ms, 10);
        inputs.escalationWebhookRetryBackoffMs.value = Number.isFinite(retryBackoffMs) && retryBackoffMs >= 50
          ? retryBackoffMs
          : defaults.escalation_webhook_retry_backoff_ms;
      }
      if (inputs.escalationWebhookEventName) {
        const eventName = String(dialogConfig.sla_critical_escalation_webhook_event_name || '').trim();
        inputs.escalationWebhookEventName.value = eventName || defaults.escalation_webhook_event_name;
      }
      if (inputs.escalationWebhookSeverity) {
        const severity = String(dialogConfig.sla_critical_escalation_webhook_severity || '').trim().toLowerCase();
        const allowedSeverities = new Set(['critical', 'high', 'medium', 'low']);
        inputs.escalationWebhookSeverity.value = allowedSeverities.has(severity)
          ? severity
          : defaults.escalation_webhook_event_severity;
      }
      if (inputs.escalationWebhookMaxTicketsPerRun) {
        const maxTicketsPerRun = Number.parseInt(dialogConfig.sla_critical_escalation_webhook_max_tickets_per_run, 10);
        inputs.escalationWebhookMaxTicketsPerRun.value = Number.isFinite(maxTicketsPerRun) && maxTicketsPerRun >= 1
          ? Math.min(500, maxTicketsPerRun)
          : defaults.escalation_webhook_max_tickets_per_run;
      }
      if (inputs.slaWindowPresets) {
        const rawPresets = Array.isArray(dialogConfig.sla_window_presets_minutes)
          ? dialogConfig.sla_window_presets_minutes
          : [15, 30, 60, 120];
        const normalizedPresets = rawPresets
          .map((value) => Number.parseInt(value, 10))
          .filter((value) => Number.isFinite(value) && value >= 5 && value <= 1440);
        inputs.slaWindowPresets.value = Array.from(new Set(normalizedPresets)).join(',');
      }
      if (inputs.slaWindowDefault) {
        const defaultMinutes = Number.parseInt(dialogConfig.sla_window_default_minutes, 10);
        inputs.slaWindowDefault.value = Number.isFinite(defaultMinutes) && defaultMinutes > 0 ? defaultMinutes : '';
      }
      if (inputs.defaultView) {
        const normalizedView = String(dialogConfig.default_view || 'all').trim().toLowerCase();
        const allowedViews = new Set(['all', 'active', 'new', 'unassigned', 'overdue', 'sla_critical', 'escalation_required']);
        inputs.defaultView.value = allowedViews.has(normalizedView) ? normalizedView : 'all';
      }
      if (inputs.quickSnoozeMinutes) {
        const quickSnooze = Number.parseInt(dialogConfig.quick_snooze_minutes, 10);
        inputs.quickSnoozeMinutes.value = Number.isFinite(quickSnooze) && quickSnooze >= 5
          ? Math.min(1440, quickSnooze)
          : defaults.quick_snooze_minutes;
      }
      if (inputs.overdueThresholdHours) {
        const overdueThreshold = Number.parseInt(dialogConfig.overdue_threshold_hours, 10);
        inputs.overdueThresholdHours.value = Number.isFinite(overdueThreshold) && overdueThreshold >= 1
          ? Math.min(168, overdueThreshold)
          : defaults.overdue_threshold_hours;
      }
      if (inputs.workspaceEnabled instanceof HTMLInputElement) {
        inputs.workspaceEnabled.checked = dialogConfig.workspace_v1 !== false;
      }
      if (inputs.workspaceSingleMode instanceof HTMLInputElement) {
        inputs.workspaceSingleMode.checked = dialogConfig.workspace_single_mode === true;
      }
      if (inputs.slaCriticalAutoSort instanceof HTMLInputElement) {
        inputs.slaCriticalAutoSort.checked = dialogConfig.sla_critical_auto_sort !== false;
      }
      if (inputs.slaCriticalPinUnassignedOnly instanceof HTMLInputElement) {
        inputs.slaCriticalPinUnassignedOnly.checked = dialogConfig.sla_critical_pin_unassigned_only === true;
      }
      if (inputs.slaCriticalViewUnassignedOnly instanceof HTMLInputElement) {
        inputs.slaCriticalViewUnassignedOnly.checked = dialogConfig.sla_critical_view_unassigned_only === true;
      }
      if (inputs.workspaceForceWorkspace instanceof HTMLInputElement) {
        inputs.workspaceForceWorkspace.checked = dialogConfig.workspace_force_workspace === true;
      }
      if (inputs.workspaceDecommissionLegacyModal instanceof HTMLInputElement) {
        inputs.workspaceDecommissionLegacyModal.checked = dialogConfig.workspace_decommission_legacy_modal === true;
      }
      if (inputs.workspaceAbEnabled instanceof HTMLInputElement) {
        inputs.workspaceAbEnabled.checked = Boolean(dialogConfig.workspace_ab_enabled);
      }
      if (inputs.workspaceInlineNavigation instanceof HTMLInputElement) {
        inputs.workspaceInlineNavigation.checked = dialogConfig.workspace_inline_navigation !== false;
      }
      if (inputs.workspaceDisableLegacyFallback instanceof HTMLInputElement) {
        inputs.workspaceDisableLegacyFallback.checked = dialogConfig.workspace_disable_legacy_fallback === true;
      }
      if (inputs.workspaceContractTimeout) {
        const timeout = Number.parseInt(dialogConfig.workspace_contract_timeout_ms, 10);
        inputs.workspaceContractTimeout.value = Number.isFinite(timeout) && timeout >= 1000
          ? timeout
          : defaults.workspace_contract_timeout_ms;
      }
      if (inputs.workspaceRetryAttempts) {
        const retries = Number.parseInt(dialogConfig.workspace_contract_retry_attempts, 10);
        inputs.workspaceRetryAttempts.value = Number.isFinite(retries) && retries >= 0
          ? retries
          : defaults.workspace_contract_retry_attempts;
      }
      if (inputs.workspaceContractInclude) {
        const includeRaw = String(dialogConfig.workspace_contract_include || '').trim();
        inputs.workspaceContractInclude.value = includeRaw || defaults.workspace_contract_include;
      }
      if (inputs.workspaceMessagesPageLimit) {
        const pageLimit = Number.parseInt(dialogConfig.workspace_messages_page_limit, 10);
        inputs.workspaceMessagesPageLimit.value = Number.isFinite(pageLimit) && pageLimit >= 10
          ? pageLimit
          : defaults.workspace_messages_page_limit;
      }
      if (inputs.workspaceFailureStreakThreshold) {
        const streak = Number.parseInt(dialogConfig.workspace_failure_streak_threshold, 10);
        inputs.workspaceFailureStreakThreshold.value = Number.isFinite(streak) && streak >= 1
          ? streak
          : defaults.workspace_failure_streak_threshold;
      }
      if (inputs.workspaceFailureCooldownMs) {
        const cooldown = Number.parseInt(dialogConfig.workspace_failure_cooldown_ms, 10);
        inputs.workspaceFailureCooldownMs.value = Number.isFinite(cooldown) && cooldown >= 1000
          ? cooldown
          : defaults.workspace_failure_cooldown_ms;
      }
      if (inputs.workspaceDraftAutosaveDelay) {
        const autosave = Number.parseInt(dialogConfig.workspace_draft_autosave_delay_ms, 10);
        inputs.workspaceDraftAutosaveDelay.value = Number.isFinite(autosave) && autosave >= 300
          ? autosave
          : defaults.workspace_draft_autosave_delay_ms;
      }
      if (inputs.workspaceDraftTelemetryInterval) {
        const telemetryInterval = Number.parseInt(dialogConfig.workspace_draft_telemetry_interval_ms, 10);
        inputs.workspaceDraftTelemetryInterval.value = Number.isFinite(telemetryInterval) && telemetryInterval >= 5000
          ? telemetryInterval
          : defaults.workspace_draft_telemetry_interval_ms;
      }
      if (inputs.workspaceOpenSloMs) {
        const openSlo = Number.parseInt(dialogConfig.workspace_open_slo_ms, 10);
        inputs.workspaceOpenSloMs.value = Number.isFinite(openSlo) && openSlo >= 500
          ? Math.min(10000, openSlo)
          : defaults.workspace_open_slo_ms;
      }
      if (inputs.listPollIntervalMs) {
        const listPoll = Number.parseInt(dialogConfig.list_poll_interval_ms, 10);
        inputs.listPollIntervalMs.value = Number.isFinite(listPoll) && listPoll >= 3000
          ? Math.min(60000, listPoll)
          : defaults.list_poll_interval_ms;
      }
      if (inputs.historyPollIntervalMs) {
        const historyPoll = Number.parseInt(dialogConfig.history_poll_interval_ms, 10);
        inputs.historyPollIntervalMs.value = Number.isFinite(historyPoll) && historyPoll >= 3000
          ? Math.min(60000, historyPoll)
          : defaults.history_poll_interval_ms;
      }
      if (inputs.workspaceAbRolloutPercent) {
        const rolloutPercent = Number.parseInt(dialogConfig.workspace_ab_rollout_percent, 10);
        inputs.workspaceAbRolloutPercent.value = Number.isFinite(rolloutPercent)
          ? Math.min(100, Math.max(0, rolloutPercent))
          : defaults.workspace_ab_rollout_percent;
      }
      if (inputs.workspaceAbExperimentName) {
        const experimentName = String(dialogConfig.workspace_ab_experiment_name || '').trim();
        inputs.workspaceAbExperimentName.value = experimentName || defaults.workspace_ab_experiment_name;
      }
      if (inputs.workspaceAbOperatorSegment) {
        const operatorSegment = String(dialogConfig.workspace_ab_operator_segment || '').trim();
        inputs.workspaceAbOperatorSegment.value = operatorSegment || defaults.workspace_ab_operator_segment;
      }
      if (inputs.workspaceAbPrimaryKpis) {
        const primaryKpis = Array.isArray(dialogConfig.workspace_ab_primary_kpis)
          ? dialogConfig.workspace_ab_primary_kpis
          : defaults.workspace_ab_primary_kpis;
        inputs.workspaceAbPrimaryKpis.value = primaryKpis
          .map((value) => String(value || '').trim())
          .filter(Boolean)
          .join(',');
      }
      if (inputs.workspaceAbSecondaryKpis) {
        const secondaryKpis = Array.isArray(dialogConfig.workspace_ab_secondary_kpis)
          ? dialogConfig.workspace_ab_secondary_kpis
          : defaults.workspace_ab_secondary_kpis;
        inputs.workspaceAbSecondaryKpis.value = secondaryKpis
          .map((value) => String(value || '').trim())
          .filter(Boolean)
          .join(',');
      }
      if (inputs.workspaceAbOperatorOverrides) {
        const rawOverrides = dialogConfig.workspace_ab_operator_overrides;
        const safeOverrides = rawOverrides && typeof rawOverrides === 'object' && !Array.isArray(rawOverrides)
          ? rawOverrides
          : defaults.workspace_ab_operator_overrides;
        inputs.workspaceAbOperatorOverrides.value = JSON.stringify(safeOverrides, null, 2);
      }
      const applyPercent = (input, rawValue, fallback) => {
        if (!input) {
          return;
        }
        const value = Number(rawValue);
        input.value = Number.isFinite(value) && value > 0 && value <= 1
          ? (value * 100).toFixed(2)
          : (fallback * 100).toFixed(2);
      };
      const applyRelativePercent = (input, rawValue, fallback) => {
        if (!input) {
          return;
        }
        const value = Number(rawValue);
        input.value = Number.isFinite(value) && value > 0 && value <= 5
          ? (value * 100).toFixed(2)
          : (fallback * 100).toFixed(2);
      };
      applyPercent(inputs.guardrailRenderErrorRate, dialogConfig.guardrail_render_error_rate, defaults.workspace_guardrail_render_error_rate);
      applyPercent(inputs.guardrailFallbackRate, dialogConfig.guardrail_fallback_rate, defaults.workspace_guardrail_fallback_rate);
      applyPercent(inputs.guardrailAbandonRate, dialogConfig.guardrail_abandon_rate, defaults.workspace_guardrail_abandon_rate);
      applyPercent(inputs.guardrailSlowOpenRate, dialogConfig.guardrail_slow_open_rate, defaults.workspace_guardrail_slow_open_rate);
      if (inputs.dimensionMinEvents) {
        const dimensionMinEvents = Number.parseInt(dialogConfig.dimension_min_events, 10);
        inputs.dimensionMinEvents.value = Number.isFinite(dimensionMinEvents) && dimensionMinEvents >= 5
          ? dimensionMinEvents
          : defaults.workspace_dimension_min_events;
      }
      if (inputs.cohortMinEvents) {
        const cohortMinEvents = Number.parseInt(dialogConfig.cohort_min_events, 10);
        inputs.cohortMinEvents.value = Number.isFinite(cohortMinEvents) && cohortMinEvents >= 5
          ? cohortMinEvents
          : defaults.workspace_cohort_min_events;
      }
      if (inputs.kpiOutcomeMinSamples) {
        const minSamples = Number.parseInt(dialogConfig.workspace_rollout_kpi_outcome_min_samples_per_cohort, 10);
        inputs.kpiOutcomeMinSamples.value = Number.isFinite(minSamples) && minSamples > 0
          ? minSamples
          : defaults.workspace_rollout_kpi_outcome_min_samples_per_cohort;
      }
      applyRelativePercent(
        inputs.kpiOutcomeFrtRegressionPercent,
        dialogConfig.workspace_rollout_kpi_outcome_frt_max_relative_regression,
        defaults.workspace_rollout_kpi_outcome_frt_max_relative_regression
      );
      applyRelativePercent(
        inputs.kpiOutcomeTtrRegressionPercent,
        dialogConfig.workspace_rollout_kpi_outcome_ttr_max_relative_regression,
        defaults.workspace_rollout_kpi_outcome_ttr_max_relative_regression
      );
      applyPercent(
        inputs.kpiOutcomeSlaAbsDeltaPercent,
        dialogConfig.workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta,
        defaults.workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta
      );
      if (inputs.kpiOutcomeSlaRelativeMultiplier) {
        const multiplier = Number(dialogConfig.workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier);
        inputs.kpiOutcomeSlaRelativeMultiplier.value = Number.isFinite(multiplier) && multiplier >= 1 && multiplier <= 10
          ? multiplier.toFixed(2)
          : defaults.workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier.toFixed(2);
      }
      if (inputs.rolloutRequiredOutcomeKpis) {
        const requiredOutcomeKpis = Array.isArray(dialogConfig.workspace_rollout_required_outcome_kpis)
          ? dialogConfig.workspace_rollout_required_outcome_kpis
          : defaults.workspace_rollout_required_outcome_kpis;
        inputs.rolloutRequiredOutcomeKpis.value = requiredOutcomeKpis.join(',');
      }
      if (inputs.winnerMinOpenImprovementPercent) {
        const winnerMinImprovement = Number(dialogConfig.workspace_rollout_winner_min_open_improvement);
        inputs.winnerMinOpenImprovementPercent.value = Number.isFinite(winnerMinImprovement) && winnerMinImprovement >= 0 && winnerMinImprovement <= 0.5
          ? (winnerMinImprovement * 100).toFixed(2)
          : (defaults.workspace_rollout_winner_min_open_improvement * 100).toFixed(2);
      }
      if (inputs.macroVariableDefaults) {
        const macroDefaults = dialogConfig.macro_variable_defaults;
        if (macroDefaults && typeof macroDefaults === 'object' && !Array.isArray(macroDefaults)) {
          inputs.macroVariableDefaults.value = JSON.stringify(macroDefaults, null, 2);
        } else {
          inputs.macroVariableDefaults.value = '{}';
        }
      }
      if (inputs.macroPublishAllowedRoles) {
        const allowedRoles = Array.isArray(dialogConfig.macro_publish_allowed_roles)
          ? dialogConfig.macro_publish_allowed_roles
          : [];
        inputs.macroPublishAllowedRoles.value = allowedRoles
          .map((value) => String(value || '').trim())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.macroRequireIndependentReview) {
        if (Object.prototype.hasOwnProperty.call(dialogConfig, 'macro_require_independent_review')) {
          inputs.macroRequireIndependentReview.checked = Boolean(dialogConfig.macro_require_independent_review);
        } else {
          inputs.macroRequireIndependentReview.checked = true;
        }
      }
      if (inputs.macroVariableCatalog) {
        const catalog = Array.isArray(dialogConfig.macro_variable_catalog)
          ? dialogConfig.macro_variable_catalog
          : [];
        inputs.macroVariableCatalog.value = JSON.stringify(catalog, null, 2);
      }
      if (inputs.macroVariableCatalogExternalUrl) {
        inputs.macroVariableCatalogExternalUrl.value = String(dialogConfig.macro_variable_catalog_external_url || '').trim();
      }
      if (inputs.macroVariableCatalogExternalTimeoutMs) {
        const timeoutMs = Number.parseInt(dialogConfig.macro_variable_catalog_external_timeout_ms, 10);
        inputs.macroVariableCatalogExternalTimeoutMs.value = Number.isFinite(timeoutMs) && timeoutMs >= 200
          ? Math.min(10000, timeoutMs)
          : 2000;
      }
      if (inputs.macroVariableCatalogExternalCacheTtlSeconds) {
        const cacheTtlSeconds = Number.parseInt(dialogConfig.macro_variable_catalog_external_cache_ttl_seconds, 10);
        inputs.macroVariableCatalogExternalCacheTtlSeconds.value = Number.isFinite(cacheTtlSeconds) && cacheTtlSeconds >= 0
          ? Math.min(3600, cacheTtlSeconds)
          : 120;
      }
      if (inputs.macroVariableCatalogExternalAuthHeader) {
        const headerName = String(dialogConfig.macro_variable_catalog_external_auth_header || '').trim();
        inputs.macroVariableCatalogExternalAuthHeader.value = headerName || defaults.macro_variable_catalog_external_auth_header;
      }
      if (inputs.macroVariableCatalogExternalAuthToken) {
        inputs.macroVariableCatalogExternalAuthToken.value = String(dialogConfig.macro_variable_catalog_external_auth_token || '').trim();
      }
      if (inputs.workspaceClientCrmProfileUrlTemplate) {
        inputs.workspaceClientCrmProfileUrlTemplate.value = String(dialogConfig.workspace_client_crm_profile_url_template || '').trim();
      }
      if (inputs.workspaceClientCrmProfileLabel) {
        inputs.workspaceClientCrmProfileLabel.value = String(dialogConfig.workspace_client_crm_profile_label || '').trim();
      }
      if (inputs.workspaceClientContractProfileUrlTemplate) {
        inputs.workspaceClientContractProfileUrlTemplate.value = String(dialogConfig.workspace_client_contract_profile_url_template || '').trim();
      }
      if (inputs.workspaceClientContractProfileLabel) {
        inputs.workspaceClientContractProfileLabel.value = String(dialogConfig.workspace_client_contract_profile_label || '').trim();
      }
      if (inputs.workspaceClientExternalLinks) {
        const links = Array.isArray(dialogConfig.workspace_client_external_links)
          ? dialogConfig.workspace_client_external_links
          : [];
        inputs.workspaceClientExternalLinks.value = JSON.stringify(links, null, 2);
      }
      if (inputs.workspaceClientExternalProfileUrl) {
        inputs.workspaceClientExternalProfileUrl.value = String(dialogConfig.workspace_client_external_profile_url || '').trim();
      }
      if (inputs.workspaceClientExternalProfileTimeoutMs) {
        const value = Number.parseInt(dialogConfig.workspace_client_external_profile_timeout_ms, 10);
        inputs.workspaceClientExternalProfileTimeoutMs.value = Number.isFinite(value)
          ? String(Math.min(10000, Math.max(300, value)))
          : String(defaults.workspace_client_external_profile_timeout_ms);
      }
      if (inputs.workspaceClientExternalProfileCacheTtlSeconds) {
        const value = Number.parseInt(dialogConfig.workspace_client_external_profile_cache_ttl_seconds, 10);
        inputs.workspaceClientExternalProfileCacheTtlSeconds.value = Number.isFinite(value)
          ? String(Math.min(3600, Math.max(0, value)))
          : String(defaults.workspace_client_external_profile_cache_ttl_seconds);
      }
      if (inputs.workspaceClientExternalProfileAuthHeader) {
        const headerName = String(dialogConfig.workspace_client_external_profile_auth_header || '').trim();
        inputs.workspaceClientExternalProfileAuthHeader.value = headerName || defaults.workspace_client_external_profile_auth_header;
      }
      if (inputs.workspaceClientExternalProfileAuthToken) {
        inputs.workspaceClientExternalProfileAuthToken.value = String(dialogConfig.workspace_client_external_profile_auth_token || '').trim();
      }
      if (inputs.workspaceClientContextRequiredSources) {
        const requiredSources = Array.isArray(dialogConfig.workspace_client_context_required_sources)
          ? dialogConfig.workspace_client_context_required_sources
          : defaults.workspace_client_context_required_sources;
        inputs.workspaceClientContextRequiredSources.value = requiredSources
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.workspaceClientContextSourcePriority) {
        const priority = Array.isArray(dialogConfig.workspace_client_context_source_priority)
          ? dialogConfig.workspace_client_context_source_priority
          : defaults.workspace_client_context_source_priority;
        inputs.workspaceClientContextSourcePriority.value = priority
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.workspaceClientContextSourceStaleAfterHours) {
        const value = Number.parseInt(dialogConfig.workspace_client_context_source_stale_after_hours, 10);
        inputs.workspaceClientContextSourceStaleAfterHours.value = Number.isFinite(value) && value >= 0 && value <= 8760
          ? value
          : defaults.workspace_client_context_source_stale_after_hours;
      }
      if (inputs.workspaceClientContextSourceLabels) {
        const labels = dialogConfig.workspace_client_context_source_labels;
        if (labels && typeof labels === 'object' && !Array.isArray(labels)) {
          inputs.workspaceClientContextSourceLabels.value = JSON.stringify(labels, null, 2);
        } else {
          inputs.workspaceClientContextSourceLabels.value = '{}';
        }
      }
      if (inputs.workspaceClientContextSourceUpdatedAtAttributes) {
        const attributes = dialogConfig.workspace_client_context_source_updated_at_attributes;
        if (attributes && typeof attributes === 'object' && !Array.isArray(attributes)) {
          inputs.workspaceClientContextSourceUpdatedAtAttributes.value = JSON.stringify(attributes, null, 2);
        } else {
          inputs.workspaceClientContextSourceUpdatedAtAttributes.value = '{}';
        }
      }
      if (inputs.workspaceClientContextSourceStaleAfterHoursBySource) {
        const values = dialogConfig.workspace_client_context_source_stale_after_hours_by_source;
        if (values && typeof values === 'object' && !Array.isArray(values)) {
          inputs.workspaceClientContextSourceStaleAfterHoursBySource.value = JSON.stringify(values, null, 2);
        } else {
          inputs.workspaceClientContextSourceStaleAfterHoursBySource.value = '{}';
        }
      }
      if (inputs.workspaceRequiredClientAttributes) {
        const requiredAttributes = Array.isArray(dialogConfig.workspace_required_client_attributes)
          ? dialogConfig.workspace_required_client_attributes
          : defaults.workspace_required_client_attributes;
        inputs.workspaceRequiredClientAttributes.value = requiredAttributes
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.workspaceRequiredClientAttributesBySegment) {
        const values = dialogConfig.workspace_required_client_attributes_by_segment;
        if (values && typeof values === 'object' && !Array.isArray(values)) {
          inputs.workspaceRequiredClientAttributesBySegment.value = JSON.stringify(values, null, 2);
        } else {
          inputs.workspaceRequiredClientAttributesBySegment.value = '{}';
        }
      }
      if (inputs.macroGovernanceRequireOwner) {
        inputs.macroGovernanceRequireOwner.checked = Boolean(dialogConfig.macro_governance_require_owner);
      }
      if (inputs.macroGovernanceRequireNamespace) {
        inputs.macroGovernanceRequireNamespace.checked = Boolean(dialogConfig.macro_governance_require_namespace);
      }
      if (inputs.macroGovernanceRequireReview) {
        inputs.macroGovernanceRequireReview.checked = Boolean(dialogConfig.macro_governance_require_review);
      }
      if (inputs.macroGovernanceReviewTtlHours) {
        const value = Number.parseInt(dialogConfig.macro_governance_review_ttl_hours, 10);
        inputs.macroGovernanceReviewTtlHours.value = Number.isFinite(value) && value >= 1 && value <= 2160
          ? value
          : defaults.macro_governance_review_ttl_hours;
      }
      if (inputs.macroGovernanceDeprecationRequiresReason) {
        inputs.macroGovernanceDeprecationRequiresReason.checked = Boolean(dialogConfig.macro_governance_deprecation_requires_reason);
      }
      if (inputs.macroGovernanceUnusedDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_unused_days, 10);
        inputs.macroGovernanceUnusedDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_unused_days;
      }
      if (inputs.macroGovernanceRedListEnabled) {
        inputs.macroGovernanceRedListEnabled.checked = Boolean(dialogConfig.macro_governance_red_list_enabled);
      }
      if (inputs.macroGovernanceRedListUsageMax) {
        const value = Number.parseInt(dialogConfig.macro_governance_red_list_usage_max, 10);
        inputs.macroGovernanceRedListUsageMax.value = Number.isFinite(value) && value >= 0 && value <= 10000
          ? value
          : defaults.macro_governance_red_list_usage_max;
      }
      if (inputs.macroGovernanceOwnerActionRequired) {
        inputs.macroGovernanceOwnerActionRequired.checked = Boolean(dialogConfig.macro_governance_owner_action_required);
      }
      if (inputs.macroGovernanceCleanupCadenceDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_cleanup_cadence_days, 10);
        inputs.macroGovernanceCleanupCadenceDays.value = Number.isFinite(value) && value >= 0 && value <= 365
          ? value
          : defaults.macro_governance_cleanup_cadence_days;
      }
      if (inputs.macroGovernanceAliasCleanupRequired) {
        inputs.macroGovernanceAliasCleanupRequired.checked = Boolean(dialogConfig.macro_governance_alias_cleanup_required);
      }
      if (inputs.macroGovernanceVariableCleanupRequired) {
        inputs.macroGovernanceVariableCleanupRequired.checked = Boolean(dialogConfig.macro_governance_variable_cleanup_required);
      }
      if (inputs.macroGovernanceUsageTierSlaRequired) {
        inputs.macroGovernanceUsageTierSlaRequired.checked = Boolean(dialogConfig.macro_governance_usage_tier_sla_required);
      }
      if (inputs.macroGovernanceUsageTierLowMax) {
        const value = Number.parseInt(dialogConfig.macro_governance_usage_tier_low_max, 10);
        inputs.macroGovernanceUsageTierLowMax.value = Number.isFinite(value) && value >= 0 && value <= 10000
          ? value
          : defaults.macro_governance_usage_tier_low_max;
      }
      if (inputs.macroGovernanceUsageTierMediumMax) {
        const value = Number.parseInt(dialogConfig.macro_governance_usage_tier_medium_max, 10);
        inputs.macroGovernanceUsageTierMediumMax.value = Number.isFinite(value) && value >= 0 && value <= 10000
          ? value
          : defaults.macro_governance_usage_tier_medium_max;
      }
      if (inputs.macroGovernanceCleanupSlaLowDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_cleanup_sla_low_days, 10);
        inputs.macroGovernanceCleanupSlaLowDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_cleanup_sla_low_days;
      }
      if (inputs.macroGovernanceCleanupSlaMediumDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_cleanup_sla_medium_days, 10);
        inputs.macroGovernanceCleanupSlaMediumDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_cleanup_sla_medium_days;
      }
      if (inputs.macroGovernanceCleanupSlaHighDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_cleanup_sla_high_days, 10);
        inputs.macroGovernanceCleanupSlaHighDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_cleanup_sla_high_days;
      }
      if (inputs.macroGovernanceDeprecationSlaLowDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_deprecation_sla_low_days, 10);
        inputs.macroGovernanceDeprecationSlaLowDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_deprecation_sla_low_days;
      }
      if (inputs.macroGovernanceDeprecationSlaMediumDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_deprecation_sla_medium_days, 10);
        inputs.macroGovernanceDeprecationSlaMediumDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_deprecation_sla_medium_days;
      }
      if (inputs.macroGovernanceDeprecationSlaHighDays) {
        const value = Number.parseInt(dialogConfig.macro_governance_deprecation_sla_high_days, 10);
        inputs.macroGovernanceDeprecationSlaHighDays.value = Number.isFinite(value) && value >= 1 && value <= 365
          ? value
          : defaults.macro_governance_deprecation_sla_high_days;
      }
      if (inputs.workspaceClientHiddenAttributes) {
        const hiddenAttributes = Array.isArray(dialogConfig.workspace_client_hidden_attributes)
          ? dialogConfig.workspace_client_hidden_attributes
          : [];
        inputs.workspaceClientHiddenAttributes.value = JSON.stringify(hiddenAttributes, null, 2);
      }
      if (inputs.workspaceClientAttributeLabels) {
        const attributeLabels = dialogConfig.workspace_client_attribute_labels;
        if (attributeLabels && typeof attributeLabels === 'object' && !Array.isArray(attributeLabels)) {
          inputs.workspaceClientAttributeLabels.value = JSON.stringify(attributeLabels, null, 2);
        } else {
          inputs.workspaceClientAttributeLabels.value = '{}';
        }
      }
      if (inputs.workspaceClientAttributeOrder) {
        const attributeOrder = Array.isArray(dialogConfig.workspace_client_attribute_order)
          ? dialogConfig.workspace_client_attribute_order
          : [];
        inputs.workspaceClientAttributeOrder.value = JSON.stringify(attributeOrder, null, 2);
      }
      if (inputs.workspaceClientExtraAttributesMax) {
        const value = Number.parseInt(dialogConfig.workspace_client_extra_attributes_max, 10);
        inputs.workspaceClientExtraAttributesMax.value = Number.isFinite(value) && value >= 1 && value <= 100
          ? value
          : defaults.workspace_client_extra_attributes_max;
      }
      if (inputs.workspaceClientExtraAttributesCollapseAfter) {
        const value = Number.parseInt(dialogConfig.workspace_client_extra_attributes_collapse_after, 10);
        inputs.workspaceClientExtraAttributesCollapseAfter.value = Number.isFinite(value) && value >= 1 && value <= 50
          ? value
          : defaults.workspace_client_extra_attributes_collapse_after;
      }
      if (inputs.workspaceClientExtraAttributesHideTechnical) {
        inputs.workspaceClientExtraAttributesHideTechnical.checked = dialogConfig.workspace_client_extra_attributes_hide_technical !== false;
      }
      if (inputs.workspaceClientExtraAttributesTechnicalPrefixes) {
        const prefixes = Array.isArray(dialogConfig.workspace_client_extra_attributes_technical_prefixes)
          ? dialogConfig.workspace_client_extra_attributes_technical_prefixes
          : defaults.workspace_client_extra_attributes_technical_prefixes;
        inputs.workspaceClientExtraAttributesTechnicalPrefixes.value = prefixes
          .map((value) => String(value || '').trim())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.workspaceContextHistoryLimit) {
        const value = Number.parseInt(dialogConfig.workspace_context_history_limit, 10);
        inputs.workspaceContextHistoryLimit.value = Number.isFinite(value) && value >= 1 && value <= 20
          ? value
          : defaults.workspace_context_history_limit;
      }
      if (inputs.workspaceContextRelatedEventsLimit) {
        const value = Number.parseInt(dialogConfig.workspace_context_related_events_limit, 10);
        inputs.workspaceContextRelatedEventsLimit.value = Number.isFinite(value) && value >= 1 && value <= 20
          ? value
          : defaults.workspace_context_related_events_limit;
      }
      if (inputs.workspaceSegmentHighLifetimeMinDialogs) {
        const value = Number.parseInt(dialogConfig.workspace_segment_high_lifetime_volume_min_dialogs, 10);
        inputs.workspaceSegmentHighLifetimeMinDialogs.value = Number.isFinite(value) && value >= 1 && value <= 500
          ? value
          : defaults.workspace_segment_high_lifetime_volume_min_dialogs;
      }
      if (inputs.workspaceSegmentMultiOpenMinDialogs) {
        const value = Number.parseInt(dialogConfig.workspace_segment_multi_open_dialogs_min_open, 10);
        inputs.workspaceSegmentMultiOpenMinDialogs.value = Number.isFinite(value) && value >= 1 && value <= 50
          ? value
          : defaults.workspace_segment_multi_open_dialogs_min_open;
      }
      if (inputs.workspaceSegmentReactivationMinDialogs) {
        const value = Number.parseInt(dialogConfig.workspace_segment_reactivation_risk_min_dialogs, 10);
        inputs.workspaceSegmentReactivationMinDialogs.value = Number.isFinite(value) && value >= 1 && value <= 500
          ? value
          : defaults.workspace_segment_reactivation_risk_min_dialogs;
      }
      if (inputs.workspaceSegmentReactivationMaxResolved30d) {
        const value = Number.parseInt(dialogConfig.workspace_segment_reactivation_risk_max_resolved_30d, 10);
        inputs.workspaceSegmentReactivationMaxResolved30d.value = Number.isFinite(value) && value >= 0 && value <= 100
          ? value
          : defaults.workspace_segment_reactivation_risk_max_resolved_30d;
      }
      if (inputs.workspaceSegmentOpenBacklogMinOpen) {
        const value = Number.parseInt(dialogConfig.workspace_segment_open_backlog_min_open, 10);
        inputs.workspaceSegmentOpenBacklogMinOpen.value = Number.isFinite(value) && value >= 1 && value <= 100
          ? value
          : defaults.workspace_segment_open_backlog_min_open;
      }
      if (inputs.workspaceSegmentOpenBacklogMinSharePercent) {
        const value = Number.parseInt(dialogConfig.workspace_segment_open_backlog_min_share_percent, 10);
        inputs.workspaceSegmentOpenBacklogMinSharePercent.value = Number.isFinite(value) && value >= 1 && value <= 100
          ? value
          : defaults.workspace_segment_open_backlog_min_share_percent;
      }
      syncWorkspaceSingleModeControls(inputs);
    }

    function initialize() {
      initDialogSlaControls();
      initWorkspaceSingleModeControls();
      bindAutoAssignRulesHelpers();
    }

    return {
      bindAutoAssignRulesHelpers,
      getInputs,
      initialize,
      syncWorkspaceSingleModeControls,
      validateSlaAutoAssignRules,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogSlaCoreRuntime) {
        return window.__settingsDialogSlaCoreRuntime;
      }
      const runtime = createRuntime(options);
      runtime.initialize();
      window.__settingsDialogSlaCoreRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogSlaCoreRuntime = Object.freeze(api);
}());

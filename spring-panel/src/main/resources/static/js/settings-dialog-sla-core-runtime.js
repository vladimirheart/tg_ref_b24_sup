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

    function sanitizeIntValue(value, min, max, fallback) {
      const parsed = Number.parseInt(value, 10);
      if (!Number.isFinite(parsed)) {
        return fallback;
      }
      if (parsed < min) {
        return min;
      }
      if (parsed > max) {
        return max;
      }
      return parsed;
    }

    function collectSlaCoreConfig() {
      const inputs = getInputs();
      const defaults = getDefaultDialogSlaConfig();
      const errors = [];

      const target = Math.round(Number(inputs.target?.value));
      const warning = Math.round(Number(inputs.warning?.value));
      const critical = Math.round(Number(inputs.critical?.value));

      const safeTarget = Number.isFinite(target) && target > 0 ? target : defaults.target_minutes;
      const safeWarning = Number.isFinite(warning) && warning > 0 ? warning : defaults.warning_minutes;
      const safeCritical = Number.isFinite(critical) && critical > 0 ? critical : defaults.critical_minutes;
      const orchestrationModeRaw = String(inputs.orchestrationMode?.value || defaults.orchestration_mode).trim().toLowerCase();
      const allowedOrchestrationModes = new Set(['monitor', 'assist', 'autopilot']);
      const safeOrchestrationMode = allowedOrchestrationModes.has(orchestrationModeRaw)
        ? orchestrationModeRaw
        : defaults.orchestration_mode;

      const aiAgentModeRaw = String(inputs.aiAgentMode?.value || defaults.ai_agent_mode).trim().toLowerCase();
      const aiAgentAllowedModes = new Set(['auto_reply', 'assist_only', 'escalate_only']);
      const safeAiAgentMode = aiAgentAllowedModes.has(aiAgentModeRaw)
        ? aiAgentModeRaw
        : defaults.ai_agent_mode;

      const aiAgentAutoReplyThresholdRaw = Number.parseFloat(inputs.aiAgentAutoReplyThreshold?.value);
      const safeAiAgentAutoReplyThreshold = Number.isFinite(aiAgentAutoReplyThresholdRaw)
        ? Math.min(0.95, Math.max(0.2, aiAgentAutoReplyThresholdRaw))
        : defaults.ai_agent_auto_reply_threshold;
      if (!Number.isFinite(aiAgentAutoReplyThresholdRaw) || aiAgentAutoReplyThresholdRaw < 0.2 || aiAgentAutoReplyThresholdRaw > 0.95) {
        errors.push('Порог автоответа AI-агента должен быть в диапазоне 0.20..0.95.');
      }

      const aiAgentSuggestThresholdRaw = Number.parseFloat(inputs.aiAgentSuggestThreshold?.value);
      const safeAiAgentSuggestThreshold = Number.isFinite(aiAgentSuggestThresholdRaw)
        ? Math.min(0.95, Math.max(0.1, aiAgentSuggestThresholdRaw))
        : defaults.ai_agent_suggest_threshold;
      if (!Number.isFinite(aiAgentSuggestThresholdRaw) || aiAgentSuggestThresholdRaw < 0.1 || aiAgentSuggestThresholdRaw > 0.95) {
        errors.push('Порог подсказки AI-агента должен быть в диапазоне 0.10..0.95.');
      }
      if (safeAiAgentSuggestThreshold > safeAiAgentAutoReplyThreshold) {
        errors.push('Порог подсказки не может быть выше порога автоответа.');
      }

      const aiAgentMaxAutoRepliesRaw = Number.parseInt(inputs.aiAgentMaxAutoRepliesPerDialog?.value, 10);
      const safeAiAgentMaxAutoReplies = Number.isFinite(aiAgentMaxAutoRepliesRaw)
        ? Math.min(20, Math.max(1, aiAgentMaxAutoRepliesRaw))
        : defaults.ai_agent_max_auto_replies_per_dialog;
      if (!Number.isFinite(aiAgentMaxAutoRepliesRaw) || aiAgentMaxAutoRepliesRaw < 1 || aiAgentMaxAutoRepliesRaw > 20) {
        errors.push('Лимит автоответов AI-агента должен быть в диапазоне 1..20.');
      }

      const autoAssignMaxPerRunRaw = Math.round(Number(inputs.autoAssignMaxPerRun?.value));
      const safeAutoAssignMaxPerRun = Number.isFinite(autoAssignMaxPerRunRaw)
        ? Math.min(100, Math.max(1, autoAssignMaxPerRunRaw))
        : defaults.auto_assign_max_per_run;
      const safeAutoAssignActor = String(inputs.autoAssignActor?.value || '').trim() || defaults.auto_assign_actor;
      const autoAssignMaxOpenPerOperatorRaw = Math.round(Number(inputs.autoAssignMaxOpenPerOperator?.value));
      const safeAutoAssignMaxOpenPerOperator = Number.isFinite(autoAssignMaxOpenPerOperatorRaw)
        ? Math.min(5000, Math.max(1, autoAssignMaxOpenPerOperatorRaw))
        : null;
      if (!Number.isFinite(autoAssignMaxPerRunRaw) || autoAssignMaxPerRunRaw < 1 || autoAssignMaxPerRunRaw > 100) {
        errors.push('Лимит авто-назначений за проход должен быть в диапазоне 1..100.');
      }
      const autoAssignMaxOpenPerOperatorHasValue = String(inputs.autoAssignMaxOpenPerOperator?.value || '').trim() !== '';
      if (autoAssignMaxOpenPerOperatorHasValue
        && (!Number.isFinite(autoAssignMaxOpenPerOperatorRaw)
          || autoAssignMaxOpenPerOperatorRaw < 1
          || autoAssignMaxOpenPerOperatorRaw > 5000)) {
        errors.push('Лимит open-диалогов на оператора должен быть в диапазоне 1..5000 или пустым.');
      }

      let safeAutoAssignRules = [];
      let safeAutoAssignOperatorSkills = {};
      let safeAutoAssignOperatorQueues = {};
      const autoAssignRulesRaw = (inputs.autoAssignRules?.value || '').trim();
      if (autoAssignRulesRaw) {
        try {
          const parsedAutoAssignRules = JSON.parse(autoAssignRulesRaw);
          if (!Array.isArray(parsedAutoAssignRules)) {
            errors.push('Правила auto-assign должны быть JSON-массивом.');
          } else {
            const validation = validateSlaAutoAssignRules(parsedAutoAssignRules);
            safeAutoAssignRules = validation.normalizedRules;
            if (validation.errors.length > 0) {
              errors.push(...validation.errors);
            }
          }
        } catch (error) {
          errors.push('Правила auto-assign: некорректный JSON.');
        }
      }

      const autoAssignOperatorSkillsRaw = (inputs.autoAssignOperatorSkills?.value || '').trim();
      if (autoAssignOperatorSkillsRaw) {
        try {
          const parsedOperatorSkills = JSON.parse(autoAssignOperatorSkillsRaw);
          if (!parsedOperatorSkills || typeof parsedOperatorSkills !== 'object' || Array.isArray(parsedOperatorSkills)) {
            errors.push('Матрица навыков операторов должна быть JSON-объектом.');
          } else {
            const normalizedSkills = {};
            Object.entries(parsedOperatorSkills).forEach(([operator, skills]) => {
              const key = String(operator || '').trim();
              if (!key) {
                return;
              }
              const values = Array.isArray(skills) ? skills : [skills];
              const normalized = Array.from(new Set(values
                .map((value) => String(value || '').trim().toLowerCase())
                .filter(Boolean)));
              if (normalized.length) {
                normalizedSkills[key] = normalized;
              }
            });
            safeAutoAssignOperatorSkills = normalizedSkills;
          }
        } catch (error) {
          errors.push('Матрица навыков операторов: некорректный JSON.');
        }
      }

      const autoAssignOperatorQueuesRaw = (inputs.autoAssignOperatorQueues?.value || '').trim();
      if (autoAssignOperatorQueuesRaw) {
        try {
          const parsedOperatorQueues = JSON.parse(autoAssignOperatorQueuesRaw);
          if (!parsedOperatorQueues || typeof parsedOperatorQueues !== 'object' || Array.isArray(parsedOperatorQueues)) {
            errors.push('Матрица очередей операторов должна быть JSON-объектом.');
          } else {
            const normalizedQueues = {};
            Object.entries(parsedOperatorQueues).forEach(([operator, queues]) => {
              const key = String(operator || '').trim();
              if (!key) {
                return;
              }
              const values = Array.isArray(queues) ? queues : [queues];
              const normalized = Array.from(new Set(values
                .map((value) => String(value || '').trim().toLowerCase())
                .filter(Boolean)));
              if (normalized.length) {
                normalizedQueues[key] = normalized;
              }
            });
            safeAutoAssignOperatorQueues = normalizedQueues;
          }
        } catch (error) {
          errors.push('Матрица очередей операторов: некорректный JSON.');
        }
      }

      const safeSlaPolicyAuditRequireLayers = Boolean(inputs.slaPolicyAuditRequireLayers?.checked);
      const safeSlaPolicyAuditRequireOwner = Boolean(inputs.slaPolicyAuditRequireOwner?.checked);
      const safeSlaPolicyAuditRequireReview = Boolean(inputs.slaPolicyAuditRequireReview?.checked);
      const safeSlaPolicyAuditReviewTtlHours = sanitizeIntValue(
        inputs.slaPolicyAuditReviewTtlHours?.value,
        1,
        2160,
        defaults.sla_critical_auto_assign_audit_review_ttl_hours
      );
      const safeSlaPolicyAuditBroadRuleCoveragePct = sanitizeIntValue(
        inputs.slaPolicyAuditBroadRuleCoveragePct?.value,
        1,
        100,
        defaults.sla_critical_auto_assign_audit_broad_rule_coverage_pct
      );
      const safeSlaPolicyAuditBlockOnConflicts = Boolean(inputs.slaPolicyAuditBlockOnConflicts?.checked);
      const safeSlaPolicyGovernanceReviewRequired = Boolean(inputs.slaPolicyGovernanceReviewRequired?.checked);
      const safeSlaPolicyGovernanceReviewPathRaw = String(inputs.slaPolicyGovernanceReviewPath?.value || '').trim().toLowerCase();
      const safeSlaPolicyGovernanceReviewPath = ['custom', 'light', 'standard', 'strict'].includes(safeSlaPolicyGovernanceReviewPathRaw)
        ? safeSlaPolicyGovernanceReviewPathRaw
        : defaults.sla_critical_auto_assign_governance_review_path;
      const safeSlaPolicyGovernanceReviewTtlHours = sanitizeIntValue(
        inputs.slaPolicyGovernanceReviewTtlHours?.value,
        1,
        2160,
        defaults.sla_critical_auto_assign_governance_review_ttl_hours
      );
      const safeSlaPolicyGovernanceDryRunTicketRequired = Boolean(inputs.slaPolicyGovernanceDryRunTicketRequired?.checked);
      const safeSlaPolicyGovernanceDecisionRequired = Boolean(inputs.slaPolicyGovernanceDecisionRequired?.checked);

      const escalationWebhookUrls = String(inputs.escalationWebhookUrls?.value || '')
        .split(/[\n,;]/)
        .map((value) => value.trim())
        .filter(Boolean);
      const safeEscalationWebhookUrls = Array.from(new Set(escalationWebhookUrls));
      const escalationWebhookCooldownRaw = Math.round(Number(inputs.escalationWebhookCooldownMinutes?.value));
      const safeEscalationWebhookCooldown = Number.isFinite(escalationWebhookCooldownRaw)
        ? Math.min(1440, Math.max(1, escalationWebhookCooldownRaw))
        : defaults.escalation_webhook_cooldown_minutes;
      if (!Number.isFinite(escalationWebhookCooldownRaw) || escalationWebhookCooldownRaw < 1 || escalationWebhookCooldownRaw > 1440) {
        errors.push('Cooldown webhook должен быть в диапазоне 1..1440 минут.');
      }

      const escalationWebhookTimeoutRaw = Math.round(Number(inputs.escalationWebhookTimeoutMs?.value));
      const safeEscalationWebhookTimeout = Number.isFinite(escalationWebhookTimeoutRaw)
        ? Math.min(30000, Math.max(500, escalationWebhookTimeoutRaw))
        : defaults.escalation_webhook_timeout_ms;
      if (!Number.isFinite(escalationWebhookTimeoutRaw) || escalationWebhookTimeoutRaw < 500 || escalationWebhookTimeoutRaw > 30000) {
        errors.push('Timeout webhook должен быть в диапазоне 500..30000 мс.');
      }

      const escalationWebhookRetryAttemptsRaw = Math.round(Number(inputs.escalationWebhookRetryAttempts?.value));
      const safeEscalationWebhookRetryAttempts = Number.isFinite(escalationWebhookRetryAttemptsRaw)
        ? Math.min(5, Math.max(1, escalationWebhookRetryAttemptsRaw))
        : defaults.escalation_webhook_retry_attempts;
      if (!Number.isFinite(escalationWebhookRetryAttemptsRaw) || escalationWebhookRetryAttemptsRaw < 1 || escalationWebhookRetryAttemptsRaw > 5) {
        errors.push('Retry попытки webhook должны быть в диапазоне 1..5.');
      }

      const escalationWebhookRetryBackoffRaw = Math.round(Number(inputs.escalationWebhookRetryBackoffMs?.value));
      const safeEscalationWebhookRetryBackoff = Number.isFinite(escalationWebhookRetryBackoffRaw)
        ? Math.min(5000, Math.max(50, escalationWebhookRetryBackoffRaw))
        : defaults.escalation_webhook_retry_backoff_ms;
      if (!Number.isFinite(escalationWebhookRetryBackoffRaw) || escalationWebhookRetryBackoffRaw < 50 || escalationWebhookRetryBackoffRaw > 5000) {
        errors.push('Retry backoff webhook должен быть в диапазоне 50..5000 мс.');
      }

      const safeEscalationWebhookEventName = String(inputs.escalationWebhookEventName?.value || '').trim()
        || defaults.escalation_webhook_event_name;
      const severityRaw = String(inputs.escalationWebhookSeverity?.value || '').trim().toLowerCase();
      const allowedEscalationSeverities = new Set(['critical', 'high', 'medium', 'low']);
      const safeEscalationWebhookSeverity = allowedEscalationSeverities.has(severityRaw)
        ? severityRaw
        : defaults.escalation_webhook_event_severity;

      const escalationWebhookMaxTicketsRaw = Math.round(Number(inputs.escalationWebhookMaxTicketsPerRun?.value));
      const safeEscalationWebhookMaxTicketsPerRun = Number.isFinite(escalationWebhookMaxTicketsRaw)
        ? Math.min(500, Math.max(1, escalationWebhookMaxTicketsRaw))
        : defaults.escalation_webhook_max_tickets_per_run;
      if (!Number.isFinite(escalationWebhookMaxTicketsRaw) || escalationWebhookMaxTicketsRaw < 1 || escalationWebhookMaxTicketsRaw > 500) {
        errors.push('Лимит тикетов webhook payload должен быть в диапазоне 1..500.');
      }

      if (Boolean(inputs.escalationWebhookEnabled?.checked) && safeEscalationWebhookUrls.length === 0) {
        errors.push('Укажите хотя бы один webhook URL для включённого режима уведомлений.');
      }

      const workspaceContractTimeoutRaw = Math.round(Number(inputs.workspaceContractTimeout?.value));
      const workspaceRetryAttemptsRaw = Math.round(Number(inputs.workspaceRetryAttempts?.value));
      const workspaceContractIncludeRaw = String(inputs.workspaceContractInclude?.value || '');
      const workspaceMessagesPageLimitRaw = Math.round(Number(inputs.workspaceMessagesPageLimit?.value));
      const workspaceFailureStreakThresholdRaw = Math.round(Number(inputs.workspaceFailureStreakThreshold?.value));
      const workspaceFailureCooldownMsRaw = Math.round(Number(inputs.workspaceFailureCooldownMs?.value));
      const workspaceAutosaveDelayRaw = Math.round(Number(inputs.workspaceDraftAutosaveDelay?.value));
      const workspaceTelemetryIntervalRaw = Math.round(Number(inputs.workspaceDraftTelemetryInterval?.value));
      const workspaceOpenSloMsRaw = Math.round(Number(inputs.workspaceOpenSloMs?.value));
      const quickSnoozeMinutesRaw = Math.round(Number(inputs.quickSnoozeMinutes?.value));
      const overdueThresholdHoursRaw = Math.round(Number(inputs.overdueThresholdHours?.value));
      const listPollIntervalRaw = Math.round(Number(inputs.listPollIntervalMs?.value));
      const historyPollIntervalRaw = Math.round(Number(inputs.historyPollIntervalMs?.value));
      const workspaceAbRolloutPercentRaw = Math.round(Number(inputs.workspaceAbRolloutPercent?.value));

      const safeWorkspaceContractTimeout = Number.isFinite(workspaceContractTimeoutRaw)
        ? Math.min(30000, Math.max(1000, workspaceContractTimeoutRaw))
        : defaults.workspace_contract_timeout_ms;
      const safeWorkspaceRetryAttempts = Number.isFinite(workspaceRetryAttemptsRaw)
        ? Math.min(5, Math.max(0, workspaceRetryAttemptsRaw))
        : defaults.workspace_contract_retry_attempts;
      const workspaceIncludeAllowed = ['messages', 'context', 'sla', 'permissions'];
      const safeWorkspaceContractInclude = (() => {
        const parts = workspaceContractIncludeRaw
          .split(',')
          .map((part) => part.trim().toLowerCase())
          .filter(Boolean);
        const unique = [];
        parts.forEach((part) => {
          if (!workspaceIncludeAllowed.includes(part) || unique.includes(part)) {
            return;
          }
          unique.push(part);
        });
        if (unique.length === 0) {
          return defaults.workspace_contract_include;
        }
        return unique.join(',');
      })();
      const safeWorkspaceMessagesPageLimit = Number.isFinite(workspaceMessagesPageLimitRaw)
        ? Math.min(200, Math.max(10, workspaceMessagesPageLimitRaw))
        : defaults.workspace_messages_page_limit;
      const safeWorkspaceFailureStreakThreshold = Number.isFinite(workspaceFailureStreakThresholdRaw)
        ? Math.min(10, Math.max(1, workspaceFailureStreakThresholdRaw))
        : defaults.workspace_failure_streak_threshold;
      const safeWorkspaceFailureCooldownMs = Number.isFinite(workspaceFailureCooldownMsRaw)
        ? Math.min(120000, Math.max(1000, workspaceFailureCooldownMsRaw))
        : defaults.workspace_failure_cooldown_ms;
      const safeWorkspaceAutosaveDelay = Number.isFinite(workspaceAutosaveDelayRaw)
        ? Math.min(5000, Math.max(300, workspaceAutosaveDelayRaw))
        : defaults.workspace_draft_autosave_delay_ms;
      const safeWorkspaceTelemetryInterval = Number.isFinite(workspaceTelemetryIntervalRaw)
        ? Math.min(120000, Math.max(5000, workspaceTelemetryIntervalRaw))
        : defaults.workspace_draft_telemetry_interval_ms;
      const safeQuickSnoozeMinutes = Number.isFinite(quickSnoozeMinutesRaw)
        ? Math.min(1440, Math.max(5, quickSnoozeMinutesRaw))
        : defaults.quick_snooze_minutes;
      const safeOverdueThresholdHours = Number.isFinite(overdueThresholdHoursRaw)
        ? Math.min(168, Math.max(1, overdueThresholdHoursRaw))
        : defaults.overdue_threshold_hours;
      const safeListPollInterval = Number.isFinite(listPollIntervalRaw)
        ? Math.min(60000, Math.max(3000, listPollIntervalRaw))
        : defaults.list_poll_interval_ms;
      const safeHistoryPollInterval = Number.isFinite(historyPollIntervalRaw)
        ? Math.min(60000, Math.max(3000, historyPollIntervalRaw))
        : defaults.history_poll_interval_ms;
      const safeWorkspaceOpenSloMs = Number.isFinite(workspaceOpenSloMsRaw)
        ? Math.min(10000, Math.max(500, workspaceOpenSloMsRaw))
        : defaults.workspace_open_slo_ms;
      const safeWorkspaceAbRolloutPercent = Number.isFinite(workspaceAbRolloutPercentRaw)
        ? Math.min(100, Math.max(0, workspaceAbRolloutPercentRaw))
        : defaults.workspace_ab_rollout_percent;

      if (!Number.isFinite(target) || target <= 0) {
        errors.push('SLA-цель должна быть положительным числом.');
      }
      if (!Number.isFinite(warning) || warning <= 0) {
        errors.push('Порог предупреждения SLA должен быть положительным числом.');
      }
      if (!Number.isFinite(critical) || critical <= 0) {
        errors.push('Критичный SLA должен быть положительным числом.');
      }
      if (safeWarning >= safeTarget) {
        errors.push('Порог предупреждения SLA должен быть меньше SLA-цели.');
      }
      if (safeCritical >= safeTarget) {
        errors.push('Критичный SLA должен быть меньше SLA-цели.');
      }
      if (!Number.isFinite(workspaceContractTimeoutRaw) || workspaceContractTimeoutRaw < 1000 || workspaceContractTimeoutRaw > 30000) {
        errors.push('Таймаут workspace-контракта должен быть в диапазоне 1000..30000 мс.');
      }
      if (!Number.isFinite(workspaceRetryAttemptsRaw) || workspaceRetryAttemptsRaw < 0 || workspaceRetryAttemptsRaw > 5) {
        errors.push('Retry попытки workspace должны быть в диапазоне 0..5.');
      }
      const workspaceIncludeInvalid = workspaceContractIncludeRaw
        .split(',')
        .map((part) => part.trim().toLowerCase())
        .filter(Boolean)
        .some((part) => !workspaceIncludeAllowed.includes(part));
      if (workspaceIncludeInvalid) {
        errors.push('Состав workspace-контракта: разрешены только messages, context, sla, permissions.');
      }
      if (!Number.isFinite(workspaceMessagesPageLimitRaw) || workspaceMessagesPageLimitRaw < 10 || workspaceMessagesPageLimitRaw > 200) {
        errors.push('Лимит сообщений workspace должен быть в диапазоне 10..200.');
      }
      if (!Number.isFinite(workspaceFailureStreakThresholdRaw) || workspaceFailureStreakThresholdRaw < 1 || workspaceFailureStreakThresholdRaw > 10) {
        errors.push('Порог fallback подряд должен быть в диапазоне 1..10.');
      }
      if (!Number.isFinite(workspaceFailureCooldownMsRaw) || workspaceFailureCooldownMsRaw < 1000 || workspaceFailureCooldownMsRaw > 120000) {
        errors.push('Cooldown fallback должен быть в диапазоне 1000..120000 мс.');
      }
      if (!Number.isFinite(workspaceAutosaveDelayRaw) || workspaceAutosaveDelayRaw < 300 || workspaceAutosaveDelayRaw > 5000) {
        errors.push('Задержка автосохранения черновика должна быть в диапазоне 300..5000 мс.');
      }
      if (!Number.isFinite(workspaceTelemetryIntervalRaw) || workspaceTelemetryIntervalRaw < 5000 || workspaceTelemetryIntervalRaw > 120000) {
        errors.push('Интервал telemetry черновиков должен быть в диапазоне 5000..120000 мс.');
      }
      if (!Number.isFinite(workspaceOpenSloMsRaw) || workspaceOpenSloMsRaw < 500 || workspaceOpenSloMsRaw > 10000) {
        errors.push('Open SLO должен быть в диапазоне 500..10000 мс.');
      }
      if (!Number.isFinite(quickSnoozeMinutesRaw) || quickSnoozeMinutesRaw < 5 || quickSnoozeMinutesRaw > 1440) {
        errors.push('Quick snooze должен быть в диапазоне 5..1440 минут.');
      }
      if (!Number.isFinite(overdueThresholdHoursRaw) || overdueThresholdHoursRaw < 1 || overdueThresholdHoursRaw > 168) {
        errors.push('Порог просроченных диалогов должен быть в диапазоне 1..168 часов.');
      }
      if (!Number.isFinite(listPollIntervalRaw) || listPollIntervalRaw < 3000 || listPollIntervalRaw > 60000) {
        errors.push('Интервал автообновления списка должен быть в диапазоне 3000..60000 мс.');
      }
      if (!Number.isFinite(historyPollIntervalRaw) || historyPollIntervalRaw < 3000 || historyPollIntervalRaw > 60000) {
        errors.push('Интервал обновления истории должен быть в диапазоне 3000..60000 мс.');
      }
      if (!Number.isFinite(workspaceAbRolloutPercentRaw) || workspaceAbRolloutPercentRaw < 0 || workspaceAbRolloutPercentRaw > 100) {
        errors.push('A/B rollout должен быть в диапазоне 0..100%.');
      }

      const parsePercentThreshold = (rawValue, fallbackValue, label) => {
        const parsed = Number(rawValue);
        if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 100) {
          errors.push(`${label} должен быть в диапазоне 0.01..100%.`);
          return fallbackValue;
        }
        return Math.min(1, Math.max(0.0001, parsed / 100));
      };

      const parseRelativePercentThreshold = (rawValue, fallbackValue, label) => {
        const parsed = Number(rawValue);
        if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 500) {
          errors.push(`${label} должен быть в диапазоне 0.1..500%.`);
          return fallbackValue;
        }
        return Math.min(5, Math.max(0.001, parsed / 100));
      };

      const safeRenderErrorRate = parsePercentThreshold(
        inputs.guardrailRenderErrorRate?.value,
        defaults.workspace_guardrail_render_error_rate,
        'Render error SLO'
      );
      const safeFallbackRate = parsePercentThreshold(
        inputs.guardrailFallbackRate?.value,
        defaults.workspace_guardrail_fallback_rate,
        'Fallback SLO'
      );
      const safeAbandonRate = parsePercentThreshold(
        inputs.guardrailAbandonRate?.value,
        defaults.workspace_guardrail_abandon_rate,
        'Abandon порог'
      );
      const safeSlowOpenRate = parsePercentThreshold(
        inputs.guardrailSlowOpenRate?.value,
        defaults.workspace_guardrail_slow_open_rate,
        'Slow open порог'
      );

      const dimensionMinEventsRaw = Math.round(Number(inputs.dimensionMinEvents?.value));
      const cohortMinEventsRaw = Math.round(Number(inputs.cohortMinEvents?.value));
      const safeDimensionMinEvents = Number.isFinite(dimensionMinEventsRaw)
        ? Math.min(1000, Math.max(5, dimensionMinEventsRaw))
        : defaults.workspace_dimension_min_events;
      const safeCohortMinEvents = Number.isFinite(cohortMinEventsRaw)
        ? Math.min(1000, Math.max(5, cohortMinEventsRaw))
        : defaults.workspace_cohort_min_events;
      if (!Number.isFinite(dimensionMinEventsRaw) || dimensionMinEventsRaw < 5 || dimensionMinEventsRaw > 1000) {
        errors.push('Мин. событий для сегментов должно быть в диапазоне 5..1000.');
      }
      if (!Number.isFinite(cohortMinEventsRaw) || cohortMinEventsRaw < 5 || cohortMinEventsRaw > 1000) {
        errors.push('Мин. событий на cohort должно быть в диапазоне 5..1000.');
      }

      const kpiOutcomeMinSamplesRaw = Math.round(Number(inputs.kpiOutcomeMinSamples?.value));
      const safeKpiOutcomeMinSamples = Number.isFinite(kpiOutcomeMinSamplesRaw)
        ? Math.min(1000, Math.max(1, kpiOutcomeMinSamplesRaw))
        : defaults.workspace_rollout_kpi_outcome_min_samples_per_cohort;
      if (!Number.isFinite(kpiOutcomeMinSamplesRaw) || kpiOutcomeMinSamplesRaw < 1 || kpiOutcomeMinSamplesRaw > 1000) {
        errors.push('Мин. KPI-сэмплов на cohort должно быть в диапазоне 1..1000.');
      }

      const safeFrtRelativeRegression = parseRelativePercentThreshold(
        inputs.kpiOutcomeFrtRegressionPercent?.value,
        defaults.workspace_rollout_kpi_outcome_frt_max_relative_regression,
        'FRT относительная деградация'
      );
      const safeTtrRelativeRegression = parseRelativePercentThreshold(
        inputs.kpiOutcomeTtrRegressionPercent?.value,
        defaults.workspace_rollout_kpi_outcome_ttr_max_relative_regression,
        'TTR относительная деградация'
      );
      const safeSlaAbsoluteDelta = parsePercentThreshold(
        inputs.kpiOutcomeSlaAbsDeltaPercent?.value,
        defaults.workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta,
        'SLA breach абсолютная дельта'
      );
      if (safeSlaAbsoluteDelta > 1) {
        errors.push('SLA breach абсолютная дельта не может быть больше 100 п.п.');
      }

      const slaRelativeMultiplierRaw = Number(inputs.kpiOutcomeSlaRelativeMultiplier?.value);
      const safeSlaRelativeMultiplier = Number.isFinite(slaRelativeMultiplierRaw)
        ? Math.min(10, Math.max(1, slaRelativeMultiplierRaw))
        : defaults.workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier;
      if (!Number.isFinite(slaRelativeMultiplierRaw) || slaRelativeMultiplierRaw < 1 || slaRelativeMultiplierRaw > 10) {
        errors.push('SLA breach относительный множитель должен быть в диапазоне 1..10.');
      }

      function normalizeCsvKpis(rawValue, fallback) {
        const normalized = String(rawValue || '')
          .split(',')
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean);
        const unique = Array.from(new Set(normalized));
        return unique.length ? unique : [...fallback];
      }

      const allowedOutcomeKpis = new Set(['frt', 'ttr', 'sla_breach']);
      const safeRequiredOutcomeKpis = normalizeCsvKpis(
        inputs.rolloutRequiredOutcomeKpis?.value,
        defaults.workspace_rollout_required_outcome_kpis
      ).filter((kpi) => allowedOutcomeKpis.has(kpi));
      if (!safeRequiredOutcomeKpis.length) {
        errors.push('Укажите хотя бы один корректный outcome KPI (frt, ttr, sla_breach).');
      }

      const winnerMinOpenImprovementPercentRaw = Number(inputs.winnerMinOpenImprovementPercent?.value);
      const safeWinnerMinOpenImprovement = Number.isFinite(winnerMinOpenImprovementPercentRaw)
        ? Math.min(50, Math.max(0, winnerMinOpenImprovementPercentRaw)) / 100
        : defaults.workspace_rollout_winner_min_open_improvement;
      if (!Number.isFinite(winnerMinOpenImprovementPercentRaw) || winnerMinOpenImprovementPercentRaw < 0 || winnerMinOpenImprovementPercentRaw > 50) {
        errors.push('Мин. улучшение open time должно быть в диапазоне 0..50%.');
      }

      const allowedViews = new Set(['all', 'active', 'new', 'unassigned', 'overdue', 'sla_critical', 'escalation_required']);
      const safeDefaultViewRaw = String(inputs.defaultView?.value || 'all').trim().toLowerCase();
      const safeDefaultView = allowedViews.has(safeDefaultViewRaw) ? safeDefaultViewRaw : 'all';

      const rawWindowPresetList = String(inputs.slaWindowPresets?.value || '')
        .split(',')
        .map((value) => Number.parseInt(value.trim(), 10))
        .filter((value) => Number.isFinite(value));
      const normalizedWindowPresetList = Array.from(new Set(rawWindowPresetList))
        .filter((value) => value >= 5 && value <= 1440)
        .sort((left, right) => left - right);
      const safeSlaWindowPresets = normalizedWindowPresetList.length
        ? normalizedWindowPresetList.slice(0, 12)
        : [15, 30, 60, 120];
      if (rawWindowPresetList.length === 0) {
        errors.push('Добавьте хотя бы один пресет SLA-фильтра (в минутах).');
      }
      if (normalizedWindowPresetList.length !== rawWindowPresetList.length) {
        errors.push('Пресеты SLA-фильтра должны быть в диапазоне 5..1440 минут.');
      }
      if (normalizedWindowPresetList.length > 12) {
        errors.push('Слишком много пресетов SLA-фильтра: максимум 12 значений.');
      }

      const slaWindowDefaultRaw = Number.parseInt(inputs.slaWindowDefault?.value, 10);
      const safeSlaWindowDefault = Number.isFinite(slaWindowDefaultRaw) && slaWindowDefaultRaw >= 5 && slaWindowDefaultRaw <= 1440
        ? slaWindowDefaultRaw
        : null;
      if (inputs.slaWindowDefault?.value && safeSlaWindowDefault === null) {
        errors.push('Пресет SLA по умолчанию должен быть в диапазоне 5..1440 минут.');
      }
      if (safeSlaWindowDefault !== null && !safeSlaWindowPresets.includes(safeSlaWindowDefault)) {
        errors.push('Пресет SLA по умолчанию должен входить в список пресетов.');
      }

      const safeWorkspaceAbExperimentName = String(inputs.workspaceAbExperimentName?.value || '').trim()
        || defaults.workspace_ab_experiment_name;
      const safeWorkspaceAbOperatorSegment = String(inputs.workspaceAbOperatorSegment?.value || '').trim()
        || defaults.workspace_ab_operator_segment;
      const safeWorkspaceAbPrimaryKpis = normalizeCsvKpis(
        inputs.workspaceAbPrimaryKpis?.value,
        defaults.workspace_ab_primary_kpis
      );
      const safeWorkspaceAbSecondaryKpis = normalizeCsvKpis(
        inputs.workspaceAbSecondaryKpis?.value,
        defaults.workspace_ab_secondary_kpis
      );
      if (safeWorkspaceAbPrimaryKpis.length > 10) {
        errors.push('Список A/B primary KPI должен содержать не более 10 значений.');
      }
      if (safeWorkspaceAbSecondaryKpis.length > 15) {
        errors.push('Список A/B secondary KPI должен содержать не более 15 значений.');
      }

      let safeWorkspaceAbOperatorOverrides = {};
      const abOverridesRaw = (inputs.workspaceAbOperatorOverrides?.value || '').trim();
      if (abOverridesRaw) {
        try {
          const parsed = JSON.parse(abOverridesRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('A/B operator overrides должны быть JSON-объектом вида {"operator":"test|control"}.');
          } else {
            Object.entries(parsed).forEach(([identityRaw, variantRaw]) => {
              const identity = String(identityRaw || '').trim();
              const variant = String(variantRaw || '').trim().toLowerCase();
              if (!identity) {
                return;
              }
              if (variant !== 'test' && variant !== 'control') {
                return;
              }
              safeWorkspaceAbOperatorOverrides[identity] = variant;
            });
          }
        } catch (error) {
          errors.push('A/B operator overrides: некорректный JSON.');
        }
      }

      return {
        errors,
        config: {
          dialog_sla_target_minutes: safeTarget,
          dialog_sla_warning_minutes: safeWarning,
          dialog_sla_critical_minutes: safeCritical,
          dialog_sla_critical_orchestration_mode: safeOrchestrationMode,
          dialog_ai_agent_enabled: Boolean(inputs.aiAgentEnabled?.checked),
          dialog_ai_agent_mode: safeAiAgentMode,
          dialog_ai_agent_auto_reply_threshold: safeAiAgentAutoReplyThreshold,
          dialog_ai_agent_suggest_threshold: safeAiAgentSuggestThreshold,
          dialog_ai_agent_max_auto_replies_per_dialog: safeAiAgentMaxAutoReplies,
          dialog_sla_critical_escalation_enabled: Boolean(inputs.escalationEnabled?.checked),
          dialog_sla_critical_auto_assign_enabled: Boolean(inputs.autoAssignEnabled?.checked),
          dialog_sla_critical_auto_assign_to: (inputs.autoAssignTo?.value || '').trim(),
          dialog_sla_critical_auto_assign_max_per_run: safeAutoAssignMaxPerRun,
          dialog_sla_critical_auto_assign_actor: safeAutoAssignActor,
          dialog_sla_critical_auto_assign_max_open_per_operator: safeAutoAssignMaxOpenPerOperator,
          dialog_sla_critical_auto_assign_require_categories: Boolean(inputs.autoAssignRequireCategories?.checked),
          dialog_sla_critical_auto_assign_include_assigned: Boolean(inputs.autoAssignIncludeAssigned?.checked),
          dialog_sla_critical_auto_assign_rules: safeAutoAssignRules,
          dialog_sla_critical_operator_skills: safeAutoAssignOperatorSkills,
          dialog_sla_critical_operator_queues: safeAutoAssignOperatorQueues,
          dialog_sla_critical_auto_assign_audit_require_layers: safeSlaPolicyAuditRequireLayers,
          dialog_sla_critical_auto_assign_audit_require_owner: safeSlaPolicyAuditRequireOwner,
          dialog_sla_critical_auto_assign_audit_require_review: safeSlaPolicyAuditRequireReview,
          dialog_sla_critical_auto_assign_audit_review_ttl_hours: safeSlaPolicyAuditReviewTtlHours,
          dialog_sla_critical_auto_assign_audit_broad_rule_coverage_pct: safeSlaPolicyAuditBroadRuleCoveragePct,
          dialog_sla_critical_auto_assign_audit_block_on_conflicts: safeSlaPolicyAuditBlockOnConflicts,
          dialog_sla_critical_auto_assign_governance_review_required: safeSlaPolicyGovernanceReviewRequired,
          dialog_sla_critical_auto_assign_governance_review_path: safeSlaPolicyGovernanceReviewPath,
          dialog_sla_critical_auto_assign_governance_review_ttl_hours: safeSlaPolicyGovernanceReviewTtlHours,
          dialog_sla_critical_auto_assign_governance_dry_run_ticket_required: safeSlaPolicyGovernanceDryRunTicketRequired,
          dialog_sla_critical_auto_assign_governance_decision_required: safeSlaPolicyGovernanceDecisionRequired,
          dialog_sla_critical_escalation_webhook_enabled: Boolean(inputs.escalationWebhookEnabled?.checked),
          dialog_sla_critical_escalation_include_assigned: Boolean(inputs.escalationIncludeAssigned?.checked),
          dialog_sla_critical_escalation_webhook_urls: safeEscalationWebhookUrls,
          dialog_sla_critical_escalation_webhook_cooldown_minutes: safeEscalationWebhookCooldown,
          dialog_sla_critical_escalation_webhook_timeout_ms: safeEscalationWebhookTimeout,
          dialog_sla_critical_escalation_webhook_retry_attempts: safeEscalationWebhookRetryAttempts,
          dialog_sla_critical_escalation_webhook_retry_backoff_ms: safeEscalationWebhookRetryBackoff,
          dialog_sla_critical_escalation_webhook_event_name: safeEscalationWebhookEventName,
          dialog_sla_critical_escalation_webhook_severity: safeEscalationWebhookSeverity,
          dialog_sla_critical_escalation_webhook_max_tickets_per_run: safeEscalationWebhookMaxTicketsPerRun,
          dialog_sla_window_presets_minutes: safeSlaWindowPresets,
          dialog_sla_window_default_minutes: safeSlaWindowDefault,
          dialog_default_view: safeDefaultView,
          dialog_quick_snooze_minutes: safeQuickSnoozeMinutes,
          dialog_overdue_threshold_hours: safeOverdueThresholdHours,
          dialog_workspace_v1: Boolean(inputs.workspaceEnabled?.checked),
          dialog_workspace_single_mode: Boolean(inputs.workspaceSingleMode?.checked),
          dialog_sla_critical_auto_sort: Boolean(inputs.slaCriticalAutoSort?.checked),
          dialog_sla_critical_pin_unassigned_only: Boolean(inputs.slaCriticalPinUnassignedOnly?.checked),
          dialog_sla_critical_view_unassigned_only: Boolean(inputs.slaCriticalViewUnassignedOnly?.checked),
          dialog_workspace_inline_navigation: Boolean(inputs.workspaceInlineNavigation?.checked),
          dialog_workspace_contract_timeout_ms: safeWorkspaceContractTimeout,
          dialog_workspace_contract_retry_attempts: safeWorkspaceRetryAttempts,
          dialog_workspace_contract_include: safeWorkspaceContractInclude,
          dialog_workspace_messages_page_limit: safeWorkspaceMessagesPageLimit,
          dialog_workspace_disable_legacy_fallback: Boolean(inputs.workspaceDisableLegacyFallback?.checked),
          dialog_workspace_failure_streak_threshold: safeWorkspaceFailureStreakThreshold,
          dialog_workspace_failure_cooldown_ms: safeWorkspaceFailureCooldownMs,
          dialog_workspace_draft_autosave_delay_ms: safeWorkspaceAutosaveDelay,
          dialog_workspace_draft_telemetry_interval_ms: safeWorkspaceTelemetryInterval,
          dialog_workspace_open_slo_ms: safeWorkspaceOpenSloMs,
          dialog_list_poll_interval_ms: safeListPollInterval,
          dialog_history_poll_interval_ms: safeHistoryPollInterval,
          dialog_workspace_force_workspace: Boolean(inputs.workspaceForceWorkspace?.checked),
          dialog_workspace_decommission_legacy_modal: Boolean(inputs.workspaceDecommissionLegacyModal?.checked),
          dialog_workspace_ab_enabled: Boolean(inputs.workspaceAbEnabled?.checked),
          dialog_workspace_ab_rollout_percent: safeWorkspaceAbRolloutPercent,
          dialog_workspace_ab_experiment_name: safeWorkspaceAbExperimentName,
          dialog_workspace_ab_operator_segment: safeWorkspaceAbOperatorSegment,
          dialog_workspace_ab_primary_kpis: safeWorkspaceAbPrimaryKpis,
          dialog_workspace_ab_secondary_kpis: safeWorkspaceAbSecondaryKpis,
          dialog_workspace_ab_operator_overrides: safeWorkspaceAbOperatorOverrides,
          dialog_workspace_guardrail_render_error_rate: safeRenderErrorRate,
          dialog_workspace_guardrail_fallback_rate: safeFallbackRate,
          dialog_workspace_guardrail_abandon_rate: safeAbandonRate,
          dialog_workspace_guardrail_slow_open_rate: safeSlowOpenRate,
          dialog_workspace_dimension_min_events: safeDimensionMinEvents,
          dialog_workspace_cohort_min_events: safeCohortMinEvents,
          dialog_workspace_rollout_kpi_outcome_min_samples_per_cohort: safeKpiOutcomeMinSamples,
          dialog_workspace_rollout_kpi_outcome_frt_max_relative_regression: safeFrtRelativeRegression,
          dialog_workspace_rollout_kpi_outcome_ttr_max_relative_regression: safeTtrRelativeRegression,
          dialog_workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta: safeSlaAbsoluteDelta,
          dialog_workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier: safeSlaRelativeMultiplier,
          dialog_workspace_rollout_required_outcome_kpis: safeRequiredOutcomeKpis,
          dialog_workspace_rollout_winner_min_open_improvement: safeWinnerMinOpenImprovement,
        },
      };
    }

    function collectMacroClientContextConfig() {
      const inputs = getInputs();
      const defaults = getDefaultDialogSlaConfig();
      const errors = [];

      const splitUniqueValues = (value) => Array.from(new Set(
        String(value || '')
          .split(/[\n,;]/)
          .map((item) => String(item || '').trim().toLowerCase())
          .filter(Boolean)
      ));

      const validateExternalUrlTemplate = (value, title) => {
        if (!value) {
          return;
        }
        if (!(value.startsWith('https://') || value.startsWith('http://'))) {
          errors.push(`${title}: URL-шаблон должен начинаться с http:// или https://.`);
        }
      };

      let safeMacroVariableDefaults = {};
      const macroDefaultsRaw = (inputs.macroVariableDefaults?.value || '').trim();
      if (macroDefaultsRaw) {
        try {
          const parsed = JSON.parse(macroDefaultsRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('Default переменные макросов должны быть JSON-объектом вида {"key":"value"}.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([keyRaw, valueRaw]) => {
              const key = String(keyRaw || '').trim().toLowerCase();
              const value = String(valueRaw ?? '').trim();
              if (key && value) {
                normalized[key] = value;
              }
            });
            safeMacroVariableDefaults = normalized;
          }
        } catch (error) {
          errors.push('Default переменные макросов: некорректный JSON.');
        }
      }

      const safeMacroPublishAllowedRoles = Array.from(new Set(
        String(inputs.macroPublishAllowedRoles?.value || '')
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean)
      ));

      let safeMacroVariableCatalog = [];
      const macroCatalogRaw = (inputs.macroVariableCatalog?.value || '').trim();
      if (macroCatalogRaw) {
        try {
          const parsed = JSON.parse(macroCatalogRaw);
          if (!Array.isArray(parsed)) {
            errors.push('Каталог macro-переменных должен быть JSON-массивом.');
          } else {
            const normalized = [];
            const keys = new Set();
            parsed.forEach((entry) => {
              if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
                return;
              }
              const key = String(entry.key || '').trim().toLowerCase();
              const label = String(entry.label || '').trim();
              const defaultValue = String(entry.default_value ?? '').trim();
              if (!key || !label || keys.has(key)) {
                return;
              }
              keys.add(key);
              const item = { key, label };
              if (defaultValue) {
                item.default_value = defaultValue;
              }
              normalized.push(item);
            });
            safeMacroVariableCatalog = normalized;
          }
        } catch (error) {
          errors.push('Каталог macro-переменных: некорректный JSON.');
        }
      }

      const safeMacroVariableCatalogExternalUrl = String(inputs.macroVariableCatalogExternalUrl?.value || '').trim();
      if (safeMacroVariableCatalogExternalUrl
        && !(safeMacroVariableCatalogExternalUrl.startsWith('https://') || safeMacroVariableCatalogExternalUrl.startsWith('http://'))) {
        errors.push('External catalog URL должен начинаться с http:// или https://.');
      }
      const safeMacroVariableCatalogExternalTimeoutMs = sanitizeIntValue(
        inputs.macroVariableCatalogExternalTimeoutMs?.value,
        200,
        10000,
        defaults.macro_variable_catalog_external_timeout_ms
      );
      const safeMacroVariableCatalogExternalCacheTtlSeconds = sanitizeIntValue(
        inputs.macroVariableCatalogExternalCacheTtlSeconds?.value,
        0,
        3600,
        defaults.macro_variable_catalog_external_cache_ttl_seconds
      );
      const safeMacroCatalogExternalAuthHeaderRaw = String(inputs.macroVariableCatalogExternalAuthHeader?.value || '').trim();
      const safeMacroCatalogExternalAuthHeader = safeMacroCatalogExternalAuthHeaderRaw
        ? safeMacroCatalogExternalAuthHeaderRaw
        : defaults.macro_variable_catalog_external_auth_header;
      if (!/^[A-Za-z0-9-]{1,64}$/.test(safeMacroCatalogExternalAuthHeader)) {
        errors.push('Auth header для external catalog API должен содержать только латиницу, цифры и дефис (1..64 символа).');
      }
      const safeMacroCatalogExternalAuthToken = String(inputs.macroVariableCatalogExternalAuthToken?.value || '').trim();

      const safeCrmProfileTemplate = String(inputs.workspaceClientCrmProfileUrlTemplate?.value || '').trim();
      const safeCrmProfileLabel = String(inputs.workspaceClientCrmProfileLabel?.value || '').trim();
      const safeContractProfileTemplate = String(inputs.workspaceClientContractProfileUrlTemplate?.value || '').trim();
      const safeContractProfileLabel = String(inputs.workspaceClientContractProfileLabel?.value || '').trim();

      validateExternalUrlTemplate(safeCrmProfileTemplate, 'CRM профиль');
      validateExternalUrlTemplate(safeContractProfileTemplate, 'Contract профиль');

      let safeWorkspaceExternalLinks = [];
      const externalLinksRaw = (inputs.workspaceClientExternalLinks?.value || '').trim();
      if (externalLinksRaw) {
        try {
          const parsed = JSON.parse(externalLinksRaw);
          if (!Array.isArray(parsed)) {
            errors.push('Дополнительные external links должны быть JSON-массивом.');
          } else {
            const normalized = [];
            const keys = new Set();
            parsed.forEach((entry) => {
              if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
                return;
              }
              const key = String(entry.key || '').trim().toLowerCase();
              const label = String(entry.label || '').trim();
              const urlTemplate = String(entry.url_template || '').trim();
              if (!key || !label || !urlTemplate || keys.has(key)) {
                return;
              }
              if (!(urlTemplate.startsWith('https://') || urlTemplate.startsWith('http://'))) {
                errors.push(`External link ${key}: url_template должен начинаться с http:// или https://.`);
                return;
              }
              keys.add(key);
              normalized.push({
                key,
                label,
                url_template: urlTemplate,
                enabled: entry.enabled !== false,
              });
            });
            safeWorkspaceExternalLinks = normalized;
          }
        } catch (error) {
          errors.push('Дополнительные external links: некорректный JSON.');
        }
      }

      const safeWorkspaceExternalProfileUrl = String(inputs.workspaceClientExternalProfileUrl?.value || '').trim();
      const safeWorkspaceExternalProfileTimeoutMs = sanitizeIntValue(
        inputs.workspaceClientExternalProfileTimeoutMs?.value,
        300,
        10000,
        defaults.workspace_client_external_profile_timeout_ms
      );
      const safeWorkspaceExternalProfileCacheTtlSeconds = sanitizeIntValue(
        inputs.workspaceClientExternalProfileCacheTtlSeconds?.value,
        0,
        3600,
        defaults.workspace_client_external_profile_cache_ttl_seconds
      );
      const safeWorkspaceExternalProfileAuthHeaderRaw = String(inputs.workspaceClientExternalProfileAuthHeader?.value || '').trim();
      const safeWorkspaceExternalProfileAuthHeader = safeWorkspaceExternalProfileAuthHeaderRaw
        ? safeWorkspaceExternalProfileAuthHeaderRaw
        : defaults.workspace_client_external_profile_auth_header;
      if (!/^[A-Za-z0-9-]{1,64}$/.test(safeWorkspaceExternalProfileAuthHeader)) {
        errors.push('Auth header для external profile API должен содержать только латиницу, цифры и дефис (1..64 символа).');
      }
      const safeWorkspaceExternalProfileAuthToken = String(inputs.workspaceClientExternalProfileAuthToken?.value || '').trim();

      const allowedContextSources = new Set(['local', 'crm', 'contract', 'external']);
      const safeWorkspaceClientContextRequiredSources = splitUniqueValues(
        inputs.workspaceClientContextRequiredSources?.value
      ).filter((value) => allowedContextSources.has(value));
      const safeWorkspaceClientContextSourcePriority = splitUniqueValues(
        inputs.workspaceClientContextSourcePriority?.value
      ).filter((value) => allowedContextSources.has(value));
      if (!safeWorkspaceClientContextSourcePriority.length) {
        safeWorkspaceClientContextSourcePriority.push(...defaults.workspace_client_context_source_priority);
      }

      const safeWorkspaceClientContextSourceStaleAfterHoursRaw = Math.round(
        Number(inputs.workspaceClientContextSourceStaleAfterHours?.value)
      );
      const safeWorkspaceClientContextSourceStaleAfterHours = Number.isFinite(safeWorkspaceClientContextSourceStaleAfterHoursRaw)
        ? Math.min(8760, Math.max(0, safeWorkspaceClientContextSourceStaleAfterHoursRaw))
        : defaults.workspace_client_context_source_stale_after_hours;
      if (!Number.isFinite(safeWorkspaceClientContextSourceStaleAfterHoursRaw)
        || safeWorkspaceClientContextSourceStaleAfterHoursRaw < 0
        || safeWorkspaceClientContextSourceStaleAfterHoursRaw > 8760) {
        errors.push('TTL freshness источников контекста должен быть в диапазоне 0..8760 часов.');
      }

      let safeWorkspaceClientContextSourceLabels = {};
      const contextSourceLabelsRaw = (inputs.workspaceClientContextSourceLabels?.value || '').trim();
      if (contextSourceLabelsRaw) {
        try {
          const parsed = JSON.parse(contextSourceLabelsRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('Подписи источников контекста должны быть JSON-объектом.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([keyRaw, valueRaw]) => {
              const key = String(keyRaw || '').trim().toLowerCase();
              const label = String(valueRaw || '').trim();
              if (allowedContextSources.has(key) && label) {
                normalized[key] = label;
              }
            });
            safeWorkspaceClientContextSourceLabels = normalized;
          }
        } catch (error) {
          errors.push('Подписи источников контекста: некорректный JSON.');
        }
      }

      let safeWorkspaceClientContextSourceUpdatedAtAttributes = {};
      const contextSourceUpdatedAtRaw = (inputs.workspaceClientContextSourceUpdatedAtAttributes?.value || '').trim();
      if (contextSourceUpdatedAtRaw) {
        try {
          const parsed = JSON.parse(contextSourceUpdatedAtRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('UTC timestamp-атрибуты источников контекста должны быть JSON-объектом.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([keyRaw, valueRaw]) => {
              const key = String(keyRaw || '').trim().toLowerCase();
              if (!allowedContextSources.has(key)) {
                return;
              }
              const values = Array.isArray(valueRaw) ? valueRaw : [valueRaw];
              const attrs = Array.from(new Set(
                values
                  .map((item) => String(item || '').trim().toLowerCase())
                  .filter(Boolean)
              ));
              if (attrs.length) {
                normalized[key] = attrs;
              }
            });
            safeWorkspaceClientContextSourceUpdatedAtAttributes = normalized;
          }
        } catch (error) {
          errors.push('UTC timestamp-атрибуты источников контекста: некорректный JSON.');
        }
      }

      let safeWorkspaceClientContextSourceStaleAfterHoursBySource = {};
      const contextSourceStaleBySourceRaw = (inputs.workspaceClientContextSourceStaleAfterHoursBySource?.value || '').trim();
      if (contextSourceStaleBySourceRaw) {
        try {
          const parsed = JSON.parse(contextSourceStaleBySourceRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('TTL freshness по источникам должен быть JSON-объектом.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([keyRaw, valueRaw]) => {
              const key = String(keyRaw || '').trim().toLowerCase();
              const ttl = Math.round(Number(valueRaw));
              if (!allowedContextSources.has(key) || !Number.isFinite(ttl) || ttl < 0 || ttl > 8760) {
                return;
              }
              normalized[key] = ttl;
            });
            safeWorkspaceClientContextSourceStaleAfterHoursBySource = normalized;
          }
        } catch (error) {
          errors.push('TTL freshness по источникам: некорректный JSON.');
        }
      }

      const safeWorkspaceRequiredClientAttributes = splitUniqueValues(
        inputs.workspaceRequiredClientAttributes?.value
      );

      let safeWorkspaceRequiredClientAttributesBySegment = {};
      const requiredClientAttributesBySegmentRaw = (inputs.workspaceRequiredClientAttributesBySegment?.value || '').trim();
      if (requiredClientAttributesBySegmentRaw) {
        try {
          const parsed = JSON.parse(requiredClientAttributesBySegmentRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('Обязательные атрибуты профиля по сегментам должны быть JSON-объектом.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([segmentRaw, attributesRaw]) => {
              const segmentKey = String(segmentRaw || '').trim().toLowerCase();
              if (!segmentKey) {
                return;
              }
              const values = Array.isArray(attributesRaw) ? attributesRaw : [attributesRaw];
              const attributes = Array.from(new Set(
                values
                  .map((item) => String(item || '').trim().toLowerCase())
                  .filter(Boolean)
              ));
              if (attributes.length) {
                normalized[segmentKey] = attributes;
              }
            });
            safeWorkspaceRequiredClientAttributesBySegment = normalized;
          }
        } catch (error) {
          errors.push('Обязательные атрибуты профиля по сегментам: некорректный JSON.');
        }
      }

      let safeWorkspaceClientHiddenAttributes = [];
      const hiddenAttributesRaw = (inputs.workspaceClientHiddenAttributes?.value || '').trim();
      if (hiddenAttributesRaw) {
        try {
          const parsed = JSON.parse(hiddenAttributesRaw);
          if (!Array.isArray(parsed)) {
            errors.push('Скрытые атрибуты клиента должны быть JSON-массивом.');
          } else {
            safeWorkspaceClientHiddenAttributes = Array.from(new Set(
              parsed
                .map((item) => String(item || '').trim().toLowerCase())
                .filter(Boolean)
            ));
          }
        } catch (error) {
          errors.push('Скрытые атрибуты клиента: некорректный JSON.');
        }
      }

      let safeWorkspaceClientAttributeLabels = {};
      const attributeLabelsRaw = (inputs.workspaceClientAttributeLabels?.value || '').trim();
      if (attributeLabelsRaw) {
        try {
          const parsed = JSON.parse(attributeLabelsRaw);
          if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            errors.push('Переименования атрибутов клиента должны быть JSON-объектом вида {"key":"label"}.');
          } else {
            const normalized = {};
            Object.entries(parsed).forEach(([keyRaw, valueRaw]) => {
              const key = String(keyRaw || '').trim().toLowerCase();
              const label = String(valueRaw || '').trim();
              if (key && label) {
                normalized[key] = label;
              }
            });
            safeWorkspaceClientAttributeLabels = normalized;
          }
        } catch (error) {
          errors.push('Переименования атрибутов клиента: некорректный JSON.');
        }
      }

      let safeWorkspaceClientAttributeOrder = [];
      const attributeOrderRaw = (inputs.workspaceClientAttributeOrder?.value || '').trim();
      if (attributeOrderRaw) {
        try {
          const parsed = JSON.parse(attributeOrderRaw);
          if (!Array.isArray(parsed)) {
            errors.push('Порядок атрибутов клиента должен быть JSON-массивом.');
          } else {
            safeWorkspaceClientAttributeOrder = Array.from(new Set(
              parsed
                .map((item) => String(item || '').trim().toLowerCase())
                .filter(Boolean)
            ));
          }
        } catch (error) {
          errors.push('Порядок атрибутов клиента: некорректный JSON.');
        }
      }

      const safeWorkspaceClientExtraAttributesMaxRaw = Math.round(Number(inputs.workspaceClientExtraAttributesMax?.value));
      const safeWorkspaceClientExtraAttributesMax = Number.isFinite(safeWorkspaceClientExtraAttributesMaxRaw)
        ? Math.min(100, Math.max(1, safeWorkspaceClientExtraAttributesMaxRaw))
        : defaults.workspace_client_extra_attributes_max;
      if (!Number.isFinite(safeWorkspaceClientExtraAttributesMaxRaw)
        || safeWorkspaceClientExtraAttributesMaxRaw < 1
        || safeWorkspaceClientExtraAttributesMaxRaw > 100) {
        errors.push('Максимум доп. атрибутов клиента должен быть в диапазоне 1..100.');
      }

      const safeWorkspaceClientExtraAttributesCollapseAfterRaw = Math.round(Number(inputs.workspaceClientExtraAttributesCollapseAfter?.value));
      const safeWorkspaceClientExtraAttributesCollapseAfter = Number.isFinite(safeWorkspaceClientExtraAttributesCollapseAfterRaw)
        ? Math.min(50, Math.max(1, safeWorkspaceClientExtraAttributesCollapseAfterRaw))
        : defaults.workspace_client_extra_attributes_collapse_after;
      if (!Number.isFinite(safeWorkspaceClientExtraAttributesCollapseAfterRaw)
        || safeWorkspaceClientExtraAttributesCollapseAfterRaw < 1
        || safeWorkspaceClientExtraAttributesCollapseAfterRaw > 50) {
        errors.push('Порог сворачивания доп. атрибутов клиента должен быть в диапазоне 1..50.');
      }
      if (safeWorkspaceClientExtraAttributesCollapseAfter > safeWorkspaceClientExtraAttributesMax) {
        errors.push('Порог сворачивания не может быть больше лимита доп. атрибутов.');
      }

      const safeWorkspaceClientExtraAttributesTechnicalPrefixes = splitUniqueValues(
        inputs.workspaceClientExtraAttributesTechnicalPrefixes?.value
      );

      const safeMacroGovernanceRequireOwner = Boolean(inputs.macroGovernanceRequireOwner?.checked);
      const safeMacroGovernanceRequireNamespace = Boolean(inputs.macroGovernanceRequireNamespace?.checked);
      const safeMacroGovernanceRequireReview = Boolean(inputs.macroGovernanceRequireReview?.checked);
      const safeMacroGovernanceReviewTtlHours = sanitizeIntValue(
        inputs.macroGovernanceReviewTtlHours?.value,
        1,
        2160,
        defaults.macro_governance_review_ttl_hours
      );
      const safeMacroGovernanceDeprecationRequiresReason = Boolean(inputs.macroGovernanceDeprecationRequiresReason?.checked);
      const safeMacroGovernanceUnusedDays = sanitizeIntValue(
        inputs.macroGovernanceUnusedDays?.value,
        1,
        365,
        defaults.macro_governance_unused_days
      );
      const safeMacroGovernanceRedListEnabled = Boolean(inputs.macroGovernanceRedListEnabled?.checked);
      const safeMacroGovernanceRedListUsageMax = sanitizeIntValue(
        inputs.macroGovernanceRedListUsageMax?.value,
        0,
        10000,
        defaults.macro_governance_red_list_usage_max
      );
      const safeMacroGovernanceOwnerActionRequired = Boolean(inputs.macroGovernanceOwnerActionRequired?.checked);
      const safeMacroGovernanceCleanupCadenceDays = sanitizeIntValue(
        inputs.macroGovernanceCleanupCadenceDays?.value,
        0,
        365,
        defaults.macro_governance_cleanup_cadence_days
      );
      const safeMacroGovernanceAliasCleanupRequired = Boolean(inputs.macroGovernanceAliasCleanupRequired?.checked);
      const safeMacroGovernanceVariableCleanupRequired = Boolean(inputs.macroGovernanceVariableCleanupRequired?.checked);
      const safeMacroGovernanceUsageTierSlaRequired = Boolean(inputs.macroGovernanceUsageTierSlaRequired?.checked);
      const safeMacroGovernanceUsageTierLowMax = sanitizeIntValue(
        inputs.macroGovernanceUsageTierLowMax?.value,
        0,
        10000,
        defaults.macro_governance_usage_tier_low_max
      );
      const safeMacroGovernanceUsageTierMediumMaxRaw = sanitizeIntValue(
        inputs.macroGovernanceUsageTierMediumMax?.value,
        0,
        10000,
        defaults.macro_governance_usage_tier_medium_max
      );
      const safeMacroGovernanceUsageTierMediumMax = Math.max(
        safeMacroGovernanceUsageTierLowMax,
        safeMacroGovernanceUsageTierMediumMaxRaw
      );
      const safeMacroGovernanceCleanupSlaLowDays = sanitizeIntValue(
        inputs.macroGovernanceCleanupSlaLowDays?.value,
        1,
        365,
        defaults.macro_governance_cleanup_sla_low_days
      );
      const safeMacroGovernanceCleanupSlaMediumDays = sanitizeIntValue(
        inputs.macroGovernanceCleanupSlaMediumDays?.value,
        1,
        365,
        defaults.macro_governance_cleanup_sla_medium_days
      );
      const safeMacroGovernanceCleanupSlaHighDays = sanitizeIntValue(
        inputs.macroGovernanceCleanupSlaHighDays?.value,
        1,
        365,
        defaults.macro_governance_cleanup_sla_high_days
      );
      const safeMacroGovernanceDeprecationSlaLowDays = sanitizeIntValue(
        inputs.macroGovernanceDeprecationSlaLowDays?.value,
        1,
        365,
        defaults.macro_governance_deprecation_sla_low_days
      );
      const safeMacroGovernanceDeprecationSlaMediumDays = sanitizeIntValue(
        inputs.macroGovernanceDeprecationSlaMediumDays?.value,
        1,
        365,
        defaults.macro_governance_deprecation_sla_medium_days
      );
      const safeMacroGovernanceDeprecationSlaHighDays = sanitizeIntValue(
        inputs.macroGovernanceDeprecationSlaHighDays?.value,
        1,
        365,
        defaults.macro_governance_deprecation_sla_high_days
      );

      const safeWorkspaceContextHistoryLimitRaw = Math.round(Number(inputs.workspaceContextHistoryLimit?.value));
      const safeWorkspaceContextHistoryLimit = Number.isFinite(safeWorkspaceContextHistoryLimitRaw)
        ? Math.min(20, Math.max(1, safeWorkspaceContextHistoryLimitRaw))
        : defaults.workspace_context_history_limit;
      if (!Number.isFinite(safeWorkspaceContextHistoryLimitRaw)
        || safeWorkspaceContextHistoryLimitRaw < 1
        || safeWorkspaceContextHistoryLimitRaw > 20) {
        errors.push('Лимит блока «История обращений» должен быть в диапазоне 1..20.');
      }

      const safeWorkspaceContextRelatedEventsLimitRaw = Math.round(Number(inputs.workspaceContextRelatedEventsLimit?.value));
      const safeWorkspaceContextRelatedEventsLimit = Number.isFinite(safeWorkspaceContextRelatedEventsLimitRaw)
        ? Math.min(20, Math.max(1, safeWorkspaceContextRelatedEventsLimitRaw))
        : defaults.workspace_context_related_events_limit;
      if (!Number.isFinite(safeWorkspaceContextRelatedEventsLimitRaw)
        || safeWorkspaceContextRelatedEventsLimitRaw < 1
        || safeWorkspaceContextRelatedEventsLimitRaw > 20) {
        errors.push('Лимит блока «Связанные события» должен быть в диапазоне 1..20.');
      }

      const safeHighLifetimeThresholdRaw = Math.round(Number(inputs.workspaceSegmentHighLifetimeMinDialogs?.value));
      const safeHighLifetimeThreshold = Number.isFinite(safeHighLifetimeThresholdRaw)
        ? Math.min(500, Math.max(1, safeHighLifetimeThresholdRaw))
        : defaults.workspace_segment_high_lifetime_volume_min_dialogs;
      if (!Number.isFinite(safeHighLifetimeThresholdRaw)
        || safeHighLifetimeThresholdRaw < 1
        || safeHighLifetimeThresholdRaw > 500) {
        errors.push('Порог high_lifetime_volume должен быть в диапазоне 1..500.');
      }

      const safeMultiOpenThresholdRaw = Math.round(Number(inputs.workspaceSegmentMultiOpenMinDialogs?.value));
      const safeMultiOpenThreshold = Number.isFinite(safeMultiOpenThresholdRaw)
        ? Math.min(50, Math.max(1, safeMultiOpenThresholdRaw))
        : defaults.workspace_segment_multi_open_dialogs_min_open;
      if (!Number.isFinite(safeMultiOpenThresholdRaw)
        || safeMultiOpenThresholdRaw < 1
        || safeMultiOpenThresholdRaw > 50) {
        errors.push('Порог multi_open_dialogs должен быть в диапазоне 1..50.');
      }

      const safeReactivationMinDialogsRaw = Math.round(Number(inputs.workspaceSegmentReactivationMinDialogs?.value));
      const safeReactivationMinDialogs = Number.isFinite(safeReactivationMinDialogsRaw)
        ? Math.min(500, Math.max(1, safeReactivationMinDialogsRaw))
        : defaults.workspace_segment_reactivation_risk_min_dialogs;
      if (!Number.isFinite(safeReactivationMinDialogsRaw)
        || safeReactivationMinDialogsRaw < 1
        || safeReactivationMinDialogsRaw > 500) {
        errors.push('Порог reactivation_risk (минимум диалогов) должен быть в диапазоне 1..500.');
      }

      const safeReactivationMaxResolvedRaw = Math.round(Number(inputs.workspaceSegmentReactivationMaxResolved30d?.value));
      const safeReactivationMaxResolved = Number.isFinite(safeReactivationMaxResolvedRaw)
        ? Math.min(100, Math.max(0, safeReactivationMaxResolvedRaw))
        : defaults.workspace_segment_reactivation_risk_max_resolved_30d;
      if (!Number.isFinite(safeReactivationMaxResolvedRaw)
        || safeReactivationMaxResolvedRaw < 0
        || safeReactivationMaxResolvedRaw > 100) {
        errors.push('Порог reactivation_risk (max resolved_30d) должен быть в диапазоне 0..100.');
      }

      const safeOpenBacklogMinOpenRaw = Math.round(Number(inputs.workspaceSegmentOpenBacklogMinOpen?.value));
      const safeOpenBacklogMinOpen = Number.isFinite(safeOpenBacklogMinOpenRaw)
        ? Math.min(100, Math.max(1, safeOpenBacklogMinOpenRaw))
        : defaults.workspace_segment_open_backlog_min_open;
      if (!Number.isFinite(safeOpenBacklogMinOpenRaw)
        || safeOpenBacklogMinOpenRaw < 1
        || safeOpenBacklogMinOpenRaw > 100) {
        errors.push('Порог open_backlog_pressure (open-диалогов) должен быть в диапазоне 1..100.');
      }

      const safeOpenBacklogMinShareRaw = Math.round(Number(inputs.workspaceSegmentOpenBacklogMinSharePercent?.value));
      const safeOpenBacklogMinShare = Number.isFinite(safeOpenBacklogMinShareRaw)
        ? Math.min(100, Math.max(1, safeOpenBacklogMinShareRaw))
        : defaults.workspace_segment_open_backlog_min_share_percent;
      if (!Number.isFinite(safeOpenBacklogMinShareRaw)
        || safeOpenBacklogMinShareRaw < 1
        || safeOpenBacklogMinShareRaw > 100) {
        errors.push('Порог open_backlog_pressure (доля open, %) должен быть в диапазоне 1..100.');
      }

      return {
        errors,
        config: {
          dialog_macro_variable_defaults: safeMacroVariableDefaults,
          dialog_macro_variable_catalog: safeMacroVariableCatalog,
          dialog_macro_variable_catalog_external_url: safeMacroVariableCatalogExternalUrl,
          dialog_macro_variable_catalog_external_timeout_ms: safeMacroVariableCatalogExternalTimeoutMs,
          dialog_macro_variable_catalog_external_cache_ttl_seconds: safeMacroVariableCatalogExternalCacheTtlSeconds,
          dialog_macro_variable_catalog_external_auth_header: safeMacroCatalogExternalAuthHeader,
          dialog_macro_variable_catalog_external_auth_token: safeMacroCatalogExternalAuthToken,
          dialog_macro_publish_allowed_roles: safeMacroPublishAllowedRoles,
          dialog_macro_require_independent_review: Boolean(inputs.macroRequireIndependentReview?.checked),
          dialog_macro_governance_require_owner: safeMacroGovernanceRequireOwner,
          dialog_macro_governance_require_namespace: safeMacroGovernanceRequireNamespace,
          dialog_macro_governance_require_review: safeMacroGovernanceRequireReview,
          dialog_macro_governance_review_ttl_hours: safeMacroGovernanceReviewTtlHours,
          dialog_macro_governance_deprecation_requires_reason: safeMacroGovernanceDeprecationRequiresReason,
          dialog_macro_governance_unused_days: safeMacroGovernanceUnusedDays,
          dialog_macro_governance_red_list_enabled: safeMacroGovernanceRedListEnabled,
          dialog_macro_governance_red_list_usage_max: safeMacroGovernanceRedListUsageMax,
          dialog_macro_governance_owner_action_required: safeMacroGovernanceOwnerActionRequired,
          dialog_macro_governance_cleanup_cadence_days: safeMacroGovernanceCleanupCadenceDays,
          dialog_macro_governance_alias_cleanup_required: safeMacroGovernanceAliasCleanupRequired,
          dialog_macro_governance_variable_cleanup_required: safeMacroGovernanceVariableCleanupRequired,
          dialog_macro_governance_usage_tier_sla_required: safeMacroGovernanceUsageTierSlaRequired,
          dialog_macro_governance_usage_tier_low_max: safeMacroGovernanceUsageTierLowMax,
          dialog_macro_governance_usage_tier_medium_max: safeMacroGovernanceUsageTierMediumMax,
          dialog_macro_governance_cleanup_sla_low_days: safeMacroGovernanceCleanupSlaLowDays,
          dialog_macro_governance_cleanup_sla_medium_days: safeMacroGovernanceCleanupSlaMediumDays,
          dialog_macro_governance_cleanup_sla_high_days: safeMacroGovernanceCleanupSlaHighDays,
          dialog_macro_governance_deprecation_sla_low_days: safeMacroGovernanceDeprecationSlaLowDays,
          dialog_macro_governance_deprecation_sla_medium_days: safeMacroGovernanceDeprecationSlaMediumDays,
          dialog_macro_governance_deprecation_sla_high_days: safeMacroGovernanceDeprecationSlaHighDays,
          dialog_workspace_client_crm_profile_url_template: safeCrmProfileTemplate,
          dialog_workspace_client_crm_profile_label: safeCrmProfileLabel,
          dialog_workspace_client_contract_profile_url_template: safeContractProfileTemplate,
          dialog_workspace_client_contract_profile_label: safeContractProfileLabel,
          dialog_workspace_client_external_links: safeWorkspaceExternalLinks,
          dialog_workspace_client_external_profile_url: safeWorkspaceExternalProfileUrl,
          dialog_workspace_client_external_profile_timeout_ms: safeWorkspaceExternalProfileTimeoutMs,
          dialog_workspace_client_external_profile_cache_ttl_seconds: safeWorkspaceExternalProfileCacheTtlSeconds,
          dialog_workspace_client_external_profile_auth_header: safeWorkspaceExternalProfileAuthHeader,
          dialog_workspace_client_external_profile_auth_token: safeWorkspaceExternalProfileAuthToken,
          dialog_workspace_required_client_attributes: safeWorkspaceRequiredClientAttributes,
          dialog_workspace_required_client_attributes_by_segment: safeWorkspaceRequiredClientAttributesBySegment,
          dialog_workspace_client_context_required_sources: safeWorkspaceClientContextRequiredSources,
          dialog_workspace_client_context_source_priority: safeWorkspaceClientContextSourcePriority,
          dialog_workspace_client_context_source_stale_after_hours: safeWorkspaceClientContextSourceStaleAfterHours,
          dialog_workspace_client_context_source_labels: safeWorkspaceClientContextSourceLabels,
          dialog_workspace_client_context_source_updated_at_attributes: safeWorkspaceClientContextSourceUpdatedAtAttributes,
          dialog_workspace_client_context_source_stale_after_hours_by_source: safeWorkspaceClientContextSourceStaleAfterHoursBySource,
          dialog_workspace_client_hidden_attributes: safeWorkspaceClientHiddenAttributes,
          dialog_workspace_client_attribute_labels: safeWorkspaceClientAttributeLabels,
          dialog_workspace_client_attribute_order: safeWorkspaceClientAttributeOrder,
          dialog_workspace_client_extra_attributes_max: safeWorkspaceClientExtraAttributesMax,
          dialog_workspace_client_extra_attributes_collapse_after: safeWorkspaceClientExtraAttributesCollapseAfter,
          dialog_workspace_client_extra_attributes_hide_technical: Boolean(inputs.workspaceClientExtraAttributesHideTechnical?.checked),
          dialog_workspace_client_extra_attributes_technical_prefixes: safeWorkspaceClientExtraAttributesTechnicalPrefixes,
          dialog_workspace_context_history_limit: safeWorkspaceContextHistoryLimit,
          dialog_workspace_context_related_events_limit: safeWorkspaceContextRelatedEventsLimit,
          dialog_workspace_segment_high_lifetime_volume_min_dialogs: safeHighLifetimeThreshold,
          dialog_workspace_segment_multi_open_dialogs_min_open: safeMultiOpenThreshold,
          dialog_workspace_segment_reactivation_risk_min_dialogs: safeReactivationMinDialogs,
          dialog_workspace_segment_reactivation_risk_max_resolved_30d: safeReactivationMaxResolved,
          dialog_workspace_segment_open_backlog_min_open: safeOpenBacklogMinOpen,
          dialog_workspace_segment_open_backlog_min_share_percent: safeOpenBacklogMinShare,
        },
      };
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
      collectMacroClientContextConfig,
      collectSlaCoreConfig,
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

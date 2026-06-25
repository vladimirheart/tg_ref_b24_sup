(function () {
  if (window.SettingsDialogWorkspaceGovernanceRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const UTC_TIMESTAMP_FIELDS = [
      {
        inputId: 'dialogWorkspaceGovernanceOwnerSignoffAt',
        statusId: 'dialogWorkspaceGovernanceOwnerSignoffAtStatus',
        configKey: 'workspace_rollout_governance_owner_signoff_at',
        label: 'Owner sign-off',
      },
      {
        inputId: 'dialogWorkspaceGovernanceReviewedAt',
        statusId: 'dialogWorkspaceGovernanceReviewedAtStatus',
        configKey: 'workspace_rollout_governance_reviewed_at',
        label: 'Governance review',
      },
    ];

    function getDialogConfig() {
      const value = typeof options.getDialogConfig === 'function' ? options.getDialogConfig() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getDefaultDialogSlaConfig() {
      const value = typeof options.getDefaultDialogSlaConfig === 'function'
        ? options.getDefaultDialogSlaConfig()
        : null;
      return value && typeof value === 'object'
        ? value
        : {
            workspace_rollout_governance_owner_signoff_ttl_hours: 168,
            workspace_rollout_governance_review_cadence_days: 0,
            workspace_rollout_governance_parity_exit_days: 0,
            workspace_rollout_governance_parity_critical_reasons: [],
            workspace_rollout_governance_legacy_blocked_reasons_top_n: 3,
          };
    }

    function getInputs() {
      return {
        packetRequired: document.getElementById('dialogWorkspaceGovernancePacketRequired'),
        ownerSignoffRequired: document.getElementById('dialogWorkspaceGovernanceOwnerSignoffRequired'),
        ownerSignoffBy: document.getElementById('dialogWorkspaceGovernanceOwnerSignoffBy'),
        ownerSignoffAt: document.getElementById('dialogWorkspaceGovernanceOwnerSignoffAt'),
        ownerSignoffTtlHours: document.getElementById('dialogWorkspaceGovernanceOwnerSignoffTtlHours'),
        reviewCadenceDays: document.getElementById('dialogWorkspaceGovernanceReviewCadenceDays'),
        reviewedBy: document.getElementById('dialogWorkspaceGovernanceReviewedBy'),
        reviewedAt: document.getElementById('dialogWorkspaceGovernanceReviewedAt'),
        reviewDecisionRequired: document.getElementById('dialogWorkspaceGovernanceReviewDecisionRequired'),
        incidentFollowupRequired: document.getElementById('dialogWorkspaceGovernanceIncidentFollowupRequired'),
        followupForNonGoRequired: document.getElementById('dialogWorkspaceGovernanceFollowupForNonGoRequired'),
        reviewDecisionAction: document.getElementById('dialogWorkspaceGovernanceReviewDecisionAction'),
        reviewIncidentFollowup: document.getElementById('dialogWorkspaceGovernanceReviewIncidentFollowup'),
        parityExitDays: document.getElementById('dialogWorkspaceGovernanceParityExitDays'),
        parityCriticalReasons: document.getElementById('dialogWorkspaceGovernanceParityCriticalReasons'),
        legacyOnlyScenarios: document.getElementById('dialogWorkspaceGovernanceLegacyOnlyScenarios'),
        legacyBlockedReasonsReviewRequired: document.getElementById('dialogWorkspaceGovernanceLegacyBlockedReasonsReviewRequired'),
        legacyBlockedReasonsTopN: document.getElementById('dialogWorkspaceGovernanceLegacyBlockedReasonsTopN'),
        legacyUsageMinWorkspaceOpens: document.getElementById('dialogWorkspaceLegacyUsageMinWorkspaceOpens'),
        utcSummary: document.getElementById('dialogWorkspaceGovernanceUtcSummary'),
      };
    }

    function parseUtcDateInput(rawValue) {
      const value = String(rawValue || '').trim();
      if (!value) {
        return null;
      }
      const utcLocalMatch = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/);
      if (utcLocalMatch) {
        const [, year, month, day, hour, minute, second = '00', fraction = ''] = utcLocalMatch;
        const milliseconds = Number((fraction + '000').slice(0, 3));
        const utcDate = new Date(Date.UTC(
          Number(year),
          Number(month) - 1,
          Number(day),
          Number(hour),
          Number(minute),
          Number(second),
          milliseconds,
        ));
        return Number.isNaN(utcDate.getTime()) ? null : utcDate;
      }
      const parsed = new Date(value);
      return Number.isNaN(parsed.getTime()) ? null : parsed;
    }

    function toUtcIsoString(rawValue) {
      const parsed = parseUtcDateInput(rawValue);
      return parsed ? parsed.toISOString() : '';
    }

    function toDateTimeLocalValue(rawValue) {
      const parsed = parseUtcDateInput(rawValue);
      if (!parsed) {
        return '';
      }
      const year = String(parsed.getUTCFullYear()).padStart(4, '0');
      const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
      const day = String(parsed.getUTCDate()).padStart(2, '0');
      const hours = String(parsed.getUTCHours()).padStart(2, '0');
      const minutes = String(parsed.getUTCMinutes()).padStart(2, '0');
      return `${year}-${month}-${day}T${hours}:${minutes}`;
    }

    function syncUtcTimestampFieldPresentation(input, rawValue, field) {
      if (!(input instanceof HTMLInputElement)) {
        return;
      }
      const status = document.getElementById(field.statusId);
      const value = String(rawValue || '').trim();
      const parsed = parseUtcDateInput(value);

      input.value = parsed ? toDateTimeLocalValue(value) : '';
      input.classList.toggle('is-invalid', Boolean(value) && !parsed);

      if (!status) {
        return;
      }

      status.classList.remove('is-invalid', 'is-ready');
      if (!value) {
        status.textContent = `${field.label}: пусто → сохранится как пустое значение.`;
        return;
      }
      if (!parsed) {
        status.textContent = `${field.label}: сохранённое значение не распознано как UTC timestamp; analytics покажет invalid_utc до исправления.`;
        status.classList.add('is-invalid');
        return;
      }
      status.textContent = `${field.label}: UTC ${parsed.toISOString()}.`;
      status.classList.add('is-ready');
    }

    function syncUtcTimestampSummary() {
      const inputs = getInputs();
      if (!inputs.utcSummary) {
        return;
      }
      const states = UTC_TIMESTAMP_FIELDS.map((field) => {
        const input = document.getElementById(field.inputId);
        const value = String(input?.value || '').trim();
        const parsed = parseUtcDateInput(value);
        return {
          label: field.label,
          empty: !value,
          invalid: Boolean(value) && !parsed,
          valid: Boolean(parsed),
        };
      });
      const invalid = states.filter((state) => state.invalid);
      const empty = states.filter((state) => state.empty);
      const valid = states.filter((state) => state.valid);
      const invalidLabels = invalid.map((state) => state.label).join(', ');
      const emptyLabels = empty.map((state) => state.label).join(', ');

      inputs.utcSummary.className = 'alert py-2 px-3 mb-1';
      if (invalid.length > 0) {
        inputs.utcSummary.classList.add('alert-warning');
        inputs.utcSummary.textContent = `UTC timestamps: ${valid.length}/${states.length} валидны, ${invalid.length} invalid (${invalidLabels}), ${empty.length} пусто${emptyLabels ? `: ${emptyLabels}` : ''}. Невалидные значения сохраняются как есть для аналитики и дают invalid_utc.`;
        return;
      }
      if (empty.length > 0) {
        inputs.utcSummary.classList.add('alert-secondary');
        inputs.utcSummary.textContent = `UTC timestamps: ${valid.length}/${states.length} валидны, ${empty.length} пусто${emptyLabels ? `: ${emptyLabels}` : ''}. Пустые значения не меняют поведение, пока соответствующий gate выключен.`;
        return;
      }
      inputs.utcSummary.classList.add('alert-success');
      inputs.utcSummary.textContent = `UTC timestamps: все ${states.length} значения валидны и будут сохранены в ISO UTC без timezone-дрейфа.`;
    }

    function syncAllUtcTimestampFields() {
      UTC_TIMESTAMP_FIELDS.forEach((field) => {
        const input = document.getElementById(field.inputId);
        syncUtcTimestampFieldPresentation(input, input?.value || '', field);
      });
      syncUtcTimestampSummary();
    }

    function populateUtcTimestampField(field) {
      const input = document.getElementById(field.inputId);
      if (!(input instanceof HTMLInputElement)) {
        return;
      }
      const raw = String(getDialogConfig()[field.configKey] || '').trim();
      syncUtcTimestampFieldPresentation(input, raw, field);
    }

    function initUtcTimestampFields() {
      UTC_TIMESTAMP_FIELDS.forEach((field) => {
        const input = document.getElementById(field.inputId);
        if (!(input instanceof HTMLInputElement)) {
          return;
        }
        const sync = () => {
          syncUtcTimestampFieldPresentation(input, input.value, field);
          syncUtcTimestampSummary();
        };
        input.addEventListener('input', sync);
        input.addEventListener('change', sync);
        sync();
      });
    }

    function initWorkspaceGovernanceControls() {
      const inputs = getInputs();
      const config = getDialogConfig();
      const defaults = getDefaultDialogSlaConfig();

      if (inputs.packetRequired) {
        inputs.packetRequired.checked = Boolean(config.workspace_rollout_governance_packet_required);
      }
      if (inputs.ownerSignoffRequired) {
        inputs.ownerSignoffRequired.checked = Boolean(config.workspace_rollout_governance_owner_signoff_required);
      }
      if (inputs.ownerSignoffBy) {
        inputs.ownerSignoffBy.value = String(config.workspace_rollout_governance_owner_signoff_by || '').trim();
      }
      populateUtcTimestampField(UTC_TIMESTAMP_FIELDS[0]);
      if (inputs.ownerSignoffTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_governance_owner_signoff_ttl_hours, 10);
        inputs.ownerSignoffTtlHours.value = Number.isFinite(value) && value >= 1 && value <= 2160
          ? value
          : defaults.workspace_rollout_governance_owner_signoff_ttl_hours;
      }
      if (inputs.reviewCadenceDays) {
        const value = Number.parseInt(config.workspace_rollout_governance_review_cadence_days, 10);
        inputs.reviewCadenceDays.value = Number.isFinite(value) && value >= 0 && value <= 90
          ? value
          : defaults.workspace_rollout_governance_review_cadence_days;
      }
      if (inputs.reviewedBy) {
        inputs.reviewedBy.value = String(config.workspace_rollout_governance_reviewed_by || '').trim();
      }
      if (inputs.reviewDecisionRequired) {
        inputs.reviewDecisionRequired.checked = Boolean(config.workspace_rollout_governance_review_decision_required);
      }
      if (inputs.incidentFollowupRequired) {
        inputs.incidentFollowupRequired.checked = Boolean(config.workspace_rollout_governance_incident_followup_required);
      }
      if (inputs.followupForNonGoRequired) {
        inputs.followupForNonGoRequired.checked = Boolean(config.workspace_rollout_governance_followup_for_non_go_required);
      }
      if (inputs.reviewDecisionAction) {
        const decisionAction = String(config.workspace_rollout_governance_review_decision_action || '').trim().toLowerCase();
        inputs.reviewDecisionAction.value = ['go', 'hold', 'rollback'].includes(decisionAction)
          ? decisionAction
          : '';
      }
      if (inputs.reviewIncidentFollowup) {
        inputs.reviewIncidentFollowup.value = String(config.workspace_rollout_governance_review_incident_followup || '').trim();
      }
      populateUtcTimestampField(UTC_TIMESTAMP_FIELDS[1]);
      if (inputs.parityExitDays) {
        const value = Number.parseInt(config.workspace_rollout_governance_parity_exit_days, 10);
        inputs.parityExitDays.value = Number.isFinite(value) && value >= 0 && value <= 90
          ? value
          : defaults.workspace_rollout_governance_parity_exit_days;
      }
      if (inputs.parityCriticalReasons) {
        const values = Array.isArray(config.workspace_rollout_governance_parity_critical_reasons)
          ? config.workspace_rollout_governance_parity_critical_reasons
          : defaults.workspace_rollout_governance_parity_critical_reasons;
        inputs.parityCriticalReasons.value = values
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean)
          .join(', ');
      }
      if (inputs.legacyOnlyScenarios) {
        const values = config.workspace_rollout_governance_legacy_only_scenarios;
        if (Array.isArray(values)) {
          inputs.legacyOnlyScenarios.value = JSON.stringify(values, null, 2);
        } else if (typeof values === 'string' && values.trim()) {
          inputs.legacyOnlyScenarios.value = values.trim();
        } else {
          inputs.legacyOnlyScenarios.value = '[]';
        }
      }
      if (inputs.legacyBlockedReasonsReviewRequired) {
        inputs.legacyBlockedReasonsReviewRequired.checked = Boolean(config.workspace_rollout_governance_legacy_blocked_reasons_review_required);
      }
      if (inputs.legacyBlockedReasonsTopN) {
        const value = Number.parseInt(config.workspace_rollout_governance_legacy_blocked_reasons_top_n, 10);
        inputs.legacyBlockedReasonsTopN.value = Number.isFinite(value) && value >= 1 && value <= 10
          ? value
          : defaults.workspace_rollout_governance_legacy_blocked_reasons_top_n;
      }
      if (inputs.legacyUsageMinWorkspaceOpens) {
        const value = Number.parseInt(config.workspace_rollout_governance_legacy_usage_min_workspace_open_events, 10);
        inputs.legacyUsageMinWorkspaceOpens.value = Number.isFinite(value) && value >= 0 && value <= 100000 ? value : '';
      }

      syncAllUtcTimestampFields();
    }

    function collectWorkspaceGovernanceConfig() {
      const inputs = getInputs();
      const defaults = getDefaultDialogSlaConfig();
      const errors = [];

      const safePacketRequired = Boolean(inputs.packetRequired?.checked);
      const safeOwnerSignoffRequired = Boolean(inputs.ownerSignoffRequired?.checked);
      const safeOwnerSignoffBy = String(inputs.ownerSignoffBy?.value || '').trim().slice(0, 120);
      const safeOwnerSignoffAtRaw = String(inputs.ownerSignoffAt?.value || '').trim();
      const safeOwnerSignoffAt = toUtcIsoString(safeOwnerSignoffAtRaw);
      if (safeOwnerSignoffAtRaw && !safeOwnerSignoffAt) {
        errors.push('Дата owner sign-off указана в некорректном формате UTC.');
      }

      const safeOwnerSignoffTtlHoursRaw = Number.parseInt(inputs.ownerSignoffTtlHours?.value || '', 10);
      const safeOwnerSignoffTtlHours = Number.isFinite(safeOwnerSignoffTtlHoursRaw)
        ? Math.max(1, Math.min(2160, safeOwnerSignoffTtlHoursRaw))
        : defaults.workspace_rollout_governance_owner_signoff_ttl_hours;
      if (safeOwnerSignoffRequired && !safeOwnerSignoffBy) {
        errors.push('Для owner sign-off укажите ответственного владельца решения.');
      }
      if (safeOwnerSignoffRequired && !safeOwnerSignoffAt) {
        errors.push('Для owner sign-off укажите валидную UTC-дату подтверждения.');
      }

      const safeReviewCadenceDaysRaw = Number.parseInt(inputs.reviewCadenceDays?.value || '', 10);
      const safeReviewCadenceDays = Number.isFinite(safeReviewCadenceDaysRaw)
        ? Math.max(0, Math.min(90, safeReviewCadenceDaysRaw))
        : defaults.workspace_rollout_governance_review_cadence_days;
      const safeReviewedBy = String(inputs.reviewedBy?.value || '').trim().slice(0, 120);
      const safeReviewedAtRaw = String(inputs.reviewedAt?.value || '').trim();
      const safeReviewedAt = toUtcIsoString(safeReviewedAtRaw);
      if (safeReviewedAtRaw && !safeReviewedAt) {
        errors.push('Дата governance review указана в некорректном формате UTC.');
      }

      const safeReviewDecisionRequired = Boolean(inputs.reviewDecisionRequired?.checked);
      const safeIncidentFollowupRequired = Boolean(inputs.incidentFollowupRequired?.checked);
      const safeFollowupForNonGoRequired = Boolean(inputs.followupForNonGoRequired?.checked);
      const safeReviewDecisionAction = (() => {
        const value = String(inputs.reviewDecisionAction?.value || '').trim().toLowerCase();
        return ['go', 'hold', 'rollback'].includes(value) ? value : '';
      })();
      const safeReviewIncidentFollowup = String(inputs.reviewIncidentFollowup?.value || '').trim().slice(0, 500);

      if (safeReviewCadenceDays > 0 && !safeReviewedBy) {
        errors.push('Для review cadence укажите последнего reviewer.');
      }
      if (safeReviewCadenceDays > 0 && !safeReviewedAt) {
        errors.push('Для review cadence укажите валидную UTC-дату последнего review.');
      }
      if (safeReviewDecisionRequired && !safeReviewDecisionAction) {
        errors.push('При включенном decision action required выберите go / hold / rollback.');
      }
      if (safeIncidentFollowupRequired && !safeReviewIncidentFollowup) {
        errors.push('При включенном incident follow-up required укажите ссылку или заметку follow-up.');
      }
      if (safeFollowupForNonGoRequired
        && safeReviewDecisionAction === 'go'
        && !safeReviewIncidentFollowup) {
        errors.push('Для перехода hold/rollback → go укажите incident follow-up ссылку или заметку.');
      }

      const safeParityExitDaysRaw = Number.parseInt(inputs.parityExitDays?.value || '', 10);
      const safeParityExitDays = Number.isFinite(safeParityExitDaysRaw)
        ? Math.max(0, Math.min(90, safeParityExitDaysRaw))
        : defaults.workspace_rollout_governance_parity_exit_days;
      const safeParityCriticalReasons = Array.from(new Set(
        String(inputs.parityCriticalReasons?.value || '')
          .split(/[\n,;]/)
          .map((value) => String(value || '').trim().toLowerCase())
          .filter(Boolean),
      ));

      const safeLegacyBlockedReasonsReviewRequired = Boolean(inputs.legacyBlockedReasonsReviewRequired?.checked);
      const safeLegacyBlockedReasonsTopNRaw = Number.parseInt(inputs.legacyBlockedReasonsTopN?.value || '', 10);
      const safeLegacyBlockedReasonsTopN = Number.isFinite(safeLegacyBlockedReasonsTopNRaw)
        ? Math.max(1, Math.min(10, safeLegacyBlockedReasonsTopNRaw))
        : defaults.workspace_rollout_governance_legacy_blocked_reasons_top_n;
      if (safeLegacyBlockedReasonsReviewRequired
        && (!Number.isFinite(safeLegacyBlockedReasonsTopNRaw)
          || safeLegacyBlockedReasonsTopNRaw < 1
          || safeLegacyBlockedReasonsTopNRaw > 10)) {
        errors.push('Top blocked reasons для review должен быть целым числом 1..10.');
      }

      let safeLegacyOnlyScenarios = [];
      const legacyOnlyScenariosRaw = String(inputs.legacyOnlyScenarios?.value || '').trim();
      if (legacyOnlyScenariosRaw) {
        if (legacyOnlyScenariosRaw.startsWith('[')) {
          try {
            const parsed = JSON.parse(legacyOnlyScenariosRaw);
            if (!Array.isArray(parsed)) {
              errors.push('Legacy-only scenarios должны быть JSON-массивом или CSV-строкой.');
            } else {
              safeLegacyOnlyScenarios = Array.from(new Set(parsed
                .map((value) => String(value || '').trim())
                .filter(Boolean)));
            }
          } catch (err) {
            errors.push('Legacy-only scenarios: некорректный JSON.');
          }
        } else {
          safeLegacyOnlyScenarios = Array.from(new Set(legacyOnlyScenariosRaw
            .split(/[\n,;]/)
            .map((value) => String(value || '').trim())
            .filter(Boolean)));
        }
      }

      const safeLegacyUsageMinWorkspaceOpensRaw = Number.parseInt(inputs.legacyUsageMinWorkspaceOpens?.value || '', 10);
      const safeLegacyUsageMinWorkspaceOpens = Number.isFinite(safeLegacyUsageMinWorkspaceOpensRaw)
        ? Math.max(0, Math.min(100000, safeLegacyUsageMinWorkspaceOpensRaw))
        : '';
      if (String(inputs.legacyUsageMinWorkspaceOpens?.value || '').trim()
        && (!Number.isFinite(safeLegacyUsageMinWorkspaceOpensRaw)
          || safeLegacyUsageMinWorkspaceOpensRaw < 0
          || safeLegacyUsageMinWorkspaceOpensRaw > 100000)) {
        errors.push('Мин. workspace opens в окне должен быть целым числом 0..100000.');
      }

      syncAllUtcTimestampFields();

      return {
        errors,
        config: {
          dialog_workspace_rollout_governance_packet_required: safePacketRequired,
          dialog_workspace_rollout_governance_owner_signoff_required: safeOwnerSignoffRequired,
          dialog_workspace_rollout_governance_owner_signoff_by: safeOwnerSignoffBy,
          dialog_workspace_rollout_governance_owner_signoff_at: safeOwnerSignoffAt,
          dialog_workspace_rollout_governance_owner_signoff_ttl_hours: safeOwnerSignoffTtlHours,
          dialog_workspace_rollout_governance_review_cadence_days: safeReviewCadenceDays,
          dialog_workspace_rollout_governance_reviewed_by: safeReviewedBy,
          dialog_workspace_rollout_governance_reviewed_at: safeReviewedAt,
          dialog_workspace_rollout_governance_review_decision_required: safeReviewDecisionRequired,
          dialog_workspace_rollout_governance_incident_followup_required: safeIncidentFollowupRequired,
          dialog_workspace_rollout_governance_followup_for_non_go_required: safeFollowupForNonGoRequired,
          dialog_workspace_rollout_governance_review_decision_action: safeReviewDecisionAction,
          dialog_workspace_rollout_governance_review_incident_followup: safeReviewIncidentFollowup,
          dialog_workspace_rollout_governance_parity_exit_days: safeParityExitDays,
          dialog_workspace_rollout_governance_parity_critical_reasons: safeParityCriticalReasons,
          dialog_workspace_rollout_governance_legacy_only_scenarios: safeLegacyOnlyScenarios,
          dialog_workspace_rollout_governance_legacy_usage_min_workspace_open_events: safeLegacyUsageMinWorkspaceOpens,
          dialog_workspace_rollout_governance_legacy_blocked_reasons_review_required: safeLegacyBlockedReasonsReviewRequired,
          dialog_workspace_rollout_governance_legacy_blocked_reasons_top_n: safeLegacyBlockedReasonsTopN,
        },
      };
    }

    function initialize() {
      initWorkspaceGovernanceControls();
      initUtcTimestampFields();
    }

    return {
      collectWorkspaceGovernanceConfig,
      initialize,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogWorkspaceGovernanceRuntime) {
        return window.__settingsDialogWorkspaceGovernanceRuntime;
      }
      const runtime = createRuntime(options);
      runtime.initialize();
      window.__settingsDialogWorkspaceGovernanceRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogWorkspaceGovernanceRuntime = Object.freeze(api);
}());

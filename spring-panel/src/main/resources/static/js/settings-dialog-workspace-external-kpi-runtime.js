(function () {
  if (window.SettingsDialogWorkspaceExternalKpiRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS = [
      {
        inputId: 'dialogWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt',
        statusId: 'dialogWorkspaceExternalKpiDatamartDependencyTicketUpdatedAtStatus',
        configKey: 'workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at',
        label: 'Dependency-ticket',
      },
      {
        inputId: 'dialogWorkspaceExternalKpiReviewedAt',
        statusId: 'dialogWorkspaceExternalKpiReviewedAtStatus',
        configKey: 'workspace_rollout_external_kpi_reviewed_at',
        label: 'Review checkpoint',
      },
      {
        inputId: 'dialogWorkspaceExternalKpiDataUpdatedAt',
        statusId: 'dialogWorkspaceExternalKpiDataUpdatedAtStatus',
        configKey: 'workspace_rollout_external_kpi_data_updated_at',
        label: 'Data-mart freshness',
      },
      {
        inputId: 'dialogWorkspaceExternalKpiDatamartHealthUpdatedAt',
        statusId: 'dialogWorkspaceExternalKpiDatamartHealthUpdatedAtStatus',
        configKey: 'workspace_rollout_external_kpi_datamart_health_updated_at',
        label: 'Health signal',
      },
      {
        inputId: 'dialogWorkspaceExternalKpiDatamartProgramUpdatedAt',
        statusId: 'dialogWorkspaceExternalKpiDatamartProgramUpdatedAtStatus',
        configKey: 'workspace_rollout_external_kpi_datamart_program_updated_at',
        label: 'Program status',
      },
      {
        inputId: 'dialogWorkspaceExternalKpiDatamartTargetReadyAt',
        statusId: 'dialogWorkspaceExternalKpiDatamartTargetReadyAtStatus',
        configKey: 'workspace_rollout_external_kpi_datamart_target_ready_at',
        label: 'Target deadline',
      },
    ];

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      if (value === null || value === undefined) {
        return '';
      }
      return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    }

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
            cross_product_omnichannel_dashboard_label: 'Omni-channel KPI dashboard',
            cross_product_finance_dashboard_label: 'Финансовый KPI dashboard',
            workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours: 336,
            workspace_rollout_external_kpi_review_ttl_hours: 168,
            workspace_rollout_external_kpi_data_freshness_ttl_hours: 48,
            workspace_rollout_external_kpi_datamart_health_status: 'unknown',
            workspace_rollout_external_kpi_datamart_health_ttl_hours: 48,
            workspace_rollout_external_kpi_datamart_program_status: 'unknown',
            workspace_rollout_external_kpi_datamart_program_ttl_hours: 336,
            workspace_rollout_external_kpi_datamart_timeline_grace_hours: 24,
            workspace_rollout_external_kpi_datamart_contract_version: 'v1',
            workspace_rollout_external_kpi_datamart_contract_mandatory_fields: 'frt,ttr,sla_breach,cost_per_contact',
            workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct: 80,
          };
    }

    function getInputs() {
      return {
        crossProductOmnichannelDashboardUrl: document.getElementById('dialogCrossProductOmnichannelDashboardUrl'),
        crossProductOmnichannelDashboardLabel: document.getElementById('dialogCrossProductOmnichannelDashboardLabel'),
        crossProductFinanceDashboardUrl: document.getElementById('dialogCrossProductFinanceDashboardUrl'),
        crossProductFinanceDashboardLabel: document.getElementById('dialogCrossProductFinanceDashboardLabel'),
        workspaceExternalKpiGateEnabled: document.getElementById('dialogWorkspaceExternalKpiGateEnabled'),
        workspaceExternalKpiOmnichannelReady: document.getElementById('dialogWorkspaceExternalKpiOmnichannelReady'),
        workspaceExternalKpiFinanceReady: document.getElementById('dialogWorkspaceExternalKpiFinanceReady'),
        workspaceExternalKpiNote: document.getElementById('dialogWorkspaceExternalKpiNote'),
        workspaceExternalKpiDatamartOwner: document.getElementById('dialogWorkspaceExternalKpiDatamartOwner'),
        workspaceExternalKpiDatamartRunbookUrl: document.getElementById('dialogWorkspaceExternalKpiDatamartRunbookUrl'),
        workspaceExternalKpiDatamartDependencyTicketRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketRequired'),
        workspaceExternalKpiDatamartDependencyTicketUrl: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketUrl'),
        workspaceExternalKpiDatamartDependencyTicketOwnerRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketOwnerRequired'),
        workspaceExternalKpiDatamartDependencyTicketOwner: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketOwner'),
        workspaceExternalKpiDatamartDependencyTicketOwnerContactRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketOwnerContactRequired'),
        workspaceExternalKpiDatamartDependencyTicketOwnerContact: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketOwnerContact'),
        workspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired'),
        workspaceExternalKpiDatamartDependencyTicketFreshnessRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketFreshnessRequired'),
        workspaceExternalKpiDatamartDependencyTicketUpdatedAt: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt'),
        workspaceExternalKpiDatamartDependencyTicketTtlHours: document.getElementById('dialogWorkspaceExternalKpiDatamartDependencyTicketTtlHours'),
        workspaceExternalKpiReviewedBy: document.getElementById('dialogWorkspaceExternalKpiReviewedBy'),
        workspaceExternalKpiReviewedAt: document.getElementById('dialogWorkspaceExternalKpiReviewedAt'),
        workspaceExternalKpiReviewTtlHours: document.getElementById('dialogWorkspaceExternalKpiReviewTtlHours'),
        workspaceExternalKpiDataFreshnessRequired: document.getElementById('dialogWorkspaceExternalKpiDataFreshnessRequired'),
        workspaceExternalKpiDataUpdatedAt: document.getElementById('dialogWorkspaceExternalKpiDataUpdatedAt'),
        workspaceExternalKpiDataFreshnessTtlHours: document.getElementById('dialogWorkspaceExternalKpiDataFreshnessTtlHours'),
        workspaceExternalKpiDashboardLinksRequired: document.getElementById('dialogWorkspaceExternalKpiDashboardLinksRequired'),
        workspaceExternalKpiDashboardStatusRequired: document.getElementById('dialogWorkspaceExternalKpiDashboardStatusRequired'),
        workspaceExternalKpiDashboardStatus: document.getElementById('dialogWorkspaceExternalKpiDashboardStatus'),
        workspaceExternalKpiDashboardStatusNote: document.getElementById('dialogWorkspaceExternalKpiDashboardStatusNote'),
        workspaceExternalKpiOwnerRunbookRequired: document.getElementById('dialogWorkspaceExternalKpiOwnerRunbookRequired'),
        workspaceExternalKpiDatamartHealthRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthRequired'),
        workspaceExternalKpiDatamartHealthStatus: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthStatus'),
        workspaceExternalKpiDatamartHealthNote: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthNote'),
        workspaceExternalKpiDatamartHealthFreshnessRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthFreshnessRequired'),
        workspaceExternalKpiDatamartHealthUpdatedAt: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthUpdatedAt'),
        workspaceExternalKpiDatamartHealthTtlHours: document.getElementById('dialogWorkspaceExternalKpiDatamartHealthTtlHours'),
        workspaceExternalKpiDatamartProgramBlockerRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramBlockerRequired'),
        workspaceExternalKpiDatamartProgramStatus: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramStatus'),
        workspaceExternalKpiDatamartProgramNote: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramNote'),
        workspaceExternalKpiDatamartProgramBlockerUrl: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramBlockerUrl'),
        workspaceExternalKpiDatamartProgramFreshnessRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramFreshnessRequired'),
        workspaceExternalKpiDatamartProgramUpdatedAt: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramUpdatedAt'),
        workspaceExternalKpiDatamartProgramTtlHours: document.getElementById('dialogWorkspaceExternalKpiDatamartProgramTtlHours'),
        workspaceExternalKpiDatamartTimelineRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartTimelineRequired'),
        workspaceExternalKpiDatamartTargetReadyAt: document.getElementById('dialogWorkspaceExternalKpiDatamartTargetReadyAt'),
        workspaceExternalKpiDatamartTimelineGraceHours: document.getElementById('dialogWorkspaceExternalKpiDatamartTimelineGraceHours'),
        workspaceExternalKpiDatamartContractRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartContractRequired'),
        workspaceExternalKpiDatamartContractVersion: document.getElementById('dialogWorkspaceExternalKpiDatamartContractVersion'),
        workspaceExternalKpiDatamartContractMandatoryFields: document.getElementById('dialogWorkspaceExternalKpiDatamartContractMandatoryFields'),
        workspaceExternalKpiDatamartContractOptionalFields: document.getElementById('dialogWorkspaceExternalKpiDatamartContractOptionalFields'),
        workspaceExternalKpiDatamartContractAvailableFields: document.getElementById('dialogWorkspaceExternalKpiDatamartContractAvailableFields'),
        workspaceExternalKpiDatamartContractOptionalCoverageRequired: document.getElementById('dialogWorkspaceExternalKpiDatamartContractOptionalCoverageRequired'),
        workspaceExternalKpiDatamartContractOptionalMinCoveragePct: document.getElementById('dialogWorkspaceExternalKpiDatamartContractOptionalMinCoveragePct'),
        workspaceExternalKpiDatamartContractSummary: document.getElementById('dialogWorkspaceExternalKpiDatamartContractSummary'),
        workspaceExternalKpiDatamartContractSummaryHeadline: document.getElementById('dialogWorkspaceExternalKpiDatamartContractSummaryHeadline'),
        workspaceExternalKpiDatamartContractSummaryChips: document.getElementById('dialogWorkspaceExternalKpiDatamartContractSummaryChips'),
        workspaceExternalKpiDatamartContractSummaryFieldGroups: document.getElementById('dialogWorkspaceExternalKpiDatamartContractSummaryFieldGroups'),
        workspaceExternalKpiDatamartContractSummaryMeta: document.getElementById('dialogWorkspaceExternalKpiDatamartContractSummaryMeta'),
        utcSummary: document.getElementById('dialogWorkspaceExternalKpiUtcSummary'),
      };
    }

    function clampNumber(value, min, max) {
      return Math.min(Math.max(value, min), max);
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

    function toIsoUtcString(rawValue) {
      return toUtcIsoString(rawValue);
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
      const states = EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS.map((field) => {
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
      EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS.forEach((field) => {
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
      EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS.forEach((field) => {
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

    function isValidExternalReferenceUrl(rawValue) {
      const value = String(rawValue || '').trim();
      if (!value) {
        return false;
      }
      try {
        const parsed = new URL(value);
        return ['http:', 'https:'].includes(parsed.protocol) && Boolean(parsed.hostname);
      } catch (error) {
        return false;
      }
    }

    function normalizeDataContractFieldList(value) {
      return Array.from(new Set(
        String(value || '')
          .split(',')
          .map((item) => item.trim())
          .filter(Boolean),
      ));
    }

    function formatCoverageLabel(current, total) {
      const safeCurrent = Number.isFinite(current) ? current : 0;
      const safeTotal = Number.isFinite(total) ? total : 0;
      const pct = safeTotal > 0 ? Math.round((safeCurrent / safeTotal) * 100) : 100;
      return `${safeCurrent}/${safeTotal} (${pct}%)`;
    }

    function renderDatamartContractFieldGroup(title, fields, options = {}) {
      const tone = options.tone || 'success';
      const subtitle = options.subtitle ? `<span class="small text-muted">${escapeHtml(options.subtitle)}</span>` : '';
      const badgeMarkup = Array.isArray(fields) && fields.length
        ? fields.map((field) => `<span class="datamart-contract-preview__field-badge">${escapeHtml(field)}</span>`).join('')
        : '<span class="datamart-contract-preview__empty">—</span>';
      return `
        <div class="datamart-contract-preview__field-group datamart-contract-preview__field-group--${tone}">
          <div class="datamart-contract-preview__field-group-title">
            <span>${escapeHtml(title)}</span>
            ${subtitle}
          </div>
          <div class="datamart-contract-preview__field-badges">${badgeMarkup}</div>
        </div>
      `;
    }

    function buildDatamartContractPreviewState(inputs = getInputs()) {
      const defaults = getDefaultDialogSlaConfig();
      const contractRequired = Boolean(inputs.workspaceExternalKpiDatamartContractRequired?.checked);
      const optionalCoverageConfigured = Boolean(inputs.workspaceExternalKpiDatamartContractOptionalCoverageRequired?.checked);
      const thresholdInput = inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct;
      const thresholdRaw = Number.parseInt(thresholdInput?.value || '', 10);
      const threshold = Number.isFinite(thresholdRaw)
        ? clampNumber(thresholdRaw, 0, 100)
        : defaults.workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct;
      const contractVersion = String(inputs.workspaceExternalKpiDatamartContractVersion?.value || '').trim()
        || defaults.workspace_rollout_external_kpi_datamart_contract_version;
      const mandatoryFields = normalizeDataContractFieldList(inputs.workspaceExternalKpiDatamartContractMandatoryFields?.value);
      const optionalFields = normalizeDataContractFieldList(inputs.workspaceExternalKpiDatamartContractOptionalFields?.value);
      const availableFieldsList = normalizeDataContractFieldList(inputs.workspaceExternalKpiDatamartContractAvailableFields?.value);
      const availableFields = new Set(availableFieldsList);
      const optionalFieldsSet = new Set(optionalFields);
      const contractFieldsSet = new Set([...mandatoryFields, ...optionalFields]);
      const missingMandatory = mandatoryFields.filter((field) => !availableFields.has(field));
      const missingOptional = optionalFields.filter((field) => !availableFields.has(field));
      const overlappingFields = mandatoryFields.filter((field) => optionalFieldsSet.has(field));
      const extraAvailableFields = availableFieldsList.filter((field) => !contractFieldsSet.has(field));
      const blockingGapCount = missingMandatory.length;
      const nonBlockingGapCount = missingOptional.length;
      const mandatoryCoveragePct = mandatoryFields.length > 0
        ? Math.round(((mandatoryFields.length - missingMandatory.length) / mandatoryFields.length) * 100)
        : 100;
      const optionalCoveragePct = optionalFields.length > 0
        ? Math.round(((optionalFields.length - missingOptional.length) / optionalFields.length) * 100)
        : 100;
      const mandatoryCoverageLabel = formatCoverageLabel(mandatoryFields.length - missingMandatory.length, mandatoryFields.length);
      const optionalCoverageLabel = formatCoverageLabel(optionalFields.length - missingOptional.length, optionalFields.length);
      const optionalCoverageGateActive = contractRequired && optionalCoverageConfigured && optionalFields.length > 0;
      const optionalCoverageReady = optionalFields.length === 0 ? true : optionalCoveragePct >= threshold;
      const severity = blockingGapCount > 0 && nonBlockingGapCount > 0
        ? 'mixed'
        : (blockingGapCount > 0 ? 'blocking' : (nonBlockingGapCount > 0 ? 'non_blocking' : 'none'));
      const hasConfigurationConflict = overlappingFields.length > 0;
      const summaryTone = hasConfigurationConflict || blockingGapCount > 0
        ? 'danger'
        : ((optionalCoverageGateActive && !optionalCoverageReady) || nonBlockingGapCount > 0 ? 'warning' : 'success');
      const rolloutState = hasConfigurationConflict
        ? 'rollout следует удерживать в hold до устранения конфликта mandatory/optional полей'
        : (blockingGapCount > 0
          ? 'rollout будет удерживаться в hold из-за mandatory-gap'
          : (optionalCoverageGateActive && !optionalCoverageReady
            ? `rollout будет удерживаться в hold из-за optional coverage ниже ${threshold}%`
            : 'contract не блокирует rollout'));

      return {
        contractRequired,
        optionalCoverageConfigured,
        threshold,
        contractVersion,
        mandatoryFields,
        optionalFields,
        availableFieldsList,
        missingMandatory,
        missingOptional,
        overlappingFields,
        extraAvailableFields,
        blockingGapCount,
        nonBlockingGapCount,
        mandatoryCoverageLabel,
        optionalCoverageLabel,
        mandatoryCoveragePct,
        optionalCoveragePct,
        optionalCoverageGateActive,
        optionalCoverageReady,
        severity,
        hasConfigurationConflict,
        summaryTone,
        rolloutState,
      };
    }

    function syncDatamartContractState(inputs = getInputs()) {
      if (!inputs) {
        return;
      }
      const summary = inputs.workspaceExternalKpiDatamartContractSummary;
      const headline = inputs.workspaceExternalKpiDatamartContractSummaryHeadline;
      const chips = inputs.workspaceExternalKpiDatamartContractSummaryChips;
      const fieldGroups = inputs.workspaceExternalKpiDatamartContractSummaryFieldGroups;
      const meta = inputs.workspaceExternalKpiDatamartContractSummaryMeta;
      const thresholdInput = inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct;
      const previewState = buildDatamartContractPreviewState(inputs);

      if (thresholdInput) {
        thresholdInput.disabled = !previewState.optionalCoverageConfigured;
        thresholdInput.setAttribute('aria-disabled', thresholdInput.disabled ? 'true' : 'false');
        thresholdInput.title = previewState.optionalCoverageConfigured
          ? 'Порог optional coverage участвует в rollout gate.'
          : 'Порог не используется, пока optional coverage gate выключен.';
      }

      if (inputs.workspaceExternalKpiDatamartContractOptionalFields) {
        inputs.workspaceExternalKpiDatamartContractOptionalFields.classList.toggle(
          'is-invalid',
          previewState.optionalCoverageConfigured && previewState.optionalFields.length === 0,
        );
      }
      if (inputs.workspaceExternalKpiDatamartContractMandatoryFields) {
        inputs.workspaceExternalKpiDatamartContractMandatoryFields.classList.toggle('is-invalid', previewState.hasConfigurationConflict);
      }
      if (inputs.workspaceExternalKpiDatamartContractOptionalFields) {
        inputs.workspaceExternalKpiDatamartContractOptionalFields.classList.toggle(
          'is-invalid',
          (previewState.optionalCoverageConfigured && previewState.optionalFields.length === 0) || previewState.hasConfigurationConflict,
        );
      }

      if (!summary) {
        return;
      }

      summary.classList.remove('alert-secondary', 'alert-success', 'alert-warning', 'alert-danger');
      if (!previewState.contractRequired) {
        summary.classList.add('alert-secondary');
        if (headline) {
          headline.innerHTML = 'Data contract выключен. Mandatory/optional поля можно подготовить заранее, но rollout gate начнёт учитывать их только после включения <code>Требовать data contract</code>.';
        }
        if (chips) {
          chips.innerHTML = [
            `<span class="datamart-contract-preview__chip">version ${escapeHtml(previewState.contractVersion)}</span>`,
            `<span class="datamart-contract-preview__chip">mandatory ${escapeHtml(previewState.mandatoryCoverageLabel)}</span>`,
            `<span class="datamart-contract-preview__chip">optional ${escapeHtml(previewState.optionalCoverageLabel)}</span>`,
          ].join('');
        }
        if (fieldGroups) {
          fieldGroups.innerHTML = [
            renderDatamartContractFieldGroup('Mandatory KPI', previewState.mandatoryFields, { tone: 'success', subtitle: `${previewState.mandatoryFields.length} полей` }),
            renderDatamartContractFieldGroup('Optional KPI', previewState.optionalFields, { tone: 'warning', subtitle: `${previewState.optionalFields.length} полей` }),
            renderDatamartContractFieldGroup('Available KPI', previewState.availableFieldsList, { tone: 'success', subtitle: `${previewState.availableFieldsList.length} полей` }),
          ].join('');
        }
        if (meta) {
          meta.textContent = previewState.hasConfigurationConflict
            ? `Обнаружен конфликт: поля ${previewState.overlappingFields.join(', ')} одновременно указаны как mandatory и optional.`
            : 'Live-preview поможет заранее проверить покрытие и структуру KPI-контракта до сохранения настроек.';
        }
        return;
      }

      summary.classList.add(`alert-${previewState.summaryTone}`);
      if (headline) {
        headline.textContent = `Data contract ${previewState.contractVersion}: severity ${previewState.severity}; ${previewState.rolloutState}.`;
      }
      if (chips) {
        const chipItems = [
          { label: `mandatory ${previewState.mandatoryCoverageLabel}`, tone: previewState.blockingGapCount > 0 ? 'danger' : 'success' },
          { label: `optional ${previewState.optionalCoverageLabel}`, tone: (previewState.optionalCoverageGateActive && !previewState.optionalCoverageReady) || previewState.nonBlockingGapCount > 0 ? 'warning' : 'success' },
          { label: `gaps B:${previewState.blockingGapCount} / NB:${previewState.nonBlockingGapCount}`, tone: previewState.blockingGapCount > 0 ? 'danger' : (previewState.nonBlockingGapCount > 0 ? 'warning' : 'success') },
          { label: `optional gate ${previewState.optionalCoverageConfigured ? (previewState.optionalCoverageGateActive ? `on @ ${previewState.threshold}%` : 'configured') : 'off'}`, tone: previewState.optionalCoverageGateActive && !previewState.optionalCoverageReady ? 'warning' : 'success' },
        ];
        if (previewState.hasConfigurationConflict) {
          chipItems.push({ label: 'mandatory/optional overlap', tone: 'danger' });
        }
        chips.innerHTML = chipItems
          .map((item) => `<span class="datamart-contract-preview__chip datamart-contract-preview__chip--${item.tone}">${escapeHtml(item.label)}</span>`)
          .join('');
      }
      if (fieldGroups) {
        fieldGroups.innerHTML = [
          renderDatamartContractFieldGroup('Missing mandatory', previewState.missingMandatory, { tone: previewState.missingMandatory.length ? 'danger' : 'success', subtitle: previewState.missingMandatory.length ? 'blocking gap' : 'ready' }),
          renderDatamartContractFieldGroup('Missing optional', previewState.missingOptional, { tone: previewState.missingOptional.length ? 'warning' : 'success', subtitle: previewState.missingOptional.length ? 'non-blocking gap' : 'ready' }),
          renderDatamartContractFieldGroup('Overlap mandatory/optional', previewState.overlappingFields, { tone: previewState.overlappingFields.length ? 'danger' : 'success', subtitle: previewState.overlappingFields.length ? 'нужно развести по спискам' : 'конфликтов нет' }),
          renderDatamartContractFieldGroup('Available вне контракта', previewState.extraAvailableFields, { tone: previewState.extraAvailableFields.length ? 'warning' : 'success', subtitle: previewState.extraAvailableFields.length ? 'полезно сверить и классифицировать' : 'лишних полей нет' }),
        ].join('');
      }
      if (meta) {
        const metaParts = [
          `Mandatory coverage ${previewState.mandatoryCoveragePct}% (${previewState.mandatoryCoverageLabel}).`,
          `Optional coverage ${previewState.optionalCoveragePct}% (${previewState.optionalCoverageLabel}).`,
        ];
        if (previewState.optionalCoverageConfigured && previewState.optionalFields.length === 0) {
          metaParts.push('Optional coverage gate включён, но список optional KPI пуст — сохранение будет заблокировано.');
        } else if (previewState.optionalCoverageGateActive && !previewState.optionalCoverageReady) {
          metaParts.push(`Optional gate активен и удержит rollout в hold до достижения ${previewState.threshold}% покрытия.`);
        }
        if (previewState.hasConfigurationConflict) {
          metaParts.push('Одно и то же поле не должно одновременно быть mandatory и optional, иначе контракт становится неоднозначным для decisioning.');
        }
        meta.textContent = metaParts.join(' ');
      }
    }

    function initDatamartContractPreview() {
      const inputs = getInputs();
      const controls = [
        inputs.workspaceExternalKpiDatamartContractRequired,
        inputs.workspaceExternalKpiDatamartContractMandatoryFields,
        inputs.workspaceExternalKpiDatamartContractOptionalFields,
        inputs.workspaceExternalKpiDatamartContractAvailableFields,
        inputs.workspaceExternalKpiDatamartContractOptionalCoverageRequired,
        inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct,
      ].filter(Boolean);
      if (!controls.length) {
        return;
      }
      controls.forEach((control) => {
        const eventName = control instanceof HTMLInputElement && control.type === 'checkbox' ? 'change' : 'input';
        control.addEventListener(eventName, () => syncDatamartContractState(inputs));
        if (!(control instanceof HTMLInputElement && control.type === 'checkbox')) {
          control.addEventListener('change', () => syncDatamartContractState(inputs));
        }
      });
      syncDatamartContractState(inputs);
    }

    function initWorkspaceExternalKpiControls() {
      const inputs = getInputs();
      const config = getDialogConfig();
      const defaults = getDefaultDialogSlaConfig();

      if (inputs.crossProductOmnichannelDashboardUrl) {
        inputs.crossProductOmnichannelDashboardUrl.value = String(config.cross_product_omnichannel_dashboard_url || '').trim();
      }
      if (inputs.crossProductOmnichannelDashboardLabel) {
        inputs.crossProductOmnichannelDashboardLabel.value = String(config.cross_product_omnichannel_dashboard_label || '').trim()
          || defaults.cross_product_omnichannel_dashboard_label;
      }
      if (inputs.crossProductFinanceDashboardUrl) {
        inputs.crossProductFinanceDashboardUrl.value = String(config.cross_product_finance_dashboard_url || '').trim();
      }
      if (inputs.crossProductFinanceDashboardLabel) {
        inputs.crossProductFinanceDashboardLabel.value = String(config.cross_product_finance_dashboard_label || '').trim()
          || defaults.cross_product_finance_dashboard_label;
      }
      if (inputs.workspaceExternalKpiGateEnabled) {
        inputs.workspaceExternalKpiGateEnabled.checked = Boolean(config.workspace_rollout_external_kpi_gate_enabled);
      }
      if (inputs.workspaceExternalKpiOmnichannelReady) {
        inputs.workspaceExternalKpiOmnichannelReady.checked = Boolean(config.workspace_rollout_external_kpi_omnichannel_ready);
      }
      if (inputs.workspaceExternalKpiFinanceReady) {
        inputs.workspaceExternalKpiFinanceReady.checked = Boolean(config.workspace_rollout_external_kpi_finance_ready);
      }
      if (inputs.workspaceExternalKpiNote) {
        inputs.workspaceExternalKpiNote.value = String(config.workspace_rollout_external_kpi_note || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartOwner) {
        inputs.workspaceExternalKpiDatamartOwner.value = String(config.workspace_rollout_external_kpi_datamart_owner || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartRunbookUrl) {
        inputs.workspaceExternalKpiDatamartRunbookUrl.value = String(config.workspace_rollout_external_kpi_datamart_runbook_url || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketRequired) {
        inputs.workspaceExternalKpiDatamartDependencyTicketRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_dependency_ticket_required);
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketUrl) {
        inputs.workspaceExternalKpiDatamartDependencyTicketUrl.value = String(config.workspace_rollout_external_kpi_datamart_dependency_ticket_url || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketOwnerRequired) {
        inputs.workspaceExternalKpiDatamartDependencyTicketOwnerRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required);
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketOwner) {
        inputs.workspaceExternalKpiDatamartDependencyTicketOwner.value = String(config.workspace_rollout_external_kpi_datamart_dependency_ticket_owner || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactRequired) {
        inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required);
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContact) {
        inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContact.value = String(config.workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired) {
        inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required);
      }
      if (inputs.workspaceExternalKpiDatamartDependencyTicketFreshnessRequired) {
        inputs.workspaceExternalKpiDatamartDependencyTicketFreshnessRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required);
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[0]);
      if (inputs.workspaceExternalKpiDatamartDependencyTicketTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours, 10);
        inputs.workspaceExternalKpiDatamartDependencyTicketTtlHours.value = Number.isFinite(value)
          ? clampNumber(value, 1, 2160)
          : defaults.workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours;
      }
      if (inputs.workspaceExternalKpiReviewedBy) {
        inputs.workspaceExternalKpiReviewedBy.value = String(config.workspace_rollout_external_kpi_reviewed_by || '').trim();
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[1]);
      if (inputs.workspaceExternalKpiReviewTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_review_ttl_hours, 10);
        inputs.workspaceExternalKpiReviewTtlHours.value = Number.isFinite(value) && value >= 1 && value <= 2160
          ? value
          : defaults.workspace_rollout_external_kpi_review_ttl_hours;
      }
      if (inputs.workspaceExternalKpiDataFreshnessRequired) {
        inputs.workspaceExternalKpiDataFreshnessRequired.checked = Boolean(config.workspace_rollout_external_kpi_data_freshness_required);
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[2]);
      if (inputs.workspaceExternalKpiDataFreshnessTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_data_freshness_ttl_hours, 10);
        inputs.workspaceExternalKpiDataFreshnessTtlHours.value = Number.isFinite(value) && value >= 1 && value <= 720
          ? value
          : defaults.workspace_rollout_external_kpi_data_freshness_ttl_hours;
      }
      if (inputs.workspaceExternalKpiDashboardLinksRequired) {
        inputs.workspaceExternalKpiDashboardLinksRequired.checked = Boolean(config.workspace_rollout_external_kpi_dashboard_links_required);
      }
      if (inputs.workspaceExternalKpiDashboardStatusRequired) {
        inputs.workspaceExternalKpiDashboardStatusRequired.checked = Boolean(config.workspace_rollout_external_kpi_dashboard_status_required);
      }
      if (inputs.workspaceExternalKpiDashboardStatus) {
        const raw = String(config.workspace_rollout_external_kpi_dashboard_status || '').trim().toLowerCase();
        inputs.workspaceExternalKpiDashboardStatus.value = ['healthy', 'degraded', 'down', 'unknown'].includes(raw) ? raw : 'unknown';
      }
      if (inputs.workspaceExternalKpiDashboardStatusNote) {
        inputs.workspaceExternalKpiDashboardStatusNote.value = String(config.workspace_rollout_external_kpi_dashboard_status_note || '').trim();
      }
      if (inputs.workspaceExternalKpiOwnerRunbookRequired) {
        inputs.workspaceExternalKpiOwnerRunbookRequired.checked = Boolean(config.workspace_rollout_external_kpi_owner_runbook_required);
      }
      if (inputs.workspaceExternalKpiDatamartHealthRequired) {
        inputs.workspaceExternalKpiDatamartHealthRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_health_required);
      }
      if (inputs.workspaceExternalKpiDatamartHealthStatus) {
        const raw = String(config.workspace_rollout_external_kpi_datamart_health_status || '').trim().toLowerCase();
        inputs.workspaceExternalKpiDatamartHealthStatus.value = ['healthy', 'degraded', 'down', 'unknown'].includes(raw)
          ? raw
          : 'unknown';
      }
      if (inputs.workspaceExternalKpiDatamartHealthNote) {
        inputs.workspaceExternalKpiDatamartHealthNote.value = String(config.workspace_rollout_external_kpi_datamart_health_note || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartHealthFreshnessRequired) {
        inputs.workspaceExternalKpiDatamartHealthFreshnessRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_health_freshness_required);
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[3]);
      if (inputs.workspaceExternalKpiDatamartHealthTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_datamart_health_ttl_hours, 10);
        inputs.workspaceExternalKpiDatamartHealthTtlHours.value = Number.isFinite(value) && value > 0
          ? value
          : defaults.workspace_rollout_external_kpi_datamart_health_ttl_hours;
      }
      if (inputs.workspaceExternalKpiDatamartProgramBlockerRequired) {
        inputs.workspaceExternalKpiDatamartProgramBlockerRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_program_blocker_required);
      }
      if (inputs.workspaceExternalKpiDatamartProgramStatus) {
        const raw = String(config.workspace_rollout_external_kpi_datamart_program_status || '').trim().toLowerCase();
        inputs.workspaceExternalKpiDatamartProgramStatus.value = ['ready', 'in_progress', 'blocked', 'unknown'].includes(raw)
          ? raw
          : 'unknown';
      }
      if (inputs.workspaceExternalKpiDatamartProgramNote) {
        inputs.workspaceExternalKpiDatamartProgramNote.value = String(config.workspace_rollout_external_kpi_datamart_program_note || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartProgramBlockerUrl) {
        inputs.workspaceExternalKpiDatamartProgramBlockerUrl.value = String(config.workspace_rollout_external_kpi_datamart_program_blocker_url || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartProgramFreshnessRequired) {
        inputs.workspaceExternalKpiDatamartProgramFreshnessRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_program_freshness_required);
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[4]);
      if (inputs.workspaceExternalKpiDatamartProgramTtlHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_datamart_program_ttl_hours, 10);
        inputs.workspaceExternalKpiDatamartProgramTtlHours.value = Number.isFinite(value)
          ? String(clampNumber(value, 1, 2160))
          : String(defaults.workspace_rollout_external_kpi_datamart_program_ttl_hours);
      }
      if (inputs.workspaceExternalKpiDatamartTimelineRequired) {
        inputs.workspaceExternalKpiDatamartTimelineRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_timeline_required);
      }
      populateUtcTimestampField(EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS[5]);
      if (inputs.workspaceExternalKpiDatamartTimelineGraceHours) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_datamart_timeline_grace_hours, 10);
        inputs.workspaceExternalKpiDatamartTimelineGraceHours.value = Number.isFinite(value)
          ? String(clampNumber(value, 0, 720))
          : String(defaults.workspace_rollout_external_kpi_datamart_timeline_grace_hours);
      }
      if (inputs.workspaceExternalKpiDatamartContractRequired) {
        inputs.workspaceExternalKpiDatamartContractRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_contract_required);
      }
      if (inputs.workspaceExternalKpiDatamartContractVersion) {
        inputs.workspaceExternalKpiDatamartContractVersion.value = String(config.workspace_rollout_external_kpi_datamart_contract_version || '').trim()
          || defaults.workspace_rollout_external_kpi_datamart_contract_version;
      }
      if (inputs.workspaceExternalKpiDatamartContractMandatoryFields) {
        inputs.workspaceExternalKpiDatamartContractMandatoryFields.value = String(config.workspace_rollout_external_kpi_datamart_contract_mandatory_fields || '').trim()
          || defaults.workspace_rollout_external_kpi_datamart_contract_mandatory_fields;
      }
      if (inputs.workspaceExternalKpiDatamartContractOptionalFields) {
        inputs.workspaceExternalKpiDatamartContractOptionalFields.value = String(config.workspace_rollout_external_kpi_datamart_contract_optional_fields || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartContractAvailableFields) {
        inputs.workspaceExternalKpiDatamartContractAvailableFields.value = String(config.workspace_rollout_external_kpi_datamart_contract_available_fields || '').trim();
      }
      if (inputs.workspaceExternalKpiDatamartContractOptionalCoverageRequired) {
        inputs.workspaceExternalKpiDatamartContractOptionalCoverageRequired.checked = Boolean(config.workspace_rollout_external_kpi_datamart_contract_optional_coverage_required);
      }
      if (inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct) {
        const value = Number.parseInt(config.workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct, 10);
        inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct.value = Number.isFinite(value)
          ? String(clampNumber(value, 0, 100))
          : String(defaults.workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct);
      }

      syncAllUtcTimestampFields();
      syncDatamartContractState(inputs);
    }

    function collectWorkspaceExternalKpiConfig() {
      const inputs = getInputs();
      const defaults = getDefaultDialogSlaConfig();
      const errors = [];

      const safeCrossProductOmnichannelDashboardUrl = String(inputs.crossProductOmnichannelDashboardUrl?.value || '').trim();
      const safeCrossProductOmnichannelDashboardLabel = String(inputs.crossProductOmnichannelDashboardLabel?.value || '').trim()
        || defaults.cross_product_omnichannel_dashboard_label;
      const safeCrossProductFinanceDashboardUrl = String(inputs.crossProductFinanceDashboardUrl?.value || '').trim();
      const safeCrossProductFinanceDashboardLabel = String(inputs.crossProductFinanceDashboardLabel?.value || '').trim()
        || defaults.cross_product_finance_dashboard_label;
      const safeWorkspaceExternalKpiGateEnabled = Boolean(inputs.workspaceExternalKpiGateEnabled?.checked);
      const safeWorkspaceExternalKpiOmnichannelReady = Boolean(inputs.workspaceExternalKpiOmnichannelReady?.checked);
      const safeWorkspaceExternalKpiFinanceReady = Boolean(inputs.workspaceExternalKpiFinanceReady?.checked);
      const safeWorkspaceExternalKpiNote = String(inputs.workspaceExternalKpiNote?.value || '').trim().slice(0, 500);
      const safeWorkspaceExternalKpiDatamartOwner = String(inputs.workspaceExternalKpiDatamartOwner?.value || '').trim().slice(0, 120);
      const safeWorkspaceExternalKpiDatamartRunbookUrl = String(inputs.workspaceExternalKpiDatamartRunbookUrl?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartDependencyTicketRequired = Boolean(inputs.workspaceExternalKpiDatamartDependencyTicketRequired?.checked);
      const safeWorkspaceExternalKpiDatamartDependencyTicketUrl = String(inputs.workspaceExternalKpiDatamartDependencyTicketUrl?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartDependencyTicketOwnerRequired = Boolean(inputs.workspaceExternalKpiDatamartDependencyTicketOwnerRequired?.checked);
      const safeWorkspaceExternalKpiDatamartDependencyTicketOwner = String(inputs.workspaceExternalKpiDatamartDependencyTicketOwner?.value || '').trim().slice(0, 120);
      const safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContactRequired = Boolean(inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactRequired?.checked);
      const safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContact = String(inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContact?.value || '').trim().slice(0, 160);
      const safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired = Boolean(inputs.workspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired?.checked);
      if (safeWorkspaceExternalKpiDatamartDependencyTicketUrl && !isValidExternalReferenceUrl(safeWorkspaceExternalKpiDatamartDependencyTicketUrl)) {
        errors.push('Dependency-ticket URL должен быть корректной http/https ссылкой.');
      }
      if (safeWorkspaceExternalKpiDatamartDependencyTicketRequired && !safeWorkspaceExternalKpiDatamartDependencyTicketUrl) {
        errors.push('Для dependency-ticket gate укажите ссылку на внешний трекер data-mart.');
      }
      const safeWorkspaceExternalKpiDatamartDependencyTicketFreshnessRequired = Boolean(inputs.workspaceExternalKpiDatamartDependencyTicketFreshnessRequired?.checked);
      const safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAtRaw = String(inputs.workspaceExternalKpiDatamartDependencyTicketUpdatedAt?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt = toUtcIsoString(safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAtRaw);
      if (safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAtRaw && !safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt) {
        errors.push('Дата обновления dependency-ticket указана в некорректном формате UTC.');
      }
      const safeWorkspaceExternalKpiDatamartDependencyTicketTtlHoursRaw = Number.parseInt(inputs.workspaceExternalKpiDatamartDependencyTicketTtlHours?.value, 10);
      const safeWorkspaceExternalKpiDatamartDependencyTicketTtlHours = Number.isFinite(safeWorkspaceExternalKpiDatamartDependencyTicketTtlHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiDatamartDependencyTicketTtlHoursRaw, 1, 2160)
        : defaults.workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours;
      if (safeWorkspaceExternalKpiDatamartDependencyTicketFreshnessRequired && !safeWorkspaceExternalKpiDatamartDependencyTicketUrl) {
        errors.push('Для freshness-gate dependency-ticket укажите URL внешнего тикета.');
      }
      if (safeWorkspaceExternalKpiDatamartDependencyTicketFreshnessRequired && !safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt) {
        errors.push('Для freshness-gate dependency-ticket укажите дату последнего обновления тикета.');
      }
      if (safeWorkspaceExternalKpiDatamartDependencyTicketOwnerRequired && !safeWorkspaceExternalKpiDatamartDependencyTicketOwner) {
        errors.push('Для owner-gate dependency-ticket укажите ответственного владельца.');
      }
      if (safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContactRequired && !safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContact) {
        errors.push('Для owner-contact gate dependency-ticket укажите канал эскалации (email/slack/@owner).');
      }

      const safeWorkspaceExternalKpiReviewedBy = String(inputs.workspaceExternalKpiReviewedBy?.value || '').trim().slice(0, 120);
      const safeWorkspaceExternalKpiReviewedAtRaw = String(inputs.workspaceExternalKpiReviewedAt?.value || '').trim();
      const safeWorkspaceExternalKpiReviewedAt = toUtcIsoString(safeWorkspaceExternalKpiReviewedAtRaw);
      if (safeWorkspaceExternalKpiReviewedAtRaw && !safeWorkspaceExternalKpiReviewedAt) {
        errors.push('Дата последнего review внешних KPI указана в некорректном формате UTC.');
      }
      const safeWorkspaceExternalKpiReviewTtlHoursRaw = Math.round(Number(inputs.workspaceExternalKpiReviewTtlHours?.value));
      const safeWorkspaceExternalKpiReviewTtlHours = Number.isFinite(safeWorkspaceExternalKpiReviewTtlHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiReviewTtlHoursRaw, 1, 2160)
        : defaults.workspace_rollout_external_kpi_review_ttl_hours;

      const safeWorkspaceExternalKpiDataFreshnessRequired = Boolean(inputs.workspaceExternalKpiDataFreshnessRequired?.checked);
      const safeWorkspaceExternalKpiDataUpdatedAtRaw = String(inputs.workspaceExternalKpiDataUpdatedAt?.value || '').trim();
      const safeWorkspaceExternalKpiDataUpdatedAt = toUtcIsoString(safeWorkspaceExternalKpiDataUpdatedAtRaw);
      if (safeWorkspaceExternalKpiDataUpdatedAtRaw && !safeWorkspaceExternalKpiDataUpdatedAt) {
        errors.push('Дата обновления внешнего data-mart указана в некорректном формате UTC.');
      }
      if (safeWorkspaceExternalKpiDataFreshnessRequired && !safeWorkspaceExternalKpiDataUpdatedAtRaw) {
        errors.push('Для контроля freshness укажите дату обновления внешнего data-mart.');
      }
      const safeWorkspaceExternalKpiDataFreshnessTtlHoursRaw = Math.round(Number(inputs.workspaceExternalKpiDataFreshnessTtlHours?.value));
      const safeWorkspaceExternalKpiDataFreshnessTtlHours = Number.isFinite(safeWorkspaceExternalKpiDataFreshnessTtlHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiDataFreshnessTtlHoursRaw, 1, 720)
        : defaults.workspace_rollout_external_kpi_data_freshness_ttl_hours;

      const safeWorkspaceExternalKpiDashboardLinksRequired = Boolean(inputs.workspaceExternalKpiDashboardLinksRequired?.checked);
      const safeWorkspaceExternalKpiDashboardStatusRequired = Boolean(inputs.workspaceExternalKpiDashboardStatusRequired?.checked);
      const safeWorkspaceExternalKpiDashboardStatusRaw = String(inputs.workspaceExternalKpiDashboardStatus?.value || '').trim().toLowerCase();
      const safeWorkspaceExternalKpiDashboardStatus = ['healthy', 'degraded', 'down', 'unknown'].includes(safeWorkspaceExternalKpiDashboardStatusRaw)
        ? safeWorkspaceExternalKpiDashboardStatusRaw
        : 'unknown';
      const safeWorkspaceExternalKpiDashboardStatusNote = String(inputs.workspaceExternalKpiDashboardStatusNote?.value || '').trim().slice(0, 300);
      const safeWorkspaceExternalKpiOwnerRunbookRequired = Boolean(inputs.workspaceExternalKpiOwnerRunbookRequired?.checked);

      const safeWorkspaceExternalKpiDatamartHealthRequired = Boolean(inputs.workspaceExternalKpiDatamartHealthRequired?.checked);
      const safeWorkspaceExternalKpiDatamartHealthStatusRaw = String(inputs.workspaceExternalKpiDatamartHealthStatus?.value || '').trim().toLowerCase();
      const safeWorkspaceExternalKpiDatamartHealthStatus = ['healthy', 'degraded', 'down', 'unknown'].includes(safeWorkspaceExternalKpiDatamartHealthStatusRaw)
        ? safeWorkspaceExternalKpiDatamartHealthStatusRaw
        : defaults.workspace_rollout_external_kpi_datamart_health_status;
      const safeWorkspaceExternalKpiDatamartHealthNote = String(inputs.workspaceExternalKpiDatamartHealthNote?.value || '').trim().slice(0, 300);
      const safeWorkspaceExternalKpiDatamartHealthFreshnessRequired = Boolean(inputs.workspaceExternalKpiDatamartHealthFreshnessRequired?.checked);
      const safeWorkspaceExternalKpiDatamartHealthUpdatedAtRaw = String(inputs.workspaceExternalKpiDatamartHealthUpdatedAt?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartHealthUpdatedAt = toUtcIsoString(safeWorkspaceExternalKpiDatamartHealthUpdatedAtRaw);
      if (safeWorkspaceExternalKpiDatamartHealthUpdatedAtRaw && !safeWorkspaceExternalKpiDatamartHealthUpdatedAt) {
        errors.push('Дата обновления health-статуса data-mart указана в некорректном формате UTC.');
      }
      const safeWorkspaceExternalKpiDatamartHealthTtlHoursRaw = Number.parseInt(inputs.workspaceExternalKpiDatamartHealthTtlHours?.value, 10);
      const safeWorkspaceExternalKpiDatamartHealthTtlHours = Number.isFinite(safeWorkspaceExternalKpiDatamartHealthTtlHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiDatamartHealthTtlHoursRaw, 1, 2160)
        : defaults.workspace_rollout_external_kpi_datamart_health_ttl_hours;

      const safeWorkspaceExternalKpiDatamartProgramBlockerRequired = Boolean(inputs.workspaceExternalKpiDatamartProgramBlockerRequired?.checked);
      const safeWorkspaceExternalKpiDatamartProgramStatusRaw = String(inputs.workspaceExternalKpiDatamartProgramStatus?.value || '').trim().toLowerCase();
      const safeWorkspaceExternalKpiDatamartProgramStatus = ['ready', 'in_progress', 'blocked', 'unknown'].includes(safeWorkspaceExternalKpiDatamartProgramStatusRaw)
        ? safeWorkspaceExternalKpiDatamartProgramStatusRaw
        : defaults.workspace_rollout_external_kpi_datamart_program_status;
      const safeWorkspaceExternalKpiDatamartProgramNote = String(inputs.workspaceExternalKpiDatamartProgramNote?.value || '').trim().slice(0, 300);
      const safeWorkspaceExternalKpiDatamartProgramBlockerUrl = String(inputs.workspaceExternalKpiDatamartProgramBlockerUrl?.value || '').trim();
      if (safeWorkspaceExternalKpiDatamartProgramBlockerUrl && !isValidExternalReferenceUrl(safeWorkspaceExternalKpiDatamartProgramBlockerUrl)) {
        errors.push('Ссылка blocker-ticket data-mart должна быть корректной http/https ссылкой.');
      }
      if (safeWorkspaceExternalKpiDatamartProgramBlockerRequired
        && safeWorkspaceExternalKpiDatamartProgramStatus === 'blocked'
        && !safeWorkspaceExternalKpiDatamartProgramBlockerUrl) {
        errors.push('Для blocked-статуса data-mart при включённом blocker-gate укажите blocker-ticket URL.');
      }
      const safeWorkspaceExternalKpiDatamartProgramFreshnessRequired = Boolean(inputs.workspaceExternalKpiDatamartProgramFreshnessRequired?.checked);
      const safeWorkspaceExternalKpiDatamartProgramUpdatedAtRaw = String(inputs.workspaceExternalKpiDatamartProgramUpdatedAt?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartProgramUpdatedAt = toUtcIsoString(safeWorkspaceExternalKpiDatamartProgramUpdatedAtRaw);
      if (safeWorkspaceExternalKpiDatamartProgramUpdatedAtRaw && !safeWorkspaceExternalKpiDatamartProgramUpdatedAt) {
        errors.push('Дата обновления программного статуса data-mart указана в некорректном формате UTC.');
      }
      const safeWorkspaceExternalKpiDatamartProgramTtlHoursRaw = Number.parseInt(inputs.workspaceExternalKpiDatamartProgramTtlHours?.value || '', 10);
      const safeWorkspaceExternalKpiDatamartProgramTtlHours = Number.isFinite(safeWorkspaceExternalKpiDatamartProgramTtlHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiDatamartProgramTtlHoursRaw, 1, 2160)
        : defaults.workspace_rollout_external_kpi_datamart_program_ttl_hours;

      const safeWorkspaceExternalKpiDatamartTimelineRequired = Boolean(inputs.workspaceExternalKpiDatamartTimelineRequired?.checked);
      const safeWorkspaceExternalKpiDatamartTargetReadyAtRaw = String(inputs.workspaceExternalKpiDatamartTargetReadyAt?.value || '').trim();
      const safeWorkspaceExternalKpiDatamartTargetReadyAt = toIsoUtcString(safeWorkspaceExternalKpiDatamartTargetReadyAtRaw);
      if (safeWorkspaceExternalKpiDatamartTargetReadyAtRaw && !safeWorkspaceExternalKpiDatamartTargetReadyAt) {
        errors.push('Целевой срок готовности data-mart указан в некорректном формате UTC.');
      }
      const safeWorkspaceExternalKpiDatamartTimelineGraceHoursRaw = Number.parseInt(inputs.workspaceExternalKpiDatamartTimelineGraceHours?.value || '', 10);
      const safeWorkspaceExternalKpiDatamartTimelineGraceHours = Number.isFinite(safeWorkspaceExternalKpiDatamartTimelineGraceHoursRaw)
        ? clampNumber(safeWorkspaceExternalKpiDatamartTimelineGraceHoursRaw, 0, 720)
        : defaults.workspace_rollout_external_kpi_datamart_timeline_grace_hours;

      const safeWorkspaceExternalKpiDatamartContractRequired = Boolean(inputs.workspaceExternalKpiDatamartContractRequired?.checked);
      const safeWorkspaceExternalKpiDatamartContractVersion = String(inputs.workspaceExternalKpiDatamartContractVersion?.value || '').trim().slice(0, 64)
        || defaults.workspace_rollout_external_kpi_datamart_contract_version;
      const safeWorkspaceExternalKpiDatamartContractMandatoryFields = String(inputs.workspaceExternalKpiDatamartContractMandatoryFields?.value || '').trim().slice(0, 300)
        || defaults.workspace_rollout_external_kpi_datamart_contract_mandatory_fields;
      const safeWorkspaceExternalKpiDatamartContractOptionalFields = String(inputs.workspaceExternalKpiDatamartContractOptionalFields?.value || '').trim().slice(0, 300);
      const safeWorkspaceExternalKpiDatamartContractAvailableFields = String(inputs.workspaceExternalKpiDatamartContractAvailableFields?.value || '').trim().slice(0, 500);
      const safeWorkspaceExternalKpiDatamartContractOptionalFieldsList = normalizeDataContractFieldList(safeWorkspaceExternalKpiDatamartContractOptionalFields);
      const safeWorkspaceExternalKpiDatamartContractOptionalCoverageRequired = Boolean(inputs.workspaceExternalKpiDatamartContractOptionalCoverageRequired?.checked);
      const safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw = Number.parseInt(inputs.workspaceExternalKpiDatamartContractOptionalMinCoveragePct?.value || '', 10);
      const safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePct = Number.isFinite(safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw)
        ? clampNumber(safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw, 0, 100)
        : defaults.workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct;
      if (!Number.isFinite(safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw)
        || safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw < 0
        || safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePctRaw > 100) {
        errors.push('Порог optional coverage для data contract должен быть в диапазоне 0..100%.');
      }
      if (safeWorkspaceExternalKpiDatamartContractOptionalCoverageRequired
        && safeWorkspaceExternalKpiDatamartContractOptionalFieldsList.length === 0) {
        errors.push('Для optional coverage gate укажите хотя бы одно optional KPI-поле в data contract.');
      }
      const safeWorkspaceExternalKpiDatamartContractMandatoryFieldsList = normalizeDataContractFieldList(safeWorkspaceExternalKpiDatamartContractMandatoryFields);
      const overlappingContractFields = safeWorkspaceExternalKpiDatamartContractMandatoryFieldsList
        .filter((field) => safeWorkspaceExternalKpiDatamartContractOptionalFieldsList.includes(field));
      if (overlappingContractFields.length > 0) {
        errors.push(`Поля data contract не могут одновременно быть mandatory и optional: ${overlappingContractFields.join(', ')}.`);
      }
      if (!Number.isFinite(safeWorkspaceExternalKpiReviewTtlHoursRaw)
        || safeWorkspaceExternalKpiReviewTtlHoursRaw < 1
        || safeWorkspaceExternalKpiReviewTtlHoursRaw > 2160) {
        errors.push('TTL review-подтверждения внешних KPI должен быть в диапазоне 1..2160 часов.');
      }

      const validateDashboardUrl = (value, title) => {
        if (!value) {
          return;
        }
        if (!(value.startsWith('https://') || value.startsWith('http://'))) {
          errors.push(`${title} URL должен начинаться с http:// или https://.`);
        }
      };
      validateDashboardUrl(safeCrossProductOmnichannelDashboardUrl, 'Omni-channel dashboard');
      validateDashboardUrl(safeCrossProductFinanceDashboardUrl, 'Финансовый dashboard');
      validateDashboardUrl(safeWorkspaceExternalKpiDatamartRunbookUrl, 'Runbook data-mart');

      syncAllUtcTimestampFields();
      syncDatamartContractState(inputs);

      return {
        errors,
        config: {
          dialog_cross_product_omnichannel_dashboard_url: safeCrossProductOmnichannelDashboardUrl,
          dialog_cross_product_omnichannel_dashboard_label: safeCrossProductOmnichannelDashboardLabel,
          dialog_cross_product_finance_dashboard_url: safeCrossProductFinanceDashboardUrl,
          dialog_cross_product_finance_dashboard_label: safeCrossProductFinanceDashboardLabel,
          dialog_workspace_rollout_external_kpi_gate_enabled: safeWorkspaceExternalKpiGateEnabled,
          dialog_workspace_rollout_external_kpi_omnichannel_ready: safeWorkspaceExternalKpiOmnichannelReady,
          dialog_workspace_rollout_external_kpi_finance_ready: safeWorkspaceExternalKpiFinanceReady,
          dialog_workspace_rollout_external_kpi_note: safeWorkspaceExternalKpiNote,
          dialog_workspace_rollout_external_kpi_datamart_owner: safeWorkspaceExternalKpiDatamartOwner,
          dialog_workspace_rollout_external_kpi_datamart_runbook_url: safeWorkspaceExternalKpiDatamartRunbookUrl,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_required: safeWorkspaceExternalKpiDatamartDependencyTicketRequired,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_url: safeWorkspaceExternalKpiDatamartDependencyTicketUrl,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required: safeWorkspaceExternalKpiDatamartDependencyTicketOwnerRequired,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner: safeWorkspaceExternalKpiDatamartDependencyTicketOwner,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required: safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContactRequired,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact: safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContact,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required: safeWorkspaceExternalKpiDatamartDependencyTicketOwnerContactActionableRequired,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required: safeWorkspaceExternalKpiDatamartDependencyTicketFreshnessRequired,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at: safeWorkspaceExternalKpiDatamartDependencyTicketUpdatedAt,
          dialog_workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours: safeWorkspaceExternalKpiDatamartDependencyTicketTtlHours,
          dialog_workspace_rollout_external_kpi_reviewed_by: safeWorkspaceExternalKpiReviewedBy,
          dialog_workspace_rollout_external_kpi_reviewed_at: safeWorkspaceExternalKpiReviewedAt,
          dialog_workspace_rollout_external_kpi_review_ttl_hours: safeWorkspaceExternalKpiReviewTtlHours,
          dialog_workspace_rollout_external_kpi_data_freshness_required: safeWorkspaceExternalKpiDataFreshnessRequired,
          dialog_workspace_rollout_external_kpi_data_updated_at: safeWorkspaceExternalKpiDataUpdatedAt,
          dialog_workspace_rollout_external_kpi_data_freshness_ttl_hours: safeWorkspaceExternalKpiDataFreshnessTtlHours,
          dialog_workspace_rollout_external_kpi_dashboard_links_required: safeWorkspaceExternalKpiDashboardLinksRequired,
          dialog_workspace_rollout_external_kpi_dashboard_status_required: safeWorkspaceExternalKpiDashboardStatusRequired,
          dialog_workspace_rollout_external_kpi_dashboard_status: safeWorkspaceExternalKpiDashboardStatus,
          dialog_workspace_rollout_external_kpi_dashboard_status_note: safeWorkspaceExternalKpiDashboardStatusNote,
          dialog_workspace_rollout_external_kpi_owner_runbook_required: safeWorkspaceExternalKpiOwnerRunbookRequired,
          dialog_workspace_rollout_external_kpi_datamart_health_required: safeWorkspaceExternalKpiDatamartHealthRequired,
          dialog_workspace_rollout_external_kpi_datamart_health_status: safeWorkspaceExternalKpiDatamartHealthStatus,
          dialog_workspace_rollout_external_kpi_datamart_health_note: safeWorkspaceExternalKpiDatamartHealthNote,
          dialog_workspace_rollout_external_kpi_datamart_health_freshness_required: safeWorkspaceExternalKpiDatamartHealthFreshnessRequired,
          dialog_workspace_rollout_external_kpi_datamart_health_updated_at: safeWorkspaceExternalKpiDatamartHealthUpdatedAt,
          dialog_workspace_rollout_external_kpi_datamart_health_ttl_hours: safeWorkspaceExternalKpiDatamartHealthTtlHours,
          dialog_workspace_rollout_external_kpi_datamart_program_blocker_required: safeWorkspaceExternalKpiDatamartProgramBlockerRequired,
          dialog_workspace_rollout_external_kpi_datamart_program_status: safeWorkspaceExternalKpiDatamartProgramStatus,
          dialog_workspace_rollout_external_kpi_datamart_program_note: safeWorkspaceExternalKpiDatamartProgramNote,
          dialog_workspace_rollout_external_kpi_datamart_program_blocker_url: safeWorkspaceExternalKpiDatamartProgramBlockerUrl,
          dialog_workspace_rollout_external_kpi_datamart_program_freshness_required: safeWorkspaceExternalKpiDatamartProgramFreshnessRequired,
          dialog_workspace_rollout_external_kpi_datamart_program_updated_at: safeWorkspaceExternalKpiDatamartProgramUpdatedAt,
          dialog_workspace_rollout_external_kpi_datamart_program_ttl_hours: safeWorkspaceExternalKpiDatamartProgramTtlHours,
          dialog_workspace_rollout_external_kpi_datamart_timeline_required: safeWorkspaceExternalKpiDatamartTimelineRequired,
          dialog_workspace_rollout_external_kpi_datamart_target_ready_at: safeWorkspaceExternalKpiDatamartTargetReadyAt,
          dialog_workspace_rollout_external_kpi_datamart_timeline_grace_hours: safeWorkspaceExternalKpiDatamartTimelineGraceHours,
          dialog_workspace_rollout_external_kpi_datamart_contract_required: safeWorkspaceExternalKpiDatamartContractRequired,
          dialog_workspace_rollout_external_kpi_datamart_contract_version: safeWorkspaceExternalKpiDatamartContractVersion,
          dialog_workspace_rollout_external_kpi_datamart_contract_mandatory_fields: safeWorkspaceExternalKpiDatamartContractMandatoryFields,
          dialog_workspace_rollout_external_kpi_datamart_contract_optional_fields: safeWorkspaceExternalKpiDatamartContractOptionalFields,
          dialog_workspace_rollout_external_kpi_datamart_contract_available_fields: safeWorkspaceExternalKpiDatamartContractAvailableFields,
          dialog_workspace_rollout_external_kpi_datamart_contract_optional_coverage_required: safeWorkspaceExternalKpiDatamartContractOptionalCoverageRequired,
          dialog_workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct: safeWorkspaceExternalKpiDatamartContractOptionalMinCoveragePct,
        },
      };
    }

    function initialize() {
      initWorkspaceExternalKpiControls();
      initUtcTimestampFields();
      initDatamartContractPreview();
    }

    return {
      collectWorkspaceExternalKpiConfig,
      initialize,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogWorkspaceExternalKpiRuntime) {
        return window.__settingsDialogWorkspaceExternalKpiRuntime;
      }
      const runtime = createRuntime(options);
      runtime.initialize();
      window.__settingsDialogWorkspaceExternalKpiRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogWorkspaceExternalKpiRuntime = Object.freeze(api);
}());

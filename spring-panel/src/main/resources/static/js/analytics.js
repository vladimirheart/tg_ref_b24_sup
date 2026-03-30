(function () {
  const card = document.getElementById('workspaceTelemetryCard');
  if (!card) {
    return;
  }

  const daysSelect = document.getElementById('workspaceTelemetryDays');
  const experimentInput = document.getElementById('workspaceTelemetryExperiment');
  const fromUtcInput = document.getElementById('workspaceTelemetryFromUtc');
  const toUtcInput = document.getElementById('workspaceTelemetryToUtc');
  const resetWindowButton = document.getElementById('workspaceTelemetryResetWindow');
  const refreshButton = document.getElementById('workspaceTelemetryRefresh');
  const exportCsvButton = document.getElementById('workspaceTelemetryExportCsv');
  const scopeSelect = document.getElementById('workspaceTelemetryScope');
  const segmentInput = document.getElementById('workspaceTelemetrySegment');
  const okBadge = document.getElementById('workspaceTelemetryGuardrailsOk');
  const attentionBadge = document.getElementById('workspaceTelemetryGuardrailsAttention');
  const updatedAt = document.getElementById('workspaceTelemetryUpdatedAt');
  const filterState = document.getElementById('workspaceTelemetryFilterState');
  const alertBox = document.getElementById('workspaceTelemetryAlertBox');
  const rolloutDecisionBox = document.getElementById('workspaceTelemetryRolloutDecision');
  const kpiSignalState = document.getElementById('workspaceTelemetryKpiSignalState');
  const scorecardUpdatedAt = document.getElementById('workspaceTelemetryScorecardUpdatedAt');
  const scorecardTable = document.getElementById('workspaceTelemetryScorecardTable');
  const packetStatus = document.getElementById('workspaceTelemetryPacketStatus');
  const packetUpdatedAt = document.getElementById('workspaceTelemetryPacketUpdatedAt');
  const packetSummary = document.getElementById('workspaceTelemetryPacketSummary');
  const packetTable = document.getElementById('workspaceTelemetryPacketTable');
  const packetOwnerState = document.getElementById('workspaceTelemetryPacketOwnerState');
  const packetOwnerMeta = document.getElementById('workspaceTelemetryPacketOwnerMeta');
  const packetParityState = document.getElementById('workspaceTelemetryPacketParityState');
  const packetParityMeta = document.getElementById('workspaceTelemetryPacketParityMeta');
  const packetIncidentState = document.getElementById('workspaceTelemetryPacketIncidentState');
  const packetIncidentMeta = document.getElementById('workspaceTelemetryPacketIncidentMeta');
  const packetReviewState = document.getElementById('workspaceTelemetryPacketReviewState');
  const packetReviewMeta = document.getElementById('workspaceTelemetryPacketReviewMeta');
  const reviewByInput = document.getElementById('workspaceTelemetryReviewBy');
  const reviewAtInput = document.getElementById('workspaceTelemetryReviewAtUtc');
  const reviewNoteInput = document.getElementById('workspaceTelemetryReviewNote');
  const reviewDecisionActionInput = document.getElementById('workspaceTelemetryReviewDecisionAction');
  const reviewIncidentFollowupInput = document.getElementById('workspaceTelemetryReviewIncidentFollowup');
  const reviewRequiredCriteriaInput = document.getElementById('workspaceTelemetryReviewRequiredCriteria');
  const reviewCheckedCriteriaInput = document.getElementById('workspaceTelemetryReviewCheckedCriteria');
  const reviewConfirmButton = document.getElementById('workspaceTelemetryReviewConfirm');
  const reviewActionState = document.getElementById('workspaceTelemetryReviewActionState');
  const packetExitState = document.getElementById('workspaceTelemetryPacketExitState');
  const packetExitMeta = document.getElementById('workspaceTelemetryPacketExitMeta');
  const packetLegacyState = document.getElementById('workspaceTelemetryPacketLegacyState');
  const packetLegacyMeta = document.getElementById('workspaceTelemetryPacketLegacyMeta');
  const legacyScenariosInput = document.getElementById('workspaceTelemetryLegacyScenarios');
  const legacyReviewedByInput = document.getElementById('workspaceTelemetryLegacyReviewedBy');
  const legacyReviewedAtInput = document.getElementById('workspaceTelemetryLegacyReviewedAtUtc');
  const legacyReviewNoteInput = document.getElementById('workspaceTelemetryLegacyReviewNote');
  const legacyScenarioMetadataInput = document.getElementById('workspaceTelemetryLegacyScenarioMetadata');
  const legacySaveButton = document.getElementById('workspaceTelemetryLegacySave');
  const legacyActionState = document.getElementById('workspaceTelemetryLegacyActionState');
  const legacyInventorySummary = document.getElementById('workspaceTelemetryLegacyInventorySummary');
  const legacyQueueSummary = document.getElementById('workspaceTelemetryLegacyQueueSummary');
  const packetLegacyUsageState = document.getElementById('workspaceTelemetryPacketLegacyUsageState');
  const packetLegacyUsageMeta = document.getElementById('workspaceTelemetryPacketLegacyUsageMeta');
  const legacyUsageReviewedByInput = document.getElementById('workspaceTelemetryLegacyUsageReviewedBy');
  const legacyUsageReviewedAtInput = document.getElementById('workspaceTelemetryLegacyUsageReviewedAtUtc');
  const legacyUsageMaxSharePctInput = document.getElementById('workspaceTelemetryLegacyUsageMaxSharePct');
  const legacyUsageMinWorkspaceOpensInput = document.getElementById('workspaceTelemetryLegacyUsageMinWorkspaceOpens');
  const legacyUsageMaxShareDeltaPctInput = document.getElementById('workspaceTelemetryLegacyUsageMaxShareDeltaPct');
  const legacyUsageMaxBlockedShareDeltaPctInput = document.getElementById('workspaceTelemetryLegacyUsageMaxBlockedShareDeltaPct');
  const legacyUsageDecisionInput = document.getElementById('workspaceTelemetryLegacyUsageDecision');
  const legacyUsageReviewNoteInput = document.getElementById('workspaceTelemetryLegacyUsageReviewNote');
  const legacyUsageSaveButton = document.getElementById('workspaceTelemetryLegacyUsageSave');
  const legacyUsageAllowedReasonsInput = document.getElementById('workspaceTelemetryLegacyUsageAllowedReasons');
  const legacyUsageReasonCatalogRequiredInput = document.getElementById('workspaceTelemetryLegacyUsageReasonCatalogRequired');
  const legacyUsageBlockedReasonsReviewedInput = document.getElementById('workspaceTelemetryLegacyUsageBlockedReasonsReviewed');
  const legacyUsageBlockedReasonsFollowupInput = document.getElementById('workspaceTelemetryLegacyUsageBlockedReasonsFollowup');
  const legacyUsageActionState = document.getElementById('workspaceTelemetryLegacyUsageActionState');
  const packetContextState = document.getElementById('workspaceTelemetryPacketContextState');
  const packetContextMeta = document.getElementById('workspaceTelemetryPacketContextMeta');
  const contextRequiredInput = document.getElementById('workspaceTelemetryContextRequired');
  const contextScenariosInput = document.getElementById('workspaceTelemetryContextScenarios');
  const contextMandatoryFieldsInput = document.getElementById('workspaceTelemetryContextMandatoryFields');
  const contextScenarioMandatoryFieldsInput = document.getElementById('workspaceTelemetryContextScenarioMandatoryFields');
  const contextSourceOfTruthInput = document.getElementById('workspaceTelemetryContextSourceOfTruth');
  const contextScenarioSourceOfTruthInput = document.getElementById('workspaceTelemetryContextScenarioSourceOfTruth');
  const contextPriorityBlocksInput = document.getElementById('workspaceTelemetryContextPriorityBlocks');
  const contextScenarioPriorityBlocksInput = document.getElementById('workspaceTelemetryContextScenarioPriorityBlocks');
  const contextPlaybooksInput = document.getElementById('workspaceTelemetryContextPlaybooks');
  const contextReviewedByInput = document.getElementById('workspaceTelemetryContextReviewedBy');
  const contextReviewedAtInput = document.getElementById('workspaceTelemetryContextReviewedAtUtc');
  const contextReviewNoteInput = document.getElementById('workspaceTelemetryContextReviewNote');
  const contextSaveButton = document.getElementById('workspaceTelemetryContextSave');
  const contextActionState = document.getElementById('workspaceTelemetryContextActionState');
  const contextActionSummary = document.getElementById('workspaceTelemetryContextActionSummary');
  const contextNoiseSummary = document.getElementById('workspaceTelemetryContextNoiseSummary');
  const slaPolicyAuditStatus = document.getElementById('workspaceTelemetrySlaPolicyAuditStatus');
  const slaPolicyAuditUpdatedAt = document.getElementById('workspaceTelemetrySlaPolicyAuditUpdatedAt');
  const slaPolicyAuditSummary = document.getElementById('workspaceTelemetrySlaPolicyAuditSummary');
  const slaPolicyAuditMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditMeta');
  const slaPolicyAuditLayers = document.getElementById('workspaceTelemetrySlaPolicyAuditLayers');
  const slaPolicyAuditLayersMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditLayersMeta');
  const slaPolicyAuditPreview = document.getElementById('workspaceTelemetrySlaPolicyAuditPreview');
  const slaPolicyAuditPreviewMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditPreviewMeta');
  const slaPolicyAuditIssueSummary = document.getElementById('workspaceTelemetrySlaPolicyAuditIssueSummary');
  const slaPolicyAuditSummaryCompact = document.getElementById('workspaceTelemetrySlaPolicyAuditSummaryCompact');
  const slaPolicyAuditIssuesTable = document.getElementById('workspaceTelemetrySlaPolicyAuditIssuesTable');
  const slaPolicyAuditRulesTable = document.getElementById('workspaceTelemetrySlaPolicyAuditRulesTable');
  const slaPolicyReviewedByInput = document.getElementById('workspaceTelemetrySlaPolicyReviewedBy');
  const slaPolicyReviewedAtInput = document.getElementById('workspaceTelemetrySlaPolicyReviewedAtUtc');
  const slaPolicyReviewNoteInput = document.getElementById('workspaceTelemetrySlaPolicyReviewNote');
  const slaPolicyDecisionInput = document.getElementById('workspaceTelemetrySlaPolicyDecision');
  const slaPolicyDryRunTicketIdInput = document.getElementById('workspaceTelemetrySlaPolicyDryRunTicketId');
  const slaPolicyReviewMeta = document.getElementById('workspaceTelemetrySlaPolicyReviewMeta');
  const slaPolicySaveReviewButton = document.getElementById('workspaceTelemetrySlaPolicySaveReview');
  const slaPolicyActionState = document.getElementById('workspaceTelemetrySlaPolicyActionState');
  const macroGovernanceStatus = document.getElementById('workspaceTelemetryMacroGovernanceStatus');
  const macroGovernanceUpdatedAt = document.getElementById('workspaceTelemetryMacroGovernanceUpdatedAt');
  const macroGovernanceSummary = document.getElementById('workspaceTelemetryMacroGovernanceSummary');
  const macroGovernanceMeta = document.getElementById('workspaceTelemetryMacroGovernanceMeta');
  const macroGovernanceRequirements = document.getElementById('workspaceTelemetryMacroGovernanceRequirements');
  const macroGovernanceRequirementsMeta = document.getElementById('workspaceTelemetryMacroGovernanceRequirementsMeta');
  const macroGovernanceCleanup = document.getElementById('workspaceTelemetryMacroGovernanceCleanup');
  const macroGovernanceCleanupMeta = document.getElementById('workspaceTelemetryMacroGovernanceCleanupMeta');
  const macroGovernanceIssueSummary = document.getElementById('workspaceTelemetryMacroGovernanceIssueSummary');
  const macroGovernanceSummaryCompact = document.getElementById('workspaceTelemetryMacroGovernanceSummaryCompact');
  const macroGovernanceIssuesTable = document.getElementById('workspaceTelemetryMacroGovernanceIssuesTable');
  const macroGovernanceTemplatesTable = document.getElementById('workspaceTelemetryMacroGovernanceTemplatesTable');
  const macroGovernanceReviewedByInput = document.getElementById('workspaceTelemetryMacroGovernanceReviewedBy');
  const macroGovernanceReviewedAtInput = document.getElementById('workspaceTelemetryMacroGovernanceReviewedAtUtc');
  const macroGovernanceReviewNoteInput = document.getElementById('workspaceTelemetryMacroGovernanceReviewNote');
  const macroGovernanceDecisionInput = document.getElementById('workspaceTelemetryMacroGovernanceDecision');
  const macroGovernanceCleanupTicketIdInput = document.getElementById('workspaceTelemetryMacroGovernanceCleanupTicketId');
  const macroGovernanceSaveReviewButton = document.getElementById('workspaceTelemetryMacroGovernanceSaveReview');
  const macroGovernanceActionState = document.getElementById('workspaceTelemetryMacroGovernanceActionState');
  const macroExternalCatalogVerifiedByInput = document.getElementById('workspaceTelemetryMacroExternalCatalogVerifiedBy');
  const macroExternalCatalogVerifiedAtInput = document.getElementById('workspaceTelemetryMacroExternalCatalogVerifiedAtUtc');
  const macroExternalCatalogExpectedVersionInput = document.getElementById('workspaceTelemetryMacroExternalCatalogExpectedVersion');
  const macroExternalCatalogObservedVersionInput = document.getElementById('workspaceTelemetryMacroExternalCatalogObservedVersion');
  const macroExternalCatalogDecisionInput = document.getElementById('workspaceTelemetryMacroExternalCatalogDecision');
  const macroExternalCatalogReviewTtlHoursInput = document.getElementById('workspaceTelemetryMacroExternalCatalogReviewTtlHours');
  const macroExternalCatalogReviewNoteInput = document.getElementById('workspaceTelemetryMacroExternalCatalogReviewNote');
  const macroExternalCatalogSavePolicyButton = document.getElementById('workspaceTelemetryMacroExternalCatalogSavePolicy');
  const macroExternalCatalogActionState = document.getElementById('workspaceTelemetryMacroExternalCatalogActionState');
  const macroDeprecationReviewedByInput = document.getElementById('workspaceTelemetryMacroDeprecationReviewedBy');
  const macroDeprecationReviewedAtInput = document.getElementById('workspaceTelemetryMacroDeprecationReviewedAtUtc');
  const macroDeprecationDecisionInput = document.getElementById('workspaceTelemetryMacroDeprecationDecision');
  const macroDeprecationReviewTtlHoursInput = document.getElementById('workspaceTelemetryMacroDeprecationReviewTtlHours');
  const macroDeprecationTicketIdInput = document.getElementById('workspaceTelemetryMacroDeprecationTicketId');
  const macroDeprecationReviewNoteInput = document.getElementById('workspaceTelemetryMacroDeprecationReviewNote');
  const macroDeprecationSavePolicyButton = document.getElementById('workspaceTelemetryMacroDeprecationSavePolicy');
  const macroDeprecationActionState = document.getElementById('workspaceTelemetryMacroDeprecationActionState');
  const alertsTable = document.getElementById('workspaceTelemetryAlertsTable');
  const shiftTable = document.getElementById('workspaceTelemetryShiftTable');
  const teamTable = document.getElementById('workspaceTelemetryTeamTable');
  const riskSegmentsTable = document.getElementById('workspaceTelemetryRiskSegmentsTable');
  const profileGapTable = document.getElementById('workspaceTelemetryProfileGapTable');
  const sourceGapTable = document.getElementById('workspaceTelemetrySourceGapTable');
  const attributePolicyGapTable = document.getElementById('workspaceTelemetryAttributePolicyGapTable');
  const blockGapTable = document.getElementById('workspaceTelemetryBlockGapTable');
  const parityGapTable = document.getElementById('workspaceTelemetryParityGapTable');

  const metricNodes = {};
  let latestPayload = null;
  card.querySelectorAll('[data-metric]').forEach((node) => {
    metricNodes[node.getAttribute('data-metric')] = node;
  });

  function escapeCsv(value) {
    const normalized = value === null || value === undefined ? '' : String(value);
    if (!/[\",\n]/.test(normalized)) {
      return normalized;
    }
    return `"${normalized.replaceAll('"', '""')}"`;
  }

  function formatNumber(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    return Number(value).toLocaleString('ru-RU');
  }

  function formatRate(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    return `${(Number(value) * 100).toFixed(2)}%`;
  }

  function formatTimestamp(value) {
    if (!value) {
      return '—';
    }
    const normalized = String(value).trim().replace(' ', 'T');
    const parsed = new Date(/\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(normalized) ? `${normalized}Z` : normalized);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    const day = String(parsed.getUTCDate()).padStart(2, '0');
    const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
    const year = parsed.getUTCFullYear();
    const hours = String(parsed.getUTCHours()).padStart(2, '0');
    const minutes = String(parsed.getUTCMinutes()).padStart(2, '0');
    return `${day}.${month}.${year} ${hours}:${minutes} UTC`;
  }

  function toDateTimeLocalValue(value) {
    if (!value) {
      return '';
    }
    const parsed = new Date(String(value));
    if (Number.isNaN(parsed.getTime())) {
      return '';
    }
    const year = parsed.getUTCFullYear();
    const month = String(parsed.getUTCMonth() + 1).padStart(2, '0');
    const day = String(parsed.getUTCDate()).padStart(2, '0');
    const hours = String(parsed.getUTCHours()).padStart(2, '0');
    const minutes = String(parsed.getUTCMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  function dateTimeLocalToUtcIso(value) {
    const normalized = String(value || '').trim();
    if (!normalized) {
      return '';
    }
    const parsed = new Date(`${normalized}:00Z`);
    if (Number.isNaN(parsed.getTime())) {
      return null;
    }
    return parsed.toISOString();
  }

  function isTimestampInvalid(signal, canonicalKey, legacyKey) {
    if (!signal || typeof signal !== 'object') {
      return false;
    }
    if (canonicalKey && signal[canonicalKey] === true) {
      return true;
    }
    if (legacyKey && signal[legacyKey] === true) {
      return true;
    }
    return false;
  }

  function setLoadingState() {
    Object.values(metricNodes).forEach((node) => {
      node.textContent = '…';
    });
    updatedAt.textContent = 'Обновление telemetry...';
    okBadge.classList.add('d-none');
    attentionBadge.classList.add('d-none');
    alertBox.classList.add('d-none');
    if (rolloutDecisionBox) {
      rolloutDecisionBox.classList.add('d-none');
      rolloutDecisionBox.className = 'alert d-none mb-2';
      rolloutDecisionBox.textContent = '';
    }
    if (kpiSignalState) {
      kpiSignalState.textContent = '';
    }
    if (scorecardUpdatedAt) {
      scorecardUpdatedAt.textContent = '';
    }
    if (scorecardTable) {
      scorecardTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка scorecard...</td></tr>';
    }
    if (packetStatus) {
      packetStatus.className = 'badge text-bg-secondary';
      packetStatus.textContent = 'Packet: —';
    }
    if (packetUpdatedAt) {
      packetUpdatedAt.textContent = '';
    }
    if (packetSummary) {
      packetSummary.textContent = '';
      packetSummary.classList.add('d-none');
    }
    if (packetTable) {
      packetTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка governance packet...</td></tr>';
    }
    if (packetOwnerState) {
      packetOwnerState.textContent = '—';
    }
    if (packetOwnerMeta) {
      packetOwnerMeta.textContent = '';
    }
    if (packetParityState) {
      packetParityState.textContent = '—';
    }
    if (packetParityMeta) {
      packetParityMeta.textContent = '';
    }
    if (packetIncidentState) {
      packetIncidentState.textContent = '—';
    }
    if (packetIncidentMeta) {
      packetIncidentMeta.textContent = '';
    }
    if (packetReviewState) {
      packetReviewState.textContent = '—';
    }
    if (packetReviewMeta) {
      packetReviewMeta.textContent = '';
    }
    if (reviewActionState) {
      reviewActionState.textContent = '';
    }
    if (packetExitState) {
      packetExitState.textContent = '—';
    }
    if (packetExitMeta) {
      packetExitMeta.textContent = '';
    }
    if (packetLegacyState) {
      packetLegacyState.textContent = '—';
    }
    if (packetLegacyMeta) {
      packetLegacyMeta.textContent = '';
    }
    if (legacyInventorySummary) {
      legacyInventorySummary.textContent = '';
    }
    if (legacyQueueSummary) {
      legacyQueueSummary.textContent = '';
    }
    if (legacyActionState) {
      legacyActionState.textContent = '';
    }
    if (packetContextState) {
      packetContextState.textContent = '—';
    }
    if (packetContextMeta) {
      packetContextMeta.textContent = '';
    }
    if (contextActionSummary) {
      contextActionSummary.textContent = '';
    }
    if (contextNoiseSummary) {
      contextNoiseSummary.textContent = '';
    }
    if (contextActionState) {
      contextActionState.textContent = '';
    }
    if (slaPolicyAuditStatus) {
      slaPolicyAuditStatus.className = 'badge text-bg-secondary';
      slaPolicyAuditStatus.textContent = 'Audit: —';
    }
    if (slaPolicyAuditUpdatedAt) {
      slaPolicyAuditUpdatedAt.textContent = '';
    }
    if (slaPolicyAuditSummary) {
      slaPolicyAuditSummary.textContent = '—';
    }
    if (slaPolicyAuditMeta) {
      slaPolicyAuditMeta.textContent = '';
    }
    if (slaPolicyAuditLayers) {
      slaPolicyAuditLayers.textContent = '—';
    }
    if (slaPolicyAuditLayersMeta) {
      slaPolicyAuditLayersMeta.textContent = '';
    }
    if (slaPolicyAuditPreview) {
      slaPolicyAuditPreview.textContent = '—';
    }
    if (slaPolicyAuditPreviewMeta) {
      slaPolicyAuditPreviewMeta.textContent = '';
    }
    if (slaPolicyAuditIssueSummary) {
      slaPolicyAuditIssueSummary.textContent = '';
      slaPolicyAuditIssueSummary.classList.add('d-none');
    }
    if (slaPolicyAuditSummaryCompact) {
      slaPolicyAuditSummaryCompact.textContent = '';
    }
    if (slaPolicyAuditIssuesTable) {
      slaPolicyAuditIssuesTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
    }
    if (slaPolicyAuditRulesTable) {
      slaPolicyAuditRulesTable.innerHTML = '<tr><td colspan="7" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
    }
    if (macroGovernanceStatus) {
      macroGovernanceStatus.className = 'badge text-bg-secondary';
      macroGovernanceStatus.textContent = 'Audit: —';
    }
    if (macroGovernanceUpdatedAt) {
      macroGovernanceUpdatedAt.textContent = '';
    }
    if (macroGovernanceSummary) {
      macroGovernanceSummary.textContent = '—';
    }
    if (macroGovernanceMeta) {
      macroGovernanceMeta.textContent = '';
    }
    if (macroGovernanceRequirements) {
      macroGovernanceRequirements.textContent = '—';
    }
    if (macroGovernanceRequirementsMeta) {
      macroGovernanceRequirementsMeta.textContent = '';
    }
    if (macroGovernanceCleanup) {
      macroGovernanceCleanup.textContent = '—';
    }
    if (macroGovernanceCleanupMeta) {
      macroGovernanceCleanupMeta.textContent = '';
    }
    if (macroGovernanceIssueSummary) {
      macroGovernanceIssueSummary.textContent = '';
      macroGovernanceIssueSummary.classList.add('d-none');
    }
    if (macroGovernanceSummaryCompact) {
      macroGovernanceSummaryCompact.textContent = '';
    }
    if (macroGovernanceIssuesTable) {
      macroGovernanceIssuesTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
    }
    if (macroGovernanceTemplatesTable) {
      macroGovernanceTemplatesTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
    }
    if (macroGovernanceActionState) {
      macroGovernanceActionState.textContent = '';
    }
    alertsTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    shiftTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    teamTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    if (riskSegmentsTable) {
      riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    }
    [profileGapTable, sourceGapTable, attributePolicyGapTable, blockGapTable, parityGapTable].forEach((tableNode) => {
      if (tableNode) {
        tableNode.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
      }
    });
  }

  function toPercentRate(count, events) {
    if (!events || events <= 0) {
      return 0;
    }
    return Number(count || 0) / events;
  }

  function renderBreakdownRows(tableNode, rows, dimensionField, emptyText) {
    if (!tableNode) {
      return;
    }
    if (!Array.isArray(rows) || rows.length === 0) {
      tableNode.innerHTML = `<tr><td colspan="5" class="text-muted text-center py-3">${emptyText}</td></tr>`;
      return;
    }

    tableNode.innerHTML = rows.map((row) => {
      const dimension = row?.[dimensionField] || '—';
      const events = Number(row?.events || 0);
      const renderErrorRate = toPercentRate(row?.render_errors, events);
      const fallbackRate = toPercentRate(row?.fallbacks, events);
      const abandonRate = toPercentRate(row?.abandons, events);
      return `
        <tr>
          <td>${dimension}</td>
          <td class="text-end">${formatNumber(events)}</td>
          <td class="text-end">${formatRate(renderErrorRate)}</td>
          <td class="text-end">${formatRate(fallbackRate)}</td>
          <td class="text-end">${formatRate(abandonRate)}</td>
        </tr>
      `;
    }).join('');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function statusBadgeClass(status) {
    switch (String(status || '').toLowerCase()) {
      case 'ok':
        return 'text-bg-success';
      case 'attention':
        return 'text-bg-warning';
      case 'off':
        return 'text-bg-secondary';
      case 'hold':
      default:
        return 'text-bg-danger';
    }
  }

  function statusLabel(status) {
    switch (String(status || '').toLowerCase()) {
      case 'ok':
        return 'OK';
      case 'attention':
        return 'Attention';
      case 'off':
        return 'Off';
      case 'hold':
      default:
        return 'Hold';
    }
  }

  function scorecardCategoryLabel(category) {
    switch (String(category || '').toLowerCase()) {
      case 'experiment':
        return 'Experiment';
      case 'guardrails':
        return 'Guardrails';
      case 'product_kpi':
        return 'Primary KPI';
      case 'product_outcome':
        return 'Outcome KPI';
      case 'workspace':
        return 'Workspace';
      case 'external_dependencies':
        return 'External gate';
      default:
        return 'Scorecard';
    }
  }

  function renderScorecard(scorecard) {
    if (!scorecardTable) {
      return;
    }
    const items = Array.isArray(scorecard?.items) ? scorecard.items : [];
    if (scorecardUpdatedAt) {
      scorecardUpdatedAt.textContent = scorecard?.generated_at
        ? `Сформировано: ${formatTimestamp(scorecard.generated_at)}`
        : '';
    }
    if (!items.length) {
      scorecardTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Scorecard пока недоступен.</td></tr>';
      return;
    }
    scorecardTable.innerHTML = items.map((item) => {
      const label = escapeHtml(item?.label || '—');
      const categoryLabel = escapeHtml(scorecardCategoryLabel(item?.category));
      const summary = escapeHtml(item?.summary || '');
      const note = escapeHtml(item?.note || '');
      const currentValue = escapeHtml(item?.current_value || '—');
      const threshold = escapeHtml(item?.threshold || '—');
      const measuredAt = escapeHtml(formatTimestamp(item?.measured_at || ''));
      const badgeClass = statusBadgeClass(item?.status);
      const badgeLabel = statusLabel(item?.status);
      const blocking = item?.blocking ? '<div class="small text-danger">Blocking gate</div>' : '<div class="small text-muted">Non-blocking</div>';
      return `
        <tr>
          <td>
            <div class="fw-semibold">${label}</div>
            <div class="small text-muted"><span class="badge text-bg-light border">${categoryLabel}</span></div>
            ${summary ? `<div class="small text-muted">${summary}</div>` : ''}
            ${note ? `<div class="small text-muted">Note: ${note}</div>` : ''}
          </td>
          <td><span class="badge ${badgeClass}">${badgeLabel}</span>${blocking}</td>
          <td class="small">${currentValue}</td>
          <td class="small">${threshold}</td>
          <td class="small text-nowrap">${measuredAt}</td>
        </tr>
      `;
    }).join('');
  }

  function renderPacket(packet) {
    const items = Array.isArray(packet?.items) ? packet.items : [];
    const status = String(packet?.status || 'off').toLowerCase();
    if (packetStatus) {
      packetStatus.className = `badge ${statusBadgeClass(status)}`;
      packetStatus.textContent = `Packet: ${statusLabel(status)}`;
    }
    if (packetUpdatedAt) {
      packetUpdatedAt.textContent = packet?.generated_at
        ? `Сформировано: ${formatTimestamp(packet.generated_at)}`
        : '';
    }
    if (packetSummary) {
      const missingItems = Array.isArray(packet?.missing_items) ? packet.missing_items : [];
      const summaryParts = [];
      if (packet?.summary) {
        summaryParts.push(String(packet.summary));
      }
      if (missingItems.length) {
        summaryParts.push(`Pending: ${missingItems.join(', ')}`);
      }
      if (latestPayload?.weekly_review_focus?.top_priority_key) {
        summaryParts.push(`weekly focus=${String(latestPayload.weekly_review_focus.top_priority_key)}`);
      }
      if (latestPayload?.weekly_review_focus?.focus_health) {
        summaryParts.push(`health=${String(latestPayload.weekly_review_focus.focus_health)}`);
      }
      if (latestPayload?.weekly_review_focus?.next_action_summary) {
        summaryParts.push(`next=${String(latestPayload.weekly_review_focus.next_action_summary)}`);
      }
      packetSummary.textContent = summaryParts.join(' · ');
      packetSummary.classList.toggle('d-none', summaryParts.length === 0);
      packetSummary.className = `alert ${status === 'ok' || status === 'off' ? 'alert-secondary' : 'alert-warning'} mb-3${summaryParts.length === 0 ? ' d-none' : ''}`;
    }
    if (packetTable) {
      if (!items.length) {
        packetTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Governance packet пока недоступен.</td></tr>';
      } else {
        packetTable.innerHTML = items.map((item) => {
          const label = escapeHtml(item?.label || item?.key || '—');
          const summary = escapeHtml(item?.summary || '');
          const note = escapeHtml(item?.note || '');
          const currentValue = escapeHtml(item?.current_value || '—');
          const threshold = escapeHtml(item?.threshold || '—');
          const measuredAt = escapeHtml(formatTimestamp(item?.measured_at || ''));
          return `
            <tr>
              <td>
                <div class="fw-semibold">${label}</div>
                ${summary ? `<div class="small text-muted">${summary}</div>` : ''}
                ${note ? `<div class="small text-muted">Note: ${note}</div>` : ''}
              </td>
              <td><span class="badge ${statusBadgeClass(item?.status)}">${statusLabel(item?.status)}</span></td>
              <td class="small">${currentValue}</td>
              <td class="small">${threshold}</td>
              <td class="small text-nowrap">${measuredAt}</td>
            </tr>
          `;
        }).join('');
      }
    }
    const ownerSignoff = packet?.owner_signoff || {};
    if (packetOwnerState) {
      if (ownerSignoff?.required !== true) {
        packetOwnerState.textContent = 'Не требуется';
      } else if (ownerSignoff?.timestamp_invalid === true) {
        packetOwnerState.textContent = 'Невалидная UTC-дата';
      } else if (ownerSignoff?.ready === true) {
        packetOwnerState.textContent = ownerSignoff?.signed_by ? `Подписал: ${ownerSignoff.signed_by}` : 'Подписано';
      } else {
        packetOwnerState.textContent = 'Ожидает sign-off';
      }
    }
    if (packetOwnerMeta) {
      const signedAt = ownerSignoff?.signed_at ? formatTimestamp(ownerSignoff.signed_at) : '—';
      const ttl = ownerSignoff?.ttl_hours ?? '—';
      const age = ownerSignoff?.age_hours ?? '—';
      packetOwnerMeta.textContent = `UTC: ${signedAt} · TTL: ${ttl}h · age: ${age}h`;
    }
    const paritySnapshot = packet?.parity_snapshot || {};
    if (packetParityState) {
      packetParityState.textContent = paritySnapshot?.ready === true
        ? `${formatRate(paritySnapshot.parity_ready_rate)} ready`
        : 'Snapshot недоступен';
    }
    if (packetParityMeta) {
      const topReasons = Array.isArray(paritySnapshot?.top_reasons)
        ? paritySnapshot.top_reasons.map((row) => `${row?.reason || 'unspecified'}(${row?.events || 0})`).join(', ')
        : '';
      packetParityMeta.textContent = `opens=${formatNumber(paritySnapshot.workspace_open_events)} · gaps=${formatNumber(paritySnapshot.parity_gap_events)}${topReasons ? ` · ${topReasons}` : ''}`;
    }
    const incidentHistory = packet?.incident_history || {};
    if (packetIncidentState) {
      packetIncidentState.textContent = `alerts=${formatNumber(incidentHistory.alert_count || 0)} · status=${String(incidentHistory.guardrail_status || 'ok')}`;
    }
    if (packetIncidentMeta) {
      packetIncidentMeta.textContent = `window=${formatNumber(incidentHistory.window_days || 0)}d UTC · render=${formatNumber(incidentHistory.render_error_alerts || 0)} · fallback=${formatNumber(incidentHistory.fallback_alerts || 0)} · abandon=${formatNumber(incidentHistory.abandon_alerts || 0)} · slow=${formatNumber(incidentHistory.slow_open_alerts || 0)}`;
    }
    const reviewCadence = packet?.review_cadence || {};
    if (packetReviewState) {
      if (reviewCadence?.enabled !== true) {
        packetReviewState.textContent = 'Не требуется';
      } else if (reviewCadence?.timestamp_invalid === true) {
        packetReviewState.textContent = 'Невалидная UTC-дата';
      } else if (reviewCadence?.criteria_ready === false) {
        packetReviewState.textContent = 'Review: критерии не закрыты';
      } else if (reviewCadence?.followup_after_non_go_ready === false) {
        packetReviewState.textContent = 'Review: нет incident follow-up для перехода в go';
      } else if (reviewCadence?.ready === true) {
        packetReviewState.textContent = reviewCadence?.reviewed_by ? `Reviewed by: ${reviewCadence.reviewed_by}` : 'Review подтверждён';
      } else {
        packetReviewState.textContent = 'Review просрочен';
      }
    }
    if (packetReviewMeta) {
      const reviewedAt = reviewCadence?.reviewed_at ? formatTimestamp(reviewCadence.reviewed_at) : '—';
      const cadenceDays = reviewCadence?.cadence_days ?? '—';
      const ageDays = reviewCadence?.age_days ?? '—';
      const confirmedEvents = reviewCadence?.confirmed_events_in_window ?? 0;
      const goEvents = reviewCadence?.decision_go_events_in_window ?? 0;
      const holdEvents = reviewCadence?.decision_hold_events_in_window ?? 0;
      const rollbackEvents = reviewCadence?.decision_rollback_events_in_window ?? 0;
      const followupLinkedEvents = reviewCadence?.incident_followup_linked_events_in_window ?? 0;
      const reviewNote = String(reviewCadence?.review_note || '').trim();
      const decisionAction = String(reviewCadence?.decision_action || '').trim().toLowerCase();
      const incidentFollowup = String(reviewCadence?.incident_followup || '').trim();
      const previousDecisionAction = String(reviewCadence?.previous_decision_action || '').trim().toLowerCase();
      const previousDecisionAt = reviewCadence?.previous_decision_at ? formatTimestamp(reviewCadence.previous_decision_at) : '';
      const followupAfterNonGoRequired = reviewCadence?.followup_after_non_go_required === true;
      const requiredCriteria = Array.isArray(reviewCadence?.required_criteria) ? reviewCadence.required_criteria.filter(Boolean) : [];
      const checkedCriteria = Array.isArray(reviewCadence?.checked_criteria) ? reviewCadence.checked_criteria.filter(Boolean) : [];
      const missingCriteria = Array.isArray(reviewCadence?.missing_criteria) ? reviewCadence.missing_criteria.filter(Boolean) : [];
      packetReviewMeta.textContent = `UTC: ${reviewedAt} · cadence: ${cadenceDays}d · age: ${ageDays}d · confirms: ${confirmedEvents} · go/hold/rollback: ${goEvents}/${holdEvents}/${rollbackEvents} · followup linked: ${followupLinkedEvents}${decisionAction ? ` · decision: ${decisionAction}` : ''}${followupAfterNonGoRequired ? ' · go-after-non-go-followup=required' : ''}${previousDecisionAction ? ` · prev decision: ${previousDecisionAction}${previousDecisionAt ? `@${previousDecisionAt}` : ''}` : ''}${requiredCriteria.length ? ` · criteria required: ${requiredCriteria.join('|')}` : ''}${checkedCriteria.length ? ` · checked: ${checkedCriteria.join('|')}` : ''}${missingCriteria.length ? ` · missing: ${missingCriteria.join('|')}` : ''}${reviewNote ? ` · note: ${reviewNote}` : ''}${incidentFollowup ? ` · incident: ${incidentFollowup}` : ''}`;
    }
    if (reviewByInput) {
      reviewByInput.value = reviewCadence?.reviewed_by || '';
    }
    if (reviewAtInput) {
      reviewAtInput.value = toDateTimeLocalValue(reviewCadence?.reviewed_at);
    }
    if (reviewNoteInput) {
      reviewNoteInput.value = String(reviewCadence?.review_note || '').trim();
    }
    if (reviewDecisionActionInput) {
      const decisionAction = String(reviewCadence?.decision_action || '').trim().toLowerCase();
      reviewDecisionActionInput.value = ['go', 'hold', 'rollback'].includes(decisionAction) ? decisionAction : '';
    }
    if (reviewIncidentFollowupInput) {
      reviewIncidentFollowupInput.value = String(reviewCadence?.incident_followup || '').trim();
    }
    if (reviewRequiredCriteriaInput) {
      const requiredCriteria = Array.isArray(reviewCadence?.required_criteria) ? reviewCadence.required_criteria.filter(Boolean) : [];
      reviewRequiredCriteriaInput.value = requiredCriteria.join(', ');
    }
    if (reviewCheckedCriteriaInput) {
      const checkedCriteria = Array.isArray(reviewCadence?.checked_criteria) ? reviewCadence.checked_criteria.filter(Boolean) : [];
      reviewCheckedCriteriaInput.value = checkedCriteria.join(', ');
    }
    const parityExit = packet?.parity_exit_criteria || {};
    if (packetExitState) {
      if (parityExit?.enabled !== true) {
        packetExitState.textContent = 'Не требуется';
      } else if (parityExit?.ready === true) {
        packetExitState.textContent = 'Exit criteria выполнен';
      } else {
        packetExitState.textContent = `Critical gaps: ${formatNumber(parityExit?.critical_gap_events || 0)}`;
      }
    }
    if (packetExitMeta) {
      const lastSeenAt = parityExit?.last_seen_at ? formatTimestamp(parityExit.last_seen_at) : '—';
      const topReasons = Array.isArray(parityExit?.top_reasons)
        ? parityExit.top_reasons.map((row) => `${row?.reason || 'unspecified'}(${row?.events || 0})`).join(', ')
        : '';
      packetExitMeta.textContent = `window=${formatNumber(parityExit?.window_days || 0)}d UTC · last=${lastSeenAt}${topReasons ? ` · ${topReasons}` : ''}`;
    }
    const legacyOnlyScenarios = Array.isArray(packet?.legacy_only_scenarios) ? packet.legacy_only_scenarios : [];
    const legacyInventory = packet?.legacy_only_inventory || {};
    if (packetLegacyState) {
      if (!legacyOnlyScenarios.length) {
        packetLegacyState.textContent = 'Legacy-only gaps не заявлены';
      } else if (legacyInventory?.managed === true) {
        packetLegacyState.textContent = `${legacyOnlyScenarios.length} open, sunset plan assigned`;
      } else {
        packetLegacyState.textContent = `${legacyOnlyScenarios.length} open, attention required`;
      }
    }
    if (packetLegacyMeta) {
      const reviewedAt = legacyInventory?.reviewed_at ? formatTimestamp(legacyInventory.reviewed_at) : '—';
      const reviewedBy = String(legacyInventory?.reviewed_by || '').trim() || '—';
      const note = String(legacyInventory?.review_note || '').trim();
      const invalid = legacyInventory?.review_timestamp_invalid === true ? ' · invalid_utc' : '';
      const managedCount = Number(legacyInventory?.managed_count || 0);
      const unmanagedCount = Number(legacyInventory?.unmanaged_count || 0);
      const overdueCount = Number(legacyInventory?.deadline_overdue_count || 0);
      packetLegacyMeta.textContent = legacyOnlyScenarios.length
        ? `${legacyOnlyScenarios.join(', ')} · managed=${managedCount}/${legacyOnlyScenarios.length} · unmanaged=${unmanagedCount}${overdueCount > 0 ? ` · overdue=${overdueCount}` : ''} · reviewed=${reviewedBy} @ ${reviewedAt}${invalid}${note ? ` · note: ${note}` : ''}`
        : `Инвентарь пуст — legacy modal можно удерживать только как rollback. reviewed=${reviewedBy} @ ${reviewedAt}${invalid}${note ? ` · note: ${note}` : ''}`;
    }
    if (legacyScenariosInput) {
      legacyScenariosInput.value = legacyOnlyScenarios.join(', ');
    }
    if (legacyReviewedByInput) {
      legacyReviewedByInput.value = String(legacyInventory?.reviewed_by || '').trim();
    }
    if (legacyReviewedAtInput) {
      legacyReviewedAtInput.value = toDateTimeLocalValue(legacyInventory?.reviewed_at);
    }
    if (legacyReviewNoteInput) {
      legacyReviewNoteInput.value = String(legacyInventory?.review_note || '').trim();
    }
    if (legacyScenarioMetadataInput) {
      const details = Array.isArray(legacyInventory?.scenario_details) ? legacyInventory.scenario_details : [];
      const metadata = {};
      details.forEach((item) => {
        const scenario = String(item?.scenario || '').trim();
        if (!scenario) {
          return;
        }
        metadata[scenario] = {
          owner: String(item?.owner || '').trim(),
          deadline_at_utc: String(item?.deadline_at_utc || '').trim(),
          note: String(item?.note || '').trim(),
        };
      });
      legacyScenarioMetadataInput.value = Object.keys(metadata).length > 0
        ? JSON.stringify(metadata, null, 2)
        : '';
    }
    if (legacyInventorySummary) {
      const actionItems = Array.isArray(legacyInventory?.action_items) ? legacyInventory.action_items : [];
      const parts = [
        `managed=${formatNumber(legacyInventory?.managed_count || 0)}/${formatNumber(legacyInventory?.open_count || 0)} (${formatNumber(legacyInventory?.managed_coverage_pct || 0)}%)`,
        `owner=${formatNumber(legacyInventory?.owner_coverage_pct || 0)}%`,
        `deadline=${formatNumber(legacyInventory?.deadline_coverage_pct || 0)}%`,
      ];
      if (Number(legacyInventory?.deadline_overdue_count || 0) > 0) {
        parts.push(`overdue=${formatNumber(legacyInventory.deadline_overdue_count)} (${formatNumber(legacyInventory?.deadline_overdue_pct || 0)}%)`);
      }
      if (legacyInventory?.repeat_review_required === true) {
        const reviewReason = formatLegacyRepeatReviewReason(legacyInventory?.repeat_review_reason);
        const overdueDays = Number(legacyInventory?.repeat_review_overdue_days || 0);
        parts.push(`repeat review=${reviewReason || 'required'}${overdueDays > 0 ? ` (+${formatNumber(overdueDays)}d)` : ''}`);
      } else if (legacyInventory?.repeat_review_due_at_utc) {
        parts.push(`review due=${formatTimestamp(legacyInventory.repeat_review_due_at_utc)}`);
      }
      if (legacyInventory?.review_queue_followup_required === true) {
        parts.push(`queue follow-up=${formatNumber(legacyInventory?.review_queue_count || 0)} cyc=${formatNumber(legacyInventory?.review_queue_repeat_cycles || 0)}`);
      } else if (Number(legacyInventory?.review_queue_count || 0) > 0) {
        parts.push(`queue=${formatNumber(legacyInventory.review_queue_count)}`);
      }
      if (legacyInventory?.review_queue_escalation_required === true) {
        parts.push(`queue pressure=${String(legacyInventory?.review_queue_closure_pressure || 'high')}`);
        if (Number(legacyInventory?.review_queue_escalated_count || 0) > 0) {
          parts.push(`mgmt=${formatNumber(legacyInventory?.review_queue_escalated_count || 0)}`);
        }
      }
      if (Number(legacyInventory?.review_queue_consolidation_count || 0) > 0) {
        parts.push(`consolidate=${formatNumber(legacyInventory?.review_queue_consolidation_count || 0)}`);
      }
      if (actionItems.length) {
        parts.push(`next: ${actionItems[0]}`);
      } else if (legacyInventory?.review_queue_next_action_summary) {
        parts.push(`next: ${String(legacyInventory.review_queue_next_action_summary)}`);
      }
      legacyInventorySummary.textContent = parts.join(' · ');
    }
    if (legacyQueueSummary) {
      const details = Array.isArray(legacyInventory?.scenario_details) ? legacyInventory.scenario_details : [];
      const queueDetails = details
        .filter((item) => item?.queue_candidate === true)
        .slice(0, 4)
        .map((item) => {
          const scenario = String(item?.scenario || 'n/a');
          const tags = [];
          if (item?.escalation_candidate === true) {
            tags.push('escalate');
          } else if (item?.consolidation_candidate === true) {
            tags.push('consolidate');
          } else {
            tags.push('follow-up');
          }
          if (item?.owner) {
            tags.push(`owner=${String(item.owner)}`);
          }
          if (item?.deadline_at_utc) {
            tags.push(`due=${formatTimestamp(item.deadline_at_utc)}`);
          } else if (item?.deadline_timestamp_invalid === true) {
            tags.push('due=invalid_utc');
          } else {
            tags.push('due=missing');
          }
          return `${scenario} [${tags.join(', ')}]`;
        });
      if (queueDetails.length) {
        legacyQueueSummary.textContent = `Queue map: ${queueDetails.join(' · ')}${Number(legacyInventory?.review_queue_count || 0) > queueDetails.length ? ` · +${Number(legacyInventory?.review_queue_count || 0) - queueDetails.length} more` : ''}`;
      } else if (legacyInventory?.review_queue_management_review_summary) {
        legacyQueueSummary.textContent = String(legacyInventory.review_queue_management_review_summary);
      } else {
        legacyQueueSummary.textContent = '';
      }
    }
    const legacyUsagePolicy = packet?.legacy_usage_policy || {};
if (packetLegacyUsageState) {
  if (legacyUsagePolicy?.enabled !== true) {
    packetLegacyUsageState.textContent = 'Не требуется';
  } else if (legacyUsagePolicy?.review_timestamp_invalid === true) {
    packetLegacyUsageState.textContent = 'Невалидная UTC-дата';
  } else if (legacyUsagePolicy?.ready === true) {
    packetLegacyUsageState.textContent = 'Policy подтверждена';
  } else {
    packetLegacyUsageState.textContent = 'Policy требует review';
  }
}
if (packetLegacyUsageMeta) {
  const reviewedAt = legacyUsagePolicy?.reviewed_at ? formatTimestamp(legacyUsagePolicy.reviewed_at) : '—';
  const reviewedBy = String(legacyUsagePolicy?.reviewed_by || '').trim() || '—';
  const share = Number(legacyUsagePolicy?.manual_legacy_share_pct || 0).toFixed(1);
  const maxShare = legacyUsagePolicy?.max_manual_legacy_share_pct;
  const minWorkspaceOpens = legacyUsagePolicy?.min_workspace_open_events;
  const volumeReady = legacyUsagePolicy?.volume_ready;
  const maxShareDelta = legacyUsagePolicy?.max_manual_legacy_share_delta_pct;
  const shareDelta = Number(legacyUsagePolicy?.manual_legacy_share_delta_pct || 0).toFixed(1);
  const trendReady = legacyUsagePolicy?.trend_ready;
  const previousShare = Number(legacyUsagePolicy?.previous_window_manual_legacy_share_pct || 0).toFixed(1);
  const maxBlockedShareDelta = legacyUsagePolicy?.max_manual_legacy_blocked_share_delta_pct;
  const blockedShareDelta = Number(legacyUsagePolicy?.manual_legacy_blocked_share_delta_pct || 0).toFixed(1);
  const blockedTrendReady = legacyUsagePolicy?.blocked_trend_ready;
  const previousBlockedShare = Number(legacyUsagePolicy?.previous_window_manual_legacy_blocked_share_pct || 0).toFixed(1);
  const note = String(legacyUsagePolicy?.review_note || '').trim();
  const policyEvents = Number(legacyUsagePolicy?.policy_updated_events_in_window || 0);
  const decision = String(legacyUsagePolicy?.decision || '').trim();
  const reasonsTop = Array.isArray(legacyUsagePolicy?.manual_legacy_reasons_top) ? legacyUsagePolicy.manual_legacy_reasons_top : [];
  const reasonsSummary = reasonsTop.length
    ? reasonsTop.slice(0, 3).map((row) => `${String(row?.reason || 'unspecified')}:${Number(row?.events || 0)}`).join(', ')
    : '';
  const blockedReasonsTop = Array.isArray(legacyUsagePolicy?.manual_legacy_blocked_reasons_top) ? legacyUsagePolicy.manual_legacy_blocked_reasons_top : [];
  const blockedReasonsSummary = blockedReasonsTop.length
    ? blockedReasonsTop.slice(0, 3).map((row) => `${String(row?.reason || 'unspecified')}:${Number(row?.events || 0)}`).join(', ')
    : '';
  const blockedReasonsReviewRequired = legacyUsagePolicy?.blocked_reasons_review_required === true;
  const blockedReasonsTopN = Number(legacyUsagePolicy?.blocked_reasons_top_n || 0);
  const blockedReasonsReviewed = Array.isArray(legacyUsagePolicy?.blocked_reasons_reviewed) ? legacyUsagePolicy.blocked_reasons_reviewed : [];
  const blockedReasonsMissing = Array.isArray(legacyUsagePolicy?.blocked_reasons_missing) ? legacyUsagePolicy.blocked_reasons_missing : [];
  const blockedReasonsReviewReady = legacyUsagePolicy?.blocked_reasons_review_ready === true;
  const blockedReasonsFollowup = String(legacyUsagePolicy?.blocked_reasons_followup || '').trim();
  const unknownEvents = Number(legacyUsagePolicy?.unknown_manual_reason_events || 0);
  packetLegacyUsageMeta.textContent = `manual legacy share=${share}%${maxShare !== null && maxShare !== undefined && maxShare !== '' ? ` (max ${maxShare}%)` : ''}${minWorkspaceOpens !== null && minWorkspaceOpens !== undefined && minWorkspaceOpens !== '' ? ` · min opens=${minWorkspaceOpens} (${volumeReady === true ? 'ok' : 'hold'})` : ''}${maxShareDelta !== null && maxShareDelta !== undefined && maxShareDelta !== '' ? ` · share delta=${shareDelta}pp vs prev ${previousShare}% (max +${maxShareDelta}pp, ${trendReady === true ? 'ok' : 'hold'})` : ''}${maxBlockedShareDelta !== null && maxBlockedShareDelta !== undefined && maxBlockedShareDelta !== '' ? ` · blocked delta=${blockedShareDelta}pp vs prev ${previousBlockedShare}% (max +${maxBlockedShareDelta}pp, ${blockedTrendReady === true ? 'ok' : 'hold'})` : ''}${reasonsSummary ? ` · manual reasons: ${reasonsSummary}` : ''}${blockedReasonsSummary ? ` · blocked reasons: ${blockedReasonsSummary}` : ''}${blockedReasonsReviewRequired ? ` · blocked review top-${blockedReasonsTopN || 3}: ${blockedReasonsReviewReady ? 'ok' : `hold${blockedReasonsMissing.length ? ` (${blockedReasonsMissing.join(', ')})` : ''}`}` : ''}${unknownEvents > 0 ? ` · unknown reasons: ${unknownEvents}` : ''} · reviewed=${reviewedBy} @ ${reviewedAt}${decision ? ` · decision: ${decision}` : ''} · policy updates: ${policyEvents}${blockedReasonsFollowup ? ` · blocked follow-up: ${blockedReasonsFollowup}` : ''}${note ? ` · note: ${note}` : ''}`;
}
if (legacyUsageReviewedByInput) {
  legacyUsageReviewedByInput.value = String(legacyUsagePolicy?.reviewed_by || '').trim();
}
if (legacyUsageReviewedAtInput) {
  legacyUsageReviewedAtInput.value = toDateTimeLocalValue(legacyUsagePolicy?.reviewed_at);
}
if (legacyUsageMaxSharePctInput) {
  const maxShare = legacyUsagePolicy?.max_manual_legacy_share_pct;
  legacyUsageMaxSharePctInput.value = maxShare === null || maxShare === undefined || maxShare === '' ? '' : String(maxShare);
}
if (legacyUsageMinWorkspaceOpensInput) {
  const minWorkspaceOpens = legacyUsagePolicy?.min_workspace_open_events;
  legacyUsageMinWorkspaceOpensInput.value = minWorkspaceOpens === null || minWorkspaceOpens === undefined || minWorkspaceOpens === '' ? '' : String(minWorkspaceOpens);
}
if (legacyUsageMaxShareDeltaPctInput) {
  const maxShareDelta = legacyUsagePolicy?.max_manual_legacy_share_delta_pct;
  legacyUsageMaxShareDeltaPctInput.value = maxShareDelta === null || maxShareDelta === undefined || maxShareDelta === '' ? '' : String(maxShareDelta);
}
if (legacyUsageMaxBlockedShareDeltaPctInput) {
  const maxBlockedShareDelta = legacyUsagePolicy?.max_manual_legacy_blocked_share_delta_pct;
  legacyUsageMaxBlockedShareDeltaPctInput.value = maxBlockedShareDelta === null || maxBlockedShareDelta === undefined || maxBlockedShareDelta === '' ? '' : String(maxBlockedShareDelta);
}
if (legacyUsageDecisionInput) {
  const decision = String(legacyUsagePolicy?.decision || '').trim().toLowerCase();
  legacyUsageDecisionInput.value = ['go', 'hold'].includes(decision) ? decision : '';
}
if (legacyUsageReviewNoteInput) {
  legacyUsageReviewNoteInput.value = String(legacyUsagePolicy?.review_note || '').trim();
}
if (legacyUsageAllowedReasonsInput) {
  const allowedReasons = Array.isArray(legacyUsagePolicy?.allowed_reasons) ? legacyUsagePolicy.allowed_reasons : [];
  legacyUsageAllowedReasonsInput.value = allowedReasons.join(', ');
}
if (legacyUsageReasonCatalogRequiredInput) {
  legacyUsageReasonCatalogRequiredInput.checked = legacyUsagePolicy?.reason_catalog_required === true;
}
if (legacyUsageBlockedReasonsReviewedInput) {
  legacyUsageBlockedReasonsReviewedInput.value = blockedReasonsReviewed.join(', ');
}
if (legacyUsageBlockedReasonsFollowupInput) {
  legacyUsageBlockedReasonsFollowupInput.value = blockedReasonsFollowup;
}
    const contextContract = packet?.context_contract || {};
    const contextScenarios = Array.isArray(contextContract?.scenarios) ? contextContract.scenarios : [];
    const contextMandatoryFields = Array.isArray(contextContract?.mandatory_fields) ? contextContract.mandatory_fields : [];
    const contextMandatoryFieldsByScenario = contextContract?.mandatory_fields_by_scenario && typeof contextContract?.mandatory_fields_by_scenario === 'object'
      ? contextContract.mandatory_fields_by_scenario
      : {};
    const contextSourceOfTruthByScenario = contextContract?.source_of_truth_by_scenario && typeof contextContract?.source_of_truth_by_scenario === 'object'
      ? contextContract.source_of_truth_by_scenario
      : {};
    const contextSourceOfTruth = Array.isArray(contextContract?.source_of_truth) ? contextContract.source_of_truth : [];
    const contextEffectiveSourceOfTruth = Array.isArray(contextContract?.effective_source_of_truth) ? contextContract.effective_source_of_truth : [];
    const contextPriorityBlocksByScenario = contextContract?.priority_blocks_by_scenario && typeof contextContract?.priority_blocks_by_scenario === 'object'
      ? contextContract.priority_blocks_by_scenario
      : {};
    const contextPriorityBlocks = Array.isArray(contextContract?.priority_blocks) ? contextContract.priority_blocks : [];
    const contextEffectivePriorityBlocks = Array.isArray(contextContract?.effective_priority_blocks) ? contextContract.effective_priority_blocks : [];
    if (packetContextState) {
      if (contextContract?.enabled !== true) {
        packetContextState.textContent = 'Не требуется';
      } else if (contextContract?.review_timestamp_invalid === true) {
        packetContextState.textContent = 'Невалидная UTC-дата';
      } else if (contextContract?.ready === true) {
        packetContextState.textContent = 'Контракт подтверждён';
      } else {
        packetContextState.textContent = 'Контракт неполный';
      }
    }
    if (packetContextMeta) {
      const reviewedAt = contextContract?.reviewed_at ? formatTimestamp(contextContract.reviewed_at) : '—';
      const reviewedBy = String(contextContract?.reviewed_by || '').trim() || '—';
      const ttl = Number(contextContract?.review_ttl_hours || 0);
      const playbookCount = Number(contextContract?.playbook_count || 0);
      const playbookExpectedCount = Number(contextContract?.playbook_expected_count || 0);
      const playbookCoveredCount = Number(contextContract?.playbook_covered_count || 0);
      const playbookCoveragePct = Number(contextContract?.playbook_coverage_pct || 0);
      packetContextMeta.textContent = `scenarios=${contextScenarios.length} · fields=${contextMandatoryFields.length} · scenario profiles=${Object.keys(contextMandatoryFieldsByScenario).length} · sources=${contextSourceOfTruth.length} (+scenario ${Object.keys(contextSourceOfTruthByScenario).length}, effective ${contextEffectiveSourceOfTruth.length}) · blocks=${contextPriorityBlocks.length} (+scenario ${Object.keys(contextPriorityBlocksByScenario).length}, effective ${contextEffectivePriorityBlocks.length}) · playbooks=${playbookCount}${playbookExpectedCount > 0 ? ` (${playbookCoveredCount}/${playbookExpectedCount}, ${playbookCoveragePct}%)` : ''}${contextContract?.secondary_noise_followup_required === true ? ` · secondary-noise=${String(contextContract?.secondary_noise_usage_level || 'followup')}` : ''}${contextContract?.extra_attributes_compaction_candidate === true ? ` · extra-attrs=${formatNumber(contextContract?.extra_attributes_open_rate_pct || 0)}% / share ${formatNumber(contextContract?.extra_attributes_share_pct_of_secondary || 0)}%` : ''} · reviewed=${reviewedBy} @ ${reviewedAt}${ttl > 0 ? ` · ttl=${ttl}h` : ''}`;
    }
    if (contextActionSummary) {
      const focusBlocks = Array.isArray(contextContract?.operator_focus_blocks) ? contextContract.operator_focus_blocks : [];
      const definitionGaps = Array.isArray(contextContract?.definition_gaps) ? contextContract.definition_gaps : [];
      const actionItems = Array.isArray(contextContract?.action_items) ? contextContract.action_items : [];
      const operatorSummary = String(contextContract?.operator_summary || '').trim();
      const nextStepSummary = String(contextContract?.next_step_summary || '').trim();
      const parts = [];
      if (operatorSummary) {
        parts.push(operatorSummary);
      }
      if (focusBlocks.length) {
        parts.push(`operator first: ${focusBlocks.join(', ')}`);
      }
      if (definitionGaps.length) {
        parts.push(`missing: ${definitionGaps.join(', ')}`);
      }
      if (nextStepSummary) {
        parts.push(`next: ${nextStepSummary}`);
      } else if (actionItems.length) {
        parts.push(`next: ${actionItems[0]}`);
      }
      if (contextContract?.extra_attributes_compaction_candidate === true) {
        parts.push(String(contextContract?.secondary_noise_compaction_summary || contextContract?.extra_attributes_summary || '').trim());
      }
      contextActionSummary.textContent = parts.join(' · ');
    }
    if (contextNoiseSummary) {
      const currentSecondaryRate = Number(latestPayload?.totals?.context_secondary_details_open_rate_pct || 0);
      const previousSecondaryRate = Number(latestPayload?.previous_totals?.context_secondary_details_open_rate_pct || 0);
      const currentExtraRate = Number(contextContract?.extra_attributes_open_rate_pct || 0);
      const previousExtraRate = Number(latestPayload?.previous_totals?.context_extra_attributes_open_rate_pct || 0);
      const secondaryDelta = Math.round(currentSecondaryRate - previousSecondaryRate);
      const extraDelta = Math.round(currentExtraRate - previousExtraRate);
      const trendLabel = secondaryDelta >= 10
        ? 'rising'
        : secondaryDelta <= -10
          ? 'improving'
          : 'stable';
      const parts = [];
      if (contextContract?.secondary_noise_summary) {
        parts.push(String(contextContract.secondary_noise_summary).trim());
      }
      if (contextContract?.secondary_noise_compaction_summary) {
        parts.push(String(contextContract.secondary_noise_compaction_summary).trim());
      }
      parts.push(`trend=${trendLabel} (${currentSecondaryRate}% vs prev ${previousSecondaryRate}%, Δ ${secondaryDelta >= 0 ? '+' : ''}${secondaryDelta}pp)`);
      if (contextContract?.extra_attributes_compaction_candidate === true || currentExtraRate > 0 || previousExtraRate > 0) {
        parts.push(`extra-attrs ${currentExtraRate}% vs prev ${previousExtraRate}% (Δ ${extraDelta >= 0 ? '+' : ''}${extraDelta}pp)`);
      }
      if (contextContract?.secondary_noise_management_review_required === true) {
        parts.push('management review required');
      }
      contextNoiseSummary.textContent = parts.join(' · ');
    }
    if (contextRequiredInput) {
      contextRequiredInput.checked = contextContract?.required === true;
    }
    if (contextScenariosInput) {
      contextScenariosInput.value = contextScenarios.join(', ');
    }
    if (contextMandatoryFieldsInput) {
      contextMandatoryFieldsInput.value = contextMandatoryFields.join(', ');
    }
    if (contextScenarioMandatoryFieldsInput) {
      contextScenarioMandatoryFieldsInput.value = Object.keys(contextMandatoryFieldsByScenario).length > 0
        ? JSON.stringify(contextMandatoryFieldsByScenario, null, 2)
        : '';
    }
    if (contextSourceOfTruthInput) {
      contextSourceOfTruthInput.value = contextSourceOfTruth.join(', ');
    }
    if (contextScenarioSourceOfTruthInput) {
      contextScenarioSourceOfTruthInput.value = Object.keys(contextSourceOfTruthByScenario).length > 0
        ? JSON.stringify(contextSourceOfTruthByScenario, null, 2)
        : '';
    }
    if (contextPriorityBlocksInput) {
      contextPriorityBlocksInput.value = contextPriorityBlocks.join(', ');
    }
    if (contextScenarioPriorityBlocksInput) {
      contextScenarioPriorityBlocksInput.value = Object.keys(contextPriorityBlocksByScenario).length > 0
        ? JSON.stringify(contextPriorityBlocksByScenario, null, 2)
        : '';
    }
    if (contextPlaybooksInput) {
      const playbooks = contextContract?.playbooks && typeof contextContract.playbooks === 'object'
        ? contextContract.playbooks
        : {};
      contextPlaybooksInput.value = Object.keys(playbooks).length > 0
        ? JSON.stringify(playbooks, null, 2)
        : '';
    }
    if (contextReviewedByInput) {
      contextReviewedByInput.value = String(contextContract?.reviewed_by || '').trim();
    }
    if (contextReviewedAtInput) {
      contextReviewedAtInput.value = toDateTimeLocalValue(contextContract?.reviewed_at);
    }
    if (contextReviewNoteInput) {
      contextReviewNoteInput.value = String(contextContract?.review_note || '').trim();
    }
  }

  function formatIssueClassification(value) {
    switch (String(value || '').toLowerCase()) {
      case 'rollout_blocker':
        return 'Rollout blocker';
      case 'immediate_incident':
        return 'Immediate incident';
      case 'backlog_candidate':
      default:
        return 'Backlog candidate';
    }
  }

  function formatReviewPath(value) {
    switch (String(value || '').trim().toLowerCase()) {
      case 'light':
        return 'light';
      case 'standard':
        return 'standard';
      case 'strict':
        return 'strict';
      default:
        return 'custom';
    }
  }

  function sortGovernanceIssues(items) {
    const issues = Array.isArray(items) ? [...items] : [];
    const severityRank = (issue) => {
      const classification = String(issue?.classification || '').toLowerCase();
      const status = String(issue?.status || '').toLowerCase();
      if (classification === 'rollout_blocker' || status === 'hold') return 0;
      if (classification === 'immediate_incident') return 1;
      if (status === 'attention') return 2;
      return 3;
    };
    return issues.sort((left, right) => {
      const severityDiff = severityRank(left) - severityRank(right);
      if (severityDiff !== 0) {
        return severityDiff;
      }
      return String(left?.summary || left?.type || '').localeCompare(String(right?.summary || right?.type || ''), 'ru');
    });
  }

  function formatLegacyRepeatReviewReason(value) {
    switch (String(value || '').trim().toLowerCase()) {
      case 'overdue_commitments':
        return 'overdue commitments';
      case 'review_missing':
        return 'review missing';
      case 'review_stale':
        return 'review stale';
      default:
        return '';
    }
  }

  function renderSlaPolicyAudit(audit) {
    const status = String(audit?.status || 'off').toLowerCase();
    if (slaPolicyAuditStatus) {
      slaPolicyAuditStatus.className = `badge ${statusBadgeClass(status)}`;
      slaPolicyAuditStatus.textContent = `Audit: ${statusLabel(status)}`;
    }
    if (slaPolicyAuditUpdatedAt) {
      slaPolicyAuditUpdatedAt.textContent = audit?.generated_at
        ? `Сформировано: ${formatTimestamp(audit.generated_at)}`
        : '';
    }
    if (slaPolicyAuditSummary) {
      slaPolicyAuditSummary.textContent = audit?.summary || 'SLA policy audit недоступен.';
    }
    if (slaPolicyAuditMeta) {
      const slaPolicyReviewEvents = latestPayload?.totals?.workspace_sla_policy_review_updated_events ?? 0;
      slaPolicyAuditMeta.textContent = `rules=${formatNumber(audit?.rules_total || 0)} · critical candidates=${formatNumber(audit?.critical_candidates || 0)} · issues=${formatNumber(audit?.issues_total || 0)} · mode=${escapeHtml(audit?.orchestration_mode || '—')} · review updates=${formatNumber(slaPolicyReviewEvents)}`;
    }
    const layerCounts = audit?.layer_counts && typeof audit.layer_counts === 'object' ? audit.layer_counts : {};
    const layerEntries = Object.entries(layerCounts);
    if (slaPolicyAuditLayers) {
      slaPolicyAuditLayers.textContent = layerEntries.length
        ? layerEntries.map(([layer, count]) => `${layer}:${formatNumber(count)}`).join(' · ')
        : 'Layers не заданы';
    }
    if (slaPolicyAuditLayersMeta) {
      const requirements = audit?.requirements && typeof audit.requirements === 'object' ? audit.requirements : {};
      slaPolicyAuditLayersMeta.textContent = `require_layers=${requirements.require_layers === true ? 'on' : 'off'} · require_owner=${requirements.require_owner === true ? 'on' : 'off'} · require_review=${requirements.require_review === true ? `on (${formatNumber(requirements.review_ttl_hours || 0)}h)` : 'off'} · governance_path=${formatReviewPath(requirements.governance_review_path)} · governance_review=${requirements.governance_review_required === true ? `on (${formatNumber(requirements.governance_review_ttl_hours || 0)}h)` : 'off'} · decision_required=${requirements.governance_decision_required === true ? 'on' : 'off'}`;
    }
    const preview = audit?.decision_preview && typeof audit.decision_preview === 'object' ? audit.decision_preview : {};
    const selectedByLayer = preview.selected_by_layer && typeof preview.selected_by_layer === 'object' ? preview.selected_by_layer : {};
    const selectedByRoute = preview.selected_by_route && typeof preview.selected_by_route === 'object' ? preview.selected_by_route : {};
    if (slaPolicyAuditPreview) {
      const previewEntries = Object.entries(selectedByLayer);
      slaPolicyAuditPreview.textContent = previewEntries.length
        ? previewEntries.map(([layer, count]) => `${layer}:${formatNumber(count)}`).join(' · ')
        : 'Preview routing недоступен';
    }
    if (slaPolicyAuditPreviewMeta) {
      const topRoutes = Object.entries(selectedByRoute)
        .sort((left, right) => Number(right[1] || 0) - Number(left[1] || 0))
        .slice(0, 3)
        .map(([route, count]) => `${route}:${formatNumber(count)}`);
      slaPolicyAuditPreviewMeta.textContent = topRoutes.length
        ? `top routes: ${topRoutes.join(', ')}`
        : 'Выбранные маршруты пока не определились.';
    }

    const issues = sortGovernanceIssues(audit?.issues);
    if (slaPolicyAuditIssueSummary) {
      const requirements = audit?.requirements && typeof audit.requirements === 'object' ? audit.requirements : {};
      const parts = [];
      if (audit?.summary) {
        parts.push(String(audit.summary));
      }
      if (audit?.weekly_review_summary) {
        parts.push(String(audit.weekly_review_summary));
      }
      if (audit?.minimum_required_review_path_summary) {
        parts.push(String(audit.minimum_required_review_path_summary));
      }
      if (issues.length) {
        parts.push(`Issues: ${issues.length}`);
      }
      if (requirements.governance_pre_review_conflicts_detected === true) {
        parts.push('pre-review conflicts detected');
      }
      parts.push(`conflicts_block=${requirements.block_on_conflicts === true ? 'on' : 'off'}`);
      slaPolicyAuditIssueSummary.textContent = parts.join(' · ');
      slaPolicyAuditIssueSummary.className = `alert ${status === 'hold' ? 'alert-danger' : (status === 'attention' ? 'alert-warning' : 'alert-secondary')} mb-3${parts.length ? '' : ' d-none'}`;
      slaPolicyAuditIssueSummary.classList.toggle('d-none', parts.length === 0);
    }
    if (slaPolicyAuditSummaryCompact) {
      const minimumPath = Array.isArray(audit?.minimum_required_review_path) ? audit.minimum_required_review_path : [];
      const advisory = Array.isArray(audit?.advisory_checkpoints) ? audit.advisory_checkpoints : [];
      const parts = [];
      if (audit?.weekly_review_priority) {
        parts.push(`weekly=${String(audit.weekly_review_priority)}`);
      }
      if (minimumPath.length) {
        parts.push(`required now: ${minimumPath.join(' -> ')}`);
      }
      if (audit?.minimum_required_review_path_ready === true) {
        parts.push('required path=ready');
      }
      if (audit?.cheap_review_path_confirmed === true) {
        parts.push('cheap path=confirmed');
      }
      parts.push(`closure=${formatNumber(audit?.required_checkpoint_ready_total || 0)}/${formatNumber(audit?.required_checkpoint_total || 0)} (${formatNumber(audit?.required_checkpoint_closure_rate_pct || 100)}%)`);
      if (Number(audit?.freshness_checkpoint_total || 0) > 0) {
        parts.push(`freshness=${formatNumber(audit?.freshness_checkpoint_ready_total || 0)}/${formatNumber(audit?.freshness_checkpoint_total || 0)}`);
      }
      parts.push(`mandatory=${formatNumber(audit?.mandatory_issue_total || 0)}`);
      parts.push(`advisory=${formatNumber(audit?.advisory_issue_total || 0)}`);
      parts.push(`noise=${formatNumber(audit?.noise_ratio_pct || 0)}% (${String(audit?.noise_level || 'controlled')})`);
      if (audit?.policy_churn_risk_level) {
        parts.push(`churn=${String(audit.policy_churn_risk_level)}`);
      }
      if (audit?.decision_lead_time_status) {
        parts.push(`lead=${String(audit.decision_lead_time_status)}`);
      }
      if (Number(latestPayload?.totals?.workspace_sla_policy_churn_ratio_pct || 0) > 0) {
        parts.push(`telemetry churn=${formatNumber(latestPayload.totals.workspace_sla_policy_churn_ratio_pct)}% (${String(latestPayload?.totals?.workspace_sla_policy_churn_level || 'controlled')})`);
      }
      if (advisory.length) {
        parts.push(`noise filter: ${advisory.slice(0, 2).join(', ')}`);
      }
      slaPolicyAuditSummaryCompact.textContent = parts.join(' · ');
    }
    if (slaPolicyAuditIssuesTable) {
      if (!issues.length) {
        slaPolicyAuditIssuesTable.innerHTML = '<tr><td colspan="4" class="text-success text-center py-3">Governance-gap по SLA policy не найдено.</td></tr>';
      } else {
        slaPolicyAuditIssuesTable.innerHTML = issues.map((issue) => {
          const ticketList = Array.isArray(issue?.tickets) && issue.tickets.length
            ? ` · tickets: ${issue.tickets.join(', ')}`
            : '';
          const related = Array.isArray(issue?.related) && issue.related.length
            ? `<div class="small text-muted">related: ${escapeHtml(issue.related.join(', '))}</div>`
            : '';
          return `
            <tr>
              <td>
                <div class="fw-semibold">${escapeHtml(issue?.summary || issue?.type || '—')}</div>
                <div class="small text-muted">${escapeHtml(issue?.type || '')}${ticketList ? escapeHtml(ticketList) : ''}</div>
                ${related}
              </td>
              <td class="small">${escapeHtml(issue?.rule_id || '—')}</td>
              <td><span class="badge ${statusBadgeClass(issue?.status)}">${escapeHtml(formatIssueClassification(issue?.classification))}</span></td>
              <td class="small">${escapeHtml(issue?.detail || '—')}</td>
            </tr>
          `;
        }).join('');
      }
    }
    const rules = Array.isArray(audit?.rules) ? audit.rules : [];
    if (slaPolicyAuditRulesTable) {
      if (!rules.length) {
        slaPolicyAuditRulesTable.innerHTML = '<tr><td colspan="7" class="text-muted text-center py-3">Routing rules для audit не заданы.</td></tr>';
      } else {
        slaPolicyAuditRulesTable.innerHTML = rules.map((rule) => {
          const issuesList = Array.isArray(rule?.issues) && rule.issues.length
            ? `<div class="small text-muted">${escapeHtml(rule.issues.join(', '))}</div>`
            : '';
          const owner = String(rule?.owner || '').trim();
          const reviewedAt = String(rule?.reviewed_at_utc || '').trim();
          const reviewMeta = [];
          if (owner) reviewMeta.push(`owner=${owner}`);
          if (reviewedAt) reviewMeta.push(`UTC ${formatTimestamp(reviewedAt)}`);
          if (rule?.reviewed_at_invalid_utc === true) reviewMeta.push('invalid_utc');
          return `
            <tr>
              <td>
                <div class="fw-semibold">${escapeHtml(rule?.rule_id || '—')}</div>
                <div class="small text-muted">${escapeHtml(rule?.route || '')}</div>
                ${issuesList}
              </td>
              <td class="small">${escapeHtml(rule?.layer || 'legacy')}</td>
              <td><span class="badge ${statusBadgeClass(rule?.status)}">${statusLabel(rule?.status)}</span></td>
              <td class="text-end">${formatNumber(rule?.matched_candidates)}</td>
              <td class="text-end">${formatNumber(rule?.selected_candidates)}</td>
              <td class="text-end">${formatRate(rule?.coverage_rate)}</td>
              <td class="small">${escapeHtml(reviewMeta.join(' · ') || 'owner/review не заданы')}</td>
            </tr>
          `;
        }).join('');
      }
    }
    const governanceReview = audit?.governance_review && typeof audit.governance_review === 'object' ? audit.governance_review : {};
    if (slaPolicyReviewedByInput) {
      slaPolicyReviewedByInput.value = String(governanceReview?.reviewed_by || '').trim();
    }
    if (slaPolicyReviewedAtInput) {
      slaPolicyReviewedAtInput.value = toDateTimeLocalValue(governanceReview?.reviewed_at_utc);
    }
    if (slaPolicyReviewNoteInput) {
      slaPolicyReviewNoteInput.value = String(governanceReview?.review_note || '').trim();
    }
    if (slaPolicyDryRunTicketIdInput) {
      slaPolicyDryRunTicketIdInput.value = String(governanceReview?.dry_run_ticket_id || '').trim();
    }
    if (slaPolicyDecisionInput) {
      const decision = String(governanceReview?.decision || '').trim().toLowerCase();
      slaPolicyDecisionInput.value = ['go', 'hold'].includes(decision) ? decision : '';
    }
    if (slaPolicyReviewMeta) {
      const parts = [`path=${formatReviewPath(governanceReview?.review_path)}`];
      if (governanceReview?.policy_changed_at_utc) {
        parts.push(`policy changed: ${formatTimestamp(governanceReview.policy_changed_at_utc)}`);
      }
      if (Number(governanceReview?.decision_lead_time_hours) >= 0) {
        parts.push(`lead time=${formatNumber(governanceReview.decision_lead_time_hours)}h`);
      } else if (Number(governanceReview?.decision_lead_time_active_hours) >= 0) {
        parts.push(`lead time pending=${formatNumber(governanceReview.decision_lead_time_active_hours)}h`);
      }
      if (audit?.decision_lead_time_status) {
        parts.push(`status=${String(audit.decision_lead_time_status)}`);
      }
      if (governanceReview?.policy_changed_after_review === true) {
        parts.push('review outdated after policy change');
      }
      if (governanceReview?.pre_review_conflicts_detected === true) {
        parts.push(`conflicts: rules=${formatNumber(governanceReview.pre_review_conflicting_rules || 0)}, tickets=${formatNumber(governanceReview.pre_review_conflicting_tickets || 0)}`);
      }
      slaPolicyReviewMeta.textContent = parts.join(' · ');
    }
  }

  function renderMacroGovernanceAudit(audit) {
    const status = String(audit?.status || 'off').toLowerCase();
    if (macroGovernanceStatus) {
      macroGovernanceStatus.className = `badge ${statusBadgeClass(status)}`;
      macroGovernanceStatus.textContent = `Audit: ${statusLabel(status)}`;
    }
    if (macroGovernanceUpdatedAt) {
      macroGovernanceUpdatedAt.textContent = audit?.generated_at
        ? `Сформировано: ${formatTimestamp(audit.generated_at)}`
        : '';
    }
    if (macroGovernanceSummary) {
      macroGovernanceSummary.textContent = audit?.summary || 'Macro governance audit недоступен.';
    }
    if (macroGovernanceMeta) {
      const reviewUpdates = latestPayload?.totals?.workspace_macro_governance_review_updated_events ?? 0;
      const externalPolicyUpdates = latestPayload?.totals?.workspace_macro_external_catalog_policy_updated_events ?? 0;
      const deprecationPolicyUpdates = latestPayload?.totals?.workspace_macro_deprecation_policy_updated_events ?? 0;
      macroGovernanceMeta.textContent = `templates=${formatNumber(audit?.templates_total || 0)} · active=${formatNumber(audit?.published_active_total || 0)} · deprecated=${formatNumber(audit?.deprecated_total || 0)} · red-list=${formatNumber(audit?.red_list_total || 0)} · owner actions=${formatNumber(audit?.owner_action_total || 0)} · issues=${formatNumber(audit?.issues_total || 0)} · review updates=${formatNumber(reviewUpdates)} · external policy updates=${formatNumber(externalPolicyUpdates)} · deprecation policy updates=${formatNumber(deprecationPolicyUpdates)}`;
    }
    const requirements = audit?.requirements && typeof audit.requirements === 'object' ? audit.requirements : {};
    const governanceReview = audit?.governance_review && typeof audit.governance_review === 'object' ? audit.governance_review : {};
    const externalCatalog = audit?.external_catalog_contract && typeof audit.external_catalog_contract === 'object'
      ? audit.external_catalog_contract
      : {};
    const deprecationPolicy = audit?.deprecation_policy && typeof audit.deprecation_policy === 'object'
      ? audit.deprecation_policy
      : {};
    if (macroGovernanceRequirements) {
      macroGovernanceRequirements.textContent = `owner=${requirements.require_owner === true ? 'required' : 'optional'} · namespace=${requirements.require_namespace === true ? 'required' : 'optional'}`;
    }
    if (macroGovernanceRequirementsMeta) {
      macroGovernanceRequirementsMeta.textContent = `review=${requirements.require_review === true ? `required (${formatNumber(requirements.review_ttl_hours || 0)}h)` : 'optional'} · deprecation_reason=${requirements.deprecation_requires_reason === true ? 'required' : 'optional'} · red_list=${requirements.red_list_enabled === true ? `on (usage<=${formatNumber(requirements.red_list_usage_max || 0)})` : 'off'} · owner_action=${requirements.owner_action_required === true ? 'on' : 'off'} · tier_sla=${requirements.usage_tier_sla_required === true ? `on (low<=${formatNumber(requirements.usage_tier_low_max || 0)}, medium<=${formatNumber(requirements.usage_tier_medium_max || 0)})` : 'off'}`;
    }
    if (macroGovernanceCleanup) {
      macroGovernanceCleanup.textContent = `unused=${formatNumber(audit?.unused_published_total || 0)} · stale_review=${formatNumber(audit?.stale_review_total || 0)} · aliases=${formatNumber(audit?.alias_cleanup_total || 0)} · variables=${formatNumber(audit?.variable_cleanup_total || 0)} · cleanup_overdue=${formatNumber(audit?.cleanup_sla_overdue_total || 0)} · deprecation_overdue=${formatNumber(audit?.deprecation_sla_overdue_total || 0)}`;
    }
    if (macroGovernanceCleanupMeta) {
      const externalCatalogMeta = externalCatalog.required === true
        ? (externalCatalog.ready === true ? 'ready' : 'gap')
        : 'optional';
      const deprecationPolicyMeta = deprecationPolicy.required === true
        ? (deprecationPolicy.ready === true ? 'ready' : 'gap')
        : 'optional';
      macroGovernanceCleanupMeta.textContent = `invalid_review=${formatNumber(audit?.invalid_review_total || 0)} · window=${formatNumber(requirements.unused_days || 0)}d UTC · cadence=${formatNumber(requirements.cleanup_cadence_days || 0)}d · cleanup_sla=${formatNumber(requirements.cleanup_sla_low_days || 0)}/${formatNumber(requirements.cleanup_sla_medium_days || 0)}/${formatNumber(requirements.cleanup_sla_high_days || 0)}d · deprecation_sla=${formatNumber(requirements.deprecation_sla_low_days || 0)}/${formatNumber(requirements.deprecation_sla_medium_days || 0)}/${formatNumber(requirements.deprecation_sla_high_days || 0)}d · deprecation_gaps=${formatNumber(audit?.deprecation_gap_total || 0)} · governance_review=${governanceReview.required === true ? (governanceReview.ready === true ? 'ready' : 'gap') : 'optional'} · external_catalog=${externalCatalogMeta} · deprecation_policy=${deprecationPolicyMeta}`;
    }
    const issues = sortGovernanceIssues(audit?.issues);
    if (macroGovernanceIssueSummary) {
      const parts = [];
      if (audit?.summary) {
        parts.push(String(audit.summary));
      }
      if (audit?.weekly_review_summary) {
        parts.push(String(audit.weekly_review_summary));
      }
      if (issues.length) {
        parts.push(`Issues: ${issues.length}`);
      }
      if (Number(audit?.red_list_total || 0) > 0) {
        parts.push(`Red-list: ${formatNumber(audit.red_list_total)}`);
      }
      if (Number(audit?.low_signal_advisory_total || 0) > 0) {
        parts.push(`Low-signal: ${formatNumber(audit.low_signal_advisory_total)}`);
      }
      if (Number(audit?.owner_action_total || 0) > 0) {
        parts.push(`Owner actions: ${formatNumber(audit.owner_action_total)}`);
      }
      const lowSignalTemplates = Array.isArray(audit?.low_signal_red_list_templates) ? audit.low_signal_red_list_templates : [];
      if (lowSignalTemplates.length) {
        parts.push(`analytics-only candidates: ${lowSignalTemplates.slice(0, 2).join(', ')}`);
      }
      macroGovernanceIssueSummary.textContent = parts.join(' · ');
      macroGovernanceIssueSummary.className = `alert ${status === 'hold' ? 'alert-danger' : (status === 'attention' ? 'alert-warning' : 'alert-secondary')} mb-3${parts.length ? '' : ' d-none'}`;
      macroGovernanceIssueSummary.classList.toggle('d-none', parts.length === 0);
    }
    if (macroGovernanceSummaryCompact) {
      const required = Array.isArray(audit?.minimum_required_checkpoints) ? audit.minimum_required_checkpoints : [];
      const advisory = Array.isArray(audit?.advisory_signals) ? audit.advisory_signals : [];
      const parts = [];
      if (audit?.weekly_review_priority) {
        parts.push(`weekly=${String(audit.weekly_review_priority)}`);
      }
      if (required.length) {
        parts.push(`required now: ${required.join(' -> ')}`);
      }
      parts.push(`closure=${formatNumber(audit?.required_checkpoint_ready_total || 0)}/${formatNumber(audit?.required_checkpoint_total || 0)} (${formatNumber(audit?.required_checkpoint_closure_rate_pct || 100)}%)`);
      if (Number(audit?.freshness_checkpoint_total || 0) > 0) {
        parts.push(`freshness=${formatNumber(audit?.freshness_checkpoint_ready_total || 0)}/${formatNumber(audit?.freshness_checkpoint_total || 0)}`);
      }
      parts.push(`mandatory=${formatNumber(audit?.mandatory_issue_total || 0)}`);
      parts.push(`advisory=${formatNumber(audit?.advisory_issue_total || 0)}`);
      if (Number(audit?.actionable_advisory_total || 0) > 0 || Number(audit?.low_signal_advisory_total || 0) > 0) {
        parts.push(`actionable=${formatNumber(audit?.actionable_advisory_total || 0)}`);
        parts.push(`low-signal=${formatNumber(audit?.low_signal_advisory_total || 0)}`);
      }
      parts.push(`noise=${formatNumber(audit?.noise_ratio_pct || 0)}% (${String(audit?.noise_level || 'controlled')})`);
      if (Number(audit?.advisory_noise_excluding_low_signal_pct || 0) > 0) {
        parts.push(`noise*=${formatNumber(audit?.advisory_noise_excluding_low_signal_pct || 0)}%`);
      }
      if (audit?.advisory_followup_required === true) {
        parts.push('follow-up=yes');
      }
      if (advisory.length) {
        parts.push(`noise filter: ${advisory.slice(0, 2).join(', ')}`);
      }
      macroGovernanceSummaryCompact.textContent = parts.join(' · ');
    }
    if (macroGovernanceIssuesTable) {
      if (!issues.length) {
        macroGovernanceIssuesTable.innerHTML = '<tr><td colspan="4" class="text-success text-center py-3">Governance-gap по macro templates не найдено.</td></tr>';
      } else {
        macroGovernanceIssuesTable.innerHTML = issues.map((issue) => `
          <tr>
            <td>
              <div class="fw-semibold">${escapeHtml(issue?.summary || issue?.type || '—')}</div>
              <div class="small text-muted">${escapeHtml(issue?.type || '')}</div>
            </td>
            <td class="small">${escapeHtml(issue?.template_name || issue?.template_id || '—')}</td>
            <td><span class="badge ${statusBadgeClass(issue?.status)}">${escapeHtml(formatIssueClassification(issue?.classification))}</span></td>
            <td class="small">${escapeHtml(issue?.detail || '—')}</td>
          </tr>
        `).join('');
      }
    }
    const templates = Array.isArray(audit?.templates) ? audit.templates : [];
    if (macroGovernanceTemplatesTable) {
      if (!templates.length) {
        macroGovernanceTemplatesTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Macro templates для audit не заданы.</td></tr>';
      } else {
        macroGovernanceTemplatesTable.innerHTML = templates.map((template) => {
          const reviewMeta = [];
          if (template?.reviewed_at_invalid_utc === true) reviewMeta.push('invalid_utc');
          if (Number(template?.review_age_hours) >= 0) reviewMeta.push(`age=${formatNumber(template.review_age_hours)}h`);
          const cleanupMeta = [];
          if (template?.deprecated === true) cleanupMeta.push('deprecated');
          if (template?.deprecation_reason) cleanupMeta.push(`reason=${String(template.deprecation_reason)}`);
          if (template?.red_list_candidate === true) cleanupMeta.push(`red-list:${Array.isArray(template?.red_list_reasons) ? template.red_list_reasons.join('|') : 'candidate'}`);
          if (template?.usage_tier) cleanupMeta.push(`tier=${String(template.usage_tier)}`);
          if (Number(template?.cleanup_due_in_days) >= 0) cleanupMeta.push(`cleanup_due_in=${formatNumber(template.cleanup_due_in_days)}d`);
          if (String(template?.cleanup_sla_status || '').toLowerCase() === 'hold') cleanupMeta.push('cleanup_sla=overdue');
          if (Number(template?.deprecation_due_in_days) >= 0) cleanupMeta.push(`deprecation_due_in=${formatNumber(template.deprecation_due_in_days)}d`);
          if (String(template?.deprecation_sla_status || '').toLowerCase() === 'hold') cleanupMeta.push('deprecation_sla=overdue');
          if (template?.owner_action_required === true) cleanupMeta.push(`owner_action=${String(template?.owner_action_status || 'attention')}`);
          if (Number(template?.owner_action_due_in_days) >= 0) cleanupMeta.push(`due_in=${formatNumber(template.owner_action_due_in_days)}d`);
          if (Number(template?.duplicate_alias_count) > 0) cleanupMeta.push(`duplicate_aliases=${formatNumber(template.duplicate_alias_count)}`);
          if (Number(template?.unknown_variable_count) > 0) cleanupMeta.push(`unknown_vars=${formatNumber(template.unknown_variable_count)}`);
          const issueList = Array.isArray(template?.issues) && template.issues.length
            ? `<div class="small text-muted">${escapeHtml(template.issues.join(', '))}</div>`
            : '';
          const usageMeta = [];
          if (Number(template?.preview_count || 0) > 0) usageMeta.push(`preview=${formatNumber(template.preview_count)}`);
          if (Number(template?.error_count || 0) > 0) usageMeta.push(`errors=${formatNumber(template.error_count)}`);
          return `
            <tr>
              <td>
                <div class="fw-semibold">${escapeHtml(template?.template_name || template?.template_id || '—')}</div>
                <div class="small text-muted">${escapeHtml(template?.template_id || '')}</div>
                ${issueList}
              </td>
              <td><span class="badge ${statusBadgeClass(template?.status)}">${statusLabel(template?.status)}</span></td>
              <td class="small">${escapeHtml([template?.owner ? `owner=${template.owner}` : '', template?.namespace ? `ns=${template.namespace}` : ''].filter(Boolean).join(' · ') || 'owner/namespace не заданы')}</td>
              <td class="small">${escapeHtml(template?.reviewed_at_utc ? formatTimestamp(template.reviewed_at_utc) : '—')}${reviewMeta.length ? `<div class="small text-muted">${escapeHtml(reviewMeta.join(' · '))}</div>` : ''}</td>
              <td class="text-end">${formatNumber(template?.usage_count || 0)}${template?.last_used_at_utc ? `<div class="small text-muted text-start">${escapeHtml(formatTimestamp(template.last_used_at_utc))}</div>` : ''}${usageMeta.length ? `<div class="small text-muted text-start">${escapeHtml(usageMeta.join(' · '))}</div>` : ''}</td>
              <td class="small">${escapeHtml(cleanupMeta.join(' · ') || 'active')}</td>
            </tr>
          `;
        }).join('');
      }
    }
    if (macroGovernanceReviewedByInput) {
      macroGovernanceReviewedByInput.value = String(governanceReview?.reviewed_by || '').trim();
    }
    if (macroGovernanceReviewedAtInput) {
      macroGovernanceReviewedAtInput.value = toDateTimeLocalValue(governanceReview?.reviewed_at_utc);
    }
    if (macroGovernanceReviewNoteInput) {
      macroGovernanceReviewNoteInput.value = String(governanceReview?.review_note || '').trim();
    }
    if (macroGovernanceCleanupTicketIdInput) {
      macroGovernanceCleanupTicketIdInput.value = String(governanceReview?.cleanup_ticket_id || '').trim();
    }
    if (macroGovernanceDecisionInput) {
      const decision = String(governanceReview?.decision || '').trim().toLowerCase();
      macroGovernanceDecisionInput.value = ['go', 'hold'].includes(decision) ? decision : '';
    }
    if (macroExternalCatalogVerifiedByInput) {
      macroExternalCatalogVerifiedByInput.value = String(externalCatalog?.verified_by || '').trim();
    }
    if (macroExternalCatalogVerifiedAtInput) {
      macroExternalCatalogVerifiedAtInput.value = toDateTimeLocalValue(externalCatalog?.verified_at_utc);
    }
    if (macroExternalCatalogExpectedVersionInput) {
      macroExternalCatalogExpectedVersionInput.value = String(externalCatalog?.expected_version || '').trim();
    }
    if (macroExternalCatalogObservedVersionInput) {
      macroExternalCatalogObservedVersionInput.value = String(externalCatalog?.observed_version || '').trim();
    }
    if (macroExternalCatalogDecisionInput) {
      const decision = String(externalCatalog?.decision || '').trim().toLowerCase();
      macroExternalCatalogDecisionInput.value = ['go', 'hold'].includes(decision) ? decision : '';
    }
    if (macroExternalCatalogReviewTtlHoursInput) {
      macroExternalCatalogReviewTtlHoursInput.value = Number(externalCatalog?.review_ttl_hours || 0) > 0
        ? String(Number(externalCatalog.review_ttl_hours))
        : '';
    }
    if (macroExternalCatalogReviewNoteInput) {
      macroExternalCatalogReviewNoteInput.value = String(externalCatalog?.review_note || '').trim();
    }
    if (macroDeprecationReviewedByInput) {
      macroDeprecationReviewedByInput.value = String(deprecationPolicy?.reviewed_by || '').trim();
    }
    if (macroDeprecationReviewedAtInput) {
      macroDeprecationReviewedAtInput.value = toDateTimeLocalValue(deprecationPolicy?.reviewed_at_utc);
    }
    if (macroDeprecationDecisionInput) {
      const decision = String(deprecationPolicy?.decision || '').trim().toLowerCase();
      macroDeprecationDecisionInput.value = ['go', 'hold'].includes(decision) ? decision : '';
    }
    if (macroDeprecationReviewTtlHoursInput) {
      macroDeprecationReviewTtlHoursInput.value = Number(deprecationPolicy?.review_ttl_hours || 0) > 0
        ? String(Number(deprecationPolicy.review_ttl_hours))
        : '';
    }
    if (macroDeprecationTicketIdInput) {
      macroDeprecationTicketIdInput.value = String(deprecationPolicy?.deprecation_ticket_id || '').trim();
    }
    if (macroDeprecationReviewNoteInput) {
      macroDeprecationReviewNoteInput.value = String(deprecationPolicy?.review_note || '').trim();
    }
  }

  function buildRiskRows(payload, filters) {
    const rows = [];
    filteredRows(payload?.by_shift, 'shift', filters).forEach((row) => {
      rows.push({ source: 'shift', segment: row?.shift || '—', ...row });
    });
    filteredRows(payload?.by_team, 'team', filters).forEach((row) => {
      rows.push({ source: 'team', segment: row?.team || '—', ...row });
    });
    return rows
      .map((row) => {
        const events = Number(row?.events || 0);
        const renderErrorRate = toPercentRate(row?.render_errors, events);
        const fallbackRate = toPercentRate(row?.fallbacks, events);
        const abandonRate = toPercentRate(row?.abandons, events);
        const riskScore = (renderErrorRate + fallbackRate + abandonRate) * 100;
        return {
          label: `${row.source === 'team' ? 'team' : 'shift'}:${row.segment}`,
          events,
          renderErrorRate,
          fallbackRate,
          abandonRate,
          riskScore,
        };
      })
      .filter((row) => row.events > 0)
      .sort((a, b) => b.riskScore - a.riskScore)
      .slice(0, 6);
  }

  function renderRiskSegments(payload, filters) {
    if (!riskSegmentsTable) {
      return;
    }
    const rows = buildRiskRows(payload, filters);
    if (rows.length === 0) {
      riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Недостаточно данных для построения risk-сегментов.</td></tr>';
      return;
    }
    riskSegmentsTable.innerHTML = rows.map((row) => `
      <tr>
        <td>${row.label}</td>
        <td class="text-end">${formatNumber(row.events)}</td>
        <td class="text-end">${formatRate(row.renderErrorRate)}</td>
        <td class="text-end">${formatRate(row.fallbackRate)}</td>
        <td class="text-end">${formatRate(row.abandonRate)}</td>
        <td class="text-end fw-semibold">${row.riskScore.toFixed(2)}</td>
      </tr>
    `).join('');
  }

  function normalizeGapRows(rows) {
    if (!Array.isArray(rows)) {
      return [];
    }
    return rows.filter((row) => row && typeof row === 'object');
  }

  function formatGapReason(reason) {
    const normalized = String(reason || '').trim();
    if (!normalized) {
      return 'unspecified';
    }
    if (normalized.includes(',')) {
      return normalized
        .split(',')
        .map((item) => formatGapReason(item))
        .filter(Boolean)
        .join('; ');
    }
    if (normalized.startsWith('mandatory_field:')) {
      return `Mandatory field: ${normalized.slice('mandatory_field:'.length)}`;
    }
    if (normalized.startsWith('source_of_truth:')) {
      const payload = normalized.slice('source_of_truth:'.length);
      const [field, source, status] = payload.split(':');
      return `Source of truth: ${field || 'field'} via ${source || 'source'}${status ? ` (${status})` : ''}`;
    }
    if (normalized.startsWith('priority_block:')) {
      return `Priority block: ${normalized.slice('priority_block:'.length)}`;
    }
    if (normalized.startsWith('field:')) {
      const payload = normalized.slice('field:'.length);
      const [field, status] = payload.split(':');
      return status ? `Field: ${field} (${status})` : `Field: ${field}`;
    }
    if (normalized.startsWith('segment:')) {
      return `Segment: ${normalized.slice('segment:'.length)}`;
    }
    return normalized;
  }

  function renderGapBreakdownTable(tableNode, rows, emptyText) {
    if (!tableNode) {
      return;
    }
    const safeRows = normalizeGapRows(rows);
    if (safeRows.length === 0) {
      tableNode.innerHTML = `<tr><td colspan="4" class="text-muted text-center py-3">${emptyText}</td></tr>`;
      return;
    }
    tableNode.innerHTML = safeRows.map((row) => `
      <tr>
        <td>${escapeHtml(formatGapReason(row?.reason))}</td>
        <td class="text-end">${formatNumber(row?.events)}</td>
        <td class="text-end">${formatNumber(row?.tickets)}</td>
        <td class="small text-nowrap">${escapeHtml(formatTimestamp(row?.last_seen_at || ''))}</td>
      </tr>
    `).join('');
  }

  function getFilters() {
    return {
      scope: scopeSelect?.value || 'all',
      segment: (segmentInput?.value || '').trim().toLowerCase(),
    };
  }

  function isAlertVisible(alert, filters) {
    if (!alert || typeof alert !== 'object') {
      return false;
    }
    const scope = String(alert.scope || '').toLowerCase();
    const segment = String(alert.segment || '').toLowerCase();
    if (filters.scope === 'global' && scope !== 'global') {
      return false;
    }
    if (filters.scope === 'shift' && scope !== 'shift') {
      return false;
    }
    if (filters.scope === 'team' && scope !== 'team') {
      return false;
    }
    if (filters.segment && !segment.includes(filters.segment)) {
      return false;
    }
    return true;
  }

  function renderAlerts(alerts, filters) {
    const visibleAlerts = Array.isArray(alerts)
      ? alerts.filter((alert) => isAlertVisible(alert, filters))
      : [];
    if (visibleAlerts.length === 0) {
      alertsTable.innerHTML = '<tr><td colspan="4" class="text-success text-center py-3">Отклонений guardrails не обнаружено.</td></tr>';
      return visibleAlerts;
    }

    alertsTable.innerHTML = visibleAlerts.map((alert) => {
      const context = alert.segment ? `${alert.scope || 'segment'}: ${alert.segment}` : 'global';
      return `
        <tr>
          <td><span class="fw-semibold">${alert.metric || 'metric'}</span><div class="small text-muted">${alert.message || ''}</div></td>
          <td>${formatRate(alert.actual)}</td>
          <td>${formatRate(alert.threshold)}</td>
          <td class="small">${context}</td>
        </tr>
      `;
    }).join('');
    return visibleAlerts;
  }

  function filteredRows(rows, dimensionField, filters) {
    if (!Array.isArray(rows)) {
      return [];
    }
    if (!filters.segment) {
      return rows;
    }
    return rows.filter((row) => String(row?.[dimensionField] || '').toLowerCase().includes(filters.segment));
  }

  function renderFilterState(filters, visibleAlertsCount) {
    if (!filterState) {
      return;
    }
    const parts = [];
    if (filters.scope !== 'all') {
      parts.push(`scope=${filters.scope}`);
    }
    if (filters.segment) {
      parts.push(`segment~${filters.segment}`);
    }
    if (parts.length === 0) {
      filterState.textContent = `Показаны все guardrail-alerts (${visibleAlertsCount}).`;
      return;
    }
    filterState.textContent = `Фильтры: ${parts.join(', ')} · alerts: ${visibleAlertsCount}.`;
  }

  function exportGuardrailsCsv(payload, filters) {
    const alerts = Array.isArray(payload?.guardrails?.alerts)
      ? payload.guardrails.alerts.filter((alert) => isAlertVisible(alert, filters))
      : [];
    const scorecardItems = Array.isArray(payload?.rollout_scorecard?.items)
      ? payload.rollout_scorecard.items
      : [];
    const rows = [
      ['generated_at', payload?.generated_at || ''],
      ['window_days', payload?.window_days || ''],
      ['window_from_utc', payload?.window_from_utc || ''],
      ['window_to_utc', payload?.window_to_utc || ''],
      ['scope_filter', filters.scope],
      ['segment_filter', filters.segment || ''],
      [],
      ['scorecard_key', 'scorecard_status', 'blocking', 'current_value', 'threshold', 'measured_at_utc', 'note'],
      ...scorecardItems.map((item) => [
        item?.key || '',
        item?.status || '',
        item?.blocking === true ? 'true' : 'false',
        item?.current_value || '',
        item?.threshold || '',
        item?.measured_at || '',
        item?.note || '',
      ]),
      [],
      ['packet_key', 'packet_status', 'blocking', 'current_value', 'threshold', 'measured_at_utc', 'note'],
      ...(Array.isArray(payload?.rollout_packet?.items) ? payload.rollout_packet.items : []).map((item) => [
        item?.key || '',
        item?.status || '',
        item?.blocking === true ? 'true' : 'false',
        item?.current_value || '',
        item?.threshold || '',
        item?.measured_at || '',
        item?.note || '',
      ]),
      [],
      ['sla_rule_id', 'layer', 'status', 'matched_candidates', 'selected_candidates', 'coverage_rate', 'owner', 'reviewed_at_utc', 'issues'],
      ...(Array.isArray(payload?.sla_policy_audit?.rules) ? payload.sla_policy_audit.rules : []).map((rule) => [
        rule?.rule_id || '',
        rule?.layer || '',
        rule?.status || '',
        rule?.matched_candidates ?? '',
        rule?.selected_candidates ?? '',
        rule?.coverage_rate ?? '',
        rule?.owner || '',
        rule?.reviewed_at_utc || '',
        Array.isArray(rule?.issues) ? rule.issues.join('|') : '',
      ]),
      [],
      ['sla_issue_type', 'rule_id', 'classification', 'status', 'detail', 'tickets', 'related'],
      ...(Array.isArray(payload?.sla_policy_audit?.issues) ? payload.sla_policy_audit.issues : []).map((issue) => [
        issue?.type || '',
        issue?.rule_id || '',
        issue?.classification || '',
        issue?.status || '',
        issue?.detail || '',
        Array.isArray(issue?.tickets) ? issue.tickets.join('|') : '',
        Array.isArray(issue?.related) ? issue.related.join('|') : '',
      ]),
      [],
      ['macro_template_id', 'macro_template_name', 'status', 'published', 'deprecated', 'owner', 'namespace', 'reviewed_at_utc', 'usage_count', 'preview_count', 'error_count', 'last_used_at_utc', 'usage_tier', 'cleanup_sla_days', 'cleanup_due_in_days', 'cleanup_sla_status', 'deprecation_sla_days', 'deprecation_due_in_days', 'deprecation_sla_status', 'red_list_candidate', 'red_list_reasons', 'owner_action_required', 'owner_action_status', 'owner_action_due_in_days', 'duplicate_alias_count', 'unknown_variable_count', 'issues'],
      ...(Array.isArray(payload?.macro_governance_audit?.templates) ? payload.macro_governance_audit.templates : []).map((template) => [
        template?.template_id || '',
        template?.template_name || '',
        template?.status || '',
        template?.published === true ? 'true' : 'false',
        template?.deprecated === true ? 'true' : 'false',
        template?.owner || '',
        template?.namespace || '',
        template?.reviewed_at_utc || '',
        template?.usage_count ?? '',
        template?.preview_count ?? '',
        template?.error_count ?? '',
        template?.last_used_at_utc || '',
        template?.usage_tier || '',
        template?.cleanup_sla_days ?? '',
        template?.cleanup_due_in_days ?? '',
        template?.cleanup_sla_status || '',
        template?.deprecation_sla_days ?? '',
        template?.deprecation_due_in_days ?? '',
        template?.deprecation_sla_status || '',
        template?.red_list_candidate === true ? 'true' : 'false',
        Array.isArray(template?.red_list_reasons) ? template.red_list_reasons.join('|') : '',
        template?.owner_action_required === true ? 'true' : 'false',
        template?.owner_action_status || '',
        template?.owner_action_due_in_days ?? '',
        template?.duplicate_alias_count ?? '',
        template?.unknown_variable_count ?? '',
        Array.isArray(template?.issues) ? template.issues.join('|') : '',
      ]),
      [],
      ['macro_issue_type', 'template_id', 'template_name', 'classification', 'status', 'detail'],
      ...(Array.isArray(payload?.macro_governance_audit?.issues) ? payload.macro_governance_audit.issues : []).map((issue) => [
        issue?.type || '',
        issue?.template_id || '',
        issue?.template_name || '',
        issue?.classification || '',
        issue?.status || '',
        issue?.detail || '',
      ]),
      [],
      ['metric', 'actual_rate', 'threshold_rate', 'scope', 'segment', 'message'],
      ...alerts.map((alert) => [
        alert?.metric || '',
        alert?.actual ?? '',
        alert?.threshold ?? '',
        alert?.scope || '',
        alert?.segment || '',
        alert?.message || '',
      ]),
      [],
      ['gap_kind', 'reason', 'events', 'tickets', 'last_seen_at_utc'],
      ...[
        ['profile', payload?.gap_breakdown?.profile],
        ['source', payload?.gap_breakdown?.source],
        ['block', payload?.gap_breakdown?.block],
        ['parity', payload?.gap_breakdown?.parity],
      ].flatMap(([kind, rows]) => normalizeGapRows(rows).map((row) => [
        kind,
        row?.reason || '',
        row?.events ?? '',
        row?.tickets ?? '',
        row?.last_seen_at || '',
      ])),
    ];
    const csv = rows
      .map((row) => Array.isArray(row) ? row.map((cell) => escapeCsv(cell)).join(',') : '')
      .join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `workspace_guardrails_${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }


  function renderRolloutDecision(decision, cohortComparison) {
    if (!rolloutDecisionBox) {
      return;
    }
    if (!decision || typeof decision !== 'object') {
      rolloutDecisionBox.className = 'alert d-none mb-2';
      rolloutDecisionBox.textContent = '';
      if (kpiSignalState) {
        kpiSignalState.textContent = '';
      }
      return;
    }
    const action = String(decision.action || 'hold').toLowerCase();
    const winner = decision.winner || 'insufficient_data';
    const rationale = decision.rationale || 'Решение не сформировано.';
    const externalSignal = decision.external_kpi_signal && typeof decision.external_kpi_signal === 'object'
      ? decision.external_kpi_signal
      : {};

    let alertClass = 'alert-secondary';
    if (action === 'scale_up') {
      alertClass = 'alert-success';
    } else if (action === 'rollback') {
      alertClass = 'alert-danger';
    } else if (action === 'hold') {
      alertClass = 'alert-warning';
    }

    rolloutDecisionBox.className = `alert ${alertClass} mb-2`;
    const externalGateEnabled = Boolean(externalSignal.enabled);
    const externalGateReady = Boolean(externalSignal.ready_for_decision);
    const reviewLabel = isTimestampInvalid(externalSignal, 'review_timestamp_invalid')
      ? 'invalid_utc'
      : (externalSignal.review_present
        ? `${externalSignal.reviewed_by || 'n/a'} @ ${formatTimestamp(externalSignal.reviewed_at || '')} (${externalSignal.review_fresh ? 'fresh' : 'stale'})`
        : 'missing');
    const freshnessLabel = externalSignal.data_freshness_required
      ? (isTimestampInvalid(externalSignal, 'data_updated_timestamp_invalid')
        ? 'invalid_utc (hold)'
        : `${externalSignal.data_fresh ? 'fresh' : 'stale'}${externalSignal.data_updated_at ? ` @ ${formatTimestamp(externalSignal.data_updated_at)}` : ''}`)
      : 'off';
    const datamartOwner = String(externalSignal.datamart_owner || '').trim();
    const datamartRunbookUrl = String(externalSignal.datamart_runbook_url || '').trim();
    const datamartDependencyTicketUrl = String(externalSignal.datamart_dependency_ticket_url || '').trim();
    const datamartContext = [
      datamartOwner ? `owner=${datamartOwner}` : '',
      datamartRunbookUrl ? `runbook=${datamartRunbookUrl}` : '',
      datamartDependencyTicketUrl ? `dependency_ticket=${datamartDependencyTicketUrl}` : '',
    ].filter(Boolean).join(', ');
    const linksLabel = externalSignal.dashboard_links_required
      ? (externalSignal.dashboard_links_present ? 'ready' : 'missing')
      : 'off';
    const dashboardStatusLabel = externalSignal.dashboard_status_required
      ? `${externalSignal.dashboard_status || 'unknown'}${externalSignal.dashboard_status_ready ? '' : ' (hold)'}`
      : 'off';
    const ownerRunbookLabel = externalSignal.owner_runbook_required
      ? (externalSignal.owner_runbook_present ? 'ready' : 'missing')
      : 'off';
    const datamartHealthLabel = externalSignal.datamart_health_required
      ? `${externalSignal.datamart_health_status || 'unknown'}${externalSignal.datamart_health_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartHealthFreshnessLabel = externalSignal.datamart_health_freshness_required
      ? `${isTimestampInvalid(externalSignal, 'datamart_health_timestamp_invalid', 'datamart_health_updated_timestamp_invalid') ? 'invalid_utc' : (externalSignal.datamart_health_fresh ? 'fresh' : 'stale')}${externalSignal.datamart_health_updated_at ? ` @ ${formatTimestamp(externalSignal.datamart_health_updated_at)}` : ''}${externalSignal.datamart_health_freshness_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartTimelineLabel = externalSignal.datamart_timeline_required
      ? `${isTimestampInvalid(externalSignal, 'datamart_target_timestamp_invalid') ? 'invalid_utc' : (externalSignal.datamart_target_ready_at ? formatTimestamp(externalSignal.datamart_target_ready_at) : 'missing')}${externalSignal.datamart_timeline_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartProgramLabel = externalSignal.datamart_program_blocker_required
      ? `${externalSignal.datamart_program_status || 'unknown'}${externalSignal.datamart_program_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartProgramFreshnessLabel = externalSignal.datamart_program_freshness_required
      ? `${isTimestampInvalid(externalSignal, 'datamart_program_timestamp_invalid', 'datamart_program_updated_timestamp_invalid') ? 'invalid_utc' : (externalSignal.datamart_program_fresh ? 'fresh' : 'stale')}${externalSignal.datamart_program_updated_at ? ` @ ${formatTimestamp(externalSignal.datamart_program_updated_at)}` : ''}${externalSignal.datamart_program_freshness_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartDependencyTicketLabel = externalSignal.datamart_dependency_ticket_required
      ? (isTimestampInvalid(externalSignal, 'dependency_ticket_timestamp_invalid', 'datamart_dependency_ticket_updated_timestamp_invalid')
        ? 'invalid_utc (hold)'
        : (externalSignal.datamart_dependency_ticket_present ? 'ready' : 'missing (hold)'))
      : 'off';
    const datamartContractMissingMandatoryFields = Array.isArray(externalSignal.datamart_contract_missing_mandatory_fields)
      ? externalSignal.datamart_contract_missing_mandatory_fields.filter(Boolean).join('|')
      : '';
    const datamartContractMissingOptionalFields = Array.isArray(externalSignal.datamart_contract_missing_optional_fields)
      ? externalSignal.datamart_contract_missing_optional_fields.filter(Boolean).join('|')
      : '';
    const datamartContractOverlappingFields = Array.isArray(externalSignal.datamart_contract_overlapping_fields)
      ? externalSignal.datamart_contract_overlapping_fields.filter(Boolean).join('|')
      : '';
    const datamartContractAvailableOutsideFields = Array.isArray(externalSignal.datamart_contract_available_outside_fields)
      ? externalSignal.datamart_contract_available_outside_fields.filter(Boolean).join('|')
      : '';
    const datamartContractConfigurationConflict = externalSignal.datamart_contract_configuration_conflict === true;
    const mandatoryCoveragePct = Number.isFinite(Number(externalSignal.datamart_contract_mandatory_coverage_pct))
      ? Number(externalSignal.datamart_contract_mandatory_coverage_pct)
      : 0;
    const optionalCoveragePct = Number.isFinite(Number(externalSignal.datamart_contract_optional_coverage_pct))
      ? Number(externalSignal.datamart_contract_optional_coverage_pct)
      : 100;
    const blockingGapCount = Number.isFinite(Number(externalSignal.datamart_contract_blocking_gap_count))
      ? Number(externalSignal.datamart_contract_blocking_gap_count)
      : (datamartContractMissingMandatoryFields ? datamartContractMissingMandatoryFields.split('|').length : 0);
    const nonBlockingGapCount = Number.isFinite(Number(externalSignal.datamart_contract_non_blocking_gap_count))
      ? Number(externalSignal.datamart_contract_non_blocking_gap_count)
      : (datamartContractMissingOptionalFields ? datamartContractMissingOptionalFields.split('|').length : 0);
    const datamartContractGapSeverityRaw = String(externalSignal.datamart_contract_gap_severity || '').trim().toLowerCase();
    const datamartContractGapSeverity = datamartContractGapSeverityRaw || (blockingGapCount > 0 ? 'blocking' : (nonBlockingGapCount > 0 ? 'non_blocking' : 'none'));
    const optionalCoverageGateConfigured = Boolean(externalSignal.datamart_contract_optional_coverage_required);
    const optionalCoverageGateEnabled = externalSignal.datamart_contract_optional_coverage_gate_active === false
      ? false
      : optionalCoverageGateConfigured;
    const optionalCoverageGateThreshold = Number.isFinite(Number(externalSignal.datamart_contract_optional_min_coverage_pct))
      ? Number(externalSignal.datamart_contract_optional_min_coverage_pct)
      : null;
    const optionalCoverageGateReady = externalSignal.datamart_contract_optional_coverage_ready === false ? false : true;
    const datamartContractLabel = externalSignal.datamart_contract_required
      ? `v=${externalSignal.datamart_contract_version || 'v1'}, mandatory=${datamartContractMissingMandatoryFields ? `missing:${datamartContractMissingMandatoryFields}` : 'ready'} (${mandatoryCoveragePct}%), optional=${datamartContractMissingOptionalFields ? `missing:${datamartContractMissingOptionalFields}` : 'ready'} (${optionalCoveragePct}%), gaps=blocking:${blockingGapCount}/non_blocking:${nonBlockingGapCount} (${datamartContractGapSeverity}), overlap=${datamartContractOverlappingFields || 'none'}${datamartContractConfigurationConflict ? ' (conflict)' : ''}, outside_contract=${datamartContractAvailableOutsideFields || 'none'}, optional_gate=${optionalCoverageGateEnabled ? `on${optionalCoverageGateThreshold !== null ? `@${optionalCoverageGateThreshold}%` : ''}${optionalCoverageGateReady ? '' : ' (hold)'}` : (optionalCoverageGateConfigured ? 'configured_but_contract_off' : 'off')}${externalSignal.datamart_contract_ready ? '' : ' (hold)'}`
      : 'off';
    const datamartRiskLevel = String(externalSignal.datamart_risk_level || 'low').trim().toLowerCase();
    const datamartRiskReasons = Array.isArray(externalSignal.datamart_risk_reasons)
      ? externalSignal.datamart_risk_reasons.filter(Boolean).join('|')
      : '';
    const datamartHealthNote = String(externalSignal.datamart_health_note || '').trim();
    const externalGateSuffix = externalGateEnabled
      ? ` External KPI gate: ${externalGateReady ? 'ready' : 'hold'} (omnichannel=${externalSignal.omnichannel_ready ? 'ok' : 'pending'}, finance=${externalSignal.finance_ready ? 'ok' : 'pending'}, review=${reviewLabel}, freshness=${freshnessLabel}, links=${linksLabel}, dashboard_status=${dashboardStatusLabel}, owner/runbook=${ownerRunbookLabel}, datamart_health=${datamartHealthLabel}, datamart_health_freshness=${datamartHealthFreshnessLabel}, datamart_program_freshness=${datamartProgramFreshnessLabel}, datamart_timeline=${datamartTimelineLabel}, datamart_program=${datamartProgramLabel}, dependency_ticket=${datamartDependencyTicketLabel}, datamart_contract=${datamartContractLabel}, datamart_risk=${datamartRiskLevel}${datamartRiskReasons ? `:${datamartRiskReasons}` : ''}${datamartContext ? `, ${datamartContext}` : ''}${datamartHealthNote ? `, health_note=${datamartHealthNote}` : ''}${String(externalSignal.dashboard_status_note || '').trim() ? `, dashboard_note=${String(externalSignal.dashboard_status_note || '').trim()}` : ''}).`
      : '';
    rolloutDecisionBox.textContent = `Rollout decision: ${action}. Winner: ${winner}. ${rationale}${externalGateSuffix}`;

    if (!kpiSignalState) {
      return;
    }
    const kpiSignal = cohortComparison && typeof cohortComparison === 'object'
      ? (cohortComparison.kpi_signal || {})
      : {};
    const required = Array.isArray(kpiSignal.required_kpis) ? kpiSignal.required_kpis : [];
    const threshold = Number(kpiSignal.min_events_per_cohort || 0);
    const metrics = kpiSignal.metrics && typeof kpiSignal.metrics === 'object' ? kpiSignal.metrics : {};
    const chunks = required.map((name) => {
      const item = metrics[name] || {};
      return `${name.toUpperCase()}: control ${formatNumber(item.control || 0)} / test ${formatNumber(item.test || 0)}`;
    });
    if (!chunks.length) {
      kpiSignalState.textContent = 'KPI-signal: недоступен.';
      return;
    }
    const ready = Boolean(kpiSignal.ready_for_decision);
    kpiSignalState.textContent = `KPI-signal (${ready ? 'готов' : 'недостаточен'}, порог ${formatNumber(threshold)} на когорту): ${chunks.join(' · ')}.`;
  }

  function render(payload) {
    latestPayload = payload || {};
    const totals = payload?.totals || {};
    const p1Control = payload?.p1_operational_control && typeof payload.p1_operational_control === 'object'
      ? payload.p1_operational_control
      : {};
    Object.entries(metricNodes).forEach(([metric, node]) => {
      const value = totals[metric];
      if (metric === 'context_profile_ready_rate'
        || metric === 'context_profile_gap_rate'
        || metric === 'context_source_ready_rate'
        || metric === 'context_source_gap_rate'
        || metric === 'context_attribute_policy_ready_rate'
        || metric === 'context_attribute_policy_gap_rate'
        || metric === 'context_block_ready_rate'
        || metric === 'context_block_gap_rate'
        || metric === 'workspace_parity_ready_rate'
        || metric === 'workspace_parity_gap_rate') {
        node.textContent = formatRate(value);
        return;
      }
      node.textContent = formatNumber(value);
    });

    const guardrails = payload?.guardrails || {};
    const alerts = Array.isArray(guardrails.alerts) ? guardrails.alerts : [];
    const filters = getFilters();
    const status = guardrails.status === 'attention' ? 'attention' : 'ok';
    okBadge.classList.toggle('d-none', status !== 'ok');
    attentionBadge.classList.toggle('d-none', status !== 'attention');

    const windowFrom = formatTimestamp(payload?.window_from_utc || '');
    const windowTo = formatTimestamp(payload?.window_to_utc || '');
    const weeklyReviewFocus = payload?.weekly_review_focus && typeof payload.weekly_review_focus === 'object'
      ? payload.weekly_review_focus
      : {};
    const weeklyFocusSections = Array.isArray(weeklyReviewFocus?.sections) ? weeklyReviewFocus.sections : [];
    updatedAt.textContent = `Обновлено: ${formatTimestamp(payload?.generated_at)} · окно ${payload?.window_days || '—'} дн.`;
    if (windowFrom !== '—' && windowTo !== '—') {
      updatedAt.textContent = `Обновлено: ${formatTimestamp(payload?.generated_at)} · окно ${payload?.window_days || '—'} дн. · ${windowFrom} — ${windowTo}`;
    }
    if (Number(totals.context_secondary_details_expanded_events || 0) > 0 || Number(totals.workspace_sla_policy_review_updated_events || 0) > 0) {
      updatedAt.textContent += ` · secondary-context opens ${formatNumber(totals.context_secondary_details_expanded_events || 0)} (${formatNumber(totals.context_secondary_details_open_rate_pct || 0)}%, ${String(totals.context_secondary_details_usage_level || 'rare')}${totals.context_secondary_details_top_section ? `, top ${String(totals.context_secondary_details_top_section)}` : ''})`;
      if (Number(totals.context_extra_attributes_expanded_events || 0) > 0) {
        updatedAt.textContent += ` · extra-attrs ${formatNumber(totals.context_extra_attributes_expanded_events || 0)} (${formatNumber(totals.context_extra_attributes_open_rate_pct || 0)}%, ${String(totals.context_extra_attributes_usage_level || 'rare')}${totals.context_extra_attributes_compaction_candidate === true ? `, compact, share ${formatNumber(totals.context_extra_attributes_share_pct_of_secondary || 0)}%` : ''})`;
      }
      updatedAt.textContent += ` · SLA churn ${formatNumber(totals.workspace_sla_policy_churn_ratio_pct || 0)}% (${String(totals.workspace_sla_policy_churn_level || 'controlled')})`;
    }
    if (weeklyFocusSections.length) {
      updatedAt.textContent += ` · weekly focus ${weeklyFocusSections.map((item) => String(item?.key || '')).filter(Boolean).join(', ')} · top=${String(weeklyReviewFocus?.top_priority_key || 'n/a')} · score=${formatNumber(weeklyReviewFocus?.focus_score || 0)} · health=${String(weeklyReviewFocus?.focus_health || 'stable')}`;
    }
    if (p1Control?.status) {
      updatedAt.textContent += ` · p1=${String(p1Control.status)}${p1Control?.context_noise_trend_status ? ` (${String(p1Control.context_noise_trend_status)})` : ''}`;
    }
    const visibleAlerts = renderAlerts(alerts, filters);
    renderFilterState(filters, visibleAlerts.length);
    renderBreakdownRows(shiftTable, filteredRows(payload?.by_shift, 'shift', filters), 'shift', 'Недостаточно данных по сменам.');
    renderBreakdownRows(teamTable, filteredRows(payload?.by_team, 'team', filters), 'team', 'Недостаточно данных по командам.');
    renderRiskSegments(payload, filters);
    renderGapBreakdownTable(profileGapTable, payload?.gap_breakdown?.profile, 'Profile gaps не зафиксированы.');
    renderGapBreakdownTable(sourceGapTable, payload?.gap_breakdown?.source, 'Source gaps не зафиксированы.');
    renderGapBreakdownTable(attributePolicyGapTable, payload?.gap_breakdown?.attribute_policy, 'Attribute policy gaps не зафиксированы.');
    renderGapBreakdownTable(blockGapTable, payload?.gap_breakdown?.block, 'Block gaps не зафиксированы.');
    renderGapBreakdownTable(parityGapTable, payload?.gap_breakdown?.parity, 'Parity gaps не зафиксированы.');
    renderScorecard(payload?.rollout_scorecard);
    renderPacket(payload?.rollout_packet);
    renderSlaPolicyAudit(payload?.sla_policy_audit);
    renderMacroGovernanceAudit(payload?.macro_governance_audit);
    renderRolloutDecision(payload?.rollout_decision, payload?.cohort_comparison);

    if (status === 'attention') {
      const focusText = weeklyFocusSections.length
        ? ` Weekly focus: ${String(weeklyReviewFocus?.summary || '').trim()}${weeklyReviewFocus?.priority_mix_summary ? ` · ${String(weeklyReviewFocus.priority_mix_summary).trim()}` : ''}${weeklyReviewFocus?.next_action_summary ? ` · next: ${String(weeklyReviewFocus.next_action_summary).trim()}` : ''}${weeklyReviewFocus?.requires_management_review === true ? ' · management review suggested' : ''}`
        : '';
      const p1Text = p1Control?.status && p1Control.status !== 'controlled'
        ? ` P1 control: ${String(p1Control.summary || '').trim()}${p1Control?.next_action_summary ? ` · next: ${String(p1Control.next_action_summary).trim()}` : ''}`
        : '';
      alertBox.textContent = `Зафиксировано ${alerts.length} отклонений guardrails (${visibleAlerts.length} по текущему фильтру).${focusText}${p1Text}`;
      alertBox.classList.remove('d-none');
    } else if (weeklyFocusSections.length || (p1Control?.status && p1Control.status !== 'controlled')) {
      alertBox.textContent = weeklyFocusSections.length
        ? `Weekly focus: ${String(weeklyReviewFocus?.summary || '').trim()}${weeklyReviewFocus?.priority_mix_summary ? ` · ${String(weeklyReviewFocus.priority_mix_summary).trim()}` : ''}${weeklyReviewFocus?.next_action_summary ? ` · next: ${String(weeklyReviewFocus.next_action_summary).trim()}` : ''}${weeklyReviewFocus?.requires_management_review === true ? ' · management review suggested' : ''}`
        : `P1 control: ${String(p1Control?.summary || '').trim()}${p1Control?.next_action_summary ? ` · next: ${String(p1Control.next_action_summary).trim()}` : ''}`;
      alertBox.classList.remove('d-none');
    } else {
      alertBox.classList.add('d-none');
    }
  }

  async function loadTelemetry() {
    setLoadingState();
    const params = new URLSearchParams();
    params.set('days', daysSelect.value || '7');
    const experimentName = (experimentInput.value || '').trim();
    if (experimentName) {
      params.set('experiment_name', experimentName);
    }
    try {
      const fromUtcIso = dateTimeLocalToUtcIso(fromUtcInput?.value || '');
      if (fromUtcIso === null) {
        throw new Error('Поле "from UTC" содержит невалидную дату.');
      }
      const toUtcIso = dateTimeLocalToUtcIso(toUtcInput?.value || '');
      if (toUtcIso === null) {
        throw new Error('Поле "to UTC" содержит невалидную дату.');
      }
      if (fromUtcIso) {
        params.set('from_utc', fromUtcIso);
      }
      if (toUtcIso) {
        params.set('to_utc', toUtcIso);
      }
      const response = await fetch(`/api/dialogs/workspace-telemetry/summary?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      render(payload || {});
    } catch (error) {
      alertBox.textContent = `Не удалось загрузить telemetry summary: ${error.message}`;
      alertBox.classList.remove('d-none');
      updatedAt.textContent = 'Ошибка загрузки telemetry.';
      alertsTable.innerHTML = '<tr><td colspan="4" class="text-danger text-center py-3">Ошибка загрузки данных. Проверьте доступ к /api/dialogs/workspace-telemetry/summary.</td></tr>';
      shiftTable.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Данные по сменам недоступны.</td></tr>';
      teamTable.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Данные по командам недоступны.</td></tr>';
      if (rolloutDecisionBox) {
        rolloutDecisionBox.className = 'alert alert-danger mb-2';
        rolloutDecisionBox.textContent = 'Rollout decision недоступен из-за ошибки загрузки telemetry.';
      }
      if (kpiSignalState) {
        kpiSignalState.textContent = '';
      }
      if (scorecardUpdatedAt) {
        scorecardUpdatedAt.textContent = '';
      }
      if (scorecardTable) {
        scorecardTable.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Scorecard недоступен.</td></tr>';
      }
      if (packetStatus) {
        packetStatus.className = 'badge text-bg-danger';
        packetStatus.textContent = 'Packet: Hold';
      }
      if (packetUpdatedAt) {
        packetUpdatedAt.textContent = '';
      }
      if (packetSummary) {
        packetSummary.className = 'alert alert-danger mb-3';
        packetSummary.textContent = 'Governance packet недоступен из-за ошибки загрузки telemetry.';
        packetSummary.classList.remove('d-none');
      }
      if (packetTable) {
        packetTable.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Governance packet недоступен.</td></tr>';
      }
      if (macroGovernanceStatus) {
        macroGovernanceStatus.className = 'badge text-bg-danger';
        macroGovernanceStatus.textContent = 'Audit: Hold';
      }
      if (macroGovernanceIssueSummary) {
        macroGovernanceIssueSummary.className = 'alert alert-danger mb-3';
        macroGovernanceIssueSummary.textContent = 'Macro governance audit недоступен из-за ошибки загрузки telemetry.';
        macroGovernanceIssueSummary.classList.remove('d-none');
      }
      if (macroGovernanceTemplatesTable) {
        macroGovernanceTemplatesTable.innerHTML = '<tr><td colspan="6" class="text-danger text-center py-3">Macro governance audit недоступен.</td></tr>';
      }
      if (riskSegmentsTable) {
        riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-danger text-center py-3">Данные риск-сегментов недоступны.</td></tr>';
      }
      [profileGapTable, sourceGapTable, blockGapTable, parityGapTable].forEach((tableNode) => {
        if (tableNode) {
          tableNode.innerHTML = '<tr><td colspan="4" class="text-danger text-center py-3">Детализация gap-блокеров недоступна.</td></tr>';
        }
      });
      Object.values(metricNodes).forEach((node) => {
        node.textContent = '—';
      });
      okBadge.classList.add('d-none');
      attentionBadge.classList.add('d-none');
    }
  }

  async function confirmWeeklyReview() {
    if (!reviewConfirmButton) {
      return;
    }
    reviewConfirmButton.disabled = true;
    if (reviewActionState) {
      reviewActionState.textContent = 'Сохраняем...';
    }
    const reviewedBy = reviewByInput ? (reviewByInput.value || '').trim() : '';
    const reviewedAtUtc = dateTimeLocalToUtcIso(reviewAtInput ? (reviewAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (reviewActionState) {
        reviewActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      reviewConfirmButton.disabled = false;
      return;
    }
    const reviewNote = reviewNoteInput ? (reviewNoteInput.value || '').trim().slice(0, 500) : '';
    const decisionAction = reviewDecisionActionInput ? String(reviewDecisionActionInput.value || '').trim().toLowerCase() : '';
    const incidentFollowup = reviewIncidentFollowupInput ? (reviewIncidentFollowupInput.value || '').trim().slice(0, 255) : '';
    const requiredCriteria = reviewRequiredCriteriaInput ? parseCsvList(reviewRequiredCriteriaInput.value || '') : [];
    const checkedCriteria = reviewCheckedCriteriaInput ? parseCsvList(reviewCheckedCriteriaInput.value || '') : [];
    try {
      const response = await fetch('/analytics/workspace-rollout/review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reviewedBy,
          reviewedAtUtc,
          reviewNote,
          decisionAction,
          incidentFollowup,
          requiredCriteria,
          checkedCriteria,
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (reviewActionState) {
        reviewActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (reviewActionState) {
        reviewActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      reviewConfirmButton.disabled = false;
    }
  }

  function parseCsvList(value) {
    return String(value || '')
      .split(/[,\n;]/)
      .map((part) => part.trim())
      .filter((part, index, arr) => part.length > 0 && part.length <= 120 && arr.indexOf(part) === index);
  }

  function parseScenarioListMap(value, label) {
    const raw = String(value || '').trim();
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error(`${label} должен быть JSON-объектом вида {"scenario":["value1"]}.`);
    }
    const result = {};
    Object.entries(parsed).forEach(([scenario, fields]) => {
      const normalizedScenario = String(scenario || '').trim();
      if (!normalizedScenario || normalizedScenario.length > 120) return;
      const normalizedFields = Array.isArray(fields)
        ? fields.map((item) => String(item || '').trim()).filter((item, index, arr) => item.length > 0 && item.length <= 120 && arr.indexOf(item) === index)
        : [];
      if (normalizedFields.length > 0) result[normalizedScenario] = normalizedFields;
    });
    return result;
  }

  function parseContextPlaybooksMap(value) {
    const raw = String(value || '').trim();
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('Context-gap playbooks должен быть JSON-объектом вида {"mandatory_field:phone":{"label":"...","url":"https://..."}}.');
    }
    const result = {};
    Object.entries(parsed).forEach(([key, item]) => {
      const normalizedKey = String(key || '').trim().toLowerCase();
      if (!normalizedKey || normalizedKey.length > 160 || !item || typeof item !== 'object' || Array.isArray(item)) {
        return;
      }
      const label = String(item.label || '').trim().slice(0, 160) || 'Playbook';
      const url = String(item.url || '').trim();
      const summary = String(item.summary || '').trim().slice(0, 300);
      if (!/^https?:\/\//i.test(url)) {
        throw new Error(`Context-gap playbook "${normalizedKey}" должен содержать http/https URL.`);
      }
      result[normalizedKey] = summary
        ? { label, url, summary }
        : { label, url };
    });
    return result;
  }

  function normalizeUtcIsoTimestamp(value, label) {
    const raw = String(value || '').trim();
    if (!raw) {
      return '';
    }
    if (!/(Z|[+-]00:00)$/i.test(raw)) {
      throw new Error(`${label} должен быть UTC timestamp в ISO-8601.`);
    }
    const parsed = new Date(raw);
    if (Number.isNaN(parsed.getTime())) {
      throw new Error(`${label} содержит невалидную дату.`);
    }
    return parsed.toISOString();
  }

  function parseLegacyScenarioMetadataMap(value) {
    const raw = String(value || '').trim();
    if (!raw) {
      return {};
    }
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      throw new Error('Owner + sunset deadline by scenario должен быть JSON-объектом вида {"scenario":{"owner":"...","deadline_at_utc":"2026-04-15T12:00:00Z"}}.');
    }
    const result = {};
    Object.entries(parsed).forEach(([scenario, item]) => {
      const normalizedScenario = String(scenario || '').trim();
      if (!normalizedScenario || normalizedScenario.length > 120 || !item || typeof item !== 'object' || Array.isArray(item)) {
        return;
      }
      const owner = String(item.owner || '').trim().slice(0, 120);
      const deadlineAtUtc = normalizeUtcIsoTimestamp(item.deadlineAtUtc || item.deadline_at_utc || '', `Deadline for ${normalizedScenario}`);
      const note = String(item.note || '').trim().slice(0, 300);
      if (!owner && !deadlineAtUtc && !note) {
        return;
      }
      result[normalizedScenario] = {
        owner,
        deadlineAtUtc,
        note,
      };
    });
    return result;
  }

  async function saveContextStandard() {
    if (!contextSaveButton) {
      return;
    }
    contextSaveButton.disabled = true;
    if (contextActionState) {
      contextActionState.textContent = 'Сохраняем...';
    }
    const reviewedAtUtc = dateTimeLocalToUtcIso(contextReviewedAtInput ? (contextReviewedAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (contextActionState) {
        contextActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      contextSaveButton.disabled = false;
      return;
    }
    let scenarioMandatoryFields = {};
    let scenarioSourceOfTruth = {};
    let scenarioPriorityBlocks = {};
    let playbooks = {};
    try {
      scenarioMandatoryFields = parseScenarioListMap(
        contextScenarioMandatoryFieldsInput ? contextScenarioMandatoryFieldsInput.value : '',
        'Scenario mandatory fields'
      );
      scenarioSourceOfTruth = parseScenarioListMap(
        contextScenarioSourceOfTruthInput ? contextScenarioSourceOfTruthInput.value : '',
        'Scenario source-of-truth'
      );
      scenarioPriorityBlocks = parseScenarioListMap(
        contextScenarioPriorityBlocksInput ? contextScenarioPriorityBlocksInput.value : '',
        'Scenario priority blocks'
      );
      playbooks = parseContextPlaybooksMap(contextPlaybooksInput ? contextPlaybooksInput.value : '');
    } catch (error) {
      if (contextActionState) {
        contextActionState.textContent = `Ошибка: ${error.message}`;
      }
      contextSaveButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/workspace-context/standard', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          required: contextRequiredInput ? contextRequiredInput.checked : false,
          scenarios: parseCsvList(contextScenariosInput ? contextScenariosInput.value : ''),
          mandatoryFields: parseCsvList(contextMandatoryFieldsInput ? contextMandatoryFieldsInput.value : ''),
          scenarioMandatoryFields,
          sourceOfTruth: parseCsvList(contextSourceOfTruthInput ? contextSourceOfTruthInput.value : ''),
          scenarioSourceOfTruth,
          priorityBlocks: parseCsvList(contextPriorityBlocksInput ? contextPriorityBlocksInput.value : ''),
          scenarioPriorityBlocks,
          playbooks,
          reviewedBy: contextReviewedByInput ? (contextReviewedByInput.value || '').trim() : '',
          reviewedAtUtc,
          note: contextReviewNoteInput ? (contextReviewNoteInput.value || '').trim().slice(0, 500) : '',
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (contextActionState) {
        contextActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (contextActionState) {
        contextActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      contextSaveButton.disabled = false;
    }
  }

  async function saveLegacyInventory() {
    if (!legacySaveButton) {
      return;
    }
    legacySaveButton.disabled = true;
    if (legacyActionState) {
      legacyActionState.textContent = 'Сохраняем...';
    }
    const reviewedAtUtc = dateTimeLocalToUtcIso(legacyReviewedAtInput ? (legacyReviewedAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (legacyActionState) {
        legacyActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      legacySaveButton.disabled = false;
      return;
    }
    let scenarioMetadata = {};
    try {
      scenarioMetadata = parseLegacyScenarioMetadataMap(legacyScenarioMetadataInput ? legacyScenarioMetadataInput.value : '');
    } catch (error) {
      if (legacyActionState) {
        legacyActionState.textContent = `Ошибка: ${error.message}`;
      }
      legacySaveButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/workspace-rollout/legacy-only-scenarios', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          scenarios: parseCsvList(legacyScenariosInput ? legacyScenariosInput.value : ''),
          scenarioMetadata,
          reviewedBy: legacyReviewedByInput ? (legacyReviewedByInput.value || '').trim() : '',
          reviewedAtUtc,
          note: legacyReviewNoteInput ? (legacyReviewNoteInput.value || '').trim().slice(0, 500) : '',
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (legacyActionState) {
        legacyActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (legacyActionState) {
        legacyActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      legacySaveButton.disabled = false;
    }
  }

async function saveLegacyUsagePolicy() {
  if (!legacyUsageSaveButton) {
    return;
  }
  legacyUsageSaveButton.disabled = true;
  if (legacyUsageActionState) {
    legacyUsageActionState.textContent = 'Сохраняем...';
  }
  const reviewedAtUtc = dateTimeLocalToUtcIso(legacyUsageReviewedAtInput ? (legacyUsageReviewedAtInput.value || '').trim() : '');
  if (reviewedAtUtc === null) {
    if (legacyUsageActionState) {
      legacyUsageActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
    }
    legacyUsageSaveButton.disabled = false;
    return;
  }
  const maxShareRaw = legacyUsageMaxSharePctInput ? String(legacyUsageMaxSharePctInput.value || '').trim() : '';
  const minWorkspaceOpensRaw = legacyUsageMinWorkspaceOpensInput ? String(legacyUsageMinWorkspaceOpensInput.value || '').trim() : '';
  const maxShareDeltaRaw = legacyUsageMaxShareDeltaPctInput ? String(legacyUsageMaxShareDeltaPctInput.value || '').trim() : '';
  const maxBlockedShareDeltaRaw = legacyUsageMaxBlockedShareDeltaPctInput ? String(legacyUsageMaxBlockedShareDeltaPctInput.value || '').trim() : '';
  let maxSharePct = null;
  let minWorkspaceOpenEvents = null;
  let maxLegacyManualShareDeltaPct = null;
  let maxLegacyBlockedShareDeltaPct = null;
  if (maxShareRaw) {
    const parsed = Number(maxShareRaw);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
      if (legacyUsageActionState) {
        legacyUsageActionState.textContent = 'Ошибка: max share должен быть целым числом 0..100.';
      }
      legacyUsageSaveButton.disabled = false;
      return;
    }
    maxSharePct = parsed;
  }
  if (minWorkspaceOpensRaw) {
    const parsed = Number(minWorkspaceOpensRaw);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100000) {
      if (legacyUsageActionState) {
        legacyUsageActionState.textContent = 'Ошибка: min workspace opens должен быть целым числом 0..100000.';
      }
      legacyUsageSaveButton.disabled = false;
      return;
    }
    minWorkspaceOpenEvents = parsed;
  }
  if (maxShareDeltaRaw) {
    const parsed = Number(maxShareDeltaRaw);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
      if (legacyUsageActionState) {
        legacyUsageActionState.textContent = 'Ошибка: max share delta должен быть целым числом 0..100.';
      }
      legacyUsageSaveButton.disabled = false;
      return;
    }
    maxLegacyManualShareDeltaPct = parsed;
  }
  if (maxBlockedShareDeltaRaw) {
    const parsed = Number(maxBlockedShareDeltaRaw);
    if (!Number.isInteger(parsed) || parsed < 0 || parsed > 100) {
      if (legacyUsageActionState) {
        legacyUsageActionState.textContent = 'Ошибка: max blocked share delta должен быть целым числом 0..100.';
      }
      legacyUsageSaveButton.disabled = false;
      return;
    }
    maxLegacyBlockedShareDeltaPct = parsed;
  }
  try {
    const response = await fetch('/analytics/workspace-rollout/legacy-usage-policy', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        reviewedBy: legacyUsageReviewedByInput ? (legacyUsageReviewedByInput.value || '').trim() : '',
        reviewedAtUtc,
        reviewNote: legacyUsageReviewNoteInput ? (legacyUsageReviewNoteInput.value || '').trim().slice(0, 500) : '',
        decision: legacyUsageDecisionInput ? String(legacyUsageDecisionInput.value || '').trim().toLowerCase() : '',
        maxLegacyManualSharePct: maxSharePct,
        minWorkspaceOpenEvents,
        maxLegacyManualShareDeltaPct,
        maxLegacyBlockedShareDeltaPct,
        allowedReasons: parseCsvList(legacyUsageAllowedReasonsInput ? legacyUsageAllowedReasonsInput.value : ''),
        reasonCatalogRequired: legacyUsageReasonCatalogRequiredInput ? legacyUsageReasonCatalogRequiredInput.checked : false,
        blockedReasonsReviewed: parseCsvList(legacyUsageBlockedReasonsReviewedInput ? legacyUsageBlockedReasonsReviewedInput.value : ''),
        blockedReasonsFollowup: legacyUsageBlockedReasonsFollowupInput ? (legacyUsageBlockedReasonsFollowupInput.value || '').trim().slice(0, 500) : '',
      }),
    });
    const payload = await response.json();
    if (!response.ok || payload?.success !== true) {
      throw new Error(payload?.error || `HTTP ${response.status}`);
    }
    if (legacyUsageActionState) {
      legacyUsageActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
    }
    await loadTelemetry();
  } catch (error) {
    if (legacyUsageActionState) {
      legacyUsageActionState.textContent = `Ошибка: ${error.message}`;
    }
  } finally {
    legacyUsageSaveButton.disabled = false;
  }
}
  async function saveSlaPolicyGovernanceReview() {
    if (!slaPolicySaveReviewButton) {
      return;
    }
    slaPolicySaveReviewButton.disabled = true;
    if (slaPolicyActionState) {
      slaPolicyActionState.textContent = 'Сохраняем...';
    }
    const reviewedAtUtc = dateTimeLocalToUtcIso(slaPolicyReviewedAtInput ? (slaPolicyReviewedAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (slaPolicyActionState) {
        slaPolicyActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      slaPolicySaveReviewButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/sla-policy/governance-review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reviewedBy: slaPolicyReviewedByInput ? (slaPolicyReviewedByInput.value || '').trim() : '',
          reviewedAtUtc,
          reviewNote: slaPolicyReviewNoteInput ? (slaPolicyReviewNoteInput.value || '').trim().slice(0, 500) : '',
          dryRunTicketId: slaPolicyDryRunTicketIdInput ? (slaPolicyDryRunTicketIdInput.value || '').trim().slice(0, 80) : '',
          decision: slaPolicyDecisionInput ? String(slaPolicyDecisionInput.value || '').trim().toLowerCase() : '',
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (slaPolicyActionState) {
        slaPolicyActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (slaPolicyActionState) {
        slaPolicyActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      slaPolicySaveReviewButton.disabled = false;
    }
  }

  async function saveMacroGovernanceReview() {
    if (!macroGovernanceSaveReviewButton) {
      return;
    }
    macroGovernanceSaveReviewButton.disabled = true;
    if (macroGovernanceActionState) {
      macroGovernanceActionState.textContent = 'Сохраняем...';
    }
    const reviewedAtUtc = dateTimeLocalToUtcIso(macroGovernanceReviewedAtInput ? (macroGovernanceReviewedAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (macroGovernanceActionState) {
        macroGovernanceActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      macroGovernanceSaveReviewButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/macro-governance/review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reviewedBy: macroGovernanceReviewedByInput ? (macroGovernanceReviewedByInput.value || '').trim() : '',
          reviewedAtUtc,
          reviewNote: macroGovernanceReviewNoteInput ? (macroGovernanceReviewNoteInput.value || '').trim().slice(0, 500) : '',
          cleanupTicketId: macroGovernanceCleanupTicketIdInput ? (macroGovernanceCleanupTicketIdInput.value || '').trim().slice(0, 80) : '',
          decision: macroGovernanceDecisionInput ? String(macroGovernanceDecisionInput.value || '').trim().toLowerCase() : '',
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (macroGovernanceActionState) {
        macroGovernanceActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (macroGovernanceActionState) {
        macroGovernanceActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      macroGovernanceSaveReviewButton.disabled = false;
    }
  }

  async function saveMacroExternalCatalogPolicy() {
    if (!macroExternalCatalogSavePolicyButton) {
      return;
    }
    macroExternalCatalogSavePolicyButton.disabled = true;
    if (macroExternalCatalogActionState) {
      macroExternalCatalogActionState.textContent = 'Сохраняем...';
    }
    const verifiedAtUtc = dateTimeLocalToUtcIso(macroExternalCatalogVerifiedAtInput ? (macroExternalCatalogVerifiedAtInput.value || '').trim() : '');
    if (verifiedAtUtc === null) {
      if (macroExternalCatalogActionState) {
        macroExternalCatalogActionState.textContent = 'Ошибка: поле "Verified at (UTC)" содержит невалидную дату.';
      }
      macroExternalCatalogSavePolicyButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/macro-governance/external-catalog-policy', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          verifiedBy: macroExternalCatalogVerifiedByInput ? (macroExternalCatalogVerifiedByInput.value || '').trim() : '',
          verifiedAtUtc,
          expectedVersion: macroExternalCatalogExpectedVersionInput ? (macroExternalCatalogExpectedVersionInput.value || '').trim().slice(0, 120) : '',
          observedVersion: macroExternalCatalogObservedVersionInput ? (macroExternalCatalogObservedVersionInput.value || '').trim().slice(0, 120) : '',
          decision: macroExternalCatalogDecisionInput ? String(macroExternalCatalogDecisionInput.value || '').trim().toLowerCase() : '',
          reviewTtlHours: macroExternalCatalogReviewTtlHoursInput ? Number(macroExternalCatalogReviewTtlHoursInput.value || 0) : 0,
          reviewNote: macroExternalCatalogReviewNoteInput ? (macroExternalCatalogReviewNoteInput.value || '').trim().slice(0, 500) : '',
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (macroExternalCatalogActionState) {
        macroExternalCatalogActionState.textContent = `Сохранено: ${formatTimestamp(payload?.verified_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (macroExternalCatalogActionState) {
        macroExternalCatalogActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      macroExternalCatalogSavePolicyButton.disabled = false;
    }
  }

  async function saveMacroDeprecationPolicy() {
    if (!macroDeprecationSavePolicyButton) {
      return;
    }
    macroDeprecationSavePolicyButton.disabled = true;
    if (macroDeprecationActionState) {
      macroDeprecationActionState.textContent = 'Сохраняем...';
    }
    const reviewedAtUtc = dateTimeLocalToUtcIso(macroDeprecationReviewedAtInput ? (macroDeprecationReviewedAtInput.value || '').trim() : '');
    if (reviewedAtUtc === null) {
      if (macroDeprecationActionState) {
        macroDeprecationActionState.textContent = 'Ошибка: поле "Reviewed at (UTC)" содержит невалидную дату.';
      }
      macroDeprecationSavePolicyButton.disabled = false;
      return;
    }
    try {
      const response = await fetch('/analytics/macro-governance/deprecation-policy', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reviewedBy: macroDeprecationReviewedByInput ? (macroDeprecationReviewedByInput.value || '').trim() : '',
          reviewedAtUtc,
          deprecationTicketId: macroDeprecationTicketIdInput ? (macroDeprecationTicketIdInput.value || '').trim().slice(0, 80) : '',
          reviewNote: macroDeprecationReviewNoteInput ? (macroDeprecationReviewNoteInput.value || '').trim().slice(0, 500) : '',
          decision: macroDeprecationDecisionInput ? String(macroDeprecationDecisionInput.value || '').trim().toLowerCase() : '',
          reviewTtlHours: macroDeprecationReviewTtlHoursInput ? Number(macroDeprecationReviewTtlHoursInput.value || 0) : 0,
        }),
      });
      const payload = await response.json();
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      if (macroDeprecationActionState) {
        macroDeprecationActionState.textContent = `Сохранено: ${formatTimestamp(payload?.reviewed_at_utc)}`;
      }
      await loadTelemetry();
    } catch (error) {
      if (macroDeprecationActionState) {
        macroDeprecationActionState.textContent = `Ошибка: ${error.message}`;
      }
    } finally {
      macroDeprecationSavePolicyButton.disabled = false;
    }
  }

  refreshButton.addEventListener('click', loadTelemetry);
  if (resetWindowButton) {
    resetWindowButton.addEventListener('click', () => {
      if (fromUtcInput) {
        fromUtcInput.value = '';
      }
      if (toUtcInput) {
        toUtcInput.value = '';
      }
      loadTelemetry();
    });
  }
  if (scopeSelect) {
    scopeSelect.addEventListener('change', () => {
      if (latestPayload) {
        render(latestPayload);
      }
    });
  }
  if (segmentInput) {
    segmentInput.addEventListener('input', () => {
      if (latestPayload) {
        render(latestPayload);
      }
    });
  }
  if (exportCsvButton) {
    exportCsvButton.addEventListener('click', () => {
      if (!latestPayload) {
        return;
      }
      exportGuardrailsCsv(latestPayload, getFilters());
    });
  }
  daysSelect.addEventListener('change', loadTelemetry);
  if (reviewConfirmButton) {
    reviewConfirmButton.addEventListener('click', confirmWeeklyReview);
  }
  if (contextSaveButton) {
    contextSaveButton.addEventListener('click', saveContextStandard);
  }
  if (legacySaveButton) {
    legacySaveButton.addEventListener('click', saveLegacyInventory);
  }
  if (legacyUsageSaveButton) {
  legacyUsageSaveButton.addEventListener('click', saveLegacyUsagePolicy);
}
  if (slaPolicySaveReviewButton) {
    slaPolicySaveReviewButton.addEventListener('click', saveSlaPolicyGovernanceReview);
  }
  if (macroGovernanceSaveReviewButton) {
    macroGovernanceSaveReviewButton.addEventListener('click', saveMacroGovernanceReview);
  }
  if (macroExternalCatalogSavePolicyButton) {
    macroExternalCatalogSavePolicyButton.addEventListener('click', saveMacroExternalCatalogPolicy);
  }
  if (macroDeprecationSavePolicyButton) {
    macroDeprecationSavePolicyButton.addEventListener('click', saveMacroDeprecationPolicy);
  }
  experimentInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadTelemetry();
    }
  });

  loadTelemetry();
})();

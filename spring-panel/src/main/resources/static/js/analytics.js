(function () {
  const card = document.getElementById('workspaceTelemetryCard');
  if (!card) {
    return;
  }

  const daysSelect = document.getElementById('workspaceTelemetryDays');
  const experimentInput = document.getElementById('workspaceTelemetryExperiment');
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
  const reviewConfirmButton = document.getElementById('workspaceTelemetryReviewConfirm');
  const reviewActionState = document.getElementById('workspaceTelemetryReviewActionState');
  const packetExitState = document.getElementById('workspaceTelemetryPacketExitState');
  const packetExitMeta = document.getElementById('workspaceTelemetryPacketExitMeta');
  const packetLegacyState = document.getElementById('workspaceTelemetryPacketLegacyState');
  const packetLegacyMeta = document.getElementById('workspaceTelemetryPacketLegacyMeta');
  const slaPolicyAuditStatus = document.getElementById('workspaceTelemetrySlaPolicyAuditStatus');
  const slaPolicyAuditUpdatedAt = document.getElementById('workspaceTelemetrySlaPolicyAuditUpdatedAt');
  const slaPolicyAuditSummary = document.getElementById('workspaceTelemetrySlaPolicyAuditSummary');
  const slaPolicyAuditMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditMeta');
  const slaPolicyAuditLayers = document.getElementById('workspaceTelemetrySlaPolicyAuditLayers');
  const slaPolicyAuditLayersMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditLayersMeta');
  const slaPolicyAuditPreview = document.getElementById('workspaceTelemetrySlaPolicyAuditPreview');
  const slaPolicyAuditPreviewMeta = document.getElementById('workspaceTelemetrySlaPolicyAuditPreviewMeta');
  const slaPolicyAuditIssueSummary = document.getElementById('workspaceTelemetrySlaPolicyAuditIssueSummary');
  const slaPolicyAuditIssuesTable = document.getElementById('workspaceTelemetrySlaPolicyAuditIssuesTable');
  const slaPolicyAuditRulesTable = document.getElementById('workspaceTelemetrySlaPolicyAuditRulesTable');
  const macroGovernanceStatus = document.getElementById('workspaceTelemetryMacroGovernanceStatus');
  const macroGovernanceUpdatedAt = document.getElementById('workspaceTelemetryMacroGovernanceUpdatedAt');
  const macroGovernanceSummary = document.getElementById('workspaceTelemetryMacroGovernanceSummary');
  const macroGovernanceMeta = document.getElementById('workspaceTelemetryMacroGovernanceMeta');
  const macroGovernanceRequirements = document.getElementById('workspaceTelemetryMacroGovernanceRequirements');
  const macroGovernanceRequirementsMeta = document.getElementById('workspaceTelemetryMacroGovernanceRequirementsMeta');
  const macroGovernanceCleanup = document.getElementById('workspaceTelemetryMacroGovernanceCleanup');
  const macroGovernanceCleanupMeta = document.getElementById('workspaceTelemetryMacroGovernanceCleanupMeta');
  const macroGovernanceIssueSummary = document.getElementById('workspaceTelemetryMacroGovernanceIssueSummary');
  const macroGovernanceIssuesTable = document.getElementById('workspaceTelemetryMacroGovernanceIssuesTable');
  const macroGovernanceTemplatesTable = document.getElementById('workspaceTelemetryMacroGovernanceTemplatesTable');
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
    if (macroGovernanceIssuesTable) {
      macroGovernanceIssuesTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
    }
    if (macroGovernanceTemplatesTable) {
      macroGovernanceTemplatesTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Загрузка audit...</td></tr>';
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
      packetReviewMeta.textContent = `UTC: ${reviewedAt} · cadence: ${cadenceDays}d · age: ${ageDays}d · confirms: ${confirmedEvents}`;
    }
    if (reviewByInput) {
      reviewByInput.value = reviewCadence?.reviewed_by || '';
    }
    if (reviewAtInput) {
      reviewAtInput.value = toDateTimeLocalValue(reviewCadence?.reviewed_at);
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
    if (packetLegacyState) {
      packetLegacyState.textContent = legacyOnlyScenarios.length
        ? `${legacyOnlyScenarios.length} open`
        : 'Legacy-only gaps не заявлены';
    }
    if (packetLegacyMeta) {
      packetLegacyMeta.textContent = legacyOnlyScenarios.length
        ? legacyOnlyScenarios.join(', ')
        : 'Инвентарь пуст — legacy modal можно удерживать только как rollback.';
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
      slaPolicyAuditMeta.textContent = `rules=${formatNumber(audit?.rules_total || 0)} · critical candidates=${formatNumber(audit?.critical_candidates || 0)} · issues=${formatNumber(audit?.issues_total || 0)} · mode=${escapeHtml(audit?.orchestration_mode || '—')}`;
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
      slaPolicyAuditLayersMeta.textContent = `require_layers=${requirements.require_layers === true ? 'on' : 'off'} · require_owner=${requirements.require_owner === true ? 'on' : 'off'} · require_review=${requirements.require_review === true ? `on (${formatNumber(requirements.review_ttl_hours || 0)}h)` : 'off'}`;
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

    const issues = Array.isArray(audit?.issues) ? audit.issues : [];
    if (slaPolicyAuditIssueSummary) {
      const requirements = audit?.requirements && typeof audit.requirements === 'object' ? audit.requirements : {};
      const parts = [];
      if (audit?.summary) {
        parts.push(String(audit.summary));
      }
      if (issues.length) {
        parts.push(`Issues: ${issues.length}`);
      }
      parts.push(`conflicts_block=${requirements.block_on_conflicts === true ? 'on' : 'off'}`);
      slaPolicyAuditIssueSummary.textContent = parts.join(' · ');
      slaPolicyAuditIssueSummary.className = `alert ${status === 'hold' ? 'alert-danger' : (status === 'attention' ? 'alert-warning' : 'alert-secondary')} mb-3${parts.length ? '' : ' d-none'}`;
      slaPolicyAuditIssueSummary.classList.toggle('d-none', parts.length === 0);
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
      macroGovernanceMeta.textContent = `templates=${formatNumber(audit?.templates_total || 0)} · active=${formatNumber(audit?.published_active_total || 0)} · deprecated=${formatNumber(audit?.deprecated_total || 0)} · issues=${formatNumber(audit?.issues_total || 0)}`;
    }
    const requirements = audit?.requirements && typeof audit.requirements === 'object' ? audit.requirements : {};
    if (macroGovernanceRequirements) {
      macroGovernanceRequirements.textContent = `owner=${requirements.require_owner === true ? 'required' : 'optional'} · namespace=${requirements.require_namespace === true ? 'required' : 'optional'}`;
    }
    if (macroGovernanceRequirementsMeta) {
      macroGovernanceRequirementsMeta.textContent = `review=${requirements.require_review === true ? `required (${formatNumber(requirements.review_ttl_hours || 0)}h)` : 'optional'} · deprecation_reason=${requirements.deprecation_requires_reason === true ? 'required' : 'optional'}`;
    }
    if (macroGovernanceCleanup) {
      macroGovernanceCleanup.textContent = `unused=${formatNumber(audit?.unused_published_total || 0)} · stale_review=${formatNumber(audit?.stale_review_total || 0)}`;
    }
    if (macroGovernanceCleanupMeta) {
      macroGovernanceCleanupMeta.textContent = `invalid_review=${formatNumber(audit?.invalid_review_total || 0)} · window=${formatNumber(requirements.unused_days || 0)}d UTC · deprecation_gaps=${formatNumber(audit?.deprecation_gap_total || 0)}`;
    }
    const issues = Array.isArray(audit?.issues) ? audit.issues : [];
    if (macroGovernanceIssueSummary) {
      const parts = [];
      if (audit?.summary) {
        parts.push(String(audit.summary));
      }
      if (issues.length) {
        parts.push(`Issues: ${issues.length}`);
      }
      macroGovernanceIssueSummary.textContent = parts.join(' · ');
      macroGovernanceIssueSummary.className = `alert ${status === 'hold' ? 'alert-danger' : (status === 'attention' ? 'alert-warning' : 'alert-secondary')} mb-3${parts.length ? '' : ' d-none'}`;
      macroGovernanceIssueSummary.classList.toggle('d-none', parts.length === 0);
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
          const issueList = Array.isArray(template?.issues) && template.issues.length
            ? `<div class="small text-muted">${escapeHtml(template.issues.join(', '))}</div>`
            : '';
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
              <td class="text-end">${formatNumber(template?.usage_count || 0)}${template?.last_used_at_utc ? `<div class="small text-muted text-start">${escapeHtml(formatTimestamp(template.last_used_at_utc))}</div>` : ''}</td>
              <td class="small">${escapeHtml(cleanupMeta.join(' · ') || 'active')}</td>
            </tr>
          `;
        }).join('');
      }
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
      ['macro_template_id', 'macro_template_name', 'status', 'published', 'deprecated', 'owner', 'namespace', 'reviewed_at_utc', 'usage_count', 'last_used_at_utc', 'issues'],
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
        template?.last_used_at_utc || '',
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

    updatedAt.textContent = `Обновлено: ${formatTimestamp(payload?.generated_at)} · окно ${payload?.window_days || '—'} дн.`;
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
      alertBox.textContent = `Зафиксировано ${alerts.length} отклонений guardrails (${visibleAlerts.length} по текущему фильтру).`;
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
    const reviewedAtLocal = reviewAtInput ? (reviewAtInput.value || '').trim() : '';
    const reviewedAtUtc = reviewedAtLocal ? `${reviewedAtLocal}:00Z` : '';
    try {
      const response = await fetch('/analytics/workspace-rollout/review', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          reviewedBy,
          reviewedAtUtc,
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

  refreshButton.addEventListener('click', loadTelemetry);
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
  experimentInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadTelemetry();
    }
  });

  loadTelemetry();
})();

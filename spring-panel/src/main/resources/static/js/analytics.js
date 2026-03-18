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
  const alertsTable = document.getElementById('workspaceTelemetryAlertsTable');
  const shiftTable = document.getElementById('workspaceTelemetryShiftTable');
  const teamTable = document.getElementById('workspaceTelemetryTeamTable');
  const riskSegmentsTable = document.getElementById('workspaceTelemetryRiskSegmentsTable');
  const profileGapTable = document.getElementById('workspaceTelemetryProfileGapTable');
  const sourceGapTable = document.getElementById('workspaceTelemetrySourceGapTable');
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
    alertsTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    shiftTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    teamTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    if (riskSegmentsTable) {
      riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    }
    [profileGapTable, sourceGapTable, blockGapTable, parityGapTable].forEach((tableNode) => {
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
        <td>${escapeHtml(row?.reason || 'unspecified')}</td>
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
    renderGapBreakdownTable(blockGapTable, payload?.gap_breakdown?.block, 'Block gaps не зафиксированы.');
    renderGapBreakdownTable(parityGapTable, payload?.gap_breakdown?.parity, 'Parity gaps не зафиксированы.');
    renderScorecard(payload?.rollout_scorecard);
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
  experimentInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadTelemetry();
    }
  });

  loadTelemetry();
})();

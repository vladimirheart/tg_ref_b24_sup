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
  const alertsTable = document.getElementById('workspaceTelemetryAlertsTable');
  const shiftTable = document.getElementById('workspaceTelemetryShiftTable');
  const teamTable = document.getElementById('workspaceTelemetryTeamTable');
  const riskSegmentsTable = document.getElementById('workspaceTelemetryRiskSegmentsTable');

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
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    return parsed.toLocaleString('ru-RU');
  }

  function setLoadingState() {
    Object.values(metricNodes).forEach((node) => {
      node.textContent = '…';
    });
    updatedAt.textContent = 'Обновление telemetry...';
    okBadge.classList.add('d-none');
    attentionBadge.classList.add('d-none');
    alertBox.classList.add('d-none');
    alertsTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    shiftTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    teamTable.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    if (riskSegmentsTable) {
      riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
    }
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
    const rows = [
      ['generated_at', payload?.generated_at || ''],
      ['window_days', payload?.window_days || ''],
      ['scope_filter', filters.scope],
      ['segment_filter', filters.segment || ''],
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

  function render(payload) {
    latestPayload = payload || {};
    const totals = payload?.totals || {};
    Object.entries(metricNodes).forEach(([metric, node]) => {
      const value = totals[metric];
      node.textContent = metric === 'avg_open_ms' ? formatNumber(value) : formatNumber(value);
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
      if (riskSegmentsTable) {
        riskSegmentsTable.innerHTML = '<tr><td colspan="6" class="text-danger text-center py-3">Данные риск-сегментов недоступны.</td></tr>';
      }
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

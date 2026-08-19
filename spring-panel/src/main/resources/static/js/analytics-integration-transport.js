(function () {
  const card = document.getElementById('analyticsIntegrationTransportCard');
  if (!card) {
    return;
  }

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
  const refreshButton = document.getElementById('analyticsIntegrationTransportRefresh');
  const replayFailedButton = document.getElementById('analyticsIntegrationTransportReplayFailed');
  const requeueFailedButton = document.getElementById('analyticsIntegrationTransportRequeueFailed');
  const updatedAtEl = document.getElementById('analyticsIntegrationTransportUpdatedAt');
  const errorEl = document.getElementById('analyticsIntegrationTransportError');
  const inboundTable = document.getElementById('analyticsIntegrationTransportInboundTable');
  const outboundTable = document.getElementById('analyticsIntegrationTransportOutboundTable');
  const incidentTable = document.getElementById('analyticsIntegrationTransportIncidentTable');
  const checkpointTable = document.getElementById('analyticsIntegrationTransportCheckpointTable');
  const snapshotTable = document.getElementById('analyticsIntegrationTransportSnapshotTable');
  const operationTable = document.getElementById('analyticsIntegrationTransportOperationTable');
  const alertsEl = document.getElementById('analyticsIntegrationTransportAlerts');
  const trendBadgeEl = document.getElementById('analyticsIntegrationTransportTrendBadge');
  const trendMetaEl = document.getElementById('analyticsIntegrationTransportTrendMeta');
  const trendSummaryEl = document.getElementById('analyticsIntegrationTransportTrendSummary');
  const snapshotMetaEl = document.getElementById('analyticsIntegrationTransportSnapshotMeta');
  const metricNodes = {
    inbound_failed: document.querySelector('[data-transport-metric="inbound_failed"]'),
    inbound_stale: document.querySelector('[data-transport-metric="inbound_stale"]'),
    outbound_failed: document.querySelector('[data-transport-metric="outbound_failed"]'),
    outbound_backlog: document.querySelector('[data-transport-metric="outbound_backlog"]'),
    stale_checkpoints: document.querySelector('[data-transport-metric="stale_checkpoints"]'),
    manual_operations: document.querySelector('[data-transport-metric="manual_operations"]'),
    unhealthy_streak: document.querySelector('[data-transport-metric="unhealthy_streak"]'),
    transport_incidents: document.querySelector('[data-transport-metric="transport_incidents"]'),
  };
  let inFlight = false;

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function formatTimestamp(value) {
    if (!value) {
      return '—';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return String(value);
    }
    return parsed.toLocaleString('ru-RU');
  }

  function formatNumber(value) {
    return Number(value || 0).toLocaleString('ru-RU');
  }

  function setError(message) {
    if (!errorEl) {
      return;
    }
    if (!message) {
      errorEl.classList.add('d-none');
      errorEl.textContent = '';
      return;
    }
    errorEl.textContent = message;
    errorEl.classList.remove('d-none');
  }

  function setMetric(node, value) {
    if (node) {
      node.textContent = formatNumber(value);
    }
  }

  function badgeClassForSeverity(severity) {
    switch (String(severity || '').toLowerCase()) {
      case 'critical':
        return 'text-bg-danger';
      case 'high':
        return 'text-bg-warning';
      case 'warning':
        return 'text-bg-warning';
      case 'ok':
      case 'healthy':
        return 'text-bg-success';
      default:
        return 'text-bg-secondary';
    }
  }

  function trendBadge(status) {
    switch (String(status || '').toLowerCase()) {
      case 'pressure':
        return { label: 'Trend: pressure', css: 'text-bg-danger' };
      case 'monitor':
        return { label: 'Trend: monitor', css: 'text-bg-warning' };
      case 'healthy':
        return { label: 'Trend: healthy', css: 'text-bg-success' };
      default:
        return { label: 'Trend: no data', css: 'text-bg-secondary' };
    }
  }

  function renderOverview(payload) {
    const inbound = payload?.inbound || {};
    const outbound = payload?.outbound || {};
    const healthSnapshot = payload?.health_snapshot || {};
    const trendSummary = payload?.trend_summary || {};
    setMetric(metricNodes.inbound_failed, inbound.failed);
    setMetric(metricNodes.inbound_stale, inbound.stale_processing);
    setMetric(metricNodes.outbound_failed, outbound.failed);
    setMetric(metricNodes.outbound_backlog, Number(outbound.queued || 0) + Number(outbound.processing || 0));
    setMetric(metricNodes.stale_checkpoints, healthSnapshot.stale_checkpoint_count);
    setMetric(metricNodes.manual_operations, healthSnapshot.recent_manual_operations);
    setMetric(metricNodes.unhealthy_streak, trendSummary.unhealthy_streak);
    setMetric(metricNodes.transport_incidents, Array.isArray(payload?.transport_incidents) ? payload.transport_incidents.length : 0);
    renderTrendSummary(trendSummary);
    renderAlerts(payload?.alerts || []);
    renderInboundRows(payload?.recent_failed_inbound || []);
    renderOutboundRows(payload?.recent_failed_outbound || []);
    renderIncidentRows(payload?.transport_incidents || []);
    renderCheckpointRows(payload?.runtime_checkpoints || []);
    renderSnapshotRows(payload?.recent_snapshots || []);
    renderOperationRows(payload?.recent_operations || []);
  }

  function renderTrendSummary(trendSummary) {
    const badge = trendBadge(trendSummary?.status);
    if (trendBadgeEl) {
      trendBadgeEl.className = `badge ${badge.css}`;
      trendBadgeEl.textContent = badge.label;
    }
    if (trendMetaEl) {
      trendMetaEl.textContent = `Окно ${formatNumber(trendSummary?.window_hours)}ч · snapshot-ов ${formatNumber(trendSummary?.snapshot_count)} · critical streak ${formatNumber(trendSummary?.critical_streak)}`;
    }
    if (trendSummaryEl) {
      trendSummaryEl.textContent = trendSummary?.latest_summary
        ? `Последний срез: ${trendSummary.latest_summary}`
        : 'Исторические snapshot-данные пока отсутствуют.';
    }
    if (snapshotMetaEl) {
      snapshotMetaEl.textContent = `Последний snapshot: ${formatTimestamp(trendSummary?.latest_created_at)}`;
    }
  }

  function renderAlerts(items) {
    if (!alertsEl) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      alertsEl.innerHTML = '<div class="text-muted small">Transport alerts не обнаружены</div>';
      return;
    }
    alertsEl.innerHTML = items.map((item) => `
      <div class="d-flex flex-column flex-lg-row justify-content-between gap-2 border rounded-3 px-3 py-2">
        <div>
          <div class="fw-semibold">${escapeHtml(item.message || item.key || 'Transport alert')}</div>
          <div class="small text-muted">Key: ${escapeHtml(item.key || '—')} · Threshold: ${escapeHtml(item.threshold ?? '—')}</div>
        </div>
        <div class="d-flex align-items-center gap-2">
          <span class="badge ${badgeClassForSeverity(item.severity)}">${escapeHtml(item.severity || 'info')}</span>
          <span class="fw-semibold">${escapeHtml(formatNumber(item.value))}</span>
        </div>
      </div>
    `).join('');
  }

  function renderInboundRows(items) {
    if (!inboundTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      inboundTable.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Нет replayable inbound events</td></tr>';
      return;
    }
    inboundTable.innerHTML = items.map((item) => `
      <tr>
        <td>
          <div class="fw-semibold">${escapeHtml(item.event_kind || item.event_id)}</div>
          <div class="small text-muted">${escapeHtml(item.event_id || '—')}</div>
        </td>
        <td>${escapeHtml(item.ticket_id || '—')}</td>
        <td>${escapeHtml(item.status || '—')}</td>
        <td class="small text-muted">${escapeHtml(item.last_error || '—')}</td>
        <td class="text-end"><button type="button" class="btn btn-sm btn-outline-primary" data-transport-action="replay-inbound" data-event-id="${escapeHtml(item.event_id || '')}">Replay</button></td>
      </tr>
    `).join('');
  }

  function renderOutboundRows(items) {
    if (!outboundTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      outboundTable.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Нет replayable outbound events</td></tr>';
      return;
    }
    outboundTable.innerHTML = items.map((item) => `
      <tr>
        <td>
          <div class="fw-semibold">${escapeHtml(item.event_kind || item.event_id)}</div>
          <div class="small text-muted">${escapeHtml(item.event_id || '—')}</div>
        </td>
        <td>${escapeHtml(item.ticket_id || '—')}</td>
        <td>${escapeHtml(item.status || '—')}</td>
        <td class="small text-muted">${escapeHtml(item.last_error || '—')}</td>
        <td class="text-end"><button type="button" class="btn btn-sm btn-outline-primary" data-transport-action="requeue-outbound" data-event-id="${escapeHtml(item.event_id || '')}">Requeue</button></td>
      </tr>
    `).join('');
  }

  function renderIncidentRows(items) {
    if (!incidentTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      incidentTable.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Transport incidents не обнаружены</td></tr>';
      return;
    }
    incidentTable.innerHTML = items.map((item) => `
      <tr>
        <td>
          <div class="fw-semibold">${escapeHtml(item.incident_key || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.title || '—')}</div>
        </td>
        <td><span class="badge ${badgeClassForSeverity(item.severity)}">${escapeHtml(item.severity || '—')}</span></td>
        <td>${escapeHtml(item.status || '—')}</td>
        <td>${escapeHtml(formatTimestamp(item.updated_at))}</td>
      </tr>
    `).join('');
  }

  function renderCheckpointRows(items) {
    if (!checkpointTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      checkpointTable.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Runtime checkpoints отсутствуют</td></tr>';
      return;
    }
    checkpointTable.innerHTML = items.map((item) => `
      <tr>
        <td>
          <div class="fw-semibold">${escapeHtml(item.worker_label || item.worker_key || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.cursor_text || '0')}</div>
        </td>
        <td><span class="badge ${badgeClassForSeverity(item.health_status)}">${escapeHtml(item.health_status || '—')}</span></td>
        <td>${escapeHtml(item.cursor_lag == null ? '—' : formatNumber(item.cursor_lag))}</td>
        <td>${escapeHtml(formatTimestamp(item.updated_at))}</td>
      </tr>
    `).join('');
  }

  function renderSnapshotRows(items) {
    if (!snapshotTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      snapshotTable.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">История transport snapshot отсутствует</td></tr>';
      return;
    }
    snapshotTable.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatTimestamp(item.created_at))}</td>
        <td><span class="badge ${badgeClassForSeverity(item.severity)}">${escapeHtml(item.severity || '—')}</span></td>
        <td>${escapeHtml(formatNumber(item.outbound_backlog))}</td>
        <td>${escapeHtml(formatNumber(item.stale_checkpoint_count))}</td>
        <td>${escapeHtml(formatNumber(item.recent_manual_operations))}</td>
        <td class="small text-muted">${escapeHtml(item.summary_text || '—')}</td>
      </tr>
    `).join('');
  }

  function renderOperationRows(items) {
    if (!operationTable) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      operationTable.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Recovery audit trail пока пуст</td></tr>';
      return;
    }
    operationTable.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatTimestamp(item.created_at))}</td>
        <td>
          <div class="fw-semibold">${escapeHtml(item.action_type || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.summary_text || '—')}</div>
        </td>
        <td>
          <div>${escapeHtml(item.target_type || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.target_id || item.ticket_id || '—')}</div>
        </td>
        <td>${escapeHtml(item.actor || 'system')}</td>
        <td><span class="badge ${badgeClassForSeverity(item.result_status === 'success' ? 'ok' : 'warning')}">${escapeHtml(item.result_status || '—')}</span></td>
      </tr>
    `).join('');
  }

  async function requestJson(url, options = {}) {
    const headers = new Headers(options.headers || {});
    if (csrfToken) {
      headers.set(csrfHeader, csrfToken);
    }
    const response = await fetch(url, {
      credentials: 'same-origin',
      cache: 'no-store',
      ...options,
      headers,
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload?.success !== true) {
      throw new Error(payload?.error || `HTTP ${response.status}`);
    }
    return payload;
  }

  async function loadOverview() {
    if (inFlight) {
      return;
    }
    inFlight = true;
    setError('');
    try {
      const payload = await requestJson('/api/analytics/integration-transport');
      renderOverview(payload);
      if (updatedAtEl) {
        updatedAtEl.textContent = `Обновлено: ${new Date().toLocaleString('ru-RU')}`;
      }
    } catch (error) {
      setError(`Не удалось загрузить transport ops: ${error.message}`);
      if (updatedAtEl) {
        updatedAtEl.textContent = 'Обновление не выполнено';
      }
    } finally {
      inFlight = false;
    }
  }

  async function invokeAction(url) {
    setError('');
    try {
      await requestJson(url, { method: 'POST' });
      await loadOverview();
    } catch (error) {
      setError(error.message);
    }
  }

  card.addEventListener('click', (event) => {
    const replayButton = event.target.closest('[data-transport-action="replay-inbound"]');
    if (replayButton) {
      event.preventDefault();
      void invokeAction(`/api/analytics/integration-transport/inbound-events/${encodeURIComponent(replayButton.dataset.eventId || '')}/replay`);
      return;
    }
    const requeueButton = event.target.closest('[data-transport-action="requeue-outbound"]');
    if (requeueButton) {
      event.preventDefault();
      void invokeAction(`/api/analytics/integration-transport/outbox-events/${encodeURIComponent(requeueButton.dataset.eventId || '')}/requeue`);
    }
  });

  refreshButton?.addEventListener('click', () => void loadOverview());
  replayFailedButton?.addEventListener('click', () => void invokeAction('/api/analytics/integration-transport/inbound-events/replay-failed?limit=25'));
  requeueFailedButton?.addEventListener('click', () => void invokeAction('/api/analytics/integration-transport/outbox-events/requeue-failed?limit=25'));

  void loadOverview();
})();

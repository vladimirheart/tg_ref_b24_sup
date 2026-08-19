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
  const metricNodes = {
    inbound_failed: document.querySelector('[data-transport-metric="inbound_failed"]'),
    inbound_stale: document.querySelector('[data-transport-metric="inbound_stale"]'),
    outbound_failed: document.querySelector('[data-transport-metric="outbound_failed"]'),
    outbound_backlog: document.querySelector('[data-transport-metric="outbound_backlog"]'),
    outbound_stale: document.querySelector('[data-transport-metric="outbound_stale"]'),
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
      node.textContent = Number(value || 0).toLocaleString('ru-RU');
    }
  }

  function renderOverview(payload) {
    const inbound = payload?.inbound || {};
    const outbound = payload?.outbound || {};
    setMetric(metricNodes.inbound_failed, inbound.failed);
    setMetric(metricNodes.inbound_stale, inbound.stale_processing);
    setMetric(metricNodes.outbound_failed, outbound.failed);
    setMetric(metricNodes.outbound_backlog, Number(outbound.queued || 0) + Number(outbound.processing || 0));
    setMetric(metricNodes.outbound_stale, outbound.stale_processing);
    setMetric(metricNodes.transport_incidents, Array.isArray(payload?.transport_incidents) ? payload.transport_incidents.length : 0);
    renderInboundRows(payload?.recent_failed_inbound || []);
    renderOutboundRows(payload?.recent_failed_outbound || []);
    renderIncidentRows(payload?.transport_incidents || []);
    renderCheckpointRows(payload?.runtime_checkpoints || []);
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
        <td>${escapeHtml(item.severity || '—')}</td>
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
      checkpointTable.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-3">Runtime checkpoints отсутствуют</td></tr>';
      return;
    }
    checkpointTable.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(item.worker_key || '—')}</td>
        <td class="small text-muted">${escapeHtml(item.cursor_text || '0')}</td>
        <td>${escapeHtml(formatTimestamp(item.updated_at))}</td>
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

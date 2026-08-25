(function () {
  const refreshBtn = document.getElementById('refreshProviderDeliveryBtn');
  const generatedAtEl = document.getElementById('providerDeliveryGeneratedAt');
  const alertsGeneratedAtEl = document.getElementById('providerDeliveryAlertsGeneratedAt');
  const overviewCaptionEl = document.getElementById('providerDeliveryOverviewCaption');
  const alertOverviewCaptionEl = document.getElementById('providerDeliveryAlertOverviewCaption');
  const channelsBody = document.getElementById('providerDeliveryChannelsBody');
  const attemptsBody = document.getElementById('providerDeliveryAttemptsBody');
  const historyCaptionEl = document.getElementById('providerDeliveryHistoryCaption');
  const historyBody = document.getElementById('providerDeliveryHistoryBody');
  const alertHistoryCaptionEl = document.getElementById('providerDeliveryAlertHistoryCaption');
  const alertHistoryBody = document.getElementById('providerDeliveryAlertHistoryBody');

  const historyModalEl = document.getElementById('providerDeliveryHistoryModal');
  const historyModal = historyModalEl ? new bootstrap.Modal(historyModalEl) : null;
  const alertHistoryModalEl = document.getElementById('providerDeliveryAlertHistoryModal');
  const alertHistoryModal = alertHistoryModalEl ? new bootstrap.Modal(alertHistoryModalEl) : null;

  const metrics = {
    attempts24h: document.getElementById('deliveryAttempts24h'),
    success24h: document.getElementById('deliverySuccess24h'),
    failure24h: document.getElementById('deliveryFailure24h'),
    rateLimited24h: document.getElementById('deliveryRateLimited24h'),
    terminal24h: document.getElementById('deliveryTerminal24h'),
    transient24h: document.getElementById('deliveryTransient24h')
  };

  const alertMetrics = {
    actionable: document.getElementById('deliveryAlertActionable'),
    critical: document.getElementById('deliveryAlertCritical'),
    warning: document.getElementById('deliveryAlertWarning'),
    failurePressure: document.getElementById('deliveryAlertFailurePressure'),
    rateLimitPressure: document.getElementById('deliveryAlertRateLimitPressure'),
    incidents: document.getElementById('deliveryAlertIncidents')
  };

  async function api(path, options) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json'
      },
      ...options
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      throw new Error(payload.error || 'Request failed');
    }
    return payload;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  function formatDateTime(value) {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString('ru-RU');
  }

  function formatDuration(value) {
    return value == null ? '—' : `${value} ms`;
  }

  function formatBurnRate(value) {
    if (value == null || Number.isNaN(Number(value))) {
      return '0.00x';
    }
    return `${Number(value).toFixed(2)}x`;
  }

  function formatPercent(value) {
    if (value == null || Number.isNaN(Number(value))) {
      return '0.0%';
    }
    return `${(Number(value) * 100).toFixed(1)}%`;
  }

  function badgeClass(status) {
    switch ((status || '').toLowerCase()) {
      case 'ok':
      case 'success':
        return 'text-bg-success';
      case 'warning':
      case 'rate_limited':
      case 'idle':
        return 'text-bg-warning';
      case 'critical':
      case 'failed':
      case 'provider_error':
      case 'network_error':
      case 'timeout':
      case 'unknown_error':
        return 'text-bg-danger';
      case 'disabled':
      case 'terminal':
        return 'text-bg-secondary';
      default:
        return 'text-bg-light';
    }
  }

  function renderOverview(overview, generatedAt) {
    const safe = overview || {};
    metrics.attempts24h.textContent = safe.attempts_24h ?? 0;
    metrics.success24h.textContent = safe.success_24h ?? 0;
    metrics.failure24h.textContent = safe.failure_24h ?? 0;
    metrics.rateLimited24h.textContent = safe.rate_limited_24h ?? 0;
    metrics.terminal24h.textContent = safe.terminal_failures_24h ?? 0;
    metrics.transient24h.textContent = safe.transient_failures_24h ?? 0;
    generatedAtEl.textContent = `Generated at ${formatDateTime(generatedAt)}`;
    overviewCaptionEl.textContent = `Channels: ${safe.total_channels ?? 0}, active: ${safe.active_channels ?? 0}, critical: ${safe.critical_channels ?? 0}, idle: ${safe.idle_channels ?? 0}.`;
  }

  function renderAlertOverview(overview, generatedAt) {
    const safe = overview || {};
    alertMetrics.actionable.textContent = safe.actionable_channels ?? 0;
    alertMetrics.critical.textContent = safe.critical_channels ?? 0;
    alertMetrics.warning.textContent = safe.warning_channels ?? 0;
    alertMetrics.failurePressure.textContent = safe.failure_pressure_channels ?? 0;
    alertMetrics.rateLimitPressure.textContent = safe.rate_limit_pressure_channels ?? 0;
    alertMetrics.incidents.textContent = safe.active_incidents ?? 0;
    alertsGeneratedAtEl.textContent = `Generated at ${formatDateTime(generatedAt)}`;
    alertOverviewCaptionEl.textContent = `Active channels: ${safe.active_channels ?? 0}, idle: ${safe.idle_channels ?? 0}, disabled: ${safe.disabled_channels ?? 0}.`;
  }

  function channelName(item) {
    return item.channel_name || `channel #${item.channel_id}`;
  }

  function incidentSummary(incidents) {
    if (!Array.isArray(incidents) || incidents.length === 0) {
      return '<span class="text-muted">—</span>';
    }
    return incidents.map((item) => `
      <div class="mb-1">
        <span class="badge ${badgeClass(item.severity)}">${escapeHtml(item.signal_kind || 'incident')}</span>
        <span class="small">${escapeHtml(item.incident_key || item.title || 'incident')}</span>
        <div class="small text-muted">${escapeHtml(item.status || 'unknown')} · ${escapeHtml(formatDateTime(item.updated_at))}</div>
      </div>
    `).join('');
  }

  function signalLines(signal) {
    if (!signal) {
      return '<div class="small text-muted">No alert signal.</div>';
    }
    return `
      <div class="mb-1">
        <span class="badge ${badgeClass(signal.status)}">${escapeHtml(signal.label || 'signal')} · ${escapeHtml(signal.status || 'unknown')}</span>
      </div>
      <div class="small text-muted">
        15m: ${escapeHtml(signal.short_window_errors ?? 0)} / ${escapeHtml(signal.short_window_attempts ?? 0)}
        (${escapeHtml(formatPercent(signal.short_window_error_rate))}, ${escapeHtml(formatBurnRate(signal.short_window_burn_rate))})
      </div>
      <div class="small text-muted">
        6h: ${escapeHtml(signal.long_window_errors ?? 0)} / ${escapeHtml(signal.long_window_attempts ?? 0)}
        (${escapeHtml(formatPercent(signal.long_window_error_rate))}, ${escapeHtml(formatBurnRate(signal.long_window_burn_rate))})
      </div>
    `;
  }

  function renderChannels(ledgerItems, alertItems) {
    const alertMap = new Map((alertItems || []).map((item) => [String(item.channel_id), item]));
    if (!Array.isArray(ledgerItems) || ledgerItems.length === 0) {
      channelsBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Provider channels not found.</td></tr>';
      return;
    }
    channelsBody.innerHTML = ledgerItems.map((item) => {
      const alert = alertMap.get(String(item.channel_id)) || {};
      return `
        <tr data-channel-id="${escapeHtml(item.channel_id)}">
          <td>
            <div class="fw-semibold">${escapeHtml(channelName(item))}</div>
            <div class="small text-muted">${escapeHtml((item.platform || '').toUpperCase())}${item.active ? '' : ' · disabled'}</div>
          </td>
          <td>
            <span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || 'unknown')}</span>
            <div class="small text-muted mt-1">
              <span class="badge ${badgeClass(alert.alert_status)}">${escapeHtml(alert.alert_status || 'no-alert')}</span>
            </div>
          </td>
          <td>
            <div>${escapeHtml(item.success_24h ?? 0)} success / ${escapeHtml(item.failure_24h ?? 0)} failed</div>
            <div class="small text-muted">${escapeHtml(item.total_24h ?? 0)} total, ${escapeHtml(item.rate_limited_24h ?? 0)} rate limited</div>
          </td>
          <td>
            <div class="small text-muted mb-2">Failures</div>
            ${signalLines(alert.failure_signal)}
            <div class="small text-muted mt-2 mb-2">Rate limit</div>
            ${signalLines(alert.rate_limit_signal)}
          </td>
          <td>${escapeHtml(formatDateTime(item.last_success_at || alert.last_success_at))}</td>
          <td>${escapeHtml(formatDateTime(item.last_failure_at || alert.last_failure_at))}</td>
          <td>${incidentSummary(alert.related_incidents)}</td>
          <td>
            <div>${escapeHtml(item.summary || '—')}</div>
            <div class="small text-muted mt-1">${escapeHtml(alert.summary || '')}</div>
            <div class="small text-muted mt-1">${escapeHtml(item.last_provider_message || '')}</div>
          </td>
          <td class="text-end">
            <div class="btn-group btn-group-sm">
              <button type="button" class="btn btn-outline-secondary" data-action="history">History</button>
              <button type="button" class="btn btn-outline-primary" data-action="alert-history">Alerts</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  function renderAttempts(items, channels) {
    const channelMap = new Map((channels || []).map((item) => [String(item.channel_id), channelName(item)]));
    if (!Array.isArray(items) || items.length === 0) {
      attemptsBody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">Outbound attempts not found.</td></tr>';
      return;
    }
    attemptsBody.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatDateTime(item.attempted_at))}</td>
        <td>${escapeHtml(channelMap.get(String(item.channel_id)) || `channel #${item.channel_id}`)}</td>
        <td>${escapeHtml(item.ticket_id || '—')}</td>
        <td>
          <div>${escapeHtml(item.message_kind || 'text')}</div>
          <div class="small text-muted">${escapeHtml(item.sender_kind || 'operator')}</div>
        </td>
        <td>
          <span class="badge ${badgeClass(item.classification)}">${escapeHtml(item.classification || item.delivery_status || 'unknown')}</span>
          <div class="small text-muted">${escapeHtml(item.retry_state || '—')}</div>
        </td>
        <td>${escapeHtml(item.http_status ?? '—')}</td>
        <td>${escapeHtml(item.provider_message || item.provider_error_code || '—')}</td>
        <td>${escapeHtml(formatDuration(item.duration_ms))}</td>
      </tr>
    `).join('');
  }

  async function loadOverview() {
    const [ledgerPayload, alertPayload] = await Promise.all([
      api('/api/monitoring/provider-delivery/channels'),
      api('/api/monitoring/provider-delivery/alerts')
    ]);
    renderOverview(ledgerPayload.overview, ledgerPayload.generated_at);
    renderAlertOverview(alertPayload.overview, alertPayload.generated_at);
    renderChannels(ledgerPayload.items, alertPayload.items);
    renderAttempts(ledgerPayload.recent_attempts, ledgerPayload.items);
  }

  async function refreshAll() {
    refreshBtn.disabled = true;
    try {
      await api('/api/monitoring/provider-delivery/refresh', { method: 'POST' });
      await loadOverview();
    } finally {
      refreshBtn.disabled = false;
    }
  }

  async function loadHistory(channelId, name) {
    historyCaptionEl.textContent = `Recent outbound attempts for ${name || `channel #${channelId}`}.`;
    historyBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3">Загрузка...</td></tr>';
    historyModal?.show();
    const payload = await api(`/api/monitoring/provider-delivery/channels/${channelId}/history`);
    const items = Array.isArray(payload.items) ? payload.items : [];
    if (!items.length) {
      historyBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-3">История пока пуста.</td></tr>';
      return;
    }
    historyBody.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatDateTime(item.attempted_at))}</td>
        <td>${escapeHtml(item.ticket_id || '—')}</td>
        <td>
          <span class="badge ${badgeClass(item.classification)}">${escapeHtml(item.classification || item.delivery_status || 'unknown')}</span>
          <div class="small text-muted">${escapeHtml(item.message_kind || 'text')}</div>
        </td>
        <td>${escapeHtml(item.http_status ?? '—')}</td>
        <td>${escapeHtml(item.retry_state || '—')}</td>
        <td>${escapeHtml(item.provider_message || item.provider_error_code || '—')}</td>
        <td class="small">${escapeHtml(item.response_excerpt || '—')}</td>
      </tr>
    `).join('');
  }

  async function loadAlertHistory(channelId, name) {
    alertHistoryCaptionEl.textContent = `Recent burn-rate snapshots for ${name || `channel #${channelId}`}.`;
    alertHistoryBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">Загрузка...</td></tr>';
    alertHistoryModal?.show();
    const payload = await api(`/api/monitoring/provider-delivery/channels/${channelId}/alert-history`);
    const items = Array.isArray(payload.items) ? payload.items : [];
    if (!items.length) {
      alertHistoryBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-3">История пока пуста.</td></tr>';
      return;
    }
    alertHistoryBody.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatDateTime(item.created_at))}</td>
        <td><span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || 'unknown')}</span></td>
        <td>${escapeHtml(item.summary || '—')}</td>
        <td class="small">${escapeHtml(item.details_excerpt || '—')}</td>
      </tr>
    `).join('');
  }

  refreshBtn?.addEventListener('click', () => {
    refreshAll().catch((error) => window.showToast?.(error.message || 'Не удалось обновить provider delivery alerting', 'danger'));
  });

  channelsBody?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
      return;
    }
    const row = button.closest('tr[data-channel-id]');
    if (!row) {
      return;
    }
    const channelId = row.getAttribute('data-channel-id');
    const name = row.querySelector('.fw-semibold')?.textContent?.trim();
    if (button.dataset.action === 'history') {
      loadHistory(channelId, name).catch((error) => window.showToast?.(error.message || 'Не удалось загрузить историю', 'danger'));
      return;
    }
    if (button.dataset.action === 'alert-history') {
      loadAlertHistory(channelId, name).catch((error) => window.showToast?.(error.message || 'Не удалось загрузить burn-rate историю', 'danger'));
    }
  });

  loadOverview().catch((error) => {
    const message = escapeHtml(error.message || 'Не удалось загрузить provider delivery monitoring');
    channelsBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${message}</td></tr>`;
    attemptsBody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">${message}</td></tr>`;
    alertHistoryBody.innerHTML = `<tr><td colspan="4" class="text-center text-danger py-3">${message}</td></tr>`;
  });
})();

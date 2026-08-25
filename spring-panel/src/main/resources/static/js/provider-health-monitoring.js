(function () {
  const tableBody = document.getElementById('providerHealthTableBody');
  const refreshBtn = document.getElementById('refreshProviderHealthBtn');
  const generatedAtEl = document.getElementById('providerGeneratedAt');
  const overviewCaptionEl = document.getElementById('providerOverviewCaption');
  const historyCaptionEl = document.getElementById('providerHealthHistoryCaption');
  const historyTableBody = document.getElementById('providerHealthHistoryTableBody');

  const historyModalEl = document.getElementById('providerHealthHistoryModal');
  const historyModal = historyModalEl ? new bootstrap.Modal(historyModalEl) : null;

  const metricElements = {
    availabilityPercent: document.getElementById('providerAvailabilityPercent'),
    total: document.getElementById('providerTotal'),
    active: document.getElementById('providerActive'),
    ok: document.getElementById('providerOk'),
    warning: document.getElementById('providerWarning'),
    critical: document.getElementById('providerCritical'),
    disabled: document.getElementById('providerDisabled')
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

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  function statusBadgeClass(status) {
    switch ((status || '').toLowerCase()) {
      case 'ok':
      case 'active':
      case 'running':
        return 'text-bg-success';
      case 'warning':
      case 'idle':
      case 'stale':
        return 'text-bg-warning';
      case 'critical':
      case 'error':
      case 'stopped':
        return 'text-bg-danger';
      case 'disabled':
      case 'inactive':
        return 'text-bg-secondary';
      default:
        return 'text-bg-light';
    }
  }

  function renderOverview(overview, generatedAt) {
    const safe = overview || {};
    metricElements.availabilityPercent.textContent = `${safe.availability_percent ?? 0}%`;
    metricElements.total.textContent = safe.total ?? 0;
    metricElements.active.textContent = safe.active ?? 0;
    metricElements.ok.textContent = safe.ok ?? 0;
    metricElements.warning.textContent = safe.warning ?? 0;
    metricElements.critical.textContent = safe.critical ?? 0;
    metricElements.disabled.textContent = safe.disabled ?? 0;
    generatedAtEl.textContent = `Generated at ${formatDateTime(generatedAt)}`;
    overviewCaptionEl.textContent = `Idle channels: ${safe.idle ?? 0}. Availability is calculated only across active provider channels.`;
  }

  function renderRows(items) {
    if (!Array.isArray(items) || items.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Provider channels not found.</td></tr>';
      return;
    }
    tableBody.innerHTML = items.map((item) => {
      const providerDetails = [
        `<span class="badge ${statusBadgeClass(item.provider_status)}">${escapeHtml(item.provider_status || 'unknown')}</span>`,
        item.provider_identity ? `<div class="small text-muted">${escapeHtml(item.provider_identity)}</div>` : '',
        item.provider_http_status != null ? `<div class="small text-muted">HTTP ${escapeHtml(item.provider_http_status)}</div>` : ''
      ].join('');
      const lastActivity = [
        `<div><span class="text-muted small">Inbound:</span> ${escapeHtml(formatDateTime(item.last_inbound_at))}</div>`,
        `<div><span class="text-muted small">Outbound:</span> ${escapeHtml(formatDateTime(item.last_outbound_at))}</div>`
      ].join('');
      return `
        <tr data-channel-id="${escapeHtml(item.channel_id)}">
          <td>
            <div class="fw-semibold">${escapeHtml(item.channel_name || '—')}</div>
            <div class="small text-muted">${escapeHtml((item.platform || '').toUpperCase())}${item.active ? '' : ' · disabled'}</div>
          </td>
          <td><span class="badge ${statusBadgeClass(item.overall_status)}">${escapeHtml(item.overall_status || 'unknown')}</span></td>
          <td>
            <span class="badge ${statusBadgeClass(item.runtime_status)}">${escapeHtml(item.runtime_status || 'unknown')}</span>
            <div class="small text-muted">${escapeHtml(item.runtime_message || '—')}</div>
          </td>
          <td>${providerDetails}</td>
          <td>
            <span class="badge ${statusBadgeClass(item.ingress_status)}">${escapeHtml(item.ingress_status || 'unknown')}</span>
            <div class="small text-muted">${escapeHtml(item.inbound_24h ?? 0)} / 24h</div>
          </td>
          <td>
            <span class="badge ${statusBadgeClass(item.outbound_status)}">${escapeHtml(item.outbound_status || 'unknown')}</span>
            <div class="small text-muted">${escapeHtml(item.outbound_24h ?? 0)} / 24h</div>
          </td>
          <td>${lastActivity}</td>
          <td>
            <div>${escapeHtml(item.summary || '—')}</div>
            <div class="small text-muted">Checked: ${escapeHtml(formatDateTime(item.checked_at))}</div>
          </td>
          <td class="text-end">
            <div class="btn-group btn-group-sm">
              <button type="button" class="btn btn-outline-secondary" data-action="history">History</button>
              <button type="button" class="btn btn-outline-primary" data-action="refresh">Refresh</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async function loadOverview() {
    const payload = await api('/api/monitoring/provider-health/channels');
    renderOverview(payload.availability_overview, payload.generated_at);
    renderRows(payload.items);
  }

  async function refreshAll() {
    refreshBtn.disabled = true;
    try {
      await api('/api/monitoring/provider-health/refresh', { method: 'POST' });
      await loadOverview();
    } finally {
      refreshBtn.disabled = false;
    }
  }

  async function refreshChannel(channelId) {
    await api(`/api/monitoring/provider-health/channels/${channelId}/refresh`, { method: 'POST' });
    await loadOverview();
  }

  async function loadHistory(channelId, channelName) {
    historyCaptionEl.textContent = `Recent provider snapshots for ${channelName || `channel #${channelId}`}.`;
    historyTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Загрузка...</td></tr>';
    historyModal?.show();
    const payload = await api(`/api/monitoring/provider-health/channels/${channelId}/history`);
    const items = Array.isArray(payload.items) ? payload.items : [];
    if (!items.length) {
      historyTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">История пока пуста.</td></tr>';
      return;
    }
    historyTableBody.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatDateTime(item.created_at))}</td>
        <td><span class="badge ${statusBadgeClass(item.status)}">${escapeHtml(item.status || 'unknown')}</span></td>
        <td>${escapeHtml(item.summary || '—')}</td>
        <td>${escapeHtml(item.http_status ?? '—')}</td>
        <td>${escapeHtml(formatDuration(item.duration_ms))}</td>
        <td class="small">${escapeHtml(item.details_excerpt || '—')}</td>
      </tr>
    `).join('');
  }

  refreshBtn?.addEventListener('click', () => {
    refreshAll().catch((error) => window.showToast?.(error.message || 'Не удалось обновить provider health', 'danger'));
  });

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
      return;
    }
    const row = button.closest('tr[data-channel-id]');
    if (!row) {
      return;
    }
    const channelId = row.getAttribute('data-channel-id');
    const channelName = row.querySelector('.fw-semibold')?.textContent?.trim();
    if (button.dataset.action === 'refresh') {
      refreshChannel(channelId).catch((error) => window.showToast?.(error.message || 'Не удалось обновить канал', 'danger'));
      return;
    }
    if (button.dataset.action === 'history') {
      loadHistory(channelId, channelName).catch((error) => window.showToast?.(error.message || 'Не удалось загрузить историю', 'danger'));
    }
  });

  loadOverview().catch((error) => {
    tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${escapeHtml(error.message || 'Не удалось загрузить provider health')}</td></tr>`;
  });
})();

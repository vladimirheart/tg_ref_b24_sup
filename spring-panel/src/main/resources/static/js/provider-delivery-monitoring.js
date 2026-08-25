(function () {
  const refreshBtn = document.getElementById('refreshProviderDeliveryBtn');
  const generatedAtEl = document.getElementById('providerDeliveryGeneratedAt');
  const overviewCaptionEl = document.getElementById('providerDeliveryOverviewCaption');
  const channelsBody = document.getElementById('providerDeliveryChannelsBody');
  const attemptsBody = document.getElementById('providerDeliveryAttemptsBody');
  const historyCaptionEl = document.getElementById('providerDeliveryHistoryCaption');
  const historyBody = document.getElementById('providerDeliveryHistoryBody');

  const historyModalEl = document.getElementById('providerDeliveryHistoryModal');
  const historyModal = historyModalEl ? new bootstrap.Modal(historyModalEl) : null;

  const metrics = {
    attempts24h: document.getElementById('deliveryAttempts24h'),
    success24h: document.getElementById('deliverySuccess24h'),
    failure24h: document.getElementById('deliveryFailure24h'),
    rateLimited24h: document.getElementById('deliveryRateLimited24h'),
    terminal24h: document.getElementById('deliveryTerminal24h'),
    transient24h: document.getElementById('deliveryTransient24h')
  };

  async function api(path) {
    const response = await fetch(path, { credentials: 'same-origin' });
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

  function channelName(item) {
    return item.channel_name || `channel #${item.channel_id}`;
  }

  function renderChannels(items) {
    if (!Array.isArray(items) || items.length === 0) {
      channelsBody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">Provider channels not found.</td></tr>';
      return;
    }
    channelsBody.innerHTML = items.map((item) => `
      <tr data-channel-id="${escapeHtml(item.channel_id)}">
        <td>
          <div class="fw-semibold">${escapeHtml(channelName(item))}</div>
          <div class="small text-muted">${escapeHtml((item.platform || '').toUpperCase())}${item.active ? '' : ' · disabled'}</div>
        </td>
        <td><span class="badge ${badgeClass(item.status)}">${escapeHtml(item.status || 'unknown')}</span></td>
        <td>
          <div>${escapeHtml(item.success_24h ?? 0)} success / ${escapeHtml(item.failure_24h ?? 0)} failed</div>
          <div class="small text-muted">${escapeHtml(item.total_24h ?? 0)} total, ${escapeHtml(item.rate_limited_24h ?? 0)} rate limited</div>
        </td>
        <td>${escapeHtml(formatDateTime(item.last_success_at))}</td>
        <td>${escapeHtml(formatDateTime(item.last_failure_at))}</td>
        <td>
          <span class="badge ${badgeClass(item.last_classification)}">${escapeHtml(item.last_classification || 'none')}</span>
          ${item.last_http_status != null ? `<div class="small text-muted">HTTP ${escapeHtml(item.last_http_status)}</div>` : ''}
        </td>
        <td>
          <div>${escapeHtml(item.summary || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.last_provider_message || '')}</div>
        </td>
        <td class="text-end">
          <button type="button" class="btn btn-sm btn-outline-secondary" data-action="history">History</button>
        </td>
      </tr>
    `).join('');
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
    const payload = await api('/api/monitoring/provider-delivery/channels');
    renderOverview(payload.overview, payload.generated_at);
    renderChannels(payload.items);
    renderAttempts(payload.recent_attempts, payload.items);
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

  refreshBtn?.addEventListener('click', () => {
    loadOverview().catch((error) => window.showToast?.(error.message || 'Не удалось обновить provider delivery ledger', 'danger'));
  });

  channelsBody?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action="history"]');
    if (!button) {
      return;
    }
    const row = button.closest('tr[data-channel-id]');
    if (!row) {
      return;
    }
    const channelId = row.getAttribute('data-channel-id');
    const name = row.querySelector('.fw-semibold')?.textContent?.trim();
    loadHistory(channelId, name).catch((error) => window.showToast?.(error.message || 'Не удалось загрузить историю', 'danger'));
  });

  loadOverview().catch((error) => {
    const message = escapeHtml(error.message || 'Не удалось загрузить provider delivery ledger');
    channelsBody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">${message}</td></tr>`;
    attemptsBody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">${message}</td></tr>`;
  });
})();

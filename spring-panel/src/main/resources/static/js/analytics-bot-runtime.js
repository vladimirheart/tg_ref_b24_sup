(function () {
  const card = document.getElementById('analyticsBotRuntimeCard');
  if (!card) {
    return;
  }

  const refreshButton = document.getElementById('analyticsBotRuntimeRefresh');
  const updatedAtEl = document.getElementById('analyticsBotRuntimeUpdatedAt');
  const errorEl = document.getElementById('analyticsBotRuntimeError');
  const tableBody = document.getElementById('analyticsBotRuntimeTable');
  const metricNodes = {
    total: document.querySelector('[data-bot-runtime-metric="total"]'),
    active: document.querySelector('[data-bot-runtime-metric="active"]'),
    running: document.querySelector('[data-bot-runtime-metric="running"]'),
    stopped: document.querySelector('[data-bot-runtime-metric="stopped"]'),
    inactive: document.querySelector('[data-bot-runtime-metric="inactive"]'),
    error: document.querySelector('[data-bot-runtime-metric="error"]'),
  };
  const emptyRow = '<tr><td colspan="5" class="text-center text-muted py-3">Нет каналов для отображения</td></tr>';
  const loadingRow = '<tr><td colspan="5" class="text-center text-muted py-3">Загрузка статусов ботов...</td></tr>';
  let pollTimer = null;
  let requestInFlight = false;

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

  function resolveStatusMeta(status, rawStatus) {
    const normalized = String(status || '').trim().toLowerCase();
    if (normalized === 'running') {
      return { badge: 'text-bg-success', label: 'Запущен', details: '' };
    }
    if (normalized === 'stopped') {
      return { badge: 'text-bg-secondary', label: 'Остановлен', details: '' };
    }
    if (normalized === 'inactive') {
      return { badge: 'text-bg-warning', label: 'Выключен', details: '' };
    }
    const details = rawStatus && rawStatus !== 'unknown' ? rawStatus : 'ошибка статуса';
    return { badge: 'text-bg-danger', label: 'Ошибка', details };
  }

  function renderSummary(summary) {
    Object.entries(metricNodes).forEach(([key, node]) => {
      if (!node) {
        return;
      }
      const value = summary && summary[key] !== undefined ? Number(summary[key]) : 0;
      node.textContent = Number.isFinite(value) ? value.toLocaleString('ru-RU') : '0';
    });
  }

  function renderBots(items) {
    if (!tableBody) {
      return;
    }
    if (!Array.isArray(items) || items.length === 0) {
      tableBody.innerHTML = emptyRow;
      return;
    }
    tableBody.innerHTML = items.map((item) => {
      const status = resolveStatusMeta(item?.status, item?.raw_status);
      const channelName = item?.channel_name || `Канал #${item?.channel_id ?? '—'}`;
      const botName = item?.bot_name || item?.bot_username || 'Без имени бота';
      const startedAt = item?.status === 'running' ? formatTimestamp(item?.started_at) : '—';
      const details = status.details ? `<div class="small text-muted mt-1">${escapeHtml(status.details)}</div>` : '';
      return `
        <tr>
          <td>
            <div class="fw-semibold">${escapeHtml(channelName)}</div>
            <div class="small text-muted">#${escapeHtml(item?.channel_id ?? '—')}</div>
          </td>
          <td>
            <div>${escapeHtml(botName)}</div>
            <div class="small text-muted">${escapeHtml(item?.platform || 'unknown')}</div>
          </td>
          <td>
            <span class="badge ${status.badge}">${escapeHtml(status.label)}</span>
            ${details}
          </td>
          <td>${escapeHtml(startedAt)}</td>
          <td class="small text-muted">${item?.active ? 'Активный канал' : 'Канал выключен'}</td>
        </tr>
      `;
    }).join('');
  }

  function setLoadingState() {
    if (tableBody) {
      tableBody.innerHTML = loadingRow;
    }
    if (errorEl) {
      errorEl.classList.add('d-none');
      errorEl.textContent = '';
    }
  }

  function setErrorState(message) {
    if (!errorEl) {
      return;
    }
    errorEl.textContent = message;
    errorEl.classList.remove('d-none');
  }

  async function loadBotRuntimeOverview() {
    if (requestInFlight) {
      return;
    }
    requestInFlight = true;
    if (refreshButton) {
      refreshButton.disabled = true;
    }
    setLoadingState();
    try {
      const response = await fetch('/api/analytics/bot-runtime', {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      renderSummary(payload.summary || {});
      renderBots(payload.bots || []);
      if (updatedAtEl) {
        updatedAtEl.textContent = `Обновлено: ${new Date().toLocaleString('ru-RU')}`;
      }
    } catch (error) {
      renderSummary({});
      renderBots([]);
      setErrorState(`Не удалось загрузить статусы ботов: ${error.message}`);
      if (updatedAtEl) {
        updatedAtEl.textContent = 'Обновление не выполнено';
      }
    } finally {
      requestInFlight = false;
      if (refreshButton) {
        refreshButton.disabled = false;
      }
    }
  }

  function startPolling() {
    if (pollTimer) {
      return;
    }
    pollTimer = window.setInterval(loadBotRuntimeOverview, 30000);
  }

  if (refreshButton) {
    refreshButton.addEventListener('click', loadBotRuntimeOverview);
  }

  window.addEventListener('panel:sse:sidebar_bots_changed', () => {
    void loadBotRuntimeOverview();
  });

  startPolling();
  void loadBotRuntimeOverview();
})();

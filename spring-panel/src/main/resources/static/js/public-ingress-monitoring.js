(function () {
  const tableBody = document.getElementById('publicIngressTableBody');
  const createForm = document.getElementById('publicIngressCreateForm');
  const editForm = document.getElementById('publicIngressEditForm');
  const refreshAllBtn = document.getElementById('refreshAllPublicIngressBtn');
  const openCreateModalBtn = document.getElementById('openPublicIngressCreateModalBtn');
  const historyTableBody = document.getElementById('publicIngressHistoryTableBody');
  const historyCaption = document.getElementById('publicIngressHistoryCaption');

  const createModal = document.getElementById('publicIngressCreateModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('publicIngressCreateModal'))
    : null;
  const editModal = document.getElementById('publicIngressEditModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('publicIngressEditModal'))
    : null;
  const historyModal = document.getElementById('publicIngressHistoryModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('publicIngressHistoryModal'))
    : null;

  const inputs = {
    create: {
      name: document.getElementById('publicIngressMonitorNameInput'),
      endpoint: document.getElementById('publicIngressEndpointInput'),
      expectedStatus: document.getElementById('publicIngressExpectedStatusInput'),
      enabled: document.getElementById('publicIngressEnabledInput'),
    },
    edit: {
      id: document.getElementById('editPublicIngressMonitorId'),
      name: document.getElementById('editPublicIngressMonitorNameInput'),
      endpoint: document.getElementById('editPublicIngressEndpointInput'),
      expectedStatus: document.getElementById('editPublicIngressExpectedStatusInput'),
      enabled: document.getElementById('editPublicIngressEnabledInput'),
    },
  };

  const overview = {
    percent: document.getElementById('publicIngressAvailabilityPercent'),
    total: document.getElementById('publicIngressTotal'),
    up: document.getElementById('publicIngressUp'),
    down: document.getElementById('publicIngressDown'),
    disabled: document.getElementById('publicIngressDisabled'),
    unknown: document.getElementById('publicIngressUnknown'),
    caption: document.getElementById('publicIngressOverviewCaption'),
  };

  let monitors = [];

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function showMessage(message, type) {
    if (typeof window.showPopup === 'function') {
      window.showPopup(message, type);
      return;
    }
    window.alert(message);
  }

  function getCookieValue(name) {
    const cookies = document.cookie ? document.cookie.split(';') : [];
    const encodedName = `${encodeURIComponent(name)}=`;
    for (const raw of cookies) {
      const value = raw.trim();
      if (value.startsWith(encodedName)) {
        return decodeURIComponent(value.slice(encodedName.length));
      }
    }
    return '';
  }

  function getCsrfToken() {
    const tokenFromMeta = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    if (tokenFromMeta) return tokenFromMeta;
    const tokenFromInput = document.querySelector('input[name="_csrf"]')?.value || '';
    if (tokenFromInput) return tokenFromInput;
    return getCookieValue('XSRF-TOKEN');
  }

  function withCsrf(init = {}) {
    const method = String(init.method || 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) return init;
    const token = getCsrfToken();
    if (!token) return init;
    const headers = new Headers(init.headers || {});
    if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token);
    if (!headers.has('X-CSRF-TOKEN')) headers.set('X-CSRF-TOKEN', token);
    return { ...init, headers };
  }

  async function requestJson(url, init = {}) {
    const response = await fetch(url, {
      credentials: 'same-origin',
      cache: 'no-store',
      ...withCsrf(init),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    return data;
  }

  function formatDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('ru-RU');
  }

  function statusBadge(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (normalized === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (normalized === 'warning') return '<span class="badge text-bg-warning">Warning</span>';
    if (normalized === 'critical') return '<span class="badge text-bg-danger">Critical</span>';
    if (normalized === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Error</span>';
  }

  function availabilityBadge(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (normalized === 'up') return '<span class="badge text-bg-success">UP</span>';
    if (normalized === 'down') return '<span class="badge text-bg-danger">DOWN</span>';
    if (normalized === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Unknown</span>';
  }

  function renderOverview(data) {
    const total = Number(data?.total || 0);
    const up = Number(data?.up || 0);
    const down = Number(data?.down || 0);
    const disabled = Number(data?.disabled || 0);
    const unknown = Number(data?.unknown || 0);
    const percent = Number(data?.availability_percent || 0);
    if (overview.percent) overview.percent.textContent = `${percent.toFixed(1)}%`;
    if (overview.total) overview.total.textContent = String(total);
    if (overview.up) overview.up.textContent = String(up);
    if (overview.down) overview.down.textContent = String(down);
    if (overview.disabled) overview.disabled.textContent = String(disabled);
    if (overview.unknown) overview.unknown.textContent = String(unknown);
    if (overview.caption) {
      overview.caption.textContent = `Активных endpoint-ов: ${Math.max(0, total - disabled)} из ${total}. Warning обычно означает близкий TLS expiry.`;
    }
  }

  function renderTable() {
    if (!tableBody) return;
    if (!monitors.length) {
      tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Public ingress monitor-ы ещё не добавлены.</td></tr>';
      return;
    }
    tableBody.innerHTML = '';
    monitors.forEach((item) => {
      const tlsCell = item.scheme === 'https'
        ? `<div>${formatDateTime(item.last_tls_expires_at)}</div><div class="small text-muted">${item.last_tls_days_left ?? '—'} d</div>`
        : '<div class="small text-muted">Skipped for HTTP</div>';
      const row = document.createElement('tr');
      row.dataset.monitorId = String(item.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(item.monitor_name || 'Monitor')}</div>
          <div class="small text-muted">${escapeHtml(item.scheme || '')}://${escapeHtml(item.host || '')}:${escapeHtml(item.port || '')}</div>
        </td>
        <td>
          <div class="font-monospace small">${escapeHtml(item.endpoint_url || '')}</div>
          <div class="small text-muted">expected: ${item.expected_http_status ? escapeHtml(item.expected_http_status) : '2xx-3xx'}</div>
        </td>
        <td>
          <div>${formatDateTime(item.last_dns_resolved_at)}</div>
          <div class="small text-muted">${escapeHtml(item.last_dns_addresses || '—')}</div>
        </td>
        <td>
          <div>${item.last_http_status ? escapeHtml(item.last_http_status) : '—'}</div>
          <div class="small text-muted">${item.last_http_duration_ms ? `${escapeHtml(item.last_http_duration_ms)} ms` : '—'}</div>
        </td>
        <td>${tlsCell}</td>
        <td>
          <div class="mb-1">${statusBadge(item.status_level || item.last_status)}</div>
          <div>${availabilityBadge(item.availability)}</div>
        </td>
        <td>
          <div class="small">${escapeHtml(item.last_summary || '—')}</div>
          ${item.last_error_message ? `<div class="small text-danger mt-1">${escapeHtml(item.last_error_message)}</div>` : ''}
        </td>
        <td>${formatDateTime(item.last_checked_at)}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm flex-wrap justify-content-end">
            <button class="btn btn-outline-secondary" type="button" data-action="refresh">Refresh</button>
            <button class="btn btn-outline-secondary" type="button" data-action="history">History</button>
            <button class="btn btn-outline-primary" type="button" data-action="edit">Edit</button>
            <button class="btn btn-outline-danger" type="button" data-action="delete">Delete</button>
          </div>
        </td>
      `;
      tableBody.appendChild(row);
    });
  }

  async function loadMonitors() {
    if (tableBody) {
      tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Загрузка...</td></tr>';
    }
    try {
      const data = await requestJson('/api/monitoring/public-ingress/monitors');
      monitors = Array.isArray(data.items) ? data.items : [];
      renderOverview(data.availability_overview || {});
      renderTable();
    } catch (error) {
      if (tableBody) {
        tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${escapeHtml(error.message)}</td></tr>`;
      }
      showMessage(`Не удалось загрузить public ingress monitor-ы: ${error.message}`, 'error');
    }
  }

  function resetCreateForm() {
    createForm?.reset();
    if (inputs.create.enabled) inputs.create.enabled.checked = true;
  }

  function getMonitorById(id) {
    const numericId = Number(id);
    return monitors.find((item) => Number(item.id) === numericId) || null;
  }

  function payloadFromForm(source) {
    const expectedValue = source.expectedStatus?.value;
    return {
      monitorName: source.name?.value || '',
      endpointUrl: source.endpoint?.value || '',
      expectedHttpStatus: expectedValue ? Number(expectedValue) : null,
      enabled: source.enabled?.checked ?? true,
    };
  }

  async function createMonitor(event) {
    event.preventDefault();
    try {
      await requestJson('/api/monitoring/public-ingress/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payloadFromForm(inputs.create)),
      });
      createModal?.hide();
      resetCreateForm();
      await loadMonitors();
      showMessage('Public ingress monitor создан.', 'success');
    } catch (error) {
      showMessage(`Не удалось создать monitor: ${error.message}`, 'error');
    }
  }

  function openEditModal(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !editModal) return;
    if (inputs.edit.id) inputs.edit.id.value = String(monitor.id);
    if (inputs.edit.name) inputs.edit.name.value = monitor.monitor_name || '';
    if (inputs.edit.endpoint) inputs.edit.endpoint.value = monitor.endpoint_url || '';
    if (inputs.edit.expectedStatus) inputs.edit.expectedStatus.value = monitor.expected_http_status || '';
    if (inputs.edit.enabled) inputs.edit.enabled.checked = Boolean(monitor.enabled);
    editModal.show();
  }

  async function saveMonitor(event) {
    event.preventDefault();
    const monitorId = Number(inputs.edit.id?.value || 0);
    try {
      await requestJson(`/api/monitoring/public-ingress/monitors/${monitorId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payloadFromForm(inputs.edit)),
      });
      editModal?.hide();
      await loadMonitors();
      showMessage('Изменения monitor-а сохранены.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить monitor: ${error.message}`, 'error');
    }
  }

  async function refreshMonitor(monitorId) {
    try {
      await requestJson(`/api/monitoring/public-ingress/monitors/${monitorId}/refresh`, { method: 'POST' });
      await loadMonitors();
      showMessage('Public ingress probe обновлён.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitor: ${error.message}`, 'error');
    }
  }

  async function deleteMonitor(monitorId) {
    if (!window.confirm('Удалить public ingress monitor?')) return;
    try {
      await requestJson(`/api/monitoring/public-ingress/monitors/${monitorId}`, { method: 'DELETE' });
      await loadMonitors();
      showMessage('Public ingress monitor удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить monitor: ${error.message}`, 'error');
    }
  }

  async function refreshAll() {
    if (refreshAllBtn) {
      refreshAllBtn.disabled = true;
      refreshAllBtn.textContent = 'Обновляем...';
    }
    try {
      await requestJson('/api/monitoring/public-ingress/refresh', { method: 'POST' });
      await loadMonitors();
      showMessage('Все public ingress monitor-ы обновлены.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitor-ы: ${error.message}`, 'error');
    } finally {
      if (refreshAllBtn) {
        refreshAllBtn.disabled = false;
        refreshAllBtn.textContent = 'Обновить всё';
      }
    }
  }

  async function openHistory(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !historyModal) return;
    if (historyCaption) {
      historyCaption.textContent = `Последние проверки для "${monitor.monitor_name || 'monitor'}".`;
    }
    if (historyTableBody) {
      historyTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Загрузка...</td></tr>';
    }
    historyModal.show();
    try {
      const data = await requestJson(`/api/monitoring/public-ingress/monitors/${monitorId}/history`);
      const items = Array.isArray(data.items) ? data.items : [];
      if (!items.length) {
        historyTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">История пока пуста.</td></tr>';
        return;
      }
      historyTableBody.innerHTML = '';
      items.forEach((item) => {
        const row = document.createElement('tr');
        row.innerHTML = `
          <td>${formatDateTime(item.created_at)}</td>
          <td>${escapeHtml(item.check_kind || '—')}</td>
          <td>${statusBadge(item.status)}</td>
          <td>${escapeHtml(item.summary || '—')}</td>
          <td class="small">${escapeHtml(item.details_excerpt || '—')}</td>
          <td>${item.duration_ms ? `${escapeHtml(item.duration_ms)} ms` : '—'}</td>
        `;
        historyTableBody.appendChild(row);
      });
    } catch (error) {
      historyTableBody.innerHTML = `<tr><td colspan="6" class="text-center text-danger py-3">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr[data-monitor-id]');
    if (!row) return;
    const monitorId = Number(row.dataset.monitorId);
    if (!Number.isFinite(monitorId)) return;
    const action = button.dataset.action;
    if (action === 'refresh') {
      refreshMonitor(monitorId);
    } else if (action === 'history') {
      openHistory(monitorId);
    } else if (action === 'edit') {
      openEditModal(monitorId);
    } else if (action === 'delete') {
      deleteMonitor(monitorId);
    }
  });

  openCreateModalBtn?.addEventListener('click', () => {
    resetCreateForm();
    createModal?.show();
  });
  createForm?.addEventListener('submit', createMonitor);
  editForm?.addEventListener('submit', saveMonitor);
  refreshAllBtn?.addEventListener('click', refreshAll);

  loadMonitors();
})();

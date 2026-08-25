(function () {
  const monitorsTableBody = document.getElementById('smtpMonitorsTableBody');
  const routeTypeTableBody = document.getElementById('routeTypeTableBody');
  const routeFailuresTableBody = document.getElementById('routeFailuresTableBody');
  const historyTableBody = document.getElementById('smtpHistoryTableBody');
  const historyCaption = document.getElementById('smtpHistoryCaption');
  const createForm = document.getElementById('smtpMonitorCreateForm');
  const editForm = document.getElementById('smtpMonitorEditForm');
  const refreshAllBtn = document.getElementById('refreshAllSmtpMonitorsBtn');
  const refreshRouteHealthBtn = document.getElementById('refreshRouteHealthBtn');
  const openCreateModalBtn = document.getElementById('openSmtpMonitorCreateModalBtn');

  const createModal = document.getElementById('smtpMonitorCreateModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('smtpMonitorCreateModal'))
    : null;
  const editModal = document.getElementById('smtpMonitorEditModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('smtpMonitorEditModal'))
    : null;
  const historyModal = document.getElementById('smtpMonitorHistoryModal') && window.bootstrap
    ? new bootstrap.Modal(document.getElementById('smtpMonitorHistoryModal'))
    : null;

  const inputs = {
    create: {
      name: document.getElementById('smtpMonitorNameInput'),
      host: document.getElementById('smtpRelayHostInput'),
      port: document.getElementById('smtpRelayPortInput'),
      protocolMode: document.getElementById('smtpProtocolModeInput'),
      timeoutMs: document.getElementById('smtpTimeoutInput'),
      enabled: document.getElementById('smtpEnabledInput'),
    },
    edit: {
      id: document.getElementById('editSmtpMonitorId'),
      name: document.getElementById('editSmtpMonitorNameInput'),
      host: document.getElementById('editSmtpRelayHostInput'),
      port: document.getElementById('editSmtpRelayPortInput'),
      protocolMode: document.getElementById('editSmtpProtocolModeInput'),
      timeoutMs: document.getElementById('editSmtpTimeoutInput'),
      enabled: document.getElementById('editSmtpEnabledInput'),
    },
  };

  const overview = {
    percent: document.getElementById('smtpAvailabilityPercent'),
    total: document.getElementById('smtpTotal'),
    up: document.getElementById('smtpUp'),
    down: document.getElementById('smtpDown'),
    disabled: document.getElementById('smtpDisabled'),
    unknown: document.getElementById('smtpUnknown'),
    caption: document.getElementById('smtpOverviewCaption'),
  };

  const routeSummary = {
    delivered24h: document.getElementById('routeDelivered24h'),
    failed24h: document.getElementById('routeFailed24h'),
    pending24h: document.getElementById('routePending24h'),
    failedBacklog: document.getElementById('routeFailedBacklog'),
    transientFailures: document.getElementById('routeTransientFailures'),
    successRate24h: document.getElementById('routeSuccessRate24h'),
    statusBadge: document.getElementById('routeOverallStatusBadge'),
    generatedAt: document.getElementById('routeGeneratedAt'),
    summary: document.getElementById('routeHealthSummary'),
    deliveryTimes: document.getElementById('routeDeliveryTimes'),
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
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleString('ru-RU');
  }

  function statusBadge(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (normalized === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (normalized === 'warning') return '<span class="badge text-bg-warning">Warning</span>';
    if (normalized === 'critical') return '<span class="badge text-bg-danger">Critical</span>';
    if (normalized === 'idle') return '<span class="badge text-bg-secondary">Idle</span>';
    if (normalized === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Unknown</span>';
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
      overview.caption.textContent = `Active relay probes: ${Math.max(0, total - disabled)} of ${total}. Use STARTTLS/TLS where production SMTP contract requires it.`;
    }
  }

  function renderMonitorsTable() {
    if (!monitorsTableBody) return;
    if (!monitors.length) {
      monitorsTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">SMTP relay monitors have not been added yet.</td></tr>';
      return;
    }
    monitorsTableBody.innerHTML = '';
    monitors.forEach((item) => {
      const row = document.createElement('tr');
      row.dataset.monitorId = String(item.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(item.monitor_name || 'Monitor')}</div>
          <div class="small text-muted">timeout ${escapeHtml(item.connect_timeout_ms || '-')} ms</div>
        </td>
        <td>
          <div class="font-monospace">${escapeHtml(item.relay_host || '')}:${escapeHtml(item.relay_port || '')}</div>
          <div class="small text-muted">mode=${escapeHtml(item.protocol_mode || 'starttls')}</div>
        </td>
        <td>
          <div class="mb-1">${statusBadge(item.status_level || item.last_status)}</div>
          <div>${availabilityBadge(item.availability)}</div>
        </td>
        <td>
          <div class="small">${escapeHtml(item.last_banner || '-')}</div>
          <div class="small text-muted">${escapeHtml(item.last_tls_protocol || '-')} ${escapeHtml(item.last_tls_cipher_suite || '')}</div>
        </td>
        <td>
          <div class="small">${escapeHtml(item.last_summary || '-')}</div>
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
      monitorsTableBody.appendChild(row);
    });
  }

  async function loadMonitors() {
    if (monitorsTableBody) {
      monitorsTableBody.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">Загрузка...</td></tr>';
    }
    try {
      const data = await requestJson('/api/monitoring/smtp-notifications/monitors');
      monitors = Array.isArray(data.items) ? data.items : [];
      renderOverview(data.availability_overview || {});
      renderMonitorsTable();
    } catch (error) {
      if (monitorsTableBody) {
        monitorsTableBody.innerHTML = `<tr><td colspan="7" class="text-center text-danger py-4">${escapeHtml(error.message)}</td></tr>`;
      }
      showMessage(`Не удалось загрузить SMTP monitors: ${error.message}`, 'error');
    }
  }

  function getMonitorById(id) {
    const numericId = Number(id);
    return monitors.find((item) => Number(item.id) === numericId) || null;
  }

  function payloadFromForm(source) {
    return {
      monitorName: source.name?.value || '',
      relayHost: source.host?.value || '',
      relayPort: source.port?.value ? Number(source.port.value) : null,
      protocolMode: source.protocolMode?.value || 'starttls',
      connectTimeoutMs: source.timeoutMs?.value ? Number(source.timeoutMs.value) : null,
      enabled: source.enabled?.checked ?? true,
    };
  }

  function resetCreateForm() {
    createForm?.reset();
    if (inputs.create.port) inputs.create.port.value = '587';
    if (inputs.create.protocolMode) inputs.create.protocolMode.value = 'starttls';
    if (inputs.create.timeoutMs) inputs.create.timeoutMs.value = '5000';
    if (inputs.create.enabled) inputs.create.enabled.checked = true;
  }

  async function createMonitor(event) {
    event.preventDefault();
    try {
      await requestJson('/api/monitoring/smtp-notifications/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payloadFromForm(inputs.create)),
      });
      createModal?.hide();
      resetCreateForm();
      await loadMonitors();
      showMessage('SMTP monitor created.', 'success');
    } catch (error) {
      showMessage(`Не удалось создать monitor: ${error.message}`, 'error');
    }
  }

  function openEditModal(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !editModal) return;
    if (inputs.edit.id) inputs.edit.id.value = String(monitor.id);
    if (inputs.edit.name) inputs.edit.name.value = monitor.monitor_name || '';
    if (inputs.edit.host) inputs.edit.host.value = monitor.relay_host || '';
    if (inputs.edit.port) inputs.edit.port.value = monitor.relay_port || '587';
    if (inputs.edit.protocolMode) inputs.edit.protocolMode.value = monitor.protocol_mode || 'starttls';
    if (inputs.edit.timeoutMs) inputs.edit.timeoutMs.value = monitor.connect_timeout_ms || '5000';
    if (inputs.edit.enabled) inputs.edit.enabled.checked = Boolean(monitor.enabled);
    editModal.show();
  }

  async function saveMonitor(event) {
    event.preventDefault();
    const monitorId = Number(inputs.edit.id?.value || 0);
    try {
      await requestJson(`/api/monitoring/smtp-notifications/monitors/${monitorId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payloadFromForm(inputs.edit)),
      });
      editModal?.hide();
      await loadMonitors();
      showMessage('Monitor changes saved.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить monitor: ${error.message}`, 'error');
    }
  }

  async function refreshMonitor(monitorId) {
    try {
      await requestJson(`/api/monitoring/smtp-notifications/monitors/${monitorId}/refresh`, { method: 'POST' });
      await loadMonitors();
      await loadRouteHealth();
      showMessage('SMTP probe refreshed.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitor: ${error.message}`, 'error');
    }
  }

  async function deleteMonitor(monitorId) {
    if (!window.confirm('Удалить SMTP relay monitor?')) return;
    try {
      await requestJson(`/api/monitoring/smtp-notifications/monitors/${monitorId}`, { method: 'DELETE' });
      await loadMonitors();
      showMessage('SMTP monitor deleted.', 'success');
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
      await requestJson('/api/monitoring/smtp-notifications/refresh', { method: 'POST' });
      await loadMonitors();
      await loadRouteHealth();
      showMessage('All SMTP monitors refreshed.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitors: ${error.message}`, 'error');
    } finally {
      if (refreshAllBtn) {
        refreshAllBtn.disabled = false;
        refreshAllBtn.textContent = 'Обновить все';
      }
    }
  }

  async function openHistory(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !historyModal) return;
    if (historyCaption) {
      historyCaption.textContent = `Recent SMTP probe timeline for "${monitor.monitor_name || 'monitor'}".`;
    }
    if (historyTableBody) {
      historyTableBody.innerHTML = '<tr><td colspan="6" class="text-center text-muted py-3">Загрузка...</td></tr>';
    }
    historyModal.show();
    try {
      const data = await requestJson(`/api/monitoring/smtp-notifications/monitors/${monitorId}/history`);
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
          <td>${escapeHtml(item.check_kind || '-')}</td>
          <td>${statusBadge(item.status)}</td>
          <td>${escapeHtml(item.summary || '-')}</td>
          <td class="small">${escapeHtml(item.details_excerpt || '-')}</td>
          <td>${item.duration_ms ? `${escapeHtml(item.duration_ms)} ms` : '-'}</td>
        `;
        historyTableBody.appendChild(row);
      });
    } catch (error) {
      historyTableBody.innerHTML = `<tr><td colspan="6" class="text-center text-danger py-3">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  function renderRouteHealth(snapshot, routeTypes, failures) {
    if (routeSummary.delivered24h) routeSummary.delivered24h.textContent = String(snapshot?.delivered_24h ?? '-');
    if (routeSummary.failed24h) routeSummary.failed24h.textContent = String(snapshot?.failed_24h ?? '-');
    if (routeSummary.pending24h) routeSummary.pending24h.textContent = String(snapshot?.pending_24h ?? '-');
    if (routeSummary.failedBacklog) routeSummary.failedBacklog.textContent = String(snapshot?.failed_backlog ?? '-');
    if (routeSummary.transientFailures) routeSummary.transientFailures.textContent = String(snapshot?.transient_failures ?? '-');
    if (routeSummary.successRate24h) {
      routeSummary.successRate24h.textContent = snapshot?.success_rate_24h === null || snapshot?.success_rate_24h === undefined
        ? '-'
        : `${Number(snapshot.success_rate_24h).toFixed(1)}%`;
    }
    if (routeSummary.statusBadge) {
      routeSummary.statusBadge.innerHTML = `Status: ${statusBadge(snapshot?.overall_status || 'unknown')}`;
    }
    if (routeSummary.generatedAt) {
      routeSummary.generatedAt.textContent = `Generated at ${formatDateTime(snapshot?.generated_at)}`;
    }
    if (routeSummary.summary) {
      routeSummary.summary.textContent = `Backlog queued=${snapshot?.queued_backlog ?? 0}, processing=${snapshot?.processing_backlog ?? 0}, failed=${snapshot?.failed_backlog ?? 0}. Window ${snapshot?.window_hours ?? 24}h.`;
    }
    if (routeSummary.deliveryTimes) {
      routeSummary.deliveryTimes.innerHTML = `Last delivered: ${escapeHtml(formatDateTime(snapshot?.last_delivered_at))}<br>Last failed: ${escapeHtml(formatDateTime(snapshot?.last_failed_at))}`;
    }

    if (routeTypeTableBody) {
      const items = Array.isArray(routeTypes) ? routeTypes : [];
      if (!items.length) {
        routeTypeTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">No delivery events in the selected window.</td></tr>';
      } else {
        routeTypeTableBody.innerHTML = '';
        items.forEach((item) => {
          const row = document.createElement('tr');
          row.innerHTML = `
            <td>${escapeHtml(item.route_type || 'unknown')}</td>
            <td>${escapeHtml(item.delivered_24h ?? 0)}</td>
            <td class="text-danger">${escapeHtml(item.failed_24h ?? 0)}</td>
            <td class="text-warning">${escapeHtml(item.pending_24h ?? 0)}</td>
            <td>${item.success_rate_24h === null || item.success_rate_24h === undefined ? '-' : `${Number(item.success_rate_24h).toFixed(1)}%`}</td>
          `;
          routeTypeTableBody.appendChild(row);
        });
      }
    }

    if (routeFailuresTableBody) {
      const items = Array.isArray(failures) ? failures : [];
      if (!items.length) {
        routeFailuresTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Recent failed deliveries are not present.</td></tr>';
      } else {
        routeFailuresTableBody.innerHTML = '';
        items.forEach((item) => {
          const row = document.createElement('tr');
          row.innerHTML = `
            <td>
              <div class="fw-semibold">${escapeHtml(item.incident_id ?? '-')}</div>
              <div class="small text-muted">${escapeHtml(item.event_type || '-')}</div>
            </td>
            <td>
              <div>${escapeHtml(item.route_type || '-')}</div>
              <div class="small text-muted font-monospace">${escapeHtml(item.route_target || '-')}</div>
            </td>
            <td>
              <div>${escapeHtml(item.attempt_count ?? '-')}</div>
              <div class="small text-muted">${escapeHtml(item.failure_kind || '-')} / ${escapeHtml(item.error_kind || '-')}</div>
            </td>
            <td class="small text-danger">${escapeHtml(item.last_error || '-')}</td>
            <td>${formatDateTime(item.updated_at)}</td>
          `;
          routeFailuresTableBody.appendChild(row);
        });
      }
    }
  }

  async function loadRouteHealth() {
    if (routeTypeTableBody) {
      routeTypeTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Загрузка...</td></tr>';
    }
    if (routeFailuresTableBody) {
      routeFailuresTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Загрузка...</td></tr>';
    }
    try {
      const data = await requestJson('/api/monitoring/smtp-notifications/route-health');
      renderRouteHealth(data.snapshot || {}, data.route_types || [], data.recent_failures || []);
    } catch (error) {
      if (routeTypeTableBody) {
        routeTypeTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger py-3">${escapeHtml(error.message)}</td></tr>`;
      }
      if (routeFailuresTableBody) {
        routeFailuresTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger py-3">${escapeHtml(error.message)}</td></tr>`;
      }
      if (routeSummary.summary) {
        routeSummary.summary.textContent = `Route health is unavailable: ${error.message}`;
      }
    }
  }

  monitorsTableBody?.addEventListener('click', (event) => {
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
  refreshRouteHealthBtn?.addEventListener('click', loadRouteHealth);

  loadMonitors();
  loadRouteHealth();
})();

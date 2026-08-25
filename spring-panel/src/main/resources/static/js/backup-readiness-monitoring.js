(function () {
  const tableBody = document.getElementById('backupReadinessTableBody');
  const createForm = document.getElementById('backupMonitorCreateForm');
  const editForm = document.getElementById('backupMonitorEditForm');
  const restoreForm = document.getElementById('backupRestoreEvidenceForm');
  const refreshAllBtn = document.getElementById('refreshAllBackupMonitorsBtn');
  const openCreateModalBtn = document.getElementById('openBackupMonitorCreateModalBtn');
  const historyTableBody = document.getElementById('backupHistoryTableBody');
  const historyCaption = document.getElementById('backupHistoryCaption');

  const createModalEl = document.getElementById('backupMonitorCreateModal');
  const editModalEl = document.getElementById('backupMonitorEditModal');
  const restoreModalEl = document.getElementById('backupRestoreEvidenceModal');
  const historyModalEl = document.getElementById('backupHistoryModal');

  const createModal = createModalEl && window.bootstrap ? new bootstrap.Modal(createModalEl) : null;
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;
  const restoreModal = restoreModalEl && window.bootstrap ? new bootstrap.Modal(restoreModalEl) : null;
  const historyModal = historyModalEl && window.bootstrap ? new bootstrap.Modal(historyModalEl) : null;

  const overview = {
    percent: document.getElementById('backupAvailabilityPercent'),
    total: document.getElementById('backupMonitorTotal'),
    up: document.getElementById('backupMonitorUp'),
    down: document.getElementById('backupMonitorDown'),
    disabled: document.getElementById('backupMonitorDisabled'),
    unknown: document.getElementById('backupMonitorUnknown'),
    caption: document.getElementById('backupOverviewCaption'),
  };

  const createInputs = {
    name: document.getElementById('backupMonitorNameInput'),
    kind: document.getElementById('backupKindInput'),
    path: document.getElementById('backupPathPatternInput'),
    freshnessHours: document.getElementById('backupFreshnessThresholdInput'),
    restoreDays: document.getElementById('backupRestoreThresholdInput'),
    enabled: document.getElementById('backupEnabledInput'),
  };

  const editInputs = {
    id: document.getElementById('editBackupMonitorId'),
    name: document.getElementById('editBackupMonitorNameInput'),
    kind: document.getElementById('editBackupKindInput'),
    path: document.getElementById('editBackupPathPatternInput'),
    freshnessHours: document.getElementById('editBackupFreshnessThresholdInput'),
    restoreDays: document.getElementById('editBackupRestoreThresholdInput'),
    enabled: document.getElementById('editBackupEnabledInput'),
  };

  const restoreInputs = {
    id: document.getElementById('restoreEvidenceMonitorId'),
    verifiedAt: document.getElementById('restoreEvidenceVerifiedAtInput'),
    note: document.getElementById('restoreEvidenceNoteInput'),
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
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
      return init;
    }
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

  function formatDateForInput(value) {
    const date = value ? new Date(value) : new Date();
    if (Number.isNaN(date.getTime())) return '';
    const pad = (part) => String(part).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function toIsoFromLocalInput(value) {
    if (!value) return null;
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? null : date.toISOString();
  }

  function formatBytes(bytes) {
    const value = Number(bytes);
    if (!Number.isFinite(value) || value < 0) return '—';
    if (value < 1024) return `${value} B`;
    if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
    if (value < 1024 * 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
    return `${(value / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  function normalizeStatus(value) {
    return String(value || '').trim().toLowerCase();
  }

  function availabilityBadge(value) {
    const normalized = String(value || '').trim().toLowerCase();
    if (normalized === 'up') return '<span class="badge text-bg-success">UP</span>';
    if (normalized === 'down') return '<span class="badge text-bg-danger">DOWN</span>';
    if (normalized === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Unknown</span>';
  }

  function statusBadge(value) {
    const normalized = normalizeStatus(value);
    if (normalized === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (normalized === 'warning') return '<span class="badge text-bg-warning">Warning</span>';
    if (normalized === 'critical') return '<span class="badge text-bg-danger">Critical</span>';
    if (normalized === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Error</span>';
  }

  function renderOverview(data) {
    const info = data || {};
    const total = Number(info.total || 0);
    const up = Number(info.up || 0);
    const down = Number(info.down || 0);
    const disabled = Number(info.disabled || 0);
    const unknown = Number(info.unknown || 0);
    const percent = Number(info.availability_percent || 0);

    if (overview.percent) overview.percent.textContent = `${percent.toFixed(1)}%`;
    if (overview.total) overview.total.textContent = String(total);
    if (overview.up) overview.up.textContent = String(up);
    if (overview.down) overview.down.textContent = String(down);
    if (overview.disabled) overview.disabled.textContent = String(disabled);
    if (overview.unknown) overview.unknown.textContent = String(unknown);
    if (overview.caption) {
      overview.caption.textContent = `Активных monitor-ов: ${Math.max(0, total - disabled)} из ${total}. Healthy включает status ok и warning.`;
    }
  }

  function renderTable() {
    if (!tableBody) return;
    if (!monitors.length) {
      tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Мониторы backup readiness ещё не добавлены.</td></tr>';
      return;
    }

    tableBody.innerHTML = '';
    monitors.forEach((item) => {
      const row = document.createElement('tr');
      row.dataset.monitorId = String(item.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(item.monitor_name || 'Monitor')}</div>
          <div class="small text-muted">${escapeHtml(item.backup_kind || 'generic')}</div>
        </td>
        <td>
          <div class="font-monospace small">${escapeHtml(item.path_pattern || '')}</div>
          <div class="small text-muted">${escapeHtml(item.last_backup_path || '—')}</div>
        </td>
        <td>${statusBadge(item.status_level || item.last_status)}</td>
        <td>${availabilityBadge(item.availability)}</td>
        <td>
          <div>${formatDateTime(item.last_backup_at)}</div>
          <div class="small text-muted">${formatBytes(item.last_backup_size_bytes)}</div>
        </td>
        <td>
          <div>${formatDateTime(item.last_restore_verified_at)}</div>
          <div class="small text-muted">${escapeHtml(item.last_restore_note || '—')}</div>
        </td>
        <td>
          <div class="small">${escapeHtml(item.last_summary || '—')}</div>
          ${item.last_error_message ? `<div class="small text-danger mt-1">${escapeHtml(item.last_error_message)}</div>` : ''}
        </td>
        <td>${formatDateTime(item.last_checked_at)}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm flex-wrap justify-content-end">
            <button class="btn btn-outline-secondary" type="button" data-action="refresh">Refresh</button>
            <button class="btn btn-outline-primary" type="button" data-action="restore">Restore</button>
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
      const data = await requestJson('/api/monitoring/backups/monitors');
      monitors = Array.isArray(data.items) ? data.items : [];
      renderOverview(data.availability_overview || {});
      renderTable();
    } catch (error) {
      if (tableBody) {
        tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${escapeHtml(error.message)}</td></tr>`;
      }
      showMessage(`Не удалось загрузить backup readiness: ${error.message}`, 'error');
    }
  }

  function resetCreateForm() {
    createForm?.reset();
    if (createInputs.enabled) createInputs.enabled.checked = true;
    if (createInputs.freshnessHours) createInputs.freshnessHours.value = '24';
    if (createInputs.restoreDays) createInputs.restoreDays.value = '14';
  }

  function getMonitorById(id) {
    const numericId = Number(id);
    return monitors.find((item) => Number(item.id) === numericId) || null;
  }

  async function createMonitor(event) {
    event.preventDefault();
    const payload = {
      monitorName: createInputs.name?.value || '',
      backupKind: createInputs.kind?.value || '',
      pathPattern: createInputs.path?.value || '',
      enabled: createInputs.enabled?.checked ?? true,
      freshnessThresholdHours: Number(createInputs.freshnessHours?.value || 24),
      restoreThresholdDays: Number(createInputs.restoreDays?.value || 14),
    };
    try {
      await requestJson('/api/monitoring/backups/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      createModal?.hide();
      resetCreateForm();
      await loadMonitors();
      showMessage('Backup monitor создан.', 'success');
    } catch (error) {
      showMessage(`Не удалось создать monitor: ${error.message}`, 'error');
    }
  }

  function openEditModal(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !editModal) return;
    if (editInputs.id) editInputs.id.value = String(monitor.id);
    if (editInputs.name) editInputs.name.value = monitor.monitor_name || '';
    if (editInputs.kind) editInputs.kind.value = monitor.backup_kind || '';
    if (editInputs.path) editInputs.path.value = monitor.path_pattern || '';
    if (editInputs.freshnessHours) editInputs.freshnessHours.value = String(monitor.freshness_threshold_hours || 24);
    if (editInputs.restoreDays) editInputs.restoreDays.value = String(monitor.restore_threshold_days || 14);
    if (editInputs.enabled) editInputs.enabled.checked = Boolean(monitor.enabled);
    editModal.show();
  }

  async function saveMonitor(event) {
    event.preventDefault();
    const monitorId = Number(editInputs.id?.value || 0);
    const payload = {
      monitorName: editInputs.name?.value || '',
      backupKind: editInputs.kind?.value || '',
      pathPattern: editInputs.path?.value || '',
      enabled: editInputs.enabled?.checked ?? true,
      freshnessThresholdHours: Number(editInputs.freshnessHours?.value || 24),
      restoreThresholdDays: Number(editInputs.restoreDays?.value || 14),
    };
    try {
      await requestJson(`/api/monitoring/backups/monitors/${monitorId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      editModal?.hide();
      await loadMonitors();
      showMessage('Изменения monitor-а сохранены.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить monitor: ${error.message}`, 'error');
    }
  }

  async function deleteMonitor(monitorId) {
    if (!window.confirm('Удалить backup monitor?')) return;
    try {
      await requestJson(`/api/monitoring/backups/monitors/${monitorId}`, { method: 'DELETE' });
      await loadMonitors();
      showMessage('Backup monitor удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить monitor: ${error.message}`, 'error');
    }
  }

  async function refreshMonitor(monitorId) {
    try {
      await requestJson(`/api/monitoring/backups/monitors/${monitorId}/refresh`, { method: 'POST' });
      await loadMonitors();
      showMessage('Проверка monitor-а завершена.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitor: ${error.message}`, 'error');
    }
  }

  async function refreshAll() {
    if (refreshAllBtn) {
      refreshAllBtn.disabled = true;
      refreshAllBtn.textContent = 'Обновляем...';
    }
    try {
      await requestJson('/api/monitoring/backups/refresh', { method: 'POST' });
      await loadMonitors();
      showMessage('Все backup monitor-ы обновлены.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить monitors: ${error.message}`, 'error');
    } finally {
      if (refreshAllBtn) {
        refreshAllBtn.disabled = false;
        refreshAllBtn.textContent = 'Обновить всё';
      }
    }
  }

  function openRestoreModal(monitorId) {
    const monitor = getMonitorById(monitorId);
    if (!monitor || !restoreModal) return;
    if (restoreInputs.id) restoreInputs.id.value = String(monitor.id);
    if (restoreInputs.verifiedAt) restoreInputs.verifiedAt.value = formatDateForInput(monitor.last_restore_verified_at);
    if (restoreInputs.note) restoreInputs.note.value = monitor.last_restore_note || '';
    restoreModal.show();
  }

  async function confirmRestoreEvidence(event) {
    event.preventDefault();
    const monitorId = Number(restoreInputs.id?.value || 0);
    const payload = {
      verifiedAt: toIsoFromLocalInput(restoreInputs.verifiedAt?.value || ''),
      note: restoreInputs.note?.value || '',
    };
    try {
      await requestJson(`/api/monitoring/backups/monitors/${monitorId}/restore-evidence`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      restoreModal?.hide();
      await loadMonitors();
      showMessage('Restore evidence подтверждён.', 'success');
    } catch (error) {
      showMessage(`Не удалось подтвердить restore evidence: ${error.message}`, 'error');
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
      const data = await requestJson(`/api/monitoring/backups/monitors/${monitorId}/history`);
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
    } else if (action === 'restore') {
      openRestoreModal(monitorId);
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
  restoreForm?.addEventListener('submit', confirmRestoreEvidence);
  refreshAllBtn?.addEventListener('click', refreshAll);

  loadMonitors();
})();

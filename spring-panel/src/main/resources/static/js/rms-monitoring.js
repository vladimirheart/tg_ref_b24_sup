(function () {
  const POLL_INTERVAL_MS = 15000;

  const tableBody = document.getElementById('rmsMonitoringTableBody');
  const queueStateEl = document.getElementById('rmsQueueState');
  const createForm = document.getElementById('rmsCreateForm');
  const refreshLicensesBtn = document.getElementById('refreshRmsLicensesBtn');
  const refreshNetworkBtn = document.getElementById('refreshRmsNetworkBtn');
  const rmsAddressInput = document.getElementById('rmsAddressInput');
  const rmsLoginInput = document.getElementById('rmsLoginInput');
  const rmsPasswordInput = document.getElementById('rmsPasswordInput');
  const rmsEnabledInput = document.getElementById('rmsEnabledInput');

  const editModalEl = document.getElementById('rmsEditModal');
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;
  const editForm = document.getElementById('rmsEditForm');
  const editRmsIdInput = document.getElementById('editRmsId');
  const editRmsAddressInput = document.getElementById('editRmsAddressInput');
  const editRmsLoginInput = document.getElementById('editRmsLoginInput');
  const editRmsPasswordInput = document.getElementById('editRmsPasswordInput');
  const editRmsEnabledInput = document.getElementById('editRmsEnabledInput');
  const editRmsPasswordHint = document.getElementById('editRmsPasswordHint');

  let sites = [];
  let refreshState = null;
  let pollTimer = null;

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

  function formatDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('ru-RU');
  }

  function formatDaysLeft(value) {
    if (value === null || value === undefined || value === '') return '—';
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return '—';
    if (numeric < 0) return `Просрочено на ${Math.abs(numeric)} дн`;
    return `${numeric} дн`;
  }

  function normalizeStatus(value) {
    return String(value || '').trim().toLowerCase();
  }

  function licenseBadge(level) {
    if (level === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (level === 'warning') return '<span class="badge text-bg-warning">Внимание</span>';
    if (level === 'critical') return '<span class="badge text-bg-danger">Критично</span>';
    if (level === 'expired') return '<span class="badge bg-danger-subtle text-danger border border-danger-subtle">Просрочено</span>';
    if (level === 'disabled') return '<span class="badge text-bg-secondary">Отключено</span>';
    return '<span class="badge text-bg-secondary">Ошибка</span>';
  }

  function rmsBadge(level) {
    if (level === 'up') return '<span class="badge text-bg-success">Доступен</span>';
    if (level === 'down') return '<span class="badge text-bg-danger">Недоступен</span>';
    if (level === 'disabled') return '<span class="badge text-bg-secondary">Отключён</span>';
    return '<span class="badge text-bg-secondary">Нет данных</span>';
  }

  function queueBadge(running, queued) {
    if (running) return '<span class="badge text-bg-primary">В работе</span>';
    if (queued) return '<span class="badge text-bg-warning">В очереди</span>';
    return '<span class="badge text-bg-secondary">Свободно</span>';
  }

  function renderQueueState() {
    if (!queueStateEl) return;
    if (!refreshState) {
      queueStateEl.textContent = 'Нет данных о состоянии очередей.';
      return;
    }

    const licenses = refreshState.licenses || {};
    const network = refreshState.network || {};
    queueStateEl.innerHTML = `
      <div class="d-flex flex-column gap-1">
        <div>
          <strong>Лицензии:</strong> ${queueBadge(licenses.running, licenses.queued)}
          <span class="ms-2">последний запуск: ${escapeHtml(formatDateTime(licenses.last_requested_at))}</span>
          <span class="ms-2">последнее завершение: ${escapeHtml(formatDateTime(licenses.last_completed_at))}</span>
        </div>
        <div>
          <strong>Статусы RMS:</strong> ${queueBadge(network.running, network.queued)}
          <span class="ms-2">последний запуск: ${escapeHtml(formatDateTime(network.last_requested_at))}</span>
          <span class="ms-2">последнее завершение: ${escapeHtml(formatDateTime(network.last_completed_at))}</span>
        </div>
      </div>
    `;

    if (refreshLicensesBtn) {
      refreshLicensesBtn.disabled = Boolean(licenses.running || licenses.queued);
      refreshLicensesBtn.textContent = licenses.running
        ? 'Обновление лицензий...'
        : licenses.queued
          ? 'Лицензии в очереди'
          : 'Обновить лицензии';
    }
    if (refreshNetworkBtn) {
      refreshNetworkBtn.disabled = Boolean(network.running || network.queued);
      refreshNetworkBtn.textContent = network.running
        ? 'Обновление статусов...'
        : network.queued
          ? 'Статусы в очереди'
          : 'Обновить статусы RMS';
    }
  }

  function renderEmptyState(message, className) {
    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="7" class="text-center ${className} py-4">${escapeHtml(message)}</td></tr>`;
  }

  function renderSites() {
    if (!tableBody) return;
    if (!sites.length) {
      renderEmptyState('Пока нет RMS для мониторинга.', 'text-muted');
      return;
    }

    tableBody.innerHTML = '';
    sites.forEach((site) => {
      const licenseLevel = normalizeStatus(site.license_status_level || site.license_status);
      const rmsLevel = normalizeStatus(site.rms_status_level || site.rms_status);
      const tracerouteButton = site.has_traceroute_report
        ? `<a class="btn btn-outline-secondary btn-sm" href="/api/monitoring/rms/sites/${encodeURIComponent(site.id)}/traceroute">Трассировка</a>`
        : '';
      const row = document.createElement('tr');
      row.dataset.siteId = String(site.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold font-mono">${escapeHtml(site.rms_address_display || site.rms_address || '—')}</div>
          <div class="small text-muted">${site.enabled ? 'Мониторинг включён' : 'Мониторинг отключён'}</div>
          <div class="small text-muted">Логин: ${escapeHtml(site.auth_login || '—')}</div>
          ${site.password_saved ? '' : '<div class="small text-warning">Пароль не сохранён</div>'}
        </td>
        <td>
          <div class="fw-semibold">${escapeHtml(site.server_name || '—')}</div>
          <div class="small text-muted">Последняя проверка: ${escapeHtml(formatDateTime(site.license_last_checked_at || site.updated_at))}</div>
        </td>
        <td>
          <div>${escapeHtml(formatDateTime(site.license_expires_at))}</div>
          <div class="small text-muted">Осталось: ${escapeHtml(formatDaysLeft(site.license_days_left))}</div>
        </td>
        <td class="license-cell status-${escapeHtml(licenseLevel || 'error')}">
          <div>${licenseBadge(licenseLevel)}</div>
          <div class="small text-muted mt-1">${escapeHtml(site.license_error_message || '')}</div>
        </td>
        <td class="rms-cell status-${escapeHtml(rmsLevel || 'unknown')}">
          <div>${rmsBadge(rmsLevel)}</div>
          <div class="small text-muted mt-1">${escapeHtml(site.rms_status_message || '—')}</div>
          <div class="small text-muted">${escapeHtml(formatDateTime(site.rms_last_checked_at))}</div>
          ${site.traceroute_summary ? `<div class="small mt-1">${escapeHtml(site.traceroute_summary)}</div>` : ''}
        </td>
        <td>
          <div>${escapeHtml(site.server_type || '—')}</div>
          <div class="small text-muted">${escapeHtml(site.server_version || '')}</div>
        </td>
        <td class="text-end">
          <div class="d-flex flex-wrap justify-content-end gap-2">
            ${tracerouteButton}
            <button class="btn btn-outline-primary btn-sm" type="button" data-action="edit">Изменить</button>
            <button class="btn btn-outline-danger btn-sm" type="button" data-action="delete">Удалить</button>
          </div>
        </td>
      `;
      tableBody.appendChild(row);
    });
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
    if (!headers.has('X-XSRF-TOKEN')) {
      headers.set('X-XSRF-TOKEN', token);
    }
    if (!headers.has('X-CSRF-TOKEN')) {
      headers.set('X-CSRF-TOKEN', token);
    }
    return {
      ...init,
      headers,
    };
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

  async function loadSites(showLoading) {
    if (showLoading) {
      renderEmptyState('Загрузка...', 'text-muted');
    }
    try {
      const data = await requestJson('/api/monitoring/rms/sites');
      sites = Array.isArray(data.items) ? data.items : [];
      refreshState = data.refresh_state || null;
      renderQueueState();
      renderSites();
    } catch (error) {
      renderQueueState();
      renderEmptyState(error.message || 'Не удалось загрузить RMS.', 'text-danger');
    }
  }

  function findSite(siteId) {
    const numericId = Number(siteId);
    return sites.find((item) => Number(item.id) === numericId) || null;
  }

  async function createSite(event) {
    event.preventDefault();
    const payload = {
      rmsAddress: rmsAddressInput?.value || '',
      authLogin: rmsLoginInput?.value || '',
      authPassword: rmsPasswordInput?.value || '',
      enabled: rmsEnabledInput?.checked ?? true,
    };
    try {
      await requestJson('/api/monitoring/rms/sites', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      createForm?.reset();
      if (rmsEnabledInput) rmsEnabledInput.checked = true;
      await loadSites(false);
      showMessage('RMS добавлен.', 'success');
    } catch (error) {
      showMessage(`Не удалось добавить RMS: ${error.message}`, 'error');
    }
  }

  function openEditModal(siteId) {
    const site = findSite(siteId);
    if (!site || !editModal) return;

    if (editRmsIdInput) editRmsIdInput.value = String(site.id);
    if (editRmsAddressInput) editRmsAddressInput.value = site.rms_address || '';
    if (editRmsLoginInput) editRmsLoginInput.value = site.auth_login || '';
    if (editRmsPasswordInput) editRmsPasswordInput.value = '';
    if (editRmsEnabledInput) editRmsEnabledInput.checked = Boolean(site.enabled);
    if (editRmsPasswordHint) {
      editRmsPasswordHint.textContent = site.password_saved
        ? 'Пароль уже сохранён. Оставьте поле пустым, чтобы его не менять.'
        : 'Для этого RMS пароль ещё не сохранён.';
    }
    editModal.show();
  }

  async function saveChanges(event) {
    event.preventDefault();
    const siteId = Number(editRmsIdInput?.value || 0);
    if (!Number.isFinite(siteId) || siteId <= 0) {
      showMessage('Не удалось определить RMS для обновления.', 'error');
      return;
    }
    const payload = {
      rmsAddress: editRmsAddressInput?.value || '',
      authLogin: editRmsLoginInput?.value || '',
      authPassword: editRmsPasswordInput?.value || '',
      enabled: editRmsEnabledInput?.checked ?? true,
    };
    try {
      await requestJson(`/api/monitoring/rms/sites/${siteId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      editModal?.hide();
      await loadSites(false);
      showMessage('Изменения сохранены.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить RMS: ${error.message}`, 'error');
    }
  }

  async function deleteSite(siteId) {
    if (!window.confirm('Удалить RMS из мониторинга?')) {
      return;
    }
    try {
      await requestJson(`/api/monitoring/rms/sites/${siteId}`, {
        method: 'DELETE',
      });
      await loadSites(false);
      showMessage('RMS удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить RMS: ${error.message}`, 'error');
    }
  }

  async function triggerRefresh(url, successMessage) {
    try {
      const data = await requestJson(url, {
        method: 'POST',
      });
      refreshState = data.refresh_state || refreshState;
      renderQueueState();
      await loadSites(false);
      showMessage(data.state === 'already_queued' ? 'Проверка уже стоит в очереди.' : successMessage, 'success');
    } catch (error) {
      showMessage(`Не удалось поставить обновление в очередь: ${error.message}`, 'error');
    }
  }

  function startPolling() {
    if (pollTimer) {
      window.clearInterval(pollTimer);
    }
    pollTimer = window.setInterval(() => {
      loadSites(false);
    }, POLL_INTERVAL_MS);
  }

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr[data-site-id]');
    if (!row) return;
    const siteId = Number(row.dataset.siteId);
    if (!Number.isFinite(siteId)) return;

    if (button.dataset.action === 'edit') {
      openEditModal(siteId);
      return;
    }
    if (button.dataset.action === 'delete') {
      deleteSite(siteId);
    }
  });

  createForm?.addEventListener('submit', createSite);
  editForm?.addEventListener('submit', saveChanges);
  refreshLicensesBtn?.addEventListener('click', () => {
    triggerRefresh('/api/monitoring/rms/refresh/licenses', 'Обновление лицензий поставлено в очередь.');
  });
  refreshNetworkBtn?.addEventListener('click', () => {
    triggerRefresh('/api/monitoring/rms/refresh/network', 'Обновление статусов RMS поставлено в очередь.');
  });

  loadSites(true);
  startPolling();
})();

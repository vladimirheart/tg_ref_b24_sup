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
  const rmsLicenseMonitoringInput = document.getElementById('rmsLicenseMonitoringInput');
  const rmsNetworkMonitoringInput = document.getElementById('rmsNetworkMonitoringInput');
  const bulkEnableLicenseMonitoringBtn = document.getElementById('bulkEnableLicenseMonitoringBtn');
  const bulkDisableLicenseMonitoringBtn = document.getElementById('bulkDisableLicenseMonitoringBtn');
  const bulkEnableNetworkMonitoringBtn = document.getElementById('bulkEnableNetworkMonitoringBtn');
  const bulkDisableNetworkMonitoringBtn = document.getElementById('bulkDisableNetworkMonitoringBtn');

  const editModalEl = document.getElementById('rmsEditModal');
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;
  const editForm = document.getElementById('rmsEditForm');
  const editRmsIdInput = document.getElementById('editRmsId');
  const editRmsAddressInput = document.getElementById('editRmsAddressInput');
  const editRmsLoginInput = document.getElementById('editRmsLoginInput');
  const editRmsPasswordInput = document.getElementById('editRmsPasswordInput');
  const editRmsEnabledInput = document.getElementById('editRmsEnabledInput');
  const editRmsLicenseMonitoringInput = document.getElementById('editRmsLicenseMonitoringInput');
  const editRmsNetworkMonitoringInput = document.getElementById('editRmsNetworkMonitoringInput');
  const editRmsPasswordHint = document.getElementById('editRmsPasswordHint');
  const licenseDetailsModalEl = document.getElementById('rmsLicenseDetailsModal');
  const licenseDetailsModal = licenseDetailsModalEl && window.bootstrap ? new bootstrap.Modal(licenseDetailsModalEl) : null;
  const licenseDetailsMeta = document.getElementById('rmsLicenseDetailsMeta');
  const licenseDetailsBody = document.getElementById('rmsLicenseDetailsBody');
  const diagnosticsModalEl = document.getElementById('rmsDiagnosticsModal');
  const diagnosticsModal = diagnosticsModalEl && window.bootstrap ? new bootstrap.Modal(diagnosticsModalEl) : null;
  const diagnosticsMeta = document.getElementById('rmsDiagnosticsMeta');
  const diagnosticsSummary = document.getElementById('rmsDiagnosticsSummary');
  const diagnosticsPingOutput = document.getElementById('rmsDiagnosticsPingOutput');
  const diagnosticsTracerouteOutput = document.getElementById('rmsDiagnosticsTracerouteOutput');
  const diagnosticsLicenseOutput = document.getElementById('rmsDiagnosticsLicenseOutput');

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

  function featureBadge(enabled, title) {
    return enabled
      ? `<span class="badge rounded-pill text-bg-success-subtle border border-success-subtle text-success">${escapeHtml(title)}: вкл</span>`
      : `<span class="badge rounded-pill text-bg-secondary">${escapeHtml(title)}: выкл</span>`;
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
      const recordEnabled = Boolean(site.enabled);
      const licenseFeatureEnabled = Boolean(site.license_monitoring_enabled);
      const networkFeatureEnabled = Boolean(site.network_monitoring_enabled);
      const row = document.createElement('tr');
      row.dataset.siteId = String(site.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold font-mono">${escapeHtml(site.rms_address_display || site.rms_address || '—')}</div>
          <div class="d-flex flex-wrap gap-1 mt-1">
            ${featureBadge(recordEnabled, 'Запись')}
            ${featureBadge(licenseFeatureEnabled, 'Лицензии')}
            ${featureBadge(networkFeatureEnabled, 'Доступность')}
          </div>
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
            <div class="btn-group">
              <button class="btn btn-outline-primary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                Действия
              </button>
              <ul class="dropdown-menu dropdown-menu-end">
                <li><button class="dropdown-item" type="button" data-action="refresh-license" ${recordEnabled && licenseFeatureEnabled ? '' : 'disabled'}>Обновить лицензию</button></li>
                <li><button class="dropdown-item" type="button" data-action="view-licenses" ${site.has_license_details ? '' : 'disabled'}>Посмотреть лицензии</button></li>
                <li><button class="dropdown-item" type="button" data-action="refresh-network" ${recordEnabled && networkFeatureEnabled ? '' : 'disabled'}>Проверить доступность</button></li>
                <li><button class="dropdown-item" type="button" data-action="diagnostics">Диагностика</button></li>
                <li><hr class="dropdown-divider"></li>
                <li><button class="dropdown-item" type="button" data-action="edit">Изменить</button></li>
                <li><button class="dropdown-item text-danger" type="button" data-action="delete">Удалить</button></li>
              </ul>
            </div>
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
      licenseMonitoringEnabled: rmsLicenseMonitoringInput?.checked ?? true,
      networkMonitoringEnabled: rmsNetworkMonitoringInput?.checked ?? true,
    };
    try {
      await requestJson('/api/monitoring/rms/sites', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      createForm?.reset();
      if (rmsEnabledInput) rmsEnabledInput.checked = true;
      if (rmsLicenseMonitoringInput) rmsLicenseMonitoringInput.checked = true;
      if (rmsNetworkMonitoringInput) rmsNetworkMonitoringInput.checked = true;
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
    if (editRmsLicenseMonitoringInput) editRmsLicenseMonitoringInput.checked = Boolean(site.license_monitoring_enabled);
    if (editRmsNetworkMonitoringInput) editRmsNetworkMonitoringInput.checked = Boolean(site.network_monitoring_enabled);
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
      licenseMonitoringEnabled: editRmsLicenseMonitoringInput?.checked ?? true,
      networkMonitoringEnabled: editRmsNetworkMonitoringInput?.checked ?? true,
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

  async function triggerSiteRefresh(siteId, scope) {
    const endpoint = scope === 'license'
      ? `/api/monitoring/rms/sites/${siteId}/refresh/licenses`
      : `/api/monitoring/rms/sites/${siteId}/refresh/network`;
    const successMessage = scope === 'license'
      ? 'Проверка лицензии поставлена в очередь.'
      : 'Проверка доступности поставлена в очередь.';
    try {
      const data = await requestJson(endpoint, {
        method: 'POST',
      });
      refreshState = data.refresh_state || refreshState;
      renderQueueState();
      await loadSites(false);
      showMessage(data.state === 'already_queued' ? 'Проверка уже стоит в очереди.' : successMessage, 'success');
    } catch (error) {
      showMessage(`Не удалось поставить проверку в очередь: ${error.message}`, 'error');
    }
  }

  function renderLicenseDetailsModal(data) {
    if (!licenseDetailsBody || !licenseDetailsMeta) return;
    const title = data.server_name || data.rms_address || 'RMS';
    licenseDetailsMeta.textContent = `${title} • последняя проверка: ${formatDateTime(data.last_checked_at)}`;
    const items = Array.isArray(data.items) ? data.items : [];
    if (!items.length) {
      licenseDetailsBody.innerHTML = '<div class="text-muted">По этой RMS пока нет сохранённого состава лицензий.</div>';
      return;
    }
    const cards = items.map((item, index) => {
      const titleText = item.title || item.name || item.license_name || item.type || `Лицензия #${index + 1}`;
      const important = [];
      if (item.expiration) important.push(`<span class="badge text-bg-light border">Истекает: ${escapeHtml(item.expiration)}</span>`);
      if (item.quantity) important.push(`<span class="badge text-bg-light border">Кол-во: ${escapeHtml(item.quantity)}</span>`);
      if (item.state) important.push(`<span class="badge text-bg-light border">Статус: ${escapeHtml(item.state)}</span>`);
      const details = Object.entries(item)
        .filter(([key]) => !['title'].includes(key))
        .map(([key, value]) => `<tr><th class="text-muted small" style="width: 35%;">${escapeHtml(key)}</th><td class="small">${escapeHtml(value)}</td></tr>`)
        .join('');
      return `
        <div class="card shadow-sm mb-3">
          <div class="card-body">
            <div class="d-flex flex-column gap-2">
              <div class="fw-semibold">${escapeHtml(titleText)}</div>
              <div class="d-flex flex-wrap gap-2">${important.join('')}</div>
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0">
                  <tbody>${details}</tbody>
                </table>
              </div>
            </div>
          </div>
        </div>
      `;
    });
    licenseDetailsBody.innerHTML = cards.join('');
  }

  async function openLicenseDetails(siteId) {
    if (!licenseDetailsModal || !licenseDetailsBody || !licenseDetailsMeta) return;
    const site = findSite(siteId);
    licenseDetailsMeta.textContent = site?.server_name || site?.rms_address_display || 'Загрузка...';
    licenseDetailsBody.innerHTML = '<div class="text-muted">Загрузка состава лицензий...</div>';
    licenseDetailsModal.show();
    try {
      const data = await requestJson(`/api/monitoring/rms/sites/${siteId}/licenses`);
      renderLicenseDetailsModal(data);
    } catch (error) {
      licenseDetailsBody.innerHTML = `<div class="text-danger">${escapeHtml(error.message || 'Не удалось загрузить состав лицензий.')}</div>`;
    }
  }

  function renderDiagnosticsModal(data) {
    if (!diagnosticsMeta || !diagnosticsSummary || !diagnosticsPingOutput || !diagnosticsTracerouteOutput || !diagnosticsLicenseOutput) return;
    const title = data.server_name || data.rms_address || 'RMS';
    diagnosticsMeta.textContent = `${title} • лицензии: ${formatDateTime(data.license_last_checked_at)} • сеть: ${formatDateTime(data.rms_last_checked_at)}`;
    diagnosticsSummary.innerHTML = `
      <span class="badge text-bg-light border">license_status: ${escapeHtml(data.license_status || '—')}</span>
      <span class="badge text-bg-light border">rms_status: ${escapeHtml(data.rms_status || '—')}</span>
      <span class="badge text-bg-light border">license_error: ${escapeHtml(data.license_error_message || '—')}</span>
      <span class="badge text-bg-light border">network_msg: ${escapeHtml(data.rms_status_message || '—')}</span>
      ${data.traceroute_summary ? `<span class="badge text-bg-light border">traceroute: ${escapeHtml(data.traceroute_summary)}</span>` : ''}
    `;
    diagnosticsPingOutput.textContent = String(data.ping_output || 'Нет сохранённого ping output.');
    diagnosticsTracerouteOutput.textContent = String(data.traceroute_report || 'Нет сохранённой трассировки.');
    diagnosticsLicenseOutput.textContent = String(data.license_debug_excerpt || data.license_error_message || 'Нет лицензионной диагностики.');
  }

  async function openDiagnostics(siteId) {
    if (!diagnosticsModal || !diagnosticsMeta || !diagnosticsSummary || !diagnosticsPingOutput || !diagnosticsTracerouteOutput || !diagnosticsLicenseOutput) return;
    const site = findSite(siteId);
    diagnosticsMeta.textContent = site?.server_name || site?.rms_address_display || 'Загрузка...';
    diagnosticsSummary.innerHTML = '<span class="text-muted">Загрузка...</span>';
    diagnosticsPingOutput.textContent = 'Загрузка...';
    diagnosticsTracerouteOutput.textContent = 'Загрузка...';
    diagnosticsLicenseOutput.textContent = 'Загрузка...';
    diagnosticsModal.show();
    try {
      const data = await requestJson(`/api/monitoring/rms/sites/${siteId}/diagnostics`);
      renderDiagnosticsModal(data);
    } catch (error) {
      diagnosticsSummary.innerHTML = `<span class="text-danger">${escapeHtml(error.message || 'Не удалось загрузить диагностику.')}</span>`;
      diagnosticsPingOutput.textContent = 'Нет данных.';
      diagnosticsTracerouteOutput.textContent = 'Нет данных.';
      diagnosticsLicenseOutput.textContent = 'Нет данных.';
    }
  }

  async function toggleBulkFeature(scope, enabled) {
    const endpoint = scope === 'license'
      ? '/api/monitoring/rms/bulk/license-monitoring'
      : '/api/monitoring/rms/bulk/network-monitoring';
    const scopeTitle = scope === 'license' ? 'лицензий' : 'доступности';
    try {
      await requestJson(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled }),
      });
      await loadSites(false);
      showMessage(
        enabled ? `Мониторинг ${scopeTitle} массово включён.` : `Мониторинг ${scopeTitle} массово отключён.`,
        'success'
      );
    } catch (error) {
      showMessage(`Не удалось изменить массовый режим: ${error.message}`, 'error');
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
    if (button.dataset.action === 'refresh-license') {
      triggerSiteRefresh(siteId, 'license');
      return;
    }
    if (button.dataset.action === 'view-licenses') {
      openLicenseDetails(siteId);
      return;
    }
    if (button.dataset.action === 'refresh-network') {
      triggerSiteRefresh(siteId, 'network');
      return;
    }
    if (button.dataset.action === 'diagnostics') {
      openDiagnostics(siteId);
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
  bulkEnableLicenseMonitoringBtn?.addEventListener('click', () => {
    toggleBulkFeature('license', true);
  });
  bulkDisableLicenseMonitoringBtn?.addEventListener('click', () => {
    toggleBulkFeature('license', false);
  });
  bulkEnableNetworkMonitoringBtn?.addEventListener('click', () => {
    toggleBulkFeature('network', true);
  });
  bulkDisableNetworkMonitoringBtn?.addEventListener('click', () => {
    toggleBulkFeature('network', false);
  });

  loadSites(true);
  startPolling();
})();

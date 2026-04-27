(function () {
  const POLL_INTERVAL_MS = 15000;

  const tableBody = document.getElementById('rmsMonitoringTableBody');
  const tableHead = tableBody?.closest('table')?.querySelector('thead') || null;
  const queueStateEl = document.getElementById('rmsQueueState');
  const availabilityPercentEl = document.getElementById('rmsAvailabilityPercent');
  const availabilityCaptionEl = document.getElementById('rmsAvailabilityCaption');
  const availabilityBarEl = document.getElementById('rmsAvailabilityOverviewBar');
  const availabilityMetaEl = document.getElementById('rmsAvailabilityOverviewMeta');
  const availabilityFiltersEl = document.getElementById('rmsAvailabilityFilters');

  const createModalEl = document.getElementById('rmsCreateModal');
  const createModal = createModalEl && window.bootstrap ? new bootstrap.Modal(createModalEl) : null;
  const openCreateModalBtn = document.getElementById('openRmsCreateModalBtn');
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
  const diagnosticsTimeline = document.getElementById('rmsDiagnosticsTimeline');
  const diagnosticsPingOutput = document.getElementById('rmsDiagnosticsPingOutput');
  const diagnosticsTracerouteOutput = document.getElementById('rmsDiagnosticsTracerouteOutput');
  const diagnosticsLicenseOutput = document.getElementById('rmsDiagnosticsLicenseOutput');

  let sites = [];
  let refreshState = null;
  let availabilityOverview = null;
  let pollTimer = null;
  let availabilityFilter = 'all';
  const sortState = {
    key: 'address',
    direction: 'asc',
  };

  const licenseStatusRank = {
    ok: 1,
    warning: 2,
    critical: 3,
    expired: 4,
    error: 5,
    disabled: 6,
  };

  const rmsStatusRank = {
    up: 1,
    down: 2,
    unknown: 3,
    disabled: 4,
  };

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function normalizeDiagnosticText(value, fallback) {
    const source = value === null || value === undefined || value === '' ? fallback : String(value);
    if (!source) return '';
    const normalized = source.replace(/\r\n/g, '\n').replace(/\r/g, '\n').trim();
    if (!normalized) return fallback || '';
    if (!/[Рр][0-9A-Za-z]/.test(normalized)) {
      return normalized;
    }
    try {
      const repaired = new TextDecoder('utf-8').decode(
        Uint8Array.from(normalized, (char) => char.charCodeAt(0) & 0xff),
      ).trim();
      if (repaired && !/[Рр][0-9A-Za-z]/.test(repaired)) {
        return repaired;
      }
    } catch (error) {
      void error;
    }
    return normalized;
  }

  function diagnosticsSummaryItem(label, value) {
    return `
      <div class="diagnostics-summary-item">
        <span class="diagnostics-summary-label">${escapeHtml(label)}</span>
        <div>${escapeHtml(normalizeDiagnosticText(value, '—'))}</div>
      </div>
    `;
  }

  function rmsTimelineKindLabel(value) {
    if (value === 'license') return 'Лицензия';
    if (value === 'network') return 'Доступность';
    return value || 'Событие';
  }

  function renderTimeline(entries) {
    const items = Array.isArray(entries) ? entries : [];
    if (!items.length) {
      return '<div class="text-muted">История проверок пока не накоплена.</div>';
    }
    return items.map((entry) => {
      const checkKind = entry.check_kind || entry.checkKind || '';
      const httpStatus = entry.http_status ?? entry.httpStatus;
      const durationMs = entry.duration_ms ?? entry.durationMs;
      const createdAt = entry.created_at || entry.createdAt;
      const status = normalizeStatus(entry.status || '');
      const statusBadgeHtml = checkKind === 'network' ? rmsBadge(status) : licenseBadge(status);
      const details = [];
      if (entry.summary) {
        details.push(`<div class="small">${escapeHtml(normalizeDiagnosticText(entry.summary, ''))}</div>`);
      }
      if (httpStatus !== null && httpStatus !== undefined && httpStatus !== '') {
        details.push(`<div class="small text-muted">HTTP: ${escapeHtml(httpStatus)}</div>`);
      }
      if (durationMs !== null && durationMs !== undefined && durationMs !== '') {
        details.push(`<div class="small text-muted">Длительность: ${escapeHtml(durationMs)} мс</div>`);
      }
      return `
        <div class="diagnostics-summary-item">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
            <div>
              <div class="small text-muted">${escapeHtml(rmsTimelineKindLabel(checkKind))}</div>
              <div class="fw-semibold">${escapeHtml(formatDateTime(createdAt))}</div>
            </div>
            <div>${statusBadgeHtml}</div>
          </div>
          <div class="mt-2">${details.join('')}</div>
        </div>
      `;
    }).join('');
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

  function formatDateOnly(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleDateString('ru-RU');
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

  function parseTimestamp(value) {
    if (!value) return null;
    const timestamp = Date.parse(value);
    return Number.isFinite(timestamp) ? timestamp : null;
  }

  function compareNullable(left, right, comparator) {
    const leftMissing = left === null || left === undefined || left === '';
    const rightMissing = right === null || right === undefined || right === '';
    if (leftMissing && rightMissing) return 0;
    if (leftMissing) return 1;
    if (rightMissing) return -1;
    return comparator(left, right);
  }

  function compareStrings(left, right) {
    return String(left).localeCompare(String(right), 'ru', { sensitivity: 'base', numeric: true });
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

  function featureIcon(label, enabled, title) {
    const stateClass = enabled ? 'is-enabled' : 'is-disabled';
    return `<span class="rms-feature-icon ${stateClass}" title="${escapeHtml(title)}">${escapeHtml(label)}</span>`;
  }

  function featureBadge(enabled, title) {
    return enabled
      ? `<span class="badge rounded-pill text-bg-success-subtle border border-success-subtle text-success">${escapeHtml(title)}: вкл</span>`
      : `<span class="badge rounded-pill text-bg-secondary">${escapeHtml(title)}: выкл</span>`;
  }

  function refreshStateLabel(value) {
    if (value === 'running') return 'обновляется';
    if (value === 'queued') return 'в очереди';
    if (value === 'done') return 'обновлено';
    return 'ожидает';
  }

  function refreshStateChip(value, prefix) {
    const state = normalizeStatus(value);
    if (!state || state === 'idle') {
      return `<span class="refresh-state-chip">${escapeHtml(prefix)}: ожидание</span>`;
    }
    return `<span class="refresh-state-chip state-${escapeHtml(state)}">${escapeHtml(prefix)}: ${escapeHtml(refreshStateLabel(state))}</span>`;
  }

  function compareSites(left, right) {
    const key = sortState.key;
    const direction = sortState.direction === 'desc' ? -1 : 1;
    let result = 0;

    if (key === 'address') {
      result = compareNullable(left?.rms_address_display || left?.rms_address, right?.rms_address_display || right?.rms_address, compareStrings);
    } else if (key === 'name') {
      result = compareNullable(left?.server_name_display || left?.server_name, right?.server_name_display || right?.server_name, compareStrings);
    } else if (key === 'expires_at') {
      result = compareNullable(parseTimestamp(left?.license_expires_at), parseTimestamp(right?.license_expires_at), (a, b) => a - b);
    } else if (key === 'license_status') {
      result = (licenseStatusRank[normalizeStatus(left?.license_status_level || left?.license_status)] ?? 99)
        - (licenseStatusRank[normalizeStatus(right?.license_status_level || right?.license_status)] ?? 99);
    } else if (key === 'rms_status') {
      result = (rmsStatusRank[normalizeStatus(left?.rms_status_level || left?.rms_status)] ?? 99)
        - (rmsStatusRank[normalizeStatus(right?.rms_status_level || right?.rms_status)] ?? 99);
    } else if (key === 'server_type') {
      result = compareNullable(left?.server_type, right?.server_type, compareStrings);
    }

    if (result === 0) {
      result = Number(left?.id || 0) - Number(right?.id || 0);
    }
    return result * direction;
  }

  function getFilteredAndSortedSites() {
    return [...sites]
      .filter((site) => {
        if (availabilityFilter === 'all') return true;
        return normalizeStatus(site?.rms_status_level || site?.rms_status) === availabilityFilter;
      })
      .sort(compareSites);
  }

  function updateSortIndicators() {
    if (!tableHead) return;
    tableHead.querySelectorAll('.sortable-button[data-sort-key]').forEach((button) => {
      const indicator = button.querySelector('.sort-indicator');
      const isActive = button.dataset.sortKey === sortState.key;
      button.classList.toggle('active', isActive);
      if (!indicator) return;
      indicator.textContent = isActive ? (sortState.direction === 'asc' ? '↑' : '↓') : '↕';
    });
  }

  function updateAvailabilityFilterButtons() {
    if (!availabilityFiltersEl) return;
    availabilityFiltersEl.querySelectorAll('[data-filter-value]').forEach((button) => {
      button.classList.toggle('active', button.getAttribute('data-filter-value') === availabilityFilter);
    });
  }

  function queueBadge(running, queued) {
    if (running) return '<span class="badge text-bg-primary">В работе</span>';
    if (queued) return '<span class="badge text-bg-warning">В очереди</span>';
    return '<span class="badge text-bg-secondary">Свободно</span>';
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

  function findSite(siteId) {
    const numericId = Number(siteId);
    return sites.find((item) => Number(item.id) === numericId) || null;
  }

  function findSiteName(siteId) {
    const site = findSite(siteId);
    if (!site) return `#${siteId}`;
    return site.server_name_display || site.server_name || site.rms_address_display || site.rms_address || `#${siteId}`;
  }

  function renderQueueLine(label, queue) {
    const running = Boolean(queue?.running);
    const queued = Boolean(queue?.queued);
    const total = Number(queue?.total_count || 0);
    const completed = Number(queue?.completed_count || 0);
    const currentMonitorId = queue?.current_monitor_id;
    const progressText = total > 0 ? `${completed}/${total}` : '—';
    const currentText = currentMonitorId ? `сейчас: ${findSiteName(currentMonitorId)}` : 'сейчас: —';
    return `
      <div>
        <strong>${escapeHtml(label)}:</strong> ${queueBadge(running, queued)}
        <span class="ms-2">прогресс: ${escapeHtml(progressText)}</span>
        <span class="ms-2">${escapeHtml(currentText)}</span>
        <span class="ms-2">последний запуск: ${escapeHtml(formatDateTime(queue?.last_requested_at))}</span>
        <span class="ms-2">последнее завершение: ${escapeHtml(formatDateTime(queue?.last_completed_at))}</span>
      </div>
    `;
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
        ${renderQueueLine('Лицензии', licenses)}
        ${renderQueueLine('Статусы RMS', network)}
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

  function renderAvailabilityOverview() {
    if (!availabilityPercentEl || !availabilityCaptionEl || !availabilityBarEl || !availabilityMetaEl) return;
    if (!availabilityOverview) {
      availabilityPercentEl.textContent = '—';
      availabilityCaptionEl.textContent = 'нет данных';
      availabilityBarEl.innerHTML = `
        <div class="progress" role="progressbar" style="width: 100%;">
          <div class="progress-bar bg-secondary-subtle text-secondary">Нет данных</div>
        </div>
      `;
      availabilityMetaEl.innerHTML = '';
      updateAvailabilityFilterButtons();
      return;
    }

    const total = Number(availabilityOverview.total || 0);
    const up = Number(availabilityOverview.up || 0);
    const down = Number(availabilityOverview.down || 0);
    const unknown = Number(availabilityOverview.unknown || 0);
    const disabled = Number(availabilityOverview.disabled || 0);
    const percent = Number(availabilityOverview.availability_percent || 0);
    const safeTotal = total > 0 ? total : 1;
    const segmentWidth = (count) => `${Math.max(0, (Number(count || 0) * 100) / safeTotal)}%`;

    availabilityPercentEl.textContent = `${percent.toFixed(1)}%`;
    availabilityCaptionEl.textContent = `активных узлов: ${Math.max(0, total - disabled)} из ${total}`;
    availabilityBarEl.innerHTML = `
      <div class="progress" role="progressbar" style="width: ${segmentWidth(up)};">
        <div class="progress-bar bg-success" title="Доступны: ${up}"></div>
      </div>
      <div class="progress" role="progressbar" style="width: ${segmentWidth(down)};">
        <div class="progress-bar bg-danger" title="Недоступны: ${down}"></div>
      </div>
      <div class="progress" role="progressbar" style="width: ${segmentWidth(unknown)};">
        <div class="progress-bar bg-secondary" title="Неизвестно: ${unknown}"></div>
      </div>
      <div class="progress" role="progressbar" style="width: ${segmentWidth(disabled)};">
        <div class="progress-bar bg-dark-subtle" title="Отключены: ${disabled}"></div>
      </div>
    `;
    availabilityMetaEl.innerHTML = `
      <span class="rms-overview-chip text-success">Доступны: ${escapeHtml(up)}</span>
      <span class="rms-overview-chip text-danger">Недоступны: ${escapeHtml(down)}</span>
      <span class="rms-overview-chip text-secondary">Неизвестно: ${escapeHtml(unknown)}</span>
      <span class="rms-overview-chip text-body-secondary">Отключены: ${escapeHtml(disabled)}</span>
    `;
    updateAvailabilityFilterButtons();
  }

  function renderEmptyState(message, className) {
    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="7" class="text-center ${className} py-4">${escapeHtml(message)}</td></tr>`;
  }

  function renderSites() {
    if (!tableBody) return;
    updateSortIndicators();
    const visibleSites = getFilteredAndSortedSites();
    if (!visibleSites.length) {
      if (sites.length && availabilityFilter !== 'all') {
        renderEmptyState('Нет RMS, подходящих под выбранный статус доступности.', 'text-muted');
        return;
      }
      renderEmptyState('Пока нет RMS для мониторинга.', 'text-muted');
      return;
    }

    tableBody.innerHTML = '';
    visibleSites.forEach((site) => {
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
      if (String(site.server_type || '').trim().toUpperCase() === 'IIKO_CHAIN') {
        row.classList.add('rms-chain-row');
      }
      row.innerHTML = `
        <td>
          <div class="fw-semibold font-mono">${escapeHtml(site.rms_address_display || site.rms_address || '—')}</div>
          <div class="small ${site.password_saved ? 'text-success' : 'text-warning'}">${site.password_saved ? 'Пароль сохранён и зашифрован' : 'Пароль не сохранён'}</div>
        </td>
        <td>
          <div class="fw-semibold">${escapeHtml(site.server_name_display || site.server_name || '—')}</div>
          <div class="small text-muted">Последняя проверка: ${escapeHtml(formatDateTime(site.license_last_checked_at || site.updated_at))}</div>
        </td>
        <td>
          <div>${escapeHtml(formatDateOnly(site.license_expires_at))}</div>
          <div class="small text-muted">Осталось: ${escapeHtml(formatDaysLeft(site.license_days_left))}</div>
          <div class="small text-muted">Лицензий 100: ${escapeHtml(site.target_license_count ?? '—')}</div>
        </td>
        <td class="license-cell status-${escapeHtml(licenseLevel || 'error')}">
          <div>${licenseBadge(licenseLevel)}</div>
          <div class="small text-muted mt-1">${escapeHtml(site.license_error_message || '')}</div>
          <div class="small mt-2">${refreshStateChip(site.license_refresh_state, 'Лицензия')}</div>
        </td>
        <td class="rms-cell status-${escapeHtml(rmsLevel || 'unknown')}">
          <div>${rmsBadge(rmsLevel)}</div>
          <div class="small text-muted mt-1">${escapeHtml(site.rms_status_message || '—')}</div>
          <div class="small text-muted">${escapeHtml(formatDateTime(site.rms_last_checked_at))}</div>
          <div class="small mt-2">${refreshStateChip(site.network_refresh_state, 'RMS')}</div>
          <div class="rms-feature-icons mt-2">
            ${featureIcon('S', recordEnabled, `Запись мониторинга: ${recordEnabled ? 'включена' : 'выключена'}`)}
            ${featureIcon('L', licenseFeatureEnabled, `Мониторинг лицензии: ${licenseFeatureEnabled ? 'включён' : 'выключен'}`)}
            ${featureIcon('N', networkFeatureEnabled, `Мониторинг доступности: ${networkFeatureEnabled ? 'включён' : 'выключен'}`)}
          </div>
          ${site.traceroute_summary ? `<div class="small mt-2">${escapeHtml(site.traceroute_summary)}</div>` : ''}
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

  async function loadSites(showLoading) {
    if (showLoading) {
      renderEmptyState('Загрузка...', 'text-muted');
    }
    try {
      const data = await requestJson('/api/monitoring/rms/sites');
      sites = Array.isArray(data.items) ? data.items : [];
      refreshState = data.refresh_state || null;
      availabilityOverview = data.availability_overview || null;
      renderAvailabilityOverview();
      renderQueueState();
      renderSites();
    } catch (error) {
      renderAvailabilityOverview();
      renderQueueState();
      renderEmptyState(error.message || 'Не удалось загрузить RMS.', 'text-danger');
    }
  }

  function resetCreateForm() {
    createForm?.reset();
    if (rmsEnabledInput) rmsEnabledInput.checked = true;
    if (rmsLicenseMonitoringInput) rmsLicenseMonitoringInput.checked = true;
    if (rmsNetworkMonitoringInput) rmsNetworkMonitoringInput.checked = true;
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
      resetCreateForm();
      createModal?.hide();
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
        ? 'Пароль уже сохранён и хранится в зашифрованном виде. Оставьте поле пустым, чтобы его не менять.'
        : 'Для этой RMS пароль ещё не сохранён.';
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
    if (!window.confirm('Удалить RMS из мониторинга?')) return;
    try {
      await requestJson(`/api/monitoring/rms/sites/${siteId}`, { method: 'DELETE' });
      await loadSites(false);
      showMessage('RMS удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить RMS: ${error.message}`, 'error');
    }
  }

  async function triggerRefresh(url, successMessage) {
    try {
      const data = await requestJson(url, { method: 'POST' });
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
      const data = await requestJson(endpoint, { method: 'POST' });
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
    const title = data.server_name_display || data.server_name || data.rms_address || 'RMS';
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
    licenseDetailsMeta.textContent = site?.server_name_display || site?.server_name || site?.rms_address_display || 'Загрузка...';
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
    if (!diagnosticsMeta || !diagnosticsSummary || !diagnosticsTimeline || !diagnosticsPingOutput || !diagnosticsTracerouteOutput || !diagnosticsLicenseOutput) return;
    const title = data.server_name_display || data.server_name || data.rms_address || 'RMS';
    diagnosticsMeta.textContent = `${title} • лицензии: ${formatDateTime(data.license_last_checked_at)} • сеть: ${formatDateTime(data.rms_last_checked_at)}`;
    diagnosticsSummary.innerHTML = `
      ${diagnosticsSummaryItem('Статус лицензии', data.license_status || '—')}
      ${diagnosticsSummaryItem('Статус RMS', data.rms_status || '—')}
      ${diagnosticsSummaryItem('Ошибка лицензии', data.license_error_message || '—')}
      ${diagnosticsSummaryItem('Сетевое сообщение', data.rms_status_message || '—')}
      ${data.traceroute_summary ? diagnosticsSummaryItem('Сводка трассировки', data.traceroute_summary) : ''}
    `;
    diagnosticsTimeline.innerHTML = renderTimeline(data.timeline);
    diagnosticsPingOutput.textContent = normalizeDiagnosticText(data.ping_output, 'Нет сохранённого ping output.');
    diagnosticsTracerouteOutput.textContent = normalizeDiagnosticText(data.traceroute_report, 'Нет сохранённой трассировки.');
    diagnosticsLicenseOutput.textContent = normalizeDiagnosticText(data.license_debug_excerpt || data.license_error_message, 'Нет лицензионной диагностики.');
  }

  async function openDiagnostics(siteId) {
    if (!diagnosticsModal || !diagnosticsMeta || !diagnosticsSummary || !diagnosticsTimeline || !diagnosticsPingOutput || !diagnosticsTracerouteOutput || !diagnosticsLicenseOutput) return;
    const site = findSite(siteId);
    diagnosticsMeta.textContent = site?.server_name_display || site?.server_name || site?.rms_address_display || 'Загрузка...';
    diagnosticsSummary.innerHTML = '<div class="text-muted">Загрузка...</div>';
    diagnosticsTimeline.innerHTML = '<div class="text-muted">Загрузка...</div>';
    diagnosticsPingOutput.textContent = 'Загрузка...';
    diagnosticsTracerouteOutput.textContent = 'Загрузка...';
    diagnosticsLicenseOutput.textContent = 'Загрузка...';
    diagnosticsModal.show();
    try {
      const data = await requestJson(`/api/monitoring/rms/sites/${siteId}/diagnostics`);
      renderDiagnosticsModal(data);
    } catch (error) {
      diagnosticsSummary.innerHTML = `<div class="text-danger">${escapeHtml(error.message || 'Не удалось загрузить диагностику.')}</div>`;
      diagnosticsTimeline.innerHTML = '<div class="text-muted">Нет данных.</div>';
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
        'success',
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

  openCreateModalBtn?.addEventListener('click', () => {
    resetCreateForm();
    createModal?.show();
  });

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

  tableHead?.addEventListener('click', (event) => {
    const button = event.target.closest('.sortable-button[data-sort-key]');
    if (!button) return;
    const selectedKey = String(button.dataset.sortKey || '').trim();
    if (!selectedKey) return;
    if (sortState.key === selectedKey) {
      sortState.direction = sortState.direction === 'asc' ? 'desc' : 'asc';
    } else {
      sortState.key = selectedKey;
      sortState.direction = 'asc';
    }
    renderSites();
  });

  availabilityFiltersEl?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-filter-value]');
    if (!button) return;
    availabilityFilter = String(button.getAttribute('data-filter-value') || 'all');
    updateAvailabilityFilterButtons();
    renderSites();
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

(function () {
  const tableBody = document.getElementById('certificateSitesTableBody');
  const tableHead = document.querySelector('#certificateMonitoringTable thead');
  const createForm = document.getElementById('certificateSiteCreateForm');
  const refreshAllBtn = document.getElementById('refreshAllCertificatesBtn');
  const openCreateModalBtn = document.getElementById('openCertificateCreateModalBtn');
  const siteNameInput = document.getElementById('siteNameInput');
  const siteEndpointInput = document.getElementById('siteEndpointInput');
  const siteEnabledInput = document.getElementById('siteEnabledInput');

  const createModalEl = document.getElementById('certificateCreateModal');
  const createModal = createModalEl && window.bootstrap ? new bootstrap.Modal(createModalEl) : null;

  const editModalEl = document.getElementById('certificateEditModal');
  const editForm = document.getElementById('certificateEditForm');
  const editSiteIdInput = document.getElementById('editSiteId');
  const editSiteNameInput = document.getElementById('editSiteNameInput');
  const editSiteEndpointInput = document.getElementById('editSiteEndpointInput');
  const editSiteEnabledInput = document.getElementById('editSiteEnabledInput');
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;

  const availabilityPercentEl = document.getElementById('certificateAvailabilityPercent');
  const availabilityCaptionEl = document.getElementById('certificateAvailabilityCaption');
  const availabilityBarEl = document.getElementById('certificateAvailabilityOverviewBar');
  const availabilityMetaEl = document.getElementById('certificateAvailabilityOverviewMeta');
  const availabilityFiltersEl = document.getElementById('certificateAvailabilityFilters');

  let sites = [];
  let availabilityOverview = null;
  let availabilityFilter = 'all';
  const sortState = {
    key: 'site',
    direction: 'asc',
  };

  const statusRank = {
    ok: 1,
    warning: 2,
    critical: 3,
    expired: 4,
    error: 5,
    disabled: 6,
  };

  const availabilityRank = {
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
    if (numeric < 0) return `Просрочен на ${Math.abs(numeric)} дн`;
    return `${numeric} дн`;
  }

  function normalizeStatus(item) {
    return String(item?.status_level || item?.monitor_status || '').toLowerCase();
  }

  function normalizeAvailability(item) {
    return String(item?.availability || '').toLowerCase();
  }

  function resolveSiteDisplayName(item) {
    return String(item?.site_display_name || item?.site_name || item?.host_display || item?.host || 'Сайт');
  }

  function resolveHostDisplay(item) {
    return String(item?.host_display || item?.host || '').trim();
  }

  function resolveEndpointDisplay(item) {
    return String(item?.endpoint_display || item?.endpoint_url || '').trim();
  }

  function sameText(left, right) {
    return String(left || '').trim().toLowerCase() === String(right || '').trim().toLowerCase();
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

  function compareSites(left, right) {
    const key = sortState.key;
    const direction = sortState.direction === 'desc' ? -1 : 1;
    let result = 0;

    if (key === 'site') {
      result = compareNullable(resolveSiteDisplayName(left), resolveSiteDisplayName(right), compareStrings);
    } else if (key === 'endpoint') {
      result = compareNullable(resolveEndpointDisplay(left), resolveEndpointDisplay(right), compareStrings);
    } else if (key === 'status') {
      result = (statusRank[normalizeStatus(left)] ?? 99) - (statusRank[normalizeStatus(right)] ?? 99);
    } else if (key === 'availability') {
      result = (availabilityRank[normalizeAvailability(left)] ?? 99) - (availabilityRank[normalizeAvailability(right)] ?? 99);
    } else if (key === 'expires_at') {
      result = compareNullable(parseTimestamp(left?.expires_at), parseTimestamp(right?.expires_at), (a, b) => a - b);
    } else if (key === 'days_left') {
      result = compareNullable(left?.days_left, right?.days_left, (a, b) => Number(a) - Number(b));
    } else if (key === 'last_checked_at') {
      result = compareNullable(parseTimestamp(left?.last_checked_at), parseTimestamp(right?.last_checked_at), (a, b) => a - b);
    } else if (key === 'error') {
      result = compareNullable(left?.error_message, right?.error_message, compareStrings);
    }

    if (result === 0) {
      result = Number(left?.id || 0) - Number(right?.id || 0);
    }
    return result * direction;
  }

  function getVisibleSites() {
    return [...sites]
      .filter((site) => availabilityFilter === 'all' || normalizeAvailability(site) === availabilityFilter)
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

  function resolveStatusCellClass(item) {
    const level = normalizeStatus(item);
    if (['ok', 'warning', 'critical', 'expired', 'disabled'].includes(level)) {
      return `status-cell status-${level}`;
    }
    return 'status-cell status-error';
  }

  function resolveAvailabilityCellClass(item) {
    const availability = normalizeAvailability(item);
    if (['up', 'down', 'disabled', 'unknown'].includes(availability)) {
      return `availability-cell availability-${availability}`;
    }
    return 'availability-cell availability-unknown';
  }

  function resolveStatusBadge(item) {
    const level = normalizeStatus(item);
    if (level === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (level === 'warning') return '<span class="badge text-bg-warning">Warning</span>';
    if (level === 'critical') return '<span class="badge text-bg-danger">Critical</span>';
    if (level === 'expired') return '<span class="badge bg-danger-subtle text-danger border border-danger-subtle">Expired</span>';
    if (level === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Error</span>';
  }

  function resolveAvailabilityBadge(item) {
    const availability = normalizeAvailability(item);
    if (availability === 'up') return '<span class="badge text-bg-success">UP</span>';
    if (availability === 'down') return '<span class="badge text-bg-danger">DOWN</span>';
    if (availability === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Unknown</span>';
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
      <span class="monitor-overview-chip text-success">Доступны: ${escapeHtml(up)}</span>
      <span class="monitor-overview-chip text-danger">Недоступны: ${escapeHtml(down)}</span>
      <span class="monitor-overview-chip text-secondary">Неизвестно: ${escapeHtml(unknown)}</span>
      <span class="monitor-overview-chip text-body-secondary">Отключены: ${escapeHtml(disabled)}</span>
    `;
    updateAvailabilityFilterButtons();
  }

  function renderSitesTable() {
    if (!tableBody) return;
    updateSortIndicators();
    const visibleSites = getVisibleSites();
    if (!visibleSites.length) {
      const message = sites.length && availabilityFilter !== 'all'
        ? 'Нет сайтов, подходящих под выбранный статус доступности.'
        : 'Пока нет сайтов для мониторинга.';
      tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-muted py-4">${escapeHtml(message)}</td></tr>`;
      return;
    }

    tableBody.innerHTML = '';
    visibleSites.forEach((site) => {
      const siteDisplayName = resolveSiteDisplayName(site);
      const hostDisplay = resolveHostDisplay(site);
      const endpointDisplay = resolveEndpointDisplay(site);
      const technicalEndpoint = String(site.endpoint_url || '').trim();
      const showHostLine = hostDisplay && !sameText(siteDisplayName, hostDisplay);
      const showTechnicalEndpoint = technicalEndpoint && !sameText(technicalEndpoint, endpointDisplay);
      const row = document.createElement('tr');
      row.dataset.siteId = String(site.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(siteDisplayName)}</div>
          ${showHostLine ? `<div class="small font-monospace text-muted">${escapeHtml(hostDisplay)}</div>` : ''}
          <div class="small text-muted">${site.enabled ? 'Активен' : 'Выключен'}</div>
        </td>
        <td>
          <div class="font-monospace">${escapeHtml(endpointDisplay)}</div>
          ${showTechnicalEndpoint ? `<div class="small text-muted font-monospace">${escapeHtml(technicalEndpoint)}</div>` : ''}
        </td>
        <td class="${resolveStatusCellClass(site)}">${resolveStatusBadge(site)}</td>
        <td class="${resolveAvailabilityCellClass(site)}">${resolveAvailabilityBadge(site)}</td>
        <td>${formatDateTime(site.expires_at)}</td>
        <td>${formatDaysLeft(site.days_left)}</td>
        <td>${formatDateTime(site.last_checked_at)}</td>
        <td class="small text-danger">${escapeHtml(site.error_message || '')}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm">
            <button class="btn btn-outline-secondary" type="button" data-action="refresh">Обновить</button>
            <button class="btn btn-outline-primary" type="button" data-action="edit">Изменить</button>
            <button class="btn btn-outline-danger" type="button" data-action="delete">Удалить</button>
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
    if (!token) {
      return init;
    }
    const headers = new Headers(init.headers || {});
    if (!headers.has('X-XSRF-TOKEN')) headers.set('X-XSRF-TOKEN', token);
    if (!headers.has('X-CSRF-TOKEN')) headers.set('X-CSRF-TOKEN', token);
    return { ...init, headers };
  }

  async function requestJson(url, init = {}) {
    const requestInit = withCsrf(init);
    const response = await fetch(url, {
      credentials: 'same-origin',
      cache: 'no-store',
      ...requestInit,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    return data;
  }

  async function loadSites() {
    if (!tableBody) return;
    tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Загрузка...</td></tr>';
    try {
      const data = await requestJson('/api/monitoring/certificates/sites');
      sites = Array.isArray(data.items) ? data.items : [];
      availabilityOverview = data.availability_overview || null;
      renderAvailabilityOverview();
      renderSitesTable();
    } catch (error) {
      renderAvailabilityOverview();
      tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  function resetCreateForm() {
    createForm?.reset();
    if (siteEnabledInput) siteEnabledInput.checked = true;
  }

  async function createSite(event) {
    event.preventDefault();
    const payload = {
      siteName: siteNameInput?.value || '',
      endpointUrl: siteEndpointInput?.value || '',
      enabled: siteEnabledInput?.checked ?? true,
    };
    try {
      await requestJson('/api/monitoring/certificates/sites', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      resetCreateForm();
      createModal?.hide();
      await loadSites();
      showMessage('Сайт добавлен и сертификат проверен.', 'success');
    } catch (error) {
      showMessage(`Не удалось добавить сайт: ${error.message}`, 'error');
    }
  }

  function findSiteById(siteId) {
    const numericId = Number(siteId);
    return sites.find((item) => Number(item.id) === numericId) || null;
  }

  function openEditModal(siteId) {
    const site = findSiteById(siteId);
    if (!site || !editModal) return;
    if (editSiteIdInput) editSiteIdInput.value = String(site.id);
    if (editSiteNameInput) editSiteNameInput.value = site.site_name || '';
    if (editSiteEndpointInput) editSiteEndpointInput.value = site.endpoint_url || '';
    if (editSiteEnabledInput) editSiteEnabledInput.checked = Boolean(site.enabled);
    editModal.show();
  }

  async function saveSiteChanges(event) {
    event.preventDefault();
    const siteId = Number(editSiteIdInput?.value || 0);
    if (!Number.isFinite(siteId) || siteId <= 0) {
      showMessage('Не удалось определить сайт для обновления.', 'error');
      return;
    }
    const payload = {
      siteName: editSiteNameInput?.value || '',
      endpointUrl: editSiteEndpointInput?.value || '',
      enabled: editSiteEnabledInput?.checked ?? true,
    };
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      editModal?.hide();
      await loadSites();
      showMessage('Изменения сохранены.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить изменения: ${error.message}`, 'error');
    }
  }

  async function refreshSite(siteId) {
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}/refresh`, {
        method: 'POST',
      });
      await loadSites();
      showMessage('Сертификат сайта обновлён.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить сертификат: ${error.message}`, 'error');
    }
  }

  async function deleteSite(siteId) {
    if (!window.confirm('Удалить сайт из мониторинга сертификатов?')) {
      return;
    }
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}`, {
        method: 'DELETE',
      });
      await loadSites();
      showMessage('Сайт удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить сайт: ${error.message}`, 'error');
    }
  }

  async function refreshAll() {
    if (refreshAllBtn) {
      refreshAllBtn.disabled = true;
      refreshAllBtn.textContent = 'Обновляем...';
    }
    try {
      await requestJson('/api/monitoring/certificates/refresh', {
        method: 'POST',
      });
      await loadSites();
      showMessage('Все сертификаты обновлены.', 'success');
    } catch (error) {
      showMessage(`Не удалось обновить сертификаты: ${error.message}`, 'error');
    } finally {
      if (refreshAllBtn) {
        refreshAllBtn.disabled = false;
        refreshAllBtn.textContent = 'Обновить все сертификаты';
      }
    }
  }

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr[data-site-id]');
    if (!row) return;
    const siteId = Number(row.dataset.siteId);
    if (!Number.isFinite(siteId)) return;
    if (button.dataset.action === 'refresh') {
      refreshSite(siteId);
      return;
    }
    if (button.dataset.action === 'edit') {
      openEditModal(siteId);
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
    renderSitesTable();
  });

  availabilityFiltersEl?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-filter-value]');
    if (!button) return;
    availabilityFilter = String(button.getAttribute('data-filter-value') || 'all');
    updateAvailabilityFilterButtons();
    renderSitesTable();
  });

  openCreateModalBtn?.addEventListener('click', () => {
    resetCreateForm();
    createModal?.show();
  });

  createForm?.addEventListener('submit', createSite);
  editForm?.addEventListener('submit', saveSiteChanges);
  refreshAllBtn?.addEventListener('click', refreshAll);

  loadSites();
})();

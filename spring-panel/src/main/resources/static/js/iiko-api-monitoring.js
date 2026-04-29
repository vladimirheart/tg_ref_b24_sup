(function () {
  const POLL_INTERVAL_MS = 15000;
  const DEFAULT_BASE_URL = 'https://api-ru.iiko.services';
  const TYPE_HINTS = {
    access_token: 'Только проверка авторизации: сервис получает bearer token по apiLogin.',
    organizations: 'Официальный read-only метод /api/1/organizations. Organization IDs здесь необязательны.',
    terminal_groups: 'Официальный read-only метод /api/1/terminal_groups. Нужен хотя бы один organization id.',
    nomenclature: 'Официальный read-only метод /api/1/nomenclature. Нужен один organization id.',
    stop_lists: 'Официальный read-only метод /api/1/stop_lists. Нужен хотя бы один organization id.',
    menu_by_id: 'Официальный read-only метод /api/2/menu/by_id. Нужны organization ids и external menu id.',
  };

  const tableBody = document.getElementById('iikoMonitoringTableBody');
  const queueStateEl = document.getElementById('iikoQueueState');
  const createForm = document.getElementById('iikoCreateForm');
  const openCreateModalBtn = document.getElementById('openIikoCreateModalBtn');
  const refreshBtn = document.getElementById('refreshIikoBtn');
  const bulkEnableBtn = document.getElementById('bulkEnableIikoBtn');
  const bulkDisableBtn = document.getElementById('bulkDisableIikoBtn');

  const createModalEl = document.getElementById('iikoCreateModal');
  const createModal = createModalEl && window.bootstrap ? new bootstrap.Modal(createModalEl) : null;

  const createMonitorNameInput = document.getElementById('iikoMonitorNameInput');
  const createBaseUrlInput = document.getElementById('iikoBaseUrlInput');
  const createApiLoginInput = document.getElementById('iikoApiLoginInput');
  const createRequestTypeInput = document.getElementById('iikoRequestTypeInput');
  const createEnabledInput = document.getElementById('iikoEnabledInput');
  let createLocationsSyncEnabledInput = document.getElementById('iikoLocationsSyncEnabledInput');
  const createOrganizationIdsInput = document.getElementById('iikoOrganizationIdsInput');
  const createOrganizationIdInput = document.getElementById('iikoOrganizationIdInput');
  const createTerminalGroupIdsInput = document.getElementById('iikoTerminalGroupIdsInput');
  const createExternalMenuIdInput = document.getElementById('iikoExternalMenuIdInput');
  const createPriceCategoryIdInput = document.getElementById('iikoPriceCategoryIdInput');
  const createMenuVersionInput = document.getElementById('iikoMenuVersionInput');
  const createLanguageInput = document.getElementById('iikoLanguageInput');
  const createStartRevisionInput = document.getElementById('iikoStartRevisionInput');
  const createReturnAdditionalInfoInput = document.getElementById('iikoReturnAdditionalInfoInput');
  const createIncludeDisabledInput = document.getElementById('iikoIncludeDisabledInput');
  const createReturnExternalDataInput = document.getElementById('iikoReturnExternalDataInput');
  const createReturnSizeInput = document.getElementById('iikoReturnSizeInput');
  const createTypeHint = document.getElementById('iikoCreateTypeHint');

  const editModalEl = document.getElementById('iikoEditModal');
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;
  const editForm = document.getElementById('iikoEditForm');
  const editIdInput = document.getElementById('editIikoIdInput');
  const editMonitorNameInput = document.getElementById('editIikoMonitorNameInput');
  const editBaseUrlInput = document.getElementById('editIikoBaseUrlInput');
  const editApiLoginInput = document.getElementById('editIikoApiLoginInput');
  const editRequestTypeInput = document.getElementById('editIikoRequestTypeInput');
  const editEnabledInput = document.getElementById('editIikoEnabledInput');
  let editLocationsSyncEnabledInput = document.getElementById('editIikoLocationsSyncEnabledInput');
  const editOrganizationIdsInput = document.getElementById('editIikoOrganizationIdsInput');
  const editOrganizationIdInput = document.getElementById('editIikoOrganizationIdInput');
  const editTerminalGroupIdsInput = document.getElementById('editIikoTerminalGroupIdsInput');
  const editExternalMenuIdInput = document.getElementById('editIikoExternalMenuIdInput');
  const editPriceCategoryIdInput = document.getElementById('editIikoPriceCategoryIdInput');
  const editMenuVersionInput = document.getElementById('editIikoMenuVersionInput');
  const editLanguageInput = document.getElementById('editIikoLanguageInput');
  const editStartRevisionInput = document.getElementById('editIikoStartRevisionInput');
  const editReturnAdditionalInfoInput = document.getElementById('editIikoReturnAdditionalInfoInput');
  const editIncludeDisabledInput = document.getElementById('editIikoIncludeDisabledInput');
  const editReturnExternalDataInput = document.getElementById('editIikoReturnExternalDataInput');
  const editReturnSizeInput = document.getElementById('editIikoReturnSizeInput');
  const editTypeHint = document.getElementById('iikoEditTypeHint');

  const responseModalEl = document.getElementById('iikoResponseModal');
  const responseModal = responseModalEl && window.bootstrap ? new bootstrap.Modal(responseModalEl) : null;
  const responseMeta = document.getElementById('iikoResponseMeta');
  const responseSummary = document.getElementById('iikoResponseSummary');
  const responseTimeline = document.getElementById('iikoResponseTimeline');
  const responseExcerpt = document.getElementById('iikoResponseExcerpt');

  let monitors = [];
  let refreshState = null;
  let requestTypes = [];
  let pollTimer = null;

  function ensureLocationSourceControls() {
    if (!createLocationsSyncEnabledInput && createEnabledInput) {
      const createColumn = createEnabledInput.closest('.col-12');
      if (createColumn) {
        const wrapper = document.createElement('div');
        wrapper.className = 'col-12 col-lg-3 iiko-config-section';
        wrapper.dataset.scope = 'create';
        wrapper.dataset.requestTypes = 'organizations';
        wrapper.innerHTML = `
          <div class="form-check form-switch mt-lg-4">
            <input class="form-check-input" type="checkbox" id="iikoLocationsSyncEnabledInput">
            <label class="form-check-label" for="iikoLocationsSyncEnabledInput">Источник структуры локаций</label>
          </div>
        `;
        createColumn.insertAdjacentElement('afterend', wrapper);
        createLocationsSyncEnabledInput = wrapper.querySelector('#iikoLocationsSyncEnabledInput');
      }
    }
    if (!editLocationsSyncEnabledInput && editEnabledInput) {
      const editColumn = editEnabledInput.closest('.col-12');
      if (editColumn) {
        const wrapper = document.createElement('div');
        wrapper.className = 'col-12 col-lg-6 iiko-config-section';
        wrapper.dataset.scope = 'edit';
        wrapper.dataset.requestTypes = 'organizations';
        wrapper.innerHTML = `
          <div class="form-check form-switch mt-lg-4">
            <input class="form-check-input" type="checkbox" id="editIikoLocationsSyncEnabledInput">
            <label class="form-check-label" for="editIikoLocationsSyncEnabledInput">Источник структуры локаций</label>
          </div>
        `;
        editColumn.insertAdjacentElement('afterend', wrapper);
        editLocationsSyncEnabledInput = wrapper.querySelector('#editIikoLocationsSyncEnabledInput');
      }
    }
  }

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

  function formatDuration(value) {
    if (value === null || value === undefined || value === '') return '—';
    const numeric = Number(value);
    return Number.isFinite(numeric) ? `${numeric} мс` : '—';
  }

  function normalizeStatus(value) {
    return String(value || '').trim().toLowerCase();
  }

  function statusBadge(level) {
    if (level === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (level === 'disabled') return '<span class="badge text-bg-secondary">Отключено</span>';
    return '<span class="badge text-bg-danger">Ошибка</span>';
  }

  function queueBadge(running, queued) {
    if (running) return '<span class="badge text-bg-primary">В работе</span>';
    if (queued) return '<span class="badge text-bg-warning">В очереди</span>';
    return '<span class="badge text-bg-secondary">Свободно</span>';
  }

  function summaryBadges(summary) {
    const entries = Object.entries(summary || {});
    if (!entries.length) {
      return '<span class="text-muted small">Нет сводки</span>';
    }
    return entries.slice(0, 6).map(([key, value]) => (
      `<span class="badge text-bg-light border">${escapeHtml(key)}: ${escapeHtml(value)}</span>`
    )).join('');
  }

  function renderTimeline(entries) {
    const items = Array.isArray(entries) ? entries : [];
    if (!items.length) {
      return '<div class="text-muted">История проверок пока не накоплена.</div>';
    }
    return items.map((entry) => {
      const checkKind = entry.check_kind || entry.checkKind || 'request';
      const httpStatus = entry.http_status ?? entry.httpStatus;
      const durationMs = entry.duration_ms ?? entry.durationMs;
      const createdAt = entry.created_at || entry.createdAt;
      const status = normalizeStatus(entry.status || '');
      return `
        <div class="border rounded-3 p-3 bg-body-tertiary">
          <div class="d-flex flex-wrap justify-content-between align-items-start gap-2">
            <div>
              <div class="small text-muted">${escapeHtml(checkKind)}</div>
              <div class="fw-semibold">${escapeHtml(formatDateTime(createdAt))}</div>
            </div>
            <div>${statusBadge(status)}</div>
          </div>
          <div class="small mt-2">${escapeHtml(entry.summary || '—')}</div>
          <div class="small text-muted mt-1">
            ${httpStatus !== null && httpStatus !== undefined && httpStatus !== '' ? `HTTP: ${escapeHtml(httpStatus)} ` : ''}
            ${durationMs !== null && durationMs !== undefined && durationMs !== '' ? `• ${escapeHtml(durationMs)} мс` : ''}
          </div>
        </div>
      `;
    }).join('');
  }

  function listToText(values) {
    return Array.isArray(values) && values.length ? values.join('\n') : '';
  }

  function textToList(value) {
    return String(value || '')
      .split(/[\n,;]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }

  function numberOrNull(value) {
    const raw = String(value || '').trim();
    if (!raw) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : null;
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

  function fillRequestTypeOptions(selectEl) {
    if (!selectEl) return;
    const current = selectEl.value;
    selectEl.innerHTML = '';
    requestTypes.forEach((item) => {
      const option = document.createElement('option');
      option.value = item.code;
      option.textContent = item.label;
      selectEl.appendChild(option);
    });
    if (current && [...selectEl.options].some((option) => option.value === current)) {
      selectEl.value = current;
    }
  }

  function updateConfigSections(scope, requestType) {
    document.querySelectorAll(`.iiko-config-section[data-scope="${scope}"]`).forEach((section) => {
      const supported = String(section.getAttribute('data-request-types') || '').split(/\s+/).filter(Boolean);
      section.hidden = !supported.includes(requestType);
    });
    if (scope === 'create' && requestType !== 'organizations' && createLocationsSyncEnabledInput) {
      createLocationsSyncEnabledInput.checked = false;
    }
    if (scope === 'edit' && requestType !== 'organizations' && editLocationsSyncEnabledInput) {
      editLocationsSyncEnabledInput.checked = false;
    }
    const hintEl = scope === 'create' ? createTypeHint : editTypeHint;
    if (hintEl) {
      hintEl.textContent = TYPE_HINTS[requestType] || 'Выберите тип запроса, чтобы увидеть параметры.';
    }
  }

  function buildPayload(scope) {
    const map = scope === 'create'
      ? {
          monitorName: createMonitorNameInput?.value || '',
          baseUrl: createBaseUrlInput?.value || '',
          apiLogin: createApiLoginInput?.value || '',
          requestType: createRequestTypeInput?.value || '',
          organizationIds: textToList(createOrganizationIdsInput?.value),
          organizationId: createOrganizationIdInput?.value || '',
          terminalGroupIds: textToList(createTerminalGroupIdsInput?.value),
          externalMenuId: createExternalMenuIdInput?.value || '',
          priceCategoryId: createPriceCategoryIdInput?.value || '',
          menuVersion: numberOrNull(createMenuVersionInput?.value),
          language: createLanguageInput?.value || '',
          startRevision: numberOrNull(createStartRevisionInput?.value),
          returnAdditionalInfo: createReturnAdditionalInfoInput?.checked || false,
          includeDisabled: createIncludeDisabledInput?.checked || false,
          returnExternalData: textToList(createReturnExternalDataInput?.value),
          returnSize: createReturnSizeInput?.checked || false,
          enabled: createEnabledInput?.checked ?? true,
          locationsSyncEnabled: (createRequestTypeInput?.value || '') === 'organizations'
            ? (createLocationsSyncEnabledInput?.checked || false)
            : false,
        }
      : {
          monitorName: editMonitorNameInput?.value || '',
          baseUrl: editBaseUrlInput?.value || '',
          apiLogin: editApiLoginInput?.value || '',
          requestType: editRequestTypeInput?.value || '',
          organizationIds: textToList(editOrganizationIdsInput?.value),
          organizationId: editOrganizationIdInput?.value || '',
          terminalGroupIds: textToList(editTerminalGroupIdsInput?.value),
          externalMenuId: editExternalMenuIdInput?.value || '',
          priceCategoryId: editPriceCategoryIdInput?.value || '',
          menuVersion: numberOrNull(editMenuVersionInput?.value),
          language: editLanguageInput?.value || '',
          startRevision: numberOrNull(editStartRevisionInput?.value),
          returnAdditionalInfo: editReturnAdditionalInfoInput?.checked || false,
          includeDisabled: editIncludeDisabledInput?.checked || false,
          returnExternalData: textToList(editReturnExternalDataInput?.value),
          returnSize: editReturnSizeInput?.checked || false,
          enabled: editEnabledInput?.checked ?? true,
          locationsSyncEnabled: (editRequestTypeInput?.value || '') === 'organizations'
            ? (editLocationsSyncEnabledInput?.checked || false)
            : false,
        };
    return map;
  }

  function resetCreateForm() {
    createForm?.reset();
    if (createBaseUrlInput) createBaseUrlInput.value = DEFAULT_BASE_URL;
    if (createEnabledInput) createEnabledInput.checked = true;
    if (createLocationsSyncEnabledInput) createLocationsSyncEnabledInput.checked = false;
    if (createRequestTypeInput && requestTypes.length) {
      createRequestTypeInput.value = requestTypes[0].code;
    }
    updateConfigSections('create', createRequestTypeInput?.value || '');
  }

  function renderQueueState() {
    if (!queueStateEl) return;
    if (!refreshState) {
      queueStateEl.textContent = 'Нет данных о состоянии очереди.';
      return;
    }
    queueStateEl.innerHTML = `
      <strong>Очередь:</strong> ${queueBadge(refreshState.running, refreshState.queued)}
      <span class="ms-2">последний запуск: ${escapeHtml(formatDateTime(refreshState.last_requested_at))}</span>
      <span class="ms-2">последнее завершение: ${escapeHtml(formatDateTime(refreshState.last_completed_at))}</span>
    `;
    if (refreshBtn) {
      refreshBtn.disabled = Boolean(refreshState.running || refreshState.queued);
      refreshBtn.textContent = refreshState.running
        ? 'Проверки выполняются...'
        : refreshState.queued
          ? 'Проверки в очереди'
          : 'Запустить проверки';
    }
  }

  function renderEmptyState(message, className) {
    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="8" class="text-center ${className} py-4">${escapeHtml(message)}</td></tr>`;
  }

  function renderMonitors() {
    if (!tableBody) return;
    if (!monitors.length) {
      renderEmptyState('Пока нет мониторов iiko API.', 'text-muted');
      return;
    }
    tableBody.innerHTML = '';
    monitors.forEach((item) => {
      const statusLevel = normalizeStatus(item.last_status_level || item.last_status);
      const summaryHtml = summaryBadges(item.last_response_summary || {});
      const row = document.createElement('tr');
      row.dataset.monitorId = String(item.id);
      const locationSourceBadge = item.locations_sync_enabled
        ? '<span class="badge text-bg-info-subtle border text-info-emphasis mt-2">Источник локаций</span>'
        : '';
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(item.monitor_name || '—')}</div>
          <div class="small text-muted font-mono">${escapeHtml(item.base_url || DEFAULT_BASE_URL)}</div>
          <div class="small text-muted">apikey: ${escapeHtml(item.api_login || '—')}</div>
          <div class="small text-muted">сбоев подряд: ${escapeHtml(item.consecutive_failures ?? 0)}</div>
        </td>
        <td>
          <div class="fw-semibold">${escapeHtml(item.request_type_label || item.request_type || '—')}</div>
          <div class="small text-muted font-mono">${escapeHtml(item.request_type || '—')}</div>
        </td>
        <td class="iiko-status-cell status-${escapeHtml(statusLevel || 'error')}">
          <div>${statusBadge(statusLevel)}</div>
          <div class="small text-muted mt-1">${escapeHtml(item.last_error_message || '')}</div>
        </td>
        <td>${escapeHtml(item.last_http_status ?? '—')}</td>
        <td>${escapeHtml(formatDuration(item.last_duration_ms))}</td>
        <td>
          <div>${escapeHtml(formatDateTime(item.last_checked_at))}</div>
          <div class="small text-muted">token: ${escapeHtml(formatDateTime(item.last_token_checked_at))}</div>
        </td>
        <td><div class="d-flex flex-wrap gap-2">${summaryHtml}</div></td>
        <td class="text-end">
          <div class="btn-group">
            <button class="btn btn-outline-primary btn-sm dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
              Действия
            </button>
            <ul class="dropdown-menu dropdown-menu-end">
              <li><button class="dropdown-item" type="button" data-action="refresh" ${item.enabled ? '' : 'disabled'}>Запустить</button></li>
              <li><button class="dropdown-item" type="button" data-action="response">Последний ответ</button></li>
              <li><hr class="dropdown-divider"></li>
              <li><button class="dropdown-item" type="button" data-action="edit">Изменить</button></li>
              <li><button class="dropdown-item text-danger" type="button" data-action="delete">Удалить</button></li>
            </ul>
          </div>
        </td>
      `;
      if (item.locations_sync_enabled) {
        row.querySelector('td')?.insertAdjacentHTML(
          'beforeend',
          '<span class="badge text-bg-info-subtle border text-info-emphasis mt-2">Источник локаций</span>'
        );
      }
      tableBody.appendChild(row);
    });
  }

  async function loadMonitors(showLoading) {
    if (showLoading) {
      renderEmptyState('Загрузка...', 'text-muted');
    }
    try {
      const data = await requestJson('/api/monitoring/iiko/monitors');
      monitors = Array.isArray(data.items) ? data.items : [];
      requestTypes = Array.isArray(data.request_types) ? data.request_types : [];
      refreshState = data.refresh_state || null;
      fillRequestTypeOptions(createRequestTypeInput);
      fillRequestTypeOptions(editRequestTypeInput);
      if (!createRequestTypeInput?.value && requestTypes.length) {
        createRequestTypeInput.value = requestTypes[0].code;
      }
      updateConfigSections('create', createRequestTypeInput?.value || '');
      updateConfigSections('edit', editRequestTypeInput?.value || '');
      renderQueueState();
      renderMonitors();
    } catch (error) {
      renderQueueState();
      renderEmptyState(error.message || 'Не удалось загрузить мониторы iiko.', 'text-danger');
    }
  }

  function findMonitor(monitorId) {
    const numericId = Number(monitorId);
    return monitors.find((item) => Number(item.id) === numericId) || null;
  }

  async function createMonitor(event) {
    event.preventDefault();
    const payload = buildPayload('create');
    try {
      await requestJson('/api/monitoring/iiko/monitors', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      resetCreateForm();
      createModal?.hide();
      await loadMonitors(false);
      showMessage('Монитор iiko API добавлен.', 'success');
    } catch (error) {
      showMessage(`Не удалось добавить монитор: ${error.message}`, 'error');
    }
  }

  function openEditModal(monitorId) {
    const item = findMonitor(monitorId);
    if (!item || !editModal) return;
    if (editIdInput) editIdInput.value = String(item.id);
    if (editMonitorNameInput) editMonitorNameInput.value = item.monitor_name || '';
    if (editBaseUrlInput) editBaseUrlInput.value = item.base_url || DEFAULT_BASE_URL;
    if (editApiLoginInput) editApiLoginInput.value = item.api_login || '';
    if (editRequestTypeInput) editRequestTypeInput.value = item.request_type || '';
    if (editEnabledInput) editEnabledInput.checked = Boolean(item.enabled);
    if (editLocationsSyncEnabledInput) editLocationsSyncEnabledInput.checked = Boolean(item.locations_sync_enabled);
    if (editOrganizationIdsInput) editOrganizationIdsInput.value = listToText(item.organization_ids);
    if (editOrganizationIdInput) editOrganizationIdInput.value = item.organization_id || '';
    if (editTerminalGroupIdsInput) editTerminalGroupIdsInput.value = listToText(item.terminal_group_ids);
    if (editExternalMenuIdInput) editExternalMenuIdInput.value = item.external_menu_id || '';
    if (editPriceCategoryIdInput) editPriceCategoryIdInput.value = item.price_category_id || '';
    if (editMenuVersionInput) editMenuVersionInput.value = item.menu_version ?? '';
    if (editLanguageInput) editLanguageInput.value = item.language || '';
    if (editStartRevisionInput) editStartRevisionInput.value = item.start_revision ?? '';
    if (editReturnAdditionalInfoInput) editReturnAdditionalInfoInput.checked = Boolean(item.return_additional_info);
    if (editIncludeDisabledInput) editIncludeDisabledInput.checked = Boolean(item.include_disabled);
    if (editReturnExternalDataInput) editReturnExternalDataInput.value = listToText(item.return_external_data);
    if (editReturnSizeInput) editReturnSizeInput.checked = Boolean(item.return_size);
    updateConfigSections('edit', editRequestTypeInput?.value || '');
    editModal.show();
  }

  async function saveMonitor(event) {
    event.preventDefault();
    const monitorId = Number(editIdInput?.value || 0);
    if (!Number.isFinite(monitorId) || monitorId <= 0) {
      showMessage('Не удалось определить запись для обновления.', 'error');
      return;
    }
    try {
      await requestJson(`/api/monitoring/iiko/monitors/${monitorId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildPayload('edit')),
      });
      editModal?.hide();
      await loadMonitors(false);
      showMessage('Монитор сохранён.', 'success');
    } catch (error) {
      showMessage(`Не удалось сохранить монитор: ${error.message}`, 'error');
    }
  }

  async function deleteMonitor(monitorId) {
    if (!window.confirm('Удалить этот монитор iiko API?')) {
      return;
    }
    try {
      await requestJson(`/api/monitoring/iiko/monitors/${monitorId}`, {
        method: 'DELETE',
      });
      await loadMonitors(false);
      showMessage('Монитор удалён.', 'success');
    } catch (error) {
      showMessage(`Не удалось удалить монитор: ${error.message}`, 'error');
    }
  }

  async function triggerRefresh(url, successMessage) {
    try {
      const data = await requestJson(url, {
        method: 'POST',
      });
      refreshState = data.refresh_state || refreshState;
      renderQueueState();
      await loadMonitors(false);
      showMessage(data.state === 'already_queued' ? 'Проверка уже стоит в очереди.' : successMessage, 'success');
    } catch (error) {
      showMessage(`Не удалось поставить проверку в очередь: ${error.message}`, 'error');
    }
  }

  async function toggleAll(enabled) {
    try {
      await requestJson('/api/monitoring/iiko/bulk/enabled', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ enabled }),
      });
      await loadMonitors(false);
      showMessage(enabled ? 'Все iiko-мониторы включены.' : 'Все iiko-мониторы отключены.', 'success');
    } catch (error) {
      showMessage(`Не удалось изменить массовый режим: ${error.message}`, 'error');
    }
  }

  async function openResponseModal(monitorId) {
    if (!responseModal || !responseMeta || !responseSummary || !responseTimeline || !responseExcerpt) return;
    const item = findMonitor(monitorId);
    responseMeta.textContent = item?.monitor_name || 'Загрузка...';
    responseSummary.innerHTML = '<span class="text-muted">Загрузка...</span>';
    responseTimeline.innerHTML = '<div class="text-muted">Загрузка...</div>';
    responseExcerpt.textContent = 'Загрузка...';
    responseModal.show();
    try {
      const data = await requestJson(`/api/monitoring/iiko/monitors/${monitorId}/response`);
      responseMeta.textContent = `${data.monitor_name || 'Монитор'} • ${data.request_type_label || data.request_type || '-'} • ${formatDateTime(data.last_checked_at)}`;
      responseSummary.innerHTML = summaryBadges(data.summary || {});
      responseTimeline.innerHTML = renderTimeline(data.timeline);
      responseExcerpt.textContent = String(data.response_excerpt || 'Нет данных.');
    } catch (error) {
      responseSummary.innerHTML = `<span class="text-danger">${escapeHtml(error.message || 'Не удалось загрузить ответ.')}</span>`;
      responseTimeline.innerHTML = '<div class="text-muted">Нет данных.</div>';
      responseExcerpt.textContent = 'Нет данных.';
    }
  }

  function startPolling() {
    if (pollTimer) {
      window.clearInterval(pollTimer);
    }
    pollTimer = window.setInterval(() => {
      loadMonitors(false);
    }, POLL_INTERVAL_MS);
  }

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr[data-monitor-id]');
    if (!row) return;
    const monitorId = Number(row.dataset.monitorId);
    if (!Number.isFinite(monitorId)) return;

    if (button.dataset.action === 'refresh') {
      triggerRefresh(`/api/monitoring/iiko/monitors/${monitorId}/refresh`, 'Проверка поставлена в очередь.');
      return;
    }
    if (button.dataset.action === 'response') {
      openResponseModal(monitorId);
      return;
    }
    if (button.dataset.action === 'edit') {
      openEditModal(monitorId);
      return;
    }
    if (button.dataset.action === 'delete') {
      deleteMonitor(monitorId);
    }
  });

  createRequestTypeInput?.addEventListener('change', () => {
    updateConfigSections('create', createRequestTypeInput.value);
  });
  editRequestTypeInput?.addEventListener('change', () => {
    updateConfigSections('edit', editRequestTypeInput.value);
  });
  openCreateModalBtn?.addEventListener('click', () => {
    resetCreateForm();
    createModal?.show();
  });

  createForm?.addEventListener('submit', createMonitor);
  editForm?.addEventListener('submit', saveMonitor);
  refreshBtn?.addEventListener('click', () => {
    triggerRefresh('/api/monitoring/iiko/refresh', 'Проверки поставлены в очередь.');
  });
  bulkEnableBtn?.addEventListener('click', () => {
    toggleAll(true);
  });
  bulkDisableBtn?.addEventListener('click', () => {
    toggleAll(false);
  });

  if (createBaseUrlInput && !createBaseUrlInput.value) {
    createBaseUrlInput.value = DEFAULT_BASE_URL;
  }

  ensureLocationSourceControls();
  loadMonitors(true);
  startPolling();
})();

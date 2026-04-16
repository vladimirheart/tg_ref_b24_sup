(function () {
  const stateEl = document.getElementById('employeeDiscountState');
  if (!stateEl) return;

  const refreshBtn = document.getElementById('employeeDiscountRefresh');
  const saveBtn = document.getElementById('employeeDiscountSave');
  const saveCredentialsBtn = document.getElementById('employeeDiscountSaveCredentials');
  const loadGroupsBtn = document.getElementById('employeeDiscountLoadGroups');
  const previewBtn = document.getElementById('employeeDiscountPreview');
  const loadCategoriesBtn = document.getElementById('employeeDiscountLoadCategories');
  const loadWalletsBtn = document.getElementById('employeeDiscountLoadWallets');
  const runDryBtn = document.getElementById('employeeDiscountRunDry');
  const runExecBtn = document.getElementById('employeeDiscountRunExec');

  const scopeEl = document.getElementById('employeeDiscountConfigScope');
  const bitrixStateEl = document.getElementById('employeeDiscountBitrixState');
  const iikoStateEl = document.getElementById('employeeDiscountIikoState');
  const secretsStateEl = document.getElementById('employeeDiscountSecretsState');
  const connectionMessageEl = document.getElementById('employeeDiscountConnectionMessage');
  const groupsEl = document.getElementById('employeeDiscountGroups');
  const previewEl = document.getElementById('employeeDiscountPreviewList');
  const categoriesEl = document.getElementById('employeeDiscountCategories');
  const walletsEl = document.getElementById('employeeDiscountWallets');
  const runsEl = document.getElementById('employeeDiscountRuns');
  const runDetailsEl = document.getElementById('employeeDiscountRunDetails');

  const bitrixPortalUrlInput = document.getElementById('employeeDiscountBitrixPortalUrl');
  const bitrixWebhookUrlInput = document.getElementById('employeeDiscountBitrixWebhookUrl');
  const iikoGroupNameInput = document.getElementById('employeeDiscountIikoGroupName');
  const iikoBaseUrlInput = document.getElementById('employeeDiscountIikoBaseUrl');
  const iikoLoginInput = document.getElementById('employeeDiscountIikoLogin');
  const iikoPasswordInput = document.getElementById('employeeDiscountIikoPassword');
  const iikoTokenInput = document.getElementById('employeeDiscountIikoToken');
  const iikoOrganizationIdInput = document.getElementById('employeeDiscountIikoOrganizationId');
  const iikoCategoriesUrlInput = document.getElementById('employeeDiscountIikoCategoriesUrl');
  const iikoWalletsUrlInput = document.getElementById('employeeDiscountIikoWalletsUrl');
  const iikoCustomerLookupUrlInput = document.getElementById('employeeDiscountIikoCustomerLookupUrl');
  const iikoCustomerUpdateUrlInput = document.getElementById('employeeDiscountIikoCustomerUpdateUrl');

  const groupIdInput = document.getElementById('employeeDiscountGroupId');
  const titleMarkersInput = document.getElementById('employeeDiscountTitleMarkers');
  const checklistLabelsInput = document.getElementById('employeeDiscountChecklistLabels');
  const phoneRegexInput = document.getElementById('employeeDiscountPhoneRegex');
  const categoryIdsInput = document.getElementById('employeeDiscountCategoryIds');
  const walletIdsInput = document.getElementById('employeeDiscountWalletIds');
  const dryRunDefaultInput = document.getElementById('employeeDiscountDryRunDefault');

  function getCookieValue(name) {
    const cookies = String(document.cookie || '').split(';');
    const expected = `${encodeURIComponent(name)}=`;
    for (const cookie of cookies) {
      const value = cookie.trim();
      if (value.startsWith(expected)) {
        return decodeURIComponent(value.slice(expected.length));
      }
    }
    return '';
  }

  function withCsrfHeaders(headers = {}) {
    const tokenFromMeta = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const token = tokenFromMeta || getCookieValue('XSRF-TOKEN');
    if (!token) return headers;
    return { ...headers, 'X-XSRF-TOKEN': token };
  }

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function parseLines(value) {
    return String(value || '')
      .split(/[\r\n,;]+/)
      .map((item) => item.trim())
      .filter(Boolean);
  }

  async function requestJson(url, options = {}) {
    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', ...options });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      throw new Error(payload.error || `HTTP ${response.status}`);
    }
    return payload;
  }

  function fillSettings(settings) {
    const data = settings || {};
    if (groupIdInput) groupIdInput.value = data.bitrix_group_id || '';
    if (titleMarkersInput) titleMarkersInput.value = Array.isArray(data.task_title_markers) ? data.task_title_markers.join('\n') : '';
    if (checklistLabelsInput) checklistLabelsInput.value = Array.isArray(data.checklist_labels) ? data.checklist_labels.join('\n') : '';
    if (phoneRegexInput) phoneRegexInput.value = data.phone_regex || '';
    if (categoryIdsInput) categoryIdsInput.value = Array.isArray(data.selected_discount_category_ids) ? data.selected_discount_category_ids.join('\n') : '';
    if (walletIdsInput) walletIdsInput.value = Array.isArray(data.excluded_wallet_ids) ? data.excluded_wallet_ids.join('\n') : '';
    if (dryRunDefaultInput) dryRunDefaultInput.checked = !!data.dry_run_by_default;
  }

  function fillCredentials(credentials) {
    const data = credentials || {};
    const bitrix = data.bitrix24 || {};
    const iiko = data.iiko || {};
    if (scopeEl) scopeEl.textContent = data.scope || '-';
    if (bitrixPortalUrlInput) bitrixPortalUrlInput.value = bitrix.portal_url || '';
    if (bitrixWebhookUrlInput) bitrixWebhookUrlInput.value = '';
    if (iikoGroupNameInput) iikoGroupNameInput.value = iiko.group_name || '';
    if (iikoBaseUrlInput) iikoBaseUrlInput.value = iiko.base_url || '';
    if (iikoLoginInput) iikoLoginInput.value = iiko.login || '';
    if (iikoPasswordInput) iikoPasswordInput.value = '';
    if (iikoTokenInput) iikoTokenInput.value = '';
    if (iikoOrganizationIdInput) iikoOrganizationIdInput.value = iiko.organization_id || '';
    if (iikoCategoriesUrlInput) iikoCategoriesUrlInput.value = iiko.categories_url || '';
    if (iikoWalletsUrlInput) iikoWalletsUrlInput.value = iiko.wallets_url || '';
    if (iikoCustomerLookupUrlInput) iikoCustomerLookupUrlInput.value = iiko.customer_lookup_url || '';
    if (iikoCustomerUpdateUrlInput) iikoCustomerUpdateUrlInput.value = iiko.customer_update_url || '';

    const savedSecrets = [];
    savedSecrets.push(bitrix.webhook_saved ? 'webhook Bitrix24 сохранён' : 'webhook Bitrix24 не задан');
    savedSecrets.push(iiko.password_saved ? 'пароль iiko сохранён' : 'пароль iiko не задан');
    savedSecrets.push(iiko.token_saved ? `token iiko сохранён (${iiko.auth_mode || 'bearer'})` : `token iiko не задан (${iiko.auth_mode || 'none'})`);
    if (secretsStateEl) secretsStateEl.textContent = savedSecrets.join(' | ');
  }

  function renderGroups(items) {
    if (!groupsEl) return;
    if (!Array.isArray(items) || !items.length) {
      groupsEl.innerHTML = '<div class="text-muted">Нет данных.</div>';
      return;
    }
    groupsEl.innerHTML = items.map((item) => `
      <button class="btn btn-sm btn-outline-secondary me-2 mb-2" type="button" data-group-id="${escapeHtml(item.id)}">
        ${escapeHtml(item.name)} <span class="text-muted">#${escapeHtml(item.id)}</span>
      </button>
    `).join('');
  }

  function renderCatalog(target, items) {
    if (!target) return;
    if (!Array.isArray(items) || !items.length) {
      target.innerHTML = '<div class="text-muted">Нет данных.</div>';
      return;
    }
    target.innerHTML = items.map((item) => `
      <div class="border rounded p-2 mb-1">
        <div class="fw-semibold">${escapeHtml(item.name)}</div>
        <div class="text-muted">id: ${escapeHtml(item.id)}</div>
      </div>
    `).join('');
  }

  function renderPreview(items) {
    if (!previewEl) return;
    if (!Array.isArray(items) || !items.length) {
      previewEl.innerHTML = '<div class="text-muted">Нет задач.</div>';
      return;
    }
    previewEl.innerHTML = items.map((item) => `
      <div class="border rounded p-2 mb-1">
        <div class="d-flex justify-content-between gap-2">
          <span class="fw-semibold">${escapeHtml(item.title || '(без названия)')}</span>
          <span class="text-muted">${escapeHtml(item.status)}</span>
        </div>
        <div class="text-muted">task: ${escapeHtml(item.task_id)} | phone: ${escapeHtml(item.phone || '-')}</div>
        <div>${escapeHtml(item.message || '')}</div>
      </div>
    `).join('');
  }

  function renderRuns(items) {
    if (!runsEl) return;
    if (!Array.isArray(items) || !items.length) {
      runsEl.innerHTML = '<div class="text-muted">История пуста.</div>';
      return;
    }
    runsEl.innerHTML = items.map((item) => `
      <button class="btn btn-sm btn-outline-secondary w-100 text-start mb-2" type="button" data-run-id="${escapeHtml(item.id)}">
        <div class="fw-semibold">#${escapeHtml(item.id)} ${escapeHtml(item.mode)}</div>
        <div class="text-muted small">${escapeHtml(item.status)} | ${escapeHtml(item.summary || '')}</div>
      </button>
    `).join('');
  }

  function renderRunDetails(payload) {
    if (!runDetailsEl) return;
    const run = payload?.run || {};
    const items = Array.isArray(payload?.items) ? payload.items : [];
    if (!run.id) {
      runDetailsEl.innerHTML = '<div class="text-muted">Выберите запуск.</div>';
      return;
    }
    runDetailsEl.innerHTML = `
      <div class="mb-2">
        <div class="fw-semibold">Запуск #${escapeHtml(run.id)}</div>
        <div class="text-muted small">${escapeHtml(run.mode)} | ${escapeHtml(run.status)} | ${escapeHtml(run.summary || '')}</div>
      </div>
      ${items.map((item) => `
        <div class="border rounded p-2 mb-1">
          <div class="d-flex justify-content-between gap-2">
            <span class="fw-semibold">${escapeHtml(item.title || '(без названия)')}</span>
            <span class="text-muted">${escapeHtml(item.status)}</span>
          </div>
          <div class="text-muted">task: ${escapeHtml(item.task_id || '')} | phone: ${escapeHtml(item.phone || '-')}</div>
          <div>${escapeHtml(item.message || '')}</div>
        </div>
      `).join('')}
    `;
  }

  async function loadStatus() {
    stateEl.textContent = 'Загрузка статуса...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/status');
      const status = payload.status || {};
      const credentials = status.credentials || {};
      const bitrix = status.bitrix_connection || {};
      const iiko = status.iiko_connection || {};
      fillSettings(status.settings || {});
      fillCredentials(credentials);
      if (bitrixStateEl) bitrixStateEl.textContent = bitrix.reachable ? 'ok' : (bitrix.configured ? 'configured, but unreachable' : 'not configured');
      if (iikoStateEl) iikoStateEl.textContent = iiko.mutation_ready ? `mutation ready (${iiko.auth_mode || 'none'})` : (iiko.configured ? `discovery only (${iiko.auth_mode || 'none'})` : 'not configured');
      if (connectionMessageEl) connectionMessageEl.textContent = bitrix.message || '-';
      stateEl.textContent = 'Статус обновлён.';
    } catch (error) {
      stateEl.textContent = `Ошибка загрузки статуса: ${error.message || 'unknown_error'}`;
    }
  }

  async function saveCredentials() {
    stateEl.textContent = 'Сохраняю личные доступы...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/credentials', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          bitrix24: {
            portal_url: bitrixPortalUrlInput?.value || '',
            webhook_url: bitrixWebhookUrlInput?.value || '',
          },
          iiko: {
            group_name: iikoGroupNameInput?.value || '',
            base_url: iikoBaseUrlInput?.value || '',
            login: iikoLoginInput?.value || '',
            password: iikoPasswordInput?.value || '',
            token: iikoTokenInput?.value || '',
            organization_id: iikoOrganizationIdInput?.value || '',
            categories_url: iikoCategoriesUrlInput?.value || '',
            wallets_url: iikoWalletsUrlInput?.value || '',
            customer_lookup_url: iikoCustomerLookupUrlInput?.value || '',
            customer_update_url: iikoCustomerUpdateUrlInput?.value || '',
          },
        }),
      });
      fillCredentials(payload.credentials || {});
      await loadStatus();
      stateEl.textContent = 'Личные доступы сохранены.';
    } catch (error) {
      stateEl.textContent = `Ошибка сохранения доступов: ${error.message || 'unknown_error'}`;
    }
  }

  async function saveSettings() {
    stateEl.textContent = 'Сохраняю настройки...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/settings', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          bitrix_group_id: groupIdInput?.value || '',
          task_title_markers: parseLines(titleMarkersInput?.value),
          checklist_labels: parseLines(checklistLabelsInput?.value),
          phone_regex: phoneRegexInput?.value || '',
          selected_discount_category_ids: parseLines(categoryIdsInput?.value),
          excluded_wallet_ids: parseLines(walletIdsInput?.value),
          dry_run_by_default: !!dryRunDefaultInput?.checked,
        }),
      });
      fillSettings(payload.settings || {});
      stateEl.textContent = 'Настройки сохранены.';
    } catch (error) {
      stateEl.textContent = `Ошибка сохранения: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadGroups() {
    stateEl.textContent = 'Загружаю группы Bitrix24...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/bitrix/groups');
      renderGroups(payload.items);
      stateEl.textContent = 'Группы загружены.';
    } catch (error) {
      groupsEl.innerHTML = '<div class="text-danger">Не удалось загрузить группы.</div>';
      stateEl.textContent = `Ошибка загрузки групп: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadPreview() {
    stateEl.textContent = 'Строю preview задач...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/preview');
      renderPreview(payload.items);
      stateEl.textContent = `Preview готов: ${(payload.items || []).length} задач.`;
    } catch (error) {
      previewEl.innerHTML = '<div class="text-danger">Не удалось построить preview.</div>';
      stateEl.textContent = `Ошибка preview: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadCategories() {
    stateEl.textContent = 'Загружаю категории iiko...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/iiko/categories');
      renderCatalog(categoriesEl, payload.items);
      stateEl.textContent = 'Категории iiko загружены.';
    } catch (error) {
      categoriesEl.innerHTML = '<div class="text-danger">Не удалось загрузить категории.</div>';
      stateEl.textContent = `Ошибка категорий: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadWallets() {
    stateEl.textContent = 'Загружаю кошельки iiko...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/iiko/wallets');
      renderCatalog(walletsEl, payload.items);
      stateEl.textContent = 'Кошельки iiko загружены.';
    } catch (error) {
      walletsEl.innerHTML = '<div class="text-danger">Не удалось загрузить кошельки.</div>';
      stateEl.textContent = `Ошибка кошельков: ${error.message || 'unknown_error'}`;
    }
  }

  async function runAutomation(dryRun) {
    stateEl.textContent = dryRun ? 'Запускаю dry-run...' : 'Запускаю боевой режим...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/runs', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ dry_run: dryRun }),
      });
      renderRunDetails(payload);
      await loadRuns();
      stateEl.textContent = dryRun ? 'Dry-run завершён.' : 'Боевой запуск завершён.';
    } catch (error) {
      stateEl.textContent = `Ошибка запуска: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadRuns() {
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/runs');
      renderRuns(payload.items);
    } catch (error) {
      runsEl.innerHTML = '<div class="text-danger">Не удалось загрузить историю.</div>';
    }
  }

  async function loadRunDetails(runId) {
    if (!runId) return;
    runDetailsEl.innerHTML = '<div class="text-muted">Загрузка деталей...</div>';
    try {
      const payload = await requestJson(`/api/ai-ops/employee-discounts/runs/${encodeURIComponent(runId)}`);
      renderRunDetails(payload);
    } catch (error) {
      runDetailsEl.innerHTML = '<div class="text-danger">Не удалось загрузить детали.</div>';
    }
  }

  if (refreshBtn) refreshBtn.addEventListener('click', loadStatus);
  if (saveBtn) saveBtn.addEventListener('click', saveSettings);
  if (saveCredentialsBtn) saveCredentialsBtn.addEventListener('click', saveCredentials);
  if (loadGroupsBtn) loadGroupsBtn.addEventListener('click', loadGroups);
  if (previewBtn) previewBtn.addEventListener('click', loadPreview);
  if (loadCategoriesBtn) loadCategoriesBtn.addEventListener('click', loadCategories);
  if (loadWalletsBtn) loadWalletsBtn.addEventListener('click', loadWallets);
  if (runDryBtn) runDryBtn.addEventListener('click', () => runAutomation(true));
  if (runExecBtn) runExecBtn.addEventListener('click', () => runAutomation(false));
  if (groupsEl) {
    groupsEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-group-id]');
      if (!btn) return;
      if (groupIdInput) groupIdInput.value = btn.getAttribute('data-group-id') || '';
    });
  }
  if (runsEl) {
    runsEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-run-id]');
      if (!btn) return;
      loadRunDetails(btn.getAttribute('data-run-id'));
    });
  }

  loadStatus();
  loadRuns();
  renderRunDetails(null);
})();

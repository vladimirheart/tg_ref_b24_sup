(function () {
  const stateEl = document.getElementById('employeeDiscountState');
  if (!stateEl) return;

  const state = {
    activeProfileUrl: '',
    selectedCategoryIds: new Set(),
    selectedWalletIds: new Set(),
  };

  const refreshBtn = document.getElementById('employeeDiscountRefresh');
  const saveBtn = document.getElementById('employeeDiscountSave');
  const saveCredentialsBtn = document.getElementById('employeeDiscountSaveCredentials');
  const loadGroupsBtn = document.getElementById('employeeDiscountLoadGroups');
  const loadOrganizationsBtn = document.getElementById('employeeDiscountLoadOrganizations');
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
  const profilesEl = document.getElementById('employeeDiscountProfiles');
  const orgsEl = document.getElementById('employeeDiscountOrganizations');
  const groupsEl = document.getElementById('employeeDiscountGroups');
  const previewEl = document.getElementById('employeeDiscountPreviewList');
  const categoriesEl = document.getElementById('employeeDiscountCategories');
  const walletsEl = document.getElementById('employeeDiscountWallets');
  const runsEl = document.getElementById('employeeDiscountRuns');
  const runDetailsEl = document.getElementById('employeeDiscountRunDetails');

  const bitrixPortalUrlInput = document.getElementById('employeeDiscountBitrixPortalUrl');
  const bitrixWebhookUrlInput = document.getElementById('employeeDiscountBitrixWebhookUrl');
  const iikoBaseUrlInput = document.getElementById('employeeDiscountIikoBaseUrl');
  const iikoApiLoginInput = document.getElementById('employeeDiscountIikoApiLogin');
  const iikoApiSecretInput = document.getElementById('employeeDiscountIikoApiSecret');
  const iikoOrganizationIdInput = document.getElementById('employeeDiscountIikoOrganizationId');

  const groupIdInput = document.getElementById('employeeDiscountGroupId');
  const titleMarkersInput = document.getElementById('employeeDiscountTitleMarkers');
  const checklistLabelsInput = document.getElementById('employeeDiscountChecklistLabels');
  const phoneRegexInput = document.getElementById('employeeDiscountPhoneRegex');
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
    if (dryRunDefaultInput) dryRunDefaultInput.checked = !!data.dry_run_by_default;
  }

  function renderProfiles(items) {
    if (!profilesEl) return;
    if (!Array.isArray(items) || !items.length) {
      profilesEl.innerHTML = '<div class="text-muted">URL-профили ещё не сохранены.</div>';
      return;
    }
    profilesEl.innerHTML = items.map((item) => `
      <button class="btn btn-sm ${item.active ? 'btn-primary' : 'btn-outline-secondary'} me-2 mb-2" type="button" data-profile-url="${escapeHtml(item.base_url)}">
        ${escapeHtml(item.base_url)}
      </button>
    `).join('');
  }

  function fillCredentials(credentials) {
    const data = credentials || {};
    const bitrix = data.bitrix24 || {};
    const activeProfile = data.iiko || {};
    const profiles = Array.isArray(data.iiko_profiles) ? data.iiko_profiles : [];

    state.activeProfileUrl = data.active_iiko_profile_url || activeProfile.base_url || '';
    state.selectedCategoryIds = new Set(Array.isArray(activeProfile.selected_discount_category_ids) ? activeProfile.selected_discount_category_ids : []);
    state.selectedWalletIds = new Set(Array.isArray(activeProfile.selected_wallet_ids) ? activeProfile.selected_wallet_ids : []);

    if (scopeEl) scopeEl.textContent = data.scope || '-';
    if (bitrixPortalUrlInput) bitrixPortalUrlInput.value = bitrix.portal_url || '';
    if (bitrixWebhookUrlInput) bitrixWebhookUrlInput.value = '';
    if (iikoBaseUrlInput) iikoBaseUrlInput.value = activeProfile.base_url || '';
    if (iikoApiLoginInput) iikoApiLoginInput.value = activeProfile.api_login || '';
    if (iikoApiSecretInput) iikoApiSecretInput.value = '';
    if (iikoOrganizationIdInput) iikoOrganizationIdInput.value = activeProfile.organization_id || '';
    renderProfiles(profiles);

    const savedSecrets = [];
    savedSecrets.push(bitrix.webhook_saved ? 'webhook Bitrix24 сохранён' : 'webhook Bitrix24 не задан');
    savedSecrets.push(activeProfile.api_secret_saved ? 'api secret iiko сохранён' : 'api secret iiko не задан');
    savedSecrets.push(state.activeProfileUrl ? `активный URL: ${state.activeProfileUrl}` : 'активный URL не выбран');
    if (secretsStateEl) secretsStateEl.textContent = savedSecrets.join(' | ');
  }

  function renderOrganizations(items) {
    if (!orgsEl) return;
    if (!Array.isArray(items) || !items.length) {
      orgsEl.innerHTML = '<div class="text-muted">Нет данных по организациям.</div>';
      return;
    }
    orgsEl.innerHTML = items.map((item) => `
      <button class="btn btn-sm btn-outline-secondary me-2 mb-2 text-start" type="button" data-org-id="${escapeHtml(item.id)}">
        ${escapeHtml(item.name)} <span class="text-muted">#${escapeHtml(item.id)}</span>
      </button>
    `).join('');
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

  function renderCatalog(target, items, type) {
    if (!target) return;
    const selected = type === 'categories' ? state.selectedCategoryIds : state.selectedWalletIds;
    if (!Array.isArray(items) || !items.length) {
      target.innerHTML = '<div class="text-muted">Нет данных.</div>';
      return;
    }
    target.innerHTML = items.map((item) => {
      const checked = selected.has(String(item.id || '')) ? 'checked' : '';
      const secondary = type === 'wallets' ? escapeHtml((item.wallet_names || []).join(', ')) : '';
      return `
        <label class="border rounded p-2 mb-1 d-block">
          <div class="form-check">
            <input class="form-check-input" type="checkbox" data-catalog-type="${type}" data-item-id="${escapeHtml(item.id)}" ${checked}>
            <span class="form-check-label fw-semibold">${escapeHtml(item.name)}</span>
          </div>
          <div class="text-muted small">id: ${escapeHtml(item.id)}</div>
          ${secondary ? `<div class="text-muted small">кошельки: ${secondary}</div>` : ''}
        </label>
      `;
    }).join('');
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
      if (iikoStateEl) iikoStateEl.textContent = iiko.mutation_ready
        ? `ready | org=${escapeHtml(iiko.organization_id || '-')} | cat=${escapeHtml(iiko.selected_categories_count || 0)} | wallet=${escapeHtml(iiko.selected_wallets_count || 0)}`
        : (iiko.configured ? 'profile saved, selection incomplete' : 'not configured');
      if (connectionMessageEl) connectionMessageEl.textContent = bitrix.message || '-';
      stateEl.textContent = 'Статус обновлён.';
    } catch (error) {
      stateEl.textContent = `Ошибка загрузки статуса: ${error.message || 'unknown_error'}`;
    }
  }

  async function saveCredentials() {
    stateEl.textContent = 'Сохраняю URL-профиль и личные доступы...';
    try {
      const baseUrl = String(iikoBaseUrlInput?.value || '').trim();
      const payload = await requestJson('/api/ai-ops/employee-discounts/credentials', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          bitrix24: {
            portal_url: bitrixPortalUrlInput?.value || '',
            webhook_url: bitrixWebhookUrlInput?.value || '',
          },
          iiko_profile: {
            base_url: baseUrl,
            api_login: iikoApiLoginInput?.value || '',
            api_secret: iikoApiSecretInput?.value || '',
            organization_id: iikoOrganizationIdInput?.value || '',
            selected_discount_category_ids: Array.from(state.selectedCategoryIds),
            selected_wallet_ids: Array.from(state.selectedWalletIds),
          },
          select_profile_url: baseUrl,
        }),
      });
      fillCredentials(payload.credentials || {});
      await loadStatus();
      stateEl.textContent = 'URL-профиль и доступы сохранены.';
    } catch (error) {
      stateEl.textContent = `Ошибка сохранения доступов: ${error.message || 'unknown_error'}`;
    }
  }

  async function selectProfile(profileUrl) {
    stateEl.textContent = 'Переключаю URL-профиль...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/credentials', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ select_profile_url: profileUrl }),
      });
      fillCredentials(payload.credentials || {});
      stateEl.textContent = 'Профиль переключён.';
    } catch (error) {
      stateEl.textContent = `Ошибка переключения профиля: ${error.message || 'unknown_error'}`;
    }
  }

  async function saveSettings() {
    stateEl.textContent = 'Сохраняю настройки Bitrix24-анализа...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/settings', {
        method: 'POST',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({
          bitrix_group_id: groupIdInput?.value || '',
          task_title_markers: parseLines(titleMarkersInput?.value),
          checklist_labels: parseLines(checklistLabelsInput?.value),
          phone_regex: phoneRegexInput?.value || '',
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

  async function loadOrganizations() {
    stateEl.textContent = 'Загружаю организации iiko...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/iiko/organizations');
      renderOrganizations(payload.items);
      stateEl.textContent = 'Организации iiko загружены.';
    } catch (error) {
      orgsEl.innerHTML = '<div class="text-danger">Не удалось загрузить организации.</div>';
      stateEl.textContent = `Ошибка организаций: ${error.message || 'unknown_error'}`;
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
    stateEl.textContent = 'Загружаю активные категории iiko...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/iiko/categories');
      renderCatalog(categoriesEl, payload.items, 'categories');
      stateEl.textContent = 'Категории iiko загружены. Отметьте нужные и сохраните профиль.';
    } catch (error) {
      categoriesEl.innerHTML = '<div class="text-danger">Не удалось загрузить категории.</div>';
      stateEl.textContent = `Ошибка категорий: ${error.message || 'unknown_error'}`;
    }
  }

  async function loadWallets() {
    stateEl.textContent = 'Загружаю активные кошельки/программы iiko...';
    try {
      const payload = await requestJson('/api/ai-ops/employee-discounts/iiko/wallets');
      renderCatalog(walletsEl, payload.items, 'wallets');
      stateEl.textContent = 'Кошельки/программы iiko загружены. Отметьте нужные и сохраните профиль.';
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

  function toggleSelection(type, itemId, checked) {
    const target = type === 'categories' ? state.selectedCategoryIds : state.selectedWalletIds;
    if (!itemId) return;
    if (checked) target.add(itemId);
    else target.delete(itemId);
  }

  if (refreshBtn) refreshBtn.addEventListener('click', loadStatus);
  if (saveBtn) saveBtn.addEventListener('click', saveSettings);
  if (saveCredentialsBtn) saveCredentialsBtn.addEventListener('click', saveCredentials);
  if (loadGroupsBtn) loadGroupsBtn.addEventListener('click', loadGroups);
  if (loadOrganizationsBtn) loadOrganizationsBtn.addEventListener('click', loadOrganizations);
  if (previewBtn) previewBtn.addEventListener('click', loadPreview);
  if (loadCategoriesBtn) loadCategoriesBtn.addEventListener('click', loadCategories);
  if (loadWalletsBtn) loadWalletsBtn.addEventListener('click', loadWallets);
  if (runDryBtn) runDryBtn.addEventListener('click', () => runAutomation(true));
  if (runExecBtn) runExecBtn.addEventListener('click', () => runAutomation(false));

  if (profilesEl) {
    profilesEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-profile-url]');
      if (!btn) return;
      selectProfile(btn.getAttribute('data-profile-url') || '');
    });
  }
  if (orgsEl) {
    orgsEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-org-id]');
      if (!btn) return;
      if (iikoOrganizationIdInput) iikoOrganizationIdInput.value = btn.getAttribute('data-org-id') || '';
    });
  }
  if (groupsEl) {
    groupsEl.addEventListener('click', (event) => {
      const btn = event.target.closest('[data-group-id]');
      if (!btn) return;
      if (groupIdInput) groupIdInput.value = btn.getAttribute('data-group-id') || '';
    });
  }
  if (categoriesEl) {
    categoriesEl.addEventListener('change', (event) => {
      const input = event.target.closest('[data-catalog-type="categories"][data-item-id]');
      if (!input) return;
      toggleSelection('categories', input.getAttribute('data-item-id') || '', !!input.checked);
    });
  }
  if (walletsEl) {
    walletsEl.addEventListener('change', (event) => {
      const input = event.target.closest('[data-catalog-type="wallets"][data-item-id]');
      if (!input) return;
      toggleSelection('wallets', input.getAttribute('data-item-id') || '', !!input.checked);
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

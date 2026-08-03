(function () {
  if (window.SettingsNetBoxSyncRuntime) {
    return;
  }

  function fallbackEscapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function normalizeSiteIdList(source) {
    if (!Array.isArray(source)) {
      return [];
    }
    const result = [];
    const seen = new Set();
    source.forEach((item) => {
      const normalized = String(item ?? '').trim();
      if (!normalized || seen.has(normalized)) {
        return;
      }
      seen.add(normalized);
      result.push(normalized);
    });
    return result;
  }

  function normalizeSiteCatalog(source) {
    if (!Array.isArray(source)) {
      return [];
    }
    return source
      .map((item) => ({
        id: String(item?.id ?? '').trim(),
        name: String(item?.name ?? item?.display ?? '').trim(),
        status: String(item?.status ?? '').trim(),
      }))
      .filter((item) => item.id && item.name);
  }

  function sanitizeSettings(source) {
    const normalized = source && typeof source === 'object' ? source : {};
    const enabled = typeof normalized.enabled === 'boolean' ? normalized.enabled : false;
    const intervalRaw = Number.parseInt(String(normalized.interval_minutes ?? normalized.intervalMinutes ?? 60), 10);
    return {
      base_url: typeof normalized.base_url === 'string'
        ? normalized.base_url.trim().replace(/\/+$/, '')
        : typeof normalized.baseUrl === 'string'
          ? normalized.baseUrl.trim().replace(/\/+$/, '')
          : '',
      api_token: typeof normalized.api_token === 'string' ? normalized.api_token : '',
      api_token_saved: Boolean(normalized.api_token_saved || normalized.apiTokenSaved),
      clear_api_token: Boolean(normalized.clear_api_token || normalized.clearApiToken),
      enabled,
      interval_minutes: Number.isFinite(intervalRaw) ? Math.min(10080, Math.max(5, intervalRaw)) : 60,
      full_overwrite_pending: normalized.full_overwrite_pending !== false && normalized.fullOverwritePending !== false,
      selected_site_ids: normalizeSiteIdList(normalized.selected_site_ids ?? normalized.selectedSiteIds),
    };
  }

  function normalizeStatus(source) {
    return source && typeof source === 'object' ? source : {};
  }

  function resolveConfig(options) {
    const config = options && typeof options.config === 'object' ? options.config : null;
    return config && !Array.isArray(config) ? config : {};
  }

  function readConfigObject(config, key) {
    const value = config && typeof config[key] === 'object' ? config[key] : null;
    return value && !Array.isArray(value) ? value : null;
  }

  function createRuntime(options = {}) {
    const config = resolveConfig(options);
    const escapeHtml = typeof options.escapeHtml === 'function'
      ? options.escapeHtml
      : fallbackEscapeHtml;
    const showPopup = typeof options.showPopup === 'function'
      ? options.showPopup
      : ((message) => console.log(message));
    let netBoxSyncSettingsState = sanitizeSettings(
      readConfigObject(config, 'netBoxSyncSettings') || options.initialSettings
    );
    let netBoxSyncStatusState = normalizeStatus(
      readConfigObject(config, 'netBoxSyncStatus') || options.initialStatus
    );
    let siteCatalogState = normalizeSiteCatalog(options.initialSiteCatalog);
    let siteCatalogLoading = false;
    let settingsLoaded = Boolean(Object.keys(netBoxSyncSettingsState).length);
    let loadingPromise = null;
    let statusPollTimer = null;

    function applyPageData(section) {
      netBoxSyncSettingsState = sanitizeSettings(section?.netBoxSyncSettings);
      netBoxSyncStatusState = normalizeStatus(section?.netBoxSyncStatus);
      settingsLoaded = true;
      renderSettings();
      renderStatus(netBoxSyncStatusState);
    }

    function ensureLoaded(forceReload = false) {
      if (!forceReload && settingsLoaded) {
        return Promise.resolve({
          settings: netBoxSyncSettingsState,
          status: netBoxSyncStatusState,
        });
      }
      if (loadingPromise) {
        return loadingPromise;
      }
      const fetchPageDataSection = window.SettingsRuntimeAccess?.fetchPageDataSection;
      if (typeof fetchPageDataSection !== 'function') {
        settingsLoaded = true;
        return Promise.resolve({
          settings: netBoxSyncSettingsState,
          status: netBoxSyncStatusState,
        });
      }
      loadingPromise = fetchPageDataSection('locations', forceReload ? { force: true } : {})
        .then((section) => {
          applyPageData(section);
          return {
            settings: netBoxSyncSettingsState,
            status: netBoxSyncStatusState,
          };
        })
        .finally(() => {
          loadingPromise = null;
        });
      return loadingPromise;
    }

    function syncStateFromDom() {
      const baseUrlInput = document.getElementById('netBoxSyncBaseUrl');
      const tokenInput = document.getElementById('netBoxSyncApiToken');
      const enabledInput = document.getElementById('netBoxSyncEnabled');
      const intervalInput = document.getElementById('netBoxSyncInterval');
      const clearTokenInput = document.getElementById('netBoxSyncClearToken');
      netBoxSyncSettingsState = sanitizeSettings({
        ...netBoxSyncSettingsState,
        base_url: baseUrlInput instanceof HTMLInputElement ? baseUrlInput.value : netBoxSyncSettingsState.base_url,
        api_token: tokenInput instanceof HTMLInputElement ? tokenInput.value : netBoxSyncSettingsState.api_token,
        enabled: enabledInput instanceof HTMLInputElement ? enabledInput.checked : netBoxSyncSettingsState.enabled,
        interval_minutes: intervalInput instanceof HTMLInputElement ? intervalInput.value : netBoxSyncSettingsState.interval_minutes,
        clear_api_token: clearTokenInput instanceof HTMLInputElement ? clearTokenInput.checked : netBoxSyncSettingsState.clear_api_token,
      });
      return netBoxSyncSettingsState;
    }

    function applySavedSettings(savedSettings) {
      netBoxSyncSettingsState = sanitizeSettings(savedSettings);
      settingsLoaded = true;
      renderSettings();
    }

    function serializeNetBoxSyncSettings() {
      const current = syncStateFromDom();
      return {
        base_url: current.base_url,
        api_token: current.api_token,
        clear_api_token: Boolean(current.clear_api_token),
        enabled: Boolean(current.enabled),
        interval_minutes: Number(current.interval_minutes || 60),
        full_overwrite_pending: Boolean(current.full_overwrite_pending),
        selected_site_ids: normalizeSiteIdList(current.selected_site_ids),
      };
    }

    function markNetBoxSyncSettingsSaved() {
      const hadNewToken = typeof netBoxSyncSettingsState.api_token === 'string' && netBoxSyncSettingsState.api_token.trim().length > 0;
      const clearedToken = Boolean(netBoxSyncSettingsState.clear_api_token);
      netBoxSyncSettingsState = sanitizeSettings({
        ...netBoxSyncSettingsState,
        api_token: '',
        api_token_saved: clearedToken ? false : (hadNewToken ? true : netBoxSyncSettingsState.api_token_saved),
        clear_api_token: false,
      });
      renderSettings();
    }

    function renderSettings() {
      const baseUrlInput = document.getElementById('netBoxSyncBaseUrl');
      const tokenInput = document.getElementById('netBoxSyncApiToken');
      const enabledInput = document.getElementById('netBoxSyncEnabled');
      const intervalInput = document.getElementById('netBoxSyncInterval');
      const clearTokenInput = document.getElementById('netBoxSyncClearToken');
      const tokenHint = document.getElementById('netBoxSyncTokenHint');
      const overwriteBadge = document.getElementById('netBoxSyncOverwriteBadge');
      if (baseUrlInput instanceof HTMLInputElement) {
        baseUrlInput.value = netBoxSyncSettingsState.base_url || '';
      }
      if (tokenInput instanceof HTMLInputElement) {
        tokenInput.value = netBoxSyncSettingsState.api_token || '';
      }
      if (enabledInput instanceof HTMLInputElement) {
        enabledInput.checked = Boolean(netBoxSyncSettingsState.enabled);
      }
      if (intervalInput instanceof HTMLInputElement) {
        intervalInput.value = String(netBoxSyncSettingsState.interval_minutes || 60);
        intervalInput.disabled = !Boolean(netBoxSyncSettingsState.enabled);
      }
      if (clearTokenInput instanceof HTMLInputElement) {
        clearTokenInput.checked = Boolean(netBoxSyncSettingsState.clear_api_token);
      }
      if (tokenHint instanceof HTMLElement) {
        tokenHint.textContent = netBoxSyncSettingsState.clear_api_token
          ? 'При сохранении текущий токен NetBox будет удалён из settings.json.'
          : netBoxSyncSettingsState.api_token_saved
          ? 'Токен уже сохранён. Оставьте поле пустым, если его не нужно менять.'
          : 'Секрет ещё не сохранён. Укажите API token NetBox.';
      }
      if (overwriteBadge instanceof HTMLElement) {
        overwriteBadge.classList.toggle('d-none', !netBoxSyncSettingsState.full_overwrite_pending);
      }
      renderSiteCatalog();
    }

    function resolveSiteStatusBadgeClass(status) {
      const normalized = String(status || '').trim().toLowerCase();
      if (normalized.includes('active') || normalized.includes('актив')) {
        return 'text-bg-success-subtle border border-success-subtle text-success-emphasis';
      }
      if (normalized.includes('planned') || normalized.includes('plan') || normalized.includes('заплан')) {
        return 'text-bg-info-subtle border border-info-subtle text-info-emphasis';
      }
      if (normalized.includes('offline') || normalized.includes('retired') || normalized.includes('decom') || normalized.includes('неактив')) {
        return 'text-bg-secondary-subtle border border-secondary-subtle text-secondary-emphasis';
      }
      return 'text-bg-light border text-body-secondary';
    }

    function renderSiteCatalog() {
      const catalog = document.getElementById('netBoxSyncSiteCatalog');
      const badge = document.getElementById('netBoxSyncSelectedSitesBadge');
      const state = document.getElementById('netBoxSyncSitesState');
      const selectedIds = normalizeSiteIdList(netBoxSyncSettingsState.selected_site_ids);
      if (badge instanceof HTMLElement) {
        badge.textContent = `Выбрано ${selectedIds.length} sites`;
      }
      if (state instanceof HTMLElement) {
        if (siteCatalogLoading) {
          state.textContent = 'Загружаем список sites из NetBox...';
        } else if (siteCatalogState.length) {
          state.textContent = `Загружено ${siteCatalogState.length} sites из NetBox.`;
        } else {
          state.textContent = 'Список sites ещё не загружен.';
        }
      }
      if (!(catalog instanceof HTMLElement)) {
        return;
      }
      if (siteCatalogLoading) {
        catalog.innerHTML = '<div class="small text-muted p-3">Загружаем список sites из NetBox...</div>';
        return;
      }
      if (!siteCatalogState.length) {
        catalog.innerHTML = '<div class="small text-muted p-3">Список sites ещё не загружен.</div>';
        return;
      }
      const selectedSet = new Set(selectedIds);
      catalog.innerHTML = siteCatalogState.map((site) => `
        <label class="d-flex align-items-start justify-content-between gap-3 p-3 border-bottom mb-0" data-netbox-site-row="${escapeHtml(site.id)}">
          <span class="d-flex align-items-start gap-2">
            <input
              class="form-check-input mt-1"
              type="checkbox"
              data-netbox-site-id="${escapeHtml(site.id)}"
              ${selectedSet.has(site.id) ? 'checked' : ''}
            />
            <span>
              <span class="d-block fw-semibold">${escapeHtml(site.name)}</span>
              <span class="d-block small text-muted">site id: ${escapeHtml(site.id)}</span>
            </span>
          </span>
          <span class="badge ${resolveSiteStatusBadgeClass(site.status)}">${escapeHtml(site.status || '—')}</span>
        </label>
      `).join('');
    }

    function formatTimestamp(value) {
      if (!value || value === 'after_startup_tick') {
        return value === 'after_startup_tick' ? 'после ближайшего тика scheduler' : '—';
      }
      const parsed = new Date(value);
      if (Number.isNaN(parsed.getTime())) {
        return value;
      }
      return parsed.toLocaleString('ru-RU');
    }

    function renderStatus(source) {
      netBoxSyncStatusState = normalizeStatus(source);
      const status = netBoxSyncStatusState;
      const syncState = String(status.state || 'idle').trim().toLowerCase();
      const running = Boolean(status.running || syncState === 'running');
      const progressPercent = Math.min(100, Math.max(0, Number.parseInt(String(status.progressPercent ?? status.progress_percent ?? 0), 10) || 0));
      const message = String(status.message || 'Синхронизация NetBox ещё не запускалась.').trim();
      const progressBar = document.getElementById('netBoxSyncProgressBar');
      const statusText = document.getElementById('netBoxSyncStatusText');
      const meta = document.getElementById('netBoxSyncMeta');
      const warnings = document.getElementById('netBoxSyncWarnings');
      const result = document.getElementById('netBoxSyncResult');
      const runButton = document.getElementById('netBoxSyncRunBtn');
      const overwriteBadge = document.getElementById('netBoxSyncOverwriteBadge');

      if (progressBar instanceof HTMLElement) {
        progressBar.style.width = `${progressPercent}%`;
        progressBar.textContent = `${progressPercent}%`;
        progressBar.classList.toggle('progress-bar-striped', running);
        progressBar.classList.toggle('progress-bar-animated', running);
        progressBar.classList.remove('bg-success', 'bg-danger', 'bg-warning');
        if (syncState === 'success') {
          progressBar.classList.add('bg-success');
        } else if (syncState === 'error') {
          progressBar.classList.add('bg-danger');
        } else if (syncState === 'idle') {
          progressBar.classList.add('bg-warning');
        }
      }
      if (statusText instanceof HTMLElement) {
        statusText.textContent = message;
      }
      if (meta instanceof HTMLElement) {
        const parts = [
          `Триггер: ${status.trigger ? String(status.trigger) : '—'}`,
          `Автосинхронизация: ${status.enabled ? `каждые ${status.intervalMinutes ?? status.interval_minutes ?? '—'} мин` : 'выключена'}`,
          `Старт: ${formatTimestamp(status.startedAtUtc || status.started_at_utc)}`,
          `Финиш: ${formatTimestamp(status.finishedAtUtc || status.finished_at_utc)}`,
          `Последний успешный запуск: ${formatTimestamp(status.lastSuccessAtUtc || status.last_success_at_utc)}`,
          `Следующий запуск: ${formatTimestamp(status.nextRunAtUtc || status.next_run_at_utc)}`,
        ];
        meta.innerHTML = parts.map((item) => `<div>${escapeHtml(item)}</div>`).join('');
      }
      if (warnings instanceof HTMLElement) {
        const warningList = Array.isArray(status.warnings) ? status.warnings.filter(Boolean) : [];
        if (warningList.length) {
          warnings.classList.remove('d-none');
          warnings.textContent = warningList.join('\n');
        } else {
          warnings.classList.add('d-none');
          warnings.textContent = '';
        }
      }
      if (result instanceof HTMLElement) {
        const summary = status.result && typeof status.result === 'object' ? status.result : null;
        if (summary) {
          result.classList.remove('d-none');
          result.innerHTML = `
            <div class="border rounded-3 p-3 bg-light-subtle">
              <div class="fw-semibold mb-2">Результат последнего sync</div>
              <div>Сайтов обработано: ${escapeHtml(summary.totalSites ?? summary.total_sites ?? 0)}</div>
              <div>Создано паспортов: ${escapeHtml(summary.createdPassports ?? summary.created_passports ?? 0)}</div>
              <div>Обновлено паспортов: ${escapeHtml(summary.updatedPassports ?? summary.updated_passports ?? 0)}</div>
              <div>Импортировано единиц оборудования: ${escapeHtml(summary.importedEquipmentItems ?? summary.imported_equipment_items ?? 0)}</div>
              <div>Импортировано фото: ${escapeHtml(summary.importedPhotos ?? summary.imported_photos ?? 0)}</div>
            </div>
          `;
        } else {
          result.classList.add('d-none');
          result.innerHTML = '';
        }
      }
      if (runButton instanceof HTMLButtonElement) {
        runButton.disabled = running;
        runButton.innerHTML = running
          ? '<i class="bi bi-arrow-repeat me-1"></i>Синхронизация идёт'
          : '<i class="bi bi-play-fill me-1"></i>Запустить sync сейчас';
      }
      if (overwriteBadge instanceof HTMLElement) {
        overwriteBadge.classList.toggle('d-none', !(status.fullOverwritePending ?? status.full_overwrite_pending));
      }
    }

    function updateSetting(field, value) {
      const next = { ...netBoxSyncSettingsState };
      if (field === 'enabled') {
        next.enabled = Boolean(value);
      } else if (field === 'interval_minutes') {
        const parsed = Number.parseInt(String(value || '').trim(), 10);
        next.interval_minutes = Number.isFinite(parsed) ? Math.min(10080, Math.max(5, parsed)) : 60;
      } else if (field === 'base_url') {
        next.base_url = typeof value === 'string' ? value.trim().replace(/\/+$/, '') : '';
      } else if (field === 'api_token') {
        next.api_token = typeof value === 'string' ? value : '';
        if (next.api_token.trim()) {
          next.clear_api_token = false;
        }
      } else if (field === 'clear_api_token') {
        next.clear_api_token = Boolean(value);
        if (next.clear_api_token) {
          next.api_token = '';
        }
      } else if (field === 'selected_site_ids') {
        next.selected_site_ids = normalizeSiteIdList(value);
      }
      netBoxSyncSettingsState = sanitizeSettings(next);
      renderSettings();
    }

    async function loadSiteCatalog() {
      const loadButton = document.getElementById('netBoxSyncLoadSitesBtn');
      if (loadButton instanceof HTMLButtonElement) {
        loadButton.disabled = true;
      }
      siteCatalogLoading = true;
      renderSiteCatalog();
      try {
        const response = await fetch('/api/settings/netbox-sync/sites', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            netbox_sync: serializeNetBoxSyncSettings(),
          }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось загрузить список sites из NetBox');
        }
        siteCatalogState = normalizeSiteCatalog(data.sites);
        showPopup(`Список sites из NetBox загружен: ${siteCatalogState.length}.`, 'success');
      } catch (error) {
        showPopup(`Не удалось загрузить список sites из NetBox: ${error.message}`, 'error');
      } finally {
        siteCatalogLoading = false;
        renderSiteCatalog();
        if (loadButton instanceof HTMLButtonElement) {
          loadButton.disabled = false;
        }
      }
    }

    async function loadStatus() {
      try {
        const response = await fetch('/api/settings/netbox-sync/status', { cache: 'no-store' });
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        renderStatus(data);
        return data;
      } catch (error) {
        showPopup(`Не удалось получить статус NetBox sync: ${error.message}`, 'error');
        throw error;
      }
    }

    async function saveSettings() {
      const saveButton = document.getElementById('netBoxSyncSaveBtn');
      const runButton = document.getElementById('netBoxSyncRunBtn');
      if (saveButton instanceof HTMLButtonElement) {
        saveButton.disabled = true;
      }
      if (runButton instanceof HTMLButtonElement) {
        runButton.disabled = true;
      }
      try {
        const response = await fetch('/api/settings/netbox-sync/save', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            netbox_sync: serializeNetBoxSyncSettings(),
          }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось сохранить конфигурацию NetBox');
        }
        if (data.savedSettings && typeof data.savedSettings === 'object') {
          applySavedSettings(data.savedSettings);
        } else {
          markNetBoxSyncSettingsSaved();
        }
        await ensureLoaded(true);
        showPopup('Конфигурация подключения NetBox сохранена.', 'success');
      } catch (error) {
        showPopup(`Не удалось сохранить конфигурацию NetBox: ${error.message}`, 'error');
      } finally {
        if (saveButton instanceof HTMLButtonElement) {
          saveButton.disabled = false;
        }
        if (runButton instanceof HTMLButtonElement) {
          runButton.disabled = Boolean(netBoxSyncStatusState.running || String(netBoxSyncStatusState.state || '').toLowerCase() === 'running');
        }
      }
    }

    async function runSync() {
      const runButton = document.getElementById('netBoxSyncRunBtn');
      if (runButton instanceof HTMLButtonElement) {
        runButton.disabled = true;
      }
      try {
        const response = await fetch('/api/settings/netbox-sync/run', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            netbox_sync: serializeNetBoxSyncSettings(),
          }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось запустить sync');
        }
        if (data.savedSettings && typeof data.savedSettings === 'object') {
          applySavedSettings(data.savedSettings);
        } else {
          markNetBoxSyncSettingsSaved();
        }
        await ensureLoaded(true);
        if (data.status) {
          renderStatus(data.status);
        }
        showPopup(data.started ? 'Синхронизация NetBox поставлена в очередь.' : 'Синхронизация NetBox уже выполняется.', data.started ? 'success' : 'warning');
        startStatusPolling();
      } catch (error) {
        showPopup(`Не удалось запустить NetBox sync: ${error.message}`, 'error');
      } finally {
        if (!(netBoxSyncStatusState.running || String(netBoxSyncStatusState.state || '').toLowerCase() === 'running')) {
          if (runButton instanceof HTMLButtonElement) {
            runButton.disabled = false;
          }
        }
      }
    }

    function stopStatusPolling() {
      if (statusPollTimer) {
        window.clearInterval(statusPollTimer);
        statusPollTimer = null;
      }
    }

    function startStatusPolling() {
      stopStatusPolling();
      statusPollTimer = window.setInterval(async () => {
        const status = await loadStatus().catch(() => null);
        const state = String(status?.state || '').toLowerCase();
        if (state && state !== 'running') {
          stopStatusPolling();
        }
      }, 3000);
    }

    async function handleModalShown() {
      await ensureLoaded();
      renderSettings();
      renderStatus(netBoxSyncStatusState);
      await loadStatus().catch(() => null);
      startStatusPolling();
    }

    function attachEvents() {
      const modal = document.getElementById('itConnectionsModal');
      if (modal instanceof HTMLElement && !modal.dataset.netBoxSyncBound) {
        modal.dataset.netBoxSyncBound = 'true';
        modal.addEventListener('shown.bs.modal', () => {
          handleModalShown();
        });
        modal.addEventListener('hidden.bs.modal', () => {
          stopStatusPolling();
        });
      }

      const baseUrlInput = document.getElementById('netBoxSyncBaseUrl');
      if (baseUrlInput instanceof HTMLInputElement && !baseUrlInput.dataset.netBoxSyncBound) {
        baseUrlInput.dataset.netBoxSyncBound = 'true';
        baseUrlInput.addEventListener('input', (event) => updateSetting('base_url', event.target.value));
      }
      const tokenInput = document.getElementById('netBoxSyncApiToken');
      if (tokenInput instanceof HTMLInputElement && !tokenInput.dataset.netBoxSyncBound) {
        tokenInput.dataset.netBoxSyncBound = 'true';
        tokenInput.addEventListener('input', (event) => updateSetting('api_token', event.target.value));
      }
      const enabledInput = document.getElementById('netBoxSyncEnabled');
      if (enabledInput instanceof HTMLInputElement && !enabledInput.dataset.netBoxSyncBound) {
        enabledInput.dataset.netBoxSyncBound = 'true';
        enabledInput.addEventListener('change', (event) => updateSetting('enabled', event.target.checked));
      }
      const clearTokenInput = document.getElementById('netBoxSyncClearToken');
      if (clearTokenInput instanceof HTMLInputElement && !clearTokenInput.dataset.netBoxSyncBound) {
        clearTokenInput.dataset.netBoxSyncBound = 'true';
        clearTokenInput.addEventListener('change', (event) => updateSetting('clear_api_token', event.target.checked));
      }
      const intervalInput = document.getElementById('netBoxSyncInterval');
      if (intervalInput instanceof HTMLInputElement && !intervalInput.dataset.netBoxSyncBound) {
        intervalInput.dataset.netBoxSyncBound = 'true';
        intervalInput.addEventListener('input', (event) => updateSetting('interval_minutes', event.target.value));
      }
      const saveButton = document.getElementById('netBoxSyncSaveBtn');
      if (saveButton instanceof HTMLButtonElement && !saveButton.dataset.netBoxSyncBound) {
        saveButton.dataset.netBoxSyncBound = 'true';
        saveButton.addEventListener('click', () => saveSettings());
      }
      const loadSitesButton = document.getElementById('netBoxSyncLoadSitesBtn');
      if (loadSitesButton instanceof HTMLButtonElement && !loadSitesButton.dataset.netBoxSyncBound) {
        loadSitesButton.dataset.netBoxSyncBound = 'true';
        loadSitesButton.addEventListener('click', () => loadSiteCatalog());
      }
      const selectAllSitesButton = document.getElementById('netBoxSyncSelectAllSitesBtn');
      if (selectAllSitesButton instanceof HTMLButtonElement && !selectAllSitesButton.dataset.netBoxSyncBound) {
        selectAllSitesButton.dataset.netBoxSyncBound = 'true';
        selectAllSitesButton.addEventListener('click', () => {
          updateSetting('selected_site_ids', siteCatalogState.map((site) => site.id));
        });
      }
      const clearSitesButton = document.getElementById('netBoxSyncClearSitesBtn');
      if (clearSitesButton instanceof HTMLButtonElement && !clearSitesButton.dataset.netBoxSyncBound) {
        clearSitesButton.dataset.netBoxSyncBound = 'true';
        clearSitesButton.addEventListener('click', () => {
          updateSetting('selected_site_ids', []);
        });
      }
      const runButton = document.getElementById('netBoxSyncRunBtn');
      if (runButton instanceof HTMLButtonElement && !runButton.dataset.netBoxSyncBound) {
        runButton.dataset.netBoxSyncBound = 'true';
        runButton.addEventListener('click', () => runSync());
      }
      const siteCatalog = document.getElementById('netBoxSyncSiteCatalog');
      if (siteCatalog instanceof HTMLElement && !siteCatalog.dataset.netBoxSyncBound) {
        siteCatalog.dataset.netBoxSyncBound = 'true';
        siteCatalog.addEventListener('change', (event) => {
          const target = event.target;
          if (!(target instanceof HTMLInputElement) || target.type !== 'checkbox') {
            return;
          }
          const siteId = String(target.dataset.netboxSiteId || '').trim();
          if (!siteId) {
            return;
          }
          const next = new Set(normalizeSiteIdList(netBoxSyncSettingsState.selected_site_ids));
          if (target.checked) {
            next.add(siteId);
          } else {
            next.delete(siteId);
          }
          updateSetting('selected_site_ids', Array.from(next));
        });
      }
    }

    attachEvents();
    renderSettings();
    renderStatus(netBoxSyncStatusState);

    return {
      ensureLoaded,
      loadStatus,
      renderStatus,
      renderSettings,
      syncStateFromDom,
      applySavedSettings,
      serializeNetBoxSyncSettings,
      markNetBoxSyncSettingsSaved,
      saveSettings,
      loadSiteCatalog,
    };
  }

  window.SettingsNetBoxSyncRuntime = Object.freeze({
    mount(options = {}) {
      if (window.__settingsNetBoxSyncRuntime) {
        return window.__settingsNetBoxSyncRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsNetBoxSyncRuntime = runtime;
      return runtime;
    },
  });
}());

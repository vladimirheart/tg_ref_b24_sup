(function () {
  if (window.SettingsLocationsIikoRuntime) {
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

  function createLocationsIikoServerSourceId() {
    return `iiko-source-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
  }

  function sanitizeLocationsIikoServerSources(source) {
    if (!Array.isArray(source)) {
      return [];
    }
    return source
      .map((item) => {
        if (!item || typeof item !== 'object') {
          return null;
        }
        const id = typeof item.id === 'string' && item.id.trim()
          ? item.id.trim()
          : createLocationsIikoServerSourceId();
        const name = typeof item.name === 'string' ? item.name.trim() : '';
        const baseUrlRaw = typeof item.base_url === 'string'
          ? item.base_url
          : typeof item.baseUrl === 'string'
            ? item.baseUrl
            : '';
        const baseUrl = baseUrlRaw.trim().replace(/\/+$/, '');
        const apiLogin = typeof item.api_login === 'string'
          ? item.api_login.trim()
          : typeof item.apiLogin === 'string'
            ? item.apiLogin.trim()
            : '';
        const apiSecret = typeof item.api_secret === 'string'
          ? item.api_secret
          : typeof item.apiSecret === 'string'
            ? item.apiSecret
            : '';
        const apiSecretSaved = Boolean(item.api_secret_saved || item.apiSecretSaved || apiSecret.trim());
        const enabled = typeof item.enabled === 'boolean' ? item.enabled : item.enabled !== false;
        if (!name && !baseUrl && !apiLogin && !apiSecret && !apiSecretSaved) {
          return null;
        }
        return {
          id,
          name,
          base_url: baseUrl,
          api_login: apiLogin,
          api_secret: apiSecret,
          api_secret_saved: apiSecretSaved,
          enabled,
        };
      })
      .filter(Boolean);
  }

  function sanitizeLocationsIikoSyncSettings(source) {
    const normalized = source && typeof source === 'object' ? source : {};
    const enabled = typeof normalized.enabled === 'boolean' ? normalized.enabled : true;
    const rawInterval = Number.parseInt(String(normalized.interval_minutes ?? normalized.intervalMinutes ?? 5), 10);
    const intervalMinutes = Number.isFinite(rawInterval) ? Math.min(1440, Math.max(1, rawInterval)) : 5;
    return {
      enabled,
      interval_minutes: intervalMinutes,
    };
  }

  function resolveConfig(options) {
    const config = options && typeof options.config === 'object' ? options.config : null;
    return config && !Array.isArray(config) ? config : {};
  }

  function hasInitialLocationsSettings(options, initialServerSources, initialSyncSettings) {
    if (typeof options?.hasInitialData === 'boolean') {
      return options.hasInitialData;
    }
    if (Array.isArray(initialServerSources) && initialServerSources.length > 0) {
      return true;
    }
    return Boolean(
      initialSyncSettings
      && typeof initialSyncSettings === 'object'
      && !Array.isArray(initialSyncSettings)
      && Object.keys(initialSyncSettings).length > 0
    );
  }

  function readConfigObject(config, key) {
    const value = config && typeof config[key] === 'object' ? config[key] : null;
    return value && !Array.isArray(value) ? value : null;
  }

  function readConfigArray(config, key) {
    return Array.isArray(config && config[key]) ? config[key] : null;
  }

  function createRuntime(options = {}) {
    const config = resolveConfig(options);
    const initialServerSources = readConfigArray(config, 'iikoServerSources') || options.initialServerSources;
    const initialSyncSettings = readConfigObject(config, 'iikoSyncSettings') || options.initialSyncSettings;
    const escapeHtml = typeof options.escapeHtml === 'function'
      ? options.escapeHtml
      : fallbackEscapeHtml;
    const notify = typeof options.showPopup === 'function'
      ? options.showPopup
      : (message) => console.log(message);
    let locationsIikoServerSourcesState = sanitizeLocationsIikoServerSources(initialServerSources);
    let locationsIikoSyncSettingsState = sanitizeLocationsIikoSyncSettings(initialSyncSettings);
    let locationsSyncStatusPollTimer = null;
    let locationsSettingsLoaded = hasInitialLocationsSettings(
      options,
      initialServerSources,
      initialSyncSettings,
    );
    let locationsSettingsLoadingPromise = null;

    function applyPageData(section) {
      locationsIikoServerSourcesState = sanitizeLocationsIikoServerSources(section?.iikoServerSources);
      locationsIikoSyncSettingsState = sanitizeLocationsIikoSyncSettings(section?.iikoSyncSettings);
      locationsSettingsLoaded = true;
    }

    function ensureLocationsSettingsLoaded() {
      if (locationsSettingsLoaded) {
        return Promise.resolve({
          iikoServerSources: locationsIikoServerSourcesState,
          iikoSyncSettings: locationsIikoSyncSettingsState,
        });
      }
      if (locationsSettingsLoadingPromise) {
        return locationsSettingsLoadingPromise;
      }
      const fetchPageDataSection = window.SettingsRuntimeAccess?.fetchPageDataSection;
      if (typeof fetchPageDataSection !== 'function') {
        locationsSettingsLoaded = true;
        return Promise.resolve({
          iikoServerSources: locationsIikoServerSourcesState,
          iikoSyncSettings: locationsIikoSyncSettingsState,
        });
      }
      locationsSettingsLoadingPromise = fetchPageDataSection('locations')
        .then((section) => {
          applyPageData(section);
          return {
            iikoServerSources: locationsIikoServerSourcesState,
            iikoSyncSettings: locationsIikoSyncSettingsState,
          };
        })
        .finally(() => {
          locationsSettingsLoadingPromise = null;
        });
      return locationsSettingsLoadingPromise;
    }

    function serializeLocationsIikoServerSources() {
      return sanitizeLocationsIikoServerSources(locationsIikoServerSourcesState).map((source) => ({
        id: source.id,
        name: source.name,
        base_url: source.base_url,
        api_login: source.api_login,
        api_secret: source.api_secret,
        enabled: Boolean(source.enabled),
      }));
    }

    function serializeLocationsIikoSyncSettings() {
      return sanitizeLocationsIikoSyncSettings(locationsIikoSyncSettingsState);
    }

    async function renderLocationsIikoServerSourcesEditor() {
      await ensureLocationsSettingsLoaded();
      const container = document.getElementById('locationsIikoServerSourcesEditor');
      if (!(container instanceof HTMLElement)) {
        return;
      }
      if (!locationsIikoServerSourcesState.length) {
        container.innerHTML = `
          <div class="border rounded-3 p-3 bg-light small text-muted">
            Источники ещё не добавлены. Укажите хотя бы один <code>iikoServer</code>-адрес, если хотите подтягивать департаменты автоматически.
          </div>
        `;
        return;
      }
      container.innerHTML = locationsIikoServerSourcesState.map((source, index) => {
        const enabledBadge = source.enabled
          ? '<span class="badge text-bg-success-subtle border border-success-subtle text-success-emphasis">Активен</span>'
          : '<span class="badge text-bg-secondary-subtle border">Выключен</span>';
        const secretHint = source.api_secret_saved
          ? 'SHA-1 пароль уже сохранён. Оставьте поле пустым, чтобы не менять его.'
          : 'Укажите SHA-1 пароль пользователя iikoServer. Без него источник не будет участвовать в синхронизации.';
        return `
          <div class="card mb-3">
            <div class="card-body">
              <div class="d-flex flex-wrap justify-content-between align-items-start gap-2 mb-3">
                <div class="d-flex align-items-center gap-2">
                  <div class="fw-semibold">Источник ${index + 1}</div>
                  ${enabledBadge}
                </div>
                <button class="btn btn-sm btn-outline-danger" type="button" data-locations-source-remove data-locations-source-index="${index}">
                  <i class="bi bi-trash me-1"></i>Удалить
                </button>
              </div>
              <div class="row g-3">
                <div class="col-md-4">
                  <label class="form-label">Название чейна</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="Например, СушиВёсла"
                    value="${escapeHtml(source.name || '')}"
                    data-locations-source-field="name"
                    data-locations-source-index="${index}"
                  >
                </div>
                <div class="col-md-5">
                  <label class="form-label">iikoServer URL</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="https://host:port"
                    value="${escapeHtml(source.base_url || '')}"
                    data-locations-source-field="base_url"
                    data-locations-source-index="${index}"
                  >
                </div>
                <div class="col-md-3">
                  <label class="form-label">Логин iikoServer</label>
                  <input
                    type="text"
                    class="form-control"
                    placeholder="login"
                    value="${escapeHtml(source.api_login || '')}"
                    data-locations-source-field="api_login"
                    data-locations-source-index="${index}"
                  >
                </div>
                <div class="col-md-8">
                  <label class="form-label">SHA-1 пароль</label>
                  <input
                    type="password"
                    class="form-control"
                    placeholder="${source.api_secret_saved ? 'Пароль сохранён, введите новый SHA-1 только для замены' : '40-символьный SHA-1 hex'}"
                    value=""
                    data-locations-source-field="api_secret"
                    data-locations-source-index="${index}"
                  >
                  <div class="form-text">${escapeHtml(secretHint)} Формат: 40 символов <code>0-9</code> / <code>a-f</code>.</div>
                </div>
                <div class="col-md-4 d-flex align-items-end">
                  <div class="form-check form-switch mb-2">
                    <input
                      class="form-check-input"
                      type="checkbox"
                      id="location-iiko-source-enabled-${escapeHtml(source.id)}"
                      ${source.enabled ? 'checked' : ''}
                      data-locations-source-enabled
                      data-locations-source-index="${index}"
                    >
                    <label class="form-check-label" for="location-iiko-source-enabled-${escapeHtml(source.id)}">Использовать в live-синхронизации</label>
                  </div>
                </div>
              </div>
            </div>
          </div>
        `;
      }).join('');
    }

    async function renderLocationsIikoSyncSettings() {
      await ensureLocationsSettingsLoaded();
      const enabledInput = document.getElementById('locationsIikoSyncEnabled');
      const intervalInput = document.getElementById('locationsIikoSyncInterval');
      if (enabledInput instanceof HTMLInputElement) {
        enabledInput.checked = Boolean(locationsIikoSyncSettingsState.enabled);
      }
      if (intervalInput instanceof HTMLInputElement) {
        intervalInput.value = String(locationsIikoSyncSettingsState.interval_minutes || 5);
        intervalInput.disabled = !Boolean(locationsIikoSyncSettingsState.enabled);
      }
    }

    function updateLocationsIikoSyncSetting(field, value) {
      const next = { ...locationsIikoSyncSettingsState };
      if (field === 'enabled') {
        next.enabled = Boolean(value);
      } else if (field === 'interval_minutes') {
        const parsed = Number.parseInt(String(value || '').trim(), 10);
        next.interval_minutes = Number.isFinite(parsed) ? Math.min(1440, Math.max(1, parsed)) : 5;
      }
      locationsIikoSyncSettingsState = sanitizeLocationsIikoSyncSettings(next);
      renderLocationsIikoSyncSettings();
    }

    function formatLocationsSyncTimestamp(value) {
      if (!value || value === 'after_startup_tick') {
        return value === 'after_startup_tick' ? 'после ближайшего тика scheduler' : '—';
      }
      const parsed = new Date(value);
      if (Number.isNaN(parsed.getTime())) {
        return value;
      }
      return parsed.toLocaleString('ru-RU');
    }

    function renderLocationsSyncStatus(status) {
      const normalized = status && typeof status === 'object' ? status : {};
      const syncState = String(normalized.state || 'idle').trim().toLowerCase();
      const progressPercent = Math.min(100, Math.max(0, Number.parseInt(String(normalized.progressPercent ?? normalized.progress_percent ?? 0), 10) || 0));
      const message = String(normalized.message || 'Синхронизация ещё не запускалась.').trim();
      const running = Boolean(normalized.running || syncState === 'running');
      const warnings = Array.isArray(normalized.warnings) ? normalized.warnings.filter(Boolean) : [];
      const result = normalized.result && typeof normalized.result === 'object' ? normalized.result : null;

      const progressBar = document.getElementById('locationsSyncProgressBar');
      const statusText = document.getElementById('locationsSyncStatusText');
      const metaNode = document.getElementById('locationsSyncMeta');
      const resultNode = document.getElementById('locationsSyncResult');
      const warningsNode = document.getElementById('locationsSyncWarnings');
      const runButton = document.getElementById('locationsSyncRunBtn');

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
        } else if (syncState === 'skipped' || syncState === 'disabled') {
          progressBar.classList.add('bg-warning');
        }
      }
      if (statusText instanceof HTMLElement) {
        statusText.textContent = message;
      }
      if (metaNode instanceof HTMLElement) {
        const parts = [];
        parts.push(`Триггер: ${normalized.trigger ? String(normalized.trigger) : '—'}`);
        parts.push(`Автосинхронизация: ${normalized.intervalMinutes ? `каждые ${normalized.intervalMinutes} мин` : '—'}`);
        parts.push(`Старт: ${formatLocationsSyncTimestamp(normalized.startedAtUtc || normalized.started_at_utc)}`);
        parts.push(`Финиш: ${formatLocationsSyncTimestamp(normalized.finishedAtUtc || normalized.finished_at_utc)}`);
        parts.push(`Следующий запуск: ${formatLocationsSyncTimestamp(normalized.nextRunAtUtc || normalized.next_run_at_utc)}`);
        parts.push(`Изменения: ${normalized.changed ? 'да' : 'нет'}`);
        metaNode.innerHTML = parts.map((item) => `<div>${escapeHtml(item)}</div>`).join('');
      }
      if (resultNode instanceof HTMLElement) {
        if (result && syncState === 'success') {
          const summary = [
            `Всего локаций: ${Number(result.totalLocations ?? result.total_locations ?? 0) || 0}`,
            `Активных: ${Number(result.activeLocations ?? result.active_locations ?? 0) || 0}`,
            `Закрытых: ${Number(result.closedLocations ?? result.closed_locations ?? 0) || 0}`,
            `Добавлено: ${Number(result.addedLocations ?? result.added_locations ?? 0) || 0}`,
            `Закрыто этой sync: ${Number(result.closedBySync ?? result.closed_by_sync ?? 0) || 0}`,
            `Переоткрыто: ${Number(result.reopenedLocations ?? result.reopened_locations ?? 0) || 0}`,
          ];
          const renderExamples = (title, values) => {
            const safeValues = Array.isArray(values) ? values.filter(Boolean).slice(0, 8) : [];
            if (!safeValues.length) {
              return '';
            }
            return `
              <div class="mt-2">
                <div class="fw-semibold">${escapeHtml(title)}</div>
                ${safeValues.map((item) => `<div class="text-muted">${escapeHtml(item)}</div>`).join('')}
              </div>
            `;
          };
          resultNode.classList.remove('d-none');
          resultNode.innerHTML = `
            <div class="border rounded-3 p-3 bg-light-subtle">
              <div class="fw-semibold mb-2">Результат синхронизации</div>
              ${summary.map((item) => `<div>${escapeHtml(item)}</div>`).join('')}
              ${renderExamples('Добавлены', result.addedExamples ?? result.added_examples)}
              ${renderExamples('Закрыты этой sync', result.closedExamples ?? result.closed_examples)}
              ${renderExamples('Переоткрыты', result.reopenedExamples ?? result.reopened_examples)}
            </div>
          `;
        } else {
          resultNode.classList.add('d-none');
          resultNode.innerHTML = '';
        }
      }
      if (warningsNode instanceof HTMLElement) {
        if (warnings.length) {
          warningsNode.classList.remove('d-none');
          warningsNode.textContent = warnings.join('\n');
        } else {
          warningsNode.classList.add('d-none');
          warningsNode.textContent = '';
        }
      }
      if (runButton instanceof HTMLButtonElement) {
        runButton.disabled = running;
        runButton.innerHTML = running
          ? '<i class="bi bi-arrow-repeat me-1"></i>Синхронизация идёт'
          : '<i class="bi bi-play-fill me-1"></i>Синхронизировать сейчас';
      }
    }

    async function loadLocationsSyncStatus() {
      try {
        const response = await fetch('/api/settings/locations-sync/status');
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const status = await response.json();
        renderLocationsSyncStatus(status);
        const running = Boolean(status && (status.running || String(status.state || '').toLowerCase() === 'running'));
        if (!running) {
          stopLocationsSyncStatusPolling();
        }
        return status;
      } catch (error) {
        renderLocationsSyncStatus({
          state: 'error',
          progressPercent: 100,
          message: `Не удалось получить статус синхронизации: ${error.message}`,
          running: false,
          warnings: [],
        });
        stopLocationsSyncStatusPolling();
        return null;
      }
    }

    function startLocationsSyncStatusPolling() {
      if (locationsSyncStatusPollTimer) {
        return;
      }
      locationsSyncStatusPollTimer = window.setInterval(() => {
        loadLocationsSyncStatus();
      }, 1500);
    }

    function stopLocationsSyncStatusPolling() {
      if (!locationsSyncStatusPollTimer) {
        return;
      }
      window.clearInterval(locationsSyncStatusPollTimer);
      locationsSyncStatusPollTimer = null;
    }

    async function prepareLocationsSettingsModal() {
      await ensureLocationsSettingsLoaded();
      await renderLocationsIikoServerSourcesEditor();
      await renderLocationsIikoSyncSettings();
      loadLocationsSyncStatus().then((status) => {
        const running = Boolean(status && (status.running || String(status.state || '').toLowerCase() === 'running'));
        if (running) {
          startLocationsSyncStatusPolling();
        }
      });
    }

    function resetLocationsSettingsModal() {
      stopLocationsSyncStatusPolling();
    }

    function markLocationsIikoServerSourcesSaved() {
      locationsIikoServerSourcesState = sanitizeLocationsIikoServerSources(locationsIikoServerSourcesState).map((source) => ({
        ...source,
        api_secret: '',
        api_secret_saved: Boolean(source.api_secret_saved || (source.api_secret || '').trim()),
      }));
      renderLocationsIikoServerSourcesEditor();
    }

    async function saveLocationsSyncSettingsOnly() {
      await ensureLocationsSettingsLoaded();
      const response = await fetch('/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          locations_iiko_server_sources: serializeLocationsIikoServerSources(),
          locations_iiko_sync: serializeLocationsIikoSyncSettings(),
        }),
      });
      const data = await response.json();
      if (!data.success) {
        throw new Error(data.error || 'неизвестная ошибка');
      }
      markLocationsIikoServerSourcesSaved();
      return data;
    }

    async function runLocationsIikoSyncNow() {
      try {
        await saveLocationsSyncSettingsOnly();
        const response = await fetch('/api/settings/locations-sync/run', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        const data = await response.json();
        if (!data.success) {
          throw new Error(data.error || 'неизвестная ошибка');
        }
        if (data.status) {
          renderLocationsSyncStatus(data.status);
        }
        if (!data.started) {
          notify('Синхронизация уже выполняется.');
        }
        startLocationsSyncStatusPolling();
        await loadLocationsSyncStatus();
      } catch (error) {
        notify('❌ Не удалось запустить синхронизацию: ' + error.message);
      }
    }

    function addLocationsIikoServerSource() {
      locationsIikoServerSourcesState = locationsIikoServerSourcesState.concat({
        id: createLocationsIikoServerSourceId(),
        name: '',
        base_url: '',
        api_login: '',
        api_secret: '',
        api_secret_saved: false,
        enabled: true,
      });
      renderLocationsIikoServerSourcesEditor();
    }

    function updateLocationsIikoServerSource(index, field, value) {
      if (!Array.isArray(locationsIikoServerSourcesState) || index < 0 || index >= locationsIikoServerSourcesState.length) {
        return;
      }
      const source = { ...locationsIikoServerSourcesState[index] };
      if (field === 'enabled') {
        source.enabled = Boolean(value);
      } else if (field === 'base_url') {
        source.base_url = String(value || '').trim().replace(/\/+$/, '');
      } else if (field === 'api_secret') {
        source.api_secret = String(value || '');
        if (source.api_secret.trim()) {
          source.api_secret_saved = true;
        }
      } else {
        source[field] = String(value || '').trim();
      }
      locationsIikoServerSourcesState = locationsIikoServerSourcesState.map((item, itemIndex) =>
        itemIndex === index ? source : item,
      );
      if (field === 'enabled') {
        renderLocationsIikoServerSourcesEditor();
      }
    }

    function removeLocationsIikoServerSource(index) {
      locationsIikoServerSourcesState = locationsIikoServerSourcesState.filter((_, itemIndex) => itemIndex !== index);
      renderLocationsIikoServerSourcesEditor();
    }

    return {
      serializeLocationsIikoServerSources,
      serializeLocationsIikoSyncSettings,
      renderLocationsIikoServerSourcesEditor,
      renderLocationsIikoSyncSettings,
      updateLocationsIikoSyncSetting,
      loadLocationsSyncStatus,
      prepareLocationsSettingsModal,
      resetLocationsSettingsModal,
      runLocationsIikoSyncNow,
      addLocationsIikoServerSource,
      markLocationsIikoServerSourcesSaved,
      updateLocationsIikoServerSource,
      removeLocationsIikoServerSource,
    };
  }

  function mount(options = {}) {
    if (window.__settingsLocationsIikoRuntime) {
      return window.__settingsLocationsIikoRuntime;
    }

    const runtime = createRuntime(options);
    window.SettingsPageCallbackRegistry?.registerMany({
      renderLocationsIikoServerSourcesEditor: runtime.renderLocationsIikoServerSourcesEditor,
      renderLocationsIikoSyncSettings: runtime.renderLocationsIikoSyncSettings,
      updateLocationsIikoSyncSetting: runtime.updateLocationsIikoSyncSetting,
      loadLocationsSyncStatus: runtime.loadLocationsSyncStatus,
      prepareLocationsSettingsModal: runtime.prepareLocationsSettingsModal,
      resetLocationsSettingsModal: runtime.resetLocationsSettingsModal,
      runLocationsIikoSyncNow: runtime.runLocationsIikoSyncNow,
      addLocationsIikoServerSource: runtime.addLocationsIikoServerSource,
      markLocationsIikoServerSourcesSaved: runtime.markLocationsIikoServerSourcesSaved,
      updateLocationsIikoServerSource: runtime.updateLocationsIikoServerSource,
      removeLocationsIikoServerSource: runtime.removeLocationsIikoServerSource,
    });
    window.__settingsLocationsIikoRuntime = runtime;
    return runtime;
  }

  window.SettingsLocationsIikoRuntime = Object.freeze({
    mount,
    sanitizeLocationsIikoServerSources,
    sanitizeLocationsIikoSyncSettings,
    renderLocationsIikoServerSourcesEditor(...args) {
      return window.__settingsLocationsIikoRuntime?.renderLocationsIikoServerSourcesEditor?.(...args);
    },
    renderLocationsIikoSyncSettings(...args) {
      return window.__settingsLocationsIikoRuntime?.renderLocationsIikoSyncSettings?.(...args);
    },
    loadLocationsSyncStatus(...args) {
      return window.__settingsLocationsIikoRuntime?.loadLocationsSyncStatus?.(...args);
    },
  });
}());

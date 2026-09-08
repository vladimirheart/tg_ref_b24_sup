(function () {
  const root = typeof window !== 'undefined' ? window : null;
  if (!root) {
    return;
  }

  const storage = (() => {
    try {
      return root.localStorage || null;
    } catch (_error) {
      return null;
    }
  })();
  const bootstrapPrefs = root.__IGUANA_UI_PREFS_BOOTSTRAP__ && typeof root.__IGUANA_UI_PREFS_BOOTSTRAP__ === 'object'
    ? root.__IGUANA_UI_PREFS_BOOTSTRAP__
    : {};
  const syncMeta = root.__IGUANA_UI_PREFS_META__ && typeof root.__IGUANA_UI_PREFS_META__ === 'object'
    ? root.__IGUANA_UI_PREFS_META__
    : {};
  const syncEnabled = syncMeta.syncEnabled === true;
  const syncEndpoint = typeof syncMeta.endpoint === 'string' && syncMeta.endpoint.trim()
    ? syncMeta.endpoint.trim()
    : '/profile/ui-preferences';
  let syncTimer = null;

  function normalizeTheme(value) {
    return value === 'dark' || value === 'light' || value === 'auto' ? value : 'light';
  }

  function normalizePalette(value) {
    return value === 'neo' || value === 'catppuccin' || value === 'amber-minimal' ? value : 'neo';
  }

  function normalizeDensity(value) {
    return value === 'compact' ? 'compact' : 'comfortable';
  }

  function normalizePinned(value) {
    if (value === true || value === '1' || value === 1 || value === 'true') {
      return '1';
    }
    return '0';
  }

  function normalizeNavOrder(value) {
    if (Array.isArray(value)) {
      return JSON.stringify(value.filter((item) => typeof item === 'string' && item.trim()));
    }
    if (typeof value !== 'string' || !value.trim()) {
      return null;
    }
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed)
        ? JSON.stringify(parsed.filter((item) => typeof item === 'string' && item.trim()))
        : null;
    } catch (_error) {
      return null;
    }
  }

  function normalizeDashboardPanelLayout(value) {
    const source = Array.isArray(value)
      ? value
      : (typeof value === 'string' && value.trim()
        ? (() => {
            try {
              const parsed = JSON.parse(value);
              return Array.isArray(parsed) ? parsed : [];
            } catch (_error) {
              return [];
            }
          })()
        : []);
    const normalized = [];
    source.forEach((entry) => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const itemId = typeof entry.itemId === 'string' ? entry.itemId.trim().toLowerCase() : '';
      if (!itemId) {
        return;
      }
      const rawOrder = Number.parseInt(entry.order, 10);
      normalized.push({
        itemId,
        order: Number.isFinite(rawOrder) && rawOrder >= 0 ? rawOrder : 0,
        pinned: entry.pinned === true || entry.pinned === 'true' || entry.pinned === 1 || entry.pinned === '1',
        hidden: entry.hidden === true || entry.hidden === 'true' || entry.hidden === 1 || entry.hidden === '1',
      });
    });
    return JSON.stringify(normalized.slice(0, 64));
  }

  function normalizePageFontScales(value) {
    let source = value;
    if (typeof source === 'string') {
      try { source = JSON.parse(source); }
      catch (_error) { source = {}; }
    }
    if (!source || typeof source !== 'object' || Array.isArray(source)) source = {};
    const result = {};
    Object.entries(source).slice(0, 64).forEach(([rawKey, rawScale]) => {
      const key = String(rawKey || '').trim();
      const scale = Number.parseInt(rawScale, 10);
      if (!key || key.length > 160) return;
      if (![90, 100, 110, 120, 130].includes(scale)) return;
      result[key] = scale;
    });
    return JSON.stringify(result);
  }

  const REGISTRY = Object.freeze({
    theme: Object.freeze({
      storageKey: 'iguana:theme',
      fallback: 'light',
      normalize: normalizeTheme,
    }),
    themePalette: Object.freeze({
      storageKey: 'iguana:theme-palette',
      fallback: 'neo',
      normalize: normalizePalette,
    }),
    sidebarPinned: Object.freeze({
      storageKey: 'sidebarPinned',
      fallback: '0',
      normalize: normalizePinned,
    }),
    uiDensityMode: Object.freeze({
      storageKey: 'uiDensityMode',
      fallback: 'comfortable',
      normalize: normalizeDensity,
    }),
    sidebarNavOrder: Object.freeze({
      storageKey: 'sidebarNavOrder',
      fallback: null,
      normalize: normalizeNavOrder,
      parse(value) {
        if (typeof value !== 'string' || !value.trim()) return null;
        try {
          const parsed = JSON.parse(value);
          return Array.isArray(parsed) ? parsed : null;
        } catch (_error) {
          return null;
        }
      },
    }),
    dashboardPanelLayout: Object.freeze({
      storageKey: 'dashboard-panel-layout-v1',
      fallback: null,
      normalize: normalizeDashboardPanelLayout,
      parse(value) {
        if (typeof value !== 'string' || !value.trim()) return null;
        try {
          const parsed = JSON.parse(value);
          return Array.isArray(parsed) ? parsed : null;
        } catch (_error) {
          return null;
        }
      },
    }),
    pageFontScales: Object.freeze({
      storageKey: 'iguana:page-font-scales-v1',
      fallback: '{}',
      normalize: normalizePageFontScales,
      parse(value) {
        if (value && typeof value === 'object' && !Array.isArray(value)) return value;
        if (typeof value !== 'string' || !value.trim()) return {};
        try {
          const parsed = JSON.parse(value);
          return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
        } catch (_error) {
          return {};
        }
      },
    }),
  });

  function getConfig(name) {
    return REGISTRY[name] || null;
  }

  function dispatchPreferenceChange(name, value, source) {
    document.dispatchEvent(new CustomEvent('ui-preference:change', {
      detail: {
        name,
        storageKey: REGISTRY[name]?.storageKey || null,
        value,
        source: source || 'runtime',
      },
    }));
  }

  function snapshot() {
    const result = {};
    Object.keys(REGISTRY).forEach((name) => {
      const value = get(name);
      if (value != null && value !== '') {
        result[name] = value;
      }
    });
    return result;
  }

  async function flushRemoteSync() {
    syncTimer = null;
    if (!syncEnabled || !syncEndpoint) {
      return;
    }
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken) {
      headers['X-CSRF-TOKEN'] = csrfToken;
    }
    try {
      await fetch(syncEndpoint, {
        method: 'PUT',
        credentials: 'same-origin',
        headers,
        body: JSON.stringify(snapshot()),
      });
    } catch (_error) {
      // ignore preference sync failures; local runtime remains the fallback
    }
  }

  function scheduleRemoteSync(source) {
    if (!syncEnabled) {
      return;
    }
    const suppressedSource = source === 'bootstrap' || source === 'storage' || source === 'server';
    if (suppressedSource) {
      return;
    }
    if (syncTimer) {
      clearTimeout(syncTimer);
    }
    syncTimer = root.setTimeout(flushRemoteSync, 300);
  }

  function readRawByStorageKey(storageKey) {
    if (!storage || !storageKey) {
      return null;
    }
    try {
      return storage.getItem(storageKey);
    } catch (_error) {
      return null;
    }
  }

  function get(name) {
    const config = getConfig(name);
    if (!config) return null;
    const raw = readRawByStorageKey(config.storageKey);
    if (raw == null || raw === '') {
      return config.parse ? config.parse(config.fallback) : config.fallback;
    }
    const normalized = config.normalize ? config.normalize(raw) : raw;
    return config.parse ? config.parse(normalized) : normalized;
  }

  function set(name, value, source = 'runtime') {
    const config = getConfig(name);
    if (!config || !storage) return null;
    const normalized = config.normalize ? config.normalize(value) : value;
    try {
      if (normalized == null || normalized === '') {
        storage.removeItem(config.storageKey);
      } else {
        storage.setItem(config.storageKey, normalized);
      }
    } catch (_error) {
      return null;
    }
    const parsed = config.parse ? config.parse(normalized) : normalized;
    dispatchPreferenceChange(name, parsed, source);
    scheduleRemoteSync(source);
    return parsed;
  }

  function remove(name, source = 'runtime') {
    const config = getConfig(name);
    if (!config || !storage) return;
    try {
      storage.removeItem(config.storageKey);
    } catch (_error) {
      return;
    }
    const fallbackValue = config.parse ? config.parse(config.fallback) : config.fallback;
    dispatchPreferenceChange(name, fallbackValue, source);
    scheduleRemoteSync(source);
  }

  function getStorageKey(name) {
    return REGISTRY[name]?.storageKey || null;
  }

  const PAGE_FONT_SCALE_STEPS = Object.freeze([90, 100, 110, 120, 130]);
  let baseRootFontPx = null;

  function resolveBaseRootFontPx() {
    if (Number.isFinite(baseRootFontPx) && baseRootFontPx > 0) {
      return baseRootFontPx;
    }

    const rootElement = document.documentElement;
    const previousInline = rootElement.style.fontSize;
    rootElement.style.removeProperty('font-size');
    const computed = Number.parseFloat(root.getComputedStyle(rootElement).fontSize);

    if (previousInline) rootElement.style.fontSize = previousInline;
    else rootElement.style.removeProperty('font-size');

    // 12.8px is only a fail-safe for the project's 80% root baseline.
    baseRootFontPx = Number.isFinite(computed) && computed > 0 ? computed : 12.8;
    return baseRootFontPx;
  }

  function currentPageFontKey() {
    let pathname = String(root.location && root.location.pathname ? root.location.pathname : '/').replace(/\/+$/, '') || '/';
    pathname = pathname.replace(/^\/object-passports\/\d+\/(?:edit|legacy-edit)$/i, '/object-passports/:id');
    pathname = pathname.split('/').map((segment) => /^\d+$/.test(segment) ? ':id' : segment).join('/') || '/';
    return pathname;
  }

  function pageFontScaleMap() {
    const value = get('pageFontScales');
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  }

  function currentPageFontScale() {
    const raw = Number.parseInt(pageFontScaleMap()[currentPageFontKey()], 10);
    return PAGE_FONT_SCALE_STEPS.includes(raw) ? raw : 100;
  }

  function applyCurrentPageFontScale() {
    const scale = currentPageFontScale();
    const basePx = resolveBaseRootFontPx();
    const effectivePx = basePx * scale / 100;
    document.documentElement.style.fontSize = `${effectivePx}px`;
    document.documentElement.dataset.pageFontScale = String(scale);
    document.documentElement.dataset.pageFontScaleBasePx = String(basePx);
    const value = document.querySelector('[data-page-font-scale-value]');
    if (value) value.textContent = `${scale}%`;
    document.querySelectorAll('[data-page-font-scale-delta]').forEach((button) => {
      const delta = Number.parseInt(button.dataset.pageFontScaleDelta, 10) || 0;
      const index = PAGE_FONT_SCALE_STEPS.indexOf(scale);
      button.disabled = (delta < 0 && index <= 0) || (delta > 0 && index >= PAGE_FONT_SCALE_STEPS.length - 1);
    });
    return scale;
  }

  function changeCurrentPageFontScale(delta) {
    const current = currentPageFontScale();
    const index = Math.max(0, PAGE_FONT_SCALE_STEPS.indexOf(current));
    const nextIndex = Math.max(0, Math.min(PAGE_FONT_SCALE_STEPS.length - 1, index + (delta < 0 ? -1 : 1)));
    const next = PAGE_FONT_SCALE_STEPS[nextIndex];
    const map = { ...pageFontScaleMap(), [currentPageFontKey()]: next };
    set('pageFontScales', map, 'page-font-scale');
    applyCurrentPageFontScale();
  }

  function installPageFontScaleControl() {
    const menu = document.getElementById('sidebarActionMenu');
    if (!menu || menu.querySelector('[data-page-font-scale-control]')) return;
    const control = document.createElement('div');
    control.className = 'sidebar-font-scale-control';
    control.dataset.pageFontScaleControl = 'true';
    control.setAttribute('aria-label', 'Размер текста на этой странице');
    control.innerHTML = `
      <button type="button" class="sidebar-font-scale-control__button" data-page-font-scale-delta="-1" aria-label="Уменьшить текст">A−</button>
      <span class="sidebar-font-scale-control__value"><span>Текст страницы</span><strong data-page-font-scale-value>100%</strong></span>
      <button type="button" class="sidebar-font-scale-control__button" data-page-font-scale-delta="1" aria-label="Увеличить текст">A+</button>
    `;
    menu.appendChild(control);
    control.addEventListener('click', (event) => {
      const button = event.target.closest('[data-page-font-scale-delta]');
      if (!button) return;
      changeCurrentPageFontScale(Number.parseInt(button.dataset.pageFontScaleDelta, 10) || 0);
    });
  }

  root.iguanaUiPreferences = Object.freeze({
    get,
    set,
    remove,
    snapshot,
    getStorageKey,
    registry: REGISTRY,
  });

  Object.entries(bootstrapPrefs).forEach(([name, value]) => {
    if (!REGISTRY[name]) {
      return;
    }
    set(name, value, 'bootstrap');
  });

  document.addEventListener('ui-preference:change', (event) => {
    if (event && event.detail && event.detail.name === 'pageFontScales') applyCurrentPageFontScale();
  });

  function initializePageFontScale() {
    // Resolve the stylesheet-defined baseline only after CSS has loaded.
    resolveBaseRootFontPx();
    installPageFontScaleControl();
    applyCurrentPageFontScale();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initializePageFontScale, { once: true });
  else initializePageFontScale();

  root.addEventListener('storage', (event) => {
    if (!event.key) return;
    Object.entries(REGISTRY).forEach(([name, config]) => {
      if (config.storageKey !== event.key) {
        return;
      }
      const nextValue = get(name);
      dispatchPreferenceChange(name, nextValue, 'storage');
    });
  });
})();

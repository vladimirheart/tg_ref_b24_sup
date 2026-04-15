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
  }

  function getStorageKey(name) {
    return REGISTRY[name]?.storageKey || null;
  }

  root.iguanaUiPreferences = Object.freeze({
    get,
    set,
    remove,
    getStorageKey,
    registry: REGISTRY,
  });

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

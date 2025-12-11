// panel/static/theme.js
(function () {
  const STORAGE_KEY = 'bender:theme';
  const root = document.documentElement;
  const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function normalize(theme) {
    if (theme === 'dark' || theme === 'light' || theme === 'auto') {
      return theme;
    }
    return 'light';
  }

  function syncBodyDataset(value) {
    const apply = () => {
      if (document.body) {
        document.body.dataset.theme = value;
      }
    };
    if (document.body) {
      apply();
    } else {
      document.addEventListener('DOMContentLoaded', apply, { once: true });
    }
  }

  function resolveEffective(theme) {
    if (theme === 'auto') {
      return media && media.matches ? 'dark' : 'light';
    }
    return theme;
  }

  function applyTheme(theme) {
    const normalized = normalize(theme);
    const effective = resolveEffective(normalized);
    if (root) {
      root.dataset.themeChoice = normalized;
      root.dataset.theme = effective;
    }
    syncBodyDataset(effective);
    document.dispatchEvent(new CustomEvent('theme:change', { detail: { theme: normalized, effective } }));
  }

  const saved = normalize(localStorage.getItem(STORAGE_KEY) || 'light');
  applyTheme(saved);

  const api = {
    get() {
      return normalize(localStorage.getItem(STORAGE_KEY) || 'light');
    },
    set(theme) {
      const normalized = normalize(theme);
      localStorage.setItem(STORAGE_KEY, normalized);
      applyTheme(normalized);
    },
    apply: applyTheme,
  };

  window.BenderTheme = api;

  if (media && media.addEventListener) {
    media.addEventListener('change', () => {
      if (api.get() === 'auto') {
        applyTheme('auto');
      }
    });
  }

  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY) {
      applyTheme(event.newValue || 'light');
    }
  });
})();

// panel/static/theme.js
(function () {
  const prefApi = window.iguanaUiPreferences || null;
  const THEME_PREF_KEY = 'theme';
  const PALETTE_PREF_KEY = 'themePalette';
  const THEME_STORAGE_KEY = prefApi ? prefApi.getStorageKey(THEME_PREF_KEY) : 'iguana:theme';
  const PALETTE_STORAGE_KEY = prefApi ? prefApi.getStorageKey(PALETTE_PREF_KEY) : 'iguana:theme-palette';
  const root = document.documentElement;
  const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function normalizeTheme(theme) {
    if (theme === 'dark' || theme === 'light' || theme === 'auto') {
      return theme;
    }
    return 'light';
  }

  function normalizePalette(palette) {
    if (palette === 'neo' || palette === 'catppuccin' || palette === 'amber-minimal') {
      return palette;
    }
    return 'neo';
  }

  function syncBodyDatasets(themeValue, paletteValue) {
    const apply = () => {
      if (!document.body) return;
      document.body.dataset.theme = themeValue;
      document.body.dataset.themePalette = paletteValue;
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
    const normalized = normalizeTheme(theme);
    const effective = resolveEffective(normalized);
    const palette = normalizePalette(prefApi ? prefApi.get(PALETTE_PREF_KEY) : localStorage.getItem(PALETTE_STORAGE_KEY) || 'neo');
    if (root) {
      root.dataset.themeChoice = normalized;
      root.dataset.theme = effective;
      root.dataset.themePalette = palette;
    }
    syncBodyDatasets(effective, palette);
    document.dispatchEvent(new CustomEvent('theme:change', {
      detail: { theme: normalized, effective, palette },
    }));
  }

  function applyPalette(palette) {
    const normalized = normalizePalette(palette);
    const theme = normalizeTheme(prefApi ? prefApi.get(THEME_PREF_KEY) : localStorage.getItem(THEME_STORAGE_KEY) || 'light');
    const effective = resolveEffective(theme);
    if (root) {
      root.dataset.themePalette = normalized;
      root.dataset.theme = effective;
      root.dataset.themeChoice = theme;
    }
    syncBodyDatasets(effective, normalized);
    document.dispatchEvent(new CustomEvent('theme:palette-change', {
      detail: { palette: normalized, theme, effective },
    }));
    document.dispatchEvent(new CustomEvent('theme:change', {
      detail: { theme, effective, palette: normalized },
    }));
  }

  const savedTheme = normalizeTheme(prefApi ? prefApi.get(THEME_PREF_KEY) : localStorage.getItem(THEME_STORAGE_KEY) || 'light');
  const savedPalette = normalizePalette(prefApi ? prefApi.get(PALETTE_PREF_KEY) : localStorage.getItem(PALETTE_STORAGE_KEY) || 'neo');
  if (prefApi) {
    prefApi.set(THEME_PREF_KEY, savedTheme, 'theme-bootstrap');
    prefApi.set(PALETTE_PREF_KEY, savedPalette, 'theme-bootstrap');
  } else {
    localStorage.setItem(THEME_STORAGE_KEY, savedTheme);
    localStorage.setItem(PALETTE_STORAGE_KEY, savedPalette);
  }
  applyTheme(savedTheme);
  applyPalette(savedPalette);

  const apiTheme = {
    get() {
      return normalizeTheme(prefApi ? prefApi.get(THEME_PREF_KEY) : localStorage.getItem(THEME_STORAGE_KEY) || 'light');
    },
    set(theme) {
      const normalized = normalizeTheme(theme);
      if (prefApi) {
        prefApi.set(THEME_PREF_KEY, normalized, 'theme-api');
      } else {
        localStorage.setItem(THEME_STORAGE_KEY, normalized);
      }
      applyTheme(normalized);
    },
    apply: applyTheme,
  };

  const apiPalette = {
    get() {
      return normalizePalette(prefApi ? prefApi.get(PALETTE_PREF_KEY) : localStorage.getItem(PALETTE_STORAGE_KEY) || 'neo');
    },
    set(palette) {
      const normalized = normalizePalette(palette);
      if (prefApi) {
        prefApi.set(PALETTE_PREF_KEY, normalized, 'theme-api');
      } else {
        localStorage.setItem(PALETTE_STORAGE_KEY, normalized);
      }
      applyPalette(normalized);
    },
    apply: applyPalette,
  };

  window.iguanaTheme = apiTheme;
  window.iguanaThemePalette = apiPalette;

  if (media && media.addEventListener) {
    media.addEventListener('change', () => {
      if (apiTheme.get() === 'auto') {
        applyTheme('auto');
      }
    });
  }

  document.addEventListener('ui-preference:change', (event) => {
    const detail = event && event.detail ? event.detail : {};
    if (detail.name === THEME_PREF_KEY) {
      applyTheme(detail.value || 'light');
      return;
    }
    if (detail.name === PALETTE_PREF_KEY) {
      applyPalette(detail.value || 'neo');
    }
  });

  if (!prefApi) {
    window.addEventListener('storage', (event) => {
      if (event.key === THEME_STORAGE_KEY) {
        applyTheme(event.newValue || 'light');
        return;
      }
      if (event.key === PALETTE_STORAGE_KEY) {
        applyPalette(event.newValue || 'neo');
      }
    });
  }
})();

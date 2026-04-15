(function () {
  if (typeof document === 'undefined') {
    return;
  }

  const PRESETS = Object.freeze({
    dashboard: Object.freeze({
      density: 'comfortable',
      hero: 'analytics',
      shell: 'wide',
      panel: 'immersive',
      chart: 'operational',
    }),
    settings: Object.freeze({
      density: 'compact',
      hero: 'workspace',
      shell: 'admin',
      panel: 'sheet',
      detail: 'stacked',
    }),
    dialogs: Object.freeze({
      density: 'comfortable',
      hero: 'desk',
      shell: 'workspace',
      panel: 'flow',
      list: 'comfortable',
    }),
  });

  function resolvePage(body) {
    if (!(body instanceof HTMLElement)) {
      return null;
    }
    const explicit = (body.dataset.uiPage || '').trim();
    if (explicit) {
      return explicit;
    }
    const path = window.location.pathname || '';
    if (path.startsWith('/settings')) return 'settings';
    if (path.startsWith('/dialogs')) return 'dialogs';
    if (path.startsWith('/dashboard') || path.startsWith('/reports')) return 'dashboard';
    return null;
  }

  function applyPreset() {
    const body = document.body;
    const root = document.documentElement;
    if (!(body instanceof HTMLElement) || !(root instanceof HTMLElement)) {
      return;
    }

    const page = resolvePage(body);
    if (!page) {
      return;
    }

    const preset = PRESETS[page];
    if (!preset) {
      return;
    }

    body.dataset.uiPage = page;
    root.dataset.uiPage = page;

    Object.entries(preset).forEach(([key, value]) => {
      const attrName = `ui${key.charAt(0).toUpperCase()}${key.slice(1)}`;
      if (!body.dataset[attrName]) {
        body.dataset[attrName] = value;
      }
      if (!root.dataset[attrName]) {
        root.dataset[attrName] = value;
      }
      root.style.setProperty(`--ui-${key}`, value);
    });

    document.dispatchEvent(new CustomEvent('ui:preset-change', {
      detail: { page, preset },
    }));
  }

  window.iguanaUiConfig = {
    presets: PRESETS,
    apply: applyPreset,
  };

  if (document.body) {
    applyPreset();
  } else {
    document.addEventListener('DOMContentLoaded', applyPreset, { once: true });
  }
})();

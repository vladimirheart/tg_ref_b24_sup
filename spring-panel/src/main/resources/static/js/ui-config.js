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
    analytics: Object.freeze({
      density: 'comfortable',
      hero: 'analytics',
      shell: 'wide',
      panel: 'immersive',
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
    clients: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'admin',
      panel: 'surface',
    }),
    knowledge: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'admin',
      panel: 'surface',
    }),
    channels: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'admin',
      panel: 'surface',
    }),
    users: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'admin',
      panel: 'surface',
    }),
    tasks: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'default',
      panel: 'surface',
    }),
    passports: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'default',
      panel: 'surface',
    }),
    public: Object.freeze({
      density: 'comfortable',
      hero: 'workspace',
      shell: 'default',
      panel: 'surface',
    }),
    'ai-ops': Object.freeze({
      density: 'comfortable',
      hero: 'analytics',
      shell: 'workspace',
      panel: 'immersive',
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
    if (path === '/ai-ops' || path.startsWith('/dialogs/ai-ops')) return 'ai-ops';
    if (path.startsWith('/dialogs')) return 'dialogs';
    if (path.startsWith('/dashboard') || path.startsWith('/reports')) return 'dashboard';
    if (path.startsWith('/analytics')) return 'analytics';
    if (path.startsWith('/clients') || path.startsWith('/unblock-requests')) return 'clients';
    if (path.startsWith('/knowledge-base')) return 'knowledge';
    if (path.startsWith('/channels')) return 'channels';
    if (path.startsWith('/users')) return 'users';
    if (path.startsWith('/tasks')) return 'tasks';
    if (path.startsWith('/object-passports')) return 'passports';
    if (path.startsWith('/public')) return 'public';
    if (path === '/') return 'dialogs';
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

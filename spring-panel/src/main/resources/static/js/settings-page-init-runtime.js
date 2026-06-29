(function () {
  if (window.SettingsPageInitRuntime) {
    return;
  }

  const PAYLOAD_SELECTOR = 'script[data-settings-page-init-payload]';

  function resolveFunction(candidate, fallback) {
    return typeof candidate === 'function' ? candidate : fallback;
  }

  function parseRawConfig(source) {
    if (source && typeof source === 'object' && !(source instanceof HTMLElement)) {
      return source;
    }

    const element = typeof source === 'string'
      ? document.getElementById(source)
      : source;
    if (!(element instanceof HTMLScriptElement)) {
      return {};
    }

    const rawText = String(element.textContent || '').trim();
    if (!rawText) {
      return {};
    }

    try {
      return JSON.parse(rawText);
    } catch (error) {
      console.error('Не удалось разобрать settings page init payload.', error);
      return {};
    }
  }

  function createRuntime(options = {}) {
    const rawConfig = parseRawConfig(options.rawConfig);
    const settingsPageConfig = window.SettingsPageConfigRuntime?.build(rawConfig) || {};

    const bootstrapRuntime = window.SettingsPageBootstrapRuntime?.mount({
      ...settingsPageConfig,
      getCookieValue: resolveFunction(options.getCookieValue, window.getCookieValue),
      requestSettingsModalClose: resolveFunction(
        options.requestSettingsModalClose,
        (source) => window.SettingsPageShell?.requestCloseModal?.(source),
      ),
      showPopup: resolveFunction(options.showPopup, window.showPopup),
      showNotification: resolveFunction(options.showNotification, window.showNotification),
      confirmDialog: resolveFunction(
        options.confirmDialog,
        typeof window.confirm === 'function' ? (message) => window.confirm(message) : null,
      ),
    }) || null;

    return {
      bootstrapRuntime,
      settingsPageConfig,
    };
  }

  function mount(options = {}) {
    if (window.__settingsPageInitRuntime) {
      return window.__settingsPageInitRuntime;
    }

    const runtime = createRuntime(options);
    window.__settingsPageInitRuntime = runtime;
    return runtime;
  }

  function mountFromPayload(source, options = {}) {
    return mount({
      ...options,
      rawConfig: parseRawConfig(source),
    });
  }

  function autoMountFromDocument() {
    if (window.__settingsPageInitRuntime) {
      return window.__settingsPageInitRuntime;
    }

    const payloadEl = document.querySelector(PAYLOAD_SELECTOR);
    if (!(payloadEl instanceof HTMLScriptElement)) {
      return null;
    }

    return mountFromPayload(payloadEl);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', autoMountFromDocument, { once: true });
  } else {
    autoMountFromDocument();
  }

  window.SettingsPageInitRuntime = Object.freeze({
    mount,
    mountFromPayload,
  });
}());

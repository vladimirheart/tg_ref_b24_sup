(function () {
  if (window.SettingsPageInitRuntime) {
    return;
  }

  const PAYLOAD_SELECTOR = 'script[data-settings-page-init-payload]';

  function resolveFunction(candidate, fallback) {
    return typeof candidate === 'function' ? candidate : fallback;
  }

  function getCommonUtils() {
    const commonUtils = window.SettingsRuntimeAccess?.resolveRuntimeApi?.('CommonUtils');
    return commonUtils && typeof commonUtils === 'object' ? commonUtils : null;
  }

  function fallbackGetCookieValue(name) {
    if (typeof document === 'undefined') {
      return '';
    }
    const cookies = document.cookie ? document.cookie.split(';') : [];
    const encodedName = encodeURIComponent(name) + '=';
    for (const cookie of cookies) {
      const trimmed = cookie.trim();
      if (trimmed.startsWith(encodedName)) {
        return decodeURIComponent(trimmed.slice(encodedName.length));
      }
    }
    return '';
  }

  function fallbackShowNotification(message, type = 'info') {
    const commonUtils = getCommonUtils();
    if (typeof commonUtils?.showNotification === 'function') {
      commonUtils.showNotification(message, type);
      return;
    }
    console.log(message);
  }

  function fallbackShowPopup(message, type = 'info') {
    const commonUtils = getCommonUtils();
    if (typeof commonUtils?.showPopup === 'function') {
      commonUtils.showPopup(message, type);
      return;
    }
    fallbackShowNotification(message, type);
  }

  function fallbackConfirmDialog(message) {
    if (typeof globalThis.confirm === 'function') {
      return globalThis.confirm(message);
    }
    return false;
  }

  function fallbackPromptDialog(message, defaultValue = '') {
    if (typeof globalThis.prompt === 'function') {
      return globalThis.prompt(message, defaultValue);
    }
    return null;
  }

  function fallbackRequestSettingsModalClose(source) {
    const requestCloseModal = window.SettingsRuntimeAccess?.resolveRuntimeMethod?.('SettingsPageShell', 'requestCloseModal');
    return typeof requestCloseModal === 'function' ? requestCloseModal(source) : undefined;
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
    const settingsPageConfig = window.SettingsRuntimeAccess?.buildPageConfig?.(rawConfig) || {};

    const bootstrapRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsPageBootstrapRuntime', {
      ...settingsPageConfig,
      getCookieValue: resolveFunction(options.getCookieValue, fallbackGetCookieValue),
      requestSettingsModalClose: resolveFunction(options.requestSettingsModalClose, fallbackRequestSettingsModalClose),
      showPopup: resolveFunction(options.showPopup, fallbackShowPopup),
      showNotification: resolveFunction(options.showNotification, fallbackShowNotification),
      confirmDialog: resolveFunction(options.confirmDialog, fallbackConfirmDialog),
      promptDialog: resolveFunction(options.promptDialog, fallbackPromptDialog),
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

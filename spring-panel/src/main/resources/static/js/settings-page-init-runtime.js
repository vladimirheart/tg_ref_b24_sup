(function () {
  if (window.__settingsPageInitRuntimeScriptLoaded) {
    return;
  }
  window.__settingsPageInitRuntimeScriptLoaded = true;

  const PAYLOAD_SELECTOR = 'script[data-settings-page-init-payload]';

  function resolveFunction(candidate, ...fallbacks) {
    if (typeof candidate === 'function') {
      return candidate;
    }
    for (const fallback of fallbacks) {
      if (typeof fallback === 'function') {
        return fallback;
      }
    }
    return null;
  }

  function getCommonUtils() {
    const commonUtils = window.SettingsRuntimeAccess?.resolveRuntimeApi?.('CommonUtils');
    return commonUtils && typeof commonUtils === 'object' ? commonUtils : null;
  }

  function resolveCommonUtilsMethod(methodName) {
    const commonUtils = getCommonUtils();
    if (!commonUtils || typeof methodName !== 'string' || !methodName.trim()) {
      return null;
    }
    const candidate = commonUtils[methodName.trim()];
    return typeof candidate === 'function' ? candidate.bind(commonUtils) : null;
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

  function noopShowNotification(message) {
    console.log(message);
  }

  function fallbackShowNotification(message, type = 'info') {
    const showNotification = resolveCommonUtilsMethod('showNotification');
    if (showNotification) {
      showNotification(message, type);
      return;
    }
    noopShowNotification(message, type);
  }

  function fallbackShowPopup(message, type = 'info') {
    const showPopup = resolveCommonUtilsMethod('showPopup');
    if (showPopup) {
      showPopup(message, type);
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

  function resolveGetCookieValue(options) {
    return resolveFunction(
      options.getCookieValue,
      resolveCommonUtilsMethod('getCookieValue'),
      fallbackGetCookieValue,
    ) || fallbackGetCookieValue;
  }

  function resolveRequestSettingsModalClose(options) {
    return resolveFunction(
      options.requestSettingsModalClose,
      fallbackRequestSettingsModalClose,
    ) || fallbackRequestSettingsModalClose;
  }

  function resolveShowPopup(options) {
    return resolveFunction(
      options.showPopup,
      resolveCommonUtilsMethod('showPopup'),
      fallbackShowPopup,
    ) || fallbackShowPopup;
  }

  function resolveShowNotification(options) {
    return resolveFunction(
      options.showNotification,
      resolveCommonUtilsMethod('showNotification'),
      fallbackShowNotification,
    ) || fallbackShowNotification;
  }

  function resolveConfirmDialog(options) {
    return resolveFunction(
      options.confirmDialog,
      fallbackConfirmDialog,
    ) || fallbackConfirmDialog;
  }

  function resolvePromptDialog(options) {
    return resolveFunction(
      options.promptDialog,
      fallbackPromptDialog,
    ) || fallbackPromptDialog;
  }

  function createHelperContract(options = {}) {
    return {
      getCookieValue: resolveGetCookieValue(options),
      requestSettingsModalClose: resolveRequestSettingsModalClose(options),
      showPopup: resolveShowPopup(options),
      showNotification: resolveShowNotification(options),
      confirmDialog: resolveConfirmDialog(options),
      promptDialog: resolvePromptDialog(options),
    };
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
    const helpers = createHelperContract(options);

    const bootstrapRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsPageBootstrapRuntime', {
      ...settingsPageConfig,
      ...helpers,
    }) || null;

    return {
      bootstrapRuntime,
      helpers,
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
}());

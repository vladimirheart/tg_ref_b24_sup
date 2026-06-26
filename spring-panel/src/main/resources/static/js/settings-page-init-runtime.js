(function () {
  if (window.SettingsPageInitRuntime) {
    return;
  }

  function resolveFunction(candidate, fallback) {
    return typeof candidate === 'function' ? candidate : fallback;
  }

  function createRuntime(options = {}) {
    const rawConfig = options.rawConfig && typeof options.rawConfig === 'object'
      ? options.rawConfig
      : {};
    const settingsPageConfig = window.SettingsPageConfigRuntime?.build(rawConfig) || {};

    window.SettingsPageConfigRuntime?.publishLegacyGlobals(settingsPageConfig);

    const bootstrapRuntime = window.SettingsPageBootstrapRuntime?.mount({
      ...settingsPageConfig,
      getCookieValue: resolveFunction(options.getCookieValue, window.getCookieValue),
      requestSettingsModalClose: resolveFunction(options.requestSettingsModalClose, window.requestSettingsModalClose),
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

  window.SettingsPageInitRuntime = Object.freeze({
    mount,
  });
}());

(function () {
  if (window.SettingsAdminShellRuntime) {
    return;
  }

  function resolveAuthManagementContainer(modalEl) {
    return modalEl instanceof HTMLElement
      ? modalEl.querySelector('[data-auth-management]')
      : null;
  }

  function resolveConfig(options) {
    const config = options && typeof options.config === 'object' ? options.config : null;
    return config && !Array.isArray(config) ? config : {};
  }

  function readConfigObject(config, key) {
    const value = config && typeof config[key] === 'object' ? config[key] : null;
    return value && !Array.isArray(value) ? value : null;
  }

  function readConfigArray(config, key) {
    return Array.isArray(config && config[key]) ? config[key] : null;
  }

  function createRuntime(options = {}) {
    const config = resolveConfig(options);
    const reportingRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsReportingManagerBindings', {
      reportingConfigInitial: readConfigObject(config, 'reportingConfig') || options.reportingConfigInitial,
      managerLocationBindingsInitial: readConfigArray(config, 'managerLocationBindings') || options.managerLocationBindingsInitial,
      getLocationsState: typeof options.getLocationsState === 'function'
        ? options.getLocationsState
        : null,
    }) || null;

    function mountAuthManagementSettingsModal(modalEl) {
      const container = resolveAuthManagementContainer(modalEl);
      const authManagementApi = window.SettingsRuntimeAccess?.resolveRuntimeApi?.('AuthManagement');
      if (!(container instanceof HTMLElement) || !authManagementApi) {
        console.error('Модуль управления доступом недоступен.');
        return;
      }

      if (!container.__authManager) {
        container.__authManager = window.SettingsRuntimeAccess?.mountRuntime?.('AuthManagement', container) || null;
        return;
      }

      container.__authManager.refresh();
    }

    function resetAuthManagementSettingsModal(modalEl) {
      const container = resolveAuthManagementContainer(modalEl);
      const manager = container && container.__authManager;
      if (!manager) {
        return;
      }

      manager.resetPasswords();
      manager.clearMessage();
    }

    return {
      reportingRuntime,
      mountAuthManagementSettingsModal,
      resetAuthManagementSettingsModal,
    };
  }

  function mount(options = {}) {
    if (window.__settingsAdminShellRuntime) {
      return window.__settingsAdminShellRuntime;
    }

    const runtime = createRuntime(options);
    window.SettingsPageCallbackRegistry?.registerMany({
      mountAuthManagementSettingsModal: runtime.mountAuthManagementSettingsModal,
      resetAuthManagementSettingsModal: runtime.resetAuthManagementSettingsModal,
    });
    window.__settingsAdminShellRuntime = runtime;
    return runtime;
  }

  window.SettingsAdminShellRuntime = Object.freeze({
    mount,
  });
}());

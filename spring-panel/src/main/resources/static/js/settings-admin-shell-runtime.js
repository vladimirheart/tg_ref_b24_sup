(function () {
  if (window.SettingsAdminShellRuntime) {
    return;
  }

  function resolveAuthManagementContainer(modalEl) {
    return modalEl instanceof HTMLElement
      ? modalEl.querySelector('[data-auth-management]')
      : null;
  }

  function createRuntime(options = {}) {
    const reportingRuntime = window.SettingsReportingManagerBindings?.mount({
      reportingConfigInitial: options.reportingConfigInitial,
      managerLocationBindingsInitial: options.managerLocationBindingsInitial,
      getLocationsState: typeof options.getLocationsState === 'function'
        ? options.getLocationsState
        : null,
    }) || null;

    function mountAuthManagementSettingsModal(modalEl) {
      const container = resolveAuthManagementContainer(modalEl);
      if (!(container instanceof HTMLElement) || !window.AuthManagement) {
        console.error('Модуль управления доступом недоступен.');
        return;
      }

      if (!container.__authManager) {
        container.__authManager = window.AuthManagement.mount(container);
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
    window.mountAuthManagementSettingsModal = runtime.mountAuthManagementSettingsModal;
    window.resetAuthManagementSettingsModal = runtime.resetAuthManagementSettingsModal;
    window.__settingsAdminShellRuntime = runtime;
    return runtime;
  }

  window.SettingsAdminShellRuntime = Object.freeze({
    mount,
  });
}());

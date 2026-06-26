(function () {
  if (window.SettingsDialogShellRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function collectRuntimeState(runtime, collectorName, fallbackMessage) {
      if (!runtime || typeof runtime[collectorName] !== 'function') {
        return {
          config: {},
          errors: [fallbackMessage],
        };
      }
      const state = runtime[collectorName]();
      if (!state || typeof state !== 'object') {
        return {
          config: {},
          errors: [fallbackMessage],
        };
      }
      return {
        config: state.config && typeof state.config === 'object' ? state.config : {},
        errors: Array.isArray(state.errors) ? state.errors : [],
      };
    }

    function collectDialogSlaConfig() {
      const errors = [];
      const slaCoreRuntime = options.slaCoreRuntime || null;
      const workspaceGovernanceRuntime = options.workspaceGovernanceRuntime || null;
      const workspaceExternalKpiRuntime = options.workspaceExternalKpiRuntime || null;

      const slaCoreState = collectRuntimeState(
        slaCoreRuntime,
        'collectSlaCoreConfig',
        'Не удалось собрать настройки SLA/workspace core.'
      );
      const macroClientContextState = collectRuntimeState(
        slaCoreRuntime,
        'collectMacroClientContextConfig',
        'Не удалось собрать настройки macro/client context.'
      );
      const workspaceGovernanceState = collectRuntimeState(
        workspaceGovernanceRuntime,
        'collectWorkspaceGovernanceConfig',
        'Не удалось собрать настройки workspace governance.'
      );
      const workspaceExternalKpiState = collectRuntimeState(
        workspaceExternalKpiRuntime,
        'collectWorkspaceExternalKpiConfig',
        'Не удалось собрать настройки workspace external KPI.'
      );

      [
        slaCoreState,
        macroClientContextState,
        workspaceGovernanceState,
        workspaceExternalKpiState,
      ].forEach((state) => {
        if (state.errors.length) {
          errors.push(...state.errors);
        }
      });

      return {
        errors,
        config: {
          ...slaCoreState.config,
          ...macroClientContextState.config,
          ...workspaceGovernanceState.config,
          ...workspaceExternalKpiState.config,
        },
      };
    }

    return {
      collectDialogSlaConfig,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogShellRuntime) {
        return window.__settingsDialogShellRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsDialogShellRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogShellRuntime = Object.freeze(api);
}());

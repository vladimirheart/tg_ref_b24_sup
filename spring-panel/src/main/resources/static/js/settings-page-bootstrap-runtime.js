(function () {
  if (window.SettingsPageBootstrapRuntime) {
    return;
  }

  function createEscapeHtml() {
    return function escapeHtml(value) {
      if (value === null || value === undefined) {
        return '';
      }
      return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
    };
  }

  function resolveConfigSection(options, sectionName) {
    const section = options && typeof options === 'object' ? options[sectionName] : null;
    return section && typeof section === 'object' ? section : {};
  }

  function resolveHelper(options, name, fallback) {
    const candidate = options && typeof options[name] === 'function' ? options[name] : null;
    return candidate || fallback;
  }

  function createRuntime(options = {}) {
    const escapeHtml = createEscapeHtml();
    let settingsLocationsTreeRuntime = null;
    const dialog = resolveConfigSection(options, 'dialog');
    const admin = resolveConfigSection(options, 'admin');
    const channels = resolveConfigSection(options, 'channels');
    const parameters = resolveConfigSection(options, 'parameters');
    const appearance = resolveConfigSection(options, 'appearance');
    const locations = resolveConfigSection(options, 'locations');

    const showPopup = resolveHelper(options, 'showPopup', function noopShowPopup() {});
    const showNotification = resolveHelper(options, 'showNotification', null);
    const requestSettingsModalClose = resolveHelper(options, 'requestSettingsModalClose', function noopRequestSettingsModalClose() {});
    const getCookieValue = resolveHelper(options, 'getCookieValue', function fallbackGetCookieValue() { return ''; });
    const confirmDialog = resolveHelper(options, 'confirmDialog', function fallbackConfirmDialog() { return false; });
    const promptDialog = resolveHelper(options, 'promptDialog', function fallbackPromptDialog() { return null; });

    const settingsDialogSlaCoreRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogSlaCoreRuntime', {
      getDialogConfig: () => dialog.config || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || {},
    }) || null;

    const settingsDialogMetricsRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogMetricsRuntime', {
      getDialogConfig: () => dialog.config || {},
      getDefaultDialogTimeMetrics: () => dialog.defaultTimeMetrics || {},
      getDefaultSummaryBadges: () => dialog.defaultSummaryBadges || {},
    }) || null;

    const settingsDialogWorkspaceGovernanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceGovernanceRuntime', {
      getDialogConfig: () => dialog.config || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || {},
    }) || null;

    const settingsDialogWorkspaceExternalKpiRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceExternalKpiRuntime', {
      getDialogConfig: () => dialog.config || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || {},
      escapeHtml,
    }) || null;

    const settingsDialogTemplatesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogTemplatesRuntime', {
      getDialogConfig: () => dialog.config || {},
      getAutoCloseConfig: () => dialog.autoCloseConfig || {},
      getAutoCloseFallbackHours: () => dialog.autoCloseFallbackHours ?? 24,
      getFallbackDialogCategories: () => dialog.fallbackCategories || [],
      canPublishDialogMacros: () => Boolean(dialog.canPublishMacros),
      escapeHtml,
    }) || null;

    const settingsDialogShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogShellRuntime', {
      slaCoreRuntime: settingsDialogSlaCoreRuntime,
      workspaceGovernanceRuntime: settingsDialogWorkspaceGovernanceRuntime,
      workspaceExternalKpiRuntime: settingsDialogWorkspaceExternalKpiRuntime,
    }) || null;

    const settingsParametersShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsParametersShellRuntime', {
      config: parameters,
      showPopup: (message) => showPopup(message),
      escapeHtml,
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
      promptDialog: (message, defaultValue) => promptDialog(message, defaultValue),
      buildLocationsTree: () => settingsLocationsTreeRuntime?.buildLocationsTree(),
    }) || null;

    const settingsNetworkProfilesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsNetworkProfilesRuntime', {
      config: parameters,
      escapeHtml,
      showPopup: (message) => showPopup(message),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
    }) || null;
    const settingsAppearanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAppearanceRuntime', {
      config: appearance,
      showPopup: (message, type) => showPopup(message, type),
      confirmDialog: (message) => confirmDialog(message),
      escapeHtml,
    }) || null;

    const settingsLocationsIikoRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsIikoRuntime', {
      config: locations,
      showPopup: (message, type) => showPopup(message, type),
      escapeHtml,
    }) || null;

    settingsLocationsTreeRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsTreeRuntime', {
      config: locations,
      loadParameters: () => settingsParametersShellRuntime?.loadParameters(),
      showPopup: (message, type) => showPopup(message, type),
      confirmDialog: (message) => confirmDialog(message),
      promptDialog: (message, defaultValue) => promptDialog(message, defaultValue),
      serializeLocationsIikoServerSources: () => settingsLocationsIikoRuntime?.serializeLocationsIikoServerSources?.() || [],
      serializeLocationsIikoSyncSettings: () => settingsLocationsIikoRuntime?.serializeLocationsIikoSyncSettings?.() || {},
      markLocationsIikoServerSourcesSaved: () => settingsLocationsIikoRuntime?.markLocationsIikoServerSourcesSaved?.(),
    }) || null;

    const settingsLocationWizardRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationWizardRuntime', {
      getParameterData: () => settingsParametersShellRuntime?.getParameterData() || {},
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
      getCityOptionsFallback: () => parameters.cityOptions || [],
      loadParameters: () => settingsParametersShellRuntime?.loadParameters(),
      isParametersLoaded: () => settingsParametersShellRuntime?.isParametersLoaded() || false,
      showPopup: (message, type) => showPopup(message, type),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      buildLocationsTree: () => settingsLocationsTreeRuntime?.buildLocationsTree(),
      setStatus: (level, parts, status) => settingsLocationsTreeRuntime?.setStatus(level, parts, status),
      writeNodeMeta: (mapName, key, meta) => settingsLocationsTreeRuntime?.writeNodeMeta(mapName, key, meta),
      makeCityMetaKey: (business, typeName, cityName) =>
        settingsLocationsTreeRuntime?.makeCityMetaKey(business, typeName, cityName),
      makeLocationMetaKey: (business, typeName, cityName, locationName) =>
        settingsLocationsTreeRuntime?.makeLocationMetaKey(business, typeName, cityName, locationName),
      sortArrayAlphabetically: (values) => settingsLocationsTreeRuntime?.sortArrayAlphabetically(values) || [],
      defaultLocationStatus: settingsLocationsTreeRuntime?.getDefaultLocationStatus(),
    }) || null;

    const settingsChannelsShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelsShellRuntime', {
      config: {
        ...channels,
        autoCloseConfig: dialog.autoCloseConfig || {},
      },
      escapeHtml,
      getCookieValue: (name) => getCookieValue(name),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      showPopup: (message) => showPopup(message),
      showNotification,
      confirmDialog: (message) => confirmDialog(message),
    }) || null;

    const settingsAdminShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAdminShellRuntime', {
      config: admin,
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
    }) || null;

    const settingsSaveRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsSaveRuntime', {
      templatesRuntime: settingsDialogTemplatesRuntime,
      metricsRuntime: settingsDialogMetricsRuntime,
      dialogShellRuntime: settingsDialogShellRuntime,
      networkProfilesRuntime: settingsNetworkProfilesRuntime,
      getAutoCloseFallbackHours: () => dialog.autoCloseFallbackHours ?? 24,
      collectStatuses: () => Array.from(document.querySelectorAll('#statusesList .status-input'))
        .map((input) => input.value.trim())
        .filter(Boolean),
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
      areLocationsLoaded: () => settingsLocationsTreeRuntime?.isLoaded?.() || false,
      serializeLocationsIikoServerSources: () => settingsLocationsIikoRuntime?.serializeLocationsIikoServerSources?.() || [],
      serializeLocationsIikoSyncSettings: () => settingsLocationsIikoRuntime?.serializeLocationsIikoSyncSettings?.() || {},
      markLocationsIikoServerSourcesSaved: () => settingsLocationsIikoRuntime?.markLocationsIikoServerSourcesSaved?.(),
      showPopup: (message) => showPopup(message),
      getSaveUrl: () => '/settings',
    }) || null;

    async function saveSettings() {
      return settingsSaveRuntime?.saveSettings();
    }

    return {
      escapeHtml,
      saveSettings,
      settingsAdminShellRuntime,
      settingsAppearanceRuntime,
      settingsChannelsShellRuntime,
      settingsDialogMetricsRuntime,
      settingsDialogShellRuntime,
      settingsDialogSlaCoreRuntime,
      settingsDialogTemplatesRuntime,
      settingsDialogWorkspaceExternalKpiRuntime,
      settingsDialogWorkspaceGovernanceRuntime,
      settingsLocationWizardRuntime,
      settingsLocationsIikoRuntime,
      settingsLocationsTreeRuntime,
      settingsNetworkProfilesRuntime,
      settingsParametersShellRuntime,
      settingsSaveRuntime,
    };
  }

  function mount(options = {}) {
    if (window.__settingsPageBootstrapRuntime) {
      return window.__settingsPageBootstrapRuntime;
    }

    const runtime = createRuntime(options);
    window.SettingsPageCallbackRegistry?.register('saveSettings', runtime.saveSettings);
    window.__settingsPageBootstrapRuntime = runtime;
    return runtime;
  }

  window.SettingsPageBootstrapRuntime = Object.freeze({
    mount,
    saveSettings(...args) {
      return window.__settingsPageBootstrapRuntime?.saveSettings?.(...args);
    },
  });
}());

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

  function createRuntime(options = {}) {
    const escapeHtml = createEscapeHtml();
    let settingsLocationsTreeRuntime = null;

    const showPopup = typeof options.showPopup === 'function'
      ? options.showPopup
      : function noopShowPopup() {};
    const requestSettingsModalClose = typeof options.requestSettingsModalClose === 'function'
      ? options.requestSettingsModalClose
      : function noopRequestSettingsModalClose() {};
    const getCookieValue = typeof options.getCookieValue === 'function'
      ? options.getCookieValue
      : function fallbackGetCookieValue() { return ''; };
    const confirmDialog = typeof options.confirmDialog === 'function'
      ? options.confirmDialog
      : function fallbackConfirmDialog(message) {
          return typeof globalThis.confirm === 'function' ? globalThis.confirm(message) : false;
        };

    const settingsDialogSlaCoreRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogSlaCoreRuntime', {
      getDialogConfig: () => options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => options.defaultDialogSlaConfig || {},
    }) || null;

    const settingsDialogMetricsRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogMetricsRuntime', {
      getDialogConfig: () => options.dialogConfig || {},
      getDefaultDialogTimeMetrics: () => options.defaultDialogTimeMetrics || {},
      getDefaultSummaryBadges: () => options.defaultSummaryBadges || {},
    }) || null;

    const settingsDialogWorkspaceGovernanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceGovernanceRuntime', {
      getDialogConfig: () => options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => options.defaultDialogSlaConfig || {},
    }) || null;

    const settingsDialogWorkspaceExternalKpiRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceExternalKpiRuntime', {
      getDialogConfig: () => options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => options.defaultDialogSlaConfig || {},
      escapeHtml,
    }) || null;

    const settingsDialogTemplatesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogTemplatesRuntime', {
      getDialogConfig: () => options.dialogConfig || {},
      getAutoCloseConfig: () => options.autoCloseConfig || {},
      getAutoCloseFallbackHours: () => options.autoCloseFallbackHours ?? 24,
      getFallbackDialogCategories: () => options.fallbackDialogCategories || [],
      canPublishDialogMacros: () => Boolean(options.canPublishDialogMacros),
      escapeHtml,
    }) || null;

    const settingsDialogShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogShellRuntime', {
      slaCoreRuntime: settingsDialogSlaCoreRuntime,
      workspaceGovernanceRuntime: settingsDialogWorkspaceGovernanceRuntime,
      workspaceExternalKpiRuntime: settingsDialogWorkspaceExternalKpiRuntime,
    }) || null;

    const settingsParametersShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsParametersShellRuntime', {
      getParameterTitles: () => options.parameterTitles || {},
      getParameterDependencies: () => options.parameterDependencies || {},
      getParameterStateTypes: () => options.parameterStateTypes || new Set(),
      getParameterStates: () => options.parameterStates || [],
      getParameterFilterKeys: () => options.parameterFilterKeys || {},
      getCityParameterType: () => options.cityParameterType || 'city',
      getCityParameterLabel: () => options.cityParameterLabel || 'Город',
      getCityOptions: () => options.cityOptions || [],
      getItConnectionFallbackLabels: () => options.itConnectionFallbackLabels || {},
      getItConnectionCategoryFields: () => options.itConnectionCategoryFields || {},
      getItConnectionUsageFilterParams: () => options.itConnectionUsageFilterParams || {},
      getPartnerContactTypes: () => options.partnerContactTypes || {},
      getPartnerContactPhoneTypes: () => options.partnerContactPhoneTypes || {},
      getPartnerContactEmailTypes: () => options.partnerContactEmailTypes || {},
      getPartnerContactStateClassMap: () => options.partnerContactStateClassMap || {},
      showPopup: (message) => showPopup(message),
      escapeHtml,
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
      buildLocationsTree: () => settingsLocationsTreeRuntime?.buildLocationsTree(),
    }) || null;

    const settingsNetworkProfilesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsNetworkProfilesRuntime', {
      getInitialProfiles: () => options.networkProfilesInitial || [],
      getContractUsageData: () => options.contractUsageData || {},
      escapeHtml,
      showPopup: (message) => showPopup(message),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
    }) || null;
    settingsNetworkProfilesRuntime?.renderNetworkProfiles();

    const settingsAppearanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAppearanceRuntime', {
      statusUsage: options.statusUsage || {},
      initialClientStatuses: options.initialClientStatuses || [],
      initialClientStatusColors: options.initialClientStatusColors || {},
      initialBusinessCellStyles: options.initialBusinessCellStyles || {},
      showPopup: (message, type) => showPopup(message, type),
      escapeHtml,
    }) || null;

    const settingsLocationsIikoRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsIikoRuntime', {
      initialServerSources: options.locationsIikoServerSourcesInitial || [],
      initialSyncSettings: options.locationsIikoSyncSettingsInitial || {},
      showPopup: (message, type) => showPopup(message, type),
      escapeHtml,
    }) || null;

    settingsLocationsTreeRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsTreeRuntime', {
      initialLocations: options.locationsInitial || {},
      loadParameters: () => settingsParametersShellRuntime?.loadParameters(),
      showPopup: (message, type) => showPopup(message, type),
      serializeLocationsIikoServerSources: () => settingsLocationsIikoRuntime?.serializeLocationsIikoServerSources?.() || [],
      serializeLocationsIikoSyncSettings: () => settingsLocationsIikoRuntime?.serializeLocationsIikoSyncSettings?.() || {},
      markLocationsIikoServerSourcesSaved: () => settingsLocationsIikoRuntime?.markLocationsIikoServerSourcesSaved?.(),
    }) || null;

    const settingsLocationWizardRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationWizardRuntime', {
      getParameterData: () => settingsParametersShellRuntime?.getParameterData() || {},
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
      getCityOptionsFallback: () => options.cityOptions || [],
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
      botSettingsInitial: options.botSettingsInitial || {},
      autoCloseConfig: options.autoCloseConfig || {},
      integrationNetworkInitial: options.integrationNetworkInitial || {},
      integrationNetworkProfilesInitial: options.integrationNetworkProfilesInitial || [],
      escapeHtml,
      getCookieValue: (name) => getCookieValue(name),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      showPopup: (message) => showPopup(message),
      showNotification: typeof options.showNotification === 'function' ? options.showNotification : null,
      confirmDialog: (message) => confirmDialog(message),
    }) || null;

    const settingsAdminShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAdminShellRuntime', {
      reportingConfigInitial: options.reportingConfigInitial || {},
      managerLocationBindingsInitial: options.managerLocationBindingsInitial || [],
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
    }) || null;

    const settingsSaveRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsSaveRuntime', {
      templatesRuntime: settingsDialogTemplatesRuntime,
      metricsRuntime: settingsDialogMetricsRuntime,
      dialogShellRuntime: settingsDialogShellRuntime,
      networkProfilesRuntime: settingsNetworkProfilesRuntime,
      getAutoCloseFallbackHours: () => options.autoCloseFallbackHours ?? 24,
      collectStatuses: () => Array.from(document.querySelectorAll('#statusesList .status-input'))
        .map((input) => input.value.trim())
        .filter(Boolean),
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
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

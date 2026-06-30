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

  function createRuntime(options = {}) {
    const escapeHtml = createEscapeHtml();
    let settingsLocationsTreeRuntime = null;
    const dialog = resolveConfigSection(options, 'dialog');
    const admin = resolveConfigSection(options, 'admin');
    const channels = resolveConfigSection(options, 'channels');
    const parameters = resolveConfigSection(options, 'parameters');
    const appearance = resolveConfigSection(options, 'appearance');
    const locations = resolveConfigSection(options, 'locations');

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
      getDialogConfig: () => dialog.config || options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || options.defaultDialogSlaConfig || {},
    }) || null;

    const settingsDialogMetricsRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogMetricsRuntime', {
      getDialogConfig: () => dialog.config || options.dialogConfig || {},
      getDefaultDialogTimeMetrics: () => dialog.defaultTimeMetrics || options.defaultDialogTimeMetrics || {},
      getDefaultSummaryBadges: () => dialog.defaultSummaryBadges || options.defaultSummaryBadges || {},
    }) || null;

    const settingsDialogWorkspaceGovernanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceGovernanceRuntime', {
      getDialogConfig: () => dialog.config || options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || options.defaultDialogSlaConfig || {},
    }) || null;

    const settingsDialogWorkspaceExternalKpiRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogWorkspaceExternalKpiRuntime', {
      getDialogConfig: () => dialog.config || options.dialogConfig || {},
      getDefaultDialogSlaConfig: () => dialog.defaultSlaConfig || options.defaultDialogSlaConfig || {},
      escapeHtml,
    }) || null;

    const settingsDialogTemplatesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogTemplatesRuntime', {
      getDialogConfig: () => dialog.config || options.dialogConfig || {},
      getAutoCloseConfig: () => dialog.autoCloseConfig || options.autoCloseConfig || {},
      getAutoCloseFallbackHours: () => dialog.autoCloseFallbackHours ?? options.autoCloseFallbackHours ?? 24,
      getFallbackDialogCategories: () => dialog.fallbackCategories || options.fallbackDialogCategories || [],
      canPublishDialogMacros: () => Boolean(dialog.canPublishMacros ?? options.canPublishDialogMacros),
      escapeHtml,
    }) || null;

    const settingsDialogShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsDialogShellRuntime', {
      slaCoreRuntime: settingsDialogSlaCoreRuntime,
      workspaceGovernanceRuntime: settingsDialogWorkspaceGovernanceRuntime,
      workspaceExternalKpiRuntime: settingsDialogWorkspaceExternalKpiRuntime,
    }) || null;

    const settingsParametersShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsParametersShellRuntime', {
      getParameterTitles: () => parameters.titles || options.parameterTitles || {},
      getParameterDependencies: () => parameters.dependencies || options.parameterDependencies || {},
      getParameterStateTypes: () => parameters.stateTypes || options.parameterStateTypes || new Set(),
      getParameterStates: () => parameters.states || options.parameterStates || [],
      getParameterFilterKeys: () => parameters.filterKeys || options.parameterFilterKeys || {},
      getCityParameterType: () => parameters.cityParameterType || options.cityParameterType || 'city',
      getCityParameterLabel: () => parameters.cityParameterLabel || options.cityParameterLabel || 'Город',
      getCityOptions: () => parameters.cityOptions || options.cityOptions || [],
      getItConnectionFallbackLabels: () => parameters.itConnectionFallbackLabels || options.itConnectionFallbackLabels || {},
      getItConnectionCategoryFields: () => parameters.itConnectionCategoryFields || options.itConnectionCategoryFields || {},
      getItConnectionUsageFilterParams: () => parameters.itConnectionUsageFilterParams || options.itConnectionUsageFilterParams || {},
      getPartnerContactTypes: () => parameters.partnerContactTypes || options.partnerContactTypes || {},
      getPartnerContactPhoneTypes: () => parameters.partnerContactPhoneTypes || options.partnerContactPhoneTypes || {},
      getPartnerContactEmailTypes: () => parameters.partnerContactEmailTypes || options.partnerContactEmailTypes || {},
      getPartnerContactStateClassMap: () => parameters.partnerContactStateClassMap || options.partnerContactStateClassMap || {},
      showPopup: (message) => showPopup(message),
      escapeHtml,
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
      buildLocationsTree: () => settingsLocationsTreeRuntime?.buildLocationsTree(),
    }) || null;

    const settingsNetworkProfilesRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsNetworkProfilesRuntime', {
      getInitialProfiles: () => parameters.networkProfiles || options.networkProfilesInitial || [],
      getContractUsageData: () => parameters.contractUsageData || options.contractUsageData || {},
      escapeHtml,
      showPopup: (message) => showPopup(message),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      confirmDialog: (message) => confirmDialog(message),
    }) || null;
    settingsNetworkProfilesRuntime?.renderNetworkProfiles();

    const settingsAppearanceRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAppearanceRuntime', {
      statusUsage: appearance.statusUsage || options.statusUsage || {},
      initialClientStatuses: appearance.clientStatuses || options.initialClientStatuses || [],
      initialClientStatusColors: appearance.clientStatusColors || options.initialClientStatusColors || {},
      initialBusinessCellStyles: appearance.businessCellStyles || options.initialBusinessCellStyles || {},
      showPopup: (message, type) => showPopup(message, type),
      escapeHtml,
    }) || null;

    const settingsLocationsIikoRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsIikoRuntime', {
      initialServerSources: locations.iikoServerSources || options.locationsIikoServerSourcesInitial || [],
      initialSyncSettings: locations.iikoSyncSettings || options.locationsIikoSyncSettingsInitial || {},
      showPopup: (message, type) => showPopup(message, type),
      escapeHtml,
    }) || null;

    settingsLocationsTreeRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationsTreeRuntime', {
      initialLocations: locations.tree || options.locationsInitial || {},
      loadParameters: () => settingsParametersShellRuntime?.loadParameters(),
      showPopup: (message, type) => showPopup(message, type),
      serializeLocationsIikoServerSources: () => settingsLocationsIikoRuntime?.serializeLocationsIikoServerSources?.() || [],
      serializeLocationsIikoSyncSettings: () => settingsLocationsIikoRuntime?.serializeLocationsIikoSyncSettings?.() || {},
      markLocationsIikoServerSourcesSaved: () => settingsLocationsIikoRuntime?.markLocationsIikoServerSourcesSaved?.(),
    }) || null;

    const settingsLocationWizardRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsLocationWizardRuntime', {
      getParameterData: () => settingsParametersShellRuntime?.getParameterData() || {},
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
      getCityOptionsFallback: () => parameters.cityOptions || options.cityOptions || [],
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
      botSettingsInitial: channels.botSettings || options.botSettingsInitial || {},
      autoCloseConfig: dialog.autoCloseConfig || options.autoCloseConfig || {},
      integrationNetworkInitial: channels.integrationNetwork || options.integrationNetworkInitial || {},
      integrationNetworkProfilesInitial: channels.integrationNetworkProfiles || options.integrationNetworkProfilesInitial || [],
      escapeHtml,
      getCookieValue: (name) => getCookieValue(name),
      requestSettingsModalClose: (source) => requestSettingsModalClose(source),
      showPopup: (message) => showPopup(message),
      showNotification: typeof options.showNotification === 'function' ? options.showNotification : null,
      confirmDialog: (message) => confirmDialog(message),
    }) || null;

    const settingsAdminShellRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsAdminShellRuntime', {
      reportingConfigInitial: admin.reportingConfig || options.reportingConfigInitial || {},
      managerLocationBindingsInitial: admin.managerLocationBindings || options.managerLocationBindingsInitial || [],
      getLocationsState: () => settingsLocationsTreeRuntime?.getState() || {},
    }) || null;

    const settingsSaveRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsSaveRuntime', {
      templatesRuntime: settingsDialogTemplatesRuntime,
      metricsRuntime: settingsDialogMetricsRuntime,
      dialogShellRuntime: settingsDialogShellRuntime,
      networkProfilesRuntime: settingsNetworkProfilesRuntime,
      getAutoCloseFallbackHours: () => dialog.autoCloseFallbackHours ?? options.autoCloseFallbackHours ?? 24,
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

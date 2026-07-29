(function () {
  if (window.SettingsChannelsShellRuntime) {
    return;
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

  function hasInitialIntegrationNetworkData(config, integrationNetworkProfilesInitial) {
    const integrationNetwork = readConfigObject(config, 'integrationNetwork');
    if (integrationNetwork && Object.keys(integrationNetwork).length > 0) {
      return true;
    }
    return Array.isArray(integrationNetworkProfilesInitial) && integrationNetworkProfilesInitial.length > 0;
  }

  function createRuntime(options = {}) {
    const config = resolveConfig(options);
    const botSettingsInitial = readConfigObject(config, 'botSettings') || {};
    const autoCloseConfig = readConfigObject(config, 'autoCloseConfig') || {};
    const integrationNetworkInitial = readConfigObject(config, 'integrationNetwork') || {};
    const integrationNetworkProfilesInitial = readConfigArray(config, 'integrationNetworkProfiles') || [];
    const hasInitialIntegrationNetworkState = hasInitialIntegrationNetworkData(config, integrationNetworkProfilesInitial);
    const state = {
      channelsInitialized: false,
      channelsRegistry: new Map(),
      channelEditorState: {
        channelId: null,
        tokenVisible: false,
      },
      channelAssignmentRoutingCatalog: {
        operators: [],
        departments: [],
        orgStructure: { nodes: [] },
      },
      integrationNetworkProfilesData: Array.isArray(integrationNetworkProfilesInitial)
        ? integrationNetworkProfilesInitial
        : [],
    };

    const elements = {
      channelsBody: document.getElementById('channelsBody'),
      channelsRefreshBotStatusesBtn: document.getElementById('channelsRefreshBotStatusesBtn'),
      addChannelFormEl: document.getElementById('addChannelForm'),
      integrationNetworkSaveBtn: document.getElementById('integrationNetworkSaveBtn'),
      projectNetworkModeInput: document.getElementById('projectNetworkMode'),
      projectProxySchemeInput: document.getElementById('projectProxyScheme'),
      botsNetworkModeInput: document.getElementById('botsNetworkMode'),
      botsProxySchemeInput: document.getElementById('botsProxyScheme'),
      channelEditorNetworkModeInput: document.getElementById('channelEditorNetworkMode'),
      channelEditorProxySchemeInput: document.getElementById('channelEditorProxyScheme'),
      projectNetworkProfileIdInput: document.getElementById('projectNetworkProfileId'),
      botsNetworkProfileIdInput: document.getElementById('botsNetworkProfileId'),
      channelEditorNetworkProfileIdInput: document.getElementById('channelEditorNetworkProfileId'),
      integrationNetworkProfilesBody: document.getElementById('integrationNetworkProfilesBody'),
      integrationNetworkProfilesSaveBtn: document.querySelector('[data-integration-network-profiles-save]'),
      integrationNetworkProfilesProbeAllBtn: document.querySelector('[data-integration-network-profiles-probe-all]'),
      integrationNetworkProfileEditorModalEl: document.getElementById('integrationNetworkProfileEditorModal'),
      vkWebhookFormEl: document.getElementById('vkWebhookForm'),
      channelEditorPanelNotifFirstResponseOverdueInput: document.getElementById('channelEditorPanelNotifFirstResponseOverdue'),
      channelEditorPanelNotifTargetModeInput: document.getElementById('channelEditorPanelNotifTargetMode'),
      channelEditorPanelNotifDeliveryModeInput: document.getElementById('channelEditorPanelNotifDeliveryMode'),
      channelEditorPanelNotifDepartmentInput: document.getElementById('channelEditorPanelNotifDepartment'),
      channelEditorPanelNotifEmployeesInput: document.getElementById('channelEditorPanelNotifEmployees'),
      channelEditorPanelNotifExcludeInput: document.getElementById('channelEditorPanelNotifExclude'),
      channelEditorAssignmentModeInput: document.getElementById('channelEditorAssignmentMode'),
      channelEditorAssignmentAssignResponsibleInput: document.getElementById('channelEditorAssignmentAssignResponsible'),
      channelEditorAssignmentStrategyInput: document.getElementById('channelEditorAssignmentStrategy'),
      channelEditorAssignmentSingleOperatorInput: document.getElementById('channelEditorAssignmentSingleOperator'),
      channelEditorAssignmentPoolOperatorsInput: document.getElementById('channelEditorAssignmentPoolOperators'),
      channelEditorAssignmentDepartmentInput: document.getElementById('channelEditorAssignmentDepartment'),
    };

    const integrationNetworkProfileForm = elements.integrationNetworkProfileEditorModalEl
      ? elements.integrationNetworkProfileEditorModalEl.querySelector('[data-integration-network-profile-form]')
      : null;
    const integrationNetworkProfileDeleteBtn = elements.integrationNetworkProfileEditorModalEl
      ? elements.integrationNetworkProfileEditorModalEl.querySelector('[data-integration-network-profile-delete]')
      : null;
    const integrationNetworkProfileModeInput = elements.integrationNetworkProfileEditorModalEl
      ? elements.integrationNetworkProfileEditorModalEl.querySelector('[data-integration-network-profile-mode]')
      : null;
    const integrationNetworkProfileProxySchemeInput = elements.integrationNetworkProfileEditorModalEl
      ? elements.integrationNetworkProfileEditorModalEl.querySelector('[data-integration-network-profile-proxy-scheme]')
      : null;

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
      }
    }

    function requestCloseModal(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function pluralize(count, forms) {
      const n = Math.abs(count) % 100;
      const n1 = n % 10;
      if (n > 10 && n < 20) {
        return forms[2];
      }
      if (n1 > 1 && n1 < 5) {
        return forms[1];
      }
      if (n1 === 1) {
        return forms[0];
      }
      return forms[2];
    }

    function formatBotLabel(channel) {
      const parts = [];
      const botName = channel.bot_name || channel.botName;
      const botUsername = channel.bot_username || channel.botUsername;
      const credentialName = channel.credential?.name ? String(channel.credential.name).trim() : '';
      if (botName) {
        parts.push(botName);
      } else if (credentialName) {
        parts.push(credentialName);
      }
      if (botUsername) {
        parts.push(`@${botUsername}`);
      } else if (credentialName) {
        parts.push('учётные данные привязаны');
      }
      return parts.length ? parts.join(' • ') : '—';
    }

    function normalizeAssignmentRoutingCatalog(raw) {
      const source = raw && typeof raw === 'object' ? raw : {};
      const operators = Array.isArray(source.operators)
        ? source.operators
            .filter((item) => item && typeof item === 'object')
            .map((item) => ({
              username: String(item.username || '').trim().toLowerCase(),
              display_name: String(item.display_name || '').trim(),
              department: String(item.department || '').trim(),
              role: String(item.role || '').trim(),
              avatar_url: String(item.avatar_url || '').trim(),
            }))
            .filter((item) => item.username)
        : [];
      const departments = Array.isArray(source.departments)
        ? Array.from(new Set(source.departments
            .map((value) => String(value || '').trim())
            .filter((value) => value)))
            .sort((left, right) => left.localeCompare(right, 'ru'))
        : [];
      const orgStructure = source.orgStructure && typeof source.orgStructure === 'object'
        ? source.orgStructure
        : { nodes: [] };
      return { operators, departments, orgStructure };
    }

    function applyAssignmentRoutingCatalog(raw) {
      state.channelAssignmentRoutingCatalog = normalizeAssignmentRoutingCatalog(raw);
      settingsChannelEditorShell?.refreshChannelEditorIfOpen?.();
    }

    function ensureChannelAssignmentRoutingCatalogLoaded(forceReload = false) {
      const fetchPageDataSection = window.SettingsRuntimeAccess?.fetchPageDataSection;
      if (typeof fetchPageDataSection !== 'function') {
        return Promise.resolve(state.channelAssignmentRoutingCatalog);
      }
      return fetchPageDataSection('channels', forceReload ? { force: true } : {})
        .then((section) => {
          const catalog = section && typeof section === 'object' ? section.assignmentRoutingCatalog : null;
          applyAssignmentRoutingCatalog(catalog);
          return state.channelAssignmentRoutingCatalog;
        })
        .catch(() => state.channelAssignmentRoutingCatalog);
    }

    const settingsChannelTemplates = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelTemplatesRuntime', {
      botSettingsInitial,
      autoCloseConfig,
      escapeHtml: options.escapeHtml,
      pluralize,
    });
    const settingsChannelConfig = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelConfigRuntime');
    const settingsIntegrationNetwork = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsIntegrationNetworkRuntime', {
      initialIntegrationNetwork: integrationNetworkInitial,
      hasInitialData: hasInitialIntegrationNetworkState,
      getProfilesData: () => state.integrationNetworkProfilesData,
      setProfilesData: (nextProfiles) => {
        state.integrationNetworkProfilesData = Array.isArray(nextProfiles) ? nextProfiles : [];
      },
      getChannelsRegistry: () => state.channelsRegistry,
      escapeHtml: options.escapeHtml,
      getCookieValue: (name) => typeof options.getCookieValue === 'function' ? options.getCookieValue(name) : '',
      requestSettingsModalClose: requestCloseModal,
      showPopup: (message, type) => typeof options.showPopup === 'function' ? options.showPopup(message, type) : undefined,
    });
    state.integrationNetworkProfilesData = Array.isArray(integrationNetworkProfilesInitial)
      ? integrationNetworkProfilesInitial.map((item) => settingsIntegrationNetwork.normalizeIntegrationNetworkProfile(item))
      : [];

    function populateChannelEditor(channel) {
      state.channelEditorState.channelId = channel.id;
      settingsChannelEditorShell?.populateChannelEditorShell?.(channel);
    }

    function readMultiSelectValues(selectEl) {
      if (!(selectEl instanceof HTMLSelectElement) || !selectEl.multiple) {
        return [];
      }
      return Array.from(selectEl.selectedOptions || [])
        .map((option) => String(option.value || '').trim().toLowerCase())
        .filter((value) => value);
    }

    function buildChannelQuestionsCfgPayload(existingQuestionsCfg) {
      const existingAssignmentRouting = existingQuestionsCfg?.assignmentRouting && typeof existingQuestionsCfg.assignmentRouting === 'object'
        ? existingQuestionsCfg.assignmentRouting
        : settingsChannelConfig.defaultChannelAssignmentRouting();
      const existingPanelNotifications = existingQuestionsCfg?.panelNotifications && typeof existingQuestionsCfg.panelNotifications === 'object'
        ? existingQuestionsCfg.panelNotifications
        : settingsChannelConfig.defaultChannelPanelNotifications();
      const existingRouting = existingPanelNotifications.routing && typeof existingPanelNotifications.routing === 'object'
        ? existingPanelNotifications.routing
        : settingsChannelConfig.defaultChannelPanelNotifications().routing;
      const existingEvents = existingPanelNotifications.events && typeof existingPanelNotifications.events === 'object'
        ? existingPanelNotifications.events
        : settingsChannelConfig.defaultChannelPanelNotifications().events;
      const notificationTargetModeRaw = String(
        elements.channelEditorPanelNotifTargetModeInput?.value
        || existingQuestionsCfg?.panelNotifications?.routing?.targetMode
        || 'all_operators'
      ).trim().toLowerCase();
      const notificationTargetMode = ['all_operators', 'department_all', 'employees_only', 'department_except'].includes(notificationTargetModeRaw)
        ? notificationTargetModeRaw
        : 'all_operators';
      const notificationDepartmentValue = String(
        elements.channelEditorPanelNotifDepartmentInput?.value
        || existingQuestionsCfg?.panelNotifications?.routing?.department
        || ''
      ).trim();
      const normalizedNotificationTargetMode = notificationDepartmentValue
        ? notificationTargetMode
        : 'all_operators';
      const notificationDeliveryModeRaw = String(
        elements.channelEditorPanelNotifDeliveryModeInput?.value
        || existingQuestionsCfg?.panelNotifications?.routing?.deliveryMode
        || 'all'
      ).trim().toLowerCase();
      const notificationDeliveryMode = notificationDeliveryModeRaw === 'online_only_fallback_all'
        ? 'online_only_fallback_all'
        : 'all';
      const assignmentModeRaw = String(
        elements.channelEditorAssignmentModeInput?.value
        || existingAssignmentRouting.mode
        || 'disabled'
      ).trim().toLowerCase();
      const assignmentMode = ['single_operator', 'operator_pool', 'department_queue'].includes(assignmentModeRaw)
        ? assignmentModeRaw
        : 'disabled';
      const assignmentEnabled = assignmentMode !== 'disabled';
      const assignmentStrategyRaw = String(
        elements.channelEditorAssignmentStrategyInput?.value
        || existingAssignmentRouting.strategy
        || 'round_robin'
      ).trim().toLowerCase();
      const assignmentStrategy = ['least_loaded', 'hash_by_ticket'].includes(assignmentStrategyRaw)
        ? assignmentStrategyRaw
        : 'round_robin';
      const assignmentSingleOperator = String(
        elements.channelEditorAssignmentSingleOperatorInput?.value
        || existingAssignmentRouting.operatorUsername
        || ''
      ).trim().toLowerCase();
      const assignmentDepartment = String(
        elements.channelEditorAssignmentDepartmentInput?.value
        || existingAssignmentRouting.department
        || ''
      ).trim();
      return {
        ...existingQuestionsCfg,
        schemaVersion: Math.max(1, Number.parseInt(existingQuestionsCfg?.schemaVersion || 1, 10) || 1),
        assignmentRouting: {
          enabled: assignmentEnabled,
          mode: assignmentEnabled ? assignmentMode : 'disabled',
          assignResponsibleOnCreate: assignmentEnabled
            ? Boolean(elements.channelEditorAssignmentAssignResponsibleInput?.checked)
            : false,
          strategy: assignmentStrategy,
          operatorUsername: assignmentEnabled && assignmentMode === 'single_operator'
            ? assignmentSingleOperator
            : '',
          operatorUsernames: assignmentEnabled && assignmentMode === 'operator_pool'
            ? readMultiSelectValues(elements.channelEditorAssignmentPoolOperatorsInput)
            : [],
          department: assignmentEnabled && assignmentMode === 'department_queue'
            ? assignmentDepartment
            : '',
        },
        panelNotifications: {
          routing: {
            department: notificationDepartmentValue,
            targetMode: normalizedNotificationTargetMode,
            deliveryMode: notificationDeliveryMode,
            employeeUsernames: settingsChannelConfig.serializeChannelNotificationList(
              elements.channelEditorPanelNotifEmployeesInput?.value || existingRouting.employeeUsernames
            ),
            excludeUsernames: settingsChannelConfig.serializeChannelNotificationList(
              elements.channelEditorPanelNotifExcludeInput?.value || existingRouting.excludeUsernames
            ),
          },
          events: {
            newPublicAppeal: typeof existingEvents.newPublicAppeal === 'boolean'
              ? existingEvents.newPublicAppeal
              : true,
            firstResponseOverdue: Boolean(elements.channelEditorPanelNotifFirstResponseOverdueInput?.checked),
          },
        },
        fields: [],
      };
    }

    const settingsChannelsCatalog = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelsCatalogRuntime', {
      getChannelsRegistry: () => state.channelsRegistry,
      getChannelEditorState: () => state.channelEditorState,
      getQuestionTemplates: () => settingsChannelTemplates.getQuestionTemplates(),
      getQuestionTemplateMap: () => settingsChannelTemplates.getQuestionTemplateMap(),
      getDefaultQuestionTemplateId: () => settingsChannelTemplates.getDefaultQuestionTemplateId(),
      getRatingTemplates: () => settingsChannelTemplates.getRatingTemplates(),
      getRatingTemplateMap: () => settingsChannelTemplates.getRatingTemplateMap(),
      getDefaultRatingTemplateId: () => settingsChannelTemplates.getDefaultRatingTemplateId(),
      getQuestionTemplateSummary: (...args) => settingsChannelTemplates.getQuestionTemplateSummary(...args),
      getRatingTemplateSummary: (...args) => settingsChannelTemplates.getRatingTemplateSummary(...args),
      getAutoActionTemplates: () => settingsChannelTemplates.getAutoActionTemplates(),
      getAutoActionTemplateMap: () => settingsChannelTemplates.getAutoActionTemplateMap(),
      getDefaultAutoActionTemplateId: () => settingsChannelTemplates.getDefaultAutoActionTemplateId(),
      getAutoActionTemplateSummary: (...args) => settingsChannelTemplates.getAutoActionTemplateSummary(...args),
      buildTemplateOptions: (...args) => settingsChannelTemplates.buildTemplateOptions(...args),
      parseDeliverySettings: (...args) => settingsChannelConfig.parseDeliverySettings(...args),
      parseQuestionsCfg: (...args) => settingsChannelConfig.parseQuestionsCfg(...args),
      parsePlatformConfig: (...args) => settingsChannelConfig.parsePlatformConfig(...args),
      sanitizeTemplateId: (...args) => settingsChannelTemplates.sanitizeTemplateId(...args),
      formatBotLabel,
      normalizeNetworkRoute: (...args) => settingsIntegrationNetwork.normalizeNetworkRoute(...args),
      updateChannelsManageOverview: (...args) => settingsIntegrationNetwork.updateChannelsManageOverview(...args),
      refreshAllBotRuntimeStatuses: (...args) => settingsChannelsBotRuntime.refreshAllBotRuntimeStatuses(...args),
      refreshChannelEditorIfOpen: (...args) => settingsChannelEditorShell.refreshChannelEditorIfOpen(...args),
      requestSettingsModalClose: requestCloseModal,
      showPopup: (message) => popup(message),
      showNotification: typeof options.showNotification === 'function' ? options.showNotification : null,
      startBot: (...args) => settingsChannelsBotRuntime.startBot(...args),
    });
    const settingsChannelsBotRuntime = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelsBotRuntime', {
      getChannelsRegistry: () => state.channelsRegistry,
      getChannelEditorState: () => state.channelEditorState,
      notifyChannelStatus: (...args) => settingsChannelsCatalog.notifyChannelStatus(...args),
      loadChannels: (...args) => settingsChannelsCatalog.loadChannels(...args),
      showPopup: (message) => popup(message),
      getCookieValue: (name) => typeof options.getCookieValue === 'function' ? options.getCookieValue(name) : '',
    });
    const settingsChannelEditorShell = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelEditorShellRuntime', {
      getChannelsRegistry: () => state.channelsRegistry,
      getChannelEditorState: () => state.channelEditorState,
      getChannelAssignmentRoutingCatalog: () => state.channelAssignmentRoutingCatalog,
      getQuestionTemplates: () => settingsChannelTemplates.getQuestionTemplates(),
      getRatingTemplates: () => settingsChannelTemplates.getRatingTemplates(),
      getAutoActionTemplates: () => settingsChannelTemplates.getAutoActionTemplates(),
      getDefaultQuestionTemplateId: () => settingsChannelTemplates.getDefaultQuestionTemplateId(),
      getDefaultRatingTemplateId: () => settingsChannelTemplates.getDefaultRatingTemplateId(),
      getDefaultAutoActionTemplateId: () => settingsChannelTemplates.getDefaultAutoActionTemplateId(),
      getTemplateSummaryBuilders: () => settingsChannelTemplates.getTemplateSummaryBuilders(),
      getChannelEditorSummaries: () => ({
        question_template_id: document.querySelector('[data-channel-editor-summary="question_template_id"]'),
        rating_template_id: document.querySelector('[data-channel-editor-summary="rating_template_id"]'),
        auto_action_template_id: document.querySelector('[data-channel-editor-summary="auto_action_template_id"]'),
      }),
      buildTemplateOptions: (...args) => settingsChannelTemplates.buildTemplateOptions(...args),
      formatBotLabel,
      parseDeliverySettings: (...args) => settingsChannelConfig.parseDeliverySettings(...args),
      parsePlatformConfig: (...args) => settingsChannelConfig.parsePlatformConfig(...args),
      normalizeChannelWorkingHours: (...args) => settingsIntegrationNetwork.normalizeChannelWorkingHours(...args),
      defaultChannelAssignmentRouting: (...args) => settingsChannelConfig.defaultChannelAssignmentRouting(...args),
      defaultChannelPanelNotifications: (...args) => settingsChannelConfig.defaultChannelPanelNotifications(...args),
      applyRouteToInputs: (...args) => settingsIntegrationNetwork.applyRouteToInputs(...args),
      updateBotRefreshControl: (...args) => settingsChannelsBotRuntime.updateBotRefreshControl(...args),
      updateChannelEditorToken: (...args) => settingsChannelsBotRuntime.updateChannelEditorToken(...args),
      updateTestRecipientState: (...args) => settingsChannelsBotRuntime.updateTestRecipientState(...args),
      setTestStatus: (...args) => settingsChannelsBotRuntime.setTestStatus(...args),
      updateBotStatusLabel: (...args) => settingsChannelsBotRuntime.updateBotStatusLabel(...args),
      updateBotControls: (...args) => settingsChannelsBotRuntime.updateBotControls(...args),
      refreshBotStatus: (...args) => settingsChannelsBotRuntime.refreshBotStatus(...args),
      loadChannels: (...args) => settingsChannelsCatalog.loadChannels(...args),
      escapeHtml: options.escapeHtml,
      showPopup: (message) => popup(message),
      populateChannelEditor,
    });
    const settingsChannelEditorPersistence = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelEditorPersistenceRuntime', {
      getChannelsRegistry: () => state.channelsRegistry,
      getChannelEditorState: () => state.channelEditorState,
      parseDeliverySettings: (...args) => settingsChannelConfig.parseDeliverySettings(...args),
      parsePlatformConfig: (...args) => settingsChannelConfig.parsePlatformConfig(...args),
      parseQuestionsCfg: (...args) => settingsChannelConfig.parseQuestionsCfg(...args),
      normalizeChannelWorkingHours: (...args) => settingsIntegrationNetwork.normalizeChannelWorkingHours(...args),
      collectRouteFromInputs: (...args) => settingsIntegrationNetwork.collectRouteFromInputs(...args),
      buildQuestionsCfgPayload: (existingQuestionsCfg) => buildChannelQuestionsCfgPayload(existingQuestionsCfg),
      loadChannels: (...args) => settingsChannelsCatalog.loadChannels(...args),
      requestSettingsModalClose: requestCloseModal,
      showPopup: (message) => popup(message),
      confirmDialog: (message) => typeof options.confirmDialog === 'function' ? options.confirmDialog(message) : false,
    });
    const settingsChannelEditorControls = window.SettingsRuntimeAccess?.mountRuntime?.('SettingsChannelEditorControlsRuntime', {
      getChannelEditorState: () => state.channelEditorState,
      channelEditorShell: settingsChannelEditorShell,
      channelBotRuntime: settingsChannelsBotRuntime,
      channelEditorPersistence: settingsChannelEditorPersistence,
    });

    function initChannelsManagement() {
      if (state.channelsInitialized) {
        return;
      }
      state.channelsInitialized = true;
      settingsChannelsCatalog.populateAddChannelTemplateSelects();
      settingsChannelsCatalog.togglePlatformFields();
      settingsIntegrationNetwork.renderIntegrationNetworkProfilesTable();
      settingsIntegrationNetwork.renderIntegrationNetworkSettings();
      settingsChannelEditorControls.bindControls();
      ensureChannelAssignmentRoutingCatalogLoaded(true);
      settingsChannelsCatalog.loadChannels();

      document.getElementById('new_platform')?.addEventListener('change', () => settingsChannelsCatalog.togglePlatformFields());
      elements.channelsBody?.addEventListener('click', (event) => settingsChannelsCatalog.handleChannelsBodyClick(event));
      elements.projectNetworkModeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleNetworkRouteFields('project'));
      elements.botsNetworkModeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleNetworkRouteFields('bots'));
      elements.channelEditorNetworkModeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleNetworkRouteFields('channel'));
      elements.projectProxySchemeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleProxyCredentialFields('project'));
      elements.botsProxySchemeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleProxyCredentialFields('bots'));
      elements.channelEditorProxySchemeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleProxyCredentialFields('channel'));
      elements.projectNetworkProfileIdInput?.addEventListener('change', () => settingsIntegrationNetwork.updateNetworkRouteSummary('project'));
      elements.botsNetworkProfileIdInput?.addEventListener('change', () => settingsIntegrationNetwork.updateNetworkRouteSummary('bots'));
      elements.channelEditorNetworkProfileIdInput?.addEventListener('change', () => settingsIntegrationNetwork.updateNetworkRouteSummary('channel'));
      elements.integrationNetworkSaveBtn?.addEventListener('click', () => settingsIntegrationNetwork.saveIntegrationNetworkSettings());
      elements.integrationNetworkProfilesProbeAllBtn?.addEventListener('click', () => settingsIntegrationNetwork.handleProbeAllProfilesClick());
      elements.integrationNetworkProfilesSaveBtn?.addEventListener('click', () => settingsIntegrationNetwork.saveIntegrationNetworkProfiles());
      integrationNetworkProfileModeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleIntegrationNetworkProfileModeFields());
      integrationNetworkProfileProxySchemeInput?.addEventListener('change', () => settingsIntegrationNetwork.toggleProxyCredentialFields('integration-profile'));
      elements.integrationNetworkProfilesBody?.addEventListener('click', (event) => settingsIntegrationNetwork.handleProfilesTableClick(event));
      integrationNetworkProfileForm?.addEventListener('submit', (event) => settingsIntegrationNetwork.handleProfileFormSubmit(event));
      integrationNetworkProfileDeleteBtn?.addEventListener('click', () => settingsIntegrationNetwork.handleProfileDeleteClick());
      elements.channelsRefreshBotStatusesBtn?.addEventListener('click', () => settingsChannelsBotRuntime.refreshAllBotRuntimeStatuses(false));
      elements.vkWebhookFormEl?.addEventListener('submit', (event) => {
        event.preventDefault();
        settingsChannelsCatalog.saveVkWebhookSettings();
      });
      elements.addChannelFormEl?.addEventListener('submit', (event) => {
        event.preventDefault();
        settingsChannelsCatalog.addChannel();
      });
    }

    function addChannel() {
      return settingsChannelsCatalog.addChannel();
    }

    return {
      addChannel,
      initChannelsManagement,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelsShellRuntime) {
        return window.__settingsChannelsShellRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        initChannelsManagement: runtime.initChannelsManagement,
        addChannel: runtime.addChannel,
      });
      window.__settingsChannelsShellRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelsShellRuntime = Object.freeze({
    ...api,
    initChannelsManagement(...args) {
      return window.__settingsChannelsShellRuntime?.initChannelsManagement?.(...args);
    },
  });
}());

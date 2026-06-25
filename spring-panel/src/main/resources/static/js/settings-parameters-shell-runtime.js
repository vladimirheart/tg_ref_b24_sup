(function () {
  if (window.SettingsParametersShellRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      parameterData: {},
      parametersLoaded: false,
      legalEntitiesBound: false,
    };

    const elements = {
      legalEntitiesContainer: document.querySelector('[data-legal-entities-list]'),
    };

    let settingsParameterDataRuntime = null;
    let settingsLegalEntitiesRuntime = null;
    let settingsItConnectionsRuntime = null;
    let settingsItEquipmentRuntime = null;
    let settingsPartnerContactsRuntime = null;
    let settingsParametersRuntime = null;

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    }

    function requestClose(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function confirmAction(message) {
      if (typeof options.confirmDialog === 'function') {
        return Boolean(options.confirmDialog(message));
      }
      return window.confirm(message);
    }

    function getParameterTitles() {
      const value = typeof options.getParameterTitles === 'function' ? options.getParameterTitles() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterDependencies() {
      const value = typeof options.getParameterDependencies === 'function' ? options.getParameterDependencies() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterStateTypes() {
      const value = typeof options.getParameterStateTypes === 'function' ? options.getParameterStateTypes() : null;
      return value instanceof Set ? value : new Set();
    }

    function getParameterStates() {
      const value = typeof options.getParameterStates === 'function' ? options.getParameterStates() : null;
      return Array.isArray(value) && value.length ? value : ['Активен'];
    }

    function getParameterFilterKeys() {
      const value = typeof options.getParameterFilterKeys === 'function' ? options.getParameterFilterKeys() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getCityParameterType() {
      return typeof options.getCityParameterType === 'function' ? options.getCityParameterType() : 'city';
    }

    function getCityParameterLabel() {
      return typeof options.getCityParameterLabel === 'function'
        ? options.getCityParameterLabel()
        : 'Город';
    }

    function getCityOptions() {
      const value = typeof options.getCityOptions === 'function' ? options.getCityOptions() : null;
      return Array.isArray(value) ? value : [];
    }

    function getItConnectionFallbackLabels() {
      const value = typeof options.getItConnectionFallbackLabels === 'function'
        ? options.getItConnectionFallbackLabels()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getItConnectionCategoryFields() {
      const value = typeof options.getItConnectionCategoryFields === 'function'
        ? options.getItConnectionCategoryFields()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getItConnectionUsageFilterParams() {
      const value = typeof options.getItConnectionUsageFilterParams === 'function'
        ? options.getItConnectionUsageFilterParams()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getPartnerContactTypes() {
      const value = typeof options.getPartnerContactTypes === 'function' ? options.getPartnerContactTypes() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getPartnerContactPhoneTypes() {
      const value = typeof options.getPartnerContactPhoneTypes === 'function'
        ? options.getPartnerContactPhoneTypes()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getPartnerContactEmailTypes() {
      const value = typeof options.getPartnerContactEmailTypes === 'function'
        ? options.getPartnerContactEmailTypes()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getPartnerContactStateClassMap() {
      const value = typeof options.getPartnerContactStateClassMap === 'function'
        ? options.getPartnerContactStateClassMap()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterData() {
      return state.parameterData;
    }

    function isParametersLoaded() {
      return state.parametersLoaded;
    }

    function getDependencyChain(...args) {
      return settingsParameterDataRuntime?.getDependencyChain(...args) || [];
    }

    function normalizeDependencyValue(...args) {
      return settingsParameterDataRuntime?.normalizeDependencyValue(...args) || '';
    }

    function getDependencyLabel(...args) {
      return settingsParameterDataRuntime?.getDependencyLabel(...args) || '';
    }

    function buildDependencySelect(...args) {
      return settingsParameterDataRuntime?.buildDependencySelect(...args) || '';
    }

    function refreshParameterDependencyControls(...args) {
      return settingsParameterDataRuntime?.refreshParameterDependencyControls(...args);
    }

    function collectDependencyValues(...args) {
      return settingsParameterDataRuntime?.collectDependencyValues(...args) || { values: {} };
    }

    function setItConnectionCategories(...args) {
      return settingsParameterDataRuntime?.setItConnectionCategories(...args);
    }

    function rebuildItConnectionCategorySelect(...args) {
      return settingsParameterDataRuntime?.rebuildItConnectionCategorySelect(...args);
    }

    function normalizeItConnectionCategory(...args) {
      return settingsParameterDataRuntime?.normalizeItConnectionCategory(...args) || 'equipment_type';
    }

    function getItConnectionCategoryLabel(...args) {
      return settingsParameterDataRuntime?.getItConnectionCategoryLabel(...args) || '—';
    }

    function syncParameterData(source) {
      const result = settingsParameterDataRuntime?.syncParameterData(source);
      if (result && typeof result === 'object') {
        state.parameterData = result;
      }
      return state.parameterData;
    }

    function renderParameters(...args) {
      return settingsParametersRuntime?.renderParameters(...args);
    }

    function renderCityCard(...args) {
      return settingsParametersRuntime?.renderCityCard(...args);
    }

    function deleteParameter(...args) {
      return settingsParametersRuntime?.deleteParameter(...args);
    }

    function restoreParameter(...args) {
      return settingsParametersRuntime?.restoreParameter(...args);
    }

    function renderCurrentParameterModal(...args) {
      return settingsParametersRuntime?.renderCurrentParameterModal(...args);
    }

    function renderItConnectionsTable(...args) {
      return settingsItConnectionsRuntime?.renderItConnectionsTable(...args);
    }

    function renderItEquipmentTable(...args) {
      return settingsItEquipmentRuntime?.renderItEquipmentTable(...args);
    }

    function loadItEquipment(...args) {
      return settingsItEquipmentRuntime?.loadItEquipment(...args);
    }

    function bindLegalEntitiesEvents() {
      if (state.legalEntitiesBound) {
        return;
      }
      document.addEventListener('click', (event) => {
        const legalEntityAddBtn = event.target.closest('[data-legal-entity-add]');
        if (legalEntityAddBtn) {
          settingsLegalEntitiesRuntime?.addLegalEntityDraft();
          return;
        }
        const legalEntityAction = event.target.closest('[data-legal-entity-action]');
        if (legalEntityAction) {
          settingsLegalEntitiesRuntime?.handleActionClick(legalEntityAction);
        }
      });
      if (elements.legalEntitiesContainer && settingsLegalEntitiesRuntime) {
        elements.legalEntitiesContainer.addEventListener('input', (event) => {
          settingsLegalEntitiesRuntime.handleContainerInput(event);
        });
        elements.legalEntitiesContainer.addEventListener('change', (event) => {
          settingsLegalEntitiesRuntime.handleContainerChange(event);
        });
      }
      state.legalEntitiesBound = true;
    }

    function mountNestedRuntimes() {
      settingsParameterDataRuntime = window.SettingsParameterDataRuntime?.mount({
        getParameterTitles,
        getParameterDependencies,
        getParameterStates,
        getItConnectionFallbackLabels,
        normalizePartnerContactExtra: (...args) =>
          settingsPartnerContactsRuntime?.normalizePartnerContactExtra(...args) || Object.assign({}, args[0] || {}),
        resetPartnerContactEdits: () => settingsPartnerContactsRuntime?.clearPartnerContactEdits(),
        escapeHtml,
      });
      state.parameterData = settingsParameterDataRuntime?.getParameterData() || state.parameterData;

      settingsLegalEntitiesRuntime = window.SettingsLegalEntitiesRuntime?.mount({
        getParameterData,
        getParameterStates,
        escapeHtml,
        showPopup: (message) => popup(message),
        syncParameterData: (data) => syncParameterData(data),
        renderParameters: (...args) => renderParameters(...args),
        renderItConnectionsTable: (...args) => renderItConnectionsTable(...args),
        deleteParameter: (...args) => deleteParameter(...args),
        restoreParameter: (...args) => restoreParameter(...args),
      });

      settingsItConnectionsRuntime = window.SettingsItConnectionsRuntime?.mount({
        getParameterData,
        getDefaultCategory: () => settingsParameterDataRuntime?.getItConnectionDefaultCategory() || 'equipment_type',
        getCategoryFieldMap: getItConnectionCategoryFields,
        getUsageFilterParams: getItConnectionUsageFilterParams,
        normalizeItConnectionCategory: (...args) => normalizeItConnectionCategory(...args),
        getItConnectionCategoryLabel: (...args) => getItConnectionCategoryLabel(...args),
        rebuildItConnectionCategorySelect: (...args) => rebuildItConnectionCategorySelect(...args),
        setItConnectionCategories: (...args) => setItConnectionCategories(...args),
        escapeHtml,
        showPopup: (message) => popup(message),
        syncParameterData: (data) => syncParameterData(data),
        renderParameters: (...args) => renderParameters(...args),
        renderCurrentParameterModal: (...args) => renderCurrentParameterModal(...args),
        requestSettingsModalClose: (source) => requestClose(source),
        confirmDialog: (message) => confirmAction(message),
        renderItEquipmentTable: (...args) => renderItEquipmentTable(...args),
      });

      settingsItEquipmentRuntime = window.SettingsItEquipmentRuntime?.mount({
        getParameterData,
        normalizeItConnectionCategory: (...args) => normalizeItConnectionCategory(...args),
        escapeHtml,
        showPopup: (message) => popup(message),
        requestSettingsModalClose: (source) => requestClose(source),
        confirmDialog: (message) => confirmAction(message),
      });

      settingsPartnerContactsRuntime = window.SettingsPartnerContactsRuntime?.mount({
        getParameterData,
        getParameterStates,
        getPartnerContactTypes,
        getPartnerContactPhoneTypes,
        getPartnerContactEmailTypes,
        getPartnerContactStateClassMap,
        escapeHtml,
        showPopup: (message) => popup(message),
        syncParameterData: (data) => syncParameterData(data),
        renderParameters: (...args) => renderParameters(...args),
        requestSettingsModalClose: (source) => requestClose(source),
        deleteParameter: (...args) => deleteParameter(...args),
        restoreParameter: (...args) => restoreParameter(...args),
      });

      settingsParametersRuntime = window.SettingsParametersRuntime?.mount({
        getParameterData,
        getParameterTitles,
        getParameterDependencies,
        getParameterStateTypes,
        getParameterStates,
        getParameterFilterKeys,
        getCityParameterType,
        getCityParameterLabel,
        getCityOptions,
        escapeHtml,
        showPopup: (message) => popup(message),
        syncParameterData: (data) => syncParameterData(data),
        renderPartnerContacts: (...args) => settingsPartnerContactsRuntime?.renderPartnerContacts(...args),
        renderLegalEntities: (...args) => settingsLegalEntitiesRuntime?.renderLegalEntities(...args),
        renderItConnectionsTable: (...args) => renderItConnectionsTable(...args),
        confirmDialog: (message) => confirmAction(message),
        getDependencyChain: (...args) => getDependencyChain(...args),
        normalizeDependencyValue: (...args) => normalizeDependencyValue(...args),
        getDependencyLabel: (...args) => getDependencyLabel(...args),
        buildDependencySelect: (...args) => buildDependencySelect(...args),
        refreshParameterDependencyControls: (...args) => refreshParameterDependencyControls(...args),
        collectDependencyValues: (...args) => collectDependencyValues(...args),
      });
    }

    function initialize() {
      mountNestedRuntimes();
      bindLegalEntitiesEvents();
      renderParameters();
      renderCityCard();
      renderItConnectionsTable();
      loadItEquipment();
    }

    async function loadParameters() {
      try {
        state.parametersLoaded = false;
        const response = await fetch('/api/settings/parameters');
        if (!response.ok) {
          throw new Error('Ошибка загрузки параметров');
        }
        const data = await response.json();
        syncParameterData((data && data.data) || data || state.parameterData);
        state.parametersLoaded = true;
        renderParameters({ forceBodies: true });
        renderCityCard();
        renderItConnectionsTable();
        if (typeof options.buildLocationsTree === 'function') {
          options.buildLocationsTree();
        }
        renderCurrentParameterModal();
      } catch (error) {
        state.parametersLoaded = false;
        console.error('Ошибка загрузки параметров:', error);
        const message = error && error.message ? error.message : error;
        popup('❌ Не удалось загрузить параметры: ' + message);
      }
    }

    return {
      getParameterData,
      initialize,
      isParametersLoaded,
      loadParameters,
      renderCityCard,
      renderCurrentParameterModal,
      renderItConnectionsTable,
      renderParameters,
      syncParameterData,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsParametersShellRuntime) {
        return window.__settingsParametersShellRuntime;
      }
      const runtime = createRuntime(options);
      runtime.initialize();
      window.__settingsParametersShellRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsParametersShellRuntime = Object.freeze(api);
}());

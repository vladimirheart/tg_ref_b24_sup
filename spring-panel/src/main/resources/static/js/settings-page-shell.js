(function () {
  if (window.__settingsPageShellInitialized) {
    return;
  }
  window.__settingsPageShellInitialized = true;

  function isSettingsPage() {
    return document.querySelector('[data-settings-page-shell]') instanceof HTMLElement;
  }

  function initThemeFormSync() {
    const form = document.querySelector('[data-theme-form]');
    if (!(form instanceof HTMLElement) || !window.iguanaTheme || !window.iguanaThemePalette) {
      return;
    }

    const themeRadios = Array.from(form.querySelectorAll('input[name="themeOption"]'));
    const paletteRadios = Array.from(form.querySelectorAll('input[name="themePaletteOption"]'));

    const syncTheme = (theme) => {
      const normalized = typeof theme === 'string' ? theme : window.iguanaTheme.get();
      themeRadios.forEach((radio) => {
        radio.checked = radio.value === normalized;
      });
    };

    const syncPalette = (palette) => {
      const normalized = typeof palette === 'string' ? palette : window.iguanaThemePalette.get();
      paletteRadios.forEach((radio) => {
        radio.checked = radio.value === normalized;
      });
    };

    syncTheme(window.iguanaTheme.get());
    syncPalette(window.iguanaThemePalette.get());

    form.addEventListener('change', (event) => {
      const target = event.target;
      if (!(target instanceof HTMLInputElement)) {
        return;
      }
      if (target.name === 'themeOption') {
        window.iguanaTheme.set(target.value);
      }
      if (target.name === 'themePaletteOption') {
        window.iguanaThemePalette.set(target.value);
      }
    });

    document.addEventListener('theme:change', (event) => {
      const detail = event && event.detail ? event.detail : {};
      syncTheme(detail.theme || window.iguanaTheme.get());
      syncPalette(detail.palette || window.iguanaThemePalette.get());
    });

    document.addEventListener('theme:palette-change', (event) => {
      const detail = event && event.detail ? event.detail : {};
      syncPalette(detail.palette || window.iguanaThemePalette.get());
    });
  }

  function initSettingsTileDescriptions() {
    const tiles = document.querySelectorAll('.settings-tiles > .col .settings-tile[role="button"]');
    tiles.forEach((tile) => {
      if (!(tile instanceof HTMLElement)) {
        return;
      }
      if (!tile.hasAttribute('tabindex')) {
        tile.setAttribute('tabindex', '0');
      }
      tile.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' && event.key !== ' ') {
          return;
        }
        event.preventDefault();
        tile.click();
      });
    });
  }

  function updateSettingsModalBodyLock() {
    const openModals = Array.from(document.querySelectorAll('.modal.show'))
      .filter((modal) => modal instanceof HTMLElement);
    if (openModals.length) {
      const scrollbarWidth = Math.max(0, window.innerWidth - document.documentElement.clientWidth);
      document.body.classList.add('modal-open');
      document.body.style.overflow = 'hidden';
      if (scrollbarWidth > 0) {
        document.body.style.paddingRight = `${scrollbarWidth}px`;
      }
      return;
    }
    document.body.classList.remove('modal-open');
    document.body.style.removeProperty('overflow');
    document.body.style.removeProperty('padding-right');
  }

  function getSettingsModalBaseZIndex(modal) {
    if (!(modal instanceof HTMLElement)) {
      return 1055;
    }
    const cached = Number.parseInt(modal.dataset.settingsModalBaseZIndex || '', 10);
    if (Number.isFinite(cached) && cached > 0) {
      return cached;
    }
    const computed = Number.parseInt(window.getComputedStyle(modal).zIndex || '', 10);
    const baseZIndex = Number.isFinite(computed) && computed > 0 ? computed : 1055;
    modal.dataset.settingsModalBaseZIndex = String(baseZIndex);
    return baseZIndex;
  }

  function getSettingsTopModalZIndex(excludeModal) {
    return Array.from(document.querySelectorAll('.modal.show, .modal-backdrop'))
      .filter((element) => element instanceof HTMLElement && element !== excludeModal)
      .reduce((maxZIndex, element) => {
        const zIndex = Number.parseInt(window.getComputedStyle(element).zIndex || '', 10);
        return Number.isFinite(zIndex) ? Math.max(maxZIndex, zIndex) : maxZIndex;
      }, 0);
  }

  function elevateSettingsModal(modal) {
    if (!(modal instanceof HTMLElement) || !document.body) {
      return;
    }
    document.body.appendChild(modal);
    const baseZIndex = getSettingsModalBaseZIndex(modal);
    const topZIndex = getSettingsTopModalZIndex(modal);
    const nextZIndex = Math.max(baseZIndex, topZIndex + 10);
    modal.style.zIndex = String(nextZIndex);
    window.setTimeout(() => {
      const backdrops = Array.from(document.querySelectorAll('.modal-backdrop'))
        .filter((backdrop) => backdrop instanceof HTMLElement && !backdrop.dataset.settingsModalManaged);
      const backdrop = backdrops[backdrops.length - 1];
      if (!(backdrop instanceof HTMLElement)) {
        return;
      }
      backdrop.dataset.settingsModalManaged = 'true';
      backdrop.style.zIndex = String(nextZIndex - 5);
    }, 0);
  }

  function resetSettingsModalElevation(modal) {
    if (!(modal instanceof HTMLElement)) {
      return;
    }
    modal.style.removeProperty('z-index');
  }

  function resolveSettingsModalElement(target) {
    if (target instanceof HTMLElement) {
      return target;
    }
    if (typeof target === 'string' && target.trim()) {
      const modalId = target.trim().startsWith('#') ? target.trim().slice(1) : target.trim();
      return document.getElementById(modalId);
    }
    return null;
  }

  function getSettingsModalInstance(target) {
    if (typeof bootstrap === 'undefined' || !bootstrap.Modal) {
      return null;
    }
    const modalEl = resolveSettingsModalElement(target);
    if (!(modalEl instanceof HTMLElement)) {
      return null;
    }
    return bootstrap.Modal.getOrCreateInstance(modalEl);
  }

  function getSettingsCollapseInstance(target, options = { toggle: false }) {
    if (typeof bootstrap === 'undefined' || !bootstrap.Collapse) {
      return null;
    }
    const collapseEl = resolveSettingsModalElement(target);
    if (!(collapseEl instanceof HTMLElement)) {
      return null;
    }
    return bootstrap.Collapse.getOrCreateInstance(collapseEl, options);
  }

  function showSettingsCollapse(target, options = { toggle: false }) {
    const collapse = getSettingsCollapseInstance(target, options);
    collapse?.show();
    return collapse;
  }

  function getSettingsTabInstance(target) {
    if (typeof bootstrap === 'undefined' || !bootstrap.Tab) {
      return null;
    }
    const tabEl = resolveSettingsModalElement(target);
    if (!(tabEl instanceof HTMLElement)) {
      return null;
    }
    return bootstrap.Tab.getOrCreateInstance(tabEl);
  }

  function showSettingsTab(target) {
    const tab = getSettingsTabInstance(target);
    tab?.show();
    return tab;
  }

  function showSettingsModal(target) {
    const modal = getSettingsModalInstance(target);
    modal?.show();
    return modal;
  }

  function hideSettingsModal(target) {
    const modalEl = resolveSettingsModalElement(target);
    if (!(modalEl instanceof HTMLElement) || typeof bootstrap === 'undefined' || !bootstrap.Modal) {
      return null;
    }
    const modal = bootstrap.Modal.getInstance(modalEl);
    modal?.hide();
    return modal;
  }

  function resolveSettingsTileForModal(modal) {
    if (!(modal instanceof HTMLElement) || !modal.id) {
      return null;
    }
    return document.querySelector(`.settings-tile[data-bs-target="#${modal.id}"]`);
  }

  function syncSettingsSheetToggle(modal, toggle) {
    if (!(modal instanceof HTMLElement) || !(toggle instanceof HTMLElement)) {
      return;
    }
    const expanded = modal.classList.contains('is-expanded');
    toggle.setAttribute('aria-pressed', expanded ? 'true' : 'false');
    toggle.setAttribute('aria-label', expanded ? 'Свернуть панель' : 'Развернуть панель');
    toggle.innerHTML = expanded
      ? '<i class="bi bi-arrows-angle-contract" aria-hidden="true"></i>'
      : '<i class="bi bi-arrows-angle-expand" aria-hidden="true"></i>';
  }

  function initSettingsPrimaryModals() {
    const primaryModals = Array.from(document.querySelectorAll('[data-settings-primary-modal]'));
    primaryModals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      modal.addEventListener('shown.bs.modal', () => {
        const tile = resolveSettingsTileForModal(modal);
        tile?.classList.add('is-active');
        updateSettingsModalBodyLock();
      });

      modal.addEventListener('hidden.bs.modal', () => {
        const tile = resolveSettingsTileForModal(modal);
        tile?.classList.remove('is-active');
        updateSettingsModalBodyLock();
      });
    });

    const sheetModals = Array.from(document.querySelectorAll('[data-settings-sheet]'));
    sheetModals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      const header = modal.querySelector('.modal-header');
      const closeButton = header ? header.querySelector('.btn-close') : null;
      if (header && closeButton && !header.querySelector('[data-settings-sheet-toggle]')) {
        const actions = document.createElement('div');
        actions.className = 'settings-sheet-actions';

        const expandButton = document.createElement('button');
        expandButton.type = 'button';
        expandButton.className = 'settings-sheet-toggle';
        expandButton.setAttribute('data-settings-sheet-toggle', '');
        syncSettingsSheetToggle(modal, expandButton);
        expandButton.addEventListener('click', () => {
          modal.classList.toggle('is-expanded');
          syncSettingsSheetToggle(modal, expandButton);
        });

        actions.appendChild(expandButton);
        actions.appendChild(closeButton);
        header.appendChild(actions);
      }

      modal.addEventListener('hidden.bs.modal', () => {
        modal.classList.remove('is-expanded');
        const toggle = modal.querySelector('[data-settings-sheet-toggle]');
        syncSettingsSheetToggle(modal, toggle);
      });
    });

    document.addEventListener('shown.bs.modal', () => {
      window.requestAnimationFrame(updateSettingsModalBodyLock);
    });
    document.addEventListener('hidden.bs.modal', () => {
      window.requestAnimationFrame(updateSettingsModalBodyLock);
    });
    document.addEventListener('show.bs.modal', (event) => {
      elevateSettingsModal(event.target);
    });
    document.addEventListener('hidden.bs.modal', (event) => {
      resetSettingsModalElevation(event.target);
    });
  }

  function initAuthManagementModalShell() {
    const usersModalEl = document.getElementById('usersModal');
    if (!(usersModalEl instanceof HTMLElement)) {
      return;
    }

    usersModalEl.addEventListener('shown.bs.modal', () => {
      const container = usersModalEl.querySelector('[data-auth-management]');
      if (!(container instanceof HTMLElement) || !window.AuthManagement) {
        console.error('Модуль управления доступом недоступен.');
        return;
      }
      if (!container.__authManager) {
        container.__authManager = window.AuthManagement.mount(container);
      } else {
        container.__authManager.refresh();
      }
    });

    usersModalEl.addEventListener('hidden.bs.modal', () => {
      const container = usersModalEl.querySelector('[data-auth-management]');
      const manager = container && container.__authManager;
      if (manager) {
        manager.resetPasswords();
        manager.clearMessage();
      }
    });
  }

  function initSettingsBodyPortals() {
    if (!document.body) {
      return;
    }
    const portaledModals = Array.from(document.querySelectorAll('[data-settings-portal-body]'));
    portaledModals.forEach((modal) => {
      if (!(modal instanceof HTMLElement) || modal.parentElement === document.body) {
        return;
      }
      document.body.appendChild(modal);
    });
  }

  function setSettingsParentModalSuspended(parentModalEl, className, suspended) {
    if (!(parentModalEl instanceof HTMLElement)) {
      return;
    }
    const nextState = Boolean(suspended);
    if (className) {
      parentModalEl.classList.toggle(className, nextState);
    }
    if (nextState) {
      parentModalEl.setAttribute('aria-hidden', 'true');
      parentModalEl.inert = true;
    } else {
      parentModalEl.removeAttribute('aria-hidden');
      parentModalEl.inert = false;
    }
  }

  function initParentChildSuspendShell() {
    const childModals = Array.from(document.querySelectorAll('[data-settings-suspend-parent]'));
    childModals.forEach((childModal) => {
      if (!(childModal instanceof HTMLElement)) {
        return;
      }
      const parentId = String(childModal.dataset.settingsSuspendParent || '').trim();
      const openClass = String(childModal.dataset.settingsSuspendClass || '').trim();
      if (!parentId) {
        return;
      }
      const parentModalEl = document.getElementById(parentId);
      if (!(parentModalEl instanceof HTMLElement)) {
        return;
      }

      childModal.addEventListener('show.bs.modal', () => {
        setSettingsParentModalSuspended(parentModalEl, openClass, true);
      });
      childModal.addEventListener('hidden.bs.modal', () => {
        setSettingsParentModalSuspended(parentModalEl, openClass, false);
      });
    });
  }

  function invokeSettingsLifecycleCallback(callbackName, modal, eventName) {
    const normalizedName = typeof callbackName === 'string' ? callbackName.trim() : '';
    if (!normalizedName) {
      return;
    }
    const callback = window[normalizedName];
    if (typeof callback !== 'function') {
      console.warn(`Settings modal callback "${normalizedName}" is not available for ${eventName}.`);
      return;
    }
    try {
      const result = callback(modal);
      if (result && typeof result.then === 'function') {
        result.catch((error) => {
          console.error(`Settings modal callback "${normalizedName}" failed for ${eventName}.`, error);
        });
      }
    } catch (error) {
      console.error(`Settings modal callback "${normalizedName}" failed for ${eventName}.`, error);
    }
  }

  function initSettingsModalLifecycleHooks() {
    const lifecycleEvents = [
      { eventName: 'show.bs.modal', dataKey: 'settingsOnShow' },
      { eventName: 'shown.bs.modal', dataKey: 'settingsOnShown' },
      { eventName: 'hide.bs.modal', dataKey: 'settingsOnHide' },
      { eventName: 'hidden.bs.modal', dataKey: 'settingsOnHidden' },
    ];

    const modals = Array.from(document.querySelectorAll('.modal'));
    modals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      lifecycleEvents.forEach(({ eventName, dataKey }) => {
        const callbackName = modal.dataset[dataKey];
        if (!callbackName) {
          return;
        }
        modal.addEventListener(eventName, () => {
          invokeSettingsLifecycleCallback(callbackName, modal, eventName);
        });
      });
    });
  }

  function initSettingsModalDefaultTabs() {
    const modals = Array.from(document.querySelectorAll('[data-settings-reset-tab]'));
    modals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      const tabId = String(modal.dataset.settingsResetTab || '').trim();
      if (!tabId) {
        return;
      }
      modal.addEventListener('show.bs.modal', () => {
        showSettingsTab(tabId);
      });
    });
  }

  function replaceSettingsHistorySearch(params) {
    const search = params.toString();
    const nextUrl = `${window.location.pathname}${search ? `?${search}` : ''}${window.location.hash || ''}`;
    window.history.replaceState({}, '', nextUrl);
  }

  function openSettingsQueryDrivenModals(params) {
    if (typeof bootstrap === 'undefined') {
      return false;
    }
    let changed = false;
    const queryDrivenModals = Array.from(document.querySelectorAll('[data-settings-query-open]'));
    queryDrivenModals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      const queryParam = String(modal.dataset.settingsQueryParam || 'open').trim() || 'open';
      const expectedValue = String(modal.dataset.settingsQueryOpen || '').trim().toLowerCase();
      const actualValue = String(params.get(queryParam) || '').trim().toLowerCase();
      if (!expectedValue || actualValue !== expectedValue) {
        return;
      }
      showSettingsModal(modal);
      if (Object.prototype.hasOwnProperty.call(modal.dataset, 'settingsQueryClearParam')) {
        const clearParam = String(modal.dataset.settingsQueryClearParam || queryParam).trim() || queryParam;
        if (params.has(clearParam)) {
          params.delete(clearParam);
          changed = true;
        }
      }
    });
    return changed;
  }

  function runSettingsDomainBootstrap() {
    const bootstrapFns = [
      'initClientStatuses',
      'initBusinessStylesEditor',
      'initAutoCloseTemplates',
      'initDialogTemplates',
      'initTimeMetricsControls',
      'initDialogSlaControls',
      'initWorkspaceGovernanceUtcTimestampFields',
      'initExternalKpiUtcTimestampFields',
      'initWorkspaceSingleModeControls',
      'initWorkspaceExternalKpiDatamartContractPreview',
      'bindAutoAssignRulesHelpers',
      'initDialogStatusBadges',
      'initLocationWizard',
      'renderLocationsIikoServerSourcesEditor',
      'renderLocationsIikoSyncSettings',
      'loadLocationsSyncStatus',
      'buildLocationsTree',
      'initChannelsManagement',
      'loadParameters',
      'renderNetworkProfiles',
    ];

    bootstrapFns.forEach((fnName) => {
      const fn = window[fnName];
      if (typeof fn === 'function') {
        fn();
      }
    });
  }

  function initLocationsModalShell() {
    const locationsModalEl = document.querySelector('[data-settings-locations-modal]')
      || document.getElementById('locationsModal');
    if (!(locationsModalEl instanceof HTMLElement)) {
      return;
    }

    locationsModalEl.addEventListener('shown.bs.modal', () => {
      if (typeof window.renderLocationsIikoServerSourcesEditor === 'function') {
        window.renderLocationsIikoServerSourcesEditor();
      }
      if (typeof window.renderLocationsIikoSyncSettings === 'function') {
        window.renderLocationsIikoSyncSettings();
      }
      if (typeof window.loadLocationsSyncStatus === 'function') {
        window.loadLocationsSyncStatus().then((status) => {
          const running = Boolean(status && (status.running || String(status.state || '').toLowerCase() === 'running'));
          if (running && typeof window.startLocationsSyncStatusPolling === 'function') {
            window.startLocationsSyncStatusPolling();
          }
        });
      }
    });

    locationsModalEl.addEventListener('hidden.bs.modal', () => {
      if (typeof window.stopLocationsSyncStatusPolling === 'function') {
        window.stopLocationsSyncStatusPolling();
      }
    });
  }

  function openRequestedSettingsModalFromUrl() {
    try {
      const params = new URLSearchParams(window.location.search);
      const openTarget = (params.get('open') || '').trim().toLowerCase();
      const legacyTab = (params.get('tab') || '').trim().toLowerCase();
      const requestedModal = openTarget || legacyTab;
      let shouldReplaceHistory = false;
      if (!requestedModal || typeof bootstrap === 'undefined') {
        shouldReplaceHistory = openSettingsQueryDrivenModals(params);
      } else {
        const modalEl = Array.from(document.querySelectorAll('[data-settings-url-modal]'))
          .find((modal) => modal instanceof HTMLElement
            && String(modal.dataset.settingsUrlModal || '').trim().toLowerCase() === requestedModal);
        if (modalEl) {
          showSettingsModal(modalEl);
        }
        shouldReplaceHistory = openSettingsQueryDrivenModals(params) || shouldReplaceHistory;
      }
      if (shouldReplaceHistory) {
        replaceSettingsHistorySearch(params);
      }
    } catch (error) {
      console.error('Failed to process URL parameters', error);
    }
  }

  function initSettingsPageShell() {
    if (!isSettingsPage()) {
      return;
    }
    initThemeFormSync();
    initSettingsTileDescriptions();
    initSettingsBodyPortals();
    initSettingsPrimaryModals();
    initParentChildSuspendShell();
    initSettingsModalLifecycleHooks();
    initSettingsModalDefaultTabs();
    runSettingsDomainBootstrap();
    openRequestedSettingsModalFromUrl();
    if (typeof window.initReporting === 'function') {
      window.initReporting();
    }
    updateSettingsModalBodyLock();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSettingsPageShell, { once: true });
  } else {
    initSettingsPageShell();
  }

  window.SettingsPageShell = Object.assign(window.SettingsPageShell || {}, {
    getCollapseInstance: getSettingsCollapseInstance,
    getModalInstance: getSettingsModalInstance,
    getTabInstance: getSettingsTabInstance,
    showCollapse: showSettingsCollapse,
    showModal: showSettingsModal,
    showTab: showSettingsTab,
    hideModal: hideSettingsModal,
  });
})();

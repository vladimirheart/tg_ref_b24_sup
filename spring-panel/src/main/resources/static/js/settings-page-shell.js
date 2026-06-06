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
        if (typeof bootstrap === 'undefined' || !bootstrap.Tab) {
          return;
        }
        const tabButton = document.getElementById(tabId);
        if (tabButton instanceof HTMLElement) {
          bootstrap.Tab.getOrCreateInstance(tabButton).show();
        }
      });
    });
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
      if (!requestedModal || typeof bootstrap === 'undefined') {
        return;
      }
      const modalEl = Array.from(document.querySelectorAll('[data-settings-url-modal]'))
        .find((modal) => modal instanceof HTMLElement
          && String(modal.dataset.settingsUrlModal || '').trim().toLowerCase() === requestedModal);
      if (modalEl) {
        bootstrap.Modal.getOrCreateInstance(modalEl).show();
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
    initSettingsPrimaryModals();
    initAuthManagementModalShell();
    initParentChildSuspendShell();
    initSettingsModalLifecycleHooks();
    initSettingsModalDefaultTabs();
    runSettingsDomainBootstrap();
    initLocationsModalShell();
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
})();

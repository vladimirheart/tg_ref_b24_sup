(function () {
  if (window.__settingsPageShellInitialized) {
    return;
  }
  window.__settingsPageShellInitialized = true;

  const DEFAULT_SETTINGS_BOOTSTRAP_FUNCTIONS = [
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
    'initReporting',
  ];

  const DEFAULT_SETTINGS_MODAL_LIFECYCLE_CALLBACKS = Object.freeze({
    usersModal: Object.freeze({
      shown: 'mountAuthManagementSettingsModal',
      hidden: 'resetAuthManagementSettingsModal',
    }),
    locationsModal: Object.freeze({
      shown: 'prepareLocationsSettingsModal',
      hidden: 'resetLocationsSettingsModal',
    }),
    locationWizardModal: Object.freeze({
      hidden: 'resetLocationWizardSettingsModal',
    }),
    parametersModal: Object.freeze({
      show: 'renderPartnerContactsSettingsModal',
      hidden: 'resetPartnerContactsSettingsModal',
    }),
    parameterItemsModal: Object.freeze({
      hidden: 'resetParameterItemsSettingsModal',
    }),
    legalEntitiesModal: Object.freeze({
      show: 'renderLegalEntitiesSettingsModal',
      hidden: 'resetLegalEntitiesSettingsModal',
    }),
    partnerContactEditorModal: Object.freeze({
      hidden: 'resetPartnerContactEditorSettingsModal',
    }),
    networkProfileEditorModal: Object.freeze({
      hidden: 'resetNetworkProfileSettingsModal',
    }),
    integrationNetworkProfileEditorModal: Object.freeze({
      hidden: 'resetIntegrationNetworkProfileSettingsModal',
    }),
    itConnectionAddModal: Object.freeze({
      show: 'prepareItConnectionAddSettingsModal',
    }),
    itEquipmentAddModal: Object.freeze({
      show: 'prepareItEquipmentAddSettingsModal',
    }),
    reportingModal: Object.freeze({
      show: 'prepareReportingSettingsModal',
    }),
    managerBindingsModal: Object.freeze({
      show: 'prepareManagerBindingsSettingsModal',
    }),
    addChannelModal: Object.freeze({
      show: 'prepareAddChannelSettingsModal',
    }),
    channelEditorModal: Object.freeze({
      hidden: 'resetChannelEditorSettingsModal',
    }),
  });

  function getSettingsShellRoot() {
    const root = document.querySelector('[data-settings-page-shell]');
    return root instanceof HTMLElement ? root : null;
  }

  function isSettingsPage() {
    return getSettingsShellRoot() instanceof HTMLElement;
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
    const tiles = document.querySelectorAll('[data-settings-tile]');
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
      tile.addEventListener('click', (event) => {
        const target = event.target;
        if (target instanceof Element) {
          const interactiveTarget = target.closest('a, button, input, select, textarea, [data-bs-dismiss]');
          if (interactiveTarget && interactiveTarget !== tile) {
            return;
          }
        }
        const modalId = String(tile.dataset.settingsTileTarget || '').trim();
        if (!modalId) {
          return;
        }
        event.preventDefault();
        showSettingsModal(modalId);
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

  function resolveClosestSettingsModalElement(target) {
    if (!(target instanceof HTMLElement)) {
      return null;
    }
    return target.closest('.modal');
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

  function resolveSettingsCollapseTarget(trigger) {
    if (!(trigger instanceof HTMLElement)) {
      return null;
    }
    const rawTarget = String(trigger.dataset.settingsOpenCollapse || '').trim();
    if (!rawTarget) {
      return null;
    }
    return resolveSettingsModalElement(rawTarget);
  }

  function syncSettingsCollapseNavState(navRoot, activeCollapseEl) {
    if (!(navRoot instanceof HTMLElement)) {
      return;
    }
    const activeClass = String(navRoot.dataset.settingsCollapseActiveClass || 'is-active').trim() || 'is-active';
    const triggers = Array.from(navRoot.querySelectorAll('[data-settings-open-collapse]'));
    triggers.forEach((trigger) => {
      if (!(trigger instanceof HTMLElement)) {
        return;
      }
      const target = resolveSettingsCollapseTarget(trigger);
      const isActive = target instanceof HTMLElement && target === activeCollapseEl;
      trigger.classList.toggle(activeClass, isActive);
      if (trigger.getAttribute('role') === 'button') {
        trigger.setAttribute('aria-pressed', isActive ? 'true' : 'false');
      }
    });
  }

  function initSettingsCollapseNavs() {
    const navRoots = Array.from(document.querySelectorAll('[data-settings-collapse-nav]'));
    navRoots.forEach((navRoot) => {
      if (!(navRoot instanceof HTMLElement)) {
        return;
      }
      const triggers = Array.from(navRoot.querySelectorAll('[data-settings-open-collapse]'));
      const collapseTargets = new Set();

      triggers.forEach((trigger) => {
        if (!(trigger instanceof HTMLElement)) {
          return;
        }
        if (!(trigger instanceof HTMLButtonElement)) {
          if (!trigger.hasAttribute('role')) {
            trigger.setAttribute('role', 'button');
          }
          if (!trigger.hasAttribute('tabindex')) {
            trigger.setAttribute('tabindex', '0');
          }
        }
        if (trigger.getAttribute('role') === 'button' && !trigger.hasAttribute('aria-pressed')) {
          trigger.setAttribute('aria-pressed', 'false');
        }
        const target = resolveSettingsCollapseTarget(trigger);
        if (target instanceof HTMLElement) {
          collapseTargets.add(target);
        }
      });

      collapseTargets.forEach((collapseEl) => {
        collapseEl.addEventListener('shown.bs.collapse', () => {
          syncSettingsCollapseNavState(navRoot, collapseEl);
        });
      });

      const initiallyShown = Array.from(collapseTargets).find((collapseEl) => collapseEl.classList.contains('show'));
      syncSettingsCollapseNavState(navRoot, initiallyShown || null);
    });
  }

  function runSettingsCollapseAction(trigger) {
    const collapseEl = resolveSettingsCollapseTarget(trigger);
    if (!(collapseEl instanceof HTMLElement)) {
      return false;
    }
    showSettingsCollapse(collapseEl, { toggle: false });
    if (Object.prototype.hasOwnProperty.call(trigger.dataset, 'settingsCollapseScroll')) {
      window.setTimeout(() => {
        collapseEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 50);
    }
    return true;
  }

  function initSettingsCollapseActionTriggers() {
    document.addEventListener('click', (event) => {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const trigger = target.closest('[data-settings-open-collapse]');
      if (!(trigger instanceof HTMLElement) || trigger.hasAttribute('disabled') || trigger.getAttribute('aria-disabled') === 'true') {
        return;
      }
      if (!runSettingsCollapseAction(trigger)) {
        return;
      }
      event.preventDefault();
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const trigger = target.closest('[data-settings-open-collapse]');
      if (!(trigger instanceof HTMLElement) || trigger instanceof HTMLButtonElement) {
        return;
      }
      if (!runSettingsCollapseAction(trigger)) {
        return;
      }
      event.preventDefault();
    });
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

  function hideClosestSettingsModal(target) {
    const modalEl = resolveClosestSettingsModalElement(target);
    if (!(modalEl instanceof HTMLElement)) {
      return null;
    }
    return hideSettingsModal(modalEl);
  }

  function requestCloseSettingsModal(target) {
    if (target instanceof HTMLElement) {
      const closeEvent = new CustomEvent('settings:close-modal', {
        bubbles: true,
        cancelable: true,
      });
      target.dispatchEvent(closeEvent);
      return closeEvent;
    }
    return hideSettingsModal(target);
  }

  function resolveSettingsModalActionTarget(trigger, actionName) {
    if (!(trigger instanceof HTMLElement)) {
      return null;
    }
    const rawTarget = actionName === 'open'
      ? String(trigger.dataset.settingsOpenModal || '').trim()
      : String(trigger.dataset.settingsHideModal || '').trim();
    if (!rawTarget) {
      return null;
    }
    if (rawTarget === 'self') {
      return trigger.closest('.modal');
    }
    return rawTarget;
  }

  function runSettingsModalAction(openTarget, hideTarget) {
    const openModalEl = resolveSettingsModalElement(openTarget);
    const hideModalEl = resolveSettingsModalElement(hideTarget);

    if (hideModalEl instanceof HTMLElement && openModalEl instanceof HTMLElement && hideModalEl !== openModalEl) {
      const hiddenHandler = () => {
        hideModalEl.removeEventListener('hidden.bs.modal', hiddenHandler);
        showSettingsModal(openModalEl);
      };
      hideModalEl.addEventListener('hidden.bs.modal', hiddenHandler);
      const hiddenModal = hideSettingsModal(hideModalEl);
      if (hiddenModal) {
        return;
      }
      hideModalEl.removeEventListener('hidden.bs.modal', hiddenHandler);
    }

    if (hideModalEl instanceof HTMLElement) {
      hideSettingsModal(hideModalEl);
    }
    if (openModalEl instanceof HTMLElement) {
      showSettingsModal(openModalEl);
    }
  }

  function invokeSettingsActionCallback(trigger) {
    if (!(trigger instanceof HTMLElement)) {
      return true;
    }
    const callbackName = String(trigger.dataset.settingsActionCallback || '').trim();
    if (!callbackName) {
      return true;
    }
    const callback = window[callbackName];
    if (typeof callback !== 'function') {
      console.warn(`Settings action callback "${callbackName}" is not available.`);
      return false;
    }
    try {
      return callback(trigger);
    } catch (error) {
      console.error(`Settings action callback "${callbackName}" failed.`, error);
      return false;
    }
  }

  function resolveSettingsCallbackArgument(rawArgument, trigger, event) {
    const argument = typeof rawArgument === 'string' ? rawArgument.trim() : '';
    if (!argument) {
      return '';
    }
    if (argument === '$element' || argument === '$trigger') {
      return trigger;
    }
    if (argument === '$event') {
      return event;
    }
    if (argument === '$target') {
      return event ? event.target : null;
    }
    if (argument === '$value') {
      if (trigger && 'value' in trigger) {
        return trigger.value;
      }
      return '';
    }
    if (argument === '$checked') {
      return Boolean(trigger && 'checked' in trigger && trigger.checked);
    }
    if (argument === 'true') {
      return true;
    }
    if (argument === 'false') {
      return false;
    }
    if (argument === 'null') {
      return null;
    }
    if (/^-?\d+$/.test(argument)) {
      return Number.parseInt(argument, 10);
    }
    if (/^-?\d+\.\d+$/.test(argument)) {
      return Number.parseFloat(argument);
    }
    return argument;
  }

  function invokeSettingsNamedCallback(callbackName, trigger, event, options = {}) {
    const normalizedName = typeof callbackName === 'string' ? callbackName.trim() : '';
    if (!normalizedName) {
      return true;
    }
    const callback = window[normalizedName];
    if (typeof callback !== 'function') {
      console.warn(`Settings callback "${normalizedName}" is not available.`);
      return false;
    }
    const rawArgs = typeof options.args === 'string' ? options.args.trim() : '';
    const args = rawArgs
      ? rawArgs
        .split(',')
        .map((argument) => resolveSettingsCallbackArgument(argument, trigger, event))
      : [];
    try {
      return callback(...args);
    } catch (error) {
      console.error(`Settings callback "${normalizedName}" failed.`, error);
      return false;
    }
  }

  function continueSettingsNamedCallback(callbackName, trigger, event, options = {}) {
    const result = invokeSettingsNamedCallback(callbackName, trigger, event, options);
    if (result && typeof result.then === 'function') {
      result.catch((error) => {
        console.error(`Settings callback "${callbackName}" promise failed.`, error);
      });
    }
    return result;
  }

  function initSettingsDeclarativeCallbacks() {
    const bindings = [
      { eventName: 'click', selector: '[data-settings-click-callback]' },
      { eventName: 'change', selector: '[data-settings-change-callback]' },
      { eventName: 'input', selector: '[data-settings-input-callback]' },
    ];

    bindings.forEach(({ eventName, selector }) => {
      document.addEventListener(eventName, (event) => {
        const target = event.target;
        if (!(target instanceof Element)) {
          return;
        }
        const trigger = target.closest(selector);
        if (!(trigger instanceof HTMLElement)) {
          return;
        }
        if ((eventName === 'click')
          && (trigger.hasAttribute('disabled') || trigger.getAttribute('aria-disabled') === 'true')) {
          return;
        }
        const callbackName = trigger.dataset[`settings${eventName.charAt(0).toUpperCase()}${eventName.slice(1)}Callback`];
        if (!callbackName) {
          return;
        }
        continueSettingsNamedCallback(callbackName, trigger, event, {
          args: trigger.dataset.settingsCallbackArgs,
        });
      });
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const trigger = target.closest('[data-settings-click-callback]');
      if (!(trigger instanceof HTMLElement) || trigger instanceof HTMLButtonElement) {
        return;
      }
      if (trigger.hasAttribute('disabled') || trigger.getAttribute('aria-disabled') === 'true') {
        return;
      }
      const callbackName = String(trigger.dataset.settingsClickCallback || '').trim();
      if (!callbackName) {
        return;
      }
      event.preventDefault();
      continueSettingsNamedCallback(callbackName, trigger, event, {
        args: trigger.dataset.settingsCallbackArgs,
      });
    });
  }

  function continueSettingsModalAction(trigger, openTarget, hideTarget) {
    const callbackResult = invokeSettingsActionCallback(trigger);
    if (callbackResult && typeof callbackResult.then === 'function') {
      callbackResult
        .then((resolved) => {
          if (resolved === false) {
            return;
          }
          runSettingsModalAction(openTarget, hideTarget);
        })
        .catch((error) => {
          console.error('Settings action callback promise failed.', error);
        });
      return;
    }
    if (callbackResult === false) {
      return;
    }
    runSettingsModalAction(openTarget, hideTarget);
  }

  function initSettingsModalActionTriggers() {
    document.addEventListener('click', (event) => {
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const trigger = target.closest('[data-settings-open-modal], [data-settings-hide-modal]');
      if (!(trigger instanceof HTMLElement) || trigger.hasAttribute('disabled') || trigger.getAttribute('aria-disabled') === 'true') {
        return;
      }
      const openTarget = resolveSettingsModalActionTarget(trigger, 'open');
      const hideTarget = resolveSettingsModalActionTarget(trigger, 'hide');
      const hasActionCallback = Boolean(String(trigger.dataset.settingsActionCallback || '').trim());
      if (!openTarget && !hideTarget && !hasActionCallback) {
        return;
      }
      event.preventDefault();
      continueSettingsModalAction(trigger, openTarget, hideTarget);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      const target = event.target;
      if (!(target instanceof Element)) {
        return;
      }
      const trigger = target.closest('[data-settings-open-modal], [data-settings-hide-modal]');
      if (!(trigger instanceof HTMLElement) || trigger instanceof HTMLButtonElement) {
        return;
      }
      if (trigger.hasAttribute('disabled') || trigger.getAttribute('aria-disabled') === 'true') {
        return;
      }
      const openTarget = resolveSettingsModalActionTarget(trigger, 'open');
      const hideTarget = resolveSettingsModalActionTarget(trigger, 'hide');
      const hasActionCallback = Boolean(String(trigger.dataset.settingsActionCallback || '').trim());
      if (!openTarget && !hideTarget && !hasActionCallback) {
        return;
      }
      event.preventDefault();
      continueSettingsModalAction(trigger, openTarget, hideTarget);
    });
  }

  function resolveSettingsTileForModal(modal) {
    if (!(modal instanceof HTMLElement) || !modal.id) {
      return null;
    }
    return document.querySelector(`[data-settings-tile-target="${modal.id}"]`);
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

      parentModalEl.addEventListener('hide.bs.modal', () => {
        hideSettingsModal(childModal);
        setSettingsParentModalSuspended(parentModalEl, openClass, false);
      });
      parentModalEl.addEventListener('hidden.bs.modal', () => {
        setSettingsParentModalSuspended(parentModalEl, openClass, false);
      });
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

  function resolveSettingsLifecycleCallbackName(modal, dataKey, eventKey) {
    if (!(modal instanceof HTMLElement)) {
      return '';
    }
    const inlineCallbackName = String(modal.dataset[dataKey] || '').trim();
    if (inlineCallbackName) {
      return inlineCallbackName;
    }
    if (!modal.id) {
      return '';
    }
    const modalCallbacks = DEFAULT_SETTINGS_MODAL_LIFECYCLE_CALLBACKS[modal.id];
    if (!modalCallbacks || typeof modalCallbacks !== 'object') {
      return '';
    }
    return String(modalCallbacks[eventKey] || '').trim();
  }

  function initSettingsModalLifecycleHooks() {
    const lifecycleEvents = [
      { eventName: 'show.bs.modal', dataKey: 'settingsOnShow', eventKey: 'show' },
      { eventName: 'shown.bs.modal', dataKey: 'settingsOnShown', eventKey: 'shown' },
      { eventName: 'hide.bs.modal', dataKey: 'settingsOnHide', eventKey: 'hide' },
      { eventName: 'hidden.bs.modal', dataKey: 'settingsOnHidden', eventKey: 'hidden' },
    ];

    const modals = Array.from(document.querySelectorAll('.modal'));
    modals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      lifecycleEvents.forEach(({ eventName, dataKey, eventKey }) => {
        const callbackName = resolveSettingsLifecycleCallbackName(modal, dataKey, eventKey);
        if (!callbackName) {
          return;
        }
        modal.addEventListener(eventName, () => {
          invokeSettingsLifecycleCallback(callbackName, modal, eventName);
        });
      });
    });
  }

  function initSettingsModalCloseRequests() {
    document.addEventListener('settings:close-modal', (event) => {
      const target = event.target;
      if (!(target instanceof HTMLElement)) {
        return;
      }
      event.preventDefault();
      hideClosestSettingsModal(target);
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

  function resolveSettingsFocusTarget(modal) {
    if (!(modal instanceof HTMLElement)) {
      return null;
    }
    const rawSelector = String(modal.dataset.settingsFocusTarget || '').trim();
    if (!rawSelector) {
      return null;
    }
    const candidates = Array.from(modal.querySelectorAll(rawSelector));
    return candidates.find((candidate) => {
      if (!(candidate instanceof HTMLElement)) {
        return false;
      }
      if ('disabled' in candidate && candidate.disabled) {
        return false;
      }
      if (candidate.getAttribute('aria-hidden') === 'true') {
        return false;
      }
      return true;
    }) || null;
  }

  function initSettingsModalFocusTargets() {
    const modals = Array.from(document.querySelectorAll('[data-settings-focus-target]'));
    modals.forEach((modal) => {
      if (!(modal instanceof HTMLElement)) {
        return;
      }
      modal.addEventListener('shown.bs.modal', () => {
        const target = resolveSettingsFocusTarget(modal);
        if (!(target instanceof HTMLElement)) {
          return;
        }
        target.focus();
        if (Object.prototype.hasOwnProperty.call(modal.dataset, 'settingsFocusSelect') && 'select' in target && typeof target.select === 'function') {
          target.select();
        }
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
    const shellRoot = getSettingsShellRoot();
    const bootstrapAttr = shellRoot ? String(shellRoot.dataset.settingsBootstrap || '') : '';
    const bootstrapFns = bootstrapAttr
      ? bootstrapAttr
        .split(',')
        .map((fnName) => fnName.trim())
        .filter(Boolean)
      : DEFAULT_SETTINGS_BOOTSTRAP_FUNCTIONS;

    bootstrapFns.forEach((fnName) => {
      const fn = window[fnName];
      if (typeof fn === 'function') {
        fn();
      }
    });
  }

  function readSettingsUrlRequest(params) {
    const shellRoot = getSettingsShellRoot();
    const openParam = shellRoot
      ? String(shellRoot.dataset.settingsUrlOpenParam || 'open').trim() || 'open'
      : 'open';
    const legacyParam = shellRoot
      ? String(shellRoot.dataset.settingsUrlLegacyParam || 'tab').trim() || 'tab'
      : 'tab';
    const primaryTarget = String(params.get(openParam) || '').trim().toLowerCase();
    const legacyTarget = String(params.get(legacyParam) || '').trim().toLowerCase();
    return primaryTarget || legacyTarget;
  }

  function openRequestedSettingsModalFromUrl() {
    try {
      const params = new URLSearchParams(window.location.search);
      const requestedModal = readSettingsUrlRequest(params);
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
    initSettingsModalActionTriggers();
    initSettingsDeclarativeCallbacks();
    initSettingsCollapseNavs();
    initSettingsCollapseActionTriggers();
    initParentChildSuspendShell();
    initSettingsModalLifecycleHooks();
    initSettingsModalCloseRequests();
    initSettingsModalDefaultTabs();
    initSettingsModalFocusTargets();
    runSettingsDomainBootstrap();
    openRequestedSettingsModalFromUrl();
    updateSettingsModalBodyLock();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSettingsPageShell, { once: true });
  } else {
    initSettingsPageShell();
  }

  window.SettingsPageShell = Object.assign(window.SettingsPageShell || {}, {
    getCollapseInstance: getSettingsCollapseInstance,
    getClosestModalElement: resolveClosestSettingsModalElement,
    getModalInstance: getSettingsModalInstance,
    getTabInstance: getSettingsTabInstance,
    showCollapse: showSettingsCollapse,
    showModal: showSettingsModal,
    showTab: showSettingsTab,
    requestCloseModal: requestCloseSettingsModal,
    hideClosestModal: hideClosestSettingsModal,
    hideModal: hideSettingsModal,
  });
})();

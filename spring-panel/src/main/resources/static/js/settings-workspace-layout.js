(() => {
  const TARGET_MODAL_IDS = [
    'channelsModal',
    'categoriesModal',
    'usersModal',
    'locationsModal',
    'parametersModal',
  ];

  function enhanceWorkspace(modal) {
    if (!(modal instanceof HTMLElement) || modal.dataset.settingsWorkspaceReady === 'true') {
      return;
    }

    const body = modal.querySelector(':scope > .modal-dialog > .modal-content > .modal-body');
    if (!(body instanceof HTMLElement)) {
      return;
    }

    const nav = body.querySelector('.settings-menu-tabs');
    const content = body.querySelector('.settings-tab-content');
    if (!(nav instanceof HTMLElement) || !(content instanceof HTMLElement)) {
      return;
    }

    const host = nav.parentElement;
    if (!(host instanceof HTMLElement) || host !== content.parentElement || !body.contains(host)) {
      modal.dataset.settingsWorkspaceReady = 'unsupported';
      return;
    }

    modal.classList.add('settings-workspace-modal');
    body.classList.add('settings-workspace-body');
    host.classList.add('settings-workspace-host');
    nav.classList.add('settings-workspace-nav');
    content.classList.add('settings-workspace-content');

    let main = host.querySelector(':scope > .settings-workspace-main');
    if (!(main instanceof HTMLElement)) {
      main = document.createElement('div');
      main.className = 'settings-workspace-main';
      host.insertBefore(main, nav);
      main.append(nav, content);
    }

    Array.from(host.children).forEach((child) => {
      if (!(child instanceof HTMLElement) || child === main) {
        return;
      }
      child.classList.add('settings-workspace-wide');
      child.style.removeProperty('grid-row');
      child.style.removeProperty('grid-column');
    });

    nav.style.removeProperty('grid-row');
    nav.style.removeProperty('grid-column');
    content.style.removeProperty('grid-row');
    content.style.removeProperty('grid-column');

    modal.dataset.settingsWorkspaceReady = 'true';
  }

  // Settings workspace expansion v7
  function enhanceItWorkspace(modal) {
    if (!(modal instanceof HTMLElement) || modal.dataset.settingsWorkspaceReady === 'true') {
      return;
    }

    const body = modal.querySelector(':scope > .modal-dialog > .modal-content > .modal-body');
    if (!(body instanceof HTMLElement)) {
      return;
    }

    const nav = body.querySelector('[data-it-settings-tiles-nav]');
    const content = body.querySelector('#itSettingsAccordion');
    if (!(nav instanceof HTMLElement) || !(content instanceof HTMLElement)) {
      modal.dataset.settingsWorkspaceReady = 'unsupported';
      return;
    }

    modal.classList.add('settings-workspace-modal', 'settings-workspace-modal--it');
    body.classList.add('settings-workspace-body', 'settings-workspace-host', 'settings-workspace-host--it');

    let main = body.querySelector(':scope > .settings-workspace-main--it');
    if (!(main instanceof HTMLElement)) {
      main = document.createElement('div');
      main.className = 'settings-workspace-main settings-workspace-main--it';
      body.insertBefore(main, nav);
      main.append(nav, content);
    }

    const lead = body.querySelector(':scope > .settings-modal-lead');
    if (lead instanceof HTMLElement) {
      lead.classList.add('settings-workspace-wide');
    }

    nav.classList.add('settings-workspace-nav--it');
    content.classList.add('settings-workspace-content--it');

    nav.querySelectorAll('[data-it-collapse-tile]').forEach((trigger) => {
      if (!(trigger instanceof HTMLElement)) {
        return;
      }
      trigger.setAttribute('role', 'button');
      trigger.setAttribute('tabindex', '0');
    });

    nav.addEventListener('keydown', (event) => {
      const trigger = event.target instanceof Element
        ? event.target.closest('[data-it-collapse-tile]')
        : null;
      if (!(trigger instanceof HTMLElement)) {
        return;
      }
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      event.preventDefault();
      trigger.click();
    });

    modal.dataset.settingsWorkspaceReady = 'true';
  }
  function init() {
    TARGET_MODAL_IDS.forEach((id) => {
      enhanceWorkspace(document.getElementById(id));
    });    enhanceItWorkspace(document.getElementById('itConnectionsModal'));

  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
(() => {
  const TARGET_MODAL_IDS = [
    'channelsModal',
    'categoriesModal',
    'usersModal',
    'locationsModal',
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

  function init() {
    TARGET_MODAL_IDS.forEach((id) => {
      enhanceWorkspace(document.getElementById(id));
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
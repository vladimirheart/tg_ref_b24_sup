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

    const wideChildren = Array.from(host.children).filter(
      (child) => child !== nav && child !== content,
    );

    wideChildren.forEach((child, index) => {
      if (!(child instanceof HTMLElement)) {
        return;
      }
      child.classList.add('settings-workspace-wide');
      child.style.gridRow = String(index + 1);
    });

    const contentRow = String(wideChildren.length + 1);
    nav.style.gridRow = contentRow;
    content.style.gridRow = contentRow;

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

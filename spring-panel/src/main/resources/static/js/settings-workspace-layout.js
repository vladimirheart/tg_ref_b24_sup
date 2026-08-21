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
  // Settings input formatting workspace v8
  function enhanceInputFormattingWorkspace(modal) {
    if (!(modal instanceof HTMLElement) || modal.dataset.settingsWorkspaceReady === 'true') {
      return;
    }

    const body = modal.querySelector(':scope > .modal-dialog > .modal-content > .modal-body');
    if (!(body instanceof HTMLElement)) {
      return;
    }

    const sections = Array.from(body.children).filter(
      (child) => child instanceof HTMLElement && child.classList.contains('card'),
    );
    if (sections.length < 2) {
      modal.dataset.settingsWorkspaceReady = 'unsupported';
      return;
    }

    const sectionMeta = sections.map((section, index) => {
      const title = String(section.querySelector(':scope > .card-header h5')?.textContent || `Раздел ${index + 1}`).trim();
      const normalized = title.toLowerCase();
      let icon = 'bi-sliders';
      if (normalized.includes('телефон')) icon = 'bi-telephone';
      else if (normalized.includes('mail') || normalized.includes('почт')) icon = 'bi-envelope';
      else if (normalized.includes('адрес')) icon = 'bi-geo-alt';
      return { section, title, icon, index };
    });

    modal.classList.add('settings-workspace-modal', 'settings-workspace-modal--input-formatting');
    body.classList.add('settings-workspace-body', 'settings-workspace-host');

    const lead = body.querySelector(':scope > .settings-modal-lead');
    if (lead instanceof HTMLElement) {
      lead.classList.add('settings-workspace-wide');
    }

    const main = document.createElement('div');
    main.className = 'settings-workspace-main settings-workspace-main--input-formatting';

    const nav = document.createElement('ul');
    nav.className = 'nav nav-tabs settings-menu-tabs settings-workspace-nav settings-section-workspace-nav';
    nav.setAttribute('role', 'tablist');
    nav.setAttribute('aria-label', 'Разделы настройки вводимых данных');

    const content = document.createElement('div');
    content.className = 'settings-tab-content settings-workspace-content settings-section-workspace-content';

    body.insertBefore(main, sections[0]);
    main.append(nav, content);

    const buttons = [];
    sectionMeta.forEach(({ section, title, icon, index }) => {
      const paneId = `input-formatting-pane-${index + 1}`;
      section.id = section.id || paneId;
      section.classList.add('settings-section-workspace-pane');
      section.classList.remove('mb-4');

      const item = document.createElement('li');
      item.className = 'nav-item';
      item.setAttribute('role', 'presentation');

      const button = document.createElement('button');
      button.className = 'nav-link';
      button.type = 'button';
      button.setAttribute('role', 'tab');
      button.setAttribute('aria-controls', section.id);
      button.innerHTML = `<i class="bi ${icon}" aria-hidden="true"></i><span>${title}</span>`;
      item.appendChild(button);
      nav.appendChild(item);
      buttons.push(button);
      content.appendChild(section);
    });

    function activateSection(nextIndex, focusButton = false) {
      const safeIndex = Math.max(0, Math.min(sectionMeta.length - 1, Number(nextIndex) || 0));
      sectionMeta.forEach(({ section }, index) => {
        const active = index === safeIndex;
        section.hidden = !active;
        buttons[index].classList.toggle('active', active);
        buttons[index].setAttribute('aria-selected', active ? 'true' : 'false');
        buttons[index].tabIndex = active ? 0 : -1;
      });
      if (focusButton) {
        buttons[safeIndex]?.focus();
      }
    }

    buttons.forEach((button, index) => {
      button.addEventListener('click', () => activateSection(index));
    });

    nav.addEventListener('keydown', (event) => {
      const currentIndex = buttons.indexOf(document.activeElement);
      if (currentIndex < 0) return;
      let nextIndex = currentIndex;
      if (event.key === 'ArrowDown' || event.key === 'ArrowRight') nextIndex = (currentIndex + 1) % buttons.length;
      else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') nextIndex = (currentIndex - 1 + buttons.length) % buttons.length;
      else if (event.key === 'Home') nextIndex = 0;
      else if (event.key === 'End') nextIndex = buttons.length - 1;
      else return;
      event.preventDefault();
      activateSection(nextIndex, true);
    });

    activateSection(0);
    modal.dataset.settingsWorkspaceReady = 'true';
  }
  function init() {
    TARGET_MODAL_IDS.forEach((id) => {
      enhanceWorkspace(document.getElementById(id));
    });
    enhanceItWorkspace(document.getElementById('itConnectionsModal'));
    enhanceInputFormattingWorkspace(document.getElementById('inputFormattingModal'));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
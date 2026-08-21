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
  // Settings legal entities workspace v9
  function enhanceLegalEntitiesWorkspace(modal) {
    if (!(modal instanceof HTMLElement) || modal.dataset.settingsWorkspaceReady === 'true') {
      return;
    }

    const body = modal.querySelector(':scope > .modal-dialog > .modal-content > .modal-body');
    if (!(body instanceof HTMLElement)) {
      return;
    }

    const list = body.querySelector('[data-legal-entities-list]');
    const empty = body.querySelector('[data-legal-entities-empty]');
    const addButton = body.querySelector('[data-legal-entity-add]');
    const lead = body.querySelector(':scope > .settings-modal-lead');
    const toolbar = addButton instanceof HTMLElement
      ? Array.from(body.children).find((child) => child instanceof HTMLElement && child.contains(addButton))
      : null;

    if (!(list instanceof HTMLElement) || !(addButton instanceof HTMLElement) || !(toolbar instanceof HTMLElement)) {
      modal.dataset.settingsWorkspaceReady = 'unsupported';
      return;
    }

    modal.classList.add('settings-workspace-modal', 'settings-workspace-modal--legal-entities');
    body.classList.add('settings-workspace-body', 'settings-workspace-host', 'settings-workspace-host--legal-entities');
    if (lead instanceof HTMLElement) {
      lead.classList.add('settings-workspace-wide');
    }

    const main = document.createElement('div');
    main.className = 'settings-workspace-main settings-workspace-main--legal-entities';

    const nav = document.createElement('aside');
    nav.className = 'settings-legal-entity-nav';
    nav.setAttribute('aria-label', 'Юридические лица');

    const navHead = document.createElement('div');
    navHead.className = 'settings-legal-entity-nav__head';

    const navTitle = document.createElement('div');
    navTitle.className = 'settings-legal-entity-nav__title';
    navTitle.textContent = 'Юридические лица';

    addButton.className = 'btn btn-sm btn-outline-primary settings-legal-entity-nav__add';
    navHead.append(navTitle, addButton);

    const navList = document.createElement('div');
    navList.className = 'settings-legal-entity-nav__list';
    nav.append(navHead, navList);

    const content = document.createElement('div');
    content.className = 'settings-legal-entity-workspace-content';

    body.insertBefore(main, toolbar);
    main.append(nav, content);

    toolbar.classList.add('settings-legal-entity-content-head');
    toolbar.classList.remove('mb-3');
    content.append(toolbar, list);
    if (empty instanceof HTMLElement) {
      content.append(empty);
    }

    let selectedKey = '';
    let selectedIndex = 0;
    let initialized = false;
    let rebuildQueued = false;

    function getEntries() {
      return Array.from(list.querySelectorAll(':scope > .col > [data-legal-entity-card]'))
        .filter((card) => card instanceof HTMLElement)
        .map((card, index) => {
          const persistedId = String(card.dataset.paramId || '').trim();
          const draftId = String(card.dataset.legalEntityDraftId || '').trim();
          const key = persistedId
            ? `id:${persistedId}`
            : draftId
              ? `draft:${draftId}`
              : `index:${index}`;
          const title = String(card.querySelector('[data-legal-entity-title]')?.textContent || 'Без названия').trim() || 'Без названия';
          const state = String(card.querySelector('[data-legal-entity-state-label]')?.textContent || '').trim();
          return {
            card,
            wrapper: card.parentElement,
            key,
            title,
            state,
            index,
            isDraft: card.dataset.legalEntityDraft === 'true',
            isDeleted: card.dataset.legalEntityDeleted === 'true',
          };
        })
        .filter((entry) => entry.wrapper instanceof HTMLElement);
    }

    function applySelection(entries = getEntries()) {
      if (!entries.length) {
        selectedKey = '';
        selectedIndex = 0;
        navList.querySelectorAll('[data-legal-entity-workspace-key]').forEach((button) => {
          button.classList.remove('active');
          button.setAttribute('aria-pressed', 'false');
        });
        return;
      }

      let targetIndex = entries.findIndex((entry) => entry.key === selectedKey);
      if (targetIndex < 0) {
        targetIndex = Math.max(0, Math.min(entries.length - 1, selectedIndex));
      }

      const target = entries[targetIndex];
      selectedKey = target.key;
      selectedIndex = targetIndex;

      entries.forEach((entry, index) => {
        const active = index === targetIndex;
        entry.wrapper.hidden = !active;
        entry.wrapper.classList.toggle('is-active', active);
        entry.card.setAttribute('aria-hidden', active ? 'false' : 'true');
      });

      navList.querySelectorAll('[data-legal-entity-workspace-key]').forEach((button) => {
        const active = button.getAttribute('data-legal-entity-workspace-key') === selectedKey;
        button.classList.toggle('active', active);
        button.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
    }

    function rebuildNavigation({ selectNewestDraft = false } = {}) {
      const entries = getEntries();

      if (selectNewestDraft && entries.length) {
        const draftEntries = entries.filter((entry) => entry.isDraft);
        const newestDraft = draftEntries[draftEntries.length - 1];
        if (newestDraft) {
          selectedKey = newestDraft.key;
          selectedIndex = newestDraft.index;
        }
      } else if (!initialized && entries.length) {
        selectedKey = entries[0].key;
        selectedIndex = 0;
      } else if (entries.length && !entries.some((entry) => entry.key === selectedKey)) {
        selectedIndex = Math.max(0, Math.min(entries.length - 1, selectedIndex));
        selectedKey = entries[selectedIndex].key;
      }

      const fragment = document.createDocumentFragment();
      entries.forEach((entry) => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'settings-legal-entity-nav__item';
        button.setAttribute('data-legal-entity-workspace-key', entry.key);
        button.setAttribute('aria-pressed', entry.key === selectedKey ? 'true' : 'false');

        const title = document.createElement('span');
        title.className = 'settings-legal-entity-nav__item-title';
        title.textContent = entry.title;

        const meta = document.createElement('span');
        meta.className = 'settings-legal-entity-nav__item-meta';
        if (entry.isDraft) {
          meta.textContent = 'Новая запись';
        } else if (entry.isDeleted) {
          meta.textContent = 'Удалено';
        } else {
          meta.textContent = entry.state || '—';
        }

        button.append(title, meta);
        if (entry.isDraft) {
          button.classList.add('is-draft');
        }
        if (entry.isDeleted) {
          button.classList.add('is-deleted');
        }
        button.addEventListener('click', () => {
          selectedKey = entry.key;
          selectedIndex = entry.index;
          applySelection(getEntries());
        });
        fragment.appendChild(button);
      });

      navList.replaceChildren(fragment);
      applySelection(entries);
      initialized = true;
    }

    const observer = new MutationObserver(() => {
      if (rebuildQueued) {
        return;
      }
      rebuildQueued = true;
      queueMicrotask(() => {
        rebuildQueued = false;
        rebuildNavigation();
      });
    });
    observer.observe(list, { childList: true, subtree: true, characterData: true });

    addButton.addEventListener('click', () => {
      window.setTimeout(() => rebuildNavigation({ selectNewestDraft: true }), 0);
    });

    rebuildNavigation();
    modal.dataset.settingsWorkspaceReady = 'true';
  }
  function init() {
    TARGET_MODAL_IDS.forEach((id) => {
      enhanceWorkspace(document.getElementById(id));
    });
    enhanceItWorkspace(document.getElementById('itConnectionsModal'));
    enhanceInputFormattingWorkspace(document.getElementById('inputFormattingModal'));
    enhanceLegalEntitiesWorkspace(document.getElementById('legalEntitiesModal'));
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
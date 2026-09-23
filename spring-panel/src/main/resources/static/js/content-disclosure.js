(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }
  if (window.__contentDisclosureInitialized) {
    return;
  }
  window.__contentDisclosureInitialized = true;

  let disclosureCounter = 0;



  function containsInteractiveContent(element) {
    return Boolean(
      element.querySelector('a, button, input, select, textarea, [role="button"], [data-bs-toggle]')
    );
  }

  function shouldSkipElement(element) {
    if (!element || element.dataset.disclosureProcessed === 'true') {
      return true;
    }
    if (element.closest('.content-disclosure')) {
      return true;
    }
    if (element.closest('.settings-tiles')) {
      return true;
    }
    if (
      element.closest('[data-no-disclosure]')
      || element.dataset.managerBindingsSummary !== undefined
      || element.dataset.reportingSummary !== undefined
    ) {
      return true;
    }
    if (containsInteractiveContent(element)) {
      return true;
    }
    return false;
  }

  function shouldSkipModalHelpElement(element) {
    if (shouldSkipElement(element)) {
      return true;
    }
    if (!element.closest('.modal')) {
      return true;
    }
    if (element.classList.contains('alert-danger') || element.classList.contains('alert-success')) {
      return true;
    }
    if (element.classList.contains('d-none')) {
      return true;
    }
    return false;
  }

  function bindInfoPopover(info, toggle, panel) {
    let lockedOpen = false;

    function setOpen(open) {
      panel.hidden = !open;
      info.classList.toggle('is-open', open);
      toggle.setAttribute('aria-expanded', String(open));
    }

    info.addEventListener('mouseenter', () => {
      setOpen(true);
    });

    info.addEventListener('mouseleave', () => {
      if (!lockedOpen && !info.contains(document.activeElement)) {
        setOpen(false);
      }
    });

    info.addEventListener('focusin', () => {
      setOpen(true);
    });

    info.addEventListener('focusout', () => {
      window.requestAnimationFrame(() => {
        if (!lockedOpen && !info.contains(document.activeElement)) {
          setOpen(false);
        }
      });
    });

    toggle.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      lockedOpen = !lockedOpen;
      setOpen(lockedOpen || info.matches(':hover') || info.contains(document.activeElement));
    });

    document.addEventListener('click', (event) => {
      if (event.target instanceof Node && info.contains(event.target)) {
        return;
      }
      lockedOpen = false;
      setOpen(false);
    });

    document.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape' || panel.hidden) {
        return;
      }
      lockedOpen = false;
      setOpen(false);
      toggle.focus({ preventScroll: true });
    });
  }
function ensurePageHeaderTitleRow(header, title) {
  if (!(header instanceof HTMLElement) || !(title instanceof HTMLElement)) {
    return null;
  }

  let titleRow = title.parentElement?.classList.contains('page-header-title-row')
    ? title.parentElement
    : null;

  if (!titleRow) {
    titleRow = document.createElement('div');
    titleRow.className = 'page-header-title-row';
    title.parentNode.insertBefore(titleRow, title);
    titleRow.appendChild(title);
  }

  const kicker = header.querySelector('.page-kicker');
  if (kicker instanceof HTMLElement && !titleRow.contains(kicker)) {
    titleRow.insertBefore(kicker, title);
  }

  return titleRow;
}

function buildHeaderInfoDisclosure(element) {
    const rawText =
        (element.textContent || '')
            .replace(/\s+/g, ' ')
            .trim();

    if (!rawText) {
        return;
    }

    const header =
        element.closest(
            '.page-header-card'
        );

    const title =
        header?.querySelector(
            '.page-title'
        );

    if (!header || !title) {
        return;
    }

    disclosureCounter += 1;

    const panelId =
        `pageHeaderInfoPanel${disclosureCounter}`;

    const titleRow =
        ensurePageHeaderTitleRow(
            header,
            title
        );

    if (!titleRow) {
        return;
    }

    const info =
        document.createElement(
            'span'
        );

    info.className =
        'page-header-info';

    const toggle =
        document.createElement(
            'button'
        );

    toggle.type = 'button';

    toggle.className =
        'page-header-info__toggle';

    toggle.innerHTML = '<i class="bi bi-info-circle" aria-hidden="true"></i>';
    toggle.title = 'Описание страницы';

    toggle.setAttribute(
        'aria-label',
        'Описание страницы'
    );

    toggle.setAttribute(
        'aria-controls',
        panelId
    );

    toggle.setAttribute(
        'aria-expanded',
        'false'
    );

    const panel =
        document.createElement(
            'div'
        );

    panel.id = panelId;

    panel.className =
        'page-header-info__panel';

    panel.setAttribute(
        'role',
        'tooltip'
    );

    panel.hidden = true;

    element.hidden = false;
    element.removeAttribute('data-disclosure-pending');

    element.classList.add(
        'page-header-info__copy'
    );

    panel.appendChild(
        element
    );

    info.appendChild(
        toggle
    );

    info.appendChild(
        panel
    );

    titleRow.appendChild(
        info
    );

    header.classList.add(
        'page-header-card--has-info'
    );

    element.dataset.disclosureProcessed =
        'true';

    bindInfoPopover(info, toggle, panel);

}


  function buildChannelEditorSectionInfo(section) {
    if (!section || section.dataset.channelEditorInfoProcessed === 'true') {
      return;
    }

    const head = Array.from(section.children)
      .find((child) => child.classList?.contains('channel-editor-section__head'));
    const title = head?.querySelector('.channel-editor-section__title');
    if (!head || !title) {
      return;
    }

    const helpers = Array.from(section.querySelectorAll(
      '.channel-editor-section__hint, .form-text:not([data-channel-editor-keep-visible]):not([data-channel-editor-keep-inline-help])'
    )).filter((element) => (
      element.closest('.channel-editor-section') === section
      && !element.closest('.d-none')
      && !containsInteractiveContent(element)
    ));

    if (!helpers.length) {
      section.dataset.channelEditorInfoProcessed = 'true';
      return;
    }

    disclosureCounter += 1;
    const panelId = `channelEditorInfoPanel${disclosureCounter}`;
    const titleText = (title.textContent || 'Раздел').replace(/\s+/g, ' ').trim();
    const heading = title.parentElement;
    heading.classList.add('channel-editor-section__heading');

    const info = document.createElement('span');
    info.className = 'channel-editor-info';
    info.setAttribute('data-channel-editor-info', 'true');

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'channel-editor-info__toggle';
    toggle.setAttribute('aria-label', `Справка: ${titleText}`);
    toggle.setAttribute('aria-describedby', panelId);
    toggle.innerHTML = '<i class="bi bi-info-circle" aria-hidden="true"></i>';

    const panel = document.createElement('div');
    panel.id = panelId;
    panel.className = 'channel-editor-info__panel';
    panel.setAttribute('role', 'tooltip');

    helpers.forEach((helper) => {
      helper.hidden = false;
      helper.removeAttribute('data-disclosure-pending');
      helper.classList.add('channel-editor-info__copy');
      helper.dataset.disclosureProcessed = 'true';
      panel.appendChild(helper);
    });

    info.appendChild(toggle);
    info.appendChild(panel);
    heading.appendChild(info);
    section.dataset.channelEditorInfoProcessed = 'true';
  }

  function buildDisclosure(element, options) {
    const rawText = (element.textContent || '').replace(/\s+/g, ' ').trim();
    if (!rawText || rawText.length < options.minLength) {
      return;
    }

    const parent = element.parentNode;
    if (!parent) {
      return;
    }

    disclosureCounter += 1;
    const panelId = `contentDisclosurePanel${disclosureCounter}`;
    element.hidden = false;
    element.removeAttribute('data-disclosure-pending');
    element.classList.add('page-header-info__copy');

    const wrapper = document.createElement('div');
    wrapper.className = `content-disclosure page-header-info ${options.variantClass}`;
    wrapper.setAttribute('data-content-disclosure', 'true');

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'page-header-info__toggle';
    const disclosureLabel = (element.dataset.disclosureLabel || 'Показать пояснение').trim() || 'Показать пояснение';
    toggle.setAttribute('aria-label', disclosureLabel);
    toggle.setAttribute('title', disclosureLabel);
    toggle.setAttribute('aria-controls', panelId);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.innerHTML = '<i class="bi bi-info-circle" aria-hidden="true"></i>';

    const panel = document.createElement('div');
    panel.id = panelId;
    panel.className = 'page-header-info__panel';
    panel.setAttribute('role', 'tooltip');
    panel.hidden = true;

    parent.insertBefore(wrapper, element);
    wrapper.appendChild(toggle);
    wrapper.appendChild(panel);
    panel.appendChild(element);
    element.dataset.disclosureProcessed = 'true';

    bindInfoPopover(wrapper, toggle, panel);
  }

  function initContentDisclosure() {
    document.querySelectorAll('.page-header-card .page-title').forEach((title) => {
      const header = title.closest('.page-header-card');
      ensurePageHeaderTitleRow(header, title);
    });

    const subtitleCandidates =
		document.querySelectorAll(
			'.page-header-card .page-subtitle'
		);

	subtitleCandidates.forEach(
		(element) => {
			if (
				shouldSkipElement(
					element
				)
			) {
				return;
			}

			buildHeaderInfoDisclosure(
				element
			);
		}
	);

    document.querySelectorAll('#channelEditorModal .channel-editor-section').forEach((section) => {
      buildChannelEditorSectionInfo(section);
    });

    const explicitCandidates = document.querySelectorAll('[data-content-disclosure-help]');
    explicitCandidates.forEach((element) => {
      if (shouldSkipElement(element)) {
        return;
      }
      buildDisclosure(element, {
        minLength: 1,
        variantClass: 'content-disclosure--inline',
      });
    });

    const cardCandidates = document.querySelectorAll('.card .card-body > .card-text.text-muted');
    cardCandidates.forEach((element) => {
      if (shouldSkipElement(element)) {
        return;
      }
      buildDisclosure(element, {
        minLength: 58,
        variantClass: 'content-disclosure--card',
      });
    });

    const modalAlertCandidates = document.querySelectorAll('.modal .alert.alert-light.border');
    modalAlertCandidates.forEach((element) => {
      if (shouldSkipModalHelpElement(element)) {
        return;
      }
      buildDisclosure(element, {
        minLength: 120,
        variantClass: 'content-disclosure--inline',
      });
    });

    const modalFormTextCandidates = document.querySelectorAll('.modal .form-text');
    modalFormTextCandidates.forEach((element) => {
      if (shouldSkipModalHelpElement(element)) {
        return;
      }
      buildDisclosure(element, {
        minLength: 140,
        variantClass: 'content-disclosure--inline',
      });
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initContentDisclosure);
  } else {
    initContentDisclosure();
  }
})();

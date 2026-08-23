(function () {
  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }
  if (window.__contentDisclosureInitialized) {
    return;
  }
  window.__contentDisclosureInitialized = true;

  let disclosureCounter = 0;

  function truncateText(value, limit) {
    if (value.length <= limit) {
      return value;
    }
    const next = value.slice(0, limit);
    const lastSpace = next.lastIndexOf(' ');
    return `${(lastSpace > 24 ? next.slice(0, lastSpace) : next).trim()}...`;
  }

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
    if (element.dataset.noDisclosure !== undefined || element.dataset.managerBindingsSummary !== undefined || element.dataset.reportingSummary !== undefined) {
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

    let titleRow =
        title.parentElement
            ?.classList
            .contains(
                'page-header-title-row'
            )
            ? title.parentElement
            : null;

    if (!titleRow) {
        titleRow =
            document.createElement(
                'div'
            );

        titleRow.className =
            'page-header-title-row';

        title.parentNode.insertBefore(
            titleRow,
            title
        );

        titleRow.appendChild(
            title
        );
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

    toggle.textContent = 'i';

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

    let lockedOpen = false;

    function setOpen(open) {
        panel.hidden = !open;

        info.classList.toggle(
            'is-open',
            open
        );

        toggle.setAttribute(
            'aria-expanded',
            String(open)
        );
    }

    info.addEventListener(
        'mouseenter',
        () => {
            setOpen(true);
        }
    );

    info.addEventListener(
        'mouseleave',
        () => {
            if (
                !lockedOpen &&
                !info.contains(
                    document.activeElement
                )
            ) {
                setOpen(false);
            }
        }
    );

    info.addEventListener(
        'focusin',
        () => {
            setOpen(true);
        }
    );

    info.addEventListener(
        'focusout',
        () => {
            window.requestAnimationFrame(
                () => {
                    if (
                        !lockedOpen &&
                        !info.contains(
                            document.activeElement
                        )
                    ) {
                        setOpen(false);
                    }
                }
            );
        }
    );

    toggle.addEventListener(
        'click',
        (event) => {
            event.preventDefault();
            event.stopPropagation();

            lockedOpen =
                !lockedOpen;

            setOpen(
                lockedOpen ||
                info.matches(':hover') ||
                info.contains(
                    document.activeElement
                )
            );
        }
    );

    document.addEventListener(
        'click',
        (event) => {
            if (
                event.target instanceof Node &&
                info.contains(
                    event.target
                )
            ) {
                return;
            }

            lockedOpen = false;

            setOpen(false);
        }
    );

    document.addEventListener(
        'keydown',
        (event) => {
            if (
                event.key !== 'Escape' ||
                panel.hidden
            ) {
                return;
            }

            lockedOpen = false;

            setOpen(false);

            toggle.focus({
                preventScroll: true
            });
        }
    );
}

  function buildDisclosure(element, options) {
    const rawText = (element.textContent || '').replace(/\s+/g, ' ').trim();
    if (!rawText || rawText.length < options.minLength) {
      return;
    }

    disclosureCounter += 1;
    const bodyId = element.id || `contentDisclosureBody${disclosureCounter}`;
    element.id = bodyId;
    element.hidden = true;
    element.classList.add('content-disclosure__body');

    const wrapper = document.createElement('div');
    wrapper.className = `content-disclosure ${options.variantClass}`;
    wrapper.setAttribute('data-content-disclosure', 'true');

    const preview = document.createElement(element.tagName === 'P' ? 'p' : 'div');
    preview.className = `content-disclosure__preview ${element.className}`.trim();
    preview.textContent = truncateText(rawText, options.previewLength);

    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'content-disclosure__toggle';
    toggle.setAttribute('aria-controls', bodyId);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.innerHTML = `Подробнее <span class="content-disclosure__icon" aria-hidden="true">▼</span>`;

    toggle.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      const expanded = toggle.getAttribute('aria-expanded') === 'true';
      toggle.setAttribute('aria-expanded', String(!expanded));
      toggle.innerHTML = `${expanded ? 'Подробнее' : 'Скрыть'} <span class="content-disclosure__icon" aria-hidden="true">▼</span>`;
      element.hidden = expanded;
      preview.hidden = !expanded;
    });

    wrapper.addEventListener('click', (event) => {
      event.stopPropagation();
    });

    element.parentNode.insertBefore(wrapper, element);
    wrapper.appendChild(preview);
    wrapper.appendChild(toggle);
    wrapper.appendChild(element);
    element.dataset.disclosureProcessed = 'true';
  }

  function initContentDisclosure() {
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

    const cardCandidates = document.querySelectorAll('.card .card-body > .card-text.text-muted');
    cardCandidates.forEach((element) => {
      if (shouldSkipElement(element)) {
        return;
      }
      buildDisclosure(element, {
        minLength: 58,
        previewLength: 72,
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
        previewLength: 110,
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
        previewLength: 120,
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

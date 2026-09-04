(() => {
  'use strict';
  const SELECTOR = 'details.ui-disclosure-native';
  const DURATION_MS = 350;
  const reducedMotion = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;

  function summaryOf(details) {
    return Array.from(details.children).find((child) => child.tagName === 'SUMMARY') || null;
  }
  function borders(details) {
    const style = window.getComputedStyle(details);
    return (parseFloat(style.borderTopWidth) || 0) + (parseFloat(style.borderBottomWidth) || 0);
  }
  function clear(details) {
    details.style.removeProperty('height');
    details.style.removeProperty('overflow');
    delete details.dataset.uiDisclosureAnimating;
  }
  function animate(details, summary, opening) {
    const start = details.getBoundingClientRect().height;
    if (opening) details.open = true;
    const end = opening ? details.getBoundingClientRect().height : summary.getBoundingClientRect().height + borders(details);
    details.style.height = String(start) + 'px';
    details.style.overflow = 'hidden';
    details.dataset.uiDisclosureAnimating = 'true';
    const animation = details.animate(
      [{ height: String(start) + 'px' }, { height: String(end) + 'px' }],
      { duration: DURATION_MS, easing: 'ease' },
    );
    animation.onfinish = () => {
      if (!opening) details.open = false;
      clear(details);
    };
    animation.oncancel = () => clear(details);
  }

  document.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const summary = target.closest('summary');
    if (!(summary instanceof HTMLElement)) return;
    const details = summary.parentElement;
    if (!(details instanceof HTMLDetailsElement) || !details.matches(SELECTOR) || summaryOf(details) !== summary) return;
    if (details.dataset.uiDisclosureAnimating === 'true') {
      event.preventDefault();
      return;
    }
    if ((reducedMotion && reducedMotion.matches) || typeof details.animate !== 'function') return;
    event.preventDefault();
    animate(details, summary, !details.open);
  });
})();

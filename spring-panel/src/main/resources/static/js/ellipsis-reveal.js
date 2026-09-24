(function () {
  'use strict';

  if (typeof window === 'undefined' || typeof document === 'undefined') {
    return;
  }
  if (window.__uiEllipsisRevealInitialized) {
    return;
  }
  window.__uiEllipsisRevealInitialized = true;

  const SELECTOR = '[data-ui-ellipsis-reveal]';
  const TOOLTIP_ID = 'uiEllipsisRevealTooltip';
  let activeTarget = null;
  let tooltip = null;

  function resolveTarget(node) {
    return node instanceof Element ? node.closest(SELECTOR) : null;
  }

  function isTruncated(target) {
    if (!(target instanceof HTMLElement)) {
      return false;
    }
    return target.scrollWidth > target.clientWidth + 1
      || target.scrollHeight > target.clientHeight + 1;
  }

  function fullValue(target) {
    return String(target?.dataset?.uiFullValue || target?.textContent || '')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function ensureTooltip() {
    if (tooltip && document.body.contains(tooltip)) {
      return tooltip;
    }
    tooltip = document.createElement('div');
    tooltip.id = TOOLTIP_ID;
    tooltip.className = 'ui-ellipsis-tooltip';
    tooltip.setAttribute('role', 'tooltip');
    tooltip.hidden = true;
    document.body.appendChild(tooltip);
    return tooltip;
  }

  function positionTooltip() {
    if (!(activeTarget instanceof HTMLElement) || !tooltip || tooltip.hidden) {
      return;
    }
    const gap = 6;
    const viewportPad = 12;
    const rect = activeTarget.getBoundingClientRect();
    const tipRect = tooltip.getBoundingClientRect();
    let left = rect.left;
    let top = rect.bottom + gap;

    if (left + tipRect.width > window.innerWidth - viewportPad) {
      left = Math.max(viewportPad, window.innerWidth - viewportPad - tipRect.width);
    } else {
      left = Math.max(viewportPad, left);
    }

    if (top + tipRect.height > window.innerHeight - viewportPad) {
      top = Math.max(viewportPad, rect.top - gap - tipRect.height);
    }

    tooltip.style.left = left + 'px';
    tooltip.style.top = top + 'px';
  }

  function show(target) {
    if (!(target instanceof HTMLElement) || !isTruncated(target)) {
      hide();
      return;
    }
    const value = fullValue(target);
    if (!value || value === '—') {
      hide();
      return;
    }
    const node = ensureTooltip();
    activeTarget = target;
    node.textContent = value;
    node.hidden = false;
    positionTooltip();
  }

  function hide() {
    activeTarget = null;
    if (tooltip) {
      tooltip.hidden = true;
      tooltip.textContent = '';
    }
  }

  document.addEventListener('pointerover', (event) => {
    const target = resolveTarget(event.target);
    if (!target) return;
    const related = event.relatedTarget;
    if (related instanceof Node && target.contains(related)) return;
    show(target);
  });

  document.addEventListener('pointerout', (event) => {
    if (!activeTarget) return;
    const related = event.relatedTarget;
    if (related instanceof Node && activeTarget.contains(related)) return;
    hide();
  });

  document.addEventListener('focusin', (event) => {
    const target = resolveTarget(event.target);
    if (target) show(target);
  });

  document.addEventListener('focusout', (event) => {
    if (!activeTarget) return;
    const related = event.relatedTarget;
    if (related instanceof Node && activeTarget.contains(related)) return;
    hide();
  });

  window.addEventListener('resize', hide);
  window.addEventListener('scroll', hide, true);
})();

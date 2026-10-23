/* Закрытие не удаляет legacy message nodes: workbench продолжает использовать их. */
(function () {
  'use strict';
  const stack = document.querySelector('[data-incident-notifications]');
  if (!stack) return;
  let previousFocus = null;
  document.addEventListener('focusin', event => {
    if (!stack.contains(event.target)) previousFocus = event.target;
  });
  stack.addEventListener('click', event => {
    const button = event.target.closest('[data-incident-notice-close]');
    if (!button || !stack.contains(button)) return;
    const notice = document.getElementById(button.dataset.incidentNoticeClose);
    if (!notice || !stack.contains(notice)) return;
    const restoreFocus = button === document.activeElement;
    notice.classList.add('d-none');
    if (restoreFocus) {
      const target = previousFocus?.isConnected ? previousFocus : document.getElementById('incidentWorkbenchRefresh');
      target?.focus({ preventScroll: true });
    }
  });
})();

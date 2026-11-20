(function () {
  let embedded = false;
  try {
    embedded = window.parent !== window && window.frameElement?.hasAttribute('data-passport-dialog-frame')
      && window.parent.location.origin === window.location.origin
      && /^\/object-passports\/[1-9][0-9]*\/?$/.test(window.parent.location.pathname);
  } catch (_error) { /* Cross-origin frames cannot opt in. */ }
  if (!embedded) return;
  const general = /^\/general-dialog-work\/[0-9a-f]{32}$/.test(window.location.pathname);
  document.documentElement.dataset.passportDialog = general ? 'general' : 'legacy';
  function notify(action) {
    window.parent.postMessage({ type: 'iguana-passport-dialog', action }, window.location.origin);
  }
  window.DialogEmbeddedRuntime = Object.freeze({ onDialogClosed() { notify('close'); return true; } });
  document.addEventListener('shown.bs.modal', event => {
    if (event.target.id === 'dialogDetailsModal') notify('ready');
  });
  if (general) {
    document.addEventListener('DOMContentLoaded', () => notify('ready'));
    document.addEventListener('keydown', event => {
      if (event.key === 'Escape' && !event.defaultPrevented && !document.querySelector('.modal.show')) {
        event.preventDefault(); notify('close');
      }
    });
  }
}());

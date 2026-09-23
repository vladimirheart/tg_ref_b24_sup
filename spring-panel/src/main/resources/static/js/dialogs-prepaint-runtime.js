'use strict';

(function () {
  const ROOT_CLASS = 'dialog-list-only-prepaint';
  const STORAGE_KEY = 'iguana:dialogs:list-only-mode';

  let enabled = false;
  try {
    const raw = String(window.localStorage.getItem(STORAGE_KEY) || '').trim().toLowerCase();
    enabled = raw === '1' || raw === 'true' || raw === 'on';
  } catch (_error) {
    enabled = false;
  }

  document.documentElement.classList.toggle(ROOT_CLASS, enabled);
})();

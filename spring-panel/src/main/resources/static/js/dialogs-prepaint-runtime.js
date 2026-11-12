'use strict';

(function () {
  const PENDING_CLASS = 'dialogs-list-preparing';
  const root = document.documentElement;
  root.classList.add(PENDING_CLASS);
  let watchdog;
  const claim = () => window.clearTimeout(watchdog);
  const ready = () => {
    root.classList.remove(PENDING_CLASS);
    claim();
  };
  window.DialogsFirstPaint = Object.freeze({ ready, claim });
  watchdog = window.setTimeout(() => {
    // A broken/missing main bundle must leave the server-rendered page usable.
    ready();
  }, 8000);
  const ROOT_CLASS = 'dialog-list-only-prepaint';
  const STORAGE_KEY = 'iguana:dialogs:list-only-mode';

  let enabled = false;
  try {
    const raw = String(window.localStorage.getItem(STORAGE_KEY) || '').trim().toLowerCase();
    enabled = raw === '1' || raw === 'true' || raw === 'on';
  } catch (_error) {
    enabled = false;
  }

  root.classList.toggle(ROOT_CLASS, enabled);
})();

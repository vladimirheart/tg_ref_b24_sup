(function () {
  if (window.__iguanaModalResizeInitialized) {
    return;
  }
  window.__iguanaModalResizeInitialized = true;

  const STORAGE_PREFIX = 'iguana:modal-size:v1:';
  const MIN_WIDTH = 320;
  const MIN_HEIGHT = 220;
  const VIEWPORT_GAP = 24;
  const DESKTOP_QUERY = '(min-width: 768px)';
  const desktopMedia = window.matchMedia ? window.matchMedia(DESKTOP_QUERY) : null;
  const initialized = new WeakSet();

  function isDesktop() {
    return !desktopMedia || desktopMedia.matches;
  }

  function storageKey(modal) {
    if (!modal || !modal.id) {
      return '';
    }
    return `${STORAGE_PREFIX}${encodeURIComponent(window.location.pathname)}:${modal.id}`;
  }

  function readStoredSize(modal) {
    const key = storageKey(modal);
    if (!key) {
      return null;
    }
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw);
      const width = Number(parsed && parsed.width);
      const height = Number(parsed && parsed.height);
      if (!Number.isFinite(width) || !Number.isFinite(height)) {
        return null;
      }
      return { width, height };
    } catch (error) {
      return null;
    }
  }

  function writeStoredSize(modal, size) {
    const key = storageKey(modal);
    if (!key || !size) {
      return;
    }
    try {
      window.localStorage.setItem(key, JSON.stringify({
        width: Math.round(size.width),
        height: Math.round(size.height),
      }));
    } catch (error) {
      // Local storage can be unavailable in privacy-restricted contexts.
    }
  }

  function removeStoredSize(modal) {
    const key = storageKey(modal);
    if (!key) {
      return;
    }
    try {
      window.localStorage.removeItem(key);
    } catch (error) {
      // Ignore storage failures and still restore the default geometry.
    }
  }

  function clampSize(width, height) {
    const maxWidth = Math.max(MIN_WIDTH, window.innerWidth - VIEWPORT_GAP);
    const maxHeight = Math.max(MIN_HEIGHT, window.innerHeight - VIEWPORT_GAP);
    return {
      width: Math.min(maxWidth, Math.max(MIN_WIDTH, Math.round(width))),
      height: Math.min(maxHeight, Math.max(MIN_HEIGHT, Math.round(height))),
    };
  }

  function applySize(dialog, size) {
    if (!dialog || !size || !isDesktop()) {
      return;
    }
    const clamped = clampSize(size.width, size.height);
    dialog.style.setProperty('--iguana-modal-user-width', `${clamped.width}px`);
    dialog.style.setProperty('--iguana-modal-user-height', `${clamped.height}px`);
    dialog.classList.add('modal-user-sized');
  }

  function clearAppliedSize(dialog) {
    if (!dialog) {
      return;
    }
    dialog.classList.remove('modal-user-sized');
    dialog.style.removeProperty('--iguana-modal-user-width');
    dialog.style.removeProperty('--iguana-modal-user-height');
  }

  function restoreSize(modal, dialog) {
    if (!isDesktop()) {
      clearAppliedSize(dialog);
      return;
    }
    const stored = readStoredSize(modal);
    if (stored) {
      applySize(dialog, stored);
    } else {
      clearAppliedSize(dialog);
    }
  }

  function resetSize(modal, dialog) {
    removeStoredSize(modal);
    clearAppliedSize(dialog);
  }

  function shouldSkip(modal, dialog) {
    if (!modal || !dialog) {
      return true;
    }
    if (modal.dataset.modalResize === 'off') {
      return true;
    }
    if (modal.matches('.settings-sheet-modal, .settings-child-modal--sheet')) {
      return true;
    }
    if (dialog.classList.contains('modal-fullscreen')) {
      return true;
    }
    return false;
  }

  function resizeByKeyboard(event, modal, dialog) {
    if (!isDesktop()) {
      return;
    }
    const keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];
    if (!keys.includes(event.key)) {
      return;
    }
    event.preventDefault();

    const rect = dialog.getBoundingClientRect();
    const step = event.shiftKey ? 48 : 24;
    let width = rect.width;
    let height = rect.height;

    if (event.key === 'ArrowLeft') width -= step;
    if (event.key === 'ArrowRight') width += step;
    if (event.key === 'ArrowUp') height -= step;
    if (event.key === 'ArrowDown') height += step;

    const next = clampSize(width, height);
    applySize(dialog, next);
    writeStoredSize(modal, next);
  }

  function attachResize(modal) {
    if (!(modal instanceof HTMLElement) || initialized.has(modal)) {
      return;
    }
    const dialog = modal.querySelector(':scope > .modal-dialog');
    if (!(dialog instanceof HTMLElement) || shouldSkip(modal, dialog)) {
      initialized.add(modal);
      return;
    }

    initialized.add(modal);
    dialog.classList.add('modal-resizable');

    const handle = document.createElement('button');
    handle.type = 'button';
    handle.className = 'modal-resize-handle';
    handle.setAttribute('aria-label', '\u0418\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0440\u0430\u0437\u043c\u0435\u0440 \u043e\u043a\u043d\u0430');
    handle.title = '\u041f\u043e\u0442\u044f\u043d\u0438\u0442\u0435, \u0447\u0442\u043e\u0431\u044b \u0438\u0437\u043c\u0435\u043d\u0438\u0442\u044c \u0440\u0430\u0437\u043c\u0435\u0440. \u0414\u0432\u043e\u0439\u043d\u043e\u0439 \u043a\u043b\u0438\u043a \u2014 \u0441\u0431\u0440\u043e\u0441.';
    dialog.appendChild(handle);

    restoreSize(modal, dialog);

    modal.addEventListener('show.bs.modal', () => restoreSize(modal, dialog));

    handle.addEventListener('dblclick', (event) => {
      event.preventDefault();
      event.stopPropagation();
      resetSize(modal, dialog);
    });

    handle.addEventListener('keydown', (event) => resizeByKeyboard(event, modal, dialog));

    handle.addEventListener('pointerdown', (event) => {
      if (!isDesktop() || event.button !== 0) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();

      const rect = dialog.getBoundingClientRect();
      const startX = event.clientX;
      const startY = event.clientY;
      const startWidth = rect.width;
      const startHeight = rect.height;
      let latest = clampSize(startWidth, startHeight);

      applySize(dialog, latest);
      document.body.classList.add('modal-resize-active');
      handle.setPointerCapture(event.pointerId);

      const onMove = (moveEvent) => {
        if (moveEvent.pointerId !== event.pointerId) {
          return;
        }
        const deltaX = moveEvent.clientX - startX;
        const deltaY = moveEvent.clientY - startY;

        // Bootstrap dialogs stay horizontally centered, so width grows on both sides.
        latest = clampSize(startWidth + (deltaX * 2), startHeight + deltaY);
        applySize(dialog, latest);
      };

      const finish = (finishEvent) => {
        if (finishEvent.pointerId !== event.pointerId) {
          return;
        }
        handle.removeEventListener('pointermove', onMove);
        handle.removeEventListener('pointerup', finish);
        handle.removeEventListener('pointercancel', finish);
        document.body.classList.remove('modal-resize-active');
        if (handle.hasPointerCapture(event.pointerId)) {
          handle.releasePointerCapture(event.pointerId);
        }
        writeStoredSize(modal, latest);
      };

      handle.addEventListener('pointermove', onMove);
      handle.addEventListener('pointerup', finish);
      handle.addEventListener('pointercancel', finish);
    });
  }

  function scan(root) {
    if (!root) {
      return;
    }
    if (root instanceof HTMLElement && root.matches('.modal')) {
      attachResize(root);
    }
    if (root.querySelectorAll) {
      root.querySelectorAll('.modal').forEach(attachResize);
    }
  }

  function refreshViewportSizes() {
    document.querySelectorAll('.modal').forEach((modal) => {
      const dialog = modal.querySelector(':scope > .modal-dialog');
      if (!(dialog instanceof HTMLElement) || shouldSkip(modal, dialog)) {
        return;
      }
      restoreSize(modal, dialog);
    });
  }

  function init() {
    scan(document);

    const observer = new MutationObserver((mutations) => {
      mutations.forEach((mutation) => {
        mutation.addedNodes.forEach((node) => {
          if (node instanceof HTMLElement) {
            scan(node);
          }
        });
      });
    });
    observer.observe(document.body, { childList: true, subtree: true });

    let resizeFrame = 0;
    window.addEventListener('resize', () => {
      if (resizeFrame) {
        window.cancelAnimationFrame(resizeFrame);
      }
      resizeFrame = window.requestAnimationFrame(() => {
        resizeFrame = 0;
        refreshViewportSizes();
      });
    });

    if (desktopMedia && typeof desktopMedia.addEventListener === 'function') {
      desktopMedia.addEventListener('change', refreshViewportSizes);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, { once: true });
  } else {
    init();
  }
})();
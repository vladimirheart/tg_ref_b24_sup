(function () {
  if (window.DialogsShellRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    let fallbackModalBackdrop = null;

    function debugLog(eventName, payload) {
      if (typeof options.debugLog === 'function') {
        options.debugLog(eventName, payload);
      }
    }

    function ensureFallbackModalBackdrop() {
      if (fallbackModalBackdrop && document.body.contains(fallbackModalBackdrop)) {
        return fallbackModalBackdrop;
      }
      const backdrop = document.createElement('div');
      backdrop.className = 'modal-backdrop fade show';
      backdrop.dataset.fallbackModalBackdrop = 'true';
      document.body.appendChild(backdrop);
      fallbackModalBackdrop = backdrop;
      return backdrop;
    }

    function removeFallbackModalBackdrop() {
      if (fallbackModalBackdrop && document.body.contains(fallbackModalBackdrop)) {
        fallbackModalBackdrop.remove();
      }
      fallbackModalBackdrop = null;
    }

    function showModalSafe(modalEl, modalInstance) {
      if (!modalEl) return;
      debugLog('showModalSafe.called', {
        modalId: modalEl.id || null,
        viaBootstrap: Boolean(modalInstance),
        classList: modalEl.className,
        styleDisplay: modalEl.style?.display || '',
      });
      if (modalInstance) {
        modalInstance.show();
        debugLog('showModalSafe.bootstrap.show()', { modalId: modalEl.id || null });
        return;
      }
      modalEl.style.display = 'block';
      modalEl.classList.add('show');
      modalEl.removeAttribute('aria-hidden');
      modalEl.setAttribute('aria-modal', 'true');
      document.body.classList.add('modal-open');
      ensureFallbackModalBackdrop();
      modalEl.dispatchEvent(new Event('shown.bs.modal'));
      debugLog('showModalSafe.fallback.show()', {
        modalId: modalEl.id || null,
        classList: modalEl.className,
        styleDisplay: modalEl.style?.display || '',
        bodyModalOpen: document.body.classList.contains('modal-open'),
      });
    }

    function hideModalSafe(modalEl, modalInstance) {
      if (!modalEl) return;
      if (modalInstance) {
        modalInstance.hide();
        return;
      }
      const hideEvent = new Event('hide.bs.modal', { cancelable: true });
      modalEl.dispatchEvent(hideEvent);
      if (hideEvent.defaultPrevented) return;
      modalEl.classList.remove('show');
      modalEl.style.display = 'none';
      modalEl.setAttribute('aria-hidden', 'true');
      modalEl.removeAttribute('aria-modal');
      document.body.classList.remove('modal-open');
      removeFallbackModalBackdrop();
      modalEl.dispatchEvent(new Event('hidden.bs.modal'));
    }

    function bindFallbackModalDismiss(modalEl, modalInstance) {
      if (!modalEl || modalInstance) return;
      modalEl.querySelectorAll('[data-bs-dismiss="modal"]').forEach((button) => {
        button.addEventListener('click', (event) => {
          event.preventDefault();
          hideModalSafe(modalEl, modalInstance);
        });
      });
    }

    function shouldContainScroll(container, deltaY) {
      if (!container || !Number.isFinite(deltaY)) {
        return false;
      }
      if (container.scrollHeight <= container.clientHeight + 1) {
        return false;
      }
      const scrollTop = container.scrollTop;
      const maxScrollTop = container.scrollHeight - container.clientHeight;
      if (deltaY < 0 && scrollTop <= 0) {
        return true;
      }
      if (deltaY > 0 && scrollTop >= maxScrollTop - 1) {
        return true;
      }
      return false;
    }

    function bindModalScrollContainment(container, runtimeOptions = {}) {
      if (!container) return;
      let touchStartY = null;
      container.addEventListener('wheel', (event) => {
        if (runtimeOptions.requireModalVisible && !runtimeOptions.isModalVisible?.()) return;
        if (shouldContainScroll(container, event.deltaY)) {
          event.preventDefault();
        }
      }, { passive: false });
      container.addEventListener('touchstart', (event) => {
        const touch = event.touches && event.touches[0];
        touchStartY = touch ? touch.clientY : null;
      }, { passive: true });
      container.addEventListener('touchmove', (event) => {
        if (runtimeOptions.requireModalVisible && !runtimeOptions.isModalVisible?.()) return;
        const touch = event.touches && event.touches[0];
        if (!touch || touchStartY === null) return;
        const deltaY = touchStartY - touch.clientY;
        if (shouldContainScroll(container, deltaY)) {
          event.preventDefault();
        } else {
          touchStartY = touch.clientY;
        }
      }, { passive: false });
      ['touchend', 'touchcancel'].forEach((eventName) => {
        container.addEventListener(eventName, () => {
          touchStartY = null;
        }, { passive: true });
      });
    }

    function avatarInitial(name) {
      const normalized = String(name || '').trim();
      return normalized ? normalized.charAt(0).toUpperCase() : '—';
    }

    function buildAvatarUrl(userId) {
      const normalized = String(userId || '').trim();
      if (!normalized) return '';
      return `/avatar/${encodeURIComponent(normalized)}`;
    }

    function getDialogUserId(source) {
      if (!source || typeof source !== 'object') return '';
      return String(
        source.userId
        || source.user_id
        || source.clientUserId
        || source.client_user_id
        || source.client?.userId
        || source.client?.user_id
        || source.client?.id
        || source.workspaceClient?.id
        || ''
      ).trim();
    }

    function bindAvatar(container, userId, name) {
      if (!container) return;
      const img = container.querySelector('[data-avatar-img]');
      const initialEl = container.querySelector('[data-avatar-initial]');
      if (initialEl) {
        initialEl.textContent = avatarInitial(name);
        initialEl.classList.remove('d-none');
      }
      if (!img) return;
      img.classList.add('d-none');
      if (!userId) {
        return;
      }
      const src = buildAvatarUrl(userId);
      if (!src) return;
      img.onload = () => {
        img.classList.remove('d-none');
        if (initialEl) initialEl.classList.add('d-none');
      };
      img.onerror = () => {
        img.classList.add('d-none');
        if (initialEl) initialEl.classList.remove('d-none');
      };
      img.src = src;
    }

    function hydrateAvatars(root) {
      const scope = root || document;
      scope.querySelectorAll('[data-avatar-user-id]').forEach((container) => {
        bindAvatar(container, container.dataset.avatarUserId, container.dataset.avatarName);
      });
    }

    function resolveStorageKey(key) {
      return String(key || '').trim();
    }

    function applyCompactMode(enabled) {
      const active = Boolean(enabled);
      document.body.classList.toggle('dialog-compact-mode', active);
      const toggle = options.elements?.dialogCompactToggle;
      if (toggle) {
        toggle.textContent = active ? 'Standard mode' : 'Compact mode';
        toggle.setAttribute('aria-pressed', active ? 'true' : 'false');
      }
    }

    function loadCompactMode() {
      const storageKey = resolveStorageKey(options.storage?.compactMode);
      if (!storageKey) {
        applyCompactMode(true);
        return;
      }
      try {
        const raw = String(localStorage.getItem(storageKey) || '').trim().toLowerCase();
        if (!raw) {
          applyCompactMode(true);
          localStorage.setItem(storageKey, '1');
          return;
        }
        applyCompactMode(raw === '1' || raw === 'true' || raw === 'on');
      } catch (_error) {
        applyCompactMode(true);
      }
    }

    function toggleCompactMode() {
      const next = !document.body.classList.contains('dialog-compact-mode');
      applyCompactMode(next);
      const storageKey = resolveStorageKey(options.storage?.compactMode);
      if (!storageKey) return;
      try {
        localStorage.setItem(storageKey, next ? '1' : '0');
      } catch (_error) {
        // ignore storage write errors
      }
    }

    function applyListOnlyMode(enabled) {
      const active = Boolean(enabled);
      document.body.classList.toggle('dialog-list-only-mode', active);
      const toggle = options.elements?.dialogListOnlyToggle;
      if (toggle) {
        toggle.textContent = active ? 'Полная страница' : 'Только список';
        toggle.setAttribute('aria-pressed', active ? 'true' : 'false');
      }
    }

    function loadListOnlyMode() {
      const storageKey = resolveStorageKey(options.storage?.listOnlyMode);
      if (!storageKey) {
        applyListOnlyMode(false);
        return;
      }
      try {
        const raw = String(localStorage.getItem(storageKey) || '').trim().toLowerCase();
        applyListOnlyMode(raw === '1' || raw === 'true' || raw === 'on');
      } catch (_error) {
        applyListOnlyMode(false);
      }
    }

    function toggleListOnlyMode() {
      const next = !document.body.classList.contains('dialog-list-only-mode');
      applyListOnlyMode(next);
      const storageKey = resolveStorageKey(options.storage?.listOnlyMode);
      if (!storageKey) return;
      try {
        localStorage.setItem(storageKey, next ? '1' : '0');
      } catch (_error) {
        // ignore storage write errors
      }
    }

    function getHeaderCells() {
      if (typeof options.getHeaderCells === 'function') {
        const cells = options.getHeaderCells();
        return Array.isArray(cells) ? cells : [];
      }
      return [];
    }

    function getRows() {
      if (typeof options.rowsList === 'function') {
        const rows = options.rowsList();
        return Array.isArray(rows) ? rows : [];
      }
      return [];
    }

    function saveColumnWidths() {
      const storageKey = resolveStorageKey(options.storage?.widths);
      if (!storageKey) return;
      const widths = {};
      getHeaderCells().forEach((cell) => {
        const key = cell?.dataset?.columnKey;
        if (!key || !cell.style.width) return;
        widths[key] = cell.style.width;
      });
      localStorage.setItem(storageKey, JSON.stringify(widths));
    }

    function restoreColumnWidths() {
      const storageKey = resolveStorageKey(options.storage?.widths);
      const table = options.elements?.table;
      if (!storageKey || !table) return;
      try {
        const raw = localStorage.getItem(storageKey);
        if (!raw) return;
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object') return;
        Object.entries(parsed).forEach(([key, width]) => {
          if (!width) return;
          const header = table.querySelector(`th[data-column-key="${key}"]`);
          if (!header) return;
          header.style.width = width;
          const index = header.cellIndex;
          getRows().forEach((row) => {
            const cell = row.children[index];
            if (cell) cell.style.width = width;
          });
        });
      } catch (_error) {
        // ignore restore errors
      }
    }

    function initColumnResize() {
      getHeaderCells().forEach((header) => {
        if (header?.dataset?.resizable !== 'true') return;
        const oldHandle = header.querySelector('.resize-handle');
        if (oldHandle) oldHandle.remove();
        const handle = document.createElement('div');
        handle.className = 'resize-handle';
        header.appendChild(handle);

        handle.addEventListener('mousedown', (event) => {
          const computed = getComputedStyle(header).width;
          header.style.width = computed;
          const index = header.cellIndex;
          getRows().forEach((row) => {
            const cell = row.children[index];
            if (cell) cell.style.width = computed;
          });

          const startX = event.pageX;
          const startWidth = parseFloat(computed);
          document.documentElement.classList.add('resizing');

          function onMouseMove(moveEvent) {
            const next = startWidth + (moveEvent.pageX - startX);
            if (next < 80) return;
            header.style.width = `${next}px`;
            getRows().forEach((row) => {
              const cell = row.children[index];
              if (cell) cell.style.width = `${next}px`;
            });
          }

          function onMouseUp() {
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
            document.documentElement.classList.remove('resizing');
            saveColumnWidths();
          }

          document.addEventListener('mousemove', onMouseMove);
          document.addEventListener('mouseup', onMouseUp);
          event.preventDefault();
        });
      });
    }

    function initDetailsResize() {
      const detailsSidebar = options.elements?.detailsSidebar;
      const detailsResizeHandle = options.elements?.detailsResizeHandle;
      if (!detailsSidebar || !detailsResizeHandle) return;
      let startX = 0;
      let startWidth = 0;

      function onMouseMove(event) {
        const delta = event.clientX - startX;
        const nextWidth = Math.min(480, Math.max(220, startWidth + delta));
        detailsSidebar.style.flexBasis = `${nextWidth}px`;
      }

      function onMouseUp() {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        document.documentElement.classList.remove('resizing');
      }

      detailsResizeHandle.addEventListener('mousedown', (event) => {
        startX = event.clientX;
        startWidth = detailsSidebar.getBoundingClientRect().width;
        document.documentElement.classList.add('resizing');
        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
        event.preventDefault();
      });
    }

    function setTaskDraft(payload) {
      const storageKey = resolveStorageKey(options.storage?.task);
      if (!storageKey || !payload || !payload.ticketId) return;
      localStorage.setItem(storageKey, JSON.stringify(payload));
    }

    function buildTaskCreateUrl(ticketId, clientName) {
      const params = new URLSearchParams();
      params.set('create', '1');
      if (ticketId) params.set('ticketId', String(ticketId));
      if (clientName) params.set('client', String(clientName));
      return `/tasks?${params.toString()}`;
    }

    function openTaskCreateSurface(ticketId, clientName) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!normalizedTicketId) return;
      const normalizedClientName = String(clientName || '').trim();
      setTaskDraft({
        ticketId: normalizedTicketId,
        client: normalizedClientName,
      });
      window.location.href = buildTaskCreateUrl(normalizedTicketId, normalizedClientName);
    }

    function openDialogSurface(ticketId, row, runtimeOptions = {}) {
      if (!ticketId) {
        return Promise.resolve();
      }
      if (options.workspaceEnabled && typeof options.openDialogWithWorkspaceFallback === 'function') {
        return Promise.resolve(options.openDialogWithWorkspaceFallback(ticketId, row, runtimeOptions));
      }
      return Promise.resolve(options.openDialogDetails?.(ticketId, row));
    }

    return {
      showModalSafe,
      hideModalSafe,
      bindFallbackModalDismiss,
      bindModalScrollContainment,
      avatarInitial,
      buildAvatarUrl,
      getDialogUserId,
      bindAvatar,
      hydrateAvatars,
      applyCompactMode,
      loadCompactMode,
      toggleCompactMode,
      applyListOnlyMode,
      loadListOnlyMode,
      toggleListOnlyMode,
      saveColumnWidths,
      restoreColumnWidths,
      initColumnResize,
      initDetailsResize,
      setTaskDraft,
      buildTaskCreateUrl,
      openTaskCreateSurface,
      openDialogSurface,
    };
  }

  window.DialogsShellRuntime = {
    createRuntime,
  };
})();

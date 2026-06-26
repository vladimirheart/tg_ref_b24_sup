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
      openDialogSurface,
    };
  }

  window.DialogsShellRuntime = {
    createRuntime,
  };
})();

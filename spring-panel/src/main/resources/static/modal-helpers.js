(function() {
  const ICON_MAP = {
    danger: '🛑',
    warning: '⚠️',
    success: '✅',
    info: 'ℹ️',
    primary: 'ℹ️',
    question: '❓',
    prompt: '✏️',
  };

  function ensureBodyReady(fn) {
    if (document.body) {
      fn();
      return;
    }
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', fn, { once: true });
    } else {
      setTimeout(fn, 0);
    }
  }

  function ensureModal(id, html) {
    let el = document.getElementById(id);
    if (el) {
      return el;
    }
    const container = document.createElement('div');
    container.innerHTML = html.trim();
    el = container.firstElementChild;
    ensureBodyReady(() => {
      document.body.appendChild(el);
    });
    return el;
  }

  function ensureMessageModal() {
    return ensureModal(
      'appMessageModal',
      `
      <div class="modal fade small-modal" id="appMessageModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content text-center p-4">
            <div class="modal-body">
              <div class="fs-1" aria-hidden="true" data-message-icon>ℹ️</div>
              <h5 class="mb-2" data-message-title>Сообщение</h5>
              <p class="mb-0" data-message-text>Текст сообщения</p>
            </div>
            <div class="modal-footer justify-content-center border-0">
              <button type="button" class="btn btn-primary" data-bs-dismiss="modal">Ок</button>
            </div>
          </div>
        </div>
      </div>
      `,
    );
  }

  function ensureConfirmModal() {
    return ensureModal(
      'confirmActionModal',
      `
      <div class="modal fade small-modal" id="confirmActionModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content text-center p-4">
            <div class="modal-body">
              <div class="fs-1" aria-hidden="true" data-confirm-icon>🛑</div>
              <h5 class="mb-2" data-confirm-title>Подтвердите действие</h5>
              <p class="text-muted mb-0" data-confirm-message>Вы уверены, что хотите продолжить?</p>
            </div>
            <div class="modal-footer justify-content-center border-0 gap-2">
              <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Отмена</button>
              <button type="button" class="btn btn-danger" data-confirm-accept>Продолжить</button>
            </div>
          </div>
        </div>
      </div>
      `,
    );
  }

  function ensurePromptModal() {
    return ensureModal(
      'promptActionModal',
      `
      <div class="modal fade small-modal" id="promptActionModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content text-center p-4">
            <div class="modal-body">
              <div class="fs-1" aria-hidden="true" data-prompt-icon>✏️</div>
              <h5 class="mb-2" data-prompt-title>Введите значение</h5>
              <p class="text-muted mb-3" data-prompt-message></p>
              <input type="text" class="form-control" data-prompt-input />
              <div class="invalid-feedback d-block text-start mt-2" style="display:none;" data-prompt-feedback></div>
            </div>
            <div class="modal-footer justify-content-center border-0 gap-2">
              <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Отмена</button>
              <button type="button" class="btn btn-primary" data-prompt-accept>Сохранить</button>
            </div>
          </div>
        </div>
      </div>
      `,
    );
  }

  function showAppModalMessage(options = {}) {
    const { title = 'Сообщение', message = '', variant = 'info', icon, onHidden } = options;
    const modalEl = document.getElementById('appMessageModal') || ensureMessageModal();

    if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap?.Modal) {
      if (message) {
        window.alert([title, message].filter(Boolean).join('\n'));
      }
      if (typeof onHidden === 'function') {
        onHidden();
      }
      return false;
    }

    const messageEl = modalEl.querySelector('[data-message-text]');
    const titleEl = modalEl.querySelector('[data-message-title]');
    const iconEl = modalEl.querySelector('[data-message-icon]');

    const textClasses = ['text-muted', 'text-danger', 'text-warning', 'text-success'];
    messageEl.textContent = message || '';
    messageEl.classList.remove(...textClasses);
    if (variant === 'danger') messageEl.classList.add('text-danger');
    else if (variant === 'warning') messageEl.classList.add('text-warning');
    else if (variant === 'success') messageEl.classList.add('text-success');
    else messageEl.classList.add('text-muted');

    if (titleEl) {
      titleEl.textContent = title;
    }
    if (iconEl) {
      iconEl.textContent = icon || ICON_MAP[variant] || ICON_MAP.info;
    }

    const instance = bootstrap.Modal.getOrCreateInstance(modalEl);
    const handleHidden = () => {
      modalEl.removeEventListener('hidden.bs.modal', handleHidden);
      if (typeof onHidden === 'function') {
        onHidden();
      }
    };
    modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });
    instance.show();
    return true;
  }

  function showConfirmActionModal(options = {}) {
    const {
      title = 'Подтвердите действие',
      message = 'Вы уверены, что хотите продолжить?',
      confirmText = 'Продолжить',
      cancelText = 'Отмена',
      confirmVariant = 'danger',
      icon,
      onConfirm,
      onCancel,
    } = options;

    const fallback = () => {
      const confirmed = window.confirm(message);
      if (confirmed && typeof onConfirm === 'function') {
        onConfirm();
      } else if (!confirmed && typeof onCancel === 'function') {
        onCancel();
      }
      return Promise.resolve(confirmed);
    };

    const modalEl = document.getElementById('confirmActionModal') || ensureConfirmModal();
    if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap?.Modal) {
      return fallback();
    }

    const confirmBtn = modalEl.querySelector('[data-confirm-accept]');
    const messageEl = modalEl.querySelector('[data-confirm-message]');
    const titleEl = modalEl.querySelector('[data-confirm-title]');
    const iconEl = modalEl.querySelector('[data-confirm-icon]');
    const cancelBtn = modalEl.querySelector('[data-bs-dismiss="modal"]');

    if (!confirmBtn) {
      return fallback();
    }

    confirmBtn.textContent = confirmText;
    if (cancelBtn) {
      cancelBtn.textContent = cancelText;
    }

    const btnBaseClass = confirmBtn.dataset.baseClass || confirmBtn.className;
    confirmBtn.dataset.baseClass = btnBaseClass;
    confirmBtn.className = btnBaseClass;
    confirmBtn.classList.remove('btn-primary', 'btn-danger', 'btn-warning', 'btn-success', 'btn-outline-danger', 'btn-outline-warning');
    if (confirmVariant === 'danger') confirmBtn.classList.add('btn-danger');
    else if (confirmVariant === 'warning') confirmBtn.classList.add('btn-warning');
    else if (confirmVariant === 'success') confirmBtn.classList.add('btn-success');
    else confirmBtn.classList.add('btn-primary');

    if (messageEl) {
      messageEl.textContent = message;
      messageEl.classList.remove('text-muted', 'text-danger', 'text-warning');
      if (confirmVariant === 'danger') messageEl.classList.add('text-danger');
      else if (confirmVariant === 'warning') messageEl.classList.add('text-warning');
      else messageEl.classList.add('text-muted');
    }
    if (titleEl) {
      titleEl.textContent = title;
    }
    if (iconEl) {
      iconEl.textContent = icon || ICON_MAP[confirmVariant] || ICON_MAP.info;
    }

    return new Promise((resolve) => {
      const instance = bootstrap.Modal.getOrCreateInstance(modalEl);
      let resolved = false;

      const cleanup = () => {
        confirmBtn.removeEventListener('click', handleConfirm);
        modalEl.removeEventListener('hidden.bs.modal', handleHidden);
        if (cancelBtn) {
          cancelBtn.removeEventListener('click', handleCancel);
        }
      };

      const handleConfirm = () => {
        resolved = true;
        if (typeof onConfirm === 'function') {
          onConfirm();
        }
        resolve(true);
        cleanup();
        instance.hide();
      };

      const handleCancel = () => {
        if (resolved) return;
        if (typeof onCancel === 'function') {
          onCancel();
        }
      };

      const handleHidden = () => {
        cleanup();
        if (!resolved) {
          if (typeof onCancel === 'function') {
            onCancel();
          }
          resolve(false);
        }
      };

      confirmBtn.addEventListener('click', handleConfirm, { once: true });
      modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });
      if (cancelBtn) {
        cancelBtn.addEventListener('click', handleCancel, { once: true });
      }

      instance.show();
    });
  }

  function showPromptModal(options = {}) {
    const {
      title = 'Введите значение',
      message = '',
      confirmText = 'Сохранить',
      cancelText = 'Отмена',
      icon = ICON_MAP.prompt,
      defaultValue = '',
      placeholder = '',
      validate,
      onSubmit,
      onCancel,
    } = options;

    const fallback = () => {
      const value = window.prompt(message || title, defaultValue);
      if (value === null) {
        if (typeof onCancel === 'function') {
          onCancel();
        }
        return Promise.resolve(null);
      }
      if (typeof onSubmit === 'function') {
        onSubmit(value);
      }
      return Promise.resolve(value);
    };

    const modalEl = document.getElementById('promptActionModal') || ensurePromptModal();
    if (!modalEl || typeof bootstrap === 'undefined' || !bootstrap?.Modal) {
      return fallback();
    }

    const inputEl = modalEl.querySelector('[data-prompt-input]');
    const feedbackEl = modalEl.querySelector('[data-prompt-feedback]');
    const confirmBtn = modalEl.querySelector('[data-prompt-accept]');
    const cancelBtn = modalEl.querySelector('[data-bs-dismiss="modal"]');
    const titleEl = modalEl.querySelector('[data-prompt-title]');
    const messageEl = modalEl.querySelector('[data-prompt-message]');
    const iconEl = modalEl.querySelector('[data-prompt-icon]');

    if (!inputEl || !confirmBtn) {
      return fallback();
    }

    inputEl.value = defaultValue || '';
    inputEl.placeholder = placeholder || '';
    inputEl.classList.remove('is-invalid');
    if (feedbackEl) {
      feedbackEl.style.display = 'none';
      feedbackEl.textContent = '';
    }

    if (titleEl) titleEl.textContent = title;
    if (messageEl) messageEl.textContent = message;
    if (iconEl) iconEl.textContent = icon || ICON_MAP.prompt;
    confirmBtn.textContent = confirmText;
    if (cancelBtn) cancelBtn.textContent = cancelText;

    return new Promise((resolve) => {
      const instance = bootstrap.Modal.getOrCreateInstance(modalEl);
      let submitted = false;

      const cleanup = () => {
        confirmBtn.removeEventListener('click', handleSubmit);
        modalEl.removeEventListener('hidden.bs.modal', handleHidden);
        modalEl.removeEventListener('shown.bs.modal', handleShown);
        if (cancelBtn) {
          cancelBtn.removeEventListener('click', handleCancel);
        }
      };

      const applyValidationError = (messageText) => {
        if (!feedbackEl) {
          return;
        }
        if (messageText) {
          feedbackEl.textContent = messageText;
          feedbackEl.style.display = '';
          inputEl.classList.add('is-invalid');
        } else {
          feedbackEl.textContent = '';
          feedbackEl.style.display = 'none';
          inputEl.classList.remove('is-invalid');
        }
      };

      const handleSubmit = () => {
        const value = inputEl.value;
        if (typeof validate === 'function') {
          const validationResult = validate(value);
          if (validationResult) {
            applyValidationError(validationResult);
            return;
          }
        }
        submitted = true;
        applyValidationError('');
        if (typeof onSubmit === 'function') {
          onSubmit(value);
        }
        resolve(value);
        cleanup();
        instance.hide();
      };

      const handleCancel = () => {
        if (submitted) return;
        if (typeof onCancel === 'function') {
          onCancel();
        }
      };

      const handleHidden = () => {
        cleanup();
        if (!submitted) {
          if (typeof onCancel === 'function') {
            onCancel();
          }
          resolve(null);
        }
      };

      const handleShown = () => {
        inputEl.focus();
        inputEl.select();
      };

      confirmBtn.addEventListener('click', handleSubmit);
      modalEl.addEventListener('hidden.bs.modal', handleHidden, { once: true });
      modalEl.addEventListener('shown.bs.modal', handleShown, { once: true });
      if (cancelBtn) {
        cancelBtn.addEventListener('click', handleCancel, { once: true });
      }

      instance.show();
    });
  }

  window.showAppModalMessage = showAppModalMessage;
  window.showConfirmActionModal = showConfirmActionModal;
  window.showPromptModal = showPromptModal;
})();

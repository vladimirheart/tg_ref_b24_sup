(function () {
  'use strict';

  if (window.EditGuard) {
    return;
  }

  const ACTIVE_BLOCKS = new Set();
  let globalDirty = false;
  let styleInjected = false;
  let modalInstance = null;
  let modalElement = null;
  let pendingResolver = null;
  let pendingBlock = null;

  function injectStyles() {
    if (styleInjected) return;
    styleInjected = true;
    const style = document.createElement('style');
    style.id = 'edit-guard-style';
    style.textContent = `
      .edit-guard-overlay {
        position: absolute;
        inset: 0;
        display: none;
        align-items: flex-start;
        justify-content: flex-end;
        padding: 1rem;
        background: transparent;
        pointer-events: none;
        z-index: 20;
      }
      .edit-guard-overlay__message {
        font-size: 0.95rem;
        color: #495057;
        text-align: left;
        background: rgba(255, 255, 255, 0.95);
        border: 1px solid rgba(222, 226, 230, 0.9);
        border-radius: 0.75rem;
        padding: 0.75rem 1rem;
        box-shadow: 0 0.75rem 1.5rem rgba(0, 0, 0, 0.08);
        max-width: 18rem;
      }
      .edit-guard-block[data-edit-locked="true"] .edit-guard-overlay {
        display: flex;
        pointer-events: auto;
      }
      .edit-guard-block.edit-guard-dirty .edit-guard-toggle {
        border-color: rgba(220, 53, 69, 0.45) !important;
        color: #dc3545 !important;
      }
      .edit-guard-block[data-edit-locked="true"] [data-edit-guard-edit-visible] {
        display: none !important;
      }
      .edit-guard-block.is-editing [data-edit-guard-edit-visible] {
        display: inline-flex !important;
      }
    `;
    document.head.appendChild(style);
  }

  function ensureModal() {
    if (modalElement) {
      return modalElement;
    }
    modalElement = document.createElement('div');
    modalElement.className = 'modal fade';
    modalElement.id = 'editGuardModal';
    modalElement.tabIndex = -1;
    modalElement.setAttribute('aria-hidden', 'true');
    modalElement.innerHTML = `
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Есть несохранённые изменения</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button>
          </div>
          <div class="modal-body">
            <p class="mb-2">Вы изменили данные, но ещё не сохранили их.</p>
            <p class="mb-0">Сохранить изменения или отменить их?</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" data-edit-guard-action="continue">Продолжить редактирование</button>
            <button type="button" class="btn btn-outline-danger" data-edit-guard-action="discard">Отменить изменения</button>
            <button type="button" class="btn btn-primary" data-edit-guard-action="save">Сохранить</button>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(modalElement);
    if (window.bootstrap && window.bootstrap.Modal) {
      modalInstance = window.bootstrap.Modal.getOrCreateInstance(modalElement, { backdrop: 'static', keyboard: false });
    }
    modalElement.addEventListener('click', (event) => {
      const actionBtn = event.target.closest('[data-edit-guard-action]');
      if (!actionBtn) return;
      const action = actionBtn.getAttribute('data-edit-guard-action');
      if (pendingResolver) {
        pendingResolver(action);
      }
      if (modalInstance) {
        modalInstance.hide();
      }
    });
    modalElement.addEventListener('hidden.bs.modal', () => {
      if (pendingResolver) {
        pendingResolver('continue');
      }
      pendingResolver = null;
      pendingBlock = null;
    });
    return modalElement;
  }

  function showModal(block) {
    ensureModal();
    pendingBlock = block || null;
    return new Promise((resolve) => {
      pendingResolver = resolve;
      if (modalInstance) {
        modalInstance.show();
      } else {
        modalElement.classList.add('show');
        modalElement.style.display = 'block';
      }
    });
  }

  function updateGlobalDirtyState() {
    globalDirty = Array.from(ACTIVE_BLOCKS).some((block) => block && block.isDirty());
  }

  window.addEventListener('beforeunload', (event) => {
    if (!globalDirty) return;
    event.preventDefault();
    event.returnValue = '';
  });

  class EditBlock {
    constructor(element, options = {}) {
      this.element = element;
      this.options = options;
      this.header = null;
      this.overlay = null;
      this.toggleButton = null;
      this._dirty = false;
      this._editing = false;
      this.snapshot = [];
      this.changeListener = this.handleChange.bind(this);
      this.inputListener = this.handleInput.bind(this);
      this.clickListener = this.handleClick.bind(this);
      this.reverting = false;
    }

    init() {
      if (!(this.element instanceof HTMLElement)) {
        return;
      }
      injectStyles();
      this.element.classList.add('edit-guard-block');
      this.element.setAttribute('data-edit-locked', 'true');
      this.header = this.element.querySelector('.card-header, [data-edit-guard-header]') || this.element;
      this.createToggleButton();
      this.createOverlay();
      this.element.addEventListener('change', this.changeListener, true);
      this.element.addEventListener('input', this.inputListener, true);
      this.element.addEventListener('click', this.clickListener, true);
      ACTIVE_BLOCKS.add(this);
      this.lock();
    }

    destroy() {
      this.element.removeEventListener('change', this.changeListener, true);
      this.element.removeEventListener('input', this.inputListener, true);
      this.element.removeEventListener('click', this.clickListener, true);
      ACTIVE_BLOCKS.delete(this);
      updateGlobalDirtyState();
    }

    createOverlay() {
      const overlay = document.createElement('div');
      overlay.className = 'edit-guard-overlay';
      overlay.innerHTML = '';
      this.element.style.position = this.element.style.position || 'relative';
      this.element.appendChild(overlay);
      this.overlay = overlay;
      this.updateOverlayPosition();
      window.addEventListener('resize', () => this.updateOverlayPosition());
    }

    updateOverlayPosition() {
      if (!this.overlay) return;
      const header = this.header;
      if (header && header !== this.element) {
        const headerHeight = header.offsetHeight || 0;
        this.overlay.style.top = `${headerHeight}px`;
      } else {
        this.overlay.style.top = '0';
      }
    }

    createToggleButton() {
      if (!this.header) return;
      const existingToggle = this.header.querySelector('[data-edit-guard-toggle]');
      if (existingToggle) {
        this.toggleButton = existingToggle;
        this.toggleButton.classList.add('edit-guard-toggle');
        this.toggleButton.addEventListener('click', () => this.handleToggle());
        return;
      }
      const container = document.createElement('div');
      container.className = 'd-flex flex-wrap gap-2';
      container.style.marginLeft = 'auto';
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'btn btn-sm btn-outline-primary edit-guard-toggle';
      toggle.textContent = '✏️ Редактировать';
      toggle.setAttribute('data-edit-guard-toggle', 'true');
      toggle.addEventListener('click', () => this.handleToggle());
      container.appendChild(toggle);
      this.header.appendChild(container);
      this.toggleButton = toggle;
    }

    handleToggle() {
      if (!this._editing) {
        this.unlock();
        return;
      }
      this.attemptClose();
    }

    async attemptClose() {
      if (!this._editing) {
        this.lock();
        return;
      }
      if (!this._dirty) {
        this.lock();
        return;
      }
      const action = await showModal(this);
      if (action === 'save') {
        await this.handleSaveAction();
      } else if (action === 'discard') {
        this.revertChanges();
        this.lock();
      } else {
        // continue editing
      }
    }

    async handleSaveAction() {
      if (typeof this.options.onSave === 'function') {
        try {
          const result = await this.options.onSave(this);
          if (result !== false) {
            this.markSaved();
          }
        } catch (error) {
          console.error('EditGuard save handler failed:', error);
        }
      } else {
        this.markSaved();
      }
    }

    lock() {
      this._editing = false;
      this.element.setAttribute('data-edit-locked', 'true');
      this.element.classList.remove('is-editing');
      if (this.toggleButton) {
        this.toggleButton.textContent = '✏️ Редактировать';
        this.toggleButton.classList.remove('btn-outline-success');
        this.toggleButton.classList.add('btn-outline-primary');
      }
      this.enableControls(false);
      this.captureSnapshot();
      this.updateOverlayPosition();
    }

    unlock() {
      this._editing = true;
      this.element.setAttribute('data-edit-locked', 'false');
      this.element.classList.add('is-editing');
      if (this.toggleButton) {
        this.toggleButton.textContent = '✔️ Готово';
        this.toggleButton.classList.remove('btn-outline-primary');
        this.toggleButton.classList.add('btn-outline-success');
      }
      this.captureSnapshot();
      this.enableControls(true);
      this.setDirty(false);
    }

    enableControls(enable) {
      const controls = this.getControls();
      controls.forEach((control) => {
        if (!(control instanceof HTMLElement)) return;
        if (control.closest('[data-edit-guard-skip]')) return;
        if (control.dataset.editGuardDisabled === 'true') return;
        if (control.hasAttribute('data-edit-guard-disable-force')) {
          control.disabled = !enable;
          return;
        }
        if (control.matches('input, textarea, select, button, .dropdown-toggle')) {
          if (!enable) {
            if (!control.hasAttribute('data-edit-guard-original-disabled')) {
              if (control.disabled) {
                control.setAttribute('data-edit-guard-original-disabled', 'true');
              }
            }
            control.disabled = true;
          } else {
            if (control.getAttribute('data-edit-guard-original-disabled') === 'true') {
              control.disabled = true;
            } else {
              control.disabled = false;
            }
          }
        }
      });
      if (!enable) {
        controls.forEach((control) => {
          if (control instanceof HTMLElement) {
            control.blur();
          }
        });
      }
    }

    getControls() {
      return Array.from(
        this.element.querySelectorAll('input, textarea, select, [contenteditable="true"], button[data-edit-guard-disable-force]')
      );
    }

    captureSnapshot() {
      const controls = this.getControls();
      this.snapshot = controls.map((control) => ({
        element: control,
        value: this.getControlValue(control),
      }));
      return this.snapshot;
    }

    revertChanges() {
      this.reverting = true;
      try {
        this.snapshot.forEach(({ element, value }) => {
          if (!element || !(element instanceof HTMLElement)) return;
          this.setControlValue(element, value);
        });
        this.setDirty(false);
      } finally {
        this.reverting = false;
      }
    }

    getControlValue(control) {
      if (!(control instanceof HTMLElement)) return null;
      if (control.hasAttribute('data-edit-value')) {
        return control.getAttribute('data-edit-value');
      }
      if (control instanceof HTMLInputElement) {
        if (control.type === 'checkbox' || control.type === 'radio') {
          return control.checked;
        }
        return control.value;
      }
      if (control instanceof HTMLTextAreaElement || control instanceof HTMLSelectElement) {
        return control.value;
      }
      if (control.isContentEditable) {
        return control.innerHTML;
      }
      return control.textContent;
    }

    setControlValue(control, value) {
      if (!(control instanceof HTMLElement)) return;
      if (control instanceof HTMLInputElement) {
        if (control.type === 'checkbox' || control.type === 'radio') {
          control.checked = Boolean(value);
        } else {
          control.value = value != null ? value : '';
        }
        control.dispatchEvent(new Event('change', { bubbles: true }));
        return;
      }
      if (control instanceof HTMLTextAreaElement || control instanceof HTMLSelectElement) {
        control.value = value != null ? value : '';
        control.dispatchEvent(new Event('change', { bubbles: true }));
        return;
      }
      if (control.isContentEditable) {
        control.innerHTML = value != null ? value : '';
        control.dispatchEvent(new Event('input', { bubbles: true }));
        return;
      }
      if (control.hasAttribute('data-edit-value')) {
        control.setAttribute('data-edit-value', value != null ? value : '');
      }
    }

    handleChange(event) {
      if (!this._editing || this.reverting) return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (!this.shouldTrackTarget(target)) return;
      this.setDirty(true);
    }

    handleInput(event) {
      if (!this._editing || this.reverting) return;
      const target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (!this.shouldTrackTarget(target)) return;
      this.setDirty(true);
    }

    handleClick(event) {
      if (!this._editing || this.reverting) return;
      const target = event.target instanceof HTMLElement ? event.target : null;
      if (!target) return;
      const actionable = target.closest('[data-edit-mark-dirty], button[data-action], a[data-action]');
      if (!actionable) return;
      const actionName = actionable.getAttribute('data-action');
      if (actionName && ['toggle-photo-edit', 'cancel-photo-edit', 'preview-photo'].includes(actionName)) {
        return;
      }
      if (actionable.hasAttribute('data-edit-guard-skip')) return;
      this.setDirty(true);
    }

    shouldTrackTarget(target) {
      if (!(target instanceof HTMLElement)) return false;
      if (target.closest('[data-edit-guard-skip]')) return false;
      if (!target.matches('input, textarea, select, [contenteditable="true"]')) return false;
      return true;
    }

    setDirty(state) {
      if (this._dirty === state) return;
      this._dirty = state;
      this.element.classList.toggle('edit-guard-dirty', state);
      updateGlobalDirtyState();
      if (typeof this.options.onDirtyChange === 'function') {
        this.options.onDirtyChange(state, this);
      }
    }

    isDirty() {
      return this._dirty;
    }

    markSaved(options = {}) {
      this.captureSnapshot();
      this.setDirty(false);
      if (!options.keepEditing) {
        this.lock();
      }
    }
  }

  window.EditGuard = {
    registerBlock(element, options) {
      if (!element) return null;
      const block = new EditBlock(element, options);
      block.init();
      return block;
    },
    markBlockSaved(element, options = {}) {
      const block = Array.from(ACTIVE_BLOCKS).find((item) => item.element === element);
      if (block) {
        block.markSaved(options);
      }
    },
    markAllSaved() {
      ACTIVE_BLOCKS.forEach((block) => block.markSaved());
      updateGlobalDirtyState();
    },
    refreshBlockSnapshot(element) {
      const block = Array.from(ACTIVE_BLOCKS).find((item) => item.element === element);
      if (block) {
        block.markSaved({ keepEditing: true });
      }
    },
    hasUnsavedChanges() {
      return globalDirty;
    }
  };
})();

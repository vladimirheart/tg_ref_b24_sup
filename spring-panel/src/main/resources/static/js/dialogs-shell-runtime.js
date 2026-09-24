(function () {
  if (window.DialogsShellRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const avatarStateByUserId = new Map();

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
      const normalizedUserId = String(userId || '').trim();
      const img = container.querySelector('[data-avatar-img]');
      const initialEl = container.querySelector('[data-avatar-initial]');
      if (initialEl) {
        initialEl.textContent = avatarInitial(name);
        initialEl.classList.remove('d-none');
      }
      if (!img) return;
      if (!normalizedUserId) {
        container.dataset.avatarBoundUserId = '';
        delete img.dataset.avatarSrc;
        delete img.dataset.avatarLoaded;
        img.classList.add('d-none');
        return;
      }
      const src = buildAvatarUrl(normalizedUserId);
      if (!src) return;
      const boundUserId = String(container.dataset.avatarBoundUserId || '').trim();
      const currentSrc = String(img.dataset.avatarSrc || img.getAttribute('src') || '').trim();
      if (boundUserId === normalizedUserId && currentSrc === src && img.dataset.avatarLoaded === 'true') {
        img.classList.remove('d-none');
        if (initialEl) initialEl.classList.add('d-none');
        return;
      }
      const cachedState = avatarStateByUserId.get(normalizedUserId);
      container.dataset.avatarBoundUserId = normalizedUserId;
      img.dataset.avatarSrc = src;
      if (cachedState === 'loaded') {
        if (currentSrc !== src) {
          img.src = src;
        }
        img.dataset.avatarLoaded = 'true';
        img.classList.remove('d-none');
        if (initialEl) initialEl.classList.add('d-none');
        return;
      }
      if (cachedState === 'missing') {
        img.dataset.avatarLoaded = 'false';
        img.classList.add('d-none');
        if (initialEl) initialEl.classList.remove('d-none');
        return;
      }
      img.classList.add('d-none');
      img.onload = () => {
        avatarStateByUserId.set(normalizedUserId, 'loaded');
        img.dataset.avatarLoaded = 'true';
        img.classList.remove('d-none');
        if (initialEl) initialEl.classList.add('d-none');
      };
      img.onerror = () => {
        avatarStateByUserId.set(normalizedUserId, 'missing');
        img.dataset.avatarLoaded = 'false';
        img.classList.add('d-none');
        if (initialEl) initialEl.classList.remove('d-none');
      };
      if (currentSrc !== src) {
        img.src = src;
      } else if (img.complete && img.naturalWidth > 0) {
        avatarStateByUserId.set(normalizedUserId, 'loaded');
        img.dataset.avatarLoaded = 'true';
        img.classList.remove('d-none');
        if (initialEl) initialEl.classList.add('d-none');
      }
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

    function applyListOnlyMode(enabled) {
      const active = Boolean(enabled);
      document.documentElement.classList.toggle('dialog-list-only-prepaint', active);
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

    function getColumnMeta() {
      if (typeof options.getColumnMeta === 'function') {
        const meta = options.getColumnMeta();
        return Array.isArray(meta) ? meta : [];
      }
      return [];
    }

    function getColumnState() {
      if (typeof options.getColumnState === 'function') {
        const state = options.getColumnState();
        return state && typeof state === 'object' ? state : {};
      }
      return {};
    }

    function getDefaultColumnState() {
      if (typeof options.getDefaultColumnState === 'function') {
        const state = options.getDefaultColumnState();
        return state && typeof state === 'object' ? state : {};
      }
      return {};
    }

    function getColumnKeys() {
      return getColumnMeta()
        .map((item) => String(item?.key || '').trim())
        .filter(Boolean);
    }

    function getColumnOrder() {
      if (typeof options.getColumnOrder === 'function') {
        const order = options.getColumnOrder();
        return Array.isArray(order)
          ? order.map((item) => String(item || '').trim()).filter(Boolean)
          : [];
      }
      return [];
    }

    function getDefaultColumnOrder() {
      if (typeof options.getDefaultColumnOrder === 'function') {
        const order = options.getDefaultColumnOrder();
        return Array.isArray(order)
          ? order.map((item) => String(item || '').trim()).filter(Boolean)
          : [];
      }
      return getColumnKeys();
    }

    function setColumnState(nextState) {
      if (typeof options.setColumnState === 'function') {
        options.setColumnState(nextState);
      }
    }

    function setColumnOrder(nextOrder) {
      if (typeof options.setColumnOrder === 'function') {
        options.setColumnOrder(Array.isArray(nextOrder) ? nextOrder : getDefaultColumnOrder());
      }
    }

    function cloneColumnState(source) {
      return source && typeof source === 'object' ? { ...source } : {};
    }

    function normalizeColumnOrder(sourceOrder) {
      const availableKeys = getColumnKeys();
      const seen = new Set();
      const normalized = [];
      (Array.isArray(sourceOrder) ? sourceOrder : []).forEach((key) => {
        const normalizedKey = String(key || '').trim();
        if (!normalizedKey || seen.has(normalizedKey) || !availableKeys.includes(normalizedKey)) {
          return;
        }
        seen.add(normalizedKey);
        normalized.push(normalizedKey);
      });
      availableKeys.forEach((key) => {
        if (seen.has(key)) return;
        seen.add(key);
        normalized.push(key);
      });
      return normalized;
    }

    function getColumnItemOrderFromList(columnsList) {
      return Array.from(columnsList?.querySelectorAll('[data-column-item]') || [])
        .map((item) => String(item.getAttribute('data-column-item') || '').trim())
        .filter(Boolean);
    }

    function reorderRowByKeys(row, orderedKeys) {
      if (!row || !Array.isArray(orderedKeys) || !orderedKeys.length) return;
      const cells = Array.from(row.children || []);
      if (!cells.length) return;
      const byKey = new Map();
      const fixedCells = [];
      cells.forEach((cell) => {
        const key = String(cell?.dataset?.columnKey || '').trim();
        if (!key || !orderedKeys.includes(key)) {
          fixedCells.push(cell);
          return;
        }
        byKey.set(key, cell);
      });
      const fragment = document.createDocumentFragment();
      fixedCells.forEach((cell) => {
        fragment.appendChild(cell);
      });
      orderedKeys.forEach((key) => {
        const cell = byKey.get(key);
        if (cell) {
          fragment.appendChild(cell);
        }
      });
      row.appendChild(fragment);
    }

    function loadColumnOrder() {
      const storageKey = resolveStorageKey(options.storage?.columnOrder);
      const versionKey = resolveStorageKey(options.storage?.columnOrderVersion);
      const schemaVersion = String(options.storage?.columnSchemaVersion || '').trim();
      const defaultOrder = normalizeColumnOrder(getDefaultColumnOrder());
      if (!storageKey) {
        setColumnOrder(defaultOrder);
        return;
      }
      try {
        const raw = localStorage.getItem(storageKey);
        let nextOrder = defaultOrder;
        if (raw) {
          const parsed = JSON.parse(raw);
          nextOrder = normalizeColumnOrder(parsed);
        }
        const migrationRequired = Boolean(versionKey && schemaVersion)
          && String(localStorage.getItem(versionKey) || '') !== schemaVersion;
        if (migrationRequired) {
          nextOrder = ['actions', ...nextOrder.filter((key) => key !== 'actions')];
          localStorage.setItem(storageKey, JSON.stringify(nextOrder));
          localStorage.setItem(versionKey, schemaVersion);
        }
        setColumnOrder(nextOrder);
      } catch (_error) {
        setColumnOrder(defaultOrder);
      }
    }

    function persistColumnOrder() {
      const storageKey = resolveStorageKey(options.storage?.columnOrder);
      if (!storageKey) return;
      const currentOrder = normalizeColumnOrder(getColumnOrder());
      const defaultOrder = normalizeColumnOrder(getDefaultColumnOrder());
      if (JSON.stringify(currentOrder) === JSON.stringify(defaultOrder)) {
        localStorage.removeItem(storageKey);
        return;
      }
      localStorage.setItem(storageKey, JSON.stringify(currentOrder));
    }

    function applyColumnOrder() {
      const table = options.elements?.table;
      if (!table) return;
      const headerRow = table.tHead?.rows?.[0];
      if (!headerRow) return;
      const normalizedOrder = normalizeColumnOrder(getColumnOrder());
      if (!normalizedOrder.length) return;
      reorderRowByKeys(headerRow, normalizedOrder);
      Array.from(table.tBodies || []).forEach((tbody) => {
        Array.from(tbody.rows || []).forEach((row) => reorderRowByKeys(row, normalizedOrder));
      });
      Array.from(table.tFoot?.rows || []).forEach((row) => reorderRowByKeys(row, normalizedOrder));
    }

    function loadColumnState() {
      const storageKey = resolveStorageKey(options.storage?.columns);
      const versionKey = resolveStorageKey(options.storage?.columnsVersion);
      const schemaVersion = String(options.storage?.columnSchemaVersion || '').trim();
      if (!storageKey) return;
      try {
        const nextState = cloneColumnState(getDefaultColumnState());
        const raw = localStorage.getItem(storageKey);
        if (raw) {
          const parsed = JSON.parse(raw);
          if (parsed && typeof parsed === 'object') {
            Object.keys(nextState).forEach((key) => {
              if (Object.prototype.hasOwnProperty.call(parsed, key)) {
                nextState[key] = Boolean(parsed[key]);
              }
            });
          }
        }
        const migrationRequired = Boolean(versionKey && schemaVersion)
          && String(localStorage.getItem(versionKey) || '') !== schemaVersion;
        if (migrationRequired) {
          if (Object.prototype.hasOwnProperty.call(nextState, 'select')) {
            nextState.select = false;
          }
          localStorage.setItem(storageKey, JSON.stringify(nextState));
          localStorage.setItem(versionKey, schemaVersion);
        }
        setColumnState(nextState);
      } catch (_error) {
        setColumnState(cloneColumnState(getDefaultColumnState()));
      }
    }

    function persistColumnState() {
      const storageKey = resolveStorageKey(options.storage?.columns);
      if (!storageKey) return;
      localStorage.setItem(storageKey, JSON.stringify(getColumnState()));
    }

    function applyColumnState() {
      const table = options.elements?.table;
      if (!table) return;
      applyColumnOrder();
      const headerRow = table.tHead?.rows?.[0];
      if (!headerRow) return;
      const columnState = getColumnState();
      getColumnMeta().forEach(({ key }) => {
        const visible = columnState[key] !== false;
        const headerCell = headerRow.querySelector(`th[data-column-key="${key}"]`);
        if (!headerCell) return;
        const columnIndex = headerCell.cellIndex;
        headerCell.classList.toggle('d-none', !visible);
        Array.from(table.tBodies || []).forEach((tbody) => {
          Array.from(tbody.rows || []).forEach((row) => {
            const cell = row.children?.[columnIndex];
            if (cell) {
              cell.classList.toggle('d-none', !visible);
            }
          });
        });
      });
      restoreColumnWidths();
    }

    // Dialog column personalization v8
    function syncColumnOrderControls() {
      const columnsList = options.elements?.columnsList;
      if (!columnsList) return;
      const items = Array.from(columnsList.querySelectorAll('[data-column-item]'));
      items.forEach((item, index) => {
        const position = item.querySelector('[data-column-position]');
        if (position) {
          position.textContent = String(index + 1);
          position.setAttribute('aria-label', `Позиция ${index + 1} из ${items.length}`);
        }
        const moveUp = item.querySelector('[data-column-move="-1"]');
        const moveDown = item.querySelector('[data-column-move="1"]');
        if (moveUp instanceof HTMLButtonElement) moveUp.disabled = index === 0;
        if (moveDown instanceof HTMLButtonElement) moveDown.disabled = index === items.length - 1;
      });
    }

    function syncColumnsList() {
      const columnsList = options.elements?.columnsList;
      if (!columnsList) return;
      const columnState = getColumnState();
      columnsList.querySelectorAll('[data-column-toggle]').forEach((checkbox) => {
        const key = checkbox.dataset.columnToggle;
        checkbox.checked = columnState[key] !== false;
      });
      normalizeColumnOrder(getColumnOrder()).forEach((key) => {
        const item = columnsList.querySelector(`[data-column-item="${key}"]`);
        if (item) {
          columnsList.appendChild(item);
        }
      });
      syncColumnOrderControls();
    }

    function buildColumnsList() {
      const columnsList = options.elements?.columnsList;
      if (!columnsList) return;
      columnsList.innerHTML = '';
      const metaByKey = new Map(getColumnMeta().map((item) => [item.key, item]));
      normalizeColumnOrder(getColumnOrder()).forEach((key) => {
        const meta = metaByKey.get(key);
        if (!meta) return;
        const item = document.createElement('div');
        item.className = 'dialog-column-order-item';
        item.setAttribute('data-column-item', key);
        item.innerHTML = `
          <div class="dialog-column-option">
            <button
              type="button"
              class="dialog-column-option__drag"
              draggable="true"
              data-column-drag-handle
              aria-label="Перетащить колонку ${meta.label}"
              title="Перетащить"
            >⋮⋮</button>
            <span class="dialog-column-option__position" data-column-position></span>
            <label class="dialog-column-option__visibility">
              <input type="checkbox" class="form-check-input" data-column-toggle="${key}">
              <span class="dialog-column-option__label">${meta.label}</span>
            </label>
            <span class="dialog-column-option__actions" aria-label="Изменить позицию колонки">
              <button type="button" class="dialog-column-option__move" data-column-move="-1" aria-label="Переместить ${meta.label} выше" title="Выше">↑</button>
              <button type="button" class="dialog-column-option__move" data-column-move="1" aria-label="Переместить ${meta.label} ниже" title="Ниже">↓</button>
            </span>
          </div>
        `;
        columnsList.appendChild(item);
      });
      syncColumnsList();
    }

    function bindColumnOrderEvents() {
      const columnsList = options.elements?.columnsList;
      if (!columnsList || columnsList.dataset.columnOrderBound === 'true') return;
      let draggingItem = null;

      columnsList.addEventListener('click', (event) => {
        const moveButton = event.target instanceof Element
          ? event.target.closest('[data-column-move]')
          : null;
        if (!(moveButton instanceof HTMLButtonElement)) return;
        const item = moveButton.closest('[data-column-item]');
        if (!(item instanceof HTMLElement)) return;
        const direction = Number.parseInt(moveButton.dataset.columnMove || '0', 10);
        if (direction < 0) {
          const previous = item.previousElementSibling;
          if (previous) columnsList.insertBefore(item, previous);
        } else if (direction > 0) {
          const next = item.nextElementSibling;
          if (next) columnsList.insertBefore(next, item);
        }
        syncColumnOrderControls();
      });

      columnsList.addEventListener('dragstart', (event) => {
        const handle = event.target instanceof Element
          ? event.target.closest('[data-column-drag-handle]')
          : null;
        const item = handle?.closest('[data-column-item]');
        if (!(handle instanceof HTMLElement) || !(item instanceof HTMLElement)) {
          event.preventDefault();
          return;
        }
        draggingItem = item;
        item.classList.add('is-dragging');
        if (event.dataTransfer) {
          event.dataTransfer.effectAllowed = 'move';
          event.dataTransfer.setData('text/plain', String(item.getAttribute('data-column-item') || ''));
        }
      });

      columnsList.addEventListener('dragover', (event) => {
        if (!draggingItem) return;
        const target = event.target instanceof Element
          ? event.target.closest('[data-column-item]')
          : null;
        if (!(target instanceof HTMLElement) || target === draggingItem) return;
        event.preventDefault();
        const targetRect = target.getBoundingClientRect();
        const insertAfter = event.clientY > targetRect.top + (targetRect.height / 2);
        const referenceNode = insertAfter ? target.nextElementSibling : target;
        if (referenceNode === draggingItem) return;
        columnsList.insertBefore(draggingItem, referenceNode);
        syncColumnOrderControls();
      });

      columnsList.addEventListener('drop', (event) => {
        if (!draggingItem) return;
        event.preventDefault();
      });

      columnsList.addEventListener('dragend', () => {
        if (!draggingItem) return;
        draggingItem.classList.remove('is-dragging');
        draggingItem = null;
        syncColumnOrderControls();
      });

      columnsList.dataset.columnOrderBound = 'true';
    }

    function resetColumnState() {
      setColumnState(cloneColumnState(getDefaultColumnState()));
      setColumnOrder(normalizeColumnOrder(getDefaultColumnOrder()));
      persistColumnOrder();
      persistColumnState();
      applyColumnState();
      syncColumnsList();
    }

    function bindColumnStateEvents() {
      const columnsBtn = options.elements?.columnsBtn;
      const columnsModalEl = options.elements?.columnsModalEl;
      const columnsModal = options.elements?.columnsModal;
      const columnsApply = options.elements?.columnsApply;
      const columnsReset = options.elements?.columnsReset;
      const columnsList = options.elements?.columnsList;

      if (columnsBtn && columnsModalEl) {
        columnsBtn.addEventListener('click', () => {
          syncColumnsList();
          showModalSafe(columnsModalEl, columnsModal);
        });
      }

      if (columnsApply) {
        columnsApply.addEventListener('click', () => {
          if (columnsList) {
            const nextState = cloneColumnState(getColumnState());
            columnsList.querySelectorAll('[data-column-toggle]').forEach((checkbox) => {
              const key = checkbox.dataset.columnToggle;
              nextState[key] = checkbox.checked;
            });
            setColumnState(nextState);
            setColumnOrder(normalizeColumnOrder(getColumnItemOrderFromList(columnsList)));
            persistColumnState();
            persistColumnOrder();
            applyColumnState();
          }
          hideModalSafe(columnsModalEl, columnsModal);
        });
      }

      if (columnsReset) {
        columnsReset.addEventListener('click', () => {
          resetColumnState();
        });
      }
    }
    const DIALOG_COLUMN_WIDTHS_PREFERENCE = 'dialogsColumnWidths';
    const DIALOG_COLUMN_WIDTHS_MAX = 1200;
    const DIALOG_COLUMN_WIDTHS_MIN = Object.freeze({
      actions: 38,
      select: 52,
      ticket: 72,
      client: 96,
      status: 84,
      channel: 72,
      business: 80,
      problem: 110,
      location: 90,
      categories: 100,
      responsible: 96,
      created: 82,
      sla: 80,
    });
    let dialogColumnWidthsBootstrapResolved = false;

    function getUiPreferenceApi() {
      const api = window.iguanaUiPreferences;
      return api && typeof api.get === 'function' && typeof api.set === 'function' ? api : null;
    }

    function normalizeDialogColumnWidths(value) {
      const source = value && typeof value === 'object' && !Array.isArray(value) ? value : {};
      const allowed = new Set(getColumnKeys());
      const result = {};
      Object.entries(source).forEach(([rawKey, rawWidth]) => {
        const key = String(rawKey || '').trim();
        const width = Number.parseInt(rawWidth, 10);
        if (!allowed.has(key) || !Number.isFinite(width)) return;
        const minWidth = DIALOG_COLUMN_WIDTHS_MIN[key] || 64;
        result[key] = Math.max(minWidth, Math.min(DIALOG_COLUMN_WIDTHS_MAX, width));
      });
      return result;
    }

    function readPersistedColumnWidths() {
      const api = getUiPreferenceApi();
      if (!api) return {};
      if (!dialogColumnWidthsBootstrapResolved) {
        const bootstrap = window.__IGUANA_UI_PREFS_BOOTSTRAP__;
        const hasServerValue = bootstrap && typeof bootstrap === 'object'
          && Object.prototype.hasOwnProperty.call(bootstrap, DIALOG_COLUMN_WIDTHS_PREFERENCE);
        if (!hasServerValue && typeof api.remove === 'function') {
          api.remove(DIALOG_COLUMN_WIDTHS_PREFERENCE, 'server');
        }
        dialogColumnWidthsBootstrapResolved = true;
      }
      return normalizeDialogColumnWidths(api.get(DIALOG_COLUMN_WIDTHS_PREFERENCE));
    }

    function applyColumnWidth(key, width) {
      const table = options.elements?.table;
      if (!table || !key) return null;
      const header = table.querySelector('th[data-column-key="' + key + '"]');
      if (!header) return null;
      const parsed = Number.parseInt(width, 10);
      if (!Number.isFinite(parsed)) return null;
      const minWidth = DIALOG_COLUMN_WIDTHS_MIN[key] || 64;
      const nextWidth = Math.max(minWidth, Math.min(DIALOG_COLUMN_WIDTHS_MAX, parsed));
      const widthValue = nextWidth + 'px';
      header.style.width = widthValue;
      header.style.minWidth = widthValue;
      const handle = header.querySelector('.resize-handle');
      if (handle) handle.setAttribute('aria-valuenow', String(nextWidth));
      const index = header.cellIndex;
      getRows().forEach((row) => {
        const cell = row.children[index];
        if (!cell) return;
        cell.style.width = widthValue;
        cell.style.minWidth = widthValue;
      });
      return nextWidth;
    }

    function syncDialogTableWidth() {
      const table = options.elements?.table;
      if (!table) return;
      let total = 0;
      getHeaderCells().forEach((header) => {
        if (!header || header.classList.contains('d-none')) return;
        const width = Number.parseFloat(header.style.width) || header.getBoundingClientRect().width;
        if (Number.isFinite(width) && width > 0) total += width;
      });
      if (total > 0) {
        const widthValue = Math.ceil(total) + 'px';
        table.style.width = widthValue;
        table.style.minWidth = widthValue;
      }
    }

    function freezeVisibleColumnWidths() {
      const frozen = {};
      getHeaderCells().forEach((header) => {
        const key = String(header?.dataset?.columnKey || '').trim();
        if (!key || header.classList.contains('d-none')) return;
        const measured = Math.round(header.getBoundingClientRect().width);
        const applied = applyColumnWidth(key, measured);
        if (applied != null) frozen[key] = applied;
      });
      syncDialogTableWidth();
      return frozen;
    }

    function saveColumnWidths() {
      const api = getUiPreferenceApi();
      if (!api) return;
      const widths = { ...readPersistedColumnWidths() };
      getHeaderCells().forEach((cell) => {
        const key = String(cell?.dataset?.columnKey || '').trim();
        const width = Number.parseInt(cell?.style?.width || '', 10);
        if (!key || !Number.isFinite(width)) return;
        widths[key] = width;
      });
      api.set(DIALOG_COLUMN_WIDTHS_PREFERENCE, widths, 'dialogs-column-widths');
      if (typeof api.flush === 'function') {
        void api.flush();
      }
    }

    function restoreColumnWidths() {
      const widths = readPersistedColumnWidths();
      const entries = Object.entries(widths);
      if (!entries.length) return;
      entries.forEach(([key, width]) => applyColumnWidth(key, width));
      syncDialogTableWidth();
    }

    function initColumnResize() {
      getHeaderCells().forEach((header) => {
        const key = String(header?.dataset?.columnKey || '').trim();
        if (!key) return;
        const oldHandle = header.querySelector('.resize-handle');
        if (oldHandle) oldHandle.remove();
        const label = String(header.textContent || key).trim() || key;
        const handle = document.createElement('div');
        handle.className = 'resize-handle';
        handle.tabIndex = 0;
        handle.setAttribute('role', 'separator');
        handle.setAttribute('aria-orientation', 'vertical');
        handle.setAttribute('aria-label', 'Изменить ширину колонки «' + label + '»');
        handle.setAttribute('aria-valuemin', String(DIALOG_COLUMN_WIDTHS_MIN[key] || 64));
        handle.setAttribute('aria-valuemax', String(DIALOG_COLUMN_WIDTHS_MAX));
        handle.setAttribute('aria-valuenow', String(Math.round(header.getBoundingClientRect().width)));
        handle.title = 'Потяните, чтобы изменить ширину колонки';
        header.appendChild(handle);

        let drag = null;

        function updateWidth(nextWidth) {
          const applied = applyColumnWidth(key, nextWidth);
          if (applied == null) return;
          syncDialogTableWidth();
        }

        handle.addEventListener('pointerdown', (event) => {
          if (event.pointerType === 'mouse' && event.button !== 0) return;
          freezeVisibleColumnWidths();
          const current = Math.round(header.getBoundingClientRect().width);
          drag = { pointerId: event.pointerId, startX: event.clientX, startWidth: current };
          handle.setPointerCapture?.(event.pointerId);
          handle.classList.add('is-dragging');
          document.documentElement.classList.add('resizing');
          event.preventDefault();
          event.stopPropagation();
        });

        handle.addEventListener('pointermove', (event) => {
          if (!drag || event.pointerId !== drag.pointerId) return;
          updateWidth(drag.startWidth + (event.clientX - drag.startX));
        });

        function finishDrag(event) {
          if (!drag || event.pointerId !== drag.pointerId) return;
          const pointerId = drag.pointerId;
          drag = null;
          handle.classList.remove('is-dragging');
          document.documentElement.classList.remove('resizing');
          if (handle.hasPointerCapture?.(pointerId)) handle.releasePointerCapture(pointerId);
          saveColumnWidths();
        }

        handle.addEventListener('pointerup', finishDrag);
        handle.addEventListener('pointercancel', finishDrag);

        handle.addEventListener('keydown', (event) => {
          if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
          event.preventDefault();
          freezeVisibleColumnWidths();
          const delta = event.key === 'ArrowLeft' ? -8 : 8;
          updateWidth(Math.round(header.getBoundingClientRect().width) + delta);
          saveColumnWidths();
        });
      });
    }

    function initDetailsResize() {
      const detailsSidebar = options.elements?.detailsSidebar;
      const detailsResizeHandle = options.elements?.detailsResizeHandle;
      if (!detailsSidebar || !detailsResizeHandle) return;
      let startX = 0;
      let startWidth = 0;
      const reverseDirection = detailsSidebar.classList.contains('dialog-details-sidebar--end')
        || detailsResizeHandle.classList.contains('dialog-details-resize-handle--start');

      function onMouseMove(event) {
        const delta = reverseDirection
          ? startX - event.clientX
          : event.clientX - startX;
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
      applyListOnlyMode,
      loadListOnlyMode,
      toggleListOnlyMode,
      loadColumnOrder,
      persistColumnOrder,
      applyColumnOrder,
      loadColumnState,
      persistColumnState,
      applyColumnState,
      buildColumnsList,
      syncColumnsList,
      bindColumnOrderEvents,
      bindColumnStateEvents,
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

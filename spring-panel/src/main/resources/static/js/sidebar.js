// panel/static/sidebar.js
(function () {
  const sidebar = document.getElementById('app-sidebar');
  if (!sidebar) return;
  const prefApi = window.iguanaUiPreferences || null;

  const pinBtn = document.getElementById('pinSidebarBtn');
  const densityModeBtn = document.getElementById('densityModeBtn');
  const bellBtn = document.getElementById('bellBtn');
  const bellBadge = document.getElementById('notify-count');
  const root = document.body;
  const mobileToggleBtn = document.getElementById('sidebarMobileToggle');
  const mobileOverlay = document.getElementById('sidebarMobileOverlay');
  const changePasswordBtn = sidebar.querySelector('[data-sidebar-change-password]');
  const sidebarUserAvatar = sidebar.querySelector('[data-sidebar-user-avatar]');
  const sidebarUserAvatarImg = sidebar.querySelector('[data-sidebar-user-avatar-img]');
  const changePasswordModalEl = document.querySelector('[data-sidebar-password-modal]');
  const changePasswordForm = changePasswordModalEl ? changePasswordModalEl.querySelector('[data-change-password-form]') : null;
  const changePasswordError = changePasswordModalEl ? changePasswordModalEl.querySelector('[data-change-password-error]') : null;
  const changePasswordSuccess = changePasswordModalEl ? changePasswordModalEl.querySelector('[data-change-password-success]') : null;
  const changePasswordSpinner = changePasswordModalEl ? changePasswordModalEl.querySelector('[data-change-password-spinner]') : null;
  const changePasswordSubmitText = changePasswordModalEl ? changePasswordModalEl.querySelector('[data-change-password-submit-text]') : null;
  let changePasswordModalInstance = null;

  const PREF_KEY_PIN = 'sidebarPinned';
  const PREF_KEY_DENSITY_MODE = 'uiDensityMode';
  const PREF_KEY_NAV_ORDER = 'sidebarNavOrder';
  const PREF_KEY_NAV_SCROLL = 'sidebarNavScrollTop';
  const DENSITY_COMFORTABLE = 'comfortable';
  const DENSITY_COMPACT = 'compact';
  const NOTIFICATIONS_POLL_INTERVAL_MS = 5000;
  let pinned = (prefApi ? prefApi.get(PREF_KEY_PIN) : localStorage.getItem(PREF_KEY_PIN)) === '1';
  const HOVER_LEAVE_DELAY_MS = 1000;
  let hoverLeaveTimer = null;
  const MOBILE_BREAKPOINT = 991.98;

  function moveModalToBody(modalEl) {
    if (!(modalEl instanceof HTMLElement) || !document.body) return;
    if (modalEl.parentElement === document.body) return;
    document.body.appendChild(modalEl);
  }

  function moveDropdownToBody(dropdownEl) {
    if (!(dropdownEl instanceof HTMLElement) || !document.body) return;
    if (dropdownEl.parentElement === document.body) return;
    document.body.appendChild(dropdownEl);
  }

  function setSidebarAvatarLoaded(loaded) {
    if (!sidebarUserAvatar) return;
    sidebarUserAvatar.classList.toggle('is-loaded', Boolean(loaded));
  }

  function initSidebarUserAvatar() {
    if (!sidebarUserAvatarImg) {
      setSidebarAvatarLoaded(false);
      return;
    }
    const applyStateFromImage = () => {
      setSidebarAvatarLoaded(
        sidebarUserAvatarImg.complete
        && typeof sidebarUserAvatarImg.naturalWidth === 'number'
        && sidebarUserAvatarImg.naturalWidth > 0
      );
    };
    sidebarUserAvatarImg.addEventListener('load', applyStateFromImage);
    sidebarUserAvatarImg.addEventListener('error', () => setSidebarAvatarLoaded(false));
    applyStateFromImage();
  }

  moveModalToBody(changePasswordModalEl);
  initSidebarUserAvatar();

  function getPreference(name, fallback = null) {
    if (prefApi) {
      const value = prefApi.get(name);
      return value == null ? fallback : value;
    }
    try {
      const value = localStorage.getItem(name);
      return value == null ? fallback : value;
    } catch (_error) {
      return fallback;
    }
  }

  function setPreference(name, value, source = 'sidebar') {
    if (prefApi) {
      return prefApi.set(name, value, source);
    }
    try {
      localStorage.setItem(name, value);
      return value;
    } catch (_error) {
      return null;
    }
  }

  function removePreference(name, source = 'sidebar') {
    if (prefApi) {
      prefApi.remove(name, source);
      return;
    }
    try {
      localStorage.removeItem(name);
    } catch (_error) {
      // ignore storage errors
    }
  }

  function isMobileViewport() {
    return window.innerWidth <= MOBILE_BREAKPOINT;
  }

  function setMobileOpen(open) {
    const shouldOpen = Boolean(open);
    root.classList.toggle('sidebar-mobile-open', shouldOpen);
    if (mobileOverlay) {
      mobileOverlay.hidden = !shouldOpen;
    }
    if (mobileToggleBtn) {
      const icon = mobileToggleBtn.querySelector('.icon');
      if (icon) icon.textContent = shouldOpen ? '×' : '☰';
      mobileToggleBtn.setAttribute('aria-label', shouldOpen ? 'Закрыть меню' : 'Открыть меню');
    }
  }

  // оборачиваем страницу, чтобы не было пустоты справа
  function ensureLayoutWrap() {
    const root = document.body;
    if (!root.classList.contains('with-sidebar')) root.classList.add('with-sidebar');

    const main = document.querySelector('main') || document.getElementById('main');
    if (!main) return;

    let wrap = document.querySelector('.sidebar-layout');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'sidebar-layout';
      const parent = main.parentElement || root;
      parent.insertBefore(wrap, main);
    }

    if (sidebar.parentElement !== wrap) {
      wrap.appendChild(sidebar);
    }

    if (main.parentElement !== wrap) {
      wrap.appendChild(main);
    }

    main.classList.add('main-content');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', ensureLayoutWrap, { once: true });
  } else {
    ensureLayoutWrap();
  }

  function setBodyCollapsedState(collapsed) {
    if (!root) return;
    if (collapsed) {
      root.classList.add('sidebar-collapsed');
      root.classList.remove('sidebar-hovering');
    } else {
      root.classList.remove('sidebar-collapsed');
      root.classList.remove('sidebar-hovering');
    }
  }

  function applyState() {
    if (hoverLeaveTimer) {
      clearTimeout(hoverLeaveTimer);
      hoverLeaveTimer = null;
    }
    if (isMobileViewport()) {
      sidebar.classList.remove('pinned', 'hovering', 'collapsed');
      setBodyCollapsedState(false);
      return;
    }
    if (pinned) {
      sidebar.classList.add('pinned');
      sidebar.classList.remove('collapsed', 'hovering');
      setBodyCollapsedState(false);
    } else {
      sidebar.classList.remove('pinned');
      sidebar.classList.add('collapsed');
      sidebar.classList.remove('hovering');
      setBodyCollapsedState(true);
    }
  }
  applyState();

  function setDensityMode(mode, options = {}) {
    const nextMode = mode === DENSITY_COMPACT ? DENSITY_COMPACT : DENSITY_COMFORTABLE;
    root.classList.toggle('density-compact', nextMode === DENSITY_COMPACT);
    root.classList.toggle('density-comfortable', nextMode !== DENSITY_COMPACT);
    if (densityModeBtn) {
      const isCompact = nextMode === DENSITY_COMPACT;
      densityModeBtn.setAttribute('aria-pressed', isCompact ? 'true' : 'false');
      densityModeBtn.textContent = isCompact ? '▤' : '◫';
      densityModeBtn.title = `Плотность интерфейса: ${isCompact ? 'compact' : 'comfortable'}`;
    }
    if (options.persist !== false) {
      try {
        setPreference(PREF_KEY_DENSITY_MODE, nextMode, 'sidebar-density');
      } catch (_error) {
        // ignore storage write errors
      }
    }
  }

  function loadDensityMode() {
    let stored = DENSITY_COMFORTABLE;
    try {
      const raw = String(getPreference(PREF_KEY_DENSITY_MODE, '') || '').trim().toLowerCase();
      if (raw === DENSITY_COMPACT || raw === DENSITY_COMFORTABLE) {
        stored = raw;
      }
    } catch (_error) {
      stored = DENSITY_COMFORTABLE;
    }
    setDensityMode(stored, { persist: false });
  }

  function toggleDensityMode() {
    const compactEnabled = root.classList.contains('density-compact');
    setDensityMode(compactEnabled ? DENSITY_COMFORTABLE : DENSITY_COMPACT);
  }

  function syncSidebarForViewport() {
    if (!isMobileViewport()) {
      setMobileOpen(false);
    }
    applyState();
  }

  if (mobileToggleBtn) {
    mobileToggleBtn.addEventListener('click', () => {
      setMobileOpen(!root.classList.contains('sidebar-mobile-open'));
    });
  }

  if (mobileOverlay) {
    mobileOverlay.addEventListener('click', () => setMobileOpen(false));
  }

  window.addEventListener('resize', syncSidebarForViewport);
  syncSidebarForViewport();

  const nav = sidebar.querySelector('.sidebar-nav');
  const DEFAULT_ORDER = [
    'dialogs',
    'ai-ops',
    'tasks',
    'clients',
    'unblock-requests',
    'passports',
    'public',
    'knowledge',
    'dashboard',
    'analytics',
    'channels',
    'settings',
    'users',
  ];
  const NAV_TITLE_DEFAULT = '';
  const NAV_TITLE_EDITING = 'Перетащите пункты, чтобы изменить порядок';
  const NAV_ORDER_HINT = 'Нажмите на иконку «⇅», чтобы изменить расположение страниц';
  const resetOrderBtn = document.getElementById('resetSidebarOrderBtn');
  const editOrderBtn = document.getElementById('editSidebarOrderBtn');
  let isEditingOrder = false;
  let orderHintShown = false;

  function getNavLinks() {
    if (!nav) return [];
    return Array.from(nav.querySelectorAll('.nav-link[data-page-key]'));
  }

  function sanitizeOrder(order) {
    if (!nav) return [];
    const availableKeys = new Set(getNavLinks().map((link) => link.dataset.pageKey));
    const seen = new Set();
    const result = [];
    (Array.isArray(order) ? order : []).forEach((key) => {
      if (!key || seen.has(key) || !availableKeys.has(key)) return;
      seen.add(key);
      result.push(key);
    });
    DEFAULT_ORDER.forEach((key) => {
      if (availableKeys.has(key) && !seen.has(key)) {
        seen.add(key);
        result.push(key);
      }
    });
    getNavLinks().forEach((link) => {
      const key = link.dataset.pageKey;
      if (key && !seen.has(key)) {
        seen.add(key);
        result.push(key);
      }
    });
    return result;
  }

  function applyOrder(order) {
    if (!nav) return;
    const previousScrollTop = nav.scrollTop;
    const byKey = new Map();
    getNavLinks().forEach((link) => {
      byKey.set(link.dataset.pageKey, link);
    });
    const fragment = document.createDocumentFragment();
    sanitizeOrder(order).forEach((key) => {
      const el = byKey.get(key);
      if (el) {
        fragment.appendChild(el);
        byKey.delete(key);
      }
    });
    byKey.forEach((el) => fragment.appendChild(el));
    nav.appendChild(fragment);
    nav.scrollTop = previousScrollTop;
  }

  function normalizedDefaultOrder() {
    return sanitizeOrder(DEFAULT_ORDER);
  }

  function persistCurrentOrder() {
    if (!nav) return;
    const current = sanitizeOrder(getNavLinks().map((link) => link.dataset.pageKey));
    const def = normalizedDefaultOrder();
    if (JSON.stringify(current) === JSON.stringify(def)) {
      removePreference(PREF_KEY_NAV_ORDER, 'sidebar-order');
    } else {
      setPreference(PREF_KEY_NAV_ORDER, current, 'sidebar-order');
    }
  }

  function loadSavedOrder() {
    try {
      const parsed = prefApi
        ? getPreference(PREF_KEY_NAV_ORDER, null)
        : JSON.parse(String(getPreference(PREF_KEY_NAV_ORDER, 'null')));
      if (!Array.isArray(parsed)) return null;
      return sanitizeOrder(parsed);
    } catch (error) {
      return null;
    }
  }

  function restoreNavScrollPosition() {
    if (!nav) return;
    const rawValue = getPreference(PREF_KEY_NAV_SCROLL, '0');
    const scrollTop = Number.parseInt(String(rawValue ?? '0'), 10);
    if (Number.isFinite(scrollTop) && scrollTop > 0) {
      nav.scrollTop = scrollTop;
    }
  }

  function persistNavScrollPosition() {
    if (!nav) return;
    setPreference(PREF_KEY_NAV_SCROLL, String(nav.scrollTop || 0), 'sidebar-nav-scroll');
  }

  function getDragAfterElement(container, y) {
    const items = Array.from(container.querySelectorAll('.nav-link[data-page-key]:not(.dragging)'));
    let closest = { offset: Number.NEGATIVE_INFINITY, element: null };
    for (const child of items) {
      const box = child.getBoundingClientRect();
      const offset = y - box.top - box.height / 2;
      if (offset < 0 && offset > closest.offset) {
        closest = { offset, element: child };
      }
    }
    return closest.element;
  }

  function applyDraggableState(enabled) {
    if (!nav) return;
    getNavLinks().forEach((link) => {
      if (enabled) {
        link.setAttribute('draggable', 'true');
        link.classList.add('draggable');
      } else {
        link.removeAttribute('draggable');
        link.classList.remove('draggable', 'dragging');
      }
    });
    nav.classList.toggle('editing-order', Boolean(enabled));
  }

  let dragEventsAttached = false;

  function setupDragAndDrop() {
    if (!nav || dragEventsAttached) return;
    dragEventsAttached = true;

    nav.addEventListener('dragstart', (event) => {
      if (!isEditingOrder) {
        event.preventDefault();
        return;
      }
      const link = event.target.closest('.nav-link[data-page-key]');
      if (!link) return;
      if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', link.dataset.pageKey || '');
      }
      link.classList.add('dragging');
    });

    nav.addEventListener('dragend', () => {
      if (!isEditingOrder) return;
      const dragging = nav.querySelector('.nav-link.dragging');
      if (dragging) dragging.classList.remove('dragging');
      persistCurrentOrder();
    });

    nav.addEventListener('dragover', (event) => {
      if (!isEditingOrder) return;
      const dragging = nav.querySelector('.nav-link.dragging');
      if (!dragging) return;
      event.preventDefault();
      const after = getDragAfterElement(nav, event.clientY);
      if (!after) {
        nav.appendChild(dragging);
      } else if (after !== dragging) {
        nav.insertBefore(dragging, after);
      }
    });

    nav.addEventListener('drop', (event) => {
      if (!isEditingOrder) return;
      const dragging = nav.querySelector('.nav-link.dragging');
      if (!dragging) return;
      event.preventDefault();
      const after = getDragAfterElement(nav, event.clientY);
      if (!after) {
        nav.appendChild(dragging);
      } else if (after !== dragging) {
        nav.insertBefore(dragging, after);
      }
      persistCurrentOrder();
    });
  }

  function updateEditOrderButton() {
    if (editOrderBtn) {
      const icon = editOrderBtn.querySelector('.icon');
      if (icon) {
        icon.textContent = isEditingOrder ? '✔️' : '⇅';
      }
      editOrderBtn.classList.toggle('active', isEditingOrder);
      editOrderBtn.setAttribute('aria-pressed', isEditingOrder ? 'true' : 'false');
      const label = isEditingOrder ? 'Завершить редактирование порядка' : 'Редактировать порядок';
      editOrderBtn.setAttribute('aria-label', label);
      editOrderBtn.setAttribute('title', label);
    }
    if (nav) {
      if (isEditingOrder && NAV_TITLE_EDITING) {
        nav.setAttribute('title', NAV_TITLE_EDITING);
      } else {
        nav.removeAttribute('title');
      }
    }
  }

  function setEditingOrder(enabled) {
    const nextState = Boolean(enabled);
    if (isEditingOrder === nextState) return;
    isEditingOrder = nextState;
    applyDraggableState(isEditingOrder);
    updateEditOrderButton();
    if (isEditingOrder && !orderHintShown) {
      orderHintShown = true;
      if (typeof showNotificationToast === 'function') {
        showNotificationToast(NAV_ORDER_HINT);
      }
    }
    if (!isEditingOrder) {
      const dragging = nav ? nav.querySelector('.nav-link.dragging') : null;
      if (dragging) dragging.classList.remove('dragging');
      persistCurrentOrder();
    }
  }

  if (nav) {
    const savedOrder = loadSavedOrder();
    if (savedOrder && savedOrder.length) {
      applyOrder(savedOrder);
    } else {
      applyOrder(DEFAULT_ORDER);
    }
    restoreNavScrollPosition();
    setupDragAndDrop();
    applyDraggableState(false);
    updateEditOrderButton();
    nav.addEventListener('scroll', persistNavScrollPosition, { passive: true });
    if (resetOrderBtn) {
      resetOrderBtn.addEventListener('click', () => {
        applyOrder(DEFAULT_ORDER);
        removePreference(PREF_KEY_NAV_ORDER, 'sidebar-order-reset');
        persistNavScrollPosition();
        setEditingOrder(false);
      });
    }

    nav.addEventListener('click', (event) => {
      const link = event.target.closest('.nav-link');
      if (!link) return;
      persistNavScrollPosition();
      if (isMobileViewport()) {
        setMobileOpen(false);
      }
    });
  }

  if (editOrderBtn) {
    editOrderBtn.addEventListener('click', () => {
      setEditingOrder(!isEditingOrder);
    });
  }

  function togglePasswordMessage(element, message) {
    if (!element) return;
    if (message) {
      element.textContent = message;
      element.classList.remove('d-none');
    } else {
      element.textContent = '';
      element.classList.add('d-none');
    }
  }

  function resetChangePasswordState() {
    if (changePasswordForm) {
      changePasswordForm.reset();
    }
    togglePasswordMessage(changePasswordError, '');
    togglePasswordMessage(changePasswordSuccess, '');
    if (changePasswordSpinner) changePasswordSpinner.classList.add('d-none');
    if (changePasswordSubmitText) changePasswordSubmitText.textContent = 'Сменить пароль';
  }

  function ensureChangePasswordModal() {
    if (!changePasswordModalEl || !window.bootstrap) return null;
    if (!changePasswordModalInstance) {
      changePasswordModalInstance = window.bootstrap.Modal.getOrCreateInstance(changePasswordModalEl, {
        backdrop: 'static',
      });
    }
    return changePasswordModalInstance;
  }

  if (changePasswordModalEl) {
    changePasswordModalEl.addEventListener('hidden.bs.modal', () => {
      resetChangePasswordState();
    });
  }

  if (changePasswordBtn) {
    changePasswordBtn.addEventListener('click', (event) => {
      event.preventDefault();
      const modal = ensureChangePasswordModal();
      if (modal) {
        resetChangePasswordState();
        modal.show();
      }
    });
  }

  if (changePasswordForm) {
    changePasswordForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      togglePasswordMessage(changePasswordError, '');
      togglePasswordMessage(changePasswordSuccess, '');

      const formData = new FormData(changePasswordForm);
      const currentPassword = (formData.get('current_password') || '').toString().trim();
      const newPassword = (formData.get('new_password') || '').toString().trim();
      const confirmPassword = (formData.get('confirm_password') || '').toString().trim();

      if (!currentPassword) {
        togglePasswordMessage(changePasswordError, 'Укажите текущий пароль.');
        return;
      }
      if (!newPassword) {
        togglePasswordMessage(changePasswordError, 'Новый пароль не может быть пустым.');
        return;
      }
      if (newPassword !== confirmPassword) {
        togglePasswordMessage(changePasswordError, 'Новый пароль и подтверждение не совпадают.');
        return;
      }

      if (changePasswordSpinner) changePasswordSpinner.classList.remove('d-none');
      if (changePasswordSubmitText) changePasswordSubmitText.textContent = 'Сохранение...';

      try {
        const response = await fetch('/profile/password', {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            current_password: currentPassword,
            new_password: newPassword,
            confirm_password: confirmPassword,
          }),
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok || !payload.success) {
          const message = payload && payload.error ? payload.error : 'Не удалось изменить пароль.';
          togglePasswordMessage(changePasswordError, message);
          return;
        }
        togglePasswordMessage(changePasswordSuccess, payload.message || 'Пароль успешно обновлён.');
        if (changePasswordSpinner) changePasswordSpinner.classList.add('d-none');
        if (changePasswordSubmitText) changePasswordSubmitText.textContent = 'Готово';
        const modal = ensureChangePasswordModal();
        if (modal) {
          setTimeout(() => {
            modal.hide();
          }, 1400);
        }
      } catch (error) {
        togglePasswordMessage(changePasswordError, 'Ошибка соединения. Попробуйте позже.');
      } finally {
        if (changePasswordSpinner) changePasswordSpinner.classList.add('d-none');
        if (changePasswordSubmitText && changePasswordSubmitText.textContent !== 'Готово') {
          changePasswordSubmitText.textContent = 'Сменить пароль';
        }
      }
    });
  }

  // hover раскрытие, если не pinned
  sidebar.addEventListener('mouseenter', () => {
    if (hoverLeaveTimer) {
      clearTimeout(hoverLeaveTimer);
      hoverLeaveTimer = null;
    }
    if (!pinned) {
      sidebar.classList.add('hovering');
      if (root) root.classList.add('sidebar-hovering');
    }
  });
  sidebar.addEventListener('mouseleave', () => {
    if (!pinned) {
      if (hoverLeaveTimer) clearTimeout(hoverLeaveTimer);
      hoverLeaveTimer = setTimeout(() => {
        sidebar.classList.remove('hovering');
        if (root) root.classList.remove('sidebar-hovering');
        hoverLeaveTimer = null;
      }, HOVER_LEAVE_DELAY_MS);
    }
  });

  // клик по "📌"
  if (pinBtn) {
    pinBtn.addEventListener('click', () => {
      pinned = !pinned;
      setPreference(PREF_KEY_PIN, pinned ? '1' : '0', 'sidebar-pin');
      applyState();
    });
  }

  if (densityModeBtn) {
    densityModeBtn.addEventListener('click', () => {
      toggleDensityMode();
    });
  }
  loadDensityMode();

  document.addEventListener('ui-preference:change', (event) => {
    const detail = event && event.detail ? event.detail : {};
    if (detail.name === PREF_KEY_DENSITY_MODE) {
      setDensityMode(detail.value === DENSITY_COMPACT ? DENSITY_COMPACT : DENSITY_COMFORTABLE, { persist: false });
      return;
    }
    if (detail.name === PREF_KEY_PIN) {
      pinned = detail.value === '1';
      applyState();
      return;
    }
    if (detail.name === PREF_KEY_NAV_ORDER && nav) {
      applyOrder(Array.isArray(detail.value) ? detail.value : DEFAULT_ORDER);
    }
  });

  const bellWrapper = document.getElementById('notify-bell-wrapper');
  const bellDropdown = document.getElementById('notify-dropdown');
  let notificationsOpen = false;
  let notificationPositionFrame = 0;
  let toastEl = null;
  let toastTimer = null;
  let lastUnreadCount = 0;
  let hasInitialUnread = false;
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeaderName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
  const NOTIFICATION_EMPTY_LABEL = '\u041d\u043e\u0432\u044b\u0445 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u0439 \u043d\u0435\u0442';
  const NOTIFICATION_LOADING_LABEL = '\u0417\u0430\u0433\u0440\u0443\u0437\u043a\u0430...';
  const NOTIFICATION_LOAD_ERROR_LABEL = '\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044c \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f';
  const NOTIFICATION_UNREAD_LABEL = '\u041d\u0435\u043f\u0440\u043e\u0447\u0438\u0442\u0430\u043d\u043d\u044b\u0435';
  const NOTIFICATION_READ_LABEL = '\u041f\u0440\u043e\u0447\u0438\u0442\u0430\u043d\u043d\u044b\u0435';
  const NOTIFICATION_ITEM_FALLBACK = '\u0423\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u0435';
  const NOTIFICATION_TOAST_ONE = '\u041d\u043e\u0432\u043e\u0435 \u043e\u043f\u043e\u0432\u0435\u0449\u0435\u043d\u0438\u0435';
  const NOTIFICATION_TOAST_MANY_PREFIX = '\u041d\u043e\u0432\u044b\u0445 \u043e\u043f\u043e\u0432\u0435\u0449\u0435\u043d\u0438\u0439: ';

  moveDropdownToBody(bellDropdown);

  function positionNotificationsDropdown() {
    if (!bellBtn || !bellDropdown || bellDropdown.hidden) return;
    const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 0;
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
    const margin = 16;
    const preferredWidth = Math.max(180, Math.min(360, viewportWidth - margin * 2));
    const triggerRect = bellBtn.getBoundingClientRect();
    const left = Math.max(
      margin,
      Math.min(triggerRect.right - preferredWidth, viewportWidth - preferredWidth - margin)
    );
    const top = Math.min(triggerRect.bottom + 10, Math.max(margin, viewportHeight - 220));
    const maxHeight = Math.max(180, viewportHeight - top - margin);
    bellDropdown.style.left = `${Math.round(left)}px`;
    bellDropdown.style.top = `${Math.round(top)}px`;
    bellDropdown.style.width = `${Math.round(preferredWidth)}px`;
    bellDropdown.style.maxHeight = `${Math.round(maxHeight)}px`;
    bellDropdown.style.right = 'auto';
  }

  function requestNotificationsDropdownPosition() {
    if (notificationPositionFrame) {
      window.cancelAnimationFrame(notificationPositionFrame);
    }
    notificationPositionFrame = window.requestAnimationFrame(() => {
      notificationPositionFrame = 0;
      positionNotificationsDropdown();
    });
  }

  const HTML_ESCAPE_RE = /[&<>"']/g;
  const HTML_ESCAPE_MAP = {
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  };

  function escapeHtml(value) {
    if (typeof value !== 'string') return '';
    return value.replace(HTML_ESCAPE_RE, (char) => HTML_ESCAPE_MAP[char] || char);
  }

  function normalizeNotificationItem(item) {
    if (!item || typeof item !== 'object') return null;
    const numericId = Number(item.id);
    const id = Number.isFinite(numericId) ? numericId : item.id;
    return {
      id,
      text: typeof item.text === 'string' ? item.text : '',
      url: typeof item.url === 'string' ? item.url : '',
      created_at: item.created_at || item.createdAt || '',
      is_read: item.is_read === true || item.is_read === 1 || item.read === true || item.read === 1,
    };
  }

  function normalizeNotificationPayload(data) {
    if (Array.isArray(data)) {
      const normalizedItems = data.map(normalizeNotificationItem).filter(Boolean);
      return {
        unread: normalizedItems.filter((item) => !item.is_read),
        read: normalizedItems.filter((item) => item.is_read),
      };
    }

    const container = data && typeof data === 'object'
      ? (typeof data.items === 'object' && data.items !== null ? data.items : data)
      : {};

    const unread = Array.isArray(container.unread)
      ? container.unread.map(normalizeNotificationItem).filter(Boolean)
      : [];
    const read = Array.isArray(container.read)
      ? container.read.map(normalizeNotificationItem).filter(Boolean)
      : [];

    return { unread, read };
  }

  function formatNotificationTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleString('ru-RU');
  }

  function navigateNotification(url) {
    if (!url) return;
    const trimmed = url.trim();
    if (!trimmed) return;
    try {
      const legacyMatch = trimmed.match(/(?:^|\/)dialogs\?[^#]*ticketId=([^&#]+)/i);
      if (legacyMatch?.[1]) {
        const ticketId = decodeURIComponent(legacyMatch[1]);
        const dialogsTarget = `/dialogs/${encodeURIComponent(ticketId)}`;
        const onDialogsPage = window.location.pathname === '/' || window.location.pathname === '/dialogs' || window.location.pathname.startsWith('/dialogs/');
        if (onDialogsPage && typeof window.openDialogDetailsByTicketId === 'function') {
          if (window.history?.pushState) {
            window.history.pushState({ ticketId }, '', dialogsTarget);
          }
          window.openDialogDetailsByTicketId(ticketId);
          return;
        }
        window.location.href = dialogsTarget;
        return;
      }
      const absolute = new URL(trimmed, window.location.origin);
      const hash = absolute.hash || '';
      const sameOrigin = absolute.origin === window.location.origin;
      if (hash.startsWith('#open=ticket:')) {
        const ticketId = hash.slice('#open=ticket:'.length);
        const dialogsTarget = `/dialogs/${encodeURIComponent(ticketId)}`;
        const onDialogsPage = window.location.pathname === '/' || window.location.pathname === '/dialogs' || window.location.pathname.startsWith('/dialogs/');
        if (onDialogsPage && typeof window.openDialogDetailsByTicketId === 'function') {
          if (window.history?.pushState) {
            window.history.pushState({ ticketId }, '', dialogsTarget);
          }
          window.openDialogDetailsByTicketId(ticketId);
        } else {
          window.location.href = dialogsTarget;
        }
        return;
      }
      if (sameOrigin) {
        window.location.href = `${absolute.pathname}${absolute.search}${hash}`;
      } else {
        window.open(absolute.href, '_blank', 'noopener');
      }
    } catch (error) {
      window.location.href = trimmed;
    }
  }

  function showNotificationToast(message) {
    if (!message) return;
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'notify-toast';
      toastEl.setAttribute('role', 'status');
      toastEl.setAttribute('aria-live', 'polite');
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = message;
    toastEl.classList.add('show');
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      if (toastEl) {
        toastEl.classList.remove('show');
      }
    }, 3000);
  }

  function setBellCount(value) {
    if (!bellBadge) return;
    if (value > 0) {
      bellBadge.hidden = false;
      bellBadge.textContent = String(value);
    } else {
      bellBadge.hidden = true;
    }
  }

  function renderNotifications(unreadItems, readItems) {
    if (!bellDropdown) return;
    const unread = Array.isArray(unreadItems) ? unreadItems.filter(Boolean) : [];
    const read = Array.isArray(readItems) ? readItems.filter(Boolean) : [];

    if (!unread.length && !read.length) {
      bellDropdown.innerHTML = '<div class="notif-item text-muted">Новых уведомлений нет</div>';
      return;
    }
    const renderSection = (items, title, unreadFlag) => {
      if (!items.length) return '';
      const sectionTitle = `<div class="notif-section-title">${escapeHtml(title)}</div>`;
      const markup = items.map((item) => {
        const text = escapeHtml(item.text || 'Уведомление');
        const url = (item.url || '').trim();
        const dateStr = formatNotificationTime(item.created_at);
        const linkStart = url ? `<a class="stretched-link" data-notification-link href="${escapeHtml(url)}" rel="noopener">` : '';
        const linkEnd = url ? '</a>' : '';
        const classes = ['notif-item', 'position-relative', unreadFlag ? 'notif-item-unread' : 'notif-item-read'];
        return `
          <div class="${classes.join(' ')}" data-id="${item.id ?? ''}">
            ${linkStart}<div class="notif-text">${text}</div>${linkEnd}
            ${dateStr ? `<div class="notif-time">${escapeHtml(dateStr)}</div>` : ''}
          </div>
        `;
      }).join('');
      return sectionTitle + markup;
    };

    const parts = [];
    parts.push(renderSection(unread, 'Непрочитанные', true));
    parts.push(renderSection(read, 'Прочитанные', false));
    bellDropdown.innerHTML = parts.filter(Boolean).join('');
  }

  async function loadNotifications() {
    if (!bellDropdown) return;
    bellDropdown.hidden = false;
    bellDropdown.innerHTML = '<div class="notif-item text-muted">Загрузка...</div>';
    notificationsOpen = true;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'true');
    try {
      const response = await fetch('/api/notifications', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) throw new Error('Failed to load notifications');
      const data = await response.json();
      const payload = normalizeNotificationPayload(data);
      renderNotifications(payload.unread, payload.read);
      hasInitialUnread = true;
      setBellCount(payload.unread.length);
      lastUnreadCount = payload.unread.length;

    } catch (error) {
      bellDropdown.innerHTML = '<div class="notif-item text-danger">Не удалось загрузить уведомления</div>';
    }
  }

  function renderNotificationsSafe(unreadItems, readItems) {
    if (!bellDropdown) return;
    const unread = Array.isArray(unreadItems) ? unreadItems.filter(Boolean) : [];
    const read = Array.isArray(readItems) ? readItems.filter(Boolean) : [];

    if (!unread.length && !read.length) {
      bellDropdown.innerHTML = `<div class="notif-item text-muted">${NOTIFICATION_EMPTY_LABEL}</div>`;
      return;
    }

    const renderSection = (items, title, unreadFlag) => {
      if (!items.length) return '';
      const sectionTitle = `<div class="notif-section-title">${escapeHtml(title)}</div>`;
      const markup = items.map((item) => {
        const text = escapeHtml(item.text || NOTIFICATION_ITEM_FALLBACK);
        const url = (item.url || '').trim();
        const dateStr = formatNotificationTime(item.created_at);
        const linkStart = url ? `<a class="stretched-link" data-notification-link href="${escapeHtml(url)}" rel="noopener">` : '';
        const linkEnd = url ? '</a>' : '';
        const classes = ['notif-item', 'position-relative', unreadFlag ? 'notif-item-unread' : 'notif-item-read'];
        return `
          <div class="${classes.join(' ')}" data-id="${item.id ?? ''}">
            ${linkStart}<div class="notif-text">${text}</div>${linkEnd}
            ${dateStr ? `<div class="notif-time">${escapeHtml(dateStr)}</div>` : ''}
          </div>
        `;
      }).join('');
      return sectionTitle + markup;
    };

    const parts = [];
    parts.push(renderSection(unread, NOTIFICATION_UNREAD_LABEL, true));
    parts.push(renderSection(read, NOTIFICATION_READ_LABEL, false));
    bellDropdown.innerHTML = parts.filter(Boolean).join('');
  }

  async function loadNotificationsSafe() {
    if (!bellDropdown) return;
    bellDropdown.hidden = false;
    bellDropdown.innerHTML = `<div class="notif-item text-muted">${NOTIFICATION_LOADING_LABEL}</div>`;
    notificationsOpen = true;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'true');
    requestNotificationsDropdownPosition();
    try {
      const response = await fetch('/api/notifications', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) throw new Error('Failed to load notifications');
      const data = await response.json();
      const payload = normalizeNotificationPayload(data);
      let unread = payload.unread;
      let read = payload.read;
      if (unread.length) {
        try {
          const markedCount = await markAllNotificationsAsRead();
          if (markedCount >= 0) {
            read = unread.map((item) => ({ ...item, is_read: true })).concat(read);
            unread = [];
          }
        } catch (_error) {
          // keep notifications visible even if bulk read update failed
        }
      }
      renderNotificationsSafe(unread, read);
      hasInitialUnread = true;
      setBellCount(unread.length);
      lastUnreadCount = unread.length;
      requestNotificationsDropdownPosition();
    } catch (_error) {
      bellDropdown.innerHTML = `<div class="notif-item text-danger">${NOTIFICATION_LOAD_ERROR_LABEL}</div>`;
      requestNotificationsDropdownPosition();
    }
  }

  async function markAllNotificationsAsRead() {
    const headers = {};
    if (csrfToken) {
      headers[csrfHeaderName] = csrfToken;
    }
    const response = await fetch('/api/notifications/read-all', {
      method: 'POST',
      credentials: 'same-origin',
      headers,
    });
    if (!response.ok) {
      throw new Error('Failed to mark all notifications as read');
    }
    const payload = await response.json().catch(() => ({}));
    return Number(payload.updated ?? 0);
  }

  async function markNotificationAsRead(notificationId) {
    if (!notificationId) return false;
    const headers = {};
    if (csrfToken) {
      headers[csrfHeaderName] = csrfToken;
    }
    const response = await fetch(`/api/notifications/${notificationId}/read`, {
      method: 'POST',
      credentials: 'same-origin',
      headers,
      keepalive: true,
    });
    return response.ok;
  }

  function applyNotificationReadState(itemEl) {
    if (!(itemEl instanceof HTMLElement)) return;
    itemEl.classList.remove('notif-item-unread');
    itemEl.classList.add('notif-item-read');
  }

  if (bellDropdown) {
    bellDropdown.addEventListener('click', async (event) => {
      const link = event.target.closest('a[data-notification-link]');
      if (!link) return;
      if (event.defaultPrevented) return;
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.button === 1) {
        return;
      }
      event.preventDefault();
      const itemEl = link.closest('.notif-item');
      const notificationId = itemEl?.dataset?.id;
      if (notificationId) {
        try {
          const marked = await markNotificationAsRead(notificationId);
          if (marked) {
            applyNotificationReadState(itemEl);
          }
        } catch (_error) {
          // ignore read marker errors
        }
      }
      const href = link.getAttribute('href') || '';
      closeNotifications();
      navigateNotification(href);
    });
  }

  function closeNotifications() {
    if (!bellDropdown) return;
    bellDropdown.hidden = true;
    notificationsOpen = false;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
    if (notificationPositionFrame) {
      window.cancelAnimationFrame(notificationPositionFrame);
      notificationPositionFrame = 0;
    }
  }

  async function updateNotificationCount() {
    try {
      const response = await fetch('/api/notifications/unread_count', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) return;
      const data = await response.json();
      const newCount = Number(data.unread ?? data.count ?? 0);
      const previousCount = lastUnreadCount;
      setBellCount(newCount);
      if (hasInitialUnread && newCount > lastUnreadCount) {
        const diff = newCount - lastUnreadCount;
        const message = diff === 1 ? 'Новое оповещение' : `Новых оповещений: ${diff}`;
        showNotificationToast(message);
      }
      lastUnreadCount = newCount;
      hasInitialUnread = true;
      if (notificationsOpen && newCount !== previousCount) {
        await loadNotificationsSafe();
      }
    } catch (error) {
      /* ignore */
    }
  }

  async function updateNotificationCountSafe() {
    try {
      const response = await fetch('/api/notifications/unread_count', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' },
      });
      if (!response.ok) return;
      const data = await response.json();
      const newCount = Number(data.unread ?? data.count ?? 0);
      const previousCount = lastUnreadCount;
      setBellCount(newCount);
      if (hasInitialUnread && newCount > lastUnreadCount) {
        const diff = newCount - lastUnreadCount;
        const message = diff === 1 ? NOTIFICATION_TOAST_ONE : `${NOTIFICATION_TOAST_MANY_PREFIX}${diff}`;
        showNotificationToast(message);
      }
      lastUnreadCount = newCount;
      hasInitialUnread = true;
      if (notificationsOpen && newCount !== previousCount) {
        await loadNotificationsSafe();
      }
    } catch (_error) {
      /* ignore */
    }
  }

  function requestNotificationRefresh() {
    return updateNotificationCountSafe();
  }

  if (bellBtn) {
    bellBtn.addEventListener('click', async (event) => {
      event.preventDefault();
      if (notificationsOpen) {
        closeNotifications();
      } else {
        await loadNotificationsSafe();
      }
    });
  }

  document.addEventListener('click', (event) => {
    if (!notificationsOpen) return;
    if (bellWrapper && bellWrapper.contains(event.target)) return;
    if (bellDropdown && bellDropdown.contains(event.target)) return;
    closeNotifications();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && root.classList.contains('sidebar-mobile-open')) {
      setMobileOpen(false);
      return;
    }
    if (event.key === 'Escape' && notificationsOpen) {
      closeNotifications();
    }
  });

  updateNotificationCountSafe();
  setInterval(updateNotificationCountSafe, NOTIFICATIONS_POLL_INTERVAL_MS);
  window.refreshSidebarNotifications = requestNotificationRefresh;
  window.addEventListener('iguana:notifications-refresh', requestNotificationRefresh);
  window.addEventListener('focus', requestNotificationRefresh);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
      requestNotificationRefresh();
    }
  });
  window.addEventListener('resize', requestNotificationsDropdownPosition);
  window.addEventListener('scroll', requestNotificationsDropdownPosition, true);

  const unblockBadge = document.querySelector('[data-unblock-count]');

  function setUnblockCount(value) {
    if (!unblockBadge) return;
    const count = Number(value || 0);
    if (count > 0) {
      unblockBadge.hidden = false;
      unblockBadge.textContent = String(count);
    } else {
      unblockBadge.hidden = true;
      unblockBadge.textContent = '0';
    }
  }

  async function updateUnblockRequestCount() {
    if (!unblockBadge) return;
    try {
      const response = await fetch('/api/unblock-requests/summary');
      if (!response.ok) return;
      const payload = await response.json();
      setUnblockCount(payload.count || 0);
    } catch (error) {
      /* ignore */
    }
  }

  updateUnblockRequestCount();
  setInterval(updateUnblockRequestCount, 30000);

  const botMiniIndicatorsEl = sidebar.querySelector('[data-sidebar-bot-mini-indicators]');
  const MAX_SIDEBAR_BOT_DOTS = 8;

  function normalizeSidebarChannelName(channel) {
    if (!channel || typeof channel !== 'object') return 'Бот';
    const name = channel.channel_name || channel.channelName || channel.name;
    if (typeof name === 'string' && name.trim()) {
      return name.trim();
    }
    const channelId = channel.id ?? channel.channel_id ?? '';
    return channelId ? `Бот #${channelId}` : 'Бот';
  }

  function createSidebarBotDot(statusCode, title) {
    const dot = document.createElement('span');
    dot.className = 'sidebar-bot-mini-dot';
    if (statusCode === 'running') {
      dot.classList.add('sidebar-bot-mini-dot--running');
    } else if (statusCode === 'stopped') {
      dot.classList.add('sidebar-bot-mini-dot--stopped');
    } else if (statusCode === 'inactive') {
      dot.classList.add('sidebar-bot-mini-dot--inactive');
    } else {
      dot.classList.add('sidebar-bot-mini-dot--error');
    }
    dot.title = title || '';
    return dot;
  }

  async function resolveSidebarBotRuntimeStatus(channel) {
    const channelId = Number(channel?.id ?? channel?.channel_id);
    const name = normalizeSidebarChannelName(channel);
    if (!Number.isFinite(channelId)) {
      return { code: 'error', title: `${name}: не найден id` };
    }
    if (!(channel?.is_active === true || channel?.is_active === 1)) {
      return { code: 'inactive', title: `${name}: канал выключен` };
    }
    try {
      const response = await fetch(`/api/bots/${channelId}/status`);
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      const status = String(payload?.status || '').trim().toLowerCase();
      if (status === 'running') {
        return { code: 'running', title: `${name}: процесс запущен` };
      }
      if (status === 'stopped') {
        return { code: 'stopped', title: `${name}: процесс остановлен` };
      }
      return { code: 'error', title: `${name}: ${payload?.status || 'неизвестный статус'}` };
    } catch (_error) {
      return { code: 'error', title: `${name}: ошибка проверки` };
    }
  }

  function renderSidebarBotMiniIndicators(items, overflowCount) {
    if (!botMiniIndicatorsEl) return;
    botMiniIndicatorsEl.innerHTML = '';
    if (!Array.isArray(items) || !items.length) {
      botMiniIndicatorsEl.hidden = true;
      return;
    }
    items.forEach((item) => {
      botMiniIndicatorsEl.appendChild(createSidebarBotDot(item.code, item.title));
    });
    if (overflowCount > 0) {
      const more = document.createElement('span');
      more.className = 'sidebar-bot-mini-more';
      more.textContent = `+${overflowCount}`;
      more.title = `Ещё ботов: ${overflowCount}`;
      botMiniIndicatorsEl.appendChild(more);
    }
    botMiniIndicatorsEl.hidden = false;
  }

  async function refreshSidebarBotMiniIndicators() {
    if (!botMiniIndicatorsEl) return;
    try {
      const channelsResponse = await fetch('/api/channels');
      if (!channelsResponse.ok) {
        throw new Error(`HTTP ${channelsResponse.status}`);
      }
      const payload = await channelsResponse.json().catch(() => ({}));
      const channels = Array.isArray(payload?.channels) ? payload.channels : [];
      if (!channels.length) {
        renderSidebarBotMiniIndicators([], 0);
        return;
      }
      const visibleChannels = channels.slice(0, MAX_SIDEBAR_BOT_DOTS);
      const overflowCount = Math.max(0, channels.length - visibleChannels.length);
      const statuses = await Promise.all(
        visibleChannels.map((channel) => resolveSidebarBotRuntimeStatus(channel))
      );
      renderSidebarBotMiniIndicators(statuses, overflowCount);
    } catch (_error) {
      renderSidebarBotMiniIndicators([], 0);
    }
  }

  refreshSidebarBotMiniIndicators();
  setInterval(refreshSidebarBotMiniIndicators, 30000);
})();

// Авто-активация пункта меню по текущему пути
(function markActiveNav(){
  const path = location.pathname.replace(/\/+$/, '');     // без хвостового слеша
  const nav = document.querySelector('.sidebar .sidebar-nav');
  let activeLink = null;
  document.querySelectorAll('.sidebar .nav-link[href]').forEach(a=>{
    const href = (a.getAttribute('href') || '').replace(/\/+$/, '');
    if (!href) return;
    // точное совпадение или «раздел» (например, /tickets и /tickets/123)
    if (path === href || (href && path.startsWith(href + '/'))) {
      a.classList.add('active');
      if (!activeLink) {
        activeLink = a;
      }
    }
  });
  if (!nav || !activeLink) return;
  requestAnimationFrame(() => {
    const navRect = nav.getBoundingClientRect();
    const linkRect = activeLink.getBoundingClientRect();
    if (linkRect.top < navRect.top + 12 || linkRect.bottom > navRect.bottom - 12) {
      activeLink.scrollIntoView({ block: 'nearest' });
    }
  });
})();

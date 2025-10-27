// panel/static/sidebar.js
(function () {
  const sidebar = document.getElementById('app-sidebar');
  if (!sidebar) return;

  const pinBtn = document.getElementById('pinSidebarBtn');
  const bellBtn = document.getElementById('bellBtn');
  const bellBadge = document.getElementById('notify-count');
  const root = document.body;

  const LS_KEY_PIN = 'sidebarPinned';
  let pinned = localStorage.getItem(LS_KEY_PIN) === '1';

  // оборачиваем страницу, чтобы не было пустоты справа
  function ensureLayoutWrap() {
    const root = document.body;
    if (!root.classList.contains('with-sidebar')) root.classList.add('with-sidebar');
    if (!document.querySelector('.sidebar-layout')) {
      const wrap = document.createElement('div');
      wrap.className = 'sidebar-layout';
      const main = document.querySelector('main') || document.getElementById('main') || document.body.firstElementChild;
      // переносим основной контейнер
      const mainWrap = document.createElement('div');
      mainWrap.className = 'main-content';
      // если main отсутствует – создаём
      if (!main || main === sidebar) {
        const m = document.createElement('div');
        m.className = 'main-content';
        while (document.body.firstChild) {
          if (document.body.firstChild === sidebar) { wrap.appendChild(sidebar); }
          else { m.appendChild(document.body.firstChild); }
        }
        wrap.appendChild(m);
        document.body.appendChild(wrap);
      } else {
        const parent = main.parentElement;
        wrap.appendChild(sidebar);
        parent.insertBefore(wrap, main);
        wrap.appendChild(main);
        main.classList.add('main-content');
      }
    }
  }
  ensureLayoutWrap();

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

  const nav = sidebar.querySelector('.sidebar-nav');
  const ORDER_STORAGE_KEY = 'sidebarNavOrder';
  const DEFAULT_ORDER = ['dialogs', 'tasks', 'clients', 'object_passports', 'knowledge_base', 'dashboard', 'analytics', 'settings'];
  const NAV_TITLE_DEFAULT = 'Нажмите «Редактировать порядок», чтобы изменить расположение страниц';
  const NAV_TITLE_EDITING = 'Перетащите пункты, чтобы изменить порядок';
  const resetOrderBtn = document.getElementById('resetSidebarOrderBtn');
  const editOrderBtn = document.getElementById('editSidebarOrderBtn');
  let isEditingOrder = false;

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
  }

  function normalizedDefaultOrder() {
    return sanitizeOrder(DEFAULT_ORDER);
  }

  function persistCurrentOrder() {
    if (!nav) return;
    const current = sanitizeOrder(getNavLinks().map((link) => link.dataset.pageKey));
    const def = normalizedDefaultOrder();
    if (JSON.stringify(current) === JSON.stringify(def)) {
      localStorage.removeItem(ORDER_STORAGE_KEY);
    } else {
      localStorage.setItem(ORDER_STORAGE_KEY, JSON.stringify(current));
    }
  }

  function loadSavedOrder() {
    try {
      const raw = localStorage.getItem(ORDER_STORAGE_KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return null;
      return sanitizeOrder(parsed);
    } catch (error) {
      return null;
    }
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
      editOrderBtn.textContent = isEditingOrder ? 'Готово' : 'Редактировать порядок';
      editOrderBtn.classList.toggle('active', isEditingOrder);
      editOrderBtn.setAttribute('aria-pressed', isEditingOrder ? 'true' : 'false');
    }
    if (nav) {
      nav.setAttribute('title', isEditingOrder ? NAV_TITLE_EDITING : NAV_TITLE_DEFAULT);
    }
  }

  function setEditingOrder(enabled) {
    const nextState = Boolean(enabled);
    if (isEditingOrder === nextState) return;
    isEditingOrder = nextState;
    applyDraggableState(isEditingOrder);
    updateEditOrderButton();
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
    setupDragAndDrop();
    applyDraggableState(false);
    updateEditOrderButton();
    if (resetOrderBtn) {
      resetOrderBtn.addEventListener('click', () => {
        applyOrder(DEFAULT_ORDER);
        localStorage.removeItem(ORDER_STORAGE_KEY);
        setEditingOrder(false);
      });
    }
  }

  if (editOrderBtn) {
    editOrderBtn.addEventListener('click', () => {
      setEditingOrder(!isEditingOrder);
    });
  }

  // hover раскрытие, если не pinned
  sidebar.addEventListener('mouseenter', () => {
    if (!pinned) {
      sidebar.classList.add('hovering');
      if (root) root.classList.add('sidebar-hovering');
    }
  });
  sidebar.addEventListener('mouseleave', () => {
    if (!pinned) {
      sidebar.classList.remove('hovering');
      if (root) root.classList.remove('sidebar-hovering');
    }
  });

  // клик по "📌"
  if (pinBtn) {
    pinBtn.addEventListener('click', () => {
      pinned = !pinned;
      localStorage.setItem(LS_KEY_PIN, pinned ? '1' : '0');
      applyState();
    });
  }

  const bellWrapper = document.getElementById('notify-bell-wrapper');
  const bellDropdown = document.getElementById('notify-dropdown');
  let notificationsOpen = false;
  let toastEl = null;
  let toastTimer = null;
  let lastUnreadCount = 0;
  let hasInitialUnread = false;

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

  function renderNotifications(items) {
    if (!bellDropdown) return;
    if (!Array.isArray(items) || !items.length) {
      bellDropdown.innerHTML = '<div class="notif-item text-muted">Новых уведомлений нет</div>';
      return;
    }
    bellDropdown.innerHTML = items.map((item) => {
      const title = (item && item.text) ? item.text : 'Уведомление';
      const url = item && item.url ? item.url : '';
      const dateStr = item && item.created_at ? new Date(item.created_at).toLocaleString('ru-RU') : '';
      const linkStart = url ? `<a class="stretched-link" href="${url}" target="_blank" rel="noopener">` : '';
      const linkEnd = url ? '</a>' : '';
      return `
        <div class="notif-item position-relative" data-id="${item.id || ''}">
          ${linkStart}<div class="fw-semibold">${title}</div>${linkEnd}
          ${dateStr ? `<div class="notif-time">${dateStr}</div>` : ''}
        </div>
      `;
    }).join('');
  }

  async function loadNotifications() {
    if (!bellDropdown) return;
    bellDropdown.hidden = false;
    bellDropdown.innerHTML = '<div class="notif-item text-muted">Загрузка...</div>';
    notificationsOpen = true;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'true');
    try {
      const response = await fetch('/api/notifications');
      if (!response.ok) throw new Error('Failed to load notifications');
      const data = await response.json();
      const items = Array.isArray(data) ? data : Array.isArray(data.items) ? data.items : [];
      renderNotifications(items);
      setBellCount(0);
      lastUnreadCount = 0;
      hasInitialUnread = true;
      const markPromises = items
        .filter((item) => item && item.id)
        .map((item) => fetch(`/api/notifications/${item.id}/read`, { method: 'POST' }));
      if (markPromises.length) {
        Promise.allSettled(markPromises);
      }
    } catch (error) {
      bellDropdown.innerHTML = '<div class="notif-item text-danger">Не удалось загрузить уведомления</div>';
    }
  }

  function closeNotifications() {
    if (!bellDropdown) return;
    bellDropdown.hidden = true;
    notificationsOpen = false;
    if (bellBtn) bellBtn.setAttribute('aria-expanded', 'false');
  }

  async function updateNotificationCount() {
    try {
      const response = await fetch('/api/notifications/unread_count');
      if (!response.ok) return;
      const data = await response.json();
      const newCount = Number(data.count || 0);
      setBellCount(newCount);
      if (hasInitialUnread && newCount > lastUnreadCount) {
        const diff = newCount - lastUnreadCount;
        const message = diff === 1 ? 'Новое оповещение' : `Новых оповещений: ${diff}`;
        showNotificationToast(message);
      }
      lastUnreadCount = newCount;
      hasInitialUnread = true;
    } catch (error) {
      /* ignore */
    }
  }

  if (bellBtn) {
    bellBtn.addEventListener('click', async (event) => {
      event.preventDefault();
      if (notificationsOpen) {
        closeNotifications();
      } else {
        await loadNotifications();
      }
    });
  }

  document.addEventListener('click', (event) => {
    if (!notificationsOpen) return;
    if (bellWrapper && bellWrapper.contains(event.target)) return;
    closeNotifications();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && notificationsOpen) {
      closeNotifications();
    }
  });

  updateNotificationCount();
  setInterval(updateNotificationCount, 15000);
})();

// Авто-активация пункта меню по текущему пути
(function markActiveNav(){
  const path = location.pathname.replace(/\/+$/, '');     // без хвостового слеша
  document.querySelectorAll('.sidebar .nav-link[href]').forEach(a=>{
    const href = (a.getAttribute('href') || '').replace(/\/+$/, '');
    if (!href) return;
    // точное совпадение или «раздел» (например, /tickets и /tickets/123)
    if (path === href || (href && path.startsWith(href + '/'))) {
      a.classList.add('active');
    }
  });
})();

(function markActiveNav(){
  const path = location.pathname.replace(/\/+$/, '');
  document.querySelectorAll('.sidebar .nav-link[href]').forEach(a=>{
    const href = (a.getAttribute('href')||'').replace(/\/+$/, '');
    if (href && (path === href || path.startsWith(href + '/'))) a.classList.add('active');
  });
})();

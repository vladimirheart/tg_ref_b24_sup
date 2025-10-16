// panel/static/sidebar.js
(function () {
  const sidebar = document.getElementById('app-sidebar');
  if (!sidebar) return;

  const pinBtn = document.getElementById('pinSidebarBtn');
  const bellBtn = document.getElementById('bellBtn') || document.querySelector('#notify-bell .bell');
  const bellBadge = document.getElementById('notify-count');

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

  function applyState() {
    if (pinned) {
      sidebar.classList.add('pinned');
      sidebar.classList.remove('collapsed', 'hovering');
    } else {
      sidebar.classList.remove('pinned');
      sidebar.classList.add('collapsed');
    }
  }
  applyState();

  // hover раскрытие, если не pinned
  sidebar.addEventListener('mouseenter', () => {
    if (!pinned) sidebar.classList.add('hovering');
  });
  sidebar.addEventListener('mouseleave', () => {
    if (!pinned) sidebar.classList.remove('hovering');
  });

  // клик по "📌"
  if (pinBtn) {
    pinBtn.addEventListener('click', () => {
      pinned = !pinned;
      localStorage.setItem(LS_KEY_PIN, pinned ? '1' : '0');
      applyState();
    });
  }

  const bellWrapper = document.getElementById('notify-bell');
  const bellDropdown = document.getElementById('notify-dropdown');
  let notificationsOpen = false;

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
    try {
      const response = await fetch('/api/notifications');
      if (!response.ok) throw new Error('Failed to load notifications');
      const data = await response.json();
      const items = Array.isArray(data) ? data : Array.isArray(data.items) ? data.items : [];
      renderNotifications(items);
      setBellCount(0);
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
  }

  async function updateNotificationCount() {
    try {
const response = await fetch('/api/notifications/unread_count');
      if (!response.ok) return;
      const data = await response.json();
      setBellCount(Number(data.count || 0));
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

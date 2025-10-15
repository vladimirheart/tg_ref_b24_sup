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

  // оповещения (заглушка опроса)
  async function poll() {
    try {
      const r = await fetch('/api/notifications/count');
      if (!r.ok) return;
      const data = await r.json();
      const n = Number(data.count || 0);
      if (n > 0) {
        bellBadge.hidden = false;
        bellBadge.textContent = n;
      } else {
        bellBadge.hidden = true;
      }
    } catch (e) { /* ignore */ }
  }
  poll(); setInterval(poll, 7000);
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

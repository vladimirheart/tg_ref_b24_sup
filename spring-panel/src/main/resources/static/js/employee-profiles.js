/* Work identity shared by tasks, dialogs and employee pickers. No assignment permissions are added here. */
(function () {
  'use strict';
  const cache = new Map(), pending = new Map();
  const key = value => String(value || '').toLocaleLowerCase();
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  function photoUrl(value) {
    if (!value) return '';
    try {
      const url = new URL(value, location.origin);
      return (['http:', 'https:'].includes(url.protocol) && !url.username && !url.password) ? url.href : '';
    } catch (_) { return ''; }
  }
  function remember(person) {
    if (!person?.username) return;
    const id = key(person.username), previous = cache.get(id) || {};
    cache.set(id, { ...previous, ...(person.resolved ? person : Object.fromEntries(Object.entries(person).filter(([, value]) => value != null && value !== ''))) });
  }
  function get(username) { return cache.get(key(username)) || { username, fullName: username }; }
  async function load(username, fresh = false) {
    const id = key(username);
    if (fresh) pending.delete(id);
    if (!pending.has(id)) pending.set(id, fetch('/api/panel-users/profile?' + new URLSearchParams({ username }), {
      credentials: 'same-origin', cache: 'no-store', redirect: 'error', headers: { Accept: 'application/json' },
    }).then(async response => {
      if (!response.ok) throw new Error(response.status === 404 ? 'Сотрудник не найден' : 'Не удалось загрузить карточку сотрудника');
      const person = await response.json();
      if (key(person.username) !== id) throw new Error('Не удалось определить сотрудника');
      remember({ ...person, resolved: true });
      window.EmployeePicker?.refresh();
      return get(username);
    }).catch(error => { pending.delete(id); throw error; }));
    return pending.get(id);
  }
  function avatar(person) {
    const name = person.fullName || person.username || '?';
    const initials = name.trim().split(/\s+/).slice(0, 2).map(part => Array.from(part)[0]).join('').toUpperCase();
    const photo = photoUrl(person.photo);
    return `<span class="employee-avatar" aria-hidden="true"><span>${escape(initials)}</span>${photo ? `<img src="${escape(photo)}" alt="" loading="lazy">` : ''}</span>`;
  }
  function link(username) {
    if (!username) return '';
    const person = get(username);
    return `<a class="employee-identity" data-employee-identity="${escape(username)}" href="/users/${encodeURIComponent(username)}" title="Открыть карточку сотрудника">${avatar(person)}<span class="employee-identity__name">${escape(person.fullName || username)}</span></a>`;
  }
  function refresh(root = document) {
    root.querySelectorAll('[data-employee-identity]').forEach(element => {
      const username = element.dataset.employeeIdentity;
      if (element.dataset.employeeHydrated === username) return;
      element.dataset.employeeHydrated = username;
      load(username).then(person => {
        if (!element.isConnected || element.dataset.employeeIdentity !== username) return;
        element.innerHTML = avatar(person) + `<span class="employee-identity__name">${escape(person.fullName || username)}</span>`;
      }).catch(() => { delete element.dataset.employeeHydrated; });
    });
  }
  let dialog, generation = 0, returnFocus;
  function ensureDialog() {
    if (dialog) return;
    dialog = document.createElement('dialog'); dialog.className = 'employee-profile'; dialog.id = 'employeeProfileDialog';
    dialog.setAttribute('aria-labelledby', 'employeeProfileTitle');
    dialog.innerHTML = '<div class="employee-profile__header"><span>Сотрудник</span><button type="button" class="btn-close" aria-label="Закрыть карточку сотрудника" autofocus></button></div><div class="employee-profile__body" aria-live="polite"></div>';
    document.body.append(dialog);
    dialog.querySelector('button').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
    dialog.addEventListener('close', () => { generation++; returnFocus?.isConnected && returnFocus.focus({ preventScroll: true }); });
    dialog.addEventListener('keydown', event => { if (event.key === 'Escape') event.stopPropagation(); });
    // Bootstrap's underlying modal focus trap must not pull focus out of this top-layer dialog.
    document.addEventListener('focusin', event => { if (dialog.open && dialog.contains(event.target)) event.stopImmediatePropagation(); }, true);
  }
  async function open(username) {
    ensureDialog();
    if (!dialog.open) returnFocus = document.activeElement;
    const current = ++generation;
    const body = dialog.querySelector('.employee-profile__body');
    body.innerHTML = '<h2 id="employeeProfileTitle">Карточка сотрудника</h2><p role="status">Загрузка…</p>';
    if (!dialog.open) dialog.showModal();
    try {
      const person = await load(username, true); if (current !== generation || !dialog.open) return;
      body.innerHTML = `${avatar(person)}<h2 id="employeeProfileTitle">${escape(person.fullName || person.username)}</h2><p class="employee-profile__department">${escape(person.department || 'Отдел не указан')}</p><dl><dt>Логин</dt><dd>${escape(person.username)}</dd></dl>`;
    } catch (error) {
      if (current !== generation || !dialog.open) return;
      body.innerHTML = `<h2 id="employeeProfileTitle">Карточка сотрудника</h2><p role="alert">${escape(error.message)}</p><button type="button" class="btn btn-outline-secondary">Повторить</button>`;
      body.querySelector('button').addEventListener('click', () => open(username));
    }
  }
  document.addEventListener('click', event => {
    const target = event.target.closest('[data-employee-identity], a[href^="/users/"]');
    if (!target || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
    let username = target.dataset.employeeIdentity;
    if (!username) { try { username = decodeURIComponent(new URL(target.href).pathname.slice('/users/'.length)); } catch (_) { return; } }
    if (!username) return;
    event.preventDefault(); event.stopPropagation(); void open(username);
  }, true);
  document.addEventListener('error', event => { if (event.target.matches?.('.employee-avatar img')) event.target.remove(); }, true);
  new MutationObserver(records => {
    for (const record of records) for (const node of record.addedNodes) {
      if (!(node instanceof Element) || !node.isConnected) continue;
      if (node.matches('[data-employee-identity]')) refresh(node.parentElement);
      else if (node.querySelector('[data-employee-identity]')) refresh(node);
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
  window.EmployeeProfiles = { get, remember, avatar, link, refresh, open, load };
})();

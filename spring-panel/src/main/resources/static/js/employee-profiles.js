/* Shared employee identity and read-only preview. Assignment permissions remain with each module. */
(function () {
  'use strict';
  const cache = new Map(), pending = new Map();
  const SELECTOR = '[data-employee-identity], [data-employee-id]';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  function reference(value) {
    const object = value && typeof value === 'object' ? value : { username: value };
    if (object.userId != null && object.userId !== '') {
      const userId = String(object.userId);
      return /^[1-9][0-9]{0,18}$/.test(userId) ? { userId, key: 'id:' + userId } : null;
    }
    const username = typeof object.username === 'string' ? object.username : '';
    return username.trim() && username.length <= 200 ? { username, key: 'name:' + username.toLocaleLowerCase() } : null;
  }
  function photoUrl(value) {
    if (!value) return '';
    try {
      const url = new URL(value, location.origin);
      return (['http:', 'https:'].includes(url.protocol) && !url.username && !url.password) ? url.href : '';
    } catch (_) { return ''; }
  }
  function remember(person, requested = reference(person)) {
    if (!requested) return;
    const previous = cache.get(requested.key) || {};
    cache.set(requested.key, { ...previous, ...(person.resolved ? person : Object.fromEntries(Object.entries(person).filter(([, value]) => value != null && value !== ''))) });
  }
  function get(value) {
    const ref = reference(value);
    return (ref && cache.get(ref.key)) || { ...(typeof value === 'object' ? value : { username: value }) };
  }
  async function load(value, fresh = false) {
    const ref = reference(value);
    if (!ref) throw new Error('Не удалось определить сотрудника');
    if (fresh) pending.delete(ref.key);
    if (!pending.has(ref.key)) pending.set(ref.key, fetch('/api/panel-users/profile?' + new URLSearchParams(ref.userId ? { userId: ref.userId } : { username: ref.username }), {
      credentials: 'same-origin', cache: 'no-store', redirect: 'error', headers: { Accept: 'application/json' },
    }).then(async response => {
      if (!response.ok) throw new Error(response.status === 404 ? 'Сотрудник не найден или недоступен' : 'Не удалось загрузить карточку сотрудника');
      const person = await response.json();
      if (ref.userId ? String(person.userId) !== ref.userId : reference({ username: person.username })?.key !== ref.key) throw new Error('Не удалось определить сотрудника');
      const serverTime = Date.parse(person.serverTime);
      remember({ ...person, resolved: true, clockOffset: Number.isFinite(serverTime) ? serverTime - Date.now() : 0 }, ref);
      window.EmployeePicker?.refresh();
      return get(ref);
    }).catch(error => { pending.delete(ref.key); throw error; }));
    return pending.get(ref.key);
  }
  function avatar(person) {
    const name = person.fullName || person.username || '?';
    const initials = name.trim().split(/\s+/).slice(0, 2).map(part => Array.from(part)[0]).join('').toUpperCase();
    const photo = photoUrl(person.photo);
    return `<span class="employee-avatar" aria-hidden="true"><span>${escape(initials)}</span>${photo ? `<img src="${escape(photo)}" alt="" loading="lazy">` : ''}</span>`;
  }
  function link(value, options = {}) {
    const ref = reference(value);
    if (!ref) return escape(options.label || value?.fullName || (typeof value === 'string' ? value : '') || '—');
    if (typeof value === 'object') remember(value, ref);
    const person = get(ref), label = options.label || person.fullName || person.username || 'Сотрудник';
    const attrs = ref.userId ? `data-employee-id="${escape(ref.userId)}"` : `data-employee-identity="${escape(ref.username)}"`;
    const tag = options.optional || ref.userId ? 'span' : 'a';
    const href = ref.username ? `/users/${encodeURIComponent(ref.username)}` : '';
    return `<${tag} class="employee-identity${options.compact ? ' employee-identity--text' : ''}" ${attrs}${options.compact ? ' data-employee-compact="true"' : ''}${options.optional ? ' data-employee-optional="true"' : ''}${tag === 'a' ? ` href="${escape(href)}"` : ''} title="Открыть карточку сотрудника">${options.compact ? '' : avatar(person)}<span class="employee-identity__name">${escape(label)}</span></${tag}>`;
  }
  function elementReference(element) {
    return reference({ userId: element.dataset.employeeId, username: element.dataset.employeeIdentity });
  }
  function refresh(root = document) {
    const elements = [...root.querySelectorAll(SELECTOR)];
    if (root.matches?.(SELECTOR)) elements.unshift(root);
    elements.forEach(element => {
      const ref = elementReference(element);
      if (!ref || element.dataset.employeeHydrated === ref.key) return;
      element.dataset.employeeHydrated = ref.key;
      element.classList.add('employee-identity');
      if (element.dataset.employeeCompact === 'true') element.classList.add('employee-identity--text');
      const optional = element.dataset.employeeOptional === 'true';
      if (!optional && !element.matches('a,button')) { element.tabIndex = 0; element.setAttribute('role', 'button'); }
      element.title = 'Открыть карточку сотрудника';
      if (element.dataset.employeePreserveContent === 'true') return;
      load(ref).then(person => {
        if (!element.isConnected || elementReference(element)?.key !== ref.key) return;
        element.dataset.employeeVerified = 'true';
        if (!element.matches('a,button')) { element.tabIndex = 0; element.setAttribute('role', 'button'); }
        element.innerHTML = (element.dataset.employeeCompact === 'true' ? '' : avatar(person)) + `<span class="employee-identity__name">${escape(person.fullName || person.username || 'Сотрудник')}</span>`;
      }).catch(() => {
        delete element.dataset.employeeHydrated;
        if (optional) {
          delete element.dataset.employeeIdentity; delete element.dataset.employeeId;
          element.classList.remove('employee-identity', 'employee-identity--text');
          element.removeAttribute('title'); element.removeAttribute('role'); element.removeAttribute('tabindex');
        }
      });
    });
  }
  let dialog, generation = 0, returnFocus, refreshTimer;
  function ensureDialog() {
    if (dialog) return;
    dialog = document.createElement('dialog'); dialog.className = 'employee-profile'; dialog.id = 'employeeProfileDialog';
    dialog.setAttribute('aria-labelledby', 'employeeProfileTitle');
    dialog.innerHTML = '<div class="employee-profile__header"><span>Сотрудник</span><button type="button" class="btn-close" aria-label="Закрыть карточку сотрудника" autofocus></button></div><div class="employee-profile__body" aria-live="polite"></div>';
    document.body.append(dialog);
    dialog.querySelector('button').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
    dialog.addEventListener('close', () => {
      // Native close events are queued: an earlier close must not invalidate a reopened preview.
      if (dialog.open) return;
      generation++; clearTimeout(refreshTimer); returnFocus?.isConnected && returnFocus.focus({ preventScroll: true });
    });
    dialog.addEventListener('keydown', event => { if (event.key === 'Escape') event.stopPropagation(); });
    document.addEventListener('focusin', event => { if (dialog.open && dialog.contains(event.target)) event.stopImmediatePropagation(); }, true);
  }
  function statusMarkup(person) {
    const at = person.lastPortalActivityAt ? new Date(person.lastPortalActivityAt) : null;
    const known = at && Number.isFinite(at.getTime());
    const online = known && Date.now() + (person.clockOffset || 0) - at.getTime() <= 30 * 60 * 1000;
    const lastActivity = known ? at.toLocaleString('ru-RU') : 'Не зафиксирована';
    return `<div class="employee-profile__statuses"><span class="employee-profile__status is-${online ? 'online' : 'offline'}" data-profile-presence title="Offline после 30 минут без активности в панели"><i class="bi bi-circle-fill" aria-hidden="true"></i>${online ? 'Online' : 'Offline'}</span><span class="employee-profile__status${person.blocked ? ' is-blocked' : ''}" data-profile-access><i class="bi bi-${person.blocked ? 'lock-fill' : 'shield-check'}" aria-hidden="true"></i>${person.blocked ? 'Заблокирован' : 'Активен'}</span></div><div class="employee-profile__activity">Последняя активность: <time${known ? ` datetime="${escape(at.toISOString())}"` : ''}>${escape(lastActivity)}</time></div>`;
  }
  function renderProfile(person) {
    const email = typeof person.email === 'string' ? person.email.trim() : '';
    const phones = Array.isArray(person.phones) ? person.phones.filter(phone => phone && typeof phone.value === 'string' && phone.value.trim()) : [];
    const labels = { work: 'Рабочий', mobile: 'Мобильный', personal: 'Личный', home: 'Домашний', other: 'Телефон' };
    const contacts = [];
    if (email) contacts.push(`<dt>E-mail</dt><dd><a href="mailto:${escape(encodeURIComponent(email))}">${escape(email)}</a></dd>`);
    for (const phone of phones) {
      const number = phone.value.trim(), dial = number.replace(/[^0-9+*#,;]/g, '');
      contacts.push(`<dt>${escape(Object.hasOwn(labels, phone.label) ? labels[phone.label] : phone.label || 'Телефон')}</dt><dd>${dial ? `<a href="tel:${escape(encodeURIComponent(dial))}">${escape(number)}</a>` : escape(number)}</dd>`);
    }
    dialog.querySelector('.employee-profile__body').innerHTML = `${avatar({ ...person, username: '', fullName: person.fullName || 'Сотрудник' })}<h2 id="employeeProfileTitle">${escape(person.fullName || 'Сотрудник')}</h2>${person.department ? `<p class="employee-profile__department">${escape(person.department)}</p>` : ''}<div data-profile-status>${statusMarkup(person)}</div><section class="employee-profile__contacts" aria-label="Контактные данные"><h3>Контакты</h3>${contacts.length ? `<dl>${contacts.join('')}</dl>` : '<p class="text-muted mb-0">Контактные данные не указаны.</p>'}</section>`;
  }
  function scheduleRefresh(ref, current) {
    clearTimeout(refreshTimer);
    refreshTimer = setTimeout(async () => {
      if (!dialog?.open || current !== generation) return;
      try {
        const person = await load(ref, true);
        if (dialog.open && current === generation) renderProfile(person);
      } catch (_) {
        const status = dialog.querySelector('[data-profile-status]');
        if (status && current === generation) status.innerHTML = statusMarkup(get(ref));
      }
      if (dialog.open && current === generation) scheduleRefresh(ref, current);
    }, 60000);
  }
  async function open(value) {
    const ref = reference(value);
    if (!ref) return;
    ensureDialog(); clearTimeout(refreshTimer);
    if (!dialog.open) returnFocus = document.activeElement;
    const current = ++generation;
    const body = dialog.querySelector('.employee-profile__body');
    body.innerHTML = '<h2 id="employeeProfileTitle">Карточка сотрудника</h2><p role="status">Загрузка…</p>';
    if (!dialog.open) dialog.showModal();
    try {
      const person = await load(ref, true); if (current !== generation || !dialog.open) return;
      renderProfile(person); scheduleRefresh(ref, current);
    } catch (error) {
      if (current !== generation || !dialog.open) return;
      body.innerHTML = `<h2 id="employeeProfileTitle">Карточка сотрудника</h2><p role="alert">${escape(error.message)}</p><button type="button" class="btn btn-outline-secondary">Повторить</button>`;
      body.querySelector('button').addEventListener('click', () => open(ref));
    }
  }
  document.addEventListener('click', event => {
    const target = event.target.closest(`${SELECTOR}, a[href^="/users/"]`);
    if (!target || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey || event.button !== 0) return;
    if (target.dataset.employeeOptional === 'true' && target.dataset.employeeVerified !== 'true') return;
    let ref = elementReference(target);
    if (!ref && target.matches('a[href^="/users/"]')) { try { ref = reference(decodeURIComponent(new URL(target.href).pathname.slice('/users/'.length))); } catch (_) { return; } }
    if (!ref) return;
    event.preventDefault(); event.stopPropagation(); void open(ref);
  }, true);
  document.addEventListener('keydown', event => {
    if (!['Enter', ' '].includes(event.key) || event.target.matches('a,button,input,select,textarea')) return;
    const target = event.target.closest(SELECTOR);
    if (target) { event.preventDefault(); target.click(); }
  });
  document.addEventListener('error', event => { if (event.target.matches?.('.employee-avatar img')) event.target.remove(); }, true);
  new MutationObserver(records => {
    for (const record of records) for (const node of record.addedNodes) {
      if (!(node instanceof Element) || !node.isConnected) continue;
      if (node.matches(SELECTOR) || node.querySelector(SELECTOR)) refresh(node);
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => refresh()); else refresh();
  function node(value, options = {}) {
    const template = document.createElement('template');
    template.innerHTML = link(value, options);
    return template.content.firstChild || document.createTextNode('—');
  }
  const actor = (value, options = {}) => link(value, { compact: true, optional: true, ...options });
  window.EmployeeProfiles = { get, remember, avatar, link, actor, node, refresh, open, load };
})();

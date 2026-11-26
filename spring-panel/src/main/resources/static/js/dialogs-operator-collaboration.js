(() => {
  'use strict';
  const clean = value => String(value ?? '');
  const publicId = value => /^[a-f0-9]{32}$/.test(clean(value));
  function matches(owner, row) {
    return owner && row && owner.ticketId === row.dataset.ticketId
      && owner.ticketPublicId === row.dataset.ticketPublicId
      && owner.scopeKind === row.dataset.scopeKind
      && owner.businessId === clean(row.dataset.businessId);
  }
  async function request(path, body) {
    const headers = { 'Content-Type': 'application/json' };
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN';
    if (token) headers[header] = token;
    const response = await fetch(path, { method: body ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store',
      signal: AbortSignal.timeout(15000), headers, ...(body ? { body: JSON.stringify(body) } : {}) })
      .catch(() => { throw new Error('Не удалось дождаться ответа сервера. Повторите действие.'); });
    const data = await response.json().catch(() => { throw new Error('Не удалось прочитать ответ сервера. Повторите действие.'); });
    if (!response.ok) throw new Error(response.status === 409 ? 'Данные или права изменились. Обновите диалог.'
      : response.status === 403 ? 'Недостаточно прав для действия.' : 'Не удалось загрузить или сохранить данные диалога.');
    return data;
  }
  window.DialogOperatorActions = {
    request, matches,
    async take(row) {
      if (!row || row.dataset.scopeKind !== 'BUSINESS' || !publicId(row.dataset.ticketPublicId)) throw new Error('Обновите диалог.');
      const path = '/api/operator-dialogs/' + row.dataset.ticketPublicId;
      const state = await request(path + '/assignment');
      if (!matches(state.owner, row) || !state.canManage) throw new Error('Назначение недоступно. Обновите диалог.');
      return request(path + '/take', { ...state.owner, revision: state.revision });
    },
  };

  const panel = document.getElementById('dialogInternalChat');
  if (!panel) return;
  const thread = panel.querySelector('[data-internal-thread]');
  const text = panel.querySelector('textarea');
  const send = panel.querySelector('[data-internal-send]');
  const older = panel.querySelector('[data-internal-older]');
  const status = panel.querySelector('[data-internal-status]');
  const badge = panel.querySelector('[data-internal-unread]');
  const jump = panel.querySelector('[data-internal-jump]');
  const emojiToggle = panel.querySelector('[data-internal-emoji-toggle]');
  const emojis = panel.querySelector('[data-internal-emojis]');
  const cache = new Map(), drafts = new Map(), pending = new Map(), unread = new Map();
  let context = null, generation = 0, loading = false, busy = false, active = '', cursor = '0';
  let unreadLoading = false, unreadAgain = false, unreadGeneration = 0, ackId = '0';
  const row = () => document.querySelector('tr.dialog-row-active');
  const key = () => {
    const current = row();
    return current && document.getElementById('dialogDetailsModal')?.classList.contains('show')
      && publicId(current.dataset.ticketPublicId) ? current.dataset.ticketPublicId : '';
  };
  const atBottom = () => thread.scrollHeight - thread.clientHeight - thread.scrollTop <= 20;
  const current = (owner, ticket) => ticket === key() && matches(owner, row());
  const notice = message => { status.textContent = message; status.hidden = !message; };
  const idValue = value => /^[0-9]+$/.test(String(value)) ? BigInt(value) : 0n;
  const latest = () => [...cache.keys()].reduce((a, b) => idValue(a) > idValue(b) ? a : b, '0');
  function scrollBottom() { thread.scrollTop = thread.scrollHeight; jump.hidden = true; }
  function setBadge(node, count) {
    const visible = idValue(count) > 0n;
    node.hidden = !visible;
    const label = visible ? (node.hasAttribute('data-internal-list-unread') ? 'Внутр. ' : '')
      + (idValue(count) > 99n ? '99+' : count) : '';
    if (node.textContent !== label) node.textContent = label;
    node.setAttribute('aria-label', 'Непрочитанные внутренние сообщения: ' + count);
  }
  function paintUnread() {
    setBadge(badge, unread.get(key())?.unreadCount || '0');
    for (const r of document.querySelectorAll('#dialogsTable tbody tr[data-ticket-public-id]')) {
      const cell = r.querySelector('[data-column-key="client"]');
      if (!cell) continue;
      let indicator = cell.querySelector('[data-internal-list-unread]');
      const count = unread.get(r.dataset.ticketPublicId)?.unreadCount || '0';
      if (!indicator && idValue(count) > 0n) {
        indicator = document.createElement('span');
        indicator.dataset.internalListUnread = '';
        indicator.className = 'badge bg-warning text-dark ms-2';
        indicator.title = 'Непрочитанные внутренние сообщения';
        cell.append(indicator);
      }
      if (indicator) setBadge(indicator, count);
    }
  }
  async function pollUnread() {
    if (document.visibilityState === 'hidden') return;
    if (unreadLoading) { unreadAgain = true; return; }
    const ids = [...new Set([...document.querySelectorAll('#dialogsTable tbody tr[data-ticket-public-id]')]
      .map(r => r.dataset.ticketPublicId).concat(key()).filter(publicId))].slice(0, 100);
    if (!ids.length) return;
    unreadLoading = true;
    const turn = unreadGeneration;
    try {
      const data = await request('/api/operator-dialog-unread', { ticketPublicIds: ids });
      if (turn !== unreadGeneration) return;
      for (const id of ids) unread.set(id, { unreadCount: '0', latestId: '0' });
      for (const item of data.items || []) if (ids.includes(item.ticketPublicId)) unread.set(item.ticketPublicId, item);
      paintUnread();
    } catch (_) {
      for (const id of ids) unread.delete(id);
      paintUnread();
    } finally {
      unreadLoading = false;
      if (unreadAgain) { unreadAgain = false; pollUnread(); }
    }
  }
  async function acknowledge() {
    const ticket = key(), owner = context?.owner, seen = latest();
    const rect = thread.getBoundingClientRect();
    const last = thread.lastElementChild?.getBoundingClientRect();
    if (!owner || !panel.open || document.visibilityState === 'hidden' || !atBottom() || !last
      || rect.height <= 0 || last.bottom > Math.min(rect.bottom, window.innerHeight) + 2
      || last.bottom <= Math.max(rect.top, 0) || !current(owner, ticket) || seen === '0'
      || idValue(seen) <= idValue(ackId)) return;
    ackId = seen;
    try {
      await request('/api/operator-dialogs/' + ticket + '/internal-messages/read', { ...owner, lastReadId: seen });
      if (!current(owner, ticket)) return;
      unreadGeneration++;
      unread.set(ticket, { unreadCount: '0', latestId: seen });
      paintUnread(); pollUnread();
    } catch (_) { if (current(owner, ticket)) ackId = '0'; }
  }
  function render(messages, append, forceBottom) {
    const stick = forceBottom || (!append && atBottom());
    const oldHeight = thread.scrollHeight, oldTop = thread.scrollTop, previousLatest = latest();
    for (const message of messages) cache.set(message.id, message);
    const ordered = [...cache.values()].sort((a, b) => idValue(a.id) < idValue(b.id) ? -1 : 1);
    const fragment = document.createDocumentFragment();
    for (const message of ordered) {
      const article = document.createElement('article');
      article.className = 'dialog-internal-message';
      const meta = document.createElement('div'); meta.className = 'small text-muted';
      meta.textContent = (message.authorName || message.author) + ' · ' + new Date(message.createdAt).toLocaleString('ru-RU');
      const body = document.createElement('div'); body.className = 'dialog-internal-body'; body.textContent = message.body;
      article.append(meta, body); fragment.append(article);
    }
    thread.replaceChildren(fragment);
    if (append) {
      cursor = messages.at(-1)?.id || cursor;
      older.hidden = messages.length < 50;
      thread.scrollTop = oldTop + thread.scrollHeight - oldHeight;
    } else {
      if (cursor === '0') { cursor = messages.at(-1)?.id || '0'; older.hidden = messages.length < 50; }
      if (stick) scrollBottom();
      else {
        thread.scrollTop = oldTop;
        if (latest() !== previousLatest) jump.hidden = false;
      }
    }
    requestAnimationFrame(acknowledge);
  }
  async function load(append = false, forceBottom = false) {
    const ticket = key(), turn = generation;
    if (!ticket || loading || !panel.open || document.visibilityState === 'hidden') return;
    loading = true;
    try {
      const data = await request('/api/operator-dialogs/' + ticket + '/internal-messages' + (append ? '?before=' + cursor : ''));
      if (turn !== generation || !current(data.owner, ticket)) return;
      context = data;
      render(data.messages || [], append, forceBottom || cache.size === 0);
      send.disabled = !data.canWrite || busy; text.disabled = !data.canWrite || busy; emojiToggle.disabled = !data.canWrite || busy;
      notice(data.canWrite ? '' : 'Только просмотр. Для отправки требуется право работы с диалогом.');
    } catch (error) {
      if (turn !== generation || ticket !== key()) return;
      context = null; cache.clear(); thread.replaceChildren();
      send.disabled = text.disabled = emojiToggle.disabled = true;
      notice(error.message);
    } finally { if (turn === generation) loading = false; }
  }
  async function sendMessage() {
    if (busy || !context?.canWrite || !text.value.trim() || !current(context.owner, key())) return;
    const ticket = key(), value = text.value, owner = context.owner;
    let requestInfo = pending.get(ticket);
    if (!requestInfo || requestInfo.body !== value) {
      const bytes = crypto.getRandomValues(new Uint8Array(16));
      bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
      const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
      requestInfo = { body: value, requestId: hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20) };
      pending.set(ticket, requestInfo);
    }
    busy = true; send.disabled = text.disabled = emojiToggle.disabled = true;
    try {
      await request('/api/operator-dialogs/' + ticket + '/internal-messages', { ...owner, ...requestInfo });
      if (!current(owner, ticket)) return;
      text.value = ''; drafts.delete(ticket); pending.delete(ticket); notice('');
      // A polling request may have started before this write; wait for its completion before the explicit refresh.
      while (loading && current(owner, ticket)) await new Promise(resolve => window.setTimeout(resolve, 20));
      if (current(owner, ticket)) await load(false, true);
    } catch (error) {
      if (current(owner, ticket)) notice(error.message + ' Текст сохранён; повторите отправку.');
    } finally {
      busy = false;
      if (current(owner, ticket)) {
        send.disabled = text.disabled = emojiToggle.disabled = !context?.canWrite;
        text.focus();
      }
    }
  }
  send.addEventListener('click', sendMessage);
  text.addEventListener('input', () => { if (key()) drafts.set(key(), text.value); });
  panel.addEventListener('toggle', () => { if (panel.open) load(false, true); });
  older.addEventListener('click', () => load(true));
  jump.addEventListener('click', () => { scrollBottom(); acknowledge(); });
  thread.addEventListener('scroll', () => { if (atBottom()) { jump.hidden = true; acknowledge(); } }, { passive: true });
  document.addEventListener('scroll', () => requestAnimationFrame(acknowledge), { passive: true, capture: true });
  emojiToggle.addEventListener('click', () => {
    emojis.hidden = !emojis.hidden;
    emojiToggle.setAttribute('aria-expanded', String(!emojis.hidden));
  });
  for (const emoji of ['😀', '🙂', '😉', '😂', '😊', '😍', '🤔', '😎', '😢', '😡', '👍', '👎', '👌', '🙏', '👏', '💪', '❤️', '🔥', '🎉', '✅', '❗', '❓']) {
    const button = document.createElement('button');
    button.type = 'button'; button.className = 'dialog-internal-emoji'; button.textContent = emoji;
    button.setAttribute('aria-label', 'Вставить ' + emoji);
    button.addEventListener('click', () => {
      if (text.disabled) return;
      text.setRangeText(emoji, text.selectionStart, text.selectionEnd, 'end');
      text.dispatchEvent(new Event('input', { bubbles: true })); text.focus();
      emojis.hidden = true; emojiToggle.setAttribute('aria-expanded', 'false');
    });
    emojis.append(button);
  }
  function syncContext() {
    const ticket = key(), next = ticket + ':' + clean(row()?.dataset.businessId);
    if (next !== active) {
      active = next; generation++; context = null; cache.clear(); thread.replaceChildren();
      text.value = drafts.get(ticket) || '';
      send.disabled = text.disabled = emojiToggle.disabled = true;
      cursor = ackId = '0'; loading = false; older.hidden = jump.hidden = emojis.hidden = true; notice('');
      paintUnread();
      if (ticket && panel.open) load(false, true);
      pollUnread();
    }
  }
  new MutationObserver(syncContext).observe(document.body, {
    subtree: true, attributes: true, attributeFilter: ['class', 'data-ticket-public-id', 'data-business-id'],
  });
  const initialize = () => {
    window.DialogsMediaComposerEnhancements?.installSendModeControl('dialogInternalText', 'dialogInternalSend', 'dialogInternalSendModeControl');
    syncContext(); pollUnread();
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize, { once: true });
  else initialize();
  window.setInterval(() => {
    syncContext();
    if (document.visibilityState !== 'hidden') {
      pollUnread();
      if (key() && panel.open && !busy) load();
    }
  }, 5000);
  document.addEventListener('visibilitychange', () => { if (document.visibilityState !== 'hidden') { pollUnread(); if (panel.open) load(); } });

  // Contain wheel/touch in the visible modal, including short/non-scrollable histories and nested modals.
  function canScrollInside(target, delta) {
    const modal = target?.closest?.('.modal.show');
    if (!modal) return false;
    for (let node = target; node && modal.contains(node); node = node.parentElement) {
      const style = getComputedStyle(node), max = node.scrollHeight - node.clientHeight;
      if (/(auto|scroll)/.test(style.overflowY) && max > 1
        && ((delta > 0 && node.scrollTop < max - 1) || (delta < 0 && node.scrollTop > 0))) return true;
      if (node === modal) break;
    }
    return false;
  }
  document.addEventListener('wheel', event => {
    if (document.getElementById('dialogDetailsModal')?.classList.contains('show') && event.deltaY
      && !canScrollInside(event.target, event.deltaY)) event.preventDefault();
  }, { passive: false, capture: true });
  let touchY = null;
  document.addEventListener('touchstart', event => { touchY = event.touches[0]?.clientY ?? null; }, { passive: true });
  document.addEventListener('touchmove', event => {
    const y = event.touches[0]?.clientY;
    if (touchY == null || y == null) return;
    const delta = touchY - y; touchY = y;
    if (document.getElementById('dialogDetailsModal')?.classList.contains('show') && delta
      && !canScrollInside(event.target, delta)) event.preventDefault();
  }, { passive: false, capture: true });
})();


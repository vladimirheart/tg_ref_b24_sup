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
      headers, ...(body ? { body: JSON.stringify(body) } : {}) });
    const data = await response.json().catch(() => ({}));
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
  let context = null, serial = 0, busy = false, requestId = null, requestBody = null, cursor = '0', showingOlder = false;
  const drafts = new Map();
  const row = () => document.querySelector('tr.dialog-row-active');
  const key = () => {
    const active = row();
    return active && document.getElementById('dialogDetailsModal')?.classList.contains('show')
      && publicId(active.dataset.ticketPublicId) ? active.dataset.ticketPublicId : '';
  };
  function current(saved, ticket) { return ticket === key() && matches(saved, row()); }
  function render(messages, append) {
    const entries = [...messages].reverse();
    if (!append) thread.replaceChildren();
    const fragment = document.createDocumentFragment();
    for (const message of entries) {
      const article = document.createElement('article');
      article.className = 'dialog-internal-message';
      const meta = document.createElement('div');
      meta.className = 'small text-muted';
      meta.textContent = message.author + ' · ' + new Date(message.createdAt).toLocaleString('ru-RU') + ' · Только сотрудникам';
      const body = document.createElement('div');
      body.style.whiteSpace = 'pre-wrap';
      body.textContent = message.body;
      article.append(meta, body); fragment.append(article);
    }
    if (append) thread.prepend(fragment); else thread.append(fragment);
    cursor = messages.at(-1)?.id || '0';
    older.hidden = messages.length < 50;
  }
  async function load(append = false) {
    const ticket = key(), turn = ++serial;
    if (!ticket) return;
    const saved = row();
    status.textContent = 'Загрузка внутренней переписки…';
    try {
      const data = await request('/api/operator-dialogs/' + ticket + '/internal-messages' + (append ? '?before=' + cursor : ''));
      if (turn !== serial || ticket !== key() || !matches(data.owner, saved) || !matches(data.owner, row())) return;
      context = data;
      render(data.messages || [], append);
      if (append) showingOlder = true;
      send.disabled = !data.canWrite || busy;
      text.disabled = !data.canWrite || busy;
      status.textContent = data.canWrite ? 'Клиент не видит эти сообщения. Доступ не зависит от приглашения.'
        : 'Только просмотр. Для отправки требуется право работы с диалогом.';
    } catch (error) {
      if (turn !== serial || ticket !== key()) return;
      context = null; thread.replaceChildren(); send.disabled = true; text.disabled = true;
      status.textContent = error.message;
    }
  }
  panel.addEventListener('toggle', () => { if (panel.open) load(); });
  older.addEventListener('click', () => load(true));
  send.addEventListener('click', async () => {
    if (busy || !context?.canWrite || !text.value.trim() || !current(context.owner, key())) return;
    const ticket = key(), value = text.value, owner = context.owner;
    if (!requestId || requestBody !== value) {
      const bytes = crypto.getRandomValues(new Uint8Array(16));
      bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
      const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
      requestId = hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
      requestBody = value;
    }
    busy = true; send.disabled = true; text.disabled = true;
    try {
      await request('/api/operator-dialogs/' + ticket + '/internal-messages', { ...owner, requestId, body: value });
      if (!current(owner, ticket)) return;
      text.value = ''; drafts.delete(ticket); requestId = null; requestBody = null;
      showingOlder = false;
      await load();
    } catch (error) {
      if (current(owner, ticket)) status.textContent = error.message + ' Текст сохранён; повторите отправку.';
    } finally {
      busy = false;
      if (current(owner, ticket)) { send.disabled = !context?.canWrite; text.disabled = !context?.canWrite; }
    }
  });
  text.addEventListener('input', () => { if (key()) drafts.set(key(), text.value); });
  let active = '';
  function syncContext() {
    const ticket = key();
    if (ticket !== active) {
      active = ticket; serial++; context = null; thread.replaceChildren();
      text.value = drafts.get(ticket) || ''; send.disabled = true; text.disabled = true;
      requestId = null; requestBody = null; older.hidden = true; showingOlder = false;
      status.textContent = 'Клиент не видит внутреннюю переписку.';
      if (ticket && panel.open) load();
    }
  }
  new MutationObserver(syncContext).observe(document.body, {
    subtree: true, attributes: true, attributeFilter: ['class', 'data-ticket-public-id', 'data-business-id'],
  });
  window.setInterval(() => {
    syncContext();
    const ticket = key();
    if (ticket && panel.open && !busy && document.visibilityState !== 'hidden') {
      // Preserve explicitly loaded older pages; refresh returns to the latest page only on user request.
      if (!showingOlder) load();
    }
  }, 5000);
})();


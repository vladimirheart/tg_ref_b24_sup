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
    const multipart = body instanceof FormData;
    const headers = multipart ? {} : { 'Content-Type': 'application/json' };
    const token = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN';
    if (token) headers[header] = token;
    const response = await fetch(path, { method: body ? 'POST' : 'GET', credentials: 'same-origin', cache: 'no-store',
      signal: AbortSignal.timeout(multipart ? 120000 : 15000), headers, ...(body ? { body: multipart ? body : JSON.stringify(body) } : {}) })
      .catch(error => { throw window.PanelErrors?.network(error, { mutating: Boolean(body), sending: Boolean(body) && path.includes('/internal-messages') }) || new Error('Не удалось дождаться ответа сервера. Проверьте результат действия перед повтором.'); });
    const data = await (window.PanelErrors ? window.PanelErrors.read(response, { mutating: Boolean(body), sending: Boolean(body) && path.includes('/internal-messages') }) : response.json());
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
  const picker = panel.querySelector('[data-internal-files]');
  const attach = panel.querySelector('[data-internal-attach]');
  const filePreview = panel.querySelector('[data-internal-file-preview]');
  const cache = new Map(), drafts = new Map(), pending = new Map(), unread = new Map(), fileDrafts = new Map();
  let fileSerial = 0, previewUrls = [], following = true;
  const stagedFiles = () => fileDrafts.get(key()) || [];
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
  function scrollBottom() { following = true; thread.scrollTop = thread.scrollHeight; jump.hidden = true; }
  function paintFiles() {
    previewUrls.forEach(url => URL.revokeObjectURL(url)); previewUrls = [];
    filePreview.replaceChildren();
    const selected = stagedFiles(); filePreview.hidden = !selected.length;
    for (const item of selected) {
      const card = document.createElement('div'); card.className = 'dialog-internal-pending-file';
      if (['image/png','image/jpeg','image/gif','image/webp'].includes(item.file.type)) {
        const image = document.createElement('img'); const url = URL.createObjectURL(item.file); previewUrls.push(url);
        image.src = url; image.alt = item.file.name; card.append(image);
      }
      const label = document.createElement('span'); label.textContent = item.file.name + ' · ' + (item.file.size / 1024 / 1024).toFixed(2) + ' МБ';
      const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'btn btn-outline-secondary btn-sm';
      remove.textContent = '×'; remove.setAttribute('aria-label', 'Убрать ' + item.file.name); remove.disabled = busy;
      remove.addEventListener('click', () => { if (!busy) { fileDrafts.set(key(), stagedFiles().filter(f => f.id !== item.id)); paintFiles(); } });
      card.append(label, remove); filePreview.append(card);
    }
  }
  function addFiles(files) {
    if (busy || !context?.canWrite) return;
    const next = [...stagedFiles(), ...Array.from(files).map(file => ({ file, id: ++fileSerial }))];
    if (next.length > 10 || next.some(f => f.file.size === 0) || next.reduce((sum, f) => sum + f.file.size, 0) > 50 * 1024 * 1024) {
      notice('До 10 непустых файлов, суммарно до 50 МБ. Выбранные ранее файлы сохранены.'); return;
    }
    fileDrafts.set(key(), next); notice(''); paintFiles();
  }
  attach.addEventListener('click', () => { if (!busy && context?.canWrite) picker.click(); });
  picker.addEventListener('change', () => { addFiles(picker.files); picker.value = ''; });
  text.addEventListener('paste', event => {
    let files = Array.from(event.clipboardData?.files || []);
    if (!files.length) files = Array.from(event.clipboardData?.items || []).filter(item => item.kind === 'file').map(item => item.getAsFile()).filter(Boolean);
    if (files.length) { event.preventDefault(); event.stopPropagation(); addFiles(files); }
  });
  panel.addEventListener('dragover', event => {
    if (Array.from(event.dataTransfer?.types || []).includes('Files')) { event.preventDefault(); panel.classList.add('is-dragover'); }
  });
  panel.addEventListener('dragleave', event => { if (!panel.contains(event.relatedTarget)) panel.classList.remove('is-dragover'); });
  panel.addEventListener('drop', event => {
    const files = Array.from(event.dataTransfer?.files || []);
    if (files.length) { event.preventDefault(); event.stopPropagation(); panel.classList.remove('is-dragover'); addFiles(files); }
  });
  function renderAttachments(container, attachments) {
    for (const file of Array.isArray(attachments) ? attachments : []) {
      if (!/^[a-f0-9-]{36}$/.test(file.id)) continue;
      const url = '/api/operator-dialogs/' + key() + '/internal-messages/attachments/' + encodeURIComponent(file.id);
      const link = document.createElement('a'); link.href = url; link.download = file.name;
      link.textContent = '📎 ' + file.name + ' · ' + (Number(file.size) / 1024 / 1024).toFixed(2) + ' МБ';
      const box = document.createElement('div'); box.className = 'dialog-internal-file';
      if (file.mimeType?.startsWith('image/') && file.mimeType !== 'image/svg+xml') {
        const image = document.createElement('img'); image.src = url + '?preview=true'; image.alt = file.name; image.loading = 'lazy';
        const preview = document.createElement('a'); preview.href = image.src; preview.target = '_blank'; preview.rel = 'noopener noreferrer';
        image.addEventListener('load', () => { if (following) scrollBottom(); requestAnimationFrame(acknowledge); });
        preview.append(image); box.append(preview);
      } else if (file.mimeType?.startsWith('video/') || file.mimeType?.startsWith('audio/')) {
        const media = document.createElement(file.mimeType.startsWith('video/') ? 'video' : 'audio');
        media.src = url + '?preview=true'; media.controls = true; media.preload = 'metadata';
        media.addEventListener('loadedmetadata', () => { if (following) scrollBottom(); });
        box.append(media);
      }
      box.append(link); container.append(box);
    }
  }
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
    if (idValue(seen) <= idValue(ackId)) return;
    const rect = thread.getBoundingClientRect();
    const last = thread.lastElementChild?.getBoundingClientRect();
    let visibleTop = Math.max(rect.top, 0), visibleBottom = Math.min(rect.bottom, window.innerHeight);
    for (let parent = thread.parentElement; parent; parent = parent.parentElement) {
      if (/(auto|scroll|hidden|clip)/.test(getComputedStyle(parent).overflowY)) {
        const bounds = parent.getBoundingClientRect();
        visibleTop = Math.max(visibleTop, bounds.top); visibleBottom = Math.min(visibleBottom, bounds.bottom);
      }
    }
    if (!owner || !panel.open || document.visibilityState === 'hidden' || !atBottom() || !last
      || rect.height <= 0 || last.bottom > visibleBottom + 2
      || last.bottom <= visibleTop || !current(owner, ticket) || seen === '0'
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
    const existing = new Map([...thread.children].map(node => [node.dataset.messageId, node]));
    let anchor = thread.firstElementChild;
    for (const message of ordered) {
      let article = existing.get(message.id);
      if (!article) {
        article = document.createElement('article'); article.className = 'dialog-internal-message'; article.dataset.messageId = message.id;
        const meta = document.createElement('div'); meta.className = 'small text-muted';
        const body = document.createElement('div'); body.className = 'dialog-internal-body'; body.textContent = message.body;
        article.append(meta, body); renderAttachments(article, message.attachments);
      }
      const meta = article.querySelector('.small');
      const label = (message.authorName || message.author) + ' · ' + new Date(message.createdAt).toLocaleString('ru-RU');
      if (meta.textContent !== label) meta.textContent = label;
      if (article === anchor) anchor = anchor.nextElementSibling; else thread.insertBefore(article, anchor);
    }
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
      send.disabled = !data.canWrite || busy; text.disabled = attach.disabled = picker.disabled = !data.canWrite || busy; emojiToggle.disabled = !data.canWrite || busy;
      notice(data.canWrite ? '' : 'Только просмотр. Для отправки требуется право работы с диалогом.');
    } catch (error) {
      if (turn !== generation || ticket !== key()) return;
      context = null; cache.clear(); thread.replaceChildren();
      send.disabled = text.disabled = attach.disabled = picker.disabled = emojiToggle.disabled = true;
      notice(error.message);
    } finally { if (turn === generation) loading = false; }
  }
  async function sendMessage() {
    if (busy || !context?.canWrite || (!text.value.trim() && !stagedFiles().length) || !current(context.owner, key())) return;
    const ticket = key(), value = text.value, owner = context.owner;
    const files = [...stagedFiles()], fileKey = files.map(f => f.id).join(',');
    let requestInfo = pending.get(ticket);
    if (!requestInfo || requestInfo.body !== value || requestInfo.fileKey !== fileKey) {
      const bytes = crypto.getRandomValues(new Uint8Array(16));
      bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128;
      const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
      requestInfo = { body: value, fileKey, requestId: hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20) };
      pending.set(ticket, requestInfo);
    }
    busy = true; send.disabled = text.disabled = attach.disabled = picker.disabled = emojiToggle.disabled = true; paintFiles();
    if (files.length) notice('Загрузка вложений…');
    try {
      const command = { ...owner, body: value, requestId: requestInfo.requestId };
      let payload = command, path = '/api/operator-dialogs/' + ticket + '/internal-messages';
      if (files.length) {
        payload = new FormData(); payload.append('command', new Blob([JSON.stringify(command)], { type: 'application/json' }));
        files.forEach(item => payload.append('files', item.file, item.file.name)); path += '/files';
      }
      await request(path, payload);
      drafts.delete(ticket); pending.delete(ticket); fileDrafts.delete(ticket);
      if (!current(owner, ticket)) return;
      text.value = ''; paintFiles(); notice('');
      // A polling request may have started before this write; wait for its completion before the explicit refresh.
      while (loading && current(owner, ticket)) await new Promise(resolve => window.setTimeout(resolve, 20));
      if (current(owner, ticket)) await load(false, true);
    } catch (error) {
      if (current(owner, ticket)) notice(error.message + (files.length ? ' Текст и вложения сохранены; повторите отправку.' : ' Текст сохранён; повторите отправку.'));
    } finally {
      busy = false;
      if (context?.owner && current(context.owner, key())) {
        send.disabled = text.disabled = attach.disabled = picker.disabled = emojiToggle.disabled = !context?.canWrite; paintFiles();
        if (current(owner, ticket)) text.focus();
      }
    }
  }
  send.addEventListener('click', sendMessage);
  text.addEventListener('input', () => { if (key()) drafts.set(key(), text.value); });
  panel.addEventListener('toggle', () => { if (panel.open) load(false, true); });
  older.addEventListener('click', () => load(true));
  jump.addEventListener('click', () => { scrollBottom(); acknowledge(); });
  thread.addEventListener('scroll', () => { following = atBottom(); if (following) { jump.hidden = true; acknowledge(); } }, { passive: true });
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
      send.disabled = text.disabled = attach.disabled = picker.disabled = emojiToggle.disabled = true;
      cursor = ackId = '0'; loading = false; older.hidden = jump.hidden = emojis.hidden = true; notice('');
      following = true; picker.value = ''; panel.classList.remove('is-dragover'); paintFiles();
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


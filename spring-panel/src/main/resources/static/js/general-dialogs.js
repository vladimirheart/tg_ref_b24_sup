(() => {
  'use strict';
  const page = document.querySelector('[data-general-dialogs]');
  if (!page) return;
  const rows = page.querySelector('#generalTicketRows');
  const card = page.querySelector('#generalTicketCard');
  const status = page.querySelector('[data-general-status]');
  const history = page.querySelector('[data-general-history]');
  const answers = page.querySelector('[data-general-answers]');
  const previous = page.querySelector('#generalPrevious');
  const next = page.querySelector('#generalNext');
  const older = page.querySelector('#generalHistoryOlder');
  const limit = 50;
  let offset = 0;
  let generation = 0;
  let cardRequest = 0;
  let selected = null;
  let before = null;
  class QueueError extends Error {}

  const label = value => ({ pending: 'Ожидает', open: 'Открыто', resolved: 'Решено', closed: 'Закрыто' })[value] || value || '—';
  const time = value => value && !Number.isNaN(new Date(value).getTime()) ? new Date(value).toLocaleString('ru-RU') : '—';
  const validId = value => typeof value === 'string' && /^[0-9a-f]{32}$/.test(value);

  function announce(message, error = false) {
    status.textContent = message;
    status.className = `alert ${error ? 'alert-danger' : 'alert-info'}`;
  }

  function clearCard() {
    card.hidden = true;
    cardRequest += 1;
    selected = null;
    before = null;
    history.replaceChildren();
    answers.replaceChildren();
    older.hidden = true;
    older.disabled = false;
    for (const field of ['id', 'client', 'ticket-status', 'problem']) {
      page.querySelector(`[data-general-${field}]`).textContent = '';
    }
  }

  function fail(error) {
    clearCard();
    rows.replaceChildren();
    previous.disabled = true;
    next.disabled = true;
    announce(error instanceof QueueError ? error.message : 'Не удалось загрузить обращения.', true);
  }

  async function getJson(url) {
    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new QueueError(response.status === 403 || response.status === 404
      ? 'Нет доступа к обращениям или обращение недоступно.' : 'Не удалось загрузить обращения.');
    return response.json();
  }

  function cell(row, text) {
    const td = document.createElement('td');
    td.textContent = String(text ?? '');
    row.appendChild(td);
    return td;
  }

  function historyNodes(entries, publicId) {
    return entries.map(entry => {
      const item = document.createElement('div');
      item.className = 'border-bottom mb-3 pb-2';
      const title = document.createElement('p');
      title.className = 'small text-muted mb-1';
      title.textContent = `${entry.sender || '—'} · ${time(entry.timestamp)}`;
      const text = document.createElement('pre');
      text.className = 'text-break';
      text.textContent = entry.message || '';
      item.append(title, text);
      if (entry.hasAttachment && Number.isSafeInteger(entry.id) && entry.id > 0) {
        const link = document.createElement('a');
        link.textContent = 'Скачать вложение';
        link.href = `/api/general-tickets/${encodeURIComponent(publicId)}/history/${entry.id}/attachment`;
        item.appendChild(link);
      }
      return item;
    });
  }

  async function openTicket(publicId) {
    if (!validId(publicId)) return;
    clearCard();
    const request = cardRequest;
    const current = generation;
    const base = `/api/general-tickets/${encodeURIComponent(publicId)}`;
    try {
      const [ticket, response, answerResponse] = await Promise.all([
        getJson(base), getJson(`${base}/history`), getJson(`${base}/answers`)
      ]);
      if (request !== cardRequest || current !== generation) return;
      if (ticket.ticketPublicId !== publicId || response.ticket?.ticketPublicId !== publicId
          || answerResponse.ticket?.ticketPublicId !== publicId) throw new QueueError('Данные обращения недоступны.');
      page.querySelector('[data-general-id]').textContent = ticket.ticketId || '';
      page.querySelector('[data-general-client]').textContent = ticket.clientName || '—';
      page.querySelector('[data-general-ticket-status]').textContent = label(ticket.status);
      page.querySelector('[data-general-problem]').textContent = ticket.problem || '';
      for (const answer of answerResponse.answers) {
        const question = document.createElement('p');
        question.className = 'mb-1 fw-semibold';
        question.textContent = answer.questionText || answer.questionId;
        const value = document.createElement('pre');
        value.className = 'text-break';
        value.textContent = answer.valueText || answer.valueLabel || '—';
        answers.append(question, value);
      }
      if (!answerResponse.relationVerified) answers.textContent = 'Связь с ответами требует проверки.';
      else if (!answerResponse.answers.length) answers.textContent = 'Ответов на вопросы нет.';
      history.replaceChildren(...historyNodes(response.entries, publicId));
      before = response.nextBeforeId;
      older.hidden = !response.hasMore || before == null;
      selected = publicId;
      card.hidden = false;
    } catch (error) {
      if (request === cardRequest && current === generation) fail(error);
    }
  }

  async function load(reopen = null) {
    const current = ++generation;
    clearCard();
    rows.replaceChildren();
    previous.disabled = true;
    next.disabled = true;
    announce('Загрузка…');
    const params = new URLSearchParams({ limit: String(limit), offset: String(offset) });
    const filter = page.querySelector('#generalTicketStatus').value;
    const query = page.querySelector('#generalTicketSearch').value.trim();
    if (filter) params.set('status', filter);
    if (query) params.set('q', query);
    try {
      const response = await getJson(`/api/general-tickets/page?${params}`);
      if (current !== generation) return;
      for (const ticket of response.tickets) {
        const row = document.createElement('tr');
        cell(row, ticket.ticketId);
        cell(row, ticket.clientName || '—');
        cell(row, label(ticket.status));
        cell(row, time(ticket.createdAt));
        const action = cell(row, '');
        if (validId(ticket.ticketPublicId)) {
          const button = document.createElement('button');
          button.type = 'button';
          button.className = 'btn btn-outline-primary btn-sm';
          button.textContent = 'Открыть';
          button.addEventListener('click', () => openTicket(ticket.ticketPublicId));
          action.appendChild(button);
        }
        rows.appendChild(row);
      }
      previous.disabled = offset === 0;
      next.disabled = !response.hasMore;
      page.querySelector('#generalPageNumber').textContent = `Страница ${Math.floor(offset / limit) + 1}`;
      announce(response.tickets.length ? `Показано обращений: ${response.tickets.length}` : 'Обращений нет.');
      if (validId(reopen)) await openTicket(reopen);
    } catch (error) {
      if (current === generation) fail(error);
    }
  }

  older.addEventListener('click', async () => {
    if (older.disabled || before == null || !selected) return;
    older.disabled = true;
    const request = cardRequest;
    const current = generation;
    const publicId = selected;
    try {
      const response = await getJson(`/api/general-tickets/${encodeURIComponent(publicId)}/history?before=${before}`);
      if (request !== cardRequest || current !== generation) return;
      if (response.ticket?.ticketPublicId !== publicId) throw new QueueError('История обращения недоступна.');
      history.prepend(...historyNodes(response.entries, publicId));
      before = response.nextBeforeId;
      older.hidden = !response.hasMore || before == null;
    } catch (error) {
      if (request === cardRequest && current === generation) fail(error);
    } finally {
      if (request === cardRequest && current === generation) older.disabled = false;
    }
  });
  page.querySelector('#generalTicketFilters').addEventListener('submit', event => { event.preventDefault(); offset = 0; load(); });
  previous.addEventListener('click', () => { if (!previous.disabled) { offset = Math.max(0, offset - limit); load(); } });
  next.addEventListener('click', () => { if (!next.disabled) { offset += limit; load(); } });
  // Dedicated GENERAL stream; payloads are invalidations, never authority or rendered dialogue.
  if (typeof EventSource === 'function') {
    const source = new EventSource('/api/general-tickets/events/stream');
    const live = page.querySelector('[data-general-realtime]');
    let refreshTimer = null;
    let stopped = false;
    const cancelRefresh = () => { if (refreshTimer !== null) clearTimeout(refreshTimer); refreshTimer = null; };
    const refresh = () => {
      if (stopped) return;
      cancelRefresh();
      refreshTimer = setTimeout(() => { refreshTimer = null; load(selected); }, 200);
    };
    source.addEventListener('connected', () => {
      if (stopped) return;
      live.textContent = 'Автообновление подключено.';
      refresh(); // Reconnect repairs missed invalidations without replaying dialogue.
    });
    source.addEventListener('general_tickets_changed', event => {
      if (stopped) return;
      try { if (validId(JSON.parse(event.data).ticketPublicId)) refresh(); } catch (_) { /* Ignore invalid data. */ }
    });
    source.addEventListener('general_access_revoked', () => {
      if (stopped) return;
      stopped = true; cancelRefresh(); source.close(); generation += 1;
      live.textContent = 'Автообновление отключено: доступ отозван.';
      fail(new QueueError('Нет доступа к обращениям без бизнеса.'));
    });
    source.addEventListener('error', () => {
      if (stopped) return;
      cancelRefresh(); generation += 1;
      live.textContent = 'Соединение прервано; выполняется переподключение.';
      fail(new QueueError('Автообновление недоступно. Данные будут перечитаны после подключения.'));
    });
    globalThis.addEventListener?.('pagehide', () => {
      stopped = true; cancelRefresh(); source.close(); generation += 1; clearCard();
    });
  }
  load();
})();

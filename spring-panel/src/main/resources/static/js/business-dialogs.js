(() => {
  'use strict';

  const page = document.querySelector('[data-business-dialogs]');
  if (!page) return;

  const select = page.querySelector('#businessScopeSelect');
  const statusFilter = page.querySelector('#businessTicketStatusFilter');
  const ticketFilter = page.querySelector('#businessTicketIdFilter');
  const status = page.querySelector('[data-business-dialogs-status]');
  const summaryStatus = page.querySelector('[data-business-summary-status]');
  const summaryRows = page.querySelector('#businessTicketSummaryRows');
  const rows = page.querySelector('#businessTicketRows');
  const card = page.querySelector('#businessTicketCard');
  const history = card.querySelector('[data-ticket-history]');
  const older = card.querySelector('#businessHistoryOlder');
  const previous = page.querySelector('#businessTicketsPrevious');
  const next = page.querySelector('#businessTicketsNext');
  const pageNumber = page.querySelector('#businessTicketsPage');
  const limit = 50;
  let offset = 0;
  let generation = 0;
  let cardRequest = 0;
  let historyBefore = null;
  let currentTicketPublicId = null;
  let olderLoading = false;

  function announce(message, error = false) {
    status.textContent = message;
    status.className = `alert ${error ? 'alert-danger' : 'alert-info'}`;
  }

  async function getJson(path) {
    const response = await fetch(path, { credentials: 'same-origin' });
    if (!response.ok) {
      throw new Error(response.status === 403
        ? 'Нет доступа к выбранному бизнесу.' : 'Не удалось загрузить обращения.');
    }
    return response.json();
  }

  function cell(row, value) {
    const item = document.createElement('td');
    item.textContent = String(value ?? '');
    row.appendChild(item);
    return item;
  }

  function statusLabel(value) {
    return ({ pending: 'Ожидает', open: 'Открыто', resolved: 'Решено', closed: 'Закрыто' })[value]
      || value || 'Не задан';
  }

  function renderSummary(snapshot) {
    summaryRows.replaceChildren();
    summaryStatus.textContent = `Всего обращений в выбранном контексте: ${snapshot.total}. Сводка не зависит от фильтров списка.`;
    if (!snapshot.businesses.length) {
      const row = document.createElement('tr');
      const empty = cell(row, 'Нет доступных бизнесов.');
      empty.colSpan = 7;
      summaryRows.appendChild(row);
      return;
    }
    for (const business of snapshot.businesses) {
      const row = document.createElement('tr');
      cell(row, business.businessName);
      for (const key of ['total', 'pending', 'open', 'resolved', 'closed', 'other']) {
        cell(row, business[key]);
      }
      summaryRows.appendChild(row);
    }
  }

  async function openCard(ticket, expectedGeneration) {
    const request = ++cardRequest;
    card.hidden = true;
    older.hidden = true;
    older.disabled = false;
    historyBefore = null;
    currentTicketPublicId = null;
    olderLoading = false;
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/history?limit=50`);
      if (expectedGeneration !== generation || request !== cardRequest) return;
      const detail = snapshot.ticket;
      if (detail.ticketPublicId !== ticket.ticketPublicId) return;
      if (select.value !== 'all' && select.value !== String(detail.businessId)) return;
      card.querySelector('[data-ticket-business]').textContent = detail.businessName;
      card.querySelector('[data-ticket-id]').textContent = detail.ticketId;
      card.querySelector('[data-ticket-user]').textContent = String(detail.userId);
      card.querySelector('[data-ticket-public]').textContent = detail.ticketPublicId;
      card.querySelector('[data-ticket-status]').textContent = statusLabel(detail.status);
      history.replaceChildren();
      if (!snapshot.entries.length) {
        const empty = document.createElement('li');
        empty.className = 'list-group-item text-muted';
        empty.textContent = 'Подтверждённых записей истории пока нет.';
        history.appendChild(empty);
      }
      history.append(...snapshot.entries.map((entry) => historyItem(entry, detail.ticketPublicId)));
      currentTicketPublicId = detail.ticketPublicId;
      historyBefore = snapshot.nextBeforeId;
      older.hidden = !snapshot.hasMore;
      card.hidden = false;
    } catch (error) {
      if (expectedGeneration !== generation || request !== cardRequest) return;
      card.hidden = true;
      announce('Карточка недоступна. Проверьте доступ и обновите список.', true);
    }
  }

  function historyItem(entry, publicId) {
    const item = document.createElement('li');
    item.className = 'list-group-item';
    const sender = entry.sender || 'Неизвестный отправитель';
    const time = entry.timestamp ? new Date(entry.timestamp).toLocaleString('ru-RU') : 'Время неизвестно';
    const body = entry.message || (entry.hasAttachment ? '[Вложение]' : '[Без текста]');
    item.textContent = `${sender} · ${time}: ${body}`;
    if (entry.hasAttachment) {
      const link = document.createElement('a');
      link.href = `/api/business-scope/tickets/${encodeURIComponent(publicId)}/history/${encodeURIComponent(entry.id)}/attachment`;
      link.textContent = 'Скачать вложение';
      link.className = 'ms-2';
      item.appendChild(link);
    }
    return item;
  }

  async function loadOlderHistory() {
    if (olderLoading || !currentTicketPublicId || historyBefore === null) return;
    const expectedGeneration = generation;
    const request = cardRequest;
    const publicId = currentTicketPublicId;
    const before = historyBefore;
    olderLoading = true;
    older.disabled = true;
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(publicId)}/history?limit=50&before=${before}`);
      if (expectedGeneration !== generation || request !== cardRequest || publicId !== currentTicketPublicId) return;
      if (snapshot.ticket.ticketPublicId !== publicId ||
          (select.value !== 'all' && select.value !== String(snapshot.ticket.businessId))) return;
      history.prepend(...snapshot.entries.map((entry) => historyItem(entry, publicId)));
      historyBefore = snapshot.nextBeforeId;
      older.hidden = !snapshot.hasMore;
    } catch (error) {
      if (expectedGeneration !== generation || request !== cardRequest) return;
      card.hidden = true;
      history.replaceChildren();
      currentTicketPublicId = null;
      historyBefore = null;
      older.hidden = true;
      announce('История недоступна. Проверьте доступ и откройте карточку заново.', true);
    } finally {
      if (request === cardRequest) {
        olderLoading = false;
        older.disabled = false;
      }
    }
  }

  async function loadTickets() {
    const current = ++generation;
    ++cardRequest;
    const businessId = select.value;
    card.hidden = true;
    older.hidden = true;
    currentTicketPublicId = null;
    historyBefore = null;
    olderLoading = false;
    older.disabled = false;
    previous.disabled = true;
    next.disabled = true;
    rows.replaceChildren();
    summaryRows.replaceChildren();
    summaryStatus.textContent = 'Загрузка сводки…';
    announce('Загрузка обращений…');
    getJson(`/api/business-scope/tickets/summary?business_id=${encodeURIComponent(businessId)}`)
      .then((snapshot) => {
        if (current === generation) renderSummary(snapshot);
      }).catch(() => {
        if (current === generation) summaryStatus.textContent = 'Сводка недоступна.';
      });
    try {
      const params = new URLSearchParams({ business_id: businessId, limit: String(limit), offset: String(offset) });
      if (statusFilter.value) params.set('status', statusFilter.value);
      if (ticketFilter.value) params.set('ticket', ticketFilter.value);
      const snapshot = await getJson(`/api/business-scope/tickets/page?${params}`);
      if (current !== generation) return;
      const tickets = snapshot.tickets;
      for (const ticket of tickets) {
        const row = document.createElement('tr');
        cell(row, ticket.businessName);
        cell(row, ticket.ticketId);
        cell(row, ticket.userId);
        cell(row, statusLabel(ticket.status));
        const action = cell(row, '');
        if (ticket.ticketPublicId) {
          const button = document.createElement('button');
          button.className = 'btn btn-sm btn-outline-primary';
          button.type = 'button';
          button.textContent = 'Карточка';
          button.addEventListener('click', () => openCard(ticket, current));
          action.appendChild(button);
        } else {
          action.textContent = 'Нет публичного ID';
        }
        rows.appendChild(row);
      }
      previous.disabled = offset === 0;
      next.disabled = !snapshot.hasMore;
      pageNumber.textContent = `Страница ${Math.floor(offset / limit) + 1}`;
      announce(tickets.length ? `Показано обращений: ${tickets.length}.`
        : offset ? 'На этой странице обращений больше нет. Вернитесь назад.'
          : 'В выбранном бизнесе пока нет обращений.');
    } catch (error) {
      if (current !== generation) return;
      previous.disabled = true;
      next.disabled = true;
      announce(error.message || 'Не удалось загрузить обращения.', true);
    }
  }

  select.addEventListener('change', () => {
    offset = 0;
    loadTickets();
  });
  page.querySelector('#businessTicketFilterForm').addEventListener('submit', (event) => {
    event.preventDefault();
    offset = 0;
    loadTickets();
  });
  page.querySelector('#businessScopeReload').addEventListener('click', loadTickets);
  previous.addEventListener('click', () => {
    offset = Math.max(0, offset - limit);
    loadTickets();
  });
  next.addEventListener('click', () => {
    offset += limit;
    loadTickets();
  });
  older.addEventListener('click', loadOlderHistory);

  getJson('/api/business-scope/businesses').then((businesses) => {
    select.replaceChildren();
    const all = document.createElement('option');
    all.value = 'all';
    all.textContent = 'Все доступные бизнесы';
    select.appendChild(all);
    for (const business of businesses) {
      const option = document.createElement('option');
      option.value = String(business.id);
      option.textContent = business.displayName;
      select.appendChild(option);
    }
    loadTickets();
  }).catch((error) => announce(error.message || 'Не удалось загрузить бизнесы.', true));
})();

(() => {
  'use strict';

  const page = document.querySelector('[data-business-dialogs]');
  if (!page) return;

  const select = page.querySelector('#businessScopeSelect');
  const statusFilter = page.querySelector('#businessTicketStatusFilter');
  const ticketFilter = page.querySelector('#businessTicketIdFilter');
  const textFilter = page.querySelector('#businessTicketTextFilter');
  const status = page.querySelector('[data-business-dialogs-status]');
  const summaryStatus = page.querySelector('[data-business-summary-status]');
  const summaryRows = page.querySelector('#businessTicketSummaryRows');
  const rows = page.querySelector('#businessTicketRows');
  const card = page.querySelector('#businessTicketCard');
  const history = card.querySelector('[data-ticket-history]');
  const answers = card.querySelector('[data-ticket-answers]');
  const answersStatus = card.querySelector('[data-ticket-answers-status]');
  const workspace = card.querySelector('[data-ticket-workspace]');
  const workspaceStatus = card.querySelector('[data-ticket-workspace-status]');
  const older = card.querySelector('#businessHistoryOlder');
  const previous = page.querySelector('#businessTicketsPrevious');
  const next = page.querySelector('#businessTicketsNext');
  const pageNumber = page.querySelector('#businessTicketsPage');
  const exportButton = page.querySelector('#businessTicketsExport');
  const limit = 50;
  let offset = 0;
  let generation = 0;
  let cardRequest = 0;
  let historyBefore = null;
  let currentTicketPublicId = null;
  let currentTicket = null;
  let olderLoading = false;
  let exporting = false;

  function clearCard() {
    card.hidden = true;
    older.hidden = true;
    older.disabled = false;
    history.replaceChildren();
    answers.replaceChildren();
    answersStatus.textContent = '';
    workspace.replaceChildren();
    workspaceStatus.textContent = '';
    for (const field of ['business', 'id', 'user', 'client', 'problem', 'public', 'status', 'created']) {
      card.querySelector(`[data-ticket-${field}]`).textContent = '';
    }
    historyBefore = null;
    currentTicketPublicId = null;
    currentTicket = null;
    olderLoading = false;
  }

  function announce(message, error = false) {
    status.textContent = message;
    status.className = `alert ${error ? 'alert-danger' : 'alert-info'}`;
  }

  async function getJson(path) {
    const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store' });
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

  function ticketTime(value) {
    if (!value) return '—';
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? '—' : parsed.toLocaleString('ru-RU');
  }

  function ticketParams(businessId) {
    const params = new URLSearchParams({ business_id: businessId });
    if (statusFilter.value) params.set('status', statusFilter.value);
    if (ticketFilter.value) params.set('ticket', ticketFilter.value);
    if (textFilter.value.trim()) params.set('q', textFilter.value.trim());
    return params;
  }

  async function exportTickets() {
    if (exporting || !select.value) return;
    const current = generation;
    const businessId = select.value;
    const params = ticketParams(businessId);
    exporting = true;
    exportButton.disabled = true;
    announce('Подготовка JSON-выгрузки…');
    try {
      const response = await fetch(`/api/business-scope/tickets/export?${params}`, {
        credentials: 'same-origin', cache: 'no-store', headers: { Accept: 'application/json' }
      });
      if (!response.ok) {
        throw new Error(response.status === 413
          ? 'Найдено больше 1000 обращений. Уточните фильтры для выгрузки.'
          : response.status === 403 ? 'Нет доступа к выбранному бизнесу.'
            : 'Не удалось выгрузить обращения.');
      }
      const blob = await response.blob();
      if (current !== generation || businessId !== select.value) return;
      const url = URL.createObjectURL(blob);
      try {
        const link = document.createElement('a');
        link.href = url;
        link.download = 'business-tickets.json';
        document.body.appendChild(link);
        link.click();
        link.remove();
      } finally {
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }
      announce('JSON-выгрузка готова.');
    } catch (error) {
      if (current === generation && businessId === select.value) {
        announce(error.message || 'Не удалось выгрузить обращения.', true);
      }
    } finally {
      exporting = false;
      exportButton.disabled = false;
    }
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

  async function loadAnswers(ticket, expectedGeneration, request) {
    answers.replaceChildren();
    answersStatus.textContent = 'Загрузка ответов…';
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/answers`);
      if (expectedGeneration !== generation || request !== cardRequest ||
          currentTicketPublicId !== ticket.ticketPublicId ||
          (select.value !== 'all' && select.value !== String(ticket.businessId))) return;
      if (snapshot.ticket.ticketPublicId !== ticket.ticketPublicId ||
          snapshot.ticket.businessId !== ticket.businessId ||
          snapshot.ticket.userId !== ticket.userId ||
          snapshot.ticket.ticketId !== ticket.ticketId) {
        answersStatus.textContent = 'Ответы недоступны: обращение изменилось. Обновите карточку.';
        return;
      }
      if (!snapshot.relationVerified) {
        answersStatus.textContent = 'Ответы недоступны: legacy ID обращения не имеет однозначной связи с ними.';
        return;
      }
      if (!snapshot.answers.length) {
        answersStatus.textContent = 'Сохранённых ответов нет.';
        return;
      }
      answersStatus.textContent = `Сохранено ответов: ${snapshot.answers.length}.`;
      for (const entry of snapshot.answers) {
        const item = document.createElement('li');
        item.className = 'list-group-item';
        const question = document.createElement('strong');
        question.textContent = entry.questionText || entry.questionId;
        const value = document.createElement('span');
        value.textContent = `: ${[entry.valueLabel, entry.valueText].filter(Boolean).join(' · ') || 'Ответ не указан'}`;
        item.append(question, value);
        answers.appendChild(item);
      }
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest &&
          currentTicketPublicId === ticket.ticketPublicId) {
        answers.replaceChildren();
        answersStatus.textContent = 'Ответы недоступны. Проверьте доступ и обновите карточку.';
      }
    }
  }

  async function loadWorkspace(ticket, expectedGeneration, request) {
    workspace.replaceChildren();
    workspaceStatus.textContent = 'Загрузка рабочих данных…';
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/workspace`);
      if (expectedGeneration !== generation || request !== cardRequest ||
          currentTicketPublicId !== ticket.ticketPublicId ||
          (select.value !== 'all' && select.value !== String(ticket.businessId))) return;
      if (!snapshot.ticket || snapshot.ticket.ticketPublicId !== ticket.ticketPublicId ||
          snapshot.ticket.businessId !== ticket.businessId ||
          snapshot.ticket.userId !== ticket.userId ||
          snapshot.ticket.ticketId !== ticket.ticketId) {
        workspaceStatus.textContent = 'Рабочие данные недоступны: обращение изменилось. Обновите карточку.';
        return;
      }
      if (!snapshot.relationVerified) {
        workspaceStatus.textContent = 'Рабочие данные недоступны: legacy ID обращения не имеет однозначной связи с ними.';
        return;
      }
      workspaceStatus.textContent = 'Подтверждённые рабочие данные обращения.';
      for (const [label, value] of [
        ['Ответственный', snapshot.responsible || 'Не назначен'],
        ['Категории', snapshot.categories.length ? snapshot.categories.join(', ') : 'Нет'],
        ['Участники', snapshot.participants.length ? snapshot.participants.join(', ') : 'Нет']
      ]) {
        const item = document.createElement('li');
        item.className = 'list-group-item';
        item.textContent = `${label}: ${value}`;
        workspace.appendChild(item);
      }
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest &&
          currentTicketPublicId === ticket.ticketPublicId) {
        workspace.replaceChildren();
        workspaceStatus.textContent = 'Рабочие данные недоступны. Проверьте доступ и обновите карточку.';
      }
    }
  }

  async function openCard(ticket, expectedGeneration) {
    const request = ++cardRequest;
    clearCard();
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/history?limit=50`);
      if (expectedGeneration !== generation || request !== cardRequest) return;
      const detail = snapshot.ticket;
      if (detail.ticketPublicId !== ticket.ticketPublicId ||
          detail.businessId !== ticket.businessId || detail.userId !== ticket.userId ||
          detail.ticketId !== ticket.ticketId ||
          (select.value !== 'all' && select.value !== String(detail.businessId))) {
        throw new Error('Ticket identity changed');
      }
      card.querySelector('[data-ticket-business]').textContent = detail.businessName;
      card.querySelector('[data-ticket-id]').textContent = detail.ticketId;
      card.querySelector('[data-ticket-user]').textContent = String(detail.userId);
      card.querySelector('[data-ticket-client]').textContent = detail.clientName || 'Не указано';
      card.querySelector('[data-ticket-problem]').textContent = detail.problemPreview || 'Не указана';
      card.querySelector('[data-ticket-public]').textContent = detail.ticketPublicId;
      card.querySelector('[data-ticket-status]').textContent = statusLabel(detail.status);
      card.querySelector('[data-ticket-created]').textContent = ticketTime(detail.createdAt);
      history.replaceChildren();
      if (!snapshot.entries.length) {
        const empty = document.createElement('li');
        empty.className = 'list-group-item text-muted';
        empty.textContent = 'Подтверждённых записей истории пока нет.';
        history.appendChild(empty);
      }
      history.append(...snapshot.entries.map((entry) => historyItem(entry, detail.ticketPublicId)));
      currentTicketPublicId = detail.ticketPublicId;
      currentTicket = detail;
      historyBefore = snapshot.nextBeforeId;
      older.hidden = !snapshot.hasMore;
      card.hidden = false;
      loadAnswers(detail, expectedGeneration, request);
      loadWorkspace(detail, expectedGeneration, request);
    } catch (error) {
      if (expectedGeneration !== generation || request !== cardRequest) return;
      clearCard();
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
    if (olderLoading || !currentTicket || historyBefore === null) return;
    const expectedGeneration = generation;
    const request = cardRequest;
    const ticket = currentTicket;
    const publicId = ticket.ticketPublicId;
    const before = historyBefore;
    olderLoading = true;
    older.disabled = true;
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(publicId)}/history?limit=50&before=${before}`);
      if (expectedGeneration !== generation || request !== cardRequest || ticket !== currentTicket) return;
      if (!snapshot.ticket || snapshot.ticket.ticketPublicId !== publicId ||
          snapshot.ticket.businessId !== ticket.businessId ||
          snapshot.ticket.userId !== ticket.userId ||
          snapshot.ticket.ticketId !== ticket.ticketId ||
          (select.value !== 'all' && select.value !== String(ticket.businessId))) {
        throw new Error('Ticket identity changed');
      }
      history.prepend(...snapshot.entries.map((entry) => historyItem(entry, publicId)));
      historyBefore = snapshot.nextBeforeId;
      older.hidden = !snapshot.hasMore;
    } catch (error) {
      if (expectedGeneration !== generation || request !== cardRequest) return;
      clearCard();
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
    clearCard();
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
      const params = ticketParams(businessId);
      params.set('limit', String(limit));
      params.set('offset', String(offset));
      const snapshot = await getJson(`/api/business-scope/tickets/page?${params}`);
      if (current !== generation) return;
      const tickets = snapshot.tickets;
      for (const ticket of tickets) {
        const row = document.createElement('tr');
        cell(row, ticket.businessName);
        cell(row, ticket.ticketId);
        cell(row, ticket.clientName || `ID ${ticket.userId}`);
        const problem = cell(row, ticket.problemPreview
          ? `${ticket.problemPreview.slice(0, 120)}${ticket.problemPreview.length > 120 ? '…' : ''}` : '—');
        problem.className = 'small text-break';
        cell(row, ticketTime(ticket.createdAt));
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
  exportButton.addEventListener('click', exportTickets);

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

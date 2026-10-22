(() => {
  'use strict';

  const page = document.querySelector('[data-business-dialogs]');
  if (!page) return;

  const select = page.querySelector('#businessScopeSelect');
  const statusFilter = page.querySelector('#businessTicketStatusFilter');
  const ticketFilter = page.querySelector('#businessTicketIdFilter');
  const textFilter = page.querySelector('#businessTicketTextFilter');
  const categoryFilter = page.querySelector('#businessTicketCategoryFilter');
  const status = page.querySelector('[data-business-dialogs-status]');
  const summaryStatus = page.querySelector('[data-business-summary-status]');
  const summaryRows = page.querySelector('#businessTicketSummaryRows');
  const categoryRows = page.querySelector('#businessCategorySummaryRows');
  const categoryStatus = page.querySelector('[data-business-category-status]');
  const rows = page.querySelector('#businessTicketRows');
  const card = page.querySelector('#businessTicketCard');
  const history = card.querySelector('[data-ticket-history]');
  const answers = card.querySelector('[data-ticket-answers]');
  const answersStatus = card.querySelector('[data-ticket-answers-status]');
  const workspace = card.querySelector('[data-ticket-workspace]');
  const workspaceStatus = card.querySelector('[data-ticket-workspace-status]');
  const spans = card.querySelector('[data-ticket-spans]');
  const spansStatus = card.querySelector('[data-ticket-spans-status]');
  const older = card.querySelector('#businessHistoryOlder');
  const previous = page.querySelector('#businessTicketsPrevious');
  const next = page.querySelector('#businessTicketsNext');
  const pageNumber = page.querySelector('#businessTicketsPage');
  const exportButton = page.querySelector('#businessTicketsExport');
  const manualForm = page.querySelector('#manualBusinessTicketForm');
  const manualBusiness = manualForm?.querySelector('#manualTicketBusiness');
  const manualStatus = manualForm?.querySelector('[data-manual-ticket-status]');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  const limit = 50;
  let offset = 0;
  let generation = 0;
  let cardRequest = 0;
  let historyBefore = null;
  let currentTicketPublicId = null;
  let currentTicket = null;
  let olderLoading = false;
  let exporting = false;
  let participantTicket = null;
  let participantRevision = null;
  let participantBefore = null;
  let participantLoading = false;
  let assignmentTicket = null;
  let assignmentRevision = null;
  let assignmentLatest = null;
  let assignmentBefore = null;
  let assignmentLoading = false;
  let workTicket = null;
  let workRevision = null;
  let workLatest = null;
  let workBefore = null;
  let workLoading = false;

  function clearCard() {
    card.hidden = true;
    const ai = card.querySelector('[data-ticket-ai]');
    if (ai) { ai.hidden = true; ai.removeAttribute('href'); }
    const assignment = card.querySelector('[data-ticket-assignment]');
    if (assignment) { assignment.hidden = true; assignment.removeAttribute('href'); }
    older.hidden = true;
    older.disabled = false;
    history.replaceChildren();
    answers.replaceChildren();
    answersStatus.textContent = '';
    workspace.replaceChildren();
    workspaceStatus.textContent = '';
    spans.replaceChildren();
    spansStatus.textContent = '';
    for (const field of ['business', 'id', 'user', 'client', 'problem', 'public',
      'status', 'created', 'resolved-at', 'resolved-by', 'categories']) {
      card.querySelector(`[data-ticket-${field}]`).textContent = '';
    }
    historyBefore = null;
    currentTicketPublicId = null;
    currentTicket = null;
    olderLoading = false;
    clearParticipants();
    clearAssignmentHistory();
    clearWorkHistory();
  }

  function clearParticipants(message = '') {
    card.querySelector('[data-ticket-participants]').replaceChildren();
    card.querySelector('[data-ticket-participant-events]').replaceChildren();
    card.querySelector('[data-ticket-participants-status]').textContent = message;
    const button = card.querySelector('[data-ticket-participants-older]');
    button.hidden = true;
    button.disabled = false;
    participantTicket = null;
    participantRevision = null;
    participantBefore = null;
    participantLoading = false;
  }

  function clearAssignmentHistory(message = '') {
    card.querySelector('[data-ticket-assignment-events]').replaceChildren();
    card.querySelector('[data-ticket-assignment-status]').textContent = message;
    const button = card.querySelector('[data-ticket-assignment-older]');
    button.hidden = true;
    button.disabled = false;
    assignmentTicket = null;
    assignmentRevision = null;
    assignmentLatest = null;
    assignmentBefore = null;
    assignmentLoading = false;
  }

  function clearWorkHistory(message = '') {
    card.querySelector('[data-ticket-work-events]').replaceChildren();
    card.querySelector('[data-ticket-work-status]').textContent = message;
    const button = card.querySelector('[data-ticket-work-older]');
    button.hidden = true;
    button.disabled = false;
    workTicket = null;
    workRevision = null;
    workLatest = null;
    workBefore = null;
    workLoading = false;
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
    if (categoryFilter.value) params.set('category', categoryFilter.value);
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

  async function loadCategorySummary(businessId, expectedGeneration) {
    categoryRows.replaceChildren();
    categoryStatus.textContent = 'Загрузка категорий…';
    try {
      const response = await fetch(`/api/business-scope/tickets/categories?business_id=${encodeURIComponent(businessId)}`,
        { credentials: 'same-origin', cache: 'no-store' });
      if (!response.ok) throw new Error(response.status === 413
        ? 'Слишком много категорий для сводки. Выберите отдельный бизнес.' : 'Сводка категорий недоступна.');
      const counts = await response.json();
      if (expectedGeneration !== generation || businessId !== select.value) return;
      categoryStatus.textContent = counts.length
        ? 'Число обращений по категориям. Одно обращение может входить в несколько категорий. Сводка не зависит от фильтров списка.'
        : 'В выбранном контексте категории пока не заданы.';
      for (const count of counts) {
        const row = document.createElement('tr');
        cell(row, count.businessName);
        const name = cell(row, count.category);
        name.className = 'text-break';
        cell(row, count.total);
        categoryRows.appendChild(row);
      }
    } catch (error) {
      if (expectedGeneration === generation && businessId === select.value) {
        categoryRows.replaceChildren();
        categoryStatus.textContent = error.message || 'Сводка категорий недоступна.';
      }
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
      workspaceStatus.textContent = 'Рабочие данные по уникальному raw ID; историческая принадлежность legacy-связей не подтверждена.';
      for (const [label, value] of [
        ['Ответственный (прежние данные)', snapshot.responsible || 'Не назначен']
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

  function participantOwnerMatches(owner, ticket) {
    return owner && owner.ticketPublicId === ticket.ticketPublicId && owner.ticketId === ticket.ticketId &&
      owner.userId === String(ticket.userId) && owner.businessId === String(ticket.businessId);
  }

  function participantEventItem(entry) {
    const item = document.createElement('li');
    item.className = 'list-group-item text-break';
    const action = entry.action === 'add' ? 'Добавлен' : 'Удалён';
    item.textContent = `${entry.revision} · ${action}: ${entry.member} (ID ${entry.memberId}) · ${entry.actor} · ${ticketTime(entry.createdAt)}`;
    return item;
  }

  async function loadParticipants(ticket, expectedGeneration, request) {
    clearParticipants('Загрузка участников…');
    try {
      const path = `/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/participants`;
      const [roster, events] = await Promise.all([getJson(path), getJson(`${path}/history?limit=50`)]);
      if (expectedGeneration !== generation || request !== cardRequest || currentTicketPublicId !== ticket.ticketPublicId) return;
      if (!participantOwnerMatches(roster.owner, ticket) || !participantOwnerMatches(events.owner, ticket) ||
          typeof roster.revision !== 'string' || roster.revision !== events.revision) {
        clearParticipants('Список участников изменился. Обновите карточку.');
        return;
      }
      const list = card.querySelector('[data-ticket-participants]');
      for (const member of roster.members) {
        const item = document.createElement('li');
        item.className = 'list-group-item text-break';
        item.textContent = `${member.username || member.snapshot} (ID ${member.id})${member.eligible ? '' : ' · нет текущего доступа'}`;
        list.appendChild(item);
      }
      card.querySelector('[data-ticket-participants-status]').textContent = roster.members.length
        ? `Участников: ${roster.members.length}. Участие не предоставляет доступ к бизнесу.` : 'Участники пока не добавлены.';
      const history = card.querySelector('[data-ticket-participant-events]');
      history.append(...events.events.map(participantEventItem));
      participantTicket = ticket;
      participantRevision = roster.revision;
      participantBefore = events.nextBeforeRevision;
      card.querySelector('[data-ticket-participants-older]').hidden = !events.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && currentTicketPublicId === ticket.ticketPublicId)
        clearParticipants('Участники недоступны. Проверьте доступ и обновите карточку.');
    }
  }

  async function loadOlderParticipants() {
    if (participantLoading || !participantTicket || participantBefore === null) return;
    const ticket = participantTicket, expectedGeneration = generation, request = cardRequest;
    const revision = participantRevision, before = participantBefore;
    const button = card.querySelector('[data-ticket-participants-older]');
    participantLoading = true;
    button.disabled = true;
    try {
      const page = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/participants/history?limit=50&before=${encodeURIComponent(before)}`);
      if (expectedGeneration !== generation || request !== cardRequest || participantTicket !== ticket) return;
      if (!participantOwnerMatches(page.owner, ticket) || page.revision !== revision) {
        clearParticipants('Список участников изменился. Обновите карточку.');
        return;
      }
      card.querySelector('[data-ticket-participant-events]').append(...page.events.map(participantEventItem));
      participantBefore = page.nextBeforeRevision;
      button.hidden = !page.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && participantTicket === ticket)
        clearParticipants('История участников недоступна. Обновите карточку.');
    } finally {
      if (expectedGeneration === generation && request === cardRequest) { participantLoading = false; button.disabled = false; }
    }
  }

  function assignmentOwnerMatches(owner, ticket) {
    return owner && owner.ticketPublicId === ticket.ticketPublicId && owner.ticketId === ticket.ticketId &&
      owner.userId === String(ticket.userId) && owner.businessId === String(ticket.businessId);
  }

  function assignmentEventItem(event) {
    const item = document.createElement('li');
    item.className = 'list-group-item text-break';
    const origin = event.origin === 'SLA' ? 'SLA' : 'Вручную';
    const action = event.action === 'take' ? 'взял в работу' : 'назначил';
    item.textContent = `${ticketTime(event.createdAt)} · ${origin} · ревизия ${event.revision} · ` +
      `${event.actor}: ${action} → ${event.assignee}`;
    return item;
  }

  async function loadAssignmentHistory(ticket, expectedGeneration, request) {
    clearAssignmentHistory('Загрузка истории назначений…');
    try {
      const page = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/assignments/history?limit=50`);
      if (expectedGeneration !== generation || request !== cardRequest || currentTicketPublicId !== ticket.ticketPublicId) return;
      if (!assignmentOwnerMatches(page.owner, ticket) || typeof page.revision !== 'string' || typeof page.latestEventRevision !== 'string') {
        clearAssignmentHistory('История назначений изменилась. Обновите карточку.');
        return;
      }
      card.querySelector('[data-ticket-assignment-events]').append(...page.events.map(assignmentEventItem));
      card.querySelector('[data-ticket-assignment-status]').textContent = page.events.length
        ? 'Ручные и SLA-назначения. События показывают историю, а не текущего ответственного.'
        : 'Сохранённых назначений в этом бизнесе пока нет.';
      assignmentTicket = ticket;
      assignmentRevision = page.revision;
      assignmentLatest = page.latestEventRevision;
      assignmentBefore = page.nextBeforeRevision;
      card.querySelector('[data-ticket-assignment-older]').hidden = !page.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && currentTicketPublicId === ticket.ticketPublicId)
        clearAssignmentHistory('История назначений недоступна. Проверьте доступ и обновите карточку.');
    }
  }

  async function loadOlderAssignments() {
    if (assignmentLoading || !assignmentTicket || assignmentBefore === null) return;
    const ticket = assignmentTicket, expectedGeneration = generation, request = cardRequest;
    const revision = assignmentRevision, latest = assignmentLatest, before = assignmentBefore;
    const button = card.querySelector('[data-ticket-assignment-older]');
    assignmentLoading = true;
    button.disabled = true;
    try {
      const page = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/assignments/history?limit=50&before=${encodeURIComponent(before)}`);
      if (expectedGeneration !== generation || request !== cardRequest || assignmentTicket !== ticket) return;
      if (!assignmentOwnerMatches(page.owner, ticket) || page.revision !== revision || page.latestEventRevision !== latest) {
        clearAssignmentHistory('История назначений изменилась. Обновите карточку.');
        return;
      }
      card.querySelector('[data-ticket-assignment-events]').append(...page.events.map(assignmentEventItem));
      assignmentBefore = page.nextBeforeRevision;
      button.hidden = !page.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && assignmentTicket === ticket)
        clearAssignmentHistory('История назначений недоступна. Обновите карточку.');
    } finally {
      if (expectedGeneration === generation && request === cardRequest) { assignmentLoading = false; button.disabled = false; }
    }
  }

  function workOwnerMatches(owner, ticket) {
    return owner && owner.ticketPublicId === ticket.ticketPublicId && owner.ticketId === ticket.ticketId &&
      owner.userId === String(ticket.userId) && owner.businessId === String(ticket.businessId);
  }

  function workEventItem(event) {
    const item = document.createElement('li');
    item.className = 'list-group-item text-break';
    const action = ({ close: 'Закрыл', reopen: 'Открыл повторно', categories: 'Изменил категории' })[event.action] || event.action;
    item.textContent = `${ticketTime(event.createdAt)} · ревизия ${event.revision} · ${event.actor}: ${action}; ` +
      `${statusLabel(event.fromStatus)} → ${statusLabel(event.toStatus)}; категории: ` +
      `${event.previousCategories.join(', ') || 'нет'} → ${event.categories.join(', ') || 'нет'}`;
    return item;
  }

  async function loadWorkHistory(ticket, expectedGeneration, request) {
    clearWorkHistory('Загрузка истории статуса и категорий…');
    try {
      const page = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/work/history?limit=50`);
      if (expectedGeneration !== generation || request !== cardRequest || currentTicketPublicId !== ticket.ticketPublicId) return;
      if (!workOwnerMatches(page.owner, ticket) || typeof page.revision !== 'string' || typeof page.latestEventRevision !== 'string') {
        clearWorkHistory('История статуса и категорий изменилась. Обновите карточку.');
        return;
      }
      card.querySelector('[data-ticket-work-events]').append(...page.events.map(workEventItem));
      card.querySelector('[data-ticket-work-status]').textContent = page.events.length
        ? 'Закрытия, повторные открытия и изменения категорий в этом бизнесе.'
        : 'Сохранённых изменений статуса и категорий в этом бизнесе пока нет.';
      workTicket = ticket;
      workRevision = page.revision;
      workLatest = page.latestEventRevision;
      workBefore = page.nextBeforeRevision;
      card.querySelector('[data-ticket-work-older]').hidden = !page.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && currentTicketPublicId === ticket.ticketPublicId)
        clearWorkHistory('История статуса и категорий недоступна. Проверьте доступ и обновите карточку.');
    }
  }

  async function loadOlderWorkEvents() {
    if (workLoading || !workTicket || workBefore === null) return;
    const ticket = workTicket, expectedGeneration = generation, request = cardRequest;
    const revision = workRevision, latest = workLatest, before = workBefore;
    const button = card.querySelector('[data-ticket-work-older]');
    workLoading = true;
    button.disabled = true;
    try {
      const page = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/work/history?limit=50&before=${encodeURIComponent(before)}`);
      if (expectedGeneration !== generation || request !== cardRequest || workTicket !== ticket) return;
      if (!workOwnerMatches(page.owner, ticket) || page.revision !== revision || page.latestEventRevision !== latest) {
        clearWorkHistory('История статуса и категорий изменилась. Обновите карточку.');
        return;
      }
      card.querySelector('[data-ticket-work-events]').append(...page.events.map(workEventItem));
      workBefore = page.nextBeforeRevision;
      button.hidden = !page.hasMore;
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest && workTicket === ticket)
        clearWorkHistory('История статуса и категорий недоступна. Обновите карточку.');
    } finally {
      if (expectedGeneration === generation && request === cardRequest) { workLoading = false; button.disabled = false; }
    }
  }

  async function loadSpans(ticket, expectedGeneration, request) {
    spans.replaceChildren();
    spansStatus.textContent = 'Загрузка интервалов работы…';
    try {
      const snapshot = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}/spans`);
      if (expectedGeneration !== generation || request !== cardRequest ||
          currentTicketPublicId !== ticket.ticketPublicId ||
          (select.value !== 'all' && select.value !== String(ticket.businessId))) return;
      if (!snapshot.ticket || snapshot.ticket.ticketPublicId !== ticket.ticketPublicId ||
          snapshot.ticket.businessId !== ticket.businessId ||
          snapshot.ticket.userId !== ticket.userId ||
          snapshot.ticket.ticketId !== ticket.ticketId) {
        spansStatus.textContent = 'Интервалы недоступны: обращение изменилось. Обновите карточку.';
        return;
      }
      if (!snapshot.relationVerified) {
        spansStatus.textContent = 'Интервалы недоступны: legacy ID обращения не имеет однозначной связи с ними.';
        return;
      }
      spansStatus.textContent = snapshot.spans.length
        ? 'Подтверждённые интервалы работы обращения.' : 'Интервалов работы пока нет.';
      for (const entry of snapshot.spans) {
        const item = document.createElement('li');
        item.className = 'list-group-item';
        const duration = Number.isInteger(entry.durationSeconds) && entry.durationSeconds >= 0
          ? `${entry.durationSeconds} с` : 'не зафиксирована';
        item.textContent = `Интервал ${entry.spanNo}: ${ticketTime(entry.startedAt)} — ` +
          `${ticketTime(entry.endedAt)}; длительность ${duration}`;
        spans.appendChild(item);
      }
    } catch (error) {
      if (expectedGeneration === generation && request === cardRequest &&
          currentTicketPublicId === ticket.ticketPublicId) {
        spans.replaceChildren();
        spansStatus.textContent = 'Интервалы недоступны. Проверьте доступ и обновите карточку.';
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
      const ai = card.querySelector('[data-ticket-ai]');
      if (ai) {
        ai.href = '/business-dialog-ai/' + encodeURIComponent(detail.ticketPublicId);
        ai.hidden = false;
      }
      const assignment = card.querySelector('[data-ticket-assignment]');
      if (assignment) {
        assignment.href = '/business-dialog-assignment/' + encodeURIComponent(detail.ticketPublicId);
        assignment.hidden = false;
      }
      card.querySelector('[data-ticket-status]').textContent = statusLabel(detail.status);
      card.querySelector('[data-ticket-categories]').textContent = detail.categories?.join(', ') || 'Нет';
      card.querySelector('[data-ticket-created]').textContent = ticketTime(detail.createdAt);
      card.querySelector('[data-ticket-resolved-at]').textContent = ticketTime(detail.resolvedAt);
      card.querySelector('[data-ticket-resolved-by]').textContent = detail.resolvedBy || '—';
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
      loadParticipants(detail, expectedGeneration, request);
      loadAssignmentHistory(detail, expectedGeneration, request);
      loadWorkHistory(detail, expectedGeneration, request);
      loadSpans(detail, expectedGeneration, request);
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
    loadCategorySummary(businessId, current);
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
        const categories = cell(row, ticket.categories?.join(', ') || 'Нет');
        categories.className = 'small text-break';
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
  card.querySelector('[data-ticket-participants-older]').addEventListener('click', loadOlderParticipants);
  card.querySelector('[data-ticket-assignment-older]').addEventListener('click', loadOlderAssignments);
  card.querySelector('[data-ticket-work-older]').addEventListener('click', loadOlderWorkEvents);
  exportButton.addEventListener('click', exportTickets);

  if (manualForm) {
    manualForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!manualBusiness.value || !manualForm.reportValidity()) return;
      const submit = manualForm.querySelector('button[type="submit"]');
      submit.disabled = true;
      manualStatus.textContent = 'Создание обращения…';
      try {
        const response = await fetch('/api/business-scope/tickets/manual', {
          method: 'POST', credentials: 'same-origin', cache: 'no-store',
          headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
          body: JSON.stringify({
            businessId: Number(manualBusiness.value),
            clientName: manualForm.querySelector('#manualTicketClient').value,
            problem: manualForm.querySelector('#manualTicketProblem').value
          })
        });
        if (!response.ok) {
          throw new Error(response.status === 403 ? 'Нет права создавать обращения в этом бизнесе.'
            : 'Не удалось создать обращение. Проверьте данные и доступ.');
        }
        const created = await response.json();
        manualForm.querySelector('#manualTicketClient').value = '';
        manualForm.querySelector('#manualTicketProblem').value = '';
        manualStatus.textContent = `Обращение ${created.ticketId} создано. Локальный ID клиента: ${created.clientUserId}.`;
        select.value = String(created.businessId);
        offset = 0;
        await loadTickets();
      } catch (error) {
        manualStatus.textContent = error.message || 'Не удалось создать обращение.';
      } finally {
        submit.disabled = false;
      }
    });
  }

  getJson('/api/business-scope/businesses').then((businesses) => {
    select.replaceChildren();
    if (manualBusiness) {
      manualBusiness.replaceChildren();
      const prompt = document.createElement('option');
      prompt.value = '';
      prompt.textContent = 'Выберите бизнес';
      manualBusiness.appendChild(prompt);
    }
    const all = document.createElement('option');
    all.value = 'all';
    all.textContent = 'Все доступные бизнесы';
    select.appendChild(all);
    for (const business of businesses) {
      const option = document.createElement('option');
      option.value = String(business.id);
      option.textContent = business.displayName;
      select.appendChild(option);
      if (manualBusiness) {
        manualBusiness.appendChild(option.cloneNode(true));
      }
    }
    if (manualBusiness) manualBusiness.value = '';
    loadTickets();
  }).catch((error) => announce(error.message || 'Не удалось загрузить бизнесы.', true));
})();

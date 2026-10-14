(() => {
  'use strict';

  const page = document.querySelector('[data-business-dialogs]');
  if (!page) return;

  const select = page.querySelector('#businessScopeSelect');
  const status = page.querySelector('[data-business-dialogs-status]');
  const rows = page.querySelector('#businessTicketRows');
  const card = page.querySelector('#businessTicketCard');
  const previous = page.querySelector('#businessTicketsPrevious');
  const next = page.querySelector('#businessTicketsNext');
  const pageNumber = page.querySelector('#businessTicketsPage');
  const limit = 50;
  let offset = 0;
  let generation = 0;

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

  async function openCard(ticket, expectedGeneration) {
    try {
      const detail = await getJson(`/api/business-scope/tickets/${encodeURIComponent(ticket.ticketPublicId)}`);
      if (expectedGeneration !== generation) return;
      if (select.value !== 'all' && select.value !== String(detail.businessId)) return;
      card.querySelector('[data-ticket-business]').textContent = detail.businessName;
      card.querySelector('[data-ticket-id]').textContent = detail.ticketId;
      card.querySelector('[data-ticket-user]').textContent = String(detail.userId);
      card.querySelector('[data-ticket-public]').textContent = detail.ticketPublicId;
      card.hidden = false;
    } catch (error) {
      if (expectedGeneration !== generation) return;
      card.hidden = true;
      announce('Карточка недоступна. Проверьте доступ и обновите список.', true);
    }
  }

  async function loadTickets() {
    const current = ++generation;
    const businessId = select.value;
    card.hidden = true;
    rows.replaceChildren();
    announce('Загрузка обращений…');
    try {
      const tickets = await getJson(`/api/business-scope/tickets?business_id=${encodeURIComponent(businessId)}&limit=${limit}&offset=${offset}`);
      if (current !== generation) return;
      for (const ticket of tickets) {
        const row = document.createElement('tr');
        cell(row, ticket.businessName);
        cell(row, ticket.ticketId);
        cell(row, ticket.userId);
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
      next.disabled = tickets.length < limit;
      pageNumber.textContent = `Страница ${Math.floor(offset / limit) + 1}`;
      announce(tickets.length ? `Показано обращений: ${tickets.length}.`
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
  page.querySelector('#businessScopeReload').addEventListener('click', loadTickets);
  previous.addEventListener('click', () => {
    offset = Math.max(0, offset - limit);
    loadTickets();
  });
  next.addEventListener('click', () => {
    offset += limit;
    loadTickets();
  });

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

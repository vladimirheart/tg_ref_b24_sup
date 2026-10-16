(() => {
  'use strict';
  const page = document.querySelector('[data-general-access]');
  if (!page) return;
  const rows = page.querySelector('#generalAccessRows');
  const status = page.querySelector('[data-general-access-status]');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  let busy = false;

  async function load() {
    rows.replaceChildren();
    const response = await fetch('/api/general-ticket-admin/access', { credentials: 'same-origin', cache: 'no-store' });
    if (!response.ok) throw new Error('Не удалось загрузить права доступа.');
    for (const account of await response.json()) {
      const row = document.createElement('tr');
      for (const text of [account.username, account.canRead ? 'Разрешён' : 'Не разрешён']) {
        const cell = document.createElement('td');
        cell.textContent = text;
        row.appendChild(cell);
      }
      const cell = document.createElement('td');
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-outline-primary btn-sm';
      button.textContent = account.canRead ? 'Отозвать просмотр' : 'Разрешить просмотр';
      button.addEventListener('click', () => save(account));
      cell.appendChild(button);
      row.appendChild(cell);
      rows.appendChild(row);
    }
    status.textContent = 'Права доступа загружены.';
    status.className = 'alert alert-info';
  }

  async function save(account) {
    if (busy || !csrf || !Number.isSafeInteger(account.userId) || account.userId <= 0) return;
    busy = true;
    for (const button of rows.querySelectorAll('button')) button.disabled = true;
    try {
      const response = await fetch(`/api/general-ticket-admin/access/${account.userId}`, {
        method: account.canRead ? 'DELETE' : 'PUT', credentials: 'same-origin', headers: { 'X-CSRF-TOKEN': csrf }
      });
      if (!response.ok) throw new Error('Не удалось изменить право доступа.');
      await load();
    } catch (error) {
      rows.replaceChildren();
      status.textContent = 'Не удалось изменить право доступа. Обновите страницу.';
      status.className = 'alert alert-danger';
    } finally { busy = false; }
  }
  load().catch(() => { rows.replaceChildren(); status.textContent = 'Не удалось загрузить права доступа.'; status.className = 'alert alert-danger'; });
})();

(() => {
  'use strict';

  const page = document.querySelector('[data-business-admin]');
  if (!page) return;

  const status = page.querySelector('[data-business-status]');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  let snapshot = null;

  function announce(message, error = false) {
    status.textContent = message;
    status.className = `alert ${error ? 'alert-danger' : 'alert-info'}`;
  }

  async function request(path, method = 'GET', body) {
    const response = await fetch(`/api/business-admin${path}`, {
      method,
      credentials: 'same-origin',
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(method !== 'GET' ? { 'X-XSRF-TOKEN': csrf } : {})
      },
      ...(body ? { body: JSON.stringify(body) } : {})
    });
    if (!response.ok) {
      const message = response.status === 403 ? 'Недостаточно прав для управления бизнесами.'
        : response.status === 409 ? 'Такая запись уже существует.'
          : response.status === 404 ? 'Бизнес или сотрудник не найден.'
            : response.status === 400 ? 'Проверьте введённые данные.'
              : 'Не удалось выполнить операцию.';
      throw new Error(message);
    }
    return response.status === 204 ? null : response.json();
  }

  function cell(row, value) {
    const item = document.createElement('td');
    item.textContent = String(value ?? '');
    row.appendChild(item);
    return item;
  }

  function fillSelect(id, items, label) {
    const select = page.querySelector(`#${id}`);
    const previous = select.value;
    select.replaceChildren();
    for (const item of items) {
      const option = document.createElement('option');
      option.value = String(item.id);
      option.textContent = label(item);
      select.appendChild(option);
    }
    if (items.some((item) => String(item.id) === previous)) select.value = previous;
  }

  function revokeButton(onClick) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'btn btn-sm btn-outline-danger';
    button.textContent = 'Отозвать';
    button.addEventListener('click', onClick);
    return button;
  }

  function render(data) {
    snapshot = data;
    const users = new Map(data.users.map((user) => [user.id, user.username]));
    const businesses = new Map(data.businesses.map((business) => [business.id, business.displayName]));
    fillSelect('membershipBusiness', data.businesses.filter((business) => business.status === 'active'),
      (business) => business.displayName);
    fillSelect('locationBusiness', data.businesses.filter((business) => business.status === 'active'),
      (business) => business.displayName);
    fillSelect('membershipUser', data.users, (user) => user.username);
    fillSelect('allGrantUser', data.users, (user) => user.username);

    const catalog = page.querySelector('#businessCatalogRows');
    catalog.replaceChildren();
    for (const business of data.businesses) {
      const row = document.createElement('tr');
      cell(row, business.displayName);
      cell(row, business.code);
      cell(row, business.status === 'active' ? 'Активен' : 'Архив');
      catalog.appendChild(row);
    }

    const locationRows = page.querySelector('#businessLocationRows');
    locationRows.replaceChildren();
    for (const location of data.locations) {
      const row = document.createElement('tr');
      cell(row, businesses.get(location.businessId) || location.businessId);
      cell(row, location.locationName);
      cell(row, location.locationType);
      cell(row, location.city);
      cell(row, location.status === 'active' ? 'Активна' : 'Архив');
      const action = cell(row, '');
      const business = data.businesses.find((item) => item.id === location.businessId);
      if (business?.status === 'active') {
        const active = location.status === 'active';
        const button = document.createElement('button');
        button.type = 'button';
        button.className = `btn btn-sm ${active ? 'btn-outline-danger' : 'btn-outline-primary'}`;
        button.textContent = active ? 'Архивировать' : 'Восстановить';
        button.addEventListener('click', async () => {
          if (active && !window.confirm('Архивировать эту локацию? Её идентификатор и связи сохранятся.')) return;
          await perform(async () => {
            await request(`/businesses/${location.businessId}/locations/${location.id}/status`,
              'PUT', { status: active ? 'archived' : 'active' });
          }, active ? 'Локация архивирована.' : 'Локация восстановлена.');
        });
        action.appendChild(button);
      }
      locationRows.appendChild(row);
    }

    const membershipRows = page.querySelector('#businessMembershipRows');
    membershipRows.replaceChildren();
    for (const grant of data.memberships) {
      const row = document.createElement('tr');
      cell(row, businesses.get(grant.businessId) || grant.businessId);
      cell(row, users.get(grant.userId) || grant.userId);
      cell(row, grant.roleCode);
      cell(row, '').appendChild(revokeButton(async () => {
        if (!window.confirm('Отозвать доступ сотрудника к выбранному бизнесу?')) return;
        await perform(async () => {
          await request(`/businesses/${grant.businessId}/memberships/${grant.userId}`, 'DELETE');
        }, 'Доступ к бизнесу отозван.');
      }));
      membershipRows.appendChild(row);
    }

    const allRows = page.querySelector('#allBusinessGrantRows');
    allRows.replaceChildren();
    for (const grant of data.allGrants) {
      const row = document.createElement('tr');
      cell(row, users.get(grant.userId) || grant.userId);
      cell(row, grant.roleCode);
      cell(row, '').appendChild(revokeButton(async () => {
        if (!window.confirm('Отозвать доступ сотрудника ко всем бизнесам?')) return;
        await perform(async () => {
          await request(`/all-grants/${grant.userId}`, 'DELETE');
        }, 'Доступ ко всем бизнесам отозван.');
      }));
      allRows.appendChild(row);
    }
  }

  async function refresh() {
    render(await request('/overview'));
  }

  async function perform(action, success) {
    try {
      await action();
      await refresh();
      announce(success);
    } catch (error) {
      announce(error.message || 'Не удалось выполнить операцию.', true);
    }
  }

  page.querySelector('#businessCreateForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    perform(async () => {
      await request('/businesses', 'POST', data);
      form.reset();
    }, 'Бизнес создан. Теперь можно назначить сотрудникам доступ.');
  });

  page.querySelector('#businessMembershipForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const businessId = page.querySelector('#membershipBusiness').value;
    const userId = page.querySelector('#membershipUser').value;
    const roleCode = page.querySelector('#membershipRole').value;
    perform(async () => {
      await request(`/businesses/${businessId}/memberships/${userId}`, 'PUT', { roleCode });
    }, 'Доступ к бизнесу сохранён.');
  });

  page.querySelector('#businessLocationForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const businessId = page.querySelector('#locationBusiness').value;
    const data = Object.fromEntries(new FormData(form));
    perform(async () => {
      await request(`/businesses/${businessId}/locations`, 'POST', data);
      form.reset();
      page.querySelector('#locationBusiness').value = businessId;
    }, 'Локация добавлена только в выбранный бизнес.');
  });

  page.querySelector('#allBusinessGrantForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const userId = page.querySelector('#allGrantUser').value;
    const roleCode = page.querySelector('#allGrantRole').value;
    perform(async () => {
      await request(`/all-grants/${userId}`, 'PUT', { roleCode });
    }, 'Доступ ко всем бизнесам сохранён.');
  });

  refresh().then(() => announce(snapshot.businesses.length
    ? 'Каталог и права загружены.' : 'Бизнесов пока нет.'))
    .catch((error) => announce(error.message || 'Не удалось загрузить каталог.', true));
})();

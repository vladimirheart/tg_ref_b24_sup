(() => {
  'use strict';

  const page = document.querySelector('[data-business-admin]');
  if (!page) return;

  const status = page.querySelector('[data-business-status]');
  const csrf = document.querySelector('meta[name="_csrf"]')?.content || '';
  let snapshot = null;
  let channelCatalog = [];
  let previewRequestId = 0;

  function announce(message, error = false) {
    status.textContent = message;
    status.className = `business-admin__status small ${error ? 'alert alert-danger' : 'text-muted'}`;
  }

  const roleLabels = { VIEWER: 'Чтение', OPERATOR: 'Оператор', MANAGER: 'Менеджер', ADMIN: 'Администратор бизнеса' };
  const lists = {
    catalog: { rows: 'businessCatalogRows', columns: 4, empty: 'Бизнесов пока нет.' },
    memberships: { rows: 'businessMembershipRows', columns: 3, owner: 'membershipBusiness', empty: 'Отдельные доступы к этому бизнесу не назначены. Общие доступы указаны ниже.' },
    locations: { rows: 'businessLocationRows', columns: 5, owner: 'locationBusiness', empty: 'У этого бизнеса пока нет локаций.' },
    channels: { rows: 'businessChannelRows', columns: 4, empty: 'Каналов пока нет.' }
  };

  function count(name, value) {
    page.querySelectorAll(`[data-business-count="${name}"]`).forEach(item => { item.textContent = String(value); });
  }

  function filterRows(kind) {
    const config = lists[kind];
    const body = page.querySelector(`#${config.rows}`);
    body.querySelector('[data-list-empty]')?.remove();
    const query = page.querySelector(`[data-business-search="${kind}"]`).value.trim().toLocaleLowerCase('ru');
    const owner = config.owner ? page.querySelector(`#${config.owner}`).value : null;
    let visible = 0;
    for (const row of body.children) {
      row.hidden = (owner !== null && row.dataset.businessOwner !== owner)
        || !row.textContent.toLocaleLowerCase('ru').includes(query);
      if (!row.hidden) visible++;
    }
    if (!visible) {
      const row = document.createElement('tr');
      row.dataset.listEmpty = '';
      const empty = cell(row, query ? 'По вашему запросу ничего не найдено.'
        : owner === '' ? 'Сначала создайте бизнес в каталоге.' : config.empty);
      empty.colSpan = config.columns;
      empty.className = 'text-muted text-center py-4';
      body.appendChild(row);
    }
    if (config.owner) {
      const form = page.querySelector(config.owner === 'membershipBusiness' ? '#businessMembershipForm' : '#businessLocationForm');
      const business = snapshot?.businesses.find(item => String(item.id) === owner);
      form.querySelectorAll('input[name], select, button[type="submit"]').forEach(control => {
        control.disabled = business?.status !== 'active' || (kind === 'memberships' && !snapshot.users.length);
      });
    }
  }

  for (const [kind, config] of Object.entries(lists)) {
    page.querySelector(`[data-business-search="${kind}"]`).addEventListener('input', () => filterRows(kind));
    if (config.owner) page.querySelector(`#${config.owner}`).addEventListener('change', () => filterRows(kind));
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
        : response.status === 409 ? (path.startsWith('/channels/')
          ? 'Конфликт привязок: отключите несовместимый активный режим.'
          : 'Такая запись уже существует.')
          : response.status === 404 ? 'Бизнес, канал или сотрудник не найден.'
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

  function catalogMenu(actions, name) {
    const wrapper = document.createElement('div');
    wrapper.className = 'dropdown';
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'btn btn-sm business-admin__menu-toggle';
    toggle.dataset.bsToggle = 'dropdown';
    toggle.setAttribute('aria-label', `Действия бизнеса ${name}`);
    toggle.setAttribute('aria-expanded', 'false');
    toggle.title = 'Действия';
    const icon = document.createElement('i');
    icon.className = 'bi bi-three-dots';
    icon.setAttribute('aria-hidden', 'true');
    toggle.appendChild(icon);
    const menu = document.createElement('div');
    menu.className = 'dropdown-menu dropdown-menu-end';
    wrapper.append(toggle, menu);
    actions.appendChild(wrapper);
    if (window.bootstrap?.Dropdown) new window.bootstrap.Dropdown(toggle, {
      popperConfig: config => ({ ...config, strategy: 'fixed' })
    });
    return menu;
  }

  function render(data) {
    snapshot = data;
    const users = new Map(data.users.map((user) => [user.id, user.username]));
    const scopedBusinesses = [...data.businesses.filter(business => business.status === 'active'),
      ...data.businesses.filter(business => business.status !== 'active')];
    fillSelect('membershipBusiness', scopedBusinesses,
      (business) => business.displayName + (business.status === 'active' ? '' : ' · Архив'));
    fillSelect('locationBusiness', scopedBusinesses,
      (business) => business.displayName + (business.status === 'active' ? '' : ' · Архив'));
    fillSelect('channelBindingBusiness', data.businesses.filter((business) => business.status === 'active'),
      (business) => business.displayName);
    fillSelect('membershipUser', data.users, (user) => user.username);
    fillSelect('allGrantUser', data.users, (user) => user.username);

    const catalog = page.querySelector('#businessCatalogRows');
    catalog.querySelectorAll('[data-bs-toggle="dropdown"]').forEach(toggle => window.bootstrap?.Dropdown.getInstance(toggle)?.dispose());
    catalog.replaceChildren();
    for (const business of data.businesses) {
      const row = document.createElement('tr');
      cell(row, business.displayName);
      cell(row, business.code);
      cell(row, business.status === 'active' ? 'Активен' : 'Архив');
      const actions = catalogMenu(cell(row, ''), business.displayName);
      const rename = document.createElement('button');
      rename.type = 'button';
      rename.className = 'dropdown-item';
      rename.textContent = 'Переименовать';
      rename.setAttribute('aria-label', `Переименовать бизнес ${business.displayName}`);
      rename.addEventListener('click', async () => {
        const name = window.prompt('Новое название бизнеса:', business.displayName);
        if (name === null || name.trim() === business.displayName) return;
        await perform(async () => {
          await request(`/businesses/${business.id}`, 'PUT', { displayName: name });
        }, 'Название бизнеса обновлено. Код и связи сохранены.');
      });
      actions.appendChild(rename);
      const active = business.status === 'active';
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = `dropdown-item${active ? ' text-danger' : ''}`;
      toggle.textContent = active ? 'Архивировать' : 'Восстановить';
      toggle.setAttribute('aria-label', `${toggle.textContent} бизнес ${business.displayName}`);
      toggle.addEventListener('click', async () => {
        if (active && !window.confirm(`Архивировать бизнес «${business.displayName}»? Данные и права сохранятся, но бизнес исчезнет из доступных списков до восстановления.`)) return;
        await perform(async () => {
          await request(`/businesses/${business.id}/status`, 'PUT',
            { status: active ? 'archived' : 'active' });
        }, active ? 'Бизнес архивирован. Данные и права сохранены.' : 'Бизнес восстановлен.');
      });
      actions.appendChild(toggle);
      catalog.appendChild(row);
    }

    const locationRows = page.querySelector('#businessLocationRows');
    locationRows.replaceChildren();
    for (const location of data.locations) {
      const row = document.createElement('tr');
      row.dataset.businessOwner = String(location.businessId);
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
      row.dataset.businessOwner = String(grant.businessId);
      cell(row, users.get(grant.userId) || grant.userId);
      cell(row, roleLabels[grant.roleCode] || grant.roleCode);
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
      cell(row, roleLabels[grant.roleCode] || grant.roleCode);
      cell(row, '').appendChild(revokeButton(async () => {
        if (!window.confirm('Отозвать доступ сотрудника ко всем бизнесам?')) return;
        await perform(async () => {
          await request(`/all-grants/${grant.userId}`, 'DELETE');
        }, 'Доступ ко всем бизнесам отозван.');
      }));
      allRows.appendChild(row);
    }
    if (!data.allGrants.length) {
      const row = document.createElement('tr');
      cell(row, 'Общие доступы не назначены.').colSpan = 3;
      allRows.appendChild(row);
    }
    count('businesses', data.businesses.length);
    count('memberships', data.memberships.length + data.allGrants.length);
    count('locations', data.locations.length);
    count('allGrants', data.allGrants.length);
    ['catalog', 'memberships', 'locations'].forEach(filterRows);
  }

  async function refresh() {
    render(await request('/overview'));
  }

  const classificationLabels = {
    CHANNEL_INACTIVE: 'Канал выключен',
    NO_ACTIVE_BINDING: 'Нет активных привязок',
    SINGLE_BUSINESS: 'Одна активная привязка к бизнесу',
    EXPLICIT_SELECTION: 'Настроен явный выбор бизнеса',
    NEEDS_EXPLICIT_DECISION: 'Требуется явное решение по маршрутизации'
  };
  const modeLabels = {
    SINGLE_BUSINESS: 'Один бизнес',
    EXPLICIT_SELECTION: 'Явный выбор',
    VERIFIED_EXTERNAL_MAPPING: 'Проверенное внешнее сопоставление',
    VERIFIED_LOCATION_MAPPING: 'Проверенное сопоставление локации'
  };

  async function showChannelPreview(channel) {
    const requestId = ++previewRequestId;
    const panel = page.querySelector('#channelBindingPreview');
    const details = page.querySelector('#channelBindingRows');
    const previewStatus = page.querySelector('[data-channel-preview-status]');
    panel.hidden = false;
    page.querySelector('[data-channel-preview-title]').textContent =
      `Привязки канала «${channel.channelName || `#${channel.id}`}»`;
    previewStatus.textContent = 'Загрузка привязок…';
    previewStatus.className = 'small text-muted';
    details.replaceChildren();
    try {
      const preview = await request(`/channels/${channel.id}/binding-preview`);
      if (requestId !== previewRequestId) return;
      previewStatus.textContent = `${classificationLabels[preview.classification] || 'Статус не определён'}. Привязки сами по себе не включают маршрутизацию и не назначают бизнес обращению.`;
      if (!preview.bindings.length) {
        const row = document.createElement('tr');
        const empty = cell(row, 'Привязок нет.');
        empty.colSpan = 5;
        details.appendChild(row);
      }
      for (const binding of preview.bindings) {
        const row = document.createElement('tr');
        cell(row, binding.businessCode || `#${binding.businessId}`);
        cell(row, modeLabels[binding.resolutionMode] || binding.resolutionMode);
        cell(row, binding.businessStatus === 'active' ? 'Активен' : 'Архив');
        cell(row, binding.active ? 'Активна' : 'Выключена');
        const action = cell(row, '');
        if (binding.active) {
          action.appendChild(revokeButton(async () => {
            if (!window.confirm('Выключить привязку? Существующие обращения не изменятся.')) return;
            await perform(async () => {
              await request(`/channels/${channel.id}/bindings/${binding.businessId}`, 'DELETE');
              await showChannelPreview(channel);
            }, 'Привязка выключена.');
          }));
        }
        details.appendChild(row);
      }
    } catch (error) {
      if (requestId !== previewRequestId) return;
      previewStatus.textContent = error.message || 'Не удалось загрузить привязки.';
      previewStatus.className = 'small text-danger';
    }
  }

  async function loadChannels() {
    const channelStatus = page.querySelector('[data-channel-list-status]');
    try {
      const channels = await request('/channels');
      channelCatalog = channels;
      fillSelect('channelBindingChannel', channels, (channel) =>
        `${channel.channelName || 'Без названия'} (#${channel.id})`);
      const rows = page.querySelector('#businessChannelRows');
      rows.replaceChildren();
      for (const channel of channels) {
        const row = document.createElement('tr');
        cell(row, `${channel.channelName || 'Без названия'} (#${channel.id})`);
        cell(row, channel.platform || '—');
        cell(row, channel.active ? 'Активен' : 'Выключен');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-outline-primary';
        button.textContent = 'Привязки';
        button.addEventListener('click', () => showChannelPreview(channel));
        cell(row, '').appendChild(button);
        rows.appendChild(row);
      }
      channelStatus.textContent = channels.length ? `Каналов: ${channels.length}.` : 'Каналов пока нет.';
      count('channels', channels.length);
      filterRows('channels');
    } catch (error) {
      channelStatus.textContent = error.message || 'Не удалось загрузить каналы.';
      channelStatus.className = 'text-danger small';
    }
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

  page.querySelector('#channelBindingForm').addEventListener('submit', (event) => {
    event.preventDefault();
    const channelId = page.querySelector('#channelBindingChannel').value;
    const businessId = page.querySelector('#channelBindingBusiness').value;
    const resolutionMode = page.querySelector('#channelBindingMode').value;
    const channel = channelCatalog.find((item) => String(item.id) === channelId);
    if (!channel) {
      announce('Выберите канал.', true);
      return;
    }
    perform(async () => {
      await request(`/channels/${channelId}/bindings/${businessId}`, 'PUT', { resolutionMode });
      await showChannelPreview(channel);
    }, 'Привязка сохранена. Маршрутизация остаётся выключенной до отдельного включения.');
  });

  refresh().then(() => announce(snapshot.businesses.length
    ? 'Каталог и права загружены.' : 'Бизнесов пока нет.'))
    .catch((error) => announce(error.message || 'Не удалось загрузить каталог.', true));
  loadChannels();
})();

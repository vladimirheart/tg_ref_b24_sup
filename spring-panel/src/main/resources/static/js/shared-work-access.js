/* Shared tasks/projects: current server capabilities, exact numeric identities and stale-response guards. */
(function () {
  'use strict';
  const moduleRoot = document.querySelector('[data-work-module]');
  const roots = new Map(Array.from(document.querySelectorAll('[data-work-object]')).map(root => [root.dataset.workObject, root]));
  if (!moduleRoot && !roots.size) return;
  const states = new Map();
  let profile = null, moduleGeneration = 0;
  const $ = (root, selector) => root?.querySelector(selector);
  const id = value => typeof value === 'string' && /^[1-9]\d*$/.test(value) && BigInt(value) <= 9223372036854775807n;
  const node = (tag, text, className) => {
    const element = document.createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  };
  const status = (root, message) => { const el = $(root, '[data-work-status]'); if (el) el.textContent = message; };
  async function request(url, options = {}) {
    const headers = new Headers(options.headers || {});
    const method = options.method || 'GET';
    if (method !== 'GET') {
      const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
      const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
      if (!token) throw new Error('Обновите страницу: отсутствует защита запроса.');
      headers.set(header, token);
    }
    const response = await fetch(url, { ...options, headers, credentials: 'same-origin', cache: 'no-store', redirect: 'error' });
    if (!response.ok) {
      const error = new Error([401, 403, 404].includes(response.status)
        ? 'Доступ отозван или объект недоступен. Обновите страницу.'
        : 'Не удалось сохранить или загрузить доступ. Повторите запрос.');
      error.status = response.status;
      throw error;
    }
    return response.status === 204 ? null : response.json();
  }
  const json = body => ({ headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  function writable(root, allowed) {
    root?.closest('.modal')?.querySelectorAll('[data-work-write]').forEach(button => { button.disabled = !allowed; });
  }
  function clear(root) {
    $(root, '[data-work-members]')?.replaceChildren();
    $(root, '[data-work-person]')?.replaceChildren();
    $(root, '[data-work-history]')?.replaceChildren();
    const more = $(root, '[data-work-history-more]'); if (more) more.hidden = true;
    const manage = $(root, '[data-work-manage]'); if (manage) manage.hidden = true;
    for (const selector of ['[data-work-add]', '[data-work-transfer]']) { const button = $(root, selector); if (button) button.disabled = true; }
    writable(root, false);
  }
  const base = state => '/api/shared-work/' + state.kind + '/' + state.id;
  function same(state) { return states.get(state.kind) === state; }
  function deny(root, error, state) {
    if (state && !same(state)) return;
    clear(root); status(root, error.message);
    if ([401,403,404].includes(error.status)) window.dispatchEvent(new CustomEvent('shared-work:denied', { detail: { kind: state?.kind } }));
  }
  async function history(root, url, valid, append = false) {
    const list = $(root, '[data-work-history]'), more = $(root, '[data-work-history-more]');
    if (!list || !more) return;
    if (!append) { list.replaceChildren(); more.hidden = true; more.dataset.before = ''; }
    const before = append ? more.dataset.before : '';
    more.disabled = true;
    try {
      const rows = await request(url + (before ? '?before=' + encodeURIComponent(before) : ''));
      if (!valid()) return;
      const labels = { CREATED: 'Создание', SYSTEM_CREATED: 'Автоматическое создание', GRANT_SET: 'Допуск к модулю',
        MEMBER_SET: 'Права участника', MEMBER_REMOVED: 'Отзыв участия', OWNER_TRANSFERRED: 'Передача владения' };
      for (const event of rows) {
        if (!id(event.id)) throw new Error('Некорректный ответ журнала.');
        const item = node('li', undefined, 'list-group-item');
        item.appendChild(node('div', event.occurredAt + ' · ' + (labels[event.action] || event.action) + ' · ' + event.actorName));
        const detail = node('details'); detail.appendChild(node('summary', 'Изменение'));
        detail.appendChild(node('pre', event.beforeState + ' → ' + event.afterState)); item.appendChild(detail); list.appendChild(item);
      }
      more.dataset.before = rows.at(-1)?.id || '';
      more.hidden = rows.length < 50;
      more.onclick = () => history(root, url, valid, true);
    } catch (error) {
      if (valid()) { list.replaceChildren(node('li', error.message, 'list-group-item')); more.hidden = true; }
    } finally { if (valid()) more.disabled = false; }
  }
  async function people(state) {
    const root = roots.get(state.kind), selector = $(root, '[data-work-person]');
    const searchGeneration = state.searchGeneration = (state.searchGeneration || 0) + 1;
    selector.replaceChildren(); $(root, '[data-work-add]').disabled = true; $(root, '[data-work-transfer]').disabled = true;
    try {
      const rows = await request(base(state) + '/people?q=' + encodeURIComponent($(root, '[data-work-search]').value));
      if (!same(state) || searchGeneration !== state.searchGeneration) return;
      for (const person of rows) {
        if (!id(person.userId)) throw new Error('Некорректный ответ списка пользователей.');
        const option = node('option', person.username); option.value = person.userId; selector.appendChild(option);
      }
      selector.value = rows[0]?.userId || '';
      const enabled = rows.length > 0;
      $(root, '[data-work-add]').disabled = !enabled; $(root, '[data-work-transfer]').disabled = !enabled;
      status(root, enabled ? 'Доступ сохранится сразу после действия.' : 'Пользователи не найдены. Уточните поиск или выдайте допуск к модулю.');
    } catch (error) { if (same(state) && searchGeneration === state.searchGeneration) deny(root, error, state); }
  }
  async function mutate(state, suffix, method, body) {
    if (!same(state) || state.busy) return;
    state.busy = true;
    const root = roots.get(state.kind);
    root.querySelectorAll('button,select,input').forEach(control => { control.disabled = true; });
    try {
      await request(base(state) + suffix, { method, ...(body ? json(body) : {}) });
      if (!same(state)) return;
      await openObject(state.kind, state.id);
      window.dispatchEvent(new CustomEvent('shared-work:changed', { detail: { kind: state.kind } }));
    } catch (error) {
      if (same(state)) {
        // Re-read after an uncertain result, but never repeat the mutation automatically.
        await openObject(state.kind, state.id);
        if (states.get(state.kind)?.id === state.id) status(root, error.message);
      }
    }
  }
  async function openObject(kind, objectId) {
    const root = roots.get(kind); if (!root) return;
    const state = { kind, id: objectId, busy: false }; states.set(kind, state);
    clear(root); root.hidden = !objectId; status(root, objectId ? 'Проверяем доступ…' : '');
    root.querySelectorAll('button,select,input').forEach(control => { control.disabled = false; });
    $(root, '[data-work-search]').value = '';
    $(root, '[data-work-add]').disabled = true; $(root, '[data-work-transfer]').disabled = true;
    if (!objectId) { await ready; if (same(state)) writable(root, !!(kind === 'TASK' ? profile?.canCreateTask : profile?.canCreateProject)); return; }
    try {
      if (!id(objectId)) throw new Error('Некорректный идентификатор объекта.');
      const view = await request(base(state) + '/access');
      if (!same(state)) return;
      if (view.kind !== kind || view.id !== objectId) throw new Error('Объект изменился. Откройте карточку заново.');
      state.manage = view.canManage;
      writable(root, view.canWrite);
      $(root, '[data-work-manage]').hidden = !view.canManage;
      const labels = { OWNER: 'Владелец', EDITOR: 'Редактор', VIEWER: 'Чтение' };
      for (const member of view.members) {
        if (!id(member.userId)) throw new Error('Некорректный ответ состава участников.');
        const row = node('li', undefined, 'list-group-item d-flex gap-2 align-items-center');
        row.appendChild(node('span', member.username + ' — ' + (labels[member.role] || member.role)));
        if (view.canManage && member.role !== 'OWNER') {
          const remove = node('button', 'Отозвать', 'btn btn-sm btn-outline-danger ms-auto');
          remove.type = 'button'; remove.addEventListener('click', () => mutate(state, '/members/' + member.userId, 'DELETE')); row.appendChild(remove);
        }
        $(root, '[data-work-members]').appendChild(row);
      }
      status(root, view.canWrite ? 'Доступ к редактированию подтверждён.' : 'Доступно только чтение.');
      if (view.canManage) { await people(state); if (same(state)) await history(root, base(state) + '/history', () => same(state)); }
    } catch (error) { deny(root, error, state); }
  }
  async function loadAccounts() {
    if (!moduleRoot) return;
    const generation = ++moduleGeneration, container = $(moduleRoot, '[data-work-accounts]');
    const message = $(moduleRoot, '[data-work-module-status]');
    container.replaceChildren(); message.textContent = 'Загрузка допусков…';
    try {
      const rows = await request('/api/shared-work-admin/access');
      if (generation !== moduleGeneration) return;
      for (const account of rows) {
        if (!id(account.userId)) throw new Error('Некорректный ответ списка допусков.');
        const row = node('div', undefined, 'border-bottom py-2 d-flex gap-3 flex-wrap align-items-center');
        row.appendChild(node('strong', account.username));
        const checks = {};
        for (const [key,label] of [['canRead','Читать'],['canCreateTask','Создавать задачи'],['canCreateProject','Создавать проекты']]) {
          const wrapper = node('label', undefined, 'd-flex gap-1 align-items-center'), input = node('input');
          input.type = 'checkbox'; input.checked = !!account[key]; checks[key] = input;
          wrapper.append(input, node('span', label)); row.appendChild(wrapper);
        }
        const hierarchy = () => {
          for (const key of ['canCreateTask','canCreateProject']) { checks[key].disabled = !checks.canRead.checked; if (!checks.canRead.checked) checks[key].checked = false; }
        };
        checks.canRead.addEventListener('change', hierarchy); hierarchy();
        const save = node('button', 'Сохранить', 'btn btn-sm btn-primary'); save.type = 'button';
        save.addEventListener('click', async () => {
          if (save.disabled || generation !== moduleGeneration) return;
          row.querySelectorAll('input,button').forEach(control => { control.disabled = true; });
          message.textContent = 'Сохранение…';
          const grant = Object.fromEntries(Object.entries(checks).map(([key,input]) => [key, input.checked]));
          try {
            await request('/api/shared-work-admin/access/' + account.userId, { method: 'PUT', ...json(grant) });
            if (generation !== moduleGeneration) return;
            await loadAccounts(); message.textContent = 'Сохранено. Значения повторно прочитаны с сервера.';
          } catch (error) {
            if (generation === moduleGeneration) {
              container.replaceChildren(); message.textContent = error.message + ' Нажмите «Обновить», чтобы проверить результат.';
              if ([401,403,404].includes(error.status)) $(moduleRoot, '[data-work-toggle]').hidden = true;
            }
          }
        });
        row.appendChild(save); container.appendChild(row);
      }
      message.textContent = '';
      await history(moduleRoot, '/api/shared-work-admin/history', () => generation === moduleGeneration);
    } catch (error) {
      if (generation === moduleGeneration) { container.replaceChildren(); message.textContent = error.message; }
    }
  }
  for (const [kind,root] of roots) {
    $(root, '[data-work-find]').addEventListener('click', () => { const state = states.get(kind); if (state?.manage && !state.busy) people(state); });
    $(root, '[data-work-add]').addEventListener('click', () => {
      const state = states.get(kind), target = $(root, '[data-work-person]').value;
      if (state?.manage && id(target)) mutate(state, '/members/' + target, 'PUT', { role: $(root, '[data-work-role]').value });
    });
    $(root, '[data-work-transfer]').addEventListener('click', async () => {
      const state = states.get(kind), target = $(root, '[data-work-person]').value;
      if (!state?.manage || state.busy || !id(target)) return;
      const selected = $(root, '[data-work-person]').selectedOptions[0]?.textContent || '';
      const accepted = typeof window.showConfirmActionModal === 'function'
        ? await window.showConfirmActionModal({ title: 'Передать владение', message: 'Новый владелец: ' + selected + '. Прежний владелец останется редактором.', confirmText: 'Передать', confirmVariant: 'warning' })
        : window.confirm('Передать владение пользователю ' + selected + '?');
      if (accepted && same(state)) mutate(state, '/owner/' + target, 'PUT');
    });
    root.closest('.modal')?.addEventListener('hidden.bs.modal', () => openObject(kind, null));
  }
  $(moduleRoot, '[data-work-toggle]')?.addEventListener('click', () => {
    const panel = $(moduleRoot, '[data-work-grants]'); panel.hidden = !panel.hidden;
    if (!panel.hidden) loadAccounts(); else { moduleGeneration++; $(moduleRoot, '[data-work-accounts]').replaceChildren(); }
  });
  $(moduleRoot, '[data-work-reload]')?.addEventListener('click', loadAccounts);
  const ready = request('/api/shared-work/context').then(value => {
    profile = value;
    document.querySelectorAll('[data-work-create]').forEach(button => {
      button.disabled = !(button.dataset.workCreate === 'TASK' ? value.canCreateTask : value.canCreateProject);
    });
    if (moduleRoot) $(moduleRoot, '[data-work-toggle]').hidden = !value.systemAdmin;
  }).catch(error => { if (moduleRoot) $(moduleRoot, '[data-work-module-status]').textContent = error.message; });
  window.SharedWorkAccess = { openObject };
})();

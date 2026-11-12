(function () {
  'use strict';
  let modal, instance, userId, initial;
  const actionNames = { 'user.create': 'Создание пользователей', 'user.username': 'Изменение логинов и профилей',
    'user.password': 'Изменение паролей', 'user.role': 'Назначение ролей', 'user.delete': 'Удаление пользователей',
    'user.block': 'Блокировка пользователей', 'role.create': 'Создание ролей', 'role.name': 'Название роли',
    'role.description': 'Описание роли', 'role.pages': 'Доступ роли к страницам',
    'role.fields.edit': 'Права роли на изменение', 'role.fields.view': 'Права роли на просмотр', 'role.delete': 'Удаление ролей' };
  function buildModal() {
    if (modal) return;
    modal = document.createElement('div'); modal.className = 'modal fade'; modal.id = 'userPermissionsModal'; modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'userPermissionsTitle');
    modal.innerHTML = `<div class="modal-dialog modal-lg modal-dialog-scrollable"><div class="modal-content">
      <div class="modal-header"><h5 class="modal-title" id="userPermissionsTitle">Права пользователя</h5>
      <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button></div>
      <div class="modal-body"><p class="small text-muted">Доступ к странице разрешает просмотр. Изменение каждого блока настроек выдаётся отдельно. Индивидуальный запрет имеет приоритет над ролью.</p>
      <p data-permissions-message role="status"></p><div class="table-responsive"><table class="table table-sm align-middle">
      <thead><tr><th>Разрешение</th><th>По роли</th><th>Индивидуально</th><th>Итог</th></tr></thead><tbody></tbody></table></div></div>
      <div class="modal-footer"><button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Закрыть</button>
      <button type="button" class="btn btn-primary" data-permissions-save>Сохранить права</button></div></div></div>`;
    document.body.append(modal);
    modal.querySelector('[data-permissions-save]').addEventListener('click', save);
    instance = bootstrap.Modal.getOrCreateInstance(modal);
  }
  function message(text, danger = false) {
    const element = modal.querySelector('[data-permissions-message]'); element.textContent = text;
    element.className = danger ? 'text-danger' : 'small text-muted';
  }
  function render(payload) {
    initial = payload;
    modal.querySelector('h5').textContent = 'Права пользователя: ' + payload.username;
    const body = modal.querySelector('tbody'); body.replaceChildren();
    let group = '';
    payload.rows.forEach(row => {
      if (row.kind !== group) {
        group = row.kind; const heading = body.insertRow(); const cell = heading.insertCell(); cell.colSpan = 4;
        cell.className = 'fw-semibold pt-3'; cell.textContent = ({ page: 'Просмотр страниц', settings: 'Изменение блоков настроек', action: 'Права на изменение и управление' })[group];
      }
      const tr = body.insertRow();
      const label = row.kind === 'page' ? row.label : actionNames[row.key] || row.label;
      tr.insertCell().textContent = label;
      tr.insertCell().textContent = row.inherited ? 'Разрешено' : 'Запрещено';
      const select = document.createElement('select'); select.className = 'form-select form-select-sm';
      select.dataset.authority = row.authority; select.setAttribute('aria-label', 'Индивидуальное право: ' + label);
      for (const [value, text] of [['inherit', 'По роли'], ['allow', 'Разрешить'], ['deny', 'Запретить']]) select.add(new Option(text, value));
      select.value = row.override; select.disabled = !payload.can_edit || row.can_override === false;
      const effective = tr.insertCell();
      const update = () => { effective.textContent = (select.value === 'inherit' ? row.inherited : select.value === 'allow') ? 'Разрешено' : 'Запрещено'; };
      select.addEventListener('change', update);
      effective.textContent = row.effective ? 'Разрешено' : 'Запрещено';
      tr.insertCell(2).append(select);
    });
    modal.querySelector('[data-permissions-save]').disabled = !payload.can_edit;
    message(payload.can_edit ? '' : 'Режим просмотра. Собственные права и права системных администраторов защищены от изменения.');
  }
  async function open(id) {
    buildModal(); userId = id; initial = null; modal.querySelector('tbody').replaceChildren();
    modal.querySelector('[data-permissions-save]').disabled = true; message('Загрузка прав…'); instance.show();
    try {
      const response = await fetch(`/api/users/${encodeURIComponent(id)}/permissions`, { credentials: 'same-origin' });
      if (!response.ok) throw new Error('Не удалось загрузить права пользователя');
      render(await response.json());
    } catch (error) { message(error.message, true); }
  }
  async function save() {
    if (!initial?.can_edit) return;
    const payload = {};
    modal.querySelectorAll('select[data-authority]').forEach(select => {
      const previous = initial.rows.find(row => row.authority === select.dataset.authority);
      if (previous && previous.override !== select.value) payload[select.dataset.authority] = select.value;
    });
    const button = modal.querySelector('[data-permissions-save]'); button.disabled = true;
    try {
      const response = await fetch(`/api/users/${encodeURIComponent(userId)}/permissions`, { method: 'PUT', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      const data = await response.json();
      if (!response.ok || data.success === false) throw new Error(data.error || 'Не удалось сохранить права');
      render(data); message('Права сохранены. Они применяются со следующего запроса пользователя.');
    } catch (error) { message(error.message, true); button.disabled = false; }
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-user-permissions]');
    if (button) open(button.dataset.userPermissions);
  });
})();

(function () {
  'use strict';
  let modal, instance, userId, initial, editor;
  let requestVersion = 0, saving = false;
  const actionNames = { 'user.create': 'Создание пользователей', 'user.username': 'Изменение логинов и профилей',
    'user.password': 'Изменение паролей', 'user.role': 'Назначение ролей', 'user.delete': 'Удаление пользователей',
    'user.block': 'Блокировка пользователей', 'role.create': 'Создание ролей', 'role.name': 'Название роли',
    'role.description': 'Описание роли', 'role.pages': 'Доступ роли к страницам',
    'role.fields.edit': 'Права роли на изменение', 'role.fields.view': 'Права роли на просмотр', 'role.delete': 'Удаление ролей' };
  function buildModal() {
    if (modal) return;
    modal = document.createElement('div'); modal.className = 'modal fade settings-child-modal settings-task-modal permissions-modal'; modal.id = 'userPermissionsModal'; modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'userPermissionsTitle');
    modal.innerHTML = `<div class="modal-dialog modal-lg modal-dialog-scrollable"><div class="modal-content">
      <div class="modal-header"><h5 class="modal-title" id="userPermissionsTitle">Права пользователя</h5>
      <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Закрыть"></button></div>
      <div class="modal-body"><p class="permissions-editor__intro">По умолчанию действуют права роли. Для отдельного разрешения можно задать исключение; индивидуальный запрет имеет приоритет.</p>
      <p data-permissions-message role="status" aria-live="polite" hidden></p><div data-permissions-content></div>
      <p class="permissions-editor__footnote">Доступ к странице разрешает её просмотр. Право изменять настройки выдаётся отдельно.</p></div>
      <div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Закрыть</button>
      <button type="button" class="btn btn-primary" data-permissions-save>Сохранить права</button></div></div></div>`;
    document.body.append(modal);
    modal.querySelector('[data-permissions-save]').addEventListener('click', save);
    instance = bootstrap.Modal.getOrCreateInstance(modal);
  }
  function message(text, danger = false) {
    const element = modal.querySelector('[data-permissions-message]'); element.textContent = text;
    element.className = danger ? 'permissions-editor__message text-danger' : 'permissions-editor__message';
    element.hidden = !text;
  }
  function render(payload, viewState) {
    initial = payload;
    modal.querySelector('h5').textContent = 'Права пользователя: ' + payload.username;
    editor = window.PermissionEditor.create();
    modal.querySelector('[data-permissions-content]').replaceChildren(editor.element);
    const groups = new Map();
    payload.rows.forEach(row => {
      if (!groups.has(row.kind)) {
        const [title, hint] = ({ page: ['Просмотр страниц', 'Какие разделы панели доступны'],
          settings: ['Изменение настроек', 'Какие блоки настроек можно изменять'],
          action: ['Действия и управление', 'Операции с пользователями, ролями и другими объектами'] })[row.kind] || ['Другие разрешения', 'Дополнительные права'];
        groups.set(row.kind, editor.addGroup(title, hint));
      }
      const item = document.createElement('div'); item.className = 'permissions-editor__row';
      const label = row.kind === 'page' ? row.label : actionNames[row.key] || row.label;
      const name = document.createElement('span'); name.className = 'permissions-editor__name'; name.textContent = label;
      const inherited = document.createElement('div'); inherited.className = 'permissions-editor__value';
      const inheritedLabel = document.createElement('span'); inheritedLabel.className = 'permissions-editor__caption'; inheritedLabel.textContent = 'От роли';
      const inheritedValue = document.createElement('span'); inheritedValue.textContent = row.inherited ? 'Разрешено' : 'Запрещено';
      inherited.append(inheritedLabel, inheritedValue);
      const control = document.createElement('label'); control.className = 'permissions-editor__control';
      const controlLabel = document.createElement('span'); controlLabel.className = 'permissions-editor__caption'; controlLabel.textContent = 'Индивидуально';
      const select = document.createElement('select'); select.className = 'form-select form-select-sm';
      select.dataset.authority = row.authority; select.setAttribute('aria-label', 'Индивидуальное право: ' + label);
      for (const [value, text] of [['inherit', 'По роли'], ['allow', 'Разрешить'], ['deny', 'Запретить']]) select.add(new Option(text, value));
      select.value = row.override; select.disabled = !payload.can_edit || row.can_override === false;
      control.append(controlLabel, select);
      const result = document.createElement('div'); result.className = 'permissions-editor__value';
      const resultLabel = document.createElement('span'); resultLabel.className = 'permissions-editor__caption'; resultLabel.textContent = 'Итог';
      const effective = document.createElement('span'); effective.className = 'permissions-editor__state';
      const showResult = allowed => { effective.textContent = allowed ? 'Разрешено' : 'Запрещено'; effective.dataset.allowed = String(allowed); };
      const update = () => { showResult(select.value === 'inherit' ? row.inherited : select.value === 'allow'); item.classList.toggle('permissions-editor__row--override', select.value !== 'inherit'); };
      select.addEventListener('change', update);
      showResult(row.effective); item.classList.toggle('permissions-editor__row--override', row.override !== 'inherit');
      result.append(resultLabel, effective); item.append(name, inherited, control, result);
      editor.track(groups.get(row.kind), item, label + ' ' + row.key, () => effective.dataset.allowed === 'true');
    });
    editor.refresh(); editor.restore(viewState);
    modal.querySelector('[data-permissions-save]').disabled = !payload.can_edit;
    message(payload.can_edit ? '' : 'Режим просмотра. Собственные права и права системных администраторов защищены от изменения.');
  }
  async function open(id) {
    buildModal(); const version = ++requestVersion; userId = id; initial = null; editor = null; saving = false;
    modal.querySelector('h5').textContent = 'Права пользователя';
    modal.querySelector('[data-permissions-content]').replaceChildren();
    modal.querySelector('[data-permissions-save]').disabled = true; message('Загрузка прав…'); instance.show();
    try {
      const response = await fetch(`/api/users/${encodeURIComponent(id)}/permissions`, { credentials: 'same-origin' });
      if (!response.ok) throw new Error('Не удалось загрузить права пользователя');
      const data = await response.json(); if (version === requestVersion) render(data);
    } catch (error) { if (version === requestVersion) message(error.message, true); }
  }
  async function save() {
    if (!initial?.can_edit || saving) return;
    const payload = {};
    modal.querySelectorAll('select[data-authority]').forEach(select => {
      const previous = initial.rows.find(row => row.authority === select.dataset.authority);
      if (previous && !select.disabled && previous.override !== select.value) payload[select.dataset.authority] = select.value;
    });
    if (!Object.keys(payload).length) { message('Изменения не найдены.'); return; }
    const version = requestVersion, viewState = editor.snapshot();
    const button = modal.querySelector('[data-permissions-save]'); button.disabled = true; saving = true;
    modal.querySelectorAll('select[data-authority]').forEach(select => select.disabled = true);
    try {
      const response = await fetch(`/api/users/${encodeURIComponent(userId)}/permissions`, { method: 'PUT', credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
      const data = await response.json();
      if (!response.ok || data.success === false) throw new Error(data.error || 'Не удалось сохранить права');
      if (version === requestVersion) { render(data, viewState); message('Права сохранены. Они применяются со следующего запроса пользователя.'); }
    } catch (error) {
      if (version === requestVersion) {
        message(error.message, true); button.disabled = !initial.can_edit;
        modal.querySelectorAll('select[data-authority]').forEach(select => {
          const row = initial.rows.find(item => item.authority === select.dataset.authority);
          select.disabled = !initial.can_edit || row?.can_override === false;
        });
      }
    } finally { if (version === requestVersion) saving = false; }
  }
  document.addEventListener('click', event => {
    const button = event.target.closest('[data-user-permissions]');
    if (button) open(button.dataset.userPermissions);
  });
})();

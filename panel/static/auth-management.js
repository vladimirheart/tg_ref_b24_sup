(() => {
  const PASSWORD_PLACEHOLDER = '••••••••';
  const PERMISSION_WILDCARD = '*';

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function normalizeList(values) {
    if (!Array.isArray(values)) {
      return [];
    }
    const set = new Set();
    values.forEach((item) => {
      const key = typeof item === 'string' ? item.trim() : '';
      if (key) {
        set.add(key);
      }
    });
    return Array.from(set).sort();
  }

  class AuthManager {
    constructor(root) {
      this.root = root;
      this.fetchUrl = root.dataset.authStateUrl || '/auth/state';
      this.usersEndpoint = root.dataset.authUsersEndpoint || '/users';
      this.rolesEndpoint = root.dataset.authRolesEndpoint || '/roles';
      this.state = {
        users: [],
        roles: [],
        catalog: { pages: [], fields: { edit: [], view: [] } },
        capabilities: { fields: { edit: {}, view: {} } },
        currentUserId: null,
      };
      this.elements = {
        message: root.querySelector('[data-auth-message]'),
        usersSection: root.querySelector('[data-auth-users-section]'),
        usersBody: root.querySelector('[data-auth-users-body]'),
        usersEmpty: root.querySelector('[data-auth-users-empty]'),
        createUserForm: root.querySelector('[data-auth-user-create]'),
        createUserError: root.querySelector('[data-auth-user-create-error]'),
        createUserRoleSelect: root.querySelector('[data-auth-user-role-select]'),
        rolesSection: root.querySelector('[data-auth-roles-section]'),
        rolesContainer: root.querySelector('[data-auth-roles-container]'),
        rolesEmpty: root.querySelector('[data-auth-roles-empty]'),
        createRoleForm: root.querySelector('[data-auth-role-create]'),
        createRoleError: root.querySelector('[data-auth-role-create-error]'),
      };
      this.handleUsersClick = this.handleUsersClick.bind(this);
      this.handleRolesClick = this.handleRolesClick.bind(this);
      this.handlePermissionToggle = this.handlePermissionToggle.bind(this);
      this.handleCreateUser = this.handleCreateUser.bind(this);
      this.handleCreateRole = this.handleCreateRole.bind(this);
    }

    init() {
      this.bindEvents();
      return this.refresh();
    }

    bindEvents() {
      if (this.elements.usersSection) {
        this.elements.usersSection.addEventListener('click', this.handleUsersClick);
      }
      if (this.elements.rolesContainer) {
        this.elements.rolesContainer.addEventListener('click', this.handleRolesClick);
        this.elements.rolesContainer.addEventListener('change', this.handlePermissionToggle);
      }
      if (this.elements.createUserForm) {
        this.elements.createUserForm.addEventListener('submit', this.handleCreateUser);
      }
      if (this.elements.createRoleForm) {
        this.elements.createRoleForm.addEventListener('submit', this.handleCreateRole);
      }
    }

    setLoading(isLoading) {
      this.root.classList.toggle('auth-management--loading', Boolean(isLoading));
    }

    setMessage(text, variant = 'danger') {
      const box = this.elements.message;
      if (!box) {
        if (text) {
          console.warn(text);
        }
        return;
      }
      if (!text) {
        box.classList.add('d-none');
        box.textContent = '';
        box.classList.remove('alert-danger', 'alert-success', 'alert-info');
        return;
      }
      box.textContent = text;
      box.classList.remove('alert-danger', 'alert-success', 'alert-info', 'd-none');
      box.classList.add(`alert-${variant}`);
    }

    clearMessage() {
      this.setMessage('');
    }

    refresh() {
      this.setLoading(true);
      this.clearMessage();
      return fetch(this.fetchUrl, { credentials: 'same-origin' })
        .then((response) => {
          if (!response.ok) {
            throw new Error('Не удалось загрузить данные доступа');
          }
          return response.json();
        })
        .then((data) => {
          this.state.users = Array.isArray(data.users) ? data.users : [];
          this.state.roles = Array.isArray(data.roles) ? data.roles : [];
          this.state.catalog = data.catalog || { pages: [], fields: { edit: [], view: [] } };
          this.state.capabilities = data.capabilities || { fields: { edit: {}, view: {} } };
          this.state.currentUserId = data.current_user_id;
          this.render();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          this.setLoading(false);
        });
    }

    render() {
      this.renderUsers();
      this.renderRoles();
      this.updateCreateControls();
    }

    capabilitiesEdit(key) {
      return Boolean(this.state.capabilities?.fields?.edit?.[key]);
    }

    capabilitiesView(key) {
      return Boolean(this.state.capabilities?.fields?.view?.[key]);
    }

    updateCreateControls() {
      if (this.elements.createUserForm) {
        const canCreateUser = this.capabilitiesEdit('user.create');
        Array.from(this.elements.createUserForm.elements || []).forEach((control) => {
          control.disabled = !canCreateUser;
        });
        if (this.elements.createUserError) {
          if (canCreateUser) {
            this.elements.createUserError.classList.add('d-none');
            this.elements.createUserError.textContent = '';
          } else {
            this.elements.createUserError.textContent = 'У вас нет прав на создание пользователей.';
            this.elements.createUserError.classList.remove('d-none');
          }
        }
      }

      if (this.elements.createRoleForm) {
        const canCreateRole = this.capabilitiesEdit('role.create');
        Array.from(this.elements.createRoleForm.elements || []).forEach((control) => {
          control.disabled = !canCreateRole;
        });
        if (this.elements.createRoleError) {
          if (canCreateRole) {
            this.elements.createRoleError.classList.add('d-none');
            this.elements.createRoleError.textContent = '';
          } else {
            this.elements.createRoleError.textContent = 'У вас нет прав на создание ролей.';
            this.elements.createRoleError.classList.remove('d-none');
          }
        }
      }
    }

    renderUsers() {
      const tbody = this.elements.usersBody;
      if (tbody) {
        tbody.innerHTML = '';
      }

      if (this.elements.usersEmpty) {
        this.elements.usersEmpty.classList.toggle('d-none', this.state.users.length > 0);
      }

      this.populateCreateUserRoleOptions();

      if (!tbody) {
        return;
      }

      this.state.users.forEach((user) => {
        const row = this.createUserRow(user);
        tbody.appendChild(row);
      });
    }

    populateCreateUserRoleOptions() {
      const select = this.elements.createUserRoleSelect;
      if (!select) {
        return;
      }
      const roles = this.state.roles || [];
      const previousValue = select.value;
      select.innerHTML = '';

      const defaultOption = document.createElement('option');
      defaultOption.value = '';
      defaultOption.textContent = 'По умолчанию';
      select.appendChild(defaultOption);

      roles.forEach((role) => {
        const option = document.createElement('option');
        option.value = role.id != null ? String(role.id) : '';
        option.textContent = role.name || '';
        select.appendChild(option);
      });

      const preferred = roles.find((role) => (role.name || '').toLowerCase() === 'user');
      const targetValue = roles.some((role) => String(role.id) === previousValue)
        ? previousValue
        : preferred && preferred.id != null
          ? String(preferred.id)
          : '';
      select.value = targetValue;
    }

    createUserRow(user) {
      const row = document.createElement('tr');
      row.dataset.userId = String(user.id);
      row.dataset.initialUsername = user.username || '';
      row.dataset.initialRoleId = user.role_id != null ? String(user.role_id) : '';

      const usernameCell = document.createElement('td');
      const usernameInput = document.createElement('input');
      usernameInput.type = 'text';
      usernameInput.className = 'form-control form-control-sm';
      usernameInput.value = user.username || '';
      usernameInput.dataset.field = 'username';
      usernameInput.autocomplete = 'off';
      usernameInput.disabled = !user.can_edit_username;
      usernameCell.appendChild(usernameInput);

      const roleCell = document.createElement('td');
      const roleSelect = document.createElement('select');
      roleSelect.className = 'form-select form-select-sm';
      roleSelect.dataset.field = 'role';
      roleSelect.disabled = !user.can_edit_role;
      (this.state.roles || []).forEach((role) => {
        const option = document.createElement('option');
        option.value = role.id != null ? String(role.id) : '';
        option.textContent = role.name || '';
        if (String(role.id) === (user.role_id != null ? String(user.role_id) : '')) {
          option.selected = true;
        }
        roleSelect.appendChild(option);
      });
      roleCell.appendChild(roleSelect);

      const passwordCell = document.createElement('td');
      const passwordGroup = document.createElement('div');
      passwordGroup.className = 'input-group input-group-sm';
      const passwordInput = document.createElement('input');
      passwordInput.type = 'password';
      passwordInput.className = 'form-control';
      passwordInput.value = PASSWORD_PLACEHOLDER;
      passwordInput.readOnly = true;
      passwordInput.dataset.field = 'password';
      passwordGroup.appendChild(passwordInput);

      const toggleBtn = document.createElement('button');
      toggleBtn.type = 'button';
      toggleBtn.className = 'btn btn-outline-secondary';
      toggleBtn.textContent = 'Показать';
      toggleBtn.dataset.action = 'toggle-password';
      toggleBtn.disabled = !user.can_view_password;
      passwordGroup.appendChild(toggleBtn);

      const changeBtn = document.createElement('button');
      changeBtn.type = 'button';
      changeBtn.className = 'btn btn-outline-secondary';
      changeBtn.textContent = 'Сменить';
      changeBtn.dataset.action = 'change-password';
      changeBtn.disabled = !user.can_edit_password;
      passwordGroup.appendChild(changeBtn);

      passwordCell.appendChild(passwordGroup);

      const actionsCell = document.createElement('td');
      actionsCell.className = 'text-end';
      const actionsWrapper = document.createElement('div');
      actionsWrapper.className = 'd-flex justify-content-end gap-2';

      const saveBtn = document.createElement('button');
      saveBtn.type = 'button';
      saveBtn.className = 'btn btn-outline-primary btn-sm';
      saveBtn.dataset.action = 'save-user';
      saveBtn.textContent = 'Сохранить';
      saveBtn.disabled = !user.can_edit_username && !user.can_edit_role;
      actionsWrapper.appendChild(saveBtn);

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-outline-danger btn-sm';
      deleteBtn.dataset.action = 'delete-user';
      deleteBtn.textContent = 'Удалить';
      deleteBtn.disabled = !user.can_delete;
      actionsWrapper.appendChild(deleteBtn);

      actionsCell.appendChild(actionsWrapper);

      row.appendChild(usernameCell);
      row.appendChild(roleCell);
      row.appendChild(passwordCell);
      row.appendChild(actionsCell);
      return row;
    }

    resetPasswords() {
      if (!this.elements.usersBody) {
        return;
      }
      this.elements.usersBody.querySelectorAll('tr[data-user-id]').forEach((row) => {
        this.hidePassword(row);
      });
    }

    hidePassword(row) {
      const input = row.querySelector('[data-field="password"]');
      const toggle = row.querySelector('[data-action="toggle-password"]');
      if (input) {
        input.type = 'password';
        input.value = PASSWORD_PLACEHOLDER;
      }
      if (toggle) {
        toggle.textContent = 'Показать';
      }
      delete row.dataset.passwordVisible;
      delete row.dataset.passwordValue;
    }

    showPassword(row, password) {
      const input = row.querySelector('[data-field="password"]');
      const toggle = row.querySelector('[data-action="toggle-password"]');
      if (input) {
        input.type = 'text';
        input.value = password;
      }
      if (toggle) {
        toggle.textContent = 'Скрыть';
      }
      row.dataset.passwordVisible = 'true';
      row.dataset.passwordValue = password;
    }

    handleUsersClick(event) {
      const button = event.target.closest('[data-action]');
      if (!button) {
        return;
      }
      const row = button.closest('tr[data-user-id]');
      if (!row) {
        return;
      }
      const userId = row.dataset.userId;
      switch (button.dataset.action) {
        case 'save-user':
          this.handleSaveUser(row, userId, button);
          break;
        case 'toggle-password':
          this.handleTogglePassword(row, userId, button);
          break;
        case 'change-password':
          this.handleChangePassword(row, userId);
          break;
        case 'delete-user':
          this.handleDeleteUser(row, userId);
          break;
        default:
          break;
      }
    }

    handleCreateUser(event) {
      event.preventDefault();
      if (!this.capabilitiesEdit('user.create')) {
        this.setMessage('У вас нет прав на создание пользователей.');
        return;
      }
      const form = event.currentTarget;
      const formData = new FormData(form);
      const payload = {
        username: String(formData.get('username') || '').trim(),
        password: String(formData.get('password') || '').trim(),
      };
      const roleId = String(formData.get('role_id') || '').trim();
      if (roleId) {
        payload.role_id = Number(roleId);
      }
      if (!payload.username || !payload.password) {
        if (this.elements.createUserError) {
          this.elements.createUserError.textContent = 'Укажите логин и пароль.';
          this.elements.createUserError.classList.remove('d-none');
        }
        return;
      }
      if (this.elements.createUserError) {
        this.elements.createUserError.classList.add('d-none');
        this.elements.createUserError.textContent = '';
      }
      this.setMessage('');
      fetch(this.usersEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось создать пользователя');
          }
          form.reset();
          this.populateCreateUserRoleOptions();
          this.setMessage('Пользователь создан.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          if (this.elements.createUserError) {
            this.elements.createUserError.textContent = error.message || String(error);
            this.elements.createUserError.classList.remove('d-none');
          } else {
            this.setMessage(error.message || String(error));
          }
        });
    }

    handleSaveUser(row, userId, button) {
      const payload = {};
      const usernameInput = row.querySelector('[data-field="username"]');
      const roleSelect = row.querySelector('[data-field="role"]');
      const initialUsername = row.dataset.initialUsername || '';
      const initialRoleId = row.dataset.initialRoleId || '';
      let hasChanges = false;

      if (usernameInput && !usernameInput.disabled) {
        const newUsername = usernameInput.value.trim();
        if (newUsername !== initialUsername) {
          payload.username = newUsername;
          hasChanges = true;
        }
      }

      if (roleSelect && !roleSelect.disabled) {
        const newRole = roleSelect.value ? String(roleSelect.value) : '';
        if (newRole !== initialRoleId) {
          payload.role_id = newRole ? Number(newRole) : null;
          hasChanges = true;
        }
      }

      if (!hasChanges) {
        this.setMessage('Нет изменений для сохранения.', 'info');
        return;
      }

      button.disabled = true;
      fetch(`${this.usersEndpoint}/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось сохранить пользователя');
          }
          this.setMessage('Данные пользователя обновлены.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          button.disabled = false;
        });
    }

    handleTogglePassword(row, userId, button) {
      if (row.dataset.passwordVisible === 'true') {
        this.hidePassword(row);
        return;
      }
      if (row.dataset.passwordValue) {
        this.showPassword(row, row.dataset.passwordValue);
        return;
      }
      button.disabled = true;
      fetch(`${this.usersEndpoint}/${userId}/password`, { credentials: 'same-origin' })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Пароль недоступен');
          }
          this.showPassword(row, data.password || '');
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          button.disabled = false;
        });
    }

    handleChangePassword(row, userId) {
      const currentName = row.dataset.initialUsername || '';
      const promptTitle = currentName ? `Введите новый пароль для ${currentName}` : 'Введите новый пароль';
      const newPassword = window.prompt(promptTitle, '');
      if (!newPassword) {
        return;
      }
      fetch(`${this.usersEndpoint}/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ password: newPassword }),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось изменить пароль');
          }
          this.hidePassword(row);
          this.setMessage('Пароль обновлён.', 'success');
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        });
    }

    handleDeleteUser(row, userId) {
      const username = row.dataset.initialUsername || '';
      if (!window.confirm(`Удалить пользователя ${username || '#' + userId}?`)) {
        return;
      }
      fetch(`${this.usersEndpoint}/${userId}`, {
        method: 'DELETE',
        credentials: 'same-origin',
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось удалить пользователя');
          }
          this.setMessage('Пользователь удалён.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        });
    }

    renderRoles() {
      const container = this.elements.rolesContainer;
      if (container) {
        container.innerHTML = '';
      }
      if (this.elements.rolesEmpty) {
        this.elements.rolesEmpty.classList.toggle('d-none', this.state.roles.length > 0);
      }
      if (!container) {
        return;
      }

      const catalog = this.state.catalog || { pages: [], fields: { edit: [], view: [] } };

      this.state.roles.forEach((role) => {
        const card = this.createRoleCard(role, catalog);
        container.appendChild(card);
      });
    }

    createRoleCard(role, catalog) {
      const card = document.createElement('div');
      card.className = 'card shadow-sm role-card';
      card.dataset.roleId = String(role.id);
      card.dataset.initialName = role.name || '';
      card.dataset.initialDescription = role.description || '';
      const pagesInitial = normalizeList(role.permissions?.pages || []);
      const fieldsEditInitial = normalizeList(role.permissions?.fields?.edit || []);
      const fieldsViewInitial = normalizeList(role.permissions?.fields?.view || []);
      card.dataset.initialPages = JSON.stringify(pagesInitial);
      card.dataset.initialFieldsEdit = JSON.stringify(fieldsEditInitial);
      card.dataset.initialFieldsView = JSON.stringify(fieldsViewInitial);

      const canEditName = this.capabilitiesEdit('role.name') && !role.is_admin;
      const canEditDescription = this.capabilitiesEdit('role.description');
      const canEditPages = this.capabilitiesEdit('role.pages') && !role.is_admin;
      const canEditFieldsEdit = this.capabilitiesEdit('role.fields.edit') && !role.is_admin;
      const canEditFieldsView = this.capabilitiesEdit('role.fields.view') && !role.is_admin;
      const canDeleteRole = this.capabilitiesEdit('role.delete') && !role.is_admin;

      const collapseId = `roleDetails${role.id != null ? role.id : Math.random().toString(16).slice(2)}`;

      const header = document.createElement('div');
      header.className = 'card-header d-flex align-items-center gap-2';

      const toggleButton = document.createElement('button');
      toggleButton.type = 'button';
      toggleButton.className =
        'role-card__toggle d-flex align-items-center flex-grow-1 text-start bg-transparent border-0 p-0';
      toggleButton.dataset.bsToggle = 'collapse';
      toggleButton.dataset.bsTarget = `#${collapseId}`;
      toggleButton.setAttribute('aria-expanded', 'false');
      toggleButton.setAttribute('aria-controls', collapseId);

      const toggleContent = document.createElement('div');
      toggleContent.className = 'flex-grow-1';
      const toggleTitle = document.createElement('div');
      toggleTitle.className = 'fw-semibold role-card__title';
      toggleContent.appendChild(toggleTitle);
      const toggleSummary = document.createElement('div');
      toggleSummary.className = 'small text-muted role-card__summary';
      toggleContent.appendChild(toggleSummary);

      toggleButton.appendChild(toggleContent);

      const chevron = document.createElement('i');
      chevron.className = 'bi bi-chevron-down role-card__chevron';
      toggleButton.appendChild(chevron);

      header.appendChild(toggleButton);

      const badgeWrapper = document.createElement('div');
      badgeWrapper.className = 'text-end align-self-center ms-2';
      const badge = document.createElement('span');
      badge.className = 'badge rounded-pill ' + (role.is_admin ? 'bg-danger' : 'bg-secondary');
      badge.textContent = role.is_admin ? 'Администратор' : 'Роль';
      badgeWrapper.appendChild(badge);
      header.appendChild(badgeWrapper);

      const collapse = document.createElement('div');
      collapse.className = 'collapse';
      collapse.id = collapseId;

      const body = document.createElement('div');
      body.className = 'card-body';

      const nameGroup = document.createElement('div');
      nameGroup.className = 'mb-3';
      const nameLabel = document.createElement('label');
      nameLabel.className = 'form-label small mb-1';
      nameLabel.textContent = 'Название';
      const nameInput = document.createElement('input');
      nameInput.type = 'text';
      nameInput.className = 'form-control form-control-sm';
      nameInput.value = role.name || '';
      nameInput.dataset.field = 'name';
      nameInput.disabled = !canEditName;
      nameInput.autocomplete = 'off';
      nameGroup.appendChild(nameLabel);
      nameGroup.appendChild(nameInput);
      body.appendChild(nameGroup);

      const descriptionGroup = document.createElement('div');
      descriptionGroup.className = 'mb-3';
      const descriptionLabel = document.createElement('label');
      descriptionLabel.className = 'form-label small mb-1';
      descriptionLabel.textContent = 'Описание';
      const descriptionInput = document.createElement('textarea');
      descriptionInput.className = 'form-control form-control-sm';
      descriptionInput.rows = 2;
      descriptionInput.dataset.field = 'description';
      descriptionInput.disabled = !canEditDescription;
      descriptionInput.value = role.description || '';
      descriptionGroup.appendChild(descriptionLabel);
      descriptionGroup.appendChild(descriptionInput);
      body.appendChild(descriptionGroup);

      const pagesGroup = this.buildPermissionGroup(
        'pages',
        catalog.pages || [],
        role.permissions?.pages || [],
        canEditPages
      );
      body.appendChild(pagesGroup);

      const fieldsEditGroup = this.buildPermissionGroup(
        'fields-edit',
        catalog.fields?.edit || [],
        role.permissions?.fields?.edit || [],
        canEditFieldsEdit
      );
      body.appendChild(fieldsEditGroup);

      const fieldsViewGroup = this.buildPermissionGroup(
        'fields-view',
        catalog.fields?.view || [],
        role.permissions?.fields?.view || [],
        canEditFieldsView,
        'permissions-view'
      );
      body.appendChild(fieldsViewGroup);

      const footer = document.createElement('div');
      footer.className = 'card-footer d-flex justify-content-between gap-2';
      const saveBtn = document.createElement('button');
      saveBtn.type = 'button';
      saveBtn.className = 'btn btn-primary btn-sm';
      saveBtn.dataset.action = 'save-role';
      saveBtn.textContent = 'Сохранить';
      saveBtn.disabled = !canEditName && !canEditDescription && !canEditPages && !canEditFieldsEdit && !canEditFieldsView;

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-outline-danger btn-sm';
      deleteBtn.dataset.action = 'delete-role';
      deleteBtn.textContent = 'Удалить';
      deleteBtn.disabled = !canDeleteRole;

      footer.appendChild(saveBtn);
      footer.appendChild(deleteBtn);

      const updateTitle = () => {
        const value = nameInput.value.trim();
        toggleTitle.textContent = value || 'Без названия';
      };
      updateTitle();
      nameInput.addEventListener('input', updateTitle);

      const updateSummary = () => {
        const value = descriptionInput.value.trim();
        if (value) {
          toggleSummary.textContent = value;
          toggleSummary.classList.remove('role-card__summary--empty');
        } else {
          toggleSummary.textContent = 'Описание не задано';
          toggleSummary.classList.add('role-card__summary--empty');
        }
      };
      updateSummary();
      descriptionInput.addEventListener('input', updateSummary);

      collapse.appendChild(body);
      collapse.appendChild(footer);

      card.appendChild(header);
      card.appendChild(collapse);
      return card;
    }

    buildPermissionGroup(groupKey, options, selectedValues, canEdit, typeSuffix) {
      const wrapper = document.createElement('div');
      wrapper.className = 'mb-3';
      const label = document.createElement('label');
      label.className = 'form-label small mb-1';
      switch (groupKey) {
        case 'pages':
          label.textContent = 'Доступ к страницам';
          break;
        case 'fields-edit':
          label.textContent = 'Права на редактирование полей';
          break;
        case 'fields-view':
          label.textContent = 'Права на просмотр полей';
          break;
        default:
          label.textContent = 'Права';
      }
      wrapper.appendChild(label);

      const row = document.createElement('div');
      row.className = 'row row-cols-1 row-cols-sm-2 g-2';
      row.dataset.permissionGroup = groupKey;

      const selected = new Set(selectedValues || []);
      const hasWildcard = selected.has(PERMISSION_WILDCARD);

      const wildcardCol = document.createElement('div');
      wildcardCol.className = 'col';
      const wildcardCheckWrapper = document.createElement('div');
      wildcardCheckWrapper.className = 'form-check';
      const wildcardCheckbox = document.createElement('input');
      wildcardCheckbox.className = 'form-check-input';
      wildcardCheckbox.type = 'checkbox';
      wildcardCheckbox.value = PERMISSION_WILDCARD;
      wildcardCheckbox.dataset.permission = groupKey;
      wildcardCheckbox.checked = hasWildcard;
      wildcardCheckbox.disabled = !canEdit;
      wildcardCheckbox.dataset.locked = (!canEdit).toString();
      const wildcardLabel = document.createElement('label');
      wildcardLabel.className = 'form-check-label';
      wildcardLabel.textContent = groupKey === 'pages' ? 'Все страницы' : 'Все элементы';
      wildcardCheckWrapper.appendChild(wildcardCheckbox);
      wildcardCheckWrapper.appendChild(wildcardLabel);
      wildcardCol.appendChild(wildcardCheckWrapper);
      row.appendChild(wildcardCol);

      options.forEach((option) => {
        const col = document.createElement('div');
        col.className = 'col';
        const checkWrapper = document.createElement('div');
        checkWrapper.className = 'form-check';
        const checkbox = document.createElement('input');
        checkbox.className = 'form-check-input';
        checkbox.type = 'checkbox';
        checkbox.value = option.key;
        checkbox.dataset.permission = groupKey;
        checkbox.dataset.locked = (!canEdit).toString();
        checkbox.checked = selected.has(option.key);
        checkbox.disabled = !canEdit || hasWildcard;
        const optionLabel = document.createElement('label');
        optionLabel.className = 'form-check-label';
        optionLabel.textContent = option.label || option.key;
        checkWrapper.appendChild(checkbox);
        checkWrapper.appendChild(optionLabel);
        col.appendChild(checkWrapper);
        row.appendChild(col);
      });

      wrapper.appendChild(row);
      return wrapper;
    }

    handlePermissionToggle(event) {
      const checkbox = event.target;
      if (!(checkbox instanceof HTMLInputElement)) {
        return;
      }
      const group = checkbox.closest('[data-permission-group]');
      if (!group) {
        return;
      }
      if (checkbox.value === PERMISSION_WILDCARD) {
        group.querySelectorAll('input[type="checkbox"]').forEach((input) => {
          if (input === checkbox) {
            return;
          }
          const locked = input.dataset.locked === 'true';
          if (checkbox.checked) {
            if (!locked) {
              input.checked = false;
              input.disabled = true;
            }
          } else if (!locked) {
            input.disabled = false;
          }
        });
      } else if (checkbox.checked) {
        const wildcard = group.querySelector(`input[type="checkbox"][value="${PERMISSION_WILDCARD}"]`);
        if (wildcard && wildcard.checked) {
          if (wildcard.dataset.locked !== 'true') {
            wildcard.checked = false;
            wildcard.disabled = false;
          }
          group.querySelectorAll('input[type="checkbox"]').forEach((input) => {
            if (input === wildcard) {
              return;
            }
            if (input.dataset.locked === 'true') {
              return;
            }
            input.disabled = false;
          });
        }
      }
    }

    handleRolesClick(event) {
      const button = event.target.closest('[data-action]');
      if (!button) {
        return;
      }
      const card = button.closest('[data-role-id]');
      if (!card) {
        return;
      }
      const roleId = card.dataset.roleId;
      switch (button.dataset.action) {
        case 'save-role':
          this.handleSaveRole(card, roleId, button);
          break;
        case 'delete-role':
          this.handleDeleteRole(card, roleId);
          break;
        default:
          break;
      }
    }

    handleCreateRole(event) {
      event.preventDefault();
      if (!this.capabilitiesEdit('role.create')) {
        this.setMessage('У вас нет прав на создание ролей.');
        return;
      }
      const form = event.currentTarget;
      const formData = new FormData(form);
      const payload = {
        name: String(formData.get('name') || '').trim(),
        description: String(formData.get('description') || '').trim(),
      };
      if (!payload.name) {
        if (this.elements.createRoleError) {
          this.elements.createRoleError.textContent = 'Название роли обязательно.';
          this.elements.createRoleError.classList.remove('d-none');
        }
        return;
      }
      if (this.elements.createRoleError) {
        this.elements.createRoleError.classList.add('d-none');
        this.elements.createRoleError.textContent = '';
      }
      fetch(this.rolesEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось создать роль');
          }
          form.reset();
          this.setMessage('Роль создана.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          if (this.elements.createRoleError) {
            this.elements.createRoleError.textContent = error.message || String(error);
            this.elements.createRoleError.classList.remove('d-none');
          } else {
            this.setMessage(error.message || String(error));
          }
        });
    }

    collectRolePayload(card) {
      const payload = {};
      const nameInput = card.querySelector('[data-field="name"]');
      const descriptionInput = card.querySelector('[data-field="description"]');

      if (nameInput && !nameInput.disabled) {
        const value = nameInput.value.trim();
        if (value !== (card.dataset.initialName || '')) {
          payload.name = value;
        }
      }
      if (descriptionInput && !descriptionInput.disabled) {
        const value = descriptionInput.value.trim();
        if (value !== (card.dataset.initialDescription || '')) {
          payload.description = value;
        }
      }

      const permissions = {};
      const pagesGroup = card.querySelector('[data-permission-group="pages"]');
      if (pagesGroup && this.isPermissionGroupEditable(pagesGroup)) {
        const pages = this.collectPermissionsFromGroup(pagesGroup);
        if (JSON.stringify(normalizeList(pages)) !== card.dataset.initialPages) {
          permissions.pages = pages;
        }
      }
      const fieldsEditGroup = card.querySelector('[data-permission-group="fields-edit"]');
      if (fieldsEditGroup && this.isPermissionGroupEditable(fieldsEditGroup)) {
        const fieldsEdit = this.collectPermissionsFromGroup(fieldsEditGroup);
        if (JSON.stringify(normalizeList(fieldsEdit)) !== card.dataset.initialFieldsEdit) {
          permissions.fields = permissions.fields || {};
          permissions.fields.edit = fieldsEdit;
        }
      }
      const fieldsViewGroup = card.querySelector('[data-permission-group="fields-view"]');
      if (fieldsViewGroup && this.isPermissionGroupEditable(fieldsViewGroup)) {
        const fieldsView = this.collectPermissionsFromGroup(fieldsViewGroup);
        if (JSON.stringify(normalizeList(fieldsView)) !== card.dataset.initialFieldsView) {
          permissions.fields = permissions.fields || {};
          permissions.fields.view = fieldsView;
        }
      }

      if (Object.keys(permissions).length > 0) {
        payload.permissions = permissions;
      }

      return payload;
    }

    collectPermissionsFromGroup(group) {
      const selected = [];
      const wildcard = group.querySelector(`input[type="checkbox"][value="${PERMISSION_WILDCARD}"]`);
      if (wildcard && wildcard.checked) {
        return [PERMISSION_WILDCARD];
      }
      group.querySelectorAll('input[type="checkbox"]').forEach((checkbox) => {
        if (checkbox.checked && checkbox.value !== PERMISSION_WILDCARD) {
          selected.push(checkbox.value);
        }
      });
      return selected;
    }

    isPermissionGroupEditable(group) {
      return Array.from(group.querySelectorAll('input[type="checkbox"]')).some(
        (checkbox) => checkbox.dataset.locked !== 'true'
      );
    }

    handleSaveRole(card, roleId, button) {
      const payload = this.collectRolePayload(card);
      if (Object.keys(payload).length === 0) {
        this.setMessage('Изменения не найдены.', 'info');
        return;
      }
      button.disabled = true;
      fetch(`${this.rolesEndpoint}/${roleId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось сохранить роль');
          }
          this.setMessage('Права роли обновлены.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          button.disabled = false;
        });
    }

    handleDeleteRole(card, roleId) {
      if (!window.confirm('Удалить роль?')) {
        return;
      }
      fetch(`${this.rolesEndpoint}/${roleId}`, {
        method: 'DELETE',
        credentials: 'same-origin',
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось удалить роль');
          }
          this.setMessage('Роль удалена.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        });
    }
  }

  window.AuthManagement = {
    mount(root) {
      if (!root) {
        return null;
      }
      if (root.__authManager) {
        root.__authManager.refresh();
        return root.__authManager;
      }
      const manager = new AuthManager(root);
      root.__authManager = manager;
      manager.init();
      return manager;
    },
  };
})();

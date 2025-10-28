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
      const documentRoot = typeof document === 'undefined' ? null : document;
      const resolveFrom = (scope, selector) => scope?.querySelector(selector) || null;
      const userModal =
        resolveFrom(root, '[data-auth-user-modal]') ||
        resolveFrom(documentRoot, '[data-auth-user-modal]');

      this.elements = {
        message: resolveFrom(root, '[data-auth-message]'),
        usersSection: resolveFrom(root, '[data-auth-users-section]'),
        usersBody: resolveFrom(root, '[data-auth-users-body]'),
        usersEmpty: resolveFrom(root, '[data-auth-users-empty]'),
        createUserButton: resolveFrom(root, '[data-auth-user-create-trigger]'),
        rolesSection: resolveFrom(root, '[data-auth-roles-section]'),
        rolesContainer: resolveFrom(root, '[data-auth-roles-container]'),
        rolesEmpty: resolveFrom(root, '[data-auth-roles-empty]'),
        createRoleForm: resolveFrom(root, '[data-auth-role-create]'),
        createRoleError: resolveFrom(root, '[data-auth-role-create-error]'),
        userModal,
        userForm: resolveFrom(userModal, '[data-auth-user-form]'),
        userModalTitle: resolveFrom(userModal, '[data-auth-user-modal-title]'),
        userModalError: resolveFrom(userModal, '[data-auth-user-modal-error]'),
        userRoleSelect: resolveFrom(userModal, '[data-auth-user-role-select]'),
        userPasswordDisplay: resolveFrom(userModal, '[data-auth-user-password-display]'),
        userPasswordToggle: resolveFrom(userModal, '[data-auth-user-password-toggle]'),
        userPasswordBlock: resolveFrom(userModal, '[data-auth-user-password-current]'),
        userPasswordInput: resolveFrom(userModal, '[name="password"]'),
        userRegistrationInput: resolveFrom(userModal, '[data-auth-user-registration]'),
        userPhonesContainer: resolveFrom(userModal, '[data-auth-user-phones]'),
        userPhonesEmpty: resolveFrom(userModal, '[data-auth-user-phones-empty]'),
        userPhoneAddButton: resolveFrom(userModal, '[data-auth-user-phone-add]'),
        userUsernameInput: resolveFrom(userModal, '[name="username"]'),
        userEmailInput: resolveFrom(userModal, '[name="email"]'),
        userDepartmentInput: resolveFrom(userModal, '[name="department"]'),
        userBirthDateInput: resolveFrom(userModal, '[name="birth_date"]'),
        userPhotoInput: resolveFrom(userModal, '[name="photo"]'),
      };
      this.handleUsersClick = this.handleUsersClick.bind(this);
      this.handleRolesClick = this.handleRolesClick.bind(this);
      this.handlePermissionToggle = this.handlePermissionToggle.bind(this);
      this.handleUserFormSubmit = this.handleUserFormSubmit.bind(this);
      this.handleCreateRole = this.handleCreateRole.bind(this);
      this.handleCreateUserTrigger = this.handleCreateUserTrigger.bind(this);
      this.handleAddPhone = this.handleAddPhone.bind(this);
      this.handlePhoneListClick = this.handlePhoneListClick.bind(this);
      this.handleModalPasswordToggle = this.handleModalPasswordToggle.bind(this);
      this.handleUserModalHidden = this.handleUserModalHidden.bind(this);
      this.modalState = null;
      this.modalPasswordState = { visible: false, loaded: false, value: '' };
      this.modalInstance = null;
      this.bootstrap = window.bootstrap || null;
    }

    init() {
      this.bindEvents();
      if (this.elements.userModal && this.bootstrap?.Modal) {
        this.modalInstance = this.bootstrap.Modal.getOrCreateInstance(this.elements.userModal);
      }
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
      if (this.elements.createRoleForm) {
        this.elements.createRoleForm.addEventListener('submit', this.handleCreateRole);
      }
      if (this.elements.createUserButton) {
        this.elements.createUserButton.addEventListener('click', this.handleCreateUserTrigger);
      }
      if (this.elements.userForm) {
        this.elements.userForm.addEventListener('submit', this.handleUserFormSubmit);
      }
      if (this.elements.userPhoneAddButton) {
        this.elements.userPhoneAddButton.addEventListener('click', this.handleAddPhone);
      }
      if (this.elements.userPhonesContainer) {
        this.elements.userPhonesContainer.addEventListener('click', this.handlePhoneListClick);
      }
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.addEventListener('click', this.handleModalPasswordToggle);
      }
      if (this.elements.userModal) {
        this.elements.userModal.addEventListener('hidden.bs.modal', this.handleUserModalHidden);
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
      if (this.elements.createUserButton) {
        const canCreateUser = this.capabilitiesEdit('user.create');
        this.elements.createUserButton.disabled = !canCreateUser;
        if (!canCreateUser) {
          this.elements.createUserButton.classList.add('disabled');
          this.elements.createUserButton.setAttribute('title', 'У вас нет прав на создание пользователей.');
        } else {
          this.elements.createUserButton.classList.remove('disabled');
          this.elements.createUserButton.removeAttribute('title');
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

      if (!tbody) {
        return;
      }

      this.state.users.forEach((user) => {
        const row = this.createUserRow(user);
        tbody.appendChild(row);
      });
    }
    createUserRow(user) {
      const row = document.createElement('tr');
      row.dataset.userId = String(user.id);
      row.dataset.userName = user.username || '';

      const usernameCell = document.createElement('td');
      const usernameTitle = document.createElement('div');
      usernameTitle.className = 'fw-semibold';
      usernameTitle.textContent = user.username || '—';
      usernameCell.appendChild(usernameTitle);
      if (user.department) {
        const departmentRow = document.createElement('div');
        departmentRow.className = 'text-muted small';
        departmentRow.textContent = user.department;
        usernameCell.appendChild(departmentRow);
      }

      const roleCell = document.createElement('td');
      roleCell.textContent = user.role || '—';

      const contactsCell = document.createElement('td');
      const contactsWrapper = document.createElement('div');
      contactsWrapper.className = 'd-flex flex-column gap-1';
      if (user.email) {
        const emailRow = document.createElement('div');
        emailRow.className = 'small';
        emailRow.innerHTML = `<i class="bi bi-envelope me-1"></i>${escapeHtml(user.email)}`;
        contactsWrapper.appendChild(emailRow);
      }
      const phonesText = this.formatPhonesList(user.phones);
      if (phonesText) {
        const phonesRow = document.createElement('div');
        phonesRow.className = 'small';
        phonesRow.innerHTML = `<i class="bi bi-telephone me-1"></i>${escapeHtml(phonesText)}`;
        contactsWrapper.appendChild(phonesRow);
      }
      if (!contactsWrapper.childElementCount) {
        const emptyRow = document.createElement('div');
        emptyRow.className = 'text-muted small';
        emptyRow.textContent = '—';
        contactsWrapper.appendChild(emptyRow);
      }
      contactsCell.appendChild(contactsWrapper);

      const actionsCell = document.createElement('td');
      actionsCell.className = 'text-end';
      const actionsWrapper = document.createElement('div');
      actionsWrapper.className = 'd-flex justify-content-end gap-2';

      const openBtn = document.createElement('button');
      openBtn.type = 'button';
      openBtn.className = 'btn btn-outline-primary btn-sm';
      openBtn.dataset.action = 'open-user';
      openBtn.textContent = 'Открыть';
      actionsWrapper.appendChild(openBtn);

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
      row.appendChild(contactsCell);
      row.appendChild(actionsCell);
      return row;
    }

    formatPhonesList(phones) {
      if (!Array.isArray(phones) || phones.length === 0) {
        return '';
      }
      const values = phones
        .map((item) => {
          if (!item) {
            return null;
          }
          const number = String(item.value || item.number || '').trim();
          if (!number) {
            return null;
          }
          const label = String(item.type || item.label || '').trim();
          return label ? `${label}: ${number}` : number;
        })
        .filter(Boolean);
      return values.join(', ');
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
        case 'open-user':
          this.openUserModal(userId);
          break;
        case 'delete-user':
          this.handleDeleteUser(row, userId);
          break;
        default:
          break;
      }
    }

    handleCreateUserTrigger(event) {
      event.preventDefault();
      if (!this.capabilitiesEdit('user.create')) {
        this.setMessage('У вас нет прав на создание пользователей.');
        return;
      }
      this.showUserModal('create');
    }

    openUserModal(userId) {
      const user = this.state.users.find((item) => String(item.id) === String(userId));
      if (!user) {
        this.setMessage('Пользователь не найден.', 'danger');
        return;
      }
      this.showUserModal('edit', user);
    }

    showUserModal(mode, user) {
      if (!this.elements.userForm) {
        return;
      }
      this.modalPasswordState = { visible: false, loaded: false, value: '' };
      const original = user ? JSON.parse(JSON.stringify(user)) : null;
      const permissions = this.resolveModalPermissions(mode, user);
      this.modalState = {
        mode,
        userId: mode === 'edit' ? user?.id : null,
        original,
        permissions,
      };
      this.elements.userForm.dataset.mode = mode;
      this.setUserModalError('');

      if (this.elements.userModalTitle) {
        this.elements.userModalTitle.textContent =
          mode === 'create'
            ? 'Новый пользователь'
            : `Пользователь ${user?.username || ''}`.trim();
      }

      const username = user?.username || '';
      if (this.elements.userUsernameInput) {
        this.elements.userUsernameInput.value = username;
        this.elements.userUsernameInput.disabled = mode === 'edit' && !permissions.canEditUsername;
      }

      const roleId = user?.role_id != null ? String(user.role_id) : '';
      this.populateRoleSelect(this.elements.userRoleSelect, mode === 'edit' ? roleId : '');
      if (this.elements.userRoleSelect) {
        this.elements.userRoleSelect.disabled = !permissions.canEditRole;
        if (mode === 'edit') {
          this.elements.userRoleSelect.value = roleId || this.elements.userRoleSelect.value;
        }
      }

      if (this.elements.userEmailInput) {
        this.elements.userEmailInput.value = user?.email || '';
        this.elements.userEmailInput.disabled = !permissions.canEditDetails;
      }
      if (this.elements.userDepartmentInput) {
        this.elements.userDepartmentInput.value = user?.department || '';
        this.elements.userDepartmentInput.disabled = !permissions.canEditDetails;
      }
      if (this.elements.userBirthDateInput) {
        this.elements.userBirthDateInput.value = user?.birth_date || '';
        this.elements.userBirthDateInput.disabled = !permissions.canEditDetails;
      }
      if (this.elements.userPhotoInput) {
        this.elements.userPhotoInput.value = user?.photo || '';
        this.elements.userPhotoInput.disabled = !permissions.canEditDetails;
      }

      if (this.elements.userPasswordInput) {
        this.elements.userPasswordInput.value = '';
        if (mode === 'create') {
          this.elements.userPasswordInput.placeholder = 'Придумайте пароль';
          this.elements.userPasswordInput.required = true;
          this.elements.userPasswordInput.disabled = false;
        } else {
          this.elements.userPasswordInput.placeholder = 'Оставьте пустым, чтобы не менять';
          this.elements.userPasswordInput.required = false;
          this.elements.userPasswordInput.disabled = !permissions.canEditPassword;
        }
      }

      this.resetModalPasswordDisplay();
      this.setPasswordBlockVisible(mode === 'edit' && permissions.canViewPassword);
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.disabled = !(mode === 'edit' && permissions.canViewPassword);
      }

      if (this.elements.userRegistrationInput) {
        this.elements.userRegistrationInput.value =
          mode === 'edit'
            ? this.formatRegistrationDate(user?.registration_date) || '—'
            : 'Будет назначена автоматически';
      }

      const phones = mode === 'edit' ? user?.phones || [] : [];
      this.renderPhoneList(phones, permissions.canEditDetails);

      if (this.modalInstance) {
        this.modalInstance.show();
      } else if (this.elements.userModal) {
        this.elements.userModal.classList.add('show');
        this.elements.userModal.style.display = 'block';
        this.elements.userModal.removeAttribute('aria-hidden');
      }
    }

    resolveModalPermissions(mode, user) {
      if (mode === 'create') {
        return {
          canEditUsername: true,
          canEditRole: this.capabilitiesEdit('user.role'),
          canEditPassword: true,
          canViewPassword: false,
          canEditDetails: true,
        };
      }
      return {
        canEditUsername: Boolean(user?.can_edit_username),
        canEditRole: Boolean(user?.can_edit_role),
        canEditPassword: Boolean(user?.can_edit_password),
        canViewPassword: Boolean(user?.can_view_password),
        canEditDetails: Boolean(user?.is_self || user?.can_edit_username),
      };
    }

    populateRoleSelect(select, currentValue) {
      if (!select) {
        return;
      }
      const roles = this.state.roles || [];
      const targetValue = currentValue != null ? String(currentValue) : '';
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
      const desiredValue = targetValue
        ? targetValue
        : preferred && preferred.id != null
          ? String(preferred.id)
          : '';
      const values = Array.from(select.options).map((option) => option.value);
      select.value = values.includes(desiredValue) ? desiredValue : '';
    }

    setUserModalError(message) {
      const box = this.elements.userModalError;
      if (!box) {
        return;
      }
      if (!message) {
        box.classList.add('d-none');
        box.textContent = '';
      } else {
        box.textContent = message;
        box.classList.remove('d-none');
      }
    }

    formatRegistrationDate(value) {
      if (!value) {
        return '';
      }
      const normalized = value.includes('T') ? value : value.replace(' ', 'T');
      const date = new Date(normalized);
      if (Number.isNaN(date.getTime())) {
        return value;
      }
      return date.toLocaleString();
    }

    renderPhoneList(phones, editable) {
      const container = this.elements.userPhonesContainer;
      if (!container) {
        return;
      }
      container.innerHTML = '';
      const list = Array.isArray(phones) ? phones : [];
      list.forEach((phone) => {
        container.appendChild(this.createPhoneRow(phone, editable));
      });
      this.togglePhonesEmpty(list.length === 0);
      if (this.elements.userPhoneAddButton) {
        this.elements.userPhoneAddButton.disabled = !editable;
      }
    }

    createPhoneRow(phone, editable) {
      const row = document.createElement('div');
      row.className = 'row g-2 align-items-end';
      row.dataset.authUserPhoneRow = 'true';

      const typeCol = document.createElement('div');
      typeCol.className = 'col-12 col-sm-5';
      const typeLabel = document.createElement('label');
      typeLabel.className = 'form-label small mb-1';
      typeLabel.textContent = 'Тип';
      const typeInput = document.createElement('input');
      typeInput.type = 'text';
      typeInput.className = 'form-control form-control-sm';
      typeInput.name = 'phone_type';
      typeInput.placeholder = 'Например, мобильный';
      typeInput.value = String(phone?.type || phone?.label || '').trim();
      typeInput.disabled = !editable;
      typeCol.appendChild(typeLabel);
      typeCol.appendChild(typeInput);

      const valueCol = document.createElement('div');
      valueCol.className = 'col-12 col-sm-6';
      const valueLabel = document.createElement('label');
      valueLabel.className = 'form-label small mb-1';
      valueLabel.textContent = 'Номер';
      const valueInput = document.createElement('input');
      valueInput.type = 'text';
      valueInput.className = 'form-control form-control-sm';
      valueInput.name = 'phone_value';
      valueInput.placeholder = '+7 (999) 000-00-00';
      valueInput.value = String(phone?.value || phone?.number || '').trim();
      valueInput.disabled = !editable;
      valueCol.appendChild(valueLabel);
      valueCol.appendChild(valueInput);

      const removeCol = document.createElement('div');
      removeCol.className = 'col-12 col-sm-1 d-flex align-items-end';
      const removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn btn-outline-danger btn-sm w-100';
      removeBtn.dataset.action = 'remove-phone';
      removeBtn.innerHTML = '<i class="bi bi-trash"></i>';
      removeBtn.disabled = !editable;
      removeCol.appendChild(removeBtn);

      row.appendChild(typeCol);
      row.appendChild(valueCol);
      row.appendChild(removeCol);
      return row;
    }

    togglePhonesEmpty(isEmpty) {
      if (!this.elements.userPhonesEmpty) {
        return;
      }
      this.elements.userPhonesEmpty.classList.toggle('d-none', !isEmpty);
    }

    handleAddPhone(event) {
      event.preventDefault();
      if (!this.modalState?.permissions?.canEditDetails) {
        return;
      }
      if (!this.elements.userPhonesContainer) {
        return;
      }
      this.elements.userPhonesContainer.appendChild(this.createPhoneRow({ type: '', value: '' }, true));
      this.togglePhonesEmpty(false);
    }

    handlePhoneListClick(event) {
      const button = event.target.closest('[data-action="remove-phone"]');
      if (!button) {
        return;
      }
      event.preventDefault();
      if (!this.modalState?.permissions?.canEditDetails) {
        return;
      }
      const row = button.closest('[data-auth-user-phone-row]');
      if (row && row.parentElement) {
        row.parentElement.removeChild(row);
      }
      if (this.elements.userPhonesContainer && this.elements.userPhonesContainer.children.length === 0) {
        this.togglePhonesEmpty(true);
      }
    }

    collectPhonePayload() {
      const container = this.elements.userPhonesContainer;
      if (!container) {
        return [];
      }
      return Array.from(container.querySelectorAll('[data-auth-user-phone-row]'))
        .map((row) => {
          const typeInput = row.querySelector('[name="phone_type"]');
          const valueInput = row.querySelector('[name="phone_value"]');
          const number = valueInput ? valueInput.value.trim() : '';
          if (!number) {
            return null;
          }
          const label = typeInput ? typeInput.value.trim() : '';
          return { type: label, value: number };
        })
        .filter(Boolean);
    }

    setPasswordBlockVisible(visible) {
      if (this.elements.userPasswordBlock) {
        this.elements.userPasswordBlock.classList.toggle('d-none', !visible);
      }
    }

    resetModalPasswordDisplay() {
      if (this.elements.userPasswordDisplay) {
        this.elements.userPasswordDisplay.type = 'password';
        this.elements.userPasswordDisplay.value = PASSWORD_PLACEHOLDER;
      }
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.textContent = 'Показать';
      }
      this.modalPasswordState.visible = false;
    }

    showModalPassword(password) {
      if (this.elements.userPasswordDisplay) {
        this.elements.userPasswordDisplay.type = 'text';
        this.elements.userPasswordDisplay.value = password;
      }
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.textContent = 'Скрыть';
      }
      this.modalPasswordState.visible = true;
      this.modalPasswordState.loaded = true;
      this.modalPasswordState.value = password;
    }

    hideModalPassword() {
      this.resetModalPasswordDisplay();
    }

    handleModalPasswordToggle(event) {
      if (event) {
        event.preventDefault();
      }
      if (!this.modalState || this.modalState.mode !== 'edit') {
        return;
      }
      if (!this.modalState.permissions?.canViewPassword) {
        return;
      }
      if (this.modalPasswordState.visible) {
        this.hideModalPassword();
        return;
      }
      if (this.modalPasswordState.loaded) {
        this.showModalPassword(this.modalPasswordState.value || '');
        return;
      }
      const userId = this.modalState.userId;
      if (!userId) {
        return;
      }
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.disabled = true;
      }
      this.fetchUserPassword(userId)
        .then((password) => {
          this.showModalPassword(password || '');
        })
        .catch((error) => {
          this.setUserModalError(error.message || String(error));
        })
        .finally(() => {
          if (this.elements.userPasswordToggle) {
            this.elements.userPasswordToggle.disabled = false;
          }
        });
    }

    fetchUserPassword(userId) {
      return fetch(`${this.usersEndpoint}/${userId}/password`, { credentials: 'same-origin' })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Пароль недоступен');
          }
          return data.password || '';
        });
    }

    handleUserFormSubmit(event) {
      event.preventDefault();
      if (!this.modalState) {
        return;
      }
      const form = event.currentTarget;
      const submitButton = form.querySelector('[data-auth-user-submit]') || form.querySelector('button[type="submit"]');
      if (submitButton) {
        submitButton.disabled = true;
      }
      this.setUserModalError('');
      const { mode, original, permissions, userId } = this.modalState;
      const payload = {};
      const username = this.elements.userUsernameInput ? this.elements.userUsernameInput.value.trim() : '';
      if (!username) {
        this.setUserModalError('Укажите логин пользователя.');
        if (submitButton) {
          submitButton.disabled = false;
        }
        return;
      }

      if (mode === 'create' || (permissions.canEditUsername && username !== (original?.username || ''))) {
        payload.username = username;
      }

      const roleSelect = this.elements.userRoleSelect;
      const roleValue = roleSelect ? roleSelect.value.trim() : '';
      const originalRoleId = original?.role_id != null ? String(original.role_id) : '';
      if (mode === 'create') {
        if (roleValue) {
          payload.role_id = Number(roleValue);
        }
      } else if (permissions.canEditRole && roleValue !== originalRoleId) {
        payload.role_id = roleValue ? Number(roleValue) : null;
      }

      const email = this.elements.userEmailInput ? this.elements.userEmailInput.value.trim() : '';
      if (mode === 'create') {
        if (email) {
          payload.email = email;
        }
      } else if (permissions.canEditDetails && email !== (original?.email || '')) {
        payload.email = email || null;
      }

      const department = this.elements.userDepartmentInput ? this.elements.userDepartmentInput.value.trim() : '';
      if (mode === 'create') {
        if (department) {
          payload.department = department;
        }
      } else if (permissions.canEditDetails && department !== (original?.department || '')) {
        payload.department = department || null;
      }

      const birthDate = this.elements.userBirthDateInput ? this.elements.userBirthDateInput.value.trim() : '';
      if (mode === 'create') {
        if (birthDate) {
          payload.birth_date = birthDate;
        }
      } else if (permissions.canEditDetails && birthDate !== (original?.birth_date || '')) {
        payload.birth_date = birthDate || null;
      }

      const photo = this.elements.userPhotoInput ? this.elements.userPhotoInput.value.trim() : '';
      if (mode === 'create') {
        if (photo) {
          payload.photo = photo;
        }
      } else if (permissions.canEditDetails && photo !== (original?.photo || '')) {
        payload.photo = photo || null;
      }

      const phones = this.collectPhonePayload();
      if (mode === 'create') {
        if (phones.length) {
          payload.phones = phones;
        }
      } else if (permissions.canEditDetails) {
        const originalPhones = Array.isArray(original?.phones)
          ? original.phones.map((item) => ({
              type: String(item?.type || item?.label || '').trim(),
              value: String(item?.value || item?.number || '').trim(),
            }))
          : [];
        const currentPhones = phones.map((item) => ({
          type: String(item.type || '').trim(),
          value: String(item.value || '').trim(),
        }));
        if (JSON.stringify(currentPhones) !== JSON.stringify(originalPhones)) {
          payload.phones = phones;
        }
      }

      const password = this.elements.userPasswordInput ? this.elements.userPasswordInput.value.trim() : '';
      if (mode === 'create') {
        if (!password) {
          this.setUserModalError('Укажите пароль для нового пользователя.');
          if (submitButton) {
            submitButton.disabled = false;
          }
          return;
        }
        payload.password = password;
      } else if (password) {
        if (!permissions.canEditPassword) {
          this.setUserModalError('У вас нет прав на изменение пароля.');
          if (submitButton) {
            submitButton.disabled = false;
          }
          return;
        }
        payload.password = password;
      }

      if (mode === 'edit' && Object.keys(payload).length === 0) {
        this.setMessage('Нет изменений для сохранения.', 'info');
        if (submitButton) {
          submitButton.disabled = false;
        }
        return;
      }

      const requestUrl = mode === 'create' ? this.usersEndpoint : `${this.usersEndpoint}/${userId}`;
      const method = mode === 'create' ? 'POST' : 'PUT';

      fetch(requestUrl, {
        method,
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(
              data.error || (mode === 'create' ? 'Не удалось создать пользователя' : 'Не удалось сохранить пользователя')
            );
          }
          this.closeUserModal();
          this.setMessage(mode === 'create' ? 'Пользователь создан.' : 'Данные пользователя обновлены.', 'success');
          return this.refresh();
        })
        .catch((error) => {
          this.setUserModalError(error.message || String(error));
        })
        .finally(() => {
          if (submitButton) {
            submitButton.disabled = false;
          }
        });
    }

    handleDeleteUser(row, userId) {
      const username = row.dataset.userName || '';
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

    closeUserModal() {
      if (this.modalInstance) {
        this.modalInstance.hide();
      } else if (this.elements.userModal) {
        this.elements.userModal.classList.remove('show');
        this.elements.userModal.style.display = 'none';
        this.elements.userModal.setAttribute('aria-hidden', 'true');
        this.handleUserModalHidden();
      }
    }

    handleUserModalHidden() {
      this.modalState = null;
      this.modalPasswordState = { visible: false, loaded: false, value: '' };
      if (this.elements.userForm) {
        this.elements.userForm.reset();
        delete this.elements.userForm.dataset.mode;
      }
      if (this.elements.userRegistrationInput) {
        this.elements.userRegistrationInput.value = '';
      }
      this.renderPhoneList([], true);
      this.setPasswordBlockVisible(false);
      this.resetModalPasswordDisplay();
      this.setUserModalError('');
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

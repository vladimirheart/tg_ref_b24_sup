(() => {
  const PASSWORD_PLACEHOLDER = '••••••••';
  const PERMISSION_WILDCARD = '*';
  const DEFAULT_PHONE_TYPES = ['Рабочий', 'Личный', 'Дополнительный'];
  const DEFAULT_PHONE_PLACEHOLDER = '+7 (999) 000-00-00';

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
        orgStructure: { nodes: [] },
      };
      this.photoUploadEndpoint = root.dataset.authPhotoUploadEndpoint || '/api/users/photo-upload';
      this.photoUploadState = { value: '', previewUrl: '', tempUrl: '' };
      this.photoUploadDisabled = false;
      this.photoUploadUploading = false;
      const documentRoot = typeof document === 'undefined' ? null : document;
      this.documentRoot = documentRoot;
      const resolveFrom = (scope, selector) => scope?.querySelector(selector) || null;
      const userModal =
        resolveFrom(root, '[data-auth-user-modal]') ||
        resolveFrom(documentRoot, '[data-auth-user-modal]');
      const orgMembersModal =
        resolveFrom(root, '[data-org-members-modal]') ||
        resolveFrom(documentRoot, '[data-org-members-modal]');

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
        userFullNameInput: resolveFrom(userModal, '[name="full_name"]'),
        userEmailInput: resolveFrom(userModal, '[name="email"]'),
        userDepartmentInput: resolveFrom(userModal, '[data-auth-user-department-select]'),
        userDepartmentSearch: resolveFrom(userModal, '[data-auth-user-department-search]'),
        userBirthDateInput: resolveFrom(userModal, '[name="birth_date"]'),
        userPhotoInput: resolveFrom(userModal, '[data-auth-user-photo-value]'),
        userPhotoFileInput: resolveFrom(userModal, '[data-auth-user-photo-file]'),
        userPhotoPreview: resolveFrom(userModal, '[data-auth-user-photo-preview]'),
        userPhotoPreviewImage: resolveFrom(userModal, '[data-auth-user-photo-img]'),
        userPhotoPlaceholder: resolveFrom(userModal, '[data-auth-user-photo-placeholder]'),
        userPhotoStatus: resolveFrom(userModal, '[data-auth-user-photo-status]'),
        userPhotoDropzone: resolveFrom(userModal, '[data-auth-user-photo-dropzone]'),
        userPhotoTrigger: resolveFrom(userModal, '[data-auth-user-photo-trigger]'),
        userPhotoChangeButton: resolveFrom(userModal, '[data-auth-user-photo-change]'),
        userPhotoRemoveButton: resolveFrom(userModal, '[data-auth-user-photo-remove]'),
        userPhotoUploadingIndicator: resolveFrom(userModal, '[data-auth-user-photo-uploading]'),
        userPhotoUploader: resolveFrom(userModal, '[data-auth-user-photo-uploader]'),
        userPasswordConfirmInput: resolveFrom(userModal, '[data-auth-user-password-confirm]'),
        userPasswordCreateSection: resolveFrom(userModal, '[data-auth-user-password-create]'),
        userPasswordChangeWrapper: resolveFrom(userModal, '[data-auth-user-password-change-wrapper]'),
        userPasswordChangeButton: resolveFrom(userModal, '[data-auth-user-password-change]'),
        userPhotoViewerModal:
          resolveFrom(userModal, '[data-auth-user-photo-viewer-modal]') ||
          resolveFrom(documentRoot, '[data-auth-user-photo-viewer-modal]'),
        userPhotoViewerImage:
          resolveFrom(userModal, '[data-auth-user-photo-viewer-img]') ||
          resolveFrom(documentRoot, '[data-auth-user-photo-viewer-img]'),
        userPhotoViewerDownload:
          resolveFrom(userModal, '[data-auth-user-photo-download]') ||
          resolveFrom(documentRoot, '[data-auth-user-photo-download]'),
        userPasswordModal: resolveFrom(documentRoot, '[data-auth-user-password-modal]'),
        userPasswordModalForm: resolveFrom(documentRoot, '[data-auth-user-password-form]'),
        userPasswordModalError: resolveFrom(documentRoot, '[data-auth-user-password-error]'),
        userPasswordModalInput: resolveFrom(documentRoot, '[data-auth-user-password-new]'),
        userPasswordModalConfirmInput: resolveFrom(documentRoot, '[data-auth-user-password-confirm-new]'),
        userPasswordModalSubmit: resolveFrom(documentRoot, '[data-auth-user-password-submit]'),
        orgSection: resolveFrom(root, '[data-org-structure-section]'),
        orgTree: resolveFrom(root, '[data-org-tree]'),
        orgTreeWrapper: resolveFrom(root, '[data-org-tree-wrapper]'),
        orgEmpty: resolveFrom(root, '[data-org-empty]'),
        orgSaveButton: resolveFrom(root, '[data-org-save]'),
        orgMembersModal,
        orgMembersForm: resolveFrom(orgMembersModal, '[data-org-members-form]'),
        orgMembersList: resolveFrom(orgMembersModal, '[data-org-members-list]'),
        orgMembersTitle: resolveFrom(orgMembersModal, '[data-org-members-title]'),
        orgMembersDescription: resolveFrom(orgMembersModal, '[data-org-members-description]'),
        orgMembersEmpty: resolveFrom(orgMembersModal, '[data-org-members-empty]'),
      };
      this.orgSaveButtonDefaultText = this.elements.orgSaveButton
        ? this.elements.orgSaveButton.textContent.trim()
        : '';
      this.orgStructureDirty = false;
      this.orgStructureSaving = false;
      this.orgCollapseState = {};
      this.orgMembersModalState = { nodeId: null };
      this.orgMembersModalInstance = null;
      this.photoViewerModalInstance = null;
      this.departmentOptions = [];
      this.customDepartmentOptions = new Set();
      this.departmentSearchTerm = '';
      this.inputSettings = typeof window !== 'undefined' ? window.__inputSettings || {} : {};
      this.handleUsersClick = this.handleUsersClick.bind(this);
      this.handleRolesClick = this.handleRolesClick.bind(this);
      this.handlePermissionToggle = this.handlePermissionToggle.bind(this);
      this.handleUserFormSubmit = this.handleUserFormSubmit.bind(this);
      this.handleCreateRole = this.handleCreateRole.bind(this);
      this.handleCreateUserTrigger = this.handleCreateUserTrigger.bind(this);
      this.handleAddPhone = this.handleAddPhone.bind(this);
      this.handlePhoneListClick = this.handlePhoneListClick.bind(this);
      this.handlePhoneValueInput = this.handlePhoneValueInput.bind(this);
      this.handleModalPasswordToggle = this.handleModalPasswordToggle.bind(this);
      this.handleUserModalHidden = this.handleUserModalHidden.bind(this);
      this.handlePhotoFileChange = this.handlePhotoFileChange.bind(this);
      this.handlePhotoDropzoneClick = this.handlePhotoDropzoneClick.bind(this);
      this.handlePhotoDrop = this.handlePhotoDrop.bind(this);
      this.handlePhotoDragOver = this.handlePhotoDragOver.bind(this);
      this.handlePhotoDragLeave = this.handlePhotoDragLeave.bind(this);
      this.handlePhotoRemove = this.handlePhotoRemove.bind(this);
      this.handlePhotoPreviewClick = this.handlePhotoPreviewClick.bind(this);
      this.handlePhotoPreviewKeydown = this.handlePhotoPreviewKeydown.bind(this);
      this.handlePasswordChangeTrigger = this.handlePasswordChangeTrigger.bind(this);
      this.handlePasswordModalSubmit = this.handlePasswordModalSubmit.bind(this);
      this.handlePasswordModalHidden = this.handlePasswordModalHidden.bind(this);
      this.handleOrgSectionClick = this.handleOrgSectionClick.bind(this);
      this.handleOrgSaveClick = this.handleOrgSaveClick.bind(this);
      this.handleOrgMembersSubmit = this.handleOrgMembersSubmit.bind(this);
      this.handleInputSettingsChange = this.handleInputSettingsChange.bind(this);
      this.handleDepartmentSearchInput = this.handleDepartmentSearchInput.bind(this);
      this.modalState = null;
      this.modalPasswordState = { visible: false, loaded: false, value: '' };
      this.modalInstance = null;
      this.passwordModalInstance = null;
      this.passwordModalState = { userId: null };
      this.bootstrap = window.bootstrap || null;
    }

    init() {
      this.bindEvents();
      if (this.elements.userModal && this.bootstrap?.Modal) {
        this.modalInstance = this.bootstrap.Modal.getOrCreateInstance(this.elements.userModal);
      }
      if (this.elements.orgMembersModal && this.bootstrap?.Modal) {
        this.orgMembersModalInstance = this.bootstrap.Modal.getOrCreateInstance(
          this.elements.orgMembersModal,
        );
      }
      if (this.elements.userPhotoViewerModal && this.bootstrap?.Modal) {
        this.photoViewerModalInstance = this.bootstrap.Modal.getOrCreateInstance(
          this.elements.userPhotoViewerModal,
        );
      }
      if (this.elements.userPasswordModal && this.bootstrap?.Modal) {
        this.passwordModalInstance = this.bootstrap.Modal.getOrCreateInstance(
          this.elements.userPasswordModal,
        );
      }
      this.refreshPhoneControlsFromSettings();
      this.refreshDepartmentSelect();
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
        this.elements.userPhonesContainer.addEventListener('input', this.handlePhoneValueInput);
      }
      if (this.elements.userPasswordToggle) {
        this.elements.userPasswordToggle.addEventListener('click', this.handleModalPasswordToggle);
      }
      if (this.elements.userPasswordChangeButton) {
        this.elements.userPasswordChangeButton.addEventListener('click', this.handlePasswordChangeTrigger);
      }
      if (this.elements.userModal) {
        this.elements.userModal.addEventListener('hidden.bs.modal', this.handleUserModalHidden);
      }
      if (this.elements.userPhotoFileInput) {
        this.elements.userPhotoFileInput.addEventListener('change', this.handlePhotoFileChange);
      }
      if (this.elements.userPhotoTrigger) {
        this.elements.userPhotoTrigger.addEventListener('click', this.handlePhotoDropzoneClick);
      }
      if (this.elements.userPhotoChangeButton) {
        this.elements.userPhotoChangeButton.addEventListener('click', this.handlePhotoDropzoneClick);
      }
      if (this.elements.userPhotoRemoveButton) {
        this.elements.userPhotoRemoveButton.addEventListener('click', this.handlePhotoRemove);
      }
      if (this.elements.userPhotoPreview) {
        this.elements.userPhotoPreview.addEventListener('click', this.handlePhotoPreviewClick);
      }
      if (this.elements.userPhotoPreviewImage) {
        this.elements.userPhotoPreviewImage.addEventListener('keydown', this.handlePhotoPreviewKeydown);
      }
      if (this.elements.userPhotoDropzone) {
        const dropzone = this.elements.userPhotoDropzone;
        dropzone.addEventListener('click', this.handlePhotoDropzoneClick);
        ['dragenter', 'dragover'].forEach((eventName) =>
          dropzone.addEventListener(eventName, this.handlePhotoDragOver)
        );
        ['dragleave', 'dragend'].forEach((eventName) =>
          dropzone.addEventListener(eventName, this.handlePhotoDragLeave)
        );
        dropzone.addEventListener('drop', this.handlePhotoDrop);
      }
      if (this.elements.userDepartmentSearch) {
        this.elements.userDepartmentSearch.addEventListener('input', this.handleDepartmentSearchInput);
      }
      if (this.documentRoot) {
        this.documentRoot.addEventListener('inputSettings:change', this.handleInputSettingsChange);
      }
      if (this.elements.userPasswordModalForm) {
        this.elements.userPasswordModalForm.addEventListener('submit', this.handlePasswordModalSubmit);
      }
      if (this.elements.userPasswordModal) {
        this.elements.userPasswordModal.addEventListener('hidden.bs.modal', this.handlePasswordModalHidden);
      }
      if (this.elements.orgSection) {
        this.elements.orgSection.addEventListener('click', this.handleOrgSectionClick);
      }
      if (this.elements.orgSaveButton) {
        this.elements.orgSaveButton.addEventListener('click', this.handleOrgSaveClick);
      }
      if (this.elements.orgMembersForm) {
        this.elements.orgMembersForm.addEventListener('submit', this.handleOrgMembersSubmit);
      }
      if (this.elements.orgMembersModal) {
        this.elements.orgMembersModal.addEventListener('hidden.bs.modal', () => {
          this.orgMembersModalState = { nodeId: null };
        });
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
          this.state.orgStructure = this.normalizeOrgStructure(data.org_structure);
          this.orgStructureDirty = false;
          this.orgStructureSaving = false;
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
      this.renderOrgStructure();
      this.updateOrgStructureControls();
      this.updateCreateControls();
      this.updateDepartmentOptionsFromOrgStructure();
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
      const displayName = (user.full_name || user.username || '').trim();
      row.dataset.userId = String(user.id);
      row.dataset.userName = displayName || '';

      const usernameCell = document.createElement('td');
      const userWrapper = document.createElement('div');
      userWrapper.className = 'd-flex align-items-center gap-3';
      const avatar = this.createUserAvatar(user);
      userWrapper.appendChild(avatar);
      const userInfo = document.createElement('div');
      userInfo.className = 'd-flex flex-column';
      const usernameTitle = document.createElement('div');
      usernameTitle.className = 'fw-semibold';
      usernameTitle.textContent = user.username || '—';
      userInfo.appendChild(usernameTitle);
      if (user.full_name) {
        const fullNameRow = document.createElement('div');
        fullNameRow.className = 'text-muted small';
        fullNameRow.textContent = user.full_name;
        userInfo.appendChild(fullNameRow);
      }
      if (user.department) {
        const departmentRow = document.createElement('div');
        departmentRow.className = 'text-muted small';
        departmentRow.textContent = user.department;
        userInfo.appendChild(departmentRow);
      }
      userWrapper.appendChild(userInfo);
      usernameCell.appendChild(userWrapper);

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

    createUserAvatar(user) {
      const container = document.createElement('div');
      container.className = 'auth-user-avatar';
      const displayName = (user?.full_name || user?.username || '').trim();
      const photoUrl = String(user?.photo || '').trim();
      if (photoUrl) {
        container.classList.add('auth-user-avatar--image');
        const img = document.createElement('img');
        img.src = photoUrl;
        img.alt = displayName ? `Фото ${displayName}` : 'Фото пользователя';
        img.loading = 'lazy';
        container.appendChild(img);
      } else {
        container.classList.add('auth-user-avatar--placeholder');
        const initials = document.createElement('span');
        initials.className = 'auth-user-avatar__initials';
        initials.textContent = this.getUserInitials(displayName || '');
        container.appendChild(initials);
      }
      return container;
    }

    getUserInitials(name) {
      const value = String(name || '').trim();
      if (!value) {
        return '•';
      }
      const parts = value.split(/\s+/).filter(Boolean);
      if (parts.length === 0) {
        return value.slice(0, 2).toUpperCase();
      }
      if (parts.length === 1) {
        return parts[0].slice(0, 2).toUpperCase();
      }
      return parts
        .slice(0, 2)
        .map((part) => part.charAt(0))
        .join('')
        .toUpperCase();
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

    resetPhotoUploader(value = '', canEdit = true) {
      if (this.photoUploadState?.tempUrl && this.photoUploadState.tempUrl.startsWith('blob:')) {
        URL.revokeObjectURL(this.photoUploadState.tempUrl);
      }
      const cleanValue = String(value || '').trim();
      this.photoUploadState = { value: cleanValue, previewUrl: cleanValue, tempUrl: '' };
      if (this.elements.userPhotoInput) {
        this.elements.userPhotoInput.value = cleanValue;
        this.elements.userPhotoInput.disabled = !canEdit;
      }
      if (this.elements.userPhotoFileInput) {
        this.elements.userPhotoFileInput.value = '';
        this.elements.userPhotoFileInput.disabled = !canEdit;
      }
      this.applyPhotoPreview(cleanValue, { temporary: false });
      this.setPhotoStatus('');
      this.setPhotoUploading(false);
      this.setPhotoUploadDisabled(!canEdit);
    }

    setPhotoValue(value) {
      const cleanValue = String(value || '').trim();
      this.photoUploadState.value = cleanValue;
      this.photoUploadState.previewUrl = cleanValue;
      if (this.elements.userPhotoInput) {
        this.elements.userPhotoInput.value = cleanValue;
      }
      this.applyPhotoPreview(cleanValue, { temporary: false });
    }

    applyPhotoPreview(url, { temporary = false } = {}) {
      const nextUrl = String(url || '').trim();
      if (temporary) {
        if (this.photoUploadState.tempUrl && this.photoUploadState.tempUrl.startsWith('blob:') && this.photoUploadState.tempUrl !== nextUrl) {
          URL.revokeObjectURL(this.photoUploadState.tempUrl);
        }
        this.photoUploadState.tempUrl = nextUrl;
      } else {
        if (this.photoUploadState.tempUrl && this.photoUploadState.tempUrl.startsWith('blob:')) {
          URL.revokeObjectURL(this.photoUploadState.tempUrl);
        }
        this.photoUploadState.tempUrl = '';
        this.photoUploadState.previewUrl = nextUrl;
      }
      const effectiveUrl = (this.photoUploadState.tempUrl || this.photoUploadState.previewUrl || '').trim();
      const hasPreview = Boolean(effectiveUrl);
      if (this.elements.userPhotoPreviewImage) {
        if (hasPreview) {
          this.elements.userPhotoPreviewImage.src = effectiveUrl;
          this.elements.userPhotoPreviewImage.alt = 'Фото пользователя';
        } else {
          this.elements.userPhotoPreviewImage.removeAttribute('src');
          this.elements.userPhotoPreviewImage.alt = 'Фото пользователя';
        }
      }
      if (this.elements.userPhotoPreview) {
        this.elements.userPhotoPreview.classList.toggle('d-none', !hasPreview);
      }
      if (this.elements.userPhotoPlaceholder) {
        this.elements.userPhotoPlaceholder.classList.toggle('d-none', hasPreview);
      }
      if (this.elements.userPhotoDropzone) {
        this.elements.userPhotoDropzone.classList.toggle('has-photo', hasPreview);
        this.elements.userPhotoDropzone.classList.remove('is-dragover');
      }
      if (this.elements.userPhotoViewerDownload && !hasPreview) {
        this.elements.userPhotoViewerDownload.href = '#';
        this.elements.userPhotoViewerDownload.classList.add('disabled');
        this.elements.userPhotoViewerDownload.removeAttribute('download');
      }
      this.updatePhotoControls();
    }

    setPhotoStatus(message, variant = 'muted') {
      const status = this.elements.userPhotoStatus;
      if (!status) {
        return;
      }
      const text = String(message || '').trim();
      status.textContent = text;
      status.classList.remove('text-danger', 'text-success', 'text-muted');
      if (!text) {
        status.classList.add('text-muted');
        return;
      }
      const variantClass = variant === 'danger' ? 'text-danger' : variant === 'success' ? 'text-success' : 'text-muted';
      status.classList.add(variantClass);
    }

    setPhotoUploadDisabled(disabled) {
      this.photoUploadDisabled = Boolean(disabled);
      const dropzone = this.elements.userPhotoDropzone;
      if (dropzone) {
        dropzone.classList.toggle('is-disabled', this.photoUploadDisabled);
        if (this.photoUploadDisabled) {
          dropzone.classList.remove('is-dragover');
        }
      }
      if (this.elements.userPhotoTrigger) {
        this.elements.userPhotoTrigger.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      if (this.elements.userPhotoChangeButton) {
        this.elements.userPhotoChangeButton.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      if (this.elements.userPhotoRemoveButton) {
        this.elements.userPhotoRemoveButton.disabled = this.photoUploadDisabled || !this.photoUploadState.value;
      }
      if (this.elements.userPhotoFileInput) {
        this.elements.userPhotoFileInput.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      this.updatePhotoControls();
    }

    setPhotoUploading(isUploading) {
      this.photoUploadUploading = Boolean(isUploading);
      const dropzone = this.elements.userPhotoDropzone;
      if (dropzone) {
        dropzone.classList.toggle('is-uploading', this.photoUploadUploading);
      }
      if (this.elements.userPhotoTrigger) {
        this.elements.userPhotoTrigger.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      if (this.elements.userPhotoChangeButton) {
        this.elements.userPhotoChangeButton.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      if (this.elements.userPhotoRemoveButton) {
        this.elements.userPhotoRemoveButton.disabled = this.photoUploadDisabled || (!this.photoUploadState.value && !this.photoUploadState.tempUrl) || this.photoUploadUploading;
      }
      if (this.elements.userPhotoUploadingIndicator) {
        this.elements.userPhotoUploadingIndicator.classList.toggle('d-none', !this.photoUploadUploading);
      }
      if (this.elements.userPhotoFileInput) {
        this.elements.userPhotoFileInput.disabled = this.photoUploadDisabled || this.photoUploadUploading;
      }
      this.updatePhotoControls();
    }

    updatePhotoControls() {
      if (this.elements.userPhotoRemoveButton) {
        const hasValue = Boolean((this.photoUploadState.value || this.photoUploadState.tempUrl || '').trim());
        this.elements.userPhotoRemoveButton.disabled = this.photoUploadDisabled || !hasValue || this.photoUploadUploading;
      }
    }

    isPhotoInteractionLocked() {
      return this.photoUploadDisabled || this.photoUploadUploading;
    }

    handlePhotoDropzoneClick(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      if (event?.target?.closest('[data-auth-user-photo-remove]')) {
        return;
      }
      if (event?.target?.closest('[data-auth-user-photo-preview-trigger]')) {
        return;
      }
      if (this.isPhotoInteractionLocked()) {
        return;
      }
      const dropzone = this.elements.userPhotoDropzone;
      const hasPhoto = dropzone ? dropzone.classList.contains('has-photo') : false;
      const clickedTrigger = event?.target?.closest('[data-auth-user-photo-trigger]');
      const clickedChange = event?.target?.closest('[data-auth-user-photo-change]');
      if (hasPhoto && !clickedTrigger && !clickedChange) {
        return;
      }
      if (this.elements.userPhotoFileInput) {
        this.elements.userPhotoFileInput.click();
      }
    }

    handlePhotoFileChange(event) {
      const files = event?.target?.files;
      if (!files || !files.length) {
        return;
      }
      this.processPhotoFile(files[0]);
    }

    handlePhotoDrop(event) {
      event.preventDefault();
      event.stopPropagation();
      if (this.elements.userPhotoDropzone) {
        this.elements.userPhotoDropzone.classList.remove('is-dragover');
      }
      if (this.isPhotoInteractionLocked()) {
        return;
      }
      let file = null;
      const items = event.dataTransfer?.items;
      if (items && items.length) {
        for (const item of items) {
          if (item.kind === 'file') {
            file = item.getAsFile();
            break;
          }
        }
      }
      if (!file) {
        const files = event.dataTransfer?.files;
        if (files && files.length) {
          file = files[0];
        }
      }
      if (file) {
        this.processPhotoFile(file);
      }
    }

    handlePhotoDragOver(event) {
      event.preventDefault();
      event.stopPropagation();
      if (!this.elements.userPhotoDropzone) {
        return;
      }
      if (this.isPhotoInteractionLocked()) {
        if (event.dataTransfer) {
          event.dataTransfer.dropEffect = 'none';
        }
        return;
      }
      this.elements.userPhotoDropzone.classList.add('is-dragover');
      if (event.dataTransfer) {
        event.dataTransfer.dropEffect = 'copy';
      }
    }

    handlePhotoDragLeave(event) {
      event.preventDefault();
      event.stopPropagation();
      if (this.elements.userPhotoDropzone) {
        this.elements.userPhotoDropzone.classList.remove('is-dragover');
      }
    }

    handlePhotoRemove(event) {
      if (event) {
        event.preventDefault();
        event.stopPropagation();
      }
      if (this.isPhotoInteractionLocked()) {
        return;
      }
      const hadValue = Boolean(this.photoUploadState.value || this.photoUploadState.previewUrl);
      this.setPhotoValue('');
      this.setPhotoStatus(hadValue ? 'Фото удалено.' : '');
    }

    processPhotoFile(file) {
      if (!file || this.isPhotoInteractionLocked()) {
        return;
      }
      const errorMessage = this.validatePhotoFile(file);
      if (errorMessage) {
        this.setPhotoStatus(errorMessage, 'danger');
        return;
      }
      const previousValue = this.photoUploadState.value;
      const previousPreview = this.photoUploadState.previewUrl;
      const objectUrl = URL.createObjectURL(file);
      this.applyPhotoPreview(objectUrl, { temporary: true });
      this.setPhotoStatus('Загружаем файл…', 'muted');
      this.setPhotoUploading(true);
      this.uploadUserPhoto(file)
        .then((data) => {
          const url = String(data?.url || data?.photo || data?.path || '').trim();
          if (!url) {
            throw new Error('Сервер не вернул ссылку на фото.');
          }
          this.setPhotoValue(url);
          this.setPhotoStatus('Фото обновлено.', 'success');
        })
        .catch((error) => {
          this.applyPhotoPreview('', { temporary: true });
          this.applyPhotoPreview(previousPreview, { temporary: false });
          this.photoUploadState.value = previousValue;
          this.photoUploadState.previewUrl = previousPreview;
          if (this.elements.userPhotoInput) {
            this.elements.userPhotoInput.value = previousValue;
          }
          this.setPhotoStatus(error.message || String(error), 'danger');
        })
        .finally(() => {
          this.setPhotoUploading(false);
          if (this.elements.userPhotoFileInput) {
            this.elements.userPhotoFileInput.value = '';
          }
        });
    }

    validatePhotoFile(file) {
      const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
      const maxSize = 5 * 1024 * 1024;
      if (file.size > maxSize) {
        return 'Размер файла не должен превышать 5 МБ.';
      }
      if (file.type && !allowedTypes.includes(file.type)) {
        return 'Поддерживаются изображения в форматах PNG, JPG, GIF или WebP.';
      }
      const extension = (file.name || '').split('.').pop()?.toLowerCase();
      if (!extension) {
        return 'Выберите изображение в поддерживаемом формате.';
      }
      const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'webp'];
      if (!allowedExtensions.includes(extension)) {
        return 'Поддерживаются изображения в форматах PNG, JPG, GIF или WebP.';
      }
      return '';
    }

    uploadUserPhoto(file) {
      if (!this.photoUploadEndpoint) {
        return Promise.reject(new Error('Загрузка фотографий недоступна.'));
      }
      const formData = new FormData();
      formData.append('photo', file);
      return fetch(this.photoUploadEndpoint, {
        method: 'POST',
        credentials: 'same-origin',
        body: formData,
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data?.error || 'Не удалось загрузить фото');
          }
          return data;
        });
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

      if (this.elements.userFullNameInput) {
        const fullName = user?.full_name || '';
        this.elements.userFullNameInput.value = fullName;
        this.elements.userFullNameInput.disabled = mode === 'edit' && !permissions.canEditDetails;
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
        this.setDepartmentValue(user?.department || '');
        this.elements.userDepartmentInput.disabled = !permissions.canEditDetails;
      }
      if (this.elements.userDepartmentSearch) {
        this.elements.userDepartmentSearch.disabled = !permissions.canEditDetails;
      }
      if (this.elements.userBirthDateInput) {
        this.elements.userBirthDateInput.value = user?.birth_date || '';
        this.elements.userBirthDateInput.disabled = !permissions.canEditDetails;
      }
      this.resetPhotoUploader(user?.photo || '', permissions.canEditDetails);

      if (this.elements.userPasswordInput) {
        this.elements.userPasswordInput.value = '';
        this.elements.userPasswordInput.placeholder =
          mode === 'create' ? 'Придумайте пароль' : 'Смена выполняется через отдельное окно';
        this.elements.userPasswordInput.required = mode === 'create';
        this.elements.userPasswordInput.disabled = mode !== 'create';
      }
      if (this.elements.userPasswordConfirmInput) {
        this.elements.userPasswordConfirmInput.value = '';
        this.elements.userPasswordConfirmInput.placeholder =
          mode === 'create' ? 'Повторите пароль' : 'Смена выполняется через отдельное окно';
        this.elements.userPasswordConfirmInput.required = mode === 'create';
        this.elements.userPasswordConfirmInput.disabled = mode !== 'create';
      }

      if (this.elements.userPasswordCreateSection) {
        this.elements.userPasswordCreateSection.classList.toggle('d-none', mode !== 'create');
      }
      if (this.elements.userPasswordChangeWrapper) {
        const showChange = mode === 'edit' && permissions.canEditPassword;
        this.elements.userPasswordChangeWrapper.classList.toggle('d-none', !showChange);
        if (this.elements.userPasswordChangeButton) {
          this.elements.userPasswordChangeButton.disabled = !showChange;
        }
      } else if (this.elements.userPasswordChangeButton) {
        this.elements.userPasswordChangeButton.disabled = !(mode === 'edit' && permissions.canEditPassword);
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

    getPhoneSettings() {
      return this.inputSettings?.phone || {};
    }

    getPhoneTypes() {
      const settings = this.getPhoneSettings();
      if (Array.isArray(settings.types) && settings.types.length) {
        return settings.types;
      }
      return DEFAULT_PHONE_TYPES;
    }

    getPhoneFormat() {
      const settings = this.getPhoneSettings();
      const format = settings?.format || {};
      return {
        prefix: format.prefix || '',
        pattern: format.pattern || '',
        example: format.example || '',
      };
    }

    formatPhoneNumber(value) {
      const format = this.getPhoneFormat();
      const digits = String(value || '').replace(/\D+/g, '');
      const prefixDigits = (format.prefix || '').replace(/\D+/g, '');
      let cleanedDigits = digits;
      if (prefixDigits && cleanedDigits.startsWith(prefixDigits)) {
        cleanedDigits = cleanedDigits.slice(prefixDigits.length);
      }
      let result = format.prefix || '';
      let digitIndex = 0;
      const pattern = format.pattern || '';
      if (pattern) {
        for (let i = 0; i < pattern.length; i += 1) {
          const char = pattern[i];
          if (char === '#') {
            if (digitIndex < cleanedDigits.length) {
              result += cleanedDigits[digitIndex];
              digitIndex += 1;
            } else {
              break;
            }
          } else if (digitIndex < cleanedDigits.length) {
            result += char;
          }
        }
      }
      while (digitIndex < cleanedDigits.length) {
        result += cleanedDigits[digitIndex];
        digitIndex += 1;
      }
      if (!pattern && !format.prefix) {
        return digits;
      }
      return result.trim();
    }

    applyPhoneValueSettings(input) {
      if (!input) {
        return;
      }
      const format = this.getPhoneFormat();
      const placeholder = format.example || DEFAULT_PHONE_PLACEHOLDER;
      input.placeholder = placeholder;
      const formatted = this.formatPhoneNumber(input.value);
      if (formatted !== input.value) {
        input.value = formatted;
      }
    }

    populatePhoneTypeSelect(select, selectedValue) {
      if (!select) {
        return;
      }
      const types = this.getPhoneTypes();
      const normalizedSelected = (selectedValue || '').trim();
      const normalizedSelectedKey = normalizedSelected.toLowerCase();
      select.innerHTML = '';
      const placeholderOption = document.createElement('option');
      placeholderOption.value = '';
      placeholderOption.textContent = 'Не выбран';
      select.appendChild(placeholderOption);
      const seen = new Set();
      types.forEach((type) => {
        const label = String(type || '').trim();
        if (!label) {
          return;
        }
        const key = label.toLowerCase();
        if (seen.has(key)) {
          return;
        }
        seen.add(key);
        const option = document.createElement('option');
        option.value = label;
        option.textContent = label;
        select.appendChild(option);
      });
      if (normalizedSelected && !seen.has(normalizedSelectedKey)) {
        const customOption = document.createElement('option');
        customOption.value = normalizedSelected;
        customOption.textContent = normalizedSelected;
        select.appendChild(customOption);
      }
      select.value = normalizedSelected;
    }

    refreshPhoneControlsFromSettings() {
      if (this.elements.userPhonesContainer) {
        const typeSelects = this.elements.userPhonesContainer.querySelectorAll('[data-auth-user-phone-type]');
        typeSelects.forEach((select) => {
          this.populatePhoneTypeSelect(select, select.value);
        });
        const valueInputs = this.elements.userPhonesContainer.querySelectorAll('[data-auth-user-phone-input]');
        valueInputs.forEach((input) => {
          this.applyPhoneValueSettings(input);
        });
      }
    }

    handlePhoneValueInput(event) {
      const input = event.target.closest('[data-auth-user-phone-input]');
      if (!input) {
        return;
      }
      if (!this.modalState?.permissions?.canEditDetails) {
        return;
      }
      const formatted = this.formatPhoneNumber(input.value);
      if (formatted !== input.value) {
        input.value = formatted;
      }
    }

    handleInputSettingsChange(event) {
      const detail = event?.detail && typeof event.detail === 'object' ? event.detail : {};
      this.inputSettings = detail;
      this.refreshPhoneControlsFromSettings();
    }

    handleDepartmentSearchInput(event) {
      if (!this.elements.userDepartmentSearch) {
        return;
      }
      this.departmentSearchTerm = event.target.value || '';
      this.refreshDepartmentSelect();
    }

    clearDepartmentSearch() {
      this.departmentSearchTerm = '';
      if (this.elements.userDepartmentSearch) {
        this.elements.userDepartmentSearch.value = '';
      }
    }

    buildDepartmentOptionList(nodes, prefix = []) {
      const result = [];
      const list = Array.isArray(nodes) ? nodes : [];
      list.forEach((node) => {
        if (!node) {
          return;
        }
        const name = String(node.name || '').trim() || 'Без названия';
        const pathParts = [...prefix, name];
        const value = pathParts.join(' / ');
        result.push({
          value,
          label: name,
          depth: prefix.length,
          search: pathParts.join(' ').toLowerCase(),
          pathParts,
        });
        if (Array.isArray(node.children) && node.children.length) {
          result.push(...this.buildDepartmentOptionList(node.children, pathParts));
        }
      });
      return result;
    }

    updateDepartmentOptionsFromOrgStructure() {
      const tree = this.buildOrgTree();
      this.departmentOptions = this.buildDepartmentOptionList(tree);
      const known = new Set(
        this.departmentOptions.map((option) => option.value.toLowerCase()),
      );
      const customValues = new Set(this.customDepartmentOptions);
      this.state.users.forEach((user) => {
        const department = String(user?.department || '').trim();
        if (department && !known.has(department.toLowerCase())) {
          customValues.add(department);
        }
      });
      this.customDepartmentOptions = customValues;
      this.refreshDepartmentSelect();
    }

    refreshDepartmentSelect(forcedValue) {
      const select = this.elements.userDepartmentInput;
      if (!select) {
        return;
      }
      if (
        this.elements.userDepartmentSearch &&
        this.elements.userDepartmentSearch.value !== this.departmentSearchTerm
      ) {
        this.elements.userDepartmentSearch.value = this.departmentSearchTerm;
      }
      const baseOptions = [...this.departmentOptions];
      this.customDepartmentOptions.forEach((value) => {
        const trimmed = String(value || '').trim();
        if (!trimmed) {
          return;
        }
        baseOptions.push({
          value: trimmed,
          label: trimmed,
          depth: 0,
          search: trimmed.toLowerCase(),
          pathParts: [trimmed],
          isCustom: true,
        });
      });
      const searchTerm = (this.departmentSearchTerm || '').trim().toLowerCase();
      let filtered = baseOptions.filter((option) => {
        if (!searchTerm) {
          return true;
        }
        return option.search.includes(searchTerm);
      });
      const selectedValue =
        typeof forcedValue === 'string' ? forcedValue : select.value || '';
      const selectedKey = selectedValue.trim().toLowerCase();
      if (selectedValue) {
        const hasSelected = filtered.some((option) => option.value.toLowerCase() === selectedKey);
        if (!hasSelected) {
          const match = baseOptions.find(
            (option) => option.value.toLowerCase() === selectedKey,
          )
            || baseOptions.find((option) => {
              const last = option.pathParts?.[option.pathParts.length - 1];
              return last && last.toLowerCase() === selectedKey;
            })
            || {
              value: selectedValue,
              label: selectedValue,
              depth: 0,
              search: selectedKey,
              pathParts: [selectedValue],
              isCustom: true,
            };
          filtered = [match, ...filtered];
        }
      }
      const seenValues = new Set();
      const uniqueOptions = [];
      filtered.forEach((option) => {
        const key = option.value.toLowerCase();
        if (seenValues.has(key)) {
          return;
        }
        seenValues.add(key);
        uniqueOptions.push(option);
      });
      select.innerHTML = '';
      const emptyOption = document.createElement('option');
      emptyOption.value = '';
      emptyOption.textContent = 'Не выбран';
      select.appendChild(emptyOption);
      uniqueOptions.forEach((option) => {
        const el = document.createElement('option');
        el.value = option.value;
        const indent = option.depth > 0 ? `${'\u00A0'.repeat(option.depth * 2)}↳ ` : '';
        el.textContent = `${indent}${option.label}`;
        el.title = option.pathParts ? option.pathParts.join(' / ') : option.label;
        select.appendChild(el);
      });
      select.value = selectedValue;
    }

    setDepartmentValue(value) {
      if (!this.elements.userDepartmentInput) {
        return;
      }
      const normalized = String(value || '').trim();
      if (normalized) {
        const exists = this.departmentOptions.some(
          (option) => option.value.toLowerCase() === normalized.toLowerCase(),
        );
        if (!exists) {
          this.customDepartmentOptions.add(normalized);
        }
      }
      this.clearDepartmentSearch();
      this.refreshDepartmentSelect(normalized);
      this.elements.userDepartmentInput.value = normalized;
    }

    handlePhotoPreviewClick(event) {
      const trigger = event.target.closest('[data-auth-user-photo-preview-trigger]');
      if (!trigger) {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      this.openPhotoViewer();
    }

    handlePhotoPreviewKeydown(event) {
      if (event.key !== 'Enter' && event.key !== ' ') {
        return;
      }
      event.preventDefault();
      event.stopPropagation();
      this.openPhotoViewer();
    }

    openPhotoViewer() {
      const url = (this.photoUploadState?.tempUrl || this.photoUploadState?.previewUrl || '').trim();
      if (!url || !this.elements.userPhotoViewerImage) {
        return;
      }
      const username =
        (this.modalState?.mode === 'edit' && this.modalState?.original?.username) ||
        (this.elements.userUsernameInput ? this.elements.userUsernameInput.value : '') ||
        '';
      const fullName =
        (this.modalState?.mode === 'edit' && this.modalState?.original?.full_name) ||
        (this.elements.userFullNameInput ? this.elements.userFullNameInput.value : '') ||
        '';
      const displayName = fullName || username;
      const altText = displayName ? `Фото пользователя ${displayName}` : 'Фото пользователя';
      this.elements.userPhotoViewerImage.src = url;
      this.elements.userPhotoViewerImage.alt = altText;
      if (this.elements.userPhotoViewerDownload) {
        const sanitizedName = (displayName || 'user-photo').replace(/[^\w\-]+/g, '_');
        this.elements.userPhotoViewerDownload.href = url;
        this.elements.userPhotoViewerDownload.setAttribute('download', sanitizedName || 'user-photo');
        this.elements.userPhotoViewerDownload.classList.remove('disabled');
      }
      if (this.photoViewerModalInstance) {
        this.photoViewerModalInstance.show();
      } else if (this.elements.userPhotoViewerModal) {
        this.elements.userPhotoViewerModal.classList.add('show');
        this.elements.userPhotoViewerModal.style.display = 'block';
        this.elements.userPhotoViewerModal.removeAttribute('aria-hidden');
      }
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
      this.refreshPhoneControlsFromSettings();
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
      const typeSelect = document.createElement('select');
      typeSelect.className = 'form-select form-select-sm';
      typeSelect.name = 'phone_type';
      typeSelect.dataset.authUserPhoneType = 'true';
      const initialType = String(phone?.type || phone?.label || '').trim();
      this.populatePhoneTypeSelect(typeSelect, initialType);
      typeSelect.disabled = !editable;
      typeCol.appendChild(typeLabel);
      typeCol.appendChild(typeSelect);

      const valueCol = document.createElement('div');
      valueCol.className = 'col-12 col-sm-6';
      const valueLabel = document.createElement('label');
      valueLabel.className = 'form-label small mb-1';
      valueLabel.textContent = 'Номер';
      const valueInput = document.createElement('input');
      valueInput.type = 'text';
      valueInput.className = 'form-control form-control-sm';
      valueInput.name = 'phone_value';
      valueInput.autocomplete = 'off';
      valueInput.dataset.authUserPhoneInput = 'true';
      const initialNumber = String(phone?.value || phone?.number || '').trim();
      valueInput.value = initialNumber;
      this.applyPhoneValueSettings(valueInput);
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
      const defaultType = this.getPhoneTypes()[0] || '';
      this.elements.userPhonesContainer.appendChild(
        this.createPhoneRow({ type: defaultType, value: '' }, true),
      );
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

    resetPasswordModal() {
      if (this.elements.userPasswordModalForm) {
        this.elements.userPasswordModalForm.reset();
      }
      if (this.elements.userPasswordModalInput) {
        this.elements.userPasswordModalInput.value = '';
      }
      if (this.elements.userPasswordModalConfirmInput) {
        this.elements.userPasswordModalConfirmInput.value = '';
      }
      this.setPasswordModalMessage('');
      if (this.elements.userPasswordModalSubmit) {
        this.elements.userPasswordModalSubmit.disabled = false;
      }
    }

    setPasswordModalMessage(message, variant = 'danger') {
      const box = this.elements.userPasswordModalError;
      if (!box) {
        return;
      }
      box.classList.remove('alert-danger', 'alert-success');
      if (!message) {
        box.classList.add('d-none');
        box.textContent = '';
        return;
      }
      const variantClass = variant === 'success' ? 'alert-success' : 'alert-danger';
      box.textContent = message;
      box.classList.remove('d-none');
      box.classList.add(variantClass);
    }

    handlePasswordChangeTrigger(event) {
      event.preventDefault();
      if (!this.modalState || this.modalState.mode !== 'edit') {
        return;
      }
      const { userId, permissions } = this.modalState;
      if (!permissions?.canEditPassword) {
        this.setMessage('У вас нет прав на изменение пароля.', 'danger');
        return;
      }
      this.passwordModalState = { userId };
      this.resetPasswordModal();
      if (this.passwordModalInstance) {
        this.passwordModalInstance.show();
      } else if (this.elements.userPasswordModal) {
        this.elements.userPasswordModal.classList.add('show');
        this.elements.userPasswordModal.style.display = 'block';
        this.elements.userPasswordModal.removeAttribute('aria-hidden');
      }
    }

    handlePasswordModalSubmit(event) {
      event.preventDefault();
      if (!this.modalState || this.modalState.mode !== 'edit') {
        return;
      }
      const { userId } = this.modalState;
      if (!userId) {
        this.setPasswordModalMessage('Пользователь не найден.');
        return;
      }
      const form = event.currentTarget;
      const submitButton = this.elements.userPasswordModalSubmit || form.querySelector('button[type="submit"]');
      if (submitButton) {
        submitButton.disabled = true;
      }
      const newPassword = this.elements.userPasswordModalInput
        ? this.elements.userPasswordModalInput.value.trim()
        : '';
      const confirmPassword = this.elements.userPasswordModalConfirmInput
        ? this.elements.userPasswordModalConfirmInput.value.trim()
        : '';
      if (!newPassword || !confirmPassword) {
        this.setPasswordModalMessage('Укажите новый пароль и подтверждение.');
        if (submitButton) {
          submitButton.disabled = false;
        }
        return;
      }
      if (newPassword !== confirmPassword) {
        this.setPasswordModalMessage('Пароли не совпадают.');
        if (submitButton) {
          submitButton.disabled = false;
        }
        return;
      }
      this.setPasswordModalMessage('');
      fetch(`${this.usersEndpoint}/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ password: newPassword }),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось обновить пароль');
          }
          this.setMessage('Пароль обновлён.', 'success');
          if (this.passwordModalInstance) {
            this.passwordModalInstance.hide();
          } else if (this.elements.userPasswordModal) {
            this.elements.userPasswordModal.classList.remove('show');
            this.elements.userPasswordModal.style.display = 'none';
            this.elements.userPasswordModal.setAttribute('aria-hidden', 'true');
            this.handlePasswordModalHidden();
          }
        })
        .catch((error) => {
          this.setPasswordModalMessage(error.message || String(error));
        })
        .finally(() => {
          if (submitButton) {
            submitButton.disabled = false;
          }
        });
    }

    handlePasswordModalHidden() {
      this.passwordModalState = { userId: null };
      this.resetPasswordModal();
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

      const fullName = this.elements.userFullNameInput ? this.elements.userFullNameInput.value.trim() : '';
      if (mode === 'create') {
        if (fullName) {
          payload.full_name = fullName;
        }
      } else if (permissions.canEditDetails && fullName !== (original?.full_name || '')) {
        payload.full_name = fullName || null;
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
      const passwordConfirm = this.elements.userPasswordConfirmInput
        ? this.elements.userPasswordConfirmInput.value.trim()
        : '';
      if (mode === 'create') {
        if (!password) {
          this.setUserModalError('Укажите пароль для нового пользователя.');
          if (submitButton) {
            submitButton.disabled = false;
          }
          return;
        }
        if (password !== passwordConfirm) {
          this.setUserModalError('Пароли не совпадают.');
          if (submitButton) {
            submitButton.disabled = false;
          }
          return;
        }
        payload.password = password;
      } else if (password || passwordConfirm) {
        this.setUserModalError('Смена пароля выполняется через отдельное окно.');
        if (submitButton) {
          submitButton.disabled = false;
        }
        return;
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
      if (this.elements.userPasswordConfirmInput) {
        this.elements.userPasswordConfirmInput.value = '';
      }
      this.clearDepartmentSearch();
      if (this.elements.userDepartmentInput) {
        this.refreshDepartmentSelect('');
        this.elements.userDepartmentInput.value = '';
        this.elements.userDepartmentInput.disabled = false;
      }
      if (this.elements.userDepartmentSearch) {
        this.elements.userDepartmentSearch.disabled = false;
      }
      this.renderPhoneList([], true);
      this.resetPhotoUploader('', true);
      this.setPasswordBlockVisible(false);
      this.resetModalPasswordDisplay();
      this.setUserModalError('');
      if (this.passwordModalInstance) {
        this.passwordModalInstance.hide();
      } else if (this.elements.userPasswordModal) {
        this.elements.userPasswordModal.classList.remove('show');
        this.elements.userPasswordModal.style.display = 'none';
        this.elements.userPasswordModal.setAttribute('aria-hidden', 'true');
      }
      this.handlePasswordModalHidden();
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

    normalizeOrgStructure(structure) {
      const nodes = [];
      const source = Array.isArray(structure?.nodes)
        ? structure.nodes
        : Array.isArray(structure)
        ? structure
        : [];
      if (!Array.isArray(source)) {
        return { nodes };
      }
      const seenIds = new Set();
      source.forEach((entry) => {
        if (!entry || typeof entry !== 'object') {
          return;
        }
        const rawId = String(entry.id || '').trim();
        if (!rawId || seenIds.has(rawId)) {
          return;
        }
        seenIds.add(rawId);
        const name = String(entry.name || '').trim() || 'Новая ветка';
        const parentIdRaw = entry.parent_id != null ? String(entry.parent_id).trim() : '';
        const parentId = parentIdRaw || null;
        const members = Array.isArray(entry.members)
          ? Array.from(
              new Set(
                entry.members
                  .map((value) => {
                    const num = Number(value);
                    return Number.isInteger(num) ? num : null;
                  })
                  .filter((value) => Number.isInteger(value)),
              ),
            ).sort((a, b) => a - b)
          : [];
        nodes.push({ id: rawId, name, parent_id: parentId, members });
      });
      return { nodes };
    }

    generateOrgNodeId() {
      if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
      }
      return `node-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
    }

    buildOrgTree() {
      const nodes = this.state.orgStructure?.nodes || [];
      const map = new Map();
      nodes.forEach((node) => {
        if (!node || !node.id) {
          return;
        }
        map.set(node.id, {
          id: node.id,
          name: node.name || 'Новая ветка',
          parent_id: node.parent_id || null,
          members: Array.isArray(node.members) ? node.members.slice() : [],
          children: [],
        });
      });

      const roots = [];
      map.forEach((node) => {
        const parentId = node.parent_id && map.has(node.parent_id) ? node.parent_id : null;
        if (parentId) {
          map.get(parentId).children.push(node);
        } else {
          roots.push(node);
        }
      });

      const collator = new Intl.Collator('ru', { sensitivity: 'base' });
      const sortNodes = (list) => {
        list.sort((a, b) => collator.compare(a.name, b.name));
        list.forEach((child) => sortNodes(child.children));
      };
      sortNodes(roots);
      return roots;
    }

    renderOrgStructure() {
      const container = this.elements.orgTree;
      if (!container) {
        return;
      }
      container.innerHTML = '';
      const tree = this.buildOrgTree();
      const hasNodes = tree.length > 0;
      if (this.elements.orgEmpty) {
        this.elements.orgEmpty.classList.toggle('d-none', hasNodes);
      }
      if (!hasNodes) {
        return;
      }
      tree.forEach((node) => {
        container.appendChild(this.createOrgNodeElement(node));
      });
    }

    createOrgNodeElement(node) {
      const item = document.createElement('li');
      item.className = 'org-tree__item';
      item.dataset.nodeId = node.id;

      const card = document.createElement('div');
      card.className = 'org-node';

      const header = document.createElement('div');
      header.className = 'org-node__header';

      const info = document.createElement('div');
      info.className = 'flex-grow-1 d-flex flex-column gap-1';
      const title = document.createElement('div');
      title.className = 'org-node__title';
      title.textContent = node.name || 'Новая ветка';
      info.appendChild(title);

      const members = this.getOrgNodeMembers(node);
      const meta = document.createElement('div');
      meta.className = 'org-node__meta';
      meta.textContent = members.length
        ? `Участников: ${members.length}`
        : 'Участники не назначены';
      info.appendChild(meta);

      if (members.length) {
        const membersContainer = document.createElement('div');
        membersContainer.className = 'org-node__members';
        members.forEach((user) => {
          const badge = document.createElement('span');
          badge.className = 'org-node__badge';
          badge.textContent = user.username || `ID ${user.id}`;
          membersContainer.appendChild(badge);
        });
        info.appendChild(membersContainer);
      }

      header.appendChild(info);

      const actions = document.createElement('div');
      actions.className = 'org-node__actions d-flex flex-wrap gap-2';

      const membersBtn = document.createElement('button');
      membersBtn.type = 'button';
      membersBtn.className = 'btn btn-outline-secondary btn-sm';
      membersBtn.dataset.orgAction = 'members';
      membersBtn.dataset.nodeId = node.id;
      membersBtn.textContent = 'Состав';
      actions.appendChild(membersBtn);

      const childBtn = document.createElement('button');
      childBtn.type = 'button';
      childBtn.className = 'btn btn-outline-primary btn-sm';
      childBtn.dataset.orgAction = 'add-child';
      childBtn.dataset.nodeId = node.id;
      childBtn.textContent = '+ Ветка';
      actions.appendChild(childBtn);

      const renameBtn = document.createElement('button');
      renameBtn.type = 'button';
      renameBtn.className = 'btn btn-outline-secondary btn-sm';
      renameBtn.dataset.orgAction = 'rename';
      renameBtn.dataset.nodeId = node.id;
      renameBtn.textContent = 'Переименовать';
      actions.appendChild(renameBtn);

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'btn btn-outline-danger btn-sm';
      deleteBtn.dataset.orgAction = 'delete';
      deleteBtn.dataset.nodeId = node.id;
      deleteBtn.textContent = 'Удалить';
      actions.appendChild(deleteBtn);

      header.appendChild(actions);
      card.appendChild(header);
      item.appendChild(card);

      if (Array.isArray(node.children) && node.children.length) {
        const childrenList = document.createElement('ul');
        childrenList.className = 'org-tree__children list-unstyled';
        node.children.forEach((child) => {
          childrenList.appendChild(this.createOrgNodeElement(child));
        });
        item.appendChild(childrenList);
      }

      return item;
    }

    getOrgNodeMembers(node) {
      const members = Array.isArray(node?.members) ? node.members : [];
      if (!members.length) {
        return [];
      }
      const lookup = new Map();
      (this.state.users || []).forEach((user) => {
        if (user && user.id != null) {
          const id = Number(user.id);
          if (Number.isInteger(id)) {
            lookup.set(id, user);
          }
        }
      });
      const resolved = [];
      members.forEach((id) => {
        const numericId = Number(id);
        if (!Number.isInteger(numericId)) {
          return;
        }
        const user = lookup.get(numericId);
        if (user) {
          resolved.push(user);
        }
      });
      const collator = new Intl.Collator('ru', { sensitivity: 'base' });
      resolved.sort((a, b) => collator.compare(a.username || '', b.username || ''));
      return resolved;
    }

    markOrgStructureDirty() {
      this.orgStructureDirty = true;
      this.updateOrgStructureControls();
    }

    updateOrgStructureControls() {
      const saveBtn = this.elements.orgSaveButton;
      if (!saveBtn) {
        return;
      }
      if (this.orgStructureSaving) {
        saveBtn.disabled = true;
        saveBtn.classList.add('disabled');
        saveBtn.textContent = 'Сохранение...';
        return;
      }
      saveBtn.disabled = !this.orgStructureDirty;
      saveBtn.classList.toggle('disabled', saveBtn.disabled);
      if (this.orgSaveButtonDefaultText) {
        saveBtn.textContent = this.orgSaveButtonDefaultText;
      }
    }

    handleOrgSectionClick(event) {
      const actionEl = event.target.closest('[data-org-action]');
      if (!actionEl || !this.elements.orgSection?.contains(actionEl)) {
        return;
      }
      const action = actionEl.dataset.orgAction;
      const nodeId = actionEl.dataset.nodeId || null;
      switch (action) {
        case 'add-root':
          event.preventDefault();
          this.addOrgNode(null);
          break;
        case 'add-child':
          event.preventDefault();
          if (nodeId) {
            this.addOrgNode(nodeId);
          }
          break;
        case 'rename':
          event.preventDefault();
          if (nodeId) {
            this.renameOrgNode(nodeId);
          }
          break;
        case 'delete':
          event.preventDefault();
          if (nodeId) {
            this.removeOrgNode(nodeId);
          }
          break;
        case 'members':
          event.preventDefault();
          if (nodeId) {
            this.openOrgMembersModal(nodeId);
          }
          break;
        default:
          break;
      }
    }

    addOrgNode(parentId) {
      if (!Array.isArray(this.state.orgStructure?.nodes)) {
        this.state.orgStructure = { nodes: [] };
      }
      const nodes = this.state.orgStructure.nodes;
      const newNode = {
        id: this.generateOrgNodeId(),
        name: parentId ? 'Новая ветка' : 'Новый отдел',
        parent_id: parentId || null,
        members: [],
      };
      nodes.push(newNode);
      this.markOrgStructureDirty();
      this.renderOrgStructure();
    }

    findOrgNode(nodeId) {
      if (!nodeId) {
        return null;
      }
      const nodes = this.state.orgStructure?.nodes || [];
      return nodes.find((node) => node?.id === nodeId) || null;
    }

    collectOrgNodeDescendants(nodeId) {
      const nodes = this.state.orgStructure?.nodes || [];
      const toRemove = new Set();
      const stack = [nodeId];
      while (stack.length) {
        const current = stack.pop();
        if (!current || toRemove.has(current)) {
          continue;
        }
        toRemove.add(current);
        nodes.forEach((node) => {
          if (node?.parent_id === current) {
            stack.push(node.id);
          }
        });
      }
      return toRemove;
    }

    removeOrgNode(nodeId) {
      const node = this.findOrgNode(nodeId);
      if (!node) {
        return;
      }
      const toRemove = this.collectOrgNodeDescendants(nodeId);
      const total = toRemove.size;
      const rest = total - 1;
      let suffix = 'дочерних веток';
      if (rest === 1) {
        suffix = 'дочернюю ветку';
      } else if (rest >= 2 && rest <= 4) {
        suffix = 'дочерние ветки';
      }
      const message =
        rest > 0
          ? `Удалить ветку "${node.name}" и ${rest} ${suffix}?`
          : `Удалить ветку "${node.name}"?`;
      if (!window.confirm(message)) {
        return;
      }
      this.state.orgStructure.nodes = (this.state.orgStructure.nodes || []).filter(
        (entry) => !toRemove.has(entry.id),
      );
      this.markOrgStructureDirty();
      this.renderOrgStructure();
    }

    renameOrgNode(nodeId) {
      const node = this.findOrgNode(nodeId);
      if (!node) {
        return;
      }
      const nextName = window.prompt('Название ветки', node.name || '') || '';
      const trimmed = nextName.trim();
      if (!trimmed || trimmed === node.name) {
        return;
      }
      node.name = trimmed;
      this.markOrgStructureDirty();
      this.renderOrgStructure();
    }

    openOrgMembersModal(nodeId) {
      const node = this.findOrgNode(nodeId);
      if (!node || !this.elements.orgMembersModal) {
        return;
      }
      this.orgMembersModalState = { nodeId };
      if (this.elements.orgMembersTitle) {
        this.elements.orgMembersTitle.textContent = `Состав ветки «${node.name}»`;
      }
      if (this.elements.orgMembersDescription) {
        this.elements.orgMembersDescription.textContent =
          'Отметьте пользователей, которые входят в выбранную ветку.';
      }
      this.populateOrgMembersModal(node);
      if (this.orgMembersModalInstance) {
        this.orgMembersModalInstance.show();
      } else {
        this.elements.orgMembersModal.classList.add('show');
        this.elements.orgMembersModal.style.display = 'block';
      }
    }

    closeOrgMembersModal() {
      if (this.orgMembersModalInstance) {
        this.orgMembersModalInstance.hide();
      } else if (this.elements.orgMembersModal) {
        this.elements.orgMembersModal.classList.remove('show');
        this.elements.orgMembersModal.style.display = 'none';
      }
      this.orgMembersModalState = { nodeId: null };
    }

    populateOrgMembersModal(node) {
      const list = this.elements.orgMembersList;
      if (!list) {
        return;
      }
      list.innerHTML = '';
      const membersSet = new Set(Array.isArray(node.members) ? node.members : []);
      const users = (this.state.users || []).slice();
      const collator = new Intl.Collator('ru', { sensitivity: 'base' });
      users.sort((a, b) => collator.compare(a.username || '', b.username || ''));

      if (!users.length) {
        if (this.elements.orgMembersEmpty) {
          this.elements.orgMembersEmpty.classList.remove('d-none');
        }
        return;
      }

      if (this.elements.orgMembersEmpty) {
        this.elements.orgMembersEmpty.classList.add('d-none');
      }

      users.forEach((user) => {
        if (user?.id == null) {
          return;
        }
        const numericId = Number(user.id);
        if (!Number.isInteger(numericId)) {
          return;
        }
        const wrapper = document.createElement('div');
        wrapper.className = 'form-check';

        const input = document.createElement('input');
        input.type = 'checkbox';
        input.className = 'form-check-input';
        input.name = 'memberIds';
        input.value = String(numericId);
        const inputId = `org-member-${node.id}-${numericId}`;
        input.id = inputId;
        input.checked = membersSet.has(numericId);

        const label = document.createElement('label');
        label.className = 'form-check-label';
        label.setAttribute('for', inputId);
        const username = user.username || `ID ${numericId}`;
        label.textContent = username;

        if (user.department || user.role) {
          const meta = document.createElement('div');
          meta.className = 'small text-muted';
          const parts = [];
          if (user.role) {
            parts.push(user.role);
          }
          if (user.department) {
            parts.push(user.department);
          }
          meta.textContent = parts.join(' • ');
          label.appendChild(document.createElement('br'));
          label.appendChild(meta);
        }

        wrapper.appendChild(input);
        wrapper.appendChild(label);
        list.appendChild(wrapper);
      });
    }

    handleOrgMembersSubmit(event) {
      event.preventDefault();
      const nodeId = this.orgMembersModalState?.nodeId;
      const node = nodeId ? this.findOrgNode(nodeId) : null;
      if (!node) {
        this.closeOrgMembersModal();
        return;
      }
      const form = event.currentTarget;
      const formData = new FormData(form);
      const selected = formData
        .getAll('memberIds')
        .map((value) => Number(value))
        .filter((value) => Number.isInteger(value));
      selected.sort((a, b) => a - b);
      node.members = Array.from(new Set(selected));
      this.markOrgStructureDirty();
      this.renderOrgStructure();
      this.closeOrgMembersModal();
      this.setMessage('Состав ветки обновлён.', 'success');
    }

    handleOrgSaveClick(event) {
      event.preventDefault();
      this.saveOrgStructure();
    }

    saveOrgStructure() {
      if (this.orgStructureSaving) {
        return;
      }
      if (!this.orgStructureDirty) {
        this.setMessage('Нет изменений для сохранения.', 'info');
        return;
      }
      this.orgStructureSaving = true;
      this.updateOrgStructureControls();
      const nodes = Array.isArray(this.state.orgStructure?.nodes)
        ? this.state.orgStructure.nodes
        : [];
      const payload = {
        org_structure: {
          nodes: nodes.map((node) => ({
            id: node.id,
            name: node.name,
            parent_id: node.parent_id || null,
            members: Array.isArray(node.members) ? node.members.slice() : [],
          })),
        },
      };

      fetch('/auth/org-structure', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(payload),
      })
        .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
        .then(({ ok, data }) => {
          if (!ok || data.success === false) {
            throw new Error(data.error || 'Не удалось сохранить оргструктуру');
          }
          this.state.orgStructure = this.normalizeOrgStructure(data.org_structure);
          this.orgStructureDirty = false;
          this.setMessage('Оргструктура сохранена.', 'success');
          this.renderOrgStructure();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          this.orgStructureSaving = false;
          this.updateOrgStructureControls();
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

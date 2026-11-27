(() => {
  const PASSWORD_PLACEHOLDER = '••••••••';
  const PERMISSION_WILDCARD = '*';
  const DEFAULT_PHONE_TYPES = ['Рабочий', 'Личный', 'Дополнительный'];
  const DEFAULT_PHONE_PLACEHOLDER = '+7 (999) 000-00-00';
  const USER_OFFLINE_AFTER_MS = 30 * 60 * 1000;

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
      this.fetchUrl = root.dataset.authStateUrl || '/api/auth/state';
      this.usersEndpoint = root.dataset.authUsersEndpoint || '/api/users';
      this.rolesEndpoint = root.dataset.authRolesEndpoint || '/api/roles';
      this.orgStructureEndpoint =
        root.dataset.authOrgStructureEndpoint || '/api/auth/org-structure';
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
        revokeAllSessionsButton: resolveFrom(root, '[data-auth-sessions-revoke-all]'),
        rolesSection: resolveFrom(root, '[data-auth-roles-section]'),
        rolesContainer: resolveFrom(root, '[data-auth-roles-container]'),
        rolesEmpty: resolveFrom(root, '[data-auth-roles-empty]'),
        createRoleForm: resolveFrom(root, '[data-auth-role-create]'),
        createRoleError: resolveFrom(root, '[data-auth-role-create-error]'),
        userModal,
        userForm: resolveFrom(userModal, '[data-auth-user-form]'),
        userModalTitle: resolveFrom(userModal, '[data-auth-user-modal-title]'),
        userModalError: resolveFrom(userModal, '[data-auth-user-modal-error]'),
        userSaveStatus: resolveFrom(userModal, '[data-auth-user-save-status]'),
        userSubmitButton: resolveFrom(userModal, '[data-auth-user-submit]'),
        userRoleSelect: resolveFrom(userModal, '[data-auth-user-role-select]'),
        userPasswordDisplay: resolveFrom(userModal, '[data-auth-user-password-display]'),
        userPasswordToggle: resolveFrom(userModal, '[data-auth-user-password-toggle]'),
        userPasswordBlock: resolveFrom(userModal, '[data-auth-user-password-current]'),
        userPasswordInput: resolveFrom(userModal, '[name="password"]'),
        userRegistrationInput: resolveFrom(userModal, '[data-auth-user-registration]'),
        userRegistrationRelative: resolveFrom(userModal, '[data-auth-user-registration-relative]'),
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
        userBlockWrapper: resolveFrom(userModal, '[data-auth-user-block-wrapper]'),
        userBlockStatus: resolveFrom(userModal, '[data-auth-user-block-status]'),
        userBlockToggle: resolveFrom(userModal, '[data-auth-user-block-toggle]'),
        userBlockMessage: resolveFrom(userModal, '[data-auth-user-block-message]'),
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
        orgMembersRoster: resolveFrom(orgMembersModal, '[data-org-members-roster]'),
        orgMembersCount: resolveFrom(orgMembersModal, '[data-org-members-count]'),
      };
      this.orgSaveButtonDefaultText = this.elements.orgSaveButton
        ? this.elements.orgSaveButton.textContent.trim()
        : '';
      this.orgStructureDirty = false;
      this.orgStructureSaving = false;
      this.orgCollapseState = {};
      this.orgMembersModalState = { nodeId: null };
      this.orgMembersModalInstance = null;
      this.collapsedOrgNodes = new Set();
      this.photoViewerModalInstance = null;
      this.departmentOptions = [];
      this.customDepartmentOptions = new Set();
      this.departmentSearchTerm = '';
      this.inputSettings = typeof window !== 'undefined' ? window.__inputSettings || {} : {};
      this.parentSheetModal = root.closest('.modal[data-settings-sheet]') || root.closest('.modal');
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
      this.handleFullNameInput = this.handleFullNameInput.bind(this);
      this.handleUsernameInput = this.handleUsernameInput.bind(this);
      this.handleBlockToggle = this.handleBlockToggle.bind(this);
      this.modalState = null;
      this.modalPasswordState = { visible: false, loaded: false, value: '' };
      this.modalInstance = null;
      this.passwordModalInstance = null;
      this.passwordModalState = { userId: null };
      this.bootstrap = window.bootstrap || null;
      const controllerHelpers = { DEFAULT_PHONE_PLACEHOLDER, DEFAULT_PHONE_TYPES, PASSWORD_PLACEHOLDER, PERMISSION_WILDCARD, normalizeList };
      this.photoController = window.AuthUserPhoto.create(this, controllerHelpers);
      this.userEditor = window.AuthUserEditor.create(this, controllerHelpers);
      this.roleController = window.AuthRoleEditor.create(this, controllerHelpers);
      this.organizationController = window.AuthOrganization.create(this, controllerHelpers);
    }

    init() {
      this.moveManagedModalToBody(this.elements.userModal);
      this.moveManagedModalToBody(this.elements.userPasswordModal);
      this.moveManagedModalToBody(this.elements.userPhotoViewerModal);
      this.moveManagedModalToBody(this.elements.orgMembersModal);
      this.bindEvents();
      this.bindCredentialInputs();
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

    moveManagedModalToBody(modalEl) {
      if (!(modalEl instanceof HTMLElement) || !this.documentRoot?.body) {
        return;
      }
      if (modalEl.parentElement === this.documentRoot.body) {
        return;
      }
      this.documentRoot.body.appendChild(modalEl);
    }

    bindEvents() {
      this.parentSheetModal?.addEventListener('shown.bs.modal', () => this.refreshUserActivity());
      this.parentSheetModal?.addEventListener('hidden.bs.modal', () => clearTimeout(this.activityTimer));
      this.documentRoot?.getElementById('auth-users-tab')?.addEventListener('shown.bs.tab', () => this.refreshUserActivity());
      this.documentRoot?.getElementById('auth-users-tab')?.addEventListener('hidden.bs.tab', () => clearTimeout(this.activityTimer));
      this.documentRoot?.addEventListener('visibilitychange', () => {
        clearTimeout(this.activityTimer);
        if (!this.documentRoot.hidden) this.refreshUserActivity();
      });
      this.elements.revokeAllSessionsButton?.addEventListener('click', () => this.handleRevokeSessions(null, this.elements.revokeAllSessionsButton));
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
        this.elements.userForm.addEventListener('invalid', event => {
          let section = event.target.closest('details');
          while (section) { section.open = true; section = section.parentElement?.closest('details'); }
        }, true);
      }
      if (this.elements.userPhoneAddButton) {
        this.elements.userPhoneAddButton.addEventListener('click', this.handleAddPhone);
      }
      this.elements.userDepartmentInput?.addEventListener('change', () => {
        const summary = this.elements.userModal?.querySelector('[data-auth-user-department-summary]');
        if (summary) summary.textContent = this.elements.userDepartmentInput.value || 'Не выбран';
      });
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
        this.elements.userModal.querySelectorAll('.dropdown').forEach(dropdown => {
          dropdown.addEventListener('keydown', event => {
            if (event.key !== 'Escape' || !dropdown.querySelector('.dropdown-menu.show')) return;
            const toggle = dropdown.querySelector('[data-bs-toggle="dropdown"]');
            event.preventDefault();
            event.stopPropagation();
            this.bootstrap?.Dropdown?.getInstance(toggle)?.hide();
            toggle?.focus();
          });
        });
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
      if (this.elements.userFullNameInput) {
        this.elements.userFullNameInput.addEventListener('input', this.handleFullNameInput);
      }
      if (this.elements.userUsernameInput) {
        this.elements.userUsernameInput.addEventListener('input', this.handleUsernameInput);
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
      if (this.elements.userBlockToggle) {
        this.elements.userBlockToggle.addEventListener('click', this.handleBlockToggle);
      }
    }

    setLoading(isLoading) {
      this.root.classList.toggle('auth-management--loading', Boolean(isLoading));
    }

    setParentSheetSuspended(suspended) {
      if (!(this.parentSheetModal instanceof HTMLElement)) {
        return;
      }
      const nextState = Boolean(suspended);
      this.parentSheetModal.classList.toggle('auth-management-child-open', nextState);
      if (nextState) {
        this.parentSheetModal.setAttribute('aria-hidden', 'true');
        this.parentSheetModal.inert = true;
      } else {
        this.parentSheetModal.removeAttribute('aria-hidden');
        this.parentSheetModal.inert = false;
      }
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
          this.openRequestedSelfEditor();
        })
        .catch((error) => {
          this.setMessage(error.message || String(error));
        })
        .finally(() => {
          this.setLoading(false);
          this.refreshUserActivity();
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

    openRequestedSelfEditor() {
      if (this.selfEditorRequestHandled) return;
      const params = new URLSearchParams(window.location.search || '');
      if (params.get('editSelf') !== '1') return;
      const userId = this.state.currentUserId;
      if (userId == null) return;
      this.selfEditorRequestHandled = true;
      this.openUserModal(userId);
      params.delete('editSelf');
      const nextSearch = params.toString();
      window.history.replaceState(window.history.state, '', `${window.location.pathname}${nextSearch ? `?${nextSearch}` : ''}${window.location.hash || ''}`);
    }

    capabilitiesEdit(key) {
      return Boolean(this.state.capabilities?.fields?.edit?.[key]);
    }

    capabilitiesView(key) {
      return Boolean(this.state.capabilities?.fields?.view?.[key]);
    }

    updateCreateControls() {
      if (this.elements.revokeAllSessionsButton) {
        const allowed = Boolean(this.state.capabilities?.sessions?.revoke_all);
        this.elements.revokeAllSessionsButton.classList.toggle('d-none', !allowed);
        this.elements.revokeAllSessionsButton.disabled = !allowed;
        this.elements.revokeAllSessionsButton.closest('[data-auth-sessions-menu]')?.classList.toggle('d-none', !allowed);
      }
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
        tbody.querySelectorAll('[data-bs-toggle="dropdown"]').forEach(toggle => this.bootstrap?.Dropdown?.getInstance(toggle)?.dispose());
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
      usernameTitle.dataset.employeeId = String(user.id);
      usernameTitle.dataset.employeePreserveContent = 'true';
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

      const statusCell = document.createElement('td');
      statusCell.className = 'auth-user-state-cell';
      statusCell.dataset.userStatus = String(user.id);
      this.renderUserStatus(statusCell, user);

      const actionsCell = document.createElement('td');
      actionsCell.className = 'text-end';
      const actionsWrapper = document.createElement('div');
      actionsWrapper.className = 'dropdown auth-user-actions';
      const toggle = document.createElement('button');
      toggle.type = 'button';
      toggle.className = 'btn btn-sm auth-user-menu-toggle';
      toggle.dataset.bsToggle = 'dropdown';
      toggle.setAttribute('aria-expanded', 'false');
      toggle.setAttribute('aria-label', `Действия пользователя ${displayName}`);
      toggle.title = 'Действия';
      toggle.innerHTML = '<i class="bi bi-three-dots" aria-hidden="true"></i>';
      actionsWrapper.appendChild(toggle);
      const menu = document.createElement('div');
      menu.className = 'dropdown-menu dropdown-menu-end';
      actionsWrapper.appendChild(menu);
      if (this.bootstrap?.Dropdown) {
        new this.bootstrap.Dropdown(toggle, {
          popperConfig: config => ({ ...config, strategy: 'fixed' }),
        });
      }

      const openBtn = document.createElement('button');
      openBtn.type = 'button';
      openBtn.className = 'dropdown-item';
      openBtn.dataset.action = 'open-user';
      openBtn.textContent = 'Открыть';
      menu.appendChild(openBtn);
      const permissionsBtn = document.createElement('button');
      permissionsBtn.type = 'button'; permissionsBtn.className = 'dropdown-item';
      permissionsBtn.dataset.userPermissions = user.permissions_user_id || String(user.id); permissionsBtn.textContent = 'Права';
      menu.appendChild(permissionsBtn);

      if (user.can_revoke_sessions) {
        const revokeBtn = document.createElement('button');
        revokeBtn.type = 'button';
        revokeBtn.className = 'dropdown-item';
        revokeBtn.dataset.action = 'revoke-sessions';
        revokeBtn.textContent = 'Завершить сеансы';
        revokeBtn.title = 'Завершить сеансы этого пользователя на всех устройствах';
        menu.appendChild(revokeBtn);
      }

      const deleteBtn = document.createElement('button');
      deleteBtn.type = 'button';
      deleteBtn.className = 'dropdown-item text-danger';
      deleteBtn.dataset.action = 'delete-user';
      deleteBtn.textContent = 'Удалить';
      deleteBtn.disabled = !user.can_delete;
      menu.appendChild(deleteBtn);

      actionsCell.appendChild(actionsWrapper);

      row.appendChild(usernameCell);
      row.appendChild(roleCell);
      row.appendChild(contactsCell);
      row.appendChild(statusCell);
      row.appendChild(actionsCell);
      return row;
    }

    renderUserStatus(container, user) {
      const lastActivity = user.last_portal_activity_at ? new Date(user.last_portal_activity_at) : null;
      const known = lastActivity && Number.isFinite(lastActivity.getTime());
      const age = known ? Math.max(0, Date.now() + (this.activityClockOffset || 0) - lastActivity.getTime()) : null;
      const online = known && age <= USER_OFFLINE_AFTER_MS;
      const relative = !known ? 'Нет данных' : age < 60000 ? 'Только что'
        : age < 3600000 ? `${Math.floor(age / 60000)} мин назад`
        : age < 86400000 ? `${Math.floor(age / 3600000)} ч назад`
        : `${Math.floor(age / 86400000)} дн назад`;
      const activityText = known ? `Последняя активность: ${lastActivity.toLocaleString('ru-RU')}`
        : 'Последняя активность не зафиксирована';
      const presenceTitle = `${online ? 'Online' : 'Offline'}. ${activityText}. Offline после 30 минут отсутствия активности.`;
      const accessTitle = user.is_blocked ? 'Заблокирован: доступ к панели закрыт' : 'Активен: доступ к панели разрешён';
      container.replaceChildren();
      const icons = document.createElement('div');
      icons.className = 'auth-user-state-icons';
      for (const [icon, variant, title] of [
        ['circle-fill', online ? 'online' : 'offline', presenceTitle],
        [user.is_blocked ? 'lock-fill' : 'shield-check', user.is_blocked ? 'blocked' : 'active', accessTitle],
      ]) {
        const indicator = document.createElement('span');
        indicator.className = `auth-user-state auth-user-state--${variant}`;
        indicator.tabIndex = 0;
        indicator.title = title;
        indicator.setAttribute('aria-label', title);
        indicator.innerHTML = `<i class="bi bi-${icon}" aria-hidden="true"></i>`;
        icons.appendChild(indicator);
      }
      const time = document.createElement('span');
      time.className = 'auth-user-last-activity';
      time.textContent = relative;
      time.title = activityText;
      container.append(icons, time);
    }

    async refreshUserActivity() {
      clearTimeout(this.activityTimer);
      if (this.activityPending || !this.isUserActivityVisible()) return;
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 10000);
      this.activityPending = true;
      try {
        const response = await fetch(`${this.usersEndpoint}/activity`, {
          credentials: 'same-origin', signal: controller.signal, headers: { Accept: 'application/json' },
        });
        if (!response.ok) return;
        const data = await response.json();
        const serverTime = Date.parse(data.server_time);
        if (Number.isFinite(serverTime)) this.activityClockOffset = serverTime - Date.now();
        for (const item of Array.isArray(data.users) ? data.users : []) {
          const user = this.state.users.find(candidate => String(candidate.id) === String(item.id));
          if (!user) continue;
          user.last_portal_activity_at = item.last_portal_activity_at;
          user.is_blocked = item.is_blocked;
        }
      } catch (_) {
        // Keep the last known timestamps; their age continues to advance during outages.
      } finally {
        clearTimeout(timeout);
        this.activityPending = false;
        this.elements.usersBody?.querySelectorAll('[data-user-status]').forEach(cell => {
          const user = this.state.users.find(item => String(item.id) === cell.dataset.userStatus);
          if (user) this.renderUserStatus(cell, user);
        });
        this.updateUserCardStatus();
        if (this.isUserActivityVisible()) {
          this.activityTimer = setTimeout(() => this.refreshUserActivity(), 60000);
        }
      }
    }

    isUserActivityVisible() {
      return !this.documentRoot?.hidden && this.parentSheetModal?.classList.contains('show')
        && this.elements.usersSection?.classList.contains('active');
    }

    updateUserCardStatus() {
      const container = this.elements.userModal?.querySelector('[data-auth-user-card-status]');
      if (!container) return;
      const user = this.state.users.find(item => String(item.id) === String(this.modalState?.userId));
      container.hidden = !user;
      if (user) this.renderUserStatus(container, user);
    }

    createUserAvatar(...args) { return this.photoController.createUserAvatar(...args); }

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

    resetPhotoUploader(...args) { return this.photoController.resetPhotoUploader(...args); }

    setPhotoValue(...args) { return this.photoController.setPhotoValue(...args); }

    applyPhotoPreview(...args) { return this.photoController.applyPhotoPreview(...args); }

    setPhotoStatus(...args) { return this.photoController.setPhotoStatus(...args); }

    setPhotoUploadDisabled(...args) { return this.photoController.setPhotoUploadDisabled(...args); }

    setPhotoUploading(...args) { return this.photoController.setPhotoUploading(...args); }

    updatePhotoControls(...args) { return this.photoController.updatePhotoControls(...args); }

    isPhotoInteractionLocked(...args) { return this.photoController.isPhotoInteractionLocked(...args); }

    handlePhotoDropzoneClick(...args) { return this.photoController.handlePhotoDropzoneClick(...args); }

    handlePhotoFileChange(...args) { return this.photoController.handlePhotoFileChange(...args); }

    handlePhotoDrop(...args) { return this.photoController.handlePhotoDrop(...args); }

    handlePhotoDragOver(...args) { return this.photoController.handlePhotoDragOver(...args); }

    handlePhotoDragLeave(...args) { return this.photoController.handlePhotoDragLeave(...args); }

    handlePhotoRemove(...args) { return this.photoController.handlePhotoRemove(...args); }

    processPhotoFile(...args) { return this.photoController.processPhotoFile(...args); }

    validatePhotoFile(...args) { return this.photoController.validatePhotoFile(...args); }

    uploadUserPhoto(...args) { return this.photoController.uploadUserPhoto(...args); }

    persistUploadedPhotoState(...args) { return this.photoController.persistUploadedPhotoState(...args); }

    syncSidebarAvatar(...args) { return this.photoController.syncSidebarAvatar(...args); }

    handleUsersClick(...args) { return this.userEditor.handleUsersClick(...args); }

    handleRevokeSessions(...args) { return this.userEditor.handleRevokeSessions(...args); }

    redirectToLogin() {
      window.location.assign('/login?sessionExpired');
    }

    handleCreateUserTrigger(...args) { return this.userEditor.handleCreateUserTrigger(...args); }

    openUserModal(...args) { return this.userEditor.openUserModal(...args); }

    showUserModal(...args) { return this.userEditor.showUserModal(...args); }

    resolveModalPermissions(...args) { return this.userEditor.resolveModalPermissions(...args); }

    populateRoleSelect(...args) { return this.userEditor.populateRoleSelect(...args); }

    setUserModalError(...args) { return this.userEditor.setUserModalError(...args); }

    setUserSaveStatus(...args) { return this.userEditor.setUserSaveStatus(...args); }

    setUserSubmitLabel(...args) { return this.userEditor.setUserSubmitLabel(...args); }

    formatRegistrationDate(...args) { return this.userEditor.formatRegistrationDate(...args); }

    getPhoneSettings(...args) { return this.userEditor.getPhoneSettings(...args); }

    getPhoneTypes(...args) { return this.userEditor.getPhoneTypes(...args); }

    getPhoneFormat(...args) { return this.userEditor.getPhoneFormat(...args); }

    formatPhoneNumber(...args) { return this.userEditor.formatPhoneNumber(...args); }

    applyPhoneValueSettings(...args) { return this.userEditor.applyPhoneValueSettings(...args); }

    populatePhoneTypeSelect(...args) { return this.userEditor.populatePhoneTypeSelect(...args); }

    refreshPhoneControlsFromSettings(...args) { return this.userEditor.refreshPhoneControlsFromSettings(...args); }

    handlePhoneValueInput(...args) { return this.userEditor.handlePhoneValueInput(...args); }

    handleInputSettingsChange(...args) { return this.userEditor.handleInputSettingsChange(...args); }

    handleDepartmentSearchInput(...args) { return this.userEditor.handleDepartmentSearchInput(...args); }

    clearDepartmentSearch(...args) { return this.userEditor.clearDepartmentSearch(...args); }

    buildDepartmentOptionList(...args) { return this.userEditor.buildDepartmentOptionList(...args); }

    updateDepartmentOptionsFromOrgStructure(...args) { return this.userEditor.updateDepartmentOptionsFromOrgStructure(...args); }

    refreshDepartmentSelect(...args) { return this.userEditor.refreshDepartmentSelect(...args); }

    setDepartmentValue(...args) { return this.userEditor.setDepartmentValue(...args); }

    handlePhotoPreviewClick(...args) { return this.photoController.handlePhotoPreviewClick(...args); }

    handlePhotoPreviewKeydown(...args) { return this.photoController.handlePhotoPreviewKeydown(...args); }

    openPhotoViewer(...args) { return this.photoController.openPhotoViewer(...args); }

    renderPhoneList(...args) { return this.userEditor.renderPhoneList(...args); }

    createPhoneRow(...args) { return this.userEditor.createPhoneRow(...args); }

    togglePhonesEmpty(...args) { return this.userEditor.togglePhonesEmpty(...args); }

    handleAddPhone(...args) { return this.userEditor.handleAddPhone(...args); }

    handlePhoneListClick(...args) { return this.userEditor.handlePhoneListClick(...args); }

    collectPhonePayload(...args) { return this.userEditor.collectPhonePayload(...args); }

    setPasswordBlockVisible(...args) { return this.userEditor.setPasswordBlockVisible(...args); }

    resetModalPasswordDisplay(...args) { return this.userEditor.resetModalPasswordDisplay(...args); }

    showModalPassword(...args) { return this.userEditor.showModalPassword(...args); }

    hideModalPassword(...args) { return this.userEditor.hideModalPassword(...args); }

    handleModalPasswordToggle(...args) { return this.userEditor.handleModalPasswordToggle(...args); }

    fetchUserPassword(...args) { return this.userEditor.fetchUserPassword(...args); }

    resetPasswordModal(...args) { return this.userEditor.resetPasswordModal(...args); }

    setPasswordModalMessage(...args) { return this.userEditor.setPasswordModalMessage(...args); }

    handlePasswordChangeTrigger(...args) { return this.userEditor.handlePasswordChangeTrigger(...args); }

    handlePasswordModalSubmit(...args) { return this.userEditor.handlePasswordModalSubmit(...args); }

    handlePasswordModalHidden(...args) { return this.userEditor.handlePasswordModalHidden(...args); }

    handleUserFormSubmit(...args) { return this.userEditor.handleUserFormSubmit(...args); }

      handleDeleteUser(...args) { return this.userEditor.handleDeleteUser(...args); }

    closeUserModal(...args) { return this.userEditor.closeUserModal(...args); }

    handleUserModalHidden(...args) { return this.userEditor.handleUserModalHidden(...args); }

    resetPasswords(...args) { return this.userEditor.resetPasswords(...args); }

    handleFullNameInput(...args) { return this.userEditor.handleFullNameInput(...args); }

    credentialControls(...args) { return this.userEditor.credentialControls(...args); }

    normalizeCredentialLogin(...args) { return this.userEditor.normalizeCredentialLogin(...args); }

    hasInvalidCredentialCharacters(...args) { return this.userEditor.hasInvalidCredentialCharacters(...args); }

    credentialPasswordError(...args) { return this.userEditor.credentialPasswordError(...args); }

    setCredentialFeedback(...args) { return this.userEditor.setCredentialFeedback(...args); }

    validateCredentialControl(...args) { return this.userEditor.validateCredentialControl(...args); }

    resetCredentialFeedback(...args) { return this.userEditor.resetCredentialFeedback(...args); }

    bindCredentialInputs(...args) { return this.userEditor.bindCredentialInputs(...args); }

    handleUsernameInput(...args) { return this.userEditor.handleUsernameInput(...args); }

    updateUserModalTitleFromInputs(...args) { return this.userEditor.updateUserModalTitleFromInputs(...args); }

    setRegistrationInfo(...args) { return this.userEditor.setRegistrationInfo(...args); }

    formatRegistrationRelative(...args) { return this.userEditor.formatRegistrationRelative(...args); }

    pluralizeRu(...args) { return this.userEditor.pluralizeRu(...args); }

    updateBlockControls(...args) { return this.userEditor.updateBlockControls(...args); }

    setBlockControlsLoading(...args) { return this.userEditor.setBlockControlsLoading(...args); }

    handleBlockToggle(...args) { return this.userEditor.handleBlockToggle(...args); }

    applyUserUpdate(...args) { return this.userEditor.applyUserUpdate(...args); }


    renderRoles(...args) { return this.roleController.renderRoles(...args); }

    createRoleCard(...args) { return this.roleController.createRoleCard(...args); }

    buildPermissionGroup(...args) { return this.roleController.buildPermissionGroup(...args); }

    handlePermissionToggle(...args) { return this.roleController.handlePermissionToggle(...args); }

    handleRolesClick(...args) { return this.roleController.handleRolesClick(...args); }

    handleCreateRole(...args) { return this.roleController.handleCreateRole(...args); }

    collectRolePayload(...args) { return this.roleController.collectRolePayload(...args); }

    collectPermissionsFromGroup(...args) { return this.roleController.collectPermissionsFromGroup(...args); }

    isPermissionGroupEditable(...args) { return this.roleController.isPermissionGroupEditable(...args); }

    handleSaveRole(...args) { return this.roleController.handleSaveRole(...args); }

      handleDeleteRole(...args) { return this.roleController.handleDeleteRole(...args); }

    normalizeOrgStructure(...args) { return this.organizationController.normalizeOrgStructure(...args); }

    generateOrgNodeId(...args) { return this.organizationController.generateOrgNodeId(...args); }

    buildOrgTree(...args) { return this.organizationController.buildOrgTree(...args); }

    renderOrgStructure(...args) { return this.organizationController.renderOrgStructure(...args); }

    createOrgNodeElement(...args) { return this.organizationController.createOrgNodeElement(...args); }

    getOrgDepartmentPaths(...args) { return this.organizationController.getOrgDepartmentPaths(...args); }

    isOrgDepartmentMember(...args) { return this.organizationController.isOrgDepartmentMember(...args); }

    getOrgNodeMembers(...args) { return this.organizationController.getOrgNodeMembers(...args); }

    markOrgStructureDirty(...args) { return this.organizationController.markOrgStructureDirty(...args); }

    updateOrgStructureControls(...args) { return this.organizationController.updateOrgStructureControls(...args); }

    handleOrgSectionClick(...args) { return this.organizationController.handleOrgSectionClick(...args); }

    addOrgNode(...args) { return this.organizationController.addOrgNode(...args); }

    findOrgNode(...args) { return this.organizationController.findOrgNode(...args); }

    collectOrgNodeDescendants(...args) { return this.organizationController.collectOrgNodeDescendants(...args); }

      removeOrgNode(...args) { return this.organizationController.removeOrgNode(...args); }

      renameOrgNode(...args) { return this.organizationController.renameOrgNode(...args); }

    openOrgMembersModal(...args) { return this.organizationController.openOrgMembersModal(...args); }

    closeOrgMembersModal(...args) { return this.organizationController.closeOrgMembersModal(...args); }

    populateOrgMembersModal(...args) { return this.organizationController.populateOrgMembersModal(...args); }

    handleOrgMembersSubmit(...args) { return this.organizationController.handleOrgMembersSubmit(...args); }

    handleOrgSaveClick(...args) { return this.organizationController.handleOrgSaveClick(...args); }

    saveOrgStructure(...args) { return this.organizationController.saveOrgStructure(...args); }
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

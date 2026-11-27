/* user controller. The host owns authoritative form/account state; no copied permissions or identity. */
(function () {
  'use strict';
  window.AuthUserEditor = Object.freeze({
    create(context, helpers) {
      const { DEFAULT_PHONE_PLACEHOLDER, DEFAULT_PHONE_TYPES, PASSWORD_PLACEHOLDER } = helpers;
      return new class AuthUserEditorController {
        constructor() { this.context = context; }

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
          case 'revoke-sessions':
            this.handleRevokeSessions(userId, button);
            break;
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

      async handleRevokeSessions(userId, button) {
        const all = userId == null;
        const user = all ? null : this.context.state.users.find(item => String(item.id) === String(userId));
        if (button.disabled || this.context.revokingSessions || (all ? !this.context.state.capabilities?.sessions?.revoke_all : !user?.can_revoke_sessions)) return;
        const confirmed = await showConfirmActionModal({
          title: all ? 'Завершить все сеансы' : 'Завершить сеансы пользователя',
          message: all
            ? 'Завершить сеансы ВСЕХ пользователей на всех устройствах, включая ваш? Для продолжения потребуется снова войти в систему.'
            : `Завершить все сеансы пользователя «${user.username}»? Повторный вход по паролю останется доступен.`,
          confirmText: 'Завершить сеансы', confirmVariant: 'danger',
        });
        if (!confirmed || this.context.revokingSessions) return;
        this.context.revokingSessions = true;
        button.disabled = true;
        try {
          const url = all ? `${this.context.usersEndpoint}/sessions/revoke-all` : `${this.context.usersEndpoint}/${encodeURIComponent(userId)}/sessions/revoke`;
          const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers: { Accept: 'application/json' } });
          if (response.status === 401) { this.context.redirectToLogin(); return; }
          const data = await response.json();
          if (!response.ok || !data.success) throw new Error(data.error || 'Не удалось завершить сеансы. Проверьте права и доступность сервера.');
          if (data.current_user_revoked) { this.context.redirectToLogin(); return; }
          this.context.setMessage('Сеансы пользователя завершены. Для продолжения ему потребуется повторный вход.', 'success');
        } catch (error) {
          this.context.setMessage(error.message || String(error), 'danger');
        } finally {
          this.context.revokingSessions = false;
          button.disabled = false;
        }
      }


      handleCreateUserTrigger(event) {
        event.preventDefault();
        if (!this.context.capabilitiesEdit('user.create')) {
          this.context.setMessage('У вас нет прав на создание пользователей.');
          return;
        }
        this.showUserModal('create');
      }

      openUserModal(userId) {
        const user = this.context.state.users.find((item) => String(item.id) === String(userId));
        if (!user) {
          this.context.setMessage('Пользователь не найден.', 'danger');
          return;
        }
        this.showUserModal('edit', user);
      }

      showUserModal(mode, user) {
        if (!this.context.elements.userForm) {
          return;
        }
        this.context.modalPasswordState = { visible: false, loaded: false, value: '' };
        const original = user ? JSON.parse(JSON.stringify(user)) : null;
        const permissions = this.resolveModalPermissions(mode, user);
        this.context.modalState = {
          mode,
          userId: mode === 'edit' ? user?.id : null,
          original,
          permissions,
          isBlocked: Boolean(user?.is_blocked),
        };
        this.context.elements.userModal?.querySelectorAll('details').forEach(item => { item.open = false; });
        this.context.elements.userModal?.querySelectorAll('[data-user-security-disclosure]').forEach(item => { item.open = mode === 'create'; });
        this.context.updateUserCardStatus();
        const departmentSummary = this.context.elements.userModal?.querySelector('[data-auth-user-department-summary]');
        if (departmentSummary) departmentSummary.textContent = user?.department || 'Не выбран';
        this.context.usernameEdited = false;
        this.resetCredentialFeedback();
        this.context.elements.userForm.dataset.mode = mode;
        this.context.elements.userForm.dataset.userId = mode === 'edit' && user?.id != null ? String(user.id) : '';
        if (this.context.elements.userModal) {
          this.context.elements.userModal.dispatchEvent(new CustomEvent('authManagement:userModalOpen', {
            detail: { mode, userId: this.context.modalState.userId },
          }));
        }
        this.setUserModalError('');
        this.setUserSaveStatus('');
        this.setUserSubmitLabel('Сохранить');

        const username = user?.username || '';
        const fullName = user?.full_name || '';
        if (this.context.elements.userModalTitle) {
          const displayName = fullName.trim() || username.trim() || 'Пользователь';
          this.context.elements.userModalTitle.textContent =
            mode === 'create' ? 'Новый пользователь' : displayName;
        }

        if (this.context.elements.userUsernameInput) {
          this.context.elements.userUsernameInput.value = username;
          this.context.elements.userUsernameInput.disabled = mode === 'edit' && !permissions.canEditUsername;
        }

        if (this.context.elements.userFullNameInput) {
          this.context.elements.userFullNameInput.value = fullName;
          this.context.elements.userFullNameInput.disabled = mode === 'edit' && !permissions.canEditDetails;
        }

        this.updateUserModalTitleFromInputs();

        const roleId = user?.role_id != null ? String(user.role_id) : '';
        this.populateRoleSelect(this.context.elements.userRoleSelect, mode === 'edit' ? roleId : '');
        if (this.context.elements.userRoleSelect) {
          this.context.elements.userRoleSelect.disabled = !permissions.canEditRole;
          if (mode === 'edit') {
            this.context.elements.userRoleSelect.value = roleId || this.context.elements.userRoleSelect.value;
          }
        }

        if (this.context.elements.userEmailInput) {
          this.context.elements.userEmailInput.value = user?.email || '';
          this.context.elements.userEmailInput.disabled = !permissions.canEditDetails;
        }
        if (this.context.elements.userDepartmentInput) {
          this.setDepartmentValue(user?.department || '');
          this.context.elements.userDepartmentInput.disabled = !permissions.canEditDetails;
        }
        if (this.context.elements.userDepartmentSearch) {
          this.context.elements.userDepartmentSearch.disabled = !permissions.canEditDetails;
        }
        if (this.context.elements.userBirthDateInput) {
          this.context.elements.userBirthDateInput.value = user?.birth_date || '';
          this.context.elements.userBirthDateInput.disabled = !permissions.canEditDetails;
        }
        this.context.resetPhotoUploader(user?.photo || '', permissions.canEditDetails);

        if (this.context.elements.userPasswordInput) {
          this.context.elements.userPasswordInput.value = '';
          this.context.elements.userPasswordInput.placeholder =
            mode === 'create' ? 'Придумайте пароль' : 'Смена выполняется через отдельное окно';
          this.context.elements.userPasswordInput.required = mode === 'create';
          this.context.elements.userPasswordInput.disabled = mode !== 'create';
        }
        if (this.context.elements.userPasswordConfirmInput) {
          this.context.elements.userPasswordConfirmInput.value = '';
          this.context.elements.userPasswordConfirmInput.placeholder =
            mode === 'create' ? 'Повторите пароль' : 'Смена выполняется через отдельное окно';
          this.context.elements.userPasswordConfirmInput.required = mode === 'create';
          this.context.elements.userPasswordConfirmInput.disabled = mode !== 'create';
        }

        if (this.context.elements.userPasswordCreateSection) {
          this.context.elements.userPasswordCreateSection.classList.toggle('d-none', mode !== 'create');
        }
        if (this.context.elements.userPasswordChangeWrapper) {
          const showChange = mode === 'edit' && permissions.canEditPassword;
          this.context.elements.userPasswordChangeWrapper.classList.toggle('d-none', !showChange);
          if (this.context.elements.userPasswordChangeButton) {
            this.context.elements.userPasswordChangeButton.disabled = !showChange;
          }
        } else if (this.context.elements.userPasswordChangeButton) {
          this.context.elements.userPasswordChangeButton.disabled = !(mode === 'edit' && permissions.canEditPassword);
        }

        this.resetModalPasswordDisplay();
        this.setPasswordBlockVisible(mode === 'edit' && permissions.canViewPassword);
        if (this.context.elements.userPasswordToggle) {
          this.context.elements.userPasswordToggle.disabled = !(mode === 'edit' && permissions.canViewPassword);
        }

        if (mode === 'edit') {
          const formattedDate = this.formatRegistrationDate(user?.registration_date) || '—';
          const relativeText = this.formatRegistrationRelative(user?.registration_date);
          this.setRegistrationInfo(formattedDate, relativeText);
        } else {
          this.setRegistrationInfo('Будет назначена автоматически', '—');
        }

        if (this.context.elements.userBlockWrapper) {
          const canBlock = permissions.canBlock;
          const isBlocked = Boolean(user?.is_blocked);
          this.context.modalState.isBlocked = isBlocked;
          this.context.elements.userBlockWrapper.classList.toggle('d-none', mode !== 'edit');
          this.updateBlockControls(isBlocked, mode === 'edit' && canBlock);
          if (this.context.elements.userBlockToggle) {
            this.context.elements.userBlockToggle.disabled = !(mode === 'edit' && canBlock);
          }
        }

        const phones = mode === 'edit' ? user?.phones || [] : [];
        this.renderPhoneList(phones, permissions.canEditDetails);
        this.context.setParentSheetSuspended(true);

        if (this.context.modalInstance) {
          this.context.modalInstance.show();
        } else if (this.context.elements.userModal) {
          this.context.elements.userModal.classList.add('show');
          this.context.elements.userModal.style.display = 'block';
          this.context.elements.userModal.removeAttribute('aria-hidden');
        }
      }

      resolveModalPermissions(mode, user) {
        if (mode === 'create') {
          return {
            canEditUsername: true,
            canEditRole: this.context.capabilitiesEdit('user.role'),
            canEditPassword: true,
            canViewPassword: false,
            canEditDetails: true,
            canBlock: false,
          };
        }
        return {
          canEditUsername: Boolean(user?.can_edit_username),
          canEditRole: Boolean(user?.can_edit_role),
          canEditPassword: Boolean(user?.can_edit_password),
          canViewPassword: Boolean(user?.can_view_password),
          canEditDetails: Boolean(user?.is_self || user?.can_edit_username),
          canBlock: Boolean(user?.can_block),
        };
      }

      populateRoleSelect(select, currentValue) {
        if (!select) {
          return;
        }
        const roles = this.context.state.roles || [];
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
        const box = this.context.elements.userModalError;
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

      setUserSaveStatus(message, variant = 'muted') {
        const status = this.context.elements.userSaveStatus;
        if (!status) {
          return;
        }
        const text = String(message || '').trim();
        status.textContent = text;
        status.classList.remove('d-none', 'text-muted', 'text-success', 'text-danger');
        if (!text) {
          status.classList.add('d-none', 'text-muted');
          return;
        }
        status.classList.add(
          variant === 'success' ? 'text-success' : variant === 'danger' ? 'text-danger' : 'text-muted',
        );
      }

      setUserSubmitLabel(label) {
        if (!this.context.elements.userSubmitButton) {
          return;
        }
        this.context.elements.userSubmitButton.textContent = String(label || 'Сохранить');
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
        return this.context.inputSettings?.phone || {};
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
        if (this.context.elements.userPhonesContainer) {
          const typeSelects = this.context.elements.userPhonesContainer.querySelectorAll('[data-auth-user-phone-type]');
          typeSelects.forEach((select) => {
            this.populatePhoneTypeSelect(select, select.value);
          });
          const valueInputs = this.context.elements.userPhonesContainer.querySelectorAll('[data-auth-user-phone-input]');
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
        if (!this.context.modalState?.permissions?.canEditDetails) {
          return;
        }
        const formatted = this.formatPhoneNumber(input.value);
        if (formatted !== input.value) {
          input.value = formatted;
        }
      }

      handleInputSettingsChange(event) {
        const detail = event?.detail && typeof event.detail === 'object' ? event.detail : {};
        this.context.inputSettings = detail;
        this.refreshPhoneControlsFromSettings();
      }

      handleDepartmentSearchInput(event) {
        if (!this.context.elements.userDepartmentSearch) {
          return;
        }
        this.context.departmentSearchTerm = event.target.value || '';
        this.refreshDepartmentSelect();
      }

      clearDepartmentSearch() {
        this.context.departmentSearchTerm = '';
        if (this.context.elements.userDepartmentSearch) {
          this.context.elements.userDepartmentSearch.value = '';
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
        const tree = this.context.buildOrgTree();
        this.context.departmentOptions = this.buildDepartmentOptionList(tree);
        const known = new Set(
          this.context.departmentOptions.map((option) => option.value.toLowerCase()),
        );
        const customValues = new Set(this.context.customDepartmentOptions);
        this.context.state.users.forEach((user) => {
          const department = String(user?.department || '').trim();
          if (department && !known.has(department.toLowerCase())) {
            customValues.add(department);
          }
        });
        this.context.customDepartmentOptions = customValues;
        this.refreshDepartmentSelect();
      }

      refreshDepartmentSelect(forcedValue) {
        const select = this.context.elements.userDepartmentInput;
        if (!select) {
          return;
        }
        if (
          this.context.elements.userDepartmentSearch &&
          this.context.elements.userDepartmentSearch.value !== this.context.departmentSearchTerm
        ) {
          this.context.elements.userDepartmentSearch.value = this.context.departmentSearchTerm;
        }
        const baseOptions = [...this.context.departmentOptions];
        this.context.customDepartmentOptions.forEach((value) => {
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
        const searchTerm = (this.context.departmentSearchTerm || '').trim().toLowerCase();
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
          el.textContent = `${indent}${option.pathParts?.join(' / ') || option.label}`;
          el.title = option.pathParts ? option.pathParts.join(' / ') : option.label;
          select.appendChild(el);
        });
        select.value = selectedValue;
      }

      setDepartmentValue(value) {
        if (!this.context.elements.userDepartmentInput) {
          return;
        }
        const normalized = String(value || '').trim();
        if (normalized) {
          const exists = this.context.departmentOptions.some(
            (option) => option.value.toLowerCase() === normalized.toLowerCase(),
          );
          if (!exists) {
            this.context.customDepartmentOptions.add(normalized);
          }
        }
        this.clearDepartmentSearch();
        this.refreshDepartmentSelect(normalized);
        this.context.elements.userDepartmentInput.value = normalized;
      }

      renderPhoneList(phones, editable) {
        const container = this.context.elements.userPhonesContainer;
        if (!container) {
          return;
        }
        container.innerHTML = '';
        const list = Array.isArray(phones) ? phones : [];
        list.forEach((phone) => {
          container.appendChild(this.createPhoneRow(phone, editable));
        });
        this.togglePhonesEmpty(list.length === 0);
        if (this.context.elements.userPhoneAddButton) {
          this.context.elements.userPhoneAddButton.disabled = !editable;
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
        if (!this.context.elements.userPhonesEmpty) {
          return;
        }
        this.context.elements.userPhonesEmpty.classList.toggle('d-none', !isEmpty);
      }

      handleAddPhone(event) {
        event.preventDefault();
        if (!this.context.modalState?.permissions?.canEditDetails) {
          return;
        }
        if (!this.context.elements.userPhonesContainer) {
          return;
        }
        const defaultType = this.getPhoneTypes()[0] || '';
        this.context.elements.userPhonesContainer.appendChild(
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
        if (!this.context.modalState?.permissions?.canEditDetails) {
          return;
        }
        const row = button.closest('[data-auth-user-phone-row]');
        if (row && row.parentElement) {
          row.parentElement.removeChild(row);
        }
        if (this.context.elements.userPhonesContainer && this.context.elements.userPhonesContainer.children.length === 0) {
          this.togglePhonesEmpty(true);
        }
      }

      collectPhonePayload() {
        const container = this.context.elements.userPhonesContainer;
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
        if (this.context.elements.userPasswordBlock) {
          this.context.elements.userPasswordBlock.classList.toggle('d-none', !visible);
        }
      }

      resetModalPasswordDisplay() {
        if (this.context.elements.userPasswordDisplay) {
          this.context.elements.userPasswordDisplay.type = 'password';
          this.context.elements.userPasswordDisplay.value = PASSWORD_PLACEHOLDER;
        }
        if (this.context.elements.userPasswordToggle) {
          this.context.elements.userPasswordToggle.textContent = 'Показать';
        }
        this.context.modalPasswordState.visible = false;
      }

      showModalPassword(password) {
        if (this.context.elements.userPasswordDisplay) {
          this.context.elements.userPasswordDisplay.type = 'text';
          this.context.elements.userPasswordDisplay.value = password;
        }
        if (this.context.elements.userPasswordToggle) {
          this.context.elements.userPasswordToggle.textContent = 'Скрыть';
        }
        this.context.modalPasswordState.visible = true;
        this.context.modalPasswordState.loaded = true;
        this.context.modalPasswordState.value = password;
      }

      hideModalPassword() {
        this.resetModalPasswordDisplay();
      }

      handleModalPasswordToggle(event) {
        if (event) {
          event.preventDefault();
        }
        if (!this.context.modalState || this.context.modalState.mode !== 'edit') {
          return;
        }
        if (!this.context.modalState.permissions?.canViewPassword) {
          return;
        }
        if (this.context.modalPasswordState.visible) {
          this.hideModalPassword();
          return;
        }
        if (this.context.modalPasswordState.loaded) {
          this.showModalPassword(this.context.modalPasswordState.value || '');
          return;
        }
        const userId = this.context.modalState.userId;
        if (!userId) {
          return;
        }
        if (this.context.elements.userPasswordToggle) {
          this.context.elements.userPasswordToggle.disabled = true;
        }
        this.fetchUserPassword(userId)
          .then((password) => {
            this.showModalPassword(password || '');
          })
          .catch((error) => {
            this.setUserModalError(error.message || String(error));
          })
          .finally(() => {
            if (this.context.elements.userPasswordToggle) {
              this.context.elements.userPasswordToggle.disabled = false;
            }
          });
      }

      fetchUserPassword(userId) {
        return fetch(`${this.context.usersEndpoint}/${userId}/password`, { credentials: 'same-origin' })
          .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
          .then(({ ok, data }) => {
            if (!ok || data.success === false) {
              throw new Error(data.error || 'Пароль недоступен');
            }
            return data.password || '';
          });
      }

      resetPasswordModal() {
        this.resetCredentialFeedback([this.context.elements.userPasswordModalInput, this.context.elements.userPasswordModalConfirmInput]);
        if (this.context.elements.userPasswordModalForm) {
          this.context.elements.userPasswordModalForm.reset();
        }
        if (this.context.elements.userPasswordModalInput) {
          this.context.elements.userPasswordModalInput.value = '';
        }
        if (this.context.elements.userPasswordModalConfirmInput) {
          this.context.elements.userPasswordModalConfirmInput.value = '';
        }
        this.setPasswordModalMessage('');
        if (this.context.elements.userPasswordModalSubmit) {
          this.context.elements.userPasswordModalSubmit.disabled = false;
        }
      }

      setPasswordModalMessage(message, variant = 'danger') {
        const box = this.context.elements.userPasswordModalError;
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
        if (!this.context.modalState || this.context.modalState.mode !== 'edit') {
          return;
        }
        const { userId, permissions } = this.context.modalState;
        if (!permissions?.canEditPassword) {
          this.context.setMessage('У вас нет прав на изменение пароля.', 'danger');
          return;
        }
        this.context.passwordModalState = { userId };
        this.resetPasswordModal();
        if (this.context.passwordModalInstance) {
          this.context.passwordModalInstance.show();
        } else if (this.context.elements.userPasswordModal) {
          this.context.elements.userPasswordModal.classList.add('show');
          this.context.elements.userPasswordModal.style.display = 'block';
          this.context.elements.userPasswordModal.removeAttribute('aria-hidden');
        }
      }

      handlePasswordModalSubmit(event) {
        event.preventDefault();
        if (!this.context.modalState || this.context.modalState.mode !== 'edit') {
          return;
        }
        const { userId } = this.context.modalState;
        if (!userId) {
          this.setPasswordModalMessage('Пользователь не найден.');
          return;
        }
        const form = event.currentTarget;
        const submitButton = this.context.elements.userPasswordModalSubmit || form.querySelector('button[type="submit"]');
        if (submitButton) {
          submitButton.disabled = true;
        }
        const newPassword = this.context.elements.userPasswordModalInput
          ? this.context.elements.userPasswordModalInput.value
          : '';
        const confirmPassword = this.context.elements.userPasswordModalConfirmInput
          ? this.context.elements.userPasswordModalConfirmInput.value
          : '';
        if (!this.validateCredentialControl(this.context.elements.userPasswordModalInput) || !this.validateCredentialControl(this.context.elements.userPasswordModalConfirmInput)) {
          this.setPasswordModalMessage('Проверьте пароль и его подтверждение: подсказка показана под полем.');
          if (submitButton) submitButton.disabled = false;
          return;
        }
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
        fetch(`${this.context.usersEndpoint}/${userId}`, {
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
            this.context.setMessage('Пароль обновлён.', 'success');
            if (this.context.passwordModalInstance) {
              this.context.passwordModalInstance.hide();
            } else if (this.context.elements.userPasswordModal) {
              this.context.elements.userPasswordModal.classList.remove('show');
              this.context.elements.userPasswordModal.style.display = 'none';
              this.context.elements.userPasswordModal.setAttribute('aria-hidden', 'true');
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
        this.context.passwordModalState = { userId: null };
        this.resetPasswordModal();
      }

      handleUserFormSubmit(event) {
        event.preventDefault();
        if (!this.context.modalState) {
          return;
        }
        const form = event.currentTarget;
        const submitButton = form.querySelector('[data-auth-user-submit]') || form.querySelector('button[type="submit"]');
        if (submitButton) {
          submitButton.disabled = true;
        }
        this.setUserModalError('');
        this.setUserSaveStatus('');
        const { mode, original, permissions, userId } = this.context.modalState;
        const payload = {};
        const rawUsername = this.context.elements.userUsernameInput?.value || '';
        const usernameChanged = mode === 'create' || permissions.canEditUsername && this.context.usernameEdited && rawUsername !== original?.username;
        const username = usernameChanged ? this.normalizeCredentialLogin(rawUsername) : original?.username || rawUsername;
        if (!this.validateCredentialControl(this.context.elements.userUsernameInput)) {
          this.setUserModalError('Проверьте логин: подсказка показана под полем.');
          if (submitButton) submitButton.disabled = false;
          return;
        }
        if (usernameChanged && this.context.elements.userUsernameInput) this.context.elements.userUsernameInput.value = username;
        if (!username) {
          this.setUserModalError('Укажите логин пользователя.');
          if (submitButton) {
            submitButton.disabled = false;
          }
          return;
        }

        if (mode === 'create' || (usernameChanged && username !== (original?.username || ''))) {
          payload.username = username;
        }

        const fullName = this.context.elements.userFullNameInput ? this.context.elements.userFullNameInput.value.trim() : '';
        if (mode === 'create') {
          if (fullName) {
            payload.full_name = fullName;
          }
        } else if (permissions.canEditDetails && fullName !== (original?.full_name || '')) {
          payload.full_name = fullName || null;
        }

        const roleSelect = this.context.elements.userRoleSelect;
        const roleValue = roleSelect ? roleSelect.value.trim() : '';
        const originalRoleId = original?.role_id != null ? String(original.role_id) : '';
        if (mode === 'create') {
          if (roleValue) {
            payload.role_id = Number(roleValue);
          }
        } else if (permissions.canEditRole && roleValue !== originalRoleId) {
          payload.role_id = roleValue ? Number(roleValue) : null;
        }

        const email = this.context.elements.userEmailInput ? this.context.elements.userEmailInput.value.trim() : '';
        if (mode === 'create') {
          if (email) {
            payload.email = email;
          }
        } else if (permissions.canEditDetails && email !== (original?.email || '')) {
          payload.email = email || null;
        }

        const department = this.context.elements.userDepartmentInput ? this.context.elements.userDepartmentInput.value.trim() : '';
        if (mode === 'create') {
          if (department) {
            payload.department = department;
          }
        } else if (permissions.canEditDetails && department !== (original?.department || '')) {
          payload.department = department || null;
        }

        const birthDate = this.context.elements.userBirthDateInput ? this.context.elements.userBirthDateInput.value.trim() : '';
        if (mode === 'create') {
          if (birthDate) {
            payload.birth_date = birthDate;
          }
        } else if (permissions.canEditDetails && birthDate !== (original?.birth_date || '')) {
          payload.birth_date = birthDate || null;
        }

        const photo = this.context.elements.userPhotoInput ? this.context.elements.userPhotoInput.value.trim() : '';
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

        const password = this.context.elements.userPasswordInput ? this.context.elements.userPasswordInput.value : '';
        const passwordConfirm = this.context.elements.userPasswordConfirmInput
          ? this.context.elements.userPasswordConfirmInput.value
          : '';
        if (mode === 'create') {
          if (!this.validateCredentialControl(this.context.elements.userPasswordInput) || !this.validateCredentialControl(this.context.elements.userPasswordConfirmInput)) {
            this.setUserModalError('Проверьте пароль и его подтверждение: подсказка показана под полем.');
            if (submitButton) submitButton.disabled = false;
            return;
          }
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

        // The footer Save covers profile fields AND the separately loaded workforce settings.
        // A synchronous event bridge keeps the two runtimes independent and avoids duplicate writes.
        const workforceChanges = { userId, pending: false, save: null };
        if (mode === 'edit' && this.context.elements.userModal) {
          this.context.elements.userModal.dispatchEvent(new CustomEvent('authManagement:collectWorkforceChanges', {
            detail: workforceChanges,
          }));
        }
        const saveWorkforce = async () => {
          if (!workforceChanges.pending) return;
          if (typeof workforceChanges.save !== 'function' || !(await workforceChanges.save())) {
            throw new Error('Настройки работы не сохранены. Проверьте сообщение в разделе «Работа и расписание».');
          }
        };
        if (mode === 'edit' && Object.keys(payload).length === 0) {
          if (!workforceChanges.pending) {
            this.setUserSaveStatus('Нет изменений для сохранения.', 'muted');
            if (submitButton) submitButton.disabled = false;
            return;
          }
          this.setUserSaveStatus('Сохраняем настройки работы…', 'muted');
          this.setUserSubmitLabel('Сохраняем…');
          Promise.resolve()
            .then(saveWorkforce)
            .then(() => {
              this.setUserSaveStatus('Настройки работы сохранены.', 'success');
              this.context.setMessage('Настройки работы пользователя обновлены.', 'success');
            })
            .catch((error) => {
              this.setUserModalError(error.message || String(error));
              this.setUserSaveStatus('Настройки работы не сохранены.', 'danger');
            })
            .finally(() => {
              this.setUserSubmitLabel('Сохранить');
              if (submitButton) submitButton.disabled = false;
            });
          return;
        }

        const requestUrl = mode === 'create' ? this.context.usersEndpoint : `${this.context.usersEndpoint}/${userId}`;
        const method = mode === 'create' ? 'POST' : 'PUT';

        this.setUserSaveStatus('Сохраняем изменения…', 'muted');
        this.setUserSubmitLabel('Сохраняем…');

        fetch(requestUrl, {
          method,
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify(payload),
        })
          .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
          .then(async ({ ok, data }) => {
            if (!ok || data.success === false) {
              throw new Error(
                data.error || (mode === 'create' ? 'Не удалось создать пользователя' : 'Не удалось сохранить пользователя')
              );
            }
            if (mode === 'create') {
              this.closeUserModal();
              this.context.setMessage('Пользователь создан.', 'success');
              return this.context.refresh();
            }

            if (original && this.context.modalState?.mode === 'edit') {
              Object.assign(original, payload);
            }
            if (workforceChanges.pending) {
              try {
                await saveWorkforce();
              } catch (error) {
                throw new Error('Профиль сохранён, но ' + (error.message || String(error)));
              }
            }
            this.setUserSaveStatus(workforceChanges.pending
              ? 'Профиль и настройки работы сохранены.' : 'Сохранено. Изменения применены.', 'success');
            this.context.setMessage(workforceChanges.pending
              ? 'Профиль и настройки работы пользователя обновлены.' : 'Данные пользователя обновлены.', 'success');
            return this.context.refresh();
          })
          .catch((error) => {
            const message = error.message || String(error);
            this.setUserModalError(message);
            this.setUserSaveStatus(message.startsWith('Профиль сохранён, но ')
              ? 'Профиль сохранён, настройки работы не сохранены.' : 'Не удалось сохранить изменения.', 'danger');
          })
          .finally(() => {
            this.setUserSubmitLabel('Сохранить');
            if (submitButton) {
              submitButton.disabled = false;
            }
          });
      }

      async handleDeleteUser(row, userId) {
          const username = row.dataset.userName || '';
          const confirmed = await showConfirmActionModal({
            title: 'Удаление пользователя',
            message: `Удалить пользователя ${username || '#' + userId}?`,
            confirmText: 'Удалить',
            confirmVariant: 'danger',
            icon: '🗑️',
          });
          if (!confirmed) {
            return;
          }

          try {
            const response = await fetch(`${this.context.usersEndpoint}/${userId}`, {
              method: 'DELETE',
              credentials: 'same-origin',
            });
            const data = await response.json();
            if (!response.ok || data.success === false) {
              throw new Error(data.error || 'Не удалось удалить пользователя');
            }
            this.context.setMessage('Пользователь удалён.', 'success');
            return this.context.refresh();
          } catch (error) {
            this.context.setMessage(error.message || String(error));
          }
        }

      closeUserModal() {
        if (this.context.modalInstance) {
          this.context.modalInstance.hide();
        } else if (this.context.elements.userModal) {
          this.context.elements.userModal.classList.remove('show');
          this.context.elements.userModal.style.display = 'none';
          this.context.elements.userModal.setAttribute('aria-hidden', 'true');
          this.handleUserModalHidden();
        }
      }

      handleUserModalHidden() {
        this.context.setParentSheetSuspended(false);
        this.context.modalState = null;
        this.context.modalPasswordState = { visible: false, loaded: false, value: '' };
        if (this.context.elements.userForm) {
          this.context.elements.userForm.reset();
          delete this.context.elements.userForm.dataset.mode;
          delete this.context.elements.userForm.dataset.userId;
        }
        if (this.context.elements.userRegistrationInput) {
          this.context.elements.userRegistrationInput.textContent = '';
        }
        if (this.context.elements.userRegistrationRelative) {
          this.context.elements.userRegistrationRelative.textContent = '—';
        }
        if (this.context.elements.userPasswordConfirmInput) {
          this.context.elements.userPasswordConfirmInput.value = '';
        }
        this.clearDepartmentSearch();
        if (this.context.elements.userDepartmentInput) {
          this.refreshDepartmentSelect('');
          this.context.elements.userDepartmentInput.value = '';
          this.context.elements.userDepartmentInput.disabled = false;
        }
        if (this.context.elements.userDepartmentSearch) {
          this.context.elements.userDepartmentSearch.disabled = false;
        }
        this.renderPhoneList([], true);
        this.context.resetPhotoUploader('', true);
        this.setPasswordBlockVisible(false);
        this.resetModalPasswordDisplay();
        this.setUserModalError('');
        this.setUserSaveStatus('');
        this.setUserSubmitLabel('Сохранить');
        if (this.context.elements.userBlockWrapper) {
          this.context.elements.userBlockWrapper.classList.add('d-none');
        }
        if (this.context.elements.userBlockStatus) {
          this.context.elements.userBlockStatus.textContent = 'Активен';
          this.context.elements.userBlockStatus.classList.remove('text-bg-danger');
          this.context.elements.userBlockStatus.classList.add('text-bg-success');
        }
        if (this.context.elements.userBlockToggle) {
          this.context.elements.userBlockToggle.disabled = false;
          this.context.elements.userBlockToggle.textContent = 'Заблокировать';
          this.context.elements.userBlockToggle.classList.remove('btn-outline-success');
          this.context.elements.userBlockToggle.classList.add('btn-outline-danger');
        }
        if (this.context.elements.userBlockMessage) {
          this.context.elements.userBlockMessage.textContent = 'Пользователь может входить в панель.';
        }
        if (this.context.passwordModalInstance) {
          this.context.passwordModalInstance.hide();
        } else if (this.context.elements.userPasswordModal) {
          this.context.elements.userPasswordModal.classList.remove('show');
          this.context.elements.userPasswordModal.style.display = 'none';
          this.context.elements.userPasswordModal.setAttribute('aria-hidden', 'true');
        }
        this.handlePasswordModalHidden();
      }

      resetPasswords() {
        this.resetModalPasswordDisplay();
        this.handlePasswordModalHidden();
      }

      handleFullNameInput() {
        this.updateUserModalTitleFromInputs();
      }

      credentialControls() {
        return [this.context.elements.userUsernameInput, this.context.elements.userPasswordInput, this.context.elements.userPasswordConfirmInput,
          this.context.elements.userPasswordModalInput, this.context.elements.userPasswordModalConfirmInput].filter(Boolean);
      }

      normalizeCredentialLogin(value) {
        return String(value || '').replace(/^ +| +$/g, '');
      }

      hasInvalidCredentialCharacters(value) {
        return /[\u0000-\u001F\u007F-\u009F]/.test(value) || Array.from(value).some(character => {
          const point = character.codePointAt(0);
          return point >= 0xD800 && point <= 0xDFFF;
        });
      }

      credentialPasswordError(value) {
        if (!value || /^\s*$/u.test(value)) return 'Пароль не может быть пустым или состоять только из пробелов.';
        if (this.hasInvalidCredentialCharacters(value)) return 'Пароль содержит недопустимые управляющие символы или некорректный Unicode. Удалите их.';
        if (new TextEncoder().encode(value).length > 72) return 'Пароль слишком длинный: максимум 72 байта UTF-8; кириллица и emoji занимают больше одного байта.';
        return '';
      }

      setCredentialFeedback(input, message) {
        if (!input) return;
        input.setCustomValidity(message);
        input.classList.toggle('is-invalid', Boolean(message));
        if (message) input.setAttribute('aria-invalid', 'true'); else input.removeAttribute('aria-invalid');
        const feedback = this.context.documentRoot.getElementById(input.id + 'Error');
        if (feedback) feedback.textContent = message;
      }

      validateCredentialControl(input) {
        if (!input || input.disabled) return true;
        let message = '';
        if (input.dataset.credentialPasteError) {
          message = 'Вставка содержит недопустимые управляющие символы. Измените поле или вставьте значение без них.';
        } else if (input === this.context.elements.userUsernameInput) {
          const unchanged = this.context.modalState?.mode === 'edit' && (!this.context.usernameEdited || input.value === this.context.modalState.original?.username);
          if (!unchanged && !/^[A-Za-z0-9._@-]+$/.test(this.normalizeCredentialLogin(input.value))) {
            message = 'Логин: только латинские буквы, цифры и . _ - @, без пробелов внутри.';
          }
        } else {
          const original = input === this.context.elements.userPasswordConfirmInput ? this.context.elements.userPasswordInput
            : input === this.context.elements.userPasswordModalConfirmInput ? this.context.elements.userPasswordModalInput : null;
          message = original ? (input.value === original.value ? '' : 'Пароли не совпадают. Пробелы тоже учитываются.') : this.credentialPasswordError(input.value);
          if (original && !input.value) message = 'Повторите пароль.';
        }
        this.setCredentialFeedback(input, message);
        return !message;
      }

      resetCredentialFeedback(controls = this.credentialControls()) {
        for (const input of controls.filter(Boolean)) { delete input.dataset.credentialPasteError; this.setCredentialFeedback(input, ''); }
        if (controls.includes(this.context.elements.userUsernameInput)) {
          const status = this.context.documentRoot.getElementById('authUserModalLoginStatus');
          if (status) status.textContent = '';
        }
      }

      bindCredentialInputs() {
        for (const input of this.credentialControls()) {
          input.addEventListener('input', () => {
            delete input.dataset.credentialPasteError;
            this.validateCredentialControl(input);
            const confirm = input === this.context.elements.userPasswordInput ? this.context.elements.userPasswordConfirmInput
              : input === this.context.elements.userPasswordModalInput ? this.context.elements.userPasswordModalConfirmInput : null;
            if (confirm?.value) this.validateCredentialControl(confirm);
          });
          input.addEventListener('paste', event => {
            const text = event.clipboardData?.getData('text') || '';
            if (this.hasInvalidCredentialCharacters(text)) {
              event.preventDefault(); input.dataset.credentialPasteError = 'true'; this.validateCredentialControl(input);
            }
          });
        }
        this.context.elements.userUsernameInput?.addEventListener('blur', () => {
          const input = this.context.elements.userUsernameInput;
          const changed = this.context.modalState?.mode === 'create' || this.context.usernameEdited && input.value !== this.context.modalState?.original?.username;
          if (changed && !input.disabled && !input.dataset.credentialPasteError) {
            const normalized = this.normalizeCredentialLogin(input.value);
            const status = this.context.documentRoot.getElementById('authUserModalLoginStatus');
            if (status) status.textContent = normalized !== input.value ? 'Пробелы по краям логина удалены.' : '';
            input.value = normalized;
          }
          this.validateCredentialControl(input);
        });
      }

      handleUsernameInput() {
        this.context.usernameEdited = true;
        this.updateUserModalTitleFromInputs();
      }

      updateUserModalTitleFromInputs() {
        if (!this.context.elements.userModalTitle || !this.context.modalState) {
          return;
        }
        const mode = this.context.modalState.mode;
        const fullName = this.context.elements.userFullNameInput ? this.context.elements.userFullNameInput.value.trim() : '';
        const username = this.context.elements.userUsernameInput ? this.context.elements.userUsernameInput.value.trim() : '';
        if (mode === 'create') {
          this.context.elements.userModalTitle.textContent = fullName || 'Новый пользователь';
          return;
        }
        this.context.elements.userModalTitle.textContent = fullName || username || 'Пользователь';
      }

      setRegistrationInfo(value, relativeText) {
        if (this.context.elements.userRegistrationInput) {
          this.context.elements.userRegistrationInput.textContent = value || '';
        }
        if (this.context.elements.userRegistrationRelative) {
          const text = relativeText && relativeText.trim() ? relativeText.trim() : '—';
          this.context.elements.userRegistrationRelative.textContent = text;
          this.context.elements.userRegistrationRelative.hidden = text === '—';
        }
      }

      formatRegistrationRelative(value) {
        if (!value) {
          return '—';
        }
        const normalized = value.includes('T') ? value : value.replace(' ', 'T');
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) {
          return '—';
        }
        const now = new Date();
        if (date > now) {
          return 'меньше месяца назад';
        }
        const nowTotalMonths = now.getFullYear() * 12 + now.getMonth();
        const dateTotalMonths = date.getFullYear() * 12 + date.getMonth();
        let totalMonths = nowTotalMonths - dateTotalMonths;
        if (now.getDate() < date.getDate()) {
          totalMonths = Math.max(0, totalMonths - 1);
        }
        if (totalMonths <= 0) {
          return 'меньше месяца назад';
        }
        const years = Math.floor(totalMonths / 12);
        const months = totalMonths % 12;
        const parts = [];
        if (years > 0) {
          parts.push(`${years} ${this.pluralizeRu(years, ['год', 'года', 'лет'])}`);
        }
        if (months > 0) {
          parts.push(`${months} ${this.pluralizeRu(months, ['месяц', 'месяца', 'месяцев'])}`);
        }
        return parts.length ? `${parts.join(' ')} назад` : 'меньше месяца назад';
      }

      pluralizeRu(value, forms) {
        const abs = Math.abs(value) % 100;
        const last = abs % 10;
        if (abs > 10 && abs < 20) {
          return forms[2];
        }
        if (last > 1 && last < 5) {
          return forms[1];
        }
        if (last === 1) {
          return forms[0];
        }
        return forms[2];
      }

      updateBlockControls(isBlocked, canBlock) {
        if (this.context.elements.userBlockStatus) {
          this.context.elements.userBlockStatus.textContent = isBlocked ? 'Заблокирован' : 'Активен';
          this.context.elements.userBlockStatus.classList.toggle('text-bg-danger', isBlocked);
          this.context.elements.userBlockStatus.classList.toggle('text-bg-success', !isBlocked);
        }
        if (this.context.elements.userBlockToggle) {
          this.context.elements.userBlockToggle.textContent = isBlocked ? 'Разблокировать' : 'Заблокировать';
          this.context.elements.userBlockToggle.classList.toggle('btn-outline-success', isBlocked);
          this.context.elements.userBlockToggle.classList.toggle('btn-outline-danger', !isBlocked);
          this.context.elements.userBlockToggle.disabled = !canBlock;
        }
        if (this.context.elements.userBlockMessage) {
          this.context.elements.userBlockMessage.textContent = isBlocked
            ? 'Пользователь не сможет войти в панель до разблокировки.'
            : 'Пользователь может входить в панель.';
        }
      }

      setBlockControlsLoading(isLoading) {
        if (!this.context.elements.userBlockToggle) {
          return;
        }
        if (isLoading) {
          this.context.elements.userBlockToggle.disabled = true;
          return;
        }
        const canBlock = this.context.modalState?.mode === 'edit' && this.context.modalState?.permissions?.canBlock;
        this.context.elements.userBlockToggle.disabled = !canBlock;
      }

      handleBlockToggle(event) {
        if (event) {
          event.preventDefault();
        }
        if (!this.context.modalState || this.context.modalState.mode !== 'edit') {
          return;
        }
        const { userId, permissions } = this.context.modalState;
        if (!permissions?.canBlock) {
          this.context.setMessage('У вас нет прав на изменение статуса доступа.', 'danger');
          return;
        }
        if (!userId) {
          this.setUserModalError('Пользователь не найден.');
          return;
        }
        const nextState = !this.context.modalState.isBlocked;
        this.setUserModalError('');
        this.setBlockControlsLoading(true);
        fetch(`${this.context.usersEndpoint}/${userId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ is_blocked: nextState }),
        })
          .then((response) => response.json().then((data) => ({ ok: response.ok, data })))
          .then(({ ok, data }) => {
            if (!ok || data.success === false) {
              throw new Error(data.error || 'Не удалось обновить статус доступа');
            }
            const updatedUser = data.user || null;
            if (updatedUser) {
              this.applyUserUpdate(updatedUser);
              this.context.modalState.original = { ...(this.context.modalState.original || {}), ...updatedUser };
              this.context.modalState.isBlocked = Boolean(updatedUser.is_blocked);
            } else {
              this.context.modalState.isBlocked = nextState;
              if (this.context.modalState.original) {
                this.context.modalState.original.is_blocked = nextState;
              }
            }
            this.updateBlockControls(this.context.modalState.isBlocked, permissions.canBlock);
            this.context.setMessage(
              this.context.modalState.isBlocked ? 'Пользователь заблокирован.' : 'Пользователь разблокирован.',
              'success',
            );
          })
          .catch((error) => {
            this.setUserModalError(error.message || String(error));
          })
          .finally(() => {
            this.setBlockControlsLoading(false);
          });
      }

      applyUserUpdate(user) {
        if (!user) {
          return;
        }
        const index = this.context.state.users.findIndex((item) => String(item.id) === String(user.id));
        if (index === -1) {
          this.context.state.users.push(user);
        } else {
          const existing = this.context.state.users[index];
          this.context.state.users[index] = { ...existing, ...user };
        }
        this.context.renderUsers();
      }
      }();
    }
  });
})();

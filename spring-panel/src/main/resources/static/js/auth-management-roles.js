/* roles controller. The host owns authoritative form/account state; no copied permissions or identity. */
(function () {
  'use strict';
  window.AuthRoleEditor = Object.freeze({
    create(context, helpers) {
      const { PERMISSION_WILDCARD, normalizeList } = helpers;
      return new class AuthRoleEditorController {
        constructor() { this.context = context; }

      renderRoles() {
        const container = this.context.elements.rolesContainer;
        if (container) {
          container.innerHTML = '';
        }
        if (this.context.elements.rolesEmpty) {
          this.context.elements.rolesEmpty.classList.toggle('d-none', this.context.state.roles.length > 0);
        }
        if (!container) {
          return;
        }

        const catalog = this.context.state.catalog || { pages: [], fields: { edit: [], view: [] } };

        this.context.state.roles.forEach((role) => {
          const card = this.createRoleCard(role, catalog);
          container.appendChild(card);
        });
      }

      createRoleCard(role, catalog) {
        const card = document.createElement('div');
        card.className = 'card role-card';
        card.dataset.roleId = String(role.id);
        card.dataset.initialName = role.name || '';
        card.dataset.initialDescription = role.description || '';
        const pagesInitial = normalizeList(role.permissions?.pages || []);
        const fieldsEditInitial = normalizeList(role.permissions?.fields?.edit || []);
        const fieldsViewInitial = normalizeList(role.permissions?.fields?.view || []);
        card.dataset.initialPages = JSON.stringify(pagesInitial);
        card.dataset.initialFieldsEdit = JSON.stringify(fieldsEditInitial);
        card.dataset.initialFieldsView = JSON.stringify(fieldsViewInitial);

        const canEditName = this.context.capabilitiesEdit('role.name') && !role.is_admin;
        const canEditDescription = this.context.capabilitiesEdit('role.description');
        const canEditPages = this.context.capabilitiesEdit('role.pages') && !role.is_admin;
        const canEditFieldsEdit = this.context.capabilitiesEdit('role.fields.edit') && !role.is_admin;
        const canEditFieldsView = this.context.capabilitiesEdit('role.fields.view') && !role.is_admin;
        const canDeleteRole = this.context.capabilitiesEdit('role.delete') && !role.is_admin;

        const collapseId = `roleDetails${role.id != null ? role.id : Math.random().toString(16).slice(2)}`;

        const header = document.createElement('div');
        header.className = 'role-card__header';

        const toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.className = 'role-card__toggle';
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
        badge.className = 'role-card__badge';
        badge.textContent = role.is_admin ? 'Администратор' : 'Роль';
        badgeWrapper.appendChild(badge);
        header.appendChild(badgeWrapper);

        const collapse = document.createElement('div');
        collapse.className = 'collapse';
        collapse.id = collapseId;

        const body = document.createElement('div');
        body.className = 'card-body';

        const metadata = document.createElement('div');
        metadata.className = 'role-card__metadata';
        const nameGroup = document.createElement('label');
        nameGroup.className = 'mb-3';
        const nameLabel = document.createElement('span');
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
        metadata.appendChild(nameGroup);

        const descriptionGroup = document.createElement('label');
        descriptionGroup.className = 'mb-3';
        const descriptionLabel = document.createElement('span');
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
        metadata.appendChild(descriptionGroup);
        body.appendChild(metadata);

        const editor = window.PermissionEditor.create({ label: 'Поиск разрешений роли' });
        body.appendChild(editor.element);

        this.buildPermissionGroup(
          'pages',
          catalog.pages || [],
          role.permissions?.pages || [],
          canEditPages,
          editor
        );

        this.buildPermissionGroup(
          'fields-edit',
          catalog.fields?.edit || [],
          role.permissions?.fields?.edit || [],
          canEditFieldsEdit,
          editor
        );

        this.buildPermissionGroup(
          'fields-view',
          catalog.fields?.view || [],
          role.permissions?.fields?.view || [],
          canEditFieldsView,
          editor
        );
        editor.refresh();

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

      buildPermissionGroup(groupKey, options, selectedValues, canEdit, editor) {
        const [title, hint] = ({ pages: ['Просмотр страниц', 'Разделы панели, доступные участникам роли'],
          'fields-edit': ['Изменение и управление', 'Настройки, поля и действия, которые можно изменять'],
          'fields-view': ['Просмотр полей', 'Дополнительные данные, доступные для просмотра'] })[groupKey];
        const group = editor.addGroup(title, hint, { choices: true });
        const row = group.body;
        row.dataset.permissionGroup = groupKey;

        const selected = new Set(selectedValues || []);
        const hasWildcard = selected.has(PERMISSION_WILDCARD);

        const wildcardCheckWrapper = document.createElement('label');
        wildcardCheckWrapper.className = 'permissions-editor__choice permissions-editor__choice--all';
        const wildcardCheckbox = document.createElement('input');
        wildcardCheckbox.className = 'form-check-input';
        wildcardCheckbox.type = 'checkbox';
        wildcardCheckbox.value = PERMISSION_WILDCARD;
        wildcardCheckbox.dataset.permission = groupKey;
        wildcardCheckbox.checked = hasWildcard;
        wildcardCheckbox.disabled = !canEdit;
        wildcardCheckbox.dataset.locked = (!canEdit).toString();
        const wildcardLabel = document.createElement('span');
        wildcardLabel.textContent = groupKey === 'pages' ? 'Все страницы' : 'Все элементы';
        wildcardCheckWrapper.appendChild(wildcardCheckbox);
        wildcardCheckWrapper.appendChild(wildcardLabel);
        editor.track(group, wildcardCheckWrapper, wildcardLabel.textContent, () => false, { count: false });

        options.forEach((option) => {
          const checkWrapper = document.createElement('label');
          checkWrapper.className = 'permissions-editor__choice';
          const checkbox = document.createElement('input');
          checkbox.className = 'form-check-input';
          checkbox.type = 'checkbox';
          checkbox.value = option.key;
          checkbox.dataset.permission = groupKey;
          checkbox.dataset.locked = (!canEdit).toString();
          checkbox.checked = selected.has(option.key);
          checkbox.disabled = !canEdit || hasWildcard;
          const optionLabel = document.createElement('span');
          optionLabel.textContent = option.label || option.key;
          checkWrapper.appendChild(checkbox);
          checkWrapper.appendChild(optionLabel);
          editor.track(group, checkWrapper, optionLabel.textContent + ' ' + option.key,
            () => wildcardCheckbox.checked || checkbox.checked);
        });

        if (groupKey === 'pages') {
          const legacyPages = new Set(this.context.state.catalog?.legacy_pages || []);
          row.dataset.preservedPermissions = JSON.stringify([...selected].filter(key => legacyPages.has(key)));
          if (this.context.state.catalog?.page_access_note) {
            const note = document.createElement('p');
            note.className = 'permissions-editor__footnote';
            note.textContent = this.context.state.catalog.page_access_note;
            group.element.appendChild(note);
          }
        }
        return group.element;
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
        if (!this.context.capabilitiesEdit('role.create')) {
          this.context.setMessage('У вас нет прав на создание ролей.');
          return;
        }
        const form = event.currentTarget;
        const formData = new FormData(form);
        const payload = {
          name: String(formData.get('name') || '').trim(),
          description: String(formData.get('description') || '').trim(),
        };
        if (!payload.name) {
          if (this.context.elements.createRoleError) {
            this.context.elements.createRoleError.textContent = 'Название роли обязательно.';
            this.context.elements.createRoleError.classList.remove('d-none');
          }
          return;
        }
        if (this.context.elements.createRoleError) {
          this.context.elements.createRoleError.classList.add('d-none');
          this.context.elements.createRoleError.textContent = '';
        }
        fetch(this.context.rolesEndpoint, {
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
            this.context.setMessage('Роль создана.', 'success');
            return this.context.refresh();
          })
          .catch((error) => {
            if (this.context.elements.createRoleError) {
              this.context.elements.createRoleError.textContent = error.message || String(error);
              this.context.elements.createRoleError.classList.remove('d-none');
            } else {
              this.context.setMessage(error.message || String(error));
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
        const selected = JSON.parse(group.dataset.preservedPermissions || '[]');
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
          this.context.setMessage('Изменения не найдены.', 'info');
          return;
        }
        button.disabled = true;
        fetch(`${this.context.rolesEndpoint}/${roleId}`, {
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
            this.context.setMessage('Права роли обновлены.', 'success');
            return this.context.refresh();
          })
          .catch((error) => {
            this.context.setMessage(error.message || String(error));
          })
          .finally(() => {
            button.disabled = false;
          });
      }

      async handleDeleteRole(card, roleId) {
          const confirmed = await showConfirmActionModal({
            title: 'Удаление роли',
            message: 'Удалить роль?',
            confirmText: 'Удалить',
            confirmVariant: 'danger',
            icon: '🗑️',
          });
          if (!confirmed) {
            return;
          }

          try {
            const response = await fetch(`${this.context.rolesEndpoint}/${roleId}`, {
              method: 'DELETE',
              credentials: 'same-origin',
            });
            const data = await response.json();
            if (!response.ok || data.success === false) {
              throw new Error(data.error || 'Не удалось удалить роль');
            }
            this.context.setMessage('Роль удалена.', 'success');
            return this.context.refresh();
          } catch (error) {
            this.context.setMessage(error.message || String(error));
          }
        }
      }();
    }
  });
})();

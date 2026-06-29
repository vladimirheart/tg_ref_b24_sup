(function () {
  if (window.SettingsNetworkProfilesRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function getInitialProfiles() {
      const value = typeof options.getInitialProfiles === 'function' ? options.getInitialProfiles() : null;
      return Array.isArray(value) ? value : [];
    }

    function getContractUsageData() {
      const value = typeof options.getContractUsageData === 'function' ? options.getContractUsageData() : null;
      return value && typeof value === 'object' ? value : {};
    }

    const state = {
      items: getInitialProfiles().map((item) => prepareNetworkProfile(item)),
      editingIndex: null,
    };

    const elements = {
      networkProfilesBody: document.getElementById('networkProfilesBody'),
      networkProfilesSaveButton: document.querySelector('[data-network-profiles-save]'),
      networkProfileModalEl: document.getElementById('networkProfileEditorModal'),
    };

    const modal = {
      form: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-form]')
        : null,
      title: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-title]')
        : null,
      deleteButton: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-delete]')
        : null,
      providerInput: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-provider]')
        : null,
      contractInput: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-contract]')
        : null,
      supportPhoneInput: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-support-phone]')
        : null,
      legalEntityInput: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-legal-entity]')
        : null,
      restaurantIdsContainer: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-restaurant-ids]')
        : null,
      addRestaurantIdButton: elements.networkProfileModalEl
        ? elements.networkProfileModalEl.querySelector('[data-network-profile-add-restaurant-id]')
        : null,
    };

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    }

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function requestClose(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function confirmAction(message) {
      if (typeof options.confirmDialog === 'function') {
        return Boolean(options.confirmDialog(message));
      }
      return window.confirm(message);
    }

    function sanitizeRestaurantIds(value) {
      const unique = [];
      const pushUnique = (candidate) => {
        const trimmed = typeof candidate === 'string'
          ? candidate.trim()
          : candidate != null
            ? String(candidate).trim()
            : '';
        if (!trimmed || unique.includes(trimmed)) {
          return;
        }
        unique.push(trimmed);
      };
      if (Array.isArray(value)) {
        value.forEach((entry) => {
          if (typeof entry === 'string' || typeof entry === 'number') {
            String(entry)
              .split(/[,;\n]/)
              .forEach((part) => pushUnique(part));
          }
        });
      } else if (typeof value === 'string' || typeof value === 'number') {
        String(value)
          .split(/[,;\n]/)
          .forEach((part) => pushUnique(part));
      }
      return unique;
    }

    function prepareNetworkProfile(item) {
      const source = item || {};
      const provider = source.provider != null ? String(source.provider).trim() : '';
      const contractNumber = source.contract_number != null ? String(source.contract_number).trim() : '';
      const supportPhone = source.support_phone != null ? String(source.support_phone).trim() : '';
      const legalEntity = source.legal_entity != null ? String(source.legal_entity).trim() : '';
      const fallbackSingle = source.restaurant_id != null ? String(source.restaurant_id).trim() : '';
      const idsSource = source.restaurant_ids !== undefined ? source.restaurant_ids : fallbackSingle;
      let restaurantIds = sanitizeRestaurantIds(idsSource);
      if (!restaurantIds.length && fallbackSingle) {
        restaurantIds = [fallbackSingle];
      }
      const primaryRestaurantId = restaurantIds[0] || '';
      return {
        provider,
        contract_number: contractNumber,
        support_phone: supportPhone,
        legal_entity: legalEntity,
        restaurant_ids: restaurantIds,
        restaurant_id: primaryRestaurantId,
      };
    }

    function createEmptyNetworkProfile() {
      return prepareNetworkProfile({});
    }

    function getProfileRestaurantIds(profile) {
      const data = profile || {};
      const ids = sanitizeRestaurantIds(data.restaurant_ids);
      if (!ids.length) {
        sanitizeRestaurantIds(data.restaurant_id).forEach((value) => {
          if (!ids.includes(value)) {
            ids.push(value);
          }
        });
      }
      return ids;
    }

    function renderText(value) {
      const trimmed = (value || '').trim();
      return trimmed ? escapeHtml(trimmed) : '<span class="text-muted">—</span>';
    }

    function renderRestaurantIdsCell(profile) {
      const ids = getProfileRestaurantIds(profile);
      if (!ids.length) {
        return '<span class="text-muted">—</span>';
      }
      const badges = ids
        .map((id) => `<span class="badge bg-light text-dark border">${escapeHtml(id)}</span>`)
        .join('');
      return `<div class="d-flex flex-wrap gap-1">${badges}</div>`;
    }

    function renderContractUsageLink(contractNumber) {
      const trimmed = (contractNumber || '').trim();
      if (!trimmed) {
        return '<span class="text-muted">—</span>';
      }
      const usageData = getContractUsageData();
      const count = usageData && typeof usageData === 'object' ? (usageData[trimmed] || 0) : 0;
      const url = `/object_passports?network_contract_number=${encodeURIComponent(trimmed)}`;
      return `<a href="${url}" class="btn btn-link btn-sm p-0">${count}</a>`;
    }

    function updateRestaurantIdRemoveButtonsState() {
      if (!modal.restaurantIdsContainer) return;
      const entries = modal.restaurantIdsContainer.querySelectorAll('[data-restaurant-id-entry]');
      const allowRemoval = entries.length > 1;
      entries.forEach((entry) => {
        const removeButton = entry.querySelector('[data-remove-restaurant-id]');
        if (removeButton) {
          removeButton.disabled = !allowRemoval;
        }
      });
    }

    function createRestaurantIdEntry(value) {
      const group = document.createElement('div');
      group.className = 'input-group input-group-sm';
      group.dataset.restaurantIdEntry = 'true';

      const input = document.createElement('input');
      input.type = 'text';
      input.className = 'form-control';
      input.placeholder = 'ID ресторана';
      input.value = value || '';
      input.setAttribute('data-restaurant-id-input', 'true');
      group.appendChild(input);

      const removeButton = document.createElement('button');
      removeButton.type = 'button';
      removeButton.className = 'btn btn-outline-danger';
      removeButton.setAttribute('data-remove-restaurant-id', 'true');
      removeButton.innerHTML = '&times;';
      removeButton.title = 'Удалить';
      group.appendChild(removeButton);

      return group;
    }

    function renderRestaurantIdInputs(values) {
      if (!modal.restaurantIdsContainer) return;
      const list = Array.isArray(values) && values.length ? values : [''];
      modal.restaurantIdsContainer.innerHTML = '';
      list.forEach((value) => {
        modal.restaurantIdsContainer.appendChild(createRestaurantIdEntry(value));
      });
      updateRestaurantIdRemoveButtonsState();
    }

    function addRestaurantIdInput(value = '') {
      if (!modal.restaurantIdsContainer) return;
      const entry = createRestaurantIdEntry(value);
      modal.restaurantIdsContainer.appendChild(entry);
      updateRestaurantIdRemoveButtonsState();
      const input = entry.querySelector('[data-restaurant-id-input]');
      if (input) {
        input.focus();
        input.select();
      }
    }

    function collectRestaurantIdsFromForm() {
      if (!modal.restaurantIdsContainer) return [];
      const inputs = modal.restaurantIdsContainer.querySelectorAll('[data-restaurant-id-input]');
      const values = [];
      inputs.forEach((input) => {
        const trimmed = (input.value || '').trim();
        if (trimmed && !values.includes(trimmed)) {
          values.push(trimmed);
        }
      });
      return values;
    }

    function fillNetworkProfileForm(profile) {
      const data = prepareNetworkProfile(profile);
      if (modal.providerInput) {
        modal.providerInput.value = data.provider || '';
      }
      if (modal.contractInput) {
        modal.contractInput.value = data.contract_number || '';
      }
      if (modal.supportPhoneInput) {
        modal.supportPhoneInput.value = data.support_phone || '';
      }
      if (modal.legalEntityInput) {
        modal.legalEntityInput.value = data.legal_entity || '';
      }
      const ids = getProfileRestaurantIds(data);
      renderRestaurantIdInputs(ids.length ? ids : ['']);
    }

    function prepareNetworkProfileModal(index) {
      const numericIndex = Number.isInteger(index) ? index : Number.parseInt(index, 10);
      const isEdit = Number.isInteger(numericIndex) && numericIndex >= 0 && numericIndex < state.items.length;
      state.editingIndex = isEdit ? numericIndex : null;
      const profile = isEdit ? state.items[numericIndex] : createEmptyNetworkProfile();
      fillNetworkProfileForm(profile);
      if (modal.title) {
        modal.title.textContent = isEdit ? 'Редактирование профиля' : 'Новый профиль провайдера';
      }
      if (modal.deleteButton) {
        modal.deleteButton.classList.toggle('d-none', !isEdit);
      }
      return Boolean(elements.networkProfileModalEl);
    }

    function prepareNetworkProfileSettingsTrigger(trigger) {
      const indexValue = trigger instanceof HTMLElement ? trigger.dataset.networkProfileIndex : trigger;
      return prepareNetworkProfileModal(indexValue);
    }

    function renderNetworkProfiles() {
      if (!elements.networkProfilesBody) return;
      elements.networkProfilesBody.innerHTML = '';
      if (!state.items.length) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = '<td colspan="7" class="text-muted text-center">Пока нет профилей</td>';
        elements.networkProfilesBody.appendChild(emptyRow);
        return;
      }
      state.items.forEach((profile, index) => {
        const prepared = prepareNetworkProfile(profile);
        state.items[index] = prepared;
        const row = document.createElement('tr');
        row.dataset.index = String(index);
        row.innerHTML = `
          <td>${renderText(prepared.provider)}</td>
          <td>${renderText(prepared.contract_number)}</td>
          <td>${renderRestaurantIdsCell(prepared)}</td>
          <td>${renderText(prepared.support_phone)}</td>
          <td>${renderText(prepared.legal_entity)}</td>
          <td data-usage-cell></td>
          <td class="text-nowrap">
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-primary" type="button" data-action="edit-profile" data-network-profile-index="${index}">Открыть</button>
              <button class="btn btn-outline-danger" type="button" data-action="remove-profile">Удалить</button>
            </div>
          </td>
        `;
        const usageCell = row.querySelector('[data-usage-cell]');
        if (usageCell) {
          usageCell.innerHTML = renderContractUsageLink(prepared.contract_number);
        }
        elements.networkProfilesBody.appendChild(row);
      });
    }

    function removeNetworkProfileRow(index) {
      if (index < 0 || index >= state.items.length) return;
      state.items.splice(index, 1);
      renderNetworkProfiles();
    }

    function collectNetworkProfilesPayload() {
      return state.items
        .map((item) => {
          const prepared = prepareNetworkProfile(item);
          return {
            provider: prepared.provider,
            contract_number: prepared.contract_number,
            support_phone: prepared.support_phone,
            legal_entity: prepared.legal_entity,
            restaurant_id: prepared.restaurant_id,
            restaurant_ids: prepared.restaurant_ids,
          };
        })
        .filter((profile) => {
          return (
            profile.provider ||
            profile.contract_number ||
            profile.support_phone ||
            profile.legal_entity ||
            profile.restaurant_id ||
            (Array.isArray(profile.restaurant_ids) && profile.restaurant_ids.length)
          );
        });
    }

    function handleNetworkProfileFormSubmit(event) {
      event.preventDefault();
      const data = {
        provider: modal.providerInput ? modal.providerInput.value : '',
        contract_number: modal.contractInput ? modal.contractInput.value : '',
        support_phone: modal.supportPhoneInput ? modal.supportPhoneInput.value : '',
        legal_entity: modal.legalEntityInput ? modal.legalEntityInput.value : '',
        restaurant_ids: collectRestaurantIdsFromForm(),
      };
      const prepared = prepareNetworkProfile(data);
      const isEdit =
        Number.isInteger(state.editingIndex) &&
        state.editingIndex >= 0 &&
        state.editingIndex < state.items.length;
      if (isEdit) {
        state.items[state.editingIndex] = prepared;
      } else {
        state.items.push(prepared);
      }
      renderNetworkProfiles();
      if (elements.networkProfileModalEl) {
        requestClose(modal.form);
      }
    }

    function handleNetworkProfileDelete() {
      if (
        !Number.isInteger(state.editingIndex) ||
        state.editingIndex < 0 ||
        state.editingIndex >= state.items.length
      ) {
        return;
      }
      if (!confirmAction('Удалить профиль провайдера?')) {
        return;
      }
      removeNetworkProfileRow(state.editingIndex);
      if (elements.networkProfileModalEl) {
        requestClose(modal.deleteButton);
      }
    }

    function resetNetworkProfileSettingsModal() {
      state.editingIndex = null;
      if (modal.form) {
        modal.form.reset();
      }
      renderRestaurantIdInputs(['']);
      if (modal.deleteButton) {
        modal.deleteButton.classList.add('d-none');
      }
    }

    async function saveNetworkProfilesSection() {
      if (elements.networkProfilesSaveButton && elements.networkProfilesSaveButton.disabled) {
        return;
      }
      const button = elements.networkProfilesSaveButton;
      const originalText = button ? button.textContent : '';
      if (button) {
        button.disabled = true;
        button.textContent = '...';
      }
      try {
        const payload = { network_profiles: collectNetworkProfilesPayload() };
        const response = await fetch('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Ошибка сохранения профилей');
        }
        popup('✅ Профили провайдеров сохранены');
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) {
          button.disabled = false;
          button.textContent = originalText || 'Сохранить изменения';
        }
      }
    }

    function handleBodyClick(event) {
      const actionButton = event.target.closest('[data-action]');
      if (!actionButton) return;
      const row = actionButton.closest('tr[data-index]');
      if (!row) return;
      const index = Number.parseInt(row.dataset.index, 10);
      if (Number.isNaN(index)) return;
      const action = actionButton.dataset.action;
      if (action === 'remove-profile' && confirmAction('Удалить профиль провайдера?')) {
        removeNetworkProfileRow(index);
      }
    }

    function handleRestaurantIdsClick(event) {
      const button = event.target.closest('[data-remove-restaurant-id]');
      if (!button) return;
      const entry = button.closest('[data-restaurant-id-entry]');
      if (!entry || !modal.restaurantIdsContainer) return;
      const entries = modal.restaurantIdsContainer.querySelectorAll('[data-restaurant-id-entry]');
      if (entries.length <= 1) {
        const input = entry.querySelector('[data-restaurant-id-input]');
        if (input) {
          input.value = '';
          input.focus();
          input.select();
        }
        return;
      }
      entry.remove();
      updateRestaurantIdRemoveButtonsState();
    }

    function bindEvents() {
      if (elements.networkProfilesBody) {
        elements.networkProfilesBody.addEventListener('click', handleBodyClick);
      }
      if (modal.addRestaurantIdButton) {
        modal.addRestaurantIdButton.addEventListener('click', () => addRestaurantIdInput(''));
      }
      if (modal.restaurantIdsContainer) {
        modal.restaurantIdsContainer.addEventListener('click', handleRestaurantIdsClick);
      }
      if (modal.form) {
        modal.form.addEventListener('submit', handleNetworkProfileFormSubmit);
      }
      if (modal.deleteButton) {
        modal.deleteButton.addEventListener('click', handleNetworkProfileDelete);
      }
      if (elements.networkProfilesSaveButton) {
        elements.networkProfilesSaveButton.addEventListener('click', (event) => {
          event.preventDefault();
          saveNetworkProfilesSection();
        });
      }
    }

    bindEvents();

    return {
      collectNetworkProfilesPayload,
      prepareNetworkProfileSettingsTrigger,
      renderNetworkProfiles,
      resetNetworkProfileSettingsModal,
      saveNetworkProfilesSection,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsNetworkProfilesRuntime) {
        return window.__settingsNetworkProfilesRuntime;
      }
      const runtime = createRuntime(options);
      window.prepareNetworkProfileSettingsTrigger = function prepareNetworkProfileSettingsTrigger(trigger) {
        return runtime.prepareNetworkProfileSettingsTrigger(trigger);
      };
      window.renderNetworkProfiles = function renderNetworkProfiles() {
        runtime.renderNetworkProfiles();
      };
      window.resetNetworkProfileSettingsModal = function resetNetworkProfileSettingsModal() {
        runtime.resetNetworkProfileSettingsModal();
      };
      window.SettingsPageCallbackRegistry?.registerMany({
        prepareNetworkProfileSettingsTrigger: window.prepareNetworkProfileSettingsTrigger,
        renderNetworkProfiles: window.renderNetworkProfiles,
        resetNetworkProfileSettingsModal: window.resetNetworkProfileSettingsModal,
      });
      window.__settingsNetworkProfilesRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsNetworkProfilesRuntime = Object.freeze({
    ...api,
    collectNetworkProfilesPayload(...args) {
      return window.__settingsNetworkProfilesRuntime?.collectNetworkProfilesPayload?.(...args);
    },
    prepareNetworkProfileSettingsTrigger(...args) {
      return window.__settingsNetworkProfilesRuntime?.prepareNetworkProfileSettingsTrigger?.(...args);
    },
    renderNetworkProfiles(...args) {
      return window.__settingsNetworkProfilesRuntime?.renderNetworkProfiles?.(...args);
    },
    resetNetworkProfileSettingsModal(...args) {
      return window.__settingsNetworkProfilesRuntime?.resetNetworkProfileSettingsModal?.(...args);
    },
    saveNetworkProfilesSection(...args) {
      return window.__settingsNetworkProfilesRuntime?.saveNetworkProfilesSection?.(...args);
    },
  });
}());

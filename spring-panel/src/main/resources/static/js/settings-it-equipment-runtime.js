(function () {
  if (window.SettingsItEquipmentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      items: [],
    };

    const elements = {
      itEquipmentBody: document.getElementById('itEquipmentBody'),
      itEquipmentAddModalEl: document.getElementById('itEquipmentAddModal'),
    };

    const addModal = {
      form: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-add-form]')
        : null,
      typeSelect: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('#itEquipmentTypeSelect')
        : null,
      vendorSelect: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('#itEquipmentVendorSelect')
        : null,
      modelSelect: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('#itEquipmentModelSelect')
        : null,
      serialNumberInput: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('#itEquipmentSerialNumberInput')
        : null,
      accessoriesInput: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('#itEquipmentAccessoriesInput')
        : null,
      linksContainer: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-add-links]')
        : null,
      submitButton: null,
    };

    addModal.submitButton = addModal.form
      ? addModal.form.querySelector('button[type="submit"]')
      : null;

    function getParameterData() {
      const data = typeof options.getParameterData === 'function' ? options.getParameterData() : null;
      return data && typeof data === 'object' ? data : {};
    }

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
      return false;
    }

    function getItems() {
      return Array.isArray(state.items) ? state.items : [];
    }

    function setItems(nextItems) {
      state.items = Array.isArray(nextItems) ? nextItems : [];
    }

    function parseEquipmentLinks(raw) {
      if (Array.isArray(raw)) {
        return raw.map((item) => (item || '').toString().trim()).filter(Boolean);
      }
      if (typeof raw === 'string') {
        const trimmed = raw.trim();
        if (!trimmed) {
          return [];
        }
        if (trimmed.startsWith('[')) {
          try {
            const parsed = JSON.parse(trimmed);
            if (Array.isArray(parsed)) {
              return parsed.map((item) => (item || '').toString().trim()).filter(Boolean);
            }
          } catch (error) {
            // ignore parsing errors and fallback to line-based parsing
          }
        }
        if (trimmed.includes('\n')) {
          return trimmed
            .split('\n')
            .map((item) => item.trim())
            .filter(Boolean);
        }
        return [trimmed];
      }
      return [];
    }

    function formatEquipmentLinksPayload(links) {
      const normalized = Array.isArray(links)
        ? links.map((item) => (item || '').toString().trim()).filter(Boolean)
        : [];
      if (!normalized.length) {
        return '';
      }
      try {
        return JSON.stringify(normalized);
      } catch (error) {
        return normalized.join('\n');
      }
    }

    function ensureEquipmentLinksPlaceholder(container) {
      if (!container) return;
      const hasItems = container.querySelector('[data-link-item]');
      const placeholder = container.querySelector('[data-link-placeholder]');
      if (hasItems && placeholder) {
        placeholder.remove();
      } else if (!hasItems && !placeholder) {
        const hint = document.createElement('div');
        hint.className = 'text-muted small';
        hint.dataset.linkPlaceholder = 'true';
        hint.textContent = 'Ссылок нет';
        container.appendChild(hint);
      }
    }

    function addEquipmentLinkInput(container, value = '') {
      if (!container) return null;
      const item = document.createElement('div');
      item.className = 'input-group input-group-sm';
      item.dataset.linkItem = 'true';
      item.innerHTML = `
        <input type="text" class="form-control form-control-sm" data-link-input placeholder="https://..." value="${escapeHtml(value)}">
        <button class="btn btn-outline-danger" type="button" data-it-equipment-link-action="remove-link">&times;</button>
      `;
      container.appendChild(item);
      ensureEquipmentLinksPlaceholder(container);
      return item;
    }

    function renderEquipmentLinks(container, links) {
      if (!container) return;
      container.innerHTML = '';
      const list = Array.isArray(links) ? links : [];
      if (!list.length) {
        ensureEquipmentLinksPlaceholder(container);
        return;
      }
      list.forEach((link) => {
        addEquipmentLinkInput(container, link);
      });
      ensureEquipmentLinksPlaceholder(container);
    }

    function collectEquipmentLinks(container) {
      if (!container) return [];
      return Array.from(container.querySelectorAll('[data-link-input]'))
        .map((input) => (input.value || '').trim())
        .filter(Boolean);
    }

    function collectEquipmentOptionSets() {
      const result = {
        types: new Set(),
        vendors: new Set(),
        models: new Set(),
      };
      const items = Array.isArray(getParameterData().it_connection) ? getParameterData().it_connection : [];
      items.forEach((item) => {
        if (!item) return;
        const category = typeof options.normalizeItConnectionCategory === 'function'
          ? options.normalizeItConnectionCategory(
              (item && item.category) || (item && item.extra && item.extra.category)
            )
          : ((item && item.category) || (item && item.extra && item.extra.category) || '');
        const values = {
          equipment_type: typeof item.equipment_type === 'string' ? item.equipment_type.trim() : '',
          equipment_vendor: typeof item.equipment_vendor === 'string' ? item.equipment_vendor.trim() : '',
          equipment_model: typeof item.equipment_model === 'string' ? item.equipment_model.trim() : '',
          value: typeof item.value === 'string' ? item.value.trim() : '',
        };
        if (category === 'equipment_type') {
          if (values.equipment_type) result.types.add(values.equipment_type);
          if (values.value) result.types.add(values.value);
        } else if (category === 'equipment_vendor') {
          if (values.equipment_vendor) result.vendors.add(values.equipment_vendor);
          if (values.value) result.vendors.add(values.value);
        } else if (category === 'equipment_model') {
          if (values.equipment_model) result.models.add(values.equipment_model);
          if (values.value) result.models.add(values.value);
        }
      });
      const sorter = (a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' });
      return {
        types: Array.from(result.types).sort(sorter),
        vendors: Array.from(result.vendors).sort(sorter),
        models: Array.from(result.models).sort(sorter),
      };
    }

    function buildEquipmentSelectOptions(values, selectedValue) {
      const list = Array.isArray(values) ? values : [];
      const normalizedSelected = typeof selectedValue === 'string' ? selectedValue.trim() : '';
      const optionsHtml = ['<option value="">—</option>'];
      const seen = new Set();
      list.forEach((raw) => {
        const value = typeof raw === 'string' ? raw.trim() : '';
        if (!value || seen.has(value)) return;
        const selectedAttr = value === normalizedSelected ? ' selected' : '';
        optionsHtml.push(`<option value="${escapeHtml(value)}"${selectedAttr}>${escapeHtml(value)}</option>`);
        seen.add(value);
      });
      if (normalizedSelected && !seen.has(normalizedSelected)) {
        optionsHtml.push(
          `<option value="${escapeHtml(normalizedSelected)}" selected>${escapeHtml(normalizedSelected)}</option>`
        );
      }
      return optionsHtml.join('');
    }

    function renderItEquipmentTable() {
      if (!elements.itEquipmentBody) return;
      const list = getItems();
      elements.itEquipmentBody.innerHTML = '';
      if (!list.length) {
        const emptyRow = document.createElement('tr');
        emptyRow.innerHTML = '<td colspan="7" class="text-muted text-center">Пока нет оборудования</td>';
        elements.itEquipmentBody.appendChild(emptyRow);
        return;
      }
      const optionSets = collectEquipmentOptionSets();
      list.forEach((item) => {
        const row = document.createElement('tr');
        const idNumber = Number.parseInt(item && item.id, 10);
        if (Number.isFinite(idNumber)) {
          row.dataset.id = String(idNumber);
        }
        const equipmentType = typeof (item && item.equipment_type) === 'string' ? item.equipment_type : '';
        const equipmentVendor = typeof (item && item.equipment_vendor) === 'string' ? item.equipment_vendor : '';
        const equipmentModel = typeof (item && item.equipment_model) === 'string' ? item.equipment_model : '';
        const serialNumber = typeof (item && item.serial_number) === 'string' ? item.serial_number : '';
        const accessories = typeof (item && item.accessories) === 'string'
          ? item.accessories
          : typeof (item && item.additional_equipment) === 'string'
            ? item.additional_equipment
            : '';
        const links = parseEquipmentLinks(item && item.photo_url);
        row.innerHTML = `
          <td><select class="form-select form-select-sm" data-field="equipment_type">${buildEquipmentSelectOptions(optionSets.types, equipmentType)}</select></td>
          <td><select class="form-select form-select-sm" data-field="equipment_vendor">${buildEquipmentSelectOptions(optionSets.vendors, equipmentVendor)}</select></td>
          <td><select class="form-select form-select-sm" data-field="equipment_model">${buildEquipmentSelectOptions(optionSets.models, equipmentModel)}</select></td>
          <td><input type="text" class="form-control form-control-sm" data-field="serial_number" value="${escapeHtml(serialNumber)}" placeholder="Серийный номер"></td>
          <td><textarea class="form-control form-control-sm" data-field="accessories" rows="2" placeholder="Комплектация / аксессуары">${escapeHtml(accessories)}</textarea></td>
          <td>
            <div class="d-flex flex-column gap-2" data-links-container></div>
            <button class="btn btn-sm btn-outline-secondary align-self-start" type="button" data-it-equipment-link-action="add-link">+ Ссылка</button>
          </td>
          <td class="text-nowrap">
            <button class="btn btn-sm btn-outline-success" type="button" data-it-equipment-action="save">Сохранить</button>
            <button class="btn btn-sm btn-outline-danger" type="button" data-it-equipment-action="delete">Удалить</button>
          </td>
        `;
        const linksContainer = row.querySelector('[data-links-container]');
        renderEquipmentLinks(linksContainer, links);
        elements.itEquipmentBody.appendChild(row);
      });
    }

    function collectItEquipmentPayload(row) {
      if (!row) return {};
      const selectValue = (selector) => {
        const element = row.querySelector(selector);
        return element ? element.value.trim() : '';
      };
      return {
        equipment_type: selectValue('[data-field="equipment_type"]'),
        equipment_vendor: selectValue('[data-field="equipment_vendor"]'),
        equipment_model: selectValue('[data-field="equipment_model"]'),
        serial_number: selectValue('[data-field="serial_number"]'),
        accessories: selectValue('[data-field="accessories"]'),
        photo_url: formatEquipmentLinksPayload(collectEquipmentLinks(row.querySelector('[data-links-container]'))),
      };
    }

    async function saveItEquipmentRow(row) {
      if (!row) return;
      const id = Number.parseInt(row.dataset.id, 10);
      if (!Number.isFinite(id)) return;
      const payload = collectItEquipmentPayload(row);
      if (!payload.equipment_type) {
        popup('Укажите тип оборудования');
        const select = row.querySelector('[data-field="equipment_type"]');
        if (select) select.focus();
        return;
      }
      if (!payload.equipment_vendor) {
        popup('Укажите производителя оборудования');
        const select = row.querySelector('[data-field="equipment_vendor"]');
        if (select) select.focus();
        return;
      }
      if (!payload.equipment_model) {
        popup('Укажите модель оборудования');
        const select = row.querySelector('[data-field="equipment_model"]');
        if (select) select.focus();
        return;
      }
      const button = row.querySelector('[data-it-equipment-action="save"]');
      const originalText = button ? button.textContent : '';
      if (button) {
        button.disabled = true;
        button.textContent = '...';
      }
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Ошибка сохранения оборудования');
        }
        if (Array.isArray(data.items)) {
          setItems(data.items);
        } else if (data.item) {
          const updatedId = Number.parseInt(data.item.id, 10);
          const nextItems = getItems().slice();
          const index = nextItems.findIndex((entry) => Number.parseInt(entry.id, 10) === updatedId);
          if (index >= 0) {
            nextItems[index] = data.item;
            setItems(nextItems);
          }
        }
        renderItEquipmentTable();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) {
          button.disabled = false;
          button.textContent = originalText || 'Сохранить';
        }
      }
    }

    async function deleteItEquipmentRow(row) {
      if (!row) return;
      const id = Number.parseInt(row.dataset.id, 10);
      if (!Number.isFinite(id)) return;
      if (!confirmAction('Удалить оборудование?')) return;
      const button = row.querySelector('[data-it-equipment-action="delete"]');
      const originalText = button ? button.textContent : '';
      if (button) {
        button.disabled = true;
        button.textContent = '...';
      }
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Ошибка удаления оборудования');
        }
        setItems(Array.isArray(data.items) ? data.items : []);
        renderItEquipmentTable();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) {
          button.disabled = false;
          button.textContent = originalText || 'Удалить';
        }
      }
    }

    async function loadItEquipment() {
      if (!elements.itEquipmentBody) return;
      try {
        const response = await fetch('/api/settings/it-equipment');
        if (!response.ok) {
          throw new Error('Ошибка загрузки оборудования');
        }
        const data = await response.json();
        if (data && data.success === false) {
          throw new Error(data.error || 'Ошибка загрузки оборудования');
        }
        setItems(Array.isArray(data && data.items) ? data.items : []);
        renderItEquipmentTable();
      } catch (error) {
        console.error('Ошибка загрузки каталога оборудования:', error);
      }
    }

    function populateItEquipmentAddOptions(selectedValues = {}) {
      const optionSets = collectEquipmentOptionSets();
      const ensureOptions = (select, values, emptyLabel, key) => {
        if (!select) return false;
        const list = Array.isArray(values) ? values : [];
        const selectedValue = selectedValues[key] || '';
        if (!list.length) {
          select.innerHTML = `<option value="" disabled selected>${escapeHtml(emptyLabel)}</option>`;
          select.disabled = true;
          select.value = '';
          select.classList.remove('is-invalid');
          return false;
        }
        select.disabled = false;
        select.innerHTML = buildEquipmentSelectOptions(list, selectedValue);
        select.value = selectedValue || '';
        select.classList.remove('is-invalid');
        return true;
      };
      const hasType = ensureOptions(
        addModal.typeSelect,
        optionSets.types,
        'Добавьте тип в разделе «Подключения»',
        'equipment_type'
      );
      const hasVendor = ensureOptions(
        addModal.vendorSelect,
        optionSets.vendors,
        'Добавьте производителя в разделе «Подключения»',
        'equipment_vendor'
      );
      const hasModel = ensureOptions(
        addModal.modelSelect,
        optionSets.models,
        'Добавьте модель в разделе «Подключения»',
        'equipment_model'
      );
      return hasType && hasVendor && hasModel;
    }

    function prepareItEquipmentAddSettingsModal() {
      const hasOptions = populateItEquipmentAddOptions();
      if (addModal.serialNumberInput) {
        addModal.serialNumberInput.value = '';
      }
      if (addModal.accessoriesInput) {
        addModal.accessoriesInput.value = '';
      }
      if (addModal.linksContainer) {
        renderEquipmentLinks(addModal.linksContainer, []);
      }
      if (addModal.submitButton) {
        addModal.submitButton.disabled = !hasOptions;
      }
    }

    function handleTableClick(event) {
      const linkButton = event.target.closest('[data-it-equipment-link-action]');
      if (linkButton) {
        const row = linkButton.closest('tr');
        if (!row) return;
        const action = linkButton.dataset.itEquipmentLinkAction;
        const container = row.querySelector('[data-links-container]');
        if (!container) return;
        if (action === 'add-link') {
          const item = addEquipmentLinkInput(container, '');
          const input = item ? item.querySelector('[data-link-input]') : null;
          if (input) {
            input.focus();
          }
        } else if (action === 'remove-link') {
          const item = linkButton.closest('[data-link-item]');
          if (item) {
            item.remove();
            ensureEquipmentLinksPlaceholder(container);
          }
        }
        return;
      }

      const button = event.target.closest('[data-it-equipment-action]');
      if (!button) return;
      const row = button.closest('tr');
      if (!row) return;
      const action = button.dataset.itEquipmentAction;
      if (action === 'save') {
        saveItEquipmentRow(row);
      } else if (action === 'delete') {
        deleteItEquipmentRow(row);
      }
    }

    function handleAddModalClick(event) {
      const addLinkButton = event.target.closest('[data-it-equipment-add-link]');
      if (addLinkButton) {
        if (addModal.linksContainer) {
          const item = addEquipmentLinkInput(addModal.linksContainer, '');
          const input = item ? item.querySelector('[data-link-input]') : null;
          if (input) {
            input.focus();
          }
        }
        return;
      }
      const removeButton = event.target.closest('[data-it-equipment-link-action="remove-link"]');
      if (removeButton && addModal.linksContainer) {
        const item = removeButton.closest('[data-link-item]');
        if (item) {
          item.remove();
          ensureEquipmentLinksPlaceholder(addModal.linksContainer);
        }
      }
    }

    async function handleAddFormSubmit(event) {
      event.preventDefault();
      const payload = {
        equipment_type:
          addModal.typeSelect && !addModal.typeSelect.disabled
            ? addModal.typeSelect.value.trim()
            : '',
        equipment_vendor:
          addModal.vendorSelect && !addModal.vendorSelect.disabled
            ? addModal.vendorSelect.value.trim()
            : '',
        equipment_model:
          addModal.modelSelect && !addModal.modelSelect.disabled
            ? addModal.modelSelect.value.trim()
            : '',
        serial_number: addModal.serialNumberInput ? addModal.serialNumberInput.value.trim() : '',
        accessories: addModal.accessoriesInput ? addModal.accessoriesInput.value.trim() : '',
        photo_url: formatEquipmentLinksPayload(collectEquipmentLinks(addModal.linksContainer)),
      };

      let hasError = false;
      if (!payload.equipment_type) {
        if (addModal.typeSelect) {
          addModal.typeSelect.classList.add('is-invalid');
        }
        hasError = true;
      }
      if (!payload.equipment_vendor) {
        if (addModal.vendorSelect) {
          addModal.vendorSelect.classList.add('is-invalid');
        }
        hasError = true;
      }
      if (!payload.equipment_model) {
        if (addModal.modelSelect) {
          addModal.modelSelect.classList.add('is-invalid');
        }
        hasError = true;
      }

      if (hasError) {
        const firstInvalid = [
          addModal.typeSelect,
          addModal.vendorSelect,
          addModal.modelSelect,
        ].find((element) => element && element.classList.contains('is-invalid'));
        if (firstInvalid) {
          firstInvalid.focus();
        }
        return;
      }

      const button = addModal.submitButton;
      const originalText = button ? button.textContent : '';
      if (button) {
        button.disabled = true;
        button.textContent = '...';
      }

      try {
        const response = await fetch('/api/settings/it-equipment', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Ошибка создания оборудования');
        }
        if (Array.isArray(data.items)) {
          setItems(data.items);
        } else if (data.item) {
          setItems(getItems().concat([data.item]));
        }
        renderItEquipmentTable();
        if (elements.itEquipmentAddModalEl) {
          requestClose(addModal.form);
        }
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) {
          button.disabled = false;
          button.textContent = originalText || 'Добавить';
        }
      }
    }

    function bindEvents() {
      if (elements.itEquipmentBody) {
        elements.itEquipmentBody.addEventListener('click', handleTableClick);
      }
      if (elements.itEquipmentAddModalEl) {
        elements.itEquipmentAddModalEl.addEventListener('click', handleAddModalClick);
      }
      [addModal.typeSelect, addModal.vendorSelect, addModal.modelSelect].forEach((select) => {
        if (!select) return;
        select.addEventListener('change', () => {
          select.classList.remove('is-invalid');
        });
      });
      if (addModal.form) {
        addModal.form.addEventListener('submit', handleAddFormSubmit);
      }
    }

    bindEvents();

    return {
      renderItEquipmentTable,
      loadItEquipment,
      prepareItEquipmentAddSettingsModal,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsItEquipmentRuntime) {
        return window.__settingsItEquipmentRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.register('prepareItEquipmentAddSettingsModal', runtime.prepareItEquipmentAddSettingsModal);
      window.__settingsItEquipmentRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsItEquipmentRuntime = Object.freeze(api);
}());

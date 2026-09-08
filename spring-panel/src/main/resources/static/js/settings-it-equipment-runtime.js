(function () {
  if (window.SettingsItEquipmentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      items: [],
      query: '',
      editingId: null,
    };

    const elements = {
      itEquipmentBody: document.getElementById('itEquipmentBody'),
      itEquipmentAddModalEl: document.getElementById('itEquipmentAddModal'),
      searchInput: document.getElementById('itEquipmentSearchInput'),
      countBadge: document.getElementById('itEquipmentCountBadge'),
      emptyState: document.getElementById('itEquipmentEmptyState'),
    };

    const addModal = {
      form: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-add-form]')
        : null,
      title: document.getElementById('itEquipmentAddModalLabel'),
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
        return;
      }
      if (elements.itEquipmentAddModalEl && window.bootstrap && window.bootstrap.Modal) {
        window.bootstrap.Modal.getOrCreateInstance(elements.itEquipmentAddModalEl).hide();
      }
    }

    function confirmAction(message) {
      if (typeof options.confirmDialog === 'function') {
        return Boolean(options.confirmDialog(message));
      }
      return window.confirm(message);
    }

    function normalize(value) {
      return String(value ?? '').trim().toLocaleLowerCase('ru-RU').replace(/\s+/g, ' ');
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
        if (!trimmed) return [];
        if (trimmed.startsWith('[')) {
          try {
            const parsed = JSON.parse(trimmed);
            if (Array.isArray(parsed)) {
              return parsed.map((item) => (item || '').toString().trim()).filter(Boolean);
            }
          } catch (error) {
            // fallback below
          }
        }
        return trimmed.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
      }
      return [];
    }

    function formatEquipmentLinksPayload(links) {
      const normalized = Array.isArray(links)
        ? links.map((item) => (item || '').toString().trim()).filter(Boolean)
        : [];
      if (!normalized.length) return '';
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
      list.forEach((link) => addEquipmentLinkInput(container, link));
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
      getItems().forEach((item) => {
        if (item && item.equipment_type) result.types.add(String(item.equipment_type).trim());
        if (item && item.equipment_vendor) result.vendors.add(String(item.equipment_vendor).trim());
        if (item && item.equipment_model) result.models.add(String(item.equipment_model).trim());
      });
      const sorter = (a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' });
      return {
        types: Array.from(result.types).filter(Boolean).sort(sorter),
        vendors: Array.from(result.vendors).filter(Boolean).sort(sorter),
        models: Array.from(result.models).filter(Boolean).sort(sorter),
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
        optionsHtml.push(`<option value="${escapeHtml(normalizedSelected)}" selected>${escapeHtml(normalizedSelected)}</option>`);
      }
      return optionsHtml.join('');
    }

    function firstLink(item) {
      return parseEquipmentLinks(item && item.photo_url)[0] || '';
    }

    function cardMatches(item) {
      const query = normalize(state.query);
      if (!query) return true;
      return normalize([
        item && item.equipment_type,
        item && item.equipment_vendor,
        item && item.equipment_model,
        item && item.serial_number,
        item && item.accessories,
      ].join(' ')).includes(query);
    }

    function renderCard(item) {
      const id = Number.parseInt(item && item.id, 10);
      const type = (item && item.equipment_type) || 'Оборудование';
      const vendor = (item && item.equipment_vendor) || '';
      const model = (item && item.equipment_model) || '';
      const serial = (item && item.serial_number) || '';
      const accessories = (item && (item.accessories || item.additional_equipment)) || '';
      const links = parseEquipmentLinks(item && item.photo_url);
      const cover = firstLink(item);
      const title = [vendor, model].filter(Boolean).join(' ') || model || vendor || type;
      const glyph = String(type || 'IT').trim().slice(0, 2).toUpperCase();
      const discovered = item && item.discovered === true;
      const itemIndex = getItems().indexOf(item);
      const usageCount = Number.parseInt(item && item.usage_count, 10) || 0;

      return `
        <article class="it-equipment-catalog-card ${discovered ? 'is-discovered' : ''}" data-id="${Number.isFinite(id) ? id : ''}" data-item-index="${itemIndex}">
          <div class="it-equipment-catalog-card__visual ${cover ? 'has-image' : ''}">
            ${cover ? `<img src="${escapeHtml(cover)}" alt="${escapeHtml(title)}" loading="lazy" onerror="this.parentElement.classList.remove('has-image');this.remove();">` : ''}
            <span>${escapeHtml(glyph)}</span>
          </div>
          <div class="it-equipment-catalog-card__body">
            <div class="it-equipment-catalog-card__top">
              <div>
                <span class="it-equipment-catalog-card__type">${escapeHtml(type)}</span>
                <h6>${escapeHtml(title)}</h6>
              </div>
              ${discovered ? '<span class="it-equipment-catalog-card__source">Из паспортов</span>' : (Number.isFinite(id) ? `<span class="it-equipment-catalog-card__id">#${id}</span>` : '')}
            </div>
            <div class="it-equipment-catalog-card__meta">
              ${serial ? `<span><small>SN</small>${escapeHtml(serial)}</span>` : ''}
              <span><small>Ссылки</small>${links.length}</span>
              ${usageCount ? `<span><small>Объектов/экз.</small>${usageCount}</span>` : ''}
            </div>
            ${accessories ? `<p class="it-equipment-catalog-card__accessories">${escapeHtml(accessories)}</p>` : '<p class="it-equipment-catalog-card__accessories text-muted">Комплектация не указана</p>'}
            <div class="it-equipment-catalog-card__actions">
              ${links[0] ? `<a class="btn btn-sm btn-outline-secondary" href="${escapeHtml(links[0])}" target="_blank" rel="noopener">Открыть</a>` : ''}
              ${discovered
                ? '<button class="btn btn-sm btn-primary" type="button" data-it-equipment-action="promote">Добавить в каталог</button>'
                : '<button class="btn btn-sm btn-outline-primary" type="button" data-it-equipment-action="edit">Изменить</button><button class="btn btn-sm btn-outline-danger" type="button" data-it-equipment-action="delete">Удалить</button>'}
            </div>
          </div>
        </article>
      `;
    }

    function renderItEquipmentTable() {
      if (!elements.itEquipmentBody) return;
      const list = getItems().filter(cardMatches);
      elements.itEquipmentBody.innerHTML = list.map(renderCard).join('');
      if (elements.countBadge) {
        elements.countBadge.textContent = state.query
          ? `${list.length} из ${getItems().length}`
          : String(getItems().length);
      }
      if (elements.emptyState) {
        elements.emptyState.classList.toggle('d-none', list.length > 0);
        elements.emptyState.textContent = state.query
          ? 'По этому запросу ничего не найдено.'
          : 'Пока нет оборудования.';
      }
    }

    async function loadItEquipment() {
      if (!elements.itEquipmentBody) return;
      try {
        const response = await fetch('/api/settings/it-equipment');
        if (!response.ok) throw new Error('Ошибка загрузки оборудования');
        const data = await response.json();
        if (data && data.success === false) throw new Error(data.error || 'Ошибка загрузки оборудования');
        setItems(Array.isArray(data && data.items) ? data.items : []);
        renderItEquipmentTable();
      } catch (error) {
        console.error('Ошибка загрузки каталога оборудования:', error);
        if (elements.emptyState) {
          elements.emptyState.textContent = 'Не удалось загрузить каталог оборудования.';
          elements.emptyState.classList.remove('d-none');
        }
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
      const hasType = ensureOptions(addModal.typeSelect, optionSets.types, 'Добавьте тип в разделе «Оборудование»', 'equipment_type');
      ensureOptions(addModal.vendorSelect, optionSets.vendors, 'Производитель не обязателен', 'equipment_vendor');
      const hasModel = ensureOptions(addModal.modelSelect, optionSets.models, 'Добавьте модель в разделе «Оборудование»', 'equipment_model');
      return hasType && hasModel;
    }

    function setModalMode(item) {
      const editing = item && Number.isFinite(Number.parseInt(item.id, 10));
      state.editingId = editing ? Number.parseInt(item.id, 10) : null;
      const selected = editing ? {
        equipment_type: item.equipment_type || '',
        equipment_vendor: item.equipment_vendor || '',
        equipment_model: item.equipment_model || '',
      } : {};
      const hasOptions = populateItEquipmentAddOptions(selected);
      if (addModal.serialNumberInput) addModal.serialNumberInput.value = editing ? (item.serial_number || '') : '';
      if (addModal.accessoriesInput) addModal.accessoriesInput.value = editing ? (item.accessories || item.additional_equipment || '') : '';
      if (addModal.linksContainer) renderEquipmentLinks(addModal.linksContainer, editing ? parseEquipmentLinks(item.photo_url) : []);
      if (addModal.title) addModal.title.textContent = editing ? 'Карточка модели оборудования' : 'Новое оборудование';
      if (addModal.submitButton) {
        addModal.submitButton.disabled = !hasOptions;
        addModal.submitButton.textContent = editing ? 'Сохранить' : 'Добавить';
      }
    }

    function prepareItEquipmentAddSettingsModal() {
      // The shared settings modal lifecycle fires for both create and edit opens.
      // Keep an already selected persisted card intact when Bootstrap emits "show".
      if (state.editingId !== null) {
        return;
      }
      setModalMode(null);
    }

    function showEditModal(item) {
      if (!elements.itEquipmentAddModalEl) return;
      setModalMode(item);
      if (window.bootstrap && window.bootstrap.Modal) {
        window.bootstrap.Modal.getOrCreateInstance(elements.itEquipmentAddModalEl).show();
      } else {
        popup('Не удалось открыть окно редактирования');
      }
    }

    function collectModalPayload() {
      return {
        equipment_type: addModal.typeSelect && !addModal.typeSelect.disabled ? addModal.typeSelect.value.trim() : '',
        equipment_vendor: addModal.vendorSelect && !addModal.vendorSelect.disabled ? addModal.vendorSelect.value.trim() : '',
        equipment_model: addModal.modelSelect && !addModal.modelSelect.disabled ? addModal.modelSelect.value.trim() : '',
        serial_number: addModal.serialNumberInput ? addModal.serialNumberInput.value.trim() : '',
        accessories: addModal.accessoriesInput ? addModal.accessoriesInput.value.trim() : '',
        photo_url: formatEquipmentLinksPayload(collectEquipmentLinks(addModal.linksContainer)),
      };
    }

    async function deleteItem(item) {
      const id = Number.parseInt(item && item.id, 10);
      if (!Number.isFinite(id)) return;
      const title = [(item && item.equipment_vendor) || '', (item && item.equipment_model) || ''].filter(Boolean).join(' ');
      if (!confirmAction(`Удалить ${title || 'оборудование'} из каталога?`)) return;
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Ошибка удаления оборудования');
        setItems(Array.isArray(data.items) ? data.items : getItems().filter((entry) => Number.parseInt(entry.id, 10) !== id));
        renderItEquipmentTable();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      }
    }


    async function promoteDiscoveredItem(item) {
      if (!item || item.discovered !== true) return;
      const payload = {
        equipment_type: String(item.equipment_type || '').trim(),
        equipment_vendor: String(item.equipment_vendor || '').trim(),
        equipment_model: String(item.equipment_model || '').trim(),
        serial_number: '',
        accessories: String(item.accessories || '').trim(),
        photo_url: item.photo_url || '',
      };
      try {
        const response = await fetch('/api/settings/it-equipment', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Ошибка добавления модели в каталог');
        if (Array.isArray(data.items)) setItems(data.items);
        renderItEquipmentTable();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      }
    }

    function handleCardClick(event) {
      const button = event.target.closest('[data-it-equipment-action]');
      if (!button) return;
      const card = button.closest('[data-id]');
      if (!card) return;
      const id = Number.parseInt(card.dataset.id, 10);
      const itemIndex = Number.parseInt(card.dataset.itemIndex, 10);
      const item = Number.isFinite(id)
        ? getItems().find((entry) => Number.parseInt(entry && entry.id, 10) === id)
        : getItems()[itemIndex];
      if (!item) return;
      if (button.dataset.itEquipmentAction === 'edit') {
        showEditModal(item);
      } else if (button.dataset.itEquipmentAction === 'delete') {
        deleteItem(item);
      } else if (button.dataset.itEquipmentAction === 'promote') {
        promoteDiscoveredItem(item);
      }
    }

    function handleAddModalClick(event) {
      const addLinkButton = event.target.closest('[data-it-equipment-add-link]');
      if (addLinkButton) {
        if (addModal.linksContainer) {
          const item = addEquipmentLinkInput(addModal.linksContainer, '');
          const input = item ? item.querySelector('[data-link-input]') : null;
          if (input) input.focus();
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
      const payload = collectModalPayload();
      let hasError = false;
      [
        ['equipment_type', addModal.typeSelect],
        ['equipment_model', addModal.modelSelect],
      ].forEach(([key, element]) => {
        if (!payload[key] && element) {
          element.classList.add('is-invalid');
          hasError = true;
        }
      });
      if (hasError) {
        const firstInvalid = [addModal.typeSelect, addModal.modelSelect]
          .find((element) => element && element.classList.contains('is-invalid'));
        if (firstInvalid) firstInvalid.focus();
        return;
      }

      const button = addModal.submitButton;
      const originalText = button ? button.textContent : '';
      if (button) {
        button.disabled = true;
        button.textContent = '...';
      }
      try {
        const editId = state.editingId;
        const response = await fetch(editId ? `/api/settings/it-equipment/${editId}` : '/api/settings/it-equipment', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || (editId ? 'Ошибка сохранения оборудования' : 'Ошибка создания оборудования'));
        }
        if (Array.isArray(data.items)) setItems(data.items);
        renderItEquipmentTable();
        state.editingId = null;
        requestClose(addModal.form);
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) {
          button.disabled = false;
          button.textContent = originalText || (state.editingId ? 'Сохранить' : 'Добавить');
        }
      }
    }

    function bindEvents() {
      if (elements.itEquipmentBody) elements.itEquipmentBody.addEventListener('click', handleCardClick);
      if (elements.searchInput) {
        elements.searchInput.addEventListener('input', () => {
          state.query = elements.searchInput.value || '';
          renderItEquipmentTable();
        });
      }
      if (elements.itEquipmentAddModalEl) elements.itEquipmentAddModalEl.addEventListener('click', handleAddModalClick);
      [addModal.typeSelect, addModal.vendorSelect, addModal.modelSelect].forEach((select) => {
        if (!select) return;
        select.addEventListener('change', () => select.classList.remove('is-invalid'));
      });
      if (addModal.form) addModal.form.addEventListener('submit', handleAddFormSubmit);
      if (elements.itEquipmentAddModalEl) {
        elements.itEquipmentAddModalEl.addEventListener('hidden.bs.modal', () => {
          state.editingId = null;
        });
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

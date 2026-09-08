(function () {
  if (window.SettingsItEquipmentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      items: [],
      query: '',
      typeFilter: '',
      vendorFilter: '',
      editingId: null,
    };

    const elements = {
      itEquipmentBody: document.getElementById('itEquipmentBody'),
      itEquipmentAddModalEl: document.getElementById('itEquipmentAddModal'),
      searchInput: document.getElementById('itEquipmentSearchInput'),
      typeFilter: document.getElementById('itEquipmentTypeFilter'),
      vendorFilter: document.getElementById('itEquipmentVendorFilter'),
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
      photosContainer: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-list]')
        : null,
      photoFileInput: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-file]')
        : null,
      photoCategorySelect: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-category]')
        : null,
      photoCommentInput: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-comment]')
        : null,
      photoUploadButton: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-upload]')
        : null,
      photoHint: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-hint]')
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
      populateCatalogFilters();
    }

    function parseEquipmentMedia(raw) {
      const empty = { links: [], photos: [] };
      if (Array.isArray(raw)) {
        return { links: raw.map((item) => String(item || '').trim()).filter(Boolean), photos: [] };
      }
      if (raw && typeof raw === 'object') {
        const links = Array.isArray(raw.links) ? raw.links.map((item) => String(item || '').trim()).filter(Boolean) : [];
        const photos = Array.isArray(raw.photos) ? raw.photos.filter((item) => item && typeof item === 'object') : [];
        return { links, photos };
      }
      if (typeof raw !== 'string') return empty;
      const trimmed = raw.trim();
      if (!trimmed) return empty;
      if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
        try {
          return parseEquipmentMedia(JSON.parse(trimmed));
        } catch (error) {
          // fallback below
        }
      }
      return { links: trimmed.split(/\r?\n/).map((item) => item.trim()).filter(Boolean), photos: [] };
    }

    function parseEquipmentLinks(raw) {
      return parseEquipmentMedia(raw).links;
    }

    function parseEquipmentPhotos(raw) {
      return parseEquipmentMedia(raw).photos;
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

    function firstPhoto(item) {
      const photos = parseEquipmentPhotos(item && item.photo_url);
      const title = photos.find((photo) => normalize(photo && photo.category) === 'title');
      const selected = title || photos[0] || null;
      return selected && selected.url ? String(selected.url).trim() : '';
    }

    function collectCatalogFilterValues(field) {
      const values = new Set();
      getItems().forEach((item) => {
        const value = item && item[field] != null ? String(item[field]).trim() : '';
        if (value) values.add(value);
      });
      return Array.from(values).sort((a, b) => a.localeCompare(b, 'ru-RU', { sensitivity: 'base' }));
    }

    function populateCatalogFilter(select, values, selectedValue, allLabel) {
      if (!select) return '';
      const selected = String(selectedValue || '').trim();
      const normalizedValues = Array.isArray(values) ? values : [];
      const available = normalizedValues.some((value) => normalize(value) === normalize(selected));
      const effectiveSelected = selected && available ? selected : '';
      select.innerHTML = [
        `<option value="">${escapeHtml(allLabel)}</option>`,
        ...normalizedValues.map((value) => {
          const selectedAttr = normalize(value) === normalize(effectiveSelected) ? ' selected' : '';
          return `<option value="${escapeHtml(value)}"${selectedAttr}>${escapeHtml(value)}</option>`;
        }),
      ].join('');
      select.value = effectiveSelected;
      return effectiveSelected;
    }

    function populateCatalogFilters() {
      state.typeFilter = populateCatalogFilter(
        elements.typeFilter,
        collectCatalogFilterValues('equipment_type'),
        state.typeFilter,
        'Все типы'
      );
      state.vendorFilter = populateCatalogFilter(
        elements.vendorFilter,
        collectCatalogFilterValues('equipment_vendor'),
        state.vendorFilter,
        'Все производители'
      );
    }

    function cardMatches(item) {
      if (state.typeFilter && normalize(item && item.equipment_type) !== normalize(state.typeFilter)) return false;
      if (state.vendorFilter && normalize(item && item.equipment_vendor) !== normalize(state.vendorFilter)) return false;
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
      const cover = firstPhoto(item);
      const title = [vendor, model].filter(Boolean).join(' ') || model || vendor || type;
      const discovered = item && item.discovered === true;
      const itemIndex = getItems().indexOf(item);
      const objectCount = Number.parseInt(item && (item.object_count ?? item.usage_count), 10) || 0;

      return `
        <article class="it-equipment-catalog-card ${discovered ? 'is-discovered' : ''}" data-id="${Number.isFinite(id) ? id : ''}" data-item-index="${itemIndex}">
          <div class="it-equipment-catalog-card__visual ${cover ? 'has-image' : ''}">
            <span class="it-equipment-catalog-card__visual-placeholder" aria-hidden="true"><i class="bi bi-image"></i></span>
            ${cover ? `<img src="${escapeHtml(cover)}" alt="${escapeHtml(title)}" loading="lazy" onerror="this.parentElement.classList.remove('has-image');this.remove();">` : ''}
          </div>
          <span class="it-equipment-catalog-card__type-center">${escapeHtml(type)}</span>
          <div class="it-equipment-catalog-card__body">
            <div class="it-equipment-catalog-card__top">
              <div class="it-equipment-catalog-card__identity">
                <h6>${escapeHtml(title)}</h6>
                ${discovered ? '<span class="it-equipment-catalog-card__source">Из паспортов</span>' : ''}
              </div>
              ${discovered
                ? ''
                : '<div class="it-equipment-catalog-card__quick-actions"><button class="it-equipment-catalog-card__icon-action" type="button" data-it-equipment-action="edit" aria-label="Изменить модель" title="Изменить"><i class="bi bi-pencil" aria-hidden="true"></i></button><button class="it-equipment-catalog-card__icon-action is-danger" type="button" data-it-equipment-action="delete" aria-label="Удалить модель" title="Удалить"><i class="bi bi-trash" aria-hidden="true"></i></button></div>'}
            </div>
            <div class="it-equipment-catalog-card__meta">
              <span class="it-equipment-catalog-card__usage"><small>Используется у объектов</small><strong>${objectCount}</strong></span>
              ${serial ? `<span><small>SN</small>${escapeHtml(serial)}</span>` : ''}
            </div>
            ${accessories ? `<p class="it-equipment-catalog-card__accessories">${escapeHtml(accessories)}</p>` : '<p class="it-equipment-catalog-card__accessories text-muted">Комплектация не указана</p>'}
            ${(links[0] || discovered) ? `<div class="it-equipment-catalog-card__actions">${links[0] ? `<a class="btn btn-sm btn-outline-secondary" href="${escapeHtml(links[0])}" target="_blank" rel="noopener">Открыть</a>` : ''}${discovered ? '<button class="btn btn-sm btn-primary" type="button" data-it-equipment-action="promote">Добавить в каталог</button>' : ''}</div>` : ''}
          </div>
          ${Number.isFinite(id) ? `<span class="it-equipment-catalog-card__id">#${id}</span>` : ''}
        </article>
      `;
    }

    function renderItEquipmentTable() {
      if (!elements.itEquipmentBody) return;
      const list = getItems().filter(cardMatches);
      elements.itEquipmentBody.innerHTML = list.map(renderCard).join('');
      const hasActiveFilter = Boolean(normalize(state.query) || state.typeFilter || state.vendorFilter);
      if (elements.countBadge) {
        elements.countBadge.textContent = hasActiveFilter
          ? `${list.length} из ${getItems().length}`
          : String(getItems().length);
      }
      if (elements.emptyState) {
        elements.emptyState.classList.toggle('d-none', list.length > 0);
        elements.emptyState.textContent = hasActiveFilter
          ? 'По выбранным фильтрам ничего не найдено.'
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

    function currentEditingItem() {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id)) return null;
      return getItems().find((item) => Number.parseInt(item && item.id, 10) === id) || null;
    }

    function renderEquipmentPhotoManager(item) {
      const editing = item && Number.isFinite(Number.parseInt(item.id, 10));
      const photos = editing ? parseEquipmentPhotos(item.photo_url) : [];
      if (addModal.photosContainer) {
        addModal.photosContainer.innerHTML = photos.length
          ? photos.map((photo) => {
              const id = String(photo && photo.id || '').trim();
              const url = String(photo && photo.url || '').trim();
              const category = normalize(photo && photo.category) === 'title' ? 'Титульное' : 'Общее';
              const comment = String((photo && (photo.comment || photo.caption)) || '').trim();
              return `<div class="it-equipment-photo-thumb" data-it-equipment-photo-id="${escapeHtml(id)}" title="${escapeHtml(comment)}">
                <a href="${escapeHtml(url)}" target="_blank" rel="noopener" aria-label="Открыть фото"><img src="${escapeHtml(url)}" alt="${escapeHtml(comment || category)}" loading="lazy"></a>
                <span class="it-equipment-photo-thumb__type">${escapeHtml(category)}</span>
                <button type="button" class="it-equipment-photo-thumb__delete" data-it-equipment-photo-delete="${escapeHtml(id)}" aria-label="Удалить фото" title="Удалить фото"><i class="bi bi-x-lg" aria-hidden="true"></i></button>
              </div>`;
            }).join('')
          : '<div class="it-equipment-photo-empty">Фото пока нет.</div>';
      }
      [addModal.photoFileInput, addModal.photoCategorySelect, addModal.photoCommentInput, addModal.photoUploadButton]
        .forEach((element) => { if (element) element.disabled = !editing; });
      if (addModal.photoHint) {
        addModal.photoHint.textContent = editing
          ? 'Каждое фото загружается отдельно: тип и комментарий обязательны. Титульное фото используется на карточке модели.'
          : 'Сначала сохраните модель оборудования, затем откройте её снова и добавьте фотографии.';
      }
    }

    function updateLocalEquipmentMedia(itemId, photoUrl) {
      const item = getItems().find((entry) => Number.parseInt(entry && entry.id, 10) === Number.parseInt(itemId, 10));
      if (item) item.photo_url = photoUrl || '';
      renderEquipmentPhotoManager(item || currentEditingItem());
      renderItEquipmentTable();
    }

    async function uploadEquipmentPhoto() {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id)) { popup('Сначала сохраните модель оборудования'); return; }
      const file = addModal.photoFileInput && addModal.photoFileInput.files ? addModal.photoFileInput.files[0] : null;
      const category = addModal.photoCategorySelect ? addModal.photoCategorySelect.value.trim() : '';
      const comment = addModal.photoCommentInput ? addModal.photoCommentInput.value.trim() : '';
      if (!file) { popup('Выберите фото'); return; }
      if (!category) { popup('Укажите тип фото'); return; }
      if (!comment) { popup('Комментарий к фото обязателен'); return; }

      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', category);
      formData.append('comment', comment);
      const button = addModal.photoUploadButton;
      if (button) button.disabled = true;
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}/photos`, { method: 'POST', body: formData });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Ошибка загрузки фото');
        updateLocalEquipmentMedia(id, data.photo_url || '');
        if (addModal.photoFileInput) addModal.photoFileInput.value = '';
        if (addModal.photoCommentInput) addModal.photoCommentInput.value = '';
        if (addModal.photoCategorySelect) addModal.photoCategorySelect.value = 'general';
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (button) button.disabled = false;
      }
    }

    async function deleteEquipmentPhoto(photoId) {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id) || !photoId) return;
      if (!confirmAction('Удалить это фото оборудования?')) return;
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}/photos/${encodeURIComponent(photoId)}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Ошибка удаления фото');
        updateLocalEquipmentMedia(id, data.photo_url || '');
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
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
      renderEquipmentPhotoManager(editing ? item : null);
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
      const photoDelete = event.target.closest('[data-it-equipment-photo-delete]');
      if (photoDelete) {
        deleteEquipmentPhoto(photoDelete.dataset.itEquipmentPhotoDelete).catch((error) => popup('❌ ' + error));
        return;
      }
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
      if (elements.typeFilter) {
        elements.typeFilter.addEventListener('change', () => {
          state.typeFilter = elements.typeFilter.value || '';
          renderItEquipmentTable();
        });
      }
      if (elements.vendorFilter) {
        elements.vendorFilter.addEventListener('change', () => {
          state.vendorFilter = elements.vendorFilter.value || '';
          renderItEquipmentTable();
        });
      }
      if (elements.itEquipmentAddModalEl) elements.itEquipmentAddModalEl.addEventListener('click', handleAddModalClick);
      [addModal.typeSelect, addModal.vendorSelect, addModal.modelSelect].forEach((select) => {
        if (!select) return;
        select.addEventListener('change', () => select.classList.remove('is-invalid'));
      });
      if (addModal.form) addModal.form.addEventListener('submit', handleAddFormSubmit);
      if (addModal.photoUploadButton) addModal.photoUploadButton.addEventListener('click', () => uploadEquipmentPhoto());
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

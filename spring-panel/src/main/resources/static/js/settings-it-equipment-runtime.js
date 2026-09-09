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
      photoViewerIndex: 0,
      photoEditId: null,
      photoConfirmResolver: null,
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
      photoAddOpen: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-add-open]')
        : null,
      photoHint: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-hint]')
        : null,
      mainTabButton: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-main-tab]')
        : null,
      photoTabButton: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-tab]')
        : null,
      photoTabCount: elements.itEquipmentAddModalEl
        ? elements.itEquipmentAddModalEl.querySelector('[data-it-equipment-photo-count]')
        : null,
      submitButton: null,
    };

    addModal.submitButton = addModal.form
      ? addModal.form.querySelector('button[type="submit"]')
      : null;

    const photoUi = {
      addModalEl: document.getElementById('itEquipmentPhotoAddModal'),
      addFile: document.querySelector('[data-it-equipment-photo-add-file]'),
      addCategory: document.querySelector('[data-it-equipment-photo-add-category]'),
      addComment: document.querySelector('[data-it-equipment-photo-add-comment]'),
      addSave: document.querySelector('[data-it-equipment-photo-add-save]'),
      viewerModalEl: document.getElementById('itEquipmentPhotoViewerModal'),
      viewerImage: document.querySelector('[data-it-equipment-photo-viewer-image]'),
      viewerType: document.querySelector('[data-it-equipment-photo-viewer-type]'),
      viewerComment: document.querySelector('[data-it-equipment-photo-viewer-comment]'),
      viewerCounter: document.querySelector('[data-it-equipment-photo-viewer-counter]'),
      viewerPrev: document.querySelector('[data-it-equipment-photo-viewer-prev]'),
      viewerNext: document.querySelector('[data-it-equipment-photo-viewer-next]'),
      editModalEl: document.getElementById('itEquipmentPhotoEditModal'),
      editPreview: document.querySelector('[data-it-equipment-photo-edit-preview]'),
      editFile: document.querySelector('[data-it-equipment-photo-edit-file]'),
      editCategory: document.querySelector('[data-it-equipment-photo-edit-category]'),
      editComment: document.querySelector('[data-it-equipment-photo-edit-comment]'),
      editSave: document.querySelector('[data-it-equipment-photo-edit-save]'),
      confirmModalEl: document.getElementById('itEquipmentPhotoConfirmModal'),
      confirmTitle: document.querySelector('[data-it-equipment-photo-confirm-title]'),
      confirmMessage: document.querySelector('[data-it-equipment-photo-confirm-message]'),
      confirmButton: document.querySelector('[data-it-equipment-photo-confirm-accept]'),
    };

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

    function equipmentPhotos() {
      const item = currentEditingItem();
      return item ? parseEquipmentPhotos(item.photo_url) : [];
    }

    function photoCategoryLabel(photo) {
      return normalize(photo && photo.category) === 'title' ? 'Титульное' : 'Общее';
    }

    function photoById(photoId) {
      const normalizedId = String(photoId || '').trim();
      return equipmentPhotos().find((photo) => String(photo && photo.id || '').trim() === normalizedId) || null;
    }

    function renderEquipmentPhotoManager(item) {
      const editing = item && Number.isFinite(Number.parseInt(item.id, 10));
      const photos = editing ? parseEquipmentPhotos(item.photo_url) : [];
      if (addModal.photosContainer) {
        addModal.photosContainer.innerHTML = photos.length
          ? photos.map((photo) => {
              const id = String(photo && photo.id || '').trim();
              const url = String(photo && photo.url || '').trim();
              const category = photoCategoryLabel(photo);
              const comment = String((photo && (photo.comment || photo.caption)) || '').trim();
              const originalName = String((photo && photo.original_name) || '').trim();
              return `<div class="it-equipment-photo-thumb" data-it-equipment-photo-id="${escapeHtml(id)}">
                <button class="it-equipment-photo-thumb__preview" type="button" data-it-equipment-photo-preview="${escapeHtml(id)}" aria-label="Открыть фото: ${escapeHtml(comment || category)}">
                  <img src="${escapeHtml(url)}" alt="${escapeHtml(comment || category)}" loading="lazy">
                </button>
                <div class="it-equipment-photo-thumb__meta">
                  <div class="it-equipment-photo-thumb__line">
                    <span class="it-equipment-photo-thumb__type">${escapeHtml(category)}</span>
                    ${originalName ? `<span class="it-equipment-photo-thumb__filename">${escapeHtml(originalName)}</span>` : ''}
                  </div>
                  <span class="it-equipment-photo-thumb__comment">${escapeHtml(comment || 'Описание не указано')}</span>
                </div>
                <div class="it-equipment-photo-thumb__actions">
                  <button type="button" class="it-equipment-photo-thumb__action" data-it-equipment-photo-edit="${escapeHtml(id)}" aria-label="Редактировать фото" title="Редактировать"><i class="bi bi-pencil" aria-hidden="true"></i></button>
                  <button type="button" class="it-equipment-photo-thumb__action is-danger" data-it-equipment-photo-delete="${escapeHtml(id)}" aria-label="Удалить фото" title="Удалить"><i class="bi bi-trash" aria-hidden="true"></i></button>
                </div>
              </div>`;
            }).join('')
          : '<div class="it-equipment-photo-empty">Фото пока нет.</div>';
      }
      if (addModal.photoAddOpen) addModal.photoAddOpen.disabled = !editing;
      if (addModal.photoTabButton) {
        addModal.photoTabButton.disabled = !editing;
        addModal.photoTabButton.setAttribute('aria-disabled', editing ? 'false' : 'true');
      }
      if (addModal.photoTabCount) addModal.photoTabCount.textContent = String(photos.length);
      if (addModal.photoHint) {
        addModal.photoHint.textContent = editing
          ? 'Фото сохраняются сразу. Для каждого фото обязательны тип и описание.'
          : 'Сначала сохраните модель оборудования, затем откройте её снова и добавьте фотографии.';
      }
    }

    function updateLocalEquipmentMedia(itemId, photoUrl) {
      const item = getItems().find((entry) => Number.parseInt(entry && entry.id, 10) === Number.parseInt(itemId, 10));
      if (item) item.photo_url = photoUrl || '';
      renderEquipmentPhotoManager(item || currentEditingItem());
      renderItEquipmentTable();
      return item || null;
    }

    function resetEquipmentModalTab() {
      if (!addModal.mainTabButton) return;
      if (window.bootstrap && window.bootstrap.Tab) {
        window.bootstrap.Tab.getOrCreateInstance(addModal.mainTabButton).show();
        return;
      }
      addModal.mainTabButton.classList.add('active');
      addModal.photoTabButton?.classList.remove('active');
      elements.itEquipmentAddModalEl?.querySelector('[data-it-equipment-main-pane]')?.classList.add('show', 'active');
      elements.itEquipmentAddModalEl?.querySelector('[data-it-equipment-photo-pane]')?.classList.remove('show', 'active');
    }

    function showPhotoConfirm({ title = 'Подтверждение', message = '', confirmLabel = 'Подтвердить', danger = false } = {}) {
      if (!photoUi.confirmModalEl || !photoUi.confirmButton || !window.bootstrap || !window.bootstrap.Modal) {
        popup(message || title);
        return Promise.resolve(false);
      }
      if (state.photoConfirmResolver) {
        state.photoConfirmResolver(false);
        state.photoConfirmResolver = null;
      }
      if (photoUi.confirmTitle) photoUi.confirmTitle.textContent = title;
      if (photoUi.confirmMessage) photoUi.confirmMessage.textContent = message;
      photoUi.confirmButton.textContent = confirmLabel;
      photoUi.confirmButton.classList.toggle('btn-danger', danger);
      photoUi.confirmButton.classList.toggle('btn-primary', !danger);
      return new Promise((resolve) => {
        state.photoConfirmResolver = resolve;
        window.bootstrap.Modal.getOrCreateInstance(photoUi.confirmModalEl).show();
      });
    }

    function resolvePhotoConfirm(value) {
      const resolver = state.photoConfirmResolver;
      state.photoConfirmResolver = null;
      if (resolver) resolver(Boolean(value));
    }

    function existingTitlePhoto(excludedPhotoId = '') {
      const excluded = String(excludedPhotoId || '').trim();
      return equipmentPhotos().find((photo) => {
        const id = String(photo && photo.id || '').trim();
        return normalize(photo && photo.category) === 'title' && (!excluded || id !== excluded);
      }) || null;
    }

    async function confirmTitleReplacement(existingTitle) {
      if (!existingTitle) return true;
      const comment = String((existingTitle.comment || existingTitle.caption) || '').trim();
      return showPhotoConfirm({
        title: 'Заменить титульное фото?',
        message: comment
          ? `Сейчас титульным является фото «${comment}». Заменить его?`
          : 'У этой модели уже есть титульное фото. Заменить его?',
        confirmLabel: 'Заменить',
      });
    }

    async function parsePhotoResponse(response, fallbackMessage) {
      let data = null;
      try {
        data = await response.json();
      } catch (error) {
        throw new Error(fallbackMessage);
      }
      if (!response.ok) throw new Error((data && data.error) || fallbackMessage);
      return data || {};
    }

    function resetPhotoAddModal() {
      if (photoUi.addFile) photoUi.addFile.value = '';
      if (photoUi.addComment) photoUi.addComment.value = '';
      if (photoUi.addCategory) photoUi.addCategory.value = existingTitlePhoto() ? 'general' : 'title';
    }

    function openPhotoAddModal() {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id)) {
        popup('Сначала сохраните модель оборудования');
        return;
      }
      if (!photoUi.addModalEl || !window.bootstrap || !window.bootstrap.Modal) {
        popup('Не удалось открыть добавление фото');
        return;
      }
      resetPhotoAddModal();
      window.bootstrap.Modal.getOrCreateInstance(photoUi.addModalEl).show();
    }

    async function sendPhotoUpload(id, file, category, comment, replaceTitle) {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', category);
      formData.append('comment', comment);
      formData.append('replace_title', replaceTitle ? 'true' : 'false');
      const response = await fetch(`/api/settings/it-equipment/${id}/photos`, {
        method: 'POST',
        body: formData,
      });
      return parsePhotoResponse(response, 'Ошибка загрузки фото');
    }

    async function uploadEquipmentPhoto() {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id)) { popup('Сначала сохраните модель оборудования'); return; }
      const file = photoUi.addFile && photoUi.addFile.files ? photoUi.addFile.files[0] : null;
      const category = photoUi.addCategory ? photoUi.addCategory.value.trim() : '';
      const comment = photoUi.addComment ? photoUi.addComment.value.trim() : '';
      if (!file) { popup('Выберите фото'); return; }
      if (!category) { popup('Укажите тип фото'); return; }
      if (!comment) { popup('Описание фото обязательно'); return; }

      let replaceTitle = false;
      const localTitle = normalize(category) === 'title' ? existingTitlePhoto() : null;
      if (localTitle) {
        const confirmed = await confirmTitleReplacement(localTitle);
        if (!confirmed) return;
        replaceTitle = true;
      }

      if (photoUi.addSave) photoUi.addSave.disabled = true;
      try {
        let data = await sendPhotoUpload(id, file, category, comment, replaceTitle);
        if (data.success === false && data.requires_confirmation === true && !replaceTitle) {
          const confirmed = await confirmTitleReplacement({ comment: data.existing_title_comment || '' });
          if (!confirmed) return;
          replaceTitle = true;
          data = await sendPhotoUpload(id, file, category, comment, true);
        }
        if (data.success === false) throw new Error(data.error || 'Ошибка загрузки фото');
        updateLocalEquipmentMedia(id, data.photo_url || '');
        resetPhotoAddModal();
        window.bootstrap.Modal.getOrCreateInstance(photoUi.addModalEl).hide();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (photoUi.addSave) photoUi.addSave.disabled = false;
      }
    }

    function renderPhotoViewer() {
      const photos = equipmentPhotos();
      if (!photos.length) return;
      state.photoViewerIndex = ((state.photoViewerIndex % photos.length) + photos.length) % photos.length;
      const photo = photos[state.photoViewerIndex] || {};
      const comment = String((photo.comment || photo.caption) || '').trim();
      if (photoUi.viewerImage) {
        photoUi.viewerImage.src = String(photo.url || '').trim();
        photoUi.viewerImage.alt = comment || photoCategoryLabel(photo);
      }
      if (photoUi.viewerType) photoUi.viewerType.textContent = photoCategoryLabel(photo);
      if (photoUi.viewerComment) photoUi.viewerComment.textContent = comment || 'Описание не указано';
      if (photoUi.viewerCounter) photoUi.viewerCounter.textContent = `${state.photoViewerIndex + 1} / ${photos.length}`;
      [photoUi.viewerPrev, photoUi.viewerNext].forEach((button) => {
        if (button) button.disabled = photos.length < 2;
      });
    }

    function openPhotoViewer(photoId) {
      const photos = equipmentPhotos();
      const index = photos.findIndex((photo) => String(photo && photo.id || '').trim() === String(photoId || '').trim());
      if (index < 0 || !photoUi.viewerModalEl || !window.bootstrap || !window.bootstrap.Modal) return;
      state.photoViewerIndex = index;
      renderPhotoViewer();
      window.bootstrap.Modal.getOrCreateInstance(photoUi.viewerModalEl).show();
    }

    function movePhotoViewer(delta) {
      const photos = equipmentPhotos();
      if (photos.length < 2) return;
      state.photoViewerIndex = (state.photoViewerIndex + delta + photos.length) % photos.length;
      renderPhotoViewer();
    }

    function openPhotoEditor(photoId) {
      const photo = photoById(photoId);
      if (!photo || !photoUi.editModalEl || !window.bootstrap || !window.bootstrap.Modal) return;
      state.photoEditId = String(photoId || '').trim();
      if (photoUi.editPreview) {
        photoUi.editPreview.src = String(photo.url || '').trim();
        photoUi.editPreview.alt = String((photo.comment || photo.caption) || '').trim() || photoCategoryLabel(photo);
      }
      if (photoUi.editFile) photoUi.editFile.value = '';
      if (photoUi.editCategory) photoUi.editCategory.value = normalize(photo.category) === 'title' ? 'title' : 'general';
      if (photoUi.editComment) photoUi.editComment.value = String((photo.comment || photo.caption) || '').trim();
      window.bootstrap.Modal.getOrCreateInstance(photoUi.editModalEl).show();
    }

    async function sendPhotoUpdate(id, photoId, payload) {
      const response = await fetch(`/api/settings/it-equipment/${id}/photos/${encodeURIComponent(photoId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      return parsePhotoResponse(response, 'Ошибка сохранения фото');
    }

    async function replaceEquipmentPhoto(id, photoId, file, category, comment, replaceTitle) {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('category', category);
      formData.append('comment', comment);
      formData.append('replace_title', replaceTitle ? 'true' : 'false');
      const response = await fetch(`/api/settings/it-equipment/${id}/photos/${encodeURIComponent(photoId)}/replace`, {
        method: 'POST',
        body: formData,
      });
      return parsePhotoResponse(response, 'Ошибка замены фото');
    }

    async function mutateEquipmentPhotoEdit(id, photoId, file, category, comment, replaceTitle) {
      if (file) {
        return replaceEquipmentPhoto(id, photoId, file, category, comment, replaceTitle);
      }
      return sendPhotoUpdate(id, photoId, { category, comment, replace_title: replaceTitle });
    }

    async function saveEquipmentPhotoEdit() {
      const id = Number.parseInt(state.editingId, 10);
      const photoId = String(state.photoEditId || '').trim();
      if (!Number.isFinite(id) || !photoId) return;
      const file = photoUi.editFile && photoUi.editFile.files ? photoUi.editFile.files[0] : null;
      const category = photoUi.editCategory ? photoUi.editCategory.value.trim() : '';
      const comment = photoUi.editComment ? photoUi.editComment.value.trim() : '';
      if (!category) { popup('Укажите тип фото'); return; }
      if (!comment) { popup('Описание фото обязательно'); return; }

      let replaceTitle = false;
      const localTitle = normalize(category) === 'title' ? existingTitlePhoto(photoId) : null;
      if (localTitle) {
        const confirmed = await confirmTitleReplacement(localTitle);
        if (!confirmed) return;
        replaceTitle = true;
      }

      if (photoUi.editSave) photoUi.editSave.disabled = true;
      try {
        let data = await mutateEquipmentPhotoEdit(id, photoId, file, category, comment, replaceTitle);
        if (data.success === false && data.requires_confirmation === true && !replaceTitle) {
          const confirmed = await confirmTitleReplacement({ comment: data.existing_title_comment || '' });
          if (!confirmed) return;
          replaceTitle = true;
          data = await mutateEquipmentPhotoEdit(id, photoId, file, category, comment, true);
        }
        if (data.success === false) throw new Error(data.error || 'Ошибка сохранения фото');
        updateLocalEquipmentMedia(id, data.photo_url || '');
        state.photoEditId = null;
        if (photoUi.editFile) photoUi.editFile.value = '';
        window.bootstrap.Modal.getOrCreateInstance(photoUi.editModalEl).hide();
      } catch (error) {
        popup('❌ ' + (error && error.message ? error.message : error));
      } finally {
        if (photoUi.editSave) photoUi.editSave.disabled = false;
      }
    }

    async function deleteEquipmentPhoto(photoId) {
      const id = Number.parseInt(state.editingId, 10);
      if (!Number.isFinite(id) || !photoId) return;
      const photo = photoById(photoId);
      const comment = String((photo && (photo.comment || photo.caption)) || '').trim();
      const confirmed = await showPhotoConfirm({
        title: 'Удалить фото?',
        message: comment
          ? `Фото «${comment}» будет удалено без возможности восстановления.`
          : 'Фото будет удалено без возможности восстановления.',
        confirmLabel: 'Удалить',
        danger: true,
      });
      if (!confirmed) return;
      try {
        const response = await fetch(`/api/settings/it-equipment/${id}/photos/${encodeURIComponent(photoId)}`, { method: 'DELETE' });
        const data = await parsePhotoResponse(response, 'Ошибка удаления фото');
        if (data.success === false) throw new Error(data.error || 'Ошибка удаления фото');
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
      resetEquipmentModalTab();
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
      const photoAdd = event.target.closest('[data-it-equipment-photo-add-open]');
      if (photoAdd) {
        openPhotoAddModal();
        return;
      }
      const photoPreview = event.target.closest('[data-it-equipment-photo-preview]');
      if (photoPreview) {
        openPhotoViewer(photoPreview.dataset.itEquipmentPhotoPreview);
        return;
      }
      const photoEdit = event.target.closest('[data-it-equipment-photo-edit]');
      if (photoEdit) {
        openPhotoEditor(photoEdit.dataset.itEquipmentPhotoEdit);
        return;
      }
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
      if (photoUi.addSave) photoUi.addSave.addEventListener('click', () => uploadEquipmentPhoto().catch((error) => popup('❌ ' + error)));
      if (photoUi.addModalEl) photoUi.addModalEl.addEventListener('hidden.bs.modal', resetPhotoAddModal);
      if (photoUi.viewerPrev) photoUi.viewerPrev.addEventListener('click', () => movePhotoViewer(-1));
      if (photoUi.viewerNext) photoUi.viewerNext.addEventListener('click', () => movePhotoViewer(1));
      if (photoUi.viewerModalEl) {
        photoUi.viewerModalEl.addEventListener('keydown', (event) => {
          if (event.key === 'ArrowLeft') movePhotoViewer(-1);
          if (event.key === 'ArrowRight') movePhotoViewer(1);
        });
      }
      if (photoUi.editSave) photoUi.editSave.addEventListener('click', () => saveEquipmentPhotoEdit().catch((error) => popup('❌ ' + error)));
      if (photoUi.editModalEl) {
        photoUi.editModalEl.addEventListener('hidden.bs.modal', () => {
          state.photoEditId = null;
          if (photoUi.editFile) photoUi.editFile.value = '';
        });
      }
      if (photoUi.confirmButton) {
        photoUi.confirmButton.addEventListener('click', () => {
          resolvePhotoConfirm(true);
          window.bootstrap?.Modal.getOrCreateInstance(photoUi.confirmModalEl)?.hide();
        });
      }
      if (photoUi.confirmModalEl) photoUi.confirmModalEl.addEventListener('hidden.bs.modal', () => resolvePhotoConfirm(false));
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

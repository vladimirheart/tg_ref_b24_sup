(function () {
  if (window.SettingsItConnectionsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      drafts: [],
    };

    const elements = {
      itConnectionsContainer: document.querySelector('[data-it-connections-container]'),
      itConnectionAddModalEl: document.getElementById('itConnectionAddModal'),
    };

    const addModal = {
      form: elements.itConnectionAddModalEl
        ? elements.itConnectionAddModalEl.querySelector('[data-it-connection-add-form]')
        : null,
      categorySelect: elements.itConnectionAddModalEl
        ? elements.itConnectionAddModalEl.querySelector('[data-it-connection-category-select]')
        : null,
      categoryCreateButton: elements.itConnectionAddModalEl
        ? elements.itConnectionAddModalEl.querySelector('[data-it-connection-category-create]')
        : null,
      valueInput: elements.itConnectionAddModalEl
        ? elements.itConnectionAddModalEl.querySelector('[data-it-connection-value-input]')
        : null,
    };

    function getParameterData() {
      const data = typeof options.getParameterData === 'function' ? options.getParameterData() : null;
      return data && typeof data === 'object' ? data : {};
    }

    function normalizeCategory(value) {
      if (typeof options.normalizeItConnectionCategory === 'function') {
        return options.normalizeItConnectionCategory(value);
      }
      return typeof value === 'string' ? value.trim() : '';
    }

    function getCategoryLabel(category) {
      if (typeof options.getItConnectionCategoryLabel === 'function') {
        return options.getItConnectionCategoryLabel(category);
      }
      return normalizeCategory(category) || '—';
    }

    function getDefaultCategory() {
      return typeof options.getDefaultCategory === 'function'
        ? options.getDefaultCategory()
        : 'equipment_type';
    }

    function getCategoryFields() {
      const value = typeof options.getCategoryFieldMap === 'function' ? options.getCategoryFieldMap() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getUsageFilterParams() {
      const value = typeof options.getUsageFilterParams === 'function' ? options.getUsageFilterParams() : null;
      return value && typeof value === 'object' ? value : {};
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

    function syncParameterData(data) {
      if (typeof options.syncParameterData === 'function') {
        options.syncParameterData(data);
        return;
      }
      window.SettingsParametersShellRuntime?.syncParameterData?.(data);
    }

    function rerenderParameters() {
      if (typeof options.renderParameters === 'function') {
        options.renderParameters({ forceBodies: true });
        return;
      }
      window.SettingsParametersShellRuntime?.renderParameters?.({ forceBodies: true });
    }

    function rerenderCurrentParameterModal() {
      if (typeof options.renderCurrentParameterModal === 'function') {
        options.renderCurrentParameterModal();
        return;
      }
      window.SettingsParametersShellRuntime?.renderCurrentParameterModal?.();
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

    function rebuildCategorySelect(select, selectedKey) {
      if (typeof options.rebuildItConnectionCategorySelect === 'function') {
        options.rebuildItConnectionCategorySelect(select, selectedKey);
      }
    }

    function setCategories(newLabels, config) {
      if (typeof options.setItConnectionCategories === 'function') {
        options.setItConnectionCategories(newLabels, config);
      }
    }

    function renderItEquipmentTable() {
      if (typeof options.renderItEquipmentTable === 'function') {
        options.renderItEquipmentTable();
        return;
      }
      window.SettingsItEquipmentRuntime?.renderItEquipmentTable?.();
    }

    function renderUsageLink(item) {
      if (!item) {
        return '<span class="text-muted">—</span>';
      }
      const category = normalizeCategory(item.category);
      const value = item && item.value ? String(item.value).trim() : '';
      const usage = Number.isFinite(Number(item && item.usage_count)) ? Number(item.usage_count) : 0;
      if (!value) {
        return '<span class="text-muted">—</span>';
      }
      const filterKey = getUsageFilterParams()[category];
      if (!filterKey) {
        const className = usage > 0 ? 'fw-semibold text-dark' : 'text-muted';
        return `<span class="${className}">${usage}</span>`;
      }
      const url = `/object_passports?${filterKey}=${encodeURIComponent(value)}`;
      const className = 'btn btn-link btn-sm p-0' + (usage > 0 ? '' : ' text-muted');
      return `<a href="${url}" class="${className}" title="Открыть паспорта с этим значением">${usage}</a>`;
    }

    function buildRows() {
      const existing = Array.isArray(getParameterData().it_connection) ? getParameterData().it_connection : [];
      const rows = existing.map((item) => ({
        item: Object.assign({}, item, {
          category: normalizeCategory(item && item.category),
          value: item && typeof item.value === 'string' ? item.value : '',
        }),
        isDraft: false,
      }));
      state.drafts.forEach((item, index) => {
        const draft = Object.assign(
          {
            category: getDefaultCategory(),
            value: '',
            is_deleted: false,
            usage_count: 0,
          },
          item || {}
        );
        draft.category = normalizeCategory(draft.category);
        draft.value = typeof draft.value === 'string' ? draft.value : '';
        rows.push({ item: draft, isDraft: true, draftIndex: index });
      });
      return rows;
    }

    function renderItConnectionsTable() {
      if (!elements.itConnectionsContainer) return;
      const rows = buildRows();
      elements.itConnectionsContainer.innerHTML = '';
      if (!rows.length) {
        const emptyState = document.createElement('div');
        emptyState.className = 'text-muted text-center py-3';
        emptyState.textContent = 'Пока нет подключений';
        elements.itConnectionsContainer.appendChild(emptyState);
        renderItEquipmentTable();
        return;
      }

      const grouped = rows.reduce((acc, entry) => {
        const category = normalizeCategory(entry && entry.item && entry.item.category);
        if (!acc.has(category)) {
          acc.set(category, []);
        }
        acc.get(category).push(entry);
        return acc;
      }, new Map());

      const accordionId = 'itConnectionsCategoryAccordion';
      const categoryEntries = Array.from(grouped.entries()).sort(([a], [b]) => {
        const labelA = getCategoryLabel(a);
        const labelB = getCategoryLabel(b);
        return labelA.localeCompare(labelB, 'ru', { sensitivity: 'base' });
      });

      categoryEntries.forEach(([category, items], index) => {
        const collapseIdSlug = (category || 'uncategorized')
          .toString()
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, '-')
          .replace(/^-+|-+$/g, '') || 'all';
        const collapseId = `${accordionId}-panel-${index}-${collapseIdSlug}`;
        const headerId = `${collapseId}-header`;
        const accordionItem = document.createElement('div');
        accordionItem.className = 'accordion-item';
        accordionItem.innerHTML = `
          <h2 class="accordion-header" id="${headerId}">
            <button class="accordion-button${index === 0 ? '' : ' collapsed'}" type="button" data-bs-toggle="collapse" data-bs-target="#${collapseId}" aria-expanded="${index === 0}" aria-controls="${collapseId}">
              ${escapeHtml(getCategoryLabel(category))}
            </button>
          </h2>
          <div id="${collapseId}" class="accordion-collapse collapse${index === 0 ? ' show' : ''}" aria-labelledby="${headerId}" data-bs-parent="#${accordionId}">
            <div class="accordion-body p-0">
              <div class="table-responsive">
                <table class="table table-sm align-middle mb-0">
                  <thead>
                    <tr>
                      <th style="width: 55%">Значение</th>
                      <th class="text-center" style="width: 10%">Использование</th>
                      <th style="width: 35%"></th>
                    </tr>
                  </thead>
                  <tbody></tbody>
                </table>
              </div>
            </div>
          </div>
        `;
        const tbody = accordionItem.querySelector('tbody');
        items.forEach(({ item, isDraft, draftIndex }) => {
          const row = document.createElement('tr');
          const disabled = !isDraft && Boolean(item && item.is_deleted);
          const value = item && typeof item.value === 'string' ? item.value : '';
          if (isDraft) {
            row.dataset.rowKind = 'draft';
            row.dataset.draftIndex = String(draftIndex);
          } else {
            row.dataset.rowKind = 'existing';
            const rawId = item && Object.prototype.hasOwnProperty.call(item, 'id') ? item.id : null;
            const idNumber = Number.parseInt(rawId, 10);
            if (Number.isFinite(idNumber)) {
              row.dataset.id = String(idNumber);
            }
            if (disabled) {
              row.classList.add('table-light', 'text-muted');
            }
          }
          row.dataset.category = category;
          const usageItem = Object.assign({}, item || {}, { category, value });
          const deletedHint = !isDraft && disabled
            ? '<div class="form-text text-danger small">Запись помечена как удалённая</div>'
            : '';
          const actions = isDraft
            ? `
                <button class="btn btn-sm btn-outline-success" type="button" data-it-connection-action="save">Сохранить</button>
                <button class="btn btn-sm btn-outline-secondary" type="button" data-it-connection-action="discard">Отменить</button>
              `
            : disabled
              ? '<button class="btn btn-sm btn-outline-secondary" type="button" data-it-connection-action="restore">Восстановить</button>'
              : `
                <button class="btn btn-sm btn-outline-success" type="button" data-it-connection-action="save">Сохранить</button>
                <button class="btn btn-sm btn-outline-danger" type="button" data-it-connection-action="delete">Удалить</button>
              `;
          row.innerHTML = `
            <td>
              <input type="text" class="form-control form-control-sm" data-field="value" value="${escapeHtml(value)}"${disabled ? ' disabled' : ''}>
              ${deletedHint}
            </td>
            <td class="text-center">${renderUsageLink(usageItem)}</td>
            <td class="text-nowrap">${actions}</td>
          `;
          tbody.appendChild(row);
        });
        elements.itConnectionsContainer.appendChild(accordionItem);
      });

      renderItEquipmentTable();
    }

    function addItConnectionRow(initial) {
      const defaults = {
        category: getDefaultCategory(),
        value: '',
        usage_count: 0,
        is_deleted: false,
      };
      const data = Object.assign({}, defaults, initial || {});
      data.category = normalizeCategory(data.category);
      data.value = typeof data.value === 'string' ? data.value : '';
      state.drafts.push(data);
      renderItConnectionsTable();
    }

    function buildItConnectionPayload(category, value) {
      const normalizedCategory = normalizeCategory(category);
      const trimmedValue = typeof value === 'string' ? value.trim() : '';
      const payload = {
        value: trimmedValue,
        category: normalizedCategory,
        equipment_type: '',
        equipment_vendor: '',
        equipment_model: '',
        equipment_status: '',
      };
      const mappedField = getCategoryFields()[normalizedCategory];
      if (mappedField === 'equipment_type') {
        payload.equipment_type = trimmedValue;
      } else if (mappedField === 'equipment_vendor') {
        payload.equipment_vendor = trimmedValue;
      } else if (mappedField === 'equipment_model') {
        payload.equipment_model = trimmedValue;
      } else if (mappedField === 'equipment_status') {
        payload.equipment_status = trimmedValue;
      }
      return payload;
    }

    function collectItConnectionPayload(row) {
      const category = normalizeCategory(row && row.dataset ? row.dataset.category : undefined);
      const input = row ? row.querySelector('[data-field="value"]') : null;
      const value = input ? input.value : '';
      return buildItConnectionPayload(category, value);
    }

    async function saveItConnection(row, context) {
      const { isDraft, draftIndex, id } = context;
      const payload = collectItConnectionPayload(row);
      if (!payload.value) {
        popup('Необходимо указать значение подключения');
        const input = row.querySelector('[data-field="value"]');
        if (input) input.focus();
        return;
      }
      const button = row.querySelector('[data-it-connection-action="save"]');
      if (button) button.disabled = true;
      try {
        let response;
        if (isDraft || !Number.isFinite(id)) {
          response = await fetch('/api/settings/parameters', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(Object.assign({ param_type: 'it_connection' }, payload)),
          });
        } else {
          response = await fetch(`/api/settings/parameters/${id}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        }
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка сохранения');
        }
        if (isDraft && Number.isInteger(draftIndex) && draftIndex >= 0) {
          state.drafts.splice(draftIndex, 1);
        }
        syncParameterData((data && data.data) || getParameterData());
        rerenderParameters();
        renderItConnectionsTable();
        rerenderCurrentParameterModal();
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        if (button) button.disabled = false;
      }
    }

    async function deleteItConnection(row, context) {
      const { isDraft, draftIndex, id } = context;
      if (isDraft) {
        if (Number.isInteger(draftIndex) && draftIndex >= 0) {
          state.drafts.splice(draftIndex, 1);
          renderItConnectionsTable();
        }
        return;
      }
      if (!Number.isFinite(id)) {
        return;
      }
      if (!confirmAction('Удалить подключение?')) {
        return;
      }
      const button = row.querySelector('[data-it-connection-action="delete"]');
      if (button) button.disabled = true;
      try {
        const response = await fetch(`/api/settings/parameters/${id}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка удаления');
        }
        syncParameterData((data && data.data) || getParameterData());
        rerenderParameters();
        renderItConnectionsTable();
        rerenderCurrentParameterModal();
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        if (button) button.disabled = false;
      }
    }

    async function restoreItConnection(row, id) {
      if (!Number.isFinite(id)) {
        return;
      }
      const button = row.querySelector('[data-it-connection-action="restore"]');
      if (button) button.disabled = true;
      try {
        const response = await fetch(`/api/settings/parameters/${id}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ is_deleted: false }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка восстановления');
        }
        syncParameterData((data && data.data) || getParameterData());
        rerenderParameters();
        renderItConnectionsTable();
        rerenderCurrentParameterModal();
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        if (button) button.disabled = false;
      }
    }

    function handleContainerClick(event) {
      const button = event.target.closest('[data-it-connection-action]');
      if (!button) return;
      const action = button.dataset.itConnectionAction;
      const row = button.closest('tr');
      if (!row) return;
      const isDraft = row.dataset.rowKind === 'draft';
      const draftIndex = isDraft ? Number.parseInt(row.dataset.draftIndex, 10) : null;
      const id = !isDraft && row.dataset.id ? Number.parseInt(row.dataset.id, 10) : null;
      if (action === 'save') {
        saveItConnection(row, {
          isDraft,
          draftIndex: Number.isFinite(draftIndex) ? draftIndex : null,
          id: Number.isFinite(id) ? id : null,
        });
      } else if (action === 'delete') {
        deleteItConnection(row, {
          isDraft,
          draftIndex: Number.isFinite(draftIndex) ? draftIndex : null,
          id: Number.isFinite(id) ? id : null,
        });
      } else if (action === 'restore') {
        restoreItConnection(row, Number.isFinite(id) ? id : null);
      } else if (action === 'discard') {
        if (isDraft && Number.isFinite(draftIndex) && draftIndex >= 0) {
          state.drafts.splice(draftIndex, 1);
          renderItConnectionsTable();
        }
      }
    }

    function prepareItConnectionAddSettingsModal() {
      rebuildCategorySelect(addModal.categorySelect, getDefaultCategory());
      if (addModal.categorySelect) {
        addModal.categorySelect.value = getDefaultCategory();
      }
      if (addModal.valueInput) {
        addModal.valueInput.value = '';
        addModal.valueInput.classList.remove('is-invalid');
      }
    }

    async function handleCategoryCreate() {
      const promptMessage = 'Введите название новой категории подключения';
      let label = window.prompt(promptMessage);
      if (label === null) {
        return;
      }
      label = String(label).trim();
      if (!label) {
        popup('Название категории не может быть пустым');
        return;
      }
      if (addModal.categoryCreateButton) {
        addModal.categoryCreateButton.disabled = true;
      }
      try {
        const response = await fetch('/api/settings/it-connection-categories', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ label }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Ошибка создания категории');
        }
        const payload = (data && data.data) || {};
        const categories = payload.categories || {};
        const createdKey = payload.key || null;
        setCategories(categories, { selectedKey: createdKey });
        renderItConnectionsTable();
        if (createdKey && addModal.categorySelect) {
          addModal.categorySelect.value = createdKey;
        }
        if (addModal.valueInput) {
          addModal.valueInput.focus();
        }
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        if (addModal.categoryCreateButton) {
          addModal.categoryCreateButton.disabled = false;
        }
      }
    }

    async function handleAddFormSubmit(event) {
      event.preventDefault();
      const selected =
        addModal.categorySelect && addModal.categorySelect.value
          ? addModal.categorySelect.value
          : getDefaultCategory();
      const payload = buildItConnectionPayload(selected, addModal.valueInput ? addModal.valueInput.value : '');
      if (!payload.value) {
        if (addModal.valueInput) {
          addModal.valueInput.classList.add('is-invalid');
          addModal.valueInput.focus();
        }
        return;
      }

      const submitButton = addModal.form ? addModal.form.querySelector('button[type="submit"]') : null;
      const originalButtonText = submitButton ? submitButton.textContent : '';
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.textContent = '...';
      }

      try {
        const response = await fetch('/api/settings/parameters', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(Object.assign({ param_type: 'it_connection' }, payload)),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка создания подключения');
        }
        if (addModal.valueInput) {
          addModal.valueInput.value = '';
          addModal.valueInput.classList.remove('is-invalid');
        }
        syncParameterData((data && data.data) || getParameterData());
        rerenderParameters();
        renderItConnectionsTable();
        rerenderCurrentParameterModal();
        if (elements.itConnectionAddModalEl) {
          requestClose(addModal.form);
        }
        setTimeout(() => {
          if (!elements.itConnectionsContainer) return;
          const rows = Array.from(elements.itConnectionsContainer.querySelectorAll('tr'));
          const createdRow = rows.find((row) => {
            const input = row.querySelector('[data-field="value"]');
            return input && input.value.trim() === payload.value;
          });
          if (createdRow) {
            const input = createdRow.querySelector('[data-field="value"]');
            if (input) input.focus();
          }
        }, 0);
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        if (submitButton) {
          submitButton.disabled = false;
          submitButton.textContent = originalButtonText;
        }
      }
    }

    function bindEvents() {
      if (elements.itConnectionsContainer) {
        elements.itConnectionsContainer.addEventListener('click', handleContainerClick);
      }
      if (addModal.categoryCreateButton) {
        addModal.categoryCreateButton.addEventListener('click', handleCategoryCreate);
      }
      if (addModal.valueInput) {
        addModal.valueInput.addEventListener('input', () => {
          addModal.valueInput.classList.remove('is-invalid');
        });
      }
      if (addModal.form) {
        addModal.form.addEventListener('submit', handleAddFormSubmit);
      }
      if (addModal.categorySelect) {
        rebuildCategorySelect(addModal.categorySelect);
      }
    }

    bindEvents();

    return {
      renderItConnectionsTable,
      addItConnectionRow,
      prepareItConnectionAddSettingsModal,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsItConnectionsRuntime) {
        return window.__settingsItConnectionsRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        prepareItConnectionAddSettingsModal: runtime.prepareItConnectionAddSettingsModal,
        addItConnectionRow: runtime.addItConnectionRow,
      });
      window.__settingsItConnectionsRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsItConnectionsRuntime = Object.freeze({
    ...api,
    renderItConnectionsTable(...args) {
      return window.__settingsItConnectionsRuntime?.renderItConnectionsTable?.(...args);
    },
    addItConnectionRow(...args) {
      return window.__settingsItConnectionsRuntime?.addItConnectionRow?.(...args);
    },
    prepareItConnectionAddSettingsModal(...args) {
      return window.__settingsItConnectionsRuntime?.prepareItConnectionAddSettingsModal?.(...args);
    },
  });
}());

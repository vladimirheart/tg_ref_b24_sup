(function () {
  if (window.SettingsParametersRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      parameterModalType: null,
    };

    const elements = {
      parameterModalEl: document.getElementById('parameterItemsModal'),
      cityCardEl: document.querySelector('[data-city-card]'),
    };

    function getParameterData() {
      const data = typeof options.getParameterData === 'function' ? options.getParameterData() : null;
      return data && typeof data === 'object' ? data : {};
    }

    function getParameterTitles() {
      const value = typeof options.getParameterTitles === 'function' ? options.getParameterTitles() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterDependencies() {
      const value = typeof options.getParameterDependencies === 'function' ? options.getParameterDependencies() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterStateTypes() {
      const value = typeof options.getParameterStateTypes === 'function' ? options.getParameterStateTypes() : null;
      return value instanceof Set ? value : new Set();
    }

    function getParameterStates() {
      const value = typeof options.getParameterStates === 'function' ? options.getParameterStates() : null;
      return Array.isArray(value) && value.length ? value : ['Активен'];
    }

    function getParameterFilterKeys() {
      const value = typeof options.getParameterFilterKeys === 'function' ? options.getParameterFilterKeys() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getCityParameterType() {
      return typeof options.getCityParameterType === 'function' ? options.getCityParameterType() : 'city';
    }

    function getCityParameterLabel() {
      return typeof options.getCityParameterLabel === 'function'
        ? options.getCityParameterLabel()
        : 'Город';
    }

    function getCityOptions() {
      const value = typeof options.getCityOptions === 'function' ? options.getCityOptions() : null;
      return Array.isArray(value) ? value : [];
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

    function renderPartnerContacts() {
      if (typeof options.renderPartnerContacts === 'function') {
        options.renderPartnerContacts();
        return;
      }
      window.SettingsPartnerContactsRuntime?.renderPartnerContacts?.();
    }

    function renderLegalEntities() {
      if (typeof options.renderLegalEntities === 'function') {
        options.renderLegalEntities();
        return;
      }
      window.SettingsLegalEntitiesRuntime?.renderLegalEntities?.();
    }

    function renderItConnectionsTable() {
      if (typeof options.renderItConnectionsTable === 'function') {
        options.renderItConnectionsTable();
        return;
      }
      window.SettingsItConnectionsRuntime?.renderItConnectionsTable?.();
    }

    function confirmAction(message) {
      if (typeof options.confirmDialog === 'function') {
        return Boolean(options.confirmDialog(message));
      }
      return false;
    }

    function getDependencyChain(type) {
      if (typeof options.getDependencyChain === 'function') {
        const chain = options.getDependencyChain(type);
        return Array.isArray(chain) ? chain : [];
      }
      const chain = getParameterDependencies()[type];
      return Array.isArray(chain) ? chain : [];
    }

    function normalizeDependencyValue(value) {
      if (typeof options.normalizeDependencyValue === 'function') {
        return options.normalizeDependencyValue(value);
      }
      return typeof value === 'string' ? value.trim() : '';
    }

    function getDependencyLabel(key) {
      if (typeof options.getDependencyLabel === 'function') {
        return options.getDependencyLabel(key);
      }
      return getParameterTitles()[key] || key;
    }

    function getParameterItemsSafe(type) {
      const items = getParameterData()[type];
      return Array.isArray(items) ? items : [];
    }

    function buildDependencySelect(type, dependencyKey, selectedValue, disabled, context) {
      if (typeof options.buildDependencySelect === 'function') {
        return options.buildDependencySelect(type, dependencyKey, selectedValue, disabled, context);
      }
      const normalized = normalizeDependencyValue(selectedValue);
      const disabledAttr = disabled ? ' disabled' : '';
      const valueAttr = escapeHtml(normalized);
      return `
        <select
          class="form-select form-select-sm"
          data-param-dependency-field="${dependencyKey}"
          data-param-type="${type}"
          data-param-dependency-context="${context}"
          data-param-dependency-value="${valueAttr}"${disabledAttr}>
        </select>
      `;
    }

    function buildDependencyOptions(type, selections) {
      const baseSelections = selections && typeof selections === 'object' ? selections : {};
      const items = getParameterItemsSafe(type);
      const unique = new Map();
      items.forEach((item) => {
        if (!item || item.is_deleted) return;
        const value = normalizeDependencyValue(item.value);
        if (!value) return;
        const deps = item && typeof item.dependencies === 'object' ? item.dependencies : {};
        const matches = Object.entries(baseSelections).every(([key, selectionValue]) => {
          const normalizedSelection = normalizeDependencyValue(selectionValue);
          if (!normalizedSelection) return true;
          if (key === type) return true;
          const candidate = normalizeDependencyValue(deps && deps[key]);
          if (!candidate) return false;
          return candidate === normalizedSelection;
        });
        if (!matches) return;
        if (!unique.has(value)) {
          const label = normalizeDependencyValue(item.label || item.value);
          unique.set(value, label || value);
        }
      });
      return Array.from(unique.entries()).map(([value, label]) => ({ value, label }));
    }

    function fillDependencySelectOptions(select, optionsList, { placeholder, selected } = {}) {
      if (!(select instanceof HTMLSelectElement)) return;
      const doc = select.ownerDocument || document;
      const desired = normalizeDependencyValue(selected);
      const wasDisabled = select.disabled;
      select.innerHTML = '';
      const placeholderOption = doc.createElement('option');
      placeholderOption.value = '';
      placeholderOption.textContent = placeholder || '—';
      select.appendChild(placeholderOption);
      let hasSelected = false;
      optionsList.forEach((option) => {
        const optionEl = doc.createElement('option');
        optionEl.value = option.value;
        optionEl.textContent = option.label || option.value;
        if (desired && option.value === desired) {
          optionEl.selected = true;
          hasSelected = true;
        }
        select.appendChild(optionEl);
      });
      if (hasSelected) {
        select.value = desired;
      } else {
        select.value = '';
      }
      if (wasDisabled) {
        select.disabled = true;
      }
    }

    function refreshDependencySelectGroup(group, type) {
      if (!(group instanceof HTMLElement)) return;
      const chain = getDependencyChain(type);
      if (!chain.length) return;
      if (!group.dataset.paramType) {
        group.dataset.paramType = type;
      }
      const selections = {};
      chain.forEach((dep) => {
        const select = group.querySelector(`[data-param-dependency-field="${dep}"]`);
        if (!(select instanceof HTMLSelectElement)) {
          selections[dep] = '';
          return;
        }
        const desired = normalizeDependencyValue(select.dataset.paramDependencyValue || select.value);
        const placeholder = `— ${getDependencyLabel(dep)} —`;
        const optionList = buildDependencyOptions(dep, selections);
        fillDependencySelectOptions(select, optionList, { placeholder, selected: desired });
        const finalValue = normalizeDependencyValue(select.value);
        select.dataset.paramDependencyValue = finalValue;
        selections[dep] = finalValue;
      });
    }

    function refreshParameterDependencyControls(root, fallbackType = null) {
      if (typeof options.refreshParameterDependencyControls === 'function') {
        options.refreshParameterDependencyControls(root, fallbackType);
        return;
      }
      const scope = root || document;
      scope.querySelectorAll('[data-param-dependency-group]').forEach((group) => {
        const groupType = group.dataset.paramType || fallbackType;
        if (!groupType) return;
        if (!getDependencyChain(groupType).length) return;
        refreshDependencySelectGroup(group, groupType);
      });
    }

    function collectDependencyValues(container, type) {
      if (typeof options.collectDependencyValues === 'function') {
        return options.collectDependencyValues(container, type);
      }
      const chain = getDependencyChain(type);
      const result = { values: {} };
      if (!chain.length) {
        return result;
      }
      if (!(container instanceof HTMLElement)) {
        return result;
      }
      for (const dep of chain) {
        const select = container.querySelector(`[data-param-dependency-field="${dep}"]`);
        if (!(select instanceof HTMLSelectElement)) {
          continue;
        }
        const value = normalizeDependencyValue(select.value);
        if (!value) {
          const label = getDependencyLabel(dep);
          return { error: `Поле «${label}» обязательно`, field: select };
        }
        result.values[dep] = value;
      }
      return result;
    }

    function buildStateSelect(type, selected, disabled, { forInput = false } = {}) {
      if (!getParameterStateTypes().has(type)) {
        return '';
      }
      const states = getParameterStates();
      const effective = states.includes(selected) ? selected : states[0];
      const optionsHtml = states
        .map((stateValue) => `<option value="${stateValue}"${stateValue === effective ? ' selected' : ''}>${stateValue}</option>`)
        .join('');
      const roleAttr = forInput ? 'data-param-state-input' : 'data-param-field="state"';
      const disabledAttr = disabled ? ' disabled' : '';
      return `<select class="form-select form-select-sm" ${roleAttr} data-param-type="${type}" data-param-current-state="${effective}"${disabledAttr}>${optionsHtml}</select>`;
    }

    function buildUsageControl(type, item) {
      if (type === 'department') {
        return '<span class="input-group-text text-muted">—</span>';
      }
      const filterKey = getParameterFilterKeys()[type];
      const value = item && item.value ? String(item.value) : '';
      const trimmedValue = value.trim();
      if (!filterKey || !trimmedValue) {
        return '<span class="input-group-text text-muted">—</span>';
      }
      const usage = Number.isFinite(Number(item && item.usage_count)) ? Number(item.usage_count) : 0;
      const url = `/object_passports?${filterKey}=${encodeURIComponent(trimmedValue)}`;
      const btnClass = usage > 0 ? 'btn btn-outline-primary' : 'btn btn-outline-secondary';
      return `<a class="${btnClass}" data-param-role="usage" href="${url}" title="Открыть паспорта с этим значением">${usage}</a>`;
    }

    function buildParameterItem(type, item) {
      const rawId = item && item.id;
      const idNumber = Number(rawId);
      const id = Number.isFinite(idNumber) ? idNumber : Number.parseInt(rawId, 10);
      if (!Number.isFinite(id)) {
        return '';
      }
      const value = item && item.value ? String(item.value) : '';
      const disabled = Boolean(item && item.is_deleted);
      const dependencyChain = getDependencyChain(type);
      const dependencyValues = item && typeof item.dependencies === 'object' ? item.dependencies : {};
      const dependencySelects = dependencyChain
        .map((dep) => {
          const selected = dependencyValues && typeof dependencyValues[dep] === 'string' ? dependencyValues[dep] : '';
          return buildDependencySelect(type, dep, selected, disabled, 'entry');
        })
        .join('');
      let serverNameInput = '';
      if (type === 'iiko_server') {
        let serverNameSource = '';
        if (item && typeof item.server_name === 'string') {
          serverNameSource = item.server_name;
        } else if (item && item.extra && typeof item.extra.server_name === 'string') {
          serverNameSource = item.extra.server_name;
        }
        const serverName = typeof serverNameSource === 'string' ? serverNameSource.trim() : '';
        serverNameInput = `<input type="text" class="form-control" data-param-field="server_name" placeholder="Имя сервера" value="${escapeHtml(serverName)}"${disabled ? ' disabled' : ''}>`;
      }
      const valuePlaceholder = type === 'iiko_server' ? 'Адрес сервера' : '';
      const valueInput = `<input type="text" class="form-control" data-param-field="value"${valuePlaceholder ? ` placeholder="${valuePlaceholder}"` : ''} value="${escapeHtml(value)}"${disabled ? ' disabled' : ''}>`;
      const stateSelect = buildStateSelect(type, item ? item.state : undefined, disabled, { forInput: false });
      const usageControl = buildUsageControl(type, item);
      const saveButton = `<button class="btn btn-outline-success" type="button" data-param-action="save" data-param-id="${id}" data-param-type="${type}" title="Сохранить"${disabled ? ' disabled' : ''}>💾</button>`;
      const deleteButton = disabled
        ? `<button class="btn btn-outline-secondary" type="button" data-param-action="restore" data-param-id="${id}" data-param-type="${type}" title="Восстановить">↩️</button>`
        : `<button class="btn btn-outline-danger" type="button" data-param-action="delete" data-param-id="${id}" data-param-type="${type}" title="Удалить">🗑</button>`;
      const deletedHint = disabled ? '<div class="form-text text-danger small">Запись помечена как удалённая</div>' : '';
      return `
        <div class="param-entry${disabled ? ' param-entry--deleted' : ''}" data-param-id="${id}" data-param-type="${type}" data-param-dependency-group="entry">
          <div class="input-group input-group-sm mb-2">
            ${dependencySelects}
            ${serverNameInput}
            ${valueInput}
            ${stateSelect}
            ${usageControl}
            ${saveButton}
            ${deleteButton}
          </div>
          ${deletedHint}
        </div>
      `;
    }

    function buildParameterContent(type, items, context) {
      const listHtml = items.length
        ? items.map((item) => buildParameterItem(type, item)).join('')
        : '<div class="empty-placeholder">Пока нет значений</div>';
      const createPlaceholder = type === 'iiko_server' ? 'Адрес сервера' : 'Добавить значение';
      const extraControls = [];
      if (type === 'iiko_server') {
        extraControls.push(
          '<input type="text" class="form-control" placeholder="Имя сервера" data-param-create-field="server_name">'
        );
      }
      const dependencyChain = getDependencyChain(type);
      const dependencySelects = dependencyChain
        .map((dep) => buildDependencySelect(type, dep, '', false, 'create'))
        .join('');
      const controls = `
        <div class="input-group input-group-sm mt-3" data-param-dependency-group="create" data-param-type="${type}">
          ${dependencySelects}
          ${buildStateSelect(type, getParameterStates()[0], false, { forInput: true })}
          ${extraControls.join('')}
          <input type="text" class="form-control" placeholder="${createPlaceholder}" data-param-input>
          <button class="btn btn-success" type="button" data-param-action="create" data-param-type="${type}">Добавить</button>
        </div>
      `;
      return `
        <div data-param-container data-param-type="${type}" data-param-context="${context}">
          <div class="param-items">${listHtml}</div>
          ${controls}
        </div>
      `;
    }

    function renderRemoteAccessSection() {
      const remoteAccessContainer = document.querySelector('[data-remote-access-container]');
      if (!remoteAccessContainer) return;
      const items = Array.isArray(getParameterData().remote_access) ? getParameterData().remote_access : [];
      remoteAccessContainer.innerHTML = buildParameterContent('remote_access', items, 'it');
    }

    function renderParameterCard(type, { forceBody = false } = {}) {
      const card = document.querySelector(`.parameter-card[data-param-type="${type}"]`);
      if (!card) return;
      const items = Array.isArray(getParameterData()[type]) ? getParameterData()[type] : [];
      const counter = card.querySelector('[data-param-count]');
      if (counter) {
        counter.textContent = items.length;
      }
      const hasMany = items.length > 10;
      card.dataset.hasMany = hasMany ? 'true' : 'false';
      const hint = card.querySelector('[data-param-hint]');
      if (hint) {
        hint.classList.toggle('d-none', !hasMany);
      }
      const body = card.querySelector('[data-param-body]');
      if (!body) return;
      if (hasMany) {
        card.classList.remove('is-expanded');
        body.classList.add('d-none');
        body.innerHTML = '';
        return;
      }
      if (!card.classList.contains('is-expanded')) {
        body.classList.add('d-none');
        body.innerHTML = '';
        return;
      }
      body.classList.remove('d-none');
      if (forceBody || !body.innerHTML.trim()) {
        body.innerHTML = buildParameterContent(type, items, 'card');
      }
      refreshParameterDependencyControls(body, type);
    }

    function renderParameters({ forceBodies = false } = {}) {
      Object.keys(getParameterTitles()).forEach((type) => {
        renderParameterCard(type, { forceBody: forceBodies });
      });
      renderRemoteAccessSection();
      renderPartnerContacts();
      renderLegalEntities();
    }

    function buildCityModalContent() {
      const cityParameterType = getCityParameterType();
      const items = Array.isArray(getParameterData()[cityParameterType]) ? getParameterData()[cityParameterType] : [];
      if (items.length) {
        return buildParameterContent(cityParameterType, items, 'modal');
      }
      const cityOptions = getCityOptions();
      if (!cityOptions.length) {
        return '<div class="empty-placeholder">Пока нет значений</div>';
      }
      const listItems = cityOptions.map((city) => `<li class="list-group-item py-1 px-2">${escapeHtml(city)}</li>`).join('');
      return `
        <div data-param-container data-param-type="${cityParameterType}">
          <div class="param-items">
            <ul class="list-group list-group-flush">${listItems}</ul>
          </div>
        </div>
      `;
    }

    function renderParameterModal(type) {
      if (!elements.parameterModalEl) return;
      const body = elements.parameterModalEl.querySelector('[data-param-modal-body]');
      if (!body) return;
      if (type === getCityParameterType()) {
        body.innerHTML = buildCityModalContent();
        refreshParameterDependencyControls(body, type);
        return;
      }
      const items = Array.isArray(getParameterData()[type]) ? getParameterData()[type] : [];
      body.innerHTML = buildParameterContent(type, items, 'modal');
      refreshParameterDependencyControls(body, type);
    }

    function renderCurrentParameterModal() {
      if (!state.parameterModalType) return;
      renderParameterModal(state.parameterModalType);
    }

    function renderCityCard() {
      if (!elements.cityCardEl) return;
      const cityParameterType = getCityParameterType();
      const parameterCities = Array.isArray(getParameterData()[cityParameterType])
        ? getParameterData()[cityParameterType]
            .map((item) => (item && typeof item.value === 'string' ? item.value.trim() : ''))
            .filter((name) => name)
        : [];
      const sourceCities = parameterCities.length ? parameterCities : getCityOptions();
      const uniqueCities = Array.from(new Set(sourceCities.map((city) => city.trim()))).filter((city) => city);
      uniqueCities.sort((a, b) => a.localeCompare(b));
      const count = uniqueCities.length;
      elements.cityCardEl.dataset.cityCount = String(count);
      const body = elements.cityCardEl.querySelector('[data-city-body]');
      const hint = elements.cityCardEl.querySelector('[data-city-hint]');
      const counter = elements.cityCardEl.querySelector('.badge');
      if (counter) {
        counter.textContent = String(count);
      }
      const listContainer = elements.cityCardEl.querySelector('[data-city-list]');
      if (listContainer) {
        if (count) {
          const items = uniqueCities.map((city) => `<li class="list-group-item py-1 px-2">${escapeHtml(city)}</li>`).join('');
          listContainer.innerHTML = `<ul class="list-group list-group-flush">${items}</ul>`;
        } else {
          listContainer.innerHTML = '<div class="empty-placeholder">Пока нет значений</div>';
        }
      }
      if (body) {
        body.classList.toggle('d-none', count > 10);
      }
      if (hint) {
        hint.classList.toggle('d-none', !(count > 10));
      }
    }

    function prepareParameterModal(type) {
      if (!elements.parameterModalEl) return false;
      state.parameterModalType = type;
      const titleEl = elements.parameterModalEl.querySelector('[data-param-modal-title]');
      if (titleEl) {
        const label = getParameterTitles()[type] || (type === getCityParameterType() ? getCityParameterLabel() : 'Значения параметра');
        titleEl.textContent = `Параметры партнёров • ${label}`;
      }
      renderParameterModal(type);
      return true;
    }

    function resetParameterItemsSettingsModal() {
      state.parameterModalType = null;
    }

    function prepareCityParameterSettingsTrigger() {
      const cityCount = elements.cityCardEl ? Number.parseInt(elements.cityCardEl.dataset.cityCount || '0', 10) : 0;
      if (Number.isFinite(cityCount) && cityCount > 10) {
        return prepareParameterModal(getCityParameterType());
      }
      return false;
    }

    function prepareParameterSettingsTrigger(trigger) {
      const card = trigger instanceof HTMLElement ? trigger.closest('.parameter-card') : null;
      if (!card) return false;
      const type = card.dataset.paramType;
      if (!type) return false;
      if (card.dataset.hasMany === 'true') {
        return prepareParameterModal(type);
      }
      card.classList.toggle('is-expanded');
      renderParameterCard(type, { forceBody: true });
      return false;
    }

    function findParameterContainer(element) {
      return element ? element.closest('[data-param-container]') : null;
    }

    function afterMutation(data) {
      syncParameterData((data && data.data) || getParameterData());
      renderParameters({ forceBodies: true });
      renderCityCard();
      renderItConnectionsTable();
      renderCurrentParameterModal();
    }

    async function createParameter(type, trigger) {
      const container = findParameterContainer(trigger);
      if (!container) return;
      const input = container.querySelector('[data-param-input]');
      if (!input) return;
      const value = input.value.trim();
      if (!value) {
        input.focus();
        return;
      }
      const stateSelect = container.querySelector('[data-param-state-input]');
      let serverNameInput = null;
      let serverName = '';
      if (type === 'iiko_server') {
        serverNameInput = container.querySelector('[data-param-create-field="server_name"]');
        serverName = serverNameInput ? serverNameInput.value.trim() : '';
        if (!serverName) {
          if (serverNameInput) {
            serverNameInput.focus();
          }
          popup('Поле «Имя сервера» обязательно');
          return;
        }
      }
      const dependencyResult = collectDependencyValues(container, type);
      if (dependencyResult.error) {
        popup('❌ ' + dependencyResult.error);
        if (dependencyResult.field) {
          dependencyResult.field.focus();
        }
        return;
      }
      const payload = { param_type: type, value };
      if (stateSelect && getParameterStateTypes().has(type)) {
        payload.state = stateSelect.value;
      }
      if (type === 'iiko_server') {
        payload.server_name = serverName;
      }
      Object.assign(payload, dependencyResult.values);

      const originalText = trigger.textContent;
      trigger.disabled = true;
      trigger.textContent = '...';

      try {
        const response = await fetch('/api/settings/parameters', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка создания параметра');
        }
        afterMutation(data);
        input.value = '';
        if (serverNameInput) {
          serverNameInput.value = '';
        }
        if (stateSelect) {
          stateSelect.value = getParameterStates()[0];
        }
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
        trigger.textContent = originalText;
      }
    }

    async function updateParameterValue(id, type, trigger) {
      const entry = trigger.closest('[data-param-id]');
      if (!entry) return;
      const input = entry.querySelector('input[data-param-field="value"]');
      if (!input) return;
      const value = input.value.trim();
      if (!value) {
        popup('Значение не может быть пустым');
        input.focus();
        return;
      }
      let serverNameInput = null;
      let serverName = '';
      if (type === 'iiko_server') {
        serverNameInput = entry.querySelector('input[data-param-field="server_name"]');
        serverName = serverNameInput ? serverNameInput.value.trim() : '';
        if (!serverName) {
          popup('Имя сервера обязательно');
          if (serverNameInput) {
            serverNameInput.focus();
          }
          return;
        }
      }

      trigger.disabled = true;

      try {
        const payload = { value };
        if (type === 'iiko_server') {
          payload.server_name = serverName;
        }
        const dependencyResult = collectDependencyValues(entry, type);
        if (dependencyResult.error) {
          popup('❌ ' + dependencyResult.error);
          if (dependencyResult.field) {
            dependencyResult.field.focus();
          }
          trigger.disabled = false;
          return;
        }
        Object.assign(payload, dependencyResult.values);
        const response = await fetch(`/api/settings/parameters/${id}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка сохранения');
        }
        afterMutation(data);
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
      }
    }

    async function updateParameterState(id, type, select) {
      const previous = select.dataset.paramCurrentState || select.value;
      select.disabled = true;

      try {
        const response = await fetch(`/api/settings/parameters/${id}`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ state: select.value }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка сохранения состояния');
        }
        afterMutation(data);
        select.dataset.paramCurrentState = select.value;
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
        select.value = previous;
      } finally {
        select.disabled = false;
      }
    }

    async function deleteParameter(id, type, trigger) {
      const confirmations = [
        'Удалить значение?',
        'Подтвердите: запись будет помечена как удалённая.',
        'Последнее подтверждение перед удалением. Продолжить?',
      ];
      for (const message of confirmations) {
        if (!confirmAction(message)) {
          return;
        }
      }

      trigger.disabled = true;

      try {
        const response = await fetch(`/api/settings/parameters/${id}`, {
          method: 'DELETE',
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка удаления');
        }
        afterMutation(data);
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
      }
    }

    async function restoreParameter(id, type, trigger) {
      trigger.disabled = true;
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
        afterMutation(data);
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
      }
    }

    function handleDocumentClick(event) {
      const actionEl = event.target.closest('[data-param-action]');
      if (!actionEl) return;
      const action = actionEl.dataset.paramAction;
      const containerWithType = actionEl.closest('[data-param-type]');
      const type = actionEl.dataset.paramType || (containerWithType ? containerWithType.dataset.paramType : null);
      if (!action || !type) return;
      if (action === 'create') {
        createParameter(type, actionEl);
        return;
      }
      const entry = actionEl.closest('[data-param-id]');
      if (!entry) return;
      const id = Number.parseInt(entry.dataset.paramId, 10);
      if (!Number.isFinite(id)) return;
      if (action === 'save') {
        updateParameterValue(id, type, actionEl);
      } else if (action === 'delete') {
        deleteParameter(id, type, actionEl);
      } else if (action === 'restore') {
        restoreParameter(id, type, actionEl);
      }
    }

    function handleDocumentChange(event) {
      const dependencySelect = event.target.closest('[data-param-dependency-field]');
      if (dependencySelect) {
        const group = dependencySelect.closest('[data-param-dependency-group]');
        const type = group ? group.dataset.paramType || dependencySelect.dataset.paramType : dependencySelect.dataset.paramType;
        if (type) {
          dependencySelect.dataset.paramDependencyValue = normalizeDependencyValue(dependencySelect.value);
          const targetGroup = group
            || dependencySelect.closest('[data-param-dependency-group]')
            || dependencySelect.closest('[data-param-type]');
          if (targetGroup) {
            refreshDependencySelectGroup(targetGroup, type);
          }
        }
        return;
      }
      const stateSelect = event.target.closest('[data-param-field="state"]');
      if (!stateSelect) return;
      const entry = stateSelect.closest('[data-param-id]');
      if (!entry) return;
      const id = Number.parseInt(entry.dataset.paramId, 10);
      const type = entry.dataset.paramType;
      if (!Number.isFinite(id) || !type) return;
      updateParameterState(id, type, stateSelect);
    }

    function bindEvents() {
      document.addEventListener('click', handleDocumentClick);
      document.addEventListener('change', handleDocumentChange);
    }

    bindEvents();

    return {
      deleteParameter,
      prepareCityParameterSettingsTrigger,
      prepareParameterSettingsTrigger,
      renderCityCard,
      renderCurrentParameterModal,
      renderParameterModal,
      renderParameters,
      resetParameterItemsSettingsModal,
      restoreParameter,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsParametersRuntime) {
        return window.__settingsParametersRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        resetParameterItemsSettingsModal: runtime.resetParameterItemsSettingsModal,
        prepareCityParameterSettingsTrigger: runtime.prepareCityParameterSettingsTrigger,
        prepareParameterSettingsTrigger: runtime.prepareParameterSettingsTrigger,
      });
      window.__settingsParametersRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsParametersRuntime = Object.freeze({
    ...api,
    deleteParameter(...args) {
      return window.__settingsParametersRuntime?.deleteParameter?.(...args);
    },
    prepareCityParameterSettingsTrigger(...args) {
      return window.__settingsParametersRuntime?.prepareCityParameterSettingsTrigger?.(...args);
    },
    prepareParameterSettingsTrigger(...args) {
      return window.__settingsParametersRuntime?.prepareParameterSettingsTrigger?.(...args);
    },
    renderCityCard(...args) {
      return window.__settingsParametersRuntime?.renderCityCard?.(...args);
    },
    renderCurrentParameterModal(...args) {
      return window.__settingsParametersRuntime?.renderCurrentParameterModal?.(...args);
    },
    renderParameterModal(...args) {
      return window.__settingsParametersRuntime?.renderParameterModal?.(...args);
    },
    renderParameters(...args) {
      return window.__settingsParametersRuntime?.renderParameters?.(...args);
    },
    resetParameterItemsSettingsModal(...args) {
      return window.__settingsParametersRuntime?.resetParameterItemsSettingsModal?.(...args);
    },
    restoreParameter(...args) {
      return window.__settingsParametersRuntime?.restoreParameter?.(...args);
    },
  });
}());

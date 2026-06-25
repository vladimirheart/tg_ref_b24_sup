(function () {
  if (window.SettingsParameterDataRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      parameterData: {},
      parameterStatusCache: new Map(),
      itConnectionCategoryLabels: {},
      itConnectionCategoryKeys: [],
      itConnectionDefaultCategory: 'equipment_type',
    };

    const elements = {
      itConnectionCategorySelect: document.getElementById('itConnectionAddModal')
        ? document.getElementById('itConnectionAddModal').querySelector('[data-it-connection-category-select]')
        : null,
    };

    function getParameterTitles() {
      const value = typeof options.getParameterTitles === 'function' ? options.getParameterTitles() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterDependencies() {
      const value = typeof options.getParameterDependencies === 'function' ? options.getParameterDependencies() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getParameterStates() {
      const value = typeof options.getParameterStates === 'function' ? options.getParameterStates() : null;
      return Array.isArray(value) && value.length ? value : ['Активен'];
    }

    function getItConnectionFallbackLabels() {
      const value = typeof options.getItConnectionFallbackLabels === 'function'
        ? options.getItConnectionFallbackLabels()
        : null;
      return value && typeof value === 'object' ? value : {};
    }

    function normalizePartnerContactExtra(extra, context) {
      if (typeof options.normalizePartnerContactExtra === 'function') {
        return options.normalizePartnerContactExtra(extra, context);
      }
      return Object.assign({}, extra || {});
    }

    function resetPartnerContactEdits() {
      if (typeof options.resetPartnerContactEdits === 'function') {
        options.resetPartnerContactEdits();
      }
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

    function createEmptyParameterData() {
      const result = {};
      Object.keys(getParameterTitles()).forEach((type) => {
        result[type] = [];
      });
      return result;
    }

    function getParameterData() {
      return state.parameterData;
    }

    function determineItConnectionDefaultCategory(keys) {
      if (Array.isArray(keys) && keys.includes('equipment_type')) {
        return 'equipment_type';
      }
      if (Array.isArray(keys) && keys.length) {
        return keys[0];
      }
      return 'equipment_type';
    }

    function sanitizeItConnectionCategoryMap(source) {
      const target = {};
      if (!source || typeof source !== 'object') {
        return target;
      }
      Object.keys(source).forEach((key) => {
        const rawKey = typeof key === 'string' ? key : String(key || '');
        const label = source[key];
        if (typeof rawKey !== 'string' || typeof label !== 'string') {
          return;
        }
        const trimmedKey = rawKey.trim();
        const trimmedLabel = label.trim();
        if (!trimmedKey || !trimmedLabel) {
          return;
        }
        target[trimmedKey] = trimmedLabel;
      });
      return target;
    }

    function rebuildItConnectionCategorySelect(select, selectedKey) {
      if (!(select instanceof HTMLSelectElement)) return;
      const desiredValue = typeof selectedKey === 'string' ? selectedKey : select.value;
      const fragment = document.createDocumentFragment();
      state.itConnectionCategoryKeys.forEach((key) => {
        const option = document.createElement('option');
        option.value = key;
        option.textContent = state.itConnectionCategoryLabels[key] || key;
        if (key === desiredValue) {
          option.selected = true;
        }
        fragment.appendChild(option);
      });
      select.innerHTML = '';
      if (fragment.childNodes.length) {
        select.appendChild(fragment);
      } else {
        const fallbackOption = document.createElement('option');
        fallbackOption.value = state.itConnectionDefaultCategory;
        fallbackOption.textContent =
          state.itConnectionCategoryLabels[state.itConnectionDefaultCategory] || state.itConnectionDefaultCategory;
        select.appendChild(fallbackOption);
      }
      if (
        desiredValue &&
        select.value !== desiredValue &&
        state.itConnectionCategoryKeys.includes(desiredValue)
      ) {
        select.value = desiredValue;
      }
    }

    function setItConnectionCategories(newLabels, options = {}) {
      const base = sanitizeItConnectionCategoryMap(getItConnectionFallbackLabels());
      const overrides = sanitizeItConnectionCategoryMap(newLabels);
      Object.keys(overrides).forEach((key) => {
        base[key] = overrides[key];
      });
      if (!Object.keys(base).length) {
        base.equipment_type = 'Тип оборудования';
        base.equipment_vendor = 'Производитель оборудования';
        base.equipment_model = 'Модель оборудования';
        base.equipment_status = 'Статус оборудования';
      }
      state.itConnectionCategoryLabels = base;
      state.itConnectionCategoryKeys = Object.keys(state.itConnectionCategoryLabels);
      state.itConnectionDefaultCategory = determineItConnectionDefaultCategory(state.itConnectionCategoryKeys);
      if (elements.itConnectionCategorySelect) {
        const selectedKey = typeof options.selectedKey === 'string' ? options.selectedKey : undefined;
        rebuildItConnectionCategorySelect(elements.itConnectionCategorySelect, selectedKey);
      }
    }

    function getItConnectionDefaultCategory() {
      return state.itConnectionDefaultCategory;
    }

    function normalizeItConnectionCategory(value) {
      if (typeof value !== 'string') {
        return state.itConnectionDefaultCategory;
      }
      const trimmed = value.trim();
      if (!trimmed) {
        return state.itConnectionDefaultCategory;
      }
      if (state.itConnectionCategoryKeys.includes(trimmed)) {
        return trimmed;
      }
      const lowered = trimmed.toLowerCase();
      const matchedKey = state.itConnectionCategoryKeys.find((key) => {
        const label = state.itConnectionCategoryLabels[key];
        return typeof label === 'string' && label.trim().toLowerCase() === lowered;
      });
      return matchedKey || trimmed;
    }

    function getItConnectionCategoryLabel(category) {
      const normalized = normalizeItConnectionCategory(category);
      return state.itConnectionCategoryLabels[normalized] || normalized || '—';
    }

    function getDependencyChain(type) {
      const chain = getParameterDependencies()[type];
      return Array.isArray(chain) ? chain : [];
    }

    function normalizeDependencyValue(value) {
      return typeof value === 'string' ? value.trim() : '';
    }

    function getDependencyLabel(key) {
      return getParameterTitles()[key] || key;
    }

    function getParameterItemsSafe(type) {
      const items = state.parameterData[type];
      return Array.isArray(items) ? items : [];
    }

    function buildDependencySelect(type, dependencyKey, selectedValue, disabled, context) {
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

    function fillDependencySelectOptions(select, options, { placeholder, selected } = {}) {
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
      options.forEach((option) => {
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
        const options = buildDependencyOptions(dep, selections);
        fillDependencySelectOptions(select, options, { placeholder, selected: desired });
        const finalValue = normalizeDependencyValue(select.value);
        select.dataset.paramDependencyValue = finalValue;
        selections[dep] = finalValue;
      });
    }

    function refreshParameterDependencyControls(root, fallbackType = null) {
      const scope = root || document;
      scope.querySelectorAll('[data-param-dependency-group]').forEach((group) => {
        const groupType = group.dataset.paramType || fallbackType;
        if (!groupType) return;
        if (!getDependencyChain(groupType).length) return;
        refreshDependencySelectGroup(group, groupType);
      });
    }

    function collectDependencyValues(container, type) {
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

    function syncParameterData(source) {
      const data = source && typeof source === 'object' ? source : {};
      const result = createEmptyParameterData();
      const itConnectionCategoryUpdates = {};

      Object.keys(getParameterTitles()).forEach((type) => {
        const items = Array.isArray(data[type]) ? data[type] : [];
        result[type] = items.map((item) => {
          const rawId = item && Object.prototype.hasOwnProperty.call(item, 'id') ? item.id : null;
          const idNumber = Number(rawId);
          const normalizedId = Number.isFinite(idNumber) ? idNumber : Number.parseInt(rawId, 10);
          const rawValue = item && Object.prototype.hasOwnProperty.call(item, 'value') ? item.value : '';
          const value = rawValue === null || rawValue === undefined ? '' : String(rawValue);
          const stateValue = item && typeof item.state === 'string' && item.state ? item.state : getParameterStates()[0];
          const usageRaw = Number(item && item.usage_count);
          const usageCount = Number.isFinite(usageRaw) ? usageRaw : 0;
          const extraRaw = item && typeof item.extra === 'object' && item.extra !== null ? item.extra : {};
          const extra = extraRaw && typeof extraRaw === 'object' ? Object.assign({}, extraRaw) : {};
          const entry = {
            id: Number.isFinite(normalizedId) ? normalizedId : null,
            value,
            state: getParameterStates().includes(stateValue) ? stateValue : getParameterStates()[0],
            is_deleted: Boolean(item && item.is_deleted),
            deleted_at: item && item.deleted_at ? item.deleted_at : null,
            usage_count: usageCount,
          };

          if (type === 'it_connection') {
            const extraData = extra && typeof extra === 'object' ? extra : {};
            const rawCategory =
              (item && typeof item.category === 'string' && item.category) ||
              (typeof extraData.category === 'string' ? extraData.category : '');
            const category = normalizeItConnectionCategory(rawCategory);
            const trimmedValue = value.trim();
            const rawCategoryLabel =
              (item && typeof item.category_label === 'string' && item.category_label) ||
              (typeof extraData.category_label === 'string' ? extraData.category_label : '');
            const categoryLabel = typeof rawCategoryLabel === 'string' ? rawCategoryLabel.trim() : '';
            const equipmentTypeSource =
              (item && typeof item.equipment_type === 'string' && item.equipment_type) ||
              (typeof extraData.equipment_type === 'string' ? extraData.equipment_type : '');
            const equipmentVendorSource =
              (item && typeof item.equipment_vendor === 'string' && item.equipment_vendor) ||
              (typeof extraData.equipment_vendor === 'string' ? extraData.equipment_vendor : '');
            const equipmentModelSource =
              (item && typeof item.equipment_model === 'string' && item.equipment_model) ||
              (typeof extraData.equipment_model === 'string' ? extraData.equipment_model : '');
            const equipmentStatusSource =
              (item && typeof item.equipment_status === 'string' && item.equipment_status) ||
              (typeof extraData.equipment_status === 'string' ? extraData.equipment_status : '');
            const equipmentType = typeof equipmentTypeSource === 'string' ? equipmentTypeSource.trim() : '';
            const equipmentVendor = typeof equipmentVendorSource === 'string' ? equipmentVendorSource.trim() : '';
            const equipmentModel = typeof equipmentModelSource === 'string' ? equipmentModelSource.trim() : '';
            const equipmentStatus = typeof equipmentStatusSource === 'string' ? equipmentStatusSource.trim() : '';
            let categoryValue = trimmedValue;
            if (category === 'equipment_type' && equipmentType) {
              categoryValue = equipmentType;
            } else if (category === 'equipment_vendor' && equipmentVendor) {
              categoryValue = equipmentVendor;
            } else if (category === 'equipment_model' && equipmentModel) {
              categoryValue = equipmentModel;
            } else if (category === 'equipment_status' && equipmentStatus) {
              categoryValue = equipmentStatus;
            }
            if (category && categoryLabel) {
              itConnectionCategoryUpdates[category] = categoryLabel;
            }
            const effectiveLabel = categoryLabel || state.itConnectionCategoryLabels[category] || category;
            entry.value = categoryValue;
            entry.category = category;
            entry.category_label = effectiveLabel;
            entry.equipment_type = equipmentType;
            entry.equipment_vendor = equipmentVendor;
            entry.equipment_model = equipmentModel;
            entry.equipment_status = equipmentStatus;
            entry.extra = {
              category,
              category_label: effectiveLabel,
              equipment_type: equipmentType,
              equipment_vendor: equipmentVendor,
              equipment_model: equipmentModel,
              equipment_status: equipmentStatus,
            };
          } else if (type === 'iiko_server') {
            const extraData = extra && typeof extra === 'object' ? extra : {};
            const serverNameSource =
              (item && typeof item.server_name === 'string' && item.server_name) ||
              (typeof extraData.server_name === 'string' ? extraData.server_name : '');
            const serverName = typeof serverNameSource === 'string' ? serverNameSource.trim() : '';
            entry.server_name = serverName;
            entry.extra = Object.assign({}, extraData, { server_name: serverName });
          } else if (type === 'partner_contact') {
            const normalizedExtra = normalizePartnerContactExtra(extra, { value });
            entry.contact_type = normalizedExtra.contact_type;
            entry.phones = normalizedExtra.phones;
            entry.emails = normalizedExtra.emails;
            entry.contacts = normalizedExtra.contacts;
            entry.served_legal_entity_id = normalizedExtra.served_legal_entity_id;
            entry.served_legal_entity_name = normalizedExtra.served_legal_entity_name;
            entry.legal_entity = normalizedExtra.legal_entity;
            entry.inn = normalizedExtra.inn;
            entry.extra = normalizedExtra;
          }

          const dependencyChain = getDependencyChain(type);
          if (dependencyChain.length) {
            const baseExtra = entry.extra && typeof entry.extra === 'object'
              ? Object.assign({}, entry.extra)
              : Object.assign({}, extra);
            const dependenciesSource = baseExtra && typeof baseExtra.dependencies === 'object'
              ? baseExtra.dependencies
              : {};
            const normalizedDependencies = {};
            dependencyChain.forEach((dep) => {
              const raw = Object.prototype.hasOwnProperty.call(dependenciesSource, dep)
                ? dependenciesSource[dep]
                : baseExtra[dep];
              normalizedDependencies[dep] = normalizeDependencyValue(raw);
            });
            entry.dependencies = normalizedDependencies;
            const enrichedExtra = Object.assign({}, baseExtra, { dependencies: normalizedDependencies });
            dependencyChain.forEach((dep) => {
              enrichedExtra[dep] = normalizedDependencies[dep];
            });
            entry.extra = enrichedExtra;
          } else {
            entry.dependencies = {};
            if (entry.extra === undefined && extra && Object.keys(extra).length) {
              entry.extra = extra;
            }
          }

          return entry;
        });
      });

      if (Object.keys(itConnectionCategoryUpdates).length) {
        setItConnectionCategories(itConnectionCategoryUpdates);
      }
      state.parameterData = result;
      state.parameterStatusCache.clear();
      resetPartnerContactEdits();
      return state.parameterData;
    }

    state.parameterData = createEmptyParameterData();
    setItConnectionCategories(getItConnectionFallbackLabels());

    return {
      buildDependencySelect,
      collectDependencyValues,
      getDependencyChain,
      getDependencyLabel,
      getItConnectionCategoryLabel,
      getItConnectionDefaultCategory,
      getParameterData,
      normalizeDependencyValue,
      normalizeItConnectionCategory,
      rebuildItConnectionCategorySelect,
      refreshParameterDependencyControls,
      setItConnectionCategories,
      syncParameterData,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsParameterDataRuntime) {
        return window.__settingsParameterDataRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsParameterDataRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsParameterDataRuntime = Object.freeze(api);
}());

(function () {
  if (window.SettingsLocationsTreeRuntime) {
    return;
  }

  const LOCATION_STATUS_OPTIONS = [
    'Активен',
    'Закрыт',
    'Готовится к запуску',
    'Приостановлен',
    'Заморожен',
  ];
  const DEFAULT_LOCATION_STATUS = LOCATION_STATUS_OPTIONS[0];
  const LOCATION_STATUS_CLASS_MAP = {
    Активен: 'status-active',
    Закрыт: 'status-closed',
    'Готовится к запуску': 'status-prelaunch',
    Приостановлен: 'status-paused',
    Заморожен: 'status-frozen',
  };
  const LOCATION_STATUS_DEFAULT_CLASS = 'status-default';
  const EDIT_ICON_HTML = '<i class="bi bi-pencil"></i>';
  const SAVE_ICON_HTML = '<i class="bi bi-check-lg"></i>';
  const DELETE_ICON_HTML = '<i class="bi bi-trash"></i>';
  const COLLAPSE_ICON_HTML = '<i class="bi bi-chevron-down"></i>';
  const EXPAND_ICON_HTML = '<i class="bi bi-chevron-right"></i>';

  function defaultNotify(message, type = 'info') {
    if (typeof window.showPopup === 'function') {
      window.showPopup(message, type);
      return;
    }
    console.log(message);
  }

  function sanitizeStatusesMap(source) {
    const result = {};
    if (!source || typeof source !== 'object') {
      return result;
    }
    Object.entries(source).forEach(([key, value]) => {
      if (typeof key !== 'string') {
        return;
      }
      const raw = typeof value === 'string' ? value.trim() : '';
      result[key] = LOCATION_STATUS_OPTIONS.includes(raw) ? raw : DEFAULT_LOCATION_STATUS;
    });
    return result;
  }

  function sortObjectAlphabetically(obj) {
    if (!obj || typeof obj !== 'object') {
      return {};
    }
    return Object.keys(obj)
      .sort((left, right) => left.localeCompare(right))
      .reduce((sorted, key) => {
        sorted[key] = obj[key];
        return sorted;
      }, {});
  }

  function sortArrayAlphabetically(arr) {
    return Array.isArray(arr) ? arr.slice().sort((left, right) => left.localeCompare(right)) : [];
  }

  function normalizeLocationTree(tree) {
    if (!tree || typeof tree !== 'object') {
      return {};
    }
    const normalized = {};
    Object.keys(tree)
      .sort((left, right) => left.localeCompare(right))
      .forEach((business) => {
        const types = tree[business];
        if (!types || typeof types !== 'object') {
          normalized[business] = {};
          return;
        }
        const sortedTypes = {};
        Object.keys(types)
          .sort((left, right) => left.localeCompare(right))
          .forEach((type) => {
            const cities = types[type];
            if (Array.isArray(cities)) {
              sortedTypes[type] = sortArrayAlphabetically(cities);
            } else if (cities && typeof cities === 'object') {
              const sortedCities = {};
              Object.keys(cities)
                .sort((left, right) => left.localeCompare(right))
                .forEach((city) => {
                  sortedCities[city] = sortArrayAlphabetically(cities[city]);
                });
              sortedTypes[type] = sortedCities;
            } else {
              sortedTypes[type] = {};
            }
          });
        normalized[business] = sortedTypes;
      });
    return normalized;
  }

  function sanitizeMetaMap(source) {
    const result = {};
    if (!source || typeof source !== 'object') {
      return result;
    }
    Object.entries(source).forEach(([key, value]) => {
      if (typeof key !== 'string' || !value || typeof value !== 'object') {
        return;
      }
      const country = typeof value.country === 'string' ? value.country.trim() : '';
      const partnerType = typeof value.partner_type === 'string' ? value.partner_type.trim() : '';
      if (!country && !partnerType) {
        return;
      }
      result[key] = { country, partner_type: partnerType };
    });
    return result;
  }

  function createRuntime(options = {}) {
    const collapsedLocationNodes = new Set();
    const popup = typeof options.showPopup === 'function'
      ? options.showPopup
      : defaultNotify;
    const state = {
      tree:
        options.initialLocations &&
        typeof options.initialLocations === 'object' &&
        options.initialLocations.tree &&
        typeof options.initialLocations.tree === 'object'
          ? normalizeLocationTree(options.initialLocations.tree)
          : normalizeLocationTree(options.initialLocations || {}),
      statuses: sanitizeStatusesMap(
        options.initialLocations && typeof options.initialLocations === 'object'
          ? options.initialLocations.statuses
          : {},
      ),
      city_meta: sanitizeMetaMap(
        options.initialLocations && typeof options.initialLocations === 'object'
          ? options.initialLocations.city_meta
          : {},
      ),
      location_meta: sanitizeMetaMap(
        options.initialLocations && typeof options.initialLocations === 'object'
          ? options.initialLocations.location_meta
          : {},
      ),
    };

    function getState() {
      return state;
    }

    function makeStatusKey(level, ...parts) {
      return [level].concat(parts).join('::');
    }

    function makeCityMetaKey(business, typeName, cityName) {
      return [business, typeName, cityName].map((part) => (part || '').trim()).join('::');
    }

    function makeLocationMetaKey(business, typeName, cityName, locationName) {
      return [business, typeName, cityName, locationName].map((part) => (part || '').trim()).join('::');
    }

    function readNodeMeta(mapName, key) {
      const storage = state && typeof state === 'object' ? state[mapName] : null;
      const raw = storage && typeof storage === 'object' ? storage[key] : null;
      if (!raw || typeof raw !== 'object') {
        return { country: '', partner_type: '' };
      }
      return {
        country: typeof raw.country === 'string' ? raw.country.trim() : '',
        partner_type: typeof raw.partner_type === 'string' ? raw.partner_type.trim() : '',
      };
    }

    function writeNodeMeta(mapName, key, meta) {
      if (!state[mapName] || typeof state[mapName] !== 'object') {
        state[mapName] = {};
      }
      const country = typeof meta.country === 'string' ? meta.country.trim() : '';
      const partnerType = typeof meta.partner_type === 'string' ? meta.partner_type.trim() : '';
      if (!country && !partnerType) {
        delete state[mapName][key];
        return;
      }
      state[mapName][key] = { country, partner_type: partnerType };
    }

    function getStatus(level, ...parts) {
      const key = makeStatusKey(level, ...parts);
      const stored = state.statuses && state.statuses[key];
      if (typeof stored === 'string' && LOCATION_STATUS_OPTIONS.includes(stored)) {
        return stored;
      }
      return DEFAULT_LOCATION_STATUS;
    }

    function setStatus(level, parts, status) {
      const resolved = LOCATION_STATUS_OPTIONS.includes(status) ? status : DEFAULT_LOCATION_STATUS;
      const key = makeStatusKey(level, ...parts);
      state.statuses[key] = resolved;
    }

    function renameStatusEntries(level, context, oldName, newName) {
      if (!oldName || oldName === newName) {
        return;
      }
      const updates = [];
      Object.keys(state.statuses || {}).forEach((key) => {
        const parts = key.split('::');
        const kind = parts[0];
        if (level === 'business') {
          if (parts[1] === oldName) {
            parts[1] = newName;
            updates.push([key, parts.join('::')]);
          }
        } else if (level === 'type') {
          if (['type', 'city', 'location'].includes(kind) && parts[1] === context.business && parts[2] === oldName) {
            parts[2] = newName;
            updates.push([key, parts.join('::')]);
          }
        } else if (level === 'city') {
          if (['city', 'location'].includes(kind)
            && parts[1] === context.business
            && parts[2] === context.type
            && parts[3] === oldName) {
            parts[3] = newName;
            updates.push([key, parts.join('::')]);
          }
        } else if (level === 'location') {
          if (kind === 'location'
            && parts[1] === context.business
            && parts[2] === context.type
            && parts[3] === context.city
            && parts[4] === oldName) {
            parts[4] = newName;
            updates.push([key, parts.join('::')]);
          }
        }
      });
      updates.forEach(([oldKey, newKey]) => {
        state.statuses[newKey] = state.statuses[oldKey];
        if (oldKey !== newKey) {
          delete state.statuses[oldKey];
        }
      });
    }

    function removeStatusEntries(level, context, targetName) {
      Object.keys(state.statuses || {}).forEach((key) => {
        const parts = key.split('::');
        const kind = parts[0];
        let shouldDelete = false;
        if (level === 'business') {
          shouldDelete = parts[1] === targetName;
        } else if (level === 'type') {
          shouldDelete = ['type', 'city', 'location'].includes(kind)
            && parts[1] === context.business
            && parts[2] === targetName;
        } else if (level === 'city') {
          shouldDelete = ['city', 'location'].includes(kind)
            && parts[1] === context.business
            && parts[2] === context.type
            && parts[3] === targetName;
        } else if (level === 'location') {
          shouldDelete = kind === 'location'
            && parts[1] === context.business
            && parts[2] === context.type
            && parts[3] === context.city
            && parts[4] === targetName;
        }
        if (shouldDelete) {
          delete state.statuses[key];
        }
      });
    }

    function makeCollapseKey(level, ...parts) {
      return ['collapse', level].concat(parts).join('::');
    }

    function pruneCollapsedNodes(tree) {
      const validKeys = new Set();
      Object.keys(tree || {}).forEach((business) => {
        validKeys.add(makeCollapseKey('business', business));
        const types = tree[business] || {};
        if (types && typeof types === 'object') {
          Object.keys(types).forEach((type) => {
            validKeys.add(makeCollapseKey('type', business, type));
            const cities = types[type];
            if (cities && typeof cities === 'object' && !Array.isArray(cities)) {
              Object.keys(cities).forEach((city) => {
                validKeys.add(makeCollapseKey('city', business, type, city));
              });
            }
          });
        }
      });
      Array.from(collapsedLocationNodes).forEach((key) => {
        if (!validKeys.has(key)) {
          collapsedLocationNodes.delete(key);
        }
      });
    }

    function setupTreeToggle(button, childrenList, key) {
      if (!(button instanceof HTMLElement) || !(childrenList instanceof HTMLElement) || !key) {
        return;
      }
      button.setAttribute('data-no-toggle', 'true');
      let isCollapsed = collapsedLocationNodes.has(key);

      const setCollapsed = (nextState) => {
        isCollapsed = nextState;
        if (isCollapsed) {
          collapsedLocationNodes.add(key);
        } else {
          collapsedLocationNodes.delete(key);
        }
        childrenList.classList.toggle('is-collapsed', isCollapsed);
        button.setAttribute('aria-expanded', isCollapsed ? 'false' : 'true');
        button.innerHTML = isCollapsed ? EXPAND_ICON_HTML : COLLAPSE_ICON_HTML;
        const title = isCollapsed ? 'Развернуть ветку' : 'Свернуть ветку';
        button.title = title;
        button.setAttribute('aria-label', title);
      };

      button.addEventListener('click', (event) => {
        event.preventDefault();
        event.stopPropagation();
        setCollapsed(!isCollapsed);
      });

      setCollapsed(isCollapsed);
    }

    function createStatusSelect(level, parts) {
      const select = document.createElement('select');
      select.className = 'form-select form-select-sm location-status-select';
      select.setAttribute('data-no-toggle', 'true');
      LOCATION_STATUS_OPTIONS.forEach((option) => {
        const opt = document.createElement('option');
        opt.value = option;
        opt.textContent = option;
        select.appendChild(opt);
      });
      select.value = getStatus(level, ...parts);
      select.disabled = true;
      return select;
    }

    function applyStatusStyle(element, status) {
      if (!element) {
        return;
      }
      const classesToRemove = new Set(Object.values(LOCATION_STATUS_CLASS_MAP));
      classesToRemove.add(LOCATION_STATUS_DEFAULT_CLASS);
      classesToRemove.forEach((cls) => {
        if (cls) {
          element.classList.remove(cls);
        }
      });
      const targetClass = LOCATION_STATUS_CLASS_MAP[status] || LOCATION_STATUS_DEFAULT_CLASS;
      if (targetClass) {
        element.classList.add(targetClass);
      }
    }

    function configureEditControls({ input, statusSelect, editButton, deleteButton, onSubmit, onReset }) {
      const fields = [];
      if (input) {
        input.disabled = true;
        input.setAttribute('data-no-toggle', 'true');
        input.dataset.originalValue = input.value;
        fields.push(input);
      }
      if (statusSelect) {
        statusSelect.disabled = true;
        statusSelect.dataset.originalValue = statusSelect.value;
        fields.push(statusSelect);
      }
      if (!editButton) {
        return;
      }
      editButton.dataset.mode = 'view';
      editButton.setAttribute('data-no-toggle', 'true');
      editButton.innerHTML = EDIT_ICON_HTML;
      editButton.title = 'Редактировать';

      if (deleteButton) {
        deleteButton.disabled = true;
        deleteButton.setAttribute('data-no-toggle', 'true');
      }

      const enterEdit = () => {
        fields.forEach((field) => {
          field.disabled = false;
        });
        editButton.dataset.mode = 'edit';
        editButton.innerHTML = SAVE_ICON_HTML;
        editButton.title = 'Сохранить';
        editButton.classList.remove('btn-outline-secondary');
        editButton.classList.add('btn-outline-success');
        if (deleteButton) {
          deleteButton.disabled = false;
        }
        if (input) {
          input.focus();
          input.select();
        }
      };

      const exitEdit = () => {
        fields.forEach((field) => {
          field.disabled = true;
        });
        editButton.dataset.mode = 'view';
        editButton.innerHTML = EDIT_ICON_HTML;
        editButton.title = 'Редактировать';
        editButton.classList.remove('btn-outline-success');
        editButton.classList.add('btn-outline-secondary');
        if (deleteButton) {
          deleteButton.disabled = true;
        }
      };

      const resetFields = () => {
        if (typeof onReset === 'function') {
          onReset();
          return;
        }
        if (input) {
          input.value = input.dataset.originalValue || input.value;
        }
        if (statusSelect) {
          statusSelect.value = statusSelect.dataset.originalValue || statusSelect.value;
        }
      };

      const applySubmit = () => {
        if (typeof onSubmit !== 'function') {
          exitEdit();
          return;
        }
        const nameValue = input ? (input.value || '').trim() : '';
        const statusValue = statusSelect ? statusSelect.value : undefined;
        const result = onSubmit(nameValue, { status: statusValue });
        if (result === false) {
          return;
        }
        if (input) {
          input.dataset.originalValue = input.value;
        }
        if (statusSelect) {
          statusSelect.dataset.originalValue = statusSelect.value;
        }
        exitEdit();
      };

      editButton.addEventListener('click', () => {
        if (editButton.dataset.mode === 'edit') {
          applySubmit();
        } else {
          enterEdit();
        }
      });

      if (input) {
        input.addEventListener('keydown', (event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            applySubmit();
          } else if (event.key === 'Escape') {
            event.preventDefault();
            resetFields();
            exitEdit();
          }
        });
      }

      if (statusSelect) {
        statusSelect.addEventListener('keydown', (event) => {
          if (event.key === 'Escape') {
            event.preventDefault();
            resetFields();
            exitEdit();
          }
        });
      }
    }

    function confirmMultipleTimes(message, attempts = 3) {
      const total = Number.isFinite(Number(attempts)) && attempts > 0 ? Math.ceil(attempts) : 1;
      for (let index = 1; index <= total; index += 1) {
        const suffix = total > 1 ? `\nПодтвердите действие (${index}/${total})` : '';
        if (!confirm(`${message}${suffix}`)) {
          return false;
        }
      }
      return true;
    }

    function getTypeMetrics(cities) {
      if (!cities || typeof cities !== 'object') {
        return { cityCount: 0, locationCount: 0 };
      }
      const cityNames = Object.keys(cities);
      let locationCount = 0;
      cityNames.forEach((cityName) => {
        const entries = Array.isArray(cities[cityName]) ? cities[cityName] : [];
        locationCount += entries.length;
      });
      return { cityCount: cityNames.length, locationCount };
    }

    function getBusinessMetrics(types) {
      const typeNames = types && typeof types === 'object' ? Object.keys(types) : [];
      let cityCount = 0;
      let locationCount = 0;
      typeNames.forEach((typeName) => {
        const { cityCount: typeCities, locationCount: typeLocations } = getTypeMetrics(types[typeName]);
        cityCount += typeCities;
        locationCount += typeLocations;
      });
      return { typeCount: typeNames.length, cityCount, locationCount };
    }

    function createLocationMeta(badges) {
      const normalized = Array.isArray(badges)
        ? badges.filter((badge) => badge && typeof badge.label === 'string')
        : [];
      if (!normalized.length) {
        return null;
      }
      const meta = document.createElement('div');
      meta.className = 'location-node-meta';
      normalized.forEach(({ label, value }) => {
        const badge = document.createElement('span');
        badge.className = 'badge rounded-pill text-bg-light';
        badge.textContent = `${label}: ${value}`;
        meta.appendChild(badge);
      });
      return meta;
    }

    function createLocationTreeRow(level, { name, statusPath, onSubmit, onReset, onDelete, collapsible = false }) {
      const row = document.createElement('div');
      row.className = 'location-tree-row';
      row.dataset.locationLevel = level;

      let toggleButton = null;
      if (collapsible) {
        toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.className = 'btn btn-sm btn-outline-secondary btn-icon location-tree-toggle';
        toggleButton.innerHTML = COLLAPSE_ICON_HTML;
        toggleButton.title = 'Свернуть ветку';
        toggleButton.setAttribute('aria-expanded', 'true');
        row.appendChild(toggleButton);
      }

      const bubble = document.createElement('div');
      bubble.className = 'location-structure-row location-tree-bubble';
      bubble.dataset.locationLevel = level;
      row.appendChild(bubble);

      const input = document.createElement('input');
      input.type = 'text';
      input.className = 'form-control form-control-sm location-name-input';
      input.value = name;
      bubble.appendChild(input);

      const controls = document.createElement('div');
      controls.className = 'location-tree-controls';
      row.appendChild(controls);

      const statusContainer = document.createElement('div');
      statusContainer.className = 'location-status-wrapper';
      const statusSelect = createStatusSelect(level, statusPath);
      statusContainer.appendChild(statusSelect);
      controls.appendChild(statusContainer);

      const actions = document.createElement('div');
      actions.className = 'location-actions';
      controls.appendChild(actions);

      const editButton = document.createElement('button');
      editButton.type = 'button';
      editButton.className = 'btn btn-sm btn-outline-secondary btn-icon';
      editButton.innerHTML = EDIT_ICON_HTML;
      editButton.title = 'Редактировать';
      actions.appendChild(editButton);

      let deleteButton = null;
      if (typeof onDelete === 'function') {
        deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.className = 'btn btn-sm btn-outline-danger btn-icon';
        deleteButton.innerHTML = DELETE_ICON_HTML;
        deleteButton.title = 'Удалить';
        deleteButton.addEventListener('click', () => onDelete());
        actions.appendChild(deleteButton);
      }

      configureEditControls({
        input,
        statusSelect,
        editButton,
        deleteButton,
        onSubmit,
        onReset,
      });

      const updateAppearance = () => applyStatusStyle(bubble, statusSelect.value);
      statusSelect.addEventListener('change', updateAppearance);
      updateAppearance();

      return {
        row,
        input,
        statusSelect,
        actions,
        editButton,
        deleteButton,
        toggleButton,
      };
    }

    function createLocationLeaf(businessName, typeName, cityName, locationName) {
      const item = document.createElement('li');
      item.className = 'org-tree__item location-tree__item location-level-location';

      const { row, input, statusSelect } = createLocationTreeRow('location', {
        name: locationName,
        statusPath: [businessName, typeName, cityName, locationName],
        onSubmit: (newName, meta) =>
          updateLocation(
            businessName,
            typeName,
            cityName,
            locationName,
            newName || locationName,
            meta.status,
          ),
        onReset: () => {
          input.value = locationName;
          statusSelect.value = getStatus('location', businessName, typeName, cityName, locationName);
        },
        onDelete: () => removeLocation(businessName, typeName, cityName, locationName),
      });

      const locationMeta = readNodeMeta('location_meta', makeLocationMetaKey(businessName, typeName, cityName, locationName));
      const meta = createLocationMeta([
        ...(locationMeta.country ? [{ label: 'Страна', value: locationMeta.country }] : []),
        ...(locationMeta.partner_type ? [{ label: 'Тип партнёра', value: locationMeta.partner_type }] : []),
      ]);

      const card = document.createElement('div');
      card.className = 'location-tree-card';
      card.appendChild(row);
      if (meta) {
        card.appendChild(meta);
      }
      item.appendChild(card);

      return item;
    }

    function createCityNode(businessName, typeName, cityName, locations) {
      const item = document.createElement('li');
      item.className = 'org-tree__item location-tree__item location-level-city';

      const locationNames = Array.isArray(locations) ? locations : [];
      const collapseKey = makeCollapseKey('city', businessName, typeName, cityName);

      const { row, input, statusSelect, actions, toggleButton } = createLocationTreeRow('city', {
        name: cityName,
        statusPath: [businessName, typeName, cityName],
        onSubmit: (newName, meta) =>
          updateCity(businessName, typeName, cityName, newName || cityName, meta.status),
        onReset: () => {
          input.value = cityName;
          statusSelect.value = getStatus('city', businessName, typeName, cityName);
        },
        onDelete: () => removeCity(businessName, typeName, cityName),
        collapsible: locationNames.length > 0,
      });

      const addLocationBtn = document.createElement('button');
      addLocationBtn.type = 'button';
      addLocationBtn.className = 'btn btn-sm btn-outline-success';
      addLocationBtn.textContent = '+ Локация';
      addLocationBtn.addEventListener('click', () => addLocation(businessName, typeName, cityName));
      actions.appendChild(addLocationBtn);

      const cityMeta = readNodeMeta('city_meta', makeCityMetaKey(businessName, typeName, cityName));
      const meta = createLocationMeta([
        { label: 'Локаций', value: locationNames.length },
        ...(cityMeta.country ? [{ label: 'Страна', value: cityMeta.country }] : []),
        ...(cityMeta.partner_type ? [{ label: 'Тип партнёра', value: cityMeta.partner_type }] : []),
      ]);

      const card = document.createElement('div');
      card.className = 'location-tree-card';
      card.appendChild(row);
      if (meta) {
        card.appendChild(meta);
      }
      item.appendChild(card);

      if (locationNames.length) {
        const childrenList = document.createElement('ul');
        childrenList.className = 'org-tree__children location-tree__children list-unstyled';
        locationNames.forEach((locationName) => {
          childrenList.appendChild(createLocationLeaf(businessName, typeName, cityName, locationName));
        });
        item.appendChild(childrenList);
        setupTreeToggle(toggleButton, childrenList, collapseKey);
      }

      return item;
    }

    function createTypeNode(businessName, typeName, cities) {
      const item = document.createElement('li');
      item.className = 'org-tree__item location-tree__item location-level-type';

      const cityEntries = cities && typeof cities === 'object' ? Object.entries(cities) : [];
      const collapseKey = makeCollapseKey('type', businessName, typeName);

      const { row, input, statusSelect, actions, toggleButton } = createLocationTreeRow('type', {
        name: typeName,
        statusPath: [businessName, typeName],
        onSubmit: (newName, meta) =>
          updateType(businessName, typeName, newName || typeName, meta.status),
        onReset: () => {
          input.value = typeName;
          statusSelect.value = getStatus('type', businessName, typeName);
        },
        onDelete: () => removeType(businessName, typeName),
        collapsible: cityEntries.length > 0,
      });

      const addCityBtn = document.createElement('button');
      addCityBtn.type = 'button';
      addCityBtn.className = 'btn btn-sm btn-outline-success';
      addCityBtn.textContent = '+ Город';
      addCityBtn.addEventListener('click', () => addCityToType(businessName, typeName));
      actions.appendChild(addCityBtn);

      const { cityCount, locationCount } = getTypeMetrics(cities);
      const meta = createLocationMeta([
        { label: 'Городов', value: cityCount },
        { label: 'Локаций', value: locationCount },
      ]);

      const card = document.createElement('div');
      card.className = 'location-tree-card';
      card.appendChild(row);
      if (meta) {
        card.appendChild(meta);
      }
      item.appendChild(card);

      if (cityEntries.length) {
        const childrenList = document.createElement('ul');
        childrenList.className = 'org-tree__children location-tree__children list-unstyled';
        cityEntries.forEach(([cityName, cityLocations]) => {
          childrenList.appendChild(createCityNode(businessName, typeName, cityName, cityLocations));
        });
        item.appendChild(childrenList);
        setupTreeToggle(toggleButton, childrenList, collapseKey);
      }

      return item;
    }

    function createBusinessNode(businessName, types) {
      const item = document.createElement('li');
      item.className = 'org-tree__item location-tree__item location-level-business';

      const typeEntries = types && typeof types === 'object' ? Object.entries(types) : [];
      const collapseKey = makeCollapseKey('business', businessName);

      const { row, input, statusSelect, actions, toggleButton } = createLocationTreeRow('business', {
        name: businessName,
        statusPath: [businessName],
        onSubmit: (newName, meta) => updateBusiness(businessName, newName || businessName, meta.status),
        onReset: () => {
          input.value = businessName;
          statusSelect.value = getStatus('business', businessName);
        },
        onDelete: () => removeBusiness(businessName),
        collapsible: typeEntries.length > 0,
      });

      const addTypeBtn = document.createElement('button');
      addTypeBtn.type = 'button';
      addTypeBtn.className = 'btn btn-sm btn-outline-success';
      addTypeBtn.textContent = '+ Тип';
      addTypeBtn.addEventListener('click', () => addTypeToBusiness(businessName));
      actions.appendChild(addTypeBtn);

      const { typeCount, cityCount, locationCount } = getBusinessMetrics(types);
      const meta = createLocationMeta([
        { label: 'Типов', value: typeCount },
        { label: 'Городов', value: cityCount },
        { label: 'Локаций', value: locationCount },
      ]);

      const card = document.createElement('div');
      card.className = 'location-tree-card';
      card.appendChild(row);
      if (meta) {
        card.appendChild(meta);
      }
      item.appendChild(card);

      if (typeEntries.length) {
        const childrenList = document.createElement('ul');
        childrenList.className = 'org-tree__children location-tree__children list-unstyled';
        typeEntries.forEach(([typeName, cities]) => {
          childrenList.appendChild(createTypeNode(businessName, typeName, cities));
        });
        item.appendChild(childrenList);
        setupTreeToggle(toggleButton, childrenList, collapseKey);
      }

      return item;
    }

    function buildLocationsTree() {
      const container = document.getElementById('locationsEditor');
      if (!(container instanceof HTMLElement)) {
        return;
      }

      state.tree = normalizeLocationTree(state.tree);
      container.innerHTML = '';
      container.classList.add('org-tree-wrapper', 'location-tree-wrapper');

      const tree = state.tree || {};
      pruneCollapsedNodes(tree);
      const businessNames = Object.keys(tree);
      if (!businessNames.length) {
        const placeholder = document.createElement('div');
        placeholder.className = 'alert alert-info small mb-0';
        placeholder.textContent = 'Пока нет записей. Добавьте бизнес или воспользуйтесь мастером выше.';
        container.appendChild(placeholder);
        return;
      }

      const list = document.createElement('ul');
      list.className = 'org-tree location-tree list-unstyled';
      businessNames.forEach((businessName) => {
        list.appendChild(createBusinessNode(businessName, tree[businessName] || {}));
      });
      container.appendChild(list);
    }

    function updateBusiness(oldName, newName, statusValue) {
      const tree = state.tree || {};
      const currentName = (oldName || '').trim();
      if (!currentName || !Object.prototype.hasOwnProperty.call(tree, currentName)) {
        return false;
      }
      const trimmed = (newName || '').trim() || currentName;
      if (trimmed !== currentName && Object.prototype.hasOwnProperty.call(tree, trimmed)) {
        popup(`Бизнес "${trimmed}" уже существует`);
        return false;
      }
      if (trimmed !== currentName) {
        tree[trimmed] = tree[currentName];
        delete tree[currentName];
        Object.entries(state.city_meta || {}).forEach(([key, value]) => {
          if (key.startsWith(`${currentName}::`)) {
            delete state.city_meta[key];
            state.city_meta[`${trimmed}${key.slice(currentName.length)}`] = value;
          }
        });
        Object.entries(state.location_meta || {}).forEach(([key, value]) => {
          if (key.startsWith(`${currentName}::`)) {
            delete state.location_meta[key];
            state.location_meta[`${trimmed}${key.slice(currentName.length)}`] = value;
          }
        });
        renameStatusEntries('business', {}, currentName, trimmed);
      }
      state.tree = normalizeLocationTree(tree);
      setStatus('business', [trimmed], statusValue || getStatus('business', trimmed));
      buildLocationsTree();
      return true;
    }

    function addBusiness() {
      const name = prompt('Название бизнеса:');
      const trimmed = (name || '').trim();
      if (!trimmed) {
        return;
      }
      if (Object.prototype.hasOwnProperty.call(state.tree || {}, trimmed)) {
        popup(`Бизнес "${trimmed}" уже существует`);
        return;
      }
      state.tree[trimmed] = { 'Новый тип': { 'Новый город': [] } };
      setStatus('business', [trimmed], DEFAULT_LOCATION_STATUS);
      setStatus('type', [trimmed, 'Новый тип'], DEFAULT_LOCATION_STATUS);
      setStatus('city', [trimmed, 'Новый тип', 'Новый город'], DEFAULT_LOCATION_STATUS);
      buildLocationsTree();
    }

    function removeBusiness(name) {
      if (!confirmMultipleTimes(`Удалить бизнес "${name}"?`)) {
        return;
      }
      delete state.tree[name];
      Object.keys(state.city_meta || {}).forEach((key) => {
        if (key.startsWith(`${name}::`)) delete state.city_meta[key];
      });
      Object.keys(state.location_meta || {}).forEach((key) => {
        if (key.startsWith(`${name}::`)) delete state.location_meta[key];
      });
      removeStatusEntries('business', {}, name);
      buildLocationsTree();
    }

    function updateType(business, oldName, newName, statusValue) {
      const tree = state.tree || {};
      const types = tree[business];
      if (!types) {
        return false;
      }
      const currentName = (oldName || '').trim();
      const trimmed = (newName || '').trim() || currentName;
      if (trimmed !== currentName && Object.prototype.hasOwnProperty.call(types, trimmed)) {
        popup(`Тип "${trimmed}" уже существует`);
        return false;
      }
      if (trimmed !== currentName) {
        types[trimmed] = types[currentName];
        delete types[currentName];
        Object.entries(state.city_meta || {}).forEach(([key, value]) => {
          const prefix = `${business}::${currentName}::`;
          if (key.startsWith(prefix)) {
            delete state.city_meta[key];
            state.city_meta[`${business}::${trimmed}::${key.slice(prefix.length)}`] = value;
          }
        });
        Object.entries(state.location_meta || {}).forEach(([key, value]) => {
          const prefix = `${business}::${currentName}::`;
          if (key.startsWith(prefix)) {
            delete state.location_meta[key];
            state.location_meta[`${business}::${trimmed}::${key.slice(prefix.length)}`] = value;
          }
        });
        renameStatusEntries('type', { business }, currentName, trimmed);
      }
      tree[business] = types;
      state.tree = normalizeLocationTree(tree);
      setStatus('type', [business, trimmed], statusValue || getStatus('type', business, trimmed));
      buildLocationsTree();
      return true;
    }

    function addTypeToBusiness(business) {
      const types = state.tree[business];
      if (!types) {
        return;
      }
      const name = prompt('Название типа:');
      const trimmed = (name || '').trim();
      if (!trimmed) {
        return;
      }
      if (Object.prototype.hasOwnProperty.call(types, trimmed)) {
        popup(`Тип "${trimmed}" уже существует`);
        return;
      }
      types[trimmed] = { 'Новый город': [] };
      setStatus('type', [business, trimmed], DEFAULT_LOCATION_STATUS);
      setStatus('city', [business, trimmed, 'Новый город'], DEFAULT_LOCATION_STATUS);
      buildLocationsTree();
    }

    function removeType(business, typeName) {
      if (!confirmMultipleTimes(`Удалить тип "${typeName}"?`)) {
        return;
      }
      const types = state.tree[business];
      if (!types) {
        return;
      }
      delete types[typeName];
      Object.keys(state.city_meta || {}).forEach((key) => {
        if (key.startsWith(`${business}::${typeName}::`)) delete state.city_meta[key];
      });
      Object.keys(state.location_meta || {}).forEach((key) => {
        if (key.startsWith(`${business}::${typeName}::`)) delete state.location_meta[key];
      });
      removeStatusEntries('type', { business }, typeName);
      buildLocationsTree();
    }

    function updateCity(business, typeName, oldName, newName, statusValue) {
      const tree = state.tree || {};
      const types = tree[business];
      if (!types) {
        return false;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return false;
      }
      const currentName = (oldName || '').trim();
      const trimmed = (newName || '').trim() || currentName;
      if (trimmed !== currentName && Object.prototype.hasOwnProperty.call(cities, trimmed)) {
        popup(`Город "${trimmed}" уже существует`);
        return false;
      }
      if (trimmed !== currentName) {
        cities[trimmed] = cities[currentName];
        delete cities[currentName];
        const oldCityKey = makeCityMetaKey(business, typeName, currentName);
        const newCityKey = makeCityMetaKey(business, typeName, trimmed);
        const oldCityMeta = state.city_meta && state.city_meta[oldCityKey];
        if (oldCityMeta) {
          state.city_meta[newCityKey] = oldCityMeta;
          delete state.city_meta[oldCityKey];
        }
        Object.entries(state.location_meta || {}).forEach(([key, value]) => {
          const prefix = `${business}::${typeName}::${currentName}::`;
          if (key.startsWith(prefix)) {
            const suffix = key.slice(prefix.length);
            delete state.location_meta[key];
            state.location_meta[`${business}::${typeName}::${trimmed}::${suffix}`] = value;
          }
        });
        renameStatusEntries('city', { business, type: typeName }, currentName, trimmed);
      }
      setStatus('city', [business, typeName, trimmed], statusValue || getStatus('city', business, typeName, trimmed));
      buildLocationsTree();
      return true;
    }

    function addCityToType(business, typeName) {
      const types = state.tree[business];
      if (!types) {
        return;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return;
      }
      const name = prompt('Название города:');
      const trimmed = (name || '').trim();
      if (!trimmed) {
        return;
      }
      if (Object.prototype.hasOwnProperty.call(cities, trimmed)) {
        popup(`Город "${trimmed}" уже существует`);
        return;
      }
      cities[trimmed] = [];
      setStatus('city', [business, typeName, trimmed], DEFAULT_LOCATION_STATUS);
      buildLocationsTree();
    }

    function removeCity(business, typeName, cityName) {
      if (!confirmMultipleTimes(`Удалить город "${cityName}"?`)) {
        return;
      }
      const types = state.tree[business];
      if (!types) {
        return;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return;
      }
      delete cities[cityName];
      delete state.city_meta[makeCityMetaKey(business, typeName, cityName)];
      Object.keys(state.location_meta || {}).forEach((key) => {
        if (key.startsWith(`${business}::${typeName}::${cityName}::`)) {
          delete state.location_meta[key];
        }
      });
      removeStatusEntries('city', { business, type: typeName }, cityName);
      buildLocationsTree();
    }

    function updateLocation(business, typeName, cityName, oldName, newName, statusValue) {
      const types = state.tree[business];
      if (!types) {
        return false;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return false;
      }
      const locations = Array.isArray(cities[cityName]) ? cities[cityName] : [];
      const currentName = (oldName || '').trim();
      const trimmed = (newName || '').trim() || currentName;
      if (trimmed !== currentName && locations.includes(trimmed)) {
        popup(`Локация "${trimmed}" уже существует`);
        return false;
      }
      const index = locations.indexOf(currentName);
      if (index === -1) {
        return false;
      }
      locations[index] = trimmed;
      cities[cityName] = sortArrayAlphabetically(locations);
      if (trimmed !== currentName) {
        const oldKey = makeLocationMetaKey(business, typeName, cityName, currentName);
        const newKey = makeLocationMetaKey(business, typeName, cityName, trimmed);
        if (state.location_meta && state.location_meta[oldKey]) {
          state.location_meta[newKey] = state.location_meta[oldKey];
          delete state.location_meta[oldKey];
        }
        renameStatusEntries('location', { business, type: typeName, city: cityName }, currentName, trimmed);
      }
      setStatus(
        'location',
        [business, typeName, cityName, trimmed],
        statusValue || getStatus('location', business, typeName, cityName, trimmed),
      );
      buildLocationsTree();
      return true;
    }

    function addLocation(business, typeName, cityName) {
      const types = state.tree[business];
      if (!types) {
        return;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return;
      }
      const list = Array.isArray(cities[cityName]) ? cities[cityName] : [];
      const name = prompt('Название локации:');
      const trimmed = (name || '').trim();
      if (!trimmed) {
        return;
      }
      if (list.includes(trimmed)) {
        popup(`Локация "${trimmed}" уже существует`);
        return;
      }
      list.push(trimmed);
      cities[cityName] = sortArrayAlphabetically(list);
      setStatus('location', [business, typeName, cityName, trimmed], DEFAULT_LOCATION_STATUS);
      buildLocationsTree();
    }

    function removeLocation(business, typeName, cityName, locationName) {
      if (!confirmMultipleTimes(`Удалить локацию "${locationName}"?`)) {
        return;
      }
      const types = state.tree[business];
      if (!types) {
        return;
      }
      const cities = types[typeName];
      if (!cities || typeof cities !== 'object') {
        return;
      }
      const list = Array.isArray(cities[cityName]) ? cities[cityName] : [];
      const index = list.indexOf(locationName);
      if (index === -1) {
        return;
      }
      list.splice(index, 1);
      cities[cityName] = list;
      delete state.location_meta[makeLocationMetaKey(business, typeName, cityName, locationName)];
      removeStatusEntries('location', { business, type: typeName, city: cityName }, locationName);
      buildLocationsTree();
    }

    async function saveLocationsChanges() {
      try {
        const response = await fetch('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          locations: state,
          locations_iiko_server_sources: typeof options.serializeLocationsIikoServerSources === 'function'
            ? options.serializeLocationsIikoServerSources()
            : window.SettingsLocationsIikoRuntime?.serializeLocationsIikoServerSources?.() || [],
          locations_iiko_sync: typeof options.serializeLocationsIikoSyncSettings === 'function'
            ? options.serializeLocationsIikoSyncSettings()
            : window.SettingsLocationsIikoRuntime?.serializeLocationsIikoSyncSettings?.() || {},
        }),
      });
      const data = await response.json();
      if (data.success) {
          if (typeof options.markLocationsIikoServerSourcesSaved === 'function') {
            options.markLocationsIikoServerSourcesSaved();
          } else {
            window.SettingsLocationsIikoRuntime?.markLocationsIikoServerSourcesSaved?.();
          }
          if (typeof options.loadParameters === 'function') {
            await options.loadParameters();
          }
          popup('✅ Структура локаций сохранена');
        } else {
          popup('❌ Ошибка: ' + (data.error || 'неизвестная ошибка'));
        }
      } catch (error) {
        popup('❌ Ошибка сети: ' + error.message);
      }
    }

    return {
      getState,
      buildLocationsTree,
      addBusiness,
      saveLocationsChanges,
      setStatus,
      writeNodeMeta,
      makeCityMetaKey,
      makeLocationMetaKey,
      sortArrayAlphabetically,
      getDefaultLocationStatus: () => DEFAULT_LOCATION_STATUS,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsLocationsTreeRuntime) {
        return window.__settingsLocationsTreeRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        buildLocationsTree: runtime.buildLocationsTree,
        addBusiness: runtime.addBusiness,
        saveLocationsChanges: runtime.saveLocationsChanges,
      });
      window.__settingsLocationsTreeRuntime = runtime;
      return runtime;
    },
    getState() {
      return window.__settingsLocationsTreeRuntime?.getState();
    },
    buildLocationsTree() {
      return window.__settingsLocationsTreeRuntime?.buildLocationsTree();
    },
    addBusiness(...args) {
      return window.__settingsLocationsTreeRuntime?.addBusiness(...args);
    },
    saveLocationsChanges(...args) {
      return window.__settingsLocationsTreeRuntime?.saveLocationsChanges(...args);
    },
    setStatus(...args) {
      return window.__settingsLocationsTreeRuntime?.setStatus(...args);
    },
    writeNodeMeta(...args) {
      return window.__settingsLocationsTreeRuntime?.writeNodeMeta(...args);
    },
    makeCityMetaKey(...args) {
      return window.__settingsLocationsTreeRuntime?.makeCityMetaKey(...args);
    },
    makeLocationMetaKey(...args) {
      return window.__settingsLocationsTreeRuntime?.makeLocationMetaKey(...args);
    },
    sortArrayAlphabetically(...args) {
      return window.__settingsLocationsTreeRuntime?.sortArrayAlphabetically(...args);
    },
    getDefaultLocationStatus() {
      return window.__settingsLocationsTreeRuntime?.getDefaultLocationStatus();
    },
  };

  window.SettingsLocationsTreeRuntime = Object.freeze(api);
}());

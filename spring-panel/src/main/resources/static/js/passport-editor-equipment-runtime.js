(function () {
  if (window.PassportEditorEquipmentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassportData = typeof options.getPassportData === 'function' ? options.getPassportData : () => ({});
    const equipmentOptionSets = options.equipmentOptionSets && typeof options.equipmentOptionSets === 'object' ? options.equipmentOptionSets : { types: [], vendors: [], models: [], serials: [], statuses: [] };
    const equipmentCatalog = Array.isArray(options.equipmentCatalog) ? options.equipmentCatalog : [];
    const itConnectionChoices = Array.isArray(options.itConnectionChoices) ? options.itConnectionChoices : [];
    const equipmentContainer = options.equipmentContainer || document.getElementById('equipmentContainer');
    const equipmentEmptyText = options.equipmentEmptyText || document.getElementById('equipmentEmptyText');
    const addEquipmentBtn = options.addEquipmentBtn || document.getElementById('addEquipmentBtn');
    const networkEquipmentSummary = options.networkEquipmentSummary || document.getElementById('networkEquipmentSummary');
    const itEquipmentList = options.itEquipmentList || document.getElementById('itEquipmentList');
    const internalNetworkInput = options.internalNetworkInput || document.getElementById('internalNetworkInput');
    const escapeHtml = options.escapeHtml;
    const normalizeCatalogValue = options.normalizeCatalogValue;
    const addUniqueOption = options.addUniqueOption;
    const buildSelectOptions = options.buildSelectOptions;
    const buildDatalistOptions = options.buildDatalistOptions;
    const ensureSelectHasOption = options.ensureSelectHasOption;
    const initializeAutoResizeInputs = options.initializeAutoResizeInputs;
    const initializeAutoExpandTextareas = options.initializeAutoExpandTextareas;
    const showSaveMessage = options.showSaveMessage;
    const showPhotoPreview = options.showPhotoPreview;
    const refreshItBlockState = options.refreshItBlockState;

    const requiredFunctions = {
      escapeHtml, normalizeCatalogValue, addUniqueOption, buildSelectOptions, buildDatalistOptions,
      ensureSelectHasOption, initializeAutoResizeInputs, initializeAutoExpandTextareas,
      showSaveMessage, showPhotoPreview, refreshItBlockState,
    };
    Object.entries(requiredFunctions).forEach(([name, fn]) => {
      if (typeof fn !== 'function') {
        throw new Error('PassportEditorEquipmentRuntime requires ' + name);
      }
    });
    if (!equipmentContainer || !equipmentEmptyText || !addEquipmentBtn) {
      throw new Error('PassportEditorEquipmentRuntime requires equipment workspace DOM');
    }

    function resolvePassportData() {
      const value = getPassportData();
      return value && typeof value === 'object' ? value : {};
    }

    function ensureEquipmentKey(item, fallbackIndex) {
      if (!item || typeof item !== 'object') {
        return `index-${fallbackIndex}`;
      }
      if (item.id !== null && typeof item.id !== 'undefined') {
        return String(item.id);
      }
      if (item._internalKey) {
        return String(item._internalKey);
      }
      const uniqueKey = `temp-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
      try {
        Object.defineProperty(item, '_internalKey', {
          value: uniqueKey,
          enumerable: false,
          configurable: true,
        });
      } catch (error) {
        item._internalKey = uniqueKey;
      }
      return uniqueKey;
    }

    let highlightedEquipmentCard = null;
    let highlightedEquipmentTimeoutId = null;

    function highlightEquipmentCard(card) {
      if (!card) return;
      if (highlightedEquipmentCard && highlightedEquipmentCard !== card) {
        highlightedEquipmentCard.classList.remove('equipment-highlight');
      }
      card.classList.add('equipment-highlight');
      if (highlightedEquipmentTimeoutId) {
        clearTimeout(highlightedEquipmentTimeoutId);
      }
      highlightedEquipmentCard = card;
      highlightedEquipmentTimeoutId = window.setTimeout(() => {
        card.classList.remove('equipment-highlight');
        if (highlightedEquipmentCard === card) {
          highlightedEquipmentCard = null;
        }
      }, 2000);
    }

    function revealEquipmentCardByKey(key) {
      if (!equipmentContainer) return false;
      const normalizedKey = String(key || '');
      if (!normalizedKey) return false;
      const cards = Array.from(equipmentContainer.querySelectorAll('[data-equipment-key]'));
      const targetCard = cards.find((element) => element.dataset.equipmentKey === normalizedKey);
      if (!targetCard) return false;
      const detailsId = targetCard.dataset.detailsTarget;
      if (detailsId) {
        const detailsElement = document.getElementById(detailsId);
        if (detailsElement) {
          if (typeof bootstrap !== 'undefined' && bootstrap.Collapse) {
            bootstrap.Collapse.getOrCreateInstance(detailsElement, { toggle: false }).show();
          } else {
            const wasShown = detailsElement.classList.contains('show');
            detailsElement.classList.add('show');
            if (!wasShown) {
              detailsElement.dispatchEvent(new Event('shown.bs.collapse'));
            }
          }
        }
      }
      targetCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
      highlightEquipmentCard(targetCard);
      return true;
    }


    function enrichEquipmentOptionSetsFromEquipment(list) {
      if (!Array.isArray(list)) return;
      list.forEach((item) => {
        if (!item || typeof item !== 'object') return;
        addUniqueOption(equipmentOptionSets.types, item.equipment_type);
        addUniqueOption(equipmentOptionSets.vendors, item.vendor);
        addUniqueOption(equipmentOptionSets.models, item.model);
        addUniqueOption(equipmentOptionSets.serials, item.serial_number);
        addUniqueOption(equipmentOptionSets.statuses, item.status);
      });
    }

    function collectCatalogValues(field, filters = {}) {
      if (!Array.isArray(equipmentCatalog) || !equipmentCatalog.length) {
        return [];
      }
      const normalizedFilters = {
        equipment_type: normalizeCatalogValue(filters.equipment_type || filters.type),
        equipment_vendor: normalizeCatalogValue(filters.equipment_vendor || filters.vendor),
        equipment_model: normalizeCatalogValue(filters.equipment_model || filters.model),
        serial_number: normalizeCatalogValue(filters.serial_number || filters.serial),
      };
      const result = new Set();
      equipmentCatalog.forEach((item) => {
        if (!item || typeof item !== 'object') return;
        const typeValue = normalizeCatalogValue(item.equipment_type);
        const vendorValue = normalizeCatalogValue(item.equipment_vendor);
        const modelValue = normalizeCatalogValue(item.equipment_model);
        const serialValue = normalizeCatalogValue(item.serial_number);
        if (field !== 'equipment_type' && normalizedFilters.equipment_type && typeValue !== normalizedFilters.equipment_type) {
          return;
        }
        if (field !== 'equipment_vendor' && normalizedFilters.equipment_vendor && vendorValue !== normalizedFilters.equipment_vendor) {
          return;
        }
        if (field !== 'equipment_model' && normalizedFilters.equipment_model && modelValue !== normalizedFilters.equipment_model) {
          return;
        }
        if (field !== 'serial_number' && normalizedFilters.serial_number && serialValue !== normalizedFilters.serial_number) {
          return;
        }
        let valueToAdd = '';
        if (field === 'equipment_type') {
          valueToAdd = typeValue;
        } else if (field === 'equipment_vendor') {
          valueToAdd = vendorValue;
        } else if (field === 'equipment_model') {
          valueToAdd = modelValue;
        } else if (field === 'serial_number') {
          valueToAdd = serialValue;
        }
        if (valueToAdd) {
          result.add(valueToAdd);
        }
      });
      return Array.from(result).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
    }

    function getCombinedCatalogOptions(field, filters = {}) {
      const normalizedFilters = {
        equipment_type: normalizeCatalogValue(filters.equipment_type || filters.type),
        equipment_vendor: normalizeCatalogValue(filters.equipment_vendor || filters.vendor),
        equipment_model: normalizeCatalogValue(filters.equipment_model || filters.model),
        serial_number: normalizeCatalogValue(filters.serial_number || filters.serial),
      };
      let baseList = [];
      if (field === 'equipment_type') {
        baseList = equipmentOptionSets.types || [];
      } else if (field === 'equipment_vendor') {
        baseList = equipmentOptionSets.vendors || [];
      } else if (field === 'equipment_model') {
        baseList = equipmentOptionSets.models || [];
      } else if (field === 'serial_number') {
        baseList = equipmentOptionSets.serials || [];
      }
      const values = new Set();
      baseList.forEach((item) => {
        const normalized = normalizeCatalogValue(item);
        if (normalized) {
          values.add(normalized);
        }
      });
      collectCatalogValues(field, normalizedFilters).forEach((value) => {
        const normalized = normalizeCatalogValue(value);
        if (normalized) {
          values.add(normalized);
        }
      });
      return Array.from(values).sort((a, b) => a.localeCompare(b, undefined, { sensitivity: 'base' }));
    }

    function rebuildEquipmentSelect(select, options, currentValue) {
      if (!select) return;
      const normalizedCurrent = normalizeCatalogValue(currentValue || select.value);
      const fragment = document.createDocumentFragment();
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = 'Не выбрано';
      fragment.appendChild(placeholder);
      const seen = new Set();
      options.forEach((value) => {
        const normalized = normalizeCatalogValue(value);
        if (!normalized || seen.has(normalized)) return;
        const option = document.createElement('option');
        option.value = normalized;
        option.textContent = normalized;
        if (normalized === normalizedCurrent) {
          option.selected = true;
        }
        fragment.appendChild(option);
        seen.add(normalized);
      });
      if (normalizedCurrent && !seen.has(normalizedCurrent)) {
        const option = document.createElement('option');
        option.value = normalizedCurrent;
        option.textContent = normalizedCurrent;
        option.selected = true;
        fragment.appendChild(option);
      }
      select.innerHTML = '';
      select.appendChild(fragment);
      if (normalizedCurrent) {
        select.value = normalizedCurrent;
      } else {
        select.value = '';
      }
    }

    function rebuildEquipmentSerialOptions(datalist, options, currentValue) {
      if (!datalist) return;
      const normalizedCurrent = normalizeCatalogValue(currentValue);
      const fragment = document.createDocumentFragment();
      const seen = new Set();
      options.forEach((value) => {
        const normalized = normalizeCatalogValue(value);
        if (!normalized || seen.has(normalized)) return;
        const option = document.createElement('option');
        option.value = normalized;
        fragment.appendChild(option);
        seen.add(normalized);
      });
      if (normalizedCurrent && !seen.has(normalizedCurrent)) {
        const option = document.createElement('option');
        option.value = normalizedCurrent;
        fragment.appendChild(option);
      }
      datalist.innerHTML = '';
      datalist.appendChild(fragment);
    }

    function findCatalogMatches(filters = {}) {
      if (!Array.isArray(equipmentCatalog) || !equipmentCatalog.length) {
        return [];
      }
      const normalizedFilters = {
        equipment_type: normalizeCatalogValue(filters.equipment_type || filters.type),
        equipment_vendor: normalizeCatalogValue(filters.equipment_vendor || filters.vendor),
        equipment_model: normalizeCatalogValue(filters.equipment_model || filters.model),
        serial_number: normalizeCatalogValue(filters.serial_number || filters.serial),
      };
      return equipmentCatalog.filter((item) => {
        if (!item || typeof item !== 'object') return false;
        if (normalizedFilters.equipment_type && normalizeCatalogValue(item.equipment_type) !== normalizedFilters.equipment_type) {
          return false;
        }
        if (normalizedFilters.equipment_vendor && normalizeCatalogValue(item.equipment_vendor) !== normalizedFilters.equipment_vendor) {
          return false;
        }
        if (normalizedFilters.equipment_model && normalizeCatalogValue(item.equipment_model) !== normalizedFilters.equipment_model) {
          return false;
        }
        if (normalizedFilters.serial_number && normalizeCatalogValue(item.serial_number) !== normalizedFilters.serial_number) {
          return false;
        }
        return true;
      });
    }

    function applyCatalogMatchToCard(card, changedField) {
      if (!card) return;
      const typeSelect = card.querySelector('.equipment-type');
      const vendorSelect = card.querySelector('.equipment-vendor');
      const modelSelect = card.querySelector('.equipment-model');
      const serialInput = card.querySelector('.equipment-serial');
      if (!typeSelect || !vendorSelect || !modelSelect || !serialInput) return;
      const state = {
        equipment_type: normalizeCatalogValue(typeSelect.value),
        equipment_vendor: normalizeCatalogValue(vendorSelect.value),
        equipment_model: normalizeCatalogValue(modelSelect.value),
        serial_number: normalizeCatalogValue(serialInput.value),
      };
      const attempts = [];
      attempts.push(state);
      attempts.push({
        equipment_type: state.equipment_type,
        equipment_vendor: state.equipment_vendor,
        equipment_model: state.equipment_model,
      });
      attempts.push({ equipment_type: state.equipment_type, equipment_vendor: state.equipment_vendor });
      attempts.push({ equipment_type: state.equipment_type, equipment_model: state.equipment_model });
      attempts.push({ equipment_vendor: state.equipment_vendor, equipment_model: state.equipment_model });
      if (state.serial_number) {
        attempts.push({ serial_number: state.serial_number });
      }
      let matches = [];
      for (const candidate of attempts) {
        if (!candidate) continue;
        const hasValue = Object.values(candidate).some((value) => normalizeCatalogValue(value));
        if (!hasValue) continue;
        matches = findCatalogMatches(candidate);
        if (matches.length) {
          break;
        }
      }
      if (!matches.length) {
        card.dataset.catalogMatched = '';
        return;
      }

      const selectedFieldMap = {
        equipment_type: 'equipment_type',
        equipment_vendor: 'equipment_vendor',
        equipment_model: 'equipment_model',
        serial_number: 'serial_number',
      };

      let matchToApply = null;
      const selectedFieldKey = changedField ? selectedFieldMap[changedField] : null;
      const selectedValue = selectedFieldKey ? state[selectedFieldKey] : '';
      if (selectedFieldKey && selectedValue) {
        matchToApply = matches.find((match) => {
          const catalogValue = normalizeCatalogValue(match[selectedFieldKey]);
          return catalogValue && catalogValue === selectedValue;
        });
      }
      if (!matchToApply) {
        if (selectedFieldKey && selectedValue) {
          card.dataset.catalogMatched = '';
          return;
        }
        matchToApply = matches[0];
      }

      const fieldsToApply = [
        { select: typeSelect, key: 'equipment_type' },
        { select: vendorSelect, key: 'equipment_vendor' },
        { select: modelSelect, key: 'equipment_model' },
      ];
      fieldsToApply.forEach(({ select, key }) => {
        if (!select) return;
        const valueFromMatch = matchToApply ? normalizeCatalogValue(matchToApply[key]) : '';
        const shouldApplyValue = valueFromMatch || !state[key] || !matches.some((match) => normalizeCatalogValue(match[key]) === state[key]);
        if (shouldApplyValue) {
          if (valueFromMatch) {
            ensureSelectHasOption(select, valueFromMatch);
            select.value = valueFromMatch;
            state[key] = valueFromMatch;
          } else {
            select.value = '';
            state[key] = '';
          }
        }
      });

      const serialValueFromMatch = matchToApply ? normalizeCatalogValue(matchToApply.serial_number) : '';
      const shouldApplySerial =
        serialValueFromMatch ||
        !state.serial_number ||
        !matches.some((match) => normalizeCatalogValue(match.serial_number) === state.serial_number);
      if (shouldApplySerial) {
        serialInput.value = serialValueFromMatch || '';
        state.serial_number = serialValueFromMatch || '';
      }

      const nameInput = card.querySelector('.equipment-name');
      if (nameInput && !nameInput.value.trim() && matchToApply) {
        const pieces = [matchToApply.equipment_vendor, matchToApply.equipment_model]
          .map((value) => normalizeCatalogValue(value))
          .filter(Boolean);
        if (pieces.length) {
          nameInput.value = pieces.join(' ');
        }
      }

      card.dataset.catalogMatched = matches.length === 1 ? 'true' : 'partial';
    }

    function updateEquipmentCardOptions(card) {
      if (!card) return;
      const typeSelect = card.querySelector('.equipment-type');
      const vendorSelect = card.querySelector('.equipment-vendor');
      const modelSelect = card.querySelector('.equipment-model');
      const serialInput = card.querySelector('.equipment-serial');
      const serialDatalist = card.querySelector('.equipment-serial-options');
      if (!typeSelect || !vendorSelect || !modelSelect || !serialInput) return;
      const typeValue = normalizeCatalogValue(typeSelect.value);
      const vendorValue = normalizeCatalogValue(vendorSelect.value);
      const modelValue = normalizeCatalogValue(modelSelect.value);
      const serialValue = normalizeCatalogValue(serialInput.value);
      const typeOptions = getCombinedCatalogOptions('equipment_type', {
        equipment_vendor: vendorValue,
        equipment_model: modelValue,
        serial_number: serialValue,
      });
      rebuildEquipmentSelect(typeSelect, typeOptions, typeValue);
      const vendorOptions = getCombinedCatalogOptions('equipment_vendor', {
        equipment_type: typeValue,
        equipment_model: modelValue,
        serial_number: serialValue,
      });
      rebuildEquipmentSelect(vendorSelect, vendorOptions, vendorValue);
      const modelOptions = getCombinedCatalogOptions('equipment_model', {
        equipment_type: typeValue,
        equipment_vendor: vendorValue,
        serial_number: serialValue,
      });
      rebuildEquipmentSelect(modelSelect, modelOptions, modelValue);
      const serialOptions = getCombinedCatalogOptions('serial_number', {
        equipment_type: typeValue,
        equipment_vendor: vendorValue,
        equipment_model: modelValue,
      });
      rebuildEquipmentSerialOptions(serialDatalist, serialOptions, serialValue);
    }

    function getEquipmentIpPreviewValue() {
      if (internalNetworkInput) {
        const blocks = (internalNetworkInput.value || '').match(/\d{1,3}/g);
        if (blocks && blocks.length >= 3) {
          return `${blocks.slice(0, 3).join('.')}.xxx`;
        }
      }
      return '192.168.0.1';
    }

    function applyEquipmentIpPreview(card, previewValue) {
      if (!card) return;
      const ipInput = card.querySelector('.equipment-ip');
      const headerIpValue = card.querySelector('[data-role="equipment-header-ip"]');
      const preview = typeof previewValue === 'string' ? previewValue : getEquipmentIpPreviewValue();
      if (ipInput) {
        ipInput.placeholder = preview;
      }
      if (headerIpValue && ipInput && !ipInput.value.trim()) {
        headerIpValue.textContent = preview;
      }
    }

    function initializeEquipmentCard(card) {
      if (!card) return;
      const typeSelect = card.querySelector('.equipment-type');
      const vendorSelect = card.querySelector('.equipment-vendor');
      const modelSelect = card.querySelector('.equipment-model');
      const serialInput = card.querySelector('.equipment-serial');
      const nameInput = card.querySelector('.equipment-name');
      const ipInput = card.querySelector('.equipment-ip');
      const statusSelect = card.querySelector('.equipment-status')
      const headerTitle = card.querySelector('.equipment-header-title');
      const headerModelValue = card.querySelector('[data-role="equipment-header-model"]');
      const headerIpValue = card.querySelector('[data-role="equipment-header-ip"]');
      const headerStatusValue = card.querySelector('[data-role="equipment-header-status"]');
      if (!typeSelect || !vendorSelect || !modelSelect || !serialInput) return;
      initializeAutoResizeInputs(card);
      const headerToggle = card.querySelector('.equipment-header-toggle');
      const cardHeader = card.querySelector('.card-header');
      const collapseTargetSelector =
        (headerToggle && headerToggle.dataset.collapseTarget) ||
        (card.dataset.detailsTarget ? `#${card.dataset.detailsTarget}` : null);
      const collapseElement = collapseTargetSelector
        ? (card.querySelector(collapseTargetSelector) || document.querySelector(collapseTargetSelector))
        : null;
      const syncDetailsCollapseState = (expanded) => {
        if (headerToggle) {
          headerToggle.classList.toggle('collapsed', !expanded);
          headerToggle.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
        if (cardHeader) {
          cardHeader.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        }
      };
      if (collapseElement) {
        const initialExpanded = collapseElement.classList.contains('show');
        syncDetailsCollapseState(initialExpanded);
        const useBootstrapCollapse = typeof bootstrap !== 'undefined' && bootstrap.Collapse;
        let collapseInstance = null;
        const getCollapseInstance = () => {
          if (!useBootstrapCollapse) return null;
          if (!collapseInstance) {
            collapseInstance = bootstrap.Collapse.getOrCreateInstance(collapseElement, { toggle: false });
          }
          return collapseInstance;
        };
        if (useBootstrapCollapse && collapseElement.dataset.collapseEventsBound !== 'true') {
          collapseElement.addEventListener('shown.bs.collapse', () => syncDetailsCollapseState(true));
          collapseElement.addEventListener('hidden.bs.collapse', () => syncDetailsCollapseState(false));
          collapseElement.dataset.collapseEventsBound = 'true';
        }
        const getCurrentExpandedState = () => {
          if (headerToggle && headerToggle.hasAttribute('aria-expanded')) {
            return headerToggle.getAttribute('aria-expanded') === 'true';
          }
          return collapseElement.classList.contains('show');
        };
        const applyCollapseState = (shouldExpand) => {
          const expanded = getCurrentExpandedState();
          const targetState = typeof shouldExpand === 'boolean' ? shouldExpand : !expanded;
          if (targetState === expanded) return;
          if (useBootstrapCollapse) {
            const instance = getCollapseInstance();
            if (!instance) {
              collapseElement.classList.toggle('show', targetState);
              syncDetailsCollapseState(targetState);
              return;
            }
            if (targetState) {
              instance.show();
            } else {
              instance.hide();
            }
          } else {
            collapseElement.classList.toggle('show', targetState);
            syncDetailsCollapseState(targetState);
          }
        };
        const attachToggleHandler = (element) => {
          if (!element || element.dataset.collapseInitialized === 'true') return;
          element.addEventListener('click', (event) => {
            if (event.target.closest('.equipment-actions')) return;
            event.preventDefault();
            event.stopPropagation();
            applyCollapseState();
          });
          element.addEventListener('keydown', (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              applyCollapseState();
            }
          });
          element.dataset.collapseInitialized = 'true';
        };
        attachToggleHandler(headerToggle);
        if (cardHeader && cardHeader.dataset.collapseHeaderInitialized !== 'true') {
          cardHeader.addEventListener('click', (event) => {
            if (!headerToggle) return;
            if (event.target.closest('.equipment-actions')) return;
            applyCollapseState();
          });
          cardHeader.dataset.collapseHeaderInitialized = 'true';
        }
      }
      const updateHeaderSummary = () => {
        if (headerTitle && nameInput) {
          const value = nameInput.value.trim();
          headerTitle.textContent = value || 'Оборудование';
        }
        if (headerModelValue && modelSelect) {
          const option = modelSelect.options[modelSelect.selectedIndex];
          const text = option ? option.text : modelSelect.value;
          headerModelValue.textContent = (text || modelSelect.value || '—').trim() || '—';
        }
        if (ipInput) {
          const value = ipInput.value.trim();
          const previewValue = getEquipmentIpPreviewValue();
          ipInput.placeholder = previewValue;
          if (headerIpValue) {
            headerIpValue.textContent = value || previewValue;
          }
        }
        if (headerStatusValue && statusSelect) {
          const option = statusSelect.options[statusSelect.selectedIndex];
          const text = option ? option.text : statusSelect.value;
          headerStatusValue.textContent = (text || statusSelect.value || '—').trim() || '—';
        }
      };
      typeSelect.addEventListener('change', () => {
        applyCatalogMatchToCard(card, 'equipment_type');
        updateEquipmentCardOptions(card);
        updateHeaderSummary();
      });
      vendorSelect.addEventListener('change', () => {
        applyCatalogMatchToCard(card, 'equipment_vendor');
        updateEquipmentCardOptions(card);
        updateHeaderSummary();
      });
      modelSelect.addEventListener('change', () => {
        applyCatalogMatchToCard(card, 'equipment_model');
        updateEquipmentCardOptions(card);
        updateHeaderSummary();
      });
      serialInput.addEventListener('change', () => {
        applyCatalogMatchToCard(card, 'serial_number');
        updateEquipmentCardOptions(card);
        updateHeaderSummary();
      });
      serialInput.addEventListener('input', () => {
        updateEquipmentCardOptions(card);
        updateHeaderSummary();
      });
      if (nameInput) {
        nameInput.addEventListener('input', updateHeaderSummary);
        nameInput.addEventListener('change', updateHeaderSummary);
      }
      if (ipInput) {
        ipInput.addEventListener('input', updateHeaderSummary);
        ipInput.addEventListener('change', updateHeaderSummary);
      }
      if (statusSelect) {
        statusSelect.addEventListener('change', () => {
          addUniqueOption(equipmentOptionSets.statuses, statusSelect.value);
          updateHeaderSummary();
        });
      }
      updateEquipmentCardOptions(card);
      applyCatalogMatchToCard(card);
      updateEquipmentCardOptions(card);
      updateHeaderSummary();
    }


    function updateItEquipmentList(equipmentList) {
      if (!itEquipmentList) return;
      const list = Array.isArray(equipmentList) ? equipmentList.filter(Boolean) : [];
      if (!list.length) {
        itEquipmentList.innerHTML = '<div class="it-equipment-empty">Оборудование ещё не добавлено.</div>';
        return;
      }
      itEquipmentList.innerHTML = '';
      list.forEach((item) => {
        const row = document.createElement('div');
        row.className = 'it-equipment-item';
        const name = escapeHtml((item && item.name) || '—');
        const ip = escapeHtml((item && item.ip_address) || '—');
        row.innerHTML = `<span class="it-equipment-name">${name}</span><span class="it-equipment-ip">${ip}</span>`;
        itEquipmentList.appendChild(row);
      });
      refreshItBlockState(resolvePassportData());
    }

    function updateNetworkEquipmentSummary(equipmentList) {
      if (!networkEquipmentSummary) return;
      const list = Array.isArray(equipmentList) ? equipmentList : [];
      const items = list
        .map((item, index) => {
          if (!item) return null;
          const name = typeof item.name === 'string' ? item.name.trim() : '';
          const ip = typeof item.ip_address === 'string' ? item.ip_address.trim() : '';
          if (!name && !ip) return null;
          const key = ensureEquipmentKey(item, index);
          const keyAttr = escapeHtml(String(key));
          const nameHtml = escapeHtml(name || '—');
          const ipHtml = escapeHtml(ip || '—');
          return `<li><button type="button" class="network-equipment-entry" data-equipment-key="${keyAttr}"><span class="network-equipment-name">${nameHtml}</span><span class="network-equipment-separator"></span><span class="network-equipment-ip">${ipHtml}</span></button></li>`;
        })
        .filter(Boolean);
      if (!items.length) {
        networkEquipmentSummary.innerHTML = '<div class="network-equipment-empty text-muted">Оборудование ещё не добавлено.</div>';
        return;
      }
      networkEquipmentSummary.innerHTML = `<ul class="list-unstyled mb-0 network-equipment-list">${items.join('')}</ul>`;
    }


    function refreshEquipmentIpPreviews() {
      if (!equipmentContainer) return;
      const previewValue = getEquipmentIpPreviewValue();
      equipmentContainer.querySelectorAll('.equipment-card').forEach((card) => {
        applyEquipmentIpPreview(card, previewValue);
      });
    }

    function renderEquipment(equipmentList) {
      const list = Array.isArray(equipmentList) ? equipmentList : [];
      resolvePassportData().equipment = list;
      enrichEquipmentOptionSetsFromEquipment(list);
      equipmentContainer.innerHTML = '';
      if (!list.length) {
        equipmentEmptyText.classList.remove('d-none');
      } else {
        equipmentEmptyText.classList.add('d-none');
      }
      updateItEquipmentList(list);
      list.forEach((item, index) => {
        const isNew = item.id === null || typeof item.id === 'undefined';
        const equipmentKey = ensureEquipmentKey(item, index);
        const card = document.createElement('div');
        card.className = 'card mb-4 equipment-card ops-section-card';
        card.dataset.id = isNew ? `new-${index}` : item.id;
        card.dataset.equipmentKey = equipmentKey;
        const typeValues = getCombinedCatalogOptions('equipment_type', {
          equipment_vendor: item.vendor,
          equipment_model: item.model,
          serial_number: item.serial_number,
        });
        const vendorValues = getCombinedCatalogOptions('equipment_vendor', {
          equipment_type: item.equipment_type,
          equipment_model: item.model,
          serial_number: item.serial_number,
        });
        const modelValues = getCombinedCatalogOptions('equipment_model', {
          equipment_type: item.equipment_type,
          equipment_vendor: item.vendor,
          serial_number: item.serial_number,
        });
        const typeOptions = buildSelectOptions(typeValues, item.equipment_type || '');
        const vendorOptions = buildSelectOptions(vendorValues, item.vendor || '');
        const modelOptions = buildSelectOptions(modelValues, item.model || '');
        const statusOptions = buildSelectOptions(equipmentOptionSets.statuses, item.status || '');
        const connectionOptions = buildSelectOptions(itConnectionChoices, item.connection_type || '');
        const collapseId = `equipmentPhotos-${equipmentKey}`;
        const detailsCollapseId = `equipmentDetails-${equipmentKey}`;
        const hasPhotos = Array.isArray(item.photos) && item.photos.length > 0;
        const collapseShowClass = hasPhotos ? ' show' : '';
        const collapseButtonClass = hasPhotos ? '' : ' collapsed';
        const collapseExpanded = hasPhotos ? 'true' : 'false';
        const detailsShowClass = isNew ? ' show' : '';
        const detailsToggleClass = isNew ? '' : ' collapsed';
        const detailsExpanded = isNew ? 'true' : 'false';
        const headerName = escapeHtml(item.name || 'Оборудование');
        const headerModel = escapeHtml(item.model || '—');
        const ipPreview = getEquipmentIpPreviewValue();
        const headerIp = escapeHtml(item.ip_address || ipPreview);
        const headerStatus = escapeHtml(item.status || '—');
        const serialOptionsId = `equipmentSerialOptions-${equipmentKey}`;
        const serialOptions = buildDatalistOptions(equipmentOptionSets.serials, item.serial_number || '');
        card.innerHTML = `
          <div class="card-header d-flex justify-content-between align-items-center" data-collapse-target="#${detailsCollapseId}">
            <div class="equipment-header-toggle${detailsToggleClass}" role="button" tabindex="0" data-collapse-target="#${detailsCollapseId}" aria-expanded="${detailsExpanded}" aria-controls="${detailsCollapseId}">
              <div class="equipment-header-summary">
                <div class="equipment-header-title">${headerName}</div>
                <div class="equipment-header-meta">
                  <span><strong>Модель:</strong> <span data-role="equipment-header-model">${headerModel}</span></span>
                  <span><strong>IP-адрес:</strong> <span data-role="equipment-header-ip">${headerIp}</span></span>
                  <span><strong>Статус:</strong> <span data-role="equipment-header-status">${headerStatus}</span></span>
                </div>
              </div>
              <span class="equipment-header-icon">▼</span>
            </div>
            <div class="equipment-actions">
              <button class="btn btn-sm btn-primary" data-action="save-equipment">💾 Сохранить</button>
              ${isNew ? '<button class="btn btn-sm btn-outline-secondary" data-action="cancel-equipment">Отменить</button>' : '<button class="btn btn-sm btn-outline-danger" data-action="delete-equipment">Удалить</button>'}
            </div>
          </div>
          <div class="collapse${detailsShowClass}" id="${detailsCollapseId}">
            <div class="card-body">
            <div class="equipment-fields-grid">
              <div class="equipment-field">
                <label class="form-label">Тип</label>
                <select class="form-select form-select-sm equipment-type">${typeOptions}</select>
              </div>
              <div class="equipment-field">
                <label class="form-label">Производитель</label>
                <select class="form-select form-select-sm equipment-vendor">${vendorOptions}</select>
              </div>
              <div class="equipment-field">
                <label class="form-label">Наименование</label>
                <input type="text" class="form-control form-control-sm equipment-name" value="${escapeHtml(item.name || '')}" />
              </div>
              <div class="equipment-field">
                <label class="form-label">Модель</label>
                <select class="form-select form-select-sm equipment-model">${modelOptions}</select>
              </div>
              <div class="equipment-field">
                <label class="form-label">Серийный номер</label>
                <input type="text" class="form-control form-control-sm equipment-serial" value="${escapeHtml(item.serial_number || '')}" list="${serialOptionsId}" placeholder="SN..." />
                <datalist id="${serialOptionsId}" class="equipment-serial-options">${serialOptions}</datalist>
              </div>
              <div class="equipment-field">
                <label class="form-label">Статус</label>
                <select class="form-select form-select-sm equipment-status">${statusOptions}</select>
              </div>
              <div class="equipment-field">
                <label class="form-label">IP-адрес</label>
                <input type="text" class="form-control form-control-sm equipment-ip" value="${escapeHtml(item.ip_address || '')}" placeholder="${escapeHtml(ipPreview)}" />
              </div>
              <div class="equipment-field">
                <label class="form-label">Подключение</label>
                <select class="form-select form-select-sm equipment-connection-type">${connectionOptions}</select>
              </div>
              <div class="equipment-field">
                <label class="form-label">ID подключения</label>
                <input type="text" class="form-control form-control-sm equipment-connection-id" value="${escapeHtml(item.connection_id || '')}" placeholder="введите id" />
              </div>
              <div class="equipment-field">
                <label class="form-label">Пароль подключения</label>
                <input type="text" class="form-control form-control-sm equipment-connection-password" value="${escapeHtml(item.connection_password || '')}" placeholder="введите пароль" />
              </div>
              <div class="equipment-field equipment-field-placeholder" aria-hidden="true"></div>
              <div class="equipment-field equipment-field-placeholder" aria-hidden="true"></div>
            </div>
            <div class="mt-3">
              <label class="form-label">Описание</label>
              <textarea class="form-control form-control-sm equipment-description">${escapeHtml(item.description || '')}</textarea>
            </div>
            ${isNew
              ? '<div class="alert alert-info mt-3 mb-0">Сохраните запись, чтобы добавить фотографии оборудования.</div>'
              : `
            <hr>
            <div class="mt-3">
              <button class="btn btn-sm btn-outline-secondary equipment-photos-toggle${collapseButtonClass}" type="button" data-bs-toggle="collapse" data-bs-target="#${collapseId}" aria-expanded="${collapseExpanded}" aria-controls="${collapseId}">
                Общие фотографии оборудования
              </button>
              <div class="collapse${collapseShowClass}" id="${collapseId}">
                <form class="row g-2 align-items-end equipment-photo-form mt-3" data-id="${item.id}">
                  <div class="col-md-6">
                    <label class="form-label">Подпись</label>
                    <input type="text" class="form-control form-control-sm" name="caption" placeholder="Описание" />
                  </div>
                  <div class="col-md-4">
                    <label class="form-label">Файл</label>
                    <input type="file" class="form-control form-control-sm" name="file" accept="image/*" required />
                  </div>
                  <div class="col-md-2">
                    <button class="btn btn-outline-primary btn-sm w-100" type="submit">Добавить</button>
                  </div>
                </form>
                <div class="row g-3 equipment-photos" data-id="${item.id}"></div>
                <div class="text-muted small" data-empty-photos="${item.id}">Фотографии ещё не добавлены.</div>
            </div>
          `}
            </div>
          </div>
        `;
        equipmentContainer.appendChild(card);
        card.dataset.detailsTarget = detailsCollapseId;
        card.id = `equipment-card-${equipmentKey}`;
        initializeAutoExpandTextareas(card);
        initializeEquipmentCard(card);
        applyEquipmentIpPreview(card);
        if (!isNew) {
          renderEquipmentPhotos(item.id, item.photos || []);
        }
      });
      updateNetworkEquipmentSummary(list);
      refreshEquipmentIpPreviews();
    }

    function renderEquipmentPhotos(equipmentId, photos) {
      const container = equipmentContainer.querySelector(`.equipment-photos[data-id="${equipmentId}"]`);
      const emptyText = equipmentContainer.querySelector(`[data-empty-photos="${equipmentId}"]`);
      if (!container) return;
      container.innerHTML = '';
      const list = Array.isArray(photos) ? photos : [];
      if (!list.length) {
        if (emptyText) {
          emptyText.classList.remove('d-none');
        }
        return;
      }
      if (emptyText) {
        emptyText.classList.add('d-none');
      }
      list.forEach((photo) => {
        const col = document.createElement('div');
        col.className = 'col-md-4';
        const captionRaw = photo.caption || '';
        const captionData = encodeURIComponent(captionRaw);
        col.innerHTML = `
          <div class="card equipment-photo h-100" data-id="${photo.id}" data-equipment="${equipmentId}">
            <img src="${escapeHtml(photo.url)}" class="card-img-top passport-photo" alt="Фото оборудования" data-photo-url="${escapeHtml(photo.url)}" data-photo-caption="${captionData}" />
            <div class="card-body">
              <div class="mb-2">
                <label class="form-label">Подпись</label>
                <input type="text" class="form-control form-control-sm equipment-photo-caption" value="${escapeHtml(photo.caption || '')}" />
              </div>
              <div class="d-flex gap-2 flex-wrap">
                <button class="btn btn-sm btn-primary" data-action="save-equipment-photo">Сохранить</button>
                <button class="btn btn-sm btn-outline-danger" data-action="delete-equipment-photo">Удалить</button>
                <a class="btn btn-sm btn-outline-secondary" href="${escapeHtml(photo.url)}" download>Скачать</a>
              </div>
            </div>
          </div>
        `;
        container.appendChild(col);
      });
      initializeAutoResizeInputs(container);
    }

    equipmentContainer.addEventListener('click', async (event) => {
      const previewTarget = event.target.closest('.passport-photo');
      if (previewTarget) {
        showPhotoPreview(previewTarget.dataset.photoUrl, previewTarget.dataset.photoCaption || '');
        return;
      }
      const card = event.target.closest('.equipment-card');
      const photosToggle = event.target.closest('.equipment-photos-toggle');
      if (photosToggle) {
        event.preventDefault();
        event.stopPropagation();
        const targetSelector = photosToggle.getAttribute('data-bs-target');
        const collapseElement = targetSelector
          ? (card ? card.querySelector(targetSelector) : document.querySelector(targetSelector))
          : null;
        if (collapseElement) {
          const isShown = collapseElement.classList.contains('show');
          if (typeof bootstrap !== 'undefined' && bootstrap.Collapse) {
            const collapseInstance = bootstrap.Collapse.getOrCreateInstance(collapseElement, {
              toggle: false,
            });
            if (isShown) {
              collapseInstance.hide();
            } else {
              collapseInstance.show();
            }
          } else {
            collapseElement.classList.toggle('show');
          }
          photosToggle.classList.toggle('collapsed', isShown);
          photosToggle.setAttribute('aria-expanded', (!isShown).toString());
        }
        return;
      }
      if (!card) return;
      const button = event.target.closest('button[data-action]');
      if (!button) return;
      const action = button.dataset.action;
      if (action === 'save-equipment') {
        handleEquipmentSave(card);
      } else if (action === 'delete-equipment') {
        handleEquipmentDelete(card);
      } else if (action === 'cancel-equipment') {
        handleEquipmentCancel(card);
      } else if (action === 'save-equipment-photo') {
        handleEquipmentPhotoSave(button.closest('.equipment-photo'));
      } else if (action === 'delete-equipment-photo') {
        handleEquipmentPhotoDelete(button.closest('.equipment-photo'));
      }
    });

    equipmentContainer.addEventListener('submit', async (event) => {
      if (!event.target.classList.contains('equipment-photo-form')) return;
      event.preventDefault();
      if (resolvePassportData().is_new) {
        showSaveMessage('error', 'Сначала сохраните паспорт.');
        return;
      }
      const equipmentId = event.target.dataset.id;
      const formData = new FormData(event.target);
      try {
        const response = await fetch(`/api/object_passports/equipment/${equipmentId}/photos`, {
          method: 'POST',
          body: formData,
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка при добавлении фото оборудования');
        }
        resolvePassportData().equipment = data.equipment || resolvePassportData().equipment;
        renderEquipment(resolvePassportData().equipment);
        showSaveMessage('success', 'Фото оборудования добавлено.');
      } catch (error) {
        console.error(error);
        showSaveMessage('error', error.message || 'Не удалось добавить фото оборудования.');
      }
    });
    function handleEquipmentCancel(card) {
      const id = card.dataset.id;
      resolvePassportData().equipment = resolvePassportData().equipment.filter((item, index) => `new-${index}` !== id);
      renderEquipment(resolvePassportData().equipment);
    }

    async function handleEquipmentSave(card) {
      const id = card.dataset.id;
      const payload = {
        equipment_type: card.querySelector('.equipment-type').value.trim(),
        vendor: card.querySelector('.equipment-vendor').value.trim(),
        name: card.querySelector('.equipment-name').value.trim(),
        model: card.querySelector('.equipment-model').value.trim(),
        serial_number: card.querySelector('.equipment-serial').value.trim(),
        status: card.querySelector('.equipment-status').value.trim(),
        ip_address: card.querySelector('.equipment-ip').value.trim(),
        connection_type: card.querySelector('.equipment-connection-type').value.trim(),
        connection_id: card.querySelector('.equipment-connection-id').value.trim(),
        connection_password: card.querySelector('.equipment-connection-password').value.trim(),
        description: card.querySelector('.equipment-description').value.trim(),
      };
      if (resolvePassportData().is_new) {
        showSaveMessage('error', 'Сначала сохраните паспорт.');
        return;
      }
      try {
        let response;
        if (id && id.startsWith('new-')) {
          response = await fetch(`/api/object_passports/${resolvePassportData().id}/equipment`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        } else {
          response = await fetch(`/api/object_passports/equipment/${id}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        }
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось сохранить оборудование');
        }
        resolvePassportData().equipment = data.equipment || resolvePassportData().equipment;
        renderEquipment(resolvePassportData().equipment);
        showSaveMessage('success', 'Данные оборудования сохранены.');
      } catch (error) {
        console.error(error);
        showSaveMessage('error', error.message || 'Ошибка при сохранении оборудования.');
      }
    }

    async function handleEquipmentDelete(card) {
      const id = card.dataset.id;
      if (id && id.startsWith('new-')) {
        handleEquipmentCancel(card);
        return;
      }
      if (!confirm('Удалить оборудование?')) return;
      try {
        const response = await fetch(`/api/object_passports/equipment/${id}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось удалить оборудование');
        }
        resolvePassportData().equipment = data.equipment || resolvePassportData().equipment.filter((item) => item.id !== id);
        renderEquipment(resolvePassportData().equipment);
        showSaveMessage('success', 'Оборудование удалено.');
      } catch (error) {
        console.error(error);
        showSaveMessage('error', error.message || 'Ошибка при удалении оборудования.');
      }
    }

    async function handleEquipmentPhotoSave(card) {
      if (!card) return;
      const photoId = card.dataset.id;
      const caption = card.querySelector('.equipment-photo-caption').value;
      try {
        const response = await fetch(`/api/object_passports/equipment/photos/${photoId}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ caption }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось сохранить фото оборудования');
        }
        resolvePassportData().equipment = data.equipment || resolvePassportData().equipment;
        renderEquipment(resolvePassportData().equipment);
        showSaveMessage('success', 'Фото оборудования обновлено.');
      } catch (error) {
        console.error(error);
        showSaveMessage('error', error.message || 'Ошибка при обновлении фото оборудования.');
      }
    }

    async function handleEquipmentPhotoDelete(card) {
      if (!card) return;
      if (!confirm('Удалить фото оборудования?')) return;
      const photoId = card.dataset.id;
      try {
        const response = await fetch(`/api/object_passports/equipment/photos/${photoId}`, { method: 'DELETE' });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось удалить фото оборудования');
        }
        resolvePassportData().equipment = data.equipment || resolvePassportData().equipment;
        renderEquipment(resolvePassportData().equipment);
        showSaveMessage('success', 'Фото оборудования удалено.');
      } catch (error) {
        console.error(error);
        showSaveMessage('error', error.message || 'Ошибка при удалении фото оборудования.');
      }
    }


    if (networkEquipmentSummary) {
      networkEquipmentSummary.addEventListener('click', (event) => {
        const button = event.target.closest('.network-equipment-entry[data-equipment-key]');
        if (!button || !networkEquipmentSummary.contains(button)) return;
        event.preventDefault();
        event.stopPropagation();
        revealEquipmentCardByKey(button.dataset.equipmentKey);
      });
    }

    addEquipmentBtn.addEventListener('click', () => {
      if (resolvePassportData().is_new) {
        showSaveMessage('error', 'Сначала сохраните паспорт.');
        return;
      }
      resolvePassportData().equipment = Array.isArray(resolvePassportData().equipment) ? resolvePassportData().equipment : [];
      resolvePassportData().equipment.push({
        id: null,
        equipment_type: '',
        vendor: '',
        name: '',
        model: '',
        serial_number: '',
        status: '',
        ip_address: '',
        connection_type: '',
        connection_id: '',
        connection_password: '',
        description: '',
        photos: [],
      });
      renderEquipment(resolvePassportData().equipment);
    });


    return Object.freeze({
      renderEquipment,
      refreshEquipmentIpPreviews,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportEditorEquipmentRuntime = Object.freeze({
    mount,
  });
}());

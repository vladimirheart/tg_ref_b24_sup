(() => {
  'use strict';

  if (window.EquipmentConfigurationEditorRuntime) return;

  const metadataCache = new Map();

  function clean(value) {
    return value == null ? '' : String(value).trim();
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function isRecord(value) {
    return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
  }

  function copyRecord(value) {
    return isRecord(value) ? { ...value } : {};
  }

  function hasOwn(value, key) {
    return Object.prototype.hasOwnProperty.call(value || {}, key);
  }

  function sameValue(left, right) {
    return JSON.stringify(left) === JSON.stringify(right);
  }

  async function requestItems(url) {
    const response = await fetch(url, { method: 'GET', credentials: 'same-origin', headers: { Accept: 'application/json' } });
    const payload = await response.json();
    if (!response.ok || !payload || payload.success === false) {
      throw new Error(clean(payload && payload.error) || ('HTTP ' + response.status));
    }
    return Array.isArray(payload.items) ? payload.items : [];
  }

  function loadMetadata(equipmentType) {
    const type = clean(equipmentType);
    if (!type) return Promise.resolve({ attributes: [], profiles: [] });
    if (metadataCache.has(type)) return metadataCache.get(type);
    const query = '?equipmentType=' + encodeURIComponent(type);
    const promise = Promise.all([
      requestItems('/api/settings/it-equipment/attributes' + query),
      requestItems('/api/settings/it-equipment/profiles' + query)
    ]).then((responses) => ({
      attributes: responses[0]
        .filter((item) => item && item.active !== false)
        .sort((a, b) => Number(a.sort_order || 0) - Number(b.sort_order || 0) || Number(a.id || 0) - Number(b.id || 0)),
      profiles: responses[1].filter((item) => item && item.active !== false)
    })).catch((error) => {
      metadataCache.delete(type);
      throw error;
    });
    metadataCache.set(type, promise);
    return promise;
  }

  function applicableProfiles(profiles, catalogId) {
    const modelId = Number(catalogId);
    return (profiles || []).filter((profile) => {
      const linked = Number(profile && profile.catalog_id);
      return !Number.isFinite(linked) || linked <= 0 || (Number.isFinite(modelId) && modelId > 0 && linked === modelId);
    });
  }

  function selectedProfile(profiles, profileId) {
    const id = Number(profileId);
    if (!Number.isFinite(id) || id <= 0) return null;
    return (profiles || []).find((profile) => Number(profile && profile.id) === id) || null;
  }

  function profileDefaults(profile) {
    return profile && isRecord(profile.values) ? profile.values : {};
  }

  function effectiveValue(attributeKey, defaults, overrides) {
    if (hasOwn(overrides, attributeKey)) return overrides[attributeKey];
    if (hasOwn(defaults, attributeKey)) return defaults[attributeKey];
    return null;
  }

  function normalizeScalar(attribute, rawValue) {
    const raw = rawValue == null ? '' : String(rawValue).trim();
    if (!raw) return { empty: true, value: null };
    const type = clean(attribute && attribute.value_type);
    if (type === 'boolean') return { empty: false, value: raw === 'true' };
    if (type === 'integer') {
      const value = Number(raw);
      return Number.isInteger(value) ? { empty: false, value } : { empty: true, value: null };
    }
    if (type === 'decimal') {
      const value = Number(raw);
      return Number.isFinite(value) ? { empty: false, value } : { empty: true, value: null };
    }
    return { empty: false, value: raw };
  }

  function pruneOverrides(overrides, defaults) {
    const result = copyRecord(overrides);
    Object.keys(result).forEach((key) => {
      if (result[key] === null && !hasOwn(defaults, key)) {
        delete result[key];
      } else if (hasOwn(defaults, key) && sameValue(result[key], defaults[key])) {
        delete result[key];
      }
    });
    return result;
  }

  function valueText(value) {
    return value == null ? '' : String(value);
  }

  function renderControl(attribute, value, inherited, overridden, disabled) {
    const key = clean(attribute && attribute.attribute_key);
    const type = clean(attribute && attribute.value_type) || 'text';
    const raw = valueText(value);
    const disabledAttr = disabled ? ' disabled' : '';
    let control = '';
    if (type === 'boolean') {
      control = '<select class="form-select form-select-sm" data-equipment-config-key="' + escapeHtml(key) + '" data-equipment-config-type="boolean"' + disabledAttr + '>' +
        '<option value=""' + (raw === '' ? ' selected' : '') + '>Без значения</option>' +
        '<option value="true"' + (raw === 'true' ? ' selected' : '') + '>Да</option>' +
        '<option value="false"' + (raw === 'false' ? ' selected' : '') + '>Нет</option></select>';
    } else if (type === 'enum') {
      const options = Array.isArray(attribute.options) ? attribute.options.map((item) => String(item)) : [];
      const effectiveOptions = raw && !options.includes(raw) ? [raw, ...options] : options;
      control = '<select class="form-select form-select-sm" data-equipment-config-key="' + escapeHtml(key) + '" data-equipment-config-type="enum"' + disabledAttr + '>' +
        '<option value=""' + (raw === '' ? ' selected' : '') + '>Без значения</option>' +
        effectiveOptions.map((option) => '<option value="' + escapeHtml(option) + '"' + (option === raw ? ' selected' : '') + '>' + escapeHtml(option) + '</option>').join('') + '</select>';
    } else if (type === 'integer' || type === 'decimal') {
      control = '<input class="form-control form-control-sm" type="number" ' + (type === 'integer' ? 'step="1"' : 'step="any"') +
        ' data-equipment-config-key="' + escapeHtml(key) + '" data-equipment-config-type="' + escapeHtml(type) + '" value="' + escapeHtml(raw) + '"' + disabledAttr + '>';
    } else {
      control = '<input class="form-control form-control-sm" type="text" data-equipment-config-key="' + escapeHtml(key) +
        '" data-equipment-config-type="text" value="' + escapeHtml(raw) + '"' + disabledAttr + '>';
    }
    const unit = clean(attribute && attribute.unit) ? ' <span class="text-muted">(' + escapeHtml(attribute.unit) + ')</span>' : '';
    const required = attribute && attribute.required ? '<span class="badge text-bg-light ms-1">обяз.</span>' : '';
    const origin = overridden
      ? '<span class="badge text-bg-warning-subtle text-warning-emphasis ms-1">override</span>'
      : (inherited ? '<span class="badge text-bg-info-subtle text-info-emphasis ms-1">из профиля</span>' : '');
    const help = clean(attribute && attribute.help_text) ? '<div class="form-text">' + escapeHtml(attribute.help_text) + '</div>' : '';
    return '<div class="col-md-6"><label class="form-label mb-1">' + escapeHtml(attribute.label) + unit + required + origin + '</label>' + control + help + '</div>';
  }

  function mount(options) {
    const host = options && options.host;
    if (!host) return;
    const equipmentType = clean(options.equipmentType);
    const catalogId = Number(options.catalogId);
    const disabled = Boolean(options.disabled);
    const onChange = typeof options.onChange === 'function' ? options.onChange : () => {};
    const sourceItem = options.item && typeof options.item === 'object' ? options.item : {};
    let current = {
      configuration_profile_id: Number(sourceItem.configuration_profile_id) > 0 ? Number(sourceItem.configuration_profile_id) : null,
      configuration: copyRecord(sourceItem.configuration)
    };
    let renderRevision = 0;

    if (!equipmentType) {
      host.innerHTML = '<div class="text-muted small">Выберите тип оборудования, чтобы настроить конфигурацию.</div>';
      return;
    }

    host.innerHTML = '<div class="text-muted small">Загрузка конфигурации…</div>';
    const revision = ++renderRevision;
    loadMetadata(equipmentType).then((metadata) => {
      if (!host.isConnected || revision !== renderRevision) return;
      render(metadata);
    }).catch((error) => {
      if (!host.isConnected || revision !== renderRevision) return;
      host.innerHTML = '<div class="alert alert-warning py-2 px-3 mb-0 small">Не удалось загрузить конфигурацию: ' + escapeHtml(error && error.message ? error.message : error) + '</div>';
    });

    function emit() {
      onChange({
        configuration_profile_id: current.configuration_profile_id,
        configuration: copyRecord(current.configuration)
      });
    }

    function render(metadata) {
      const profiles = applicableProfiles(metadata.profiles, catalogId);
      let profile = selectedProfile(profiles, current.configuration_profile_id);
      if (current.configuration_profile_id && !profile) {
        current.configuration_profile_id = null;
        current.configuration = pruneOverrides(current.configuration, {});
        emit();
      }
      profile = selectedProfile(profiles, current.configuration_profile_id);
      const defaults = profileDefaults(profile);
      current.configuration = pruneOverrides(current.configuration, defaults);
      const profileOptions = profiles.map((item) => {
        const linked = Number(item && item.catalog_id);
        const modelSuffix = Number.isFinite(linked) && linked > 0
          ? ' · ' + [clean(item.equipment_vendor), clean(item.equipment_model)].filter(Boolean).join(' ')
          : ' · для типа';
        return '<option value="' + Number(item.id) + '"' + (Number(item.id) === Number(current.configuration_profile_id) ? ' selected' : '') + '>' + escapeHtml(item.profile_name + modelSuffix) + '</option>';
      }).join('');
      const groups = new Map();
      metadata.attributes.forEach((attribute) => {
        const section = clean(attribute.section_name) || 'Основное';
        if (!groups.has(section)) groups.set(section, []);
        groups.get(section).push(attribute);
      });
      const controls = Array.from(groups.entries()).map((entry) => {
        const fields = entry[1].map((attribute) => {
          const key = clean(attribute.attribute_key);
          const overridden = hasOwn(current.configuration, key);
          const inherited = !overridden && hasOwn(defaults, key);
          const value = effectiveValue(key, defaults, current.configuration);
          return renderControl(attribute, value, inherited, overridden, disabled);
        }).join('');
        return '<div class="col-12"><div class="small fw-semibold text-uppercase text-muted mt-2">' + escapeHtml(entry[0]) + '</div></div>' + fields;
      }).join('');
      const overrideCount = Object.keys(current.configuration).length;
      const profileName = profile ? clean(profile.profile_name) : 'Без профиля';
      host.innerHTML = '<div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-2"><div><strong class="small">Конфигурация экземпляра</strong><div class="text-muted small">Профиль: ' + escapeHtml(profileName) + '</div></div><span class="badge text-bg-light">override: ' + overrideCount + '</span></div>' +
        '<label class="form-label small mb-2 w-100">Профиль комплектации<select class="form-select form-select-sm mt-1" data-equipment-config-profile' + (disabled ? ' disabled' : '') + '><option value="">Без профиля</option>' + profileOptions + '</select></label>' +
        (controls ? '<div class="row g-2">' + controls + '</div>' : '<div class="text-muted small">Для этого типа активные характеристики не заданы.</div>');

      const profileSelect = host.querySelector('[data-equipment-config-profile]');
      profileSelect?.addEventListener('change', () => {
        const nextId = Number(profileSelect.value);
        current.configuration_profile_id = Number.isFinite(nextId) && nextId > 0 ? nextId : null;
        const nextProfile = selectedProfile(profiles, current.configuration_profile_id);
        current.configuration = pruneOverrides(current.configuration, profileDefaults(nextProfile));
        emit();
        render(metadata);
      });

      host.querySelectorAll('[data-equipment-config-key]').forEach((control) => {
        control.addEventListener('change', () => {
          const key = clean(control.getAttribute('data-equipment-config-key'));
          const attribute = metadata.attributes.find((item) => clean(item.attribute_key) === key);
          if (!attribute) return;
          const nextProfile = selectedProfile(profiles, current.configuration_profile_id);
          const nextDefaults = profileDefaults(nextProfile);
          const parsed = normalizeScalar(attribute, control.value);
          const nextOverrides = copyRecord(current.configuration);
          if (parsed.empty) {
            if (hasOwn(nextDefaults, key)) nextOverrides[key] = null;
            else delete nextOverrides[key];
          } else if (hasOwn(nextDefaults, key) && sameValue(parsed.value, nextDefaults[key])) {
            delete nextOverrides[key];
          } else {
            nextOverrides[key] = parsed.value;
          }
          current.configuration = pruneOverrides(nextOverrides, nextDefaults);
          emit();
          render(metadata);
        });
      });
    }
  }

  window.EquipmentConfigurationEditorRuntime = Object.freeze({
    mount,
    clearCache() { metadataCache.clear(); }
  });
})();

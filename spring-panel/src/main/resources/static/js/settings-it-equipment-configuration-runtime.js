(() => {
  'use strict';

  const root = document.querySelector('[data-it-equipment-configuration-root]');
  if (!root) return;

  const typeSelect = document.querySelector('[data-it-equipment-configuration-type]');
  const message = document.querySelector('[data-it-equipment-configuration-message]');
  const attributesHost = document.querySelector('[data-it-equipment-configuration-attributes]');
  const attributeEditor = document.querySelector('[data-it-equipment-configuration-attribute-editor]');
  const profilesHost = document.querySelector('[data-it-equipment-configuration-profiles]');
  const profileEditor = document.querySelector('[data-it-equipment-configuration-profile-editor]');
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';

  const state = {
    catalog: [],
    attributes: [],
    profiles: [],
    type: ''
  };

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

  function showMessage(text, kind) {
    if (!message) return;
    const value = clean(text);
    message.className = 'alert py-2 px-3 ' + (value ? 'alert-' + (kind || 'danger') : 'd-none');
    message.textContent = value;
  }

  async function requestJson(url, options) {
    const request = Object.assign({ credentials: 'same-origin' }, options || {});
    request.headers = Object.assign({}, request.headers || {});
    if (request.body != null) {
      request.headers['Content-Type'] = 'application/json';
    }
    if (csrfToken && request.method && request.method !== 'GET') {
      request.headers[csrfHeader] = csrfToken;
    }
    const response = await fetch(url, request);
    const text = await response.text();
    let payload = {};
    if (text) {
      try { payload = JSON.parse(text); } catch (_error) { payload = { error: text }; }
    }
    if (!response.ok || payload.success === false) {
      throw new Error(clean(payload.error) || ('HTTP ' + response.status));
    }
    return payload;
  }

  function typeCatalog() {
    return state.catalog.filter(item => clean(item && item.equipment_type) === state.type);
  }

  function populateTypes(extraAttributes, extraProfiles) {
    const types = new Set();
    state.catalog.forEach(item => { const value = clean(item && item.equipment_type); if (value) types.add(value); });
    (extraAttributes || []).forEach(item => { const value = clean(item && item.equipment_type); if (value) types.add(value); });
    (extraProfiles || []).forEach(item => { const value = clean(item && item.equipment_type); if (value) types.add(value); });
    const values = Array.from(types).sort((a, b) => a.localeCompare(b, 'ru'));
    const previous = clean(typeSelect && typeSelect.value);
    if (typeSelect) {
      typeSelect.innerHTML = '<option value="">Выберите тип оборудования</option>' + values.map(value => '<option value="' + escapeHtml(value) + '">' + escapeHtml(value) + '</option>').join('');
      typeSelect.value = values.includes(previous) ? previous : (values[0] || '');
    }
    state.type = clean(typeSelect && typeSelect.value);
  }

  async function bootstrap() {
    try {
      showMessage('');
      const responses = await Promise.all([
        requestJson('/api/settings/it-equipment', { method: 'GET' }),
        requestJson('/api/settings/it-equipment/attributes', { method: 'GET' }),
        requestJson('/api/settings/it-equipment/profiles', { method: 'GET' })
      ]);
      state.catalog = Array.isArray(responses[0].items) ? responses[0].items : [];
      const allAttributes = Array.isArray(responses[1].items) ? responses[1].items : [];
      const allProfiles = Array.isArray(responses[2].items) ? responses[2].items : [];
      populateTypes(allAttributes, allProfiles);
      await reloadType();
    } catch (error) {
      showMessage(error.message || 'Не удалось загрузить конфигурации оборудования');
    }
  }

  async function reloadType() {
    state.type = clean(typeSelect && typeSelect.value);
    attributeEditor.innerHTML = '';
    profileEditor.innerHTML = '';
    if (!state.type) {
      state.attributes = [];
      state.profiles = [];
      renderAttributes();
      renderProfiles();
      return;
    }
    try {
      showMessage('');
      const query = '?equipmentType=' + encodeURIComponent(state.type);
      const responses = await Promise.all([
        requestJson('/api/settings/it-equipment/attributes' + query, { method: 'GET' }),
        requestJson('/api/settings/it-equipment/profiles' + query, { method: 'GET' })
      ]);
      state.attributes = Array.isArray(responses[0].items) ? responses[0].items : [];
      state.profiles = Array.isArray(responses[1].items) ? responses[1].items : [];
      renderAttributes();
      renderProfiles();
    } catch (error) {
      showMessage(error.message || 'Не удалось загрузить конфигурацию типа');
    }
  }

  function attributeTypeLabel(type) {
    return ({ text: 'Текст', integer: 'Целое число', decimal: 'Число', boolean: 'Да / нет', enum: 'Список' })[clean(type)] || clean(type);
  }

  function renderAttributes() {
    if (!attributesHost) return;
    if (!state.type) {
      attributesHost.innerHTML = '<div class="text-muted small">Выберите тип оборудования.</div>';
      return;
    }
    const rows = state.attributes.map(item => {
      const options = Array.isArray(item.options) && item.options.length ? '<div class="small text-muted">' + escapeHtml(item.options.join(' · ')) + '</div>' : '';
      const meta = [clean(item.section_name), clean(item.unit)].filter(Boolean).join(' · ');
      return '<tr>' +
        '<td><strong>' + escapeHtml(item.label) + '</strong><div class="small text-muted"><code>' + escapeHtml(item.attribute_key) + '</code>' + (meta ? ' · ' + escapeHtml(meta) : '') + '</div>' + options + '</td>' +
        '<td>' + escapeHtml(attributeTypeLabel(item.value_type)) + (item.required ? '<div class="small text-muted">обязательная для экземпляра</div>' : '') + '</td>' +
        '<td>' + (item.active === false ? '<span class="badge text-bg-secondary">Выключена</span>' : '<span class="badge text-bg-success">Активна</span>') + '</td>' +
        '<td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary me-1" type="button" data-config-attribute-edit="' + Number(item.id) + '">Изменить</button><button class="btn btn-sm btn-outline-danger" type="button" data-config-attribute-delete="' + Number(item.id) + '">Удалить</button></td>' +
        '</tr>';
    }).join('');
    attributesHost.innerHTML = '<div class="d-flex justify-content-between align-items-center gap-2 mb-2"><div class="small text-muted">Характеристик: ' + state.attributes.length + '</div><button class="btn btn-sm btn-outline-primary" type="button" data-config-attribute-new>+ Характеристика</button></div>' +
      (rows ? '<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead><tr><th>Характеристика</th><th>Тип</th><th>Состояние</th><th></th></tr></thead><tbody>' + rows + '</tbody></table></div>' : '<div class="text-muted small">Для этого типа характеристики ещё не заданы.</div>');
    attributesHost.querySelector('[data-config-attribute-new]')?.addEventListener('click', () => openAttributeEditor(null));
    attributesHost.querySelectorAll('[data-config-attribute-edit]').forEach(button => button.addEventListener('click', () => {
      const id = Number(button.getAttribute('data-config-attribute-edit'));
      openAttributeEditor(state.attributes.find(item => Number(item.id) === id) || null);
    }));
    attributesHost.querySelectorAll('[data-config-attribute-delete]').forEach(button => button.addEventListener('click', async () => {
      const id = Number(button.getAttribute('data-config-attribute-delete'));
      const item = state.attributes.find(entry => Number(entry.id) === id);
      if (!item || !window.confirm('Удалить характеристику «' + clean(item.label) + '»? Значение будет удалено из существующих профилей этого типа.')) return;
      try {
        await requestJson('/api/settings/it-equipment/attributes/' + id, { method: 'DELETE' });
        showMessage('Характеристика удалена', 'success');
        await reloadType();
      } catch (error) { showMessage(error.message); }
    }));
  }

  function openAttributeEditor(item) {
    const editing = !!item;
    const options = Array.isArray(item && item.options) ? item.options.join('\n') : '';
    attributeEditor.innerHTML = '<div class="card card-body border-0 bg-body-tertiary"><div class="d-flex justify-content-between align-items-center mb-3"><strong>' + (editing ? 'Изменить характеристику' : 'Новая характеристика') + '</strong><button class="btn-close" type="button" aria-label="Закрыть" data-config-attribute-cancel></button></div>' +
      '<form data-config-attribute-form><div class="row g-3">' +
      '<div class="col-md-4"><label class="form-label">Код</label><input class="form-control form-control-sm" name="attribute_key" value="' + escapeHtml(item && item.attribute_key) + '" placeholder="cpu_cores" required pattern="[a-z][a-z0-9_.-]{0,99}"></div>' +
      '<div class="col-md-5"><label class="form-label">Название</label><input class="form-control form-control-sm" name="label" value="' + escapeHtml(item && item.label) + '" required></div>' +
      '<div class="col-md-3"><label class="form-label">Тип значения</label><select class="form-select form-select-sm" name="value_type"><option value="text">Текст</option><option value="integer">Целое число</option><option value="decimal">Число</option><option value="boolean">Да / нет</option><option value="enum">Список</option></select></div>' +
      '<div class="col-md-4"><label class="form-label">Раздел</label><input class="form-control form-control-sm" name="section_name" value="' + escapeHtml(item && item.section_name) + '" placeholder="Вычислительная часть"></div>' +
      '<div class="col-md-2"><label class="form-label">Единица</label><input class="form-control form-control-sm" name="unit" value="' + escapeHtml(item && item.unit) + '" placeholder="ГБ"></div>' +
      '<div class="col-md-2"><label class="form-label">Порядок</label><input class="form-control form-control-sm" type="number" name="sort_order" value="' + escapeHtml(item && item.sort_order != null ? item.sort_order : 0) + '"></div>' +
      '<div class="col-md-4 d-flex align-items-end gap-3 pb-1"><label class="form-check"><input class="form-check-input" type="checkbox" name="required"' + (item && item.required ? ' checked' : '') + '><span class="form-check-label">Обязательная</span></label><label class="form-check"><input class="form-check-input" type="checkbox" name="active"' + (!item || item.active !== false ? ' checked' : '') + '><span class="form-check-label">Активна</span></label></div>' +
      '<div class="col-12"><label class="form-label">Подсказка</label><input class="form-control form-control-sm" name="help_text" value="' + escapeHtml(item && item.help_text) + '"></div>' +
      '<div class="col-12" data-config-enum-options><label class="form-label">Варианты списка</label><textarea class="form-control form-control-sm" name="options" rows="3" placeholder="По одному варианту на строку">' + escapeHtml(options) + '</textarea><div class="form-text">Используется только для типа «Список».</div></div>' +
      '</div><div class="d-flex justify-content-end gap-2 mt-3"><button class="btn btn-sm btn-outline-secondary" type="button" data-config-attribute-cancel>Отмена</button><button class="btn btn-sm btn-primary" type="submit">Сохранить</button></div></form></div>';
    const form = attributeEditor.querySelector('[data-config-attribute-form]');
    const valueType = form.querySelector('[name="value_type"]');
    valueType.value = clean(item && item.value_type) || 'text';
    const optionsRow = form.querySelector('[data-config-enum-options]');
    const updateOptionsVisibility = () => optionsRow.classList.toggle('d-none', valueType.value !== 'enum');
    valueType.addEventListener('change', updateOptionsVisibility);
    updateOptionsVisibility();
    attributeEditor.querySelectorAll('[data-config-attribute-cancel]').forEach(button => button.addEventListener('click', () => { attributeEditor.innerHTML = ''; }));
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const data = new FormData(form);
      const payload = {
        equipment_type: state.type,
        attribute_key: clean(data.get('attribute_key')).toLowerCase(),
        label: clean(data.get('label')),
        value_type: clean(data.get('value_type')),
        section_name: clean(data.get('section_name')) || null,
        unit: clean(data.get('unit')) || null,
        help_text: clean(data.get('help_text')) || null,
        required: form.elements.required.checked,
        active: form.elements.active.checked,
        sort_order: Number.parseInt(clean(data.get('sort_order')) || '0', 10),
        options: clean(data.get('value_type')) === 'enum' ? clean(data.get('options')).split(/\r?\n/).map(clean).filter(Boolean) : []
      };
      try {
        await requestJson('/api/settings/it-equipment/attributes' + (editing ? '/' + Number(item.id) : ''), { method: editing ? 'PUT' : 'POST', body: JSON.stringify(payload) });
        attributeEditor.innerHTML = '';
        showMessage('Характеристика сохранена', 'success');
        await reloadType();
      } catch (error) { showMessage(error.message); }
    });
  }

  function renderProfiles() {
    if (!profilesHost) return;
    if (!state.type) {
      profilesHost.innerHTML = '<div class="text-muted small">Выберите тип оборудования.</div>';
      return;
    }
    const rows = state.profiles.map(item => {
      const model = item.catalog_id ? [clean(item.equipment_vendor), clean(item.equipment_model)].filter(Boolean).join(' ') : 'Все модели типа';
      const values = item.values && typeof item.values === 'object' ? Object.keys(item.values).length : 0;
      return '<tr>' +
        '<td><strong>' + escapeHtml(item.profile_name) + '</strong>' + (clean(item.description) ? '<div class="small text-muted">' + escapeHtml(item.description) + '</div>' : '') + '</td>' +
        '<td>' + escapeHtml(model) + '</td><td>' + values + '</td>' +
        '<td>' + (item.active === false ? '<span class="badge text-bg-secondary">Выключен</span>' : '<span class="badge text-bg-success">Активен</span>') + '</td>' +
        '<td class="text-end text-nowrap"><button class="btn btn-sm btn-outline-secondary me-1" type="button" data-config-profile-edit="' + Number(item.id) + '">Изменить</button><button class="btn btn-sm btn-outline-danger" type="button" data-config-profile-delete="' + Number(item.id) + '">Удалить</button></td></tr>';
    }).join('');
    profilesHost.innerHTML = '<div class="d-flex justify-content-between align-items-center gap-2 mb-2"><div class="small text-muted">Профилей: ' + state.profiles.length + '</div><button class="btn btn-sm btn-outline-primary" type="button" data-config-profile-new>+ Профиль</button></div>' +
      (rows ? '<div class="table-responsive"><table class="table table-sm align-middle mb-0"><thead><tr><th>Профиль</th><th>Модель</th><th>Значений</th><th>Состояние</th><th></th></tr></thead><tbody>' + rows + '</tbody></table></div>' : '<div class="text-muted small">Для этого типа профили ещё не созданы.</div>');
    profilesHost.querySelector('[data-config-profile-new]')?.addEventListener('click', () => openProfileEditor(null));
    profilesHost.querySelectorAll('[data-config-profile-edit]').forEach(button => button.addEventListener('click', () => {
      const id = Number(button.getAttribute('data-config-profile-edit'));
      openProfileEditor(state.profiles.find(item => Number(item.id) === id) || null);
    }));
    profilesHost.querySelectorAll('[data-config-profile-delete]').forEach(button => button.addEventListener('click', async () => {
      const id = Number(button.getAttribute('data-config-profile-delete'));
      const item = state.profiles.find(entry => Number(entry.id) === id);
      if (!item || !window.confirm('Удалить профиль «' + clean(item.profile_name) + '»?')) return;
      try {
        await requestJson('/api/settings/it-equipment/profiles/' + id, { method: 'DELETE' });
        showMessage('Профиль удалён', 'success');
        await reloadType();
      } catch (error) { showMessage(error.message); }
    }));
  }

  function profileValueControl(attribute, value) {
    const key = escapeHtml(attribute.attribute_key);
    const label = escapeHtml(attribute.label);
    const unit = clean(attribute.unit) ? ' <span class="text-muted">(' + escapeHtml(attribute.unit) + ')</span>' : '';
    const help = clean(attribute.help_text) ? '<div class="form-text">' + escapeHtml(attribute.help_text) + '</div>' : '';
    const required = attribute.required ? '<span class="badge text-bg-light ms-1">обязательная для экземпляра</span>' : '';
    const raw = value == null ? '' : String(value);
    let control = '';
    if (attribute.value_type === 'enum') {
      const options = (Array.isArray(attribute.options) ? attribute.options : []).map(option => '<option value="' + escapeHtml(option) + '"' + (String(option) === raw ? ' selected' : '') + '>' + escapeHtml(option) + '</option>').join('');
      control = '<select class="form-select form-select-sm" data-config-key="' + key + '" data-config-type="enum"><option value="">Без значения по умолчанию</option>' + options + '</select>';
    } else if (attribute.value_type === 'boolean') {
      control = '<select class="form-select form-select-sm" data-config-key="' + key + '" data-config-type="boolean"><option value="">Без значения по умолчанию</option><option value="true"' + (raw === 'true' ? ' selected' : '') + '>Да</option><option value="false"' + (raw === 'false' ? ' selected' : '') + '>Нет</option></select>';
    } else if (attribute.value_type === 'integer' || attribute.value_type === 'decimal') {
      control = '<input class="form-control form-control-sm" type="number" ' + (attribute.value_type === 'integer' ? 'step="1"' : 'step="any"') + ' data-config-key="' + key + '" data-config-type="' + escapeHtml(attribute.value_type) + '" value="' + escapeHtml(raw) + '">';
    } else {
      control = '<input class="form-control form-control-sm" type="text" data-config-key="' + key + '" data-config-type="text" value="' + escapeHtml(raw) + '">';
    }
    return '<div class="col-md-6"><label class="form-label">' + label + unit + required + '</label>' + control + help + '</div>';
  }

  function openProfileEditor(item) {
    const editing = !!item;
    const values = item && item.values && typeof item.values === 'object' ? item.values : {};
    const models = typeCatalog().filter(entry => Number(entry && entry.id) > 0).map(entry => {
      const id = Number(entry.id);
      const label = [clean(entry.equipment_vendor), clean(entry.equipment_model)].filter(Boolean).join(' ') || ('#' + id);
      return '<option value="' + id + '"' + (Number(item && item.catalog_id) === id ? ' selected' : '') + '>' + escapeHtml(label) + '</option>';
    }).join('');
    const activeAttributes = state.attributes.filter(attribute => attribute.active !== false).sort((a, b) => Number(a.sort_order || 0) - Number(b.sort_order || 0) || Number(a.id || 0) - Number(b.id || 0));
    const grouped = new Map();
    activeAttributes.forEach(attribute => {
      const section = clean(attribute.section_name) || 'Основное';
      if (!grouped.has(section)) grouped.set(section, []);
      grouped.get(section).push(attribute);
    });
    const valuesHtml = Array.from(grouped.entries()).map(entry => '<div class="col-12"><div class="small fw-semibold text-uppercase text-muted mt-2">' + escapeHtml(entry[0]) + '</div></div>' + entry[1].map(attribute => profileValueControl(attribute, values[attribute.attribute_key])).join('')).join('');
    profileEditor.innerHTML = '<div class="card card-body border-0 bg-body-tertiary"><div class="d-flex justify-content-between align-items-center mb-3"><strong>' + (editing ? 'Изменить профиль' : 'Новый профиль') + '</strong><button class="btn-close" type="button" aria-label="Закрыть" data-config-profile-cancel></button></div>' +
      '<form data-config-profile-form><div class="row g-3">' +
      '<div class="col-md-5"><label class="form-label">Название профиля</label><input class="form-control form-control-sm" name="profile_name" value="' + escapeHtml(item && item.profile_name) + '" required></div>' +
      '<div class="col-md-5"><label class="form-label">Модель <span class="text-muted">(необязательно)</span></label><select class="form-select form-select-sm" name="catalog_id"><option value="">Для всех моделей типа</option>' + models + '</select></div>' +
      '<div class="col-md-2 d-flex align-items-end pb-1"><label class="form-check"><input class="form-check-input" type="checkbox" name="active"' + (!item || item.active !== false ? ' checked' : '') + '><span class="form-check-label">Активен</span></label></div>' +
      '<div class="col-12"><label class="form-label">Описание</label><input class="form-control form-control-sm" name="description" value="' + escapeHtml(item && item.description) + '"></div>' +
      (valuesHtml || '<div class="col-12"><div class="text-muted small">У этого типа пока нет активных характеристик. Профиль можно сохранить без значений.</div></div>') +
      '</div><div class="d-flex justify-content-end gap-2 mt-3"><button class="btn btn-sm btn-outline-secondary" type="button" data-config-profile-cancel>Отмена</button><button class="btn btn-sm btn-primary" type="submit">Сохранить</button></div></form></div>';
    const form = profileEditor.querySelector('[data-config-profile-form]');
    profileEditor.querySelectorAll('[data-config-profile-cancel]').forEach(button => button.addEventListener('click', () => { profileEditor.innerHTML = ''; }));
    form.addEventListener('submit', async event => {
      event.preventDefault();
      const data = new FormData(form);
      const configValues = {};
      form.querySelectorAll('[data-config-key]').forEach(input => {
        const key = clean(input.getAttribute('data-config-key'));
        const type = clean(input.getAttribute('data-config-type'));
        const raw = clean(input.value);
        if (!key || raw === '') return;
        if (type === 'boolean') configValues[key] = raw === 'true';
        else if (type === 'integer') configValues[key] = Number.parseInt(raw, 10);
        else if (type === 'decimal') configValues[key] = Number(raw);
        else configValues[key] = raw;
      });
      const catalogRaw = clean(data.get('catalog_id'));
      const payload = {
        equipment_type: state.type,
        catalog_id: catalogRaw ? Number(catalogRaw) : null,
        profile_name: clean(data.get('profile_name')),
        description: clean(data.get('description')) || null,
        values: configValues,
        active: form.elements.active.checked
      };
      try {
        await requestJson('/api/settings/it-equipment/profiles' + (editing ? '/' + Number(item.id) : ''), { method: editing ? 'PUT' : 'POST', body: JSON.stringify(payload) });
        profileEditor.innerHTML = '';
        showMessage('Профиль сохранён', 'success');
        await reloadType();
      } catch (error) { showMessage(error.message); }
    });
  }

  typeSelect?.addEventListener('change', () => { reloadType(); });
  bootstrap();
})();

(function (root) {
  'use strict';

  function collect(rows) {
    const groups = Object.create(null);
    let total = 0;
    for (const row of rows) {
      const group = row.group.trim(), field = row.field.trim();
      const values = row.values.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
      if (!/^[a-z][a-z0-9_]{0,39}$/.test(group) || group === 'locations'
          || !/^[a-z][a-z0-9_]{0,39}$/.test(field)) throw new Error('Коды группы и поля: латинские строчные буквы, цифры, подчёркивание; до 40 символов. Группа locations зарезервирована.');
      if (!values.length || values.length > 100 || new Set(values).size !== values.length
          || values.some(value => value.length > 200 || /[\u0000-\u001f\u007f-\u009f]/.test(value))) throw new Error('Укажите от 1 до 100 разных значений, каждое до 200 символов, по одному на строку.');
      if (!Object.hasOwn(groups, group)) groups[group] = Object.create(null);
      if (Object.hasOwn(groups[group], field)) throw new Error('Пара группа / поле повторяется.');
      groups[group][field] = values;
      total += values.length;
      if (Object.keys(groups).length > 20 || Object.keys(groups[group]).length > 20 || total > 1000) throw new Error('Допустимы до 20 групп, до 20 полей в группе и до 1000 значений всего.');
    }
    return groups;
  }

  // Converts one owner's runtime snapshot to the existing question editor's display model.
  function definitions(snapshot, businessId) {
    if (snapshot?.locationTree?.businessId !== businessId || snapshot?.presetDefinitions?.businessId !== businessId) throw new Error('Справочник принадлежит другому бизнесу.');
    const result = Object.create(null);
    const fields = Object.create(null);
    for (const [key, column, label] of [['location_type', 'locationType', 'Тип локации'], ['city', 'city', 'Город'], ['location_name', 'name', 'Локация']]) {
      const options = [...new Set((snapshot.locationTree.locations || []).map(row => row[column]).filter(value => typeof value === 'string' && value.trim()))];
      if (options.length) fields[key] = { label, options };
    }
    if (Object.keys(fields).length) result.locations = { label: 'Локации бизнеса', fields };
    for (const [group, entries] of Object.entries(snapshot.presetDefinitions.groups || {})) {
      result[group] = { label: group, fields: Object.fromEntries(Object.entries(entries).map(([field, options]) => [field, { label: field, options: [...options] }])) };
    }
    return result;
  }

  function mount(page, { fetch: send = root.fetch.bind(root), document: doc = root.document, confirm = root.confirm.bind(root) } = {}) {
    const owner = page.querySelector('[data-presets-business]');
    const rows = page.querySelector('[data-presets-rows]');
    const status = page.querySelector('[data-presets-status]');
    const form = page.querySelector('[data-presets-form]');
    const reload = page.querySelector('[data-presets-reload]');
    const add = page.querySelector('[data-presets-add]');
    const save = page.querySelector('[data-presets-save]');
    let loaded = null, dirty = false, sequence = 0, saving = false;
    const csrf = doc.querySelector('meta[name="_csrf"]')?.content || '';
    function announce(message, error = false) { status.textContent = message; status.className = error ? 'text-danger small' : 'text-muted small'; }
    function enabled(ready) { add.disabled = save.disabled = !ready; }
    async function request(path, method = 'GET', body) {
      const response = await send('/api/business-admin' + path, { method, credentials: 'same-origin', cache: 'no-store',
        headers: body ? { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf } : {}, ...(body ? { body: JSON.stringify(body) } : {}) });
      if (!response.ok) throw new Error(response.status === 409 ? 'Справочник изменён в другой сессии. Черновик сохранён на экране. Нажмите «Загрузить заново» для сверки.'
        : response.status === 403 ? 'Недостаточно прав для настройки справочника.' : response.status === 404 ? 'Активный бизнес не найден.'
          : response.status === 400 ? 'Проверьте коды и значения полей.' : 'Не удалось загрузить или сохранить справочник.');
      return response.json();
    }
    function row(group = '', field = '', values = []) {
      const item = doc.createElement('tr');
      for (const [name, value, multiline] of [['group', group, false], ['field', field, false], ['values', values.join('\n'), true]]) {
        const cell = doc.createElement('td'), input = doc.createElement(multiline ? 'textarea' : 'input');
        input.className = 'form-control'; input.value = value; input.dataset.presetColumn = name;
        input.setAttribute('aria-label', name === 'group' ? 'Код группы' : name === 'field' ? 'Код поля' : 'Варианты ответа');
        if (multiline) input.rows = 4; else input.maxLength = 40;
        input.addEventListener('input', () => { dirty = true; }); cell.appendChild(input); item.appendChild(cell);
      }
      const cell = doc.createElement('td'), button = doc.createElement('button');
      button.type = 'button'; button.className = 'btn btn-outline-danger btn-sm'; button.textContent = 'Удалить поле';
      button.addEventListener('click', () => { if (!saving) { item.remove(); dirty = true; } });
      cell.appendChild(button); item.appendChild(cell); rows.appendChild(item);
    }
    async function load(id) {
      const generation = ++sequence; loaded = null; enabled(false); rows.replaceChildren(); dirty = false;
      if (!id) { announce('Выберите активный бизнес.'); return; }
      announce('Загрузка справочника…');
      try {
        const data = await request(`/businesses/${id}/questionnaire-presets`);
        if (generation !== sequence || owner.value !== String(id)) return;
        if (String(data.businessId) !== String(id) || !/^[a-f0-9]{64}$/.test(data.sha256)) throw new Error('Получена неверная версия справочника.');
        loaded = data;
        for (const [group, fields] of Object.entries(data.groups)) for (const [field, values] of Object.entries(fields)) row(group, field, values);
        enabled(true); announce('Справочник загружен. Изменения применятся после сохранения.');
      } catch (error) { if (generation === sequence) announce(error.message, true); }
    }
    owner.addEventListener('change', () => {
      if (dirty && !confirm('Отбросить несохранённые изменения справочника?')) { owner.value = loaded ? String(loaded.businessId) : ''; return; }
      void load(owner.value);
    });
    reload.addEventListener('click', () => {
      if (!dirty || confirm('Загрузить сохранённую версию и отбросить черновик?')) void load(owner.value);
    });
    add.addEventListener('click', () => { if (loaded && !saving) { row(); dirty = true; } });
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (!loaded || saving || owner.value !== String(loaded.businessId)) return;
      try {
        const groups = collect([...rows.children].map(item => Object.fromEntries([...item.querySelectorAll('[data-preset-column]')].map(input => [input.dataset.presetColumn, input.value]))));
        const draft = loaded; saving = true; enabled(false); owner.disabled = reload.disabled = true;
        rows.querySelectorAll('input, textarea, button').forEach(input => { input.disabled = true; });
        // Freeze both the request owner and version; an old response cannot overwrite another business.
        const data = await request(`/businesses/${draft.businessId}/questionnaire-presets`, 'PUT', { expectedSha256: draft.sha256, groups });
        if (data.businessId !== draft.businessId || !/^[a-f0-9]{64}$/.test(data.sha256)) throw new Error('Не удалось подтвердить сохранённую версию. Загрузите справочник заново.');
        loaded = data; dirty = false; announce('Справочник выбранного бизнеса сохранён. Новые опросы используют новую версию; начатые могут потребовать повторного выбора.');
      } catch (error) { announce(error.message, true); }
      finally { saving = false; enabled(loaded !== null); owner.disabled = reload.disabled = false;
        rows.querySelectorAll('input, textarea, button').forEach(input => { input.disabled = false; }); }
    });
    enabled(false);
    const ready = request('/overview').then(data => {
      owner.replaceChildren(); const empty = doc.createElement('option'); empty.value = ''; empty.textContent = 'Выберите бизнес'; owner.appendChild(empty);
      for (const business of data.businesses.filter(item => item.status === 'active')) {
        const option = doc.createElement('option'); option.value = String(business.id); option.textContent = business.displayName; owner.appendChild(option);
      }
      announce('Выберите бизнес для настройки готовых полей.');
    }).catch(error => announce(error.message, true));
    return { ready };
  }

  function context({ select, status, container, apply, changed, legacy, fetch: send = root.fetch.bind(root), document: doc = root.document }) {
    let sequence = 0;
    const catalog = send('/api/business-admin/overview', { credentials: 'same-origin', cache: 'no-store' }).then(async response => {
      if (!response.ok) return;
      const data = await response.json();
      for (const business of data.businesses.filter(item => item.status === 'active')) {
        const option = doc.createElement('option'); option.value = String(business.id); option.textContent = business.displayName; select.appendChild(option);
      }
      container.hidden = false;
    }).catch(() => { /* The scoped controls remain hidden if the API is disabled or unavailable. */ });
    async function load() {
      const generation = ++sequence, id = select.value;
      changed(id); apply({});
      if (id === 'legacy') { status.textContent = 'Общий справочник для прежних каналов.'; legacy(); return; }
      if (id === 'general') { status.textContent = 'Вопросник «Без бизнеса» использует свободные вопросы и списки вариантов.'; return; }
      status.textContent = 'Загрузка полей выбранного бизнеса…';
      try {
        const response = await send(`/api/business-admin/businesses/${encodeURIComponent(id)}/questionnaire-presets/resources`, { credentials: 'same-origin', cache: 'no-store' });
        if (!response.ok) throw new Error('Не удалось загрузить справочник выбранного бизнеса. Повторите выбор.');
        const snapshot = await response.json();
        if (generation !== sequence || select.value !== id) return;
        apply(definitions(snapshot, Number(id))); status.textContent = 'Показаны поля выбранного бизнеса. Привязка шаблона настраивается отдельно в каналах.';
      } catch (error) { if (generation === sequence) status.textContent = error.message; }
    }
    select.addEventListener('change', load);
    return { catalog, load };
  }

  const api = { collect, definitions, mount, context };
  if (typeof module === 'object' && module.exports) module.exports = api;
  else {
    root.BusinessQuestionnairePresets = api;
    const page = root.document.querySelector('[data-business-presets]');
    if (page) mount(page);
  }
})(typeof window === 'object' ? window : globalThis);

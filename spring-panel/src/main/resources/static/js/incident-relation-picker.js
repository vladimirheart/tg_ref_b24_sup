(() => {
  'use strict';
  const byId = id => document.getElementById(id);
  const modal = byId('incidentCreateModal'), catalog = byId('incidentExternalObjectsModal');
  if (!modal || !catalog) return;
  const type = byId('incidentCreateRelationType'), input = byId('incidentCreateRelationSearch');
  const key = byId('incidentCreateRelationKey'), results = byId('incidentCreateRelationResults');
  const status = byId('incidentCreateRelationStatus'), form = byId('incidentExternalForm');
  const catalogInput = byId('incidentExternalSearch'), catalogList = byId('incidentExternalList');
  const catalogStatus = byId('incidentExternalStatus'), catalogButton = byId('incidentOpenExternalCatalog');
  let capabilities, capabilitiesPromise, options = [], active = -1, returnToCreate = false, chosenOnReturn = null;

  async function request(url, options = {}) {
    const headers = { Accept: 'application/json', ...options.headers };
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    if (csrf && csrfHeader) headers[csrfHeader] = csrf;
    const response = await fetch(url, { ...options, headers, credentials: 'same-origin', cache: 'no-store' });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || payload.error || (response.status === 403 ? 'Нет доступа к этому действию.' : 'Не удалось загрузить или сохранить объект. Повторите попытку.'));
    return payload;
  }

  async function loadCapabilities() {
    if (!capabilitiesPromise) {
      capabilitiesPromise = request('/api/incidents/related-object-options').then(payload => {
        capabilities = payload;
        for (const option of type.options) { option.disabled = !payload.types.includes(option.value); option.hidden = option.disabled; }
        if (!payload.types.includes(type.value)) type.value = payload.types[0] || '';
        byId('incidentExternalAdd').hidden = payload.can_create_external !== true;
        updateCatalogButton();
        return payload;
      }).catch(error => { capabilitiesPromise = null; throw error; });
    }
    return capabilitiesPromise;
  }

  // A request generation is invalidated on input, type change, selection, reset and modal close.
  // Abort is only an optimization; the generation check also rejects responses already in flight.
  function loader(run, render, error) {
    let generation = 0, timer, controller;
    function cancel() { generation++; clearTimeout(timer); controller?.abort(); }
    function schedule(immediate = false) {
      cancel();
      const current = generation;
      timer = setTimeout(async () => {
        controller = new AbortController();
        try {
          const payload = await run(controller.signal);
          if (current === generation) render(payload.items || []);
        } catch (e) { if (current === generation && e.name !== 'AbortError') error(e); }
      }, immediate ? 0 : 200);
    }
    return { cancel, schedule };
  }

  function closeResults() { results.hidden = true; input.setAttribute('aria-expanded', 'false'); input.removeAttribute('aria-activedescendant'); active = -1; }
  function invalidate() {
    key.value = '';
    input.setCustomValidity('Выберите существующий объект из списка результатов.');
    options = []; results.replaceChildren(); closeResults();
  }
  function select(item) {
    search.cancel();
    key.value = String(item.value);
    input.value = String(item.label);
    input.setCustomValidity('');
    status.textContent = item.detail || 'Объект выбран.';
    closeResults();
  }
  function resultButton(item, index) {
    const button = document.createElement('button');
    button.type = 'button'; button.className = 'incident-object-option';
    const label = document.createElement('span'); label.textContent = item.label;
    const detail = document.createElement('small'); detail.textContent = item.detail;
    button.append(label, detail);
    if (index !== undefined) {
      button.id = `incidentObjectOption-${index}`; button.setAttribute('role', 'option');
      button.setAttribute('aria-selected', 'false'); button.tabIndex = -1;
    }
    return button;
  }
  const search = loader(signal => {
    status.textContent = 'Поиск…';
    return request(`/api/incidents/related-objects?${new URLSearchParams({ type: type.value, query: input.value, limit: '20' })}`, { signal });
  }, items => {
    options = items; active = -1; results.replaceChildren();
    items.forEach((item, index) => {
      const button = resultButton(item, index);
      button.addEventListener('click', () => { select(item); input.focus(); }); results.append(button);
    });
    results.hidden = !items.length; input.setAttribute('aria-expanded', String(Boolean(items.length)));
    status.textContent = items.length ? `Найдено: ${items.length}. Выберите запись.` : 'Ничего не найдено. Измените запрос.';
  }, error => { closeResults(); status.textContent = error.message; });

  function updateCatalogButton() { catalogButton.hidden = type.value !== 'external_object'; }
  input.addEventListener('input', () => { invalidate(); if (capabilities) search.schedule(); });
  input.addEventListener('focus', () => { if (capabilities && !key.value) search.schedule(true); });
  input.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); search.cancel(); closeResults(); }
    if (results.hidden || !options.length) return;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault(); active = (active + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
      [...results.children].forEach((node, index) => node.setAttribute('aria-selected', String(index === active)));
      input.setAttribute('aria-activedescendant', results.children[active].id); results.children[active].scrollIntoView({ block: 'nearest' });
    }
    if (event.key === 'Enter') { event.preventDefault(); if (active >= 0) select(options[active]); }
  });
  document.addEventListener('click', event => { if (!input.parentElement.contains(event.target)) closeResults(); });
  type.addEventListener('change', () => { search.cancel(); input.value = ''; invalidate(); updateCatalogButton(); if (capabilities) search.schedule(true); });
  byId('incidentCreateForm').addEventListener('reset', () => {
    search.cancel(); invalidate();
    queueMicrotask(() => { if (capabilities && !capabilities.types.includes(type.value)) type.value = capabilities.types[0] || ''; updateCatalogButton(); });
  });
  modal.addEventListener('shown.bs.modal', async () => {
    try { await loadCapabilities(); if (modal.classList.contains('show') && !key.value) { invalidate(); search.schedule(true); } }
    catch (error) { invalidate(); status.textContent = error.message; }
  });
  modal.addEventListener('hidden.bs.modal', () => { search.cancel(); closeResults(); });

  const catalogSearch = loader(signal => {
    catalogStatus.textContent = 'Поиск…';
    return request(`/api/incidents/related-objects?${new URLSearchParams({ type: 'external_object', query: catalogInput.value, limit: '30' })}`, { signal });
  }, items => {
    catalogList.replaceChildren();
    items.forEach(item => {
      if (returnToCreate) {
        const button = resultButton(item);
        button.addEventListener('click', () => { chosenOnReturn = item; bootstrap.Modal.getOrCreateInstance(catalog).hide(); });
        catalogList.append(button);
      } else {
        const row = document.createElement('div'); row.className = 'incident-object-option';
        const label = document.createElement('strong'); label.textContent = item.label;
        const detail = document.createElement('small'); detail.textContent = item.detail;
        row.append(label, detail); catalogList.append(row);
      }
    });
    catalogStatus.textContent = items.length ? (returnToCreate ? 'Выберите объект для инцидента.' : `Найдено: ${items.length}.`) : 'Объекты не найдены.';
  }, error => { catalogList.replaceChildren(); catalogStatus.textContent = error.message; });
  catalogInput.addEventListener('input', () => catalogSearch.schedule());
  catalog.addEventListener('show.bs.modal', event => {
    returnToCreate = event.relatedTarget?.id === catalogButton.id; chosenOnReturn = null;
    byId('incidentExternalClose').textContent = returnToCreate ? 'Назад к инциденту' : 'Закрыть';
  });
  catalog.addEventListener('shown.bs.modal', async () => {
    try { await loadCapabilities(); if (catalog.classList.contains('show')) catalogSearch.schedule(true); }
    catch (error) { catalogStatus.textContent = error.message; }
  });
  catalog.addEventListener('hidden.bs.modal', () => {
    catalogSearch.cancel();
    if (returnToCreate) {
      if (chosenOnReturn) { type.value = 'external_object'; updateCatalogButton(); select(chosenOnReturn); }
      bootstrap.Modal.getOrCreateInstance(modal).show();
    }
  });
  byId('incidentExternalAdd').addEventListener('click', () => { form.hidden = !form.hidden; if (!form.hidden) byId('incidentExternalName').focus(); });
  form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!form.reportValidity() || capabilities?.can_create_external !== true) return;
    const submit = form.querySelector('[type="submit"]'); if (submit.disabled) return;
    submit.disabled = true; byId('incidentExternalError').textContent = '';
    try {
      const payload = await request('/api/incidents/external-objects', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
        name: byId('incidentExternalName').value, external_number: byId('incidentExternalNumber').value,
        external_url: byId('incidentExternalUrl').value, description: byId('incidentExternalDescription').value
      }) });
      form.reset(); form.hidden = true;
      if (returnToCreate && catalog.classList.contains('show')) { chosenOnReturn = payload.item; bootstrap.Modal.getOrCreateInstance(catalog).hide(); }
      else { catalogInput.value = ''; catalogSearch.schedule(true); }
    } catch (error) { byId('incidentExternalError').textContent = error.message; }
    finally { submit.disabled = false; }
  });
  invalidate();
})();

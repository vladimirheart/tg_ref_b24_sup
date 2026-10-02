(() => {
  'use strict';

  const editButton = document.getElementById('dialogDetailsLocationBusinessBtn');
  const modalElement = document.getElementById('dialogLocationBusinessModal');
  const form = document.getElementById('dialogLocationBusinessForm');
  const businessSelect = document.getElementById('dialogLocationBusinessBusiness');
  const typeSelect = document.getElementById('dialogLocationBusinessType');
  const citySelect = document.getElementById('dialogLocationBusinessCity');
  const locationSelect = document.getElementById('dialogLocationBusinessLocation');
  const errorElement = document.getElementById('dialogLocationBusinessError');
  const saveButton = document.getElementById('dialogLocationBusinessSave');

  if (!editButton || !modalElement || !form || typeof bootstrap === 'undefined' || !bootstrap.Modal) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
  let catalogItems = [];
  let catalogLoaded = false;

  function clean(value) {
    const text = String(value || '').trim();
    return !text || text === '—' ? '' : text;
  }

  function unique(values) {
    return Array.from(new Set(values.map(clean).filter(Boolean))).sort((left, right) => left.localeCompare(right, 'ru'));
  }

  function setOptions(select, values, selected, placeholder) {
    if (!select) return;
    const current = clean(selected);
    const items = unique(values);
    if (current && !items.includes(current)) items.unshift(current);
    select.innerHTML = '';
    const blank = document.createElement('option');
    blank.value = '';
    blank.textContent = placeholder;
    select.appendChild(blank);
    items.forEach(value => {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    });
    select.value = current;
  }

  function resolveTicketId() {
    const routeMatch = (window.location.pathname || '').match(/^\/dialogs\/([^/]+)\/?$/);
    if (routeMatch) {
      try { return decodeURIComponent(routeMatch[1]); } catch (_error) { return routeMatch[1]; }
    }
    const taskLink = document.getElementById('dialogDetailsCreateTask');
    try {
      const url = new URL(taskLink?.href || '', window.location.origin);
      return clean(url.searchParams.get('ticketId'));
    } catch (_error) {
      return '';
    }
  }

  function requestOptions(method, body) {
    const headers = new Headers({ 'Content-Type': 'application/json' });
    if (csrfToken) headers.set(csrfHeader, csrfToken);
    return {
      method,
      credentials: 'same-origin',
      headers,
      body: JSON.stringify(body),
    };
  }

  async function loadCatalog() {
    if (catalogLoaded) return;
    const response = await fetch('/api/dialogs/location-business/catalog', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Не удалось загрузить каталог локаций');
    const payload = await response.json();
    catalogItems = Array.isArray(payload?.items) ? payload.items : [];
    catalogLoaded = true;
  }

  function filteredByBusiness() {
    const business = clean(businessSelect?.value);
    return catalogItems.filter(item => !business || clean(item.business) === business);
  }

  function filteredByType() {
    const type = clean(typeSelect?.value);
    return filteredByBusiness().filter(item => !type || clean(item.locationType) === type);
  }

  function filteredByCity() {
    const city = clean(citySelect?.value);
    return filteredByType().filter(item => !city || clean(item.city) === city);
  }

  function refreshTypes(selected = '') {
    setOptions(typeSelect, filteredByBusiness().map(item => item.locationType), selected, 'Не указано');
  }

  function refreshCities(selected = '') {
    setOptions(citySelect, filteredByType().map(item => item.city), selected, 'Не указано');
  }

  function refreshLocations(selected = '') {
    setOptions(locationSelect, filteredByCity().map(item => item.locationName), selected, 'Не указано');
  }

  function inferCurrentPath(currentBusiness, currentLocation) {
    const business = clean(currentBusiness);
    const location = clean(currentLocation);
    return catalogItems.find(item => clean(item.business) === business && clean(item.locationName) === location)
      || catalogItems.find(item => clean(item.locationName) === location)
      || null;
  }

  function showError(message) {
    if (!errorElement) return;
    const text = clean(message);
    errorElement.textContent = text;
    errorElement.classList.toggle('d-none', !text);
  }

  async function openEditor() {
    showError('');
    try {
      await loadCatalog();
      const currentBusiness = clean(document.getElementById('dialogDetailsBusiness')?.textContent);
      const currentLocation = clean(document.getElementById('dialogDetailsLocation')?.textContent);
      const inferred = inferCurrentPath(currentBusiness, currentLocation);
      setOptions(businessSelect, catalogItems.map(item => item.business), currentBusiness || inferred?.business || '', 'Не указано');
      refreshTypes(inferred?.locationType || '');
      refreshCities(inferred?.city || '');
      refreshLocations(currentLocation || inferred?.locationName || '');
      modal.show();
    } catch (error) {
      showError(error instanceof Error ? error.message : String(error));
      modal.show();
    }
  }

  editButton.addEventListener('click', openEditor);
  businessSelect?.addEventListener('change', () => {
    refreshTypes('');
    refreshCities('');
    refreshLocations('');
  });
  typeSelect?.addEventListener('change', () => {
    refreshCities('');
    refreshLocations('');
  });
  citySelect?.addEventListener('change', () => refreshLocations(''));

  form.addEventListener('submit', async event => {
    event.preventDefault();
    showError('');
    const ticketId = resolveTicketId();
    if (!ticketId) {
      showError('Не удалось определить идентификатор диалога');
      return;
    }
    if (saveButton) saveButton.disabled = true;
    try {
      const response = await fetch(
        '/api/dialogs/' + encodeURIComponent(ticketId) + '/location-business',
        requestOptions('PATCH', {
          business: clean(businessSelect?.value) || null,
          locationType: clean(typeSelect?.value) || null,
          city: clean(citySelect?.value) || null,
          locationName: clean(locationSelect?.value) || null,
        })
      );
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload?.success === false) {
        throw new Error(payload?.error || 'HTTP ' + response.status);
      }
      modal.hide();
      window.location.reload();
    } catch (error) {
      showError(error instanceof Error ? error.message : String(error));
    } finally {
      if (saveButton) saveButton.disabled = false;
    }
  });
})();

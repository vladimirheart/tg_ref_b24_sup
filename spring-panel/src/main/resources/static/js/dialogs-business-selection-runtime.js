(() => {
  'use strict';

  const editButton = document.getElementById('dialogDetailsLocationBusinessBtn');
  const modalElement = document.getElementById('dialogLocationBusinessModal');
  const form = document.getElementById('dialogLocationBusinessForm');
  const businessSelect = document.getElementById('dialogLocationBusinessBusiness');
  const errorElement = document.getElementById('dialogLocationBusinessError');
  const saveButton = document.getElementById('dialogLocationBusinessSave');
  if (!editButton || !modalElement || !form || !businessSelect
      || typeof bootstrap === 'undefined' || !bootstrap.Modal) return;

  const modal = bootstrap.Modal.getOrCreateInstance(modalElement);
  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
  let activeContext = null;

  function clean(value) {
    return String(value || '').trim();
  }

  function currentContext() {
    const row = document.querySelector('tr.dialog-row-active');
    return {
      scopeKind: clean(row?.dataset?.scopeKind),
      ticketPublicId: clean(row?.dataset?.ticketPublicId),
    };
  }

  function validPublicId(value) {
    return /^[a-f0-9]{32}$/.test(value);
  }

  function showError(message) {
    const text = clean(message);
    errorElement.textContent = text;
    errorElement.classList.toggle('d-none', !text);
  }

  function requestOptions(method, body) {
    const headers = new Headers({ 'Content-Type': 'application/json' });
    if (csrfToken) headers.set(csrfHeader, csrfToken);
    return { method, credentials: 'same-origin', headers, body: JSON.stringify(body) };
  }

  function setBusinesses(items) {
    businessSelect.replaceChildren();
    const placeholder = document.createElement('option');
    placeholder.value = '';
    placeholder.textContent = 'Выберите бизнес';
    businessSelect.appendChild(placeholder);
    (Array.isArray(items) ? items : []).forEach((item) => {
      const id = Number(item?.id);
      const displayName = clean(item?.displayName);
      if (!Number.isSafeInteger(id) || id <= 0 || !displayName) return;
      const option = document.createElement('option');
      option.value = String(id);
      option.textContent = displayName;
      businessSelect.appendChild(option);
    });
  }

  async function openEditor() {
    activeContext = currentContext();
    showError('');
    if (activeContext.scopeKind === 'BUSINESS') {
      if (validPublicId(activeContext.ticketPublicId)) {
        window.location.assign(`/business-dialog-assignment/${encodeURIComponent(activeContext.ticketPublicId)}`);
        return;
      }
      showError('У бизнес-обращения отсутствует публичный идентификатор.');
      modal.show();
      return;
    }
    if (activeContext.scopeKind !== 'UNRESOLVED' || !validPublicId(activeContext.ticketPublicId)) {
      showError('Бизнес можно выбрать только для обращения, где вопросник его не определил.');
      modal.show();
      return;
    }
    try {
      const response = await fetch('/api/unresolved-scope/businesses', {
        credentials: 'same-origin', cache: 'no-store',
      });
      const payload = await response.json().catch(() => []);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      setBusinesses(payload);
      if (businessSelect.options.length <= 1) {
        throw new Error('Нет доступных бизнесов с правом работы.');
      }
      modal.show();
    } catch (error) {
      showError(error instanceof Error ? error.message : String(error));
      modal.show();
    }
  }

  editButton.addEventListener('click', openEditor);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    showError('');
    const businessId = Number(businessSelect.value);
    if (!activeContext || activeContext.scopeKind !== 'UNRESOLVED' || !validPublicId(activeContext.ticketPublicId)) {
      showError('Обращение больше не находится в состоянии выбора бизнеса. Обновите страницу.');
      return;
    }
    if (!Number.isSafeInteger(businessId) || businessId <= 0) {
      showError('Выберите бизнес.');
      return;
    }
    if (saveButton) saveButton.disabled = true;
    try {
      const response = await fetch(
        `/api/unresolved-scope/tickets/${encodeURIComponent(activeContext.ticketPublicId)}/business`,
        requestOptions('POST', { businessId }),
      );
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload?.success !== true) {
        throw new Error(payload?.error || `HTTP ${response.status}`);
      }
      const managementUrl = clean(payload.managementUrl);
      if (!managementUrl.startsWith('/business-dialog-assignment/')) {
        throw new Error('Сервер не вернул адрес бизнес-карточки.');
      }
      window.location.assign(managementUrl);
    } catch (error) {
      showError(error instanceof Error ? error.message : String(error));
    } finally {
      if (saveButton) saveButton.disabled = false;
    }
  });
})();

(function () {
  const tableBody = document.getElementById('certificateSitesTableBody');
  const createForm = document.getElementById('certificateSiteCreateForm');
  const refreshAllBtn = document.getElementById('refreshAllCertificatesBtn');
  const siteNameInput = document.getElementById('siteNameInput');
  const siteEndpointInput = document.getElementById('siteEndpointInput');
  const siteEnabledInput = document.getElementById('siteEnabledInput');

  const editModalEl = document.getElementById('certificateEditModal');
  const editForm = document.getElementById('certificateEditForm');
  const editSiteIdInput = document.getElementById('editSiteId');
  const editSiteNameInput = document.getElementById('editSiteNameInput');
  const editSiteEndpointInput = document.getElementById('editSiteEndpointInput');
  const editSiteEnabledInput = document.getElementById('editSiteEnabledInput');
  const editModal = editModalEl && window.bootstrap ? new bootstrap.Modal(editModalEl) : null;

  let sites = [];

  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function formatDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('ru-RU');
  }

  function formatDaysLeft(value) {
    if (value === null || value === undefined || value === '') return '—';
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return '—';
    if (numeric < 0) return `Просрочен на ${Math.abs(numeric)} дн`;
    return `${numeric} дн`;
  }

  function resolveStatusBadge(item) {
    const level = String(item?.status_level || item?.monitor_status || '').toLowerCase();
    if (level === 'ok') return '<span class="badge text-bg-success">OK</span>';
    if (level === 'warning') return '<span class="badge text-bg-warning">Warning</span>';
    if (level === 'critical') return '<span class="badge text-bg-danger">Critical</span>';
    if (level === 'expired') return '<span class="badge bg-danger-subtle text-danger border border-danger-subtle">Expired</span>';
    if (level === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Error</span>';
  }

  function resolveAvailabilityBadge(item) {
    const availability = String(item?.availability || '').toLowerCase();
    if (availability === 'up') return '<span class="badge text-bg-success">UP</span>';
    if (availability === 'down') return '<span class="badge text-bg-danger">DOWN</span>';
    if (availability === 'disabled') return '<span class="badge text-bg-secondary">Disabled</span>';
    return '<span class="badge text-bg-secondary">Unknown</span>';
  }

  function renderSitesTable() {
    if (!tableBody) return;
    if (!sites.length) {
      tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Пока нет сайтов для мониторинга.</td></tr>';
      return;
    }
    tableBody.innerHTML = '';
    sites.forEach((site) => {
      const row = document.createElement('tr');
      row.dataset.siteId = String(site.id);
      row.innerHTML = `
        <td>
          <div class="fw-semibold">${escapeHtml(site.site_name || site.host || 'Сайт')}</div>
          <div class="small text-muted">${site.enabled ? 'Активен' : 'Выключен'}</div>
        </td>
        <td>
          <div class="font-monospace">${escapeHtml(site.endpoint_url || '')}</div>
          <div class="small text-muted">${escapeHtml(site.host || '')}:${escapeHtml(site.port || '')}</div>
        </td>
        <td>${resolveStatusBadge(site)}</td>
        <td>${resolveAvailabilityBadge(site)}</td>
        <td>${formatDateTime(site.expires_at)}</td>
        <td>${formatDaysLeft(site.days_left)}</td>
        <td>${formatDateTime(site.last_checked_at)}</td>
        <td class="small text-danger">${escapeHtml(site.error_message || '')}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm">
            <button class="btn btn-outline-secondary" type="button" data-action="refresh">Обновить</button>
            <button class="btn btn-outline-primary" type="button" data-action="edit">Изменить</button>
            <button class="btn btn-outline-danger" type="button" data-action="delete">Удалить</button>
          </div>
        </td>
      `;
      tableBody.appendChild(row);
    });
  }

  function getCookieValue(name) {
    const cookies = document.cookie ? document.cookie.split(';') : [];
    const encodedName = `${encodeURIComponent(name)}=`;
    for (const raw of cookies) {
      const value = raw.trim();
      if (value.startsWith(encodedName)) {
        return decodeURIComponent(value.slice(encodedName.length));
      }
    }
    return '';
  }

  function getCsrfToken() {
    const tokenFromMeta = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    if (tokenFromMeta) return tokenFromMeta;
    const tokenFromInput = document.querySelector('input[name="_csrf"]')?.value || '';
    if (tokenFromInput) return tokenFromInput;
    return getCookieValue('XSRF-TOKEN');
  }

  function withCsrf(init = {}) {
    const method = String(init.method || 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
      return init;
    }
    const token = getCsrfToken();
    if (!token) {
      return init;
    }
    const headers = new Headers(init.headers || {});
    if (!headers.has('X-XSRF-TOKEN')) {
      headers.set('X-XSRF-TOKEN', token);
    }
    if (!headers.has('X-CSRF-TOKEN')) {
      headers.set('X-CSRF-TOKEN', token);
    }
    return {
      ...init,
      headers,
    };
  }

  async function requestJson(url, init = {}) {
    const requestInit = withCsrf(init);
    const response = await fetch(url, {
      credentials: 'same-origin',
      cache: 'no-store',
      ...requestInit,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok || data.success === false) {
      throw new Error(data.error || `HTTP ${response.status}`);
    }
    return data;
  }

  async function loadSites() {
    if (!tableBody) return;
    tableBody.innerHTML = '<tr><td colspan="9" class="text-center text-muted py-4">Загрузка...</td></tr>';
    try {
      const data = await requestJson('/api/monitoring/certificates/sites');
      sites = Array.isArray(data.items) ? data.items : [];
      renderSitesTable();
    } catch (error) {
      tableBody.innerHTML = `<tr><td colspan="9" class="text-center text-danger py-4">${escapeHtml(error.message)}</td></tr>`;
    }
  }

  async function createSite(event) {
    event.preventDefault();
    const payload = {
      siteName: siteNameInput?.value || '',
      endpointUrl: siteEndpointInput?.value || '',
      enabled: siteEnabledInput?.checked ?? true,
    };
    try {
      await requestJson('/api/monitoring/certificates/sites', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      if (createForm) createForm.reset();
      if (siteEnabledInput) siteEnabledInput.checked = true;
      await loadSites();
      showPopup('Сайт добавлен и сертификат проверен.', 'success');
    } catch (error) {
      showPopup(`Не удалось добавить сайт: ${error.message}`, 'error');
    }
  }

  function findSiteById(siteId) {
    const numericId = Number(siteId);
    return sites.find((item) => Number(item.id) === numericId) || null;
  }

  function openEditModal(siteId) {
    const site = findSiteById(siteId);
    if (!site || !editModal) return;
    if (editSiteIdInput) editSiteIdInput.value = String(site.id);
    if (editSiteNameInput) editSiteNameInput.value = site.site_name || '';
    if (editSiteEndpointInput) editSiteEndpointInput.value = site.endpoint_url || '';
    if (editSiteEnabledInput) editSiteEnabledInput.checked = Boolean(site.enabled);
    editModal.show();
  }

  async function saveSiteChanges(event) {
    event.preventDefault();
    const siteId = Number(editSiteIdInput?.value || 0);
    if (!Number.isFinite(siteId) || siteId <= 0) {
      showPopup('Не удалось определить сайт для обновления.', 'error');
      return;
    }
    const payload = {
      siteName: editSiteNameInput?.value || '',
      endpointUrl: editSiteEndpointInput?.value || '',
      enabled: editSiteEnabledInput?.checked ?? true,
    };
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      editModal?.hide();
      await loadSites();
      showPopup('Изменения сохранены.', 'success');
    } catch (error) {
      showPopup(`Не удалось сохранить изменения: ${error.message}`, 'error');
    }
  }

  async function refreshSite(siteId) {
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}/refresh`, {
        method: 'POST',
      });
      await loadSites();
      showPopup('Сертификат сайта обновлён.', 'success');
    } catch (error) {
      showPopup(`Не удалось обновить сертификат: ${error.message}`, 'error');
    }
  }

  async function deleteSite(siteId) {
    if (!window.confirm('Удалить сайт из мониторинга сертификатов?')) {
      return;
    }
    try {
      await requestJson(`/api/monitoring/certificates/sites/${siteId}`, {
        method: 'DELETE',
      });
      await loadSites();
      showPopup('Сайт удалён.', 'success');
    } catch (error) {
      showPopup(`Не удалось удалить сайт: ${error.message}`, 'error');
    }
  }

  async function refreshAll() {
    if (refreshAllBtn) {
      refreshAllBtn.disabled = true;
      refreshAllBtn.textContent = 'Обновляем...';
    }
    try {
      const data = await requestJson('/api/monitoring/certificates/refresh', {
        method: 'POST',
      });
      sites = Array.isArray(data.items) ? data.items : [];
      renderSitesTable();
      showPopup('Все сертификаты обновлены.', 'success');
    } catch (error) {
      showPopup(`Не удалось обновить сертификаты: ${error.message}`, 'error');
    } finally {
      if (refreshAllBtn) {
        refreshAllBtn.disabled = false;
        refreshAllBtn.textContent = 'Обновить все сертификаты';
      }
    }
  }

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action]');
    if (!button) return;
    const row = button.closest('tr[data-site-id]');
    if (!row) return;
    const siteId = Number(row.dataset.siteId);
    if (!Number.isFinite(siteId)) return;
    if (button.dataset.action === 'refresh') {
      refreshSite(siteId);
      return;
    }
    if (button.dataset.action === 'edit') {
      openEditModal(siteId);
      return;
    }
    if (button.dataset.action === 'delete') {
      deleteSite(siteId);
    }
  });

  createForm?.addEventListener('submit', createSite);
  editForm?.addEventListener('submit', saveSiteChanges);
  refreshAllBtn?.addEventListener('click', refreshAll);

  loadSites();
})();

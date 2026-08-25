(function () {
  const tableBody = document.getElementById('credentialRotationTableBody');
  const refreshBtn = document.getElementById('refreshCredentialRotationBtn');
  const generatedAtEl = document.getElementById('credentialRotationGeneratedAt');
  const captionEl = document.getElementById('credentialRotationCaption');
  const historyCaptionEl = document.getElementById('credentialRotationHistoryCaption');
  const historyTableBody = document.getElementById('credentialRotationHistoryTableBody');
  const editCaptionEl = document.getElementById('credentialRotationEditCaption');

  const editModalEl = document.getElementById('credentialRotationEditModal');
  const historyModalEl = document.getElementById('credentialRotationHistoryModal');
  const editModal = editModalEl ? new bootstrap.Modal(editModalEl) : null;
  const historyModal = historyModalEl ? new bootstrap.Modal(historyModalEl) : null;

  const form = document.getElementById('credentialRotationEditForm');
  const entryIdInput = document.getElementById('credentialRotationEntryId');
  const expiresAtInput = document.getElementById('credentialRotationExpiresAt');
  const rotatedAtInput = document.getElementById('credentialRotationRotatedAt');
  const intervalInput = document.getElementById('credentialRotationIntervalDays');
  const ownerInput = document.getElementById('credentialRotationOwnerName');
  const noteInput = document.getElementById('credentialRotationNote');

  const metrics = {
    total: document.getElementById('credentialRotationTotal'),
    ok: document.getElementById('credentialRotationOk'),
    warning: document.getElementById('credentialRotationWarning'),
    critical: document.getElementById('credentialRotationCritical'),
    trackingMissing: document.getElementById('credentialRotationTrackingMissing'),
    missingSecret: document.getElementById('credentialRotationMissingSecret'),
    sourceRemoved: document.getElementById('credentialRotationSourceRemoved')
  };

  let currentItems = [];

  async function api(path, options) {
    const response = await fetch(path, {
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json'
      },
      ...options
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      throw new Error(payload.error || 'Request failed');
    }
    return payload;
  }

  function formatDateTime(value) {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString('ru-RU');
  }

  function formatDuration(value) {
    return value == null ? '—' : `${value} ms`;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  function statusBadgeClass(level) {
    switch ((level || '').toLowerCase()) {
      case 'ok':
        return 'text-bg-success';
      case 'warning':
        return 'text-bg-warning';
      case 'critical':
        return 'text-bg-danger';
      default:
        return 'text-bg-secondary';
    }
  }

  function toLocalDateTimeInput(value) {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    const offset = date.getTimezoneOffset();
    const local = new Date(date.getTime() - offset * 60000);
    return local.toISOString().slice(0, 16);
  }

  function fromLocalDateTimeInput(value) {
    if (!value) {
      return '';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toISOString();
  }

  function renderOverview(overview, generatedAt) {
    const safe = overview || {};
    metrics.total.textContent = safe.total ?? 0;
    metrics.ok.textContent = safe.ok ?? 0;
    metrics.warning.textContent = safe.warning ?? 0;
    metrics.critical.textContent = safe.critical ?? 0;
    metrics.trackingMissing.textContent = safe.tracking_missing ?? 0;
    metrics.missingSecret.textContent = safe.missing_secret ?? 0;
    metrics.sourceRemoved.textContent = safe.source_removed ?? 0;
    generatedAtEl.textContent = `Generated at ${formatDateTime(generatedAt)}`;
    captionEl.textContent = `Metadata gaps: ${safe.tracking_missing ?? 0}. Missing secret: ${safe.missing_secret ?? 0}. Source removed: ${safe.source_removed ?? 0}.`;
  }

  function renderRows(items) {
    currentItems = Array.isArray(items) ? items : [];
    if (!currentItems.length) {
      tableBody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-4">Tracked credentials not found.</td></tr>';
      return;
    }
    tableBody.innerHTML = currentItems.map((item) => `
      <tr data-entry-id="${escapeHtml(item.id)}">
        <td>
          <div class="fw-semibold">${escapeHtml(item.display_name || '—')}</div>
          <div class="small text-muted">${escapeHtml(item.integration_kind || '—')} · ${escapeHtml(item.credential_kind || '—')}</div>
        </td>
        <td>
          <span class="badge ${statusBadgeClass(item.status_level)}">${escapeHtml(item.last_status || 'unknown')}</span>
          <div class="small text-muted mt-1">${escapeHtml(item.status_reason || '—')}</div>
        </td>
        <td>
          <div class="small">${escapeHtml(item.source_ref || '—')}</div>
          <div class="small text-muted">present=${escapeHtml(item.source_present)} / secret=${escapeHtml(item.secret_present)}</div>
        </td>
        <td>
          <div>${escapeHtml(formatDateTime(item.expires_at))}</div>
          <div class="small text-muted">last seen: ${escapeHtml(formatDateTime(item.last_seen_at))}</div>
        </td>
        <td>
          <div>rotated: ${escapeHtml(formatDateTime(item.rotated_at))}</div>
          <div class="small text-muted">next due: ${escapeHtml(formatDateTime(item.next_rotation_due_at))}</div>
          <div class="small text-muted">interval: ${escapeHtml(item.rotation_interval_days ?? '—')} d</div>
        </td>
        <td>${escapeHtml(item.owner_name || '—')}</td>
        <td>${escapeHtml(item.note || '—')}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm">
            <button type="button" class="btn btn-outline-secondary" data-action="history">History</button>
            <button type="button" class="btn btn-outline-primary" data-action="edit">Edit</button>
          </div>
        </td>
      </tr>
    `).join('');
  }

  async function loadOverview() {
    const payload = await api('/api/monitoring/credential-rotation/entries');
    renderOverview(payload.overview, payload.generated_at);
    renderRows(payload.items);
  }

  async function refreshAll() {
    refreshBtn.disabled = true;
    try {
      const payload = await api('/api/monitoring/credential-rotation/refresh', { method: 'POST' });
      renderOverview(payload.overview, payload.generated_at);
      renderRows(payload.items);
    } finally {
      refreshBtn.disabled = false;
    }
  }

  function openEditModal(entry) {
    if (!entry) {
      return;
    }
    entryIdInput.value = entry.id ?? '';
    expiresAtInput.value = toLocalDateTimeInput(entry.expires_at);
    rotatedAtInput.value = toLocalDateTimeInput(entry.rotated_at);
    intervalInput.value = entry.rotation_interval_days ?? '';
    ownerInput.value = entry.owner_name || '';
    noteInput.value = entry.note || '';
    editCaptionEl.textContent = `${entry.display_name || 'Credential'} · ${entry.source_ref || 'unknown source'}`;
    editModal?.show();
  }

  async function submitEditForm(event) {
    event.preventDefault();
    const entryId = entryIdInput.value;
    if (!entryId) {
      return;
    }
    const payload = {
      ownerName: ownerInput.value.trim() || '',
      note: noteInput.value.trim() || '',
      expiresAt: fromLocalDateTimeInput(expiresAtInput.value),
      rotatedAt: fromLocalDateTimeInput(rotatedAtInput.value),
      rotationIntervalDays: intervalInput.value ? Number(intervalInput.value) : null
    };
    await api(`/api/monitoring/credential-rotation/entries/${entryId}`, {
      method: 'PATCH',
      body: JSON.stringify(payload)
    });
    editModal?.hide();
    await loadOverview();
  }

  async function loadHistory(entry) {
    if (!entry?.id) {
      return;
    }
    historyCaptionEl.textContent = `Recent registry snapshots for ${entry.display_name || `entry #${entry.id}`}.`;
    historyTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">Загрузка...</td></tr>';
    historyModal?.show();
    const payload = await api(`/api/monitoring/credential-rotation/entries/${entry.id}/history`);
    const items = Array.isArray(payload.items) ? payload.items : [];
    if (!items.length) {
      historyTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-3">История пока пуста.</td></tr>';
      return;
    }
    historyTableBody.innerHTML = items.map((item) => `
      <tr>
        <td>${escapeHtml(formatDateTime(item.created_at))}</td>
        <td><span class="badge ${statusBadgeClass(item.status)}">${escapeHtml(item.status || 'unknown')}</span></td>
        <td>${escapeHtml(item.summary || '—')}</td>
        <td>${escapeHtml(formatDuration(item.duration_ms))}</td>
        <td class="small">${escapeHtml(item.details_excerpt || '—')}</td>
      </tr>
    `).join('');
  }

  refreshBtn?.addEventListener('click', () => {
    refreshAll().catch((error) => window.showToast?.(error.message || 'Не удалось обновить registry', 'danger'));
  });

  form?.addEventListener('submit', (event) => {
    submitEditForm(event).catch((error) => window.showToast?.(error.message || 'Не удалось сохранить metadata', 'danger'));
  });

  tableBody?.addEventListener('click', (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
      return;
    }
    const row = button.closest('tr[data-entry-id]');
    if (!row) {
      return;
    }
    const entryId = row.getAttribute('data-entry-id');
    const entry = currentItems.find((item) => String(item.id) === String(entryId));
    if (!entry) {
      return;
    }
    if (button.dataset.action === 'edit') {
      openEditModal(entry);
      return;
    }
    if (button.dataset.action === 'history') {
      loadHistory(entry).catch((error) => window.showToast?.(error.message || 'Не удалось загрузить историю', 'danger'));
    }
  });

  loadOverview().catch((error) => {
    tableBody.innerHTML = `<tr><td colspan="8" class="text-center text-danger py-4">${escapeHtml(error.message || 'Не удалось загрузить credential rotation registry')}</td></tr>`;
  });
})();

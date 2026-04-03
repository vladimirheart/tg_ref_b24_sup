(function () {
  const refreshBtn = document.getElementById('aiOpsRefresh');
  const stateEl = document.getElementById('aiOpsState');
  const alertsEl = document.getElementById('aiOpsAlerts');
  const runbookEl = document.getElementById('aiOpsRunbook');
  const eventsEl = document.getElementById('aiOpsEvents');
  const eventTypeInput = document.getElementById('aiOpsEventType');
  const actorInput = document.getElementById('aiOpsActor');
  const applyFiltersBtn = document.getElementById('aiOpsApplyFilters');
  const exportCsvBtn = document.getElementById('aiOpsExportCsv');
  const autoReplyRateEl = document.getElementById('aiOpsAutoReplyRate');
  const assistRateEl = document.getElementById('aiOpsAssistRate');
  const escalationRateEl = document.getElementById('aiOpsEscalationRate');
  const correctionRateEl = document.getElementById('aiOpsCorrectionRate');
  if (!stateEl) return;

  const filters = { eventType: '', actor: '' };

  function escapeHtml(value) {
    return String(value || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatRatePercent(value) {
    const n = Number(value);
    if (!Number.isFinite(n)) return '—';
    return `${(n * 100).toFixed(1)}%`;
  }

  function formatUtcDate(value) {
    if (!value) return '—';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    return d.toLocaleString();
  }

  function buildEventsQuery(days = 7, limit = 50, includeFormat) {
    const params = new URLSearchParams();
    params.set('days', String(days));
    params.set('limit', String(limit));
    if (filters.eventType) params.set('eventType', filters.eventType);
    if (filters.actor) params.set('actor', filters.actor);
    if (includeFormat) params.set('format', includeFormat);
    return params.toString();
  }

  function renderAlerts(alerts) {
    if (!alertsEl) return;
    if (!Array.isArray(alerts) || !alerts.length) {
      alertsEl.innerHTML = '<div class="text-muted">Алертов нет.</div>';
      return;
    }
    alertsEl.innerHTML = alerts.map((a) => {
      const severity = String(a?.severity || 'info');
      const cls = severity === 'warning'
        ? 'alert alert-warning py-2 px-3 mb-2'
        : (severity === 'ok' ? 'alert alert-success py-2 px-3 mb-2' : 'alert alert-info py-2 px-3 mb-2');
      return `<div class="${cls}">
        <div>${escapeHtml(a?.message || 'AI alert')}</div>
        <div class="small text-muted">value: ${formatRatePercent(a?.value)} | threshold: ${formatRatePercent(a?.threshold)}</div>
      </div>`;
    }).join('');
  }

  function renderRunbook(items) {
    if (!runbookEl) return;
    if (!Array.isArray(items) || !items.length) {
      runbookEl.innerHTML = '<li>Runbook недоступен.</li>';
      return;
    }
    runbookEl.innerHTML = items.map((x) => `<li>${escapeHtml(x)}</li>`).join('');
  }

  function renderEvents(items) {
    if (!eventsEl) return;
    if (!Array.isArray(items) || !items.length) {
      eventsEl.innerHTML = '<div class="text-muted">Событий нет.</div>';
      return;
    }
    eventsEl.innerHTML = items.slice(0, 20).map((item) => {
      const createdAt = formatUtcDate(item?.created_at);
      return `<div class="border rounded p-2 mb-1">
        <div class="d-flex flex-wrap justify-content-between gap-2">
          <span class="fw-semibold">${escapeHtml(item?.event_type || 'event')}</span>
          <span class="text-muted">${escapeHtml(createdAt)}</span>
        </div>
        <div class="text-muted">ticket: ${escapeHtml(item?.ticket_id || '-')} | actor: ${escapeHtml(item?.actor || 'system')}</div>
        <div>${escapeHtml(item?.decision_reason || item?.detail || '')}</div>
      </div>`;
    }).join('');
  }

  async function loadSummary(days = 7) {
    stateEl.textContent = 'Загрузка…';
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/summary?days=${encodeURIComponent(days)}`, { credentials: 'same-origin', cache: 'no-store' });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      const summary = payload.summary || {};
      const kpis = summary.kpis || {};
      if (autoReplyRateEl) autoReplyRateEl.textContent = formatRatePercent(kpis.auto_reply_rate);
      if (assistRateEl) assistRateEl.textContent = formatRatePercent(kpis.assist_usage_rate);
      if (escalationRateEl) escalationRateEl.textContent = formatRatePercent(kpis.escalation_rate);
      if (correctionRateEl) correctionRateEl.textContent = formatRatePercent(kpis.correction_rate);
      renderAlerts(summary.alerts);
      renderRunbook(summary.runbook?.items);
      stateEl.textContent = `Окно: ${summary.window_days || days} дн | обновлено ${formatUtcDate(summary.generated_at)}`;
    } catch (error) {
      stateEl.textContent = `Не удалось загрузить AI-метрики: ${error.message || 'unknown_error'}`;
      renderAlerts([]);
      renderRunbook([]);
    }
  }

  async function loadEvents(days = 7, limit = 50) {
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/events?${buildEventsQuery(days, limit)}`, { credentials: 'same-origin', cache: 'no-store' });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      renderEvents(Array.isArray(payload.items) ? payload.items : []);
    } catch (_error) {
      renderEvents([]);
    }
  }

  function exportEventsCsv(days = 7, limit = 200) {
    const href = `/api/dialogs/ai-monitoring/events?${buildEventsQuery(days, limit, 'csv')}`;
    const link = document.createElement('a');
    link.href = href;
    link.download = `ai-ops-events-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  if (refreshBtn) refreshBtn.addEventListener('click', () => { loadSummary(7); loadEvents(7, 50); });
  if (applyFiltersBtn) applyFiltersBtn.addEventListener('click', () => {
    filters.eventType = String(eventTypeInput?.value || '').trim().toLowerCase();
    filters.actor = String(actorInput?.value || '').trim().toLowerCase();
    loadEvents(7, 50);
  });
  if (exportCsvBtn) exportCsvBtn.addEventListener('click', () => {
    filters.eventType = String(eventTypeInput?.value || '').trim().toLowerCase();
    filters.actor = String(actorInput?.value || '').trim().toLowerCase();
    exportEventsCsv(7, 200);
  });

  loadSummary(7);
  loadEvents(7, 50);
})();

(function () {
  const detailNode = document.getElementById('incidentWorkbenchDetail');
  if (!detailNode) {
    return;
  }

  let requestSerial = 0;
  let refreshTimer = null;

  function currentIncidentId() {
    return String(detailNode.dataset.incidentId || '').trim();
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDate(value) {
    if (!value) {
      return '—';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('ru-RU');
  }

  function formatRate(value) {
    const number = Number(value);
    return Number.isFinite(number) ? `${number.toFixed(number % 1 === 0 ? 0 : 1)}%` : '—';
  }

  function statusBadge(status) {
    switch (String(status || '').toLowerCase()) {
      case 'healthy':
      case 'delivered':
        return 'text-bg-success';
      case 'failed':
        return 'text-bg-danger';
      case 'degraded':
      case 'pending':
      case 'queued':
      case 'processing':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  async function requestJson(url) {
    const response = await fetch(url, {
      credentials: 'same-origin',
      headers: { Accept: 'application/json' }
    });
    if (!response.ok) {
      let message = `Ошибка запроса: HTTP ${response.status}`;
      try {
        const payload = await response.json();
        message = payload?.message || payload?.error || message;
      } catch (error) {
        // Keep HTTP fallback.
      }
      throw new Error(message);
    }
    return response.json();
  }

  function findRouteCard() {
    return Array.from(detailNode.querySelectorAll('.incident-detail-columns .card')).find((card) => {
      const title = card.querySelector('.card-header strong')?.textContent?.trim();
      return title === 'Маршруты доставки';
    }) || null;
  }

  function ensureCard(incidentId) {
    const routeCard = findRouteCard();
    if (!routeCard || currentIncidentId() !== String(incidentId)) {
      return null;
    }
    let card = document.getElementById('incidentRouteDeliveryHealthCard');
    if (!card) {
      card = document.createElement('section');
      card.id = 'incidentRouteDeliveryHealthCard';
      card.className = 'card incident-route-health';
      routeCard.insertAdjacentElement('afterend', card);
    }
    card.dataset.incidentId = String(incidentId);
    return card;
  }

  function metric(label, value) {
    return `
      <div class="incident-route-health__metric">
        <div class="incident-route-health__metric-label">${escapeHtml(label)}</div>
        <div class="incident-route-health__metric-value">${escapeHtml(value)}</div>
      </div>
    `;
  }

  function routeRow(route) {
    const status = String(route?.health_status || route?.route_status || 'idle');
    return `
      <div class="incident-route-health__route">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div class="min-w-0">
            <strong>${escapeHtml(route?.route_type || 'route')} #${escapeHtml(route?.route_id ?? '—')}</strong>
            <div class="incident-route-health__route-meta text-break">${escapeHtml(route?.route_target || '—')}</div>
          </div>
          <span class="badge ${statusBadge(status)}">${escapeHtml(status)}</span>
        </div>
        <div class="incident-route-health__route-meta mt-2">
          24ч: delivered ${escapeHtml(route?.delivered_24h_count ?? 0)} · failed ${escapeHtml(route?.failed_24h_count ?? 0)} · pending ${escapeHtml(route?.pending_24h_count ?? 0)} · success ${escapeHtml(formatRate(route?.success_rate_24h))}
        </div>
      </div>
    `;
  }

  function historyRow(item) {
    const status = String(item?.status || 'unknown');
    const error = String(item?.last_error || '').trim();
    const retry = item?.available_at ? ` · retry ${formatDate(item.available_at)}` : '';
    return `
      <div class="incident-route-health__event">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div>
            <strong>${escapeHtml(item?.event_type || 'event')}</strong>
            <span class="incident-route-health__event-meta"> · route #${escapeHtml(item?.route_id ?? '—')}</span>
          </div>
          <span class="badge ${statusBadge(status)}">${escapeHtml(status)}</span>
        </div>
        <div class="incident-route-health__event-meta mt-1">
          ${escapeHtml(formatDate(item?.created_at))} · attempts ${escapeHtml(item?.attempt_count ?? 0)}${escapeHtml(retry)} · actor ${escapeHtml(item?.requested_by || 'system')}
        </div>
        ${error ? `
          <div class="incident-route-health__event-meta mt-1">Причина: ${escapeHtml(item?.error_kind || 'delivery_error')}</div>
          <pre class="incident-code incident-route-health__error text-danger">${escapeHtml(error)}</pre>
        ` : ''}
      </div>
    `;
  }

  function renderHealth(payload, incidentId) {
    const card = ensureCard(incidentId);
    if (!card) {
      return;
    }
    const summary = payload?.summary || {};
    const routes = Array.isArray(payload?.routes) ? payload.routes : [];
    const history = Array.isArray(payload?.history) ? payload.history : [];
    const overallStatus = String(summary?.overall_status || 'idle');
    card.innerHTML = `
      <div class="card-header d-flex flex-wrap align-items-center justify-content-between gap-2">
        <div>
          <strong>Здоровье доставки</strong>
          <div class="small text-muted">Фактические delivery events за ${escapeHtml(payload?.window_hours || 24)}ч, без synthetic probes.</div>
        </div>
        <div class="d-flex align-items-center gap-2">
          <span class="badge ${statusBadge(overallStatus)}">${escapeHtml(overallStatus)}</span>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-route-health-refresh>Обновить</button>
        </div>
      </div>
      <div class="card-body">
        <div class="incident-route-health__summary mb-3">
          ${metric('События / 24ч', summary?.events_24h_count ?? 0)}
          ${metric('Доставлено', summary?.delivered_24h_count ?? 0)}
          ${metric('Ошибки', summary?.failed_24h_count ?? 0)}
          ${metric('Success rate', formatRate(summary?.success_rate_24h))}
        </div>
        <div class="small text-muted mb-2">Маршруты · ${escapeHtml(summary?.route_count ?? routes.length)} · pending ${escapeHtml(summary?.pending_24h_count ?? 0)}</div>
        <div class="incident-route-health__routes mb-3">
          ${routes.map(routeRow).join('') || '<div class="text-muted">Маршруты доставки ещё не заданы.</div>'}
        </div>
        <details class="ui-disclosure-native">
          <summary>Последние delivery events · ${escapeHtml(history.length)}</summary>
          <div class="incident-route-health__history-list mt-2">
            ${history.map(historyRow).join('') || '<div class="text-muted">История доставки пока пуста.</div>'}
          </div>
        </details>
        <div class="small text-muted mt-2">Обновлено ${escapeHtml(formatDate(payload?.generated_at))}</div>
      </div>
    `;
  }

  function renderError(incidentId, message) {
    const card = ensureCard(incidentId);
    if (!card) {
      return;
    }
    card.innerHTML = `
      <div class="card-header"><strong>Здоровье доставки</strong></div>
      <div class="card-body small text-danger">${escapeHtml(message)}</div>
    `;
  }

  async function refreshHealth(force = false) {
    const incidentId = currentIncidentId();
    if (!incidentId || !findRouteCard()) {
      document.getElementById('incidentRouteDeliveryHealthCard')?.remove();
      return;
    }
    const existing = document.getElementById('incidentRouteDeliveryHealthCard');
    if (!force && existing?.dataset.incidentId === incidentId) {
      return;
    }
    const serial = ++requestSerial;
    try {
      const payload = await requestJson(`/api/incidents/${encodeURIComponent(incidentId)}/route-delivery-health`);
      if (serial !== requestSerial || currentIncidentId() !== incidentId) {
        return;
      }
      renderHealth(payload, incidentId);
    } catch (error) {
      if (serial !== requestSerial || currentIncidentId() !== incidentId) {
        return;
      }
      renderError(incidentId, error.message);
    }
  }

  function scheduleRefresh(force = false) {
    if (refreshTimer) {
      window.clearTimeout(refreshTimer);
    }
    refreshTimer = window.setTimeout(() => {
      refreshTimer = null;
      void refreshHealth(force);
    }, 0);
  }

  detailNode.addEventListener('click', (event) => {
    const button = event.target.closest('[data-route-health-refresh]');
    if (!(button instanceof HTMLButtonElement)) {
      return;
    }
    void refreshHealth(true);
  });

  const observer = new MutationObserver(() => scheduleRefresh(false));
  observer.observe(detailNode, { childList: true, subtree: true });
  scheduleRefresh(true);
}());
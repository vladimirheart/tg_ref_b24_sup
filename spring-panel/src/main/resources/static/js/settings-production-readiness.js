(() => {
  const modal = document.getElementById('productionReadinessModal');
  if (!(modal instanceof HTMLElement)) {
    return;
  }

  const body = modal.querySelector('[data-production-readiness-body]');
  const overall = modal.querySelector('[data-production-readiness-overall]');
  const meta = modal.querySelector('[data-production-readiness-meta]');
  const refreshButton = modal.querySelector('[data-production-readiness-refresh]');
  let requestSerial = 0;

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function formatDate(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('ru-RU');
  }

  function statusLabel(status) {
    switch (String(status || '').toLowerCase()) {
      case 'ready': return 'Готов';
      case 'healthy': return 'Исправен';
      case 'degraded': return 'Требует внимания';
      case 'compatibility': return 'Локально допустимо';
      case 'unavailable': return 'Недоступно';
      default: return String(status || 'Неизвестно');
    }
  }

  function statusClass(status) {
    switch (String(status || '').toLowerCase()) {
      case 'ready':
      case 'healthy':
        return 'is-healthy';
      case 'degraded':
        return 'is-degraded';
      case 'unavailable':
        return 'is-unavailable';
      default:
        return 'is-compatibility';
    }
  }

  function formatDetailValue(value) {
    if (value === null || value === undefined || value === '') return '—';
    if (typeof value === 'boolean') return value ? 'Да' : 'Нет';
    if (typeof value === 'number' && String(value).includes('.')) return String(value);
    return String(value);
  }

  function detailLabel(key) {
    switch (String(key || '')) {
      case 'mode': return 'Режим координации';
      case 'required_for_postgresql': return 'Обязателен для PostgreSQL';
      case 'transport_mode': return 'Режим транспорта';
      case 'provider': return 'Текущий провайдер';
      case 'product': return 'СУБД';
      case 'inbound_queue': return 'Входящая очередь';
      case 'ticket_created_queue': return 'Очередь ticket-created';
      case 'inbound_dlq': return 'DLQ входящей очереди';
      case 'ticket_created_dlq': return 'DLQ ticket-created';
      case 'inbound_messages': return 'Сообщений во входящей очереди';
      case 'ticket_created_messages': return 'Сообщений в ticket-created';
      case 'inbound_dlq_messages': return 'Сообщений во входящей DLQ';
      case 'ticket_created_dlq_messages': return 'Сообщений в ticket-created DLQ';
      case 'failed_current': return 'Текущих failed';
      case 'queued_current': return 'Текущих queued';
      case 'processing_current': return 'Текущих processing';
      case 'stale_processing': return 'Зависших processing';
      case 'delivered_24h': return 'Доставлено за 24 ч';
      case 'failed_24h': return 'Ошибок за 24 ч';
      case 'terminal_success_rate_24h': return 'Успешность за 24 ч, %';
      default: return String(key || 'detail');
    }
  }

  function contourLabel(value) {
    return String(value || '').toLowerCase() === 'production'
      ? 'production contour'
      : 'local bootstrap contour';
  }

  function componentCard(component) {
    const details = component?.details && typeof component.details === 'object'
      ? Object.entries(component.details)
      : [];
    const status = String(component?.status || 'unavailable');
    return `
      <article class="production-readiness-card ${statusClass(status)}">
        <div class="production-readiness-card__head">
          <div>
            <div class="production-readiness-card__title">${escapeHtml(component?.label || component?.key || 'Component')}</div>
            <div class="production-readiness-card__summary">${escapeHtml(component?.summary || '')}</div>
          </div>
          <span class="production-readiness-state ${statusClass(status)}">${escapeHtml(statusLabel(status))}</span>
        </div>
        <div class="production-readiness-card__meta">
          <span>${component?.required ? 'Обязательный production gate' : 'Локально допустимый компонент'}</span>
        </div>
        ${details.length ? `
          <dl class="production-readiness-details">
            ${details.map(([key, value]) => `
              <div class="production-readiness-details__row">
                <dt>${escapeHtml(detailLabel(key))}</dt>
                <dd>${escapeHtml(formatDetailValue(value))}</dd>
              </div>
            `).join('')}
          </dl>
        ` : ''}
      </article>
    `;
  }

  function render(payload) {
    const components = Array.isArray(payload?.components) ? payload.components : [];
    const overallStatus = String(payload?.overall || 'degraded');
    if (overall instanceof HTMLElement) {
      overall.className = `production-readiness-state ${statusClass(overallStatus)}`;
      overall.textContent = statusLabel(overallStatus);
    }
    if (meta instanceof HTMLElement) {
      meta.textContent = `${contourLabel(payload?.contour)} · DB ${payload?.datasource_mode || 'unknown'} · transport ${payload?.transport_mode || 'unknown'} · ${formatDate(payload?.generated_at)}`;
    }
    if (body instanceof HTMLElement) {
      body.innerHTML = `
        <div class="production-readiness-intro">
          <div>
            <strong>Production contour snapshot</strong>
            <div class="small text-muted mt-1">Проверки выполняются только на чтение. Для local bootstrap compatibility-компоненты показываются отдельно от обязательных production gate.</div>
          </div>
          <code>${escapeHtml(payload?.runbook || 'docs/runbooks/postgresql-production-contour.md')}</code>
        </div>
        <div class="production-readiness-grid">
          ${components.map(componentCard).join('') || '<div class="text-muted">Компоненты readiness не получены.</div>'}
        </div>
      `;
    }
  }

  function renderError(message) {
    if (overall instanceof HTMLElement) {
      overall.className = 'production-readiness-state is-unavailable';
      overall.textContent = 'Недоступно';
    }
    if (meta instanceof HTMLElement) {
      meta.textContent = 'Проверка не выполнена';
    }
    if (body instanceof HTMLElement) {
      body.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(message || 'Не удалось получить production readiness.')}</div>`;
    }
  }

  async function refresh() {
    const serial = ++requestSerial;
    if (refreshButton instanceof HTMLButtonElement) {
      refreshButton.disabled = true;
      refreshButton.setAttribute('aria-busy', 'true');
    }
    if (meta instanceof HTMLElement) meta.textContent = 'Проверка...';
    try {
      const response = await fetch('/api/settings/production-readiness', {
        credentials: 'same-origin',
        headers: { Accept: 'application/json' }
      });
      if (!response.ok) {
        let message = `Ошибка readiness: HTTP ${response.status}`;
        try {
          const payload = await response.json();
          message = payload?.message || payload?.error || message;
        } catch (error) {
          // Keep HTTP fallback.
        }
        throw new Error(message);
      }
      const payload = await response.json();
      if (serial === requestSerial) render(payload);
    } catch (error) {
      if (serial === requestSerial) renderError(error.message);
    } finally {
      if (refreshButton instanceof HTMLButtonElement && serial === requestSerial) {
        refreshButton.disabled = false;
        refreshButton.removeAttribute('aria-busy');
      }
    }
  }

  modal.addEventListener('shown.bs.modal', () => void refresh());
  refreshButton?.addEventListener('click', () => void refresh());
})();

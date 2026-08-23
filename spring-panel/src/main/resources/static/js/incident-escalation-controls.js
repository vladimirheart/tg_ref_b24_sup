(function () {
  const detailNode = document.getElementById('incidentWorkbenchDetail');
  if (!detailNode) {
    return;
  }

  const errorNode = document.getElementById('incidentWorkbenchError');
  const successNode = document.getElementById('incidentWorkbenchSuccess');
  let requestSerial = 0;
  let refreshTimer = null;
  let successTimer = null;

  function csrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const headerName = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-CSRF-TOKEN';
    return token ? { [headerName]: token } : {};
  }

  async function requestJson(url, options = {}) {
    const response = await fetch(url, {
      credentials: 'same-origin',
      ...options,
      headers: {
        Accept: 'application/json',
        ...csrfHeaders(),
        ...(options.headers || {})
      }
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

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function currentIncidentId() {
    return String(detailNode.dataset.incidentId || '').trim();
  }

  function formatDuration(seconds) {
    const safeSeconds = Math.max(0, Number(seconds || 0));
    if (!safeSeconds) {
      return 'нет';
    }
    const minutes = Math.max(1, Math.ceil(safeSeconds / 60));
    if (minutes < 60) {
      return `${minutes} мин`;
    }
    const hours = Math.floor(minutes / 60);
    const restMinutes = minutes % 60;
    return restMinutes ? `${hours} ч ${restMinutes} мин` : `${hours} ч`;
  }

  function formatDate(value) {
    if (!value) {
      return 'ещё не было';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('ru-RU');
  }

  function showSuccess(message) {
    if (!successNode) {
      return;
    }
    if (successTimer) {
      window.clearTimeout(successTimer);
    }
    successNode.textContent = String(message || 'Готово');
    successNode.classList.remove('d-none');
    errorNode?.classList.add('d-none');
    successTimer = window.setTimeout(() => {
      successNode.classList.add('d-none');
      successTimer = null;
    }, 3500);
  }

  function showError(message) {
    if (!errorNode) {
      return;
    }
    errorNode.textContent = String(message || 'Неизвестная ошибка');
    errorNode.classList.remove('d-none');
    successNode?.classList.add('d-none');
  }

  function findInsertionPoint() {
    const columns = detailNode.querySelector('.incident-detail-columns');
    const primaryColumn = columns?.firstElementChild;
    if (!(primaryColumn instanceof HTMLElement)) {
      return null;
    }
    const chronology = Array.from(primaryColumn.children).find((child) => {
      const title = child.querySelector('.card-header strong')?.textContent?.trim();
      return title === 'Хронология';
    }) || null;
    return { primaryColumn, chronology };
  }

  function policyMeta(policy) {
    const threshold = Number(policy?.threshold_minutes || 0);
    const parts = [`cooldown ${Number(policy?.cooldown_minutes || 0)} мин`];
    if (threshold > 0) {
      parts.unshift(`порог ${threshold} мин`);
    }
    return parts.join(' · ');
  }

  function policyRow(policy, incidentActive) {
    const id = String(policy?.policy || '');
    const muted = Boolean(policy?.muted);
    const muteRemaining = Number(policy?.mute_remaining_seconds || 0);
    const cooldownRemaining = Number(policy?.cooldown_remaining_seconds || 0);
    const disabled = !incidentActive ? 'disabled' : '';
    return `
      <div class="incident-escalation-policy" data-escalation-policy-row="${escapeHtml(id)}">
        <div class="incident-escalation-policy__main">
          <div class="d-flex flex-wrap align-items-center gap-2">
            <strong>${escapeHtml(policy?.label || id)}</strong>
            <span class="badge ${muted ? 'text-bg-secondary' : 'text-bg-light'}">
              ${muted ? `Mute · ${escapeHtml(formatDuration(muteRemaining))}` : 'Активен'}
            </span>
          </div>
          <div class="small text-muted mt-1">${escapeHtml(policyMeta(policy))}</div>
          <div class="small text-muted">
            Последняя эскалация: ${escapeHtml(formatDate(policy?.last_escalated_at))}
            ${cooldownRemaining > 0 ? ` · anti-storm ещё ${escapeHtml(formatDuration(cooldownRemaining))}` : ''}
          </div>
        </div>
        <div class="incident-escalation-policy__actions">
          <select class="form-select form-select-sm" data-escalation-mute-duration="${escapeHtml(id)}" aria-label="Длительность mute ${escapeHtml(policy?.label || id)}" ${disabled}>
            <option value="15">15 мин</option>
            <option value="60" selected>1 час</option>
            <option value="240">4 часа</option>
            <option value="1440">24 часа</option>
          </select>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-escalation-mute="${escapeHtml(id)}" ${disabled}>
            Приглушить
          </button>
          ${muted ? `<button type="button" class="btn btn-sm btn-outline-primary" data-escalation-unmute="${escapeHtml(id)}" ${disabled}>Снять mute</button>` : ''}
        </div>
      </div>
    `;
  }

  function renderControl(payload, incidentId) {
    const insertion = findInsertionPoint();
    if (!insertion || currentIncidentId() !== String(incidentId)) {
      return;
    }
    let card = document.getElementById('incidentEscalationControlCard');
    if (!card) {
      card = document.createElement('section');
      card.className = 'card incident-escalation-control';
      card.id = 'incidentEscalationControlCard';
      if (insertion.chronology) {
        insertion.primaryColumn.insertBefore(card, insertion.chronology);
      } else {
        insertion.primaryColumn.appendChild(card);
      }
    }
    card.dataset.incidentId = String(incidentId);
    const policies = Array.isArray(payload?.policies) ? payload.policies : [];
    const automationEnabled = payload?.enabled !== false;
    const incidentActive = payload?.incident_active !== false;
    card.innerHTML = `
      <div class="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
        <strong>Автоэскалация</strong>
        <span class="badge ${automationEnabled ? 'text-bg-success' : 'text-bg-secondary'}">
          ${automationEnabled ? 'Automation включена' : 'Automation выключена'}
        </span>
      </div>
      <div class="card-body">
        <div class="small text-muted mb-2">
          Mute хранится в shared coordination и не сбрасывает anti-storm cooldown.
        </div>
        <div class="incident-escalation-policy-list">
          ${policies.map((policy) => policyRow(policy, incidentActive)).join('') || '<div class="text-muted">Policy state недоступен.</div>'}
        </div>
      </div>
    `;
  }

  function renderControlError(incidentId, message) {
    const insertion = findInsertionPoint();
    if (!insertion || currentIncidentId() !== String(incidentId)) {
      return;
    }
    let card = document.getElementById('incidentEscalationControlCard');
    if (!card) {
      card = document.createElement('section');
      card.className = 'card incident-escalation-control';
      card.id = 'incidentEscalationControlCard';
      if (insertion.chronology) {
        insertion.primaryColumn.insertBefore(card, insertion.chronology);
      } else {
        insertion.primaryColumn.appendChild(card);
      }
    }
    card.dataset.incidentId = String(incidentId);
    card.innerHTML = `
      <div class="card-header"><strong>Автоэскалация</strong></div>
      <div class="card-body small text-danger">${escapeHtml(message)}</div>
    `;
  }

  async function refreshControl(force = false) {
    const incidentId = currentIncidentId();
    if (!incidentId || !findInsertionPoint()) {
      document.getElementById('incidentEscalationControlCard')?.remove();
      return;
    }
    const existing = document.getElementById('incidentEscalationControlCard');
    if (!force && existing?.dataset.incidentId === incidentId) {
      return;
    }
    const serial = ++requestSerial;
    try {
      const payload = await requestJson(`/api/incidents/${encodeURIComponent(incidentId)}/escalation-control`);
      if (serial !== requestSerial || currentIncidentId() !== incidentId) {
        return;
      }
      renderControl(payload, incidentId);
    } catch (error) {
      if (serial !== requestSerial || currentIncidentId() !== incidentId) {
        return;
      }
      renderControlError(incidentId, error.message);
    }
  }

  function scheduleRefresh(force = false) {
    if (refreshTimer) {
      window.clearTimeout(refreshTimer);
    }
    refreshTimer = window.setTimeout(() => {
      refreshTimer = null;
      void refreshControl(force);
    }, 0);
  }

  async function runControlAction(button, action) {
    if (!(button instanceof HTMLButtonElement) || button.disabled || button.dataset.pending === 'true') {
      return;
    }
    button.dataset.pending = 'true';
    button.disabled = true;
    button.setAttribute('aria-busy', 'true');
    try {
      await action();
    } finally {
      if (button.isConnected) {
        delete button.dataset.pending;
        button.disabled = false;
        button.removeAttribute('aria-busy');
      }
    }
  }

  detailNode.addEventListener('click', (event) => {
    const muteButton = event.target.closest('[data-escalation-mute]');
    const unmuteButton = event.target.closest('[data-escalation-unmute]');
    const button = muteButton || unmuteButton;
    if (!(button instanceof HTMLButtonElement)) {
      return;
    }
    const incidentId = currentIncidentId();
    const policy = String(
      muteButton?.getAttribute('data-escalation-mute') ||
      unmuteButton?.getAttribute('data-escalation-unmute') || ''
    ).trim();
    if (!incidentId || !policy) {
      return;
    }

    void runControlAction(button, async () => {
      try {
        if (muteButton) {
          const durationSelect = Array.from(
            detailNode.querySelectorAll('[data-escalation-mute-duration]')
          ).find((item) => item.getAttribute('data-escalation-mute-duration') === policy) || null;
          const minutes = Number(durationSelect?.value || 60);
          const payload = await requestJson(
            `/api/incidents/${encodeURIComponent(incidentId)}/escalation-mutes/${encodeURIComponent(policy)}`,
            {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ minutes })
            }
          );
          renderControl(payload, incidentId);
          showSuccess(`Эскалация ${policy} приглушена.`);
        } else {
          const payload = await requestJson(
            `/api/incidents/${encodeURIComponent(incidentId)}/escalation-mutes/${encodeURIComponent(policy)}`,
            { method: 'DELETE' }
          );
          renderControl(payload, incidentId);
          showSuccess(`Mute ${policy} снят.`);
        }
        window.setTimeout(() => {
          document.getElementById('incidentWorkbenchRefresh')?.click();
        }, 80);
      } catch (error) {
        showError(error.message);
        throw error;
      }
    });
  });

  const observer = new MutationObserver(() => scheduleRefresh(false));
  observer.observe(detailNode, { childList: true, subtree: true });
  scheduleRefresh(true);
}());
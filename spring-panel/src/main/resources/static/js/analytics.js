(function () {
  const card = document.getElementById('workspaceTelemetryCard');
  if (!card) {
    return;
  }

  const daysSelect = document.getElementById('workspaceTelemetryDays');
  const experimentInput = document.getElementById('workspaceTelemetryExperiment');
  const refreshButton = document.getElementById('workspaceTelemetryRefresh');
  const okBadge = document.getElementById('workspaceTelemetryGuardrailsOk');
  const attentionBadge = document.getElementById('workspaceTelemetryGuardrailsAttention');
  const updatedAt = document.getElementById('workspaceTelemetryUpdatedAt');
  const alertBox = document.getElementById('workspaceTelemetryAlertBox');
  const alertsTable = document.getElementById('workspaceTelemetryAlertsTable');

  const metricNodes = {};
  card.querySelectorAll('[data-metric]').forEach((node) => {
    metricNodes[node.getAttribute('data-metric')] = node;
  });

  function formatNumber(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    return Number(value).toLocaleString('ru-RU');
  }

  function formatRate(value) {
    if (value === null || value === undefined || Number.isNaN(Number(value))) {
      return '—';
    }
    return `${(Number(value) * 100).toFixed(2)}%`;
  }

  function formatTimestamp(value) {
    if (!value) {
      return '—';
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
      return value;
    }
    return parsed.toLocaleString('ru-RU');
  }

  function setLoadingState() {
    Object.values(metricNodes).forEach((node) => {
      node.textContent = '…';
    });
    updatedAt.textContent = 'Обновление telemetry...';
    okBadge.classList.add('d-none');
    attentionBadge.classList.add('d-none');
    alertBox.classList.add('d-none');
    alertsTable.innerHTML = '<tr><td colspan="4" class="text-muted text-center py-3">Загрузка данных...</td></tr>';
  }

  function renderAlerts(alerts) {
    if (!Array.isArray(alerts) || alerts.length === 0) {
      alertsTable.innerHTML = '<tr><td colspan="4" class="text-success text-center py-3">Отклонений guardrails не обнаружено.</td></tr>';
      return;
    }

    alertsTable.innerHTML = alerts.map((alert) => {
      const context = alert.segment ? `${alert.scope || 'segment'}: ${alert.segment}` : 'global';
      return `
        <tr>
          <td><span class="fw-semibold">${alert.metric || 'metric'}</span><div class="small text-muted">${alert.message || ''}</div></td>
          <td>${formatRate(alert.actual)}</td>
          <td>${formatRate(alert.threshold)}</td>
          <td class="small">${context}</td>
        </tr>
      `;
    }).join('');
  }

  function render(payload) {
    const totals = payload?.totals || {};
    Object.entries(metricNodes).forEach(([metric, node]) => {
      const value = totals[metric];
      node.textContent = metric === 'avg_open_ms' ? formatNumber(value) : formatNumber(value);
    });

    const guardrails = payload?.guardrails || {};
    const alerts = Array.isArray(guardrails.alerts) ? guardrails.alerts : [];
    const status = guardrails.status === 'attention' ? 'attention' : 'ok';
    okBadge.classList.toggle('d-none', status !== 'ok');
    attentionBadge.classList.toggle('d-none', status !== 'attention');

    updatedAt.textContent = `Обновлено: ${formatTimestamp(payload?.generated_at)} · окно ${payload?.window_days || '—'} дн.`;
    renderAlerts(alerts);

    if (status === 'attention') {
      alertBox.textContent = `Зафиксировано ${alerts.length} отклонений guardrails — проверьте таблицу ниже.`;
      alertBox.classList.remove('d-none');
    } else {
      alertBox.classList.add('d-none');
    }
  }

  async function loadTelemetry() {
    setLoadingState();
    const params = new URLSearchParams();
    params.set('days', daysSelect.value || '7');
    const experimentName = (experimentInput.value || '').trim();
    if (experimentName) {
      params.set('experiment_name', experimentName);
    }

    try {
      const response = await fetch(`/api/dialogs/workspace-telemetry/summary?${params.toString()}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      render(payload || {});
    } catch (error) {
      alertBox.textContent = `Не удалось загрузить telemetry summary: ${error.message}`;
      alertBox.classList.remove('d-none');
      updatedAt.textContent = 'Ошибка загрузки telemetry.';
      alertsTable.innerHTML = '<tr><td colspan="4" class="text-danger text-center py-3">Ошибка загрузки данных. Проверьте доступ к /api/dialogs/workspace-telemetry/summary.</td></tr>';
      Object.values(metricNodes).forEach((node) => {
        node.textContent = '—';
      });
      okBadge.classList.add('d-none');
      attentionBadge.classList.add('d-none');
    }
  }

  refreshButton.addEventListener('click', loadTelemetry);
  daysSelect.addEventListener('change', loadTelemetry);
  experimentInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      loadTelemetry();
    }
  });

  loadTelemetry();
})();

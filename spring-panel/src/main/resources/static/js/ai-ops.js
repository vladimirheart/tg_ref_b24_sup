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
  const actionStatusEl = document.getElementById('aiOpsActionStatus');
  const autoRefreshStateEl = document.getElementById('aiOpsAutoRefreshState');

  const memoryStateEl = document.getElementById('aiOpsMemoryState');
  const memoryListEl = document.getElementById('aiOpsMemoryList');
  const memoryQueryInput = document.getElementById('aiOpsMemoryQuery');
  const memorySearchBtn = document.getElementById('aiOpsMemorySearch');
  const memoryRefreshBtn = document.getElementById('aiOpsMemoryRefresh');
  const reviewStateEl = document.getElementById('aiOpsReviewState');
  const reviewTableBodyEl = document.getElementById('aiOpsReviewTableBody');
  const reviewRefreshBtn = document.getElementById('aiOpsReviewRefresh');
  const offlineEvalStateEl = document.getElementById('aiOpsOfflineEvalState');
  const offlineEvalDatasetEl = document.getElementById('aiOpsOfflineEvalDataset');
  const offlineEvalMetricsEl = document.getElementById('aiOpsOfflineEvalMetrics');
  const offlineEvalFailuresEl = document.getElementById('aiOpsOfflineEvalFailures');
  const offlineEvalRefreshBtn = document.getElementById('aiOpsOfflineEvalRefresh');
  const offlineEvalRunBtn = document.getElementById('aiOpsOfflineEvalRun');

  if (!stateEl) return;

  const AUTO_REFRESH_MS = 20000;
  const filters = { eventType: '', actor: '' };
  const memoryFilters = { query: '' };
  const actionStatusState = { timerId: 0 };
  const refreshRuntime = {
    timerId: 0,
    lastSuccessfulAt: 0,
    lastOutcome: 'idle',
  };
  const inFlight = {
    review: null,
    offline: null,
  };

  function getCookieValue(name) {
    const cookies = String(document.cookie || '').split(';');
    const expected = `${encodeURIComponent(name)}=`;
    for (const cookie of cookies) {
      const value = cookie.trim();
      if (value.startsWith(expected)) {
        return decodeURIComponent(value.slice(expected.length));
      }
    }
    return '';
  }

  function withCsrfHeaders(headers = {}) {
    const tokenFromMeta = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
    const token = tokenFromMeta || getCookieValue('XSRF-TOKEN');
    if (!token) return headers;
    return {
      ...headers,
      'X-XSRF-TOKEN': token,
    };
  }

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
    if (!Number.isFinite(n)) return '--';
    return `${(n * 100).toFixed(1)}%`;
  }

  function formatCount(value) {
    const n = Number(value);
    return Number.isFinite(n) ? String(n) : '--';
  }

  function formatTime(value) {
    const d = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(d.getTime())) return '--';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  function formatUtcDate(value) {
    if (!value) return '--';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return String(value);
    return d.toLocaleString();
  }

  function setTextIfChanged(el, value) {
    if (!el) return;
    const next = String(value || '');
    if (el.textContent !== next) {
      el.textContent = next;
    }
  }

  function setHtmlIfChanged(el, value) {
    if (!el) return;
    const next = String(value || '');
    if (el.innerHTML !== next) {
      el.innerHTML = next;
    }
  }

  function showActionStatus(message, tone = 'info', autoHideMs = 5000) {
    if (!actionStatusEl) return;
    window.clearTimeout(actionStatusState.timerId);
    actionStatusEl.dataset.tone = tone;
    setTextIfChanged(actionStatusEl, message);
    actionStatusEl.classList.add('is-visible');
    if (autoHideMs > 0) {
      actionStatusState.timerId = window.setTimeout(() => {
        actionStatusEl.classList.remove('is-visible');
      }, autoHideMs);
    }
  }

  function updateAutoRefreshState(outcome = refreshRuntime.lastOutcome) {
    if (!autoRefreshStateEl) return;
    const intervalLabel = `${Math.round(AUTO_REFRESH_MS / 1000)} сек.`;
    if (!refreshRuntime.lastSuccessfulAt) {
      setTextIfChanged(autoRefreshStateEl, `Автообновление каждые ${intervalLabel}. Ожидание первого цикла.`);
      return;
    }
    const suffix = outcome === 'error' ? 'Последний цикл с ошибкой' : 'Последняя синхронизация';
    setTextIfChanged(autoRefreshStateEl, `Автообновление каждые ${intervalLabel}. ${suffix}: ${formatTime(refreshRuntime.lastSuccessfulAt)}`);
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
    let html = '';
    if (!Array.isArray(alerts) || !alerts.length) {
      html = '<div class="text-muted">No alerts.</div>';
      setHtmlIfChanged(alertsEl, html);
      return;
    }
    html = alerts.map((a) => {
      const severity = String(a?.severity || 'info');
      const cls = severity === 'warning'
        ? 'alert alert-warning py-2 px-3 mb-2'
        : (severity === 'ok' ? 'alert alert-success py-2 px-3 mb-2' : 'alert alert-info py-2 px-3 mb-2');
      return `<div class="${cls}">
        <div>${escapeHtml(a?.message || 'AI alert')}</div>
        <div class="small text-muted">value: ${formatRatePercent(a?.value)} | threshold: ${formatRatePercent(a?.threshold)}</div>
      </div>`;
    }).join('');
    setHtmlIfChanged(alertsEl, html);
  }

  function renderRunbook(items) {
    if (!runbookEl) return;
    let html = '';
    if (!Array.isArray(items) || !items.length) {
      html = '<li>Runbook unavailable.</li>';
      setHtmlIfChanged(runbookEl, html);
      return;
    }
    html = items.map((x) => `<li>${escapeHtml(x)}</li>`).join('');
    setHtmlIfChanged(runbookEl, html);
  }

  function renderEvents(items) {
    if (!eventsEl) return;
    let html = '';
    if (!Array.isArray(items) || !items.length) {
      html = '<div class="text-muted">No events.</div>';
      setHtmlIfChanged(eventsEl, html);
      return;
    }
    html = items.slice(0, 20).map((item) => {
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
    setHtmlIfChanged(eventsEl, html);
  }

  function renderSolutionMemory(items) {
    if (!memoryListEl) return;
    let html = '';
    if (!Array.isArray(items) || !items.length) {
      html = '<div class="text-muted">No records.</div>';
      setHtmlIfChanged(memoryListEl, html);
      return;
    }
    html = items.map((item) => {
      const key = escapeHtml(item?.query_key || '');
      const queryText = escapeHtml(item?.query_text || '');
      const solutionText = escapeHtml(item?.solution_text || '');
      const reviewRequired = Number(item?.review_required || 0) > 0;
      const updatedAt = formatUtcDate(item?.updated_at || item?.created_at);
      const stats = `used: ${Number(item?.times_used || 0)} | confirmed: ${Number(item?.times_confirmed || 0)} | corrected: ${Number(item?.times_corrected || 0)}`;
      return `<div class="border rounded p-2 mb-2" data-memory-row="${key}">
        <div class="d-flex flex-wrap justify-content-between gap-2 mb-1">
          <span class="fw-semibold">${queryText || '(empty query)'}</span>
          <span class="text-muted">${escapeHtml(updatedAt)}</span>
        </div>
        <div class="text-muted mb-2">${escapeHtml(stats)}</div>
        <textarea class="form-control form-control-sm mb-2" rows="2" data-memory-query>${queryText}</textarea>
        <textarea class="form-control form-control-sm mb-2" rows="4" data-memory-solution>${solutionText}</textarea>
        <div class="d-flex flex-wrap align-items-center gap-2">
          <label class="form-check-label small">
            <input type="checkbox" class="form-check-input me-1" data-memory-review ${reviewRequired ? 'checked' : ''}> review required
          </label>
          <button class="btn btn-sm btn-outline-primary" type="button" data-memory-save="${key}">Save</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" data-memory-history="${key}">History</button>
          <button class="btn btn-sm btn-outline-danger" type="button" data-memory-delete="${key}">Delete</button>
          <span class="small text-muted" data-memory-row-state></span>
        </div>
        <div class="small mt-2" data-memory-history-list></div>
      </div>`;
    }).join('');
    setHtmlIfChanged(memoryListEl, html);
  }

  function renderReviewQueue(items) {
    if (!reviewTableBodyEl) return;
    let html = '';
    if (!Array.isArray(items) || !items.length) {
      html = '<tr><td colspan="5" class="text-muted text-center py-3">Очередь ревизий пуста.</td></tr>';
      setHtmlIfChanged(reviewTableBodyEl, html);
      return;
    }
    html = items.map((item) => {
      const queryKey = escapeHtml(String(item?.query_key || '').trim());
      const ticketId = String(item?.last_ticket_id || '').trim();
      const ticketLabel = ticketId ? `#${escapeHtml(ticketId)}` : '—';
      const current = escapeHtml(String(item?.solution_text || '').trim() || '—');
      const pending = escapeHtml(String(item?.pending_solution_text || '').trim() || '—');
      const question = escapeHtml(String(item?.query_text || '').trim() || '—');
      return `<tr data-ai-review-key="${queryKey}" data-ai-review-ticket="${escapeHtml(ticketId)}">
        <td class="small">${question}</td>
        <td class="small text-muted">${current}</td>
        <td class="small">${pending}</td>
        <td>${ticketLabel}</td>
        <td class="text-end">
          <div class="btn-group btn-group-sm" role="group">
            <button class="btn btn-outline-primary" type="button" data-ai-review-open ${ticketId ? '' : 'disabled'}>Открыть</button>
            <button class="btn btn-success" type="button" data-ai-review-approve>Принять</button>
            <button class="btn btn-outline-secondary" type="button" data-ai-review-reject>Отклонить</button>
          </div>
        </td>
      </tr>`;
    }).join('');
    setHtmlIfChanged(reviewTableBodyEl, html);
  }

  function renderOfflineEval(payload) {
    if (offlineEvalDatasetEl) {
      const dataset = payload?.dataset || {};
      const datasetVersion = escapeHtml(String(dataset?.dataset_version || payload?.dataset_version || '—'));
      const casesTotal = formatCount(dataset?.cases_total ?? payload?.dataset_cases);
      const templatesTotal = formatCount(dataset?.templates_total);
      setHtmlIfChanged(offlineEvalDatasetEl, `
        <div><strong>Dataset:</strong> ${datasetVersion}</div>
        <div><strong>Cases:</strong> ${casesTotal}</div>
        <div><strong>Templates:</strong> ${templatesTotal}</div>
      `);
    }
    if (offlineEvalMetricsEl) {
      const available = payload?.available === true;
      if (!available) {
        setHtmlIfChanged(offlineEvalMetricsEl, '<div class="text-muted">Прогонов ещё не было. Можно запустить первый eval вручную.</div>');
      } else {
        const createdAt = formatUtcDate(payload?.created_at);
        const actor = escapeHtml(String(payload?.actor || 'system'));
        setHtmlIfChanged(offlineEvalMetricsEl, `
          <div><strong>Последний запуск:</strong> ${escapeHtml(createdAt)}</div>
          <div><strong>Actor:</strong> ${actor}</div>
          <div><strong>Passed:</strong> ${formatCount(payload?.cases_passed)} / ${formatCount(payload?.cases_total)}</div>
          <div><strong>Intent accuracy:</strong> ${formatRatePercent(payload?.intent_accuracy)}</div>
          <div><strong>Policy accuracy:</strong> ${formatRatePercent(payload?.policy_accuracy)}</div>
          <div><strong>Retrieval hit rate:</strong> ${formatRatePercent(payload?.retrieval_hit_rate)}</div>
          <div><strong>Confirmed reply rate:</strong> ${formatRatePercent(payload?.confirmed_reply_rate)}</div>
        `);
      }
    }
    if (offlineEvalFailuresEl) {
      const failures = Array.isArray(payload?.details?.sample_failures) ? payload.details.sample_failures : [];
      if (!failures.length) {
        setHtmlIfChanged(offlineEvalFailuresEl, '<div class="text-muted">Нет сохранённых ошибок или прогон ещё не выполнялся.</div>');
      } else {
        setHtmlIfChanged(offlineEvalFailuresEl, failures.slice(0, 5).map((item) => {
          const message = escapeHtml(String(item?.message || '').trim() || '—');
          const expectedIntent = escapeHtml(String(item?.expected_intent || '').trim() || '—');
          const actualIntent = escapeHtml(String(item?.actual_intent || '').trim() || '—');
          const reason = escapeHtml(String(item?.consistency_reason || '').trim() || '—');
          return `<div class="border rounded p-2">
            <div class="fw-semibold">${message}</div>
            <div class="text-muted">expected: ${expectedIntent} | actual: ${actualIntent}</div>
            <div>${reason}</div>
          </div>`;
        }).join(''));
      }
    }
  }

  async function loadSummary(days = 7, options = {}) {
    if (!options.silent) setTextIfChanged(stateEl, 'Loading...');
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/summary?days=${encodeURIComponent(days)}`, { credentials: 'same-origin', cache: 'no-store' });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      const summary = payload.summary || {};
      const kpis = summary.kpis || {};
      setTextIfChanged(autoReplyRateEl, formatRatePercent(kpis.auto_reply_rate));
      setTextIfChanged(assistRateEl, formatRatePercent(kpis.assist_usage_rate));
      setTextIfChanged(escalationRateEl, formatRatePercent(kpis.escalation_rate));
      setTextIfChanged(correctionRateEl, formatRatePercent(kpis.correction_rate));
      renderAlerts(summary.alerts);
      renderRunbook(summary.runbook?.items);
      setTextIfChanged(stateEl, `Window: ${summary.window_days || days} days | updated ${formatUtcDate(summary.generated_at)}`);
      return { success: true };
    } catch (error) {
      setTextIfChanged(stateEl, `Failed to load AI metrics: ${error.message || 'unknown_error'}`);
      renderAlerts([]);
      renderRunbook([]);
      return { success: false, error };
    }
  }

  async function loadEvents(days = 7, limit = 50, options = {}) {
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/events?${buildEventsQuery(days, limit)}`, { credentials: 'same-origin', cache: 'no-store' });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      renderEvents(Array.isArray(payload.items) ? payload.items : []);
      return { success: true };
    } catch (_error) {
      renderEvents([]);
      if (!options.silent) {
        showActionStatus('Не удалось обновить список событий AI Ops.', 'error', 5500);
      }
      return { success: false };
    }
  }

  async function loadSolutionMemory(limit = 100, options = {}) {
    if (!options.silent) setTextIfChanged(memoryStateEl, 'Loading...');
    try {
      const params = new URLSearchParams();
      params.set('limit', String(limit));
      if (memoryFilters.query) params.set('query', memoryFilters.query);
      const resp = await fetch(`/api/dialogs/ai-solution-memory?${params.toString()}`, { credentials: 'same-origin', cache: 'no-store' });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      const items = Array.isArray(payload.items) ? payload.items : [];
      renderSolutionMemory(items);
      setTextIfChanged(memoryStateEl, `Loaded: ${items.length}`);
      return { success: true, count: items.length };
    } catch (error) {
      renderSolutionMemory([]);
      setTextIfChanged(memoryStateEl, `Failed to load: ${error.message || 'unknown_error'}`);
      return { success: false, error };
    }
  }

  async function loadReviewQueue(limit = 30, options = {}) {
    if (inFlight.review) return inFlight.review;
    if (!options.silent) setTextIfChanged(reviewStateEl, 'Загрузка...');
    inFlight.review = (async () => {
      try {
        const resp = await fetch(`/api/dialogs/ai-reviews?limit=${encodeURIComponent(limit)}`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
        const items = Array.isArray(payload.items) ? payload.items : [];
        renderReviewQueue(items);
        setTextIfChanged(reviewStateEl, items.length
          ? `Найдено ревизий: ${items.length}`
          : 'Очередь ревизий пуста.');
        return { success: true, count: items.length };
      } catch (error) {
        if (!options.keepStaleOnError) {
          renderReviewQueue([]);
        }
        setTextIfChanged(reviewStateEl, `Не удалось загрузить очередь ревизий: ${error.message || 'unknown_error'}`);
        return { success: false, error };
      } finally {
        inFlight.review = null;
      }
    })();
    return inFlight.review;
  }

  async function reviewQueueAction(queryKey, action, row) {
    const key = String(queryKey || '').trim();
    const normalizedAction = String(action || '').trim().toLowerCase();
    if (!key || !['approve', 'reject'].includes(normalizedAction)) return;
    const rowButtons = row ? Array.from(row.querySelectorAll('button')) : [];
    rowButtons.forEach((button) => { button.disabled = true; });
    setTextIfChanged(reviewStateEl, normalizedAction === 'approve' ? 'Принятие ревизии...' : 'Отклонение ревизии...');
    try {
      const resp = await fetch(`/api/dialogs/ai-reviews/${encodeURIComponent(key)}/${normalizedAction}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: withCsrfHeaders(),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      await Promise.all([
        loadReviewQueue(30, { keepStaleOnError: true }),
        loadSolutionMemory(100, { silent: true }),
      ]);
      showActionStatus(
        normalizedAction === 'approve'
          ? 'Ревизия принята и очередь обновлена.'
          : 'Ревизия отклонена и очередь обновлена.',
        'success'
      );
    } catch (error) {
      setTextIfChanged(reviewStateEl, `Не удалось выполнить действие: ${error.message || 'unknown_error'}`);
      showActionStatus(`Не удалось выполнить действие по ревизии: ${error.message || 'unknown_error'}`, 'error', 6500);
    } finally {
      if (row && row.isConnected) {
        const openBtn = row.querySelector('[data-ai-review-open]');
        const ticketId = String(row.getAttribute('data-ai-review-ticket') || '').trim();
        if (openBtn) openBtn.disabled = !ticketId;
        row.querySelectorAll('[data-ai-review-approve],[data-ai-review-reject]').forEach((button) => {
          button.disabled = false;
        });
      }
    }
  }

  async function loadOfflineEval(options = {}) {
    if (inFlight.offline) return inFlight.offline;
    if (!options.silent) setTextIfChanged(offlineEvalStateEl, 'Загрузка...');
    inFlight.offline = (async () => {
      try {
        const resp = await fetch('/api/dialogs/ai-monitoring/offline-eval', {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
        renderOfflineEval(payload.offline_eval || {});
        const available = payload?.offline_eval?.available === true;
        setTextIfChanged(offlineEvalStateEl, available
          ? `Последний прогон: ${formatUtcDate(payload.offline_eval.created_at)}`
          : 'Offline eval ещё не запускался.');
        return { success: true, available };
      } catch (error) {
        if (!options.keepStaleOnError) {
          renderOfflineEval({});
        }
        setTextIfChanged(offlineEvalStateEl, `Не удалось загрузить offline eval: ${error.message || 'unknown_error'}`);
        return { success: false, error };
      } finally {
        inFlight.offline = null;
      }
    })();
    return inFlight.offline;
  }

  async function runOfflineEvalNow() {
    if (!offlineEvalRunBtn) return;
    offlineEvalRunBtn.disabled = true;
    setTextIfChanged(offlineEvalStateEl, 'Запуск offline eval...');
    try {
      const resp = await fetch('/api/dialogs/ai-monitoring/offline-eval/run', {
        method: 'POST',
        credentials: 'same-origin',
        headers: withCsrfHeaders(),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      renderOfflineEval(payload.offline_eval || {});
      setTextIfChanged(offlineEvalStateEl, `Offline eval обновлён: ${formatUtcDate(payload?.offline_eval?.created_at)}`);
      showActionStatus('Offline eval успешно запущен и результат обновлён.', 'success');
    } catch (error) {
      setTextIfChanged(offlineEvalStateEl, `Не удалось запустить offline eval: ${error.message || 'unknown_error'}`);
      showActionStatus(`Не удалось запустить offline eval: ${error.message || 'unknown_error'}`, 'error', 6500);
    } finally {
      offlineEvalRunBtn.disabled = false;
    }
  }

  async function refreshAutoSlices() {
    if (document.hidden) return;
    const [reviewResult, offlineResult] = await Promise.all([
      loadReviewQueue(30, { silent: true, keepStaleOnError: true }),
      loadOfflineEval({ silent: true, keepStaleOnError: true }),
    ]);
    if (reviewResult?.success && offlineResult?.success) {
      refreshRuntime.lastSuccessfulAt = Date.now();
      refreshRuntime.lastOutcome = 'ok';
    } else {
      refreshRuntime.lastOutcome = 'error';
    }
    updateAutoRefreshState(refreshRuntime.lastOutcome);
  }

  function startAutoRefresh() {
    window.clearInterval(refreshRuntime.timerId);
    updateAutoRefreshState('idle');
    refreshRuntime.timerId = window.setInterval(() => {
      refreshAutoSlices();
    }, AUTO_REFRESH_MS);
  }

  async function saveSolutionMemoryRow(button) {
    if (!button) return;
    const key = String(button.getAttribute('data-memory-save') || '').trim();
    const row = button.closest('[data-memory-row]');
    const queryEl = row?.querySelector('[data-memory-query]');
    const solutionEl = row?.querySelector('[data-memory-solution]');
    const reviewEl = row?.querySelector('[data-memory-review]');
    const stateRowEl = row?.querySelector('[data-memory-row-state]');
    if (!key || !queryEl || !solutionEl) return;

    const body = {
      query_text: String(queryEl.value || '').trim(),
      solution_text: String(solutionEl.value || '').trim(),
      review_required: !!(reviewEl && reviewEl.checked),
    };
    if (!body.query_text || !body.solution_text) {
      if (stateRowEl) stateRowEl.textContent = 'query/solution required';
      return;
    }
    button.disabled = true;
    if (stateRowEl) stateRowEl.textContent = 'saving...';
    try {
      const resp = await fetch(`/api/dialogs/ai-solution-memory/${encodeURIComponent(key)}`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify(body),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false || payload.updated === false) {
        throw new Error(payload.error || 'update_failed');
      }
      if (stateRowEl) stateRowEl.textContent = 'saved';
    } catch (error) {
      if (stateRowEl) stateRowEl.textContent = `error: ${error.message || 'unknown_error'}`;
    } finally {
      button.disabled = false;
    }
  }

  async function loadSolutionMemoryHistory(button) {
    if (!button) return;
    const key = String(button.getAttribute('data-memory-history') || '').trim();
    const row = button.closest('[data-memory-row]');
    const historyEl = row?.querySelector('[data-memory-history-list]');
    if (!key || !historyEl) return;
    historyEl.textContent = 'loading history...';
    try {
      const resp = await fetch(`/api/dialogs/ai-solution-memory/${encodeURIComponent(key)}/history?limit=20`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (!items.length) {
        historyEl.innerHTML = '<div class="text-muted">No history.</div>';
        return;
      }
      historyEl.innerHTML = items.map((h) => {
        const id = Number(h?.id || 0);
        const createdAt = formatUtcDate(h?.created_at);
        const who = escapeHtml(h?.changed_by || 'system');
        const action = escapeHtml(h?.change_action || 'update');
        const oldSolution = escapeHtml(h?.old_solution_text || '');
        const newSolution = escapeHtml(h?.new_solution_text || '');
        return `<div class="border rounded p-2 mb-1" data-memory-history-row="${id}">
          <div class="d-flex flex-wrap justify-content-between gap-2">
            <span class="fw-semibold">#${id} ${action}</span>
            <span class="text-muted">${escapeHtml(createdAt)}</span>
          </div>
          <div class="text-muted">by: ${who}</div>
          <div class="small"><strong>old:</strong> ${oldSolution || '-'}</div>
          <div class="small"><strong>new:</strong> ${newSolution || '-'}</div>
          <button class="btn btn-sm btn-outline-danger mt-1" type="button" data-memory-rollback="${id}" data-memory-key="${key}">Rollback to old</button>
        </div>`;
      }).join('');
    } catch (error) {
      historyEl.textContent = `history error: ${error.message || 'unknown_error'}`;
    }
  }

  async function rollbackSolutionMemory(button) {
    if (!button) return;
    const historyId = Number(button.getAttribute('data-memory-rollback') || 0);
    const key = String(button.getAttribute('data-memory-key') || '').trim();
    if (!historyId || !key) return;
    button.disabled = true;
    try {
      const resp = await fetch(`/api/dialogs/ai-solution-memory/${encodeURIComponent(key)}/rollback`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: withCsrfHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ history_id: historyId }),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false || payload.updated === false) {
        throw new Error(payload.error || 'rollback_failed');
      }
      await loadSolutionMemory(100);
    } catch (error) {
      const row = button.closest('[data-memory-history-row]');
      if (row) {
        const err = document.createElement('div');
        err.className = 'small text-danger mt-1';
        err.textContent = `rollback error: ${error.message || 'unknown_error'}`;
        row.appendChild(err);
      }
    } finally {
      button.disabled = false;
    }
  }

  async function deleteSolutionMemoryRow(button) {
    if (!button) return;
    const key = String(button.getAttribute('data-memory-delete') || '').trim();
    const row = button.closest('[data-memory-row]');
    const stateRowEl = row?.querySelector('[data-memory-row-state]');
    if (!key || !row) return;
    const confirmed = window.confirm('Delete this memory record? This action cannot be undone.');
    if (!confirmed) return;
    button.disabled = true;
    if (stateRowEl) stateRowEl.textContent = 'deleting...';
    try {
      const resp = await fetch(`/api/dialogs/ai-solution-memory/${encodeURIComponent(key)}`, {
        method: 'DELETE',
        credentials: 'same-origin',
        headers: withCsrfHeaders(),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false || payload.deleted === false) {
        throw new Error(payload.error || 'delete_failed');
      }
      await loadSolutionMemory(100);
    } catch (error) {
      if (stateRowEl) stateRowEl.textContent = `error: ${error.message || 'unknown_error'}`;
    } finally {
      button.disabled = false;
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

  if (refreshBtn) refreshBtn.addEventListener('click', async () => {
    const [summaryResult, eventsResult, reviewResult, offlineResult] = await Promise.all([
      loadSummary(7),
      loadEvents(7, 50, { silent: true }),
      loadReviewQueue(30, { keepStaleOnError: true }),
      loadOfflineEval({ keepStaleOnError: true }),
    ]);
    if (summaryResult?.success && eventsResult?.success && reviewResult?.success && offlineResult?.success) {
      refreshRuntime.lastSuccessfulAt = Date.now();
      refreshRuntime.lastOutcome = 'ok';
      updateAutoRefreshState('ok');
      showActionStatus('AI Ops обновлён без полной перезагрузки страницы.', 'success');
    } else {
      refreshRuntime.lastOutcome = 'error';
      updateAutoRefreshState('error');
      showActionStatus('Часть данных AI Ops не удалось обновить. Старые данные сохранены, где это возможно.', 'error', 6500);
    }
  });
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
  if (memorySearchBtn) memorySearchBtn.addEventListener('click', () => {
    memoryFilters.query = String(memoryQueryInput?.value || '').trim();
    loadSolutionMemory(100);
  });
  if (memoryRefreshBtn) memoryRefreshBtn.addEventListener('click', async () => {
    const result = await loadSolutionMemory(100);
    showActionStatus(
      result?.success ? 'Solution memory обновлена.' : 'Не удалось обновить solution memory.',
      result?.success ? 'success' : 'error',
      result?.success ? 4500 : 6500
    );
  });
  if (reviewRefreshBtn) reviewRefreshBtn.addEventListener('click', async () => {
    const result = await loadReviewQueue(30, { keepStaleOnError: true });
    showActionStatus(
      result?.success ? 'Очередь ревизий обновлена.' : 'Не удалось обновить очередь ревизий.',
      result?.success ? 'success' : 'error',
      result?.success ? 4500 : 6500
    );
  });
  if (offlineEvalRefreshBtn) offlineEvalRefreshBtn.addEventListener('click', async () => {
    const result = await loadOfflineEval({ keepStaleOnError: true });
    showActionStatus(
      result?.success ? 'Данные offline eval обновлены.' : 'Не удалось обновить offline eval.',
      result?.success ? 'success' : 'error',
      result?.success ? 4500 : 6500
    );
  });
  if (offlineEvalRunBtn) offlineEvalRunBtn.addEventListener('click', () => {
    runOfflineEvalNow();
  });
  if (reviewTableBodyEl) {
    reviewTableBodyEl.addEventListener('click', (event) => {
      const row = event.target.closest('[data-ai-review-key]');
      if (!row) return;
      const queryKey = String(row.getAttribute('data-ai-review-key') || '').trim();
      const ticketId = String(row.getAttribute('data-ai-review-ticket') || '').trim();
      const openBtn = event.target.closest('[data-ai-review-open]');
      if (openBtn && ticketId) {
        window.location.href = `/dialogs/${encodeURIComponent(ticketId)}`;
        return;
      }
      const approveBtn = event.target.closest('[data-ai-review-approve]');
      if (approveBtn) {
        reviewQueueAction(queryKey, 'approve', row);
        return;
      }
      const rejectBtn = event.target.closest('[data-ai-review-reject]');
      if (rejectBtn) {
        reviewQueueAction(queryKey, 'reject', row);
      }
    });
  }
  if (memoryListEl) {
    memoryListEl.addEventListener('click', (event) => {
      const saveBtn = event.target.closest('[data-memory-save]');
      if (saveBtn) saveSolutionMemoryRow(saveBtn);
      const historyBtn = event.target.closest('[data-memory-history]');
      if (historyBtn) loadSolutionMemoryHistory(historyBtn);
      const rollbackBtn = event.target.closest('[data-memory-rollback]');
      if (rollbackBtn) rollbackSolutionMemory(rollbackBtn);
      const deleteBtn = event.target.closest('[data-memory-delete]');
      if (deleteBtn) deleteSolutionMemoryRow(deleteBtn);
    });
  }

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      refreshAutoSlices();
    }
  });

  Promise.all([
    loadSummary(7),
    loadEvents(7, 50, { silent: true }),
    loadSolutionMemory(100, { silent: true }),
    loadReviewQueue(30, { keepStaleOnError: true }),
    loadOfflineEval({ keepStaleOnError: true }),
  ]).then((results) => {
    const criticalOk = results[0]?.success && results[3]?.success && results[4]?.success;
    refreshRuntime.lastOutcome = criticalOk ? 'ok' : 'error';
    if (criticalOk) {
      refreshRuntime.lastSuccessfulAt = Date.now();
    }
    updateAutoRefreshState(refreshRuntime.lastOutcome);
    startAutoRefresh();
  });
})();

(function () {
  if (window.DialogsAiRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      return String(value ?? '');
    }

    function formatUtcDate(value, formatOptions = {}) {
      if (typeof options.formatUtcDate === 'function') {
        return options.formatUtcDate(value, formatOptions);
      }
      return String(value || '').trim();
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function getAiMonitoringFilters() {
      const filters = typeof options.getAiMonitoringFilters === 'function'
        ? options.getAiMonitoringFilters()
        : null;
      return filters && typeof filters === 'object'
        ? filters
        : { eventType: '', actor: '' };
    }

    function getWorkspaceAiReviewState() {
      const state = typeof options.getWorkspaceAiReviewState === 'function'
        ? options.getWorkspaceAiReviewState()
        : null;
      return state && typeof state === 'object'
        ? state
        : { problemCandidates: [], solutionCandidates: [] };
    }

    function setWorkspaceAiReviewState(problemCandidates, solutionCandidates) {
      if (typeof options.setWorkspaceAiReviewState === 'function') {
        options.setWorkspaceAiReviewState(problemCandidates, solutionCandidates);
      }
    }

    async function loadWorkspaceAiSuggestions(ticketId) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!elements.workspaceAiSuggestionsSection || !elements.workspaceAiSuggestionsState || !elements.workspaceAiSuggestionsList) {
        return;
      }
      if (!normalizedTicketId) {
        elements.workspaceAiSuggestionsState.textContent = 'Откройте диалог, чтобы загрузить AI-подсказки.';
        elements.workspaceAiSuggestionsList.classList.add('d-none');
        elements.workspaceAiSuggestionsList.innerHTML = '';
        return;
      }
      elements.workspaceAiSuggestionsState.textContent = 'Загрузка AI-подсказок...';
      elements.workspaceAiSuggestionsList.classList.add('d-none');
      elements.workspaceAiSuggestionsList.innerHTML = '';
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-suggestions?limit=3`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || (`HTTP ${resp.status}`));
        }
        const items = Array.isArray(payload.items) ? payload.items : [];
        if (!items.length) {
          elements.workspaceAiSuggestionsState.textContent = payload.processing
            ? 'AI is processing this dialog, suggestions are not ready yet.'
            : 'No suggestions yet. Reply manually or refresh later.';
          return;
        }
        elements.workspaceAiSuggestionsState.textContent = payload.processing
          ? 'Dialog is in automatic processing mode. Suggestions:'
          : 'Suggestions ready:';
        elements.workspaceAiSuggestionsList.innerHTML = items.map((item, index) => {
          const scoreLabel = String(item?.score_label || item?.score || '').trim();
          const source = String(item?.source || '').trim();
          const title = escapeHtml(String(item?.title || `Suggestion ${index + 1}`));
          const snippet = escapeHtml(String(item?.snippet || ''));
          const reply = escapeHtml(String(item?.reply || item?.snippet || ''));
          const explain = escapeHtml(String(item?.explain || '').trim());
          const sourceBadge = `${escapeHtml(source || 'source')}${scoreLabel ? ` · ${escapeHtml(scoreLabel)}` : ''}`;
          const rawSource = escapeHtml(String(item?.source || '').trim());
          const rawTitle = escapeHtml(String(item?.title || '').trim());
          const rawSnippet = escapeHtml(String(item?.snippet || '').trim());
          return `
            <article class="border rounded p-2">
              <div class="d-flex justify-content-between align-items-start gap-2">
                <div class="fw-semibold">${title}</div>
                <span class="badge text-bg-light border">${sourceBadge}</span>
              </div>
              ${snippet ? `<div class="small text-muted mt-1">${snippet}</div>` : ''}
              ${explain ? `<div class="small mt-1"><strong>Почему выбрано:</strong> ${explain}</div>` : ''}
              <div class="mt-2">
                <div class="btn-group btn-group-sm" role="group">
                  <button type="button" class="btn btn-outline-primary" data-ai-suggestion-apply="${index}" data-ai-suggestion-reply="${reply}">Вставить как есть</button>
                  <button type="button" class="btn btn-outline-secondary" data-ai-suggestion-apply-edit="${index}" data-ai-suggestion-reply="${reply}">Вставить и отредактировать</button>
                  <button type="button" class="btn btn-outline-danger" data-ai-suggestion-reject="${index}" data-ai-suggestion-source="${rawSource}" data-ai-suggestion-title="${rawTitle}" data-ai-suggestion-snippet="${rawSnippet}" data-ai-suggestion-reply="${reply}">Отклонить</button>
                </div>
              </div>
            </article>
          `;
        }).join('');
        elements.workspaceAiSuggestionsList.classList.remove('d-none');
      } catch (error) {
        elements.workspaceAiSuggestionsState.textContent = `Failed to load suggestions: ${error.message || 'unknown_error'}`;
      }
    }

    async function loadDetailsAiSuggestions(ticketId) {
      if (!elements.detailsAiState || !elements.detailsAiList) {
        return;
      }
      const normalizedTicketId = String(ticketId || '').trim();
      if (!normalizedTicketId) {
        elements.detailsAiState.textContent = 'Откройте диалог для загрузки подсказок.';
        elements.detailsAiList.classList.add('d-none');
        elements.detailsAiList.innerHTML = '';
        return;
      }
      elements.detailsAiState.textContent = 'Загрузка подсказок AI...';
      elements.detailsAiList.classList.add('d-none');
      elements.detailsAiList.innerHTML = '';
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-suggestions?limit=3`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || (`HTTP ${resp.status}`));
        }
        const items = Array.isArray(payload.items) ? payload.items : [];
        if (!items.length) {
          elements.detailsAiState.textContent = payload.processing
            ? 'AI обрабатывает диалог, подсказки скоро появятся.'
            : 'Пока нет подсказок для этого диалога.';
          return;
        }
        elements.detailsAiState.textContent = payload.processing
          ? 'AI обрабатывает диалог. Подсказки уже доступны:'
          : 'Подсказки AI для оператора:';
        elements.detailsAiList.innerHTML = items.map((item, index) => {
          const title = escapeHtml(String(item?.title || `Вариант ${index + 1}`));
          const explain = escapeHtml(String(item?.explain || ''));
          const reply = String(item?.reply || item?.snippet || '').trim();
          const replyEscaped = escapeHtml(reply);
          return `
            <article class="border rounded p-2">
              <div class="fw-semibold small mb-1">${title}</div>
              ${explain ? `<div class="small text-muted mb-1">${explain}</div>` : ''}
              <div class="small mb-2">${replyEscaped || '—'}</div>
              <button class="btn btn-sm btn-outline-primary" type="button" data-details-ai-insert="${replyEscaped}">Вставить в ответ</button>
            </article>
          `;
        }).join('');
        elements.detailsAiList.classList.remove('d-none');
      } catch (error) {
        elements.detailsAiState.textContent = `Не удалось загрузить подсказки: ${error.message || 'unknown_error'}`;
      }
    }

    async function sendAiSuggestionFeedback(ticketId, payload) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!normalizedTicketId) {
        return false;
      }
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-suggestions/feedback`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload || {}),
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok || data.success === false) {
        throw new Error(data.error || `HTTP ${resp.status}`);
      }
      return true;
    }

    async function loadWorkspaceAiControl(ticketId) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!elements.workspaceAiControlState || !elements.workspaceAiDisableForDialog || !elements.workspaceAiHandoffNoAutoReply) {
        return;
      }
      if (!normalizedTicketId) {
        elements.workspaceAiControlState.textContent = 'Откройте диалог, чтобы управлять AI.';
        elements.workspaceAiDisableForDialog.disabled = true;
        elements.workspaceAiHandoffNoAutoReply.disabled = true;
        return;
      }
      elements.workspaceAiControlState.textContent = 'Загрузка настроек AI для диалога...';
      elements.workspaceAiDisableForDialog.disabled = true;
      elements.workspaceAiHandoffNoAutoReply.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-control`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || `HTTP ${resp.status}`);
        }
        const control = payload.control || {};
        const disabled = control.ai_disabled === true;
        const blocked = control.auto_reply_blocked === true;
        elements.workspaceAiDisableForDialog.textContent = disabled ? 'Включить AI для диалога' : 'Отключить AI для диалога';
        elements.workspaceAiControlState.textContent = disabled
          ? 'AI is fully disabled for this dialog.'
          : (blocked ? 'AI auto-reply is disabled for this dialog. Suggestions remain available.' : 'AI runs in standard mode.');
        elements.workspaceAiDisableForDialog.disabled = false;
        elements.workspaceAiHandoffNoAutoReply.disabled = false;
      } catch (error) {
        elements.workspaceAiControlState.textContent = `Failed to load AI control state: ${error.message || 'unknown_error'}`;
      }
    }

    async function updateWorkspaceAiControl(ticketId, controlPayload, successMessage) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!normalizedTicketId) {
        return;
      }
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-control`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(controlPayload || {}),
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || `HTTP ${resp.status}`);
      }
      if (successMessage) {
        notify(successMessage, 'success');
      }
      await loadWorkspaceAiControl(normalizedTicketId);
    }

    function renderAiReviewQueueRows(items) {
      if (!elements.aiReviewQueueBody) {
        return;
      }
      if (!Array.isArray(items) || items.length === 0) {
        elements.aiReviewQueueBody.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Очередь ревизий пуста.</td></tr>';
        return;
      }
      elements.aiReviewQueueBody.innerHTML = items.map((item) => {
        const queryKey = String(item?.query_key || '').trim();
        const ticketId = String(item?.last_ticket_id || '').trim();
        const question = escapeHtml(String(item?.query_text || '').trim() || '—');
        const current = escapeHtml(String(item?.solution_text || '').trim() || '—');
        const pending = escapeHtml(String(item?.pending_solution_text || '').trim() || '—');
        const dialogLabel = ticketId ? `#${escapeHtml(ticketId)}` : '—';
        return `
          <tr data-ai-review-query-key="${escapeHtml(queryKey)}" data-ai-review-ticket-id="${escapeHtml(ticketId)}">
            <td class="small">${question}</td>
            <td class="small text-muted">${current}</td>
            <td class="small">${pending}</td>
            <td>${dialogLabel}</td>
            <td class="text-end">
              <div class="btn-group btn-group-sm" role="group">
                <button class="btn btn-outline-primary" type="button" data-ai-review-open ${ticketId ? '' : 'disabled'}>Открыть</button>
                <button class="btn btn-success" type="button" data-ai-review-approve>Принять</button>
                <button class="btn btn-outline-secondary" type="button" data-ai-review-reject>Отклонить</button>
              </div>
            </td>
          </tr>
        `;
      }).join('');
    }

    async function loadAiReviewQueue() {
      if (!elements.aiReviewQueueSection || !elements.aiReviewQueueState || !elements.aiReviewQueueBody) {
        return;
      }
      elements.aiReviewQueueState.textContent = 'Загрузка очереди ревизий…';
      try {
        const resp = await fetch('/api/dialogs/ai-reviews?limit=30', {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || (`HTTP ${resp.status}`));
        }
        const items = Array.isArray(payload.items) ? payload.items : [];
        elements.aiReviewQueueState.textContent = items.length
          ? `Найдено ревизий: ${items.length}`
          : 'Очередь ревизий пуста.';
        renderAiReviewQueueRows(items);
      } catch (error) {
        elements.aiReviewQueueState.textContent = `Не удалось загрузить очередь ревизий: ${error.message || 'unknown_error'}`;
        elements.aiReviewQueueBody.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Ошибка загрузки очереди ревизий.</td></tr>';
      }
    }

    function formatRatePercent(value) {
      const numeric = Number(value);
      if (!Number.isFinite(numeric)) {
        return '—';
      }
      return `${(numeric * 100).toFixed(1)}%`;
    }

    function renderAiMonitoringAlerts(alerts) {
      if (!elements.aiMonitoringAlerts) {
        return;
      }
      if (!Array.isArray(alerts) || alerts.length === 0) {
        elements.aiMonitoringAlerts.innerHTML = '<div class="text-muted">Алертов нет.</div>';
        return;
      }
      elements.aiMonitoringAlerts.innerHTML = alerts.map((alert) => {
        const severity = String(alert?.severity || 'info').trim().toLowerCase();
        const cls = severity === 'warning'
          ? 'alert alert-warning py-2 px-3 mb-2'
          : (severity === 'ok' ? 'alert alert-success py-2 px-3 mb-2' : 'alert alert-info py-2 px-3 mb-2');
        const message = escapeHtml(String(alert?.message || 'AI alert'));
        const formattedValue = formatRatePercent(alert?.value);
        const threshold = formatRatePercent(alert?.threshold);
        return `<div class="${cls}"><div>${message}</div><div class="small text-muted">value: ${formattedValue} / threshold: ${threshold}</div></div>`;
      }).join('');
    }

    function renderAiMonitoringRunbook(items) {
      if (!elements.aiMonitoringRunbook) {
        return;
      }
      if (!Array.isArray(items) || !items.length) {
        elements.aiMonitoringRunbook.innerHTML = '<li>Runbook недоступен.</li>';
        return;
      }
      elements.aiMonitoringRunbook.innerHTML = items.map((item) => `<li>${escapeHtml(String(item || ''))}</li>`).join('');
    }

    function renderAiMonitoringEvents(items) {
      if (!elements.aiMonitoringEvents) {
        return;
      }
      if (!Array.isArray(items) || !items.length) {
        elements.aiMonitoringEvents.innerHTML = '<div class="text-muted">Событий нет.</div>';
        return;
      }
      elements.aiMonitoringEvents.innerHTML = items.slice(0, 12).map((item) => {
        const createdAt = formatUtcDate(item?.created_at, { includeTime: true });
        const eventType = escapeHtml(String(item?.event_type || 'event'));
        const ticketId = escapeHtml(String(item?.ticket_id || '-'));
        const reason = escapeHtml(String(item?.decision_reason || item?.detail || ''));
        const source = escapeHtml(String(item?.source || ''));
        const actor = escapeHtml(String(item?.actor || 'system'));
        return `<div class="border rounded p-2 mb-1">
          <div class="d-flex flex-wrap justify-content-between gap-2">
            <span class="fw-semibold">${eventType}</span>
            <span class="text-muted">${createdAt}</span>
          </div>
          <div class="text-muted">ticket: ${ticketId} | actor: ${actor}${source ? ` | source: ${source}` : ''}</div>
          ${reason ? `<div>${reason}</div>` : ''}
        </div>`;
      }).join('');
    }

    function buildAiMonitoringEventsQuery(days = 7, limit = 50) {
      const params = new URLSearchParams();
      const filters = getAiMonitoringFilters();
      params.set('days', String(days));
      params.set('limit', String(limit));
      if (filters.eventType) {
        params.set('eventType', filters.eventType);
      }
      if (filters.actor) {
        params.set('actor', filters.actor);
      }
      return params.toString();
    }

    async function loadAiMonitoringEvents(days = 7, limit = 50) {
      if (!elements.aiMonitoringEvents) {
        return;
      }
      try {
        const resp = await fetch(`/api/dialogs/ai-monitoring/events?${buildAiMonitoringEventsQuery(days, limit)}`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || `HTTP ${resp.status}`);
        }
        renderAiMonitoringEvents(Array.isArray(payload.items) ? payload.items : []);
      } catch (_error) {
        renderAiMonitoringEvents([]);
      }
    }

    function exportAiMonitoringEventsCsv(days = 7, limit = 200) {
      const query = buildAiMonitoringEventsQuery(days, limit);
      const href = `/api/dialogs/ai-monitoring/events?${query}&format=csv`;
      const link = document.createElement('a');
      link.href = href;
      link.download = `ai-monitoring-events-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(link);
      link.click();
      link.remove();
    }

    async function loadAiMonitoringSummary(days = 7) {
      if (!elements.aiMonitoringSection || !elements.aiMonitoringState) {
        return;
      }
      elements.aiMonitoringState.textContent = 'Загрузка AI-сводки';
      try {
        const resp = await fetch(`/api/dialogs/ai-monitoring/summary?days=${encodeURIComponent(days)}`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          throw new Error(payload.error || `HTTP ${resp.status}`);
        }
        const summary = payload.summary || {};
        const kpis = summary.kpis || {};
        if (elements.aiKpiAutoReplyRate) elements.aiKpiAutoReplyRate.textContent = formatRatePercent(kpis.auto_reply_rate);
        if (elements.aiKpiAssistRate) elements.aiKpiAssistRate.textContent = formatRatePercent(kpis.assist_usage_rate);
        if (elements.aiKpiEscalationRate) elements.aiKpiEscalationRate.textContent = formatRatePercent(kpis.escalation_rate);
        if (elements.aiKpiCorrectionRate) elements.aiKpiCorrectionRate.textContent = formatRatePercent(kpis.correction_rate);
        renderAiMonitoringAlerts(summary.alerts);
        renderAiMonitoringRunbook(summary.runbook?.items);
        await loadAiMonitoringEvents(days, 50);
        const windowDays = Number(summary.window_days || days);
        elements.aiMonitoringState.textContent = `Окно: ${Number.isFinite(windowDays) ? windowDays : days} дн / обновлено ${formatUtcDate(summary.generated_at, { includeTime: true })}`;
      } catch (error) {
        elements.aiMonitoringState.textContent = `Не удалось загрузить AI-сводку: ${error.message || 'unknown_error'}`;
        renderAiMonitoringAlerts([]);
        renderAiMonitoringRunbook([]);
        renderAiMonitoringEvents([]);
      }
    }

    function formatAiReviewMessageOption(item) {
      const text = String(item?.text || '').replace(/\s+/g, ' ').trim();
      const shortText = text.length > 120 ? `${text.slice(0, 117)}...` : text;
      const timestamp = formatUtcDate(item?.timestamp, { includeTime: true, fallback: '' });
      if (timestamp && shortText) return `${timestamp} · ${shortText}`;
      if (shortText) return shortText;
      if (timestamp) return timestamp;
      return 'Сообщение без текста';
    }

    function renderAiReviewMessageSelect(selectEl, candidates, selectedId, emptyLabel) {
      if (!selectEl) {
        return;
      }
      const list = Array.isArray(candidates) ? candidates : [];
      const selected = String(selectedId || '').trim();
      selectEl.innerHTML = '';
      if (!list.length) {
        const emptyOption = document.createElement('option');
        emptyOption.value = '';
        emptyOption.textContent = emptyLabel;
        selectEl.appendChild(emptyOption);
        selectEl.disabled = true;
        return;
      }
      list.forEach((item, index) => {
        const option = document.createElement('option');
        const id = Number(item?.id);
        option.value = Number.isFinite(id) && id > 0 ? String(id) : '';
        option.textContent = formatAiReviewMessageOption(item);
        if (selected && option.value === selected) {
          option.selected = true;
        } else if (!selected && index === list.length - 1) {
          option.selected = true;
        }
        selectEl.appendChild(option);
      });
      selectEl.disabled = false;
    }

    function resolveAiReviewSelectedMessageText(selectEl, candidates) {
      const selectedValue = String(selectEl?.value || '').trim();
      if (!selectedValue) {
        return '';
      }
      const list = Array.isArray(candidates) ? candidates : [];
      for (const item of list) {
        const id = Number(item?.id);
        if (Number.isFinite(id) && String(id) === selectedValue) {
          return String(item?.text || '').trim();
        }
      }
      return '';
    }

    async function loadWorkspaceAiReview(ticketId) {
      const normalizedTicketId = String(ticketId || '').trim();
      if (!elements.workspaceAiReviewBox) {
        return;
      }
      elements.workspaceAiReviewBox.classList.add('d-none');
      setWorkspaceAiReviewState([], []);
      renderAiReviewMessageSelect(elements.workspaceAiReviewProblemSelect, [], null, 'Нет сообщений клиента');
      renderAiReviewMessageSelect(elements.workspaceAiReviewSolutionSelect, [], null, 'Нет сообщений оператора');
      if (!normalizedTicketId) {
        return;
      }
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-review`, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) {
          return;
        }
        const review = payload.review || {};
        if (review.pending !== true) {
          return;
        }
        if (elements.workspaceAiReviewQuestion) {
          const question = String(review.query_text || '').trim();
          elements.workspaceAiReviewQuestion.textContent = `Для обучения AI подтвердите соответствие: какое сообщение клиента описывает проблему и какое сообщение оператора является решением. ${question}`;
        }
        const problemCandidates = Array.isArray(review.problem_message_candidates) ? review.problem_message_candidates : [];
        const solutionCandidates = Array.isArray(review.solution_message_candidates) ? review.solution_message_candidates : [];
        setWorkspaceAiReviewState(problemCandidates, solutionCandidates);
        renderAiReviewMessageSelect(
          elements.workspaceAiReviewProblemSelect,
          problemCandidates,
          review.selected_problem_message_id,
          'Нет сообщений клиента'
        );
        renderAiReviewMessageSelect(
          elements.workspaceAiReviewSolutionSelect,
          solutionCandidates,
          review.selected_solution_message_id,
          'Нет сообщений оператора'
        );
        if (elements.workspaceAiReviewCurrent) {
          elements.workspaceAiReviewCurrent.textContent = String(review.current_solution || '').trim() || '—';
        }
        if (elements.workspaceAiReviewPending) {
          elements.workspaceAiReviewPending.textContent = String(review.pending_solution || '').trim() || '—';
        }
        elements.workspaceAiReviewBox.classList.remove('d-none');
      } catch (_error) {
        setWorkspaceAiReviewState([], []);
      }
    }

    return {
      loadWorkspaceAiSuggestions,
      loadDetailsAiSuggestions,
      sendAiSuggestionFeedback,
      loadWorkspaceAiControl,
      updateWorkspaceAiControl,
      renderAiReviewQueueRows,
      loadAiReviewQueue,
      formatRatePercent,
      renderAiMonitoringAlerts,
      renderAiMonitoringRunbook,
      renderAiMonitoringEvents,
      buildAiMonitoringEventsQuery,
      loadAiMonitoringEvents,
      exportAiMonitoringEventsCsv,
      loadAiMonitoringSummary,
      formatAiReviewMessageOption,
      renderAiReviewMessageSelect,
      resolveAiReviewSelectedMessageText,
      loadWorkspaceAiReview,
    };
  }

  window.DialogsAiRuntime = {
    createRuntime,
  };
})();

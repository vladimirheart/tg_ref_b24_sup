(function () {
  if (window.DialogsExperimentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      refreshTimer: null,
      refreshControlsBound: false,
    };

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function formatTimestamp(value, formatOptions = {}) {
      return typeof options.formatTimestamp === 'function'
        ? options.formatTimestamp(value, formatOptions)
        : String(value || formatOptions.fallback || '—');
    }

    function formatWorkspaceDateTime(value) {
      return typeof options.formatWorkspaceDateTime === 'function'
        ? options.formatWorkspaceDateTime(value)
        : formatTimestamp(value, { includeTime: true, fallback: '—' });
    }

    function getWorkspaceAbTestConfig() {
      return options.workspaceAbTestConfig && typeof options.workspaceAbTestConfig === 'object'
        ? options.workspaceAbTestConfig
        : {
          experimentName: 'workspace',
          primaryKpis: [],
          secondaryKpis: [],
        };
    }

    function getWorkspaceExperimentContext() {
      return options.workspaceExperimentContext && typeof options.workspaceExperimentContext === 'object'
        ? options.workspaceExperimentContext
        : {
          cohort: 'control',
          operatorSegment: 'unknown',
        };
    }

    function renderExperimentKpiItems(container, items) {
      if (!container) return;
      const safeItems = Array.isArray(items) && items.length ? items : ['—'];
      container.innerHTML = safeItems.map((item) => `<li>${escapeHtml(String(item))}</li>`).join('');
    }

    function renderExperimentInfoPanel() {
      const config = getWorkspaceAbTestConfig();
      const context = getWorkspaceExperimentContext();
      if (elements.experimentInfoMeta) {
        elements.experimentInfoMeta.textContent = `Эксперимент: ${config.experimentName} · когорта: ${context.cohort} · сегмент: ${context.operatorSegment}`;
      }
      renderExperimentKpiItems(elements.experimentPrimaryKpis, config.primaryKpis);
      renderExperimentKpiItems(elements.experimentSecondaryKpis, config.secondaryKpis);
    }

    function formatGuardrailPercent(value) {
      const safe = Number(value);
      if (!Number.isFinite(safe)) return '0.00%';
      return `${(safe * 100).toFixed(2)}%`;
    }

    function formatDeltaPercent(value) {
      const safe = Number(value);
      if (!Number.isFinite(safe)) return '0.00 п.п.';
      const sign = safe > 0 ? '+' : '';
      return `${sign}${(safe * 100).toFixed(2)} п.п.`;
    }

    function formatDeltaMs(value) {
      const safe = Number(value);
      if (!Number.isFinite(safe)) return '—';
      const sign = safe > 0 ? '+' : '';
      return `${sign}${Math.round(safe)}мс`;
    }

    function renderExperimentTelemetryGuardrails(guardrails) {
      if (!elements.experimentTelemetryGuardrailState || !elements.experimentTelemetryGuardrailAlerts) return;
      if (!guardrails || typeof guardrails !== 'object') {
        elements.experimentTelemetryGuardrailState.classList.add('d-none');
        elements.experimentTelemetryGuardrailState.textContent = '';
        elements.experimentTelemetryGuardrailAlerts.classList.add('d-none');
        elements.experimentTelemetryGuardrailAlerts.innerHTML = '';
        return;
      }
      const status = String(guardrails?.status || 'ok').toLowerCase();
      const rates = guardrails?.rates || {};
      const alerts = Array.isArray(guardrails?.alerts) ? guardrails.alerts : [];

      elements.experimentTelemetryGuardrailState.classList.remove('d-none', 'alert-success', 'alert-warning');
      elements.experimentTelemetryGuardrailAlerts.classList.add('d-none');
      elements.experimentTelemetryGuardrailAlerts.innerHTML = '';

      const summary = `SLO: render_error ${formatGuardrailPercent(rates.render_error)} / fallback ${formatGuardrailPercent(rates.fallback)} / abandon ${formatGuardrailPercent(rates.abandon)} / slow_open ${formatGuardrailPercent(rates.slow_open)}.`;
      if (status === 'attention') {
        elements.experimentTelemetryGuardrailState.classList.add('alert-warning');
        elements.experimentTelemetryGuardrailState.textContent = `Найдены отклонения guardrails. ${summary}`;
        if (alerts.length) {
          elements.experimentTelemetryGuardrailAlerts.classList.remove('d-none');
          elements.experimentTelemetryGuardrailAlerts.innerHTML = alerts.map((alert) => {
            const message = String(alert?.message || 'Отклонение метрики');
            const value = formatGuardrailPercent(alert?.value);
            const threshold = formatGuardrailPercent(alert?.threshold);
            const scope = String(alert?.scope || '').trim();
            const segment = String(alert?.segment || '').trim();
            const events = Number(alert?.events || 0);
            const previousValue = Number(alert?.previous_value);
            const delta = Number(alert?.delta);
            const scopeMeta = scope && segment
              ? ` · срез: ${scope}=${segment}${events > 0 ? ` · событий: ${events}` : ''}`
              : '';
            const previousMeta = Number.isFinite(previousValue)
              ? ` · предыдущее окно: ${escapeHtml(formatGuardrailPercent(previousValue))}`
              : '';
            const deltaMeta = Number.isFinite(delta)
              ? ` · delta: ${escapeHtml(formatDeltaPercent(delta))}`
              : '';
            return `<li>${escapeHtml(message)} (факт: ${escapeHtml(value)} · порог: ${escapeHtml(threshold)}${previousMeta}${deltaMeta}${escapeHtml(scopeMeta)})</li>`;
          }).join('');
        }
        return;
      }

      elements.experimentTelemetryGuardrailState.classList.add('alert-success');
      elements.experimentTelemetryGuardrailState.textContent = `Guardrails в норме. ${summary}`;
    }

    function formatRolloutDecisionAction(action) {
      const safe = String(action || 'hold').trim().toLowerCase();
      if (safe === 'scale_up') return 'scale_up';
      if (safe === 'rollback') return 'rollback';
      return 'hold';
    }

    function formatKpiOutcomeDelta(metricName, value) {
      const safe = Number(value);
      if (!Number.isFinite(safe)) return '—';
      const isPercentMetric = String(metricName || '').toLowerCase() === 'sla_breach';
      if (isPercentMetric) {
        return formatDeltaPercent(safe);
      }
      const sign = safe > 0 ? '+' : '';
      return `${sign}${Math.round(safe)}мс`;
    }

    function renderExperimentRolloutPacket(packet) {
      if (!elements.experimentRolloutPacketState
          || !elements.experimentRolloutPacketChecklist
          || !elements.experimentRolloutPacketWrap
          || !elements.experimentRolloutPacketRows) {
        return;
      }
      const safePacket = packet && typeof packet === 'object' ? packet : null;
      if (!safePacket) {
        elements.experimentRolloutPacketState.classList.add('d-none');
        elements.experimentRolloutPacketState.textContent = '';
        elements.experimentRolloutPacketChecklist.classList.add('d-none');
        elements.experimentRolloutPacketChecklist.innerHTML = '';
        elements.experimentRolloutPacketWrap.classList.add('d-none');
        elements.experimentRolloutPacketRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Governance packet появится после первых telemetry-сигналов.</td></tr>';
        return;
      }

      const status = String(safePacket?.status || 'attention').trim().toLowerCase();
      const required = Boolean(safePacket?.required);
      const packetReady = Boolean(safePacket?.packet_ready);
      const summary = String(safePacket?.summary || '').trim() || 'Governance packet загружен.';
      const decisionAction = String(safePacket?.decision_action || 'hold').trim().toUpperCase();
      const generatedAt = formatTimestamp(safePacket?.generated_at, { includeTime: true, fallback: '—' });
      const blockingCount = Math.max(0, Number(safePacket?.blocking_count || 0));
      const attentionCount = Math.max(0, Number(safePacket?.attention_count || 0));
      const invalidUtcItems = Array.isArray(safePacket?.invalid_utc_items) ? safePacket.invalid_utc_items : [];
      const missingItems = Array.isArray(safePacket?.missing_items) ? safePacket.missing_items : [];
      const legacyOnlyScenarios = Array.isArray(safePacket?.legacy_only_scenarios) ? safePacket.legacy_only_scenarios : [];
      const legacyInventory = safePacket?.legacy_only_inventory && typeof safePacket.legacy_only_inventory === 'object'
        ? safePacket.legacy_only_inventory
        : {};
      const contextContract = safePacket?.context_contract && typeof safePacket.context_contract === 'object'
        ? safePacket.context_contract
        : {};
      const nextReviewAt = formatTimestamp(safePacket?.next_review_at_utc, { includeTime: true, fallback: '' });

      elements.experimentRolloutPacketState.classList.remove('d-none', 'alert-success', 'alert-warning', 'alert-danger', 'alert-secondary');
      if (status === 'ok') {
        elements.experimentRolloutPacketState.classList.add('alert-success');
      } else if (status === 'hold') {
        elements.experimentRolloutPacketState.classList.add('alert-danger');
      } else if (status === 'off') {
        elements.experimentRolloutPacketState.classList.add('alert-secondary');
      } else {
        elements.experimentRolloutPacketState.classList.add('alert-warning');
      }
      elements.experimentRolloutPacketState.textContent = `Governance packet: ${status.toUpperCase()} · decision ${decisionAction} · blocking ${blockingCount} · attention ${attentionCount} · generated ${generatedAt}. ${summary}`;
      elements.experimentRolloutPacketState.classList.remove('d-none');

      const checks = [
        { ok: packetReady, label: required ? 'Полный governance packet собран' : 'Governance packet не блокирует rollout' },
        { ok: missingItems.length === 0, label: missingItems.length ? `Пропущенные элементы: ${missingItems.join(', ')}` : 'Нет пропущенных элементов пакета' },
        { ok: invalidUtcItems.length === 0, label: invalidUtcItems.length ? `UTC-ошибки: ${invalidUtcItems.join(', ')}` : 'UTC-метки governance валидны' },
        { ok: legacyOnlyScenarios.length === 0, label: legacyOnlyScenarios.length ? `Legacy-only inventory открыт: ${legacyOnlyScenarios.join(', ')}` : 'Legacy-only inventory пуст' },
      ];
      if (legacyOnlyScenarios.length) {
        const legacyActionItems = Array.isArray(legacyInventory?.action_items) ? legacyInventory.action_items : [];
        const overdue = Number(legacyInventory?.deadline_overdue_count || 0);
        const reviewQueueScenarios = Array.isArray(legacyInventory?.review_queue_scenarios) ? legacyInventory.review_queue_scenarios : [];
        const overdueScenarios = Array.isArray(legacyInventory?.overdue_scenarios) ? legacyInventory.overdue_scenarios : [];
        checks.push({
          ok: overdue === 0,
          label: overdue > 0
            ? `Sunset commitments overdue: ${overdue}${legacyActionItems.length ? ` · ${legacyActionItems[0]}` : ''}`
            : `Owner/deadline coverage ${Number(legacyInventory?.owner_coverage_pct || 0)}% / ${Number(legacyInventory?.deadline_coverage_pct || 0)}%`
        });
        if (reviewQueueScenarios.length) {
          checks.push({
            ok: legacyInventory?.review_queue_followup_required !== true,
            label: legacyInventory?.review_queue_summary
              ? String(legacyInventory.review_queue_summary)
              : `Повторно остаются в legacy review-queue: ${reviewQueueScenarios.slice(0, 3).join(', ')}${reviewQueueScenarios.length > 3 ? ` +${reviewQueueScenarios.length - 3}` : ''}`
          });
        }
        if (legacyInventory?.review_queue_escalation_required === true) {
          const escalatedScenarios = Array.isArray(legacyInventory?.review_queue_escalated_scenarios) ? legacyInventory.review_queue_escalated_scenarios : [];
          checks.push({
            ok: false,
            label: `Legacy queue escalation (${String(legacyInventory?.review_queue_closure_pressure || 'high')}${Number(legacyInventory?.review_queue_escalated_count || 0) > 0 ? `, mgmt ${Number(legacyInventory?.review_queue_escalated_count || 0)}` : ''}): ${escalatedScenarios.length ? escalatedScenarios.join(', ') : 'management review required'}`
          });
        }
        if (Number(legacyInventory?.review_queue_consolidation_count || 0) > 0) {
          const consolidationCandidates = Array.isArray(legacyInventory?.review_queue_consolidation_candidates) ? legacyInventory.review_queue_consolidation_candidates : [];
          checks.push({
            ok: false,
            label: `Legacy queue consolidation: ${consolidationCandidates.length ? consolidationCandidates.join(', ') : `${Number(legacyInventory?.review_queue_consolidation_count || 0)} сценария(ев)`}`
          });
        }
        if (overdueScenarios.length) {
          checks.push({
            ok: false,
            label: `Overdue scenarios: ${overdueScenarios.join(', ')}`
          });
        }
        if (legacyInventory?.repeat_review_required === true) {
          checks.push({
            ok: false,
            label: `Повторный legacy review обязателен (${String(legacyInventory?.repeat_review_reason || 'review_due')})${legacyInventory?.repeat_review_due_at_utc ? ` · due ${formatTimestamp(legacyInventory.repeat_review_due_at_utc, { includeTime: true, fallback: '—' })}` : ''}`
          });
        }
      }
      if (contextContract?.enabled === true) {
        const contextActionItems = Array.isArray(contextContract?.action_items) ? contextContract.action_items : [];
        const focusBlocks = Array.isArray(contextContract?.operator_focus_blocks) ? contextContract.operator_focus_blocks : [];
        const operatorSummary = String(contextContract?.operator_summary || '').trim();
        const nextStepSummary = String(contextContract?.next_step_summary || '').trim();
        checks.push({
          ok: contextContract?.ready === true,
          label: contextContract?.ready === true
            ? `Context contract ready${focusBlocks.length ? ` · operator first: ${focusBlocks.join(', ')}` : ''}`
            : (nextStepSummary || operatorSummary || contextActionItems[0] || 'Context contract требует action-oriented follow-up')
        });
        if (contextContract?.extra_attributes_compaction_candidate === true) {
          checks.push({
            ok: contextContract?.secondary_noise_management_review_required !== true,
            label: String(contextContract?.secondary_noise_compaction_summary || contextContract?.extra_attributes_summary || 'Extra attributes требуют compaction review.')
          });
        }
      }
      if (nextReviewAt) {
        checks.push({ ok: true, label: `Следующий review due UTC: ${nextReviewAt}` });
      }
      elements.experimentRolloutPacketChecklist.classList.remove('d-none');
      elements.experimentRolloutPacketChecklist.innerHTML = checks.map((item) => (`<li>${item.ok ? '✅' : '⚠️'} ${escapeHtml(item.label)}</li>`)).join('');

      const items = Array.isArray(safePacket?.items) ? safePacket.items : [];
      if (!items.length) {
        elements.experimentRolloutPacketWrap.classList.add('d-none');
        elements.experimentRolloutPacketRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Governance packet появится после первых telemetry-сигналов.</td></tr>';
        return;
      }

      elements.experimentRolloutPacketWrap.classList.remove('d-none');
      elements.experimentRolloutPacketRows.innerHTML = items.map((item) => {
        const itemStatus = String(item?.status || 'attention').trim().toLowerCase();
        const badge = itemStatus === 'ok'
          ? '<span class="badge text-bg-success">ok</span>'
          : (itemStatus === 'attention'
            ? '<span class="badge text-bg-warning">attention</span>'
            : (itemStatus === 'off'
              ? '<span class="badge text-bg-secondary">off</span>'
              : '<span class="badge text-bg-danger">hold</span>'));
        const note = String(item?.note || item?.summary || '').trim();
        return `
        <tr>
          <td>
            <div>${escapeHtml(String(item?.label || '—'))}</div>
            ${note ? `<div class="small text-muted">${escapeHtml(note)}</div>` : ''}
          </td>
          <td>${escapeHtml(String(item?.category || '—'))}</td>
          <td>${escapeHtml(String(item?.current_value || '—'))}</td>
          <td>${escapeHtml(String(item?.threshold || '—'))}</td>
          <td>${escapeHtml(formatTimestamp(item?.measured_at, { includeTime: true, fallback: '—' }))}</td>
          <td class="text-end">${badge}</td>
        </tr>
      `;
      }).join('');
    }

    function renderExperimentGapBreakdown(breakdown) {
      if (!elements.experimentGapBreakdownWrap || !elements.experimentGapBreakdownRows) return;
      const safeBreakdown = breakdown && typeof breakdown === 'object' ? breakdown : null;
      const categoryLabels = {
        profile: 'profile',
        source: 'source',
        attribute_policy: 'attribute_policy',
        block: 'block',
        contract: 'contract',
        parity: 'parity',
      };
      const rows = [];
      Object.entries(categoryLabels).forEach(([key, label]) => {
        const items = Array.isArray(safeBreakdown?.[key]) ? safeBreakdown[key] : [];
        items.forEach((item) => rows.push({ category: label, ...item }));
      });
      if (!rows.length) {
        elements.experimentGapBreakdownWrap.classList.add('d-none');
        elements.experimentGapBreakdownRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Gap breakdown появится после первых parity/context-gap событий.</td></tr>';
        return;
      }
      elements.experimentGapBreakdownWrap.classList.remove('d-none');
      elements.experimentGapBreakdownRows.innerHTML = rows.map((item) => `
      <tr>
        <td>${escapeHtml(String(item?.category || 'unknown'))}</td>
        <td>${escapeHtml(String(item?.reason || 'unspecified'))}</td>
        <td class="text-end">${escapeHtml(String(Number(item?.events || 0)))}</td>
        <td class="text-end">${escapeHtml(String(Number(item?.tickets || 0)))}</td>
        <td>${escapeHtml(formatTimestamp(item?.last_seen_at, { includeTime: true, fallback: '—' }))}</td>
      </tr>
    `).join('');
    }

    function renderExperimentRolloutScorecard(scorecard) {
      if (!elements.experimentRolloutScorecardWrap || !elements.experimentRolloutScorecardRows) return;
      const items = Array.isArray(scorecard?.items) ? scorecard.items : [];
      if (!items.length) {
        elements.experimentRolloutScorecardWrap.classList.add('d-none');
        elements.experimentRolloutScorecardRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Rollout scorecard появится после первых telemetry-сигналов.</td></tr>';
        return;
      }

      elements.experimentRolloutScorecardWrap.classList.remove('d-none');
      elements.experimentRolloutScorecardRows.innerHTML = items.map((item) => {
        const status = String(item?.status || 'hold').trim().toLowerCase();
        const badge = status === 'ok'
          ? '<span class="badge text-bg-success">ok</span>'
          : (status === 'attention'
            ? '<span class="badge text-bg-warning">attention</span>'
            : (status === 'off'
              ? '<span class="badge text-bg-secondary">off</span>'
              : '<span class="badge text-bg-danger">hold</span>'));
        const note = String(item?.note || item?.summary || '').trim();
        return `
        <tr>
          <td>
            <div>${escapeHtml(String(item?.label || '—'))}</div>
            ${note ? `<div class="small text-muted">${escapeHtml(note)}</div>` : ''}
          </td>
          <td>${escapeHtml(String(item?.category || '—'))}</td>
          <td>${escapeHtml(String(item?.current_value || '—'))}</td>
          <td>${escapeHtml(String(item?.threshold || '—'))}</td>
          <td>${escapeHtml(formatTimestamp(item?.measured_at, { includeTime: true, fallback: '—' }))}</td>
          <td class="text-end">${badge}</td>
        </tr>
      `;
      }).join('');
    }

    function renderExperimentRolloutDecision(decision, cohortComparison) {
      if (!elements.experimentRolloutDecisionState
          || !elements.experimentRolloutDecisionChecklist
          || !elements.experimentRolloutKpiOutcomesWrap
          || !elements.experimentRolloutKpiOutcomeRows) {
        return;
      }
      const safeDecision = decision && typeof decision === 'object' ? decision : null;
      const safeComparison = cohortComparison && typeof cohortComparison === 'object' ? cohortComparison : null;
      if (!safeDecision) {
        elements.experimentRolloutDecisionState.classList.add('d-none');
        elements.experimentRolloutDecisionState.textContent = '';
        elements.experimentRolloutDecisionChecklist.classList.add('d-none');
        elements.experimentRolloutDecisionChecklist.innerHTML = '';
        elements.experimentRolloutKpiOutcomesWrap.classList.add('d-none');
        elements.experimentRolloutKpiOutcomeRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Данные появятся после первых KPI-сигналов.</td></tr>';
        return;
      }

      const action = formatRolloutDecisionAction(safeDecision?.action);
      const winner = String(safeDecision?.winner || 'insufficient_data');
      const rationale = String(safeDecision?.rationale || 'Решение будет принято после накопления данных.');

      elements.experimentRolloutDecisionState.classList.remove('d-none', 'alert-success', 'alert-warning', 'alert-danger');
      if (action === 'scale_up') {
        elements.experimentRolloutDecisionState.classList.add('alert-success');
      } else if (action === 'rollback') {
        elements.experimentRolloutDecisionState.classList.add('alert-danger');
      } else {
        elements.experimentRolloutDecisionState.classList.add('alert-warning');
      }
      elements.experimentRolloutDecisionState.textContent = `Rollout decision: ${action.toUpperCase()} · winner: ${winner}. ${rationale}`;

      const checks = [
        { ok: Boolean(safeDecision?.sample_size_ok), label: 'Достаточная выборка control/test' },
        { ok: Boolean(safeDecision?.kpi_signal_ready), label: 'Покрытие KPI-сигналов (FRT/TTR/SLA breach)' },
        { ok: Boolean(safeDecision?.kpi_outcome_ready), label: 'Готовность KPI-результатов к сравнению' },
        { ok: !Boolean(safeDecision?.kpi_outcome_regressions), label: 'Нет деградации product KPI в test cohort' },
      ];
      elements.experimentRolloutDecisionChecklist.classList.remove('d-none');
      elements.experimentRolloutDecisionChecklist.innerHTML = checks.map((item) => (`<li>${item.ok ? '✅' : '⚠️'} ${escapeHtml(item.label)}</li>`)).join('');

      const outcomeMetrics = safeComparison?.kpi_outcome_signal?.metrics;
      const metricEntries = outcomeMetrics && typeof outcomeMetrics === 'object' ? Object.entries(outcomeMetrics) : [];
      if (!metricEntries.length) {
        elements.experimentRolloutKpiOutcomesWrap.classList.add('d-none');
        elements.experimentRolloutKpiOutcomeRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Данные появятся после первых KPI-сигналов.</td></tr>';
        return;
      }
      elements.experimentRolloutKpiOutcomesWrap.classList.remove('d-none');
      elements.experimentRolloutKpiOutcomeRows.innerHTML = metricEntries.map(([metricName, metricPayload]) => {
        const metric = metricPayload && typeof metricPayload === 'object' ? metricPayload : {};
        const ready = Boolean(metric?.ready);
        const regression = Boolean(metric?.regression);
        const status = !ready ? 'waiting' : (regression ? 'regression' : 'ok');
        const statusBadge = status === 'ok'
          ? '<span class="badge text-bg-success">ok</span>'
          : (status === 'regression'
            ? '<span class="badge text-bg-danger">regression</span>'
            : '<span class="badge text-bg-secondary">waiting</span>');
        const controlValue = Number(metric?.control_value);
        const testValue = Number(metric?.test_value);
        const controlDisplay = Number.isFinite(controlValue)
          ? (String(metricName).toLowerCase() === 'sla_breach' ? formatGuardrailPercent(controlValue) : `${Math.round(controlValue)}мс`)
          : '—';
        const testDisplay = Number.isFinite(testValue)
          ? (String(metricName).toLowerCase() === 'sla_breach' ? formatGuardrailPercent(testValue) : `${Math.round(testValue)}мс`)
          : '—';
        return `
        <tr>
          <td>${escapeHtml(String(metricName))}</td>
          <td class="text-end">${escapeHtml(controlDisplay)}</td>
          <td class="text-end">${escapeHtml(testDisplay)}</td>
          <td class="text-end">${escapeHtml(formatKpiOutcomeDelta(metricName, metric?.delta))}</td>
          <td class="text-end">${statusBadge}</td>
        </tr>
      `;
      }).join('');
    }

    function renderExperimentTelemetrySummaryRows(rows) {
      if (!elements.experimentTelemetrySummaryRows) return;
      const safeRows = Array.isArray(rows) ? rows : [];
      if (!safeRows.length) {
        elements.experimentTelemetrySummaryRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Недостаточно telemetry-данных для расчёта.</td></tr>';
        return;
      }
      elements.experimentTelemetrySummaryRows.innerHTML = safeRows.map((row) => {
        const avgOpenMs = Number.isFinite(Number(row?.avg_open_ms)) ? Math.round(Number(row.avg_open_ms)) : '—';
        return `
        <tr>
          <td>${escapeHtml(String(row?.experiment_cohort || 'unknown'))}</td>
          <td>${escapeHtml(String(row?.operator_segment || 'unknown'))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.events || 0)))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.fallbacks || 0)))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.render_errors || 0)))}</td>
          <td class="text-end">${escapeHtml(String(avgOpenMs))}</td>
        </tr>
      `;
      }).join('');
    }

    function renderExperimentTelemetryDimensionRows(container, rows, dimensionKey) {
      if (!container) return;
      const safeRows = Array.isArray(rows) ? rows : [];
      if (!safeRows.length) {
        container.innerHTML = '<tr><td colspan="4" class="small text-muted">Недостаточно telemetry-данных для расчёта.</td></tr>';
        return;
      }
      container.innerHTML = safeRows.map((row) => {
        const events = Math.max(0, Number(row?.events || 0));
        const fallbackRate = events > 0 ? Number(row?.fallbacks || 0) / events : 0;
        const renderErrorRate = events > 0 ? Number(row?.render_errors || 0) / events : 0;
        const dimension = String(row?.[dimensionKey] || 'unknown');
        return `
        <tr>
          <td>${escapeHtml(dimension)}</td>
          <td class="text-end">${escapeHtml(String(events))}</td>
          <td class="text-end">${escapeHtml(formatGuardrailPercent(fallbackRate))}</td>
          <td class="text-end">${escapeHtml(formatGuardrailPercent(renderErrorRate))}</td>
        </tr>
      `;
      }).join('');
    }

    function clearExperimentTelemetryRefreshTimer() {
      if (!state.refreshTimer) return;
      window.clearInterval(state.refreshTimer);
      state.refreshTimer = null;
    }

    function syncExperimentTelemetryAutoRefresh() {
      clearExperimentTelemetryRefreshTimer();
      if (!elements.experimentTelemetryAutoRefresh || !elements.experimentTelemetryAutoRefresh.checked) {
        return;
      }
      state.refreshTimer = window.setInterval(() => {
        loadExperimentTelemetrySummary();
      }, 30000);
    }

    async function loadExperimentTelemetrySummary() {
      if (!elements.experimentTelemetrySummaryRows) return;
      if (elements.experimentTelemetrySummaryState) {
        elements.experimentTelemetrySummaryState.textContent = 'Загрузка агрегатов telemetry…';
      }
      try {
        const experimentName = encodeURIComponent(getWorkspaceAbTestConfig().experimentName);
        const response = await fetch(`/api/dialogs/workspace-telemetry/summary?days=7&experiment_name=${experimentName}`);
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const payload = await response.json();
        renderExperimentTelemetrySummaryRows(payload?.rows || []);
        renderExperimentTelemetryDimensionRows(elements.experimentTelemetryShiftRows, payload?.by_shift || [], 'shift');
        renderExperimentTelemetryDimensionRows(elements.experimentTelemetryTeamRows, payload?.by_team || [], 'team');
        renderExperimentTelemetryGuardrails(payload?.guardrails || {});
        renderExperimentRolloutPacket(payload?.rollout_packet || {});
        renderExperimentRolloutScorecard(payload?.rollout_scorecard || {});
        renderExperimentRolloutDecision(payload?.rollout_decision || {}, payload?.cohort_comparison || {});
        renderExperimentGapBreakdown(payload?.gap_breakdown || {});
        if (elements.experimentTelemetrySummaryState) {
          const totals = payload?.totals || {};
          const previousTotals = payload?.previous_totals || {};
          const comparison = payload?.period_comparison || {};
          const p1Control = payload?.p1_operational_control && typeof payload.p1_operational_control === 'object'
            ? payload.p1_operational_control
            : {};
          const p2Control = payload?.p2_governance_control && typeof payload.p2_governance_control === 'object'
            ? payload.p2_governance_control
            : {};
          const slaAudit = payload?.sla_policy_audit && typeof payload.sla_policy_audit === 'object'
            ? payload.sla_policy_audit
            : {};
          const slaReviewPathControl = payload?.sla_review_path_control && typeof payload.sla_review_path_control === 'object'
            ? payload.sla_review_path_control
            : {};
          const weeklyReviewFocus = payload?.weekly_review_focus && typeof payload.weekly_review_focus === 'object'
            ? payload.weekly_review_focus
            : {};
          const weeklyFocusSections = Array.isArray(weeklyReviewFocus?.sections) ? weeklyReviewFocus.sections : [];
          const avgCurrent = Number.isFinite(Number(totals.avg_open_ms)) ? `${Math.round(Number(totals.avg_open_ms))}мс` : '—';
          const avgPrevious = Number.isFinite(Number(previousTotals.avg_open_ms)) ? `${Math.round(Number(previousTotals.avg_open_ms))}мс` : '—';
          const generatedAt = formatWorkspaceDateTime(payload?.generated_at);
          elements.experimentTelemetrySummaryState.textContent = `События: ${Number(totals.events || 0)} (пред. период: ${Number(previousTotals.events || 0)}) · Fallback: ${Number(totals.fallbacks || 0)} · Legacy вручную: ${Number(totals.manual_legacy_open_events || 0)} · Legacy заблокировано: ${Number(totals.workspace_open_legacy_blocked_events || 0)} · Inline nav: ${Number(totals.workspace_inline_navigation_events || 0)} · Просмотры rollout packet: ${Number(totals.workspace_rollout_packet_viewed_events || 0)} · Открытия доп. контекста: ${Number(totals.context_secondary_details_expanded_events || 0)} (${Number(totals.context_secondary_details_open_rate_pct || 0)}%, ${String(totals.context_secondary_details_usage_level || 'rare')}${totals.context_secondary_details_top_section ? `, top ${String(totals.context_secondary_details_top_section)}` : ''})${Number(totals.context_extra_attributes_expanded_events || 0) > 0 ? ` · Доп. атрибуты: ${Number(totals.context_extra_attributes_expanded_events || 0)} (${Number(totals.context_extra_attributes_open_rate_pct || 0)}%, ${String(totals.context_extra_attributes_usage_level || 'rare')}${totals.context_extra_attributes_compaction_candidate === true ? `, compact, share ${Number(totals.context_extra_attributes_share_pct_of_secondary || 0)}%` : ''})` : ''} · P1 control: ${String(p1Control?.status || 'controlled')}${p1Control?.context_noise_trend_status ? ` (${String(p1Control.context_noise_trend_status)})` : ''}${p1Control?.next_action_summary ? ` · next ${String(p1Control.next_action_summary)}` : ''} · P2 control: ${String(p2Control?.status || 'controlled')}${p2Control?.sla_churn_trend_status ? ` (${String(p2Control.sla_churn_trend_status)})` : ''}${p2Control?.governance_closure_health ? ` · closure ${String(p2Control.governance_closure_health)}` : ''}${p2Control?.macro_noise_health ? ` · macro-noise ${String(p2Control.macro_noise_health)}` : ''}${p2Control?.next_action_summary ? ` · next ${String(p2Control.next_action_summary)}` : ''} · Source policy gaps: ${Number(totals.context_attribute_policy_gap_events || 0)} · Context contract gaps: ${Number(totals.context_contract_gap_events || 0)} · SLA policy gaps: ${Number(totals.workspace_sla_policy_gap_events || 0)} · SLA churn: ${Number(totals.workspace_sla_policy_churn_ratio_pct || 0)}% (${String(totals.workspace_sla_policy_churn_level || 'controlled')}) · SLA path: ${String(slaReviewPathControl?.status || 'controlled')}${slaReviewPathControl?.decision_lead_time_status ? ` (${String(slaReviewPathControl.decision_lead_time_status)})` : ''}${slaReviewPathControl?.minimum_required_review_path_summary ? ` · ${String(slaReviewPathControl.minimum_required_review_path_summary)}` : ''}${slaReviewPathControl?.cheap_review_path_confirmed === true ? ' · cheap path confirmed' : ''}${slaReviewPathControl?.next_action_summary ? ` · next ${String(slaReviewPathControl.next_action_summary)}` : ''}${slaAudit?.cheap_review_path_confirmed === true ? ' · SLA cheap path confirmed' : ''}${slaAudit?.decision_lead_time_status ? ` · SLA lead ${String(slaAudit.decision_lead_time_status)}` : ''} · Обновления macro policy: ${Number(totals.workspace_macro_policy_update_events || 0)}${p2Control?.macro_low_signal_backlog_dominant === true ? ` · macro low-signal ${Number(p2Control.macro_low_signal_advisory_share_pct || 0)}%` : ''} · Weekly focus: ${weeklyFocusSections.length ? weeklyFocusSections.map((item) => String(item?.key || '')).filter(Boolean).join(', ') : 'none'}${weeklyReviewFocus?.top_priority_key ? ` (top ${String(weeklyReviewFocus.top_priority_key)})` : ''}${weeklyReviewFocus?.focus_health ? `, health ${String(weeklyReviewFocus.focus_health)}` : ''}${weeklyReviewFocus?.priority_mix_summary ? ` · ${String(weeklyReviewFocus.priority_mix_summary)}` : ''}${weeklyReviewFocus?.next_action_summary ? ` · next: ${String(weeklyReviewFocus.next_action_summary)}` : ''}${weeklyReviewFocus?.requires_management_review === true ? ' · management review suggested' : ''} · Parity gaps: ${Number(totals.workspace_parity_gap_events || 0)} · Render error: ${Number(totals.render_errors || 0)} · Среднее открытие: ${avgCurrent} (предыдущее ${avgPrevious}, delta ${formatDeltaMs(comparison.avg_open_ms_delta)}) · Обновлено: ${generatedAt}.`;
        }
      } catch (_error) {
        renderExperimentTelemetrySummaryRows([]);
        renderExperimentTelemetryDimensionRows(elements.experimentTelemetryShiftRows, [], 'shift');
        renderExperimentTelemetryDimensionRows(elements.experimentTelemetryTeamRows, [], 'team');
        renderExperimentTelemetryGuardrails(null);
        renderExperimentRolloutPacket(null);
        renderExperimentRolloutScorecard(null);
        renderExperimentRolloutDecision(null, null);
        renderExperimentGapBreakdown(null);
        if (elements.experimentTelemetrySummaryState) {
          elements.experimentTelemetrySummaryState.textContent = 'Не удалось загрузить telemetry-агрегаты. Проверьте API /api/dialogs/workspace-telemetry/summary.';
        }
      }
    }

    function bindTelemetryRefreshControls() {
      if (state.refreshControlsBound) return;
      state.refreshControlsBound = true;
      if (elements.experimentTelemetryRefreshBtn) {
        elements.experimentTelemetryRefreshBtn.addEventListener('click', () => {
          loadExperimentTelemetrySummary();
        });
      }
      if (elements.experimentTelemetryAutoRefresh) {
        elements.experimentTelemetryAutoRefresh.addEventListener('change', () => {
          syncExperimentTelemetryAutoRefresh();
        });
      }
    }

    return {
      renderExperimentInfoPanel,
      renderExperimentTelemetryGuardrails,
      renderExperimentRolloutPacket,
      renderExperimentGapBreakdown,
      renderExperimentRolloutScorecard,
      renderExperimentRolloutDecision,
      renderExperimentTelemetrySummaryRows,
      renderExperimentTelemetryDimensionRows,
      clearExperimentTelemetryRefreshTimer,
      syncExperimentTelemetryAutoRefresh,
      loadExperimentTelemetrySummary,
      bindTelemetryRefreshControls,
    };
  }

  window.DialogsExperimentRuntime = {
    createRuntime,
  };
})();

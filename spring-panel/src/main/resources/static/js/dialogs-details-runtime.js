(function () {
  if (window.DialogsDetailsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function getActiveDialogState() {
      const state = options.getActiveDialogState?.();
      return state && typeof state === 'object'
        ? state
        : {
          ticketId: null,
          channelId: null,
          responsibleRaw: '',
          responsibleAvatarUrl: '',
          context: {},
          row: null,
        };
    }

    function normalizeDialogTimeMetrics(raw) {
      const base = {
        good_limit: 30,
        warning_limit: 60,
        colors: {
          good: '#d1fae5',
          warning: '#fef3c7',
          danger: '#fee2e2',
        },
      };

      if (raw && typeof raw === 'object') {
        const good = Number.parseInt(raw.good_limit, 10);
        const warning = Number.parseInt(raw.warning_limit, 10);
        if (Number.isFinite(good) && good > 0) {
          base.good_limit = good;
        }
        if (Number.isFinite(warning) && warning > 0) {
          base.warning_limit = warning;
        }
        if (raw.colors && typeof raw.colors === 'object') {
          base.colors.good = String(raw.colors.good || base.colors.good);
          base.colors.warning = String(raw.colors.warning || base.colors.warning);
          base.colors.danger = String(raw.colors.danger || base.colors.danger);
        }
      }

      if (!Number.isFinite(base.good_limit) || base.good_limit <= 0) {
        base.good_limit = 30;
      }
      if (!Number.isFinite(base.warning_limit) || base.warning_limit <= base.good_limit) {
        base.warning_limit = Math.max(base.good_limit + 1, 60);
      }

      return base;
    }

    function parseTimestampValue(raw) {
      if (!raw) return null;
      const text = String(raw).trim();
      if (!text) return null;
      if (/^\d{10,13}$/.test(text)) {
        const value = text.length === 10 ? Number(text) * 1000 : Number(text);
        return Number.isFinite(value) ? value : null;
      }
      const parsed = Date.parse(text.replace(' ', 'T'));
      return Number.isFinite(parsed) ? parsed : null;
    }

    function parseDialogTimestamp(summary, fallbackRow) {
      const candidates = [
        summary?.createdAt,
        summary?.created_at,
        summary?.createdDate,
        summary?.created_date,
        summary?.createdTime,
        summary?.created_time,
        fallbackRow?.dataset?.createdAt,
        fallbackRow?.dataset?.created_at,
      ];
      for (const raw of candidates) {
        const parsed = parseTimestampValue(raw);
        if (parsed) return parsed;
      }
      return null;
    }

    function formatDuration(totalMinutes) {
      if (!Number.isFinite(totalMinutes) || totalMinutes < 0) return '—';
      const minutes = Math.floor(totalMinutes);
      if (minutes < 60) return `${minutes} мин`;
      const hours = Math.floor(minutes / 60);
      const restMinutes = minutes % 60;
      if (hours < 24) return `${hours} ч ${restMinutes} мин`;
      const days = Math.floor(hours / 24);
      const restHours = hours % 24;
      return `${days} д ${restHours} ч`;
    }

    function resolveTimeMetricColor(totalMinutes, config) {
      if (!config || !Number.isFinite(totalMinutes)) return null;
      if (totalMinutes <= config.good_limit) return config.colors.good;
      if (totalMinutes <= config.warning_limit) return config.colors.warning;
      return config.colors.danger;
    }

    function updateDetailsTakeButton(responsible) {
      if (!elements.detailsTakeBtn) return;
      const activeState = getActiveDialogState();
      const safeResponsible = String(responsible || '').trim();
      const canTakeOwnership = Boolean(activeState.ticketId)
        && options.canTakeDialogOwnership?.(safeResponsible, activeState.row ? options.isResolvedRow?.(activeState.row) === true : false);
      elements.detailsTakeBtn.disabled = !canTakeOwnership;
      elements.detailsTakeBtn.classList.toggle('d-none', !canTakeOwnership);
    }

    function updateDetailsResponsible(responsible, runtimeOptions = {}) {
      const activeState = getActiveDialogState();
      const safeResponsible = String(responsible || '').trim() || '—';
      const rawResponsible = String(runtimeOptions.rawResponsible || '').trim();
      const fallbackAvatar = options.isOwnedByCurrentOperator?.(rawResponsible || safeResponsible)
        ? String(options.operatorAvatarUrl || '').trim()
        : String(activeState.responsibleAvatarUrl || '').trim();
      const avatarUrl = String(runtimeOptions.avatarUrl || fallbackAvatar || '').trim();
      options.setActiveDialogResponsibleState?.(rawResponsible || (safeResponsible === '—' ? '' : safeResponsible), avatarUrl);
      options.setActiveDialogContext?.({
        ...activeState.context,
        operatorName: safeResponsible,
      });
      const responsibleRow = elements.detailsSummary
        ? Array.from(elements.detailsSummary.querySelectorAll('.d-flex.justify-content-between.gap-2'))
          .find((row) => row.firstElementChild?.textContent?.trim() === 'Ответственный')
        : null;
      const responsibleValue = responsibleRow?.lastElementChild || null;
      if (responsibleValue) {
        responsibleValue.innerHTML = safeResponsible !== '—'
          ? `<a class="dialog-summary-value-link" href="/users/${encodeURIComponent(rawResponsible || safeResponsible)}" target="_blank" rel="noopener">${escapeHtml(safeResponsible)}</a>`
          : '—';
      }
      updateDetailsTakeButton(rawResponsible || safeResponsible);
    }

    function updateDetailsStatusSummary(statusLabel, statusKey = 'waiting_client') {
      const activeState = getActiveDialogState();
      const safeStatus = statusLabel || '—';
      options.setActiveDialogContext?.({
        ...activeState.context,
        status: safeStatus,
      });
      const statusRow = elements.detailsSummary
        ? Array.from(elements.detailsSummary.querySelectorAll('.d-flex.justify-content-between.gap-2'))
          .find((row) => row.firstElementChild?.textContent?.trim() === '������')
        : null;
      const statusValue = statusRow?.lastElementChild || null;
      if (statusValue) {
        statusValue.innerHTML = options.renderSummaryBadge?.(
          safeStatus,
          options.resolveSummaryBadgeStyle?.('status', safeStatus)
        ) || escapeHtml(safeStatus);
      }
      options.updateResolveButton?.(statusKey === 'closed' || statusKey === 'auto_closed' ? 'resolved' : 'pending');
    }

    function formatStatusLabel(raw, fallback, statusKey) {
      if (fallback) return fallback;
      if (statusKey) {
        switch (statusKey) {
          case 'auto_processing':
            return 'в автоматической обработке';
          case 'auto_closed':
            return 'Закрыт автоматически';
          case 'closed':
            return 'Закрыт';
          case 'waiting_operator':
            return 'ожидает ответа оператора';
          case 'waiting_client':
            return 'ожидает ответа клиента';
          case 'new':
            return 'новый';
          default:
            return '—';
        }
      }
      const normalized = String(raw || '').toLowerCase();
      if (normalized === 'resolved' || normalized === 'closed') return 'Закрыт';
      if (normalized === 'pending') return 'ожидает ответа оператора';
      if (normalized) return 'ожидает ответа клиента';
      return '—';
    }

    function isResolvedStatus(statusRaw, statusKey, statusLabel) {
      const raw = String(statusRaw || '').toLowerCase();
      const key = String(statusKey || '').toLowerCase();
      return raw === 'resolved' || raw === 'closed' || key === 'closed' || key === 'auto_closed';
    }

    async function openDialogDetails(ticketId, fallbackRow) {
      if (!ticketId || !elements.detailsModalEl) {
        options.debugLog?.('openDialogDetails.skipped', {
          ticketId,
          hasDetailsModal: Boolean(elements.detailsModalEl),
        });
        return;
      }
      options.debugLog?.('openDialogDetails.start', {
        ticketId,
        fallbackRowFound: Boolean(fallbackRow),
        channelId: fallbackRow?.dataset?.channelId || null,
      });
      options.setActiveDialogTicketId?.(ticketId);
      options.initMacroVariableCatalog?.(ticketId, true);
      options.setActiveDialogRow?.(fallbackRow || null, { ensureVisible: true });
      options.setActiveDialogChannelId?.(fallbackRow?.dataset?.channelId || null);
      options.setActiveDialogResponsibleState?.(
        String(fallbackRow?.dataset?.responsibleRaw || fallbackRow?.dataset?.responsible || '').trim(),
        String(fallbackRow?.dataset?.responsibleAvatarUrl || '').trim()
      );
      options.updateDialogUnreadCount?.(Number(fallbackRow?.dataset?.unread) || 0);
      if (elements.detailsMeta) {
        const fallbackRequestNumber = fallbackRow?.dataset?.requestNumber || '';
        elements.detailsMeta.textContent = options.formatDialogMeta?.(ticketId, fallbackRequestNumber) || '';
      }
      if (elements.detailsRating) {
        elements.detailsRating.textContent = '';
        elements.detailsRating.classList.add('d-none');
      }
      options.resetPreviousDialogHistoryState?.();
      if (elements.detailsSummary) elements.detailsSummary.innerHTML = '<div>Загрузка...</div>';
      if (elements.detailsMetrics) elements.detailsMetrics.innerHTML = '<div class="text-muted">Загрузка метрик...</div>';
      options.updateDetailsLocationLabel?.('—');
      if (elements.detailsHistory) {
        options.renderHistory?.([], { scrollToBottom: false });
      }
      if (elements.detailsAiState) elements.detailsAiState.textContent = 'Загрузка подсказок AI...';
      if (elements.detailsAiList) {
        elements.detailsAiList.innerHTML = '';
        elements.detailsAiList.classList.add('d-none');
      }
      options.setDialogParticipantsState?.([]);
      options.renderDialogParticipantsLoadingState?.('Загрузка участников...');
      options.syncParticipantsSelectOptions?.();
      options.syncReassignSelectOptions?.();
      if (elements.detailsReplyText) elements.detailsReplyText.value = '';
      if (elements.detailsOpenClientCard) {
        elements.detailsOpenClientCard.dataset.userId = '';
        elements.detailsOpenClientCard.disabled = true;
        elements.detailsOpenClientCard.classList.add('disabled');
        elements.detailsOpenClientCard.setAttribute('aria-disabled', 'true');
      }
      if (elements.detailsSpam) {
        elements.detailsSpam.dataset.userId = '';
        elements.detailsSpam.disabled = true;
      }
      updateDetailsTakeButton(fallbackRow?.dataset?.responsibleRaw || fallbackRow?.dataset?.responsible || '');

      options.showModalSafe?.(elements.detailsModalEl, elements.detailsModal);
      try {
        const activeState = getActiveDialogState();
        const url = options.withChannelParam?.(`/api/dialogs/${encodeURIComponent(ticketId)}`, activeState.channelId)
          || `/api/dialogs/${encodeURIComponent(ticketId)}`;
        options.debugLog?.('openDialogDetails.fetch.start', { url });
        const response = await fetch(url, {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        options.debugLog?.('openDialogDetails.fetch.response', {
          status: response.status,
          ok: response.ok,
        });
        const data = await response.json();
        if (!response.ok) {
          throw new Error(data?.error || `Ошибка ${response.status}`);
        }
        const summary = data.summary || {};
        const selectedCategories = new Set(Array.isArray(data.categories) ? data.categories.filter(Boolean).map((item) => String(item).trim()) : []);
        options.setSelectedCategories?.(selectedCategories);
        options.syncCategorySelections?.();
        options.updateSummaryCategories?.(options.formatCategoriesLabel?.(Array.from(selectedCategories)) || '—');
        if (summary.channelId) {
          options.setActiveDialogChannelId?.(summary.channelId);
        }

        const resolvedBy = summary.resolvedBy || summary.resolved_by;
        const resolvedAt = summary.resolvedAt || summary.resolved_at;
        const createdDate = summary.createdDate || summary.created_date;
        const createdTime = summary.createdTime || summary.created_time;
        const createdAt = summary.createdAt || summary.created_at;
        const createdLabel = [createdDate, createdTime].filter(Boolean).join(' ')
          || createdAt
          || '—';
        const createdDisplay = options.formatTimestamp?.(createdLabel, { includeTime: true }) || '—';
        const resolvedDisplay = options.formatTimestamp?.(resolvedAt || '', { includeTime: true }) || '—';
        const responsibleRaw = summary.rawResponsible
          || summary.raw_responsible
          || fallbackRow?.dataset?.responsibleRaw
          || fallbackRow?.dataset?.responsible
          || '';
        const responsibleAvatarUrl = summary.responsibleAvatarUrl
          || summary.responsible_avatar_url
          || fallbackRow?.dataset?.responsibleAvatarUrl
          || '';
        const responsibleLabel = summary.responsible
          || resolvedBy
          || fallbackRow?.dataset?.responsible
          || '—';
        const clientName = summary.clientName
          || summary.username
          || fallbackRow?.dataset?.client
          || '—';
        const clientStatus = summary.clientStatus || fallbackRow?.dataset?.clientStatus || '—';
        const channelLabel = summary.channelName || fallbackRow?.dataset?.channel || '—';
        const businessLabel = summary.business || fallbackRow?.dataset?.business || '—';
        const statusRaw = summary.status || fallbackRow?.dataset?.statusRaw || '';
        const statusKey = summary.statusKey || fallbackRow?.dataset?.statusKey || '';
        const statusLabel = formatStatusLabel(statusRaw, summary.statusLabel || fallbackRow?.dataset?.status, statusKey);
        const locationLabel = summary.locationName || summary.city || fallbackRow?.dataset?.location || '—';
        const problemLabel = summary.problem || fallbackRow?.dataset?.problem || '—';
        const selectedCategoriesLabel = options.formatCategoriesLabel?.(Array.from(selectedCategories)) || '—';
        const categoriesLabel = selectedCategoriesLabel !== '—'
          ? selectedCategoriesLabel
          : (summary.categoriesSafe || summary.categories || fallbackRow?.dataset?.categories || '—');
        const requestNumber = summary.requestNumber || fallbackRow?.dataset?.requestNumber || '';
        const ratingValue = summary.rating ?? fallbackRow?.dataset?.rating;
        const ratingStars = options.formatRatingStars?.(ratingValue) || '';
        const clientUserId = options.getDialogUserId?.(summary) || String(fallbackRow?.dataset?.userId || '').trim();
        if (elements.detailsAvatar) {
          options.bindAvatar?.(elements.detailsAvatar, clientUserId, clientName);
        }
        if (elements.detailsClientName) elements.detailsClientName.textContent = clientName;
        if (elements.detailsClientStatus) elements.detailsClientStatus.textContent = clientStatus;
        options.updateDetailsLocationLabel?.(locationLabel);
        if (elements.detailsOpenClientCard) {
          if (clientUserId) {
            elements.detailsOpenClientCard.dataset.userId = clientUserId;
            elements.detailsOpenClientCard.disabled = false;
            elements.detailsOpenClientCard.classList.remove('disabled');
            elements.detailsOpenClientCard.setAttribute('aria-disabled', 'false');
          } else {
            elements.detailsOpenClientCard.dataset.userId = '';
            elements.detailsOpenClientCard.disabled = true;
            elements.detailsOpenClientCard.classList.add('disabled');
            elements.detailsOpenClientCard.setAttribute('aria-disabled', 'true');
          }
        }
        if (elements.detailsSpam) {
          elements.detailsSpam.dataset.userId = clientUserId || '';
        }
        updateDetailsResponsible(responsibleLabel, {
          rawResponsible: responsibleRaw,
          avatarUrl: responsibleAvatarUrl,
        });
        options.updateSummaryCategories?.(categoriesLabel || '—');
        if (elements.detailsProblem) elements.detailsProblem.textContent = problemLabel;
        if (elements.detailsMeta) elements.detailsMeta.textContent = options.formatDialogMeta?.(ticketId, requestNumber) || '';
        if (elements.detailsRating) {
          if (ratingStars) {
            elements.detailsRating.textContent = ratingStars;
            elements.detailsRating.classList.remove('d-none');
          } else {
            elements.detailsRating.textContent = '';
            elements.detailsRating.classList.add('d-none');
          }
        }
        const summaryItems = [
          ['Клиент', summary.clientName || summary.username || fallbackRow?.dataset?.client || '—'],
          ['������ �������', summary.clientStatus || fallbackRow?.dataset?.clientStatus || '�'],
          ['������', statusLabel || '�'],
          ['Канал', summary.channelName || fallbackRow?.dataset?.channel || '—'],
          ['Бизнес', businessLabel],
          ['Проблема', summary.problem || fallbackRow?.dataset?.problem || '—'],
          ['Категории', categoriesLabel || '—'],
          ['Ответственный', responsibleLabel],
          ['������', createdDisplay || createdLabel],
        ];
        options.setActiveDialogContext?.({
          ...getActiveDialogState().context,
          clientName,
          clientUserId,
          operatorName: responsibleLabel || 'Оператор',
          channelName: channelLabel,
          business: businessLabel,
          location: locationLabel,
          status: statusLabel || '—',
          createdAt: createdDisplay || createdLabel || '—',
        });
        if (elements.detailsSummary) {
          elements.detailsSummary.innerHTML = summaryItems.map(([label, value]) => {
            const safeValue = value || '—';
            let renderedValue = `<span class="text-dark">${escapeHtml(safeValue)}</span>`;
            if (label === '������') {
              renderedValue = options.renderSummaryBadge?.(safeValue, options.resolveSummaryBadgeStyle?.('status', safeValue)) || renderedValue;
            }
            if (label === 'Канал') {
              renderedValue = options.renderSummaryBadge?.(safeValue, options.resolveSummaryBadgeStyle?.('channel', safeValue)) || renderedValue;
            }
            if (label === 'Бизнес') {
              const businessKey = String(safeValue || '').trim();
              const businessStyle = options.businessStyleMap?.[businessKey] || {};
              renderedValue = options.renderSummaryBadge?.(safeValue, {
                background: businessStyle.background || options.summaryBadgeStyles?.default?.background,
                text: businessStyle.text || options.summaryBadgeStyles?.default?.text,
              }) || renderedValue;
            }
            if (label === 'Клиент' && safeValue !== '—' && clientUserId) {
              renderedValue = `<a class="dialog-summary-value-link" href="/client/${encodeURIComponent(clientUserId)}" target="_blank" rel="noopener">${escapeHtml(safeValue)}</a>`;
            }
            if (label === 'Ответственный' && safeValue !== '—') {
              renderedValue = `<a class="dialog-summary-value-link" href="/users/${encodeURIComponent(safeValue)}" target="_blank" rel="noopener">${escapeHtml(safeValue)}</a>`;
            }
            const fieldAttr = label === 'Категории'
              ? ' data-summary-field="categories"'
              : (label === 'Ответственный' ? ' data-summary-field="responsible"' : '');
            const valueMarkup = label === 'Категории'
              ? `<span data-summary-value>${options.renderCategoryBadges?.(safeValue) || escapeHtml(safeValue)}</span>`
              : renderedValue;
            return `
          <div class="d-flex justify-content-between gap-2"${fieldAttr}>
            <span>${label}</span>
            ${valueMarkup}
          </div>
        `;
          }).join('');
        }
        if (elements.detailsMetrics) {
          const timeMetricsConfig = normalizeDialogTimeMetrics(window.DIALOG_CONFIG?.time_metrics);
          const createdAtTimestamp = parseDialogTimestamp(summary, fallbackRow);
          const resolvedAtTimestamp = parseTimestampValue(resolvedAt);
          const shouldUseResolvedTime = isResolvedStatus(statusRaw, statusKey, statusLabel) && Number.isFinite(resolvedAtTimestamp);
          const endTimestamp = shouldUseResolvedTime ? resolvedAtTimestamp : Date.now();
          const totalMinutes = Number.isFinite(createdAtTimestamp)
            ? Math.max(0, Math.floor((endTimestamp - createdAtTimestamp) / 60000))
            : null;
          const timeLabel = totalMinutes === null ? '—' : formatDuration(totalMinutes);
          const timeColor = totalMinutes === null ? null : resolveTimeMetricColor(totalMinutes, timeMetricsConfig);
          const metrics = [
            { label: '�������', value: createdDisplay },
            { label: 'Время обращения', value: timeLabel, color: timeColor },
            { label: 'Закрыто', value: resolvedDisplay || '—' },
            { label: 'Канал', value: channelLabel },
            { label: 'Бизнес', value: businessLabel },
            { label: 'Ответственный', value: responsibleLabel },
          ];
          elements.detailsMetrics.innerHTML = metrics.map((item) => `
            <div class="dialog-metric-item">
              <span>${item.label}</span>
              ${
                item.color
                  ? `<span class="dialog-time-metric-badge" style="background-color: ${item.color};">${item.value || '—'}</span>`
                  : `<span>${item.value || '—'}</span>`
              }
            </div>
          `).join('');
        }
        options.renderHistory?.(data.history || []);
        try {
          await options.loadDialogParticipants?.();
        } catch (participantsError) {
          options.renderDialogParticipantsLoadingState?.(participantsError?.message || 'Не удалось загрузить участников.');
        }
        options.loadDetailsAiSuggestions?.(ticketId);
        options.updateResolveButton?.(statusRaw);
        if (statusRaw || statusKey) {
          options.updateRowStatus?.(options.getActiveDialogState?.().row, statusRaw, statusLabel, statusKey, summary.unreadCount);
        }
        options.updateDialogUnreadCount?.(0);
        options.debugLog?.('openDialogDetails.render.done', {
          ticketId,
          historyCount: Array.isArray(data?.history) ? data.history.length : null,
        });
      } catch (error) {
        options.debugLog?.('openDialogDetails.catch', {
          ticketId,
          message: error?.message || String(error || ''),
        });
        if (elements.detailsSummary) {
          elements.detailsSummary.innerHTML = `<div class="text-danger">Не удалось загрузить детали: ${error.message}</div>`;
        }
        if (elements.detailsMetrics) {
          elements.detailsMetrics.innerHTML = '<div class="text-muted">Метрики недоступны.</div>';
        }
        options.updateDetailsLocationLabel?.('—');
      }

      options.startHistoryPolling?.();
      options.debugLog?.('openDialogDetails.finish', {
        ticketId,
        modalVisible: elements.detailsModalEl.classList.contains('show'),
        modalDisplay: elements.detailsModalEl.style?.display || '',
      });
    }

    return {
      updateDetailsTakeButton,
      updateDetailsResponsible,
      updateDetailsStatusSummary,
      formatStatusLabel,
      isResolvedStatus,
      openDialogDetails,
    };
  }

  window.DialogsDetailsRuntime = {
    createRuntime,
  };
})();

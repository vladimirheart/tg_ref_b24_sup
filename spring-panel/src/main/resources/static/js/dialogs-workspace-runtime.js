(function () {
  if (window.DialogsWorkspaceRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      activeReplyToTelegramId: null,
      messagesNextCursor: null,
      messagesHasMore: false,
      messagesLoadingMore: false,
      draftAutosaveTimer: null,
      draftLastSavedValue: '',
      draftLastTelemetryAt: 0,
    };

    function getActiveWorkspaceState() {
      const activeState = typeof options.getActiveWorkspaceState === 'function'
        ? options.getActiveWorkspaceState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : {
          ticketId: '',
          channelId: null,
          composerTicketId: '',
          payload: null,
          row: null,
          readonlyMode: false,
        };
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function formatWorkspaceDateTime(value) {
      return typeof options.formatWorkspaceDateTime === 'function'
        ? options.formatWorkspaceDateTime(value)
        : String(value || '').trim();
    }

    function formatTimestamp(value, formatOptions = {}) {
      return typeof options.formatTimestamp === 'function'
        ? options.formatTimestamp(value, formatOptions)
        : String(value || '').trim();
    }

    function renderWorkspaceSimpleList(items, formatter) {
      return typeof options.renderWorkspaceSimpleList === 'function'
        ? options.renderWorkspaceSimpleList(items, formatter)
        : '';
    }

    function resolveWorkspaceRolloutBannerClass(tone) {
      switch (String(tone || '').toLowerCase()) {
        case 'success':
          return 'alert alert-success py-2 px-3 small mb-3';
        case 'warning':
          return 'alert alert-warning py-2 px-3 small mb-3';
        case 'danger':
          return 'alert alert-danger py-2 px-3 small mb-3';
        default:
          return 'alert alert-info py-2 px-3 small mb-3';
      }
    }

    function renderWorkspaceRolloutBanner(rollout) {
      if (!elements.workspaceRolloutBanner) return;
      const summary = String(rollout?.summary || '').trim();
      const reviewedAt = formatTimestamp(rollout?.reviewed_at_utc || '', { includeTime: true, fallback: '' });
      const dataUpdatedAt = formatTimestamp(rollout?.data_updated_at_utc || '', { includeTime: true, fallback: '' });
      const metaParts = [];
      if (String(rollout?.mode || '').trim()) {
        metaParts.push(`mode: ${String(rollout.mode).trim()}`);
      }
      if (Number.isFinite(Number(rollout?.rollout_percent)) && Number(rollout?.rollout_percent) > 0) {
        metaParts.push(`rollout: ${Math.max(0, Math.min(100, Number(rollout.rollout_percent)))}%`);
      }
      if (reviewedAt && reviewedAt !== '—') {
        metaParts.push(`reviewed UTC: ${reviewedAt}`);
      }
      if (dataUpdatedAt && dataUpdatedAt !== '—') {
        metaParts.push(`data updated UTC: ${dataUpdatedAt}`);
      }
      elements.workspaceRolloutBanner.className = resolveWorkspaceRolloutBannerClass(rollout?.banner_tone);
      elements.workspaceRolloutBanner.classList.remove('d-none');
      elements.workspaceRolloutBanner.textContent = [summary || 'Workspace rollout state loaded.', metaParts.join(' · ')].filter(Boolean).join(' ');
    }

    function resolveWorkspaceParityBannerClass(status) {
      switch (String(status || '').toLowerCase()) {
        case 'ok':
          return 'alert alert-success py-2 px-3 small mb-3';
        case 'blocked':
          return 'alert alert-danger py-2 px-3 small mb-3';
        default:
          return 'alert alert-warning py-2 px-3 small mb-3';
      }
    }

    function renderWorkspaceParityBanner(parity) {
      if (!elements.workspaceParityBanner) return;
      const safeParity = parity && typeof parity === 'object' ? parity : null;
      const summary = String(safeParity?.summary || '').trim();
      const status = String(safeParity?.status || '').trim().toLowerCase();
      if (!safeParity || !summary) {
        elements.workspaceParityBanner.classList.add('d-none');
        elements.workspaceParityBanner.textContent = '';
        return;
      }
      const checkedAtUtc = formatTimestamp(safeParity?.checked_at, { includeTime: true, fallback: '' });
      const missingLabels = Array.isArray(safeParity?.missing_labels)
        ? safeParity.missing_labels.filter(Boolean).map((item) => String(item).trim())
        : [];
      const metaParts = [];
      if (Number.isFinite(Number(safeParity?.score_pct))) {
        metaParts.push(`parity score: ${Math.max(0, Math.min(100, Number(safeParity.score_pct)))}%`);
      }
      if (checkedAtUtc && checkedAtUtc !== '—') {
        metaParts.push(`checked UTC: ${checkedAtUtc}`);
      }
      if (missingLabels.length > 0) {
        metaParts.push(`gaps: ${missingLabels.join(', ')}`);
      }
      elements.workspaceParityBanner.className = resolveWorkspaceParityBannerClass(status);
      elements.workspaceParityBanner.classList.remove('d-none');
      elements.workspaceParityBanner.textContent = [summary, metaParts.join(' · ')].filter(Boolean).join(' ');
    }

    function setWorkspaceReadonlyMode(isReadonly, reasonText) {
      const nextState = Boolean(isReadonly);
      const activeState = getActiveWorkspaceState();
      const stateChanged = Boolean(activeState.readonlyMode) !== nextState;
      options.setWorkspaceReadonlyState?.(nextState);
      if (elements.workspaceReadonlyBanner) {
        elements.workspaceReadonlyBanner.classList.toggle('d-none', !nextState);
        if (nextState && reasonText) {
          elements.workspaceReadonlyBanner.textContent = String(reasonText);
        }
      }
      if (stateChanged) {
        options.updateBulkToolbarState?.();
        options.renderTableFromRows?.(true);
      }
    }

    function resolveWorkspaceReadonlyReason(permissions) {
      if (!permissions || typeof permissions !== 'object') {
        return 'Workspace открыт в режиме только чтения: не удалось получить права оператора.';
      }
      const mutatingPermissionKeys = Array.isArray(options.workspaceMutatingPermissionKeys)
        ? options.workspaceMutatingPermissionKeys
        : [];
      const requiredFlags = ['can_reply', ...mutatingPermissionKeys];
      const hasInvalidFlag = requiredFlags.some((flag) => typeof permissions[flag] !== 'boolean');
      if (hasInvalidFlag) {
        return 'Workspace открыт в режиме только чтения: права оператора загружены некорректно.';
      }
      const hasMutatingPermission = mutatingPermissionKeys.some((flag) => permissions[flag] === true);
      if (!hasMutatingPermission) {
        return 'Workspace открыт в режиме только чтения: действия изменения отключены политикой доступа.';
      }
      return null;
    }

    function getWorkspaceDraftStorageKey(ticketId) {
      return `${options.draftStoragePrefix || 'iguana:dialogs:workspace-draft:'}${String(ticketId || '').trim()}`;
    }

    function getStoredWorkspaceDraft(ticketId) {
      if (!ticketId) return '';
      try {
        return localStorage.getItem(getWorkspaceDraftStorageKey(ticketId)) || '';
      } catch (_error) {
        return '';
      }
    }

    function resetWorkspaceReplyTarget(replyOptions = {}) {
      const activeState = getActiveWorkspaceState();
      const hadActiveTarget = Number.isFinite(Number(state.activeReplyToTelegramId));
      state.activeReplyToTelegramId = null;
      if (elements.workspaceReplyTarget) {
        elements.workspaceReplyTarget.classList.add('d-none');
      }
      if (elements.workspaceReplyTargetText) {
        elements.workspaceReplyTargetText.textContent = '';
      }
      if (elements.workspaceComposerText) {
        elements.workspaceComposerText.placeholder = 'Введите ответ клиенту…';
      }
      if (hadActiveTarget && replyOptions.emitTelemetry !== false) {
        options.emitWorkspaceTelemetry?.('workspace_reply_target_cleared', {
          ticketId: activeState.ticketId,
          reason: replyOptions.reason || 'manual_clear',
          contractVersion: activeState.payload?.contract_version || 'workspace.v1',
        });
      }
    }

    function setWorkspaceReplyTarget(messageId, preview) {
      const normalizedMessageId = Number.parseInt(messageId, 10);
      if (!Number.isFinite(normalizedMessageId)) {
        resetWorkspaceReplyTarget({ emitTelemetry: false });
        return;
      }
      const activeState = getActiveWorkspaceState();
      state.activeReplyToTelegramId = normalizedMessageId;
      if (elements.workspaceComposerText) {
        elements.workspaceComposerText.placeholder = `Ответ на сообщение #${normalizedMessageId}`;
      }
      if (elements.workspaceReplyTarget) {
        elements.workspaceReplyTarget.classList.remove('d-none');
      }
      if (elements.workspaceReplyTargetText) {
        const safePreview = String(preview || '').trim();
        elements.workspaceReplyTargetText.textContent = safePreview || `Сообщение #${normalizedMessageId}`;
      }
      options.emitWorkspaceTelemetry?.('workspace_reply_target_selected', {
        ticketId: activeState.ticketId,
        reason: `message:${normalizedMessageId}`,
        contractVersion: activeState.payload?.contract_version || 'workspace.v1',
      });
    }

    function hasUnsavedWorkspaceComposerChanges(nextTicketId) {
      const activeState = getActiveWorkspaceState();
      if (!elements.workspaceComposerText || !activeState.composerTicketId) return false;
      if (!elements.workspaceComposerText.value.trim()) return false;
      if (String(nextTicketId || '').trim() === String(activeState.composerTicketId || '').trim()) return false;
      const stored = getStoredWorkspaceDraft(activeState.composerTicketId).trim();
      return elements.workspaceComposerText.value.trim() !== stored;
    }

    function confirmWorkspaceTicketSwitch(nextTicketId) {
      if (!hasUnsavedWorkspaceComposerChanges(nextTicketId)) return true;
      const activeState = getActiveWorkspaceState();
      const accepted = window.confirm('Есть несохранённые изменения в черновике. Сохранить текущий черновик перед переключением диалога?');
      if (accepted) {
        saveWorkspaceDraft(activeState.composerTicketId, elements.workspaceComposerText?.value || '', { reason: 'manual' });
      }
      return accepted;
    }

    function updateWorkspaceDraftState(text) {
      if (!elements.workspaceComposerDraftState) return;
      elements.workspaceComposerDraftState.textContent = text || 'Черновик не сохранён';
    }

    function saveWorkspaceDraft(ticketId, message, saveOptions = {}) {
      if (!ticketId || !elements.workspaceComposerText) return;
      const value = String(message || '').trim();
      const storageKey = getWorkspaceDraftStorageKey(ticketId);
      try {
        if (!value) {
          localStorage.removeItem(storageKey);
          updateWorkspaceDraftState('Черновик очищен');
        } else {
          localStorage.setItem(storageKey, value);
          state.draftLastSavedValue = value;
          if (!saveOptions.silent) {
            updateWorkspaceDraftState(`Черновик сохранён · ${new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`);
          }
          const now = Date.now();
          const minInterval = Number(options.draftTelemetryMinInterval) || 30000;
          const shouldSendDraftTelemetry = saveOptions.reason === 'manual'
            || (saveOptions.reason === 'autosave' && now - state.draftLastTelemetryAt >= minInterval);
          if (shouldSendDraftTelemetry) {
            state.draftLastTelemetryAt = now;
            options.emitWorkspaceTelemetry?.('workspace_draft_saved', {
              ticketId,
              reason: saveOptions.reason || 'manual',
              length: value.length,
            });
          }
        }
      } catch (_error) {
        updateWorkspaceDraftState('Не удалось сохранить черновик');
      }
    }

    function restoreWorkspaceDraft(ticketId) {
      if (!ticketId || !elements.workspaceComposerText) return;
      const storageKey = getWorkspaceDraftStorageKey(ticketId);
      try {
        const stored = localStorage.getItem(storageKey);
        elements.workspaceComposerText.value = stored || '';
        state.draftLastSavedValue = elements.workspaceComposerText.value.trim();
        if (stored) {
          updateWorkspaceDraftState('Черновик восстановлен');
          options.emitWorkspaceTelemetry?.('workspace_draft_restored', {
            ticketId,
            length: stored.length,
          });
        } else {
          updateWorkspaceDraftState('Черновик не сохранён');
        }
      } catch (_error) {
        elements.workspaceComposerText.value = '';
        state.draftLastSavedValue = '';
        updateWorkspaceDraftState('Не удалось загрузить черновик');
      }
    }

    function scheduleWorkspaceDraftAutosave() {
      const activeState = getActiveWorkspaceState();
      if (!activeState.composerTicketId || !elements.workspaceComposerText) return;
      const value = elements.workspaceComposerText.value.trim();
      if (state.draftAutosaveTimer) {
        window.clearTimeout(state.draftAutosaveTimer);
      }
      const delay = Number(options.draftAutosaveDelay) || 900;
      state.draftAutosaveTimer = window.setTimeout(() => {
        if (value === state.draftLastSavedValue) return;
        saveWorkspaceDraft(activeState.composerTicketId, elements.workspaceComposerText.value, { reason: 'autosave' });
      }, delay);
    }

    function appendWorkspaceMessage(message) {
      if (!elements.workspaceMessagesList) return;
      elements.workspaceMessagesList.classList.remove('d-none');
      elements.workspaceMessagesList.insertAdjacentHTML(
        'beforeend',
        options.renderWorkspaceMessageItem?.(message) || '',
      );
      if (elements.workspaceMessagesState) {
        elements.workspaceMessagesState.classList.add('d-none');
        elements.workspaceMessagesState.textContent = '';
      }
    }

    function updateWorkspaceMessagesLoadMoreState() {
      if (!elements.workspaceMessagesLoadMoreWrap || !elements.workspaceMessagesLoadMore) return;
      const activeState = getActiveWorkspaceState();
      const canLoadMore = Boolean(activeState.ticketId) && state.messagesHasMore;
      elements.workspaceMessagesLoadMoreWrap.classList.toggle('d-none', !canLoadMore);
      elements.workspaceMessagesLoadMore.disabled = state.messagesLoadingMore || !canLoadMore;
      elements.workspaceMessagesLoadMore.textContent = state.messagesLoadingMore ? 'Загрузка…' : 'Загрузить ещё';
    }

    function syncWorkspaceMessagesPagination(messages) {
      const safeMessages = messages && typeof messages === 'object' ? messages : {};
      state.messagesNextCursor = Number.isInteger(safeMessages.next_cursor) ? safeMessages.next_cursor : null;
      state.messagesHasMore = safeMessages.has_more === true;
      state.messagesLoadingMore = false;
      updateWorkspaceMessagesLoadMoreState();
    }

    async function sendWorkspaceReply() {
      const activeState = getActiveWorkspaceState();
      if (!elements.workspaceComposerText || !elements.workspaceComposerSend || !activeState.composerTicketId) return;
      const message = elements.workspaceComposerText.value.trim();
      const replyToTelegramId = Number.isFinite(Number(state.activeReplyToTelegramId))
        ? Number(state.activeReplyToTelegramId)
        : null;
      const replyPreview = elements.workspaceReplyTargetText ? elements.workspaceReplyTargetText.textContent.trim() : '';
      if (!message) return;
      if (activeState.readonlyMode || elements.workspaceComposerText.disabled) {
        options.notifyPermissionDenied?.('Отправка ответа');
        return;
      }
      elements.workspaceComposerSend.disabled = true;
      if (elements.workspaceComposerSaveDraft) {
        elements.workspaceComposerSaveDraft.disabled = true;
      }
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeState.composerTicketId)}/reply`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message, replyToTelegramId }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        elements.workspaceComposerText.value = '';
        saveWorkspaceDraft(activeState.composerTicketId, '');
        resetWorkspaceReplyTarget({ reason: 'message_sent' });
        appendWorkspaceMessage({
          senderName: data.responsible || 'Оператор',
          messageText: message,
          sentAt: data.timestamp || new Date().toISOString(),
          telegramMessageId: data.telegramMessageId || null,
          replyPreview,
        });
        if (activeState.row) {
          options.updateRowStatus?.(activeState.row, activeState.row.dataset.statusRaw || '', 'ожидает ответа клиента', 'waiting_client', 0);
          options.updateRowResponsible?.(activeState.row, data.responsible || activeState.row.dataset.responsible || '');
          options.applyFilters?.();
        }
      } catch (error) {
        notify(error.message || 'Не удалось отправить сообщение', 'error');
      } finally {
        elements.workspaceComposerSend.disabled = false;
        if (elements.workspaceComposerSaveDraft) {
          elements.workspaceComposerSaveDraft.disabled = false;
        }
      }
    }

    function mergeWorkspacePayload(basePayload, partialPayload, include) {
      return typeof options.mergeWorkspacePayload === 'function'
        ? options.mergeWorkspacePayload(basePayload, partialPayload, include)
        : partialPayload;
    }

    async function reloadWorkspaceSection(include, reloadOptions = {}) {
      const activeState = getActiveWorkspaceState();
      if (!options.workspaceEnabled || !activeState.ticketId) return;
      options.setWorkspaceSectionLoading?.(reloadOptions.stateElement, reloadOptions.errorElement, reloadOptions.statusText || 'Повторная загрузка...');
      try {
        const partialPayload = await options.preloadWorkspaceContract?.(activeState.ticketId, activeState.channelId, { include });
        const mergedPayload = mergeWorkspacePayload(activeState.payload, partialPayload, include);
        options.setActiveWorkspacePayload?.(mergedPayload);
        options.renderWorkspaceShell?.(mergedPayload);
      } catch (_error) {
        if (reloadOptions.errorElement) {
          reloadOptions.errorElement.classList.remove('d-none');
        }
        notify(reloadOptions.failMessage || 'Не удалось обновить секцию workspace.', 'warning');
      }
    }

    async function loadMoreWorkspaceMessages() {
      const activeState = getActiveWorkspaceState();
      if (!activeState.ticketId || !state.messagesHasMore || state.messagesLoadingMore) return;
      state.messagesLoadingMore = true;
      updateWorkspaceMessagesLoadMoreState();
      try {
        const workspacePayload = await options.preloadWorkspaceContract?.(activeState.ticketId, activeState.channelId, {
          include: 'messages',
          cursor: state.messagesNextCursor,
          limit: options.messagesPageLimit,
        });
        const messages = workspacePayload?.messages || {};
        const items = Array.isArray(messages.items) ? messages.items : [];
        if (elements.workspaceMessagesList && items.length > 0) {
          const markup = items.map((item) => options.renderWorkspaceMessageItem?.(item) || '').join('');
          elements.workspaceMessagesList.insertAdjacentHTML('beforeend', markup);
          elements.workspaceMessagesList.classList.remove('d-none');
        }
        state.messagesNextCursor = Number.isInteger(messages.next_cursor) ? messages.next_cursor : null;
        state.messagesHasMore = messages.has_more === true;
        if (elements.workspaceMessagesState && !state.messagesHasMore) {
          elements.workspaceMessagesState.classList.remove('d-none');
          elements.workspaceMessagesState.textContent = 'Показаны все сообщения по диалогу.';
        }
      } catch (_error) {
        notify('Не удалось догрузить сообщения workspace.', 'warning');
      } finally {
        state.messagesLoadingMore = false;
        updateWorkspaceMessagesLoadMoreState();
      }
    }

    function buildWorkspaceNavigationStateText(navigation) {
      const safeNavigation = navigation && typeof navigation === 'object' ? navigation : null;
      if (!safeNavigation) {
        return 'Очередь не определена';
      }
      if (typeof safeNavigation.summary === 'string' && safeNavigation.summary.trim()) {
        return safeNavigation.summary.trim();
      }
      const position = Number(safeNavigation.position || 0);
      const total = Number(safeNavigation.total || 0);
      if (position > 0 && total > 0) {
        return `Позиция ${position} из ${total}.`;
      }
      return safeNavigation.enabled === false
        ? 'Inline navigation отключена.'
        : 'Текущий диалог открыт вне активной очереди.';
    }

    function getWorkspaceNavigationTarget(direction, navigation) {
      const key = direction === 'previous' ? 'previous' : 'next';
      const candidate = navigation && typeof navigation === 'object' ? navigation[key] : null;
      const ticketId = String(candidate?.ticket_id || '').trim();
      if (!ticketId) {
        return null;
      }
      return {
        ticketId,
        channelId: candidate?.channel_id ?? null,
        clientName: String(candidate?.client_name || '').trim(),
        status: String(candidate?.status || '').trim(),
      };
    }

    function renderWorkspaceNavigation(navigation) {
      const previous = getWorkspaceNavigationTarget('previous', navigation);
      const next = getWorkspaceNavigationTarget('next', navigation);
      const navigationEnabled = options.inlineNavigation !== false && navigation?.enabled !== false;

      if (elements.workspaceNavPrevBtn) {
        elements.workspaceNavPrevBtn.disabled = !navigationEnabled || !previous;
        elements.workspaceNavPrevBtn.title = previous
          ? `${previous.ticketId}${previous.clientName ? ` · ${previous.clientName}` : ''}${previous.status ? ` · ${previous.status}` : ''}`
          : 'Предыдущий диалог недоступен';
      }
      if (elements.workspaceNavNextBtn) {
        elements.workspaceNavNextBtn.disabled = !navigationEnabled || !next;
        elements.workspaceNavNextBtn.title = next
          ? `${next.ticketId}${next.clientName ? ` · ${next.clientName}` : ''}${next.status ? ` · ${next.status}` : ''}`
          : 'Следующий диалог недоступен';
      }
      if (elements.workspaceNavState) {
        elements.workspaceNavState.textContent = buildWorkspaceNavigationStateText(navigation);
      }
    }

    async function navigateWorkspaceInline(direction) {
      if (!options.inlineNavigation) {
        return;
      }
      const safeDirection = direction === 'previous' ? 'previous' : 'next';
      const activeState = getActiveWorkspaceState();
      const navigation = activeState.payload?.meta?.navigation || null;
      const target = getWorkspaceNavigationTarget(safeDirection, navigation);
      if (!target?.ticketId) {
        options.openVisibleDialogByOffset?.(safeDirection === 'previous' ? -1 : 1);
        return;
      }
      const row = (options.rowsList?.() || []).find((item) => String(item.dataset.ticketId || '') === target.ticketId) || null;
      options.setActiveDialogRow?.(row, { ensureVisible: true });
      await options.emitWorkspaceTelemetry?.('workspace_inline_navigation', {
        ticketId: target.ticketId,
        reason: safeDirection,
        durationMs: Number.isFinite(Number(navigation?.position)) ? Number(navigation.position) : null,
        contractVersion: activeState.payload?.contract_version || 'workspace.v1',
      });
      await options.openDialogWithWorkspaceFallback?.(target.ticketId, row, {
        source: `inline_navigation_${safeDirection}`,
        channelId: target.channelId,
      });
    }

    async function refreshActiveWorkspaceContract(refreshOptions = {}) {
      const activeState = getActiveWorkspaceState();
      if (!activeState.ticketId) return;
      const payload = await options.preloadWorkspaceContract?.(activeState.ticketId, activeState.channelId, {
        limit: options.messagesPageLimit,
      });
      options.setActiveWorkspacePayload?.(payload);
      options.renderWorkspaceShell?.(payload);
      if (!refreshOptions.silent && refreshOptions.successMessage) {
        notify(refreshOptions.successMessage, 'success');
      }
    }

    function appendToWorkspaceComposer(text) {
      if (!elements.workspaceComposerText || !text) return;
      const value = String(elements.workspaceComposerText.value || '');
      const addition = String(text).trim();
      if (!addition) return;
      elements.workspaceComposerText.value = value ? `${value}\n\n${addition}` : addition;
      elements.workspaceComposerText.focus();
    }

    function renderWorkspaceSlaPolicyMarkup(policy) {
      const safePolicy = policy && typeof policy === 'object' ? policy : null;
      if (!safePolicy) {
        return '';
      }
      const policyIssues = Array.isArray(safePolicy.issues) ? safePolicy.issues.filter(Boolean) : [];
      const policyBadgeClass = (() => {
        switch (String(safePolicy.status || '').toLowerCase()) {
          case 'ready':
            return 'text-bg-success';
          case 'disabled':
            return 'text-bg-secondary';
          case 'invalid_utc':
          case 'attention':
            return 'text-bg-warning';
          default:
            return 'text-bg-light border';
        }
      })();
      const policyMeta = [];
      if (safePolicy.mode) policyMeta.push(`mode: ${String(safePolicy.mode).toLowerCase()}`);
      if (safePolicy.route) policyMeta.push(`route: ${String(safePolicy.route)}`);
      if (safePolicy.recommended_assignee) policyMeta.push(`owner: ${String(safePolicy.recommended_assignee)}`);
      if (safePolicy.evaluated_at_utc) policyMeta.push(`UTC ${formatWorkspaceDateTime(safePolicy.evaluated_at_utc)}`);
      return `<div class="border rounded px-2 py-2 mt-2 bg-light-subtle">
          <div class="d-flex flex-wrap align-items-center gap-2">
            <span class="fw-semibold small">SLA policy</span>
            <span class="badge ${policyBadgeClass}">${escapeHtml(String(safePolicy.status || 'unknown'))}</span>
            ${safePolicy.action ? `<span class="badge text-bg-light border">${escapeHtml(String(safePolicy.action))}</span>` : ''}
          </div>
          ${policyMeta.length ? `<div class="small text-muted mt-1">${escapeHtml(policyMeta.join(' · '))}</div>` : ''}
          ${safePolicy.summary ? `<div class="small text-muted mt-1">${escapeHtml(String(safePolicy.summary))}</div>` : ''}
          ${policyIssues.length ? `<div class="small text-warning mt-1">Issues: ${escapeHtml(policyIssues.join(', '))}</div>` : ''}
        </div>`;
    }

    function normalizeWorkspaceContextViolationDetails(details) {
      if (!Array.isArray(details)) {
        return [];
      }
      return details
        .filter((item) => item && typeof item === 'object')
        .map((item) => {
          const playbook = item.playbook && typeof item.playbook === 'object' ? item.playbook : null;
          const playbookUrl = String(playbook?.url || '').trim();
          return {
            type: String(item.type || '').trim(),
            code: String(item.code || '').trim(),
            severity: String(item.severity || '').trim().toLowerCase(),
            severityRank: Number(item.severity_rank || 0),
            shortLabel: String(item.short_label || item.operator_message || item.analytics_message || item.code || '').trim(),
            operatorMessage: String(item.operator_message || item.analytics_message || item.code || '').trim(),
            nextStep: String(item.next_step || '').trim(),
            actionLabel: String(item.action_label || '').trim(),
            analyticsMessage: String(item.analytics_message || '').trim(),
            playbookLabel: String(playbook?.label || 'Playbook').trim() || 'Playbook',
            playbookUrl: /^https?:\/\//i.test(playbookUrl) ? playbookUrl : '',
            playbookSummary: String(playbook?.summary || '').trim(),
          };
        })
        .sort((left, right) => {
          const severityDelta = Number(right.severityRank || 0) - Number(left.severityRank || 0);
          if (severityDelta !== 0) {
            return severityDelta;
          }
          return String(left.shortLabel || '').localeCompare(String(right.shortLabel || ''), 'ru');
        })
        .filter((item) => item.operatorMessage);
    }

    function workspaceContextViolationTypeLabel(type) {
      switch (String(type || '').trim().toLowerCase()) {
        case 'mandatory_field':
          return 'Mandatory field';
        case 'source_of_truth':
          return 'Source of truth';
        case 'priority_block':
          return 'Priority block';
        default:
          return 'Context gap';
      }
    }

    function workspaceContextViolationSeverityLabel(severity) {
      switch (String(severity || '').trim().toLowerCase()) {
        case 'high':
          return 'Срочно';
        case 'medium':
          return 'Нужно действие';
        default:
          return 'К сведению';
      }
    }

    function workspaceContextViolationSeverityBadge(severity) {
      switch (String(severity || '').trim().toLowerCase()) {
        case 'high':
          return 'text-bg-danger';
        case 'medium':
          return 'text-bg-warning';
        default:
          return 'text-bg-secondary';
      }
    }

    function isWorkspaceClientExtraValue(value) {
      if (value === null || value === undefined) return false;
      if (Array.isArray(value)) return value.length > 0;
      if (typeof value === 'object') return Object.keys(value).length > 0;
      return String(value).trim() !== '';
    }

    function formatWorkspaceClientExtraValue(value) {
      if (Array.isArray(value)) {
        return value.map((item) => formatWorkspaceClientExtraValue(item)).filter(Boolean).join(', ');
      }
      if (value && typeof value === 'object') {
        return Object.entries(value)
          .map(([key, nested]) => `${prettifyWorkspaceClientExtraKey(key)}: ${formatWorkspaceClientExtraValue(nested)}`)
          .join('; ');
      }
      return String(value);
    }

    function prettifyWorkspaceClientExtraKey(key) {
      return String(key || '')
        .replace(/[_-]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim()
        .replace(/^./, (char) => char.toUpperCase()) || 'Атрибут';
    }

    function normalizeWorkspaceClientAttributeKey(key) {
      return String(key || '')
        .trim()
        .toLowerCase()
        .replace(/[^a-z0-9_]+/g, '_')
        .replace(/^_+|_+$/g, '')
        .replace(/_+/g, '_');
    }

    function resolveWorkspaceClientAttributeLabelMap(rawLabels) {
      const labels = new Map();
      if (!rawLabels || typeof rawLabels !== 'object') {
        return labels;
      }
      Object.entries(rawLabels).forEach(([key, value]) => {
        const normalizedKey = normalizeWorkspaceClientAttributeKey(key);
        const label = String(value || '').trim();
        if (!normalizedKey || !label) return;
        labels.set(normalizedKey, label);
      });
      return labels;
    }

    function resolveWorkspaceClientAttributeOrder(rawOrder) {
      if (!Array.isArray(rawOrder)) {
        return [];
      }
      const order = [];
      rawOrder.forEach((item) => {
        const normalizedKey = normalizeWorkspaceClientAttributeKey(item);
        if (!normalizedKey || order.includes(normalizedKey)) return;
        order.push(normalizedKey);
      });
      return order;
    }

    function orderWorkspaceClientExtraEntries(entries, order) {
      if (!Array.isArray(entries) || entries.length === 0 || !Array.isArray(order) || order.length === 0) {
        return Array.isArray(entries) ? entries : [];
      }
      const priority = new Map(order.map((key, index) => [key, index]));
      return [...entries].sort(([leftKey], [rightKey]) => {
        const leftPriority = priority.has(normalizeWorkspaceClientAttributeKey(leftKey))
          ? priority.get(normalizeWorkspaceClientAttributeKey(leftKey))
          : Number.POSITIVE_INFINITY;
        const rightPriority = priority.has(normalizeWorkspaceClientAttributeKey(rightKey))
          ? priority.get(normalizeWorkspaceClientAttributeKey(rightKey))
          : Number.POSITIVE_INFINITY;
        if (leftPriority !== rightPriority) {
          return leftPriority - rightPriority;
        }
        return String(leftKey).localeCompare(String(rightKey), 'ru');
      });
    }

    function renderWorkspaceClientProfile(client, context = {}) {
      if (!client || typeof client !== 'object') {
        return '<div class="small text-muted">Профиль клиента недоступен.</div>';
      }
      const fields = [
        ['ID', client.id],
        ['Username', client.username],
        ['Статус', client.status],
        ['Канал', client.channel],
        ['Бизнес', client.business],
        ['Локация', client.location],
        ['Ответственный', client.responsible],
        ['Непрочитанные', client.unread_count],
        ['Оценка', client.rating],
        ['Последнее сообщение', formatWorkspaceDateTime(client.last_message_at)],
        ['Всего диалогов', client.total_dialogs],
        ['Открытых диалогов', client.open_dialogs],
        ['Закрыто за 30 дней', client.resolved_30d],
        ['Первое обращение', formatWorkspaceDateTime(client.first_seen_at)],
        ['Последняя активность тикета', formatWorkspaceDateTime(client.last_ticket_activity_at)],
        ['Язык', client.language],
      ];
      const reservedClientKeys = new Set([
        'id',
        'name',
        'username',
        'status',
        'channel',
        'business',
        'location',
        'responsible',
        'unread_count',
        'rating',
        'last_message_at',
        'total_dialogs',
        'open_dialogs',
        'resolved_30d',
        'first_seen_at',
        'last_ticket_activity_at',
        'language',
        'segments',
        'context_sources',
        'context_contract',
        'external_links',
        'attribute_labels',
        'attribute_order',
      ]);
      const rows = fields
        .filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== '')
        .map(([label, value]) => `<div class="small text-muted">${escapeHtml(label)}: <span class="text-body">${escapeHtml(String(value))}</span></div>`)
        .join('');

      const profileHealth = client.profile_health && typeof client.profile_health === 'object' ? client.profile_health : null;
      const missingFields = Array.isArray(profileHealth?.missing_field_labels)
        ? profileHealth.missing_field_labels.filter(Boolean)
        : [];
      const activeProfileSegments = Array.isArray(profileHealth?.active_segments)
        ? profileHealth.active_segments.filter(Boolean)
        : [];
      const totalRequiredProfileFields = Array.isArray(profileHealth?.required_fields)
        ? profileHealth.required_fields.filter(Boolean).length
        : 0;
      const profileRuleSummary = [];
      if (totalRequiredProfileFields > 0) {
        profileRuleSummary.push(`Обязательных полей: ${totalRequiredProfileFields}`);
      }
      if (activeProfileSegments.length) {
        profileRuleSummary.push(`Сегменты: ${activeProfileSegments.join(', ')}`);
      }
      const healthBanner = profileHealth && profileHealth.enabled === true
        ? `<div class="alert ${profileHealth.ready ? 'alert-success' : 'alert-warning'} py-2 px-3 small mb-2">${profileHealth.ready ? `Контекст клиента готов (${Number(profileHealth.coverage_pct || 100)}%).` : `Нужно дозаполнить контекст (${Number(profileHealth.coverage_pct || 0)}%): ${escapeHtml(missingFields.join(', ') || 'нет обязательных полей')}.`}${profileRuleSummary.length ? `<div class="text-muted mt-1">${escapeHtml(profileRuleSummary.join(' · '))}</div>` : ''}<div class="text-muted mt-1">Проверено: ${escapeHtml(formatWorkspaceDateTime(profileHealth.checked_at_utc || profileHealth.checked_at))}</div></div>`
        : '';
      const profileMatchCandidates = context?.profile_match_candidates && typeof context.profile_match_candidates === 'object'
        ? context.profile_match_candidates
        : (client?.profile_match_candidates && typeof client.profile_match_candidates === 'object'
          ? client.profile_match_candidates
          : null);
      const profileMatchFields = Array.isArray(profileMatchCandidates?.fields)
        ? profileMatchCandidates.fields.filter((item) => item && typeof item === 'object')
        : [];
      const profileMatchReviewFields = profileMatchFields.filter((item) => Array.isArray(item.candidates) && item.candidates.length > 0);
      const clientCardUrl = Number.isFinite(Number(client?.id)) ? `/client/${Number(client.id)}` : null;
      const profileMatchSection = profileMatchCandidates && profileMatchFields.length
        ? `<details class="mt-2"${profileMatchReviewFields.length ? ' open' : ''}>
            <summary class="small fw-semibold">Сопоставление данных <span class="text-muted fw-normal">(${profileMatchReviewFields.length}/${profileMatchFields.length})</span></summary>
            <div class="small text-muted mt-1">Проверьте предложенные совпадения по бизнесу, локации, городу и стране.</div>
            <div class="d-flex flex-column gap-2 mt-2">
              ${profileMatchFields.map((field) => {
                const incomingValue = String(field?.incoming_value || '').trim();
                const label = String(field?.label || field?.field || 'field').trim();
                const candidates = Array.isArray(field?.candidates) ? field.candidates.slice(0, 3) : [];
                const candidateLine = candidates.length
                  ? candidates.map((candidate) => {
                    const value = String(candidate?.value || '').trim();
                    const matchType = String(candidate?.match_type || '').trim();
                    const confidence = Number(candidate?.confidence || 0);
                    const confidenceLabel = Number.isFinite(confidence) ? `${Math.round(confidence * 100)}%` : '';
                    return `${escapeHtml(value)}${matchType ? ` (${escapeHtml(matchType)})` : ''}${confidenceLabel ? ` · ${escapeHtml(confidenceLabel)}` : ''}`;
                  }).join('; ')
                  : 'нет совпадений';
                return `<div class="border rounded px-2 py-1 bg-white">
                    <div class="small"><span class="fw-semibold">${escapeHtml(label)}:</span> <span class="text-body">${escapeHtml(incomingValue || '—')}</span></div>
                    <div class="small text-muted">Кандидаты: ${candidateLine}</div>
                  </div>`;
              }).join('')}
            </div>
            ${clientCardUrl ? `<a class="btn btn-sm btn-outline-primary mt-2" href="${clientCardUrl}" target="_blank" rel="noopener noreferrer">Открыть карточку клиента для правок</a>` : ''}
          </details>`
        : '';

      const contextBlocks = Array.isArray(context?.blocks)
        ? context.blocks.filter((item) => item && typeof item === 'object')
        : [];
      const contextBlocksHealth = context?.blocks_health && typeof context.blocks_health === 'object'
        ? context.blocks_health
        : null;
      const contextBlockBadgeClass = (status) => {
        switch (String(status || '').toLowerCase()) {
          case 'ready':
            return 'text-bg-success';
          case 'attention':
          case 'stale':
            return 'text-bg-warning';
          case 'missing':
          case 'invalid_utc':
          case 'unavailable':
            return 'text-bg-danger';
          default:
            return 'text-bg-secondary';
        }
      };
      const contextBlocksSection = contextBlocksHealth && contextBlocksHealth.enabled === true && contextBlocks.length
        ? `<div class="alert ${contextBlocksHealth.ready ? 'alert-success' : 'alert-warning'} py-2 px-3 small mb-2">
            <div class="fw-semibold mb-1">Приоритет customer context</div>
            <div>${contextBlocksHealth.ready
              ? `Все обязательные блоки готовы (${Number(contextBlocksHealth.coverage_pct || 100)}%).`
              : `Покрытие обязательных блоков: ${Number(contextBlocksHealth.coverage_pct || 0)}%. Не хватает: ${escapeHtml((Array.isArray(contextBlocksHealth.missing_required_labels) ? contextBlocksHealth.missing_required_labels : []).join(', ') || '—')}.`}</div>
            <div class="d-flex flex-column gap-2 mt-2">
              ${contextBlocks.map((block) => {
                const meta = [];
                if (Number.isFinite(Number(block.priority))) {
                  meta.push(`P${Number(block.priority)}`);
                }
                if (block.required === true) {
                  meta.push('required');
                }
                if (block.updated_at_utc) {
                  meta.push(`UTC ${formatWorkspaceDateTime(block.updated_at_utc)}`);
                }
                return `<div class="border rounded px-2 py-1 bg-white">
                  <div class="d-flex flex-wrap align-items-center gap-2">
                    <span class="fw-semibold">${escapeHtml(block.label || block.key || 'Блок')}</span>
                    <span class="badge ${contextBlockBadgeClass(block.status)}">${escapeHtml(String(block.status || 'unavailable'))}</span>
                  </div>
                  ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
                  ${block.summary ? `<div class="small text-muted">${escapeHtml(String(block.summary))}</div>` : ''}
                </div>`;
              }).join('')}
            </div>
          </div>`
        : '';

      const extraAttributeLabelMap = resolveWorkspaceClientAttributeLabelMap(client.attribute_labels);
      const extraAttributeOrder = resolveWorkspaceClientAttributeOrder(client.attribute_order);
      const orderedExtraEntries = orderWorkspaceClientExtraEntries(
        Object.entries(client)
          .filter(([key, value]) => !reservedClientKeys.has(key) && isWorkspaceClientExtraValue(value)),
        extraAttributeOrder,
      );
      const hiddenAttributes = options.hiddenAttributes instanceof Set ? options.hiddenAttributes : new Set();
      const technicalPrefixes = Array.isArray(options.extraAttributesTechnicalPrefixes)
        ? options.extraAttributesTechnicalPrefixes
        : [];
      const filteredExtraEntries = orderedExtraEntries
        .filter(([key]) => {
          const normalized = normalizeWorkspaceClientAttributeKey(key);
          if (!normalized) {
            return false;
          }
          if (hiddenAttributes.has(normalized)) {
            return false;
          }
          if (options.extraAttributesHideTechnical === false) {
            return true;
          }
          return !technicalPrefixes.some((prefix) => prefix && normalized.startsWith(prefix));
        });
      const extraAttributesMax = Number(options.extraAttributesMax) || filteredExtraEntries.length;
      const collapseAfter = Number(options.extraAttributesCollapseAfter) || filteredExtraEntries.length;
      const limitedExtraEntries = filteredExtraEntries.slice(0, extraAttributesMax);
      const hiddenByLimitCount = Math.max(0, filteredExtraEntries.length - limitedExtraEntries.length);
      const extraAttributeTotalCount = filteredExtraEntries.length;
      const expandedExtraEntries = limitedExtraEntries.slice(0, collapseAfter);
      const collapsedExtraEntries = limitedExtraEntries.slice(collapseAfter);

      const renderExtraRow = ([key, value]) => {
        const label = extraAttributeLabelMap.get(normalizeWorkspaceClientAttributeKey(key)) || prettifyWorkspaceClientExtraKey(key);
        return `<div class="small text-muted">${escapeHtml(label)}: <span class="text-body">${escapeHtml(formatWorkspaceClientExtraValue(value))}</span></div>`;
      };

      const expandedRows = expandedExtraEntries.map((entry) => renderExtraRow(entry)).join('');
      const collapsedRows = collapsedExtraEntries.map(([key, value]) => renderExtraRow([key, value])).join('');

      const segments = Array.isArray(client.segments) ? client.segments.filter(Boolean) : [];
      const segmentBadges = segments.length
        ? `<div class="d-flex flex-wrap gap-1 mt-2">${segments.map((segment) => `<span class="badge text-bg-light border">${escapeHtml(segment)}</span>`).join('')}</div>`
        : '';

      const contextSources = Array.isArray(client.context_sources)
        ? client.context_sources.filter((item) => item && typeof item === 'object')
        : [];
      const sourceBadgeClass = (status) => {
        switch (String(status || '').toLowerCase()) {
          case 'ready':
            return 'text-bg-success';
          case 'stale':
            return 'text-bg-warning';
          case 'invalid_utc':
          case 'missing':
            return 'text-bg-danger';
          default:
            return 'text-bg-secondary';
        }
      };
      const sourceStatusLabel = (status) => {
        switch (String(status || '').toLowerCase()) {
          case 'ready':
            return 'ready';
          case 'stale':
            return 'stale';
          case 'invalid_utc':
            return 'invalid UTC';
          case 'missing':
            return 'missing';
          default:
            return 'optional';
        }
      };
      const contextSourceIssueCount = contextSources.filter((source) => {
        const status = String(source?.status || '').toLowerCase();
        return ['missing', 'stale', 'invalid_utc'].includes(status);
      }).length;
      const contextSourceRequiredCount = contextSources.filter((source) => source?.required === true).length;
      const contextSourcesSection = contextSources.length
        ? `<details class="mt-2" data-workspace-telemetry-section="context_sources" data-workspace-telemetry-items="${contextSources.length}" data-workspace-telemetry-required="${contextSourceRequiredCount}" data-workspace-telemetry-gaps="${contextSourceIssueCount}" data-workspace-telemetry-hidden="0"${contextSourceIssueCount > 0 ? ' open' : ''}>
            <summary class="small fw-semibold">Источники контекста <span class="text-muted fw-normal">(${contextSources.length}; required ${contextSourceRequiredCount}; gaps ${contextSourceIssueCount})</span></summary>
            <div class="small text-muted mt-1">Свернуто по умолчанию, чтобы primary customer-context оставался выше policy-шума.</div>
            <div class="d-flex flex-column gap-2 mt-2">
              ${contextSources.map((source) => {
                const meta = [];
                if (Number.isFinite(Number(source.matched_attribute_count)) && Number(source.matched_attribute_count) > 0) {
                  meta.push(`${Number(source.matched_attribute_count)} атр.`);
                }
                if (source.updated_at_utc) {
                  meta.push(`UTC ${formatWorkspaceDateTime(source.updated_at_utc)}`);
                } else if (source.updated_at_raw && String(source.status || '').toLowerCase() === 'invalid_utc') {
                  meta.push(`invalid: ${String(source.updated_at_raw).trim()}`);
                }
                if (source.linked === true) {
                  meta.push('есть ссылка');
                }
                return `<div class="border rounded px-2 py-1">
                  <div class="d-flex flex-wrap align-items-center gap-2">
                    <span class="fw-semibold">${escapeHtml(source.label || source.key || 'Источник')}</span>
                    <span class="badge ${sourceBadgeClass(source.status)}">${escapeHtml(sourceStatusLabel(source.status))}</span>
                    ${source.required === true ? '<span class="badge text-bg-light border">required</span>' : ''}
                  </div>
                  ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
                  ${source.summary ? `<div class="small text-muted">${escapeHtml(String(source.summary))}</div>` : ''}
                </div>`;
              }).join('')}
            </div></details>`
        : '';

      const attributePolicies = Array.isArray(client.attribute_policies)
        ? client.attribute_policies.filter((item) => item && typeof item === 'object')
        : [];
      const attributePolicyBadgeClass = (status) => {
        switch (String(status || '').toLowerCase()) {
          case 'ready':
            return 'text-bg-success';
          case 'stale':
            return 'text-bg-warning';
          case 'missing':
          case 'invalid_utc':
          case 'unavailable':
            return 'text-bg-danger';
          default:
            return 'text-bg-secondary';
        }
      };
      const attributePolicyStatusLabel = (status) => {
        switch (String(status || '').toLowerCase()) {
          case 'ready':
            return 'ready';
          case 'stale':
            return 'stale';
          case 'missing':
            return 'missing';
          case 'invalid_utc':
            return 'invalid UTC';
          case 'unavailable':
            return 'unavailable';
          default:
            return 'untracked';
        }
      };
      const attributePolicyIssueCount = attributePolicies.filter((policy) => {
        const status = String(policy?.status || '').toLowerCase();
        return ['missing', 'stale', 'invalid_utc', 'unavailable'].includes(status);
      }).length;
      const attributePolicyRequiredCount = attributePolicies.filter((policy) => policy?.required === true).length;
      const attributePoliciesSection = attributePolicies.length
        ? `<details class="mt-2" data-workspace-telemetry-section="attribute_policy" data-workspace-telemetry-items="${attributePolicies.length}" data-workspace-telemetry-required="${attributePolicyRequiredCount}" data-workspace-telemetry-gaps="${attributePolicyIssueCount}" data-workspace-telemetry-hidden="0"${attributePolicyIssueCount > 0 ? ' open' : ''}>
            <summary class="small fw-semibold">Source / freshness policy <span class="text-muted fw-normal">(${attributePolicies.length}; required ${attributePolicyRequiredCount}; gaps ${attributePolicyIssueCount})</span></summary>
            <div class="small text-muted mt-1">Вторичные policy-детали раскрываются отдельно, чтобы не конкурировать с action-oriented contract summary.</div>
            <div class="d-flex flex-column gap-2 mt-2">
              ${attributePolicies.map((policy) => {
                const meta = [];
                if (policy.source_label || policy.source_key) {
                  meta.push(`source: ${String(policy.source_label || policy.source_key).trim()}`);
                }
                if (Number.isFinite(Number(policy.freshness_ttl_hours)) && Number(policy.freshness_ttl_hours) > 0) {
                  meta.push(`TTL ${Number(policy.freshness_ttl_hours)}h`);
                } else if (policy.freshness_required === true) {
                  meta.push('freshness required');
                }
                if (policy.updated_at_utc) {
                  meta.push(`UTC ${formatWorkspaceDateTime(policy.updated_at_utc)}`);
                } else if (policy.updated_at_raw && String(policy.status || '').toLowerCase() === 'invalid_utc') {
                  meta.push(`invalid: ${String(policy.updated_at_raw).trim()}`);
                }
                return `<div class="border rounded px-2 py-1">
                  <div class="d-flex flex-wrap align-items-center gap-2">
                    <span class="fw-semibold">${escapeHtml(policy.label || policy.key || 'Attribute')}</span>
                    <span class="badge ${attributePolicyBadgeClass(policy.status)}">${escapeHtml(attributePolicyStatusLabel(policy.status))}</span>
                    ${policy.required === true ? '<span class="badge text-bg-light border">required</span>' : ''}
                  </div>
                  ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
                  ${policy.summary ? `<div class="small text-muted">${escapeHtml(String(policy.summary))}</div>` : ''}
                </div>`;
              }).join('')}
            </div></details>`
        : '';

      const contract = context?.contract && typeof context.contract === 'object'
        ? context.contract
        : (client.context_contract && typeof client.context_contract === 'object' ? client.context_contract : null);
      const contractViolations = Array.isArray(contract?.violations) ? contract.violations.filter(Boolean) : [];
      const contractViolationDetails = normalizeWorkspaceContextViolationDetails(contract?.violation_details);
      const primaryViolationPayload = Array.isArray(contract?.primary_violation_details)
        ? normalizeWorkspaceContextViolationDetails(contract.primary_violation_details)
        : [];
      const primaryViolationCards = primaryViolationPayload.length
        ? primaryViolationPayload
        : contractViolationDetails.slice(0, 2);
      const extraViolationCards = contractViolationDetails.slice(primaryViolationCards.length);
      const deferredViolationCount = Number(contract?.deferred_violation_count || extraViolationCards.length || 0);
      const contractOperatorSummary = String(contract?.operator_summary || '').trim();
      const contractNextStepSummary = String(contract?.next_step_summary || '').trim();
      const focusBlocks = Array.isArray(contract?.operator_focus_blocks) ? contract.operator_focus_blocks : [];
      const progressiveDisclosureReady = contract?.progressive_disclosure_ready === true;
      const renderViolationCard = (detail) => `<div class="border rounded px-2 py-2 bg-white">
          <div class="d-flex flex-wrap align-items-center gap-2">
            <span class="badge ${workspaceContextViolationSeverityBadge(detail.severity)}">${escapeHtml(workspaceContextViolationSeverityLabel(detail.severity))}</span>
            <span class="badge text-bg-light border">${escapeHtml(workspaceContextViolationTypeLabel(detail.type))}</span>
            ${detail.code ? `<span class="small text-muted">${escapeHtml(detail.code)}</span>` : ''}
          </div>
          <div class="mt-1 fw-semibold">${escapeHtml(detail.shortLabel || detail.operatorMessage)}</div>
          <div class="small text-muted mt-1">${escapeHtml(detail.operatorMessage)}</div>
          ${detail.nextStep ? `<div class="small mt-1"><span class="text-muted">Следующий шаг:</span> ${escapeHtml(detail.nextStep)}</div>` : ''}
          ${detail.playbookUrl
            ? `<div class="small mt-1"><a href="${escapeHtml(detail.playbookUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(detail.actionLabel || detail.playbookLabel)}</a>${detail.playbookSummary ? ` <span class="text-muted">· ${escapeHtml(detail.playbookSummary)}</span>` : ''}</div>`
            : ''}
        </div>`;
      const violationActionsSection = contract.ready === true || contractViolationDetails.length === 0
        ? ''
        : `<div class="d-flex flex-column gap-2 mt-2">
            ${primaryViolationCards.map((detail) => renderViolationCard(detail)).join('')}
            ${extraViolationCards.length
              ? `<details${progressiveDisclosureReady ? '' : ' open'}><summary class="small text-muted">Показать ещё ${deferredViolationCount}</summary><div class="d-flex flex-column gap-2 mt-2">${extraViolationCards.map((detail) => renderViolationCard(detail)).join('')}</div></details>`
              : ''}
          </div>`;
      const contextContractSection = contract && contract.enabled === true
        ? `<div class="alert ${contract.ready === true ? 'alert-success' : 'alert-warning'} py-2 px-3 small mt-2 mb-2">
            <div class="fw-semibold mb-1">Context contract</div>
            <div>${contractOperatorSummary || (contract.ready === true
              ? 'Minimum profile complete.'
              : contractViolationDetails.length
                ? `Need to close ${contractViolationDetails.length} context-gap item(s).`
                : `Deviations found: ${escapeHtml(contractViolations.join(', ') || 'contract_not_ready')}.`)}</div>
            ${focusBlocks.length
              ? `<div class="mt-1"><span class="text-muted">Operator first:</span> ${escapeHtml(focusBlocks.join(', '))}</div>`
              : ''}
            ${contractNextStepSummary
              ? `<div class="mt-1"><span class="text-muted">Что сделать:</span> ${escapeHtml(contractNextStepSummary)}</div>`
              : (Array.isArray(contract.action_items) && contract.action_items.length
                ? `<div class="mt-1"><span class="text-muted">Что сделать:</span> ${escapeHtml(contract.action_items[0])}</div>`
                : '')}
            ${deferredViolationCount > 0
              ? `<div class="mt-1 text-muted">Остальные детали скрыты до раскрытия: ${deferredViolationCount}.</div>`
              : ''}
            ${violationActionsSection}
            <div class="text-muted mt-1">Проверено: ${escapeHtml(formatWorkspaceDateTime(contract.checked_at_utc || contract.checked_at))}</div>
          </div>`
        : '';

      const extraSection = expandedRows || collapsedRows
        ? `<details class="mt-2" data-workspace-telemetry-section="extra_attributes" data-workspace-telemetry-items="${limitedExtraEntries.length}" data-workspace-telemetry-required="0" data-workspace-telemetry-gaps="0" data-workspace-telemetry-hidden="${hiddenByLimitCount}">
            <summary class="small fw-semibold">Доп. атрибуты <span class="text-muted fw-normal">(${extraAttributeTotalCount}; visible ${limitedExtraEntries.length}${hiddenByLimitCount > 0 ? `; hidden ${hiddenByLimitCount}` : ''})</span></summary>
            <div class="small text-muted mt-1">Второстепенные поля скрыты до раскрытия, чтобы не конкурировать с customer-context contract.</div>
            <div class="mt-2">${expandedRows}</div>
            ${collapsedRows ? `<details class="mt-1"><summary class="small text-muted">Показать ещё ${collapsedExtraEntries.length}</summary><div class="mt-1">${collapsedRows}</div></details>` : ''}
            ${hiddenByLimitCount > 0 ? `<div class="small text-muted mt-1">Скрыто по лимиту: ${hiddenByLimitCount}.</div>` : ''}
          </details>`
        : '';

      const externalLinks = (client.external_links && typeof client.external_links === 'object')
        ? Object.values(client.external_links)
          .filter((item) => item && typeof item === 'object')
          .map((item) => {
            const url = String(item.url || '').trim();
            if (!url || !(url.startsWith('http://') || url.startsWith('https://'))) {
              return '';
            }
            const label = String(item.label || 'Профиль').trim() || 'Профиль';
            return `<a class="btn btn-sm btn-outline-secondary" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`;
          })
          .filter(Boolean)
        : [];
      const linksSection = externalLinks.length > 0
        ? `<div class="d-flex flex-wrap gap-2 mt-2">${externalLinks.join('')}</div>`
        : '';

      return `<div class="small"><strong>${escapeHtml(client.name || '—')}</strong></div>${profileMatchSection}${healthBanner}${contextBlocksSection}${contextContractSection}${rows || '<div class="small text-muted">Дополнительные атрибуты отсутствуют.</div>'}${contextSourcesSection}${attributePoliciesSection}${extraSection}${segmentBadges}${linksSection}`;
    }

    function renderWorkspaceClientSection(context) {
      const client = context?.client;
      if (client && Object.keys(client).length) {
        if (elements.workspaceClientState) elements.workspaceClientState.classList.add('d-none');
        if (elements.workspaceClientError) elements.workspaceClientError.classList.add('d-none');
        if (elements.workspaceClientContent) {
          elements.workspaceClientContent.classList.remove('d-none');
          elements.workspaceClientContent.innerHTML = renderWorkspaceClientProfile(client, context);
          options.bindWorkspaceContextDisclosureTelemetry?.(elements.workspaceClientContent);
        }
      } else {
        if (elements.workspaceClientState) elements.workspaceClientState.classList.add('d-none');
        if (elements.workspaceClientContent) elements.workspaceClientContent.classList.add('d-none');
        if (elements.workspaceClientError) elements.workspaceClientError.classList.remove('d-none');
      }
    }

    function renderWorkspaceHistorySection(context) {
      const contextHistory = Array.isArray(context?.history) ? context.history : null;
      if (contextHistory) {
        if (elements.workspaceHistoryState) elements.workspaceHistoryState.classList.add('d-none');
        if (elements.workspaceHistoryError) elements.workspaceHistoryError.classList.add('d-none');
        if (elements.workspaceHistoryContent) {
          elements.workspaceHistoryContent.classList.remove('d-none');
          elements.workspaceHistoryContent.innerHTML = renderWorkspaceSimpleList(contextHistory, (item) => {
            const ticketId = escapeHtml(item.ticket_id || item.ticketId || '—');
            const status = escapeHtml(item.status || '—');
            const createdAt = escapeHtml(formatWorkspaceDateTime(item.created_at || item.createdAt));
            const problem = escapeHtml(item.problem || 'Без описания');
            return `<div><strong>#${ticketId}</strong> · <span class="text-muted">${status}</span></div><div class="text-muted">${createdAt}</div><div>${problem}</div>`;
          });
        }
      } else {
        if (elements.workspaceHistoryState) elements.workspaceHistoryState.classList.add('d-none');
        if (elements.workspaceHistoryContent) elements.workspaceHistoryContent.classList.add('d-none');
        if (elements.workspaceHistoryError) elements.workspaceHistoryError.classList.remove('d-none');
      }
    }

    function renderWorkspaceRelatedEventsSection(context) {
      const relatedEvents = Array.isArray(context?.related_events) ? context.related_events : null;
      if (relatedEvents) {
        if (elements.workspaceRelatedEventsState) elements.workspaceRelatedEventsState.classList.add('d-none');
        if (elements.workspaceRelatedEventsError) elements.workspaceRelatedEventsError.classList.add('d-none');
        if (elements.workspaceRelatedEventsContent) {
          elements.workspaceRelatedEventsContent.classList.remove('d-none');
          elements.workspaceRelatedEventsContent.innerHTML = renderWorkspaceSimpleList(relatedEvents, (item) => {
            const actor = escapeHtml(item.actor || 'Система');
            const type = escapeHtml(item.type || 'event');
            const timestamp = escapeHtml(formatWorkspaceDateTime(item.timestamp));
            const detail = escapeHtml(item.detail || '—');
            return `<div><strong>${actor}</strong> · <span class="text-muted">${type}</span></div><div class="text-muted">${timestamp}</div><div>${detail}</div>`;
          });
        }
      } else {
        if (elements.workspaceRelatedEventsState) elements.workspaceRelatedEventsState.classList.add('d-none');
        if (elements.workspaceRelatedEventsContent) elements.workspaceRelatedEventsContent.classList.add('d-none');
        if (elements.workspaceRelatedEventsError) elements.workspaceRelatedEventsError.classList.remove('d-none');
      }
    }

    function renderWorkspaceSlaSection(sla) {
      if (sla && sla.state && sla.state !== 'unknown') {
        if (elements.workspaceSlaState) elements.workspaceSlaState.classList.add('d-none');
        if (elements.workspaceSlaError) elements.workspaceSlaError.classList.add('d-none');
        if (elements.workspaceSlaContent) {
          const badgeClass = options.resolveWorkspaceSlaBadgeClass?.(sla.state) || 'text-bg-success';
          const remaining = options.formatWorkspaceSlaRemaining?.(sla.minutes_left) || '—';
          const policyMarkup = renderWorkspaceSlaPolicyMarkup(sla.policy);
          const escalationHint = sla.escalation_required === true
            ? '<div class="small text-danger mt-1">Требуется эскалация: окно SLA критичное.</div>'
            : '';
          elements.workspaceSlaContent.classList.remove('d-none');
          elements.workspaceSlaContent.innerHTML = `<div class="small">Состояние: <span class="badge ${badgeClass}">${escapeHtml(sla.state)}</span></div><div class="small text-muted">До дедлайна: ${escapeHtml(remaining)}</div><div class="small text-muted">Дедлайн: ${escapeHtml(formatWorkspaceDateTime(sla.deadline_at))}</div>${escalationHint}${policyMarkup}`;
        }
      } else {
        if (elements.workspaceSlaState) elements.workspaceSlaState.classList.add('d-none');
        if (elements.workspaceSlaContent) elements.workspaceSlaContent.classList.add('d-none');
        if (elements.workspaceSlaError) elements.workspaceSlaError.classList.remove('d-none');
      }
    }

    function renderWorkspaceShell(payload) {
      if (!elements.workspaceShell) return;
      elements.workspaceShell.classList.remove('d-none');

      const activeState = getActiveWorkspaceState();
      const currentDialogContext = typeof options.getActiveDialogContext === 'function'
        ? options.getActiveDialogContext()
        : {};
      const conversation = payload?.conversation || {};
      const messages = payload?.messages || {};
      const context = payload?.context || {};
      const sla = payload?.sla || {};
      const permissions = payload?.permissions;
      const composer = payload?.composer || {};
      const parity = payload?.meta?.parity || null;
      const navigation = payload?.meta?.navigation || null;
      const workspaceClient = context?.client && typeof context.client === 'object' ? context.client : {};

      options.setActiveDialogContext?.({
        clientName: String(workspaceClient?.name || conversation?.clientName || conversation?.username || currentDialogContext.clientName || '—').trim() || '—',
        clientUserId: String(workspaceClient?.id || options.getDialogUserId?.(conversation) || currentDialogContext.clientUserId || '').trim(),
        operatorName: String(conversation?.responsible || options.operatorDisplayName || currentDialogContext.operatorName || 'Оператор').trim() || 'Оператор',
        channelName: String(conversation?.channelName || workspaceClient?.channel || currentDialogContext.channelName || '—').trim() || '—',
        business: String(conversation?.business || workspaceClient?.business || currentDialogContext.business || '—').trim() || '—',
        location: String(workspaceClient?.location || conversation?.locationName || conversation?.city || currentDialogContext.location || '—').trim() || '—',
        status: String(conversation?.statusLabel || conversation?.status || currentDialogContext.status || '—').trim() || '—',
        createdAt: formatWorkspaceDateTime(conversation?.createdAt || conversation?.created_at || currentDialogContext.createdAt || '—'),
      });

      const readonlyReason = options.resolveWorkspaceReadonlyReason?.(permissions);
      const readonlyMode = Boolean(readonlyReason);
      options.setWorkspaceReadonlyMode?.(readonlyMode, readonlyReason);
      options.setSelectedCategories?.(new Set(options.normalizeCategories?.(
        conversation.categoriesSafe || conversation.categories || activeState.row?.dataset?.categories || ''
      ) || []));
      resetWorkspaceReplyTarget({ emitTelemetry: false });
      options.setWorkspaceComposerTicketId?.(String(conversation.ticketId || '').trim());
      const nextComposerTicketId = String(conversation.ticketId || '').trim();
      restoreWorkspaceDraft(nextComposerTicketId);
      options.loadWorkspaceAiSuggestions?.(nextComposerTicketId);
      options.loadWorkspaceAiReview?.(nextComposerTicketId);
      options.loadWorkspaceAiControl?.(nextComposerTicketId);

      const composerMeta = typeof options.getWorkspaceComposerMeta === 'function'
        ? options.getWorkspaceComposerMeta()
        : { hasActiveMacroTemplate: false, macroTemplatesLength: 0 };
      const canReplyInWorkspace = permissions && permissions.can_reply === true && !readonlyMode;
      if (elements.workspaceComposerText) elements.workspaceComposerText.disabled = !canReplyInWorkspace;
      if (elements.workspaceComposerSend) elements.workspaceComposerSend.disabled = !canReplyInWorkspace;
      if (elements.workspaceComposerMediaTrigger) elements.workspaceComposerMediaTrigger.disabled = !canReplyInWorkspace || composer.media_supported === false;
      if (elements.workspaceComposerSaveDraft) elements.workspaceComposerSaveDraft.disabled = !canReplyInWorkspace;
      if (elements.workspaceComposerMacroApply) elements.workspaceComposerMacroApply.disabled = !canReplyInWorkspace || !composerMeta.hasActiveMacroTemplate;
      if (elements.workspaceComposerMacroSelect) elements.workspaceComposerMacroSelect.disabled = !canReplyInWorkspace || Number(composerMeta.macroTemplatesLength || 0) === 0;
      if (elements.workspaceReplyTargetClear) elements.workspaceReplyTargetClear.disabled = !canReplyInWorkspace || composer.reply_target_supported === false;

      if (elements.workspaceConversationTitle) {
        elements.workspaceConversationTitle.textContent = `Диалог #${conversation.ticketId || '—'}`;
      }
      if (elements.workspaceConversationMeta) {
        const status = conversation.statusLabel || conversation.status || '—';
        const assignee = conversation.responsible || 'без ответственного';
        const createdAt = formatWorkspaceDateTime(conversation.createdAt || conversation.created_at);
        elements.workspaceConversationMeta.textContent = `Статус: ${status} · Ответственный: ${assignee} · Создан: ${createdAt}`;
      }

      renderWorkspaceNavigation(navigation);
      options.updateWorkspaceActionButtons?.(conversation, permissions || {}, payload);
      options.renderWorkspaceParityBanner?.(parity);

      if (elements.workspaceMessagesState) {
        elements.workspaceMessagesState.classList.toggle('d-none', Array.isArray(messages.items) && messages.items.length > 0);
        elements.workspaceMessagesState.textContent = Array.isArray(messages.items) && messages.items.length > 0
          ? ''
          : 'Сообщения отсутствуют или ещё не загружены.';
      }
      if (elements.workspaceMessagesList) {
        const items = Array.isArray(messages.items) ? messages.items : [];
        elements.workspaceMessagesList.classList.toggle('d-none', items.length === 0);
        elements.workspaceMessagesList.innerHTML = items.map((item) => options.renderWorkspaceMessageItem?.(item) || '').join('');
      }
      syncWorkspaceMessagesPagination(messages);
      if (elements.workspaceMessagesError) {
        elements.workspaceMessagesError.classList.add('d-none');
      }

      renderWorkspaceClientSection(context);
      renderWorkspaceHistorySection(context);
      renderWorkspaceRelatedEventsSection(context);

      options.renderWorkspaceCategories?.();
      if (elements.workspaceCategoriesError) {
        elements.workspaceCategoriesError.classList.add('d-none');
      }
      options.emitWorkspaceProfileGapTelemetry?.(context, conversation);
      options.emitWorkspaceContextSourceGapTelemetry?.(context, conversation);
      options.emitWorkspaceContextAttributePolicyGapTelemetry?.(context, conversation);
      options.emitWorkspaceContextBlockGapTelemetry?.(context, conversation);
      options.emitWorkspaceContextContractGapTelemetry?.(context, conversation);
      options.emitWorkspaceSlaPolicyGapTelemetry?.(sla, conversation);
      options.emitWorkspaceParityGapTelemetry?.(parity, conversation);

      renderWorkspaceSlaSection(sla);
    }

    return {
      resetWorkspaceReplyTarget,
      setWorkspaceReplyTarget,
      hasUnsavedWorkspaceComposerChanges,
      confirmWorkspaceTicketSwitch,
      updateWorkspaceDraftState,
      saveWorkspaceDraft,
      restoreWorkspaceDraft,
      scheduleWorkspaceDraftAutosave,
      appendWorkspaceMessage,
      updateWorkspaceMessagesLoadMoreState,
      syncWorkspaceMessagesPagination,
      sendWorkspaceReply,
      reloadWorkspaceSection,
      loadMoreWorkspaceMessages,
      renderWorkspaceNavigation,
      navigateWorkspaceInline,
      refreshActiveWorkspaceContract,
      appendToWorkspaceComposer,
      renderWorkspaceRolloutBanner,
      renderWorkspaceParityBanner,
      setWorkspaceReadonlyMode,
      resolveWorkspaceReadonlyReason,
      renderWorkspaceShell,
      normalizeWorkspaceContextViolationDetails,
      workspaceContextViolationTypeLabel,
      workspaceContextViolationSeverityLabel,
      workspaceContextViolationSeverityBadge,
      renderWorkspaceClientProfile,
      isWorkspaceClientExtraValue,
      formatWorkspaceClientExtraValue,
      prettifyWorkspaceClientExtraKey,
      resolveWorkspaceClientAttributeLabelMap,
      resolveWorkspaceClientAttributeOrder,
      orderWorkspaceClientExtraEntries,
      normalizeWorkspaceClientAttributeKey,
    };
  }

  window.DialogsWorkspaceRuntime = {
    createRuntime,
  };
})();

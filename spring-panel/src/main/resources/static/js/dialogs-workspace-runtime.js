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
    };
  }

  window.DialogsWorkspaceRuntime = {
    createRuntime,
  };
})();

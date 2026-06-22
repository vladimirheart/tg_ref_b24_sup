(function () {
  if (window.DialogsFlowRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      workspaceFirstInteractionTickets: new Set(),
      shortcutEventsBound: false,
      detailsLifecycleBound: false,
      workspaceTelemetryBound: false,
    };

    function isTypingTarget(target) {
      if (!target) return false;
      const tagName = String(target.tagName || '').toLowerCase();
      if (target.isContentEditable) return true;
      return tagName === 'input' || tagName === 'textarea' || tagName === 'select';
    }

    function openDialogEntry(ticketId, row) {
      if (!ticketId) return;
      options.debugLog?.('openDialogEntry.called', {
        ticketId,
        rowFound: Boolean(row),
        detailsModalExists: Boolean(elements.detailsModalEl),
      });
      if (!options.confirmWorkspaceTicketSwitch?.(ticketId)) {
        options.debugLog?.('openDialogEntry.aborted.confirmWorkspaceTicketSwitch=false', { ticketId });
        return;
      }
      options.setWorkspaceReadonlyMode?.(false);
      const channelId = row?.dataset?.channelId ?? null;
      const nextUrl = options.buildWorkspaceDialogUrl?.(ticketId, channelId) || `/dialogs/${encodeURIComponent(ticketId)}`;
      const currentPath = `${window.location.pathname || ''}${window.location.search || ''}`;
      if (currentPath !== nextUrl) {
        window.history.pushState({ ticketId: String(ticketId || '').trim() }, '', nextUrl);
        options.debugLog?.('openDialogEntry.pushState', { currentPath, nextUrl });
      }
      if (!elements.detailsModalEl) {
        options.debugLog?.('openDialogEntry.redirect.noDetailsModal', { ticketId });
        window.location.href = `/dialogs/${encodeURIComponent(ticketId)}`;
        return;
      }
      Promise.resolve(options.openDialogDetails?.(ticketId, row)).catch((error) => {
        options.debugLog?.('openDialogEntry.openDialogDetails.catch', {
          ticketId,
          message: error?.message || String(error || ''),
        });
        console.error('Failed to open dialog details modal', error);
        window.location.href = `/dialogs/${encodeURIComponent(ticketId)}`;
      });
    }

    function handleGlobalShortcuts(event) {
      if (event.defaultPrevented || event.repeat) return;
      if (isTypingTarget(event.target)) return;

      if (event.key === '/') {
        if (!elements.quickSearch) return;
        event.preventDefault();
        elements.quickSearch.focus();
        elements.quickSearch.select();
        return;
      }

      if ((event.key === '?' || (event.shiftKey && event.key === '/')) && elements.hotkeysModalEl) {
        event.preventDefault();
        options.showModalSafe?.(elements.hotkeysModalEl, elements.hotkeysModal);
        return;
      }

      if (!event.altKey) return;
      const key = String(event.key || '').toLowerCase();
      const filterState = typeof options.getFilterState === 'function'
        ? options.getFilterState()
        : null;
      const viewMap = {
        '1': 'all',
        '2': 'active',
        '3': 'new',
        '4': 'unassigned',
        '5': 'overdue',
      };

      if (viewMap[key]) {
        event.preventDefault();
        options.setViewTab?.(viewMap[key]);
        return;
      }

      const statusShortcutMap = {
        '6': 'Новый',
        '7': 'Ожидает ответа оператора',
        '8': 'Ожидает ответа клиента',
        '9': 'Закрыт',
        '0': '',
      };

      if (Object.prototype.hasOwnProperty.call(statusShortcutMap, key)) {
        event.preventDefault();
        options.applyStatusFilter?.(statusShortcutMap[key]);
        return;
      }

      if (key === 'l' && filterState) {
        event.preventDefault();
        const nextSortMode = filterState.sortMode === 'sla_priority' ? 'default' : 'sla_priority';
        filterState.sortMode = nextSortMode;
        if (elements.sortModeSelect) {
          elements.sortModeSelect.value = nextSortMode;
        }
        if (nextSortMode !== 'sla_priority') {
          options.setLastManualSortMode?.(nextSortMode);
        }
        options.persistDialogPreferences?.();
        options.applyFilters?.();
        options.showNotification?.(
          nextSortMode === 'sla_priority' ? 'Включена сортировка SLA-first' : 'Включена стандартная сортировка',
          'info'
        );
        return;
      }

      if (event.shiftKey && key === 'a') {
        event.preventDefault();
        if (!options.canRunAction?.('can_bulk') || !options.canRunAction?.('can_assign')) {
          options.notifyPermissionDenied?.('Назначить выбранные на меня');
          return;
        }
        options.runBulkAction?.('take');
        return;
      }

      if (event.shiftKey && key === 's') {
        event.preventDefault();
        if (!options.canRunAction?.('can_bulk') || !options.canRunAction?.('can_snooze')) {
          options.notifyPermissionDenied?.(
            `Отложить выбранные на ${options.formatSnoozeActionLabel?.(options.quickSnoozeMinutes).replace('Отложить ', '')}`
          );
          return;
        }
        options.runBulkAction?.('snooze');
        return;
      }

      if (event.shiftKey && key === 'c') {
        event.preventDefault();
        if (!options.canRunAction?.('can_bulk') || !options.canRunAction?.('can_close')) {
          options.notifyPermissionDenied?.('Закрыть выбранные');
          return;
        }
        options.runBulkAction?.('close');
        return;
      }

      if (key === 'a') {
        event.preventDefault();
        if (!options.canRunAction?.('can_assign')) {
          options.notifyPermissionDenied?.('Назначить мне');
          return;
        }
        const row = options.getShortcutTargetRow?.();
        const takeBtn = row?.querySelector('.dialog-take-btn:not(.d-none)');
        const ticketId = takeBtn?.dataset.ticketId;
        if (!ticketId || !takeBtn) return;
        options.setActiveDialogRow?.(row, { ensureVisible: true });
        options.takeDialog?.(ticketId, row, takeBtn)
          .then(() => {
            options.showNotification?.('Диалог назначен на вас', 'success');
          })
          .catch((error) => {
            options.showNotification?.(error.message || 'Не удалось назначить диалог', 'error');
          });
        return;
      }

      if (key === 's') {
        event.preventDefault();
        if (!options.canRunAction?.('can_snooze')) {
          options.notifyPermissionDenied?.(options.formatSnoozeActionLabel?.(options.quickSnoozeMinutes));
          return;
        }
        const row = options.getShortcutTargetRow?.();
        const snoozeBtn = row?.querySelector('.dialog-snooze-btn:not(.d-none)');
        const ticketId = snoozeBtn?.dataset.ticketId;
        if (!ticketId || !snoozeBtn) return;
        options.setActiveDialogRow?.(row, { ensureVisible: true });
        options.snoozeDialog?.(ticketId, options.quickSnoozeMinutes, snoozeBtn)
          .then(() => {
            options.setSnooze?.(ticketId, options.quickSnoozeMinutes);
            options.updateRowQuickActions?.(row);
            options.applyFilters?.();
            options.showNotification?.(
              `Диалог отложен на ${options.formatSnoozeActionLabel?.(options.quickSnoozeMinutes).replace('Отложить ', '')}`,
              'success'
            );
          })
          .catch((error) => {
            options.showNotification?.(error.message || 'Не удалось отложить диалог', 'error');
          });
        return;
      }

      if (key === 'c') {
        event.preventDefault();
        if (!options.canRunAction?.('can_close')) {
          options.notifyPermissionDenied?.('Закрыть');
          return;
        }
        const row = options.getShortcutTargetRow?.();
        const closeBtn = row?.querySelector('.dialog-close-btn:not(.d-none)');
        const ticketId = closeBtn?.dataset.ticketId;
        if (!ticketId || !closeBtn) return;
        options.setActiveDialogRow?.(row, { ensureVisible: true });
        closeBtn.disabled = true;
        options.closeDialogQuick?.(ticketId, row, closeBtn)
          .then(() => {
            options.clearSnooze?.(ticketId);
            options.showNotification?.('Диалог закрыт', 'success');
          })
          .catch((error) => {
            closeBtn.disabled = false;
            options.showNotification?.(error.message || 'Не удалось закрыть диалог', 'error');
          });
        return;
      }

      if (key === 'r') {
        if (!elements.detailsModalEl?.classList.contains('show')) return;
        event.preventDefault();
        if (elements.detailsResolve && !elements.detailsResolve.disabled && !elements.detailsResolve.classList.contains('d-none')) {
          elements.detailsResolve.click();
          return;
        }
        if (elements.detailsReopen && !elements.detailsReopen.disabled && !elements.detailsReopen.classList.contains('d-none')) {
          elements.detailsReopen.click();
        }
        return;
      }

      if (event.key === 'ArrowDown') {
        event.preventDefault();
        options.openVisibleDialogByOffset?.(1);
        return;
      }

      if (event.key === 'ArrowUp') {
        event.preventDefault();
        options.openVisibleDialogByOffset?.(-1);
      }
    }

    function bindGlobalShortcutEvents() {
      if (state.shortcutEventsBound) return;
      state.shortcutEventsBound = true;
      document.addEventListener('keydown', handleGlobalShortcuts);
    }

    function bindDetailsModalLifecycle() {
      if (state.detailsLifecycleBound || !elements.detailsModalEl) return;
      state.detailsLifecycleBound = true;
      elements.detailsModalEl.addEventListener('shown.bs.modal', () => {
        options.startHistoryPolling?.();
      });
      elements.detailsModalEl.addEventListener('hidden.bs.modal', () => {
        const panelState = typeof options.getCategoryPanelState === 'function'
          ? options.getCategoryPanelState()
          : { suppressDetailsReset: false };
        if (panelState.suppressDetailsReset) {
          return;
        }
        if (elements.categoriesModal) {
          elements.categoriesModal.hide();
        }
        options.hideModalSafe?.(elements.participantsModalEl, elements.participantsModal);
        options.hideModalSafe?.(elements.reassignModalEl, elements.reassignModal);
        options.setActiveDialogTicketId?.(null);
        options.initMacroVariableCatalog?.(null, true);
        options.setActiveDialogChannelId?.(null);
        options.setActiveDialogResponsibleState?.('', '');
        options.setActiveDialogRow?.(null);
        if (elements.detailsReplyText) elements.detailsReplyText.value = '';
        if (elements.detailsReplyMedia) elements.detailsReplyMedia.value = '';
        options.resetReplyTarget?.();
        options.renderHistory?.([], { scrollToBottom: false });
        options.resetPreviousDialogHistoryState?.();
        options.setActiveDialogContext?.({
          clientName: '—',
          clientUserId: '',
          operatorName: '—',
          channelName: '—',
          business: '—',
          location: '—',
          status: '—',
          createdAt: '—',
        });
        options.setSelectedCategories?.(new Set());
        options.setDialogParticipantsState?.([]);
        options.renderDialogParticipantsLoadingState?.('Откройте диалог для загрузки списка участников.');
        options.syncParticipantsSelectOptions?.();
        options.syncReassignSelectOptions?.();
        options.updateDetailsTakeButton?.('');
        options.stopHistoryPolling?.();
        if (options.workspaceEnabled === true && options.isWorkspaceDialogPath?.(window.location.pathname)) {
          const nextPath = window.location.pathname === '/' ? '/' : '/dialogs';
          window.history.replaceState(null, '', nextPath);
        }
      });
    }

    function registerWorkspaceFirstInteraction() {
      if (options.workspaceEnabled !== true) return;
      const activeWorkspaceTicketId = options.getActiveWorkspaceTicketId?.();
      if (!activeWorkspaceTicketId) return;
      if (state.workspaceFirstInteractionTickets.has(activeWorkspaceTicketId)) return;
      state.workspaceFirstInteractionTickets.add(activeWorkspaceTicketId);
    }

    function bindWorkspaceAbandonTelemetry() {
      if (state.workspaceTelemetryBound) return;
      state.workspaceTelemetryBound = true;
      ['click', 'keydown'].forEach((eventName) => {
        document.addEventListener(eventName, registerWorkspaceFirstInteraction, { once: true });
      });
      window.addEventListener('beforeunload', () => {
        if (options.workspaceEnabled !== true) return;
        const activeWorkspaceTicketId = options.getActiveWorkspaceTicketId?.();
        if (!activeWorkspaceTicketId) return;
        if (state.workspaceFirstInteractionTickets.has(activeWorkspaceTicketId)) return;
        if (typeof navigator.sendBeacon !== 'function') return;
        const payload = new Blob([JSON.stringify({
          event_type: 'workspace_abandon',
          event_group: options.workspaceAbandonEventGroup || 'engagement',
          timestamp: new Date().toISOString(),
          ticket_id: activeWorkspaceTicketId,
          reason: 'no_first_interaction',
          experiment_name: options.workspaceExperimentContext?.experimentName,
          experiment_cohort: options.workspaceExperimentContext?.cohort,
          operator_segment: options.workspaceExperimentContext?.operatorSegment,
          primary_kpis: options.workspacePrimaryKpis,
          secondary_kpis: options.workspaceSecondaryKpis,
        })], { type: 'application/json' });
        navigator.sendBeacon('/api/dialogs/workspace-telemetry', payload);
      });
    }

    return {
      openDialogEntry,
      handleGlobalShortcuts,
      bindGlobalShortcutEvents,
      bindDetailsModalLifecycle,
      bindWorkspaceAbandonTelemetry,
    };
  }

  window.DialogsFlowRuntime = {
    createRuntime,
  };
})();

(function () {
  if (window.DialogsActionsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};

    function getActiveDialogState() {
      const activeState = typeof options.getActiveDialogState === 'function'
        ? options.getActiveDialogState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { ticketId: '', row: null };
    }

    function getActiveWorkspaceState() {
      const activeState = typeof options.getActiveWorkspaceState === 'function'
        ? options.getActiveWorkspaceState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { ticketId: '', payload: null, readonlyMode: false };
    }

    function getSelectedCategories() {
      const categories = typeof options.getSelectedCategories === 'function'
        ? options.getSelectedCategories()
        : null;
      return categories instanceof Set ? categories : new Set();
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function resolveSnoozeLabel(minutes) {
      return typeof options.formatSnoozeActionLabel === 'function'
        ? options.formatSnoozeActionLabel(minutes)
        : `Отложить на ${minutes}м`;
    }

    function resolveSnoozeSuccessSuffix(minutes) {
      return resolveSnoozeLabel(minutes).replace('Отложить ', '');
    }

    function parseRowCategories(row) {
      const categoriesValue = String(row?.dataset?.categories || '').trim();
      if (!categoriesValue || categoriesValue === '—') {
        return [];
      }
      return categoriesValue.split(',').map((item) => item.trim()).filter(Boolean);
    }

    function updateRowQuickActions(row) {
      if (!row) return;
      const isClosed = options.isResolvedRow?.(row) === true;
      const closeBtn = row.querySelector('.dialog-close-btn');
      const snoozeBtn = row.querySelector('.dialog-snooze-btn');
      const takeBtn = row.querySelector('.dialog-take-btn');
      const responsible = typeof options.resolveRowResponsibleRaw === 'function'
        ? options.resolveRowResponsibleRaw(row)
        : '';
      if (takeBtn) {
        takeBtn.classList.toggle('d-none', !options.canTakeDialogOwnership?.(responsible, isClosed));
      }
      if (closeBtn) {
        closeBtn.classList.toggle('d-none', isClosed || !options.canRunAction?.('can_close'));
      }
      if (snoozeBtn) {
        snoozeBtn.classList.toggle('d-none', isClosed || !options.canRunAction?.('can_snooze'));
        const ticketId = row.dataset.ticketId;
        const until = options.getSnoozeUntil?.(ticketId);
        snoozeBtn.textContent = until
          ? `Отложен до ${options.formatUtcDate?.(new Date(until), { includeTime: true })}`
          : resolveSnoozeLabel(options.quickSnoozeMinutes);
      }
    }

    function setDialogActionsMenuOpen(menu, isOpen) {
      if (!menu) return;
      menu.classList.toggle('is-open', Boolean(isOpen));
      const toggle = menu.querySelector('[data-dialog-actions-toggle]');
      if (toggle) {
        toggle.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
      }
    }

    function closeDialogActionsMenus(exceptMenu = null) {
      const table = elements.table;
      if (!table) return;
      table.querySelectorAll('.dialog-actions-dropdown.is-open').forEach((menu) => {
        if (menu !== exceptMenu) {
          setDialogActionsMenuOpen(menu, false);
        }
      });
    }

    async function closeDialogQuick(ticketId, row, triggerButton) {
      if (!ticketId) return;
      const categories = parseRowCategories(row);
      if (!categories.length) {
        throw new Error('Для быстрого закрытия укажите категорию в карточке диалога.');
      }
      const btn = triggerButton || null;
      if (btn) btn.disabled = true;
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/resolve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ categories }),
      });
      const data = await resp.json();
      if (!resp.ok || !data?.success) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      options.updateRowStatus?.(row, 'resolved', 'Закрыт', 'closed', 0);
      options.emitWorkspaceTelemetry?.('triage_quick_close', { ticketId });
      options.updateRowSlaBadge?.(row);
      updateRowQuickActions(row);
      options.applyFilters?.();
      return data;
    }

    async function takeDialog(ticketId, row, triggerButton) {
      if (!ticketId) return null;
      const activeDialogState = getActiveDialogState();
      const targetRow = row
        || (String(activeDialogState.ticketId || '').trim() === String(ticketId || '').trim()
          ? activeDialogState.row
          : null);
      if (targetRow && options.isResolvedRow?.(targetRow) === true) {
        const error = new Error('Взять в работу можно только открытый диалог');
        notify(error.message, 'error');
        throw error;
      }
      const btn = triggerButton || null;
      if (btn) btn.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/take`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        options.updateRowResponsible?.(row || targetRow, data.responsible || '');
        if (String(activeDialogState.ticketId || '').trim() === String(ticketId || '').trim()) {
          options.loadDialogParticipants?.().catch(() => {});
        }
        options.emitWorkspaceTelemetry?.('triage_quick_assign', { ticketId });
        options.applyFilters?.();
        notify('Диалог назначен на вас', 'success');
        return data;
      } catch (error) {
        if (btn) btn.disabled = false;
        notify(error.message || 'Не удалось взять диалог', 'error');
        throw error;
      }
    }

    async function snoozeDialog(ticketId, minutes, triggerButton) {
      if (!ticketId) return null;
      const btn = triggerButton || null;
      if (btn) btn.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/snooze`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ minutes }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        options.emitWorkspaceTelemetry?.('triage_quick_snooze', { ticketId, reason: `minutes:${minutes}` });
        return data;
      } finally {
        if (btn) btn.disabled = false;
      }
    }

    function resolveDetailsTicketId() {
      const activeDialogState = getActiveDialogState();
      const direct = String(activeDialogState.ticketId || '').trim();
      if (direct) {
        return direct;
      }
      const metaText = String(elements.detailsMeta?.textContent || '');
      const match = metaText.match(/#([A-Za-z0-9._:-]+)/);
      if (!match?.[1]) {
        return null;
      }
      options.setActiveDialogTicketId?.(match[1]);
      return match[1];
    }

    function updateResolveButton(statusRaw) {
      if (!elements.detailsResolve) return;
      const activeDialogState = getActiveDialogState();
      const currentStatus = String(statusRaw || activeDialogState.row?.dataset?.statusRaw || activeDialogState.row?.dataset?.status || '').trim();
      const currentStatusKey = String(activeDialogState.row?.dataset?.statusKey || '').trim();
      const resolved = options.isResolvedStatus?.(currentStatus, currentStatusKey, '') === true;
      const ticketId = String(activeDialogState.ticketId || '').trim();
      const canResolve = options.isWorkspaceActionEnabled?.(
        'resolve',
        options.canRunAction?.('can_close') && !resolved,
        ticketId
      ) === true;
      elements.detailsResolve.disabled = !canResolve;
      elements.detailsResolve.textContent = resolved ? 'Обращение закрыто' : 'Закрыть обращение';
      if (elements.detailsSpam) {
        const clientUserId = String(elements.detailsSpam.dataset.userId || '').trim();
        const canMarkSpam = options.isWorkspaceActionEnabled?.(
          'spam',
          options.canRunAction?.('can_close') && !resolved,
          ticketId
        ) === true;
        elements.detailsSpam.disabled = !canMarkSpam || !clientUserId;
        elements.detailsSpam.classList.toggle('d-none', !canMarkSpam);
      }
      if (elements.detailsReopen) {
        const canReopen = options.isWorkspaceActionEnabled?.(
          'reopen',
          options.canRunAction?.('can_close') && resolved,
          ticketId
        ) === true;
        elements.detailsReopen.disabled = !canReopen;
        elements.detailsReopen.classList.toggle('d-none', !canReopen);
      }
      if (elements.detailsCategoriesBtn) {
        const canEditCategories = options.isWorkspaceActionEnabled?.(
          'categories',
          options.canRunAction?.('can_close'),
          ticketId
        ) === true;
        elements.detailsCategoriesBtn.disabled = !canEditCategories;
        elements.detailsCategoriesBtn.classList.toggle('d-none', !canEditCategories);
      }
    }

    function updateWorkspaceActionButtons(conversation, permissions, payload = null) {
      const activeWorkspaceState = getActiveWorkspaceState();
      const statusRaw = conversation?.status || '';
      const statusKey = conversation?.statusKey || '';
      const statusLabel = conversation?.statusLabel || '';
      const resolved = options.isResolvedStatus?.(statusRaw, statusKey, statusLabel) === true;
      const responsible = String(conversation?.responsible || '').trim();
      const ticketId = conversation?.ticketId || activeWorkspaceState.ticketId;
      const takeEnabled = options.isWorkspaceActionEnabled?.(
        'take',
        options.canTakeDialogOwnership?.(responsible, resolved),
        ticketId
      ) === true;
      const canAssign = permissions?.can_assign === true && !activeWorkspaceState.readonlyMode;
      const canClose = permissions?.can_close === true && !activeWorkspaceState.readonlyMode;
      const canSnooze = options.isWorkspaceActionEnabled?.(
        'snooze',
        permissions?.can_snooze === true && !activeWorkspaceState.readonlyMode && !resolved,
        ticketId
      ) === true;
      const canResolve = options.isWorkspaceActionEnabled?.(
        'resolve',
        canClose && !resolved,
        ticketId
      ) === true;
      const canReopen = options.isWorkspaceActionEnabled?.(
        'reopen',
        canClose && resolved,
        ticketId
      ) === true;

      if (elements.workspaceAssignBtn) {
        elements.workspaceAssignBtn.disabled = !canAssign || !takeEnabled;
        elements.workspaceAssignBtn.classList.toggle('d-none', !canAssign || !takeEnabled);
      }
      if (elements.workspaceSnoozeBtn) {
        elements.workspaceSnoozeBtn.disabled = !canSnooze;
        elements.workspaceSnoozeBtn.classList.toggle('d-none', !canSnooze);
        elements.workspaceSnoozeBtn.textContent = resolveSnoozeLabel(options.quickSnoozeMinutes);
      }
      if (elements.workspaceResolveBtn) {
        elements.workspaceResolveBtn.disabled = !canResolve;
        elements.workspaceResolveBtn.classList.toggle('d-none', !canResolve);
      }
      if (elements.workspaceReopenBtn) {
        elements.workspaceReopenBtn.disabled = !canReopen;
        elements.workspaceReopenBtn.classList.toggle('d-none', !canReopen);
      }
      const safePayload = payload && typeof payload === 'object'
        ? payload
        : activeWorkspaceState.payload;
      const rollout = safePayload?.meta?.rollout || {};
      options.renderWorkspaceRolloutBanner?.(rollout);
      if (elements.workspaceLegacyBtn) {
        const fallbackAvailable = !options.workspaceSingleMode
          && (rollout?.legacy_fallback_available === true || (!safePayload?.meta?.rollout && !options.workspaceDisableLegacyFallback));
        elements.workspaceLegacyBtn.disabled = !fallbackAvailable;
        elements.workspaceLegacyBtn.classList.toggle('d-none', !fallbackAvailable);
      }
      if (elements.workspaceCreateTaskBtn) {
        elements.workspaceCreateTaskBtn.classList.remove('disabled');
      }
    }

    function bindDocumentQuickActions() {
      document.addEventListener('click', (event) => {
        const target = event.target instanceof Element ? event.target : null;
        if (target?.closest('.dialog-actions-dropdown')) return;
        closeDialogActionsMenus();
      });
    }

    function bindTableQuickActions() {
      if (!elements.table) return;
      elements.table.addEventListener('click', (event) => {
        const target = event.target instanceof Element ? event.target : event.target?.parentElement;
        if (!target) return;

        const actionToggle = target.closest('[data-dialog-actions-toggle]');
        if (actionToggle) {
          event.preventDefault();
          event.stopPropagation();
          const menu = actionToggle.closest('.dialog-actions-dropdown');
          if (!menu) return;
          const nextState = !menu.classList.contains('is-open');
          closeDialogActionsMenus(menu);
          setDialogActionsMenuOpen(menu, nextState);
          return;
        }

        const takeBtn = target.closest('.dialog-take-btn');
        if (takeBtn) {
          event.preventDefault();
          const actionMenu = takeBtn.closest('.dialog-actions-dropdown');
          if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
          if (!options.canRunAction?.('can_assign')) {
            options.notifyPermissionDenied?.('Назначить мне');
            return;
          }
          const ticketId = takeBtn.dataset.ticketId;
          const row = takeBtn.closest('tr');
          options.setActiveDialogRow?.(row, { ensureVisible: true });
          takeDialog(ticketId, row, takeBtn).catch(() => {});
          return;
        }

        const snoozeBtn = target.closest('.dialog-snooze-btn');
        if (snoozeBtn) {
          event.preventDefault();
          const actionMenu = snoozeBtn.closest('.dialog-actions-dropdown');
          if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
          if (!options.canRunAction?.('can_snooze')) {
            options.notifyPermissionDenied?.(resolveSnoozeLabel(options.quickSnoozeMinutes));
            return;
          }
          const ticketId = snoozeBtn.dataset.ticketId;
          const row = snoozeBtn.closest('tr');
          options.setActiveDialogRow?.(row, { ensureVisible: true });
          snoozeDialog(ticketId, options.quickSnoozeMinutes, snoozeBtn)
            .then(() => {
              options.setSnooze?.(ticketId, options.quickSnoozeMinutes);
              updateRowQuickActions(row);
              options.applyFilters?.();
              notify(`Диалог отложен на ${resolveSnoozeSuccessSuffix(options.quickSnoozeMinutes)}`, 'success');
            })
            .catch((error) => {
              notify(error.message || 'Не удалось отложить диалог', 'error');
            });
          return;
        }

        const closeBtn = target.closest('.dialog-close-btn');
        if (closeBtn) {
          event.preventDefault();
          const actionMenu = closeBtn.closest('.dialog-actions-dropdown');
          if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
          if (!options.canRunAction?.('can_close')) {
            options.notifyPermissionDenied?.('Закрыть');
            return;
          }
          const ticketId = closeBtn.dataset.ticketId;
          const row = closeBtn.closest('tr');
          options.setActiveDialogRow?.(row, { ensureVisible: true });
          closeBtn.disabled = true;
          closeDialogQuick(ticketId, row, closeBtn)
            .then(() => {
              options.clearSnooze?.(ticketId);
              notify('Диалог закрыт', 'success');
            })
            .catch((error) => {
              notify(error.message || 'Не удалось закрыть диалог', 'error');
              closeBtn.disabled = false;
            });
        }
      });
    }

    function bindDetailsQuickActions() {
      if (elements.detailsSpam) {
        elements.detailsSpam.addEventListener('click', async () => {
          const ticketId = resolveDetailsTicketId();
          const activeDialogState = getActiveDialogState();
          const userId = String(elements.detailsSpam.dataset.userId || '').trim();
          if (!ticketId || !userId) {
            notify('Не удалось определить клиента для блокировки', 'error');
            return;
          }
          const defaultReason = `Спам в диалоге ${ticketId}`;
          const reasonInput = window.prompt('Причина блокировки клиента как спам:', defaultReason);
          if (reasonInput === null) {
            return;
          }
          const reason = String(reasonInput || '').trim() || defaultReason;
          elements.detailsSpam.disabled = true;
          try {
            const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/spam`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ reason }),
            });
            const data = await resp.json();
            if (!resp.ok || !data?.success) {
              throw new Error(data?.error || `Ошибка ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Клиент заблокирован как спам', 'success');
          } catch (error) {
            notify(error.message || 'Не удалось заблокировать клиента', 'error');
          } finally {
            updateResolveButton(String(activeDialogState.row?.dataset?.statusRaw || activeDialogState.row?.dataset?.status || ''));
          }
        });
      }

      if (elements.detailsTakeBtn) {
        elements.detailsTakeBtn.addEventListener('click', async () => {
          const activeDialogState = getActiveDialogState();
          if (!activeDialogState.ticketId || !activeDialogState.row) return;
          try {
            const data = await takeDialog(activeDialogState.ticketId, activeDialogState.row, elements.detailsTakeBtn);
            options.updateDetailsResponsible?.(data?.responsible || activeDialogState.row.dataset.responsible || '');
          } catch (_error) {
            // Notification is already shown inside takeDialog.
          }
        });
      }

      if (elements.detailsResolve) {
        elements.detailsResolve.addEventListener('click', async () => {
          const ticketId = resolveDetailsTicketId();
          const activeDialogState = getActiveDialogState();
          if (!ticketId) return;
          elements.detailsResolve.disabled = true;
          try {
            const categories = Array.from(getSelectedCategories());
            if (!categories.length) {
              options.openCategoryPanel?.();
              throw new Error('Укажите хотя бы одну категорию обращения перед закрытием.');
            }
            const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/resolve`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ categories }),
            });
            const data = await resp.json();
            if (!resp.ok || !data?.success) {
              throw new Error(data?.error || `Ошибка ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Диалог закрыт', 'success');
          } catch (error) {
            notify(error.message || 'Не удалось закрыть диалог', 'error');
            elements.detailsResolve.disabled = false;
          }
        });
      }

      if (elements.detailsReopen) {
        elements.detailsReopen.addEventListener('click', async () => {
          const ticketId = resolveDetailsTicketId();
          const activeDialogState = getActiveDialogState();
          if (!ticketId) return;
          if (!window.confirm('Переоткрыть закрытое обращение?')) {
            return;
          }
          elements.detailsReopen.disabled = true;
          try {
            const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/reopen`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
            });
            const data = await resp.json();
            if (!resp.ok || !data?.success) {
              throw new Error(data?.error || `Ошибка ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Диалог переоткрыт', 'success');
          } catch (error) {
            notify(error.message || 'Не удалось переоткрыть диалог', 'error');
          } finally {
            elements.detailsReopen.disabled = false;
          }
        });
      }
    }

    function bindWorkspaceQuickActions() {
      if (elements.workspaceAssignBtn) {
        elements.workspaceAssignBtn.addEventListener('click', async () => {
          const activeDialogState = getActiveDialogState();
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId || !activeDialogState.row) return;
          try {
            await takeDialog(activeWorkspaceState.ticketId, activeDialogState.row, elements.workspaceAssignBtn);
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Диалог назначен на вас.' });
          } catch (_error) {
            // Notification is already shown inside takeDialog.
          }
        });
      }

      if (elements.workspaceSnoozeBtn) {
        elements.workspaceSnoozeBtn.addEventListener('click', async () => {
          const activeDialogState = getActiveDialogState();
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId) return;
          try {
            await snoozeDialog(activeWorkspaceState.ticketId, options.quickSnoozeMinutes, elements.workspaceSnoozeBtn);
            options.setSnooze?.(activeWorkspaceState.ticketId, options.quickSnoozeMinutes);
            if (activeDialogState.row) {
              updateRowQuickActions(activeDialogState.row);
            }
            notify(`Диалог отложен на ${options.formatSnoozeDurationLabel?.(options.quickSnoozeMinutes)}.`, 'success');
          } catch (error) {
            notify(error.message || 'Не удалось отложить диалог', 'error');
          }
        });
      }

      if (elements.workspaceResolveBtn) {
        elements.workspaceResolveBtn.addEventListener('click', async () => {
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId) return;
          elements.workspaceResolveBtn.disabled = true;
          try {
            const categories = Array.from(getSelectedCategories());
            if (!categories.length) {
              options.renderWorkspaceCategories?.();
              throw new Error('Выберите хотя бы одну категорию перед закрытием диалога.');
            }
            const response = await fetch(`/api/dialogs/${encodeURIComponent(activeWorkspaceState.ticketId)}/resolve`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ categories }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Диалог закрыт.' });
          } catch (error) {
            elements.workspaceResolveBtn.disabled = false;
            notify(error.message || 'Не удалось закрыть диалог', 'error');
          }
        });
      }

      if (elements.workspaceReopenBtn) {
        elements.workspaceReopenBtn.addEventListener('click', async () => {
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId) return;
          if (!window.confirm('Переоткрыть закрытое обращение?')) {
            return;
          }
          elements.workspaceReopenBtn.disabled = true;
          try {
            const response = await fetch(`/api/dialogs/${encodeURIComponent(activeWorkspaceState.ticketId)}/reopen`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Диалог переоткрыт.' });
          } catch (error) {
            notify(error.message || 'Не удалось переоткрыть диалог', 'error');
          } finally {
            elements.workspaceReopenBtn.disabled = false;
          }
        });
      }
    }

    return {
      updateRowQuickActions,
      setDialogActionsMenuOpen,
      closeDialogActionsMenus,
      closeDialogQuick,
      takeDialog,
      snoozeDialog,
      resolveDetailsTicketId,
      updateResolveButton,
      updateWorkspaceActionButtons,
      bindDocumentQuickActions,
      bindTableQuickActions,
      bindDetailsQuickActions,
      bindWorkspaceQuickActions,
    };
  }

  window.DialogsActionsRuntime = {
    createRuntime,
  };
})();

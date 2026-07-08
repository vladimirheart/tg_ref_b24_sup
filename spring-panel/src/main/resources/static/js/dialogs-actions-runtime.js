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
        : `Р С›РЎвЂљР В»Р С•Р В¶Р С‘РЎвЂљРЎРЉ Р Р…Р В° ${minutes}Р С`;
    }

    function resolveSnoozeSuccessSuffix(minutes) {
      return resolveSnoozeLabel(minutes).replace('Р С›РЎвЂљР В»Р С•Р В¶Р С‘РЎвЂљРЎРЉ ', '');
    }

    function parseRowCategories(row) {
      const categoriesValue = String(row?.dataset?.categories || '').trim();
      if (!categoriesValue || categoriesValue === 'РІР‚вЂќ') {
        return [];
      }
      return categoriesValue.split(',').map((item) => item.trim()).filter(Boolean);
    }

    function resolveLegacyOpenPolicy(rollout) {
      const policy = rollout?.legacy_manual_open_policy && typeof rollout.legacy_manual_open_policy === 'object'
        ? rollout.legacy_manual_open_policy
        : {};
      return {
        enabled: policy.enabled === true,
        reasonRequired: policy.reason_required === true,
        blocked: policy.blocked === true,
        blockReason: String(policy.block_reason || '').trim(),
        reviewedBy: String(policy.reviewed_by || '').trim(),
        reviewedAtUtc: String(policy.reviewed_at_utc || '').trim(),
        decision: String(policy.decision || '').trim(),
      };
    }

    function formatLegacyOpenBlockReason(reason) {
      switch (String(reason || '').trim()) {
        case 'review_decision_hold':
          return 'policy decision = HOLD';
        case 'stale_review':
          return 'policy review is stale';
        case 'invalid_review_timestamp':
          return 'policy review timestamp is invalid UTC';
        default:
          return 'legacy manual-open policy is blocking';
      }
    }

    function updateRowResponsible(row, responsible, runtimeOptions = {}) {
      if (!row) return;
      const activeDialogState = getActiveDialogState();
      const value = String(runtimeOptions.displayResponsible ?? responsible ?? '').trim();
      const rawValue = String(runtimeOptions.rawResponsible ?? responsible ?? '').trim();
      const fallbackAvatar = options.isOwnedByCurrentOperator?.(rawValue || value)
        ? String(options.operatorAvatarUrl || '').trim()
        : String(row.dataset.responsibleAvatarUrl || '').trim();
      const avatarUrl = String(runtimeOptions.avatarUrl || fallbackAvatar || '').trim();
      row.dataset.responsible = value;
      row.dataset.responsibleRaw = rawValue || value;
      row.dataset.responsibleAvatarUrl = avatarUrl;
      const responsibleIndex = typeof options.getResponsibleColumnIndex === 'function'
        ? options.getResponsibleColumnIndex()
        : -1;
      const rowCells = row.children;
      if (responsibleIndex >= 0 && rowCells[responsibleIndex]) {
        rowCells[responsibleIndex].innerHTML = options.renderResponsibleCell?.(value || 'РІР‚вЂќ', avatarUrl) || (value || 'РІР‚вЂќ');
      }
      const takeBtn = row.querySelector('.dialog-take-btn');
      if (takeBtn) {
        takeBtn.classList.toggle('d-none', !options.canTakeDialogOwnership?.(rawValue || value, options.isResolvedRow?.(row) === true));
      }
      if (row === activeDialogState.row || String(row.dataset.ticketId || '').trim() === String(activeDialogState.ticketId || '').trim()) {
        options.updateDetailsResponsible?.(value || 'РІР‚вЂќ', { rawResponsible: rawValue || value, avatarUrl });
      }
      options.syncMyDialogsStateFromTable?.();
      options.renderMyDialogsPanel?.();
    }

    function updateRowStatus(row, statusRaw, statusLabel, statusKey, unreadCount = 0) {
      if (!row) return;
      row.dataset.status = statusLabel;
      row.dataset.statusRaw = statusRaw;
      if (statusKey) {
        row.dataset.statusKey = statusKey;
      }
      const badge = row.querySelector('.badge');
      if (badge) {
        badge.textContent = statusLabel;
        badge.className = `badge rounded-pill ${options.statusClassByKey?.(statusKey) || ''}`.trim();
      }
      options.setRowUnreadCount?.(row, unreadCount);
      updateRowQuickActions(row);
      options.syncMyDialogsStateFromTable?.();
      options.renderMyDialogsPanel?.();
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
          ? `Р С›РЎвЂљР В»Р С•Р В¶Р ВµР Р… Р Т‘Р С• ${options.formatUtcDate?.(new Date(until), { includeTime: true })}`
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
        throw new Error('Р вЂќР В»РЎРЏ Р В±РЎвЂ№РЎРѓРЎвЂљРЎР‚Р С•Р С–Р С• Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С‘РЎРЏ РЎС“Р С”Р В°Р В¶Р С‘РЎвЂљР Вµ Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘РЎР‹ Р Р† Р С”Р В°РЎР‚РЎвЂљР С•РЎвЂЎР С”Р Вµ Р Т‘Р С‘Р В°Р В»Р С•Р С–Р В°.');
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
        throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
      }
      updateRowStatus(row, 'resolved', 'Р вЂ”Р В°Р С”РЎР‚РЎвЂ№РЎвЂљ', 'closed', 0);
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
        const error = new Error('Р вЂ™Р В·РЎРЏРЎвЂљРЎРЉ Р Р† РЎР‚Р В°Р В±Р С•РЎвЂљРЎС“ Р СР С•Р В¶Р Р…Р С• РЎвЂљР С•Р В»РЎРЉР С”Р С• Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎвЂ№Р в„– Р Т‘Р С‘Р В°Р В»Р С•Р С–');
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
          throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
        }
        updateRowResponsible(row || targetRow, data.responsible || '');
        if (String(activeDialogState.ticketId || '').trim() === String(ticketId || '').trim()
            && typeof options.loadDialogParticipants === 'function') {
          options.loadDialogParticipants().catch(() => {});
        }
        options.emitWorkspaceTelemetry?.('triage_quick_assign', { ticketId });
        options.applyFilters?.();
        notify('Р вЂќР С‘Р В°Р В»Р С•Р С– Р Р…Р В°Р В·Р Р…Р В°РЎвЂЎР ВµР Р… Р Р…Р В° Р Р†Р В°РЎРѓ', 'success');
        return data;
      } catch (error) {
        if (btn) btn.disabled = false;
        notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р Р†Р В·РЎРЏРЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
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
          throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
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
      elements.detailsResolve.textContent = resolved ? 'Р С›Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С•' : 'Р вЂ”Р В°Р С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ';
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
        return;

        const takeBtn = target.closest('.dialog-take-btn');
        if (takeBtn) {
          event.preventDefault();
          const actionMenu = takeBtn.closest('.dialog-actions-dropdown');
          if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
          if (!options.canRunAction?.('can_assign')) {
            options.notifyPermissionDenied?.('Р СњР В°Р В·Р Р…Р В°РЎвЂЎР С‘РЎвЂљРЎРЉ Р СР Р…Р Вµ');
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
              notify(`Р вЂќР С‘Р В°Р В»Р С•Р С– Р С•РЎвЂљР В»Р С•Р В¶Р ВµР Р… Р Р…Р В° ${resolveSnoozeSuccessSuffix(options.quickSnoozeMinutes)}`, 'success');
            })
            .catch((error) => {
              notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С•РЎвЂљР В»Р С•Р В¶Р С‘РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
            });
          return;
        }

        const closeBtn = target.closest('.dialog-close-btn');
        if (closeBtn) {
          event.preventDefault();
          const actionMenu = closeBtn.closest('.dialog-actions-dropdown');
          if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
          if (!options.canRunAction?.('can_close')) {
            options.notifyPermissionDenied?.('Р вЂ”Р В°Р С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ');
            return;
          }
          const ticketId = closeBtn.dataset.ticketId;
          const row = closeBtn.closest('tr');
          options.setActiveDialogRow?.(row, { ensureVisible: true });
          closeBtn.disabled = true;
          closeDialogQuick(ticketId, row, closeBtn)
            .then(() => {
              options.clearSnooze?.(ticketId);
              notify('Р вЂќР С‘Р В°Р В»Р С•Р С– Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљ', 'success');
            })
            .catch((error) => {
              notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
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
            notify('Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С•Р С—РЎР‚Р ВµР Т‘Р ВµР В»Р С‘РЎвЂљРЎРЉ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В° Р Т‘Р В»РЎРЏ Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р С”Р С‘', 'error');
            return;
          }
          const defaultReason = `Р РЋР С—Р В°Р С Р Р† Р Т‘Р С‘Р В°Р В»Р С•Р С–Р Вµ ${ticketId}`;
          const reasonInput = window.prompt('Р СџРЎР‚Р С‘РЎвЂЎР С‘Р Р…Р В° Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р С”Р С‘ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В° Р С”Р В°Р С” РЎРѓР С—Р В°Р С:', defaultReason);
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
              throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Р С™Р В»Р С‘Р ВµР Р…РЎвЂљ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°Р Р… Р С”Р В°Р С” РЎРѓР С—Р В°Р С', 'success');
          } catch (error) {
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°РЎвЂљРЎРЉ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В°', 'error');
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
              throw new Error('Р Р€Р С”Р В°Р В¶Р С‘РЎвЂљР Вµ РЎвЂ¦Р С•РЎвЂљРЎРЏ Р В±РЎвЂ№ Р С•Р Т‘Р Р…РЎС“ Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘РЎР‹ Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘РЎРЏ Р С—Р ВµРЎР‚Р ВµР Т‘ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С‘Р ВµР С.');
            }
            const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/resolve`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ categories }),
            });
            const data = await resp.json();
            if (!resp.ok || !data?.success) {
              throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Р вЂќР С‘Р В°Р В»Р С•Р С– Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљ', 'success');
          } catch (error) {
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
            elements.detailsResolve.disabled = false;
          }
        });
      }

      if (elements.detailsReopen) {
        elements.detailsReopen.addEventListener('click', async () => {
          const ticketId = resolveDetailsTicketId();
          const activeDialogState = getActiveDialogState();
          if (!ticketId) return;
          if (!window.confirm('Р СџР ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С•Р Вµ Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ?')) {
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
              throw new Error(data?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${resp.status}`);
            }
            await options.openDialogDetails?.(ticketId, activeDialogState.row);
            notify('Р вЂќР С‘Р В°Р В»Р С•Р С– Р С—Р ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљ', 'success');
          } catch (error) {
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
          } finally {
            elements.detailsReopen.disabled = false;
          }
        });
      }
    }

    function bindWorkspaceQuickActions() {
      if (elements.workspaceLegacyBtn) {
        elements.workspaceLegacyBtn.addEventListener('click', async () => {
          const activeDialogState = getActiveDialogState();
          const activeWorkspaceState = getActiveWorkspaceState();
          if (options.workspaceSingleMode) return;
          if (!activeWorkspaceState.ticketId || !activeDialogState.row || elements.workspaceLegacyBtn.disabled) return;
          const policy = resolveLegacyOpenPolicy(activeWorkspaceState.payload?.meta?.rollout);
          if (policy.enabled && policy.blocked) {
            const blockReason = policy.blockReason || 'policy_blocked';
            const humanReason = formatLegacyOpenBlockReason(blockReason);
            const reviewMeta = [policy.reviewedBy, policy.reviewedAtUtc].filter(Boolean).join(' @ ');
            notify(`Legacy modal blocked: ${humanReason}${reviewMeta ? ` (${reviewMeta})` : ''}.`, 'warning');
            await options.emitWorkspaceTelemetry?.('workspace_open_legacy_blocked', {
              ticketId: activeWorkspaceState.ticketId,
              reason: blockReason,
              decision: policy.decision || null,
              reviewedBy: policy.reviewedBy || null,
              reviewedAtUtc: policy.reviewedAtUtc || null,
              contractVersion: activeWorkspaceState.payload?.contract_version || null,
            });
            return;
          }
          let legacyOpenReason = 'manual_rollback';
          if (policy.enabled && policy.reasonRequired) {
            const answer = window.prompt('Р Р€Р С”Р В°Р В¶Р С‘РЎвЂљР Вµ Р С—РЎР‚Р С‘РЎвЂЎР С‘Р Р…РЎС“ manual legacy-open (UTC policy checkpoint):', 'manual_rollback');
            legacyOpenReason = String(answer || '').trim();
            if (!legacyOpenReason) {
              notify('Legacy modal Р Р…Р Вµ Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљ: РЎвЂљРЎР‚Р ВµР В±РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р С—РЎР‚Р С‘РЎвЂЎР С‘Р Р…Р В° manual open.', 'warning');
              return;
            }
          }
          await options.emitWorkspaceTelemetry?.('workspace_open_legacy_manual', {
            ticketId: activeWorkspaceState.ticketId,
            reason: legacyOpenReason,
            contractVersion: activeWorkspaceState.payload?.contract_version || null,
          });
          options.openDialogDetails?.(activeWorkspaceState.ticketId, activeDialogState.row);
        });
      }

      if (elements.workspaceAssignBtn) {
        elements.workspaceAssignBtn.addEventListener('click', async () => {
          const activeDialogState = getActiveDialogState();
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId || !activeDialogState.row) return;
          try {
            await takeDialog(activeWorkspaceState.ticketId, activeDialogState.row, elements.workspaceAssignBtn);
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Р вЂќР С‘Р В°Р В»Р С•Р С– Р Р…Р В°Р В·Р Р…Р В°РЎвЂЎР ВµР Р… Р Р…Р В° Р Р†Р В°РЎРѓ.' });
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
            notify(`Р вЂќР С‘Р В°Р В»Р С•Р С– Р С•РЎвЂљР В»Р С•Р В¶Р ВµР Р… Р Р…Р В° ${options.formatSnoozeDurationLabel?.(options.quickSnoozeMinutes)}.`, 'success');
          } catch (error) {
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С•РЎвЂљР В»Р С•Р В¶Р С‘РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
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
              throw new Error('Р вЂ™РЎвЂ№Р В±Р ВµРЎР‚Р С‘РЎвЂљР Вµ РЎвЂ¦Р С•РЎвЂљРЎРЏ Р В±РЎвЂ№ Р С•Р Т‘Р Р…РЎС“ Р С”Р В°РЎвЂљР ВµР С–Р С•РЎР‚Р С‘РЎР‹ Р С—Р ВµРЎР‚Р ВµР Т‘ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С‘Р ВµР С Р Т‘Р С‘Р В°Р В»Р С•Р С–Р В°.');
            }
            const response = await fetch(`/api/dialogs/${encodeURIComponent(activeWorkspaceState.ticketId)}/resolve`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ categories }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${response.status}`);
            }
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Р вЂќР С‘Р В°Р В»Р С•Р С– Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљ.' });
          } catch (error) {
            elements.workspaceResolveBtn.disabled = false;
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
          }
        });
      }

      if (elements.workspaceReopenBtn) {
        elements.workspaceReopenBtn.addEventListener('click', async () => {
          const activeWorkspaceState = getActiveWorkspaceState();
          if (!activeWorkspaceState.ticketId) return;
          if (!window.confirm('Р СџР ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР С•Р Вµ Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ?')) {
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
              throw new Error(payload?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${response.status}`);
            }
            await options.refreshActiveWorkspaceContract?.({ successMessage: 'Р вЂќР С‘Р В°Р В»Р С•Р С– Р С—Р ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљ.' });
          } catch (error) {
            notify(error.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р ВµРЎР‚Р ВµР С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљРЎРЉ Р Т‘Р С‘Р В°Р В»Р С•Р С–', 'error');
          } finally {
            elements.workspaceReopenBtn.disabled = false;
          }
        });
      }
    }

    function bindDetailsReplyActions() {
      if (!elements.detailsReplySend || !elements.detailsReplyText) return;
      const sendReply = async () => {
        const activeDialogState = getActiveDialogState();
        const message = elements.detailsReplyText.value.trim();
        const pendingMediaFiles = options.getPendingMediaFiles?.(elements.detailsReplyMedia) || [];
        const ticketId = resolveDetailsTicketId();
        const replyToTelegramId = options.getActiveReplyToTelegramId?.() ?? null;
        if ((!message && !pendingMediaFiles.length) || !ticketId) return;
        if (pendingMediaFiles.length) {
          await options.sendMediaFiles?.(pendingMediaFiles, {
            ticketId,
            caption: message,
            sendButton: elements.detailsReplySend,
            mediaInput: elements.detailsReplyMedia,
            replyToTelegramId,
            afterSuccess: () => {
              elements.detailsReplyText.value = '';
              options.resetReplyTarget?.();
            },
          });
          return;
        }
        elements.detailsReplySend.disabled = true;
        try {
          const response = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/reply`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              message,
              replyToTelegramId,
            }),
          });
          const payload = await response.json();
          if (!response.ok || !payload?.success) {
            throw new Error(payload?.error || `Р С›РЎв‚¬Р С‘Р В±Р С”Р В° ${response.status}`);
          }
          elements.detailsReplyText.value = '';
          options.resetReplyTarget?.();
          options.updateDetailsResponsible?.(payload.responsible || options.getActiveDialogContext?.().operatorName);
          options.appendHistoryMessage?.({
            sender: options.operatorDisplayName || payload.responsible || 'Р С›Р С—Р ВµРЎР‚Р В°РЎвЂљР С•РЎР‚',
            message,
            timestamp: payload.timestamp || new Date().toISOString(),
            messageType: 'operator_message',
          });
          if (activeDialogState.row) {
            updateRowStatus(activeDialogState.row, 'pending', 'Р С•Р В¶Р С‘Р Т‘Р В°Р ВµРЎвЂљ Р С•РЎвЂљР Р†Р ВµРЎвЂљР В° Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В°', 'waiting_client', 0);
            updateRowResponsible(activeDialogState.row, payload.responsible || activeDialogState.row.dataset.responsible || '');
            options.applyFilters?.();
          }
          options.updateDetailsStatusSummary?.('Р С•Р В¶Р С‘Р Т‘Р В°Р ВµРЎвЂљ Р С•РЎвЂљР Р†Р ВµРЎвЂљР В° Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В°', 'waiting_client');
        } catch (error) {
          notify(error?.message || 'Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С•РЎвЂљР С—РЎР‚Р В°Р Р†Р С‘РЎвЂљРЎРЉ РЎРѓР С•Р С•Р В±РЎвЂ°Р ВµР Р…Р С‘Р Вµ', 'error');
        } finally {
          elements.detailsReplySend.disabled = false;
        }
      };

      elements.detailsReplySend.addEventListener('click', sendReply);
      elements.detailsReplyText.addEventListener('keydown', (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
          sendReply();
        }
      });
      elements.detailsReplyText.addEventListener('paste', (event) => {
        const files = options.extractClipboardImageFiles?.(event) || [];
        if (!files.length) return;
        event.preventDefault();
        const totalFiles = options.stageMediaFilesInInput?.(elements.detailsReplyMedia, files) || 0;
        if (!totalFiles) {
          notify('Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—РЎР‚Р С‘Р С”РЎР‚Р ВµР С—Р С‘РЎвЂљРЎРЉ РЎРѓР С”РЎР‚Р С‘Р Р…РЎв‚¬Р С•РЎвЂљ Р В°Р Р†РЎвЂљР С•Р СР В°РЎвЂљР С‘РЎвЂЎР ВµРЎРѓР С”Р С‘. Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р в„–РЎвЂљР Вµ Р С”Р Р…Р С•Р С—Р С”РЎС“ Р С—РЎР‚Р С‘Р С”РЎР‚Р ВµР С—Р В»Р ВµР Р…Р С‘РЎРЏ Р СР ВµР Т‘Р С‘Р В°.', 'warning');
          return;
        }
      });
    }

    return {
      updateRowResponsible,
      updateRowStatus,
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
      bindDetailsReplyActions,
      bindWorkspaceQuickActions,
    };
  }

  window.DialogsActionsRuntime = {
    createRuntime,
  };
})();

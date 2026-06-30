(function () {
  if (window.DialogsListRuntime) {
    return;
  }

  const ALLOWED_DIALOG_VIEWS = new Set([
    'all',
    'active',
    'new',
    'unassigned',
    'overdue',
    'sla_critical',
    'escalation_required',
  ]);

  function normalizeDialogView(value) {
    const normalized = String(value || '').trim().toLowerCase();
    return ALLOWED_DIALOG_VIEWS.has(normalized) ? normalized : 'all';
  }

  function normalizePageSize(value, fallbackValue = 20) {
    if (value === 'all') return Infinity;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallbackValue;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const table = options.table;
    const emptyRow = options.emptyRow;

    function getFilterState() {
      return options.filterState;
    }

    function getSelectedTicketIds() {
      return options.selectedTicketIds instanceof Set ? options.selectedTicketIds : new Set();
    }

    function getLastFilteredRows() {
      return Array.isArray(options.getLastFilteredRows?.()) ? options.getLastFilteredRows() : [];
    }

    function setLastFilteredRows(rows) {
      if (typeof options.setLastFilteredRows === 'function') {
        options.setLastFilteredRows(rows);
      }
    }

    function getSlaOrchestrationByTicket() {
      return options.slaOrchestrationByTicket instanceof Map ? options.slaOrchestrationByTicket : new Map();
    }

    function getActiveDialogState() {
      const state = typeof options.getActiveDialogState === 'function'
        ? options.getActiveDialogState()
        : null;
      return state && typeof state === 'object'
        ? state
        : { row: null, ticketId: null };
    }

    function setActiveDialogState(row) {
      if (typeof options.setActiveDialogState === 'function') {
        options.setActiveDialogState(row);
      }
    }

    function setListPollTimer(timerId) {
      if (typeof options.setListPollTimer === 'function') {
        options.setListPollTimer(timerId);
      }
    }

    function getListPollTimer() {
      return typeof options.getListPollTimer === 'function' ? options.getListPollTimer() : null;
    }

    function setListLoading(nextValue) {
      if (typeof options.setListLoading === 'function') {
        options.setListLoading(nextValue);
      }
    }

    function isListLoading() {
      return typeof options.getListLoading === 'function' ? options.getListLoading() === true : false;
    }

    let triagePreferencesLoadedFromServer = false;
    let triagePreferencesSaveTimer = null;

    function resolveStorageKey(key) {
      return String(key || '').trim();
    }

    function resolvePageSize(value) {
      return normalizePageSize(value, options.defaultPageSize);
    }

    function resolveDialogView(value) {
      return normalizeDialogView(value);
    }

    function getSlaWindowPresets() {
      return Array.isArray(options.dialogSlaWindowPresets) ? options.dialogSlaWindowPresets : [];
    }

    function getViewTabs() {
      return Array.from(elements.viewTabs || []);
    }

    function loadPageSize() {
      const storageKey = resolveStorageKey(options.storage?.pageSize);
      const raw = storageKey ? localStorage.getItem(storageKey) : null;
      const normalized = resolvePageSize(raw);
      const filterState = getFilterState();
      filterState.pageSize = normalized;
      if (elements.pageSizeSelect) {
        elements.pageSizeSelect.value = normalized === Infinity ? 'all' : String(normalized);
      }
    }

    function configureSlaWindowSelect() {
      if (!elements.slaWindowSelect) {
        return;
      }
      const optionsMarkup = ['<option value="">SLA: все</option>']
        .concat(getSlaWindowPresets().map((minutes) => `<option value="${minutes}">Реакция ≤ ${minutes}м</option>`))
        .join('');
      elements.slaWindowSelect.innerHTML = optionsMarkup;
      const filterState = getFilterState();
      elements.slaWindowSelect.value = Number.isFinite(filterState.slaWindowMinutes)
        ? String(filterState.slaWindowMinutes)
        : '';
    }

    function queueServerTriagePreferencesSave() {
      if (triagePreferencesSaveTimer) {
        clearTimeout(triagePreferencesSaveTimer);
      }
      triagePreferencesSaveTimer = setTimeout(() => {
        triagePreferencesSaveTimer = null;
        void saveServerTriagePreferences();
      }, 500);
    }

    function persistPageSize() {
      const storageKey = resolveStorageKey(options.storage?.pageSize);
      if (!storageKey) return;
      const filterState = getFilterState();
      if (filterState.pageSize === Infinity) {
        localStorage.setItem(storageKey, 'all');
        queueServerTriagePreferencesSave();
        return;
      }
      localStorage.setItem(storageKey, String(filterState.pageSize));
      queueServerTriagePreferencesSave();
    }

    function restoreDialogPreferences() {
      try {
        const filterState = getFilterState();
        const viewStorageKey = resolveStorageKey(options.storage?.view);
        const slaWindowStorageKey = resolveStorageKey(options.storage?.slaWindow);
        const sortModeStorageKey = resolveStorageKey(options.storage?.sortMode);
        const storedView = viewStorageKey ? localStorage.getItem(viewStorageKey) : null;
        if (storedView) {
          filterState.view = resolveDialogView(storedView);
        }
        const storedSlaWindow = Number.parseInt(
          slaWindowStorageKey ? localStorage.getItem(slaWindowStorageKey) : null,
          10
        );
        if (Number.isFinite(storedSlaWindow) && getSlaWindowPresets().includes(storedSlaWindow)) {
          filterState.slaWindowMinutes = storedSlaWindow;
        }
        const storedSortMode = String(sortModeStorageKey ? localStorage.getItem(sortModeStorageKey) : '')
          .trim()
          .toLowerCase();
        filterState.sortMode = storedSortMode === 'sla_priority' ? 'sla_priority' : filterState.sortMode;
      } catch (_error) {
        // ignore storage read errors
      }
    }

    function persistDialogPreferences() {
      try {
        const filterState = getFilterState();
        const viewStorageKey = resolveStorageKey(options.storage?.view);
        const slaWindowStorageKey = resolveStorageKey(options.storage?.slaWindow);
        const sortModeStorageKey = resolveStorageKey(options.storage?.sortMode);
        if (viewStorageKey) {
          localStorage.setItem(viewStorageKey, filterState.view || 'all');
        }
        if (slaWindowStorageKey) {
          if (Number.isFinite(filterState.slaWindowMinutes) && filterState.slaWindowMinutes > 0) {
            localStorage.setItem(slaWindowStorageKey, String(filterState.slaWindowMinutes));
          } else {
            localStorage.removeItem(slaWindowStorageKey);
          }
        }
        if (sortModeStorageKey) {
          localStorage.setItem(sortModeStorageKey, filterState.sortMode || 'default');
        }
        queueServerTriagePreferencesSave();
      } catch (_error) {
        // ignore storage write errors
      }
    }

    function applyServerTriagePreferences(preferences) {
      if (!preferences || typeof preferences !== 'object') return false;
      const filterState = getFilterState();
      let changed = false;
      const rawView = String(preferences.view || '').trim().toLowerCase();
      if (rawView) {
        const normalizedView = resolveDialogView(rawView);
        if (normalizedView !== filterState.view) {
          filterState.view = normalizedView;
          changed = true;
        }
      }
      const rawSortMode = String(preferences.sort_mode || preferences.sortMode || '').trim().toLowerCase();
      if (rawSortMode) {
        const normalizedSortMode = rawSortMode === 'sla_priority' ? 'sla_priority' : 'default';
        if (normalizedSortMode !== filterState.sortMode) {
          filterState.sortMode = normalizedSortMode;
          changed = true;
        }
      }
      const rawSlaWindow = Number.parseInt(preferences.sla_window_minutes ?? preferences.slaWindowMinutes, 10);
      if (Number.isFinite(rawSlaWindow) && getSlaWindowPresets().includes(rawSlaWindow)) {
        if (rawSlaWindow !== filterState.slaWindowMinutes) {
          filterState.slaWindowMinutes = rawSlaWindow;
          changed = true;
        }
      } else if ((preferences.sla_window_minutes === null || preferences.slaWindowMinutes === null)
        && Number.isFinite(filterState.slaWindowMinutes)) {
        filterState.slaWindowMinutes = null;
        changed = true;
      }
      const rawPageSize = String(preferences.page_size || preferences.pageSize || '').trim().toLowerCase();
      if (rawPageSize) {
        const normalizedPageSize = resolvePageSize(rawPageSize);
        if (normalizedPageSize !== filterState.pageSize) {
          filterState.pageSize = normalizedPageSize;
          changed = true;
        }
      }
      return changed;
    }

    function bindViewStateEvents() {
      if (elements.pageSizeSelect) {
        elements.pageSizeSelect.addEventListener('change', () => {
          const filterState = getFilterState();
          filterState.pageSize = resolvePageSize(elements.pageSizeSelect.value);
          persistPageSize();
          applyFilters({ resetPage: true });
        });
      }

      if (elements.slaWindowSelect) {
        elements.slaWindowSelect.addEventListener('change', () => {
          const parsed = Number.parseInt(elements.slaWindowSelect.value, 10);
          const filterState = getFilterState();
          filterState.slaWindowMinutes = Number.isFinite(parsed) && parsed > 0 ? parsed : null;
          persistDialogPreferences();
          applyFilters({ resetPage: true });
        });
      }

      if (elements.sortModeSelect) {
        elements.sortModeSelect.addEventListener('change', () => {
          const filterState = getFilterState();
          const value = String(elements.sortModeSelect.value || 'default').trim().toLowerCase();
          filterState.sortMode = value === 'sla_priority' ? 'sla_priority' : 'default';
          if (filterState.sortMode !== 'sla_priority') {
            options.setLastManualSortMode?.(filterState.sortMode);
          }
          persistDialogPreferences();
          applyFilters({ resetPage: true });
        });
      }

      getViewTabs().forEach((tab) => {
        tab.addEventListener('click', () => {
          setViewTab(tab.dataset.dialogView || 'all');
        });
      });
    }

    async function loadServerTriagePreferences() {
      try {
        const response = await fetch('/api/dialogs/triage-preferences', { headers: { Accept: 'application/json' } });
        if (!response.ok) return;
        const payload = await response.json().catch(() => null);
        if (!payload || payload.success !== true || !payload.preferences) return;
        if (applyServerTriagePreferences(payload.preferences)) {
          if (elements.pageSizeSelect) {
            const filterState = getFilterState();
            elements.pageSizeSelect.value = filterState.pageSize === Infinity ? 'all' : String(filterState.pageSize);
          }
          configureSlaWindowSelect();
          if (elements.sortModeSelect) {
            elements.sortModeSelect.value = getFilterState().sortMode;
          }
          const activeTab = getViewTabs().find((tab) => tab.dataset.dialogView === getFilterState().view);
          if (activeTab) {
            setViewTab(getFilterState().view);
          } else {
            applyFilters();
          }
        }
        triagePreferencesLoadedFromServer = true;
      } catch (_error) {
        // ignore network errors and continue with local defaults
      }
    }

    async function saveServerTriagePreferences() {
      const filterState = getFilterState();
      const payload = {
        view: filterState.view || 'all',
        sort_mode: filterState.sortMode || 'default',
        sla_window_minutes: Number.isFinite(filterState.slaWindowMinutes) ? filterState.slaWindowMinutes : null,
        page_size: filterState.pageSize === Infinity ? 'all' : String(filterState.pageSize || options.defaultPageSize),
      };
      try {
        const response = await fetch('/api/dialogs/triage-preferences', {
          method: 'POST',
          headers: {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(payload),
        });
        if (!response.ok) return;
        if (triagePreferencesLoadedFromServer) {
          options.emitWorkspaceTelemetry('triage_preferences_saved', {
            reason: `view=${payload.view};sort=${payload.sort_mode};sla=${payload.sla_window_minutes || 'all'};page=${payload.page_size}`,
          });
        }
      } catch (_error) {
        // ignore network errors and keep local-storage behavior
      }
    }

    function updateSelectAllState() {
      if (!elements.selectAllCheckbox) {
        return;
      }
      const visible = visibleRows().filter((row) => row.querySelector('.dialog-row-select'));
      const selectedTicketIds = getSelectedTicketIds();
      if (!visible.length) {
        elements.selectAllCheckbox.checked = false;
        elements.selectAllCheckbox.indeterminate = false;
        elements.selectAllCheckbox.disabled = true;
        return;
      }
      const selectedVisible = visible.filter((row) => selectedTicketIds.has(String(row.dataset.ticketId || ''))).length;
      elements.selectAllCheckbox.disabled = false;
      elements.selectAllCheckbox.checked = selectedVisible > 0 && selectedVisible === visible.length;
      elements.selectAllCheckbox.indeterminate = selectedVisible > 0 && selectedVisible < visible.length;
    }

    function rowsList() {
      return typeof options.rowsList === 'function' ? options.rowsList() : [];
    }

    function syncDialogsTable(dialogs) {
      const tbody = table?.tBodies?.[0];
      if (!tbody) {
        return;
      }
      const nextDialogs = Array.isArray(dialogs) ? dialogs : [];
      const existingByTicketId = new Map(
        rowsList().map((row) => [String(row.dataset.ticketId || ''), row])
      );
      const orderedRows = [];
      nextDialogs.forEach((item) => {
        const ticketId = String(item?.ticketId || '');
        if (!ticketId) {
          return;
        }
        const nextMarker = options.buildDialogItemMarker(item);
        const existingRow = existingByTicketId.get(ticketId);
        if (existingRow) {
          existingByTicketId.delete(ticketId);
          if (String(existingRow.dataset.dialogMarker || '') === nextMarker) {
            orderedRows.push(existingRow);
            return;
          }
          existingRow.remove();
        }
        const nextRow = options.createDialogRowElement(item);
        if (nextRow) {
          orderedRows.push(nextRow);
        }
      });

      existingByTicketId.forEach((row) => {
        row.remove();
      });

      orderedRows.forEach((row) => {
        tbody.appendChild(row);
      });

      if (!orderedRows.length) {
        rowsList().forEach((row) => row.remove());
      }

      options.applyColumnState();
      options.applyBusinessCellStyles();
      options.restoreColumnWidths();
      options.updateAllSlaBadges();
      const selectedTicketIds = getSelectedTicketIds();
      const availableTicketIds = new Set(rowsList().map((row) => String(row.dataset.ticketId || '')));
      Array.from(selectedTicketIds).forEach((ticketId) => {
        if (!availableTicketIds.has(ticketId)) {
          selectedTicketIds.delete(ticketId);
        }
      });
      applyFilters();
      rowsList().forEach((row) => options.updateRowQuickActions(row));

      const activeState = getActiveDialogState();
      if (activeState.ticketId) {
        const selector = `.dialog-open-btn[data-ticket-id="${options.escapeSelectorValue(activeState.ticketId)}"]`;
        const openBtn = table.querySelector(selector);
        setActiveDialogRow(openBtn ? openBtn.closest('tr') : null);
      }
    }

    async function refreshDialogsList() {
      if (isListLoading()) {
        return;
      }
      if (document.visibilityState === 'hidden') {
        return;
      }
      setListLoading(true);
      try {
        const resp = await fetch('/api/dialogs', {
          credentials: 'same-origin',
          cache: 'no-store',
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        const dialogs = data.dialogs || [];
        syncSlaOrchestrationSignals(data.sla_orchestration || null);
        applySlaOrchestrationToRows();
        const marker = options.buildDialogsMarker(dialogs);
        const lastMarker = typeof options.getLastListMarker === 'function' ? options.getLastListMarker() : null;
        const isInitialSync = lastMarker === null;
        const hasListChanges = !isInitialSync && marker !== lastMarker;
        if (isInitialSync || marker !== lastMarker) {
          options.setLastListMarker?.(marker);
        }
        syncDialogsTable(dialogs);
        if (data.my_dialogs && typeof data.my_dialogs === 'object') {
          options.normalizeMyDialogsState(data.my_dialogs);
        } else {
          options.syncMyDialogsStateFromTable();
        }
        options.renderMyDialogsPanel();
        applySlaOrchestrationToRows();
        options.refreshSummaryCounters(data.summary || {});
        if (hasListChanges) {
          options.requestSidebarNotificationRefresh('dialogs-list-change');
        }
      } catch (_error) {
        // ignore polling errors
      } finally {
        setListLoading(false);
      }
    }

    function startDialogsPolling() {
      if (getListPollTimer()) {
        return;
      }
      setListPollTimer(setInterval(refreshDialogsList, options.listPollInterval));
    }

    function stopDialogsPolling() {
      const timerId = getListPollTimer();
      if (timerId) {
        clearInterval(timerId);
        setListPollTimer(null);
      }
    }

    function ensureEmptyRow() {
      const tbody = table?.tBodies?.[0];
      if (!tbody || !emptyRow) {
        return;
      }
      if (!tbody.contains(emptyRow)) {
        tbody.appendChild(emptyRow);
      }
    }

    function syncSlaOrchestrationSignals(slaOrchestration) {
      const store = getSlaOrchestrationByTicket();
      store.clear();
      const tickets = slaOrchestration && typeof slaOrchestration === 'object'
        ? slaOrchestration.tickets
        : null;
      if (!tickets || typeof tickets !== 'object') {
        return;
      }
      Object.entries(tickets).forEach(([ticketId, signal]) => {
        if (!ticketId || !signal || typeof signal !== 'object') {
          return;
        }
        store.set(String(ticketId), signal);
      });
    }

    function applySlaOrchestrationToRows() {
      const store = getSlaOrchestrationByTicket();
      rowsList().forEach((row) => {
        const ticketId = String(row?.dataset?.ticketId || '');
        const signal = store.get(ticketId);
        if (!signal) {
          row.dataset.slaServerState = '';
          row.dataset.slaServerPinned = '';
          row.dataset.slaMinutesLeft = '';
          row.dataset.slaEscalationRequired = '';
          return;
        }
        const minutesLeft = Number(signal.minutes_left);
        row.dataset.slaServerState = String(signal.state || '');
        row.dataset.slaServerPinned = signal.auto_pin ? 'true' : 'false';
        row.dataset.slaMinutesLeft = Number.isFinite(minutesLeft) ? String(minutesLeft) : '';
        row.dataset.slaEscalationRequired = signal.escalation_required ? 'true' : 'false';
      });
    }

    function resolveSlaPriority(row) {
      if (!row || isResolved(row)) {
        return { bucket: 3, minutesLeft: Number.POSITIVE_INFINITY, state: 'closed' };
      }
      const minutesLeft = resolveSlaMinutesLeft(row);
      if (!Number.isFinite(minutesLeft)) {
        return { bucket: 3, minutesLeft: Number.POSITIVE_INFINITY, state: 'unknown' };
      }
      if (minutesLeft <= 0) {
        return { bucket: 0, minutesLeft, state: 'breached' };
      }
      if (minutesLeft <= options.slaWarningMinutes) {
        return { bucket: 1, minutesLeft, state: 'at_risk' };
      }
      return { bucket: 2, minutesLeft, state: 'normal' };
    }

    function applySlaPriorityClass(row) {
      if (!row) {
        return;
      }
      row.classList.remove(
        'dialog-priority-breached',
        'dialog-priority-at-risk',
        'dialog-priority-normal',
        'dialog-priority-pinned',
        'dialog-priority-escalation-required'
      );
      const priority = resolveSlaPriority(row);
      const pinned = isCriticalSlaDialog(row);
      const escalationRequired = isEscalationRequiredDialog(row);
      row.dataset.slaPinned = pinned ? 'true' : 'false';
      row.classList.toggle('dialog-priority-pinned', pinned);
      row.classList.toggle('dialog-priority-escalation-required', escalationRequired);
      if (priority.state === 'breached') {
        row.classList.add('dialog-priority-breached');
      } else if (priority.state === 'at_risk') {
        row.classList.add('dialog-priority-at-risk');
      } else if (priority.state === 'normal') {
        row.classList.add('dialog-priority-normal');
      }
    }

    function compareRowsBySlaPriority(left, right) {
      const leftPinned = left?.dataset?.slaPinned === 'true';
      const rightPinned = right?.dataset?.slaPinned === 'true';
      if (leftPinned !== rightPinned) {
        return leftPinned ? -1 : 1;
      }
      const leftPriority = resolveSlaPriority(left);
      const rightPriority = resolveSlaPriority(right);
      if (leftPriority.bucket !== rightPriority.bucket) {
        return leftPriority.bucket - rightPriority.bucket;
      }
      if (leftPriority.minutesLeft !== rightPriority.minutesLeft) {
        return leftPriority.minutesLeft - rightPriority.minutesLeft;
      }
      const leftCreated = options.parseUtcDateValue(String(left?.dataset?.createdAt || ''))?.getTime() ?? 0;
      const rightCreated = options.parseUtcDateValue(String(right?.dataset?.createdAt || ''))?.getTime() ?? 0;
      if (Number.isFinite(leftCreated) && Number.isFinite(rightCreated) && leftCreated !== rightCreated) {
        return leftCreated - rightCreated;
      }
      return String(left?.dataset?.ticketId || '').localeCompare(String(right?.dataset?.ticketId || ''), 'ru');
    }

    function isNewDialog(row) {
      const key = String(row.dataset.statusKey || '').toLowerCase();
      const raw = String(row.dataset.statusRaw || '').toLowerCase();
      return key === 'new' || raw === 'new';
    }

    function isUnassignedDialog(row) {
      const responsible = String(row.dataset.responsible || '').trim().toLowerCase();
      return !responsible || responsible === '—' || responsible === '-';
    }

    function isOverdueDialog(row) {
      if (isResolved(row)) {
        return false;
      }
      const createdAtRaw = String(row.dataset.createdAt || '').trim();
      if (!createdAtRaw) {
        return false;
      }
      const createdAt = options.parseUtcDateValue(createdAtRaw);
      if (!createdAt) {
        return false;
      }
      const overdueThresholdMs = options.overdueThresholdHours * 60 * 60 * 1000;
      return Date.now() - createdAt.getTime() > overdueThresholdMs;
    }

    function isSnoozedDialog(row) {
      return Boolean(options.getSnoozeUntil(row?.dataset?.ticketId));
    }

    function isCriticalSlaDialog(row) {
      if (row?.dataset?.slaServerPinned === 'true') {
        return !options.slaCriticalPinUnassignedOnly || isUnassignedDialog(row);
      }
      const minutesLeft = resolveSlaMinutesLeft(row);
      if (!Number.isFinite(minutesLeft)) {
        return false;
      }
      if (minutesLeft > options.slaCriticalMinutes) {
        return false;
      }
      if (options.slaCriticalPinUnassignedOnly && !isUnassignedDialog(row)) {
        return false;
      }
      return true;
    }

    function isEscalationRequiredDialog(row) {
      return row?.dataset?.slaEscalationRequired === 'true';
    }

    function resolveSlaMinutesLeft(row) {
      if (!row || isResolved(row)) {
        return null;
      }
      const serverMinutesLeft = Number(row.dataset.slaMinutesLeft);
      if (Number.isFinite(serverMinutesLeft)) {
        return serverMinutesLeft;
      }
      const createdAtRaw = String(row.dataset.createdAt || '').trim();
      if (!createdAtRaw) {
        return null;
      }
      const createdAt = options.parseUtcDateValue(createdAtRaw);
      if (!createdAt) {
        return null;
      }
      return options.slaTargetMinutes - ((Date.now() - createdAt.getTime()) / 60000);
    }

    function matchesSlaReactionWindow(row) {
      const filterState = getFilterState();
      if (!Number.isFinite(filterState.slaWindowMinutes) || filterState.slaWindowMinutes <= 0) {
        return true;
      }
      const minutesLeft = resolveSlaMinutesLeft(row);
      if (!Number.isFinite(minutesLeft)) {
        return false;
      }
      return minutesLeft <= filterState.slaWindowMinutes;
    }

    function matchesCurrentView(row) {
      const filterState = getFilterState();
      if (isSnoozedDialog(row) && filterState.view !== 'all') {
        return false;
      }
      switch (filterState.view) {
        case 'active':
          return !isResolved(row);
        case 'new':
          return isNewDialog(row);
        case 'unassigned':
          return isUnassignedDialog(row);
        case 'overdue':
          return isOverdueDialog(row);
        case 'sla_critical':
          return isCriticalSlaDialog(row)
            && (!options.slaCriticalViewUnassignedOnly || isUnassignedDialog(row));
        case 'escalation_required':
          return isEscalationRequiredDialog(row);
        default:
          return true;
      }
    }

    function isResolved(row) {
      const raw = (row?.dataset?.statusRaw || '').toLowerCase();
      const key = (row?.dataset?.statusKey || '').toLowerCase();
      return raw === 'resolved' || raw === 'closed' || key.includes('closed');
    }

    function syncRowSelectionState(row) {
      if (!row) {
        return;
      }
      const ticketId = String(row.dataset.ticketId || '');
      const checkbox = row.querySelector('.dialog-row-select');
      if (!checkbox || !ticketId) {
        return;
      }
      checkbox.checked = getSelectedTicketIds().has(ticketId);
    }

    function selectedRows() {
      const selectedTicketIds = getSelectedTicketIds();
      return rowsList().filter((row) => selectedTicketIds.has(String(row.dataset.ticketId || '')));
    }

    function updateBulkActionsState() {
      const count = getSelectedTicketIds().size;
      const canShowBulkToolbar = count > 1;
      if (elements.bulkToolbar) {
        elements.bulkToolbar.classList.toggle('d-none', !canShowBulkToolbar);
      }
      if (elements.bulkCount) {
        elements.bulkCount.textContent = `Выбрано: ${count}`;
      }
      if (elements.bulkTakeBtn) elements.bulkTakeBtn.disabled = !canShowBulkToolbar || !options.canRunAction('can_bulk') || !options.canRunAction('can_assign');
      if (elements.bulkSnoozeBtn) elements.bulkSnoozeBtn.disabled = !canShowBulkToolbar || !options.canRunAction('can_bulk') || !options.canRunAction('can_snooze');
      if (elements.bulkCloseBtn) elements.bulkCloseBtn.disabled = !canShowBulkToolbar || !options.canRunAction('can_bulk') || !options.canRunAction('can_close');
      if (elements.bulkClearBtn) elements.bulkClearBtn.disabled = !canShowBulkToolbar;
      rowsList().forEach((row) => syncRowSelectionState(row));
      updateSelectAllState();
    }

    function clearSelection() {
      getSelectedTicketIds().clear();
      updateBulkActionsState();
    }

    async function runBulkAction(action) {
      const permissionMap = {
        take: ['can_bulk', 'can_assign', 'Назначить выбранные на меня'],
        snooze: ['can_bulk', 'can_snooze', `Отложить выбранные на ${options.formatSnoozeActionLabel(options.quickSnoozeMinutes).replace('Отложить ', '')}`],
        close: ['can_bulk', 'can_close', 'Закрыть выбранные'],
      };
      const [bulkPermission, actionPermission, actionTitle] = permissionMap[action] || [];
      if (!options.canRunAction(bulkPermission) || !options.canRunAction(actionPermission)) {
        options.notifyPermissionDenied(actionTitle || 'Групповое действие');
        options.emitWorkspaceTelemetry('triage_bulk_action', {
          reason: `${action || 'unknown'}:permission_denied`,
        });
        return;
      }

      const rows = selectedRows();
      if (!rows.length) {
        return;
      }

      const eligibilitySelectorMap = {
        take: '.dialog-take-btn:not(.d-none)',
        snooze: '.dialog-snooze-btn:not(.d-none)',
        close: '.dialog-close-btn:not(.d-none)',
      };
      const selector = eligibilitySelectorMap[action];
      const eligibleRows = selector
        ? rows.filter((row) => Boolean(row.querySelector(selector)))
        : rows;
      const skippedRows = rows.filter((row) => !eligibleRows.includes(row));
      if (!eligibleRows.length) {
        if (typeof options.showNotification === 'function') {
          options.showNotification('Нет диалогов, подходящих для выбранного группового действия.', 'warning');
        }
        options.emitWorkspaceTelemetry('triage_bulk_action', {
          reason: `${action || 'unknown'}:nothing_eligible:selected=${rows.length}`,
        });
        return;
      }
      if (skippedRows.length && typeof options.showNotification === 'function') {
        options.showNotification(`Пропущено ${skippedRows.length} диалог(ов): действие недоступно для текущего статуса/прав.`, 'warning');
      }
      let processedCount = 0;
      const originalDisabled = [elements.bulkTakeBtn, elements.bulkSnoozeBtn, elements.bulkCloseBtn, elements.bulkClearBtn]
        .filter(Boolean)
        .map((button) => ({ button, disabled: button.disabled }));
      originalDisabled.forEach(({ button }) => {
        button.disabled = true;
      });

      const errors = [];
      for (const row of eligibleRows) {
        const ticketId = String(row.dataset.ticketId || '');
        if (!ticketId) {
          continue;
        }
        try {
          if (action === 'take') {
            const takeBtn = row.querySelector('.dialog-take-btn:not(.d-none)');
            if (!takeBtn) continue;
            await options.takeDialog(ticketId, row, takeBtn);
          }
          if (action === 'snooze') {
            const snoozeBtn = row.querySelector('.dialog-snooze-btn:not(.d-none)');
            if (!snoozeBtn) continue;
            await options.snoozeDialog(ticketId, options.quickSnoozeMinutes, snoozeBtn);
            options.setSnooze(ticketId, options.quickSnoozeMinutes);
            options.updateRowQuickActions(row);
          }
          if (action === 'close') {
            const closeBtn = row.querySelector('.dialog-close-btn:not(.d-none)');
            if (!closeBtn) continue;
            await options.closeDialogQuick(ticketId, row, closeBtn);
            options.clearSnooze(ticketId);
          }
          processedCount += 1;
        } catch (error) {
          errors.push(`${ticketId}: ${error.message || 'ошибка'}`);
        }
      }

      applyFilters();
      if (errors.length) {
        if (typeof options.showNotification === 'function') {
          options.showNotification(`Часть операций не выполнена (${errors.length}).`, 'error');
        }
        console.warn('Bulk action errors', action, errors);
      } else if (typeof options.showNotification === 'function') {
        const successMap = {
          take: 'Выбранные диалоги назначены на вас',
          snooze: options.formatBulkSnoozeLabel(options.quickSnoozeMinutes),
          close: 'Выбранные диалоги закрыты',
        };
        options.showNotification(successMap[action] || 'Групповое действие выполнено', 'success');
      }
      options.emitWorkspaceTelemetry('triage_bulk_action', {
        reason: `${action || 'unknown'}:${errors.length ? 'partial_failure' : 'success'}:processed=${processedCount}:errors=${errors.length}:skipped=${skippedRows.length}`,
        ticketId: eligibleRows.length === 1 ? String(eligibleRows[0]?.dataset?.ticketId || '') : null,
      });

      clearSelection();
      originalDisabled.forEach(({ button, disabled }) => {
        button.disabled = disabled;
      });
      updateBulkActionsState();
    }

    function applyFilters(runtimeOptions = {}) {
      const filterState = getFilterState();
      if (runtimeOptions.resetPage === true) {
        filterState.page = 1;
      }
      options.updateAllSlaBadges();
      const search = (filterState.search || '').trim().toLowerCase();
      const status = (filterState.status || '').trim().toLowerCase();
      const matchedRows = [];
      const hiddenRows = [];
      rowsList().forEach((row) => {
        const text = options.collectRowSearchText(row);
        const statusValue = (row.dataset.status || '').toLowerCase();
        const matchesSearch = !search || text.includes(search);
        const matchesStatus = !status || statusValue === status;
        const matchesView = matchesCurrentView(row);
        const matchesSlaWindow = matchesSlaReactionWindow(row);
        const visible = matchesSearch && matchesStatus && matchesView && matchesSlaWindow;
        row.classList.toggle('d-none', !visible);
        applySlaPriorityClass(row);
        if (visible) {
          matchedRows.push(row);
        } else {
          hiddenRows.push(row);
        }
      });
      if (filterState.sortMode === 'sla_priority') {
        matchedRows.sort(compareRowsBySlaPriority);
      }
      setLastFilteredRows(matchedRows.slice());
      const tableBody = table?.tBodies?.[0];
      matchedRows.forEach((row) => tableBody?.appendChild(row));
      hiddenRows.forEach((row) => tableBody?.appendChild(row));
      const visibleCount = applyPageSize(matchedRows);
      updateZebraRows(matchedRows);
      ensureEmptyRow();
      if (emptyRow) {
        emptyRow.classList.toggle('d-none', visibleCount !== 0);
      }
      updateBulkActionsState();
    }

    function applyQuickSearch(value) {
      const filterState = getFilterState();
      filterState.search = value || '';
      applyFilters({ resetPage: true });
    }

    function applyStatusFilter(statusLabel = '') {
      const filterState = getFilterState();
      const normalized = String(statusLabel || '').trim().toLowerCase();
      filterState.status = normalized;
      if (elements.statusFilter) {
        const statusOptions = Array.from(elements.statusFilter.options || []);
        const match = statusOptions.find((option) => String(option.value || '').trim().toLowerCase() === normalized);
        elements.statusFilter.value = match ? match.value : '';
      }
      applyFilters({ resetPage: true });
    }

    function setViewTab(nextView) {
      const filterState = getFilterState();
      const resolvedView = nextView || 'all';
      const previousView = filterState.view || 'all';
      const isEnteringCriticalView = resolvedView === 'sla_critical';
      const isLeavingCriticalView = filterState.view === 'sla_critical' && resolvedView !== 'sla_critical';

      if (options.autoSlaPriorityForCriticalView && isEnteringCriticalView) {
        if (filterState.sortMode !== 'sla_priority') {
          options.setLastManualSortMode?.(filterState.sortMode);
        }
        filterState.sortMode = 'sla_priority';
        if (elements.sortModeSelect) {
          elements.sortModeSelect.value = 'sla_priority';
        }
      } else if (options.autoSlaPriorityForCriticalView && isLeavingCriticalView) {
        const lastManualSortMode = typeof options.getLastManualSortMode === 'function'
          ? options.getLastManualSortMode()
          : 'default';
        filterState.sortMode = lastManualSortMode === 'sla_priority' ? 'default' : lastManualSortMode;
        if (elements.sortModeSelect) {
          elements.sortModeSelect.value = filterState.sortMode;
        }
      }

      filterState.view = resolvedView;
      Array.from(elements.viewTabs || []).forEach((tab) => {
        tab.classList.toggle('active', tab.dataset.dialogView === filterState.view);
      });
      if (previousView !== resolvedView) {
        options.emitWorkspaceTelemetry('triage_view_switch', { reason: `${previousView}->${resolvedView}` });
      }
      persistDialogPreferences();
      applyFilters({ resetPage: true });
    }

    function visibleRows() {
      return rowsList().filter((row) => !row.classList.contains('d-none'));
    }

    function ensureDialogRowPage(row) {
      const filterState = getFilterState();
      if (!row || filterState.pageSize === Infinity || !row.classList.contains('d-none')) {
        return;
      }
      const rowIndex = getLastFilteredRows().indexOf(row);
      if (rowIndex < 0) {
        return;
      }
      const limit = Number.isFinite(filterState.pageSize) && filterState.pageSize > 0
        ? filterState.pageSize
        : options.defaultPageSize;
      const nextPage = Math.floor(rowIndex / limit) + 1;
      if (nextPage !== filterState.page) {
        filterState.page = nextPage;
        applyFilters();
      }
    }

    function setActiveDialogRow(row, runtimeOptions = {}) {
      const currentState = getActiveDialogState();
      const nextRow = row && row.tagName === 'TR' ? row : null;
      if (nextRow && runtimeOptions.ensureVisible) {
        ensureDialogRowPage(nextRow);
      }
      if (currentState.row && currentState.row !== nextRow) {
        currentState.row.classList.remove('dialog-row-active');
      }
      setActiveDialogState(nextRow);
      const nextState = getActiveDialogState();
      if (nextState.row) {
        nextState.row.classList.add('dialog-row-active');
        if (runtimeOptions.ensureVisible) {
          nextState.row.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        }
      }
    }

    function getShortcutTargetRow() {
      const currentState = getActiveDialogState();
      if (currentState.row && !currentState.row.classList.contains('d-none')) {
        return currentState.row;
      }
      return visibleRows()[0] || null;
    }

    function openVisibleDialogByOffset(offset) {
      const rows = visibleRows();
      if (!rows.length) {
        return;
      }
      const currentState = getActiveDialogState();
      const currentIndex = currentState.row ? rows.indexOf(currentState.row) : -1;
      let nextIndex = currentIndex + offset;
      if (nextIndex < 0) nextIndex = rows.length - 1;
      if (nextIndex >= rows.length) nextIndex = 0;
      const nextRow = rows[nextIndex];
      const openButton = nextRow?.querySelector('.dialog-open-btn');
      const ticketId = openButton?.dataset?.ticketId || nextRow?.dataset?.ticketId;
      if (!ticketId) {
        return;
      }
      setActiveDialogRow(nextRow, { ensureVisible: true });
      options.openDialogEntry(ticketId, nextRow);
    }

    function resolveListPagination(totalItems) {
      const filterState = getFilterState();
      let limit = filterState.pageSize;
      if (limit === Infinity) {
        return {
          limit: Infinity,
          totalItems,
          totalPages: totalItems > 0 ? 1 : 0,
          currentPage: totalItems > 0 ? 1 : 0,
          startIndex: 0,
          endIndex: totalItems,
          visibleCount: totalItems,
        };
      }
      if (!Number.isFinite(limit) || limit <= 0) {
        limit = options.defaultPageSize;
      }
      const totalPages = totalItems > 0 ? Math.ceil(totalItems / limit) : 0;
      const currentPage = totalPages > 0
        ? Math.min(Math.max(1, Number.parseInt(filterState.page, 10) || 1), totalPages)
        : 0;
      const startIndex = currentPage > 0 ? (currentPage - 1) * limit : 0;
      const endIndex = currentPage > 0 ? Math.min(startIndex + limit, totalItems) : 0;
      return {
        limit,
        totalItems,
        totalPages,
        currentPage,
        startIndex,
        endIndex,
        visibleCount: Math.max(0, endIndex - startIndex),
      };
    }

    function updatePaginationControls(pagination) {
      if (!elements.pagePrevBtn || !elements.pageNextBtn || !elements.pageState) {
        return;
      }
      const totalItems = Number(pagination?.totalItems || 0);
      const totalPages = Number(pagination?.totalPages || 0);
      const currentPage = Number(pagination?.currentPage || 0);
      const start = totalItems > 0 ? Number(pagination?.startIndex || 0) + 1 : 0;
      const end = totalItems > 0 ? Number(pagination?.endIndex || 0) : 0;

      elements.pagePrevBtn.disabled = currentPage <= 1;
      elements.pageNextBtn.disabled = currentPage <= 0 || currentPage >= totalPages;
      renderPaginationPageNumbers(totalPages, currentPage, pagination?.limit);

      if (totalItems === 0) {
        elements.pageState.textContent = '0 из 0';
        return;
      }

      if (pagination?.limit === Infinity || totalPages <= 1) {
        elements.pageState.textContent = `${totalItems} из ${totalItems}`;
        return;
      }

      elements.pageState.textContent = `${start}-${end} из ${totalItems} | стр. ${currentPage}/${totalPages}`;
    }

    function renderPaginationPageNumbers(totalPages, currentPage, limit) {
      if (!elements.pageNumbers) {
        return;
      }
      elements.pageNumbers.innerHTML = '';

      if (limit === Infinity || totalPages <= 1 || currentPage <= 0) {
        return;
      }

      const windowSize = 5;
      const safeTotalPages = Math.max(0, Number(totalPages) || 0);
      const safeCurrentPage = Math.min(Math.max(1, Number(currentPage) || 1), safeTotalPages);
      let startPage = Math.max(1, safeCurrentPage - Math.floor(windowSize / 2));
      let endPage = Math.min(safeTotalPages, startPage + windowSize - 1);

      if ((endPage - startPage + 1) < windowSize) {
        startPage = Math.max(1, endPage - windowSize + 1);
      }

      for (let page = startPage; page <= endPage; page += 1) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = page === safeCurrentPage ? 'btn btn-primary btn-sm' : 'btn btn-outline-secondary btn-sm';
        button.textContent = String(page);
        button.setAttribute('aria-label', `Страница ${page}`);
        if (page === safeCurrentPage) {
          button.setAttribute('aria-current', 'page');
          button.disabled = true;
        } else {
          button.addEventListener('click', () => {
            getFilterState().page = page;
            applyFilters();
          });
        }
        elements.pageNumbers.appendChild(button);
      }
    }

    function applyPageSize(matchedRows) {
      const pagination = resolveListPagination(matchedRows.length);
      getFilterState().page = pagination.currentPage || 1;
      matchedRows.forEach((row, index) => {
        const visible = pagination.limit === Infinity
          ? true
          : index >= pagination.startIndex && index < pagination.endIndex;
        row.classList.toggle('d-none', !visible);
      });
      updatePaginationControls(pagination);
      return pagination.visibleCount;
    }

    function updateZebraRows(matchedRows) {
      rowsList().forEach((row) => {
        row.classList.remove('dialog-row-even', 'dialog-row-odd');
      });
      matchedRows.filter((row) => !row.classList.contains('d-none')).forEach((row, index) => {
        row.classList.add(index % 2 === 0 ? 'dialog-row-odd' : 'dialog-row-even');
      });
    }

    return {
      syncDialogsTable,
      refreshDialogsList,
      startDialogsPolling,
      stopDialogsPolling,
      syncSlaOrchestrationSignals,
      applySlaOrchestrationToRows,
      resolveSlaPriority,
      applySlaPriorityClass,
      compareRowsBySlaPriority,
      isNewDialog,
      isUnassignedDialog,
      isOverdueDialog,
      isSnoozedDialog,
      isCriticalSlaDialog,
      isEscalationRequiredDialog,
      resolveSlaMinutesLeft,
      matchesSlaReactionWindow,
      matchesCurrentView,
      isResolved,
      syncRowSelectionState,
      selectedRows,
      updateSelectAllState,
      updateBulkActionsState,
      clearSelection,
      runBulkAction,
      loadPageSize,
      configureSlaWindowSelect,
      persistPageSize,
      restoreDialogPreferences,
      persistDialogPreferences,
      loadServerTriagePreferences,
      bindViewStateEvents,
      applyFilters,
      applyQuickSearch,
      applyStatusFilter,
      setViewTab,
      visibleRows,
      ensureDialogRowPage,
      setActiveDialogRow,
      getShortcutTargetRow,
      openVisibleDialogByOffset,
      resolveListPagination,
      updatePaginationControls,
      renderPaginationPageNumbers,
      applyPageSize,
      updateZebraRows,
    };
  }

  window.DialogsListRuntime = {
    createRuntime,
    normalizeDialogView,
    normalizePageSize,
  };
})();

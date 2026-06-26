(function () {
  if (window.DialogsMyDialogsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function getMyDialogsState() {
      const state = options.getMyDialogsState?.();
      return state && typeof state === 'object'
        ? state
        : { unanswered: [], inWork: [] };
    }

    function setMyDialogsState(state) {
      options.setMyDialogsState?.(state && typeof state === 'object'
        ? state
        : { unanswered: [], inWork: [] });
    }

    function normalizeMyDialogsCollection(items) {
      return Array.isArray(items) ? items.filter((item) => item && typeof item === 'object') : [];
    }

    function resolveResponsibleRawFromItem(item) {
      return String(
        item?.rawResponsible
        || item?.raw_responsible
        || item?.responsibleRaw
        || item?.responsible
        || ''
      ).trim();
    }

    function resolveRowResponsibleRaw(row) {
      return String(row?.dataset?.responsibleRaw || row?.dataset?.responsible || '').trim();
    }

    function normalizeMyDialogsState(payload) {
      const source = payload && typeof payload === 'object' ? payload : {};
      setMyDialogsState({
        unanswered: normalizeMyDialogsCollection(source.unanswered),
        inWork: normalizeMyDialogsCollection(source.in_work || source.inWork),
      });
    }

    function isMyDialogItemUnanswered(dialog) {
      const unreadCount = Number(dialog?.unreadCount ?? dialog?.unread_count ?? 0) || 0;
      const statusKey = String(dialog?.statusKey || dialog?.status_key || '').trim().toLowerCase();
      return unreadCount > 0 || statusKey === 'waiting_operator';
    }

    function isMyDialogItemClosed(dialog) {
      const statusKey = String(dialog?.statusKey || dialog?.status_key || '').trim().toLowerCase();
      return statusKey === 'closed' || statusKey === 'auto_closed';
    }

    function buildMyDialogStateFromRow(row) {
      if (!row) return null;
      const responsibleRaw = resolveRowResponsibleRaw(row);
      if (!options.isOwnedByCurrentOperator?.(responsibleRaw) || options.isResolvedRow?.(row) === true) {
        return null;
      }
      return {
        ticketId: String(row.dataset.ticketId || '').trim(),
        requestNumber: String(row.dataset.requestNumber || '').trim(),
        clientName: String(row.dataset.client || '').trim(),
        channelName: String(row.dataset.channel || '').trim(),
        problem: String(row.dataset.problem || '').trim(),
        statusKey: String(row.dataset.statusKey || '').trim(),
        unreadCount: Number(row.dataset.unread) || 0,
        rawResponsible: responsibleRaw,
        responsible: String(row.dataset.responsible || '').trim(),
        lastMessageSender: String(row.dataset.lastMessageSender || '').trim(),
        lastMessageTimestamp: String(row.dataset.lastMessageTimestamp || '').trim(),
        userId: String(row.dataset.userId || '').trim(),
      };
    }

    function syncMyDialogsStateFromTable() {
      const nextState = {
        unanswered: [],
        inWork: [],
      };
      (options.rowsList?.() || []).forEach((row) => {
        const dialog = buildMyDialogStateFromRow(row);
        if (!dialog || !dialog.ticketId || isMyDialogItemClosed(dialog)) {
          return;
        }
        if (isMyDialogItemUnanswered(dialog)) {
          nextState.unanswered.push(dialog);
        } else {
          nextState.inWork.push(dialog);
        }
      });
      setMyDialogsState(nextState);
    }

    function formatMyDialogLastActivity(dialog) {
      const sender = String(dialog?.lastMessageSender || '').trim();
      const timestamp = options.formatTimestamp?.(dialog?.lastMessageTimestamp || '', { includeTime: true, fallback: '' }) || '';
      if (sender && timestamp && timestamp !== '—') {
        return `${sender} · ${timestamp}`;
      }
      if (timestamp && timestamp !== '—') {
        return timestamp;
      }
      if (sender) {
        return sender;
      }
      return 'Активность пока не зафиксирована';
    }

    function renderMyDialogItem(dialog) {
      const ticketId = String(dialog?.ticketId || '').trim();
      if (!ticketId) return '';
      const requestNumber = String(dialog?.requestNumber || '').trim();
      const title = requestNumber || ticketId;
      const clientName = String(dialog?.clientName || dialog?.username || 'Клиент').trim();
      const channelName = String(dialog?.channelName || dialog?.channel || 'Без канала').trim();
      const unreadCount = Number(dialog?.unreadCount ?? dialog?.unread_count ?? 0) || 0;
      const isActive = String(options.getActiveDialogTicketId?.() || '').trim() === ticketId;
      const lastActivity = formatMyDialogLastActivity(dialog);
      const problem = String(dialog?.problem || '').trim();
      const metaParts = [clientName, channelName].filter(Boolean);
      return `
      <button type="button"
              class="dialog-my-dialog-item ${isActive ? 'is-active' : ''}"
              data-my-dialog-ticket-id="${escapeHtml(ticketId)}">
        <div class="dialog-my-dialog-item-head">
          <div class="dialog-my-dialog-item-title">№ ${escapeHtml(title)}</div>
          <span class="badge dialog-unread-count ${unreadCount > 0 ? '' : 'd-none'}">${unreadCount}</span>
        </div>
        <div class="dialog-my-dialog-item-meta">${metaParts.map((part) => `<span>${escapeHtml(part)}</span>`).join('')}</div>
        <div class="dialog-my-dialog-item-last">${escapeHtml(problem || lastActivity)}</div>
        ${problem ? `<div class="dialog-my-dialog-item-last">${escapeHtml(lastActivity)}</div>` : ''}
      </button>
    `;
    }

    function renderMyDialogsPanel() {
      if (!elements.panel || !elements.unansweredList || !elements.inWorkList) return;
      const state = getMyDialogsState();
      const unanswered = normalizeMyDialogsCollection(state.unanswered);
      const inWork = normalizeMyDialogsCollection(state.inWork);
      const totalActive = unanswered.length + inWork.length;
      elements.unansweredList.innerHTML = unanswered.map(renderMyDialogItem).join('');
      elements.inWorkList.innerHTML = inWork.map(renderMyDialogItem).join('');
      if (elements.count) {
        elements.count.textContent = `(${totalActive})`;
      }
      if (elements.unansweredSection) {
        elements.unansweredSection.classList.toggle('d-none', unanswered.length === 0);
      }
      if (elements.inWorkSection) {
        elements.inWorkSection.classList.toggle('d-none', inWork.length === 0);
      }
      if (elements.empty) {
        elements.empty.classList.toggle('d-none', unanswered.length > 0 || inWork.length > 0);
      }
    }

    function bindPanelEvents() {
      if (!elements.panel || elements.panel.dataset.myDialogsBound === 'true') return;
      elements.panel.dataset.myDialogsBound = 'true';
      elements.panel.addEventListener('click', (event) => {
        const trigger = event.target instanceof Element
          ? event.target.closest('[data-my-dialog-ticket-id]')
          : null;
        if (!trigger) return;
        event.preventDefault();
        const ticketId = String(trigger.getAttribute('data-my-dialog-ticket-id') || '').trim();
        if (!ticketId) return;
        const row = typeof options.findRowByTicketId === 'function'
          ? options.findRowByTicketId(ticketId)
          : null;
        options.setActiveDialogRow?.(row, { ensureVisible: true });
        options.openDialogSurface?.(ticketId, row, { source: 'manual_open' });
      });
    }

    return {
      normalizeMyDialogsCollection,
      resolveResponsibleRawFromItem,
      resolveRowResponsibleRaw,
      normalizeMyDialogsState,
      isMyDialogItemUnanswered,
      isMyDialogItemClosed,
      buildMyDialogStateFromRow,
      syncMyDialogsStateFromTable,
      formatMyDialogLastActivity,
      renderMyDialogItem,
      renderMyDialogsPanel,
      bindPanelEvents,
    };
  }

  window.DialogsMyDialogsRuntime = {
    createRuntime,
  };
})();

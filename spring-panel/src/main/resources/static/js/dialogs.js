// panel/static/dialogs.js
(function () {
  const table = document.getElementById('dialogsTable');
  if (!table) return;

  const quickSearch = document.getElementById('dialogQuickSearch');
  const filtersBtn = document.getElementById('dialogFiltersBtn');
  const columnsBtn = document.getElementById('dialogColumnsBtn');
  const filtersModalEl = document.getElementById('dialogFiltersModal');
  const columnsModalEl = document.getElementById('dialogColumnsModal');
  const detailsModalEl = document.getElementById('dialogDetailsModal');

  const filtersForm = document.getElementById('dialogFiltersForm');
  const filtersApply = document.getElementById('dialogFiltersApply');
  const filtersReset = document.getElementById('dialogFiltersReset');
  const columnsList = document.getElementById('dialogColumnsList');
  const columnsApply = document.getElementById('dialogColumnsApply');
  const columnsReset = document.getElementById('dialogColumnsReset');
  const viewTabs = document.querySelectorAll('[data-dialog-view]');

  const detailsMeta = document.getElementById('dialogDetailsMeta');
  const detailsSummary = document.getElementById('dialogDetailsSummary');
  const detailsHistory = document.getElementById('dialogDetailsHistory');
  const detailsCreateTask = document.getElementById('dialogDetailsCreateTask');

  const filtersModal = (typeof bootstrap !== 'undefined' && filtersModalEl)
    ? new bootstrap.Modal(filtersModalEl)
    : null;
  const columnsModal = (typeof bootstrap !== 'undefined' && columnsModalEl)
    ? new bootstrap.Modal(columnsModalEl)
    : null;
  const detailsModal = (typeof bootstrap !== 'undefined' && detailsModalEl)
    ? new bootstrap.Modal(detailsModalEl)
    : null;

  const STORAGE_COLUMNS = 'bender:dialogs:columns';
  const STORAGE_WIDTHS = 'bender:dialogs:column-widths';
  const STORAGE_TASK = 'bender:dialogs:create-task';

  const headerRow = table.tHead ? table.tHead.rows[0] : null;
  const headerCells = headerRow ? Array.from(headerRow.cells) : [];

  const columnMeta = headerCells
    .map((cell) => ({
      key: cell.dataset.columnKey || '',
      label: (cell.textContent || '').trim(),
    }))
    .filter((item) => item.key);

  const defaultColumnState = columnMeta.reduce((acc, item) => {
    acc[item.key] = true;
    return acc;
  }, {});

  let columnState = { ...defaultColumnState };

  function loadColumnState() {
    try {
      const raw = localStorage.getItem(STORAGE_COLUMNS);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') return;
      columnState = { ...defaultColumnState };
      Object.keys(defaultColumnState).forEach((key) => {
        if (Object.prototype.hasOwnProperty.call(parsed, key)) {
          columnState[key] = Boolean(parsed[key]);
        }
      });
    } catch (error) {
      columnState = { ...defaultColumnState };
    }
  }

  function persistColumnState() {
    localStorage.setItem(STORAGE_COLUMNS, JSON.stringify(columnState));
  }

  function applyColumnState() {
    columnMeta.forEach(({ key }) => {
      const visible = columnState[key] !== false;
      const selector = `[data-column-key="${key}"]`;
      table.querySelectorAll(selector).forEach((cell) => {
        cell.classList.toggle('d-none', !visible);
      });
    });
  }

  function buildColumnsList() {
    if (!columnsList) return;
    columnsList.innerHTML = '';
    columnMeta.forEach(({ key, label }) => {
      const col = document.createElement('div');
      col.className = 'col-12 col-sm-6';
      col.innerHTML = `
        <label class="dialog-column-option">
          <input type="checkbox" class="form-check-input" data-column-toggle="${key}">
          <span>${label}</span>
        </label>
      `;
      columnsList.appendChild(col);
    });
    syncColumnsList();
  }

  function syncColumnsList() {
    if (!columnsList) return;
    columnsList.querySelectorAll('[data-column-toggle]').forEach((checkbox) => {
      const key = checkbox.dataset.columnToggle;
      checkbox.checked = columnState[key] !== false;
    });
  }

  function setTaskDraft(payload) {
    if (!payload || !payload.ticketId) return;
    localStorage.setItem(STORAGE_TASK, JSON.stringify(payload));
  }

  function collectRowSearchText(row) {
    const parts = [
      row.dataset.ticketId,
      row.dataset.client,
      row.dataset.clientStatus,
      row.dataset.channel,
      row.dataset.business,
      row.dataset.problem,
      row.dataset.status,
      row.dataset.location,
      row.dataset.responsible,
    ];
    return parts.filter(Boolean).join(' ').toLowerCase();
  }

  function rowsList() {
    return Array.from(table.tBodies[0].rows)
      .filter((row) => row.dataset && row.dataset.ticketId);
  }

  const emptyRow = document.createElement('tr');
  emptyRow.innerHTML = '<td colspan="12" class="text-center text-muted py-4">Нет результатов</td>';
  emptyRow.classList.add('d-none');

  function ensureEmptyRow() {
    if (!table.tBodies[0].contains(emptyRow)) {
      table.tBodies[0].appendChild(emptyRow);
    }
  }

  const filterState = { search: '', status: '', view: 'all' };

  function isResolved(row) {
    const raw = (row.dataset.statusRaw || '').toLowerCase();
    const label = (row.dataset.status || '').toLowerCase();
    return raw === 'resolved' || label === 'закрыт';
  }

  function applyFilters() {
    const search = (filterState.search || '').trim().toLowerCase();
    const status = (filterState.status || '').trim().toLowerCase();
    let visibleCount = 0;
    rowsList().forEach((row) => {
      const text = collectRowSearchText(row);
      const statusValue = (row.dataset.status || '').toLowerCase();
      const matchesSearch = !search || text.includes(search);
      const matchesStatus = !status || statusValue === status;
      const matchesView = filterState.view === 'active' ? !isResolved(row) : true;
      const visible = matchesSearch && matchesStatus && matchesView;
      row.classList.toggle('d-none', !visible);
      if (visible) visibleCount += 1;
    });
    ensureEmptyRow();
    emptyRow.classList.toggle('d-none', visibleCount !== 0);
  }

  function applyQuickSearch(value) {
    filterState.search = value || '';
    applyFilters();
  }

  function setViewTab(nextView) {
    filterState.view = nextView || 'all';
    viewTabs.forEach((tab) => {
      tab.classList.toggle('active', tab.dataset.dialogView === filterState.view);
    });
    applyFilters();
  }

  function restoreColumnWidths() {
    try {
      const raw = localStorage.getItem(STORAGE_WIDTHS);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') return;
      Object.entries(parsed).forEach(([key, width]) => {
        if (!width) return;
        const header = table.querySelector(`th[data-column-key="${key}"]`);
        if (!header) return;
        header.style.width = width;
        const index = header.cellIndex;
        rowsList().forEach((row) => {
          const cell = row.children[index];
          if (cell) cell.style.width = width;
        });
      });
    } catch (error) {
      // ignore restore errors
    }
  }

  function saveColumnWidths() {
    const widths = {};
    headerCells.forEach((cell) => {
      const key = cell.dataset.columnKey;
      if (!key || !cell.style.width) return;
      widths[key] = cell.style.width;
    });
    localStorage.setItem(STORAGE_WIDTHS, JSON.stringify(widths));
  }

  function initColumnResize() {
    headerCells.forEach((header) => {
      if (header.dataset.resizable !== 'true') return;
      const oldHandle = header.querySelector('.resize-handle');
      if (oldHandle) oldHandle.remove();
      const handle = document.createElement('div');
      handle.className = 'resize-handle';
      header.appendChild(handle);

      handle.addEventListener('mousedown', (event) => {
        const computed = getComputedStyle(header).width;
        header.style.width = computed;
        const index = header.cellIndex;
        rowsList().forEach((row) => {
          const cell = row.children[index];
          if (cell) cell.style.width = computed;
        });

        const startX = event.pageX;
        const startWidth = parseFloat(computed);
        document.documentElement.classList.add('resizing');

        function onMouseMove(moveEvent) {
          const next = startWidth + (moveEvent.pageX - startX);
          if (next < 80) return;
          header.style.width = `${next}px`;
          rowsList().forEach((row) => {
            const cell = row.children[index];
            if (cell) cell.style.width = `${next}px`;
          });
        }

        function onMouseUp() {
          document.removeEventListener('mousemove', onMouseMove);
          document.removeEventListener('mouseup', onMouseUp);
          document.documentElement.classList.remove('resizing');
          saveColumnWidths();
        }

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
        event.preventDefault();
      });
    });
  }

  function normalizeMessageSender(sender) {
    const value = String(sender || '').toLowerCase();
    if (value.includes('support') || value.includes('operator') || value.includes('admin')) {
      return 'support';
    }
    return 'user';
  }

  function formatTimestamp(value) {
    if (!value) return '';
    const parsed = new Date(value);
    if (!Number.isNaN(parsed.getTime())) {
      return parsed.toLocaleString();
    }
    return value;
  }

  function renderHistory(messages) {
    if (!detailsHistory) return;
    if (!Array.isArray(messages) || messages.length === 0) {
      detailsHistory.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
      return;
    }
    detailsHistory.innerHTML = messages.map((msg) => {
      const senderType = normalizeMessageSender(msg.sender);
      const timestamp = formatTimestamp(msg.timestamp);
      const attachment = msg.attachment
        ? `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`
        : '';
      const body = msg.message ? msg.message.replace(/\n/g, '<br>') : '—';
      return `
        <div class="chat-message ${senderType}">
          <div class="d-flex justify-content-between small text-muted mb-1">
            <span>${msg.sender || 'Пользователь'}</span>
            <span>${timestamp}</span>
          </div>
          <div>${body}</div>
          ${attachment}
        </div>
      `;
    }).join('');
  }

  async function openDialogDetails(ticketId, fallbackRow) {
    if (!ticketId || !detailsModal) return;
    if (detailsMeta) detailsMeta.textContent = `ID диалога: ${ticketId}`;
    if (detailsSummary) detailsSummary.innerHTML = '<div>Загрузка...</div>';
    if (detailsHistory) detailsHistory.innerHTML = '';

    try {
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}`, { credentials: 'same-origin' });
      const data = await resp.json();
      if (!resp.ok) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      const summary = data.summary || {};
      const summaryItems = [
        ['Клиент', summary.clientName || summary.username || fallbackRow?.dataset.client || '—'],
        ['Статус', summary.status ? summary.statusLabel || summary.status : fallbackRow?.dataset.status || '—'],
        ['Проблема', summary.problem || fallbackRow?.dataset.problem || '—'],
        ['Локация', summary.locationName || summary.city || fallbackRow?.dataset.location || '—'],
        ['Ответственный', summary.resolvedBy || fallbackRow?.dataset.responsible || '—'],
      ];
      if (detailsSummary) {
        detailsSummary.innerHTML = summaryItems.map(([label, value]) => `
          <div class="d-flex justify-content-between gap-2">
            <span>${label}</span>
            <span class="text-dark">${value || '—'}</span>
          </div>
        `).join('');
      }
      renderHistory(data.history || []);
    } catch (error) {
      if (detailsSummary) {
        detailsSummary.innerHTML = `<div class="text-danger">Не удалось загрузить детали: ${error.message}</div>`;
      }
    }

    detailsModal.show();
  }

  table.addEventListener('click', (event) => {
    const openBtn = event.target.closest('.dialog-open-btn');
    if (openBtn) {
      event.preventDefault();
      const ticketId = openBtn.dataset.ticketId;
      const row = openBtn.closest('tr');
      openDialogDetails(ticketId, row);
      return;
    }
    const taskBtn = event.target.closest('.dialog-task-btn');
    if (taskBtn) {
      setTaskDraft({
        ticketId: taskBtn.dataset.ticketId,
        client: taskBtn.dataset.client,
      });
    }
  });

  if (detailsCreateTask) {
    detailsCreateTask.addEventListener('click', () => {
      const meta = (detailsMeta?.textContent || '').match(/ID диалога:\s*(.+)$/);
      if (meta && meta[1]) {
        setTaskDraft({ ticketId: meta[1].trim() });
      }
    });
  }

  if (quickSearch) {
    quickSearch.addEventListener('input', () => applyQuickSearch(quickSearch.value));
  }

  if (filtersBtn && filtersModal) {
    filtersBtn.addEventListener('click', () => {
      if (filtersForm) {
        filtersForm.search.value = filterState.search || '';
        filtersForm.status.value = filterState.status || '';
      }
      filtersModal.show();
    });
  }

  if (filtersApply) {
    filtersApply.addEventListener('click', () => {
      if (filtersForm) {
        filterState.search = filtersForm.search.value;
        filterState.status = filtersForm.status.value;
        if (quickSearch) quickSearch.value = filterState.search || '';
      }
      applyFilters();
      if (filtersModal) filtersModal.hide();
    });
  }

  if (filtersReset) {
    filtersReset.addEventListener('click', () => {
      filterState.search = '';
      filterState.status = '';
      if (filtersForm) filtersForm.reset();
      if (quickSearch) quickSearch.value = '';
      applyFilters();
    });
  }

  if (viewTabs.length) {
    viewTabs.forEach((tab) => {
      tab.addEventListener('click', () => {
        setViewTab(tab.dataset.dialogView || 'all');
      });
    });
  }

  if (columnsBtn && columnsModal) {
    columnsBtn.addEventListener('click', () => {
      syncColumnsList();
      columnsModal.show();
    });
  }

  if (columnsApply) {
    columnsApply.addEventListener('click', () => {
      if (columnsList) {
        columnsList.querySelectorAll('[data-column-toggle]').forEach((checkbox) => {
          const key = checkbox.dataset.columnToggle;
          columnState[key] = checkbox.checked;
        });
        persistColumnState();
        applyColumnState();
      }
      if (columnsModal) columnsModal.hide();
    });
  }

  if (columnsReset) {
    columnsReset.addEventListener('click', () => {
      columnState = { ...defaultColumnState };
      persistColumnState();
      applyColumnState();
      syncColumnsList();
    });
  }

  loadColumnState();
  buildColumnsList();
  applyColumnState();
  if (viewTabs.length) {
    const activeTab = Array.from(viewTabs).find((tab) => tab.classList.contains('active'));
    setViewTab(activeTab?.dataset.dialogView || 'all');
  } else {
    applyFilters();
  }
  initColumnResize();
  restoreColumnWidths();
})();

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
  const detailsAvatar = document.getElementById('dialogDetailsAvatar');
  const detailsClientName = document.getElementById('dialogDetailsClientName');
  const detailsClientStatus = document.getElementById('dialogDetailsClientStatus');
  const detailsSummary = document.getElementById('dialogDetailsSummary');
  const detailsHistory = document.getElementById('dialogDetailsHistory');
  const detailsCreateTask = document.getElementById('dialogDetailsCreateTask');
  const detailsResolve = document.getElementById('dialogDetailsResolve');
  const detailsReopen = document.getElementById('dialogDetailsReopen');
  const detailsProblem = document.getElementById('dialogDetailsProblem');
  const detailsMetrics = document.getElementById('dialogDetailsMetrics');
  const detailsSidebar = document.getElementById('dialogDetailsSidebar');
  const detailsResizeHandle = document.getElementById('dialogDetailsResizeHandle');
  const detailsReplyText = document.getElementById('dialogReplyText');
  const detailsReplySend = document.getElementById('dialogReplySend');
  const categoryTemplatesSection = document.getElementById('dialogCategoryTemplatesSection');
  const categoryTemplateSelect = document.getElementById('dialogCategoryTemplateSelect');
  const categoryTemplateList = document.getElementById('dialogCategoryTemplateList');
  const categoryTemplateEmpty = document.getElementById('dialogCategoryTemplateEmpty');
  const questionTemplatesSection = document.getElementById('dialogQuestionTemplatesSection');
  const questionTemplateSelect = document.getElementById('dialogQuestionTemplateSelect');
  const questionTemplateList = document.getElementById('dialogQuestionTemplateList');
  const questionTemplateEmpty = document.getElementById('dialogQuestionTemplateEmpty');
  const completionTemplatesSection = document.getElementById('dialogCompletionTemplatesSection');
  const completionTemplateSelect = document.getElementById('dialogCompletionTemplateSelect');
  const completionTemplateList = document.getElementById('dialogCompletionTemplateList');
  const completionTemplateEmpty = document.getElementById('dialogCompletionTemplateEmpty');

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
  const HISTORY_POLL_INTERVAL = 8000;

  const DEFAULT_DIALOG_TIME_METRICS = Object.freeze({
    good_limit: 30,
    warning_limit: 60,
    colors: Object.freeze({
      good: '#d1f7d1',
      warning: '#fff4d6',
      danger: '#f8d7da',
    }),
  });

  const DIALOG_TEMPLATES = {
    categoryTemplates: Array.isArray(window.DIALOG_CONFIG?.category_templates)
      ? window.DIALOG_CONFIG.category_templates
      : [],
    questionTemplates: Array.isArray(window.DIALOG_CONFIG?.question_templates)
      ? window.DIALOG_CONFIG.question_templates
      : [],
    completionTemplates: Array.isArray(window.DIALOG_CONFIG?.completion_templates)
      ? window.DIALOG_CONFIG.completion_templates
      : [],
  };

  let activeDialogTicketId = null;
  let activeDialogChannelId = null;
  let activeDialogRow = null;
  let historyPollTimer = null;
  let lastHistoryMarker = null;
  let historyLoading = false;

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

  function sanitizeHexColor(value, fallback) {
    const raw = typeof value === 'string' ? value.trim() : '';
    if (/^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(raw)) {
      return raw;
    }
    return fallback;
  }

  function normalizeDialogTimeMetrics(raw) {
    const base = {
      good_limit: DEFAULT_DIALOG_TIME_METRICS.good_limit,
      warning_limit: DEFAULT_DIALOG_TIME_METRICS.warning_limit,
      colors: {
        ...DEFAULT_DIALOG_TIME_METRICS.colors,
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
        base.colors.good = sanitizeHexColor(raw.colors.good, base.colors.good);
        base.colors.warning = sanitizeHexColor(raw.colors.warning, base.colors.warning);
        base.colors.danger = sanitizeHexColor(raw.colors.danger, base.colors.danger);
      }
    }

    if (!Number.isFinite(base.good_limit) || base.good_limit <= 0) {
      base.good_limit = DEFAULT_DIALOG_TIME_METRICS.good_limit;
    }

    if (!Number.isFinite(base.warning_limit) || base.warning_limit <= base.good_limit) {
      base.warning_limit = Math.max(base.good_limit + 1, DEFAULT_DIALOG_TIME_METRICS.warning_limit);
    }

    return base;
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
      if (!raw) continue;
      const text = String(raw).trim();
      if (!text) continue;
      if (/^\d{10,13}$/.test(text)) {
        const value = text.length === 10 ? Number(text) * 1000 : Number(text);
        if (Number.isFinite(value)) return value;
      }
      const parsed = Date.parse(text.replace(' ', 'T'));
      if (Number.isFinite(parsed)) return parsed;
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
    if (!Number.isFinite(totalMinutes) || !config) return null;
    if (totalMinutes <= config.good_limit) return config.colors.good;
    if (totalMinutes <= config.warning_limit) return config.colors.warning;
    return config.colors.danger;
  }

  function buildTemplateOptions(selectEl, templates, labelPrefix) {
    if (!selectEl) return;
    selectEl.innerHTML = '';
    templates.forEach((template, index) => {
      const option = document.createElement('option');
      option.value = template?.id || String(index);
      option.textContent = template?.name || `${labelPrefix} ${index + 1}`;
      selectEl.appendChild(option);
    });
  }

  function findTemplateByValue(templates, value) {
    return templates.find((template, index) => template?.id === value || String(index) === value);
  }

  function renderCategoryTemplate(template) {
    if (!categoryTemplateList || !categoryTemplateEmpty) return;
    const categories = Array.isArray(template?.categories) ? template.categories.filter(Boolean) : [];
    categoryTemplateList.innerHTML = '';
    categories.forEach((category) => {
      const badge = document.createElement('span');
      badge.className = 'badge rounded-pill text-bg-light border';
      badge.textContent = category;
      categoryTemplateList.appendChild(badge);
    });
    const hasItems = categories.length > 0;
    categoryTemplateList.classList.toggle('d-none', !hasItems);
    categoryTemplateEmpty.classList.toggle('d-none', hasItems);
  }

  function renderQuestionTemplate(template) {
    if (!questionTemplateList || !questionTemplateEmpty) return;
    const questions = Array.isArray(template?.questions) ? template.questions.filter(Boolean) : [];
    questionTemplateList.innerHTML = '';
    questions.forEach((question) => {
      const button = document.createElement('button');
      button.className = 'btn btn-outline-secondary btn-sm text-start';
      button.type = 'button';
      button.dataset.questionTemplateItem = '';
      button.dataset.questionValue = question;
      button.textContent = question;
      questionTemplateList.appendChild(button);
    });
    const hasItems = questions.length > 0;
    questionTemplateList.classList.toggle('d-none', !hasItems);
    questionTemplateEmpty.classList.toggle('d-none', hasItems);
  }

  function renderCompletionTemplate(template) {
    if (!completionTemplateList || !completionTemplateEmpty) return;
    const items = Array.isArray(template?.items) ? template.items.filter(Boolean) : [];
    completionTemplateList.innerHTML = '';
    items.forEach((item) => {
      const wrapper = document.createElement('div');
      wrapper.className = 'border rounded p-2 bg-light';
      const question = document.createElement('div');
      question.className = 'fw-semibold';
      question.textContent = item?.question || 'Контрольный вопрос';
      const action = document.createElement('div');
      action.className = 'text-muted';
      action.textContent = item?.action || 'Действие';
      wrapper.appendChild(question);
      wrapper.appendChild(action);
      completionTemplateList.appendChild(wrapper);
    });
    const hasItems = items.length > 0;
    completionTemplateList.classList.toggle('d-none', !hasItems);
    completionTemplateEmpty.classList.toggle('d-none', hasItems);
  }

  function initDialogTemplates() {
    if (categoryTemplatesSection) {
      const templates = DIALOG_TEMPLATES.categoryTemplates;
      const hasTemplates = templates.length > 0;
      categoryTemplatesSection.classList.toggle('d-none', !hasTemplates);
      if (hasTemplates && categoryTemplateSelect) {
        buildTemplateOptions(categoryTemplateSelect, templates, 'Шаблон категорий');
        renderCategoryTemplate(templates[0]);
        categoryTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(templates, categoryTemplateSelect.value);
          renderCategoryTemplate(selected);
        });
      }
    }

    if (questionTemplatesSection) {
      const templates = DIALOG_TEMPLATES.questionTemplates;
      const hasTemplates = templates.length > 0;
      questionTemplatesSection.classList.toggle('d-none', !hasTemplates);
      if (hasTemplates && questionTemplateSelect) {
        buildTemplateOptions(questionTemplateSelect, templates, 'Шаблон вопросов');
        renderQuestionTemplate(templates[0]);
        questionTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(templates, questionTemplateSelect.value);
          renderQuestionTemplate(selected);
        });
      }
    }

    if (completionTemplatesSection) {
      const templates = DIALOG_TEMPLATES.completionTemplates;
      const hasTemplates = templates.length > 0;
      completionTemplatesSection.classList.toggle('d-none', !hasTemplates);
      if (hasTemplates && completionTemplateSelect) {
        buildTemplateOptions(completionTemplateSelect, templates, 'Шаблон действий');
        renderCompletionTemplate(templates[0]);
        completionTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(templates, completionTemplateSelect.value);
          renderCompletionTemplate(selected);
        });
      }
    }
  }

  function insertReplyText(value) {
    if (!detailsReplyText || !value) return;
    const existing = detailsReplyText.value.trim();
    detailsReplyText.value = existing ? `${existing}\n${value}` : value;
    detailsReplyText.focus();
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

  function initDetailsResize() {
    if (!detailsSidebar || !detailsResizeHandle) return;
    let startX = 0;
    let startWidth = 0;

    function onMouseMove(event) {
      const delta = event.clientX - startX;
      const nextWidth = Math.min(480, Math.max(220, startWidth + delta));
      detailsSidebar.style.flexBasis = `${nextWidth}px`;
    }

    function onMouseUp() {
      document.removeEventListener('mousemove', onMouseMove);
      document.removeEventListener('mouseup', onMouseUp);
      document.documentElement.classList.remove('resizing');
    }

    detailsResizeHandle.addEventListener('mousedown', (event) => {
      startX = event.clientX;
      startWidth = detailsSidebar.getBoundingClientRect().width;
      document.documentElement.classList.add('resizing');
      document.addEventListener('mousemove', onMouseMove);
      document.addEventListener('mouseup', onMouseUp);
      event.preventDefault();
    });
  }

  function normalizeMessageSender(sender) {
    const value = String(sender || '').toLowerCase();
    if (value.includes('support') || value.includes('operator') || value.includes('admin') || value.includes('system')) {
      return 'support';
    }
    return 'user';
  }

  function formatTimestamp(value, options = {}) {
    if (!value) return '';
    const rawValue = typeof value === 'string' ? value.trim() : value;
    let normalized = rawValue;
    if (typeof rawValue === 'string' && /^\d+(\.\d+)?$/.test(rawValue)) {
      normalized = Number(rawValue);
    }
    if (typeof normalized === 'number' && normalized < 1000000000000) {
      normalized *= 1000;
    }
    const parsed = new Date(normalized);
    if (!Number.isNaN(parsed.getTime())) {
      const day = String(parsed.getDate()).padStart(2, '0');
      const month = String(parsed.getMonth() + 1).padStart(2, '0');
      const year = parsed.getFullYear();
      const base = `${day}.${month}.${year}`;
      if (options.includeTime) {
        const hours = String(parsed.getHours()).padStart(2, '0');
        const minutes = String(parsed.getMinutes()).padStart(2, '0');
        return `${base} ${hours}:${minutes}`;
      }
      return base;
    }
    return value;
  }

  function renderHistory(messages) {
    if (!detailsHistory) return;
    if (!Array.isArray(messages) || messages.length === 0) {
      detailsHistory.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
      lastHistoryMarker = 'empty';
      return;
    }
    lastHistoryMarker = historyMarker(messages);
    detailsHistory.innerHTML = messages.map((msg) => {
      const senderType = normalizeMessageSender(msg.sender);
      const timestamp = formatTimestamp(msg.timestamp);
      const replyPreview = msg.replyPreview
        ? `<div class="small text-muted border-start ps-2 mb-1">${msg.replyPreview}</div>`
        : '';
      const attachment = msg.attachment
        ? `<div class="small"><a href="${msg.attachment}" target="_blank" rel="noopener">Вложение</a></div>`
        : '';
      const bodyText = msg.message ? msg.message.replace(/\n/g, '<br>') : '';
      const fallbackType = msg.messageType && !bodyText ? `[${msg.messageType}]` : '';
      const body = bodyText || fallbackType || '—';
      return `
        <div class="chat-message ${senderType}">
          <div class="d-flex justify-content-between small text-muted mb-1">
            <span>${msg.sender || 'Пользователь'}</span>
            <span>${timestamp}</span>
          </div>
          ${replyPreview}
          <div>${body}</div>
          ${attachment}
        </div>
      `;
    }).join('');
  }

  function appendOperatorMessage(message, timestamp) {
    if (!detailsHistory) return;
    const wrapper = document.createElement('div');
    wrapper.className = 'chat-message support';
    wrapper.innerHTML = `
      <div class="d-flex justify-content-between small text-muted mb-1">
        <span>Оператор</span>
        <span>${formatTimestamp(timestamp)}</span>
      </div>
      <div>${message.replace(/\n/g, '<br>')}</div>
    `;
    detailsHistory.appendChild(wrapper);
    detailsHistory.scrollTop = detailsHistory.scrollHeight;
    lastHistoryMarker = `local:${Date.now()}`;
  }

  function historyMarker(messages) {
    if (!Array.isArray(messages) || messages.length === 0) return 'empty';
    const last = messages[messages.length - 1] || {};
    return [
      messages.length,
      last.telegramMessageId || '',
      last.timestamp || '',
      last.sender || '',
      last.message || '',
    ].join('|');
  }

  function withChannelParam(path, channelId) {
    if (!channelId) return path;
    const separator = path.includes('?') ? '&' : '?';
    return `${path}${separator}channelId=${encodeURIComponent(channelId)}`;
  }

  async function refreshHistory() {
    if (!activeDialogTicketId || historyLoading) return;
    historyLoading = true;
    try {
      const url = withChannelParam(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/history`, activeDialogChannelId);
      const resp = await fetch(url, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const data = await resp.json();
      if (!resp.ok || !data?.success) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      const messages = data.messages || [];
      const marker = historyMarker(messages);
      if (marker !== lastHistoryMarker) {
        renderHistory(messages);
      }
    } catch (error) {
      // ignore polling errors
    } finally {
      historyLoading = false;
    }
  }

  function startHistoryPolling() {
    if (historyPollTimer) return;
    historyPollTimer = setInterval(refreshHistory, HISTORY_POLL_INTERVAL);
  }

  function stopHistoryPolling() {
    if (historyPollTimer) {
      clearInterval(historyPollTimer);
      historyPollTimer = null;
    }
  }

  function updateResolveButton(statusRaw) {
    if (!detailsResolve) return;
    const resolved = String(statusRaw || '').toLowerCase() === 'resolved';
    detailsResolve.disabled = resolved;
    detailsResolve.textContent = resolved ? 'Обращение закрыто' : 'Закрыть обращение';
    if (detailsReopen) {
      detailsReopen.disabled = !resolved;
      detailsReopen.classList.toggle('d-none', !resolved);
    }
  }

  function updateRowStatus(row, statusRaw, statusLabel) {
    if (!row) return;
    row.dataset.status = statusLabel;
    row.dataset.statusRaw = statusRaw;
    const badge = row.querySelector('.badge');
    if (badge) {
      badge.textContent = statusLabel;
      badge.classList.remove('bg-primary-subtle', 'text-primary', 'bg-warning-subtle', 'text-warning', 'bg-success-subtle', 'text-success', 'bg-secondary-subtle', 'text-secondary');
      if (statusRaw === 'resolved') {
        badge.classList.add('bg-success-subtle', 'text-success');
      } else if (statusRaw === 'pending') {
        badge.classList.add('bg-warning-subtle', 'text-warning');
      } else {
        badge.classList.add('bg-primary-subtle', 'text-primary');
      }
    }
  }

  function formatStatusLabel(raw, fallback) {
    if (fallback) return fallback;
    const normalized = String(raw || '').toLowerCase();
    if (normalized === 'resolved') return 'Закрыт';
    if (normalized === 'pending') return 'В ожидании';
    if (normalized) return 'Открыт';
    return '—';
  }

  async function openDialogDetails(ticketId, fallbackRow) {
    if (!ticketId || !detailsModal) return;
    activeDialogTicketId = ticketId;
    activeDialogRow = fallbackRow || null;
    activeDialogChannelId = fallbackRow?.dataset?.channelId || null;
    if (detailsMeta) detailsMeta.textContent = `ID диалога: ${ticketId}`;
    if (detailsSummary) detailsSummary.innerHTML = '<div>Загрузка...</div>';
    if (detailsHistory) detailsHistory.innerHTML = '';
    if (detailsReplyText) detailsReplyText.value = '';

    try {
      const url = withChannelParam(`/api/dialogs/${encodeURIComponent(ticketId)}`, activeDialogChannelId);
      const resp = await fetch(url, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const data = await resp.json();
      if (!resp.ok) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      const summary = data.summary || {};
      if (summary.channelId) {
        activeDialogChannelId = summary.channelId;
      }

      const resolvedBy = summary.resolvedBy || summary.resolved_by;
      const resolvedAt = summary.resolvedAt || summary.resolved_at;
      const createdDate = summary.createdDate || summary.created_date;
      const createdTime = summary.createdTime || summary.created_time;
      const createdAt = summary.createdAt || summary.created_at;
      const createdLabel = [createdDate, createdTime].filter(Boolean).join(' ')
        || createdAt
        || '—';
      const createdDisplay = formatTimestamp(createdLabel);
      const resolvedDisplay = formatTimestamp(resolvedAt || '', { includeTime: true });
      const responsibleLabel = summary.responsible
        || resolvedBy
        || fallbackRow?.dataset.responsible
        || '—';
      const clientName = summary.clientName
        || summary.username
        || fallbackRow?.dataset.client
        || '—';
      const clientStatus = summary.clientStatus || fallbackRow?.dataset.clientStatus || '—';
      const channelLabel = summary.channelName || fallbackRow?.dataset.channel || '—';
      const businessLabel = summary.business || fallbackRow?.dataset.business || '—';
      const statusRaw = summary.status || fallbackRow?.dataset.statusRaw || '';
      const statusLabel = formatStatusLabel(statusRaw, fallbackRow?.dataset.status);
      const locationLabel = summary.locationName || summary.city || fallbackRow?.dataset.location || '—';
      const problemLabel = summary.problem || fallbackRow?.dataset.problem || '—';
      if (detailsAvatar) {
        const initial = clientName && clientName !== '—' ? clientName.trim().charAt(0).toUpperCase() : '—';
        detailsAvatar.textContent = initial || '—';
      }
      if (detailsClientName) detailsClientName.textContent = clientName;
      if (detailsClientStatus) detailsClientStatus.textContent = clientStatus;
      if (detailsProblem) detailsProblem.textContent = problemLabel;
      const summaryItems = [
        ['Клиент', summary.clientName || summary.username || fallbackRow?.dataset.client || '—'],
        ['Статус клиента', summary.clientStatus || fallbackRow?.dataset.clientStatus || '—'],
        ['Статус', statusLabel || '—'],
        ['Канал', summary.channelName || fallbackRow?.dataset.channel || '—'],
        ['Бизнес', summary.business || fallbackRow?.dataset.business || '—'],
        ['Проблема', summary.problem || fallbackRow?.dataset.problem || '—'],
        ['Локация', summary.locationName || summary.city || fallbackRow?.dataset.location || '—'],
        ['Ответственный', responsibleLabel],
        ['Создан', createdDisplay || createdLabel],
      ];
      if (detailsSummary) {
        detailsSummary.innerHTML = summaryItems.map(([label, value]) => `
          <div class="d-flex justify-content-between gap-2">
            <span>${label}</span>
            <span class="text-dark">${value || '—'}</span>
          </div>
        `).join('');
      }
      if (detailsMetrics) {
        const timeMetricsConfig = normalizeDialogTimeMetrics(window.DIALOG_CONFIG?.time_metrics);
        const createdAtTimestamp = parseDialogTimestamp(summary, fallbackRow);
        const totalMinutes = Number.isFinite(createdAtTimestamp)
          ? Math.max(0, Math.floor((Date.now() - createdAtTimestamp) / 60000))
          : null;
        const timeLabel = totalMinutes === null ? '—' : formatDuration(totalMinutes);
        const timeColor = totalMinutes === null ? null : resolveTimeMetricColor(totalMinutes, timeMetricsConfig);
        const metrics = [
          { label: 'Создано', value: createdDisplay },
          { label: 'Время обращения', value: timeLabel, color: timeColor },
          { label: 'Закрыто', value: resolvedDisplay || '—' },
          { label: 'Канал', value: channelLabel },
          { label: 'Бизнес', value: businessLabel },
          { label: 'Ответственный', value: responsibleLabel },
        ];
        detailsMetrics.innerHTML = metrics.map((item) => `
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
      renderHistory(data.history || []);
      updateResolveButton(statusRaw);
      if (statusRaw) {
        updateRowStatus(activeDialogRow, statusRaw, statusLabel);
      }
    } catch (error) {
      if (detailsSummary) {
        detailsSummary.innerHTML = `<div class="text-danger">Не удалось загрузить детали: ${error.message}</div>`;
      }
    }

    detailsModal.show();
    startHistoryPolling();
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

  if (detailsResolve) {
    detailsResolve.addEventListener('click', async () => {
      if (!activeDialogTicketId) return;
      detailsResolve.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/resolve`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        await openDialogDetails(activeDialogTicketId, activeDialogRow);
        if (typeof showNotification === 'function') {
          showNotification('Диалог закрыт', 'success');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось закрыть диалог', 'error');
        }
        detailsResolve.disabled = false;
      }
    });
  }

  if (detailsReopen) {
    detailsReopen.addEventListener('click', async () => {
      if (!activeDialogTicketId) return;
      detailsReopen.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/reopen`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        await openDialogDetails(activeDialogTicketId, activeDialogRow);
        if (typeof showNotification === 'function') {
          showNotification('Диалог переоткрыт', 'success');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось переоткрыть диалог', 'error');
        }
      } finally {
        detailsReopen.disabled = false;
      }
    });
  }

  if (detailsReplySend && detailsReplyText) {
    const sendReply = async () => {
      const message = detailsReplyText.value.trim();
      if (!message || !activeDialogTicketId) return;
      detailsReplySend.disabled = true;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/reply`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        detailsReplyText.value = '';
        appendOperatorMessage(message, data.timestamp || new Date().toISOString());
        if (typeof showNotification === 'function') {
          showNotification('Сообщение отправлено', 'success');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось отправить сообщение', 'error');
        }
      } finally {
        detailsReplySend.disabled = false;
      }
    };
    detailsReplySend.addEventListener('click', sendReply);
    detailsReplyText.addEventListener('keydown', (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        sendReply();
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

  if (detailsModalEl) {
    detailsModalEl.addEventListener('shown.bs.modal', () => {
      startHistoryPolling();
    });
    detailsModalEl.addEventListener('hidden.bs.modal', () => {
      activeDialogTicketId = null;
      activeDialogChannelId = null;
      activeDialogRow = null;
      if (detailsReplyText) detailsReplyText.value = '';
      stopHistoryPolling();
    });
  }

  if (questionTemplateList) {
    questionTemplateList.addEventListener('click', (event) => {
      const button = event.target.closest('[data-question-template-item]');
      if (!button) return;
      insertReplyText(button.dataset.questionValue || '');
    });
  }

  initDialogTemplates();
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
  initDetailsResize();
})();

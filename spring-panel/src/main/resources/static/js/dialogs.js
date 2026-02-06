// panel/static/dialogs.js
(function () {
  const table = document.getElementById('dialogsTable');
  if (!table) return;

  const quickSearch = document.getElementById('dialogQuickSearch');
  const pageSizeSelect = document.getElementById('dialogPageSize');
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
  const summaryTotal = document.getElementById('dialogsSummaryTotal');
  const summaryPending = document.getElementById('dialogsSummaryPending');
  const summaryResolved = document.getElementById('dialogsSummaryResolved');

  const detailsMeta = document.getElementById('dialogDetailsMeta');
  const detailsAvatar = document.getElementById('dialogDetailsAvatar');
  const detailsClientName = document.getElementById('dialogDetailsClientName');
  const detailsClientStatus = document.getElementById('dialogDetailsClientStatus');
  const detailsCategories = document.getElementById('dialogDetailsCategories');
  const detailsRating = document.getElementById('dialogDetailsRating');
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
  const detailsReplyMedia = document.getElementById('dialogReplyMedia');
  const detailsReplyMediaTrigger = document.getElementById('dialogReplyMediaTrigger');
  const detailsReplyEmojiTrigger = document.getElementById('dialogReplyEmojiTrigger');
  const emojiPanel = document.getElementById('dialogEmojiPanel');
  const emojiList = document.getElementById('dialogEmojiList');
  const mediaPreviewModalEl = document.getElementById('dialogMediaPreviewModal');
  const mediaPreviewVideo = document.getElementById('dialogMediaPreviewVideo');
  const mediaPreviewImage = document.getElementById('dialogMediaPreviewImage');
  const mediaPreviewImageControls = document.getElementById('dialogMediaImageControls');
  const mediaPreviewZoomOut = document.getElementById('dialogMediaZoomOut');
  const mediaPreviewZoomIn = document.getElementById('dialogMediaZoomIn');
  const mediaPreviewDownloadLink = document.getElementById('dialogMediaDownloadLink');
  const replyTarget = document.getElementById('dialogReplyTarget');
  const replyTargetText = document.getElementById('dialogReplyTargetText');
  const replyTargetClear = document.getElementById('dialogReplyTargetClear');
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
  const completionTemplatesToggle = document.getElementById('dialogCompletionTemplatesToggle');
  const completionTemplatesMenu = document.getElementById('dialogCompletionTemplatesMenu');
  const templateToggleButtons = document.querySelectorAll('[data-template-toggle]');

  const filtersModal = (typeof bootstrap !== 'undefined' && filtersModalEl)
    ? new bootstrap.Modal(filtersModalEl)
    : null;
  const columnsModal = (typeof bootstrap !== 'undefined' && columnsModalEl)
    ? new bootstrap.Modal(columnsModalEl)
    : null;
  const detailsModal = (typeof bootstrap !== 'undefined' && detailsModalEl)
    ? new bootstrap.Modal(detailsModalEl)
    : null;
  const mediaPreviewModal = (typeof bootstrap !== 'undefined' && mediaPreviewModalEl)
    ? new bootstrap.Modal(mediaPreviewModalEl)
    : null;

  const STORAGE_COLUMNS = 'bender:dialogs:columns';
  const STORAGE_WIDTHS = 'bender:dialogs:column-widths';
  const STORAGE_TASK = 'bender:dialogs:create-task';
  const STORAGE_PAGE_SIZE = 'bender:dialogs:page-size';
  const HISTORY_POLL_INTERVAL = 8000;
  const LIST_POLL_INTERVAL = 8000;
  const DEFAULT_PAGE_SIZE = 20;

  const BUSINESS_STYLES = (window.BUSINESS_CELL_STYLES && typeof window.BUSINESS_CELL_STYLES === 'object')
    ? window.BUSINESS_CELL_STYLES
    : {};

  const DEFAULT_DIALOG_TIME_METRICS = Object.freeze({
    good_limit: 30,
    warning_limit: 60,
    colors: Object.freeze({
      good: '#d1f7d1',
      warning: '#fff4d6',
      danger: '#f8d7da',
    }),
  });

  const DIALOG_EMOJI = ['😀','😁','😂','😊','😍','🤔','😢','😡','👍','🙏','🔥','🎉','✅','❗','📌'];

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
  let listPollTimer = null;
  let lastHistoryMarker = null;
  let lastListMarker = null;
  let historyLoading = false;
  let listLoading = false;
  let activeDialogContext = { clientName: '—', operatorName: '—' };
  let completionHideTimer = null;
  let activeAudioPlayer = null;
  let activeAudioSource = null;
  let selectedCategories = new Set();
  let activeReplyToTelegramId = null;
  let mediaImageScale = 1;

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

  function normalizePageSize(value) {
    if (value === 'all') return Infinity;
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_PAGE_SIZE;
  }

  function loadPageSize() {
    const raw = localStorage.getItem(STORAGE_PAGE_SIZE);
    const normalized = normalizePageSize(raw);
    filterState.pageSize = normalized;
    if (pageSizeSelect) {
      pageSizeSelect.value = normalized === Infinity ? 'all' : String(normalized);
    }
  }

  function persistPageSize() {
    if (filterState.pageSize === Infinity) {
      localStorage.setItem(STORAGE_PAGE_SIZE, 'all');
      return;
    }
    localStorage.setItem(STORAGE_PAGE_SIZE, String(filterState.pageSize));
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
      row.dataset.categories,
      row.dataset.responsible,
    ];
    return parts.filter(Boolean).join(' ').toLowerCase();
  }

  function rowsList() {
    return Array.from(table.tBodies[0].rows)
      .filter((row) => row.dataset && row.dataset.ticketId);
  }

  function buildDialogsMarker(dialogs) {
    if (!Array.isArray(dialogs) || dialogs.length === 0) return 'empty';
    return dialogs
      .map((item) => ({
        ticketId: item?.ticketId || '',
        requestNumber: item?.requestNumber || '',
        channelId: item?.channelId || '',
        status: item?.status || '',
        statusKey: item?.statusKey || '',
        unreadCount: Number(item?.unreadCount) || 0,
        lastMessageTimestamp: item?.lastMessageTimestamp || '',
        categories: item?.categories || '',
        rating: Number(item?.rating) || 0,
      }))
      .sort((a, b) => `${a.ticketId}:${a.channelId}`.localeCompare(`${b.ticketId}:${b.channelId}`))
      .map((item) => [
        item.ticketId,
        item.requestNumber,
        item.channelId,
        item.status,
        item.statusKey,
        item.unreadCount,
        item.lastMessageTimestamp,
        item.categories,
        item.rating,
      ].join('|'))
      .join('||');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function statusClassByKey(statusKey) {
    const normalizedKey = String(statusKey || '').toLowerCase();
    if (normalizedKey === 'auto_closed') return 'bg-secondary-subtle text-secondary';
    if (normalizedKey === 'closed') return 'bg-success-subtle text-success';
    if (normalizedKey === 'waiting_operator') return 'bg-warning-subtle text-warning';
    if (normalizedKey === 'waiting_client') return 'bg-info-subtle text-info';
    return 'bg-primary-subtle text-primary';
  }

  function resetReplyTarget() {
    activeReplyToTelegramId = null;
    if (replyTarget) {
      replyTarget.classList.add('d-none');
    }
    if (replyTargetText) {
      replyTargetText.textContent = '';
    }
    if (detailsReplyText) {
      detailsReplyText.placeholder = 'Введите ответ...';
    }
  }

  function setReplyTarget(messageId, preview) {
    activeReplyToTelegramId = messageId;
    if (detailsReplyText) {
      detailsReplyText.placeholder = `Ответ на сообщение #${messageId}`;
    }
    if (replyTarget) {
      replyTarget.classList.remove('d-none');
    }
    if (replyTargetText) {
      const safePreview = String(preview || '').trim();
      replyTargetText.textContent = safePreview || `Сообщение #${messageId}`;
    }
  }

  function resetMediaPreview() {
    mediaImageScale = 1;
    if (mediaPreviewImage) {
      mediaPreviewImage.style.transform = 'scale(1)';
      mediaPreviewImage.removeAttribute('src');
      mediaPreviewImage.classList.add('d-none');
    }
    if (mediaPreviewVideo) {
      mediaPreviewVideo.pause();
      mediaPreviewVideo.removeAttribute('src');
      mediaPreviewVideo.load();
      mediaPreviewVideo.classList.add('d-none');
    }
    if (mediaPreviewImageControls) {
      mediaPreviewImageControls.classList.add('d-none');
    }
    if (mediaPreviewDownloadLink) {
      mediaPreviewDownloadLink.classList.add('d-none');
      mediaPreviewDownloadLink.setAttribute('href', '#');
    }
  }

  function showImagePreview(src, name) {
    if (!src || !mediaPreviewModal || !mediaPreviewImage) return;
    resetMediaPreview();
    mediaPreviewImage.src = src;
    mediaPreviewImage.alt = name || 'Изображение';
    mediaPreviewImage.classList.remove('d-none');
    if (mediaPreviewImageControls) {
      mediaPreviewImageControls.classList.remove('d-none');
    }
    if (mediaPreviewDownloadLink) {
      mediaPreviewDownloadLink.classList.remove('d-none');
      mediaPreviewDownloadLink.setAttribute('href', src);
      mediaPreviewDownloadLink.setAttribute('download', name || 'image');
    }
    mediaPreviewModal.show();
  }

  function showVideoPreview(src) {
    if (!src || !mediaPreviewModal || !mediaPreviewVideo) return;
    resetMediaPreview();
    mediaPreviewVideo.src = src;
    mediaPreviewVideo.classList.remove('d-none');
    mediaPreviewVideo.play().catch(() => {});
    if (mediaPreviewDownloadLink) {
      mediaPreviewDownloadLink.classList.remove('d-none');
      mediaPreviewDownloadLink.setAttribute('href', src);
      mediaPreviewDownloadLink.setAttribute('download', 'video');
    }
    mediaPreviewModal.show();
  }

  function escapeSelectorValue(value) {
    if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
      return CSS.escape(value);
    }
    return String(value).replace(/"/g, '\\"');
  }

  function avatarInitial(name) {
    const normalized = String(name || '').trim();
    return normalized ? normalized.charAt(0).toUpperCase() : '—';
  }

  function formatRatingStars(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric) || numeric <= 0) return '';
    const capped = Math.min(5, Math.max(1, Math.round(numeric)));
    return '★'.repeat(capped);
  }

  function formatDialogMeta(ticketId, requestNumber) {
    const normalizedTicketId = ticketId ? String(ticketId) : '';
    const normalizedRequest = requestNumber ? String(requestNumber) : '';
    if (normalizedRequest) {
      if (normalizedTicketId && normalizedRequest !== normalizedTicketId) {
        return `№ обращения: ${normalizedRequest} · ID: ${normalizedTicketId}`;
      }
      return `№ обращения: ${normalizedRequest}`;
    }
    return normalizedTicketId ? `ID диалога: ${normalizedTicketId}` : '';
  }

  function renderDialogRow(item) {
    const ticketId = item?.ticketId || '—';
    const requestNumber = item?.requestNumber;
    const displayNumber = requestNumber || ticketId;
    const clientName = item?.clientName || item?.username || 'Неизвестный клиент';
    const clientStatus = item?.clientStatus || 'статус не указан';
    const channelLabel = item?.channelName || 'Без канала';
    const businessLabel = item?.business || 'Без бизнеса';
    const problemLabel = item?.problem || 'Проблема не указана';
    const locationLabel = item?.location || [item?.city, item?.locationName].filter(Boolean).join(', ') || '—';
    const statusRaw = item?.status || '';
    const categories = item?.categoriesSafe || item?.categories || '—';
    const statusKey = item?.statusKey || '';
    const statusLabel = formatStatusLabel(statusRaw, item?.statusLabel || '', statusKey);
    const responsible = item?.responsible || item?.resolvedBy || '—';
    const unreadCount = Number(item?.unreadCount) || 0;
    const createdDate = item?.createdDateSafe || item?.createdDate || 'Дата не указана';
    const createdTime = item?.createdTimeSafe || item?.createdTime || '—';
    const ratingValue = Number(item?.rating);
    const ratingStars = formatRatingStars(ratingValue);

    return `
      <tr data-ticket-id="${escapeHtml(ticketId)}"
          data-request-number="${escapeHtml(requestNumber || '')}"
          data-client="${escapeHtml(clientName)}"
          data-client-status="${escapeHtml(clientStatus)}"
          data-channel-id="${escapeHtml(item?.channelId || '')}"
          data-user-id="${escapeHtml(item?.userId || '')}"
          data-channel="${escapeHtml(channelLabel)}"
          data-business="${escapeHtml(businessLabel)}"
          data-problem="${escapeHtml(problemLabel)}"
          data-status="${escapeHtml(statusLabel)}"
          data-status-raw="${escapeHtml(statusRaw)}"
          data-status-key="${escapeHtml(statusKey)}"
          data-location="${escapeHtml(locationLabel)}"
          data-categories="${escapeHtml(categories)}"
          data-responsible="${escapeHtml(responsible === '—' ? '' : responsible)}"
          data-created-at="${escapeHtml(item?.createdAt || '')}"
          data-unread="${unreadCount}"
          data-rating="${Number.isFinite(ratingValue) ? ratingValue : ''}"
          data-last-message-timestamp="${escapeHtml(item?.lastMessageTimestamp || '')}">
        <td>${escapeHtml(displayNumber)}</td>
        <td>
          <div class="d-flex align-items-center gap-2">
            <div class="dialog-avatar">
              <span class="avatar-circle">${escapeHtml(avatarInitial(clientName))}</span>
            </div>
            <div>
              <div class="fw-semibold">${escapeHtml(clientName)}</div>
              <div class="small text-muted">${escapeHtml(clientStatus)}</div>
            </div>
          </div>
        </td>
        <td>
          <div class="d-flex align-items-center gap-2">
            <span class="badge rounded-pill ${statusClassByKey(statusKey)}">${escapeHtml(statusLabel)}</span>
            <span class="badge text-bg-danger dialog-unread-count ${unreadCount > 0 ? '' : 'd-none'}">${unreadCount}</span>
          </div>
          ${ratingStars ? `<div class="small text-warning">${escapeHtml(ratingStars)}</div>` : ''}
        </td>
        <td>${escapeHtml(channelLabel)}</td>
        <td class="dialog-business-cell" data-business="${escapeHtml(businessLabel)}">
          <div class="dialog-business-pill">
            <span class="dialog-business-icon d-none" aria-hidden="true"></span>
            <span class="dialog-business-text">${escapeHtml(businessLabel)}</span>
          </div>
        </td>
        <td class="text-truncate" style="max-width: 260px;">${escapeHtml(problemLabel)}</td>
        <td>${escapeHtml(locationLabel)}</td>
        <td>${escapeHtml(categories)}</td>
        <td>${escapeHtml(responsible)}</td>
        <td>
          <div class="small text-muted">${escapeHtml(createdDate)}</div>
          <div class="small">${escapeHtml(createdTime)}</div>
        </td>
        <td class="dialog-actions">
          <a href="#" class="btn btn-sm btn-outline-primary dialog-open-btn" data-ticket-id="${escapeHtml(ticketId)}">Открыть</a>
          <a href="/tasks" class="btn btn-sm btn-outline-secondary dialog-task-btn"
             data-ticket-id="${escapeHtml(ticketId)}"
             data-client="${escapeHtml(clientName)}">Задача</a>
        </td>
      </tr>
    `;
  }

  function refreshSummaryCounters(summary) {
    if (summaryTotal) summaryTotal.textContent = String(summary?.totalTickets ?? 0);
    if (summaryPending) summaryPending.textContent = String(summary?.pendingTickets ?? 0);
    if (summaryResolved) summaryResolved.textContent = String(summary?.resolvedTickets ?? 0);
  }

  function syncDialogsTable(dialogs) {
    const tbody = table.tBodies[0];
    if (!tbody) return;
    tbody.innerHTML = Array.isArray(dialogs) && dialogs.length
      ? dialogs.map((item) => renderDialogRow(item)).join('')
      : '';
    applyColumnState();
    applyBusinessCellStyles();
    restoreColumnWidths();
    applyFilters();

    if (activeDialogTicketId) {
      const selector = `.dialog-open-btn[data-ticket-id="${escapeSelectorValue(activeDialogTicketId)}"]`;
      const openBtn = table.querySelector(selector);
      activeDialogRow = openBtn ? openBtn.closest('tr') : null;
    }
  }

  async function refreshDialogsList() {
    if (listLoading) return;
    if (document.visibilityState === 'hidden') return;
    listLoading = true;
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
      const marker = buildDialogsMarker(dialogs);
      if (lastListMarker === null) {
        lastListMarker = marker;
      }
      if (marker !== lastListMarker) {
        syncDialogsTable(dialogs);
        refreshSummaryCounters(data.summary || {});
        lastListMarker = marker;
      }
    } catch (error) {
      // ignore polling errors
    } finally {
      listLoading = false;
    }
  }

  function startDialogsPolling() {
    if (listPollTimer) return;
    listPollTimer = setInterval(refreshDialogsList, LIST_POLL_INTERVAL);
  }

  function stopDialogsPolling() {
    if (listPollTimer) {
      clearInterval(listPollTimer);
      listPollTimer = null;
    }
  }

  const emptyRow = document.createElement('tr');
  emptyRow.innerHTML = '<td colspan="11" class="text-center text-muted py-4">Нет результатов</td>';
  emptyRow.classList.add('d-none');

  function ensureEmptyRow() {
    if (!table.tBodies[0].contains(emptyRow)) {
      table.tBodies[0].appendChild(emptyRow);
    }
  }

  const filterState = { search: '', status: '', view: 'all', pageSize: DEFAULT_PAGE_SIZE };;

  function isResolved(row) {
    const raw = (row.dataset.statusRaw || '').toLowerCase();
    const label = (row.dataset.status || '').toLowerCase();
    const key = (row.dataset.statusKey || '').toLowerCase();
    return raw === 'resolved' || raw === 'closed' || label.startsWith('закрыт') || key.includes('closed');
  }

  function applyFilters() {
    const search = (filterState.search || '').trim().toLowerCase();
    const status = (filterState.status || '').trim().toLowerCase();
    const matchedRows = [];
    rowsList().forEach((row) => {
      const text = collectRowSearchText(row);
      const statusValue = (row.dataset.status || '').toLowerCase();
      const matchesSearch = !search || text.includes(search);
      const matchesStatus = !status || statusValue === status;
      const matchesView = filterState.view === 'active' ? !isResolved(row) : true;
      const visible = matchesSearch && matchesStatus && matchesView;
      row.classList.toggle('d-none', !visible);
      if (visible) {
        matchedRows.push(row);
      }
    });
    const visibleCount = applyPageSize(matchedRows);
    updateZebraRows(matchedRows);
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

  function normalizeBusinessStyles(raw) {
    const styles = {};
    if (!raw || typeof raw !== 'object') return styles;
    Object.entries(raw).forEach(([key, value]) => {
      if (typeof key !== 'string') return;
      const business = key.trim();
      if (!business) return;
      const config = value && typeof value === 'object' ? value : {};
      styles[business] = {
        background: sanitizeHexColor(config.background_color, ''),
        text: sanitizeHexColor(config.text_color, ''),
        icon: typeof config.icon === 'string' ? config.icon.trim() : '',
      };
    });
    return styles;
  }

  const BUSINESS_STYLE_MAP = normalizeBusinessStyles(BUSINESS_STYLES);

  function applyBusinessCellStyles() {
    if (!Object.keys(BUSINESS_STYLE_MAP).length) return;
    rowsList().forEach((row) => {
      const cell = row.querySelector('.dialog-business-cell');
      if (!cell) return;
      const business = (cell.dataset.business || row.dataset.business || '').trim();
      const config = BUSINESS_STYLE_MAP[business];
      const pill = cell.querySelector('.dialog-business-pill');
      const icon = cell.querySelector('.dialog-business-icon');
      if (!pill) return;
      pill.style.backgroundColor = '';
      pill.style.color = '';
      if (icon) {
        icon.classList.add('d-none');
        icon.innerHTML = '';
      }
      if (!config) return;
      if (config.background) {
        pill.style.backgroundColor = config.background;
      }
      if (config.text) {
        pill.style.color = config.text;
      }
      if (icon && config.icon) {
        icon.innerHTML = `<img src="${config.icon}" alt="" />`;
        icon.classList.remove('d-none');
      }
    });
  }

  function applyPageSize(matchedRows) {
    let limit = filterState.pageSize;
    if (limit === Infinity) {
      return matchedRows.length;
    }
    if (!Number.isFinite(limit) || limit <= 0) {
      limit = DEFAULT_PAGE_SIZE;
    }
    let visibleCount = 0;
    matchedRows.forEach((row, index) => {
      const visible = index < limit;
      row.classList.toggle('d-none', !visible);
      if (visible) visibleCount += 1;
    });
    return visibleCount;
  }

  function updateZebraRows(matchedRows) {
    rowsList().forEach((row) => {
      row.classList.remove('dialog-row-even', 'dialog-row-odd');
    });
    matchedRows.filter((row) => !row.classList.contains('d-none')).forEach((row, index) => {
      row.classList.add(index % 2 === 0 ? 'dialog-row-odd' : 'dialog-row-even');
    });
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
      const badge = document.createElement('button');
      const normalized = String(category).trim();
      badge.className = 'badge rounded-pill text-bg-light border dialog-category-badge';
      badge.type = 'button';
      badge.dataset.categoryValue = normalized;
      badge.textContent = normalized;
      badge.classList.toggle('is-selected', selectedCategories.has(normalized));
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
        syncCategorySelections();
        categoryTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(templates, categoryTemplateSelect.value);
          renderCategoryTemplate(selected);
          syncCategorySelections();
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

  function syncCategorySelections() {
    if (!categoryTemplateList) return;
    categoryTemplateList.querySelectorAll('[data-category-value]').forEach((item) => {
      const value = item.dataset.categoryValue || '';
      item.classList.toggle('is-selected', selectedCategories.has(value));
    });
  }

  function renderEmojiPanel() {
    if (!emojiList) return;
    emojiList.innerHTML = '';
    DIALOG_EMOJI.forEach((emoji) => {
      const button = document.createElement('button');
      button.className = 'btn btn-outline-secondary btn-sm';
      button.type = 'button';
      button.dataset.emojiValue = emoji;
      button.textContent = emoji;
      emojiList.appendChild(button);
    });
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

  function normalizeMessageSenderByType(messageType, sender) {
    const type = String(messageType || '').toLowerCase();
    if (type.includes('operator')) return 'support';
    if (type.includes('system')) return 'system';
    return normalizeMessageSender(sender);
  }

  function resolveSenderLabel(message, context) {
    const senderType = normalizeMessageSenderByType(message?.messageType, message?.sender);
    if (senderType === 'support') {
      return context?.operatorName || message?.sender || 'Оператор';
    }
    if (senderType === 'system') {
      return message?.sender || 'Система';
    }
    return context?.clientName || message?.sender || 'Клиент';
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

  function resolveAttachmentKind(messageType, attachment) {
    const type = String(messageType || '').toLowerCase();
    if (type.includes('animation')) return 'animation';
    if (type.includes('video') || type.includes('videonote') || type.includes('video_note')) return 'video';
    if (type.includes('audio') || type.includes('voice')) return 'audio';
    if (type.includes('photo') || type.includes('image')) return 'image';
    if (attachment) {
      const lower = attachment.toLowerCase();
      if (lower.endsWith('.gif')) return 'animation';
      if (/\.(mp4|webm|mov|m4v)$/i.test(lower)) return 'video';
      if (/\.(mp3|wav|ogg|m4a)$/i.test(lower)) return 'audio';
      if (/\.(png|jpe?g|webp|bmp)$/i.test(lower)) return 'image';
    }
    return 'file';
  }

  function resolveAttachmentName(message, attachment) {
    const extractExtension = (value) => {
      if (!value) return '';
      const clean = String(value).split('?')[0].split('#')[0];
      const lastSegment = clean.split('/').pop()?.split('\\').pop() || clean;
      const dotIndex = lastSegment.lastIndexOf('.');
      return dotIndex > 0 ? lastSegment.slice(dotIndex) : '';
    };

    if (message) {
      const hasPath = /[\\/]/.test(message) || /^https?:\/\//i.test(message);
      if (!hasPath) return message;
      const extension = extractExtension(message);
      return extension ? `Файл ${extension}` : 'Файл';
    }

    if (!attachment) return 'Файл';
    const extension = extractExtension(attachment);
    return extension ? `Файл ${extension}` : 'Файл';
  }

  function buildMediaMarkup(message) {
    if (!message?.attachment) return '';
    const kind = resolveAttachmentKind(message.messageType, message.attachment);
    const name = resolveAttachmentName(message.message, message.attachment);
    const downloadLink = `
      <a class="btn btn-sm btn-outline-secondary" href="${message.attachment}" download target="_blank" rel="noopener">
        Скачать
      </a>
    `;
    if (kind === 'audio') {
      return `
        <div class="chat-media">
          <div class="chat-media-actions">
            <button class="btn btn-sm btn-outline-primary chat-audio-play" type="button"
              data-audio-src="${message.attachment}">Воспроизвести</button>
            ${downloadLink}
            <span class="chat-media-file-name">${name}</span>
          </div>
        </div>
      `;
    }
    if (kind === 'video') {
      return `
        <div class="chat-media">
          <video class="chat-media-preview video" src="${message.attachment}" data-video-src="${message.attachment}" data-media-name="${name}" muted playsinline preload="metadata"></video>
          <div class="chat-media-actions">
            ${downloadLink}
            <span class="chat-media-file-name">${name}</span>
          </div>
        </div>
      `;
    }
    if (kind === 'animation') {
      const isGif = /\.gif($|\?)/i.test(message.attachment);
      const preview = isGif
        ? `<img class=\"chat-media-preview\" src=\"${message.attachment}\" alt=\"${name}\" data-image-src=\"${message.attachment}\" data-media-name=\"${name}\">`
        : `<video class=\"chat-media-preview\" src=\"${message.attachment}\" autoplay loop muted playsinline></video>`;
      return `
        <div class="chat-media">
          ${preview}
          <div class="chat-media-actions">
            ${downloadLink}
            <span class="chat-media-file-name">${name}</span>
          </div>
        </div>
      `;
    }
    if (kind === 'image') {
      return `
        <div class="chat-media">
          <img class="chat-media-preview" src="${message.attachment}" alt="${name}" data-image-src="${message.attachment}" data-media-name="${name}">
          <div class="chat-media-actions">
            ${downloadLink}
            <span class="chat-media-file-name">${name}</span>
          </div>
        </div>
      `;
    }
    return `
      <div class="chat-media">
        <div class="chat-media-actions">
          ${downloadLink}
          <span class="chat-media-file-name">${name}</span>
        </div>
      </div>
    `;
  }

  function messageToHtml(message) {
    const senderType = normalizeMessageSenderByType(message?.messageType, message?.sender);
    const senderLabel = resolveSenderLabel(message, activeDialogContext);
    const timestamp = formatTimestamp(message?.timestamp, { includeTime: true });
    const isDeleted = Boolean(message?.deletedAt);
    const isEdited = Boolean(message?.editedAt);
    const isSupport = senderType === 'support';
    const replyPreview = message?.replyPreview
      ? `<div class="small text-muted border-start ps-2 mb-1 chat-message-reply-source">↪ ${escapeHtml(message.replyPreview)}</div>`
      : '';
    const forwardedBadge = message?.forwardedFrom
      ? `<div class="small text-muted mb-1">Переслано от ${escapeHtml(message.forwardedFrom)}</div>`
      : '';
    const bodyText = message?.message ? escapeHtml(message.message).replace(/\n/g, '<br>') : '';
    const fallbackType = message?.messageType && !bodyText ? `[${escapeHtml(message.messageType)}]` : '';
    const body = isDeleted ? '<span class="text-muted">Сообщение удалено</span>' : (bodyText || fallbackType || '—');
    const originalBlock = isEdited && message?.originalMessage && message.originalMessage !== message.message
      ? `<div class="small text-muted mt-1"><div>Было: ${escapeHtml(message.originalMessage)}</div><div>Стало: ${escapeHtml(message.message || '')}</div></div>`
      : '';
    const statusBadges = [
      isEdited ? '<span class="chat-message-meta-badge">✏️ Изменено</span>' : '',
      isDeleted ? '<span class="chat-message-meta-badge">🗑 Удалено</span>' : ''
    ].join(' ');
    const media = isDeleted ? '' : buildMediaMarkup(message);
    const canReply = senderType !== 'system' && message?.telegramMessageId;
    const actionButtons = canReply
      ? `<div class="chat-message-actions mt-2">
          <button class="btn btn-sm btn-outline-secondary" type="button" data-action="reply" data-message-id="${message.telegramMessageId}">Ответить</button>
          ${isSupport ? `<button class="btn btn-sm btn-outline-secondary" type="button" data-action="edit" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Редактировать</button>` : ''}
          ${isSupport ? `<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Удалить</button>` : ''}
        </div>`
      : '';

    return `
      <div class="chat-message ${senderType} ${isDeleted ? 'is-deleted' : ''}" data-telegram-message-id="${message?.telegramMessageId || ''}">
        <div class="chat-message-header">
          <span>${escapeHtml(senderLabel)}</span>
          <span>${escapeHtml(timestamp)}</span>
        </div>
        ${statusBadges ? `<div class="small text-muted mb-1">${statusBadges}</div>` : ''}
        ${forwardedBadge}
        ${replyPreview}
        <div class="chat-message-body">${body}</div>
        ${originalBlock}
        ${media}
        ${actionButtons}
      </div>
    `;
  }

  function renderHistory(messages) {
    if (!detailsHistory) return;
    if (!Array.isArray(messages) || messages.length === 0) {
      detailsHistory.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
      lastHistoryMarker = 'empty';
      return;
    }
    lastHistoryMarker = historyMarker(messages);
    detailsHistory.innerHTML = messages.map((msg) => messageToHtml(msg)).join('');
    detailsHistory.scrollTop = detailsHistory.scrollHeight;
  }

  function appendHistoryMessage(message) {
    if (!detailsHistory) return;
    const wrapper = document.createElement('div');
    wrapper.innerHTML = messageToHtml(message);
    const node = wrapper.firstElementChild;
    if (node) {
      detailsHistory.appendChild(node);
    }
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
      badge.classList.remove('bg-primary-subtle', 'text-primary', 'bg-warning-subtle', 'text-warning', 'bg-success-subtle', 'text-success', 'bg-secondary-subtle', 'text-secondary', 'bg-info-subtle', 'text-info');
      const normalizedKey = (statusKey || '').toLowerCase();
      if (normalizedKey === 'auto_closed') {
        badge.classList.add('bg-secondary-subtle', 'text-secondary');
      } else if (normalizedKey === 'closed') {
        badge.classList.add('bg-success-subtle', 'text-success');
      } else if (normalizedKey === 'waiting_operator') {
        badge.classList.add('bg-warning-subtle', 'text-warning');
      } else if (normalizedKey === 'waiting_client') {
        badge.classList.add('bg-info-subtle', 'text-info');
      } else {
        badge.classList.add('bg-primary-subtle', 'text-primary');
      }
    }
    const unreadBadge = row.querySelector('.dialog-unread-count');
    if (unreadBadge) {
      const count = Number(unreadCount) || 0;
      unreadBadge.textContent = count;
      unreadBadge.classList.toggle('d-none', count <= 0);
    }
  }

  function formatStatusLabel(raw, fallback, statusKey) {
    if (fallback) return fallback;
    if (statusKey) {
      switch (statusKey) {
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
    const label = String(statusLabel || '').toLowerCase();
    return raw === 'resolved' || raw === 'closed' || key === 'closed' || key === 'auto_closed' || label.startsWith('закрыт');
  }

  async function openDialogDetails(ticketId, fallbackRow) {
    if (!ticketId || !detailsModal) return;
    activeDialogTicketId = ticketId;
    activeDialogRow = fallbackRow || null;
    activeDialogChannelId = fallbackRow?.dataset?.channelId || null;
    if (detailsMeta) {
      const fallbackRequestNumber = fallbackRow?.dataset?.requestNumber || '';
      detailsMeta.textContent = formatDialogMeta(ticketId, fallbackRequestNumber);
    }
    if (detailsRating) {
      detailsRating.textContent = '';
      detailsRating.classList.add('d-none');
    }
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
      selectedCategories = new Set(Array.isArray(data.categories) ? data.categories.filter(Boolean).map((item) => String(item).trim()) : []);
      syncCategorySelections();
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
      const createdDisplay = formatTimestamp(createdLabel, { includeTime: true });
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
      const statusKey = summary.statusKey || fallbackRow?.dataset.statusKey || '';
      const statusLabel = formatStatusLabel(statusRaw, summary.statusLabel || fallbackRow?.dataset.status, statusKey);
      const locationLabel = summary.locationName || summary.city || fallbackRow?.dataset.location || '—';
      const problemLabel = summary.problem || fallbackRow?.dataset.problem || '—';
      const categoriesLabel = summary.categoriesSafe || summary.categories || fallbackRow?.dataset.categories || '—';
      const requestNumber = summary.requestNumber || fallbackRow?.dataset.requestNumber || '';
      const ratingValue = summary.rating ?? fallbackRow?.dataset.rating;
      const ratingStars = formatRatingStars(ratingValue);
      if (detailsAvatar) {
        const initial = clientName && clientName !== '—' ? clientName.trim().charAt(0).toUpperCase() : '—';
        detailsAvatar.textContent = initial || '—';
      }
      if (detailsClientName) detailsClientName.textContent = clientName;
      if (detailsClientStatus) detailsClientStatus.textContent = clientStatus;
      if (detailsCategories) detailsCategories.textContent = `Категории: ${categoriesLabel || '—'}`;
      if (detailsProblem) detailsProblem.textContent = problemLabel;
      if (detailsMeta) detailsMeta.textContent = formatDialogMeta(ticketId, requestNumber);
      if (detailsRating) {
        if (ratingStars) {
          detailsRating.textContent = ratingStars;
          detailsRating.classList.remove('d-none');
        } else {
          detailsRating.textContent = '';
          detailsRating.classList.add('d-none');
        }
      }
      const summaryItems = [
        ['Клиент', summary.clientName || summary.username || fallbackRow?.dataset.client || '—'],
        ['Статус клиента', summary.clientStatus || fallbackRow?.dataset.clientStatus || '—'],
        ['Статус', statusLabel || '—'],
        ['Канал', summary.channelName || fallbackRow?.dataset.channel || '—'],
        ['Бизнес', businessLabel],
        ['Проблема', summary.problem || fallbackRow?.dataset.problem || '—'],
        ['Локация', summary.locationName || summary.city || fallbackRow?.dataset.location || '—'],
        ['Категории', categoriesLabel || '—'],
        ['Ответственный', responsibleLabel],
        ['Создан', createdDisplay || createdLabel],
      ];
      activeDialogContext = {
        clientName,
        operatorName: responsibleLabel || 'Оператор',
      };
      if (detailsSummary) {
        const clientUserId = summary.userId || fallbackRow?.dataset.userId || '';
        detailsSummary.innerHTML = summaryItems.map(([label, value]) => {
          const safeValue = value || '—';
          let renderedValue = `<span class="text-dark">${safeValue}</span>`;
          if (label === 'Клиент' && safeValue !== '—' && clientUserId) {
            renderedValue = `<a class="dialog-summary-value-link" href="/client/${encodeURIComponent(clientUserId)}" target="_blank" rel="noopener">${safeValue}</a>`;
          }
          if (label === 'Ответственный' && safeValue !== '—') {
            renderedValue = `<a class="dialog-summary-value-link" href="/users/${encodeURIComponent(safeValue)}" target="_blank" rel="noopener">${safeValue}</a>`;
          }
          return `
          <div class="d-flex justify-content-between gap-2">
            <span>${label}</span>
            ${renderedValue}
          </div>
        `;
        }).join('');
      }
      if (detailsMetrics) {
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
      if (statusRaw || statusKey) {
        updateRowStatus(activeDialogRow, statusRaw, statusLabel, statusKey, summary.unreadCount);
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
        const categories = Array.from(selectedCategories);
        if (!categories.length) {
          throw new Error('Укажите хотя бы одну категорию обращения перед закрытием.');
        }
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/resolve`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ categories }),
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
          body: JSON.stringify({ message, replyToTelegramId: activeReplyToTelegramId }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        detailsReplyText.value = '';
        resetReplyTarget();
        activeDialogContext.operatorName = data.responsible || activeDialogContext.operatorName;
        appendHistoryMessage({
          sender: data.responsible || 'Оператор',
          message,
          timestamp: data.timestamp || new Date().toISOString(),
          messageType: 'operator_message',
        });
        if (activeDialogRow) {
          updateRowStatus(activeDialogRow, activeDialogRow.dataset.statusRaw || '', 'ожидает ответа клиента', 'waiting_client', 0);
          const rowCells = activeDialogRow.children;
          const responsibleIndex = table.querySelector('th[data-column-key="responsible"]')?.cellIndex ?? -1;
          const responsibleValue = data.responsible || activeDialogRow.dataset.responsible;
          if (responsibleValue && responsibleIndex >= 0 && rowCells[responsibleIndex]) {
            rowCells[responsibleIndex].textContent = responsibleValue;
            activeDialogRow.dataset.responsible = responsibleValue;
          }
          applyFilters();
        }
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

  async function sendMediaFiles(files) {
    if (!activeDialogTicketId || !files || files.length === 0) return;
    const caption = detailsReplyText?.value?.trim() || '';
    detailsReplySend.disabled = true;
    try {
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        if (caption) {
          formData.append('message', caption);
        }
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/media`, {
          method: 'POST',
          body: formData,
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        activeDialogContext.operatorName = data.responsible || activeDialogContext.operatorName;
        appendHistoryMessage({
          sender: data.responsible || 'Оператор',
          message: data.message || '',
          timestamp: data.timestamp || new Date().toISOString(),
          messageType: data.messageType || 'operator_media',
          attachment: data.attachment || null,
        });
        if (activeDialogRow) {
          updateRowStatus(activeDialogRow, activeDialogRow.dataset.statusRaw || '', 'ожидает ответа клиента', 'waiting_client', 0);
        }
      }
      if (detailsReplyText) detailsReplyText.value = '';
      if (typeof showNotification === 'function') {
        showNotification('Медиа отправлено', 'success');
      }
    } catch (error) {
      if (typeof showNotification === 'function') {
        showNotification(error.message || 'Не удалось отправить медиа', 'error');
      }
    } finally {
      detailsReplySend.disabled = false;
      if (detailsReplyMedia) {
        detailsReplyMedia.value = '';
      }
    }
  }


  if (detailsHistory) {
    detailsHistory.addEventListener('click', async (event) => {
      const button = event.target.closest('button[data-action]');
      if (!button || !activeDialogTicketId) return;
      const messageId = Number.parseInt(button.dataset.messageId, 10);
      if (!Number.isFinite(messageId)) return;
      const action = button.dataset.action;
      if (action === 'reply') {
        const messageNode = button.closest('.chat-message');
        const previewText = messageNode?.querySelector('.chat-message-reply-source')?.textContent
          || messageNode?.querySelector('.chat-message-body')?.textContent
          || '';
        setReplyTarget(messageId, previewText);
        if (detailsReplyText) {
          detailsReplyText.focus();
        }
        return;
      }
      if (action === 'edit') {
        const current = button.closest('.chat-message')?.querySelector('div:nth-of-type(2)')?.textContent || '';
        const nextText = window.prompt('Введите новый текст сообщения:', current.trim());
        if (!nextText || !nextText.trim()) return;
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/edit`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ telegramMessageId: messageId, message: nextText.trim() }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) throw new Error(data?.error || `Ошибка ${resp.status}`);
        await refreshHistory();
        return;
      }
      if (action === 'delete') {
        if (!window.confirm('Удалить сообщение у клиента?')) return;
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/delete`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ telegramMessageId: messageId }),
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) throw new Error(data?.error || `Ошибка ${resp.status}`);
        await refreshHistory();
      }
    });
  }

  if (quickSearch) {
    quickSearch.addEventListener('input', () => applyQuickSearch(quickSearch.value));
  }

  if (pageSizeSelect) {
    pageSizeSelect.addEventListener('change', () => {
      filterState.pageSize = normalizePageSize(pageSizeSelect.value);
      persistPageSize();
      applyFilters();
    });
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
      if (detailsReplyMedia) detailsReplyMedia.value = '';
      resetReplyTarget();
      selectedCategories = new Set();
      stopHistoryPolling();
    });
  }

  if (categoryTemplateList) {
    categoryTemplateList.addEventListener('click', (event) => {
      const badge = event.target.closest('[data-category-value]');
      if (!badge) return;
      const value = badge.dataset.categoryValue || '';
      if (!value) return;
      if (selectedCategories.has(value)) {
        selectedCategories.delete(value);
      } else {
        selectedCategories.add(value);
      }
      syncCategorySelections();
    });
  }

  if (detailsReplyEmojiTrigger && emojiPanel) {
    detailsReplyEmojiTrigger.addEventListener('click', () => {
      emojiPanel.classList.toggle('is-open');
    });
  }

  if (emojiList) {
    emojiList.addEventListener('click', (event) => {
      const button = event.target.closest('[data-emoji-value]');
      if (!button) return;
      insertReplyText(button.dataset.emojiValue || '');
    });
  }

  if (questionTemplateList) {
    questionTemplateList.addEventListener('click', (event) => {
      const button = event.target.closest('[data-question-template-item]');
      if (!button) return;
      insertReplyText(button.dataset.questionValue || '');
    });
  }

  if (detailsReplyMediaTrigger && detailsReplyMedia) {
    detailsReplyMediaTrigger.addEventListener('click', () => {
      detailsReplyMedia.click();
    });
    detailsReplyMedia.addEventListener('change', () => {
      sendMediaFiles(detailsReplyMedia.files);
    });
  }

  if (replyTargetClear) {
    replyTargetClear.addEventListener('click', () => {
      resetReplyTarget();
      if (detailsReplyText) {
        detailsReplyText.focus();
      }
    });
  }

  if (mediaPreviewZoomIn) {
    mediaPreviewZoomIn.addEventListener('click', () => {
      mediaImageScale = Math.min(3, mediaImageScale + 0.2);
      if (mediaPreviewImage) {
        mediaPreviewImage.style.transform = `scale(${mediaImageScale})`;
      }
    });
  }

  if (mediaPreviewZoomOut) {
    mediaPreviewZoomOut.addEventListener('click', () => {
      mediaImageScale = Math.max(0.4, mediaImageScale - 0.2);
      if (mediaPreviewImage) {
        mediaPreviewImage.style.transform = `scale(${mediaImageScale})`;
      }
    });
  }

  if (templateToggleButtons.length) {
    templateToggleButtons.forEach((button) => {
      button.addEventListener('click', () => {
        const target = button.dataset.templateToggle;
        const section = target === 'category' ? categoryTemplatesSection : questionTemplatesSection;
        if (!section) return;
        section.classList.toggle('is-open');
      });
    });
  }

  if (completionTemplatesSection) {
    const openCompletion = () => {
      completionTemplatesSection.classList.add('is-open');
    };
    const scheduleClose = () => {
      if (completionHideTimer) {
        clearTimeout(completionHideTimer);
      }
      completionHideTimer = setTimeout(() => {
        completionTemplatesSection.classList.remove('is-open');
      }, 2000);
    };
    completionTemplatesSection.addEventListener('mouseenter', () => {
      if (completionHideTimer) clearTimeout(completionHideTimer);
      openCompletion();
    });
    completionTemplatesSection.addEventListener('mouseleave', scheduleClose);
    if (completionTemplatesToggle) {
      completionTemplatesToggle.addEventListener('click', () => {
        completionTemplatesSection.classList.toggle('is-open');
      });
    }
  }

  if (detailsHistory) {
    detailsHistory.addEventListener('click', (event) => {
      const playButton = event.target.closest('.chat-audio-play');
      if (playButton) {
        const src = playButton.dataset.audioSrc;
        if (!src) return;
        if (activeAudioPlayer && activeAudioSource === src && !activeAudioPlayer.paused) {
          activeAudioPlayer.pause();
          playButton.textContent = 'Воспроизвести';
          return;
        }
        if (activeAudioPlayer) {
          activeAudioPlayer.pause();
        }
        activeAudioPlayer = new Audio(src);
        activeAudioSource = src;
        playButton.textContent = 'Пауза';
        activeAudioPlayer.addEventListener('ended', () => {
          playButton.textContent = 'Воспроизвести';
        });
        activeAudioPlayer.play().catch(() => {
          playButton.textContent = 'Воспроизвести';
        });
        return;
      }
      const imagePreview = event.target.closest('[data-image-src]');
      if (imagePreview) {
        const src = imagePreview.dataset.imageSrc;
        if (!src) return;
        showImagePreview(src, imagePreview.dataset.mediaName || 'image');
        return;
      }
      const videoPreview = event.target.closest('[data-video-src]');
      if (videoPreview) {
        const src = videoPreview.dataset.videoSrc;
        if (!src) return;
        showVideoPreview(src);
      }
    });
  }

  if (mediaPreviewModalEl) {
    mediaPreviewModalEl.addEventListener('hidden.bs.modal', () => {
      resetMediaPreview();
    });
  }

  initDialogTemplates();
  renderEmojiPanel();
  lastListMarker = buildDialogsMarker(rowsList().map((row) => ({
    ticketId: row.dataset.ticketId || '',
    requestNumber: row.dataset.requestNumber || '',
    channelId: row.dataset.channelId || '',
    status: row.dataset.statusRaw || row.dataset.status || '',
    statusKey: row.dataset.statusKey || '',
    unreadCount: Number(row.dataset.unread || 0) || 0,
    lastMessageTimestamp: row.dataset.lastMessageTimestamp || row.dataset.createdAt || '',
    rating: Number(row.dataset.rating) || 0,
  })));
  startDialogsPolling();
  loadColumnState();
  loadPageSize();
  buildColumnsList();
  applyColumnState();
  applyBusinessCellStyles();
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

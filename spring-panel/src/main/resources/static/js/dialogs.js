// panel/static/dialogs.js
(function () {
  const table = document.getElementById('dialogsTable');
  if (!table) return;

  const quickSearch = document.getElementById('dialogQuickSearch');
  const pageSizeSelect = document.getElementById('dialogPageSize');
  const slaWindowSelect = document.getElementById('dialogSlaWindow');
  const sortModeSelect = document.getElementById('dialogSortMode');
  const filtersBtn = document.getElementById('dialogFiltersBtn');
  const columnsBtn = document.getElementById('dialogColumnsBtn');
  const filtersModalEl = document.getElementById('dialogFiltersModal');
  const columnsModalEl = document.getElementById('dialogColumnsModal');
  const detailsModalEl = document.getElementById('dialogDetailsModal');
  const workspaceShell = document.getElementById('dialogsWorkspaceShell');
  const workspaceConversationTitle = document.getElementById('workspaceConversationTitle');
  const workspaceConversationMeta = document.getElementById('workspaceConversationMeta');
  const workspaceMessagesState = document.getElementById('workspaceMessagesState');
  const workspaceMessagesList = document.getElementById('workspaceMessagesList');
  const workspaceMessagesError = document.getElementById('workspaceMessagesError');
  const workspaceMessagesRetry = document.getElementById('workspaceMessagesRetry');
  const workspaceClientState = document.getElementById('workspaceClientState');
  const workspaceClientContent = document.getElementById('workspaceClientContent');
  const workspaceClientError = document.getElementById('workspaceClientError');
  const workspaceHistoryState = document.getElementById('workspaceHistoryState');
  const workspaceHistoryContent = document.getElementById('workspaceHistoryContent');
  const workspaceHistoryError = document.getElementById('workspaceHistoryError');
  const workspaceRelatedEventsState = document.getElementById('workspaceRelatedEventsState');
  const workspaceRelatedEventsContent = document.getElementById('workspaceRelatedEventsContent');
  const workspaceRelatedEventsError = document.getElementById('workspaceRelatedEventsError');
  const workspaceSlaState = document.getElementById('workspaceSlaState');
  const workspaceSlaContent = document.getElementById('workspaceSlaContent');
  const workspaceSlaError = document.getElementById('workspaceSlaError');
  const workspaceReadonlyBanner = document.getElementById('workspaceReadonlyBanner');
  const workspaceComposerText = document.getElementById('workspaceComposerText');
  const workspaceComposerSend = document.getElementById('workspaceComposerSend');
  const workspaceComposerSaveDraft = document.getElementById('workspaceComposerSaveDraft');
  const workspaceComposerDraftState = document.getElementById('workspaceComposerDraftState');

  const filtersForm = document.getElementById('dialogFiltersForm');
  const filtersApply = document.getElementById('dialogFiltersApply');
  const filtersReset = document.getElementById('dialogFiltersReset');
  const statusFilter = document.getElementById('dialogFilterStatus');
  const columnsList = document.getElementById('dialogColumnsList');
  const columnsApply = document.getElementById('dialogColumnsApply');
  const columnsReset = document.getElementById('dialogColumnsReset');
  const viewTabs = document.querySelectorAll('[data-dialog-view]');
  const summaryTotal = document.getElementById('dialogsSummaryTotal');
  const summaryPending = document.getElementById('dialogsSummaryPending');
  const summaryResolved = document.getElementById('dialogsSummaryResolved');
  const bulkCount = document.getElementById('dialogBulkCount');
  const bulkTakeBtn = document.getElementById('dialogBulkTakeBtn');
  const bulkSnoozeBtn = document.getElementById('dialogBulkSnoozeBtn');
  const bulkCloseBtn = document.getElementById('dialogBulkCloseBtn');
  const bulkClearBtn = document.getElementById('dialogBulkClearBtn');
  const selectAllCheckbox = document.getElementById('dialogSelectAll');

  const detailsMeta = document.getElementById('dialogDetailsMeta');
  const detailsAvatar = document.getElementById('dialogDetailsAvatar');
  const detailsClientName = document.getElementById('dialogDetailsClientName');
  const detailsClientStatus = document.getElementById('dialogDetailsClientStatus');
  const detailsCategories = document.getElementById('dialogDetailsCategories');
  const detailsUnreadCount = document.getElementById('dialogDetailsUnreadCount');
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
  const dialogFontSizeRange = document.getElementById('dialogFontSizeRange');
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
  const macroTemplatesSection = document.getElementById('dialogMacroTemplatesSection');
  const macroTemplateSelect = document.getElementById('dialogMacroTemplateSelect');
  const macroTemplateSearch = document.getElementById('dialogMacroTemplateSearch');
  const macroTemplatePreview = document.getElementById('dialogMacroTemplatePreview');
  const macroTemplateMeta = document.getElementById('dialogMacroTemplateMeta');
  const macroTemplateApply = document.getElementById('dialogMacroTemplateApply');
  const macroTemplateEmpty = document.getElementById('dialogMacroTemplateEmpty');
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

  const STORAGE_COLUMNS = 'iguana:dialogs:columns';
  const STORAGE_WIDTHS = 'iguana:dialogs:column-widths';
  const STORAGE_TASK = 'iguana:dialogs:create-task';
  const STORAGE_PAGE_SIZE = 'iguana:dialogs:page-size';
  const STORAGE_DIALOG_FONT = 'iguana:dialogs:font-size';
  const STORAGE_SNOOZED = 'iguana:dialogs:snoozed';
  const HISTORY_POLL_INTERVAL = 8000;
  const LIST_POLL_INTERVAL = 8000;
  const DEFAULT_PAGE_SIZE = 20;
  const DEFAULT_DIALOG_FONT = 14;
  const DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
  const DEFAULT_SLA_WARNING_MINUTES = 4 * 60;

  function normalizeSlaMinutes(value, fallbackValue) {
    const parsed = Number.parseInt(value, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return fallbackValue;
    }
    return parsed;
  }

  const SLA_TARGET_MINUTES = normalizeSlaMinutes(
    window.DIALOG_CONFIG?.sla_target_minutes,
    DEFAULT_SLA_TARGET_MINUTES,
  );
  const SLA_WARNING_MINUTES = Math.min(
    normalizeSlaMinutes(window.DIALOG_CONFIG?.sla_warning_minutes, DEFAULT_SLA_WARNING_MINUTES),
    SLA_TARGET_MINUTES,
  );
  const SLA_CRITICAL_MINUTES = Math.min(
    normalizeSlaMinutes(window.DIALOG_CONFIG?.sla_critical_minutes, 30),
    SLA_TARGET_MINUTES,
  );
  const AUTO_SLA_PRIORITY_FOR_CRITICAL_VIEW = window.DIALOG_CONFIG?.sla_critical_auto_sort !== false;
  const WORKSPACE_V1_ENABLED = Boolean(window.DIALOG_CONFIG?.workspace_v1);
  const WORKSPACE_INLINE_NAVIGATION = window.DIALOG_CONFIG?.workspace_inline_navigation !== false;
  const DEFAULT_OPERATOR_PERMISSIONS = Object.freeze({
    can_assign: true,
    can_snooze: true,
    can_close: true,
    can_bulk: true,
  });
  const CONFIG_OPERATOR_PERMISSIONS = (window.DIALOG_CONFIG?.operator_permissions && typeof window.DIALOG_CONFIG.operator_permissions === 'object')
    ? window.DIALOG_CONFIG.operator_permissions
    : {};
  const OPERATOR_PERMISSIONS = Object.freeze({
    ...DEFAULT_OPERATOR_PERMISSIONS,
    ...CONFIG_OPERATOR_PERMISSIONS,
  });
  const INITIAL_DIALOG_TICKET_ID = String(document.body?.dataset?.initialDialogTicketId || '').trim();
  const WORKSPACE_OPEN_SLO_MS = 2000;
  const WORKSPACE_MUTATING_PERMISSION_KEYS = Object.freeze(['can_assign', 'can_close', 'can_snooze', 'can_bulk']);
  const WORKSPACE_AB_TEST_CONFIG = Object.freeze({
    experimentName: String(window.DIALOG_CONFIG?.workspace_ab_experiment_name || 'workspace_v1_rollout').trim() || 'workspace_v1_rollout',
    enabled: Boolean(window.DIALOG_CONFIG?.workspace_ab_enabled),
    rolloutPercent: Math.max(0, Math.min(100, Number(window.DIALOG_CONFIG?.workspace_ab_rollout_percent) || 0)),
  });
  const STORAGE_WORKSPACE_AB = 'iguana:dialogs:workspace-ab-cohort';
  const STORAGE_WORKSPACE_DRAFT_PREFIX = 'iguana:dialogs:workspace-draft:';
  const DIALOGS_TELEMETRY_EVENT_GROUPS = Object.freeze({
    workspace_open_ms: 'performance',
    workspace_render_error: 'stability',
    workspace_fallback_to_legacy: 'stability',
    workspace_abandon: 'engagement',
    workspace_experiment_exposure: 'experiment',
    macro_preview: 'macro',
    macro_apply: 'macro',
  });
  let workspaceReadonlyMode = false;
  let macroTemplatesCache = [];
  let workspaceComposerTicketId = '';
  let activeWorkspaceTicketId = INITIAL_DIALOG_TICKET_ID;


  const BUSINESS_STYLES = (window.BUSINESS_CELL_STYLES && typeof window.BUSINESS_CELL_STYLES === 'object')
    ? window.BUSINESS_CELL_STYLES
    : {};

  const SUMMARY_BADGE_SETTINGS = (window.DIALOG_CONFIG?.summary_badges && typeof window.DIALOG_CONFIG.summary_badges === 'object')
    ? window.DIALOG_CONFIG.summary_badges
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

  function canRunAction(permissionKey) {
    if (!permissionKey) return true;
    if (workspaceReadonlyMode && WORKSPACE_MUTATING_PERMISSION_KEYS.includes(permissionKey)) {
      return false;
    }
    return OPERATOR_PERMISSIONS[permissionKey] !== false;
  }

  function setWorkspaceReadonlyMode(isReadonly, reasonText) {
    const nextState = Boolean(isReadonly);
    const stateChanged = workspaceReadonlyMode !== nextState;
    workspaceReadonlyMode = nextState;
    if (workspaceReadonlyBanner) {
      workspaceReadonlyBanner.classList.toggle('d-none', !nextState);
      if (nextState && reasonText) {
        workspaceReadonlyBanner.textContent = String(reasonText);
      }
    }
    if (stateChanged) {
      updateBulkToolbarState();
      renderTableFromRows(true);
    }
  }

  function resolveWorkspaceReadonlyReason(permissions) {
    if (!permissions || typeof permissions !== 'object') {
      return 'Workspace открыт в режиме только чтения: не удалось получить права оператора.';
    }
    const requiredFlags = ['can_reply', ...WORKSPACE_MUTATING_PERMISSION_KEYS];
    const hasInvalidFlag = requiredFlags.some((flag) => typeof permissions[flag] !== 'boolean');
    if (hasInvalidFlag) {
      return 'Workspace открыт в режиме только чтения: права оператора загружены некорректно.';
    }
    const hasMutatingPermission = WORKSPACE_MUTATING_PERMISSION_KEYS.some((flag) => permissions[flag] === true);
    if (!hasMutatingPermission) {
      return 'Workspace открыт в режиме только чтения: действия изменения отключены политикой доступа.';
    }
    return null;
  }

  function notifyPermissionDenied(actionTitle) {
    if (typeof showNotification === 'function') {
      showNotification(`Недостаточно прав для действия «${actionTitle}».`, 'warning');
    }
  }

  function loadSnoozedDialogs() {
    try {
      const raw = localStorage.getItem(STORAGE_SNOOZED);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      if (!parsed || typeof parsed !== 'object') return {};
      const now = Date.now();
      const cleaned = Object.fromEntries(Object.entries(parsed).filter(([, value]) => Number(value) > now));
      if (Object.keys(cleaned).length !== Object.keys(parsed).length) {
        localStorage.setItem(STORAGE_SNOOZED, JSON.stringify(cleaned));
      }
      return cleaned;
    } catch (_error) {
      return {};
    }
  }

  function saveSnoozedDialogs(state) {
    localStorage.setItem(STORAGE_SNOOZED, JSON.stringify(state || {}));
  }

  const snoozedDialogs = loadSnoozedDialogs();

  function getSnoozeUntil(ticketId) {
    const value = snoozedDialogs[String(ticketId || '')];
    return Number(value) > Date.now() ? Number(value) : null;
  }

  function setSnooze(ticketId, minutes) {
    if (!ticketId) return;
    snoozedDialogs[String(ticketId)] = Date.now() + Math.max(1, Number(minutes) || 60) * 60 * 1000;
    saveSnoozedDialogs(snoozedDialogs);
  }

  function clearSnooze(ticketId) {
    if (!ticketId) return;
    delete snoozedDialogs[String(ticketId)];
    saveSnoozedDialogs(snoozedDialogs);
  }

  const DIALOG_TEMPLATES = {
    categoryTemplates: Array.isArray(window.DIALOG_CONFIG?.category_templates)
      ? window.DIALOG_CONFIG.category_templates
      : [],
    questionTemplates: Array.isArray(window.DIALOG_CONFIG?.question_templates)
      ? window.DIALOG_CONFIG.question_templates
      : [],
    macroTemplates: Array.isArray(window.DIALOG_CONFIG?.macro_templates)
      ? window.DIALOG_CONFIG.macro_templates
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
  const selectedTicketIds = new Set();
  let mediaImageScale = 1;
  let categorySaveTimer = null;
  let activeMacroTemplate = null;
  let activeMacroMeta = null;
  const workspaceOpenTimers = new Map();
  const workspaceFirstInteractionTickets = new Set();
  const workspaceExperimentContext = resolveWorkspaceExperimentContext();

  function resolveWorkspaceExperimentContext() {
    if (!WORKSPACE_AB_TEST_CONFIG.enabled || WORKSPACE_AB_TEST_CONFIG.rolloutPercent <= 0) {
      return {
        experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
        cohort: 'disabled',
      };
    }
    let cached = null;
    try {
      cached = localStorage.getItem(STORAGE_WORKSPACE_AB);
    } catch (_error) {
      cached = null;
    }
    if (cached === 'test' || cached === 'control') {
      return {
        experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
        cohort: cached,
      };
    }
    const cohort = Math.random() * 100 < WORKSPACE_AB_TEST_CONFIG.rolloutPercent ? 'test' : 'control';
    try {
      localStorage.setItem(STORAGE_WORKSPACE_AB, cohort);
    } catch (_error) {
      // noop: cohort persistence is best-effort only
    }
    return {
      experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
      cohort,
    };
  }

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

  function applyDialogFontSize(value) {
    const parsed = Number.parseInt(value, 10);
    const size = Number.isFinite(parsed) ? parsed : DEFAULT_DIALOG_FONT;
    if (detailsHistory) {
      detailsHistory.style.setProperty('--dialog-font-size', `${size}px`);
    }
    if (dialogFontSizeRange) {
      dialogFontSizeRange.value = String(size);
    }
    localStorage.setItem(STORAGE_DIALOG_FONT, String(size));
  }

  function loadDialogFontSize() {
    const stored = Number.parseInt(localStorage.getItem(STORAGE_DIALOG_FONT), 10);
    const size = Number.isFinite(stored) ? stored : DEFAULT_DIALOG_FONT;
    applyDialogFontSize(size);
  }

  function formatCategoriesLabel(categories) {
    const normalized = Array.isArray(categories)
      ? categories.map((item) => String(item || '').trim()).filter(Boolean)
      : [];
    return normalized.length ? normalized.join(', ') : '—';
  }

  function normalizeCategories(value) {
    if (Array.isArray(value)) {
      return value.map((item) => String(item || '').trim()).filter((item) => item && item !== '—');
    }
    const normalized = String(value || '').trim();
    if (!normalized || normalized === '—') return [];
    return normalized
      .split(',')
      .map((item) => item.trim())
      .filter((item) => item && item !== '—');
  }

  function categoryBadgePalette(label) {
    const text = String(label || '');
    let hash = 0;
    for (let i = 0; i < text.length; i += 1) {
      hash = (hash * 31 + text.charCodeAt(i)) % 360;
    }
    const hue = hash;
    return {
      background: `hsl(${hue} 70% 92%)`,
      text: `hsl(${hue} 45% 28%)`,
    };
  }

  function renderCategoryBadges(categories) {
    const list = normalizeCategories(categories);
    if (!list.length) {
      return '<span class="text-muted">—</span>';
    }
    const badges = list.map((category) => {
      const palette = categoryBadgePalette(category);
      return `
        <span class="dialog-category-chip" style="background-color: ${palette.background}; color: ${palette.text};">
          ${escapeHtml(category)}
        </span>
      `;
    }).join('');
    return `<span class="dialog-category-list">${badges}</span>`;
  }

  function updateSummaryCategories(label) {
    if (detailsCategories) {
      detailsCategories.innerHTML = `
        <span>Категории:</span>
        ${renderCategoryBadges(label)}
      `;
    }
    if (detailsSummary) {
      const summaryValue = detailsSummary.querySelector('[data-summary-field="categories"] [data-summary-value]');
      if (summaryValue) {
        summaryValue.innerHTML = renderCategoryBadges(label);
      }
    }
    if (activeDialogRow) {
      const rowLabel = label || '—';
      activeDialogRow.dataset.categories = rowLabel;
      const categoriesIndex = table.querySelector('th[data-column-key="categories"]')?.cellIndex ?? -1;
      if (categoriesIndex >= 0 && activeDialogRow.children[categoriesIndex]) {
        activeDialogRow.children[categoriesIndex].textContent = rowLabel;
      }
    }
  }

  async function persistDialogCategories(categories) {
    if (!activeDialogTicketId) return;
    const payload = { categories };
    const resp = await fetch(`/api/dialogs/${encodeURIComponent(activeDialogTicketId)}/categories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await resp.json();
    if (!resp.ok || !data?.success) {
      throw new Error(data?.error || `Ошибка ${resp.status}`);
    }
  }

  function scheduleCategorySave() {
    if (!activeDialogTicketId) return;
    if (categorySaveTimer) {
      clearTimeout(categorySaveTimer);
    }
    categorySaveTimer = setTimeout(async () => {
      try {
        const categories = Array.from(selectedCategories);
        await persistDialogCategories(categories);
        updateSummaryCategories(formatCategoriesLabel(categories));
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось сохранить категории', 'error');
        }
      }
    }, 400);
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
    if (normalizedKey === 'auto_closed') return 'dialog-status-badge status-auto-closed';
    if (normalizedKey === 'closed') return 'dialog-status-badge status-closed';
    if (normalizedKey === 'waiting_operator') return 'dialog-status-badge status-waiting-operator';
    if (normalizedKey === 'waiting_client') return 'dialog-status-badge status-waiting-client';
    return 'dialog-status-badge status-new';
  }

  function isResolvedStatusKey(statusKey) {
    const normalized = String(statusKey || '').toLowerCase();
    return normalized === 'closed' || normalized === 'auto_closed';
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

  function buildAvatarUrl(userId) {
    const normalized = String(userId || '').trim();
    if (!normalized) return '';
    return `/avatar/${encodeURIComponent(normalized)}?strict=1`;
  }

  function bindAvatar(container, userId, name) {
    if (!container) return;
    const img = container.querySelector('[data-avatar-img]');
    const initialEl = container.querySelector('[data-avatar-initial]');
    if (initialEl) {
      initialEl.textContent = avatarInitial(name);
      initialEl.classList.remove('d-none');
    }
    if (!img) return;
    img.classList.add('d-none');
    if (!userId) {
      return;
    }
    const src = buildAvatarUrl(userId);
    if (!src) return;
    img.onload = () => {
      img.classList.remove('d-none');
      if (initialEl) initialEl.classList.add('d-none');
    };
    img.onerror = () => {
      img.classList.add('d-none');
      if (initialEl) initialEl.classList.remove('d-none');
    };
    img.src = src;
  }

  function hydrateAvatars(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-avatar-user-id]').forEach((container) => {
      bindAvatar(container, container.dataset.avatarUserId, container.dataset.avatarName);
    });
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

  function formatDurationMinutes(totalMinutes) {
    const safeValue = Math.max(0, Math.floor(totalMinutes));
    const hours = Math.floor(safeValue / 60);
    const minutes = safeValue % 60;
    if (hours > 0) {
      return `${hours}ч ${minutes}м`;
    }
    return `${minutes}м`;
  }

  function computeSlaState(row) {
    if (!row || isResolved(row)) {
      return { label: 'Закрыт', className: 'dialog-sla-closed', title: 'SLA не применяется к закрытому обращению' };
    }
    const createdAtRaw = String(row.dataset.createdAt || '').trim();
    const createdAt = new Date(createdAtRaw);
    if (!createdAtRaw || Number.isNaN(createdAt.getTime())) {
      return { label: 'Нет даты', className: 'dialog-sla-closed', title: 'Не удалось определить время создания обращения' };
    }
    const ageMinutes = (Date.now() - createdAt.getTime()) / 60000;
    const minutesLeft = SLA_TARGET_MINUTES - ageMinutes;
    const deadline = new Date(createdAt.getTime() + SLA_TARGET_MINUTES * 60000);
    const deadlineLabel = deadline.toLocaleString('ru-RU');
    if (minutesLeft <= 0) {
      return {
        label: `Просрочен ${formatDurationMinutes(Math.abs(minutesLeft))}`,
        className: 'dialog-sla-overdue',
        title: `SLA просрочен. Дедлайн: ${deadlineLabel}`,
      };
    }
    if (minutesLeft <= SLA_WARNING_MINUTES) {
      return {
        label: `Риск ${formatDurationMinutes(minutesLeft)}`,
        className: 'dialog-sla-risk',
        title: `До дедлайна SLA: ${formatDurationMinutes(minutesLeft)} (дедлайн: ${deadlineLabel})`,
      };
    }
    return {
      label: `До SLA ${formatDurationMinutes(minutesLeft)}`,
      className: 'dialog-sla-safe',
      title: `До дедлайна SLA: ${formatDurationMinutes(minutesLeft)} (дедлайн: ${deadlineLabel})`,
    };
  }

  function updateRowSlaBadge(row) {
    if (!row) return;
    const slaCell = row.querySelector('.dialog-sla-cell');
    if (!slaCell) return;
    const badge = slaCell.querySelector('.dialog-sla-badge');
    if (!badge) return;
    const state = computeSlaState(row);
    badge.className = `badge rounded-pill dialog-sla-badge ${state.className}`;
    badge.textContent = state.label;
    badge.title = state.title || '';
  }

  function updateAllSlaBadges() {
    rowsList().forEach((row) => { updateRowSlaBadge(row); updateRowQuickActions(row); });
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
    const hasResponsible = Boolean(String(responsible || '').trim() && responsible !== '—');
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
        <td class="dialog-select-column">
          <input class="form-check-input dialog-row-select" type="checkbox" data-ticket-id="${escapeHtml(ticketId)}" aria-label="Выбрать диалог">
        </td>
        <td>${escapeHtml(displayNumber)}</td>
        <td>
          <div class="d-flex align-items-center gap-2">
            <div class="dialog-avatar" data-avatar-user-id="${escapeHtml(item?.userId || '')}"
                 data-avatar-name="${escapeHtml(clientName)}">
              <img class="dialog-avatar-img d-none" alt="Аватар клиента" data-avatar-img>
              <span class="avatar-circle" data-avatar-initial>${escapeHtml(avatarInitial(clientName))}</span>
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
        <td class="dialog-sla-cell">
          <span class="badge rounded-pill dialog-sla-badge">—</span>
        </td>
        <td class="dialog-actions">
          <a href="#" class="btn btn-sm btn-outline-primary dialog-open-btn" data-ticket-id="${escapeHtml(ticketId)}">Открыть</a>
          <button type="button" class="btn btn-sm btn-outline-success dialog-take-btn ${hasResponsible || !canRunAction('can_assign') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Назначить мне</button>
          <button type="button" class="btn btn-sm btn-outline-warning dialog-snooze-btn ${isResolvedStatusKey(statusKey) || !canRunAction('can_snooze') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Отложить 1ч</button>
          <button type="button" class="btn btn-sm btn-outline-danger dialog-close-btn ${isResolvedStatusKey(statusKey) || !canRunAction('can_close') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Закрыть</button>
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

  function updateRowResponsible(row, responsible) {
    if (!row) return;
    const value = String(responsible || '').trim();
    row.dataset.responsible = value;
    const responsibleIndex = table.querySelector('th[data-column-key="responsible"]')?.cellIndex ?? -1;
    const rowCells = row.children;
    if (responsibleIndex >= 0 && rowCells[responsibleIndex]) {
      rowCells[responsibleIndex].textContent = value || '—';
    }
    const takeBtn = row.querySelector('.dialog-take-btn');
    if (takeBtn) {
      takeBtn.classList.toggle('d-none', Boolean(value) || !canRunAction('can_assign'));
    }
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
    const isClosed = isResolved(row);
    const closeBtn = row.querySelector('.dialog-close-btn');
    const snoozeBtn = row.querySelector('.dialog-snooze-btn');
    const takeBtn = row.querySelector('.dialog-take-btn');
    const hasResponsible = Boolean(String(row.dataset.responsible || '').trim());
    if (takeBtn) takeBtn.classList.toggle('d-none', hasResponsible || !canRunAction('can_assign'));
    if (closeBtn) closeBtn.classList.toggle('d-none', isClosed || !canRunAction('can_close'));
    if (snoozeBtn) {
      snoozeBtn.classList.toggle('d-none', isClosed || !canRunAction('can_snooze'));
      const ticketId = row.dataset.ticketId;
      const until = getSnoozeUntil(ticketId);
      snoozeBtn.textContent = until ? `Отложен до ${new Date(until).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}` : 'Отложить 1ч';
    }
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
    updateRowStatus(row, 'resolved', 'Закрыт', 'closed', 0);
    updateRowSlaBadge(row);
    updateRowQuickActions(row);
    applyFilters();
  }

  async function takeDialog(ticketId, row, triggerButton) {
    if (!ticketId) return;
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
      updateRowResponsible(row, data.responsible || '');
      applyFilters();
      if (typeof showNotification === 'function') {
        showNotification('Диалог назначен на вас', 'success');
      }
    } catch (error) {
      if (btn) btn.disabled = false;
      if (typeof showNotification === 'function') {
        showNotification(error.message || 'Не удалось взять диалог', 'error');
      }
    }
  }

  async function snoozeDialog(ticketId, minutes, triggerButton) {
    if (!ticketId) return;
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
    } finally {
      if (btn) btn.disabled = false;
    }
  }

  function syncDialogsTable(dialogs) {
    const tbody = table.tBodies[0];
    if (!tbody) return;
    tbody.innerHTML = Array.isArray(dialogs) && dialogs.length
      ? dialogs.map((item) => renderDialogRow(item)).join('')
      : '';
    hydrateAvatars(tbody);
    applyColumnState();
    applyBusinessCellStyles();
    restoreColumnWidths();
    updateAllSlaBadges();
    const availableTicketIds = new Set(rowsList().map((row) => String(row.dataset.ticketId || '')));
    Array.from(selectedTicketIds).forEach((ticketId) => {
      if (!availableTicketIds.has(ticketId)) {
        selectedTicketIds.delete(ticketId);
      }
    });
    applyFilters();
    rowsList().forEach((row) => updateRowQuickActions(row));

    if (activeDialogTicketId) {
      const selector = `.dialog-open-btn[data-ticket-id="${escapeSelectorValue(activeDialogTicketId)}"]`;
      const openBtn = table.querySelector(selector);
      setActiveDialogRow(openBtn ? openBtn.closest('tr') : null);
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
  emptyRow.innerHTML = '<td colspan="13" class="text-center text-muted py-4">Нет результатов</td>';
  emptyRow.classList.add('d-none');

  function ensureEmptyRow() {
    if (!table.tBodies[0].contains(emptyRow)) {
      table.tBodies[0].appendChild(emptyRow);
    }
  }

  const filterState = {
    search: '',
    status: '',
    view: 'all',
    pageSize: DEFAULT_PAGE_SIZE,
    slaWindowMinutes: null,
    sortMode: 'default',
  };
  let lastManualSortMode = filterState.sortMode;

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
    if (minutesLeft <= SLA_WARNING_MINUTES) {
      return { bucket: 1, minutesLeft, state: 'at_risk' };
    }
    return { bucket: 2, minutesLeft, state: 'normal' };
  }

  function applySlaPriorityClass(row) {
    if (!row) return;
    row.classList.remove('dialog-priority-breached', 'dialog-priority-at-risk', 'dialog-priority-normal', 'dialog-priority-pinned');
    const priority = resolveSlaPriority(row);
    const isPinned = isCriticalSlaDialog(row);
    row.dataset.slaPinned = isPinned ? 'true' : 'false';
    row.classList.toggle('dialog-priority-pinned', isPinned);
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
    const leftCreated = new Date(String(left?.dataset?.createdAt || '')).getTime();
    const rightCreated = new Date(String(right?.dataset?.createdAt || '')).getTime();
    if (Number.isFinite(leftCreated) && Number.isFinite(rightCreated) && leftCreated !== rightCreated) {
      return leftCreated - rightCreated;
    }
    return String(left?.dataset?.ticketId || '').localeCompare(String(right?.dataset?.ticketId || ''), 'ru');
  }

  function isNewDialog(row) {
    const key = String(row.dataset.statusKey || '').toLowerCase();
    const raw = String(row.dataset.statusRaw || '').toLowerCase();
    const label = String(row.dataset.status || '').toLowerCase();
    return key === 'new' || raw === 'new' || label === 'новый';
  }

  function isUnassignedDialog(row) {
    const responsible = String(row.dataset.responsible || '').trim().toLowerCase();
    return !responsible || responsible === '—' || responsible === '-';
  }

  function isOverdueDialog(row) {
    if (isResolved(row)) return false;
    const createdAtRaw = String(row.dataset.createdAt || '').trim();
    if (!createdAtRaw) return false;
    const createdAt = new Date(createdAtRaw);
    if (Number.isNaN(createdAt.getTime())) return false;
    const overdueThresholdMs = 24 * 60 * 60 * 1000;
    return Date.now() - createdAt.getTime() > overdueThresholdMs;
  }

  function isSnoozedDialog(row) {
    const ticketId = row?.dataset?.ticketId;
    return Boolean(getSnoozeUntil(ticketId));
  }

  function isCriticalSlaDialog(row) {
    const minutesLeft = resolveSlaMinutesLeft(row);
    if (!Number.isFinite(minutesLeft)) return false;
    return minutesLeft <= SLA_CRITICAL_MINUTES;
  }

  function resolveSlaMinutesLeft(row) {
    if (!row || isResolved(row)) return null;
    const createdAtRaw = String(row.dataset.createdAt || '').trim();
    if (!createdAtRaw) return null;
    const createdAt = new Date(createdAtRaw);
    if (Number.isNaN(createdAt.getTime())) return null;
    return SLA_TARGET_MINUTES - ((Date.now() - createdAt.getTime()) / 60000);
  }

  function matchesSlaReactionWindow(row) {
    if (!Number.isFinite(filterState.slaWindowMinutes) || filterState.slaWindowMinutes <= 0) {
      return true;
    }
    const minutesLeft = resolveSlaMinutesLeft(row);
    if (!Number.isFinite(minutesLeft)) return false;
    return minutesLeft <= filterState.slaWindowMinutes;
  }

  function matchesCurrentView(row) {
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
        return isCriticalSlaDialog(row);
      default:
        return true;
    }
  }

  function isResolved(row) {
    const raw = (row.dataset.statusRaw || '').toLowerCase();
    const label = (row.dataset.status || '').toLowerCase();
    const key = (row.dataset.statusKey || '').toLowerCase();
    return raw === 'resolved' || raw === 'closed' || label.startsWith('закрыт') || key.includes('closed');
  }

  function syncRowSelectionState(row) {
    if (!row) return;
    const ticketId = String(row.dataset.ticketId || '');
    const checkbox = row.querySelector('.dialog-row-select');
    if (!checkbox || !ticketId) return;
    checkbox.checked = selectedTicketIds.has(ticketId);
  }

  function selectedRows() {
    return rowsList().filter((row) => selectedTicketIds.has(String(row.dataset.ticketId || '')));
  }

  function updateSelectAllState() {
    if (!selectAllCheckbox) return;
    const visible = visibleRows().filter((row) => row.querySelector('.dialog-row-select'));
    if (!visible.length) {
      selectAllCheckbox.checked = false;
      selectAllCheckbox.indeterminate = false;
      selectAllCheckbox.disabled = true;
      return;
    }
    const selectedVisible = visible.filter((row) => selectedTicketIds.has(String(row.dataset.ticketId || ''))).length;
    selectAllCheckbox.disabled = false;
    selectAllCheckbox.checked = selectedVisible > 0 && selectedVisible === visible.length;
    selectAllCheckbox.indeterminate = selectedVisible > 0 && selectedVisible < visible.length;
  }

  function updateBulkActionsState() {
    const count = selectedTicketIds.size;
    if (bulkCount) {
      bulkCount.textContent = `Выбрано: ${count}`;
    }
    if (bulkTakeBtn) bulkTakeBtn.disabled = count === 0 || !canRunAction('can_bulk') || !canRunAction('can_assign');
    if (bulkSnoozeBtn) bulkSnoozeBtn.disabled = count === 0 || !canRunAction('can_bulk') || !canRunAction('can_snooze');
    if (bulkCloseBtn) bulkCloseBtn.disabled = count === 0 || !canRunAction('can_bulk') || !canRunAction('can_close');
    if (bulkClearBtn) bulkClearBtn.disabled = count === 0;
    rowsList().forEach((row) => syncRowSelectionState(row));
    updateSelectAllState();
  }

  function clearSelection() {
    selectedTicketIds.clear();
    updateBulkActionsState();
  }

  async function runBulkAction(action) {
    const permissionMap = {
      take: ['can_bulk', 'can_assign', 'Назначить выбранные на меня'],
      snooze: ['can_bulk', 'can_snooze', 'Отложить выбранные на 1ч'],
      close: ['can_bulk', 'can_close', 'Закрыть выбранные'],
    };
    const [bulkPermission, actionPermission, actionTitle] = permissionMap[action] || [];
    if (!canRunAction(bulkPermission) || !canRunAction(actionPermission)) {
      notifyPermissionDenied(actionTitle || 'Групповое действие');
      return;
    }

    const rows = selectedRows();
    if (!rows.length) return;
    const originalDisabled = [bulkTakeBtn, bulkSnoozeBtn, bulkCloseBtn, bulkClearBtn]
      .filter(Boolean)
      .map((button) => ({ button, disabled: button.disabled }));
    originalDisabled.forEach(({ button }) => {
      button.disabled = true;
    });

    const errors = [];
    for (const row of rows) {
      const ticketId = String(row.dataset.ticketId || '');
      if (!ticketId) continue;
      try {
        if (action === 'take') {
          const takeBtn = row.querySelector('.dialog-take-btn:not(.d-none)');
          if (!takeBtn) continue;
          await takeDialog(ticketId, row, takeBtn);
        }
        if (action === 'snooze') {
          const snoozeBtn = row.querySelector('.dialog-snooze-btn:not(.d-none)');
          if (!snoozeBtn) continue;
          await snoozeDialog(ticketId, 60, snoozeBtn);
          setSnooze(ticketId, 60);
          updateRowQuickActions(row);
        }
        if (action === 'close') {
          const closeBtn = row.querySelector('.dialog-close-btn:not(.d-none)');
          if (!closeBtn) continue;
          await closeDialogQuick(ticketId, row, closeBtn);
          clearSnooze(ticketId);
        }
      } catch (error) {
        errors.push(`${ticketId}: ${error.message || 'ошибка'}`);
      }
    }

    applyFilters();
    if (errors.length) {
      if (typeof showNotification === 'function') {
        showNotification(`Часть операций не выполнена (${errors.length}).`, 'error');
      }
      console.warn('Bulk action errors', action, errors);
    } else if (typeof showNotification === 'function') {
      const successMap = {
        take: 'Выбранные диалоги назначены на вас',
        snooze: 'Выбранные диалоги отложены на 1 час',
        close: 'Выбранные диалоги закрыты',
      };
      showNotification(successMap[action] || 'Групповое действие выполнено', 'success');
    }

    clearSelection();
    originalDisabled.forEach(({ button, disabled }) => {
      button.disabled = disabled;
    });
    updateBulkActionsState();
  }

  function applyFilters() {
    updateAllSlaBadges();
    const search = (filterState.search || '').trim().toLowerCase();
    const status = (filterState.status || '').trim().toLowerCase();
    const matchedRows = [];
    const hiddenRows = [];
    rowsList().forEach((row) => {
      const text = collectRowSearchText(row);
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
    const tableBody = table.tBodies[0];
    matchedRows.forEach((row) => tableBody.appendChild(row));
    hiddenRows.forEach((row) => tableBody.appendChild(row));
    const visibleCount = applyPageSize(matchedRows);
    updateZebraRows(matchedRows);
    ensureEmptyRow();
    emptyRow.classList.toggle('d-none', visibleCount !== 0);
    updateBulkActionsState();
  }

  function applyQuickSearch(value) {
    filterState.search = value || '';
    applyFilters();
  }

  function applyStatusFilter(statusLabel = '') {
    const normalized = String(statusLabel || '').trim().toLowerCase();
    filterState.status = statusLabel;
    if (statusFilter) {
      const options = Array.from(statusFilter.options || []);
      const match = options.find((option) => String(option.value || '').trim().toLowerCase() === normalized);
      statusFilter.value = match ? match.value : '';
    }
    applyFilters();
  }

  function setViewTab(nextView) {
    const resolvedView = nextView || 'all';
    const isEnteringCriticalView = resolvedView === 'sla_critical';
    const isLeavingCriticalView = filterState.view === 'sla_critical' && resolvedView !== 'sla_critical';

    if (AUTO_SLA_PRIORITY_FOR_CRITICAL_VIEW && isEnteringCriticalView) {
      if (filterState.sortMode !== 'sla_priority') {
        lastManualSortMode = filterState.sortMode;
      }
      filterState.sortMode = 'sla_priority';
      if (sortModeSelect) {
        sortModeSelect.value = 'sla_priority';
      }
    } else if (AUTO_SLA_PRIORITY_FOR_CRITICAL_VIEW && isLeavingCriticalView) {
      filterState.sortMode = lastManualSortMode === 'sla_priority' ? 'default' : lastManualSortMode;
      if (sortModeSelect) {
        sortModeSelect.value = filterState.sortMode;
      }
    }

    filterState.view = resolvedView;
    viewTabs.forEach((tab) => {
      tab.classList.toggle('active', tab.dataset.dialogView === filterState.view);
    });
    applyFilters();
  }

  function visibleRows() {
    return rowsList().filter((row) => !row.classList.contains('d-none'));
  }

  function setActiveDialogRow(row, options = {}) {
    const nextRow = row && row.tagName === 'TR' ? row : null;
    if (activeDialogRow && activeDialogRow !== nextRow) {
      activeDialogRow.classList.remove('dialog-row-active');
    }
    activeDialogRow = nextRow;
    if (activeDialogRow) {
      activeDialogRow.classList.add('dialog-row-active');
      if (options.ensureVisible) {
        activeDialogRow.scrollIntoView({ block: 'nearest', inline: 'nearest' });
      }
    }
  }

  function getShortcutTargetRow() {
    if (activeDialogRow && !activeDialogRow.classList.contains('d-none')) {
      return activeDialogRow;
    }
    return visibleRows()[0] || null;
  }

  function openVisibleDialogByOffset(offset) {
    const rows = visibleRows();
    if (!rows.length) return;
    const currentIndex = activeDialogRow ? rows.indexOf(activeDialogRow) : -1;
    let nextIndex = currentIndex + offset;
    if (nextIndex < 0) nextIndex = rows.length - 1;
    if (nextIndex >= rows.length) nextIndex = 0;
    const nextRow = rows[nextIndex];
    const openButton = nextRow?.querySelector('.dialog-open-btn');
    const ticketId = openButton?.dataset?.ticketId || nextRow?.dataset?.ticketId;
    if (!ticketId) return;
    setActiveDialogRow(nextRow, { ensureVisible: true });
    openDialogEntry(ticketId, nextRow);
  }

  function buildWorkspaceDialogUrl(ticketId, channelId) {
    if (!ticketId) return '/dialogs';
    const basePath = `/dialogs/${encodeURIComponent(ticketId)}`;
    return withChannelParam(basePath, channelId);
  }

  function isWorkspaceDialogPath(pathname) {
    return /^\/dialogs\/[^/]+/.test(String(pathname || ''));
  }

  function startWorkspaceOpenTimer(ticketId) {
    if (!ticketId) return;
    workspaceOpenTimers.set(String(ticketId), performance.now());
  }

  function finishWorkspaceOpenTimer(ticketId) {
    const key = String(ticketId || '');
    const startedAt = workspaceOpenTimers.get(key);
    if (!Number.isFinite(startedAt)) return null;
    workspaceOpenTimers.delete(key);
    const elapsedMs = Math.max(0, Math.round(performance.now() - startedAt));
    return elapsedMs;
  }

  async function emitWorkspaceTelemetry(eventType, payload = {}) {
    if (!eventType) return;
    const eventGroup = DIALOGS_TELEMETRY_EVENT_GROUPS[eventType] || null;
    const body = {
      event_type: String(eventType),
      event_group: eventGroup,
      timestamp: new Date().toISOString(),
      ticket_id: payload.ticketId || null,
      reason: payload.reason || null,
      error_code: payload.errorCode || null,
      contract_version: payload.contractVersion || null,
      duration_ms: Number.isFinite(payload.durationMs) ? payload.durationMs : null,
      experiment_name: workspaceExperimentContext.experimentName,
      experiment_cohort: workspaceExperimentContext.cohort,
      template_id: payload.templateId || null,
      template_name: payload.templateName || null,
    };
    try {
      await fetch('/api/dialogs/workspace-telemetry', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    } catch (_error) {
      // no-op: telemetry should never break operator flow
    }
  }

  function isValidWorkspaceContract(payload) {
    const version = String(payload?.contract_version || '').trim();
    const ticketId = String(payload?.conversation?.ticketId || '').trim();
    const status = String(payload?.conversation?.statusKey || payload?.conversation?.status || '').trim();
    const permissions = payload?.permissions;
    const slaState = String(payload?.sla?.state || '').trim();
    const hasPermissions = Boolean(permissions && typeof permissions === 'object');
    return version === 'workspace.v1' && Boolean(ticketId) && Boolean(status) && hasPermissions && Boolean(slaState);
  }

  function formatWorkspaceDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString('ru-RU');
  }

  function renderWorkspaceSimpleList(items, formatter) {
    const list = Array.isArray(items) ? items : [];
    if (list.length === 0) {
      return '<div class="small text-muted">Пока нет данных.</div>';
    }
    return `<ul class="list-unstyled small mb-0">${list.map((item) => `<li class="mb-2">${formatter(item)}</li>`).join('')}</ul>`;
  }

  function getWorkspaceDraftStorageKey(ticketId) {
    return `${STORAGE_WORKSPACE_DRAFT_PREFIX}${String(ticketId || '').trim()}`;
  }

  function updateWorkspaceDraftState(text) {
    if (!workspaceComposerDraftState) return;
    workspaceComposerDraftState.textContent = text || 'Черновик не сохранён';
  }

  function saveWorkspaceDraft(ticketId, message, options = {}) {
    if (!ticketId || !workspaceComposerText) return;
    const value = String(message || '').trim();
    const storageKey = getWorkspaceDraftStorageKey(ticketId);
    try {
      if (!value) {
        localStorage.removeItem(storageKey);
        updateWorkspaceDraftState('Черновик очищен');
      } else {
        localStorage.setItem(storageKey, value);
        if (!options.silent) {
          updateWorkspaceDraftState(`Черновик сохранён · ${new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`);
        }
      }
    } catch (_error) {
      updateWorkspaceDraftState('Не удалось сохранить черновик');
    }
  }

  function restoreWorkspaceDraft(ticketId) {
    if (!ticketId || !workspaceComposerText) return;
    const storageKey = getWorkspaceDraftStorageKey(ticketId);
    try {
      const stored = localStorage.getItem(storageKey);
      workspaceComposerText.value = stored || '';
      if (stored) {
        updateWorkspaceDraftState('Черновик восстановлен');
      } else {
        updateWorkspaceDraftState('Черновик не сохранён');
      }
    } catch (_error) {
      workspaceComposerText.value = '';
      updateWorkspaceDraftState('Не удалось загрузить черновик');
    }
  }

  function appendWorkspaceMessage(message) {
    if (!workspaceMessagesList) return;
    const author = message.senderName || message.senderRole || 'Оператор';
    const text = message.messageText || message.message || '—';
    const timestamp = formatWorkspaceDateTime(message.sentAt || message.createdAt || new Date().toISOString());
    workspaceMessagesList.classList.remove('d-none');
    workspaceMessagesList.insertAdjacentHTML(
      'beforeend',
      `<article class="workspace-message-item"><div class="workspace-message-meta">${escapeHtml(author)} · ${escapeHtml(timestamp)}</div><div>${escapeHtml(text)}</div></article>`,
    );
    if (workspaceMessagesState) {
      workspaceMessagesState.classList.add('d-none');
      workspaceMessagesState.textContent = '';
    }
  }

  async function sendWorkspaceReply() {
    if (!workspaceComposerText || !workspaceComposerSend || !workspaceComposerTicketId) return;
    const message = workspaceComposerText.value.trim();
    if (!message) return;
    if (workspaceReadonlyMode || workspaceComposerText.disabled) {
      notifyPermissionDenied('Отправка ответа');
      return;
    }
    workspaceComposerSend.disabled = true;
    if (workspaceComposerSaveDraft) workspaceComposerSaveDraft.disabled = true;
    try {
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(workspaceComposerTicketId)}/reply`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
      });
      const data = await resp.json();
      if (!resp.ok || !data?.success) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      workspaceComposerText.value = '';
      saveWorkspaceDraft(workspaceComposerTicketId, '');
      appendWorkspaceMessage({
        senderName: data.responsible || 'Оператор',
        messageText: message,
        sentAt: data.timestamp || new Date().toISOString(),
      });
      if (activeDialogRow) {
        updateRowStatus(activeDialogRow, activeDialogRow.dataset.statusRaw || '', 'ожидает ответа клиента', 'waiting_client', 0);
        updateRowResponsible(activeDialogRow, data.responsible || activeDialogRow.dataset.responsible || '');
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
      workspaceComposerSend.disabled = false;
      if (workspaceComposerSaveDraft) workspaceComposerSaveDraft.disabled = false;
    }
  }

  function renderWorkspaceShell(payload) {
    if (!workspaceShell) return;
    workspaceShell.classList.remove('d-none');

    const conversation = payload?.conversation || {};
    const messages = payload?.messages || {};
    const context = payload?.context || {};
    const sla = payload?.sla || {};
    const permissions = payload?.permissions;

    const readonlyReason = resolveWorkspaceReadonlyReason(permissions);
    setWorkspaceReadonlyMode(Boolean(readonlyReason), readonlyReason);
    workspaceComposerTicketId = String(conversation.ticketId || '').trim();
    restoreWorkspaceDraft(workspaceComposerTicketId);
    const canReplyInWorkspace = permissions && permissions.can_reply === true && !workspaceReadonlyMode;
    if (workspaceComposerText) workspaceComposerText.disabled = !canReplyInWorkspace;
    if (workspaceComposerSend) workspaceComposerSend.disabled = !canReplyInWorkspace;
    if (workspaceComposerSaveDraft) workspaceComposerSaveDraft.disabled = !canReplyInWorkspace;

    if (workspaceConversationTitle) {
      workspaceConversationTitle.textContent = `Диалог #${conversation.ticketId || '—'}`;
    }
    if (workspaceConversationMeta) {
      const status = conversation.statusLabel || conversation.status || '—';
      const assignee = conversation.responsible || 'без ответственного';
      workspaceConversationMeta.textContent = `Статус: ${status} · Ответственный: ${assignee}`;
    }

    if (workspaceMessagesState) {
      workspaceMessagesState.classList.toggle('d-none', Array.isArray(messages.items) && messages.items.length > 0);
      workspaceMessagesState.textContent = Array.isArray(messages.items) && messages.items.length > 0
        ? ''
        : 'Сообщения отсутствуют или ещё не загружены.';
    }
    if (workspaceMessagesList) {
      const items = Array.isArray(messages.items) ? messages.items : [];
      workspaceMessagesList.classList.toggle('d-none', items.length === 0);
      workspaceMessagesList.innerHTML = items.map((message) => {
        const author = message.senderName || message.senderRole || 'Участник';
        const text = message.messageText || message.message || '—';
        const timestamp = formatWorkspaceDateTime(message.sentAt || message.createdAt);
        return `<article class="workspace-message-item"><div class="workspace-message-meta">${escapeHtml(author)} · ${escapeHtml(timestamp)}</div><div>${escapeHtml(text)}</div></article>`;
      }).join('');
    }
    if (workspaceMessagesError) {
      workspaceMessagesError.classList.add('d-none');
    }

    const client = context.client;
    if (client && Object.keys(client).length) {
      if (workspaceClientState) workspaceClientState.classList.add('d-none');
      if (workspaceClientError) workspaceClientError.classList.add('d-none');
      if (workspaceClientContent) {
        workspaceClientContent.classList.remove('d-none');
        workspaceClientContent.innerHTML = `<div class="small"><strong>${escapeHtml(client.name || '—')}</strong></div><div class="small text-muted">ID: ${escapeHtml(client.id || '—')}</div><div class="small text-muted">Язык: ${escapeHtml(client.language || '—')}</div>`;
      }
    } else {
      if (workspaceClientState) workspaceClientState.classList.add('d-none');
      if (workspaceClientContent) workspaceClientContent.classList.add('d-none');
      if (workspaceClientError) workspaceClientError.classList.remove('d-none');
    }

    const contextHistory = Array.isArray(context.history) ? context.history : null;
    if (contextHistory) {
      if (workspaceHistoryState) workspaceHistoryState.classList.add('d-none');
      if (workspaceHistoryError) workspaceHistoryError.classList.add('d-none');
      if (workspaceHistoryContent) {
        workspaceHistoryContent.classList.remove('d-none');
        workspaceHistoryContent.innerHTML = renderWorkspaceSimpleList(contextHistory, (item) => {
          const ticketId = escapeHtml(item.ticket_id || item.ticketId || '—');
          const status = escapeHtml(item.status || '—');
          const createdAt = escapeHtml(formatWorkspaceDateTime(item.created_at || item.createdAt));
          const problem = escapeHtml(item.problem || 'Без описания');
          return `<div><strong>#${ticketId}</strong> · <span class="text-muted">${status}</span></div><div class="text-muted">${createdAt}</div><div>${problem}</div>`;
        });
      }
    } else {
      if (workspaceHistoryState) workspaceHistoryState.classList.add('d-none');
      if (workspaceHistoryContent) workspaceHistoryContent.classList.add('d-none');
      if (workspaceHistoryError) workspaceHistoryError.classList.remove('d-none');
    }

    const relatedEvents = Array.isArray(context.related_events) ? context.related_events : null;
    if (relatedEvents) {
      if (workspaceRelatedEventsState) workspaceRelatedEventsState.classList.add('d-none');
      if (workspaceRelatedEventsError) workspaceRelatedEventsError.classList.add('d-none');
      if (workspaceRelatedEventsContent) {
        workspaceRelatedEventsContent.classList.remove('d-none');
        workspaceRelatedEventsContent.innerHTML = renderWorkspaceSimpleList(relatedEvents, (item) => {
          const actor = escapeHtml(item.actor || 'Система');
          const type = escapeHtml(item.type || 'event');
          const timestamp = escapeHtml(formatWorkspaceDateTime(item.timestamp));
          const detail = escapeHtml(item.detail || '—');
          return `<div><strong>${actor}</strong> · <span class="text-muted">${type}</span></div><div class="text-muted">${timestamp}</div><div>${detail}</div>`;
        });
      }
    } else {
      if (workspaceRelatedEventsState) workspaceRelatedEventsState.classList.add('d-none');
      if (workspaceRelatedEventsContent) workspaceRelatedEventsContent.classList.add('d-none');
      if (workspaceRelatedEventsError) workspaceRelatedEventsError.classList.remove('d-none');
    }

    if (sla && sla.state && sla.state !== 'unknown') {
      if (workspaceSlaState) workspaceSlaState.classList.add('d-none');
      if (workspaceSlaError) workspaceSlaError.classList.add('d-none');
      if (workspaceSlaContent) {
        workspaceSlaContent.classList.remove('d-none');
        workspaceSlaContent.innerHTML = `<div class="small">Состояние: <span class="badge text-bg-secondary">${escapeHtml(sla.state)}</span></div><div class="small text-muted">Дедлайн: ${escapeHtml(formatWorkspaceDateTime(sla.deadline_at))}</div>`;
      }
    } else {
      if (workspaceSlaState) workspaceSlaState.classList.add('d-none');
      if (workspaceSlaContent) workspaceSlaContent.classList.add('d-none');
      if (workspaceSlaError) workspaceSlaError.classList.remove('d-none');
    }
  }

  async function preloadWorkspaceContract(ticketId, channelId) {
    const endpoint = withChannelParam(`/api/dialogs/${encodeURIComponent(ticketId)}/workspace`, channelId);
    const response = await fetch(endpoint, {
      credentials: 'same-origin',
      cache: 'no-store',
    });
    const payload = await response.json();
    if (!response.ok) {
      throw new Error(payload?.error || `workspace_http_${response.status}`);
    }
    const contractVersion = String(payload?.contract_version || '').trim();
    if (contractVersion !== 'workspace.v1') {
      const error = new Error('workspace_version_mismatch');
      error.code = 'version_mismatch';
      error.contractVersion = contractVersion || null;
      throw error;
    }
    if (!isValidWorkspaceContract(payload)) {
      const error = new Error('workspace_invalid_payload');
      error.code = 'invalid_payload';
      error.contractVersion = contractVersion;
      throw error;
    }
    return payload;
  }

  async function reloadWorkspaceForInitialRoute() {
    if (!WORKSPACE_V1_ENABLED || !activeWorkspaceTicketId) return;
    const initialRow = rowsList().find((row) => String(row.dataset.ticketId || '') === activeWorkspaceTicketId) || null;
    if (workspaceMessagesError) workspaceMessagesError.classList.add('d-none');
    if (workspaceMessagesState) {
      workspaceMessagesState.classList.remove('d-none');
      workspaceMessagesState.textContent = 'Повторная загрузка ленты…';
    }
    await openDialogWithWorkspaceFallback(activeWorkspaceTicketId, initialRow, { source: 'initial_route' });
  }

  async function openDialogWithWorkspaceFallback(ticketId, row, options = {}) {
    const source = options.source || 'manual_open';
    const channelId = row?.dataset?.channelId || null;
    startWorkspaceOpenTimer(ticketId);
    try {
      const workspacePayload = await preloadWorkspaceContract(ticketId, channelId);
      const durationMs = finishWorkspaceOpenTimer(ticketId);
      await emitWorkspaceTelemetry('workspace_open_ms', {
        ticketId,
        durationMs,
        contractVersion: workspacePayload?.contract_version || null,
      });
      if (Number.isFinite(durationMs) && durationMs > WORKSPACE_OPEN_SLO_MS) {
        console.warn(`workspace_open_ms degraded for ticket ${ticketId}: ${durationMs}ms > ${WORKSPACE_OPEN_SLO_MS}ms`);
      }
      activeWorkspaceTicketId = String(ticketId || '').trim();
      if (source === 'initial_route' || WORKSPACE_INLINE_NAVIGATION) {
        renderWorkspaceShell(workspacePayload);
        if (source !== 'initial_route') {
          const nextUrl = buildWorkspaceDialogUrl(ticketId, channelId);
          window.history.pushState({ ticketId: activeWorkspaceTicketId }, '', nextUrl);
        }
      } else {
        const nextUrl = buildWorkspaceDialogUrl(ticketId, channelId);
        window.location.assign(nextUrl);
      }
    } catch (error) {
      setWorkspaceReadonlyMode(false);
      const durationMs = finishWorkspaceOpenTimer(ticketId);
      const reason = String(error?.code || error?.message || 'unknown_error');
      await emitWorkspaceTelemetry('workspace_render_error', {
        ticketId,
        durationMs,
        errorCode: reason,
        contractVersion: error?.contractVersion || null,
      });
      await emitWorkspaceTelemetry('workspace_fallback_to_legacy', {
        ticketId,
        reason,
        durationMs,
        contractVersion: error?.contractVersion || null,
      });
      if (source === 'initial_route' && workspaceShell) {
        workspaceShell.classList.remove('d-none');
        if (workspaceMessagesState) {
          workspaceMessagesState.classList.remove('d-none');
          workspaceMessagesState.textContent = 'Workspace временно недоступен, открыт legacy-режим.';
        }
        if (workspaceMessagesError) {
          workspaceMessagesError.classList.remove('d-none');
        }
      }
      await openDialogDetails(ticketId, row);
    }
  }

  function openDialogEntry(ticketId, row) {
    if (!ticketId) return;
    setWorkspaceReadonlyMode(false);
    if (!WORKSPACE_V1_ENABLED) {
      openDialogDetails(ticketId, row);
      return;
    }
    openDialogWithWorkspaceFallback(ticketId, row);
  }

  function isTypingTarget(target) {
    if (!target) return false;
    const tagName = String(target.tagName || '').toLowerCase();
    if (target.isContentEditable) return true;
    return tagName === 'input' || tagName === 'textarea' || tagName === 'select';
  }

  function handleGlobalShortcuts(event) {
    if (event.defaultPrevented || event.repeat) return;
    if (isTypingTarget(event.target)) return;

    if (event.key === '/') {
      if (!quickSearch) return;
      event.preventDefault();
      quickSearch.focus();
      quickSearch.select();
      return;
    }

    if (!event.altKey) return;
    const key = String(event.key || '').toLowerCase();
    const viewMap = {
      '1': 'all',
      '2': 'active',
      '3': 'new',
      '4': 'unassigned',
      '5': 'overdue',
    };

    if (viewMap[key]) {
      event.preventDefault();
      setViewTab(viewMap[key]);
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
      applyStatusFilter(statusShortcutMap[key]);
      return;
    }

    if (event.shiftKey && key === 'a') {
      event.preventDefault();
      if (!canRunAction('can_bulk') || !canRunAction('can_assign')) {
        notifyPermissionDenied('Назначить выбранные на меня');
        return;
      }
      runBulkAction('take');
      return;
    }

    if (event.shiftKey && key === 's') {
      event.preventDefault();
      if (!canRunAction('can_bulk') || !canRunAction('can_snooze')) {
        notifyPermissionDenied('Отложить выбранные на 1ч');
        return;
      }
      runBulkAction('snooze');
      return;
    }

    if (event.shiftKey && key === 'c') {
      event.preventDefault();
      if (!canRunAction('can_bulk') || !canRunAction('can_close')) {
        notifyPermissionDenied('Закрыть выбранные');
        return;
      }
      runBulkAction('close');
      return;
    }

    if (key === 'a') {
      event.preventDefault();
      if (!canRunAction('can_assign')) {
        notifyPermissionDenied('Назначить мне');
        return;
      }
      const row = getShortcutTargetRow();
      const takeBtn = row?.querySelector('.dialog-take-btn:not(.d-none)');
      const ticketId = takeBtn?.dataset.ticketId;
      if (!ticketId || !takeBtn) return;
      setActiveDialogRow(row, { ensureVisible: true });
      takeDialog(ticketId, row, takeBtn)
        .then(() => {
          if (typeof showNotification === 'function') {
            showNotification('Диалог назначен на вас', 'success');
          }
        })
        .catch((error) => {
          if (typeof showNotification === 'function') {
            showNotification(error.message || 'Не удалось назначить диалог', 'error');
          }
        });
      return;
    }

    if (key === 's') {
      event.preventDefault();
      if (!canRunAction('can_snooze')) {
        notifyPermissionDenied('Отложить 1ч');
        return;
      }
      const row = getShortcutTargetRow();
      const snoozeBtn = row?.querySelector('.dialog-snooze-btn:not(.d-none)');
      const ticketId = snoozeBtn?.dataset.ticketId;
      if (!ticketId || !snoozeBtn) return;
      setActiveDialogRow(row, { ensureVisible: true });
      snoozeDialog(ticketId, 60, snoozeBtn)
        .then(() => {
          setSnooze(ticketId, 60);
          updateRowQuickActions(row);
          applyFilters();
          if (typeof showNotification === 'function') {
            showNotification('Диалог отложен на 1 час', 'success');
          }
        })
        .catch((error) => {
          if (typeof showNotification === 'function') {
            showNotification(error.message || 'Не удалось отложить диалог', 'error');
          }
        });
      return;
    }

    if (key === 'c') {
      event.preventDefault();
      if (!canRunAction('can_close')) {
        notifyPermissionDenied('Закрыть');
        return;
      }
      const row = getShortcutTargetRow();
      const closeBtn = row?.querySelector('.dialog-close-btn:not(.d-none)');
      const ticketId = closeBtn?.dataset.ticketId;
      if (!ticketId || !closeBtn) return;
      setActiveDialogRow(row, { ensureVisible: true });
      closeBtn.disabled = true;
      closeDialogQuick(ticketId, row, closeBtn)
        .then(() => {
          clearSnooze(ticketId);
          if (typeof showNotification === 'function') {
            showNotification('Диалог закрыт', 'success');
          }
        })
        .catch((error) => {
          closeBtn.disabled = false;
          if (typeof showNotification === 'function') {
            showNotification(error.message || 'Не удалось закрыть диалог', 'error');
          }
        });
      return;
    }

    if (key === 'r') {
      if (!detailsModalEl?.classList.contains('show')) return;
      event.preventDefault();
      if (detailsResolve && !detailsResolve.disabled && !detailsResolve.classList.contains('d-none')) {
        detailsResolve.click();
        return;
      }
      if (detailsReopen && !detailsReopen.disabled && !detailsReopen.classList.contains('d-none')) {
        detailsReopen.click();
      }
      return;
    }

    if (event.key === 'ArrowDown') {
      event.preventDefault();
      openVisibleDialogByOffset(1);
      return;
    }

    if (event.key === 'ArrowUp') {
      event.preventDefault();
      openVisibleDialogByOffset(-1);
    }
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

  function normalizeSummaryBadgeStyle(value) {
    if (!value) return {};
    if (typeof value === 'string') {
      return { background: sanitizeHexColor(value, ''), text: '' };
    }
    if (typeof value === 'object') {
      return {
        background: sanitizeHexColor(value.background || value.bg || value.fill || '', ''),
        text: sanitizeHexColor(value.text || value.color || value.foreground || '', ''),
      };
    }
    return {};
  }

  function normalizeSummaryBadgeSettings(raw) {
    const fallback = normalizeSummaryBadgeStyle(raw?.default) || {};
    const base = {
      default: {
        background: fallback.background || '#f1f3f5',
        text: fallback.text || '#1f2937',
      },
      status: {},
      channel: {},
    };
    const status = raw?.status && typeof raw.status === 'object' ? raw.status : {};
    const channel = raw?.channel && typeof raw.channel === 'object' ? raw.channel : {};
    Object.entries(status).forEach(([key, value]) => {
      if (!key) return;
      base.status[key.toLowerCase()] = normalizeSummaryBadgeStyle(value);
    });
    Object.entries(channel).forEach(([key, value]) => {
      if (!key) return;
      base.channel[key.toLowerCase()] = normalizeSummaryBadgeStyle(value);
    });
    return base;
  }

  const SUMMARY_BADGE_STYLES = normalizeSummaryBadgeSettings(SUMMARY_BADGE_SETTINGS);

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

  function resolveSummaryBadgeStyle(type, value) {
    const normalized = String(value || '').trim().toLowerCase();
    const base = SUMMARY_BADGE_STYLES.default || {};
    const style = (SUMMARY_BADGE_STYLES[type] && normalized)
      ? SUMMARY_BADGE_STYLES[type][normalized]
      : null;
    return {
      background: style?.background || base.background || '',
      text: style?.text || base.text || '',
    };
  }

  function renderSummaryBadge(value, style) {
    const safeValue = escapeHtml(value || '—');
    const background = style?.background ? `background-color: ${style.background}` : '';
    const color = style?.text ? `color: ${style.text}` : '';
    const inlineStyle = [background, color].filter(Boolean).join('; ');
    const styleAttr = inlineStyle ? ` style="${inlineStyle}"` : '';
    return `<span class="dialog-summary-badge"${styleAttr}>${safeValue}</span>`;
  }

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

  function resolveMacroSearchText(template) {
    const name = String(template?.name || '').trim();
    const tags = Array.isArray(template?.tags) ? template.tags.join(' ') : '';
    const message = String(template?.message || template?.text || '').trim();
    return `${name} ${tags} ${message}`.toLowerCase();
  }

  function filterMacroTemplates(templates, query) {
    const normalizedQuery = String(query || '').trim().toLowerCase();
    if (!normalizedQuery) return templates;
    return templates.filter((template) => resolveMacroSearchText(template).includes(normalizedQuery));
  }

  function renderMacroTemplateOptions(templates) {
    if (!macroTemplateSelect) return;
    const nextTemplates = Array.isArray(templates) ? templates : [];
    buildTemplateOptions(macroTemplateSelect, nextTemplates, 'Макрос');
    const hasOptions = nextTemplates.length > 0;
    if (macroTemplateSelect) {
      macroTemplateSelect.disabled = !hasOptions;
    }
    const selected = hasOptions ? findTemplateByValue(nextTemplates, macroTemplateSelect.value) || nextTemplates[0] : null;
    if (selected && macroTemplateSelect.value !== (selected.id || '')) {
      macroTemplateSelect.value = selected.id || String(nextTemplates.indexOf(selected));
    }
    renderMacroTemplate(selected);
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

  function resolveMacroText(template) {
    if (!template) return '';
    const text = String(template?.message || template?.text || '').trim();
    if (!text) return '';
    const variables = {
      client_name: activeDialogContext.clientName || 'клиент',
      ticket_id: activeDialogTicketId || '—',
      operator_name: activeDialogContext.operatorName || 'оператор',
    };
    return text.replace(/\{\{\s*([a-z0-9_]+)\s*\}\}/gi, (_match, key) => {
      const normalizedKey = String(key || '').toLowerCase();
      return Object.prototype.hasOwnProperty.call(variables, normalizedKey)
        ? String(variables[normalizedKey])
        : '';
    }).trim();
  }

  function renderMacroTemplate(template) {
    if (!macroTemplatePreview || !macroTemplateEmpty || !macroTemplateMeta) return;
    activeMacroTemplate = template || null;
    activeMacroMeta = template
      ? {
        id: String(template?.id || '').trim() || null,
        name: String(template?.name || '').trim() || null,
      }
      : null;
    const message = resolveMacroText(template);
    macroTemplatePreview.textContent = message || 'Выберите макрос для предпросмотра.';
    macroTemplateMeta.innerHTML = '';
    const tags = Array.isArray(template?.tags) ? template.tags.filter(Boolean) : [];
    tags.forEach((tag) => {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-light border';
      badge.textContent = String(tag);
      macroTemplateMeta.appendChild(badge);
    });
    const hasMessage = Boolean(message);
    macroTemplateEmpty.classList.toggle('d-none', hasMessage);
    if (macroTemplateApply) {
      macroTemplateApply.disabled = !hasMessage;
    }
    if (activeMacroMeta && hasMessage) {
      emitWorkspaceTelemetry('macro_preview', {
        ticketId: activeDialogTicketId,
        templateId: activeMacroMeta.id,
        templateName: activeMacroMeta.name,
      });
    }
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


    if (macroTemplatesSection) {
      const templates = DIALOG_TEMPLATES.macroTemplates;
      const hasTemplates = templates.length > 0;
      macroTemplatesCache = templates;
      macroTemplatesSection.classList.toggle('d-none', !hasTemplates);
      if (hasTemplates && macroTemplateSelect) {
        renderMacroTemplateOptions(templates);
        macroTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(templates, macroTemplateSelect.value);
          renderMacroTemplate(selected);
        });
        if (macroTemplateSearch) {
          macroTemplateSearch.addEventListener('input', () => {
            const filteredTemplates = filterMacroTemplates(macroTemplatesCache, macroTemplateSearch.value);
            renderMacroTemplateOptions(filteredTemplates);
          });
        }
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

  function openCategoryPanel() {
    if (!categoryTemplatesSection || categoryTemplatesSection.classList.contains('d-none')) return;
    categoryTemplatesSection.classList.add('is-open');
    categoryTemplatesSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function insertReplyText(value) {
    if (!detailsReplyText || !value) return;
    const existing = detailsReplyText.value.trim();
    detailsReplyText.value = existing ? `${existing}\n${value}` : value;
    detailsReplyText.focus();
  }


  function applyMacroTemplate() {
    if (!activeMacroTemplate) return;
    const message = resolveMacroText(activeMacroTemplate);
    if (!message) return;
    insertReplyText(message);
    if (typeof showNotification === 'function') {
      showNotification('Макрос добавлен в поле ответа.', 'success');
    }
    emitWorkspaceTelemetry('macro_apply', {
      ticketId: activeDialogTicketId,
      templateId: activeMacroMeta?.id || null,
      templateName: activeMacroMeta?.name || null,
    });
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
      ? `<div class="chat-message-menu">
          <button class="chat-message-menu-toggle" type="button" data-action-menu aria-label="Действия с сообщением">⋯</button>
          <div class="chat-message-menu-list">
            <button class="btn btn-sm btn-outline-secondary" type="button" data-action="reply" data-message-id="${message.telegramMessageId}">Ответить</button>
            ${isSupport ? `<button class="btn btn-sm btn-outline-secondary" type="button" data-action="edit" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Редактировать</button>` : ''}
            ${isSupport ? `<button class="btn btn-sm btn-outline-danger" type="button" data-action="delete" data-message-id="${message.telegramMessageId}" ${isDeleted ? 'disabled' : ''}>Удалить</button>` : ''}
          </div>
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
      updateDialogUnreadCount(0);
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

  function setRowUnreadCount(row, unreadCount) {
    if (!row) return;
    const count = Number(unreadCount) || 0;
    row.dataset.unread = String(count);
    const unreadBadge = row.querySelector('.dialog-unread-count');
    if (unreadBadge) {
      unreadBadge.textContent = count;
      unreadBadge.classList.toggle('d-none', count <= 0);
    }
  }

  function updateDialogUnreadCount(unreadCount) {
    const count = Number(unreadCount) || 0;
    if (detailsUnreadCount) {
      detailsUnreadCount.textContent = count;
      detailsUnreadCount.classList.toggle('d-none', count <= 0);
    }
    setRowUnreadCount(activeDialogRow, count);
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
      badge.className = `badge rounded-pill ${statusClassByKey(statusKey)}`;
    }
    setRowUnreadCount(row, unreadCount);
    updateRowQuickActions(row);
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
    setActiveDialogRow(fallbackRow || null, { ensureVisible: true });
    activeDialogChannelId = fallbackRow?.dataset?.channelId || null;
    updateDialogUnreadCount(Number(fallbackRow?.dataset?.unread) || 0);
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
      updateSummaryCategories(formatCategoriesLabel(Array.from(selectedCategories)));
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
      const selectedCategoriesLabel = formatCategoriesLabel(Array.from(selectedCategories));
      const categoriesLabel = selectedCategoriesLabel !== '—'
        ? selectedCategoriesLabel
        : (summary.categoriesSafe || summary.categories || fallbackRow?.dataset.categories || '—');
      const requestNumber = summary.requestNumber || fallbackRow?.dataset.requestNumber || '';
      const ratingValue = summary.rating ?? fallbackRow?.dataset.rating;
      const ratingStars = formatRatingStars(ratingValue);
      if (detailsAvatar) {
        const clientUserId = summary.userId || fallbackRow?.dataset.userId || '';
        bindAvatar(detailsAvatar, clientUserId, clientName);
      }
      if (detailsClientName) detailsClientName.textContent = clientName;
      if (detailsClientStatus) detailsClientStatus.textContent = clientStatus;
      updateSummaryCategories(categoriesLabel || '—');
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
          let renderedValue = `<span class="text-dark">${escapeHtml(safeValue)}</span>`;
          if (label === 'Статус') {
            renderedValue = renderSummaryBadge(safeValue, resolveSummaryBadgeStyle('status', safeValue));
          }
          if (label === 'Канал') {
            renderedValue = renderSummaryBadge(safeValue, resolveSummaryBadgeStyle('channel', safeValue));
          }
          if (label === 'Бизнес') {
            const businessKey = String(safeValue || '').trim();
            const businessStyle = BUSINESS_STYLE_MAP[businessKey] || {};
            renderedValue = renderSummaryBadge(safeValue, {
              background: businessStyle.background || SUMMARY_BADGE_STYLES.default.background,
              text: businessStyle.text || SUMMARY_BADGE_STYLES.default.text,
            });
          }
          if (label === 'Клиент' && safeValue !== '—' && clientUserId) {
            renderedValue = `<a class="dialog-summary-value-link" href="/client/${encodeURIComponent(clientUserId)}" target="_blank" rel="noopener">${escapeHtml(safeValue)}</a>`;
          }
          if (label === 'Ответственный' && safeValue !== '—') {
            renderedValue = `<a class="dialog-summary-value-link" href="/users/${encodeURIComponent(safeValue)}" target="_blank" rel="noopener">${escapeHtml(safeValue)}</a>`;
          }
          const fieldAttr = label === 'Категории' ? ' data-summary-field="categories"' : '';
          const valueMarkup = label === 'Категории'
            ? `<span data-summary-value>${renderCategoryBadges(safeValue)}</span>`
            : renderedValue;
          return `
          <div class="d-flex justify-content-between gap-2"${fieldAttr}>
            <span>${label}</span>
            ${valueMarkup}
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
      updateDialogUnreadCount(0);
    } catch (error) {
      if (detailsSummary) {
        detailsSummary.innerHTML = `<div class="text-danger">Не удалось загрузить детали: ${error.message}</div>`;
      }
    }

    detailsModal.show();
    startHistoryPolling();
  }

  table.addEventListener('click', (event) => {
    const rowSelect = event.target.closest('.dialog-row-select');
    if (rowSelect) {
      const ticketId = String(rowSelect.dataset.ticketId || '');
      if (ticketId) {
        if (rowSelect.checked) {
          selectedTicketIds.add(ticketId);
        } else {
          selectedTicketIds.delete(ticketId);
        }
      }
      updateBulkActionsState();
      return;
    }

    const openBtn = event.target.closest('.dialog-open-btn');
    if (openBtn) {
      event.preventDefault();
      const ticketId = openBtn.dataset.ticketId;
      const row = openBtn.closest('tr');
      setActiveDialogRow(row, { ensureVisible: true });
      openDialogEntry(ticketId, row);
      return;
    }
    const taskBtn = event.target.closest('.dialog-task-btn');
    if (taskBtn) {
      setTaskDraft({
        ticketId: taskBtn.dataset.ticketId,
        client: taskBtn.dataset.client,
      });
      return;
    }
    const takeBtn = event.target.closest('.dialog-take-btn');
    if (takeBtn) {
      event.preventDefault();
      if (!canRunAction('can_assign')) {
        notifyPermissionDenied('Назначить мне');
        return;
      }
      const ticketId = takeBtn.dataset.ticketId;
      const row = takeBtn.closest('tr');
      setActiveDialogRow(row, { ensureVisible: true });
      takeDialog(ticketId, row, takeBtn);
      return;
    }
    const snoozeBtn = event.target.closest('.dialog-snooze-btn');
    if (snoozeBtn) {
      event.preventDefault();
      if (!canRunAction('can_snooze')) {
        notifyPermissionDenied('Отложить 1ч');
        return;
      }
      const ticketId = snoozeBtn.dataset.ticketId;
      const row = snoozeBtn.closest('tr');
      setActiveDialogRow(row, { ensureVisible: true });
      snoozeDialog(ticketId, 60, snoozeBtn)
        .then(() => {
          setSnooze(ticketId, 60);
          updateRowQuickActions(row);
          applyFilters();
          if (typeof showNotification === 'function') {
            showNotification('Диалог отложен на 1 час', 'success');
          }
        })
        .catch((error) => {
          if (typeof showNotification === 'function') {
            showNotification(error.message || 'Не удалось отложить диалог', 'error');
          }
        });
      return;
    }
    const closeBtn = event.target.closest('.dialog-close-btn');
    if (closeBtn) {
      event.preventDefault();
      if (!canRunAction('can_close')) {
        notifyPermissionDenied('Закрыть');
        return;
      }
      const ticketId = closeBtn.dataset.ticketId;
      const row = closeBtn.closest('tr');
      setActiveDialogRow(row, { ensureVisible: true });
      closeBtn.disabled = true;
      closeDialogQuick(ticketId, row, closeBtn)
        .then(() => {
          clearSnooze(ticketId);
          if (typeof showNotification === 'function') {
            showNotification('Диалог закрыт', 'success');
          }
        })
        .catch((error) => {
          if (typeof showNotification === 'function') {
            showNotification(error.message || 'Не удалось закрыть диалог', 'error');
          }
          closeBtn.disabled = false;
        });
    }
  });

  if (selectAllCheckbox) {
    selectAllCheckbox.addEventListener('change', () => {
      visibleRows().forEach((row) => {
        const ticketId = String(row.dataset.ticketId || '');
        if (!ticketId) return;
        if (selectAllCheckbox.checked) {
          selectedTicketIds.add(ticketId);
        } else {
          selectedTicketIds.delete(ticketId);
        }
      });
      updateBulkActionsState();
    });
  }

  if (bulkTakeBtn) {
    bulkTakeBtn.addEventListener('click', () => runBulkAction('take'));
  }
  if (bulkSnoozeBtn) {
    bulkSnoozeBtn.addEventListener('click', () => runBulkAction('snooze'));
  }
  if (bulkCloseBtn) {
    bulkCloseBtn.addEventListener('click', () => runBulkAction('close'));
  }
  if (bulkClearBtn) {
    bulkClearBtn.addEventListener('click', () => clearSelection());
  }

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
          openCategoryPanel();
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
      if (!window.confirm('Переоткрыть закрытое обращение?')) {
        return;
      }
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
          updateRowResponsible(activeDialogRow, data.responsible || activeDialogRow.dataset.responsible || '');
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
      const menuToggle = event.target.closest('[data-action-menu]');
      if (menuToggle) {
        const menu = menuToggle.closest('.chat-message-menu');
        if (!menu) return;
        detailsHistory.querySelectorAll('.chat-message-menu.is-open').forEach((openMenu) => {
          if (openMenu !== menu) openMenu.classList.remove('is-open');
        });
        menu.classList.toggle('is-open');
        return;
      }
      const button = event.target.closest('button[data-action]');
      if (!button || !activeDialogTicketId) return;
      const menu = button.closest('.chat-message-menu');
      if (menu) menu.classList.remove('is-open');
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

  document.addEventListener('click', (event) => {
    if (!detailsHistory) return;
    if (event.target.closest('.chat-message-menu')) return;
    detailsHistory.querySelectorAll('.chat-message-menu.is-open').forEach((menu) => menu.classList.remove('is-open'));
  });

  if (dialogFontSizeRange) {
    dialogFontSizeRange.addEventListener('input', () => {
      applyDialogFontSize(dialogFontSizeRange.value);
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

  if (slaWindowSelect) {
    slaWindowSelect.addEventListener('change', () => {
      const parsed = Number.parseInt(slaWindowSelect.value, 10);
      filterState.slaWindowMinutes = Number.isFinite(parsed) && parsed > 0 ? parsed : null;
      applyFilters();
    });
  }

  if (sortModeSelect) {
    sortModeSelect.addEventListener('change', () => {
      const value = String(sortModeSelect.value || 'default').trim().toLowerCase();
      filterState.sortMode = value === 'sla_priority' ? 'sla_priority' : 'default';
      if (filterState.sortMode !== 'sla_priority') {
        lastManualSortMode = filterState.sortMode;
      }
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
      if (slaWindowSelect) {
        slaWindowSelect.value = '';
      }
      filterState.slaWindowMinutes = null;
      if (sortModeSelect) {
        sortModeSelect.value = 'default';
      }
      filterState.sortMode = 'default';
      lastManualSortMode = 'default';
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
      setActiveDialogRow(null);
      if (detailsReplyText) detailsReplyText.value = '';
      if (detailsReplyMedia) detailsReplyMedia.value = '';
      resetReplyTarget();
      selectedCategories = new Set();
      stopHistoryPolling();
      if (WORKSPACE_V1_ENABLED && isWorkspaceDialogPath(window.location.pathname)) {
        const nextPath = window.location.pathname === '/'
          ? '/'
          : '/dialogs';
        window.history.replaceState(null, '', nextPath);
      }
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
      updateSummaryCategories(formatCategoriesLabel(Array.from(selectedCategories)));
      scheduleCategorySave();
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

  if (macroTemplateApply) {
    macroTemplateApply.addEventListener('click', () => {
      applyMacroTemplate();
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
        const section = target === 'category'
            ? categoryTemplatesSection
            : (target === 'macro' ? macroTemplatesSection : questionTemplatesSection);
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

  document.addEventListener('keydown', handleGlobalShortcuts);


  function applyOperatorPermissionGuards() {
    rowsList().forEach((row) => updateRowQuickActions(row));
    updateBulkActionsState();
  }
  if (workspaceMessagesRetry) {
    workspaceMessagesRetry.addEventListener('click', () => {
      reloadWorkspaceForInitialRoute();
    });
  }

  if (workspaceComposerSend) {
    workspaceComposerSend.addEventListener('click', () => {
      sendWorkspaceReply();
    });
  }

  if (workspaceComposerSaveDraft) {
    workspaceComposerSaveDraft.addEventListener('click', () => {
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText?.value || '');
    });
  }

  if (workspaceComposerText) {
    workspaceComposerText.addEventListener('input', () => {
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { silent: true });
    });
    workspaceComposerText.addEventListener('keydown', (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault();
        sendWorkspaceReply();
      }
      if ((event.ctrlKey || event.metaKey) && String(event.key).toLowerCase() === 's') {
        event.preventDefault();
        saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value);
      }
    });
  }

  function registerWorkspaceFirstInteraction() {
    if (!WORKSPACE_V1_ENABLED || !activeWorkspaceTicketId) return;
    if (workspaceFirstInteractionTickets.has(activeWorkspaceTicketId)) return;
    workspaceFirstInteractionTickets.add(activeWorkspaceTicketId);
  }

  ['click', 'keydown'].forEach((eventName) => {
    document.addEventListener(eventName, registerWorkspaceFirstInteraction, { once: true });
  });

  window.addEventListener('beforeunload', () => {
    if (!WORKSPACE_V1_ENABLED || !activeWorkspaceTicketId) return;
    if (workspaceFirstInteractionTickets.has(activeWorkspaceTicketId)) return;
    if (typeof navigator.sendBeacon !== 'function') return;
    const payload = new Blob([JSON.stringify({
      event_type: 'workspace_abandon',
      event_group: WORKSPACE_TELEMETRY_EVENT_GROUPS.workspace_abandon,
      timestamp: new Date().toISOString(),
      ticket_id: activeWorkspaceTicketId,
      reason: 'no_first_interaction',
      experiment_name: workspaceExperimentContext.experimentName,
      experiment_cohort: workspaceExperimentContext.cohort,
    })], { type: 'application/json' });
    navigator.sendBeacon('/api/dialogs/workspace-telemetry', payload);
  });

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
  loadDialogFontSize();
  buildColumnsList();
  applyColumnState();
  applyBusinessCellStyles();
  hydrateAvatars(table);
  applyOperatorPermissionGuards();
  updateAllSlaBadges();
  setInterval(updateAllSlaBadges, 60 * 1000);
  emitWorkspaceTelemetry('workspace_experiment_exposure', {
    reason: WORKSPACE_AB_TEST_CONFIG.enabled ? 'bootstrap' : 'experiment_disabled',
  });
  if (viewTabs.length) {
    const activeTab = Array.from(viewTabs).find((tab) => tab.classList.contains('active'));
    setViewTab(activeTab?.dataset.dialogView || 'all');
  } else {
    applyFilters();
  }

  if (WORKSPACE_V1_ENABLED && INITIAL_DIALOG_TICKET_ID) {
    const initialRow = rowsList().find((row) => String(row.dataset.ticketId || '') === INITIAL_DIALOG_TICKET_ID) || null;
    if (initialRow) {
      setActiveDialogRow(initialRow, { ensureVisible: true });
    }
    openDialogWithWorkspaceFallback(INITIAL_DIALOG_TICKET_ID, initialRow, { source: 'initial_route' });
  }

  if (WORKSPACE_V1_ENABLED && WORKSPACE_INLINE_NAVIGATION) {
    window.addEventListener('popstate', () => {
      const path = window.location.pathname || '';
      const match = path.match(/^\/dialogs\/([^/]+)$/);
      if (!match?.[1]) return;
      const ticketId = decodeURIComponent(match[1]);
      const row = rowsList().find((item) => String(item.dataset.ticketId || '') === ticketId) || null;
      setActiveDialogRow(row, { ensureVisible: true });
      openDialogWithWorkspaceFallback(ticketId, row, { source: 'initial_route' });
    });
  }

  initColumnResize();
  restoreColumnWidths();
  initDetailsResize();
})();

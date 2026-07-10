// panel/static/dialogs.js
(function () {
  const table = document.getElementById('dialogsTable');
  if (!table) return;

  const DEBUG_DIALOGS =
    (new URLSearchParams(window.location.search || '')).get('debugDialogs') === '1'
    || String(localStorage.getItem('debugDialogs') || '').trim() === '1';

  function debugLog(message, payload) {
    if (!DEBUG_DIALOGS) return;
    const history = window.__dialogsDebugEvents = window.__dialogsDebugEvents || [];
    history.push({
      ts: Date.now(),
      message,
      payload: payload === undefined ? null : payload,
    });
    if (history.length > 300) {
      history.shift();
    }
  }

  if (DEBUG_DIALOGS) {
    window.__dialogsDebugEnabled = true;
    window.addEventListener('error', (event) => {
      debugLog('window.error', {
        message: event?.message,
        filename: event?.filename,
        line: event?.lineno,
        col: event?.colno,
      });
    });
    window.addEventListener('unhandledrejection', (event) => {
      const reason = event?.reason;
      debugLog('window.unhandledrejection', {
        message: reason?.message || String(reason || ''),
        stack: reason?.stack || null,
      });
    });
    debugLog('bootstrap.start', {
      href: window.location.href,
      hasDetailsModal: Boolean(document.getElementById('dialogDetailsModal')),
      hasDialogsTable: Boolean(table),
    });
  }

  const quickSearch = document.getElementById('dialogQuickSearch');
  const aiReviewQueueSection = document.getElementById('aiReviewQueueSection');
  const aiReviewQueueRefresh = document.getElementById('aiReviewQueueRefresh');
  const aiReviewQueueState = document.getElementById('aiReviewQueueState');
  const aiReviewQueueBody = document.getElementById('aiReviewQueueBody');
  const aiMonitoringSection = document.getElementById('aiMonitoringSection');
  const aiMonitoringRefresh = document.getElementById('aiMonitoringRefresh');
  const aiMonitoringState = document.getElementById('aiMonitoringState');
  const aiMonitoringAlerts = document.getElementById('aiMonitoringAlerts');
  const aiMonitoringRunbook = document.getElementById('aiMonitoringRunbook');
  const aiMonitoringEvents = document.getElementById('aiMonitoringEvents');
  const aiMonitoringEventTypeFilter = document.getElementById('aiMonitoringEventTypeFilter');
  const aiMonitoringActorFilter = document.getElementById('aiMonitoringActorFilter');
  const aiMonitoringApplyFilters = document.getElementById('aiMonitoringApplyFilters');
  const aiMonitoringExportCsv = document.getElementById('aiMonitoringExportCsv');
  const aiKpiAutoReplyRate = document.getElementById('aiKpiAutoReplyRate');
  const aiKpiAssistRate = document.getElementById('aiKpiAssistRate');
  const aiKpiEscalationRate = document.getElementById('aiKpiEscalationRate');
  const aiKpiCorrectionRate = document.getElementById('aiKpiCorrectionRate');
  const pageSizeSelect = document.getElementById('dialogPageSize');
  const pagePrevBtn = document.getElementById('dialogPagePrev');
  const pageNextBtn = document.getElementById('dialogPageNext');
  const pageNumbers = document.getElementById('dialogPageNumbers');
  const pageState = document.getElementById('dialogPageState');
  const slaWindowSelect = document.getElementById('dialogSlaWindow');
  const sortModeSelect = document.getElementById('dialogSortMode');
  const filtersBtn = document.getElementById('dialogFiltersBtn');
  const columnsBtn = document.getElementById('dialogColumnsBtn');
  const dialogCompactToggle = document.getElementById('dialogCompactToggle');
  const dialogListOnlyToggle = document.getElementById('dialogListOnlyToggle');
  const hotkeysBtn = document.getElementById('dialogHotkeysBtn');
  const experimentInfoMeta = document.getElementById('dialogExperimentInfoMeta');
  const experimentPrimaryKpis = document.getElementById('dialogExperimentPrimaryKpis');
  const experimentSecondaryKpis = document.getElementById('dialogExperimentSecondaryKpis');
  const experimentTelemetrySummaryState = document.getElementById('dialogExperimentTelemetrySummaryState');
  const experimentTelemetryGuardrailState = document.getElementById('dialogExperimentTelemetryGuardrailState');
  const experimentTelemetryGuardrailAlerts = document.getElementById('dialogExperimentTelemetryGuardrailAlerts');
  const experimentRolloutDecisionState = document.getElementById('dialogExperimentRolloutDecisionState');
  const experimentRolloutDecisionChecklist = document.getElementById('dialogExperimentRolloutDecisionChecklist');
  const experimentRolloutPacketState = document.getElementById('dialogExperimentRolloutPacketState');
  const experimentRolloutPacketChecklist = document.getElementById('dialogExperimentRolloutPacketChecklist');
  const experimentRolloutPacketWrap = document.getElementById('dialogExperimentRolloutPacketWrap');
  const experimentRolloutPacketRows = document.getElementById('dialogExperimentRolloutPacketRows');
  const experimentRolloutScorecardWrap = document.getElementById('dialogExperimentRolloutScorecardWrap');
  const experimentRolloutScorecardRows = document.getElementById('dialogExperimentRolloutScorecardRows');
  const experimentRolloutKpiOutcomesWrap = document.getElementById('dialogExperimentRolloutKpiOutcomesWrap');
  const experimentRolloutKpiOutcomeRows = document.getElementById('dialogExperimentRolloutKpiOutcomeRows');
  const experimentTelemetrySummaryRows = document.getElementById('dialogExperimentTelemetrySummaryRows');
  const experimentGapBreakdownWrap = document.getElementById('dialogExperimentGapBreakdownWrap');
  const experimentGapBreakdownRows = document.getElementById('dialogExperimentGapBreakdownRows');
  const experimentTelemetryShiftRows = document.getElementById('dialogExperimentTelemetryShiftRows');
  const experimentTelemetryTeamRows = document.getElementById('dialogExperimentTelemetryTeamRows');
  const experimentTelemetryRefreshBtn = document.getElementById('dialogExperimentTelemetryRefreshBtn');
  const experimentTelemetryAutoRefresh = document.getElementById('dialogExperimentTelemetryAutoRefresh');
  const experimentInfoModalEl = document.getElementById('dialogExperimentInfoModal');
  const filtersModalEl = document.getElementById('dialogFiltersModal');
  const columnsModalEl = document.getElementById('dialogColumnsModal');
  const detailsModalEl = document.getElementById('dialogDetailsModal');
  const categoriesModalEl = document.getElementById('dialogCategoriesModal');
  const hotkeysModalEl = document.getElementById('dialogHotkeysModal');
  const workspaceShell = document.getElementById('dialogsWorkspaceShell');
  const workspaceConversationTitle = document.getElementById('workspaceConversationTitle');
  const workspaceConversationMeta = document.getElementById('workspaceConversationMeta');
  const workspaceNavPrevBtn = document.getElementById('workspaceNavPrevBtn');
  const workspaceNavNextBtn = document.getElementById('workspaceNavNextBtn');
  const workspaceNavState = document.getElementById('workspaceNavState');
  const workspaceRolloutBanner = document.getElementById('workspaceRolloutBanner');
  const workspaceParityBanner = document.getElementById('workspaceParityBanner');
  const workspaceMessagesState = document.getElementById('workspaceMessagesState');
  const workspaceMessagesList = document.getElementById('workspaceMessagesList');
  const workspaceMessagesLoadMoreWrap = document.getElementById('workspaceMessagesLoadMoreWrap');
  const workspaceMessagesLoadMore = document.getElementById('workspaceMessagesLoadMore');
  const workspaceMessagesError = document.getElementById('workspaceMessagesError');
  const workspaceMessagesRetry = document.getElementById('workspaceMessagesRetry');
  const workspaceClientState = document.getElementById('workspaceClientState');
  const workspaceClientContent = document.getElementById('workspaceClientContent');
  const workspaceClientError = document.getElementById('workspaceClientError');
  const workspaceClientRetry = document.getElementById('workspaceClientRetry');
  const workspaceHistoryState = document.getElementById('workspaceHistoryState');
  const workspaceHistoryContent = document.getElementById('workspaceHistoryContent');
  const workspaceHistoryError = document.getElementById('workspaceHistoryError');
  const workspaceHistoryRetry = document.getElementById('workspaceHistoryRetry');
  const workspaceRelatedEventsState = document.getElementById('workspaceRelatedEventsState');
  const workspaceRelatedEventsContent = document.getElementById('workspaceRelatedEventsContent');
  const workspaceRelatedEventsError = document.getElementById('workspaceRelatedEventsError');
  const workspaceRelatedEventsRetry = document.getElementById('workspaceRelatedEventsRetry');
  const workspaceSlaState = document.getElementById('workspaceSlaState');
  const workspaceSlaContent = document.getElementById('workspaceSlaContent');
  const workspaceSlaError = document.getElementById('workspaceSlaError');
  const workspaceSlaRetry = document.getElementById('workspaceSlaRetry');
  const workspaceReadonlyBanner = document.getElementById('workspaceReadonlyBanner');
  const workspaceComposerText = document.getElementById('workspaceComposerText');
  const workspaceComposerSend = document.getElementById('workspaceComposerSend');
  const workspaceComposerMediaTrigger = document.getElementById('workspaceComposerMediaTrigger');
  const workspaceComposerMedia = document.getElementById('workspaceComposerMedia');
  const workspaceComposerSaveDraft = document.getElementById('workspaceComposerSaveDraft');
  const workspaceComposerDraftState = document.getElementById('workspaceComposerDraftState');
  const workspaceComposerMacroSection = document.getElementById('workspaceComposerMacroSection');
  const workspaceComposerMacroSearch = document.getElementById('workspaceComposerMacroSearch');
  const workspaceComposerMacroSelect = document.getElementById('workspaceComposerMacroSelect');
  const workspaceComposerMacroApply = document.getElementById('workspaceComposerMacroApply');
  const workspaceComposerMacroPreview = document.getElementById('workspaceComposerMacroPreview');
  const workspaceComposerMacroMeta = document.getElementById('workspaceComposerMacroMeta');
  const workspaceReplyTarget = document.getElementById('workspaceReplyTarget');
  const workspaceReplyTargetText = document.getElementById('workspaceReplyTargetText');
  const workspaceReplyTargetClear = document.getElementById('workspaceReplyTargetClear');
  const workspaceAiSuggestionsSection = document.getElementById('workspaceAiSuggestionsSection');
  const workspaceAiSuggestionsState = document.getElementById('workspaceAiSuggestionsState');
  const workspaceAiSuggestionsList = document.getElementById('workspaceAiSuggestionsList');
  const workspaceAiSuggestionsRefresh = document.getElementById('workspaceAiSuggestionsRefresh');
  const workspaceAiDisableForDialog = document.getElementById('workspaceAiDisableForDialog');
  const workspaceAiHandoffNoAutoReply = document.getElementById('workspaceAiHandoffNoAutoReply');
  const workspaceAiControlState = document.getElementById('workspaceAiControlState');
  const workspaceAiReviewBox = document.getElementById('workspaceAiReviewBox');
  const workspaceAiReviewQuestion = document.getElementById('workspaceAiReviewQuestion');
  const workspaceAiReviewProblemSelect = document.getElementById('workspaceAiReviewProblemSelect');
  const workspaceAiReviewSolutionSelect = document.getElementById('workspaceAiReviewSolutionSelect');
  const workspaceAiReviewCurrent = document.getElementById('workspaceAiReviewCurrent');
  const workspaceAiReviewPending = document.getElementById('workspaceAiReviewPending');
  const workspaceAiReviewApprove = document.getElementById('workspaceAiReviewApprove');
  const workspaceAiReviewLearn = document.getElementById('workspaceAiReviewLearn');
  const workspaceAiReviewReject = document.getElementById('workspaceAiReviewReject');
  const workspaceAssignBtn = document.getElementById('workspaceAssignBtn');
  const workspaceSnoozeBtn = document.getElementById('workspaceSnoozeBtn');
  const workspaceResolveBtn = document.getElementById('workspaceResolveBtn');
  const workspaceReopenBtn = document.getElementById('workspaceReopenBtn');
  const workspaceCreateTaskBtn = document.getElementById('workspaceCreateTaskBtn');
  const workspaceAiIncidentExport = document.getElementById('workspaceAiIncidentExport');
  const workspaceLegacyBtn = document.getElementById('workspaceLegacyBtn');
  const workspaceTabButtons = Array.from(document.querySelectorAll('[data-workspace-tab]'));
  const workspaceTabPanels = Array.from(document.querySelectorAll('[data-workspace-tab-panel]'));
  const workspaceCategoriesState = document.getElementById('workspaceCategoriesState');
  const workspaceCategoriesList = document.getElementById('workspaceCategoriesList');
  const workspaceCategoriesError = document.getElementById('workspaceCategoriesError');
  const workspaceCategoriesClear = document.getElementById('workspaceCategoriesClear');

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
  const bulkToolbar = document.getElementById('dialogBulkToolbar');
  const bulkTakeBtn = document.getElementById('dialogBulkTakeBtn');
  const bulkSnoozeBtn = document.getElementById('dialogBulkSnoozeBtn');
  const hotkeySingleSnoozeLabel = document.getElementById('dialogHotkeySingleSnoozeLabel');
  const hotkeyBulkSnoozeLabel = document.getElementById('dialogHotkeyBulkSnoozeLabel');
  const bulkCloseBtn = document.getElementById('dialogBulkCloseBtn');
  const bulkClearBtn = document.getElementById('dialogBulkClearBtn');
  const selectAllCheckbox = document.getElementById('dialogSelectAll');

  const detailsMeta = document.getElementById('dialogDetailsMeta');
  const detailsAvatar = document.getElementById('dialogDetailsAvatar');
  const detailsClientName = document.getElementById('dialogDetailsClientName');
  const detailsClientStatus = document.getElementById('dialogDetailsClientStatus');
  const detailsLocation = document.getElementById('dialogDetailsLocation');
  const detailsOpenClientCard = document.getElementById('dialogDetailsOpenClientCard');
  const detailsTakeBtn = document.getElementById('dialogDetailsTakeBtn');
  const detailsParticipantsBtn = document.getElementById('dialogDetailsParticipantsBtn');
  const detailsReassignBtn = document.getElementById('dialogDetailsReassignBtn');
  const detailsCategories = document.getElementById('dialogDetailsCategories');
  const detailsCategoriesBtn = document.getElementById('dialogDetailsCategoriesBtn');
  const detailsUnreadCount = document.getElementById('dialogDetailsUnreadCount');
  const detailsRating = document.getElementById('dialogDetailsRating');
  const detailsSummary = document.getElementById('dialogDetailsSummary');
  const detailsHistory = document.getElementById('dialogDetailsHistory');
  const detailsCreateTask = document.getElementById('dialogDetailsCreateTask');
  const detailsResolve = document.getElementById('dialogDetailsResolve');
  const detailsReopen = document.getElementById('dialogDetailsReopen');
  const detailsProblem = document.getElementById('dialogDetailsProblem');
  const detailsMetrics = document.getElementById('dialogDetailsMetrics');
  const detailsAiState = document.getElementById('dialogDetailsAiState');
  const detailsAiList = document.getElementById('dialogDetailsAiList');
  const detailsAiRefresh = document.getElementById('dialogDetailsAiRefresh');
  const detailsParticipantsSection = document.getElementById('dialogDetailsParticipantsSection');
  const detailsParticipantsManageBtn = document.getElementById('dialogDetailsParticipantsManageBtn');
  const detailsParticipantsState = document.getElementById('dialogDetailsParticipantsState');
  const detailsParticipantsList = document.getElementById('dialogDetailsParticipantsList');
  const detailsSidebar = document.getElementById('dialogDetailsSidebar');
  const detailsSidebarScroll = detailsSidebar ? detailsSidebar.querySelector('.dialog-details-sidebar-scroll') : null;
  const detailsResizeHandle = document.getElementById('dialogDetailsResizeHandle');
  const myDialogsPanel = document.getElementById('dialogMyDialogsPanel');
  const myDialogsCount = document.getElementById('dialogMyDialogsCount');
  const myDialogsEmpty = document.getElementById('dialogMyDialogsEmpty');
  const myDialogsNewSection = document.getElementById('dialogMyDialogsNewSection');
  const myDialogsNewList = document.getElementById('dialogMyDialogsNewList');
  const myDialogsUnansweredSection = document.getElementById('dialogMyDialogsUnansweredSection');
  const myDialogsUnansweredList = document.getElementById('dialogMyDialogsUnansweredList');
  const myDialogsInWorkSection = document.getElementById('dialogMyDialogsInWorkSection');
  const myDialogsInWorkList = document.getElementById('dialogMyDialogsInWorkList');
  const detailsReplyText = document.getElementById('dialogReplyText');
  const detailsReplySend = document.getElementById('dialogReplySend');
  const detailsReplyMedia = document.getElementById('dialogReplyMedia');
  const detailsReplyMediaTrigger = document.getElementById('dialogReplyMediaTrigger');
  const detailsReplyEmojiTrigger = document.getElementById('dialogReplyEmojiTrigger');
  const dialogFontSizeRange = document.getElementById('dialogFontSizeRange');
  const emojiPanel = document.getElementById('dialogEmojiPanel');
  const emojiList = document.getElementById('dialogEmojiList');
  const detailsSpam = document.getElementById('dialogDetailsSpam');
  const mediaPreviewModalEl = document.getElementById('dialogMediaPreviewModal');
  const mediaPreviewVideo = document.getElementById('dialogMediaPreviewVideo');
  const mediaPreviewImage = document.getElementById('dialogMediaPreviewImage');
  const mediaPreviewImageControls = document.getElementById('dialogMediaImageControls');
  const mediaPreviewZoomOut = document.getElementById('dialogMediaZoomOut');
  const mediaPreviewZoomIn = document.getElementById('dialogMediaZoomIn');
  const mediaPreviewDownloadLink = document.getElementById('dialogMediaDownloadLink');
  const participantsModalEl = document.getElementById('dialogParticipantsModal');
  const participantsMeta = document.getElementById('dialogParticipantsMeta');
  const participantsSelect = document.getElementById('dialogParticipantsSelect');
  const participantsSelectHint = document.getElementById('dialogParticipantsSelectHint');
  const participantsAddBtn = document.getElementById('dialogParticipantsAddBtn');
  const participantsCurrentState = document.getElementById('dialogParticipantsCurrentState');
  const participantsCurrentList = document.getElementById('dialogParticipantsCurrentList');
  const reassignModalEl = document.getElementById('dialogReassignModal');
  const reassignMeta = document.getElementById('dialogReassignMeta');
  const reassignCurrent = document.getElementById('dialogReassignCurrent');
  const reassignSelect = document.getElementById('dialogReassignSelect');
  const reassignHint = document.getElementById('dialogReassignHint');
  const reassignSubmit = document.getElementById('dialogReassignSubmit');
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
  const macroVariableCatalog = document.getElementById('dialogMacroVariableCatalog');
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
  const categoriesModal = (typeof bootstrap !== 'undefined' && categoriesModalEl)
    ? new bootstrap.Modal(categoriesModalEl)
    : null;
  const hotkeysModal = (typeof bootstrap !== 'undefined' && hotkeysModalEl)
    ? new bootstrap.Modal(hotkeysModalEl)
    : null;
  const mediaPreviewModal = (typeof bootstrap !== 'undefined' && mediaPreviewModalEl)
    ? new bootstrap.Modal(mediaPreviewModalEl)
    : null;
  const participantsModal = (typeof bootstrap !== 'undefined' && participantsModalEl)
    ? new bootstrap.Modal(participantsModalEl)
    : null;
  const reassignModal = (typeof bootstrap !== 'undefined' && reassignModalEl)
    ? new bootstrap.Modal(reassignModalEl)
    : null;
  let dialogsShellRuntime = null;

  function showModalSafe(modalEl, modalInstance) {
    dialogsShellRuntime?.showModalSafe(modalEl, modalInstance);
  }

  function hideModalSafe(modalEl, modalInstance) {
    dialogsShellRuntime?.hideModalSafe(modalEl, modalInstance);
  }

  function runDialogsInitStep(label, callback) {
    if (typeof callback !== 'function') return null;
    try {
      return callback();
    } catch (error) {
      debugLog('dialogs.init.step.failed', {
        label,
        message: error?.message || String(error || ''),
      });
      console.error(`Dialogs init step failed: ${label}`, error);
      return null;
    }
  }

  function bindFallbackModalDismiss(modalEl, modalInstance) {
    dialogsShellRuntime?.bindFallbackModalDismiss(modalEl, modalInstance);
  }

  function bindModalScrollContainment(container) {
    dialogsShellRuntime?.bindModalScrollContainment(container, {
      requireModalVisible: true,
      isModalVisible: () => detailsModalEl?.classList.contains('show') === true,
    });
  }

  function applyCompactMode(enabled) {
    dialogsShellRuntime?.applyCompactMode(enabled);
  }

  function loadCompactMode() {
    dialogsShellRuntime?.loadCompactMode();
  }

  function toggleCompactMode() {
    dialogsShellRuntime?.toggleCompactMode();
  }

  function applyListOnlyMode(enabled) {
    dialogsShellRuntime?.applyListOnlyMode(enabled);
  }

  function loadListOnlyMode() {
    dialogsShellRuntime?.loadListOnlyMode();
  }

  function toggleListOnlyMode() {
    dialogsShellRuntime?.toggleListOnlyMode();
  }

  function exitListOnlyMode(reason = 'dialog_open') {
    if (!document.body.classList.contains('dialog-list-only-mode')) {
      return;
    }
    dialogsShellRuntime?.applyListOnlyMode(false);
    try {
      localStorage.setItem(STORAGE_LIST_ONLY_MODE, '0');
    } catch (_error) {
      // ignore storage write errors
    }
    debugLog('listOnlyMode.exited', { reason });
  }

  function openTaskCreateSurface(ticketId, clientName) {
    dialogsShellRuntime?.openTaskCreateSurface(ticketId, clientName);
  }

  function loadColumnState() {
    dialogsShellRuntime?.loadColumnState();
  }

  function loadColumnOrder() {
    dialogsShellRuntime?.loadColumnOrder();
  }

  function persistColumnState() {
    dialogsShellRuntime?.persistColumnState();
  }

  function persistColumnOrder() {
    dialogsShellRuntime?.persistColumnOrder();
  }

  function applyColumnState() {
    dialogsShellRuntime?.applyColumnState();
  }

  function applyColumnOrder() {
    dialogsShellRuntime?.applyColumnOrder();
  }

  function buildColumnsList() {
    dialogsShellRuntime?.buildColumnsList();
  }

  function syncColumnsList() {
    dialogsShellRuntime?.syncColumnsList();
  }

  const STORAGE_COLUMNS = 'iguana:dialogs:columns';
  const STORAGE_COLUMN_ORDER = 'iguana:dialogs:column-order';
  const STORAGE_WIDTHS = 'iguana:dialogs:column-widths';
  const STORAGE_TASK = 'iguana:dialogs:create-task';
  const STORAGE_PAGE_SIZE = 'iguana:dialogs:page-size';
  const STORAGE_DIALOG_FONT = 'iguana:dialogs:font-size';
  const STORAGE_SNOOZED = 'iguana:dialogs:snoozed';
  const STORAGE_VIEW = 'iguana:dialogs:view';
  const STORAGE_SLA_WINDOW = 'iguana:dialogs:sla-window';
  const STORAGE_SORT_MODE = 'iguana:dialogs:sort-mode';
  const STORAGE_COMPACT_MODE = 'iguana:dialogs:compact-mode';
  const STORAGE_LIST_ONLY_MODE = 'iguana:dialogs:list-only-mode';
  const DEFAULT_LIST_POLL_INTERVAL_MS = 8000;
  const DEFAULT_HISTORY_POLL_INTERVAL_MS = 8000;
  const DEFAULT_QUICK_SNOOZE_MINUTES = 60;
  const DEFAULT_OVERDUE_THRESHOLD_HOURS = 24;
  const DEFAULT_PAGE_SIZE = 20;
  const DEFAULT_DIALOG_FONT = 14;
  const DEFAULT_SLA_TARGET_MINUTES = 24 * 60;
  const DEFAULT_SLA_WARNING_MINUTES = 4 * 60;
  const DEFAULT_PRIMARY_KPIS = Object.freeze(['FRT', 'TTR', 'SLA breach %']);
  const DEFAULT_SECONDARY_KPIS = Object.freeze(['Dialogs per operator / shift', 'CSAT', 'UI error-rate']);
  const aiMonitoringFilters = {
    eventType: '',
    actor: '',
  };

  function normalizeStringArray(value, fallbackValue) {
    if (!Array.isArray(value)) {
      return fallbackValue;
    }
    const normalized = value
      .map((item) => String(item || '').trim())
      .filter((item) => item.length > 0);
    return normalized.length ? normalized : fallbackValue;
  }

  function normalizeExperimentCohort(value) {
    const normalized = String(value || '').trim().toLowerCase();
    return normalized === 'test' || normalized === 'control' ? normalized : null;
  }

  function normalizeOperatorExperimentOverrides(value) {
    if (!value || typeof value !== 'object') {
      return {};
    }
    const normalizedOverrides = {};
    Object.entries(value).forEach(([identity, override]) => {
      const normalizedIdentity = String(identity || '').trim().toLowerCase();
      if (!normalizedIdentity || !override || typeof override !== 'object') {
        return;
      }
      const cohort = normalizeExperimentCohort(override.cohort);
      const segment = String(override.segment || '').trim();
      if (!cohort && !segment) {
        return;
      }
      normalizedOverrides[normalizedIdentity] = {
        cohort,
        segment: segment || null,
      };
    });
    return normalizedOverrides;
  }

  function normalizeSlaMinutes(value, fallbackValue) {
    const parsed = Number.parseInt(value, 10);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return fallbackValue;
    }
    return parsed;
  }

  function normalizeSlaWindowPresets(value) {
    if (!Array.isArray(value)) {
      return [15, 30, 60, 120];
    }
    const normalized = Array.from(new Set(value
      .map((item) => Number.parseInt(item, 10))
      .filter((item) => Number.isFinite(item) && item >= 5 && item <= 1440)))
      .sort((left, right) => left - right);
    return normalized.length ? normalized : [15, 30, 60, 120];
  }

  function normalizeNumberInRange(value, fallbackValue, minValue, maxValue) {
    const parsed = Number.parseInt(value, 10);
    if (!Number.isFinite(parsed)) {
      return fallbackValue;
    }
    return Math.min(maxValue, Math.max(minValue, parsed));
  }

  function formatSnoozeActionLabel(minutes) {
    const normalizedMinutes = Math.max(1, Number(minutes) || QUICK_SNOOZE_MINUTES);
    if (normalizedMinutes % 60 === 0) {
      const hours = normalizedMinutes / 60;
      return `Отложить ${hours}ч`;
    }
    return `Отложить ${normalizedMinutes}м`;
  }

  function formatBulkSnoozeLabel(minutes) {
    const normalizedMinutes = Math.max(1, Number(minutes) || QUICK_SNOOZE_MINUTES);
    if (normalizedMinutes % 60 === 0) {
      const hours = normalizedMinutes / 60;
      const hoursLabel = hours === 1 ? '1 час' : `${hours} ч`;
      return `Выбранные диалоги отложены на ${hoursLabel}`;
    }
    return `Выбранные диалоги отложены на ${normalizedMinutes} мин`;
  }

  function formatSnoozeDurationLabel(minutes) {
    const normalizedMinutes = Math.max(1, Number(minutes) || QUICK_SNOOZE_MINUTES);
    if (normalizedMinutes % 60 === 0) {
      const hours = normalizedMinutes / 60;
      return hours === 1 ? '1 час' : `${hours} ч`;
    }
    return `${normalizedMinutes} мин`;
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
  const SLA_CRITICAL_PIN_UNASSIGNED_ONLY = window.DIALOG_CONFIG?.sla_critical_pin_unassigned_only === true;
  const SLA_CRITICAL_VIEW_UNASSIGNED_ONLY = window.DIALOG_CONFIG?.sla_critical_view_unassigned_only === true;
  const AUTO_SLA_PRIORITY_FOR_CRITICAL_VIEW = window.DIALOG_CONFIG?.sla_critical_auto_sort !== false;
  const DIALOG_SLA_WINDOW_PRESETS = normalizeSlaWindowPresets(window.DIALOG_CONFIG?.sla_window_presets_minutes);
  const DIALOG_DEFAULT_SLA_WINDOW = (() => {
    const parsed = Number.parseInt(window.DIALOG_CONFIG?.sla_window_default_minutes, 10);
    return Number.isFinite(parsed) && DIALOG_SLA_WINDOW_PRESETS.includes(parsed) ? parsed : null;
  })();
  const DIALOG_DEFAULT_VIEW = window.DialogsListRuntime?.normalizeDialogView?.(window.DIALOG_CONFIG?.default_view || 'all') || 'all';
  const QUICK_SNOOZE_MINUTES = normalizeNumberInRange(
    window.DIALOG_CONFIG?.quick_snooze_minutes,
    DEFAULT_QUICK_SNOOZE_MINUTES,
    5,
    24 * 60,
  );
  const OVERDUE_THRESHOLD_HOURS = normalizeNumberInRange(
    window.DIALOG_CONFIG?.overdue_threshold_hours,
    DEFAULT_OVERDUE_THRESHOLD_HOURS,
    1,
    168,
  );
  const LIST_POLL_INTERVAL = normalizeNumberInRange(
    window.DIALOG_CONFIG?.list_poll_interval_ms,
    DEFAULT_LIST_POLL_INTERVAL_MS,
    3000,
    60000,
  );
  const HISTORY_POLL_INTERVAL = normalizeNumberInRange(
    window.DIALOG_CONFIG?.history_poll_interval_ms,
    DEFAULT_HISTORY_POLL_INTERVAL_MS,
    3000,
    60000,
  );
  const WORKSPACE_V1_ENABLED = window.DIALOG_CONFIG?.workspace_v1 !== false;
  const WORKSPACE_SINGLE_MODE = window.DIALOG_CONFIG?.workspace_single_mode === true;
  const WORKSPACE_FORCE_MODE = window.DIALOG_CONFIG?.workspace_force_workspace === true || WORKSPACE_SINGLE_MODE;
  const OPEN_DIALOGS_IN_WORKSPACE = false;
  const WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_MAX = normalizeNumberInRange(
    window.DIALOG_CONFIG?.workspace_client_extra_attributes_max,
    20,
    1,
    100,
  );
  const WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_COLLAPSE_AFTER = normalizeNumberInRange(
    window.DIALOG_CONFIG?.workspace_client_extra_attributes_collapse_after,
    8,
    1,
    50,
  );
  const WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_HIDE_TECHNICAL = window.DIALOG_CONFIG?.workspace_client_extra_attributes_hide_technical !== false;
  const WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_TECHNICAL_PREFIXES = normalizeStringArray(
    window.DIALOG_CONFIG?.workspace_client_extra_attributes_technical_prefixes,
    ['_', 'internal_'],
  ).map((value) => value.toLowerCase());
  const WORKSPACE_CLIENT_HIDDEN_ATTRIBUTES = new Set(
    normalizeStringArray(window.DIALOG_CONFIG?.workspace_client_hidden_attributes, []).map((value) => value.toLowerCase()),
  );
  const WORKSPACE_INLINE_NAVIGATION = window.DIALOG_CONFIG?.workspace_inline_navigation !== false;
  const WORKSPACE_DECOMMISSION_LEGACY_MODAL = window.DIALOG_CONFIG?.workspace_decommission_legacy_modal === true || WORKSPACE_SINGLE_MODE;
  const WORKSPACE_DISABLE_LEGACY_FALLBACK = window.DIALOG_CONFIG?.workspace_disable_legacy_fallback === true
    || WORKSPACE_FORCE_MODE
    || WORKSPACE_DECOMMISSION_LEGACY_MODAL;
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
  const INITIAL_DIALOG_TICKET_ID = (() => {
    const fromBody = String(document.body?.dataset?.initialDialogTicketId || '').trim();
    if (fromBody) {
      return fromBody;
    }
    const routeMatch = (window.location.pathname || '').match(/^\/dialogs\/([^/]+)$/);
    return routeMatch?.[1] ? decodeURIComponent(routeMatch[1]) : '';
  })();
  const INITIAL_DIALOG_CHANNEL_ID = (() => {
    const raw = new URLSearchParams(window.location.search || '').get('channelId');
    const normalized = String(raw || '').trim();
    return normalized || null;
  })();
  const WORKSPACE_OPEN_SLO_MS = normalizeNumberInRange(
    window.DIALOG_CONFIG?.workspace_open_slo_ms,
    2000,
    500,
    10000,
  );
  const WORKSPACE_CONTRACT_TIMEOUT_MS = Math.max(1000, Math.min(30000, Number(window.DIALOG_CONFIG?.workspace_contract_timeout_ms) || 8000));
  const configuredWorkspaceRetryAttempts = Number.parseInt(window.DIALOG_CONFIG?.workspace_contract_retry_attempts, 10);
  const WORKSPACE_CONTRACT_RETRY_ATTEMPTS = Math.max(
    0,
    Math.min(3, Number.isFinite(configuredWorkspaceRetryAttempts) ? configuredWorkspaceRetryAttempts : 1),
  );
  const WORKSPACE_DEFAULT_INCLUDE = 'messages,context,sla,permissions';
  const WORKSPACE_ALLOWED_INCLUDE_BLOCKS = Object.freeze(['messages', 'context', 'sla', 'permissions']);
  const WORKSPACE_CONTRACT_INCLUDE = (() => {
    const configured = String(window.DIALOG_CONFIG?.workspace_contract_include || '')
      .split(',')
      .map((part) => part.trim().toLowerCase())
      .filter(Boolean);
    const unique = [];
    configured.forEach((block) => {
      if (!WORKSPACE_ALLOWED_INCLUDE_BLOCKS.includes(block) || unique.includes(block)) return;
      unique.push(block);
    });
    return unique.length > 0 ? unique.join(',') : WORKSPACE_DEFAULT_INCLUDE;
  })();
  const WORKSPACE_MESSAGES_PAGE_LIMIT = Math.max(10, Math.min(200, Number(window.DIALOG_CONFIG?.workspace_messages_page_limit) || 50));
  const WORKSPACE_FAILURE_STREAK_THRESHOLD = Math.max(1, Math.min(10, Number(window.DIALOG_CONFIG?.workspace_failure_streak_threshold) || 3));
  const WORKSPACE_FAILURE_COOLDOWN_MS = Math.max(1000, Math.min(120000, Number(window.DIALOG_CONFIG?.workspace_failure_cooldown_ms) || 30000));
  const WORKSPACE_MUTATING_PERMISSION_KEYS = Object.freeze(['can_assign', 'can_close', 'can_snooze', 'can_bulk']);
  const WORKSPACE_AB_TEST_CONFIG = Object.freeze({
    experimentName: String(window.DIALOG_CONFIG?.workspace_ab_experiment_name || 'workspace_v1_rollout').trim() || 'workspace_v1_rollout',
    enabled: Boolean(window.DIALOG_CONFIG?.workspace_ab_enabled) && !WORKSPACE_SINGLE_MODE,
    rolloutPercent: Math.max(0, Math.min(100, Number(window.DIALOG_CONFIG?.workspace_ab_rollout_percent) || 0)),
    operatorSegment: String(window.DIALOG_CONFIG?.workspace_ab_operator_segment || 'all_operators').trim() || 'all_operators',
    operatorOverrides: normalizeOperatorExperimentOverrides(window.DIALOG_CONFIG?.workspace_ab_operator_overrides),
    primaryKpis: normalizeStringArray(window.DIALOG_CONFIG?.workspace_ab_primary_kpis, DEFAULT_PRIMARY_KPIS),
    secondaryKpis: normalizeStringArray(window.DIALOG_CONFIG?.workspace_ab_secondary_kpis, DEFAULT_SECONDARY_KPIS),
  });
  const STORAGE_WORKSPACE_AB = 'iguana:dialogs:workspace-ab-cohort';
  const OPERATOR_IDENTITY = String(document.body?.dataset?.operatorIdentity || 'anonymous').trim() || 'anonymous';
  const OPERATOR_DISPLAY_NAME = String(document.body?.dataset?.operatorDisplayName || OPERATOR_IDENTITY || 'Оператор').trim() || 'Оператор';
  const OPERATOR_AVATAR_URL = String(document.body?.dataset?.operatorAvatarUrl || '').trim();
  const STORAGE_WORKSPACE_DRAFT_PREFIX = 'iguana:dialogs:workspace-draft:';
  const WORKSPACE_DRAFT_AUTOSAVE_DELAY_MS = Math.max(300, Math.min(5000, Number(window.DIALOG_CONFIG?.workspace_draft_autosave_delay_ms) || 900));
  const WORKSPACE_DRAFT_TELEMETRY_MIN_INTERVAL_MS = Math.max(5000, Math.min(120000, Number(window.DIALOG_CONFIG?.workspace_draft_telemetry_interval_ms) || 30000));
  const DIALOGS_TELEMETRY_EVENT_GROUPS = Object.freeze({
    workspace_open_ms: 'performance',
    workspace_render_error: 'stability',
    workspace_fallback_to_legacy: 'stability',
    workspace_guardrail_breach: 'stability',
    workspace_abandon: 'engagement',
    workspace_experiment_exposure: 'experiment',
    kpi_frt_recorded: 'kpi',
    kpi_ttr_recorded: 'kpi',
    kpi_sla_breach_recorded: 'kpi',
    kpi_dialogs_per_shift_recorded: 'kpi',
    kpi_csat_recorded: 'kpi',
    triage_view_switch: 'triage',
    triage_preferences_saved: 'triage',
    triage_quick_assign: 'triage',
    triage_quick_snooze: 'triage',
    triage_quick_close: 'triage',
    triage_bulk_action: 'triage',
    macro_preview: 'macro',
    macro_apply: 'macro',
    workspace_draft_saved: 'workspace',
    workspace_draft_restored: 'workspace',
    workspace_context_profile_gap: 'workspace',
    workspace_context_source_gap: 'workspace',
    workspace_context_attribute_policy_gap: 'workspace',
    workspace_context_block_gap: 'workspace',
    workspace_context_contract_gap: 'workspace',
    workspace_context_sources_expanded: 'workspace',
    workspace_context_attribute_policy_expanded: 'workspace',
    workspace_context_extra_attributes_expanded: 'workspace',
    workspace_sla_policy_gap: 'workspace',
    workspace_parity_gap: 'workspace',
    workspace_inline_navigation: 'workspace',
    workspace_open_legacy_manual: 'workspace',
    workspace_open_legacy_blocked: 'workspace',
    workspace_rollout_packet_viewed: 'experiment',
  });
  let workspaceReadonlyMode = false;
  let workspaceComposerTicketId = '';
  let activeWorkspaceTicketId = INITIAL_DIALOG_TICKET_ID;
  let activeWorkspaceChannelId = null;
  let activeWorkspacePayload = null;
  let workspaceAiReviewProblemCandidates = [];
  let workspaceAiReviewSolutionCandidates = [];


  const BUSINESS_STYLES = (window.BUSINESS_CELL_STYLES && typeof window.BUSINESS_CELL_STYLES === 'object')
    ? window.BUSINESS_CELL_STYLES
    : {};

  const SUMMARY_BADGE_SETTINGS = (window.DIALOG_CONFIG?.summary_badges && typeof window.DIALOG_CONFIG.summary_badges === 'object')
    ? window.DIALOG_CONFIG.summary_badges
    : {};

  const DIALOG_EMOJI = ['😀','😁','😂','😊','😍','🤔','😢','😡','👍','🙏','🔥','🎉','✅','❗','📌'];

  function canRunAction(permissionKey) {
    if (!permissionKey) return true;
    if (workspaceReadonlyMode && WORKSPACE_MUTATING_PERMISSION_KEYS.includes(permissionKey)) {
      return false;
    }
    return OPERATOR_PERMISSIONS[permissionKey] !== false;
  }

  function resolveWorkspaceContractTicketId(ticketId = null) {
    const requestedTicketId = String(ticketId || activeDialogTicketId || activeWorkspaceTicketId || '').trim();
    const payloadTicketId = String(activeWorkspacePayload?.conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    if (!requestedTicketId || !payloadTicketId || requestedTicketId !== payloadTicketId) {
      return null;
    }
    return payloadTicketId;
  }

  function getWorkspaceActionGuard(actionKey, ticketId = null) {
    if (!actionKey) return null;
    const contractTicketId = resolveWorkspaceContractTicketId(ticketId);
    if (!contractTicketId) return null;
    const actions = activeWorkspacePayload?.workflow?.actions;
    if (!actions || typeof actions !== 'object') return null;
    const guard = actions[actionKey];
    return guard && typeof guard === 'object' ? guard : null;
  }

  function isWorkspaceActionEnabled(actionKey, fallbackEnabled, ticketId = null) {
    const guard = getWorkspaceActionGuard(actionKey, ticketId);
    return typeof guard?.enabled === 'boolean' ? guard.enabled === true : Boolean(fallbackEnabled);
  }

  function getWorkspaceWorkflowCollection(key, ticketId = null) {
    if (!key) return null;
    const contractTicketId = resolveWorkspaceContractTicketId(ticketId);
    if (!contractTicketId) return null;
    const value = activeWorkspacePayload?.workflow?.[key];
    return Array.isArray(value) ? value : null;
  }

  function resolveWorkspaceActionBlockedMessage(actionTitle, disabledReason) {
    switch (String(disabledReason || '').trim()) {
      case 'already_assigned_to_operator':
        return 'Диалог уже назначен на вас.';
      case 'closed_dialog':
        return actionTitle === 'Взять себе'
          ? 'Взять в работу можно только открытый диалог.'
          : actionTitle === 'Отложить'
            ? 'Отложить можно только открытый диалог.'
            : `Действие «${actionTitle}» доступно только для открытого диалога.`;
      case 'already_closed':
        return 'Диалог уже закрыт.';
      case 'not_closed':
        return 'Диалог ещё не закрыт.';
      case 'no_reassign_candidates':
        return 'Подходящих пользователей для передачи сейчас нет.';
      case 'no_participant_candidates':
        return '��������� ������������� ��� ���������� ������ ���.';
      case 'no_participants':
        return 'В диалоге нет участников для удаления.';
      default:
        return '';
    }
  }

  function notifyActionBlocked(actionKey, actionTitle, options = {}) {
    const permissionKey = options.permissionKey || null;
    if (permissionKey && !canRunAction(permissionKey)) {
      notifyPermissionDenied(actionTitle);
      return true;
    }
    const guard = getWorkspaceActionGuard(actionKey, options.ticketId);
    if (guard?.enabled === false) {
      const message = resolveWorkspaceActionBlockedMessage(actionTitle, guard.disabled_reason);
      if (message && typeof showNotification === 'function') {
        showNotification(message, 'warning');
      } else {
        notifyPermissionDenied(actionTitle);
      }
      return true;
    }
    return false;
  }


  function renderWorkspaceRolloutBanner(rollout) {
    dialogsWorkspaceRuntime?.renderWorkspaceRolloutBanner(rollout);
  }

  function renderWorkspaceParityBanner(parity) {
    dialogsWorkspaceRuntime?.renderWorkspaceParityBanner(parity);
  }

  function setWorkspaceReadonlyMode(isReadonly, reasonText) {
    dialogsWorkspaceRuntime?.setWorkspaceReadonlyMode(isReadonly, reasonText);
  }

  function resolveWorkspaceReadonlyReason(permissions) {
    return dialogsWorkspaceRuntime?.resolveWorkspaceReadonlyReason(permissions) || null;
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


  if (bulkSnoozeBtn) {
    bulkSnoozeBtn.textContent = `Отложить выбранные на ${formatSnoozeDurationLabel(QUICK_SNOOZE_MINUTES)}`;
  }
  if (hotkeySingleSnoozeLabel) {
    hotkeySingleSnoozeLabel.textContent = `Отложить диалог на ${formatSnoozeDurationLabel(QUICK_SNOOZE_MINUTES)} (из списка)`;
  }
  if (hotkeyBulkSnoozeLabel) {
    hotkeyBulkSnoozeLabel.textContent = `Отложить выбранные диалоги на ${formatSnoozeDurationLabel(QUICK_SNOOZE_MINUTES)}`;
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

  function isMacroTemplatePublished(template) {
    if (!template || typeof template !== 'object') return false;
    if (!Object.prototype.hasOwnProperty.call(template, 'published')) {
      return true;
    }
    return template.published === true || template.published === 'true' || template.published === 1 || template.published === '1';
  }

  function isMacroTemplateDeprecated(template) {
    if (!template || typeof template !== 'object') return false;
    return template.deprecated === true || template.deprecated === 'true' || template.deprecated === 1 || template.deprecated === '1';
  }

  const DIALOG_TEMPLATES = {
    categoryTemplates: Array.isArray(window.DIALOG_CONFIG?.category_templates)
      ? window.DIALOG_CONFIG.category_templates
      : [],
    questionTemplates: Array.isArray(window.DIALOG_CONFIG?.question_templates)
      ? window.DIALOG_CONFIG.question_templates
      : [],
    macroTemplates: Array.isArray(window.DIALOG_CONFIG?.macro_templates)
      ? window.DIALOG_CONFIG.macro_templates.filter((template) => isMacroTemplatePublished(template) && !isMacroTemplateDeprecated(template))
      : [],
    completionTemplates: Array.isArray(window.DIALOG_CONFIG?.completion_templates)
      ? window.DIALOG_CONFIG.completion_templates
      : [],
  };

  let activeDialogTicketId = null;
  let activeDialogChannelId = null;
  let activeDialogResponsibleRaw = '';
  let activeDialogResponsibleAvatarUrl = '';
  let activeDialogRow = null;
  let listPollTimer = null;
  let lastListMarker = null;
  let listLoading = false;
  let activeDialogContext = {
    clientName: '—',
    clientUserId: '',
    operatorName: '—',
    channelName: '—',
    business: '—',
    location: '—',
    status: '—',
    createdAt: '—',
  };
  let selectedCategories = new Set();
  const selectedTicketIds = new Set();
  let detailsCategoryModalState = {
    suppressDetailsReset: false,
    reopenAfterClose: false,
  };
  const workspaceExperimentContext = resolveWorkspaceExperimentContext();
  const WORKSPACE_EXPERIENCE_ENABLED = resolveWorkspaceExperienceEnabled();
  const INITIAL_MY_DIALOGS = (window.INITIAL_MY_DIALOGS && typeof window.INITIAL_MY_DIALOGS === 'object')
    ? window.INITIAL_MY_DIALOGS
    : {};

  function hashStringToBucket(source) {
    const value = String(source || '');
    let hash = 0;
    for (let index = 0; index < value.length; index += 1) {
      hash = ((hash * 31) + value.charCodeAt(index)) % 10000;
    }
    return hash;
  }

  function resolveWorkspaceExperimentContext() {
    const operatorOverride = WORKSPACE_AB_TEST_CONFIG.operatorOverrides[String(OPERATOR_IDENTITY || '').toLowerCase()];
    const overrideCohort = normalizeExperimentCohort(operatorOverride?.cohort);
    const operatorSegment = String(operatorOverride?.segment || WORKSPACE_AB_TEST_CONFIG.operatorSegment || 'all_operators').trim() || 'all_operators';

    if (!WORKSPACE_AB_TEST_CONFIG.enabled || WORKSPACE_AB_TEST_CONFIG.rolloutPercent <= 0) {
      return {
        experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
        cohort: overrideCohort || 'disabled',
        operatorSegment,
      };
    }

    if (overrideCohort) {
      return {
        experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
        cohort: overrideCohort,
        operatorSegment,
      };
    }

    const cohortSeed = `${WORKSPACE_AB_TEST_CONFIG.experimentName}:${OPERATOR_IDENTITY}`;
    const bucket = hashStringToBucket(cohortSeed) % 100;
    const deterministicCohort = bucket < WORKSPACE_AB_TEST_CONFIG.rolloutPercent ? 'test' : 'control';

    let cached = null;
    try {
      cached = localStorage.getItem(STORAGE_WORKSPACE_AB);
    } catch (_error) {
      cached = null;
    }
    if (cached === 'test' || cached === 'control') {
      if (cached !== deterministicCohort) {
        try {
          localStorage.setItem(STORAGE_WORKSPACE_AB, deterministicCohort);
        } catch (_error) {
          // noop: cohort persistence is best-effort only
        }
      }
      return {
        experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
        cohort: deterministicCohort,
        operatorSegment,
      };
    }

    try {
      localStorage.setItem(STORAGE_WORKSPACE_AB, deterministicCohort);
    } catch (_error) {
      // noop: cohort persistence is best-effort only
    }
    return {
      experimentName: WORKSPACE_AB_TEST_CONFIG.experimentName,
      cohort: deterministicCohort,
      operatorSegment,
    };
  }

  function resolveWorkspaceExperienceEnabled() {
    if (!WORKSPACE_V1_ENABLED) {
      return false;
    }
    if (WORKSPACE_FORCE_MODE) {
      return true;
    }
    if (!WORKSPACE_AB_TEST_CONFIG.enabled) {
      return true;
    }
    return workspaceExperimentContext.cohort === 'test';
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
  let columnOrder = columnMeta.map((item) => item.key);
  let myDialogsState = {
    new: Array.isArray(INITIAL_MY_DIALOGS.new)
      ? INITIAL_MY_DIALOGS.new.filter((item) => item && typeof item === 'object')
      : [],
    unanswered: Array.isArray(INITIAL_MY_DIALOGS.unanswered)
      ? INITIAL_MY_DIALOGS.unanswered.filter((item) => item && typeof item === 'object')
      : [],
    inWork: Array.isArray(INITIAL_MY_DIALOGS.in_work || INITIAL_MY_DIALOGS.inWork)
      ? (INITIAL_MY_DIALOGS.in_work || INITIAL_MY_DIALOGS.inWork).filter((item) => item && typeof item === 'object')
      : [],
  };

  function loadPageSize() {
    dialogsListRuntime?.loadPageSize();
  }

  function configureSlaWindowSelect() {
    dialogsListRuntime?.configureSlaWindowSelect();
  }

  function persistPageSize() {
    dialogsListRuntime?.persistPageSize();
  }

  function restoreDialogPreferences() {
    dialogsListRuntime?.restoreDialogPreferences();
  }

  function persistDialogPreferences() {
    dialogsListRuntime?.persistDialogPreferences();
  }

  async function loadServerTriagePreferences() {
    return await dialogsListRuntime?.loadServerTriagePreferences();
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
    return dialogsTemplatesRuntime?.normalizeCategories(value) || [];
  }

  function renderCategoryBadges(categories) {
    return dialogsTemplatesRuntime?.renderCategoryBadges(categories)
      || '<span class="text-muted">—</span>';
  }

  function updateSummaryCategories(label) {
    dialogsTemplatesRuntime?.updateSummaryCategories(label);
  }

  function updateDetailsLocationLabel(value) {
    if (!detailsLocation) return;
    const safeValue = String(value || '—').trim() || '—';
    detailsLocation.textContent = `Локация: ${safeValue}`;
  }

  function scheduleCategorySave() {
    dialogsTemplatesRuntime?.scheduleCategorySave();
  }

  function buildDialogItemMarker(item) {
    return [
      item?.ticketId || '',
      item?.requestNumber || '',
      item?.clientName || item?.username || '',
      item?.clientStatus || '',
      item?.channelId || '',
      item?.channelName || '',
      item?.business || '',
      item?.problem || '',
      item?.location || item?.city || item?.locationName || '',
      item?.categoriesSafe || item?.categories || '',
      resolveResponsibleRawFromItem(item),
      item?.responsible || item?.resolvedBy || '',
      item?.responsibleAvatarUrl || '',
      item?.status || '',
      item?.statusKey || '',
      Number(item?.unreadCount) || 0,
      item?.lastMessageSender || '',
      item?.lastMessageTimestamp || '',
      item?.createdAt || '',
      item?.createdDateSafe || item?.createdDate || '',
      item?.createdTimeSafe || item?.createdTime || '',
      Number(item?.rating) || 0,
      getDialogUserId(item) || '',
    ].join('|');
  }

  function createDialogRowElement(item) {
    const wrapper = document.createElement('tbody');
    wrapper.innerHTML = renderDialogRow(item).trim();
    const row = wrapper.firstElementChild;
    if (!row) return null;
    row.dataset.dialogMarker = String(item?.dialogMarker || '').trim() || buildDialogItemMarker(item);
    hydrateAvatars(row);
    return row;
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
      .map((item) => {
        if (item && typeof item === 'object' && typeof item.dialogMarker === 'string' && item.dialogMarker.trim()) {
          return item.dialogMarker.trim();
        }
        return buildDialogItemMarker(item);
      })
      .sort((left, right) => left.localeCompare(right))
      .join('||');
  }

  function buildDialogsMarkerFromRows(rows) {
    const normalizedRows = Array.isArray(rows) ? rows : [];
    if (!normalizedRows.length) return 'empty';
    return normalizedRows
      .map((row) => String(row?.dataset?.dialogMarker || '').trim())
      .filter(Boolean)
      .sort((left, right) => left.localeCompare(right))
      .join('||') || 'empty';
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
    if (normalizedKey === 'auto_processing') return 'dialog-status-badge status-auto-processing';
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
    dialogsDetailsHistoryRuntime?.resetReplyTarget();
  }

  function setReplyTarget(messageId, preview) {
    dialogsDetailsHistoryRuntime?.setReplyTarget(messageId, preview);
  }

  function resetWorkspaceReplyTarget(options = {}) {
    dialogsWorkspaceRuntime?.resetWorkspaceReplyTarget(options);
  }

  function setWorkspaceReplyTarget(messageId, preview) {
    dialogsWorkspaceRuntime?.setWorkspaceReplyTarget(messageId, preview);
  }

  let dialogsTemplatesRuntime = null;
  let dialogsMacroRuntime = null;
  let dialogsExperimentRuntime = null;
  let dialogsParticipantsRuntime = null;
  let dialogsDetailsRuntime = null;
  let dialogsMyDialogsRuntime = null;
  let dialogsAvatarRuntime = null;
  let dialogsSlaRuntime = null;
  let dialogsPresentationRuntime = null;
  dialogsShellRuntime = window.DialogsShellRuntime?.createRuntime({
    debugLog,
    workspaceEnabled: OPEN_DIALOGS_IN_WORKSPACE,
    openDialogWithWorkspaceFallback,
    openDialogDetails,
    elements: {
      dialogCompactToggle,
      dialogListOnlyToggle,
      columnsBtn,
      columnsList,
      columnsApply,
      columnsReset,
      columnsModalEl,
      columnsModal,
      detailsSidebar,
      detailsResizeHandle,
      table,
    },
    storage: {
      compactMode: STORAGE_COMPACT_MODE,
      listOnlyMode: STORAGE_LIST_ONLY_MODE,
      columns: STORAGE_COLUMNS,
      columnOrder: STORAGE_COLUMN_ORDER,
      widths: STORAGE_WIDTHS,
      task: STORAGE_TASK,
    },
    getColumnMeta: () => columnMeta,
    getColumnState: () => columnState,
    setColumnState: (nextState) => {
      columnState = nextState && typeof nextState === 'object'
        ? nextState
        : { ...defaultColumnState };
    },
    getDefaultColumnState: () => defaultColumnState,
    getColumnOrder: () => columnOrder,
    setColumnOrder: (nextOrder) => {
      columnOrder = Array.isArray(nextOrder) && nextOrder.length
        ? nextOrder.map((item) => String(item || '').trim()).filter(Boolean)
        : columnMeta.map((item) => item.key);
    },
    getDefaultColumnOrder: () => columnMeta.map((item) => item.key),
    getHeaderCells: () => headerCells,
    rowsList,
  }) || null;
  dialogsParticipantsRuntime = window.DialogsParticipantsRuntime?.createRuntime({
    elements: {
      detailsParticipantsSection,
      detailsParticipantsState,
      detailsParticipantsList,
      participantsCurrentState,
      participantsCurrentList,
      participantsSelect,
      participantsAddBtn,
      participantsSelectHint,
      reassignSelect,
      reassignSubmit,
      reassignHint,
      reassignCurrent,
      detailsParticipantsBtn,
      detailsParticipantsManageBtn,
      detailsReassignBtn,
      participantsMeta,
      participantsModalEl,
      participantsModal,
      reassignMeta,
      reassignModalEl,
      reassignModal,
    },
    escapeHtml,
    formatTimestamp,
    normalizeIdentity,
    buildResponsibleAvatarSpec,
    renderResponsibleCell,
    canRunAction,
    isWorkspaceActionEnabled,
    getWorkspaceActionGuard,
    getWorkspaceWorkflowCollection,
    notifyActionBlocked,
    showNotification,
    showModalSafe,
    hideModalSafe,
    getActiveDialogTicketId: () => activeDialogTicketId,
    getActiveDialogResponsibleRaw: () => activeDialogResponsibleRaw,
    getActiveDialogContext: () => activeDialogContext,
    getActiveDialogResponsibleAvatarUrl: () => activeDialogResponsibleAvatarUrl,
    getActiveDialogRow: () => activeDialogRow,
    findRowByTicketId: (ticketId) => table.querySelector(`tr[data-ticket-id="${escapeSelectorValue(ticketId)}"]`),
    updateRowResponsible,
    updateDetailsResponsible,
  }) || null;
  dialogsMyDialogsRuntime = window.DialogsMyDialogsRuntime?.createRuntime({
    elements: {
      panel: myDialogsPanel,
      count: myDialogsCount,
      empty: myDialogsEmpty,
      newSection: myDialogsNewSection,
      newList: myDialogsNewList,
      unansweredSection: myDialogsUnansweredSection,
      unansweredList: myDialogsUnansweredList,
      inWorkSection: myDialogsInWorkSection,
      inWorkList: myDialogsInWorkList,
    },
    escapeHtml,
    formatTimestamp,
    rowsList,
    isOwnedByCurrentOperator,
    isResolvedRow: isResolved,
    getMyDialogsState: () => myDialogsState,
    setMyDialogsState: (state) => {
      myDialogsState = state && typeof state === 'object'
        ? state
        : { new: [], unanswered: [], inWork: [] };
    },
    getActiveDialogTicketId: () => activeDialogTicketId,
    findRowByTicketId: (ticketId) => table.querySelector(`tr[data-ticket-id="${escapeSelectorValue(ticketId)}"]`),
    setActiveDialogRow,
    openDialogSurface,
  }) || null;
  dialogsAvatarRuntime = window.DialogsAvatarRuntime?.createRuntime({
    escapeHtml,
    avatarInitial,
    buildAvatarUrl,
    normalizeIdentity,
    normalizeMessageSenderByType,
    operatorIdentity: OPERATOR_IDENTITY,
    operatorDisplayName: OPERATOR_DISPLAY_NAME,
    operatorAvatarUrl: OPERATOR_AVATAR_URL,
    getActiveDialogContext: () => activeDialogContext,
  }) || null;
  dialogsSlaRuntime = window.DialogsSlaRuntime?.createRuntime({
    slaTargetMinutes: SLA_TARGET_MINUTES,
    slaWarningMinutes: SLA_WARNING_MINUTES,
    parseUtcDateValue,
    formatUtcDate,
    isResolvedRow: isResolved,
    isCriticalSlaDialog,
    isEscalationRequiredDialog,
    rowsList,
    updateRowQuickActions,
  }) || null;
  dialogsPresentationRuntime = window.DialogsPresentationRuntime?.createRuntime({
    escapeHtml,
    formatWorkspaceDateTime,
    buildMediaMarkup,
    renderMessageAvatar,
    resolveWorkspaceMessageAvatarSpec,
    getActiveWorkspacePayload: () => activeWorkspacePayload,
  }) || null;
  const dialogsFlowRuntime = window.DialogsFlowRuntime?.createRuntime({
    elements: {
      quickSearch,
      hotkeysModalEl,
      hotkeysModal,
      sortModeSelect,
      detailsModalEl,
      detailsResolve,
      detailsReopen,
      detailsReplyText,
      detailsReplyMedia,
      categoriesModal,
      participantsModalEl,
      participantsModal,
      reassignModalEl,
      reassignModal,
    },
    debugLog,
    workspaceEnabled: OPEN_DIALOGS_IN_WORKSPACE,
    workspaceAbandonEventGroup: DIALOGS_TELEMETRY_EVENT_GROUPS.workspace_abandon,
    workspaceExperimentContext,
    workspacePrimaryKpis: WORKSPACE_AB_TEST_CONFIG.primaryKpis,
    workspaceSecondaryKpis: WORKSPACE_AB_TEST_CONFIG.secondaryKpis,
    quickSnoozeMinutes: QUICK_SNOOZE_MINUTES,
    getFilterState: () => filterState,
    setLastManualSortMode: (value) => {
      lastManualSortMode = value || 'default';
    },
    getCategoryPanelState: () => detailsCategoryModalState,
    confirmWorkspaceTicketSwitch,
    setWorkspaceReadonlyMode,
    buildWorkspaceDialogUrl,
    openDialogWithWorkspaceFallback,
    openDialogDetails,
    showModalSafe,
    setViewTab,
    applyStatusFilter,
    persistDialogPreferences,
    applyFilters,
    canRunAction,
    notifyPermissionDenied,
    runBulkAction,
    formatSnoozeActionLabel,
    getShortcutTargetRow,
    setActiveDialogRow,
    takeDialog,
    snoozeDialog,
    setSnooze,
    updateRowQuickActions,
    closeDialogQuick,
    clearSnooze,
    showNotification,
    openVisibleDialogByOffset,
    startHistoryPolling,
    hideModalSafe,
    setActiveDialogTicketId: (value) => {
      activeDialogTicketId = value ? String(value).trim() : null;
    },
    initMacroVariableCatalog,
    setActiveDialogChannelId: (value) => {
      activeDialogChannelId = value ? String(value).trim() : null;
    },
    setActiveDialogResponsibleState: (raw, avatarUrl) => {
      activeDialogResponsibleRaw = String(raw || '').trim();
      activeDialogResponsibleAvatarUrl = String(avatarUrl || '').trim();
    },
    resetReplyTarget,
    renderHistory,
    resetPreviousDialogHistoryState,
    setActiveDialogContext: (context) => {
      activeDialogContext = context && typeof context === 'object' ? context : activeDialogContext;
    },
    setSelectedCategories: (categories) => {
      selectedCategories = categories instanceof Set ? categories : new Set();
    },
    setDialogParticipantsState,
    renderDialogParticipantsLoadingState,
    syncParticipantsSelectOptions,
    syncReassignSelectOptions,
    updateDetailsTakeButton,
    stopHistoryPolling,
    isWorkspaceDialogPath,
    getActiveWorkspaceTicketId: () => activeWorkspaceTicketId,
  }) || null;
  dialogsExperimentRuntime = window.DialogsExperimentRuntime?.createRuntime({
    elements: {
      experimentInfoMeta,
      experimentPrimaryKpis,
      experimentSecondaryKpis,
      experimentTelemetrySummaryState,
      experimentTelemetryGuardrailState,
      experimentTelemetryGuardrailAlerts,
      experimentRolloutDecisionState,
      experimentRolloutDecisionChecklist,
      experimentRolloutPacketState,
      experimentRolloutPacketChecklist,
      experimentRolloutPacketWrap,
      experimentRolloutPacketRows,
      experimentRolloutScorecardWrap,
      experimentRolloutScorecardRows,
      experimentRolloutKpiOutcomesWrap,
      experimentRolloutKpiOutcomeRows,
      experimentTelemetrySummaryRows,
      experimentGapBreakdownWrap,
      experimentGapBreakdownRows,
      experimentTelemetryShiftRows,
      experimentTelemetryTeamRows,
      experimentTelemetryRefreshBtn,
      experimentTelemetryAutoRefresh,
    },
    workspaceAbTestConfig: WORKSPACE_AB_TEST_CONFIG,
    workspaceExperimentContext,
    escapeHtml,
    formatTimestamp,
    formatWorkspaceDateTime,
  }) || null;
  const dialogsNotificationsRuntime = window.DialogsNotificationsRuntime?.createRuntime({
    elements: {
      detailsUnreadCount,
    },
    getActiveDialogState: () => ({
      row: activeDialogRow,
    }),
  }) || null;

  const dialogsWorkspaceRuntime = window.DialogsWorkspaceRuntime?.createRuntime({
    elements: {
      workspaceRolloutBanner,
      workspaceParityBanner,
      workspaceReadonlyBanner,
      workspaceShell,
      workspaceConversationTitle,
      workspaceConversationMeta,
      workspaceComposerText,
      workspaceComposerSend,
      workspaceComposerMediaTrigger,
      workspaceComposerMedia,
      workspaceComposerSaveDraft,
      workspaceComposerDraftState,
      workspaceComposerMacroApply,
      workspaceComposerMacroSelect,
      workspaceReplyTarget,
      workspaceReplyTargetText,
      workspaceReplyTargetClear,
      workspaceMessagesState,
      workspaceMessagesList,
      workspaceMessagesLoadMoreWrap,
      workspaceMessagesLoadMore,
      workspaceMessagesRetry,
      workspaceMessagesError,
      workspaceNavPrevBtn,
      workspaceNavNextBtn,
      workspaceNavState,
      workspaceClientState,
      workspaceClientContent,
      workspaceClientRetry,
      workspaceClientError,
      workspaceHistoryState,
      workspaceHistoryContent,
      workspaceHistoryRetry,
      workspaceHistoryError,
      workspaceRelatedEventsState,
      workspaceRelatedEventsContent,
      workspaceRelatedEventsRetry,
      workspaceRelatedEventsError,
      workspaceSlaState,
      workspaceSlaContent,
      workspaceSlaRetry,
      workspaceSlaError,
      workspaceCategoriesState,
      workspaceCategoriesList,
      workspaceCategoriesError,
      workspaceCategoriesClear,
    },
    getActiveWorkspaceState: () => ({
      ticketId: activeWorkspaceTicketId,
      channelId: activeWorkspaceChannelId,
      composerTicketId: workspaceComposerTicketId,
      payload: activeWorkspacePayload,
      row: activeDialogRow,
      readonlyMode: workspaceReadonlyMode,
    }),
    workspaceEnabled: WORKSPACE_V1_ENABLED,
    inlineNavigation: WORKSPACE_INLINE_NAVIGATION,
    workspaceDisableLegacyFallback: WORKSPACE_DISABLE_LEGACY_FALLBACK,
    workspaceOpenSloMs: WORKSPACE_OPEN_SLO_MS,
    workspaceFailureStreakThreshold: WORKSPACE_FAILURE_STREAK_THRESHOLD,
    workspaceFailureCooldownMs: WORKSPACE_FAILURE_COOLDOWN_MS,
    workspaceContractTimeoutMs: WORKSPACE_CONTRACT_TIMEOUT_MS,
    workspaceContractRetryAttempts: WORKSPACE_CONTRACT_RETRY_ATTEMPTS,
    workspaceContractInclude: WORKSPACE_CONTRACT_INCLUDE,
    workspaceTelemetryEventGroups: DIALOGS_TELEMETRY_EVENT_GROUPS,
    workspaceExperimentContext,
    workspacePrimaryKpis: WORKSPACE_AB_TEST_CONFIG.primaryKpis,
    workspaceSecondaryKpis: WORKSPACE_AB_TEST_CONFIG.secondaryKpis,
    messagesPageLimit: WORKSPACE_MESSAGES_PAGE_LIMIT,
    draftStoragePrefix: STORAGE_WORKSPACE_DRAFT_PREFIX,
    draftAutosaveDelay: WORKSPACE_DRAFT_AUTOSAVE_DELAY_MS,
    draftTelemetryMinInterval: WORKSPACE_DRAFT_TELEMETRY_MIN_INTERVAL_MS,
    extraAttributesMax: WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_MAX,
    extraAttributesCollapseAfter: WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_COLLAPSE_AFTER,
    extraAttributesHideTechnical: WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_HIDE_TECHNICAL,
    extraAttributesTechnicalPrefixes: WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_TECHNICAL_PREFIXES,
    hiddenAttributes: WORKSPACE_CLIENT_HIDDEN_ATTRIBUTES,
    escapeHtml,
    formatTimestamp,
    formatWorkspaceDateTime,
    workspaceMutatingPermissionKeys: WORKSPACE_MUTATING_PERMISSION_KEYS,
    getActiveDialogContext: () => activeDialogContext,
    setActiveDialogContext: (context) => {
      activeDialogContext = context && typeof context === 'object' ? context : activeDialogContext;
    },
    getDialogUserId,
    operatorDisplayName: OPERATOR_DISPLAY_NAME,
    setWorkspaceReadonlyMode,
    resolveWorkspaceReadonlyReason,
    normalizeCategories,
    getSelectedCategories: () => selectedCategories,
    getActiveDialogTicketId: () => activeDialogTicketId,
    dialogTemplates: DIALOG_TEMPLATES,
    canRunAction,
    isWorkspaceActionEnabled,
    handleMediaSurfaceClick: (event) => dialogsDetailsHistoryRuntime?.handleMediaSurfaceClick(event),
    setSelectedCategories: (categories) => {
      selectedCategories = categories instanceof Set ? categories : new Set();
    },
    setWorkspaceComposerTicketId: (ticketId) => {
      workspaceComposerTicketId = String(ticketId || '').trim();
    },
    setWorkspaceReadonlyState: (nextState) => {
      workspaceReadonlyMode = nextState === true;
    },
    setActiveWorkspaceTicketId: (ticketId) => {
      activeWorkspaceTicketId = String(ticketId || '').trim();
    },
    setActiveWorkspaceChannelId: (channelId) => {
      activeWorkspaceChannelId = channelId ?? null;
    },
    getWorkspaceComposerMeta: () => dialogsMacroRuntime?.getWorkspaceComposerMeta() || {
      hasActiveMacroTemplate: false,
      macroTemplatesLength: 0,
    },
    loadWorkspaceAiSuggestions,
    loadWorkspaceAiReview,
    loadWorkspaceAiControl,
    updateWorkspaceActionButtons,
    renderWorkspaceMessageItem,
    extractClipboardImageFiles,
    stageMediaFilesInInput,
    getPendingMediaFiles,
    clearPendingMediaFiles,
    hydrateMediaRoot: (root) => dialogsDetailsHistoryRuntime?.hydrateMediaRoot?.(root),
    sendWorkspaceMediaFiles,
    withChannelParam,
    openDialogDetails,
    setActiveWorkspacePayload: (payload) => {
      activeWorkspacePayload = payload;
    },
    rowsList,
    setActiveDialogRow,
    openVisibleDialogByOffset,
    updateBulkToolbarState,
    renderTableFromRows,
    updateRowStatus,
    updateRowResponsible,
    applyFilters,
    notifyPermissionDenied,
    showNotification,
  }) || null;

  const dialogsDetailsHistoryRuntime = window.DialogsDetailsHistoryRuntime?.createRuntime({
    elements: {
      detailsHistory,
      detailsReplyText,
      detailsReplySend,
      detailsReplyMediaTrigger,
      detailsReplyMedia,
      replyTarget,
      replyTargetText,
      replyTargetClear,
      mediaPreviewModalEl,
      mediaPreviewModal,
      mediaPreviewVideo,
      mediaPreviewImage,
      mediaPreviewImageControls,
      mediaPreviewDownloadLink,
    },
    getActiveDialogState: () => ({
      ticketId: activeDialogTicketId,
      channelId: activeDialogChannelId,
      row: activeDialogRow,
      context: activeDialogContext,
    }),
    getActiveWorkspaceState: () => ({
      ticketId: activeWorkspaceTicketId,
      payload: activeWorkspacePayload,
      composerText: workspaceComposerText,
      composerSend: workspaceComposerSend,
      composerMedia: workspaceComposerMedia,
      composerTicketId: activeWorkspaceTicketId,
      messagesState: workspaceMessagesState,
      messagesError: workspaceMessagesError,
    }),
    showModalSafe,
    hideModalSafe,
    escapeHtml,
    formatTimestamp,
    normalizeMessageSenderByType,
    resolveSenderLabel,
    renderMessageAvatar,
    resolveDialogMessageAvatarSpec,
    withChannelParam,
    showNotification,
    updateDialogUnreadCount: (unreadCount) => dialogsNotificationsRuntime?.updateDialogUnreadCount(unreadCount),
    requestSidebarNotificationRefresh: (source = 'dialogs') => dialogsNotificationsRuntime?.requestSidebarNotificationRefresh(source),
    updateDetailsResponsible,
    updateRowStatus,
    updateDetailsStatusSummary,
    emitWorkspaceTelemetry,
    saveWorkspaceDraft,
    reloadWorkspaceSection,
    operatorDisplayName: OPERATOR_DISPLAY_NAME,
    historyPollInterval: HISTORY_POLL_INTERVAL,
  }) || null;

  const dialogsActionsRuntime = window.DialogsActionsRuntime?.createRuntime({
    elements: {
      table,
      detailsMeta,
      detailsResolve,
      detailsSpam,
      detailsReopen,
      detailsCategoriesBtn,
      detailsTakeBtn,
      detailsReplyText,
      detailsReplySend,
      workspaceAssignBtn,
      workspaceSnoozeBtn,
      workspaceResolveBtn,
      workspaceReopenBtn,
      workspaceLegacyBtn,
      workspaceCreateTaskBtn,
    },
    getActiveDialogState: () => ({
      ticketId: activeDialogTicketId,
      row: activeDialogRow,
    }),
    setActiveDialogTicketId: (ticketId) => {
      activeDialogTicketId = String(ticketId || '').trim() || null;
    },
    getActiveWorkspaceState: () => ({
      ticketId: activeWorkspaceTicketId,
      payload: activeWorkspacePayload,
      readonlyMode: workspaceReadonlyMode,
    }),
    getSelectedCategories: () => selectedCategories,
    quickSnoozeMinutes: QUICK_SNOOZE_MINUTES,
    workspaceSingleMode: WORKSPACE_SINGLE_MODE,
    workspaceDisableLegacyFallback: WORKSPACE_DISABLE_LEGACY_FALLBACK,
    canRunAction,
    isWorkspaceActionEnabled,
    canTakeDialogOwnership,
    resolveRowResponsibleRaw,
    getSnoozeUntil,
    formatUtcDate,
    formatSnoozeActionLabel,
    formatSnoozeDurationLabel,
    isResolvedRow: isResolved,
    isResolvedStatus,
    notifyPermissionDenied,
    updateRowSlaBadge,
    updateDetailsResponsible,
    updateDetailsStatusSummary,
    emitWorkspaceTelemetry,
    applyFilters,
    loadDialogParticipants,
    openDialogDetails,
    openCategoryPanel,
    renderWorkspaceCategories,
    refreshActiveWorkspaceContract,
    renderWorkspaceRolloutBanner,
    setActiveDialogRow,
    setSnooze,
    clearSnooze,
    setRowUnreadCount: (row, unreadCount) => dialogsNotificationsRuntime?.setRowUnreadCount(row, unreadCount),
    statusClassByKey,
    renderResponsibleCell,
    isOwnedByCurrentOperator,
    operatorAvatarUrl: OPERATOR_AVATAR_URL,
    getResponsibleColumnIndex: () => table.querySelector('th[data-column-key="responsible"]')?.cellIndex ?? -1,
    syncMyDialogsStateFromTable,
    renderMyDialogsPanel,
    getActiveReplyToTelegramId: () => dialogsDetailsHistoryRuntime?.getActiveReplyToTelegramId?.() ?? null,
    resetReplyTarget,
    getActiveDialogContext: () => activeDialogContext,
    operatorDisplayName: OPERATOR_DISPLAY_NAME,
    appendHistoryMessage,
    extractClipboardImageFiles,
    stageMediaFilesInInput,
    getPendingMediaFiles,
    clearPendingMediaFiles,
    sendMediaFiles,
    showNotification,
  }) || null;

  dialogsTemplatesRuntime = window.DialogsTemplatesRuntime?.createRuntime({
    elements: {
      detailsReplyText,
      detailsReplyEmojiTrigger,
      emojiPanel,
      emojiList,
      categoryTemplatesSection,
      categoryTemplateSelect,
      categoryTemplateList,
      categoryTemplateEmpty,
      questionTemplatesSection,
      questionTemplateSelect,
      questionTemplateList,
      questionTemplateEmpty,
      completionTemplatesSection,
      completionTemplateSelect,
      completionTemplateList,
      completionTemplateEmpty,
      completionTemplatesToggle,
      templateToggleButtons: Array.from(templateToggleButtons || []),
      macroTemplatesSection,
      workspaceCategoriesList,
      workspaceCategoriesClear,
      workspaceCategoriesError,
      detailsCategories,
      detailsSummary,
      detailsCategoriesBtn,
      categoriesModalEl,
      categoriesModal,
      detailsModalEl,
      detailsModal,
    },
    getSelectedCategories: () => selectedCategories,
    setSelectedCategories: (nextCategories) => {
      selectedCategories = nextCategories instanceof Set ? nextCategories : new Set();
    },
    getTemplateConfig: () => ({
      categoryTemplates: DIALOG_TEMPLATES.categoryTemplates,
      questionTemplates: DIALOG_TEMPLATES.questionTemplates,
      completionTemplates: DIALOG_TEMPLATES.completionTemplates,
      emoji: DIALOG_EMOJI,
    }),
    getDialogState: () => ({
      activeDialogTicketId,
      activeWorkspaceTicketId,
      detailsModalOpen: detailsModalEl?.classList.contains('show') === true,
    }),
    getActiveDialogRow: () => activeDialogRow,
    getCategoriesColumnIndex: () => table.querySelector('th[data-column-key="categories"]')?.cellIndex ?? -1,
    getCategoryPanelState: () => detailsCategoryModalState,
    setCategoryPanelState: (nextState) => {
      detailsCategoryModalState = {
        suppressDetailsReset: nextState?.suppressDetailsReset === true,
        reopenAfterClose: nextState?.reopenAfterClose === true,
      };
    },
    escapeHtml,
    buildTemplateOptions,
    findTemplateByValue,
    notifyActionBlocked,
    showModalSafe,
    showNotification,
    renderWorkspaceCategories,
    formatCategoriesLabel,
  }) || null;

  dialogsMacroRuntime = window.DialogsMacroRuntime?.createRuntime({
    elements: {
      macroTemplatesSection,
      macroTemplateSelect,
      macroTemplateSearch,
      macroTemplatePreview,
      macroTemplateMeta,
      macroTemplateApply,
      macroTemplateEmpty,
      macroVariableCatalog,
      workspaceComposerMacroSection,
      workspaceComposerMacroSearch,
      workspaceComposerMacroSelect,
      workspaceComposerMacroApply,
      workspaceComposerMacroPreview,
      workspaceComposerMacroMeta,
    },
    getActiveDialogState: () => ({
      ticketId: activeDialogTicketId,
      row: activeDialogRow,
      context: activeDialogContext,
    }),
    getActiveWorkspaceState: () => ({
      ticketId: activeWorkspaceTicketId,
      composerTicketId: workspaceComposerTicketId,
      composerText: workspaceComposerText,
    }),
    getMacroTemplates: () => DIALOG_TEMPLATES.macroTemplates,
    buildTemplateOptions,
    findTemplateByValue,
    insertReplyText,
    takeDialog,
    snoozeDialog,
    closeDialogQuick,
    setSnooze,
    updateRowQuickActions,
    applyFilters,
    rowsList,
    saveWorkspaceDraft,
    emitWorkspaceTelemetry,
    showNotification,
  }) || null;

  function resetMediaPreview() {
    dialogsDetailsHistoryRuntime?.resetMediaPreview();
  }

  function showImagePreview(src, name) {
    dialogsDetailsHistoryRuntime?.showImagePreview(src, name);
  }

  function showVideoPreview(src) {
    dialogsDetailsHistoryRuntime?.showVideoPreview(src);
  }

  function escapeSelectorValue(value) {
    if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
      return CSS.escape(value);
    }
    return String(value).replace(/"/g, '\\"');
  }

  function avatarInitial(name) {
    return dialogsShellRuntime?.avatarInitial(name) || '—';
  }

  function resolveChannelToneKey(label) {
    return dialogsPresentationRuntime?.resolveChannelToneKey(label) || 'custom';
  }

  function renderChannelBadge(label) {
    return dialogsPresentationRuntime?.renderChannelBadge(label)
      || '<span class="dialog-channel-pill channel-tone-custom">Без канала</span>';
  }

  function buildResponsibleAvatarSpec(responsible, avatarUrl = '') {
    return dialogsAvatarRuntime?.buildResponsibleAvatarSpec(responsible, avatarUrl) || null;
  }

  function renderResponsibleCell(responsible, avatarUrl = '') {
    return dialogsAvatarRuntime?.renderResponsibleCell(responsible, avatarUrl)
      || '<span class="text-muted">—</span>';
  }

  function normalizeIdentity(value) {
    return String(value || '').trim().toLowerCase();
  }

  function isOwnedByCurrentOperator(responsible) {
    const owner = normalizeIdentity(responsible);
    if (!owner || owner === '—' || owner === '-') return false;
    return owner === normalizeIdentity(OPERATOR_IDENTITY);
  }

  function normalizeMyDialogsCollection(items) {
    return dialogsMyDialogsRuntime?.normalizeMyDialogsCollection(items) || [];
  }

  function resolveResponsibleRawFromItem(item) {
    return dialogsMyDialogsRuntime?.resolveResponsibleRawFromItem(item) || '';
  }

  function resolveRowResponsibleRaw(row) {
    return dialogsMyDialogsRuntime?.resolveRowResponsibleRaw(row) || '';
  }

  function normalizeMyDialogsState(payload) {
    dialogsMyDialogsRuntime?.normalizeMyDialogsState(payload);
  }

  function isMyDialogItemUnanswered(dialog) {
    return dialogsMyDialogsRuntime?.isMyDialogItemUnanswered(dialog) === true;
  }

  function isMyDialogItemClosed(dialog) {
    return dialogsMyDialogsRuntime?.isMyDialogItemClosed(dialog) === true;
  }

  function buildMyDialogStateFromRow(row) {
    return dialogsMyDialogsRuntime?.buildMyDialogStateFromRow(row) || null;
  }

  function syncMyDialogsStateFromTable() {
    dialogsMyDialogsRuntime?.syncMyDialogsStateFromTable();
  }

  function formatMyDialogLastActivity(dialog) {
    return dialogsMyDialogsRuntime?.formatMyDialogLastActivity(dialog) || 'Активность пока не зафиксирована';
  }

  function renderMyDialogItem(dialog) {
    return dialogsMyDialogsRuntime?.renderMyDialogItem(dialog) || '';
  }

  function renderMyDialogsPanel() {
    dialogsMyDialogsRuntime?.renderMyDialogsPanel();
  }

  function buildAvatarUrl(userId) {
    return dialogsShellRuntime?.buildAvatarUrl(userId) || '';
  }

  function renderMessageAvatar(spec, extraClassName = '') {
    return dialogsAvatarRuntime?.renderMessageAvatar(spec, extraClassName)
      || '<span class="chat-message-avatar" aria-hidden="true"><span>—</span></span>';
  }

  function resolveDialogMessageAvatarSpec(message, context) {
    return dialogsAvatarRuntime?.resolveDialogMessageAvatarSpec(message, context) || {
      src: '',
      initial: '—',
      label: 'Клиент',
    };
  }

  function resolveWorkspaceMessageAvatarSpec(message) {
    return dialogsAvatarRuntime?.resolveWorkspaceMessageAvatarSpec(message) || {
      src: '',
      initial: '—',
      label: 'Клиент',
    };
  }

  function getDialogUserId(source) {
    return dialogsShellRuntime?.getDialogUserId(source) || '';
  }

  function bindAvatar(container, userId, name) {
    dialogsShellRuntime?.bindAvatar(container, userId, name);
  }

  function hydrateAvatars(root) {
    dialogsShellRuntime?.hydrateAvatars(root);
  }

  function formatRatingStars(value) {
    return dialogsPresentationRuntime?.formatRatingStars(value) || '';
  }

  function formatDialogMeta(ticketId, requestNumber) {
    return dialogsPresentationRuntime?.formatDialogMeta(ticketId, requestNumber) || '';
  }

  function formatDurationMinutes(totalMinutes) {
    return dialogsSlaRuntime?.formatDurationMinutes(totalMinutes) || '0м';
  }

  function computeSlaState(row) {
    return dialogsSlaRuntime?.computeSlaState(row)
      || { label: 'Нет даты', className: 'dialog-sla-closed', title: 'Не удалось определить время создания обращения' };
  }

  function updateRowSlaBadge(row) {
    dialogsSlaRuntime?.updateRowSlaBadge(row);
  }

  function updateAllSlaBadges() {
    dialogsSlaRuntime?.updateAllSlaBadges();
  }

  function renderDialogRow(item) {
    const ticketId = item?.ticketId || '—';
    const requestNumber = item?.requestNumber;
    const channelId = item?.channelId;
    const openHref = channelId
      ? `/dialogs/${encodeURIComponent(ticketId)}?channelId=${encodeURIComponent(channelId)}`
      : `/dialogs/${encodeURIComponent(ticketId)}`;
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
    const responsibleAvatarUrl = item?.responsibleAvatarUrl || '';
    const responsibleRaw = resolveResponsibleRawFromItem(item) || (responsible === '—' ? '' : String(responsible).trim());
    const canTakeOwnership = !isOwnedByCurrentOperator(responsibleRaw);
    const unreadCount = Number(item?.unreadCount) || 0;
    const createdDate = item?.createdDateSafe || item?.createdDate || 'Дата не указана';
    const createdTime = item?.createdTimeSafe || item?.createdTime || '—';
    const ratingValue = Number(item?.rating);
    const ratingStars = formatRatingStars(ratingValue);
    const userId = getDialogUserId(item);

    return `
      <tr data-ticket-id="${escapeHtml(ticketId)}"
          data-request-number="${escapeHtml(requestNumber || '')}"
          data-client="${escapeHtml(clientName)}"
          data-client-status="${escapeHtml(clientStatus)}"
          data-channel-id="${escapeHtml(item?.channelId || '')}"
          data-user-id="${escapeHtml(userId)}"
          data-channel="${escapeHtml(channelLabel)}"
          data-business="${escapeHtml(businessLabel)}"
          data-problem="${escapeHtml(problemLabel)}"
          data-status="${escapeHtml(statusLabel)}"
          data-status-raw="${escapeHtml(statusRaw)}"
          data-status-key="${escapeHtml(statusKey)}"
          data-location="${escapeHtml(locationLabel)}"
          data-categories="${escapeHtml(categories)}"
          data-responsible="${escapeHtml(responsible === '—' ? '' : responsible)}"
          data-responsible-raw="${escapeHtml(responsibleRaw)}"
          data-responsible-avatar-url="${escapeHtml(responsibleAvatarUrl)}"
          data-created-at="${escapeHtml(item?.createdAt || '')}"
          data-unread="${unreadCount}"
          data-rating="${Number.isFinite(ratingValue) ? ratingValue : ''}"
          data-last-message-sender="${escapeHtml(item?.lastMessageSender || '')}"
          data-last-message-timestamp="${escapeHtml(item?.lastMessageTimestamp || '')}">
        <td class="dialog-select-column" data-column-key="select">
          <input class="form-check-input dialog-row-select" type="checkbox" data-ticket-id="${escapeHtml(ticketId)}" aria-label="Выбрать диалог">
        </td>
        <td data-column-key="ticket">${escapeHtml(displayNumber)}</td>
        <td data-column-key="client">
          <div class="client-cell">
            <div class="dialog-avatar" data-avatar-user-id="${escapeHtml(userId)}"
                 data-avatar-name="${escapeHtml(clientName)}">
              <img class="dialog-avatar-img d-none" alt="Аватар клиента" data-avatar-img>
              <span class="avatar-circle" data-avatar-initial>${escapeHtml(avatarInitial(clientName))}</span>
            </div>
            <div class="dialog-client-meta">
              <div class="fw-semibold">${escapeHtml(clientName)}</div>
              <div class="small text-muted">${escapeHtml(clientStatus)}</div>
            </div>
          </div>
        </td>
        <td data-column-key="status">
          <div class="d-flex align-items-center gap-2">
            <span class="badge rounded-pill ${statusClassByKey(statusKey)}">${escapeHtml(statusLabel)}</span>
            <span class="badge text-bg-danger dialog-unread-count ${unreadCount > 0 ? '' : 'd-none'}">${unreadCount}</span>
          </div>
          ${ratingStars ? `<div class="small text-warning">${escapeHtml(ratingStars)}</div>` : ''}
        </td>
        <td data-column-key="channel">${renderChannelBadge(channelLabel)}</td>
        <td class="dialog-business-cell" data-column-key="business" data-business="${escapeHtml(businessLabel)}">
          <div class="dialog-business-pill">
            <span class="dialog-business-icon d-none" aria-hidden="true"></span>
            <span class="dialog-business-text">${escapeHtml(businessLabel)}</span>
          </div>
        </td>
        <td data-column-key="problem">
          <span class="text-truncate dialog-problem-cell d-block">${escapeHtml(problemLabel)}</span>
        </td>
        <td data-column-key="location">${escapeHtml(locationLabel)}</td>
        <td data-column-key="categories">${escapeHtml(categories)}</td>
        <td data-column-key="responsible">${renderResponsibleCell(responsible, responsibleAvatarUrl)}</td>
        <td data-column-key="created">
          <div class="small text-muted">${escapeHtml(createdDate)}</div>
          <div class="small">${escapeHtml(createdTime)}</div>
        </td>
        <td class="dialog-sla-cell" data-column-key="sla">
          <span class="badge rounded-pill dialog-sla-badge">—</span>
        </td>
        <td class="dialog-actions" data-column-key="actions">
          <div class="dialog-actions-inline">
            <a href="${escapeHtml(openHref)}" class="btn btn-sm btn-outline-primary dialog-open-btn" data-ticket-id="${escapeHtml(ticketId)}">Открыть</a>
            <div class="dialog-actions-dropdown" data-dialog-actions>
              <button type="button" class="btn btn-sm btn-outline-secondary dialog-actions-toggle" data-dialog-actions-toggle aria-expanded="false" aria-label="Действия" title="Действия">⋯</button>
              <div class="dialog-actions-menu" data-dialog-actions-menu>
              <button type="button" class="btn btn-sm btn-outline-success dialog-take-btn ${isResolvedStatusKey(statusKey) || !canTakeOwnership || !canRunAction('can_assign') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Взять себе</button>
              <button type="button" class="btn btn-sm btn-outline-warning dialog-snooze-btn ${isResolvedStatusKey(statusKey) || !canRunAction('can_snooze') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES)}</button>
              <button type="button" class="btn btn-sm btn-outline-danger dialog-close-btn ${isResolvedStatusKey(statusKey) || !canRunAction('can_close') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Закрыть</button>
              <a href="/tasks" class="btn btn-sm btn-outline-secondary dialog-task-btn"
                 data-ticket-id="${escapeHtml(ticketId)}"
                 data-client="${escapeHtml(clientName)}">Задача</a>
              </div>
            </div>
          </div>
        </td>
      </tr>
    `;
  }

  function refreshSummaryCounters(summary) {
    if (summaryTotal) summaryTotal.textContent = String(summary?.totalTickets ?? 0);
    if (summaryPending) summaryPending.textContent = String(summary?.pendingTickets ?? 0);
    if (summaryResolved) summaryResolved.textContent = String(summary?.resolvedTickets ?? 0);
  }

  function updateRowResponsible(row, responsible, options = {}) {
    dialogsActionsRuntime?.updateRowResponsible(row, responsible, options);
  }

  function updateDetailsTakeButton(responsible) {
    dialogsDetailsRuntime?.updateDetailsTakeButton(responsible);
  }

  function canTakeDialogOwnership(responsible, isClosed) {
    return canRunAction('can_assign') && !isClosed && !isOwnedByCurrentOperator(responsible);
  }

  function updateDetailsResponsible(responsible, options = {}) {
    dialogsDetailsRuntime?.updateDetailsResponsible(responsible, options);
  }

  function setDialogParticipantsState(participants) {
    return dialogsParticipantsRuntime?.setParticipantsState(participants) || [];
  }

  function renderDialogParticipantsState() {
    dialogsParticipantsRuntime?.renderDialogParticipantsState();
  }

  function renderDialogParticipantsLoadingState(message) {
    dialogsParticipantsRuntime?.renderDialogParticipantsLoadingState(message);
  }

  function syncParticipantsSelectOptions() {
    dialogsParticipantsRuntime?.syncParticipantsSelectOptions();
  }

  function syncReassignSelectOptions() {
    dialogsParticipantsRuntime?.syncReassignSelectOptions();
  }

  async function ensureAssignableOperatorsLoaded(force = false) {
    return dialogsParticipantsRuntime?.ensureAssignableOperatorsLoaded(force) || [];
  }

  async function loadDialogParticipants() {
    return dialogsParticipantsRuntime?.loadDialogParticipants() || [];
  }

  function renderReassignCurrentResponsible() {
    dialogsParticipantsRuntime?.renderReassignCurrentResponsible();
  }

  async function openParticipantsManager() {
    await dialogsParticipantsRuntime?.openParticipantsManager();
  }

  async function openReassignDialog() {
    await dialogsParticipantsRuntime?.openReassignDialog();
  }

  function syncDialogAssignControls() {
    dialogsParticipantsRuntime?.syncDialogAssignControls();
  }

  function switchWorkspaceTab(tabName) {
    const target = String(tabName || 'client').trim().toLowerCase() || 'client';
    workspaceTabButtons.forEach((button) => {
      const active = String(button?.dataset?.workspaceTab || '').trim().toLowerCase() === target;
      button.classList.toggle('is-active', active);
      button.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    workspaceTabPanels.forEach((panel) => {
      const visible = String(panel?.dataset?.workspaceTabPanel || '').trim().toLowerCase() === target;
      panel.classList.toggle('d-none', !visible);
    });
  }

  function exportWorkspaceIncidentCsv() {
    if (!activeWorkspaceTicketId) {
      if (typeof showNotification === 'function') {
        showNotification('������� �������� ������, ����� �������������� ��������.', 'warning');
      }
      return;
    }
    const link = document.createElement('a');
    link.href = `/api/dialogs/ai-monitoring/events?ticketId=${encodeURIComponent(activeWorkspaceTicketId)}&days=30&limit=500&format=csv`;
    link.download = `incident-${activeWorkspaceTicketId}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function updateRowQuickActions(row) {
    dialogsActionsRuntime?.updateRowQuickActions(row);
  }

  function setDialogActionsMenuOpen(menu, isOpen) {
    dialogsActionsRuntime?.setDialogActionsMenuOpen(menu, isOpen);
  }

  function closeDialogActionsMenus(exceptMenu = null) {
    dialogsActionsRuntime?.closeDialogActionsMenus(exceptMenu);
  }

  async function closeDialogQuick(ticketId, row, triggerButton) {
    return dialogsActionsRuntime?.closeDialogQuick(ticketId, row, triggerButton);
  }

  async function takeDialog(ticketId, row, triggerButton) {
    return dialogsActionsRuntime?.takeDialog(ticketId, row, triggerButton);
  }

  async function snoozeDialog(ticketId, minutes, triggerButton) {
    return dialogsActionsRuntime?.snoozeDialog(ticketId, minutes, triggerButton);
  }

  function syncDialogsTable(dialogs) {
    return dialogsListRuntime?.syncDialogsTable(dialogs);
  }

  async function refreshDialogsList() {
    return dialogsListRuntime?.refreshDialogsList();
  }

  function startDialogsPolling() {
    return dialogsListRuntime?.startDialogsPolling();
  }

  function stopDialogsPolling() {
    return dialogsListRuntime?.stopDialogsPolling();
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
    view: DIALOG_DEFAULT_VIEW,
    pageSize: DEFAULT_PAGE_SIZE,
    page: 1,
    slaWindowMinutes: DIALOG_DEFAULT_SLA_WINDOW,
    sortMode: 'default',
  };
  let lastManualSortMode = filterState.sortMode;
  let lastFilteredRows = [];
  const slaOrchestrationByTicket = new Map();

  const dialogsListRuntime = window.DialogsListRuntime?.createRuntime({
    table,
    emptyRow,
    filterState,
    selectedTicketIds,
    slaOrchestrationByTicket,
    listPollInterval: LIST_POLL_INTERVAL,
    defaultPageSize: DEFAULT_PAGE_SIZE,
    dialogSlaWindowPresets: DIALOG_SLA_WINDOW_PRESETS,
    quickSnoozeMinutes: QUICK_SNOOZE_MINUTES,
    overdueThresholdHours: OVERDUE_THRESHOLD_HOURS,
    slaCriticalMinutes: SLA_CRITICAL_MINUTES,
    slaWarningMinutes: SLA_WARNING_MINUTES,
    slaTargetMinutes: SLA_TARGET_MINUTES,
    slaCriticalPinUnassignedOnly: SLA_CRITICAL_PIN_UNASSIGNED_ONLY,
    slaCriticalViewUnassignedOnly: SLA_CRITICAL_VIEW_UNASSIGNED_ONLY,
    autoSlaPriorityForCriticalView: AUTO_SLA_PRIORITY_FOR_CRITICAL_VIEW,
    elements: {
      bulkToolbar,
      bulkCount,
      bulkTakeBtn,
      bulkSnoozeBtn,
      bulkCloseBtn,
      bulkClearBtn,
      selectAllCheckbox,
      statusFilter,
      pageSizeSelect,
      slaWindowSelect,
      viewTabs,
      sortModeSelect,
      pagePrevBtn,
      pageNextBtn,
      pageNumbers,
      pageState,
    },
    getLastFilteredRows: () => lastFilteredRows,
    setLastFilteredRows: (rows) => {
      lastFilteredRows = Array.isArray(rows) ? rows : [];
    },
    getActiveDialogState: () => ({
      row: activeDialogRow,
      ticketId: activeDialogTicketId,
    }),
    setActiveDialogState: (row) => {
      activeDialogRow = row && row.tagName === 'TR' ? row : null;
    },
    getListPollTimer: () => listPollTimer,
    setListPollTimer: (timerId) => {
      listPollTimer = timerId || null;
    },
    getListLoading: () => listLoading,
    setListLoading: (nextValue) => {
      listLoading = nextValue === true;
    },
    getLastListMarker: () => lastListMarker,
    setLastListMarker: (marker) => {
      lastListMarker = marker;
    },
    getLastManualSortMode: () => lastManualSortMode,
    setLastManualSortMode: (value) => {
      lastManualSortMode = value || 'default';
    },
    rowsList,
    buildDialogItemMarker,
    createDialogRowElement,
    applyColumnState,
    applyBusinessCellStyles,
    restoreColumnWidths,
    updateAllSlaBadges,
    escapeSelectorValue,
    buildDialogsMarker,
    normalizeMyDialogsState,
    syncMyDialogsStateFromTable,
    renderMyDialogsPanel,
    refreshSummaryCounters,
    requestSidebarNotificationRefresh: (source = 'dialogs') => dialogsNotificationsRuntime?.requestSidebarNotificationRefresh(source),
    parseUtcDateValue,
    getSnoozeUntil,
    canRunAction,
    notifyPermissionDenied,
    emitWorkspaceTelemetry,
    showNotification,
    formatSnoozeActionLabel,
    formatBulkSnoozeLabel,
    takeDialog,
    snoozeDialog,
    setSnooze,
    updateRowQuickActions,
    closeDialogQuick,
    clearSnooze,
    collectRowSearchText,
    storage: {
      pageSize: STORAGE_PAGE_SIZE,
      view: STORAGE_VIEW,
      slaWindow: STORAGE_SLA_WINDOW,
      sortMode: STORAGE_SORT_MODE,
    },
    openDialogEntry,
  }) || null;

  function syncSlaOrchestrationSignals(slaOrchestration) {
    return dialogsListRuntime?.syncSlaOrchestrationSignals(slaOrchestration);
  }

  function applySlaOrchestrationToRows() {
    return dialogsListRuntime?.applySlaOrchestrationToRows();
  }

  function resolveSlaPriority(row) {
    return dialogsListRuntime?.resolveSlaPriority(row) || { bucket: 3, minutesLeft: Number.POSITIVE_INFINITY, state: 'closed' };
  }

  function applySlaPriorityClass(row) {
    return dialogsListRuntime?.applySlaPriorityClass(row);
  }

  function compareRowsBySlaPriority(left, right) {
    return dialogsListRuntime?.compareRowsBySlaPriority(left, right) || 0;
  }

  function isNewDialog(row) {
    return dialogsListRuntime?.isNewDialog(row) === true;
  }

  function isUnassignedDialog(row) {
    return dialogsListRuntime?.isUnassignedDialog(row) === true;
  }

  function isOverdueDialog(row) {
    return dialogsListRuntime?.isOverdueDialog(row) === true;
  }

  function isSnoozedDialog(row) {
    return dialogsListRuntime?.isSnoozedDialog(row) === true;
  }

  function isCriticalSlaDialog(row) {
    return dialogsListRuntime?.isCriticalSlaDialog(row) === true;
  }

  function isEscalationRequiredDialog(row) {
    return dialogsListRuntime?.isEscalationRequiredDialog(row) === true;
  }

  function resolveSlaMinutesLeft(row) {
    return dialogsListRuntime?.resolveSlaMinutesLeft(row) ?? null;
  }

  function matchesSlaReactionWindow(row) {
    return dialogsListRuntime?.matchesSlaReactionWindow(row) !== false;
  }

  function matchesCurrentView(row) {
    return dialogsListRuntime?.matchesCurrentView(row) !== false;
  }

  function isResolved(row) {
    return dialogsListRuntime?.isResolved(row) === true;
  }

  function syncRowSelectionState(row) {
    return dialogsListRuntime?.syncRowSelectionState(row);
  }

  function selectedRows() {
    return dialogsListRuntime?.selectedRows() || [];
  }

  function updateSelectAllState() {
    return dialogsListRuntime?.updateSelectAllState();
  }

  function updateBulkActionsState() {
    return dialogsListRuntime?.updateBulkActionsState();
  }

  function updateBulkToolbarState() {
    return updateBulkActionsState();
  }

  function clearSelection() {
    return dialogsListRuntime?.clearSelection();
  }

  async function runBulkAction(action) {
    return dialogsListRuntime?.runBulkAction(action);
  }

  function applyFilters(options = {}) {
    return dialogsListRuntime?.applyFilters(options);
  }

  function renderTableFromRows(resetPage = false) {
    return applyFilters(resetPage === true ? { resetPage: true } : {});
  }

  function applyQuickSearch(value) {
    return dialogsListRuntime?.applyQuickSearch(value);
  }

  function applyStatusFilter(statusLabel = '') {
    return dialogsListRuntime?.applyStatusFilter(statusLabel);
  }

  function setViewTab(nextView) {
    return dialogsListRuntime?.setViewTab(nextView);
  }

  function visibleRows() {
    return dialogsListRuntime?.visibleRows() || [];
  }

  function ensureDialogRowPage(row) {
    return dialogsListRuntime?.ensureDialogRowPage(row);
  }

  function setActiveDialogRow(row, options = {}) {
    return dialogsListRuntime?.setActiveDialogRow(row, options);
  }

  function getShortcutTargetRow() {
    return dialogsListRuntime?.getShortcutTargetRow() || null;
  }

  function openVisibleDialogByOffset(offset) {
    return dialogsListRuntime?.openVisibleDialogByOffset(offset);
  }

  function buildWorkspaceDialogUrl(ticketId, channelId) {
    if (!ticketId) return '/dialogs';
    const basePath = `/dialogs/${encodeURIComponent(ticketId)}`;
    return withChannelParam(basePath, channelId);
  }

  function isWorkspaceDialogPath(pathname) {
    return /^\/dialogs\/[^/]+/.test(String(pathname || ''));
  }

  async function emitWorkspaceTelemetry(eventType, payload = {}) {
    await dialogsWorkspaceRuntime?.emitWorkspaceTelemetry(eventType, payload);
  }

  function renderExperimentInfoPanel() {
    dialogsExperimentRuntime?.renderExperimentInfoPanel();
  }

  function clearExperimentTelemetryRefreshTimer() {
    dialogsExperimentRuntime?.clearExperimentTelemetryRefreshTimer();
  }

  function syncExperimentTelemetryAutoRefresh() {
    dialogsExperimentRuntime?.syncExperimentTelemetryAutoRefresh();
  }

  async function loadExperimentTelemetrySummary() {
    await dialogsExperimentRuntime?.loadExperimentTelemetrySummary();
  }

  function formatWorkspaceDateTime(value) {
    return formatTimestamp(value, { includeTime: true, fallback: '—' });
  }

  function hasUnsavedWorkspaceComposerChanges(nextTicketId) {
    return dialogsWorkspaceRuntime?.hasUnsavedWorkspaceComposerChanges(nextTicketId) === true;
  }

  function confirmWorkspaceTicketSwitch(nextTicketId) {
    return dialogsWorkspaceRuntime?.confirmWorkspaceTicketSwitch(nextTicketId) !== false;
  }

  function updateWorkspaceDraftState(text) {
    dialogsWorkspaceRuntime?.updateWorkspaceDraftState(text);
  }

  function saveWorkspaceDraft(ticketId, message, options = {}) {
    dialogsWorkspaceRuntime?.saveWorkspaceDraft(ticketId, message, options);
  }

  function restoreWorkspaceDraft(ticketId) {
    dialogsWorkspaceRuntime?.restoreWorkspaceDraft(ticketId);
  }

  function scheduleWorkspaceDraftAutosave() {
    dialogsWorkspaceRuntime?.scheduleWorkspaceDraftAutosave();
  }

  function appendWorkspaceMessage(message) {
    dialogsWorkspaceRuntime?.appendWorkspaceMessage(message);
  }

  function buildMacroGovernanceMeta(template) {
    return dialogsMacroRuntime?.buildMacroGovernanceMeta(template) || '';
  }

  function renderWorkspaceMacroPreview(template) {
    dialogsMacroRuntime?.renderWorkspaceMacroPreview(template);
  }

  function renderWorkspaceMacroOptions(templates) {
    dialogsMacroRuntime?.renderWorkspaceMacroOptions(templates);
  }

  function findMacroTemplateByWorkspaceOptionValue(value, templatePool = null) {
    return dialogsMacroRuntime?.findMacroTemplateByWorkspaceOptionValue(value, templatePool) || null;
  }

  async function applyWorkspaceMacroTemplate() {
    await dialogsMacroRuntime?.applyWorkspaceMacroTemplate();
  }

  async function sendWorkspaceReply() {
    await dialogsWorkspaceRuntime?.sendWorkspaceReply();
  }

  function updateWorkspaceMessagesLoadMoreState() {
    dialogsWorkspaceRuntime?.updateWorkspaceMessagesLoadMoreState();
  }

  function syncWorkspaceMessagesPagination(messages) {
    dialogsWorkspaceRuntime?.syncWorkspaceMessagesPagination(messages);
  }


  function resolveWorkspaceSlaBadgeClass(state) {
    return dialogsWorkspaceRuntime?.resolveWorkspaceSlaBadgeClass(state) || 'text-bg-success';
  }

  function formatWorkspaceSlaRemaining(minutesLeft) {
    return dialogsWorkspaceRuntime?.formatWorkspaceSlaRemaining(minutesLeft) || '—';
  }

  function mergeWorkspacePayload(basePayload, partialPayload, include) {
    return dialogsWorkspaceRuntime?.mergeWorkspacePayload(basePayload, partialPayload, include) || partialPayload || basePayload;
  }

  function setWorkspaceSectionLoading(stateEl, errorEl, message) {
    dialogsWorkspaceRuntime?.setWorkspaceSectionLoading(stateEl, errorEl, message);
  }

  async function reloadWorkspaceSection(include, options = {}) {
    await dialogsWorkspaceRuntime?.reloadWorkspaceSection(include, options);
  }

  async function loadMoreWorkspaceMessages() {
    await dialogsWorkspaceRuntime?.loadMoreWorkspaceMessages();
  }

  function renderWorkspaceCategories() {
    dialogsWorkspaceRuntime?.renderWorkspaceCategories();
  }

  function updateWorkspaceActionButtons(conversation, permissions, payload = null) {
    dialogsActionsRuntime?.updateWorkspaceActionButtons(conversation, permissions, payload);
  }

  function emitWorkspaceProfileGapTelemetry(context, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceProfileGapTelemetry(context, conversation);
  }

  function emitWorkspaceContextSourceGapTelemetry(context, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceContextSourceGapTelemetry(context, conversation);
  }

  function emitWorkspaceContextAttributePolicyGapTelemetry(context, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceContextAttributePolicyGapTelemetry(context, conversation);
  }

  function emitWorkspaceContextBlockGapTelemetry(context, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceContextBlockGapTelemetry(context, conversation);
  }

  function emitWorkspaceContextContractGapTelemetry(context, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceContextContractGapTelemetry(context, conversation);
  }

  function emitWorkspaceSlaPolicyGapTelemetry(sla, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceSlaPolicyGapTelemetry(sla, conversation);
  }

  function emitWorkspaceParityGapTelemetry(parity, conversation) {
    dialogsWorkspaceRuntime?.emitWorkspaceParityGapTelemetry(parity, conversation);
  }

  function renderWorkspaceNavigation(navigation) {
    dialogsWorkspaceRuntime?.renderWorkspaceNavigation(navigation);
  }

  async function navigateWorkspaceInline(direction) {
    await dialogsWorkspaceRuntime?.navigateWorkspaceInline(direction);
  }

  async function refreshActiveWorkspaceContract(options = {}) {
    await dialogsWorkspaceRuntime?.refreshActiveWorkspaceContract(options);
  }

  function appendToWorkspaceComposer(text) {
    dialogsWorkspaceRuntime?.appendToWorkspaceComposer(text);
  }

  const dialogsAiRuntime = window.DialogsAiRuntime?.createRuntime({
    elements: {
      aiReviewQueueSection,
      aiReviewQueueState,
      aiReviewQueueBody,
      aiMonitoringSection,
      aiMonitoringState,
      aiMonitoringAlerts,
      aiMonitoringRunbook,
      aiMonitoringEvents,
      aiKpiAutoReplyRate,
      aiKpiAssistRate,
      aiKpiEscalationRate,
      aiKpiCorrectionRate,
      detailsAiState,
      detailsAiList,
      workspaceAiSuggestionsSection,
      workspaceAiSuggestionsState,
      workspaceAiSuggestionsList,
      workspaceAiControlState,
      workspaceAiDisableForDialog,
      workspaceAiHandoffNoAutoReply,
      workspaceAiReviewBox,
      workspaceAiReviewQuestion,
      workspaceAiReviewProblemSelect,
      workspaceAiReviewSolutionSelect,
      workspaceAiReviewCurrent,
      workspaceAiReviewPending,
    },
    escapeHtml,
    formatUtcDate,
    getAiMonitoringFilters: () => aiMonitoringFilters,
    getWorkspaceAiReviewState: () => ({
      problemCandidates: workspaceAiReviewProblemCandidates,
      solutionCandidates: workspaceAiReviewSolutionCandidates,
    }),
    setWorkspaceAiReviewState: (problemCandidates, solutionCandidates) => {
      workspaceAiReviewProblemCandidates = Array.isArray(problemCandidates) ? problemCandidates : [];
      workspaceAiReviewSolutionCandidates = Array.isArray(solutionCandidates) ? solutionCandidates : [];
    },
    showNotification,
  }) || null;

  async function loadWorkspaceAiSuggestions(ticketId) {
    return dialogsAiRuntime?.loadWorkspaceAiSuggestions(ticketId);
  }

  async function loadDetailsAiSuggestions(ticketId) {
    return dialogsAiRuntime?.loadDetailsAiSuggestions(ticketId);
  }

  async function sendAiSuggestionFeedback(ticketId, payload) {
    return dialogsAiRuntime?.sendAiSuggestionFeedback(ticketId, payload);
  }

  async function loadWorkspaceAiControl(ticketId) {
    return dialogsAiRuntime?.loadWorkspaceAiControl(ticketId);
  }

  async function updateWorkspaceAiControl(ticketId, controlPayload, successMessage) {
    return dialogsAiRuntime?.updateWorkspaceAiControl(ticketId, controlPayload, successMessage);
  }

  function renderAiReviewQueueRows(items) {
    return dialogsAiRuntime?.renderAiReviewQueueRows(items);
  }

  async function loadAiReviewQueue() {
    return dialogsAiRuntime?.loadAiReviewQueue();
  }

  function formatRatePercent(value) {
    return dialogsAiRuntime?.formatRatePercent(value) || '—';
  }

  function renderAiMonitoringAlerts(alerts) {
    return dialogsAiRuntime?.renderAiMonitoringAlerts(alerts);
  }

  function renderAiMonitoringRunbook(items) {
    return dialogsAiRuntime?.renderAiMonitoringRunbook(items);
  }

  function renderAiMonitoringEvents(items) {
    return dialogsAiRuntime?.renderAiMonitoringEvents(items);
  }

  function buildAiMonitoringEventsQuery(days = 7, limit = 50) {
    return dialogsAiRuntime?.buildAiMonitoringEventsQuery(days, limit) || '';
  }

  async function loadAiMonitoringEvents(days = 7, limit = 50) {
    return dialogsAiRuntime?.loadAiMonitoringEvents(days, limit);
  }

  function exportAiMonitoringEventsCsv(days = 7, limit = 200) {
    return dialogsAiRuntime?.exportAiMonitoringEventsCsv(days, limit);
  }

  async function loadAiMonitoringSummary(days = 7) {
    return dialogsAiRuntime?.loadAiMonitoringSummary(days);
  }

  function formatAiReviewMessageOption(item) {
    return dialogsAiRuntime?.formatAiReviewMessageOption(item) || '��������� ��� ������';
  }

  function renderAiReviewMessageSelect(selectEl, candidates, selectedId, emptyLabel) {
    return dialogsAiRuntime?.renderAiReviewMessageSelect(selectEl, candidates, selectedId, emptyLabel);
  }

  function resolveAiReviewSelectedMessageText(selectEl, candidates) {
    return dialogsAiRuntime?.resolveAiReviewSelectedMessageText(selectEl, candidates) || '';
  }

  async function loadWorkspaceAiReview(ticketId) {
    return dialogsAiRuntime?.loadWorkspaceAiReview(ticketId);
  }

  function renderWorkspaceShell(payload) {
    dialogsWorkspaceRuntime?.renderWorkspaceShell(payload);
  }

  function normalizeWorkspaceContextViolationDetails(details) {
    return dialogsWorkspaceRuntime?.normalizeWorkspaceContextViolationDetails(details) || [];
  }

  function workspaceContextViolationTypeLabel(type) {
    return dialogsWorkspaceRuntime?.workspaceContextViolationTypeLabel(type) || 'Context gap';
  }

  function workspaceContextViolationSeverityLabel(severity) {
    return dialogsWorkspaceRuntime?.workspaceContextViolationSeverityLabel(severity) || 'К сведению';
  }

  function workspaceContextViolationSeverityBadge(severity) {
    return dialogsWorkspaceRuntime?.workspaceContextViolationSeverityBadge(severity) || 'text-bg-secondary';
  }

  function renderWorkspaceClientProfile(client, context = {}) {
    return dialogsWorkspaceRuntime?.renderWorkspaceClientProfile(client, context)
      || '<div class="small text-muted">Профиль клиента недоступен.</div>';
  }

  function isWorkspaceClientExtraValue(value) {
    return dialogsWorkspaceRuntime?.isWorkspaceClientExtraValue(value) === true;
  }

  function formatWorkspaceClientExtraValue(value) {
    return dialogsWorkspaceRuntime?.formatWorkspaceClientExtraValue(value) || String(value);
  }

  function prettifyWorkspaceClientExtraKey(key) {
    return dialogsWorkspaceRuntime?.prettifyWorkspaceClientExtraKey(key) || 'Атрибут';
  }

  function resolveWorkspaceClientAttributeLabelMap(rawLabels) {
    return dialogsWorkspaceRuntime?.resolveWorkspaceClientAttributeLabelMap(rawLabels) || new Map();
  }

  function resolveWorkspaceClientAttributeOrder(rawOrder) {
    return dialogsWorkspaceRuntime?.resolveWorkspaceClientAttributeOrder(rawOrder) || [];
  }

  function orderWorkspaceClientExtraEntries(entries, order) {
    return dialogsWorkspaceRuntime?.orderWorkspaceClientExtraEntries(entries, order)
      || (Array.isArray(entries) ? entries : []);
  }

  function normalizeWorkspaceClientAttributeKey(key) {
    return dialogsWorkspaceRuntime?.normalizeWorkspaceClientAttributeKey(key) || '';
  }

  async function reloadWorkspaceForInitialRoute(statusText = 'Повторная загрузка ленты…') {
    await dialogsWorkspaceRuntime?.reloadWorkspaceForInitialRoute(statusText);
  }

  function isWorkspaceTemporarilyDisabled() {
    return dialogsWorkspaceRuntime?.isWorkspaceTemporarilyDisabled() === true;
  }

  function registerWorkspaceFailure() {
    return dialogsWorkspaceRuntime?.registerWorkspaceFailure() === true;
  }

  function clearWorkspaceFailureStreak() {
    dialogsWorkspaceRuntime?.clearWorkspaceFailureStreak();
  }

  async function openDialogWithWorkspaceFallback(ticketId, row, options = {}) {
    await dialogsWorkspaceRuntime?.openDialogWithWorkspaceFallback(ticketId, row, options);
  }

  function openDialogEntry(ticketId, row) {
    dialogsFlowRuntime?.openDialogEntry(ticketId, row);
  }

  function openDialogSurface(ticketId, row, options = {}) {
    exitListOnlyMode(options?.reason || options?.source || 'dialog_open');
    return dialogsShellRuntime?.openDialogSurface(ticketId, row, options) || Promise.resolve();
  }

  function handleGlobalShortcuts(event) {
    dialogsFlowRuntime?.handleGlobalShortcuts(event);
  }

  function handleMediaPreviewEscape(event) {
    dialogsDetailsHistoryRuntime?.handleMediaPreviewEscape(event);
  }

  function restoreColumnWidths() {
    dialogsShellRuntime?.restoreColumnWidths();
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

  dialogsDetailsRuntime = window.DialogsDetailsRuntime?.createRuntime({
    elements: {
      detailsModalEl,
      detailsModal,
      detailsSummary,
      detailsMetrics,
      detailsMeta,
      detailsRating,
      detailsHistory,
      detailsAiState,
      detailsAiList,
      detailsReplyText,
      detailsOpenClientCard,
      detailsSpam,
      detailsAvatar,
      detailsClientName,
      detailsClientStatus,
      detailsProblem,
      detailsTakeBtn,
    },
    escapeHtml,
    debugLog,
    showModalSafe,
    withChannelParam,
    formatDialogMeta,
    formatRatingStars,
    formatTimestamp,
    formatCategoriesLabel,
    getDialogUserId,
    bindAvatar,
    updateDetailsLocationLabel,
    renderHistory,
    resetPreviousDialogHistoryState,
    setDialogParticipantsState,
    renderDialogParticipantsLoadingState,
    syncParticipantsSelectOptions,
    syncReassignSelectOptions,
    updateSummaryCategories,
    syncCategorySelections,
    loadDialogParticipants,
    loadDetailsAiSuggestions,
    updateResolveButton,
    updateRowStatus,
    updateDialogUnreadCount: (unreadCount) => dialogsNotificationsRuntime?.updateDialogUnreadCount(unreadCount),
    startHistoryPolling,
    renderSummaryBadge,
    resolveSummaryBadgeStyle,
    renderCategoryBadges,
    businessStyleMap: BUSINESS_STYLE_MAP,
    summaryBadgeStyles: SUMMARY_BADGE_STYLES,
    canTakeDialogOwnership,
    isResolvedRow: isResolved,
    isOwnedByCurrentOperator,
    operatorAvatarUrl: OPERATOR_AVATAR_URL,
    getActiveDialogState: () => ({
      ticketId: activeDialogTicketId,
      channelId: activeDialogChannelId,
      responsibleRaw: activeDialogResponsibleRaw,
      responsibleAvatarUrl: activeDialogResponsibleAvatarUrl,
      context: activeDialogContext,
      row: activeDialogRow,
    }),
    setActiveDialogTicketId: (ticketId) => {
      activeDialogTicketId = ticketId || null;
    },
    setActiveDialogChannelId: (channelId) => {
      activeDialogChannelId = channelId || null;
    },
    setActiveDialogResponsibleState: (responsibleRaw, avatarUrl) => {
      activeDialogResponsibleRaw = String(responsibleRaw || '').trim();
      activeDialogResponsibleAvatarUrl = String(avatarUrl || '').trim();
    },
    setActiveDialogContext: (context) => {
      if (!context || typeof context !== 'object') return;
      activeDialogContext = context;
    },
    setActiveDialogRow,
    setSelectedCategories: (categories) => {
      selectedCategories = categories instanceof Set ? new Set(categories) : new Set();
    },
    initMacroVariableCatalog,
  }) || null;

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

  function resolveListPagination(totalItems) {
    return dialogsListRuntime?.resolveListPagination(totalItems) || {
      limit: Infinity,
      totalItems: Number(totalItems) || 0,
      totalPages: 0,
      currentPage: 0,
      startIndex: 0,
      endIndex: 0,
      visibleCount: 0,
    };
  }

  function updatePaginationControls(pagination) {
    return dialogsListRuntime?.updatePaginationControls(pagination);
  }

  function renderPaginationPageNumbers(totalPages, currentPage, limit) {
    return dialogsListRuntime?.renderPaginationPageNumbers(totalPages, currentPage, limit);
  }

  function applyPageSize(matchedRows) {
    return dialogsListRuntime?.applyPageSize(matchedRows) || 0;
  }

  function updateZebraRows(matchedRows) {
    return dialogsListRuntime?.updateZebraRows(matchedRows);
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
    return dialogsMacroRuntime?.resolveMacroSearchText(template) || '';
  }

  function filterMacroTemplates(templates, query) {
    return dialogsMacroRuntime?.filterMacroTemplates(templates, query) || [];
  }

  function renderMacroTemplateOptions(templates) {
    dialogsMacroRuntime?.renderMacroTemplateOptions(templates);
  }

  function renderCategoryTemplate(template) {
    dialogsTemplatesRuntime?.renderCategoryTemplate(template);
  }

  function renderQuestionTemplate(template) {
    dialogsTemplatesRuntime?.renderQuestionTemplate(template);
  }

  function renderCompletionTemplate(template) {
    dialogsTemplatesRuntime?.renderCompletionTemplate(template);
  }


  function renderMacroVariableCatalog(variables) {
    dialogsMacroRuntime?.renderMacroVariableCatalog(variables);
  }

  function resolveMacroVariableDefaults(variables) {
    return dialogsMacroRuntime?.resolveMacroVariableDefaults(variables) || {};
  }

  async function initMacroVariableCatalog(ticketId, forceReload = false) {
    await dialogsMacroRuntime?.initMacroVariableCatalog(ticketId, forceReload);
  }

  function resolveMacroVariables() {
    return dialogsMacroRuntime?.resolveMacroVariables() || {};
  }

  function resolveMacroText(template) {
    return dialogsMacroRuntime?.resolveMacroText(template) || '';
  }

  function renderMacroTemplate(template) {
    dialogsMacroRuntime?.renderMacroTemplate(template);
  }

  function initDialogTemplates() {
    dialogsTemplatesRuntime?.initTemplatePanels();
    dialogsMacroRuntime?.initMacroTemplates();
  }

  function syncCategorySelections() {
    dialogsTemplatesRuntime?.syncCategorySelections();
  }

  function renderEmojiPanel() {
    dialogsTemplatesRuntime?.renderEmojiPanel();
  }

  function openCategoryPanel() {
    dialogsTemplatesRuntime?.openCategoryPanel();
  }

  function insertReplyText(value) {
    dialogsTemplatesRuntime?.insertReplyText(value);
  }

  function resolveMacroWorkflow(template) {
    return dialogsMacroRuntime?.resolveMacroWorkflow(template) || {
      assignToMe: false,
      closeTicket: false,
      snoozeMinutes: 0,
    };
  }

  async function executeMacroWorkflow(template, options = {}) {
    return await dialogsMacroRuntime?.executeMacroWorkflow(template, options) || [];
  }

  async function applyMacroTemplate() {
    await dialogsMacroRuntime?.applyMacroTemplate();
  }

  function initColumnResize() {
    dialogsShellRuntime?.initColumnResize();
  }

  function initDetailsResize() {
    dialogsShellRuntime?.initDetailsResize();
  }

  function normalizeMessageSenderByType(messageType, sender) {
    return dialogsPresentationRuntime?.normalizeMessageSenderByType(messageType, sender) || 'user';
  }

  function resolveSenderLabel(message, context) {
    return dialogsPresentationRuntime?.resolveSenderLabel(message, context)
      || context?.clientName
      || message?.sender
      || 'Клиент';
  }

  function parseUtcDateValue(value) {
    return dialogsPresentationRuntime?.parseUtcDateValue(value) || null;
  }

  function formatUtcDate(date, options = {}) {
    return dialogsPresentationRuntime?.formatUtcDate(date, options) || options.fallback || '—';
  }

  function formatTimestamp(value, options = {}) {
    return dialogsPresentationRuntime?.formatTimestamp(value, options)
      || options.fallback
      || String(value || '');
  }

  function buildMediaMarkup(message) {
    return dialogsDetailsHistoryRuntime?.buildMediaMarkup(message) || '';
  }

  function messageToHtml(message, options = {}) {
    return dialogsDetailsHistoryRuntime?.messageToHtml(message, options) || '';
  }

  function filterDialogHistoryMessages(messages, context = activeDialogContext) {
    return dialogsDetailsHistoryRuntime?.filterDialogHistoryMessages(messages, context) || [];
  }

  function resetPreviousDialogHistoryState() {
    dialogsDetailsHistoryRuntime?.resetPreviousDialogHistoryState();
  }

  function renderPreviousDialogHistoryControls() {
    return dialogsDetailsHistoryRuntime?.renderPreviousDialogHistoryControls() || '';
  }

  function renderArchivedHistoryBatch(batch) {
    return dialogsDetailsHistoryRuntime?.renderArchivedHistoryBatch(batch) || '';
  }

  function renderDialogHistory(options = {}) {
    dialogsDetailsHistoryRuntime?.renderDialogHistory(options);
  }

  function renderHistory(messages, options = {}) {
    dialogsDetailsHistoryRuntime?.renderHistory(messages, options);
  }

  function appendHistoryMessage(message) {
    dialogsDetailsHistoryRuntime?.appendHistoryMessage(message);
  }

  function historyMarker(messages) {
    return dialogsDetailsHistoryRuntime?.historyMarker(messages) || 'empty';
  }

  function withChannelParam(path, channelId) {
    if (!channelId) return path;
    const separator = path.includes('?') ? '&' : '?';
    return `${path}${separator}channelId=${encodeURIComponent(channelId)}`;
  }

  function normalizeChannelId(channelId) {
    if (channelId === null || channelId === undefined) return null;
    const normalized = String(channelId).trim();
    return normalized || null;
  }

  function getRouteChannelId(search = window.location.search || '') {
    return normalizeChannelId(new URLSearchParams(search).get('channelId'));
  }

  function findDialogRowByTicketId(ticketId, channelId = null) {
    const normalizedTicketId = String(ticketId || '').trim();
    const normalizedChannelId = normalizeChannelId(channelId);
    if (!normalizedTicketId) return null;
    if (normalizedChannelId) {
      const exactMatch = rowsList().find((row) => {
        return String(row.dataset.ticketId || '') === normalizedTicketId
          && normalizeChannelId(row.dataset.channelId) === normalizedChannelId;
      });
      if (exactMatch) {
        return exactMatch;
      }
    }
    return rowsList().find((row) => String(row.dataset.ticketId || '') === normalizedTicketId) || null;
  }

  async function loadPreviousDialogHistory() {
    return dialogsDetailsHistoryRuntime?.loadPreviousDialogHistory();
  }

  async function refreshHistory() {
    return dialogsDetailsHistoryRuntime?.refreshHistory();
  }

  function startHistoryPolling() {
    dialogsDetailsHistoryRuntime?.startHistoryPolling();
  }

  function stopHistoryPolling() {
    dialogsDetailsHistoryRuntime?.stopHistoryPolling();
  }

  function updateResolveButton(statusRaw) {
    dialogsActionsRuntime?.updateResolveButton(statusRaw);
  }

  function updateRowStatus(row, statusRaw, statusLabel, statusKey, unreadCount = 0) {
    dialogsActionsRuntime?.updateRowStatus(row, statusRaw, statusLabel, statusKey, unreadCount);
  }

  function updateDetailsStatusSummary(statusLabel, statusKey = 'waiting_client') {
    dialogsDetailsRuntime?.updateDetailsStatusSummary(statusLabel, statusKey);
  }

  function formatStatusLabel(raw, fallback, statusKey) {
    return dialogsDetailsRuntime?.formatStatusLabel(raw, fallback, statusKey) || '—';
  }

  function isResolvedStatus(statusRaw, statusKey, statusLabel) {
    return dialogsDetailsRuntime?.isResolvedStatus(statusRaw, statusKey, statusLabel) || false;
  }

  async function openDialogDetails(ticketId, fallbackRow) {
    await dialogsDetailsRuntime?.openDialogDetails(ticketId, fallbackRow);
  }

  table.addEventListener('click', (event) => {
    const target = event.target instanceof Element ? event.target : event.target?.parentElement;
    if (!target) return;

    const rowSelect = target.closest('.dialog-row-select');
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

    const openBtn = target.closest('.dialog-open-btn');
    if (openBtn) {
      event.preventDefault();
      event.stopPropagation();
      const actionMenu = openBtn.closest('.dialog-actions-dropdown');
      if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
      const ticketId = openBtn.dataset.ticketId;
      const row = openBtn.closest('tr');
      debugLog('table.click.openBtn', {
        ticketId: ticketId || null,
        href: openBtn.getAttribute('href'),
      });
      setActiveDialogRow(row, { ensureVisible: true });
      openDialogEntry(ticketId, row);
      return;
    }
    const taskBtn = target.closest('.dialog-task-btn');
    if (taskBtn) {
      event.preventDefault();
      const actionMenu = taskBtn.closest('.dialog-actions-dropdown');
      if (actionMenu) setDialogActionsMenuOpen(actionMenu, false);
      const ticketId = taskBtn.dataset.ticketId;
      const clientName = taskBtn.dataset.client;
      openTaskCreateSurface(ticketId, clientName);
      return;
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

  runDialogsInitStep('dialogsActionsRuntime.bindDocumentQuickActions', () => dialogsActionsRuntime?.bindDocumentQuickActions());
  runDialogsInitStep('dialogsActionsRuntime.bindTableQuickActions', () => dialogsActionsRuntime?.bindTableQuickActions());
  runDialogsInitStep('dialogsActionsRuntime.bindDetailsQuickActions', () => dialogsActionsRuntime?.bindDetailsQuickActions());
  runDialogsInitStep('dialogsActionsRuntime.bindDetailsReplyActions', () => dialogsActionsRuntime?.bindDetailsReplyActions());
  runDialogsInitStep('dialogsActionsRuntime.bindWorkspaceQuickActions', () => dialogsActionsRuntime?.bindWorkspaceQuickActions());
  runDialogsInitStep('dialogsWorkspaceRuntime.bindWorkspaceInteractionEvents', () => dialogsWorkspaceRuntime?.bindWorkspaceInteractionEvents());
  runDialogsInitStep('dialogsTemplatesRuntime.bindTemplateEvents', () => dialogsTemplatesRuntime?.bindTemplateEvents());
  runDialogsInitStep('dialogsMacroRuntime.bindMacroTemplateEvents', () => dialogsMacroRuntime?.bindMacroTemplateEvents());
  runDialogsInitStep('dialogsMyDialogsRuntime.bindPanelEvents', () => dialogsMyDialogsRuntime?.bindPanelEvents());

  syncDialogAssignControls();

  if (detailsCreateTask) {
    detailsCreateTask.addEventListener('click', (event) => {
      event.preventDefault();
      const ticketId = String(activeDialogTicketId || activeWorkspaceTicketId || '').trim();
      const client = String(detailsClientName?.textContent || '').trim();
      if (!ticketId) return;
      openTaskCreateSurface(ticketId, client);
    });
  }

  if (detailsOpenClientCard) {
    detailsOpenClientCard.addEventListener('click', (event) => {
      event.preventDefault();
      const userId = String(detailsOpenClientCard.dataset.userId || '').trim();
      if (!userId) return;
      const clientUrl = `/client/${encodeURIComponent(userId)}`;
      const opened = window.open(clientUrl, '_blank', 'noopener');
      if (!opened) {
        window.location.href = clientUrl;
      }
    });
  }

  runDialogsInitStep('dialogsParticipantsRuntime.bindParticipantEvents', () => dialogsParticipantsRuntime?.bindParticipantEvents());

  if (workspaceCreateTaskBtn) {
    workspaceCreateTaskBtn.addEventListener('click', (event) => {
      event.preventDefault();
      const ticketId = String(activeWorkspaceTicketId || '').trim();
      const client = String(detailsClientName?.textContent || '').trim();
      if (!ticketId) return;
      openTaskCreateSurface(ticketId, client);
    });
  }

  function resolveDetailsTicketId() {
    return dialogsActionsRuntime?.resolveDetailsTicketId() || null;
  }

  function renderWorkspaceMessageItem(message) {
    return dialogsPresentationRuntime?.renderWorkspaceMessageItem(message) || '';
  }

  async function sendMediaFiles(files, options = {}) {
    return dialogsDetailsHistoryRuntime?.sendMediaFiles(files, options);
  }

  function extractClipboardImageFiles(event) {
    return dialogsDetailsHistoryRuntime?.extractClipboardImageFiles(event) || [];
  }

  async function sendWorkspaceMediaFiles(files, options = {}) {
    await dialogsDetailsHistoryRuntime?.sendWorkspaceMediaFiles(files, options);
  }

  function stageMediaFilesInInput(mediaInput, files) {
    return dialogsDetailsHistoryRuntime?.stageMediaFilesInInput(mediaInput, files) || 0;
  }

  function getPendingMediaFiles(mediaInput) {
    return dialogsDetailsHistoryRuntime?.getPendingMediaFiles(mediaInput) || [];
  }

  function clearPendingMediaFiles(mediaInput) {
    dialogsDetailsHistoryRuntime?.clearPendingMediaFiles(mediaInput);
  }


  runDialogsInitStep('dialogsDetailsHistoryRuntime.bindHistoryInteractionEvents', () => dialogsDetailsHistoryRuntime?.bindHistoryInteractionEvents());

  if (dialogFontSizeRange) {
    dialogFontSizeRange.addEventListener('input', () => {
      applyDialogFontSize(dialogFontSizeRange.value);
    });
  }

  if (quickSearch) {
    quickSearch.addEventListener('input', () => applyQuickSearch(quickSearch.value));
  }

  if (pagePrevBtn) {
    pagePrevBtn.addEventListener('click', () => {
      const currentPage = Math.max(1, Number.parseInt(filterState.page, 10) || 1);
      if (currentPage <= 1) return;
      filterState.page = currentPage - 1;
      applyFilters();
    });
  }

  if (pageNextBtn) {
    pageNextBtn.addEventListener('click', () => {
      const currentPage = Math.max(1, Number.parseInt(filterState.page, 10) || 1);
      filterState.page = currentPage + 1;
      applyFilters();
    });
  }

  dialogsListRuntime?.bindViewStateEvents();

  bindFallbackModalDismiss(filtersModalEl, filtersModal);
  bindFallbackModalDismiss(columnsModalEl, columnsModal);
  bindFallbackModalDismiss(hotkeysModalEl, hotkeysModal);
  bindFallbackModalDismiss(mediaPreviewModalEl, mediaPreviewModal);
  bindFallbackModalDismiss(participantsModalEl, participantsModal);
  bindFallbackModalDismiss(reassignModalEl, reassignModal);
  bindModalScrollContainment(detailsHistory);
  bindModalScrollContainment(detailsSidebarScroll);

  if (filtersBtn && filtersModalEl) {
    filtersBtn.addEventListener('click', () => {
      if (filtersForm) {
        filtersForm.search.value = filterState.search || '';
        filtersForm.status.value = filterState.status || '';
      }
      showModalSafe(filtersModalEl, filtersModal);
    });
  }

  if (filtersApply) {
    filtersApply.addEventListener('click', () => {
      if (filtersForm) {
        filterState.search = filtersForm.search.value;
        filterState.status = filtersForm.status.value;
        if (quickSearch) quickSearch.value = filterState.search || '';
      }
      applyFilters({ resetPage: true });
      hideModalSafe(filtersModalEl, filtersModal);
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
      persistDialogPreferences();
      applyFilters({ resetPage: true });
    });
  }

  if (hotkeysBtn && hotkeysModalEl) {
    hotkeysBtn.addEventListener('click', () => {
      showModalSafe(hotkeysModalEl, hotkeysModal);
    });
  }

  dialogsShellRuntime?.bindColumnStateEvents();
  dialogsShellRuntime?.bindColumnOrderEvents();

  if (detailsModalEl) {
    bindFallbackModalDismiss(detailsModalEl, detailsModal);
  }

  if (categoriesModalEl) {
    bindFallbackModalDismiss(categoriesModalEl, categoriesModal);
  }

  if (workspaceAiSuggestionsRefresh) {
    workspaceAiSuggestionsRefresh.addEventListener('click', () => {
      const ticketId = activeWorkspaceTicketId || workspaceComposerTicketId;
      loadWorkspaceAiSuggestions(ticketId);
      loadWorkspaceAiReview(ticketId);
      loadWorkspaceAiControl(ticketId);
    });
  }

  if (detailsAiRefresh) {
    detailsAiRefresh.addEventListener('click', () => {
      loadDetailsAiSuggestions(activeDialogTicketId);
    });
  }

  if (aiReviewQueueRefresh) {
    aiReviewQueueRefresh.addEventListener('click', () => {
      loadAiReviewQueue();
    });
  }

  if (aiMonitoringRefresh) {
    aiMonitoringRefresh.addEventListener('click', () => {
      loadAiMonitoringSummary(7);
    });
  }

  if (dialogCompactToggle) {
    dialogCompactToggle.addEventListener('click', () => {
      toggleCompactMode();
    });
  }

  if (dialogListOnlyToggle) {
    dialogListOnlyToggle.addEventListener('click', () => {
      toggleListOnlyMode();
    });
  }

  if (workspaceTabButtons.length) {
    workspaceTabButtons.forEach((button) => {
      button.addEventListener('click', () => {
        switchWorkspaceTab(button.dataset.workspaceTab || 'client');
      });
    });
  }

  if (workspaceAiIncidentExport) {
    workspaceAiIncidentExport.addEventListener('click', () => {
      exportWorkspaceIncidentCsv();
    });
  }

  if (aiMonitoringApplyFilters) {
    aiMonitoringApplyFilters.addEventListener('click', () => {
      aiMonitoringFilters.eventType = String(aiMonitoringEventTypeFilter?.value || '').trim().toLowerCase();
      aiMonitoringFilters.actor = String(aiMonitoringActorFilter?.value || '').trim().toLowerCase();
      loadAiMonitoringEvents(7, 50);
    });
  }

  if (aiMonitoringExportCsv) {
    aiMonitoringExportCsv.addEventListener('click', () => {
      aiMonitoringFilters.eventType = String(aiMonitoringEventTypeFilter?.value || '').trim().toLowerCase();
      aiMonitoringFilters.actor = String(aiMonitoringActorFilter?.value || '').trim().toLowerCase();
      exportAiMonitoringEventsCsv(7, 200);
    });
  }

  if (aiReviewQueueBody) {
    aiReviewQueueBody.addEventListener('click', async (event) => {
      const row = event.target.closest('tr[data-ai-review-query-key]');
      if (!row) return;
      const queryKey = String(row.dataset.aiReviewQueryKey || '').trim();
      const ticketId = String(row.dataset.aiReviewTicketId || '').trim();
      if (!queryKey) return;

      if (event.target.closest('[data-ai-review-open]')) {
        if (!ticketId) return;
        const rowEl = rowsList().find((item) => String(item.dataset.ticketId || '') === ticketId) || null;
        setActiveDialogRow(rowEl, { ensureVisible: true });
        await openDialogWithWorkspaceFallback(ticketId, rowEl, { source: 'ai_review_queue' });
        return;
      }

      if (event.target.closest('[data-ai-review-approve]')) {
        try {
          const resp = await fetch(`/api/dialogs/ai-reviews/${encodeURIComponent(queryKey)}/approve`, {
            method: 'POST',
            credentials: 'same-origin',
          });
          const payload = await resp.json().catch(() => ({}));
          if (!resp.ok || payload.success === false) throw new Error(payload.error || (`HTTP ${resp.status}`));
          if (typeof showNotification === 'function') showNotification('Правка принята', 'success');
          loadAiReviewQueue();
          if (ticketId && String(activeWorkspaceTicketId || '') === ticketId) {
            loadWorkspaceAiReview(ticketId);
            loadWorkspaceAiSuggestions(ticketId);
          }
        } catch (error) {
          if (typeof showNotification === 'function') showNotification(`Не удалось принять правку: ${error.message || 'unknown_error'}`, 'warning');
        }
        return;
      }

      if (event.target.closest('[data-ai-review-reject]')) {
        try {
          const resp = await fetch(`/api/dialogs/ai-reviews/${encodeURIComponent(queryKey)}/reject`, {
            method: 'POST',
            credentials: 'same-origin',
          });
          const payload = await resp.json().catch(() => ({}));
          if (!resp.ok || payload.success === false) throw new Error(payload.error || (`HTTP ${resp.status}`));
          if (typeof showNotification === 'function') showNotification('Правка отклонена', 'success');
          loadAiReviewQueue();
          if (ticketId && String(activeWorkspaceTicketId || '') === ticketId) {
            loadWorkspaceAiReview(ticketId);
            loadWorkspaceAiSuggestions(ticketId);
          }
        } catch (error) {
          if (typeof showNotification === 'function') showNotification(`Не удалось отклонить правку: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
    });
  }

  if (workspaceAiReviewApprove) {
    workspaceAiReviewApprove.addEventListener('click', async () => {
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      const clientMessageId = Number(workspaceAiReviewProblemSelect?.value || '');
      const operatorMessageId = Number(workspaceAiReviewSolutionSelect?.value || '');
      const payloadBody = {};
      if (Number.isFinite(clientMessageId) && clientMessageId > 0) payloadBody.clientMessageId = clientMessageId;
      if (Number.isFinite(operatorMessageId) && operatorMessageId > 0) payloadBody.operatorMessageId = operatorMessageId;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/ai-review/approve`, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payloadBody),
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
        loadWorkspaceAiReview(ticketId);
        loadWorkspaceAiSuggestions(ticketId);
        if (typeof showNotification === 'function') showNotification('Правка AI-решения принята', 'success');
      } catch (error) {
        if (typeof showNotification === 'function') showNotification(`Не удалось принять правку: ${error.message || 'unknown_error'}`, 'warning');
      }
    });
  }

  if (workspaceAiReviewLearn) {
    workspaceAiReviewLearn.addEventListener('click', async () => {
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      const clientProblemMessage = resolveAiReviewSelectedMessageText(
        workspaceAiReviewProblemSelect,
        workspaceAiReviewProblemCandidates
      );
      const operatorSolutionMessage = resolveAiReviewSelectedMessageText(
        workspaceAiReviewSolutionSelect,
        workspaceAiReviewSolutionCandidates
      );
      if (!clientProblemMessage || !operatorSolutionMessage) {
        if (typeof showNotification === 'function') {
          showNotification('Выберите сообщение клиента и сообщение оператора для обучения.', 'warning');
        }
        return;
      }
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/ai-learning/mapping`, {
          method: 'POST',
          credentials: 'same-origin',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            clientProblemMessage,
            operatorSolutionMessage,
          }),
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false || payload.updated !== true) {
          throw new Error(payload.error || `HTTP ${resp.status}`);
        }
        loadWorkspaceAiReview(ticketId);
        loadWorkspaceAiSuggestions(ticketId);
        if (typeof showNotification === 'function') {
          showNotification('Пара проблема/решение сохранена в базу знаний AI.', 'success');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`Не удалось сохранить разметку: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
    });
  }

  if (workspaceAiReviewReject) {
    workspaceAiReviewReject.addEventListener('click', async () => {
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/ai-review/reject`, {
          method: 'POST',
          credentials: 'same-origin',
        });
        const payload = await resp.json().catch(() => ({}));
        if (!resp.ok || payload.success === false) throw new Error(payload.error || `HTTP ${resp.status}`);
        loadWorkspaceAiReview(ticketId);
        if (typeof showNotification === 'function') showNotification('Оставлено текущее AI-решение', 'success');
      } catch (error) {
        if (typeof showNotification === 'function') showNotification(`Не удалось отклонить правку: ${error.message || 'unknown_error'}`, 'warning');
      }
    });
  }

  if (workspaceAiSuggestionsList) {
    workspaceAiSuggestionsList.addEventListener('click', async (event) => {
      const applyBtn = event.target.closest('[data-ai-suggestion-apply]');
      if (applyBtn) {
        const reply = String(applyBtn.dataset.aiSuggestionReply || '').trim();
        if (!reply) return;
        appendToWorkspaceComposer(reply);
        return;
      }
      const applyEditBtn = event.target.closest('[data-ai-suggestion-apply-edit]');
      if (applyEditBtn) {
        const reply = String(applyEditBtn.dataset.aiSuggestionReply || '').trim();
        if (!reply) return;
        appendToWorkspaceComposer(reply);
        if (workspaceComposerText) {
          const len = workspaceComposerText.value.length;
          workspaceComposerText.focus();
          workspaceComposerText.setSelectionRange(len, len);
        }
        return;
      }
      const rejectBtn = event.target.closest('[data-ai-suggestion-reject]');
      if (!rejectBtn) return;
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      const source = String(rejectBtn.dataset.aiSuggestionSource || '').trim();
      const title = String(rejectBtn.dataset.aiSuggestionTitle || '').trim();
      const snippet = String(rejectBtn.dataset.aiSuggestionSnippet || '').trim();
      const suggestedReply = String(rejectBtn.dataset.aiSuggestionReply || '').trim();
      try {
        await sendAiSuggestionFeedback(ticketId, {
          decision: 'rejected',
          source,
          title,
          snippet,
          suggested_reply: suggestedReply,
        });
        const card = rejectBtn.closest('article');
        if (card) card.remove();
        if (typeof showNotification === 'function') {
          showNotification('Подсказка отклонена и отправлена в feedback-loop', 'info');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`Не удалось отклонить подсказку: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
    });
  }

  if (detailsAiList) {
    detailsAiList.addEventListener('click', (event) => {
      const btn = event.target instanceof Element ? event.target.closest('[data-details-ai-insert]') : null;
      if (!btn || !detailsReplyText) return;
      const suggestion = String(btn.getAttribute('data-details-ai-insert') || '').trim();
      if (!suggestion) return;
      const decoded = suggestion
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&amp;', '&');
      detailsReplyText.value = detailsReplyText.value
        ? `${detailsReplyText.value}\n\n${decoded}`
        : decoded;
      detailsReplyText.focus();
      detailsReplyText.dispatchEvent(new Event('input', { bubbles: true }));
      if (typeof showNotification === 'function') {
        showNotification('Подсказка AI добавлена в поле ответа', 'success');
      }
    });
  }

  if (workspaceAiDisableForDialog) {
    workspaceAiDisableForDialog.addEventListener('click', async () => {
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      const disableMode = !/enable/i.test(String(workspaceAiDisableForDialog.textContent || ''));
      try {
        await updateWorkspaceAiControl(ticketId, {
          ai_disabled: disableMode,
          reason: disableMode ? 'disabled_by_operator' : 'enabled_by_operator',
        }, disableMode ? 'AI отключен для этого диалога' : 'AI включен для этого диалога');
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`Не удалось изменить режим AI: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
    });
  }

  if (workspaceAiHandoffNoAutoReply) {
    workspaceAiHandoffNoAutoReply.addEventListener('click', async () => {
      const ticketId = String(activeWorkspaceTicketId || workspaceComposerTicketId || '').trim();
      if (!ticketId) return;
      try {
        await takeDialog(ticketId, activeDialogRow, null);
        await updateWorkspaceAiControl(ticketId, {
          ai_disabled: false,
          auto_reply_blocked: true,
          reason: 'handoff_no_auto_reply',
        }, 'Диалог передан оператору, автоответ AI отключен');
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`Не удалось выполнить handoff: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
    });
  }

  if (mediaPreviewZoomIn) {
    mediaPreviewZoomIn.addEventListener('click', () => {
      dialogsDetailsHistoryRuntime?.adjustMediaPreviewZoom('in');
    });
  }

  if (mediaPreviewZoomOut) {
    mediaPreviewZoomOut.addEventListener('click', () => {
      dialogsDetailsHistoryRuntime?.adjustMediaPreviewZoom('out');
    });
  }

  document.addEventListener('keydown', handleMediaPreviewEscape, true);
  dialogsFlowRuntime?.bindGlobalShortcutEvents();
  dialogsFlowRuntime?.bindDetailsModalLifecycle();


  function applyOperatorPermissionGuards() {
    rowsList().forEach((row) => updateRowQuickActions(row));
    updateBulkActionsState();
  }
  dialogsFlowRuntime?.bindWorkspaceAbandonTelemetry();

  initDialogTemplates();
  renderEmojiPanel();
  lastListMarker = buildDialogsMarkerFromRows(rowsList());
  startDialogsPolling();
  loadColumnOrder();
  loadColumnState();
  restoreDialogPreferences();
  loadPageSize();
  loadCompactMode();
  loadListOnlyMode();
  switchWorkspaceTab('client');
  configureSlaWindowSelect();
  if (sortModeSelect) {
    sortModeSelect.value = filterState.sortMode;
  }
  loadDialogFontSize();
  buildColumnsList();
  applyColumnOrder();
  applyColumnState();
  applyBusinessCellStyles();
  hydrateAvatars(table);
  applyOperatorPermissionGuards();
  renderExperimentInfoPanel();
  dialogsExperimentRuntime?.bindTelemetryRefreshControls();
  if (experimentInfoModalEl) {
    experimentInfoModalEl.addEventListener('show.bs.modal', () => {
      emitWorkspaceTelemetry('workspace_rollout_packet_viewed', {
        reason: 'experiment_modal',
      });
      loadExperimentTelemetrySummary();
      syncExperimentTelemetryAutoRefresh();
    });
    experimentInfoModalEl.addEventListener('hidden.bs.modal', () => {
      clearExperimentTelemetryRefreshTimer();
    });
  }
  updateAllSlaBadges();
  setInterval(updateAllSlaBadges, 60 * 1000);
  emitWorkspaceTelemetry('workspace_experiment_exposure', {
    reason: WORKSPACE_AB_TEST_CONFIG.enabled ? 'bootstrap' : 'experiment_disabled',
  });
  if (viewTabs.length) {
    const activeTab = Array.from(viewTabs).find((tab) => tab.dataset.dialogView === filterState.view)
      || Array.from(viewTabs).find((tab) => tab.classList.contains('active'));
    setViewTab(activeTab?.dataset.dialogView || filterState.view || 'all');
  } else {
    applyFilters();
  }
  renderMyDialogsPanel();
  window.openDialogDetailsByTicketId = (ticketId, runtimeOptions = {}) => {
    const normalizedTicketId = String(ticketId || '').trim();
    const routeMatch = (window.location.pathname || '').match(/^\/dialogs\/([^/]+)$/);
    const routeTicketId = routeMatch?.[1] ? decodeURIComponent(routeMatch[1]) : '';
    const normalizedChannelId = normalizeChannelId(runtimeOptions?.channelId)
      || (normalizedTicketId === INITIAL_DIALOG_TICKET_ID ? INITIAL_DIALOG_CHANNEL_ID : null)
      || (normalizedTicketId === routeTicketId
        ? getRouteChannelId()
        : null);
    if (!normalizedTicketId) return;
    debugLog('window.openDialogDetailsByTicketId.called', {
      ticketId: normalizedTicketId,
      channelId: normalizedChannelId,
    });
    const row = findDialogRowByTicketId(normalizedTicketId, normalizedChannelId);
    setActiveDialogRow(row, { ensureVisible: true });
    const source = normalizedTicketId === routeTicketId ? 'initial_route' : 'manual_open';
    openDialogSurface(normalizedTicketId, row, {
      source,
      channelId: normalizedChannelId,
    });
  };
  window.refreshAiReviewQueue = () => {};

  void loadServerTriagePreferences();
  if (aiMonitoringSection && aiMonitoringState) {
    loadAiMonitoringSummary(7);
    setInterval(() => loadAiMonitoringSummary(7), 60 * 1000);
  }

  if (INITIAL_DIALOG_TICKET_ID) {
    debugLog('initialTicket.autoload', {
      ticketId: INITIAL_DIALOG_TICKET_ID,
      channelId: INITIAL_DIALOG_CHANNEL_ID,
    });
    const initialRow = findDialogRowByTicketId(INITIAL_DIALOG_TICKET_ID, INITIAL_DIALOG_CHANNEL_ID);
    if (initialRow) {
      setActiveDialogRow(initialRow, { ensureVisible: true });
    }
    openDialogDetails(INITIAL_DIALOG_TICKET_ID, initialRow);
  }

  window.addEventListener('beforeunload', () => {
    if (!workspaceComposerTicketId || !workspaceComposerText || !workspaceComposerText.value.trim()) return;
    saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { reason: 'autosave' });
  });

  if (OPEN_DIALOGS_IN_WORKSPACE && WORKSPACE_EXPERIENCE_ENABLED && WORKSPACE_INLINE_NAVIGATION) {
    window.addEventListener('popstate', () => {
      const path = window.location.pathname || '';
      const match = path.match(/^\/dialogs\/([^/]+)$/);
      if (!match?.[1]) return;
      const ticketId = decodeURIComponent(match[1]);
      const channelId = getRouteChannelId();
      if (!confirmWorkspaceTicketSwitch(ticketId)) {
        const rollbackUrl = buildWorkspaceDialogUrl(activeWorkspaceTicketId, activeWorkspaceChannelId);
        window.history.pushState({ ticketId: activeWorkspaceTicketId }, '', rollbackUrl);
        return;
      }
      const row = findDialogRowByTicketId(ticketId, channelId);
      setActiveDialogRow(row, { ensureVisible: true });
      openDialogSurface(ticketId, row, { source: 'initial_route', channelId });
    });
  }

  initColumnResize();
  restoreColumnWidths();
  initDetailsResize();
  window.__dialogsPrimaryReady = true;
})();


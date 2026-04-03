// panel/static/dialogs.js
(function () {
  const table = document.getElementById('dialogsTable');
  if (!table) return;

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
  const workspaceAiReviewCurrent = document.getElementById('workspaceAiReviewCurrent');
  const workspaceAiReviewPending = document.getElementById('workspaceAiReviewPending');
  const workspaceAiReviewApprove = document.getElementById('workspaceAiReviewApprove');
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
  const detailsOpenClientCard = document.getElementById('dialogDetailsOpenClientCard');
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
  const hotkeysModal = (typeof bootstrap !== 'undefined' && hotkeysModalEl)
    ? new bootstrap.Modal(hotkeysModalEl)
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

  function normalizeDialogView(value) {
    const normalized = String(value || '').trim().toLowerCase();
    const allowed = new Set(['all', 'active', 'new', 'unassigned', 'overdue', 'sla_critical', 'escalation_required']);
    return allowed.has(normalized) ? normalized : 'all';
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
  const DIALOG_DEFAULT_VIEW = normalizeDialogView(window.DIALOG_CONFIG?.default_view || 'all');
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
  const INITIAL_DIALOG_TICKET_ID = String(document.body?.dataset?.initialDialogTicketId || '').trim();
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
  let macroTemplatesCache = [];
  let macroVariableCatalogInitialized = false;
  let macroVariableCatalogTicketId = null;
  let macroVariableDefaults = {};
  let workspaceComposerTicketId = '';
  let workspaceFailureStreak = 0;
  let workspaceTemporarilyDisabledUntil = 0;
  let workspaceComposerMacroTemplates = [];
  let activeWorkspaceMacroTemplate = null;
  let activeWorkspaceTicketId = INITIAL_DIALOG_TICKET_ID;
  let activeWorkspaceChannelId = null;
  let activeWorkspacePayload = null;
  let workspaceMessagesNextCursor = null;
  let workspaceMessagesHasMore = false;
  let workspaceMessagesLoadingMore = false;
  let workspaceDraftAutosaveTimer = null;
  let workspaceDraftLastSavedValue = '';
  let workspaceDraftLastTelemetryAt = 0;
  let workspaceLastProfileGapSignature = '';
  let workspaceLastContextSourceGapSignature = '';
  let workspaceLastAttributePolicyGapSignature = '';
  let workspaceLastContextBlockGapSignature = '';
  let workspaceLastSlaPolicyGapSignature = '';
  let workspaceLastParityGapSignature = '';
  const workspaceContextDisclosureSignatures = new Set();


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


  function resolveWorkspaceRolloutBannerClass(tone) {
    switch (String(tone || '').toLowerCase()) {
      case 'success':
        return 'alert alert-success py-2 px-3 small mb-3';
      case 'warning':
        return 'alert alert-warning py-2 px-3 small mb-3';
      case 'danger':
        return 'alert alert-danger py-2 px-3 small mb-3';
      default:
        return 'alert alert-info py-2 px-3 small mb-3';
    }
  }

  function renderWorkspaceRolloutBanner(rollout) {
    if (!workspaceRolloutBanner) return;
    const summary = String(rollout?.summary || '').trim();
    const reviewedAt = formatTimestamp(rollout?.reviewed_at_utc || '', { includeTime: true, fallback: '' });
    const dataUpdatedAt = formatTimestamp(rollout?.data_updated_at_utc || '', { includeTime: true, fallback: '' });
    const metaParts = [];
    if (String(rollout?.mode || '').trim()) {
      metaParts.push(`mode: ${String(rollout.mode).trim()}`);
    }
    if (Number.isFinite(Number(rollout?.rollout_percent)) && Number(rollout?.rollout_percent) > 0) {
      metaParts.push(`rollout: ${Math.max(0, Math.min(100, Number(rollout.rollout_percent)))}%`);
    }
    if (reviewedAt && reviewedAt !== '—') {
      metaParts.push(`reviewed UTC: ${reviewedAt}`);
    }
    if (dataUpdatedAt && dataUpdatedAt !== '—') {
      metaParts.push(`data updated UTC: ${dataUpdatedAt}`);
    }
    workspaceRolloutBanner.className = resolveWorkspaceRolloutBannerClass(rollout?.banner_tone);
    workspaceRolloutBanner.classList.remove('d-none');
    workspaceRolloutBanner.textContent = [summary || 'Workspace rollout state loaded.', metaParts.join(' · ')].filter(Boolean).join(' ');
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

  function resolveWorkspaceParityBannerClass(status) {
    switch (String(status || '').toLowerCase()) {
      case 'ok':
        return 'alert alert-success py-2 px-3 small mb-3';
      case 'blocked':
        return 'alert alert-danger py-2 px-3 small mb-3';
      default:
        return 'alert alert-warning py-2 px-3 small mb-3';
    }
  }

  function renderWorkspaceParityBanner(parity) {
    if (!workspaceParityBanner) return;
    const safeParity = parity && typeof parity === 'object' ? parity : null;
    const summary = String(safeParity?.summary || '').trim();
    const status = String(safeParity?.status || '').trim().toLowerCase();
    if (!safeParity || !summary) {
      workspaceParityBanner.classList.add('d-none');
      workspaceParityBanner.textContent = '';
      return;
    }
    const checkedAtUtc = formatTimestamp(safeParity?.checked_at, { includeTime: true, fallback: '' });
    const missingLabels = Array.isArray(safeParity?.missing_labels)
      ? safeParity.missing_labels.filter(Boolean).map((item) => String(item).trim())
      : [];
    const metaParts = [];
    if (Number.isFinite(Number(safeParity?.score_pct))) {
      metaParts.push(`parity score: ${Math.max(0, Math.min(100, Number(safeParity.score_pct)))}%`);
    }
    if (checkedAtUtc && checkedAtUtc !== '—') {
      metaParts.push(`checked UTC: ${checkedAtUtc}`);
    }
    if (missingLabels.length > 0) {
      metaParts.push(`gaps: ${missingLabels.join(', ')}`);
    }
    workspaceParityBanner.className = resolveWorkspaceParityBannerClass(status);
    workspaceParityBanner.classList.remove('d-none');
    workspaceParityBanner.textContent = [summary, metaParts.join(' · ')].filter(Boolean).join(' ');
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
  let activeDialogRow = null;
  let historyPollTimer = null;
  let listPollTimer = null;
  let lastHistoryMarker = null;
  let lastListMarker = null;
  let historyLoading = false;
  let listLoading = false;
  let activeDialogContext = {
    clientName: '—',
    operatorName: '—',
    channelName: '—',
    business: '—',
    location: '—',
    status: '—',
    createdAt: '—',
  };
  let completionHideTimer = null;
  let activeAudioPlayer = null;
  let activeAudioSource = null;
  let selectedCategories = new Set();
  let activeReplyToTelegramId = null;
  let activeWorkspaceReplyToTelegramId = null;
  const selectedTicketIds = new Set();
  let mediaImageScale = 1;
  let categorySaveTimer = null;
  let activeMacroTemplate = null;
  let activeMacroMeta = null;
  let experimentTelemetryRefreshTimer = null;
  const workspaceOpenTimers = new Map();
  const workspaceFirstInteractionTickets = new Set();
  const workspaceExperimentContext = resolveWorkspaceExperimentContext();
  const WORKSPACE_EXPERIENCE_ENABLED = resolveWorkspaceExperienceEnabled();

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
  let triagePreferencesLoadedFromServer = false;
  let triagePreferencesSaveTimer = null;

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

  function configureSlaWindowSelect() {
    if (!slaWindowSelect) {
      return;
    }
    const optionsMarkup = ['<option value="">SLA: все</option>']
      .concat(DIALOG_SLA_WINDOW_PRESETS.map((minutes) => `<option value="${minutes}">Реакция ≤ ${minutes}м</option>`))
      .join('');
    slaWindowSelect.innerHTML = optionsMarkup;
    slaWindowSelect.value = Number.isFinite(filterState.slaWindowMinutes)
      ? String(filterState.slaWindowMinutes)
      : '';
  }

  function persistPageSize() {
    if (filterState.pageSize === Infinity) {
      localStorage.setItem(STORAGE_PAGE_SIZE, 'all');
      queueServerTriagePreferencesSave();
      return;
    }
    localStorage.setItem(STORAGE_PAGE_SIZE, String(filterState.pageSize));
    queueServerTriagePreferencesSave();
  }

  function restoreDialogPreferences() {
    try {
      const storedView = localStorage.getItem(STORAGE_VIEW);
      if (storedView) {
        filterState.view = normalizeDialogView(storedView);
      }
      const storedSlaWindow = Number.parseInt(localStorage.getItem(STORAGE_SLA_WINDOW), 10);
      if (Number.isFinite(storedSlaWindow) && DIALOG_SLA_WINDOW_PRESETS.includes(storedSlaWindow)) {
        filterState.slaWindowMinutes = storedSlaWindow;
      }
      const storedSortMode = String(localStorage.getItem(STORAGE_SORT_MODE) || '').trim().toLowerCase();
      filterState.sortMode = storedSortMode === 'sla_priority' ? 'sla_priority' : filterState.sortMode;
    } catch (_error) {
      // ignore storage read errors
    }
  }

  function persistDialogPreferences() {
    try {
      localStorage.setItem(STORAGE_VIEW, filterState.view || 'all');
      if (Number.isFinite(filterState.slaWindowMinutes) && filterState.slaWindowMinutes > 0) {
        localStorage.setItem(STORAGE_SLA_WINDOW, String(filterState.slaWindowMinutes));
      } else {
        localStorage.removeItem(STORAGE_SLA_WINDOW);
      }
      localStorage.setItem(STORAGE_SORT_MODE, filterState.sortMode || 'default');
      queueServerTriagePreferencesSave();
    } catch (_error) {
      // ignore storage write errors
    }
  }

  function applyServerTriagePreferences(preferences) {
    if (!preferences || typeof preferences !== 'object') return false;
    let changed = false;
    const rawView = String(preferences.view || '').trim().toLowerCase();
    if (rawView) {
      const normalizedView = normalizeDialogView(rawView);
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
    if (Number.isFinite(rawSlaWindow) && DIALOG_SLA_WINDOW_PRESETS.includes(rawSlaWindow)) {
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
      const normalizedPageSize = normalizePageSize(rawPageSize);
      if (normalizedPageSize !== filterState.pageSize) {
        filterState.pageSize = normalizedPageSize;
        changed = true;
      }
    }
    return changed;
  }

  async function loadServerTriagePreferences() {
    try {
      const response = await fetch('/api/dialogs/triage-preferences', { headers: { Accept: 'application/json' } });
      if (!response.ok) return;
      const payload = await response.json().catch(() => null);
      if (!payload || payload.success !== true || !payload.preferences) return;
      if (applyServerTriagePreferences(payload.preferences)) {
        if (pageSizeSelect) {
          pageSizeSelect.value = filterState.pageSize === Infinity ? 'all' : String(filterState.pageSize);
        }
        configureSlaWindowSelect();
        if (sortModeSelect) {
          sortModeSelect.value = filterState.sortMode;
        }
        const activeTab = Array.from(viewTabs).find((tab) => tab.dataset.dialogView === filterState.view);
        if (activeTab) {
          setViewTab(filterState.view);
        } else {
          applyFilters();
        }
      }
      triagePreferencesLoadedFromServer = true;
    } catch (_error) {
      // ignore network errors and continue with local defaults
    }
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

  async function saveServerTriagePreferences() {
    const payload = {
      view: filterState.view || 'all',
      sort_mode: filterState.sortMode || 'default',
      sla_window_minutes: Number.isFinite(filterState.slaWindowMinutes) ? filterState.slaWindowMinutes : null,
      page_size: filterState.pageSize === Infinity ? 'all' : String(filterState.pageSize || DEFAULT_PAGE_SIZE),
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
        emitWorkspaceTelemetry('triage_preferences_saved', {
          reason: `view=${payload.view};sort=${payload.sort_mode};sla=${payload.sla_window_minutes || 'all'};page=${payload.page_size}`,
        });
      }
    } catch (_error) {
      // ignore network errors and keep local-storage behavior
    }
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
    const ticketId = activeDialogTicketId || activeWorkspaceTicketId;
    if (!ticketId) return;
    const payload = { categories };
    const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/categories`, {
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
    if (!activeDialogTicketId && !activeWorkspaceTicketId) return;
    if (categorySaveTimer) {
      clearTimeout(categorySaveTimer);
    }
    categorySaveTimer = setTimeout(async () => {
      try {
        const categories = Array.from(selectedCategories);
        await persistDialogCategories(categories);
        updateSummaryCategories(formatCategoriesLabel(categories));
        renderWorkspaceCategories();
        if (workspaceCategoriesError) {
          workspaceCategoriesError.classList.add('d-none');
        }
      } catch (error) {
        if (workspaceCategoriesError) {
          workspaceCategoriesError.classList.remove('d-none');
        }
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

  function buildTaskCreateUrl(ticketId, clientName) {
    const params = new URLSearchParams();
    params.set('create', '1');
    if (ticketId) params.set('ticketId', String(ticketId));
    if (clientName) params.set('client', String(clientName));
    return `/tasks?${params.toString()}`;
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

  function resetWorkspaceReplyTarget(options = {}) {
    const hadActiveTarget = Number.isFinite(Number(activeWorkspaceReplyToTelegramId));
    activeWorkspaceReplyToTelegramId = null;
    if (workspaceReplyTarget) {
      workspaceReplyTarget.classList.add('d-none');
    }
    if (workspaceReplyTargetText) {
      workspaceReplyTargetText.textContent = '';
    }
    if (workspaceComposerText) {
      workspaceComposerText.placeholder = 'Введите ответ клиенту…';
    }
    if (hadActiveTarget && options.emitTelemetry !== false) {
      emitWorkspaceTelemetry('workspace_reply_target_cleared', {
        ticketId: activeWorkspaceTicketId,
        reason: options.reason || 'manual_clear',
        contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
      });
    }
  }

  function setWorkspaceReplyTarget(messageId, preview) {
    const normalizedMessageId = Number.parseInt(messageId, 10);
    if (!Number.isFinite(normalizedMessageId)) {
      resetWorkspaceReplyTarget({ emitTelemetry: false });
      return;
    }
    activeWorkspaceReplyToTelegramId = normalizedMessageId;
    if (workspaceComposerText) {
      workspaceComposerText.placeholder = `Ответ на сообщение #${normalizedMessageId}`;
    }
    if (workspaceReplyTarget) {
      workspaceReplyTarget.classList.remove('d-none');
    }
    if (workspaceReplyTargetText) {
      const safePreview = String(preview || '').trim();
      workspaceReplyTargetText.textContent = safePreview || `Сообщение #${normalizedMessageId}`;
    }
    emitWorkspaceTelemetry('workspace_reply_target_selected', {
      ticketId: activeWorkspaceTicketId,
      reason: `message:${normalizedMessageId}`,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
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

  function normalizeIdentity(value) {
    return String(value || '').trim().toLowerCase();
  }

  function isOwnedByCurrentOperator(responsible) {
    const owner = normalizeIdentity(responsible);
    if (!owner || owner === '—' || owner === '-') return false;
    return owner === normalizeIdentity(OPERATOR_IDENTITY);
  }

  function buildAvatarUrl(userId) {
    const normalized = String(userId || '').trim();
    if (!normalized) return '';
    return `/avatar/${encodeURIComponent(normalized)}`;
  }

  function getDialogUserId(source) {
    if (!source || typeof source !== 'object') return '';
    return String(
      source.userId
      || source.user_id
      || source.clientUserId
      || source.client_user_id
      || '',
    ).trim();
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
    const createdAt = parseUtcDateValue(createdAtRaw);
    if (!createdAtRaw || !createdAt) {
      return { label: 'Нет даты', className: 'dialog-sla-closed', title: 'Не удалось определить время создания обращения' };
    }
    const ageMinutes = (Date.now() - createdAt.getTime()) / 60000;
    const minutesLeft = SLA_TARGET_MINUTES - ageMinutes;
    const deadline = new Date(createdAt.getTime() + SLA_TARGET_MINUTES * 60000);
    const deadlineLabel = formatUtcDate(deadline, { includeTime: true });
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
    const criticalPinned = isCriticalSlaDialog(row);
    const escalationRequired = isEscalationRequiredDialog(row);
    const pinMarker = criticalPinned ? ' 📌' : '';
    const escalationMarker = escalationRequired ? ' ⚠' : '';
    badge.className = `badge rounded-pill dialog-sla-badge ${state.className}`;
    badge.textContent = `${state.label}${pinMarker}${escalationMarker}`;
    const markers = [
      criticalPinned ? 'Автопин: критичный SLA' : '',
      escalationRequired ? 'Требуется эскалация' : '',
    ].filter(Boolean).join(' · ');
    badge.title = markers ? `${state.title || ''} · ${markers}` : (state.title || '');
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
    const canTakeOwnership = !isOwnedByCurrentOperator(responsible);
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
            <div class="dialog-avatar" data-avatar-user-id="${escapeHtml(userId)}"
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
          <button type="button" class="btn btn-sm btn-outline-success dialog-take-btn ${!canTakeOwnership || !canRunAction('can_assign') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">Взять себе</button>
          <button type="button" class="btn btn-sm btn-outline-warning dialog-snooze-btn ${isResolvedStatusKey(statusKey) || !canRunAction('can_snooze') ? 'd-none' : ''}" data-ticket-id="${escapeHtml(ticketId)}">${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES)}</button>
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
      takeBtn.classList.toggle('d-none', isOwnedByCurrentOperator(value) || !canRunAction('can_assign'));
    }
  }

  function applyCompactMode(enabled) {
    const active = Boolean(enabled);
    document.body.classList.toggle('dialog-compact-mode', active);
    if (dialogCompactToggle) {
      dialogCompactToggle.textContent = active ? 'Standard mode' : 'Compact mode';
      dialogCompactToggle.setAttribute('aria-pressed', active ? 'true' : 'false');
    }
  }

  function loadCompactMode() {
    try {
      const raw = String(localStorage.getItem(STORAGE_COMPACT_MODE) || '').trim().toLowerCase();
      if (!raw) {
        applyCompactMode(true);
        localStorage.setItem(STORAGE_COMPACT_MODE, '1');
        return;
      }
      applyCompactMode(raw === '1' || raw === 'true' || raw === 'on');
    } catch (_error) {
      applyCompactMode(true);
    }
  }

  function toggleCompactMode() {
    const next = !document.body.classList.contains('dialog-compact-mode');
    applyCompactMode(next);
    try {
      localStorage.setItem(STORAGE_COMPACT_MODE, next ? '1' : '0');
    } catch (_error) {
      // ignore storage write errors
    }
  }

  function applyListOnlyMode(enabled) {
    const active = Boolean(enabled);
    document.body.classList.toggle('dialog-list-only-mode', active);
    if (dialogListOnlyToggle) {
      dialogListOnlyToggle.textContent = active ? 'Полная страница' : 'Только список';
      dialogListOnlyToggle.setAttribute('aria-pressed', active ? 'true' : 'false');
    }
  }

  function loadListOnlyMode() {
    try {
      const raw = String(localStorage.getItem(STORAGE_LIST_ONLY_MODE) || '').trim().toLowerCase();
      applyListOnlyMode(raw === '1' || raw === 'true' || raw === 'on');
    } catch (_error) {
      applyListOnlyMode(false);
    }
  }

  function toggleListOnlyMode() {
    const next = !document.body.classList.contains('dialog-list-only-mode');
    applyListOnlyMode(next);
    try {
      localStorage.setItem(STORAGE_LIST_ONLY_MODE, next ? '1' : '0');
    } catch (_error) {
      // ignore storage write errors
    }
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
    const responsible = String(row.dataset.responsible || '').trim();
    if (takeBtn) takeBtn.classList.toggle('d-none', isOwnedByCurrentOperator(responsible) || !canRunAction('can_assign'));
    if (closeBtn) closeBtn.classList.toggle('d-none', isClosed || !canRunAction('can_close'));
    if (snoozeBtn) {
      snoozeBtn.classList.toggle('d-none', isClosed || !canRunAction('can_snooze'));
      const ticketId = row.dataset.ticketId;
      const until = getSnoozeUntil(ticketId);
      snoozeBtn.textContent = until
        ? `Отложен до ${formatUtcDate(new Date(until), { includeTime: true })}`
        : formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES);
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
    emitWorkspaceTelemetry('triage_quick_close', { ticketId });
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
      emitWorkspaceTelemetry('triage_quick_assign', { ticketId });
      applyFilters();
      if (typeof showNotification === 'function') {
        showNotification('Диалог назначен на вас', 'success');
      }
    } catch (error) {
      if (btn) btn.disabled = false;
      if (typeof showNotification === 'function') {
        showNotification(error.message || 'Не удалось взять диалог', 'error');
      }
      throw error;
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
      emitWorkspaceTelemetry('triage_quick_snooze', { ticketId, reason: `minutes:${minutes}` });
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
      syncSlaOrchestrationSignals(data.sla_orchestration || null);
      applySlaOrchestrationToRows();
      const marker = buildDialogsMarker(dialogs);
      const isInitialSync = lastListMarker === null;
      if (isInitialSync || marker !== lastListMarker) {
        lastListMarker = marker;
      }
      syncDialogsTable(dialogs);
      applySlaOrchestrationToRows();
      refreshSummaryCounters(data.summary || {});
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
    view: DIALOG_DEFAULT_VIEW,
    pageSize: DEFAULT_PAGE_SIZE,
    slaWindowMinutes: DIALOG_DEFAULT_SLA_WINDOW,
    sortMode: 'default',
  };
  let lastManualSortMode = filterState.sortMode;
  const slaOrchestrationByTicket = new Map();

  function syncSlaOrchestrationSignals(slaOrchestration) {
    slaOrchestrationByTicket.clear();
    const tickets = slaOrchestration && typeof slaOrchestration === 'object'
      ? slaOrchestration.tickets
      : null;
    if (!tickets || typeof tickets !== 'object') {
      return;
    }
    Object.entries(tickets).forEach(([ticketId, signal]) => {
      if (!ticketId || !signal || typeof signal !== 'object') return;
      slaOrchestrationByTicket.set(String(ticketId), signal);
    });
  }

  function applySlaOrchestrationToRows() {
    rowsList().forEach((row) => {
      const ticketId = String(row?.dataset?.ticketId || '');
      const signal = slaOrchestrationByTicket.get(ticketId);
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
    if (minutesLeft <= SLA_WARNING_MINUTES) {
      return { bucket: 1, minutesLeft, state: 'at_risk' };
    }
    return { bucket: 2, minutesLeft, state: 'normal' };
  }

  function applySlaPriorityClass(row) {
    if (!row) return;
    row.classList.remove(
      'dialog-priority-breached',
      'dialog-priority-at-risk',
      'dialog-priority-normal',
      'dialog-priority-pinned',
      'dialog-priority-escalation-required',
    );
    const priority = resolveSlaPriority(row);
    const isPinned = isCriticalSlaDialog(row);
    const escalationRequired = isEscalationRequiredDialog(row);
    row.dataset.slaPinned = isPinned ? 'true' : 'false';
    row.classList.toggle('dialog-priority-pinned', isPinned);
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
    const leftCreated = parseUtcDateValue(String(left?.dataset?.createdAt || ''))?.getTime() ?? 0;
    const rightCreated = parseUtcDateValue(String(right?.dataset?.createdAt || ''))?.getTime() ?? 0;
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
    const createdAt = parseUtcDateValue(createdAtRaw);
    if (!createdAt) return false;
    const overdueThresholdMs = OVERDUE_THRESHOLD_HOURS * 60 * 60 * 1000;
    return Date.now() - createdAt.getTime() > overdueThresholdMs;
  }

  function isSnoozedDialog(row) {
    const ticketId = row?.dataset?.ticketId;
    return Boolean(getSnoozeUntil(ticketId));
  }

  function isCriticalSlaDialog(row) {
    if (row?.dataset?.slaServerPinned === 'true') {
      return !SLA_CRITICAL_PIN_UNASSIGNED_ONLY || isUnassignedDialog(row);
    }
    const minutesLeft = resolveSlaMinutesLeft(row);
    if (!Number.isFinite(minutesLeft)) return false;
    if (minutesLeft > SLA_CRITICAL_MINUTES) return false;
    if (SLA_CRITICAL_PIN_UNASSIGNED_ONLY && !isUnassignedDialog(row)) {
      return false;
    }
    return true;
  }

  function isEscalationRequiredDialog(row) {
    return row?.dataset?.slaEscalationRequired === 'true';
  }

  function resolveSlaMinutesLeft(row) {
    if (!row || isResolved(row)) return null;
    const serverMinutesLeft = Number(row.dataset.slaMinutesLeft);
    if (Number.isFinite(serverMinutesLeft)) {
      return serverMinutesLeft;
    }
    const createdAtRaw = String(row.dataset.createdAt || '').trim();
    if (!createdAtRaw) return null;
    const createdAt = parseUtcDateValue(createdAtRaw);
    if (!createdAt) return null;
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
        return isCriticalSlaDialog(row)
          && (!SLA_CRITICAL_VIEW_UNASSIGNED_ONLY || isUnassignedDialog(row));
      case 'escalation_required':
        return isEscalationRequiredDialog(row);
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
    const canShowBulkToolbar = count > 1;
    if (bulkToolbar) {
      bulkToolbar.classList.toggle('d-none', !canShowBulkToolbar);
    }
    if (bulkCount) {
      bulkCount.textContent = `Выбрано: ${count}`;
    }
    if (bulkTakeBtn) bulkTakeBtn.disabled = !canShowBulkToolbar || !canRunAction('can_bulk') || !canRunAction('can_assign');
    if (bulkSnoozeBtn) bulkSnoozeBtn.disabled = !canShowBulkToolbar || !canRunAction('can_bulk') || !canRunAction('can_snooze');
    if (bulkCloseBtn) bulkCloseBtn.disabled = !canShowBulkToolbar || !canRunAction('can_bulk') || !canRunAction('can_close');
    if (bulkClearBtn) bulkClearBtn.disabled = !canShowBulkToolbar;
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
      snooze: ['can_bulk', 'can_snooze', `Отложить выбранные на ${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES).replace('Отложить ', '')}`],
      close: ['can_bulk', 'can_close', 'Закрыть выбранные'],
    };
    const [bulkPermission, actionPermission, actionTitle] = permissionMap[action] || [];
    if (!canRunAction(bulkPermission) || !canRunAction(actionPermission)) {
      notifyPermissionDenied(actionTitle || 'Групповое действие');
      emitWorkspaceTelemetry('triage_bulk_action', {
        reason: `${action || 'unknown'}:permission_denied`,
      });
      return;
    }

    const rows = selectedRows();
    if (!rows.length) return;

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
      if (typeof showNotification === 'function') {
        showNotification('Нет диалогов, подходящих для выбранного группового действия.', 'warning');
      }
      emitWorkspaceTelemetry('triage_bulk_action', {
        reason: `${action || 'unknown'}:nothing_eligible:selected=${rows.length}`,
      });
      return;
    }
    if (skippedRows.length && typeof showNotification === 'function') {
      showNotification(`Пропущено ${skippedRows.length} диалог(ов): действие недоступно для текущего статуса/прав.`, 'warning');
    }
    let processedCount = 0;
    const originalDisabled = [bulkTakeBtn, bulkSnoozeBtn, bulkCloseBtn, bulkClearBtn]
      .filter(Boolean)
      .map((button) => ({ button, disabled: button.disabled }));
    originalDisabled.forEach(({ button }) => {
      button.disabled = true;
    });

    const errors = [];
    for (const row of eligibleRows) {
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
          await snoozeDialog(ticketId, QUICK_SNOOZE_MINUTES, snoozeBtn);
          setSnooze(ticketId, QUICK_SNOOZE_MINUTES);
          updateRowQuickActions(row);
        }
        if (action === 'close') {
          const closeBtn = row.querySelector('.dialog-close-btn:not(.d-none)');
          if (!closeBtn) continue;
          await closeDialogQuick(ticketId, row, closeBtn);
          clearSnooze(ticketId);
        }
        processedCount += 1;
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
        snooze: formatBulkSnoozeLabel(QUICK_SNOOZE_MINUTES),
        close: 'Выбранные диалоги закрыты',
      };
      showNotification(successMap[action] || 'Групповое действие выполнено', 'success');
    }
    emitWorkspaceTelemetry('triage_bulk_action', {
      reason: `${action || 'unknown'}:${errors.length ? 'partial_failure' : 'success'}:processed=${processedCount}:errors=${errors.length}:skipped=${skippedRows.length}`,
      ticketId: eligibleRows.length === 1 ? String(eligibleRows[0]?.dataset?.ticketId || '') : null,
    });

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
    const previousView = filterState.view || 'all';
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
    if (previousView !== resolvedView) {
      emitWorkspaceTelemetry('triage_view_switch', { reason: `${previousView}->${resolvedView}` });
    }
    persistDialogPreferences();
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
      operator_segment: workspaceExperimentContext.operatorSegment,
      primary_kpis: WORKSPACE_AB_TEST_CONFIG.primaryKpis,
      secondary_kpis: WORKSPACE_AB_TEST_CONFIG.secondaryKpis,
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

  function emitWorkspaceContextDisclosureTelemetry(eventType, detail) {
    const ticketId = String(activeWorkspaceTicketId || '').trim();
    if (!ticketId || !eventType || !detail || detail.open !== true) return;
    const reason = [
      `section:${String(detail.section || 'unknown').trim()}`,
      `items:${Number(detail.items || 0)}`,
      `required:${Number(detail.required || 0)}`,
      `gaps:${Number(detail.gaps || 0)}`,
      `hidden:${Number(detail.hidden || 0)}`,
    ].join('|');
    const signature = `${ticketId}:${eventType}:${reason}`;
    if (workspaceContextDisclosureSignatures.has(signature)) {
      return;
    }
    workspaceContextDisclosureSignatures.add(signature);
    emitWorkspaceTelemetry(eventType, {
      ticketId,
      reason,
      durationMs: Number(detail.items || 0),
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function bindWorkspaceContextDisclosureTelemetry(container) {
    if (!container) return;
    const eventBySection = {
      context_sources: 'workspace_context_sources_expanded',
      attribute_policy: 'workspace_context_attribute_policy_expanded',
      extra_attributes: 'workspace_context_extra_attributes_expanded',
    };
    container.querySelectorAll('details[data-workspace-telemetry-section]').forEach((node) => {
      if (node.dataset.telemetryBound === 'true') return;
      node.dataset.telemetryBound = 'true';
      node.addEventListener('toggle', () => {
        const section = String(node.dataset.workspaceTelemetrySection || '').trim();
        const eventType = eventBySection[section];
        if (!eventType) return;
        emitWorkspaceContextDisclosureTelemetry(eventType, {
          section,
          open: node.open === true,
          items: Number(node.dataset.workspaceTelemetryItems || 0),
          required: Number(node.dataset.workspaceTelemetryRequired || 0),
          gaps: Number(node.dataset.workspaceTelemetryGaps || 0),
          hidden: Number(node.dataset.workspaceTelemetryHidden || 0),
        });
      });
    });
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

  function renderExperimentKpiItems(container, items) {
    if (!container) return;
    const safeItems = Array.isArray(items) && items.length ? items : ['—'];
    container.innerHTML = safeItems.map((item) => `<li>${escapeHtml(String(item))}</li>`).join('');
  }

  function renderExperimentInfoPanel() {
    if (experimentInfoMeta) {
      experimentInfoMeta.textContent = `Эксперимент: ${WORKSPACE_AB_TEST_CONFIG.experimentName} · Когорта: ${workspaceExperimentContext.cohort} · Сегмент: ${workspaceExperimentContext.operatorSegment}`;
    }
    renderExperimentKpiItems(experimentPrimaryKpis, WORKSPACE_AB_TEST_CONFIG.primaryKpis);
    renderExperimentKpiItems(experimentSecondaryKpis, WORKSPACE_AB_TEST_CONFIG.secondaryKpis);
  }


  function formatGuardrailPercent(value) {
    const safe = Number(value);
    if (!Number.isFinite(safe)) return '0.00%';
    return `${(safe * 100).toFixed(2)}%`;
  }

  function formatDeltaPercent(value) {
    const safe = Number(value);
    if (!Number.isFinite(safe)) return '0.00 п.п.';
    const sign = safe > 0 ? '+' : '';
    return `${sign}${(safe * 100).toFixed(2)} п.п.`;
  }

  function formatDeltaMs(value) {
    const safe = Number(value);
    if (!Number.isFinite(safe)) return '—';
    const sign = safe > 0 ? '+' : '';
    return `${sign}${Math.round(safe)}мс`;
  }

  function renderExperimentTelemetryGuardrails(guardrails) {
    if (!experimentTelemetryGuardrailState || !experimentTelemetryGuardrailAlerts) return;
    if (!guardrails || typeof guardrails !== 'object') {
      experimentTelemetryGuardrailState.classList.add('d-none');
      experimentTelemetryGuardrailState.textContent = '';
      experimentTelemetryGuardrailAlerts.classList.add('d-none');
      experimentTelemetryGuardrailAlerts.innerHTML = '';
      return;
    }
    const status = String(guardrails?.status || 'ok').toLowerCase();
    const rates = guardrails?.rates || {};
    const alerts = Array.isArray(guardrails?.alerts) ? guardrails.alerts : [];

    experimentTelemetryGuardrailState.classList.remove('d-none', 'alert-success', 'alert-warning');
    experimentTelemetryGuardrailAlerts.classList.add('d-none');
    experimentTelemetryGuardrailAlerts.innerHTML = '';

    const summary = `SLO: render_error ${formatGuardrailPercent(rates.render_error)} / fallback ${formatGuardrailPercent(rates.fallback)} / abandon ${formatGuardrailPercent(rates.abandon)} / slow_open ${formatGuardrailPercent(rates.slow_open)}.`;
    if (status === 'attention') {
      experimentTelemetryGuardrailState.classList.add('alert-warning');
      experimentTelemetryGuardrailState.textContent = `Найдены отклонения guardrails. ${summary}`;
      if (alerts.length) {
        experimentTelemetryGuardrailAlerts.classList.remove('d-none');
        experimentTelemetryGuardrailAlerts.innerHTML = alerts.map((alert) => {
          const message = String(alert?.message || 'Отклонение метрики');
          const value = formatGuardrailPercent(alert?.value);
          const threshold = formatGuardrailPercent(alert?.threshold);
          const scope = String(alert?.scope || '').trim();
          const segment = String(alert?.segment || '').trim();
          const events = Number(alert?.events || 0);
          const previousValue = Number(alert?.previous_value);
          const delta = Number(alert?.delta);
          const scopeMeta = scope && segment
            ? ` · срез: ${scope}=${segment}${events > 0 ? ` · событий: ${events}` : ''}`
            : '';
          const previousMeta = Number.isFinite(previousValue)
            ? ` · предыдущее окно: ${escapeHtml(formatGuardrailPercent(previousValue))}`
            : '';
          const deltaMeta = Number.isFinite(delta)
            ? ` · Δ: ${escapeHtml(formatDeltaPercent(delta))}`
            : '';
          return `<li>${escapeHtml(message)} (факт: ${escapeHtml(value)} · порог: ${escapeHtml(threshold)}${previousMeta}${deltaMeta}${escapeHtml(scopeMeta)})</li>`;
        }).join('');
      }
      return;
    }

    experimentTelemetryGuardrailState.classList.add('alert-success');
    experimentTelemetryGuardrailState.textContent = `Guardrails в норме. ${summary}`;
  }

  function formatRolloutDecisionAction(action) {
    const safe = String(action || 'hold').trim().toLowerCase();
    if (safe === 'scale_up') return 'scale_up';
    if (safe === 'rollback') return 'rollback';
    return 'hold';
  }

  function formatKpiOutcomeDelta(metricName, value) {
    const safe = Number(value);
    if (!Number.isFinite(safe)) return '—';
    const isPercentMetric = String(metricName || '').toLowerCase() === 'sla_breach';
    if (isPercentMetric) {
      return formatDeltaPercent(safe);
    }
    const sign = safe > 0 ? '+' : '';
    return `${sign}${Math.round(safe)}мс`;
  }


  function renderExperimentRolloutPacket(packet) {
    if (!experimentRolloutPacketState || !experimentRolloutPacketChecklist || !experimentRolloutPacketWrap || !experimentRolloutPacketRows) return;
    const safePacket = packet && typeof packet === 'object' ? packet : null;
    if (!safePacket) {
      experimentRolloutPacketState.classList.add('d-none');
      experimentRolloutPacketState.textContent = '';
      experimentRolloutPacketChecklist.classList.add('d-none');
      experimentRolloutPacketChecklist.innerHTML = '';
      experimentRolloutPacketWrap.classList.add('d-none');
      experimentRolloutPacketRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Governance packet появится после первых telemetry-сигналов.</td></tr>';
      return;
    }

    const status = String(safePacket?.status || 'attention').trim().toLowerCase();
    const required = Boolean(safePacket?.required);
    const packetReady = Boolean(safePacket?.packet_ready);
    const summary = String(safePacket?.summary || '').trim() || 'Governance packet загружен.';
    const decisionAction = String(safePacket?.decision_action || 'hold').trim().toUpperCase();
    const generatedAt = formatTimestamp(safePacket?.generated_at, { includeTime: true, fallback: '—' });
    const blockingCount = Math.max(0, Number(safePacket?.blocking_count || 0));
    const attentionCount = Math.max(0, Number(safePacket?.attention_count || 0));
    const invalidUtcItems = Array.isArray(safePacket?.invalid_utc_items) ? safePacket.invalid_utc_items : [];
    const missingItems = Array.isArray(safePacket?.missing_items) ? safePacket.missing_items : [];
    const legacyOnlyScenarios = Array.isArray(safePacket?.legacy_only_scenarios) ? safePacket.legacy_only_scenarios : [];
    const legacyInventory = safePacket?.legacy_only_inventory && typeof safePacket.legacy_only_inventory === 'object'
      ? safePacket.legacy_only_inventory
      : {};
    const contextContract = safePacket?.context_contract && typeof safePacket.context_contract === 'object'
      ? safePacket.context_contract
      : {};
    const nextReviewAt = formatTimestamp(safePacket?.next_review_at_utc, { includeTime: true, fallback: '' });

    experimentRolloutPacketState.classList.remove('d-none', 'alert-success', 'alert-warning', 'alert-danger', 'alert-secondary');
    if (status === 'ok') {
      experimentRolloutPacketState.classList.add('alert-success');
    } else if (status === 'hold') {
      experimentRolloutPacketState.classList.add('alert-danger');
    } else if (status === 'off') {
      experimentRolloutPacketState.classList.add('alert-secondary');
    } else {
      experimentRolloutPacketState.classList.add('alert-warning');
    }
    experimentRolloutPacketState.textContent = `Governance packet: ${status.toUpperCase()} · decision ${decisionAction} · blocking ${blockingCount} · attention ${attentionCount} · generated ${generatedAt}. ${summary}`;
    experimentRolloutPacketState.classList.remove('d-none');

    const checks = [
      { ok: packetReady, label: required ? 'Полный governance packet собран' : 'Governance packet не блокирует rollout' },
      { ok: missingItems.length === 0, label: missingItems.length ? `Пропущенные элементы: ${missingItems.join(', ')}` : 'Нет пропущенных элементов пакета' },
      { ok: invalidUtcItems.length === 0, label: invalidUtcItems.length ? `UTC-ошибки: ${invalidUtcItems.join(', ')}` : 'UTC-метки governance валидны' },
      { ok: legacyOnlyScenarios.length === 0, label: legacyOnlyScenarios.length ? `Legacy-only inventory открыт: ${legacyOnlyScenarios.join(', ')}` : 'Legacy-only inventory пуст' },
    ];
    if (legacyOnlyScenarios.length) {
      const legacyActionItems = Array.isArray(legacyInventory?.action_items) ? legacyInventory.action_items : [];
      const overdue = Number(legacyInventory?.deadline_overdue_count || 0);
      const reviewQueueScenarios = Array.isArray(legacyInventory?.review_queue_scenarios) ? legacyInventory.review_queue_scenarios : [];
      const overdueScenarios = Array.isArray(legacyInventory?.overdue_scenarios) ? legacyInventory.overdue_scenarios : [];
      checks.push({
        ok: overdue === 0,
        label: overdue > 0
          ? `Sunset commitments overdue: ${overdue}${legacyActionItems.length ? ` · ${legacyActionItems[0]}` : ''}`
          : `Owner/deadline coverage ${Number(legacyInventory?.owner_coverage_pct || 0)}% / ${Number(legacyInventory?.deadline_coverage_pct || 0)}%`
      });
      if (reviewQueueScenarios.length) {
        checks.push({
          ok: legacyInventory?.review_queue_followup_required !== true,
          label: legacyInventory?.review_queue_summary
            ? String(legacyInventory.review_queue_summary)
            : `Повторно остаются в legacy review-queue: ${reviewQueueScenarios.slice(0, 3).join(', ')}${reviewQueueScenarios.length > 3 ? ` +${reviewQueueScenarios.length - 3}` : ''}`
        });
      }
      if (legacyInventory?.review_queue_escalation_required === true) {
        const escalatedScenarios = Array.isArray(legacyInventory?.review_queue_escalated_scenarios) ? legacyInventory.review_queue_escalated_scenarios : [];
        checks.push({
          ok: false,
          label: `Legacy queue escalation (${String(legacyInventory?.review_queue_closure_pressure || 'high')}${Number(legacyInventory?.review_queue_escalated_count || 0) > 0 ? `, mgmt ${Number(legacyInventory?.review_queue_escalated_count || 0)}` : ''}): ${escalatedScenarios.length ? escalatedScenarios.join(', ') : 'management review required'}`
        });
      }
      if (Number(legacyInventory?.review_queue_consolidation_count || 0) > 0) {
        const consolidationCandidates = Array.isArray(legacyInventory?.review_queue_consolidation_candidates) ? legacyInventory.review_queue_consolidation_candidates : [];
        checks.push({
          ok: false,
          label: `Legacy queue consolidation: ${consolidationCandidates.length ? consolidationCandidates.join(', ') : `${Number(legacyInventory?.review_queue_consolidation_count || 0)} сценария(ев)`}`
        });
      }
      if (overdueScenarios.length) {
        checks.push({
          ok: false,
          label: `Overdue scenarios: ${overdueScenarios.join(', ')}`
        });
      }
      if (legacyInventory?.repeat_review_required === true) {
        checks.push({
          ok: false,
          label: `Повторный legacy review обязателен (${String(legacyInventory?.repeat_review_reason || 'review_due')})${legacyInventory?.repeat_review_due_at_utc ? ` · due ${formatTimestamp(legacyInventory.repeat_review_due_at_utc, { includeTime: true, fallback: '—' })}` : ''}`
        });
      }
    }
    if (contextContract?.enabled === true) {
      const contextActionItems = Array.isArray(contextContract?.action_items) ? contextContract.action_items : [];
      const focusBlocks = Array.isArray(contextContract?.operator_focus_blocks) ? contextContract.operator_focus_blocks : [];
      const operatorSummary = String(contextContract?.operator_summary || '').trim();
      const nextStepSummary = String(contextContract?.next_step_summary || '').trim();
      checks.push({
        ok: contextContract?.ready === true,
        label: contextContract?.ready === true
          ? `Context contract ready${focusBlocks.length ? ` · operator first: ${focusBlocks.join(', ')}` : ''}`
          : (nextStepSummary || operatorSummary || contextActionItems[0] || 'Context contract требует action-oriented follow-up')
      });
      if (contextContract?.extra_attributes_compaction_candidate === true) {
        checks.push({
          ok: contextContract?.secondary_noise_management_review_required !== true,
          label: String(contextContract?.secondary_noise_compaction_summary || contextContract?.extra_attributes_summary || 'Extra attributes требуют compaction review.')
        });
      }
    }
    if (nextReviewAt) {
      checks.push({ ok: true, label: `Следующий review due UTC: ${nextReviewAt}` });
    }
    experimentRolloutPacketChecklist.classList.remove('d-none');
    experimentRolloutPacketChecklist.innerHTML = checks.map((item) => (`<li>${item.ok ? '✅' : '⚠️'} ${escapeHtml(item.label)}</li>`)).join('');

    const items = Array.isArray(safePacket?.items) ? safePacket.items : [];
    if (!items.length) {
      experimentRolloutPacketWrap.classList.add('d-none');
      experimentRolloutPacketRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Governance packet появится после первых telemetry-сигналов.</td></tr>';
      return;
    }

    experimentRolloutPacketWrap.classList.remove('d-none');
    experimentRolloutPacketRows.innerHTML = items.map((item) => {
      const itemStatus = String(item?.status || 'attention').trim().toLowerCase();
      const badge = itemStatus === 'ok'
        ? '<span class="badge text-bg-success">ok</span>'
        : (itemStatus === 'attention'
          ? '<span class="badge text-bg-warning">attention</span>'
          : (itemStatus === 'off'
            ? '<span class="badge text-bg-secondary">off</span>'
            : '<span class="badge text-bg-danger">hold</span>'));
      const note = String(item?.note || item?.summary || '').trim();
      return `
        <tr>
          <td>
            <div>${escapeHtml(String(item?.label || '—'))}</div>
            ${note ? `<div class="small text-muted">${escapeHtml(note)}</div>` : ''}
          </td>
          <td>${escapeHtml(String(item?.category || '—'))}</td>
          <td>${escapeHtml(String(item?.current_value || '—'))}</td>
          <td>${escapeHtml(String(item?.threshold || '—'))}</td>
          <td>${escapeHtml(formatTimestamp(item?.measured_at, { includeTime: true, fallback: '—' }))}</td>
          <td class="text-end">${badge}</td>
        </tr>
      `;
    }).join('');
  }

  function renderExperimentGapBreakdown(breakdown) {
    if (!experimentGapBreakdownWrap || !experimentGapBreakdownRows) return;
    const safeBreakdown = breakdown && typeof breakdown === 'object' ? breakdown : null;
    const categoryLabels = {
      profile: 'profile',
      source: 'source',
      attribute_policy: 'attribute_policy',
      block: 'block',
      contract: 'contract',
      parity: 'parity',
    };
    const rows = [];
    Object.entries(categoryLabels).forEach(([key, label]) => {
      const items = Array.isArray(safeBreakdown?.[key]) ? safeBreakdown[key] : [];
      items.forEach((item) => rows.push({ category: label, ...item }));
    });
    if (!rows.length) {
      experimentGapBreakdownWrap.classList.add('d-none');
      experimentGapBreakdownRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Gap breakdown появится после первых parity/context-gap событий.</td></tr>';
      return;
    }
    experimentGapBreakdownWrap.classList.remove('d-none');
    experimentGapBreakdownRows.innerHTML = rows.map((item) => `
      <tr>
        <td>${escapeHtml(String(item?.category || 'unknown'))}</td>
        <td>${escapeHtml(String(item?.reason || 'unspecified'))}</td>
        <td class="text-end">${escapeHtml(String(Number(item?.events || 0)))}</td>
        <td class="text-end">${escapeHtml(String(Number(item?.tickets || 0)))}</td>
        <td>${escapeHtml(formatTimestamp(item?.last_seen_at, { includeTime: true, fallback: '—' }))}</td>
      </tr>
    `).join('');
  }

  function renderExperimentRolloutScorecard(scorecard) {
    if (!experimentRolloutScorecardWrap || !experimentRolloutScorecardRows) return;
    const items = Array.isArray(scorecard?.items) ? scorecard.items : [];
    if (!items.length) {
      experimentRolloutScorecardWrap.classList.add('d-none');
      experimentRolloutScorecardRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Rollout scorecard появится после первых telemetry-сигналов.</td></tr>';
      return;
    }

    experimentRolloutScorecardWrap.classList.remove('d-none');
    experimentRolloutScorecardRows.innerHTML = items.map((item) => {
      const status = String(item?.status || 'hold').trim().toLowerCase();
      const badge = status === 'ok'
        ? '<span class="badge text-bg-success">ok</span>'
        : (status === 'attention'
          ? '<span class="badge text-bg-warning">attention</span>'
          : (status === 'off'
            ? '<span class="badge text-bg-secondary">off</span>'
            : '<span class="badge text-bg-danger">hold</span>'));
      const note = String(item?.note || item?.summary || '').trim();
      return `
        <tr>
          <td>
            <div>${escapeHtml(String(item?.label || '—'))}</div>
            ${note ? `<div class="small text-muted">${escapeHtml(note)}</div>` : ''}
          </td>
          <td>${escapeHtml(String(item?.category || '—'))}</td>
          <td>${escapeHtml(String(item?.current_value || '—'))}</td>
          <td>${escapeHtml(String(item?.threshold || '—'))}</td>
          <td>${escapeHtml(formatTimestamp(item?.measured_at, { includeTime: true, fallback: '—' }))}</td>
          <td class="text-end">${badge}</td>
        </tr>
      `;
    }).join('');
  }

  function renderExperimentRolloutDecision(decision, cohortComparison) {
    if (!experimentRolloutDecisionState || !experimentRolloutDecisionChecklist || !experimentRolloutKpiOutcomesWrap || !experimentRolloutKpiOutcomeRows) return;
    const safeDecision = decision && typeof decision === 'object' ? decision : null;
    const safeComparison = cohortComparison && typeof cohortComparison === 'object' ? cohortComparison : null;
    if (!safeDecision) {
      experimentRolloutDecisionState.classList.add('d-none');
      experimentRolloutDecisionState.textContent = '';
      experimentRolloutDecisionChecklist.classList.add('d-none');
      experimentRolloutDecisionChecklist.innerHTML = '';
      experimentRolloutKpiOutcomesWrap.classList.add('d-none');
      experimentRolloutKpiOutcomeRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Данные появятся после первых KPI-сигналов.</td></tr>';
      return;
    }

    const action = formatRolloutDecisionAction(safeDecision?.action);
    const winner = String(safeDecision?.winner || 'insufficient_data');
    const rationale = String(safeDecision?.rationale || 'Решение будет доступно после накопления данных.');

    experimentRolloutDecisionState.classList.remove('d-none', 'alert-success', 'alert-warning', 'alert-danger');
    if (action === 'scale_up') {
      experimentRolloutDecisionState.classList.add('alert-success');
    } else if (action === 'rollback') {
      experimentRolloutDecisionState.classList.add('alert-danger');
    } else {
      experimentRolloutDecisionState.classList.add('alert-warning');
    }
    experimentRolloutDecisionState.textContent = `Rollout decision: ${action.toUpperCase()} · winner: ${winner}. ${rationale}`;

    const checks = [
      { ok: Boolean(safeDecision?.sample_size_ok), label: 'Достаточная выборка control/test' },
      { ok: Boolean(safeDecision?.kpi_signal_ready), label: 'Покрытие KPI-сигналов (FRT/TTR/SLA breach)' },
      { ok: Boolean(safeDecision?.kpi_outcome_ready), label: 'Готовность KPI-результатов к сравнению' },
      { ok: !Boolean(safeDecision?.kpi_outcome_regressions), label: 'Нет деградации product KPI в test cohort' },
    ];
    experimentRolloutDecisionChecklist.classList.remove('d-none');
    experimentRolloutDecisionChecklist.innerHTML = checks.map((item) => (`<li>${item.ok ? '✅' : '⚠️'} ${escapeHtml(item.label)}</li>`)).join('');

    const outcomeMetrics = safeComparison?.kpi_outcome_signal?.metrics;
    const metricEntries = outcomeMetrics && typeof outcomeMetrics === 'object' ? Object.entries(outcomeMetrics) : [];
    if (!metricEntries.length) {
      experimentRolloutKpiOutcomesWrap.classList.add('d-none');
      experimentRolloutKpiOutcomeRows.innerHTML = '<tr><td colspan="5" class="small text-muted">Данные появятся после первых KPI-сигналов.</td></tr>';
      return;
    }
    experimentRolloutKpiOutcomesWrap.classList.remove('d-none');
    experimentRolloutKpiOutcomeRows.innerHTML = metricEntries.map(([metricName, metricPayload]) => {
      const metric = metricPayload && typeof metricPayload === 'object' ? metricPayload : {};
      const ready = Boolean(metric?.ready);
      const regression = Boolean(metric?.regression);
      const status = !ready ? 'waiting' : (regression ? 'regression' : 'ok');
      const statusBadge = status === 'ok'
        ? '<span class="badge text-bg-success">ok</span>'
        : (status === 'regression'
          ? '<span class="badge text-bg-danger">regression</span>'
          : '<span class="badge text-bg-secondary">waiting</span>');
      const controlValue = Number(metric?.control_value);
      const testValue = Number(metric?.test_value);
      const controlDisplay = Number.isFinite(controlValue)
        ? (String(metricName).toLowerCase() === 'sla_breach' ? formatGuardrailPercent(controlValue) : `${Math.round(controlValue)}мс`)
        : '—';
      const testDisplay = Number.isFinite(testValue)
        ? (String(metricName).toLowerCase() === 'sla_breach' ? formatGuardrailPercent(testValue) : `${Math.round(testValue)}мс`)
        : '—';
      return `
        <tr>
          <td>${escapeHtml(String(metricName))}</td>
          <td class="text-end">${escapeHtml(controlDisplay)}</td>
          <td class="text-end">${escapeHtml(testDisplay)}</td>
          <td class="text-end">${escapeHtml(formatKpiOutcomeDelta(metricName, metric?.delta))}</td>
          <td class="text-end">${statusBadge}</td>
        </tr>
      `;
    }).join('');
  }

  function renderExperimentTelemetrySummaryRows(rows) {
    if (!experimentTelemetrySummaryRows) return;
    const safeRows = Array.isArray(rows) ? rows : [];
    if (!safeRows.length) {
      experimentTelemetrySummaryRows.innerHTML = '<tr><td colspan="6" class="small text-muted">Недостаточно telemetry-данных для расчёта.</td></tr>';
      return;
    }
    experimentTelemetrySummaryRows.innerHTML = safeRows.map((row) => {
      const avgOpenMs = Number.isFinite(Number(row?.avg_open_ms)) ? Math.round(Number(row.avg_open_ms)) : '—';
      return `
        <tr>
          <td>${escapeHtml(String(row?.experiment_cohort || 'unknown'))}</td>
          <td>${escapeHtml(String(row?.operator_segment || 'unknown'))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.events || 0)))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.fallbacks || 0)))}</td>
          <td class="text-end">${escapeHtml(String(Number(row?.render_errors || 0)))}</td>
          <td class="text-end">${escapeHtml(String(avgOpenMs))}</td>
        </tr>
      `;
    }).join('');
  }

  function renderExperimentTelemetryDimensionRows(container, rows, dimensionKey) {
    if (!container) return;
    const safeRows = Array.isArray(rows) ? rows : [];
    if (!safeRows.length) {
      container.innerHTML = '<tr><td colspan="4" class="small text-muted">Недостаточно telemetry-данных для расчёта.</td></tr>';
      return;
    }
    container.innerHTML = safeRows.map((row) => {
      const events = Math.max(0, Number(row?.events || 0));
      const fallbackRate = events > 0 ? Number(row?.fallbacks || 0) / events : 0;
      const renderErrorRate = events > 0 ? Number(row?.render_errors || 0) / events : 0;
      const dimension = String(row?.[dimensionKey] || 'unknown');
      return `
        <tr>
          <td>${escapeHtml(dimension)}</td>
          <td class="text-end">${escapeHtml(String(events))}</td>
          <td class="text-end">${escapeHtml(formatGuardrailPercent(fallbackRate))}</td>
          <td class="text-end">${escapeHtml(formatGuardrailPercent(renderErrorRate))}</td>
        </tr>
      `;
    }).join('');
  }

  function clearExperimentTelemetryRefreshTimer() {
    if (!experimentTelemetryRefreshTimer) return;
    window.clearInterval(experimentTelemetryRefreshTimer);
    experimentTelemetryRefreshTimer = null;
  }

  function syncExperimentTelemetryAutoRefresh() {
    clearExperimentTelemetryRefreshTimer();
    if (!experimentTelemetryAutoRefresh || !experimentTelemetryAutoRefresh.checked) {
      return;
    }
    experimentTelemetryRefreshTimer = window.setInterval(() => {
      loadExperimentTelemetrySummary();
    }, 30000);
  }

  async function loadExperimentTelemetrySummary() {
    if (!experimentTelemetrySummaryRows) return;
    if (experimentTelemetrySummaryState) {
      experimentTelemetrySummaryState.textContent = 'Загрузка агрегатов telemetry…';
    }
    try {
      const response = await fetch(`/api/dialogs/workspace-telemetry/summary?days=7&experiment_name=${encodeURIComponent(WORKSPACE_AB_TEST_CONFIG.experimentName)}`);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }
      const payload = await response.json();
      renderExperimentTelemetrySummaryRows(payload?.rows || []);
      renderExperimentTelemetryDimensionRows(experimentTelemetryShiftRows, payload?.by_shift || [], 'shift');
      renderExperimentTelemetryDimensionRows(experimentTelemetryTeamRows, payload?.by_team || [], 'team');
      renderExperimentTelemetryGuardrails(payload?.guardrails || {});
      renderExperimentRolloutPacket(payload?.rollout_packet || {});
      renderExperimentRolloutScorecard(payload?.rollout_scorecard || {});
      renderExperimentRolloutDecision(payload?.rollout_decision || {}, payload?.cohort_comparison || {});
      renderExperimentGapBreakdown(payload?.gap_breakdown || {});
      if (experimentTelemetrySummaryState) {
        const totals = payload?.totals || {};
        const previousTotals = payload?.previous_totals || {};
        const comparison = payload?.period_comparison || {};
        const p1Control = payload?.p1_operational_control && typeof payload.p1_operational_control === 'object'
          ? payload.p1_operational_control
          : {};
        const p2Control = payload?.p2_governance_control && typeof payload.p2_governance_control === 'object'
          ? payload.p2_governance_control
          : {};
        const slaAudit = payload?.sla_policy_audit && typeof payload.sla_policy_audit === 'object'
          ? payload.sla_policy_audit
          : {};
        const slaReviewPathControl = payload?.sla_review_path_control && typeof payload.sla_review_path_control === 'object'
          ? payload.sla_review_path_control
          : {};
        const weeklyReviewFocus = payload?.weekly_review_focus && typeof payload.weekly_review_focus === 'object'
          ? payload.weekly_review_focus
          : {};
        const weeklyFocusSections = Array.isArray(weeklyReviewFocus?.sections) ? weeklyReviewFocus.sections : [];
        const avgCurrent = Number.isFinite(Number(totals.avg_open_ms)) ? `${Math.round(Number(totals.avg_open_ms))}мс` : '—';
        const avgPrevious = Number.isFinite(Number(previousTotals.avg_open_ms)) ? `${Math.round(Number(previousTotals.avg_open_ms))}мс` : '—';
        const generatedAt = formatWorkspaceDateTime(payload?.generated_at);
        experimentTelemetrySummaryState.textContent = `Событий: ${Number(totals.events || 0)} (пред. окно: ${Number(previousTotals.events || 0)}) · Fallback: ${Number(totals.fallbacks || 0)} · Manual legacy: ${Number(totals.manual_legacy_open_events || 0)} · Legacy blocked: ${Number(totals.workspace_open_legacy_blocked_events || 0)} · Inline nav: ${Number(totals.workspace_inline_navigation_events || 0)} · Rollout packet views: ${Number(totals.workspace_rollout_packet_viewed_events || 0)} · Secondary context opens: ${Number(totals.context_secondary_details_expanded_events || 0)} (${Number(totals.context_secondary_details_open_rate_pct || 0)}%, ${String(totals.context_secondary_details_usage_level || 'rare')}${totals.context_secondary_details_top_section ? `, top ${String(totals.context_secondary_details_top_section)}` : ''})${Number(totals.context_extra_attributes_expanded_events || 0) > 0 ? ` · Extra attrs: ${Number(totals.context_extra_attributes_expanded_events || 0)} (${Number(totals.context_extra_attributes_open_rate_pct || 0)}%, ${String(totals.context_extra_attributes_usage_level || 'rare')}${totals.context_extra_attributes_compaction_candidate === true ? `, compact, share ${Number(totals.context_extra_attributes_share_pct_of_secondary || 0)}%` : ''})` : ''} · P1 control: ${String(p1Control?.status || 'controlled')}${p1Control?.context_noise_trend_status ? ` (${String(p1Control.context_noise_trend_status)})` : ''}${p1Control?.next_action_summary ? ` · next ${String(p1Control.next_action_summary)}` : ''} · P2 control: ${String(p2Control?.status || 'controlled')}${p2Control?.sla_churn_trend_status ? ` (${String(p2Control.sla_churn_trend_status)})` : ''}${p2Control?.governance_closure_health ? ` · closure ${String(p2Control.governance_closure_health)}` : ''}${p2Control?.macro_noise_health ? ` · macro-noise ${String(p2Control.macro_noise_health)}` : ''}${p2Control?.next_action_summary ? ` · next ${String(p2Control.next_action_summary)}` : ''} · Source policy gaps: ${Number(totals.context_attribute_policy_gap_events || 0)} · Context contract gaps: ${Number(totals.context_contract_gap_events || 0)} · SLA policy gaps: ${Number(totals.workspace_sla_policy_gap_events || 0)} · SLA churn: ${Number(totals.workspace_sla_policy_churn_ratio_pct || 0)}% (${String(totals.workspace_sla_policy_churn_level || 'controlled')}) · SLA path: ${String(slaReviewPathControl?.status || 'controlled')}${slaReviewPathControl?.decision_lead_time_status ? ` (${String(slaReviewPathControl.decision_lead_time_status)})` : ''}${slaReviewPathControl?.minimum_required_review_path_summary ? ` · ${String(slaReviewPathControl.minimum_required_review_path_summary)}` : ''}${slaReviewPathControl?.cheap_review_path_confirmed === true ? ' · cheap path confirmed' : ''}${slaReviewPathControl?.next_action_summary ? ` · next ${String(slaReviewPathControl.next_action_summary)}` : ''}${slaAudit?.cheap_review_path_confirmed === true ? ' · SLA cheap path confirmed' : ''}${slaAudit?.decision_lead_time_status ? ` · SLA lead ${String(slaAudit.decision_lead_time_status)}` : ''} · Macro policy updates: ${Number(totals.workspace_macro_policy_update_events || 0)}${p2Control?.macro_low_signal_backlog_dominant === true ? ` · macro low-signal ${Number(p2Control.macro_low_signal_advisory_share_pct || 0)}%` : ''} · Weekly focus: ${weeklyFocusSections.length ? weeklyFocusSections.map((item) => String(item?.key || '')).filter(Boolean).join(', ') : 'none'}${weeklyReviewFocus?.top_priority_key ? ` (top ${String(weeklyReviewFocus.top_priority_key)})` : ''}${weeklyReviewFocus?.focus_health ? `, health ${String(weeklyReviewFocus.focus_health)}` : ''}${weeklyReviewFocus?.priority_mix_summary ? ` · ${String(weeklyReviewFocus.priority_mix_summary)}` : ''}${weeklyReviewFocus?.next_action_summary ? ` · next: ${String(weeklyReviewFocus.next_action_summary)}` : ''}${weeklyReviewFocus?.requires_management_review === true ? ' · management review suggested' : ''} · Parity gaps: ${Number(totals.workspace_parity_gap_events || 0)} · Render error: ${Number(totals.render_errors || 0)} · Avg open: ${avgCurrent} (было ${avgPrevious}, Δ ${formatDeltaMs(comparison.avg_open_ms_delta)}) · Обновлено: ${generatedAt}.`;
      }
    } catch (_error) {
      renderExperimentTelemetrySummaryRows([]);
      renderExperimentTelemetryDimensionRows(experimentTelemetryShiftRows, [], 'shift');
      renderExperimentTelemetryDimensionRows(experimentTelemetryTeamRows, [], 'team');
      renderExperimentTelemetryGuardrails(null);
      renderExperimentRolloutPacket(null);
      renderExperimentRolloutScorecard(null);
      renderExperimentRolloutDecision(null, null);
      renderExperimentGapBreakdown(null);
      if (experimentTelemetrySummaryState) {
        experimentTelemetrySummaryState.textContent = 'Не удалось загрузить telemetry-агрегаты. Проверьте API /api/dialogs/workspace-telemetry/summary.';
      }
    }
  }

  function formatWorkspaceDateTime(value) {
    return formatTimestamp(value, { includeTime: true, fallback: '—' });
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

  function getStoredWorkspaceDraft(ticketId) {
    if (!ticketId) return '';
    try {
      return localStorage.getItem(getWorkspaceDraftStorageKey(ticketId)) || '';
    } catch (_error) {
      return '';
    }
  }

  function hasUnsavedWorkspaceComposerChanges(nextTicketId) {
    if (!workspaceComposerText || !workspaceComposerTicketId) return false;
    if (!workspaceComposerText.value.trim()) return false;
    if (String(nextTicketId || '').trim() === String(workspaceComposerTicketId || '').trim()) return false;
    const stored = getStoredWorkspaceDraft(workspaceComposerTicketId).trim();
    return workspaceComposerText.value.trim() !== stored;
  }

  function confirmWorkspaceTicketSwitch(nextTicketId) {
    if (!hasUnsavedWorkspaceComposerChanges(nextTicketId)) return true;
    const accepted = window.confirm('Есть несохранённые изменения в черновике. Сохранить текущий черновик перед переключением диалога?');
    if (accepted) {
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText?.value || '', { reason: 'manual' });
    }
    return accepted;
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
        workspaceDraftLastSavedValue = value;
        if (!options.silent) {
          updateWorkspaceDraftState(`Черновик сохранён · ${new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`);
        }
        const now = Date.now();
        const shouldSendDraftTelemetry = options.reason === 'manual'
          || (options.reason === 'autosave' && now - workspaceDraftLastTelemetryAt >= WORKSPACE_DRAFT_TELEMETRY_MIN_INTERVAL_MS);
        if (shouldSendDraftTelemetry) {
          workspaceDraftLastTelemetryAt = now;
          emitWorkspaceTelemetry('workspace_draft_saved', {
            ticketId,
            reason: options.reason || 'manual',
            length: value.length,
          });
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
      workspaceDraftLastSavedValue = workspaceComposerText.value.trim();
      if (stored) {
        updateWorkspaceDraftState('Черновик восстановлен');
        emitWorkspaceTelemetry('workspace_draft_restored', {
          ticketId,
          length: stored.length,
        });
      } else {
        updateWorkspaceDraftState('Черновик не сохранён');
      }
    } catch (_error) {
      workspaceComposerText.value = '';
      workspaceDraftLastSavedValue = '';
      updateWorkspaceDraftState('Не удалось загрузить черновик');
    }
  }

  function scheduleWorkspaceDraftAutosave() {
    if (!workspaceComposerTicketId || !workspaceComposerText) return;
    const value = workspaceComposerText.value.trim();
    if (workspaceDraftAutosaveTimer) {
      window.clearTimeout(workspaceDraftAutosaveTimer);
    }
    workspaceDraftAutosaveTimer = window.setTimeout(() => {
      if (value === workspaceDraftLastSavedValue) return;
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { reason: 'autosave' });
    }, WORKSPACE_DRAFT_AUTOSAVE_DELAY_MS);
  }

  function appendWorkspaceMessage(message) {
    if (!workspaceMessagesList) return;
    workspaceMessagesList.classList.remove('d-none');
    workspaceMessagesList.insertAdjacentHTML(
      'beforeend',
      renderWorkspaceMessageItem(message),
    );
    if (workspaceMessagesState) {
      workspaceMessagesState.classList.add('d-none');
      workspaceMessagesState.textContent = '';
    }
  }

  function buildMacroGovernanceMeta(template) {
    if (!template || typeof template !== 'object') return '';
    const parts = [];
    const namespace = String(template?.namespace || '').trim();
    const owner = String(template?.owner || '').trim();
    const reviewedAt = String(template?.reviewed_at || template?.reviewed_at_utc || '').trim();
    if (namespace) parts.push(`namespace=${namespace}`);
    if (owner) parts.push(`owner=${owner}`);
    if (reviewedAt) parts.push(`review UTC ${reviewedAt.replace('T', ' ').replace('Z', ' UTC')}`);
    return parts.join(' · ');
  }

  function renderWorkspaceMacroPreview(template) {
    activeWorkspaceMacroTemplate = template || null;
    if (!workspaceComposerMacroPreview) return;
    const text = resolveMacroText(template);
    workspaceComposerMacroPreview.textContent = text || 'Выберите макрос для предпросмотра.';
    if (workspaceComposerMacroMeta) {
      workspaceComposerMacroMeta.textContent = buildMacroGovernanceMeta(template);
    }
    if (workspaceComposerMacroApply) {
      workspaceComposerMacroApply.disabled = !text;
    }
  }

  function renderWorkspaceMacroOptions(templates) {
    if (!workspaceComposerMacroSelect) return;
    const nextTemplates = Array.isArray(templates) ? templates : [];
    workspaceComposerMacroSelect.innerHTML = '';
    nextTemplates.forEach((template, index) => {
      const option = document.createElement('option');
      option.value = template?.id
        ? `id:${template.id}`
        : `idx:${Number.isFinite(Number(template?.__macroIndex)) ? Number(template.__macroIndex) : index}`;
      option.textContent = template?.name || `Макрос ${index + 1}`;
      workspaceComposerMacroSelect.appendChild(option);
    });
    const selected = nextTemplates.length > 0
      ? findMacroTemplateByWorkspaceOptionValue(workspaceComposerMacroSelect.value, nextTemplates) || nextTemplates[0]
      : null;
    if (selected) {
      workspaceComposerMacroSelect.value = selected?.id
        ? `id:${selected.id}`
        : `idx:${Number.isFinite(Number(selected?.__macroIndex)) ? Number(selected.__macroIndex) : nextTemplates.indexOf(selected)}`;
    }
    workspaceComposerMacroSelect.disabled = nextTemplates.length === 0;
    renderWorkspaceMacroPreview(selected);
  }

  function findMacroTemplateByWorkspaceOptionValue(value, templatePool = workspaceComposerMacroTemplates) {
    const normalized = String(value || '').trim();
    if (!normalized) return null;
    if (normalized.startsWith('id:')) {
      const templateId = normalized.slice(3);
      return templatePool.find((template) => String(template?.id || '') === templateId) || null;
    }
    if (normalized.startsWith('idx:')) {
      const idx = Number.parseInt(normalized.slice(4), 10);
      if (!Number.isFinite(idx)) return null;
      return workspaceComposerMacroTemplates.find((template) => Number(template?.__macroIndex) === idx) || null;
    }
    return null;
  }

  async function applyWorkspaceMacroTemplate() {
    if (!workspaceComposerText || !activeWorkspaceMacroTemplate) return;
    const message = resolveMacroText(activeWorkspaceMacroTemplate);
    const row = workspaceComposerTicketId
      ? rowsList().find((candidate) => String(candidate.dataset.ticketId || '') === String(workspaceComposerTicketId)) || null
      : null;
    const appliedSteps = await executeMacroWorkflow(activeWorkspaceMacroTemplate, {
      ticketId: workspaceComposerTicketId,
      row,
    });
    if (message) {
      const existing = workspaceComposerText.value.trim();
      workspaceComposerText.value = existing ? `${existing}\n${message}` : message;
      workspaceComposerText.focus();
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { silent: true });
    }
    emitWorkspaceTelemetry('macro_apply', {
      ticketId: workspaceComposerTicketId,
      templateId: String(activeWorkspaceMacroTemplate?.id || '').trim() || null,
      templateName: String(activeWorkspaceMacroTemplate?.name || '').trim() || null,
      source: 'workspace_composer',
      workflowActions: appliedSteps.length ? appliedSteps.join('|') : null,
    });
    if (typeof showNotification === 'function' && (message || appliedSteps.length)) {
      const workflowSuffix = appliedSteps.length ? `; действия: ${appliedSteps.join(', ')}` : '';
      showNotification(`Макрос workspace применён${workflowSuffix}.`, 'success');
    }
  }

  async function sendWorkspaceReply() {
    if (!workspaceComposerText || !workspaceComposerSend || !workspaceComposerTicketId) return;
    const message = workspaceComposerText.value.trim();
    const replyToTelegramId = Number.isFinite(Number(activeWorkspaceReplyToTelegramId))
      ? Number(activeWorkspaceReplyToTelegramId)
      : null;
    const replyPreview = workspaceReplyTargetText ? workspaceReplyTargetText.textContent.trim() : '';
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
        body: JSON.stringify({ message, replyToTelegramId }),
      });
      const data = await resp.json();
      if (!resp.ok || !data?.success) {
        throw new Error(data?.error || `Ошибка ${resp.status}`);
      }
      workspaceComposerText.value = '';
      saveWorkspaceDraft(workspaceComposerTicketId, '');
      resetWorkspaceReplyTarget({ reason: 'message_sent' });
      appendWorkspaceMessage({
        senderName: data.responsible || 'Оператор',
        messageText: message,
        sentAt: data.timestamp || new Date().toISOString(),
        telegramMessageId: data.telegramMessageId || null,
        replyPreview,
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

  function updateWorkspaceMessagesLoadMoreState() {
    if (!workspaceMessagesLoadMoreWrap || !workspaceMessagesLoadMore) return;
    const canLoadMore = Boolean(activeWorkspaceTicketId) && workspaceMessagesHasMore;
    workspaceMessagesLoadMoreWrap.classList.toggle('d-none', !canLoadMore);
    workspaceMessagesLoadMore.disabled = workspaceMessagesLoadingMore || !canLoadMore;
    workspaceMessagesLoadMore.textContent = workspaceMessagesLoadingMore ? 'Загрузка…' : 'Загрузить ещё';
  }


  function resolveWorkspaceSlaBadgeClass(state) {
    const normalized = String(state || '').trim().toLowerCase();
    if (normalized === 'breached') return 'text-bg-danger';
    if (normalized === 'at_risk') return 'text-bg-warning';
    if (normalized === 'closed') return 'text-bg-secondary';
    return 'text-bg-success';
  }

  function formatWorkspaceSlaRemaining(minutesLeft) {
    const value = Number(minutesLeft);
    if (!Number.isFinite(value)) return '—';
    if (value === 0) return '0м';
    const absValue = Math.abs(Math.round(value));
    const hours = Math.floor(absValue / 60);
    const minutes = absValue % 60;
    const suffix = value < 0 ? 'назад' : 'осталось';
    if (hours > 0) {
      return `${hours}ч ${minutes}м ${suffix}`;
    }
    return `${minutes}м ${suffix}`;
  }

  function mergeWorkspacePayload(basePayload, partialPayload, include) {
    const includeSet = new Set(String(include || '').split(',').map((item) => item.trim()).filter(Boolean));
    if (!basePayload || typeof basePayload !== 'object') {
      return partialPayload;
    }
    if (!partialPayload || typeof partialPayload !== 'object') {
      return basePayload;
    }
    const merged = {
      ...basePayload,
      conversation: partialPayload.conversation || basePayload.conversation,
      composer: partialPayload.composer || basePayload.composer,
      meta: partialPayload.meta || basePayload.meta,
      success: partialPayload.success,
    };
    if (includeSet.has('messages')) {
      merged.messages = partialPayload.messages || basePayload.messages;
    }
    if (includeSet.has('context')) {
      merged.context = partialPayload.context || basePayload.context;
    }
    if (includeSet.has('sla')) {
      merged.sla = partialPayload.sla || basePayload.sla;
    }
    if (includeSet.has('permissions')) {
      merged.permissions = partialPayload.permissions || basePayload.permissions;
    }
    return merged;
  }

  function setWorkspaceSectionLoading(stateEl, errorEl, message) {
    if (errorEl) errorEl.classList.add('d-none');
    if (stateEl) {
      stateEl.classList.remove('d-none');
      stateEl.textContent = message;
    }
  }

  async function reloadWorkspaceSection(include, options = {}) {
    if (!WORKSPACE_V1_ENABLED || !activeWorkspaceTicketId) return;
    const channelId = activeWorkspaceChannelId;
    setWorkspaceSectionLoading(options.stateElement, options.errorElement, options.statusText || 'Повторная загрузка...');
    try {
      const partialPayload = await preloadWorkspaceContract(activeWorkspaceTicketId, channelId, { include });
      activeWorkspacePayload = mergeWorkspacePayload(activeWorkspacePayload, partialPayload, include);
      renderWorkspaceShell(activeWorkspacePayload);
    } catch (_error) {
      if (options.errorElement) options.errorElement.classList.remove('d-none');
      if (typeof showNotification === 'function') {
        showNotification(options.failMessage || 'Не удалось обновить секцию workspace.', 'warning');
      }
    }
  }

  async function loadMoreWorkspaceMessages() {
    if (!activeWorkspaceTicketId || !workspaceMessagesHasMore || workspaceMessagesLoadingMore) return;
    workspaceMessagesLoadingMore = true;
    updateWorkspaceMessagesLoadMoreState();
    try {
      const workspacePayload = await preloadWorkspaceContract(activeWorkspaceTicketId, activeWorkspaceChannelId, {
        include: 'messages',
        cursor: workspaceMessagesNextCursor,
        limit: WORKSPACE_MESSAGES_PAGE_LIMIT,
      });
      const messages = workspacePayload?.messages || {};
      const items = Array.isArray(messages.items) ? messages.items : [];
      if (workspaceMessagesList && items.length > 0) {
        const markup = items.map(renderWorkspaceMessageItem).join('');
        workspaceMessagesList.insertAdjacentHTML('beforeend', markup);
        workspaceMessagesList.classList.remove('d-none');
      }
      workspaceMessagesNextCursor = Number.isInteger(messages.next_cursor) ? messages.next_cursor : null;
      workspaceMessagesHasMore = messages.has_more === true;
      if (workspaceMessagesState && !workspaceMessagesHasMore) {
        workspaceMessagesState.classList.remove('d-none');
        workspaceMessagesState.textContent = 'Показаны все сообщения по диалогу.';
      }
    } catch (_error) {
      if (typeof showNotification === 'function') {
        showNotification('Не удалось догрузить сообщения workspace.', 'warning');
      }
    } finally {
      workspaceMessagesLoadingMore = false;
      updateWorkspaceMessagesLoadMoreState();
    }
  }

  function renderWorkspaceCategories() {
    if (!workspaceCategoriesList || !workspaceCategoriesState) return;
    const templateCategories = DIALOG_TEMPLATES.categoryTemplates
      .flatMap((template) => Array.isArray(template?.categories) ? template.categories : [])
      .map((item) => String(item || '').trim())
      .filter(Boolean);
    const ordered = Array.from(new Set([...templateCategories, ...Array.from(selectedCategories)]));
    if (ordered.length === 0) {
      workspaceCategoriesState.classList.remove('d-none');
      workspaceCategoriesState.textContent = 'Категории не настроены.';
      workspaceCategoriesList.classList.add('d-none');
      workspaceCategoriesList.innerHTML = '';
      return;
    }
    workspaceCategoriesState.classList.remove('d-none');
    workspaceCategoriesState.textContent = selectedCategories.size > 0
      ? `Выбрано: ${selectedCategories.size}. Изменения сохраняются автоматически.`
      : 'Выберите хотя бы одну категорию для закрытия диалога.';
    workspaceCategoriesList.classList.remove('d-none');
    workspaceCategoriesList.innerHTML = ordered.map((category) => {
      const selected = selectedCategories.has(category);
      return `<button class="badge rounded-pill text-bg-light border dialog-category-badge ${selected ? 'is-selected' : ''}" type="button" data-category-value="${escapeHtml(category)}">${escapeHtml(category)}</button>`;
    }).join('');
  }

  function updateWorkspaceActionButtons(conversation, permissions) {
    const statusRaw = conversation?.status || '';
    const statusKey = conversation?.statusKey || '';
    const statusLabel = conversation?.statusLabel || '';
    const resolved = isResolvedStatus(statusRaw, statusKey, statusLabel);
    const responsible = String(conversation?.responsible || '').trim();
    const canTakeOwnership = !isOwnedByCurrentOperator(responsible);
    const canAssign = permissions?.can_assign === true && !workspaceReadonlyMode;
    const canClose = permissions?.can_close === true && !workspaceReadonlyMode;
    const canSnooze = permissions?.can_snooze === true && !workspaceReadonlyMode;

    if (workspaceAssignBtn) {
      workspaceAssignBtn.disabled = !canAssign || !canTakeOwnership;
      workspaceAssignBtn.classList.toggle('d-none', !canAssign || !canTakeOwnership);
    }
    if (workspaceSnoozeBtn) {
      workspaceSnoozeBtn.disabled = !canSnooze || resolved;
      workspaceSnoozeBtn.classList.toggle('d-none', !canSnooze || resolved);
      workspaceSnoozeBtn.textContent = formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES);
    }
    if (workspaceResolveBtn) {
      workspaceResolveBtn.disabled = !canClose || resolved;
      workspaceResolveBtn.classList.toggle('d-none', !canClose || resolved);
    }
    if (workspaceReopenBtn) {
      workspaceReopenBtn.disabled = !canClose || !resolved;
      workspaceReopenBtn.classList.toggle('d-none', !resolved);
    }
    const rollout = payload?.meta?.rollout || {};
    renderWorkspaceRolloutBanner(rollout);
    if (workspaceLegacyBtn) {
      const fallbackAvailable = !WORKSPACE_SINGLE_MODE
        && (rollout?.legacy_fallback_available === true || (!payload?.meta?.rollout && !WORKSPACE_DISABLE_LEGACY_FALLBACK));
      workspaceLegacyBtn.disabled = !fallbackAvailable;
      workspaceLegacyBtn.classList.toggle('d-none', !fallbackAvailable);
    }
    if (workspaceCreateTaskBtn) {
      workspaceCreateTaskBtn.classList.remove('disabled');
    }
  }

  function emitWorkspaceProfileGapTelemetry(context, conversation) {
    const profileHealth = context?.profile_health;
    if (!profileHealth || profileHealth.enabled !== true) {
      workspaceLastProfileGapSignature = '';
      return;
    }
    const missingFields = Array.isArray(profileHealth.missing_fields) ? profileHealth.missing_fields.filter(Boolean) : [];
    if (profileHealth.ready === true || missingFields.length === 0) {
      workspaceLastProfileGapSignature = '';
      return;
    }
    const activeSegments = Array.isArray(profileHealth.active_segments)
      ? profileHealth.active_segments.filter(Boolean).map((item) => String(item).trim())
      : [];
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const signature = `${ticketId}:${activeSegments.join('|')}:${missingFields.join(',')}`;
    if (!ticketId || workspaceLastProfileGapSignature === signature) {
      return;
    }
    workspaceLastProfileGapSignature = signature;
    emitWorkspaceTelemetry('workspace_context_profile_gap', {
      ticketId,
      reason: [
        ...activeSegments.map((segment) => `segment:${segment}`),
        ...missingFields.map((field) => `field:${field}`),
      ].join(','),
      durationMs: missingFields.length,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceContextSourceGapTelemetry(context, conversation) {
    const contextSources = Array.isArray(context?.context_sources)
      ? context.context_sources
      : (Array.isArray(context?.client?.context_sources) ? context.client.context_sources : []);
    if (!contextSources.length) {
      workspaceLastContextSourceGapSignature = '';
      return;
    }
    const blockingSources = contextSources
      .filter((source) => source && source.required === true && source.ready !== true)
      .map((source) => `${String(source.key || '').trim()}:${String(source.status || 'unknown').trim()}`)
      .filter(Boolean);
    if (!blockingSources.length) {
      workspaceLastContextSourceGapSignature = '';
      return;
    }
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const signature = `${ticketId}:${blockingSources.join(',')}`;
    if (!ticketId || workspaceLastContextSourceGapSignature === signature) {
      return;
    }
    workspaceLastContextSourceGapSignature = signature;
    emitWorkspaceTelemetry('workspace_context_source_gap', {
      ticketId,
      reason: blockingSources.join(','),
      durationMs: blockingSources.length,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceContextAttributePolicyGapTelemetry(context, conversation) {
    const attributePolicies = Array.isArray(context?.attribute_policies)
      ? context.attribute_policies
      : (Array.isArray(context?.client?.attribute_policies) ? context.client.attribute_policies : []);
    if (!attributePolicies.length) {
      workspaceLastAttributePolicyGapSignature = '';
      return;
    }
    const blockingPolicies = attributePolicies
      .filter((item) => item && item.required === true && item.ready !== true)
      .map((item) => `field:${String(item.key || '').trim()}:${String(item.status || 'unknown').trim()}`)
      .filter(Boolean);
    if (!blockingPolicies.length) {
      workspaceLastAttributePolicyGapSignature = '';
      return;
    }
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const signature = `${ticketId}:${blockingPolicies.join(',')}`;
    if (!ticketId || workspaceLastAttributePolicyGapSignature === signature) {
      return;
    }
    workspaceLastAttributePolicyGapSignature = signature;
    emitWorkspaceTelemetry('workspace_context_attribute_policy_gap', {
      ticketId,
      reason: blockingPolicies.join(','),
      durationMs: blockingPolicies.length,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceContextBlockGapTelemetry(context, conversation) {
    const health = context?.blocks_health;
    if (!health || health.enabled !== true) {
      workspaceLastContextBlockGapSignature = '';
      return;
    }
    const missingBlocks = Array.isArray(health.missing_required_keys)
      ? health.missing_required_keys.filter(Boolean).map((item) => String(item).trim())
      : [];
    if (health.ready === true || missingBlocks.length === 0) {
      workspaceLastContextBlockGapSignature = '';
      return;
    }
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const signature = `${ticketId}:${missingBlocks.join(',')}`;
    if (!ticketId || workspaceLastContextBlockGapSignature === signature) {
      return;
    }
    workspaceLastContextBlockGapSignature = signature;
    emitWorkspaceTelemetry('workspace_context_block_gap', {
      ticketId,
      reason: missingBlocks.join(','),
      durationMs: missingBlocks.length,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceContextContractGapTelemetry(context, conversation) {
    const contract = context?.contract;
    if (!contract || contract.enabled !== true || contract.ready === true) {
      return;
    }
    const violationDetails = normalizeWorkspaceContextViolationDetails(contract?.violation_details);
    const violations = Array.isArray(contract.violations)
      ? contract.violations.filter(Boolean).map((item) => String(item).trim()).filter(Boolean)
      : [];
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    if (!ticketId) {
      return;
    }
    const telemetryReasons = violationDetails.length
      ? violationDetails.map((item) => item.analyticsMessage || item.code).filter(Boolean)
      : violations;
    emitWorkspaceTelemetry('workspace_context_contract_gap', {
      ticketId,
      reason: telemetryReasons.join(',') || 'contract_not_ready',
      durationMs: telemetryReasons.length,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceSlaPolicyGapTelemetry(sla, conversation) {
    const policy = sla?.policy;
    if (!policy || typeof policy !== 'object') {
      workspaceLastSlaPolicyGapSignature = '';
      return;
    }
    const status = String(policy.status || '').trim().toLowerCase();
    const issues = Array.isArray(policy.issues) ? policy.issues.filter(Boolean) : [];
    if (!['attention', 'invalid_utc'].includes(status)) {
      workspaceLastSlaPolicyGapSignature = '';
      return;
    }
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const reason = issues.join(',') || status || 'sla_policy_gap';
    const signature = `${ticketId}:${reason}`;
    if (!ticketId || workspaceLastSlaPolicyGapSignature === signature) {
      return;
    }
    workspaceLastSlaPolicyGapSignature = signature;
    emitWorkspaceTelemetry('workspace_sla_policy_gap', {
      ticketId,
      reason,
      durationMs: Number.isFinite(Number(sla?.minutes_left)) ? Number(sla.minutes_left) : 0,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function emitWorkspaceParityGapTelemetry(parity, conversation) {
    const safeParity = parity && typeof parity === 'object' ? parity : null;
    if (!safeParity || String(safeParity?.status || '').toLowerCase() === 'ok') {
      workspaceLastParityGapSignature = '';
      return;
    }
    const ticketId = String(conversation?.ticketId || activeWorkspaceTicketId || '').trim();
    const missingCapabilities = Array.isArray(safeParity?.missing_capabilities)
      ? safeParity.missing_capabilities.filter(Boolean).map((item) => String(item).trim())
      : [];
    const signature = `${ticketId}:${String(safeParity?.status || '').trim()}:${missingCapabilities.join(',')}:${Number(safeParity?.score_pct || 0)}`;
    if (!ticketId || workspaceLastParityGapSignature === signature) {
      return;
    }
    workspaceLastParityGapSignature = signature;
    emitWorkspaceTelemetry('workspace_parity_gap', {
      ticketId,
      reason: missingCapabilities.join(',') || String(safeParity?.status || 'parity_gap'),
      durationMs: Number.isFinite(Number(safeParity?.score_pct)) ? Math.max(0, Math.min(100, Number(safeParity.score_pct))) : null,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
  }

  function buildWorkspaceNavigationStateText(navigation) {
    const safeNavigation = navigation && typeof navigation === 'object' ? navigation : null;
    if (!safeNavigation) {
      return 'Очередь не определена';
    }
    if (typeof safeNavigation.summary === 'string' && safeNavigation.summary.trim()) {
      return safeNavigation.summary.trim();
    }
    const position = Number(safeNavigation.position || 0);
    const total = Number(safeNavigation.total || 0);
    if (position > 0 && total > 0) {
      return `Позиция ${position} из ${total}.`;
    }
    return safeNavigation.enabled === false
      ? 'Inline navigation отключена.'
      : 'Текущий диалог открыт вне активной очереди.';
  }

  function getWorkspaceNavigationTarget(direction, navigation) {
    const key = direction === 'previous' ? 'previous' : 'next';
    const candidate = navigation && typeof navigation === 'object' ? navigation[key] : null;
    const ticketId = String(candidate?.ticket_id || '').trim();
    if (!ticketId) {
      return null;
    }
    return {
      ticketId,
      channelId: candidate?.channel_id ?? null,
      clientName: String(candidate?.client_name || '').trim(),
      status: String(candidate?.status || '').trim(),
    };
  }

  function renderWorkspaceNavigation(navigation) {
    const previous = getWorkspaceNavigationTarget('previous', navigation);
    const next = getWorkspaceNavigationTarget('next', navigation);
    const navigationEnabled = WORKSPACE_INLINE_NAVIGATION && navigation?.enabled !== false;

    if (workspaceNavPrevBtn) {
      workspaceNavPrevBtn.disabled = !navigationEnabled || !previous;
      workspaceNavPrevBtn.title = previous
        ? `${previous.ticketId}${previous.clientName ? ` · ${previous.clientName}` : ''}${previous.status ? ` · ${previous.status}` : ''}`
        : 'Предыдущий диалог недоступен';
    }
    if (workspaceNavNextBtn) {
      workspaceNavNextBtn.disabled = !navigationEnabled || !next;
      workspaceNavNextBtn.title = next
        ? `${next.ticketId}${next.clientName ? ` · ${next.clientName}` : ''}${next.status ? ` · ${next.status}` : ''}`
        : 'Следующий диалог недоступен';
    }
    if (workspaceNavState) {
      workspaceNavState.textContent = buildWorkspaceNavigationStateText(navigation);
    }
  }

  async function navigateWorkspaceInline(direction) {
    if (!WORKSPACE_INLINE_NAVIGATION) {
      return;
    }
    const safeDirection = direction === 'previous' ? 'previous' : 'next';
    const navigation = activeWorkspacePayload?.meta?.navigation || null;
    const target = getWorkspaceNavigationTarget(safeDirection, navigation);
    if (!target?.ticketId) {
      openVisibleDialogByOffset(safeDirection === 'previous' ? -1 : 1);
      return;
    }
    const row = rowsList().find((item) => String(item.dataset.ticketId || '') === target.ticketId) || null;
    setActiveDialogRow(row, { ensureVisible: true });
    await emitWorkspaceTelemetry('workspace_inline_navigation', {
      ticketId: target.ticketId,
      reason: safeDirection,
      durationMs: Number.isFinite(Number(navigation?.position)) ? Number(navigation.position) : null,
      contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
    });
    await openDialogWithWorkspaceFallback(target.ticketId, row, {
      source: `inline_navigation_${safeDirection}`,
      channelId: target.channelId,
    });
  }

  async function refreshActiveWorkspaceContract(options = {}) {
    if (!activeWorkspaceTicketId) return;
    const payload = await preloadWorkspaceContract(activeWorkspaceTicketId, activeWorkspaceChannelId, { limit: WORKSPACE_MESSAGES_PAGE_LIMIT });
    activeWorkspacePayload = payload;
    renderWorkspaceShell(payload);
    if (!options.silent && typeof showNotification === 'function' && options.successMessage) {
      showNotification(options.successMessage, 'success');
    }
  }

  function appendToWorkspaceComposer(text) {
    if (!workspaceComposerText || !text) return;
    const value = String(workspaceComposerText.value || '');
    const addition = String(text).trim();
    if (!addition) return;
    workspaceComposerText.value = value ? `${value}\n\n${addition}` : addition;
    workspaceComposerText.focus();
  }

  async function loadWorkspaceAiSuggestions(ticketId) {
    const normalizedTicketId = String(ticketId || '').trim();
    if (!workspaceAiSuggestionsSection || !workspaceAiSuggestionsState || !workspaceAiSuggestionsList) return;
    if (!normalizedTicketId) {
      workspaceAiSuggestionsState.textContent = 'Open a dialog to load AI suggestions.';
      workspaceAiSuggestionsList.classList.add('d-none');
      workspaceAiSuggestionsList.innerHTML = '';
      return;
    }
    workspaceAiSuggestionsState.textContent = 'Loading AI suggestions...';
    workspaceAiSuggestionsList.classList.add('d-none');
    workspaceAiSuggestionsList.innerHTML = '';
    try {
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-suggestions?limit=3`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || (`HTTP ${resp.status}`));
      }
      const items = Array.isArray(payload.items) ? payload.items : [];
      if (!items.length) {
        workspaceAiSuggestionsState.textContent = payload.processing
          ? 'AI is processing this dialog, suggestions are not ready yet.'
          : 'No suggestions yet. Reply manually or refresh later.';
        return;
      }
      workspaceAiSuggestionsState.textContent = payload.processing
        ? 'Dialog is in automatic processing mode. Suggestions:'
        : 'Suggestions ready:';
      workspaceAiSuggestionsList.innerHTML = items.map((item, index) => {
        const scoreLabel = String(item?.score_label || item?.score || '').trim();
        const source = String(item?.source || '').trim();
        const title = escapeHtml(String(item?.title || `Suggestion ${index + 1}`));
        const snippet = escapeHtml(String(item?.snippet || ''));
        const reply = escapeHtml(String(item?.reply || item?.snippet || ''));
        const explain = escapeHtml(String(item?.explain || '').trim());
        const sourceBadge = `${escapeHtml(source || 'source')}${scoreLabel ? ` � ${escapeHtml(scoreLabel)}` : ''}`;
        const rawSource = escapeHtml(String(item?.source || '').trim());
        const rawTitle = escapeHtml(String(item?.title || '').trim());
        const rawSnippet = escapeHtml(String(item?.snippet || '').trim());
        return `
          <article class="border rounded p-2">
            <div class="d-flex justify-content-between align-items-start gap-2">
              <div class="fw-semibold">${title}</div>
              <span class="badge text-bg-light border">${sourceBadge}</span>
            </div>
            ${snippet ? `<div class="small text-muted mt-1">${snippet}</div>` : ''}
            ${explain ? `<div class="small mt-1"><strong>Why selected:</strong> ${explain}</div>` : ''}
            <div class="mt-2">
              <div class="btn-group btn-group-sm" role="group">
                <button type="button" class="btn btn-outline-primary" data-ai-suggestion-apply="${index}" data-ai-suggestion-reply="${reply}">Insert as-is</button>
                <button type="button" class="btn btn-outline-secondary" data-ai-suggestion-apply-edit="${index}" data-ai-suggestion-reply="${reply}">Insert and edit</button>
                <button type="button" class="btn btn-outline-danger" data-ai-suggestion-reject="${index}" data-ai-suggestion-source="${rawSource}" data-ai-suggestion-title="${rawTitle}" data-ai-suggestion-snippet="${rawSnippet}" data-ai-suggestion-reply="${reply}">Reject</button>
              </div>
            </div>
          </article>
        `;
      }).join('');
      workspaceAiSuggestionsList.classList.remove('d-none');
    } catch (error) {
      workspaceAiSuggestionsState.textContent = `Failed to load suggestions: ${error.message || 'unknown_error'}`;
    }
  }

  async function sendAiSuggestionFeedback(ticketId, payload) {
    const normalizedTicketId = String(ticketId || '').trim();
    if (!normalizedTicketId) return false;
    const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-suggestions/feedback`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload || {}),
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok || data.success === false) {
      throw new Error(data.error || `HTTP ${resp.status}`);
    }
    return true;
  }

  async function loadWorkspaceAiControl(ticketId) {
    const normalizedTicketId = String(ticketId || '').trim();
    if (!workspaceAiControlState || !workspaceAiDisableForDialog || !workspaceAiHandoffNoAutoReply) return;
    if (!normalizedTicketId) {
      workspaceAiControlState.textContent = 'Open a dialog to manage AI.';
      workspaceAiDisableForDialog.disabled = true;
      workspaceAiHandoffNoAutoReply.disabled = true;
      return;
    }
    workspaceAiControlState.textContent = 'Loading AI dialog controls...';
    workspaceAiDisableForDialog.disabled = true;
    workspaceAiHandoffNoAutoReply.disabled = true;
    try {
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-control`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || `HTTP ${resp.status}`);
      }
      const control = payload.control || {};
      const disabled = control.ai_disabled === true;
      const blocked = control.auto_reply_blocked === true;
      workspaceAiDisableForDialog.textContent = disabled ? 'Enable AI for dialog' : 'Disable AI for dialog';
      workspaceAiControlState.textContent = disabled
        ? 'AI is fully disabled for this dialog.'
        : (blocked ? 'AI auto-reply is disabled for this dialog. Suggestions remain available.' : 'AI runs in standard mode.');
      workspaceAiDisableForDialog.disabled = false;
      workspaceAiHandoffNoAutoReply.disabled = false;
    } catch (error) {
      workspaceAiControlState.textContent = `Failed to load AI control state: ${error.message || 'unknown_error'}`;
    }
  }

  async function updateWorkspaceAiControl(ticketId, controlPayload, successMessage) {
    const normalizedTicketId = String(ticketId || '').trim();
    if (!normalizedTicketId) return;
    const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-control`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(controlPayload || {}),
    });
    const payload = await resp.json().catch(() => ({}));
    if (!resp.ok || payload.success === false) {
      throw new Error(payload.error || `HTTP ${resp.status}`);
    }
    if (typeof showNotification === 'function' && successMessage) {
      showNotification(successMessage, 'success');
    }
    await loadWorkspaceAiControl(normalizedTicketId);
  }
  function renderAiReviewQueueRows(items) {
    if (!aiReviewQueueBody) return;
    if (!Array.isArray(items) || items.length === 0) {
      aiReviewQueueBody.innerHTML = '<tr><td colspan="5" class="text-muted text-center py-3">Очередь ревизий пуста.</td></tr>';
      return;
    }
    aiReviewQueueBody.innerHTML = items.map((item) => {
      const queryKey = String(item?.query_key || '').trim();
      const ticketId = String(item?.last_ticket_id || '').trim();
      const question = escapeHtml(String(item?.query_text || '').trim() || '—');
      const current = escapeHtml(String(item?.solution_text || '').trim() || '—');
      const pending = escapeHtml(String(item?.pending_solution_text || '').trim() || '—');
      const dialogLabel = ticketId ? `#${escapeHtml(ticketId)}` : '—';
      return `
        <tr data-ai-review-query-key="${escapeHtml(queryKey)}" data-ai-review-ticket-id="${escapeHtml(ticketId)}">
          <td class="small">${question}</td>
          <td class="small text-muted">${current}</td>
          <td class="small">${pending}</td>
          <td>${dialogLabel}</td>
          <td class="text-end">
            <div class="btn-group btn-group-sm" role="group">
              <button class="btn btn-outline-primary" type="button" data-ai-review-open ${ticketId ? '' : 'disabled'}>Открыть</button>
              <button class="btn btn-success" type="button" data-ai-review-approve>Принять</button>
              <button class="btn btn-outline-secondary" type="button" data-ai-review-reject>Отклонить</button>
            </div>
          </td>
        </tr>
      `;
    }).join('');
  }

  async function loadAiReviewQueue() {
    if (!aiReviewQueueSection || !aiReviewQueueState || !aiReviewQueueBody) return;
    aiReviewQueueState.textContent = 'Загрузка очереди ревизий…';
    try {
      const resp = await fetch('/api/dialogs/ai-reviews?limit=30', {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || (`HTTP ${resp.status}`));
      }
      const items = Array.isArray(payload.items) ? payload.items : [];
      aiReviewQueueState.textContent = items.length
        ? `Найдено ревизий: ${items.length}`
        : 'Очередь ревизий пуста.';
      renderAiReviewQueueRows(items);
    } catch (error) {
      aiReviewQueueState.textContent = `Не удалось загрузить очередь ревизий: ${error.message || 'unknown_error'}`;
      aiReviewQueueBody.innerHTML = '<tr><td colspan="5" class="text-danger text-center py-3">Ошибка загрузки очереди ревизий.</td></tr>';
    }
  }

  function formatRatePercent(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return '�';
    return `${(numeric * 100).toFixed(1)}%`;
  }

  function renderAiMonitoringAlerts(alerts) {
    if (!aiMonitoringAlerts) return;
    if (!Array.isArray(alerts) || alerts.length === 0) {
      aiMonitoringAlerts.innerHTML = '<div class="text-muted">������� ���.</div>';
      return;
    }
    aiMonitoringAlerts.innerHTML = alerts.map((alert) => {
      const severity = String(alert?.severity || 'info').trim().toLowerCase();
      const cls = severity === 'warning'
        ? 'alert alert-warning py-2 px-3 mb-2'
        : (severity === 'ok' ? 'alert alert-success py-2 px-3 mb-2' : 'alert alert-info py-2 px-3 mb-2');
      const message = escapeHtml(String(alert?.message || 'AI alert'));
      const value = formatRatePercent(alert?.value);
      const threshold = formatRatePercent(alert?.threshold);
      return `<div class="${cls}"><div>${message}</div><div class="small text-muted">value: ${value} � threshold: ${threshold}</div></div>`;
    }).join('');
  }

  function renderAiMonitoringRunbook(items) {
    if (!aiMonitoringRunbook) return;
    if (!Array.isArray(items) || !items.length) {
      aiMonitoringRunbook.innerHTML = '<li>Runbook ����������.</li>';
      return;
    }
    aiMonitoringRunbook.innerHTML = items.map((item) => `<li>${escapeHtml(String(item || ''))}</li>`).join('');
  }

  function renderAiMonitoringEvents(items) {
    if (!aiMonitoringEvents) return;
    if (!Array.isArray(items) || !items.length) {
      aiMonitoringEvents.innerHTML = '<div class="text-muted">������� ���.</div>';
      return;
    }
    aiMonitoringEvents.innerHTML = items.slice(0, 12).map((item) => {
      const createdAt = formatUtcDate(item?.created_at, { includeTime: true });
      const eventType = escapeHtml(String(item?.event_type || 'event'));
      const ticketId = escapeHtml(String(item?.ticket_id || '-'));
      const reason = escapeHtml(String(item?.decision_reason || item?.detail || ''));
      const source = escapeHtml(String(item?.source || ''));
      const actor = escapeHtml(String(item?.actor || 'system'));
      return `<div class="border rounded p-2 mb-1">
        <div class="d-flex flex-wrap justify-content-between gap-2">
          <span class="fw-semibold">${eventType}</span>
          <span class="text-muted">${createdAt}</span>
        </div>
        <div class="text-muted">ticket: ${ticketId} | actor: ${actor}${source ? ` | source: ${source}` : ''}</div>
        ${reason ? `<div>${reason}</div>` : ''}
      </div>`;
    }).join('');
  }

  function buildAiMonitoringEventsQuery(days = 7, limit = 50) {
    const params = new URLSearchParams();
    params.set('days', String(days));
    params.set('limit', String(limit));
    if (aiMonitoringFilters.eventType) params.set('eventType', aiMonitoringFilters.eventType);
    if (aiMonitoringFilters.actor) params.set('actor', aiMonitoringFilters.actor);
    return params.toString();
  }

  async function loadAiMonitoringEvents(days = 7, limit = 50) {
    if (!aiMonitoringEvents) return;
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/events?${buildAiMonitoringEventsQuery(days, limit)}`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || `HTTP ${resp.status}`);
      }
      renderAiMonitoringEvents(Array.isArray(payload.items) ? payload.items : []);
    } catch (_error) {
      renderAiMonitoringEvents([]);
    }
  }

  function exportAiMonitoringEventsCsv(days = 7, limit = 200) {
    const query = buildAiMonitoringEventsQuery(days, limit);
    const href = `/api/dialogs/ai-monitoring/events?${query}&format=csv`;
    const link = document.createElement('a');
    link.href = href;
    link.download = `ai-monitoring-events-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  async function loadAiMonitoringSummary(days = 7) {
    if (!aiMonitoringSection || !aiMonitoringState) return;
    aiMonitoringState.textContent = '�������� AI-������';
    try {
      const resp = await fetch(`/api/dialogs/ai-monitoring/summary?days=${encodeURIComponent(days)}`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) {
        throw new Error(payload.error || `HTTP ${resp.status}`);
      }
      const summary = payload.summary || {};
      const kpis = summary.kpis || {};
      if (aiKpiAutoReplyRate) aiKpiAutoReplyRate.textContent = formatRatePercent(kpis.auto_reply_rate);
      if (aiKpiAssistRate) aiKpiAssistRate.textContent = formatRatePercent(kpis.assist_usage_rate);
      if (aiKpiEscalationRate) aiKpiEscalationRate.textContent = formatRatePercent(kpis.escalation_rate);
      if (aiKpiCorrectionRate) aiKpiCorrectionRate.textContent = formatRatePercent(kpis.correction_rate);
      renderAiMonitoringAlerts(summary.alerts);
      renderAiMonitoringRunbook(summary.runbook?.items);
      await loadAiMonitoringEvents(days, 50);
      const windowDays = Number(summary.window_days || days);
      aiMonitoringState.textContent = `����: ${Number.isFinite(windowDays) ? windowDays : days} �� � ��������� ${formatUtcDate(summary.generated_at, { includeTime: true })}`;
    } catch (error) {
      aiMonitoringState.textContent = `�� ������� ��������� AI-�������: ${error.message || 'unknown_error'}`;
      renderAiMonitoringAlerts([]);
      renderAiMonitoringRunbook([]);
      renderAiMonitoringEvents([]);
    }
  }

  async function loadWorkspaceAiReview(ticketId) {
    const normalizedTicketId = String(ticketId || '').trim();
    if (!workspaceAiReviewBox) return;
    workspaceAiReviewBox.classList.add('d-none');
    if (!normalizedTicketId) return;
    try {
      const resp = await fetch(`/api/dialogs/${encodeURIComponent(normalizedTicketId)}/ai-review`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await resp.json().catch(() => ({}));
      if (!resp.ok || payload.success === false) return;
      const review = payload.review || {};
      if (review.pending !== true) return;
      if (workspaceAiReviewQuestion) workspaceAiReviewQuestion.textContent = String(review.query_text || '').trim();
      if (workspaceAiReviewCurrent) workspaceAiReviewCurrent.textContent = String(review.current_solution || '').trim();
      if (workspaceAiReviewPending) workspaceAiReviewPending.textContent = String(review.pending_solution || '').trim();
      workspaceAiReviewBox.classList.remove('d-none');
    } catch (_error) {
      // silent by design
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
    const composer = payload?.composer || {};
    const parity = payload?.meta?.parity || null;
    const navigation = payload?.meta?.navigation || null;

    const readonlyReason = resolveWorkspaceReadonlyReason(permissions);
    setWorkspaceReadonlyMode(Boolean(readonlyReason), readonlyReason);
    selectedCategories = new Set(normalizeCategories(conversation.categoriesSafe || conversation.categories || activeDialogRow?.dataset?.categories || ''));
    resetWorkspaceReplyTarget({ emitTelemetry: false });
    workspaceComposerTicketId = String(conversation.ticketId || '').trim();
    restoreWorkspaceDraft(workspaceComposerTicketId);
    loadWorkspaceAiSuggestions(workspaceComposerTicketId);
    loadWorkspaceAiReview(workspaceComposerTicketId);
    loadWorkspaceAiControl(workspaceComposerTicketId);
    const canReplyInWorkspace = permissions && permissions.can_reply === true && !workspaceReadonlyMode;
    if (workspaceComposerText) workspaceComposerText.disabled = !canReplyInWorkspace;
    if (workspaceComposerSend) workspaceComposerSend.disabled = !canReplyInWorkspace;
    if (workspaceComposerMediaTrigger) workspaceComposerMediaTrigger.disabled = !canReplyInWorkspace || composer.media_supported === false;
    if (workspaceComposerSaveDraft) workspaceComposerSaveDraft.disabled = !canReplyInWorkspace;
    if (workspaceComposerMacroApply) workspaceComposerMacroApply.disabled = !canReplyInWorkspace || !activeWorkspaceMacroTemplate;
    if (workspaceComposerMacroSelect) workspaceComposerMacroSelect.disabled = !canReplyInWorkspace || workspaceComposerMacroTemplates.length === 0;
    if (workspaceReplyTargetClear) workspaceReplyTargetClear.disabled = !canReplyInWorkspace || composer.reply_target_supported === false;

    if (workspaceConversationTitle) {
      workspaceConversationTitle.textContent = `Диалог #${conversation.ticketId || '—'}`;
    }
    if (workspaceConversationMeta) {
      const status = conversation.statusLabel || conversation.status || '—';
      const assignee = conversation.responsible || 'без ответственного';
      const createdAt = formatWorkspaceDateTime(conversation.createdAt || conversation.created_at);
      workspaceConversationMeta.textContent = `Статус: ${status} · Ответственный: ${assignee} · Создан: ${createdAt}`;
    }
    renderWorkspaceNavigation(navigation);
    updateWorkspaceActionButtons(conversation, permissions || {});
    renderWorkspaceParityBanner(parity);

    if (workspaceMessagesState) {
      workspaceMessagesState.classList.toggle('d-none', Array.isArray(messages.items) && messages.items.length > 0);
      workspaceMessagesState.textContent = Array.isArray(messages.items) && messages.items.length > 0
        ? ''
        : 'Сообщения отсутствуют или ещё не загружены.';
    }
    if (workspaceMessagesList) {
      const items = Array.isArray(messages.items) ? messages.items : [];
      workspaceMessagesList.classList.toggle('d-none', items.length === 0);
      workspaceMessagesList.innerHTML = items.map(renderWorkspaceMessageItem).join('');
    }
    workspaceMessagesNextCursor = Number.isInteger(messages.next_cursor) ? messages.next_cursor : null;
    workspaceMessagesHasMore = messages.has_more === true;
    workspaceMessagesLoadingMore = false;
    updateWorkspaceMessagesLoadMoreState();
    if (workspaceMessagesError) {
      workspaceMessagesError.classList.add('d-none');
    }

    const client = context.client;
    if (client && Object.keys(client).length) {
      if (workspaceClientState) workspaceClientState.classList.add('d-none');
      if (workspaceClientError) workspaceClientError.classList.add('d-none');
      if (workspaceClientContent) {
        workspaceClientContent.classList.remove('d-none');
        workspaceClientContent.innerHTML = renderWorkspaceClientProfile(client, context);
        bindWorkspaceContextDisclosureTelemetry(workspaceClientContent);
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

    renderWorkspaceCategories();
    if (workspaceCategoriesError) {
      workspaceCategoriesError.classList.add('d-none');
    }
    emitWorkspaceProfileGapTelemetry(context, conversation);
    emitWorkspaceContextSourceGapTelemetry(context, conversation);
    emitWorkspaceContextAttributePolicyGapTelemetry(context, conversation);
    emitWorkspaceContextBlockGapTelemetry(context, conversation);
    emitWorkspaceContextContractGapTelemetry(context, conversation);
    emitWorkspaceSlaPolicyGapTelemetry(sla, conversation);
    emitWorkspaceParityGapTelemetry(parity, conversation);

    if (sla && sla.state && sla.state !== 'unknown') {
      if (workspaceSlaState) workspaceSlaState.classList.add('d-none');
      if (workspaceSlaError) workspaceSlaError.classList.add('d-none');
      if (workspaceSlaContent) {
        const badgeClass = resolveWorkspaceSlaBadgeClass(sla.state);
        const remaining = formatWorkspaceSlaRemaining(sla.minutes_left);
        const policy = sla.policy && typeof sla.policy === 'object' ? sla.policy : null;
        const policyIssues = Array.isArray(policy?.issues) ? policy.issues.filter(Boolean) : [];
        const policyBadgeClass = (() => {
          switch (String(policy?.status || '').toLowerCase()) {
            case 'ready':
              return 'text-bg-success';
            case 'disabled':
              return 'text-bg-secondary';
            case 'invalid_utc':
            case 'attention':
              return 'text-bg-warning';
            default:
              return 'text-bg-light border';
          }
        })();
        const policyMeta = [];
        if (policy?.mode) policyMeta.push(`mode: ${String(policy.mode).toLowerCase()}`);
        if (policy?.route) policyMeta.push(`route: ${String(policy.route)}`);
        if (policy?.recommended_assignee) policyMeta.push(`owner: ${String(policy.recommended_assignee)}`);
        if (policy?.evaluated_at_utc) policyMeta.push(`UTC ${formatWorkspaceDateTime(policy.evaluated_at_utc)}`);
        const policyMarkup = policy
          ? `<div class="border rounded px-2 py-2 mt-2 bg-light-subtle">
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="fw-semibold small">SLA policy</span>
                <span class="badge ${policyBadgeClass}">${escapeHtml(String(policy.status || 'unknown'))}</span>
                ${policy.action ? `<span class="badge text-bg-light border">${escapeHtml(String(policy.action))}</span>` : ''}
              </div>
              ${policyMeta.length ? `<div class="small text-muted mt-1">${escapeHtml(policyMeta.join(' · '))}</div>` : ''}
              ${policy.summary ? `<div class="small text-muted mt-1">${escapeHtml(String(policy.summary))}</div>` : ''}
              ${policyIssues.length ? `<div class="small text-warning mt-1">Issues: ${escapeHtml(policyIssues.join(', '))}</div>` : ''}
            </div>`
          : '';
        const escalationHint = sla.escalation_required === true
          ? '<div class="small text-danger mt-1">Требуется эскалация: окно SLA критичное.</div>'
          : '';
        workspaceSlaContent.classList.remove('d-none');
        workspaceSlaContent.innerHTML = `<div class="small">Состояние: <span class="badge ${badgeClass}">${escapeHtml(sla.state)}</span></div><div class="small text-muted">До дедлайна: ${escapeHtml(remaining)}</div><div class="small text-muted">Дедлайн: ${escapeHtml(formatWorkspaceDateTime(sla.deadline_at))}</div>${escalationHint}${policyMarkup}`;
      }
    } else {
      if (workspaceSlaState) workspaceSlaState.classList.add('d-none');
      if (workspaceSlaContent) workspaceSlaContent.classList.add('d-none');
      if (workspaceSlaError) workspaceSlaError.classList.remove('d-none');
    }
  }

  function normalizeWorkspaceContextViolationDetails(details) {
    if (!Array.isArray(details)) {
      return [];
    }
    return details
      .filter((item) => item && typeof item === 'object')
      .map((item) => {
        const playbook = item.playbook && typeof item.playbook === 'object' ? item.playbook : null;
        const playbookUrl = String(playbook?.url || '').trim();
        return {
          type: String(item.type || '').trim(),
          code: String(item.code || '').trim(),
          severity: String(item.severity || '').trim().toLowerCase(),
          severityRank: Number(item.severity_rank || 0),
          shortLabel: String(item.short_label || item.operator_message || item.analytics_message || item.code || '').trim(),
          operatorMessage: String(item.operator_message || item.analytics_message || item.code || '').trim(),
          nextStep: String(item.next_step || '').trim(),
          actionLabel: String(item.action_label || '').trim(),
          analyticsMessage: String(item.analytics_message || '').trim(),
          playbookLabel: String(playbook?.label || 'Playbook').trim() || 'Playbook',
          playbookUrl: /^https?:\/\//i.test(playbookUrl) ? playbookUrl : '',
          playbookSummary: String(playbook?.summary || '').trim(),
        };
      })
      .sort((left, right) => {
        const severityDelta = Number(right.severityRank || 0) - Number(left.severityRank || 0);
        if (severityDelta !== 0) {
          return severityDelta;
        }
        return String(left.shortLabel || '').localeCompare(String(right.shortLabel || ''), 'ru');
      })
      .filter((item) => item.operatorMessage);
  }

  function workspaceContextViolationTypeLabel(type) {
    switch (String(type || '').trim().toLowerCase()) {
      case 'mandatory_field':
        return 'Mandatory field';
      case 'source_of_truth':
        return 'Source of truth';
      case 'priority_block':
        return 'Priority block';
      default:
        return 'Context gap';
    }
  }

  function workspaceContextViolationSeverityLabel(severity) {
    switch (String(severity || '').trim().toLowerCase()) {
      case 'high':
        return 'Срочно';
      case 'medium':
        return 'Нужно действие';
      default:
        return 'К сведению';
    }
  }

  function workspaceContextViolationSeverityBadge(severity) {
    switch (String(severity || '').trim().toLowerCase()) {
      case 'high':
        return 'text-bg-danger';
      case 'medium':
        return 'text-bg-warning';
      default:
        return 'text-bg-secondary';
    }
  }

  function renderWorkspaceClientProfile(client, context = {}) {
    if (!client || typeof client !== 'object') {
      return '<div class="small text-muted">Профиль клиента недоступен.</div>';
    }
    const fields = [
      ['ID', client.id],
      ['Username', client.username],
      ['Статус', client.status],
      ['Канал', client.channel],
      ['Бизнес', client.business],
      ['Локация', client.location],
      ['Ответственный', client.responsible],
      ['Непрочитанные', client.unread_count],
      ['Оценка', client.rating],
      ['Последнее сообщение', formatWorkspaceDateTime(client.last_message_at)],
      ['Всего диалогов', client.total_dialogs],
      ['Открытых диалогов', client.open_dialogs],
      ['Закрыто за 30 дней', client.resolved_30d],
      ['Первое обращение', formatWorkspaceDateTime(client.first_seen_at)],
      ['Последняя активность тикета', formatWorkspaceDateTime(client.last_ticket_activity_at)],
      ['Язык', client.language],
    ];
    const reservedClientKeys = new Set([
      'id',
      'name',
      'username',
      'status',
      'channel',
      'business',
      'location',
      'responsible',
      'unread_count',
      'rating',
      'last_message_at',
      'total_dialogs',
      'open_dialogs',
      'resolved_30d',
      'first_seen_at',
      'last_ticket_activity_at',
      'language',
      'segments',
      'context_sources',
      'context_contract',
      'external_links',
      'attribute_labels',
      'attribute_order',
    ]);
    const rows = fields
      .filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== '')
      .map(([label, value]) => `<div class="small text-muted">${escapeHtml(label)}: <span class="text-body">${escapeHtml(String(value))}</span></div>`)
      .join('');

    const profileHealth = client.profile_health && typeof client.profile_health === 'object' ? client.profile_health : null;
    const missingFields = Array.isArray(profileHealth?.missing_field_labels)
      ? profileHealth.missing_field_labels.filter(Boolean)
      : [];
    const activeProfileSegments = Array.isArray(profileHealth?.active_segments)
      ? profileHealth.active_segments.filter(Boolean)
      : [];
    const totalRequiredProfileFields = Array.isArray(profileHealth?.required_fields)
      ? profileHealth.required_fields.filter(Boolean).length
      : 0;
    const profileRuleSummary = [];
    if (totalRequiredProfileFields > 0) {
      profileRuleSummary.push(`Обязательных полей: ${totalRequiredProfileFields}`);
    }
    if (activeProfileSegments.length) {
      profileRuleSummary.push(`Сегменты: ${activeProfileSegments.join(', ')}`);
    }
    const healthBanner = profileHealth && profileHealth.enabled === true
      ? `<div class="alert ${profileHealth.ready ? 'alert-success' : 'alert-warning'} py-2 px-3 small mb-2">${profileHealth.ready ? `Контекст клиента готов (${Number(profileHealth.coverage_pct || 100)}%).` : `Нужно дозаполнить контекст (${Number(profileHealth.coverage_pct || 0)}%): ${escapeHtml(missingFields.join(', ') || 'нет обязательных полей')}.`}${profileRuleSummary.length ? `<div class="text-muted mt-1">${escapeHtml(profileRuleSummary.join(' · '))}</div>` : ''}<div class="text-muted mt-1">Проверено: ${escapeHtml(formatWorkspaceDateTime(profileHealth.checked_at_utc || profileHealth.checked_at))}</div></div>`
      : '';

    const contextBlocks = Array.isArray(context?.blocks)
      ? context.blocks.filter((item) => item && typeof item === 'object')
      : [];
    const contextBlocksHealth = context?.blocks_health && typeof context.blocks_health === 'object'
      ? context.blocks_health
      : null;
    const contextBlockBadgeClass = (status) => {
      switch (String(status || '').toLowerCase()) {
        case 'ready':
          return 'text-bg-success';
        case 'attention':
        case 'stale':
          return 'text-bg-warning';
        case 'missing':
        case 'invalid_utc':
        case 'unavailable':
          return 'text-bg-danger';
        default:
          return 'text-bg-secondary';
      }
    };
    const contextBlocksSection = contextBlocksHealth && contextBlocksHealth.enabled === true && contextBlocks.length
      ? `<div class="alert ${contextBlocksHealth.ready ? 'alert-success' : 'alert-warning'} py-2 px-3 small mb-2">
          <div class="fw-semibold mb-1">Приоритет customer context</div>
          <div>${contextBlocksHealth.ready
            ? `Все обязательные блоки готовы (${Number(contextBlocksHealth.coverage_pct || 100)}%).`
            : `Покрытие обязательных блоков: ${Number(contextBlocksHealth.coverage_pct || 0)}%. Не хватает: ${escapeHtml((Array.isArray(contextBlocksHealth.missing_required_labels) ? contextBlocksHealth.missing_required_labels : []).join(', ') || '—')}.`}</div>
          <div class="d-flex flex-column gap-2 mt-2">
            ${contextBlocks.map((block) => {
              const meta = [];
              if (Number.isFinite(Number(block.priority))) {
                meta.push(`P${Number(block.priority)}`);
              }
              if (block.required === true) {
                meta.push('required');
              }
              if (block.updated_at_utc) {
                meta.push(`UTC ${formatWorkspaceDateTime(block.updated_at_utc)}`);
              }
              return `<div class="border rounded px-2 py-1 bg-white">
                <div class="d-flex flex-wrap align-items-center gap-2">
                  <span class="fw-semibold">${escapeHtml(block.label || block.key || 'Блок')}</span>
                  <span class="badge ${contextBlockBadgeClass(block.status)}">${escapeHtml(String(block.status || 'unavailable'))}</span>
                </div>
                ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
                ${block.summary ? `<div class="small text-muted">${escapeHtml(String(block.summary))}</div>` : ''}
              </div>`;
            }).join('')}
          </div>
        </div>`
      : '';

    const extraAttributeLabelMap = resolveWorkspaceClientAttributeLabelMap(client.attribute_labels);
    const extraAttributeOrder = resolveWorkspaceClientAttributeOrder(client.attribute_order);
    const orderedExtraEntries = orderWorkspaceClientExtraEntries(
      Object.entries(client)
        .filter(([key, value]) => !reservedClientKeys.has(key) && isWorkspaceClientExtraValue(value)),
      extraAttributeOrder,
    );
    const filteredExtraEntries = orderedExtraEntries
      .filter(([key]) => {
        const normalized = normalizeWorkspaceClientAttributeKey(key);
        if (!normalized) {
          return false;
        }
        if (WORKSPACE_CLIENT_HIDDEN_ATTRIBUTES.has(normalized)) {
          return false;
        }
        if (!WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_HIDE_TECHNICAL) {
          return true;
        }
        return !WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_TECHNICAL_PREFIXES
          .some((prefix) => prefix && normalized.startsWith(prefix));
      });
    const limitedExtraEntries = filteredExtraEntries.slice(0, WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_MAX);
    const hiddenByLimitCount = Math.max(0, filteredExtraEntries.length - limitedExtraEntries.length);
    const extraAttributeTotalCount = filteredExtraEntries.length;
    const expandedExtraEntries = limitedExtraEntries.slice(0, WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_COLLAPSE_AFTER);
    const collapsedExtraEntries = limitedExtraEntries.slice(WORKSPACE_CLIENT_EXTRA_ATTRIBUTES_COLLAPSE_AFTER);

    const renderExtraRow = ([key, value]) => {
      const label = extraAttributeLabelMap.get(normalizeWorkspaceClientAttributeKey(key)) || prettifyWorkspaceClientExtraKey(key);
      return `<div class="small text-muted">${escapeHtml(label)}: <span class="text-body">${escapeHtml(formatWorkspaceClientExtraValue(value))}</span></div>`;
    };

    const expandedRows = expandedExtraEntries
      .map((entry) => renderExtraRow(entry))
      .join('');
    const collapsedRows = collapsedExtraEntries
      .map(([key, value]) => {
        return renderExtraRow([key, value]);
      })
      .join('');

    const segments = Array.isArray(client.segments) ? client.segments.filter(Boolean) : [];
    const segmentBadges = segments.length
      ? `<div class="d-flex flex-wrap gap-1 mt-2">${segments.map((segment) => `<span class="badge text-bg-light border">${escapeHtml(segment)}</span>`).join('')}</div>`
      : '';

    const contextSources = Array.isArray(client.context_sources)
      ? client.context_sources.filter((item) => item && typeof item === 'object')
      : [];
    const sourceBadgeClass = (status) => {
      switch (String(status || '').toLowerCase()) {
        case 'ready':
          return 'text-bg-success';
        case 'stale':
          return 'text-bg-warning';
        case 'invalid_utc':
        case 'missing':
          return 'text-bg-danger';
        default:
          return 'text-bg-secondary';
      }
    };
    const sourceStatusLabel = (status) => {
      switch (String(status || '').toLowerCase()) {
        case 'ready':
          return 'ready';
        case 'stale':
          return 'stale';
        case 'invalid_utc':
          return 'invalid UTC';
        case 'missing':
          return 'missing';
        default:
          return 'optional';
      }
    };
    const contextSourceIssueCount = contextSources.filter((source) => {
      const status = String(source?.status || '').toLowerCase();
      return ['missing', 'stale', 'invalid_utc'].includes(status);
    }).length;
    const contextSourceRequiredCount = contextSources.filter((source) => source?.required === true).length;
    const contextSourcesSection = contextSources.length
      ? `<details class="mt-2" data-workspace-telemetry-section="context_sources" data-workspace-telemetry-items="${contextSources.length}" data-workspace-telemetry-required="${contextSourceRequiredCount}" data-workspace-telemetry-gaps="${contextSourceIssueCount}" data-workspace-telemetry-hidden="0"${contextSourceIssueCount > 0 ? ' open' : ''}>
        <summary class="small fw-semibold">Источники контекста <span class="text-muted fw-normal">(${contextSources.length}; required ${contextSourceRequiredCount}; gaps ${contextSourceIssueCount})</span></summary>
        <div class="small text-muted mt-1">Свернуто по умолчанию, чтобы primary customer-context оставался выше policy-шума.</div>
        <div class="d-flex flex-column gap-2 mt-2">
          ${contextSources.map((source) => {
            const meta = [];
            if (Number.isFinite(Number(source.matched_attribute_count)) && Number(source.matched_attribute_count) > 0) {
              meta.push(`${Number(source.matched_attribute_count)} атр.`);
            }
            if (source.updated_at_utc) {
              meta.push(`UTC ${formatWorkspaceDateTime(source.updated_at_utc)}`);
            } else if (source.updated_at_raw && String(source.status || '').toLowerCase() === 'invalid_utc') {
              meta.push(`invalid: ${String(source.updated_at_raw).trim()}`);
            }
            if (source.linked === true) {
              meta.push('есть ссылка');
            }
            return `<div class="border rounded px-2 py-1">
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="fw-semibold">${escapeHtml(source.label || source.key || 'Источник')}</span>
                <span class="badge ${sourceBadgeClass(source.status)}">${escapeHtml(sourceStatusLabel(source.status))}</span>
                ${source.required === true ? '<span class="badge text-bg-light border">required</span>' : ''}
              </div>
              ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
              ${source.summary ? `<div class="small text-muted">${escapeHtml(String(source.summary))}</div>` : ''}
            </div>`;
          }).join('')}
        </div></details>`
      : '';

    const attributePolicies = Array.isArray(client.attribute_policies)
      ? client.attribute_policies.filter((item) => item && typeof item === 'object')
      : [];
    const attributePolicyBadgeClass = (status) => {
      switch (String(status || '').toLowerCase()) {
        case 'ready':
          return 'text-bg-success';
        case 'stale':
          return 'text-bg-warning';
        case 'missing':
        case 'invalid_utc':
        case 'unavailable':
          return 'text-bg-danger';
        default:
          return 'text-bg-secondary';
      }
    };
    const attributePolicyStatusLabel = (status) => {
      switch (String(status || '').toLowerCase()) {
        case 'ready':
          return 'ready';
        case 'stale':
          return 'stale';
        case 'missing':
          return 'missing';
        case 'invalid_utc':
          return 'invalid UTC';
        case 'unavailable':
          return 'unavailable';
        default:
          return 'untracked';
      }
    };
    const attributePolicyIssueCount = attributePolicies.filter((policy) => {
      const status = String(policy?.status || '').toLowerCase();
      return ['missing', 'stale', 'invalid_utc', 'unavailable'].includes(status);
    }).length;
    const attributePolicyRequiredCount = attributePolicies.filter((policy) => policy?.required === true).length;
    const attributePoliciesSection = attributePolicies.length
      ? `<details class="mt-2" data-workspace-telemetry-section="attribute_policy" data-workspace-telemetry-items="${attributePolicies.length}" data-workspace-telemetry-required="${attributePolicyRequiredCount}" data-workspace-telemetry-gaps="${attributePolicyIssueCount}" data-workspace-telemetry-hidden="0"${attributePolicyIssueCount > 0 ? ' open' : ''}>
        <summary class="small fw-semibold">Source / freshness policy <span class="text-muted fw-normal">(${attributePolicies.length}; required ${attributePolicyRequiredCount}; gaps ${attributePolicyIssueCount})</span></summary>
        <div class="small text-muted mt-1">Вторичные policy-детали раскрываются отдельно, чтобы не конкурировать с action-oriented contract summary.</div>
        <div class="d-flex flex-column gap-2 mt-2">
          ${attributePolicies.map((policy) => {
            const meta = [];
            if (policy.source_label || policy.source_key) {
              meta.push(`source: ${String(policy.source_label || policy.source_key).trim()}`);
            }
            if (Number.isFinite(Number(policy.freshness_ttl_hours)) && Number(policy.freshness_ttl_hours) > 0) {
              meta.push(`TTL ${Number(policy.freshness_ttl_hours)}h`);
            } else if (policy.freshness_required === true) {
              meta.push('freshness required');
            }
            if (policy.updated_at_utc) {
              meta.push(`UTC ${formatWorkspaceDateTime(policy.updated_at_utc)}`);
            } else if (policy.updated_at_raw && String(policy.status || '').toLowerCase() === 'invalid_utc') {
              meta.push(`invalid: ${String(policy.updated_at_raw).trim()}`);
            }
            return `<div class="border rounded px-2 py-1">
              <div class="d-flex flex-wrap align-items-center gap-2">
                <span class="fw-semibold">${escapeHtml(policy.label || policy.key || 'Attribute')}</span>
                <span class="badge ${attributePolicyBadgeClass(policy.status)}">${escapeHtml(attributePolicyStatusLabel(policy.status))}</span>
                ${policy.required === true ? '<span class="badge text-bg-light border">required</span>' : ''}
              </div>
              ${meta.length ? `<div class="small text-muted mt-1">${escapeHtml(meta.join(' · '))}</div>` : ''}
              ${policy.summary ? `<div class="small text-muted">${escapeHtml(String(policy.summary))}</div>` : ''}
            </div>`;
          }).join('')}
        </div></details>`
      : '';

    const contract = context?.contract && typeof context.contract === 'object'
      ? context.contract
      : (client.context_contract && typeof client.context_contract === 'object' ? client.context_contract : null);
    const contractViolations = Array.isArray(contract?.violations) ? contract.violations.filter(Boolean) : [];
    const contractViolationDetails = normalizeWorkspaceContextViolationDetails(contract?.violation_details);
    const primaryViolationPayload = Array.isArray(contract?.primary_violation_details)
      ? normalizeWorkspaceContextViolationDetails(contract.primary_violation_details)
      : [];
    const primaryViolationCards = primaryViolationPayload.length
      ? primaryViolationPayload
      : contractViolationDetails.slice(0, 2);
    const extraViolationCards = contractViolationDetails.slice(primaryViolationCards.length);
    const deferredViolationCount = Number(contract?.deferred_violation_count || extraViolationCards.length || 0);
    const contractOperatorSummary = String(contract?.operator_summary || '').trim();
    const contractNextStepSummary = String(contract?.next_step_summary || '').trim();
    const focusBlocks = Array.isArray(contract?.operator_focus_blocks) ? contract.operator_focus_blocks : [];
    const progressiveDisclosureReady = contract?.progressive_disclosure_ready === true;
    const renderViolationCard = (detail) => `<div class="border rounded px-2 py-2 bg-white">
        <div class="d-flex flex-wrap align-items-center gap-2">
          <span class="badge ${workspaceContextViolationSeverityBadge(detail.severity)}">${escapeHtml(workspaceContextViolationSeverityLabel(detail.severity))}</span>
          <span class="badge text-bg-light border">${escapeHtml(workspaceContextViolationTypeLabel(detail.type))}</span>
          ${detail.code ? `<span class="small text-muted">${escapeHtml(detail.code)}</span>` : ''}
        </div>
        <div class="mt-1 fw-semibold">${escapeHtml(detail.shortLabel || detail.operatorMessage)}</div>
        <div class="small text-muted mt-1">${escapeHtml(detail.operatorMessage)}</div>
        ${detail.nextStep ? `<div class="small mt-1"><span class="text-muted">Следующий шаг:</span> ${escapeHtml(detail.nextStep)}</div>` : ''}
        ${detail.playbookUrl
          ? `<div class="small mt-1"><a href="${escapeHtml(detail.playbookUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(detail.actionLabel || detail.playbookLabel)}</a>${detail.playbookSummary ? ` <span class="text-muted">· ${escapeHtml(detail.playbookSummary)}</span>` : ''}</div>`
          : ''}
      </div>`;
    const violationActionsSection = contract.ready === true || contractViolationDetails.length === 0
      ? ''
      : `<div class="d-flex flex-column gap-2 mt-2">
          ${primaryViolationCards.map((detail) => renderViolationCard(detail)).join('')}
          ${extraViolationCards.length
            ? `<details${progressiveDisclosureReady ? '' : ' open'}><summary class="small text-muted">Показать ещё ${deferredViolationCount}</summary><div class="d-flex flex-column gap-2 mt-2">${extraViolationCards.map((detail) => renderViolationCard(detail)).join('')}</div></details>`
            : ''}
        </div>`;
    const contextContractSection = contract && contract.enabled === true
      ? `<div class="alert ${contract.ready === true ? 'alert-success' : 'alert-warning'} py-2 px-3 small mt-2 mb-2">
          <div class="fw-semibold mb-1">Context contract</div>
          <div>${contractOperatorSummary || (contract.ready === true
            ? 'Minimum profile соблюдён.'
            : contractViolationDetails.length
              ? `Нужно закрыть ${contractViolationDetails.length} context-gap ${contractViolationDetails.length === 1 ? 'элемент' : (contractViolationDetails.length < 5 ? 'элемента' : 'элементов')}.`
              : `Есть отклонения: ${escapeHtml(contractViolations.join(', ') || 'contract_not_ready')}.`}</div>
          ${focusBlocks.length
            ? `<div class="mt-1"><span class="text-muted">Operator first:</span> ${escapeHtml(focusBlocks.join(', '))}</div>`
            : ''}
          ${contractNextStepSummary
            ? `<div class="mt-1"><span class="text-muted">Что сделать:</span> ${escapeHtml(contractNextStepSummary)}</div>`
            : (Array.isArray(contract.action_items) && contract.action_items.length
              ? `<div class="mt-1"><span class="text-muted">Что сделать:</span> ${escapeHtml(contract.action_items[0])}</div>`
              : '')}
          ${deferredViolationCount > 0
            ? `<div class="mt-1 text-muted">Остальные детали скрыты до раскрытия: ${deferredViolationCount}.</div>`
            : ''}
          ${violationActionsSection}
          <div class="text-muted mt-1">Проверено: ${escapeHtml(formatWorkspaceDateTime(contract.checked_at_utc || contract.checked_at))}</div>
        </div>`
      : '';

    const extraSection = expandedRows || collapsedRows
      ? `<details class="mt-2" data-workspace-telemetry-section="extra_attributes" data-workspace-telemetry-items="${limitedExtraEntries.length}" data-workspace-telemetry-required="0" data-workspace-telemetry-gaps="0" data-workspace-telemetry-hidden="${hiddenByLimitCount}">
        <summary class="small fw-semibold">Доп. атрибуты <span class="text-muted fw-normal">(${extraAttributeTotalCount}; visible ${limitedExtraEntries.length}${hiddenByLimitCount > 0 ? `; hidden ${hiddenByLimitCount}` : ''})</span></summary>
        <div class="small text-muted mt-1">Второстепенные поля скрыты до раскрытия, чтобы не конкурировать с customer-context contract.</div>
        <div class="mt-2">${expandedRows}</div>
        ${collapsedRows ? `<details class="mt-1"><summary class="small text-muted">Показать ещё ${collapsedExtraEntries.length}</summary><div class="mt-1">${collapsedRows}</div></details>` : ''}
        ${hiddenByLimitCount > 0 ? `<div class="small text-muted mt-1">Скрыто по лимиту: ${hiddenByLimitCount}.</div>` : ''}
      </details>`
      : '';

    const externalLinks = (client.external_links && typeof client.external_links === 'object')
      ? Object.values(client.external_links)
        .filter((item) => item && typeof item === 'object')
        .map((item) => {
          const url = String(item.url || '').trim();
          if (!url || !(url.startsWith('http://') || url.startsWith('https://'))) {
            return '';
          }
          const label = String(item.label || 'Профиль').trim() || 'Профиль';
          return `<a class="btn btn-sm btn-outline-secondary" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${escapeHtml(label)}</a>`;
        })
        .filter(Boolean)
      : [];
    const linksSection = externalLinks.length > 0
      ? `<div class="d-flex flex-wrap gap-2 mt-2">${externalLinks.join('')}</div>`
      : '';

    return `<div class="small"><strong>${escapeHtml(client.name || '—')}</strong></div>${healthBanner}${contextBlocksSection}${contextContractSection}${rows || '<div class="small text-muted">Дополнительные атрибуты отсутствуют.</div>'}${contextSourcesSection}${attributePoliciesSection}${extraSection}${segmentBadges}${linksSection}`;
  }

  function isWorkspaceClientExtraValue(value) {
    if (value === null || value === undefined) return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === 'object') return Object.keys(value).length > 0;
    return String(value).trim() !== '';
  }

  function formatWorkspaceClientExtraValue(value) {
    if (Array.isArray(value)) {
      return value.map((item) => formatWorkspaceClientExtraValue(item)).filter(Boolean).join(', ');
    }
    if (value && typeof value === 'object') {
      return Object.entries(value)
        .map(([key, nested]) => `${prettifyWorkspaceClientExtraKey(key)}: ${formatWorkspaceClientExtraValue(nested)}`)
        .join('; ');
    }
    return String(value);
  }

  function prettifyWorkspaceClientExtraKey(key) {
    return String(key || '')
      .replace(/[_-]+/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .replace(/^./, (char) => char.toUpperCase()) || 'Атрибут';
  }

  function resolveWorkspaceClientAttributeLabelMap(rawLabels) {
    const labels = new Map();
    if (!rawLabels || typeof rawLabels !== 'object') {
      return labels;
    }
    Object.entries(rawLabels).forEach(([key, value]) => {
      const normalizedKey = normalizeWorkspaceClientAttributeKey(key);
      const label = String(value || '').trim();
      if (!normalizedKey || !label) return;
      labels.set(normalizedKey, label);
    });
    return labels;
  }

  function resolveWorkspaceClientAttributeOrder(rawOrder) {
    if (!Array.isArray(rawOrder)) {
      return [];
    }
    const order = [];
    rawOrder.forEach((item) => {
      const normalizedKey = normalizeWorkspaceClientAttributeKey(item);
      if (!normalizedKey || order.includes(normalizedKey)) return;
      order.push(normalizedKey);
    });
    return order;
  }

  function orderWorkspaceClientExtraEntries(entries, order) {
    if (!Array.isArray(entries) || entries.length === 0 || !Array.isArray(order) || order.length === 0) {
      return Array.isArray(entries) ? entries : [];
    }
    const priority = new Map(order.map((key, index) => [key, index]));
    return [...entries].sort(([leftKey], [rightKey]) => {
      const leftPriority = priority.has(normalizeWorkspaceClientAttributeKey(leftKey))
        ? priority.get(normalizeWorkspaceClientAttributeKey(leftKey))
        : Number.POSITIVE_INFINITY;
      const rightPriority = priority.has(normalizeWorkspaceClientAttributeKey(rightKey))
        ? priority.get(normalizeWorkspaceClientAttributeKey(rightKey))
        : Number.POSITIVE_INFINITY;
      if (leftPriority !== rightPriority) {
        return leftPriority - rightPriority;
      }
      return String(leftKey).localeCompare(String(rightKey), 'ru');
    });
  }

  function normalizeWorkspaceClientAttributeKey(key) {
    return String(key || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_]+/g, '_')
      .replace(/^_+|_+$/g, '')
      .replace(/_+/g, '_');
  }

  function resolveWorkspaceFallbackReason(error) {
    const rawCode = String(error?.code || error?.message || '').trim();
    if (rawCode === 'version_mismatch') return 'version_mismatch';
    if (rawCode === 'invalid_payload') return 'invalid_payload';
    if (rawCode === 'timeout' || rawCode === 'AbortError') return 'timeout';
    const status = Number(error?.httpStatus);
    if (Number.isInteger(status) && status >= 500) return '5xx';
    if (rawCode.startsWith('workspace_http_5')) return '5xx';
    return 'unknown_error';
  }

  async function preloadWorkspaceContract(ticketId, channelId, options = {}) {
    let endpoint = withChannelParam(`/api/dialogs/${encodeURIComponent(ticketId)}/workspace`, channelId);
    const queryParams = new URLSearchParams();
    const include = typeof options.include === 'string' && options.include.trim()
      ? options.include.trim()
      : WORKSPACE_CONTRACT_INCLUDE;
    queryParams.set('include', include);
    if (Number.isInteger(options.cursor) && options.cursor >= 0) {
      queryParams.set('cursor', String(options.cursor));
    }
    if (Number.isInteger(options.limit) && options.limit > 0) {
      queryParams.set('limit', String(options.limit));
    }
    if (queryParams.size > 0) {
      endpoint += `${endpoint.includes('?') ? '&' : '?'}${queryParams.toString()}`;
    }
    let response = null;
    let payload = null;
    for (let attempt = 0; attempt <= WORKSPACE_CONTRACT_RETRY_ATTEMPTS; attempt += 1) {
      const controller = new AbortController();
      const timeoutId = window.setTimeout(() => controller.abort(), WORKSPACE_CONTRACT_TIMEOUT_MS);
      try {
        response = await fetch(endpoint, {
          credentials: 'same-origin',
          cache: 'no-store',
          signal: controller.signal,
        });
        payload = await response.json();
      } catch (fetchError) {
        window.clearTimeout(timeoutId);
        if (fetchError?.name === 'AbortError') {
          const timeoutError = new Error('workspace_request_timeout');
          timeoutError.code = 'timeout';
          throw timeoutError;
        }
        if (attempt < WORKSPACE_CONTRACT_RETRY_ATTEMPTS) {
          continue;
        }
        const networkError = new Error('workspace_network_error');
        networkError.code = 'network_error';
        throw networkError;
      }
      window.clearTimeout(timeoutId);
      if (response.ok) break;
      if (response.status >= 500 && attempt < WORKSPACE_CONTRACT_RETRY_ATTEMPTS) {
        continue;
      }
      const requestError = new Error(payload?.error || `workspace_http_${response.status}`);
      requestError.code = `workspace_http_${response.status}`;
      requestError.httpStatus = response.status;
      throw requestError;
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

  async function reloadWorkspaceForInitialRoute(statusText = 'Повторная загрузка ленты…') {
    if (!WORKSPACE_V1_ENABLED || !activeWorkspaceTicketId) return;
    const initialRow = rowsList().find((row) => String(row.dataset.ticketId || '') === activeWorkspaceTicketId) || null;
    if (workspaceMessagesError) workspaceMessagesError.classList.add('d-none');
    if (workspaceClientError) workspaceClientError.classList.add('d-none');
    if (workspaceHistoryError) workspaceHistoryError.classList.add('d-none');
    if (workspaceRelatedEventsError) workspaceRelatedEventsError.classList.add('d-none');
    if (workspaceSlaError) workspaceSlaError.classList.add('d-none');
    if (workspaceMessagesState) {
      workspaceMessagesState.classList.remove('d-none');
      workspaceMessagesState.textContent = statusText;
    }
    await openDialogWithWorkspaceFallback(activeWorkspaceTicketId, initialRow, { source: 'initial_route' });
  }

  function isWorkspaceTemporarilyDisabled() {
    return workspaceTemporarilyDisabledUntil > Date.now();
  }

  function registerWorkspaceFailure() {
    workspaceFailureStreak += 1;
    if (workspaceFailureStreak >= WORKSPACE_FAILURE_STREAK_THRESHOLD) {
      workspaceTemporarilyDisabledUntil = Date.now() + WORKSPACE_FAILURE_COOLDOWN_MS;
      workspaceFailureStreak = 0;
      return true;
    }
    return false;
  }

  function clearWorkspaceFailureStreak() {
    workspaceFailureStreak = 0;
    workspaceTemporarilyDisabledUntil = 0;
  }

  async function openDialogWithWorkspaceFallback(ticketId, row, options = {}) {
    activeWorkspacePayload = null;
    const source = options.source || 'manual_open';
    const channelId = options.channelId ?? row?.dataset?.channelId ?? null;
    if (isWorkspaceTemporarilyDisabled()) {
      if (!WORKSPACE_DISABLE_LEGACY_FALLBACK) {
        if (typeof showNotification === 'function') {
          showNotification('Workspace временно в cooldown — открыт legacy modal как rollback.', 'warning');
        }
        openDialogDetails(ticketId, row || activeDialogRow);
      } else if (typeof showNotification === 'function') {
        showNotification('Legacy modal отключён: дождитесь завершения workspace cooldown или исправьте причину деградации workspace.', 'warning');
      }
      return;
    }
    startWorkspaceOpenTimer(ticketId);
    try {
      const workspacePayload = await preloadWorkspaceContract(ticketId, channelId, { limit: WORKSPACE_MESSAGES_PAGE_LIMIT });
      const durationMs = finishWorkspaceOpenTimer(ticketId);
      await emitWorkspaceTelemetry('workspace_open_ms', {
        ticketId,
        durationMs,
        contractVersion: workspacePayload?.contract_version || null,
      });
      if (Number.isFinite(durationMs) && durationMs > WORKSPACE_OPEN_SLO_MS) {
        console.warn(`workspace_open_ms degraded for ticket ${ticketId}: ${durationMs}ms > ${WORKSPACE_OPEN_SLO_MS}ms`);
        await emitWorkspaceTelemetry('workspace_guardrail_breach', {
          ticketId,
          reason: 'slow_open',
          sloMs: WORKSPACE_OPEN_SLO_MS,
          durationMs,
          source,
          contractVersion: workspacePayload?.contract_version || null,
        });
      }
      clearWorkspaceFailureStreak();
      activeWorkspaceTicketId = String(ticketId || '').trim();
      activeWorkspaceChannelId = channelId;
      activeWorkspacePayload = workspacePayload;
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
      const reason = resolveWorkspaceFallbackReason(error);
      await emitWorkspaceTelemetry('workspace_render_error', {
        ticketId,
        durationMs,
        errorCode: reason,
        contractVersion: error?.contractVersion || null,
        httpStatus: Number(error?.httpStatus) || null,
      });
      await emitWorkspaceTelemetry('workspace_guardrail_breach', {
        ticketId,
        reason: 'render_error',
        errorCode: reason,
        durationMs,
        source,
        contractVersion: error?.contractVersion || null,
        httpStatus: Number(error?.httpStatus) || null,
      });
      await emitWorkspaceTelemetry('workspace_fallback_to_legacy', {
        ticketId,
        reason,
        durationMs,
        contractVersion: error?.contractVersion || null,
        httpStatus: Number(error?.httpStatus) || null,
      });
      await emitWorkspaceTelemetry('workspace_guardrail_breach', {
        ticketId,
        reason: 'fallback_to_legacy',
        fallbackReason: reason,
        durationMs,
        source,
        contractVersion: error?.contractVersion || null,
        httpStatus: Number(error?.httpStatus) || null,
      });
      const activatedCooldown = registerWorkspaceFailure();
      if (activatedCooldown) {
        await emitWorkspaceTelemetry('workspace_guardrail_breach', {
          ticketId,
          reason: 'fallback_streak_cooldown',
          threshold: WORKSPACE_FAILURE_STREAK_THRESHOLD,
          cooldownMs: WORKSPACE_FAILURE_COOLDOWN_MS,
          source,
        });
        if (typeof showNotification === 'function') {
          showNotification('Workspace временно переведён в cooldown из-за серии ошибок. Используется legacy-режим.', 'warning');
        }
      }
      const fallbackAllowed = !WORKSPACE_DISABLE_LEGACY_FALLBACK;
      if (fallbackAllowed) {
        if (typeof showNotification === 'function') {
          showNotification('Workspace временно недоступен — выполнен rollback в legacy modal.', 'warning');
        }
        openDialogDetails(ticketId, row || activeDialogRow);
        return;
      }
      if (source === 'initial_route' && workspaceShell) {
        workspaceShell.classList.remove('d-none');
        if (workspaceMessagesState) {
          workspaceMessagesState.classList.remove('d-none');
          workspaceMessagesState.textContent = 'Workspace временно недоступен. Auto-fallback в legacy отключён текущим режимом rollout.';
        }
        if (workspaceMessagesError) {
          workspaceMessagesError.classList.remove('d-none');
        }
      }
      if (typeof showNotification === 'function') {
        showNotification('Legacy modal отключён: auto-fallback недоступен. Проверьте telemetry workspace и исправьте ошибку контракта.', 'warning');
      }
      return;
    }
  }

  function openDialogEntry(ticketId, row) {
    if (!ticketId) return;
    if (!confirmWorkspaceTicketSwitch(ticketId)) {
      return;
    }
    setWorkspaceReadonlyMode(false);
    const channelId = row?.dataset?.channelId ?? null;
    const nextUrl = buildWorkspaceDialogUrl(ticketId, channelId);
    const currentPath = `${window.location.pathname || ''}${window.location.search || ''}`;
    if (currentPath !== nextUrl) {
      window.history.pushState({ ticketId: String(ticketId || '').trim() }, '', nextUrl);
    }
    openDialogDetails(ticketId, row);
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

    if ((event.key === '?' || (event.shiftKey && event.key === '/')) && hotkeysModal) {
      event.preventDefault();
      hotkeysModal.show();
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

    if (key === 'l') {
      event.preventDefault();
      const nextSortMode = filterState.sortMode === 'sla_priority' ? 'default' : 'sla_priority';
      filterState.sortMode = nextSortMode;
      if (sortModeSelect) {
        sortModeSelect.value = nextSortMode;
      }
      if (nextSortMode !== 'sla_priority') {
        lastManualSortMode = nextSortMode;
      }
      persistDialogPreferences();
      applyFilters();
      if (typeof showNotification === 'function') {
        showNotification(nextSortMode === 'sla_priority' ? 'Включена сортировка SLA-first' : 'Включена стандартная сортировка', 'info');
      }
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
        notifyPermissionDenied(`Отложить выбранные на ${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES).replace('Отложить ', '')}`);
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
        notifyPermissionDenied(formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES));
        return;
      }
      const row = getShortcutTargetRow();
      const snoozeBtn = row?.querySelector('.dialog-snooze-btn:not(.d-none)');
      const ticketId = snoozeBtn?.dataset.ticketId;
      if (!ticketId || !snoozeBtn) return;
      setActiveDialogRow(row, { ensureVisible: true });
      snoozeDialog(ticketId, QUICK_SNOOZE_MINUTES, snoozeBtn)
        .then(() => {
          setSnooze(ticketId, QUICK_SNOOZE_MINUTES);
          updateRowQuickActions(row);
          applyFilters();
          if (typeof showNotification === 'function') {
            showNotification(`Диалог отложен на ${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES).replace('Отложить ', '')}`, 'success');
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


  function renderMacroVariableCatalog(variables) {
    if (!macroVariableCatalog) return;
    if (!Array.isArray(variables) || variables.length === 0) {
      macroVariableCatalog.textContent = 'Доступны шаблоны вида {{ticket_id}} и {{client_name}}.';
      return;
    }
    macroVariableCatalog.innerHTML = '';
    const title = document.createElement('span');
    title.className = 'text-muted';
    title.textContent = 'Переменные: ';
    macroVariableCatalog.appendChild(title);
    variables.forEach((item, index) => {
      const code = document.createElement('code');
      const key = String(item?.key || '').trim();
      const label = String(item?.label || '').trim();
      const defaultValue = String(item?.default_value || '').trim();
      const source = String(item?.source || '').trim();
      code.textContent = `{{${key}}}`;
      const titleParts = [];
      if (label || key) titleParts.push(label || key);
      if (defaultValue) titleParts.push(`default: ${defaultValue}`);
      if (source) titleParts.push(`source: ${source}`);
      code.title = titleParts.join(' · ');
      macroVariableCatalog.appendChild(code);
      if (index < variables.length - 1) {
        macroVariableCatalog.appendChild(document.createTextNode(', '));
      }
    });
  }

  function resolveMacroVariableDefaults(variables) {
    const defaults = {};
    if (!Array.isArray(variables)) return defaults;
    variables.forEach((item) => {
      const key = String(item?.key || '').trim().toLowerCase();
      const defaultValue = String(item?.default_value || '').trim();
      if (!key || !defaultValue) return;
      defaults[key] = defaultValue;
    });
    return defaults;
  }

  async function initMacroVariableCatalog(ticketId, forceReload = false) {
    const normalizedTicketId = ticketId ? String(ticketId).trim() : '';
    if (!forceReload && macroVariableCatalogInitialized && macroVariableCatalogTicketId === normalizedTicketId) return;
    macroVariableCatalogInitialized = true;
    macroVariableCatalogTicketId = normalizedTicketId;
    const fallbackVariables = [
      { key: 'client_name', label: 'Имя клиента' },
      { key: 'ticket_id', label: 'ID обращения' },
      { key: 'operator_name', label: 'Имя оператора' },
      { key: 'channel_name', label: 'Канал обращения' },
      { key: 'business', label: 'Бизнес-направление' },
      { key: 'location', label: 'Локация клиента' },
      { key: 'dialog_status', label: 'Статус диалога' },
      { key: 'created_at', label: 'Дата создания' },
      { key: 'current_date', label: 'Текущая дата' },
      { key: 'current_time', label: 'Текущее время' },
    ];
    try {
      const params = new URLSearchParams();
      if (normalizedTicketId) params.set('ticketId', normalizedTicketId);
      const endpoint = params.toString() ? `/api/dialogs/macro/variables?${params.toString()}` : '/api/dialogs/macro/variables';
      const response = await fetch(endpoint, { headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const payload = await response.json();
      const catalogVariables = Array.isArray(payload?.variables) ? payload.variables : fallbackVariables;
      macroVariableDefaults = resolveMacroVariableDefaults(catalogVariables);
      renderMacroVariableCatalog(catalogVariables);
    } catch (error) {
      console.warn('Failed to load macro variables catalog', error);
      macroVariableDefaults = {};
      renderMacroVariableCatalog(fallbackVariables);
    }
  }

  function resolveMacroVariables() {
    const now = new Date();
    const variables = {
      client_name: activeDialogContext.clientName || 'клиент',
      ticket_id: activeDialogTicketId || '—',
      operator_name: activeDialogContext.operatorName || 'оператор',
      channel_name: activeDialogContext.channelName || '—',
      business: activeDialogContext.business || '—',
      location: activeDialogContext.location || '—',
      dialog_status: activeDialogContext.status || '—',
      created_at: activeDialogContext.createdAt || '—',
      current_date: now.toLocaleDateString('ru-RU'),
      current_time: now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
    };
    Object.entries(macroVariableDefaults).forEach(([key, value]) => {
      if (!Object.prototype.hasOwnProperty.call(variables, key) || !String(variables[key] || '').trim()) {
        variables[key] = value;
      }
    });
    return variables;
  }

  function resolveMacroText(template) {
    if (!template) return '';
    const text = String(template?.message || template?.text || '').trim();
    if (!text) return '';
    const variables = resolveMacroVariables();
    return text.replace(/\{\{\s*([a-z0-9_]+)(?:\s*\|\s*([^}]+))?\s*\}\}/gi, (match, key, fallback) => {
      const normalizedKey = String(key || '').toLowerCase();
      if (Object.prototype.hasOwnProperty.call(variables, normalizedKey)) {
        return String(variables[normalizedKey]);
      }
      const fallbackText = String(fallback || '').trim();
      return fallbackText || match;
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
    const workflow = resolveMacroWorkflow(template);
    if (workflow.assignToMe) {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-info-subtle border';
      badge.textContent = 'Workflow: назначить мне';
      macroTemplateMeta.appendChild(badge);
    }
    if (workflow.snoozeMinutes > 0) {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-info-subtle border';
      badge.textContent = `Workflow: snooze ${workflow.snoozeMinutes}м`;
      macroTemplateMeta.appendChild(badge);
    }
    if (workflow.closeTicket) {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-warning-subtle border';
      badge.textContent = 'Workflow: закрыть тикет';
      macroTemplateMeta.appendChild(badge);
    }
    const namespace = String(template?.namespace || '').trim();
    if (namespace) {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-light border';
      badge.textContent = `Namespace: ${namespace}`;
      macroTemplateMeta.appendChild(badge);
    }
    const owner = String(template?.owner || '').trim();
    if (owner) {
      const badge = document.createElement('span');
      badge.className = 'badge text-bg-light border';
      badge.textContent = `Owner: ${owner}`;
      macroTemplateMeta.appendChild(badge);
    }
    const hasActions = workflow.assignToMe || workflow.snoozeMinutes > 0 || workflow.closeTicket;
    const hasMessage = Boolean(message);
    macroTemplateEmpty.classList.toggle('d-none', hasMessage || hasActions);
    if (macroTemplateApply) {
      macroTemplateApply.disabled = !(hasMessage || hasActions);
    }
    if (activeMacroMeta && (hasMessage || hasActions)) {
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
      if (hasTemplates) {
        initMacroVariableCatalog(activeDialogTicketId, true);
      }
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

    if (workspaceComposerMacroSection) {
      workspaceComposerMacroTemplates = DIALOG_TEMPLATES.macroTemplates.map((template, index) => ({
        ...template,
        __macroIndex: index,
      }));
      const hasTemplates = workspaceComposerMacroTemplates.length > 0;
      workspaceComposerMacroSection.classList.toggle('d-none', !hasTemplates);
      if (hasTemplates) {
        renderWorkspaceMacroOptions(workspaceComposerMacroTemplates);
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

  function resolveMacroWorkflow(template) {
    const workflow = template && typeof template?.workflow === 'object' && template.workflow !== null
      ? template.workflow
      : template || {};
    const snoozeMinutes = Number.parseInt(workflow.snooze_minutes, 10);
    return {
      assignToMe: workflow.assign_to_me === true || workflow.assign_to_me === 'true' || workflow.assign_to_me === 1 || workflow.assign_to_me === '1',
      closeTicket: workflow.close_ticket === true || workflow.close_ticket === 'true' || workflow.close_ticket === 1 || workflow.close_ticket === '1',
      snoozeMinutes: Number.isFinite(snoozeMinutes) && snoozeMinutes >= 1 ? Math.min(snoozeMinutes, 1440) : 0,
    };
  }

  async function executeMacroWorkflow(template, options = {}) {
    const ticketId = String(options.ticketId || '').trim();
    const row = options.row || null;
    if (!ticketId) return [];
    const workflow = resolveMacroWorkflow(template);
    const steps = [];

    if (workflow.assignToMe) {
      await takeDialog(ticketId, row, null);
      steps.push('назначить мне');
    }
    if (workflow.snoozeMinutes > 0) {
      await snoozeDialog(ticketId, workflow.snoozeMinutes, null);
      setSnooze(ticketId, workflow.snoozeMinutes);
      if (row) updateRowQuickActions(row);
      applyFilters();
      steps.push(`отложить на ${workflow.snoozeMinutes} мин`);
    }
    if (workflow.closeTicket && row) {
      await closeDialogQuick(ticketId, row, null);
      steps.push('закрыть тикет');
    }

    return steps;
  }

  async function applyMacroTemplate() {
    if (!activeMacroTemplate) return;
    const message = resolveMacroText(activeMacroTemplate);
    const appliedSteps = await executeMacroWorkflow(activeMacroTemplate, {
      ticketId: activeDialogTicketId,
      row: activeDialogRow,
    });
    if (message) {
      insertReplyText(message);
    }
    if (typeof showNotification === 'function' && (message || appliedSteps.length)) {
      const workflowSuffix = appliedSteps.length ? `; действия: ${appliedSteps.join(', ')}` : '';
      showNotification(`Макрос применён${workflowSuffix}.`, 'success');
    }
    emitWorkspaceTelemetry('macro_apply', {
      ticketId: activeDialogTicketId,
      templateId: activeMacroMeta?.id || null,
      templateName: activeMacroMeta?.name || null,
      workflowActions: appliedSteps.length ? appliedSteps.join('|') : null,
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
    if (value.includes('support') || value.includes('operator') || value.includes('admin') || value.includes('system') || value.includes('ai_agent')) {
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

  function parseUtcDateValue(value) {
    if (value === null || value === undefined || value === '') return null;
    const rawValue = typeof value === 'string' ? value.trim() : value;
    if (rawValue === '') return null;
    let normalized = rawValue;
    if (typeof rawValue === 'string' && /^\d+(\.\d+)?$/.test(rawValue)) {
      normalized = Number(rawValue);
    }
    if (typeof normalized === 'number') {
      const epochMs = normalized < 1000000000000 ? normalized * 1000 : normalized;
      const parsedFromEpoch = new Date(epochMs);
      return Number.isNaN(parsedFromEpoch.getTime()) ? null : parsedFromEpoch;
    }
    if (typeof normalized === 'string') {
      let candidate = normalized.replace(' ', 'T');
      if (/^\d{4}-\d{2}-\d{2}$/.test(candidate)) {
        candidate = `${candidate}T00:00:00Z`;
      } else if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(candidate)) {
        candidate = `${candidate}Z`;
      }
      const parsedFromString = new Date(candidate);
      if (!Number.isNaN(parsedFromString.getTime())) {
        return parsedFromString;
      }
    }
    const parsed = new Date(normalized);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  function formatUtcDate(date, options = {}) {
    if (!(date instanceof Date) || Number.isNaN(date.getTime())) {
      return options.fallback || '—';
    }
    const day = String(date.getUTCDate()).padStart(2, '0');
    const month = String(date.getUTCMonth() + 1).padStart(2, '0');
    const year = date.getUTCFullYear();
    const base = `${day}.${month}.${year}`;
    if (!options.includeTime) {
      return base;
    }
    const hours = String(date.getUTCHours()).padStart(2, '0');
    const minutes = String(date.getUTCMinutes()).padStart(2, '0');
    return `${base} ${hours}:${minutes} UTC`;
  }

  function formatTimestamp(value, options = {}) {
    const parsed = parseUtcDateValue(value);
    if (!parsed) return options.fallback || String(value || '');
    return formatUtcDate(parsed, { includeTime: options.includeTime, fallback: options.fallback || '—' });
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

  function isTechnicalHistoryMessage(message) {
    const type = String(message?.messageType || '').toLowerCase();
    const senderType = normalizeMessageSenderByType(message?.messageType, message?.sender);
    const text = String(message?.message || '').toLowerCase();
    if (type.includes('feedback') || type.includes('rating')) return true;
    if (senderType === 'system' && (text.includes('поставьте оценку') || text.includes('оцените') || text.includes('оценк'))) {
      return true;
    }
    return false;
  }

  function renderHistory(messages) {
    if (!detailsHistory) return;
    const filteredMessages = Array.isArray(messages)
      ? messages.filter((msg) => !isTechnicalHistoryMessage(msg))
      : [];
    if (filteredMessages.length === 0) {
      detailsHistory.innerHTML = '<div class="text-muted">Сообщения не найдены.</div>';
      lastHistoryMarker = 'empty';
      return;
    }
    lastHistoryMarker = historyMarker(filteredMessages);
    detailsHistory.innerHTML = filteredMessages.map((msg) => messageToHtml(msg)).join('');
    detailsHistory.scrollTop = detailsHistory.scrollHeight;
  }

  function appendHistoryMessage(message) {
    if (!detailsHistory) return;
    if (isTechnicalHistoryMessage(message)) return;
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
        case 'auto_processing':
          return 'в автоматической обработке';
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
    initMacroVariableCatalog(ticketId, true);
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
    if (detailsOpenClientCard) {
      detailsOpenClientCard.href = '#';
      detailsOpenClientCard.classList.add('disabled');
      detailsOpenClientCard.setAttribute('aria-disabled', 'true');
    }

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
      const clientUserId = getDialogUserId(summary) || String(fallbackRow?.dataset?.userId || '').trim();
      if (detailsAvatar) {
        bindAvatar(detailsAvatar, clientUserId, clientName);
      }
      if (detailsClientName) detailsClientName.textContent = clientName;
      if (detailsClientStatus) detailsClientStatus.textContent = clientStatus;
      if (detailsOpenClientCard) {
        if (clientUserId) {
          detailsOpenClientCard.href = `/client/${encodeURIComponent(clientUserId)}`;
          detailsOpenClientCard.classList.remove('disabled');
          detailsOpenClientCard.setAttribute('aria-disabled', 'false');
        } else {
          detailsOpenClientCard.href = '#';
          detailsOpenClientCard.classList.add('disabled');
          detailsOpenClientCard.setAttribute('aria-disabled', 'true');
        }
      }
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
        channelName: channelLabel,
        business: businessLabel,
        location: locationLabel,
        status: statusLabel || '—',
        createdAt: createdDisplay || createdLabel || '—',
      };
      if (detailsSummary) {
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
      event.preventDefault();
      const ticketId = taskBtn.dataset.ticketId;
      const clientName = taskBtn.dataset.client;
      setTaskDraft({
        ticketId,
        client: clientName,
      });
      window.location.href = buildTaskCreateUrl(ticketId, clientName);
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
        notifyPermissionDenied(formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES));
        return;
      }
      const ticketId = snoozeBtn.dataset.ticketId;
      const row = snoozeBtn.closest('tr');
      setActiveDialogRow(row, { ensureVisible: true });
      snoozeDialog(ticketId, QUICK_SNOOZE_MINUTES, snoozeBtn)
        .then(() => {
          setSnooze(ticketId, QUICK_SNOOZE_MINUTES);
          updateRowQuickActions(row);
          applyFilters();
          if (typeof showNotification === 'function') {
            showNotification(`Диалог отложен на ${formatSnoozeActionLabel(QUICK_SNOOZE_MINUTES).replace('Отложить ', '')}`, 'success');
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
    detailsCreateTask.addEventListener('click', (event) => {
      event.preventDefault();
      const ticketId = String(activeDialogTicketId || activeWorkspaceTicketId || '').trim();
      const client = String(detailsClientName?.textContent || '').trim();
      if (!ticketId) return;
      setTaskDraft({ ticketId, client });
      window.location.href = buildTaskCreateUrl(ticketId, client);
    });
  }

  if (detailsOpenClientCard) {
    detailsOpenClientCard.addEventListener('click', (event) => {
      const href = String(detailsOpenClientCard.getAttribute('href') || '').trim();
      if (!href || href === '#') {
        event.preventDefault();
      }
    });
  }

  if (workspaceCreateTaskBtn) {
    workspaceCreateTaskBtn.addEventListener('click', (event) => {
      event.preventDefault();
      const ticketId = String(activeWorkspaceTicketId || '').trim();
      const client = String(detailsClientName?.textContent || '').trim();
      if (!ticketId) return;
      setTaskDraft({ ticketId, client });
      window.location.href = buildTaskCreateUrl(ticketId, client);
    });
  }

  if (workspaceNavPrevBtn) {
    workspaceNavPrevBtn.addEventListener('click', async () => {
      if (workspaceNavPrevBtn.disabled) return;
      await navigateWorkspaceInline('previous');
    });
  }

  if (workspaceNavNextBtn) {
    workspaceNavNextBtn.addEventListener('click', async () => {
      if (workspaceNavNextBtn.disabled) return;
      await navigateWorkspaceInline('next');
    });
  }

  if (workspaceAssignBtn) {
    workspaceAssignBtn.addEventListener('click', async () => {
      if (!activeWorkspaceTicketId || !activeDialogRow) return;
      try {
        await takeDialog(activeWorkspaceTicketId, activeDialogRow, workspaceAssignBtn);
        await refreshActiveWorkspaceContract({ successMessage: 'Диалог назначен на вас.' });
      } catch (_error) {
        // notification is already shown inside takeDialog
      }
    });
  }

  if (workspaceSnoozeBtn) {
    workspaceSnoozeBtn.addEventListener('click', async () => {
      if (!activeWorkspaceTicketId) return;
      try {
        await snoozeDialog(activeWorkspaceTicketId, QUICK_SNOOZE_MINUTES, workspaceSnoozeBtn);
        setSnooze(activeWorkspaceTicketId, QUICK_SNOOZE_MINUTES);
        if (activeDialogRow) {
          updateRowQuickActions(activeDialogRow);
        }
        if (typeof showNotification === 'function') {
          showNotification(`Диалог отложен на ${formatSnoozeDurationLabel(QUICK_SNOOZE_MINUTES)}.`, 'success');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось отложить диалог', 'error');
        }
      }
    });
  }

  if (workspaceResolveBtn) {
    workspaceResolveBtn.addEventListener('click', async () => {
      if (!activeWorkspaceTicketId) return;
      workspaceResolveBtn.disabled = true;
      try {
        const categories = Array.from(selectedCategories);
        if (!categories.length) {
          renderWorkspaceCategories();
          throw new Error('Выберите хотя бы одну категорию перед закрытием диалога.');
        }
        const response = await fetch(`/api/dialogs/${encodeURIComponent(activeWorkspaceTicketId)}/resolve`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ categories }),
        });
        const payload = await response.json();
        if (!response.ok || !payload?.success) {
          throw new Error(payload?.error || `Ошибка ${response.status}`);
        }
        await refreshActiveWorkspaceContract({ successMessage: 'Диалог закрыт.' });
      } catch (error) {
        workspaceResolveBtn.disabled = false;
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось закрыть диалог', 'error');
        }
      }
    });
  }

  if (workspaceReopenBtn) {
    workspaceReopenBtn.addEventListener('click', async () => {
      if (!activeWorkspaceTicketId) return;
      if (!window.confirm('Переоткрыть закрытое обращение?')) {
        return;
      }
      workspaceReopenBtn.disabled = true;
      try {
        const response = await fetch(`/api/dialogs/${encodeURIComponent(activeWorkspaceTicketId)}/reopen`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
        });
        const payload = await response.json();
        if (!response.ok || !payload?.success) {
          throw new Error(payload?.error || `Ошибка ${response.status}`);
        }
        await refreshActiveWorkspaceContract({ successMessage: 'Диалог переоткрыт.' });
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error.message || 'Не удалось переоткрыть диалог', 'error');
        }
      } finally {
        workspaceReopenBtn.disabled = false;
      }
    });
  }

  if (workspaceLegacyBtn) {
    workspaceLegacyBtn.addEventListener('click', async () => {
      if (WORKSPACE_SINGLE_MODE) return;
      if (!activeWorkspaceTicketId || !activeDialogRow || workspaceLegacyBtn.disabled) return;
      const policy = resolveLegacyOpenPolicy(activeWorkspacePayload?.meta?.rollout);
      if (policy.enabled && policy.blocked) {
        const blockReason = policy.blockReason || 'policy_blocked';
        const humanReason = formatLegacyOpenBlockReason(blockReason);
        if (typeof showNotification === 'function') {
          const reviewMeta = [policy.reviewedBy, policy.reviewedAtUtc].filter(Boolean).join(' @ ');
          showNotification(`Legacy modal blocked: ${humanReason}${reviewMeta ? ` (${reviewMeta})` : ''}.`, 'warning');
        }
        await emitWorkspaceTelemetry('workspace_open_legacy_blocked', {
          ticketId: activeWorkspaceTicketId,
          reason: blockReason,
          decision: policy.decision || null,
          reviewedBy: policy.reviewedBy || null,
          reviewedAtUtc: policy.reviewedAtUtc || null,
          contractVersion: activeWorkspacePayload?.contract_version || null,
        });
        return;
      }
      let legacyOpenReason = 'manual_rollback';
      if (policy.enabled && policy.reasonRequired) {
        const answer = window.prompt('Укажите причину manual legacy-open (UTC policy checkpoint):', 'manual_rollback');
        legacyOpenReason = String(answer || '').trim();
        if (!legacyOpenReason) {
          if (typeof showNotification === 'function') {
            showNotification('Legacy modal не открыт: требуется причина manual open.', 'warning');
          }
          return;
        }
      }
      await emitWorkspaceTelemetry('workspace_open_legacy_manual', {
        ticketId: activeWorkspaceTicketId,
        reason: legacyOpenReason,
        contractVersion: activeWorkspacePayload?.contract_version || null,
      });
      openDialogDetails(activeWorkspaceTicketId, activeDialogRow);
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

  function renderWorkspaceMessageItem(message) {
    const author = message?.senderName || message?.senderRole || 'Участник';
    const timestamp = formatWorkspaceDateTime(message?.sentAt || message?.createdAt);
    const text = String(message?.messageText || message?.message || '').trim();
    const replyPreviewText = String(message?.replyPreview || message?.reply_preview || '').trim();
    const telegramMessageId = Number.parseInt(message?.telegramMessageId ?? message?.telegram_message_id, 10);
    const senderType = String(message?.senderRole || message?.sender || '').trim().toLowerCase();
    const normalizedMessage = {
      messageType: message?.messageType || message?.type || '',
      message: text,
      attachment: message?.attachment || message?.attachmentUrl || null,
      attachmentName: message?.attachmentName || message?.fileName || null,
    };
    const mediaMarkup = normalizedMessage.attachment ? buildMediaMarkup(normalizedMessage) : '';
    const textMarkup = text ? `<div class="workspace-message-body">${escapeHtml(text)}</div>` : '';
    const replyPreviewMarkup = replyPreviewText
      ? `<div class="small text-muted border-start ps-2 mb-1 workspace-message-reply-source">↪ ${escapeHtml(replyPreviewText)}</div>`
      : '';
    const replyTargetSupported = activeWorkspacePayload?.composer?.reply_target_supported !== false;
    const canReply = replyTargetSupported && Number.isFinite(telegramMessageId) && senderType !== 'system';
    const actionMarkup = canReply
      ? `<div class="mt-2"><button class="btn btn-sm btn-outline-secondary" type="button" data-workspace-action="reply" data-message-id="${telegramMessageId}">Ответить</button></div>`
      : '';
    const fallbackMarkup = textMarkup || mediaMarkup ? '' : '<div>—</div>';
    return `<article class="workspace-message-item" data-telegram-message-id="${Number.isFinite(telegramMessageId) ? telegramMessageId : ''}"><div class="workspace-message-meta">${escapeHtml(author)} · ${escapeHtml(timestamp)}</div>${replyPreviewMarkup}${textMarkup}${mediaMarkup}${fallbackMarkup}${actionMarkup}</article>`;
  }

  async function sendMediaFiles(files, options = {}) {
    const ticketId = String(options.ticketId || activeDialogTicketId || activeWorkspaceTicketId || '').trim();
    if (!ticketId || !files || files.length === 0) return;
    const captionSource = typeof options.caption === 'string' ? options.caption : detailsReplyText?.value || '';
    const caption = String(captionSource).trim();
    const sendButton = options.sendButton || detailsReplySend;
    const mediaInput = options.mediaInput || detailsReplyMedia;
    const appendHistory = options.appendHistory !== false;
    if (sendButton) sendButton.disabled = true;
    try {
      for (const file of Array.from(files)) {
        const formData = new FormData();
        formData.append('file', file);
        if (caption) {
          formData.append('message', caption);
        }
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/media`, {
          method: 'POST',
          body: formData,
        });
        const data = await resp.json();
        if (!resp.ok || !data?.success) {
          throw new Error(data?.error || `Ошибка ${resp.status}`);
        }
        activeDialogContext.operatorName = data.responsible || activeDialogContext.operatorName;
        if (appendHistory) {
          appendHistoryMessage({
            sender: data.responsible || 'Оператор',
            message: data.message || '',
            timestamp: data.timestamp || new Date().toISOString(),
            messageType: data.messageType || 'operator_media',
            attachment: data.attachment || null,
          });
        }
        if (activeDialogRow) {
          updateRowStatus(activeDialogRow, activeDialogRow.dataset.statusRaw || '', 'ожидает ответа клиента', 'waiting_client', 0);
        }
        if (ticketId === activeWorkspaceTicketId) {
          emitWorkspaceTelemetry('workspace_media_sent', {
            ticketId,
            reason: data.messageType || file.type || 'attachment',
            contractVersion: activeWorkspacePayload?.contract_version || 'workspace.v1',
          });
        }
      }
      if (typeof options.afterSuccess === 'function') {
        options.afterSuccess();
      } else if (detailsReplyText) {
        detailsReplyText.value = '';
      }
      if (typeof showNotification === 'function') {
        showNotification(options.successMessage || 'Медиа отправлено', 'success');
      }
    } catch (error) {
      if (typeof showNotification === 'function') {
        showNotification(error.message || options.errorMessage || 'Не удалось отправить медиа', 'error');
      }
    } finally {
      if (sendButton) sendButton.disabled = false;
      if (mediaInput) {
        mediaInput.value = '';
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
      persistDialogPreferences();
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
      persistDialogPreferences();
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
      persistDialogPreferences();
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

  if (hotkeysBtn && hotkeysModal) {
    hotkeysBtn.addEventListener('click', () => {
      hotkeysModal.show();
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
      initMacroVariableCatalog(null, true);
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
      renderWorkspaceCategories();
      updateSummaryCategories(formatCategoriesLabel(Array.from(selectedCategories)));
      scheduleCategorySave();
    });
  }

  if (workspaceCategoriesList) {
    workspaceCategoriesList.addEventListener('click', (event) => {
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
      renderWorkspaceCategories();
      updateSummaryCategories(formatCategoriesLabel(Array.from(selectedCategories)));
      scheduleCategorySave();
    });
  }

  if (workspaceCategoriesClear) {
    workspaceCategoriesClear.addEventListener('click', () => {
      selectedCategories = new Set();
      syncCategorySelections();
      renderWorkspaceCategories();
      updateSummaryCategories('—');
      scheduleCategorySave();
    });
  }

  if (workspaceAiSuggestionsRefresh) {
    workspaceAiSuggestionsRefresh.addEventListener('click', () => {
      const ticketId = activeWorkspaceTicketId || workspaceComposerTicketId;
      loadWorkspaceAiSuggestions(ticketId);
      loadWorkspaceAiReview(ticketId);
      loadWorkspaceAiControl(ticketId);
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
      try {
        const resp = await fetch(`/api/dialogs/${encodeURIComponent(ticketId)}/ai-review/approve`, {
          method: 'POST',
          credentials: 'same-origin',
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
          showNotification('��������� ��������� � ���������� � feedback-loop', 'info');
        }
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`�� ������� ��������� ���������: ${error.message || 'unknown_error'}`, 'warning');
        }
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
        }, disableMode ? 'AI �������� ��� �������� �������' : 'AI ������� ��� �������� �������');
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`�� ������� �������� ����� AI: ${error.message || 'unknown_error'}`, 'warning');
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
        }, '������ ������� ���������, ���������� AI ���������');
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(`�� ������� ��������� handoff: ${error.message || 'unknown_error'}`, 'warning');
        }
      }
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
    macroTemplateApply.addEventListener('click', async () => {
      try {
        await applyMacroTemplate();
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error?.message || 'Не удалось применить макрос', 'error');
        }
      }
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
      reloadWorkspaceSection('messages', {
        stateElement: workspaceMessagesState,
        errorElement: workspaceMessagesError,
        statusText: 'Повторная загрузка ленты…',
        failMessage: 'Не удалось обновить ленту workspace.',
      });
    });
  }

  if (workspaceMessagesLoadMore) {
    workspaceMessagesLoadMore.addEventListener('click', () => {
      loadMoreWorkspaceMessages();
    });
  }

  if (workspaceMessagesList) {
    workspaceMessagesList.addEventListener('click', (event) => {
      const replyButton = event.target.closest('button[data-workspace-action="reply"]');
      if (replyButton) {
        const messageId = Number.parseInt(replyButton.dataset.messageId, 10);
        if (!Number.isFinite(messageId)) return;
        const messageNode = replyButton.closest('.workspace-message-item');
        const previewText = messageNode?.querySelector('.workspace-message-reply-source')?.textContent
          || messageNode?.querySelector('.workspace-message-body')?.textContent
          || '';
        setWorkspaceReplyTarget(messageId, previewText);
        if (workspaceComposerText) {
          workspaceComposerText.focus();
        }
        return;
      }
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

  if (workspaceClientRetry) {
    workspaceClientRetry.addEventListener('click', () => {
      reloadWorkspaceSection('context', {
        stateElement: workspaceClientState,
        errorElement: workspaceClientError,
        statusText: 'Повторная загрузка профиля клиента…',
        failMessage: 'Не удалось обновить профиль клиента.',
      });
    });
  }

  if (workspaceHistoryRetry) {
    workspaceHistoryRetry.addEventListener('click', () => {
      reloadWorkspaceSection('context', {
        stateElement: workspaceHistoryState,
        errorElement: workspaceHistoryError,
        statusText: 'Повторная загрузка истории клиента…',
        failMessage: 'Не удалось обновить историю клиента.',
      });
    });
  }

  if (workspaceRelatedEventsRetry) {
    workspaceRelatedEventsRetry.addEventListener('click', () => {
      reloadWorkspaceSection('context', {
        stateElement: workspaceRelatedEventsState,
        errorElement: workspaceRelatedEventsError,
        statusText: 'Повторная загрузка связанных событий…',
        failMessage: 'Не удалось обновить связанные события.',
      });
    });
  }

  if (workspaceSlaRetry) {
    workspaceSlaRetry.addEventListener('click', () => {
      reloadWorkspaceSection('sla', {
        stateElement: workspaceSlaState,
        errorElement: workspaceSlaError,
        statusText: 'Повторная загрузка SLA-контекста…',
        failMessage: 'Не удалось обновить SLA-контекст.',
      });
    });
  }

  if (workspaceComposerSend) {
    workspaceComposerSend.addEventListener('click', () => {
      sendWorkspaceReply();
    });
  }

  if (workspaceComposerMediaTrigger && workspaceComposerMedia) {
    workspaceComposerMediaTrigger.addEventListener('click', () => {
      workspaceComposerMedia.click();
    });
    workspaceComposerMedia.addEventListener('change', async () => {
      await sendMediaFiles(workspaceComposerMedia.files, {
        ticketId: activeWorkspaceTicketId,
        caption: workspaceComposerText?.value || '',
        sendButton: workspaceComposerSend,
        mediaInput: workspaceComposerMedia,
        appendHistory: false,
        afterSuccess: () => {
          if (workspaceComposerText) {
            workspaceComposerText.value = '';
          }
          saveWorkspaceDraft(workspaceComposerTicketId, '');
          reloadWorkspaceSection('messages', {
            stateElement: workspaceMessagesState,
            errorElement: workspaceMessagesError,
            statusText: 'Обновление ленты после отправки медиа…',
            failMessage: 'Медиа отправлено, но лента workspace не обновилась автоматически.',
          });
        },
      });
    });
  }

  if (workspaceComposerSaveDraft) {
    workspaceComposerSaveDraft.addEventListener('click', () => {
      saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText?.value || '', { reason: 'manual' });
    });
  }

  if (workspaceComposerText) {
    workspaceComposerText.addEventListener('input', () => {
      scheduleWorkspaceDraftAutosave();
    });
    workspaceComposerText.addEventListener('keydown', (event) => {
      if (((event.ctrlKey || event.metaKey || event.altKey) && event.key === 'Enter')) {
        event.preventDefault();
        sendWorkspaceReply();
      }
      if (((event.ctrlKey || event.metaKey || event.altKey) && String(event.key).toLowerCase() === 's')) {
        event.preventDefault();
        saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { reason: 'manual' });
      }
    });
  }

  if (workspaceReplyTargetClear) {
    workspaceReplyTargetClear.addEventListener('click', () => {
      resetWorkspaceReplyTarget({ reason: 'manual_clear' });
      if (workspaceComposerText) {
        workspaceComposerText.focus();
      }
    });
  }

  if (workspaceComposerMacroSearch) {
    workspaceComposerMacroSearch.addEventListener('input', () => {
      const filteredTemplates = filterMacroTemplates(workspaceComposerMacroTemplates, workspaceComposerMacroSearch.value);
      renderWorkspaceMacroOptions(filteredTemplates);
    });
  }

  if (workspaceComposerMacroSelect) {
    workspaceComposerMacroSelect.addEventListener('change', () => {
      const selected = findMacroTemplateByWorkspaceOptionValue(workspaceComposerMacroSelect.value);
      renderWorkspaceMacroPreview(selected);
    });
  }

  if (workspaceComposerMacroApply) {
    workspaceComposerMacroApply.addEventListener('click', async () => {
      try {
        await applyWorkspaceMacroTemplate();
      } catch (error) {
        if (typeof showNotification === 'function') {
          showNotification(error?.message || 'Не удалось применить macro workflow', 'error');
        }
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
      event_group: DIALOGS_TELEMETRY_EVENT_GROUPS.workspace_abandon,
      timestamp: new Date().toISOString(),
      ticket_id: activeWorkspaceTicketId,
      reason: 'no_first_interaction',
      experiment_name: workspaceExperimentContext.experimentName,
      experiment_cohort: workspaceExperimentContext.cohort,
      operator_segment: workspaceExperimentContext.operatorSegment,
      primary_kpis: WORKSPACE_AB_TEST_CONFIG.primaryKpis,
      secondary_kpis: WORKSPACE_AB_TEST_CONFIG.secondaryKpis,
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
  applyColumnState();
  applyBusinessCellStyles();
  hydrateAvatars(table);
  applyOperatorPermissionGuards();
  renderExperimentInfoPanel();
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
  if (experimentTelemetryRefreshBtn) {
    experimentTelemetryRefreshBtn.addEventListener('click', () => {
      loadExperimentTelemetrySummary();
    });
  }
  if (experimentTelemetryAutoRefresh) {
    experimentTelemetryAutoRefresh.addEventListener('change', () => {
      syncExperimentTelemetryAutoRefresh();
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
  void loadServerTriagePreferences();
  loadAiReviewQueue();
  setInterval(loadAiReviewQueue, 30 * 1000);
  if (aiMonitoringSection && aiMonitoringState) {
    loadAiMonitoringSummary(7);
    setInterval(() => loadAiMonitoringSummary(7), 60 * 1000);
  }

  if (INITIAL_DIALOG_TICKET_ID) {
    const initialRow = rowsList().find((row) => String(row.dataset.ticketId || '') === INITIAL_DIALOG_TICKET_ID) || null;
    if (initialRow) {
      setActiveDialogRow(initialRow, { ensureVisible: true });
    }
    openDialogDetails(INITIAL_DIALOG_TICKET_ID, initialRow);
  }

  window.addEventListener('beforeunload', () => {
    if (!workspaceComposerTicketId || !workspaceComposerText || !workspaceComposerText.value.trim()) return;
    saveWorkspaceDraft(workspaceComposerTicketId, workspaceComposerText.value, { reason: 'autosave' });
  });

  if (WORKSPACE_EXPERIENCE_ENABLED && WORKSPACE_INLINE_NAVIGATION) {
    window.addEventListener('popstate', () => {
      const path = window.location.pathname || '';
      const match = path.match(/^\/dialogs\/([^/]+)$/);
      if (!match?.[1]) return;
      const ticketId = decodeURIComponent(match[1]);
      if (!confirmWorkspaceTicketSwitch(ticketId)) {
        const rollbackUrl = buildWorkspaceDialogUrl(activeWorkspaceTicketId, activeWorkspaceChannelId);
        window.history.pushState({ ticketId: activeWorkspaceTicketId }, '', rollbackUrl);
        return;
      }
      const row = rowsList().find((item) => String(item.dataset.ticketId || '') === ticketId) || null;
      setActiveDialogRow(row, { ensureVisible: true });
      openDialogDetails(ticketId, row);
    });
  }

  initColumnResize();
  restoreColumnWidths();
  initDetailsResize();
})();

(function () {
  'use strict';

  // Lazy access preserves initialization order; feature modules never copy mutable page state.
  const questionModel = window.BotQuestionModel.create({
    get DASHBOARD_ATTRIBUTE_KEY_SET() { return DASHBOARD_ATTRIBUTE_KEY_SET; },
    get DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE() { return DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE; },
    get DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES() { return DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES; },
    get QUESTION_ROUTE_PROBLEM_ID() { return QUESTION_ROUTE_PROBLEM_ID; },
    get defaultRatingPrompt() { return defaultRatingPrompt; },
    get defaultResponsesMap() { return defaultResponsesMap; },
    get editorState() { return editorState; },
    get firstPreset() { return firstPreset; },
    get generateRatingTemplateId() { return generateRatingTemplateId; },
    get generatedOptionIds() { return generatedOptionIds; },
    get generatedQuestionIds() { return generatedQuestionIds; },
    get generatedRatingTemplateIds() { return generatedRatingTemplateIds; },
    get generatedTemplateIds() { return generatedTemplateIds; },
    get getPresetMeta() { return getPresetMeta; },
    get normalizeRatingTemplate() { return normalizeRatingTemplate; },
    get presetExists() { return presetExists; },
  });
  const ratingEditor = window.BotRatingEditor.create({
    get applyStatusAppearance() { return applyStatusAppearance; },
    get generatedRatingTemplateIds() { return generatedRatingTemplateIds; },
    get hideChildModal() { return hideChildModal; },
    get html() { return html; },
    get ratingDescriptionInput() { return ratingDescriptionInput; },
    get ratingDiagnosticEl() { return ratingDiagnosticEl; },
    get ratingEditorState() { return ratingEditorState; },
    get ratingModalEl() { return ratingModalEl; },
    get ratingNameInput() { return ratingNameInput; },
    get ratingPromptInput() { return ratingPromptInput; },
    get ratingResponsesContainer() { return ratingResponsesContainer; },
    get ratingScaleInput() { return ratingScaleInput; },
    get ratingStatusEl() { return ratingStatusEl; },
    get ratingTemplatesContainer() { return ratingTemplatesContainer; },
    get showChildModal() { return showChildModal; },
    get state() { return state; },
  });
  const questionEditor = window.BotQuestionEditor.create({
    get DASHBOARD_ATTRIBUTE_KEYS() { return DASHBOARD_ATTRIBUTE_KEYS; },
    get DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE() { return DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE; },
    get DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES() { return DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES; },
    get PRESET_GROUPS() { return PRESET_GROUPS; },
    get QUESTION_ROUTE_PROBLEM_ID() { return QUESTION_ROUTE_PROBLEM_ID; },
    get addQuestionButtons() { return addQuestionButtons; },
    get canManageChildModal() { return canManageChildModal; },
    get cloneQuestion() { return cloneQuestion; },
    get cloneTemplate() { return cloneTemplate; },
    get editorState() { return editorState; },
    get encodePresetValue() { return encodePresetValue; },
    get ensurePresetDefinitionsLoaded() { return ensurePresetDefinitionsLoaded; },
    get generateTemplateId() { return generateTemplateId; },
    get getPresetMeta() { return getPresetMeta; },
    get hasPresetDefinitions() { return hasPresetDefinitions; },
    get hideChildModal() { return hideChildModal; },
    get html() { return html; },
    get isDashboardBindingKeyAllowed() { return isDashboardBindingKeyAllowed; },
    get legacyDiagnosticEl() { return legacyDiagnosticEl; },
    get normalizeBindingKey() { return normalizeBindingKey; },
    get normalizeFirstResponseTimeoutMessageValue() { return normalizeFirstResponseTimeoutMessageValue; },
    get normalizeFirstResponseTimeoutMinutesValue() { return normalizeFirstResponseTimeoutMinutesValue; },
    get normalizeRatingResponses() { return normalizeRatingResponses; },
    get normalizeScale() { return normalizeScale; },
    get normalizeStringArray() { return normalizeStringArray; },
    get persistBotSettingsPayload() { return persistBotSettingsPayload; },
    get presetEmptyAlertEl() { return presetEmptyAlertEl; },
    get presetExists() { return presetExists; },
    get presetHintsEl() { return presetHintsEl; },
    get presetStatusMessage() { return presetStatusMessage; },
    get presetSummaryCardEl() { return presetSummaryCardEl; },
    get questionsContainer() { return questionsContainer; },
    get refreshLinkedBotSettingsConsumers() { return refreshLinkedBotSettingsConsumers; },
    get renderTemplates() { return renderTemplates; },
    get resolveQuestionOptionLabels() { return resolveQuestionOptionLabels; },
    get resolveQuestionRouteChoices() { return resolveQuestionRouteChoices; },
    get resolveQuestionRouteTargets() { return resolveQuestionRouteTargets; },
    get sanitizeQuestionRoutes() { return sanitizeQuestionRoutes; },
    get serializeQuestion() { return serializeQuestion; },
    get setTemplateStatus() { return setTemplateStatus; },
    get showChildModal() { return showChildModal; },
    get state() { return state; },
    get templateCancelButton() { return templateCancelButton; },
    get templateDescriptionInput() { return templateDescriptionInput; },
    get templateFirstResponseTimeoutInput() { return templateFirstResponseTimeoutInput; },
    get templateFirstResponseTimeoutMessageInput() { return templateFirstResponseTimeoutMessageInput; },
    get templateModalEl() { return templateModalEl; },
    get templateNameInput() { return templateNameInput; },
    get templateSaveButton() { return templateSaveButton; },
    get templateStartMessageInput() { return templateStartMessageInput; },
    get templateUnifiedFieldsInput() { return templateUnifiedFieldsInput; },
    get validateQuestionFlow() { return validateQuestionFlow; },
  });


  function resolvePageBotConfig() {
    const pageConfigRuntime = window.SettingsRuntimeAccess?.resolvePageConfigRuntime?.();
    if (pageConfigRuntime
      && typeof pageConfigRuntime.getBotSettingsInitial === 'function'
      && typeof pageConfigRuntime.getBotPresetDefinitions === 'function') {
      return {
        initial: pageConfigRuntime.getBotSettingsInitial(),
        presets: pageConfigRuntime.getBotPresetDefinitions(),
      };
    }

    if (typeof console !== 'undefined' && typeof console.warn === 'function') {
      console.warn('Bot settings runtime requires SettingsPageConfigRuntime.getBotSettingsInitial/getBotPresetDefinitions bootstrap.');
    }
    return null;
  }

  const pageBotConfig = resolvePageBotConfig();
  if (!pageBotConfig) {
    return;
  }
  const pageBotSettingsInitial = pageBotConfig.initial;
  const pageBotPresetDefinitions = pageBotConfig.presets;

  const mainModal = document.querySelector('[data-bot-settings-modal]');
  if (!mainModal) {
    return;
  }
  const parentModalEl = mainModal.closest('.modal');

  function resolveSettingsPageShellMethod(methodName) {
    const settingsPageShell = window.SettingsPageShell;
    if (settingsPageShell && typeof settingsPageShell[methodName] === 'function') {
      return settingsPageShell[methodName].bind(settingsPageShell);
    }
    const runtimeMethod = window.SettingsRuntimeAccess?.resolveRuntimeMethod?.('SettingsPageShell', methodName);
    return typeof runtimeMethod === 'function' ? runtimeMethod : null;
  }

  const showSettingsModal = resolveSettingsPageShellMethod('showModal');
  const hideSettingsModal = resolveSettingsPageShellMethod('hideModal');

  const templateModalEl = document.getElementById('botTemplateEditorModal');
  const templatesContainer = mainModal.querySelector('[data-bot-templates-container]');
  const createTemplateButton = mainModal.querySelector('[data-bot-template-create]');
  const ratingTemplatesContainer = mainModal.querySelector('[data-bot-rating-templates-container]');
  const ratingCreateButton = mainModal.querySelector('[data-bot-rating-template-create]');
  const saveButton = mainModal.querySelector('[data-bot-settings-save]');
  const statusEl = mainModal.querySelector('[data-bot-settings-status]');
  const unblockCooldownInput = mainModal.querySelector('[data-bot-unblock-cooldown]');

  const templateNameInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-name]') : null;
  const templateDescriptionInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-description]') : null;
  const templateStartMessageInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-start-message]') : null;
  const templateUnifiedFieldsInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-unified-fields]') : null;
  const templateFirstResponseTimeoutInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-first-response-timeout]') : null;
  const templateFirstResponseTimeoutMessageInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-first-response-message]') : null;
  const questionsContainer = templateModalEl ? templateModalEl.querySelector('[data-bot-questions-container]') : null;
  const addQuestionButtons = templateModalEl ? templateModalEl.querySelectorAll('[data-bot-question-add]') : [];
  const presetHintsEl = templateModalEl ? templateModalEl.querySelector('[data-bot-preset-hints]') : null;
  const presetEmptyAlertEl = templateModalEl ? templateModalEl.querySelector('[data-bot-preset-empty-alert]') : null;
  const presetSummaryCardEl = templateModalEl ? templateModalEl.querySelector('[data-bot-presets-summary]') : null;
  const templateStatusEl = templateModalEl ? templateModalEl.querySelector('[data-bot-template-status]') : null;
  const templateSaveButton = templateModalEl ? templateModalEl.querySelector('[data-bot-template-save]') : null;
  const templateCancelButton = templateModalEl ? templateModalEl.querySelector('[data-bot-template-cancel]') : null;

  const ratingModalEl = document.getElementById('botRatingTemplateModal');
  const ratingNameInput = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-name]') : null;
  const ratingDescriptionInput = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-description]') : null;
  const ratingPromptInput = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-prompt]') : null;
  const ratingScaleInput = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-scale]') : null;
  const ratingResponsesContainer = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-responses]') : null;
  const ratingStatusEl = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-status]') : null;
  const ratingSaveButton = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-save]') : null;
  const ratingCancelButton = ratingModalEl ? ratingModalEl.querySelector('[data-bot-rating-template-cancel]') : null;
  const questionDiagnosticEl = mainModal.querySelector('[data-bot-question-active-diagnostic]');
  const ratingDiagnosticEl = mainModal.querySelector('[data-bot-rating-active-diagnostic]');
  const legacyDiagnosticEl = mainModal.querySelector('[data-bot-legacy-diagnostic]');

  function setParentModalSuspended(suspended) {
    if (!(parentModalEl instanceof HTMLElement)) {
      return;
    }
    const nextState = Boolean(suspended);
    parentModalEl.classList.toggle('bot-settings-child-open', nextState);
    if (nextState) {
      parentModalEl.setAttribute('aria-hidden', 'true');
      parentModalEl.inert = true;
    } else {
      parentModalEl.removeAttribute('aria-hidden');
      parentModalEl.inert = false;
    }
  }

  function canManageChildModal(modalEl) {
    return modalEl instanceof HTMLElement
      && typeof showSettingsModal === 'function'
      && typeof hideSettingsModal === 'function';
  }

  function showChildModal(modalEl) {
    if (!canManageChildModal(modalEl)) {
      return false;
    }
    showSettingsModal(modalEl);
    return true;
  }

  function hideChildModal(modalEl) {
    if (!canManageChildModal(modalEl)) {
      return false;
    }
    hideSettingsModal(modalEl);
    return true;
  }

  const PRESET_GROUPS = {};
  let presetDefinitionsLoadingPromise = null;
  let botSettingsLoadingPromise = null;
  let presetContext = 'legacy';
  const QUESTION_ROUTE_PROBLEM_ID = 'problem';
  const DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES = 10;
  const DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE = 'Вы не ответили. Диалог был закрыт. При возникновении или актуализации вопросов создайте новое обращение.';
  const DASHBOARD_ATTRIBUTE_KEYS = [
    { value: 'business', label: 'Бизнес' },
    { value: 'direction', label: 'Направление обращения' },
    { value: 'bot_product', label: 'Продукт бота' },
    { value: 'issue_type', label: 'Тип обращения' },
    { value: 'location_type', label: 'Тип бизнеса' },
    { value: 'city', label: 'Город' },
    { value: 'location_name', label: 'Локация' },
  ];
  const DASHBOARD_ATTRIBUTE_KEY_SET = new Set(DASHBOARD_ATTRIBUTE_KEYS.map((item) => item.value));

  function applyPresetDefinitions(rawDefinitions) {
    Object.keys(PRESET_GROUPS).forEach((groupKey) => {
      delete PRESET_GROUPS[groupKey];
    });
    Object.entries(rawDefinitions || {}).forEach(([groupKey, groupValue]) => {
      if (!groupValue || typeof groupValue !== 'object') {
        return;
      }
      const label = typeof groupValue.label === 'string' ? groupValue.label.trim() : '';
      const fields = {};
      Object.entries(groupValue.fields || {}).forEach(([fieldKey, fieldValue]) => {
        if (!fieldValue || typeof fieldValue !== 'object') {
          return;
        }
        const fieldLabel = typeof fieldValue.label === 'string' ? fieldValue.label.trim() : '';
        const options = Array.isArray(fieldValue.options)
          ? fieldValue.options
              .map((value) => (typeof value === 'string' ? value.trim() : ''))
              .filter((value) => value)
          : [];
        const optionDependencies = fieldValue.option_dependencies && typeof fieldValue.option_dependencies === 'object'
          ? fieldValue.option_dependencies
          : {};
        const tree = fieldValue.tree && typeof fieldValue.tree === 'object' ? fieldValue.tree : null;
        fields[fieldKey] = {
          label: fieldLabel || fieldKey,
          options,
          optionDependencies,
          tree,
        };
      });
      PRESET_GROUPS[groupKey] = { label: label || groupKey, fields };
    });
  }

  applyPresetDefinitions(pageBotPresetDefinitions);

  const presetBusinessSelect = templateModalEl?.querySelector('[data-bot-preset-business]');
  if (presetBusinessSelect && window.BusinessQuestionnairePresets) {
    window.BusinessQuestionnairePresets.context({
      select: presetBusinessSelect,
      status: templateModalEl.querySelector('[data-bot-preset-context-status]'),
      container: templateModalEl.querySelector('[data-bot-preset-context]'),
      changed: value => { presetContext = value; },
      apply: definitions => { applyPresetDefinitions(definitions); rerenderPresetUi(); },
      legacy: () => { applyPresetDefinitions(pageBotPresetDefinitions); rerenderPresetUi(); void ensurePresetDefinitionsLoaded(); },
    });
  }

  function hasPresetDefinitions() {
    return Object.values(PRESET_GROUPS).some((groupValue) => Object.keys(groupValue.fields || {}).length);
  }

  function presetUnavailableMessage() {
    if (presetContext !== 'legacy') return presetContext === 'general'
      ? 'Готовые поля бизнеса недоступны для вопросника «Без бизнеса».'
      : 'У выбранного бизнеса нет доступных готовых полей. Настройте его локации или справочник.';
    return 'Готовые поля недоступны. Добавьте структуру локаций / готовые поля в разделе "Структура локаций".';
  }

  function presetLoadingMessage() {
    return '\u0417\u0430\u0433\u0440\u0443\u0436\u0430\u0435\u043c \u0433\u043e\u0442\u043e\u0432\u044b\u0435 \u043f\u043e\u043b\u044f \u0438\u0437 \u0441\u0442\u0440\u0443\u043a\u0442\u0443\u0440\u044b \u043b\u043e\u043a\u0430\u0446\u0438\u0439...';
  }

  function presetStatusMessage() {
    return presetDefinitionsLoadingPromise ? presetLoadingMessage() : presetUnavailableMessage();
  }

  function rerenderPresetUi() {
    renderPresetHints();
    if (templateModalEl && templateModalEl.classList.contains('show')) {
      renderQuestions();
    }
  }

  function ensurePresetDefinitionsLoaded(forceReload = false) {
    if (presetContext !== 'legacy') return Promise.resolve(hasPresetDefinitions());
    if (!forceReload && hasPresetDefinitions()) {
      return Promise.resolve(true);
    }
    if (!forceReload && presetDefinitionsLoadingPromise) {
      return presetDefinitionsLoadingPromise;
    }
    const fetchPageDataSection = window.SettingsRuntimeAccess?.fetchPageDataSection;
    if (typeof fetchPageDataSection !== 'function') {
      return Promise.resolve(hasPresetDefinitions());
    }
    presetDefinitionsLoadingPromise = fetchPageDataSection('channels', forceReload ? { force: true } : {})
      .then((section) => {
        const rawDefinitions = section && typeof section === 'object' ? section.botPresetDefinitions : null;
        if (presetContext === 'legacy' && rawDefinitions && typeof rawDefinitions === 'object') {
          applyPresetDefinitions(rawDefinitions);
        }
        return hasPresetDefinitions();
      })
      .catch((error) => {
        if (typeof console !== 'undefined' && typeof console.warn === 'function') {
          console.warn('Failed to load bot preset definitions from settings channels section.', error);
        }
        return false;
      })
      .finally(() => {
        presetDefinitionsLoadingPromise = null;
        rerenderPresetUi();
      });
    rerenderPresetUi();
    return presetDefinitionsLoadingPromise;
  }

  function ensureBotSettingsLoaded(forceReload = false) {
    if (!forceReload && botSettingsLoadingPromise) {
      return botSettingsLoadingPromise;
    }
    const fetchPageDataSection = window.SettingsRuntimeAccess?.fetchPageDataSection;
    if (typeof fetchPageDataSection !== 'function') {
      return Promise.resolve(false);
    }
    botSettingsLoadingPromise = fetchPageDataSection('channels', forceReload ? { force: true } : {})
      .then((section) => {
        const botSettings = section && typeof section === 'object' ? section.botSettings : null;
        if (!botSettings || typeof botSettings !== 'object') {
          return false;
        }
        initialState = botSettings;
        hydrateStateFrom(initialState);
        renderTemplates();
        renderRatingTemplates();
        return true;
      })
      .catch((error) => {
        if (typeof console !== 'undefined' && typeof console.warn === 'function') {
          console.warn('Failed to load current bot settings from settings channels section.', error);
        }
        return false;
      })
      .finally(() => {
        botSettingsLoadingPromise = null;
      });
    return botSettingsLoadingPromise;
  }

  async function refreshLinkedBotSettingsConsumers() {
    await ensureBotSettingsLoaded(true);
    const refreshTemplateCatalog = window.SettingsRuntimeAccess?.resolveRuntimeMethod?.(
      'SettingsChannelsShellRuntime',
      'refreshTemplateCatalog'
    );
    if (typeof refreshTemplateCatalog === 'function') {
      await refreshTemplateCatalog(true);
    }
  }

  const generatedQuestionIds = new Set();
  const generatedOptionIds = new Set();
  const generatedTemplateIds = new Set();
  const generatedRatingTemplateIds = new Set();

  function html(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
  }

  function normalizeStringArray(...args) { return questionModel.normalizeStringArray(...args); }

  function normalizeQuestionRequired(...args) { return questionModel.normalizeQuestionRequired(...args); }

  function buildDependencyValueHtml(...args) { return questionEditor.buildDependencyValueHtml(...args); }

  function buildDependencyPathsHtml(...args) { return questionEditor.buildDependencyPathsHtml(...args); }

  function buildOptionDependencyHtml(...args) { return questionEditor.buildOptionDependencyHtml(...args); }

  function highlightOptionRow(...args) { return questionEditor.highlightOptionRow(...args); }

  function showOptionDependencies(...args) { return questionEditor.showOptionDependencies(...args); }

  function presetExists(group, field) {
    return Boolean(
      PRESET_GROUPS[group] &&
        PRESET_GROUPS[group].fields &&
        PRESET_GROUPS[group].fields[field]
    );
  }

  function firstPreset() {
    for (const [groupKey, groupValue] of Object.entries(PRESET_GROUPS)) {
      const fieldKeys = Object.keys(groupValue.fields || {});
      if (fieldKeys.length) {
        return [groupKey, fieldKeys[0]];
      }
    }
    return [null, null];
  }

  function getPresetMeta(group, field) {
    const groupValue = PRESET_GROUPS[group];
    if (!groupValue) {
      return null;
    }
    const fieldValue = (groupValue.fields || {})[field];
    if (!fieldValue) {
      return null;
    }
    return {
      label: fieldValue.label || field,
      options: Array.isArray(fieldValue.options) ? fieldValue.options : [],
      groupLabel: groupValue.label || group,
      optionDependencies: fieldValue.optionDependencies && typeof fieldValue.optionDependencies === 'object'
        ? fieldValue.optionDependencies
        : {},
      tree: fieldValue.tree && typeof fieldValue.tree === 'object' ? fieldValue.tree : null,
    };
  }

  function encodePresetValue(group, field) {
    if (!group || !field) {
      return '';
    }
    return `${group}::${field}`;
  }

  function decodePresetValue(raw) {
    if (typeof raw !== 'string') {
      return [null, null];
    }
    const parts = raw.split('::');
    if (parts.length !== 2) {
      return [null, null];
    }
    return [parts[0] || null, parts[1] || null];
  }

  function generateQuestionId(...args) { return questionModel.generateQuestionId(...args); }

  function ensureQuestionId(...args) { return questionModel.ensureQuestionId(...args); }

  function generateOptionId(...args) { return questionModel.generateOptionId(...args); }

  function ensureOptionId(...args) { return questionModel.ensureOptionId(...args); }

  function cloneQuestionOptions(...args) { return questionModel.cloneQuestionOptions(...args); }

  function normalizeBindingKey(...args) { return questionModel.normalizeBindingKey(...args); }

  function normalizeEditableQuestionOptions(...args) { return questionModel.normalizeEditableQuestionOptions(...args); }

  function resolveQuestionBindingKey(...args) { return questionModel.resolveQuestionBindingKey(...args); }

  function isDashboardBindingKeyAllowed(...args) { return questionModel.isDashboardBindingKeyAllowed(...args); }

  function suggestDashboardBindingKey(...args) { return questionModel.suggestDashboardBindingKey(...args); }





  function resolveQuestionOptionLabels(...args) { return questionModel.resolveQuestionOptionLabels(...args); }

  function cloneQuestionRoutes(...args) { return questionModel.cloneQuestionRoutes(...args); }

  function resolveQuestionRouteChoices(...args) { return questionModel.resolveQuestionRouteChoices(...args); }

  function resolveQuestionRouteTargets(...args) { return questionModel.resolveQuestionRouteTargets(...args); }

  function sanitizeQuestionRoutes(...args) { return questionModel.sanitizeQuestionRoutes(...args); }

  function buildQuestionRouteControls(...args) { return questionEditor.buildQuestionRouteControls(...args); }

  function escapeSelectorValue(...args) { return questionEditor.escapeSelectorValue(...args); }

  function resolveQuestionEditorScrollContainer(...args) { return questionEditor.resolveQuestionEditorScrollContainer(...args); }

  function resolveFocusedQuestionFieldState(...args) { return questionEditor.resolveFocusedQuestionFieldState(...args); }

  function captureQuestionEditorUiState(...args) { return questionEditor.captureQuestionEditorUiState(...args); }

  function restoreQuestionEditorUiState(...args) { return questionEditor.restoreQuestionEditorUiState(...args); }

  function validateQuestionFlow(...args) { return questionModel.validateQuestionFlow(...args); }

  function generateTemplateId(...args) { return questionModel.generateTemplateId(...args); }

  function ensureTemplateId(...args) { return questionModel.ensureTemplateId(...args); }

  function generateRatingTemplateId(...args) { return ratingEditor.generateRatingTemplateId(...args); }

  function ensureRatingTemplateId(...args) { return ratingEditor.ensureRatingTemplateId(...args); }

  function cloneQuestion(...args) { return questionModel.cloneQuestion(...args); }

  function cloneTemplate(...args) { return questionModel.cloneTemplate(...args); }

  function defaultRatingPrompt(...args) { return ratingEditor.defaultRatingPrompt(...args); }

  function normalizeScale(...args) { return ratingEditor.normalizeScale(...args); }

  function normalizeRatingResponses(...args) { return ratingEditor.normalizeRatingResponses(...args); }

  function cloneRatingTemplate(...args) { return ratingEditor.cloneRatingTemplate(...args); }

  function normalizeQuestion(...args) { return questionModel.normalizeQuestion(...args); }

  function normalizeTemplate(...args) { return questionModel.normalizeTemplate(...args); }

  function defaultResponsesMap(...args) { return ratingEditor.defaultResponsesMap(...args); }

  function normalizeRatingTemplate(...args) { return ratingEditor.normalizeRatingTemplate(...args); }

  function normalizeCooldownMinutes(...args) { return questionModel.normalizeCooldownMinutes(...args); }

  function normalizeFirstResponseTimeoutMinutesValue(...args) { return questionModel.normalizeFirstResponseTimeoutMinutesValue(...args); }

  function normalizeFirstResponseTimeoutMessageValue(...args) { return questionModel.normalizeFirstResponseTimeoutMessageValue(...args); }

  function createLegacyDiagnostics(...args) { return questionModel.createLegacyDiagnostics(...args); }

  function normalizeSettings(...args) { return questionModel.normalizeSettings(...args); }

  const state = {
    templates: [],
    activeTemplateId: null,
    ratingTemplates: [],
    activeRatingTemplateId: null,
    unblockCooldownMinutes: 60,
    legacyDiagnostics: createLegacyDiagnostics(),
  };

  const editorState = {
    index: -1,
    templateId: null,
    name: '',
    description: '',
    startMessage: '',
    firstResponseTimeoutMinutes: DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES,
    firstResponseTimeoutMessage: DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE,
    unifiedFields: false,
    questionFlow: [],
  };

  const ratingEditorState = {
    index: -1,
    templateId: null,
    name: '',
    description: '',
    promptText: '',
    scaleSize: 5,
    responses: {},
  };

  let initialState = pageBotSettingsInitial && typeof pageBotSettingsInitial === 'object'
    ? pageBotSettingsInitial
    : {};

  function normalizeHydrationSource(source) {
    const candidate = source && typeof source === 'object' ? source : {};
    const hasNormalizedShape = Array.isArray(candidate.templates) || Array.isArray(candidate.ratingTemplates);
    if (!hasNormalizedShape) {
      return normalizeSettings(candidate);
    }
    return {
      templates: Array.isArray(candidate.templates) ? candidate.templates : [],
      activeTemplateId: typeof candidate.activeTemplateId === 'string' ? candidate.activeTemplateId.trim() : '',
      ratingTemplates: Array.isArray(candidate.ratingTemplates) ? candidate.ratingTemplates : [],
      activeRatingTemplateId: typeof candidate.activeRatingTemplateId === 'string'
        ? candidate.activeRatingTemplateId.trim()
        : '',
      unblockCooldownMinutes: normalizeCooldownMinutes(candidate),
      legacyDiagnostics: candidate.legacyDiagnostics || createLegacyDiagnostics(),
    };
  }

  function hydrateStateFrom(source) {
    const normalized = normalizeHydrationSource(source);
    state.templates = normalized.templates.map((template) => cloneTemplate(template));
    state.activeTemplateId = normalized.activeTemplateId;
    state.ratingTemplates = normalized.ratingTemplates.map((template) => cloneRatingTemplate(template));
    state.activeRatingTemplateId = normalized.activeRatingTemplateId;
    state.unblockCooldownMinutes = normalized.unblockCooldownMinutes;
    state.legacyDiagnostics = normalized.legacyDiagnostics || createLegacyDiagnostics();
    renderCooldownSettings();
    renderLegacyDiagnostics();
  }

  hydrateStateFrom(initialState);
  renderTemplates();
  renderRatingTemplates();
  renderCooldownSettings();

  if (unblockCooldownInput) {
    unblockCooldownInput.addEventListener('input', () => {
      const rawValue = Number.parseInt(unblockCooldownInput.value, 10);
      state.unblockCooldownMinutes = Number.isFinite(rawValue) && rawValue >= 0 ? rawValue : 0;
      setStatus('', false);
    });
  }

  function applyStatusAppearance(element, message, isError) {
    if (!(element instanceof HTMLElement)) {
      return;
    }
    element.classList.remove('text-muted', 'text-danger', 'text-success');
    if (!message) {
      element.textContent = '';
      element.classList.add('text-muted');
      return;
    }
    element.textContent = message;
    element.classList.add(isError ? 'text-danger' : 'text-success');
  }

  function setStatus(message, isError) {
    if (!statusEl) {
      return;
    }
    applyStatusAppearance(statusEl, message, isError);
    setTimeout(() => {
      if (statusEl.textContent === message) {
        applyStatusAppearance(statusEl, '', false);
      }
    }, 4000);
  }

  function renderCooldownSettings() {
    if (!unblockCooldownInput) {
      return;
    }
    unblockCooldownInput.value = state.unblockCooldownMinutes;
  }

  function setTemplateStatus(message, isError) {
    if (!templateStatusEl) {
      return;
    }
    applyStatusAppearance(templateStatusEl, message, isError);
    if (!isError) {
      setTimeout(() => {
        if (templateStatusEl.textContent === message) {
          applyStatusAppearance(templateStatusEl, '', false);
        }
      }, 3000);
    }
  }

  function renderActiveQuestionDiagnostic() {
    if (!questionDiagnosticEl) {
      return;
    }
    const activeTemplate = state.templates.find((template) => template.id === state.activeTemplateId) || null;
    if (!activeTemplate) {
      questionDiagnosticEl.textContent = 'Активный шаблон вопросов не выбран.';
      return;
    }
    const questionCount = Array.isArray(activeTemplate.questionFlow) ? activeTemplate.questionFlow.length : 0;
    const firstQuestion = questionCount > 0
      ? String(activeTemplate.questionFlow[0]?.text || '').trim()
      : '';
    const diagnosticParts = [
      `Активный шаблон: ${activeTemplate.name || 'Шаблон вопросов'}`,
      `id: ${activeTemplate.id}`,
      `вопросов: ${questionCount}`,
    ];
    if (firstQuestion) {
      diagnosticParts.push(`первый вопрос: ${firstQuestion}`);
    }
    questionDiagnosticEl.textContent = diagnosticParts.join(' | ');
  }

  function renderTemplates() {
    renderActiveQuestionDiagnostic();
    if (!templatesContainer) {
      return;
    }
    templatesContainer.innerHTML = '';
    if (!state.templates.length) {
      const placeholder = document.createElement('div');
      placeholder.className = 'alert alert-light border mb-0';
      placeholder.textContent = 'Шаблоны не созданы. Добавьте шаблон, чтобы настроить вопросы бота.';
      templatesContainer.appendChild(placeholder);
      return;
    }
    state.templates.forEach((template) => {
      const isActive = template.id === state.activeTemplateId;
      const card = document.createElement('div');
      card.className = `card template-card channels-template-card${isActive ? ' border-primary' : ''}`;
      card.dataset.templateId = template.id;
      const questionsCount = template.questionFlow.length;
      const descriptionHtml = template.description
        ? `<p class="small text-muted mb-1">${html(template.description)}</p>`
        : '';
      const summary = questionsCount === 1 ? '1 вопрос' : `${questionsCount} вопросов`;
      const activeBadgeHtml = isActive
        ? '<span class="channels-template-active" role="img" aria-label="Активный шаблон" title="Активный шаблон"><i class="bi bi-check-circle-fill" aria-hidden="true"></i></span>'
        : '';
      card.innerHTML = `
        <div class="card-body">
          <div class="channels-template-card__layout">
            <div class="template-card-header channels-template-card__header">
              <h6 class="mb-1">${html(template.name || 'Шаблон вопросов')}${activeBadgeHtml}</h6>
              ${descriptionHtml}
              <div class="small text-muted">${html(summary)}</div>
            </div>
            <div class="channels-template-card__controls">
              <div class="form-check form-switch channels-template-default">
                <input class="form-check-input" type="radio" name="bot-template-default" value="${html(template.id)}" ${template.id === state.activeTemplateId ? 'checked' : ''} data-bot-template-select>
                <label class="form-check-label small">Использовать по умолчанию</label>
              </div>
              <div class="d-flex template-card-actions channels-template-actions">
                <button class="btn btn-outline-primary channels-template-action" type="button" data-bot-template-edit aria-label="Редактировать" title="Редактировать"><i class="bi bi-pencil" aria-hidden="true"></i><span class="visually-hidden">Редактировать</span></button>
                <button class="btn btn-outline-secondary channels-template-action" type="button" data-bot-template-duplicate aria-label="Дублировать" title="Дублировать"><i class="bi bi-files" aria-hidden="true"></i><span class="visually-hidden">Дублировать</span></button>
                <button class="btn btn-outline-danger channels-template-action" type="button" data-bot-template-delete aria-label="Удалить" title="Удалить" ${state.templates.length === 1 ? 'disabled' : ''}><i class="bi bi-trash3" aria-hidden="true"></i><span class="visually-hidden">Удалить</span></button>
              </div>
            </div>
          </div>
        </div>
      `;
      templatesContainer.appendChild(card);
    });
  }

  function setRatingTemplateStatus(...args) { return ratingEditor.setRatingTemplateStatus(...args); }

  function ensureRatingEditorResponses(...args) { return ratingEditor.ensureRatingEditorResponses(...args); }

  function renderRatingEditorResponses(...args) { return ratingEditor.renderRatingEditorResponses(...args); }

  function renderRatingTemplates(...args) { return ratingEditor.renderRatingTemplates(...args); }

  function openRatingTemplateEditor(...args) { return ratingEditor.openRatingTemplateEditor(...args); }

  function saveRatingTemplateFromEditor(...args) { return ratingEditor.saveRatingTemplateFromEditor(...args); }

  function findRatingTemplateIndexById(...args) { return ratingEditor.findRatingTemplateIndexById(...args); }

  function duplicateRatingTemplate(...args) { return ratingEditor.duplicateRatingTemplate(...args); }

  function deleteRatingTemplate(...args) { return ratingEditor.deleteRatingTemplate(...args); }

  function renderPresetHints(...args) { return questionEditor.renderPresetHints(...args); }

  function renderActiveRatingDiagnostic(...args) { return ratingEditor.renderActiveRatingDiagnostic(...args); }

  function renderLegacyDiagnostics(...args) { return questionEditor.renderLegacyDiagnostics(...args); }

  function renderHiddenSummary(...args) { return questionEditor.renderHiddenSummary(...args); }

  function renderQuestionPreview(...args) { return questionEditor.renderQuestionPreview(...args); }

  function collectDependencyValues(...args) { return questionEditor.collectDependencyValues(...args); }

  function buildFilterSelectHtml(...args) { return questionEditor.buildFilterSelectHtml(...args); }

  function buildFilterControls(...args) { return questionEditor.buildFilterControls(...args); }

  function applyQuestionFilters(...args) { return questionEditor.applyQuestionFilters(...args); }


  function renderQuestions(...args) { return questionEditor.renderQuestions(...args); }




  function addQuestion(type) {
    if (type === 'preset' && !hasPresetDefinitions()) {
      setTemplateStatus(presetUnavailableMessage(), true);
      renderPresetHints();
      return;
    }
    const question = {
      id: generateQuestionId(),
      type: type === 'preset' ? 'preset' : (type === 'select' ? 'select' : (type === 'business' ? 'business' : 'custom')),
      text: type === 'business' ? 'Выберите бизнес' : '',
      order: editorState.questionFlow.length + 1,
      preset: null,
      options: type === 'select' ? [{ id: generateOptionId(), label: '' }] : [],
      presetUnavailable: false,
      excludedOptions: [],
      filterState: {},
      bindingKey: type === 'business' ? 'business' : '',
      includeInDashboard: type === 'business',
      routes: [],
      required: true,
    };
    if (question.type === 'preset') {
      const [group, field] = firstPreset();
      if (group && field) {
        question.preset = { group, field };
        const meta = getPresetMeta(group, field);
        if (meta) {
          question.text = meta.label;
        }
        question.required = true;
      } else {
        question.presetUnavailable = true;
      }
    }
    if (question.type === 'business') {
      editorState.unifiedFields = true;
    }
    editorState.questionFlow.push(question);
    renderQuestions();
    if (questionsContainer) {
      const cards = questionsContainer.querySelectorAll('.card');
      if (cards.length) {
        const lastCard = cards[cards.length - 1];
        const input = lastCard.querySelector('[data-bot-question-text]');
        if (input) {
          input.focus();
        }
      }
    }
  }

  function moveQuestion(index, delta) {
    const newIndex = index + delta;
    if (newIndex < 0 || newIndex >= editorState.questionFlow.length) {
      return;
    }
    const [item] = editorState.questionFlow.splice(index, 1);
    editorState.questionFlow.splice(newIndex, 0, item);
    renderQuestions();
  }

  function removeQuestion(index) {
    if (index < 0 || index >= editorState.questionFlow.length) {
      return;
    }
    editorState.questionFlow.splice(index, 1);
    renderQuestions();
  }

  function setQuestionType(index, type) {
    const question = editorState.questionFlow[index];
    if (!question) {
      return;
    }
    const wasBusiness = question.type === 'business';
    if (type === 'preset') {
      if (!hasPresetDefinitions()) {
        question.type = 'preset';
        question.presetUnavailable = true;
        question.required = true;
        question.filterState = {};
        setTemplateStatus(presetUnavailableMessage(), true);
        renderQuestions();
        return;
      }
      let group = question.preset && question.preset.group;
      let field = question.preset && question.preset.field;
      if (!presetExists(group, field)) {
        const [fallbackGroup, fallbackField] = firstPreset();
        group = fallbackGroup;
        field = fallbackField;
      }
      if (presetExists(group, field)) {
        const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
        question.preset = { group, field };
        question.type = 'preset';
        const meta = getPresetMeta(group, field);
        if (!question.text || (previousMeta && question.text === previousMeta.label)) {
          question.text = meta ? meta.label : question.text;
        }
        question.presetUnavailable = false;
        question.excludedOptions = Array.isArray(question.excludedOptions) ? question.excludedOptions : [];
        question.required = true;
        question.routes = sanitizeQuestionRoutes(question, index, meta);
      } else {
        question.type = 'preset';
        question.presetUnavailable = true;
        question.required = true;
        setTemplateStatus(presetUnavailableMessage(), true);
      }
    } else if (type === 'select') {
      question.type = 'select';
      question.preset = null;
      question.presetUnavailable = false;
      question.excludedOptions = [];
      question.required = true;
      question.options = Array.isArray(question.options) && question.options.length
        ? question.options
        : [{ id: generateOptionId(), label: '' }];
      if (wasBusiness) {
        question.bindingKey = '';
        question.includeInDashboard = false;
      }
      question.routes = sanitizeQuestionRoutes(question, index, null);
    } else if (type === 'business') {
      question.type = 'business';
      question.preset = null;
      question.presetUnavailable = false;
      question.excludedOptions = [];
      question.options = [];
      question.bindingKey = 'business';
      question.includeInDashboard = true;
      question.required = true;
      question.routes = [];
      if (!question.text) {
        question.text = 'Выберите бизнес';
      }
      editorState.unifiedFields = true;
    } else {
      question.type = 'custom';
      question.preset = null;
      question.presetUnavailable = false;
      question.excludedOptions = [];
      question.required = normalizeQuestionRequired(question.required, true);
      if (wasBusiness) {
        question.bindingKey = '';
        question.includeInDashboard = false;
      }
      question.routes = [];
    }
    question.filterState = {};
    renderQuestions();
  }

  function updateQuestionPreset(index, group, field) {
    const question = editorState.questionFlow[index];
    if (!question) {
      return;
    }
    if (!presetExists(group, field)) {
      setTemplateStatus('Выберите поле из списка готовых вариантов.', true);
      question.type = 'preset';
      question.presetUnavailable = true;
      renderQuestions();
      return;
    }
    const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
    question.preset = { group, field };
    question.type = 'preset';
    question.presetUnavailable = false;
    const meta = getPresetMeta(group, field);
    if (!question.text || (previousMeta && meta && question.text === previousMeta.label)) {
      question.text = meta ? meta.label : question.text;
    }
    question.excludedOptions = Array.isArray(question.excludedOptions) ? question.excludedOptions : [];
    question.required = true;
    question.routes = sanitizeQuestionRoutes(question, index, meta);
    question.filterState = {};
    renderQuestions();
  }

  function updateOptionVisibility(index, value, shouldHide) {
    const question = editorState.questionFlow[index];
    if (!question || typeof value !== 'string') {
      return;
    }
    const normalized = value.trim();
    if (!normalized) {
      return;
    }
    if (!Array.isArray(question.excludedOptions)) {
      question.excludedOptions = [];
    }
    const set = new Set(question.excludedOptions);
    if (shouldHide) {
      set.add(normalized);
    } else {
      set.delete(normalized);
    }
    question.excludedOptions = Array.from(set);
    const card = questionsContainer ? questionsContainer.querySelector(`.card[data-index="${index}"]`) : null;
    const meta = card && card.__questionMeta
      ? card.__questionMeta
      : question.preset
        ? getPresetMeta(question.preset.group, question.preset.field)
        : null;
    question.routes = sanitizeQuestionRoutes(question, index, meta);
    renderQuestions();
  }

  function resetState() {
    hydrateStateFrom(initialState);
    renderTemplates();
    renderRatingTemplates();
    setStatus('', false);
  }

  function serializeQuestion(...args) { return questionModel.serializeQuestion(...args); }



  async function persistBotSettingsPayload(payload) {
    const response = await fetch('/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ bot_settings: payload }),
    });
    let data = null;
    let rawText = '';
    try {
      data = await response.json();
    } catch (jsonError) {
      rawText = await response.text();
    }
    if (!response.ok || (data && data.success === false)) {
      throw new Error((data && data.error) || rawText || `HTTP ${response.status}`);
    }
    initialState = payload;
    hydrateStateFrom(initialState);
    renderTemplates();
    renderRatingTemplates();
  }



  async function saveBotSettings() {
    if (!saveButton) {
      return;
    }
    const payload = collectPayload();
    const errorMessage = validatePayload(payload);
    if (errorMessage) {
      setStatus(errorMessage, true);
      return;
    }
    saveButton.disabled = true;
    const originalText = saveButton.textContent;
    saveButton.textContent = 'Сохранение...';
    setStatus('Сохраняем настройки...', false);
    try {
      await persistBotSettingsPayload(payload);
      await refreshLinkedBotSettingsConsumers().catch(() => undefined);
      setStatus('Настройки сохранены.', false);
      hideSettingsModal(parentModalEl);
    } catch (error) {
      const message = error && error.message ? error.message : String(error);
      setStatus(`Ошибка сохранения: ${message}`, true);
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = originalText || 'Сохранить';
    }
  }

  function findTemplateIndexById(id) {
    return state.templates.findIndex((template) => template.id === id);
  }

  function duplicateTemplate(index) {
    if (index < 0 || index >= state.templates.length) {
      return;
    }
    const copy = cloneTemplate(state.templates[index], { preserveIds: false });
    copy.name = `${state.templates[index].name} (копия)`;
    state.templates.splice(index + 1, 0, copy);
    renderTemplates();
  }

  function deleteTemplate(index) {
    if (state.templates.length <= 1 || index < 0 || index >= state.templates.length) {
      return;
    }
    const removed = state.templates.splice(index, 1)[0];
    if (removed && removed.id === state.activeTemplateId) {
      state.activeTemplateId = state.templates.length ? state.templates[0].id : null;
    }
    renderTemplates();
  }

  if (parentModalEl) {
    parentModalEl.addEventListener('show.bs.modal', () => {
      resetState();
      ensureBotSettingsLoaded(true);
    });
  }

  if (createTemplateButton) {
    createTemplateButton.addEventListener('click', () => {
      openTemplateEditor(-1);
    });
  }

    if (templatesContainer) {
      templatesContainer.addEventListener('click', async (event) => {
        const card = event.target.closest('.card[data-template-id]');
        if (!card) {
          return;
        }
        const templateId = card.dataset.templateId;
      const index = findTemplateIndexById(templateId);
      if (index === -1) {
        return;
      }
      if (event.target.closest('[data-bot-template-edit]')) {
        openTemplateEditor(index);
        return;
      }
        if (event.target.closest('[data-bot-template-duplicate]')) {
          duplicateTemplate(index);
          return;
        }
        if (event.target.closest('[data-bot-template-delete]')) {
          if (state.templates.length <= 1) {
            return;
          }
          const confirmed = await showConfirmActionModal({
            title: 'Удаление шаблона',
            message: 'Удалить шаблон вопросов?',
            confirmText: 'Удалить',
            confirmVariant: 'danger',
            icon: '🗑️',
          });
          if (confirmed) {
            deleteTemplate(index);
          }
          return;
        }
      });

    templatesContainer.addEventListener('change', (event) => {
      const input = event.target.closest('[data-bot-template-select]');
      if (!input) {
        return;
      }
      const templateId = input.value;
      if (state.templates.some((template) => template.id === templateId)) {
        state.activeTemplateId = templateId;
        renderTemplates();
      }
    });
  }

  addQuestionButtons.forEach((button) => {
    button.addEventListener('click', () => {
      const type = button.dataset.type || button.getAttribute('data-type');
      addQuestion(type === 'preset' ? 'preset' : (type === 'select' ? 'select' : (type === 'business' ? 'business' : 'custom')));
    });
  });

  if (questionsContainer) {
    questionsContainer.addEventListener('input', (event) => {
      const input = event.target.closest('[data-bot-question-text]');
      if (input) {
        const card = input.closest('.card[data-index]');
        if (!card) {
          return;
        }
        const index = Number.parseInt(card.dataset.index, 10);
        if (Number.isFinite(index) && editorState.questionFlow[index]) {
          const question = editorState.questionFlow[index];
          question.text = input.value;
          const meta = card.__questionMeta || (question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null);
          renderQuestionPreview(card, question, meta);
        }
        return;
      }
      const optionsInput = event.target.closest('[data-bot-question-select-options]');
      if (optionsInput) {
        const card = optionsInput.closest('.card[data-index]');
        if (!card) {
          return;
        }
        const index = Number.parseInt(card.dataset.index, 10);
        const question = Number.isFinite(index) ? editorState.questionFlow[index] : null;
        if (!question) {
          return;
        }
        const lines = optionsInput.value.split(/\r?\n/).map((value) => value.trim()).filter((value) => value);
        const previous = Array.isArray(question.options) ? question.options : [];
        const previousByLabel = new Map();
        previous.forEach((option) => {
          const key = typeof option?.label === 'string' ? option.label.trim().toLowerCase() : '';
          if (key && option.id && !previousByLabel.has(key)) {
            previousByLabel.set(key, option.id);
          }
        });
        const usedIds = new Set();
        question.options = lines.map((label, optionIndex) => ({
          id: (() => {
            const indexed = previous[optionIndex] && previous[optionIndex].id ? previous[optionIndex].id : '';
            if (indexed && !usedIds.has(indexed) && previous[optionIndex]?.label?.trim() === label) {
              usedIds.add(indexed);
              return indexed;
            }
            const byLabel = previousByLabel.get(label.toLowerCase());
            if (byLabel && !usedIds.has(byLabel)) {
              usedIds.add(byLabel);
              return byLabel;
            }
            const generated = generateOptionId();
            usedIds.add(generated);
            return generated;
          })(),
          label,
        }));
        question.routes = sanitizeQuestionRoutes(question, index, null);
        renderQuestionPreview(card, question, null);
        return;
      }
      const bindingInput = event.target.closest('[data-bot-question-binding]');
      if (bindingInput) {
        const card = bindingInput.closest('.card[data-index]');
        if (!card) {
          return;
        }
        const index = Number.parseInt(card.dataset.index, 10);
        const question = Number.isFinite(index) ? editorState.questionFlow[index] : null;
        if (question) {
          const normalized = question.includeInDashboard
            ? (isDashboardBindingKeyAllowed(bindingInput.value) ? bindingInput.value.trim() : '')
            : normalizeBindingKey(bindingInput.value);
          question.bindingKey = normalized;
          if (bindingInput instanceof HTMLInputElement || bindingInput instanceof HTMLSelectElement) {
            bindingInput.value = normalized;
          }
        }
      }
    });

    questionsContainer.addEventListener('change', (event) => {
      const card = event.target.closest('.card[data-index]');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (!Number.isFinite(index)) {
        return;
      }
      const filterSelect = event.target.closest('[data-bot-question-filter]');
      if (filterSelect) {
        const filterKey = filterSelect.dataset.botQuestionFilter;
        const value = filterSelect.value || '';
        if (filterKey === 'business') {
          card.dataset.filterBusiness = value;
        } else if (filterKey === 'location_type') {
          card.dataset.filterLocationType = value;
        }
        const question = editorState.questionFlow[index];
        if (question) {
          if (!question.filterState || typeof question.filterState !== 'object') {
            question.filterState = {};
          }
          if (filterKey === 'business' || filterKey === 'location_type') {
            question.filterState[filterKey] = value;
          }
        }
        applyQuestionFilters(card);
        return;
      }
      const optionsTextarea = event.target.closest('[data-bot-question-select-options]');
      if (optionsTextarea) {
        const question = editorState.questionFlow[index];
        if (question) {
          question.routes = sanitizeQuestionRoutes(question, index, null);
        }
        renderQuestions();
        return;
      }
      const typeSelect = event.target.closest('[data-bot-question-type]');
      if (typeSelect) {
        setQuestionType(index, typeSelect.value === 'preset' ? 'preset' : (typeSelect.value === 'select' ? 'select' : 'custom'));
        return;
      }
      const bindingSelect = event.target.closest('[data-bot-question-binding]');
      if (bindingSelect) {
        const question = editorState.questionFlow[index];
        if (question) {
          question.bindingKey = question.includeInDashboard
            ? (isDashboardBindingKeyAllowed(bindingSelect.value) ? bindingSelect.value.trim() : '')
            : normalizeBindingKey(bindingSelect.value);
        }
        return;
      }
      const dashboardToggle = event.target.closest('[data-bot-question-dashboard]');
      if (dashboardToggle) {
        const question = editorState.questionFlow[index];
        if (question) {
          question.includeInDashboard = dashboardToggle.checked;
          if (dashboardToggle.checked) {
            question.bindingKey = suggestDashboardBindingKey(question);
          }
        }
        renderQuestions();
        return;
      }
      const requiredToggle = event.target.closest('[data-bot-question-required]');
      if (requiredToggle) {
        const question = editorState.questionFlow[index];
        if (question) {
          question.required = requiredToggle.checked;
          const meta = card.__questionMeta || (question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null);
          renderQuestionPreview(card, question, meta);
        }
        return;
      }
      const presetSelect = event.target.closest('[data-bot-question-preset]');
      if (presetSelect) {
        const [group, field] = decodePresetValue(presetSelect.value);
        if (group && field) {
          updateQuestionPreset(index, group, field);
        }
        return;
      }
      const routeSelect = event.target.closest('[data-bot-question-route-target]');
      if (routeSelect) {
        const question = editorState.questionFlow[index];
        if (question) {
          const valueId = routeSelect.dataset.botQuestionRouteTarget || '';
          const targetId = routeSelect.value || '';
          const routes = sanitizeQuestionRoutes(question, index, card.__questionMeta || null)
            .filter((route) => route.valueId !== valueId);
          if (valueId && targetId) {
            routes.push({ valueId, nextQuestionId: targetId });
          }
          question.routes = routes;
        }
        return;
      }
      const optionCheckbox = event.target.closest('[data-bot-question-option]');
      if (optionCheckbox) {
        updateOptionVisibility(index, optionCheckbox.value, !optionCheckbox.checked);
        return;
      }
    });

    questionsContainer.addEventListener('click', (event) => {
      const infoButton = event.target.closest('[data-bot-question-option-info]');
      if (infoButton) {
        const card = infoButton.closest('.card[data-index]');
        if (card) {
          const row = infoButton.closest('[data-bot-question-option-row]');
          const optionValue = row && typeof row.dataset.optionValue === 'string' ? row.dataset.optionValue : '';
          showOptionDependencies(card, optionValue);
        }
        return;
      }
      const button = event.target.closest('[data-bot-question-action]');
      if (!button) {
        return;
      }
      const card = button.closest('.card[data-index]');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (!Number.isFinite(index)) {
        return;
      }
      const action = button.dataset.botQuestionAction;
      if (action === 'move-up') {
        moveQuestion(index, -1);
      } else if (action === 'move-down') {
        moveQuestion(index, 1);
      } else if (action === 'remove') {
        removeQuestion(index);
      }
    });
  }

  function buildDashboardBindingOptions(...args) { return questionEditor.buildDashboardBindingOptions(...args); }

  function buildBindingEditorHtml(...args) { return questionEditor.buildBindingEditorHtml(...args); }

  function openTemplateEditor(...args) { return questionEditor.openTemplateEditor(...args); }

  function saveTemplateFromEditor(...args) { return questionEditor.saveTemplateFromEditor(...args); }

  function collectPayload(...args) { return questionEditor.collectPayload(...args); }

  function validatePayload(...args) { return questionEditor.validatePayload(...args); }

  if (templateSaveButton) {
    templateSaveButton.addEventListener('click', async () => {
      await saveTemplateFromEditor();
    });
  }

  if (templateCancelButton && templateModalEl) {
    templateCancelButton.addEventListener('click', () => {
      hideChildModal(templateModalEl);
    });
  }

  if (templateModalEl) {
    templateModalEl.addEventListener('show.bs.modal', () => {
      setParentModalSuspended(true);
    });
    templateModalEl.addEventListener('hidden.bs.modal', () => {
      setParentModalSuspended(false);
      setTemplateStatus('', false);
    });
  }

  if (ratingCreateButton) {
    ratingCreateButton.addEventListener('click', () => {
      openRatingTemplateEditor(-1);
    });
  }

    if (ratingTemplatesContainer) {
      ratingTemplatesContainer.addEventListener('click', async (event) => {
        const card = event.target.closest('.card[data-rating-template-id]');
        if (!card) {
          return;
        }
        const templateId = card.dataset.ratingTemplateId;
      const index = findRatingTemplateIndexById(templateId);
      if (index === -1) {
        return;
      }
      if (event.target.closest('[data-bot-rating-template-edit]')) {
        openRatingTemplateEditor(index);
        return;
      }
        if (event.target.closest('[data-bot-rating-template-duplicate]')) {
          duplicateRatingTemplate(index);
          return;
        }
        if (event.target.closest('[data-bot-rating-template-delete]')) {
          if (state.ratingTemplates.length <= 1) {
            return;
          }
          const confirmed = await showConfirmActionModal({
            title: 'Удаление шаблона',
            message: 'Удалить шаблон оценок?',
            confirmText: 'Удалить',
            confirmVariant: 'danger',
            icon: '🗑️',
          });
          if (confirmed) {
            deleteRatingTemplate(index);
          }
        }
      });

    ratingTemplatesContainer.addEventListener('change', (event) => {
      const input = event.target.closest('[data-bot-rating-template-select]');
      if (!input) {
        return;
      }
      const templateId = input.value;
      if (state.ratingTemplates.some((template) => template.id === templateId)) {
        state.activeRatingTemplateId = templateId;
        renderRatingTemplates();
      }
    });
  }

  if (ratingSaveButton) {
    ratingSaveButton.addEventListener('click', () => {
      saveRatingTemplateFromEditor();
    });
  }

  if (ratingCancelButton && ratingModalEl) {
    ratingCancelButton.addEventListener('click', () => {
      hideChildModal(ratingModalEl);
    });
  }

  if (ratingModalEl) {
    ratingModalEl.addEventListener('show.bs.modal', () => {
      setParentModalSuspended(true);
    });
    ratingModalEl.addEventListener('hidden.bs.modal', () => {
      setParentModalSuspended(false);
      setRatingTemplateStatus('', false);
    });
  }

  if (ratingScaleInput) {
    ratingScaleInput.addEventListener('input', () => {
      ratingEditorState.scaleSize = normalizeScale(ratingScaleInput.value);
      renderRatingEditorResponses();
    });
    ratingScaleInput.addEventListener('blur', () => {
      ratingEditorState.scaleSize = normalizeScale(ratingScaleInput.value);
      ratingScaleInput.value = ratingEditorState.scaleSize;
      renderRatingEditorResponses();
    });
  }

  if (ratingResponsesContainer) {
    ratingResponsesContainer.addEventListener('input', (event) => {
      const textarea = event.target.closest('[data-bot-rating-template-response]');
      if (!textarea) {
        return;
      }
      const value = Number.parseInt(textarea.dataset.value, 10);
      if (!Number.isFinite(value)) {
        return;
      }
      ratingEditorState.responses[String(value)] = textarea.value;
    });
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      saveBotSettings();
    });
  }
})();

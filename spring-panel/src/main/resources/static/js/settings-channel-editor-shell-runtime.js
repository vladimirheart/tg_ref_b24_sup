(function () {
  if (window.SettingsChannelEditorShellRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = {
      channelEditorModalEl: document.getElementById('channelEditorModal'),
      channelEditorNameInput: document.getElementById('channelEditorName'),
      channelEditorActiveInput: document.getElementById('channelEditorIsActive'),
      channelEditorStatusLabel: document.querySelector('[data-channel-editor-status-label]'),
      channelEditorQuestionSelect: document.getElementById('channelEditorQuestionTemplate'),
      channelEditorRatingSelect: document.getElementById('channelEditorRatingTemplate'),
      channelEditorAutoSelect: document.getElementById('channelEditorAutoTemplate'),
      channelEditorAutoCloseFollowUpProjectInput: document.getElementById('channelEditorAutoCloseFollowUpProject'),
      channelEditorSupportChatInput: document.getElementById('channelEditorSupportChatId'),
      channelEditorBroadcastChannelInput: document.getElementById('channelEditorBroadcastChannelId'),
      channelEditorPanelNotifFirstResponseOverdueInput: document.getElementById('channelEditorPanelNotifFirstResponseOverdue'),
      channelEditorPanelNotifTargetModeInput: document.getElementById('channelEditorPanelNotifTargetMode'),
      channelEditorPanelNotifDeliveryModeInput: document.getElementById('channelEditorPanelNotifDeliveryMode'),
      channelEditorPanelNotifDepartmentInput: document.getElementById('channelEditorPanelNotifDepartment'),
      channelEditorPanelNotifEmployeesInput: document.getElementById('channelEditorPanelNotifEmployees'),
      channelEditorPanelNotifExcludeInput: document.getElementById('channelEditorPanelNotifExclude'),
      channelEditorAssignmentModeInput: document.getElementById('channelEditorAssignmentMode'),
      channelEditorAssignmentStrategyBlock: document.getElementById('channelEditorAssignmentStrategyBlock'),
      channelEditorAssignmentStrategyInput: document.getElementById('channelEditorAssignmentStrategy'),
      channelEditorAssignmentSingleOperatorBlock: document.getElementById('channelEditorAssignmentSingleOperatorBlock'),
      channelEditorAssignmentSingleOperatorInput: document.getElementById('channelEditorAssignmentSingleOperator'),
      channelEditorAssignmentPoolOperatorsBlock: document.getElementById('channelEditorAssignmentPoolOperatorsBlock'),
      channelEditorAssignmentPoolOperatorsInput: document.getElementById('channelEditorAssignmentPoolOperators'),
      channelEditorAssignmentDepartmentBlock: document.getElementById('channelEditorAssignmentDepartmentBlock'),
      channelEditorAssignmentDepartmentInput: document.getElementById('channelEditorAssignmentDepartment'),
      channelEditorAssignmentAssignResponsibleInput: document.getElementById('channelEditorAssignmentAssignResponsible'),
      channelEditorWorkingHoursStartInput: document.getElementById('channelEditorWorkingHoursStart'),
      channelEditorWorkingHoursEndInput: document.getElementById('channelEditorWorkingHoursEnd'),
      channelEditorScheduleToGroupInput: document.getElementById('channelEditorScheduleToGroup'),
      channelEditorScheduleToChannelInput: document.getElementById('channelEditorScheduleToChannel'),
      channelEditorScheduleSimultaneousInput: document.getElementById('channelEditorScheduleSimultaneous'),
      channelEditorPlatformEl: document.querySelector('[data-channel-editor-platform]'),
      channelEditorBotEl: document.querySelector('[data-channel-editor-bot]'),
      channelEditorMetaEl: document.querySelector('[data-channel-editor-meta]'),
      channelEditorHintEl: document.querySelector('[data-channel-editor-hint]'),
      channelEditorSupportHint: document.querySelector('[data-channel-editor-support-hint]'),
      channelEditorVkBtn: document.getElementById('channelEditorVkButton'),
      channelEditorTelegramBaseUrlBlock: document.getElementById('channelEditorTelegramBaseUrlBlock'),
      channelEditorTelegramBaseUrlInput: document.getElementById('channelEditorTelegramBaseUrl'),
      channelEditorMaxWebhookBlock: document.getElementById('channelEditorMaxWebhookBlock'),
      channelEditorMaxWebhookSecretInput: document.getElementById('channelEditorMaxWebhookSecret'),
      channelEditorNewTokenInput: document.getElementById('channelEditorNewToken'),
      channelEditorTestTargetSelect: document.getElementById('channelEditorTestTarget'),
      channelEditorTestMessageInput: document.getElementById('channelEditorTestMessage'),
    };

    function getRegistry() {
      const registry = typeof options.getChannelsRegistry === 'function' ? options.getChannelsRegistry() : null;
      return registry instanceof Map ? registry : new Map();
    }

    function getChannelEditorState() {
      const state = typeof options.getChannelEditorState === 'function' ? options.getChannelEditorState() : null;
      return state && typeof state === 'object'
        ? state
        : { channelId: null, tokenVisible: false };
    }

    function getQuestionTemplates() {
      return typeof options.getQuestionTemplates === 'function' ? options.getQuestionTemplates() || [] : [];
    }

    function getRatingTemplates() {
      return typeof options.getRatingTemplates === 'function' ? options.getRatingTemplates() || [] : [];
    }

    function getAutoActionTemplates() {
      return typeof options.getAutoActionTemplates === 'function' ? options.getAutoActionTemplates() || [] : [];
    }

    function getDefaultQuestionTemplateId() {
      return typeof options.getDefaultQuestionTemplateId === 'function' ? options.getDefaultQuestionTemplateId() || '' : '';
    }

    function getDefaultRatingTemplateId() {
      return typeof options.getDefaultRatingTemplateId === 'function' ? options.getDefaultRatingTemplateId() || '' : '';
    }

    function getDefaultAutoActionTemplateId() {
      return typeof options.getDefaultAutoActionTemplateId === 'function' ? options.getDefaultAutoActionTemplateId() || '' : '';
    }

    function getTemplateSummaryBuilders() {
      return typeof options.getTemplateSummaryBuilders === 'function' ? options.getTemplateSummaryBuilders() || {} : {};
    }

    function getChannelSummaries() {
      return typeof options.getChannelEditorSummaries === 'function' ? options.getChannelEditorSummaries() || {} : {};
    }

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function parseDeliverySettings(raw) {
      return typeof options.parseDeliverySettings === 'function' ? options.parseDeliverySettings(raw) : {};
    }

    function parsePlatformConfig(raw) {
      return typeof options.parsePlatformConfig === 'function' ? options.parsePlatformConfig(raw) : {};
    }

    function normalizeChannelWorkingHours(raw) {
      return typeof options.normalizeChannelWorkingHours === 'function' ? options.normalizeChannelWorkingHours(raw) : { start_hour: 9, end_hour: 18 };
    }

    function defaultChannelPanelNotifications() {
      return typeof options.defaultChannelPanelNotifications === 'function'
        ? options.defaultChannelPanelNotifications()
        : { routing: {}, events: {} };
    }

    function defaultChannelAssignmentRouting() {
      return typeof options.defaultChannelAssignmentRouting === 'function'
        ? options.defaultChannelAssignmentRouting()
        : {
            enabled: false,
            mode: 'disabled',
            assignResponsibleOnCreate: false,
            strategy: 'round_robin',
            operatorUsername: '',
            operatorUsernames: [],
            department: '',
          };
    }

    function buildTemplateOptions(templates, selectedId) {
      return typeof options.buildTemplateOptions === 'function' ? options.buildTemplateOptions(templates, selectedId) : '';
    }

    function escapeHtml(value) {
      if (typeof options.escapeHtml === 'function') {
        return options.escapeHtml(value);
      }
      return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
    }

    function formatBotLabel(channel) {
      return typeof options.formatBotLabel === 'function' ? options.formatBotLabel(channel) : '';
    }

    function updateBotRefreshControl(isTelegram, isLoading) {
      if (typeof options.updateBotRefreshControl === 'function') {
        options.updateBotRefreshControl(isTelegram, isLoading);
      }
    }

    function updateChannelEditorToken() {
      if (typeof options.updateChannelEditorToken === 'function') {
        options.updateChannelEditorToken();
      }
    }

    function updateTestRecipientState() {
      if (typeof options.updateTestRecipientState === 'function') {
        options.updateTestRecipientState();
      }
    }

    function setTestStatus(message) {
      if (typeof options.setTestStatus === 'function') {
        options.setTestStatus(message);
      }
    }

    function updateBotStatusLabel(status, startedAt) {
      if (typeof options.updateBotStatusLabel === 'function') {
        options.updateBotStatusLabel(status, startedAt);
      }
    }

    function updateBotControls(running) {
      if (typeof options.updateBotControls === 'function') {
        options.updateBotControls(running);
      }
    }

    function refreshBotStatus(channelId) {
      if (typeof options.refreshBotStatus === 'function') {
        options.refreshBotStatus(channelId);
      }
    }

    function getChannelAssignmentRoutingCatalog() {
      const catalog = typeof options.getChannelAssignmentRoutingCatalog === 'function'
        ? options.getChannelAssignmentRoutingCatalog()
        : null;
      return catalog && typeof catalog === 'object'
        ? catalog
        : { operators: [], departments: [], orgStructure: { nodes: [] } };
    }

    function updateChannelEditorStatusLabel(isActive) {
      if (elements.channelEditorStatusLabel) {
        elements.channelEditorStatusLabel.textContent = isActive ? 'Канал активен' : 'Канал выключен';
      }
    }

    function updateChannelEditorSummary(field, value) {
      const builder = getTemplateSummaryBuilders()[field];
      const summaryEl = getChannelSummaries()[field];
      if (typeof builder === 'function' && summaryEl) {
        summaryEl.textContent = builder(value || '');
      }
    }

    function configureChannelEditorSelect(selectEl, templates, selectedId, fallbackId, field) {
      if (!selectEl) {
        return;
      }
      selectEl.innerHTML = buildTemplateOptions(templates, selectedId || fallbackId);
      selectEl.disabled = !templates.length;
      updateChannelEditorSummary(field, selectEl.value || '');
    }

    function updateSupportChatHint(value, optionsArg = {}) {
      if (!elements.channelEditorSupportHint) {
        return;
      }
      const pending = Boolean(optionsArg.pending);
      if (pending && value) {
        elements.channelEditorSupportHint.textContent = `Будет сохранён ID: ${value}`;
        return;
      }
      if (value) {
        elements.channelEditorSupportHint.textContent = `Текущее значение: ${value}`;
        return;
      }
      elements.channelEditorSupportHint.textContent = 'ID группы не задан. Добавьте бота в чат или укажите значение вручную.';
    }

    function populateChannelNotificationSelection(input, rawValues) {
      if (!input) return;
      const values = Array.isArray(rawValues) ? rawValues : [];
      const value = input.dataset.employeeValueFormat === 'username-json'
        ? JSON.stringify(values) : values.join(', ');
      input.defaultValue = value;
      input.value = value;
    }

    function updateChannelPanelNotificationRoutingState() {
      const targetMode = String(elements.channelEditorPanelNotifTargetModeInput?.value || 'all_operators').trim().toLowerCase();
      const usesDepartment = targetMode !== 'all_operators';
      const usesEmployees = targetMode === 'employees_only';
      const usesExclude = targetMode === 'department_except';
      if (elements.channelEditorPanelNotifDepartmentInput) {
        elements.channelEditorPanelNotifDepartmentInput.disabled = !usesDepartment;
      }
      if (elements.channelEditorPanelNotifEmployeesInput) {
        elements.channelEditorPanelNotifEmployeesInput.disabled = !usesEmployees;
      }
      if (elements.channelEditorPanelNotifExcludeInput) {
        elements.channelEditorPanelNotifExcludeInput.disabled = !usesExclude;
      }
    }

    function normalizeAssignmentUsername(value) {
      const username = String(value || '');
      return username.trim() ? username.toLowerCase() : '';
    }

    function buildAssignmentOperatorLabel(operator) {
      const username = String(operator?.username || '');
      const displayName = String(operator?.display_name || '').trim();
      const department = String(operator?.department || '').trim();
      const identity = displayName && username
        ? `${displayName} (@${username})`
        : (displayName || (username ? `@${username}` : 'Неизвестный оператор'));
      return department ? `${identity} · ${department}` : identity;
    }

    function populateAssignmentOperatorSelect(selectEl, operators, selectedValue, optionsArg = {}) {
      if (!(selectEl instanceof HTMLSelectElement)) {
        return;
      }
      const selectedUsername = normalizeAssignmentUsername(selectedValue);
      const includePlaceholder = optionsArg.includePlaceholder !== false;
      const placeholderLabel = optionsArg.placeholderLabel || 'Выберите оператора';
      const optionItems = (Array.isArray(operators) ? operators : []).filter(operator => normalizeAssignmentUsername(operator?.username));
      const hasSelectedOperator = optionItems.some(operator => normalizeAssignmentUsername(operator?.username) === selectedUsername);
      const nodes = [];
      if (includePlaceholder) {
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = optionItems.length ? placeholderLabel : 'Нет доступных операторов';
        placeholder.defaultSelected = !hasSelectedOperator;
        nodes.push(placeholder);
      }
      for (const operator of optionItems) {
        const option = document.createElement('option');
        // A DOM property keeps CR/LF in account keys; HTML attribute parsing normalizes them.
        option.value = normalizeAssignmentUsername(operator.username);
        option.textContent = buildAssignmentOperatorLabel(operator);
        option.defaultSelected = option.value === selectedUsername;
        nodes.push(option);
      }
      selectEl.replaceChildren(...nodes);
      selectEl.value = hasSelectedOperator ? selectedUsername : '';
      selectEl.dataset.assignmentOptionsAvailable = optionItems.length ? '1' : '0';
      selectEl.disabled = optionItems.length === 0;
    }

    function populateAssignmentPoolSelect(selectEl, operators, selectedValues) {
      if (!(selectEl instanceof HTMLSelectElement)) {
        return;
      }
      const selectedUsernames = new Set(Array.isArray(selectedValues)
        ? selectedValues.map(normalizeAssignmentUsername).filter(Boolean) : []);
      const optionItems = (Array.isArray(operators) ? operators : []).filter(operator => normalizeAssignmentUsername(operator?.username));
      const nodes = optionItems.map(operator => {
        const option = document.createElement('option');
        option.value = normalizeAssignmentUsername(operator.username);
        option.textContent = buildAssignmentOperatorLabel(operator);
        option.defaultSelected = selectedUsernames.has(option.value);
        return option;
      });
      selectEl.replaceChildren(...nodes);
      selectEl.dataset.assignmentOptionsAvailable = optionItems.length ? '1' : '0';
      selectEl.disabled = optionItems.length === 0;
    }

    function populateAssignmentDepartmentSelect(selectEl, departments, selectedValue) {
      if (!(selectEl instanceof HTMLSelectElement)) {
        return;
      }
      const selectedDepartment = String(selectedValue || '').trim();
      const optionItems = Array.isArray(departments) ? departments : [];
      const hasSelectedDepartment = optionItems.some((department) => String(department || '').trim() === selectedDepartment);
      const optionMarkup = optionItems.map((department) => {
        const value = String(department || '').trim();
        if (!value) {
          return '';
        }
        const selectedAttr = value === selectedDepartment ? ' selected' : '';
        return `<option value="${escapeHtml(value)}"${selectedAttr}>${escapeHtml(value)}</option>`;
      }).join('');
      const placeholderSelected = !hasSelectedDepartment ? ' selected' : '';
      const placeholderLabel = optionItems.length ? 'Выберите отдел' : 'Отделы не найдены';
      selectEl.innerHTML = `<option value=""${placeholderSelected}>${escapeHtml(placeholderLabel)}</option>${optionMarkup}`;
      selectEl.dataset.assignmentOptionsAvailable = optionItems.length ? '1' : '0';
      selectEl.disabled = optionItems.length === 0;
    }

    function toggleAssignmentField(blockEl, inputEl, visible) {
      if (blockEl) {
        blockEl.classList.toggle('d-none', !visible);
      }
      if (inputEl instanceof HTMLElement) {
        const hasOptions = !(inputEl instanceof HTMLSelectElement) || inputEl.dataset.assignmentOptionsAvailable !== '0';
        inputEl.toggleAttribute('disabled', !visible || !hasOptions);
      }
    }

    function updateChannelAssignmentRoutingState() {
      const mode = String(elements.channelEditorAssignmentModeInput?.value || 'disabled').trim().toLowerCase();
      const normalizedMode = ['single_operator', 'operator_pool', 'department_queue'].includes(mode)
        ? mode
        : 'disabled';
      const usesStrategy = normalizedMode === 'operator_pool' || normalizedMode === 'department_queue';
      const usesSingleOperator = normalizedMode === 'single_operator';
      const usesOperatorPool = normalizedMode === 'operator_pool';
      const usesDepartment = normalizedMode === 'department_queue';

      toggleAssignmentField(
        elements.channelEditorAssignmentStrategyBlock,
        elements.channelEditorAssignmentStrategyInput,
        usesStrategy,
      );
      toggleAssignmentField(
        elements.channelEditorAssignmentSingleOperatorBlock,
        elements.channelEditorAssignmentSingleOperatorInput,
        usesSingleOperator,
      );
      toggleAssignmentField(
        elements.channelEditorAssignmentPoolOperatorsBlock,
        elements.channelEditorAssignmentPoolOperatorsInput,
        usesOperatorPool,
      );
      toggleAssignmentField(
        elements.channelEditorAssignmentDepartmentBlock,
        elements.channelEditorAssignmentDepartmentInput,
        usesDepartment,
      );

      if (elements.channelEditorAssignmentAssignResponsibleInput) {
        elements.channelEditorAssignmentAssignResponsibleInput.disabled = normalizedMode === 'disabled';
        if (normalizedMode === 'disabled') {
          elements.channelEditorAssignmentAssignResponsibleInput.checked = false;
        }
      }
    }

    function populateChannelEditorShell(channel) {
      const editorState = getChannelEditorState();
      if (elements.channelEditorNameInput) {
        elements.channelEditorNameInput.value = channel.channel_name || '';
      }
      if (elements.channelEditorActiveInput) {
        elements.channelEditorActiveInput.checked = Boolean(channel.is_active);
        updateChannelEditorStatusLabel(elements.channelEditorActiveInput.checked);
      }
      if (elements.channelEditorPlatformEl) {
        elements.channelEditorPlatformEl.textContent = channel.platformLabel;
      }
      if (elements.channelEditorBotEl) {
        elements.channelEditorBotEl.textContent = formatBotLabel(channel);
      }
      updateBotRefreshControl(channel.platform === 'telegram');
      editorState.tokenVisible = false;
      updateChannelEditorToken();
      if (elements.channelEditorNewTokenInput) {
        elements.channelEditorNewTokenInput.value = '';
      }
      if (elements.channelEditorMetaEl) {
        elements.channelEditorMetaEl.textContent = `ID канала: #${channel.id}`;
      }
      configureChannelEditorSelect(
        elements.channelEditorQuestionSelect,
        getQuestionTemplates(),
        channel.question_template_id,
        getDefaultQuestionTemplateId(),
        'question_template_id',
      );
      configureChannelEditorSelect(
        elements.channelEditorRatingSelect,
        getRatingTemplates(),
        channel.rating_template_id,
        getDefaultRatingTemplateId(),
        'rating_template_id',
      );
      configureChannelEditorSelect(
        elements.channelEditorAutoSelect,
        getAutoActionTemplates(),
        channel.auto_action_template_id,
        getDefaultAutoActionTemplateId(),
        'auto_action_template_id',
      );
      const platformConfig = parsePlatformConfig(channel.platform_config);
      const supportChatValue = channel.support_chat_id ?? '';
      const deliverySettings = parseDeliverySettings(channel.delivery_settings);
      const workingHours = normalizeChannelWorkingHours(deliverySettings.working_hours);
      if (elements.channelEditorAutoCloseFollowUpProjectInput) {
        const projectId = Number.parseInt(deliverySettings.auto_close_follow_up_project_id, 10);
        elements.channelEditorAutoCloseFollowUpProjectInput.value = Number.isSafeInteger(projectId) && projectId > 0
          ? String(projectId)
          : '';
      }
      const panelNotifications = channel.questions_cfg?.panelNotifications || defaultChannelPanelNotifications();
      const assignmentRouting = channel.questions_cfg?.assignmentRouting || defaultChannelAssignmentRouting();
      const panelRouting = panelNotifications.routing || defaultChannelPanelNotifications().routing;
      const panelEvents = panelNotifications.events || defaultChannelPanelNotifications().events;
      const assignmentCatalog = getChannelAssignmentRoutingCatalog();
      const assignmentOperators = Array.isArray(assignmentCatalog.operators) ? assignmentCatalog.operators : [];
      const assignmentDepartments = Array.isArray(assignmentCatalog.departments) ? assignmentCatalog.departments : [];
      if (elements.channelEditorSupportChatInput) {
        elements.channelEditorSupportChatInput.value = supportChatValue;
      }
      if (elements.channelEditorBroadcastChannelInput) {
        elements.channelEditorBroadcastChannelInput.value = deliverySettings.broadcast_channel_id || '';
      }
      if (elements.channelEditorPanelNotifFirstResponseOverdueInput) {
        elements.channelEditorPanelNotifFirstResponseOverdueInput.checked = Boolean(panelEvents.firstResponseOverdue);
      }
      if (elements.channelEditorPanelNotifTargetModeInput) {
        elements.channelEditorPanelNotifTargetModeInput.value = panelRouting.targetMode || 'all_operators';
      }
      if (elements.channelEditorPanelNotifDeliveryModeInput) {
        elements.channelEditorPanelNotifDeliveryModeInput.value = panelRouting.deliveryMode || 'all';
      }
      if (elements.channelEditorPanelNotifDepartmentInput) {
        elements.channelEditorPanelNotifDepartmentInput.value = panelRouting.department || '';
      }
      populateChannelNotificationSelection(elements.channelEditorPanelNotifEmployeesInput, panelRouting.employeeUsernames);
      populateChannelNotificationSelection(elements.channelEditorPanelNotifExcludeInput, panelRouting.excludeUsernames);
      populateAssignmentOperatorSelect(
        elements.channelEditorAssignmentSingleOperatorInput,
        assignmentOperators,
        assignmentRouting.operatorUsername || '',
        { includePlaceholder: true, placeholderLabel: 'Выберите оператора' },
      );
      populateAssignmentPoolSelect(
        elements.channelEditorAssignmentPoolOperatorsInput,
        assignmentOperators,
        assignmentRouting.operatorUsernames || [],
      );
      populateAssignmentDepartmentSelect(
        elements.channelEditorAssignmentDepartmentInput,
        assignmentDepartments,
        assignmentRouting.department || '',
      );
      if (elements.channelEditorAssignmentModeInput) {
        elements.channelEditorAssignmentModeInput.value = assignmentRouting.enabled
          ? (assignmentRouting.mode || 'disabled')
          : 'disabled';
      }
      if (elements.channelEditorAssignmentStrategyInput) {
        elements.channelEditorAssignmentStrategyInput.value = assignmentRouting.strategy || 'round_robin';
      }
      if (elements.channelEditorAssignmentAssignResponsibleInput) {
        elements.channelEditorAssignmentAssignResponsibleInput.checked = Boolean(
          assignmentRouting.enabled && assignmentRouting.assignResponsibleOnCreate
        );
      }
      if (elements.channelEditorWorkingHoursStartInput) {
        elements.channelEditorWorkingHoursStartInput.value = String(workingHours.start_hour);
      }
      if (elements.channelEditorWorkingHoursEndInput) {
        elements.channelEditorWorkingHoursEndInput.value = String(workingHours.end_hour);
      }
      updateChannelPanelNotificationRoutingState();
      updateChannelAssignmentRoutingState();
      if (elements.channelEditorScheduleToGroupInput) {
        elements.channelEditorScheduleToGroupInput.checked = Boolean(deliverySettings.schedule_to_group ?? true);
      }
      if (elements.channelEditorScheduleToChannelInput) {
        elements.channelEditorScheduleToChannelInput.checked = Boolean(deliverySettings.schedule_to_channel);
      }
      if (elements.channelEditorScheduleSimultaneousInput) {
        elements.channelEditorScheduleSimultaneousInput.checked = Boolean(deliverySettings.schedule_simultaneous);
      }
      if (elements.channelEditorMaxWebhookSecretInput) {
        const maxSecret = String(
          platformConfig.secret
            ?? platformConfig.webhook_secret
            ?? platformConfig.webhookSecret
            ?? ''
        ).trim();
        elements.channelEditorMaxWebhookSecretInput.value = maxSecret;
      }
      if (elements.channelEditorTelegramBaseUrlInput) {
        const telegramBaseUrl = String(
          platformConfig.base_url
            ?? platformConfig.baseUrl
            ?? platformConfig.api_base_url
            ?? platformConfig.apiBaseUrl
            ?? platformConfig.telegram_api_base_url
            ?? platformConfig.telegramApiBaseUrl
            ?? ''
        ).trim().replace(/\/+$/, '').replace(/\/bot$/, '');
        elements.channelEditorTelegramBaseUrlInput.value = telegramBaseUrl;
      }
      if (typeof options.applyRouteToInputs === 'function') {
        options.applyRouteToInputs('channel', channel.network_route);
      }
      updateSupportChatHint(supportChatValue);
      if (elements.channelEditorTestMessageInput) {
        elements.channelEditorTestMessageInput.value = 'Проверка отправки из панели';
      }
      if (elements.channelEditorTestTargetSelect) {
        elements.channelEditorTestTargetSelect.value = 'group';
      }
      updateTestRecipientState();
      setTestStatus('—');
      if (elements.channelEditorVkBtn) {
        elements.channelEditorVkBtn.classList.toggle('d-none', channel.platform !== 'vk');
      }
      if (elements.channelEditorTelegramBaseUrlBlock) {
        elements.channelEditorTelegramBaseUrlBlock.classList.toggle('d-none', channel.platform !== 'telegram');
      }
      if (elements.channelEditorMaxWebhookBlock) {
        elements.channelEditorMaxWebhookBlock.classList.toggle('d-none', channel.platform !== 'max');
      }
      if (elements.channelEditorHintEl) {
        if (channel.platform === 'vk') {
          const groupId = platformConfig.group_id ?? platformConfig.groupId ?? '';
          const callbackUrl = groupId
            ? `${window.location.origin}/webhooks/vk/${groupId}`
            : `${window.location.origin}/webhooks/vk/<group_id>`;
          elements.channelEditorHintEl.textContent = `VK Callback URL: ${callbackUrl}`;
        } else if (channel.platform === 'max') {
          elements.channelEditorHintEl.textContent = 'MAX работает через long polling: отдельный webhook endpoint не требуется.';
        } else if (channel.platform === 'telegram') {
          elements.channelEditorHintEl.textContent = 'Если используете Bot API mirror или self-hosted endpoint, укажите его base URL ниже. Для обычного Telegram оставьте поле пустым.';
        } else {
          elements.channelEditorHintEl.textContent = 'Изменения вступят в силу сразу после сохранения.';
        }
      }
      updateBotStatusLabel('загрузка');
      updateBotControls(false);
      refreshBotStatus(channel.id);
    }

    function refreshChannelEditorIfOpen() {
      if (!elements.channelEditorModalEl) {
        return;
      }
      const editorState = getChannelEditorState();
      const isVisible = elements.channelEditorModalEl.classList.contains('show');
      if (!isVisible || editorState.channelId === null) {
        return;
      }
      const channel = getRegistry().get(String(editorState.channelId));
      if (channel && typeof options.populateChannelEditor === 'function') {
        options.populateChannelEditor(channel);
      }
    }

    function prepareChannelEditorSettingsTrigger(trigger) {
      const id = trigger instanceof HTMLElement ? trigger.getAttribute('data-channel-edit') : null;
      const channel = id ? getRegistry().get(String(id)) : null;
      if (!channel) {
        popup('Канал не найден.');
        return false;
      }
      if (typeof options.populateChannelEditor === 'function') {
        options.populateChannelEditor(channel);
      }
      return true;
    }

    function resetChannelEditorSettingsModal() {
      const editorState = getChannelEditorState();
      editorState.channelId = null;
      editorState.tokenVisible = false;
    }

    function handleActiveInputChange() {
      updateChannelEditorStatusLabel(Boolean(elements.channelEditorActiveInput?.checked));
    }

    function handleQuestionTemplateChange() {
      updateChannelEditorSummary('question_template_id', elements.channelEditorQuestionSelect?.value || '');
    }

    function handleRatingTemplateChange() {
      updateChannelEditorSummary('rating_template_id', elements.channelEditorRatingSelect?.value || '');
    }

    function handleAutoTemplateChange() {
      updateChannelEditorSummary('auto_action_template_id', elements.channelEditorAutoSelect?.value || '');
    }

    function handleSupportChatInput() {
      const value = elements.channelEditorSupportChatInput?.value?.trim() || '';
      if (value) {
        updateSupportChatHint(value, { pending: true });
        return;
      }
      const editorState = getChannelEditorState();
      const channel = editorState.channelId !== null
        ? getRegistry().get(String(editorState.channelId))
        : null;
      const actualValue = channel && (channel.support_chat_id ?? '');
      updateSupportChatHint(actualValue || '');
    }

    return {
      updateChannelEditorStatusLabel,
      updateChannelEditorSummary,
      configureChannelEditorSelect,
      updateSupportChatHint,
      updateChannelPanelNotificationRoutingState,
      updateChannelAssignmentRoutingState,
      populateChannelEditorShell,
      refreshChannelEditorIfOpen,
      prepareChannelEditorSettingsTrigger,
      resetChannelEditorSettingsModal,
      handleActiveInputChange,
      handleQuestionTemplateChange,
      handleRatingTemplateChange,
      handleAutoTemplateChange,
      handleSupportChatInput,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelEditorShellRuntime) {
        return window.__settingsChannelEditorShellRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        prepareChannelEditorSettingsTrigger: runtime.prepareChannelEditorSettingsTrigger,
        resetChannelEditorSettingsModal: runtime.resetChannelEditorSettingsModal,
      });
      window.__settingsChannelEditorShellRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelEditorShellRuntime = Object.freeze(api);
}());

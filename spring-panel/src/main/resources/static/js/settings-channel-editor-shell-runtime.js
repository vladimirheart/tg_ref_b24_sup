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
      channelEditorSupportChatInput: document.getElementById('channelEditorSupportChatId'),
      channelEditorBroadcastChannelInput: document.getElementById('channelEditorBroadcastChannelId'),
      channelEditorPanelNotifFirstResponseOverdueInput: document.getElementById('channelEditorPanelNotifFirstResponseOverdue'),
      channelEditorPanelNotifTargetModeInput: document.getElementById('channelEditorPanelNotifTargetMode'),
      channelEditorPanelNotifDeliveryModeInput: document.getElementById('channelEditorPanelNotifDeliveryMode'),
      channelEditorPanelNotifDepartmentInput: document.getElementById('channelEditorPanelNotifDepartment'),
      channelEditorPanelNotifEmployeesInput: document.getElementById('channelEditorPanelNotifEmployees'),
      channelEditorPanelNotifExcludeInput: document.getElementById('channelEditorPanelNotifExclude'),
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

    function buildTemplateOptions(templates, selectedId) {
      return typeof options.buildTemplateOptions === 'function' ? options.buildTemplateOptions(templates, selectedId) : '';
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
      const panelNotifications = channel.questions_cfg?.panelNotifications || defaultChannelPanelNotifications();
      const panelRouting = panelNotifications.routing || defaultChannelPanelNotifications().routing;
      const panelEvents = panelNotifications.events || defaultChannelPanelNotifications().events;
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
      if (elements.channelEditorPanelNotifEmployeesInput) {
        elements.channelEditorPanelNotifEmployeesInput.value = Array.isArray(panelRouting.employeeUsernames)
          ? panelRouting.employeeUsernames.join(', ')
          : '';
      }
      if (elements.channelEditorPanelNotifExcludeInput) {
        elements.channelEditorPanelNotifExcludeInput.value = Array.isArray(panelRouting.excludeUsernames)
          ? panelRouting.excludeUsernames.join(', ')
          : '';
      }
      if (elements.channelEditorWorkingHoursStartInput) {
        elements.channelEditorWorkingHoursStartInput.value = String(workingHours.start_hour);
      }
      if (elements.channelEditorWorkingHoursEndInput) {
        elements.channelEditorWorkingHoursEndInput.value = String(workingHours.end_hour);
      }
      updateChannelPanelNotificationRoutingState();
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
      window.prepareChannelEditorSettingsTrigger = function prepareChannelEditorSettingsTrigger(trigger) {
        return runtime.prepareChannelEditorSettingsTrigger(trigger);
      };
      window.resetChannelEditorSettingsModal = function resetChannelEditorSettingsModal() {
        runtime.resetChannelEditorSettingsModal();
      };
      window.__settingsChannelEditorShellRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelEditorShellRuntime = Object.freeze(api);
}());

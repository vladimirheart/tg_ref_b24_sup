(function () {
  if (window.SettingsChannelEditorPersistenceRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = {
      channelEditorNameInput: document.getElementById('channelEditorName'),
      channelEditorActiveInput: document.getElementById('channelEditorIsActive'),
      channelEditorQuestionSelect: document.getElementById('channelEditorQuestionTemplate'),
      channelEditorRatingSelect: document.getElementById('channelEditorRatingTemplate'),
      channelEditorAutoSelect: document.getElementById('channelEditorAutoTemplate'),
      channelEditorSupportChatInput: document.getElementById('channelEditorSupportChatId'),
      channelEditorBroadcastChannelInput: document.getElementById('channelEditorBroadcastChannelId'),
      channelEditorPanelNotifTargetModeInput: document.getElementById('channelEditorPanelNotifTargetMode'),
      channelEditorWorkingHoursStartInput: document.getElementById('channelEditorWorkingHoursStart'),
      channelEditorWorkingHoursEndInput: document.getElementById('channelEditorWorkingHoursEnd'),
      channelEditorScheduleToGroupInput: document.getElementById('channelEditorScheduleToGroup'),
      channelEditorScheduleToChannelInput: document.getElementById('channelEditorScheduleToChannel'),
      channelEditorScheduleSimultaneousInput: document.getElementById('channelEditorScheduleSimultaneous'),
      channelEditorTelegramBaseUrlInput: document.getElementById('channelEditorTelegramBaseUrl'),
      channelEditorMaxWebhookSecretInput: document.getElementById('channelEditorMaxWebhookSecret'),
      channelEditorNewTokenInput: document.getElementById('channelEditorNewToken'),
      channelEditorDeleteBtn: document.getElementById('channelEditorDeleteBtn'),
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

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function confirmDialog(message) {
      if (typeof options.confirmDialog === 'function') {
        return options.confirmDialog(message);
      }
      return window.confirm(message);
    }

    function parseDeliverySettings(raw) {
      return typeof options.parseDeliverySettings === 'function' ? options.parseDeliverySettings(raw) : {};
    }

    function parsePlatformConfig(raw) {
      return typeof options.parsePlatformConfig === 'function' ? options.parsePlatformConfig(raw) : {};
    }

    function parseQuestionsCfg(raw) {
      return typeof options.parseQuestionsCfg === 'function' ? options.parseQuestionsCfg(raw) : {};
    }

    function normalizeChannelWorkingHours(raw) {
      return typeof options.normalizeChannelWorkingHours === 'function'
        ? options.normalizeChannelWorkingHours(raw)
        : { start_hour: 9, end_hour: 18 };
    }

    function collectRouteFromInputs(scope) {
      return typeof options.collectRouteFromInputs === 'function' ? options.collectRouteFromInputs(scope) : { mode: 'direct' };
    }

    function buildQuestionsCfgPayload(existingQuestionsCfg) {
      if (typeof options.buildQuestionsCfgPayload === 'function') {
        const payload = options.buildQuestionsCfgPayload(existingQuestionsCfg);
        return payload && typeof payload === 'object' ? payload : existingQuestionsCfg;
      }
      return existingQuestionsCfg;
    }

    async function reloadChannels() {
      if (typeof options.loadChannels === 'function') {
        await options.loadChannels();
      }
    }

    function requestCloseModal(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function createValidationError(message) {
      const error = new Error(message);
      error.name = 'ChannelEditorValidationError';
      return error;
    }

    function buildChannelSaveBody(id) {
      const channelName = elements.channelEditorNameInput?.value?.trim() || '';
      if (!channelName) {
        throw createValidationError('Укажите название для панели.');
      }

      const newToken = elements.channelEditorNewTokenInput?.value?.trim() || '';
      const body = {
        channel_name: channelName,
        is_active: elements.channelEditorActiveInput?.checked ? 1 : 0,
      };

      if (newToken) {
        body.token = newToken;
      }

      if (elements.channelEditorSupportChatInput) {
        const supportValue = elements.channelEditorSupportChatInput.value.trim();
        body.support_chat_id = supportValue || null;
      }

      const currentChannel = getRegistry().get(String(id));
      const channelRoute = collectRouteFromInputs('channel');
      const workingHoursStart = Number.parseInt(elements.channelEditorWorkingHoursStartInput?.value || '9', 10);
      const workingHoursEnd = Number.parseInt(elements.channelEditorWorkingHoursEndInput?.value || '18', 10);
      if (channelRoute.mode === 'profile' && !(channelRoute.profile_ids || []).length) {
        throw createValidationError('Для бота в режиме «Профиль» выберите хотя бы один профиль failover.');
      }
      if (channelRoute.mode === 'proxy' && channelRoute.proxy.scheme === 'vless' && !String(channelRoute.proxy.token || '').trim()) {
        throw createValidationError('Для VLESS-маршрута бота заполните token.');
      }
      if (!Number.isFinite(workingHoursStart) || workingHoursStart < 0 || workingHoursStart > 23) {
        throw createValidationError('Начало рабочего времени должно быть в диапазоне от 0 до 23.');
      }
      if (!Number.isFinite(workingHoursEnd) || workingHoursEnd < 1 || workingHoursEnd > 24) {
        throw createValidationError('Конец рабочего времени должен быть в диапазоне от 1 до 24.');
      }
      if (workingHoursEnd <= workingHoursStart) {
        throw createValidationError('Конец рабочего времени должен быть позже начала.');
      }

      const workingHours = normalizeChannelWorkingHours({
        start_hour: workingHoursStart,
        end_hour: workingHoursEnd,
      });
      const existingDeliverySettings = parseDeliverySettings(currentChannel?.delivery_settings);
      body.delivery_settings = {
        ...existingDeliverySettings,
        broadcast_channel_id: elements.channelEditorBroadcastChannelInput?.value?.trim() || null,
        schedule_to_group: elements.channelEditorScheduleToGroupInput?.checked ?? true,
        schedule_to_channel: elements.channelEditorScheduleToChannelInput?.checked ?? false,
        schedule_simultaneous: elements.channelEditorScheduleSimultaneousInput?.checked ?? false,
        network_route: channelRoute,
        working_hours: workingHours,
      };

      const currentPlatform = String(currentChannel?.platform || '').toLowerCase();
      const currentPlatformConfig = parsePlatformConfig(currentChannel?.platform_config);
      if (currentPlatform === 'telegram') {
        const telegramBaseUrl = elements.channelEditorTelegramBaseUrlInput?.value?.trim() || '';
        body.platform_config = {
          ...currentPlatformConfig,
          base_url: telegramBaseUrl || null,
        };
      } else if (currentPlatform === 'max') {
        const maxSecret = elements.channelEditorMaxWebhookSecretInput?.value?.trim() || '';
        body.platform_config = {
          ...currentPlatformConfig,
          secret: maxSecret || null,
        };
      }

      const existingQuestionsCfg = parseQuestionsCfg(currentChannel?.questions_cfg);
      if (elements.channelEditorQuestionSelect && !elements.channelEditorQuestionSelect.disabled) {
        body.question_template_id = elements.channelEditorQuestionSelect.value || '';
      }
      if (elements.channelEditorRatingSelect && !elements.channelEditorRatingSelect.disabled) {
        body.rating_template_id = elements.channelEditorRatingSelect.value || '';
      }
      if (elements.channelEditorAutoSelect && !elements.channelEditorAutoSelect.disabled) {
        body.auto_action_template_id = elements.channelEditorAutoSelect.value || '';
      }
      body.questions_cfg = buildQuestionsCfgPayload(existingQuestionsCfg);

      return body;
    }

    async function parseJsonSafe(response) {
      try {
        return await response.json();
      } catch (error) {
        return {};
      }
    }

    async function saveChannelRequest(id, method, body) {
      const resp = await fetch(`/api/channels/${id}`, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      return { resp, data: await parseJsonSafe(resp) };
    }

    async function saveChannel(id) {
      let body;
      try {
        body = buildChannelSaveBody(id);
      } catch (error) {
        if (error?.name === 'ChannelEditorValidationError') {
          popup(error.message);
          return;
        }
        popup('Ошибка сохранения: ' + error.message);
        return;
      }

      try {
        let result = await saveChannelRequest(id, 'POST', body);
        if (result.resp.status === 405) {
          result = await saveChannelRequest(id, 'POST', body);
        }
        if (result.resp.status === 405) {
          result = await saveChannelRequest(id, 'PUT', body);
        }
        if (!result.resp.ok || result.data.success === false) {
          throw new Error(result.data.error || ('HTTP ' + result.resp.status));
        }
        popup('Настройки канала сохранены.');
        if (elements.channelEditorNewTokenInput) {
          elements.channelEditorNewTokenInput.value = '';
        }
        await reloadChannels();
      } catch (error) {
        popup('Ошибка сохранения: ' + error.message);
      }
    }

    async function removeChannel(id) {
      if (!confirmDialog('Удалить канал? Действие необратимо.')) {
        return;
      }
      try {
        const resp = await fetch(`/api/channels/${id}`, { method: 'DELETE' });
        const data = await parseJsonSafe(resp);
        if (!resp.ok || data.success === false) {
          throw new Error(data.error || ('HTTP ' + resp.status));
        }
        await reloadChannels();
        if (getChannelEditorState().channelId === id) {
          requestCloseModal(elements.channelEditorDeleteBtn);
        }
      } catch (error) {
        popup('Ошибка удаления: ' + error.message);
      }
    }

    function handleSaveClick() {
      const channelId = getChannelEditorState().channelId;
      if (channelId !== null) {
        saveChannel(channelId);
      }
    }

    function handleDeleteClick() {
      const channelId = getChannelEditorState().channelId;
      if (channelId !== null) {
        removeChannel(channelId);
      }
    }

    return {
      saveChannel,
      removeChannel,
      handleSaveClick,
      handleDeleteClick,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelEditorPersistenceRuntime) {
        return window.__settingsChannelEditorPersistenceRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsChannelEditorPersistenceRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelEditorPersistenceRuntime = Object.freeze(api);
}());

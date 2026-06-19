(function () {
  if (window.SettingsChannelsCatalogRuntime) {
    return;
  }

  function escapeHtml(value) {
    if (typeof window.escapeHtml === 'function') {
      return window.escapeHtml(value);
    }
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function createRuntime(options = {}) {
    const state = {
      vkWebhook: {
        channelId: null,
        groupId: null,
        confirmationToken: '',
        secret: '',
      },
    };

    const elements = {
      channelsBody: document.getElementById('channelsBody'),
      addChannelFormEl: document.getElementById('addChannelForm'),
      vkWebhookFormEl: document.getElementById('vkWebhookForm'),
    };

    function getRegistry() {
      const registry = typeof options.getChannelsRegistry === 'function' ? options.getChannelsRegistry() : null;
      return registry instanceof Map ? registry : new Map();
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
        return;
      }
      if (typeof options.showPopup === 'function') {
        options.showPopup(type === 'error' ? 'Ошибка: ' + message : message);
        return;
      }
      console.log(message);
    }

    function requestCloseModal(source) {
      if (typeof options.requestSettingsModalClose === 'function') {
        options.requestSettingsModalClose(source);
      }
    }

    function parseDeliverySettings(raw) {
      return typeof options.parseDeliverySettings === 'function' ? options.parseDeliverySettings(raw) : {};
    }

    function parsePlatformConfig(raw) {
      return typeof options.parsePlatformConfig === 'function' ? options.parsePlatformConfig(raw) : {};
    }

    function normalizeNetworkRoute(raw, allowDefault) {
      return typeof options.normalizeNetworkRoute === 'function' ? options.normalizeNetworkRoute(raw, allowDefault) : raw;
    }

    function parseQuestionsCfg(raw) {
      return typeof options.parseQuestionsCfg === 'function' ? options.parseQuestionsCfg(raw) : {};
    }

    function sanitizeTemplateId(raw, map, fallback) {
      return typeof options.sanitizeTemplateId === 'function' ? options.sanitizeTemplateId(raw, map, fallback) : (fallback || '');
    }

    function formatBotLabel(channel) {
      return typeof options.formatBotLabel === 'function' ? options.formatBotLabel(channel) : '';
    }

    function updateChannelsManageOverview() {
      if (typeof options.updateChannelsManageOverview === 'function') {
        options.updateChannelsManageOverview();
      }
    }

    async function refreshAllBotRuntimeStatuses(silent) {
      if (typeof options.refreshAllBotRuntimeStatuses === 'function') {
        await options.refreshAllBotRuntimeStatuses(silent);
      }
    }

    function refreshChannelEditorIfOpen() {
      if (typeof options.refreshChannelEditorIfOpen === 'function') {
        options.refreshChannelEditorIfOpen();
      }
    }

    function getQuestionTemplates() {
      if (typeof options.getQuestionTemplates === 'function') {
        const value = options.getQuestionTemplates();
        return Array.isArray(value) ? value : [];
      }
      return Array.isArray(options.questionTemplates) ? options.questionTemplates : [];
    }

    function getRatingTemplates() {
      if (typeof options.getRatingTemplates === 'function') {
        const value = options.getRatingTemplates();
        return Array.isArray(value) ? value : [];
      }
      return Array.isArray(options.ratingTemplates) ? options.ratingTemplates : [];
    }

    function getAutoActionTemplates() {
      if (typeof options.getAutoActionTemplates === 'function') {
        const value = options.getAutoActionTemplates();
        return Array.isArray(value) ? value : [];
      }
      return Array.isArray(options.autoActionTemplates) ? options.autoActionTemplates : [];
    }

    function getQuestionTemplateMap() {
      if (typeof options.getQuestionTemplateMap === 'function') {
        const value = options.getQuestionTemplateMap();
        return value instanceof Map ? value : new Map();
      }
      return options.questionTemplateMap instanceof Map ? options.questionTemplateMap : new Map();
    }

    function getRatingTemplateMap() {
      if (typeof options.getRatingTemplateMap === 'function') {
        const value = options.getRatingTemplateMap();
        return value instanceof Map ? value : new Map();
      }
      return options.ratingTemplateMap instanceof Map ? options.ratingTemplateMap : new Map();
    }

    function getAutoActionTemplateMap() {
      if (typeof options.getAutoActionTemplateMap === 'function') {
        const value = options.getAutoActionTemplateMap();
        return value instanceof Map ? value : new Map();
      }
      return options.autoActionTemplateMap instanceof Map ? options.autoActionTemplateMap : new Map();
    }

    function getDefaultQuestionTemplateId() {
      if (typeof options.getDefaultQuestionTemplateId === 'function') {
        return options.getDefaultQuestionTemplateId() || '';
      }
      return options.defaultQuestionTemplateId || '';
    }

    function getDefaultRatingTemplateId() {
      if (typeof options.getDefaultRatingTemplateId === 'function') {
        return options.getDefaultRatingTemplateId() || '';
      }
      return options.defaultRatingTemplateId || '';
    }

    function getDefaultAutoActionTemplateId() {
      if (typeof options.getDefaultAutoActionTemplateId === 'function') {
        return options.getDefaultAutoActionTemplateId() || '';
      }
      return options.defaultAutoActionTemplateId || '';
    }

    function buildTemplateOptions(templates, selectedId) {
      return typeof options.buildTemplateOptions === 'function' ? options.buildTemplateOptions(templates, selectedId) : '';
    }

    function updateAddChannelValidationAlert(message = '', type = 'warning') {
      const alertEl = document.getElementById('addChannelValidationAlert');
      if (!alertEl) {
        return;
      }
      const normalized = type === 'error'
        ? 'alert-danger'
        : type === 'success'
          ? 'alert-success'
          : type === 'info'
            ? 'alert-info'
            : 'alert-warning';
      alertEl.classList.remove('d-none', 'alert-danger', 'alert-success', 'alert-info', 'alert-warning');
      if (!message) {
        alertEl.textContent = '';
        alertEl.classList.add('d-none', normalized);
        return;
      }
      alertEl.classList.add(normalized);
      alertEl.textContent = message;
    }

    function notifyChannelStatus(message, type = 'info') {
      updateAddChannelValidationAlert(message, type === 'error' ? 'error' : type);
      notify(message, type);
    }

    function renderChannels(rows) {
      if (!elements.channelsBody) {
        return;
      }
      elements.channelsBody.innerHTML = '';
      const registry = getRegistry();
      registry.clear();
      if (!rows.length) {
        elements.channelsBody.innerHTML = '<tr><td colspan="3" class="text-center text-muted py-4">Каналы ещё не добавлены</td></tr>';
        updateChannelsManageOverview();
        return;
      }
      rows.forEach((row) => {
        const platform = String(row.platform || 'telegram').toLowerCase();
        const platformLabel = platform === 'vk' ? 'VK' : (platform === 'max' ? 'MAX' : 'Telegram');
        const platformConfig = parsePlatformConfig(row.platform_config ?? row.platformConfig);
        const deliverySettings = parseDeliverySettings(row.delivery_settings ?? row.deliverySettings);
        const vkGroupId = platform === 'vk' ? (platformConfig.group_id ?? platformConfig.groupId ?? null) : null;
        const questionTemplateId = sanitizeTemplateId(
          row.question_template_id ?? row.questionTemplateId,
          getQuestionTemplateMap(),
          getDefaultQuestionTemplateId(),
        );
        const ratingTemplateId = sanitizeTemplateId(
          row.rating_template_id ?? row.ratingTemplateId,
          getRatingTemplateMap(),
          getDefaultRatingTemplateId(),
        );
        const autoTemplateId = sanitizeTemplateId(
          row.auto_action_template_id ?? row.autoActionTemplateId,
          getAutoActionTemplateMap(),
          getDefaultAutoActionTemplateId(),
        );

        const prepared = {
          id: row.id,
          channel_name: row.channel_name || row.channelName || row.name || '',
          platform,
          platformLabel,
          platform_config: platformConfig,
          is_active: Boolean(row.is_active ?? row.active ?? row.isActive),
          question_template_id: questionTemplateId,
          rating_template_id: ratingTemplateId,
          auto_action_template_id: autoTemplateId,
          bot_name: row.bot_name || row.botName || '',
          bot_username: row.bot_username || row.botUsername || '',
          token: row.token || '',
          public_id: row.public_id || row.publicId || '',
          vk_group_id: vkGroupId,
          support_chat_id: row.support_chat_id ?? row.supportChatId ?? null,
          credential: row.credential && typeof row.credential === 'object' ? row.credential : null,
          delivery_settings: deliverySettings,
          network_route: normalizeNetworkRoute(row.network_route ?? deliverySettings.network_route, true),
          questions_cfg: parseQuestionsCfg(row.questions_cfg ?? row.questionsCfg),
        };

        registry.set(String(row.id), prepared);

        const statusBadge = prepared.is_active
          ? '<span class="badge bg-success-subtle text-success">Активен</span>'
          : '<span class="badge bg-secondary-subtle text-secondary">Выключен</span>';
        const supportChatRaw = prepared.support_chat_id;
        const hasSupportChat = supportChatRaw !== null && supportChatRaw !== undefined && String(supportChatRaw).trim() !== '';
        const supportChatInfo = hasSupportChat
          ? '<div class="small text-muted mt-1">ID группы: ' + escapeHtml(String(supportChatRaw)) + '</div>'
          : '<div class="small text-muted mt-1">ID группы пока не привязан</div>';
        const routeLabel = prepared.network_route?.mode === 'proxy'
          ? 'Маршрут: proxy'
          : prepared.network_route?.mode === 'vpn'
            ? 'Маршрут: VPN'
            : prepared.network_route?.mode === 'profile'
              ? 'Маршрут: профиль ' + (prepared.network_route?.profile_id || 'не выбран')
              : prepared.network_route?.mode === 'direct'
                ? 'Маршрут: direct'
                : 'Маршрут: общий';
        const botLabel = formatBotLabel(prepared);

        const tr = document.createElement('tr');
        tr.dataset.id = row.id;
        tr.innerHTML = `
          <td>
            <div class="fw-medium">${escapeHtml(prepared.channel_name || '—')}</div>
            <div class="text-muted small">Бот: ${escapeHtml(botLabel)}</div>
          </td>
          <td>
            <div><span class="badge bg-secondary-subtle text-uppercase">${escapeHtml(platformLabel)}</span></div>
            ${prepared.vk_group_id ? `<div class="small text-muted mt-1">group_id: ${escapeHtml(String(prepared.vk_group_id))}</div>` : ''}
            ${supportChatInfo}
            <div class="small text-muted mt-1">${escapeHtml(routeLabel)}</div>
          </td>
          <td class="text-center">
            <div>${statusBadge}</div>
            <div class="mt-1">
              <span class="badge bg-light text-secondary border border-secondary-subtle" data-channel-bot-runtime-status="${prepared.id}">Процесс: проверка…</span>
            </div>
            <div class="d-flex justify-content-center gap-2 mt-2">
              <button type="button" class="btn btn-sm btn-outline-success" data-channel-start="${prepared.id}">Запустить</button>
              <button type="button" class="btn btn-sm btn-outline-primary" data-channel-edit="${prepared.id}">Редактировать</button>
            </div>
          </td>
        `;
        elements.channelsBody.appendChild(tr);
      });
      updateChannelsManageOverview();
    }

    async function loadChannels() {
      if (!elements.channelsBody) {
        return;
      }
      try {
        const resp = await fetch('/api/channels');
        if (!resp.ok) {
          throw new Error('HTTP ' + resp.status);
        }
        const data = await resp.json();
        const rows = Array.isArray(data)
          ? data
          : Array.isArray(data.channels)
            ? data.channels
            : [];
        renderChannels(rows);
        await refreshAllBotRuntimeStatuses(true);
        refreshChannelEditorIfOpen();
      } catch (error) {
        notify('Не удалось загрузить каналы: ' + error.message, 'error');
      }
    }

    function resetAddChannelForm() {
      const tokenInput = document.getElementById('new_token');
      const tokenLabel = document.getElementById('new_token_label');
      const tokenHelp = document.getElementById('new_token_help');
      const nameInput = document.getElementById('new_channel_name');
      const supportChatInput = document.getElementById('new_support_chat_id');
      const platformSelect = document.getElementById('new_platform');
      const questionSelect = document.getElementById('new_question_template');
      const ratingSelect = document.getElementById('new_rating_template');
      const autoSelect = document.getElementById('new_auto_action_template');
      const vkGroupInput = document.getElementById('new_vk_group_id');
      const vkConfirmInput = document.getElementById('new_vk_confirmation');
      const vkSecretInput = document.getElementById('new_vk_secret');
      const telegramBaseUrlInput = document.getElementById('new_telegram_base_url');
      const maxSecretInput = document.getElementById('new_max_secret');

      if (tokenInput) tokenInput.value = '';
      if (tokenInput) tokenInput.placeholder = 'Токен из BotFather';
      if (tokenLabel) tokenLabel.textContent = 'Токен бота';
      if (tokenHelp) tokenHelp.textContent = 'Проверяется автоматически для Telegram.';
      if (nameInput) nameInput.value = '';
      if (supportChatInput) supportChatInput.value = '';
      if (vkGroupInput) vkGroupInput.value = '';
      if (vkConfirmInput) vkConfirmInput.value = '';
      if (vkSecretInput) vkSecretInput.value = '';
      if (telegramBaseUrlInput) telegramBaseUrlInput.value = '';
      if (maxSecretInput) maxSecretInput.value = '';
      if (platformSelect) platformSelect.value = 'telegram';
      if (questionSelect && !questionSelect.disabled) {
        questionSelect.value = getDefaultQuestionTemplateId();
      }
      if (ratingSelect && !ratingSelect.disabled) {
        ratingSelect.value = getDefaultRatingTemplateId();
      }
      if (autoSelect && !autoSelect.disabled) {
        autoSelect.value = getDefaultAutoActionTemplateId();
      }
      togglePlatformFields();
    }

    function populateAddChannelTemplateSelects() {
      const questionSelect = document.getElementById('new_question_template');
      if (questionSelect) {
        questionSelect.innerHTML = buildTemplateOptions(getQuestionTemplates(), getDefaultQuestionTemplateId());
        questionSelect.disabled = !getQuestionTemplates().length;
      }
      const ratingSelect = document.getElementById('new_rating_template');
      if (ratingSelect) {
        ratingSelect.innerHTML = buildTemplateOptions(getRatingTemplates(), getDefaultRatingTemplateId());
        ratingSelect.disabled = !getRatingTemplates().length;
      }
      const autoSelect = document.getElementById('new_auto_action_template');
      if (autoSelect) {
        autoSelect.innerHTML = buildTemplateOptions(getAutoActionTemplates(), getDefaultAutoActionTemplateId());
        autoSelect.disabled = !getAutoActionTemplates().length;
      }
    }

    function togglePlatformFields() {
      const platformSelect = document.getElementById('new_platform');
      const tokenContainer = document.getElementById('token_field_container');
      const tokenInput = document.getElementById('new_token');
      const tokenLabel = document.getElementById('new_token_label');
      const tokenHelp = document.getElementById('new_token_help');
      const telegramContainer = document.getElementById('telegram_base_url_container');
      const vkContainer = document.getElementById('vk_group_id_container');
      const maxContainer = document.getElementById('max_webhook_container');
      const platform = platformSelect?.value || 'telegram';
      if (tokenContainer) {
        tokenContainer.classList.remove('d-none');
      }
      if (telegramContainer) {
        telegramContainer.style.display = platform === 'telegram' ? '' : 'none';
      }
      if (vkContainer) {
        vkContainer.style.display = platform === 'vk' ? '' : 'none';
      }
      if (maxContainer) {
        maxContainer.style.display = platform === 'max' ? '' : 'none';
      }
      if (platform === 'vk') {
        if (tokenLabel) tokenLabel.textContent = 'Токен сообщества VK';
        if (tokenInput) tokenInput.placeholder = 'Ключ доступа сообщества VK';
        if (tokenHelp) tokenHelp.textContent = 'Возьмите в VK: Работа с API → Ключи доступа. Нужны права сообщений.';
        return;
      }
      if (platform === 'max') {
        if (tokenLabel) tokenLabel.textContent = 'Токен MAX-бота';
        if (tokenInput) tokenInput.placeholder = 'Bot token MAX';
        if (tokenHelp) tokenHelp.textContent = 'Возьмите в кабинете интеграций MAX. Используется как основной auth-токен.';
        return;
      }
      if (tokenLabel) tokenLabel.textContent = 'Токен бота';
      if (tokenInput) tokenInput.placeholder = 'Токен из BotFather';
      if (tokenHelp) tokenHelp.textContent = 'Проверяется автоматически для Telegram.';
    }

    function prepareAddChannelSettingsModal() {
      resetAddChannelForm();
      updateAddChannelValidationAlert('');
      populateAddChannelTemplateSelects();
      togglePlatformFields();
      return true;
    }

    async function addChannel() {
      const tokenInput = document.getElementById('new_token');
      const nameInput = document.getElementById('new_channel_name');
      const supportChatInput = document.getElementById('new_support_chat_id');
      const platformSelect = document.getElementById('new_platform');
      const questionSelect = document.getElementById('new_question_template');
      const ratingSelect = document.getElementById('new_rating_template');
      const autoSelect = document.getElementById('new_auto_action_template');
      const vkGroupInput = document.getElementById('new_vk_group_id');
      const vkConfirmInput = document.getElementById('new_vk_confirmation');
      const vkSecretInput = document.getElementById('new_vk_secret');
      const telegramBaseUrlInput = document.getElementById('new_telegram_base_url');
      const maxSecretInput = document.getElementById('new_max_secret');

      const platform = platformSelect?.value || 'telegram';
      const token = tokenInput?.value?.trim() || '';
      const channelName = nameInput?.value?.trim() || '';

      updateAddChannelValidationAlert('');

      if (platform === 'telegram') {
        if (!token && !channelName) {
          notifyChannelStatus('Для Telegram заполните токен бота и название канала в панели.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!token) {
          notifyChannelStatus('Для Telegram необходим токен бота из @BotFather.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!channelName) {
          notifyChannelStatus('Кроме токена, для Telegram нужно указать название канала в панели.', 'warning');
          nameInput?.focus();
          return;
        }
      } else if (platform === 'vk') {
        if (!token && !channelName) {
          notifyChannelStatus('Для VK заполните токен сообщества и название канала в панели.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!token) {
          notifyChannelStatus('Для VK необходим токен сообщества (Работа с API → Ключи доступа).', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!channelName) {
          notifyChannelStatus('Для VK укажите название канала в панели.', 'warning');
          nameInput?.focus();
          return;
        }
      } else if (platform === 'max') {
        if (!token && !channelName) {
          notifyChannelStatus('Для MAX заполните токен бота и название канала в панели.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!token) {
          notifyChannelStatus('Для MAX необходим токен бота.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!channelName) {
          notifyChannelStatus('Для MAX укажите название канала в панели.', 'warning');
          nameInput?.focus();
          return;
        }
      } else {
        if (!token) {
          notifyChannelStatus('Укажите токен бота.', 'warning');
          tokenInput?.focus();
          return;
        }
        if (!channelName) {
          notifyChannelStatus('Укажите название канала.', 'warning');
          nameInput?.focus();
          return;
        }
      }

      const payload = {
        token,
        channel_name: channelName,
        platform,
        is_active: true,
      };

      const questionTemplateId = questionSelect?.value || '';
      const ratingTemplateId = ratingSelect?.value || '';
      const autoTemplateId = autoSelect?.value || '';
      if (questionTemplateId) payload.question_template_id = questionTemplateId;
      if (ratingTemplateId) payload.rating_template_id = ratingTemplateId;
      if (autoTemplateId) payload.auto_action_template_id = autoTemplateId;
      const supportChatId = supportChatInput?.value?.trim() || '';
      if (supportChatId) payload.support_chat_id = supportChatId;

      if (platform === 'telegram') {
        const telegramBaseUrl = telegramBaseUrlInput?.value?.trim() || '';
        if (telegramBaseUrl) {
          payload.platform_config = { base_url: telegramBaseUrl };
        }
      } else if (platform === 'vk') {
        const groupId = Number.parseInt(vkGroupInput?.value || '0', 10);
        const confirmation = vkConfirmInput?.value?.trim() || '';
        if (!Number.isFinite(groupId) || groupId <= 0) {
          notifyChannelStatus('Укажите корректный ID сообщества VK.', 'warning');
          return;
        }
        if (!confirmation) {
          notifyChannelStatus('Укажите код подтверждения Callback API.', 'warning');
          return;
        }
        payload.platform_config = { group_id: groupId, confirmation_token: confirmation };
        const secret = vkSecretInput?.value?.trim();
        if (secret) {
          payload.platform_config.secret = secret;
        }
      } else if (platform === 'max') {
        const secret = maxSecretInput?.value?.trim();
        if (secret) {
          payload.platform_config = { secret };
        }
      }

      try {
        const resp = await fetch('/api/channels', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await resp.json();
        if (!resp.ok || data.success === false) {
          throw new Error(data.error || ('HTTP ' + resp.status));
        }
        resetAddChannelForm();
        updateAddChannelValidationAlert('');
        requestCloseModal(elements.addChannelFormEl);
        notifyChannelStatus('Канал «' + channelName + '» успешно добавлен.', 'success');
        loadChannels();
      } catch (error) {
        notifyChannelStatus('Ошибка добавления канала: ' + error.message, 'error');
      }
    }

    function prepareVkWebhookSettingsTrigger() {
      const editorState = typeof options.getChannelEditorState === 'function' ? options.getChannelEditorState() : null;
      const id = editorState && editorState.channelId !== undefined ? editorState.channelId : null;
      const channel = getRegistry().get(String(id));
      if (!channel || channel.platform !== 'vk') {
        notify('Настройки вебхука доступны только для VK.', 'warning');
        return false;
      }
      const platformConfig = parsePlatformConfig(channel.platform_config);
      state.vkWebhook.channelId = id;
      state.vkWebhook.groupId = platformConfig.group_id ?? platformConfig.groupId ?? null;
      state.vkWebhook.confirmationToken = platformConfig.confirmation_token ?? platformConfig.confirmationToken ?? '';
      state.vkWebhook.secret = platformConfig.secret ?? platformConfig.callback_secret ?? '';

      const groupInput = document.getElementById('vkGroupIdInput');
      const confirmationInput = document.getElementById('vkConfirmationInput');
      const secretInput = document.getElementById('vkSecretInput');
      if (groupInput) groupInput.value = state.vkWebhook.groupId || '';
      if (confirmationInput) confirmationInput.value = state.vkWebhook.confirmationToken || '';
      if (secretInput) secretInput.value = state.vkWebhook.secret || '';
      return true;
    }

    async function saveVkWebhookSettings() {
      if (state.vkWebhook.channelId === null) {
        return;
      }
      const groupInput = document.getElementById('vkGroupIdInput');
      const confirmationInput = document.getElementById('vkConfirmationInput');
      const secretInput = document.getElementById('vkSecretInput');
      const groupIdVal = groupInput?.value?.trim();
      const confirmationVal = confirmationInput?.value?.trim();
      const secretVal = secretInput?.value?.trim();

      const groupId = Number.parseInt(groupIdVal, 10);
      if (!Number.isFinite(groupId) || groupId <= 0) {
        notify('group_id должен быть положительным числом.', 'warning');
        return;
      }
      if (!confirmationVal) {
        notify('Укажите код подтверждения Callback API.', 'warning');
        return;
      }

      const payload = {
        platform_config: {
          group_id: groupId,
          confirmation_token: confirmationVal,
          secret: secretVal || null,
        },
      };

      try {
        const resp = await fetch('/api/channels/' + state.vkWebhook.channelId, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await resp.json();
        if (!resp.ok || data.success === false) {
          throw new Error(data.error || ('HTTP ' + resp.status));
        }

        requestCloseModal(elements.vkWebhookFormEl);
        await loadChannels();
        notify('Настройки вебхука сохранены.', 'success');
      } catch (error) {
        notify('Не удалось сохранить настройки вебхука: ' + error.message, 'error');
      }
    }

    function handleChannelsBodyClick(event) {
      const startBtn = event.target.closest('[data-channel-start]');
      if (!startBtn) {
        return;
      }
      event.preventDefault();
      const channelId = startBtn.getAttribute('data-channel-start');
      if (channelId && typeof options.startBot === 'function') {
        options.startBot(channelId);
      }
    }

    return {
      renderChannels,
      loadChannels,
      updateAddChannelValidationAlert,
      notifyChannelStatus,
      resetAddChannelForm,
      populateAddChannelTemplateSelects,
      togglePlatformFields,
      prepareAddChannelSettingsModal,
      addChannel,
      prepareVkWebhookSettingsTrigger,
      saveVkWebhookSettings,
      handleChannelsBodyClick,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelsCatalogRuntime) {
        return window.__settingsChannelsCatalogRuntime;
      }
      const runtime = createRuntime(options);
      window.prepareAddChannelSettingsModal = function prepareAddChannelSettingsModal() {
        return runtime.prepareAddChannelSettingsModal();
      };
      window.prepareVkWebhookSettingsTrigger = function prepareVkWebhookSettingsTrigger() {
        return runtime.prepareVkWebhookSettingsTrigger();
      };
      window.__settingsChannelsCatalogRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelsCatalogRuntime = Object.freeze(api);
}());

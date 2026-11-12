(function () {
  if (window.SettingsChannelsBotRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = {
      channelsRefreshBotStatusesBtn: document.getElementById('channelsRefreshBotStatusesBtn'),
      channelsBotStatusesInfo: document.getElementById('channelsBotStatusesInfo'),
      channelEditorTestTargetSelect: document.getElementById('channelEditorTestTarget'),
      channelEditorTestRecipientInput: document.getElementById('channelEditorTestRecipient'),
      channelEditorTestMessageInput: document.getElementById('channelEditorTestMessage'),
      channelEditorSendTestBtn: document.getElementById('channelEditorSendTestBtn'),
      channelEditorTestStatusEl: document.getElementById('channelEditorTestStatus'),
      channelEditorTokenEl: document.querySelector('[data-channel-editor-token]'),
      channelEditorToggleTokenBtn: document.getElementById('channelEditorToggleTokenBtn'),
      channelEditorCopyTokenBtn: document.getElementById('channelEditorCopyTokenBtn'),
      channelEditorBotStartBtn: document.getElementById('channelEditorBotStartBtn'),
      channelEditorBotStopBtn: document.getElementById('channelEditorBotStopBtn'),
      channelEditorBotRestartBtn: document.getElementById('channelEditorBotRestartBtn'),
      channelEditorBotStatusEl: document.querySelector('[data-channel-editor-bot-status]'),
      channelEditorBotStatusBadgeEl: document.querySelector('[data-channel-editor-bot-status-badge]'),
      channelEditorBotStatusDetailsEl: document.querySelector('[data-channel-editor-bot-status-details]'),
      channelEditorBotRefreshBtn: document.getElementById('channelEditorBotRefreshBtn'),
    };

    const botRefreshDefaultLabel = elements.channelEditorBotRefreshBtn?.textContent?.trim() || 'Обновить информацию о боте';
    const pendingActions = new Map();
    const runtimeStatuses = new Map();
    const statusRevisions = new Map();
    const actionLabels = { start: 'Запускаем…', stop: 'Останавливаем…', restart: 'Перезапускаем…' };

    function isCurrentEditor(channelId) {
      return String(getChannelEditorState().channelId) === String(channelId);
    }

    function canManageBots() {
      return window.SettingsPermissions?.canEdit('channels') !== false;
    }

    function setActionButton(button, disabled, action, label) {
      if (!button) return;
      button.disabled = disabled || !canManageBots() || button.dataset.permissionDisabled === 'true';
      button.setAttribute('aria-busy', String(Boolean(action)));
      if (action && !button.dataset.botIdleHtml) {
        button.dataset.botIdleHtml = button.innerHTML;
        button.dataset.botIdleLabel = button.getAttribute('aria-label') || '';
        const hidden = button.classList.contains('channels-icon-button') ? ' visually-hidden' : '';
        button.innerHTML = `<span class="spinner-border spinner-border-sm" aria-hidden="true"></span><span class="ms-1${hidden}">${label}</span>`;
        button.setAttribute('aria-label', label);
      } else if (!action && button.dataset.botIdleHtml) {
        button.innerHTML = button.dataset.botIdleHtml;
        if (button.dataset.botIdleLabel) button.setAttribute('aria-label', button.dataset.botIdleLabel);
        else button.removeAttribute('aria-label');
        delete button.dataset.botIdleHtml;
        delete button.dataset.botIdleLabel;
      }
    }

    function showPendingBadge(badge, action) {
      badge.className = 'badge bg-primary-subtle text-primary';
      badge.setAttribute('aria-busy', 'true');
      badge.innerHTML = `<span class="spinner-border spinner-border-sm me-1" aria-hidden="true"></span>${actionLabels[action]}`;
    }

    function paintChannelState(channelId) {
      const state = runtimeStatuses.get(String(channelId)) || { status: '', startedAt: null };
      setChannelRowBotRuntimeStatus(channelId, state.status, state.startedAt);
      if (isCurrentEditor(channelId)) {
        updateBotStatusLabel(state.status, state.startedAt);
        updateBotControls(state.status === 'running');
      }
    }

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

    function notify(message, type = 'info') {
      if (typeof options.notifyChannelStatus === 'function') {
        options.notifyChannelStatus(message, type);
        return;
      }
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function getXsrfToken() {
      if (typeof options.getCookieValue === 'function') {
        return options.getCookieValue('XSRF-TOKEN');
      }
      return '';
    }

    function maskToken(token) {
      const str = String(token || '').trim();
      if (!str) {
        return '—';
      }
      if (str.length <= 10) {
        return str;
      }
      return `${str.slice(0, 6)}…${str.slice(-4)}`;
    }

    function updateTestRecipientState() {
      if (!elements.channelEditorTestTargetSelect || !elements.channelEditorTestRecipientInput) {
        return;
      }
      const custom = elements.channelEditorTestTargetSelect.value === 'custom';
      elements.channelEditorTestRecipientInput.disabled = !custom;
      if (!custom) {
        elements.channelEditorTestRecipientInput.value = '';
      }
    }

    function setTestStatus(message) {
      if (elements.channelEditorTestStatusEl) {
        elements.channelEditorTestStatusEl.textContent = `Статус теста: ${message}`;
      }
    }

    function normalizeBotRuntimeStatus(status) {
      const raw = String(status || '').trim();
      const normalized = raw.toLowerCase();
      if (normalized === 'running') {
        return { code: 'running', label: 'запущен', details: '' };
      }
      if (normalized === 'stopped') {
        return { code: 'stopped', label: 'остановлен', details: '' };
      }
      if (!raw) {
        return { code: 'unknown', label: '—', details: '' };
      }
      return { code: 'error', label: 'ошибка', details: raw };
    }

    function getBotRuntimeBadgeClass(code) {
      if (code === 'running') return 'badge bg-success-subtle text-success';
      if (code === 'stopped') return 'badge bg-secondary-subtle text-secondary';
      if (code === 'error') return 'badge bg-danger-subtle text-danger';
      return 'badge bg-warning-subtle text-warning-emphasis';
    }

    function updateBotStatusLabel(status, startedAt) {
      const action = pendingActions.get(String(getChannelEditorState().channelId));
      const normalized = normalizeBotRuntimeStatus(status);
      if (elements.channelEditorBotStatusBadgeEl) {
        if (action) showPendingBadge(elements.channelEditorBotStatusBadgeEl, action);
        else {
          elements.channelEditorBotStatusBadgeEl.setAttribute('aria-busy', 'false');
          elements.channelEditorBotStatusBadgeEl.className = getBotRuntimeBadgeClass(normalized.code);
          elements.channelEditorBotStatusBadgeEl.textContent = normalized.label;
        }
      }
      if (elements.channelEditorBotStatusDetailsEl) {
        if (action) {
          elements.channelEditorBotStatusDetailsEl.textContent = 'Ожидаем подтверждения выполнения.';
        } else if (normalized.code === 'running' && startedAt) {
          elements.channelEditorBotStatusDetailsEl.textContent = `с ${new Date(startedAt).toLocaleString()}`;
        } else if (normalized.details) {
          elements.channelEditorBotStatusDetailsEl.textContent = normalized.details;
        } else {
          elements.channelEditorBotStatusDetailsEl.textContent = '';
        }
      }
      if (elements.channelEditorBotStatusEl) {
        elements.channelEditorBotStatusEl.title = normalized.details || '';
      }
    }

    function updateChannelRowBotControls(channelId, code) {
      const startBtn = document.querySelector(`[data-channel-start="${channelId}"]`);
      const stopBtn = document.querySelector(`[data-channel-stop="${channelId}"]`);
      const restartBtn = document.querySelector(`[data-channel-restart="${channelId}"]`);
      const action = pendingActions.get(String(channelId));
      const running = code === 'running';
      const startAllowed = code === 'stopped' || code === 'error';
      if (startBtn) {
        startBtn.hidden = running;
        setActionButton(startBtn, Boolean(action) || !startAllowed, action === 'start', actionLabels.start);
      }
      if (stopBtn) {
        stopBtn.hidden = !running;
        setActionButton(stopBtn, Boolean(action) || !running, action === 'stop', actionLabels.stop);
      }
      setActionButton(restartBtn, Boolean(action) || !running, action === 'restart', actionLabels.restart);
    }

    function setChannelRowBotRuntimeStatus(channelId, status, startedAt) {
      const action = pendingActions.get(String(channelId));
      if (!action) runtimeStatuses.set(String(channelId), { status, startedAt });
      const normalized = normalizeBotRuntimeStatus(status);
      updateChannelRowBotControls(channelId, normalized.code);
      const badge = document.querySelector(`[data-channel-bot-runtime-status="${channelId}"]`);
      if (!badge) {
        return;
      }
      if (action) {
        showPendingBadge(badge, action);
        return;
      }
      badge.setAttribute('aria-busy', 'false');
      badge.className = getBotRuntimeBadgeClass(normalized.code);
      if (normalized.code === 'running' && startedAt) {
        badge.textContent = `Процесс: запущен с ${new Date(startedAt).toLocaleTimeString()}`;
        badge.title = new Date(startedAt).toLocaleString();
        return;
      }
      if (normalized.details) {
        badge.textContent = `Процесс: ${normalized.label}`;
        badge.title = normalized.details;
        return;
      }
      badge.textContent = `Процесс: ${normalized.label}`;
      badge.title = '';
    }

    async function refreshAllBotRuntimeStatuses(silent = false) {
      const channelIds = Array.from(getRegistry().keys())
        .map((value) => Number.parseInt(value, 10))
        .filter((value) => Number.isFinite(value));
      if (!channelIds.length) {
        if (!silent && elements.channelsBotStatusesInfo) {
          elements.channelsBotStatusesInfo.textContent = 'Нет каналов для обновления статусов.';
        }
        return;
      }
      if (elements.channelsRefreshBotStatusesBtn) {
        elements.channelsRefreshBotStatusesBtn.disabled = true;
        elements.channelsRefreshBotStatusesBtn.textContent = 'Обновляем…';
      }
      if (elements.channelsBotStatusesInfo) {
        elements.channelsBotStatusesInfo.textContent = `Обновление статусов: 0/${channelIds.length}`;
      }
      let done = 0;
      let errors = 0;
      await Promise.all(channelIds.map(async (channelId) => {
        const revision = statusRevisions.get(String(channelId));
        try {
          if (pendingActions.has(String(channelId))) {
            paintChannelState(channelId);
            return;
          }
          const response = await fetch(`/api/bots/${channelId}/status`);
          const payload = await response.json().catch(() => ({}));
          if (!response.ok) {
            throw new Error(payload?.error || ('HTTP ' + response.status));
          }
          if (revision !== statusRevisions.get(String(channelId)) || pendingActions.has(String(channelId))) return;
          setChannelRowBotRuntimeStatus(channelId, payload?.status, payload?.startedAt);
          paintChannelState(channelId);
        } catch (error) {
          errors += 1;
          if (revision !== statusRevisions.get(String(channelId)) || pendingActions.has(String(channelId))) return;
          setChannelRowBotRuntimeStatus(channelId, 'ошибка', null);
          paintChannelState(channelId);
        } finally {
          done += 1;
          if (elements.channelsBotStatusesInfo) {
            elements.channelsBotStatusesInfo.textContent = `Обновление статусов: ${done}/${channelIds.length}`;
          }
        }
      }));
      const refreshedAt = new Date().toLocaleTimeString();
      if (elements.channelsBotStatusesInfo) {
        elements.channelsBotStatusesInfo.textContent = errors
          ? `Обновлено ${channelIds.length - errors}/${channelIds.length}, ошибок: ${errors}. ${refreshedAt}`
          : `Статусы обновлены: ${channelIds.length}/${channelIds.length}. ${refreshedAt}`;
      }
      if (elements.channelsRefreshBotStatusesBtn) {
        elements.channelsRefreshBotStatusesBtn.disabled = false;
        elements.channelsRefreshBotStatusesBtn.textContent = botRefreshDefaultLabel;
      }
    }

    function updateBotControls(running) {
      const action = pendingActions.get(String(getChannelEditorState().channelId));
      setActionButton(elements.channelEditorBotStartBtn, Boolean(action) || running, action === 'start', actionLabels.start);
      setActionButton(elements.channelEditorBotStopBtn, Boolean(action) || !running, action === 'stop', actionLabels.stop);
      setActionButton(elements.channelEditorBotRestartBtn, Boolean(action) || !running, action === 'restart', actionLabels.restart);
    }

    function updateBotRefreshControl(isTelegram, isLoading = false) {
      if (!elements.channelEditorBotRefreshBtn) {
        return;
      }
      elements.channelEditorBotRefreshBtn.disabled = !isTelegram || isLoading;
      elements.channelEditorBotRefreshBtn.textContent = isLoading ? 'Обновляем…' : botRefreshDefaultLabel;
      elements.channelEditorBotRefreshBtn.title = isTelegram ? '' : 'Обновление доступно только для Telegram';
    }

    function getChannelEditorTokenValue() {
      const editorState = getChannelEditorState();
      if (editorState.channelId === null) {
        return '';
      }
      const channel = getRegistry().get(String(editorState.channelId));
      return String(channel?.token || '').trim();
    }

    function updateChannelEditorToken() {
      if (!elements.channelEditorTokenEl) {
        return;
      }
      const editorState = getChannelEditorState();
      const token = getChannelEditorTokenValue();
      elements.channelEditorTokenEl.textContent = editorState.tokenVisible ? (token || '—') : maskToken(token);
      if (elements.channelEditorToggleTokenBtn) {
        elements.channelEditorToggleTokenBtn.textContent = editorState.tokenVisible ? 'Скрыть' : 'Показать';
        elements.channelEditorToggleTokenBtn.disabled = !token;
      }
      if (elements.channelEditorCopyTokenBtn) {
        elements.channelEditorCopyTokenBtn.disabled = !token;
      }
    }

    async function copyChannelToken() {
      const token = getChannelEditorTokenValue();
      if (!token) {
        popup('Токен канала отсутствует.');
        return;
      }
      try {
        await navigator.clipboard.writeText(token);
        popup('Токен скопирован.');
      } catch (error) {
        popup('Не удалось скопировать токен: ' + error.message);
      }
    }

    async function refreshBotStatus(channelId) {
      if (!channelId) {
        return;
      }
      const key = String(channelId);
      const revision = statusRevisions.get(key);
      if (pendingActions.has(key)) {
        paintChannelState(channelId);
        return;
      }
      try {
        const response = await fetch(`/api/bots/${channelId}/status`);
        const payload = await response.json();
        if (revision !== statusRevisions.get(key) || pendingActions.has(key)) return;
        if (!response.ok) throw new Error(payload.error || `HTTP ${response.status}`);
        setChannelRowBotRuntimeStatus(channelId, payload.status, payload.startedAt);
        paintChannelState(channelId);
      } catch (error) {
        if (revision !== statusRevisions.get(key) || pendingActions.has(key)) return;
        console.error('Failed to load bot status', error);
        setChannelRowBotRuntimeStatus(channelId, 'ошибка', null);
        paintChannelState(channelId);
      }
    }

    async function refreshChannelBotInfo(channelId) {
      if (!channelId) {
        return;
      }
      const channel = getRegistry().get(String(channelId));
      if (!channel) {
        notify('Канал не найден.', 'error');
        return;
      }
      if (channel.platform !== 'telegram') {
        notify('Обновление имени доступно только для Telegram.', 'warning');
        return;
      }
      updateBotRefreshControl(true, true);
      try {
        const resp = await fetch(`/api/channels/${channelId}/bot-info`, { method: 'POST' });
        const data = await resp.json();
        if (!resp.ok || data.success === false) {
          throw new Error(data.error || ('HTTP ' + resp.status));
        }
        if (typeof options.loadChannels === 'function') {
          await options.loadChannels();
        }
        notify('Информация о боте обновлена.', 'success');
      } catch (error) {
        notify(`Не удалось обновить информацию о боте: ${error.message}`, 'error');
      } finally {
        const updated = getRegistry().get(String(channelId));
        updateBotRefreshControl(updated ? updated.platform === 'telegram' : true);
      }
    }

    async function runBotAction(channelId, action) {
      const key = String(channelId);
      if (!channelId || pendingActions.has(key) || !canManageBots()) return false;
      pendingActions.set(key, action);
      statusRevisions.set(key, (statusRevisions.get(key) || 0) + 1);
      paintChannelState(channelId);
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), options.lifecycleTimeoutMs || 125000);
      let result = null, failure = null;
      try {
        // The existing start command stops the previous process and waits for the replacement's readiness.
        const endpoint = action === 'restart' ? 'start' : action;
        const response = await fetch(`/api/bots/${channelId}/${endpoint}`, {
          method: 'POST',
          headers: { 'X-XSRF-TOKEN': getXsrfToken() },
          signal: controller.signal,
        });
        const payload = await response.json();
        if (!response.ok || payload.success !== true || payload.status !== (action === 'stop' ? 'stopped' : 'running')) {
          throw new Error(payload.error || payload.status || ('HTTP ' + response.status));
        }
        result = payload;
      } catch (error) {
        failure = error?.name === 'AbortError' ? 'Время ожидания истекло. Обновите статус бота перед повторной командой.' : error.message;
      } finally {
        clearTimeout(timeout);
        pendingActions.delete(key);
        setChannelRowBotRuntimeStatus(channelId, result?.status || failure || 'Ошибка', result?.startedAt || null);
        paintChannelState(channelId);
      }
      if (failure) {
        const verb = { start: 'запустить', stop: 'остановить', restart: 'перезапустить' }[action];
        popup(`Не удалось ${verb} бота: ${failure}`);
      }
      return result !== null;
    }

    async function startBotForChannel(channelId) {
      return runBotAction(channelId, 'start');
    }

    async function startBot(channelId) {
      const success = await startBotForChannel(channelId);
      if (success) {
        popup('Бот запущен.');
      }
      return success;
    }

    async function stopBotForChannel(channelId) {
      return runBotAction(channelId, 'stop');
    }

    async function stopBot(channelId) {
      const success = await stopBotForChannel(channelId);
      if (success) {
        popup('Бот остановлен.');
      }
      return success;
    }

    async function restartBotForChannel(channelId) {
      return runBotAction(channelId, 'restart');
    }

    async function restartBot(channelId) {
      const success = await restartBotForChannel(channelId);
      if (success) popup('Бот перезапущен.');
      return success;
    }

    async function sendChannelTestMessage() {
      const editorState = getChannelEditorState();
      if (editorState.channelId === null) {
        return;
      }
      const message = elements.channelEditorTestMessageInput?.value?.trim() || '';
      const targetMode = elements.channelEditorTestTargetSelect?.value || 'group';
      const recipient = elements.channelEditorTestRecipientInput?.value?.trim() || '';
      if (!message) {
        setTestStatus('укажите текст сообщения');
        return;
      }
      if (targetMode === 'custom' && !recipient) {
        setTestStatus('укажите chat_id');
        return;
      }
      try {
        if (elements.channelEditorSendTestBtn) {
          elements.channelEditorSendTestBtn.disabled = true;
        }
        setTestStatus('отправка…');
        const response = await fetch(`/api/channels/${editorState.channelId}/test-message`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-XSRF-TOKEN': getXsrfToken(),
          },
          body: JSON.stringify({ message, target_mode: targetMode, recipient }),
        });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok || payload.success === false) {
          throw new Error(payload.error || ('HTTP ' + response.status));
        }
        const sentCount = Array.isArray(payload.sent) ? payload.sent.length : 0;
        const failedCount = Array.isArray(payload.failed) ? payload.failed.length : 0;
        setTestStatus(`успешно: ${sentCount}, ошибок: ${failedCount}`);
        if (failedCount > 0) {
          popup('Тест отправлен частично. Проверьте chat_id и права бота.');
        }
      } catch (error) {
        setTestStatus(`ошибка: ${error.message}`);
        popup('Не удалось отправить тест: ' + error.message);
      } finally {
        if (elements.channelEditorSendTestBtn) {
          elements.channelEditorSendTestBtn.disabled = false;
        }
      }
    }

    return {
      updateTestRecipientState,
      setTestStatus,
      normalizeBotRuntimeStatus,
      getBotRuntimeBadgeClass,
      updateBotStatusLabel,
      setChannelRowBotRuntimeStatus,
      refreshAllBotRuntimeStatuses,
      updateBotControls,
      updateBotRefreshControl,
      getChannelEditorTokenValue,
      updateChannelEditorToken,
      copyChannelToken,
      refreshBotStatus,
      refreshChannelBotInfo,
      startBotForChannel,
      startBot,
      stopBotForChannel,
      stopBot,
      restartBotForChannel,
      restartBot,
      sendChannelTestMessage,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelsBotRuntime) {
        return window.__settingsChannelsBotRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsChannelsBotRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelsBotRuntime = Object.freeze(api);
}());

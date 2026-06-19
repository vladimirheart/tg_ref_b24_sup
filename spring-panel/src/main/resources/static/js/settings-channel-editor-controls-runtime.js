(function () {
  if (window.SettingsChannelEditorControlsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = {
      channelEditorActiveInput: document.getElementById('channelEditorIsActive'),
      channelEditorQuestionSelect: document.getElementById('channelEditorQuestionTemplate'),
      channelEditorRatingSelect: document.getElementById('channelEditorRatingTemplate'),
      channelEditorAutoSelect: document.getElementById('channelEditorAutoTemplate'),
      channelEditorSupportChatInput: document.getElementById('channelEditorSupportChatId'),
      channelEditorPanelNotifTargetModeInput: document.getElementById('channelEditorPanelNotifTargetMode'),
      channelEditorTestTargetSelect: document.getElementById('channelEditorTestTarget'),
      channelEditorSendTestBtn: document.getElementById('channelEditorSendTestBtn'),
      channelEditorBotStartBtn: document.getElementById('channelEditorBotStartBtn'),
      channelEditorBotStopBtn: document.getElementById('channelEditorBotStopBtn'),
      channelEditorBotRefreshBtn: document.getElementById('channelEditorBotRefreshBtn'),
      channelEditorToggleTokenBtn: document.getElementById('channelEditorToggleTokenBtn'),
      channelEditorCopyTokenBtn: document.getElementById('channelEditorCopyTokenBtn'),
      channelEditorSaveBtn: document.getElementById('channelEditorSaveBtn'),
      channelEditorDeleteBtn: document.getElementById('channelEditorDeleteBtn'),
      channelEditorRegeneratePublicIdBtn: document.getElementById('channelEditorRegeneratePublicIdBtn'),
      channelEditorCopyPublicLinkBtn: document.getElementById('channelEditorCopyPublicLinkBtn'),
    };

    let controlsBound = false;

    function getChannelEditorState() {
      const state = typeof options.getChannelEditorState === 'function' ? options.getChannelEditorState() : null;
      return state && typeof state === 'object'
        ? state
        : { channelId: null, tokenVisible: false, publicFormFields: [] };
    }

    function bindControls() {
      if (controlsBound) {
        return;
      }
      controlsBound = true;

      elements.channelEditorRegeneratePublicIdBtn?.addEventListener('click', () => options.channelEditorShell?.handleRegeneratePublicIdClick?.());
      elements.channelEditorCopyPublicLinkBtn?.addEventListener('click', () => options.channelEditorShell?.handleCopyPublicLinkClick?.());
      elements.channelEditorActiveInput?.addEventListener('change', () => options.channelEditorShell?.handleActiveInputChange?.());
      elements.channelEditorQuestionSelect?.addEventListener('change', () => options.channelEditorShell?.handleQuestionTemplateChange?.());
      elements.channelEditorRatingSelect?.addEventListener('change', () => options.channelEditorShell?.handleRatingTemplateChange?.());
      elements.channelEditorAutoSelect?.addEventListener('change', () => options.channelEditorShell?.handleAutoTemplateChange?.());
      elements.channelEditorSupportChatInput?.addEventListener('input', () => options.channelEditorShell?.handleSupportChatInput?.());
      elements.channelEditorPanelNotifTargetModeInput?.addEventListener('change', () => options.channelEditorShell?.updateChannelPanelNotificationRoutingState?.());
      elements.channelEditorTestTargetSelect?.addEventListener('change', () => options.channelBotRuntime?.updateTestRecipientState?.());
      elements.channelEditorSendTestBtn?.addEventListener('click', () => options.channelBotRuntime?.sendChannelTestMessage?.());
      elements.channelEditorBotStartBtn?.addEventListener('click', () => {
        const channelId = getChannelEditorState().channelId;
        if (channelId !== null) {
          options.channelBotRuntime?.startBotForChannel?.(channelId);
        }
      });
      elements.channelEditorBotStopBtn?.addEventListener('click', () => {
        const channelId = getChannelEditorState().channelId;
        if (channelId !== null) {
          options.channelBotRuntime?.stopBotForChannel?.(channelId);
        }
      });
      elements.channelEditorBotRefreshBtn?.addEventListener('click', () => {
        const channelId = getChannelEditorState().channelId;
        if (channelId !== null) {
          options.channelBotRuntime?.refreshChannelBotInfo?.(channelId);
        }
      });
      elements.channelEditorToggleTokenBtn?.addEventListener('click', () => {
        const editorState = getChannelEditorState();
        editorState.tokenVisible = !editorState.tokenVisible;
        options.channelBotRuntime?.updateChannelEditorToken?.();
      });
      elements.channelEditorCopyTokenBtn?.addEventListener('click', () => options.channelBotRuntime?.copyChannelToken?.());
      elements.channelEditorSaveBtn?.addEventListener('click', () => options.channelEditorPersistence?.handleSaveClick?.());
      elements.channelEditorDeleteBtn?.addEventListener('click', () => options.channelEditorPersistence?.handleDeleteClick?.());
    }

    return {
      bindControls,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsChannelEditorControlsRuntime) {
        return window.__settingsChannelEditorControlsRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsChannelEditorControlsRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsChannelEditorControlsRuntime = Object.freeze(api);
}());

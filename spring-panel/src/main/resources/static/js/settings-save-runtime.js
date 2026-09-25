(function () {
  if (window.SettingsSaveRuntime) {
    return;
  }

  const SETTINGS_SAVE_SCOPE_V29 = Object.freeze({
    ALL: 'all',
    DIALOGS: 'dialogs',
    LOCATIONS: 'locations',
    AUTO_CLOSE: 'auto-close',
  });
  const SETTINGS_SAVE_SCOPES = new Set(Object.values(SETTINGS_SAVE_SCOPE_V29));

  function createRuntime(options = {}) {
    function uniqueErrors(errors) {
      return Array.from(new Set((Array.isArray(errors) ? errors : []).filter(Boolean)));
    }

    function collectState(runtime, collectorName, fallbackState) {
      if (!runtime || typeof runtime[collectorName] !== 'function') {
        return fallbackState;
      }
      const state = runtime[collectorName]();
      if (!state || typeof state !== 'object') {
        return fallbackState;
      }
      return state;
    }

    function showMessage(message, type = 'info') {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message, type);
      }
    }

    function collectStatuses() {
      if (typeof options.collectStatuses === 'function') {
        return options.collectStatuses();
      }
      return Array.from(document.querySelectorAll('#statusesList .status-input'))
        .map((input) => input.value.trim())
        .filter(Boolean);
    }

    function resolveSaveSource(source) {
      if (source instanceof HTMLElement) {
        return source;
      }
      if (source?.currentTarget instanceof HTMLElement) {
        return source.currentTarget;
      }
      if (source?.target instanceof HTMLElement) {
        return source.target.closest('[data-save-settings]');
      }
      return null;
    }

    function resolveSaveScope(source) {
      const button = resolveSaveSource(source);
      const rawScope = button?.dataset?.settingsSaveScope || SETTINGS_SAVE_SCOPE_V29.ALL;
      const normalized = String(rawScope).trim().toLowerCase();
      return SETTINGS_SAVE_SCOPES.has(normalized)
        ? normalized
        : SETTINGS_SAVE_SCOPE_V29.ALL;
    }

    function setSaveButtonBusy(source, busy) {
      const button = resolveSaveSource(source);
      if (!button) {
        return;
      }
      if (busy) {
        if (!button.dataset.settingsSaveIdleLabel) {
          button.dataset.settingsSaveIdleLabel = button.textContent?.trim() || 'Сохранить';
        }
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        button.textContent = 'Сохраняем…';
        return;
      }
      button.disabled = false;
      button.removeAttribute('aria-busy');
      button.textContent = button.dataset.settingsSaveIdleLabel || 'Сохранить';
      delete button.dataset.settingsSaveIdleLabel;
    }

    function collectAutoClosePayload() {
      const autoCloseState = collectState(
        options.templatesRuntime,
        'collectAutoCloseConfig',
        {
          templates: [],
          active_template_id: null,
          errors: ['Не удалось собрать шаблоны автозакрытия.'],
        }
      );
      if (autoCloseState.errors && autoCloseState.errors.length) {
        showMessage(uniqueErrors(autoCloseState.errors).join('\n'), 'error');
        return null;
      }
      if (!Array.isArray(autoCloseState.templates) || !autoCloseState.templates.length) {
        showMessage('Добавьте хотя бы один шаблон автозакрытия.', 'error');
        return null;
      }
      const activeTemplate = autoCloseState.templates.find(
        (template) => template.id === autoCloseState.active_template_id
      ) || autoCloseState.templates[0];
      return {
        templates: autoCloseState.templates,
        active_template_id: autoCloseState.active_template_id || activeTemplate?.id || null,
      };
    }

    function collectDialogPayload() {
      const timeMetricsState = collectState(
        options.metricsRuntime,
        'collectTimeMetricsConfig',
        {
          config: {},
          errors: ['Не удалось собрать настройки time metrics.'],
        }
      );
      if (timeMetricsState.errors && timeMetricsState.errors.length) {
        showMessage(uniqueErrors(timeMetricsState.errors).join('\n'), 'error');
        return null;
      }

      const dialogSlaState = collectState(
        options.dialogShellRuntime,
        'collectDialogSlaConfig',
        {
          config: {},
          errors: ['Не удалось собрать dialog settings shell config.'],
        }
      );
      if (dialogSlaState.errors && dialogSlaState.errors.length) {
        showMessage(uniqueErrors(dialogSlaState.errors).join('\n'), 'error');
        return null;
      }

      const summaryBadgesState = collectState(
        options.metricsRuntime,
        'collectDialogSummaryBadges',
        {
          status: {},
          errors: ['Не удалось собрать настройки status badges.'],
        }
      );
      if (summaryBadgesState.errors && summaryBadgesState.errors.length) {
        showMessage(uniqueErrors(summaryBadgesState.errors).join('\n'), 'error');
        return null;
      }

      const dialogTemplatesState = collectState(
        options.templatesRuntime,
        'collectDialogTemplatesPayload',
        {
          categoryTemplates: [],
          questionTemplates: [],
          completionTemplates: [],
          macroTemplates: [],
          fallbackCategories: [],
        }
      );

      return {
        categories: dialogTemplatesState.fallbackCategories || [],
        dialog_category_templates: dialogTemplatesState.categoryTemplates || [],
        dialog_question_templates: dialogTemplatesState.questionTemplates || [],
        dialog_completion_templates: dialogTemplatesState.completionTemplates || [],
        dialog_macro_templates: dialogTemplatesState.macroTemplates || [],
        dialog_time_metrics: timeMetricsState.config || {},
        ...((dialogSlaState.config && typeof dialogSlaState.config === 'object')
          ? dialogSlaState.config
          : {}),
        dialog_summary_badges: { status: summaryBadgesState.status || {} },
      };
    }

    function collectLocationsPayload() {
      const locationsLoaded = typeof options.areLocationsLoaded === 'function'
        ? Boolean(options.areLocationsLoaded())
        : true;
      const locationsState = typeof options.getLocationsState === 'function'
        ? options.getLocationsState()
        : null;

      if (!locationsLoaded || !locationsState || typeof locationsState !== 'object') {
        showMessage('Структура локаций ещё не загружена. Дождитесь загрузки и повторите сохранение.', 'error');
        return null;
      }

      return {
        locations_iiko_server_sources: typeof options.serializeLocationsIikoServerSources === 'function'
          ? options.serializeLocationsIikoServerSources()
          : [],
        locations_iiko_sync: typeof options.serializeLocationsIikoSyncSettings === 'function'
          ? options.serializeLocationsIikoSyncSettings()
          : {},
        locations: locationsState,
      };
    }

    function collectFullPayload() {
      const dialogPayload = collectDialogPayload();
      if (!dialogPayload) {
        return null;
      }
      const autoClosePayload = collectAutoClosePayload();
      if (!autoClosePayload) {
        return null;
      }

      const networkProfilesPayload = options.networkProfilesRuntime?.collectNetworkProfilesPayload?.() || [];
      const locationsLoaded = typeof options.areLocationsLoaded === 'function'
        ? Boolean(options.areLocationsLoaded())
        : true;
      const locationsState = typeof options.getLocationsState === 'function'
        ? options.getLocationsState()
        : null;

      const payload = {
        ...dialogPayload,
        client_statuses: collectStatuses(),
        locations_iiko_server_sources: typeof options.serializeLocationsIikoServerSources === 'function'
          ? options.serializeLocationsIikoServerSources()
          : [],
        locations_iiko_sync: typeof options.serializeLocationsIikoSyncSettings === 'function'
          ? options.serializeLocationsIikoSyncSettings()
          : {},
        netbox_sync: typeof options.serializeNetBoxSyncSettings === 'function'
          ? options.serializeNetBoxSyncSettings()
          : {},
        network_profiles: networkProfilesPayload,
        auto_close_config: autoClosePayload,
      };

      if (locationsLoaded && locationsState && typeof locationsState === 'object') {
        payload.locations = locationsState;
      }
      return payload;
    }

    function buildPayload(scope) {
      switch (scope) {
        case SETTINGS_SAVE_SCOPE_V29.DIALOGS:
          return collectDialogPayload();
        case SETTINGS_SAVE_SCOPE_V29.LOCATIONS:
          return collectLocationsPayload();
        case SETTINGS_SAVE_SCOPE_V29.AUTO_CLOSE: {
          const autoClosePayload = collectAutoClosePayload();
          return autoClosePayload ? { auto_close_config: autoClosePayload } : null;
        }
        case SETTINGS_SAVE_SCOPE_V29.ALL:
        default:
          return collectFullPayload();
      }
    }

    function successMessage(scope) {
      switch (scope) {
        case SETTINGS_SAVE_SCOPE_V29.DIALOGS:
          return 'Настройки диалогов сохранены.';
        case SETTINGS_SAVE_SCOPE_V29.LOCATIONS:
          return 'Структура и синхронизация локаций сохранены.';
        case SETTINGS_SAVE_SCOPE_V29.AUTO_CLOSE:
          return 'Настройки автозакрытия сохранены.';
        default:
          return 'Настройки сохранены.';
      }
    }

    async function saveSettings(source) {
      const scope = resolveSaveScope(source);
      const payload = buildPayload(scope);
      if (!payload) {
        return false;
      }

      const saveUrl = typeof options.getSaveUrl === 'function' ? options.getSaveUrl() : '/settings';
      setSaveButtonBusy(source, true);
      try {
        const response = await fetch(saveUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });
        const data = await response.json();
        if (!response.ok || data.success !== true) {
          showMessage(`❌ Ошибка: ${data.error || `HTTP ${response.status}`}`, 'error');
          return false;
        }

        if (
          (scope === SETTINGS_SAVE_SCOPE_V29.LOCATIONS || scope === SETTINGS_SAVE_SCOPE_V29.ALL)
          && typeof options.markLocationsIikoServerSourcesSaved === 'function'
        ) {
          options.markLocationsIikoServerSourcesSaved();
        }
        if (
          scope === SETTINGS_SAVE_SCOPE_V29.ALL
          && typeof options.markNetBoxSyncSettingsSaved === 'function'
        ) {
          options.markNetBoxSyncSettingsSaved();
        }

        showMessage(`✅ ${successMessage(scope)}`, 'success');
        if (Array.isArray(data.warnings) && data.warnings.length) {
          showMessage(`⚠️ ${data.warnings.join('\n')}`, 'warning');
        }
        return true;
      } catch (error) {
        showMessage(`❌ Ошибка сети: ${error.message}`, 'error');
        return false;
      } finally {
        setSaveButtonBusy(source, false);
      }
    }

    return {
      saveSettings,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsSaveRuntime) {
        return window.__settingsSaveRuntime;
      }
      const runtime = createRuntime(options);
      window.__settingsSaveRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsSaveRuntime = Object.freeze(api);
}());
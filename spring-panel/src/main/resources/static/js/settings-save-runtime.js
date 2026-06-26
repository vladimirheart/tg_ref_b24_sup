(function () {
  if (window.SettingsSaveRuntime) {
    return;
  }

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

    function showMessage(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
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

    async function saveSettings() {
      try {
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
          showMessage(autoCloseState.errors.join('\n'));
          return;
        }
        if (!Array.isArray(autoCloseState.templates) || !autoCloseState.templates.length) {
          showMessage('Добавьте хотя бы один шаблон автозакрытия.');
          return;
        }

        const autoCloseFallbackHours = typeof options.getAutoCloseFallbackHours === 'function'
          ? options.getAutoCloseFallbackHours()
          : 24;
        const activeTemplate = autoCloseState.templates.find((template) => template.id === autoCloseState.active_template_id)
          || autoCloseState.templates[0];
        const autoCloseHours = activeTemplate?.hours || autoCloseFallbackHours;
        const autoClosePayload = {
          templates: autoCloseState.templates,
          active_template_id: autoCloseState.active_template_id,
        };

        const timeMetricsState = collectState(
          options.metricsRuntime,
          'collectTimeMetricsConfig',
          {
            config: {},
            errors: ['Не удалось собрать настройки time metrics.'],
          }
        );
        if (timeMetricsState.errors && timeMetricsState.errors.length) {
          showMessage(uniqueErrors(timeMetricsState.errors).join('\n'));
          return;
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
          showMessage(uniqueErrors(dialogSlaState.errors).join('\n'));
          return;
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
          showMessage(uniqueErrors(summaryBadgesState.errors).join('\n'));
          return;
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

        const networkProfilesPayload = options.networkProfilesRuntime?.collectNetworkProfilesPayload?.() || [];
        const payload = {
          categories: dialogTemplatesState.fallbackCategories || [],
          client_statuses: collectStatuses(),
          locations: typeof options.getLocationsState === 'function' ? options.getLocationsState() : {},
          locations_iiko_server_sources: typeof options.serializeLocationsIikoServerSources === 'function'
            ? options.serializeLocationsIikoServerSources()
            : [],
          locations_iiko_sync: typeof options.serializeLocationsIikoSyncSettings === 'function'
            ? options.serializeLocationsIikoSyncSettings()
            : {},
          network_profiles: networkProfilesPayload,
          dialog_category_templates: dialogTemplatesState.categoryTemplates || [],
          dialog_question_templates: dialogTemplatesState.questionTemplates || [],
          dialog_completion_templates: dialogTemplatesState.completionTemplates || [],
          dialog_macro_templates: dialogTemplatesState.macroTemplates || [],
          dialog_time_metrics: timeMetricsState.config || {},
          ...((dialogSlaState.config && typeof dialogSlaState.config === 'object') ? dialogSlaState.config : {}),
          dialog_summary_badges: { status: summaryBadgesState.status || {} },
          auto_close_config: autoClosePayload,
          auto_close_hours: autoCloseHours,
        };

        const saveUrl = typeof options.getSaveUrl === 'function' ? options.getSaveUrl() : '/settings';
        const response = await fetch(saveUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
        });

        const data = await response.json();
        if (data.success) {
          if (typeof options.markLocationsIikoServerSourcesSaved === 'function') {
            options.markLocationsIikoServerSourcesSaved();
          }
          showMessage('✅ Настройки сохранены');
          if (Array.isArray(data.warnings) && data.warnings.length) {
            showMessage(`⚠️ ${data.warnings.join('\n')}`);
          }
          return;
        }

        showMessage(`❌ Ошибка: ${data.error || 'неизвестная ошибка'}`);
      } catch (error) {
        showMessage(`❌ Ошибка сети: ${error.message}`);
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

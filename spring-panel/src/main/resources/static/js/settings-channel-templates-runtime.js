(function () {
  if (window.SettingsChannelTemplatesRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const botSettings = options.botSettingsInitial && typeof options.botSettingsInitial === 'object'
      ? options.botSettingsInitial
      : {};
    const autoCloseConfig = options.autoCloseConfig && typeof options.autoCloseConfig === 'object'
      ? options.autoCloseConfig
      : {};

    let questionTemplates = [];
    let questionTemplateMap = new Map();
    let defaultQuestionTemplateId = '';

    let ratingTemplates = [];
    let ratingTemplateMap = new Map();
    let defaultRatingTemplateId = '';

    let autoActionTemplates = [];
    let autoActionTemplateMap = new Map();
    let defaultAutoActionTemplateId = '';

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

    function pluralize(count, forms) {
      if (typeof options.pluralize === 'function') {
        return options.pluralize(count, forms);
      }
      return forms[2];
    }

    function sanitizeTemplateId(raw, map, fallback) {
      const value = typeof raw === 'string' ? raw.trim() : '';
      if (value && map instanceof Map && map.has(value)) {
        return value;
      }
      return fallback || '';
    }

    function normalizeTemplates(rawTemplates) {
      return Array.isArray(rawTemplates)
        ? rawTemplates.filter((tpl) => tpl && typeof tpl === 'object' && tpl.id)
        : [];
    }

    function resolveDefaultTemplateId(rawId, templateMap, templates) {
      const value = typeof rawId === 'string' ? rawId.trim() : '';
      if (value && templateMap.has(value)) {
        return value;
      }
      const first = templates.find((tpl) => tpl && tpl.id);
      return first ? String(first.id).trim() : '';
    }

    function applyBotSettings(nextBotSettings) {
      const source = nextBotSettings && typeof nextBotSettings === 'object' ? nextBotSettings : {};
      questionTemplates = normalizeTemplates(source.question_templates);
      questionTemplateMap = new Map(
        questionTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
      );
      defaultQuestionTemplateId = resolveDefaultTemplateId(
        source.active_template_id,
        questionTemplateMap,
        questionTemplates
      );

      ratingTemplates = normalizeTemplates(source.rating_templates);
      ratingTemplateMap = new Map(
        ratingTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
      );
      defaultRatingTemplateId = resolveDefaultTemplateId(
        source.active_rating_template_id,
        ratingTemplateMap,
        ratingTemplates
      );
    }

    function applyAutoCloseConfig(nextAutoCloseConfig) {
      const source = nextAutoCloseConfig && typeof nextAutoCloseConfig === 'object' ? nextAutoCloseConfig : {};
      autoActionTemplates = normalizeTemplates(source.templates);
      autoActionTemplateMap = new Map(
        autoActionTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
      );
      defaultAutoActionTemplateId = resolveDefaultTemplateId(
        source.active_template_id,
        autoActionTemplateMap,
        autoActionTemplates
      );
    }

    function replaceSource(nextBotSettings, nextAutoCloseConfig) {
      applyBotSettings(nextBotSettings);
      applyAutoCloseConfig(nextAutoCloseConfig);
    }

    replaceSource(botSettings, autoCloseConfig);

    function buildTemplateOptions(templates, selectedId) {
      if (!Array.isArray(templates) || !templates.length) {
        return '<option value="" selected>Нет доступных шаблонов</option>';
      }
      return templates
        .map((tpl) => {
          const id = String(tpl.id || '').trim();
          if (!id) {
            return '';
          }
          const name = tpl.name ? String(tpl.name).trim() : id;
          const selected = id === selectedId ? ' selected' : '';
          return `<option value="${escapeHtml(id)}"${selected}>${escapeHtml(name || id)}</option>`;
        })
        .filter(Boolean)
        .join('');
    }

    function buildTemplateSummary(template, detail) {
      const name = typeof template?.name === 'string' ? template.name.trim() : '';
      const normalizedDetail = typeof detail === 'string' ? detail.trim() : '';
      if (name && normalizedDetail) {
        return `${name} - ${normalizedDetail}`;
      }
      return name || normalizedDetail || 'Шаблон не выбран';
    }

    function getQuestionTemplateSummary(id) {
      if (!questionTemplateMap.size) {
        return 'Нет доступных шаблонов';
      }
      const templateId = sanitizeTemplateId(id, questionTemplateMap, defaultQuestionTemplateId);
      const template = questionTemplateMap.get(templateId);
      if (!template) {
        return 'Шаблон не выбран';
      }
      const flow = Array.isArray(template.question_flow) ? template.question_flow : [];
      const count = flow.length;
      const base = count
        ? `${count} ${pluralize(count, ['вопрос', 'вопроса', 'вопросов'])}`
        : 'Без вопросов';
      const description = typeof template.description === 'string' && template.description.trim()
        ? template.description.trim()
        : '';
      return buildTemplateSummary(template, description ? `${base} - ${description}` : base);
    }

    function getRatingTemplateSummary(id) {
      if (!ratingTemplateMap.size) {
        return 'Нет доступных шаблонов';
      }
      const templateId = sanitizeTemplateId(id, ratingTemplateMap, defaultRatingTemplateId);
      const template = ratingTemplateMap.get(templateId);
      if (!template) {
        return 'Шаблон не выбран';
      }
      const scale = Number.parseInt(template.scale_size, 10)
        || (Array.isArray(template.responses) ? template.responses.length : 0)
        || 0;
      const base = scale > 1 ? `Шкала 1-${scale}` : 'Единая оценка';
      const prompt = typeof template.prompt_text === 'string' && template.prompt_text.trim()
        ? template.prompt_text.trim()
        : '';
      return buildTemplateSummary(template, prompt ? `${base} - ${prompt}` : base);
    }

    function getAutoActionTemplateSummary(id) {
      if (!autoActionTemplateMap.size) {
        return 'Нет доступных шаблонов';
      }
      const templateId = sanitizeTemplateId(id, autoActionTemplateMap, defaultAutoActionTemplateId);
      const template = autoActionTemplateMap.get(templateId);
      if (!template) {
        return 'Шаблон не выбран';
      }
      const hours = Number.parseInt(template.hours, 10);
      const hasHours = Number.isFinite(hours) && hours > 0;
      const base = hasHours
        ? `Автозакрытие через ${hours} ${pluralize(hours, ['час', 'часа', 'часов'])}`
        : 'Автозакрытие отключено';
      const description = typeof template.description === 'string' && template.description.trim()
        ? template.description.trim()
        : '';
      return buildTemplateSummary(template, description ? `${base} - ${description}` : base);
    }

    const templateSummaryBuilders = {
      question_template_id: getQuestionTemplateSummary,
      rating_template_id: getRatingTemplateSummary,
      auto_action_template_id: getAutoActionTemplateSummary,
    };

    return {
      getQuestionTemplates() {
        return questionTemplates;
      },
      getQuestionTemplateMap() {
        return questionTemplateMap;
      },
      getDefaultQuestionTemplateId() {
        return defaultQuestionTemplateId;
      },
      getRatingTemplates() {
        return ratingTemplates;
      },
      getRatingTemplateMap() {
        return ratingTemplateMap;
      },
      getDefaultRatingTemplateId() {
        return defaultRatingTemplateId;
      },
      getAutoActionTemplates() {
        return autoActionTemplates;
      },
      getAutoActionTemplateMap() {
        return autoActionTemplateMap;
      },
      getDefaultAutoActionTemplateId() {
        return defaultAutoActionTemplateId;
      },
      sanitizeTemplateId,
      buildTemplateOptions,
      getQuestionTemplateSummary,
      getRatingTemplateSummary,
      getAutoActionTemplateSummary,
      getTemplateSummaryBuilders() {
        return templateSummaryBuilders;
      },
      replaceSource,
    };
  }

  const api = {
    mount(options = {}) {
      return createRuntime(options);
    },
  };

  window.SettingsChannelTemplatesRuntime = Object.freeze(api);
}());

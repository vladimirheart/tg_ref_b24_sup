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

    const questionTemplates = Array.isArray(botSettings.question_templates)
      ? botSettings.question_templates.filter((tpl) => tpl && typeof tpl === 'object' && tpl.id)
      : [];
    const questionTemplateMap = new Map(
      questionTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
    );
    const defaultQuestionTemplateId = (() => {
      const raw = typeof botSettings.active_template_id === 'string'
        ? botSettings.active_template_id.trim()
        : '';
      if (raw && questionTemplateMap.has(raw)) {
        return raw;
      }
      const first = questionTemplates.find((tpl) => tpl && tpl.id);
      return first ? String(first.id).trim() : '';
    })();

    const ratingTemplates = Array.isArray(botSettings.rating_templates)
      ? botSettings.rating_templates.filter((tpl) => tpl && typeof tpl === 'object' && tpl.id)
      : [];
    const ratingTemplateMap = new Map(
      ratingTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
    );
    const defaultRatingTemplateId = (() => {
      const raw = typeof botSettings.active_rating_template_id === 'string'
        ? botSettings.active_rating_template_id.trim()
        : '';
      if (raw && ratingTemplateMap.has(raw)) {
        return raw;
      }
      const first = ratingTemplates.find((tpl) => tpl && tpl.id);
      return first ? String(first.id).trim() : '';
    })();

    const autoActionTemplates = Array.isArray(autoCloseConfig.templates)
      ? autoCloseConfig.templates.filter((tpl) => tpl && typeof tpl === 'object' && tpl.id)
      : [];
    const autoActionTemplateMap = new Map(
      autoActionTemplates.map((tpl) => [String(tpl.id || '').trim(), tpl]),
    );
    const defaultAutoActionTemplateId = (() => {
      const raw = typeof autoCloseConfig.active_template_id === 'string'
        ? autoCloseConfig.active_template_id.trim()
        : '';
      if (raw && autoActionTemplateMap.has(raw)) {
        return raw;
      }
      const first = autoActionTemplates.find((tpl) => tpl && tpl.id);
      return first ? String(first.id).trim() : '';
    })();

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
    };
  }

  const api = {
    mount(options = {}) {
      return createRuntime(options);
    },
  };

  window.SettingsChannelTemplatesRuntime = Object.freeze(api);
}());

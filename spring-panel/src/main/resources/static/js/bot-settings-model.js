/* model responsibilities. Live context getters preserve the page's canonical state and business-scoped catalog. */
(function () {
  'use strict';
  window.BotQuestionModel = Object.freeze({
    create(context) {
      function normalizeStringArray(raw) {
        if (!Array.isArray(raw)) {
          return [];
        }
        return raw
          .map((value) => (typeof value === 'string' ? value.trim() : ''))
          .filter((value) => Boolean(value));
      }

      function normalizeQuestionRequired(raw, fallback = true) {
        if (typeof raw === 'boolean') {
          return raw;
        }
        if (typeof raw === 'string') {
          const normalized = raw.trim().toLowerCase();
          if (normalized === 'true' || normalized === '1') {
            return true;
          }
          if (normalized === 'false' || normalized === '0') {
            return false;
          }
        }
        return Boolean(fallback);
      }

      function generateQuestionId() {
        let id;
        do {
          id = `q_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
        } while (context.generatedQuestionIds.has(id));
        context.generatedQuestionIds.add(id);
        return id;
      }

      function ensureQuestionId(rawId) {
        if (typeof rawId === 'string' && rawId.trim()) {
          const trimmed = rawId.trim();
          context.generatedQuestionIds.add(trimmed);
          return trimmed;
        }
        return generateQuestionId();
      }

      function generateOptionId() {
        let id;
        do {
          id = `opt_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
        } while (context.generatedOptionIds.has(id));
        context.generatedOptionIds.add(id);
        return id;
      }

      function ensureOptionId(rawId) {
        if (typeof rawId === 'string' && rawId.trim()) {
          const trimmed = rawId.trim();
          context.generatedOptionIds.add(trimmed);
          return trimmed;
        }
        return generateOptionId();
      }

      function cloneQuestionOptions(rawOptions) {
        if (!Array.isArray(rawOptions)) {
          return [];
        }
        return rawOptions
          .map((option) => {
            if (!option || typeof option !== 'object') {
              return null;
            }
            const label = typeof option.label === 'string' ? option.label : '';
            return {
              id: ensureOptionId(option.id),
              label,
            };
          })
          .filter((option) => option && option.id);
      }

      function normalizeBindingKey(value) {
        if (typeof value !== 'string') {
          return '';
        }
        return value
          .trim()
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, '_')
          .replace(/^_+|_+$/g, '')
          .replace(/_+/g, '_');
      }

      function normalizeEditableQuestionOptions(rawOptions) {
        return cloneQuestionOptions(rawOptions)
          .map((option) => ({
            id: ensureOptionId(option && option.id),
            label: typeof option?.label === 'string' ? option.label.trim() : '',
          }))
          .filter((option) => option.label);
      }

      function resolveQuestionBindingKey(question) {
        const explicitKey = normalizeBindingKey(
          question && typeof question.bindingKey === 'string'
            ? question.bindingKey
            : (question && typeof question.binding_key === 'string' ? question.binding_key : ''),
        );
        if (explicitKey) {
          return explicitKey;
        }
        if (question && question.preset && typeof question.preset.field === 'string') {
          return question.preset.field.trim();
        }
        return '';
      }

      function isDashboardBindingKeyAllowed(value) {
        return typeof value === 'string' && context.DASHBOARD_ATTRIBUTE_KEY_SET.has(value.trim());
      }

      function suggestDashboardBindingKey(question) {
        const explicitKey = resolveQuestionBindingKey(question);
        if (isDashboardBindingKeyAllowed(explicitKey)) {
          return explicitKey;
        }
        const presetField = question && question.preset && typeof question.preset.field === 'string'
          ? question.preset.field.trim()
          : '';
        if (isDashboardBindingKeyAllowed(presetField)) {
          return presetField;
        }
        return '';
      }

      function resolveQuestionOptionLabels(question, meta) {
        if (!question) {
          return [];
        }
        if (question.type === 'preset') {
          return Array.isArray(meta && meta.options)
            ? meta.options
                .map((value) => (typeof value === 'string' ? value.trim() : ''))
                .filter((value) => value)
            : [];
        }
        if (question.type === 'select') {
          return normalizeEditableQuestionOptions(question.options).map((option) => option.label);
        }
        return [];
      }

      function cloneQuestionRoutes(rawRoutes) {
        if (!Array.isArray(rawRoutes)) {
          return [];
        }
        return rawRoutes
          .map((route) => {
            if (!route || typeof route !== 'object') {
              return null;
            }
            const valueId = typeof route.value_id === 'string'
              ? route.value_id.trim()
              : (typeof route.valueId === 'string' ? route.valueId.trim() : '');
            const nextQuestionId = typeof route.next_question_id === 'string'
              ? route.next_question_id.trim()
              : (typeof route.nextQuestionId === 'string' ? route.nextQuestionId.trim() : '');
            if (!valueId || !nextQuestionId) {
              return null;
            }
            return { valueId, nextQuestionId };
          })
          .filter((route) => route && route.valueId && route.nextQuestionId);
      }

      function resolveQuestionRouteChoices(question, meta) {
        if (!question) {
          return [];
        }
        if (question.type === 'select') {
          return normalizeEditableQuestionOptions(question.options).map((option) => ({
            id: option.id,
            label: option.label,
          }));
        }
        if (question.type === 'preset') {
          const excluded = new Set(
            Array.isArray(question.excludedOptions)
              ? question.excludedOptions
                .map((value) => (typeof value === 'string' ? value.trim() : ''))
                .filter((value) => value)
              : [],
          );
          return Array.isArray(meta && meta.options)
            ? meta.options
                .map((value) => (typeof value === 'string' ? value.trim() : ''))
                .filter((value) => value && !excluded.has(value))
                .map((value) => ({ id: value, label: value }))
            : [];
        }
        return [];
      }

      function resolveQuestionRouteTargets(questionIndex) {
        const targets = [];
        for (let i = questionIndex + 1; i < context.editorState.questionFlow.length; i += 1) {
          const candidate = context.editorState.questionFlow[i];
          if (!candidate || !candidate.id) {
            continue;
          }
          const title = String(candidate.text || '').trim() || `Вопрос ${i + 1}`;
          targets.push({
            id: candidate.id,
            label: `${i + 1}. ${title}`,
          });
        }
        targets.push({
          id: context.QUESTION_ROUTE_PROBLEM_ID,
          label: 'Финальный шаг: "Опишите проблему"',
        });
        return targets;
      }

      function sanitizeQuestionRoutes(question, questionIndex, meta) {
        if (!question || (question.type !== 'select' && question.type !== 'preset')) {
          return [];
        }
        const choiceIds = new Set(resolveQuestionRouteChoices(question, meta).map((option) => option.id));
        if (!choiceIds.size) {
          return [];
        }
        const targetIds = new Set(resolveQuestionRouteTargets(questionIndex).map((target) => target.id));
        return cloneQuestionRoutes(question.routes)
          .filter((route) => choiceIds.has(route.valueId) && targetIds.has(route.nextQuestionId));
      }

      function validateQuestionFlow(questionFlow, templateName, checkResources = true) {
        const usedBindingKeys = new Map();
        let businessQuestionCount = 0;
        for (let i = 0; i < questionFlow.length; i += 1) {
          const question = questionFlow[i] || {};
          const questionNumber = i + 1;
          const bindingKey = resolveQuestionBindingKey(question);
          const includeInDashboard = Boolean(question.includeInDashboard || question.include_in_dashboard);
          if (question.type === 'business') {
            businessQuestionCount += 1;
            if (businessQuestionCount > 1 || bindingKey !== 'business') {
              return `В шаблоне${templateName ? ` "${templateName}"` : ''} может быть только один вопрос «Бизнес канала» с ключом business.`;
            }
          }
          const meta = question.type === 'preset' && question.preset
            ? context.getPresetMeta(question.preset.group, question.preset.field)
            : null;
          if (question.type === 'preset') {
            if (!question.preset || !question.preset.group || !question.preset.field) {
              return `Выберите готовое поле для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''}.`;
            }
          } else {
            const text = String(question.text || '').trim();
            if (!text) {
              return `Укажите текст для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''}.`;
            }
            if (question.type === 'select') {
              const options = resolveQuestionOptionLabels(question, null);
              if (!options.length) {
                return `Добавьте хотя бы один вариант ответа для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''}.`;
              }
              if (new Set(options.map((value) => value.toLowerCase())).size !== options.length) {
                return `Варианты ответа для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''} должны быть уникальными.`;
              }
            }
          }
          const targets = new Set(questionFlow.slice(i + 1).map(item => item.id));
          const choices = new Set(resolveQuestionRouteChoices(question, meta).map(item => item.id));
          if (cloneQuestionRoutes(question.routes).some(route => !targets.has(route.nextQuestionId)
              || (!(question.type === 'preset' && !checkResources) && !choices.has(route.valueId)))) {
            return `Проверьте маршруты для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''}: часть переходов указывает на недоступные варианты или вопросы выше по сценарию.`;
          }
          if (includeInDashboard && !bindingKey) {
            return `Для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''} заполните ключ атрибута латиницей, если он должен попадать в дашборд.`;
          }
          if (includeInDashboard && !isDashboardBindingKeyAllowed(bindingKey)) {
            return `Для вопроса №${questionNumber}${templateName ? ` в шаблоне "${templateName}"` : ''} выберите атрибут из списка разрешённых значений дашборда.`;
          }
          const effectiveKey = normalizeBindingKey(question.bindingKey || question.binding_key || '') || question.id;
          if (effectiveKey) {
            const previousIndex = usedBindingKeys.get(effectiveKey);
            if (previousIndex != null) {
              return `Ключ атрибута "${effectiveKey}" повторяется в вопросах №${previousIndex + 1} и №${questionNumber}${templateName ? ` шаблона "${templateName}"` : ''}.`;
            }
            usedBindingKeys.set(effectiveKey, i);
          }
        }
        return null;
      }

      function generateTemplateId() {
        let id;
        do {
          id = `tpl_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
        } while (context.generatedTemplateIds.has(id));
        context.generatedTemplateIds.add(id);
        return id;
      }

      function ensureTemplateId(rawId) {
        if (typeof rawId === 'string' && rawId.trim()) {
          const trimmed = rawId.trim();
          context.generatedTemplateIds.add(trimmed);
          return trimmed;
        }
        return generateTemplateId();
      }

      function cloneQuestion(question, { preserveId = true } = {}) {
        const id = preserveId ? ensureQuestionId(question && question.id) : generateQuestionId();
        const preset = question && question.preset && question.preset.group && question.preset.field
          ? { group: question.preset.group, field: question.preset.field }
          : null;
        const options = cloneQuestionOptions(question && question.options);
        const excluded = Array.isArray(question && question.excludedOptions)
          ? Array.from(new Set(question.excludedOptions.map((value) => (value || '').toString())))
          : [];
        const filterState = question && typeof question.filterState === 'object'
          ? Object.assign({}, question.filterState)
          : {};
        const routes = cloneQuestionRoutes(question && question.routes);
        return {
          id,
          type: question && (question.type === 'preset' || question.type === 'select' || question.type === 'business') ? question.type : 'custom',
          text: question && typeof question.text === 'string' ? question.text : '',
          order: Number.isFinite(question && question.order) ? Number(question.order) : 0,
          preset,
          options,
          presetUnavailable: Boolean(question && question.presetUnavailable),
          excludedOptions: excluded,
          filterState,
          bindingKey: question && typeof question.bindingKey === 'string'
            ? question.bindingKey
            : (question && typeof question.binding_key === 'string' ? question.binding_key : ''),
          includeInDashboard: Boolean(question && (question.includeInDashboard || question.include_in_dashboard)),
          routes,
          required: question && (question.type === 'preset' || question.type === 'business')
            ? true
            : normalizeQuestionRequired(question && question.required, true),
        };
      }

      function cloneTemplate(template, { preserveIds = true } = {}) {
        const id = preserveIds ? ensureTemplateId(template && template.id) : generateTemplateId();
        const questionFlow = Array.isArray(template && template.questionFlow)
          ? template.questionFlow.map((question) => cloneQuestion(question, { preserveId: preserveIds }))
          : [];
        return {
          id,
          name: template && typeof template.name === 'string' ? template.name : 'Шаблон вопросов',
          description: template && typeof template.description === 'string' ? template.description : '',
          startMessage: template && typeof template.startMessage === 'string'
            ? template.startMessage
            : (template && typeof template.start_message === 'string' ? template.start_message : ''),
          firstResponseTimeoutMinutes: normalizeFirstResponseTimeoutMinutesValue(
            template && (template.firstResponseTimeoutMinutes ?? template.first_response_timeout_minutes)
          ),
          firstResponseTimeoutMessage: normalizeFirstResponseTimeoutMessageValue(
            template && (template.firstResponseTimeoutMessage ?? template.first_response_timeout_message)
          ),
          unifiedFields: Boolean(template && (template.unifiedFields || template.scope_mode === 'UNIFIED_FIELDS')),
          questionFlow,
        };
      }

      function normalizeQuestion(raw, order) {
        if (typeof raw === 'string') {
          const text = raw.trim();
          return {
            id: generateQuestionId(),
            type: 'custom',
            text,
            order,
            preset: null,
            options: [],
            presetUnavailable: false,
            excludedOptions: [],
            bindingKey: '',
            includeInDashboard: false,
            routes: [],
            required: true,
          };
        }

        const source = raw && typeof raw === 'object' ? raw : {};
        const id = ensureQuestionId(source.id);
        let type = source.type === 'preset' ? 'preset' : (source.type === 'select' ? 'select' : (source.type === 'business' ? 'business' : 'custom'));
        let text = typeof source.text === 'string' ? source.text.trim() : '';
        let preset = null;
        let presetUnavailable = false;
        const options = cloneQuestionOptions(source.options);
        if (type === 'preset') {
          const presetSource = source.preset && typeof source.preset === 'object' ? source.preset : source;
          let group = typeof presetSource.group === 'string' ? presetSource.group.trim() : '';
          let field = typeof presetSource.field === 'string' ? presetSource.field.trim() : '';
          if (context.presetExists(group, field)) {
            preset = { group, field };
            const meta = context.getPresetMeta(group, field);
            if (!text && meta) {
              text = meta.label;
            }
          } else {
            preset = group && field ? { group, field } : null;
            presetUnavailable = true;
          }
        }
        if (type === 'custom' && !text) {
          text = '';
        }
        const excludedOptions = [];
        const rawExcluded = source.excluded_options || source.excludedOptions;
        if (Array.isArray(rawExcluded)) {
          rawExcluded.forEach((value) => {
            if (typeof value === 'string' && value.trim()) {
              excludedOptions.push(value.trim());
            }
          });
        }
        return {
          id,
          type,
          text,
          order,
          preset,
          options,
          presetUnavailable,
          excludedOptions: Array.from(new Set(excludedOptions)),
          bindingKey: typeof source.binding_key === 'string'
            ? source.binding_key.trim()
            : (typeof source.bindingKey === 'string' ? source.bindingKey.trim() : ''),
          includeInDashboard: Boolean(source.include_in_dashboard || source.includeInDashboard),
          routes: cloneQuestionRoutes(source.routes),
          required: type === 'preset' || type === 'business' ? true : normalizeQuestionRequired(source.required, true),
        };
      }

      function normalizeTemplate(raw, index) {
        if (!raw || typeof raw !== 'object') {
          return null;
        }
        const templateId = ensureTemplateId(raw.id || `tpl_${index}`);
        const name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : 'Шаблон вопросов';
        const description = typeof raw.description === 'string' ? raw.description.trim() : '';
        const startMessageRaw = raw.start_message ?? raw.startMessage;
        const startMessage = typeof startMessageRaw === 'string' ? startMessageRaw.trim() : '';
        const firstResponseTimeoutMinutes = normalizeFirstResponseTimeoutMinutesValue(
          raw.first_response_timeout_minutes ?? raw.firstResponseTimeoutMinutes
        );
        const firstResponseTimeoutMessage = normalizeFirstResponseTimeoutMessageValue(
          raw.first_response_timeout_message ?? raw.firstResponseTimeoutMessage
        );
        const flowSource = Array.isArray(raw.question_flow) ? raw.question_flow : [];
        const questionFlow = flowSource
          .map((item, order) => normalizeQuestion(item, order + 1))
          .filter((question) => question.type !== 'custom' || question.text);
        return {
          id: templateId,
          name,
          description,
          startMessage,
          firstResponseTimeoutMinutes,
          firstResponseTimeoutMessage,
          unifiedFields: raw.scope_mode === 'UNIFIED_FIELDS',
          questionFlow,
        };
      }

      function normalizeCooldownMinutes(raw) {
        const source = raw && typeof raw === 'object' ? raw : {};
        const rawValue = source.unblock_request_cooldown_minutes ?? source.unblockRequestCooldownMinutes;
        const parsed = Number.parseInt(rawValue, 10);
        if (Number.isFinite(parsed) && parsed >= 0) {
          return parsed;
        }
        return 60;
      }

      function normalizeFirstResponseTimeoutMinutesValue(rawValue) {
        const parsed = Number.parseInt(rawValue, 10);
        if (Number.isFinite(parsed) && parsed >= 0) {
          return parsed;
        }
        return context.DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES;
      }

      function normalizeFirstResponseTimeoutMessageValue(rawValue) {
        const value = typeof rawValue === 'string' ? rawValue.trim() : '';
        return value || context.DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE;
      }

      function createLegacyDiagnostics() {
        return {};
      }

      function normalizeSettings(raw) {
        context.generatedQuestionIds.clear();
        context.generatedTemplateIds.clear();
        context.generatedRatingTemplateIds.clear();
        const source = raw && typeof raw === 'object' ? raw : {};
        const legacyDiagnostics = createLegacyDiagnostics();
        const templateSource = Array.isArray(source.question_templates) ? source.question_templates : [];
        const templates = templateSource
          .map((item, index) => normalizeTemplate(item, index))
          .filter((template) => Boolean(template));

        if (!templates.length) {
          const [group, field] = context.firstPreset();
          if (group && field) {
            templates.push({
              id: generateTemplateId(),
              name: 'Шаблон вопросов',
              description: '',
              startMessage: '',
              questionFlow: [
                {
                  id: generateQuestionId(),
                  type: 'preset',
                  text: context.getPresetMeta(group, field)?.label || 'Вопрос',
                  order: 1,
                  preset: { group, field },
                  excludedOptions: [],
                },
              ],
            });
          }
        }

        const activeIdRaw = typeof source.active_template_id === 'string' ? source.active_template_id.trim() : '';
        const activeTemplateId = templates.some((template) => template.id === activeIdRaw)
          ? activeIdRaw
          : templates[0]?.id;

        const ratingTemplateSource = Array.isArray(source.rating_templates) ? source.rating_templates : [];
        const ratingTemplates = ratingTemplateSource
          .map((item, index) => context.normalizeRatingTemplate(item, index))
          .filter((template) => template && template.promptText);

        if (!ratingTemplates.length) {
          const scale = 5;
          ratingTemplates.push({
            id: context.generateRatingTemplateId(),
            name: 'Шаблон оценок',
            description: '',
            promptText: context.defaultRatingPrompt(scale),
            scaleSize: scale,
            responses: context.defaultResponsesMap(scale),
          });
        }

        const activeRatingRaw = typeof source.active_rating_template_id === 'string'
          ? source.active_rating_template_id.trim()
          : '';
        const activeRatingTemplateId = ratingTemplates.some((template) => template.id === activeRatingRaw)
          ? activeRatingRaw
          : ratingTemplates[0]?.id;

        return {
          templates,
          activeTemplateId,
          ratingTemplates,
          activeRatingTemplateId,
          unblockCooldownMinutes: normalizeCooldownMinutes(source),
          legacyDiagnostics,
        };
      }

      function serializeQuestion(question, index) {
        const bindingKey = normalizeBindingKey(question.bindingKey || question.binding_key || '');
        const includeInDashboard = Boolean(question.includeInDashboard || question.include_in_dashboard);
        const meta = question.type === 'preset' && question.preset
          ? context.getPresetMeta(question.preset.group, question.preset.field)
          : null;
        const routes = cloneQuestionRoutes(question.routes);
        const entry = {
          id: question.id || generateQuestionId(),
          type: question.type === 'preset' ? 'preset' : (question.type === 'select' ? 'select' : (question.type === 'business' ? 'business' : 'custom')),
          text: String(question.text || '').trim(),
          order: index + 1,
          required: question.type === 'preset' || question.type === 'business' ? true : question.required !== false,
        };
        if (entry.type === 'preset' && question.preset && question.preset.group && question.preset.field) {
          entry.preset = { group: question.preset.group, field: question.preset.field };
          const excluded = Array.isArray(question.excludedOptions)
            ? question.excludedOptions.map((value) => (value || '').toString().trim()).filter((value) => value)
            : [];
          if (excluded.length) {
            entry.excluded_options = Array.from(new Set(excluded));
          }
        }
        if (entry.type === 'select') {
          entry.options = normalizeEditableQuestionOptions(question.options);
        }
        if (entry.type === 'business') {
          entry.binding_key = 'business';
          entry.include_in_dashboard = true;
        } else if (bindingKey) {
          entry.binding_key = bindingKey;
        }
        if (includeInDashboard) {
          entry.include_in_dashboard = true;
        }
        if (routes.length) {
          entry.routes = routes.map((route) => ({
            value_id: route.valueId,
            next_question_id: route.nextQuestionId,
          }));
        }
        return entry;
      }
      return { normalizeStringArray, normalizeQuestionRequired, generateQuestionId, ensureQuestionId, generateOptionId, ensureOptionId, cloneQuestionOptions, normalizeBindingKey, normalizeEditableQuestionOptions, resolveQuestionBindingKey, isDashboardBindingKeyAllowed, suggestDashboardBindingKey, resolveQuestionOptionLabels, cloneQuestionRoutes, resolveQuestionRouteChoices, resolveQuestionRouteTargets, sanitizeQuestionRoutes, validateQuestionFlow, generateTemplateId, ensureTemplateId, cloneQuestion, cloneTemplate, normalizeQuestion, normalizeTemplate, normalizeCooldownMinutes, normalizeFirstResponseTimeoutMinutesValue, normalizeFirstResponseTimeoutMessageValue, createLegacyDiagnostics, normalizeSettings, serializeQuestion };
    }
  });
})();

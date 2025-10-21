(function () {
  'use strict';

  if (typeof BOT_SETTINGS_INITIAL === 'undefined' || typeof BOT_PRESET_DEFINITIONS === 'undefined') {
    return;
  }

  const mainModal = document.querySelector('[data-bot-settings-modal]');
  if (!mainModal) {
    return;
  }

  const templateModalEl = document.getElementById('botTemplateEditorModal');
  const templateModal = templateModalEl && typeof bootstrap !== 'undefined'
    ? new bootstrap.Modal(templateModalEl)
    : null;

  const templatesContainer = mainModal.querySelector('[data-bot-templates-container]');
  const createTemplateButton = mainModal.querySelector('[data-bot-template-create]');
  const saveButton = mainModal.querySelector('[data-bot-settings-save]');
  const statusEl = mainModal.querySelector('[data-bot-settings-status]');
  const ratingPromptInput = mainModal.querySelector('[data-bot-rating-prompt]');
  const ratingScaleInput = mainModal.querySelector('[data-bot-rating-scale]');
  const ratingResponsesContainer = mainModal.querySelector('[data-bot-rating-responses]');

  const templateNameInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-name]') : null;
  const templateDescriptionInput = templateModalEl ? templateModalEl.querySelector('[data-bot-template-description]') : null;
  const questionsContainer = templateModalEl ? templateModalEl.querySelector('[data-bot-questions-container]') : null;
  const addQuestionButtons = templateModalEl ? templateModalEl.querySelectorAll('[data-bot-question-add]') : [];
  const presetHintsEl = templateModalEl ? templateModalEl.querySelector('[data-bot-preset-hints]') : null;
  const templateStatusEl = templateModalEl ? templateModalEl.querySelector('[data-bot-template-status]') : null;
  const templateSaveButton = templateModalEl ? templateModalEl.querySelector('[data-bot-template-save]') : null;
  const templateCancelButton = templateModalEl ? templateModalEl.querySelector('[data-bot-template-cancel]') : null;

  const PRESET_GROUPS = {};
  Object.entries(BOT_PRESET_DEFINITIONS || {}).forEach(([groupKey, groupValue]) => {
    if (!groupValue || typeof groupValue !== 'object') {
      return;
    }
    const label = typeof groupValue.label === 'string' ? groupValue.label.trim() : '';
    const fields = {};
    Object.entries(groupValue.fields || {}).forEach(([fieldKey, fieldValue]) => {
      if (!fieldValue || typeof fieldValue !== 'object') {
        return;
      }
      const fieldLabel = typeof fieldValue.label === 'string' ? fieldValue.label.trim() : '';
      const options = Array.isArray(fieldValue.options)
        ? fieldValue.options
            .map((value) => (typeof value === 'string' ? value.trim() : ''))
            .filter((value) => value)
        : [];
      const optionDependencies = fieldValue.option_dependencies && typeof fieldValue.option_dependencies === 'object'
        ? fieldValue.option_dependencies
        : {};
      const tree = fieldValue.tree && typeof fieldValue.tree === 'object' ? fieldValue.tree : null;
      fields[fieldKey] = {
        label: fieldLabel || fieldKey,
        options,
        optionDependencies,
        tree,
      };
    });
    PRESET_GROUPS[groupKey] = { label: label || groupKey, fields };
  });

  const generatedQuestionIds = new Set();
  const generatedTemplateIds = new Set();

  function html(value) {
    if (typeof window.escapeHtml === 'function') {
      return window.escapeHtml(value);
    }
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
  }

  function presetExists(group, field) {
    return Boolean(
      PRESET_GROUPS[group] &&
        PRESET_GROUPS[group].fields &&
        PRESET_GROUPS[group].fields[field]
    );
  }

  function firstPreset() {
    for (const [groupKey, groupValue] of Object.entries(PRESET_GROUPS)) {
      const fieldKeys = Object.keys(groupValue.fields || {});
      if (fieldKeys.length) {
        return [groupKey, fieldKeys[0]];
      }
    }
    return [null, null];
  }

  function getPresetMeta(group, field) {
    const groupValue = PRESET_GROUPS[group];
    if (!groupValue) {
      return null;
    }
    const fieldValue = (groupValue.fields || {})[field];
    if (!fieldValue) {
      return null;
    }
    return {
      label: fieldValue.label || field,
      options: Array.isArray(fieldValue.options) ? fieldValue.options : [],
      groupLabel: groupValue.label || group,
      optionDependencies: fieldValue.optionDependencies && typeof fieldValue.optionDependencies === 'object'
        ? fieldValue.optionDependencies
        : {},
      tree: fieldValue.tree && typeof fieldValue.tree === 'object' ? fieldValue.tree : null,
    };
  }

  function encodePresetValue(group, field) {
    if (!group || !field) {
      return '';
    }
    return `${group}::${field}`;
  }

  function decodePresetValue(raw) {
    if (typeof raw !== 'string') {
      return [null, null];
    }
    const parts = raw.split('::');
    if (parts.length !== 2) {
      return [null, null];
    }
    return [parts[0] || null, parts[1] || null];
  }

  function generateQuestionId() {
    let id;
    do {
      id = `q_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
    } while (generatedQuestionIds.has(id));
    generatedQuestionIds.add(id);
    return id;
  }

  function ensureQuestionId(rawId) {
    if (typeof rawId === 'string' && rawId.trim()) {
      const trimmed = rawId.trim();
      generatedQuestionIds.add(trimmed);
      return trimmed;
    }
    return generateQuestionId();
  }

  function generateTemplateId() {
    let id;
    do {
      id = `tpl_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
    } while (generatedTemplateIds.has(id));
    generatedTemplateIds.add(id);
    return id;
  }

  function ensureTemplateId(rawId) {
    if (typeof rawId === 'string' && rawId.trim()) {
      const trimmed = rawId.trim();
      generatedTemplateIds.add(trimmed);
      return trimmed;
    }
    return generateTemplateId();
  }

  function cloneQuestion(question, { preserveId = true } = {}) {
    const id = preserveId ? ensureQuestionId(question && question.id) : generateQuestionId();
    const preset = question && question.preset && question.preset.group && question.preset.field
      ? { group: question.preset.group, field: question.preset.field }
      : null;
    const excluded = Array.isArray(question && question.excludedOptions)
      ? Array.from(new Set(question.excludedOptions.map((value) => (value || '').toString())))
      : [];
    const filterState = question && typeof question.filterState === 'object'
      ? Object.assign({}, question.filterState)
      : {};
    return {
      id,
      type: question && question.type === 'preset' ? 'preset' : 'custom',
      text: question && typeof question.text === 'string' ? question.text : '',
      order: Number.isFinite(question && question.order) ? Number(question.order) : 0,
      preset,
      excludedOptions: excluded,
      filterState,
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
      questionFlow,
    };
  }

  function normalizeScale(value) {
    const num = Number.parseInt(value, 10);
    if (!Number.isFinite(num)) {
      return 5;
    }
    if (num < 1) {
      return 1;
    }
    if (num > 10) {
      return 10;
    }
    return num;
  }

  function normalizeQuestion(raw, order) {
    const source = raw && typeof raw === 'object' ? raw : {};
const id = ensureQuestionId(source.id);
    let type = source.type === 'preset' ? 'preset' : 'custom';
    let text = typeof source.text === 'string' ? source.text.trim() : '';
    let preset = null;
    if (type === 'preset') {
      const presetSource = source.preset && typeof source.preset === 'object' ? source.preset : source;
      let group = typeof presetSource.group === 'string' ? presetSource.group.trim() : '';
      let field = typeof presetSource.field === 'string' ? presetSource.field.trim() : '';
      if (!presetExists(group, field)) {
        const [fallbackGroup, fallbackField] = firstPreset();
        group = fallbackGroup || '';
        field = fallbackField || '';
      }
      if (presetExists(group, field)) {
        preset = { group, field };
        const meta = getPresetMeta(group, field);
        if (!text && meta) {
          text = meta.label;
        }
      } else {
        type = 'custom';
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
      excludedOptions: Array.from(new Set(excludedOptions)),
    };
  }

  function normalizeTemplate(raw, index) {
    if (!raw || typeof raw !== 'object') {
      return null;
    }
    const templateId = ensureTemplateId(raw.id || `tpl_${index}`);
    const name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : 'Шаблон вопросов';
    const description = typeof raw.description === 'string' ? raw.description.trim() : '';
    let flowSource = raw.question_flow;
    if (!Array.isArray(flowSource)) {
      flowSource = raw.questionFlow;
    }
    if (!Array.isArray(flowSource)) {
      const legacy = raw.questions;
      if (Array.isArray(legacy)) {
        flowSource = legacy
          .map((entry) => (typeof entry === 'string' && entry.trim() ? { text: entry.trim(), type: 'custom' } : null))
          .filter(Boolean);
      } else {
        flowSource = [];
      }
    }
    const questionFlow = flowSource
      .map((item, order) => normalizeQuestion(item, order + 1))
      .filter((question) => question.type !== 'custom' || question.text);
    return {
      id: templateId,
      name,
      description,
      questionFlow,
    };
  }

  function defaultResponsesMap(scale) {
    const map = {};
    for (let value = 1; value <= Math.max(1, scale); value += 1) {
      map[String(value)] = `Спасибо за вашу оценку ${value}!`;
    }
    return map;
  }

  function normalizeRatingSystem(raw) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const scale = normalizeScale(source.scale_size ?? source.scaleSize);
    const prompt = typeof source.prompt_text === 'string' && source.prompt_text.trim()
      ? source.prompt_text.trim()
      : '';
    const responses = {};
    const rawResponses = Array.isArray(source.responses)
      ? source.responses
      : source.responses && typeof source.responses === 'object'
        ? Object.entries(source.responses).map(([value, text]) => ({ value, text }))
        : [];
    rawResponses.forEach((item) => {
      if (!item || typeof item !== 'object') {
        return;
      }
      const rawValue = item.value;
      const rawText = item.text != null ? item.text : item.label;
      const value = Number.parseInt(rawValue, 10);
      if (!Number.isFinite(value)) {
        return;
      }
      const textValue = typeof rawText === 'string' ? rawText.trim() : '';
      if (!textValue) {
        return;
      }
      responses[String(value)] = textValue;
    });
    const defaults = Array.isArray(BOT_SETTINGS_INITIAL?.rating_system?.responses)
      ? BOT_SETTINGS_INITIAL.rating_system.responses.reduce((acc, item) => {
          if (item && typeof item === 'object') {
            const value = Number.parseInt(item.value, 10);
            const text = typeof item.text === 'string' ? item.text.trim() : '';
            if (Number.isFinite(value) && text) {
              acc[String(value)] = text;
            }
          }
          return acc;
        }, {})
      : defaultResponsesMap(scale);
    const ensured = {};
    for (let value = 1; value <= Math.max(1, scale); value += 1) {
      const key = String(value);
      ensured[key] = responses[key] || defaults[key] || `Спасибо за вашу оценку ${value}!`;
    }
    return {
      prompt_text: prompt,
      scale_size: scale,
      responses: ensured,
    };
  }

  function normalizeSettings(raw) {
    generatedQuestionIds.clear();
    generatedTemplateIds.clear();
    const source = raw && typeof raw === 'object' ? raw : {};
    const templateSource = Array.isArray(source.question_templates) ? source.question_templates : [];
    const templates = templateSource
      .map((item, index) => normalizeTemplate(item, index))
      .filter((template) => template && template.questionFlow.length);

    if (!templates.length) {
      const fallbackFlow = Array.isArray(source.question_flow) ? source.question_flow : [];
      const fallbackTemplate = normalizeTemplate(
        {
          id: source.active_template_id || generateTemplateId(),
          name: 'Импортированный сценарий',
          question_flow: fallbackFlow,
        },
        0,
      );
      if (fallbackTemplate && fallbackTemplate.questionFlow.length) {
        templates.push(fallbackTemplate);
      }
    }

    if (!templates.length && Array.isArray(BOT_SETTINGS_INITIAL?.question_flow)) {
      const defaultTemplate = normalizeTemplate(
        {
          id: generateTemplateId(),
          name: 'Базовый шаблон вопросов',
          question_flow: BOT_SETTINGS_INITIAL.question_flow,
        },
        0,
      );
      if (defaultTemplate && defaultTemplate.questionFlow.length) {
        templates.push(defaultTemplate);
      }
    }

    if (!templates.length) {
      const [group, field] = firstPreset();
      if (group && field) {
        templates.push({
          id: generateTemplateId(),
          name: 'Шаблон вопросов',
          description: '',
          questionFlow: [
            {
              id: generateQuestionId(),
              type: 'preset',
              text: getPresetMeta(group, field)?.label || 'Вопрос',
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

    const ratingSystem = normalizeRatingSystem(source.rating_system || source.ratingSystem);

    return {
      templates,
      activeTemplateId,
      ratingSystem,
    };
  }

  const state = {
    templates: [],
    activeTemplateId: null,
    ratingSystem: { prompt_text: '', scale_size: 5, responses: {} },
  };

  const editorState = {
    index: -1,
    templateId: null,
    name: '',
    description: '',
    questionFlow: [],
  };

  let initialState = normalizeSettings(BOT_SETTINGS_INITIAL);

  function hydrateStateFrom(source) {
    const normalized = normalizeSettings(source);
    state.templates = normalized.templates.map((template) => cloneTemplate(template));
    state.activeTemplateId = normalized.activeTemplateId;
    state.ratingSystem = {
      prompt_text: normalized.ratingSystem.prompt_text || '',
      scale_size: normalized.ratingSystem.scale_size || 5,
      responses: Object.assign({}, normalized.ratingSystem.responses || {}),
    };
  }

  function setStatus(message, isError) {
    if (!statusEl) {
      return;
    }
    statusEl.classList.remove('text-danger', 'text-success');
    if (!message) {
      statusEl.textContent = '';
      return;
    }
    statusEl.textContent = message;
    statusEl.classList.add(isError ? 'text-danger' : 'text-success');
    setTimeout(() => {
      if (statusEl.textContent === message) {
        statusEl.textContent = '';
        statusEl.classList.remove('text-danger', 'text-success');
      }
    }, 4000);
  }

  function setTemplateStatus(message, isError) {
    if (!templateStatusEl) {
      return;
    }
    templateStatusEl.classList.remove('text-danger', 'text-success');
    if (!message) {
      templateStatusEl.textContent = '';
      return;
    }
    templateStatusEl.textContent = message;
    templateStatusEl.classList.add(isError ? 'text-danger' : 'text-success');
    if (!isError) {
      setTimeout(() => {
        if (templateStatusEl.textContent === message) {
          templateStatusEl.textContent = '';
          templateStatusEl.classList.remove('text-danger', 'text-success');
        }
      }, 3000);
    }
  }

  function renderTemplates() {
    if (!templatesContainer) {
      return;
    }
    templatesContainer.innerHTML = '';
    if (!state.templates.length) {
      const placeholder = document.createElement('div');
      placeholder.className = 'alert alert-light border mb-0';
      placeholder.textContent = 'Шаблоны не созданы. Добавьте шаблон, чтобы настроить вопросы бота.';
      templatesContainer.appendChild(placeholder);
      return;
    }
    state.templates.forEach((template) => {
      const card = document.createElement('div');
      card.className = 'card shadow-sm';
      card.dataset.templateId = template.id;
      const questionsCount = template.questionFlow.length;
      const descriptionHtml = template.description
        ? `<p class="small text-muted mb-1">${html(template.description)}</p>`
        : '';
      const summary = questionsCount === 1 ? '1 вопрос' : `${questionsCount} вопросов`;
      card.innerHTML = `
        <div class="card-body d-flex flex-column flex-lg-row justify-content-between align-items-start gap-3">
          <div>
            <h6 class="mb-1">${html(template.name || 'Шаблон вопросов')}</h6>
            ${descriptionHtml}
            <div class="small text-muted">${html(summary)}</div>
          </div>
          <div class="d-flex flex-column align-items-lg-end gap-2 w-100 w-lg-auto">
            <div class="form-check form-switch align-self-start align-self-lg-end">
              <input class="form-check-input" type="radio" name="bot-template-default" value="${html(template.id)}" ${template.id === state.activeTemplateId ? 'checked' : ''} data-bot-template-select>
              <label class="form-check-label small">Использовать по умолчанию</label>
            </div>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-primary" type="button" data-bot-template-edit>Редактировать</button>
              <button class="btn btn-outline-secondary" type="button" data-bot-template-duplicate>Дублировать</button>
              <button class="btn btn-outline-danger" type="button" data-bot-template-delete ${state.templates.length === 1 ? 'disabled' : ''}>Удалить</button>
            </div>
          </div>
        </div>
      `;
      templatesContainer.appendChild(card);
    });
  }

  function ensureRatingResponses(scale) {
    const responses = state.ratingSystem.responses || {};
    const ensured = {};
    for (let value = 1; value <= Math.max(1, scale); value += 1) {
      const key = String(value);
      if (responses[key] && responses[key].trim()) {
        ensured[key] = responses[key];
      } else {
        ensured[key] = `Спасибо за вашу оценку ${value}!`;
      }
    }
    state.ratingSystem.responses = ensured;
  }

  function renderRatingResponses() {
    if (!ratingResponsesContainer) {
      return;
    }
    ratingResponsesContainer.innerHTML = '';
    const scale = state.ratingSystem.scale_size || 5;
    ensureRatingResponses(scale);
    for (let value = 1; value <= Math.max(1, scale); value += 1) {
      const textValue = state.ratingSystem.responses[String(value)] || '';
      const card = document.createElement('div');
      card.className = 'card border rounded-3';
      card.innerHTML = `
        <div class="card-body p-3">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <span class="fw-semibold">Оценка ${value}</span>
            <span class="badge bg-light text-muted">${value}</span>
          </div>
          <textarea class="form-control" rows="2" data-bot-rating-response data-value="${value}">${html(textValue)}</textarea>
        </div>
      `;
      ratingResponsesContainer.appendChild(card);
    }
  }

  function renderRating() {
    if (ratingPromptInput) {
      ratingPromptInput.value = state.ratingSystem.prompt_text || '';
    }
    if (ratingScaleInput) {
      ratingScaleInput.value = state.ratingSystem.scale_size || 5;
    }
    renderRatingResponses();
  }

  function renderPresetHints() {
    if (!presetHintsEl) {
      return;
    }
    const rows = [];
    Object.entries(PRESET_GROUPS).forEach(([groupKey, groupValue]) => {
      const fieldEntries = Object.entries(groupValue.fields || {});
      if (!fieldEntries.length) {
        return;
      }
      const parts = fieldEntries.map(([fieldKey, fieldValue]) => {
        const options = fieldValue.options || [];
        if (options.length) {
          const preview = options.slice(0, 5).join(', ');
          return `${fieldValue.label || fieldKey} (например: ${preview}${options.length > 5 ? ' ...' : ''})`;
        }
        return fieldValue.label || fieldKey;
      });
      rows.push(`${groupValue.label || groupKey}: ${parts.join(' | ')}`);
    });
    presetHintsEl.textContent = rows.length
      ? rows.join('  ||  ')
      : 'Нет доступных полей. Добавьте данные в разделе "Структура локаций".';
  }

  function renderHiddenSummary(card, question, meta) {
    if (!card) {
      return;
    }
    const summaryEl = card.querySelector('[data-bot-question-hidden-summary]');
    if (!summaryEl) {
      return;
    }
    const options = (meta && meta.options) || [];
    const excluded = Array.isArray(question.excludedOptions) ? question.excludedOptions : [];
    if (!options.length) {
      summaryEl.textContent = 'Готовые варианты недоступны';
      return;
    }
    if (!excluded.length) {
      summaryEl.textContent = 'Все варианты отображаются пользователю';
      return;
    }
    if (excluded.length >= options.length) {
      summaryEl.textContent = 'Все варианты скрыты';
      return;
    }
    summaryEl.textContent = `Скрыто: ${excluded.length} из ${options.length}`;
  }

  function collectDependencyValues(optionDependencies) {
    const values = {
      business: new Set(),
      location_type: new Set(),
    };
    if (!optionDependencies || typeof optionDependencies !== 'object') {
      return values;
    }
    Object.values(optionDependencies).forEach((entry) => {
      if (!entry || typeof entry !== 'object') {
        return;
      }
      const businessValues = Array.isArray(entry.business) ? entry.business : [];
      businessValues.forEach((value) => {
        if (typeof value === 'string' && value.trim()) {
          values.business.add(value.trim());
        }
      });
      const typeValues = Array.isArray(entry.location_type) ? entry.location_type : [];
      typeValues.forEach((value) => {
        if (typeof value === 'string' && value.trim()) {
          values.location_type.add(value.trim());
        }
      });
    });
    return values;
  }

  function buildFilterSelectHtml(key, label, valuesSet) {
    if (!valuesSet || typeof valuesSet.forEach !== 'function' || valuesSet.size <= 1) {
      return '';
    }
    const collator = typeof Intl !== 'undefined' && Intl.Collator
      ? new Intl.Collator('ru', { sensitivity: 'base' })
      : null;
    const sorted = Array.from(valuesSet).sort((a, b) => {
      if (collator) {
        return collator.compare(a, b);
      }
      return a.localeCompare(b);
    });
    const options = ['<option value="">Все</option>'].concat(
      sorted.map((value) => `<option value="${html(value)}">${html(value)}</option>`),
    );
    return `
      <div class="d-flex align-items-center gap-1" data-bot-question-filter-wrapper="${html(key)}">
        <span class="small text-muted">${html(label)}:</span>
        <select class="form-select form-select-sm" data-bot-question-filter="${html(key)}">
          ${options.join('')}
        </select>
      </div>
    `;
  }

  function buildFilterControls(meta) {
    if (!meta || typeof meta.optionDependencies !== 'object') {
      return '';
    }
    const dependencyValues = collectDependencyValues(meta.optionDependencies);
    const pieces = [];
    const businessFilter = buildFilterSelectHtml('business', 'Бизнес', dependencyValues.business);
    if (businessFilter) {
      pieces.push(businessFilter);
    }
    const locationTypeFilter = buildFilterSelectHtml('location_type', 'Тип бизнеса', dependencyValues.location_type);
    if (locationTypeFilter) {
      pieces.push(locationTypeFilter);
    }
    if (!pieces.length) {
      return '';
    }
    return `<div class="d-flex flex-wrap gap-2 align-items-center" data-bot-question-filters>${pieces.join('')}</div>`;
  }

  function applyQuestionFilters(card) {
    if (!card) {
      return;
    }
    const container = card.querySelector('[data-bot-question-options]');
    if (!container) {
      return;
    }
    const filters = {
      business: card.dataset.filterBusiness || '',
      location_type: card.dataset.filterLocationType || '',
    };
    let visibleCount = 0;
    container.querySelectorAll('.form-check').forEach((row) => {
      if (!(row instanceof HTMLElement)) {
        return;
      }
      let visible = true;
      if (filters.business) {
        const tags = (row.dataset.businessTags || '').split('|').filter(Boolean);
        if (!tags.includes(filters.business)) {
          visible = false;
        }
      }
      if (visible && filters.location_type) {
        const tags = (row.dataset.locationTypeTags || '').split('|').filter(Boolean);
        if (!tags.includes(filters.location_type)) {
          visible = false;
        }
      }
      row.classList.toggle('d-none', !visible);
      if (visible) {
        visibleCount += 1;
      }
    });
    const placeholder = container.querySelector('[data-bot-question-options-empty]');
    if (placeholder) {
      placeholder.classList.toggle('d-none', visibleCount > 0);
    }
  }


  function renderQuestions() {
    if (!questionsContainer) {
      return;
    }
    questionsContainer.innerHTML = '';
    if (!editorState.questionFlow.length) {
      const empty = document.createElement('div');
      empty.className = 'alert alert-light border mb-0';
      empty.textContent = 'Вопросы не заданы. Добавьте вопрос, чтобы сохранить шаблон.';
      questionsContainer.appendChild(empty);
      renderPresetHints();
      return;
    }
    editorState.questionFlow.forEach((question, index) => {
      question.order = index + 1;
      const card = document.createElement('div');
      card.className = 'card shadow-sm';
      card.dataset.index = String(index);
      const preset = question.preset && question.preset.group && question.preset.field ? question.preset : null;
      const presetValue = preset ? encodePresetValue(preset.group, preset.field) : '';
      const isPreset = question.type === 'preset' && preset;
      const presetOptions = (() => {
        const groups = Object.entries(PRESET_GROUPS);
        if (!groups.length) {
          return '<option value="" disabled>Нет доступных полей</option>';
        }
        const pieces = ['<option value="">Выберите поле</option>'];
        groups.forEach(([groupKey, groupValue]) => {
          const options = Object.entries(groupValue.fields || {});
          if (!options.length) {
            return;
          }
          const htmlOptions = options
            .map(([fieldKey, fieldValue]) => {
              const value = encodePresetValue(groupKey, fieldKey);
              const selected = presetValue === value ? ' selected' : '';
              return `<option value="${html(value)}"${selected}>${html(fieldValue.label || fieldKey)}</option>`;
            })
            .join('');
          pieces.push(`<optgroup label="${html(groupValue.label || groupKey)}">${htmlOptions}</optgroup>`);
        });
        return pieces.join('');
      })();
      const meta = preset ? getPresetMeta(preset.group, preset.field) : null;
      const options = meta && Array.isArray(meta.options) ? meta.options : [];
      const optionDependencies = meta && typeof meta.optionDependencies === 'object' ? meta.optionDependencies : {};
      const excluded = new Set(Array.isArray(question.excludedOptions) ? question.excludedOptions : []);
      let optionsHtml;
      if (options.length) {
        const items = options
          .map((option) => {
            const optionValue = typeof option === 'string' ? option.trim() : '';
            if (!optionValue) {
              return '';
            }
            const deps = optionDependencies[optionValue] && typeof optionDependencies[optionValue] === 'object'
              ? optionDependencies[optionValue]
              : {};
            const locationTypeTags = Array.isArray(deps.location_type)
              ? deps.location_type.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim())
              : [];
            const businessTags = Array.isArray(deps.business)
              ? deps.business.filter((value) => typeof value === 'string' && value.trim()).map((value) => value.trim())
              : [];
            const locationTypeAttr = locationTypeTags.length
              ? ` data-location-type-tags="${html(locationTypeTags.join('|'))}"`
              : '';
            const businessAttr = businessTags.length
              ? ` data-business-tags="${html(businessTags.join('|'))}"`
              : '';
            const checked = excluded.has(optionValue) ? '' : ' checked';
            return `
              <div class="form-check form-check-sm"${locationTypeAttr}${businessAttr}>
                <input class="form-check-input" type="checkbox" value="${html(optionValue)}" data-bot-question-option${checked}>
                <label class="form-check-label">${html(optionValue)}</label>
              </div>
            `;
          })
          .filter(Boolean);
        if (items.length) {
          items.push('<div class="text-muted small d-none" data-bot-question-options-empty>Нет вариантов для выбранных фильтров.</div>');
          optionsHtml = items.join('');
        } else {
          optionsHtml = '<div class="text-muted small">Нет готовых значений для скрытия.</div>';
        }
      } else {
        optionsHtml = '<div class="text-muted small">Нет готовых значений для скрытия.</div>';
      }
      const hintText = meta && meta.options && meta.options.length
        ? `Варианты: ${meta.options.slice(0, 5).join(', ')}${meta.options.length > 5 ? ' ...' : ''}`
        : 'Значения берутся из выбранного справочника.';
      const filtersHtml = isPreset ? buildFilterControls(meta) : '';
      const filterState = question.filterState && typeof question.filterState === 'object'
        ? question.filterState
        : {};
      card.dataset.filterBusiness = filterState.business || '';
      card.dataset.filterLocationType = filterState.location_type || '';
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
            <div class="d-flex align-items-center gap-2">
              <span class="badge bg-primary rounded-pill">${index + 1}</span>
              <span class="text-muted small">Порядок вопроса</span>
            </div>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-up" ${index === 0 ? 'disabled' : ''}>↑</button>
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-down" ${index === editorState.questionFlow.length - 1 ? 'disabled' : ''}>↓</button>
              <button class="btn btn-outline-danger" type="button" data-bot-question-action="remove">Удалить</button>
            </div>
          </div>
          <div class="row g-3 align-items-end">
            <div class="col-lg-6">
              <label class="form-label">Текст вопроса</label>
              <input type="text" class="form-control" value="${html(question.text || '')}" data-bot-question-text>
            </div>
            <div class="col-lg-3">
              <label class="form-label">Тип вопроса</label>
              <select class="form-select" data-bot-question-type>
                <option value="custom"${isPreset ? '' : ' selected'}>Свободный текст</option>
                <option value="preset"${isPreset ? ' selected' : ''}>Готовое поле</option>
              </select>
            </div>
            <div class="col-lg-3${isPreset ? '' : ' d-none'}" data-bot-question-preset-wrapper>
              <label class="form-label">Готовое поле</label>
              <select class="form-select" data-bot-question-preset>
                ${presetOptions}
              </select>
              <div class="form-text small text-muted" data-bot-question-preset-hint>${html(hintText)}</div>
            </div>
          </div>
          <div class="mt-3${isPreset ? '' : ' d-none'}" data-bot-question-options-wrapper>
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
              <div class="d-flex align-items-center gap-2">
                <span class="small fw-semibold">Варианты для отображения</span>
                <span class="small text-muted" data-bot-question-hidden-summary></span>
              </div>
              ${filtersHtml}
            </div>
            <div class="border rounded-3 p-3 bg-light" data-bot-question-options>
              ${optionsHtml}
            </div>
          </div>
        </div>
      `;
      questionsContainer.appendChild(card);
      if (isPreset) {
        const select = card.querySelector('[data-bot-question-preset]');
        if (select) {
          select.value = presetValue;
        }
      }
      const filterSelects = card.querySelectorAll('[data-bot-question-filter]');
      filterSelects.forEach((select) => {
        if (!(select instanceof HTMLSelectElement)) {
          return;
        }
        const filterKey = select.dataset.botQuestionFilter;
        if (filterKey === 'business') {
          select.value = card.dataset.filterBusiness || '';
        } else if (filterKey === 'location_type') {
          select.value = card.dataset.filterLocationType || '';
        }
      });
      applyQuestionFilters(card);
      renderHiddenSummary(card, question, meta);
    });
    renderPresetHints();
  }
  function openTemplateEditor(index) {
    if (!templateModal) {
      return;
    }
    if (typeof index === 'number' && index >= 0 && index < state.templates.length) {
      const template = state.templates[index];
      editorState.index = index;
      editorState.templateId = template.id;
      editorState.name = template.name;
      editorState.description = template.description;
      editorState.questionFlow = template.questionFlow.map((question) => cloneQuestion(question));
    } else {
      editorState.index = -1;
      editorState.templateId = generateTemplateId();
      editorState.name = 'Новый шаблон вопросов';
      editorState.description = '';
      editorState.questionFlow = [];
    }
    if (templateNameInput) {
      templateNameInput.value = editorState.name || '';
    }
    if (templateDescriptionInput) {
      templateDescriptionInput.value = editorState.description || '';
    }
    setTemplateStatus('', false);
    renderQuestions();
    templateModal.show();
  }

  function saveTemplateFromEditor() {
    const name = templateNameInput ? templateNameInput.value.trim() : '';
    const description = templateDescriptionInput ? templateDescriptionInput.value.trim() : '';
    if (!name) {
      setTemplateStatus('Укажите название шаблона.', true);
      if (templateNameInput) {
        templateNameInput.focus();
      }
      return false;
    }
    if (!editorState.questionFlow.length) {
      setTemplateStatus('Добавьте хотя бы один вопрос.', true);
      return false;
    }
    for (let i = 0; i < editorState.questionFlow.length; i += 1) {
      const question = editorState.questionFlow[i];
      if (question.type === 'custom') {
        if (!question.text || !question.text.trim()) {
          setTemplateStatus(`Укажите текст для вопроса №${i + 1}.`, true);
          return false;
        }
      } else if (!question.preset || !presetExists(question.preset.group, question.preset.field)) {
        setTemplateStatus(`Выберите готовое поле для вопроса №${i + 1}.`, true);
        return false;
      }
    }
    const normalizedQuestions = editorState.questionFlow.map((question, index) => {
      const entry = {
        id: question.id || generateQuestionId(),
        type: question.type === 'preset' ? 'preset' : 'custom',
        text: String(question.text || '').trim(),
        order: index + 1,
      };
      if (entry.type === 'preset' && question.preset && presetExists(question.preset.group, question.preset.field)) {
        entry.preset = { group: question.preset.group, field: question.preset.field };
        const excluded = Array.isArray(question.excludedOptions)
          ? question.excludedOptions.map((value) => (value || '').toString().trim()).filter((value) => value)
          : [];
        if (excluded.length) {
          entry.excluded_options = Array.from(new Set(excluded));
        }
      }
      return entry;
    });

    const templatePayload = {
      id: editorState.templateId || generateTemplateId(),
      name,
      description,
      questionFlow: normalizedQuestions.map((question) => ({
        id: question.id,
        type: question.type,
        text: question.text,
        order: question.order,
        preset: question.preset ? { group: question.preset.group, field: question.preset.field } : undefined,
        excludedOptions: Array.isArray(question.excluded_options)
          ? question.excluded_options.slice()
          : Array.isArray(question.excludedOptions)
            ? question.excludedOptions.slice()
            : [],
      })),
    };

    if (editorState.index >= 0 && editorState.index < state.templates.length) {
      state.templates[editorState.index] = {
        id: templatePayload.id,
        name: templatePayload.name,
        description: templatePayload.description,
        questionFlow: templatePayload.questionFlow.map((question) => ({
          id: question.id,
          type: question.type,
          text: question.text,
          order: question.order,
          preset: question.preset ? { group: question.preset.group, field: question.preset.field } : null,
          excludedOptions: Array.isArray(question.excludedOptions) ? question.excludedOptions : [],
        })),
      };
    } else {
      state.templates.push({
        id: templatePayload.id,
        name: templatePayload.name,
        description: templatePayload.description,
        questionFlow: templatePayload.questionFlow.map((question) => ({
          id: question.id,
          type: question.type,
          text: question.text,
          order: question.order,
          preset: question.preset ? { group: question.preset.group, field: question.preset.field } : null,
          excludedOptions: Array.isArray(question.excludedOptions) ? question.excludedOptions : [],
        })),
      });
      if (!state.activeTemplateId) {
        state.activeTemplateId = templatePayload.id;
      }
    }

    templateModal.hide();
    renderTemplates();
    return true;
  }

  function addQuestion(type) {
    const question = {
      id: generateQuestionId(),
      type: type === 'preset' ? 'preset' : 'custom',
      text: '',
      order: editorState.questionFlow.length + 1,
      preset: null,
      excludedOptions: [],
      filterState: {},
    };
    if (question.type === 'preset') {
      const [group, field] = firstPreset();
      if (group && field) {
        question.preset = { group, field };
        const meta = getPresetMeta(group, field);
        if (meta) {
          question.text = meta.label;
        }
      } else {
        question.type = 'custom';
      }
    }
    editorState.questionFlow.push(question);
    renderQuestions();
    if (questionsContainer) {
      const cards = questionsContainer.querySelectorAll('.card');
      if (cards.length) {
        const lastCard = cards[cards.length - 1];
        const input = lastCard.querySelector('[data-bot-question-text]');
        if (input) {
          input.focus();
        }
      }
    }
  }

  function moveQuestion(index, delta) {
    const newIndex = index + delta;
    if (newIndex < 0 || newIndex >= editorState.questionFlow.length) {
      return;
    }
    const [item] = editorState.questionFlow.splice(index, 1);
    editorState.questionFlow.splice(newIndex, 0, item);
    renderQuestions();
  }

  function removeQuestion(index) {
    if (index < 0 || index >= editorState.questionFlow.length) {
      return;
    }
    editorState.questionFlow.splice(index, 1);
    renderQuestions();
  }

  function setQuestionType(index, type) {
    const question = editorState.questionFlow[index];
    if (!question) {
      return;
    }
    if (type === 'preset') {
      let group = question.preset && question.preset.group;
      let field = question.preset && question.preset.field;
      if (!presetExists(group, field)) {
        const [fallbackGroup, fallbackField] = firstPreset();
        group = fallbackGroup;
        field = fallbackField;
      }
      if (presetExists(group, field)) {
        const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
        question.preset = { group, field };
        question.type = 'preset';
        const meta = getPresetMeta(group, field);
        if (!question.text || (previousMeta && question.text === previousMeta.label)) {
          question.text = meta ? meta.label : question.text;
        }
        question.excludedOptions = Array.isArray(question.excludedOptions) ? question.excludedOptions : [];
      } else {
        setTemplateStatus('Нет доступных готовых полей. Добавьте данные в структуре локаций.', true);
        question.type = 'custom';
        question.preset = null;
        question.excludedOptions = [];
      }
    } else {
      question.type = 'custom';
      question.preset = null;
      question.excludedOptions = [];
    }
    question.filterState = {};
    renderQuestions();
  }

  function updateQuestionPreset(index, group, field) {
    const question = editorState.questionFlow[index];
    if (!question) {
      return;
    }
    if (!presetExists(group, field)) {
      setTemplateStatus('Выберите поле из списка готовых вариантов.', true);
      renderQuestions();
      return;
    }
    const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
    question.preset = { group, field };
    question.type = 'preset';
    const meta = getPresetMeta(group, field);
    if (!question.text || (previousMeta && meta && question.text === previousMeta.label)) {
      question.text = meta ? meta.label : question.text;
    }
    question.excludedOptions = Array.isArray(question.excludedOptions) ? question.excludedOptions : [];
    question.filterState = {};
    renderQuestions();
  }

  function updateOptionVisibility(index, value, shouldHide) {
    const question = editorState.questionFlow[index];
    if (!question || typeof value !== 'string') {
      return;
    }
    const normalized = value.trim();
    if (!normalized) {
      return;
    }
    if (!Array.isArray(question.excludedOptions)) {
      question.excludedOptions = [];
    }
    const set = new Set(question.excludedOptions);
    if (shouldHide) {
      set.add(normalized);
    } else {
      set.delete(normalized);
    }
    question.excludedOptions = Array.from(set);
    const card = questionsContainer ? questionsContainer.querySelector(`.card[data-index="${index}"]`) : null;
    const meta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
    renderHiddenSummary(card, question, meta);
  }

  function resetState() {
    hydrateStateFrom(initialState);
    renderTemplates();
    renderRating();
    setStatus('', false);
  }

  function serializeQuestion(question, index) {
    const entry = {
      id: question.id || generateQuestionId(),
      type: question.type === 'preset' ? 'preset' : 'custom',
      text: String(question.text || '').trim(),
      order: index + 1,
    };
    if (entry.type === 'preset' && question.preset && presetExists(question.preset.group, question.preset.field)) {
      entry.preset = { group: question.preset.group, field: question.preset.field };
      const excluded = Array.isArray(question.excludedOptions)
        ? question.excludedOptions.map((value) => (value || '').toString().trim()).filter((value) => value)
        : [];
      if (excluded.length) {
        entry.excluded_options = Array.from(new Set(excluded));
      }
    }
    return entry;
  }

  function collectPayload() {
    const templatesPayload = state.templates.map((template) => ({
      id: template.id,
      name: template.name,
      description: template.description,
      question_flow: template.questionFlow.map(serializeQuestion),
    }));
    let activeTemplateId = state.activeTemplateId;
    if (!templatesPayload.some((template) => template.id === activeTemplateId)) {
      activeTemplateId = templatesPayload.length ? templatesPayload[0].id : null;
    }
    const activeTemplate = templatesPayload.find((template) => template.id === activeTemplateId);
    const scale = normalizeScale(state.ratingSystem.scale_size);
    ensureRatingResponses(scale);
    const responses = [];
    for (let value = 1; value <= Math.max(1, scale); value += 1) {
      const key = String(value);
      responses.push({ value, text: String(state.ratingSystem.responses[key] || '').trim() });
    }
    return {
      question_templates: templatesPayload,
      active_template_id: activeTemplateId,
      question_flow: activeTemplate ? activeTemplate.question_flow : [],
      rating_system: {
        prompt_text: String(state.ratingSystem.prompt_text || '').trim(),
        scale_size: scale,
        responses,
      },
    };
  }

  function validatePayload(payload) {
    const templates = Array.isArray(payload.question_templates) ? payload.question_templates : [];
    if (!templates.length) {
      return 'Добавьте хотя бы один шаблон вопросов.';
    }
    const activeId = payload.active_template_id;
    const activeTemplate = templates.find((template) => template.id === activeId) || templates[0];
    const activeFlow = Array.isArray(activeTemplate && activeTemplate.question_flow)
      ? activeTemplate.question_flow
      : [];
    if (!activeFlow.length) {
      return 'Активный шаблон должен содержать хотя бы один вопрос.';
    }
    for (let i = 0; i < activeFlow.length; i += 1) {
      const question = activeFlow[i];
      if (question.type === 'preset') {
        if (!question.preset || !question.preset.group || !question.preset.field) {
          return `Выберите готовое поле для вопроса №${i + 1}.`;
        }
      } else if (!question.text) {
        return `Укажите текст для вопроса №${i + 1}.`;
      }
    }
    if (!payload.rating_system || !payload.rating_system.prompt_text) {
      return 'Укажите текст запроса оценки.';
    }
    const responses = Array.isArray(payload.rating_system.responses) ? payload.rating_system.responses : [];
    if (!responses.length) {
      return 'Укажите сообщения для каждой оценки.';
    }
    for (const response of responses) {
      if (!response || !String(response.text || '').trim()) {
        return 'Каждое значение оценки должно иметь текст ответа.';
      }
    }
    return null;
  }

  async function saveBotSettings() {
    if (!saveButton) {
      return;
    }
    const payload = collectPayload();
    const errorMessage = validatePayload(payload);
    if (errorMessage) {
      setStatus(errorMessage, true);
      return;
    }
    saveButton.disabled = true;
    const originalText = saveButton.textContent;
    saveButton.textContent = 'Сохранение...';
    setStatus('Сохраняем настройки...', false);
    try {
      const response = await fetch('/settings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ bot_settings: payload }),
      });
      const data = await response.json();
      if (!response.ok || data.success === false) {
        throw new Error((data && data.error) || `HTTP ${response.status}`);
      }
      setStatus('Настройки сохранены.', false);
      initialState = normalizeSettings(payload);
      hydrateStateFrom(initialState);
      renderTemplates();
      renderRating();
    } catch (error) {
      const message = error && error.message ? error.message : String(error);
      setStatus(`Ошибка сохранения: ${message}`, true);
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = originalText || 'Сохранить';
    }
  }

  function findTemplateIndexById(id) {
    return state.templates.findIndex((template) => template.id === id);
  }

  function duplicateTemplate(index) {
    if (index < 0 || index >= state.templates.length) {
      return;
    }
    const copy = cloneTemplate(state.templates[index], { preserveIds: false });
    copy.name = `${state.templates[index].name} (копия)`;
    state.templates.splice(index + 1, 0, copy);
    renderTemplates();
  }

  function deleteTemplate(index) {
    if (state.templates.length <= 1 || index < 0 || index >= state.templates.length) {
      return;
    }
    const removed = state.templates.splice(index, 1)[0];
    if (removed && removed.id === state.activeTemplateId) {
      state.activeTemplateId = state.templates.length ? state.templates[0].id : null;
    }
    renderTemplates();
  }

  if (mainModal) {
    mainModal.addEventListener('show.bs.modal', () => {
      resetState();
    });
  }

  if (createTemplateButton) {
    createTemplateButton.addEventListener('click', () => {
      openTemplateEditor(-1);
    });
  }

  if (templatesContainer) {
    templatesContainer.addEventListener('click', (event) => {
      const card = event.target.closest('.card[data-template-id]');
      if (!card) {
        return;
      }
      const templateId = card.dataset.templateId;
      const index = findTemplateIndexById(templateId);
      if (index === -1) {
        return;
      }
      if (event.target.closest('[data-bot-template-edit]')) {
        openTemplateEditor(index);
        return;
      }
      if (event.target.closest('[data-bot-template-duplicate]')) {
        duplicateTemplate(index);
        return;
      }
      if (event.target.closest('[data-bot-template-delete]')) {
        if (state.templates.length <= 1) {
          return;
        }
        if (confirm('Удалить шаблон вопросов?')) {
          deleteTemplate(index);
        }
        return;
      }
    });

    templatesContainer.addEventListener('change', (event) => {
      const input = event.target.closest('[data-bot-template-select]');
      if (!input) {
        return;
      }
      const templateId = input.value;
      if (state.templates.some((template) => template.id === templateId)) {
        state.activeTemplateId = templateId;
      }
    });
  }

  addQuestionButtons.forEach((button) => {
    button.addEventListener('click', () => {
      const type = button.dataset.type || button.getAttribute('data-type');
      addQuestion(type === 'preset' ? 'preset' : 'custom');
    });
  });

  if (questionsContainer) {
    questionsContainer.addEventListener('input', (event) => {
      const input = event.target.closest('[data-bot-question-text]');
      if (!input) {
        return;
      }
      const card = input.closest('.card[data-index]');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (Number.isFinite(index) && editorState.questionFlow[index]) {
        editorState.questionFlow[index].text = input.value;
      }
    });

    questionsContainer.addEventListener('change', (event) => {
      const card = event.target.closest('.card[data-index]');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (!Number.isFinite(index)) {
        return;
      }
      const filterSelect = event.target.closest('[data-bot-question-filter]');
      if (filterSelect) {
        const filterKey = filterSelect.dataset.botQuestionFilter;
        const value = filterSelect.value || '';
        if (filterKey === 'business') {
          card.dataset.filterBusiness = value;
        } else if (filterKey === 'location_type') {
          card.dataset.filterLocationType = value;
        }
        const question = editorState.questionFlow[index];
        if (question) {
          if (!question.filterState || typeof question.filterState !== 'object') {
            question.filterState = {};
          }
          if (filterKey === 'business' || filterKey === 'location_type') {
            question.filterState[filterKey] = value;
          }
        }
        applyQuestionFilters(card);
        return;
      }
      const typeSelect = event.target.closest('[data-bot-question-type]');
      if (typeSelect) {
        setQuestionType(index, typeSelect.value === 'preset' ? 'preset' : 'custom');
        return;
      }
      const presetSelect = event.target.closest('[data-bot-question-preset]');
      if (presetSelect) {
        const [group, field] = decodePresetValue(presetSelect.value);
        if (group && field) {
          updateQuestionPreset(index, group, field);
        }
        return;
      }
      const optionCheckbox = event.target.closest('[data-bot-question-option]');
      if (optionCheckbox) {
        updateOptionVisibility(index, optionCheckbox.value, !optionCheckbox.checked);
      }
    });

    questionsContainer.addEventListener('click', (event) => {
      const button = event.target.closest('[data-bot-question-action]');
      if (!button) {
        return;
      }
      const card = button.closest('.card[data-index]');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (!Number.isFinite(index)) {
        return;
      }
      const action = button.dataset.botQuestionAction;
      if (action === 'move-up') {
        moveQuestion(index, -1);
      } else if (action === 'move-down') {
        moveQuestion(index, 1);
      } else if (action === 'remove') {
        removeQuestion(index);
      }
    });
  }

  if (templateSaveButton) {
    templateSaveButton.addEventListener('click', () => {
      saveTemplateFromEditor();
    });
  }

  if (templateCancelButton && templateModal) {
    templateCancelButton.addEventListener('click', () => {
      templateModal.hide();
    });
  }

  if (templateModalEl) {
    templateModalEl.addEventListener('hidden.bs.modal', () => {
      setTemplateStatus('', false);
    });
  }

  if (ratingPromptInput) {
    ratingPromptInput.addEventListener('input', () => {
      state.ratingSystem.prompt_text = ratingPromptInput.value;
    });
  }

  if (ratingScaleInput) {
    ratingScaleInput.addEventListener('input', () => {
      state.ratingSystem.scale_size = normalizeScale(ratingScaleInput.value);
      renderRatingResponses();
    });
    ratingScaleInput.addEventListener('blur', () => {
      state.ratingSystem.scale_size = normalizeScale(ratingScaleInput.value);
      ratingScaleInput.value = state.ratingSystem.scale_size;
      renderRatingResponses();
    });
  }

  if (ratingResponsesContainer) {
    ratingResponsesContainer.addEventListener('input', (event) => {
      const textarea = event.target.closest('[data-bot-rating-response]');
      if (!textarea) {
        return;
      }
      const value = Number.parseInt(textarea.dataset.value, 10);
      if (!Number.isFinite(value)) {
        return;
      }
      if (!state.ratingSystem.responses) {
        state.ratingSystem.responses = {};
      }
      state.ratingSystem.responses[String(value)] = textarea.value;
    });
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      saveBotSettings();
    });
  }
})();

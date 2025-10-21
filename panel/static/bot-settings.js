(function () {
  'use strict';

  if (typeof BOT_SETTINGS_INITIAL === 'undefined' || typeof BOT_PRESET_DEFINITIONS === 'undefined') {
    return;
  }

  const modal = document.querySelector('[data-bot-settings-modal]');
  if (!modal) {
    return;
  }

  const questionsContainer = modal.querySelector('[data-bot-questions-container]');
  const presetsHintEl = modal.querySelector('[data-bot-preset-hints]');
  const addQuestionButton = modal.querySelector('[data-bot-question-add]');
  const ratingScaleInput = modal.querySelector('[data-bot-rating-scale]');
  const ratingActionsContainer = modal.querySelector('[data-bot-rating-actions]');
  const addActionButton = modal.querySelector('[data-bot-rating-add-action]');
  const saveButton = modal.querySelector('[data-bot-settings-save]');
  const statusEl = modal.querySelector('[data-bot-settings-status]');

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
      fields[fieldKey] = { label: fieldLabel || fieldKey, options };
    });
    PRESET_GROUPS[groupKey] = { label: label || groupKey, fields };
  });

  const generatedIds = new Set();

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

  function generateQuestionId() {
    let id;
    do {
      id = `q_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
    } while (generatedIds.has(id));
    generatedIds.add(id);
    return id;
  }

  function ensureQuestionId(rawId) {
    if (typeof rawId === 'string' && rawId.trim()) {
      const trimmed = rawId.trim();
      generatedIds.add(trimmed);
      return trimmed;
    }
    return generateQuestionId();
  }

  function normalizeQuestion(raw, order) {
    const source = raw && typeof raw === 'object' ? raw : {};
    const question = {
      id: ensureQuestionId(source.id),
      type: 'custom',
      text: '',
      order,
      preset: null,
    };
    let type = typeof source.type === 'string' ? source.type.trim().toLowerCase() : 'custom';
    if (type !== 'preset') {
      type = 'custom';
    }
    let text = '';
    if (typeof source.text === 'string' && source.text.trim()) {
      text = source.text.trim();
    } else if (typeof source.label === 'string' && source.label.trim()) {
      text = source.label.trim();
    }
    const presetSource = source.preset && typeof source.preset === 'object' ? source.preset : source;
    let group = typeof presetSource.group === 'string' ? presetSource.group.trim() : '';
    let field = typeof presetSource.field === 'string' ? presetSource.field.trim() : '';
    if (type === 'preset') {
      if (presetExists(group, field)) {
        const meta = getPresetMeta(group, field);
        if (meta && !text) {
          text = meta.label;
        }
        question.preset = { group, field };
      } else {
        const [fallbackGroup, fallbackField] = firstPreset();
        if (fallbackGroup && fallbackField) {
          const meta = getPresetMeta(fallbackGroup, fallbackField);
          question.preset = { group: fallbackGroup, field: fallbackField };
          if (!text && meta) {
            text = meta.label;
          }
        } else {
          type = 'custom';
        }
      }
    }
    question.type = type;
    question.text = text;
    return question;
  }

  function normalizeSettings(raw) {
    generatedIds.clear();
    const source = raw && typeof raw === 'object' ? raw : {};
    const rawFlow = Array.isArray(source.question_flow)
      ? source.question_flow
      : Array.isArray(source.questionFlow)
        ? source.questionFlow
        : [];
    const questionFlow = rawFlow.map((item, index) => normalizeQuestion(item, index + 1));
    const ratingRaw =
      source.rating_system && typeof source.rating_system === 'object'
        ? source.rating_system
        : source.ratingSystem && typeof source.ratingSystem === 'object'
          ? source.ratingSystem
          : {};
    const scale = normalizeScale(ratingRaw.scale_size ?? ratingRaw.scaleSize);
    const actionsSource = Array.isArray(ratingRaw.post_actions)
      ? ratingRaw.post_actions
      : Array.isArray(ratingRaw.postActions)
        ? ratingRaw.postActions
        : [];
    const postActions = actionsSource
      .map((action) => {
        if (typeof action === 'string') {
          return action.trim();
        }
        if (action && typeof action === 'object') {
          return String(action.text || action.label || '').trim();
        }
        return '';
      })
      .filter((value) => value);
    return {
      questionFlow,
      ratingSystem: {
        scale_size: scale,
        post_actions: postActions,
      },
    };
  }

  const state = {
    questionFlow: [],
    ratingSystem: { scale_size: 5, post_actions: [] },
  };

  let initialState = normalizeSettings(BOT_SETTINGS_INITIAL);

  function hydrateStateFrom(source) {
    generatedIds.clear();
    const flow = Array.isArray(source.questionFlow) ? source.questionFlow : [];
    state.questionFlow = flow.map((item, index) => {
      const id = ensureQuestionId(item && item.id);
      const type = item && item.type === 'preset' ? 'preset' : 'custom';
      let text = item && typeof item.text === 'string' ? item.text : '';
      let preset = null;
      if (type === 'preset' && item && item.preset && presetExists(item.preset.group, item.preset.field)) {
        preset = { group: item.preset.group, field: item.preset.field };
        if (!text) {
          const meta = getPresetMeta(preset.group, preset.field);
          text = meta ? meta.label : '';
        }
      } else if (type === 'preset') {
        const [fallbackGroup, fallbackField] = firstPreset();
        if (fallbackGroup && fallbackField) {
          preset = { group: fallbackGroup, field: fallbackField };
          const meta = getPresetMeta(fallbackGroup, fallbackField);
          if (!text && meta) {
            text = meta.label;
          }
        } else {
          text = text || '';
        }
      }
      return {
        id,
        type,
        text,
        order: index + 1,
        preset,
      };
    });
    const ratingSource = source && typeof source.ratingSystem === 'object' ? source.ratingSystem : {};
    state.ratingSystem = {
      scale_size: normalizeScale(ratingSource.scale_size),
      post_actions: Array.isArray(ratingSource.post_actions)
        ? ratingSource.post_actions.map((action) => String(action || '').trim()).filter((value) => value)
        : [],
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

  function renderPresetHints() {
    if (!presetsHintEl) {
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
    presetsHintEl.textContent = rows.length
      ? rows.join('  ||  ')
      : 'Нет доступных полей. Добавьте данные в разделе "Структура локаций".';
  }

  function buildPresetSelectOptions(selectedGroup, selectedField) {
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
          const selected = selectedGroup === groupKey && selectedField === fieldKey ? ' selected' : '';
          return `<option value="${html(value)}"${selected}>${html(fieldValue.label || fieldKey)}</option>`;
        })
        .join('');
      pieces.push(`<optgroup label="${html(groupValue.label || groupKey)}">${htmlOptions}</optgroup>`);
    });
    return pieces.join('');
  }

  function renderQuestions() {
    if (!questionsContainer) {
      return;
    }
    questionsContainer.innerHTML = '';
    if (!state.questionFlow.length) {
      const empty = document.createElement('div');
      empty.className = 'alert alert-light border mb-0';
      empty.textContent = 'Вопросы не заданы. Добавьте хотя бы один вопрос.';
      questionsContainer.appendChild(empty);
      return;
    }
    state.questionFlow.forEach((question, index) => {
      question.order = index + 1;
      const card = document.createElement('div');
      card.className = 'card shadow-sm';
      card.dataset.index = String(index);
      const preset = question.preset && question.preset.group && question.preset.field ? question.preset : null;
      const presetValue = preset ? encodePresetValue(preset.group, preset.field) : '';
      const presetOptions = buildPresetSelectOptions(preset ? preset.group : null, preset ? preset.field : null);
      const isPreset = question.type === 'preset';
      const meta = preset ? getPresetMeta(preset.group, preset.field) : null;
      const hintText = meta && meta.options && meta.options.length
        ? `Варианты: ${meta.options.slice(0, 5).join(', ')}${meta.options.length > 5 ? ' ...' : ''}`
        : 'Значения берутся из выбранного справочника.';
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
            <div class="d-flex align-items-center gap-2">
              <span class="badge bg-primary rounded-pill">${index + 1}</span>
              <span class="text-muted small">Порядок вопроса</span>
            </div>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-up" ${index === 0 ? 'disabled' : ''}>↑</button>
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-down" ${index === state.questionFlow.length - 1 ? 'disabled' : ''}>↓</button>
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
        </div>
      `;
      questionsContainer.appendChild(card);
      if (preset && presetValue) {
        const select = card.querySelector('[data-bot-question-preset]');
        if (select) {
          select.value = presetValue;
        }
      }
    });
  }

  function renderRating() {
    if (ratingScaleInput) {
      ratingScaleInput.value = state.ratingSystem.scale_size || 5;
    }
    renderRatingActions();
  }

  function renderRatingActions() {
    if (!ratingActionsContainer) {
      return;
    }
    ratingActionsContainer.innerHTML = '';
    const actions = state.ratingSystem.post_actions || [];
    if (!actions.length) {
      const placeholder = document.createElement('div');
      placeholder.className = 'text-muted small';
      placeholder.textContent = 'Действия не указаны.';
      ratingActionsContainer.appendChild(placeholder);
      return;
    }
    actions.forEach((action, index) => {
      const group = document.createElement('div');
      group.className = 'input-group input-group-sm';
      group.dataset.index = String(index);
      group.innerHTML = `
        <input type="text" class="form-control" value="${html(action)}" data-bot-rating-action-input>
        <button class="btn btn-outline-danger" type="button" data-bot-rating-action="remove">&times;</button>
      `;
      ratingActionsContainer.appendChild(group);
    });
  }

  function resetState() {
    hydrateStateFrom(initialState);
    renderQuestions();
    renderRating();
    renderPresetHints();
    setStatus('', false);
  }

  function addQuestion(type) {
    const question = {
      id: generateQuestionId(),
      type: type === 'preset' ? 'preset' : 'custom',
      text: '',
      order: state.questionFlow.length + 1,
      preset: null,
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
    state.questionFlow.push(question);
    renderQuestions();
    const cards = questionsContainer ? questionsContainer.querySelectorAll('.card') : [];
    if (cards.length) {
      const lastCard = cards[cards.length - 1];
      const input = lastCard.querySelector('[data-bot-question-text]');
      if (input) {
        input.focus();
      }
    }
  }

  function moveQuestion(index, delta) {
    const newIndex = index + delta;
    if (newIndex < 0 || newIndex >= state.questionFlow.length) {
      return;
    }
    const [item] = state.questionFlow.splice(index, 1);
    state.questionFlow.splice(newIndex, 0, item);
    renderQuestions();
  }

  function removeQuestion(index) {
    if (index < 0 || index >= state.questionFlow.length) {
      return;
    }
    state.questionFlow.splice(index, 1);
    renderQuestions();
  }

  function setQuestionType(index, type) {
    const question = state.questionFlow[index];
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
      if (group && field) {
        const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
        question.preset = { group, field };
        const meta = getPresetMeta(group, field);
        if (!question.text || (previousMeta && question.text === previousMeta.label)) {
          question.text = meta ? meta.label : question.text;
        }
        question.type = 'preset';
      } else {
        setStatus('Нет доступных готовых полей. Добавьте данные в структуре локаций.', true);
        question.type = 'custom';
        question.preset = null;
      }
    } else {
      question.type = 'custom';
      question.preset = null;
    }
    renderQuestions();
  }

  function updateQuestionPreset(index, group, field) {
    const question = state.questionFlow[index];
    if (!question) {
      return;
    }
    if (!presetExists(group, field)) {
      setStatus('Выберите поле из списка готовых вариантов.', true);
      renderQuestions();
      return;
    }
    const previousMeta = question.preset ? getPresetMeta(question.preset.group, question.preset.field) : null;
    question.preset = { group, field };
    question.type = 'preset';
    const meta = getPresetMeta(group, field);
    if (!question.text || (previousMeta && question.text === previousMeta.label)) {
      question.text = meta ? meta.label : question.text;
    }
    renderQuestions();
  }

  function collectPayload() {
    const questions = state.questionFlow.map((item, index) => {
      const entry = {
        id: item.id || generateQuestionId(),
        type: item.type === 'preset' ? 'preset' : 'custom',
        text: String(item.text || '').trim(),
        order: index + 1,
      };
      if (entry.type === 'preset' && item.preset && presetExists(item.preset.group, item.preset.field)) {
        entry.preset = { group: item.preset.group, field: item.preset.field };
      }
      return entry;
    });
    const scale = normalizeScale(state.ratingSystem.scale_size);
    const actions = (state.ratingSystem.post_actions || [])
      .map((action) => (action || '').toString().trim())
      .filter((action) => action);
    return {
      question_flow: questions,
      rating_system: {
        scale_size: scale,
        post_actions: actions,
      },
    };
  }

  function validatePayload(payload) {
    const questions = Array.isArray(payload.question_flow) ? payload.question_flow : [];
    if (!questions.length) {
      return 'Добавьте хотя бы один вопрос.';
    }
    for (let i = 0; i < questions.length; i += 1) {
      const question = questions[i];
      if (question.type === 'preset') {
        if (!question.preset || !question.preset.group || !question.preset.field) {
          return `Выберите готовое поле для вопроса №${i + 1}.`;
        }
      } else if (!question.text) {
        return `Укажите текст для вопроса №${i + 1}.`;
      }
    }
    const scale = Number.parseInt(payload.rating_system && payload.rating_system.scale_size, 10);
    if (!Number.isFinite(scale) || scale < 1) {
      return 'Количество оценок должно быть не меньше 1.';
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
      renderQuestions();
      renderRating();
      renderPresetHints();
    } catch (error) {
      const message = error && error.message ? error.message : String(error);
      setStatus(`Ошибка сохранения: ${message}`, true);
    } finally {
      saveButton.disabled = false;
      saveButton.textContent = originalText || 'Сохранить';
    }
  }

  modal.addEventListener('show.bs.modal', () => {
    resetState();
  });

  if (addQuestionButton) {
    addQuestionButton.addEventListener('click', () => {
      addQuestion('custom');
    });
  }

  if (questionsContainer) {
    questionsContainer.addEventListener('input', (event) => {
      const input = event.target.closest('[data-bot-question-text]');
      if (!input) {
        return;
      }
      const card = input.closest('.card');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (Number.isFinite(index) && state.questionFlow[index]) {
        state.questionFlow[index].text = input.value;
      }
    });

    questionsContainer.addEventListener('change', (event) => {
      const typeSelect = event.target.closest('[data-bot-question-type]');
      const presetSelect = event.target.closest('[data-bot-question-preset]');
      const card = event.target.closest('.card');
      if (!card) {
        return;
      }
      const index = Number.parseInt(card.dataset.index, 10);
      if (!Number.isFinite(index)) {
        return;
      }
      if (typeSelect) {
        setQuestionType(index, typeSelect.value === 'preset' ? 'preset' : 'custom');
        return;
      }
      if (presetSelect) {
        const [group, field] = decodePresetValue(presetSelect.value);
        if (group && field) {
          updateQuestionPreset(index, group, field);
        }
      }
    });

    questionsContainer.addEventListener('click', (event) => {
      const button = event.target.closest('[data-bot-question-action]');
      if (!button) {
        return;
      }
      const card = button.closest('.card');
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

  if (ratingScaleInput) {
    ratingScaleInput.addEventListener('input', () => {
      state.ratingSystem.scale_size = normalizeScale(ratingScaleInput.value);
    });
    ratingScaleInput.addEventListener('blur', () => {
      state.ratingSystem.scale_size = normalizeScale(ratingScaleInput.value);
      ratingScaleInput.value = state.ratingSystem.scale_size;
    });
  }

  if (addActionButton) {
    addActionButton.addEventListener('click', () => {
      state.ratingSystem.post_actions.push('');
      renderRatingActions();
      if (ratingActionsContainer) {
        const rows = ratingActionsContainer.querySelectorAll('[data-index]');
        if (rows.length) {
          const lastRow = rows[rows.length - 1];
          const input = lastRow.querySelector('[data-bot-rating-action-input]');
          if (input) {
            input.focus();
          }
        }
      }
    });
  }

  if (ratingActionsContainer) {
    ratingActionsContainer.addEventListener('input', (event) => {
      const input = event.target.closest('[data-bot-rating-action-input]');
      if (!input) {
        return;
      }
      const row = input.closest('[data-index]');
      if (!row) {
        return;
      }
      const index = Number.parseInt(row.dataset.index, 10);
      if (Number.isFinite(index)) {
        state.ratingSystem.post_actions[index] = input.value;
      }
    });

    ratingActionsContainer.addEventListener('click', (event) => {
      const button = event.target.closest('[data-bot-rating-action="remove"]');
      if (!button) {
        return;
      }
      const row = button.closest('[data-index]');
      if (!row) {
        return;
      }
      const index = Number.parseInt(row.dataset.index, 10);
      if (Number.isFinite(index)) {
        state.ratingSystem.post_actions.splice(index, 1);
        renderRatingActions();
      }
    });
  }

  if (saveButton) {
    saveButton.addEventListener('click', () => {
      saveBotSettings();
    });
  }
})();

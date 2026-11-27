/* rating responsibilities. Live context getters preserve the page's canonical state and business-scoped catalog. */
(function () {
  'use strict';
  window.BotRatingEditor = Object.freeze({
    create(context) {
      function generateRatingTemplateId() {
        let id;
        do {
          id = `rtpl_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
        } while (context.generatedRatingTemplateIds.has(id));
        context.generatedRatingTemplateIds.add(id);
        return id;
      }

      function ensureRatingTemplateId(rawId) {
        if (typeof rawId === 'string' && rawId.trim()) {
          const trimmed = rawId.trim();
          context.generatedRatingTemplateIds.add(trimmed);
          return trimmed;
        }
        return generateRatingTemplateId();
      }

      function defaultRatingPrompt(scale) {
        const normalized = normalizeScale(scale);
        if (normalized <= 1) {
          return 'Пожалуйста, оцените качество ответа: отправьте число 1.';
        }
        return `Пожалуйста, оцените качество ответа от 1 до ${normalized}.`;
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

      function normalizeRatingResponses(rawResponses, scale) {
        const collected = {};
        if (Array.isArray(rawResponses)) {
          rawResponses.forEach((item) => {
            if (!item || typeof item !== 'object') {
              return;
            }
            const rawValue = item.value != null ? item.value : item.key;
            const rawText = item.text != null ? item.text : item.label;
            const value = Number.parseInt(rawValue, 10);
            if (!Number.isFinite(value)) {
              return;
            }
            const text = typeof rawText === 'string' ? rawText.trim() : '';
            if (text) {
              collected[String(value)] = text;
            }
          });
        } else if (rawResponses && typeof rawResponses === 'object') {
          Object.entries(rawResponses).forEach(([key, rawText]) => {
            const value = Number.parseInt(key, 10);
            if (!Number.isFinite(value)) {
              return;
            }
            const text = typeof rawText === 'string' ? rawText.trim() : '';
            if (text) {
              collected[String(value)] = text;
            }
          });
        }
        const ensured = {};
        const defaults = defaultResponsesMap(scale);
        for (let value = 1; value <= Math.max(1, scale); value += 1) {
          const key = String(value);
          ensured[key] = collected[key] || defaults[key] || `Спасибо за вашу оценку ${value}!`;
        }
        return ensured;
      }

      function cloneRatingTemplate(template, { preserveId = true } = {}) {
        const id = preserveId ? ensureRatingTemplateId(template && template.id) : generateRatingTemplateId();
        const name = template && typeof template.name === 'string' ? template.name.trim() : '';
        const description = template && typeof template.description === 'string' ? template.description.trim() : '';
        const scale = normalizeScale(template && (template.scaleSize ?? template.scale_size ?? template.scale));
        const promptSource = template && (template.promptText ?? template.prompt_text ?? template.prompt);
        const promptText = typeof promptSource === 'string' && promptSource.trim()
          ? promptSource.trim()
          : defaultRatingPrompt(scale);
        const responsesSource = template && template.responses ? template.responses : {};
        const responses = normalizeRatingResponses(responsesSource, scale);
        return {
          id,
          name: name || 'Шаблон оценок',
          description,
          promptText,
          scaleSize: scale,
          responses,
        };
      }

      function defaultResponsesMap(scale) {
        const map = {};
        for (let value = 1; value <= Math.max(1, scale); value += 1) {
          map[String(value)] = `Спасибо за вашу оценку ${value}!`;
        }
        return map;
      }

      function normalizeRatingTemplate(raw, index) {
        if (!raw || typeof raw !== 'object') {
          return null;
        }
        const templateId = ensureRatingTemplateId(raw.id || `rtpl_${index}`);
        const name = typeof raw.name === 'string' && raw.name.trim() ? raw.name.trim() : 'Шаблон оценок';
        const description = typeof raw.description === 'string' ? raw.description.trim() : '';
        const scale = normalizeScale(raw.scale_size ?? raw.scaleSize ?? raw.scale);
        const promptSource = raw.prompt_text ?? raw.promptText ?? raw.prompt;
        const promptText = typeof promptSource === 'string' && promptSource.trim()
          ? promptSource.trim()
          : defaultRatingPrompt(scale);
        const responses = normalizeRatingResponses(raw.responses, scale);
        return {
          id: templateId,
          name,
          description,
          promptText,
          scaleSize: scale,
          responses,
        };
      }

      function setRatingTemplateStatus(message, isError) {
        if (!context.ratingStatusEl) {
          return;
        }
        context.applyStatusAppearance(context.ratingStatusEl, message, isError);
        if (!isError) {
          setTimeout(() => {
            if (context.ratingStatusEl.textContent === message) {
              context.applyStatusAppearance(context.ratingStatusEl, '', false);
            }
          }, 3000);
        }
      }

      function ensureRatingEditorResponses(scale) {
        context.ratingEditorState.responses = normalizeRatingResponses(context.ratingEditorState.responses, scale);
      }

      function renderRatingEditorResponses() {
        if (!context.ratingResponsesContainer) {
          return;
        }
        context.ratingResponsesContainer.innerHTML = '';
        const scale = normalizeScale(context.ratingEditorState.scaleSize || 5);
        ensureRatingEditorResponses(scale);
        for (let value = 1; value <= Math.max(1, scale); value += 1) {
          const textValue = context.ratingEditorState.responses[String(value)] || '';
          const card = document.createElement('div');
          card.className = 'card border rounded-3';
          card.innerHTML = `
        <div class="card-body p-3">
          <div class="d-flex justify-content-between align-items-center mb-2">
            <span class="fw-semibold">Оценка ${value}</span>
            <span class="badge bg-light text-muted">${value}</span>
          </div>
          <textarea class="form-control" rows="2" data-bot-rating-template-response data-value="${value}">${context.html(textValue)}</textarea>
        </div>
      `;
          context.ratingResponsesContainer.appendChild(card);
        }
      }

      function renderRatingTemplates() {
        renderActiveRatingDiagnostic();
        if (!context.ratingTemplatesContainer) {
          return;
        }
        context.ratingTemplatesContainer.innerHTML = '';
        if (!context.state.ratingTemplates.length) {
          const placeholder = document.createElement('div');
          placeholder.className = 'alert alert-light border mb-0';
          placeholder.textContent = 'Шаблоны оценок не созданы. Добавьте шаблон, чтобы настроить систему оценок.';
          context.ratingTemplatesContainer.appendChild(placeholder);
          return;
        }
        context.state.ratingTemplates.forEach((template) => {
          const isActive = template.id === context.state.activeRatingTemplateId;
          const card = document.createElement('div');
          card.className = `card template-card channels-template-card${isActive ? ' border-primary' : ''}`;
          card.dataset.ratingTemplateId = template.id;
          const descriptionHtml = template.description
            ? `<p class="small text-muted mb-1">${context.html(template.description)}</p>`
            : '';
          const scale = normalizeScale(template.scaleSize || template.scale_size || 5);
          const responsesCount = Object.keys(template.responses || {}).length;
          const responsesLabel = responsesCount === 1 ? '1 ответ' : `${responsesCount} ответ${responsesCount >= 5 ? 'ов' : responsesCount >= 2 ? 'а' : ''}`;
          const promptPreview = template.promptText ? template.promptText.trim() : '';
          const promptSummary = promptPreview.length > 120 ? `${promptPreview.slice(0, 117)}…` : promptPreview;
          const summaryParts = [`Шкала: 1–${scale}`, responsesLabel];
          if (promptSummary) {
            summaryParts.push(`Запрос: ${promptSummary}`);
          }
          const summaryText = summaryParts.join(' • ');
          const activeBadgeHtml = isActive
            ? '<span class="channels-template-active" role="img" aria-label="Активный шаблон" title="Активный шаблон"><i class="bi bi-check-circle-fill" aria-hidden="true"></i></span>'
            : '';
          card.innerHTML = `
        <div class="card-body">
          <div class="channels-template-card__layout">
            <div class="template-card-header channels-template-card__header">
              <h6 class="mb-1">${context.html(template.name || 'Шаблон оценок')}${activeBadgeHtml}</h6>
              ${descriptionHtml}
              <div class="small text-muted">${context.html(summaryText)}</div>
            </div>
            <div class="channels-template-card__controls">
              <div class="form-check form-switch channels-template-default">
                <input class="form-check-input" type="radio" name="bot-rating-template-default" value="${context.html(template.id)}" ${template.id === context.state.activeRatingTemplateId ? 'checked' : ''} data-bot-rating-template-select>
                <label class="form-check-label small">Использовать по умолчанию</label>
              </div>
              <div class="d-flex template-card-actions channels-template-actions">
                <button class="btn btn-outline-primary channels-template-action" type="button" data-bot-rating-template-edit aria-label="Редактировать" title="Редактировать"><i class="bi bi-pencil" aria-hidden="true"></i><span class="visually-hidden">Редактировать</span></button>
                <button class="btn btn-outline-secondary channels-template-action" type="button" data-bot-rating-template-duplicate aria-label="Дублировать" title="Дублировать"><i class="bi bi-files" aria-hidden="true"></i><span class="visually-hidden">Дублировать</span></button>
                <button class="btn btn-outline-danger channels-template-action" type="button" data-bot-rating-template-delete aria-label="Удалить" title="Удалить" ${context.state.ratingTemplates.length === 1 ? 'disabled' : ''}><i class="bi bi-trash3" aria-hidden="true"></i><span class="visually-hidden">Удалить</span></button>
              </div>
            </div>
          </div>
        </div>
      `;
          context.ratingTemplatesContainer.appendChild(card);
        });
      }

      function openRatingTemplateEditor(index) {
        context.ratingEditorState.index = Number.isFinite(index) ? index : -1;
        context.ratingEditorState.templateId = null;
        context.ratingEditorState.name = '';
        context.ratingEditorState.description = '';
        context.ratingEditorState.scaleSize = 5;
        context.ratingEditorState.promptText = defaultRatingPrompt(5);
        context.ratingEditorState.responses = defaultResponsesMap(5);

        const existing = Number.isFinite(index) && index >= 0 && index < context.state.ratingTemplates.length
          ? context.state.ratingTemplates[index]
          : null;
        if (existing) {
          context.ratingEditorState.templateId = existing.id;
          context.ratingEditorState.name = existing.name || '';
          context.ratingEditorState.description = existing.description || '';
          context.ratingEditorState.scaleSize = normalizeScale(existing.scaleSize || existing.scale_size || 5);
          context.ratingEditorState.promptText = existing.promptText || existing.prompt_text || defaultRatingPrompt(context.ratingEditorState.scaleSize);
          context.ratingEditorState.responses = Object.assign({}, existing.responses || {});
          ensureRatingEditorResponses(context.ratingEditorState.scaleSize);
        } else {
          ensureRatingEditorResponses(context.ratingEditorState.scaleSize);
        }

        if (context.ratingNameInput) {
          context.ratingNameInput.value = context.ratingEditorState.name;
        }
        if (context.ratingDescriptionInput) {
          context.ratingDescriptionInput.value = context.ratingEditorState.description;
        }
        if (context.ratingPromptInput) {
          context.ratingPromptInput.value = context.ratingEditorState.promptText;
        }
        if (context.ratingScaleInput) {
          context.ratingScaleInput.value = context.ratingEditorState.scaleSize;
        }

        renderRatingEditorResponses();
        setRatingTemplateStatus('', false);
        context.showChildModal(context.ratingModalEl);
      }

      function saveRatingTemplateFromEditor() {
        const name = context.ratingNameInput ? context.ratingNameInput.value.trim() : '';
        const description = context.ratingDescriptionInput ? context.ratingDescriptionInput.value.trim() : '';
        const promptText = context.ratingPromptInput ? context.ratingPromptInput.value.trim() : '';
        const scale = context.ratingScaleInput ? normalizeScale(context.ratingScaleInput.value) : normalizeScale(context.ratingEditorState.scaleSize);
        ensureRatingEditorResponses(scale);
        const responses = {};
        for (let value = 1; value <= Math.max(1, scale); value += 1) {
          const key = String(value);
          const text = context.ratingEditorState.responses[key] ? context.ratingEditorState.responses[key].trim() : '';
          if (!text) {
            setRatingTemplateStatus(`Укажите текст ответа для оценки ${value}.`, true);
            return;
          }
          responses[key] = text;
        }
        if (!promptText) {
          setRatingTemplateStatus('Укажите текст запроса оценки.', true);
          return;
        }

        const templateId = ensureRatingTemplateId(context.ratingEditorState.templateId);
        const template = {
          id: templateId,
          name: name || 'Шаблон оценок',
          description,
          promptText,
          scaleSize: scale,
          responses,
        };

        const isExisting = context.ratingEditorState.index >= 0 && context.ratingEditorState.index < context.state.ratingTemplates.length;
        if (isExisting) {
          const previous = context.state.ratingTemplates[context.ratingEditorState.index];
          const wasActive = previous && previous.id === context.state.activeRatingTemplateId;
          context.state.ratingTemplates[context.ratingEditorState.index] = template;
          if (wasActive) {
            context.state.activeRatingTemplateId = template.id;
          }
        } else {
          context.state.ratingTemplates.push(template);
          if (!context.state.activeRatingTemplateId) {
            context.state.activeRatingTemplateId = template.id;
          }
        }

        context.hideChildModal(context.ratingModalEl);
        renderRatingTemplates();
      }

      function findRatingTemplateIndexById(id) {
        return context.state.ratingTemplates.findIndex((template) => template.id === id);
      }

      function duplicateRatingTemplate(index) {
        if (index < 0 || index >= context.state.ratingTemplates.length) {
          return;
        }
        const copy = cloneRatingTemplate(context.state.ratingTemplates[index], { preserveId: false });
        copy.name = `${context.state.ratingTemplates[index].name} (копия)`;
        context.state.ratingTemplates.splice(index + 1, 0, copy);
        renderRatingTemplates();
      }

      function deleteRatingTemplate(index) {
        if (context.state.ratingTemplates.length <= 1 || index < 0 || index >= context.state.ratingTemplates.length) {
          return;
        }
        const removed = context.state.ratingTemplates.splice(index, 1)[0];
        if (removed && removed.id === context.state.activeRatingTemplateId) {
          context.state.activeRatingTemplateId = context.state.ratingTemplates.length ? context.state.ratingTemplates[0].id : null;
        }
        renderRatingTemplates();
      }

      function renderActiveRatingDiagnostic() {
        if (!context.ratingDiagnosticEl) {
          return;
        }
        const activeTemplate = context.state.ratingTemplates.find((template) => template.id === context.state.activeRatingTemplateId) || null;
        if (!activeTemplate) {
          context.ratingDiagnosticEl.textContent = 'Активный шаблон оценок не выбран.';
          return;
        }
        const scale = normalizeScale(activeTemplate.scaleSize || activeTemplate.scale_size || 5);
        const responseValues = [];
        for (let value = 1; value <= scale; value += 1) {
          if (activeTemplate.responses && activeTemplate.responses[String(value)]) {
            responseValues.push(String(value));
          }
        }
        const maxValue = String(scale);
        const maxResponse = activeTemplate.responses && activeTemplate.responses[maxValue]
          ? activeTemplate.responses[maxValue].trim()
          : '';
        const diagnosticParts = [
          `Активный шаблон: ${activeTemplate.name || 'Шаблон оценок'}`,
          `id: ${activeTemplate.id}`,
          `шкала 1-${scale}`,
          `ответы: ${responseValues.join(', ') || 'не заданы'}`,
        ];
        if (maxResponse) {
          diagnosticParts.push(`оценка ${maxValue}: ${maxResponse}`);
        }
        context.ratingDiagnosticEl.textContent = diagnosticParts.join(' | ');
      }
      return { generateRatingTemplateId, ensureRatingTemplateId, defaultRatingPrompt, normalizeScale, normalizeRatingResponses, cloneRatingTemplate, defaultResponsesMap, normalizeRatingTemplate, setRatingTemplateStatus, ensureRatingEditorResponses, renderRatingEditorResponses, renderRatingTemplates, openRatingTemplateEditor, saveRatingTemplateFromEditor, findRatingTemplateIndexById, duplicateRatingTemplate, deleteRatingTemplate, renderActiveRatingDiagnostic };
    }
  });
})();

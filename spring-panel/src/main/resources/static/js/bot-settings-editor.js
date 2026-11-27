/* editor responsibilities. Live context getters preserve the page's canonical state and business-scoped catalog. */
(function () {
  'use strict';
  window.BotQuestionEditor = Object.freeze({
    create(context) {
      function buildDependencyValueHtml(label, values) {
        const normalized = context.normalizeStringArray(values);
        if (!normalized.length) {
          return '';
        }
        const badges = normalized
          .map((value) => `<span class="badge text-bg-light border">${context.html(value)}</span>`)
          .join(' ');
        return `
      <div>
        <span class="fw-semibold text-muted">${context.html(label)}:</span>
        <span class="d-inline-flex flex-wrap gap-1 align-items-center ms-1">${badges}</span>
      </div>
    `;
      }

      function buildDependencyPathsHtml(paths) {
        if (!Array.isArray(paths) || !paths.length) {
          return '';
        }
        const LABELS = {
          business: 'Бизнес',
          location_type: 'Тип бизнеса',
          city: 'Город',
        };
        const items = paths
          .map((path) => {
            if (!path || typeof path !== 'object') {
              return '';
            }
            const segments = [];
            Object.keys(LABELS).forEach((key) => {
              const value = typeof path[key] === 'string' ? path[key].trim() : '';
              if (value) {
                segments.push(`${LABELS[key]}: ${value}`);
              }
            });
            if (!segments.length) {
              return '';
            }
            const chain = segments
              .map((segment) => `<span>${context.html(segment)}</span>`)
              .join('<span class="text-muted mx-1">→</span>');
            return `<li>${chain}</li>`;
          })
          .filter(Boolean);
        if (!items.length) {
          return '';
        }
        return `
      <div>
        <div class="small fw-semibold text-muted">Комбинации значений:</div>
        <ul class="small mb-0 ps-3 d-flex flex-column gap-1">${items.join('')}</ul>
      </div>
    `;
      }

      function buildOptionDependencyHtml(entry) {
        if (!entry || typeof entry !== 'object') {
          return '<div class="text-muted small">Для этого варианта нет дополнительных зависимостей.</div>';
        }
        const pieces = [];
        pieces.push(buildDependencyValueHtml('Бизнес', entry.business));
        pieces.push(buildDependencyValueHtml('Тип бизнеса', entry.location_type));
        pieces.push(buildDependencyValueHtml('Город', entry.city));
        pieces.push(buildDependencyPathsHtml(entry.paths));
        const content = pieces.filter(Boolean).join('');
        if (!content) {
          return '<div class="text-muted small">Для этого варианта нет дополнительных зависимостей. Он доступен без ограничений.</div>';
        }
        const intro = '<div class="small text-muted">Этот вариант увидит клиент, если выполнены условия:</div>';
        return `<div class="d-flex flex-column gap-2">${intro}${content}</div>`;
      }

      function highlightOptionRow(card, optionValue) {
        if (!card) {
          return null;
        }
        const normalized = typeof optionValue === 'string' ? optionValue.trim() : '';
        let activeRow = null;
        card.querySelectorAll('[data-bot-question-option-row]').forEach((row) => {
          if (!(row instanceof HTMLElement)) {
            return;
          }
          const rowValue = typeof row.dataset.optionValue === 'string' ? row.dataset.optionValue.trim() : '';
          const isActive = normalized && rowValue === normalized;
          row.classList.toggle('is-active', isActive);
          if (isActive) {
            row.setAttribute('data-bot-question-option-active', 'true');
            activeRow = row;
          } else {
            row.removeAttribute('data-bot-question-option-active');
            const details = row.querySelector('[data-bot-question-option-dependencies]');
            if (details) {
              details.classList.add('d-none');
              details.innerHTML = '';
            }
          }
        });
        return activeRow;
      }

      function showOptionDependencies(card, optionValue) {
        if (!card) {
          return;
        }
        const meta = card.__questionMeta || null;
        if (!meta || typeof meta.optionDependencies !== 'object') {
          return;
        }
        const value = typeof optionValue === 'string' ? optionValue.trim() : '';
        if (!value) {
          return;
        }
        const activeRow = highlightOptionRow(card, value);
        if (!activeRow) {
          return;
        }
        const container = activeRow.querySelector('[data-bot-question-option-dependencies]');
        if (!container) {
          return;
        }
        const entry = meta.optionDependencies[value] || meta.optionDependencies[optionValue] || null;
        container.innerHTML = `
      <div class="bot-question-option__dependencies-title small fw-semibold text-primary">
        Вариант: <span class="fw-normal text-body">${context.html(value)}</span>
      </div>
      ${buildOptionDependencyHtml(entry)}
    `;
        container.classList.remove('d-none');
      }

      function buildQuestionRouteControls(question, questionIndex, meta) {
        const routeChoices = context.resolveQuestionRouteChoices(question, meta);
        if (!routeChoices.length) {
          return '';
        }
        const targetChoices = context.resolveQuestionRouteTargets(questionIndex);
        const hasIntermediateTargets = targetChoices.some((target) => target.id !== context.QUESTION_ROUTE_PROBLEM_ID);
        const targetOptions = ['<option value="">По порядку</option>']
          .concat(targetChoices.map((target) => `<option value="${context.html(target.id)}">${context.html(target.label)}</option>`))
          .join('');
        const rows = routeChoices.map((option) => `
      <div class="bot-question-route-row">
        <div class="small fw-semibold">Если клиент выбрал: ${context.html(option.label)}</div>
        <label class="small text-muted">Следующий шаг</label>
        <select class="form-select form-select-sm" data-bot-question-route-target="${context.html(option.id)}">
          ${targetOptions}
        </select>
      </div>
    `).join('');
        return `
      <div class="row g-3 mt-1 align-items-stretch bot-question-route-rowset">
        <div class="col-12">
          <div class="border rounded-3 bg-light p-3 bot-question-surface bot-question-info-surface bot-question-route-surface">
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
              <div class="small text-uppercase text-muted fw-semibold">Маршрутизация</div>
              <div class="small text-muted">Если маршрут не задан, бот задаст следующий вопрос по порядку.</div>
            </div>
            <div class="bot-question-route-grid">
              ${rows}
            </div>
            <div class="form-text mt-2 mb-0">Можно отправить клиента сразу к следующему релевантному вопросу или сразу к финальному описанию проблемы.</div>
          </div>
        </div>
      </div>
    `;
      }

      function escapeSelectorValue(value) {
        const stringValue = value == null ? '' : String(value);
        if (typeof CSS !== 'undefined' && typeof CSS.escape === 'function') {
          return CSS.escape(stringValue);
        }
        return stringValue.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
      }

      function resolveQuestionEditorScrollContainer() {
        if (context.templateModalEl instanceof HTMLElement) {
          const modalBody = context.templateModalEl.querySelector('.modal-body');
          if (modalBody instanceof HTMLElement) {
            return modalBody;
          }
        }
        return context.questionsContainer instanceof HTMLElement ? context.questionsContainer : null;
      }

      function resolveFocusedQuestionFieldState(activeElement, card) {
        if (!(activeElement instanceof HTMLElement) || !(card instanceof HTMLElement)) {
          return null;
        }
        if (activeElement.matches('[data-bot-question-text]')) {
          return { selector: '[data-bot-question-text]' };
        }
        if (activeElement.matches('[data-bot-question-type]')) {
          return { selector: '[data-bot-question-type]' };
        }
        if (activeElement.matches('[data-bot-question-preset]')) {
          return { selector: '[data-bot-question-preset]' };
        }
        if (activeElement.matches('[data-bot-question-select-options]')) {
          return { selector: '[data-bot-question-select-options]' };
        }
        if (activeElement.matches('[data-bot-question-binding]')) {
          return { selector: '[data-bot-question-binding]' };
        }
        if (activeElement.matches('[data-bot-question-required]')) {
          return { selector: '[data-bot-question-required]' };
        }
        if (activeElement.matches('[data-bot-question-dashboard]')) {
          return { selector: '[data-bot-question-dashboard]' };
        }
        if (activeElement.matches('[data-bot-question-route-target]')) {
          const routeValueId = activeElement.dataset.botQuestionRouteTarget || '';
          return routeValueId
            ? { selector: `[data-bot-question-route-target="${escapeSelectorValue(routeValueId)}"]` }
            : { selector: '[data-bot-question-route-target]' };
        }
        if (activeElement.matches('[data-bot-question-filter]')) {
          const filterKey = activeElement.dataset.botQuestionFilter || '';
          return filterKey ? { selector: `[data-bot-question-filter="${escapeSelectorValue(filterKey)}"]` } : null;
        }
        if (activeElement.matches('[data-bot-question-option]')) {
          const optionValue = activeElement.getAttribute('value') || '';
          return optionValue
            ? { selector: `[data-bot-question-option][value="${escapeSelectorValue(optionValue)}"]` }
            : { selector: '[data-bot-question-option]' };
        }
        if (activeElement.matches('[data-bot-question-option-info]')) {
          const row = activeElement.closest('[data-bot-question-option-row]');
          const optionValue = row instanceof HTMLElement ? (row.dataset.optionValue || '') : '';
          return optionValue
            ? { selector: `[data-bot-question-option-row][data-option-value="${escapeSelectorValue(optionValue)}"] [data-bot-question-option-info]` }
            : { selector: '[data-bot-question-option-info]' };
        }
        if (activeElement.matches('[data-bot-question-action]')) {
          const action = activeElement.dataset.botQuestionAction || '';
          return action ? { selector: `[data-bot-question-action="${escapeSelectorValue(action)}"]` } : null;
        }
        return null;
      }

      function captureQuestionEditorUiState() {
        if (!(context.questionsContainer instanceof HTMLElement)) {
          return null;
        }
        const scrollContainer = resolveQuestionEditorScrollContainer();
        const uiState = {
          scrollTop: scrollContainer ? scrollContainer.scrollTop : 0,
          activeQuestionId: '',
          activeFieldSelector: '',
          selectionStart: null,
          selectionEnd: null,
          anchorOffsetTop: null,
          expandedOptions: [],
          disclosures: [],
        };
        context.questionsContainer.querySelectorAll('.card[data-question-id]').forEach((card) => {
          if (!(card instanceof HTMLElement)) {
            return;
          }
          card.querySelectorAll('details[data-question-section]').forEach((details) => {
            uiState.disclosures.push({ questionId: card.dataset.questionId || '', section: details.dataset.questionSection, open: details.open });
          });
          const activeRow = card.querySelector('[data-bot-question-option-active="true"]');
          if (!(activeRow instanceof HTMLElement)) {
            return;
          }
          const optionValue = activeRow.dataset.optionValue || '';
          if (!optionValue) {
            return;
          }
          uiState.expandedOptions.push({
            questionId: card.dataset.questionId || '',
            optionValue,
          });
        });
        const activeElement = document.activeElement;
        if (!(activeElement instanceof HTMLElement) || !context.questionsContainer.contains(activeElement)) {
          return uiState;
        }
        const card = activeElement.closest('.card[data-question-id]');
        if (!(card instanceof HTMLElement)) {
          return uiState;
        }
        const fieldState = resolveFocusedQuestionFieldState(activeElement, card);
        if (!fieldState || !fieldState.selector) {
          return uiState;
        }
        uiState.activeQuestionId = card.dataset.questionId || '';
        uiState.activeFieldSelector = fieldState.selector;
        if (scrollContainer instanceof HTMLElement) {
          uiState.anchorOffsetTop = card.getBoundingClientRect().top - scrollContainer.getBoundingClientRect().top;
        }
        if ((activeElement instanceof HTMLInputElement || activeElement instanceof HTMLTextAreaElement)
          && typeof activeElement.selectionStart === 'number'
          && typeof activeElement.selectionEnd === 'number') {
          uiState.selectionStart = activeElement.selectionStart;
          uiState.selectionEnd = activeElement.selectionEnd;
        }
        return uiState;
      }

      function restoreQuestionEditorUiState(uiState) {
        if (!uiState || !(context.questionsContainer instanceof HTMLElement)) {
          return;
        }
        const scrollContainer = resolveQuestionEditorScrollContainer();
        (uiState.disclosures || []).forEach((entry) => {
          const card = context.questionsContainer.querySelector(`.card[data-question-id="${escapeSelectorValue(entry.questionId)}"]`);
          const details = card?.querySelector(`details[data-question-section="${escapeSelectorValue(entry.section)}"]`);
          if (details) details.open = entry.open;
        });
        if (scrollContainer instanceof HTMLElement && Number.isFinite(uiState.scrollTop)) {
          scrollContainer.scrollTop = uiState.scrollTop;
        }
        if (Array.isArray(uiState.expandedOptions)) {
          uiState.expandedOptions.forEach((entry) => {
            if (!entry || !entry.questionId || !entry.optionValue) {
              return;
            }
            const card = context.questionsContainer.querySelector(`.card[data-question-id="${escapeSelectorValue(entry.questionId)}"]`);
            if (card instanceof HTMLElement) {
              showOptionDependencies(card, entry.optionValue);
            }
          });
        }
        if (!uiState.activeQuestionId || !uiState.activeFieldSelector) {
          if (scrollContainer instanceof HTMLElement && Number.isFinite(uiState.scrollTop)) {
            requestAnimationFrame(() => {
              scrollContainer.scrollTop = uiState.scrollTop;
            });
          }
          return;
        }
        const card = context.questionsContainer.querySelector(`.card[data-question-id="${escapeSelectorValue(uiState.activeQuestionId)}"]`);
        if (!(card instanceof HTMLElement)) {
          if (scrollContainer instanceof HTMLElement && Number.isFinite(uiState.scrollTop)) {
            requestAnimationFrame(() => {
              scrollContainer.scrollTop = uiState.scrollTop;
            });
          }
          return;
        }
        const target = card.querySelector(uiState.activeFieldSelector);
        if (target instanceof HTMLElement) {
          try {
            target.focus({ preventScroll: true });
          } catch (error) {
            target.focus();
          }
          if ((target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)
            && typeof uiState.selectionStart === 'number'
            && typeof uiState.selectionEnd === 'number'
            && typeof target.setSelectionRange === 'function') {
            target.setSelectionRange(uiState.selectionStart, uiState.selectionEnd);
          }
        }
        if (scrollContainer instanceof HTMLElement) {
          const restorePosition = () => {
            if (typeof uiState.anchorOffsetTop === 'number') {
              const delta = card.getBoundingClientRect().top - scrollContainer.getBoundingClientRect().top - uiState.anchorOffsetTop;
              scrollContainer.scrollTop += delta;
            } else if (Number.isFinite(uiState.scrollTop)) {
              scrollContainer.scrollTop = uiState.scrollTop;
            }
          };
          restorePosition();
          requestAnimationFrame(restorePosition);
        }
      }

      function renderPresetHints() {
        const presetsAvailable = context.hasPresetDefinitions();
        const statusMessage = context.presetStatusMessage();
        context.addQuestionButtons.forEach((button) => {
          if (!(button instanceof HTMLButtonElement) || button.dataset.type !== 'preset') {
            return;
          }
          button.disabled = !presetsAvailable;
          button.title = presetsAvailable ? '' : statusMessage;
        });
        if (context.presetEmptyAlertEl) {
          context.presetEmptyAlertEl.classList.toggle('d-none', presetsAvailable);
          context.presetEmptyAlertEl.textContent = statusMessage;
        }
        if (context.presetSummaryCardEl) {
          context.presetSummaryCardEl.classList.toggle('border', !presetsAvailable);
          context.presetSummaryCardEl.classList.toggle('border-warning', !presetsAvailable);
          context.presetSummaryCardEl.classList.toggle('bg-warning-subtle', !presetsAvailable);
        }
        if (!context.presetHintsEl) {
          return;
        }
        const rows = [];
        Object.entries(context.PRESET_GROUPS).forEach(([groupKey, groupValue]) => {
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
        context.presetHintsEl.textContent = rows.length
          ? rows.join('  ||  ')
          : statusMessage;
      }

      function renderLegacyDiagnostics() {
        if (!context.legacyDiagnosticEl) {
          return;
        }
        const message = typeof context.state.legacyDiagnostics?.message === 'string'
          ? context.state.legacyDiagnostics.message.trim()
          : '';
        if (!message) {
          context.legacyDiagnosticEl.hidden = true;
          context.legacyDiagnosticEl.textContent = '';
          return;
        }
        context.legacyDiagnosticEl.hidden = false;
        context.legacyDiagnosticEl.classList.remove('alert-light', 'alert-warning', 'alert-success');
        context.legacyDiagnosticEl.classList.add(context.state.legacyDiagnostics.level === 'warning' ? 'alert-warning' : 'alert-success');
        context.legacyDiagnosticEl.textContent = message;
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

      function renderQuestionPreview(card, question, meta) {
        if (!card) {
          return;
        }
        const previewEl = card.querySelector('[data-bot-question-preview]');
        if (!previewEl) {
          return;
        }
        const titleEl = previewEl.querySelector('[data-bot-question-preview-title]');
        const descriptionEl = previewEl.querySelector('[data-bot-question-preview-description]');
        const optionsEl = previewEl.querySelector('[data-bot-question-preview-options]');
        const hintEl = previewEl.querySelector('[data-bot-question-preview-hint]');
        const text = question && typeof question.text === 'string' ? question.text.trim() : '';
        const isPreset = Boolean(question && question.type === 'preset');
        const isSelect = Boolean(question && question.type === 'select');
        if (titleEl) {
          titleEl.textContent = text || 'Текст вопроса пока не задан';
          titleEl.classList.toggle('text-muted', !text);
        }
        if (!question || (!isPreset && !isSelect)) {
          if (descriptionEl) {
            descriptionEl.textContent = 'Клиент введёт ответ в свободной форме.';
          }
          if (optionsEl) {
            optionsEl.innerHTML = '';
            optionsEl.classList.add('d-none');
          }
          if (hintEl) {
            if (question && question.required === false) {
              hintEl.textContent = 'Вопрос необязательный: клиент сможет пропустить его.';
              hintEl.classList.remove('d-none');
            } else {
              hintEl.classList.add('d-none');
              hintEl.textContent = '';
            }
          }
          return;
        }
        if (isSelect) {
          const options = context.resolveQuestionOptionLabels(question, meta);
          if (descriptionEl) {
            descriptionEl.textContent = options.length
              ? 'Клиент выберет один из подготовленных вариантов ответа.'
              : 'Добавьте варианты ответа, чтобы вопрос корректно отображался клиенту.';
          }
          if (optionsEl) {
            if (options.length) {
              const badges = options
                .slice(0, 12)
                .map((value) => `<button class="btn btn-outline-secondary btn-sm bot-question-preview__badge" type="button" disabled>${context.html(value)}</button>`);
              if (options.length > 12) {
                badges.push(
                  `<button class="btn btn-outline-secondary btn-sm bot-question-preview__badge" type="button" disabled>+${options.length - 12} ещё</button>`,
                );
              }
              optionsEl.innerHTML = badges.join(' ');
            } else {
              optionsEl.innerHTML = '<span class="text-muted small">Список вариантов пока пуст.</span>';
            }
            optionsEl.classList.remove('d-none');
          }
          if (hintEl) {
            if (question.required === false) {
              hintEl.textContent = 'Вопрос необязательный: клиент сможет пропустить его.';
              hintEl.classList.remove('d-none');
            } else {
              hintEl.classList.add('d-none');
              hintEl.textContent = '';
            }
          }
          return;
        }
        if (!meta) {
          if (descriptionEl) {
            descriptionEl.textContent = 'Выберите готовое поле, чтобы показать клиенту связанные варианты ответа.';
          }
          if (optionsEl) {
            optionsEl.innerHTML = '';
            optionsEl.classList.add('d-none');
          }
          if (hintEl) {
            hintEl.classList.add('d-none');
            hintEl.textContent = '';
          }
          return;
        }
        const options = Array.isArray(meta.options) ? meta.options : [];
        const excludedValues = Array.isArray(question.excludedOptions)
          ? question.excludedOptions
              .map((value) => (typeof value === 'string' ? value.trim() : ''))
              .filter((value) => value)
          : [];
        const hiddenSet = new Set(excludedValues);
        const visibleOptions = options.filter((value) => !hiddenSet.has(value));
        if (descriptionEl) {
          descriptionEl.textContent = visibleOptions.length
            ? 'Клиент увидит следующие варианты ответа:'
            : 'Сейчас ни один вариант не отображается клиенту.';
        }
        if (optionsEl) {
          if (visibleOptions.length) {
            const badges = visibleOptions
              .slice(0, 12)
              .map((value) => `<button class="btn btn-outline-secondary btn-sm bot-question-preview__badge" type="button" disabled>${context.html(value)}</button>`);
            if (visibleOptions.length > 12) {
              badges.push(
                `<button class="btn btn-outline-secondary btn-sm bot-question-preview__badge" type="button" disabled>+${visibleOptions.length - 12} ещё</button>`,
              );
            }
            optionsEl.innerHTML = badges.join(' ');
          } else {
            optionsEl.innerHTML = '<span class="text-muted small">Варианты будут скрыты до изменения настроек.</span>';
          }
          optionsEl.classList.remove('d-none');
        }
        if (hintEl) {
          const hasDependencies = meta.optionDependencies && Object.keys(meta.optionDependencies).length > 0;
          if (hasDependencies) {
            hintEl.textContent = 'Нажмите на иконку рядом с вариантом, чтобы увидеть условия его показа для клиента.';
            hintEl.classList.remove('d-none');
          } else {
            hintEl.classList.add('d-none');
            hintEl.textContent = '';
          }
        }
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
          sorted.map((value) => `<option value="${context.html(value)}">${context.html(value)}</option>`),
        );
        return `
      <div class="d-flex align-items-center gap-1" data-bot-question-filter-wrapper="${context.html(key)}">
        <span class="small text-muted">${context.html(label)}:</span>
        <select class="form-select form-select-sm" data-bot-question-filter="${context.html(key)}">
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

      function renderQuestions(options = {}) {
        if (!context.questionsContainer) {
          return;
        }
        const shouldPreserveUi = options.preserveUi !== false
          && context.templateModalEl instanceof HTMLElement
          && context.templateModalEl.classList.contains('show');
        const uiState = shouldPreserveUi ? captureQuestionEditorUiState() : null;
        context.questionsContainer.innerHTML = '';
        if (!context.editorState.questionFlow.length) {
          const empty = document.createElement('div');
          empty.className = 'alert alert-light border mb-0';
          empty.textContent = 'Вопросы не заданы. Добавьте вопрос, чтобы сохранить шаблон.';
          context.questionsContainer.appendChild(empty);
          renderPresetHints();
          return;
        }
        context.editorState.questionFlow.forEach((question, index) => {
          question.order = index + 1;
          const card = document.createElement('div');
          card.className = 'card bot-question-card';
          card.dataset.index = String(index);
          card.dataset.questionId = question.id || '';
          const preset = question.preset && question.preset.group && question.preset.field ? question.preset : null;
          const presetValue = preset ? context.encodePresetValue(preset.group, preset.field) : '';
          const isPreset = question.type === 'preset';
          const isSelect = question.type === 'select';
          const isBusiness = question.type === 'business';
          const presetsAvailable = context.hasPresetDefinitions();
          const presetOptions = (() => {
            const groups = Object.entries(context.PRESET_GROUPS);
            if (!groups.length) {
              return `<option value="" selected disabled>${context.html(context.presetStatusMessage())}</option>`;
            }
            const pieces = ['<option value="">Выберите поле</option>'];
            groups.forEach(([groupKey, groupValue]) => {
              const options = Object.entries(groupValue.fields || {});
              if (!options.length) {
                return;
              }
              const htmlOptions = options
                .map(([fieldKey, fieldValue]) => {
                  const value = context.encodePresetValue(groupKey, fieldKey);
                  const selected = presetValue === value ? ' selected' : '';
                  return `<option value="${context.html(value)}"${selected}>${context.html(fieldValue.label || fieldKey)}</option>`;
                })
                .join('');
              pieces.push(`<optgroup label="${context.html(groupValue.label || groupKey)}">${htmlOptions}</optgroup>`);
            });
            return pieces.join('');
          })();
          const meta = preset && context.presetExists(preset.group, preset.field) ? context.getPresetMeta(preset.group, preset.field) : null;
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
                  ? ` data-location-type-tags="${context.html(locationTypeTags.join('|'))}"`
                  : '';
                const businessAttr = businessTags.length
                  ? ` data-business-tags="${context.html(businessTags.join('|'))}"`
                  : '';
                const checked = excluded.has(optionValue) ? '' : ' checked';
                return `
              <div class="bot-question-option form-check form-check-sm py-2 px-3 mb-2"${locationTypeAttr}${businessAttr} data-bot-question-option-row="true" data-option-value="${context.html(optionValue)}">
                <div class="d-flex align-items-start justify-content-between gap-2 bot-question-option__header">
                  <div class="flex-grow-1 d-flex align-items-start gap-2">
                    <input class="form-check-input mt-1" type="checkbox" value="${context.html(optionValue)}" data-bot-question-option${checked}>
                    <label class="form-check-label">${context.html(optionValue)}</label>
                  </div>
                  <button type="button" class="btn btn-link btn-sm px-1 text-decoration-none" data-bot-question-option-info title="Показать зависимости варианта" aria-label="Показать зависимости варианта">
                    <i class="bi bi-diagram-3"></i>
                  </button>
                </div>
                <div class="bot-question-option__dependencies small text-muted mt-2 d-none" data-bot-question-option-dependencies></div>
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
            optionsHtml = '<div class="text-muted small">Нет готовых значений для отображения.</div>';
          }
          const hintText = !presetsAvailable
            ? context.presetStatusMessage()
            : meta && meta.options && meta.options.length
            ? `Варианты: ${meta.options.slice(0, 5).join(', ')}${meta.options.length > 5 ? ' ...' : ''}`
            : 'Значения берутся из выбранного справочника.';
          const presetAlertText = !presetsAvailable
            ? context.presetStatusMessage()
            : (isPreset && !meta)
              ? 'Выбранное готовое поле недоступно в текущих preset definitions. Проверьте структуру локаций и конфиг готовых полей.'
              : '';
          const filtersHtml = isPreset ? buildFilterControls(meta) : '';
          const filterState = question.filterState && typeof question.filterState === 'object'
            ? question.filterState
            : {};
          card.dataset.filterBusiness = filterState.business || '';
          card.dataset.filterLocationType = filterState.location_type || '';
          const isRequired = isBusiness || question.required !== false;
          const selectOptionsText = isSelect ? context.resolveQuestionOptionLabels(question, null).join('\n') : '';
          const bindingKey = context.normalizeBindingKey(question.bindingKey || question.binding_key || '');
          const analyticsHint = isPreset
            ? 'Оставьте поле пустым, чтобы использовать системный ключ готового поля. Для вывода в дашборд можно задать свой ключ.'
            : 'Стабильный ключ для интеграций и аналитики, например company, direction или bot_product. Если включён дашборд, ключ обязателен.';
          const answerBodyHtml = isBusiness
            ? `
            <div class="small text-uppercase text-muted fw-semibold mb-2">Канонический бизнес</div>
            <div class="small mb-1">Бот покажет активные бизнесы, привязанные к тому каналу, где выбран этот шаблон.</div>
            <div class="form-text mb-0">Значение записывается в обращение и аналитику как <code>business</code>. Список вручную не задаётся.</div>
          `
            : isSelect
            ? `
            <label class="form-label">Варианты ответа</label>
            <textarea class="form-control font-monospace" rows="6" data-bot-question-select-options placeholder="По одному варианту на строку">${context.html(selectOptionsText)}</textarea>
            <div class="form-text mb-0">Каждая строка станет отдельным вариантом ответа для клиента.</div>
          `
            : `
            <div class="small text-uppercase text-muted fw-semibold mb-2">Поле ответа</div>
            <div class="small mb-1 bot-question-answer-copy">Ответ клиента будет сохранён как свободный текст для этого вопроса.</div>
            <div class="form-text mb-0">Если отключить обязательность, клиент сможет написать "Пропустить" и перейти дальше.</div>
          `;
          card.innerHTML = `
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-3">
            <div class="d-flex align-items-center gap-2">
              <span class="bot-question-number">Вопрос ${index + 1}</span>
            </div>
            <div class="btn-group btn-group-sm">
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-up" ${index === 0 ? 'disabled' : ''}>↑</button>
              <button class="btn btn-outline-secondary" type="button" data-bot-question-action="move-down" ${index === context.editorState.questionFlow.length - 1 ? 'disabled' : ''}>↓</button>
              <button class="btn btn-outline-danger" type="button" data-bot-question-action="remove">Удалить</button>
            </div>
          </div>
          <div class="row g-3 align-items-end">
            <div class="col-lg-6">
              <label class="form-label">Текст вопроса</label>
              <input type="text" class="form-control" value="${context.html(question.text || '')}" data-bot-question-text${isPreset ? ' readonly aria-readonly="true"' : ''}>
            </div>
            <div class="col-lg-3">
              <label class="form-label">Тип вопроса</label>
              <select class="form-select" data-bot-question-type>
                <option value="custom"${isPreset || isBusiness || isSelect ? '' : ' selected'}>Свободный текст</option>
                <option value="select"${isSelect ? ' selected' : ''}>Выбор из списка</option>
                <option value="business"${isBusiness ? ' selected' : ''}>Бизнес канала</option>
                <option value="preset"${isPreset ? ' selected' : ''}${!presetsAvailable && !isPreset ? ' disabled' : ''}>Готовое поле</option>
              </select>
            </div>
            <div class="col-lg-3${isPreset ? '' : ' d-none'}" data-bot-question-preset-wrapper>
              <label class="form-label">Готовое поле</label>
              <select class="form-select" data-bot-question-preset${presetsAvailable ? '' : ' disabled'}>
                ${presetOptions}
              </select>
              <div class="form-text small text-muted" data-bot-question-preset-hint>${context.html(hintText)}</div>
              <div class="alert alert-warning small py-2 px-3 mt-2 mb-0${presetAlertText ? '' : ' d-none'}" data-bot-question-preset-alert>${context.html(presetAlertText)}</div>
            </div>
          </div>
          <div class="row g-3 mt-1 align-items-stretch${isPreset ? ' d-none' : ''}" data-bot-question-answer-wrapper>
            <div class="col-lg-8">
              <div class="border rounded-3 bg-light p-3 h-100 bot-question-surface bot-question-info-surface">
                <div class="small text-uppercase text-muted fw-semibold mb-2">Поле ответа</div>
                <div class="small mb-1 bot-question-answer-copy">Ответ клиента будет сохранён как свободный текст для этого вопроса.</div>
                <div class="form-text mb-0">Если выключить обязательность, клиент сможет написать "Пропустить" и перейти дальше.</div>
              </div>
            </div>
            <div class="col-lg-4">
              <div class="border rounded-3 p-3 h-100 bot-question-surface bot-question-switch-surface">
                <div class="small text-uppercase text-muted fw-semibold mb-2">Обязательность ответа</div>
                <div class="form-check form-switch mb-0 bot-question-switch-control">
                  <input class="form-check-input" type="checkbox" data-bot-question-required${isRequired ? ' checked' : ''}${isBusiness ? ' disabled' : ''}>
                  <label class="form-check-label">Ответ обязателен</label>
                </div>
                <div class="form-text mb-0">Оставьте включённым, если клиент должен ответить перед переходом к следующему шагу.</div>
              </div>
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
            <div class="small text-muted mb-2">
              Отметьте галочкой варианты, которые увидит клиент. Нажмите на значок <i class="bi bi-diagram-3"></i>, чтобы увидеть условия показа конкретного варианта.
            </div>
            <div class="border rounded-3 p-3 bg-light" data-bot-question-options>
              ${optionsHtml}
            </div>
          </div>
          ${(isSelect || isPreset) ? buildQuestionRouteControls(question, index, meta) : ''}
          <div class="mt-3" data-bot-question-preview-wrapper>
            <div class="bot-question-preview alert alert-light border mb-0" data-bot-question-preview>
              <div class="small text-uppercase text-muted fw-semibold mb-2">Как увидит клиент</div>
              <div class="fw-semibold" data-bot-question-preview-title></div>
              <div class="small text-muted mt-2" data-bot-question-preview-description></div>
              <div class="d-flex flex-wrap gap-2 mt-2" data-bot-question-preview-options></div>
              <div class="small text-muted mt-2 d-none" data-bot-question-preview-hint></div>
            </div>
          </div>
        </div>
      `;
          context.questionsContainer.appendChild(card);
          const typeSelect = card.querySelector('[data-bot-question-type]');
          if (typeSelect) {
            typeSelect.innerHTML = `
          <option value="custom"${!isPreset && !isSelect && !isBusiness ? ' selected' : ''}>Свободный текст</option>
          <option value="select"${isSelect ? ' selected' : ''}>Выбор из списка</option>
          <option value="business"${isBusiness ? ' selected' : ''}>Бизнес канала</option>
          <option value="preset"${isPreset ? ' selected' : ''}${!presetsAvailable && !isPreset ? ' disabled' : ''}>Готовое поле</option>
        `;
          }
          const answerWrapper = card.querySelector('[data-bot-question-answer-wrapper]');
          if (answerWrapper) {
            const answerBox = answerWrapper.querySelector('.col-lg-8 .border');
            if (answerBox) {
              answerBox.innerHTML = answerBodyHtml;
            }
            const analyticsRow = document.createElement('div');
            analyticsRow.className = 'row g-3 mt-1 align-items-stretch bot-question-analytics-row';
            analyticsRow.innerHTML = `
          <div class="col-lg-7">
            <div class="border rounded-3 bg-light p-3 h-100 bot-question-surface bot-question-info-surface">
              <label class="form-label">Ключ атрибута</label>
              <input type="text" class="form-control font-monospace" value="${context.html(bindingKey)}" data-bot-question-binding placeholder="company">
              <div class="form-text mb-0">${context.html(analyticsHint)}</div>
            </div>
          </div>
          <div class="col-lg-5">
            <div class="border rounded-3 p-3 h-100 bot-question-surface bot-question-switch-surface bot-question-analytics-surface">
              <div class="small text-uppercase text-muted fw-semibold mb-2">Аналитика</div>
              <div class="form-check form-switch mb-0 bot-question-switch-control">
                <input class="form-check-input" type="checkbox" data-bot-question-dashboard${question.includeInDashboard ? ' checked' : ''}>
                <label class="form-check-label">Отображать в дашборде</label>
              </div>
              <div class="form-text mb-0">Включайте для вопросов, которые должны попадать в агрегаты и виджеты дашборда.</div>
            </div>
          </div>
        `;
            answerWrapper.insertAdjacentElement('afterend', analyticsRow);
            const bindingSurface = analyticsRow.querySelector('.col-lg-7 .bot-question-info-surface');
            if (bindingSurface instanceof HTMLElement) {
              bindingSurface.innerHTML = buildBindingEditorHtml(question, bindingKey, analyticsHint);
              if (isBusiness) {
                const bindingInput = bindingSurface.querySelector('[data-bot-question-binding]');
                if (bindingInput instanceof HTMLInputElement || bindingInput instanceof HTMLSelectElement) {
                  bindingInput.value = 'business';
                  bindingInput.disabled = true;
                }
                const dashboardInput = analyticsRow.querySelector('[data-bot-question-dashboard]');
                if (dashboardInput instanceof HTMLInputElement) {
                  dashboardInput.checked = true;
                  dashboardInput.disabled = true;
                }
              }
            }
          }
          context.sanitizeQuestionRoutes(question, index, meta).forEach((route) => {
            const routeSelect = card.querySelector(`[data-bot-question-route-target="${escapeSelectorValue(route.valueId)}"]`);
            if (routeSelect instanceof HTMLSelectElement) {
              routeSelect.value = route.nextQuestionId;
            }
          });
          const routeSurface = card.querySelector('.bot-question-route-surface');
          if (routeSurface instanceof HTMLElement) {
            const routeTargets = context.resolveQuestionRouteTargets(index);
            const hasIntermediateTargets = routeTargets.some((target) => target.id !== context.QUESTION_ROUTE_PROBLEM_ID);
            const title = routeSurface.querySelector('.small.text-uppercase.text-muted.fw-semibold');
            if (title instanceof HTMLElement) {
              title.textContent = 'Маршрутизация';
            }
            const headerHint = routeSurface.querySelector('.d-flex.justify-content-between .small.text-muted');
            if (headerHint instanceof HTMLElement) {
              headerHint.textContent = 'Если маршрут не задан, бот задаст следующий вопрос по порядку.';
            }
            const routeGrid = routeSurface.querySelector('.bot-question-route-grid');
            if (routeGrid instanceof HTMLElement && !routeSurface.querySelector('[data-bot-question-route-note]')) {
              const note = document.createElement('div');
              note.className = 'small text-muted mb-3';
              note.dataset.botQuestionRouteNote = 'true';
              note.textContent = 'В списке показываются только вопросы ниже текущего. Сначала добавьте следующие шаги сценария, и затем они появятся здесь.';
              routeGrid.insertAdjacentElement('beforebegin', note);
            }
            const footer = routeSurface.querySelector('.form-text.mt-2.mb-0');
            if (footer instanceof HTMLElement) {
              footer.textContent = hasIntermediateTargets
                ? 'Можно отправить клиента к следующему релевантному вопросу или сразу к финальному описанию проблемы.'
                : 'Пока ниже нет дополнительных вопросов, поэтому доступен только переход по порядку или сразу к финальному описанию проблемы.';
            }
          }
          const sections = [
            ['options', '[data-bot-question-options-wrapper]', 'Варианты ответа'],
            ['analytics', '.bot-question-analytics-row', 'Аналитика и ключ ответа'],
            ['routes', '.bot-question-route-rowset', 'Переходы по ответам'],
            ['preview', '[data-bot-question-preview-wrapper]', 'Предпросмотр для клиента'],
          ];
          sections.forEach(([key, selector, label]) => {
            const content = card.querySelector(selector);
            if (!content || content.classList.contains('d-none')) return;
            const details = document.createElement('details');
            details.className = 'bot-question-disclosure';
            details.dataset.questionSection = key;
            const summary = document.createElement('summary');
            summary.textContent = label;
            if (key === 'analytics' && question.includeInDashboard) summary.textContent += ' · включена';
            if (key === 'routes' && question.routes?.length) summary.textContent += ` · ${question.routes.length}`;
            content.before(details);
            details.append(summary, content);
          });
          card.__questionMeta = meta;
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
          renderQuestionPreview(card, question, meta);
        });
        renderPresetHints();
        if (uiState) {
          restoreQuestionEditorUiState(uiState);
        }
      }

      function buildDashboardBindingOptions(selectedValue) {
        const normalized = context.isDashboardBindingKeyAllowed(selectedValue) ? selectedValue.trim() : '';
        return ['<option value="">Выберите атрибут</option>']
          .concat(context.DASHBOARD_ATTRIBUTE_KEYS.map((item) => (
            `<option value="${context.html(item.value)}"${item.value === normalized ? ' selected' : ''}>${context.html(item.label)}</option>`
          )))
          .join('');
      }

      function buildBindingEditorHtml(question, bindingKey, analyticsHint) {
        const includeInDashboard = Boolean(question && (question.includeInDashboard || question.include_in_dashboard));
        if (includeInDashboard) {
          return `
        <label class="form-label">Атрибут дашборда</label>
        <select class="form-select" data-bot-question-binding>
          ${buildDashboardBindingOptions(bindingKey)}
        </select>
        <div class="form-text mb-0">Для дашборда можно выбирать только разрешённые атрибуты, чтобы не создавать ошибки в аналитике.</div>
      `;
        }
        return `
      <label class="form-label">Ключ атрибута</label>
      <input type="text" class="form-control font-monospace" value="${context.html(bindingKey)}" data-bot-question-binding placeholder="company">
      <div class="form-text mb-0">${context.html(analyticsHint)}</div>
    `;
      }

      function openTemplateEditor(index) {
        if (!context.canManageChildModal(context.templateModalEl)) {
          return;
        }
        if (typeof index === 'number' && index >= 0 && index < context.state.templates.length) {
          const template = context.state.templates[index];
          context.editorState.index = index;
          context.editorState.templateId = template.id;
          context.editorState.name = template.name;
          context.editorState.description = template.description;
          context.editorState.startMessage = template.startMessage || '';
          context.editorState.firstResponseTimeoutMinutes = context.normalizeFirstResponseTimeoutMinutesValue(
            template.firstResponseTimeoutMinutes ?? template.first_response_timeout_minutes
          );
          context.editorState.firstResponseTimeoutMessage = context.normalizeFirstResponseTimeoutMessageValue(
            template.firstResponseTimeoutMessage ?? template.first_response_timeout_message
          );
          context.editorState.unifiedFields = Boolean(template.unifiedFields || template.scope_mode === 'UNIFIED_FIELDS');
          context.editorState.questionFlow = template.questionFlow.map((question) => context.cloneQuestion(question));
        } else {
          context.editorState.index = -1;
          context.editorState.templateId = context.generateTemplateId();
          context.editorState.name = 'Новый шаблон вопросов';
          context.editorState.description = '';
          context.editorState.startMessage = '';
          context.editorState.firstResponseTimeoutMinutes = context.DEFAULT_FIRST_RESPONSE_TIMEOUT_MINUTES;
          context.editorState.firstResponseTimeoutMessage = context.DEFAULT_FIRST_RESPONSE_TIMEOUT_MESSAGE;
          context.editorState.unifiedFields = false;
          context.editorState.questionFlow = [];
        }
        if (context.templateNameInput) {
          context.templateNameInput.value = context.editorState.name || '';
        }
        if (context.templateDescriptionInput) {
          context.templateDescriptionInput.value = context.editorState.description || '';
        }
        if (context.templateStartMessageInput) {
          context.templateStartMessageInput.value = context.editorState.startMessage || '';
        }
        if (context.templateFirstResponseTimeoutInput) {
          context.templateFirstResponseTimeoutInput.value = context.editorState.firstResponseTimeoutMinutes;
        }
        if (context.templateFirstResponseTimeoutMessageInput) {
          context.templateFirstResponseTimeoutMessageInput.value = context.editorState.firstResponseTimeoutMessage || '';
        }
        if (context.templateUnifiedFieldsInput) {
          context.templateUnifiedFieldsInput.checked = Boolean(context.editorState.unifiedFields);
        }
        context.setTemplateStatus('', false);
        renderQuestions();
        if (!context.hasPresetDefinitions()) {
          void context.ensurePresetDefinitionsLoaded();
        }
        context.showChildModal(context.templateModalEl);
      }

      async function saveTemplateFromEditor() {
        const name = context.templateNameInput ? context.templateNameInput.value.trim() : '';
        const description = context.templateDescriptionInput ? context.templateDescriptionInput.value.trim() : '';
        const startMessage = context.templateStartMessageInput ? context.templateStartMessageInput.value.trim() : '';
        const rawTimeoutValue = context.templateFirstResponseTimeoutInput ? context.templateFirstResponseTimeoutInput.value : '';
        const firstResponseTimeoutMinutes = context.normalizeFirstResponseTimeoutMinutesValue(
          rawTimeoutValue !== '' ? rawTimeoutValue : context.editorState.firstResponseTimeoutMinutes
        );
        const firstResponseTimeoutMessage = context.normalizeFirstResponseTimeoutMessageValue(
          context.templateFirstResponseTimeoutMessageInput ? context.templateFirstResponseTimeoutMessageInput.value : context.editorState.firstResponseTimeoutMessage
        );
        const unifiedFields = Boolean(context.templateUnifiedFieldsInput && context.templateUnifiedFieldsInput.checked)
          || context.editorState.questionFlow.some((question) => question && question.type === 'business');
        if (!name) {
          context.setTemplateStatus('Укажите название шаблона.', true);
          if (context.templateNameInput) {
            context.templateNameInput.focus();
          }
          return false;
        }
        if (context.templateFirstResponseTimeoutInput) {
          const parsed = Number.parseInt(rawTimeoutValue, 10);
          if (rawTimeoutValue !== '' && (!Number.isFinite(parsed) || parsed < 0)) {
            context.setTemplateStatus('Укажите корректный таймаут первого ответа в минутах.', true);
            context.templateFirstResponseTimeoutInput.focus();
            return false;
          }
        }
        if (!context.editorState.questionFlow.length) {
          context.setTemplateStatus('Добавьте хотя бы один вопрос.', true);
          return false;
        }
        for (let i = 0; i < context.editorState.questionFlow.length; i += 1) {
          const question = context.editorState.questionFlow[i];
          if (question.type === 'custom') {
            if (!question.text || !question.text.trim()) {
              context.setTemplateStatus(`Укажите текст для вопроса №${i + 1}.`, true);
              return false;
            }
          } else if (question.type === 'preset' && (!question.preset || !context.presetExists(question.preset.group, question.preset.field))) {
            context.setTemplateStatus(`Выберите готовое поле для вопроса №${i + 1}.`, true);
            return false;
          }
        }
        const questionFlowError = context.validateQuestionFlow(context.editorState.questionFlow, name);
        if (questionFlowError) {
          context.setTemplateStatus(questionFlowError, true);
          return false;
        }

        const normalizedQuestions = context.editorState.questionFlow.map((question, index) => context.serializeQuestion(question, index));
        const templatePayload = {
          id: context.editorState.templateId || context.generateTemplateId(),
          name,
          description,
          startMessage,
          firstResponseTimeoutMinutes,
          firstResponseTimeoutMessage,
          unifiedFields,
          questionFlow: normalizedQuestions.map((question) => ({
            id: question.id,
            type: question.type,
            text: question.text,
            order: question.order,
            required: question.required !== false,
            preset: question.preset ? { group: question.preset.group, field: question.preset.field } : undefined,
            options: Array.isArray(question.options) ? question.options.map((option) => ({ id: option.id, label: option.label })) : [],
            bindingKey: question.binding_key || '',
            includeInDashboard: Boolean(question.include_in_dashboard),
            routes: Array.isArray(question.routes)
              ? question.routes.map((route) => ({ valueId: route.value_id, nextQuestionId: route.next_question_id }))
              : [],
            excludedOptions: Array.isArray(question.excluded_options)
              ? question.excluded_options.slice()
              : Array.isArray(question.excludedOptions)
                ? question.excludedOptions.slice()
                : [],
          })),
        };

        const previousTemplates = context.state.templates.map((template) => context.cloneTemplate(template));
        const previousActiveTemplateId = context.state.activeTemplateId;
        const normalizedTemplateState = {
          id: templatePayload.id,
          name: templatePayload.name,
          description: templatePayload.description,
          startMessage: templatePayload.startMessage,
          firstResponseTimeoutMinutes: templatePayload.firstResponseTimeoutMinutes,
          firstResponseTimeoutMessage: templatePayload.firstResponseTimeoutMessage,
          unifiedFields: templatePayload.unifiedFields,
          questionFlow: templatePayload.questionFlow.map((question) => ({
            id: question.id,
            type: question.type,
            text: question.text,
            order: question.order,
            required: question.required !== false,
            preset: question.preset ? { group: question.preset.group, field: question.preset.field } : null,
            options: Array.isArray(question.options) ? question.options.map((option) => ({ id: option.id, label: option.label })) : [],
            bindingKey: question.bindingKey || '',
            includeInDashboard: Boolean(question.includeInDashboard),
            routes: Array.isArray(question.routes)
              ? question.routes.map((route) => ({ valueId: route.valueId, nextQuestionId: route.nextQuestionId }))
              : [],
            excludedOptions: Array.isArray(question.excludedOptions) ? question.excludedOptions : [],
          })),
        };

        if (context.editorState.index >= 0 && context.editorState.index < context.state.templates.length) {
          context.state.templates[context.editorState.index] = normalizedTemplateState;
        } else {
          context.state.templates.push(normalizedTemplateState);
          if (!context.state.activeTemplateId) {
            context.state.activeTemplateId = templatePayload.id;
          }
        }

        const originalSaveText = context.templateSaveButton ? context.templateSaveButton.textContent : '';
        if (context.templateSaveButton) {
          context.templateSaveButton.disabled = true;
          context.templateSaveButton.textContent = 'Сохраняем...';
        }
        if (context.templateCancelButton) {
          context.templateCancelButton.disabled = true;
        }
        context.setTemplateStatus('Сохраняем шаблон и общие настройки бота...', false);
        try {
          await context.persistBotSettingsPayload(collectPayload());
          await context.refreshLinkedBotSettingsConsumers().catch(() => undefined);
          context.setTemplateStatus('Шаблон сохранён.', false);
          context.hideChildModal(context.templateModalEl);
          return true;
        } catch (error) {
          context.state.templates = previousTemplates.map((template) => context.cloneTemplate(template));
          context.state.activeTemplateId = previousActiveTemplateId;
          context.renderTemplates();
          const message = error && error.message ? error.message : String(error);
          context.setTemplateStatus(`Ошибка сохранения: ${message}`, true);
          return false;
        } finally {
          if (context.templateSaveButton) {
            context.templateSaveButton.disabled = false;
            context.templateSaveButton.textContent = originalSaveText || 'Сохранить шаблон';
          }
          if (context.templateCancelButton) {
            context.templateCancelButton.disabled = false;
          }
        }
      }

      function collectPayload() {
        const templatesPayload = context.state.templates.map((template) => ({
          id: template.id,
          name: template.name,
          description: template.description,
          start_message: String(template.startMessage || template.start_message || '').trim(),
          first_response_timeout_minutes: context.normalizeFirstResponseTimeoutMinutesValue(
            template.firstResponseTimeoutMinutes ?? template.first_response_timeout_minutes
          ),
          first_response_timeout_message: context.normalizeFirstResponseTimeoutMessageValue(
            template.firstResponseTimeoutMessage ?? template.first_response_timeout_message
          ),
          scope_mode: template.unifiedFields ? 'UNIFIED_FIELDS' : undefined,
          question_flow: template.questionFlow.map(context.serializeQuestion),
        }));
        let activeTemplateId = context.state.activeTemplateId;
        if (!templatesPayload.some((template) => template.id === activeTemplateId)) {
          activeTemplateId = templatesPayload.length ? templatesPayload[0].id : null;
        }
        const ratingTemplatesPayload = context.state.ratingTemplates.map((template) => {
          const scale = context.normalizeScale(template.scaleSize || template.scale_size || 5);
          const ensured = context.normalizeRatingResponses(template.responses || {}, scale);
          const responses = [];
          for (let value = 1; value <= Math.max(1, scale); value += 1) {
            const key = String(value);
            responses.push({ value, text: String(ensured[key] || '').trim() });
          }
          return {
            id: template.id,
            name: String(template.name || '').trim() || 'Шаблон оценок',
            description: String(template.description || '').trim(),
            prompt_text: String(template.promptText || template.prompt_text || '').trim(),
            scale_size: scale,
            responses,
          };
        });
        let activeRatingTemplateId = context.state.activeRatingTemplateId;
        if (!ratingTemplatesPayload.some((template) => template.id === activeRatingTemplateId)) {
          activeRatingTemplateId = ratingTemplatesPayload.length ? ratingTemplatesPayload[0].id : null;
        }
        return {
          question_templates: templatesPayload,
          active_template_id: activeTemplateId,
          rating_templates: ratingTemplatesPayload,
          active_rating_template_id: activeRatingTemplateId,
          unblock_request_cooldown_minutes: context.state.unblockCooldownMinutes,
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
        for (const template of templates) {
          if (!template) {
            continue;
          }
          if (template.first_response_timeout_minutes != null) {
            const parsedTimeout = Number.parseInt(template.first_response_timeout_minutes, 10);
            if (!Number.isFinite(parsedTimeout) || parsedTimeout < 0) {
              return `Укажите корректный таймаут первого ответа в шаблоне "${String(template.name || '').trim() || 'Шаблон вопросов'}".`;
            }
          }
          const flow = Array.isArray(template.question_flow) ? template.question_flow : [];
          if (!flow.length) {
            return `Шаблон "${String(template.name || '').trim() || 'Шаблон вопросов'}" должен содержать хотя бы один вопрос.`;
          }
          const flowError = context.validateQuestionFlow(flow, String(template.name || '').trim(), false);
          if (flowError) {
            return flowError;
          }
        }
        const ratingTemplates = Array.isArray(payload.rating_templates) ? payload.rating_templates : [];
        if (!ratingTemplates.length) {
          return 'Добавьте хотя бы один шаблон оценок.';
        }
        if (payload.unblock_request_cooldown_minutes != null) {
          const parsed = Number.parseInt(payload.unblock_request_cooldown_minutes, 10);
          if (!Number.isFinite(parsed) || parsed < 0) {
            return 'Укажите корректный интервал между запросами на разблокировку (в минутах).';
          }
        }
        const activeRatingId = payload.active_rating_template_id;
        const nameForMessage = (template) => String(template?.name || '').trim() || 'Шаблон оценок';
        for (const template of ratingTemplates) {
          if (!template) {
            continue;
          }
          const prompt = String(template.prompt_text || template.prompt || '').trim();
          if (!prompt) {
            return `Укажите текст запроса оценки в шаблоне "${nameForMessage(template)}".`;
          }
          const scale = context.normalizeScale(template.scale_size || template.scale || template.scaleSize);
          const responses = Array.isArray(template.responses) ? template.responses : [];
          if (responses.length < Math.max(1, scale)) {
            return `Добавьте ответы для всех значений шкалы в шаблоне "${nameForMessage(template)}".`;
          }
          for (const response of responses) {
            if (!response || !String(response.text || '').trim()) {
              return `Каждое значение оценки должно иметь текст ответа в шаблоне "${nameForMessage(template)}".`;
            }
          }
        }
        if (!ratingTemplates.some((template) => template && template.id === activeRatingId)) {
          return 'Выберите активный шаблон оценок.';
        }
        return null;
      }
      return { buildDependencyValueHtml, buildDependencyPathsHtml, buildOptionDependencyHtml, highlightOptionRow, showOptionDependencies, buildQuestionRouteControls, escapeSelectorValue, resolveQuestionEditorScrollContainer, resolveFocusedQuestionFieldState, captureQuestionEditorUiState, restoreQuestionEditorUiState, renderPresetHints, renderLegacyDiagnostics, renderHiddenSummary, renderQuestionPreview, collectDependencyValues, buildFilterSelectHtml, buildFilterControls, applyQuestionFilters, renderQuestions, buildDashboardBindingOptions, buildBindingEditorHtml, openTemplateEditor, saveTemplateFromEditor, collectPayload, validatePayload };
    }
  });
})();

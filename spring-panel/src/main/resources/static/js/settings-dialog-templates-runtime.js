(function () {
  if (window.SettingsDialogTemplatesRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    function getDialogConfig() {
      const value = typeof options.getDialogConfig === 'function' ? options.getDialogConfig() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getAutoCloseConfig() {
      const value = typeof options.getAutoCloseConfig === 'function' ? options.getAutoCloseConfig() : null;
      return value && typeof value === 'object' ? value : {};
    }

    function getAutoCloseFallbackHours() {
      const value = Number(typeof options.getAutoCloseFallbackHours === 'function'
        ? options.getAutoCloseFallbackHours()
        : 24);
      return Number.isFinite(value) && value > 0 ? value : 24;
    }

    function getFallbackDialogCategories() {
      const value = typeof options.getFallbackDialogCategories === 'function'
        ? options.getFallbackDialogCategories()
        : null;
      return Array.isArray(value) ? value : [];
    }

    function canPublishDialogMacros() {
      return Boolean(typeof options.canPublishDialogMacros === 'function'
        ? options.canPublishDialogMacros()
        : false);
    }

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

    function generateDialogTemplateId(prefix) {
      const randomPart = Math.random().toString(36).slice(2, 8);
      const timePart = Date.now().toString(36);
      return `${prefix}-${randomPart}-${timePart}`;
    }

    function pluralizeRussian(number, forms) {
      const abs = Math.abs(number);
      const mod10 = abs % 10;
      const mod100 = abs % 100;
      if (mod10 === 1 && mod100 !== 11) {
        return forms[0];
      }
      if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
        return forms[1];
      }
      return forms[2];
    }

    function buildPreview(values, limit = 3) {
      if (!Array.isArray(values) || !values.length) {
        return '';
      }
      const slice = values.slice(0, limit);
      const preview = slice.join(', ');
      if (values.length > limit) {
        return `${preview}...`;
      }
      return preview;
    }

    function findDialogTemplateCard(source) {
      if (!(source instanceof HTMLElement)) {
        return null;
      }
      return source.closest('[data-category-template], [data-question-template], [data-completion-template], [data-macro-template], [data-auto-close-template]');
    }

    function toggleDialogTemplateEditor(source, forceState) {
      const card = source instanceof HTMLElement
        && source.matches('[data-category-template], [data-question-template], [data-completion-template], [data-macro-template], [data-auto-close-template]')
        ? source
        : findDialogTemplateCard(source);
      if (!card) {
        return;
      }
      const editor = card.querySelector('[data-dialog-template-editor]');
      const toggleBtn = card.querySelector('[data-dialog-template-toggle]');
      if (!editor || !toggleBtn) {
        return;
      }
      let shouldShow;
      if (typeof forceState === 'boolean') {
        shouldShow = forceState;
      } else {
        shouldShow = editor.classList.contains('d-none');
      }
      editor.classList.toggle('d-none', !shouldShow);
      toggleBtn.textContent = shouldShow ? 'Свернуть' : 'Настроить';
      toggleBtn.dataset.expanded = shouldShow ? 'true' : 'false';
      toggleBtn.setAttribute('aria-expanded', shouldShow ? 'true' : 'false');
      editor.setAttribute('aria-hidden', shouldShow ? 'false' : 'true');
    }

    function updateCategoryTemplateSummary(card) {
      if (!(card instanceof HTMLElement)) return;
      const nameInput = card.querySelector('.category-template-name');
      const titleEl = card.querySelector('[data-dialog-template-title]');
      const summaryEl = card.querySelector('[data-dialog-template-summary]');
      const name = (nameInput?.value || '').trim();
      if (titleEl) {
        titleEl.textContent = name || 'Шаблон категорий';
      }
      if (summaryEl) {
        const categories = Array.from(card.querySelectorAll('.category-item-input'))
          .map((input) => (input.value || '').trim())
          .filter((value) => value);
        if (!categories.length) {
          summaryEl.textContent = 'Нет категорий. Добавьте элементы в шаблон.';
        } else {
          const countText = `${categories.length} ${pluralizeRussian(categories.length, ['категория', 'категории', 'категорий'])}`;
          const preview = buildPreview(categories);
          summaryEl.textContent = preview ? `${countText} · ${preview}` : countText;
        }
      }
    }

    function updateQuestionTemplateSummary(card) {
      if (!(card instanceof HTMLElement)) return;
      const nameInput = card.querySelector('.question-template-name');
      const titleEl = card.querySelector('[data-dialog-template-title]');
      const summaryEl = card.querySelector('[data-dialog-template-summary]');
      const name = (nameInput?.value || '').trim();
      if (titleEl) {
        titleEl.textContent = name || 'Шаблон вопросов';
      }
      if (summaryEl) {
        const questions = Array.from(card.querySelectorAll('.question-item-input'))
          .map((input) => (input.value || '').trim())
          .filter((value) => value);
        if (!questions.length) {
          summaryEl.textContent = 'Нет вопросов. Добавьте их в редактор ниже.';
        } else {
          const countText = `${questions.length} ${pluralizeRussian(questions.length, ['вопрос', 'вопроса', 'вопросов'])}`;
          const preview = buildPreview(questions);
          summaryEl.textContent = preview ? `${countText} · ${preview}` : countText;
        }
      }
    }

    function updateCompletionTemplateSummary(card) {
      if (!(card instanceof HTMLElement)) return;
      const nameInput = card.querySelector('.completion-template-name');
      const titleEl = card.querySelector('[data-dialog-template-title]');
      const summaryEl = card.querySelector('[data-dialog-template-summary]');
      const name = (nameInput?.value || '').trim();
      if (titleEl) {
        titleEl.textContent = name || 'Шаблон действий';
      }
      if (summaryEl) {
        const items = Array.from(card.querySelectorAll('[data-completion-item]'))
          .map((row) => {
            if (!(row instanceof HTMLElement)) return null;
            const question = (row.querySelector('.completion-question-input')?.value || '').trim();
            const action = (row.querySelector('.completion-action-input')?.value || '').trim();
            if (!question && !action) return null;
            return question || action;
          })
          .filter(Boolean);
        if (!items.length) {
          summaryEl.textContent = 'Нет действий. Добавьте контрольные вопросы и шаги.';
        } else {
          const countText = `${items.length} ${pluralizeRussian(items.length, ['шаг', 'шага', 'шагов'])}`;
          const preview = buildPreview(items);
          summaryEl.textContent = preview ? `${countText} · ${preview}` : countText;
        }
      }
    }

    function updateMacroTemplateSummary(card) {
      if (!(card instanceof HTMLElement)) return;
      const nameInput = card.querySelector('.macro-template-name');
      const titleEl = card.querySelector('[data-dialog-template-title]');
      const summaryEl = card.querySelector('[data-dialog-template-summary]');
      const reviewStateEl = card.querySelector('[data-macro-review-state]');
      const approvedInput = card.querySelector('.macro-template-approved');
      const publishedInput = card.querySelector('.macro-template-published');
      const deprecatedInput = card.querySelector('.macro-template-deprecated');
      const name = (nameInput?.value || '').trim();
      const isApproved = Boolean(approvedInput?.checked);
      const isDeprecated = Boolean(deprecatedInput?.checked);
      if (titleEl) {
        titleEl.textContent = name || 'Макрос';
      }
      if (reviewStateEl) {
        reviewStateEl.textContent = canPublishDialogMacros()
          ? (isApproved ? 'Одобрено к публикации' : 'Требует ревью')
          : 'Публикация доступна пользователю с правом DIALOG_MACRO_PUBLISH';
      }
      if (approvedInput && !canPublishDialogMacros()) {
        approvedInput.disabled = true;
      }
      if (publishedInput) {
        if (!canPublishDialogMacros()) {
          publishedInput.disabled = true;
        } else {
          publishedInput.disabled = !isApproved;
          if (!isApproved) {
            publishedInput.checked = false;
          }
        }
      }
      if (summaryEl) {
        const tags = Array.from(card.querySelectorAll('.macro-tag-input'))
          .map((input) => (input.value || '').trim())
          .filter((value) => value);
        const message = (card.querySelector('.macro-template-message')?.value || '').trim();
        const owner = (card.querySelector('.macro-template-owner')?.value || '').trim();
        const namespace = (card.querySelector('.macro-template-namespace')?.value || '').trim();
        const deprecationReason = (card.querySelector('.macro-template-deprecation-reason')?.value || '').trim();
        if (!message && !tags.length) {
          summaryEl.textContent = 'Нет текста. Добавьте сообщение для быстрого ответа.';
        } else {
          const tagsText = tags.length
            ? `${tags.length} ${pluralizeRussian(tags.length, ['тег', 'тега', 'тегов'])}`
            : 'без тегов';
          const meta = [
            owner ? `owner: ${owner}` : '',
            namespace ? `ns: ${namespace}` : '',
            isDeprecated ? 'deprecated' : '',
            isDeprecated && deprecationReason ? `reason: ${deprecationReason}` : '',
          ]
            .filter(Boolean)
            .join(' · ');
          const baseSummary = message
            ? `${message.slice(0, 80)}${message.length > 80 ? '...' : ''} · ${tagsText}`
            : tagsText;
          summaryEl.textContent = meta ? `${baseSummary} · ${meta}` : baseSummary;
        }
      }
    }

    function addCategoryTemplate(template) {
      const list = document.getElementById('categoryTemplatesList');
      if (!list) return null;
      const data = template && typeof template === 'object' ? template : {};
      const templateId = data.id || generateDialogTemplateId('cat');
      const name = typeof data.name === 'string' ? data.name : '';
      const categories = Array.isArray(data.categories) ? data.categories : [];

      const card = document.createElement('div');
      card.className = 'card shadow-sm dialog-template-card';
      card.dataset.categoryTemplate = 'true';
      card.dataset.templateId = templateId;
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex flex-column flex-lg-row justify-content-between align-items-start gap-3">
            <div>
              <h6 class="mb-1" data-dialog-template-title>Шаблон категорий</h6>
              <div class="small text-muted dialog-template-summary" data-dialog-template-summary>Нет категорий. Добавьте элементы в шаблон.</div>
            </div>
            <div class="d-flex flex-column align-items-lg-end gap-2 w-100 w-lg-auto">
              <div class="btn-group btn-group-sm align-self-start align-self-lg-end">
                <button class="btn btn-outline-primary" type="button" data-dialog-template-toggle>Настроить</button>
                <button class="btn btn-outline-danger" type="button" data-category-template-remove>Удалить</button>
              </div>
            </div>
          </div>
          <div class="dialog-template-editor bg-light mt-3" data-dialog-template-editor aria-hidden="false">
            <div class="mb-3">
              <label class="form-label" for="category-template-${templateId}">Название шаблона</label>
              <input type="text" class="form-control form-control-sm category-template-name" id="category-template-${templateId}" placeholder="Название шаблона" value="">
            </div>
            <div class="d-flex flex-column gap-2" data-category-items></div>
            <button class="btn btn-sm btn-outline-primary mt-3" type="button" data-category-row-add>+ Категория</button>
          </div>
        </div>
      `;

      list.appendChild(card);
      const nameInput = card.querySelector('.category-template-name');
      if (nameInput) {
        nameInput.value = name;
        nameInput.addEventListener('input', () => updateCategoryTemplateSummary(card));
      }

      const itemsContainer = card.querySelector('[data-category-items]');
      if (itemsContainer) {
        if (categories.length) {
          categories.forEach((category) => addCategoryRow(itemsContainer, category));
        } else {
          addCategoryRow(itemsContainer);
        }
      }

      updateCategoryTemplateSummary(card);
      toggleDialogTemplateEditor(card, !(name || categories.length));
      return card;
    }

    function addCategoryRow(source, value = '') {
      let container = null;
      if (source instanceof HTMLElement && source.matches('[data-category-items]')) {
        container = source;
      } else if (source instanceof HTMLElement) {
        const card = source.closest('[data-category-template]');
        container = card ? card.querySelector('[data-category-items]') : null;
      }
      if (!container) return;
      const row = document.createElement('div');
      row.className = 'input-group input-group-sm';
      row.innerHTML = `
        <input type="text" class="form-control category-item-input" placeholder="Категория">
        <button class="btn btn-outline-danger" type="button" data-category-row-remove>✕</button>
      `;
      container.appendChild(row);
      const input = row.querySelector('input');
      if (input) {
        input.value = value || '';
        input.addEventListener('input', () => updateCategoryTemplateSummary(container.closest('[data-category-template]')));
      }
      updateCategoryTemplateSummary(container.closest('[data-category-template]'));
    }

    function removeCategoryTemplate(trigger) {
      const card = trigger?.closest('[data-category-template]');
      if (!card) return;
      const list = document.getElementById('categoryTemplatesList');
      card.remove();
      if (list && !list.querySelector('[data-category-template]')) {
        addCategoryTemplate();
      }
    }

    function removeCategoryRow(trigger) {
      const row = trigger?.closest('.input-group');
      if (!row) return;
      const container = row.parentElement;
      row.remove();
      if (container && !container.querySelector('.input-group')) {
        addCategoryRow(container);
      }
      updateCategoryTemplateSummary(container?.closest('[data-category-template]'));
    }

    function addQuestionTemplate(template) {
      const list = document.getElementById('questionTemplatesList');
      if (!list) return null;
      const data = template && typeof template === 'object' ? template : {};
      const templateId = data.id || generateDialogTemplateId('q');
      const name = typeof data.name === 'string' ? data.name : '';
      const questions = Array.isArray(data.questions) ? data.questions : [];

      const card = document.createElement('div');
      card.className = 'card shadow-sm dialog-template-card';
      card.dataset.questionTemplate = 'true';
      card.dataset.templateId = templateId;
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex flex-column flex-lg-row justify-content-between align-items-start gap-3">
            <div>
              <h6 class="mb-1" data-dialog-template-title>Шаблон вопросов</h6>
              <div class="small text-muted dialog-template-summary" data-dialog-template-summary>Нет вопросов. Добавьте их в редактор ниже.</div>
            </div>
            <div class="d-flex flex-column align-items-lg-end gap-2 w-100 w-lg-auto">
              <div class="btn-group btn-group-sm align-self-start align-self-lg-end">
                <button class="btn btn-outline-primary" type="button" data-dialog-template-toggle>Настроить</button>
                <button class="btn btn-outline-danger" type="button" data-question-template-remove>Удалить</button>
              </div>
            </div>
          </div>
          <div class="dialog-template-editor bg-light mt-3" data-dialog-template-editor aria-hidden="false">
            <div class="mb-3">
              <label class="form-label" for="question-template-${templateId}">Название шаблона</label>
              <input type="text" class="form-control form-control-sm question-template-name" id="question-template-${templateId}" placeholder="Название шаблона" value="">
            </div>
            <div class="d-flex flex-column gap-2" data-question-items></div>
            <button class="btn btn-sm btn-outline-primary mt-3" type="button" data-question-row-add>+ Вопрос</button>
          </div>
        </div>
      `;
      list.appendChild(card);

      const nameInput = card.querySelector('.question-template-name');
      if (nameInput) {
        nameInput.value = name;
        nameInput.addEventListener('input', () => updateQuestionTemplateSummary(card));
      }
      const itemsContainer = card.querySelector('[data-question-items]');
      if (itemsContainer) {
        if (questions.length) {
          questions.forEach((question) => addQuestionRow(itemsContainer, question));
        } else {
          addQuestionRow(itemsContainer);
        }
      }
      updateQuestionTemplateSummary(card);
      toggleDialogTemplateEditor(card, !(name || questions.length));
      return card;
    }

    function addQuestionRow(source, value = '') {
      let container = null;
      if (source instanceof HTMLElement && source.matches('[data-question-items]')) {
        container = source;
      } else if (source instanceof HTMLElement) {
        const card = source.closest('[data-question-template]');
        container = card ? card.querySelector('[data-question-items]') : null;
      }
      if (!container) return;
      const row = document.createElement('div');
      row.className = 'input-group input-group-sm';
      row.innerHTML = `
        <input type="text" class="form-control question-item-input" placeholder="Типовой вопрос">
        <button class="btn btn-outline-danger" type="button" data-question-row-remove>✕</button>
      `;
      container.appendChild(row);
      const input = row.querySelector('input');
      if (input) {
        input.value = value || '';
        input.addEventListener('input', () => updateQuestionTemplateSummary(container.closest('[data-question-template]')));
      }
      updateQuestionTemplateSummary(container.closest('[data-question-template]'));
    }

    function removeQuestionTemplate(trigger) {
      const card = trigger?.closest('[data-question-template]');
      if (!card) return;
      const list = document.getElementById('questionTemplatesList');
      card.remove();
      if (list && !list.querySelector('[data-question-template]')) {
        addQuestionTemplate();
      }
    }

    function removeQuestionRow(trigger) {
      const row = trigger?.closest('.input-group');
      if (!row) return;
      const container = row.parentElement;
      row.remove();
      if (container && !container.querySelector('.input-group')) {
        addQuestionRow(container);
      }
      updateQuestionTemplateSummary(container?.closest('[data-question-template]'));
    }

    function addCompletionTemplate(template) {
      const list = document.getElementById('completionTemplatesList');
      if (!list) return null;
      const data = template && typeof template === 'object' ? template : {};
      const templateId = data.id || generateDialogTemplateId('act');
      const name = typeof data.name === 'string' ? data.name : '';
      const items = Array.isArray(data.items) ? data.items : [];

      const card = document.createElement('div');
      card.className = 'card shadow-sm dialog-template-card';
      card.dataset.completionTemplate = 'true';
      card.dataset.templateId = templateId;
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex flex-column flex-lg-row justify-content-between align-items-start gap-3">
            <div>
              <h6 class="mb-1" data-dialog-template-title>Шаблон действий</h6>
              <div class="small text-muted dialog-template-summary" data-dialog-template-summary>Нет действий. Добавьте контрольные вопросы и шаги.</div>
            </div>
            <div class="d-flex flex-column align-items-lg-end gap-2 w-100 w-lg-auto">
              <div class="btn-group btn-group-sm align-self-start align-self-lg-end">
                <button class="btn btn-outline-primary" type="button" data-dialog-template-toggle>Настроить</button>
                <button class="btn btn-outline-danger" type="button" data-completion-template-remove>Удалить</button>
              </div>
            </div>
          </div>
          <div class="dialog-template-editor bg-light mt-3" data-dialog-template-editor aria-hidden="false">
            <div class="mb-3">
              <label class="form-label" for="completion-template-${templateId}">Название шаблона</label>
              <input type="text" class="form-control form-control-sm completion-template-name" id="completion-template-${templateId}" placeholder="Название шаблона" value="">
            </div>
            <div class="d-flex flex-column gap-3" data-completion-items></div>
            <button class="btn btn-sm btn-outline-primary mt-3" type="button" data-completion-row-add>+ Действие</button>
          </div>
        </div>
      `;
      list.appendChild(card);

      const nameInput = card.querySelector('.completion-template-name');
      if (nameInput) {
        nameInput.value = name;
        nameInput.addEventListener('input', () => updateCompletionTemplateSummary(card));
      }
      const itemsContainer = card.querySelector('[data-completion-items]');
      if (itemsContainer) {
        if (items.length) {
          items.forEach((entry) => {
            const question = entry && typeof entry.question === 'string' ? entry.question : '';
            const action = entry && typeof entry.action === 'string' ? entry.action : '';
            addCompletionRow(itemsContainer, question, action);
          });
        } else {
          addCompletionRow(itemsContainer);
        }
      }
      updateCompletionTemplateSummary(card);
      toggleDialogTemplateEditor(card, !(name || items.length));
      return card;
    }

    function addCompletionRow(source, questionValue = '', actionValue = '') {
      let container = null;
      if (source instanceof HTMLElement && source.matches('[data-completion-items]')) {
        container = source;
      } else if (source instanceof HTMLElement) {
        const card = source.closest('[data-completion-template]');
        container = card ? card.querySelector('[data-completion-items]') : null;
      }
      if (!container) return;

      const row = document.createElement('div');
      row.className = 'row g-2 align-items-start completion-item-row';
      row.dataset.completionItem = 'true';
      row.innerHTML = `
        <div class="col-md-5">
          <input type="text" class="form-control form-control-sm completion-question-input" placeholder="Контрольный вопрос">
        </div>
        <div class="col-md-6">
          <input type="text" class="form-control form-control-sm completion-action-input" placeholder="Действие">
        </div>
        <div class="col-md-1 d-flex align-items-center">
          <button class="btn btn-outline-danger btn-sm w-100" type="button" data-completion-row-remove>✕</button>
        </div>
      `;
      container.appendChild(row);
      const questionInput = row.querySelector('.completion-question-input');
      const actionInput = row.querySelector('.completion-action-input');
      if (questionInput) questionInput.value = questionValue || '';
      if (actionInput) actionInput.value = actionValue || '';
      [questionInput, actionInput].forEach((input) => {
        if (input) {
          input.addEventListener('input', () => updateCompletionTemplateSummary(container.closest('[data-completion-template]')));
        }
      });
      updateCompletionTemplateSummary(container.closest('[data-completion-template]'));
    }

    function removeCompletionTemplate(trigger) {
      const card = trigger?.closest('[data-completion-template]');
      if (!card) return;
      const list = document.getElementById('completionTemplatesList');
      card.remove();
      if (list && !list.querySelector('[data-completion-template]')) {
        addCompletionTemplate();
      }
    }

    function removeCompletionRow(trigger) {
      const row = trigger?.closest('[data-completion-item]');
      if (!row) return;
      const container = row.parentElement;
      row.remove();
      if (container && !container.querySelector('[data-completion-item]')) {
        addCompletionRow(container);
      }
      updateCompletionTemplateSummary(container?.closest('[data-completion-template]'));
    }

    function addMacroTemplate(template) {
      const list = document.getElementById('macroTemplatesList');
      if (!list) return null;
      const data = template && typeof template === 'object' ? template : {};
      const templateId = data.id || generateDialogTemplateId('macro');
      const name = typeof data.name === 'string' ? data.name : '';
      const message = typeof data.message === 'string'
        ? data.message
        : (typeof data.text === 'string' ? data.text : '');
      const tags = Array.isArray(data.tags) ? data.tags : [];
      const owner = typeof data.owner === 'string' ? data.owner : '';
      const namespace = typeof data.namespace === 'string' ? data.namespace : '';
      const deprecated = data.deprecated === true;
      const deprecationReason = typeof data.deprecation_reason === 'string' ? data.deprecation_reason : '';
      const published = data.published !== false;
      const approvedForPublish = data.approved_for_publish === true || data.review_state === 'approved';
      const reviewStateLabel = canPublishDialogMacros()
        ? (approvedForPublish ? 'Одобрено к публикации' : 'Требует ревью')
        : 'Публикация доступна пользователю с правом DIALOG_MACRO_PUBLISH';

      const card = document.createElement('div');
      card.className = 'card shadow-sm dialog-template-card';
      card.dataset.macroTemplate = 'true';
      card.dataset.templateId = templateId;
      card.innerHTML = `
        <div class="card-body">
          <div class="d-flex flex-column flex-lg-row justify-content-between align-items-start gap-3">
            <div>
              <h6 class="mb-1" data-dialog-template-title>Макрос</h6>
              <div class="small text-muted dialog-template-summary" data-dialog-template-summary>Нет текста. Добавьте сообщение для быстрого ответа.</div>
            </div>
            <div class="d-flex flex-column align-items-lg-end gap-2 w-100 w-lg-auto">
              <div class="btn-group btn-group-sm align-self-start align-self-lg-end">
                <button class="btn btn-outline-primary" type="button" data-dialog-template-toggle>Настроить</button>
                <button class="btn btn-outline-danger" type="button" data-macro-template-remove>Удалить</button>
              </div>
            </div>
          </div>
          <div class="dialog-template-editor bg-light mt-3" data-dialog-template-editor aria-hidden="false">
            <div class="mb-3">
              <label class="form-label" for="macro-template-${templateId}">Название макроса</label>
              <input type="text" class="form-control form-control-sm macro-template-name" id="macro-template-${templateId}" placeholder="Например, Проверка доставки" value="">
            </div>
            <div class="mb-3">
              <label class="form-label" for="macro-template-message-${templateId}">Текст ответа</label>
              <textarea class="form-control form-control-sm macro-template-message" id="macro-template-message-${templateId}" rows="3" placeholder="Текст, который будет вставлен в composer"></textarea>
            </div>
            <div class="row g-3 mb-3">
              <div class="col-md-6">
                <label class="form-label" for="macro-template-owner-${templateId}">Owner</label>
                <input type="text" class="form-control form-control-sm macro-template-owner" id="macro-template-owner-${templateId}" placeholder="Например, billing-ops" value="${escapeHtml(owner)}">
              </div>
              <div class="col-md-6">
                <label class="form-label" for="macro-template-namespace-${templateId}">Namespace</label>
                <input type="text" class="form-control form-control-sm macro-template-namespace" id="macro-template-namespace-${templateId}" placeholder="Например, billing.refund" value="${escapeHtml(namespace)}">
              </div>
            </div>
            <div class="form-check form-switch mb-3">
              <input class="form-check-input macro-template-published" type="checkbox" id="macro-template-published-${templateId}" ${published ? 'checked' : ''} ${canPublishDialogMacros() ? '' : 'disabled'}>
              <label class="form-check-label" for="macro-template-published-${templateId}">Опубликован для операторов</label>
            </div>
            <div class="form-check form-switch mb-2">
              <input class="form-check-input macro-template-approved" type="checkbox" id="macro-template-approved-${templateId}" ${approvedForPublish ? 'checked' : ''} ${canPublishDialogMacros() ? '' : 'disabled'}>
              <label class="form-check-label" for="macro-template-approved-${templateId}">Одобрен к публикации (review/admin)</label>
            </div>
            <div class="form-check form-switch mb-2">
              <input class="form-check-input macro-template-deprecated" type="checkbox" id="macro-template-deprecated-${templateId}" ${deprecated ? 'checked' : ''}>
              <label class="form-check-label" for="macro-template-deprecated-${templateId}">Deprecated: скрыть из операторского UI</label>
            </div>
            <div class="mb-2">
              <label class="form-label" for="macro-template-deprecation-reason-${templateId}">Причина deprecation</label>
              <input type="text" class="form-control form-control-sm macro-template-deprecation-reason" id="macro-template-deprecation-reason-${templateId}" placeholder="Почему макрос выводится из эксплуатации" value="${escapeHtml(deprecationReason)}">
            </div>
            <div class="small text-muted mb-3" data-macro-review-state>${reviewStateLabel}</div>
            <div class="d-flex flex-column gap-2" data-macro-tags></div>
            <button class="btn btn-sm btn-outline-primary mt-3" type="button" data-macro-tag-row-add>+ Тег</button>
          </div>
        </div>
      `;

      list.appendChild(card);
      const nameInput = card.querySelector('.macro-template-name');
      const messageInput = card.querySelector('.macro-template-message');
      if (nameInput) {
        nameInput.value = name;
        nameInput.addEventListener('input', () => updateMacroTemplateSummary(card));
      }
      if (messageInput) {
        messageInput.value = message;
        messageInput.addEventListener('input', () => updateMacroTemplateSummary(card));
      }
      const approvedInput = card.querySelector('.macro-template-approved');
      if (approvedInput) {
        approvedInput.addEventListener('change', () => updateMacroTemplateSummary(card));
      }
      ['.macro-template-owner', '.macro-template-namespace', '.macro-template-deprecation-reason', '.macro-template-deprecated'].forEach((selector) => {
        const input = card.querySelector(selector);
        if (input) {
          input.addEventListener('input', () => updateMacroTemplateSummary(card));
          input.addEventListener('change', () => updateMacroTemplateSummary(card));
        }
      });
      const tagsContainer = card.querySelector('[data-macro-tags]');
      if (tagsContainer) {
        if (tags.length) {
          tags.forEach((tag) => addMacroTagRow(tagsContainer, tag));
        } else {
          addMacroTagRow(tagsContainer);
        }
      }
      updateMacroTemplateSummary(card);
      toggleDialogTemplateEditor(card, !(name || message || tags.length));
      return card;
    }

    function addMacroTagRow(source, value = '') {
      let container = null;
      if (source instanceof HTMLElement && source.matches('[data-macro-tags]')) {
        container = source;
      } else if (source instanceof HTMLElement) {
        const card = source.closest('[data-macro-template]');
        container = card ? card.querySelector('[data-macro-tags]') : null;
      }
      if (!container) return;
      const row = document.createElement('div');
      row.className = 'input-group input-group-sm';
      row.innerHTML = `
        <input type="text" class="form-control macro-tag-input" placeholder="Тег">
        <button class="btn btn-outline-danger" type="button" data-macro-tag-row-remove>✕</button>
      `;
      container.appendChild(row);
      const input = row.querySelector('input');
      if (input) {
        input.value = value || '';
        input.addEventListener('input', () => updateMacroTemplateSummary(container.closest('[data-macro-template]')));
      }
      updateMacroTemplateSummary(container.closest('[data-macro-template]'));
    }

    function removeMacroTemplate(trigger) {
      const card = trigger?.closest('[data-macro-template]');
      if (!card) return;
      const list = document.getElementById('macroTemplatesList');
      card.remove();
      if (list && !list.querySelector('[data-macro-template]')) {
        addMacroTemplate();
      }
    }

    function removeMacroTagRow(trigger) {
      const row = trigger?.closest('.input-group');
      if (!row) return;
      const container = row.parentElement;
      row.remove();
      if (container && !container.querySelector('.input-group')) {
        addMacroTagRow(container);
      }
      updateMacroTemplateSummary(container?.closest('[data-macro-template]'));
    }

    function buildAutoCloseTemplateCard(template) {
      const list = document.querySelector('[data-auto-close-templates]');
      if (!list) return null;
      const data = template && typeof template === 'object' ? template : {};
      const templateId = data.id || generateDialogTemplateId('auto');
      const name = typeof data.name === 'string' ? data.name : '';
      const description = typeof data.description === 'string' ? data.description : '';
      let hoursCandidate = null;
      ['hours', 'timeout_hours', 'auto_close_hours'].some((key) => {
        const value = data && typeof data === 'object' ? Number(data[key]) : Number.NaN;
        if (Number.isFinite(value) && value > 0) {
          hoursCandidate = value;
          return true;
        }
        return false;
      });
      const rawHours = Number.isFinite(hoursCandidate) && hoursCandidate > 0
        ? hoursCandidate
        : getAutoCloseFallbackHours();
      const hours = Math.min(Math.max(Math.round(rawHours || getAutoCloseFallbackHours()), 1), 720);
      const safeIdAttr = escapeHtml(templateId);
      const safeName = escapeHtml(name);
      const safeDescription = escapeHtml(description);

      const card = document.createElement('div');
      card.className = 'card shadow-sm dialog-template-card template-card';
      card.dataset.autoCloseTemplate = 'true';
      card.dataset.templateId = templateId;
      card.innerHTML = `
        <div class="card-body">
          <div class="template-card-header">
            <h6 class="mb-1" data-auto-close-title>Шаблон автозакрытия</h6>
            <div class="small text-muted dialog-template-summary" data-auto-close-summary>Настройте параметры автозакрытия.</div>
          </div>
          <div class="d-flex flex-column flex-lg-row align-items-center justify-content-between gap-3 mt-3">
            <div class="form-check">
              <input class="form-check-input" type="radio" id="auto-template-active-${safeIdAttr}" name="autoCloseActive" value="${safeIdAttr}" data-auto-close-active>
              <label class="form-check-label small" for="auto-template-active-${safeIdAttr}">Активный по умолчанию</label>
            </div>
            <div class="btn-group btn-group-sm template-card-actions">
              <button class="btn btn-outline-primary" type="button" data-dialog-template-toggle>Настроить</button>
              <button class="btn btn-outline-danger" type="button" data-auto-close-template-remove>Удалить</button>
            </div>
          </div>
          <div class="dialog-template-editor bg-light mt-3" data-dialog-template-editor>
            <div class="mb-3">
              <label class="form-label" for="auto-template-name-${safeIdAttr}">Название шаблона</label>
              <input type="text" class="form-control form-control-sm auto-close-template-name" id="auto-template-name-${safeIdAttr}" placeholder="Например, 24 часа" value="${safeName}">
            </div>
            <div class="row g-2">
              <div class="col-lg-4">
                <label class="form-label" for="auto-template-hours-${safeIdAttr}">Через сколько закрывать (часы)</label>
                <input type="number" class="form-control form-control-sm auto-close-template-hours" id="auto-template-hours-${safeIdAttr}" min="1" max="720" step="1" value="${hours}">
                <div class="form-text">Минимум 1 час, максимум 720 часов.</div>
              </div>
              <div class="col-lg-8">
                <label class="form-label" for="auto-template-description-${safeIdAttr}">Описание (необязательно)</label>
                <textarea class="form-control form-control-sm auto-close-template-description" id="auto-template-description-${safeIdAttr}" rows="2" placeholder="Добавьте комментарий или условия срабатывания.">${safeDescription}</textarea>
              </div>
            </div>
          </div>
        </div>
      `;

      list.appendChild(card);
      const nameInput = card.querySelector('.auto-close-template-name');
      const hoursInput = card.querySelector('.auto-close-template-hours');
      const descriptionInput = card.querySelector('.auto-close-template-description');
      const activeInput = card.querySelector('[data-auto-close-active]');
      const updateSummary = () => updateAutoCloseTemplateSummary(card);

      nameInput?.addEventListener('input', updateSummary);
      descriptionInput?.addEventListener('input', updateSummary);
      hoursInput?.addEventListener('input', () => {
        if (!hoursInput) return;
        const value = Math.round(Number(hoursInput.value));
        if (!Number.isFinite(value) || value <= 0) {
          hoursInput.classList.add('is-invalid');
        } else {
          hoursInput.classList.remove('is-invalid');
        }
        updateSummary();
      });
      activeInput?.addEventListener('change', () => {
        document.querySelectorAll('[data-auto-close-template]').forEach((otherCard) => {
          if (!(otherCard instanceof HTMLElement)) return;
          otherCard.classList.toggle('border-primary', otherCard === card && activeInput.checked);
        });
      });
      const hasCustomHours = data && typeof data === 'object' && Object.prototype.hasOwnProperty.call(data, 'hours');
      toggleDialogTemplateEditor(card, !(name || description || hasCustomHours));
      updateAutoCloseTemplateSummary(card);
      return card;
    }

    function updateAutoCloseTemplateSummary(card) {
      if (!(card instanceof HTMLElement)) return;
      const nameInput = card.querySelector('.auto-close-template-name');
      const hoursInput = card.querySelector('.auto-close-template-hours');
      const descriptionInput = card.querySelector('.auto-close-template-description');
      const titleEl = card.querySelector('[data-auto-close-title]');
      const summaryEl = card.querySelector('[data-auto-close-summary]');
      const name = (nameInput?.value || '').trim();
      const description = (descriptionInput?.value || '').trim();
      const hoursValue = Math.round(Number(hoursInput?.value));
      if (titleEl) {
        titleEl.textContent = name || 'Шаблон автозакрытия';
      }
      if (summaryEl) {
        if (!Number.isFinite(hoursValue) || hoursValue <= 0) {
          summaryEl.textContent = 'Укажите время автозакрытия и при необходимости описание.';
        } else {
          const normalized = Math.min(Math.max(hoursValue, 1), 720);
          const hoursText = `${normalized} ${pluralizeRussian(normalized, ['час', 'часа', 'часов'])}`;
          summaryEl.textContent = description
            ? `Закрытие через ${hoursText}. ${description}`
            : `Закрытие через ${hoursText}.`;
        }
      }
    }

    function addAutoCloseTemplate(template) {
      const card = buildAutoCloseTemplateCard(template);
      if (!card) return;
      const activeInput = card.querySelector('[data-auto-close-active]');
      if (activeInput) {
        const shouldActivate = template?.isActive === true;
        activeInput.checked = shouldActivate;
        if (shouldActivate) {
          activeInput.dispatchEvent(new Event('change'));
        }
      }
    }

    function removeAutoCloseTemplate(trigger) {
      const card = trigger?.closest('[data-auto-close-template]');
      const list = document.querySelector('[data-auto-close-templates]');
      if (!card || !list) return;
      const wasActive = card.querySelector('[data-auto-close-active]')?.checked;
      card.remove();
      if (!list.querySelector('[data-auto-close-template]')) {
        addAutoCloseTemplate({ hours: getAutoCloseFallbackHours(), isActive: true });
      }
      if (wasActive) {
        const firstRadio = list.querySelector('[data-auto-close-template] [data-auto-close-active]');
        if (firstRadio instanceof HTMLInputElement) {
          firstRadio.checked = true;
          firstRadio.dispatchEvent(new Event('change'));
        }
      }
    }

    function collectAutoCloseConfig() {
      const cards = Array.from(document.querySelectorAll('[data-auto-close-template]'));
      const templates = [];
      let hasInvalidHours = false;
      cards.forEach((card, index) => {
        if (!(card instanceof HTMLElement)) return;
        let templateId = card.dataset.templateId || generateDialogTemplateId('auto');
        card.dataset.templateId = templateId;
        const nameInput = card.querySelector('.auto-close-template-name');
        const hoursInput = card.querySelector('.auto-close-template-hours');
        const descriptionInput = card.querySelector('.auto-close-template-description');
        const name = (nameInput?.value || '').trim();
        const description = (descriptionInput?.value || '').trim();
        const rawHours = Math.round(Number(hoursInput?.value));
        if (!Number.isFinite(rawHours) || rawHours <= 0) {
          hasInvalidHours = true;
          hoursInput?.classList.add('is-invalid');
          return;
        }
        const normalizedHours = Math.min(Math.max(rawHours, 1), 720);
        if (hoursInput) {
          hoursInput.classList.remove('is-invalid');
          hoursInput.value = normalizedHours;
        }
        if (!templateId) {
          templateId = generateDialogTemplateId('auto');
          card.dataset.templateId = templateId;
        }
        templates.push({
          id: templateId,
          name: name || `Шаблон автозакрытия ${index + 1}`,
          hours: normalizedHours,
          description,
        });
      });
      const activeInput = document.querySelector('input[name="autoCloseActive"]:checked');
      const activeId = activeInput?.value && templates.some((template) => template.id === activeInput.value)
        ? activeInput.value
        : (templates[0]?.id || null);
      const errors = hasInvalidHours
        ? ['Укажите корректное количество часов для всех шаблонов автозакрытия.']
        : [];
      return { templates, active_template_id: activeId, errors };
    }

    function initAutoCloseTemplates() {
      const list = document.querySelector('[data-auto-close-templates]');
      if (!list) return;
      list.innerHTML = '';
      const autoCloseConfig = getAutoCloseConfig();
      const rawTemplates = Array.isArray(autoCloseConfig.templates) ? autoCloseConfig.templates : [];
      const activeId = typeof autoCloseConfig.active_template_id === 'string' ? autoCloseConfig.active_template_id : null;
      if (rawTemplates.length) {
        rawTemplates.forEach((template) => {
          const card = buildAutoCloseTemplateCard(template);
          if (!card) return;
          const radio = card.querySelector('[data-auto-close-active]');
          if (radio instanceof HTMLInputElement) {
            const isActive = typeof template?.id === 'string' && template.id === activeId;
            radio.checked = isActive;
            radio.dispatchEvent(new Event('change'));
            card.dataset.templateId = template.id || card.dataset.templateId;
          }
          updateAutoCloseTemplateSummary(card);
        });
      } else {
        const card = buildAutoCloseTemplateCard({ hours: getAutoCloseFallbackHours() });
        if (card) {
          const radio = card.querySelector('[data-auto-close-active]');
          if (radio instanceof HTMLInputElement) {
            radio.checked = true;
            radio.dispatchEvent(new Event('change'));
          }
        }
      }
      if (!document.querySelector('input[name="autoCloseActive"]:checked')) {
        const firstRadio = list.querySelector('[data-auto-close-template] [data-auto-close-active]');
        if (firstRadio instanceof HTMLInputElement) {
          firstRadio.checked = true;
          firstRadio.dispatchEvent(new Event('change'));
        }
      }
    }

    function initDialogTemplates() {
      const dialogConfig = getDialogConfig();
      const categoryTemplates = Array.isArray(dialogConfig.category_templates)
        ? dialogConfig.category_templates
        : [];
      const categoryList = document.getElementById('categoryTemplatesList');
      if (categoryList) {
        categoryList.innerHTML = '';
      }
      if (categoryTemplates.length) {
        categoryTemplates.forEach((template) => addCategoryTemplate(template));
      } else {
        addCategoryTemplate({ categories: Array.isArray(getFallbackDialogCategories()) ? [...getFallbackDialogCategories()] : [] });
      }

      const questionTemplates = Array.isArray(dialogConfig.question_templates)
        ? dialogConfig.question_templates
        : [];
      const questionList = document.getElementById('questionTemplatesList');
      if (questionList) {
        questionList.innerHTML = '';
      }
      if (questionTemplates.length) {
        questionTemplates.forEach((template) => addQuestionTemplate(template));
      } else {
        addQuestionTemplate();
      }

      const completionTemplates = Array.isArray(dialogConfig.completion_templates)
        ? dialogConfig.completion_templates
        : [];
      const completionList = document.getElementById('completionTemplatesList');
      if (completionList) {
        completionList.innerHTML = '';
      }
      if (completionTemplates.length) {
        completionTemplates.forEach((template) => addCompletionTemplate(template));
      } else {
        addCompletionTemplate();
      }

      const macroTemplates = Array.isArray(dialogConfig.macro_templates)
        ? dialogConfig.macro_templates
        : [];
      const macroList = document.getElementById('macroTemplatesList');
      if (macroList) {
        macroList.innerHTML = '';
      }
      if (macroTemplates.length) {
        macroTemplates.forEach((template) => addMacroTemplate(template));
      } else {
        addMacroTemplate();
      }
    }

    function collectDialogTemplatesPayload() {
      const categoryTemplates = Array.from(document.querySelectorAll('[data-category-template]'))
        .map((card, index) => {
          if (!(card instanceof HTMLElement)) return null;
          const templateId = card.dataset.templateId || generateDialogTemplateId('cat');
          card.dataset.templateId = templateId;
          const name = (card.querySelector('.category-template-name')?.value || '').trim();
          const categories = Array.from(card.querySelectorAll('.category-item-input'))
            .map((input) => (input.value || '').trim())
            .filter((value) => value);
          return {
            id: templateId,
            name: name || `Шаблон категорий ${index + 1}`,
            categories,
          };
        })
        .filter(Boolean);

      const fallbackCategories = categoryTemplates.length ? categoryTemplates[0].categories : [];

      const questionTemplates = Array.from(document.querySelectorAll('[data-question-template]'))
        .map((card, index) => {
          if (!(card instanceof HTMLElement)) return null;
          const templateId = card.dataset.templateId || generateDialogTemplateId('q');
          card.dataset.templateId = templateId;
          const name = (card.querySelector('.question-template-name')?.value || '').trim();
          const questions = Array.from(card.querySelectorAll('.question-item-input'))
            .map((input) => (input.value || '').trim())
            .filter((value) => value);
          return {
            id: templateId,
            name: name || `Шаблон вопросов ${index + 1}`,
            questions,
          };
        })
        .filter(Boolean);

      const completionTemplates = Array.from(document.querySelectorAll('[data-completion-template]'))
        .map((card, index) => {
          if (!(card instanceof HTMLElement)) return null;
          const templateId = card.dataset.templateId || generateDialogTemplateId('act');
          card.dataset.templateId = templateId;
          const name = (card.querySelector('.completion-template-name')?.value || '').trim();
          const items = Array.from(card.querySelectorAll('[data-completion-item]'))
            .map((row) => {
              if (!(row instanceof HTMLElement)) return null;
              const question = (row.querySelector('.completion-question-input')?.value || '').trim();
              const action = (row.querySelector('.completion-action-input')?.value || '').trim();
              if (!question && !action) return null;
              return { question, action };
            })
            .filter(Boolean);
          return {
            id: templateId,
            name: name || `Шаблон действий ${index + 1}`,
            items,
          };
        })
        .filter(Boolean);

      const macroTemplates = Array.from(document.querySelectorAll('[data-macro-template]'))
        .map((card, index) => {
          if (!(card instanceof HTMLElement)) return null;
          const templateId = card.dataset.templateId || generateDialogTemplateId('macro');
          card.dataset.templateId = templateId;
          const name = (card.querySelector('.macro-template-name')?.value || '').trim();
          const message = (card.querySelector('.macro-template-message')?.value || '').trim();
          const owner = (card.querySelector('.macro-template-owner')?.value || '').trim();
          const namespace = (card.querySelector('.macro-template-namespace')?.value || '').trim();
          const deprecated = Boolean(card.querySelector('.macro-template-deprecated')?.checked);
          const deprecationReason = (card.querySelector('.macro-template-deprecation-reason')?.value || '').trim();
          const approvedForPublish = Boolean(card.querySelector('.macro-template-approved')?.checked);
          const tags = Array.from(card.querySelectorAll('.macro-tag-input'))
            .map((input) => (input.value || '').trim())
            .filter((value, tagIndex, values) => value && values.indexOf(value) === tagIndex);
          if (!name && !message && !tags.length && !owner && !namespace && !deprecated && !deprecationReason) {
            return null;
          }
          return {
            id: templateId,
            name: name || `Макрос ${index + 1}`,
            message,
            owner,
            namespace,
            deprecated,
            deprecation_reason: deprecationReason,
            approved_for_publish: approvedForPublish,
            published: Boolean(card.querySelector('.macro-template-published')?.checked),
            tags,
          };
        })
        .filter(Boolean);

      return {
        categoryTemplates,
        completionTemplates,
        fallbackCategories,
        macroTemplates,
        questionTemplates,
      };
    }

    function initialize() {
      initDialogTemplates();
      initAutoCloseTemplates();
    }

    return {
      addAutoCloseTemplate,
      addCategoryRow,
      addCategoryTemplate,
      addCompletionRow,
      addCompletionTemplate,
      addMacroTagRow,
      addMacroTemplate,
      addQuestionRow,
      addQuestionTemplate,
      collectAutoCloseConfig,
      collectDialogTemplatesPayload,
      initAutoCloseTemplates,
      initDialogTemplates,
      initialize,
      removeAutoCloseTemplate,
      removeCategoryRow,
      removeCategoryTemplate,
      removeCompletionRow,
      removeCompletionTemplate,
      removeMacroTagRow,
      removeMacroTemplate,
      removeQuestionRow,
      removeQuestionTemplate,
      toggleDialogTemplateEditor,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsDialogTemplatesRuntime) {
        return window.__settingsDialogTemplatesRuntime;
      }
      const runtime = createRuntime(options);
      window.SettingsPageCallbackRegistry?.registerMany({
        addAutoCloseTemplate: runtime.addAutoCloseTemplate,
        addCategoryTemplate: runtime.addCategoryTemplate,
        addQuestionTemplate: runtime.addQuestionTemplate,
        addCompletionTemplate: runtime.addCompletionTemplate,
        addMacroTemplate: runtime.addMacroTemplate,
        toggleDialogTemplateEditor: runtime.toggleDialogTemplateEditor,
        removeCategoryTemplate: runtime.removeCategoryTemplate,
        addCategoryRow: runtime.addCategoryRow,
        removeCategoryRow: runtime.removeCategoryRow,
        removeQuestionTemplate: runtime.removeQuestionTemplate,
        addQuestionRow: runtime.addQuestionRow,
        removeQuestionRow: runtime.removeQuestionRow,
        removeCompletionTemplate: runtime.removeCompletionTemplate,
        addCompletionRow: runtime.addCompletionRow,
        removeCompletionRow: runtime.removeCompletionRow,
        removeMacroTemplate: runtime.removeMacroTemplate,
        addMacroTagRow: runtime.addMacroTagRow,
        removeMacroTagRow: runtime.removeMacroTagRow,
        removeAutoCloseTemplate: runtime.removeAutoCloseTemplate,
      });
      runtime.initialize();
      window.__settingsDialogTemplatesRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsDialogTemplatesRuntime = Object.freeze({
    ...api,
    addAutoCloseTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.addAutoCloseTemplate?.(...args);
    },
    addCategoryTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.addCategoryTemplate?.(...args);
    },
    addQuestionTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.addQuestionTemplate?.(...args);
    },
    addCompletionTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.addCompletionTemplate?.(...args);
    },
    addMacroTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.addMacroTemplate?.(...args);
    },
    toggleDialogTemplateEditor(...args) {
      return window.__settingsDialogTemplatesRuntime?.toggleDialogTemplateEditor?.(...args);
    },
    removeCategoryTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeCategoryTemplate?.(...args);
    },
    addCategoryRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.addCategoryRow?.(...args);
    },
    removeCategoryRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeCategoryRow?.(...args);
    },
    removeQuestionTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeQuestionTemplate?.(...args);
    },
    addQuestionRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.addQuestionRow?.(...args);
    },
    removeQuestionRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeQuestionRow?.(...args);
    },
    removeCompletionTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeCompletionTemplate?.(...args);
    },
    addCompletionRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.addCompletionRow?.(...args);
    },
    removeCompletionRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeCompletionRow?.(...args);
    },
    removeMacroTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeMacroTemplate?.(...args);
    },
    addMacroTagRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.addMacroTagRow?.(...args);
    },
    removeMacroTagRow(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeMacroTagRow?.(...args);
    },
    removeAutoCloseTemplate(...args) {
      return window.__settingsDialogTemplatesRuntime?.removeAutoCloseTemplate?.(...args);
    },
  });
}());

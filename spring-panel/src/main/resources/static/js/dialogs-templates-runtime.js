(function () {
  if (window.DialogsTemplatesRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      completionHideTimer: null,
      templateEventsBound: false,
    };

    function getSelectedCategories() {
      const categories = typeof options.getSelectedCategories === 'function'
        ? options.getSelectedCategories()
        : null;
      return categories instanceof Set ? categories : new Set();
    }

    function getTemplateConfig() {
      return typeof options.getTemplateConfig === 'function'
        ? options.getTemplateConfig()
        : {
          categoryTemplates: [],
          questionTemplates: [],
          completionTemplates: [],
          emoji: [],
        };
    }

    function getDialogState() {
      const dialogState = typeof options.getDialogState === 'function'
        ? options.getDialogState()
        : null;
      return dialogState && typeof dialogState === 'object'
        ? dialogState
        : {
          activeDialogTicketId: '',
          activeWorkspaceTicketId: '',
          detailsModalOpen: false,
        };
    }

    function renderCategoryTemplate(template) {
      if (!elements.categoryTemplateList || !elements.categoryTemplateEmpty) return;
      const selectedCategories = getSelectedCategories();
      const categories = Array.isArray(template?.categories) ? template.categories.filter(Boolean) : [];
      elements.categoryTemplateList.innerHTML = '';
      categories.forEach((category) => {
        const badge = document.createElement('button');
        const normalized = String(category).trim();
        badge.className = 'badge rounded-pill text-bg-light border dialog-category-badge';
        badge.type = 'button';
        badge.dataset.categoryValue = normalized;
        badge.textContent = normalized;
        badge.classList.toggle('is-selected', selectedCategories.has(normalized));
        elements.categoryTemplateList.appendChild(badge);
      });
      const hasItems = categories.length > 0;
      elements.categoryTemplateList.classList.toggle('d-none', !hasItems);
      elements.categoryTemplateEmpty.classList.toggle('d-none', hasItems);
    }

    function renderQuestionTemplate(template) {
      if (!elements.questionTemplateList || !elements.questionTemplateEmpty) return;
      const questions = Array.isArray(template?.questions) ? template.questions.filter(Boolean) : [];
      elements.questionTemplateList.innerHTML = '';
      questions.forEach((question) => {
        const button = document.createElement('button');
        button.className = 'btn btn-outline-secondary btn-sm text-start';
        button.type = 'button';
        button.dataset.questionTemplateItem = '';
        button.dataset.questionValue = question;
        button.textContent = question;
        elements.questionTemplateList.appendChild(button);
      });
      const hasItems = questions.length > 0;
      elements.questionTemplateList.classList.toggle('d-none', !hasItems);
      elements.questionTemplateEmpty.classList.toggle('d-none', hasItems);
    }

    function renderCompletionTemplate(template) {
      if (!elements.completionTemplateList || !elements.completionTemplateEmpty) return;
      const items = Array.isArray(template?.items) ? template.items.filter(Boolean) : [];
      elements.completionTemplateList.innerHTML = '';
      items.forEach((item) => {
        const wrapper = document.createElement('div');
        wrapper.className = 'border rounded p-2 bg-light';
        const question = document.createElement('div');
        question.className = 'fw-semibold';
        question.textContent = item?.question || 'Контрольный вопрос';
        const action = document.createElement('div');
        action.className = 'text-muted';
        action.textContent = item?.action || 'Действие';
        wrapper.appendChild(question);
        wrapper.appendChild(action);
        elements.completionTemplateList.appendChild(wrapper);
      });
      const hasItems = items.length > 0;
      elements.completionTemplateList.classList.toggle('d-none', !hasItems);
      elements.completionTemplateEmpty.classList.toggle('d-none', hasItems);
    }

    function syncCategorySelections() {
      if (!elements.categoryTemplateList) return;
      const selectedCategories = getSelectedCategories();
      elements.categoryTemplateList.querySelectorAll('[data-category-value]').forEach((item) => {
        const value = item.dataset.categoryValue || '';
        item.classList.toggle('is-selected', selectedCategories.has(value));
      });
    }

    function renderEmojiPanel() {
      if (!elements.emojiList) return;
      const safeEmoji = Array.isArray(getTemplateConfig().emoji) ? getTemplateConfig().emoji : [];
      elements.emojiList.innerHTML = '';
      safeEmoji.forEach((emoji) => {
        const button = document.createElement('button');
        button.className = 'btn btn-outline-secondary btn-sm';
        button.type = 'button';
        button.dataset.emojiValue = emoji;
        button.textContent = emoji;
        elements.emojiList.appendChild(button);
      });
    }

    function insertReplyText(value) {
      if (!elements.detailsReplyText || !value) return;
      const existing = elements.detailsReplyText.value.trim();
      elements.detailsReplyText.value = existing ? `${existing}\n${value}` : value;
      elements.detailsReplyText.focus();
    }

    function openCategoryPanel() {
      if (!elements.categoryTemplatesSection || elements.categoryTemplatesSection.classList.contains('d-none')) return;
      const dialogState = getDialogState();
      const ticketId = dialogState.activeDialogTicketId || dialogState.activeWorkspaceTicketId;
      if (options.notifyActionBlocked?.('categories', 'Категории', { ticketId, permissionKey: 'can_close' })) {
        return;
      }
      if (dialogState.detailsModalOpen && dialogState.activeDialogTicketId) {
        options.setCategoryPanelState?.({
          suppressDetailsReset: true,
          reopenAfterClose: true,
        });
      }
      if (elements.categoriesModalEl) {
        options.showModalSafe?.(elements.categoriesModalEl, elements.categoriesModal);
        return;
      }
      elements.categoryTemplatesSection.classList.add('is-open');
      elements.categoryTemplatesSection.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    function initTemplatePanels() {
      const templateConfig = getTemplateConfig();
      if (elements.categoryTemplatesSection) {
        const templates = Array.isArray(templateConfig.categoryTemplates) ? templateConfig.categoryTemplates : [];
        const hasTemplates = templates.length > 0;
        elements.categoryTemplatesSection.classList.toggle('d-none', !hasTemplates);
        if (hasTemplates && elements.categoryTemplateSelect) {
          options.buildTemplateOptions?.(elements.categoryTemplateSelect, templates, 'Шаблон категорий');
          renderCategoryTemplate(templates[0]);
          syncCategorySelections();
        }
      }

      if (elements.questionTemplatesSection) {
        const templates = Array.isArray(templateConfig.questionTemplates) ? templateConfig.questionTemplates : [];
        const hasTemplates = templates.length > 0;
        elements.questionTemplatesSection.classList.toggle('d-none', !hasTemplates);
        if (hasTemplates && elements.questionTemplateSelect) {
          options.buildTemplateOptions?.(elements.questionTemplateSelect, templates, 'Шаблон вопросов');
          renderQuestionTemplate(templates[0]);
        }
      }

      if (elements.completionTemplatesSection) {
        const templates = Array.isArray(templateConfig.completionTemplates) ? templateConfig.completionTemplates : [];
        const hasTemplates = templates.length > 0;
        elements.completionTemplatesSection.classList.toggle('d-none', !hasTemplates);
        if (hasTemplates && elements.completionTemplateSelect) {
          options.buildTemplateOptions?.(elements.completionTemplateSelect, templates, 'Шаблон действий');
          renderCompletionTemplate(templates[0]);
        }
      }
    }

    function toggleCategorySelection(value) {
      const selectedCategories = getSelectedCategories();
      if (!value) return;
      if (selectedCategories.has(value)) {
        selectedCategories.delete(value);
      } else {
        selectedCategories.add(value);
      }
      syncCategorySelections();
      options.renderWorkspaceCategories?.();
      options.updateSummaryCategories?.(options.formatCategoriesLabel?.(Array.from(selectedCategories)) || '—');
      options.scheduleCategorySave?.();
    }

    function bindTemplateEvents() {
      if (state.templateEventsBound) return;
      state.templateEventsBound = true;

      if (elements.categoryTemplateSelect) {
        elements.categoryTemplateSelect.addEventListener('change', () => {
          const templates = Array.isArray(getTemplateConfig().categoryTemplates) ? getTemplateConfig().categoryTemplates : [];
          const selected = options.findTemplateByValue?.(templates, elements.categoryTemplateSelect.value);
          renderCategoryTemplate(selected);
          syncCategorySelections();
        });
      }

      if (elements.questionTemplateSelect) {
        elements.questionTemplateSelect.addEventListener('change', () => {
          const templates = Array.isArray(getTemplateConfig().questionTemplates) ? getTemplateConfig().questionTemplates : [];
          const selected = options.findTemplateByValue?.(templates, elements.questionTemplateSelect.value);
          renderQuestionTemplate(selected);
        });
      }

      if (elements.completionTemplateSelect) {
        elements.completionTemplateSelect.addEventListener('change', () => {
          const templates = Array.isArray(getTemplateConfig().completionTemplates) ? getTemplateConfig().completionTemplates : [];
          const selected = options.findTemplateByValue?.(templates, elements.completionTemplateSelect.value);
          renderCompletionTemplate(selected);
        });
      }

      if (elements.categoryTemplateList) {
        elements.categoryTemplateList.addEventListener('click', (event) => {
          const badge = event.target.closest('[data-category-value]');
          if (!badge) return;
          const value = badge.dataset.categoryValue || '';
          toggleCategorySelection(value);
        });
      }

      if (elements.workspaceCategoriesList) {
        elements.workspaceCategoriesList.addEventListener('click', (event) => {
          const dialogState = getDialogState();
          if (options.notifyActionBlocked?.('categories', 'Категории', {
            ticketId: dialogState.activeWorkspaceTicketId || dialogState.activeDialogTicketId,
            permissionKey: 'can_close',
          })) {
            return;
          }
          const badge = event.target.closest('[data-category-value]');
          if (!badge) return;
          const value = badge.dataset.categoryValue || '';
          toggleCategorySelection(value);
        });
      }

      if (elements.workspaceCategoriesClear) {
        elements.workspaceCategoriesClear.addEventListener('click', () => {
          const dialogState = getDialogState();
          if (options.notifyActionBlocked?.('categories', 'Категории', {
            ticketId: dialogState.activeWorkspaceTicketId || dialogState.activeDialogTicketId,
            permissionKey: 'can_close',
          })) {
            return;
          }
          options.setSelectedCategories?.(new Set());
          syncCategorySelections();
          options.renderWorkspaceCategories?.();
          options.updateSummaryCategories?.('—');
          options.scheduleCategorySave?.();
        });
      }

      if (elements.detailsCategoriesBtn) {
        elements.detailsCategoriesBtn.addEventListener('click', (event) => {
          event.preventDefault();
          openCategoryPanel();
        });
      }

      if (elements.categoriesModalEl) {
        elements.categoriesModalEl.addEventListener('hidden.bs.modal', () => {
          const panelState = typeof options.getCategoryPanelState === 'function'
            ? options.getCategoryPanelState()
            : { suppressDetailsReset: false, reopenAfterClose: false };
          const shouldReopenDetails = panelState.reopenAfterClose === true;
          options.setCategoryPanelState?.({
            suppressDetailsReset: false,
            reopenAfterClose: false,
          });
          const dialogState = getDialogState();
          if (shouldReopenDetails && elements.detailsModalEl && dialogState.activeDialogTicketId) {
            options.showModalSafe?.(elements.detailsModalEl, elements.detailsModal);
          }
        });
      }

      if (elements.detailsReplyEmojiTrigger && elements.emojiPanel) {
        elements.detailsReplyEmojiTrigger.addEventListener('click', () => {
          elements.emojiPanel.classList.toggle('is-open');
        });
      }

      if (elements.emojiList) {
        elements.emojiList.addEventListener('click', (event) => {
          const button = event.target.closest('[data-emoji-value]');
          if (!button) return;
          insertReplyText(button.dataset.emojiValue || '');
        });
      }

      if (elements.questionTemplateList) {
        elements.questionTemplateList.addEventListener('click', (event) => {
          const button = event.target.closest('[data-question-template-item]');
          if (!button) return;
          insertReplyText(button.dataset.questionValue || '');
        });
      }

      if (Array.isArray(elements.templateToggleButtons) && elements.templateToggleButtons.length) {
        elements.templateToggleButtons.forEach((button) => {
          button.addEventListener('click', () => {
            const target = button.dataset.templateToggle;
            const section = target === 'category'
              ? elements.categoryTemplatesSection
              : (target === 'macro' ? elements.macroTemplatesSection : elements.questionTemplatesSection);
            if (!section) return;
            section.classList.toggle('is-open');
          });
        });
      }

      if (elements.completionTemplatesSection) {
        const openCompletion = () => {
          elements.completionTemplatesSection.classList.add('is-open');
        };
        const scheduleClose = () => {
          if (state.completionHideTimer) {
            clearTimeout(state.completionHideTimer);
          }
          state.completionHideTimer = setTimeout(() => {
            elements.completionTemplatesSection.classList.remove('is-open');
          }, 2000);
        };
        elements.completionTemplatesSection.addEventListener('mouseenter', () => {
          if (state.completionHideTimer) clearTimeout(state.completionHideTimer);
          openCompletion();
        });
        elements.completionTemplatesSection.addEventListener('mouseleave', scheduleClose);
        if (elements.completionTemplatesToggle) {
          elements.completionTemplatesToggle.addEventListener('click', () => {
            elements.completionTemplatesSection.classList.toggle('is-open');
          });
        }
      }
    }

    return {
      renderCategoryTemplate,
      renderQuestionTemplate,
      renderCompletionTemplate,
      initTemplatePanels,
      syncCategorySelections,
      renderEmojiPanel,
      openCategoryPanel,
      insertReplyText,
      bindTemplateEvents,
    };
  }

  window.DialogsTemplatesRuntime = {
    createRuntime,
  };
})();

(function () {
  if (window.SettingsLegalEntitiesRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const state = {
      drafts: [],
      draftCounter: 0,
    };

    const elements = {
      legalEntitiesContainer: document.querySelector('[data-legal-entities-list]'),
      legalEntitiesEmptyPlaceholder: document.querySelector('[data-legal-entities-empty]'),
    };

    function getParameterData() {
      const data = typeof options.getParameterData === 'function' ? options.getParameterData() : null;
      return data && typeof data === 'object' ? data : {};
    }

    function getParameterStates() {
      const value = typeof options.getParameterStates === 'function' ? options.getParameterStates() : null;
      return Array.isArray(value) && value.length ? value : ['active'];
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

    function popup(message) {
      if (typeof options.showPopup === 'function') {
        options.showPopup(message);
        return;
      }
      console.log(message);
    }

    function syncParameterData(data) {
      if (typeof options.syncParameterData === 'function') {
        options.syncParameterData(data);
        return;
      }
      window.SettingsParametersShellRuntime?.syncParameterData?.(data);
    }

    function rerenderParameters() {
      if (typeof options.renderParameters === 'function') {
        options.renderParameters({ forceBodies: true });
        return;
      }
      window.SettingsParametersShellRuntime?.renderParameters?.({ forceBodies: true });
    }

    function rerenderItConnectionsTable() {
      if (typeof options.renderItConnectionsTable === 'function') {
        options.renderItConnectionsTable();
        return;
      }
      window.SettingsItConnectionsRuntime?.renderItConnectionsTable?.();
    }

    function deleteParameter(id, type, trigger) {
      if (typeof options.deleteParameter === 'function') {
        options.deleteParameter(id, type, trigger);
      }
    }

    function restoreParameter(id, type, trigger) {
      if (typeof options.restoreParameter === 'function') {
        options.restoreParameter(id, type, trigger);
      }
    }

    function buildLegalEntityStateSelect(rawState, disabled, { isDraft = false } = {}) {
      const parameterStates = getParameterStates();
      const effective = parameterStates.includes(rawState) ? rawState : parameterStates[0];
      const optionsHtml = parameterStates
        .map((item) => `<option value="${escapeHtml(item)}"${item === effective ? ' selected' : ''}>${escapeHtml(item)}</option>`)
        .join('');
      const disabledAttr = disabled ? ' disabled' : '';
      if (isDraft) {
        return `<select class="form-select form-select-sm" data-legal-entity-field="state"${disabledAttr}>${optionsHtml}</select>`;
      }
      return `
        <select
          class="form-select form-select-sm"
          data-legal-entity-field="state"
          data-param-field="state"
          data-param-type="legal_entity"
          data-param-current-state="${escapeHtml(effective)}"
          ${disabledAttr}
        >
          ${optionsHtml}
        </select>
      `;
    }

    function getRenderItems() {
      const existing = Array.isArray(getParameterData().legal_entity) ? getParameterData().legal_entity : [];
      const mappedExisting = existing.map((item) => {
        const extra = item && typeof item.extra === 'object' ? item.extra : {};
        const usage = Number.isFinite(Number(item && item.usage_count)) ? Number(item.usage_count) : 0;
        return {
          id: item.id,
          value: item && typeof item.value === 'string' ? item.value : '',
          state: item && typeof item.state === 'string' ? item.state : getParameterStates()[0],
          is_deleted: Boolean(item && item.is_deleted),
          usage_count: usage,
          inn: typeof extra.inn === 'string' ? extra.inn : '',
          manager_contacts: typeof extra.manager_contacts === 'string' ? extra.manager_contacts : '',
          isDraft: false,
        };
      });
      const mappedDrafts = state.drafts.map((draft) => ({
        draftId: draft.draftId,
        value: typeof draft.value === 'string' ? draft.value : '',
        state: typeof draft.state === 'string' ? draft.state : getParameterStates()[0],
        is_deleted: false,
        usage_count: 0,
        inn: typeof draft.inn === 'string' ? draft.inn : '',
        manager_contacts: typeof draft.manager_contacts === 'string' ? draft.manager_contacts : '',
        isDraft: true,
      }));
      return mappedExisting.concat(mappedDrafts);
    }

    function renderCard(item) {
      const isDraft = Boolean(item && item.isDraft);
      const nameValue = typeof item.value === 'string' ? item.value : '';
      const trimmedName = nameValue.trim();
      const innValue = typeof item.inn === 'string' ? item.inn : '';
      const contactsValue = typeof item.manager_contacts === 'string' ? item.manager_contacts : '';
      const stateValue = typeof item.state === 'string' ? item.state : getParameterStates()[0];
      const isDeleted = Boolean(item && item.is_deleted);
      const usageCount = Number.isFinite(Number(item && item.usage_count)) ? Number(item.usage_count) : 0;
      const usageLabel = `Используется: ${usageCount}`;
      const usageControl = !isDraft && trimmedName
        ? `<a href="/object_passports?legal_entity=${encodeURIComponent(trimmedName)}" class="btn btn-link btn-sm p-0${usageCount ? '' : ' text-muted'}">${usageLabel}</a>`
        : `<span class="text-muted small">${usageLabel}</span>`;
      const disabledAttr = isDeleted ? ' disabled' : '';
      const stateSelectHtml = buildLegalEntityStateSelect(stateValue, isDeleted, { isDraft });
      const contactsField = `<textarea class="form-control" rows="3" data-legal-entity-field="manager_contacts"${disabledAttr} placeholder="Телефон, почта, Telegram">${escapeHtml(contactsValue)}</textarea>`;
      const primaryLabel = isDraft ? 'Создать' : 'Сохранить';
      const primaryClass = isDraft ? 'btn-success' : 'btn-primary';
      const primaryDisabled = isDeleted ? ' disabled' : '';
      let secondaryButton = '';
      if (isDraft) {
        secondaryButton = '<button class="btn btn-outline-secondary btn-sm" type="button" data-legal-entity-action="cancel">Отмена</button>';
      } else if (isDeleted) {
        secondaryButton = '<button class="btn btn-outline-success btn-sm" type="button" data-legal-entity-action="restore">Восстановить</button>';
      } else {
        secondaryButton = '<button class="btn btn-outline-danger btn-sm" type="button" data-legal-entity-action="delete">Удалить</button>';
      }
      const deletedHint = isDeleted
        ? '<div class="alert alert-warning small mt-3 mb-0">Запись помечена как удалённая. Восстановите её, чтобы вносить изменения.</div>'
        : '';
      const idBadge = !isDraft ? `<span class="badge bg-secondary ms-auto">ID: ${item.id}</span>` : '';
      const cardClasses = ['card', 'shadow-sm', 'h-100', 'legal-entity-card'];
      if (isDeleted) {
        cardClasses.push('border-danger', 'opacity-75');
      }
      const datasetAttrs = isDraft
        ? `data-legal-entity-card="true" data-legal-entity-draft="true" data-legal-entity-draft-id="${escapeHtml(item.draftId)}"`
        : `data-legal-entity-card="true" data-legal-entity-draft="false" data-param-id="${item.id}" data-param-type="legal_entity"${isDeleted ? ' data-legal-entity-deleted="true"' : ''}`;
      return `
        <div class="col">
          <div class="${cardClasses.join(' ')}" ${datasetAttrs}>
            <div class="card-header d-flex justify-content-between align-items-start gap-2">
              <div>
                <div class="fw-semibold" data-legal-entity-title>${escapeHtml(trimmedName || 'Без названия')}</div>
                <div class="small text-muted" data-legal-entity-state-label>${escapeHtml(stateValue)}</div>
              </div>
              <div>${usageControl}</div>
            </div>
            <div class="card-body d-flex flex-column gap-3">
              <div>
                <label class="form-label">Название ЮЛ</label>
                <input type="text" class="form-control" data-legal-entity-field="value"${disabledAttr} value="${escapeHtml(nameValue)}" placeholder="Например, ООО «Партнёр»">
              </div>
              <div class="row g-3">
                <div class="col-sm-6">
                  <label class="form-label">ИНН</label>
                  <input type="text" class="form-control" data-legal-entity-field="inn"${disabledAttr} value="${escapeHtml(innValue)}" placeholder="12 цифр">
                </div>
                <div class="col-sm-6">
                  <label class="form-label">Статус</label>
                  ${stateSelectHtml}
                </div>
              </div>
              <div>
                <label class="form-label">Контакты менеджеров</label>
                ${contactsField}
                <div class="form-text">Укажите телефоны, почту или мессенджеры ответственных менеджеров.</div>
              </div>
              <div class="d-flex flex-wrap gap-2 align-items-center">
                <button class="btn ${primaryClass} btn-sm" type="button" data-legal-entity-action="save"${primaryDisabled}>${primaryLabel}</button>
                ${secondaryButton}
                ${idBadge}
              </div>
              ${deletedHint}
            </div>
          </div>
        </div>
      `;
    }

    function renderLegalEntities() {
      if (!elements.legalEntitiesContainer) {
        return;
      }
      const items = getRenderItems();
      if (!items.length) {
        elements.legalEntitiesContainer.innerHTML = '';
        if (elements.legalEntitiesEmptyPlaceholder) {
          elements.legalEntitiesEmptyPlaceholder.classList.remove('d-none');
        }
        return;
      }
      elements.legalEntitiesContainer.innerHTML = items.map((item) => renderCard(item)).join('');
      if (elements.legalEntitiesEmptyPlaceholder) {
        elements.legalEntitiesEmptyPlaceholder.classList.add('d-none');
      }
    }

    function resetLegalEntitiesState() {
      state.drafts = [];
      state.draftCounter = 0;
      renderLegalEntities();
    }

    function addLegalEntityDraft(initial = {}) {
      state.draftCounter += 1;
      const defaults = {
        draftId: `draft-${state.draftCounter}`,
        value: '',
        inn: '',
        manager_contacts: '',
        state: getParameterStates()[0],
      };
      state.drafts.push(Object.assign({}, defaults, initial || {}));
      renderLegalEntities();
    }

    function removeLegalEntityDraft(draftId) {
      const index = state.drafts.findIndex((item) => item.draftId === draftId);
      if (index === -1) {
        return;
      }
      state.drafts.splice(index, 1);
      renderLegalEntities();
    }

    function updateLegalEntityDraftValue(draftId, field, value) {
      const draft = state.drafts.find((item) => item.draftId === draftId);
      if (!draft) {
        return;
      }
      if (field === 'value') {
        draft.value = value;
      } else if (field === 'inn') {
        draft.inn = value;
      } else if (field === 'manager_contacts') {
        draft.manager_contacts = value;
      } else if (field === 'state') {
        draft.state = getParameterStates().includes(value) ? value : draft.state;
      }
    }

    function updateLegalEntityStateLabel(card, value) {
      const label = card instanceof HTMLElement ? card.querySelector('[data-legal-entity-state-label]') : null;
      if (label) {
        label.textContent = value || '—';
      }
    }

    async function handleLegalEntitySave(card, trigger) {
      const isDraft = card.dataset.legalEntityDraft === 'true';
      const valueInput = card.querySelector('[data-legal-entity-field="value"]');
      const innInput = card.querySelector('[data-legal-entity-field="inn"]');
      const contactsInput = card.querySelector('[data-legal-entity-field="manager_contacts"]');
      const stateSelect = card.querySelector('[data-legal-entity-field="state"]');
      const name = valueInput ? valueInput.value.trim() : '';
      if (!name) {
        popup('Название ЮЛ не может быть пустым');
        if (valueInput) {
          valueInput.focus();
        }
        return;
      }
      const payload = {
        value: name,
        inn: innInput ? innInput.value.trim() : '',
        manager_contacts: contactsInput ? contactsInput.value.trim() : '',
      };
      if (stateSelect && !stateSelect.disabled) {
        payload.state = stateSelect.value;
      }

      const originalText = trigger.textContent;
      trigger.disabled = true;
      trigger.textContent = '...';

      try {
        let response;
        if (isDraft) {
          payload.param_type = 'legal_entity';
          response = await fetch('/api/settings/parameters', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        } else {
          const id = Number.parseInt(card.dataset.paramId, 10);
          if (!Number.isFinite(id)) {
            throw new Error('Не удалось определить идентификатор записи');
          }
          response = await fetch(`/api/settings/parameters/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload),
          });
        }
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Ошибка сохранения записи');
        }
        if (isDraft) {
          const draftId = card.dataset.legalEntityDraftId;
          if (draftId) {
            const index = state.drafts.findIndex((item) => item.draftId === draftId);
            if (index !== -1) {
              state.drafts.splice(index, 1);
            }
          }
        }
        syncParameterData((data && data.data) || getParameterData());
        rerenderParameters();
        rerenderItConnectionsTable();
      } catch (error) {
        const message = error && error.message ? error.message : error;
        popup('❌ ' + message);
      } finally {
        trigger.disabled = false;
        trigger.textContent = originalText;
      }
    }

    function handleActionClick(button) {
      const card = button instanceof HTMLElement ? button.closest('[data-legal-entity-card]') : null;
      if (!card) {
        return false;
      }
      const action = button.dataset.legalEntityAction;
      if (action === 'save') {
        handleLegalEntitySave(card, button);
      } else if (action === 'delete') {
        const id = Number.parseInt(card.dataset.paramId, 10);
        if (Number.isFinite(id)) {
          deleteParameter(id, 'legal_entity', button);
        }
      } else if (action === 'restore') {
        const id = Number.parseInt(card.dataset.paramId, 10);
        if (Number.isFinite(id)) {
          restoreParameter(id, 'legal_entity', button);
        }
      } else if (action === 'cancel') {
        const draftId = card.dataset.legalEntityDraftId;
        if (draftId) {
          removeLegalEntityDraft(draftId);
        }
      }
      return true;
    }

    function handleContainerInput(event) {
      const field = event.target.dataset.legalEntityField;
      if (!field) {
        return;
      }
      const card = event.target.closest('[data-legal-entity-card]');
      if (!card) {
        return;
      }
      if (field === 'value') {
        const title = card.querySelector('[data-legal-entity-title]');
        if (title) {
          const text = event.target.value.trim();
          title.textContent = text || 'Без названия';
        }
      }
      if (card.dataset.legalEntityDraft === 'true') {
        const draftId = card.dataset.legalEntityDraftId;
        if (draftId) {
          updateLegalEntityDraftValue(draftId, field, event.target.value);
        }
      }
    }

    function handleContainerChange(event) {
      const field = event.target.dataset.legalEntityField;
      if (!field) {
        return;
      }
      const card = event.target.closest('[data-legal-entity-card]');
      if (!card) {
        return;
      }
      if (field === 'state') {
        updateLegalEntityStateLabel(card, event.target.value);
      }
      if (card.dataset.legalEntityDraft === 'true') {
        const draftId = card.dataset.legalEntityDraftId;
        if (draftId) {
          updateLegalEntityDraftValue(draftId, field, event.target.value);
        }
      }
    }

    return {
      renderLegalEntities,
      resetLegalEntitiesState,
      addLegalEntityDraft,
      handleActionClick,
      handleContainerInput,
      handleContainerChange,
    };
  }

  const api = {
    mount(options = {}) {
      if (window.__settingsLegalEntitiesRuntime) {
        return window.__settingsLegalEntitiesRuntime;
      }
      const runtime = createRuntime(options);
      window.renderLegalEntitiesSettingsModal = function renderLegalEntitiesSettingsModal() {
        runtime.renderLegalEntities();
      };
      window.resetLegalEntitiesSettingsModal = function resetLegalEntitiesSettingsModal() {
        runtime.resetLegalEntitiesState();
      };
      window.SettingsPageCallbackRegistry?.registerMany({
        renderLegalEntitiesSettingsModal: window.renderLegalEntitiesSettingsModal,
        resetLegalEntitiesSettingsModal: window.resetLegalEntitiesSettingsModal,
      });
      window.__settingsLegalEntitiesRuntime = runtime;
      return runtime;
    },
  };

  window.SettingsLegalEntitiesRuntime = Object.freeze({
    ...api,
    renderLegalEntities(...args) {
      return window.__settingsLegalEntitiesRuntime?.renderLegalEntities?.(...args);
    },
    resetLegalEntitiesState(...args) {
      return window.__settingsLegalEntitiesRuntime?.resetLegalEntitiesState?.(...args);
    },
    addLegalEntityDraft(...args) {
      return window.__settingsLegalEntitiesRuntime?.addLegalEntityDraft?.(...args);
    },
    handleActionClick(...args) {
      return window.__settingsLegalEntitiesRuntime?.handleActionClick?.(...args);
    },
    handleContainerInput(...args) {
      return window.__settingsLegalEntitiesRuntime?.handleContainerInput?.(...args);
    },
    handleContainerChange(...args) {
      return window.__settingsLegalEntitiesRuntime?.handleContainerChange?.(...args);
    },
  });
}());

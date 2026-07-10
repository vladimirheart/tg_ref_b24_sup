(function () {
  if (window.DialogsMacroRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      macroTemplatesCache: [],
      macroVariableCatalogInitialized: false,
      macroVariableCatalogTicketId: null,
      macroVariableDefaults: {},
      workspaceComposerMacroTemplates: [],
      activeWorkspaceMacroTemplate: null,
      activeMacroTemplate: null,
      activeMacroMeta: null,
      macroEventsBound: false,
    };

    function getActiveDialogState() {
      const activeState = typeof options.getActiveDialogState === 'function'
        ? options.getActiveDialogState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { ticketId: '', row: null, context: null };
    }

    function getActiveWorkspaceState() {
      const activeState = typeof options.getActiveWorkspaceState === 'function'
        ? options.getActiveWorkspaceState()
        : null;
      return activeState && typeof activeState === 'object'
        ? activeState
        : { ticketId: '', composerTicketId: '', composerText: null };
    }

    function getMacroTemplates() {
      const templates = typeof options.getMacroTemplates === 'function'
        ? options.getMacroTemplates()
        : [];
      return Array.isArray(templates) ? templates : [];
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function buildTemplateOptions(selectEl, templates, labelPrefix) {
      if (typeof options.buildTemplateOptions === 'function') {
        options.buildTemplateOptions(selectEl, templates, labelPrefix);
      }
    }

    function findTemplateByValue(templates, value) {
      if (typeof options.findTemplateByValue === 'function') {
        return options.findTemplateByValue(templates, value);
      }
      return Array.isArray(templates)
        ? templates.find((template, index) => template?.id === value || String(index) === value) || null
        : null;
    }

    function buildMacroGovernanceMeta(template) {
      if (!template || typeof template !== 'object') return '';
      const parts = [];
      const namespace = String(template?.namespace || '').trim();
      const owner = String(template?.owner || '').trim();
      const reviewedAt = String(template?.reviewed_at || template?.reviewed_at_utc || '').trim();
      if (namespace) parts.push(`namespace=${namespace}`);
      if (owner) parts.push(`owner=${owner}`);
      if (reviewedAt) parts.push(`review UTC ${reviewedAt.replace('T', ' ').replace('Z', ' UTC')}`);
      return parts.join(' · ');
    }

    function resolveMacroSearchText(template) {
      const name = String(template?.name || '').trim();
      const tags = Array.isArray(template?.tags) ? template.tags.join(' ') : '';
      const message = String(template?.message || template?.text || '').trim();
      return `${name} ${tags} ${message}`.toLowerCase();
    }

    function filterMacroTemplates(templates, query) {
      const normalizedQuery = String(query || '').trim().toLowerCase();
      if (!normalizedQuery) return Array.isArray(templates) ? templates : [];
      return (Array.isArray(templates) ? templates : [])
        .filter((template) => resolveMacroSearchText(template).includes(normalizedQuery));
    }

    function renderWorkspaceMacroPreview(template) {
      state.activeWorkspaceMacroTemplate = template || null;
      if (!elements.workspaceComposerMacroPreview) return;
      const text = resolveMacroText(template);
      elements.workspaceComposerMacroPreview.textContent = text || 'Выберите макрос для предпросмотра.';
      if (elements.workspaceComposerMacroMeta) {
        elements.workspaceComposerMacroMeta.textContent = buildMacroGovernanceMeta(template);
      }
      if (elements.workspaceComposerMacroApply) {
        elements.workspaceComposerMacroApply.disabled = !text;
      }
    }

    function findMacroTemplateByWorkspaceOptionValue(value, templatePool = state.workspaceComposerMacroTemplates) {
      const normalized = String(value || '').trim();
      if (!normalized) return null;
      if (normalized.startsWith('id:')) {
        const templateId = normalized.slice(3);
        return templatePool.find((template) => String(template?.id || '') === templateId) || null;
      }
      if (normalized.startsWith('idx:')) {
        const idx = Number.parseInt(normalized.slice(4), 10);
        if (!Number.isFinite(idx)) return null;
        return state.workspaceComposerMacroTemplates.find((template) => Number(template?.__macroIndex) === idx) || null;
      }
      return null;
    }

    function renderWorkspaceMacroOptions(templates) {
      if (!elements.workspaceComposerMacroSelect) return;
      const nextTemplates = Array.isArray(templates) ? templates : [];
      elements.workspaceComposerMacroSelect.innerHTML = '';
      nextTemplates.forEach((template, index) => {
        const option = document.createElement('option');
        option.value = template?.id
          ? `id:${template.id}`
          : `idx:${Number.isFinite(Number(template?.__macroIndex)) ? Number(template.__macroIndex) : index}`;
        option.textContent = template?.name || `Макрос ${index + 1}`;
        elements.workspaceComposerMacroSelect.appendChild(option);
      });
      const selected = nextTemplates.length > 0
        ? findMacroTemplateByWorkspaceOptionValue(elements.workspaceComposerMacroSelect.value, nextTemplates) || nextTemplates[0]
        : null;
      if (selected) {
        elements.workspaceComposerMacroSelect.value = selected?.id
          ? `id:${selected.id}`
          : `idx:${Number.isFinite(Number(selected?.__macroIndex)) ? Number(selected.__macroIndex) : nextTemplates.indexOf(selected)}`;
      }
      elements.workspaceComposerMacroSelect.disabled = nextTemplates.length === 0;
      renderWorkspaceMacroPreview(selected);
    }

    function renderMacroVariableCatalog(variables) {
      if (!elements.macroVariableCatalog) return;
      if (!Array.isArray(variables) || variables.length === 0) {
        elements.macroVariableCatalog.textContent = 'Доступны шаблоны вида {{ticket_id}} и {{client_name}}.';
        return;
      }
      elements.macroVariableCatalog.innerHTML = '';
      const title = document.createElement('span');
      title.className = 'text-muted';
      title.textContent = 'Переменные: ';
      elements.macroVariableCatalog.appendChild(title);
      variables.forEach((item, index) => {
        const code = document.createElement('code');
        const key = String(item?.key || '').trim();
        const label = String(item?.label || '').trim();
        const defaultValue = String(item?.default_value || '').trim();
        const source = String(item?.source || '').trim();
        code.textContent = `{{${key}}}`;
        const titleParts = [];
        if (label || key) titleParts.push(label || key);
        if (defaultValue) titleParts.push(`default: ${defaultValue}`);
        if (source) titleParts.push(`source: ${source}`);
        code.title = titleParts.join(' · ');
        elements.macroVariableCatalog.appendChild(code);
        if (index < variables.length - 1) {
          elements.macroVariableCatalog.appendChild(document.createTextNode(', '));
        }
      });
    }

    function resolveMacroVariableDefaults(variables) {
      const defaults = {};
      if (!Array.isArray(variables)) return defaults;
      variables.forEach((item) => {
        const key = String(item?.key || '').trim().toLowerCase();
        const defaultValue = String(item?.default_value || '').trim();
        if (!key || !defaultValue) return;
        defaults[key] = defaultValue;
      });
      return defaults;
    }

    async function initMacroVariableCatalog(ticketId, forceReload = false) {
      const normalizedTicketId = ticketId ? String(ticketId).trim() : '';
      if (!forceReload && state.macroVariableCatalogInitialized && state.macroVariableCatalogTicketId === normalizedTicketId) {
        return;
      }
      state.macroVariableCatalogInitialized = true;
      state.macroVariableCatalogTicketId = normalizedTicketId;
      const fallbackVariables = [
        { key: 'client_name', label: 'Имя клиента' },
        { key: 'ticket_id', label: 'ID обращения' },
        { key: 'operator_name', label: 'Имя оператора' },
        { key: 'channel_name', label: 'Канал обращения' },
        { key: 'business', label: 'Бизнес-направление' },
        { key: 'location', label: 'Локация клиента' },
        { key: 'dialog_status', label: '������ �������' },
        { key: 'created_at', label: 'Дата создания' },
        { key: 'current_date', label: 'Текущая дата' },
        { key: 'current_time', label: 'Текущее время' },
      ];
      try {
        const params = new URLSearchParams();
        if (normalizedTicketId) params.set('ticketId', normalizedTicketId);
        const endpoint = params.toString()
          ? `/api/dialogs/macro/variables?${params.toString()}`
          : '/api/dialogs/macro/variables';
        const response = await fetch(endpoint, { headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const payload = await response.json();
        const catalogVariables = Array.isArray(payload?.variables) ? payload.variables : fallbackVariables;
        state.macroVariableDefaults = resolveMacroVariableDefaults(catalogVariables);
        renderMacroVariableCatalog(catalogVariables);
      } catch (error) {
        console.warn('Failed to load macro variables catalog', error);
        state.macroVariableDefaults = {};
        renderMacroVariableCatalog(fallbackVariables);
      }
    }

    function resolveMacroVariables() {
      const now = new Date();
      const activeDialogState = getActiveDialogState();
      const activeContext = activeDialogState.context && typeof activeDialogState.context === 'object'
        ? activeDialogState.context
        : {};
      const variables = {
        client_name: activeContext.clientName || 'клиент',
        ticket_id: activeDialogState.ticketId || '—',
        operator_name: activeContext.operatorName || 'оператор',
        channel_name: activeContext.channelName || '—',
        business: activeContext.business || '—',
        location: activeContext.location || '—',
        dialog_status: activeContext.status || '—',
        created_at: activeContext.createdAt || '—',
        current_date: now.toLocaleDateString('ru-RU'),
        current_time: now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' }),
      };
      Object.entries(state.macroVariableDefaults).forEach(([key, value]) => {
        if (!Object.prototype.hasOwnProperty.call(variables, key) || !String(variables[key] || '').trim()) {
          variables[key] = value;
        }
      });
      return variables;
    }

    function resolveMacroText(template) {
      if (!template) return '';
      const text = String(template?.message || template?.text || '').trim();
      if (!text) return '';
      const variables = resolveMacroVariables();
      return text.replace(/\{\{\s*([a-z0-9_]+)(?:\s*\|\s*([^}]+))?\s*\}\}/gi, (match, key, fallback) => {
        const normalizedKey = String(key || '').toLowerCase();
        if (Object.prototype.hasOwnProperty.call(variables, normalizedKey)) {
          return String(variables[normalizedKey]);
        }
        const fallbackText = String(fallback || '').trim();
        return fallbackText || match;
      }).trim();
    }

    function resolveMacroWorkflow(template) {
      const workflow = template && typeof template?.workflow === 'object' && template.workflow !== null
        ? template.workflow
        : template || {};
      const snoozeMinutes = Number.parseInt(workflow.snooze_minutes, 10);
      return {
        assignToMe: workflow.assign_to_me === true || workflow.assign_to_me === 'true' || workflow.assign_to_me === 1 || workflow.assign_to_me === '1',
        closeTicket: workflow.close_ticket === true || workflow.close_ticket === 'true' || workflow.close_ticket === 1 || workflow.close_ticket === '1',
        snoozeMinutes: Number.isFinite(snoozeMinutes) && snoozeMinutes >= 1 ? Math.min(snoozeMinutes, 1440) : 0,
      };
    }

    function renderMacroTemplate(template) {
      if (!elements.macroTemplatePreview || !elements.macroTemplateEmpty || !elements.macroTemplateMeta) return;
      state.activeMacroTemplate = template || null;
      state.activeMacroMeta = template
        ? {
          id: String(template?.id || '').trim() || null,
          name: String(template?.name || '').trim() || null,
        }
        : null;
      const message = resolveMacroText(template);
      elements.macroTemplatePreview.textContent = message || 'Выберите макрос для предпросмотра.';
      elements.macroTemplateMeta.innerHTML = '';
      const tags = Array.isArray(template?.tags) ? template.tags.filter(Boolean) : [];
      tags.forEach((tag) => {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-light border';
        badge.textContent = String(tag);
        elements.macroTemplateMeta.appendChild(badge);
      });
      const workflow = resolveMacroWorkflow(template);
      if (workflow.assignToMe) {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-info-subtle border';
        badge.textContent = 'Workflow: назначить мне';
        elements.macroTemplateMeta.appendChild(badge);
      }
      if (workflow.snoozeMinutes > 0) {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-info-subtle border';
        badge.textContent = `Workflow: snooze ${workflow.snoozeMinutes}м`;
        elements.macroTemplateMeta.appendChild(badge);
      }
      if (workflow.closeTicket) {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-warning-subtle border';
        badge.textContent = 'Workflow: закрыть тикет';
        elements.macroTemplateMeta.appendChild(badge);
      }
      const namespace = String(template?.namespace || '').trim();
      if (namespace) {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-light border';
        badge.textContent = `Namespace: ${namespace}`;
        elements.macroTemplateMeta.appendChild(badge);
      }
      const owner = String(template?.owner || '').trim();
      if (owner) {
        const badge = document.createElement('span');
        badge.className = 'badge text-bg-light border';
        badge.textContent = `Owner: ${owner}`;
        elements.macroTemplateMeta.appendChild(badge);
      }
      const hasActions = workflow.assignToMe || workflow.snoozeMinutes > 0 || workflow.closeTicket;
      const hasMessage = Boolean(message);
      elements.macroTemplateEmpty.classList.toggle('d-none', hasMessage || hasActions);
      if (elements.macroTemplateApply) {
        elements.macroTemplateApply.disabled = !(hasMessage || hasActions);
      }
      if (state.activeMacroMeta && (hasMessage || hasActions)) {
        options.emitWorkspaceTelemetry?.('macro_preview', {
          ticketId: getActiveDialogState().ticketId,
          templateId: state.activeMacroMeta.id,
          templateName: state.activeMacroMeta.name,
        });
      }
    }

    function renderMacroTemplateOptions(templates) {
      if (!elements.macroTemplateSelect) return;
      const nextTemplates = Array.isArray(templates) ? templates : [];
      buildTemplateOptions(elements.macroTemplateSelect, nextTemplates, 'Макрос');
      const hasOptions = nextTemplates.length > 0;
      elements.macroTemplateSelect.disabled = !hasOptions;
      const selected = hasOptions
        ? findTemplateByValue(nextTemplates, elements.macroTemplateSelect.value) || nextTemplates[0]
        : null;
      if (selected && elements.macroTemplateSelect.value !== (selected.id || '')) {
        elements.macroTemplateSelect.value = selected.id || String(nextTemplates.indexOf(selected));
      }
      renderMacroTemplate(selected);
    }

    function initMacroTemplates() {
      const templates = getMacroTemplates();
      if (elements.macroTemplatesSection) {
        const hasTemplates = templates.length > 0;
        state.macroTemplatesCache = templates;
        elements.macroTemplatesSection.classList.toggle('d-none', !hasTemplates);
        if (hasTemplates) {
          initMacroVariableCatalog(getActiveDialogState().ticketId, true);
        }
        if (hasTemplates && elements.macroTemplateSelect) {
          renderMacroTemplateOptions(templates);
        }
      }

      if (elements.workspaceComposerMacroSection) {
        state.workspaceComposerMacroTemplates = templates.map((template, index) => ({
          ...template,
          __macroIndex: index,
        }));
        const hasTemplates = state.workspaceComposerMacroTemplates.length > 0;
        elements.workspaceComposerMacroSection.classList.toggle('d-none', !hasTemplates);
        if (hasTemplates) {
          renderWorkspaceMacroOptions(state.workspaceComposerMacroTemplates);
        }
      }
    }

    async function executeMacroWorkflow(template, applyOptions = {}) {
      const ticketId = String(applyOptions.ticketId || '').trim();
      const row = applyOptions.row || null;
      if (!ticketId) return [];
      const workflow = resolveMacroWorkflow(template);
      const steps = [];

      if (workflow.assignToMe) {
        await options.takeDialog?.(ticketId, row, null);
        steps.push('назначить мне');
      }
      if (workflow.snoozeMinutes > 0) {
        await options.snoozeDialog?.(ticketId, workflow.snoozeMinutes, null);
        options.setSnooze?.(ticketId, workflow.snoozeMinutes);
        if (row) options.updateRowQuickActions?.(row);
        options.applyFilters?.();
        steps.push(`отложить на ${workflow.snoozeMinutes} мин`);
      }
      if (workflow.closeTicket && row) {
        await options.closeDialogQuick?.(ticketId, row, null);
        steps.push('закрыть тикет');
      }

      return steps;
    }

    async function applyMacroTemplate() {
      if (!state.activeMacroTemplate) return;
      const activeDialogState = getActiveDialogState();
      const message = resolveMacroText(state.activeMacroTemplate);
      const appliedSteps = await executeMacroWorkflow(state.activeMacroTemplate, {
        ticketId: activeDialogState.ticketId,
        row: activeDialogState.row,
      });
      if (message) {
        options.insertReplyText?.(message);
      }
      if (message || appliedSteps.length) {
        const workflowSuffix = appliedSteps.length ? `; действия: ${appliedSteps.join(', ')}` : '';
        notify(`Макрос применён${workflowSuffix}.`, 'success');
      }
      options.emitWorkspaceTelemetry?.('macro_apply', {
        ticketId: activeDialogState.ticketId,
        templateId: state.activeMacroMeta?.id || null,
        templateName: state.activeMacroMeta?.name || null,
        workflowActions: appliedSteps.length ? appliedSteps.join('|') : null,
      });
    }

    async function applyWorkspaceMacroTemplate() {
      const activeWorkspaceState = getActiveWorkspaceState();
      if (!activeWorkspaceState.composerText || !state.activeWorkspaceMacroTemplate) return;
      const message = resolveMacroText(state.activeWorkspaceMacroTemplate);
      const rows = typeof options.rowsList === 'function' ? options.rowsList() : [];
      const row = activeWorkspaceState.composerTicketId
        ? (rows.find((candidate) => String(candidate.dataset.ticketId || '') === String(activeWorkspaceState.composerTicketId)) || null)
        : null;
      const appliedSteps = await executeMacroWorkflow(state.activeWorkspaceMacroTemplate, {
        ticketId: activeWorkspaceState.composerTicketId,
        row,
      });
      if (message) {
        const existing = activeWorkspaceState.composerText.value.trim();
        activeWorkspaceState.composerText.value = existing ? `${existing}\n${message}` : message;
        activeWorkspaceState.composerText.focus();
        options.saveWorkspaceDraft?.(
          activeWorkspaceState.composerTicketId,
          activeWorkspaceState.composerText.value,
          { silent: true }
        );
      }
      options.emitWorkspaceTelemetry?.('macro_apply', {
        ticketId: activeWorkspaceState.composerTicketId,
        templateId: String(state.activeWorkspaceMacroTemplate?.id || '').trim() || null,
        templateName: String(state.activeWorkspaceMacroTemplate?.name || '').trim() || null,
        source: 'workspace_composer',
        workflowActions: appliedSteps.length ? appliedSteps.join('|') : null,
      });
      if (message || appliedSteps.length) {
        const workflowSuffix = appliedSteps.length ? `; действия: ${appliedSteps.join(', ')}` : '';
        notify(`Макрос workspace применён${workflowSuffix}.`, 'success');
      }
    }

    function bindMacroTemplateEvents() {
      if (state.macroEventsBound) return;
      state.macroEventsBound = true;

      if (elements.macroTemplateSelect) {
        elements.macroTemplateSelect.addEventListener('change', () => {
          const selected = findTemplateByValue(state.macroTemplatesCache, elements.macroTemplateSelect.value);
          renderMacroTemplate(selected);
        });
      }

      if (elements.macroTemplateSearch) {
        elements.macroTemplateSearch.addEventListener('input', () => {
          const filteredTemplates = filterMacroTemplates(state.macroTemplatesCache, elements.macroTemplateSearch.value);
          renderMacroTemplateOptions(filteredTemplates);
        });
      }

      if (elements.macroTemplateApply) {
        elements.macroTemplateApply.addEventListener('click', async () => {
          try {
            await applyMacroTemplate();
          } catch (error) {
            notify(error?.message || 'Не удалось применить макрос', 'error');
          }
        });
      }

      if (elements.workspaceComposerMacroSearch) {
        elements.workspaceComposerMacroSearch.addEventListener('input', () => {
          const filteredTemplates = filterMacroTemplates(
            state.workspaceComposerMacroTemplates,
            elements.workspaceComposerMacroSearch.value
          );
          renderWorkspaceMacroOptions(filteredTemplates);
        });
      }

      if (elements.workspaceComposerMacroSelect) {
        elements.workspaceComposerMacroSelect.addEventListener('change', () => {
          const selected = findMacroTemplateByWorkspaceOptionValue(elements.workspaceComposerMacroSelect.value);
          renderWorkspaceMacroPreview(selected);
        });
      }

      if (elements.workspaceComposerMacroApply) {
        elements.workspaceComposerMacroApply.addEventListener('click', async () => {
          try {
            await applyWorkspaceMacroTemplate();
          } catch (error) {
            notify(error?.message || 'Не удалось применить macro workflow', 'error');
          }
        });
      }
    }

    function getWorkspaceComposerMeta() {
      return {
        hasActiveMacroTemplate: Boolean(state.activeWorkspaceMacroTemplate),
        macroTemplatesLength: state.workspaceComposerMacroTemplates.length,
      };
    }

    return {
      buildMacroGovernanceMeta,
      renderWorkspaceMacroPreview,
      renderWorkspaceMacroOptions,
      findMacroTemplateByWorkspaceOptionValue,
      applyWorkspaceMacroTemplate,
      resolveMacroSearchText,
      filterMacroTemplates,
      renderMacroTemplateOptions,
      renderMacroVariableCatalog,
      resolveMacroVariableDefaults,
      initMacroVariableCatalog,
      resolveMacroVariables,
      resolveMacroText,
      renderMacroTemplate,
      initMacroTemplates,
      resolveMacroWorkflow,
      executeMacroWorkflow,
      applyMacroTemplate,
      bindMacroTemplateEvents,
      getWorkspaceComposerMeta,
    };
  }

  window.DialogsMacroRuntime = {
    createRuntime,
  };
})();

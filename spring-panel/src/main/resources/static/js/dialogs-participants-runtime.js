(function () {
  if (window.DialogsParticipantsRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const elements = options.elements || {};
    const state = {
      assignableOperators: null,
      participants: [],
      scoped: null,
      generation: 0,
    };

    function exactLegacyTicketId(value) {
      const raw = value == null ? '' : String(value);
      return raw.trim() ? raw : '';
    }

    function escapeHtml(value) {
      return typeof options.escapeHtml === 'function'
        ? options.escapeHtml(value)
        : String(value ?? '');
    }

    function formatTimestamp(value, formatOptions = {}) {
      return typeof options.formatTimestamp === 'function'
        ? options.formatTimestamp(value, formatOptions)
        : String(value || formatOptions.fallback || '—');
    }

    function normalizeIdentity(value) {
      return typeof options.normalizeIdentity === 'function'
        ? options.normalizeIdentity(value)
        : String(value || '').trim().toLowerCase();
    }

    function notify(message, type = 'info') {
      if (typeof options.showNotification === 'function') {
        options.showNotification(message, type);
      }
    }

    function getActiveDialogTicketId() {
      return exactLegacyTicketId(options.getActiveDialogTicketId?.());
    }

    function getActiveDialogResponsibleRaw() {
      return String(options.getActiveDialogResponsibleRaw?.() || '').trim();
    }

    function getActiveDialogContext() {
      const context = options.getActiveDialogContext?.();
      return context && typeof context === 'object' ? context : {};
    }

    function getActiveDialogResponsibleAvatarUrl() {
      return String(options.getActiveDialogResponsibleAvatarUrl?.() || '').trim();
    }

    function getWorkspaceCandidates(key) {
      const ticketId = getActiveDialogTicketId();
      return options.getWorkspaceWorkflowCollection?.(key, ticketId);
    }

    function businessReference() {
      const context = getActiveDialogContext(), row = options.getActiveDialogRow?.()?.dataset || {};
      if (String(context.scopeKind || row.scopeKind || '').toUpperCase() !== 'BUSINESS') return null;
      const ref = { ticketId: getActiveDialogTicketId(), ticketPublicId: String(context.ticketPublicId || row.ticketPublicId || ''),
        businessId: String(context.businessId || row.businessId || '') };
      return { ...ref, key: JSON.stringify(ref) };
    }

    function currentScoped() {
      return state.scoped?.key === businessReference()?.key ? state.scoped : null;
    }

    async function scopedRequest(url, init = {}) {
      const headers = { Accept: 'application/json', ...(init.headers || {}) };
      if (init.method) {
        headers['Content-Type'] = 'application/json';
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        if (token) headers[document.querySelector('meta[name="_csrf_header"]')?.content || 'X-XSRF-TOKEN'] = token;
      }
      const response = await fetch(url, { ...init, headers, credentials: 'same-origin', cache: 'no-store', redirect: 'error' });
      if (!response.ok) {
        const messages = { 403: 'Доступ к участникам отозван.', 404: 'Обращение недоступно. Обновите список диалогов.',
          409: 'Список или права изменились. Обновите участников.', 503: 'Список участников временно недоступен.' };
        throw new Error(messages[response.status] || `Не удалось загрузить или изменить участников (${response.status}).`);
      }
      return response.status === 204 ? null : response.json();
    }

    async function loadBusinessParticipants(ref) {
      const serial = ++state.generation;
      state.scoped = null;
      renderDialogParticipantsLoadingState(); syncParticipantsSelectOptions();
      const base = `/api/business-scope/tickets/${encodeURIComponent(ref.ticketPublicId)}/participants`;
      let roster, management;
      try { [roster, management] = await Promise.all([scopedRequest(base), scopedRequest(base + '/management')]); }
      catch (error) { if (serial === state.generation && businessReference()?.key === ref.key) throw error; return []; }
      if (serial !== state.generation || businessReference()?.key !== ref.key) return [];
      const owner = roster?.owner;
      if (!owner || ['userId', 'ticketId', 'ticketPublicId', 'businessId'].some(key => typeof owner[key] !== 'string') || owner.ticketId !== ref.ticketId || owner.ticketPublicId !== ref.ticketPublicId || owner.businessId !== ref.businessId
          || !/^(0|-?[1-9][0-9]*)$/.test(owner.userId) || !/^(0|[1-9][0-9]*)$/.test(roster.revision)
          || ['userId', 'ticketId', 'ticketPublicId', 'businessId'].some(key => owner[key] !== management?.owner?.[key])
          || typeof roster.revision !== 'string' || roster.revision !== management.revision
          || !Array.isArray(roster.members) || !Array.isArray(management.candidates)
          || roster.members.some(person => typeof person.id !== 'string' || !/^[1-9][0-9]*$/.test(person.id))
          || management.candidates.some(person => typeof person.userId !== 'string' || !/^[1-9][0-9]*$/.test(person.userId)))
        throw new Error('Данные обращения или список участников изменились. Обновите участников.');
      state.scoped = { key: ref.key, base, owner, revision: roster.revision, canManage: management.canManage === true,
        candidates: management.candidates };
      setParticipantsState(roster.members.map(member => ({ userId: String(member.id), username: member.username || '',
        displayName: member.username || member.snapshot || 'Сотрудник', eligible: member.eligible })));
      renderDialogParticipantsState(); syncParticipantsSelectOptions();
      return getParticipantsState();
    }

    async function changeBusinessParticipant(method, memberId) {
      const scoped = currentScoped();
      if (!scoped?.canManage || !/^[1-9][0-9]*$/.test(memberId)) return;
      await scopedRequest(scoped.base, { method, body: JSON.stringify({ ...scoped.owner, revision: scoped.revision, memberId }) });
      if (businessReference()?.key !== scoped.key) return;
      await loadBusinessParticipants(businessReference());
      notify(method === 'POST' ? 'Пользователь подключён к диалогу' : 'Участник убран из диалога', 'success');
    }

    function getParticipantsState() {
      return Array.isArray(state.participants) ? state.participants : [];
    }

    function setParticipantsState(participants) {
      state.participants = Array.isArray(participants) ? participants : [];
      return state.participants;
    }

    function getAssignableOperators() {
      return Array.isArray(state.assignableOperators) ? state.assignableOperators : [];
    }

    function setAssignableOperators(operators) {
      state.assignableOperators = Array.isArray(operators) ? operators : [];
      return state.assignableOperators;
    }

    function buildOperatorOptionLabel(operator) {
      const displayName = String(operator?.displayName || operator?.display_name || '').trim();
      const username = String(operator?.username || '').trim();
      const department = String(operator?.department || '').trim();
      const role = String(operator?.role || '').trim();
      const head = displayName && username && normalizeIdentity(displayName) !== normalizeIdentity(username)
        ? `${displayName} (${username})`
        : (displayName || username || '—');
      const tail = [department, role].filter(Boolean).join(' · ');
      return tail ? `${head} · ${tail}` : head;
    }

    function rememberEmployee(person) {
      window.EmployeeProfiles?.remember({ username: person?.username,
        fullName: person?.displayName || person?.display_name,
        photo: person?.avatarUrl || person?.avatar_url, department: person?.department });
    }

    function buildParticipantAvatarMarkup(displayName, avatarUrl, alt) {
      const spec = options.buildResponsibleAvatarSpec?.(displayName, avatarUrl);
      if (spec?.avatarUrl) {
        return `<span class="dialog-responsible-avatar has-image"><img src="${escapeHtml(spec.avatarUrl)}" alt="${escapeHtml(alt || 'Аватар участника')}"></span>`;
      }
      return `<span class="dialog-responsible-avatar"><span>${escapeHtml(spec?.initial || displayName.substring(0, 1).toUpperCase())}</span></span>`;
    }

    function buildParticipantProfileHref(participant) {
      const username = String(participant?.username || '').trim();
      const displayName = String(participant?.displayName || participant?.display_name || '').trim();
      const profileId = username;
      if (!profileId) {
        return '';
      }
      return `/users/${encodeURIComponent(profileId)}`;
    }

    function renderParticipantCard(participant, runtimeOptions = {}) {
      rememberEmployee(participant);
      const username = String(participant?.username || '').trim();
      const displayName = String(participant?.displayName || participant?.display_name || '').trim() || username || '—';
      const avatarUrl = String(participant?.avatarUrl || participant?.avatar_url || '').trim();
      const department = String(participant?.department || '').trim();
      const role = String(participant?.role || '').trim();
      const metaParts = [department, role].filter(Boolean);
      const avatarMarkup = buildParticipantAvatarMarkup(displayName, avatarUrl, 'Аватар участника');
      const profileHref = buildParticipantProfileHref(participant);
      const removeButton = runtimeOptions.removable && (username || participant.userId)
        ? `<button type="button" class="btn btn-sm btn-outline-danger" ${currentScoped() ? `data-remove-participant-id="${escapeHtml(participant.userId)}"` : `data-remove-participant="${escapeHtml(username)}"`}>${escapeHtml(runtimeOptions.removeLabel || 'Убрать')}</button>`
        : '';
      return `
        <div class="dialog-details-participant-card">
          <div class="dialog-details-participant-main">
            ${participant.userId && window.EmployeeProfiles
              ? `<div class="dialog-details-participant-copy">${window.EmployeeProfiles.link({ userId: participant.userId, username, fullName: displayName })}</div>`
              : profileHref
              ? `<div class="dialog-details-participant-copy">${window.EmployeeProfiles?.link(username) || `<a class="dialog-details-participant-link" href="${escapeHtml(profileHref)}">${avatarMarkup}${escapeHtml(displayName)}</a>`}<div class="dialog-details-participant-meta">${metaParts.map((item) => `<span>${escapeHtml(item)}</span>`).join('')}</div></div>`
              : `${avatarMarkup}<div class="dialog-details-participant-copy"><div class="dialog-details-participant-name">${escapeHtml(displayName)}</div><div class="dialog-details-participant-meta">${metaParts.map((item) => `<span>${escapeHtml(item)}</span>`).join('')}</div></div>`}
          </div>
          ${removeButton}
        </div>
      `;
    }

    function renderParticipantInlineChip(participant) {
      rememberEmployee(participant);
      const username = String(participant?.username || '').trim();
      const displayName = String(participant?.displayName || participant?.display_name || '').trim() || username || '—';
      const avatarUrl = String(participant?.avatarUrl || participant?.avatar_url || '').trim();
      const avatarMarkup = buildParticipantAvatarMarkup(displayName, avatarUrl, 'Аватар участника');
      const titleParts = [displayName];
      if (username && username !== displayName) {
        titleParts.push(`@${username}`);
      }
      const profileHref = buildParticipantProfileHref(participant);
      const safeTitle = escapeHtml(titleParts.join(' · '));
      if (participant.userId && window.EmployeeProfiles) return window.EmployeeProfiles.link({ userId: participant.userId, username, fullName: displayName });
      if (username && window.EmployeeProfiles) return window.EmployeeProfiles.link(username);
      return `
        ${profileHref
          ? `<a class="dialog-details-participant-pill" href="${escapeHtml(profileHref)}" target="_blank" rel="noopener" title="${safeTitle}">${avatarMarkup}<span class="dialog-details-participant-pill-name">${escapeHtml(displayName)}</span></a>`
          : `<span class="dialog-details-participant-pill" title="${safeTitle}">${avatarMarkup}<span class="dialog-details-participant-pill-name">${escapeHtml(displayName)}</span></span>`}
      `;
    }

    function getParticipantStateEmptyText() {
      return 'Дополнительные участники не подключены.';
    }

    function renderDialogParticipantsState() {
      const participants = getParticipantsState();
      if (elements.detailsParticipantsSection) {
        elements.detailsParticipantsSection.classList.toggle('d-none', participants.length === 0);
      }
      if (elements.detailsParticipantsState) {
        elements.detailsParticipantsState.textContent = participants.length
          ? `Подключено: ${participants.length}`
          : getParticipantStateEmptyText();
      }
      if (elements.detailsParticipantsList) {
        elements.detailsParticipantsList.innerHTML = participants.map((participant) => renderParticipantInlineChip(participant)).join('');
        elements.detailsParticipantsList.classList.toggle('d-none', participants.length === 0);
      }
      if (elements.participantsCurrentState) {
        elements.participantsCurrentState.textContent = participants.length
          ? `Подключено: ${participants.length}`
          : getParticipantStateEmptyText();
      }
      if (elements.participantsCurrentList) {
        elements.participantsCurrentList.innerHTML = participants.map((participant) => renderParticipantCard(participant, {
          removable: businessReference() ? currentScoped()?.canManage : options.isWorkspaceActionEnabled?.(
            'participants_remove',
            options.canRunAction?.('can_assign'),
            getActiveDialogTicketId()
          ),
          removeLabel: 'Убрать',
        })).join('');
        elements.participantsCurrentList.classList.toggle('d-none', participants.length === 0);
      }
      if (elements.detailsParticipantsList) window.EmployeeProfiles?.refresh(elements.detailsParticipantsList);
      if (elements.participantsCurrentList) window.EmployeeProfiles?.refresh(elements.participantsCurrentList);
    }

    function renderDialogParticipantsLoadingState(message) {
      const text = String(message || 'Загрузка участников...').trim();
      if (elements.detailsParticipantsSection) elements.detailsParticipantsSection.classList.add('d-none');
      if (elements.detailsParticipantsState) elements.detailsParticipantsState.textContent = text;
      if (elements.detailsParticipantsList) {
        elements.detailsParticipantsList.innerHTML = '';
      }
      if (elements.participantsCurrentState) elements.participantsCurrentState.textContent = text;
      if (elements.participantsCurrentList) {
        elements.participantsCurrentList.innerHTML = '';
        elements.participantsCurrentList.classList.add('d-none');
      }
    }

    function syncParticipantsSelectOptions() {
      if (!elements.participantsSelect) return;
      const ref = businessReference(), scoped = currentScoped();
      if (ref) {
        const existing = new Set(getParticipantsState().map(person => person.userId));
        elements.participantsSelect.replaceChildren();
        const placeholder = document.createElement('option'); placeholder.value = ''; placeholder.textContent = 'Выберите сотрудника';
        elements.participantsSelect.append(placeholder);
        const candidates = (scoped?.candidates || []).filter(person => !existing.has(person.userId));
        for (const person of candidates) {
          const option = document.createElement('option'); option.value = person.userId; option.textContent = person.username;
          option.dataset.employeeName = person.username; elements.participantsSelect.append(option);
        }
        elements.participantsSelect.disabled = !scoped?.canManage || !candidates.length;
        if (elements.participantsAddBtn) elements.participantsAddBtn.disabled = elements.participantsSelect.disabled;
        if (elements.participantsSelectHint) elements.participantsSelectHint.textContent = !scoped ? 'Загрузка участников…'
          : !scoped.canManage ? 'Доступен просмотр участников. Изменение требует права обработки этого бизнеса и открытого обращения.'
            : !candidates.length ? 'Доступных сотрудников для подключения больше нет.' : 'Показаны сотрудники с доступом к этому бизнесу.';
        window.EmployeePicker?.refresh();
        return;
      }
      const workspaceCandidates = getWorkspaceCandidates('participant_candidates');
      const operators = Array.isArray(workspaceCandidates) ? workspaceCandidates : getAssignableOperators();
      const participantIds = new Set(getParticipantsState().map((item) => normalizeIdentity(item?.username)));
      const currentResponsible = normalizeIdentity(getActiveDialogResponsibleRaw());
      const availableOperators = operators.filter((item) => {
        const username = normalizeIdentity(item?.username);
        if (!username) return false;
        if (username === currentResponsible) return false;
        return !participantIds.has(username);
      });
      elements.participantsSelect.innerHTML = '';
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = availableOperators.length ? 'Выберите пользователя…' : 'Нет доступных пользователей';
      elements.participantsSelect.appendChild(placeholder);
      availableOperators.forEach((operator) => {
        rememberEmployee(operator);
        const option = document.createElement('option');
        option.value = operator.username || '';
        option.textContent = buildOperatorOptionLabel(operator);
        option.dataset.employeeName = operator.displayName || operator.display_name || operator.username || '';
        option.dataset.employeePhoto = operator.avatarUrl || operator.avatar_url || '';
        elements.participantsSelect.appendChild(option);
      });
      const canAddParticipants = options.isWorkspaceActionEnabled?.(
        'participants_add',
        availableOperators.length > 0 && options.canRunAction?.('can_assign'),
        getActiveDialogTicketId()
      );
      elements.participantsSelect.disabled = availableOperators.length === 0 || !canAddParticipants;
      if (elements.participantsAddBtn) {
        elements.participantsAddBtn.disabled = availableOperators.length === 0 || !canAddParticipants;
      }
      if (elements.participantsSelectHint) {
        const guard = options.getWorkspaceActionGuard?.('participants_add', getActiveDialogTicketId());
        if (!canAddParticipants && guard?.disabled_reason === 'closed_dialog') {
          elements.participantsSelectHint.textContent = 'К закрытому диалогу нельзя добавлять новых участников.';
        } else if (!canAddParticipants && guard?.disabled_reason === 'no_participant_candidates') {
          elements.participantsSelectHint.textContent = 'Доступных пользователей для подключения больше нет.';
        } else {
          elements.participantsSelectHint.textContent = availableOperators.length
            ? 'Ответственный не отображается в списке: он уже подключён к диалогу автоматически.'
            : 'Доступных пользователей для подключения больше нет.';
        }
      }
    }

    function syncReassignSelectOptions() {
      if (!elements.reassignSelect) return;
      const workspaceCandidates = getWorkspaceCandidates('reassign_candidates');
      const operators = Array.isArray(workspaceCandidates) ? workspaceCandidates : getAssignableOperators();
      const currentResponsible = normalizeIdentity(getActiveDialogResponsibleRaw());
      const availableOperators = operators.filter((item) => {
        const username = normalizeIdentity(item?.username);
        return username && username !== currentResponsible;
      });
      elements.reassignSelect.innerHTML = '';
      const placeholder = document.createElement('option');
      placeholder.value = '';
      placeholder.textContent = availableOperators.length ? 'Выберите пользователя…' : 'Некому передать диалог';
      elements.reassignSelect.appendChild(placeholder);
      availableOperators.forEach((operator) => {
        rememberEmployee(operator);
        const option = document.createElement('option');
        option.value = operator.username || '';
        option.textContent = buildOperatorOptionLabel(operator);
        option.dataset.employeeName = operator.displayName || operator.display_name || operator.username || '';
        option.dataset.employeePhoto = operator.avatarUrl || operator.avatar_url || '';
        elements.reassignSelect.appendChild(option);
      });
      const canReassign = options.isWorkspaceActionEnabled?.(
        'reassign',
        availableOperators.length > 0 && options.canRunAction?.('can_assign'),
        getActiveDialogTicketId()
      );
      elements.reassignSelect.disabled = availableOperators.length === 0 || !canReassign;
      if (elements.reassignSubmit) {
        elements.reassignSubmit.disabled = availableOperators.length === 0 || !canReassign;
      }
      if (elements.reassignHint) {
        const guard = options.getWorkspaceActionGuard?.('reassign', getActiveDialogTicketId());
        if (!canReassign && guard?.disabled_reason === 'closed_dialog') {
          elements.reassignHint.textContent = 'Переадресовать можно только открытый диалог.';
        } else if (!canReassign && guard?.disabled_reason === 'no_reassign_candidates') {
          elements.reassignHint.textContent = 'Подходящих пользователей для переадресации не найдено.';
        } else {
          elements.reassignHint.textContent = availableOperators.length
            ? 'Если пользователь уже был участником диалога, после передачи он станет только ответственным.'
            : 'Подходящих пользователей для переадресации не найдено.';
        }
      }
    }

    async function ensureAssignableOperatorsLoaded(force = false) {
      if (!force && Array.isArray(state.assignableOperators)) {
        return state.assignableOperators;
      }
      const response = await fetch('/api/dialogs/operators', {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await response.json();
      if (!response.ok || !payload?.success) {
        throw new Error(payload?.error || `Ошибка ${response.status}`);
      }
      setAssignableOperators(payload.operators);
      syncParticipantsSelectOptions();
      syncReassignSelectOptions();
      return getAssignableOperators();
    }

    async function loadDialogParticipants() {
      syncDialogAssignControls();
      const ref = businessReference();
      if (ref) return loadBusinessParticipants(ref);
      state.scoped = null;
      const serial = ++state.generation;
      const ticketId = getActiveDialogTicketId();
      if (!ticketId) {
        setParticipantsState([]);
        renderDialogParticipantsState();
        return [];
      }
      renderDialogParticipantsLoadingState('Загрузка участников...');
      const response = await fetch(`/api/dialog-ticket/participants?ticketId=${encodeURIComponent(ticketId)}`, {
        credentials: 'same-origin',
        cache: 'no-store',
      });
      const payload = await response.json();
      if (!response.ok || !payload?.success) {
        throw new Error(payload?.error || `Ошибка ${response.status}`);
      }
      if (serial !== state.generation || getActiveDialogTicketId() !== ticketId) return [];
      setParticipantsState(payload.participants);
      renderDialogParticipantsState();
      syncParticipantsSelectOptions();
      return getParticipantsState();
    }

    function renderReassignCurrentResponsible() {
      if (!elements.reassignCurrent) return;
      const label = String(getActiveDialogContext().operatorName || '').trim() || '—';
      elements.reassignCurrent.innerHTML = options.renderResponsibleCell?.(label, getActiveDialogResponsibleAvatarUrl()) || '—';
    }

    async function openParticipantsManager() {
      const ticketId = getActiveDialogTicketId();
      if (!ticketId) return;
      if (businessReference()) {
        if (elements.participantsMeta) elements.participantsMeta.textContent = 'Дополнительные участники текущего обращения.';
        options.showModalSafe?.(elements.participantsModalEl, elements.participantsModal);
        try { await loadDialogParticipants(); }
        catch (error) { renderDialogParticipantsLoadingState(error.message); notify(error.message, 'error'); }
        return;
      }
      const canAddParticipants = options.isWorkspaceActionEnabled?.('participants_add', options.canRunAction?.('can_assign'), ticketId);
      const canRemoveParticipants = options.isWorkspaceActionEnabled?.('participants_remove', options.canRunAction?.('can_assign'), ticketId);
      if (!canAddParticipants && !canRemoveParticipants) {
        if (options.notifyActionBlocked?.('participants_add', 'Участники', { ticketId, permissionKey: 'can_assign' })) {
          return;
        }
        if (options.notifyActionBlocked?.('participants_remove', 'Участники', { ticketId, permissionKey: 'can_assign' })) {
          return;
        }
        return;
      }
      if (elements.participantsMeta) {
        elements.participantsMeta.textContent = `Настройте дополнительных участников для обращения ${ticketId}.`;
      }
      try {
        await ensureAssignableOperatorsLoaded();
        await loadDialogParticipants();
        syncParticipantsSelectOptions();
        options.showModalSafe?.(elements.participantsModalEl, elements.participantsModal);
      } catch (error) {
        renderDialogParticipantsLoadingState(error?.message || 'Не удалось загрузить участников.');
        notify(error?.message || 'Не удалось открыть список участников', 'error');
      }
    }

    async function openReassignDialog() {
      const ticketId = getActiveDialogTicketId();
      if (!ticketId) return;
      if (options.notifyActionBlocked?.('reassign', 'Передать', { ticketId, permissionKey: 'can_assign' })) {
        return;
      }
      if (elements.reassignMeta) {
        elements.reassignMeta.textContent = `Передайте обращение ${ticketId} другому сотруднику панели.`;
      }
      renderReassignCurrentResponsible();
      try {
        await ensureAssignableOperatorsLoaded();
        syncReassignSelectOptions();
        options.showModalSafe?.(elements.reassignModalEl, elements.reassignModal);
      } catch (error) {
        notify(error?.message || 'Не удалось загрузить список пользователей', 'error');
      }
    }

    function syncDialogAssignControls() {
      const ticketId = getActiveDialogTicketId();
      const reassignEnabled = options.isWorkspaceActionEnabled?.('reassign', options.canRunAction?.('can_assign'), ticketId);
      const participantAddEnabled = options.isWorkspaceActionEnabled?.('participants_add', options.canRunAction?.('can_assign'), ticketId);
      const participantRemoveEnabled = options.isWorkspaceActionEnabled?.('participants_remove', options.canRunAction?.('can_assign'), ticketId);
      const participantsEnabled = participantAddEnabled || participantRemoveEnabled;
      if (elements.detailsReassignBtn) {
        elements.detailsReassignBtn.classList.toggle('d-none', !reassignEnabled);
        elements.detailsReassignBtn.disabled = !reassignEnabled;
      }
      [elements.detailsParticipantsBtn, elements.detailsParticipantsManageBtn].forEach((button) => {
        if (!button) return;
        button.classList.toggle('d-none', !businessReference() && !participantsEnabled);
        button.disabled = !businessReference() && !participantsEnabled;
      });
    }

    function bindParticipantEvents() {
      elements.participantsModalEl?.querySelector('[data-dialog-participants-refresh]')?.addEventListener('click', () => openParticipantsManager());
      if (elements.detailsParticipantsBtn) {
        elements.detailsParticipantsBtn.addEventListener('click', () => {
          openParticipantsManager();
        });
      }
      if (elements.detailsParticipantsManageBtn) {
        elements.detailsParticipantsManageBtn.addEventListener('click', () => {
          openParticipantsManager();
        });
      }
      if (elements.participantsAddBtn) {
        elements.participantsAddBtn.addEventListener('click', async () => {
          const username = String(elements.participantsSelect?.value || '').trim();
          const ticketId = getActiveDialogTicketId();
          if (!ticketId || !username) return;
          elements.participantsAddBtn.disabled = true;
          if (businessReference()) {
            try { await changeBusinessParticipant('POST', username); }
            catch (error) { notify(error.message, 'error'); }
            finally { syncParticipantsSelectOptions(); }
            return;
          }
          try {
            const response = await fetch(`/api/dialog-ticket/participants?ticketId=${encodeURIComponent(ticketId)}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ username }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            setParticipantsState(payload.participants);
            renderDialogParticipantsState();
            syncParticipantsSelectOptions();
            notify(payload.changed ? 'Пользователь подключён к диалогу' : 'Пользователь уже добавлен к диалогу', 'success');
          } catch (error) {
            notify(error?.message || 'Не удалось добавить участника', 'error');
          } finally {
            syncParticipantsSelectOptions();
          }
        });
      }
      if (elements.participantsCurrentList) {
        elements.participantsCurrentList.addEventListener('click', async (event) => {
          const removeButton = event.target instanceof Element
            ? event.target.closest('[data-remove-participant], [data-remove-participant-id]')
            : null;
          const ticketId = getActiveDialogTicketId();
          if (!removeButton || !ticketId) return;
          if (businessReference()) {
            removeButton.disabled = true;
            try { await changeBusinessParticipant('DELETE', removeButton.dataset.removeParticipantId); }
            catch (error) { notify(error.message, 'error'); }
            finally { if (removeButton.isConnected) removeButton.disabled = false; }
            return;
          }
          const username = String(removeButton.getAttribute('data-remove-participant') || '').trim();
          if (!username) return;
          removeButton.disabled = true;
          try {
            const response = await fetch(`/api/dialog-ticket/participants?ticketId=${encodeURIComponent(ticketId)}&username=${encodeURIComponent(username)}`, {
              method: 'DELETE',
              headers: { 'Content-Type': 'application/json' },
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            setParticipantsState(payload.participants);
            renderDialogParticipantsState();
            syncParticipantsSelectOptions();
            notify(payload.changed ? 'Участник убран из диалога' : 'Участник уже отсутствует', 'success');
          } catch (error) {
            removeButton.disabled = false;
            notify(error?.message || 'Не удалось убрать участника', 'error');
          }
        });
      }
      if (elements.detailsReassignBtn) {
        elements.detailsReassignBtn.addEventListener('click', () => {
          openReassignDialog();
        });
      }
      if (elements.reassignSubmit) {
        elements.reassignSubmit.addEventListener('click', async () => {
          const username = String(elements.reassignSelect?.value || '').trim();
          const ticketId = getActiveDialogTicketId();
          if (!ticketId || !username) return;
          elements.reassignSubmit.disabled = true;
          try {
            const response = await fetch(`/api/dialog-ticket/reassign?ticketId=${encodeURIComponent(ticketId)}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ username }),
            });
            const payload = await response.json();
            if (!response.ok || !payload?.success) {
              throw new Error(payload?.error || `Ошибка ${response.status}`);
            }
            const displayResponsible = payload.displayResponsible || payload.display_responsible || payload.responsible || username;
            const row = options.getActiveDialogRow?.() || options.findRowByTicketId?.(ticketId);
            if (row) {
              options.updateRowResponsible?.(row, payload.responsible || username, {
                rawResponsible: payload.responsible || username,
                displayResponsible,
                avatarUrl: payload.avatarUrl || payload.avatar_url || '',
              });
            }
            options.updateDetailsResponsible?.(displayResponsible, {
              rawResponsible: payload.responsible || username,
              avatarUrl: payload.avatarUrl || payload.avatar_url || '',
            });
            setParticipantsState(payload.participants);
            renderDialogParticipantsState();
            syncParticipantsSelectOptions();
            syncReassignSelectOptions();
            renderReassignCurrentResponsible();
            options.hideModalSafe?.(elements.reassignModalEl, elements.reassignModal);
            notify('Диалог переадресован новому ответственному', 'success');
          } catch (error) {
            notify(error?.message || 'Не удалось переадресовать диалог', 'error');
          } finally {
            syncReassignSelectOptions();
          }
        });
      }
    }

    return {
      setParticipantsState,
      renderDialogParticipantsState,
      renderDialogParticipantsLoadingState,
      syncParticipantsSelectOptions,
      syncReassignSelectOptions,
      ensureAssignableOperatorsLoaded,
      loadDialogParticipants,
      renderReassignCurrentResponsible,
      openParticipantsManager,
      openReassignDialog,
      syncDialogAssignControls,
      bindParticipantEvents,
    };
  }

  window.DialogsParticipantsRuntime = {
    createRuntime,
  };
})();

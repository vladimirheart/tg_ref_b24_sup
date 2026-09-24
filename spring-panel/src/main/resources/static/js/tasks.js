(function () {
  const table = document.getElementById('tasksTable');
  if (!table) return;

  const tbody = document.getElementById('tasksBody') || table.querySelector('tbody');
  const filters = document.getElementById('filters');
  const filtersModalEl = document.getElementById('filtersModal');
  const filtersModal = (filtersModalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal)
    ? bootstrap.Modal.getOrCreateInstance(filtersModalEl)
    : null;
  const pageSizeSel = document.getElementById('pageSizeSel');
  const pager = document.getElementById('pagination');
  const totalCounter = document.getElementById('tasksTotal');
  const shownCounter = document.getElementById('tasksShown');
  const summaryEl = document.getElementById('tasksSummary');
  const projectScope = document.getElementById('taskProjectScope');
  const scopeButtons = Array.from(document.querySelectorAll('[data-task-scope]'));

  const taskModalEl = document.getElementById('taskModal');
  const taskModal = (taskModalEl && typeof bootstrap !== 'undefined' && bootstrap.Modal)
    ? bootstrap.Modal.getOrCreateInstance(taskModalEl)
    : null;
  const taskForm = document.getElementById('taskForm');
  const taskId = document.getElementById('taskId');
  const taskNumber = document.getElementById('taskNumber');
  const taskBody = document.getElementById('bodyEditor');
  const taskProjects = document.getElementById('taskProjectsSelect');
  const taskEvents = document.getElementById('taskEvents');
  const taskHistory = document.getElementById('history');
  const commentsBlock = document.getElementById('commentsBlock');
  const comments = document.getElementById('comments');
  const commentEditor = document.getElementById('commentEditor');
  const sendCommentBtn = document.getElementById('sendCommentBtn');
  const saveTaskBtn = document.getElementById('saveTaskBtn');
  const deleteTaskBtn = document.getElementById('deleteTaskBtn');
  const editTaskBtn = document.getElementById('editToggleBtn');
  const timeLeft = document.getElementById('timeLeft');
  const createdAt = document.getElementById('createdAt');

  const FINAL_STATUSES = new Set(['завершена', 'отменена']);
  const state = {
    page: 1,
    pageSize: pageSizeSel ? (Number.parseInt(pageSizeSel.value, 10) || 20) : 20,
    sortBy: 'last_activity_at',
    sortDir: 'desc',
    total: 0,
    mine: false,
    projectId: '',
    projects: [],
  };

  let dirty = false;
  let forcedClose = false;

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';

  function requestOptions(options = {}) {
    const merged = { credentials: 'same-origin', ...options };
    const method = String(merged.method || 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) || !csrfToken) return merged;
    const headers = new Headers(merged.headers || {});
    headers.set(csrfHeader, csrfToken);
    return { ...merged, headers };
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function fmtDateTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString();
  }

  function toLocalDateTime(value) {
    if (!value) return '';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  }

  function isFinalStatus(value) {
    return FINAL_STATUSES.has(String(value || '').trim().toLowerCase());
  }

  function humanLeft(ms) {
    if (!Number.isFinite(ms)) return '—';
    const overdue = ms < 0;
    let seconds = Math.floor(Math.abs(ms) / 1000);
    let value;
    if (seconds < 3600) value = `${Math.max(1, Math.floor(seconds / 60))} мин`;
    else if (seconds < 86400) value = `${Math.floor(seconds / 3600)} ч`;
    else if (seconds < 604800) value = `${Math.floor(seconds / 86400)} дн`;
    else if (seconds < 2592000) value = `${Math.floor(seconds / 604800)} нед`;
    else value = `${Math.floor(seconds / 2592000)} мес`;
    return overdue ? `-${value}` : value;
  }

  async function httpJson(url, options = {}) {
    const response = await fetch(url, requestOptions(options));
    if (response.status === 401) {
      window.location.href = '/login';
      throw new Error('Unauthorized');
    }
    if (!response.ok) {
      let message = `HTTP ${response.status}`;
      try {
        const payload = await response.json();
        message = payload?.message || payload?.error || payload?.detail || message;
      } catch (_error) {
        // keep HTTP fallback
      }
      throw new Error(message);
    }
    return response;
  }

  function showError(title, error) {
    const message = error instanceof Error ? error.message : String(error || 'Неизвестная ошибка');
    if (typeof showAppModalMessage === 'function') {
      showAppModalMessage({ title, message, variant: 'danger' });
    } else {
      console.error(title, message);
    }
  }

  function activeFiltersQuery() {
    const query = new URLSearchParams(filters ? new FormData(filters) : undefined);
    query.set('page', String(state.page));
    query.set('page_size', String(state.pageSize));
    query.set('sort_by', state.sortBy);
    query.set('sort_dir', state.sortDir);
    if (state.mine) query.set('mine', 'true');
    if (state.projectId) query.set('project_id', state.projectId);
    return query;
  }

  function renderProjectChips(projects) {
    const list = Array.isArray(projects) ? projects : [];
    if (!list.length) return '<span class="text-muted">—</span>';
    const shown = list.slice(0, 2).map(project => (
      `<span class="tasks-project-chip" title="${escapeHtml(project.project_key || '')}">${escapeHtml(project.name || project.project_key || 'Проект')}</span>`
    ));
    if (list.length > 2) shown.push(`<span class="tasks-project-chip tasks-project-chip--more">+${list.length - 2}</span>`);
    return shown.join(' ');
  }

  function renderTagChips(tags) {
    const list = Array.isArray(tags) ? tags : [];
    if (!list.length) return '<span class="text-muted">—</span>';
    const shown = list.slice(0, 3).map(tag => `<span class="tasks-tag-chip">${escapeHtml(tag.name || '')}</span>`);
    if (list.length > 3) shown.push(`<span class="tasks-tag-chip tasks-tag-chip--more">+${list.length - 3}</span>`);
    return shown.join(' ');
  }

  function deadlineState(task) {
    if (!task?.due_at) return { rowClass: '', text: '—', meta: '' };
    const due = new Date(task.due_at).getTime();
    if (!Number.isFinite(due)) return { rowClass: '', text: '—', meta: '' };
    if (isFinalStatus(task.status)) {
      const closed = task.closed_at ? new Date(task.closed_at).getTime() : Date.now();
      const late = Number.isFinite(closed) && closed > due;
      return {
        rowClass: late ? 'overdue' : '',
        text: fmtDateTime(task.due_at),
        meta: late ? `Просрочено на ${humanLeft(-(closed - due)).replace('-', '')}` : 'Завершена в срок',
      };
    }
    const diff = due - Date.now();
    return {
      rowClass: diff < 0 ? 'overdue' : (diff < 86400000 ? 'warn' : ''),
      text: fmtDateTime(task.due_at),
      meta: humanLeft(diff),
    };
  }

  function renderRows(items) {
    tbody.innerHTML = '';
    if (!items.length) {
      const row = document.createElement('tr');
      row.className = 'ops-empty-state-row';
      row.innerHTML = '<td colspan="9" class="text-center text-muted py-4">Задачи не найдены. Измените условия или создайте новую задачу.</td>';
      tbody.appendChild(row);
      return;
    }

    for (const task of items) {
      const deadline = deadlineState(task);
      const row = document.createElement('tr');
      row.className = `tasks-row ${deadline.rowClass}`.trim();
      row.dataset.id = task.id || '';
      row.innerHTML = `
        <td class="tasks-cell-number">${escapeHtml(task.display_no || '')}</td>
        <td class="tasks-cell-title">
          <button class="tasks-title-button" type="button" data-open-task="${escapeHtml(task.id)}">
            ${escapeHtml(task.title || 'Без названия')}
          </button>
        </td>
        <td><div class="tasks-chip-stack">${renderProjectChips(task.projects)}</div></td>
        <td>${escapeHtml(task.assignee || '—')}</td>
        <td>
          <div>${deadline.text}</div>
          <div class="small ${deadline.rowClass === 'overdue' ? 'text-danger fw-semibold' : 'text-muted'}">${escapeHtml(deadline.meta)}</div>
        </td>
        <td><div class="tasks-chip-stack">${renderTagChips(task.tags)}</div></td>
        <td><span class="badge task-status-badge" data-task-status="${escapeHtml(task.status || '')}">${escapeHtml(task.status || '—')}</span></td>
        <td class="text-muted small">${fmtDateTime(task.last_activity_at)}</td>
        <td class="text-end"><button class="btn btn-sm btn-outline-secondary tasks-row-open" type="button" data-open-task="${escapeHtml(task.id)}" aria-label="Открыть задачу"><i class="bi bi-chevron-right" aria-hidden="true"></i></button></td>
      `;
      tbody.appendChild(row);
    }
  }

  function renderPagination() {
    if (!pager) return;
    pager.innerHTML = '';
    const totalPages = Math.max(1, Math.ceil(state.total / state.pageSize));
    if (totalPages <= 1) return;

    const append = (page, label, disabled, active) => {
      const item = document.createElement('li');
      item.className = `page-item${disabled ? ' disabled' : ''}${active ? ' active' : ''}`;
      const link = document.createElement('button');
      link.type = 'button';
      link.className = 'page-link';
      link.textContent = label;
      link.disabled = disabled;
      link.addEventListener('click', () => {
        if (disabled) return;
        state.page = page;
        loadTasks();
      });
      item.appendChild(link);
      pager.appendChild(item);
    };

    append(Math.max(1, state.page - 1), '‹', state.page <= 1, false);
    const start = Math.max(1, state.page - 2);
    const end = Math.min(totalPages, start + 4);
    for (let page = start; page <= end; page += 1) append(page, String(page), false, page === state.page);
    append(Math.min(totalPages, state.page + 1), '›', state.page >= totalPages, false);
  }

  function markSort() {
    table.querySelectorAll('th.sortable').forEach(header => {
      const active = header.dataset.sort === state.sortBy;
      const indicator = header.querySelector('.sort-ind');
      if (indicator) indicator.textContent = active ? (state.sortDir === 'asc' ? '↑' : '↓') : '↕';
      if (active) header.setAttribute('aria-sort', state.sortDir === 'asc' ? 'ascending' : 'descending');
      else header.removeAttribute('aria-sort');
    });
  }

  async function loadTasks() {
    try {
      const response = await httpJson(`/api/tasks?${activeFiltersQuery().toString()}`);
      const payload = await response.json();
      const items = Array.isArray(payload.items) ? payload.items : [];
      state.total = Number(payload.total || 0);
      state.page = Number(payload.page || state.page || 1);
      state.pageSize = Number(payload.page_size || state.pageSize || 20);
      if (pageSizeSel) pageSizeSel.value = String(state.pageSize);
      renderRows(items);
      renderPagination();
      markSort();
      if (totalCounter) totalCounter.textContent = String(state.total);
      if (shownCounter) shownCounter.textContent = String(items.length);
      if (summaryEl) {
        const pages = Math.max(1, Math.ceil(Math.max(state.total, 1) / state.pageSize));
        summaryEl.textContent = state.total
          ? `Показано ${items.length} из ${state.total} · Страница ${state.page}/${pages}`
          : 'Нет задач, подходящих под текущий срез.';
      }
    } catch (error) {
      showError('Не удалось загрузить задачи', error);
    }
  }

  function updateScopeUi() {
    scopeButtons.forEach(button => {
      const active = button.dataset.taskScope === (state.mine ? 'mine' : 'all');
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  function populateProjectControls() {
    if (projectScope) {
      const previous = state.projectId;
      projectScope.innerHTML = '<option value="">Все проекты</option>';
      for (const project of state.projects) {
        const option = document.createElement('option');
        option.value = String(project.id);
        option.textContent = `${project.project_key || ''} ${project.name || ''}`.trim();
        projectScope.appendChild(option);
      }
      projectScope.value = previous;
    }
    if (taskProjects) {
      taskProjects.innerHTML = '';
      for (const project of state.projects) {
        const option = document.createElement('option');
        option.value = String(project.id);
        option.textContent = `${project.project_key || ''} · ${project.name || ''}`.replace(/^ · /, '');
        taskProjects.appendChild(option);
      }
    }
  }

  async function loadProjects() {
    try {
      const response = await httpJson('/api/projects');
      const payload = await response.json();
      state.projects = Array.isArray(payload.items) ? payload.items : [];
      const params = new URLSearchParams(window.location.search);
      const requestedProject = params.get('project');
      if (requestedProject && state.projects.some(project => String(project.id) === requestedProject)) {
        state.projectId = requestedProject;
      }
      populateProjectControls();
    } catch (error) {
      showError('Не удалось загрузить проекты', error);
    }
  }

  function selectedProjectIds() {
    if (!taskProjects) return '';
    return Array.from(taskProjects.selectedOptions).map(option => option.value).filter(Boolean).join(',');
  }

  function setSelectedProjects(projects) {
    const desired = new Set((Array.isArray(projects) ? projects : []).map(project => String(project.id)));
    if (!taskProjects) return;
    Array.from(taskProjects.options).forEach(option => { option.selected = desired.has(option.value); });
  }

  function syncStatus(value) {
    if (!taskForm) return;
    const status = String(value || 'Новая').trim() || 'Новая';
    taskForm.dataset.status = status;
    const select = taskForm.elements.namedItem('status');
    if (select) select.value = status;
  }

  function setEditorMode(editing) {
    if (!taskForm) return;
    const title = taskForm.elements.namedItem('title');
    if (title) title.readOnly = !editing;
    if (taskBody) taskBody.setAttribute('contenteditable', editing ? 'true' : 'false');
    taskForm.classList.toggle('is-editing', editing);
    if (editTaskBtn) {
      editTaskBtn.hidden = editing || !taskId?.value;
    }
  }

  function renderComments(items) {
    if (!comments) return;
    const list = Array.isArray(items) ? items : [];
    comments.innerHTML = list.length ? list.map(item => `
      <article class="tasks-comment">
        <div class="tasks-comment-meta">${escapeHtml(item.author || '—')} · ${escapeHtml(fmtDateTime(item.created_at))}</div>
        <div class="tasks-comment-body">${item.html || ''}</div>
      </article>
    `).join('') : '<div class="text-muted small">Комментариев пока нет.</div>';
  }

  function eventText(event) {
    const field = event.field_name ? ` · ${event.field_name}` : '';
    if (event.event_type === 'FIELD_CHANGED') return `Изменено${field}`;
    if (event.event_type === 'TAG_ADDED') return `Добавлен тег ${event.new_value || ''}`.trim();
    if (event.event_type === 'TAG_REMOVED') return `Удалён тег ${event.old_value || ''}`.trim();
    if (event.event_type === 'PROJECT_ADDED') return `Добавлен проект ${event.new_value || ''}`.trim();
    if (event.event_type === 'PROJECT_REMOVED') return `Удалён проект ${event.old_value || ''}`.trim();
    if (event.event_type === 'COMMENT_ADDED') return 'Добавлен комментарий';
    if (event.event_type === 'TASK_CREATED') return 'Задача создана';
    return String(event.event_type || 'Событие');
  }

  function renderEvents(items) {
    if (!taskEvents) return;
    const list = Array.isArray(items) ? items : [];
    taskEvents.innerHTML = list.length ? list.map(event => `
      <div class="tasks-event-row">
        <span class="tasks-event-dot" aria-hidden="true"></span>
        <span class="tasks-event-copy">
          <strong>${escapeHtml(eventText(event))}</strong>
          <span>${escapeHtml(event.actor || 'system')} · ${escapeHtml(fmtDateTime(event.occurred_at))}</span>
        </span>
      </div>
    `).join('') : '<div class="text-muted small">Структурированных событий пока нет.</div>';
  }

  function renderLegacyHistory(items) {
    if (!taskHistory) return;
    const list = Array.isArray(items) ? items : [];
    taskHistory.innerHTML = list.length ? list.map(item => `
      <div class="tasks-history-row"><span>${escapeHtml(fmtDateTime(item.at))}</span>${escapeHtml(item.text || '')}</div>
    `).join('') : '<div class="text-muted small">Legacy history пуст.</div>';
  }

  function updateTaskTime(task) {
    if (createdAt) createdAt.textContent = fmtDateTime(task?.created_at);
    if (!timeLeft) return;
    if (!task?.due_at || isFinalStatus(task.status)) {
      timeLeft.textContent = '—';
      return;
    }
    timeLeft.textContent = humanLeft(new Date(task.due_at).getTime() - Date.now());
  }

  function resetTaskForm() {
    taskForm?.reset();
    if (taskId) taskId.value = '';
    if (taskBody) taskBody.innerHTML = '';
    if (taskNumber) taskNumber.textContent = 'Новая задача';
    if (commentsBlock) commentsBlock.hidden = true;
    if (comments) comments.innerHTML = '';
    if (taskEvents) taskEvents.innerHTML = '<div class="text-muted small">События появятся после сохранения.</div>';
    if (taskHistory) taskHistory.innerHTML = '<div class="text-muted small">История появится после сохранения.</div>';
    if (deleteTaskBtn) deleteTaskBtn.hidden = true;
    setSelectedProjects(state.projectId ? [{ id: state.projectId }] : []);
    syncStatus('Новая');
    updateTaskTime(null);
    setEditorMode(true);
    dirty = false;
  }

  function showTaskModal() {
    if (taskModal) taskModal.show();
  }

  function hideTaskModal(force = false) {
    if (!taskModal) return;
    forcedClose = force;
    taskModal.hide();
  }

  async function openTask(id) {
    if (!id) return;
    try {
      const response = await httpJson(`/api/tasks/${encodeURIComponent(id)}`);
      const task = await response.json();
      if (taskId) taskId.value = String(task.id || '');
      if (taskNumber) taskNumber.textContent = task.display_no || `DL_${task.id}`;
      if (taskForm?.elements.namedItem('title')) taskForm.elements.namedItem('title').value = task.title || '';
      if (taskForm?.elements.namedItem('creator')) taskForm.elements.namedItem('creator').value = task.creator || '';
      if (taskForm?.elements.namedItem('assignee')) taskForm.elements.namedItem('assignee').value = task.assignee || '';
      if (taskForm?.elements.namedItem('co')) taskForm.elements.namedItem('co').value = (task.co || []).join(', ');
      if (taskForm?.elements.namedItem('watchers')) taskForm.elements.namedItem('watchers').value = (task.watchers || []).join(', ');
      if (taskForm?.elements.namedItem('tags')) taskForm.elements.namedItem('tags').value = (task.tags || []).map(tag => tag.name).join(', ');
      if (taskForm?.elements.namedItem('due_at')) taskForm.elements.namedItem('due_at').value = toLocalDateTime(task.due_at);
      if (taskBody) taskBody.innerHTML = task.body_html || '';
      setSelectedProjects(task.projects || []);
      syncStatus(task.status || 'Новая');
      renderComments(task.comments || []);
      renderEvents(task.events || []);
      renderLegacyHistory(task.history || []);
      if (commentsBlock) commentsBlock.hidden = false;
      if (deleteTaskBtn) deleteTaskBtn.hidden = false;
      updateTaskTime(task);
      setEditorMode(false);
      dirty = false;
      showTaskModal();
    } catch (error) {
      showError('Не удалось открыть задачу', error);
    }
  }

  async function saveTask() {
    if (!taskForm || !taskForm.reportValidity()) return;
    const data = new FormData();
    const id = String(taskId?.value || '').trim();
    const tags = String(taskForm.elements.namedItem('tags')?.value || '').trim();
    data.append('id', id);
    data.append('title', String(taskForm.elements.namedItem('title')?.value || '').trim());
    data.append('body_html', taskBody?.innerHTML || '');
    data.append('creator', String(taskForm.elements.namedItem('creator')?.value || '').trim());
    data.append('assignee', String(taskForm.elements.namedItem('assignee')?.value || '').trim());
    data.append('co', String(taskForm.elements.namedItem('co')?.value || '').trim());
    data.append('watchers', String(taskForm.elements.namedItem('watchers')?.value || '').trim());
    data.append('tags', tags);
    data.append('tag', tags);
    data.append('project_ids', selectedProjectIds());
    data.append('due_at', String(taskForm.elements.namedItem('due_at')?.value || '').trim());
    data.append('status', String(taskForm.dataset.status || 'Новая'));

    try {
      const response = await httpJson('/api/tasks', { method: 'POST', body: data });
      const payload = await response.json();
      if (payload?.ok === false) throw new Error(payload.error || 'Ошибка сохранения');
      dirty = false;
      hideTaskModal(true);
      await loadTasks();
    } catch (error) {
      showError('Не удалось сохранить задачу', error);
    }
  }

  async function addComment() {
    const id = String(taskId?.value || '').trim();
    const html = String(commentEditor?.innerHTML || '').trim();
    if (!id || !html) return;
    const data = new FormData();
    data.append('html', html);
    try {
      await httpJson(`/api/tasks/${encodeURIComponent(id)}/comments`, { method: 'POST', body: data });
      if (commentEditor) commentEditor.innerHTML = '';
      await openTask(id);
    } catch (error) {
      showError('Не удалось добавить комментарий', error);
    }
  }

  async function deleteTask() {
    const id = String(taskId?.value || '').trim();
    if (!id) return;
    const confirmed = typeof showConfirmActionModal === 'function'
      ? await showConfirmActionModal({
          title: 'Удалить задачу',
          message: 'Задача и её комментарии будут удалены. Продолжить?',
          confirmText: 'Удалить',
          confirmVariant: 'danger',
        })
      : window.confirm('Удалить задачу?');
    if (!confirmed) return;
    try {
      await httpJson(`/api/tasks/${encodeURIComponent(id)}`, { method: 'DELETE' });
      dirty = false;
      hideTaskModal(true);
      await loadTasks();
    } catch (error) {
      showError('Не удалось удалить задачу', error);
    }
  }

  function openCreateFromDialogContext() {
    let context = null;
    try {
      const params = new URLSearchParams(window.location.search);
      if (params.get('create') === '1' && params.get('ticketId')) {
        context = { ticketId: params.get('ticketId'), client: params.get('client') || '' };
        params.delete('create');
        params.delete('ticketId');
        params.delete('client');
        const suffix = params.toString();
        window.history.replaceState({}, '', `${window.location.pathname}${suffix ? `?${suffix}` : ''}${window.location.hash}`);
      }
    } catch (_error) {
      // ignore malformed location state
    }
    if (!context) {
      try {
        const raw = localStorage.getItem('iguana:dialogs:create-task');
        if (raw) {
          localStorage.removeItem('iguana:dialogs:create-task');
          context = JSON.parse(raw);
        }
      } catch (_error) {
        // ignore stale client context
      }
    }
    if (!context?.ticketId) return;
    resetTaskForm();
    const title = taskForm?.elements.namedItem('title');
    if (title) title.value = `Обращение #${context.ticketId}${context.client ? `: ${context.client}` : ''}`;
    const tags = taskForm?.elements.namedItem('tags');
    if (tags) tags.value = 'dialog';
    if (taskBody) taskBody.innerHTML = `<p>Создано из диалога #${escapeHtml(context.ticketId)}${context.client ? `, клиент: ${escapeHtml(context.client)}` : ''}.</p>`;
    dirty = true;
    showTaskModal();
  }

  document.getElementById('filtersBtn')?.addEventListener('click', () => filtersModal?.show());
  document.getElementById('applyFiltersBtn')?.addEventListener('click', () => {
    state.page = 1;
    filtersModal?.hide();
    loadTasks();
  });
  document.getElementById('resetFiltersBtn')?.addEventListener('click', () => {
    filters?.reset();
    state.page = 1;
    filtersModal?.hide();
    loadTasks();
  });
  filters?.addEventListener('submit', event => {
    event.preventDefault();
    state.page = 1;
    filtersModal?.hide();
    loadTasks();
  });

  pageSizeSel?.addEventListener('change', () => {
    state.pageSize = Number.parseInt(pageSizeSel.value, 10) || 20;
    state.page = 1;
    loadTasks();
  });

  projectScope?.addEventListener('change', () => {
    state.projectId = projectScope.value || '';
    state.page = 1;
    const url = new URL(window.location.href);
    if (state.projectId) url.searchParams.set('project', state.projectId);
    else url.searchParams.delete('project');
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
    loadTasks();
  });

  scopeButtons.forEach(button => button.addEventListener('click', () => {
    state.mine = button.dataset.taskScope === 'mine';
    state.page = 1;
    updateScopeUi();
    loadTasks();
  }));

  table.querySelectorAll('th.sortable').forEach(header => {
    const activate = () => {
      const key = header.dataset.sort;
      if (!key) return;
      if (state.sortBy === key) state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
      else {
        state.sortBy = key;
        state.sortDir = 'asc';
      }
      state.page = 1;
      loadTasks();
    };
    header.addEventListener('click', activate);
    header.addEventListener('keydown', event => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      event.preventDefault();
      activate();
    });
  });

  document.addEventListener('click', event => {
    const opener = event.target.closest('[data-open-task]');
    if (!opener) return;
    openTask(opener.dataset.openTask);
  });

  document.getElementById('createTaskBtn')?.addEventListener('click', () => {
    resetTaskForm();
    showTaskModal();
  });
  editTaskBtn?.addEventListener('click', () => setEditorMode(true));
  saveTaskBtn?.addEventListener('click', saveTask);
  sendCommentBtn?.addEventListener('click', addComment);
  deleteTaskBtn?.addEventListener('click', deleteTask);

  taskForm?.addEventListener('input', () => { dirty = true; });
  taskForm?.addEventListener('change', () => { dirty = true; });
  taskBody?.addEventListener('input', () => { dirty = true; });

  taskModalEl?.addEventListener('hide.bs.modal', event => {
    if (forcedClose) {
      forcedClose = false;
      return;
    }
    if (!dirty) return;
    event.preventDefault();
    const confirm = typeof showConfirmActionModal === 'function'
      ? showConfirmActionModal({
          title: 'Закрыть задачу',
          message: 'Есть несохранённые изменения. Закрыть без сохранения?',
          confirmText: 'Закрыть',
          confirmVariant: 'warning',
        })
      : Promise.resolve(window.confirm('Закрыть без сохранения?'));
    Promise.resolve(confirm).then(accepted => {
      if (accepted) {
        dirty = false;
        hideTaskModal(true);
      }
    });
  });

  document.querySelectorAll('[data-task-close]').forEach(button => button.addEventListener('click', event => {
    event.preventDefault();
    if (!taskModal) return;
    if (!dirty) hideTaskModal(true);
    else taskModalEl?.dispatchEvent(new Event('hide.bs.modal', { cancelable: true }));
  }));

  window.addEventListener('hashchange', () => {
    const match = window.location.hash.match(/^#task=(\d+)$/);
    if (match) openTask(match[1]);
  });

  updateScopeUi();
  Promise.resolve()
    .then(loadProjects)
    .then(loadTasks)
    .then(() => {
      const match = window.location.hash.match(/^#task=(\d+)$/);
      if (match) return openTask(match[1]);
      openCreateFromDialogContext();
      return null;
    });

  window.__tasksPrimaryReady = true;
})();

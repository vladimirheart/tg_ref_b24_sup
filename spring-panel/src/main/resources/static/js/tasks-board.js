(function () {
  const boardView = document.getElementById('taskBoardView');
  const analyticsView = document.getElementById('taskAnalyticsView');
  const listView = document.getElementById('taskListView');
  const listControls = document.getElementById('taskListControls');
  const filtersBtn = document.getElementById('filtersBtn');
  const viewButtons = Array.from(document.querySelectorAll('[data-task-view]'));
  const scopeSelect = document.getElementById('taskBoardScope');
  const kanban = document.getElementById('taskKanban');
  const boardTitle = document.getElementById('taskBoardTitle');
  const boardMeta = document.getElementById('taskBoardMeta');
  const boardAlert = document.getElementById('taskBoardAlert');
  const addColumnBtn = document.getElementById('taskBoardAddColumnBtn');
  const listProjectScope = document.getElementById('taskProjectScope');
  if (!boardView || !analyticsView || !listView || !scopeSelect || !kanban) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
  const state = { view: 'list', board: null, projects: [], draggingTaskId: null };

  function requestOptions(options = {}) {
    const merged = { credentials: 'same-origin', ...options };
    const method = String(merged.method || 'GET').toUpperCase();
    const headers = new Headers(merged.headers || {});
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) && csrfToken) headers.set(csrfHeader, csrfToken);
    return { ...merged, headers };
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
      } catch (_error) {}
      throw new Error(message);
    }
    return response.json();
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function showError(error) {
    const message = error instanceof Error ? error.message : String(error || 'Неизвестная ошибка');
    if (boardAlert) {
      boardAlert.textContent = message;
      boardAlert.classList.remove('d-none');
    }
  }

  function clearError() {
    if (!boardAlert) return;
    boardAlert.textContent = '';
    boardAlert.classList.add('d-none');
  }

  function fmtDate(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return date.toLocaleString();
  }

  function boardScope() {
    const value = String(scopeSelect.value || 'mine');
    if (value.startsWith('project:')) return { type: 'project', projectId: value.slice('project:'.length) };
    return { type: 'mine', projectId: '' };
  }

  function syncListScope(scope) {
    if (scope.type === 'project') {
      document.querySelector('[data-task-scope="all"]')?.click();
      if (listProjectScope && listProjectScope.value !== scope.projectId) {
        listProjectScope.value = scope.projectId;
        listProjectScope.dispatchEvent(new Event('change', { bubbles: true }));
      }
      return;
    }
    document.querySelector('[data-task-scope="mine"]')?.click();
    if (listProjectScope && listProjectScope.value) {
      listProjectScope.value = '';
      listProjectScope.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }

  function updateUrl() {
    const url = new URL(window.location.href);
    if (state.view === 'board' || state.view === 'analytics') url.searchParams.set('view', state.view);
    else url.searchParams.delete('view');
    const scope = boardScope();
    if (state.view === 'board' && scope.type === 'project') url.searchParams.set('project', scope.projectId);
    else if (state.view === 'board') url.searchParams.delete('project');
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
  }

  function setView(view, load = true) {
    state.view = ['board', 'analytics'].includes(view) ? view : 'list';
    const boardActive = state.view === 'board';
    const analyticsActive = state.view === 'analytics';
    listView.hidden = boardActive || analyticsActive;
    boardView.hidden = !boardActive;
    analyticsView.hidden = !analyticsActive;
    if (listControls) listControls.hidden = boardActive || analyticsActive;
    if (filtersBtn) filtersBtn.hidden = boardActive || analyticsActive;
    viewButtons.forEach(button => {
      const active = button.dataset.taskView === state.view;
      button.classList.toggle('active', active);
      button.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
    updateUrl();
    if (boardActive && load) loadBoard();
    if (analyticsActive && load) window.dispatchEvent(new CustomEvent('tasks:analytics-activate'));
  }

  function populateScopes() {
    const requested = new URLSearchParams(window.location.search).get('project');
    scopeSelect.innerHTML = '<option value="mine">Мои задачи</option>';
    for (const project of state.projects) {
      const option = document.createElement('option');
      option.value = `project:${project.id}`;
      option.textContent = `${project.project_key || ''} · ${project.name || ''}`.replace(/^ · /, '');
      scopeSelect.appendChild(option);
    }
    if (requested && state.projects.some(project => String(project.id) === requested)) {
      scopeSelect.value = `project:${requested}`;
    } else {
      scopeSelect.value = 'mine';
    }
  }

  async function loadProjects() {
    const payload = await httpJson('/api/projects');
    state.projects = Array.isArray(payload.items) ? payload.items : [];
    populateScopes();
  }

  function tagsHtml(tags) {
    const list = Array.isArray(tags) ? tags : [];
    return list.slice(0, 3).map(tag => `<span class="tasks-tag-chip">${escapeHtml(tag.name || '')}</span>`).join('');
  }

  function cardHtml(card) {
    const displayNo = card.seq != null ? `DL_${card.seq}` : `DL_${card.id}`;
    return `
      <article class="tasks-kanban-card" draggable="true" data-task-id="${escapeHtml(card.id)}">
        <div class="tasks-kanban-card-topline">
          <span class="tasks-kanban-number">${escapeHtml(displayNo)}</span>
          <span class="badge task-status-badge" data-task-status="${escapeHtml(card.status || '')}">${escapeHtml(card.status || '—')}</span>
        </div>
        <button class="tasks-kanban-title" type="button" data-open-task="${escapeHtml(card.id)}">${escapeHtml(card.title || 'Без названия')}</button>
        <div class="tasks-kanban-card-meta"><span><i class="bi bi-person" aria-hidden="true"></i>${escapeHtml(card.assignee || '—')}</span><span><i class="bi bi-calendar3" aria-hidden="true"></i>${escapeHtml(fmtDate(card.due_at))}</span></div>
        <div class="tasks-kanban-tags">${tagsHtml(card.tags)}</div>
      </article>`;
  }

  function renderBoard(board) {
    state.board = board;
    clearError();
    if (boardTitle) boardTitle.textContent = board?.name || 'Доска';
    const columns = Array.isArray(board?.columns) ? board.columns : [];
    const cards = columns.reduce((sum, column) => sum + (Array.isArray(column.cards) ? column.cards.length : 0), 0);
    if (boardMeta) boardMeta.textContent = `${columns.length} колонок · ${cards} задач`;
    kanban.innerHTML = columns.map((column, index) => {
      const items = Array.isArray(column.cards) ? column.cards : [];
      return `
        <section class="tasks-kanban-column" data-column-id="${escapeHtml(column.id)}">
          <header class="tasks-kanban-column-head">
            <div class="tasks-kanban-column-title"><strong>${escapeHtml(column.name || 'Колонка')}</strong><span>${items.length}</span></div>
            <div class="tasks-kanban-column-actions">
              <button type="button" class="btn btn-sm btn-link" data-column-left="${escapeHtml(column.id)}" ${index === 0 ? 'disabled' : ''} aria-label="Сдвинуть колонку влево"><i class="bi bi-arrow-left"></i></button>
              <button type="button" class="btn btn-sm btn-link" data-column-right="${escapeHtml(column.id)}" ${index === columns.length - 1 ? 'disabled' : ''} aria-label="Сдвинуть колонку вправо"><i class="bi bi-arrow-right"></i></button>
              <button type="button" class="btn btn-sm btn-link" data-column-rename="${escapeHtml(column.id)}" aria-label="Переименовать колонку"><i class="bi bi-pencil"></i></button>
              <button type="button" class="btn btn-sm btn-link text-danger" data-column-delete="${escapeHtml(column.id)}" aria-label="Удалить колонку"><i class="bi bi-trash"></i></button>
            </div>
          </header>
          <div class="tasks-kanban-card-list" data-column-dropzone="${escapeHtml(column.id)}">
            ${items.map(cardHtml).join('')}
            ${items.length ? '' : '<div class="tasks-kanban-empty">Перетащите задачу сюда</div>'}
          </div>
        </section>`;
    }).join('');
    bindDragAndDrop();
  }

  async function loadBoard() {
    if (state.view !== 'board') return;
    clearError();
    try {
      const scope = boardScope();
      syncListScope(scope);
      updateUrl();
      const endpoint = scope.type === 'project'
        ? `/api/task-boards/project/${encodeURIComponent(scope.projectId)}/ensure`
        : '/api/task-boards/mine/ensure';
      const payload = await httpJson(endpoint, { method: 'POST' });
      renderBoard(payload.board);
    } catch (error) {
      showError(error);
    }
  }

  async function boardMutation(url, method, body) {
    clearError();
    try {
      const options = { method };
      if (body !== undefined) {
        options.headers = { 'Content-Type': 'application/json' };
        options.body = JSON.stringify(body);
      }
      const payload = await httpJson(url, options);
      renderBoard(payload.board);
    } catch (error) {
      showError(error);
    }
  }

  function columnIndex(columnId) {
    return (state.board?.columns || []).findIndex(column => String(column.id) === String(columnId));
  }

  async function renameColumn(columnId) {
    const column = (state.board?.columns || []).find(item => String(item.id) === String(columnId));
    if (!column) return;
    const name = window.prompt('Название колонки', column.name || '');
    if (name == null || !name.trim() || name.trim() === column.name) return;
    await boardMutation(`/api/task-boards/${state.board.id}/columns/${columnId}`, 'PATCH', { name: name.trim() });
  }

  async function moveColumn(columnId, delta) {
    const current = columnIndex(columnId);
    if (current < 0) return;
    const target = current + delta;
    if (target < 0 || target >= (state.board?.columns || []).length) return;
    await boardMutation(`/api/task-boards/${state.board.id}/columns/${columnId}`, 'PATCH', { target_index: target });
  }

  async function deleteColumn(columnId) {
    const column = (state.board?.columns || []).find(item => String(item.id) === String(columnId));
    if (!column) return;
    const accepted = typeof showConfirmActionModal === 'function'
      ? await showConfirmActionModal({ title: 'Удалить колонку', message: `Удалить колонку «${column.name}»? Она должна быть пустой.`, confirmText: 'Удалить', confirmVariant: 'danger' })
      : window.confirm(`Удалить колонку «${column.name}»?`);
    if (!accepted) return;
    await boardMutation(`/api/task-boards/${state.board.id}/columns/${columnId}`, 'DELETE');
  }

  function dropIndex(list, clientY) {
    const cards = Array.from(list.querySelectorAll('.tasks-kanban-card:not(.is-dragging)'));
    for (let index = 0; index < cards.length; index += 1) {
      const box = cards[index].getBoundingClientRect();
      if (clientY < box.top + box.height / 2) return index;
    }
    return cards.length;
  }

  function bindDragAndDrop() {
    kanban.querySelectorAll('.tasks-kanban-card').forEach(card => {
      card.addEventListener('dragstart', event => {
        state.draggingTaskId = card.dataset.taskId || null;
        card.classList.add('is-dragging');
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', state.draggingTaskId || '');
      });
      card.addEventListener('dragend', () => {
        state.draggingTaskId = null;
        card.classList.remove('is-dragging');
        kanban.querySelectorAll('.is-drop-target').forEach(item => item.classList.remove('is-drop-target'));
      });
    });

    kanban.querySelectorAll('[data-column-dropzone]').forEach(list => {
      list.addEventListener('dragover', event => {
        event.preventDefault();
        event.dataTransfer.dropEffect = 'move';
        list.classList.add('is-drop-target');
      });
      list.addEventListener('dragleave', event => {
        if (!list.contains(event.relatedTarget)) list.classList.remove('is-drop-target');
      });
      list.addEventListener('drop', event => {
        event.preventDefault();
        list.classList.remove('is-drop-target');
        const taskId = state.draggingTaskId || event.dataTransfer.getData('text/plain');
        const columnId = list.dataset.columnDropzone;
        if (!taskId || !columnId || !state.board?.id) return;
        const targetIndex = dropIndex(list, event.clientY);
        boardMutation(`/api/task-boards/${state.board.id}/move`, 'POST', {
          task_id: Number(taskId),
          column_id: Number(columnId),
          target_index: targetIndex,
        });
      });
    });
  }

  viewButtons.forEach(button => button.addEventListener('click', () => setView(button.dataset.taskView)));
  scopeSelect.addEventListener('change', loadBoard);
  addColumnBtn?.addEventListener('click', async () => {
    if (!state.board?.id) return;
    const name = window.prompt('Название новой колонки');
    if (name == null || !name.trim()) return;
    await boardMutation(`/api/task-boards/${state.board.id}/columns`, 'POST', { name: name.trim() });
  });

  kanban.addEventListener('click', event => {
    const rename = event.target.closest('[data-column-rename]');
    if (rename) return void renameColumn(rename.dataset.columnRename);
    const left = event.target.closest('[data-column-left]');
    if (left) return void moveColumn(left.dataset.columnLeft, -1);
    const right = event.target.closest('[data-column-right]');
    if (right) return void moveColumn(right.dataset.columnRight, 1);
    const remove = event.target.closest('[data-column-delete]');
    if (remove) return void deleteColumn(remove.dataset.columnDelete);
  });

  document.getElementById('taskModal')?.addEventListener('hidden.bs.modal', () => {
    if (state.view === 'board') loadBoard();
  });

  Promise.resolve()
    .then(loadProjects)
    .then(() => {
      const requestedView = new URLSearchParams(window.location.search).get('view');
      setView(requestedView === 'board' ? 'board' : (requestedView === 'analytics' ? 'analytics' : 'list'));
    })
    .catch(showError);

  window.__tasksBoardReady = true;
})();
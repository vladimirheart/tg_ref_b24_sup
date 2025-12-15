// panel/static/tasks.js
(function () {
  // если таблицы задач на странице нет — выходим молча
  const table = document.getElementById('tasksTable');
  if (!table) return;

  const tbody = table.querySelector('tbody');
  const filters = document.getElementById('filters');
  const filtersModalEl = document.getElementById('filtersModal');
  const exportBtn = document.getElementById('exportBtn');
  const pageSizeSel = document.getElementById('pageSizeSel');
  const pager = document.getElementById('pager');
  const modalEl = document.getElementById('taskModal');
  const form = document.getElementById('taskForm');
  const bodyEditor = document.getElementById('bodyEditor');
  const deleteBtn = document.getElementById('deleteTaskBtn');
  const totalCounter = document.getElementById('tasksTotal');
  const shownCounter = document.getElementById('tasksShown');
  const summaryEl = document.getElementById('tasksSummary');
  const taskNumberEl = document.getElementById('taskNumber');

  // элементы могут отсутствовать — страхуемся
  const filtersModal = (typeof bootstrap !== 'undefined' && filtersModalEl)
    ? new bootstrap.Modal(filtersModalEl)
    : null;

  const FINAL_STATUSES = new Set(['завершена', 'отменена']);

  // ===== состояние таблицы
  let state = {
    page: 1,
    page_size: pageSizeSel ? (parseInt(pageSizeSel.value, 10) || 20) : 20,
    sort_by: 'last_activity_at',
    sort_dir: 'desc',
    total: 0
  };

  // ===== utils
  function qs(formEl) {
    const p = new URLSearchParams(formEl ? new FormData(formEl) : undefined);
    p.set('page', state.page);
    p.set('page_size', state.page_size);
    p.set('sort_by', state.sort_by);
    p.set('sort_dir', state.sort_dir);
    return p.toString();
  }
  function fmtDT(s) {
    if (!s) return '—';
    const d = new Date(s);
    return d.toLocaleString();
  }
  function escapeAttr(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
    function isFinalStatus(status) {
    return FINAL_STATUSES.has(String(status || '').trim().toLowerCase());
  }
// ===== utils
        function humanLeft(ms) {
          if (ms == null) return '—';
          const sec = Math.floor(ms / 1000);
	  
	  if (sec < 0) {
		// Просрочено - отрицательные значения
		const absSec = Math.abs(sec);
		
		if (absSec < 3600) {
		  // Меньше 1 часа - минуты
		  return `-${Math.floor(absSec / 60)} мин`;
		} else if (absSec < 86400) {
		  // Меньше 24 часов - часы
		  return `-${Math.floor(absSec / 3600)} ч`;
		} else if (absSec < 604800) {
		  // Меньше 7 дней - дни
		  return `-${Math.floor(absSec / 86400)} дн`;
		} else if (absSec < 2592000) {
		  // Меньше 30 дней (примерно 1 месяц) - недели
		  return `-${Math.floor(absSec / 604800)} нед`;
		} else if (absSec < 31536000) {
		  // Меньше 1 года - месяцы
		  return `-${Math.floor(absSec / 2592000)} мес`;
		} else {
		  // Больше 1 года - годы
		  return `-${Math.floor(absSec / 31536000)} г`;
		}
	  } else {
		// Не просрочено - положительные значения (старая логика)
		const day = 86400, week = 7 * day, month = 30 * day, year = 365 * day;
		if (sec < day) return `${Math.floor(sec / 3600)} ч`;
		if (sec < week) return `${Math.floor(sec / day)} дн`;
		if (sec < month) return `${Math.floor(sec / week)} нед`;
		if (sec < year) return `${Math.floor(sec / month)} мес`;
		return `${Math.floor(sec / year)} г`;
        }
        }

  function formatDurationAbs(ms) {
    const sec = Math.floor(Math.abs(ms) / 1000);
    if (sec < 60) return `${sec} сек`;
    if (sec < 3600) return `${Math.floor(sec / 60)} мин`;
    if (sec < 86400) return `${Math.floor(sec / 3600)} ч`;
    if (sec < 604800) return `${Math.floor(sec / 86400)} дн`;
    if (sec < 2592000) return `${Math.floor(sec / 604800)} нед`;
    if (sec < 31536000) return `${Math.floor(sec / 2592000)} мес`;
    return `${Math.floor(sec / 31536000)} г`;
  }

  function describeFinalOverdue(dueAt, closedAt) {
    if (!dueAt) return { text: '', overdue: false };
    const dueTs = new Date(dueAt).getTime();
    const closedTs = closedAt ? new Date(closedAt).getTime() : Date.now();
    if (Number.isNaN(dueTs) || Number.isNaN(closedTs)) {
      return { text: '', overdue: false };
    }
    const diff = closedTs - dueTs;
    if (diff > 0) {
      return { text: `Просрочено на ${formatDurationAbs(diff)}`, overdue: true };
    }
    if (diff === 0) {
      return { text: 'Завершена в срок', overdue: false };
    }
    return { text: 'Завершена досрочно', overdue: false };
  }

  function getModalInstance() {
    if (typeof bootstrap === 'undefined' || !modalEl) return null;
    return bootstrap.Modal.getOrCreateInstance(modalEl);
  }

  let skipDirtyConfirm = false;
  function hideTaskModal(force = false) {
    const inst = getModalInstance();
    if (!inst) return;
    if (force) skipDirtyConfirm = true;
    inst.hide();
  }

    function requestModalClose() {
      if (!isDirty) {
        hideTaskModal(true);
        return;
      }
      showConfirmActionModal({
        title: 'Закрыть задачу',
        message: 'Есть несохранённые изменения. Закрыть без сохранения?',
        confirmText: 'Закрыть',
        confirmVariant: 'warning',
        icon: '⚠️',
        onConfirm: () => hideTaskModal(true),
      });
    }

  function syncStatusButtons(value) {
    if (!form) return;
    const normalized = String(value || '').trim();
    const target = normalized || 'Новая';
    form.dataset.status = target;
    document.querySelectorAll('.task-status').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.st === target);
    });
  }

  async function httpJson(url, opts = {}) {
    const r = await fetch(url, { credentials: 'same-origin', ...opts });
      if (r.status === 401) {
        let msg = 'Сессия истекла или нет доступа. Пожалуйста, войдите заново.';
        try { const data = await r.json(); if (data && data.message) msg = 'Сессия истекла. Пожалуйста, войдите заново.'; } catch {}
        if (!location.pathname.includes('/login')) {
          const redirect = () => { window.location.href = '/login'; };
          const shown = showAppModalMessage({
            title: 'Требуется вход',
            message: msg,
            variant: 'warning',
            onHidden: redirect,
          });
          if (!shown) {
            redirect();
          }
        }
        throw new Error('Unauthorized');
      }
    return r;
  }

  // ===== таблица
  async function load() {
    const q = qs(filters);
    const r = await httpJson('/api/tasks?' + q);
    const data = await r.json();
    state.total = data.total || 0;
    const pageFromServer = parseInt(data.page, 10);
    if (!Number.isNaN(pageFromServer) && pageFromServer > 0) state.page = pageFromServer;
    const sizeFromServer = parseInt(data.page_size, 10);
    if (!Number.isNaN(sizeFromServer) && sizeFromServer > 0) state.page_size = sizeFromServer;
    const items = data.items || [];
    if (pageSizeSel) {
      const valueStr = String(state.page_size);
      if (![...pageSizeSel.options].some(o => o.value === valueStr)) {
        const opt = document.createElement('option');
        opt.value = valueStr;
        opt.textContent = valueStr;
        pageSizeSel.appendChild(opt);
      }
      pageSizeSel.value = valueStr;
    }

    tbody.innerHTML = '';
    for (const t of items) {
      const tr = document.createElement('tr');

      const final = isFinalStatus(t.status);
      const dueAtValue = t.due_at || '';
      const closedAtValue = t.closed_at || '';
      tr.dataset.status = t.status || '';
      tr.dataset.dueAt = dueAtValue;
      tr.dataset.closedAt = closedAtValue;
      tr.dataset.id = t.id || '';
      tr.className = '';
      let timeLeftText = '';
      let timeLeftClass = 'text-muted';
      let finalOverdue = false;
      if (dueAtValue) {
        if (final) {
          const info = describeFinalOverdue(dueAtValue, closedAtValue);
          timeLeftText = info.text;
          finalOverdue = info.overdue;
          timeLeftClass = info.overdue ? 'text-danger fw-semibold' : 'text-muted';
          if (info.overdue) {
            tr.classList.add('overdue');
          }
        } else {
          const left = new Date(dueAtValue).getTime() - Date.now();
          timeLeftText = humanLeft(left);
          if (left < 0) {
            tr.classList.add('overdue');
            timeLeftClass = 'pulsating';
          } else if (left < 86400000) {
            tr.classList.add('warn');
          }
        }
      }
      const safeStatusAttr = escapeAttr(t.status);
      tr.innerHTML = `
  <td>${t.display_no || ''}</td>
  <td>${t.title || ''}</td>
  <td>${t.assignee || ''}</td>
  <td>
    ${dueAtValue ? fmtDT(dueAtValue) : '—'}
    <div class="small ${timeLeftClass} time-left" data-due="${dueAtValue}" data-status="${safeStatusAttr}" data-final="${final ? '1' : '0'}" data-overdue="${finalOverdue ? '1' : '0'}">
      ${timeLeftText}
    </div>
  </td>
  <td>${fmtDT(t.last_activity_at)}</td>
  <td>${fmtDT(t.created_at)}</td>
  <td>${fmtDT(t.closed_at)}</td>
  <td>${t.tag || ''}</td>
  <td>${t.status || ''}</td>
  <td><button class="btn btn-sm btn-outline-primary edit-btn" data-id="${t.id}">Открыть</button></td>
`;
      tbody.appendChild(tr);
    }

    if (!items.length) {
      const emptyRow = document.createElement('tr');
      emptyRow.innerHTML = '<td class="text-center text-muted" colspan="10">Задачи не найдены. Попробуйте изменить фильтры или создать новую задачу.</td>';
      tbody.appendChild(emptyRow);
    }

    if (totalCounter) totalCounter.textContent = state.total;
    if (shownCounter) shownCounter.textContent = items.length;
    const totalPages = Math.max(1, Math.ceil(Math.max(state.total, 1) / state.page_size));
    if (state.page > totalPages) state.page = totalPages;
    if (summaryEl) {
      summaryEl.textContent = state.total
        ? `Показано ${items.length} из ${state.total} · Страница ${state.page}/${totalPages}`
        : 'Нет задач, подходящих под условия фильтра.';
    }
    updateOverdueTasks();
    renderPager();
    markSortHeader();
  }

function updateOverdueTasks() {
  const now = Date.now();
  document.querySelectorAll('#tasksTable tbody tr').forEach(row => {
    const status = row.dataset.status || '';
    const dueAt = row.dataset.dueAt || '';
    const timeEl = row.querySelector('.time-left');
    const final = isFinalStatus(status);
    if (!timeEl) return;

    if (!dueAt) {
      row.classList.remove('overdue', 'warn');
      timeEl.classList.remove('pulsating');
      timeEl.classList.add('text-muted');
      timeEl.textContent = '';
      return;
    }

    if (final) {
      const isFinalOverdue = timeEl.dataset.overdue === '1';
      row.classList.toggle('overdue', isFinalOverdue);
      row.classList.remove('warn');
      timeEl.classList.remove('pulsating');
      timeEl.classList.toggle('text-danger', isFinalOverdue);
      timeEl.classList.toggle('fw-semibold', isFinalOverdue);
      timeEl.classList.toggle('text-muted', !isFinalOverdue);
      return;
    }

    const diff = new Date(dueAt).getTime() - now;
    const isOverdue = diff < 0;
    const isWarn = !isOverdue && diff < 86400000;

    row.classList.toggle('overdue', isOverdue);
    row.classList.toggle('warn', isWarn);
    timeEl.classList.toggle('pulsating', isOverdue);
    timeEl.classList.toggle('text-muted', !isOverdue);
    timeEl.textContent = humanLeft(diff);
  });
}

// Обновлять каждую минуту
setInterval(updateOverdueTasks, 60000);

  function renderPager() {
    const totalPages = Math.max(1, Math.ceil(state.total / state.page_size));
    pager.innerHTML = '';
    if (totalPages <= 1) return;
    const mkItem = (p, label, disabled = false, active = false) => {
      const li = document.createElement('li');
      li.className = `page-item ${disabled ? 'disabled' : ''} ${active ? 'active' : ''}`;
      li.innerHTML = `<a class="page-link" href="#">${label}</a>`;
      li.addEventListener('click', (e) => { e.preventDefault(); if (!disabled) { state.page = p; load(); } });
      return li;
    };
    pager.appendChild(mkItem(Math.max(1, state.page - 1), '«', state.page === 1));
    const start = Math.max(1, state.page - 2);
    const end = Math.min(totalPages, start + 4);
    for (let p = start; p <= end; p++) pager.appendChild(mkItem(p, String(p), false, p === state.page));
    pager.appendChild(mkItem(Math.min(totalPages, state.page + 1), '»', state.page === totalPages));
  }

  function markSortHeader() {
    table.querySelectorAll('thead th.sortable').forEach(th => {
      const key = th.dataset.sort;
      const ind = th.querySelector('.sort-ind');
      if (!ind) return;
      ind.textContent = (state.sort_by === key) ? (state.sort_dir === 'asc' ? '↑' : '↓') : '↕';
    });
  }

  // сортировка
  table.querySelectorAll('thead th.sortable').forEach(th => {
    th.addEventListener('click', () => {
      const key = th.dataset.sort;
      if (state.sort_by === key) {
        state.sort_dir = (state.sort_dir === 'asc') ? 'desc' : 'asc';
      } else {
        state.sort_by = key;
        state.sort_dir = 'asc';
      }
      state.page = 1;
      load();
    });
  });

  // фильтры
  if (filters) {
    filters.addEventListener('submit', (e) => { e.preventDefault(); state.page = 1; load().then(() => filtersModal && filtersModal.hide()); });
    const reset = document.getElementById('resetFilters');
    if (reset) reset.addEventListener('click', () => { filters.reset(); state.page = 1; load().then(() => filtersModal && filtersModal.hide()); });
    const filtersBtn = document.getElementById('filtersBtn');
    if (filtersBtn && filtersModal) filtersBtn.addEventListener('click', () => filtersModal.show());
  }
  if (pageSizeSel) pageSizeSel.addEventListener('change', () => { state.page_size = parseInt(pageSizeSel.value, 10) || 20; state.page = 1; load(); });

  // экспорт
  if (exportBtn) {
    exportBtn.addEventListener('click', (e) => {
      e.preventDefault();
      const p = new URLSearchParams(filters ? new FormData(filters) : undefined);
      p.set('sort_by', state.sort_by);
      p.set('sort_dir', state.sort_dir);
      window.location.href = '/api/tasks/export?' + p.toString();
    });
  }

  // ===== модал / черновик / подтверждение закрытия
  const commentsBlock = document.getElementById('commentsBlock');
  const commentEditor = document.getElementById('commentEditor');
  const sendCommentBtn = document.getElementById('sendCommentBtn');

  function handleCommentPaste(e) {
    if (!commentEditor) return;
    const items = (e.clipboardData || e.originalEvent?.clipboardData || {}).items || [];
    let handledImage = false;
    for (const it of items) {
      if (it.type && it.type.indexOf('image') === 0) {
        if (!handledImage) {
          e.preventDefault();
          handledImage = true;
        }
        const file = it.getAsFile();
        const reader = new FileReader();
        reader.onload = function (evt) {
          const img = new Image();
          img.src = evt.target.result;
          img.style.maxWidth = '100%';
          commentEditor.appendChild(img);
        };
        reader.readAsDataURL(file);
      }
    }
  }

  let isDirty = false;
  const DRAFT_NEW_KEY = 'taskDraft_new';
  function setDirty() { isDirty = true; }
  ['title', 'creator', 'assignee', 'co', 'watchers', 'tag', 'due_at'].forEach(n => {
    if (form[n]) form[n].addEventListener('input', setDirty);
  });
  if (bodyEditor) bodyEditor.addEventListener('input', setDirty);
  const filesInput = document.getElementById('filesInput');
  if (filesInput) filesInput.addEventListener('change', setDirty);

  function saveDraft() {
    const data = {
      title: form.title?.value || '',
      body_html: bodyEditor?.innerHTML || '',
      creator: form.creator?.value || '',
      assignee: form.assignee?.value || '',
      co: form.co?.value || '',
      watchers: form.watchers?.value || '',
      tag: form.tag?.value || '',
      due_at: form.due_at?.value || '',
      status: form.dataset.status || ''
    };
    if (!form.id.value) localStorage.setItem(DRAFT_NEW_KEY, JSON.stringify(data));
    else localStorage.setItem('taskDraft_' + form.id.value, JSON.stringify(data));
  }
  function loadDraft(taskId) {
    const key = taskId ? ('taskDraft_' + taskId) : DRAFT_NEW_KEY;
    const raw = localStorage.getItem(key);
    if (!raw) return;
    try {
      const d = JSON.parse(raw);
      if (form.title) form.title.value = d.title || '';
      if (bodyEditor) bodyEditor.innerHTML = d.body_html || '';
      if (form.creator) form.creator.value = d.creator || form.creator.value || '';
      if (form.assignee) form.assignee.value = d.assignee || form.assignee.value || '';
      if (form.co) form.co.value = d.co || '';
      if (form.watchers) form.watchers.value = d.watchers || '';
      if (form.tag) form.tag.value = d.tag || '';
      if (form.due_at) form.due_at.value = d.due_at || '';
      form.dataset.status = d.status || '';
    } catch { }
    syncStatusButtons(form.dataset.status);
  }
  if (modalEl) setInterval(() => { if (modalEl.classList.contains('show')) saveDraft(); }, 2000);

  if (modalEl) {
      modalEl.addEventListener('hide.bs.modal', (e) => {
        if (skipDirtyConfirm) { skipDirtyConfirm = false; return; }
        if (isDirty) {
          e.preventDefault();
          showConfirmActionModal({
            title: 'Закрыть задачу',
            message: 'Есть несохранённые изменения. Закрыть без сохранения?',
            confirmText: 'Закрыть',
            confirmVariant: 'warning',
            icon: '⚠️',
            onConfirm: () => {
              hideTaskModal(true);
            },
          });
        }
      });
    modalEl.addEventListener('hidden.bs.modal', () => { skipDirtyConfirm = false; });
    modalEl.querySelectorAll('[data-task-close]').forEach(btn => {
      btn.addEventListener('click', (ev) => { ev.preventDefault(); requestModalClose(); });
    });
  }

  document.querySelectorAll('.task-status').forEach(b => {
    b.addEventListener('click', (e) => {
      e.preventDefault();
      syncStatusButtons(b.dataset.st);
      setDirty();
    });
  });
  if (form) syncStatusButtons(form.dataset.status || '');

  // создать новую
  const createBtn = document.getElementById('createTaskBtn');
  if (createBtn) createBtn.addEventListener('click', () => {
    form.reset();
    form.id.value = '';
    if (bodyEditor) bodyEditor.innerHTML = '';
    if (deleteBtn) deleteBtn.hidden = true;
    if (form.title) form.title.readOnly = false;
    if (bodyEditor) bodyEditor.setAttribute('contenteditable', 'true');

    // в новой задаче блок комментариев скрыт
    if (commentsBlock) commentsBlock.hidden = true;

    // авто-подстановка автора/исполнителя (если шаблон подставил — оставим)
    if (form.creator && !form.creator.value) form.creator.value = (document.body.dataset.userEmail || '');
    if (form.assignee && !form.assignee.value) form.assignee.value = form.creator.value;

    if (taskNumberEl) taskNumberEl.textContent = 'Новая задача';
    const createdAt = document.getElementById('createdAt');
    if (createdAt) createdAt.textContent = '—';
    const timeLeft = document.getElementById('timeLeft');
    if (timeLeft) timeLeft.textContent = '—';

    form.dataset.status = 'Новая';
    loadDraft(null);
    syncStatusButtons(form.dataset.status);
    isDirty = false;
    const inst = getModalInstance();
    if (inst) inst.show();
  });

  // открыть существующую
  async function openTaskModal(taskId) {
    if (!taskId) return;

    if (form.title) form.title.readOnly = true;
    if (bodyEditor) bodyEditor.setAttribute('contenteditable', 'false');
    const toggle = document.getElementById('editToggleBtn');
    if (toggle) toggle.onclick = (ev) => {
      ev.preventDefault();
      const ro = form.title.readOnly;
      form.title.readOnly = !ro;
      bodyEditor.setAttribute('contenteditable', ro ? 'true' : 'false');
    };

    const res = await httpJson(`/api/tasks/${taskId}`);
    const t = await res.json();

    form.id.value = t.id;
    if (form.title) form.title.value = t.title || '';
    if (bodyEditor) bodyEditor.innerHTML = t.body_html || '';
    if (form.creator) form.creator.value = t.creator || (document.body.dataset.userEmail || '');
    if (form.assignee) form.assignee.value = t.assignee || form.creator.value;
    if (form.co) form.co.value = (t.co || []).join(', ');
    if (form.watchers) form.watchers.value = (t.watchers || []).join(', ');
    if (form.tag) form.tag.value = t.tag || '';
    if (form.due_at) form.due_at.value = t.due_at ? t.due_at.replace('Z', '') : '';
    if (taskNumberEl) taskNumberEl.textContent = t.display_no ? `№${t.display_no}` : (t.id ? `№DL_${t.id}` : '');
    const createdAt = document.getElementById('createdAt');
    if (createdAt) createdAt.textContent = t.created_at ? (new Date(t.created_at)).toLocaleString() : '—';
    const timeLeft = document.getElementById('timeLeft');
    const isFinal = isFinalStatus(t.status);
    if (timeLeft) timeLeft.textContent = t.due_at ? (isFinal ? '—' : humanLeft(new Date(t.due_at) - Date.now())) : '—';

    syncStatusButtons(t.status || '');

    const comm = document.getElementById('comments');
    if (comm) {
      comm.innerHTML = (t.comments || []).map(c => `
        <div class="mb-2">
          <div class="small text-muted">${new Date(c.created_at).toLocaleString()} • ${c.author || ''}</div>
          <div>${c.html || ''}</div>
        </div>`).join('');
    }
    const hist = document.getElementById('history');
    if (hist) {
      hist.innerHTML = (t.history || []).map(h => `
        <div class="mb-1 small text-muted">${new Date(h.at).toLocaleString()} — ${h.text}</div>
      `).join('');
    }

    if (commentsBlock) commentsBlock.hidden = false;
    if (sendCommentBtn && commentEditor) {
      sendCommentBtn.onclick = async (ev) => {
        ev.preventDefault();
        const html = commentEditor.innerHTML.trim();
        if (!html) return;
        const fd = new FormData();
        fd.append('html', html);
        const r = await httpJson(`/api/tasks/${form.id.value}/comments`, { method: 'POST', body: fd });
        const data = await r.json();
        if (data && data.ok) {
          commentEditor.innerHTML = '';
          const c = data.item;
          const wrap = document.createElement('div');
          wrap.className = 'mb-2';
          wrap.innerHTML = `
            <div class="small text-muted">${new Date(c.created_at).toLocaleString()} • ${c.author || ''}</div>
            <div>${c.html || ''}</div>
          `;
          document.getElementById('comments').appendChild(wrap);
        }
      };
      if (!commentEditor.dataset.pasteHandlerAttached) {
        commentEditor.addEventListener('paste', handleCommentPaste);
        commentEditor.dataset.pasteHandlerAttached = '1';
      }
    }

    loadDraft(t.id);
    syncStatusButtons(form.dataset.status || t.status || '');
    if (deleteBtn) deleteBtn.hidden = false;
    isDirty = false;
    const inst = getModalInstance();
    if (inst) inst.show();
  }

  // открыть существующую
  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('.edit-btn');
    if (!btn) return;
    await openTaskModal(btn.dataset.id);
  });

  // вставка картинок в тело задачи из буфера
  if (bodyEditor) bodyEditor.addEventListener('paste', (e) => {
    const items = (e.clipboardData || e.originalEvent?.clipboardData || {}).items || [];
    let handledImage = false;
    for (const it of items) {
      if (it.type && it.type.indexOf("image") === 0) {
        if (!handledImage) {
          e.preventDefault();
          handledImage = true;
        }
        const file = it.getAsFile();
        const reader = new FileReader();
        reader.onload = function (evt) {
          const img = new Image();
          img.src = evt.target.result;
          img.style.maxWidth = '100%';
          bodyEditor.appendChild(img);
          setDirty();
        };
        reader.readAsDataURL(file);
      }
    }
  });

  // сохранить
  const saveBtn = document.getElementById('saveTaskBtn');
  if (saveBtn) saveBtn.addEventListener('click', async () => {
    const fd = new FormData();
    fd.append('id', form.id.value);
    fd.append('title', form.title?.value || '');
    fd.append('body_html', bodyEditor?.innerHTML || '');
    fd.append('creator', form.creator?.value || '');
    fd.append('assignee', form.assignee?.value || '');
    fd.append('co', form.co?.value || '');
    fd.append('watchers', form.watchers?.value || '');
    fd.append('tag', form.tag?.value || '');
    fd.append('due_at', form.due_at?.value || '');
    fd.append('status', form.dataset.status || '');

    const files = filesInput?.files || [];
    for (let i = 0; i < files.length; i++) fd.append('files', files[i]);

    try {
      const r = await httpJson('/api/tasks', { method: 'POST', body: fd });
      const data = await r.json();
        if (data && data.ok) {
          localStorage.removeItem('taskDraft_' + (form.id.value || 'new'));
          isDirty = false;
          hideTaskModal(true);
          await load();
        } else {
          showAppModalMessage({
            title: 'Ошибка сохранения',
            message: data?.error || 'Ошибка сохранения',
            variant: 'danger',
          });
        }
    } catch (e) {
      // httpJson сам обработает 401
    }
  });

  // удаление
  if (deleteBtn) deleteBtn.addEventListener('click', async () => {
      if (!form.id.value) return;
      const confirmed = await showConfirmActionModal({
        title: 'Удаление задачи',
        message: 'Удалить задачу?',
        confirmText: 'Удалить',
        confirmVariant: 'danger',
        icon: '🗑️',
      });
      if (!confirmed) return;
    const r = await httpJson('/api/tasks/' + form.id.value, { method: 'DELETE' });
    const data = await r.json();
    if (data && data.ok) {
      localStorage.removeItem('taskDraft_' + form.id.value);
      isDirty = false;
      hideTaskModal(true);
      load();
      } else {
        showAppModalMessage({
          title: 'Ошибка удаления',
          message: data?.error || 'Не удалось удалить задачу',
          variant: 'danger',
        });
      }
  });

  // открыть задачу из hash: #task=123
  async function openFromHash() {
    const m = (location.hash || '').match(/#task=(\d+)/);
    if (!m) return;
    await openTaskModal(m[1]);
  }
  window.addEventListener('hashchange', openFromHash);
  openFromHash();

  // стартовая загрузка
  load();

})();
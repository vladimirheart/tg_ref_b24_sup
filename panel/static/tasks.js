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

  // элементы могут отсутствовать — страхуемся
  const filtersModal = (typeof bootstrap !== 'undefined' && filtersModalEl)
    ? new bootstrap.Modal(filtersModalEl)
    : null;

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
  async function httpJson(url, opts = {}) {
    const r = await fetch(url, { credentials: 'same-origin', ...opts });
    if (r.status === 401) {
      let msg = 'Сессия истекла или нет доступа. Пожалуйста, войдите заново.';
      try { const data = await r.json(); if (data && data.message) msg = 'Сессия истекла. Пожалуйста, войдите заново.'; } catch {}
      if (!location.pathname.includes('/login')) { alert(msg); location.href = '/login'; }
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

    tbody.innerHTML = '';
    for (const t of (data.items || [])) {
      const tr = document.createElement('tr');

      // подсветка строк по дедлайну
      let cls = '';
      if (t.due_at) {
        const left = new Date(t.due_at).getTime() - Date.now();
        if (left < 0) cls = 'overdue';
        else if (left < 86400000) cls = 'warn';
      }
      tr.className = cls;

	  tr.innerHTML = `
  <td>${t.display_no}</td>
  <td>${t.title || ''}</td>
  <td>${t.assignee || ''}</td>
  <td>
    ${t.due_at ? fmtDT(t.due_at) : '—'}
    <div class="small ${cls === 'overdue' ? 'pulsating' : 'text-muted'}">
      ${t.due_at ? humanLeft(new Date(t.due_at) - Date.now()) : ''}
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
    renderPager();
    markSortHeader();
  }

function updateOverdueTasks() {
  document.querySelectorAll('.overdue .pulsating').forEach(el => {
    const taskRow = el.closest('tr');
    const dueText = el.textContent;
    // Если текст содержит "просрочено" или начинается с минуса, обновляем
    if (dueText.includes('просрочено') || dueText.startsWith('-')) {
      // Можно добавить логику для обновления времени, если нужно
      // Пока просто поддерживаем анимацию
    }
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
  }
  if (modalEl) setInterval(() => { if (modalEl.classList.contains('show')) saveDraft(); }, 2000);

  if (modalEl) {
    modalEl.addEventListener('hide.bs.modal', (e) => {
      if (!isDirty) return;
      if (!confirm('Есть несохранённые изменения. Закрыть без сохранения?')) e.preventDefault();
    });
  }

  document.querySelectorAll('.task-status').forEach(b => {
    b.addEventListener('click', (e) => {
      e.preventDefault();
      form.dataset.status = b.dataset.st;
      document.querySelectorAll('.task-status').forEach(x => x.classList.remove('active'));
      b.classList.add('active');
      setDirty();
    });
  });

  // создать новую
  const createBtn = document.getElementById('createTaskBtn');
  if (createBtn) createBtn.addEventListener('click', () => {
    form.reset();
    form.id.value = '';
    if (bodyEditor) bodyEditor.innerHTML = '';
    if (deleteBtn) deleteBtn.hidden = true;

    // в новой задаче блок комментариев скрыт
    if (commentsBlock) commentsBlock.hidden = true;

    // авто-подстановка автора/исполнителя (если шаблон подставил — оставим)
    if (form.creator && !form.creator.value) form.creator.value = (document.body.dataset.userEmail || '');
    if (form.assignee && !form.assignee.value) form.assignee.value = form.creator.value;

    loadDraft(null);
    isDirty = false;
    if (typeof bootstrap !== 'undefined' && modalEl) new bootstrap.Modal(modalEl).show();
  });

  // открыть существующую
  document.addEventListener('click', async (e) => {
    const btn = e.target.closest('.edit-btn');
    if (!btn) return;
    const id = btn.dataset.id;

    // поля названия/тела — только для чтения до нажатия «Редактировать задачу»
    if (form.title) form.title.readOnly = true;
    if (bodyEditor) bodyEditor.setAttribute('contenteditable', 'false');
    const toggle = document.getElementById('editToggleBtn');
    if (toggle) toggle.onclick = (ev) => {
      ev.preventDefault();
      const ro = form.title.readOnly;
      form.title.readOnly = !ro;
      bodyEditor.setAttribute('contenteditable', ro ? 'true' : 'false');
    };

    const res = await httpJson(`/api/tasks/${id}`);
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
    const createdAt = document.getElementById('createdAt');
    if (createdAt) createdAt.textContent = t.created_at ? (new Date(t.created_at)).toLocaleString() : '—';
    const timeLeft = document.getElementById('timeLeft');
    if (timeLeft) timeLeft.textContent = t.due_at ? humanLeft(new Date(t.due_at) - Date.now()) : '—';

    // комментарии
    const comm = document.getElementById('comments');
    if (comm) {
      comm.innerHTML = (t.comments || []).map(c => `
        <div class="mb-2">
          <div class="small text-muted">${new Date(c.created_at).toLocaleString()} • ${c.author || ''}</div>
          <div>${c.html || ''}</div>
        </div>`).join('');
    }
    // история (сворачиваемая)
    const hist = document.getElementById('history');
    if (hist) {
      hist.innerHTML = (t.history || []).map(h => `
        <div class="mb-1 small text-muted">${new Date(h.at).toLocaleString()} — ${h.text}</div>
      `).join('');
    }

    // комментарии доступны только для существующей задачи
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
      // вставка изображений в комментарий из буфера
      commentEditor.addEventListener('paste', (e) => {
        const items = (e.clipboardData || e.originalEvent?.clipboardData || {}).items || [];
        for (const it of items) {
          if (it.type && it.type.indexOf("image") === 0) {
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
      });
    }

    // черновик редактирования
    loadDraft(t.id);
    if (deleteBtn) deleteBtn.hidden = false;
    isDirty = false;
    if (typeof bootstrap !== 'undefined' && modalEl) new bootstrap.Modal(modalEl).show();
  });

  // вставка картинок в тело задачи из буфера
  if (bodyEditor) bodyEditor.addEventListener('paste', (e) => {
    const items = (e.clipboardData || e.originalEvent?.clipboardData || {}).items || [];
    for (const it of items) {
      if (it.type && it.type.indexOf("image") === 0) {
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
        if (typeof bootstrap !== 'undefined' && modalEl) new bootstrap.Modal(modalEl).hide();
        await load();
      } else {
        alert(data?.error || 'Ошибка сохранения');
      }
    } catch (e) {
      // httpJson сам обработает 401
    }
  });

  // удаление
  if (deleteBtn) deleteBtn.addEventListener('click', async () => {
    if (!form.id.value) return;
    if (!confirm('Удалить задачу?')) return;
    const r = await httpJson('/api/tasks/' + form.id.value, { method: 'DELETE' });
    const data = await r.json();
    if (data && data.ok) {
      localStorage.removeItem('taskDraft_' + form.id.value);
      isDirty = false;
      if (typeof bootstrap !== 'undefined' && modalEl) new bootstrap.Modal(modalEl).hide();
      load();
    } else {
      alert('Ошибка удаления');
    }
  });

  // открыть задачу из hash: #task=123
  async function openFromHash() {
    const m = (location.hash || '').match(/#task=(\d+)/);
    if (!m) return;
    const id = m[1];
    const r = await httpJson(`/api/tasks/${id}`);
    const t = await r.json();

    // переиспользуем логику из «открыть существующую» (сокращённая версия)
    form.id.value = t.id;
    if (form.title) form.title.value = t.title || '';
    if (bodyEditor) bodyEditor.innerHTML = t.body_html || '';
    if (form.creator) form.creator.value = t.creator || (document.body.dataset.userEmail || '');
    if (form.assignee) form.assignee.value = t.assignee || form.creator.value;
    if (form.co) form.co.value = (t.co || []).join(', ');
    if (form.watchers) form.watchers.value = (t.watchers || []).join(', ');
    if (form.tag) form.tag.value = t.tag || '';
    if (form.due_at) form.due_at.value = t.due_at ? t.due_at.replace('Z', '') : '';
    const createdAt = document.getElementById('createdAt');
    if (createdAt) createdAt.textContent = t.created_at ? (new Date(t.created_at)).toLocaleString() : '—';
    const timeLeft = document.getElementById('timeLeft');
    if (timeLeft) timeLeft.textContent = t.due_at ? humanLeft(new Date(t.due_at) - Date.now()) : '—';

    if (typeof bootstrap !== 'undefined' && modalEl) new bootstrap.Modal(modalEl).show();
  }
  window.addEventListener('hashchange', openFromHash);
  openFromHash();

  // стартовая загрузка
  load();

})();

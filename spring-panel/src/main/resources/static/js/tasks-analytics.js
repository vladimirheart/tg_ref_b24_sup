(function () {
  const view = document.getElementById('taskAnalyticsView');
  const form = document.getElementById('taskAnalyticsFilters');
  const projectSelect = document.getElementById('taskAnalyticsProject');
  const summary = document.getElementById('taskAnalyticsSummary');
  const alertBox = document.getElementById('taskAnalyticsAlert');
  const metricGrid = document.getElementById('taskAnalyticsMetrics');
  const statusList = document.getElementById('taskAnalyticsStatusBreakdown');
  const assigneeList = document.getElementById('taskAnalyticsAssigneeBreakdown');
  const sourceList = document.getElementById('taskAnalyticsSourceBreakdown');
  const eventList = document.getElementById('taskAnalyticsEvents');
  const timeInStatusList = document.getElementById('taskAnalyticsTimeInStatus');
  const notes = document.getElementById('taskAnalyticsNotes');
  const savedViewsSelect = document.getElementById('taskAnalyticsSavedView');
  const saveViewBtn = document.getElementById('taskAnalyticsSaveViewBtn');
  const deleteViewBtn = document.getElementById('taskAnalyticsDeleteViewBtn');
  const exportBtn = document.getElementById('taskAnalyticsExportBtn');
  if (!view || !form || !metricGrid) return;

  const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content') || '';
  const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content') || 'X-XSRF-TOKEN';
  let projectsLoaded = false;
  let savedViewsLoaded = false;
  let savedViews = [];
  let loading = false;

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function requestOptions(options = {}) {
    const merged = { credentials: 'same-origin', ...options };
    const method = String(merged.method || 'GET').toUpperCase();
    if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method) || !csrfToken) return merged;
    const headers = new Headers(merged.headers || {});
    headers.set(csrfHeader, csrfToken);
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

  function isoDate(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }

  function ensureDefaultPeriod() {
    const from = form.elements.namedItem('from');
    const to = form.elements.namedItem('to');
    if (!from || !to || from.value || to.value) return;
    const today = new Date();
    const start = new Date(today);
    start.setDate(start.getDate() - 29);
    from.value = isoDate(start);
    to.value = isoDate(today);
  }

  async function loadProjects() {
    if (projectsLoaded || !projectSelect) return;
    const payload = await httpJson('/api/projects');
    const projects = Array.isArray(payload.items) ? payload.items : [];
    projectSelect.innerHTML = '<option value="">Все проекты</option>';
    for (const project of projects) {
      const option = document.createElement('option');
      option.value = String(project.id);
      option.textContent = `${project.project_key || ''} · ${project.name || ''}`.replace(/^ · /, '');
      projectSelect.appendChild(option);
    }
    const requestedProject = new URLSearchParams(window.location.search).get('project');
    if (requestedProject && projects.some(project => String(project.id) === requestedProject)) {
      projectSelect.value = requestedProject;
    }
    projectsLoaded = true;
  }

  function currentFilters() {
    const result = {};
    new FormData(form).forEach((value, key) => {
      const text = String(value || '').trim();
      if (text) result[key] = text;
    });
    return result;
  }

  function currentQuery() {
    const query = new URLSearchParams();
    Object.entries(currentFilters()).forEach(([key, value]) => query.set(key, value));
    return query;
  }

  function applyFilters(filters) {
    const values = filters && typeof filters === 'object' ? filters : {};
    Array.from(form.elements).forEach(element => {
      if (!element.name) return;
      element.value = Object.prototype.hasOwnProperty.call(values, element.name)
        ? String(values[element.name] ?? '')
        : '';
    });
    ensureDefaultPeriod();
  }

  async function loadSavedViews(force = false) {
    if (!savedViewsSelect || (savedViewsLoaded && !force)) return;
    const payload = await httpJson('/api/task-analytics/views');
    savedViews = Array.isArray(payload.items) ? payload.items : [];
    const selected = savedViewsSelect.value;
    savedViewsSelect.innerHTML = '<option value="">Сохранённые представления</option>';
    for (const item of savedViews) {
      const option = document.createElement('option');
      option.value = String(item.id);
      option.textContent = item.name || `View ${item.id}`;
      savedViewsSelect.appendChild(option);
    }
    if (selected && savedViews.some(item => String(item.id) === selected)) savedViewsSelect.value = selected;
    savedViewsLoaded = true;
    updateSavedViewActions();
  }

  function selectedSavedView() {
    const id = String(savedViewsSelect?.value || '');
    return savedViews.find(item => String(item.id) === id) || null;
  }

  function updateSavedViewActions() {
    if (deleteViewBtn) deleteViewBtn.disabled = !selectedSavedView();
  }

  async function saveCurrentView() {
    const current = selectedSavedView();
    const proposed = window.prompt('Название аналитического представления', current?.name || '');
    if (proposed == null || !proposed.trim()) return;
    clearError();
    try {
      const payload = { name: proposed.trim(), filters: currentFilters() };
      const url = current ? `/api/task-analytics/views/${encodeURIComponent(current.id)}` : '/api/task-analytics/views';
      const method = current ? 'PATCH' : 'POST';
      const response = await httpJson(url, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      });
      savedViewsLoaded = false;
      await loadSavedViews(true);
      if (response?.view?.id && savedViewsSelect) savedViewsSelect.value = String(response.view.id);
      updateSavedViewActions();
    } catch (error) {
      showError(error);
    }
  }

  async function deleteCurrentView() {
    const current = selectedSavedView();
    if (!current) return;
    const accepted = typeof showConfirmActionModal === 'function'
      ? await showConfirmActionModal({
          title: 'Удалить представление',
          message: `Удалить «${current.name || 'аналитическое представление'}»?`,
          confirmText: 'Удалить',
          confirmVariant: 'danger',
        })
      : window.confirm(`Удалить «${current.name || 'аналитическое представление'}»?`);
    if (!accepted) return;
    clearError();
    try {
      await httpJson(`/api/task-analytics/views/${encodeURIComponent(current.id)}`, { method: 'DELETE' });
      if (savedViewsSelect) savedViewsSelect.value = '';
      savedViewsLoaded = false;
      await loadSavedViews(true);
    } catch (error) {
      showError(error);
    }
  }

  function exportCurrentSlice() {
    const query = currentQuery();
    const link = document.createElement('a');
    link.href = `/api/task-analytics/export.csv?${query.toString()}`;
    link.rel = 'noopener';
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  function formatHours(value) {
    if (value == null || !Number.isFinite(Number(value))) return '—';
    const hours = Number(value);
    if (hours < 24) return `${hours.toFixed(hours < 10 ? 1 : 0)} ч`;
    return `${(hours / 24).toFixed(1)} дн`;
  }

  function metric(label, value, meta) {
    return `<article class="tasks-analytics-metric"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong><small>${escapeHtml(meta || '')}</small></article>`;
  }

  function renderMetrics(payload) {
    const m = payload?.metrics || {};
    const eligible = Number(m.time_in_status_eligible_tasks || 0);
    const total = Number(m.time_in_status_total_tasks || 0);
    metricGrid.innerHTML = [
      metric('Всего задач', Number(m.tasks_total || 0), 'Текущий срез'),
      metric('Открыто', Number(m.open_tasks || 0), `Просрочено: ${Number(m.overdue_open || 0)}`),
      metric('Создано', Number(m.created_in_period || 0), 'За выбранный период'),
      metric('Throughput', Number(m.throughput || 0), 'Задач завершено за период'),
      metric('Reopen', Number(m.reopened || 0), 'Возвратов из финального статуса'),
      metric('Avg lead', formatHours(m.avg_lead_hours), `Выборка: ${Number(m.lead_samples || 0)}`),
      metric('Avg cycle', formatHours(m.avg_cycle_hours), `Выборка: ${Number(m.cycle_samples || 0)}`),
      metric('Time-in-status', `${eligible}/${total}`, 'Задач с полной timeline'),
      metric('Смен статуса', Number(m.status_change_events || 0), 'Event-based')
    ].join('');
  }

  function renderBreakdown(target, items, emptyText) {
    if (!target) return;
    const list = Array.isArray(items) ? items : [];
    if (!list.length) {
      target.innerHTML = `<div class="tasks-analytics-empty">${escapeHtml(emptyText)}</div>`;
      return;
    }
    const max = Math.max(...list.map(item => Number(item.count || 0)), 1);
    target.innerHTML = list.slice(0, 12).map(item => {
      const count = Number(item.count || 0);
      const width = Math.max(4, Math.round((count / max) * 100));
      return `<div class="tasks-analytics-breakdown-row"><span>${escapeHtml(item.key || '—')}</span><div><i style="width:${width}%"></i></div><strong>${count}</strong></div>`;
    }).join('');
  }

  function renderTimeInStatus(items) {
    if (!timeInStatusList) return;
    const list = Array.isArray(items) ? items : [];
    if (!list.length) {
      timeInStatusList.innerHTML = '<div class="tasks-analytics-empty">Пока нет задач с полной status timeline.</div>';
      return;
    }
    const max = Math.max(...list.map(item => Number(item.total_hours || 0)), 1);
    timeInStatusList.innerHTML = list.slice(0, 12).map(item => {
      const total = Number(item.total_hours || 0);
      const width = Math.max(4, Math.round((total / max) * 100));
      return `<div class="tasks-analytics-breakdown-row tasks-analytics-breakdown-row--hours"><span>${escapeHtml(item.key || '—')}</span><div><i style="width:${width}%"></i></div><strong>${escapeHtml(formatHours(total))}</strong><small>avg ${escapeHtml(formatHours(item.avg_hours))} · ${Number(item.samples || 0)}</small></div>`;
    }).join('');
  }

  function render(payload) {
    renderMetrics(payload);
    renderBreakdown(statusList, payload.status_breakdown, 'Нет данных по статусам');
    renderBreakdown(assigneeList, payload.assignee_breakdown, 'Нет данных по исполнителям');
    renderBreakdown(sourceList, payload.source_breakdown, 'Нет данных по источникам');
    renderBreakdown(eventList, payload.event_breakdown, 'За период событий нет');
    renderTimeInStatus(payload.time_in_status_breakdown);
    const period = payload.period || {};
    if (summary) summary.textContent = `Период ${period.from || '—'} — ${period.to || '—'} · ${Number(payload?.metrics?.tasks_total || 0)} задач в срезе`;
    if (notes) {
      const list = Array.isArray(payload.notes) ? payload.notes : [];
      notes.innerHTML = list.map(item => `<li>${escapeHtml(item)}</li>`).join('');
    }
  }

  function showError(error) {
    if (!alertBox) return;
    alertBox.textContent = error instanceof Error ? error.message : String(error || 'Неизвестная ошибка');
    alertBox.classList.remove('d-none');
  }

  function clearError() {
    if (!alertBox) return;
    alertBox.textContent = '';
    alertBox.classList.add('d-none');
  }

  async function loadAnalytics() {
    if (loading || view.hidden) return;
    loading = true;
    clearError();
    try {
      ensureDefaultPeriod();
      await Promise.all([loadProjects(), loadSavedViews()]);
      const query = currentQuery();
      const payload = await httpJson(`/api/task-analytics?${query.toString()}`);
      render(payload);
    } catch (error) {
      showError(error);
    } finally {
      loading = false;
    }
  }

  form.addEventListener('submit', event => {
    event.preventDefault();
    loadAnalytics();
  });
  document.getElementById('taskAnalyticsResetBtn')?.addEventListener('click', () => {
    form.reset();
    if (savedViewsSelect) savedViewsSelect.value = '';
    updateSavedViewActions();
    ensureDefaultPeriod();
    loadAnalytics();
  });
  savedViewsSelect?.addEventListener('change', () => {
    const selected = selectedSavedView();
    updateSavedViewActions();
    if (!selected) return;
    applyFilters(selected.filters || {});
    loadAnalytics();
  });
  saveViewBtn?.addEventListener('click', saveCurrentView);
  deleteViewBtn?.addEventListener('click', deleteCurrentView);
  exportBtn?.addEventListener('click', exportCurrentSlice);
  window.addEventListener('tasks:analytics-activate', loadAnalytics);

  ensureDefaultPeriod();
  updateSavedViewActions();
  window.__tasksAnalyticsReady = true;
})();

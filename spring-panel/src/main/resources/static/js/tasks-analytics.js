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
  const notes = document.getElementById('taskAnalyticsNotes');
  if (!view || !form || !metricGrid) return;

  let projectsLoaded = false;
  let loading = false;

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  async function httpJson(url) {
    const response = await fetch(url, { credentials: 'same-origin' });
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
    metricGrid.innerHTML = [
      metric('Всего задач', Number(m.tasks_total || 0), 'Текущий срез'),
      metric('Открыто', Number(m.open_tasks || 0), `Просрочено: ${Number(m.overdue_open || 0)}`),
      metric('Создано', Number(m.created_in_period || 0), 'За выбранный период'),
      metric('Throughput', Number(m.throughput || 0), 'Задач завершено за период'),
      metric('Reopen', Number(m.reopened || 0), 'Возвратов из финального статуса'),
      metric('Avg lead', formatHours(m.avg_lead_hours), `Выборка: ${Number(m.lead_samples || 0)}`),
      metric('Avg cycle', formatHours(m.avg_cycle_hours), `Выборка: ${Number(m.cycle_samples || 0)}`),
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

  function render(payload) {
    renderMetrics(payload);
    renderBreakdown(statusList, payload.status_breakdown, 'Нет данных по статусам');
    renderBreakdown(assigneeList, payload.assignee_breakdown, 'Нет данных по исполнителям');
    renderBreakdown(sourceList, payload.source_breakdown, 'Нет данных по источникам');
    renderBreakdown(eventList, payload.event_breakdown, 'За период событий нет');
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
      await loadProjects();
      const query = new URLSearchParams(new FormData(form));
      Array.from(query.keys()).forEach(key => {
        if (!String(query.get(key) || '').trim()) query.delete(key);
      });
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
    ensureDefaultPeriod();
    loadAnalytics();
  });
  window.addEventListener('tasks:analytics-activate', loadAnalytics);

  ensureDefaultPeriod();
  window.__tasksAnalyticsReady = true;
})();

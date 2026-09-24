(function () {
  if (window.PassportDetailActivityRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getCases = typeof options.getCases === 'function' ? options.getCases : () => [];
    const getTasks = typeof options.getTasks === 'function' ? options.getTasks : () => [];
    const getIncidents = typeof options.getIncidents === 'function' ? options.getIncidents : () => [];
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailActivityRuntime requires coreRuntime');
    }
    const { escapeHtml, first, statusTone } = coreRuntime;

    function resolveList(getter) {
      const value = getter();
      return Array.isArray(value) ? value : [];
    }

    function recordCard(item, kind) {
        const id = first(item && item.id, item && item.ticket_id, item && item.task_id, item && item.number, item && item.key, '');
        const title = first(item && item.title, item && item.subject, item && item.name, item && item.category, kind === 'task' ? 'Задача' : 'Обращение');
        const status = first(item && item.status, item && item.state, '');
        const date = first(item && item.created_at, item && item.createdAt, item && item.updated_at, item && item.date, '');
        const meta = [id ? `#${id}` : '', date].filter(Boolean).join(' · ');
        return `<div class="passport-record-card">
            <div><strong data-ui-ellipsis-reveal tabindex="0">${escapeHtml(title)}</strong>${meta ? `<span>${escapeHtml(meta)}</span>` : ''}</div>
            ${status ? `<span class="passport-record-status" data-tone="${statusTone(status)}">${escapeHtml(status)}</span>` : ''}
        </div>`;
    }

    function renderActivity() {
        const cases = resolveList(getCases);
        const tasks = resolveList(getTasks);
        const incidents = resolveList(getIncidents);
        const casesList = document.getElementById('passportCasesList');
        const tasksList = document.getElementById('passportTasksList');
        const incidentsList = document.getElementById('passportIncidentsList');

        casesList.innerHTML = cases.map((item) => recordCard(item, 'case')).join('');
        tasksList.innerHTML = tasks.map((item) => recordCard(item, 'task')).join('');
        document.getElementById('passportCasesEmpty').classList.toggle('d-none', cases.length > 0);
        document.getElementById('passportTasksEmpty').classList.toggle('d-none', tasks.length > 0);

        if (incidents.length) {
            document.getElementById('passportIncidentsHeading').classList.remove('d-none');
            incidentsList.innerHTML = incidents.map((item) => recordCard(item, 'incident')).join('');
        }
    }


    return Object.freeze({
      renderActivity,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailActivityRuntime = Object.freeze({
    mount,
  });
}());

(function () {
  'use strict';
  const modalNode = document.getElementById('incidentResolutionModal');
  if (!modalNode) return;
  const comment = document.getElementById('incidentResolutionComment');
  let pending = null, accepted = null;
  document.getElementById('incidentResolutionForm').addEventListener('submit', event => {
    event.preventDefault();
    comment.setCustomValidity(comment.value.trim() ? '' : 'Опишите, что конкретно сделано.');
    if (!event.target.reportValidity()) return;
    accepted = comment.value.trim(); bootstrap.Modal.getOrCreateInstance(modalNode).hide();
  });
  comment.addEventListener('input', () => comment.setCustomValidity(''));
  modalNode.addEventListener('shown.bs.modal', () => comment.focus());
  modalNode.addEventListener('hidden.bs.modal', () => { const resolve = pending; pending = null; resolve?.(accepted); });
  window.IncidentWorkbenchUi = {
    requestResolution(incident, status) {
      if (pending) return Promise.resolve(null);
      accepted = null; comment.value = ''; comment.setCustomValidity('');
      document.getElementById('incidentResolutionContext').textContent = `${incident.incident_key || incident.id} · ${incident.title || ''}`;
      document.getElementById('incidentResolutionLabel').textContent = status === 'closed' ? 'Закрыть инцидент' : 'Решение инцидента';
      return new Promise(resolve => { pending = resolve; bootstrap.Modal.getOrCreateInstance(modalNode).show(); });
    }
  };
  const labels = {
    incidentDetailTitle: 'Название', incidentDetailSummary: 'Краткое описание', incidentDetailSeverity: 'Приоритет', incidentDetailOwner: 'Ответственный', incidentDetailSource: 'Источник', incidentDetailDescription: 'Описание и ход решения',
    incidentWorkbenchQuery: 'Поиск', incidentWorkbenchStatusFilter: 'Статус', incidentWorkbenchSeverityFilter: 'Приоритет', incidentWorkbenchSignalTypeFilter: 'Направление', incidentWorkbenchLimitFilter: 'Показывать записей'
  };
  function decorate() {
    for (const [id, text] of Object.entries(labels)) {
      const control = document.getElementById(id);
      if (!control || control.closest('.incident-field')) continue;
      const field = document.createElement('div'); field.className = 'incident-field';
      const label = document.createElement('label'); label.htmlFor = id; label.textContent = text;
      control.before(field); field.append(label, control);
    }
    for (const id of ['incidentDetailOwner', 'incidentWatcherInput']) {
      const control = document.getElementById(id); if (control) control.dataset.employeePicker = 'true';
    }
    const routeType = document.getElementById('incidentRouteType'), routeTarget = document.getElementById('incidentRouteTarget');
    if (routeType && routeTarget && !document.getElementById('incidentRouteUserPicker')) {
      const people = {};
      for (const mode of ['user', 'users']) {
        const field = document.createElement('div'); field.className = 'incident-route-person-field';
        const input = document.createElement('input'); input.type = 'text'; input.id = mode === 'user' ? 'incidentRouteUserPicker' : 'incidentRouteUsersPicker';
        input.dataset.employeePicker = 'true'; input.dataset.employeeMultiple = String(mode === 'users'); input.setAttribute('aria-label', 'Получатель маршрута');
        field.append(input); routeTarget.after(field); people[mode] = { field, input };
        input.addEventListener('change', () => { if (routeType.value === mode) routeTarget.value = input.value; });
      }
      function toggle(clear) {
        const personal = ['user', 'users'].includes(routeType.value);
        routeTarget.hidden = personal;
        for (const [mode, value] of Object.entries(people)) { value.field.hidden = mode !== routeType.value; if (clear) value.input.value = ''; }
        if (clear) routeTarget.value = '';
        window.EmployeePicker?.refresh();
      }
      routeType.addEventListener('change', () => toggle(true)); toggle(false);
    }
    window.EmployeePicker?.refresh();
  }
  const detail = document.getElementById('incidentWorkbenchDetail');
  new MutationObserver(() => decorate()).observe(detail, { childList: true });
  decorate();

  const monitoringModal = document.getElementById('incidentMonitoringModal');
  const monitoringList = document.getElementById('incidentMonitoringList');
  const message = document.getElementById('incidentMonitoringStatus');
  let editable = false;
  async function request(url, options = {}) {
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const header = document.querySelector('meta[name="_csrf_header"]')?.content;
    const response = await fetch(url, { credentials: 'same-origin', ...options, headers: { Accept: 'application/json', ...(csrf && header ? { [header]: csrf } : {}), ...options.headers } });
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message || body.error || `Ошибка HTTP ${response.status}`); }
    return response.json();
  }
  function error(ex) { message.className = 'alert alert-danger'; message.textContent = ex.message; }
  function row(item) {
    const form = document.createElement('form'); form.className = 'incident-monitoring-row';
    const deviceZone = new Intl.DateTimeFormat().resolvedOptions().timeZone;
    const info = document.createElement('div'), title = document.createElement('strong'), description = document.createElement('p'), state = document.createElement('div');
    title.textContent = item.label; description.textContent = item.description;
    state.className = 'small'; state.textContent = item.suppressed ? (item.mode === 'paused' ? 'Пауза до ' + new Date(item.until).toLocaleString('ru-RU', { timeZone: deviceZone }) + ` (${deviceZone})` : 'Отключено бессрочно') : 'Включено';
    info.append(title, description, state);
    const controls = document.createElement('div'), select = document.createElement('select'); select.className = 'form-select form-select-sm'; select.setAttribute('aria-label', 'Режим: ' + item.label);
    for (const [value, text] of [['enabled', 'Включено'], ['hour', 'Пауза на час'], ['week', 'Пауза на неделю'], ['paused', 'Пауза до даты…'], ['disabled', 'Отключено бессрочно']]) { const option = new Option(text, value); select.add(option); }
    select.value = item.effective_mode;
    const until = document.createElement('input'); until.type = 'datetime-local'; until.className = 'form-control form-control-sm mb-2'; until.setAttribute('aria-label', 'Окончание паузы: ' + item.label);
    if (item.until) { const date = new Date(item.until); until.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
    const initialUntil = until.value;
    const zoneHint = document.createElement('div'); zoneHint.className = 'form-text mb-2'; zoneHint.textContent = 'Местное время: ' + deviceZone;
    const toggle = () => { until.hidden = zoneHint.hidden = select.value !== 'paused'; until.required = !until.hidden; }; select.addEventListener('change', toggle); toggle();
    const save = document.createElement('button'); save.type = 'submit'; save.className = 'btn btn-sm btn-outline-primary'; save.textContent = 'Применить';
    select.disabled = until.disabled = save.disabled = !editable;
    controls.append(select, until, zoneHint, save); form.append(info, controls);
    form.addEventListener('submit', async event => {
      event.preventDefault(); if (!editable || !form.reportValidity()) return;
      const mode = ['hour', 'week'].includes(select.value) ? 'paused' : select.value;
      const duration = select.value === 'hour' ? 60 : select.value === 'week' ? 10080 : null;
      save.disabled = true; message.textContent = ''; message.className = '';
      try {
        const data = await request('/api/incidents/monitoring/' + encodeURIComponent(item.id), { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ mode, ...(mode === 'paused' ? (duration ? { duration_minutes: duration } : { until: item.until && until.value === initialUntil ? item.until : new Date(until.value).toISOString() }) : {}) }) });
        const updated = data.items.find(value => value.id === item.id); form.replaceWith(row(updated));
        message.className = 'alert alert-success'; message.textContent = 'Сохранено: ' + item.label;
      } catch (ex) { error(ex); save.disabled = false; }
    });
    return form;
  }
  monitoringModal.addEventListener('show.bs.modal', async () => {
    message.className = 'text-muted'; message.textContent = 'Загрузка настроек…'; monitoringList.replaceChildren();
    try { const data = await request('/api/incidents/monitoring'); editable = data.can_edit === true; message.textContent = editable ? '' : 'Просмотр. Изменять настройки может администратор.'; monitoringList.replaceChildren(...data.items.map(row)); }
    catch (ex) { error(ex); }
  });
})();

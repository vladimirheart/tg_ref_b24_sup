/* Единый выбор сотрудников. Исходное поле и его server-provided scope остаются источником значения. */
(function () {
  'use strict';
  const mounted = new WeakMap();
  let sequence = 0, directory;
  const normalize = value => String(value || '').toLocaleLowerCase('ru').replace(/ё/g, 'е');
  const split = value => String(value || '').split(/[,;\n]+/).map(item => item.trim()).filter(Boolean);
  function mount(source) {
    if (mounted.has(source) || source.closest('.employee-picker')) return;
    const multiple = source.multiple || source.dataset.employeeMultiple === 'true';
    const wrapper = document.createElement('div'); wrapper.className = 'employee-picker';
    const trigger = document.createElement('button'); trigger.type = 'button'; trigger.className = 'form-select employee-picker__trigger';
    trigger.setAttribute('aria-haspopup', 'dialog'); trigger.setAttribute('aria-expanded', 'false');
    const label = document.querySelector(`label[for="${source.id}"]`)?.textContent || source.getAttribute('aria-label') || source.closest('div')?.querySelector('label')?.textContent || 'Сотрудники';
    trigger.setAttribute('aria-label', label.trim());
    const menu = document.createElement('div'); menu.className = 'employee-picker__menu'; menu.hidden = true;
    menu.id = 'employee-picker-' + ++sequence; menu.setAttribute('role', 'dialog'); menu.setAttribute('aria-label', label.trim());
    trigger.setAttribute('aria-controls', menu.id);
    const search = document.createElement('input'); search.type = 'search'; search.className = 'form-control form-control-sm'; search.placeholder = 'Поиск сотрудника'; search.setAttribute('aria-label', 'Поиск сотрудника');
    const list = document.createElement('div'); list.className = 'employee-picker__list';
    const status = document.createElement('div'); status.className = 'small text-muted'; status.setAttribute('role', 'status');
    const done = document.createElement('button'); done.type = 'button'; done.className = 'btn btn-sm btn-outline-secondary'; done.textContent = 'Готово';
    menu.append(search, list, status, done);
    source.before(wrapper); wrapper.append(source, trigger, menu);
    // Не меняем type: legacy form.elements/value и submit остаются прежними.
    source.hidden = true; source.dataset.employeeSource = 'true';
    let remote = null, busy = false, error = '';
    function options() {
      if (source instanceof HTMLSelectElement) return Array.from(source.options).filter(option => option.value && !option.disabled).map(option => ({ value: option.value, label: option.textContent }));
      if (source.list) return Array.from(source.list.options).map(option => ({ value: option.value, label: option.label || option.value }));
      return remote || [];
    }
    function selected() { return multiple ? (source instanceof HTMLSelectElement ? Array.from(source.selectedOptions).map(option => option.value) : split(source.value)) : (source.value ? [source.value] : []); }
    function refresh() {
      const values = selected(), all = options();
      trigger.textContent = values.map(value => all.find(option => option.value === value)?.label || value).join(', ') || source.dataset.employeePlaceholder || 'Выберите сотрудника';
      trigger.disabled = source.disabled;
      if (source.validity.valid) trigger.removeAttribute('aria-invalid');
      trigger.title = trigger.textContent;
      if (!menu.hidden) render();
    }
    function setValue(values) {
      if (source instanceof HTMLSelectElement && multiple) Array.from(source.options).forEach(option => { option.selected = values.includes(option.value); });
      else source.value = values.join(', ');
      source.dispatchEvent(new Event('input', { bubbles: true })); source.dispatchEvent(new Event('change', { bubbles: true })); refresh();
    }
    function render() {
      const current = selected(), query = normalize(search.value), all = options();
      const items = all.filter(option => normalize(option.label + ' ' + option.value).includes(query));
      list.replaceChildren();
      if (!source.required || multiple) {
        const clear = document.createElement('button'); clear.type = 'button'; clear.className = 'employee-picker__option'; clear.textContent = 'Очистить выбор'; clear.addEventListener('click', () => { setValue([]); if (!multiple) close(); }); list.append(clear);
      }
      for (const option of items) {
        const button = document.createElement('button'); button.type = 'button'; button.className = 'employee-picker__option';
        button.textContent = (current.includes(option.value) ? '✓ ' : '') + option.label;
        button.dataset.employeeValue = option.value;
        button.setAttribute('aria-pressed', String(current.includes(option.value)));
        button.addEventListener('click', () => { const values = selected(); setValue(multiple ? (values.includes(option.value) ? values.filter(value => value !== option.value) : [...values, option.value]) : [option.value]); if (!multiple) close(); else search.focus({ preventScroll: true }); }); list.append(button);
      }
      status.textContent = busy ? 'Загрузка сотрудников…' : error || (items.length ? `Найдено: ${items.length}` : 'Сотрудники не найдены');
    }
    function close(restore = true) { menu.hidden = true; trigger.setAttribute('aria-expanded', 'false'); if (restore) trigger.focus({ preventScroll: true }); }
    async function open() {
      menu.hidden = false; trigger.setAttribute('aria-expanded', 'true'); search.value = ''; refresh(); search.focus({ preventScroll: true });
      if (!(source instanceof HTMLSelectElement) && !source.list && !remote) {
        busy = true; error = ''; render();
        try {
          directory ||= fetch('/api/panel-users/options', { credentials: 'same-origin', headers: { Accept: 'application/json' } }).then(async response => { if (!response.ok) throw new Error('Не удалось загрузить сотрудников'); return response.json(); }).catch(ex => { directory = null; throw ex; });
          remote = await directory; if (!Array.isArray(remote)) throw new Error('Неверный ответ справочника');
        } catch (ex) { error = ex.message; } finally { busy = false; if (source.isConnected) refresh(); }
      }
    }
    trigger.addEventListener('click', () => { if (menu.hidden) void open(); else close(); });
    done.addEventListener('click', () => close()); search.addEventListener('input', render);
    wrapper.addEventListener('keydown', event => {
      if (event.key === 'Enter' && event.target === search) { event.preventDefault(); event.stopPropagation(); list.querySelector('[data-employee-value]')?.click(); }
      if (event.key === 'Escape' && !menu.hidden) { event.preventDefault(); event.stopPropagation(); close(); }
      if (['ArrowDown', 'ArrowUp'].includes(event.key) && !menu.hidden) {
        const buttons = Array.from(list.querySelectorAll('button')); const index = buttons.indexOf(document.activeElement);
        event.preventDefault(); (buttons[index + (event.key === 'ArrowDown' ? 1 : -1)] || (event.key === 'ArrowDown' ? buttons[0] : search))?.focus();
      }
    });
    wrapper.addEventListener('focusout', () => setTimeout(() => { if (!wrapper.contains(document.activeElement)) close(false); }, 0));
    document.addEventListener('pointerdown', event => { if (!wrapper.contains(event.target) && !menu.hidden) close(false); }, { signal: (() => { const controller = new AbortController(); wrapper.__employeeAbort = controller; return controller.signal; })() });
    source.addEventListener('change', refresh);
    source.addEventListener('invalid', event => { event.preventDefault(); trigger.focus(); trigger.setAttribute('aria-invalid', 'true'); });
    new MutationObserver(refresh).observe(source, { attributes: true, childList: true, subtree: true });
    mounted.set(source, { refresh, close }); refresh();
  }
  function scan(root = document) {
    const selectors = "#taskForm input[list='usersList'], input[list='projectUsersList'], #dialogParticipantsSelect, #dialogReassignSelect, #membershipUser, #allGrantUser, #assignmentTarget, select[name='assigneeId'], select[name='memberId'], .tasks-property-group select[name='assignee'], #channelEditorAssignmentSingleOperator, #channelEditorAssignmentPoolOperators, #channelEditorPanelNotifEmployees, #channelEditorPanelNotifExclude, [data-ticket-assignee-candidate], [data-ticket-participant-candidate]";
    root.querySelectorAll(selectors).forEach(source => {
      source.dataset.employeePicker = 'true';
      if (['co', 'watchers'].includes(source.name) || ['channelEditorPanelNotifEmployees', 'channelEditorPanelNotifExclude'].includes(source.id)) source.dataset.employeeMultiple = 'true';
    });
    root.querySelectorAll('[data-employee-picker]:not([data-employee-source])').forEach(mount);
    root.querySelectorAll('[data-employee-source]').forEach(source => mounted.get(source)?.refresh());
  }
  let queued = false;
  new MutationObserver(records => {
    for (const record of records) for (const node of record.removedNodes) if (node instanceof Element) {
      if (!node.isConnected) { node.__employeeAbort?.abort(); node.querySelectorAll('.employee-picker').forEach(item => item.__employeeAbort?.abort()); }
    }
    if (!queued && records.some(record => Array.from(record.addedNodes).some(node => node instanceof Element && !node.closest('.employee-picker')))) {
      queued = true; queueMicrotask(() => { queued = false; scan(); });
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('shown.bs.modal', () => scan());
  document.addEventListener('reset', () => setTimeout(() => scan(), 0));
  window.EmployeePicker = { refresh: scan };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => scan()); else scan();
})();

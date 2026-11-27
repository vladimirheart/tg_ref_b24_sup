/* Shared presentation only. Permission values and editability come from the caller. */
(function () {
  'use strict';
  let sequence = 0;
  function create({ label = 'Поиск разрешений' } = {}) {
    const element = document.createElement('div');
    element.className = 'permissions-editor';
    element.innerHTML = `<div class="permissions-editor__toolbar">
      <label class="permissions-editor__search"><span class="visually-hidden"></span>
        <input type="search" class="form-control form-control-sm" placeholder="Найти разрешение…" autocomplete="off">
      </label><span class="permissions-editor__total" role="status" aria-live="polite"></span></div>
      <div class="permissions-editor__groups"></div>
      <p class="permissions-editor__empty" hidden>Ничего не найдено. Попробуйте другой запрос.</p>`;
    const search = element.querySelector('input'); search.setAttribute('aria-label', label);
    element.querySelector('.visually-hidden').textContent = label;
    const host = element.querySelector('.permissions-editor__groups');
    const total = element.querySelector('.permissions-editor__total');
    const empty = element.querySelector('.permissions-editor__empty');
    const groups = []; let beforeSearch = null;
    const normalize = value => String(value).toLocaleLowerCase('ru').replace(/ё/g, 'е').trim();
    function addGroup(title, description, { choices = false } = {}) {
      const details = document.createElement('details'); details.className = 'permissions-editor__group';
      const summary = document.createElement('summary'); summary.className = 'permissions-editor__summary';
      const heading = document.createElement('span'); heading.className = 'permissions-editor__heading';
      const name = document.createElement('span'); name.className = 'permissions-editor__title'; name.textContent = title;
      const hint = document.createElement('span'); hint.className = 'permissions-editor__hint'; hint.textContent = description;
      heading.append(name, hint);
      const count = document.createElement('span'); count.className = 'permissions-editor__count';
      const chevron = document.createElement('span'); chevron.className = 'permissions-editor__chevron'; chevron.setAttribute('aria-hidden', 'true');
      summary.append(heading, count, chevron);
      const body = document.createElement('div'); body.className = 'permissions-editor__body' + (choices ? ' permissions-editor__body--choices' : '');
      body.id = 'permissionGroup' + (++sequence); summary.setAttribute('aria-controls', body.id);
      details.append(summary, body); host.append(details);
      const group = { element: details, body, count, rows: [], title }; groups.push(group); return group;
    }
    function track(group, node, text, allowed, { count = true } = {}) {
      group.rows.push({ node, text: normalize(text), allowed, count });
      group.body.append(node);
    }
    function refresh() {
      const query = normalize(search.value);
      if (query && !beforeSearch) beforeSearch = groups.map(group => group.element.open);
      let shown = 0, size = 0, granted = 0, visibleRows = 0;
      groups.forEach((group, index) => {
        let matches = 0, groupSize = 0, groupGranted = 0;
        const titleMatches = query && normalize(group.title).includes(query);
        group.rows.forEach(row => {
          row.node.hidden = Boolean(query && !titleMatches && !row.text.includes(query));
          if (!row.node.hidden) { matches++; visibleRows++; }
          if (row.count) {
            groupSize++; if (row.allowed()) groupGranted++;
            if (!row.node.hidden) shown++;
          }
        });
        size += groupSize; granted += groupGranted;
        group.count.textContent = `${groupGranted} / ${groupSize}`;
        group.count.setAttribute('aria-label', `Разрешено ${groupGranted} из ${groupSize}`);
        group.element.hidden = matches === 0;
        if (query && matches) group.element.open = true;
        if (!query && beforeSearch) group.element.open = beforeSearch[index] || false;
      });
      if (!query) beforeSearch = null;
      empty.hidden = visibleRows > 0 || size === 0;
      total.textContent = query ? `Найдено ${shown} из ${size}` : `Разрешено ${granted} из ${size}`;
    }
    function snapshot() { return { query: search.value, open: beforeSearch || groups.map(group => group.element.open) }; }
    function restore(state) {
      if (!state) return;
      groups.forEach((group, index) => group.element.open = state.open[index] || false);
      search.value = state.query; refresh();
    }
    search.addEventListener('input', refresh);
    // Run after the caller's delegated wildcard/change handler has finished.
    element.addEventListener('change', () => queueMicrotask(refresh));
    return { element, addGroup, track, refresh, snapshot, restore };
  }
  window.PermissionEditor = Object.freeze({ create });
})();

(function () {
  'use strict';
  function mount({ passportId, csrfToken = '' } = {}) {
    const root = document.getElementById('passportDocuments');
    if (!root || !passportId) return null;
    const find = selector => root.querySelector(selector);
    const list = find('[data-document-list]'), status = find('[data-document-status]');
    const search = find('[data-document-search]'), more = find('[data-document-more]');
    const create = find('[data-document-create]');
    const editor = document.getElementById('passportDocumentEditor');
    const viewer = document.getElementById('passportDocumentViewer');
    const form = document.getElementById('passportDocumentForm');
    const extra = form.querySelector('[data-document-extra-fields]');
    const error = form.querySelector('[data-document-error]');
    const base = `/api/object_passports/${encodeURIComponent(passportId)}/documents`;
    let items = [], cursor = '', loaded = false, canEdit = false, generation = 0, editing = null, saving = false;
    let selectedUrl = '', viewing = null, historyCursor = 0, viewerGeneration = 0;
    const actions = { create: 'Создание', edit: 'Изменение', archive: 'В архив', unarchive: 'Возврат из архива' };
    const node = (tag, className, text) => {
      const element = document.createElement(tag);
      if (className) element.className = className;
      if (text != null) element.textContent = text;
      return element;
    };
    const button = (text, action, className = 'btn btn-sm btn-outline-secondary') => {
      const result = node('button', className, text); result.type = 'button'; result.dataset.action = action; return result;
    };
    const date = value => value ? new Date(value).toLocaleString('ru-RU') : '';
    const bytes = size => size < 1024 * 1024 ? `${Math.ceil(size / 1024)} КБ` : `${(size / 1024 / 1024).toFixed(1)} МБ`;
    const url = item => `${base}/${encodeURIComponent(item.id)}/revisions/${item.revision}`;
    const meta = item => [`Версия ${item.revision}`, actions[item.action] || '', item.actor, date(item.changed_at)].filter(Boolean).join(' · ');

    async function request(address, options = {}) {
      const headers = { Accept: 'application/json', ...(options.headers || {}) };
      if (options.method && options.method !== 'GET') headers['X-XSRF-TOKEN'] = csrfToken;
      const response = await fetch(address, { ...options, credentials: 'same-origin', headers });
      const payload = await response.json().catch(() => ({}));
      if (!response.ok || payload.success === false) {
        const message = response.status === 409 ? 'Документ уже изменён. Закройте редактор, обновите список и откройте запись снова. Введённые данные пока сохранены в форме.'
          : response.status === 403 ? 'Недостаточно прав для этого действия.'
          : response.status === 413 ? 'Размер файла — не более 50 МБ.'
          : payload.message || payload.error || 'Не удалось выполнить действие. Попробуйте ещё раз.';
        throw new Error(message);
      }
      return payload;
    }

    function render() {
      list.replaceChildren();
      for (const item of items) {
        const card = node('article', 'passport-document-card' + (item.archived ? ' is-archived' : ''));
        card.dataset.documentId = item.id;
        const visual = button('', 'view', 'passport-document-card__preview');
        visual.setAttribute('aria-label', 'Открыть: ' + item.title);
        if (item.file?.preview_kind === 'image') {
          const img = node('img'); img.src = url(item) + '/file?preview=true'; img.alt = item.title; img.loading = 'lazy'; visual.append(img);
        } else visual.textContent = item.file ? (item.file.preview_kind === 'pdf' ? 'PDF' : item.file.name.split('.').pop().toUpperCase().slice(0, 12)) : 'Запись без файла';
        const content = node('div', 'passport-document-card__content');
        content.append(node('span', 'small text-muted', [item.category, item.event_date, item.archived ? 'Архив' : ''].filter(Boolean).join(' · ')), node('h3', '', item.title));
        content.append(node('p', 'passport-document-card__description', item.description.length > 200 ? item.description.slice(0, 200) + '…' : item.description));
        if (item.file) content.append(node('p', 'small text-muted mb-1', item.file.name + ' · ' + bytes(item.file.size)));
        content.append(node('p', 'small text-muted mb-0', meta(item)));
        const controls = node('div', 'passport-document-card__actions'); controls.append(button('Просмотр', 'view'));
        if (canEdit) {
          if (!item.archived) controls.append(button('Изменить', 'edit'));
          controls.append(button(item.archived ? 'Вернуть из архива' : 'В архив', 'archive'));
        }
        card.append(visual, content, controls); list.append(card);
      }
    }

    async function load(reset = false) {
      if (loaded && !reset) return;
      if (reset) cursor = '';
      const token = ++generation;
      status.textContent = 'Загружаем документы…'; more.disabled = true;
      const params = new URLSearchParams({ q: search.elements.q.value.trim(), archived: String(search.elements.archived.checked) });
      if (cursor) params.set('before', cursor);
      try {
        const result = await request(base + '?' + params);
        if (token !== generation) return;
        items = cursor ? [...items, ...result.items] : result.items;
        cursor = result.next_cursor || ''; canEdit = result.can_edit === true; loaded = true;
        create.hidden = !canEdit; more.hidden = !result.has_more;
        document.getElementById('passportDocumentsTabCount').textContent = String(result.total);
        status.textContent = items.length ? `Записей: ${result.total}` : 'Документы пока не добавлены. Здесь можно хранить файлы и заметки об изменениях.';
        render();
      } catch (ex) { if (token === generation) status.textContent = ex.message; }
      finally { if (token === generation) more.disabled = false; }
    }

    function addField(label = '', value = '') {
      if (extra.children.length >= 30) { error.textContent = 'Не более 30 дополнительных полей.'; return; }
      const row = node('div', 'passport-document-extra-field');
      const key = node('input', 'form-control'), input = node('textarea', 'form-control');
      key.value = label; key.maxLength = 80; key.dataset.extraKey = ''; key.placeholder = 'Название поля'; key.setAttribute('aria-label', 'Название дополнительного поля');
      input.value = value; input.maxLength = 2000; input.rows = 1; input.dataset.extraValue = ''; input.placeholder = 'Значение'; input.setAttribute('aria-label', 'Значение дополнительного поля');
      const remove = button('×', 'remove-field'); remove.setAttribute('aria-label', 'Удалить поле');
      remove.addEventListener('click', () => row.remove()); row.append(key, input, remove); extra.append(row);
    }

    function clearLocalPreview() {
      if (selectedUrl) URL.revokeObjectURL(selectedUrl);
      selectedUrl = ''; form.querySelector('[data-document-local-preview]').replaceChildren();
    }
    function selectedPreview() {
      clearLocalPreview(); error.textContent = '';
      const file = form.elements.file.files[0];
      if (!file) return;
      if (file.size > 50 * 1024 * 1024) { error.textContent = 'Размер файла — не более 50 МБ.'; return; }
      const area = form.querySelector('[data-document-local-preview]');
      if (/^image\/(png|jpeg|gif|bmp|webp)$/.test(file.type)) {
        selectedUrl = URL.createObjectURL(file); const image = node('img'); image.src = selectedUrl; image.alt = file.name; area.append(image);
      }
      area.append(node('p', 'small text-muted', file.name + ' · ' + bytes(file.size)));
    }
    function openEditor(item = null) {
      if (!canEdit) return;
      editing = item; form.reset(); extra.replaceChildren(); error.textContent = ''; clearLocalPreview();
      document.getElementById('passportDocumentEditorTitle').textContent = item ? 'Изменить запись' : 'Новая запись';
      for (const key of ['title', 'category', 'event_date', 'description']) form.elements[key].value = item?.[key] || '';
      for (const [key, value] of Object.entries(item?.fields || {})) addField(key, value);
      form.querySelector('[data-document-current-file]').textContent = item?.file
        ? `Сейчас: ${item.file.name}. Выберите файл для замены или оставьте поле пустым, чтобы сохранить текущий.` : '';
      editor.showModal(); form.elements.title.focus();
    }
    function closeEditor() { if (!saving) editor.close(); }
    editor.addEventListener('cancel', event => { if (saving) event.preventDefault(); });
    editor.addEventListener('close', clearLocalPreview);
    editor.querySelectorAll('[data-document-editor-close]').forEach(item => item.addEventListener('click', closeEditor));
    form.querySelector('[data-document-field-add]').addEventListener('click', () => addField());
    form.elements.file.addEventListener('change', selectedPreview);

    form.addEventListener('submit', async event => {
      event.preventDefault(); if (saving || !form.reportValidity()) return;
      error.textContent = '';
      const fields = Object.create(null);
      for (const row of extra.children) {
        const key = row.querySelector('[data-extra-key]').value.trim(), value = row.querySelector('[data-extra-value]').value.trim();
        if (!key && !value) continue;
        if (!key || Object.hasOwn(fields, key)) { error.textContent = 'Укажите непустые и уникальные названия дополнительных полей.'; return; }
        fields[key] = value;
      }
      const file = form.elements.file.files[0];
      if (file && file.size > 50 * 1024 * 1024) { error.textContent = 'Размер файла — не более 50 МБ.'; return; }
      const metadata = { base_revision: editing?.revision || 0, fields };
      for (const key of ['title', 'category', 'description']) metadata[key] = form.elements[key].value.trim();
      metadata.event_date = form.elements.event_date.value || null;
      const payload = new FormData(); payload.append('metadata', JSON.stringify(metadata)); if (file) payload.append('file', file);
      saving = true; form.querySelector('fieldset').disabled = true; form.querySelector('[data-document-save]').disabled = true;
      try {
        await request(editing ? `${base}/${encodeURIComponent(editing.id)}/revisions` : base, { method: 'POST', body: payload });
        editor.close(); await load(true);
      } catch (ex) { error.textContent = ex.message; }
      finally { saving = false; form.querySelector('fieldset').disabled = false; form.querySelector('[data-document-save]').disabled = false; }
    });

    async function openViewer(item, keepHistory = false) {
      const token = ++viewerGeneration; viewing = item;
      const message = viewer.querySelector('[data-document-viewer-status]'); message.textContent = 'Загружаем запись…';
      if (!keepHistory) {
        viewer.querySelector('[data-document-history]').hidden = true;
        viewer.querySelector('[data-document-history-list]').replaceChildren(); historyCursor = 0;
      }
      viewer.querySelector('[data-document-preview]').replaceChildren();
      viewer.querySelector('[data-document-description]').replaceChildren();
      viewer.querySelector('[data-document-properties]').replaceChildren();
      viewer.querySelector('[data-document-download]').hidden = true;
      if (!viewer.open) viewer.showModal();
      try {
        const result = await request(url(item));
        if (token !== viewerGeneration) return;
        const current = result.document; viewing = current;
        document.getElementById('passportDocumentViewerTitle').textContent = current.title;
        viewer.querySelector('[data-document-version-meta]').textContent = meta(current);
        const description = viewer.querySelector('[data-document-description]'); description.style.whiteSpace = 'pre-wrap'; description.textContent = current.description;
        const properties = viewer.querySelector('[data-document-properties]');
        for (const [key, value] of [['Тип записи', current.category], ['Дата события', current.event_date], ...Object.entries(current.fields || {})]) {
          if (value) properties.append(node('dt', '', key), node('dd', '', value));
        }
        const area = viewer.querySelector('[data-document-preview]'), file = current.file;
        if (file) {
          if (file.preview_kind === 'image') { const image = node('img'); image.src = url(current) + '/file?preview=true'; image.alt = current.title; area.append(image); }
          else if (file.preview_kind === 'pdf') { const frame = node('iframe'); frame.src = url(current) + '/file?preview=true'; frame.title = current.title; area.append(frame); }
          else if (file.preview_kind === 'text') area.append(node('pre', '', file.preview_text));
          else area.append(node('p', 'text-muted', 'Предпросмотр этого формата недоступен. Скачайте файл, чтобы открыть его.'));
          const download = viewer.querySelector('[data-document-download]'); download.href = url(current) + '/file'; download.download = file.name; download.hidden = false;
          message.textContent = file.name + ' · ' + bytes(file.size);
        } else message.textContent = 'Запись без файла';
      } catch (ex) { if (token === viewerGeneration) message.textContent = ex.message; }
    }

    async function history(append = false) {
      if (!viewing) return;
      const documentId = viewing.id;
      const area = viewer.querySelector('[data-document-history]'), rows = viewer.querySelector('[data-document-history-list]');
      area.hidden = false;
      const moreHistory = viewer.querySelector('[data-document-history-more]'); moreHistory.disabled = true;
      try {
        const result = await request(`${base}/${encodeURIComponent(documentId)}/revisions` + (append ? '?before=' + historyCursor : ''));
        if (documentId !== viewing?.id) return;
        if (!append) rows.replaceChildren();
        for (const item of result.items) {
          const row = node('div', 'passport-document-history-row'); row.append(node('span', 'small', meta(item)));
          const open = button('Открыть версию ' + item.revision, 'version'); open.addEventListener('click', () => openViewer(item, true)); row.append(open); rows.append(row);
        }
        historyCursor = result.next_cursor; moreHistory.hidden = !result.has_more;
      } catch (ex) { viewer.querySelector('[data-document-viewer-status]').textContent = ex.message; }
      finally { moreHistory.disabled = false; }
    }
    viewer.querySelector('[data-document-history-toggle]').addEventListener('click', () => history());
    viewer.querySelector('[data-document-history-more]').addEventListener('click', () => history(true));
    viewer.querySelector('[data-document-viewer-close]').addEventListener('click', () => viewer.close());
    viewer.addEventListener('close', () => { viewerGeneration++; viewer.querySelector('[data-document-preview]').replaceChildren(); });

    list.addEventListener('click', async event => {
      const control = event.target.closest('[data-action]'), card = event.target.closest('[data-document-id]');
      const item = items.find(entry => entry.id === card?.dataset.documentId);
      if (!item || !control) return;
      if (control.dataset.action === 'view') openViewer(item);
      if (control.dataset.action === 'edit') openEditor(item);
      if (control.dataset.action === 'archive' && canEdit) {
        control.disabled = true;
        try {
          await request(`${base}/${encodeURIComponent(item.id)}/archive`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ base_revision: item.revision, archived: !item.archived }) }); await load(true);
        } catch (ex) { status.textContent = ex.message; control.disabled = false; }
      }
    });
    create.addEventListener('click', () => openEditor());
    search.addEventListener('submit', event => { event.preventDefault(); load(true); });
    search.elements.archived.addEventListener('change', () => load(true));
    more.addEventListener('click', () => { loaded = false; load(); });
    return Object.freeze({ load });
  }
  window.PassportDocumentsRuntime = Object.freeze({ mount });
}());

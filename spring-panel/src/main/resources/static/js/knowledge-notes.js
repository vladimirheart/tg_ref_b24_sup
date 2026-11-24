(function () {
  'use strict';

  function formatTimes() {
    const uiTime = window.iguanaUiTime;
    document.querySelectorAll('[data-note-time]').forEach(function (element) {
      const value = element.getAttribute('datetime') || element.textContent || '';
      if (!value) return;
      if (uiTime && typeof uiTime.formatDateTime === 'function') {
        element.textContent = uiTime.formatDateTime(value, { fallback: value });
      }
    });
  }

  function bindCustomFields() {
    const container = document.querySelector('[data-note-custom-fields]');
    const addButton = document.querySelector('[data-note-add-field]');
    if (!container || !addButton) return;

    function bindRemove(button) {
      if (!button || button.dataset.noteBound === '1') return;
      button.dataset.noteBound = '1';
      button.addEventListener('click', function () {
        const row = button.closest('[data-note-field-row]');
        if (row) row.remove();
      });
    }

    container.querySelectorAll('[data-note-remove-field]').forEach(bindRemove);
    addButton.addEventListener('click', function () {
      const row = document.createElement('div');
      row.className = 'row g-2 mb-2';
      row.setAttribute('data-note-field-row', '');
      row.innerHTML = '<div class="col-md-5"><input class="form-control" name="customFieldKey" maxlength="120" placeholder="Название поля"></div>' +
        '<div class="col-md-6"><input class="form-control" name="customFieldValue" maxlength="4000" placeholder="Значение"></div>' +
        '<div class="col-md-1 d-grid"><button class="btn btn-outline-secondary" type="button" data-note-remove-field aria-label="Удалить поле"><i class="bi bi-x-lg"></i></button></div>';
      container.appendChild(row);
      bindRemove(row.querySelector('[data-note-remove-field]'));
      row.querySelector('input')?.focus();
    });
  }

  function bindPhotos() {
    const form = document.querySelector('[data-knowledge-note-form]');
    const textarea = form?.querySelector('[name="body"]');
    const button = form?.querySelector('[data-note-add-photo]');
    const input = form?.querySelector('[data-note-photo-input]');
    const state = form?.querySelector('[data-note-photo-state]');
    const preview = form?.querySelector('[data-note-body-preview]');
    const content = window.IguanaNoteContent;
    if (!textarea || !button || !input || !state || !preview || !content) return;
    let uploading = false;
    function refreshPreview() {
      preview.hidden = !textarea.value.trim();
      content.render(preview, textarea.value);
    }
    async function upload(files) {
      if (uploading || !files.length) return;
      uploading = true; button.disabled = true;
      const submit = form.querySelector('[type="submit"]');
      const wasDisabled = submit?.disabled;
      if (submit) submit.disabled = true;
      state.classList.remove('text-danger');
      let added = 0;
      try {
        for (const file of files) {
          const isPhoto = /\.(png|jpe?g|gif|webp)$/i.test(file.name);
          if (!file.size || file.size > (isPhoto ? 10 : 50) * 1024 * 1024) throw new Error('Фотографии — до 10 МБ, остальные файлы — до 50 МБ.');
          if (textarea.value.length > 99000) throw new Error('В заметке недостаточно места для ссылки на файл.');
          state.textContent = `Загрузка файла ${added + 1} из ${files.length}…`;
          const data = new FormData(); data.append('file', file);
          const token = document.querySelector('meta[name="_csrf"]')?.content || form.querySelector('[name="_csrf"]')?.value;
          const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
          const response = await fetch('/api/attachments/knowledge-base/note-files', {
            method: 'POST', credentials: 'same-origin', headers: token ? { [header]: token } : {}, body: data
          });
          if (!response.ok || response.redirected) throw new Error('Не удалось загрузить файл. Проверьте доступ и повторите попытку.');
          const metadata = await response.json();
          const path = content.attachmentPath('/api/attachments/knowledge-base/' + encodeURIComponent(metadata.storedName || ''));
          if (!path) throw new Error('Хранилище вернуло неподдерживаемое имя файла.');
          const label = String(metadata.originalName || file.name).replace(/[\[\]\r\n]/g, ' ');
          const reference = isPhoto && content.photoPath(path) ? `\n![Фото](${path})\n` : `\n[${label}](${path})\n`;
          if (textarea.value.length + reference.length > 100000) throw new Error('Превышена максимальная длина заметки.');
          textarea.setRangeText(reference, textarea.selectionStart, textarea.selectionEnd, 'end');
          textarea.dispatchEvent(new Event('input', { bubbles: true }));
          added++;
        }
        state.textContent = `Добавлено файлов: ${added}. Сохраните заметку.`;
      } catch (error) {
        state.classList.add('text-danger');
        state.textContent = `${added ? `Добавлено: ${added}. ` : ''}${error.message}`;
      } finally {
        uploading = false; button.disabled = false; input.value = '';
        if (submit) submit.disabled = wasDisabled;
        refreshPreview();
      }
    }
    button.addEventListener('click', () => input.click());
    input.addEventListener('change', () => upload(Array.from(input.files || [])));
    textarea.addEventListener('paste', event => {
      const files = Array.from(event.clipboardData?.files || []);
      if (files.length) { event.preventDefault(); upload(files); }
    });
    textarea.addEventListener('input', refreshPreview);
    form.addEventListener('submit', event => {
      if (uploading) { event.preventDefault(); state.textContent = 'Дождитесь окончания загрузки файлов.'; }
    });
    refreshPreview();
  }

  function init() {
    formatTimes();
    bindCustomFields();
    bindPhotos();
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();

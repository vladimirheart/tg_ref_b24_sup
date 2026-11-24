/* Task files stay in the local draft until the task/comment multipart request succeeds. */
(function () {
  'use strict';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const imageTypes = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);
  function sizeLabel(bytes) {
    const size = Number(bytes);
    return size > 0 ? (size >= 1048576 ? `${(size / 1048576).toFixed(1)} МБ` : `${Math.max(1, Math.ceil(size / 1024))} КБ`) : '';
  }
  function safeUrl(value, image = false) {
    if (typeof value !== 'string' || !value) return '';
    if (image && /^data:image\/(?:png|jpeg|gif|webp);base64,[a-z0-9+/=\s]+$/i.test(value || '')) return value;
    try { const url = new URL(value, location.origin); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''; }
    catch (_) { return ''; }
  }
  function collect(html) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    const items = [];
    root.querySelectorAll('a[data-task-attachment], figure[data-task-attachment], a[download], img').forEach(node => {
      const image = node.matches('img') ? node : node.querySelector('img');
      const figure = node.closest('[data-task-attachment]');
      const anchor = node.matches('a') ? node : node.querySelector('a') || node.closest('a');
      const href = safeUrl(anchor?.getAttribute('href') || image?.getAttribute('src'), !!image);
      if (!href) return;
      const preview = image ? safeUrl(image.getAttribute('src'), true) : '';
      const name = figure?.dataset.attachmentName || node.dataset.attachmentName || anchor?.getAttribute('download')
        || image?.getAttribute('alt') || node.textContent.trim() || 'Изображение';
      items.push({ href, preview, name, size: figure?.dataset.attachmentSize || node.dataset.attachmentSize || '' });
    });
    return items;
  }
  function compactComment(html) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    root.querySelectorAll('figure[data-task-attachment], a[data-task-attachment]').forEach(node => {
      const item = collect(node.outerHTML)[0];
      if (!item) return;
      const link = document.createElement('a'); link.className = 'tasks-file-chip'; link.href = item.href;
      link.download = item.name; link.textContent = '📎 ' + item.name; node.replaceWith(link);
    });
    root.querySelectorAll('img').forEach(image => {
      const src = safeUrl(image.getAttribute('src'), true); if (!src) { image.remove(); return; }
      const link = document.createElement('a'); link.className = 'tasks-file-chip'; link.href = src;
      link.dataset.taskImagePreview = src; link.textContent = '📎 ' + (image.alt || 'Изображение');
      const parentLink = image.closest('a'); if (parentLink) parentLink.replaceWith(link); else image.replaceWith(link);
    });
    return root.innerHTML;
  }
  function create(options) {
    const queues = { body: [], comment: [] }; let persisted = [], allowed = false, hasTask = false, sequence = 0;
    const panel = document.getElementById('taskAttachments'), count = document.getElementById('taskAttachmentsCount');
    const commentList = document.getElementById('taskCommentFiles');
    function render() {
      const selected = [...queues.body.map(file => ({ ...file, scope: 'body' })), ...queues.comment.map(file => ({ ...file, scope: 'comment' }))];
      const seen = new Set(), items = persisted.filter(item => { const id = item.href.replace(/\?preview=true$/, ''); if (seen.has(id)) return false; seen.add(id); return true; });
      if (count) count.textContent = items.length + selected.length || '';
      if (panel) panel.innerHTML = [
        ...items.map(item => `<article class="tasks-attachment"><a class="tasks-attachment__preview" href="${escape(item.href)}" ${item.preview ? `data-task-image-preview="${escape(item.preview)}"` : `download="${escape(item.name)}"`} aria-label="${escape(item.name)}">${item.preview ? `<img src="${escape(item.preview)}" alt="" loading="lazy">` : '<i class="bi bi-file-earmark" aria-hidden="true"></i>'}</a><div class="tasks-attachment__copy"><a href="${escape(item.href)}" download="${escape(item.name)}" title="${escape(item.name)}">${escape(item.name)}</a><small>${escape(sizeLabel(item.size))}</small></div></article>`),
        ...selected.map(item => `<article class="tasks-attachment"><span class="tasks-attachment__preview">${item.preview ? `<img src="${escape(item.preview)}" alt="">` : '<i class="bi bi-file-earmark" aria-hidden="true"></i>'}</span><div class="tasks-attachment__copy"><strong title="${escape(item.file.name)}">${escape(item.file.name)}</strong><small>${escape(sizeLabel(item.file.size))} · ${item.scope === 'body' ? 'После сохранения задачи' : 'С комментарием'}</small></div><button type="button" class="btn btn-sm btn-link tasks-attachment__remove" data-remove-task-file="${item.id}" data-file-scope="${item.scope}" aria-label="Убрать ${escape(item.file.name)}">×</button></article>`),
      ].join('') || '<span class="small text-muted">Вложений пока нет</span>';
      if (commentList) commentList.innerHTML = queues.comment.map(item => `<span class="tasks-file-chip"><span>${escape(item.file.name)}</span><button type="button" data-remove-task-file="${item.id}" data-file-scope="comment" aria-label="Убрать ${escape(item.file.name)}">×</button></span>`).join('');
    }
    function changed(scope) { render(); options.onChange?.(scope); }
    function add(scope, files) {
      if (!allowed || (scope === 'comment' && !hasTask)) return;
      const incoming = Array.from(files || []);
      if (!incoming.length) return;
      const all = [...queues[scope].map(item => item.file), ...incoming];
      if (all.length > 10 || all.reduce((sum, file) => sum + file.size, 0) > 40 * 1024 * 1024) { options.onError?.(new Error('До 10 файлов, суммарно до 40 МБ')); return; }
      if (incoming.some(file => !file.size || file.size > 20 * 1024 * 1024)) { options.onError?.(new Error('Выберите непустые файлы до 20 МБ каждый')); return; }
      incoming.forEach(file => queues[scope].push({ id: ++sequence, file, preview: imageTypes.has(file.type) ? URL.createObjectURL(file) : '' }));
      changed(scope);
    }
    function clear(scope) {
      queues[scope].forEach(item => { if (item.preview) URL.revokeObjectURL(item.preview); }); queues[scope] = []; render();
    }
    options.modal.addEventListener('click', event => {
      const remove = event.target.closest('[data-remove-task-file]'); if (!remove) return;
      const scope = remove.dataset.fileScope;
      if (!allowed || (scope === 'comment' && !hasTask) || !queues[scope]) return;
      const item = queues[scope].find(item => String(item.id) === remove.dataset.removeTaskFile);
      if (item?.preview) URL.revokeObjectURL(item.preview);
      queues[scope] = queues[scope].filter(item => item.id !== Number(remove.dataset.removeTaskFile)); changed(scope);
    });
    for (const [scope, inputId, buttonId, dropTarget, editor] of [
      ['body', 'taskFileInput', 'attachTaskFileBtn', options.modal.querySelector('[data-task-pane="description"]'), options.body],
      ['comment', 'commentFileInput', 'attachCommentFileBtn', options.modal.querySelector('.tasks-chat-composer'), options.comment],
    ]) {
      const input = document.getElementById(inputId), button = document.getElementById(buttonId);
      button?.addEventListener('click', () => input?.click());
      input?.addEventListener('change', () => { add(scope, input.files); input.value = ''; });
      dropTarget?.addEventListener('dragover', event => {
        if (!Array.from(event.dataTransfer?.types || []).includes('Files')) return;
        event.preventDefault(); event.stopPropagation();
        if (allowed && (scope !== 'comment' || hasTask)) { event.dataTransfer.dropEffect = 'copy'; dropTarget.classList.add('is-file-drop-target'); }
      });
      dropTarget?.addEventListener('dragleave', event => { if (!dropTarget.contains(event.relatedTarget)) dropTarget.classList.remove('is-file-drop-target'); });
      dropTarget?.addEventListener('drop', event => {
        if (!event.dataTransfer?.files?.length) return;
        event.preventDefault(); event.stopPropagation(); dropTarget.classList.remove('is-file-drop-target'); add(scope, event.dataTransfer.files);
      });
      editor?.addEventListener('paste', event => {
        const files = Array.from(event.clipboardData?.files || []); if (!files.length) return;
        event.preventDefault(); if (editor.getAttribute('contenteditable') === 'true') add(scope, files);
      });
    }
    options.modal.addEventListener('dragover', event => { if (Array.from(event.dataTransfer?.types || []).includes('Files')) event.preventDefault(); });
    options.modal.addEventListener('drop', event => { if (event.dataTransfer?.files?.length) event.preventDefault(); });
    return {
      files: scope => queues[scope].map(item => item.file), clear,
      reset() { clear('body'); clear('comment'); persisted = []; render(); },
      update(body, comments) { persisted = [body || '', ...(comments || []).map(comment => comment.html || '')].flatMap(collect); render(); },
      setContext(canWrite, saved) {
        allowed = canWrite; hasTask = saved;
        for (const id of ['attachTaskFileBtn', 'taskFileInput']) { const node = document.getElementById(id); if (node) node.disabled = !allowed; }
        for (const id of ['attachCommentFileBtn', 'commentFileInput']) { const node = document.getElementById(id); if (node) node.disabled = !allowed || !hasTask; }
        render();
      },
    };
  }
  let viewer;
  document.addEventListener('click', event => {
    const link = event.target.closest('[data-task-image-preview]'); if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const source = safeUrl(link.dataset.taskImagePreview, true); if (!source) return;
    event.preventDefault();
    if (!viewer) {
      viewer = document.createElement('dialog'); viewer.className = 'tasks-image-viewer'; viewer.setAttribute('aria-label', 'Просмотр изображения');
      viewer.innerHTML = '<button type="button" class="btn-close" aria-label="Закрыть изображение" autofocus></button><img alt="Вложение задачи">'; document.body.append(viewer);
      viewer.querySelector('button').addEventListener('click', () => viewer.close());
      viewer.addEventListener('keydown', event => { if (event.key === 'Escape') event.stopPropagation(); });
      document.addEventListener('focusin', event => { if (viewer.open && viewer.contains(event.target)) event.stopImmediatePropagation(); }, true);
    }
    viewer.querySelector('img').src = source; if (!viewer.open) viewer.showModal();
  });
  window.TaskAttachments = { create, compactComment };
})();

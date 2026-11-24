/* Draft uploads, persistent references and media use the same task access scope. */
(function () {
  'use strict';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  const imageTypes = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);
  const canonical = value => String(value || '').replace(/\?preview=true$/, '');
  function sizeLabel(bytes) {
    const size = Number(bytes);
    return size > 0 ? (size >= 1048576 ? `${(size / 1048576).toFixed(1)} МБ` : `${Math.max(1, Math.ceil(size / 1024))} КБ`) : '';
  }
  function safeUrl(value, image = false) {
    if (typeof value !== 'string' || !value) return '';
    if (image && /^data:image\/(?:png|jpeg|gif|webp);base64,[a-z0-9+/=\s]+$/i.test(value)) return value;
    try { const url = new URL(value, location.origin); return ['http:', 'https:'].includes(url.protocol) && !url.username && !url.password ? url.href : ''; }
    catch (_) { return ''; }
  }
  function collect(html, origin = {}) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    const items = [];
    const selector = 'figure[data-task-attachment], a[data-task-attachment], a[download], img, video, audio';
    root.querySelectorAll(selector).forEach(node => {
      if (node.parentElement?.closest(selector)) return;
      const media = node.matches('img, video, audio') ? node : node.querySelector('img, video, audio');
      const anchor = node.matches('a') ? node : node.querySelector('a') || node.closest('a');
      const reference = canonical(anchor?.getAttribute('href') || media?.getAttribute('src'));
      const href = safeUrl(reference, media?.matches('img')); if (!href) return;
      const name = node.dataset.attachmentName || anchor?.getAttribute('download') || media?.getAttribute('alt') || node.textContent.trim() || 'Медиафайл';
      let type = node.dataset.attachmentType || (media?.matches('img') ? 'image/' : media?.matches('video') ? 'video/' : media?.matches('audio') ? 'audio/' : '');
      if (!type && /\.(png|jpe?g|gif|webp)(?:$|\?)/i.test(reference)) type = 'image/';
      if (!type && /\.(mp4|webm)(?:$|\?)/i.test(reference)) type = 'video/';
      if (!type && /\.(mp3|wav|ogg)(?:$|\?)/i.test(reference)) type = 'audio/';
      const preview = type.startsWith('image/') || type.startsWith('video/') || type.startsWith('audio/')
        ? safeUrl(media?.getAttribute('src') || (reference.startsWith('/api/tasks/') ? reference + '?preview=true' : reference), type.startsWith('image/')) : '';
      items.push({ href, reference, preview, type, name, size: node.dataset.attachmentSize || '', ...origin });
    });
    return items;
  }
  function previewMarkup(item, small = false) {
    const attributes = item.preview ? `data-task-media-preview="${escape(item.preview)}" data-task-media-type="${escape(item.type)}"` : `download="${escape(item.name)}"`;
    const thumbnail = item.preview && item.type.startsWith('image/') ? `<img src="${escape(item.preview)}" alt="${escape(item.name)}" loading="lazy">`
      : item.preview && item.type.startsWith('video/') ? `<video src="${escape(item.preview)}" muted playsinline preload="metadata" aria-label="${escape(item.name)}"></video><i class="bi bi-play-fill" aria-hidden="true"></i>`
      : `<i class="bi ${item.type.startsWith('audio/') ? 'bi-soundwave' : 'bi-file-earmark'}" aria-hidden="true"></i>`;
    return `<a class="${small ? 'tasks-comment-media' : 'tasks-attachment__preview'}" href="${escape(item.href)}" ${attributes} data-task-media-name="${escape(item.name)}" aria-label="${escape(item.name)}">${thumbnail}${small ? '<span>' + escape(item.name) + '</span>' : ''}</a>`;
  }
  function compactComment(html) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    const selector = 'figure[data-task-attachment], a[data-task-attachment], a[download], img, video, audio';
    root.querySelectorAll(selector).forEach(node => {
      if (!root.contains(node) || node.parentElement?.closest(selector)) return;
      const item = collect(node.outerHTML)[0]; if (!item) return;
      const holder = document.createElement('div'); holder.innerHTML = previewMarkup(item, true);
      if (node.parentElement?.matches('a')) node.parentElement.replaceWith(holder.firstElementChild);
      else node.replaceWith(holder.firstElementChild);
    });
    return root.innerHTML;
  }
  function editableText(html) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    root.querySelectorAll('figure[data-task-attachment], a[data-task-attachment], a[download], img, video, audio').forEach(node => {
      if (root.contains(node)) node.remove();
    });
    return root.innerHTML;
  }
  function removeSavedReference(html, reference) {
    const root = document.createElement('div'); root.innerHTML = html || '';
    root.querySelectorAll('a[href], img[src], video[src], audio[src], source[src]').forEach(node => {
      if (!root.contains(node) || canonical(node.getAttribute(node.hasAttribute('href') ? 'href' : 'src')) !== canonical(reference)) return;
      (node.closest('figure[data-task-attachment]') || node).remove();
    });
    return root.innerHTML;
  }
  function create(options) {
    const queues = { body: [], comment: [] }; let persisted = [], displayed = [], allowed = false, hasTask = false, commentAllowed = true, sequence = 0, removing = false;
    const panel = document.getElementById('taskAttachments'), count = document.getElementById('taskAttachmentsCount');
    const commentList = document.getElementById('taskCommentFiles');
    function render() {
      const selected = [...queues.body.map(file => ({ ...file, scope: 'body' })), ...queues.comment.map(file => ({ ...file, scope: 'comment' }))];
      const seen = new Set(); displayed = persisted.filter(item => { const key = item.scope + ':' + (item.commentId || '') + ':' + item.reference; if (seen.has(key)) return false; seen.add(key); return true; });
      if (count) count.textContent = displayed.length + selected.length || '';
      if (panel) panel.innerHTML = [
        ...displayed.map((item,index) => `<article class="tasks-attachment" data-attachment-origin="${item.scope}">${previewMarkup(item)}<div class="tasks-attachment__copy"><a href="${escape(item.href)}" ${item.preview ? 'data-task-media-preview="' + escape(item.preview) + '" data-task-media-type="' + escape(item.type) + '"' : 'download="' + escape(item.name) + '"'} data-task-media-name="${escape(item.name)}" title="${escape(item.name)}">${escape(item.name)}</a><small>${escape(item.originLabel)}${item.size ? ' · ' + escape(sizeLabel(item.size)) : ''}</small></div><a class="tasks-attachment__download" href="${escape(item.href)}" download="${escape(item.name)}" aria-label="Скачать ${escape(item.name)}" title="Скачать"><i class="bi bi-download" aria-hidden="true"></i></a>${allowed ? '<button type="button" class="tasks-attachment__remove" data-remove-saved-task-file="' + index + '" aria-label="Удалить ' + escape(item.name) + '" title="Удалить вложение" ' + (removing ? 'disabled' : '') + '><i class="bi bi-trash" aria-hidden="true"></i></button>' : ''}</article>`),
        ...selected.map(item => `<article class="tasks-attachment"><span class="tasks-attachment__preview">${item.preview ? '<img src="' + escape(item.preview) + '" alt="">' : '<i class="bi bi-file-earmark" aria-hidden="true"></i>'}</span><div class="tasks-attachment__copy"><strong title="${escape(item.file.name)}">${escape(item.file.name)}</strong><small>${escape(sizeLabel(item.file.size))} · ${item.scope === 'body' ? 'Описание · после сохранения' : 'Чат · с комментарием'}</small></div><button type="button" class="tasks-attachment__remove" data-remove-task-file="${item.id}" data-file-scope="${item.scope}" aria-label="Убрать ${escape(item.file.name)}">×</button></article>`),
      ].join('') || '<span class="small text-muted">Вложений пока нет</span>';
      if (commentList) commentList.innerHTML = queues.comment.map(item => `<span class="tasks-file-chip"><span>${escape(item.file.name)}</span><button type="button" data-remove-task-file="${item.id}" data-file-scope="comment" aria-label="Убрать ${escape(item.file.name)}">×</button></span>`).join('');
    }
    function changed(scope) { render(); options.onChange?.(scope); }
    function add(scope, files) {
      if (!allowed || (scope === 'comment' && (!hasTask || !commentAllowed))) return;
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

    options.modal.addEventListener('click', async event => {
      const saved = event.target.closest('[data-remove-saved-task-file]');
      if (saved) {
        if (!allowed || removing) return;
        const item = displayed[Number(saved.dataset.removeSavedTaskFile)]; if (!item) return;
        if (!window.confirm('Удалить «' + item.name + '» из ' + (item.scope === 'body' ? 'описания задачи' : 'комментария') + '?')) return;
        removing = true; render();
        try { await options.onRemove?.(item); } catch (error) { options.onError?.(error); }
        finally { removing = false; render(); }
        return;
      }
      const remove = event.target.closest('[data-remove-task-file]'); if (!remove) return;
      const scope = remove.dataset.fileScope;
      if (!allowed || (scope === 'comment' && (!hasTask || !commentAllowed)) || !queues[scope]) return;
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
        if (allowed && (scope !== 'comment' || hasTask && commentAllowed)) { event.dataTransfer.dropEffect = 'copy'; dropTarget.classList.add('is-file-drop-target'); }
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
    options.modal.addEventListener('click', event => {
      if (event.target.closest('[data-task-media-preview]') || event.target.closest('a[download].tasks-attachment__download')) return;
      const media = event.target.closest('#bodyEditor img, #bodyEditor video, #bodyEditor audio');
      const anchor = event.target.closest('#bodyEditor a');
      if (!media && !anchor) return;
      const node = (media || anchor).closest('figure[data-task-attachment]') || anchor || media;
      const item = collect(node.outerHTML)[0]; if (!item?.preview) return;
      event.preventDefault(); openMedia(item, media || anchor, persisted);
    });
    return {
      files: scope => queues[scope].map(item => item.file), clear,
      setCommentEnabled(value) { commentAllowed = value; },
      reset() { clear('body'); clear('comment'); persisted = []; render(); },
      update(body, comments) {
        persisted = [...collect(body, { scope: 'body', commentId: null, originLabel: 'Описание задачи' }), ...(comments || []).flatMap((comment,index) => collect(comment.html, { scope: 'comment', commentId: comment.id, originLabel: 'Чат · комментарий ' + (index + 1) }))]; render();
      },
      setContext(canWrite, saved) {
        allowed = canWrite; hasTask = saved;
        for (const id of ['attachTaskFileBtn', 'taskFileInput']) { const node = document.getElementById(id); if (node) node.disabled = !allowed; }
        for (const id of ['attachCommentFileBtn', 'commentFileInput']) { const node = document.getElementById(id); if (node) node.disabled = !allowed || !hasTask; }
        render();
      },
    };
  }
  function openMedia(item, trigger, gallery = []) {
    if (item.type.startsWith('image/')) {
      const images = gallery.filter(file => file.type.startsWith('image/') && file.preview);
      let index = images.findIndex(file => file.preview === item.preview); if (index < 0) { images.push(item); index = images.length - 1; }
      window.IguanaNoteContent?.openPhotos(images.map(file => ({ photo: file.preview, download: file.href, name: file.name, alt: file.name + (file.originLabel ? ' · ' + file.originLabel : '') })), index, trigger, 'Вложения задачи');
      return;
    }
    if (!item.type.startsWith('video/') && !item.type.startsWith('audio/')) return;
    const viewer = document.createElement('dialog'); viewer.className = 'tasks-media-viewer'; viewer.setAttribute('aria-label', item.name);
    const media = document.createElement(item.type.startsWith('video/') ? 'video' : 'audio'); media.controls = true; media.src = item.preview;
    const title = document.createElement('strong'); title.textContent = item.name;
    const close = document.createElement('button'); close.type = 'button'; close.className = 'btn btn-sm btn-outline-secondary'; close.textContent = 'Закрыть ×';
    const download = document.createElement('a'); download.className = 'btn btn-sm btn-outline-secondary'; download.href = item.href; download.download = item.name; download.textContent = 'Скачать';
    const header = document.createElement('header'); header.append(title,close); viewer.append(header,media,download);
    (trigger.closest('.modal.show') || document.body).append(viewer);
    const finish = () => { media.pause(); media.removeAttribute('src'); media.load(); viewer.remove(); trigger?.focus({ preventScroll: true }); };
    close.addEventListener('click', () => viewer.close()); viewer.addEventListener('close',finish,{once:true});
    viewer.addEventListener('keydown',event => { if(event.key === 'Escape') event.stopPropagation(); }); viewer.showModal(); close.focus();
  }
  document.addEventListener('click', event => {
    const link = event.target.closest('[data-task-media-preview]'); if (!link || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const type = link.dataset.taskMediaType || 'image/', preview = safeUrl(link.dataset.taskMediaPreview,type.startsWith('image/')); if (!preview) return;
    event.preventDefault();
    const gallery = collect(document.getElementById('bodyEditor')?.innerHTML);
    document.querySelectorAll('#comments [data-task-media-preview]').forEach(node => gallery.push({ type: node.dataset.taskMediaType, preview: node.dataset.taskMediaPreview, href: node.href, name: node.dataset.taskMediaName || node.textContent }));
    openMedia({ type, preview, href: link.href, name: link.dataset.taskMediaName || link.textContent },link,gallery);
  });
  window.TaskAttachments = { create, compactComment, editableText, removeSavedReference };
})();

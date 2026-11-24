/* Notes remain plain text; only protected local raster-photo references are rendered. */
(function (root) {
  'use strict';
  const prefix = '/api/attachments/knowledge-base/';
  const viewers = new WeakMap();
  function photoPath(path) {
    if (typeof path !== 'string' || !path.startsWith(prefix)) return null;
    const filename = path.slice(prefix.length);
    if (!filename || !/^[a-zA-Z0-9%_.~-]+$/.test(filename)) return null;
    try {
      const decoded = decodeURIComponent(filename);
      if (/[\\/\u0000-\u001f?#]/.test(decoded) || decoded.includes('..')) return null;
      return /\.(png|jpe?g|gif|webp)$/i.test(decoded) ? path : null;
    } catch (_error) { return null; }
  }
  function parts(body) {
    const text = String(body || '');
    const pattern = /!\[([^\]\r\n]*)\]\((\/api\/attachments\/knowledge-base\/[^\s)]+)\)/g;
    const result = []; let cursor = 0, match;
    while ((match = pattern.exec(text))) {
      if (!photoPath(match[2])) continue;
      if (match.index > cursor) result.push({ text: text.slice(cursor, match.index) });
      result.push({ photo: match[2], alt: match[1] || 'Фото к заметке' });
      cursor = pattern.lastIndex;
    }
    if (cursor < text.length) result.push({ text: text.slice(cursor) });
    return result;
  }
  function matches(note, query) {
    const normalize = text => String(text || '').normalize('NFKC').toLocaleLowerCase('ru-RU').replaceAll('ё', 'е');
    return normalize(`${note?.title || ''}\n${note?.body || ''}`).includes(normalize(query).trim());
  }

  function photoViewer(doc) {
    if (viewers.has(doc)) return viewers.get(doc);
    const dialog = doc.createElement('dialog');
    dialog.className = 'note-photo-viewer';
    dialog.setAttribute('aria-labelledby', 'notePhotoViewerTitle');
    dialog.innerHTML = `<header class="note-photo-viewer__header">
      <h2 id="notePhotoViewerTitle">Фотографии заметки</h2>
      <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-close aria-label="Закрыть просмотр">Закрыть ×</button>
    </header>
    <div class="note-photo-viewer__viewport">
      <button type="button" class="btn btn-outline-secondary note-photo-viewer__nav" data-note-photo-prev aria-label="Предыдущее фото">‹</button>
      <div class="note-photo-viewer__stage"><img alt="" draggable="false" referrerpolicy="same-origin" data-note-photo-image></div>
      <button type="button" class="btn btn-outline-secondary note-photo-viewer__nav" data-note-photo-next aria-label="Следующее фото">›</button>
    </div>
    <footer class="note-photo-viewer__footer">
      <div class="note-photo-viewer__caption" data-note-photo-caption></div>
      <div role="status" class="note-photo-viewer__status" data-note-photo-status></div>
      <div class="note-photo-viewer__toolbar">
        <span data-note-photo-counter aria-live="polite" aria-atomic="true"></span>
        <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-zoom aria-pressed="false">Исходный размер</button>
      </div>
    </footer>`;
    const image = dialog.querySelector('[data-note-photo-image]');
    const stage = dialog.querySelector('.note-photo-viewer__stage');
    const status = dialog.querySelector('[data-note-photo-status]');
    const zoom = dialog.querySelector('[data-note-photo-zoom]');
    const prev = dialog.querySelector('[data-note-photo-prev]');
    const next = dialog.querySelector('[data-note-photo-next]');
    let items = [], position = 0, trigger = null, touch = null;

    function setZoom(enabled) {
      dialog.classList.toggle('is-zoomed', enabled);
      zoom.setAttribute('aria-pressed', String(enabled));
      zoom.textContent = enabled ? 'Уместить в окне' : 'Исходный размер';
      stage.scrollLeft = enabled ? (stage.scrollWidth - stage.clientWidth) / 2 : 0;
      stage.scrollTop = enabled ? (stage.scrollHeight - stage.clientHeight) / 2 : 0;
    }
    function show() {
      setZoom(false);
      const photo = items[position];
      image.alt = photo.alt;
      zoom.disabled = true;
      status.hidden = false;
      status.textContent = 'Загрузка фотографии…';
      dialog.querySelector('[data-note-photo-caption]').textContent = photo.alt;
      dialog.querySelector('[data-note-photo-counter]').textContent = `${position + 1} / ${items.length}`;
      prev.hidden = next.hidden = items.length < 2;
      image.src = photo.photo;
    }
    function move(delta) {
      if (items.length < 2) return;
      position = (position + delta + items.length) % items.length;
      show();
    }
    function cleanup() {
      image.removeAttribute('src'); items = []; touch = null;
      doc.documentElement.classList.remove('note-photo-viewer-open');
      if (trigger?.isConnected) trigger.focus({ preventScroll: true });
      trigger = null;
    }
    function close() { dialog.close(); cleanup(); }
    image.addEventListener('load', () => { status.hidden = true; zoom.disabled = false; });
    image.addEventListener('error', () => {
      status.hidden = false;
      status.textContent = 'Не удалось загрузить фотографию. Попробуйте открыть её ещё раз.';
      zoom.disabled = true;
    });
    dialog.querySelector('[data-note-photo-close]').addEventListener('click', close);
    prev.addEventListener('click', () => move(-1));
    next.addEventListener('click', () => move(1));
    zoom.addEventListener('click', () => setZoom(!dialog.classList.contains('is-zoomed')));
    dialog.addEventListener('click', event => { if (event.target === dialog) close(); });
    dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
    dialog.addEventListener('keydown', event => {
      if (event.key === 'Tab') {
        const controls = Array.from(dialog.querySelectorAll('button')).filter(button => !button.hidden && !button.disabled);
        const first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && doc.activeElement === first || !event.shiftKey && doc.activeElement === last) {
          event.preventDefault(); event.stopPropagation();
          (event.shiftKey ? last : first).focus();
        }
        return;
      }
      if (!['Escape', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      // Keep Escape inside this viewer when notes are shown in a Bootstrap dialog.
      event.preventDefault(); event.stopPropagation();
      if (event.key === 'Escape') close();
      else move(event.key === 'ArrowRight' ? 1 : -1);
    });
    stage.addEventListener('pointerdown', event => {
      touch = event.pointerType === 'touch' && event.isPrimary && !dialog.classList.contains('is-zoomed')
        ? { x: event.clientX, y: event.clientY, id: event.pointerId } : null;
    });
    stage.addEventListener('pointerup', event => {
      if (!touch || touch.id !== event.pointerId) return;
      const dx = event.clientX - touch.x, dy = event.clientY - touch.y;
      touch = null;
      if (Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.3) move(dx < 0 ? 1 : -1);
    });
    stage.addEventListener('pointercancel', () => { touch = null; });
    // The native close event is queued; it must not clear a gallery already opened again.
    dialog.addEventListener('close', () => { if (!dialog.open) cleanup(); });
    const api = { open(photos, index, opener) {
      items = photos; position = index; trigger = opener;
      // Stay inside the surrounding focus trap while native dialog supplies the top layer.
      const host = opener.closest('.modal.show, dialog[open]') || doc.body;
      if (dialog.parentNode !== host) host.append(dialog);
      show(); dialog.showModal();
      doc.documentElement.classList.add('note-photo-viewer-open');
      dialog.querySelector('[data-note-photo-close]').focus({ preventScroll: true });
    } };
    viewers.set(doc, api);
    return api;
  }

  function render(container, body, maxText = Infinity) {
    container.replaceChildren();
    const doc = container.ownerDocument;
    const content = parts(body);
    const gallery = content.filter(part => part.photo);
    function photoButton(index, label) {
      const button = doc.createElement('button'); button.type = 'button'; button.className = 'note-photo-link';
      button.setAttribute('data-note-photo-open', '');
      button.setAttribute('aria-haspopup', 'dialog');
      button.setAttribute('aria-label', `${label} (${index + 1} из ${gallery.length})`);
      button.addEventListener('click', () => photoViewer(doc).open(gallery, index, button));
      return button;
    }
    let remaining = maxText, photos = 0;
    for (const part of content) {
      if (part.photo) {
        if (photos++ >= 8) continue;
        const link = photoButton(photos - 1, `Смотреть фото: ${part.alt}`);
        const img = doc.createElement('img'); img.src = part.photo; img.alt = part.alt; img.className = 'note-photo'; img.loading = 'lazy'; img.referrerPolicy = 'same-origin';
        link.append(img); container.append(link);
      } else if (remaining > 0 && part.text) {
        const span = doc.createElement('span'); span.className = 'note-body-text';
        span.textContent = part.text.length > remaining ? part.text.slice(0, remaining) + '…' : part.text;
        remaining -= part.text.length;
        container.append(span);
      }
    }
    if (gallery.length > 8) {
      const more = photoButton(8, 'Смотреть остальные фотографии');
      more.classList.add('note-photo-more'); more.textContent = `Ещё фото: ${gallery.length - 8}`;
      container.append(more);
    }
  }
  const api = { photoPath, parts, matches, render };
  if (root) root.IguanaNoteContent = api;
  if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof window === 'object' ? window : null);

/* Notes remain plain text with protected local photos and downloadable file links. */
(function (root) {
  'use strict';
  const prefix = '/api/attachments/knowledge-base/';
  const viewers = new WeakMap();
  function attachmentPath(path) {
    if (typeof path !== 'string' || !path.startsWith(prefix)) return null;
    const filename = path.slice(prefix.length);
    if (!filename || !/^[a-zA-Z0-9%_.~-]+$/.test(filename)) return null;
    try {
      const decoded = decodeURIComponent(filename);
      if (/[\\/\u0000-\u001f?#]/.test(decoded) || decoded.includes('..')) return null;
      return path;
    } catch (_error) { return null; }
  }
  function photoPath(path) {
    return attachmentPath(path) && /\.(png|jpe?g|gif|webp)$/i.test(decodeURIComponent(path.slice(prefix.length))) ? path : null;
  }
  function parts(body) {
    const text = String(body || '');
    const pattern = /(!?)\[([^\]\r\n]*)\]\((\/api\/attachments\/knowledge-base\/[^\s)]+)\)/g;
    const result = []; let cursor = 0, match;
    while ((match = pattern.exec(text))) {
      if (!(match[1] ? photoPath(match[3]) : attachmentPath(match[3]))) continue;
      if (match.index > cursor) result.push({ text: text.slice(cursor, match.index) });
      result.push(match[1] ? { photo: match[3], alt: match[2] || 'Фото к заметке' }
        : { file: match[3], name: match[2] || decodeURIComponent(match[3].slice(prefix.length)) });
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
    </header><div class="note-photo-viewer__viewport">
      <button type="button" class="btn btn-outline-secondary note-photo-viewer__nav" data-note-photo-prev aria-label="Предыдущее фото">‹</button>
      <div class="note-photo-viewer__stage"><div class="note-photo-viewer__canvas"><img alt="" draggable="false" referrerpolicy="same-origin" data-note-photo-image></div></div>
      <button type="button" class="btn btn-outline-secondary note-photo-viewer__nav" data-note-photo-next aria-label="Следующее фото">›</button>
    </div><footer class="note-photo-viewer__footer">
      <div class="note-photo-viewer__caption" data-note-photo-caption></div>
      <div role="status" class="note-photo-viewer__status" data-note-photo-status></div>
      <div class="note-photo-viewer__toolbar">
        <span data-note-photo-counter aria-live="polite" aria-atomic="true"></span>
        <div class="note-photo-viewer__scale-controls">
          <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-minus aria-label="Уменьшить">−</button>
          <label class="note-photo-viewer__scale-label"><input type="number" min="1" max="800" step="10" value="100" data-note-photo-scale aria-label="Масштаб в процентах"> %</label>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-plus aria-label="Увеличить">+</button>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-zoom>100%</button>
          <button type="button" class="btn btn-sm btn-outline-secondary" data-note-photo-fit>Уместить</button>
        </div>
        <a class="btn btn-sm btn-outline-secondary" data-note-photo-download download>Скачать</a>
      </div>
    </footer>`;
    const image = dialog.querySelector('[data-note-photo-image]'), stage = dialog.querySelector('.note-photo-viewer__stage');
    const canvas = dialog.querySelector('.note-photo-viewer__canvas'), status = dialog.querySelector('[data-note-photo-status]');
    const scaleInput = dialog.querySelector('[data-note-photo-scale]'), download = dialog.querySelector('[data-note-photo-download]');
    const prev = dialog.querySelector('[data-note-photo-prev]'), next = dialog.querySelector('[data-note-photo-next]');
    const scaling = Array.from(dialog.querySelectorAll('.note-photo-viewer__scale-controls button, .note-photo-viewer__scale-controls input'));
    let items = [], position = 0, trigger = null, scale = 1, autoFit = true, pointer = null, pinch = null, frame = 0;
    const touches = new Map();
    const ready = () => dialog.open && image.complete && image.naturalWidth > 0 && !image.hidden;
    const fitScale = () => Math.min(1, (stage.clientWidth - 2) / image.naturalWidth, (stage.clientHeight - 2) / image.naturalHeight);
    function setScale(value, anchor = { x: stage.clientWidth / 2, y: stage.clientHeight / 2 }) {
      if (!ready() || !Number.isFinite(value) || value <= 0) return;
      const oldLeft = Math.max(0, (canvas.offsetWidth - image.naturalWidth * scale) / 2);
      const oldTop = Math.max(0, (canvas.offsetHeight - image.naturalHeight * scale) / 2);
      const x = (stage.scrollLeft + anchor.x - oldLeft) / scale, y = (stage.scrollTop + anchor.y - oldTop) / scale;
      scale = Math.max(Math.min(0.01, fitScale()), Math.min(8, value));
      const width = image.naturalWidth * scale, height = image.naturalHeight * scale;
      const areaWidth = Math.max(stage.clientWidth, width), areaHeight = Math.max(stage.clientHeight, height);
      canvas.style.width = `${areaWidth}px`; canvas.style.height = `${areaHeight}px`;
      image.style.width = `${width}px`; image.style.height = `${height}px`;
      image.style.left = `${(areaWidth - width) / 2}px`; image.style.top = `${(areaHeight - height) / 2}px`;
      stage.scrollLeft = autoFit ? 0 : x * scale + (areaWidth - width) / 2 - anchor.x;
      stage.scrollTop = autoFit ? 0 : y * scale + (areaHeight - height) / 2 - anchor.y;
      scaleInput.value = String(Math.round(scale * 100));
      dialog.classList.toggle('is-zoomed', scale > fitScale() + 0.001);
    }
    function zoom(value, anchor) { autoFit = false; setScale(value, anchor); }
    function fit() { autoFit = true; setScale(fitScale()); }
    function show() {
      const photo = items[position];
      autoFit = true; scale = 1; pointer = null; pinch = null; touches.clear();
      image.hidden = true; image.alt = photo.alt;
      scaling.forEach(control => { control.disabled = true; });
      status.hidden = false; status.textContent = 'Загрузка фотографии…';
      dialog.querySelector('[data-note-photo-caption]').textContent = photo.alt;
      dialog.querySelector('[data-note-photo-counter]').textContent = `${position + 1} / ${items.length}`;
      prev.hidden = next.hidden = items.length < 2;
      dialog.classList.toggle('has-single-photo', items.length < 2);
      download.href = photo.photo;
      download.download = decodeURIComponent(photo.photo.slice(prefix.length));
      image.src = photo.photo;
    }
    function move(delta) { if (items.length > 1) { position = (position + delta + items.length) % items.length; show(); } }
    function cleanup() {
      image.removeAttribute('src'); image.hidden = true; items = []; touches.clear(); pointer = null; pinch = null;
      doc.defaultView.cancelAnimationFrame(frame);
      doc.documentElement.classList.remove('note-photo-viewer-open');
      if (trigger?.isConnected) trigger.focus({ preventScroll: true });
      trigger = null;
    }
    function close() { dialog.close(); cleanup(); }
    image.addEventListener('load', () => {
      if (!dialog.open || !image.complete || !image.naturalWidth) return;
      image.hidden = false; status.hidden = true; scaling.forEach(control => { control.disabled = false; }); fit();
    });
    image.addEventListener('error', () => {
      if (!dialog.open) return;
      status.hidden = false; status.textContent = 'Не удалось загрузить фотографию. Попробуйте открыть её ещё раз.';
      scaling.forEach(control => { control.disabled = true; });
    });
    dialog.querySelector('[data-note-photo-close]').addEventListener('click', close);
    prev.addEventListener('click', () => move(-1)); next.addEventListener('click', () => move(1));
    dialog.querySelector('[data-note-photo-minus]').addEventListener('click', () => zoom(scale / 1.25));
    dialog.querySelector('[data-note-photo-plus]').addEventListener('click', () => zoom(scale * 1.25));
    dialog.querySelector('[data-note-photo-zoom]').addEventListener('click', () => zoom(1));
    dialog.querySelector('[data-note-photo-fit]').addEventListener('click', fit);
    scaleInput.addEventListener('change', () => zoom(scaleInput.valueAsNumber / 100));
    stage.addEventListener('wheel', event => {
      if (!ready() || !event.deltaY) return;
      event.preventDefault(); const rect = stage.getBoundingClientRect();
      zoom(scale * (event.deltaY < 0 ? 1.15 : 1 / 1.15), { x: event.clientX - rect.left, y: event.clientY - rect.top });
    }, { passive: false });
    dialog.addEventListener('click', event => { if (event.target === dialog) close(); });
    dialog.addEventListener('cancel', event => { event.preventDefault(); close(); });
    dialog.addEventListener('keydown', event => {
      if (event.key === 'Tab') {
        const controls = Array.from(dialog.querySelectorAll('button, a[href], input')).filter(control => !control.hidden && !control.disabled);
        const first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && doc.activeElement === first || !event.shiftKey && doc.activeElement === last) {
          event.preventDefault(); event.stopPropagation(); (event.shiftKey ? last : first).focus();
        }
        return;
      }
      if (event.key !== 'Escape' && event.target === scaleInput) return;
      if (!['Escape', 'ArrowLeft', 'ArrowRight', '+', '-', '0'].includes(event.key)) return;
      event.preventDefault(); event.stopPropagation();
      if (event.key === 'Escape') close();
      else if (event.key === '+') zoom(scale * 1.25);
      else if (event.key === '-') zoom(scale / 1.25);
      else if (event.key === '0') fit();
      else move(event.key === 'ArrowRight' ? 1 : -1);
    });
    const distance = () => { const [a, b] = Array.from(touches.values()); return Math.hypot(a.x - b.x, a.y - b.y); };
    stage.addEventListener('pointerdown', event => {
      if (!ready() || event.button !== 0) return;
      if (event.pointerType === 'touch') touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      stage.setPointerCapture(event.pointerId);
      if (touches.size === 2) { pinch = { distance: distance(), scale }; pointer = null; return; }
      pointer = { id: event.pointerId, x: event.clientX, y: event.clientY, left: stage.scrollLeft, top: stage.scrollTop,
        pan: dialog.classList.contains('is-zoomed'), touch: event.pointerType === 'touch' };
      event.preventDefault();
    });
    stage.addEventListener('pointermove', event => {
      if (touches.has(event.pointerId)) touches.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pinch && touches.size === 2) { zoom(pinch.scale * distance() / Math.max(1, pinch.distance)); return; }
      if (!pointer || pointer.id !== event.pointerId || !pointer.pan) return;
      stage.scrollLeft = pointer.left - (event.clientX - pointer.x); stage.scrollTop = pointer.top - (event.clientY - pointer.y);
    });
    stage.addEventListener('pointerup', event => {
      touches.delete(event.pointerId);
      if (pinch) { pinch = null; pointer = null; return; }
      if (!pointer || pointer.id !== event.pointerId) return;
      const dx = event.clientX - pointer.x, dy = event.clientY - pointer.y;
      if (pointer.touch && !pointer.pan && Math.abs(dx) > 50 && Math.abs(dx) > Math.abs(dy) * 1.3) move(dx < 0 ? 1 : -1);
      pointer = null;
    });
    stage.addEventListener('pointercancel', () => { touches.clear(); pointer = null; pinch = null; });
    new doc.defaultView.ResizeObserver(() => {
      doc.defaultView.cancelAnimationFrame(frame);
      frame = doc.defaultView.requestAnimationFrame(() => { if (ready()) { if (autoFit) fit(); else setScale(scale); } });
    }).observe(stage);
    // A queued close event must not clear a gallery opened again in the meantime.
    dialog.addEventListener('close', () => { if (!dialog.open) cleanup(); });
    const api = { open(photos, index, opener) {
      items = photos; position = index; trigger = opener;
      const host = opener.closest('.modal.show, dialog[open]') || doc.body;
      if (dialog.parentNode !== host) host.append(dialog);
      show(); dialog.showModal(); doc.documentElement.classList.add('note-photo-viewer-open');
      dialog.querySelector('[data-note-photo-close]').focus({ preventScroll: true });
    } };
    viewers.set(doc, api); return api;
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
      } else if (part.file) {
        const link = doc.createElement('a'); link.className = 'note-file-link'; link.href = part.file;
        link.download = decodeURIComponent(part.file.slice(prefix.length));
        link.setAttribute('aria-label', `Скачать файл: ${part.name}`);
        const name = doc.createElement('strong'); name.textContent = part.name;
        const action = doc.createElement('span'); action.textContent = 'Скачать файл';
        link.append(name, action); container.append(link);
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
  const api = { attachmentPath, photoPath, parts, matches, render };
  if (root) root.IguanaNoteContent = api;
  if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof window === 'object' ? window : null);

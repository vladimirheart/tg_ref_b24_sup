/* Notes remain plain text; only protected local raster-photo references are rendered. */
(function (root) {
  'use strict';
  const prefix = '/api/attachments/knowledge-base/';
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
  function render(container, body, maxText = Infinity) {
    container.replaceChildren();
    const doc = container.ownerDocument;
    let remaining = maxText, photos = 0;
    for (const part of parts(body)) {
      if (part.photo) {
        if (photos++ >= 8) continue;
        const link = doc.createElement('a'); link.href = part.photo; link.target = '_blank'; link.rel = 'noopener'; link.className = 'note-photo-link';
        link.setAttribute('aria-label', `Открыть или скачать: ${part.alt}`);
        const img = doc.createElement('img'); img.src = part.photo; img.alt = part.alt; img.className = 'note-photo'; img.loading = 'lazy'; img.referrerPolicy = 'same-origin';
        link.append(img); container.append(link);
      } else if (remaining > 0 && part.text) {
        const span = doc.createElement('span'); span.className = 'note-body-text';
        span.textContent = part.text.length > remaining ? part.text.slice(0, remaining) + '…' : part.text;
        remaining -= part.text.length;
        container.append(span);
      }
    }
  }
  const api = { photoPath, parts, matches, render };
  if (root) root.IguanaNoteContent = api;
  if (typeof module === 'object' && module.exports) module.exports = api;
})(typeof window === 'object' ? window : null);

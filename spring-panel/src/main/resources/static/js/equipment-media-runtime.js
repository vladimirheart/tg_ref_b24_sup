(function () {
  if (window.EquipmentMediaRuntime) {
    return;
  }

  function text(value) {
    return value == null ? '' : String(value).trim();
  }

  function normalize(value) {
    return text(value).toLocaleLowerCase('ru-RU').replace(/\s+/g, ' ');
  }

  function parse(raw) {
    const empty = { links: [], photos: [] };
    if (Array.isArray(raw)) {
      return { links: raw.map(text).filter(Boolean), photos: [] };
    }
    if (raw && typeof raw === 'object') {
      return {
        links: Array.isArray(raw.links) ? raw.links.map(text).filter(Boolean) : [],
        photos: Array.isArray(raw.photos) ? raw.photos.filter((photo) => photo && typeof photo === 'object') : [],
      };
    }
    const value = text(raw);
    if (!value) return empty;
    if (value.startsWith('[') || value.startsWith('{')) {
      try {
        return parse(JSON.parse(value));
      } catch (error) {
        // Legacy newline-separated links remain supported below.
      }
    }
    return { links: value.split(/\r?\n/).map(text).filter(Boolean), photos: [] };
  }

  function coverPhoto(raw) {
    const photos = parse(raw).photos;
    return photos.find((photo) => normalize(photo && photo.category) === 'title') || photos[0] || null;
  }

  function coverUrl(raw) {
    const selected = coverPhoto(raw);
    return selected && selected.url ? text(selected.url) : '';
  }

  window.EquipmentMediaRuntime = Object.freeze({
    parse,
    coverPhoto,
    coverUrl,
  });
}());

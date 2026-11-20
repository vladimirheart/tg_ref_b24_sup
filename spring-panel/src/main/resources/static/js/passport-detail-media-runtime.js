(function () {
  if (window.PassportDetailMediaRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailMediaRuntime requires coreRuntime');
    }
    const { text, escapeHtml, normalizeKey, first } = coreRuntime;
    let photoViewerPosition = 0;
    let viewerPhotos = [];
    let photoTrigger = null;

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function renderPhotos() {
        const passport = resolvePassport();
        const grid = document.getElementById('passportPhotoGrid');
        const photos = Array.isArray(passport.photos) ? passport.photos : [];
        document.getElementById('passportPhotosEmpty').classList.toggle('d-none', photos.length > 0);
        grid.innerHTML = photos.map((photo, index) => {
            const url = text(photo && photo.url);
            const caption = first(photo && photo.caption, photo && photo.original_name, 'Фото объекта');
            const category = normalizeKey(photo && photo.category) === 'title' ? 'Титульное' : 'Архивное';
            return `<figure class="passport-photo-tile" ${url ? `data-passport-photo-index="${index}" tabindex="0" role="button" aria-label="Открыть фото: ${escapeHtml(caption)}"` : ''}>
                ${url ? `<img src="${escapeHtml(url)}" alt="${escapeHtml(caption)}" loading="lazy">` : '<div class="passport-photo-tile__empty">Нет файла</div>'}
                <figcaption><span>${escapeHtml(category)}</span><strong>${escapeHtml(caption)}</strong></figcaption>
            </figure>`;
        }).join('');
    }


    function viewablePhotos() {
        const passport = resolvePassport();
        const photos = Array.isArray(passport.photos) ? passport.photos : [];
        return photos.map((photo, originalIndex) => ({ photo, originalIndex })).filter((entry) => text(entry.photo && entry.photo.url));
    }

    function renderPhotoViewer() {
        const viewer = document.getElementById('passportPhotoViewer');
        const items = viewerPhotos;
        if (!viewer || !items.length) return;
        photoViewerPosition = ((photoViewerPosition % items.length) + items.length) % items.length;
        const entry = items[photoViewerPosition];
        const photo = entry || {};
        const caption = first(photo.caption, photo.original_name, 'Фото объекта');
        document.getElementById('passportPhotoViewerImage').src = text(photo.url);
        document.getElementById('passportPhotoViewerImage').alt = caption;
        document.getElementById('passportPhotoViewerCaption').textContent = caption;
        document.getElementById('passportPhotoViewerCategory').textContent = photo.categoryLabel || (normalizeKey(photo.category) === 'title' ? 'Титульное' : 'Архивное');
        document.getElementById('passportPhotoViewerCounter').textContent = `${photoViewerPosition + 1} / ${items.length}`;
        viewer.querySelectorAll('[data-passport-photo-prev], [data-passport-photo-next]').forEach((button) => button.hidden = items.length < 2);
    }

    function openPhotoViewer(originalIndex) {
        const items = viewablePhotos();
        const position = items.findIndex((entry) => entry.originalIndex === Number(originalIndex));
        if (position < 0) return;
        openPhotoGallery(items.map((entry) => entry.photo), position);
    }

    function openPhotoGallery(photos, position = 0, trigger = document.activeElement) {
        const viewer = document.getElementById('passportPhotoViewer');
        const items = Array.isArray(photos) ? photos.filter((photo) => text(photo && photo.url)) : [];
        if (!viewer || !items.length) return;
        viewerPhotos = items;
        photoViewerPosition = position;
        photoTrigger = trigger;
        renderPhotoViewer();
        viewer.hidden = false;
        viewer.setAttribute('aria-hidden', 'false');
        document.body.classList.add('passport-photo-viewer-open');
        viewer.querySelector('.passport-photo-viewer__close')?.focus({ preventScroll: true });
    }

    function closePhotoViewer() {
        const viewer = document.getElementById('passportPhotoViewer');
        if (!viewer) return;
        viewer.hidden = true;
        viewer.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('passport-photo-viewer-open');
        if (photoTrigger?.isConnected) photoTrigger.focus({ preventScroll: true });
        photoTrigger = null;
        viewerPhotos = [];
    }

    function movePhotoViewer(delta) {
        const items = viewerPhotos;
        if (items.length < 2) return;
        photoViewerPosition = (photoViewerPosition + delta + items.length) % items.length;
        renderPhotoViewer();
    }


    return Object.freeze({
      renderPhotos,
      openPhotoViewer,
      openPhotoGallery,
      closePhotoViewer,
      movePhotoViewer,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailMediaRuntime = Object.freeze({
    mount,
  });
}());

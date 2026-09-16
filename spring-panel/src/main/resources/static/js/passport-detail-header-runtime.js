(function () {
  if (window.PassportDetailHeaderRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const getPassportId = typeof options.getPassportId === 'function' ? options.getPassportId : () => null;
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailHeaderRuntime requires coreRuntime');
    }
    const { text, escapeHtml, normalizeKey, first, setStatus } = coreRuntime;

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function resolvePassportId() {
      const value = getPassportId();
      return value == null ? null : value;
    }

    function renderCover() {
        const passport = resolvePassport();
        const cover = document.getElementById('passportWorkspaceCover');
        if (!cover) return;
        const photos = Array.isArray(passport.photos) ? passport.photos : [];
        const titlePhoto = photos.find((photo) => normalizeKey(photo && photo.category) === 'title') || photos[0];
        const photoIndex = titlePhoto ? photos.indexOf(titlePhoto) : -1;
        const url = titlePhoto ? text(titlePhoto.url) : '';
        delete cover.dataset.passportPhotoIndex;
        if (!url) {
            cover.innerHTML = '<span>PO</span>';
            return;
        }
        cover.dataset.passportPhotoIndex = String(photoIndex);
        cover.innerHTML = `<img src="${escapeHtml(url)}" alt="Фото объекта">`;
    }

    function renderHeader() {
        const passport = resolvePassport();
        const passportId = resolvePassportId();
        const title = first(passport.department, passport.object_name, `Паспорт #${passportId}`);
        document.getElementById('passportWorkspaceTitle').textContent = title;
        setStatus(document.getElementById('passportWorkspaceStatus'), passport.status);

        const subtitle = [passport.business, passport.city, passport.location_address]
            .map(text)
            .filter(Boolean)
            .join(' · ');
        document.getElementById('passportWorkspaceSubtitle').textContent = subtitle || 'Адрес и бизнес не указаны';

        const meta = [];
        if (text(passport.partner_type)) meta.push(passport.partner_type);
        if (text(passport.legal_entity)) meta.push(passport.legal_entity);
        meta.push(`#${passportId}`);
        document.getElementById('passportWorkspaceMeta').innerHTML = meta
            .map((item) => `<span>${escapeHtml(item)}</span>`)
            .join('');

        const source = document.getElementById('passportWorkspaceSource');
        if (source && text(passport.netbox_site_id)) {
            source.textContent = `NetBox site #${passport.netbox_site_id}`;
            source.classList.remove('d-none');
        }

        const legacyLink = document.getElementById('passportLegacyEditLink');
        if (legacyLink) legacyLink.href = `/object-passports/${passportId}/legacy-edit`;
        renderCover();
    }


    return Object.freeze({
      renderCover,
      renderHeader,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailHeaderRuntime = Object.freeze({
    mount,
  });
}());

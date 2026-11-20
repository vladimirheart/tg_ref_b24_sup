(function () {
  if (window.PassportDetailEquipmentRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailEquipmentRuntime requires coreRuntime');
    }
    const { text, escapeHtml, normalizeKey, first, statusTone, parseCatalogMedia, equipmentCover, isEquipmentArchived, findCatalogItem } = coreRuntime;
    const openPhotoGallery = options.openPhotoGallery;
    let activeDetails = null;

    function closeDetails(restoreFocus = false) {
        if (!activeDetails) return;
        const { panel, button, home } = activeDetails;
        activeDetails = null;
        panel.hidden = true;
        home.appendChild(panel);
        button.setAttribute('aria-expanded', 'false');
        if (restoreFocus && button.isConnected) button.focus({ preventScroll: true });
    }

    function positionDetails() {
        if (!activeDetails) return;
        const { panel, button } = activeDetails;
        const rect = button.getBoundingClientRect();
        if (!button.isConnected || !button.getClientRects().length || rect.bottom < 0 || rect.top > innerHeight) {
            closeDetails();
            return;
        }
        const belowSpace = innerHeight - rect.bottom - 20;
        const aboveSpace = rect.top - 20;
        const showBelow = belowSpace >= 240 || belowSpace >= aboveSpace;
        panel.style.maxHeight = `${Math.max(80, Math.min(420, showBelow ? belowSpace : aboveSpace))}px`;
        const width = panel.offsetWidth, height = panel.offsetHeight;
        const left = Math.max(12, Math.min(rect.right - width, innerWidth - width - 12));
        const top = Math.max(12, showBelow ? rect.bottom + 8 : rect.top - height - 8);
        panel.style.left = `${left}px`;
        panel.style.top = `${top}px`;
    }

    document.addEventListener('pointerdown', (event) => {
        if (activeDetails && !activeDetails.panel.contains(event.target) && !activeDetails.button.contains(event.target)) closeDetails();
    });
    document.addEventListener('focusin', (event) => {
        if (activeDetails && !activeDetails.panel.contains(event.target) && event.target !== activeDetails.button) closeDetails();
    });
    document.addEventListener('keydown', (event) => {
        if (activeDetails && event.key === 'Escape') {
            event.preventDefault();
            event.stopImmediatePropagation();
            closeDetails(true);
        }
    }, true);
    window.addEventListener('resize', positionDetails);
    window.addEventListener('scroll', positionDetails, true);

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function equipmentView(item) {
        const catalogItem = findCatalogItem(item || {});
        const type = first(item && item.equipment_type, catalogItem && catalogItem.equipment_type, 'Оборудование');
        const vendor = first(item && item.vendor, item && item.equipment_vendor, catalogItem && catalogItem.equipment_vendor);
        const model = first(item && item.model, item && item.equipment_model, catalogItem && catalogItem.equipment_model);
        const name = first(item && item.name, [vendor, model].filter(Boolean).join(' '), type);
        const archived = isEquipmentArchived(item);
        const status = archived ? 'Архив' : first(item && item.status, '—');
        const ip = first(item && item.ip_address, '');
        const serial = first(item && item.serial_number, '');
        const accessories = first(item && item.accessories, catalogItem && catalogItem.accessories, '');
        const media = parseCatalogMedia(catalogItem && catalogItem.photo_url);
        const links = media.links;
        const cover = equipmentCover(catalogItem && catalogItem.photo_url);
        const catalogId = catalogItem && catalogItem.id ? Number(catalogItem.id) : null;
        const photos = media.photos.filter((photo) => text(photo.url));
        return { item, catalogItem, catalogId, type, vendor, model, name, status, ip, serial, accessories, links, cover, photos, archived };
    }

    const equipmentProfileNameCache = new Map();
    const equipmentProfileRequestCache = new Map();
    let equipmentRenderRevision = 0;
    const expandedEquipmentTypeKeys = new Set();

    function configurationProfileId(view) {
        const id = Number(view && view.item && view.item.configuration_profile_id);
        return Number.isFinite(id) && id > 0 ? id : null;
    }

    function equipmentProfileLabel(profileId) {
        const id = Number(profileId);
        const name = equipmentProfileNameCache.get(id);
        return name ? `${name} (#${id})` : `Профиль #${id}`;
    }

    function loadEquipmentProfiles(equipmentType) {
        const type = text(equipmentType);
        if (!type) return Promise.resolve([]);
        if (equipmentProfileRequestCache.has(type)) return equipmentProfileRequestCache.get(type);
        const url = '/api/settings/it-equipment/profiles?equipmentType=' + encodeURIComponent(type);
        const request = fetch(url, {
            method: 'GET',
            credentials: 'same-origin',
            headers: { Accept: 'application/json' }
        }).then(async (response) => {
            if (!response.ok) return [];
            const payload = await response.json();
            const items = payload && Array.isArray(payload.items) ? payload.items : [];
            items.forEach((profile) => {
                const id = Number(profile && profile.id);
                const name = text(profile && profile.profile_name);
                if (Number.isFinite(id) && id > 0 && name) equipmentProfileNameCache.set(id, name);
            });
            return items;
        }).catch(() => []);
        equipmentProfileRequestCache.set(type, request);
        return request;
    }

    function hydrateEquipmentProfileNames(grid, views, revision) {
        const types = Array.from(new Set(views
            .filter((view) => configurationProfileId(view))
            .map((view) => text(view.type))
            .filter(Boolean)));
        if (!types.length) return;
        Promise.all(types.map(loadEquipmentProfiles)).then(() => {
            if (revision !== equipmentRenderRevision || !grid.isConnected) return;
            views.forEach((view, index) => {
                const profileId = configurationProfileId(view);
                if (!profileId) return;
                const node = document.getElementById(`passportEquipmentDetails${index}`)?.querySelector(`[data-equipment-profile-name="${profileId}"]`);
                if (node) node.textContent = equipmentProfileLabel(profileId);
            });
            positionDetails();
        });
    }

    function bindEquipmentDetailsToggles(grid) {
        grid.querySelectorAll('[data-equipment-details-toggle]').forEach((button) => {
            button.addEventListener('click', () => {
                const panelId = button.getAttribute('aria-controls');
                const panel = panelId ? document.getElementById(panelId) : null;
                if (!panel) return;
                const wasOpen = activeDetails?.button === button;
                closeDetails();
                if (wasOpen) return;
                activeDetails = { panel, button, home: panel.parentElement };
                document.body.appendChild(panel);
                panel.hidden = false;
                button.setAttribute('aria-expanded', 'true');
                positionDetails();
                panel.querySelector('[data-equipment-details-close]')?.focus({ preventScroll: true });
            });
        });
        grid.querySelectorAll('[data-equipment-details-close]').forEach((button) => button.addEventListener('click', () => closeDetails(true)));
    }

    function equipmentTypeGroupKey(type) {
        return normalizeKey(text(type)) || 'equipment';
    }

    function renderEquipmentCard(view, index) {
        const description = text(view.item && view.item.description);
        const connection = first(view.item && view.item.connection_type, view.item && view.item.it_connection_type, '');
        const connectionId = first(view.item && view.item.connection_id, '');
        const profileId = configurationProfileId(view);
        const details = [
            ['Тип', view.type],
            ['Производитель', view.vendor],
            ['Модель', view.model],
            ['Серийный номер', view.serial],
            ['IP-адрес', view.ip],
            ['Подключение', connection],
            ['ID подключения', connectionId],
            ['Комплектация', view.accessories]
        ].filter((pair) => text(pair[1]));
        const quickItems = [
            view.ip ? `<span><small>IP</small>${escapeHtml(view.ip)}</span>` : '',
            view.serial ? `<span><small>SN</small>${escapeHtml(view.serial)}</span>` : '',
            view.catalogId ? `<span><small>CAT</small>#${view.catalogId}</span>` : ''
        ].filter(Boolean).join('');
        const hasDetails = Boolean(details.length || description || view.links.length || profileId);
        const detailsId = `passportEquipmentDetails${index}`;
        return `<article class="passport-asset-card ${view.archived ? 'is-archived' : ''}" data-equipment-index="${index}">
            <${view.cover ? 'button type="button"' : 'div'} class="passport-asset-card__visual ${view.cover ? 'has-image' : ''}" ${view.cover ? `data-equipment-photo-index="${index}" aria-label="Открыть фото: ${escapeHtml(view.name)}" title="Открыть фото"` : ''}>
                <span class="passport-asset-card__visual-placeholder" aria-hidden="true"><i class="bi bi-image"></i></span>
                ${view.cover ? `<img src="${escapeHtml(view.cover)}" alt="${escapeHtml(view.name)}" loading="lazy" onerror="this.parentElement.classList.remove('has-image');this.remove();">` : ''}
                <span class="passport-asset-card__type-center">${escapeHtml(view.type)}</span>
            </${view.cover ? 'button' : 'div'}>
            <div class="passport-asset-card__body">
                <div class="passport-asset-card__head">
                    <div>
                        <span class="passport-asset-type">${escapeHtml(view.type)}</span>
                        <h3>${escapeHtml(view.name)}</h3>
                        ${view.vendor && view.model && normalizeKey(view.name) !== normalizeKey(`${view.vendor} ${view.model}`) ? `<div class="passport-asset-model">${escapeHtml(`${view.vendor} ${view.model}`)}</div>` : ''}
                    </div>
                    <span class="passport-asset-status" data-tone="${statusTone(view.status)}">${escapeHtml(view.status)}</span>
                </div>
                ${(quickItems || hasDetails) ? `<div class="passport-asset-card__quick-line">
                    <div class="passport-asset-card__quick">${quickItems}</div>
                    ${hasDetails ? `<button class="page-header-info__toggle passport-asset-details__toggle" type="button" data-equipment-details-toggle aria-haspopup="dialog" aria-expanded="false" aria-controls="${detailsId}" aria-label="Показать сведения об оборудовании" title="Показать сведения"><i class="bi bi-info-circle" aria-hidden="true"></i></button>` : ''}
                </div>` : ''}
                ${hasDetails ? `<div class="passport-asset-details passport-equipment-popover" id="${detailsId}" data-equipment-details role="dialog" aria-label="Сведения: ${escapeHtml(view.name)}" hidden>
                    <div class="passport-equipment-popover__header"><strong>${escapeHtml(view.name)}</strong><button type="button" class="btn btn-sm btn-outline-secondary" data-equipment-details-close aria-label="Закрыть сведения">&times;</button></div>
                    <div class="passport-asset-details__grid">
                        ${details.map((pair) => `<div><span>${escapeHtml(pair[0])}</span><strong>${escapeHtml(pair[1])}</strong></div>`).join('')}
                        ${profileId ? `<div><span>Профиль комплектации</span><strong data-equipment-profile-name="${profileId}">${escapeHtml(equipmentProfileLabel(profileId))}</strong></div>` : ''}
                    </div>
                    ${description ? `<pre>${escapeHtml(description)}</pre>` : ''}
                    ${view.links.length ? `<div class="passport-asset-links">${view.links.map((url, linkIndex) => `<a href="${escapeHtml(url)}" target="_blank" rel="noopener">Ссылка ${linkIndex + 1}</a>`).join('')}</div>` : ''}
                </div>` : ''}
            </div>
        </article>`;
    }

    function bindEquipmentGroupToggles(grid) {
        grid.querySelectorAll('[data-equipment-group-toggle]').forEach((button) => {
            button.addEventListener('click', () => {
                const bodyId = button.getAttribute('aria-controls');
                const body = bodyId ? document.getElementById(bodyId) : null;
                if (!body) return;
                const key = text(button.getAttribute('data-equipment-group-key'));
                const expanded = button.getAttribute('aria-expanded') === 'true';
                const nextExpanded = !expanded;
                button.setAttribute('aria-expanded', String(nextExpanded));
                body.hidden = !nextExpanded;
                if (key) {
                    if (nextExpanded) expandedEquipmentTypeKeys.add(key);
                    else expandedEquipmentTypeKeys.delete(key);
                }
            });
        });
    }

    function renderEquipment() {
        closeDetails();
        const passport = resolvePassport();
        const equipmentSearch = document.getElementById('passportEquipmentSearch');
        const grid = document.getElementById('passportEquipmentGrid');
        const items = Array.isArray(passport.equipment) ? passport.equipment : [];
        const query = normalizeKey(equipmentSearch ? equipmentSearch.value : '');
        const views = items.map(equipmentView).filter((view) => {
            if (!query) return true;
            return normalizeKey([
                view.name, view.type, view.vendor, view.model, view.ip, view.serial, view.status
            ].join(' ')).includes(query);
        });
        const revision = ++equipmentRenderRevision;
        const groupMap = new Map();
        const groups = [];
        views.forEach((view, index) => {
            const label = text(view.type) || 'Оборудование';
            const key = equipmentTypeGroupKey(label);
            let group = groupMap.get(key);
            if (!group) {
                group = { key, label, items: [] };
                groupMap.set(key, group);
                groups.push(group);
            }
            group.items.push({ view, index });
        });

        document.getElementById('passportEquipmentEmpty').classList.toggle('d-none', views.length > 0);
        const expandForSearch = Boolean(query);
        grid.innerHTML = groups.map((group, groupIndex) => {
            const bodyId = `passportEquipmentTypeGroup${groupIndex}`;
            const expanded = expandForSearch || expandedEquipmentTypeKeys.has(group.key);
            return `<section class="passport-equipment-group" data-equipment-group="${escapeHtml(group.key)}">
                <button class="passport-equipment-group__toggle" type="button" data-equipment-group-toggle data-equipment-group-key="${escapeHtml(group.key)}" aria-expanded="${expanded}" aria-controls="${bodyId}">
                    <span class="passport-equipment-group__label">${escapeHtml(group.label)}</span>
                    <span class="passport-equipment-group__count">${group.items.length}</span>
                    <i class="bi bi-chevron-down passport-equipment-group__chevron" aria-hidden="true"></i>
                </button>
                <div class="passport-equipment-group__body" id="${bodyId}" data-equipment-group-body ${expanded ? '' : 'hidden'}>
                    ${group.items.map((entry) => renderEquipmentCard(entry.view, entry.index)).join('')}
                </div>
            </section>`;
        }).join('');
        bindEquipmentGroupToggles(grid);
        bindEquipmentDetailsToggles(grid);
        grid.querySelectorAll('[data-equipment-photo-index]').forEach((button) => {
            button.addEventListener('click', () => {
                closeDetails();
                const view = views[Number(button.dataset.equipmentPhotoIndex)];
                if (!view || typeof openPhotoGallery !== 'function') return;
                openPhotoGallery(view.photos.map((photo) => ({
                    ...photo,
                    caption: first(photo.caption, photo.original_name, view.name),
                    categoryLabel: view.name,
                })), Math.max(0, view.photos.findIndex((photo) => text(photo.url) === view.cover)), button);
            });
        });
        hydrateEquipmentProfileNames(grid, views, revision);
    }


    return Object.freeze({
      renderEquipment,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailEquipmentRuntime = Object.freeze({
    mount,
  });
}());

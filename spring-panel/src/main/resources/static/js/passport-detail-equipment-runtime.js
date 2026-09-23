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
        return { item, catalogItem, catalogId, type, vendor, model, name, status, ip, serial, accessories, links, cover, archived };
    }

    function renderEquipment() {
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

        document.getElementById('passportEquipmentEmpty').classList.toggle('d-none', views.length > 0);
        grid.innerHTML = views.map((view, index) => {
            const description = text(view.item && view.item.description);
            const connection = first(view.item && view.item.connection_type, view.item && view.item.it_connection_type, '');
            const connectionId = first(view.item && view.item.connection_id, '');
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
            return `<article class="passport-asset-card ${view.archived ? 'is-archived' : ''}" data-equipment-index="${index}">
                <div class="passport-asset-card__visual ${view.cover ? 'has-image' : ''}">
                    <span class="passport-asset-card__visual-placeholder" aria-hidden="true"><i class="bi bi-image"></i></span>
                    ${view.cover ? `<img src="${escapeHtml(view.cover)}" alt="${escapeHtml(view.name)}" loading="lazy" onerror="this.parentElement.classList.remove('has-image');this.remove();">` : ''}
                    <span class="passport-asset-card__type-center">${escapeHtml(view.type)}</span>
                </div>
                <div class="passport-asset-card__body">
                    <div class="passport-asset-card__head">
                        <div>
                            <span class="passport-asset-type">${escapeHtml(view.type)}</span>
                            <h3>${escapeHtml(view.name)}</h3>
                            ${view.vendor && view.model && normalizeKey(view.name) !== normalizeKey(`${view.vendor} ${view.model}`) ? `<div class="passport-asset-model">${escapeHtml(`${view.vendor} ${view.model}`)}</div>` : ''}
                        </div>
                        <span class="passport-asset-status" data-tone="${statusTone(view.status)}">${escapeHtml(view.status)}</span>
                    </div>
                    <div class="passport-asset-card__quick">
                        ${view.ip ? `<span><small>IP</small>${escapeHtml(view.ip)}</span>` : ''}
                        ${view.serial ? `<span><small>SN</small>${escapeHtml(view.serial)}</span>` : ''}
                        ${view.catalogId ? `<span><small>CAT</small>#${view.catalogId}</span>` : ''}
                    </div>
                    ${(details.length || description || view.links.length) ? `<details class="passport-asset-details">
                        <summary class="page-header-info__toggle passport-asset-details__toggle" aria-label="Показать сведения об оборудовании" title="Показать сведения об оборудовании"><i class="bi bi-info-circle" aria-hidden="true"></i></summary>
                        <div class="passport-asset-details__grid">
                            ${details.map((pair) => `<div><span>${escapeHtml(pair[0])}</span><strong>${escapeHtml(pair[1])}</strong></div>`).join('')}
                        </div>
                        ${description ? `<pre>${escapeHtml(description)}</pre>` : ''}
                        ${view.links.length ? `<div class="passport-asset-links">${view.links.map((url, linkIndex) => `<a href="${escapeHtml(url)}" target="_blank" rel="noopener">Ссылка ${linkIndex + 1}</a>`).join('')}</div>` : ''}
                    </details>` : ''}
                </div>
            </article>`;
        }).join('');
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

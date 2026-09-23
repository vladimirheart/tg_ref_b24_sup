(function () {
  if (window.PassportDetailCoreRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const equipmentCatalog = Array.isArray(options.equipmentCatalog) ? options.equipmentCatalog : [];
    const equipmentMediaRuntime = options.equipmentMediaRuntime || window.EquipmentMediaRuntime;
    if (!equipmentMediaRuntime || typeof equipmentMediaRuntime.parse !== 'function' || typeof equipmentMediaRuntime.coverUrl !== 'function') {
      throw new Error('PassportDetailCoreRuntime requires EquipmentMediaRuntime');
    }

    const DAY_LABELS = {
        mon: 'Пн', tue: 'Вт', wed: 'Ср', thu: 'Чт', fri: 'Пт', sat: 'Сб', sun: 'Вс'
    };

    function text(value) {
        return value == null ? '' : String(value).trim();
    }

    function escapeHtml(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function normalizeKey(value) {
        return text(value).toLocaleLowerCase('ru-RU').replace(/\s+/g, ' ');
    }

    function first(...values) {
        for (const value of values) {
            const normalized = text(value);
            if (normalized) return normalized;
        }
        return '';
    }

    function statusTone(value) {
        const normalized = normalizeKey(value);
        if (!normalized) return 'neutral';
        if (normalized.includes('актив') || normalized === 'active' || normalized.includes('online') || normalized.includes('работ')) return 'ok';
        if (normalized.includes('удал') || normalized.includes('закры') || normalized.includes('retired')) return 'danger';
        if (normalized.includes('заморож') || normalized.includes('приост') || normalized.includes('стро') || normalized.includes('repair') || normalized.includes('attention')) return 'warn';
        return 'neutral';
    }

    function setStatus(element, value) {
        if (!element) return;
        element.textContent = text(value) || '—';
        element.dataset.tone = statusTone(value);
    }

    function renderProperties(targetId, items) {
        const target = document.getElementById(targetId);
        if (!target) return;
        const visible = items.filter((item) => text(item.value));
        target.innerHTML = visible.length
            ? visible.map((item) => `
                <div class="passport-property">
                    <span class="passport-property__label">${escapeHtml(item.label)}</span>
                    <span class="passport-property__value">${escapeHtml(item.value)}</span>
                </div>
            `).join('')
            : '<div class="passport-inline-empty">Нет заполненных данных.</div>';
    }

    function parseCatalogMedia(raw) {
        return equipmentMediaRuntime.parse(raw);
    }

    function equipmentCover(raw) {
        return equipmentMediaRuntime.coverUrl(raw);
    }

    function parseLinks(raw) {
        return parseCatalogMedia(raw).links;
    }

    function isEquipmentArchived(item) {
        return Boolean(item && (item.archived === true || normalizeKey(item.archived) === 'true'));
    }

    function catalogKey(type, vendor, model) {
        return [type, vendor, model].map(normalizeKey).join('::');
    }

    function findCatalogItem(item) {
        const catalogId = Number(item && item.catalog_id);
        if (Number.isFinite(catalogId) && catalogId > 0) {
            const byId = equipmentCatalog.find((entry) => Number(entry && entry.id) === catalogId);
            if (byId) return byId;
        }
        const key = catalogKey(
            item && item.equipment_type,
            item && (item.vendor || item.equipment_vendor),
            item && (item.model || item.equipment_model)
        );
        return equipmentCatalog.find((entry) => catalogKey(
            entry && entry.equipment_type,
            entry && entry.equipment_vendor,
            entry && entry.equipment_model
        ) === key) || null;
    }


    return Object.freeze({
      DAY_LABELS,
      text,
      escapeHtml,
      normalizeKey,
      first,
      statusTone,
      setStatus,
      renderProperties,
      parseCatalogMedia,
      equipmentCover,
      parseLinks,
      isEquipmentArchived,
      catalogKey,
      findCatalogItem,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailCoreRuntime = Object.freeze({
    mount,
  });
}());

(function () {
  if (window.PassportDetailEditorRuntime) {
    return;
  }

  function createRuntime(options = {}) {
    const getPassport = typeof options.getPassport === 'function' ? options.getPassport : () => ({});
    const setPassport = typeof options.setPassport === 'function' ? options.setPassport : () => {};
    const getPassportId = typeof options.getPassportId === 'function' ? options.getPassportId : () => null;
    const equipmentCatalog = Array.isArray(options.equipmentCatalog) ? options.equipmentCatalog : [];
    const statusesRaw = Array.isArray(options.statusesRaw) ? options.statusesRaw : [];
    const parameterValuesRaw = options.parameterValuesRaw && typeof options.parameterValuesRaw === 'object' ? options.parameterValuesRaw : {};
    const csrfToken = typeof options.csrfToken === 'string' ? options.csrfToken : '';
    const refreshWorkspace = typeof options.refreshWorkspace === 'function' ? options.refreshWorkspace : () => {};
    const refreshMedia = typeof options.refreshMedia === 'function' ? options.refreshMedia : () => {};
    const coreRuntime = options.coreRuntime && typeof options.coreRuntime === 'object' ? options.coreRuntime : null;
    if (!coreRuntime) {
      throw new Error('PassportDetailEditorRuntime requires coreRuntime');
    }
    const { DAY_LABELS, text, escapeHtml, normalizeKey, first, isEquipmentArchived } = coreRuntime;
    let editEquipmentDraft = [];
    let editScheduleDraft = [];

    function resolvePassport() {
      const value = getPassport();
      return value && typeof value === 'object' ? value : {};
    }

    function resolvePassportId() {
      const value = getPassportId();
      return value == null ? null : value;
    }

    const EDIT_FIELD_GROUPS = {
        main: [
            { key: 'business', label: 'Бизнес' },
            { key: 'department', label: 'Департамент', required: true },
            { key: 'partner_type', label: 'Тип партнёра' },
            { key: 'country', label: 'Страна' },
            { key: 'legal_entity', label: 'Юридическое лицо' },
            { key: 'city', label: 'Город' },
            { key: 'location_address', label: 'Адрес', span: 2 },
            { key: 'status', label: 'Статус', options: () => statusesRaw }
        ],
        contacts: [
            { key: 'it_manager_name', label: 'Управляющий' },
            { key: 'it_manager_phone', label: 'Телефон управляющего', type: 'tel' },
            { key: 'it_object_phone', label: 'Телефон объекта', type: 'tel' }
        ],
        lifecycle: [
            { key: 'start_date', label: 'Дата запуска', type: 'date' },
            { key: 'end_date', label: 'Дата закрытия', type: 'date' },
            { key: 'suspension_date', label: 'Дата приостановки', type: 'date' },
            { key: 'resume_date', label: 'Дата возобновления', type: 'date' }
        ],
        network: [
            { key: 'network', label: 'Внутренняя сеть' },
            { key: 'network_tunnel', label: 'Туннель' },
            { key: 'it_iiko_server', label: 'Сервер iiko' },
            { key: 'it_connection_type', label: 'Тип удалённого доступа' },
            { key: 'it_connection_id', label: 'ID подключения' },
            { key: 'network_provider', label: 'Провайдер' },
            { key: 'network_contract_number', label: 'Номер договора' },
            { key: 'network_restaurant_id', label: 'ID ресторана' },
            { key: 'network_legal_entity', label: 'ЮЛ провайдера' },
            { key: 'network_support_phone', label: 'Телефон ТП', type: 'tel' },
            { key: 'network_speed', label: 'Скорость' },
            { key: 'network_connection_params', label: 'Параметры подключения', textarea: true, span: 2 }
        ]
    };

    function deepClone(value) {
        try { return JSON.parse(JSON.stringify(value == null ? null : value)); }
        catch (_error) { return value; }
    }

    function csrfHeaders(json = false) {
        const headers = {};
        if (json) headers['Content-Type'] = 'application/json';
        if (csrfToken) headers['X-CSRF-TOKEN'] = csrfToken;
        return headers;
    }

    function optionValuesFor(key) {
        if (key === 'status') return statusesRaw;
        const raw = parameterValuesRaw && parameterValuesRaw[key];
        return Array.isArray(raw) ? raw.map(text).filter(Boolean) : [];
    }

    function editorControl(field) {
        const passport = resolvePassport();
        const value = text(passport[field.key]);
        const id = `passportEditField-${field.key}`;
        const options = typeof field.options === 'function' ? field.options() : optionValuesFor(field.key);
        if (Array.isArray(options) && options.length) {
            const unique = [...new Set(options.map(text).filter(Boolean))];
            return `<label class="passport-edit-field ${field.span === 2 ? 'passport-edit-field--wide' : ''}" for="${id}">
                <span>${escapeHtml(field.label)}${field.required ? ' *' : ''}</span>
                <select class="form-select form-select-sm" id="${id}" data-passport-edit-field="${escapeHtml(field.key)}" ${field.required ? 'required' : ''}>
                    <option value="">—</option>
                    ${unique.map((option) => `<option value="${escapeHtml(option)}" ${option === value ? 'selected' : ''}>${escapeHtml(option)}</option>`).join('')}
                    ${value && !unique.includes(value) ? `<option value="${escapeHtml(value)}" selected>${escapeHtml(value)}</option>` : ''}
                </select>
            </label>`;
        }
        if (field.textarea) {
            return `<label class="passport-edit-field passport-edit-field--wide" for="${id}">
                <span>${escapeHtml(field.label)}</span>
                <textarea class="form-control form-control-sm" id="${id}" rows="5" data-passport-edit-field="${escapeHtml(field.key)}">${escapeHtml(value)}</textarea>
            </label>`;
        }
        return `<label class="passport-edit-field ${field.span === 2 ? 'passport-edit-field--wide' : ''}" for="${id}">
            <span>${escapeHtml(field.label)}${field.required ? ' *' : ''}</span>
            <input class="form-control form-control-sm" id="${id}" type="${field.type || 'text'}" value="${escapeHtml(value)}" data-passport-edit-field="${escapeHtml(field.key)}" ${field.required ? 'required' : ''}>
        </label>`;
    }

    function renderEditorScalarFields() {
        const targets = {
            main: 'passportEditMainFields',
            contacts: 'passportEditContactFields',
            lifecycle: 'passportEditLifecycleFields',
            network: 'passportEditNetworkFields'
        };
        Object.entries(targets).forEach(([group, targetId]) => {
            const target = document.getElementById(targetId);
            if (!target) return;
            target.innerHTML = EDIT_FIELD_GROUPS[group].map(editorControl).join('');
        });
    }

    function normalizedScheduleDraft() {
        const passport = resolvePassport();
        const source = Array.isArray(passport.schedule) ? deepClone(passport.schedule) : [];
        const byDay = new Map(source.map((item) => [text(item && item.day).toLowerCase(), item]));
        return Object.keys(DAY_LABELS).map((day) => ({
            day,
            from: text(byDay.get(day) && byDay.get(day).from),
            to: text(byDay.get(day) && byDay.get(day).to),
            is_24: Boolean(byDay.get(day) && byDay.get(day).is_24)
        }));
    }

    function renderEditorSchedule() {
        const target = document.getElementById('passportEditSchedule');
        if (!target) return;
        target.innerHTML = editScheduleDraft.map((item, index) => `<div class="passport-edit-schedule-row" data-edit-schedule-index="${index}">
            <strong>${escapeHtml(DAY_LABELS[item.day] || item.day)}</strong>
            <input class="form-control form-control-sm" type="time" data-schedule-field="from" value="${escapeHtml(item.from)}" ${item.is_24 ? 'disabled' : ''}>
            <span>—</span>
            <input class="form-control form-control-sm" type="time" data-schedule-field="to" value="${escapeHtml(item.to)}" ${item.is_24 ? 'disabled' : ''}>
            <label class="form-check"><input class="form-check-input" type="checkbox" data-schedule-field="is_24" ${item.is_24 ? 'checked' : ''}><span class="form-check-label">24 часа</span></label>
        </div>`).join('');
    }

    function catalogOptions(selectedId) {
        const options = ['<option value="">Без связи с каталогом</option>'];
        equipmentCatalog.forEach((item) => {
            const id = Number(item && item.id);
            if (!Number.isFinite(id)) return;
            const label = [item.equipment_vendor, item.equipment_model].map(text).filter(Boolean).join(' ') || text(item.equipment_type) || `#${id}`;
            options.push(`<option value="${id}" ${Number(selectedId) === id ? 'selected' : ''}>${escapeHtml(label)} · #${id}</option>`);
        });
        return options.join('');
    }

    function equipmentInput(index, key, label, value, options = {}) {
        const id = `passportEditEquipment-${index}-${key}`;
        const disabled = options.disabled ? ' disabled' : '';
        if (options.textarea) {
            return `<label class="passport-edit-equipment-field passport-edit-equipment-field--wide" for="${id}"><span>${escapeHtml(label)}</span><textarea class="form-control form-control-sm" id="${id}" rows="3" data-equipment-field="${key}"${disabled}>${escapeHtml(text(value))}</textarea></label>`;
        }
        return `<label class="passport-edit-equipment-field" for="${id}"><span>${escapeHtml(label)}</span><input class="form-control form-control-sm" id="${id}" type="${options.type || 'text'}" value="${escapeHtml(text(value))}" data-equipment-field="${key}"${disabled}></label>`;
    }

    function renderEditorEquipment() {
        const target = document.getElementById('passportEditEquipment');
        if (!target) return;
        if (!editEquipmentDraft.length) {
            target.innerHTML = '<div class="passport-inline-empty">Оборудование не добавлено.</div>';
            return;
        }
        target.innerHTML = editEquipmentDraft.map((item, index) => {
            const archived = isEquipmentArchived(item);
            const archivedAt = text(item && item.archived_at);
            return `<article class="passport-edit-equipment-card ${archived ? 'is-archived' : ''}" data-edit-equipment-index="${index}">
            <div class="passport-edit-equipment-card__head">
                <label><span>Модель из каталога</span><select class="form-select form-select-sm" data-equipment-catalog${archived ? ' disabled' : ''}>${catalogOptions(item && item.catalog_id)}</select></label>
                ${archived
                    ? '<button type="button" class="btn btn-sm btn-outline-primary" data-equipment-restore>Восстановить</button>'
                    : '<button type="button" class="btn btn-sm btn-outline-danger" data-equipment-remove>В архив</button>'}
            </div>
            ${archived ? `<div class="passport-edit-equipment-card__history">Историческая запись${archivedAt ? ` · ${escapeHtml(archivedAt)}` : ''}</div>` : ''}
            <div class="passport-edit-equipment-fields">
                ${equipmentInput(index, 'equipment_type', 'Тип', item && item.equipment_type, { disabled: archived })}
                ${equipmentInput(index, 'vendor', 'Производитель', first(item && item.vendor, item && item.equipment_vendor), { disabled: archived })}
                ${equipmentInput(index, 'model', 'Модель', first(item && item.model, item && item.equipment_model), { disabled: archived })}
                ${equipmentInput(index, 'name', 'Имя устройства', item && item.name, { disabled: archived })}
                ${equipmentInput(index, 'serial_number', 'Серийный номер', item && item.serial_number, { disabled: archived })}
                ${equipmentInput(index, 'status', 'Статус', item && item.status, { disabled: archived })}
                ${equipmentInput(index, 'ip_address', 'IP-адрес', item && item.ip_address, { disabled: archived })}
                ${equipmentInput(index, 'connection_type', 'Тип подключения', item && item.connection_type, { disabled: archived })}
                ${equipmentInput(index, 'connection_id', 'ID подключения', item && item.connection_id, { disabled: archived })}
                ${equipmentInput(index, 'connection_password', 'Пароль подключения', item && item.connection_password, { type: 'password', disabled: archived })}
                ${equipmentInput(index, 'description', 'Описание', item && item.description, { textarea: true, disabled: archived })}
            </div>
        </article>`;
        }).join('');
    }

    function renderEditorPhotos() {
        const passport = resolvePassport();
        const target = document.getElementById('passportEditPhotoList');
        if (!target) return;
        const photos = Array.isArray(passport.photos) ? passport.photos : [];
        target.innerHTML = photos.length ? photos.map((photo, index) => {
            const url = text(photo && photo.url);
            const id = text(photo && photo.id);
            return `<article class="passport-edit-photo-row" data-edit-photo-index="${index}" data-photo-id="${escapeHtml(id)}">
                <div class="passport-edit-photo-row__thumb">${url ? `<img src="${escapeHtml(url)}" alt="Фото">` : '<span>Нет файла</span>'}</div>
                <div class="passport-edit-photo-row__fields">
                    <select class="form-select form-select-sm" data-edit-photo-category>
                        <option value="archive" ${normalizeKey(photo && photo.category) !== 'title' ? 'selected' : ''}>Архивное</option>
                        <option value="title" ${normalizeKey(photo && photo.category) === 'title' ? 'selected' : ''}>Титульное</option>
                    </select>
                    <input class="form-control form-control-sm" data-edit-photo-caption value="${escapeHtml(first(photo && photo.caption, photo && photo.original_name, ''))}" placeholder="Подпись">
                </div>
                <div class="passport-edit-photo-row__actions">
                    ${id ? '<button type="button" class="btn btn-sm btn-outline-primary" data-edit-photo-save>Сохранить</button><button type="button" class="btn btn-sm btn-outline-danger" data-edit-photo-delete>Удалить</button>' : '<span class="text-muted small">Внешнее фото</span>'}
                </div>
            </article>`;
        }).join('') : '<div class="passport-inline-empty">Фотографий пока нет.</div>';
    }

    function setEditorTab(name) {
        document.querySelectorAll('[data-passport-edit-tab]').forEach((button) => button.classList.toggle('is-active', button.dataset.passportEditTab === name));
        document.querySelectorAll('[data-passport-edit-panel]').forEach((panel) => panel.classList.toggle('is-active', panel.dataset.passportEditPanel === name));
    }

    function openEditor(tab = 'main') {
        const passport = resolvePassport();
        const layer = document.getElementById('passportEditLayer');
        if (!layer) return;
        editEquipmentDraft = deepClone(Array.isArray(passport.equipment) ? passport.equipment : []) || [];
        editScheduleDraft = normalizedScheduleDraft();
        renderEditorScalarFields();
        renderEditorSchedule();
        renderEditorEquipment();
        renderEditorPhotos();
        setEditorTab(tab);
        document.getElementById('passportEditStatus').textContent = '';
        layer.hidden = false;
        layer.setAttribute('aria-hidden', 'false');
        document.body.classList.add('passport-edit-open');
        window.requestAnimationFrame(() => layer.querySelector('[data-passport-edit-panel].is-active input, [data-passport-edit-panel].is-active select, [data-passport-edit-panel].is-active textarea')?.focus({ preventScroll: true }));
    }

    function closeEditor() {
        const layer = document.getElementById('passportEditLayer');
        if (!layer) return;
        layer.hidden = true;
        layer.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('passport-edit-open');
    }

    function collectEditorPayload() {
        const passport = resolvePassport();
        const payload = {};
        document.querySelectorAll('[data-passport-edit-field]').forEach((control) => {
            const key = control.dataset.passportEditField;
            const value = control.value == null ? '' : String(control.value).trim();
            if (value !== text(passport[key])) payload[key] = value;
        });
        if (JSON.stringify(editScheduleDraft) !== JSON.stringify(normalizedScheduleDraft())) payload.schedule = deepClone(editScheduleDraft);
        const currentEquipment = Array.isArray(passport.equipment) ? passport.equipment : [];
        if (JSON.stringify(editEquipmentDraft) !== JSON.stringify(currentEquipment)) payload.equipment = deepClone(editEquipmentDraft);
        return payload;
    }

    async function saveEditor() {
        const passportId = resolvePassportId();
        const status = document.getElementById('passportEditStatus');
        const button = document.getElementById('passportEditSave');
        const departmentControl = document.querySelector('[data-passport-edit-field="department"]');
        if (departmentControl && !String(departmentControl.value || '').trim()) {
            status.textContent = 'Департамент обязателен.';
            status.dataset.tone = 'danger';
            departmentControl.focus();
            return;
        }
        const payload = collectEditorPayload();
        if (!Object.keys(payload).length) {
            closeEditor();
            return;
        }
        button.disabled = true;
        status.textContent = 'Сохраняем…';
        status.dataset.tone = 'neutral';
        try {
            const response = await fetch(`/api/object_passports/${passportId}`, {
                method: 'PUT',
                credentials: 'same-origin',
                headers: csrfHeaders(true),
                body: JSON.stringify(payload)
            });
            const data = await response.json();
            if (!response.ok || data.success === false || !data.passport) throw new Error((data && data.error) || 'Не удалось сохранить паспорт');
            setPassport(data.passport);
            refreshWorkspace();
            closeEditor();
        } catch (error) {
            status.textContent = error && error.message ? error.message : String(error);
            status.dataset.tone = 'danger';
        } finally {
            button.disabled = false;
        }
    }

    async function refreshPhotosFromResponse(data) {
        if (data && Array.isArray(data.photos)) {
            const passport = resolvePassport();
            setPassport({ ...passport, photos: data.photos });
        }
        refreshMedia();
        renderEditorPhotos();
    }

    async function savePhotoRow(row) {
        const id = text(row && row.dataset.photoId);
        if (!id) return;
        const response = await fetch(`/api/object_passports/photos/${encodeURIComponent(id)}`, {
            method: 'PATCH', credentials: 'same-origin', headers: csrfHeaders(true),
            body: JSON.stringify({ category: row.querySelector('[data-edit-photo-category]')?.value || 'archive', caption: row.querySelector('[data-edit-photo-caption]')?.value || '' })
        });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Не удалось сохранить фото');
        await refreshPhotosFromResponse(data);
    }

    async function deletePhotoRow(row) {
        const id = text(row && row.dataset.photoId);
        if (!id || !window.confirm('Удалить это фото?')) return;
        const response = await fetch(`/api/object_passports/photos/${encodeURIComponent(id)}`, { method: 'DELETE', credentials: 'same-origin', headers: csrfHeaders(false) });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Не удалось удалить фото');
        await refreshPhotosFromResponse(data);
    }

    async function uploadPhoto(event) {
        const passportId = resolvePassportId();
        event.preventDefault();
        const file = document.getElementById('passportPhotoUploadFile')?.files?.[0];
        if (!file) return;
        const formData = new FormData();
        formData.append('file', file);
        formData.append('category', document.getElementById('passportPhotoUploadCategory')?.value || 'archive');
        formData.append('caption', document.getElementById('passportPhotoUploadCaption')?.value || '');
        const response = await fetch(`/api/object_passports/${passportId}/photos`, { method: 'POST', credentials: 'same-origin', headers: csrfHeaders(false), body: formData });
        const data = await response.json();
        if (!response.ok || data.success === false) throw new Error((data && data.error) || 'Не удалось загрузить фото');
        event.currentTarget.reset();
        await refreshPhotosFromResponse(data);
    }


    function bindEvents() {
        document.getElementById('passportEditLink')?.addEventListener('click', () => openEditor('main'));
        document.getElementById('passportEquipmentEditLink')?.addEventListener('click', () => openEditor('equipment'));
        document.getElementById('passportPhotosEditLink')?.addEventListener('click', () => openEditor('photos'));
        document.getElementById('passportEditSave')?.addEventListener('click', saveEditor);
        document.querySelectorAll('[data-passport-edit-close]').forEach((button) => button.addEventListener('click', closeEditor));
        document.getElementById('passportEditTabs')?.addEventListener('click', (event) => {
            const button = event.target.closest('[data-passport-edit-tab]');
            if (button) setEditorTab(button.dataset.passportEditTab);
        });
        document.getElementById('passportEditSchedule')?.addEventListener('input', (event) => {
            const row = event.target.closest('[data-edit-schedule-index]');
            const field = event.target.dataset.scheduleField;
            const index = Number(row && row.dataset.editScheduleIndex);
            if (!Number.isFinite(index) || !field || !editScheduleDraft[index]) return;
            editScheduleDraft[index][field] = field === 'is_24' ? Boolean(event.target.checked) : event.target.value;
            if (field === 'is_24') renderEditorSchedule();
        });
        document.getElementById('passportEditEquipmentAdd')?.addEventListener('click', () => {
            editEquipmentDraft.push({ equipment_type: '', vendor: '', name: '', model: '', serial_number: '', status: '', ip_address: '', connection_type: '', connection_id: '', connection_password: '', description: '' });
            renderEditorEquipment();
        });
        document.getElementById('passportEditEquipment')?.addEventListener('input', (event) => {
            const card = event.target.closest('[data-edit-equipment-index]');
            const index = Number(card && card.dataset.editEquipmentIndex);
            const field = event.target.dataset.equipmentField;
            if (!Number.isFinite(index) || !field || !editEquipmentDraft[index]) return;
            editEquipmentDraft[index][field] = event.target.value;
        });
        document.getElementById('passportEditEquipment')?.addEventListener('change', (event) => {
            const card = event.target.closest('[data-edit-equipment-index]');
            const index = Number(card && card.dataset.editEquipmentIndex);
            if (!Number.isFinite(index) || !editEquipmentDraft[index]) return;
            if (event.target.matches('[data-equipment-catalog]')) {
                const id = Number(event.target.value);
                if (Number.isFinite(id) && id > 0) {
                    const catalogItem = equipmentCatalog.find((entry) => Number(entry && entry.id) === id);
                    if (catalogItem) {
                        editEquipmentDraft[index].catalog_id = id;
                        editEquipmentDraft[index].equipment_type = text(catalogItem.equipment_type);
                        editEquipmentDraft[index].vendor = text(catalogItem.equipment_vendor);
                        editEquipmentDraft[index].model = text(catalogItem.equipment_model);
                    }
                } else {
                    delete editEquipmentDraft[index].catalog_id;
                }
                renderEditorEquipment();
            }
        });
        document.getElementById('passportEditEquipment')?.addEventListener('click', (event) => {
            const restore = event.target.closest('[data-equipment-restore]');
            if (restore) {
                const card = restore.closest('[data-edit-equipment-index]');
                const index = Number(card && card.dataset.editEquipmentIndex);
                if (!Number.isFinite(index)) return;
                const restored = { ...(editEquipmentDraft[index] || {}) };
                delete restored.archived;
                delete restored.archived_at;
                delete restored.archive_reason;
                editEquipmentDraft[index] = restored;
                renderEditorEquipment();
                return;
            }
            const remove = event.target.closest('[data-equipment-remove]');
            if (!remove) return;
            const card = remove.closest('[data-edit-equipment-index]');
            const index = Number(card && card.dataset.editEquipmentIndex);
            if (!Number.isFinite(index)) return;
            if (!window.confirm('Убрать оборудование из активного состава? Запись останется в паспорте как история и перестанет блокировать удаление модели из каталога.')) return;
            editEquipmentDraft[index] = {
                ...(editEquipmentDraft[index] || {}),
                archived: true,
                archived_at: new Date().toISOString(),
                archive_reason: 'Снято с объекта вручную'
            };
            renderEditorEquipment();
        });
        document.getElementById('passportPhotoUploadForm')?.addEventListener('submit', (event) => uploadPhoto(event).catch((error) => { const status = document.getElementById('passportEditStatus'); status.textContent = error.message || String(error); status.dataset.tone = 'danger'; }));
        document.getElementById('passportEditPhotoList')?.addEventListener('click', (event) => {
            const row = event.target.closest('[data-photo-id]');
            if (!row) return;
            if (event.target.closest('[data-edit-photo-save]')) savePhotoRow(row).catch((error) => { const status = document.getElementById('passportEditStatus'); status.textContent = error.message || String(error); status.dataset.tone = 'danger'; });
            if (event.target.closest('[data-edit-photo-delete]')) deletePhotoRow(row).catch((error) => { const status = document.getElementById('passportEditStatus'); status.textContent = error.message || String(error); status.dataset.tone = 'danger'; });
        });
    }

    return Object.freeze({
      openEditor,
      closeEditor,
      bindEvents,
    });
  }

  function mount(options = {}) {
    return createRuntime(options);
  }

  window.PassportDetailEditorRuntime = Object.freeze({
    mount,
  });
}());

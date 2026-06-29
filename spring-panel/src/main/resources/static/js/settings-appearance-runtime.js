(function () {
  if (window.SettingsAppearanceRuntime) {
    return;
  }

  const STATUS_FILTER_BASE_URL = '/clients';
  const STATUS_COLOR_FALLBACK = '#0d6efd';
  const BUSINESS_ICON_ALLOWED_PREFIXES = ['http://', 'https://', '/', 'data:image/'];
  const BUSINESS_ICON_MAX_FILE_SIZE = 16 * 1024;

  function notify(message, type = 'info') {
    if (typeof window.showPopup === 'function') {
      window.showPopup(message, type);
      return;
    }
    console.log(message);
  }

  function escapeHtml(value) {
    if (typeof window.escapeHtml === 'function') {
      return window.escapeHtml(value);
    }
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function isValidHexColor(value) {
    return typeof value === 'string' && /^#(?:[0-9a-f]{6})$/i.test(value.trim());
  }

  function sanitizeHexColor(value, fallback = '') {
    const normalized = typeof value === 'string' ? value.trim() : String(value || '').trim();
    if (!normalized) {
      return fallback;
    }
    return isValidHexColor(normalized) ? normalized.toLowerCase() : fallback;
  }

  function sanitizeColorValue(value) {
    return sanitizeHexColor(value, STATUS_COLOR_FALLBACK);
  }

  function sanitizeStatusColorMap(source) {
    const map = {};
    if (!source || typeof source !== 'object') {
      return map;
    }
    Object.entries(source).forEach(([key, value]) => {
      if (typeof key !== 'string') {
        return;
      }
      const label = key.trim();
      if (!label) {
        return;
      }
      map[label] = sanitizeColorValue(value);
    });
    return map;
  }

  function createRuntime(options = {}) {
    const state = {
      clientStatusUsage: options.statusUsage && typeof options.statusUsage === 'object'
        ? { ...options.statusUsage }
        : {},
      initialClientStatuses: Array.isArray(options.initialClientStatuses)
        ? options.initialClientStatuses.slice()
        : [],
      initialClientStatusColors: options.initialClientStatusColors && typeof options.initialClientStatusColors === 'object'
        ? { ...options.initialClientStatusColors }
        : {},
      initialBusinessCellStyles: options.initialBusinessCellStyles && typeof options.initialBusinessCellStyles === 'object'
        ? { ...options.initialBusinessCellStyles }
        : {},
      clientStatusColors: {},
      statusEditMode: false,
      initialStatusesSnapshot: [],
      businessStylesState: [],
    };

    const businessStylesContainer = document.getElementById('businessStylesContainer');
    const businessStylesEmptyState = document.getElementById('businessStylesEmpty');
    const addBusinessStyleBtn = document.getElementById('addBusinessStyleBtn');
    const saveBusinessStylesBtn = document.getElementById('saveBusinessStylesBtn');

    function resolveStatusColor(status) {
      const label = typeof status === 'string' ? status.trim() : '';
      if (label && state.clientStatusColors[label]) {
        return sanitizeColorValue(state.clientStatusColors[label]);
      }
      return STATUS_COLOR_FALLBACK;
    }

    function normalizeStatusItem(entry) {
      if (entry && typeof entry === 'object') {
        const rawValue =
          typeof entry.value === 'string'
            ? entry.value
            : typeof entry.label === 'string'
              ? entry.label
              : typeof entry.status === 'string'
                ? entry.status
                : '';
        const value = rawValue.trim();
        const colorSource = entry.color || entry.colour || entry.hex || (value ? state.clientStatusColors[value] : '');
        return { value, color: sanitizeColorValue(colorSource || resolveStatusColor(value)) };
      }
      const value = typeof entry === 'string' ? entry.trim() : '';
      return { value, color: resolveStatusColor(value) };
    }

    function buildStatusFilterLink(status) {
      const value = typeof status === 'string' ? status.trim() : '';
      if (!value) {
        return '#';
      }
      const params = new URLSearchParams({ client_status: value });
      return `${STATUS_FILTER_BASE_URL}?${params.toString()}`;
    }

    function handleStatusInputChange(row) {
      if (!(row instanceof HTMLElement)) {
        return;
      }
      const input = row.querySelector('.status-input');
      const link = row.querySelector('.status-count-link');
      const colorPreview = row.querySelector('.status-color-preview');
      if (!(input instanceof HTMLInputElement) || !(link instanceof HTMLAnchorElement)) {
        return;
      }

      const value = input.value.trim();
      const original = row.dataset.originalValue || '';
      const baseCount = Number(row.dataset.count || 0);
      let displayCount = baseCount;

      if (colorPreview instanceof HTMLElement) {
        colorPreview.title = value ? `Цвет статуса «${value}»` : 'Цвет статуса';
      }

      if (!value) {
        link.textContent = '0';
        link.href = '#';
        link.classList.add('disabled');
        return;
      }

      if (value !== original) {
        displayCount = 0;
      }

      link.textContent = String(displayCount);
      link.href = buildStatusFilterLink(value);
      link.classList.toggle('disabled', state.statusEditMode);
    }

    function removeStatus(trigger) {
      const row = trigger?.closest('.status-row');
      if (row) {
        row.remove();
        updateStatusesEmptyState();
      }
    }

    function buildStatusRow(status) {
      const { value, color } = normalizeStatusItem(status);
      const container = document.createElement('div');
      container.className = 'list-group-item d-flex align-items-center gap-3 status-row';
      const count = Number(state.clientStatusUsage[value] || 0);
      container.dataset.originalValue = value;
      container.dataset.count = String(count);
      container.dataset.color = color;

      const main = document.createElement('div');
      main.className = 'status-row-main flex-grow-1';

      const colorPreview = document.createElement('span');
      colorPreview.className = 'status-color-preview';
      colorPreview.style.setProperty('--status-color', color);
      colorPreview.title = value ? `Цвет статуса «${value}»` : 'Цвет статуса';
      main.appendChild(colorPreview);

      const colorInput = document.createElement('input');
      colorInput.type = 'color';
      colorInput.className = 'status-color-input form-control form-control-color';
      colorInput.value = color;
      colorInput.disabled = !state.statusEditMode;
      if (!state.statusEditMode) {
        colorInput.classList.add('d-none');
      }
      colorInput.addEventListener('input', () => {
        const sanitized = sanitizeColorValue(colorInput.value);
        colorInput.value = sanitized;
        container.dataset.color = sanitized;
        colorPreview.style.setProperty('--status-color', sanitized);
      });
      main.appendChild(colorInput);

      const input = document.createElement('input');
      input.type = 'text';
      input.className = 'form-control status-input';
      input.value = value;
      input.disabled = !state.statusEditMode;
      input.placeholder = 'Новый статус';
      input.addEventListener('input', () => handleStatusInputChange(container));
      main.appendChild(input);

      container.appendChild(main);

      const badge = document.createElement('a');
      badge.className = 'status-count-link';
      badge.dataset.statusCount = 'true';
      badge.title = value ? `Клиентов со статусом «${value}»` : 'Клиентов с этим статусом';
      badge.href = buildStatusFilterLink(value);
      badge.textContent = String(count);
      if (!value || state.statusEditMode) {
        badge.classList.add('disabled');
      }
      container.appendChild(badge);

      const removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'btn btn-outline-danger btn-sm';
      removeBtn.textContent = 'Удалить';
      if (!state.statusEditMode) {
        removeBtn.classList.add('d-none');
      }
      removeBtn.dataset.statusRemove = 'true';
      removeBtn.addEventListener('click', () => removeStatus(removeBtn));
      container.appendChild(removeBtn);

      handleStatusInputChange(container);
      return container;
    }

    function renderStatusesList(statuses) {
      const list = document.getElementById('statusesList');
      const placeholder = document.getElementById('statusesEmptyPlaceholder');
      if (!list) {
        return;
      }
      list.innerHTML = '';
      const items = Array.isArray(statuses) ? statuses : [];
      if (!items.length) {
        if (placeholder) {
          placeholder.classList.remove('d-none');
        }
        return;
      }
      if (placeholder) {
        placeholder.classList.add('d-none');
      }
      items.forEach((status) => {
        list.appendChild(buildStatusRow(status));
      });
      updateStatusesEmptyState();
    }

    function updateStatusesEmptyState() {
      const list = document.getElementById('statusesList');
      const placeholder = document.getElementById('statusesEmptyPlaceholder');
      if (!list || !placeholder) {
        return;
      }
      const hasRows = Boolean(list.querySelector('.status-row'));
      placeholder.classList.toggle('d-none', hasRows);
    }

    function collectStatusItems() {
      return Array.from(document.querySelectorAll('#statusesList .status-row'))
        .map((row) => {
          const input = row.querySelector('.status-input');
          const colorInput = row.querySelector('.status-color-input');
          const preview = row.querySelector('.status-color-preview');
          const value = input instanceof HTMLInputElement ? input.value.trim() : '';
          const rawColor = colorInput instanceof HTMLInputElement ? colorInput.value : row.dataset.color;
          const color = sanitizeColorValue(rawColor);
          row.dataset.color = color;
          if (preview instanceof HTMLElement) {
            preview.style.setProperty('--status-color', color);
          }
          return { value, color };
        })
        .filter((item) => Boolean(item.value));
    }

    function setStatusesEditMode(enable, { restoreSnapshot = false } = {}) {
      state.statusEditMode = Boolean(enable);
      const editBtn = document.getElementById('editStatusesBtn');
      const saveBtn = document.getElementById('saveStatusesBtn');
      const cancelBtn = document.getElementById('cancelStatusesBtn');
      const addBtn = document.getElementById('addStatusBtn');

      if (editBtn) {
        editBtn.classList.toggle('d-none', state.statusEditMode);
      }
      if (saveBtn) {
        saveBtn.classList.toggle('d-none', !state.statusEditMode);
      }
      if (cancelBtn) {
        cancelBtn.classList.toggle('d-none', !state.statusEditMode);
      }
      if (addBtn) {
        addBtn.classList.toggle('d-none', !state.statusEditMode);
      }

      if (!state.statusEditMode && restoreSnapshot) {
        renderStatusesList(state.initialStatusesSnapshot);
      }

      document.querySelectorAll('#statusesList .status-row').forEach((row) => {
        const input = row.querySelector('.status-input');
        const removeBtn = row.querySelector('[data-status-remove]');
        const badge = row.querySelector('.status-count-link');
        const colorInput = row.querySelector('.status-color-input');
        if (input instanceof HTMLInputElement) {
          input.disabled = !state.statusEditMode;
        }
        if (colorInput instanceof HTMLInputElement) {
          colorInput.disabled = !state.statusEditMode;
          colorInput.classList.toggle('d-none', !state.statusEditMode);
        }
        if (removeBtn instanceof HTMLElement) {
          removeBtn.classList.toggle('d-none', !state.statusEditMode);
        }
        if (badge instanceof HTMLElement) {
          badge.classList.toggle('disabled', state.statusEditMode || !(input?.value.trim()));
        }
        handleStatusInputChange(row);
      });
    }

    function startStatusesEdit() {
      state.initialStatusesSnapshot = collectStatusItems();
      setStatusesEditMode(true);
    }

    function cancelStatusesEdit() {
      setStatusesEditMode(false, { restoreSnapshot: true });
    }

    function addStatus() {
      if (!state.statusEditMode) {
        return;
      }
      const list = document.getElementById('statusesList');
      if (!list) {
        return;
      }
      const row = buildStatusRow('');
      row.dataset.originalValue = '';
      row.dataset.count = '0';
      list.appendChild(row);
      updateStatusesEmptyState();
      const input = row.querySelector('.status-input');
      if (input instanceof HTMLInputElement) {
        setTimeout(() => input.focus(), 0);
      }
    }

    function syncStatusUsage(statuses) {
      const usage = {};
      const currentUsage = state.clientStatusUsage || {};
      statuses.forEach((status) => {
        usage[status] = Object.prototype.hasOwnProperty.call(currentUsage, status)
          ? currentUsage[status]
          : 0;
      });
      state.clientStatusUsage = usage;
    }

    async function saveClientStatuses() {
      const saveBtn = document.getElementById('saveStatusesBtn');
      const items = collectStatusItems();
      const statuses = items.map((item) => item.value);
      const colors = items.reduce((acc, item) => {
        acc[item.value] = item.color;
        return acc;
      }, {});

      if (!statuses.length) {
        if (!confirm('Список статусов пуст. Сохранить и удалить все статусы?')) {
          return;
        }
      }

      if (saveBtn) {
        saveBtn.disabled = true;
      }
      try {
        const response = await fetch('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ client_statuses: statuses, client_status_colors: colors }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error(data.error || 'Не удалось сохранить статусы');
        }
        syncStatusUsage(statuses);
        state.clientStatusColors = sanitizeStatusColorMap(colors);
        renderStatusesList(items);
        setStatusesEditMode(false);
        notify('✅ Статусы клиентов сохранены');
      } catch (error) {
        const message = error && error.message ? error.message : String(error);
        notify('❌ Ошибка: ' + message);
      } finally {
        if (saveBtn) {
          saveBtn.disabled = false;
        }
      }
    }

    function initClientStatuses() {
      state.clientStatusColors = sanitizeStatusColorMap(state.initialClientStatusColors);
      renderStatusesList(state.initialClientStatuses);
      updateStatusesEmptyState();
      setStatusesEditMode(false);
    }

    function generateBusinessStyleId() {
      if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return crypto.randomUUID();
      }
      return `business-style-${Math.random().toString(36).slice(2, 11)}`;
    }

    function createBusinessStyleState(initial = {}) {
      return {
        id: generateBusinessStyleId(),
        business: String(initial.business || '').trim(),
        background_color: String(initial.background_color || '').trim(),
        text_color: String(initial.text_color || '').trim(),
        icon: String(initial.icon || '').trim(),
      };
    }

    function updateBusinessStylesEmptyState() {
      if (!businessStylesEmptyState) {
        return;
      }
      businessStylesEmptyState.classList.toggle('d-none', Boolean(state.businessStylesState.length));
    }

    function updateBusinessStylePreview(card, item) {
      if (!(card instanceof HTMLElement)) {
        return;
      }
      const preview = card.querySelector('[data-business-preview]');
      if (!(preview instanceof HTMLElement)) {
        return;
      }
      const background = sanitizeHexColor(item.background_color, '');
      preview.style.backgroundColor = background || '#f8f9fa';
      const textEl = preview.querySelector('.business-style-preview-text');
      if (textEl instanceof HTMLElement) {
        textEl.textContent = item.business || 'Пример';
        const color = sanitizeHexColor(item.text_color, '');
        textEl.style.color = color || '#212529';
      }
      const iconSpot = preview.querySelector('.business-style-preview-icon');
      if (iconSpot instanceof HTMLElement) {
        const iconValue = String(item.icon || '').trim();
        if (iconValue) {
          iconSpot.classList.remove('d-none');
          iconSpot.innerHTML = `<img src="${escapeHtml(iconValue)}" alt="" width="4" height="4">`;
          preview.classList.add('business-style-preview--with-icon');
        } else {
          iconSpot.innerHTML = '';
          iconSpot.classList.add('d-none');
          preview.classList.remove('business-style-preview--with-icon');
        }
      }
    }

    function refreshBusinessStyleCard(id) {
      if (!businessStylesContainer) {
        return;
      }
      const card = businessStylesContainer.querySelector(`[data-style-id="${id}"]`);
      const item = state.businessStylesState.find((entry) => entry.id === id);
      if (card && item) {
        updateBusinessStylePreview(card, item);
      }
    }

    function updateBusinessStyleField(id, field, value) {
      const item = state.businessStylesState.find((entry) => entry.id === id);
      if (!item) {
        return;
      }
      item[field] = typeof value === 'string' ? value.trim() : value;
      refreshBusinessStyleCard(id);
    }

    function removeBusinessStyle(id) {
      state.businessStylesState = state.businessStylesState.filter((entry) => entry.id !== id);
      renderBusinessStyles();
    }

    function attachColorControls(card, item, field) {
      const textInput = card.querySelector(`[data-field="${field}"]`);
      const picker = card.querySelector(`[data-color-picker="${field}"]`);
      const clearBtn = card.querySelector(`[data-clear-field="${field}"]`);
      const currentValue = item[field] || '';

      if (textInput instanceof HTMLInputElement) {
        textInput.value = currentValue;
        textInput.addEventListener('input', (event) => {
          const value = String(event.target.value || '').trim();
          updateBusinessStyleField(item.id, field, value);
          if (picker instanceof HTMLInputElement) {
            const sanitized = sanitizeHexColor(value, '');
            if (sanitized) {
              picker.value = sanitized;
            }
          }
        });
      }

      if (picker instanceof HTMLInputElement) {
        picker.value = sanitizeHexColor(currentValue, picker.value || '#ffffff') || '#ffffff';
        picker.addEventListener('input', (event) => {
          const value = event.target.value || '';
          if (textInput instanceof HTMLInputElement) {
            textInput.value = value;
          }
          updateBusinessStyleField(item.id, field, value);
        });
      }

      if (clearBtn instanceof HTMLElement) {
        clearBtn.addEventListener('click', () => {
          if (textInput instanceof HTMLInputElement) {
            textInput.value = '';
          }
          if (picker instanceof HTMLInputElement) {
            picker.value = '#ffffff';
          }
          updateBusinessStyleField(item.id, field, '');
        });
      }
    }

    function handleBusinessIconFileSelect(fileInput, iconInput, styleId) {
      if (!(fileInput instanceof HTMLInputElement)) {
        return;
      }
      const file = fileInput.files && fileInput.files[0];
      if (!file) {
        return;
      }
      if (!file.type || !file.type.startsWith('image/')) {
        notify('❌ Можно загрузить только изображения (PNG, JPG, SVG и т.п.).');
        fileInput.value = '';
        return;
      }
      if (file.size > BUSINESS_ICON_MAX_FILE_SIZE) {
        notify(`❌ Размер файла не должен превышать ${Math.round(BUSINESS_ICON_MAX_FILE_SIZE / 1024)} КБ.`);
        fileInput.value = '';
        return;
      }
      const reader = new FileReader();
      reader.onload = () => {
        const result = typeof reader.result === 'string' ? reader.result : '';
        if (iconInput instanceof HTMLInputElement) {
          iconInput.value = result;
        }
        updateBusinessStyleField(styleId, 'icon', result);
        fileInput.value = '';
      };
      reader.onerror = () => {
        notify('❌ Не удалось прочитать файл. Попробуйте ещё раз.');
        fileInput.value = '';
      };
      reader.readAsDataURL(file);
    }

    function attachIconControls(card, item) {
      const iconInput = card.querySelector('[data-field="icon"]');
      const uploadBtn = card.querySelector('[data-icon-upload]');
      const fileInput = card.querySelector('[data-icon-file]');
      const clearBtn = card.querySelector('[data-clear-field="icon"]');

      if (iconInput instanceof HTMLInputElement) {
        iconInput.value = item.icon || '';
        iconInput.addEventListener('input', (event) => {
          updateBusinessStyleField(item.id, 'icon', event.target.value || '');
        });
      }

      if (clearBtn instanceof HTMLElement) {
        clearBtn.addEventListener('click', () => {
          if (iconInput instanceof HTMLInputElement) {
            iconInput.value = '';
          }
          if (fileInput instanceof HTMLInputElement) {
            fileInput.value = '';
          }
          updateBusinessStyleField(item.id, 'icon', '');
        });
      }

      if (uploadBtn instanceof HTMLElement && fileInput instanceof HTMLInputElement) {
        uploadBtn.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', () => handleBusinessIconFileSelect(fileInput, iconInput, item.id));
      }
    }

    function createBusinessStyleCard(item) {
      const card = document.createElement('div');
      card.className = 'business-style-card card';
      card.dataset.styleId = item.id;
      card.innerHTML = `
        <div class="card-body">
          <div class="row g-3 align-items-end">
            <div class="col-md-4 col-lg-3">
              <label class="form-label">Бизнес</label>
              <input type="text" class="form-control form-control-sm" data-field="business" placeholder="Название бизнеса">
              <div class="form-text">Текст из столбца «Бизнес».</div>
            </div>
            <div class="col-md-4 col-lg-3">
              <label class="form-label">Иконка</label>
              <div class="input-group input-group-sm business-icon-input">
                <input
                  type="url"
                  class="form-control"
                  data-field="icon"
                  placeholder="https://... или /static/..."
                >
                <button class="btn btn-outline-secondary" type="button" data-icon-upload title="Загрузить файл">
                  <i class="bi bi-upload"></i>
                </button>
                <button class="btn btn-outline-secondary" type="button" data-clear-field="icon" title="Очистить иконку">
                  &times;
                </button>
              </div>
              <input type="file" class="d-none" accept="image/*" data-icon-file>
              <div class="form-text">Поддерживаются http(s), data:image, относительные пути и загрузка файла.</div>
            </div>
            <div class="col-md-4 col-lg-2">
              <label class="form-label">Цвет ячейки</label>
              <div class="input-group input-group-sm business-color-input">
                <input type="text" class="form-control" data-field="background_color" placeholder="#FFAA00">
                <input type="color" class="form-control form-control-color flex-shrink-0" data-color-picker="background_color" value="#ffffff" title="Цвет ячейки">
                <button class="btn btn-outline-secondary" type="button" data-clear-field="background_color" title="Очистить цвет">&times;</button>
              </div>
            </div>
            <div class="col-md-4 col-lg-2">
              <label class="form-label">Цвет текста</label>
              <div class="input-group input-group-sm business-color-input">
                <input type="text" class="form-control" data-field="text_color" placeholder="#FFFFFF">
                <input type="color" class="form-control form-control-color flex-shrink-0" data-color-picker="text_color" value="#ffffff" title="Цвет текста">
                <button class="btn btn-outline-secondary" type="button" data-clear-field="text_color" title="Очистить цвет">&times;</button>
              </div>
            </div>
            <div class="col-md-4 col-lg-2">
              <label class="form-label">Превью</label>
              <div class="business-style-preview" data-business-preview>
                <span class="business-style-preview-icon d-none"></span>
                <span class="business-style-preview-text">Пример</span>
              </div>
              <button type="button" class="btn btn-outline-danger btn-sm w-100 mt-2" data-remove-style>Удалить</button>
            </div>
          </div>
        </div>
      `;

      const businessInput = card.querySelector('[data-field="business"]');
      if (businessInput instanceof HTMLInputElement) {
        businessInput.value = item.business || '';
        businessInput.addEventListener('input', (event) => {
          updateBusinessStyleField(item.id, 'business', event.target.value || '');
        });
      }

      attachIconControls(card, item);
      attachColorControls(card, item, 'background_color');
      attachColorControls(card, item, 'text_color');

      const removeBtn = card.querySelector('[data-remove-style]');
      if (removeBtn instanceof HTMLElement) {
        removeBtn.addEventListener('click', () => removeBusinessStyle(item.id));
      }

      updateBusinessStylePreview(card, item);
      return card;
    }

    function renderBusinessStyles() {
      if (!businessStylesContainer) {
        return;
      }
      businessStylesContainer.innerHTML = '';
      state.businessStylesState.forEach((item) => {
        businessStylesContainer.appendChild(createBusinessStyleCard(item));
      });
      updateBusinessStylesEmptyState();
    }

    function initBusinessStylesEditor() {
      if (!businessStylesContainer) {
        return;
      }
      state.businessStylesState = [];
      Object.entries(state.initialBusinessCellStyles || {}).forEach(([business, config]) => {
        state.businessStylesState.push(createBusinessStyleState({
          business,
          background_color: config && config.background_color,
          text_color: config && config.text_color,
          icon: config && config.icon,
        }));
      });
      renderBusinessStyles();
    }

    function isValidBusinessIcon(icon) {
      if (typeof icon !== 'string') {
        return false;
      }
      const trimmed = icon.trim();
      return BUSINESS_ICON_ALLOWED_PREFIXES.some((prefix) => trimmed.startsWith(prefix));
    }

    function collectBusinessStylesPayload() {
      const payload = {};
      const errors = [];
      state.businessStylesState.forEach((item, index) => {
        const business = String(item.business || '').trim();
        if (!business) {
          errors.push(`Карточка №${index + 1}: укажите название бизнеса или удалите её.`);
          return;
        }
        const background = item.background_color ? sanitizeHexColor(item.background_color, '') : '';
        if (item.background_color && !background) {
          errors.push(`«${business}»: цвет ячейки должен быть в формате HEX (#RRGGBB).`);
        }
        const textColor = item.text_color ? sanitizeHexColor(item.text_color, '') : '';
        if (item.text_color && !textColor) {
          errors.push(`«${business}»: цвет текста должен быть в формате HEX (#RRGGBB).`);
        }
        const icon = String(item.icon || '').trim();
        if (icon && !isValidBusinessIcon(icon)) {
          errors.push(`«${business}»: иконка должна начинаться с http(s), / или data:image/.`);
        }
        payload[business] = {
          background_color: background,
          text_color: textColor,
          icon,
        };
      });
      return { payload, errors };
    }

    async function saveBusinessStyles() {
      if (!saveBusinessStylesBtn) {
        return;
      }
      const { payload, errors } = collectBusinessStylesPayload();
      if (errors.length) {
        notify('❌ ' + errors.join('\n'));
        return;
      }
      const originalText = saveBusinessStylesBtn.textContent;
      saveBusinessStylesBtn.disabled = true;
      saveBusinessStylesBtn.textContent = '...';
      try {
        const response = await fetch('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ business_cell_styles: payload }),
        });
        const data = await response.json();
        if (!response.ok || data.success === false) {
          throw new Error((data && data.error) || 'Не удалось сохранить настройки бизнесов');
        }
        state.businessStylesState = Object.entries(payload || {}).map(([business, config]) =>
          createBusinessStyleState({
            business,
            background_color: config && config.background_color,
            text_color: config && config.text_color,
            icon: config && config.icon,
          }));
        renderBusinessStyles();
        notify('✅ Настройки бизнесов сохранены');
      } catch (error) {
        const message = error && error.message ? error.message : String(error);
        notify('❌ Ошибка: ' + message);
      } finally {
        saveBusinessStylesBtn.disabled = false;
        saveBusinessStylesBtn.textContent = originalText || 'Сохранить изменения';
      }
    }

    function bindBusinessStylesActions() {
      if (addBusinessStyleBtn instanceof HTMLElement && businessStylesContainer) {
        addBusinessStyleBtn.addEventListener('click', () => {
          state.businessStylesState.push(createBusinessStyleState());
          renderBusinessStyles();
        });
      }

      if (saveBusinessStylesBtn instanceof HTMLElement) {
        saveBusinessStylesBtn.addEventListener('click', (event) => {
          event.preventDefault();
          saveBusinessStyles();
        });
      }
    }

    let businessStylesActionsBound = false;

    function init() {
      if (!businessStylesActionsBound) {
        bindBusinessStylesActions();
        businessStylesActionsBound = true;
      }
    }

    return {
      init,
      initClientStatuses,
      initBusinessStylesEditor,
      startStatusesEdit,
      saveClientStatuses,
      cancelStatusesEdit,
      addStatus,
    };
  }

  function mount(options = {}) {
    if (window.__settingsAppearanceRuntime) {
      return window.__settingsAppearanceRuntime;
    }

    const runtime = createRuntime(options);
    runtime.init();
    window.initClientStatuses = runtime.initClientStatuses;
    window.initBusinessStylesEditor = runtime.initBusinessStylesEditor;
    window.startStatusesEdit = runtime.startStatusesEdit;
    window.saveClientStatuses = runtime.saveClientStatuses;
    window.cancelStatusesEdit = runtime.cancelStatusesEdit;
    window.addStatus = runtime.addStatus;
    window.SettingsPageCallbackRegistry?.registerMany({
      initClientStatuses: runtime.initClientStatuses,
      initBusinessStylesEditor: runtime.initBusinessStylesEditor,
      startStatusesEdit: runtime.startStatusesEdit,
      saveClientStatuses: runtime.saveClientStatuses,
      cancelStatusesEdit: runtime.cancelStatusesEdit,
      addStatus: runtime.addStatus,
    });
    window.__settingsAppearanceRuntime = runtime;
    return runtime;
  }

  window.SettingsAppearanceRuntime = Object.freeze({
    mount,
    initClientStatuses(...args) {
      return window.__settingsAppearanceRuntime?.initClientStatuses?.(...args);
    },
    initBusinessStylesEditor(...args) {
      return window.__settingsAppearanceRuntime?.initBusinessStylesEditor?.(...args);
    },
    startStatusesEdit(...args) {
      return window.__settingsAppearanceRuntime?.startStatusesEdit?.(...args);
    },
    saveClientStatuses(...args) {
      return window.__settingsAppearanceRuntime?.saveClientStatuses?.(...args);
    },
    cancelStatusesEdit(...args) {
      return window.__settingsAppearanceRuntime?.cancelStatusesEdit?.(...args);
    },
    addStatus(...args) {
      return window.__settingsAppearanceRuntime?.addStatus?.(...args);
    },
  });
}());

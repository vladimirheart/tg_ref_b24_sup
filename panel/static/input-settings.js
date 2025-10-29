(function () {
  const modal = document.getElementById('inputFormattingModal');
  if (!modal) {
    return;
  }

  const PHONE_FORMATS = Array.from(modal.querySelectorAll('[data-phone-country] option')).map((option) => ({
    key: option.value,
    prefix: option.dataset.prefix || '',
    pattern: option.dataset.pattern || '',
    example: option.dataset.example || '',
    label: option.textContent.trim(),
  }));

  const phoneSelectors = {
    select: modal.querySelector('[data-phone-country]'),
    example: modal.querySelector('[data-phone-format-example]'),
    input: modal.querySelector('[data-phone-format-input]'),
    preview: modal.querySelector('[data-phone-preview]'),
    typeInput: modal.querySelector('[data-phone-type-input]'),
    typeAdd: modal.querySelector('[data-phone-type-add]'),
    typeList: modal.querySelector('[data-phone-type-list]'),
  };

  const FONT_DEFAULT_SIZE = 16;
  const defaultPhoneTypes = ['Рабочий', 'Личный', 'Дополнительный'];
  const phoneTypes = new Set(defaultPhoneTypes);

  function exportInputSettingsState() {
    const existing = window.__inputSettings || {};
    const format = getPhoneFormatByKey(phoneSelectors.select ? phoneSelectors.select.value : PHONE_FORMATS[0]?.key);
    const nextState = {
      ...existing,
      phone: {
        format: {
          key: format?.key || '',
          prefix: format?.prefix || '',
          pattern: format?.pattern || '',
          example: format?.example || '',
          label: format?.label || '',
        },
        types: Array.from(phoneTypes),
      },
    };
    window.__inputSettings = nextState;
    document.dispatchEvent(new CustomEvent('inputSettings:change', { detail: nextState }));
  }

  function getPhoneFormatByKey(key) {
    return PHONE_FORMATS.find((item) => item.key === key) || PHONE_FORMATS[0];
  }

  function applyPhoneMask(rawValue, format) {
    if (!format) return rawValue || '';
    const digits = (rawValue || '').replace(/\D+/g, '');
    const prefixDigits = (format.prefix || '').replace(/\D+/g, '');
    let cleanedDigits = digits;
    if (prefixDigits && cleanedDigits.startsWith(prefixDigits)) {
      cleanedDigits = cleanedDigits.slice(prefixDigits.length);
    }
    let result = format.prefix || '';
    let digitIndex = 0;
    const pattern = format.pattern || '';
    for (let i = 0; i < pattern.length; i += 1) {
      const char = pattern[i];
      if (char === '#') {
        if (digitIndex < cleanedDigits.length) {
          result += cleanedDigits[digitIndex];
          digitIndex += 1;
        } else {
          break;
        }
      } else if (digitIndex < cleanedDigits.length) {
        result += char;
      }
    }
    return result.trimEnd();
  }

  function updatePhonePreview(format, value) {
    if (phoneSelectors.example) {
      phoneSelectors.example.textContent = format.example || '';
    }
    const formattedValue = applyPhoneMask(value || '', format);
    const trimmedPrefix = (format.prefix || '').trim();
    const shouldUseExample = !formattedValue || formattedValue === trimmedPrefix;
    if (phoneSelectors.preview) {
      phoneSelectors.preview.textContent = shouldUseExample ? format.example || '' : formattedValue;
    }
  }

  function handlePhoneInput(event) {
    const format = getPhoneFormatByKey(phoneSelectors.select.value);
    const formatted = applyPhoneMask(event.target.value, format);
    event.target.value = formatted;
    updatePhonePreview(format, formatted);
  }

  function handlePhoneCountryChange() {
    const format = getPhoneFormatByKey(phoneSelectors.select.value);
    if (phoneSelectors.input) {
      phoneSelectors.input.value = format.prefix || '';
      phoneSelectors.input.setAttribute('placeholder', format.example || 'Введите номер телефона');
    }
    updatePhonePreview(format, '');
  }

  function createPhoneTypeBadge(value) {
    const badge = document.createElement('span');
    badge.className = 'badge bg-primary-subtle text-primary d-inline-flex align-items-center gap-2';
    badge.textContent = value;
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn-close ms-1';
    removeBtn.setAttribute('aria-label', 'Удалить тип');
    removeBtn.addEventListener('click', () => {
      phoneTypes.delete(value);
      renderPhoneTypes();
    });
    badge.appendChild(removeBtn);
    return badge;
  }

  function renderPhoneTypes() {
    if (phoneSelectors.typeList) {
      phoneSelectors.typeList.innerHTML = '';
      if (!phoneTypes.size) {
        const placeholder = document.createElement('span');
        placeholder.className = 'text-muted small';
        placeholder.textContent = 'Типы не заданы';
        phoneSelectors.typeList.appendChild(placeholder);
      } else {
        phoneTypes.forEach((type) => {
          phoneSelectors.typeList.appendChild(createPhoneTypeBadge(type));
        });
      }
    }
    exportInputSettingsState();
  }

  function addPhoneType() {
    if (!phoneSelectors.typeInput) return;
    const value = phoneSelectors.typeInput.value.trim();
    if (!value) {
      return;
    }
    phoneTypes.add(value);
    phoneSelectors.typeInput.value = '';
    renderPhoneTypes();
  }

  if (phoneSelectors.typeAdd) {
    phoneSelectors.typeAdd.addEventListener('click', addPhoneType);
  }

  if (phoneSelectors.typeInput) {
    phoneSelectors.typeInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        addPhoneType();
      }
    });
  }

  if (phoneSelectors.select) {
    phoneSelectors.select.addEventListener('change', () => {
      handlePhoneCountryChange();
      exportInputSettingsState();
    });
  }

  if (phoneSelectors.input) {
    phoneSelectors.input.addEventListener('input', handlePhoneInput);
  }

  renderPhoneTypes();
  handlePhoneCountryChange();
  exportInputSettingsState();

  const emailSelectors = {
    requireAt: modal.querySelector('[data-email-require-at]'),
    requireDomain: modal.querySelector('[data-email-require-domain]'),
    messageAt: modal.querySelector('[data-email-message-at]'),
    messageDomain: modal.querySelector('[data-email-message-domain]'),
    previewInput: modal.querySelector('[data-email-preview-input]'),
    previewMessage: modal.querySelector('[data-email-preview-message]'),
  };

  function getEmailValidationState() {
    return {
      requireAt: emailSelectors.requireAt ? emailSelectors.requireAt.checked : false,
      requireDomain: emailSelectors.requireDomain ? emailSelectors.requireDomain.checked : false,
      messageAt: emailSelectors.messageAt ? emailSelectors.messageAt.value.trim() : '',
      messageDomain: emailSelectors.messageDomain ? emailSelectors.messageDomain.value.trim() : '',
    };
  }

  function validateEmail(value, state) {
    const trimmed = (value || '').trim();
    if (!trimmed) {
      return {
        valid: false,
        message: 'Введите e-mail для проверки.',
        tone: 'muted',
      };
    }
    if (state.requireAt && !trimmed.includes('@')) {
      return {
        valid: false,
        message: state.messageAt || 'Укажите e-mail с символом @',
        tone: 'danger',
      };
    }
    const domain = trimmed.split('@')[1] || '';
    if (state.requireDomain && (!domain || !/\./.test(domain))) {
      return {
        valid: false,
        message: state.messageDomain || 'Добавьте домен, например example.com',
        tone: 'danger',
      };
    }
    return {
      valid: true,
      message: 'E-mail проходит проверку.',
      tone: 'success',
    };
  }

  function updateEmailPreview() {
    if (!emailSelectors.previewMessage) return;
    const state = getEmailValidationState();
    const value = emailSelectors.previewInput ? emailSelectors.previewInput.value : '';
    const result = validateEmail(value, state);
    emailSelectors.previewMessage.textContent = result.message;
    emailSelectors.previewMessage.classList.remove('text-danger', 'text-success', 'text-muted');
    const toneClass =
      result.tone === 'danger' ? 'text-danger' : result.tone === 'success' ? 'text-success' : 'text-muted';
    emailSelectors.previewMessage.classList.add(toneClass);
  }

  if (emailSelectors.previewInput) {
    emailSelectors.previewInput.addEventListener('input', updateEmailPreview);
  }
  if (emailSelectors.requireAt) {
    emailSelectors.requireAt.addEventListener('change', updateEmailPreview);
  }
  if (emailSelectors.requireDomain) {
    emailSelectors.requireDomain.addEventListener('change', updateEmailPreview);
  }
  if (emailSelectors.messageAt) {
    emailSelectors.messageAt.addEventListener('input', updateEmailPreview);
  }
  if (emailSelectors.messageDomain) {
    emailSelectors.messageDomain.addEventListener('input', updateEmailPreview);
  }

  updateEmailPreview();

  const ADDRESS_FIELDS = [
    { key: 'country', label: 'Страна' },
    { key: 'postal_code', label: 'Индекс' },
    { key: 'region', label: 'Регион' },
    { key: 'city', label: 'Город' },
    { key: 'district', label: 'Район' },
    { key: 'street', label: 'Улица' },
    { key: 'building', label: 'Дом' },
    { key: 'block', label: 'Строение / корпус' },
    { key: 'apartment', label: 'Квартира / офис' },
  ];

  const addressSelectors = {
    raw: modal.querySelector('[data-address-raw]'),
    previewList: modal.querySelector('[data-address-preview-list]'),
    overflow: modal.querySelector('[data-address-overflow]'),
  };

  const addressToggles = new Map();

  ADDRESS_FIELDS.forEach((field) => {
    const toggle = modal.querySelector(`[data-address-toggle="${field.key}"]`);
    if (toggle) {
      addressToggles.set(field.key, toggle);
      toggle.addEventListener('change', updateAddressPreview);
    }
  });

  function getEnabledFields() {
    return ADDRESS_FIELDS.filter((field) => {
      const toggle = addressToggles.get(field.key);
      return toggle ? toggle.checked : true;
    });
  }

  function parseAddressRaw(value) {
    return (value || '')
      .split(/[\n,]+/)
      .map((chunk) => chunk.trim())
      .filter(Boolean);
  }

  function updateAddressPreview() {
    const rawValue = addressSelectors.raw ? addressSelectors.raw.value : '';
    const chunks = parseAddressRaw(rawValue);
    const enabledFields = getEnabledFields();
    const assigned = new Map();
    let cursor = 0;
    enabledFields.forEach((field) => {
      const value = chunks[cursor] || '';
      assigned.set(field.key, value);
      if (value) {
        cursor += 1;
      }
      const valueCell = modal.querySelector(`[data-address-value="${field.key}"]`);
      if (valueCell) {
        valueCell.textContent = value || '—';
      }
    });

    ADDRESS_FIELDS.forEach((field) => {
      if (!assigned.has(field.key)) {
        const valueCell = modal.querySelector(`[data-address-value="${field.key}"]`);
        if (valueCell) {
          valueCell.textContent = '—';
        }
      }
    });

    if (addressSelectors.previewList) {
      addressSelectors.previewList.innerHTML = '';
      ADDRESS_FIELDS.forEach((field) => {
        if (!assigned.has(field.key)) {
          return;
        }
        const li = document.createElement('li');
        li.className = 'mb-1';
        const value = assigned.get(field.key) || '—';
        li.textContent = `${field.label}: ${value || '—'}`;
        addressSelectors.previewList.appendChild(li);
      });
      if (!addressSelectors.previewList.children.length) {
        const empty = document.createElement('li');
        empty.className = 'text-muted';
        empty.textContent = 'Добавьте адрес для предпросмотра.';
        addressSelectors.previewList.appendChild(empty);
      }
    }

    const overflowChunks = chunks.slice(cursor);
    if (addressSelectors.overflow) {
      if (overflowChunks.length) {
        addressSelectors.overflow.classList.remove('d-none');
        addressSelectors.overflow.textContent = `Неиспользованные данные: ${overflowChunks.join(', ')}`;
      } else {
        addressSelectors.overflow.classList.add('d-none');
        addressSelectors.overflow.textContent = '';
      }
    }
  }

  if (addressSelectors.raw) {
    addressSelectors.raw.addEventListener('input', updateAddressPreview);
  }

  updateAddressPreview();

  function applyFontSettings(block) {
    const key = block.dataset.fontSettings;
    if (!key) return;
    const familySelect = block.querySelector('[data-font-family]');
    const sizeInput = block.querySelector('[data-font-size]');
    const previews = modal.querySelectorAll(`[data-font-preview="${key}"]`);
    function updateStyles() {
      const family = familySelect ? familySelect.value : '';
      const size = sizeInput && sizeInput.value ? `${sizeInput.value}px` : `${FONT_DEFAULT_SIZE}px`;
      previews.forEach((element) => {
        if (family) {
          element.style.fontFamily = family;
        }
        if (size) {
          element.style.fontSize = size;
        }
      });
    }
    if (familySelect) {
      familySelect.addEventListener('change', updateStyles);
    }
    if (sizeInput) {
      sizeInput.addEventListener('input', updateStyles);
    }
    updateStyles();
  }

  modal.querySelectorAll('[data-font-settings]').forEach(applyFontSettings);

  const saveButton = modal.querySelector('[data-input-settings-save]');
  if (saveButton) {
    saveButton.addEventListener('click', () => {
      const enabledAddressFields = getEnabledFields().map((field) => field.label).join(', ');
      const message = [
        'Настройки вводимых данных обновлены.',
        `Формат телефона: ${getPhoneFormatByKey(phoneSelectors.select.value).label}.`,
        `Типы телефонов: ${Array.from(phoneTypes).join(', ') || 'не заданы'}.`,
        `Проверка e-mail: ${emailSelectors.requireAt && emailSelectors.requireAt.checked ? 'обязателен @' : 'символ @ не обязателен'}, ${
          emailSelectors.requireDomain && emailSelectors.requireDomain.checked ? 'обязателен домен' : 'домен не обязателен'
        }.`,
        `Блоки адреса: ${enabledAddressFields || 'ничего не выбрано'}.`,
      ].join('\n');
      alert(message);
    });
  }
})();

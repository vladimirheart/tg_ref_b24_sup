(() => {
  const table = document.querySelector('table');
  const alertBox = document.getElementById('channelsActionAlert');
  const configModalEl = document.getElementById('publicFormConfigModal');
  const configForm = document.getElementById('publicFormConfigForm');
  const configAlert = document.getElementById('publicFormConfigAlert');
  const configChannelIdInput = document.getElementById('publicFormConfigChannelId');
  const configTitle = document.getElementById('publicFormConfigModalTitle');
  const configEnabledInput = document.getElementById('publicFormEnabled');
  const configCaptchaInput = document.getElementById('publicFormCaptchaEnabled');
  const configDisabledStatusInput = document.getElementById('publicFormDisabledStatus');
  const configFieldsInput = document.getElementById('publicFormFieldsJson');
  const configModal = window.bootstrap && configModalEl ? new window.bootstrap.Modal(configModalEl) : null;

  if (!table) {
    return;
  }

  function showAlert(message, type = 'success') {
    if (!alertBox) {
      return;
    }
    alertBox.textContent = message;
    alertBox.className = `alert alert-${type} mt-3`;
    alertBox.classList.remove('d-none');
    window.setTimeout(() => alertBox.classList.add('d-none'), 3500);
  }

  function showConfigAlert(message, type = 'success') {
    if (!configAlert) {
      return;
    }
    configAlert.textContent = message;
    configAlert.className = `alert alert-${type}`;
    configAlert.classList.remove('d-none');
  }

  function clearConfigAlert() {
    if (!configAlert) {
      return;
    }
    configAlert.textContent = '';
    configAlert.className = 'alert d-none mb-0';
  }

  async function fetchChannel(channelId) {
    const response = await fetch('/api/channels', { credentials: 'same-origin' });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      throw new Error(payload.error || 'Не удалось загрузить канал');
    }
    const channels = Array.isArray(payload.channels) ? payload.channels : [];
    return channels.find((item) => String(item.id) === String(channelId));
  }

  function normalizeFieldsForEditor(fields) {
    if (!Array.isArray(fields)) {
      return [];
    }
    return fields.map((field, index) => ({
      id: String(field?.id || '').trim(),
      text: String(field?.text || '').trim(),
      type: String(field?.type || 'text').trim().toLowerCase(),
      required: field?.required === true,
      order: Number.parseInt(field?.order, 10) || ((index + 1) * 10),
      placeholder: String(field?.placeholder || '').trim(),
      helpText: String(field?.helpText || '').trim(),
      minLength: Number.parseInt(field?.minLength, 10) || 0,
      maxLength: Number.parseInt(field?.maxLength, 10) || 500,
      options: Array.isArray(field?.options) ? field.options.map((item) => String(item || '').trim()).filter(Boolean) : undefined,
    }));
  }

  async function openPublicFormEditor(row) {
    if (!configModal || !configForm) {
      throw new Error('Форма настройки недоступна на этой странице');
    }
    clearConfigAlert();
    const channelId = row.dataset.channelId;
    if (!channelId) {
      throw new Error('Не удалось определить канал');
    }
    const channel = await fetchChannel(channelId);
    if (!channel) {
      throw new Error('Канал не найден');
    }
    const cfg = (channel.questions_cfg && typeof channel.questions_cfg === 'object') ? channel.questions_cfg : {};
    configChannelIdInput.value = String(channel.id);
    configTitle.textContent = `Настройка внешней формы — ${channel.channel_name || `Канал #${channel.id}`}`;
    configEnabledInput.checked = cfg.enabled === true;
    configCaptchaInput.checked = cfg.captchaEnabled === true;
    configDisabledStatusInput.value = Number.parseInt(cfg.disabledStatus, 10) === 410 ? '410' : '404';
    configFieldsInput.value = JSON.stringify(normalizeFieldsForEditor(cfg.fields), null, 2);
    configModal.show();
  }

  async function savePublicFormConfig() {
    clearConfigAlert();
    const channelId = configChannelIdInput?.value;
    if (!channelId) {
      throw new Error('Не выбран канал');
    }
    let fields;
    try {
      fields = JSON.parse(String(configFieldsInput?.value || '[]'));
    } catch (error) {
      throw new Error('Некорректный JSON в полях формы');
    }
    if (!Array.isArray(fields)) {
      throw new Error('Поля формы должны быть JSON-массивом');
    }

    const payload = {
      questions_cfg: {
        schemaVersion: 1,
        enabled: Boolean(configEnabledInput?.checked),
        captchaEnabled: Boolean(configCaptchaInput?.checked),
        disabledStatus: Number.parseInt(configDisabledStatusInput?.value, 10) === 410 ? 410 : 404,
        fields,
      },
    };

    const response = await fetch(`/api/channels/${encodeURIComponent(channelId)}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      credentials: 'same-origin',
      body: JSON.stringify(payload),
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok || result.success === false) {
      throw new Error(result.error || 'Не удалось сохранить конфигурацию формы');
    }
    configModal.hide();
    showAlert('Конфигурация внешней формы сохранена.');
  }

  async function copyToClipboard(value) {
    if (!value) {
      throw new Error('Публичная ссылка отсутствует');
    }
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value);
      return;
    }
    const temp = document.createElement('textarea');
    temp.value = value;
    temp.setAttribute('readonly', 'readonly');
    temp.style.position = 'absolute';
    temp.style.left = '-9999px';
    document.body.appendChild(temp);
    temp.select();
    document.execCommand('copy');
    document.body.removeChild(temp);
  }

  function resolvePublicLink(row) {
    const explicit = row.querySelector('[data-role="public-link"]');
    if (explicit?.href) {
      return explicit.href;
    }
    const publicId = (row.dataset.publicId || '').trim();
    if (!publicId) {
      return '';
    }
    return `${window.location.origin}/public/forms/${encodeURIComponent(publicId)}`;
  }

  async function regeneratePublicId(row, button) {
    const channelId = row.dataset.channelId;
    if (!channelId) {
      throw new Error('Не удалось определить канал');
    }
    const response = await fetch(`/api/channels/${encodeURIComponent(channelId)}/public-id/regenerate`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'same-origin'
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok || payload.success === false) {
      throw new Error(payload.error || 'Не удалось регенерировать ссылку');
    }
    const publicId = (payload.public_id || '').toString().trim();
    row.dataset.publicId = publicId;
    const idCell = row.querySelector('[data-role="public-id"]');
    if (idCell) {
      idCell.textContent = publicId || '—';
    }
    let linkEl = row.querySelector('[data-role="public-link"]');
    if (publicId) {
      const relativePath = `/public/forms/${publicId}`;
      if (!linkEl) {
        linkEl = document.createElement('a');
        linkEl.className = 'small';
        linkEl.dataset.role = 'public-link';
        linkEl.target = '_blank';
        linkEl.rel = 'noopener';
        idCell?.insertAdjacentElement('afterend', linkEl);
      }
      linkEl.href = relativePath;
      linkEl.textContent = relativePath;
    }
    button.blur();
    showAlert('Публичный ID регенерирован. Старые ссылки более не действуют.', 'warning');
  }

  table.addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-action]');
    if (!button) {
      return;
    }
    const row = button.closest('tr[data-channel-id]');
    if (!row) {
      return;
    }

    const action = button.dataset.action;
    try {
      button.disabled = true;
      if (action === 'copy-public-link') {
        const link = resolvePublicLink(row);
        await copyToClipboard(link);
        showAlert('Ссылка на форму скопирована в буфер обмена.');
      }
      if (action === 'regenerate-public-id') {
        if (!window.confirm('Сгенерировать новый публичный ID? Старые ссылки перестанут работать.')) {
          return;
        }
        await regeneratePublicId(row, button);
      }
      if (action === 'configure-public-form') {
        await openPublicFormEditor(row);
      }
    } catch (error) {
      showAlert(error.message || 'Операция не выполнена', 'danger');
    } finally {
      button.disabled = false;
    }
  });

  configForm?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submitButton = configForm.querySelector('button[type="submit"]');
    try {
      if (submitButton) {
        submitButton.disabled = true;
      }
      await savePublicFormConfig();
    } catch (error) {
      showConfigAlert(error.message || 'Не удалось сохранить настройки формы', 'danger');
    } finally {
      if (submitButton) {
        submitButton.disabled = false;
      }
    }
  });
})();

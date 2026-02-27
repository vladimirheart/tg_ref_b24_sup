(() => {
  const table = document.querySelector('table');
  const alertBox = document.getElementById('channelsActionAlert');
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
    } catch (error) {
      showAlert(error.message || 'Операция не выполнена', 'danger');
    } finally {
      button.disabled = false;
    }
  });
})();
